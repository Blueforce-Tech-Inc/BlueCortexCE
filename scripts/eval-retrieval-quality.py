#!/usr/bin/env python3
"""Measure retrieval quality (hit@1, hit@5, MRR) on real session-history queries.

Manual harness, like the model evaluator: it needs the real embedding provider and
a disposable PostgreSQL. It does not run in CI.

It builds a corpus (see build-retrieval-corpus.py), embeds every observation and
query with the configured real model (BAAI/bge-m3, 1024 dimensions by default),
loads a disposable database, and then measures the three retrieval shapes that
exist in production SQL:

  vector    ObservationRepository.semanticSearch1024 (cosine, no similarity floor)
  tsvector  ObservationRepository.fullTextSearchScored (plainto_tsquery + ts_rank)
  hybrid    ObservationRepository.hybridSearchScored (cosine >= 0.5 union ts_rank)

Two diagnostic hybrids are measured as well, so a fusion problem can be named
rather than guessed: no-floor hybrid and reciprocal-rank fusion over the top 20
of each half. The production SQL is copied verbatim from the repository; if it
changes, this harness must change with it.

Usage:
    python3 scripts/eval-retrieval-quality.py \
        --corpus backend/src/test/resources/eval/retrieval-corpus-real.json
"""

from __future__ import annotations

import argparse
import json
import os
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
            now(), {epoch + index},
            (SELECT id FROM mem_memory_scopes
             WHERE scope_type = 'project' AND scope_key = {sql_quote(PROJECT)} COLLATE "C"),
            'claude', 'complete', {sql_quote(vector_literal(vector))}::vector
        );
        """)
    statements.append("ANALYZE mem_observations;")
    psql("\n".join(statements))


SCOPE_FILTER = f"""
    memory_scope_id IN (
        SELECT id FROM mem_memory_scopes
        WHERE scope_type = 'project' AND scope_key = {sql_quote(PROJECT)} COLLATE "C"
    )
"""


def rank_vector(query_vector: str, limit: int = TOP_K) -> list[str]:
    return psql(f"""
        SELECT id FROM mem_observations
        WHERE {SCOPE_FILTER}
          AND embedding_1024 IS NOT NULL
          AND created_at_epoch > 0
        ORDER BY embedding_1024 <=> cast({sql_quote(query_vector)} as vector)
        LIMIT {limit};
    """).splitlines()


def rank_fulltext(query: str, limit: int = TOP_K) -> list[str]:
    return psql(f"""
        SELECT o.id
        FROM mem_observations o
        WHERE {SCOPE_FILTER}
          AND o.search_vector @@ plainto_tsquery('english', {sql_quote(query)})
          AND ts_rank(o.search_vector, plainto_tsquery('english', {sql_quote(query)}) ) > 0
        ORDER BY ts_rank(o.search_vector, plainto_tsquery('english', {sql_quote(query)}) ) DESC
        LIMIT {limit};
    """).splitlines()


def rank_hybrid(query: str, query_vector: str, min_similarity: float, limit: int = TOP_K) -> list[str]:
    return psql(f"""
        WITH semantic_results AS (
            SELECT o.id AS id, 1.0 AS score_type,
                   (1.0 - (o.embedding_1024 <=> cast({sql_quote(query_vector)} as vector))) AS similarity
            FROM mem_observations o
            WHERE {SCOPE_FILTER}
              AND o.embedding_1024 IS NOT NULL
              AND o.created_at_epoch > 0
              AND (1.0 - (o.embedding_1024 <=> cast({sql_quote(query_vector)} as vector))) >= {min_similarity}
            ORDER BY o.embedding_1024 <=> cast({sql_quote(query_vector)} as vector)
            LIMIT {limit}
        ),
        fulltext_results AS (
            SELECT o.id AS id, 0.8 AS score_type,
                   ts_rank(o.search_vector, plainto_tsquery('english', {sql_quote(query)})) AS similarity
            FROM mem_observations o
            WHERE {SCOPE_FILTER}
              AND o.search_vector @@ plainto_tsquery('english', {sql_quote(query)})
              AND ts_rank(o.search_vector, plainto_tsquery('english', {sql_quote(query)})) > 0
            LIMIT {limit}
        )
        SELECT id
        FROM (SELECT * FROM semantic_results UNION ALL SELECT * FROM fulltext_results) combined
        GROUP BY id
        ORDER BY MAX(CASE WHEN score_type = 1.0 THEN similarity ELSE 0 END) DESC,
                 MAX(CASE WHEN score_type = 0.8 THEN similarity ELSE 0 END) DESC
        LIMIT {limit};
    """).splitlines()


def rank_rrf(query: str, query_vector: str, limit: int = TOP_K, pool: int = 20) -> list[str]:
    return psql(f"""
        WITH semantic_results AS (
            SELECT id, row_number() OVER (
                       ORDER BY embedding_1024 <=> cast({sql_quote(query_vector)} as vector)) AS rank
            FROM mem_observations
            WHERE {SCOPE_FILTER}
              AND embedding_1024 IS NOT NULL
              AND created_at_epoch > 0
            ORDER BY embedding_1024 <=> cast({sql_quote(query_vector)} as vector)
            LIMIT {pool}
        ),
        fulltext_results AS (
            SELECT id, row_number() OVER (
                       ORDER BY ts_rank(search_vector, plainto_tsquery('english', {sql_quote(query)}) ) DESC) AS rank
            FROM mem_observations
            WHERE {SCOPE_FILTER}
              AND search_vector @@ plainto_tsquery('english', {sql_quote(query)})
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


def rank_simple_fulltext(query: str, limit: int = TOP_K) -> list[str]:
    """Lexical ranking with the 'simple' analyzer and identifiers in the text.

    The production search_vector uses the English analyzer over title/content only.
    Real requests are often Chinese and name identifiers such as CUX-048 or
    AssetRepository.findProjectMedia. This diagnostic asks whether an analyzer that
    keeps identifiers intact (and indexes files_modified) gives the lexical half
    anything to contribute.
    """
    document = ("to_tsvector('simple', coalesce(o.title,'') || ' ' || coalesce(o.content,'') "
                "|| ' ' || coalesce(o.files_modified::text,''))")
    return psql(f"""
        SELECT o.id
        FROM mem_observations o
        WHERE {SCOPE_FILTER}
          AND {document} @@ plainto_tsquery('simple', {sql_quote(query)})
        ORDER BY ts_rank({document}, plainto_tsquery('simple', {sql_quote(query)})) DESC
        LIMIT {limit};
    """).splitlines()


def rank_hybrid_simple(query: str, query_vector: str, min_similarity: float = 0.0,
                       limit: int = TOP_K) -> list[str]:
    document = ("to_tsvector('simple', coalesce(o.title,'') || ' ' || coalesce(o.content,'') "
                "|| ' ' || coalesce(o.files_modified::text,''))")
    return psql(f"""
        WITH semantic_results AS (
            SELECT o.id AS id, 1.0 AS score_type,
                   (1.0 - (o.embedding_1024 <=> cast({sql_quote(query_vector)} as vector))) AS similarity
            FROM mem_observations o
            WHERE {SCOPE_FILTER}
              AND o.embedding_1024 IS NOT NULL
              AND o.created_at_epoch > 0
              AND (1.0 - (o.embedding_1024 <=> cast({sql_quote(query_vector)} as vector))) >= {min_similarity}
            ORDER BY o.embedding_1024 <=> cast({sql_quote(query_vector)} as vector)
            LIMIT {limit}
        ),
        fulltext_results AS (
            SELECT o.id AS id, 0.8 AS score_type,
                   ts_rank({document}, plainto_tsquery('simple', {sql_quote(query)})) AS similarity
            FROM mem_observations o
            WHERE {SCOPE_FILTER}
              AND {document} @@ plainto_tsquery('simple', {sql_quote(query)})
            LIMIT {limit}
        )
        SELECT id
        FROM (SELECT * FROM semantic_results UNION ALL SELECT * FROM fulltext_results) combined
        GROUP BY id
        ORDER BY MAX(CASE WHEN score_type = 1.0 THEN similarity ELSE 0 END) DESC,
                 MAX(CASE WHEN score_type = 0.8 THEN similarity ELSE 0 END) DESC
        LIMIT {limit};
    """).splitlines()


def rank_hybrid_simple_rrf(query: str, query_vector: str, limit: int = TOP_K,
                           pool: int = 20) -> list[str]:
    document = ("to_tsvector('simple', coalesce(title,'') || ' ' || coalesce(content,'') "
                "|| ' ' || coalesce(files_modified::text,''))")
    return psql(f"""
        WITH semantic_results AS (
            SELECT id, row_number() OVER (
                       ORDER BY embedding_1024 <=> cast({sql_quote(query_vector)} as vector)) AS rank
            FROM mem_observations
            WHERE {SCOPE_FILTER}
              AND embedding_1024 IS NOT NULL
              AND created_at_epoch > 0
            ORDER BY embedding_1024 <=> cast({sql_quote(query_vector)} as vector)
            LIMIT {pool}
        ),
        fulltext_results AS (
            SELECT id, row_number() OVER (
                       ORDER BY ts_rank({document}, plainto_tsquery('simple', {sql_quote(query)})) DESC) AS rank
            FROM mem_observations
            WHERE {SCOPE_FILTER}
              AND {document} @@ plainto_tsquery('simple', {sql_quote(query)})
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
        WHERE {SCOPE_FILTER}
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
            WHERE {SCOPE_FILTER}
              AND embedding_1024 IS NOT NULL
              AND created_at_epoch > 0
            ORDER BY embedding_1024 <=> cast({sql_quote(query_vector)} as vector)
            LIMIT {pool}
        ),
        fulltext_results AS (
            SELECT id, row_number() OVER (
                       ORDER BY ts_rank({document}, {tsquery}) DESC) AS rank
            FROM mem_observations
            WHERE {SCOPE_FILTER}
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
    for name, summary in result["summary"].items():
        lines.append(
            f"| {name} | {summary['hit@1']:.3f} | {summary['hit@5']:.3f} | "
            f"{summary['mrr']:.3f} | {summary['empty_results']} |"
        )
    lines += [
        "",
        "## Diagnosis",
        "",
        result["diagnosis"],
        "",
        "## Per-query first relevant rank",
        "",
        "| query | relevant | vector | vector-head120 | vector-tail120 | tsvector | "
        "hybrid | hybrid-no-floor | rrf | simple-lexical | hybrid-simple | "
        "hybrid-simple-rrf | identifier-or | identifier-or-rrf |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
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
            f"{rank('vector_head120')} | {rank('vector_tail120')} | {rank('tsvector')} | "
            f"{rank('hybrid')} | {rank('hybrid_no_floor')} | "
            f"{rank('rrf')} | {rank('simple_lexical')} | {rank('hybrid_simple')} | "
            f"{rank('hybrid_simple_rrf')} | {rank('identifier_or')} | "
            f"{rank('identifier_or_rrf')} |"
        )
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--corpus", default="backend/src/test/resources/eval/retrieval-corpus-real.json")
    parser.add_argument("--report", default="docs/archive/" +
                        time.strftime("%Y-%m-%d") + "_retrieval-quality-real.md")
    parser.add_argument("--metrics-out", default="docs/archive/" +
                        time.strftime("%Y-%m-%d") + "_retrieval-quality-real.json")
    parser.add_argument("--keep-db", action="store_true")
    args = parser.parse_args()

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

    create_database()
    migrate()
    load_rows(observations, observation_vectors)

    names = {
        "vector": lambda q, v: rank_vector(v),
        "tsvector": lambda q, v: rank_fulltext(q),
        "hybrid": lambda q, v: rank_hybrid(q, v, 0.5),
        "hybrid_no_floor": lambda q, v: rank_hybrid(q, v, 0.0),
        "rrf": lambda q, v: rank_rrf(q, v),
        "simple_lexical": lambda q, v: rank_simple_fulltext(q),
        "hybrid_simple": lambda q, v: rank_hybrid_simple(q, v),
        "hybrid_simple_rrf": lambda q, v: rank_hybrid_simple_rrf(q, v),
        "identifier_or": lambda q, v: rank_identifier_or(q),
        "identifier_or_rrf": lambda q, v: rank_identifier_or_rrf(q, v),
    }
    corpus_ids = {obs["id"] for obs in observations}
    rows = []
    for query, vector, head, tail in zip(queries, query_vectors, head_vectors, tail_vectors):
        relevant = {uuid_for(item) for item in query["relevant"] if item in corpus_ids}
        row = {"id": query["id"], "text": query["text"], "relevant": sorted(relevant)}
        for name, function in names.items():
            row[name] = metrics(function(query["text"], vector_literal(vector)), relevant)
        row["vector_head120"] = metrics(rank_vector(vector_literal(head)), relevant)
        row["vector_tail120"] = metrics(rank_vector(vector_literal(tail)), relevant)
        rows.append(row)

    summary_names = list(names) + ["vector_head120", "vector_tail120"]
    result = {
        "corpus": {"observations": len(observations), "queries": len(queries),
                   "path": str(corpus_path.relative_to(ROOT))},
        "embedding_model": EMBED_MODEL,
        "summary": {name: summarize([row[name] for row in rows]) for name in summary_names},
        "rows": rows,
    }
    vector_summary = result["summary"]["vector"]
    hybrid_summary = result["summary"]["hybrid"]
    rrf_summary = result["summary"]["rrf"]
    simple_summary = result["summary"]["simple_lexical"]
    hybrid_simple_summary = result["summary"]["hybrid_simple"]
    hybrid_simple_rrf_summary = result["summary"]["hybrid_simple_rrf"]
    identifier_summary = result["summary"]["identifier_or"]
    identifier_rrf_summary = result["summary"]["identifier_or_rrf"]
    identifier_coverage = sum(1 for query in queries if identifier_tokens(query["text"]))
    head_summary = result["summary"]["vector_head120"]
    tail_summary = result["summary"]["vector_tail120"]
    candidates = {
        "vector_head120": head_summary,
        "vector_tail120": tail_summary,
        "identifier_or_rrf": identifier_rrf_summary,
        "hybrid_simple_rrf": hybrid_simple_rrf_summary,
    }
    better = {name: summary for name, summary in candidates.items()
              if summary["mrr"] > vector_summary["mrr"] + 0.01}
    if better:
        best_name, best = max(better.items(), key=lambda item: item[1]["mrr"])
        diagnosis = (
            f"Measured hybrid ties pure vector at MRR {hybrid_summary['mrr']:.3f}: its lexical "
            f"half is empty for {result['summary']['tsvector']['empty_results']} of "
            f"{result['summary']['tsvector']['queries']} Chinese queries because "
            f"plainto_tsquery is conjunctive and the analyzer is English. Identifier OR alone "
            f"reaches {identifier_summary['mrr']:.3f}; fusing it with the vector reaches "
            f"{identifier_rrf_summary['mrr']:.3f}. The useful lever is query focus: "
            f"{best_name} reaches MRR {best['mrr']:.3f}, hit@5 {best['hit@5']:.3f}, against "
            f"vector's {vector_summary['mrr']:.3f}/{vector_summary['hit@5']:.3f}."
        )
    elif hybrid_summary["mrr"] < vector_summary["mrr"]:
        diagnosis = (
            f"Hybrid MRR {hybrid_summary['mrr']:.3f} is below pure vector "
            f"{vector_summary['mrr']:.3f}. The fusion is the cause, not the embedding: the "
            f"semantic half is gated by a hard cosine floor of 0.5, and the text half only "
            f"matches when the query shares English tokens with the observation. "
            f"Hybrid with no floor reaches {result['summary']['hybrid_no_floor']['mrr']:.3f}; "
            f"reciprocal-rank fusion reaches {rrf_summary['mrr']:.3f}."
        )
    else:
        diagnosis = (
            f"Measured hybrid ties pure vector at MRR {hybrid_summary['mrr']:.3f}. "
            f"The English tsvector half returns nothing for these Chinese queries "
            f"({result['summary']['tsvector']['empty_results']} of "
            f"{result['summary']['tsvector']['queries']} empty). Identifier OR reaches "
            f"{identifier_summary['mrr']:.3f}; focused query prefixes reach "
            f"{head_summary['mrr']:.3f}/{tail_summary['mrr']:.3f}. Vector remains the best "
            f"single signal on this corpus."
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
