package com.vtm.vampiretheminequerade.event;

import static com.vtm.vampiretheminequerade.Vampire.ModAttachments.FRENZY_COOLDOWN;
import static com.vtm.vampiretheminequerade.Vampire.ModAttachments.VAMPIRE_DATA;
import static com.vtm.vampiretheminequerade.event.VampireEvents.EFFECT_TICKS;
import static com.vtm.vampiretheminequerade.event.VampireEvents.SUN_EFFECTS;
import static com.vtm.vampiretheminequerade.event.VampireEvents.VAMPIRE_TICK_INTERVAL;
import static net.minecraft.world.effect.MobEffects.DAMAGE_BOOST;
import static net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN;
import static net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN;
import static net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED;
import static net.minecraft.world.effect.MobEffects.WEAKNESS;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.vtm.vampiretheminequerade.VampireTheMinequerade;
import com.vtm.vampiretheminequerade.Vampire.VampireData;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = VampireTheMinequerade.MODID)
public class FrenzyEvents {

    // ============================================================
    // CONSTANTS
    // ============================================================

    private static final int FRENZY_INTERVAL = VAMPIRE_TICK_INTERVAL * 120; // 30 seconds
    private static final int FRENZY_DURATION_TICKS = 600; // 30 seconds
    private static final int CRASH_TICKS = 1200; // 1 minute
    private static final int FRENZY_COOLDOWN_TICKS = 1200; // 1 minute
    private static final int BLOOD_THRESHOLD = 20;
    private static final int RESPAWN_BLOOD = 50;
    private static final Map<Holder<MobEffect>, Integer> FRENZY_BUFFS = Map.of(DAMAGE_BOOST, 3, MOVEMENT_SPEED, 1);
    private static final List<Holder<MobEffect>> CRASH_EFFECTS = List.of(WEAKNESS, MOVEMENT_SLOWDOWN, DIG_SLOWDOWN);
    private static final float CRASH_HEALTH = 2.0F; // one heart
    private static final int FRENZY_TITLE_FADE_IN = 10; // 1/2 second
    private static final int FRENZY_TITLE_STAY = 40; // 2 seconds
    private static final int FRENZY_TITLE_FADE_OUT = 20; // 1 second
    private static final int HEARTBEAT_SLOW_TICKS = 20; // 1 second
    private static final int HEARTBEAT_MEDIUM_TICKS = 15;
    private static final int HEARTBEAT_FAST_TICKS = 10;

    // ============================================================
    // RESET FRENZY ON RESPAWN
    // ============================================================

    /* Reset blood and frenzy when a vampire respawns. */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered()) { return; }

        Player player = event.getEntity();
        Optional<VampireData> existingData = player.getExistingData(VAMPIRE_DATA);
        if (existingData.isEmpty()) { return; }

        player.setData(VAMPIRE_DATA, existingData.get().withBlood(RESPAWN_BLOOD).withFrenzyTicks(0));
    }

    // ============================================================
    // FRENZY TICK
    // ============================================================

    /* Low blood: weakness and frenzy roll. */
    static VampireData tickLowBlood(Player player, VampireData vData) {
        if (vData.blood() < BLOOD_THRESHOLD && !vData.isFrenzied()) {
            if (!player.hasEffect(WEAKNESS)) {
                player.addEffect(new MobEffectInstance(WEAKNESS, EFFECT_TICKS, 0, true, false, true));
            }
            // Frenzy roll.
            if (player.tickCount % FRENZY_INTERVAL == 0 && player.getData(FRENZY_COOLDOWN) <= 0 && shouldFrenzy(player, vData.blood())) {
                vData = vData.withFrenzyTicks(FRENZY_DURATION_TICKS);
                if (player instanceof ServerPlayer serverPlayer) {
                    // Dramatic effects on frenzy.
                    serverPlayer.connection.send(new ClientboundSetTitlesAnimationPacket(FRENZY_TITLE_FADE_IN, FRENZY_TITLE_STAY, FRENZY_TITLE_FADE_OUT));
                    serverPlayer.connection.send(new ClientboundSetTitleTextPacket(
                            Component.translatable("message.vampiretheminequerade.frenzy").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
                    serverPlayer.playNotifySound(SoundEvents.WOLF_GROWL, SoundSource.PLAYERS, 1.0F, 0.5F);
                }
            }
        } else {
            MobEffectInstance effect = player.getEffect(WEAKNESS);
            if (effect != null && effect.isAmbient()) {
                player.removeEffect(WEAKNESS);
            }
        }
        return vData;
    }

    /* Active frenzy. */
    static VampireData tickFrenzy(Player player, VampireData vData) {
        if (vData.isFrenzied()) {
            int remaining = vData.frenzyTicks() - VAMPIRE_TICK_INTERVAL;
            vData = vData.withFrenzyTicks(remaining);

            int heartbeat = HEARTBEAT_FAST_TICKS;
            if (remaining > FRENZY_DURATION_TICKS * 2 / 3) {
                heartbeat = HEARTBEAT_SLOW_TICKS;
            } else if (remaining > FRENZY_DURATION_TICKS / 3) {
                heartbeat = HEARTBEAT_MEDIUM_TICKS;
            }
            if (player.tickCount % heartbeat == 0) {
                player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 1.0F);
            }

            FRENZY_BUFFS.forEach((buff, amplifier) -> {
                if (!player.hasEffect(buff)) {
                    player.addEffect(new MobEffectInstance(buff, EFFECT_TICKS, amplifier, true, false, true));
                }
            });

            if (remaining <= 0) {
                frenzyCrash(player);
            }
        } else if (player.getData(FRENZY_COOLDOWN) > 0) {
            player.setData(FRENZY_COOLDOWN, player.getData(FRENZY_COOLDOWN) - VAMPIRE_TICK_INTERVAL);
        }
        return vData;
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

    /* Remove frenzy from player + consequences.*/
    private static void frenzyCrash(Player player) {
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
        player.playNotifySound(SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.0F, 0.6F);
        player.setData(FRENZY_COOLDOWN, FRENZY_COOLDOWN_TICKS);
    }
}
