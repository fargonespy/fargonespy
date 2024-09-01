package main

import (
	"bufio"
	"flag"
	"fmt"
	"log"
	"net"
	"os"
	"runtime"
	"strings"

	"github.com/miekg/dns"
	"github.com/qdm12/dns/v2/pkg/nameserver"
	"golang.org/x/sync/errgroup"
)

var (
	interceptDomains = flag.String("intercept_domains", "gamespy.com,gamespy.net", "Comma separated list of domains to intercept. Lookups for intercepted domains return the address determined by --answer_ip")
	forwardDomains   = flag.String("forward_domains", ".", "Comma separated list of domains for which requests will be forwarded to the upstream DNS server.")
	forwarder        = flag.String("forwarder", "", "The IP and port to forward non-intercepted requests to. Detected automatically from system settings if not specified.")
	logAllRequests   = flag.Bool("log_all_requests", false, "Log all DNS requests, not just for GameSpy addresses.")
	answerIP         = flag.String("answer_ip", "", "IP to answer with for intercepted requests. Automatically determined if not specified.")
)

// staticInterceptor responds with a fixed IP address.
// Assumes that only A queries will be received.
type staticInterceptor struct {
	answerIP net.IP
}

func (si *staticInterceptor) ServeDNS(w dns.ResponseWriter, req *dns.Msg) {
	m := &dns.Msg{}
	m.SetReply(req)
	m.Authoritative = true
	m.Rcode = dns.RcodeSuccess
	aRec := &dns.A{
		Hdr: dns.RR_Header{
			Name:   req.Question[0].Name,
			Rrtype: dns.TypeA,
			Class:  dns.ClassINET,
			Ttl:    60,
		},
		A: si.answerIP,
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

// forwardingInterceptor forwards all requests to another DNS server.
type forwardingHandler struct {
	forwardServer string
}

func (fh *forwardingHandler) ServeDNS(w dns.ResponseWriter, r *dns.Msg) {
	if *logAllRequests {
		for _, q := range r.Question {
			log.Printf("forwarding request: %s %s\n", dns.Type(q.Qtype), q.Name)
		}
	}
	c := &dns.Client{}
	reply, _, err := c.Exchange(r, fh.forwardServer)
	if err != nil {
		log.Printf("could not query server: %s\n", err)
		_ = w.Close()
		return
	}
	if err := w.WriteMsg(reply); err != nil {
		log.Printf("could not send reply: %s\n", err)
	}
}

type refusingHandler struct {
}

func (fh *refusingHandler) ServeDNS(w dns.ResponseWriter, r *dns.Msg) {
	if *logAllRequests {
		for _, q := range r.Question {
			log.Printf("refusing request: %s %s\n", dns.Type(q.Qtype), q.Name)
		}
	}
	m := &dns.Msg{}
	m.SetReply(r)
	m.Rcode = dns.RcodeRefused
	if err := w.WriteMsg(m); err != nil {
		log.Printf("could not send reply: %s\n", err)
		return
	}
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
		if ip.IsLoopback() || ip.To4() == nil || ip.IsLinkLocalUnicast() || !ip.IsPrivate() {
			continue
		}
		listenAddrs = append(listenAddrs, ip)
	}

	if listenAddrs == nil {
		fatalf("could not determine addresses to listen on")
	}

	log.Printf("listening on %s\n", listenAddrs)

	mux := dns.NewServeMux()
	mux.Handle(".", &refusingHandler{})

	for _, d := range strings.Split(*forwardDomains, ",") {
		log.Printf("Will forward requests for %q.\n", d)
		mux.Handle(d, &forwardingHandler{forwardServer: forwardServer})
	}

	var answer net.IP
	if *answerIP != "" {
		answer = net.ParseIP(*answerIP)
		if answer == nil {
			fatalf("--answer_ip %s is not valid", *answerIP)
		}
	} else {
		answer = listenAddrs[0]
	}

	log.Printf("Intercepted requests will be sent to %q.", answer)

	for _, d := range strings.Split(*interceptDomains, ",") {
		log.Printf("Will intercept requests for %q.\n", d)
		mux.Handle(d, &staticInterceptor{answerIP: answer})
	}

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
