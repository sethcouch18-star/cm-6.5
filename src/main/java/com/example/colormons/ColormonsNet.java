package com.example.colormons;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One Pokemon's colours plus finish, shared so every player sees the same thing. */
public record ColormonsNet(String uuid, int c0, int c1, int c2, int c3, int finish)
        implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("colormons", "palette");
    public static final CustomPacketPayload.Type<ColormonsNet> TYPE = new CustomPacketPayload.Type<>(ID);

    private static final StreamCodec<RegistryFriendlyByteBuf, Integer> INT =
            ByteBufCodecs.VAR_INT.cast();

    public static final StreamCodec<RegistryFriendlyByteBuf, ColormonsNet> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ColormonsNet::uuid,
                    INT, ColormonsNet::c0,
                    INT, ColormonsNet::c1,
                    INT, ColormonsNet::c2,
                    INT, ColormonsNet::c3,
                    INT, ColormonsNet::finish,
                    ColormonsNet::new);

    @Override
    public Type<ColormonsNet> type() { return TYPE; }

    public static ColormonsNet from(String uuid, MonColors mc) {
        return new ColormonsNet(uuid, mc.colors[0], mc.colors[1], mc.colors[2], mc.colors[3], mc.finish);
    }

    public MonColors toColors(MonColors existing) {
        MonColors target = existing != null ? existing : new MonColors();
        target.colors = new int[]{c0, c1, c2, c3};
        target.finish = finish;
        return target;
    }
}
