package com.vtm.vampiretheminequerade.event;

import static com.vtm.vampiretheminequerade.Vampire.ModAttachments.FRENZY_COOLDOWN;
import static com.vtm.vampiretheminequerade.Vampire.ModAttachments.VAMPIRE_DATA;
import static net.minecraft.world.effect.MobEffects.CONFUSION;
import static net.minecraft.world.effect.MobEffects.DAMAGE_BOOST;
import static net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN;
import static net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN;
import static net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED;
import static net.minecraft.world.effect.MobEffects.WEAKNESS;
import static net.minecraft.world.effect.MobEffects.WITHER;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.vtm.vampiretheminequerade.VampireTheMinequerade;
import com.vtm.vampiretheminequerade.Vampire.VampireData;
import com.vtm.vampiretheminequerade.Vampire.BloodHandler;

import net.minecraft.core.Holder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

    // ---------- Timing ----------

    // Intervals based on a 20 TPS assumption.
    private static final int VAMPIRE_TICK_INTERVAL = 5; // 1/4 second
    private static final int HUNGER_INTERVAL = VAMPIRE_TICK_INTERVAL * 240; // 1 minute
    private static final int FRENZY_INTERVAL = VAMPIRE_TICK_INTERVAL * 120; // 30 seconds
    private static final int EFFECT_TICKS = 200; // 10 seconds
    private static final int FRENZY_DURATION_TICKS = 600; // 30 seconds
    private static final int CRASH_TICKS = 1200; // 1 minute
    private static final int FRENZY_COOLDOWN_TICKS = 1200; // 1 minute

    // ---------- Blood ----------

    private static final int BLOOD_THRESHOLD = 20;
    private static final int RESPAWN_BLOOD = 50;

    // ---------- Effects ----------

    private static final List<Holder<MobEffect>> SUN_EFFECTS = List.of(WITHER, CONFUSION);
    private static final Map<Holder<MobEffect>, Integer> FRENZY_BUFFS = Map.of(DAMAGE_BOOST, 3, MOVEMENT_SPEED, 1);
    private static final List<Holder<MobEffect>> CRASH_EFFECTS = List.of(WEAKNESS, MOVEMENT_SLOWDOWN, DIG_SLOWDOWN);
    private static final float CRASH_HEALTH = 2.0F; // one heart
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

        if (player.tickCount % VAMPIRE_TICK_INTERVAL != 0) { return; } // Quit early if a vampire event shouldn't happen yet.

        Optional<VampireData> existingData = player.getExistingData(VAMPIRE_DATA);
        if (existingData.isEmpty()) { return; }
        VampireData vData = existingData.get();

        // ---------- Blood loss ----------

        if (player.tickCount % HUNGER_INTERVAL == 0) {
            BloodHandler.chgBlood(vData, -1)
                    .ifPresent(newData -> player.setData(VAMPIRE_DATA, newData));
        }

        // ---------- Low blood: weakness and frenzy roll ----------

        // Apply weakness and frenzy risk on low hunger.
        if (vData.blood() < BLOOD_THRESHOLD && !vData.isFrenzied()) {
            if (!player.hasEffect(WEAKNESS)) {
                player.addEffect(new MobEffectInstance(WEAKNESS, EFFECT_TICKS, 0, true, false, true));
            }
            // Frenzy path.
            if (player.tickCount % FRENZY_INTERVAL == 0 && player.getData(FRENZY_COOLDOWN) <= 0 && shouldFrenzy(player, vData.blood())) {
                player.setData(VAMPIRE_DATA, player.getData(VAMPIRE_DATA).withFrenzyTicks(FRENZY_DURATION_TICKS));
                player.displayClientMessage(Component.translatable("message.vampiretheminequerade.frenzy").withStyle(ChatFormatting.DARK_RED), true);
            }
        } else {
            MobEffectInstance effect = player.getEffect(WEAKNESS);
            if (effect != null && effect.isAmbient()) {
                player.removeEffect(WEAKNESS);
            }
        }

        // ---------- Active frenzy ----------

        VampireData vState = player.getData(VAMPIRE_DATA);
        if (vState.isFrenzied()) {
            int remaining = vState.frenzyTicks() - VAMPIRE_TICK_INTERVAL;
            player.setData(VAMPIRE_DATA, vState.withFrenzyTicks(remaining));
            FRENZY_BUFFS.forEach((buff, amplifier) -> {
                if (!player.hasEffect(buff)) {
                    player.addEffect(new MobEffectInstance(buff, EFFECT_TICKS, amplifier, true, false, true));
                }
            });
            // End frenzy.
            if (remaining <= 0) {
                frenzyCrash(player);
            }
        } else if (player.getData(FRENZY_COOLDOWN) > 0) {
            player.setData(FRENZY_COOLDOWN, player.getData(FRENZY_COOLDOWN) - VAMPIRE_TICK_INTERVAL);
        }

        // ---------- Handle Food ----------

        // Vampires can eat, but it makes them sick.
        if (player.tickCount > VAMPIRE_TICK_INTERVAL && player.getFoodData().getFoodLevel() > BloodHandler.VAMPIRE_FOOD_LEVEL) {
            player.addEffect(new MobEffectInstance(CONFUSION, EFFECT_TICKS));
        }
        BloodHandler.pinFood(player);

        // ---------- Sun ----------

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
    // RESET FRENZY ON RESPAWN
    // ============================================================

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered()) { return; }

        Player player = event.getEntity();
        Optional<VampireData> existingData = player.getExistingData(VAMPIRE_DATA);
        if (existingData.isEmpty()) { return; }

        player.setData(VAMPIRE_DATA, existingData.get().withBlood(RESPAWN_BLOOD).withFrenzyTicks(0));
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

    // ============================================================
    // FRENZY HELPERS
    // ============================================================

    /* Emulate a V5 willpower roll for frenzy. */
    private static boolean shouldFrenzy(Player player, int blood) {
        int difficulty = 1 + (BLOOD_THRESHOLD - blood) * (5 - 1) / BLOOD_THRESHOLD;
        int successes = 0;
        for (int i = 0; i < 5; i++) {
            if (player.getRandom().nextInt(10) + 1 >= 6 ) { successes++; }
        }
        return successes < difficulty;
    }

    /* Grant consequences to the player for the frenzy. */
    public static void clearVampireEffects(Player player) {
        List<Holder<MobEffect>> effects = new ArrayList<>(SUN_EFFECTS);
        effects.addAll(FRENZY_BUFFS.keySet());
        effects.addAll(CRASH_EFFECTS);
        for (Holder<MobEffect> effect : effects) {
            MobEffectInstance instance = player.getEffect(effect);
            if (instance != null && instance.isAmbient()) {
                player.removeEffect(effect);
            }
        }
    }

    private static void frenzyCrash(Player player) {
        // Remove the frenzy buffs from the player.
        for (Holder<MobEffect> buff : FRENZY_BUFFS.keySet()) {
            MobEffectInstance effect = player.getEffect(buff);
            if (effect != null && effect.isAmbient()) {
                player.removeEffect(buff);
            }
        }
        // Add the frenzy drawbacks.
        for (Holder<MobEffect> crash : CRASH_EFFECTS) {
            player.addEffect(new MobEffectInstance(crash, CRASH_TICKS, 1, true, false, true));
        }
        player.setHealth(Math.min(player.getHealth(), CRASH_HEALTH));
        player.setData(FRENZY_COOLDOWN, FRENZY_COOLDOWN_TICKS);
    }
}
