# Audit list → test coverage gap scan — 2026-11-05

Scope: `docs/drafts/backend-review-findings.md` currently holds **119 fixed**
entries (`Status: ✅ …`) and **71 recorded-not-fixed** entries (`⏸`). The fixed
ones were swept for a test that would fail if the fix were reverted.

Result: **two backend behaviours had no test and now do**; the rest are either
already pinned, documentation-only, or outside this repository. No entry was
left in the "fixed but untested and untestable-without-cost" bucket.

## Added this batch

| Finding | Behaviour | Test |
|---|---|---|
| P2-78 | `allowedMethods` was missing `PATCH`, so a browser preflight for either live PATCH endpoint failed while every other method succeeded | `WebConfigCorsTest.apiMappingAllowsPatch` |
| P2-80 | A documented `"a, b"` origin list kept the leading space on every element after the first, so only the first origin matched | `WebConfigCorsTest.originsAreTrimmed` |
| P2-78/P2-80 | `/stream` is GET-only and shares the same trimmed list; a blank list registers no origin and no credentials | `WebConfigCorsTest.streamMappingStaysGetOnly`, `emptyOriginsDenyEverything` |

Both assertions are made on the `CorsConfiguration` the registry will actually
compare against the `Origin` header, not on the YAML text. `CorsRegistry` only
exposes the path → configuration map through the protected
`getCorsConfigurations()`, so the test reads it with `ReflectionTestUtils` —
acceptable for a config-shape test, and it fails loudly if Spring renames the
method.

Also from this batch (measured by the provider-outage harness rather than the
audit list): `LlmService.classify` had **no test at all**, and the transport-vs-
answered distinction decides whether the circuit opens. `LlmServiceClassificationTest`
now pins eight outcomes, including the exact JDK message that used to classify
`UNKNOWN` and close an open circuit.

## Fixed entries that already have tests (not re-added)

`P2-24` (`L2HttpRegressionTest` posts `/api/memory/feedback`), `P2-83` and the
`PATCH /api/session/{id}/user` path names (`BackendQueryParamContractTest`),
`P2-171` and `P2-186`/`P2-210` (endpoint surface + `scripts/check-api-doc-surface.py`),
`P2-120`/`P2-127`/`P2-142`/`P2-143`/`P2-153` (OpenAPI contract tests),
`P2-22`/`P2-202` (`CursorApiContractTest`), `P2-139` (`TestControllerLlmFailureTest`),
`P2-165`/`P2-166`/`P2-174`/`P2-177`/`P2-181`/`P2-188`/`P2-191` (SDK guard/retry suites),
`P2-134`/`P2-147`/`P2-155`/`P2-158`/`P2-160`/`P2-162`/`P2-167`/`P2-185`/`P2-193`/
`P2-196`/`P2-203`/`P2-205`/`P2-207`/`P2-209`/`P2-212`/`P2-213`/`P2-214`/`P2-215`
(each says "N 个新用例" in its status line).

## Fixed entries deliberately left untested, with the reason

| Group | Entries (examples) | Why a test is not worth it |
|---|---|---|
| Documentation only ("零可执行行") | `P2-86` `P2-87` `P2-89` `P2-94` `P2-97` `P2-98` `P2-99` `P2-101` `P2-102` `P2-104` `P2-105` `P2-108` `P2-114` `P2-117` `P2-118` `P2-130` `P2-131` `P2-137` `P2-138` `P2-140` `P2-145` `P2-148` `P2-152` `P2-154` `P2-157` `P2-159` `P2-163` `P2-164` `P2-172` `P2-173` `P2-175` `P2-178` `P2-180` `P2-182` `P2-184` `P2-189` `P2-190` `P2-192` `P2-194` `P2-197` `P2-201` `P2-204` `P2-206` `P2-208` `P2-216` | The fix changed README / design / architecture / changelog text. A test would have to assert the *wording*, which rots on every edit and catches nothing: the failure mode is a reader being misled, and the guard is review. The two numbers that can drift mechanically are already guarded by `scripts/check-api-doc-surface.py` (endpoints/operations) and the SDK test counts in `run-all-e2e.sh`. |
| Javadoc / comment only | `P2-67` `P2-68` `P2-92` `P2-103` `P2-107` `P2-109` `P2-110` `P2-111` | Same reason: no behaviour changed. For `P2-109`/`P2-110` the *actual* behaviour they describe (filter path vs tsvector, page floor) is already covered by `SearchServiceTest` and the pagination tests. |
| Config-only | `P2-73` (`logging.level.com.claudemem` pointed at a package that no longer exists) | The fix is a YAML prefix. A test would have to boot with the dev profile and capture logs for a package that emits none by default; the observable failure (missing debug lines) is not a contract. Log-file rotation and level handling have their own tests. |
| Demo shell scripts | `P2-168` `P2-169` `P2-170` `P2-183` | These are shell e2e suites under `demo*/`; the fix is in their `check_demo`/`curl -sf` logic. They are already executed by `scripts/run-all-e2e.sh`, which is the test. Turning them into JUnit tests would duplicate the harness without adding a fast signal. |
| Out of this repository | `P2-64` `P2-65` `P2-66` `P2-74` `P2-84` `P2-85` `P2-117` `P2-146` `P2-149` `P2-150` `P2-163` `P2-165` `P2-166` `P2-174` `P2-175` `P2-177` `P2-178` `P2-179` `P2-180` `P2-181` `P2-182` `P2-188` `P2-191` (Java/Go/Python/JS SDK items) | `java-sdk/` and `python-sdk/` are not in this working tree; the Go and JS SDKs live in their own repositories. Their guards/retry tests belong to those suites, and the findings record which suite covers them. |
| Runtime finding recorded, not fixed | 71 `⏸` entries | No behaviour changed, so there is nothing to pin. Each entry keeps the measurement that justifies leaving it. |

## Why this scan does not add more

The audit's catalogue is dominated by "the document says X, the code does Y".
Those are already answered by `CursorApiContractTest`, `BackendQueryParamContractTest`,
`BackendEndpointSurfaceTest`, `BackendTransportEndpointSurfaceTest`,
`WebUiWireContractTest`, and `scripts/check-api-doc-surface.py`, which read the
live Spring context rather than the prose. Where a *code* fix had no test
(CORS, LLM failure classification), this batch added one; where the fix was
prose, a test would assert prose.
