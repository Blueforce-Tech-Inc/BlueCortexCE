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
