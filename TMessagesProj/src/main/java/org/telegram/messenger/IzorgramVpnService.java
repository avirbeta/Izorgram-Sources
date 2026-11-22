package org.telegram.messenger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import incycore.DialerController;
import incycore.Incycore;

public class IzorgramVpnService extends VpnService implements DialerController {

    private static final String CHANNEL = "izorgram_vpn";
    public static final String EXTRA_LINK = "link";

    private static volatile IzorgramVpnService instance;
    private static volatile ParcelFileDescriptor tun = null;
    private static volatile String currentLink;
    private static volatile String status = "";

    private Thread worker;
    private final Object lock = new Object();

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String link = intent != null ? intent.getStringExtra(EXTRA_LINK) : null;
        if (link != null) {
            currentLink = link;
        }
        if (currentLink == null || currentLink.isEmpty()) {
            // START_STICKY может перезапустить сервис без ссылки — не держим пустой туннель.
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(1, buildNotification(getString(R.string.IzorgramVpnTitle), connectionStatus()));
        ensureTunnel();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopConnection();
        try {
            stopForeground(true);
        } catch (Exception ignore) {
        }
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onRevoke() {
        // Пользователь отключил VPN или его перехватило другое приложение.
        stopConnection();
        stopSelf();
        super.onRevoke();
    }

    @Override
    public boolean protectFd(long fd) {
        try {
            if (fd > 0) {
                return protect((int) fd);
            }
        } catch (Exception ignore) {
        }
        return false;
    }

    public static boolean isActive() {
        try {
            return Incycore.isXrayRunning() || Incycore.isTun2SocksRunning();
        } catch (Throwable t) {
            return false;
        }
    }

    public static String connectionStatus() {
        if (isActive()) {
            return status != null && !status.isEmpty() ? status : "Connected";
        }
        return "Idle";
    }

    public static String currentLink() {
        return currentLink;
    }

    private void ensureTunnel() {
        synchronized (lock) {
            if (worker != null && worker.isAlive()) {
                return;
            }
            worker = new Thread(() -> {
                try {
                    startConnection(currentLink);
                } catch (Exception e) {
                    FileLog.e(e);
                    status = e.getMessage();
                    try {
                        stopSelf();
                    } catch (Exception ignore) {
                    }
                }
            }, "izorgram-vpn");
            worker.start();
        }
    }

    private void startConnection(String link) throws Exception {
        stopConnection();
        if (link == null || link.isEmpty()) {
            return;
        }
        status = "Preparing assets";
        ensureGeoAssets();

        Incycore.touch();
        File geoDir = new File(getFilesDir(), "geo");
        Incycore.setXrayAssetsPath(geoDir.getAbsolutePath());
        Incycore.setAssetsPath(geoDir.getAbsolutePath());
        Incycore.registerDialerController(this);

        status = "Building tunnel";
        Builder builder = new Builder();
        builder.setSession("Izorgram VPN");
        builder.setMtu(1500);
        builder.addAddress("10.8.0.1", 24);
        builder.addAddress("fd00::1", 120);
        builder.addRoute("0.0.0.0", 0);
        builder.addRoute("::", 0);
        builder.addDnsServer("8.8.8.8");
        builder.addDnsServer("1.1.1.1");
        ParcelFileDescriptor pfd = builder.establish();
        if (pfd == null) {
            throw new Exception("tunnel establish failed - check VPN permission");
        }
        synchronized (this) {
            tun = pfd;
        }

        String configJson = VpnXrayConfig.buildFullConfig(link).toString();
        status = "Starting Xray";
        Incycore.initMemory();
        Incycore.startXray(configJson);
        long t0 = System.currentTimeMillis();
        while (!Incycore.isXrayRunning()) {
            if (System.currentTimeMillis() - t0 > 15000) {
                throw new Exception("Xray start timeout");
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignore) {
            }
        }

        status = "Starting tun2socks";
        Incycore.startTun2Socks(pfd.getFd(), VpnXrayConfig.SOCKS_HOST + ":" + VpnXrayConfig.SOCKS_PORT);
        status = "Connected";
        try {
            Incycore.initDns("", this);
        } catch (Throwable ignore) {
        }
        updateNotification();
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, IzorgramVpnService.class);
        context.stopService(intent);
        ParcelFileDescriptor p = tun;
        if (p != null) {
            try {
                p.close();
            } catch (Exception ignore) {
            }
            tun = null;
        }
        try {
            Incycore.stopTun2Socks();
            Incycore.stopXray();
        } catch (Throwable ignore) {
        }
        status = "Idle";
    }

    private void stopConnection() {
        ParcelFileDescriptor p;
        synchronized (this) {
            p = tun;
            tun = null;
        }
        try {
            Incycore.stopTun2Socks();
        } catch (Throwable ignore) {
        }
        try {
            Incycore.stopXray();
        } catch (Throwable ignore) {
        }
        if (p != null) {
            try {
                p.close();
            } catch (Exception ignore) {
            }
        }
        status = "Idle";
    }

    private void ensureGeoAssets() {
        try {
            File geoDir = new File(getFilesDir(), "geo");
            if (!geoDir.exists() && !geoDir.mkdirs() && !geoDir.exists()) {
                return;
            }
            copyAsset("geo/geoip.dat", new File(geoDir, "geoip.dat"));
            copyAsset("geo/geosite.dat", new File(geoDir, "geosite.dat"));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void copyAsset(String name, File out) throws Exception {
        if (out.exists() && out.length() > 0) {
            return;
        }
        try (InputStream is = getAssets().open(name);
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) > 0) {
                fos.write(buf, 0, r);
            }
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, getString(R.string.IzorgramVpnTitle), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.IzorgramVpnNotification));
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void updateNotification() {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.notify(1, buildNotification(getString(R.string.IzorgramVpnTitle), connectionStatus()));
            }
        } catch (Exception ignore) {
        }
    }

    private Notification buildNotification(String title, String text) {
        // Intent на сервис через getActivities не работает: нужен getActivity.
        Intent intent = new Intent(this, org.telegram.ui.LaunchActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.outline_shield_check)
                .setOngoing(true)
                .setContentIntent(pi);
        return b.build();
    }
}