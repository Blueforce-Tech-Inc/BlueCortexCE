package cortexmem_test

// This file pins the COMPLETE key set of every outbound request body the Go
// SDK sends.
//
// Why this file exists
//
// Every other wire-format test in this package asserts that *some* key is
// present with a given value, or that a handful of specific wrong keys are
// absent. None of them asserts which keys are all there. A measurement on
// 2026-10-22 put a number on that: adding one extra always-present field to
// each of the nine request DTOs — a field with no `omitempty`, so it is on
// the wire every single time — left the whole module suite GREEN in 9 cases
// out of 9. Renaming any of the 40 declared wire keys turned the suite red in
// 36 of them; the four that stayed green were UserPromptRequest.prompt_number,
// ObservationUpdate.content, ObservationUpdate.facts and
// FeedbackRequest.comment.
//
// The gap this closes is the "new field" direction: the backend accepts
// unknown JSON properties and ignores them, so a field added to a request
// struct changes the wire silently. The expectation sets below are written
// out by hand on purpose — deriving them from the struct's own `json` tags
// would make the assertion tautological, and a tautological assertion is
// worse than no assertion at all.
//
// The three families of check here are:
//
//  1. the complete key set of a fully populated request (what the backend
//     receives when the caller supplies everything),
//  2. the complete key set of a minimal request (what the backend receives
//     when the caller supplies only the required fields — Go's `omitempty`
//     makes the key set payload-dependent, so both ends need pinning),
//  3. every key the SDK sends is a key the backend actually declares
//     (read from the backend's own request records), which is the one
//     direction the first two families cannot see: the backend contract
//     changing under a stale SDK.
//
// This is the third SDK in the family to have this gap (the JS SDK in round
// 399 and the Java SDK in round 402); the Java SDK's fix lives in
// CortexMemWireFormatTest and the Java SDK's README publishes its wire names
// next to the same kind of table.

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"testing"

	"github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go"
	"github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/dto"
)

// ------------------------------------------------------------------
// Family 1 + 2: the complete key set, fully populated and minimal.
// ------------------------------------------------------------------

// wireCase is one body-carrying endpoint: how to call it, what the request
// looks like when every optional field is filled in, and what it looks like
// when only the required fields are set.
type wireCase struct {
	name string
	// endpoint is the path the SDK must hit.
	endpoint string
	// contract is the endpoint's identity in the backend's own contract
	// tables. It differs from `endpoint` where the SDK's request path
	// carries a concrete id while the contract names it as a variable.
	contract string
	// method is the HTTP method.
	method string
	// full is the request with every field populated.
	full func() (any, func(cortexmem.Client) error)
	// minimal is the request with only the required fields populated.
	minimal func() (any, func(cortexmem.Client) error)
	// fullKeys is the complete set of keys the backend receives for `full`.
	fullKeys []string
	// minimalKeys is the complete set of keys for `minimal`.
	minimalKeys []string
}

// wireCases lists every endpoint in this SDK that sends a request body.
// There are ten: eight request DTOs plus two raw maps/records built inside
// the client itself.
var wireCases = []wireCase{
	{
		name:     "POST /api/session/start",
		endpoint: "/api/session/start",
		contract: "POST /api/session/start",
		method:   http.MethodPost,
		full: func() (any, func(cortexmem.Client) error) {
			return dto.SessionStartRequest{
					SessionID:      "sess-1",
					ProjectPath:    "/proj",
					UserID:         "user-1",
					PlatformSource: "codex",
				}, func(c cortexmem.Client) error {
					_, err := c.StartSession(context.Background(), dto.SessionStartRequest{
						SessionID:      "sess-1",
						ProjectPath:    "/proj",
						UserID:         "user-1",
						PlatformSource: "codex",
					})
					return err
				}
		},
		minimal: func() (any, func(cortexmem.Client) error) {
			return dto.SessionStartRequest{
					SessionID:   "sess-1",
					ProjectPath: "/proj",
				}, func(c cortexmem.Client) error {
					_, err := c.StartSession(context.Background(), dto.SessionStartRequest{
						SessionID:   "sess-1",
						ProjectPath: "/proj",
					})
					return err
				}
		},
		fullKeys:    []string{"platform_source", "project_path", "session_id", "user_id"},
		minimalKeys: []string{"project_path", "session_id"},
	},
	{
		name:     "POST /api/ingest/tool-use",
		endpoint: "/api/ingest/tool-use",
		contract: "POST /api/ingest/tool-use",
		method:   http.MethodPost,
		full: func() (any, func(cortexmem.Client) error) {
			return dto.ObservationRequest{
					SessionID:      "sess-1",
					ProjectPath:    "/proj",
					ToolName:       "Edit",
					ToolInput:      map[string]any{"file": "main.go"},
					ToolResponse:   map[string]any{"ok": true},
					PromptNumber:   7,
					Source:         "manual",
					ExtractedData:  map[string]any{"k": "v"},
					PlatformSource: "codex",
				}, func(c cortexmem.Client) error {
					return c.RecordObservation(context.Background(), dto.ObservationRequest{
						SessionID:      "sess-1",
						ProjectPath:    "/proj",
						ToolName:       "Edit",
						ToolInput:      map[string]any{"file": "main.go"},
						ToolResponse:   map[string]any{"ok": true},
						PromptNumber:   7,
						Source:         "manual",
						ExtractedData:  map[string]any{"k": "v"},
						PlatformSource: "codex",
					})
				}
		},
		minimal: func() (any, func(cortexmem.Client) error) {
			return dto.ObservationRequest{
					SessionID:   "sess-1",
					ProjectPath: "/proj",
					ToolName:    "Edit",
				}, func(c cortexmem.Client) error {
					return c.RecordObservation(context.Background(), dto.ObservationRequest{
						SessionID:   "sess-1",
						ProjectPath: "/proj",
						ToolName:    "Edit",
					})
				}
		},
		fullKeys: []string{
			"cwd", "extractedData", "platform_source", "prompt_number", "session_id", "source",
			"tool_input", "tool_name", "tool_response",
		},
		minimalKeys: []string{"cwd", "session_id", "tool_name"},
	},
	{
		name:     "POST /api/ingest/session-end",
		endpoint: "/api/ingest/session-end",
		contract: "POST /api/ingest/session-end",
		method:   http.MethodPost,
		full: func() (any, func(cortexmem.Client) error) {
			return dto.SessionEndRequest{
					SessionID:            "sess-1",
					ProjectPath:          "/proj",
					LastAssistantMessage: "done",
					PlatformSource:       "codex",
				}, func(c cortexmem.Client) error {
					return c.RecordSessionEnd(context.Background(), dto.SessionEndRequest{
						SessionID:            "sess-1",
						ProjectPath:          "/proj",
						LastAssistantMessage: "done",
						PlatformSource:       "codex",
					})
				}
		},
		minimal: func() (any, func(cortexmem.Client) error) {
			return dto.SessionEndRequest{
					SessionID:   "sess-1",
					ProjectPath: "/proj",
				}, func(c cortexmem.Client) error {
					return c.RecordSessionEnd(context.Background(), dto.SessionEndRequest{
						SessionID:   "sess-1",
						ProjectPath: "/proj",
					})
				}
		},
		fullKeys:    []string{"cwd", "last_assistant_message", "platform_source", "session_id"},
		minimalKeys: []string{"cwd", "session_id"},
	},
	{
		name:     "POST /api/ingest/user-prompt",
		endpoint: "/api/ingest/user-prompt",
		contract: "POST /api/ingest/user-prompt",
		method:   http.MethodPost,
		full: func() (any, func(cortexmem.Client) error) {
			return dto.UserPromptRequest{
					SessionID:      "sess-1",
					PromptText:     "hello",
					ProjectPath:    "/proj",
					PromptNumber:   3,
					PlatformSource: "codex",
				}, func(c cortexmem.Client) error {
					return c.RecordUserPrompt(context.Background(), dto.UserPromptRequest{
						SessionID:      "sess-1",
						PromptText:     "hello",
						ProjectPath:    "/proj",
						PromptNumber:   3,
						PlatformSource: "codex",
					})
				}
		},
		minimal: func() (any, func(cortexmem.Client) error) {
			return dto.UserPromptRequest{
					SessionID:   "sess-1",
					PromptText:  "hello",
					ProjectPath: "/proj",
				}, func(c cortexmem.Client) error {
					return c.RecordUserPrompt(context.Background(), dto.UserPromptRequest{
						SessionID:   "sess-1",
						PromptText:  "hello",
						ProjectPath: "/proj",
					})
				}
		},
		fullKeys: []string{
			"cwd", "platform_source", "prompt_number", "prompt_text", "session_id",
		},
		minimalKeys: []string{"cwd", "prompt_text", "session_id"},
	},
	{
		name:     "POST /api/memory/experiences",
		endpoint: "/api/memory/experiences",
		contract: "POST /api/memory/experiences",
		method:   http.MethodPost,
		full: func() (any, func(cortexmem.Client) error) {
			return dto.ExperienceRequest{
					Task:             "task",
					Project:          "/proj",
					Count:            4,
					Source:           "manual",
					RequiredConcepts: []string{"a"},
					UserID:           "user-1",
				}, func(c cortexmem.Client) error {
					_, err := c.RetrieveExperiences(context.Background(), dto.ExperienceRequest{
						Task:             "task",
						Project:          "/proj",
						Count:            4,
						Source:           "manual",
						RequiredConcepts: []string{"a"},
						UserID:           "user-1",
					})
					return err
				}
		},
		minimal: func() (any, func(cortexmem.Client) error) {
			return dto.ExperienceRequest{
					Task: "task",
				}, func(c cortexmem.Client) error {
					_, err := c.RetrieveExperiences(context.Background(), dto.ExperienceRequest{Task: "task"})
					return err
				}
		},
		fullKeys: []string{
			"count", "project", "requiredConcepts", "source", "task", "userId",
		},
		minimalKeys: []string{"task"},
	},
	{
		name:     "POST /api/memory/icl-prompt",
		endpoint: "/api/memory/icl-prompt",
		contract: "POST /api/memory/icl-prompt",
		method:   http.MethodPost,
		full: func() (any, func(cortexmem.Client) error) {
			return dto.ICLPromptRequest{
					Task:     "task",
					Project:  "/proj",
					MaxChars: 4000,
					UserID:   "user-1",
				}, func(c cortexmem.Client) error {
					_, err := c.BuildICLPrompt(context.Background(), dto.ICLPromptRequest{
						Task:     "task",
						Project:  "/proj",
						MaxChars: 4000,
						UserID:   "user-1",
					})
					return err
				}
		},
		minimal: func() (any, func(cortexmem.Client) error) {
			return dto.ICLPromptRequest{
					Task: "task",
				}, func(c cortexmem.Client) error {
					_, err := c.BuildICLPrompt(context.Background(), dto.ICLPromptRequest{Task: "task"})
					return err
				}
		},
		fullKeys:    []string{"maxChars", "project", "task", "userId"},
		minimalKeys: []string{"task"},
	},
	{
		name:     "POST /api/observations/batch",
		endpoint: "/api/observations/batch",
		contract: "POST /api/observations/batch",
		method:   http.MethodPost,
		full: func() (any, func(cortexmem.Client) error) {
			return dto.BatchObservationsRequest{
					IDs: []string{"11111111-1111-1111-1111-111111111111"},
				}, func(c cortexmem.Client) error {
					_, err := c.GetObservationsByIds(context.Background(),
						[]string{"11111111-1111-1111-1111-111111111111"})
					return err
				}
		},
		minimal: func() (any, func(cortexmem.Client) error) {
			return dto.BatchObservationsRequest{
					IDs: []string{"11111111-1111-1111-1111-111111111111"},
				}, func(c cortexmem.Client) error {
					_, err := c.GetObservationsByIds(context.Background(),
						[]string{"11111111-1111-1111-1111-111111111111"})
					return err
				}
		},
		fullKeys:    []string{"ids"},
		minimalKeys: []string{"ids"},
	},
	{
		name:     "POST /api/memory/feedback",
		endpoint: "/api/memory/feedback",
		contract: "POST /api/memory/feedback",
		method:   http.MethodPost,
		full: func() (any, func(cortexmem.Client) error) {
			return dto.FeedbackRequest{
					ObservationID: "obs-1",
					FeedbackType:  "SUCCESS",
					Comment:       "good",
				}, func(c cortexmem.Client) error {
					return c.SubmitFeedback(context.Background(), "obs-1", "SUCCESS", "good")
				}
		},
		minimal: func() (any, func(cortexmem.Client) error) {
			return dto.FeedbackRequest{
					ObservationID: "obs-1",
					FeedbackType:  "SUCCESS",
				}, func(c cortexmem.Client) error {
					return c.SubmitFeedback(context.Background(), "obs-1", "SUCCESS", "")
				}
		},
		fullKeys:    []string{"comment", "feedbackType", "observationId"},
		minimalKeys: []string{"feedbackType", "observationId"},
	},
	{
		// The update carries two aliases for the same backend field, and
		// UpdateObservation refuses to send both at once (they would be
		// silently reconciled by the backend, content winning). The complete
		// key set is therefore pinned twice: once with `content`, once with
		// `narrative`. The two variants together are the eight keys the
		// backend reads — see family 3, which reads that list out of the
		// backend handler itself.
		name:     "PATCH /api/memory/observations/{id} (content variant)",
		endpoint: "/api/memory/observations/11111111-1111-1111-1111-111111111111",
		contract: "PATCH /api/memory/observations/{id}",
		method:   http.MethodPatch,
		full: func() (any, func(cortexmem.Client) error) {
			return dto.ObservationUpdate{
					Title:         strPtr("t"),
					Subtitle:      strPtr("s"),
					Content:       strPtr("c"),
					Facts:         []string{"f"},
					Concepts:      []string{"c1"},
					Source:        strPtr("manual"),
					ExtractedData: map[string]any{"k": "v"},
				}, func(c cortexmem.Client) error {
					return c.UpdateObservation(context.Background(),
						"11111111-1111-1111-1111-111111111111", dto.ObservationUpdate{
							Title:         strPtr("t"),
							Subtitle:      strPtr("s"),
							Content:       strPtr("c"),
							Facts:         []string{"f"},
							Concepts:      []string{"c1"},
							Source:        strPtr("manual"),
							ExtractedData: map[string]any{"k": "v"},
						})
				}
		},
		minimal: func() (any, func(cortexmem.Client) error) {
			return dto.ObservationUpdate{
					Title: strPtr("t"),
				}, func(c cortexmem.Client) error {
					return c.UpdateObservation(context.Background(),
						"11111111-1111-1111-1111-111111111111", dto.ObservationUpdate{
							Title: strPtr("t"),
						})
				}
		},
		fullKeys: []string{
			"concepts", "content", "extractedData", "facts", "source", "subtitle", "title",
		},
		minimalKeys: []string{"title"},
	},
	{
		name:     "PATCH /api/memory/observations/{id} (narrative variant)",
		endpoint: "/api/memory/observations/11111111-1111-1111-1111-111111111111",
		contract: "PATCH /api/memory/observations/{id}",
		method:   http.MethodPatch,
		full: func() (any, func(cortexmem.Client) error) {
			return dto.ObservationUpdate{
					Title:         strPtr("t"),
					Subtitle:      strPtr("s"),
					Narrative:     strPtr("n"),
					Facts:         []string{"f"},
					Concepts:      []string{"c1"},
					Source:        strPtr("manual"),
					ExtractedData: map[string]any{"k": "v"},
				}, func(c cortexmem.Client) error {
					return c.UpdateObservation(context.Background(),
						"11111111-1111-1111-1111-111111111111", dto.ObservationUpdate{
							Title:         strPtr("t"),
							Subtitle:      strPtr("s"),
							Narrative:     strPtr("n"),
							Facts:         []string{"f"},
							Concepts:      []string{"c1"},
							Source:        strPtr("manual"),
							ExtractedData: map[string]any{"k": "v"},
						})
				}
		},
		minimal: func() (any, func(cortexmem.Client) error) {
			return dto.ObservationUpdate{
					Title: strPtr("t"),
				}, func(c cortexmem.Client) error {
					return c.UpdateObservation(context.Background(),
						"11111111-1111-1111-1111-111111111111", dto.ObservationUpdate{
							Title: strPtr("t"),
						})
				}
		},
		fullKeys: []string{
			"concepts", "extractedData", "facts", "narrative", "source", "subtitle", "title",
		},
		minimalKeys: []string{"title"},
	},
	{
		// Built inline in the client, not from a DTO.
		name:     "PATCH /api/session/user",
		endpoint: "/api/session/user",
		contract: "PATCH /api/session/user",
		method:   http.MethodPatch,
		full: func() (any, func(cortexmem.Client) error) {
			return map[string]string{"user_id": "user-1"}, func(c cortexmem.Client) error {
				_, err := c.UpdateSessionUserId(context.Background(), "sess-1", "user-1")
				return err
			}
		},
		minimal: func() (any, func(cortexmem.Client) error) {
			return map[string]string{"user_id": "user-1"}, func(c cortexmem.Client) error {
				_, err := c.UpdateSessionUserId(context.Background(), "sess-1", "user-1")
				return err
			}
		},
		fullKeys:    []string{"user_id"},
		minimalKeys: []string{"user_id"},
	},
}

// captureBody runs one client call against a server that records the request
// body, and returns the recorded body's key set.
//
// The response is a benign JSON document so that the client-side parse of
// the response cannot be mistaken for a request-side failure.
func captureBody(t *testing.T, tc wireCase, call func(cortexmem.Client) error) (map[string]any, string, string) {
	t.Helper()
	var (
		gotPath   string
		gotMethod string
		gotBody   map[string]any
		seen      bool
	)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotPath = r.URL.Path
		gotMethod = r.Method
		seen = true
		if r.Body != nil {
			_ = json.NewDecoder(r.Body).Decode(&gotBody)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{}`))
	}))
	defer server.Close()

	client := cortexmem.NewClient(cortexmem.WithBaseURL(server.URL))
	_ = call(client)

	if !seen {
		t.Fatalf("%s: no request reached the server", tc.name)
	}
	if gotPath != tc.endpoint {
		t.Errorf("%s: path = %q, want %q", tc.name, gotPath, tc.endpoint)
	}
	if gotMethod != tc.method {
		t.Errorf("%s: method = %q, want %q", tc.name, gotMethod, tc.method)
	}
	return gotBody, gotPath, gotMethod
}

// keysOf returns the sorted key set of a decoded JSON object.
func keysOf(body map[string]any) []string {
	out := make([]string, 0, len(body))
	for k := range body {
		out = append(out, k)
	}
	sort.Strings(out)
	return out
}

// diffKeys renders the difference between the expected and actual key sets.
func diffKeys(want, got []string) string {
	wantSet := map[string]bool{}
	for _, k := range want {
		wantSet[k] = true
	}
	gotSet := map[string]bool{}
	for _, k := range got {
		gotSet[k] = true
	}
	var missing, unexpected []string
	for _, k := range want {
		if !gotSet[k] {
			missing = append(missing, k)
		}
	}
	for _, k := range got {
		if !wantSet[k] {
			unexpected = append(unexpected, k)
		}
	}
	return fmt.Sprintf("missing=%v unexpected=%v", missing, unexpected)
}

// TestWireFormat_CompleteKeySet pins the complete key set of every outbound
// request body, for a fully populated payload.
func TestWireFormat_CompleteKeySet(t *testing.T) {
	for _, tc := range wireCases {
		tc := tc
		t.Run(tc.name, func(t *testing.T) {
			_, call := tc.full()
			body, _, _ := captureBody(t, tc, call)
			got := keysOf(body)
			want := append([]string(nil), tc.fullKeys...)
			sort.Strings(want)
			if len(got) != len(want) || !equalStrings(got, want) {
				t.Errorf("%s: complete key set mismatch\n  want: %v\n  got:  %v\n  %s",
					tc.name, want, got, diffKeys(want, got))
			}
		})
	}
}

// TestWireFormat_MinimalKeySet pins the complete key set of every outbound
// request body, for a payload that carries only the required fields.
//
// Go's `omitempty` makes the key set payload-dependent: the same endpoint
// sends three keys for a full session start and two for a minimal one. The
// backend ignores unknown properties, so a field that stops being omitted
// (or starts being omitted) changes the wire with nothing turning red.
func TestWireFormat_MinimalKeySet(t *testing.T) {
	for _, tc := range wireCases {
		tc := tc
		t.Run(tc.name, func(t *testing.T) {
			_, call := tc.minimal()
			body, _, _ := captureBody(t, tc, call)
			got := keysOf(body)
			want := append([]string(nil), tc.minimalKeys...)
			sort.Strings(want)
			if len(got) != len(want) || !equalStrings(got, want) {
				t.Errorf("%s: minimal key set mismatch\n  want: %v\n  got:  %v\n  %s",
					tc.name, want, got, diffKeys(want, got))
			}
		})
	}
}

// ------------------------------------------------------------------
// Family 3: the backend contract, read from the backend's own source.
// ------------------------------------------------------------------

// backendRequestRecords maps an endpoint to the request record the backend
// declares for it. The records live in
// backend/src/main/java/com/ablueforce/cortexce/dto/ApiRequests.java.
var backendRequestRecords = map[string]string{
	"POST /api/session/start":      "SessionStartRequest",
	"PATCH /api/session/user":      "SessionUserUpdateRequest",
	"POST /api/ingest/tool-use":    "ToolUseRequest",
	"POST /api/ingest/session-end": "SessionEndRequest",
	"POST /api/ingest/user-prompt": "UserPromptRequest",
	"POST /api/memory/experiences": "ExperienceRequest",
	"POST /api/memory/icl-prompt":  "ICLPromptRequest",
	"POST /api/memory/feedback":    "FeedbackRequest",
	"POST /api/observations/batch": "BatchObservationsRequest",
}

// backendRoot locates the backend sources from this package's directory.
// The package lives at <repo>/go-sdk/cortex-mem-go, so the repo root is two
// levels up.
func backendRoot(t *testing.T) string {
	t.Helper()
	root, err := filepath.Abs(filepath.Join("..", "..", "backend"))
	if err != nil {
		t.Fatalf("cannot resolve the backend directory: %v", err)
	}
	if _, err := os.Stat(root); err != nil {
		t.Fatalf("cannot stat the backend directory %s: %v", root, err)
	}
	return root
}

var jsonPropertyRe = regexp.MustCompile(`@JsonProperty\("([^"]+)"\)`)

// recordKeys extracts the @JsonProperty names of one request record.
func recordKeys(source, record string) []string {
	idx := strings.Index(source, "record "+record+"(")
	if idx < 0 {
		return nil
	}
	end := strings.Index(source[idx:], ") {}")
	if end < 0 {
		return nil
	}
	body := source[idx : idx+end]
	seen := map[string]bool{}
	var keys []string
	for _, m := range jsonPropertyRe.FindAllStringSubmatch(body, -1) {
		if !seen[m[1]] {
			seen[m[1]] = true
			keys = append(keys, m[1])
		}
	}
	sort.Strings(keys)
	return keys
}

// TestWireFormat_SentKeysAreDeclaredByTheBackend binds the keys this SDK
// sends to the keys the backend declares.
//
// The two families above can only see changes made in THIS repository's SDK
// code. They cannot see the backend's contract changing underneath a stale
// SDK: the backend would accept the old key set, ignore the new field, and
// the SDK would keep shipping a payload the server no longer reads.
//
// The expectation is a subset relation, not equality: the backend accepts
// more keys than the SDK sends (platform_source, for instance, which no SDK
// sends). What must never happen is the SDK sending a key the backend does
// not declare — the backend ignores unknown properties silently, so such a
// key is a field that is written and never read.
func TestWireFormat_SentKeysAreDeclaredByTheBackend(t *testing.T) {
	root := backendRoot(t)

	apiRequestsPath := filepath.Join(root, "src", "main", "java", "com", "ablueforce",
		"cortexce", "dto", "ApiRequests.java")
	apiRequestsRaw, err := os.ReadFile(apiRequestsPath)
	if err != nil {
		t.Fatalf("cannot read %s: %v", apiRequestsPath, err)
	}
	apiRequests := string(apiRequestsRaw)

	for _, tc := range wireCases {
		tc := tc
		t.Run(tc.name, func(t *testing.T) {
			sent := append([]string(nil), tc.fullKeys...)
			sort.Strings(sent)

			var declared []string
			if record, ok := backendRequestRecords[tc.contract]; ok {
				declared = recordKeys(apiRequests, record)
				if len(declared) == 0 {
					t.Fatalf("cannot read the @JsonProperty names of %s out of %s",
						record, apiRequestsPath)
				}
			} else if tc.contract == "PATCH /api/memory/observations/{id}" {
				// This endpoint takes a raw Map<String, Object>; the keys it
				// acts on are the ones its handler asks for by name.
				declared = backendUpdateObservationKeys(t, root)
			} else {
				t.Fatalf("%s: no backend contract is bound for this endpoint", tc.name)
			}

			declaredSet := map[string]bool{}
			for _, k := range declared {
				declaredSet[k] = true
			}
			for _, k := range sent {
				if !declaredSet[k] {
					t.Errorf("%s: the SDK sends %q but the backend does not declare it\n  backend declares: %v",
						tc.name, k, declared)
				}
			}
		})
	}
}

// backendUpdateObservationKeys reads the keys the PATCH-observation handler
// acts on. The handler takes a raw Map, so the set of keys it honours is the
// set of names it asks for.
func backendUpdateObservationKeys(t *testing.T, root string) []string {
	t.Helper()
	path := filepath.Join(root, "src", "main", "java", "com", "ablueforce",
		"cortexce", "controller", "MemoryController.java")
	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("cannot read %s: %v", path, err)
	}
	source := string(raw)
	start := strings.Index(source, "public ResponseEntity<Map<String, Object>> updateObservation(")
	if start < 0 {
		t.Fatalf("cannot find updateObservation in %s", path)
	}
	end := strings.Index(source[start:], "\n    }")
	if end < 0 {
		end = len(source) - start
	}
	block := source[start : start+end]

	re := regexp.MustCompile(`body\.containsKey\("([^"]+)"\)`)
	seen := map[string]bool{}
	var keys []string
	for _, m := range re.FindAllStringSubmatch(block, -1) {
		if !seen[m[1]] {
			seen[m[1]] = true
			keys = append(keys, m[1])
		}
	}
	sort.Strings(keys)
	return keys
}

// equalStrings compares two sorted string slices.
func equalStrings(a, b []string) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}
