package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"

	cortexmem "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go"
	"github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/dto"
)

// The Python demo (@app.errorhandler(APIError)) and the JS demo (its express
// error middleware) both surface the backend's real status code. These tests pin
// the Go demo to the same contract: an SDK error that carries a status must not
// be flattened into 500.
func TestWriteSDKError_MapsAPIErrorsToTheirRealStatus(t *testing.T) {
	cases := []struct {
		name string
		err  error
		want int
	}{
		{"not found", &cortexmem.APIError{StatusCode: 404, Message: "Observation not found"}, http.StatusNotFound},
		{"bad request", &cortexmem.APIError{StatusCode: 400, Message: "project is required"}, http.StatusBadRequest},
		{"rate limited", &cortexmem.APIError{StatusCode: 429, Message: "slow down"}, http.StatusTooManyRequests},
		{"service unavailable", &cortexmem.APIError{StatusCode: 503, Message: "down"}, http.StatusServiceUnavailable},
		{"teapot still passes through", &cortexmem.APIError{StatusCode: 418, Message: "teapot"}, 418},
		{"server error passes through", &cortexmem.APIError{StatusCode: 500, Message: "boom"}, http.StatusInternalServerError},
		{"wrapped API error still unwraps", fmt.Errorf("calling sdk: %w", &cortexmem.APIError{StatusCode: 404, Message: "nope"}), http.StatusNotFound},
		{"client-side validation", &cortexmem.ValidationError{Field: "projectPath", Message: "projectPath is required"}, http.StatusBadRequest},
		{"observation update validation", emptyUpdateErr(t), http.StatusBadRequest},
		{"plain error stays 500", errors.New("connection refused"), http.StatusInternalServerError},
		{"nil stays 500", nil, http.StatusInternalServerError},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			rec := httptest.NewRecorder()
			writeSDKError(rec, tc.err, "failed to do the thing")

			if rec.Code != tc.want {
				t.Errorf("status = %d, want %d (err=%v)", rec.Code, tc.want, tc.err)
			}
			if ct := rec.Header().Get("Content-Type"); ct != "application/json" {
				t.Errorf("Content-Type = %q, want application/json", ct)
			}
			want := fmt.Sprintf("failed to do the thing: %v", tc.err)
			if got := decodeErrorBody(t, rec); got != want {
				t.Errorf("body = %q, want %q", got, want)
			}
		})
	}
}

// A status outside [400,600) cannot be echoed back — it would be an invalid HTTP
// status — so it must not be reflected to the client.
func TestWriteSDKError_RejectsOutOfRangeStatus(t *testing.T) {
	rec := httptest.NewRecorder()
	writeSDKError(rec, &cortexmem.APIError{StatusCode: 302, Message: "weird"}, "ctx")
	if rec.Code != http.StatusInternalServerError {
		t.Errorf("status = %d, want 500 for an out-of-range API status", rec.Code)
	}
}

func decodeErrorBody(t *testing.T, rec *httptest.ResponseRecorder) string {
	t.Helper()
	var body struct {
		Error string `json:"error"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("decoding error body %q: %v", rec.Body.String(), err)
	}
	return body.Error
}

// emptyUpdateErr returns the error an empty ObservationUpdate validates to.
func emptyUpdateErr(t *testing.T) error {
	t.Helper()
	err := dto.ObservationUpdate{}.Validate()
	if err == nil {
		t.Fatal("expected an empty ObservationUpdate to fail validation")
	}
	return err
}

// TestAtoiParam pins the integer-query-parameter rule the four demos share.
//
// The rule was measured, not chosen. Against a live backend, ?limit= 5 and
// ?limit=+5 both return 5, ?limit=0x10 returns 16 observations (Spring converts
// through Integer.decode semantics), and ?limit= returns the backend default.
// strconv.Atoi trims nothing, so this demo was the only one of the four that
// rejected " 5" while the backend and the other demos accepted it.
func TestAtoiParam(t *testing.T) {
	tests := []struct {
		name    string
		in      string
		want    int
		wantErr bool
	}{
		{"plain", "5", 5, false},
		{"leading plus", "+5", 5, false},
		{"leading minus", "-5", -5, false},
		{"leading space", " 5", 5, false},
		{"trailing space", "5 ", 5, false},
		{"surrounded by whitespace", "\t 5 \n", 5, false},
		{"zero", "0", 0, false},
		{"hex is not an integer here", "0x10", 0, true},
		{"trailing garbage", "10abc", 0, true},
		{"fraction", "1.5", 0, true},
		{"exponent", "1e3", 0, true},
		{"empty", "", 0, true},
		{"lone sign", "+", 0, true},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			got, err := atoiParam(tc.in)
			if tc.wantErr {
				if err == nil {
					t.Fatalf("atoiParam(%q) = %d, want error", tc.in, got)
				}
				return
			}
			if err != nil {
				t.Fatalf("atoiParam(%q) unexpected error: %v", tc.in, err)
			}
			if got != tc.want {
				t.Fatalf("atoiParam(%q) = %d, want %d", tc.in, got, tc.want)
			}
		})
	}
}
