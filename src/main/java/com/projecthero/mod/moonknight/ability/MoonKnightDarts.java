package com.projecthero.mod.moonknight.ability;

import java.util.Comparator;
import java.util.List;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * R -- Crescent Darts (Moon Knight Phase 3).
 * <ul>
 *   <li><b>TAP</b>: one {@link CrescentDartEntity} ({@link MoonKnightConfig#DART_DAMAGE}, homing at night, boomerangs
 *       home on a miss). Cooldown {@link MoonKnightConfig#DART_COOLDOWN}.</li>
 *   <li><b>HOLD</b> Crescent Fan (v0.14.4): as soon as the hold registers, five darts fly, each locked on to one of the
 *       five closest hostiles ({@link #fanTargets}, the Green Lantern Missile Barrage's targeting) for a normal dart's
 *       damage. Cooldown {@link MoonKnightConfig#DART_FAN_COOLDOWN}.</li>
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

	/**
	 * v0.14.4: the fan no longer charges -- the moment the hold registers (0.5 s) the five darts fly, each locked on to
	 * one of the five closest hostiles (see {@link #throwFan}).
	 */
	@Override
	public void holdStart(ServerPlayer player) {
		if (!MoonKnightAbilities.ready(player, "darts_hold")) {
			return;
		}
		throwFan(player);
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
		if (charging(player)) {
			endCharge(player); // (nothing charges since v0.14.4; a stale flag from an older session is simply dropped)
		}
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

	/**
	 * The Crescent Fan's targets (v0.14.4): the {@link MoonKnightConfig#DART_FAN_COUNT} closest hostile mobs (or mobs
	 * hunting him) within {@link MoonKnightConfig#DART_FAN_TARGET_RANGE} that he can see -- never himself, a
	 * squad-mate or his own pet (the Green Lantern Missile Barrage's targeting, without the in-front-of-you cone).
	 */
	public static List<LivingEntity> fanTargets(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		return AbilityHelpers.living(player.serverLevel(), eye, MoonKnightConfig.DART_FAN_TARGET_RANGE,
				e -> com.projecthero.mod.combat.HeroTargets.isHostile(player, e) // v0.14.20: auto-picks, rule 2
						&& player.hasLineOfSight(e)).stream()
				.sorted(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
				.limit(MoonKnightConfig.DART_FAN_COUNT)
				.toList();
	}

	/**
	 * Throw the fan (v0.14.4): always five darts, each locked on to one of the five closest hostiles
	 * ({@link #fanTargets}) and doing a normal dart's damage. With fewer than five targets the spare darts double up
	 * on the closest ones; with none at all they fan out along the aim like before. Returns how many were thrown.
	 */
	public static int throwFan(ServerPlayer player) {
		float power = MoonKnightAbilities.power(player);
		int count = MoonKnightConfig.DART_FAN_COUNT;
		List<LivingEntity> targets = fanTargets(player);
		Vec3 aim = aimDirection(player);
		Vec3 hand = AbilityHelpers.handPosition(player);
		float spread = MoonKnightConfig.DART_FAN_SPREAD_DEGREES;
		for (int i = 0; i < count; i++) {
			float offset = (i - (count - 1) / 2.0f) * spread;
			Vec3 dir = aim.yRot((float) Math.toRadians(-offset));
			LivingEntity target = targets.isEmpty() ? null : targets.get(i % targets.size());
			if (target != null) {
				// leave the hand fanned out a little toward its own target, then the lock bends it in
				Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(hand);
				if (to.lengthSqr() > 1.0e-4) {
					dir = to.normalize().add(dir.scale(0.25)).normalize();
				}
			}
			CrescentDartEntity dart = throwOne(player, dir, MoonKnightConfig.DART_SPEED, MoonKnightConfig.DART_DAMAGE * power, 0);
			if (target != null) {
				dart.lockOn(target);
			}
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
