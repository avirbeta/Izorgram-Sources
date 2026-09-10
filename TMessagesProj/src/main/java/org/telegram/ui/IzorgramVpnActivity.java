package org.telegram.ui;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.IzorgramVpnService;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Cells.TextCheckCell;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class IzorgramVpnActivity extends BaseFragment {

    private static final String PREFS = "izorgram_vpn";

    private UniversalRecyclerView listView;
    private ArrayList<Server> servers = new ArrayList<>();
    private boolean pinging;

    public static class Server {
        String name;
        String type;
        String host;
        int port;
        String user;
        String pass;
        String raw;
        long ping = -1;
        boolean connecting;
        boolean selected;

        JSONObject toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("name", name);
                o.put("type", type);
                o.put("host", host);
                o.put("port", port);
                o.put("user", user);
                o.put("pass", pass);
                o.put("raw", raw);
                return o;
            } catch (Exception ignore) {
                return null;
            }
        }

        static Server fromJson(JSONObject o) {
            Server s = new Server();
            s.name = o.optString("name");
            s.type = o.optString("type");
            s.host = o.optString("host");
            s.port = o.optInt("port");
            s.user = o.optString("user");
            s.pass = o.optString("pass");
            s.raw = o.optString("raw");
            return s;
        }
    }

    private SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void loadServers() {
        servers.clear();
        try {
            String raw = prefs().getString("servers", "");
            if (raw != null && !raw.isEmpty()) {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); ++i) {
                    servers.add(Server.fromJson(arr.getJSONObject(i)));
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        int last = prefs().getInt("last", -1);
        for (int i = 0; i < servers.size(); ++i) {
            servers.get(i).selected = i == last;
        }
    }

    private void saveServers() {
        prefs().edit().putString("servers", serversToJson().toString()).apply();
    }

    private JSONArray serversToJson() {
        JSONArray arr = new JSONArray();
        for (Server s : servers) {
            JSONObject o = s.toJson();
            if (o != null) {
                arr.put(o);
            }
        }
        return arr;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.IzorgramVpnTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        loadServers();

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        return fragmentView = contentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.IzorgramVpnTitle)));
        items.add(UItem.asButton(1, getString(R.string.IzorgramVpnAdd), getString(R.string.IzorgramVpnAddInfo)));
        items.add(UItem.asButton(2, getString(R.string.IzorgramVpnPingAll)));

        UItem auto = UItem.asCheck(4, getString(R.string.IzorgramVpnAutoConnect)).setChecked(prefs().getBoolean("auto_connect_vpn", false));
        auto.subtext = getString(R.string.IzorgramVpnAutoConnectInfo);
        items.add(auto);

        boolean vpnActive = IzorgramVpnService.isActive();
        String systemStatus = IzorgramVpnService.connectionStatus();
        UItem system = UItem.asButton(5,
                vpnActive ? getString(R.string.IzorgramVpnSystemDisconnect) : getString(R.string.IzorgramVpnSystemConnect),
                getString(R.string.IzorgramVpnSystemStatus) + ": " + systemStatus);
        system.subtext = getString(R.string.IzorgramVpnSystemInfo);
        items.add(system);

        if (servers.isEmpty()) {
            items.add(UItem.asShadow(null));
            items.add(UItem.asButton(-1, getString(R.string.IzorgramVpnEmpty)));
        } else {
            items.add(UItem.asShadow(null));
            items.add(UItem.asHeader(getString(R.string.IzorgramVpnServers)));
            for (int i = 0; i < servers.size(); ++i) {
                Server s = servers.get(i);
                String status;
                if (s.ping >= 0) {
                    status = s.ping + " ms" + (s.selected ? getString(R.string.IzorgramVpnActive) : "");
                } else if (s.ping == -2) {
                    status = getString(R.string.IzorgramVpnUnavailable);
                } else {
                    status = getString(R.string.IzorgramVpnNotPinged);
                }
                String title = "[" + s.type + "] " + (s.name != null && !s.name.isEmpty() ? s.name : s.host + ":" + s.port);
                UItem serverItem = UItem.asButton(100 + i, title, status);
                items.add(serverItem);
            }
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case 1:
                showAddDialog();
                break;
            case 2:
                pingAll();
                break;
            case 5:
                toggleSystemVpn();
                break;
            case 4:
                boolean nv = !prefs().getBoolean("auto_connect_vpn", false);
                prefs().edit().putBoolean("auto_connect_vpn", nv).apply();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(nv);
                }
                listView.adapter.update(false);
                break;
            default:
                if (item.id >= 100) {
                    int index = item.id - 100;
                    if (index >= 0 && index < servers.size()) {
                        connectServer(index);
                    }
                }
                break;
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= 100) {
            int index = item.id - 100;
            if (index >= 0 && index < servers.size()) {
                servers.remove(index);
                saveServers();
                listView.adapter.update(false);
                return true;
            }
        }
        return false;
    }

    private void showAddDialog() {
        if (getParentActivity() == null) {
            return;
        }
        EditText editText = new EditText(getParentActivity());
        editText.setHint("vless://… | vmess://… | trojan://… | ss://… | socks5://…");
        editText.setSingleLine(true);
        Context ctx = getParentActivity();
        new AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.IzorgramVpnAdd))
            .setView(editText)
            .setPositiveButton(getString(R.string.OK), (dialog, which) -> {
                String text = editText.getText().toString().trim();
                if (text.isEmpty()) {
                    return;
                }
                parseSubscription(text);
            })
            .setNegativeButton(getString(R.string.Cancel), null)
            .show();
    }

    private void parseSubscription(final String link) {
        if (link.startsWith("vless://") || link.startsWith("vmess://") || link.startsWith("trojan://") ||
            link.startsWith("ss://") || link.startsWith("socks5://") || link.startsWith("socks://") ||
            link.startsWith("http://") || link.startsWith("https://")) {
            if (link.startsWith("http://") || link.startsWith("https://")) {
                fetchList(link);
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    int added = parseServers(link);
                    if (added > 0) {
                        saveServers();
                        listView.adapter.update(false);
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, formatString(R.string.IzorgramVpnAdded, added)).show(true);
                    } else {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.IzorgramVpnParseFail)).show(true);
                    }
                });
            }
        } else if (link.contains("\n") || link.contains(";")) {
            int added = parseServers(link);
            if (added > 0) {
                saveServers();
                listView.adapter.update(false);
                BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, formatString(R.string.IzorgramVpnAdded, added)).show(true);
            } else {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.IzorgramVpnParseFail)).show(true);
            }
        } else if (link.matches("\\d{1,3}(\\.\\d{1,3}){3}:\\d+")) {
            addRaw(link);
        } else {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.IzorgramVpnParseFail)).show(true);
        }
    }

    private void addRaw(String raw) {
        String[] parts = raw.split(":");
        if (parts.length >= 2) {
            Server s = new Server();
            s.type = "socks5";
            s.name = raw;
            if (parts.length == 3) {
                s.user = parts[0];
                s.host = parts[1];
                s.port = parsePort(parts[2]);
            } else {
                s.host = parts[0];
                s.port = parsePort(parts[1]);
            }
            s.raw = linkFromFields(s);
            servers.add(s);
            saveServers();
            listView.adapter.update(false);
        }
    }

    private void fetchList(final String url) {
        Utilities.globalQueue.postRunnable(() -> {
            String data = null;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                InputStream is = conn.getInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int r;
                while ((r = is.read(buf)) > 0) {
                    baos.write(buf, 0, r);
                }
                is.close();
                data = baos.toString("UTF-8");
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (data == null || data.trim().isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(IzorgramVpnActivity.this).createSimpleBulletin(R.raw.error, getString(R.string.IzorgramVpnFetchFail)).show(true));
                return;
            }
            boolean isBase64 = !data.contains("://");
            String text = data;
            if (isBase64) {
                text = decodeB64(data);
                if (text == null) {
                    text = data;
                }
            }
            final String finalText = text;
            AndroidUtilities.runOnUIThread(() -> {
                int added = parseServers(finalText);
                if (added > 0) {
                    saveServers();
                    listView.adapter.update(false);
                    BulletinFactory.of(IzorgramVpnActivity.this).createSimpleBulletin(R.raw.chats_infotip, formatString(R.string.IzorgramVpnAdded, added)).show(true);
                } else {
                    BulletinFactory.of(IzorgramVpnActivity.this).createSimpleBulletin(R.raw.error, getString(R.string.IzorgramVpnParseFail)).show(true);
                }
            });
        });
    }

    private int parseServers(String text) {
        int added = 0;
        String[] lines = text.split("\\n|;");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            Server s = parseLine(line);
            if (s != null) {
                servers.add(s);
                added++;
            }
        }
        return added;
    }

    private Server parseLine(String line) {
        try {
            if (line.startsWith("socks5://") || line.startsWith("socks://")) {
                java.net.URI uri = new java.net.URI(line.replace("socks://", "socks5://"));
                Server s = new Server();
                s.type = "socks5";
                s.host = uri.getHost();
                s.port = uri.getPort() > 0 ? uri.getPort() : 1080;
                s.user = uri.getUserInfo() != null && uri.getUserInfo().contains(":") ? uri.getUserInfo().split(":")[0] : "";
                s.pass = uri.getUserInfo() != null && uri.getUserInfo().contains(":") ? uri.getUserInfo().substring(uri.getUserInfo().indexOf(':') + 1) : "";
                s.name = s.host + ":" + s.port;
                s.raw = line;
                return s;
            }
            if (line.startsWith("ss://")) {
                Server s = parseSs(line.substring(5));
                if (s != null) {
                    s.raw = line;
                }
                return s;
            }
            if (line.startsWith("vmess://")) {
                Server s = parseVmess(line.substring(8));
                if (s != null) {
                    s.raw = line;
                }
                return s;
            }
            if (line.startsWith("trojan://")) {
                java.net.URI uri = new java.net.URI(line);
                Server s = new Server();
                s.type = "trojan";
                s.host = uri.getHost();
                s.port = uri.getPort() > 0 ? uri.getPort() : 443;
                s.user = "";
                s.pass = uri.getUserInfo();
                s.name = s.host + ":" + s.port;
                s.raw = line;
                return s;
            }
            if (line.startsWith("vless://")) {
                java.net.URI uri = new java.net.URI(line);
                Server s = new Server();
                s.type = "vless";
                s.host = uri.getHost();
                s.port = uri.getPort() > 0 ? uri.getPort() : 443;
                s.pass = uri.getUserInfo();
                s.name = uri.getFragment() != null && !uri.getFragment().isEmpty() ? uri.getFragment() : s.host;
                s.raw = line;
                return s;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private Server parseSs(String body) {
        try {
            String payload = body;
            Server s = new Server();
            s.type = "shadowsocks";
            if (payload.startsWith("aes-") || payload.startsWith("chacha20") || payload.startsWith("rc4-") || payload.startsWith("xchacha")) {
                String[] parts = payload.split("@");
                if (parts.length == 2) {
                    s.user = partDecode(parts[0]);
                    int nameIdx = parts[1].indexOf('#');
                    String hostPort = nameIdx > 0 ? parts[1].substring(0, nameIdx) : parts[1];
                    String[] hp = hostPort.split(":");
                    s.host = hp[0];
                    s.port = hp.length > 1 ? parsePort(hp[1]) : 443;
                    s.name = nameIdx > 0 ? parts[1].substring(nameIdx + 1) : s.host;
                    return s;
                }
            }
            int idx = payload.indexOf('#');
            String b64 = idx > 0 ? payload.substring(0, idx) : payload;
            String decoded = decodeB64(b64);
            if (decoded == null) {
                return null;
            }
            if (decoded.contains("@")) {
                String[] parts = decoded.split("@");
                s.user = parts[0];
                int hi = parts[1].indexOf('#');
                String hostPort = hi > 0 ? parts[1].substring(0, hi) : parts[1];
                String[] hp = hostPort.split(":");
                s.host = hp[0];
                s.port = hp.length > 1 ? parsePort(hp[1]) : 443;
                s.name = idx > 0 ? payload.substring(idx + 1) : s.host;
            } else {
                int sep = decoded.lastIndexOf(':');
                s.host = decoded.substring(0, sep);
                s.port = parsePort(decoded.substring(sep + 1));
                s.name = s.host;
            }
            return s;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private Server parseVmess(String body) {
        try {
            String decoded = decodeB64(body);
            if (decoded == null) {
                decoded = body;
            }
            JSONObject o = new JSONObject(decoded);
            Server s = new Server();
            s.type = "vmess";
            s.host = o.getString("add");
            s.port = o.optInt("port", 443);
            s.user = o.optString("id");
            s.pass = o.optString("aid", "0");
            s.name = o.optString("ps", s.host + ":" + s.port);
            return s;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static String partDecode(String s) {
        String d = decodeB64(s);
        return d != null ? d : s;
    }

    private static String decodeB64(String s) {
        try {
            return new String(android.util.Base64.decode(s, android.util.Base64.DEFAULT), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static int parsePort(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 443;
        }
    }

    private void pingAll() {
        if (pinging) {
            return;
        }
        pinging = true;
        for (Server s : servers) {
            s.ping = -1;
        }
        listView.adapter.update(false);
        final CountDownLatch latch = new CountDownLatch(servers.size());
        for (final Server s : servers) {
            new Thread(() -> {
                long ping = pingServer(s);
                synchronized (this) {
                    s.ping = ping;
                }
                latch.countDown();
            }).start();
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                latch.await(12, TimeUnit.SECONDS);
            } catch (Exception ignore) {
            }
            AndroidUtilities.runOnUIThread(() -> {
                pinging = false;
                listView.adapter.update(false);
            });
        });
    }

    private static long pingServer(Server s) {
        long start = System.currentTimeMillis();
        try {
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new InetSocketAddress(s.host, s.port), 5000);
            socket.close();
            return System.currentTimeMillis() - start;
        } catch (Exception e) {
            return -2;
        }
    }

    private int pendingServer = -1;

    @Override
    public void onResume() {
        super.onResume();
        if (pendingServer >= 0) {
            int idx = pendingServer;
            pendingServer = -1;
            if (getParentActivity() != null) {
                if (VpnService.prepare(getParentActivity()) == null) {
                    startSystemVpnServer(idx);
                } else {
                    BulletinFactory.of(IzorgramVpnActivity.this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.IzorgramVpnPermGrant)).show(true);
                }
            }
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
    }

    private void connectServer(int index) {
        final Server s = servers.get(index);
        if (getParentActivity() == null) {
            return;
        }
        if (pendingServer >= 0) {
            return;
        }
        Intent prepare = VpnService.prepare(getParentActivity());
        if (prepare != null) {
            pendingServer = index;
            try {
                getParentActivity().startActivityForResult(prepare, 1010);
            } catch (Exception e) {
                pendingServer = -1;
                FileLog.e(e);
            }
            return;
        }
        startSystemVpnServer(index);
    }

    private void toggleSystemVpn() {
        if (IzorgramVpnService.isActive()) {
            IzorgramVpnService.stop(ApplicationLoader.applicationContext);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.IzorgramVpnDisconnected)).show(true);
            listView.adapter.update(false);
            return;
        }
        int sel = prefs().getInt("last", -1);
        if (sel >= 0 && sel < servers.size()) {
            connectServer(sel);
        }
    }

    private void startSystemVpnServer(int index) {
        Server s = servers.get(index);
        if (s == null) {
            return;
        }
        String link = s.raw;
        if (link == null || link.isEmpty()) {
            link = linkFromFields(s);
        }
        prefs().edit().putInt("last", index).apply();
        for (int i = 0; i < servers.size(); ++i) {
            servers.get(i).selected = i == index;
        }
        Context ctx = ApplicationLoader.applicationContext;
        Intent i = new Intent(ctx, IzorgramVpnService.class).putExtra(IzorgramVpnService.EXTRA_LINK, link);
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, formatString(R.string.IzorgramVpnConnected, s.name != null && !s.name.isEmpty() ? s.name : s.host)).show(true);
    }

    private static String linkFromFields(Server s) {
        if ("socks5".equals(s.type)) {
            StringBuilder sb = new StringBuilder("socks5://");
            if (s.user != null && !s.user.isEmpty()) {
                sb.append(s.user);
                if (s.pass != null && !s.pass.isEmpty()) {
                    sb.append(':').append(s.pass);
                }
                sb.append('@');
            }
            sb.append(s.host).append(':').append(s.port);
            return sb.toString();
        }
        return s.raw;
    }

    public static void autoConnectIfNeeded() {
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("auto_connect_vpn", false)) {
            return;
        }
        int last = prefs.getInt("last", -1);
        if (last < 0) {
            return;
        }
        try {
            JSONArray arr = new JSONArray(prefs.getString("servers", "[]"));
            if (last >= arr.length()) {
                return;
            }
            JSONObject o = arr.getJSONObject(last);
            String raw = o.optString("raw");
            if (raw == null || raw.isEmpty()) {
                return;
            }
            if (VpnService.prepare(ApplicationLoader.applicationContext) != null) {
                return;
            }
            Context ctx = ApplicationLoader.applicationContext;
            Intent i = new Intent(ctx, IzorgramVpnService.class).putExtra(IzorgramVpnService.EXTRA_LINK, raw);
            if (Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(i);
            } else {
                ctx.startService(i);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}