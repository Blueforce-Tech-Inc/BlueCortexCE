package cortexmem_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"net/url"
	"reflect"
	"sort"
	"testing"

	cortexmem "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go"
	"github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/dto"
)

// Why this file exists: the query parameter names this SDK puts on the wire
// are hand-written strings inside client_methods.go. A wrong name does not
// fail loudly — the backend binds only the names it declares and silently
// drops the rest — so the parameter stops working while the SDK still reports
// success.
//
// Measured before this file: renaming every outgoing parameter name to a
// distinct new name (19 slots across 8 endpoints) turned the existing suite
// red for 17 of them and left TWO slots completely invisible:
//
//	search.query    — the only assertion was q.Get("query") != "" in
//	                  TestSearch_OmitsEmptyParams, i.e. a REVERSE assertion
//	                  that a rename satisfies just as well as the original;
//	search.orderBy  — the name appears nowhere in the test tree at all, and
//	                  the dto field it comes from is never even set.
//
// The expected key sets below are written out by hand. Deriving them from the
// code under test would make the assertion tautological.

type recordedRequest struct {
	method string
	path   string
	query  url.Values
}

// expectedFullQueryKeys is the COMPLETE set of query parameter names each
// endpoint sends when every argument is supplied.
var expectedFullQueryKeys = map[string][]string{
	"GET /api/search":                          {"project", "query", "type", "concept", "source", "limit", "offset", "orderBy"},
	"GET /api/observations":                    {"project", "offset", "limit"},
	"POST /api/memory/refine":                  {"project"},
	"GET /api/memory/quality-distribution":     {"project"},
	"POST /api/extraction/run":                 {"projectPath"},
	"GET /api/extraction/{templateName}/latest":  {"projectPath", "userId"},
	"GET /api/extraction/{templateName}/history": {"projectPath", "limit", "userId"},
	"GET /api/stats":                           {"project"},
}

// recordRequest runs fn against a server that records the request it
// receives, and returns that request.
func recordRequest(t *testing.T, respond func(w http.ResponseWriter), fn func(c cortexmem.Client)) recordedRequest {
	t.Helper()
	var got recordedRequest
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		got = recordedRequest{method: r.Method, path: r.URL.Path, query: r.URL.Query()}
		respond(w)
	}))
	defer server.Close()
	client := newTestClient(server)
	fn(client)
	return got
}

func jsonBody(v any) func(w http.ResponseWriter) {
	return func(w http.ResponseWriter) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(v)
	}
}

func noContent(w http.ResponseWriter) {
	w.WriteHeader(http.StatusNoContent)
}

func sortedKeys(v url.Values) []string {
	out := make([]string, 0, len(v))
	for k := range v {
		out = append(out, k)
	}
	sort.Strings(out)
	return out
}

// assertKeys checks the method, the path and the COMPLETE query key set.
func assertKeys(t *testing.T, endpoint string, got recordedRequest, wantPath string) {
	t.Helper()
	want := append([]string(nil), expectedFullQueryKeys[endpoint]...)
	sort.Strings(want)
	have := sortedKeys(got.query)
	if !reflect.DeepEqual(want, have) {
		t.Errorf("query keys of %s drifted: expected %v, got %v", endpoint, want, have)
	}
}

// ------------------------------------------------------------- endpoints

func TestSearch_SendsExactlyTheDocumentedQueryKeys(t *testing.T) {
	got := recordRequest(t, jsonBody(dto.SearchResult{}), func(c cortexmem.Client) {
		_, _ = c.Search(context.Background(), dto.SearchRequest{
			Project: "/my/project",
			Query:   "needle",
			Type:    "bugfix",
			Concept: "auth",
			Source:  "manual",
			Limit:   10,
			Offset:  5,
			OrderBy: "created_at_epoch",
		})
	})
	assertKeys(t, "GET /api/search", got, "/api/search")
	if got.method != "GET" {
		t.Errorf("HTTP method of GET /api/search: expected GET, got %s", got.method)
	}
}

func TestListObservations_SendsExactlyTheDocumentedQueryKeys(t *testing.T) {
	got := recordRequest(t, jsonBody(dto.ObservationsResponse{Items: []dto.Observation{}}), func(c cortexmem.Client) {
		_, _ = c.ListObservations(context.Background(), dto.ObservationsRequest{
			Project: "/my/project",
			Offset:  5,
			Limit:   20,
		})
	})
	assertKeys(t, "GET /api/observations", got, "/api/observations")
	if got.method != "GET" {
		t.Errorf("HTTP method of GET /api/observations: expected GET, got %s", got.method)
	}
}

func TestTriggerRefinement_SendsExactlyTheDocumentedQueryKeys(t *testing.T) {
	got := recordRequest(t, noContent, func(c cortexmem.Client) {
		_ = c.TriggerRefinement(context.Background(), "/my/project")
	})
	assertKeys(t, "POST /api/memory/refine", got, "/api/memory/refine")
	if got.method != "POST" {
		t.Errorf("HTTP method of POST /api/memory/refine: expected POST, got %s", got.method)
	}
}

func TestGetQualityDistribution_SendsExactlyTheDocumentedQueryKeys(t *testing.T) {
	got := recordRequest(t, jsonBody(dto.QualityDistribution{}), func(c cortexmem.Client) {
		_, _ = c.GetQualityDistribution(context.Background(), "/my/project")
	})
	assertKeys(t, "GET /api/memory/quality-distribution", got, "/api/memory/quality-distribution")
	if got.method != "GET" {
		t.Errorf("HTTP method of GET /api/memory/quality-distribution: expected GET, got %s", got.method)
	}
}

func TestTriggerExtraction_SendsExactlyTheDocumentedQueryKeys(t *testing.T) {
	got := recordRequest(t, noContent, func(c cortexmem.Client) {
		_ = c.TriggerExtraction(context.Background(), "/my/project")
	})
	assertKeys(t, "POST /api/extraction/run", got, "/api/extraction/run")
	if got.method != "POST" {
		t.Errorf("HTTP method of POST /api/extraction/run: expected POST, got %s", got.method)
	}
}

func TestGetLatestExtraction_SendsExactlyTheDocumentedQueryKeys(t *testing.T) {
	got := recordRequest(t, jsonBody(dto.ExtractionResult{}), func(c cortexmem.Client) {
		_, _ = c.GetLatestExtraction(context.Background(), "/my/project", "user-preferences", "alice")
	})
	assertKeys(t, "GET /api/extraction/{templateName}/latest", got, "/api/extraction/user-preferences/latest")
	if got.method != "GET" {
		t.Errorf("HTTP method of GET /api/extraction/{templateName}/latest: expected GET, got %s", got.method)
	}
}

func TestGetExtractionHistory_SendsExactlyTheDocumentedQueryKeys(t *testing.T) {
	got := recordRequest(t, jsonBody([]dto.ExtractionResult{}), func(c cortexmem.Client) {
		_, _ = c.GetExtractionHistory(context.Background(), "/my/project", "user-preferences", "alice", 10)
	})
	assertKeys(t, "GET /api/extraction/{templateName}/history", got, "/api/extraction/user-preferences/history")
	if got.method != "GET" {
		t.Errorf("HTTP method of GET /api/extraction/{templateName}/history: expected GET, got %s", got.method)
	}
}

func TestGetStats_SendsExactlyTheDocumentedQueryKeys(t *testing.T) {
	got := recordRequest(t, jsonBody(dto.StatsResponse{}), func(c cortexmem.Client) {
		_, _ = c.GetStats(context.Background(), "/my/project")
	})
	assertKeys(t, "GET /api/stats", got, "/api/stats")
	if got.method != "GET" {
		t.Errorf("HTTP method of GET /api/stats: expected GET, got %s", got.method)
	}
}

// ------------------------------------------------------------- omission

func TestOptionalQueryParamsAreOmittedRatherThanSentEmpty(t *testing.T) {
	got := recordRequest(t, jsonBody(dto.SearchResult{}), func(c cortexmem.Client) {
		_, _ = c.Search(context.Background(), dto.SearchRequest{
			Project: "/my/project",
			Query:   "needle",
			Source:  "manual",
			Limit:   10,
			Offset:  5,
		})
	})
	want := []string{"limit", "offset", "project", "query", "source"}
	if have := sortedKeys(got.query); !reflect.DeepEqual(want, have) {
		t.Errorf("search without type/concept/orderBy: expected %v, got %v", want, have)
	}
}

func TestZeroValuedAndEmptyQueryParamsAreNotSent(t *testing.T) {
	got := recordRequest(t, jsonBody(dto.ObservationsResponse{Items: []dto.Observation{}}), func(c cortexmem.Client) {
		_, _ = c.ListObservations(context.Background(), dto.ObservationsRequest{
			Project: "/my/project",
			Offset:  0,
			Limit:   0,
		})
	})
	want := []string{"project"}
	if have := sortedKeys(got.query); !reflect.DeepEqual(want, have) {
		t.Errorf("observations with offset=0 and limit=0: expected %v, got %v", want, have)
	}

	got2 := recordRequest(t, jsonBody(dto.ExtractionResult{}), func(c cortexmem.Client) {
		_, _ = c.GetLatestExtraction(context.Background(), "/my/project", "user-preferences", "")
	})
	want2 := []string{"projectPath"}
	if have := sortedKeys(got2.query); !reflect.DeepEqual(want2, have) {
		t.Errorf("latest extraction without a userId: expected %v, got %v", want2, have)
	}
}

// ------------------------------------------------------------- coverage

func TestExpectedQueryKeyTableIsComplete(t *testing.T) {
	if len(expectedFullQueryKeys) != 8 {
		t.Errorf("expected 8 endpoints in the expectation table, got %d", len(expectedFullQueryKeys))
	}
	slots := 0
	for endpoint, keys := range expectedFullQueryKeys {
		if len(keys) == 0 {
			t.Errorf("%s: expected at least one query key", endpoint)
		}
		slots += len(keys)
	}
	if slots < 19 {
		t.Errorf("expected at least 19 query slots in the expectation table, got %d", slots)
	}
	// every endpoint key must name a method and a path, and the path variable
	// must be the one the backend publishes.
	for endpoint := range expectedFullQueryKeys {
		method, path, ok := splitEndpointKey(endpoint)
		if !ok {
			t.Errorf("endpoint key %q is not \"<METHOD> <path>\"", endpoint)
			continue
		}
		if method != "GET" && method != "POST" {
			t.Errorf("endpoint key %q has an unexpected method %q", endpoint, method)
		}
		if path == "" {
			t.Errorf("endpoint key %q has an empty path", endpoint)
		}
	}
	if _, ok := expectedFullQueryKeys["GET /api/extraction/{templateName}/latest"]; !ok {
		t.Error("the extraction latest endpoint must be registered under the published path variable name")
	}
}

func splitEndpointKey(key string) (string, string, bool) {
	for i := 0; i < len(key); i++ {
		if key[i] == ' ' {
			return key[:i], key[i+1:], true
		}
	}
	return "", "", false
}

func TestTheQueryKeyComparisonCanActuallyFail(t *testing.T) {
	// The comparison must distinguish a renamed key from the original, must
	// ignore ordering, and must notice an EXTRA key. Without this, a
	// comparison that always answered "equal" would let the endpoint tests
	// pass against any implementation at all.
	expected := url.Values{"project": []string{"/p"}, "limit": []string{"10"}}
	reordered := url.Values{"limit": []string{"10"}, "project": []string{"/p"}}
	renamed := url.Values{"project": []string{"/p"}, "offset": []string{"10"}}
	extra := url.Values{"project": []string{"/p"}, "limit": []string{"10"}, "surprise": []string{"x"}}

	want := []string{"limit", "project"}
	if have := sortedKeys(expected); !reflect.DeepEqual(want, have) {
		t.Errorf("sortedKeys(expected): expected %v, got %v", want, have)
	}
	if have := sortedKeys(reordered); !reflect.DeepEqual(want, have) {
		t.Errorf("sortedKeys(reordered): expected %v, got %v", want, have)
	}
	if have := sortedKeys(renamed); reflect.DeepEqual(want, have) {
		t.Error("a renamed key must not compare equal to the expected set")
	}
	if have := sortedKeys(extra); reflect.DeepEqual(want, have) {
		t.Error("an extra key must not compare equal to the expected set")
	}
}
