package com.frostether.frostchain;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Temporal capsules sealed or added on this device, in temporal.json next to the chain. On a phone that's the app's
 * own storage, which other apps can't read. Uninstalling the app deletes it, so capsules can be copied out as capsule
 * codes ("temporal1:" + base64url JSON) and added back.
 */
public final class TemporalStore {
    private final File file;
    private final List<Map<String, Object>> capsules = new ArrayList<>();

    public TemporalStore(File dir) {
        this.file = dir == null ? null : new File(dir, "temporal.json");
        if (this.file != null && this.file.exists()) {
            try {
                Object parsed = Json.parse(new String(Files.readAllBytes(this.file.toPath()), Bytes.UTF8));
                if (parsed instanceof List) {
                    for (Object o : (List<?>) parsed) {
                        if (o instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> m = (Map<String, Object>) o;
                            this.capsules.add(m);
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                Log.w("temporal", "couldn't read " + this.file + ": " + e);
            }
        }
    }

    /** A capsule as stored: its words, salt and opening time, and its fingerprint. */
    public static Map<String, Object> capsule(long opens, String salt, String text) {
        if (!salt.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("a capsule's salt is 32 hex characters");
        }
        if (text.trim().isEmpty()) {
            throw new IllegalArgumentException("write a message first");
        }
        if (text.length() > Temporal.MAX_TEXT) {
            throw new IllegalArgumentException("keep it under " + Temporal.MAX_TEXT + " characters");
        }
        return Json.o("fingerprint", Temporal.fingerprint(opens, salt, text), "opens", Long.valueOf(opens), "salt", salt, "text", text);
    }

    public static String code(Map<String, Object> c) {
        String json = Json.write(Json.o("v", Long.valueOf(1), "opens", c.get("opens"), "salt", c.get("salt"), "text", c.get("text")));
        return "temporal1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(Bytes.utf8(json));
    }

    /** Reads a capsule code, or the JSON of a capsule file from the web page. */
    public static Map<String, Object> decode(String code) {
        String s = code == null ? "" : code.trim();
        Map<String, Object> m;
        try {
            if (s.startsWith("temporal1:")) {
                m = Json.obj(new String(Base64.getUrlDecoder().decode(s.substring(10)), Bytes.UTF8));
            } else if (s.startsWith("{")) {
                m = Json.obj(s);
            } else {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("that isn't a Temporal capsule code");
        }
        Map<String, Object> c = capsule(Json.num(m, "opens", -1L), Json.str(m, "salt", "").toLowerCase(Locale.ROOT), Json.str(m, "text", ""));
        String claimed = Json.str(m, "fingerprint", "");
        if (!claimed.isEmpty() && !claimed.equals(c.get("fingerprint"))) {
            throw new IllegalArgumentException("this capsule was changed after it was sealed: its fingerprint doesn't match");
        }
        return c;
    }

    public synchronized List<Map<String, Object>> all() {
        return new ArrayList<>(this.capsules);
    }

    public synchronized Map<String, Object> get(String fingerprint) {
        for (Map<String, Object> c : this.capsules) {
            if (fingerprint.equals(c.get("fingerprint"))) {
                return c;
            }
        }
        return null;
    }

    /** Adds it, or merges new details (like the seal's txid) into the copy already here. */
    public synchronized void put(Map<String, Object> capsule) throws IOException {
        Map<String, Object> have = get((String) capsule.get("fingerprint"));
        if (have == null) {
            this.capsules.add(0, capsule);
        } else {
            have.putAll(capsule);
        }
        save();
    }

    public synchronized boolean remove(String fingerprint) throws IOException {
        boolean removed = this.capsules.removeIf(c -> fingerprint.equals(c.get("fingerprint")));
        if (removed) {
            save();
        }
        return removed;
    }

    private void save() throws IOException {
        if (this.file == null) {
            return;
        }
        File tmp = new File(this.file.getPath() + ".tmp");
        Files.write(tmp.toPath(), Bytes.utf8(Json.write(this.capsules)));
        Files.move(tmp.toPath(), this.file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
