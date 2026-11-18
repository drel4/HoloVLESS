package libv2ray

import (
	"context"
	"fmt"
	"io"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	corenet "github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/common/serial"
	"github.com/xtls/xray-core/core"
	confserial "github.com/xtls/xray-core/infra/conf/serial"
)

// DelayTester owns one cancellable batch of independent, inbound-free cores.
// This follows AndroidLibXrayLite's real-delay approach without replacing the VPN.
type DelayTester struct {
	ctx    context.Context
	cancel context.CancelFunc
}

func NewDelayTester() *DelayTester {
	initV2Env()
	ctx, cancel := context.WithCancel(context.Background())
	return &DelayTester{ctx: ctx, cancel: cancel}
}

func (t *DelayTester) Cancel() { t.cancel() }

func (t *DelayTester) Measure(configJSON, testURL string) (int64, error) {
	ctx, cancel := context.WithTimeout(t.ctx, 12*time.Second)
	defer cancel()
	if err := ctx.Err(); err != nil {
		return -1, err
	}
	config, err := confserial.LoadJSONConfig(strings.NewReader(configJSON))
	if err != nil {
		return -1, fmt.Errorf("invalid test configuration")
	}
	config.Inbound = nil
	var essential []*serial.TypedMessage
	for _, app := range config.App {
		if app.Type == "xray.app.proxyman.OutboundConfig" || app.Type == "xray.app.dispatcher.Config" || app.Type == "xray.app.log.Config" {
			essential = append(essential, app)
		}
	}
	config.App = essential
	inst, err := core.New(config)
	if err != nil {
		return -1, fmt.Errorf("test core creation failed")
	}
	var closeOnce sync.Once
	closeCore := func() { closeOnce.Do(func() { _ = inst.Close() }) }
	defer closeCore()
	if err = inst.Start(); err != nil {
		return -1, fmt.Errorf("test core startup failed")
	}
	go func() { <-ctx.Done(); closeCore() }()
	transport := &http.Transport{
		TLSHandshakeTimeout: 6 * time.Second,
		DialContext: func(ctx context.Context, network, address string) (net.Conn, error) {
			dest, err := corenet.ParseDestination(network + ":" + address)
			if err != nil {
				return nil, err
			}
			return core.Dial(ctx, inst, dest)
		},
	}
	defer transport.CloseIdleConnections()
	client := &http.Client{Transport: transport, Timeout: 12 * time.Second}
	best := int64(-1)
	for attempt := 0; attempt < 2; attempt++ {
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, testURL, nil)
		if err != nil {
			return -1, fmt.Errorf("invalid test URL")
		}
		started := time.Now()
		response, err := client.Do(req)
		if err != nil {
			continue
		}
		_, readErr := io.Copy(io.Discard, io.LimitReader(response.Body, 1024*1024+1))
		response.Body.Close()
		if readErr != nil || (response.StatusCode != http.StatusOK && response.StatusCode != http.StatusNoContent) {
			continue
		}
		elapsed := time.Since(started).Milliseconds()
		if best < 0 || elapsed < best {
			best = elapsed
		}
	}
	if best < 0 {
		return -1, fmt.Errorf("profile connection test failed")
	}
	return best, nil
}
