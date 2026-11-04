# Sony KD-55AF8 (BRAVIA 4K GB ATV3, Android 8.0 / API 26) — debloat + Apple-TV-style launcher

Everything here was done on the real TV at `192.168.1.6` over ADB and verified with screenshots pulled
from the device. Backups of the original state are in `backups/2026-10-20-initial/`.

## What is running now

- Custom launcher `com.gathin.tvhome` is the **default home screen**; the stock Google TV launcher is
  disabled. Dock order: **Stremio → Nuvio → YouTube → Spotify** (from `res/values/config.xml`).
- **TMDB wallpapers**: the launcher pulls backdrops for *now playing* (current box office) and *top-rated
  TV* from TMDB, interleaved so both are always represented, caches them as 1920-wide JPEGs on the TV, and
  rotates on a schedule you pick (1:30 / 30 min / paused, in the Settings pill). The title and year of the
  backdrop on screen sit discreetly under the clock.
  Refresh happens at most once every 12 h, on the background thread, only while the launcher is resumed.
  No network, no images? It stays black — nothing blocks the UI.
- A glass "now playing" island at the top centre: album art, title, artist, thin progress bar. Focus it
  and it grows to 1.06x and reveals **previous / play-pause / next** (play/pause drives the session
  directly). It stays while a track is paused and fades out ~1.5 s after the session ends, so a track
  change never blinks it away. Artwork is cached per track: play/pause/skip no longer re-decodes or
  swaps the image.
- Home typography: greeting, then the **date above the clock**, all left-aligned, with a soft text shadow
  so the block stays legible.
- Cinematic grade (`CinemaView`): letterbox shading top and bottom, a corner vignette and a very light
  warm tint, drawn once as a static overlay. No blur, no animation — it just makes a photo read like a
  film still behind the UI. The dock band is warmed slightly to match.
- Thirteen Sony/Google packages disabled (telemetry, retail demo, support/help, recommendations, stock
  launcher). Full list: `work/disabled-packages.txt`.

## Files

| Path | What it is |
|---|---|
| `restore.sh` | Re-enables every disabled package, puts the stock launcher back, reverts settings. |
| `build-and-install.sh` | Builds the launcher and installs/sets it as home. |
| `work/tv_adb.py` | The ADB client used throughout (Python `adb-shell`, because macOS Local Network privacy breaks the stock `adb` binary). |
| `work/disabled-packages.txt` | The exact packages this project disabled. |
| `backups/2026-10-20-initial/` | getprop, package lists, settings, and screenshots of the original state. |
| `work/tv-vibehack-source/launcher/` | Launcher source (fork of `benfirad/tv-vibehack`, MIT). |
| `outputs/app-debug.apk` | The installed build. |

## Talking to the TV again

The TV's ADB is on port 5555 and needs Developer options → USB debugging (and Network debugging) on.

```bash
work/adb-client/bin/python work/tv_adb.py 192.168.1.6 -- 'pm list packages | head'
work/adb-client/bin/python work/tv_adb.py --push local.apk /data/local/tmp/x.apk 192.168.1.6
work/adb-client/bin/python work/tv_adb.py --base64-out shot.png 192.168.1.6 \
  'screencap -p /sdcard/s.png && base64 /sdcard/s.png && rm /sdcard/s.png'
```

Rules that saved time here: **one `pm` command per shell call** (chains silently stop), pushes go as
base64 chunks with an MD5 check, and `pm install` needs a 60 s transport timeout.

## Re-enabling an app

```bash
work/adb-client/bin/python work/tv_adb.py 192.168.1.6 -- 'pm enable --user 0 com.sony.dtv.sonyselect'
```

Or run `./restore.sh` to bring everything back at once (`./restore.sh --uninstall-launcher` also removes
the custom launcher). Nothing was uninstalled — every change is a `pm disable-user`, so all of it is
reversible without a factory reset.

## Changing the dock order

- Dock: edit `work/tv-vibehack-source/launcher/app/src/main/res/values/config.xml` (`dock_apps`
  array, package names), then `TV_HOST=192.168.1.6 ./build-and-install.sh`.


## The now-playing island

Android only exposes media sessions to notification listeners, so the launcher ships a tiny
`NowPlayingService`. It must be enabled once (the build script does this):

```bash
settings put secure enabled_notification_listeners <existing>:com.gathin.tvhome/com.gathin.tvhome.NowPlayingService
```

D-pad map:
- **Up** from a dock tile → the island when something is playing (it expands with play/pause focused),
  otherwise the top-right Settings pill.
- **Left** from the Inputs pill → island · **Left/Right** inside the island → previous / play-pause / next
  · **Right** past next → Inputs pill · **Down** → back to the dock, remembering the last tile.
- **Up** from a shelf row → one row up; the top bar's ends stay put instead of falling into the grid.
Play/pause and skip drive Spotify through `MediaController`; they work on any app that publishes a
session, not just Spotify.

## Wallpapers

The library lives in `files/mine/` on the TV and comes from **Cinemeta** — the catalogue addon Stremio
itself uses. Its `background` field points at `images.metahub.space`, which serves the official
landscape backdrop per title: no fan art, no portrait compositions, no burned-in title treatments.
That is literally the artwork Stremio shows behind a film.

- Rebuild: `work/adb-client/bin/python work/seed_cinemeta.py work/cinemeta`
  then `bash work/push_all_walls.sh work/cinemeta` (70 titles, cinema + series).
- Entries are 1920x1080 — Stremio's own size, and exactly panel resolution, so nothing is upscaled.
  A couple of newer titles come through at 3840x2160.
- Titles that Cinemeta has no backdrop for are skipped rather than filled with fan art.

**The TV also grows the library by itself.** `TmdbWallpapers.fetch()` runs at most once a week (checked on
every resume and on the 15 s tick, self-throttled): it reads two TMDB discovery shelves on a page that
rotates with the week number, adds at most 8 new titles per run, and applies the same gate as the
desktop seeder — 16:9, >= 3840 px, textless, >= 400 KB after compression, dark enough for white type.
Animation and horror are excluded. Your `mine/` files are never touched or evicted.

Earlier sources, kept for the record: TMDB (removed — ratings don't predict composition), Wallhaven
(fan-art heavy, produced pillarboxed portrait art), 4kwallpapers.com (Cloudflare-walled, harvest-only).

Add your own: `./push-wallpapers.sh ~/Downloads/my-wallpapers` (native size, filename becomes the
caption). Delete a file from `files/mine/` to drop it. Rotation timing: Settings -> Wallpaper.

## OLED care

Every static element — clock, date, greeting, caption, pills, now-playing island, tiles, dock — drifts on a
slow Lissajous path: amplitude 3 dp (~6 px on this panel), one sweep every 8 minutes, re-evaluated every
3 s and snapped to whole pixels. It is `setTranslationX/Y` only, so there is no layout pass and no redraw of
the wallpaper; all views share one offset, which keeps focus geometry and the glass highlight aligned. The
wallpaper itself is excluded (it changes every 90 s anyway) so no black edges can appear.

## Settings overlay focus

The panel is a sibling of the grid, so drawing it on top is not enough: the focus finder will happily walk into
the tiles behind it. `setBackgroundFocusable(false)` blocks descendants of the grid, the three top pills and the
now-playing island while a panel is open, and restores them on close.

## Performance: what actually cost frames

Two things made the TV feel slow, both measured with `dumpsys gfxinfo` while driving the D-pad:

| change | median frame | janky |
|---|---|---|
| TextureView video wallpaper | 61 ms | 99.96 % |
| video removed, ambient glow on | 26 ms | 97.3 % |
| **ambient glow removed (current)** | **9 ms** | **~16 %** |

The "ambient glow" was a full-screen radial gradient in the colour of the focused app: every focus move
recoloured and repainted the entire surface. It is gone, along with the custom wallpaper. The dock band and
the tile scale now carry the focus indication.

## Private DNS / ad blocking — not possible on this TV

The TV runs **Android 8.0**, and Private DNS (DoT) only exists from Android 9. Setting
`private_dns_specifier` on API 26 stores the key but nothing reads it — DNS keeps going to the router
(`192.168.1.254`), so `syy97khz.dot.ublockdns.com` and the uBlock DoH URL cannot be used here. The
setting was reverted to `private_dns_mode=opportunistic`.

Working options, in order of effort:

1. **Router-level**: set the Bbox's DHCP DNS to a filtering resolver (AdGuard `94.140.14.14`, or your
   uBlock DoH endpoint if the router speaks DoH). Covers every device, nothing to install.
2. **On-TV DNS client app**: Nebulo / DNS66 / personalDNSfilter (sideload an APK) run a local VPN and
   speak DoT/DoH — this is the only way to use your uBlock hostname on this firmware.
3. **Pi-hole** on the LAN, then point the router at it.

Either way: **DNS blocking cannot remove YouTube ads.** They are served from the same domains as the
video, so there is nothing to block without breaking playback. Expect in-app banners and trackers to go
away, not YouTube pre-rolls.

## Security — switch ADB off when you are done

Port 5555 is plaintext and this firmware accepts an RSA-authorised shell from anything on the LAN. To
close it:

1. TV: Settings → Device preferences → About → build/version 7× (if needed) → Developer options →
   **USB debugging off** and **Network debugging / ADB over network off**.
2. **Revoke USB debugging authorisations** in the same menu.
3. Reboot, then on the Mac:

```bash
nc -vz 192.168.1.6 5555    # must fail
```

Everything in this project keeps working with ADB off; you only need it again to rebuild/install.

## Known limitations

- Android 8's screenshot API returns a black frame on the Live TV / HDMI inputs — check
  `dumpsys activity activities | grep mResumedActivity` instead. The launcher itself screenshots fine.
- The TV is on **Ethernet** (the Wi-Fi pill shows "Ethernet" because that is the active default
  network); Wi-Fi is still associated to `Bbox-58F89AD7` as a fallback.
- The launcher is a debug build signed with the debug key in
  `work/tv-vibehack-source/launcher/work/android-user-home/debug.keystore`. Keep that file, or future
  builds will be refused with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
- RAM is ~1 GB: the island decodes album art once per track change and the ticker only runs while it is
  visible.
