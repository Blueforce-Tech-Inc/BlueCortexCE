#!/usr/bin/env python3
"""Fail a new migration that can lose data or lock a live table.

Why this script exists
----------------------
Flyway runs every migration on deploy, so a destructive statement is a production
change with no review gate in front of it. The existing 23 migrations predate
this check; rewriting them would change applied history. They are therefore
reported but grandfathered by ``scripts/migration-baseline.json``. Any file that
is not in that list is a new migration and must pass.

What is checked (new migrations)
--------------------------------
* ``DROP COLUMN``, ``DROP TABLE``, ``TRUNCATE``, ``DROP CONSTRAINT`` and
  unqualified ``DELETE FROM`` are destructive.
* ``ALTER COLUMN ... TYPE`` is reported when the target type is narrowing-prone
  (``smallint``/``integer``/``varchar(n)``/``numeric(p,s)``/``real``/``char(n)``).
  The source type is not known from text alone, so the finding asks the author
  to prove it is not a narrowing; the rollback note is the place to say so.
* ``CREATE INDEX`` without ``CONCURRENTLY`` is reported unless the same file
  creates the table first (a fresh table has no readers to lock).
* A migration carrying any finding above must contain a
  ``-- rollback: <how to undo it>`` line. Purely additive DDL does not need one.

Commented-out SQL is ignored (the Chinese lexical migration shows its own
rollback this way), so the checker reads what Flyway will execute.

Exit codes
----------
0 -- every new migration is clean (existing findings are printed, not fatal).
1 -- at least one new migration has a finding.
2 -- the checker itself could not run (missing directory/baseline, bad self-test).

Usage
-----
    python3 scripts/check-migrations.py
    python3 scripts/check-migrations.py --self-test
"""

import argparse
import json
import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_MIGRATIONS = os.path.join(
    REPO_ROOT, "backend", "src", "main", "resources", "db", "migration"
)
DEFAULT_BASELINE = os.path.join(REPO_ROOT, "scripts", "migration-baseline.json")

NARROWING_TYPE = re.compile(
    r"\b(smallint|integer|int4|varchar\s*\(\s*\d+\s*\)|character\s+varying\s*\(\s*\d+\s*\)"
    r"|numeric\s*\(\s*\d+\s*,\s*\d+\s*\)|decimal\s*\(\s*\d+\s*,\s*\d+\s*\)"
    r"|real|float4|char\s*\(\s*\d+\s*\))",
    re.IGNORECASE,
)


def strip_comments(sql):
    """The statements Flyway will execute, with comments removed."""
    sql = re.sub(r"/\*.*?\*/", " ", sql, flags=re.S)
    return re.sub(r"--[^\n]*", " ", sql)


def analyze_sql(sql, table_already_exists=()):
    """Findings for one migration body. Pure, so the self-test can drive it.

    ``table_already_exists`` is a hack for the self-test only; real analysis
    derives it from the same file.
    """
    body = strip_comments(sql)
    findings = []
    created_tables = set(re.findall(
        r"\bCREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([a-zA-Z0-9_\.]+)",
        body, re.IGNORECASE))
    created_tables |= {name.lower() for name in table_already_exists}

    for match in re.finditer(r"\bDROP\s+COLUMN\b", body, re.IGNORECASE):
        findings.append(("drop-column", "drops a column; existing values are lost"))
        break
    for match in re.finditer(r"\bDROP\s+TABLE\b", body, re.IGNORECASE):
        findings.append(("drop-table", "drops a table; all rows are lost"))
        break
    if re.search(r"\bTRUNCATE\b", body, re.IGNORECASE):
        findings.append(("truncate", "truncates a table; all rows are lost"))
    if re.search(r"\bDROP\s+CONSTRAINT\b", body, re.IGNORECASE):
        findings.append(("drop-constraint", "removes an integrity constraint"))
    if re.search(r"\bDELETE\s+FROM\b", body, re.IGNORECASE) and not re.search(
            r"\bDELETE\s+FROM\b[\s\S]*?\bWHERE\b", body, re.IGNORECASE):
        findings.append(("delete-without-where", "deletes every row in the table"))

    for match in re.finditer(
            r"\bALTER\s+TABLE\s+[\s\S]*?\bALTER\s+COLUMN\s+([a-zA-Z0-9_]+)\s+TYPE\s+"
            r"([a-zA-Z0-9_]+(?:\s*\([^)]*\))?)",
            body, re.IGNORECASE):
        target = match.group(2).strip()
        if NARROWING_TYPE.search(target):
            findings.append((
                "type-narrowing",
                "column %s changes to %s; prove the target can hold every existing value"
                % (match.group(1), target)))

    for match in re.finditer(
            r"\bCREATE\s+(?:UNIQUE\s+)?INDEX\s+(?!CONCURRENTLY\b)"
            r"(?:IF\s+NOT\s+EXISTS\s+)?[a-zA-Z0-9_]+\s+ON\s+(?:ONLY\s+)?([a-zA-Z0-9_\.]+)",
            body, re.IGNORECASE):
        table = match.group(1).lower()
        if table in created_tables:
            continue
        findings.append((
            "non-concurrent-index",
            "index on %s is built without CONCURRENTLY and locks writes" % match.group(1)))

    return findings


def find_rollback_note(sql):
    for line in sql.splitlines():
        match = re.match(r"\s*--\s*rollback\s*:\s*(.*)$", line, re.IGNORECASE)
        if match and match.group(1).strip():
            return match.group(1).strip()
    return None


def analyze_file(path):
    with open(path, encoding="utf-8") as handle:
        sql = handle.read()
    findings = analyze_sql(sql)
    if findings and not find_rollback_note(sql):
        findings.append(("missing-rollback", "add a '-- rollback: <how to undo>' line"))
    return findings


def load_baseline(path):
    try:
        with open(path, encoding="utf-8") as handle:
            data = json.load(handle)
    except (OSError, ValueError) as exc:
        return None, "cannot read %s (%s)" % (path, exc)
    grandfathered = data.get("grandfathered")
    if not isinstance(grandfathered, list) or not grandfathered:
        return None, "%s has no non-empty 'grandfathered' list" % path
    return set(grandfathered), ""


def self_test():
    cases = [
        ("drop column", "ALTER TABLE t DROP COLUMN c;", True),
        ("drop table", "DROP TABLE t;", True),
        ("truncate", "TRUNCATE t;", True),
        ("delete without where", "DELETE FROM t;", True),
        ("delete with where is scoped", "DELETE FROM t WHERE id = 1;", False),
        ("type narrowing", "ALTER TABLE t ALTER COLUMN c TYPE varchar(20);", True),
        ("widening to text", "ALTER TABLE t ALTER COLUMN c TYPE text;", False),
        ("plain index on an existing table",
         "CREATE INDEX idx ON t (c);", True),
        ("concurrent index",
         "CREATE INDEX CONCURRENTLY idx ON t (c);", False),
        ("plain index on a table created in the same file",
         "CREATE TABLE t (c int);\nCREATE INDEX idx ON t (c);", False),
        ("commented-out destructive SQL",
         "-- ALTER TABLE t DROP COLUMN c;\nSELECT 1;", False),
        ("rollback note supplied",
         "-- rollback: restore c from backup\nALTER TABLE t DROP COLUMN c;", True),
        ("destructive without rollback",
         "ALTER TABLE t DROP COLUMN c;", True),
    ]
    failures = []
    for name, sql, expect_findings in cases:
        findings = analyze_sql(sql)
        if bool(findings) != expect_findings:
            failures.append("%s: expected findings=%s, got %s"
                            % (name, expect_findings, findings))
        if name == "rollback note supplied" and find_rollback_note(sql) is None:
            failures.append("%s: a supplied rollback note was not found" % name)
        if name == "destructive without rollback" and find_rollback_note(sql) is not None:
            failures.append("%s: a rollback note was detected where none exists" % name)

    # A destructive statement is only complete when both findings and a
    # rollback note are considered.
    with_note = (
        "-- rollback: restore c from the pre-migration dump\n"
        "ALTER TABLE t DROP COLUMN c;"
    )
    destructive_findings = analyze_sql(with_note)
    if find_rollback_note(with_note) is None:
        failures.append("a supplied rollback note was not found")
    if not destructive_findings:
        failures.append("a destructive statement was not reported")

    if failures:
        print("SELF-TEST FAILED")
        for failure in failures:
            print("  - %s" % failure)
        return 1
    print("SELF-TEST PASSED (%d cases)" % (len(cases) + 2))
    return 0


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--migrations-dir", default=DEFAULT_MIGRATIONS)
    parser.add_argument("--baseline", default=DEFAULT_BASELINE)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)

    if args.self_test:
        return self_test()

    if not os.path.isdir(args.migrations_dir):
        print("ERROR: migration directory not found: %s" % args.migrations_dir, file=sys.stderr)
        return 2
    grandfathered, problem = load_baseline(args.baseline)
    if problem:
        print("ERROR: %s" % problem, file=sys.stderr)
        return 2

    files = sorted(
        name for name in os.listdir(args.migrations_dir) if name.endswith(".sql")
    )
    missing = sorted(grandfathered - set(files))
    if missing:
        print("ERROR: grandfathered migration(s) no longer exist: %s" % ", ".join(missing),
              file=sys.stderr)
        return 2

    existing_findings = 0
    new_findings = 0
    for name in files:
        findings = analyze_file(os.path.join(args.migrations_dir, name))
        if name in grandfathered:
            if findings:
                existing_findings += 1
                print("existing (reported, not blocking): %s" % name)
                for rule, detail in findings:
                    print("  %-22s %s" % (rule, detail))
            continue
        if findings:
            new_findings += 1
            print("FAIL: new migration %s" % name)
            for rule, detail in findings:
                print("  %-22s %s" % (rule, detail))

    print("")
    print("migrations          : %d (%d grandfathered, %d new)"
          % (len(files), len(grandfathered), len(files) - len(grandfathered)))
    print("existing findings   : %d file(s), reported only" % existing_findings)
    if new_findings:
        print("FAIL: %d new migration(s) need a fix or an explicit rollback story" % new_findings)
        return 1
    print("PASS: no new migration has an unreviewed destructive or locking operation")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
