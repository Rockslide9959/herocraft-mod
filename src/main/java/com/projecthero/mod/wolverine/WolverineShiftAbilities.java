package com.projecthero.mod.wolverine;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.16.1: Wolverine's Shift moves -- Sneak + the same key, each with its own cooldown (separate from the base move),
 * its own animation ({@code WolverineClawPose}, keyed off the synced action stamp) and its own effects:
 * <pre>
 *   Shift+R  Rising Uppercut     Shift+G  Whirlwind           Shift+X  Pounce
 *   Shift+Z  Berserker Plunge    Shift+V  Bleed Flurry        Shift+C  Feral Roar
 * </pre>
 * Every number is in {@link WolverineConfig}. Multi-step moves run on {@link WolverineScheduler}.
 */
public final class WolverineShiftAbilities {
	public static final String UPPERCUT = "rising_uppercut";
	public static final String WHIRLWIND = "whirlwind";
	public static final String POUNCE = "pounce";
	public static final String PLUNGE = "berserker_plunge";
	public static final String FLURRY = "bleed_flurry";
	public static final String ROAR = "feral_roar";

	/** Action stamps (synced {@code WolverineState.lastAction}) the client animates. */
	public static final int ACT_UPPERCUT = 11;
	public static final int ACT_WHIRL = 12;
	public static final int ACT_POUNCE = 13;
	public static final int ACT_PLUNGE = 14;
	public static final int ACT_FLURRY = 15;
	public static final int ACT_ROAR = 16;
	public static final int ACT_RAKE = 17;
	public static final int ACT_SLAM = 18;

	private static final DustParticleOptions STEEL = new DustParticleOptions(new Vector3f(0.62f, 0.78f, 0.96f), 0.8f);

	private WolverineShiftAbilities() {
	}

	private static boolean ready(ServerPlayer player, String id, String nameKey, boolean claws) {
		return WolverineAbilities.prepare(player, id, nameKey, claws);
	}

	private static String key(String id) {
		return "projecthero.wolverine.ability." + id;
	}

	private static void noTarget(ServerPlayer player) {
		player.displayClientMessage(Component.translatable("message.projecthero.wolverine.no_target")
				.withStyle(ChatFormatting.GRAY), true);
	}

	// ---------------- Shift+R: Rising Uppercut ----------------

	public static void risingUppercut(ServerPlayer player) {
		if (!ready(player, UPPERCUT, key(UPPERCUT), true)) {
			return;
		}
		Wolverine.triggerCooldown(player, UPPERCUT, WolverineConfig.UPPERCUT_COOLDOWN);
		WolverineAbilities.swing(player, ACT_UPPERCUT);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.75f);
		AbilityHelpers.sound(player, SoundEvents.CHAIN_HIT, 0.5f, 1.3f);
		for (LivingEntity t : WolverineAbilities.inBox(player, WolverineConfig.UPPERCUT_RANGE, WolverineConfig.UPPERCUT_WIDTH)) {
			if (WolverineAbilities.strike(player, t, WolverineConfig.UPPERCUT_DAMAGE, 0.1)) {
				launchUp(t, WolverineConfig.UPPERCUT_LAUNCH);
				if (player.level() instanceof ServerLevel level) {
					Vec3 p = t.position();
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y + 0.3, p.z, 8, 0.2, 0.8, 0.2, 0.25);
				}
			}
		}
	}

	private static void launchUp(LivingEntity t, double up) {
		Vec3 v = t.getDeltaMovement();
		t.setDeltaMovement(v.x, Math.max(v.y, 0.0) + up, v.z);
		t.hurtMarked = true;
	}

	// ---------------- Shift+G: Whirlwind ----------------

	public static void whirlwind(ServerPlayer player) {
		if (!ready(player, WHIRLWIND, key(WHIRLWIND), true)) {
			return;
		}
		Wolverine.triggerCooldown(player, WHIRLWIND, WolverineConfig.WHIRLWIND_COOLDOWN);
		for (int i = 0; i < WolverineConfig.WHIRLWIND_SPINS; i++) {
			final int spin = i;
			WolverineScheduler.schedule(player, i * WolverineConfig.WHIRLWIND_SPIN_TICKS, () -> spin(player, spin));
		}
	}

	private static void spin(ServerPlayer player, int spin) {
		Wolverine.markAction(player, ACT_WHIRL);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.9f + 0.2f * spin);
		Vec3 c = player.position().add(0, 0.9, 0);
		for (LivingEntity t : AbilityHelpers.enemiesAround(player, c, WolverineConfig.WHIRLWIND_RADIUS)) {
			WolverineAbilities.strike(player, t, WolverineConfig.WHIRLWIND_DAMAGE_EACH, 0.5);
		}
		if (player.level() instanceof ServerLevel level) {
			for (int k = 0; k < 16; k++) {
				double a = k * Math.PI / 8;
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x + Math.cos(a) * 2.2, c.y, c.z + Math.sin(a) * 2.2, 1, 0, 0, 0, 0);
			}
		}
	}

	// ---------------- Shift+X: Pounce ----------------

	public static void pounce(ServerPlayer player) {
		if (!Wolverine.hasPower(player)) {
			return;
		}
		if (!Wolverine.abilityReady(player, POUNCE)) {
			ready(player, POUNCE, key(POUNCE), true); // prints the cooldown note
			return;
		}
		List<LivingEntity> targets = WolverineAbilities.inCone(player, WolverineConfig.POUNCE_RANGE, 0.6);
		if (targets.isEmpty()) {
			noTarget(player);
			return; // nothing to pounce on: no cooldown spent
		}
		if (!ready(player, POUNCE, key(POUNCE), true)) {
			return;
		}
		LivingEntity target = targets.get(0);
		Wolverine.triggerCooldown(player, POUNCE, WolverineConfig.POUNCE_COOLDOWN);
		Wolverine.markAction(player, ACT_POUNCE);
		Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(player.getEyePosition());
		double dist = Math.max(1.0, aim.length() - 1.0);
		AbilityHelpers.launchSelf(player, AbilityHelpers.ballisticLaunch(aim, dist, player.onGround()));
		AbilityHelpers.sound(player, SoundEvents.PHANTOM_SWOOP, 0.9f, 1.2f);
		AbilityHelpers.sound(player, SoundEvents.WOLF_GROWL, 0.8f, 0.8f);
		boolean[] landed = {false};
		for (int i = 1; i <= WolverineConfig.POUNCE_MAX_TICKS; i++) {
			WolverineScheduler.schedule(player, i, () -> {
				if (landed[0] || !target.isAlive()) {
					return;
				}
				if (AbilityHelpers.distanceSqToBox(target, player.position().add(0, 0.9, 0))
						<= WolverineConfig.POUNCE_REACH * WolverineConfig.POUNCE_REACH) {
					landed[0] = true;
					pin(player, target);
				}
			});
		}
	}

	/** Landed on it: pin it (unless it is a boss) and rake it three times. */
	private static void pin(ServerPlayer player, LivingEntity target) {
		player.setDeltaMovement(Vec3.ZERO);
		player.hurtMarked = true;
		player.fallDistance = 0.0f;
		boolean boss = target.getMaxHealth() >= WolverineConfig.POUNCE_BOSS_HEALTH;
		if (!boss) {
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, WolverineConfig.POUNCE_PIN_TICKS, 9, false, false, true));
			target.setDeltaMovement(Vec3.ZERO);
			target.hurtMarked = true;
		}
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.7f);
		for (int i = 0; i < WolverineConfig.POUNCE_RAKES; i++) {
			WolverineScheduler.schedule(player, i * WolverineConfig.POUNCE_RAKE_GAP, () -> {
				if (!target.isAlive()) {
					return;
				}
				WolverineAbilities.swing(player, ACT_RAKE);
				if (WolverineAbilities.strike(player, target, WolverineConfig.POUNCE_RAKE_DAMAGE, 0.0)) {
					AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.4f);
				}
			});
		}
	}

	// ---------------- Shift+Z: Berserker Plunge ----------------

	public static void berserkerPlunge(ServerPlayer player) {
		if (!ready(player, PLUNGE, key(PLUNGE), true)) {
			return;
		}
		Wolverine.triggerCooldown(player, PLUNGE, WolverineConfig.PLUNGE_COOLDOWN);
		Wolverine.markAction(player, ACT_PLUNGE);
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
		AbilityHelpers.launchSelf(player, new Vec3(flat.x * 0.35, WolverineConfig.PLUNGE_JUMP, flat.z * 0.35));
		AbilityHelpers.sound(player, SoundEvents.WOLF_GROWL, 0.9f, 0.6f);
		boolean[] done = {false};
		for (int i = 4; i <= WolverineConfig.PLUNGE_MAX_TICKS; i++) {
			final int tick = i;
			WolverineScheduler.schedule(player, i, () -> {
				if (done[0]) {
					return;
				}
				if (player.getDeltaMovement().y < 0 && !player.onGround()) {
					// on the way down: claws first, driven at the ground
					player.setDeltaMovement(player.getDeltaMovement().add(0, -0.12, 0));
					player.hurtMarked = true;
				}
				if (player.onGround() || tick == WolverineConfig.PLUNGE_MAX_TICKS) {
					done[0] = true;
					slam(player);
				}
			});
		}
	}

	private static void slam(ServerPlayer player) {
		player.fallDistance = 0.0f;
		WolverineAbilities.swing(player, ACT_SLAM);
		Vec3 c = player.position();
		for (LivingEntity t : AbilityHelpers.enemiesAround(player, c.add(0, 0.5, 0), WolverineConfig.PLUNGE_RADIUS)) {
			if (WolverineAbilities.strike(player, t, WolverineConfig.PLUNGE_DAMAGE, 0.4)) {
				launchUp(t, WolverineConfig.PLUNGE_KNOCKUP);
			}
		}
		AbilityHelpers.sound(player, SoundEvents.ANVIL_LAND, 0.7f, 1.3f);
		AbilityHelpers.sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.4f);
		if (player.level() instanceof ServerLevel level) {
			ring(level, c.add(0, 0.15, 0), WolverineConfig.PLUNGE_RADIUS, 28, 0.35);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.2, c.z, 20, 0.6, 0.1, 0.6, 0.35);
		}
	}

	/** A shockwave ring: puffs racing outward along the ground (count-0 particles move with the given velocity). */
	private static void ring(ServerLevel level, Vec3 c, double radius, int points, double speed) {
		for (int k = 0; k < points; k++) {
			double a = k * 2 * Math.PI / points;
			double dx = Math.cos(a);
			double dz = Math.sin(a);
			level.sendParticles(ParticleTypes.CLOUD, c.x + dx * 0.6, c.y, c.z + dz * 0.6, 0, dx, 0.02, dz, speed);
			level.sendParticles(STEEL, c.x + dx * radius * 0.5, c.y + 0.1, c.z + dz * radius * 0.5, 1, 0.05, 0.02, 0.05, 0.0);
		}
	}

	// ---------------- Shift+V: Bleed Flurry ----------------

	public static void bleedFlurry(ServerPlayer player) {
		if (!Wolverine.hasPower(player)) {
			return;
		}
		if (!Wolverine.abilityReady(player, FLURRY)) {
			ready(player, FLURRY, key(FLURRY), true);
			return;
		}
		if (WolverineAbilities.inCone(player, WolverineConfig.FLURRY_RANGE, 0.5).isEmpty()) {
			noTarget(player);
			return;
		}
		if (!ready(player, FLURRY, key(FLURRY), true)) {
			return;
		}
		Wolverine.triggerCooldown(player, FLURRY, WolverineConfig.FLURRY_COOLDOWN);
		Set<Integer> lacerated = new HashSet<>();
		for (int i = 0; i < WolverineConfig.FLURRY_STRIKES; i++) {
			WolverineScheduler.schedule(player, i * WolverineConfig.FLURRY_INTERVAL_TICKS, () -> flurryStrike(player, lacerated));
		}
	}

	private static void flurryStrike(ServerPlayer player, Set<Integer> lacerated) {
		WolverineAbilities.swing(player, ACT_FLURRY);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.5f);
		for (LivingEntity t : WolverineAbilities.inCone(player, WolverineConfig.FLURRY_RANGE, 0.5)) {
			if (WolverineAbilities.strike(player, t, WolverineConfig.FLURRY_DAMAGE, 0.05) && lacerated.add(t.getId())) {
				lacerate(player, t);
			}
		}
	}

	/** A short bleed: {@code LACERATE_DAMAGE} a second for {@code LACERATE_SECONDS}, with steel-blue flecks (no blood). */
	private static void lacerate(ServerPlayer player, LivingEntity t) {
		for (int s = 1; s <= WolverineConfig.LACERATE_SECONDS; s++) {
			WolverineScheduler.schedule(player, s * 20, () -> {
				if (!t.isAlive()) {
					return;
				}
				t.invulnerableTime = 0;
				t.hurt(player.damageSources().playerAttack(player), WolverineConfig.LACERATE_DAMAGE);
				if (t.level() instanceof ServerLevel level) {
					level.sendParticles(STEEL, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 6, 0.25, 0.3, 0.25, 0.0);
				}
			});
		}
	}

	// ---------------- Shift+C: Feral Roar ----------------

	public static void feralRoar(ServerPlayer player) {
		if (!Wolverine.hasPower(player)) {
			return;
		}
		if (!ready(player, ROAR, key(ROAR), false)) {
			return;
		}
		Wolverine.triggerCooldown(player, ROAR, WolverineConfig.ROAR_COOLDOWN);
		Wolverine.markAction(player, ACT_ROAR);
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ROAR, 1.0f, 1.1f);
		AbilityHelpers.sound(player, SoundEvents.WOLF_GROWL, 1.0f, 0.5f);
		Vec3 c = player.position();
		for (LivingEntity t : AbilityHelpers.enemiesAround(player, c.add(0, 0.9, 0), WolverineConfig.ROAR_RADIUS)) {
			AbilityHelpers.knockbackFrom(t, c, WolverineConfig.ROAR_KNOCKBACK);
			t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WolverineConfig.ROAR_WEAKNESS_TICKS, 0));
		}
		if (player.level() instanceof ServerLevel level) {
			ring(level, c.add(0, 0.3, 0), WolverineConfig.ROAR_RADIUS, 36, 0.55);
			ring(level, c.add(0, 1.2, 0), WolverineConfig.ROAR_RADIUS, 24, 0.45);
		}
	}
}
