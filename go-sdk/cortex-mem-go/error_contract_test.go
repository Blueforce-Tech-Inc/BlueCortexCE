package cortexmem_test

import (
	"context"
	"errors"
	"os"
	"strings"
	"testing"

	cortexmem "github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go"
	"github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go/dto"
)

// Why this file exists
// -------------------
//
// The Go SDK has **two** validation error types:
//
//	cortexmem.ValidationError                 (error.go)  — required arguments
//	dto.ObservationUpdateValidationError     (dto/)      — ObservationUpdate only
//
// They are different Go types, so `errors.As` never crosses between them and
// `cortexmem.IsValidationError` returns **false** for the dto type. The
// existing tests never pinned that distinction: `client_test.go` asserted
// `strings.Contains(err.Error(), "at least one field")` (a substring, so any
// prefix change still passed) and `dto/dto_test.go` asserted the type but not
// the message. A caller who writes `if cortexmem.IsValidationError(err)` to
// decide "the request was malformed, do not retry" therefore gets `false` for
// the empty-update case — a real trap, and the Go README publishes the full
// message as a code block, so the message is a contract too.
//
// This file pins that contract exactly: the type, the exact message, the
// helper predicates, and the fact that no request is sent. It also pins the
// README's published message to the runtime message, so the two cannot drift
// apart again.
//
// The README checks are literal: they read README.md and README-zh-CN.md from
// the package directory and compare what the document publishes against what
// the code produces.

const (
	// emptyUpdateBody is the message body. All four SDKs use the same words,
	// but each wraps them in its own prefix (or none).
	emptyUpdateBody = "at least one field must be provided for update"

	// emptyUpdatePrefix is Go's own prefix, from
	// dto.ObservationUpdateValidationError.Error().
	emptyUpdatePrefix = "cortex-ce: ObservationUpdate validation error: "

	// jsPythonUpdatePrefix is the prefix the JS and Python SDKs use for the
	// same message body (their ValidationError takes a field name).
	jsPythonUpdatePrefix = "cortex-ce: validation error on update: "

	// emptyUpdateMessage is the exact string the Go SDK produces at runtime.
	emptyUpdateMessage = emptyUpdatePrefix + emptyUpdateBody

	// observationIDMessage is the exact string produced for a blank observationID.
	observationIDMessage = "cortex-ce: validation error on observationID: observationID is required"

	// conflictMessage is the exact string produced when Content and Narrative
	// are both set. The em dash and the single quotes are part of the message.
	conflictMessage = "cortex-ce: ObservationUpdate validation error: both Content and Narrative are set — they are aliases for the same backend field; the backend silently uses 'content' and ignores 'narrative'. Use only Content or only Narrative."
)

// TestUpdateObservation_EmptyUpdate_ExactErrorContract pins the whole contract
// of an empty update: the exact message, the exact type, the Field value and
// the fact that no request reaches the server.
func TestUpdateObservation_EmptyUpdate_ExactErrorContract(t *testing.T) {
	client := newValidationClient(t)

	err := client.UpdateObservation(context.Background(), "obs-1", dto.ObservationUpdate{})
	if err == nil {
		t.Fatal("UpdateObservation must reject an update that sets no field")
	}

	if got := err.Error(); got != emptyUpdateMessage {
		t.Errorf("empty-update message mismatch\n  want: %q\n  got:  %q", emptyUpdateMessage, got)
	}

	var ve *dto.ObservationUpdateValidationError
	if !errors.As(err, &ve) {
		t.Fatalf("expected *dto.ObservationUpdateValidationError, got %T", err)
	}
	if ve.Field != "update" {
		t.Errorf("expected Field=%q, got %q", "update", ve.Field)
	}
	if !dto.IsObservationUpdateValidationError(err) {
		t.Error("dto.IsObservationUpdateValidationError(err) must be true for an empty update")
	}
}

// TestUpdateObservation_EmptyUpdate_IsNotACortexmemValidationError pins the
// distinction between the SDK's two validation error types.
//
// The empty update is reported as *dto.ObservationUpdateValidationError, NOT
// as *cortexmem.ValidationError. `cortexmem.IsValidationError` uses errors.As
// against the cortexmem type, and a dto error is a different Go type — so the
// helper returns false. A caller who branches on that helper to decide
// "malformed input, do not retry" gets the wrong answer for this case.
//
// The existing test (`TestUpdateObservation_UsesValidationError`) asserts
// `IsValidationError(err) || IsObservationUpdateValidationError(err)` — an
// OR — so it passes no matter which type is returned and cannot tell the two
// apart. This test is the one that can.
func TestUpdateObservation_EmptyUpdate_IsNotACortexmemValidationError(t *testing.T) {
	client := newValidationClient(t)

	err := client.UpdateObservation(context.Background(), "obs-1", dto.ObservationUpdate{})
	if err == nil {
		t.Fatal("UpdateObservation must reject an update that sets no field")
	}

	if cortexmem.IsValidationError(err) {
		t.Errorf("cortexmem.IsValidationError(err) must be false for an empty update: "+
			"the SDK returns *dto.ObservationUpdateValidationError, not *cortexmem.ValidationError (got %T)", err)
	}
	var ce *cortexmem.ValidationError
	if errors.As(err, &ce) {
		t.Errorf("errors.As(err, *cortexmem.ValidationError) must be false, got %T", ce)
	}
}

// TestUpdateObservation_EmptyObservationID_ExactErrorContract pins the other
// side of the pair: a blank observationID is reported as the cortexmem type,
// with the cortexmem prefix, and the dto helper does not match it.
func TestUpdateObservation_EmptyObservationID_ExactErrorContract(t *testing.T) {
	client := newValidationClient(t)

	err := client.UpdateObservation(context.Background(), "   ", dto.ObservationUpdate{Title: strPtr("t")})
	if err == nil {
		t.Fatal("UpdateObservation must reject a blank observationID")
	}

	if got := err.Error(); got != observationIDMessage {
		t.Errorf("blank-observationID message mismatch\n  want: %q\n  got:  %q", observationIDMessage, got)
	}

	var ve *cortexmem.ValidationError
	if !errors.As(err, &ve) {
		t.Fatalf("expected *cortexmem.ValidationError, got %T", err)
	}
	if ve.Field != "observationID" {
		t.Errorf("expected Field=%q, got %q", "observationID", ve.Field)
	}
	if !cortexmem.IsValidationError(err) {
		t.Error("cortexmem.IsValidationError(err) must be true for a blank observationID")
	}
	if dto.IsObservationUpdateValidationError(err) {
		t.Error("dto.IsObservationUpdateValidationError(err) must be false for a blank observationID")
	}
}

// TestUpdateObservation_ObservationIDIsCheckedBeforeTheUpdate pins the order
// of the two checks. Both arguments are invalid in this call, and only one of
// the two errors can be reported. The blank observationID is detected first,
// so the caller learns about the ID rather than about the update — and the
// error type tells them which check fired.
func TestUpdateObservation_ObservationIDIsCheckedBeforeTheUpdate(t *testing.T) {
	client := newValidationClient(t)

	err := client.UpdateObservation(context.Background(), "", dto.ObservationUpdate{})
	if err == nil {
		t.Fatal("UpdateObservation must reject a blank observationID")
	}
	if got := err.Error(); got != observationIDMessage {
		t.Errorf("expected the observationID error to win\n  want: %q\n  got:  %q", observationIDMessage, got)
	}
	if !cortexmem.IsValidationError(err) {
		t.Errorf("expected *cortexmem.ValidationError, got %T", err)
	}
	if dto.IsObservationUpdateValidationError(err) {
		t.Error("the empty-update error must not be reported when the observationID is blank")
	}
}

// TestUpdateObservation_ContentNarrativeConflict_ExactErrorContract pins the
// second update-level error. It is the same dto type as the empty update, but
// a different Field and a different message.
func TestUpdateObservation_ContentNarrativeConflict_ExactErrorContract(t *testing.T) {
	client := newValidationClient(t)

	err := client.UpdateObservation(context.Background(), "obs-1", dto.ObservationUpdate{
		Content:   strPtr("hello"),
		Narrative: strPtr("world"),
	})
	if err == nil {
		t.Fatal("UpdateObservation must reject an update that sets both Content and Narrative")
	}

	if got := err.Error(); got != conflictMessage {
		t.Errorf("conflict message mismatch\n  want: %q\n  got:  %q", conflictMessage, got)
	}

	var ve *dto.ObservationUpdateValidationError
	if !errors.As(err, &ve) {
		t.Fatalf("expected *dto.ObservationUpdateValidationError, got %T", err)
	}
	if ve.Field != "content|narrative" {
		t.Errorf("expected Field=%q, got %q", "content|narrative", ve.Field)
	}
	if !dto.IsObservationUpdateValidationError(err) {
		t.Error("dto.IsObservationUpdateValidationError(err) must be true for a content/narrative conflict")
	}
	if cortexmem.IsValidationError(err) {
		t.Error("cortexmem.IsValidationError(err) must be false for a content/narrative conflict")
	}
}

// TestREADME_PublishedEmptyUpdateMessageMatchesRuntime pins the message the
// Go README publishes to the message the SDK produces.
//
// The comparison is against the **runtime** message, not against the constant
// above: if the SDK's message ever changes, the README must change with it,
// and this test is what says so. A check that only compared the README with
// itself would stay green while the document went stale.
//
// The README shows the empty-update message in a code block, in a table cell
// and in the four-SDK comparison table. A cell that renders the message
// without its prefix is a different string to a reader who copies it.
//
// The rule is per-SDK, because the comparison table legitimately prints all
// four prefixes:
//
//   - Go's prefix, and the JS/Python prefix, are always acceptable;
//   - the bare body is acceptable only on a line that names Java, because
//     Java is the one SDK that prepends nothing.
//
// A garbled prefix (`ValidationError: …`, `cortex-ce: validation error: …`)
// satisfies neither branch and is reported.
func TestREADME_PublishedEmptyUpdateMessageMatchesRuntime(t *testing.T) {
	runtime := runtimeEmptyUpdateMessage(t)
	if runtime != emptyUpdateMessage {
		t.Fatalf("the runtime message is %q but the pinned contract is %q — "+
			"the README below is compared against the runtime value", runtime, emptyUpdateMessage)
	}

	for _, name := range []string{"README.md", "README-zh-CN.md"} {
		t.Run(name, func(t *testing.T) {
			content := readReadme(t, name)

			if !strings.Contains(content, runtime) {
				t.Errorf("%s does not publish the message the SDK returns: %q", name, runtime)
			}

			occurrences := 0
			for _, idx := range substringIndexes(content, emptyUpdateBody) {
				occurrences++
				line := lineAt(content, idx)
				if strings.HasSuffix(content[:idx], emptyUpdatePrefix) {
					continue // Go's own message.
				}
				if strings.HasSuffix(content[:idx], jsPythonUpdatePrefix) {
					continue // The JS and Python message.
				}
				if strings.Contains(line, "Java") {
					continue // Java's message has no prefix at all.
				}
				t.Errorf("%s: the empty-update message on this line is published without a "+
					"documented prefix, so it is not the string any SDK returns:\n  %s",
					name, strings.TrimSpace(line))
			}
			if occurrences == 0 {
				t.Errorf("%s: no occurrence of %q found — the check above passed vacuously",
					name, emptyUpdateBody)
			}
		})
	}
}

// TestREADME_FactsRowPublishesTheFullMessage pins the one row that used to
// render the message without its prefix.
//
// The row reads "`Facts: []string{}` alone" in the English README and
// "只有 `Facts: []string{}`" in the Chinese one, but the `Facts: []string{}`
// marker is common to both, so one check covers the two files.
func TestREADME_FactsRowPublishesTheFullMessage(t *testing.T) {
	runtime := runtimeEmptyUpdateMessage(t)

	for _, name := range []string{"README.md", "README-zh-CN.md"} {
		t.Run(name, func(t *testing.T) {
			content := readReadme(t, name)

			var row string
			for _, line := range strings.Split(content, "\n") {
				if strings.Contains(line, "`Facts: []string{}`") && strings.Contains(line, "|") {
					row = line
					break
				}
			}
			if row == "" {
				t.Fatalf("%s has no row mentioning `Facts: []string{}`", name)
			}
			if !strings.Contains(row, runtime) {
				t.Errorf("%s: the `Facts: []string{}` row must publish the full message %q, got:\n  %s",
					name, runtime, strings.TrimSpace(row))
			}
		})
	}
}

// runtimeEmptyUpdateMessage returns the message the SDK actually produces for
// an update that sets no field.
func runtimeEmptyUpdateMessage(t *testing.T) string {
	t.Helper()
	client := newValidationClient(t)
	err := client.UpdateObservation(context.Background(), "obs-1", dto.ObservationUpdate{})
	if err == nil {
		t.Fatal("UpdateObservation must reject an update that sets no field")
	}
	return err.Error()
}

// TestREADME_NamesThePerSDKErrorTypes pins that the README names the error
// type each of the four SDKs actually returns, instead of claiming they are
// the same.
//
// The three layers are kept apart on purpose: the **type** differs (Go's dto
// type vs the cortexmem type vs Java's IllegalArgumentException), the
// **prefix** differs, and only the **message body** is shared.
func TestREADME_NamesThePerSDKErrorTypes(t *testing.T) {
	for _, name := range []string{"README.md", "README-zh-CN.md"} {
		t.Run(name, func(t *testing.T) {
			content := readReadme(t, name)

			for _, want := range []string{
				"`*dto.ObservationUpdateValidationError`", // Go
				"`ValidationError`",                       // JS and Python
				"`IllegalArgumentException`",              // Java
				"cortex-ce: validation error on update: ", // the JS and Python prefix
			} {
				if !strings.Contains(content, want) {
					t.Errorf("%s must name %s so the reader knows which type to catch", name, want)
				}
			}
		})
	}
}

// TestREADME_DoesNotClaimCrossSDKSameness pins that the two false claims that
// were in both READMEs do not come back.
//
// The first claimed that Java, Python and JS use the same **message** as Go.
// They do not: JS and Python use `cortex-ce: validation error on update: `
// and Java uses no prefix at all. The second claimed that Java, Python and JS
// enforce exactly the same set of required-argument checks. Java enforces one
// more: `projectPath` must be an absolute path in RecordObservation,
// RecordSessionEnd and RecordUserPrompt.
func TestREADME_DoesNotClaimCrossSDKSameness(t *testing.T) {
	for _, name := range []string{"README.md", "README-zh-CN.md"} {
		t.Run(name, func(t *testing.T) {
			content := readReadme(t, name)

			for _, banned := range []string{
				"The same rule and message apply in the Java, Python and JS SDKs",
				"enforce exactly the same set",
				"规则与消息完全相同",
				"强制的是完全相同的一组规则",
			} {
				if strings.Contains(content, banned) {
					t.Errorf("%s still contains the false claim %q", name, banned)
				}
			}
		})
	}
}

// readReadme reads a README from the package directory.
func readReadme(t *testing.T, name string) string {
	t.Helper()
	data, err := os.ReadFile(name)
	if err != nil {
		t.Fatalf("cannot read %s: %v", name, err)
	}
	return string(data)
}

// substringIndexes returns the byte offsets of every non-overlapping
// occurrence of sub in s.
func substringIndexes(s, sub string) []int {
	var idxs []int
	for i := 0; i+len(sub) <= len(s); {
		j := strings.Index(s[i:], sub)
		if j < 0 {
			break
		}
		idxs = append(idxs, i+j)
		i += j + len(sub)
	}
	return idxs
}

// lineAt returns the line of s that contains byte offset idx.
func lineAt(s string, idx int) string {
	start := strings.LastIndex(s[:idx], "\n") + 1
	end := strings.Index(s[idx:], "\n")
	if end < 0 {
		end = len(s)
	} else {
		end += idx
	}
	return s[start:end]
}
