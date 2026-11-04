package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The wallpaper library: what is on screen is always a file already on the device.
 *
 * <p>Two folders, both under the app's external files dir:
 * <ul>
 *   <li>{@code mine/} — pictures you pushed yourself. Filenames become captions, and these always sort
 *       first because they were chosen by hand.</li>
 *   <li>{@code wallpapers/} — any earlier locally stored set. Its captions live in {@code index.json}
 *       next to the files, which is read-only here.</li>
 * </ul>
 *
 * <p>Nothing in this class touches the network. Add pictures by copying them into {@code mine/} (see
 * {@code push-wallpapers.sh}); Settings &rarr; Wallpaper &rarr; "Rescan library" picks up files that
 * arrived while the launcher was already running.
 */
public final class Wallpapers {

    public static final class Wallpaper {
        public final File file;
        public final String label;

        Wallpaper(File file, String label) {
            this.file = file;
            this.label = label;
        }
    }

    private Wallpapers() {
    }

    /** Folder for locally stored wallpapers whose captions are kept in {@code index.json}. */
    public static File storeDir(Context ctx) {
        File d = new File(ctx.getExternalFilesDir(null), "wallpapers");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Folder the user drops their own wallpapers into; it is never touched by the app. */
    public static File userDir(Context ctx) {
        File d = new File(ctx.getExternalFilesDir(null), "mine");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Every wallpaper currently on the device: the user's own first, then the stored set. */
    public static List<Wallpaper> cached(Context ctx) {
        JSONObject index = readIndex(ctx);
        List<Wallpaper> out = new ArrayList<>();

        File[] mine = userDir(ctx).listFiles((d, n) -> {
            String l = n.toLowerCase(java.util.Locale.US);
            return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png");
        });
        if (mine != null) {
            List<File> own = new ArrayList<>(Arrays.asList(mine));
            own.sort(Comparator.comparing(File::getName));
            for (File f : own) out.add(new Wallpaper(f, labelFor(f.getName())));
        }

        File[] files = storeDir(ctx).listFiles((d, n) -> n.endsWith(".jpg"));
        if (files == null) return out;
        List<File> list = new ArrayList<>(Arrays.asList(files));
        list.sort(Comparator.comparing(File::getName));
        for (File f : list) out.add(new Wallpaper(f, index.optString(f.getName(), null)));
        return out;
    }

    /** How many wallpapers are on the device right now; cheap, this is a directory listing. */
    public static int count(Context ctx) {
        return cached(ctx).size();
    }

    /** "the-dark-knight_2008.jpg" -> "THE DARK KNIGHT 2008" */
    private static String labelFor(String name) {
        String base = name.replaceAll("\\.[A-Za-z0-9]+$", "").replace('_', ' ').replace('-', ' ');
        base = base.replaceAll("\\s+", " ").trim();
        return base.isEmpty() ? null : base.toUpperCase(java.util.Locale.US);
    }

    private static JSONObject readIndex(Context ctx) {
        File f = new File(storeDir(ctx), "index.json");
        if (!f.exists()) return new JSONObject();
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return new JSONObject(sb.toString());
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
