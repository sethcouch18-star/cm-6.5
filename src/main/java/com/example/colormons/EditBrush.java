package com.example.colormons;

/** Paints texture pixels into a chosen group, and gives that group a colour if it had none. */
public class EditBrush {
    public static int size = 1;

    public static void paint(MonColors mc, String species, int cx, int cy, int width, int height, int group) {
        int g = Math.max(0, Math.min(MonColors.GROUPS - 1, group));
        boolean wasUnset = mc.colors[g] < 0;

        int painted = 0;
        long r = 0, gg = 0, b = 0;

        for (int dy = -size; dy <= size; dy++) for (int dx = -size; dx <= size; dx++) {
            if (dx * dx + dy * dy > size * size + size) continue;
            int x = cx + dx, y = cy + dy;
            if (x < 0 || y < 0 || x >= width || y >= height) continue;
            set(mc, y * width + x, g);
            painted++;

            int[] px = ScreenData.pixels(species);
            if (px != null) {
                int idx = y * width + x;
                if (idx >= 0 && idx < px.length) {
                    r += (px[idx] >> 16) & 0xFF;
                    gg += (px[idx] >> 8) & 0xFF;
                    b += px[idx] & 0xFF;
                }
            }
        }

        // a group with no colour yet gets one from what was just painted, so the change
        // is visible straight away instead of needing a separate trip to the wheel
        if (wasUnset && painted > 0) {
            if (g == TextureAnalyzer.CUSTOM) {
                mc.colors[g] = 0xBB66FF;
            } else if (r + gg + b > 0) {
                mc.colors[g] = 0xFF000000
                        | ((int) (r / painted) << 16)
                        | ((int) (gg / painted) << 8)
                        | (int) (b / painted);
            } else {
                mc.colors[g] = Palette.FALLBACK[g];
            }
        }
    }

    public static void set(MonColors mc, int index, int group) {
        for (int i = 0; i < mc.editPixels.size(); i++) {
            if (mc.editPixels.get(i) == index) { mc.editGroups.set(i, group); return; }
        }
        mc.editPixels.add(index);
        mc.editGroups.add(group);
    }

    public static void undo(MonColors mc, int n) {
        for (int i = 0; i < n && !mc.editPixels.isEmpty(); i++) {
            mc.editPixels.remove(mc.editPixels.size() - 1);
            mc.editGroups.remove(mc.editGroups.size() - 1);
        }
    }
}
