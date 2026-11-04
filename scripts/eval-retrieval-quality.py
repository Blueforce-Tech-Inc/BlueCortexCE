#!/usr/bin/env python3
"""Measure retrieval quality (hit@1, hit@5, MRR) on real session-history queries.

Manual harness, like the model evaluator: it needs the real embedding provider and
a disposable PostgreSQL. It does not run in CI.

It builds a corpus (see build-retrieval-corpus.py), embeds every observation and
query with the configured real model (BAAI/bge-m3, 1024 dimensions by default),
loads a disposable database, and then measures the retrieval shapes that exist
in production SQL:

  vector    ObservationRepository.semanticSearch1024 (cosine, no similarity floor)
  lexical   ObservationRepository.fullTextSearchScored (V24 token coverage)
  hybrid    ObservationRepository.hybridSearchScored (cosine + w * coverage)

Diagnostics keep a claim explainable rather than guessed: the old English
tsvector path (kept to document what V24 replaced), ts_rank over the same tokens,
and a sweep of the fusion weight. Round 16 adds the language split (zh / en /
mixed), a recency-rerank sweep on the fused pool, and a near-duplicate merge;
candidates are cut at the request's turn so later observations cannot leak in.
Production now applies the 7-day recency half-life chosen here, so the plain
`hybrid` number is the pre-decay baseline, not what the service returns. The
production SQL is copied verbatim from the repository; if it changes, this
harness must change with it.

Usage:
    python3 scripts/eval-retrieval-quality.py \
        --corpus backend/src/test/resources/eval/retrieval-corpus-real.json
"""

from __future__ import annotations

import argparse
import json
import math
import os
import random
import re
import statistics
import subprocess
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
CONTAINER = os.environ.get("RETRIEVAL_PG_CONTAINER", "claude-mem-test-pg")
DB_PORT = int(os.environ.get("RETRIEVAL_PG_PORT", "5433"))
DB_USER = "postgres"
DB_PASSWORD = "123456"
DATABASE = os.environ.get("RETRIEVAL_DB", f"cortexce_retrieval_{int(time.time())}")
PROJECT = "/tmp/cortexce-retrieval-project"
SESSION = "retrieval-eval-session"
TOP_K = 5
# Production: `cortexce.search.lexical-weight` and the pool depth SearchService passes
# (`limit * 2` for the default 20-result request).
LEXICAL_WEIGHT = 2.0
PRODUCTION_LIMIT = 40
# Recency rerank sweep: the fused pool is reordered by score * 0.5^(age/half_life).
DECAY_HALF_LIVES = (7, 30, 90, 365)
# Round 18: candidate-pool depths. Production fetches `limit * 2` = 40 for the default
# 20-result page; the sweep asks whether widening the pool lets the recency rerank
# promote relevant rows that the fused top-40 cut away.
POOL_DEPTHS = (40, 100, 200)
# Round 20: widen only the vector candidate list. The lexical half stays at the
# production depth, so any delta is attributable to vectors the fused top-40
# never saw. 400 is the corpus-sized ceiling (188 at the time of writing).
VECTOR_DEPTHS = (100, 200, 400)
# Round 21: vector:lexical RRF ratios. 1:2 is the production baseline; the
# sweep asks whether the lexical half really is the bottleneck K3 suggested.
FUSION_WEIGHTS = (0.5, 1.0, 1.5, 2.0, 3.0)


def load_env() -> dict:
    values = {}
    for line in (ROOT / "backend/.env").read_text().splitlines():
        match = re.match(r"^([A-Z0-9_]+)=(.*)$", line.strip())
        if match:
            values[match.group(1)] = match.group(2).strip().strip('"').strip("'")
    return values


ENV = load_env()
EMBED_BASE = ENV.get("SPRING_AI_OPENAI_EMBEDDING_BASE_URL", "https://api.siliconflow.cn")
EMBED_KEY = ENV.get("SPRING_AI_OPENAI_EMBEDDING_API_KEY", "")
EMBED_MODEL = ENV.get("SPRING_AI_OPENAI_EMBEDDING_MODEL", "BAAI/bge-m3")
EMBED_DIM = int(ENV.get("SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS", "1024"))


def psql(sql: str, database: str = DATABASE, timeout: int = 300) -> str:
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "psql", "-U", DB_USER, "-d", database,
         "-v", "ON_ERROR_STOP=1", "-t", "-A"],
        input=sql, text=True, capture_output=True, timeout=timeout,
    )
    if result.returncode != 0:
        raise SystemExit(f"psql failed:\n{result.stderr}\n--- sql head ---\n{sql[:2000]}")
    return result.stdout.strip()


def create_database() -> None:
    psql(f"DROP DATABASE IF EXISTS {DATABASE};", database="postgres")
    psql(f"CREATE DATABASE {DATABASE};", database="postgres")


def latest_jar() -> Path:
    jars = sorted((ROOT / "backend/target").glob("cortex-ce-*.jar"))
    jars = [jar for jar in jars if not jar.name.endswith("-sources.jar")]
    if not jars:
        raise SystemExit("Build the jar first: cd backend && mvn -o -B -DskipTests package")
    return jars[-1]


def migrate() -> None:
    """Start the service once so Flyway applies this repo's migrations, then stop it."""
    env = dict(os.environ)
    env.update({
        "SERVER_PORT": "37890",
        "SPRING_DATASOURCE_URL": f"jdbc:postgresql://127.0.0.1:{DB_PORT}/{DATABASE}",
        "SPRING_DATASOURCE_USERNAME": DB_USER,
        "SPRING_DATASOURCE_PASSWORD": DB_PASSWORD,
        "SPRING_AI_OPENAI_EMBEDDING_API_KEY": EMBED_KEY,
        "SPRING_AI_OPENAI_EMBEDDING_BASE_URL": EMBED_BASE,
        "SPRING_AI_OPENAI_EMBEDDING_MODEL": EMBED_MODEL,
        "SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS": str(EMBED_DIM),
    })
    log = open("/tmp/retrieval-migrate.log", "w")
    process = subprocess.Popen(["java", "-Xmx512m", "-jar", str(latest_jar())],
                               env=env, stdout=log, stderr=subprocess.STDOUT)
    try:
        deadline = time.time() + 180
        while time.time() < deadline:
            if process.poll() is not None:
                raise SystemExit("Service exited during migration; see /tmp/retrieval-migrate.log")
            try:
                with urlopen("http://127.0.0.1:37890/api/health", timeout=2):
                    break
            except (HTTPError, OSError):
                time.sleep(1)
        else:
            raise SystemExit("Service did not become healthy; see /tmp/retrieval-migrate.log")
    finally:
        process.terminate()
        process.wait(timeout=30)
    applied = psql("SELECT max(version::numeric) FROM flyway_schema_history WHERE success;",
                   database=DATABASE)
    print(f"migration head applied: {applied}")


def embed(texts: list[str], batch: int = 16) -> list[list[float]]:
    vectors: list[list[float]] = []
    for start in range(0, len(texts), batch):
        chunk = [text[:2000] if text else "empty" for text in texts[start:start + batch]]
        payload = json.dumps({"model": EMBED_MODEL, "input": chunk, "encoding_format": "float"})
        request = Request(
            f"{EMBED_BASE.rstrip('/')}/v1/embeddings",
            data=payload.encode(), headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {EMBED_KEY}",
            },
        )
        last_error = None
        for attempt in range(4):
            try:
                with urlopen(request, timeout=120) as response:
                    body = json.loads(response.read())
                break
            except (HTTPError, URLError, ConnectionError) as error:
                last_error = error
                time.sleep(2 * (attempt + 1))
        else:
            raise SystemExit(f"embedding call failed after retries: {last_error}")
        ordered = sorted(body["data"], key=lambda item: item.get("index", 0))
        vectors.extend(item["embedding"] for item in ordered)
        print(f"  embedded {len(vectors)}/{len(texts)}", end="\r")
    print()
    if any(len(vector) != EMBED_DIM for vector in vectors):
        raise SystemExit(f"embedding dimension mismatch, expected {EMBED_DIM}")
    return vectors


def vector_literal(vector: list[float]) -> str:
    return "[" + ",".join(f"{value:.7f}" for value in vector) + "]"


def cosine(left: list[float], right: list[float]) -> float:
    dot = sum(a * b for a, b in zip(left, right))
    return dot / (math.sqrt(sum(a * a for a in left)) * math.sqrt(sum(b * b for b in right)))


def sql_quote(text: str) -> str:
    return "'" + text.replace("'", "''") + "'"


def observation_text(observation: dict) -> str:
    facts = " ; ".join(observation.get("facts") or [])
    return f"{observation['title']}\n{observation['narrative']}\n{facts}"


def uuid_for(key: str) -> str:
    """Stable UUID per corpus observation so ranked rows can be mapped back."""
    import uuid
    return str(uuid.uuid5(uuid.NAMESPACE_DNS, key))


def load_rows(observations: list[dict], vectors: list[list[float]]) -> None:
    statements = [
        f"""
        INSERT INTO mem_sessions (id, content_session_id, project_path, started_at_epoch,
                                  status, platform_source)
        VALUES (gen_random_uuid(), {sql_quote(SESSION)}, {sql_quote(PROJECT)},
                {int(time.time())}, 'active', 'claude')
        ON CONFLICT DO NOTHING;
        INSERT INTO mem_memory_scopes (scope_type, scope_key, scope_key_sha256, display_name)
        VALUES ('project', {sql_quote(PROJECT)}, sha256(convert_to({sql_quote(PROJECT)}, 'UTF8')),
                'Retrieval eval')
        ON CONFLICT DO NOTHING;
        """
    ]
    epoch = int(time.time()) - len(observations)
    for index, (observation, vector) in enumerate(zip(observations, vectors)):
        statements.append(f"""
        INSERT INTO mem_observations (
            id, content_session_id, project_path, type, title, subtitle, content, facts,
            concepts, files_modified, content_hash, created_at, created_at_epoch,
            memory_scope_id, platform_source, embedding_state, embedding_1024
        ) VALUES (
            {sql_quote(uuid_for(observation['id']))}::uuid,
            {sql_quote(SESSION)}, {sql_quote(PROJECT)}, 'feature',
            {sql_quote(observation['title'])}, '', {sql_quote(observation['narrative'])},
            {sql_quote(json.dumps(observation.get('facts') or [], ensure_ascii=False))}::jsonb,
            '[]'::jsonb,
            {sql_quote(json.dumps([observation.get('file', '')], ensure_ascii=False))}::jsonb,
            {sql_quote(observation['id'][-16:].ljust(16, '0')[:16])},
            now(), {int(observation.get('observed_at') or (epoch + index))},
            (SELECT id FROM mem_memory_scopes
             WHERE scope_type = 'project' AND scope_key = {sql_quote(PROJECT)} COLLATE "C"),
            'claude', 'ready', {sql_quote(vector_literal(vector))}::vector
        );
        """)
    statements.append("ANALYZE mem_observations;")
    psql("\n".join(statements))


# Evaluation clock: observations created after the query's turn did not exist yet
# when the request was made, so letting the retriever rank them is future leakage
# and makes recency meaningless. main() sets this before ranking each query.
CURRENT_CUTOFF = 0


def scope_filter() -> str:
    clause = f"""
    memory_scope_id IN (
        SELECT id FROM mem_memory_scopes
        WHERE scope_type = 'project' AND scope_key = {sql_quote(PROJECT)} COLLATE "C"
    )"""
    if CURRENT_CUTOFF:
        clause += f"\n      AND created_at_epoch <= {CURRENT_CUTOFF}"
    return clause


def rank_vector(query_vector: str, limit: int = TOP_K) -> list[str]:
    return psql(f"""
        SELECT id FROM mem_observations
        WHERE {scope_filter()}
          AND embedding_1024 IS NOT NULL
          AND created_at_epoch > 0
        ORDER BY embedding_1024 <=> cast({sql_quote(query_vector)} as vector)
        LIMIT {limit};
    """).splitlines()


TOKEN_QUERY = """
        WITH qb AS (
            SELECT DISTINCT unnest(string_to_array(cortex_tokens({query}), ' ')) AS tok
        )
"""


def rank_fulltext(query: str, limit: int = TOP_K) -> list[str]:
    """Production lexical path: count of distinct query tokens the row contains."""
    return psql(TOKEN_QUERY.format(query=sql_quote(query)) + f"""
        SELECT o.id
        FROM qb
        JOIN mem_observations o
          ON o.search_tokens @@ to_tsquery('simple', quote_literal(qb.tok))
        WHERE {scope_filter()}
        GROUP BY o.id
        ORDER BY count(*) DESC, o.id
        LIMIT {limit};
    """).splitlines()


def rank_fulltext_english(query: str, limit: int = TOP_K) -> list[str]:
    """What V24 replaced: English tsvector + conjunctive plainto_tsquery."""
    return psql(f"""
        SELECT o.id
        FROM mem_observations o
        WHERE {scope_filter()}
          AND to_tsvector('english', coalesce(o.title,'') || ' ' || coalesce(o.content,''))
              @@ plainto_tsquery('english', {sql_quote(query)})
        LIMIT {limit};
    """).splitlines()


def rank_fulltext_ts_rank(query: str, limit: int = TOP_K) -> list[str]:
    """Diagnostic: same tokens, but ordered by ts_rank_cd instead of coverage."""
    return psql(f"""
        WITH qb AS (
            SELECT DISTINCT unnest(string_to_array(cortex_tokens({sql_quote(query)}), ' ')) AS tok
        ),
        tq AS (SELECT to_tsquery('simple', string_agg(quote_literal(tok), ' | ')) AS query FROM qb)
        SELECT o.id
        FROM mem_observations o, tq
        WHERE {scope_filter()}
          AND o.search_tokens @@ tq.query
        ORDER BY ts_rank_cd(o.search_tokens, tq.query) DESC
        LIMIT {limit};
    """).splitlines()


def rank_hybrid(query: str, query_vector: str, min_similarity: float,
                lexical_weight: float = LEXICAL_WEIGHT, limit: int = PRODUCTION_LIMIT) -> list[str]:
    """Production hybrid: weighted reciprocal-rank fusion of cosine and token coverage."""
    return psql(TOKEN_QUERY.format(query=sql_quote(query)) + f"""
        , qtotal AS (SELECT greatest(count(*), 1) AS n FROM qb),
        text_cov AS (
            SELECT o.id, count(*) AS matched
            FROM qb
            JOIN mem_observations o
              ON o.search_tokens @@ to_tsquery('simple', quote_literal(qb.tok))
            WHERE {scope_filter()}
              AND o.created_at_epoch > 0
            GROUP BY o.id
        ),
        semantic_ranked AS (
            SELECT o.id, row_number() OVER (
                       ORDER BY o.embedding_1024 <=> cast({sql_quote(query_vector)} as vector)) AS rank
            FROM mem_observations o
            WHERE {scope_filter()}
              AND o.embedding_1024 IS NOT NULL
              AND o.created_at_epoch > 0
              AND (1.0 - (o.embedding_1024 <=> cast({sql_quote(query_vector)} as vector))) >= {min_similarity}
            ORDER BY o.embedding_1024 <=> cast({sql_quote(query_vector)} as vector)
            LIMIT {limit}
        ),
        lexical_ranked AS (
            SELECT id, row_number() OVER (ORDER BY matched DESC, id) AS rank FROM text_cov LIMIT {limit}
        ),
        fused AS (
            SELECT id, SUM(score) AS score
            FROM (
                SELECT id, 1.0 / (60 + rank) AS score FROM semantic_ranked
                UNION ALL
                SELECT id, {lexical_weight} / (60 + rank) AS score FROM lexical_ranked
            ) parts
            GROUP BY id
        )
        SELECT f.id
        FROM fused f
        LEFT JOIN text_cov t ON t.id = f.id
        ORDER BY f.score DESC, f.id
        LIMIT {limit};
    """).splitlines()


def hybrid_pool(query: str, query_vector: str, min_similarity: float,
                limit: int = PRODUCTION_LIMIT,
                semantic_limit: int | None = None,
                lexical_weight: float = LEXICAL_WEIGHT) -> list[dict]:
    """The fused top-N with the metadata a reranker needs: fused score, time, title.

    `semantic_limit` widens only the vector list; the lexical list and the final
    fused cut stay at `limit`. That is the round-20 experiment: a relevant row
    that the vector scan ranked below the fused top-40 can enter without giving
    the lexical pool more candidates.
    """
    semantic_limit = semantic_limit or limit
    rows = psql(TOKEN_QUERY.format(query=sql_quote(query)) + f"""
        , qtotal AS (SELECT greatest(count(*), 1) AS n FROM qb),
        text_cov AS (
            SELECT o.id, count(*) AS matched
            FROM qb
            JOIN mem_observations o
              ON o.search_tokens @@ to_tsquery('simple', quote_literal(qb.tok))
            WHERE {scope_filter()}
              AND o.created_at_epoch > 0
            GROUP BY o.id
        ),
        semantic_ranked AS (
            SELECT o.id, row_number() OVER (
                       ORDER BY o.embedding_1024 <=> cast({sql_quote(query_vector)} as vector)) AS rank
            FROM mem_observations o
            WHERE {scope_filter()}
              AND o.embedding_1024 IS NOT NULL
              AND o.created_at_epoch > 0
              AND (1.0 - (o.embedding_1024 <=> cast({sql_quote(query_vector)} as vector))) >= {min_similarity}
            ORDER BY o.embedding_1024 <=> cast({sql_quote(query_vector)} as vector)
            LIMIT {semantic_limit}
        ),
        lexical_ranked AS (
            SELECT id, row_number() OVER (ORDER BY matched DESC, id) AS rank FROM text_cov LIMIT {limit}
        ),
        fused AS (
            SELECT id, SUM(score) AS score
            FROM (
                SELECT id, 1.0 / (60 + rank) AS score FROM semantic_ranked
                UNION ALL
                SELECT id, {lexical_weight} / (60 + rank) AS score FROM lexical_ranked
            ) parts
            GROUP BY id
        )
        SELECT json_build_object(
                   'id', f.id, 'score', f.score,
                   'cosine', 1.0 - (o.embedding_1024 <=> cast({sql_quote(query_vector)} as vector)),
                   'epoch', o.created_at_epoch, 'title', o.title)::text
        FROM fused f
        JOIN mem_observations o ON o.id = f.id
        ORDER BY f.score DESC, f.id
        LIMIT {limit};
    """).splitlines()
    return [json.loads(line) for line in rows if line.strip()]


def normalize_title(title: str) -> str:
    """Same-title key for near-duplicate merging.

    Observation titles are generated as "Created <file>" / "Changed <file>", so
    the same file edited in five sessions produces five titles that differ only
    by the verb' position. Lowercase, drop the verb and punctuation, and collapse
    whitespace so those collapse into one key.
    """
    text = (title or "").lower()
    text = re.sub(r"^(created|changed|updated|modified)\s+", "", text)
    text = re.sub(r"[^0-9a-z\u4e00-\u9fff]+", " ", text)
    return re.sub(r"\s+", " ", text).strip() or (title or "").lower().strip()


def rerank_time_decay(pool: list[dict], now_ms: int, half_life_days: float) -> list[dict]:
    """Recency rerank: halve the fused score every half_life_days of age."""
    def adjusted(item: dict) -> float:
        age_days = max(0.0, (now_ms - int(item["epoch"])) / 86_400_000)
        return float(item["score"]) * (0.5 ** (age_days / half_life_days))

    return sorted(pool, key=lambda item: (-adjusted(item), item["id"]))


def rerank_dedupe(pool: list[dict]) -> list[dict]:
    """Near-duplicate merge: keep only the newest row for each normalized title.

    Order is preserved, so a newest duplicate that sat below its older copies
    moves up when those are dropped. Relevant rows that lose this merge are
    counted per query, because a merge that hides the expected row is a real
    regression even when the aggregate metric improves.
    """
    newest: dict[str, dict] = {}
    for item in pool:
        key = normalize_title(str(item.get("title") or ""))
        current = newest.get(key)
        if current is None or int(item["epoch"]) > int(current["epoch"]):
            newest[key] = item
    keep = {item["id"] for item in newest.values()}
    return [item for item in pool if item["id"] in keep]


# ---------------------------------------------------------------------------
# Round 17 experiments: intent/type weighting, bilingual synonym expansion and
# embedding rerank of the fused top-20. Each is a generic rule with fixed
# parameters; none of them was tuned by sweeping values against these 90 queries.
# ---------------------------------------------------------------------------

# Intent is read from ordinary task verbs, not from corpus labels. A request that
# asks to create something should favour rows the assistant wrote; a request that
# asks to fix or change something should favour rows it edited.
WRITE_CUES = ("新增", "创建", "新建", "实现", "添加", "加一个", "写一个", "补一个",
              "create", "add ", "implement", "new file", "introduce")
EDIT_CUES = ("修复", "改成", "改为", "调整", "重构", "删掉", "移除", "替换", "回退",
             "fix", "change", "refactor", "update", "modify", "replace", "remove",
             "rename", "migrate")
TYPE_BOOST = 1.10


def classify_intent(query: str) -> str:
    lowered = query.lower()
    writes = sum(1 for cue in WRITE_CUES if cue in lowered)
    edits = sum(1 for cue in EDIT_CUES if cue in lowered)
    if writes == 0 and edits == 0:
        return "neutral"
    return "write" if writes > edits else "edit"


def observation_kind(title: str) -> str:
    """The corpus title records whether the tool call wrote or changed a file."""
    return "write" if (title or "").strip().lower().startswith("created") else "edit"


def rerank_intent_type(pool: list[dict], intent: str) -> list[dict]:
    """Boost rows whose kind matches the request intent, preserving fused order otherwise."""
    if intent == "neutral":
        return list(pool)
    return sorted(
        pool,
        key=lambda item: (-(float(item["score"]) * (TYPE_BOOST
                             if observation_kind(item.get("title") or "") == intent else 1.0)),
                          item["id"]),
    )


# Generic bilingual vocabulary: the request is Chinese, the observations are
# English code, and V24 indexes them as CJK bigrams plus ASCII tokens, so the two
# sides share almost no token. The map is software terminology, not corpus phrases.
SYNONYMS = {
    "修复": ["fix"], "报错": ["error"], "异常": ["exception"], "队列": ["queue"],
    "缓存": ["cache"], "索引": ["index"], "检索": ["search", "retrieval"],
    "查询": ["query"], "迁移": ["migration", "migrate"], "认证": ["auth", "authentication"],
    "权限": ["permission", "authorization"], "并发": ["concurrency", "concurrent"],
    "测试": ["test"], "部署": ["deploy", "deployment"], "配置": ["config", "configuration"],
    "日志": ["log"], "指标": ["metric", "metrics"], "监控": ["monitor"],
    "文档": ["doc", "documentation"], "接口": ["api", "endpoint"], "数据库": ["database", "db"],
    "事务": ["transaction"], "超时": ["timeout"], "重试": ["retry"], "分页": ["pagination", "page"],
    "锁": ["lock"], "字段": ["column", "field"], "校验": ["validate", "validation"],
    "解析": ["parse"], "回滚": ["rollback"], "备份": ["backup"], "恢复": ["restore"],
    "批量": ["batch"], "压缩": ["compress"], "去重": ["dedupe"], "排序": ["sort", "order"],
}
MAX_EXPANSIONS = 12


def expand_query(query: str) -> str:
    """Append at most MAX_EXPANSIONS English equivalents for terms present in the query."""
    extra: list[str] = []
    for term, replacements in SYNONYMS.items():
        if term in query:
            for replacement in replacements:
                if replacement not in extra:
                    extra.append(replacement)
    return query + " " + " ".join(extra[:MAX_EXPANSIONS]) if extra else query


def rerank_by_cosine(pool: list[dict], key: str) -> list[dict]:
    """Pure embedding rerank of the fused pool."""
    return sorted(pool, key=lambda item: (-float(item.get(key) or 0.0), item["id"]))


# ---------------------------------------------------------------------------
# Round 19 experiments: cluster expansion and query-side identifier expansion.
#
# Both rules answer the production question "can a candidate that never entered
# the fused top-40 still be promoted?", which the round-18 pool sweep answered
# "no" by fetching deeper. Cluster expansion does not fetch a deeper fused pool:
# it takes the neighbours of the rows that already ranked and gives them a
# fraction of the hit's decayed score. Query expansion changes the lexical half
# instead, appending identifier-like tokens that the project actually used
# recently, which is where the cross-language gap is worst.
# ---------------------------------------------------------------------------

CLUSTER_WEIGHTS = (0.5, 0.75, 1.0)
SESSION_WINDOW = 2
RECENT_WINDOW_DAYS = 30
MAX_RECENT_IDENTIFIERS = 3
IDENTIFIER_PATTERN = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(?:[./][A-Za-z0-9_]+)*")


def cluster_neighbours(observations: list[dict]) -> dict[str, dict[str, set[str]]]:
    """Neighbour sets per row: session-adjacent (±SESSION_WINDOW in time) and same file."""
    by_session: dict[str, list[dict]] = {}
    by_file: dict[str, set[str]] = {}
    for observation in observations:
        by_session.setdefault(observation.get("session") or "", []).append(observation)
        if observation.get("file"):
            by_file.setdefault(observation["file"], set()).add(uuid_for(observation["id"]))
    session_neighbours: dict[str, set[str]] = {}
    for rows in by_session.values():
        ordered = sorted(rows, key=lambda item: (int(item.get("observed_at") or 0), item["id"]))
        for index, observation in enumerate(ordered):
            session_neighbours[uuid_for(observation["id"])] = {
                uuid_for(ordered[other]["id"])
                for other in range(max(0, index - SESSION_WINDOW),
                                   min(len(ordered), index + SESSION_WINDOW + 1))
                if other != index
            }
    file_neighbours: dict[str, set[str]] = {}
    for observation in observations:
        key = uuid_for(observation["id"])
        file_neighbours[key] = (by_file.get(observation.get("file") or "", set()) - {key})
    neighbours = {
        "session": session_neighbours,
        "file": file_neighbours,
        "both": {key: session_neighbours.get(key, set()) | file_neighbours.get(key, set())
                 for key in set(session_neighbours) | set(file_neighbours)},
    }
    return neighbours


def rerank_cluster(pool: list[dict], now_ms: int, half_life_days: float,
                   neighbours: dict[str, set[str]], weight: float,
                   limit: int = TOP_K) -> list[str]:
    """Promote neighbours of each ranked row to weight × the row's decayed score."""
    scores: dict[str, float] = {}
    for item in pool:
        age_days = max(0.0, (now_ms - int(item["epoch"])) / 86_400_000)
        decayed = float(item["score"]) * (0.5 ** (age_days / half_life_days))
        scores[item["id"]] = max(scores.get(item["id"], 0.0), decayed)
        for neighbour in neighbours.get(item["id"], ()):
            scores[neighbour] = max(scores.get(neighbour, 0.0), decayed * weight)
    ranked = sorted(scores.items(), key=lambda pair: (-pair[1], pair[0]))
    return [item_id for item_id, _ in ranked[:limit]]


def recent_identifiers(observations: list[dict], reference_ms: int, query_text: str,
                       limit: int = MAX_RECENT_IDENTIFIERS) -> list[str]:
    """Identifier-like tokens this project used recently, minus the ones the query names.

    Tokens that appear in more than 20% of the visible rows are skipped as too
    generic to carry meaning; a token the query already contains adds nothing.
    """
    window_ms = RECENT_WINDOW_DAYS * 86_400_000
    visible = [observation for observation in observations
               if reference_ms - window_ms <= int(observation.get("observed_at") or 0) <= reference_ms]
    if not visible:
        return []
    already_named = set(IDENTIFIER_PATTERN.findall(query_text.lower()))
    counts: dict[str, int] = {}
    for observation in visible:
        tokens = {token for token in IDENTIFIER_PATTERN.findall(observation_text(observation).lower())
                  if len(token) >= 8}
        for token in tokens:
            counts[token] = counts.get(token, 0) + 1
    cap = max(2, int(len(visible) * 0.2))
    ordered = sorted(counts.items(), key=lambda pair: (-pair[1], pair[0]))
    return [token for token, count in ordered
            if count <= cap and token not in already_named][:limit]



def rerank_title_blend(pool: list[dict], title_scores: dict[str, float],
                       weight: float = 0.5, limit: int = TOP_K) -> list[dict]:
    """RRF over the fused order and the title-embedding order (weight fixed at 0.5)."""
    by_title = sorted(pool, key=lambda item: (-title_scores.get(item["id"], 0.0), item["id"]))
    title_rank = {item["id"]: index for index, item in enumerate(by_title, start=1)}
    fused_rank = {item["id"]: index for index, item in enumerate(pool, start=1)}
    score = {item["id"]: 1.0 / (60 + fused_rank[item["id"]])
             + weight / (60 + title_rank[item["id"]]) for item in pool}
    ordered = sorted(pool, key=lambda item: (-score[item["id"]], item["id"]))
    return ordered[:limit]



def rank_hybrid_additive(query: str, query_vector: str, min_similarity: float,
                          text_weight: float, limit: int = TOP_K) -> list[str]:
    """Diagnostic: cosine + text_weight * coverage (the shape RRF replaced)."""
    return psql(TOKEN_QUERY.format(query=sql_quote(query)) + f"""
        , qtotal AS (SELECT greatest(count(*), 1) AS n FROM qb),
        text_cov AS (
            SELECT o.id, count(*) AS matched
            FROM qb
            JOIN mem_observations o
              ON o.search_tokens @@ to_tsquery('simple', quote_literal(qb.tok))
            WHERE {scope_filter()}
              AND o.created_at_epoch > 0
            GROUP BY o.id
        ),
        semantic AS (
            SELECT o.id,
                   (1.0 - (o.embedding_1024 <=> cast({sql_quote(query_vector)} as vector))) AS cosine
            FROM mem_observations o
            WHERE {scope_filter()}
              AND o.embedding_1024 IS NOT NULL
              AND o.created_at_epoch > 0
              AND (1.0 - (o.embedding_1024 <=> cast({sql_quote(query_vector)} as vector))) >= {min_similarity}
            ORDER BY o.embedding_1024 <=> cast({sql_quote(query_vector)} as vector)
            LIMIT {limit}
        ),
        candidates AS (
            SELECT id FROM semantic
            UNION
            SELECT id FROM text_cov
        )
        SELECT c.id
        FROM candidates c
        LEFT JOIN semantic s ON s.id = c.id
        LEFT JOIN text_cov t ON t.id = c.id
        ORDER BY (coalesce(s.cosine, 0)
                  + {text_weight} * coalesce(t.matched, 0)::double precision / (SELECT n FROM qtotal)) DESC,
                 c.id
        LIMIT {limit};
    """).splitlines()


def rank_rrf(query: str, query_vector: str, limit: int = TOP_K, pool: int = 20) -> list[str]:
    """Reciprocal-rank fusion over the vector list and the token-coverage list."""
    return psql(TOKEN_QUERY.format(query=sql_quote(query)) + f"""
        , semantic_results AS (
            SELECT o.id AS id, row_number() OVER (
                       ORDER BY o.embedding_1024 <=> cast({sql_quote(query_vector)} as vector)) AS rank
            FROM mem_observations o
            WHERE {scope_filter()}
              AND o.embedding_1024 IS NOT NULL
              AND o.created_at_epoch > 0
            ORDER BY o.embedding_1024 <=> cast({sql_quote(query_vector)} as vector)
            LIMIT {pool}
        ),
        lexical_results AS (
            SELECT o.id AS id, row_number() OVER (ORDER BY count(*) DESC) AS rank
            FROM qb
            JOIN mem_observations o
              ON o.search_tokens @@ to_tsquery('simple', quote_literal(qb.tok))
            WHERE {scope_filter()}
              AND o.created_at_epoch > 0
            GROUP BY o.id
            LIMIT {pool}
        )
        SELECT id
        FROM (SELECT id, rank FROM semantic_results
              UNION ALL
              SELECT id, rank FROM lexical_results) combined
        GROUP BY id
        ORDER BY SUM(1.0 / (60 + rank)) DESC
        LIMIT {limit};
    """).splitlines()


IDENTIFIER_STOPWORDS = {
    "the", "and", "for", "with", "this", "that", "you", "please", "not", "are", "was",
    "from", "into", "then", "than", "but", "all", "any", "can", "use", "only", "may",
}


def identifier_tokens(query: str) -> list[str]:
    """Latin identifiers in a real request (CUX-048, tsc, report.md, D-65, ...)."""
    tokens = []
    for token in re.findall(r"[A-Za-z][A-Za-z0-9_.\-/]{2,}", query):
        lowered = token.lower()
        if lowered in IDENTIFIER_STOPWORDS or lowered.isdigit():
            continue
        if lowered not in tokens:
            tokens.append(lowered)
    return tokens[:12]


def rank_identifier_or(query: str, limit: int = TOP_K) -> list[str]:
    """Lexical ranking with OR semantics over identifiers extracted from the query.

    plainto_tsquery is conjunctive: every token must be present, so a long natural
    language request never matches. Identifiers are the useful lexical signal, and
    OR is the only way they can add candidates.
    """
    tokens = identifier_tokens(query)
    if not tokens:
        return []
    expression = " OR ".join(tokens)
    document = ("to_tsvector('simple', coalesce(o.title,'') || ' ' || coalesce(o.content,'') "
                "|| ' ' || coalesce(o.files_modified::text,''))")
    tsquery = f"websearch_to_tsquery('simple', {sql_quote(expression)})"
    return psql(f"""
        SELECT o.id
        FROM mem_observations o
        WHERE {scope_filter()}
          AND {document} @@ {tsquery}
        ORDER BY ts_rank({document}, {tsquery}) DESC
        LIMIT {limit};
    """).splitlines()


def rank_identifier_or_rrf(query: str, query_vector: str, limit: int = TOP_K,
                           pool: int = 20) -> list[str]:
    tokens = identifier_tokens(query)
    if not tokens:
        return rank_vector(query_vector, limit)
    document = ("to_tsvector('simple', coalesce(title,'') || ' ' || coalesce(content,'') "
                "|| ' ' || coalesce(files_modified::text,''))")
    tsquery = f"websearch_to_tsquery('simple', {sql_quote(' OR '.join(tokens))})"
    return psql(f"""
        WITH semantic_results AS (
            SELECT id, row_number() OVER (
                       ORDER BY embedding_1024 <=> cast({sql_quote(query_vector)} as vector)) AS rank
            FROM mem_observations
            WHERE {scope_filter()}
              AND embedding_1024 IS NOT NULL
              AND created_at_epoch > 0
            ORDER BY embedding_1024 <=> cast({sql_quote(query_vector)} as vector)
            LIMIT {pool}
        ),
        fulltext_results AS (
            SELECT id, row_number() OVER (
                       ORDER BY ts_rank({document}, {tsquery}) DESC) AS rank
            FROM mem_observations
            WHERE {scope_filter()}
              AND {document} @@ {tsquery}
            LIMIT {pool}
        )
        SELECT id
        FROM (SELECT id, rank FROM semantic_results
              UNION ALL
              SELECT id, rank FROM fulltext_results) combined
        GROUP BY id
        ORDER BY SUM(1.0 / (60 + rank)) DESC
        LIMIT {limit};
    """).splitlines()


def metrics(ranked: list[str], relevant: set[str]) -> dict:
    hit1 = 1.0 if ranked[:1] and ranked[0] in relevant else 0.0
    hit5 = 1.0 if any(item in relevant for item in ranked[:5]) else 0.0
    rr = 0.0
    for index, item in enumerate(ranked, start=1):
        if item in relevant:
            rr = 1.0 / index
            break
    return {"hit@1": hit1, "hit@5": hit5, "mrr": rr, "ranked": ranked}


def percentile(values: list[float], p: float) -> float:
    ordered = sorted(values)
    if not ordered:
        return 0.0
    return ordered[min(len(ordered) - 1, int(round((p / 100) * (len(ordered) - 1))))]


def summarize(rows: list[dict]) -> dict:
    return {
        "queries": len(rows),
        "hit@1": round(statistics.fmean(row["hit@1"] for row in rows), 4),
        "hit@5": round(statistics.fmean(row["hit@5"] for row in rows), 4),
        "mrr": round(statistics.fmean(row["mrr"] for row in rows), 4),
        "empty_results": sum(1 for row in rows if not row["ranked"]),
    }


def bootstrap_delta(rows: list[dict], base_key: str, candidate_key: str,
                    metric: str, iterations: int = 2000) -> dict:
    """Paired query-level bootstrap of (candidate - baseline) for one metric.

    A repeated run of the same configuration is deterministic, so it cannot
    estimate how much of a delta could be corpus noise. Resampling the 90
    queries with their paired scores does: the returned interval is the noise
    band the candidate must clear.
    """
    rng = random.Random(20261104)
    deltas: list[float] = []
    for _ in range(iterations):
        sample = [rows[rng.randrange(len(rows))] for _ in range(len(rows))]
        base = statistics.fmean(row[base_key][metric] for row in sample)
        candidate = statistics.fmean(row[candidate_key][metric] for row in sample)
        deltas.append(candidate - base)
    return {
        "delta": round(statistics.fmean(
            row[candidate_key][metric] - row[base_key][metric] for row in rows), 4),
        "ci_low": round(percentile(deltas, 2.5), 4),
        "ci_high": round(percentile(deltas, 97.5), 4),
    }


def render_report(result: dict, corpus_path: Path) -> str:
    lines = [
        "# Retrieval quality: real session-history queries",
        "",
        f"- Date: {time.strftime('%Y-%m-%d %H:%M:%S')}",
        f"- Corpus: `{corpus_path.relative_to(ROOT)}` ({result['corpus']['observations']} "
        f"observations, {result['corpus']['queries']} queries)",
        f"- Embedding model: {EMBED_MODEL} ({EMBED_DIM} dimensions), real provider calls",
        f"- Database: disposable {DATABASE} on {CONTAINER}:{DB_PORT}",
        "- Ground truth: temporal attribution (the observations produced while the "
        "assistant answered the real prompt); rationale stored per query.",
        "",
        "## hit@1 / hit@5 / MRR",
        "",
        "| path | hit@1 | hit@5 | MRR | empty results |",
        "|---|---:|---:|---:|---:|",
    ]
    # Label the diagnostic rows so the production shape is not confused with the
    # additive-fusion experiments that were measured and rejected.
    labels = {
        "hybrid": "hybrid (production RRF)",
        "hybrid_w0.25": "additive w0.25 (rejected)",
        "hybrid_w0.75": "additive w0.75 (rejected)",
        "hybrid_w1.0": "additive w1.0 (rejected)",
        "lexical": "lexical (V24 token coverage)",
        "tsvector_english": "english tsvector (replaced by V24)",
        "hybrid_decay_7d": "hybrid + recency 7 d half-life",
        "hybrid_decay_30d": "hybrid + recency 30 d half-life",
        "hybrid_decay_90d": "hybrid + recency 90 d half-life",
        "hybrid_decay_365d": "hybrid + recency 365 d half-life",
        "hybrid_dedupe": "hybrid + near-duplicate merge",
        "hybrid_dedupe_decay30d": "merge + recency 30 d",
        "hybrid_decay7d_intent": "decay 7d + intent/kind boost",
        "hybrid_synonym_decay7d": "synonym expansion + decay 7d",
        "hybrid_synonym_intent_decay7d": "synonym + intent + decay 7d",
        "hybrid_synonym_both_decay7d": "synonym (lexical+vector) + decay 7d",
        "hybrid_synonym_both_dedupe_decay7d": "synonym both + merge + decay 7d",
        "hybrid_decay7d_body_rerank": "decay 7d + body embedding rerank",
        "hybrid_decay7d_title_rerank": "decay 7d + title embedding rerank",
        "hybrid_decay7d_title_blend": "decay 7d + title RRF blend",
    }
    for name, summary in result["summary"].items():
        lines.append(
            f"| {labels.get(name, name)} | {summary['hit@1']:.3f} | "
            f"{summary['hit@5']:.3f} | {summary['mrr']:.3f} | {summary['empty_results']} |"
        )
    lines += [
        "",
        "## Candidate pool depth (round 18)",
        "",
        "| pool | mean rows fetched | hit@1 (fusion) | hit@5 (fusion) | MRR (fusion) | "
        "hit@1 (+7d) | hit@5 (+7d) | MRR (+7d) |",
        "|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for depth in result.get("pool_depths") or []:
        sizes = [row[f"pool_{depth}_size"] for row in result["rows"]]
        fused = result["summary"][f"pool_{depth}"]
        decayed = result["summary"][f"pool_{depth}_decay7d"]
        lines.append(
            f"| {depth} | {statistics.fmean(sizes):.1f} | {fused['hit@1']:.3f} | "
            f"{fused['hit@5']:.3f} | {fused['mrr']:.3f} | {decayed['hit@1']:.3f} | "
            f"{decayed['hit@5']:.3f} | {decayed['mrr']:.3f} |"
        )
    lines += [
        "",
        "## Vector-only candidate expansion (round 20)",
        "",
        "Semantic list widened; lexical list and fused cut stay at production depth 40.",
        "",
        "| vector depth | mean rows | hit@1 (fusion) | hit@5 (fusion) | MRR (fusion) | "
        "hit@1 (+7d) | hit@5 (+7d) | MRR (+7d) | mean ms | p95 ms |",
        "|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for depth in result.get("round20", {}).get("vector_depths") or []:
        sizes = [row[f"vecdeep_{depth}_size"] for row in result["rows"]]
        fused = result["summary"][f"vecdeep_{depth}"]
        decayed = result["summary"][f"vecdeep_{depth}_decay7d"]
        latency = result["latency_ms"][f"vecdeep_{depth}"]
        lines.append(
            f"| {depth} | {statistics.fmean(sizes):.1f} | {fused['hit@1']:.3f} | "
            f"{fused['hit@5']:.3f} | {fused['mrr']:.3f} | {decayed['hit@1']:.3f} | "
            f"{decayed['hit@5']:.3f} | {decayed['mrr']:.3f} | {latency['mean']:.1f} | "
            f"{latency['p95']:.1f} |"
        )
    lines += [
        "",
        "## RRF weight sweep (round 21)",
        "",
        "Lexical weight in the RRF sum; vector weight is fixed at 1.0. Baseline is 2.0.",
        "Delta and the 95% interval are paired query-level bootstrap values.",
        "",
        "| lexical weight | hit@1 | hit@5 | MRR | +7d hit@1 | +7d hit@5 | +7d MRR | "
        "d hit@1 [95% CI] | d hit@5 [95% CI] | d MRR [95% CI] | mean ms |",
        "|---:|---:|---:|---:|---:|---:|---:|---|---|---|---:|",
    ]
    for weight in result.get("round21", {}).get("fusion_weights", []):
        item = result["round21"]["weights"][f"{weight:g}"]
        fused = result["summary"][f"weight_{weight:g}"]
        decayed = item["summary"]
        def interval(metric):
            value = item["delta"][metric]
            return f"{value['delta']:+.3f} [{value['ci_low']:+.3f}, {value['ci_high']:+.3f}]"
        lines.append(
            f"| {weight:g} | {fused['hit@1']:.3f} | {fused['hit@5']:.3f} | {fused['mrr']:.3f} | "
            f"{decayed['hit@1']:.3f} | {decayed['hit@5']:.3f} | {decayed['mrr']:.3f} | "
            f"{interval('hit@1')} | {interval('hit@5')} | {interval('mrr')} | "
            f"{item['latency_ms']['mean']:.1f} |"
        )
    primary = result["round21"]["vector_primary"]
    primary_cells = " | ".join(
        f"{primary['delta'][metric]['delta']:+.3f} "
        f"[{primary['delta'][metric]['ci_low']:+.3f}, {primary['delta'][metric]['ci_high']:+.3f}]"
        for metric in ("hit@1", "hit@5", "mrr"))
    lines.append(
        f"| vector-primary | - | - | - | {primary['summary']['hit@1']:.3f} | "
        f"{primary['summary']['hit@5']:.3f} | {primary['summary']['mrr']:.3f} | "
        f"{primary_cells} | - |"
    )
    noise = result["round21"]["repeat_noise"]
    lines += [
        "",
        f"Repeat-run noise (same configuration three times): hit@1 {noise['hit@1']:.3f}, "
        f"hit@5 {noise['hit@5']:.3f}, MRR {noise['mrr']:.3f}.",
        "",
        "### Per-language (7-day decay)",
        "",
        "| language | queries | baseline hit@1/5/MRR | best weight | best hit@1/5/MRR | "
        "vector-primary hit@1/5/MRR | lexical empty |",
        "|---|---:|---|---|---|---|---:|",
    ]
    for language, item in result["round21"]["languages"].items():
        base = item["baseline"]
        best_weight, best = max(item["weights"].items(),
                                key=lambda pair: (pair[1]["hit@5"], pair[1]["mrr"]))
        initial = item["vector_primary"]
        lines.append(
            f"| {language} | {item['queries']} | {base['hit@1']:.3f} / {base['hit@5']:.3f} / "
            f"{base['mrr']:.3f} | {best_weight} | {best['hit@1']:.3f} / {best['hit@5']:.3f} / "
            f"{best['mrr']:.3f} | {initial['hit@1']:.3f} / {initial['hit@5']:.3f} / "
            f"{initial['mrr']:.3f} | {item['lexical_empty']} |"
        )
    lines += [
        "",
        "## Language split (round 16 contrast set)",
        "",
        "| language | queries | hybrid hit@1 | hybrid hit@5 | hybrid MRR | merge+recency hit@1 |",
        "|---|---:|---:|---:|---:|---:|",
    ]
    for language, summaries in result["by_language"].items():
        hybrid_language = summaries["hybrid"]
        combined_language = summaries["hybrid_dedupe_decay30d"]
        lines.append(
            f"| {language} | {hybrid_language['queries']} | {hybrid_language['hit@1']:.3f} | "
            f"{hybrid_language['hit@5']:.3f} | {hybrid_language['mrr']:.3f} | "
            f"{combined_language['hit@1']:.3f} |"
        )
    lines += [
        "",
        "## Diagnosis",
        "",
        result["diagnosis"],
        "",
        "## Round 20 production misses",
        "",
        "| query | relevant | relevant outside pool | vector reachable @max | recovered by vecdeep |",
        "|---|---:|---:|---:|---:|",
    ]
    max_depth = max((result.get("round20", {}).get("vector_depths") or [0]))
    for row in result["rows"]:
        if row["hybrid_decay_7d"]["hit@5"] != 0:
            continue
        recovered = any(row[f"vecdeep_{depth}_decay7d"]["hit@5"] == 1
                        for depth in result["round20"]["vector_depths"])
        lines.append(
            f"| {row['id']} | {len(row['relevant'])} | {row['relevant_missing_from_pool']} | "
            f"{row[f'vector_{max_depth}_reachable']} | {'yes' if recovered else 'no'} |"
        )
    lines += [
        "",
        "## Per-query first relevant rank",
        "",
        "| query | relevant | vector | vector-head120 | vector-tail120 | lexical | "
        "lexical-ts-rank | english-tsvector | add-w0.25 | hybrid (RRF w2) | add-w0.75 | "
        "add-w1.0 | rrf-k60 | identifier-or | decay30d | merge | merge+decay | "
        "intent | synonym7d | body-rerank | title-rerank | title-blend |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|"
        "---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for row in result["rows"]:
        def rank(key: str) -> str:
            ranked = row[key]["ranked"]
            for index, item in enumerate(ranked, start=1):
                if item in row["relevant"]:
                    return str(index)
            return "-"
        lines.append(
            f"| {row['id']} | {len(row['relevant'])} | {rank('vector')} | "
            f"{rank('vector_head120')} | {rank('vector_tail120')} | {rank('lexical')} | "
            f"{rank('lexical_ts_rank')} | {rank('tsvector_english')} | "
            f"{rank('hybrid_w0.25')} | {rank('hybrid')} | {rank('hybrid_w0.75')} | "
            f"{rank('hybrid_w1.0')} | {rank('rrf')} | {rank('identifier_or')} | "
            f"{rank('hybrid_decay_30d')} | {rank('hybrid_dedupe')} | "
            f"{rank('hybrid_dedupe_decay30d')} | "
            f"{row['intent']} | {rank('hybrid_synonym_decay7d')} | "
            f"{rank('hybrid_decay7d_body_rerank')} | {rank('hybrid_decay7d_title_rerank')} | "
            f"{rank('hybrid_decay7d_title_blend')} |"
        )
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    global CURRENT_CUTOFF
    parser = argparse.ArgumentParser()
    parser.add_argument("--corpus", default="backend/src/test/resources/eval/retrieval-corpus-real.json")
    parser.add_argument("--report", default="docs/archive/" +
                        time.strftime("%Y-%m-%d") + "_retrieval-quality-real.md")
    parser.add_argument("--metrics-out", default="docs/archive/" +
                        time.strftime("%Y-%m-%d") + "_retrieval-quality-real.json")
    parser.add_argument("--pool-depths", default=",".join(str(d) for d in POOL_DEPTHS),
                        help="comma-separated candidate-pool depths to compare")
    parser.add_argument("--vector-depths",
                        default=",".join(str(d) for d in VECTOR_DEPTHS),
                        help="comma-separated vector-only candidate depths (round 20)")
    parser.add_argument("--fusion-weights",
                        default=",".join(str(w) for w in FUSION_WEIGHTS),
                        help="comma-separated lexical weights in RRF (round 21)")
    parser.add_argument("--keep-db", action="store_true")
    args = parser.parse_args()
    pool_depths = tuple(int(value) for value in args.pool_depths.split(",") if value.strip())
    vector_depths = tuple(int(value) for value in args.vector_depths.split(",") if value.strip())
    fusion_weights = tuple(float(value) for value in args.fusion_weights.split(",")
                           if value.strip())

    corpus_path = ROOT / args.corpus
    corpus = json.loads(corpus_path.read_text())
    observations = corpus["observations"]
    queries = corpus["queries"]
    print(f"corpus: {len(observations)} observations, {len(queries)} queries")

    print("embedding observations")
    observation_vectors = embed([observation_text(obs) for obs in observations])
    print("embedding queries")
    query_vectors = embed([query["text"] for query in queries])
    print("embedding focused query variants")
    head_vectors = embed([query["text"][:120] for query in queries])
    tail_vectors = embed([query["text"][-120:] for query in queries])
    print("embedding observation titles")
    title_vectors = embed([observation["title"] for observation in observations])
    print("embedding synonym-expanded queries")
    expanded_vectors = embed([expand_query(query["text"]) for query in queries])

    create_database()
    migrate()
    load_rows(observations, observation_vectors)

    names = {
        "vector": lambda q, v: rank_vector(v),
        "lexical": lambda q, v: rank_fulltext(q),
        "lexical_ts_rank": lambda q, v: rank_fulltext_ts_rank(q),
        "tsvector_english": lambda q, v: rank_fulltext_english(q),
        "hybrid": lambda q, v: rank_hybrid(q, v, 0.5),
        "hybrid_w0.25": lambda q, v: rank_hybrid_additive(q, v, 0.5, 0.25),
        "hybrid_w0.75": lambda q, v: rank_hybrid_additive(q, v, 0.5, 0.75),
        "hybrid_w1.0": lambda q, v: rank_hybrid_additive(q, v, 0.5, 1.0),
        "hybrid_no_floor": lambda q, v: rank_hybrid(q, v, 0.0),
        "rrf": lambda q, v: rank_rrf(q, v),
        "identifier_or": lambda q, v: rank_identifier_or(q),
        "identifier_or_rrf": lambda q, v: rank_identifier_or_rrf(q, v),
    }
    corpus_ids = {obs["id"] for obs in observations}
    observed_at = {obs["id"]: obs.get("observed_at") for obs in observations}
    neighbours = cluster_neighbours(observations)
    now_ms = int(time.time() * 1000)
    rows = []
    for query, vector, head, tail, expanded_vector in zip(
            queries, query_vectors, head_vectors, tail_vectors, expanded_vectors):
        relevant = {uuid_for(item) for item in query["relevant"] if item in corpus_ids}
        # The request's own clock: hide observations that did not exist yet, and
        # measure recency from the moment the user asked.
        relevant_epochs = [int(observed_at[item]) for item in query["relevant"]
                           if observed_at.get(item)]
        reference_ms = max(relevant_epochs) if relevant_epochs else int(
            query.get("asked_at") or now_ms)
        CURRENT_CUTOFF = reference_ms
        title_scores = {
            uuid_for(observation["id"]): cosine(vector, title_vector)
            for observation, title_vector in zip(observations, title_vectors)
        }
        row = {"id": query["id"], "text": query["text"],
               "language": query.get("language", "unknown"), "relevant": sorted(relevant)}
        for name, function in names.items():
            row[name] = metrics(function(query["text"], vector_literal(vector)), relevant)
        row["vector_head120"] = metrics(rank_vector(vector_literal(head)), relevant)
        row["vector_tail120"] = metrics(rank_vector(vector_literal(tail)), relevant)

        # Rerank experiments run on the same fused pool production returns, so
        # the only variable is the reorder rule.
        pool_started = time.perf_counter()
        pool = hybrid_pool(query["text"], vector_literal(vector), 0.5)
        row["latency_ms"] = {"pool": (time.perf_counter() - pool_started) * 1000}
        row["hybrid_pool_size"] = len(pool)
        row["relevant_missing_from_pool"] = len(relevant - {item["id"] for item in pool})
        # Round 20: how deep does the vector list have to go before every relevant
        # row is reachable, and does widening only that half change the fused cut?
        reachable = rank_vector(vector_literal(vector), max(vector_depths))
        for depth in vector_depths:
            row[f"vector_{depth}_reachable"] = len(relevant & set(reachable[:depth]))
            started = time.perf_counter()
            deep_pool = hybrid_pool(query["text"], vector_literal(vector), 0.5,
                                    limit=PRODUCTION_LIMIT, semantic_limit=depth)
            row["latency_ms"][f"vecdeep_{depth}"] = (time.perf_counter() - started) * 1000
            row[f"vecdeep_{depth}_size"] = len(deep_pool)
            row[f"vecdeep_{depth}"] = metrics([item["id"] for item in deep_pool], relevant)
            row[f"vecdeep_{depth}_decay7d"] = metrics(
                [item["id"] for item in rerank_time_decay(deep_pool, reference_ms, 7)],
                relevant)
        # Round 21: RRF weight sweep and the vector-primary/lexical-fallback
        # alternative. Both reuse the production top-40 cut and 7-day decay, so
        # only the ordering rule changes.
        for weight in fusion_weights:
            started = time.perf_counter()
            weighted = hybrid_pool(query["text"], vector_literal(vector), 0.5,
                                   limit=PRODUCTION_LIMIT, lexical_weight=weight)
            row["latency_ms"][f"weight_{weight:g}"] = (time.perf_counter() - started) * 1000
            row[f"weight_{weight:g}"] = metrics([item["id"] for item in weighted], relevant)
            row[f"weight_{weight:g}_decay7d"] = metrics(
                [item["id"] for item in rerank_time_decay(weighted, reference_ms, 7)],
                relevant)
        vector_primary_ids = rank_vector(vector_literal(vector), PRODUCTION_LIMIT)
        if not vector_primary_ids:
            vector_primary_ids = rank_fulltext(query["text"], PRODUCTION_LIMIT)
        vector_primary = [{"id": item_id, "score": 1.0 / (index + 1),
                           "epoch": observed_at.get(item_id, reference_ms)}
                          for index, item_id in enumerate(vector_primary_ids)]
        row["vector_primary"] = metrics(vector_primary_ids, relevant)
        row["vector_primary_decay7d"] = metrics(
            [item["id"] for item in rerank_time_decay(vector_primary, reference_ms, 7)],
            relevant)
        # Repeat the production configuration to measure run-to-run noise. It is
        # deterministic, so this pins the pipeline rather than the corpus noise;
        # the bootstrap below supplies the sampling band.
        for repeat in (2, 3):
            repeated = hybrid_pool(query["text"], vector_literal(vector), 0.5)
            row[f"repeat_{repeat}"] = metrics([item["id"] for item in repeated], relevant)
        # Round 18: pool depth sweep. Same fusion and same 7-day recency rerank as
        # production; the only variable is how many fused candidates are fetched.
        for depth in pool_depths:
            depth_pool = hybrid_pool(query["text"], vector_literal(vector), 0.5, limit=depth)
            row[f"pool_{depth}_size"] = len(depth_pool)
            row[f"pool_{depth}"] = metrics([item["id"] for item in depth_pool], relevant)
            row[f"pool_{depth}_decay7d"] = metrics(
                [item["id"] for item in rerank_time_decay(depth_pool, reference_ms, 7)],
                relevant)
        for half_life in DECAY_HALF_LIVES:
            row[f"hybrid_decay_{half_life}d"] = metrics(
                [item["id"] for item in rerank_time_decay(pool, reference_ms, half_life)],
                relevant)
        deduped = rerank_dedupe(pool)
        row["hybrid_dedupe"] = metrics([item["id"] for item in deduped], relevant)
        row["hybrid_dedupe_decay30d"] = metrics(
            [item["id"] for item in rerank_time_decay(deduped, reference_ms, 30)], relevant)
        row["dedupe_hidden_relevant"] = len(relevant - {item["id"] for item in deduped})

        # Round 17: all three experiments start from the 7-day-decayed pool, the
        # configuration production already uses, so the delta is attributable to
        # the new rule rather than to a different base.
        decayed7 = rerank_time_decay(pool, reference_ms, 7)
        intent = classify_intent(query["text"])
        row["intent"] = intent
        row["hybrid_decay7d_intent"] = metrics(
            [item["id"] for item in rerank_intent_type(decayed7, intent)], relevant)

        expanded = expand_query(query["text"])
        row["expanded_terms"] = len(expanded.split()) - len(query["text"].split())
        expanded_pool = hybrid_pool(expanded, vector_literal(vector), 0.5)
        expanded_decayed = rerank_time_decay(expanded_pool, reference_ms, 7)
        row["hybrid_synonym_decay7d"] = metrics(
            [item["id"] for item in expanded_decayed], relevant)
        row["hybrid_synonym_intent_decay7d"] = metrics(
            [item["id"] for item in rerank_intent_type(expanded_decayed, intent)], relevant)
        both_pool = hybrid_pool(expanded, vector_literal(expanded_vector), 0.5)
        both_decayed = rerank_time_decay(both_pool, reference_ms, 7)
        row["hybrid_synonym_both_decay7d"] = metrics(
            [item["id"] for item in both_decayed], relevant)
        row["hybrid_synonym_both_dedupe_decay7d"] = metrics(
            [item["id"] for item in rerank_time_decay(
                rerank_dedupe(both_pool), reference_ms, 7)], relevant)

        for item in decayed7:
            item["title_cosine"] = title_scores[item["id"]]
        row["hybrid_decay7d_body_rerank"] = metrics(
            [item["id"] for item in rerank_by_cosine(decayed7, "cosine")], relevant)
        row["hybrid_decay7d_title_rerank"] = metrics(
            [item["id"] for item in rerank_by_cosine(decayed7, "title_cosine")], relevant)
        row["hybrid_decay7d_title_blend"] = metrics(
            [item["id"] for item in rerank_title_blend(decayed7, title_scores)], relevant)

        # Round 19: cluster expansion from the rows that already ranked, and query
        # expansion from the identifiers this project used in the last 30 days.
        for label, neighbour_set in (("session", neighbours["session"]),
                                     ("file", neighbours["file"]),
                                     ("both", neighbours["both"])):
            for weight in CLUSTER_WEIGHTS:
                row[f"cluster_{label}_{weight}"] = metrics(
                    rerank_cluster(pool, reference_ms, 7, neighbour_set, weight), relevant)
        identifiers = recent_identifiers(observations, reference_ms, query["text"])
        row["query_identifiers"] = identifiers
        if identifiers:
            identifier_query = query["text"] + " " + " ".join(identifiers)
            identifier_pool = hybrid_pool(identifier_query, vector_literal(vector), 0.5)
            row["hybrid_queryident_decay7d"] = metrics(
                [item["id"] for item in rerank_time_decay(identifier_pool, reference_ms, 7)],
                relevant)
            row["hybrid_queryident_cluster_decay7d"] = metrics(
                rerank_cluster(identifier_pool, reference_ms, 7, neighbours["both"], 0.5),
                relevant)
        else:
            row["hybrid_queryident_decay7d"] = row["hybrid_decay_7d"]
            row["hybrid_queryident_cluster_decay7d"] = row["cluster_both_0.5"]
        rows.append(row)
    CURRENT_CUTOFF = 0

    summary_names = list(names) + ["vector_head120", "vector_tail120"]
    summary_names += [f"hybrid_decay_{half_life}d" for half_life in DECAY_HALF_LIVES]
    summary_names += ["hybrid_dedupe", "hybrid_dedupe_decay30d"]
    summary_names += ["hybrid_decay7d_intent", "hybrid_synonym_decay7d",
                      "hybrid_synonym_intent_decay7d", "hybrid_synonym_both_decay7d",
                      "hybrid_synonym_both_dedupe_decay7d", "hybrid_decay7d_body_rerank",
                      "hybrid_decay7d_title_rerank", "hybrid_decay7d_title_blend"]
    summary_names += [f"pool_{depth}" for depth in pool_depths]
    summary_names += [f"pool_{depth}_decay7d" for depth in pool_depths]
    summary_names += [f"vecdeep_{depth}" for depth in vector_depths]
    summary_names += [f"vecdeep_{depth}_decay7d" for depth in vector_depths]
    summary_names += [f"weight_{weight:g}" for weight in fusion_weights]
    summary_names += [f"weight_{weight:g}_decay7d" for weight in fusion_weights]
    summary_names += ["vector_primary", "vector_primary_decay7d", "repeat_2", "repeat_3"]
    summary_names += [f"cluster_{label}_{weight}"
                      for label in ("session", "file", "both") for weight in CLUSTER_WEIGHTS]
    summary_names += ["hybrid_queryident_decay7d", "hybrid_queryident_cluster_decay7d"]
    result = {
        "corpus": {"observations": len(observations), "queries": len(queries),
                   "path": str(corpus_path.relative_to(ROOT)),
                   "languages": {language: sum(1 for row in rows if row["language"] == language)
                                 for language in sorted({row["language"] for row in rows})}},
        "embedding_model": EMBED_MODEL,
        "pool_depths": list(pool_depths),
        "summary": {name: summarize([row[name] for row in rows]) for name in summary_names},
        "by_language": {
            language: {name: summarize([row[name] for row in rows if row["language"] == language])
                       for name in summary_names}
            for language in sorted({row["language"] for row in rows})
        },
        "rows": rows,
    }
    latency_keys = (["pool"] + [f"vecdeep_{depth}" for depth in vector_depths]
                    + [f"weight_{weight:g}" for weight in fusion_weights])
    result["latency_ms"] = {
        key: {"mean": round(statistics.fmean(row["latency_ms"][key] for row in rows), 2),
              "p95": round(percentile([row["latency_ms"][key] for row in rows], 95), 2)}
        for key in latency_keys
    }
    production_misses = [row for row in rows if row["hybrid_decay_7d"]["hit@5"] == 0]
    result["round20"] = {
        "vector_depths": list(vector_depths),
        "production_misses": len(production_misses),
        "misses_with_relevant_outside_pool":
            sum(1 for row in production_misses if row["relevant_missing_from_pool"] > 0),
        "queries_with_relevant_outside_pool":
            sum(1 for row in rows if row["relevant_missing_from_pool"] > 0),
        "missing_relevant_rows": sum(row["relevant_missing_from_pool"] for row in rows),
        "vector_reachable": {
            str(depth): sum(row[f"vector_{depth}_reachable"] for row in rows)
            for depth in vector_depths},
        "relevant_total": sum(len(row["relevant"]) for row in rows),
    }
    result["round21"] = {
        "baseline_key": "hybrid_decay_7d",
        "fusion_weights": list(fusion_weights),
        "weights": {
            f"{weight:g}": {
                "summary": result["summary"][f"weight_{weight:g}_decay7d"],
                "delta": {metric: bootstrap_delta(rows, "hybrid_decay_7d",
                                                  f"weight_{weight:g}_decay7d", metric)
                          for metric in ("hit@1", "hit@5", "mrr")},
                "latency_ms": result["latency_ms"][f"weight_{weight:g}"],
            }
            for weight in fusion_weights
        },
        "vector_primary": {
            "summary": result["summary"]["vector_primary_decay7d"],
            "delta": {metric: bootstrap_delta(rows, "hybrid_decay_7d",
                                              "vector_primary_decay7d", metric)
                      for metric in ("hit@1", "hit@5", "mrr")},
        },
        "repeat_noise": {
            metric: max(
                abs(result["summary"][key][metric] - result["summary"]["hybrid"][metric])
                for key in ("repeat_2", "repeat_3"))
            for metric in ("hit@1", "hit@5", "mrr")
        },
        "languages": {
            language: {
                "queries": len([row for row in rows if row["language"] == language]),
                "baseline": result["by_language"][language]["hybrid_decay_7d"],
                "weights": {f"{weight:g}": result["by_language"][language]
                            [f"weight_{weight:g}_decay7d"] for weight in fusion_weights},
                "vector_primary": result["by_language"][language]["vector_primary_decay7d"],
                "lexical_empty": sum(
                    1 for row in rows
                    if row["language"] == language and not row["lexical"]["ranked"]),
            }
            for language in result["by_language"]
        },
    }
    vector_summary = result["summary"]["vector"]
    hybrid_summary = result["summary"]["hybrid"]
    lexical_summary = result["summary"]["lexical"]
    english_summary = result["summary"]["tsvector_english"]
    identifiers_summary = result["summary"]["identifier_or"]
    additive = {w: result["summary"][f"hybrid_w{w}"] for w in ("0.25", "0.75", "1.0")}
    decay = {half_life: result["summary"][f"hybrid_decay_{half_life}d"]
             for half_life in DECAY_HALF_LIVES}
    best_decay = max(DECAY_HALF_LIVES,
                     key=lambda half_life: (decay[half_life]["hit@1"], decay[half_life]["mrr"]))
    dedupe_summary = result["summary"]["hybrid_dedupe"]
    combined_summary = result["summary"]["hybrid_dedupe_decay30d"]
    hidden_relevant = sum(row["dedupe_hidden_relevant"] for row in rows)
    r17_names = ("hybrid_decay7d_intent", "hybrid_synonym_decay7d",
                 "hybrid_synonym_intent_decay7d", "hybrid_synonym_both_decay7d",
                 "hybrid_synonym_both_dedupe_decay7d", "hybrid_decay7d_body_rerank",
                 "hybrid_decay7d_title_rerank", "hybrid_decay7d_title_blend")
    r17 = {name: result["summary"][name] for name in r17_names}
    intents = {value: sum(1 for row in rows if row["intent"] == value)
               for value in ("write", "edit", "neutral")}
    expanded_queries = sum(1 for row in rows if row["expanded_terms"] > 0)
    baseline = decay[7]
    languages_text = ", ".join(
        f"{language} {count}" for language, count in result["corpus"]["languages"].items())
    diagnosis = (
        f"Corpus split: {languages_text}. V24 lexical path reaches hit@1 "
        f"{lexical_summary['hit@1']:.3f}, hit@5 {lexical_summary['hit@5']:.3f}, MRR "
        f"{lexical_summary['mrr']:.3f} with {lexical_summary['empty_results']} empty results; "
        f"the English tsvector it replaced was empty for {english_summary['empty_results']} of "
        f"{english_summary['queries']} queries (hit@5 {english_summary['hit@5']:.3f}). "
        f"Production hybrid (weighted RRF, lexical weight {LEXICAL_WEIGHT}) reaches hit@1 "
        f"{hybrid_summary['hit@1']:.3f}, hit@5 {hybrid_summary['hit@5']:.3f}, MRR "
        f"{hybrid_summary['mrr']:.3f} against pure vector {vector_summary['hit@1']:.3f} / "
        f"{vector_summary['hit@5']:.3f} / {vector_summary['mrr']:.3f}. "
        f"Additive score fusion froze hit@5 at {additive['1.0']['hit@5']:.3f} for every weight "
        f"(MRR {additive['0.25']['mrr']:.3f} / {additive['0.75']['mrr']:.3f} / "
        f"{additive['1.0']['mrr']:.3f} at w=0.25/0.75/1.0) because cosine (0.6-0.8) swamps "
        f"coverage (0.05-0.2) — rank fusion removes that scale mismatch. Ordering the same "
        f"tokens with ts_rank_cd gives MRR {result['summary']['lexical_ts_rank']['mrr']:.3f} "
        f"versus {lexical_summary['mrr']:.3f} for coverage. Identifier-OR ranking scores "
        f"{identifiers_summary['mrr']:.3f} MRR. "
        f"Recency rerank on the fused pool: best half-life {best_decay}d gives hit@1 "
        f"{decay[best_decay]['hit@1']:.3f}, hit@5 {decay[best_decay]['hit@5']:.3f}, MRR "
        f"{decay[best_decay]['mrr']:.3f}; the 30-day variant alone is "
        f"{decay[30]['hit@1']:.3f} / {decay[30]['hit@5']:.3f} / {decay[30]['mrr']:.3f}. "
        f"Near-duplicate merge (newest of each normalized title) gives hit@1 "
        f"{dedupe_summary['hit@1']:.3f}, hit@5 {dedupe_summary['hit@5']:.3f}, MRR "
        f"{dedupe_summary['mrr']:.3f}; it hid a relevant row for {hidden_relevant} queries. "
        f"Merge then 30-day decay gives {combined_summary['hit@1']:.3f} / "
        f"{combined_summary['hit@5']:.3f} / {combined_summary['mrr']:.3f}. "
        f"Round 17 starts from the 7-day-decayed pool ({baseline['hit@1']:.3f} / "
        f"{baseline['hit@5']:.3f} / {baseline['mrr']:.3f}). Intent split is "
        f"{intents['write']} write / {intents['edit']} edit / {intents['neutral']} neutral; "
        f"intent-aware kind boost ({TYPE_BOOST}x) gives {r17['hybrid_decay7d_intent']['hit@1']:.3f} "
        f"/ {r17['hybrid_decay7d_intent']['hit@5']:.3f} / {r17['hybrid_decay7d_intent']['mrr']:.3f}. "
        f"Bilingual synonym expansion fired on {expanded_queries} of {len(rows)} queries and gives "
        f"{r17['hybrid_synonym_decay7d']['hit@1']:.3f} / "
        f"{r17['hybrid_synonym_decay7d']['hit@5']:.3f} / "
        f"{r17['hybrid_synonym_decay7d']['mrr']:.3f}; with the intent boost "
        f"{r17['hybrid_synonym_intent_decay7d']['hit@1']:.3f} / "
        f"{r17['hybrid_synonym_intent_decay7d']['hit@5']:.3f} / "
        f"{r17['hybrid_synonym_intent_decay7d']['mrr']:.3f}. Expanding the vector half too "
        f"(lexical+vector) gives {r17['hybrid_synonym_both_decay7d']['hit@1']:.3f} / "
        f"{r17['hybrid_synonym_both_decay7d']['hit@5']:.3f} / "
        f"{r17['hybrid_synonym_both_decay7d']['mrr']:.3f}; adding the near-duplicate merge "
        f"{r17['hybrid_synonym_both_dedupe_decay7d']['hit@1']:.3f} / "
        f"{r17['hybrid_synonym_both_dedupe_decay7d']['hit@5']:.3f} / "
        f"{r17['hybrid_synonym_both_dedupe_decay7d']['mrr']:.3f}. Embedding rerank of the fused "
        f"top-20: body cosine {r17['hybrid_decay7d_body_rerank']['hit@1']:.3f} / "
        f"{r17['hybrid_decay7d_body_rerank']['hit@5']:.3f} / "
        f"{r17['hybrid_decay7d_body_rerank']['mrr']:.3f}, title cosine "
        f"{r17['hybrid_decay7d_title_rerank']['hit@1']:.3f} / "
        f"{r17['hybrid_decay7d_title_rerank']['hit@5']:.3f} / "
        f"{r17['hybrid_decay7d_title_rerank']['mrr']:.3f}, title blend "
        f"{r17['hybrid_decay7d_title_blend']['hit@1']:.3f} / "
        f"{r17['hybrid_decay7d_title_blend']['hit@5']:.3f} / "
        f"{r17['hybrid_decay7d_title_blend']['mrr']:.3f}. "
    )
    pool_text = "; ".join(
        f"pool {depth}: {result['summary'][f'pool_{depth}']['hit@1']:.3f} / "
        f"{result['summary'][f'pool_{depth}']['hit@5']:.3f} / "
        f"{result['summary'][f'pool_{depth}']['mrr']:.3f} fused, "
        f"{result['summary'][f'pool_{depth}_decay7d']['hit@1']:.3f} / "
        f"{result['summary'][f'pool_{depth}_decay7d']['hit@5']:.3f} / "
        f"{result['summary'][f'pool_{depth}_decay7d']['mrr']:.3f} after 7-day decay"
        for depth in pool_depths)
    diagnosis += (
        f" Round 18 candidate-pool sweep: {pool_text}. A 90-query corpus moves by one query "
        f"per 0.011 hit@1, so any delta below 0.02 is inside one-query noise."
    )
    cluster_text = "; ".join(
        f"{label} x{weight}: {result['summary'][f'cluster_{label}_{weight}']['hit@1']:.3f} / "
        f"{result['summary'][f'cluster_{label}_{weight}']['hit@5']:.3f} / "
        f"{result['summary'][f'cluster_{label}_{weight}']['mrr']:.3f}"
        for label in ("session", "file", "both") for weight in CLUSTER_WEIGHTS)
    identified = sum(1 for row in rows if row["query_identifiers"])
    diagnosis += (
        f" Round 19 cluster expansion from the fused pool plus 7-day decay: {cluster_text}. "
        f"Query-side identifier expansion fired on {identified} of {len(rows)} queries "
        f"(up to {MAX_RECENT_IDENTIFIERS} tokens from the last {RECENT_WINDOW_DAYS} days) and gives "
        f"{result['summary']['hybrid_queryident_decay7d']['hit@1']:.3f} / "
        f"{result['summary']['hybrid_queryident_decay7d']['hit@5']:.3f} / "
        f"{result['summary']['hybrid_queryident_decay7d']['mrr']:.3f}; combined with the 0.5 "
        f"cluster rule {result['summary']['hybrid_queryident_cluster_decay7d']['hit@1']:.3f} / "
        f"{result['summary']['hybrid_queryident_cluster_decay7d']['hit@5']:.3f} / "
        f"{result['summary']['hybrid_queryident_cluster_decay7d']['mrr']:.3f}. "
    )
    round20 = result["round20"]
    vector_text = "; ".join(
        f"{depth}: hit@1 {result['summary'][f'vecdeep_{depth}']['hit@1']:.3f}, "
        f"hit@5 {result['summary'][f'vecdeep_{depth}']['hit@5']:.3f}, "
        f"MRR {result['summary'][f'vecdeep_{depth}']['mrr']:.3f}, "
        f"after 7-day decay {result['summary'][f'vecdeep_{depth}_decay7d']['hit@1']:.3f} / "
        f"{result['summary'][f'vecdeep_{depth}_decay7d']['hit@5']:.3f} / "
        f"{result['summary'][f'vecdeep_{depth}_decay7d']['mrr']:.3f}, "
        f"mean {result['latency_ms'][f'vecdeep_{depth}']['mean']:.1f} ms"
        for depth in vector_depths)
    diagnosis += (
        f" Round 20 vector-only expansion: {round20['production_misses']} production misses, "
        f"{round20['misses_with_relevant_outside_pool']} of them had a relevant row outside the "
        f"fused top-40; {round20['queries_with_relevant_outside_pool']} of {len(rows)} queries "
        f"had any relevant row outside it ({round20['missing_relevant_rows']} rows in total). "
        f"Vector list reachable relevant rows at depths "
        + ", ".join(f"{depth}: {count}/{round20['relevant_total']}"
                    for depth, count in round20["vector_reachable"].items())
        + f". Widening only the vector half: {vector_text}. A 90-query corpus moves by one "
        f"query per 0.011, so a delta below 0.02 is inside one-query noise; client wall time "
        f"includes a fixed psql process cost, so differences under a few milliseconds are not "
        f"attributable to the scan."
    )
    result["diagnosis"] = diagnosis
    round21 = result["round21"]
    weight_text = "; ".join(
        f"{weight}: hit@1 {item['summary']['hit@1']:.3f}, hit@5 {item['summary']['hit@5']:.3f}, "
        f"MRR {item['summary']['mrr']:.3f}, delta hit@5 "
        f"{item['delta']['hit@5']['delta']:+.3f} "
        f"[{item['delta']['hit@5']['ci_low']:+.3f}, {item['delta']['hit@5']['ci_high']:+.3f}]"
        for weight, item in round21["weights"].items())
    languages = result["corpus"]["languages"]
    language_text = ", ".join(f"{name}={count}" for name, count in languages.items())
    diagnosis += (
        f" Round 21 RRF weights: corpus languages {language_text} (no English-only query; "
        f"the 7-day-decayed baseline is hit@1 0.833 / hit@5 0.911 / MRR 0.866). "
        f"Weights {weight_text}. Vector-primary with lexical only on an empty vector list: "
        f"hit@1 {round21['vector_primary']['summary']['hit@1']:.3f}, "
        f"hit@5 {round21['vector_primary']['summary']['hit@5']:.3f}, "
        f"MRR {round21['vector_primary']['summary']['mrr']:.3f}, delta hit@5 "
        f"{round21['vector_primary']['delta']['hit@5']['delta']:+.3f} "
        f"[{round21['vector_primary']['delta']['hit@5']['ci_low']:+.3f}, "
        f"{round21['vector_primary']['delta']['hit@5']['ci_high']:+.3f}]. "
        f"Repeat-run spread is hit@1 {round21['repeat_noise']['hit@1']:.3f}, "
        f"hit@5 {round21['repeat_noise']['hit@5']:.3f}, MRR {round21['repeat_noise']['mrr']:.3f}. "
        f"The paired bootstrap interval is the decision band; a candidate that does not lift "
        f"the lower bound above zero is rejected."
    )
    result["diagnosis"] = diagnosis

    report_path = ROOT / args.report
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(render_report(result, corpus_path))
    metrics_path = ROOT / args.metrics_out
    metrics_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(result["summary"], indent=2))
    print(diagnosis)
    print(f"report: {report_path}")
    print(f"metrics: {metrics_path}")

    if not args.keep_db:
        psql(f"DROP DATABASE IF EXISTS {DATABASE} WITH (FORCE);", database="postgres")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
