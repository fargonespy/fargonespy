package main

import (
	"bufio"
	"flag"
	"fmt"
	"log"
	"net"
	"os"
	"runtime"

	"github.com/miekg/dns"
	"github.com/qdm12/dns/v2/pkg/nameserver"
	"golang.org/x/sync/errgroup"
)

var (
	forwarder      = flag.String("forwarder", "", "The IP and port to forward non-gamespy requests to. Detected automatically from system settings if not specified.")
	logAllRequests = flag.Bool("log_all_requests", false, "Log all DNS requests, not just for GameSpy addresses.")
)

type interceptor struct {
	listenAddr net.IP
}

func (g *interceptor) ServeDNS(w dns.ResponseWriter, req *dns.Msg) {
	m := &dns.Msg{}
	m.SetReply(req)
	m.Authoritative = true
	aRec := &dns.A{
		Hdr: dns.RR_Header{
			Name:   req.Question[0].Name,
			Rrtype: dns.TypeA,
			Class:  dns.ClassINET,
			Ttl:    60,
		},
		A: g.listenAddr,
	}
	m.Answer = append(m.Answer, aRec)
	if err := w.WriteMsg(m); err != nil {
		log.Printf("could not send reply: %s\n", err)
		return
	}
	log.Printf("handled request for %q", req.Question[0].Name)
}

func fatalf(format string, v ...any) {
	log.Printf(format, v...)
	if runtime.GOOS == "windows" {
		fmt.Println("Press Enter to continue.")
		_, _ = bufio.NewReader(os.Stdin).ReadBytes('\n')
	}
	os.Exit(1)
}

func main() {
	flag.Parse()

	servers := nameserver.GetDNSServers()
	if len(servers) == 0 {
		fatalf("no DNS servers found")
	}

	forwardServer := *forwarder

	if forwardServer == "" {
		for _, server := range servers {
			log.Printf("DNS server option: %s\n", server)
		}

		forwardServer = servers[0].String()
	}
	log.Printf("using upstream DNS server %q", forwardServer)

	addr, err := net.InterfaceAddrs()
	if err != nil {
		log.Fatalf("could not retrieve interface addresses: %s\n", err)
		return
	}

	var listenAddrs []net.IP
	for _, a := range addr {
		log.Printf("interface addr: %s\n", a)
		ip, _, err := net.ParseCIDR(a.String())
		if err != nil {
			log.Printf("could not parse addr: %s\n", err)
			continue
		}
		if ip.IsLoopback() || ip.To4() == nil || ip.IsLinkLocalUnicast() {
			continue
		}
		listenAddrs = append(listenAddrs, ip)
	}

	if listenAddrs == nil {
		fatalf("could not determine addresses to listen on")
	}

	log.Printf("listening on %s\n", listenAddrs)

	mux := dns.NewServeMux()
	mux.HandleFunc(".", func(w dns.ResponseWriter, r *dns.Msg) {
		if *logAllRequests {
			for _, q := range r.Question {
				log.Printf("non-gamespy request: %s\n", q.Name)
			}
		}
		c := &dns.Client{}
		reply, _, err := c.Exchange(r, forwardServer)
		if err != nil {
			log.Printf("could not query server: %s\n", err)
			_ = w.Close()
			return
		}
		if err := w.WriteMsg(reply); err != nil {
			log.Printf("could not send reply: %s\n", err)
		}
	})

	log.Printf("GameSpy requests will be sent to %s", listenAddrs[0])

	mux.Handle("gamespy.com", &interceptor{listenAddr: listenAddrs[0]})
	mux.Handle("gamespy.net", &interceptor{listenAddr: listenAddrs[0]})

	eg := errgroup.Group{}
	for _, addr := range listenAddrs {
		addr := addr
		eg.Go(func() error {
			s := &dns.Server{
				Addr:    addr.String() + ":53",
				Net:     "udp4",
				Handler: mux,
			}
			if err := s.ListenAndServe(); err != nil {
				return fmt.Errorf("could not start server on %s: %s\n", addr, err)
			}
			return nil
		})
	}

	if err := eg.Wait(); err != nil {
		fatalf("could not start DNS server: %s", err)
	}
}
