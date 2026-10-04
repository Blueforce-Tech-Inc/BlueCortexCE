package cortexmem

// This file is the internal test package on purpose: the configuration the tests
// below assert on is normalised inside NewClient and stored on the unexported
// httpClient, so there is no public surface that can observe it. The external
// tests in client_test.go therefore cannot cover it at all -- which is how the
// round-255 defect survived: nothing asserted on the normalisation path.

import (
	"testing"
	"time"
)

// TestClientTimeoutIsFlooredNotReset pins the behaviour fixed in round 255:
// a caller asking for a short timeout was silently given the 30s default, so
// WithTimeout(50ms) produced a timeout 600x longer than requested. The floor
// must be 100ms -- the same trigger constant the RetryBackoff line in the same
// block already used, and the same minimum the Python SDK documents.
func TestClientTimeoutIsFlooredNotReset(t *testing.T) {
	const floor = 100 * time.Millisecond

	tests := []struct {
		requested time.Duration
		want      time.Duration
	}{
		{0, floor},
		{10 * time.Millisecond, floor},
		{50 * time.Millisecond, floor}, // the case that used to yield 30s
		{99 * time.Millisecond, floor},
		{100 * time.Millisecond, floor},
		{250 * time.Millisecond, 250 * time.Millisecond},
		{5 * time.Second, 5 * time.Second},
	}

	for _, tt := range tests {
		c := NewClient(WithTimeout(tt.requested), WithConnectTimeout(tt.requested))
		if got := c.(*httpClient).config.Timeout; got != tt.want {
			t.Errorf("WithTimeout(%v) -> %v, want %v", tt.requested, got, tt.want)
		}
		if got := c.(*httpClient).config.ConnectTimeout; got != tt.want {
			t.Errorf("WithConnectTimeout(%v) -> %v, want %v", tt.requested, got, tt.want)
		}
	}
}

// TestClientRetryBackoffFloor is the control: RetryBackoff already floored at
// 100ms before the fix and must keep doing so.
func TestClientRetryBackoffFloor(t *testing.T) {
	for _, requested := range []time.Duration{0, 10 * time.Millisecond, 99 * time.Millisecond} {
		c := NewClient(WithRetryBackoff(requested))
		if got := c.(*httpClient).config.RetryBackoff; got != 100*time.Millisecond {
			t.Errorf("WithRetryBackoff(%v) -> %v, want 100ms", requested, got)
		}
	}
	if got := NewClient(WithRetryBackoff(2 * time.Second)).(*httpClient).config.RetryBackoff; got != 2*time.Second {
		t.Errorf("WithRetryBackoff(2s) -> %v, want 2s", got)
	}
}

// TestClientTimeoutDefaultsUnchanged is the second control: with no timeout
// option at all, the defaults must still be 30s / 10s / 500ms.
func TestClientTimeoutDefaultsUnchanged(t *testing.T) {
	cfg := NewClient().(*httpClient).config
	if cfg.Timeout != 30*time.Second {
		t.Errorf("default Timeout = %v, want 30s", cfg.Timeout)
	}
	if cfg.ConnectTimeout != 10*time.Second {
		t.Errorf("default ConnectTimeout = %v, want 10s", cfg.ConnectTimeout)
	}
	if cfg.RetryBackoff != 500*time.Millisecond {
		t.Errorf("default RetryBackoff = %v, want 500ms", cfg.RetryBackoff)
	}
}
