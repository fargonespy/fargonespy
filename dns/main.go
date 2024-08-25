package main

import (
	"flag"
	"log"
	"net"

	"github.com/miekg/dns"
	"github.com/qdm12/dns/v2/pkg/nameserver"
)

var (
	forwarder = flag.String("forwarder", "", "The IP and port to forward non-gamespy requests to. Detected automatically from system settings if not specified.")
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

func main() {
	flag.Parse()

	servers := nameserver.GetDNSServers()
	if len(servers) == 0 {
		log.Fatalf("no DNS servers found")
	}

	forwardServer := *forwarder

	if forwardServer == "" {
		for _, server := range servers {
			log.Printf("DNS server option: %s\n", server)
		}

		forwardServer = servers[0].String()
	}
	log.Printf("using DNS server %q", forwardServer)

	addr, err := net.InterfaceAddrs()
	if err != nil {
		log.Fatalf("could not retrieve interface addresses: %s\n", err)
		return
	}

	var listenAddr *net.IP
	for _, a := range addr {
		log.Printf("interface addr: %s\n", a)
		ip, _, err := net.ParseCIDR(a.String())
		if err != nil {
			log.Printf("could not parse addr: %s\n", err)
			continue
		}
		if ip.IsLoopback() || ip.To4() == nil {
			continue
		}
		listenAddr = &ip
		break
	}

	if listenAddr == nil {
		log.Fatalf("could not determine address to listen on")
	}

	log.Printf("listening on %s\n", listenAddr)

	mux := dns.NewServeMux()
	mux.HandleFunc(".", func(w dns.ResponseWriter, r *dns.Msg) {
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
	mux.Handle("gamespy.com", &interceptor{listenAddr: *listenAddr})
	mux.Handle("gamespy.net", &interceptor{listenAddr: *listenAddr})

	s := &dns.Server{
		Addr:    listenAddr.String() + ":53",
		Net:     "udp4",
		Handler: mux,
	}
	if err := s.ListenAndServe(); err != nil {
		log.Fatalf("could not start server: %s\n", err)
	}
}
