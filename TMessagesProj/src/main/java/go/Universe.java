package go;

// Universe mirrors the Go package bound alongside the Xray core.
// Its static initializer boots the Go runtime for the whole process.
public abstract class Universe {

    static {
        _init();
    }

    // Empty method to run class initializer.
    public static void touch() {
    }

    private static native void _init();
}