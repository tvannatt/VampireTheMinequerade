package com.vtm.vampiretheminequerade.event;

import java.util.List;
import java.util.Optional;

import com.vtm.vampiretheminequerade.VampireTheMinequerade;
import com.vtm.vampiretheminequerade.Vampire.ModAttachments;
import com.vtm.vampiretheminequerade.Vampire.VampireData;
import com.vtm.vampiretheminequerade.Vampire.BloodHandler;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = VampireTheMinequerade.MODID)
public class VampireEvents {

    private static final int HUNGER_INTERVAL = 1200; // 1 minute
    private static final int SUN_INTERVAL = 5; // 1/4 second
    private static final int SUN_EFFECT_TICKS = 100;
    private static final int FOOD_NAUSEA_TICKS = 200;
    private static final List<Holder<MobEffect>> SUN_EFFECTS = List.of(MobEffects.WITHER, MobEffects.CONFUSION);

    /* Handle player tick events for vampirism. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) { return; }

        // Determine when we should run hunger and sun checks.
        boolean hungerTick = player.tickCount % HUNGER_INTERVAL == 0;
        boolean sunTick = player.tickCount % SUN_INTERVAL == 0;
        if (!hungerTick && !sunTick) { return; }

        Optional<VampireData> vData = player.getExistingData(ModAttachments.VAMPIRE_DATA);
        if (vData.isEmpty()) { return; }

        // Handle blood loss.
        if (hungerTick) {
            BloodHandler.chgBlood(vData.get(), -1)
                    .ifPresent(newData -> player.setData(ModAttachments.VAMPIRE_DATA, newData));
        }

        // Handle sun damage (we're using wither because it's easy.)
        if (sunTick) {
            // Vampires can eat, but it makes them sick. 
            if (player.tickCount > SUN_INTERVAL && player.getFoodData().getFoodLevel() > BloodHandler.VAMPIRE_FOOD_LEVEL) {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, FOOD_NAUSEA_TICKS));
            }

            // We do this in sunTick since they happen more often. Doing this in hungerTick would stop players from sprinting occasionally.
            BloodHandler.pinFood(player);

            Level level = player.level();
            boolean exposed = !player.isCreative()
                    && !player.isSpectator()
                    && level.dimensionType().hasSkyLight()
                    && level.isDay()
                    && level.canSeeSky(player.blockPosition());

            // Add punishiment for being in the sun.
            for (Holder<MobEffect> sunEffect : SUN_EFFECTS) {
                if (exposed) {
                    if (!player.hasEffect(sunEffect)) {
                        player.addEffect(new MobEffectInstance(sunEffect, SUN_EFFECT_TICKS, 0, true, false, true));
                    }
                } else {
                    MobEffectInstance effect = player.getEffect(sunEffect);
                    if (effect != null && effect.isAmbient()) {
                        player.removeEffect(sunEffect);
                    }
                }
            }
        }
    }
}
