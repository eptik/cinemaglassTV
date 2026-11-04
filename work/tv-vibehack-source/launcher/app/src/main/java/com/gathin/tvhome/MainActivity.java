package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LayoutAnimationController;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private GlassView glass;
    private TextView heroName;
    private boolean heroShown;
    private TextView greeting;
    private TextView btnWifi;
    private FrameLayout npHost;
    private FrameLayout npRoot;
    private FrameLayout npBar;
    private ImageView wallpaper;
    private TextView wpCaption;
    private int wallpaperIndex = -1;
    private long lastWallpaperSwitchAt = 0L;
    private final ExecutorService wallpaperLoader = Executors.newSingleThreadExecutor();
    private DiscArtView npArt;
    private TextView npTitle, npArtist;
    private NowPlayingPill npPill;
    private LinearLayout npControls;
    private ImageView npPrev, npToggle, npNext;
    private boolean npVisible;
    private boolean npExpanded;
    private Bitmap lastArtShown;
    private boolean isDockPeeking = false;
    private ValueAnimator dockPeekAnimator;
    private float peekOffset;
    private String peekDepthPref = "ultra";
    private boolean isTopBarShowText = true;
    private View settingsOverlay;
    private TextView btnWallFast, btnWallSlow, btnWallOff;
    private TextView btnPeekUltra, btnPeekBalanced, btnPeekNone;
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    private float lastShiftX, lastShiftY;
    private int sleepMinutes = 0;
    private boolean glassShown;
    private int lastFocused = 0;
    private float density;

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Wallpaper rotation needs finer granularity than the minute tick (default interval is 90 s). */
    private final Runnable wallpaperTick = new Runnable() {
        @Override
        public void run() {
            maybeRotateWallpaper();
            handler.postDelayed(this, 15_000L);
        }
    };

    /**
     * OLED care: every static element (clock, date, pills, tiles, dock) drifts on a slow Lissajous path.
     * It is a translation only — no layout, no redraw of the bitmap — and every view moves by the same
     * offset, so focus geometry and the glass highlight stay aligned. Amplitude is a few pixels, which is
     * invisible up close but stops one set of subpixels from ageing faster than its neighbours.
     */
    private final Runnable pixelShiftTick = new Runnable() {
        @Override
        public void run() {
            applyPixelShift();
            handler.postDelayed(this, 3000L);
        }
    };

    private boolean pixelShiftEnabled() {
        return getSharedPreferences("tvhome_prefs", MODE_PRIVATE).getBoolean("pixel_shift", true);
    }

    private void applyPixelShift() {
        if (!pixelShiftEnabled()) return;
        View rootView = findViewById(R.id.root);
        if (!(rootView instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup rootGroup = (android.view.ViewGroup) rootView;
        long t = android.os.SystemClock.elapsedRealtime();
        // 20 minutes for a full sweep: at this speed a whole pixel takes several seconds, so the eye sees
        // drift rather than steps. Values stay fractional on purpose — rounding was what made it jump.
        // Figure-eight: horizontal at f, vertical at 2f. A 1:2 Lissajous closes on itself, so the eye
        // follows a loop it can almost predict — that is what makes it hypnotic rather than random.
        double period = 16 * 60_000d;
        float amp = 2f * density;                       // ~4 px horizontally, safe inside the dock margins
        float ampY = 1f * density;                      // ~2 px vertically
        float dx = (float) (amp * Math.sin(2 * Math.PI * t / period));
        float dy = (float) (ampY * Math.sin(4 * Math.PI * t / period + 1.0));
        for (int i = 0; i < rootGroup.getChildCount(); i++) {
            View child = rootGroup.getChildAt(i);
            // The glass highlight is *positioned* with translationX/Y (spring to the focused tile), so
            // shifting it here would teleport it to the corner and let the spring drag it back.
            if (child.getId() == R.id.wallpaper || child.getId() == R.id.scrim
                    || child.getId() == R.id.glass || child.getId() == R.id.settingsOverlay) continue;
            // The tile layer is pinned: the focus ring lives in root coordinates and is animated by a
            // spring, so drifting the grid underneath it would leave the ring permanently offset.
            if (child.getId() == R.id.contentLayer) {
                child.setTranslationX(0f);
                child.setTranslationY(0f);
                continue;
            }
            child.setTranslationX(dx);
            child.setTranslationY(dy);
        }
    }

    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateWifiLabel();
            maybeRotateWallpaper();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        COLUMNS = Math.max(1, getResources().getInteger(R.integer.columns));
        liveTvPkg = getString(R.string.live_tv_package).trim();

        SharedPreferences prefs = getSharedPreferences("tvhome_prefs", MODE_PRIVATE);
        peekDepthPref = prefs.getString("dock_peek_depth", "ultra");
        updatePeekOffset();

        root = findViewById(R.id.root);
        glass = findViewById(R.id.glass);
        heroName = findViewById(R.id.heroName);
        greeting = findViewById(R.id.greeting);
        btnWifi = findViewById(R.id.btnWifi);
        npBar = findViewById(R.id.npBar);
        wallpaper = findViewById(R.id.wallpaper);
        wpCaption = findViewById(R.id.wpCaption);
        npHost = findViewById(R.id.npHost);
        npRoot = findViewById(R.id.npRoot);
        npArt = findViewById(R.id.npArt);
        npTitle = findViewById(R.id.npTitle);
        npArtist = findViewById(R.id.npArtist);
        npPill = findViewById(R.id.npRoot);
        npControls = findViewById(R.id.npControls);
        npPrev = findViewById(R.id.npPrev);
        npToggle = findViewById(R.id.npToggle);
        npNext = findViewById(R.id.npNext);
        npHost.setFocusable(true);
        npHost.setFocusableInTouchMode(false);
        // Keep the island clear of the clock/date block on the left and the pills on the right.
        final View leftBlock = findViewById(R.id.leftBlock);
        final Runnable placeIsland = () -> {
            if (npBar == null || leftBlock == null) return;
            int barWidth = npBar.getWidth();
            View pills = findViewById(R.id.btnSettings);
            if (barWidth <= 0 || pills == null) return;
            // Coordinates in the root's frame: getLeft() is parent-relative and cannot be compared across parents.
            Rect leftRect = new Rect(0, 0, leftBlock.getWidth(), leftBlock.getHeight());
            root.offsetDescendantRectToMyCoords(leftBlock, leftRect);
            Rect pillRect = new Rect(0, 0, pills.getWidth(), pills.getHeight());
            root.offsetDescendantRectToMyCoords(pills, pillRect);

            int gap = (int) (18 * density);
            // Symmetric padding: the pill must sit on the panel's centre line, not the midpoint of whatever
            // gap happens to be left between the clock block and the settings pill.
            int need = Math.max(leftRect.right + gap, barWidth - (pillRect.left - gap));
            int freeWidth = barWidth - 2 * need;
            int leftPad = need;
            int rightPad = need;
            if (need < 0 || freeWidth < (int) (360 * density)) {
                leftPad = 0;              // no room between clock and pill: use the whole width
                rightPad = 0;
            }
            if (npBar.getPaddingLeft() != leftPad || npBar.getPaddingRight() != rightPad) {
                npBar.setPadding(leftPad, npBar.getPaddingTop(), rightPad, npBar.getPaddingBottom());
                // Machine-readable geometry for the centring gate (uiautomator cannot settle while the
                // drift tick keeps the window busy).
                int[] hostLoc = new int[2];
                npHost.getLocationOnScreen(hostLoc);
                android.util.Log.d("IslandGeom", "bar=" + barWidth + " left=" + leftPad + " right=" + rightPad
                        + " host=" + hostLoc[0] + " hostw=" + npHost.getWidth());
            }
        };
        npRoot.post(placeIsland);
        leftBlock.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or2, ob) -> placeIsland.run());
        npBar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or2, ob) -> placeIsland.run());
        // The pill's width changes when a new track arrives; re-centre once it has settled.
        npHost.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or2, ob) -> placeIsland.run());
        View calendarPill = findViewById(R.id.btnSettings);
        if (calendarPill != null) {
            calendarPill.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or2, ob) -> placeIsland.run());
        }

        npHost.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                expandIsland();
                npHost.post(() -> {
                    if (npHost.hasFocus() && npToggle != null) npToggle.requestFocus();
                });
            }
        });
        npPrev.setOnClickListener(v -> NowPlaying.skipPrevious());
        npToggle.setOnClickListener(v -> NowPlaying.togglePlayPause());
        npNext.setOnClickListener(v -> NowPlaying.skipNext());
        npPrev.setOnKeyListener(npKey(0));
        npToggle.setOnKeyListener(npKey(1));
        npNext.setOnKeyListener(npKey(2));

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        grid.addItemDecoration(new DockDecoration(COLUMNS, density));
        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));
        adapter = new AppAdapter(this, this, COLUMNS);
        grid.setAdapter(adapter);
        renderNuvioRow();

        // tvOS layout: 2-row dock sits at the bottom, giving maximum space for the wallpaper above.
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            int want = Math.max(0, h - (int) ((225 + 24) * density));
            if (grid.getPaddingTop() != want) {
                grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), grid.getPaddingBottom());
            }
        });

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!glassShown) return;
                glassTargetY -= dy;
                if (springY.isRunning()) springY.animateToFinalPosition(glassTargetY);
                else glass.setTranslationY(glassTargetY);
            }
        });

        // Liquid feel: smooth, stable, non-bouncy springs on highlight position.
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_Y);
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            s.setSpring(new SpringForce().setStiffness(360f).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
        }

        View btnCalendar = findViewById(R.id.btnSettings);
        View btnSettings = findViewById(R.id.btnSettings);
        btnCalendar.setOnClickListener(v -> showInputs());
        btnWifi.setOnClickListener(v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        btnSettings.setOnClickListener(v -> showSettings());
        isTopBarShowText = false;
        applyTopBarStyle(false);            // icon-only pills: Inputs / connection / Settings

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v.getId() == R.id.btnSettings) {
                if (npVisible) {
                    expandIsland();
                    npToggle.requestFocus();
                    return true;
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnSettings) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                setDockPeek(false, true);
                int target = (lastFocused >= 0 && lastFocused < COLUMNS) ? lastFocused : 0;
                focusTile(target);
                return true;
            }
            return false;
        };
        btnCalendar.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);

        // Hide the glass/hero and peek the dock when focus leaves the tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean inIsland = newF != null && isDescendant(npHost, newF);
            if (!inIsland && npExpanded) collapseIsland();
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) {
                hideGlass();
                hideHero();
                setDockPeek(true, true);
            } else {
                setDockPeek(false, true);
            }
        });
    }

    @Override
    public void onInputsTileClicked() {
        showInputs();
    }

    private boolean isInitialLaunch = true;

    @Override
    protected void onResume() {
        super.onResume();
        glassShown = false;
        glass.setAlpha(0f);
        heroShown = false;
        heroName.setAlpha(0f);

        if (adapter.getItemCount() == 0) {
            adapter.reload();
            grid.scheduleLayoutAnimation();
        }

        updateGreeting();
        updateWifiLabel();

        handler.removeCallbacks(minuteTick);
        handler.postDelayed(minuteTick, MINUTE_MS);

        NowPlaying.addListener(npListener);
        NowPlaying.refresh(getApplicationContext());
        renderNuvioRow();
        handler.postDelayed(this::renderNuvioRow, 2200L);   // after the poll has seen Nuvio's session
        // The listener service can bind a moment after the activity resumes (e.g. right after an install),
        // so ask again shortly instead of showing an empty header for the rest of the session.
        handler.postDelayed(() -> NowPlaying.refresh(getApplicationContext()), 1500L);
        handler.postDelayed(() -> NowPlaying.refresh(getApplicationContext()), 5000L);

        if (wallpaperIndex < 0) rotateWallpaper(true);
        else maybeRotateWallpaper();
        handler.removeCallbacks(wallpaperTick);
        handler.postDelayed(wallpaperTick, 15_000L);
        handler.removeCallbacks(pixelShiftTick);
        handler.post(pixelShiftTick);

        // Only move focus to top bar and set peek mode on initial cold launch
        if (isInitialLaunch) {
            isInitialLaunch = false;
            grid.post(() -> {
                View btn = findViewById(R.id.btnSettings);
                if (btn != null) btn.requestFocus();
                setDockPeek(true, false);
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(minuteTick);
        handler.removeCallbacks(wallpaperTick);
        handler.removeCallbacks(npTicker);
        NowPlaying.removeListener(npListener);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (isPurpleOrMediaKey(code)) {
                launchStremio();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean isPurpleOrMediaKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_DVR:
            case KeyEvent.KEYCODE_GUIDE:
            case KeyEvent.KEYCODE_BUTTON_1:
            case KeyEvent.KEYCODE_BUTTON_2:
            case KeyEvent.KEYCODE_BUTTON_3:
            case KeyEvent.KEYCODE_BUTTON_4:
            case KeyEvent.KEYCODE_PROG_RED:
            case KeyEvent.KEYCODE_PROG_GREEN:
            case KeyEvent.KEYCODE_PROG_YELLOW:
            case KeyEvent.KEYCODE_PROG_BLUE:
            case KeyEvent.KEYCODE_TV_MEDIA_CONTEXT_MENU:
            case KeyEvent.KEYCODE_LAST_CHANNEL:
            case 284:
            case 285:
            case 286:
            case 287:
                return true;
            default:
                return false;
        }
    }

    private void launchStremio() {
        try {
            Intent i = getPackageManager().getLeanbackLaunchIntentForPackage("com.stremio.one");
            if (i == null) i = getPackageManager().getLaunchIntentForPackage("com.stremio.one");
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(i);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() {
        if (settingsOverlay != null && settingsOverlay.getVisibility() == View.VISIBLE) {
            hideSettings();
            return;
        }
        if (calendarOverlay != null && calendarOverlay.getVisibility() == View.VISIBLE) {
            hideCalendar();
            return;
        }
        View focused = getCurrentFocus();
        if (focused != null && focused.getId() == R.id.card) {
            if (lastFocused >= COLUMNS * 2) {
                // Return from apps grid to the dock
                focusTile(0);
            } else {
                // Return from dock to top bar (peek mode, full photo view)
                View btn = findViewById(R.id.btnSettings);
                if (btn != null) btn.requestFocus();
            }
        }
    }

    // ---- Calendar Overlay ------------------------------------------------------------------------

    private View calendarOverlay;
    private TextView tvCalMonthYear, tvCalSubtitle;
    private TableLayout calGridTable;
    private int calYear = 2026, calMonth = Calendar.OCTOBER;

    private static final class CalEvent {
        final String badge;
        final String title;
        final int color;
        CalEvent(String badge, String title, int color) {
            this.badge = badge;
            this.title = title;
            this.color = color;
        }
    }
    private static final Map<Integer, CalEvent> OCT_EVENTS = new HashMap<>();

    private void showCalendar() {
        if (calendarOverlay == null) {
            calendarOverlay = findViewById(R.id.calendarOverlay);
            tvCalMonthYear = findViewById(R.id.tvCalMonthYear);
            tvCalSubtitle = findViewById(R.id.tvCalSubtitle);
            calGridTable = findViewById(R.id.calGridTable);
            findViewById(R.id.btnCalClose).setOnClickListener(v -> hideCalendar());
            findViewById(R.id.btnCalPrev).setOnClickListener(v -> {
                calMonth--;
                if (calMonth < 0) { calMonth = 11; calYear--; }
                renderCalendar();
                v.requestFocus();
            });
            findViewById(R.id.btnCalNext).setOnClickListener(v -> {
                calMonth++;
                if (calMonth > 11) { calMonth = 0; calYear++; }
                renderCalendar();
                v.requestFocus();
            });
            findViewById(R.id.btnCalToday).setOnClickListener(v -> {
                Calendar c = Calendar.getInstance();
                calYear = c.get(Calendar.YEAR);
                calMonth = c.get(Calendar.MONTH);
                renderCalendar();
                v.requestFocus();
            });
        }
        grid.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        ((ViewGroup) findViewById(R.id.topButtons)).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        Calendar c = Calendar.getInstance();
        calYear = c.get(Calendar.YEAR);
        calMonth = c.get(Calendar.MONTH);
        renderCalendar();
        calendarOverlay.setAlpha(0f);
        calendarOverlay.setVisibility(View.VISIBLE);
        calendarOverlay.animate().alpha(1f).setDuration(220).start();
    }

    private void hideCalendar() {
        if (calendarOverlay == null || calendarOverlay.getVisibility() != View.VISIBLE) return;
        calendarOverlay.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            calendarOverlay.setVisibility(View.GONE);
            grid.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            ((ViewGroup) findViewById(R.id.topButtons)).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            View btn = findViewById(R.id.btnSettings);
            if (btn != null) btn.requestFocus();
        }).start();
    }

    private void renderCalendar() {
        Calendar cal = Calendar.getInstance();
        int todayYear = cal.get(Calendar.YEAR);
        int todayMonth = cal.get(Calendar.MONTH);
        int todayDay = cal.get(Calendar.DAY_OF_MONTH);

        cal.set(Calendar.YEAR, calYear);
        cal.set(Calendar.MONTH, calMonth);
        cal.set(Calendar.DAY_OF_MONTH, 1);

        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        tvCalMonthYear.setText(sdf.format(cal.getTime()));
        tvCalSubtitle.setText(todayDay + " " + new SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.getTime()).toUpperCase(Locale.getDefault()) + " — No scheduled events");

        calGridTable.removeAllViews();

        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1; // 0=Sunday
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        Calendar prevCal = (Calendar) cal.clone();
        prevCal.add(Calendar.MONTH, -1);
        int daysInPrevMonth = prevCal.getActualMaximum(Calendar.DAY_OF_MONTH);

        int dayCounter = 1;
        int nextMonthDayCounter = 1;
        View viewToFocus = null;

        for (int row = 0; row < 5; row++) {
            TableRow tr = new TableRow(this);
            TableLayout.LayoutParams trLp = new TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            tr.setLayoutParams(trLp);

            for (int col = 0; col < 7; col++) {
                View cell = getLayoutInflater().inflate(R.layout.item_cal_day, tr, false);
                TextView tvDayNum = cell.findViewById(R.id.tvDayNum);
                View dotToday = cell.findViewById(R.id.dotToday);
                View eventBadge = cell.findViewById(R.id.eventBadge);
                View eventDot = cell.findViewById(R.id.eventDot);
                TextView eventText = cell.findViewById(R.id.eventText);

                TableRow.LayoutParams cellLp = new TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT, 1f);
                cell.setLayoutParams(cellLp);

                int cellIndex = row * 7 + col;
                if (cellIndex < firstDayOfWeek) {
                    int pDay = daysInPrevMonth - (firstDayOfWeek - 1 - cellIndex);
                    tvDayNum.setText(String.valueOf(pDay));
                    tvDayNum.setTextColor(0x38FFFFFF);
                    cell.setAlpha(0.35f);
                    cell.setFocusable(false);
                    cell.setClickable(false);
                } else if (dayCounter <= daysInMonth) {
                    final int d = dayCounter;
                    tvDayNum.setText(String.valueOf(d));
                    cell.setFocusable(true);
                    cell.setClickable(true);
                    if (row == 0) {
                        cell.setNextFocusUpId(R.id.btnCalToday);
                    }
                    boolean isToday = (calYear == todayYear && calMonth == todayMonth && d == todayDay);
                    if (isToday) {
                        dotToday.setVisibility(View.VISIBLE);
                        dotToday.setBackgroundColor(0xFF38BDF8);
                        tvDayNum.setTextColor(0xFF38BDF8);
                        viewToFocus = cell;
                    }

                    if (calYear == 2026 && calMonth == Calendar.OCTOBER && OCT_EVENTS.containsKey(d)) {
                        CalEvent ev = OCT_EVENTS.get(d);
                        eventBadge.setVisibility(View.VISIBLE);
                        eventDot.setBackgroundColor(ev.color);
                        eventText.setText(ev.badge);
                        eventText.setTextColor(ev.color);
                    }

                    cell.setOnFocusChangeListener((v, hasFocus) -> {
                        if (hasFocus) {
                            if (calYear == 2026 && calMonth == Calendar.OCTOBER && OCT_EVENTS.containsKey(d)) {
                                tvCalSubtitle.setText(OCT_EVENTS.get(d).title);
                                tvCalSubtitle.setTextColor(OCT_EVENTS.get(d).color);
                            } else {
                                tvCalSubtitle.setText(d + " " + sdf.format(cal.getTime()).toUpperCase(Locale.getDefault()) + " — No scheduled events");
                                tvCalSubtitle.setTextColor(0xFF38BDF8);
                            }
                        }
                    });

                    dayCounter++;
                } else {
                    tvDayNum.setText(String.valueOf(nextMonthDayCounter++));
                    tvDayNum.setTextColor(0x38FFFFFF);
                    cell.setAlpha(0.35f);
                    cell.setFocusable(false);
                    cell.setClickable(false);
                }

                tr.addView(cell);
            }
            calGridTable.addView(tr);
        }

        if (viewToFocus != null) {
            final View f = viewToFocus;
            f.post(f::requestFocus);
        }
    }



    private void setDockPeek(boolean peek, boolean animate) {
        if (isDockPeeking == peek && dockPeekAnimator == null) return;
        isDockPeeking = peek;
        final float targetY = peek ? peekOffset : 0f;
        if (peek) {
            grid.scrollToPosition(0);
        }

        if (dockPeekAnimator != null) {
            dockPeekAnimator.cancel();
            dockPeekAnimator = null;
        }

        if (!animate) {
            grid.setTranslationY(targetY);
            return;
        }

        final float startY = grid.getTranslationY();
        if (Math.abs(startY - targetY) < 1f) return;

        dockPeekAnimator = ValueAnimator.ofFloat(startY, targetY);
        dockPeekAnimator.setDuration(peek ? 240 : 260);
        dockPeekAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
        dockPeekAnimator.addUpdateListener(anim -> {
            grid.setTranslationY((float) anim.getAnimatedValue());
        });
        dockPeekAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                dockPeekAnimator = null;
                if (!isDockPeeking) {
                    RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(lastFocused);
                    if (vh != null) {
                        View card = vh.itemView.findViewById(R.id.card);
                        if (card != null && card.isFocused()) {
                            onTileFocused(lastFocused, card);
                        }
                    }
                }
            }
        });
        dockPeekAnimator.start();
    }

    // ---- glass highlight -------------------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        lastFocused = position;
        // Tile rect in root coordinates, normalized by subtracting current grid translation.
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = 6f * density;        // matches GlassView's glow inset
        float w = card.getWidth() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float h = card.getHeight() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f - grid.getTranslationY();

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
            glass.setRadius(16f * density * AppAdapter.FOCUS_SCALE);   // same corner as the scaled tile
        }
        x = Math.round(x);
        y = Math.round(y);
        glassTargetX = x;
        glassTargetY = y;
        if (!glassShown) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            glass.animate().alpha(1f).setDuration(180).start();
            glassShown = true;
        } else {
            springX.animateToFinalPosition(x);
            springY.animateToFinalPosition(y);
        }
        View item = (View) card.getParent();
        int row = position / COLUMNS;
        if (row == 0) {
            // Row 0 (Favorites Bar): smoothly park grid at top padding
            RecyclerView.ViewHolder vh0 = grid.findViewHolderForAdapterPosition(0);
            int dockTop = (vh0 != null) ? vh0.itemView.getTop() : item.getTop();
            int dy = dockTop - (int) (6 * density) - grid.getPaddingTop();
            if (Math.abs(dy) > 1) grid.smoothScrollBy(0, dy);
        } else {
            // Row 1+ (Stremio shelf and All Apps): smooth, natural scroll without violent jumps
            Rect want = new Rect(0, -(int) (12 * density), item.getWidth(), item.getHeight() + (int) (26 * density));
            grid.requestChildRectangleOnScreen(item, want, false);
        }
        showHero(position, 0f);
        // The dock peek and the grid scroll both animate after focus lands, moving the tile under a ring
        // that was measured too early. Re-measure once they have settled.
        final View focusedCard = card;
        retargetGlass(focusedCard);                 // move on the frame focus lands, not after
        handler.postDelayed(() -> {
            if (focusedCard.hasFocus()) retargetGlass(focusedCard);   // then correct once layout settles
        }, 380L);
    }

    /** Re-measure the ring against a tile that has since moved (dock peek, scroll). */
    private void retargetGlass(View card) {
        if (glass == null || !glassShown) return;
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = 6f * density;
        float w = card.getWidth() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float h = card.getHeight() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float x = Math.round(r.exactCenterX() - w / 2f);
        float y = Math.round(r.exactCenterY() - h / 2f);
        glassTargetX = x;
        glassTargetY = y;
        springX.animateToFinalPosition(x);
        springY.animateToFinalPosition(y);
    }

    // ---- TMDB wallpapers ---------------------------------------------------------------------------

    /** Seconds between wallpapers; 0 pauses rotation. Chosen in the Settings overlay. */
    private long wallpaperRotateMs() {
        int seconds = getSharedPreferences("tvhome_prefs", MODE_PRIVATE).getInt("wallpaper_rotate_sec", 90);
        return seconds <= 0 ? 0L : seconds * 1000L;
    }

    private void maybeRotateWallpaper() {
        if (wallpaperIndex < 0) {
            rotateWallpaper(true);
            return;
        }
        long interval = wallpaperRotateMs();
        if (interval == 0) return;
        if (System.currentTimeMillis() - lastWallpaperSwitchAt < interval) return;
        rotateWallpaper(false);
    }

    private void setWallpaperRotation(int seconds) {
        getSharedPreferences("tvhome_prefs", MODE_PRIVATE).edit()
                .putInt("wallpaper_rotate_sec", seconds).apply();
        lastWallpaperSwitchAt = 0L;
        updateSettingsUI();
        if (seconds > 0) maybeRotateWallpaper();
    }

    /** Long titles would push the transport buttons out of the pill; keep the head and add an ellipsis. */
    private static String clip(String text, int max) {
        if (text == null) return "";
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max).trim() + "\u2026";
    }

    private void showCaption(String label) {
        if (wpCaption == null) return;
        if (label == null || label.isEmpty()) {
            wpCaption.setVisibility(View.GONE);
        } else {
            wpCaption.setText(label);
            wpCaption.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Ken Burns, the cheap way: the new frame starts 4.5% larger and eases to 1.0 across the current
     * rotation interval. Scale only — no re-decode, no extra bitmap, and it never shows an edge because
     * the zoom stays above 1.
     */
    private void settleWallpaperZoom() {
        if (wallpaper == null) return;
        int interval = (int) Math.max(20L, Math.min(120L, wallpaperRotateMs() / 1000L));
        // ObjectAnimators, not ViewPropertyAnimator: .animate().cancel() here used to kill the fade-in
        // that was started a line earlier, leaving the wallpaper at alpha 0 (black screen).
        wallpaper.setScaleX(1.045f);
        wallpaper.setScaleY(1.045f);
        android.animation.ObjectAnimator sx =
                android.animation.ObjectAnimator.ofFloat(wallpaper, "scaleX", 1.045f, 1f);
        android.animation.ObjectAnimator sy =
                android.animation.ObjectAnimator.ofFloat(wallpaper, "scaleY", 1.045f, 1f);
        sx.setDuration(interval * 1000L);
        sy.setDuration(interval * 1000L);
        sx.setInterpolator(new android.view.animation.LinearInterpolator());
        sy.setInterpolator(new android.view.animation.LinearInterpolator());
        sx.start();
        sy.start();
    }

    private void rotateWallpaper(final boolean firstPaint) {
        final java.util.List<Wallpapers.Wallpaper> files = Wallpapers.cached(this);
        if (files.isEmpty() || wallpaper == null) return;
        if (wallpaperIndex < 0) wallpaperIndex = (int) (Math.random() * files.size());
        else wallpaperIndex = (wallpaperIndex + 1) % files.size();
        lastWallpaperSwitchAt = System.currentTimeMillis();
        final Wallpapers.Wallpaper item = files.get(wallpaperIndex);
        final java.io.File file = item.file;
        showCaption(item.label);
        wallpaperLoader.execute(() -> {
            // Files stay at native 4K; decode one step down (>=1920 px) because the panel is 1080p and a full
            // 4K bitmap is 33 MB, which this 1 GB box cannot hold twice during the cross-fade.
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), o);
            // 3840 -> 1920, 7680 -> 1920, 10667 -> 2667: never hold a full 4K+ bitmap on a 1 GB box.
            o.inSampleSize = 1;
            while (o.outWidth / o.inSampleSize > 3000) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            final Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), o);
            if (bmp == null) return;
            runOnUiThread(() -> {
                if (wallpaper == null) return;
                if (firstPaint) {
                    wallpaper.setImageBitmap(bmp);
                    wallpaper.setAlpha(1f);
                    settleWallpaperZoom();
                } else {
                    wallpaper.animate().alpha(0f).setDuration(380).withEndAction(() -> {
                        wallpaper.setImageBitmap(bmp);
                        wallpaper.animate().alpha(1f).setDuration(620).start();
                        settleWallpaperZoom();
                    }).start();
                }
            });
        });
    }

    // ---- top shelf & ambient ---------------------------------------------------------------------

    private void showHero(int position, float dockTopInRoot) {
        if (heroName != null) {
            heroName.setVisibility(View.GONE);
        }
        // No ambient glow: a full-screen radial gradient repaints the whole surface on every focus move and
        // cost ~19 ms a frame on this TV. The dock band and the tile scale carry the focus instead.
    }


    private void hideHero() {
        if (heroName != null) {
            heroName.setVisibility(View.GONE);
        }
        heroShown = false;
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        glass.animate().alpha(0f).setDuration(160).start();
    }

    // ---- navigation ------------------------------------------------------------------------------

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        int count = adapter.getItemCount();
        int target;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < COLUMNS) {
                // Nearest thing up: the now-playing island while music is on, else the Settings pill.
                if (npVisible && npToggle != null) {
                    expandIsland();
                    npToggle.requestFocus();
                } else {
                    View settings = findViewById(R.id.btnSettings);
                    if (settings != null) settings.requestFocus();
                }
                return true;
            }
            target = position - COLUMNS;
        } else {
            int lastRowStart = ((count - 1) / COLUMNS) * COLUMNS;
            if (position >= lastRowStart) return true;     // already on the last row: stay put
            target = Math.min(position + COLUMNS, count - 1);
        }
        focusTile(target);
        return true;
    }

    private void focusTile(int position) {
        if (adapter.getItemCount() == 0) return;
        final int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            vh.itemView.findViewById(R.id.card).requestFocus();
            return;
        }
        // Not laid out yet: scroll there, then focus once the scroll settles.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pos);
                View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
                if (target != null) target.requestFocus();
            }
        });
        grid.smoothScrollToPosition(pos);
    }

    // ---- top bar ---------------------------------------------------------------------------------

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        greeting.setText(res);
    }

    // ---- now playing glass island ------------------------------------------------------------------

    private final NowPlaying.Listener npListener = this::renderNowPlaying;

    private final Runnable npTicker = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            NowPlaying.Track t = NowPlaying.current();
            if (t != null && npToggle != null) {
                npToggle.setImageResource(t.playing ? R.drawable.ic_pause : R.drawable.ic_play);
            }
            if (npVisible) handler.postDelayed(this, 1000L);
        }
    };

    /** Hides the island, but only after a delay, so a session blip does not make it blink. */
    private final Runnable npHide = new Runnable() {
        @Override
        public void run() {
            if (!npVisible) return;
            npVisible = false;
            if (npHost.hasFocus()) {
                View pill = findViewById(R.id.btnSettings);
                if (pill != null) pill.requestFocus();
            }
            npHost.setFocusable(false);
            collapseIsland();
            handler.removeCallbacks(npTicker);
            npHost.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f)
                    .setDuration(180).withEndAction(() -> npHost.setVisibility(View.GONE)).start();
        }
    };

    private void renderNowPlaying(NowPlaying.Track track) {
        if (npHost == null) return;
        boolean show = track != null && track.title != null;
        handler.removeCallbacks(npHide);
        if (!show) {
            if (npVisible) handler.postDelayed(npHide, 1500L);
            return;
        }
        npHost.setFocusable(true);
        npTitle.setText(clip(track.title, 25));
        String artist = track.artist != null ? track.artist : "";
        npArtist.setText(artist);
        npArtist.setVisibility(artist.isEmpty() ? View.GONE : View.VISIBLE);
        npToggle.setImageResource(track.playing ? R.drawable.ic_pause : R.drawable.ic_play);
        if (track.art != null) {
            if (track.art != lastArtShown) {          // only swap when the bitmap really changed
                npArt.setArt(track.art);
                lastArtShown = track.art;
            }
        } else if (lastArtShown != null) {
            npArt.setArt(null);
            lastArtShown = null;
        }
        updateProgress();
        if (!npVisible) {
            npVisible = true;
            npHost.setVisibility(View.VISIBLE);
            npHost.setAlpha(0f);
            npHost.setScaleX(0.92f);
            npHost.setScaleY(0.92f);
            npHost.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240).start();
            handler.removeCallbacks(npTicker);
            handler.postDelayed(npTicker, 1000L);
        }
    }

    private void updateProgress() {
        NowPlaying.Track track = NowPlaying.current();
        if (track == null || npPill == null || track.durationMs <= 0) return;
        npPill.setFraction(NowPlaying.positionMs() / (float) track.durationMs);
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private void updateWifiLabel() {
        if (!isTopBarShowText) {
            if (btnWifi != null) btnWifi.setText("");
            return;
        }
        String text = getString(R.string.wifi);
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm != null ? cm.getActiveNetwork() : null;
            NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
            if (caps == null) {
                text = getString(R.string.offline);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                text = getString(R.string.ethernet);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        if (btnWifi != null) btnWifi.setText(text);
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show();
        }
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private void showInputs() {
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = (int) (240 * density);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase().contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                                TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel")));
            }
        }
        Intent liveTv = liveTvPkg.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTv));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Drop in: fade and unfold from the top edge.
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAtLocation(root, Gravity.CENTER, 0, 0);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This panel reports the same state for every port whether or not anything is plugged in, so a
        // "Connected" badge would be a lie. Only an explicit DISCONNECTED is worth showing.
        if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            status.setVisibility(View.VISIBLE);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }

    // ---- Settings & Top Bar Preferences ----------------------------------------------------------

    private void updatePeekOffset() {
        if ("none".equals(peekDepthPref)) {
            peekOffset = 0f;
        } else if ("balanced".equals(peekDepthPref)) {
            peekOffset = 140f * density;
        } else {
            peekOffset = 220f * density; // default: ultra low peek (wallpaper takes ~95% of screen)
        }
    }

    private void applyTopBarStyle(boolean showText) {
        isTopBarShowText = showText;
        TextView btnCalendar = findViewById(R.id.btnSettings);
        TextView btnSettings = findViewById(R.id.btnSettings);

        int padStart = (int) ((showText ? 16 : 11) * density);
        int padEnd = (int) ((showText ? 20 : 11) * density);
        int drawPad = (int) ((showText ? 9 : 0) * density);

        TextView[] pills = new TextView[]{btnCalendar, btnWifi, btnSettings};
        for (TextView p : pills) {
            if (p != null) {
                p.setPadding(padStart, 0, padEnd, 0);
                p.setCompoundDrawablePadding(drawPad);
            }
        }

        if (btnCalendar != null) btnCalendar.setText(showText ? "Inputs" : "");
        if (btnSettings != null) btnSettings.setText(showText ? getString(R.string.settings) : "");
        updateWifiLabel();
    }

    /**
     * The overlay is a sibling of the grid, so being drawn on top does not stop the focus finder from walking
     * into the tiles behind it. Block the background while a panel is open and restore it on close.
     */
    private void setBackgroundFocusable(boolean enabled) {
        View gridView = findViewById(R.id.grid);
        if (gridView instanceof ViewGroup) {
            ((ViewGroup) gridView).setDescendantFocusability(enabled
                    ? ViewGroup.FOCUS_AFTER_DESCENDANTS
                    : ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        for (int id : new int[]{R.id.btnSettings, R.id.btnWifi, R.id.btnSettings}) {
            View v = findViewById(id);
            if (v != null) v.setFocusable(enabled);
        }
        if (npHost != null) npHost.setFocusable(enabled && npVisible);
    }

    private void showSettings() {
        if (settingsOverlay == null) {
            settingsOverlay = findViewById(R.id.settingsOverlay);
            findViewById(R.id.btnSettingsClose).setOnClickListener(v -> hideSettings());

            btnPeekUltra = findViewById(R.id.btnPeekUltra);
            btnPeekBalanced = findViewById(R.id.btnPeekBalanced);
            btnPeekNone = findViewById(R.id.btnPeekNone);
            btnWallFast = findViewById(R.id.btnWallFast);
            btnWallSlow = findViewById(R.id.btnWallSlow);
            btnWallOff = findViewById(R.id.btnWallOff);

            btnWallFast.setOnClickListener(v -> setWallpaperRotation(90));
            btnWallSlow.setOnClickListener(v -> setWallpaperRotation(300));
            btnWallOff.setOnClickListener(v -> setWallpaperRotation(0));


            bindQuickControls();
            findViewById(R.id.btnDriftOn).setOnClickListener(v -> setPixelShift(true));
            findViewById(R.id.btnDriftOff).setOnClickListener(v -> setPixelShift(false));
            findViewById(R.id.btnWallRefresh).setOnClickListener(v -> rescanWallpapers());

            btnPeekUltra.setOnClickListener(v -> setPeekDepth("ultra"));
            btnPeekBalanced.setOnClickListener(v -> setPeekDepth("balanced"));
            btnPeekNone.setOnClickListener(v -> setPeekDepth("none"));

            findViewById(R.id.btnSysSettings).setOnClickListener(v -> open(new Intent(Settings.ACTION_SETTINGS)));
            findViewById(R.id.btnSysWifi).setOnClickListener(v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
            findViewById(R.id.btnSysApps).setOnClickListener(v -> open(new Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)));
        }

        updateSettingsUI();
        applyNetworkState();
        setBackgroundFocusable(false);
        settingsOverlay.setVisibility(View.VISIBLE);
        settingsOverlay.setAlpha(0f);
        settingsOverlay.animate().alpha(1f).setDuration(220).start();
        findViewById(R.id.btnSettingsClose).requestFocus();
    }

    private void hideSettings() {
        if (settingsOverlay == null || settingsOverlay.getVisibility() != View.VISIBLE) return;
        settingsOverlay.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            settingsOverlay.setVisibility(View.GONE);
            setBackgroundFocusable(true);
            View btn = findViewById(R.id.btnSettings);
            if (btn != null) btn.requestFocus();
        }).start();
    }

    private void updateSettingsUI() {
        if (btnWallFast == null) return;
        int rotateSec = getSharedPreferences("tvhome_prefs", MODE_PRIVATE).getInt("wallpaper_rotate_sec", 90);
        btnWallFast.setSelected(rotateSec == 90);
        btnWallSlow.setSelected(rotateSec == 300);
        btnWallOff.setSelected(rotateSec == 0);

        btnPeekUltra.setSelected("ultra".equals(peekDepthPref));
        btnPeekBalanced.setSelected("balanced".equals(peekDepthPref));
        btnPeekNone.setSelected("none".equals(peekDepthPref));

        TextView sleepTile = findViewById(R.id.btnSleep);
        if (sleepTile != null) {
            sleepTile.setText(sleepMinutes == 0 ? "Sleep off" : "Sleep " + sleepMinutes + "m");
        }

        boolean drift = pixelShiftEnabled();
        View driftOn = findViewById(R.id.btnDriftOn);
        View driftOff = findViewById(R.id.btnDriftOff);
        if (driftOn != null) driftOn.setSelected(drift);
        if (driftOff != null) driftOff.setSelected(!drift);

    }

    /** Recently played in Nuvio, as tiles that deep-link straight back into the title. */
    private void renderNuvioRow() {
        HorizontalScrollView scroll = findViewById(R.id.nuvioScroll);
        LinearLayout row = findViewById(R.id.nuvioRow);
        if (scroll == null || row == null) return;
        java.util.List<NuvioHistory.Item> items = NuvioHistory.load(this);
        if (items.isEmpty()) {
            scroll.setVisibility(View.GONE);
            return;
        }
        scroll.setVisibility(View.VISIBLE);
        row.removeAllViews();
        for (NuvioHistory.Item it : items) row.addView(nuvioTile(it));
    }

    private View nuvioTile(NuvioHistory.Item it) {
        int w = (int) (112 * density);
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd((int) (10 * density));
        tile.setLayoutParams(lp);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(false);
        tile.setBackgroundResource(R.drawable.tile_bg);
        int pad = (int) (4 * density);
        tile.setPadding(pad, pad, pad, pad);
        tile.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                    .setDuration(90).start();          // focus must land on the frame it moves
        });
        tile.setOnClickListener(v -> launchNuvio(it));

        ImageView poster = new ImageView(this);
        poster.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (156 * density)));
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setContentDescription(it.title);
        tile.addView(poster);

        TextView label = new TextView(this);
        label.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        label.setText(it.title);
        label.setTextColor(0xE6FFFFFF);
        label.setTextSize(10);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setPadding(0, (int) (5 * density), 0, 0);
        tile.addView(label);

        String url = !it.poster.isEmpty() ? it.poster
                : (it.id.isEmpty() ? "" : "https://images.metahub.space/poster/medium/" + it.id + "/img.jpg");
        if (!url.isEmpty()) loadPoster(poster, url);
        return tile;
    }

    private void loadPoster(ImageView target, String url) {
        target.setTag(url);
        new Thread(() -> {
            android.graphics.Bitmap bmp = null;
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(5000);
                bmp = android.graphics.BitmapFactory.decodeStream(c.getInputStream());
                c.disconnect();
            } catch (Exception ignored) {
            }
            final android.graphics.Bitmap done = bmp;
            if (done == null) return;
            runOnUiThread(() -> {
                if (url.equals(target.getTag())) target.setImageBitmap(done);
            });
        }, "nuvio-poster").start();
    }

    private void launchNuvio(NuvioHistory.Item it) {
        if (!it.id.isEmpty()) {
            Intent deep = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("nuvio://detail/" + it.type + "/" + it.id));
            deep.setPackage("com.nuvio.tv");
            deep.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(deep);
                return;
            } catch (Exception ignored) {
                // Nuvio may be absent or reject the route; fall through to its home screen.
            }
        }
        Intent fallback = getPackageManager().getLeanbackLaunchIntentForPackage("com.nuvio.tv");
        if (fallback != null) {
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(fallback);
        } else {
            Toast.makeText(this, "Nuvio is not installed", Toast.LENGTH_SHORT).show();
        }
    }

    /** The two quick controls that survived on this hardware: screensaver and next wallpaper. */
    private void bindQuickControls() {
        View screensaver = findViewById(R.id.btnScreensaver);
        if (screensaver != null) {
            screensaver.setOnClickListener(v -> {
                hideSettings();
                try {
                    startActivity(new Intent().setClassName("com.android.systemui",
                            "com.android.systemui.Somnambulator"));
                } catch (Exception e) {
                    try {
                        open(new Intent(Settings.ACTION_DREAM_SETTINGS));
                    } catch (Exception ignored) { }
                }
            });
        }

        View sleep = findViewById(R.id.btnSleep);
        if (sleep != null) {
            sleep.setOnClickListener(v -> {
                cycleSleepTimer();
                updateSettingsUI();
            });
        }

        View nextWall = findViewById(R.id.btnNextWall);
        if (nextWall != null) {
            nextWall.setOnClickListener(v -> {
                hideSettings();
                lastWallpaperSwitchAt = 0L;
                rotateWallpaper(false);
            });
        }
    }

    /**
     * Sleep timer: cycles off / 30 / 60 / 90 minutes and writes it to Android's own screen timeout, which
     * is what puts this TV to standby. That needs WRITE_SETTINGS, so the first tap opens the grant screen.
     */
    private void cycleSleepTimer() {
        int[] options = {0, 30, 60, 90};
        int next = options[(java.util.Arrays.binarySearch(options, sleepMinutes) + 1) % options.length];
        if (java.util.Arrays.binarySearch(options, sleepMinutes) < 0) next = 30;
        SharedPreferences prefs = getSharedPreferences("tvhome_prefs", MODE_PRIVATE);
        if (!prefs.contains("orig_screen_timeout")) {
            prefs.edit().putInt("orig_screen_timeout",
                    Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 1800000)).apply();
        }
        if (!Settings.System.canWrite(this)) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        android.net.Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) { }
            return;
        }
        int ms = next == 0
                ? prefs.getInt("orig_screen_timeout", 1800000)
                : next * 60_000;
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, ms);
        sleepMinutes = next;
    }

    /** macOS habit: the Wi-Fi glyph goes blue when the TV is actually on a wireless network. */
    private void applyNetworkState() {
        TextView wifi = findViewById(R.id.btnSysWifi);
        if (wifi == null) return;
        // Blue means "this TV is on Wi-Fi". On Ethernet the glyph stays neutral — a blue Wi-Fi icon
        // while nothing is wireless would be lying about the connection.
        boolean wireless = false;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm != null) {
            Network active = cm.getActiveNetwork();
            NetworkCapabilities caps = active == null ? null : cm.getNetworkCapabilities(active);
            wireless = caps != null
                    && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
        int color = wireless ? 0xFF0A84FF : 0xB3FFFFFF;
        wifi.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(color));
        wifi.setTextColor(wireless ? 0xFFEAF4FF : 0xCCFFFFFF);
    }

    private void setPixelShift(boolean on) {
        getSharedPreferences("tvhome_prefs", MODE_PRIVATE).edit().putBoolean("pixel_shift", on).apply();
        if (!on) {
            // Return everything to its resting position before stopping the tick.
            View rootView = findViewById(R.id.root);
            if (rootView instanceof android.view.ViewGroup) {
                android.view.ViewGroup g = (android.view.ViewGroup) rootView;
                for (int i = 0; i < g.getChildCount(); i++) {
                    g.getChildAt(i).setTranslationX(0f);
                    g.getChildAt(i).setTranslationY(0f);
                }
            }
        }
        updateSettingsUI();
    }

    /**
     * Re-read the wallpaper folders. The launcher does not download anything; pictures arrive by copying
     * them into the app's {@code files/mine/} folder, which this picks up without a restart.
     */
    private void rescanWallpapers() {
        int n = Wallpapers.count(this);
        if (n > 0 && wallpaperIndex >= n) wallpaperIndex = -1;   // library shrank: re-pick a valid index
        if (wallpaperIndex < 0) rotateWallpaper(true);
    }


    private void setPeekDepth(String depth) {
        peekDepthPref = depth;
        getSharedPreferences("tvhome_prefs", MODE_PRIVATE).edit().putString("dock_peek_depth", depth).apply();
        updatePeekOffset();
        if (isDockPeeking) {
            setDockPeek(true, true);
        }
        updateSettingsUI();
    }


    // ---- now playing island: focus, expansion and transport ----------------------------------------

    private boolean isDescendant(View parent, View child) {
        if (parent == null || child == null) return false;
        if (parent == child) return true;
        if (!(child.getParent() instanceof View)) return false;
        return isDescendant(parent, (View) child.getParent());
    }

    private void expandIsland() {
        if (npControls == null || !npVisible || npExpanded) return;
        npExpanded = true;
        npControls.setVisibility(View.VISIBLE);
        npControls.setAlpha(0f);
        npControls.animate().alpha(1f).setDuration(160).start();
        // No scale on focus: a scaled SurfaceView-less pill gets clipped at its bottom edge, and the
        // extra width from the controls already reads as "expanded".

    }

    private void collapseIsland() {
        if (npControls == null || !npExpanded) return;
        npExpanded = false;
        npControls.animate().alpha(0f).setDuration(140).withEndAction(() -> {
            if (!npExpanded) npControls.setVisibility(View.GONE);
        }).start();
    }

    /** D-pad routing inside the island: 0 = previous, 1 = play/pause, 2 = next. */
    private View.OnKeyListener npKey(final int slot) {
        return (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (slot == 0) {
                        collapseIsland();
                        View pill = findViewById(R.id.btnSettings);
                        if (pill != null) pill.requestFocus();
                    } else {
                        (slot == 1 ? npPrev : npToggle).requestFocus();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (slot == 2) {
                        collapseIsland();
                        View pill = findViewById(R.id.btnSettings);
                        if (pill != null) pill.requestFocus();
                    } else {
                        (slot == 0 ? npToggle : npNext).requestFocus();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    collapseIsland();
                    setDockPeek(false, true);
                    focusTile(lastFocused >= 0 && lastFocused < COLUMNS ? lastFocused : 0);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    collapseIsland();
                    return true;
                default:
                    return false;
            }
        };
    }
}
