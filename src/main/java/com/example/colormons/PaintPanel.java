package com.example.colormons;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The paint grid. Draws the species texture's pixels tinted by group.
 *
 * Cell size comes from the texture's largest dimension, so the WHOLE texture fits the
 * panel however large it is. A minimum cell of 1 pixel keeps a big sheet drawable, and
 * a maximum keeps a tiny texture from becoming a blur of huge blocks.
 */
public class PaintPanel {
    public static final int MIN_CELL = 1;
    public static final int MAX_CELL = 6;

    public static int cellFor(String species, int box) {
        ScreenData.Size s = ScreenData.size(species);
        if (s == null) return 3;
        int max = Math.max(Math.max(s.width(), s.height()), 1);
        int cell = box / max;
        return Math.max(MIN_CELL, Math.min(MAX_CELL, cell));
    }

    /** The panel actually occupied, so the caller draws a box that matches. */
    public static int sizeFor(String species, int box) {
        ScreenData.Size s = ScreenData.size(species);
        if (s == null) return box;
        return cellFor(species, box) * Math.max(s.width(), s.height());
    }

    public static void draw(GuiGraphics g, String species, MonColors mc, int x, int y, int box) {
        int[] pixels = ScreenData.pixels(species);
        ScreenData.Size s = ScreenData.size(species);
        int[] effective = ScreenData.effectiveGroups(species, mc);
        if (pixels == null || s == null || effective == null) return;

        int cell = cellFor(species, box);
        int tw = s.width(), th = s.height();

        for (int py = 0; py < th; py++) for (int px = 0; px < tw; px++) {
            int idx = py * tw + px;
            int argb = pixels[idx];
            if (((argb >>> 24) & 0xFF) == 0) continue;

            int r = (argb >> 16) & 0xFF, gg = (argb >> 8) & 0xFF, b = argb & 0xFF;
            int grp = effective[idx];
            int out;
            if (grp >= 0 && grp < Palette.HIGHLIGHT.length) {
                int tint = Palette.HIGHLIGHT[grp];
                float lum = (r * 0.299f + gg * 0.587f + b * 0.114f) / 255f;
                float mix = 0.55f + 0.45f * lum;
                int tr = (tint >> 16) & 0xFF, tg = (tint >> 8) & 0xFF, tb = tint & 0xFF;
                out = (blend(r, tr, mix) << 16) | (blend(gg, tg, mix) << 8) | blend(b, tb, mix);
            } else {
                out = argb & 0xFFFFFF;
            }
            g.fill(x + px * cell, y + py * cell, x + px * cell + cell, y + py * cell + cell,
                    0xFF000000 | out);
        }
    }

    private static int blend(int from, int to, float t) {
        int v = (int) (from * (1f - t) + to * t);
        return Math.max(0, Math.min(255, v));
    }

    public static int[] pixelAt(String species, double mx, double my, int x, int y, int box) {
        ScreenData.Size s = ScreenData.size(species);
        if (s == null) return null;
        int cell = cellFor(species, box);
        int px = (int) Math.floor((mx - x) / cell);
        int py = (int) Math.floor((my - y) / cell);
        if (px < 0 || py < 0 || px >= s.width() || py >= s.height()) return null;
        return new int[]{px, py};
    }
}
