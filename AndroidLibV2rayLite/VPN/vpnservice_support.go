package VPN

import (
	"context"
	"errors"
	"fmt"
	"log"
	"net"
	"os"
	"sync"
	"syscall"
	"time"

	v2net "github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/features/dns"
	"github.com/xtls/xray-core/features/outbound"
	v2internet "github.com/xtls/xray-core/transport/internet"
	"golang.org/x/sys/unix"
)

type protectSet interface {
	Protect(int) int
}

type resolved struct {
	domain       string
	IPs          []net.IP
	Port         int
	ipIdx        uint8
	ipLock       sync.Mutex
	lastSwitched time.Time
}

// NextIP switch to another resolved result.
// there still be race-condition here if multiple err concurently occured
// may cause idx keep switching,
// but that's an outside error can hardly handled here
func (r *resolved) NextIP() {
	r.ipLock.Lock()
	defer r.ipLock.Unlock()

	if len(r.IPs) > 1 {

		// throttle, don't switch too quickly
		now := time.Now()
		if now.Sub(r.lastSwitched) < time.Second*5 {
			log.Println("switch too quickly")
			return
		}
		r.lastSwitched = now
		r.ipIdx++

	} else {
		return
	}

	if r.ipIdx >= uint8(len(r.IPs)) {
		r.ipIdx = 0
	}

	cur := r.currentIP()
	log.Printf("switched to next IP: %s", cur)
}

func (r *resolved) currentIP() net.IP {
	if len(r.IPs) > 0 {
		return r.IPs[r.ipIdx]
	}

	return nil
}

// NewPreotectedDialer ...
func NewPreotectedDialer(p protectSet) *ProtectedDialer {
	d := &ProtectedDialer{
		// prefer native lookup on Android
		resolver:   &net.Resolver{PreferGo: false},
		protectSet: p,
	}
	return d
}

// ProtectedDialer ...
type ProtectedDialer struct {
	currentServer string
	resolveChan   chan struct{}

	vServer  *resolved
	resolver *net.Resolver

	protectSet
}

// DestIpAddress leaves transport address resolution profile-specific.
func (d *ProtectedDialer) DestIpAddress() net.IP {
	// One process may test several unrelated outbounds. Do not substitute the
	// active VPN server's cached IP into another profile's transport.
	return nil
}

func (d *ProtectedDialer) IsVServerReady() bool {
	return (d.vServer != nil)
}

func (d *ProtectedDialer) PrepareResolveChan() {
	d.resolveChan = make(chan struct{})
}

func (d *ProtectedDialer) ResolveChan() <-chan struct{} {
	return d.resolveChan
}

// simplicated version of golang: internetAddrList in src/net/ipsock.go
func (d *ProtectedDialer) lookupAddr(addr string) (*resolved, error) {

	var (
		err        error
		host, port string
		portnum    int
	)

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if host, port, err = net.SplitHostPort(addr); err != nil {
		log.Printf("PrepareDomain SplitHostPort Err: %v", err)
		return nil, err
	}

	if portnum, err = d.resolver.LookupPort(ctx, "tcp", port); err != nil {
		log.Printf("PrepareDomain LookupPort Err: %v", err)
		return nil, err
	}

	addrs, err := d.resolver.LookupIPAddr(ctx, host)
	if err != nil {
		return nil, err
	}
	if len(addrs) == 0 {
		return nil, fmt.Errorf("domain %s Failed to resolve", addr)
	}

	IPs := make([]net.IP, len(addrs))
	for i, ia := range addrs {
		IPs[i] = ia.IP
	}

	rs := &resolved{
		domain: host,
		IPs:    IPs,
		Port:   portnum,
	}

	return rs, nil
}

// PrepareDomain caches direct v2ray server host
func (d *ProtectedDialer) PrepareDomain(domainName string, closeCh <-chan struct{}) {
	log.Printf("Preparing Domain: %s", domainName)
	d.currentServer = domainName

	defer close(d.resolveChan)
	maxRetry := 10
	for {
		if maxRetry == 0 {
			log.Println("PrepareDomain maxRetry reached. exiting.")
			return
		}

		resolved, err := d.lookupAddr(domainName)
		if err != nil {
			maxRetry--
			log.Printf("PrepareDomain err: %v\n", err)
			select {
			case <-closeCh:
				log.Printf("PrepareDomain exit due to v2ray closed")
				return
			case <-time.After(time.Second * 2):
			}
			continue
		}

		d.vServer = resolved
		log.Printf("Prepare Result:\n Domain: %s\n Port: %d\n IPs: %v\n",
			resolved.domain, resolved.Port, resolved.IPs)
		return
	}
}

func (d *ProtectedDialer) getFd(network v2net.Network, ip net.IP) (fd int, err error) {
	domain := unix.AF_INET6
	if ip.To4() != nil {
		domain = unix.AF_INET
	}
	switch network {
	case v2net.Network_TCP:
		fd, err = unix.Socket(domain, unix.SOCK_STREAM, unix.IPPROTO_TCP)
	case v2net.Network_UDP:
		fd, err = unix.Socket(domain, unix.SOCK_DGRAM, unix.IPPROTO_UDP)
	default:
		err = fmt.Errorf("unknow network")
	}
	return
}

// Init implement internet.SystemDialer
func (d *ProtectedDialer) Init(_ dns.Client, _ outbound.Manager) {
	// do nothing
}

// Dial exported as the protected dial method
func (d *ProtectedDialer) Dial(ctx context.Context,
	src v2net.Address, dest v2net.Destination, sockopt *v2internet.SocketConfig) (net.Conn, error) {

	address := dest.NetAddr()
	if address == d.currentServer && d.resolveChan != nil {
		select {
		case <-ctx.Done():
			return nil, ctx.Err()
		case <-d.resolveChan:
		}
		if d.vServer != nil {
			address = net.JoinHostPort(d.vServer.currentIP().String(), fmt.Sprint(d.vServer.Port))
		}
	}
	dialer := net.Dialer{Timeout: 10 * time.Second, Resolver: d.resolver,
		Control: func(_, _ string, socket syscall.RawConn) error {
			var protectErr error
			err := socket.Control(func(fd uintptr) {
				if d.Protect(int(fd)) != 0 {
					protectErr = errors.New("VPN socket protection failed")
				}
			})
			if err != nil {
				return err
			}
			return protectErr
		},
	}
	return dialer.DialContext(ctx, dest.Network.SystemString(), address)
}

func (d *ProtectedDialer) fdConn(ctx context.Context, ip net.IP, port int, fd int) (net.Conn, error) {

	defer unix.Close(fd)

	// call android VPN service to "protect" the fd connecting straight out
	d.Protect(fd)

	sa, err := sockaddrForIP(ip, port)
	if err != nil {
		return nil, err
	}

	if err := unix.Connect(fd, sa); err != nil {
		log.Printf("fdConn unix.Connect err, Close Fd: %d Err: %v", fd, err)
		return nil, err
	}

	file := os.NewFile(uintptr(fd), "Socket")
	if file == nil {
		// returned value will be nil if fd is not a valid file descriptor
		return nil, errors.New("fdConn fd invalid")
	}

	defer file.Close()
	//Closing conn does not affect file, and closing file does not affect conn.
	conn, err := net.FileConn(file)
	if err != nil {
		log.Printf("fdConn FileConn Close Fd: %d Err: %v", fd, err)
		return nil, err
	}

	return conn, nil
}

func sockaddrForIP(ip net.IP, port int) (unix.Sockaddr, error) {
	if ip4 := ip.To4(); ip4 != nil {
		sa := &unix.SockaddrInet4{Port: port}
		copy(sa.Addr[:], ip4)
		return sa, nil
	}
	if ip6 := ip.To16(); ip6 != nil {
		sa := &unix.SockaddrInet6{Port: port}
		copy(sa.Addr[:], ip6)
		return sa, nil
	}
	return nil, fmt.Errorf("invalid IP address")
}
