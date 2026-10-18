package com.example.colormons;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

/**
 * Draws the real Pokemon inside the GUI.
 *
 * The scale is derived from the entity's own bounding box, so a Guzzlord and a Pikachu
 * both fill the panel to the same degree. A hard clamp means nothing can overflow the
 * panel whatever the species reports.
 */
public class Preview {

    public static void draw(GuiGraphics g, PokemonEntity entity, float x, float y,
                            float boxW, float boxH, float yaw, float pitch) {
        if (entity == null) return;
        Minecraft mc = Minecraft.getInstance();

        float size = fitScale(entity, boxW, boxH);

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 100.0f);
        pose.scale(size, size, -size);
        pose.mulPose(new Quaternionf().rotateZ((float) Math.PI));
        pose.mulPose(new Quaternionf().rotateY(yaw));
        if (pitch != 0f) pose.mulPose(new Quaternionf().rotateX(pitch));

        Lighting.setupForEntityInInventory();
        mc.getEntityRenderDispatcher().setRenderShadow(false);
        try {
            mc.getEntityRenderDispatcher().render(
                    entity,
                    0.0, 0.0, 0.0,
                    0.0f,
                    0.0f,
                    pose,
                    mc.renderBuffers().bufferSource(),
                    0xF000F0);
            mc.renderBuffers().bufferSource().endBatch();
        } finally {
            mc.getEntityRenderDispatcher().setRenderShadow(true);
            pose.popPose();
        }
    }

    /**
     * Scale that makes the entity fit the panel. Uses the largest horizontal extent and
     * the height of its bounding box, then clamps so a huge model cannot escape.
     */
    public static float fitScale(PokemonEntity entity, float boxW, float boxH) {
        float span = 1.0f;
        float tall = 1.0f;
        try {
            AABB bb = entity.getBoundingBox();
            double sx = bb.getXsize(), sy = bb.getYsize(), sz = bb.getZsize();
            span = (float) Math.max(sx, sz);
            tall = (float) Math.max(0.2, sy);
        } catch (Throwable ignored) {}

        // the model is usually a bit wider than its hitbox; give a small allowance
        float modelSpan = Math.max(0.4f, span * 1.35f);
        float modelTall = Math.max(0.4f, tall * 1.15f);

        float byWidth = (boxW * 0.85f) / modelSpan;
        float byHeight = (boxH * 0.92f) / modelTall;
        float scale = Math.min(byWidth, byHeight);

        // never allow an absurd scale in either direction
        return Math.max(2.0f, Math.min(140.0f, scale));
    }
}
