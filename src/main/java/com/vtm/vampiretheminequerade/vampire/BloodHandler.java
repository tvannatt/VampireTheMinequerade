package com.vtm.vampiretheminequerade.vampire;

import java.util.Optional;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import com.vtm.vampiretheminequerade.data.VampireData;
import com.vtm.vampiretheminequerade.data.MortalData;
import com.vtm.vampiretheminequerade.registry.ModAttachments;

public class BloodHandler {

    public static final int VAMPIRE_FOOD_LEVEL = 17;
    public static final int REGEN_TICKS_PER_POINT = 60; // 3 seconds

    public static void pinFood(Player player) {
        FoodData food = player.getFoodData();
        food.setFoodLevel(VAMPIRE_FOOD_LEVEL);
        food.setSaturation(0.0F);
        food.setExhaustion(0.0F);
    }
     
    public static Optional<VampireData> chgBlood(VampireData vData, int amount) {
        if (amount < 0 && vData.blood() + amount < 0) { return Optional.empty(); } 
        if (amount >= 0 && vData.blood() >= VampireData.BLOOD_MAX) { return Optional.empty(); } 
        return Optional.of(vData.withBlood(vData.blood() + amount));
    }

    public static int maxMortalBlood(LivingEntity entity) {
        return Mth.clamp(Math.round(entity.getMaxHealth() * MortalData.BLOOD_MULT), 1, MortalData.BLOOD_MAX);
    }

    public static MortalData withRegen(LivingEntity entity, long gameTime) {
        int max = maxMortalBlood(entity);
        Optional<MortalData> existing = entity.getExistingData(ModAttachments.MORTAL_DATA);
        if (existing.isEmpty()) { return new MortalData(max, gameTime); }

        MortalData data = existing.get();
        long regen = Math.max(0, gameTime - data.lastUpdate()) / REGEN_TICKS_PER_POINT;
        return data.withBlood((int) Math.min(max, data.blood() + regen), gameTime);
    }

    public static int drainMortal(LivingEntity entity, int amount, long gameTime) {
        MortalData data = withRegen(entity, gameTime);
        int taken = Math.min(amount, data.blood());
        entity.setData(ModAttachments.MORTAL_DATA, data.withBlood(data.blood() - taken, gameTime));
        return taken;
    }

    public static boolean isDrained(LivingEntity entity) {
        return entity.getExistingData(ModAttachments.MORTAL_DATA).map(data -> data.blood() <= 0).orElse(false);
    }
}
