package com.vtm.vampiretheminequerade.event;

import static com.vtm.vampiretheminequerade.Vampire.ModAttachments.FEEDING;
import static com.vtm.vampiretheminequerade.Vampire.ModAttachments.VAMPIRE_DATA;
import static net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN;

import java.util.Set;

import com.vtm.vampiretheminequerade.VampireTheMinequerade;
import com.vtm.vampiretheminequerade.Vampire.BloodHandler;
import com.vtm.vampiretheminequerade.Vampire.FeedingState;
import com.vtm.vampiretheminequerade.Vampire.VampireData;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

@EventBusSubscriber(modid = VampireTheMinequerade.MODID)
public class FeedingEvents {

    // ============================================================
    // CONSTANTS
    // ============================================================

    private static final int FEED_PULSE_TICKS = 20; // 1 second
    private static final int FEED_PULSE_AMOUNT = 10;
    private static final double FEED_REACH_SQ = 16.0; // 4 blocks, squared
    private static final ResourceLocation FEED_LOCK_ID = ResourceLocation.fromNamespaceAndPath(VampireTheMinequerade.MODID, "feed_lock");
    private static final AttributeModifier FEED_LOCK = new AttributeModifier(FEED_LOCK_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final double FEED_MOVE_TOLERANCE = 0.5; // blocks
    private static final double FEED_LOOK_COS = 0.75; // ~40 degrees
    private static final int FEED_LOCK_TICKS = 40; // 2 seconds, refreshed while feeding
    private static final int FEED_LOCK_AMPLIFIER = 10; // high value to root mobs
    private static final TagKey<EntityType<?>> BLOODLESS =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(VampireTheMinequerade.MODID, "bloodless"));

    // ============================================================
    // FEEDING
    // ============================================================

    /* Start feeding on a punch, or cancel it on a second punch. */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        Entity target = event.getTarget();

        if (player.level().isClientSide() || !player.hasData(VAMPIRE_DATA)) { return; }
        if (player.getData(FEEDING).isFeeding()) {
            // Cancel
            if (target.getUUID().equals(player.getData(FEEDING).victim())) {
                event.setCanceled(true);
                if (!player.getData(VAMPIRE_DATA).isFrenzied()) { breakFeeding(player); }
            }
            return;
        }
        if (!player.getMainHandItem().isEmpty() || !player.isShiftKeyDown()) { return; }

        // This does allow modded (potentially non-fleshy) mobs to be fed on, but meh. Rule of cool I guess.
        if (!(target instanceof LivingEntity victim) || target.getType().is(BLOODLESS)) { return; }
        if (!player.canInteractWithEntity(target, 1D)) { return; }
        if (BloodHandler.currentMortalData(victim, player.level().getGameTime()).blood() <= 0) { return; }

        event.setCanceled(true);
        player.setData(FEEDING, new FeedingState(victim.getUUID(), player.position()));
        lockVictim(victim);
    }

    /* Cancel feeding when the feeder takes damage. */
    @SubscribeEvent
    public static void onFeederDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Player player) || !player.hasData(VAMPIRE_DATA)) {return;}
        if (player.getData(FEEDING).isFeeding() && !player.getData(VAMPIRE_DATA).isFrenzied()) {
            breakFeeding(player);
        }
    }

    // ============================================================
    // FEEDING TICK
    // ============================================================

    /* Advance an in-progress feed. */
    static VampireData tickFeeding(Player player, VampireData vData) {
        FeedingState fState = player.getData(FEEDING);
        if (!fState.isFeeding()) { return vData; }

        LivingEntity victim = getVictim(player);

        if (victim == null || !victim.isAlive()) {
            // Cancel feed if the victim dies.
            breakFeeding(player);
            return vData;
        }

        if (player.distanceToSqr(victim) > FEED_REACH_SQ) {
            breakFeeding(player);
            return vData;
        }

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (vData.isFrenzied()) {
            if (!speed.hasModifier(FEED_LOCK_ID)) { speed.addTransientModifier(FEED_LOCK); }

            Vec3 lookDir = getLookDir(player, victim);
            // Lock the player's cam on mobs when frenzied.
            if (player.getViewVector(1.0F).dot(lookDir) < FEED_LOOK_COS && player instanceof ServerPlayer serverPlayer) {
                float yaw = (float) (Mth.atan2(lookDir.z, lookDir.x) * Mth.RAD_TO_DEG) - 90.0F;
                float pitch = (float) -(Mth.atan2(lookDir.y, Math.sqrt(lookDir.x * lookDir.x + lookDir.z * lookDir.z)) * Mth.RAD_TO_DEG);
                serverPlayer.connection.teleport(0, 0, 0, yaw, pitch, Set.of(RelativeMovement.X, RelativeMovement.Y, RelativeMovement.Z));
            }
        } else {
            speed.removeModifier(FEED_LOCK_ID);
            if (isContactBroken(player, fState, victim)) {
                breakFeeding(player);
                return vData;
            }
        }

        if (player.tickCount % FEED_PULSE_TICKS == 0) {
            int taken = BloodHandler.drainMortal(victim, FEED_PULSE_AMOUNT, player.level().getGameTime());
            vData = BloodHandler.chgBlood(vData, taken).orElse(vData);
            if (BloodHandler.isDrained(victim)) {
                breakFeeding(player);
                finishVictim(player, victim);
                return vData;
            }
        }
        lockVictim(victim);
        return vData;
    }

    /* Whether the player has moved away from or looked away from the victim. */
    private static boolean isContactBroken(Player player, FeedingState fState, LivingEntity victim) {
        if (player.position().distanceToSqr(fState.startPos()) > FEED_MOVE_TOLERANCE * FEED_MOVE_TOLERANCE) {
            // Cancel feed if we move.
            return true;
        }

        double dp = player.getViewVector(1.0F).dot(getLookDir(player, victim));
        // Break feeding if the player looks away.
        return dp < FEED_LOOK_COS;
    }

    /* The direction from the player's eyes to the center of the victim. */
    private static Vec3 getLookDir(Player player, LivingEntity victim) {
        return victim.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
    }

    // ============================================================
    // FEEDING HELPERS
    // ============================================================

    /* Keep the feeding victim from moving. */
    public static void lockVictim(LivingEntity victim) {
        victim.addEffect(new MobEffectInstance(MOVEMENT_SLOWDOWN, FEED_LOCK_TICKS, FEED_LOCK_AMPLIFIER, true, false, false));
    }

    /* Kill a drained victim. Players are left at 1 health. */
    private static void finishVictim(Player player, LivingEntity victim) {
        if (victim instanceof Player) {
            victim.setHealth(Math.min(victim.getHealth(), 1.0F));
            return;
        }
        victim.hurt(player.damageSources().playerAttack(player), Float.MAX_VALUE);
    }

    /* The player's current feeding victim, if one exists. */
    private static LivingEntity getVictim(Player player) {
        FeedingState feeding = player.getData(FEEDING);
        if (!(player.level() instanceof ServerLevel serverLevel)) { return null; }
        return serverLevel.getEntity(feeding.victim()) instanceof LivingEntity victim ? victim : null;
    }

    /* Cancel the feeding process. */
    public static void breakFeeding(Player player) {
        player.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(FEED_LOCK_ID);
        LivingEntity victim = getVictim(player);
        if (victim != null) {
            MobEffectInstance effect = victim.getEffect(MOVEMENT_SLOWDOWN);
            if (effect != null && effect.isAmbient()) {
                victim.removeEffect(MOVEMENT_SLOWDOWN);
            }
        }
        player.setData(FEEDING, FeedingState.NONE);
    }
}
