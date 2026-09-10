package org.telegram.messenger;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;

public class Izorgram {

    public static final String PREFIX = "izorgram-backup:";
    private static final String[] SKIP_KEYS = {"currentAccount", "noInitialMessages", "newThemeInstalled", "language"};

    public static String prefsNameAcc(int account) {
        return "userconfig_" + account;
    }

    public static String buildBackupJson(int account) {
        try {
            JSONObject root = new JSONObject();
            root.put("v", 1);
            root.put("app", "izorgram");
            JSONObject acc = new JSONObject();
            acc.put("main", dump(MessagesController.getInstance(account).getMainSettings().getAll()));
            acc.put("system", dump(ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Context.MODE_PRIVATE).getAll()));
            acc.put("mainconfig", dump(ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE).getAll()));
            root.put("prefs", acc);
            return root.toString();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static JSONObject dump(java.util.Map<String, ?> map) {
        JSONObject o = new JSONObject();
        for (java.util.Map.Entry<String, ?> e : map.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Boolean) {
                try { o.put(e.getKey(), ((Boolean) v)); } catch (Exception ignore) {}
            } else if (v instanceof Integer || v instanceof Long || v instanceof Float) {
                try { o.put(e.getKey(), ((Number) v)); } catch (Exception ignore) {}
            } else if (v instanceof String) {
                try { o.put(e.getKey(), (String) v); } catch (Exception ignore) {}
            } else if (v instanceof java.util.Set) {
                try { o.put(e.getKey(), TextUtils.join(",", (java.util.Set<String>) v)); } catch (Exception ignore) {}
            }
        }
        return o;
    }

    public static void backupToSavedMessages(int account, Runnable onDone) {
        String json = buildBackupJson(account);
        if (json == null) {
            return;
        }
        long self = UserConfig.getInstance(account).getClientUserId();
        if (self == 0) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(PREFIX + json, self);
        params.notify = false;
        SendMessagesHelper.getInstance(account).sendMessage(params);
        if (onDone != null) {
            onDone.run();
        }
    }

    public static void restoreLastFromSavedMessages(int account, Runnable onDone) {
        long self = UserConfig.getInstance(account).getClientUserId();
        MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
            String json = null;
            try {
                SQLiteCursor cursor = MessagesStorage.getInstance(account).getDatabase().queryFinalized(String.format(
                    "SELECT mid, data FROM messages_v2 WHERE uid = %s ORDER BY date DESC LIMIT 60", self));
                while (cursor.next()) {
                    final int mid = cursor.intValue(0);
                    final NativeByteBuffer data = cursor.byteBufferValue(1);
                    final TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    if (message != null) {
                        message.readAttachPath(data, UserConfig.getInstance(account).clientUserId);
                    }
                    data.reuse();
                    if (message != null && message.out && message.message != null && message.message.startsWith(PREFIX)) {
                        json = message.message.substring(PREFIX.length());
                        break;
                    }
                }
                cursor.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
            String finalJson = json;
            AndroidUtilities.runOnUIThread(() -> {
                if (finalJson != null) {
                    if (applyBackupJson(account, finalJson)) {
                        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, 0);
                    }
                }
                if (onDone != null) {
                    onDone.run();
                }
            });
        });
    }

    public static boolean applyBackupJson(int account, String json) {
        try {
            JSONObject root = new JSONObject(json);
            if (!"izorgram".equals(root.optString("app"))) {
                return false;
            }
            JSONObject prefs = root.optJSONObject("prefs");
            if (prefs == null) {
                return false;
            }
            apply(account, MessagesController.getInstance(account).getMainSettings(), prefs.optJSONObject("main"));
            apply(account, ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE), prefs.optJSONObject("mainconfig"));
            apply(account, ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Context.MODE_PRIVATE), prefs.optJSONObject("system"));
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    private static boolean isSkipped(String key) {
        for (String s : SKIP_KEYS) {
            if (s.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    private static void apply(int account, android.content.SharedPreferences prefs, JSONObject src) {
        if (src == null) {
            return;
        }
        android.content.SharedPreferences.Editor editor = prefs.edit();
        Iterator<String> it = src.keys();
        while (it.hasNext()) {
            String key = it.next();
            if (isSkipped(key)) {
                continue;
            }
            try {
                Object v = src.get(key);
                if (v instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) v);
                } else if (v instanceof Integer) {
                    editor.putInt(key, (Integer) v);
                } else if (v instanceof Long) {
                    editor.putLong(key, (Long) v);
                } else if (v instanceof Double) {
                    editor.putFloat(key, ((Double) v).floatValue());
                } else if (v instanceof String) {
                    editor.putString(key, (String) v);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        editor.apply();
    }

    public static boolean exportToFile(Context context, Uri uri, int account) {
        try {
            OutputStream os = context.getContentResolver().openOutputStream(uri, "w");
            if (os == null) {
                return false;
            }
            String json = buildBackupJson(account);
            os.write(json == null ? "{}".getBytes() : json.getBytes());
            os.close();
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    public static String readFile(Context context, Uri uri) {
        try {
            ContentResolver cr = context.getContentResolver();
            InputStream is = cr.openInputStream(uri);
            if (is == null) {
                return null;
            }
            int size = (int) (cr.openAssetFileDescriptor(uri, "r") != null ? cr.openAssetFileDescriptor(uri, "r").getLength() : 0);
            byte[] data;
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(size);
            byte[] b = new byte[8192];
            int r;
            while ((r = is.read(b)) > 0) {
                baos.write(b, 0, r);
            }
            is.close();
            data = baos.toByteArray();
            return new String(data, "UTF-8");
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }
}