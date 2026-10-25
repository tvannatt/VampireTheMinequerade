package com.vtm.vampiretheminequerade.Vampire;

import java.util.function.Supplier;

import com.vtm.vampiretheminequerade.VampireTheMinequerade;

import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, VampireTheMinequerade.MODID);

    public static final Supplier<AttachmentType<VampireData>> VAMPIRE_DATA = ATTACHMENT_TYPES.register(
            "vampire_data",
            () -> AttachmentType.builder(() -> VampireData.forClan(Clan.random(RandomSource.create())))
                    .serialize(VampireData.CODEC)
                    .sync(VampireData.STREAM_CODEC)
                    .copyOnDeath()
                    .build());

    public static void register(IEventBus eventBus) {
        ATTACHMENT_TYPES.register(eventBus);
    }
}
