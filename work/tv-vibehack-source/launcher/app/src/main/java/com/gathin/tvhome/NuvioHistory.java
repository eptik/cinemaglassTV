package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * What Nuvio played recently.
 *
 * Nuvio keeps its history in private storage: the package is not debuggable and it exports no
 * content provider, so there is nothing to query. The one honest source is the MediaSession it
 * publishes while a title plays (PlayerMediaSessionMetadata.kt) — title, "S1:E2 – Episode" or the
 * release year, a poster URL and whether it is a movie or a show. We keep that, and resolve the
 * IMDb id through Cinemeta so a tile can deep-link straight back into Nuvio.
 */
final class NuvioHistory {

    static final class Item {
        final String title;
        final String subtitle;
        final String poster;
        final String type;      // "movie" or "series"
        final String id;        // IMDb id, empty until Cinemeta answers

        Item(String title, String subtitle, String poster, String type, String id) {
            this.title = title;
            this.subtitle = subtitle;
            this.poster = poster;
            this.type = type;
            this.id = id;
        }
    }

    private static final String TAG = "NuvioHistory";
    private static final String PREFS = "nuvio_history";
    private static final String KEY = "items";
    private static final int MAX = 10;
    private static final String CINEMETA = "https://v3-cinemeta.strem.io/catalog/%s/top/search=%s.json";

    private NuvioHistory() {
    }

    static List<Item> load(Context ctx) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(ctx).getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Item(o.optString("title"), o.optString("subtitle"), o.optString("poster"),
                        o.optString("type", "movie"), o.optString("id")));
            }
        } catch (Exception e) {
            Log.w(TAG, "history unreadable", e);
        }
        return out;
    }

    /** Called from the media-session poll. Repeat calls for the same title are free. */
    static void observe(Context ctx, CharSequence rawTitle, CharSequence rawSubtitle,
                        String poster, boolean series) {
        if (rawTitle == null) return;
        final String title = rawTitle.toString().trim();
        if (title.isEmpty()) return;
        List<Item> items = load(ctx);
        if (!items.isEmpty() && title.equals(items.get(0).title)) return;   // still the same title

        final Context app = ctx.getApplicationContext();
        final String subtitle = rawSubtitle == null ? "" : rawSubtitle.toString().trim();
        final String type = series ? "series" : "movie";
        items.removeIf(item -> title.equals(item.title));
        items.add(0, new Item(title, subtitle, poster == null ? "" : poster, type, ""));
        while (items.size() > MAX) items.remove(items.size() - 1);
        // Persist now so the row can render; the id arrives later and is merged in by idFor().
        save(app, items);

        new Thread(() -> {
            String id = resolveId(title, type, subtitle);
            if (id.isEmpty()) return;
            List<Item> current = load(app);
            for (int i = 0; i < current.size(); i++) {
                Item it = current.get(i);
                if (title.equals(it.title) && it.id.isEmpty()) {
                    current.set(i, new Item(it.title, it.subtitle, it.poster, it.type, id));
                }
            }
            save(app, current);
        }, "nuvio-id").start();
    }

    /** Title -> IMDb id. Cinemeta is the same free catalogue Stremio and Nuvio both read. */
    private static String resolveId(String title, String type, String subtitle) {
        try {
            String year = subtitle.length() == 4 && Character.isDigit(subtitle.charAt(0)) ? subtitle : "";
            String url = String.format(CINEMETA, type, URLEncoder.encode(title, "UTF-8"));
            JSONArray metas = new JSONObject(get(url)).optJSONArray("metas");
            if (metas == null || metas.length() == 0) return "";
            String fallback = "";
            for (int i = 0; i < metas.length(); i++) {
                JSONObject m = metas.getJSONObject(i);
                String id = m.optString("id");
                if (!id.startsWith("tt")) continue;
                if (fallback.isEmpty()) fallback = id;
                if (!year.isEmpty() && year.equals(m.optString("releaseInfo"))) return id;
            }
            return fallback;
        } catch (Exception e) {
            Log.w(TAG, "Cinemeta lookup failed for " + title, e);
            return "";
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(4000);
        c.setReadTimeout(5000);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    private static void save(Context ctx, List<Item> items) {
        JSONArray arr = new JSONArray();
        try {
            for (Item it : items) {
                arr.put(new JSONObject()
                        .put("title", it.title)
                        .put("subtitle", it.subtitle)
                        .put("poster", it.poster)
                        .put("type", it.type)
                        .put("id", it.id));
            }
        } catch (Exception ignored) {
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
