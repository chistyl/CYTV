package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.content.res.Resources;

/** Shared aspect-preserving renderer for custom app artwork in every launcher surface. */
final class AppArtwork {
    private AppArtwork() { }

    /** Build a reusable 16:9 tile image with exactly the same renderer used by the desktop. */
    static Drawable render(Resources resources, Drawable artwork, int backgroundColor, float scale, boolean fullTile) {
        int width = 640, height = 360;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        RectF tile = new RectF(0, 0, width, height);
        if (fullTile) drawFull(canvas, tile, artwork, backgroundColor);
        else draw(canvas, tile, artwork, backgroundColor, scale);
        return new BitmapDrawable(resources, bitmap);
    }

    private static float aspectRatio(Drawable artwork, RectF fallbackTile) {
        if (artwork == null) return fallbackTile.width() / fallbackTile.height();
        int width = artwork.getIntrinsicWidth();
        int height = artwork.getIntrinsicHeight();
        // Some platform/adaptive drawables report -1 for intrinsic dimensions. Never
        // turn those into a 1:1 image: use the tile's aspect until real dimensions exist.
        return width > 0 && height > 0
                ? (float) width / (float) height
                : fallbackTile.width() / fallbackTile.height();
    }

    static void drawFull(Canvas canvas, RectF tile, Drawable artwork, int backgroundColor) {
        Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setColor(backgroundColor);
        canvas.drawRect(tile, background);
        if (artwork == null) return;
        Drawable mark = artwork.getConstantState() != null
                ? artwork.getConstantState().newDrawable().mutate() : artwork.mutate();
        float aspect = aspectRatio(mark, tile);
        float width = tile.width();
        float height = width / aspect;
        if (height > tile.height()) {
            height = tile.height();
            width = height * aspect;
        }
        Rect oldBounds = new Rect(mark.getBounds());
        mark.setBounds(Math.round(tile.centerX() - width / 2f), Math.round(tile.centerY() - height / 2f),
                Math.round(tile.centerX() + width / 2f), Math.round(tile.centerY() + height / 2f));
        mark.draw(canvas);
        mark.setBounds(oldBounds);
    }

    static void draw(Canvas canvas, RectF tile, Drawable artwork, int backgroundColor, float scale) {
        Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setColor(backgroundColor);
        canvas.drawRect(tile, background);
        if (artwork == null) return;

        Drawable mark = artwork.getConstantState() != null
                ? artwork.getConstantState().newDrawable().mutate() : artwork.mutate();
        float aspect = aspectRatio(mark, tile);
        float maxWidth = tile.width() * 0.375f;
        float maxHeight = tile.height() * (2f / 3f);
        float factor = Math.max(0.10f, Math.min(3.75f, scale));
        float drawWidth = maxWidth * factor;
        float drawHeight = drawWidth / aspect;
        if (drawHeight > maxHeight * factor) {
            drawHeight = maxHeight * factor;
            drawWidth = drawHeight * aspect;
        }
        float left = tile.centerX() - drawWidth / 2f;
        float top = tile.centerY() - drawHeight / 2f;
        Rect oldBounds = new Rect(mark.getBounds());
        mark.setBounds(Math.round(left), Math.round(top),
                Math.round(left + drawWidth), Math.round(top + drawHeight));
        mark.draw(canvas);
        mark.setBounds(oldBounds);
    }
}