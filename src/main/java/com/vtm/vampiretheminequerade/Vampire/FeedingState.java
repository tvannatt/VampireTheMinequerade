package com.vtm.vampiretheminequerade.Vampire;

import java.util.UUID;

import net.minecraft.world.phys.Vec3;

public record FeedingState(UUID victim, Vec3 startPos) {

    public static final FeedingState NONE = new FeedingState(null, Vec3.ZERO);

    public boolean isFeeding() {
        return victim != null;
    }
}
