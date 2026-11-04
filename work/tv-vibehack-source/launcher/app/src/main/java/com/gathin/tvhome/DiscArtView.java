package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Album art as a vinyl disc: circular crop, a hairline rim and a label ring. Static by design — the launcher
 * must not burn frames on decoration.
 */
public class DiscArtView extends View {

    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint groove = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final Rect src = new Rect();
    private final RectF dst = new RectF();

    private Bitmap art;

    public DiscArtView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1f * density);
        rim.setColor(0x33FFFFFF);
        groove.setStyle(Paint.Style.STROKE);
        groove.setStrokeWidth(1f * density);
        groove.setColor(0x1A000000);
    }

    public void setArt(Bitmap bitmap) {
        if (bitmap == art) return;
        art = bitmap;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        float cx = w / 2f;
        float cy = h / 2f;
        float radius = Math.min(w, h) / 2f;

        if (art != null) {
            // centre-crop the artwork to a square so the disc is never distorted
            int side = Math.min(art.getWidth(), art.getHeight());
            src.set((art.getWidth() - side) / 2, (art.getHeight() - side) / 2,
                    (art.getWidth() + side) / 2, (art.getHeight() + side) / 2);

            canvas.save();
            clip.reset();
            clip.addCircle(cx, cy, radius, Path.Direction.CW);
            canvas.clipPath(clip);
            dst.set(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawBitmap(art, src, dst, artPaint);
            canvas.restore();

            canvas.drawCircle(cx, cy, radius - 0.5f, rim);
            canvas.drawCircle(cx, cy, radius * 0.30f, groove);   // label ring
            canvas.drawCircle(cx, cy, radius * 0.05f, groove);   // spindle hole
        }
    }

}