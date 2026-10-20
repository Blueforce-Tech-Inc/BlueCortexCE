package main

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

// TestRegisterFallback_UnknownPathAnswersJSON pins the demo's own README
// contract: "Every error is {"error": "<message>"}".
//
// http.ServeMux answers an unmatched path with its own plain-text 404
// ("404 page not found", Content-Type text/plain). That made this the one
// error a caller was most likely to hit — a typo in the path — answer with a
// body the other three demos never send: measured live before this handler,
// Go answered text/plain while Python, JS and Java all answered
// application/json.
func TestRegisterFallback_UnknownPathAnswersJSON(t *testing.T) {
	mux := http.NewServeMux()
	registerFallback(mux)

	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/no-such-endpoint-xyz", nil))

	if rec.Code != http.StatusNotFound {
		t.Errorf("status = %d, want 404", rec.Code)
	}
	if ct := rec.Header().Get("Content-Type"); ct != "application/json" {
		t.Errorf("Content-Type = %q, want application/json", ct)
	}
	if got := decodeErrorBody(t, rec); got != "not found: /no-such-endpoint-xyz" {
		t.Errorf("error = %q, want %q", got, "not found: /no-such-endpoint-xyz")
	}
}

// TestRegisterFallback_DoesNotShadowRegisteredRoutes guards the other half of
// the change: registering "/" must not take over a path that already has a
// more specific pattern. Go's ServeMux picks the most specific pattern, so
// the fallback only sees what nothing else matched.
func TestRegisterFallback_DoesNotShadowRegisteredRoutes(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		if !checkMethod(w, r, http.MethodGet) {
			return
		}
		writeJSON(w, map[string]string{"status": "ok"})
	})
	mux.HandleFunc("/observations/{id}", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, map[string]string{"id": r.PathValue("id")})
	})
	registerFallback(mux)

	cases := []struct {
		name   string
		method string
		path   string
		want   int
	}{
		{"exact route still wins", http.MethodGet, "/health", http.StatusOK},
		{"parameterised route still wins", http.MethodGet, "/observations/abc", http.StatusOK},
		{"wrong method on a known route is still 405", http.MethodPost, "/health", http.StatusMethodNotAllowed},
		{"unknown path falls through to the fallback", http.MethodGet, "/nope", http.StatusNotFound},
	}
	for _, tt := range cases {
		t.Run(tt.name, func(t *testing.T) {
			rec := httptest.NewRecorder()
			mux.ServeHTTP(rec, httptest.NewRequest(tt.method, tt.path, nil))
			if rec.Code != tt.want {
				t.Errorf("%s %s -> status %d, want %d", tt.method, tt.path, rec.Code, tt.want)
			}
		})
	}
}
