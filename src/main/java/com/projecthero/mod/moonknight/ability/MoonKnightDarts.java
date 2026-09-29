package com.projecthero.mod.moonknight.ability;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.data.MoonKnightAction;
import com.projecthero.mod.moonknight.entity.CrescentDartEntity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * R -- Crescent Darts (Moon Knight Phase 3).
 * <ul>
 *   <li><b>TAP</b>: one {@link CrescentDartEntity} ({@link MoonKnightConfig#DART_DAMAGE}, homing at night, boomerangs
 *       home on a miss). Cooldown {@link MoonKnightConfig#DART_COOLDOWN}.</li>
 *   <li><b>HOLD</b>: charge up to {@link MoonKnightConfig#DART_FAN_MAX_CHARGE} ({@code FLAG_CHARGING}, chargeKey 1), release
 *       to throw a fan of 3 (5 under a full moon). Charge sets the fan's speed and damage
 *       ({@link MoonKnightConfig#DART_FAN_MIN_CHARGE} .. 1). Cooldown {@link MoonKnightConfig#DART_FAN_COOLDOWN}.</li>
 *   <li><b>SNEAK+R</b>: Moon Mark -- a single dart that sticks in; the target glows and takes
 *       +{@link MoonKnightConfig#MOON_MARK_BONUS} from everything this player does to it while the mark lasts
 *       ({@link #outgoingFactor}). Cooldown {@link MoonKnightConfig#MOON_MARK_COOLDOWN}.</li>
 * </ul>
 * Damage, homing range, boomerang range and mark length are x lunar power; cooldowns / lunar power.
 */
public final class MoonKnightDarts implements MoonKnightMove {
	public static final MoonKnightDarts INSTANCE = new MoonKnightDarts();
	/** The HUD / pose slot number of R. */
	public static final int SLOT_NUMBER = 1;

	private record Mark(UUID owner, long until) {
	}

	/** Marked target UUID -> who marked it and until when. */
	private static final Map<UUID, Mark> MARKS = new ConcurrentHashMap<>();

	private MoonKnightDarts() {
	}

	// ---------------------------------------------------------------- TAP: one dart

	@Override
	public void tap(ServerPlayer player) {
		if (!MoonKnightAbilities.ready(player, "darts")) {
			return;
		}
		float power = MoonKnightAbilities.power(player);
		throwOne(player, aimDirection(player), MoonKnightConfig.DART_SPEED, MoonKnightConfig.DART_DAMAGE * power, 0);
		MoonKnightAnim.play(player, MoonKnightAnim.DART_THROW);
		MoonKnightAbilities.cooldown(player, "darts", MoonKnightConfig.DART_COOLDOWN);
		AbilityHelpers.sound(player, SoundEvents.TRIDENT_THROW, 0.6f, 1.6f);
	}

	// ---------------------------------------------------------------- HOLD: the fan

	@Override
	public void holdStart(ServerPlayer player) {
		if (!MoonKnightAbilities.ready(player, "darts_hold")) {
			return;
		}
		MoonKnightAction c = MoonKnightAnim.action(player).with(MoonKnightAction.FLAG_CHARGING, true);
		c.chargeKey = SLOT_NUMBER;
		c.chargeStart = player.level().getGameTime();
		MoonKnightAnim.save(player, c);
		AbilityHelpers.sound(player, SoundEvents.CROSSBOW_QUICK_CHARGE_1, 0.6f, 1.3f);
	}

	private static boolean charging(ServerPlayer player) {
		MoonKnightAction a = MoonKnightAnim.action(player);
		return a.has(MoonKnightAction.FLAG_CHARGING) && a.chargeKey == SLOT_NUMBER;
	}

	@Override
	public void holdTick(ServerPlayer player, int ticksHeld) {
		if (!charging(player)) {
			return;
		}
		int charged = ticksHeld - MoonKnightConfig.HOLD_THRESHOLD_TICKS;
		ServerLevel level = player.serverLevel();
		if (charged % 4 == 0 && charged <= MoonKnightConfig.DART_FAN_MAX_CHARGE) {
			Vec3 hand = AbilityHelpers.handPosition(player);
			level.sendParticles(MoonKnightCombat.MOON, hand.x, hand.y, hand.z, 2, 0.12, 0.12, 0.12, 0.0);
		}
		if (charged == MoonKnightConfig.DART_FAN_MAX_CHARGE) {
			AbilityHelpers.sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);
			Vec3 hand = AbilityHelpers.handPosition(player);
			level.sendParticles(ParticleTypes.END_ROD, hand.x, hand.y, hand.z, 8, 0.15, 0.15, 0.15, 0.03);
		}
	}

	@Override
	public void holdRelease(ServerPlayer player, int ticksHeld) {
		if (!charging(player)) {
			return;
		}
		endCharge(player);
		float charge = Math.min(1.0f, Math.max(0.0f,
				(ticksHeld - MoonKnightConfig.HOLD_THRESHOLD_TICKS) / (float) MoonKnightConfig.DART_FAN_MAX_CHARGE));
		throwFan(player, charge);
	}

	@Override
	public void cancelHold(ServerPlayer player) {
		if (charging(player)) {
			endCharge(player);
		}
	}

	private static void endCharge(ServerPlayer player) {
		MoonKnightAction c = MoonKnightAnim.action(player).with(MoonKnightAction.FLAG_CHARGING, false);
		c.chargeKey = 0;
		MoonKnightAnim.save(player, c);
	}

	/** Throw the fan: 3 darts, 5 under a full moon, spread evenly around the aim. Returns how many were thrown. */
	public static int throwFan(ServerPlayer player, float charge) {
		float power = MoonKnightAbilities.power(player);
		int count = MoonKnightAbilities.fullMoon(player) ? MoonKnightConfig.DART_FAN_COUNT_FULL_MOON : MoonKnightConfig.DART_FAN_COUNT;
		float scale = MoonKnightConfig.DART_FAN_MIN_CHARGE + (1.0f - MoonKnightConfig.DART_FAN_MIN_CHARGE) * charge;
		Vec3 aim = aimDirection(player);
		float spread = MoonKnightConfig.DART_FAN_SPREAD_DEGREES;
		for (int i = 0; i < count; i++) {
			float offset = (i - (count - 1) / 2.0f) * spread;
			Vec3 dir = aim.yRot((float) Math.toRadians(-offset));
			throwOne(player, dir, MoonKnightConfig.DART_SPEED * scale, MoonKnightConfig.DART_DAMAGE * power * scale, 0);
		}
		MoonKnightAnim.play(player, MoonKnightAnim.DART_FAN);
		MoonKnightAbilities.cooldown(player, "darts_hold", MoonKnightConfig.DART_FAN_COOLDOWN);
		AbilityHelpers.sound(player, SoundEvents.TRIDENT_THROW, 0.8f, 1.3f);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.6f);
		return count;
	}

	// ---------------------------------------------------------------- SNEAK: Moon Mark

	@Override
	public void sneak(ServerPlayer player) {
		if (!MoonKnightAbilities.ready(player, "darts_sneak")) {
			return;
		}
		float power = MoonKnightAbilities.power(player);
		int ticks = Math.round(MoonKnightConfig.MOON_MARK_TICKS * power);
		throwOne(player, aimDirection(player), MoonKnightConfig.DART_SPEED, MoonKnightConfig.DART_DAMAGE * power, ticks);
		MoonKnightAnim.play(player, MoonKnightAnim.MOON_MARK);
		MoonKnightAbilities.cooldown(player, "darts_sneak", MoonKnightConfig.MOON_MARK_COOLDOWN);
		AbilityHelpers.sound(player, SoundEvents.TRIDENT_THROW, 0.6f, 1.0f);
		AbilityHelpers.sound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6f, 1.4f);
	}

	/** Mark {@code target} for {@code ticks}: it glows, and {@code owner} deals it +30% from every source. */
	public static void applyMark(ServerPlayer owner, LivingEntity target, int ticks) {
		MARKS.put(target.getUUID(), new Mark(owner.getUUID(), owner.level().getGameTime() + ticks));
		target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false, true), owner);
		owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.moon_knight.marked",
				target.getDisplayName()).withStyle(net.minecraft.ChatFormatting.WHITE), true);
	}

	public static boolean isMarkedBy(ServerPlayer owner, LivingEntity target) {
		Mark m = MARKS.get(target.getUUID());
		return m != null && m.owner().equals(owner.getUUID()) && m.until() > owner.level().getGameTime();
	}

	/** Outgoing-damage multiplier from this key (Moon Mark: +30% against a target this player marked). */
	public static float outgoingFactor(ServerPlayer attacker, LivingEntity target) {
		return isMarkedBy(attacker, target) ? 1.0f + MoonKnightConfig.MOON_MARK_BONUS : 1.0f;
	}

	// ---------------------------------------------------------------- shared

	/** From the hand toward what the crosshair is on, so darts converge where the player aims. */
	private static Vec3 aimDirection(ServerPlayer player) {
		Vec3 hand = AbilityHelpers.handPosition(player);
		Vec3 aim = AbilityHelpers.aimPoint(player, MoonKnightConfig.DART_BOOMERANG_RANGE);
		Vec3 d = aim.subtract(hand);
		return d.lengthSqr() < 1.0 ? player.getLookAngle() : d.normalize();
	}

	private static CrescentDartEntity throwOne(ServerPlayer player, Vec3 dir, double speed, float damage, int markTicks) {
		float power = MoonKnightAbilities.power(player);
		boolean homing = MoonKnightAbilities.night(player);
		Vec3 hand = AbilityHelpers.handPosition(player);
		return CrescentDartEntity.throwDart(player, hand, dir, speed, damage, homing,
				MoonKnightConfig.DART_BOOMERANG_RANGE * power, markTicks);
	}

	@Override
	public void tick(ServerPlayer player) {
		if (player.level().getGameTime() % 100L == 0L && !MARKS.isEmpty()) {
			long now = player.level().getGameTime();
			MARKS.values().removeIf(m -> m.until() <= now);
		}
	}

	@Override
	public void onUntransform(ServerPlayer player) {
		cancelHold(player);
		MARKS.values().removeIf(m -> m.owner().equals(player.getUUID()));
	}

	public static void clearSessionState() {
		MARKS.clear();
	}
}
