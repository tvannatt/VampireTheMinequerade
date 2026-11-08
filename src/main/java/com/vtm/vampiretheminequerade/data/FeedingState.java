package com.vtm.vampiretheminequerade.data;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

public record FeedingState(UUID victim, Vec3 startPos) {

    public static final FeedingState NONE = new FeedingState(null, Vec3.ZERO);

    public boolean isFeeding() {
        return victim != null;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, FeedingState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), state -> Optional.ofNullable(state.victim()),
            victim -> victim.map(uuid -> new FeedingState(uuid, Vec3.ZERO)).orElse(NONE)
    );
}
