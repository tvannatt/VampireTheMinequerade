package com.vtm.vampiretheminequerade.Vampire;

import java.util.function.Supplier;

import com.vtm.vampiretheminequerade.VampireTheMinequerade;

import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModAttachments {

    /* Holds every attachment type we register. */
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, VampireTheMinequerade.MODID);

    // TODO: The clan won't always be random. Replace this default once clan selection exists.
    /* A player's vampire state. */
    public static final Supplier<AttachmentType<VampireData>> VAMPIRE_DATA = ATTACHMENT_TYPES.register(
            "vampire_data",
            () -> AttachmentType.builder(() -> VampireData.forClan(Clan.random(RandomSource.create())))
                    .serialize(VampireData.CODEC)
                    .sync(VampireData.STREAM_CODEC)
                    .copyOnDeath()
                    .build());

    /* Ticks left before a player can frenzy again. */
    public static final Supplier<AttachmentType<Integer>> FRENZY_COOLDOWN = ATTACHMENT_TYPES.register(
            "frenzy_cooldown",
            () -> AttachmentType.builder(() -> 0).build());

    /* Hook our attachments up to the mod event bus. */
    public static void register(IEventBus eventBus) {
        ATTACHMENT_TYPES.register(eventBus);
    }
}
