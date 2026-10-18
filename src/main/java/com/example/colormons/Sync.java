package com.example.colormons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

/** Sends palettes to the server and applies palettes from other players. */
public class Sync {

    public static void register(PayloadRegistrar registrar) {
        registrar.playBidirectional(ColormonsNet.TYPE, ColormonsNet.CODEC,
                (payload, context) -> context.enqueueWork(() -> apply(payload)));
    }

    public static void broadcast(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        LocalPlayer player = mc.player;
        if (player == null) return;
        MonColors mine = ColorStore.of(id);
        if (mine.isEmpty()) return;
        try {
            PacketDistributor.sendToServer(ColormonsNet.from(id.toString(), mine));
        } catch (Throwable ignored) {}
    }

    private static void apply(ColormonsNet payload) {
        try {
            UUID id = UUID.fromString(payload.uuid());
            ColorStore.putRemote(id, payload.toColors(ColorStore.has(id) ? ColorStore.of(id) : null));
        } catch (Exception ignored) {}
    }
}
