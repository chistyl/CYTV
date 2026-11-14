package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * Lightweight firefly-like particles over the upper-left ambient glow.
 * Uses a fixed particle set and layered translucent circles for soft edges.
 * No per-frame allocations; animation sleeps while the view is inactive.
 */
public final class GlowParticlesView extends View {
    private static final int PARTICLES = 22;
    private static final long FRAME_DELAY_MS = 64L;
    // Enlarge the R138 particle field by 100% (2x overall scale).
    private static final float EFFECT_SCALE = 2.0f;
    // Increase the R138 particle radius spread by a further 50%.
    private static final float SIZE_VARIATION_SCALE = 3.375f;
    // Move the particle field 70dp right and 100dp down.
    private static final float OFFSET_X_DP = 70.0f;
    private static final float OFFSET_Y_DP = 100.0f;

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

    /** Draws a diffuse halo using nested translucent circles, avoiding a hard dot edge. */
    private void drawSoftGlow(Canvas canvas, float x, float y, float radius, int alpha, float density) {
        paint.setColor((Math.max(2, alpha / 9) << 24) | 0x00DDF4FF);
        canvas.drawCircle(x, y, radius * 4.8f, paint);
        paint.setColor((Math.max(3, alpha / 7) << 24) | 0x00E5F7FF);
        canvas.drawCircle(x, y, radius * 3.7f, paint);
        paint.setColor((Math.max(4, alpha / 5) << 24) | 0x00E9F8FF);
        canvas.drawCircle(x, y, radius * 2.8f, paint);
        paint.setColor((Math.max(5, alpha / 4) << 24) | 0x00F0FAFF);
        canvas.drawCircle(x, y, radius * 2.0f, paint);
        paint.setColor((Math.max(6, alpha / 3) << 24) | 0x00F5FCFF);
        canvas.drawCircle(x, y, radius * 1.35f, paint);
        paint.setColor((Math.max(8, alpha / 2) << 24) | 0x00FFFFFF);
        canvas.drawCircle(x, y, Math.max(.55f * density, radius * .72f), paint);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!active || getWidth() <= 0 || getHeight() <= 0) return;

        long now = SystemClock.uptimeMillis();
        float density = getResources().getDisplayMetrics().density;
        float zoneW = getWidth() * .47f;
        float zoneH = getHeight() * .66f;

        canvas.save();
        canvas.translate(OFFSET_X_DP * density, OFFSET_Y_DP * density);
        canvas.scale(EFFECT_SCALE, EFFECT_SCALE);
        canvas.clipRect(0f, 0f, zoneW, zoneH);

        for (int i = 0; i < PARTICLES; i++) {
            int p = i * 4;
            // Slow, overlapping horizontal and vertical drift gives each firefly an individual path.
            float motionPhase = now * (0.00020f + (i % 5) * 0.000035f) + i * 1.37f;
            float driftX = (float) Math.sin(motionPhase) * (10f + (i % 5) * 2.8f) * density
                    + (float) Math.sin(motionPhase * .47f + i) * 4.0f * density;
            float driftY = (float) Math.cos(motionPhase * .71f + i * .63f) * (12f + (i % 4) * 3.0f) * density
                    + (float) Math.sin(motionPhase * .39f) * 5.0f * density;
            float x = seeds[p] * zoneW + driftX;
            float y = seeds[p + 1] * zoneH + driftY;

            // A slow, gentle glow cycle with a small secondary wave; no abrupt on/off flicker.
            float glowPhase = now * (0.00075f + (i % 6) * 0.00013f) + i * 2.11f;
            float pulseWave = .5f + .5f * (float) Math.sin(glowPhase
                    + .32f * (float) Math.sin(glowPhase * .43f + i));
            float pulse = .14f + .86f * pulseWave;

            // Preserve the soft firefly character while widening individual size differences.
            float radiusFactor = 1.30f + (seeds[p + 3] - .95f) * SIZE_VARIATION_SCALE;
            float radius = Math.max(.55f, radiusFactor) * density;
            int alpha = Math.min(155, Math.max(9, (int) (seeds[p + 2] * 190f * pulse)));
            drawSoftGlow(canvas, x, y, radius, alpha, density);
        }
        canvas.restore();
    }
}
