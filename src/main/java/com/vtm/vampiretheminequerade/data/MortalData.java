package com.vtm.vampiretheminequerade.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * Per-entity mortal state: This really exists just to provide mobs with a blood value.
 * <p>
 * @param blood         the mob/player's remaining blood resource
 * @param lastUpdate    the game time the blood was last changed
 * </p>
 */
public record MortalData(int blood, long lastUpdate) {
    public static final int BLOOD_MULT = 5;
    public static final int BLOOD_MAX = 100;
    public static final int BLOOD_MIN = 0;

    public MortalData{
        blood = Mth.clamp(blood, BLOOD_MIN, BLOOD_MAX);
    }
    
    public static final Codec<MortalData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("blood").forGetter(MortalData::blood),
            Codec.LONG.optionalFieldOf("last_update", 0L).forGetter(MortalData::lastUpdate)
    ).apply(i, MortalData::new));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, MortalData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MortalData::blood,
            ByteBufCodecs.VAR_LONG, MortalData::lastUpdate,
            MortalData::new
    );

    public MortalData withBlood(int blood, long lastUpdate) {
        return new MortalData(blood, lastUpdate);
    }
}
