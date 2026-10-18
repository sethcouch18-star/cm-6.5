package com.example.colormons;

import net.minecraft.client.gui.GuiGraphics;

/** The group map: texture shape, each pixel in its group's colour, hovered group lit. */
public class GroupOverlay {

    public static void drawMap(GuiGraphics g, String species, int x, int y, int box,
                               MonColors mc, int highlight) {
        int[] pixels = ScreenData.pixels(species);
        int[] effective = ScreenData.effectiveGroups(species, mc);
        ScreenData.Size s = ScreenData.size(species);
        if (pixels == null || effective == null || s == null) return;

        int w = s.width(), h = s.height();
        int cell = Math.max(1, box / Math.max(w, h));

        for (int cy = 0; cy < h; cy++) for (int cx = 0; cx < w; cx++) {
            int i = cy * w + cx;
            int argb = pixels[i];
            if (((argb >>> 24) & 0xFF) == 0) continue;
            int grp = effective[i];
            if (grp < 0 || grp >= Palette.HIGHLIGHT.length) continue;

            if (highlight >= 0 && grp != highlight) {
                // dimmed: show the texture's silhouette faintly
                int r = (argb >> 16) & 0xFF, gg = (argb >> 8) & 0xFF, b = argb & 0xFF;
                int v = (int) ((r * 0.299f + gg * 0.587f + b * 0.114f) * 0.25f);
                g.fill(x + cx * cell, y + cy * cell, x + cx * cell + cell, y + cy * cell + cell,
                        0xFF000000 | (v << 16) | (v << 8) | v);
            } else {
                g.fill(x + cx * cell, y + cy * cell, x + cx * cell + cell, y + cy * cell + cell,
                        0xFF000000 | Palette.HIGHLIGHT[grp]);
            }
        }
    }
}
