package org.telegram.messenger;

import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Локальный архив сообщений. Пишет только на устройство, ничего не отправляет.
 * Позволяет позже посмотреть текст, даже если сообщение удалили в Telegram.
 */
public final class IzorgramMessageArchive {

    private static final Object LOCK = new Object();
    private static final Set<String> SEEN = new HashSet<>();
    private static final long MAX_FILE_SIZE = 8L * 1024L * 1024L;
    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

    public static final class Entry {
        public final long dialogId;
        public final int messageId;
        public final int date;
        public final long senderId;
        public final String text;
        public final boolean out;

        Entry(long dialogId, int messageId, int date, long senderId, String text, boolean out) {
            this.dialogId = dialogId;
            this.messageId = messageId;
            this.date = date;
            this.senderId = senderId;
            this.text = text;
            this.out = out;
        }

        public String title() {
            return (out ? "→ " : "← ") + "dialog " + dialogId + " · " + messageId;
        }

        public String subtitle() {
            return DATE.format(new Date(date * 1000L)) + " · " + text;
        }
    }

    private IzorgramMessageArchive() {
    }

    private static File file() {
        return new File(ApplicationLoader.applicationContext.getFilesDir(), "izorgram_messages.jsonl");
    }

    public static void save(long dialogId, TLRPC.Message message) {
        if (message == null || message.id == 0) return;
        String text = message.message == null ? "" : message.message.trim();
        if (text.isEmpty()) {
            if (message.media == null) return;
            text = "[медиа] " + message.media.getClass().getSimpleName();
        }

        String key = dialogId + ":" + message.id;
        synchronized (LOCK) {
            if (!SEEN.add(key)) return;
            if (SEEN.size() > 20000) SEEN.clear();
            try {
                File f = file();
                if (f.length() > MAX_FILE_SIZE) {
                    File old = new File(f.getParentFile(), "izorgram_messages.old.jsonl");
                    if (old.exists()) //noinspection ResultOfMethodCallIgnored
                        old.delete();
                    //noinspection ResultOfMethodCallIgnored
                    f.renameTo(old);
                }
                JSONObject o = new JSONObject();
                o.put("d", dialogId);
                o.put("m", message.id);
                o.put("t", message.date);
                o.put("s", peerId(message.from_id));
                o.put("x", text);
                o.put("o", message.out);
                try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(f, true), StandardCharsets.UTF_8))) {
                    w.write(o.toString());
                    w.newLine();
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    public static List<Entry> load(int limit) {
        ArrayList<Entry> result = new ArrayList<>();
        ArrayDeque<Entry> tail = new ArrayDeque<>();
        File f = file();
        if (!f.exists()) return result;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                try {
                    JSONObject o = new JSONObject(line);
                    Entry e = new Entry(
                            o.optLong("d"), o.optInt("m"), o.optInt("t"),
                            o.optLong("s"), o.optString("x"), o.optBoolean("o"));
                    tail.addLast(e);
                    if (tail.size() > limit) tail.removeFirst();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        while (!tail.isEmpty()) result.add(tail.removeFirst());
        return result;
    }

    private static long peerId(TLRPC.Peer peer) {
        if (peer == null) return 0;
        if (peer.user_id != 0) return peer.user_id;
        if (peer.chat_id != 0) return -peer.chat_id;
        if (peer.channel_id != 0) return -peer.channel_id;
        return 0;
    }
}
