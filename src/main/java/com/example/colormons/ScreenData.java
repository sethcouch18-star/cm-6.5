package com.example.colormons;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Flat arrays the screen draws from. Nothing runs during rendering. */
public class ScreenData {
    public record Size(int width, int height) {}

    private static final Map<String, int[]> GROUPS = new HashMap<>();
    private static final Map<String, int[]> PIXELS = new HashMap<>();
    private static final Map<String, Size> SIZES = new HashMap<>();
    private static final Map<String, Boolean> FOUND = new HashMap<>();

    public static void reset() { GROUPS.clear(); PIXELS.clear(); SIZES.clear(); FOUND.clear(); }

    /** Cobblemon stores species textures under numbered folders, so scan for the name. */
    public static ResourceLocation textureOf(String species) {
        ResourceLocation direct = ResourceLocation.fromNamespaceAndPath(
                "cobblemon", "textures/pokemon/" + species + ".png");
        if (exists(direct)) return direct;
        ResourceLocation scanned = scan(species);
        return scanned != null ? scanned : direct;
    }

    private static ResourceLocation scan(String species) {
        try {
            var rm = Minecraft.getInstance().getResourceManager();
            var all = rm.listResources("textures/pokemon",
                    p -> p.getPath().endsWith("/" + species + ".png"));
            for (var entry : all.keySet()) return entry;
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean exists(ResourceLocation loc) {
        try {
            return Minecraft.getInstance().getResourceManager().getResource(loc).isPresent();
        } catch (Throwable t) { return false; }
    }

    public static boolean available(String species) {
        ensure(species);
        return Boolean.TRUE.equals(FOUND.get(species));
    }

    private static void ensure(String species) {
        if (GROUPS.containsKey(species)) return;
        NativeImage img = read(textureOf(species));
        if (img == null) { FOUND.put(species, false); return; }

        NativeImage mask = read(TintCache.maskLocation(species));
        int[] group = TextureAnalyzer.analyse(img, mask);
        if (mask != null) mask.close();

        int w = img.getWidth(), h = img.getHeight();
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int abgr = img.getPixelRGBA(x, y);
            int a = (abgr >>> 24) & 0xFF;
            int r = abgr & 0xFF, g = (abgr >> 8) & 0xFF, b = (abgr >> 16) & 0xFF;
            pixels[y * w + x] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        img.close();

        SIZES.put(species, new Size(w, h));
        PIXELS.put(species, pixels);
        GROUPS.put(species, group);
        FOUND.put(species, true);
    }

    public static int[] groups(String species) { ensure(species); return GROUPS.get(species); }
    public static int[] pixels(String species) { ensure(species); return PIXELS.get(species); }
    public static Size size(String species) { ensure(species); return SIZES.get(species); }

    /** Average colour of a group's pixels as detected from the texture, or -1. */
    public static int averageOf(String species, int group) {
        int[] px = pixels(species);
        int[] g = groups(species);
        if (px == null || g == null) return -1;
        long r = 0, gg = 0, b = 0;
        int n = 0;
        for (int i = 0; i < g.length && i < px.length; i++) {
            if (g[i] != group) continue;
            if (((px[i] >>> 24) & 0xFF) == 0) continue;
            r += (px[i] >> 16) & 0xFF;
            gg += (px[i] >> 8) & 0xFF;
            b += px[i] & 0xFF;
            n++;
        }
        if (n == 0) return -1;
        return 0xFF000000 | ((int) (r / n) << 16) | ((int) (gg / n) << 8) | (int) (b / n);
    }

    public static int[] effectiveGroups(String species, MonColors mc) {
        int[] base = groups(species);
        if (base == null) return null;
        int[] out = base.clone();
        for (int i = 0; i < mc.editPixels.size() && i < mc.editGroups.size(); i++) {
            int idx = mc.editPixels.get(i);
            if (idx >= 0 && idx < out.length) out[idx] = mc.editGroups.get(i);
        }
        return out;
    }

    private static NativeImage read(ResourceLocation loc) {
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(loc);
        if (res.isEmpty()) return null;
        try (InputStream in = res.get().open()) { return NativeImage.read(in); }
        catch (Exception e) { return null; }
    }
}
