package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class VpnXrayConfig {

    public static final String SOCKS_HOST = "127.0.0.1";
    public static final int SOCKS_PORT = 10808;

    public static JSONObject buildFullConfig(String link) throws Exception {
        JSONObject outbound = buildOutbound(link);
        JSONObject full = new JSONObject();

        JSONObject log = new JSONObject();
        log.put("loglevel", "warning");
        log.put("error", true);
        full.put("log", log);

        JSONObject dns = new JSONObject();
        JSONArray servers = new JSONArray();
        JSONObject s1 = new JSONObject();
        s1.put("address", "8.8.8.8");
        s1.put("skipFallback", true);
        JSONObject s2 = new JSONObject();
        s2.put("address", "1.1.1.1");
        s2.put("skipFallback", true);
        servers.put(s1);
        servers.put(s2);
        dns.put("servers", servers);
        dns.put("queryStrategy", "UseIPv4");
        full.put("dns", dns);

        JSONArray inbounds = new JSONArray();
        JSONObject inbound = new JSONObject();
        inbound.put("protocol", "socks");
        inbound.put("port", SOCKS_PORT);
        inbound.put("listen", SOCKS_HOST);
        JSONObject sniffing = new JSONObject();
        sniffing.put("enabled", true);
        JSONArray destOverride = new JSONArray();
        destOverride.put("http");
        destOverride.put("tls");
        sniffing.put("destOverride", destOverride);
        inbound.put("sniffing", sniffing);
        JSONObject ibSettings = new JSONObject();
        ibSettings.put("auth", "noauth");
        ibSettings.put("udp", true);
        inbound.put("settings", ibSettings);
        inbounds.put(inbound);
        full.put("inbounds", inbounds);

        JSONArray outbounds = new JSONArray();
        outbounds.put(outbound);
        full.put("outbounds", outbounds);

        JSONObject routing = new JSONObject();
        routing.put("domainStrategy", "AsIs");
        routing.put("rules", new JSONArray());
        full.put("routing", routing);
        return full;
    }

    public static JSONObject buildOutbound(String link) throws Exception {
        if (link.startsWith("vless://")) {
            return vless(link);
        }
        if (link.startsWith("vmess://")) {
            return vmess(link);
        }
        if (link.startsWith("trojan://")) {
            return trojan(link);
        }
        if (link.startsWith("ss://")) {
            return shadowsocks(link);
        }
        if (link.startsWith("socks5://") || link.startsWith("socks://")) {
            return socks(link);
        }
        throw new IllegalArgumentException("unsupported scheme");
    }

    public static String serverName(String link) {
        try {
            URI uri = new URI(link);
            if (uri.getHost() != null) {
                String name = uri.getFragment();
                return name != null && !name.isEmpty() ? name : uri.getHost() + ":" + port(uri);
            }
        } catch (Exception ignore) {
        }
        return link;
    }

    private static Map<String, String> rawQuery(URI uri) {
        Map<String, String> map = new LinkedHashMap<>();
        String q = uri.getRawQuery();
        if (q == null || q.isEmpty()) {
            return map;
        }
        for (String pair : q.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String k = urlDecode(pair.substring(0, eq));
                String v = urlDecode(pair.substring(eq + 1));
                map.put(k, v);
            } else if (!pair.isEmpty()) {
                map.put(urlDecode(pair), "");
            }
        }
        return map;
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static int port(URI uri) {
        int p = uri.getPort();
        return p > 0 ? p : 443;
    }

    private static String b64(String s, boolean urlSafe) {
        try {
            if (urlSafe) {
                return new String(android.util.Base64.decode(s.replace("-", "+").replace("_", "/"), android.util.Base64.DEFAULT), StandardCharsets.UTF_8);
            }
            return new String(android.util.Base64.decode(s, android.util.Base64.DEFAULT), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject vless(String link) throws Exception {
        URI uri = new URI(link);
        Map<String, String> q = rawQuery(uri);
        String host = uri.getHost();
        int port = port(uri);
        String id = uri.getRawUserInfo();

        JSONObject outbound = new JSONObject();
        outbound.put("protocol", "vless");
        JSONObject settings = new JSONObject();
        JSONArray vnext = new JSONArray();
        JSONObject vNode = new JSONObject();
        vNode.put("address", host);
        vNode.put("port", port);
        JSONArray users = new JSONArray();
        JSONObject user = new JSONObject();
        user.put("id", id);
        user.put("encryption", q.containsKey("encryption") ? q.get("encryption") : "none");
        if (q.containsKey("flow") && !q.get("flow").isEmpty()) {
            user.put("flow", q.get("flow"));
        }
        users.put(user);
        vNode.put("users", users);
        vnext.put(vNode);
        settings.put("vnext", vnext);
        outbound.put("settings", settings);
        outbound.put("streamSettings", streamSettings(q));
        outbound.put("mux", muxDisabled());
        return outbound;
    }

    private static JSONObject vmess(String link) throws Exception {
        String body = link.substring("vmess://".length());
        String decoded = b64(body, false);
        if (decoded == null) {
            decoded = body;
        }
        JSONObject o = new JSONObject(decoded);
        JSONObject outbound = new JSONObject();
        outbound.put("protocol", "vmess");
        JSONObject settings = new JSONObject();
        JSONArray vnext = new JSONArray();
        JSONObject vNode = new JSONObject();
        vNode.put("address", o.getString("add"));
        vNode.put("port", o.optInt("port", 443));
        JSONArray users = new JSONArray();
        JSONObject user = new JSONObject();
        user.put("id", o.optString("id"));
        user.put("alterId", o.optInt("aid", 0));
        user.put("security", o.optString("scy", "auto"));
        users.put(user);
        vNode.put("users", users);
        vnext.put(vNode);
        settings.put("vnext", vnext);
        outbound.put("settings", settings);

        String net = o.optString("net", "tcp");
        String path = o.optString("path");
        String hostHeader = o.optString("host");
        String tls = o.optString("tls");
        String sni = o.optString("sni");
        String fp = o.optString("fp");

        JSONObject ss = new JSONObject();
        ss.put("network", net);
        if ("ws".equals(net)) {
            JSONObject ws = new JSONObject();
            if (hostHeader != null && !hostHeader.isEmpty()) {
                ws.put("headers", new JSONObject().put("Host", hostHeader));
            }
            if (path != null && !path.isEmpty()) {
                ws.put("path", path);
            }
            ss.put("wsSettings", ws);
        } else if ("grpc".equals(net)) {
            JSONObject grpc = new JSONObject();
            grpc.put("serviceName", hostHeader != null ? hostHeader : "");
            ss.put("grpcSettings", grpc);
        }
        String security = ("tls".equals(tls) || "xtls".equals(tls)) ? "tls" : (tls != null && !tls.isEmpty() ? tls : "none");
        ss.put("security", security);
        if (!"none".equals(security)) {
            JSONObject tlsSettings = new JSONObject();
            tlsSettings.put("allowInsecure", true);
            if (sni != null && !sni.isEmpty()) {
                tlsSettings.put("serverName", sni);
            } else if (hostHeader != null && !hostHeader.isEmpty()) {
                tlsSettings.put("serverName", hostHeader);
            }
            if (fp != null && !fp.isEmpty()) {
                tlsSettings.put("fingerprint", fp);
            }
            ss.put("tlsSettings", tlsSettings);
        }
        outbound.put("streamSettings", ss);
        outbound.put("mux", muxDisabled());
        return outbound;
    }

    private static JSONObject trojan(String link) throws Exception {
        URI uri = new URI(link);
        Map<String, String> q = rawQuery(uri);
        String host = uri.getHost();
        int port = port(uri);
        String password = uri.getRawUserInfo();

        JSONObject outbound = new JSONObject();
        outbound.put("protocol", "trojan");
        JSONObject settings = new JSONObject();
        JSONArray servers = new JSONArray();
        JSONObject s = new JSONObject();
        s.put("address", host);
        s.put("port", port);
        s.put("password", password);
        servers.put(s);
        settings.put("servers", servers);
        outbound.put("settings", settings);
        outbound.put("streamSettings", streamSettings(q));
        outbound.put("mux", muxDisabled());
        return outbound;
    }

    private static JSONObject shadowsocks(String link) throws Exception {
        String body = link.substring("ss://".length());
        String name = "";
        int hashIdx = body.lastIndexOf('#');
        if (hashIdx >= 0) {
            name = body.substring(hashIdx + 1);
            body = body.substring(0, hashIdx);
        }
        String sspart = body;
        String plugin = null;
        if (body.contains("?plugin=")) {
            int qi = body.indexOf("?plugin=");
            sspart = body.substring(0, qi);
            plugin = urlDecode(body.substring(qi + 8));
        }
        String plain = b64(sspart, false);
        String method = null;
        String password = null;
        String host = null;
        int port = 443;
        if (plain != null && plain.contains("@")) {
            int at = plain.lastIndexOf('@');
            String mp = plain.substring(0, at);
            String hp = plain.substring(at + 1);
            int colon = mp.indexOf(':');
            if (colon > 0) {
                method = mp.substring(0, colon);
                password = mp.substring(colon + 1);
            }
            int hc = hp.lastIndexOf(':');
            if (hc > 0) {
                host = hp.substring(0, hc);
                try {
                    port = Integer.parseInt(hp.substring(hc + 1));
                } catch (Exception ignore) {
                }
            }
        }
        if (method == null || host == null) {
            String bs = b64(body, false);
            if (bs == null) {
                bs = body;
            }
            String[] m = bs.split(":");
            if (m.length >= 2 && !bs.contains("@")) {
                method = m[0];
            }
        }

        JSONObject outbound = new JSONObject();
        outbound.put("protocol", "shadowsocks");
        JSONObject settings = new JSONObject();
        JSONArray servers = new JSONArray();
        JSONObject s = new JSONObject();
        s.put("address", host != null ? host : "");
        s.put("port", port);
        if (method != null) {
            s.put("method", method);
        }
        if (password != null) {
            s.put("password", password);
        }
        servers.put(s);
        settings.put("servers", servers);
        outbound.put("settings", settings);
        outbound.put("streamSettings", new JSONObject().put("network", "tcp"));
        outbound.put("mux", muxDisabled());
        return outbound;
    }

    private static JSONObject socks(String link) throws Exception {
        URI uri = new URI(link.replaceFirst("^socks://", "socks5://"));
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 1080;

        JSONObject outbound = new JSONObject();
        outbound.put("protocol", "socks");
        JSONObject settings = new JSONObject();
        JSONArray servers = new JSONArray();
        JSONObject s = new JSONObject();
        s.put("address", host);
        s.put("port", port);
        if (uri.getUserInfo() != null) {
            String ui = uri.getUserInfo();
            int c = ui.indexOf(':');
            JSONArray users = new JSONArray();
            JSONObject u = new JSONObject();
            if (c > 0) {
                u.put("user", ui.substring(0, c));
                u.put("pass", ui.substring(c + 1));
            } else {
                u.put("user", ui);
            }
            users.put(u);
            s.put("users", users);
        }
        servers.put(s);
        settings.put("servers", servers);
        outbound.put("settings", settings);
        outbound.put("streamSettings", new JSONObject().put("network", "tcp"));
        outbound.put("mux", muxDisabled());
        return outbound;
    }

    private static JSONObject streamSettings(Map<String, String> q) throws Exception {
        String net = q.containsKey("type") ? q.get("type") : "tcp";
        if (net == null || net.isEmpty()) {
            net = "tcp";
        }
        if ("none".equals(net)) {
            net = "tcp";
        }
        String security = q.containsKey("security") ? q.get("security") : "none";
        String sni = q.containsKey("sni") ? q.get("sni") : null;
        String host = q.containsKey("host") ? q.get("host") : null;
        String fp = q.containsKey("fp") ? q.get("fp") : null;
        String alpn = q.containsKey("alpn") ? q.get("alpn") : null;
        String path = q.containsKey("path") ? q.get("path") : null;
        String serviceName = q.containsKey("serviceName") ? q.get("serviceName") : null;
        String flow = q.containsKey("flow") ? q.get("flow") : null;

        JSONObject ss = new JSONObject();
        ss.put("network", net);
        if ("ws".equals(net)) {
            JSONObject ws = new JSONObject();
            if (host != null && !host.isEmpty()) {
                ws.put("headers", new JSONObject().put("Host", host));
            }
            if (path != null && !path.isEmpty()) {
                ws.put("path", path);
            }
            ss.put("wsSettings", ws);
        } else if ("grpc".equals(net)) {
            JSONObject grpc = new JSONObject();
            grpc.put("serviceName", serviceName != null ? serviceName : (host != null ? host : ""));
            ss.put("grpcSettings", grpc);
        } else if ("httpupgrade".equals(net)) {
            JSONObject hu = new JSONObject();
            if (path != null && !path.isEmpty()) {
                hu.put("path", path);
            }
            ss.put("httpupgradeSettings", hu);
        } else if ("splithttp".equals(net)) {
            JSONObject sh = new JSONObject();
            if (path != null && !path.isEmpty()) {
                sh.put("path", path);
            }
            ss.put("splithttpSettings", sh);
        }
        ss.put("security", security);
        if (!"none".equals(security)) {
            JSONObject tls = new JSONObject();
            JSONObject reality = new JSONObject();
            boolean isReality = "reality".equals(security);
            boolean hasPublic = q.containsKey("publicKey");
            if (isReality || hasPublic) {
                tls.put("fingerprint", fp != null ? fp : "chrome");
                if (hasPublic) {
                    reality.put("publicKey", q.get("publicKey"));
                }
                if (q.containsKey("shortId")) {
                    reality.put("shortId", q.get("shortId"));
                }
                if ("reality".equals(security) && !hasPublic) {
                    reality.put("show", false);
                }
                if (flow != null && !flow.isEmpty()) {
                    reality.put("spiderX", "");
                }
            } else {
                tls.put("allowInsecure", true);
                if (fp != null && !fp.isEmpty()) {
                    tls.put("fingerprint", fp);
                }
                if (alpn != null && !alpn.isEmpty()) {
                    JSONArray a = new JSONArray();
                    for (String s : alpn.split(",")) {
                        if (!s.trim().isEmpty()) {
                            a.put(s.trim());
                        }
                    }
                    tls.put("alpn", a);
                }
            }
            if (sni != null && !sni.isEmpty()) {
                tls.put("serverName", sni);
            } else if (host != null && !host.isEmpty()) {
                tls.put("serverName", host);
            }
            if (isReality) {
                tls.put("realitySettings", reality);
            }
            ss.put("tlsSettings", tls);
        }
        return ss;
    }

    private static JSONObject muxDisabled() throws Exception {
        JSONObject mux = new JSONObject();
        mux.put("enabled", false);
        return mux;
    }
}