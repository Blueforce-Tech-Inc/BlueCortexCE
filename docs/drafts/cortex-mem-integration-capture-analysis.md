# cortex-mem-spring-integration: Capture Coverage Analysis

> **Date**: 2026-03-18  
> **Purpose**: Assess whether the integration captures enough user-agent interactions; identify gaps and improvement opportunities.

---

## 1. Current Capture Matrix (Updated 2026-03-18)

| Interaction Type | Backend API | Integration | Auto? | Notes |
|------------------|-------------|-------------|-------|-------|
| **Tool use** | `POST /api/ingest/tool-use` | `CortexToolAspect` → `recordObservation()` | ✅ Yes | AOP intercepts `@Tool` methods when `CortexSessionContext` is active |
| **User prompt** | `POST /api/ingest/user-prompt` | `CortexMemoryAdvisor` → `recordUserPrompt()` | ✅ Yes | When `ChatMemory.CONVERSATION_ID` or `CortexSessionContext` provides session ID |
| **Session end** | `POST /api/ingest/session-end` | `ObservationCaptureService.recordSessionEnd()` | ❌ No | Developer must call explicitly |
| **Session start** | `POST /api/session/start` | `CortexMemClient.startSession()` | ❌ No | Developer must call explicitly |
| **AI response** | (session-end `last_assistant_message`) | — | ❌ No | No per-turn capture (deferred) |

---

## 2. CortexMemoryAdvisor: Retrieval + Capture ✅

`CortexMemoryAdvisor` **retrieves** memory (ICL injection) **and** auto-captures user prompts when:

- `cortex.mem.capture-user-prompt-enabled=true` (config, default true)
- Session ID available from either `ChatMemory.CONVERSATION_ID` (request context) or `CortexSessionContext`

**Fine-grained config**: `capture-enabled` controls @Tool capture; `capture-user-prompt-enabled` controls user prompt capture independently.

---

## 3. Gap Analysis

### 3.1 User Prompts Not Auto-Captured ✅ RESOLVED

**Was**: User message never sent to backend.

**Now**: `CortexMemoryAdvisor` auto-captures when session ID provided via `ChatMemory.CONVERSATION_ID` or `CortexSessionContext`. See Section 6.

### 3.2 Session Lifecycle Fully Manual

**Problem**: `startSession`, `recordSessionEnd` require explicit developer calls.

**Impact**:
- Easy to forget → inconsistent session boundaries
- Web apps: when does a "session" start? (first request, login, …)
- When does it end? (timeout, explicit end, browser close?)

### 3.3 AI Responses Not Captured Per Turn

**Problem**: The backend stores `last_assistant_message` only at session-end. Per-turn assistant messages are not recorded.

**Impact**:
- Multi-turn conversations: only the final assistant message is kept
- Prior-messages / continuity features have less context

---

## 4. Improvement Opportunities

### 4.1 Auto-Capture User Prompts (High Value, Low Effort)

**Idea**: Add optional user-prompt capture to `CortexMemoryAdvisor` (or a separate `CortexUserPromptCaptureAdvisor`).

**Flow**:
1. In `adviseCall` / `adviseStream`, extract `userText` from request
2. If `captureEnabled` and `CortexSessionContext.isActive()`:
   - Fire-and-forget `recordUserPrompt(sessionId, projectPath, userText, promptNumber)`
3. Continue with ICL enrichment and chain

**Requirements**: `CortexSessionContext` must be active (sessionId, projectPath, promptNumber). Developers using ChatClient would need to call `CortexSessionContext.begin()` before the ChatClient call—same as for tool capture.

**Config**: `cortex.mem.capture-enabled` (already exists).

### 4.2 Optional AI Response Capture (Medium Value, Medium Effort)

**Idea**: After `chain.nextCall()`, capture the assistant response.

**Challenges**:
- Backend has no dedicated "store assistant message" endpoint
- `session-end` accepts `last_assistant_message`—could call session-end per turn (heavy) or accumulate
- May need backend support for per-turn assistant storage

**Recommendation**: Defer until backend API clarifies. For now, session-end with `last_assistant_message` is sufficient for summary generation.

### 4.3 Session Lifecycle Helpers (Medium Value, Low Effort)

**Idea**: Provide filters or advice for common web patterns.

- **SessionStartFilter**: On first request with `X-Session-Id` (or similar), call `startSession`
- **SessionEndScheduler**: Configurable timeout to call `recordSessionEnd` when no activity for N minutes
- **@CortexSession** annotation: AOP to auto-begin/end around controller methods

**Recommendation**: Add as optional utilities; keep default behavior explicit to avoid surprises.

### 4.4 Richer Tool Capture Context (Low–Medium Value)

**Idea**: When capturing tool use, include the user message that led to it (if available).

- `ObservationRequest` could have optional `userMessage` / `conversationContext`
- Backend tool-use already receives `tool_input` and `tool_response`; LLM infers context
- Extra user message could improve observation quality

**Recommendation**: Backend would need to accept and use this. Lower priority than user-prompt capture.

---

## 5. Summary

| Gap | Severity | Status | Notes |
|-----|----------|--------|-------|
| User prompts not auto-captured | **High** | ✅ Resolved | CortexMemoryAdvisor + ChatMemory.CONVERSATION_ID / CortexSessionContext |
| Session start/end manual | Medium | ⏳ Open | Provide helpers; document patterns |
| AI response per-turn | Medium | ⏳ Deferred | Backend design first |
| Tool context (user message) | Low | ⏳ Deferred | Lower priority |

**Conclusion**: User-prompt auto-capture is implemented. Tool use and user prompts are now both captured when session context is provided.

---

## 6. Implementation (2026-03-18) ✅

| Change | Location |
|--------|----------|
| `CortexMemoryAdvisor.captureUserPromptIfActive()` | cortex-mem-spring-ai |
| `CortexMemoryAdvisor.Builder.captureEnabled()` | cortex-mem-spring-ai |
| `CortexMemAutoConfiguration` passes `captureEnabled` | cortex-mem-starter |
| `ChatController` wraps chat in `CortexSessionContext` | examples/cortex-mem-demo |

**Usage**: Provide a session ID via either:
- Spring AI `ChatMemory.CONVERSATION_ID`: `.advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, id))`
- `CortexSessionContext.begin(sessionId, projectPath)` + `incrementAndGetPromptNumber()` before each call

**Spring AI alignment (2026-03-18)**: `CortexMemoryAdvisor` now reads `ChatMemory.CONVERSATION_ID` from request context first. When used with `MessageChatMemoryAdvisor` and `.param(ChatMemory.CONVERSATION_ID, id)`, both advisors share the same session/conversation ID—no need for `CortexSessionContext` when using Spring AI's native conversation ID.

---

## 7. Test Verification (2026-03-18)

| Test Suite | Result |
|------------|--------|
| cortex-mem-spring-integration unit tests | ✅ All pass |
| cortex-mem-demo unit tests | ✅ All pass |
| E2E (run-e2e.sh) | ✅ 11/11 pass (with -Plocal demo) |

**E2E user-prompt capture**: Verifies that `/chat` records the user message to `/api/prompts`. Requires demo built with `mvn -Plocal` after `mvn install` of cortex-mem-spring-integration.
