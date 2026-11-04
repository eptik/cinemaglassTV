package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * The now-playing pill itself: glass background plus a playback outline that hugs its own border.
 *
 * <p>Drawing the progress here rather than as an overlay view matters — a match_parent overlay inside a
 * wrap_content parent makes the parent fill the screen, which is exactly the bug that blew the widget up to
 * full size the first time.
 */
public class NowPlayingPill extends FrameLayout {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Path perimeter = new Path();
    private final Path segment = new Path();
    private PathMeasure measure;
    private final float radius;
    private float fraction;

    public NowPlayingPill(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        radius = 30f * density;                       // matches np_glass corners
        for (Paint p : new Paint[]{track, fill}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f * density);
            p.setStrokeCap(Paint.Cap.ROUND);
        }
        track.setColor(0x66FFFFFF);      // visible on every edge; the played share is much brighter
        fill.setColor(0xE6FFFFFF);
        setWillNotDraw(false);
    }

    public void setFraction(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        if (Math.abs(clamped - fraction) < 0.002f) return;
        fraction = clamped;
        invalidate();
    }

    private void buildPerimeter() {
        // Inset by a full stroke width, not half: on this hardware the bottom half-pixel row of a
        // centred stroke gets clipped, which made the pill look like it had no bottom edge.
        float inset = fill.getStrokeWidth();
        bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
        float r = Math.min(radius, Math.min(bounds.width(), bounds.height()) / 2f);
        perimeter.reset();
        perimeter.addRoundRect(bounds, r, r, Path.Direction.CW);
        measure = new PathMeasure(perimeter, false);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);           // children first: the border must never be covered
        buildPerimeter();
        if (measure == null) return;
        canvas.drawPath(perimeter, track);
        if (fraction > 0f) {
            segment.reset();
            measure.getSegment(0f, measure.getLength() * fraction, segment, true);
            canvas.drawPath(segment, fill);
        }
    }
}