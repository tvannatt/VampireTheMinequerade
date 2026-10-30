package com.vtm.vampiretheminequerade.Vampire;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Per-player vampire state: clan, blood, and known disciplines.
 * <p>
 * Immutable. To change a value, use a {@code with...} method and store the returned
 * copy on the player.
 *
 * @param clan        the player's clan, as a namespaced ID
 * @param blood         the player's current blood, from {@link #BLOOD_MIN} to {@link #BLOOD_MAX}
 * @param bloodPotency  the player's blood potency, from {@link #POTENCY_MIN} to {@link #POTENCY_MAX}
 * @param disciplines   the IDs of the disciplines the player knows
 */
public record VampireData(ResourceLocation clan, int blood, int bloodPotency, Set<ResourceLocation> disciplines, int frenzyTicks) {

    // Defaults for ALL vampires. 
    public static final int BLOOD_MIN = 0;
    public static final int BLOOD_MAX = 100;
    public static final int POTENCY_MIN = 1;
    public static final int POTENCY_MAX = 5;

    /** Clamps blood and blood potency into their valid ranges on every NEW. */
    public VampireData {
        blood = Mth.clamp(blood, BLOOD_MIN, BLOOD_MAX);
        bloodPotency = Mth.clamp(bloodPotency, POTENCY_MIN, POTENCY_MAX);
        frenzyTicks = Math.max(frenzyTicks, 0);
    }

    public boolean isFrenzied() {
        return frenzyTicks > 0;
    }

    /** Factory for a new clan. Clans have a default of 3 disciplines - this handles that.**/
    public static VampireData forClan(Clan clan) {
        return new VampireData(clan.id(), BLOOD_MAX, POTENCY_MIN, clan.startingDisciplines(), 0);
    }

    /* The stuff below is all boilerplate. It's telling NeoForge what to do with this record. */

    /** Disk serialization. **/
    public static final Codec<VampireData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("clan").forGetter(VampireData::clan),
            Codec.INT.fieldOf("blood").forGetter(VampireData::blood),
            Codec.INT.fieldOf("blood_potency").forGetter(VampireData::bloodPotency),
            ResourceLocation.CODEC.listOf().xmap(Set::copyOf, List::copyOf)
                    .fieldOf("disciplines").forGetter(VampireData::disciplines),
            Codec.INT.optionalFieldOf("frenzy_ticks", 0).forGetter(VampireData::frenzyTicks)
    ).apply(i, VampireData::new));

    /** Network serialization. */
    public static final StreamCodec<RegistryFriendlyByteBuf, VampireData> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, VampireData::clan,
            ByteBufCodecs.VAR_INT, VampireData::blood,
            ByteBufCodecs.VAR_INT, VampireData::bloodPotency,
            ByteBufCodecs.collection(HashSet::new, ResourceLocation.STREAM_CODEC)
                    .map(Set::copyOf, HashSet::new),
            VampireData::disciplines,
            ByteBufCodecs.VAR_INT, VampireData::frenzyTicks,
            VampireData::new);
    
     /* SETTERS */

     /** Returns a copy with the clan replaced. 
      * 
      * WARNING: THIS SHOULD ONLY EVER BE CALLED IN THE COMMAND PATH.
      * 
      * **/
     public VampireData withClan(ResourceLocation clan) {
        return new VampireData(clan, this.blood, this.bloodPotency, this.disciplines, this.frenzyTicks);
     }

     /** Returns a copy with the maximum blood value. **/
     public VampireData withMaxBlood() {
        return new VampireData(this.clan, BLOOD_MAX, this.bloodPotency, this.disciplines, this.frenzyTicks);
     }
     /** Returns a copy with the blood value replaced. **/
     public VampireData withBlood(int blood) {
        return new VampireData(this.clan, blood, this.bloodPotency, this.disciplines, this.frenzyTicks);
     }

     /** Returns a copy with the blood potency replaced. **/
     public VampireData withBloodPotency(int bloodPotency) {
        return new VampireData(this.clan, this.blood, bloodPotency, this.disciplines, this.frenzyTicks);
     }

     /** Returns a copy with the discipline set replaced. **/
     public VampireData withDisciplines(Set<ResourceLocation> disciplines) {
        return new VampireData(this.clan, this.blood, this.bloodPotency, disciplines, this.frenzyTicks);
     }

     public VampireData withFrenzyTicks(int frenzyTicks) {
        return new VampireData(this.clan, this.blood, this.bloodPotency, this.disciplines, frenzyTicks);
     }



    
}
