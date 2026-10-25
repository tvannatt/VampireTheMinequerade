package com.vtm.vampiretheminequerade.Vampire;

import java.util.Optional;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

public class BloodHandler {

    public static final int VAMPIRE_FOOD_LEVEL = 17;

    /* Keep hunger in lockstep with blood. */
    public static void pinFood(Player player) {
        FoodData food = player.getFoodData();
        food.setFoodLevel(VAMPIRE_FOOD_LEVEL);
        food.setSaturation(0.0F);
        food.setExhaustion(0.0F);
    }
     
    /* Update the player's blood pool. */
    public static Optional<VampireData> chgBlood(VampireData vData, int amount) {
        if (amount < 0 && vData.blood() + amount < 0) { return Optional.empty(); } 
        if (amount >= 0 && vData.blood() >= VampireData.BLOOD_MAX) { return Optional.empty(); } 
        return Optional.of(vData.withBlood(vData.blood() + amount));
    }
}
