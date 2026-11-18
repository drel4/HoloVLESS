package libv2ray

import (
	"encoding/json"
	"fmt"
	confserial "github.com/xtls/xray-core/infra/conf/serial"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
	"time"
)

func TestPrivateJSONConfigFixture(t *testing.T) {
	path := os.Getenv("HOLOVLESS_JSON_FIXTURE")
	if path == "" {
		t.Skip("no private fixture supplied")
	}
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatal("cannot read private fixture")
	}
	var configs []json.RawMessage
	if json.Unmarshal(data, &configs) != nil || len(configs) == 0 {
		t.Fatal("invalid private fixture")
	}
	for index, config := range configs {
		if _, err := confserial.LoadJSONConfig(strings.NewReader(string(config))); err != nil {
			t.Errorf("profile %d rejected by core parser", index)
		}
	}
	t.Logf("validated %d full JSON configurations", len(configs))
}

func TestDelayUsesCoreWithoutBindingProfileListeners(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(204) }))
	defer server.Close()
	busy, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer busy.Close()
	port := busy.Addr().(*net.TCPAddr).Port
	config := fmt.Sprintf(`{"inbounds":[{"port":%d,"listen":"127.0.0.1","protocol":"socks"}],"outbounds":[{"protocol":"freedom","tag":"proxy"}]}`, port)
	tester := NewDelayTester()
	defer tester.Cancel()
	if delay, err := tester.Measure(config, server.URL); err != nil || delay < 0 {
		t.Fatalf("independent test failed: %v", err)
	}
}

func TestDelayCannotPassViaDirectFallback(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(204) }))
	defer server.Close()
	tester := NewDelayTester()
	defer tester.Cancel()
	config := `{"outbounds":[{"protocol":"blackhole","tag":"proxy"},{"protocol":"freedom","tag":"direct"}]}`
	if _, err := tester.Measure(config, server.URL); err == nil {
		t.Fatal("blackhole profile unexpectedly passed")
	}
}

func TestDelayCancellationClosesActiveRequest(t *testing.T) {
	entered := make(chan struct{}, 1)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		select {
		case entered <- struct{}{}:
		default:
		}
		<-r.Context().Done()
	}))
	defer server.Close()
	tester := NewDelayTester()
	defer tester.Cancel()
	done := make(chan error, 1)
	go func() { _, err := tester.Measure(`{"outbounds":[{"protocol":"freedom"}]}`, server.URL); done <- err }()
	select {
	case <-entered:
	case <-time.After(3 * time.Second):
		t.Fatal("request did not start")
	}
	tester.Cancel()
	select {
	case err := <-done:
		if err == nil {
			t.Fatal("cancelled test passed")
		}
	case <-time.After(2 * time.Second):
		t.Fatal("cancellation did not interrupt test")
	}
}
