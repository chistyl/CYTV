package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * Lightweight decorative particles over the upper-left ambient glow.
 * Draws a fixed set of tiny dots (no bitmaps, allocations, or random work per frame)
 * and sleeps completely while Home is paused or the glow is hidden.
 */
public final class GlowParticlesView extends View {
    private static final int PARTICLES = 22;
    private static final long FRAME_DELAY_MS = 72L;
    private static final float EFFECT_SCALE = 2.0f;
    private static final float SIZE_VARIATION_SCALE = 1.5f;
    private static final float OFFSET_DP = 30.0f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] seeds = new float[PARTICLES * 4];
    private boolean active;

    public GlowParticlesView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float[] values = {
            .07f,.12f,.12f,.7f, .18f,.08f,.20f,1.1f, .29f,.18f,.10f,.8f,
            .36f,.10f,.17f,1.2f, .11f,.29f,.15f,.9f, .23f,.34f,.22f,1.3f,
            .40f,.28f,.13f,.7f, .06f,.43f,.18f,1.0f, .16f,.49f,.12f,.7f,
            .31f,.43f,.19f,1.1f, .43f,.48f,.14f,.8f, .25f,.58f,.18f,1.0f,
            .08f,.61f,.11f,.7f, .37f,.62f,.20f,1.2f, .19f,.22f,.13f,.8f,
            .34f,.33f,.11f,.7f, .04f,.20f,.16f,.9f, .27f,.04f,.18f,1.1f,
            .45f,.17f,.12f,.7f, .13f,.55f,.16f,.9f, .39f,.55f,.12f,.8f
        };
        System.arraycopy(values, 0, seeds, 0, Math.min(values.length, seeds.length));
        setWillNotDraw(false);
    }

    public void setActive(boolean enabled) {
        if (active == enabled) return;
        active = enabled;
        if (active && getVisibility() == VISIBLE && isAttachedToWindow()) {
            removeCallbacks(frame);
            invalidate();
            post(frame);
        } else {
            removeCallbacks(frame);
        }
    }

    private final Runnable frame = new Runnable() {
        @Override public void run() {
            if (!active || getVisibility() != VISIBLE || !isAttachedToWindow()) return;
            invalidate();
            postDelayed(this, FRAME_DELAY_MS);
        }
    };

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (active) post(frame);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(frame);
        super.onDetachedFromWindow();
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != VISIBLE) removeCallbacks(frame);
        else if (active && isAttachedToWindow()) post(frame);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!active || getWidth() <= 0 || getHeight() <= 0) return;
        long now = SystemClock.uptimeMillis();
        float w = getWidth();
        float h = getHeight();
        float zoneW = w * .47f;
        float zoneH = h * .66f;
        canvas.save();
        float density = getResources().getDisplayMetrics().density;
        canvas.translate(OFFSET_DP * density, OFFSET_DP * density);
        canvas.scale(EFFECT_SCALE, EFFECT_SCALE);
        canvas.clipRect(0f, 0f, zoneW, zoneH);
        for (int i = 0; i < PARTICLES; i++) {
            int p = i * 4;
            float phase = now * (0.00035f + (i % 5) * 0.00008f) + i * 1.71f;
            float x = seeds[p] * zoneW + (float) Math.sin(phase) * (5f + (i % 4) * 2f);
            float y = seeds[p + 1] * zoneH + (float) Math.cos(phase * .73f) * (4f + (i % 3) * 2f);
            float pulse = .30f + .70f * (0.5f + 0.5f * (float) Math.sin(phase * 1.4f));
            // Keep the original average dot size while widening the radius spread by 50%.
            float radiusFactor = 1.30f + (seeds[p + 3] - .95f) * SIZE_VARIATION_SCALE;
            float radius = radiusFactor * density;
            int alpha = Math.min(150, Math.max(12, (int) (seeds[p + 2] * 170f * pulse)));
            paint.setColor((alpha << 24) | 0x00E9F5FF);
            canvas.drawCircle(x, y, radius * 2.5f, paint);
            paint.setColor((Math.min(205, alpha + 35) << 24) | 0x00FFFFFF);
            canvas.drawCircle(x, y, Math.max(.65f * density, radius * .55f), paint);
        }
        canvas.restore();
    }
}
