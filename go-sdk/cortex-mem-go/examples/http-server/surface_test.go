package main

import (
	"os"
	"regexp"
	"sort"
	"strings"
	"testing"
)

// Pins the demo's endpoint surface — every (verb, path) it serves — and binds
// it to the two other places that also list it: the README's endpoint table
// and the route list the server prints at startup.
//
// Why this exists: the Java demo pins its 44 endpoints (DemoEndpointSurfaceTest)
// and the Python demo pins its 26 (TestDemoSurface, which also keeps that
// demo's README honest). The Go demo pinned neither, and it carries **three**
// copies of the same fact with nothing asserting that they agree:
//
//   1. the 24 handlers registered in main.go (26 verb/path pairs, because
//      /observations/{id} answers GET, PATCH and DELETE from one handler);
//   2. the 26-row table in README.md;
//   3. the 26-line route list main.go prints on startup.
//
// A renamed path, a flipped verb or a dropped handler would have left all
// three quietly inconsistent, and the README is the first thing a reader
// copies from.
//
// The set is compared for **equality**, not containment: an added or a removed
// endpoint fails this test, which is the point.
//
// The routes are read off the source rather than from a running server, so the
// test needs no backend and no port. What the server actually serves was
// checked separately against a live instance.

var (
	// mux.HandleFunc("/path", func(...) {   — the registration itself.
	reRegister = regexp.MustCompile(`mux\.HandleFunc\("([^"]+)"`)

	// checkMethod(w, r, http.MethodGet)  /  case http.MethodGet:
	// how each handler declares the verb it answers.
	reCheckMethod = regexp.MustCompile(`checkMethod\(w, r, http\.(Method\w+)\)`)
	reSwitchCase  = regexp.MustCompile(`case http\.(Method\w+):`)

	// A line of the startup route list: fmt.Println("  GET    /health   - ...").
	rePrinted = regexp.MustCompile(`fmt\.Println\("  (GET|POST|PUT|PATCH|DELETE)\s+(\S+)\s+-\s`)

	// A row of the README table: | GET | `/health` | ... |
	reReadmeRow = regexp.MustCompile(`(?m)^\|\s*(GET|POST|PUT|PATCH|DELETE)\s*\|\s*` + "`([^`]+)`" + `\s*\|`)

	// The README headline: "exposing 26 REST endpoints" — the phrase may wrap
	// across a line break, so the whitespace between the words is \s+.
	reReadmeCount = regexp.MustCompile(`exposing (\d+) REST\s+endpoints`)
)

// normalisePath makes the two spellings of a path parameter one: the source
// and the README both use {id} here, but the JS demo uses :id, so a future
// edit that switches either side must not read as a surface change.
func normalisePath(p string) string {
	p = regexp.MustCompile(`:[A-Za-z0-9_]+`).ReplaceAllString(p, "{id}")
	p = regexp.MustCompile(`\{[^}]*\}`).ReplaceAllString(p, "{id}")
	return p
}

func verbName(m string) string {
	// http.MethodGet -> "GET": the README table and the startup route list
	// both spell the verbs in upper case, and the comparison is on the verb.
	return strings.ToUpper(strings.TrimPrefix(m, "Method"))
}

// registeredRoutes returns the (verb, path) pairs the demo actually serves,
// read off main.go.
func registeredRoutes(t *testing.T) map[string]bool {
	t.Helper()
	src, err := os.ReadFile("main.go")
	if err != nil {
		t.Fatalf("cannot read main.go: %v", err)
	}
	lines := strings.Split(string(src), "\n")

	out := map[string]bool{}
	for i := 0; i < len(lines); i++ {
		m := reRegister.FindStringSubmatch(lines[i])
		if m == nil {
			continue
		}
		path := m[1]
		if path == "/" {
			continue // the JSON 404 fallback, not part of the demo's API
		}
		// Brace-match the handler body. The closure's opening brace sits on
		// the HandleFunc line itself, so the depth starts at 1 there.
		depth := strings.Count(lines[i], "{") - strings.Count(lines[i], "}")
		body := []string{}
		j := i + 1
		for ; j < len(lines) && depth > 0; j++ {
			body = append(body, lines[j])
			depth += strings.Count(lines[j], "{") - strings.Count(lines[j], "}")
		}
		blob := strings.Join(body, "\n")

		verbs := map[string]bool{}
		for _, v := range reCheckMethod.FindAllStringSubmatch(blob, -1) {
			verbs[verbName(v[1])] = true
		}
		for _, v := range reSwitchCase.FindAllStringSubmatch(blob, -1) {
			verbs[verbName(v[1])] = true
		}
		if len(verbs) == 0 {
			t.Errorf("handler %q declares no verb: neither checkMethod nor a switch on r.Method", path)
			continue
		}
		for v := range verbs {
			out[v+" "+normalisePath(path)] = true
		}
		i = j
	}
	if len(out) == 0 {
		t.Fatal("no routes were read off main.go — the parser is broken, not the demo")
	}
	return out
}

// readmeRoutes returns the (verb, path) pairs the README's endpoint table
// publishes.
func readmeRoutes(t *testing.T) map[string]bool {
	t.Helper()
	raw, err := os.ReadFile("README.md")
	if err != nil {
		t.Fatalf("cannot read README.md: %v", err)
	}
	out := map[string]bool{}
	for _, m := range reReadmeRow.FindAllStringSubmatch(string(raw), -1) {
		out[m[1]+" "+normalisePath(m[2])] = true
	}
	if len(out) == 0 {
		t.Fatal("no rows were read from the README endpoint table — the parser is broken, not the README")
	}
	return out
}

// printedRoutes returns the (verb, path) pairs the server prints at startup.
func printedRoutes(t *testing.T) map[string]bool {
	t.Helper()
	src, err := os.ReadFile("main.go")
	if err != nil {
		t.Fatalf("cannot read main.go: %v", err)
	}
	out := map[string]bool{}
	for _, m := range rePrinted.FindAllStringSubmatch(string(src), -1) {
		out[m[1]+" "+normalisePath(m[2])] = true
	}
	if len(out) == 0 {
		t.Fatal("no route lines were read from the startup print block — the parser is broken, not the demo")
	}
	return out
}

func sortedKeys(m map[string]bool) []string {
	out := make([]string, 0, len(m))
	for k := range m {
		out = append(out, k)
	}
	sort.Strings(out)
	return out
}

func difference(a, b map[string]bool) []string {
	out := []string{}
	for k := range a {
		if !b[k] {
			out = append(out, k)
		}
	}
	sort.Strings(out)
	return out
}

func TestDemoEndpointSurface_MatchesTheReadmeTable(t *testing.T) {
	registered := registeredRoutes(t)
	documented := readmeRoutes(t)

	if missing := difference(registered, documented); len(missing) > 0 {
		t.Errorf("endpoints served but missing from the README table: %v", missing)
	}
	if extra := difference(documented, registered); len(extra) > 0 {
		t.Errorf("README rows with no endpoint behind them: %v", extra)
	}
}

// The three sources above can all drift together — a rename applied to the
// handlers, the README and the startup list at once would leave them
// agreeing with each other and disagreeing with the world. This set is the
// fourth opinion, written down rather than derived, so that adding or
// removing an endpoint is a deliberate act. Same shape as the Java demo's
// DemoEndpointSurfaceTest.
func TestDemoEndpointSurface_MatchesTheDeclaredSet(t *testing.T) {
	registered := registeredRoutes(t)

	declared := []string{
		"GET /health",
		"POST /chat",
		"GET /search",
		"GET /version",
		"GET /experiences",
		"GET /iclprompt",
		"GET /observations",
		"GET /observations/{id}",
		"PATCH /observations/{id}",
		"DELETE /observations/{id}",
		"POST /batch-observations",
		"GET /projects",
		"GET /stats",
		"GET /modes",
		"GET /settings",
		"GET /quality",
		"GET /extraction/latest",
		"GET /extraction/history",
		"POST /extraction/run",
		"POST /refine",
		"POST /feedback",
		"POST /session/start",
		"PATCH /session/user",
		"POST /create-observation",
		"POST /ingest/prompt",
		"POST /ingest/session-end",
	}
	want := map[string]bool{}
	for _, d := range declared {
		want[d] = true
	}

	if missing := difference(registered, want); len(missing) > 0 {
		t.Errorf("endpoints served but not in the declared set: %v", missing)
	}
	if extra := difference(want, registered); len(extra) > 0 {
		t.Errorf("declared endpoints the demo does not serve: %v", extra)
	}
}

func TestDemoEndpointSurface_MatchesTheStartupRouteList(t *testing.T) {
	registered := registeredRoutes(t)
	printed := printedRoutes(t)

	if missing := difference(registered, printed); len(missing) > 0 {
		t.Errorf("endpoints served but missing from the startup route list: %v", missing)
	}
	if extra := difference(printed, registered); len(extra) > 0 {
		t.Errorf("startup route list entries with no endpoint behind them: %v", extra)
	}
}

func TestDemoEndpointSurface_MatchesTheReadmeHeadlineCount(t *testing.T) {
	registered := registeredRoutes(t)

	raw, err := os.ReadFile("README.md")
	if err != nil {
		t.Fatalf("cannot read README.md: %v", err)
	}
	m := reReadmeCount.FindStringSubmatch(string(raw))
	if m == nil {
		t.Fatal("the README no longer states an endpoint count")
	}
	want := m[1]
	if want != itoa(len(registered)) {
		t.Errorf("the README says %s endpoints, the demo serves %d (%v)",
			want, len(registered), sortedKeys(registered))
	}
}

// itoa avoids pulling strconv in for one call.
func itoa(n int) string {
	if n == 0 {
		return "0"
	}
	digits := []byte{}
	for n > 0 {
		digits = append([]byte{byte('0' + n%10)}, digits...)
		n /= 10
	}
	return string(digits)
}
