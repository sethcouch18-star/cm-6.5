package com.example.colormons;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Builds the recoloured texture for one Pokemon. */
public class TintCache {
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();

    /** Glow overlay: only the painted pixels, fully lit. */
    private static final Map<String, ResourceLocation> GLOW = new HashMap<>();

    /** Builds a texture where painted pixels keep full brightness and the rest is clear. */
    public static ResourceLocation buildGlow(UUID id, String species, ResourceLocation original, MonColors mc) {
        String key = id + "|" + original + "|" + mc.colors.length + "|" + mc.editPixels.size();
        ResourceLocation hit = GLOW.get(key);
        if (hit != null) return hit;

        NativeImage base = read(original);
        if (base == null) return null;

        NativeImage img = new NativeImage(base.getWidth(), base.getHeight(), true);
        img.fillRect(0, 0, base.getWidth(), base.getHeight(), 0);

        for (int i = 0; i < mc.editPixels.size() && i < mc.editGroups.size(); i++) {
            int idx = mc.editPixels.get(i);
            int grp = mc.editGroups.get(i);
            if (idx < 0 || idx >= base.getWidth() * base.getHeight()) continue;
            int colour = (grp >= 0 && grp < mc.colors.length && mc.colors[grp] >= 0)
                    ? mc.colors[grp] : 0xFFFFFF;
            int x = idx % base.getWidth(), y = idx / base.getWidth();
            int src = base.getPixelRGBA(x, y);
            int a = (src >>> 24) & 0xFF;
            // keep the pixel's own shading but at full strength, so the glow has shape
            int r = (colour >> 16) & 0xFF, g = (colour >> 8) & 0xFF, b = colour & 0xFF;
            img.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
        }
        base.close();

        ResourceLocation out = Minecraft.getInstance().getTextureManager().register(
                "colormons_glow/" + id.toString().replace("-", ""), new DynamicTexture(img));
        GLOW.put(key, out);
        return out;
    }

    public static void invalidateGlow(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        GLOW.entrySet().removeIf(e -> {
            if (e.getKey().startsWith(id.toString())) {
                mc.getTextureManager().release(e.getValue());
                return true;
            }
            return false;
        });
    }

    public static ResourceLocation maskLocation(String species) {
        return ResourceLocation.fromNamespaceAndPath("colormons", "masks/" + species + ".png");
    }

    public static ResourceLocation getOrCreate(UUID id, String species, ResourceLocation original, MonColors mc) {
        String key = id + "|" + original + "|" + Arrays.toString(mc.colors) + "|" + mc.finish + "|" + mc.editPixels.size();
        ResourceLocation hit = CACHE.get(key);
        if (hit != null) return hit;

        NativeImage img = read(original);
        if (img == null) return original;

        int w = img.getWidth(), h = img.getHeight();
        NativeImage mask = read(maskLocation(species));
        int[] group = TextureAnalyzer.analyse(img, mask);
        if (mask != null) mask.close();

        for (int i = 0; i < mc.editPixels.size() && i < mc.editGroups.size(); i++) {
            int idx = mc.editPixels.get(i);
            if (idx >= 0 && idx < group.length) group[idx] = mc.editGroups.get(i);
        }

        float[] gLum = new float[TextureAnalyzer.GROUP_COUNT];
        int[] gN = new int[TextureAnalyzer.GROUP_COUNT];
        for (int i = 0; i < group.length; i++) {
            int g = group[i];
            if (g < 0 || g >= gLum.length) continue;
            gLum[g] += TextureAnalyzer.lum(img, i % w, i / w);
            gN[g]++;
        }
        for (int g = 0; g < gLum.length; g++) gLum[g] = gN[g] > 0 ? gLum[g] / gN[g] : 1f;

        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x;
            int g = group[i];
            int p = img.getPixelRGBA(x, y);
            int a = (p >>> 24) & 0xFF;
            if (a == 0) continue;
            float l = TextureAnalyzer.lum(img, x, y);
            float shade = (g >= 0 && g < gLum.length) ? l / Math.max(gLum[g], 0.05f) : l;

            if (g >= 0 && g < mc.colors.length && mc.colors[g] >= 0) {
                int t = mc.colors[g];
                int r = clamp(((t >> 16) & 0xFF) * shade);
                int gr = clamp(((t >> 8) & 0xFF) * shade);
                int b = clamp((t & 0xFF) * shade);
                img.setPixelRGBA(x, y, (a << 24) | (b << 16) | (gr << 8) | r);
            } else if (mc.finish == Finish.GLOSSY || mc.finish == Finish.MATTE) {
                Finish.apply(img, x, y, p, a, l, mc.finish, shade);
            }
        }

        ResourceLocation out = Minecraft.getInstance().getTextureManager().register(
                "colormons/" + id.toString().replace("-", ""), new DynamicTexture(img));
        CACHE.put(key, out);
        return out;
    }

    private static NativeImage read(ResourceLocation loc) {
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(loc);
        if (res.isEmpty()) return null;
        try (InputStream in = res.get().open()) { return NativeImage.read(in); }
        catch (Exception e) { return null; }
    }

    public static void invalidate(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        CACHE.entrySet().removeIf(e -> {
            if (e.getKey().startsWith(id.toString())) {
                mc.getTextureManager().release(e.getValue());
                return true;
            }
            return false;
        });
        invalidateGlow(id);
    }

    private static int clamp(float v) { return Math.max(0, Math.min(255, (int) v)); }
}
