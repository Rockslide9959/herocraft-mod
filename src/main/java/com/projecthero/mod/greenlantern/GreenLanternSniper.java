package com.projecthero.mod.greenlantern;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.data.GreenLanternFx;
import com.projecthero.mod.greenlantern.entity.HardLightConstructEntity;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.16.1: the Sniper Rifle construct -- a hard-light rifle shaped in the ring hand from the construct wheel (C forms it,
 * C again puts it away; it is an ordinary {@link GreenLanternConstructs} construct, so N, death, logout, a dimension change
 * or an empty ring end it like any other). While it is out the client hands attack / use to this class:
 * <ul>
 *   <li><b>Right-click (hold)</b>: scope in -- the rifle comes up to the eye, the view zooms ~5x behind a green hard-light
 *   reticle ({@code GreenLanternScopeOverlay}); synced as {@link GreenLanternFx#CH_SNIPER_SCOPE} so others see the pose.</li>
 *   <li><b>Attack (hold, release)</b>: charge and fire. The bolt is hit-scan out to {@link GreenLanternConfig#SNIPER_RANGE}
 *   blocks: {@link GreenLanternConfig#SNIPER_DAMAGE} at a full {@link GreenLanternConfig#SNIPER_FULL_CHARGE_TICKS}-tick
 *   charge, {@link GreenLanternConfig#SNIPER_MIN_CHARGE_FRACTION} of it tapped, times the Oath multiplier. Every shot costs
 *   {@link GreenLanternConfig#SNIPER_SHOT_COST} Ring Charge and starts a {@link GreenLanternConfig#SNIPER_COOLDOWN_TICKS}
 *   cooldown; no charge, no shot.</li>
 * </ul>
 * The tracer and the impact flash are one {@link HardLightConstructEntity.Shape#SNIPER_BOLT} entity at the muzzle (so the
 * shooter, who is up to 120 blocks from the impact, still has it tracked), plus a forced particle burst at the impact.
 * Nothing here touches flight, so it fires as well in the air as on the ground.
 */
public final class GreenLanternSniper {
	public static final String COOLDOWN_ID = "construct_sniper";

	/** Player -> game time the current charge started. */
	private static final Map<UUID, Long> CHARGING = new ConcurrentHashMap<>();

	private static final ParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.208f, 0.941f, 0.459f), 1.8f);
	private static final ParticleOptions SPARK = new DustParticleOptions(new Vector3f(0.75f, 1.0f, 0.8f), 1.0f);

	private GreenLanternSniper() {
	}

	public static void clearSessionState() {
		CHARGING.clear();
	}

	/** Whether {@code player}'s Sniper Rifle is out. */
	public static boolean isOut(ServerPlayer player) {
		return GreenLanternConstructs.hasActive(player.getUUID(), ConstructType.SNIPER_RIFLE);
	}

	public static boolean isCharging(ServerPlayer player) {
		return CHARGING.containsKey(player.getUUID());
	}

	/** 0..1 -- how far a charge of {@code ticks} is towards a full shot. */
	public static float chargeFraction(int ticks) {
		return Mth.clamp(ticks / (float) GreenLanternConfig.SNIPER_FULL_CHARGE_TICKS, 0f, 1f);
	}

	/** The damage a shot charged for {@code ticks} does, before the Oath multiplier. */
	public static float damageFor(int ticks) {
		float min = GreenLanternConfig.SNIPER_MIN_CHARGE_FRACTION;
		return GreenLanternConfig.SNIPER_DAMAGE * (min + (1f - min) * chargeFraction(ticks));
	}

	// ---------------------------------------------------------------- input (GreenLanternSniperPayload)

	/** Attack pressed: start charging (if the rifle is out). */
	public static void chargeStart(ServerPlayer player) {
		if (!isOut(player) || isCharging(player)) {
			return;
		}
		long now = player.level().getGameTime();
		CHARGING.put(player.getUUID(), now);
		GreenLanternVisuals.anim(player, GreenLanternFx.ANIM_SNIPER_CHARGE);
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT,
				SoundSource.PLAYERS, 0.35f, 1.9f);
	}

	/** Attack released: fire with whatever charge has built up. */
	public static void release(ServerPlayer player) {
		Long since = CHARGING.remove(player.getUUID());
		if (since == null) {
			return;
		}
		fire(player, (int) (player.level().getGameTime() - since));
	}

	/** Right-click held / let go: the scope (synced for the pose; the zoom itself is client-side). */
	public static void setScoped(ServerPlayer player, boolean on) {
		GreenLanternVisuals.channel(player, GreenLanternFx.CH_SNIPER_SCOPE, on && isOut(player));
	}

	// ---------------------------------------------------------------- the shot

	/**
	 * Fires one bolt charged for {@code chargeTicks}. Returns whether a shot actually went off (refused without the rifle
	 * out, on cooldown or without the Ring Charge for it -- nothing is spent then).
	 */
	public static boolean fire(ServerPlayer player, int chargeTicks) {
		if (!isOut(player)) {
			return false;
		}
		if (!GreenLantern.abilityReady(player, COOLDOWN_ID)) {
			int seconds = (int) Math.ceil(GreenLantern.cooldownRemaining(player, COOLDOWN_ID) / 20.0);
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.construct_cooldown",
					Math.max(1, seconds)), true);
			return false;
		}
		if (!GreenLanternEnergy.spend(player, GreenLanternConfig.SNIPER_SHOT_COST)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE,
					SoundSource.PLAYERS, 0.4f, 2.0f);
			return false;
		}
		GreenLanternBattery.onAbilityUsed(player);
		GreenLantern.triggerCooldown(player, COOLDOWN_ID, GreenLanternConfig.SNIPER_COOLDOWN_TICKS);
		CHARGING.remove(player.getUUID());

		float frac = chargeFraction(chargeTicks);
		float damage = damageFor(chargeTicks) * GreenLanternRanks.power(player);
		ServerLevel level = player.serverLevel();
		Vec3 eye = player.getEyePosition();
		Vec3 dir = player.getLookAngle();
		Vec3 far = eye.add(dir.scale(GreenLanternConfig.SNIPER_RANGE));
		BlockHitResult bh = GreenLanternConstructAttacks.clip(level, eye, far, player);
		Vec3 end = bh.getType() == HitResult.Type.MISS ? far : bh.getLocation();
		LivingEntity target = GreenLanternConstructAttacks.firstAlong(level, player, eye, end, 0.3, null);
		if (target != null) {
			end = target.getBoundingBox().inflate(0.15).clip(eye, end)
					.orElse(target.position().add(0, target.getBbHeight() * 0.6, 0));
			AbilityHelpers.hurtBurst(player, target, damage); // a one-shot hit: lands through a recent hit's i-frames
			double kb = GreenLanternConfig.SNIPER_KNOCKBACK * frac;
			if (kb > 0.0) {
				target.knockback(kb, -dir.x, -dir.z);
			}
		}

		Vec3 muzzle = muzzle(player);
		HardLightConstructEntity bolt = HardLightConstructEntity.create(level, HardLightConstructEntity.Shape.SNIPER_BOLT,
				player.getUUID(), muzzle, 0.35f + 0.65f * frac, 12);
		bolt.setEnd(end);
		bolt.face(end.subtract(muzzle));
		level.addFreshEntity(bolt);

		impactFlash(level, end, frac, target != null);
		muzzleParticles(player, muzzle, 6, 0.08, 0.06);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS,
				0.9f, 0.55f + 0.25f * frac);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
				SoundSource.PLAYERS, 1.0f, 1.6f);
		level.playSound(null, end.x, end.y, end.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
		GreenLanternVisuals.anim(player, GreenLanternFx.ANIM_SNIPER_SHOT);
		return true;
	}

	/** Roughly where the rifle's muzzle is: out past the ring fist, or just ahead of the eye while scoped. */
	public static Vec3 muzzle(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		if (GreenLanternVisuals.fx(player).has(GreenLanternFx.CH_SNIPER_SCOPE)) {
			return eye.add(look.scale(2.0)).add(0, -0.3, 0);
		}
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		return eye.add(look.scale(1.6)).add(right.scale(0.36)).add(0, -0.32, 0);
	}

	/**
	 * Sparks at the muzzle for everyone nearby -- except the shooter while scoped: a few blocks from the eye under a 5x
	 * zoom each dust particle fills a quarter of the lens.
	 */
	private static void muzzleParticles(ServerPlayer player, Vec3 at, int count, double spread, double speed) {
		ServerLevel level = player.serverLevel();
		boolean scoped = GreenLanternVisuals.fx(player).has(GreenLanternFx.CH_SNIPER_SCOPE);
		for (ServerPlayer viewer : level.players()) {
			if ((viewer == player && scoped) || viewer.distanceToSqr(at) > 64.0 * 64.0) {
				continue;
			}
			level.sendParticles(viewer, SPARK, false, at.x, at.y, at.z, count, spread, spread, spread, speed);
		}
	}

	/** A burst of light where the bolt struck, sent to everyone in range of the level (forced: the shooter may be far off). */
	private static void impactFlash(ServerLevel level, Vec3 at, float frac, boolean hitCreature) {
		int n = 10 + Math.round(14 * frac);
		for (ServerPlayer viewer : level.players()) {
			if (viewer.distanceToSqr(at) > 200.0 * 200.0) {
				continue;
			}
			level.sendParticles(viewer, GREEN, true, at.x, at.y, at.z, n, 0.25, 0.25, 0.25, 0.12);
			level.sendParticles(viewer, SPARK, true, at.x, at.y, at.z, n / 2, 0.1, 0.1, 0.1, 0.2);
			level.sendParticles(viewer, ParticleTypes.END_ROD, true, at.x, at.y, at.z, hitCreature ? 6 : 3, 0.05, 0.05, 0.05, 0.12);
			level.sendParticles(viewer, ParticleTypes.FLASH, true, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		}
	}

	// ---------------------------------------------------------------- tick / cleanup

	/** Every tick while the rifle is out (from {@link GreenLanternConstructs}' construct tick). */
	public static void tick(ServerPlayer player) {
		Long since = CHARGING.get(player.getUUID());
		if (since == null) {
			return;
		}
		long held = player.level().getGameTime() - since;
		Vec3 m = muzzle(player);
		if (held % 2 == 0) {
			float f = chargeFraction((int) held);
			muzzleParticles(player, m, 1 + Math.round(2 * f), 0.04, 0.01);
		}
		if (held == GreenLanternConfig.SNIPER_FULL_CHARGE_TICKS) {
			// a soft chime as the charge tops out
			player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
					SoundSource.PLAYERS, 0.8f, 1.8f);
		}
	}

	/** The rifle ended (C, N, death, an empty ring...): drop any charge and the scope. */
	public static void onPutAway(UUID owner, ServerPlayer player) {
		CHARGING.remove(owner);
		if (player != null) {
			GreenLanternVisuals.channel(player, GreenLanternFx.CH_SNIPER_SCOPE, false);
		}
	}
}
