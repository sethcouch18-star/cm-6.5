package com.example.colormons;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Layout is derived from one axis: the wheel sits in the middle, and everything else
 * is packed against the nearest edge of the space left over.
 */
public class ColorWheelScreen extends Screen {
    private static final int WHEEL = 170;
    private static final int ROW_H = 22;
    private static final int BTN_H = 18;

    private static ResourceLocation wheelTex;

    private final UUID monId;
    private final String species;
    private final PokemonEntity entity;
    private final MonColors mc;
    private final List<int[]> history = new ArrayList<>();

    private int selected = 0;
    private float brightness = 1f;
    private boolean editing = false;
    private int hovered = -1;
    private boolean hasTexture = true;

    private float yaw = 0.6f, pitch = -0.25f;
    private boolean draggingPreview = false;

    // computed in init()
    private int wheelX, wheelY, barVX, barSX;
    private int rightX, groupY, swatchX, countX;
    private int leftW, leftPanel, previewX, previewY, previewW, previewH;
    private int bottomY;

    public ColorWheelScreen(PokemonEntity mon) {
        super(Component.literal(mon.getPokemon().getSpecies().getName() + " Colours"));
        this.entity = mon;
        this.monId = mon.getPokemon().getUuid();
        this.species = mon.getPokemon().getSpecies().getResourceIdentifier().getPath();
        this.mc = ColorStore.of(monId);

        // colours are NOT seeded: the Pokemon keeps its own look until you pick something.
        // the first group that already has a colour is selected, otherwise Primary.
        for (int i = 0; i < MonColors.GROUPS; i++) {
            if (mc.colors[i] >= 0) { selected = i; break; }
        }
        ScreenData.groups(species);
        this.hasTexture = ScreenData.available(species);
    }

    private void snapshot() {
        history.add(mc.colors.clone());
        if (history.size() > 20) history.remove(0);
    }

    @Override
    protected void init() {
        buildWheelTexture();

        int bottom = 14;                       // how close the button rows sit to the edge
        bottomY = height - bottom - BTN_H;

        // ---- the wheel owns the centre ----
        wheelX = width / 2 - WHEEL / 2 - 40;
        wheelY = height / 2 - WHEEL / 2 - 10;
        barVX = wheelX + WHEEL + 10;
        barSX = wheelX + WHEEL + 32;

        // ---- left column: whatever room is left of the wheel ----
        leftW = Math.max(90, wheelX - 24);
        previewX = 8;
        previewY = height - bottom - 40 - 120;
        previewW = leftW;
        previewH = 120;
        previewY = Math.max(8, previewY);

        // ---- right column: from the wheel's right edge to the screen edge ----
        rightX = wheelX + WHEEL + 60;
        int rightRoom = width - rightX - 8;
        groupY = 40;
        swatchX = rightX + Math.min(120, rightRoom - 96);
        countX = swatchX + 26;

        for (int i = 0; i < MonColors.GROUPS; i++) {
            final int g = i;
            addRenderableWidget(Button.builder(Component.literal(Palette.GROUP_NAMES[i]), b -> selected = g)
                    .bounds(rightX, groupY + i * ROW_H, Math.min(120, rightRoom - 96), BTN_H).build());
        }

        int btnY = groupY + MonColors.GROUPS * ROW_H + 10;
        int btnW = Math.min(88, (Math.min(120, rightRoom - 96)) - 0) ;
        addRenderableWidget(Button.builder(Component.literal(editing ? "Edit: ON" : "Edit: OFF"), b -> {
            editing = !editing;
            b.setMessage(Component.literal(editing ? "Edit: ON" : "Edit: OFF"));
        }).bounds(rightX, btnY, btnW, BTN_H).build());

        addRenderableWidget(Button.builder(Component.literal("Brush " + (EditBrush.size + 1)), b -> {
            EditBrush.size = (EditBrush.size + 1) % 4;
            b.setMessage(Component.literal("Brush " + (EditBrush.size + 1)));
        }).bounds(rightX + btnW + 4, btnY, btnW, BTN_H).build());

        addRenderableWidget(Button.builder(Component.literal("Undo brush"), b -> {
            EditBrush.undo(mc, (EditBrush.size + 1) * (EditBrush.size + 1));
            ColorStore.touched(monId);
        }).bounds(rightX, btnY + BTN_H + 4, btnW * 2 + 4, BTN_H).build());

        addRenderableWidget(Button.builder(Component.literal("Reset view"), b -> {
            yaw = 0.6f;
            pitch = -0.25f;
        }).bounds(rightX, btnY + (BTN_H + 4) * 2, btnW * 2 + 4, BTN_H).build());

        // ---- finishes and actions, one row hard against the bottom edge ----
        int fx = 8;
        for (int i = 0; i < 4; i++) {
            final int f = i;
            addRenderableWidget(Button.builder(Component.literal(Palette.FINISH_NAMES[i]), b -> {
                snapshot();
                mc.finish = f;
                ColorStore.touched(monId);
            }).bounds(fx + i * 74, bottomY, 70, BTN_H).build());
        }

        int ax = fx + 4 * 74 + 12;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> snapshot())
                .bounds(ax, bottomY, 58, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("Undo"), b -> {
            if (!history.isEmpty()) {
                System.arraycopy(history.remove(history.size() - 1), 0, mc.colors, 0, MonColors.GROUPS);
                ColorStore.touched(monId);
            }
        }).bounds(ax + 62, bottomY, 58, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            snapshot();
            for (int i = 0; i < MonColors.GROUPS; i++) mc.colors[i] = -1;
            mc.editPixels.clear();
            mc.editGroups.clear();
            mc.finish = 0;
            ColorStore.touched(monId);
        }).bounds(ax + 124, bottomY, 58, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(ax + 186, bottomY, 58, BTN_H).build());
    }

    private void buildWheelTexture() {
        if (wheelTex != null) return;
        NativeImage img = new NativeImage(WHEEL, WHEEL, true);
        float r = WHEEL / 2f;
        for (int y = 0; y < WHEEL; y++) for (int x = 0; x < WHEEL; x++) {
            float dx = x - r, dy = y - r;
            float d = (float) Math.sqrt(dx * dx + dy * dy) / r;
            if (d > 1f) { img.setPixelRGBA(x, y, 0); continue; }
            float hue = (float) ((Math.atan2(dy, dx) / (2 * Math.PI) + 1) % 1);
            int rgb = Color.HSBtoRGB(hue, d, 1f);
            int rr = (rgb >> 16) & 0xFF, gg = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
            img.setPixelRGBA(x, y, 0xFF000000 | (b << 16) | (gg << 8) | rr);
        }
        wheelTex = minecraft.getTextureManager().register("colormons/wheel", new DynamicTexture(img));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        hovered = groupUnderMouse(mouseX, mouseY);
        super.render(g, mouseX, mouseY, delta);

        // ---- left: the paint grid while editing, otherwise the group map ----
        int room = Math.min(leftW, previewY - 26);
        int panel = PaintPanel.sizeFor(species, room);
        int px = 8, py = 8;
        g.fill(px - 2, py - 2, px + panel + 4, py + panel + 4, 0xCC000000);
        if (editing) {
            PaintPanel.draw(g, species, mc, px, py, room);
            g.drawString(font, "paint " + Palette.GROUP_NAMES[selected],
                    px + 3, py + panel + 14, 0x66FF66);
        } else {
            GroupOverlay.drawMap(g, species, px, py, panel, mc, hovered);
            g.drawString(font, hovered >= 0 ? Palette.GROUP_NAMES[hovered] : "all groups",
                    px + 3, py + panel + 14, 0x999999);
        }

        // ---- left lower: the 3D preview ----
        g.fill(previewX - 2, previewY - 2, previewX + previewW + 4, previewY + previewH + 4, 0xCC000000);
        if (entity != null) {
            try {
                Preview.draw(g, entity,
                        previewX + previewW / 2.0f,
                        previewY + previewH - 20.0f,
                        previewW, previewH,
                        yaw, pitch);
            } catch (Throwable ignored) {
                g.drawCenteredString(font, "preview unavailable",
                        previewX + previewW / 2, previewY + previewH / 2, 0xAA6666);
            }
        }

        // ---- wheel and bars, dead centre ----
        if (wheelTex != null) g.blit(wheelTex, wheelX, wheelY, 0, 0, WHEEL, WHEEL, WHEEL, WHEEL);
        drawBar(g, barVX);
        drawBar(g, barSX);
        g.fill(barVX - 3, wheelY + (int) ((1f - brightness) * (WHEEL - 2)) - 1,
               barVX + 13, wheelY + (int) ((1f - brightness) * (WHEEL - 2)) + 1, 0xFFFF4444);
        int satY = wheelY + (int) ((1f - saturation()) * (WHEEL - 2));
        g.fill(barSX - 3, satY - 1, barSX + 13, satY + 1, 0xFF44FF44);

        // ---- right: group rows with counts beside the swatch ----
        for (int i = 0; i < MonColors.GROUPS; i++) {
            int y = groupY + i * ROW_H;
            if (i == selected) g.fill(rightX - 3, y - 2, countX + 46, y + BTN_H + 2, 0x55FFFFFF);
            if (i == hovered && i != selected) g.fill(rightX - 3, y - 2, countX + 46, y + BTN_H + 2, 0x22FFFF00);
            int set = mc.colors[i];
            if (set >= 0) {
                g.fill(swatchX, y, swatchX + 18, y + BTN_H, 0xFF000000 | set);
            } else {
                // a hint: the colour this group actually is in the texture, dimmed
                int hint = ScreenData.averageOf(species, i);
                if (hint >= 0) {
                    g.fill(swatchX, y, swatchX + 18, y + BTN_H, dim(0xFF000000 | hint, 130));
                    g.fill(swatchX, y, swatchX + 18, y + 1, 0xFF555555);
                } else {
                    g.fill(swatchX, y, swatchX + 18, y + BTN_H, 0xFF202020);
                }
            }
            g.drawString(font, countPixels(i) + "px", countX, y + 5, 0xBBBBBB);
        }

        g.drawCenteredString(font, title, wheelX + WHEEL / 2, 14, 0xFFFFFF);
        g.drawCenteredString(font,
                editing ? "painting into " + Palette.GROUP_NAMES[selected] + "  -  brush " + (EditBrush.size + 1)
                        : "hover a row to highlight  -  drag the model to turn it",
                wheelX + WHEEL / 2, 28, editing ? 0x66FF66 : 0x888888);
    }

    private static int dim(int argb, int amount) {
        int a = (argb >>> 24) & 0xFF;
        int r = ((argb >> 16) & 0xFF) * amount / 255;
        int gg = ((argb >> 8) & 0xFF) * amount / 255;
        int b = (argb & 0xFF) * amount / 255;
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    private void drawBar(GuiGraphics g, int x) {
        for (int i = 0; i < WHEEL; i++) {
            int v = 255 - i * 255 / WHEEL;
            g.fill(x, wheelY + i, x + 8, wheelY + i + 1, 0xFF000000 | (v << 16) | (v << 8) | v);
        }
    }

    private float saturation() {
        int c = Palette.shown(mc, selected);
        float[] hsb = Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
        return hsb[1];
    }

    private int countPixels(int group) {
        int[] effective = ScreenData.effectiveGroups(species, mc);
        if (effective == null) return 0;
        int n = 0;
        for (int v : effective) if (v == group) n++;
        return n;
    }

    private int groupUnderMouse(int mx, int my) {
        for (int i = 0; i < MonColors.GROUPS; i++) {
            int y = groupY + i * ROW_H;
            if (my >= y - 2 && my <= y + BTN_H + 2 && mx >= rightX - 4 && mx <= countX + 46) return i;
        }
        return -1;
    }

    private boolean overPreview(double mx, double my) {
        return mx >= previewX && mx <= previewX + previewW
                && my >= previewY && my <= previewY + previewH;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (overPreview(mx, my) && !isOverButton(mx, my)) { draggingPreview = true; return true; }

        int row = groupUnderMouse((int) mx, (int) my);
        if (row >= 0 && mx >= swatchX - 6) { selected = row; return true; }

        if (editing && !isOverButton(mx, my)) {
            int room = Math.min(leftW, previewY - 26);
            int[] hit = PaintPanel.pixelAt(species, mx, my, 8, 8, room);
            if (hit != null) {
                ScreenData.Size sz = ScreenData.size(species);
                EditBrush.paint(mc, species, hit[0], hit[1], sz.width(), sz.height(), selected);
                ColorStore.touched(monId);
                return true;
            }
        }
        return pick(mx, my) || super.mouseClicked(mx, my, b);
    }

    private boolean isOverButton(double mx, double my) {
        for (var child : children()) {
            if (child instanceof Button btn && btn.isMouseOver(mx, my)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (draggingPreview) {
            yaw += (float) dx * 0.02f;
            pitch = Math.max(-1.2f, Math.min(1.2f, pitch + (float) dy * 0.02f));
            return true;
        }
        if (editing && !isOverButton(mx, my)) {
            int room = Math.min(leftW, previewY - 26);
            int[] hit = PaintPanel.pixelAt(species, mx, my, 8, 8, room);
            if (hit != null) {
                ScreenData.Size sz = ScreenData.size(species);
                EditBrush.paint(mc, species, hit[0], hit[1], sz.width(), sz.height(), selected);
                ColorStore.touched(monId);
                return true;
            }
        }
        return pick(mx, my) || super.mouseDragged(mx, my, b, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        draggingPreview = false;
        ColorStore.save();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (overPreview(mx, my)) {
            EditBrush.size = Math.max(1, Math.min(4, EditBrush.size + (dy > 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int mods) {
        if (key == GLFW.GLFW_KEY_LEFT) { yaw -= 0.12f; return true; }
        if (key == GLFW.GLFW_KEY_RIGHT) { yaw += 0.12f; return true; }
        if (key == GLFW.GLFW_KEY_UP) { pitch = Math.max(-1.2f, pitch - 0.08f); return true; }
        if (key == GLFW.GLFW_KEY_DOWN) { pitch = Math.min(1.2f, pitch + 0.08f); return true; }
        return super.keyPressed(key, scancode, mods);
    }

    private boolean pick(double mx, double my) {
        if (overBar(mx, my, barVX)) {
            brightness = clamp01(1f - (float) ((my - wheelY) / WHEEL));
            setFromWheel();
            return true;
        }
        if (overBar(mx, my, barSX)) {
            applySaturation(clamp01(1f - (float) ((my - wheelY) / WHEEL)));
            return true;
        }
        float r = WHEEL / 2f;
        double dx = mx - (wheelX + r), dy = my - (wheelY + r);
        double d = Math.sqrt(dx * dx + dy * dy) / r;
        if (d <= 1.0) {
            float hue = (float) ((Math.atan2(dy, dx) / (2 * Math.PI) + 1) % 1);
            mc.colors[selected] = Color.HSBtoRGB(hue, (float) d, brightness) & 0xFFFFFF;
            ColorStore.touched(monId);
            return true;
        }
        return false;
    }

    private boolean overBar(double mx, double my, int x) {
        return mx >= x - 4 && mx <= x + 12 && my >= wheelY - 2 && my <= wheelY + WHEEL + 2;
    }

    private void setFromWheel() {
        int c = Palette.shown(mc, selected);
        float[] hsb = Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
        mc.colors[selected] = Color.HSBtoRGB(hsb[0], hsb[1], brightness) & 0xFFFFFF;
        ColorStore.touched(monId);
    }

    private void applySaturation(float sat) {
        int c = Palette.shown(mc, selected);
        float[] hsb = Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
        mc.colors[selected] = Color.HSBtoRGB(hsb[0], sat, brightness) & 0xFFFFFF;
        ColorStore.touched(monId);
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    @Override
    public void onClose() {
        ColorStore.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
