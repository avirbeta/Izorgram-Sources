package incycore;

import go.Seq;

public abstract class Incycore {
    public static final long PingDelayError = 10000;
    public static final long PingDelayTimeout = 11000;

    public static final class proxyDialerController implements Seq.Proxy, DialerController {
        private final int refnum;

        public proxyDialerController(int i6) {
            this.refnum = i6;
            Seq.trackGoRef(i6, this);
        }

        @Override
        public final int incRefnum() {
            Seq.incGoRef(this.refnum, this);
            return this.refnum;
        }

        @Override
        public native boolean protectFd(long j10);
    }

    public static final class proxyPingCallback implements Seq.Proxy, PingCallback {
        private final int refnum;

        public proxyPingCallback(int i6) {
            this.refnum = i6;
            Seq.trackGoRef(i6, this);
        }

        @Override
        public final int incRefnum() {
            Seq.incGoRef(this.refnum, this);
            return this.refnum;
        }

        @Override
        public native void onPingComplete();

        @Override
        public native void onPingResult(int i6, long j10, String str);
    }

    static {
        Seq.touch();
        _init();
    }

    private Incycore() {
    }

    private static native void _init();

    public static native String version();

    public static native String getXrayVersion();

    public static native String xrayVersion();

    public static native boolean isXrayRunning();

    public static native boolean xrayIsRunning();

    public static native boolean isTun2SocksRunning();

    public static native boolean tun2SocksIsRunning();

    public static native void setAssetsPath(String str);

    public static native void setXrayAssetsPath(String str);

    public static native void validateAssets(String str) throws Exception;

    public static native void validateGeoFiles(String str) throws Exception;

    public static native void registerDialerController(DialerController dialerController);

    public static native void registerListenerController(DialerController dialerController);

    public static native void initMemory();

    public static native void initMemoryManagement();

    public static native void initDns(String str, DialerController dialerController);

    public static native void resetDns();

    public static native void startXray(String str) throws Exception;

    public static native void xrayStart(String str) throws Exception;

    public static native void startXrayWithTunFd(String str, int i6) throws Exception;

    public static native void stopXray() throws Exception;

    public static native void xrayStop() throws Exception;

    public static native void startTun2Socks(long j10, String str) throws Exception;

    public static native void tun2SocksStart(int i6, String str) throws Exception;

    public static native void stopTun2Socks() throws Exception;

    public static native void tun2SocksStop() throws Exception;

    public static native void startVPN(String str, int i6, String str2) throws Exception;

    public static native void stopVPN() throws Exception;

    public static native long ping(long j10, String str, String str2) throws Exception;

    public static native long pingConfig(String str, int i6, String str2, String str3) throws Exception;

    public static native String pingConfigs(String str, int i6, String str2);

    public static native String pingConfigsBatchSync(String str, int i6, String str2);

    public static native long pingProxy(int i6, String str, String str2) throws Exception;

    public static native long pingXray(String str, long j10, String str2, String str3) throws Exception;

    public static native long measureDelay(long j10, String str, String str2) throws Exception;

    public static native void freeMemory();

    public static native void releaseMemory();

    public static void touch() {
    }
}