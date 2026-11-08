package com.vtm.vampiretheminequerade.event;

import static com.vtm.vampiretheminequerade.Vampire.ModAttachments.VAMPIRE_DATA;
import static net.minecraft.world.effect.MobEffects.CONFUSION;
import static net.minecraft.world.effect.MobEffects.WITHER;

import java.util.List;
import java.util.Optional;

import com.vtm.vampiretheminequerade.VampireTheMinequerade;
import com.vtm.vampiretheminequerade.Vampire.VampireData;
import com.vtm.vampiretheminequerade.Vampire.BloodHandler;

import net.minecraft.core.Holder;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = VampireTheMinequerade.MODID)
public class VampireEvents {

    // ============================================================
    // CONSTANTS
    // ============================================================

    // Intervals based on a 20 TPS assumption.
    static final int VAMPIRE_TICK_INTERVAL = 5; // 1/4 second
    private static final int HUNGER_INTERVAL = VAMPIRE_TICK_INTERVAL * 240; // 1 minute
    static final int EFFECT_TICKS = 200; // 10 seconds

    static final List<Holder<MobEffect>> SUN_EFFECTS = List.of(WITHER, CONFUSION);
    private static final float FIRE_DAMAGE_MULTIPLIER = 2.0F;

    // ============================================================
    // PLAYER TICK
    // ============================================================

    /* Handle player tick events for vampirism. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {

        // ---------- Guards ----------
        Player player = event.getEntity();
        if (player.level().isClientSide()) { return; }
        if (player.tickCount % VAMPIRE_TICK_INTERVAL != 0) { return; }

        Optional<VampireData> existingData = player.getExistingData(VAMPIRE_DATA);
        if (existingData.isEmpty()) { return; }
        VampireData startData = existingData.get();
        VampireData vData = startData;

        vData = tickBloodLoss(player, vData);
        vData = FrenzyEvents.tickLowBlood(player, vData);
        vData = FrenzyEvents.tickFrenzy(player, vData);
        vData = FeedingEvents.tickFeeding(player, vData);
        tickFood(player);
        tickSun(player);

        if (!vData.equals(startData)) {
            player.setData(VAMPIRE_DATA, vData);
        }
    }

    // ---------- Blood loss ----------

    /* Drain the player's blood over time. */
    private static VampireData tickBloodLoss(Player player, VampireData vData) {
        if (player.tickCount % HUNGER_INTERVAL == 0) {
            return BloodHandler.chgBlood(vData, -1).orElse(vData);
        }
        return vData;
    }

    // ---------- Handle Food ----------

    /* Make the player sick for eating and keep their hunger pinned. */
    private static void tickFood(Player player) {
        // Vampires can eat, but it makes them sick.
        if (player.tickCount > VAMPIRE_TICK_INTERVAL && player.getFoodData().getFoodLevel() > BloodHandler.VAMPIRE_FOOD_LEVEL) {
            player.addEffect(new MobEffectInstance(CONFUSION, EFFECT_TICKS));
        }
        BloodHandler.pinFood(player);
    }

    // ---------- Sun ----------

    /* Punish the player for standing in the sun. */
    private static void tickSun(Player player) {
        Level level = player.level();
        boolean exposed = !player.isCreative()
                && !player.isSpectator()
                && level.dimensionType().hasSkyLight()
                && level.isDay()
                && level.canSeeSky(player.blockPosition());

        for (Holder<MobEffect> sunEffect : SUN_EFFECTS) {
            if (exposed) {
                if (!player.hasEffect(sunEffect)) {
                    player.addEffect(new MobEffectInstance(sunEffect, EFFECT_TICKS, 0, true, false, true));
                }
            } else {
                MobEffectInstance effect = player.getEffect(sunEffect);
                if (effect != null && effect.isAmbient()) {
                    player.removeEffect(sunEffect);
                }
            }
        }
    }

    // ============================================================
    // FIRE DAMAGE
    // ============================================================

    /* Make vampires take extra fire damage. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.hasData(VAMPIRE_DATA)) { return; }

        if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
            event.setAmount(event.getAmount() * FIRE_DAMAGE_MULTIPLIER);
        }
    }
}
