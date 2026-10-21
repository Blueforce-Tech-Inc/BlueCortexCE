package cortexmem_test

// This file pins the Go SDK's required-argument contract and binds the
// published table of required arguments to the runtime.
//
// Why this needs pinning. The README publishes an eighteen-row table of required
// arguments and states that the SDK "returns a ValidationError and sends no
// request" for every one of them. A per-guard single-variable mutation sweep of
// the Go SDK client -- one guard made permanently false at a time, the whole
// multi-module suite re-run, the source file restored byte for byte -- found
// that the guard set was almost entirely pinned, with two exceptions:
//
//   - GetObservation's blank-id check was pinned by no test at all;
//   - the "an empty BaseURL falls back to the published default" normalisation
//     had a test NAMED after it that asserted nothing about it.
//
// The published table itself was never compared with the runtime, so a guard
// appearing on (or disappearing from) a method could not turn anything red.
//
// Everything here is offline: the server is an httptest server, so no test can
// reach the resident backend.

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"os"
	"reflect"
	"sort"
	"strings"
	"sync/atomic"
	"testing"

	cortexmem "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go"
	"github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/dto"
)

// guardProbe is one required argument of one method, exercised with a blank
// value. The list is written out one entry per guard rather than derived: the
// point of the test is to pin the individual guards, so a guard that stops
// existing must make this table incomplete, not silently disappear.
type guardProbe struct {
	method  string
	field   string
	message string
	call    func(c cortexmem.Client) error
}

// requiredArgumentProbes covers every client-side argument guard in
// client_methods.go: one entry per required argument.
func requiredArgumentProbes() []guardProbe {
	tooMany := make([]string, 101)
	for i := range tooMany {
		tooMany[i] = "obs"
	}
	return []guardProbe{
		{"StartSession", "sessionID", "sessionID is required", func(c cortexmem.Client) error {
			_, err := c.StartSession(context.Background(), dto.SessionStartRequest{SessionID: "", ProjectPath: "/proj"})
			return err
		}},
		{"StartSession", "projectPath", "projectPath is required", func(c cortexmem.Client) error {
			_, err := c.StartSession(context.Background(), dto.SessionStartRequest{SessionID: "s1", ProjectPath: ""})
			return err
		}},
		{"UpdateSessionUserId", "sessionID", "sessionID is required", func(c cortexmem.Client) error {
			_, err := c.UpdateSessionUserId(context.Background(), "", "user-1")
			return err
		}},
		{"UpdateSessionUserId", "userID", "userID is required", func(c cortexmem.Client) error {
			_, err := c.UpdateSessionUserId(context.Background(), "s1", "")
			return err
		}},
		{"RecordObservation", "sessionID", "sessionID is required", func(c cortexmem.Client) error {
			return c.RecordObservation(context.Background(), dto.ObservationRequest{SessionID: "", ProjectPath: "/proj", ToolName: "Read"})
		}},
		{"RecordObservation", "projectPath", "projectPath is required", func(c cortexmem.Client) error {
			return c.RecordObservation(context.Background(), dto.ObservationRequest{SessionID: "s1", ProjectPath: "", ToolName: "Read"})
		}},
		{"RecordObservation", "toolName", "toolName is required", func(c cortexmem.Client) error {
			return c.RecordObservation(context.Background(), dto.ObservationRequest{SessionID: "s1", ProjectPath: "/proj", ToolName: ""})
		}},
		{"RecordSessionEnd", "sessionID", "sessionID is required", func(c cortexmem.Client) error {
			return c.RecordSessionEnd(context.Background(), dto.SessionEndRequest{SessionID: "", ProjectPath: "/proj"})
		}},
		{"RecordSessionEnd", "projectPath", "projectPath is required", func(c cortexmem.Client) error {
			return c.RecordSessionEnd(context.Background(), dto.SessionEndRequest{SessionID: "s1", ProjectPath: ""})
		}},
		{"RecordUserPrompt", "sessionID", "sessionID is required", func(c cortexmem.Client) error {
			return c.RecordUserPrompt(context.Background(), dto.UserPromptRequest{SessionID: "", PromptText: "hi", ProjectPath: "/proj"})
		}},
		{"RecordUserPrompt", "promptText", "promptText is required", func(c cortexmem.Client) error {
			return c.RecordUserPrompt(context.Background(), dto.UserPromptRequest{SessionID: "s1", PromptText: "", ProjectPath: "/proj"})
		}},
		{"RecordUserPrompt", "projectPath", "projectPath is required", func(c cortexmem.Client) error {
			return c.RecordUserPrompt(context.Background(), dto.UserPromptRequest{SessionID: "s1", PromptText: "hi", ProjectPath: ""})
		}},
		{"RetrieveExperiences", "task", "task is required", func(c cortexmem.Client) error {
			_, err := c.RetrieveExperiences(context.Background(), dto.ExperienceRequest{Task: ""})
			return err
		}},
		{"BuildICLPrompt", "task", "task is required", func(c cortexmem.Client) error {
			_, err := c.BuildICLPrompt(context.Background(), dto.ICLPromptRequest{Task: ""})
			return err
		}},
		{"Search", "project", "project is required", func(c cortexmem.Client) error {
			_, err := c.Search(context.Background(), dto.SearchRequest{Project: ""})
			return err
		}},
		{"GetObservation", "id", "id is required", func(c cortexmem.Client) error {
			_, err := c.GetObservation(context.Background(), "")
			return err
		}},
		{"GetObservationsByIds", "ids", "ids must not be empty", func(c cortexmem.Client) error {
			_, err := c.GetObservationsByIds(context.Background(), nil)
			return err
		}},
		{"GetObservationsByIds", "ids", "batch size exceeds maximum of 100 (got 101)", func(c cortexmem.Client) error {
			_, err := c.GetObservationsByIds(context.Background(), tooMany)
			return err
		}},
		{"GetObservationsByIds", "ids[1]", "id must not be empty", func(c cortexmem.Client) error {
			_, err := c.GetObservationsByIds(context.Background(), []string{"obs-1", "  "})
			return err
		}},
		{"TriggerRefinement", "projectPath", "projectPath is required", func(c cortexmem.Client) error {
			return c.TriggerRefinement(context.Background(), "")
		}},
		{"SubmitFeedback", "observationID", "observationID is required", func(c cortexmem.Client) error {
			return c.SubmitFeedback(context.Background(), "", "helpfulness", "")
		}},
		{"SubmitFeedback", "feedbackType", "feedbackType is required", func(c cortexmem.Client) error {
			return c.SubmitFeedback(context.Background(), "obs-1", "", "")
		}},
		{"UpdateObservation", "observationID", "observationID is required", func(c cortexmem.Client) error {
			return c.UpdateObservation(context.Background(), "", dto.ObservationUpdate{})
		}},
		{"DeleteObservation", "observationID", "observationID is required", func(c cortexmem.Client) error {
			return c.DeleteObservation(context.Background(), "")
		}},
		{"GetQualityDistribution", "projectPath", "projectPath is required", func(c cortexmem.Client) error {
			_, err := c.GetQualityDistribution(context.Background(), "")
			return err
		}},
		{"TriggerExtraction", "projectPath", "projectPath is required", func(c cortexmem.Client) error {
			return c.TriggerExtraction(context.Background(), "")
		}},
		{"GetLatestExtraction", "projectPath", "projectPath is required", func(c cortexmem.Client) error {
			_, err := c.GetLatestExtraction(context.Background(), "", "tpl", "")
			return err
		}},
		{"GetLatestExtraction", "templateName", "templateName is required", func(c cortexmem.Client) error {
			_, err := c.GetLatestExtraction(context.Background(), "/proj", "", "")
			return err
		}},
		{"GetExtractionHistory", "projectPath", "projectPath is required", func(c cortexmem.Client) error {
			_, err := c.GetExtractionHistory(context.Background(), "", "tpl", "", 10)
			return err
		}},
		{"GetExtractionHistory", "templateName", "templateName is required", func(c cortexmem.Client) error {
			_, err := c.GetExtractionHistory(context.Background(), "/proj", "", "", 10)
			return err
		}},
		{"GetExtractionHistory", "limit", "limit must not be negative", func(c cortexmem.Client) error {
			_, err := c.GetExtractionHistory(context.Background(), "/proj", "tpl", "", -1)
			return err
		}},
	}
}

// TestRequiredArgumentsAreRejectedBeforeAnyRequest pins one case per required
// argument: the error type, the field named in the message, the message body
// and -- the part that makes the guard meaningful -- the fact that no request
// was sent. A client that rejected the call only after it had already sent it
// would satisfy the first three.
func TestRequiredArgumentsAreRejectedBeforeAnyRequest(t *testing.T) {
	for _, probe := range requiredArgumentProbes() {
		t.Run(probe.method+"/"+probe.field, func(t *testing.T) {
			// newValidationClient points the client at a hermetic server that
			// fails the test if it ever receives a request.
			client := newValidationClient(t)

			err := probe.call(client)
			if err == nil {
				t.Fatalf("%s: a blank %s must be rejected, got no error", probe.method, probe.field)
			}

			var ve *cortexmem.ValidationError
			if !errors.As(err, &ve) {
				t.Fatalf("%s: expected *cortexmem.ValidationError, got %T: %v", probe.method, err, err)
			}
			if ve.Field != probe.field {
				t.Errorf("%s: expected Field=%q, got %q", probe.method, probe.field, ve.Field)
			}
			if ve.Message != probe.message {
				t.Errorf("%s: expected Message=%q, got %q", probe.method, probe.message, ve.Message)
			}
			want := "cortex-ce: validation error on " + probe.field + ": " + probe.message
			if got := err.Error(); got != want {
				t.Errorf("%s: expected the full message\n  want: %q\n  got:  %q", probe.method, want, got)
			}
			if !cortexmem.IsValidationError(err) {
				t.Errorf("%s: IsValidationError(err) must be true for a blank %s", probe.method, probe.field)
			}
		})
	}
}

// TestEmptyUpdateThrowsTheOtherErrorType is the one case in the published
// table whose error is NOT a *cortexmem.ValidationError: the empty update
// throws *dto.ObservationUpdateValidationError, and IsValidationError says
// false for it. The README's "Required Arguments Are Checked Client-Side"
// section used to state a single error type for every row of its own table,
// which is wrong for this row -- so the type distinction is pinned here.
func TestEmptyUpdateThrowsTheOtherErrorType(t *testing.T) {
	client := newValidationClient(t)

	err := client.UpdateObservation(context.Background(), "obs-1", dto.ObservationUpdate{})
	if err == nil {
		t.Fatal("an empty update must be rejected")
	}

	var ove *dto.ObservationUpdateValidationError
	if !errors.As(err, &ove) {
		t.Fatalf("expected *dto.ObservationUpdateValidationError, got %T: %v", err, err)
	}
	if ove.Field != "update" {
		t.Errorf("expected Field=%q, got %q", "update", ove.Field)
	}
	if cortexmem.IsValidationError(err) {
		t.Error("IsValidationError(err) must be false for an empty update: it is a different Go type")
	}
	if got := err.Error(); got != "cortex-ce: ObservationUpdate validation error: at least one field must be provided for update" {
		t.Errorf("unexpected message\n  want: %q\n  got:  %q",
			"cortex-ce: ObservationUpdate validation error: at least one field must be provided for update", got)
	}
}

// TestPositiveControlsSendExactlyOneRequest is the control for the guard cases
// above. Without it, "no request was sent" could hold vacuously: a client that
// never sent anything would satisfy every guard case.
func TestPositiveControlsSendExactlyOneRequest(t *testing.T) {
	cases := []struct {
		name string
		call func(c cortexmem.Client) error
	}{
		{"StartSession", func(c cortexmem.Client) error {
			_, err := c.StartSession(context.Background(), dto.SessionStartRequest{SessionID: "s1", ProjectPath: "/proj"})
			return err
		}},
		{"RecordObservation", func(c cortexmem.Client) error {
			return c.RecordObservation(context.Background(), dto.ObservationRequest{SessionID: "s1", ProjectPath: "/proj", ToolName: "Read"})
		}},
		{"RecordSessionEnd", func(c cortexmem.Client) error {
			return c.RecordSessionEnd(context.Background(), dto.SessionEndRequest{SessionID: "s1", ProjectPath: "/proj"})
		}},
		{"RecordUserPrompt", func(c cortexmem.Client) error {
			return c.RecordUserPrompt(context.Background(), dto.UserPromptRequest{SessionID: "s1", PromptText: "hi", ProjectPath: "/proj"})
		}},
		{"Search", func(c cortexmem.Client) error {
			_, err := c.Search(context.Background(), dto.SearchRequest{Project: "/proj"})
			return err
		}},
		{"GetObservation", func(c cortexmem.Client) error {
			_, err := c.GetObservation(context.Background(), "obs-1")
			return err
		}},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			client, hits := newCountingClient(t)
			_ = tc.call(client)
			if n := atomic.LoadInt64(hits); n != 1 {
				t.Errorf("%s: expected exactly 1 request, got %d", tc.name, n)
			}
		})
	}
}

// TestPublishedRequiredArgumentTableMatchesRuntime binds the published table
// to the runtime: the set of methods that reject a blank argument is derived
// from behaviour, and must equal the row set of the table in both languages.
//
// The set is derived by calling every API method with blank arguments and
// recording only those that rejected the call before any request went out.
func TestPublishedRequiredArgumentTableMatchesRuntime(t *testing.T) {
	client, hits := newCountingClient(t)

	guarded := guardedMethodNames(client, hits)

	if len(guarded) == 0 {
		t.Fatal("no method rejected a blank argument - the comparison below would hold vacuously")
	}

	for _, name := range []string{"README.md", "README-zh-CN.md"} {
		published := publishedRequiredArgumentTable(t, name)
		if len(published) == 0 {
			t.Fatalf("%s: the published table is empty - the comparison would hold vacuously", name)
		}
		if !equalStringSets(published, guarded) {
			t.Errorf("%s: the published table does not match the runtime\n  published: %v\n  runtime:   %v",
				name, published, guarded)
		}
	}
}

// TestExposedAPIMethodCountIsPinned pins the SDK's API surface. Close and
// String are lifecycle and debug members, not API methods, so the interface's
// 27 methods are 25 API methods.
func TestExposedAPIMethodCountIsPinned(t *testing.T) {
	typ := reflect.TypeOf((*cortexmem.Client)(nil)).Elem()
	api := 0
	for i := 0; i < typ.NumMethod(); i++ {
		name := typ.Method(i).Name
		if name == "Close" || name == "String" {
			continue
		}
		api++
	}
	if api != 25 {
		t.Errorf("expected 25 API methods, got %d", api)
	}
}

// TestPublishedDefaultBaseURLMatchesRuntime pins the base URL the README
// publishes in its configuration table against the value the SDK actually
// uses. The internal test pins the other half of the chain: that an empty
// BaseURL falls back to that same value.
func TestPublishedDefaultBaseURLMatchesRuntime(t *testing.T) {
	published := publishedDefaultBaseURL(t)
	if published == "" {
		t.Fatal("the README does not publish a default base URL - the comparison would hold vacuously")
	}
	if got := cortexmem.DefaultClientConfig().BaseURL; got != published {
		t.Errorf("the README publishes the default base URL %q, the SDK uses %q", published, got)
	}
}

// guardedMethodNames calls every API method with blank arguments and returns
// the names of those that rejected the call before any request was sent.
func guardedMethodNames(client cortexmem.Client, hits *int64) []string {
	ctx := context.Background()
	probes := map[string]func(cortexmem.Client) error{
		"StartSession": func(c cortexmem.Client) error {
			_, err := c.StartSession(ctx, dto.SessionStartRequest{})
			return err
		},
		"UpdateSessionUserId": func(c cortexmem.Client) error {
			_, err := c.UpdateSessionUserId(ctx, "", "")
			return err
		},
		"RecordObservation": func(c cortexmem.Client) error {
			return c.RecordObservation(ctx, dto.ObservationRequest{})
		},
		"RecordSessionEnd": func(c cortexmem.Client) error {
			return c.RecordSessionEnd(ctx, dto.SessionEndRequest{})
		},
		"RecordUserPrompt": func(c cortexmem.Client) error {
			return c.RecordUserPrompt(ctx, dto.UserPromptRequest{})
		},
		"RetrieveExperiences": func(c cortexmem.Client) error {
			_, err := c.RetrieveExperiences(ctx, dto.ExperienceRequest{})
			return err
		},
		"BuildICLPrompt": func(c cortexmem.Client) error {
			_, err := c.BuildICLPrompt(ctx, dto.ICLPromptRequest{})
			return err
		},
		"Search": func(c cortexmem.Client) error {
			_, err := c.Search(ctx, dto.SearchRequest{})
			return err
		},
		"ListObservations": func(c cortexmem.Client) error {
			_, err := c.ListObservations(ctx, dto.ObservationsRequest{})
			return err
		},
		"GetObservation": func(c cortexmem.Client) error {
			_, err := c.GetObservation(ctx, "")
			return err
		},
		"GetObservationsByIds": func(c cortexmem.Client) error {
			_, err := c.GetObservationsByIds(ctx, []string{" "})
			return err
		},
		"TriggerRefinement": func(c cortexmem.Client) error {
			return c.TriggerRefinement(ctx, "")
		},
		"SubmitFeedback": func(c cortexmem.Client) error {
			return c.SubmitFeedback(ctx, "", "", "")
		},
		"UpdateObservation": func(c cortexmem.Client) error {
			return c.UpdateObservation(ctx, "", dto.ObservationUpdate{})
		},
		"DeleteObservation": func(c cortexmem.Client) error {
			return c.DeleteObservation(ctx, "")
		},
		"GetQualityDistribution": func(c cortexmem.Client) error {
			_, err := c.GetQualityDistribution(ctx, "")
			return err
		},
		"HealthCheck": func(c cortexmem.Client) error {
			return c.HealthCheck(ctx)
		},
		"TriggerExtraction": func(c cortexmem.Client) error {
			return c.TriggerExtraction(ctx, "")
		},
		"GetLatestExtraction": func(c cortexmem.Client) error {
			_, err := c.GetLatestExtraction(ctx, "", "", "")
			return err
		},
		"GetExtractionHistory": func(c cortexmem.Client) error {
			_, err := c.GetExtractionHistory(ctx, "", "", "", 0)
			return err
		},
		"GetVersion": func(c cortexmem.Client) error {
			_, err := c.GetVersion(ctx)
			return err
		},
		"GetProjects": func(c cortexmem.Client) error {
			_, err := c.GetProjects(ctx)
			return err
		},
		"GetStats": func(c cortexmem.Client) error {
			_, err := c.GetStats(ctx, "")
			return err
		},
		"GetModes": func(c cortexmem.Client) error {
			_, err := c.GetModes(ctx)
			return err
		},
		"GetSettings": func(c cortexmem.Client) error {
			_, err := c.GetSettings(ctx)
			return err
		},
	}

	guarded := []string{}
	for name, probe := range probes {
		before := atomic.LoadInt64(hits)
		err := probe(client)
		after := atomic.LoadInt64(hits)
		var ve *cortexmem.ValidationError
		if errors.As(err, &ve) && after == before {
			guarded = append(guarded, name)
		}
	}
	sort.Strings(guarded)
	return guarded
}

// TestRequiredArgumentsSectionNamesBothErrorTypes pins the wording of the
// "Required Arguments Are Checked Client-Side" section.
//
// It used to state a single error type for every row of its own table, but the
// empty-update case of UpdateObservation throws a different Go type -- and the
// README says so itself, 40 lines earlier, in "Empty Updates Are Rejected". A
// caller who read only this section would catch the wrong type and, for the
// empty update, would be told by IsValidationError that it is not a validation
// error at all.
//
// The check is deliberately about WHICH types are named, not about the exact
// sentence: a rewording that keeps both names passes, a rewording that drops
// the exception fails.
func TestRequiredArgumentsSectionNamesBothErrorTypes(t *testing.T) {
	for _, tc := range []struct {
		file    string
		heading string
	}{
		{"README.md", "### Required Arguments Are Checked Client-Side"},
		{"README-zh-CN.md", "### 必填参数在客户端校验"},
	} {
		t.Run(tc.file, func(t *testing.T) {
			section := readReadmeSection(t, tc.file, tc.heading)
			if !strings.Contains(section, "ValidationError") {
				t.Errorf("%s: the required-arguments section must name the ValidationError it returns", tc.file)
			}
			if !strings.Contains(section, "dto.ObservationUpdateValidationError") {
				t.Errorf("%s: the required-arguments section must name the other error type "+
					"(dto.ObservationUpdateValidationError) -- its own UpdateObservation row produces it", tc.file)
			}
		})
	}
}

// readReadmeSection returns the body of one section of a README, from the
// heading up to the next heading of the same level.
func readReadmeSection(t *testing.T, name, heading string) string {
	t.Helper()
	lines := readReadmeLines(t, name)
	start := -1
	for i, line := range lines {
		if strings.TrimSpace(line) == heading {
			start = i
			break
		}
	}
	if start < 0 {
		t.Fatalf("%s: section %q not found", name, heading)
	}
	var body strings.Builder
	for _, line := range lines[start+1:] {
		if strings.HasPrefix(line, "### ") {
			break
		}
		body.WriteString(line)
		body.WriteString("\n")
	}
	return body.String()
}

// newCountingClient returns a client pointed at a local server that answers
// every request with an empty JSON object, plus the counter of requests it
// received.
func newCountingClient(t *testing.T) (cortexmem.Client, *int64) {
	t.Helper()
	var hits int64
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt64(&hits, 1)
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{}`))
	}))
	t.Cleanup(server.Close)
	return cortexmem.NewClient(cortexmem.WithBaseURL(server.URL)), &hits
}

// publishedRequiredArgumentTable reads the required-argument table out of one
// of the two READMEs and returns the method names it lists.
func publishedRequiredArgumentTable(t *testing.T, name string) []string {
	t.Helper()
	header := "| Method | Required arguments |"
	if name == "README-zh-CN.md" {
		header = "| 方法 | 必填参数 |"
	}
	lines := readReadmeLines(t, name)
	methods := []string{}
	inTable := false
	for _, line := range lines {
		if !inTable {
			inTable = strings.HasPrefix(line, header)
			continue
		}
		if !strings.HasPrefix(line, "|") {
			break
		}
		cells := strings.Split(line, "|")
		if len(cells) < 2 {
			continue
		}
		first := strings.TrimSpace(cells[1])
		if first == "" || strings.HasPrefix(first, "-") {
			continue
		}
		methods = append(methods, strings.Trim(first, "`"))
	}
	sort.Strings(methods)
	return methods
}

// publishedDefaultBaseURL reads the default base URL out of the README's
// configuration table.
func publishedDefaultBaseURL(t *testing.T) string {
	t.Helper()
	for _, name := range []string{"README.md", "README-zh-CN.md"} {
		for _, line := range readReadmeLines(t, name) {
			if !strings.HasPrefix(line, "| `WithBaseURL` |") {
				continue
			}
			cells := strings.Split(line, "|")
			if len(cells) < 3 {
				continue
			}
			return strings.Trim(strings.TrimSpace(cells[2]), "`")
		}
	}
	return ""
}

// readReadmeLines reads one of the two READMEs from the SDK root, which is the
// package directory the test runs in.
func readReadmeLines(t *testing.T, name string) []string {
	t.Helper()
	data, err := os.ReadFile(name)
	if err != nil {
		t.Fatalf("cannot read %s: %v", name, err)
	}
	return strings.Split(strings.ReplaceAll(string(data), "\r\n", "\n"), "\n")
}

// equalStringSets compares two sorted string slices.
func equalStringSets(a, b []string) bool {
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
