package com.gonespy.service.serverlist;

import java.net.InetAddress;
import java.util.Map;

public class Server {
    private final InetAddress address;
    private final short port;
    private final Map<String, String> attrs;

    public Server(InetAddress address, short port, Map<String, String> attrs) {
        this.address = address;
        this.port = port;
        this.attrs = attrs;
    }

    public Server(InetAddress address, short port, Map<String, String> attrs, String challenge) {
        this.address = address;
        this.port = port;
        this.attrs = attrs;
    }

    public InetAddress getAddress() {
        return address;
    }

    public short getPort() {
        return port;
    }

    public Map<String, String> getAttrs() {
        return attrs;
    }

    public String getAttr(String field) {
        return this.attrs.get(field);
    }
}
