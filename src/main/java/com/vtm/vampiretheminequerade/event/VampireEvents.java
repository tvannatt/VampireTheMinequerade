package com.vtm.vampiretheminequerade.event;

import static com.vtm.vampiretheminequerade.registry.ModAttachments.FEEDING;
import static com.vtm.vampiretheminequerade.registry.ModAttachments.VAMPIRE_DATA;
import static net.minecraft.world.effect.MobEffects.CONFUSION;
import static net.minecraft.world.effect.MobEffects.WITHER;

import java.util.List;
import java.util.Optional;

import com.vtm.vampiretheminequerade.VampireTheMinequerade;
import com.vtm.vampiretheminequerade.data.FeedingState;
import com.vtm.vampiretheminequerade.data.VampireData;
import com.vtm.vampiretheminequerade.vampire.BloodHandler;

import net.minecraft.core.Holder;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
    private static final int RESPAWN_BLOOD = 50;
    static final List<Holder<MobEffect>> SUN_EFFECTS = List.of(WITHER, CONFUSION);
    private static final float FIRE_DAMAGE_MULTIPLIER = 2.0F;

    // ============================================================
    // LOGOUT
    // ============================================================

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        Optional<VampireData> existingData = player.getExistingData(VAMPIRE_DATA);

        if (existingData.isEmpty()) { return; }
        FeedingEvents.breakFeeding(player);
    }


    // ============================================================
    // RESPAWN
    // ============================================================

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered()) { return; }

        Player player = event.getEntity();
        Optional<VampireData> existingData = player.getExistingData(VAMPIRE_DATA);
        if (existingData.isEmpty()) { return; }

        FeedingEvents.breakFeeding(player);
        player.setData(VAMPIRE_DATA, existingData.get().withBlood(RESPAWN_BLOOD).withFrenzyTicks(0));
    }

    // ============================================================
    // PLAYER TICK
    // ============================================================

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

    private static VampireData tickBloodLoss(Player player, VampireData vData) {
        if (player.tickCount % HUNGER_INTERVAL == 0) {
            return BloodHandler.chgBlood(vData, -1).orElse(vData);
        }
        return vData;
    }

    // ---------- Handle Food ----------

    private static void tickFood(Player player) {
        // Vampires can eat, but it makes them sick.
        if (player.tickCount > VAMPIRE_TICK_INTERVAL && player.getFoodData().getFoodLevel() > BloodHandler.VAMPIRE_FOOD_LEVEL) {
            player.addEffect(new MobEffectInstance(CONFUSION, EFFECT_TICKS));
        }
        BloodHandler.pinFood(player);
    }

    // ---------- Sun ----------

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

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.hasData(VAMPIRE_DATA)) { return; }

        if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
            event.setAmount(event.getAmount() * FIRE_DAMAGE_MULTIPLIER);
        }
    }
}
