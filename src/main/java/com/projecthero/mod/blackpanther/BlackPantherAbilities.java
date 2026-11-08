package com.projecthero.mod.blackpanther;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.blackpanther.data.BlackPantherState;
import com.projecthero.mod.blackpanther.entity.KimoyoBeadEntity;
import com.projecthero.mod.blackpanther.entity.VibraniumDaggerEntity;
import com.projecthero.mod.blackpanther.network.PantherSensesPayload;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Black Panther's twelve moves (v0.17.3). See {@link BlackPantherAbilityManager} for the keys. The timed moves are small
 * per-player runs stepped from {@link #tick}; every map here is dropped by {@link #clear} and {@link #clearSessionState}.
 */
public final class BlackPantherAbilities {
	public static final String PULSE = "kinetic_pulse";
	public static final String OVERLOAD = "kinetic_overload";
	public static final String CLAWS = "claw_combo";
	public static final String POUNCE = "pounce";
	public static final String LEAP = "panther_leap";
	public static final String DROP = "drop_strike";
	public static final String DAGGER = "vibranium_dagger";
	public static final String KIMOYO = "kimoyo_flash";
	public static final String STALK = "stalk";
	public static final String SENSES = "panther_senses";
	public static final String ABSORB = "absorb_stance";
	public static final String RETURN = "kinetic_return";

	private record Pounce(int target, long start, long landedAt) {
	}

	private record Bleed(UUID owner, long until) {
	}

	/** Claw Combo: game time of the first slash, and the next slash to land (1 or 2; slash 0 lands on the press). */
	private static final Map<UUID, long[]> CLAW_RUNS = new ConcurrentHashMap<>();
	private static final Map<UUID, Pounce> POUNCES = new ConcurrentHashMap<>();
	/** Panther Leap: until when he can still catch a ledge. */
	private static final Map<UUID, Long> LEAPS = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> DROPS = new ConcurrentHashMap<>();
	/** Absorb Stance: when it was raised. */
	private static final Map<UUID, Long> STANCES = new ConcurrentHashMap<>();
	/** Entity id -> bleed. */
	private static final Map<Integer, Bleed> BLEEDS = new ConcurrentHashMap<>();
	/** Panther Senses: per player, the feet of each creature over the last few seconds. */
	private static final Map<UUID, Map<Integer, Deque<Vec3>>> TRACKS = new ConcurrentHashMap<>();

	private BlackPantherAbilities() {
	}

	static void clearSessionState() {
		CLAW_RUNS.clear();
		POUNCES.clear();
		LEAPS.clear();
		DROPS.clear();
		STANCES.clear();
		BLEEDS.clear();
		TRACKS.clear();
	}

	/** Ends every running move (the bleed on a target runs its course). */
	public static void clear(ServerPlayer p) {
		UUID id = p.getUUID();
		CLAW_RUNS.remove(id);
		POUNCES.remove(id);
		LEAPS.remove(id);
		DROPS.remove(id);
		STANCES.remove(id);
		TRACKS.remove(id);
		BlackPantherState s = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.BLACK_PANTHER_STATE, null);
		if (s != null && (s.absorbing || s.stalking)) {
			BlackPantherState n = s.copy();
			n.absorbing = false;
			n.stalking = false;
			BlackPanther.save(p, n);
			BlackPanther.reconcile(p);
		}
	}

	// ---------------------------------------------------------------- the gate

	private static boolean begin(ServerPlayer p, String id) {
		if (!BlackPanther.canAct(p)) {
			return false;
		}
		int cd = BlackPanther.cooldownRemaining(p, id);
		if (cd > 0) {
			BlackPanther.say(p, "message.projecthero.black_panther.cooldown", ChatFormatting.GRAY,
					Component.translatable("projecthero.black_panther.ability." + id), String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		return true;
	}

	private static void cooldown(ServerPlayer p, String id, int ticks) {
		BlackPantherState n = BlackPanther.state(p).copy();
		n.abilityReadyAt.put(id, p.level().getGameTime() + ticks);
		BlackPanther.save(p, n);
	}

	private static void anim(ServerPlayer p, int id) {
		BlackPantherState n = BlackPanther.state(p).copy();
		n.animId = id;
		n.animStart = p.level().getGameTime();
		BlackPanther.save(p, n);
	}

	private static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	private static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		return flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
	}

	private static void shockwave(ServerPlayer p, float radius) {
		BlackPantherState n = BlackPanther.state(p).copy();
		n.pulseStart = p.level().getGameTime();
		n.pulseRadius = radius;
		n.pulsePos = new ArrayList<>(List.of(p.getX(), p.getY() + 0.9, p.getZ()));
		BlackPanther.save(p, n);
	}

	// ---------------------------------------------------------------- R: the bar comes back out

	public static void pulse(ServerPlayer p) {
		if (!begin(p, PULSE)) {
			return;
		}
		if (!BlackPanther.spend(p, BlackPantherConfig.PULSE_COST)) {
			BlackPanther.say(p, "message.projecthero.black_panther.low_kinetic", ChatFormatting.DARK_PURPLE, (int) BlackPantherConfig.PULSE_COST,
					(int) Math.floor(BlackPanther.kinetic(p)));
			return;
		}
		cooldown(p, PULSE, BlackPantherConfig.PULSE_COOLDOWN);
		anim(p, BlackPantherState.ANIM_PULSE);
		shockwave(p, (float) BlackPantherConfig.PULSE_RADIUS);
		Vec3 c = p.position().add(0, 0.9, 0);
		for (LivingEntity e : BlackPantherCombat.within(p, c, BlackPantherConfig.PULSE_RADIUS, e -> BlackPantherCombat.isTarget(p, e))) {
			BlackPantherCombat.strike(p, e, c, BlackPantherConfig.PULSE_DAMAGE, 1.1, 0.35);
		}
		level(p).sendParticles(BlackPanther.PURPLE_BIG, c.x, c.y, c.z, 30, 1.2, 0.4, 1.2, 0.0);
		sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.7f, 1.6f);
	}

	public static void overload(ServerPlayer p) {
		if (!begin(p, OVERLOAD)) {
			return;
		}
		if (BlackPanther.kinetic(p) < BlackPantherConfig.KINETIC_MAX - 0.01f) {
			BlackPanther.say(p, "message.projecthero.black_panther.overload_full", ChatFormatting.DARK_PURPLE);
			return;
		}
		BlackPanther.setKinetic(p, 0f);
		cooldown(p, OVERLOAD, BlackPantherConfig.OVERLOAD_COOLDOWN);
		anim(p, BlackPantherState.ANIM_OVERLOAD);
		shockwave(p, (float) BlackPantherConfig.OVERLOAD_RADIUS);
		Vec3 c = p.position().add(0, 0.9, 0);
		for (LivingEntity e : BlackPantherCombat.within(p, c, BlackPantherConfig.OVERLOAD_RADIUS, e -> BlackPantherCombat.isTarget(p, e))) {
			// full damage up close, half at the edge
			double t = Math.sqrt(e.position().add(0, e.getBbHeight() * 0.5, 0).distanceToSqr(c)) / BlackPantherConfig.OVERLOAD_RADIUS;
			BlackPantherCombat.strike(p, e, c, (float) (BlackPantherConfig.OVERLOAD_DAMAGE * (1.0 - 0.5 * t)), 2.2, 0.7);
		}
		level(p).sendParticles(BlackPanther.PURPLE_BIG, c.x, c.y, c.z, 90, 2.5, 1.0, 2.5, 0.0);
		level(p).sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.4f, 0.8f);
		sound(p, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.7f);
	}

	// ---------------------------------------------------------------- G: the claws

	public static void claws(ServerPlayer p) {
		if (CLAW_RUNS.containsKey(p.getUUID()) || !begin(p, CLAWS)) {
			return;
		}
		cooldown(p, CLAWS, BlackPantherConfig.CLAW_COOLDOWN);
		anim(p, BlackPantherState.ANIM_CLAW);
		BlackPanther.setClaws(p, true); // the combo pops the claws out by itself
		long now = p.level().getGameTime();
		// slash 1 lands now: the server first steps the run a tick later, so a slash waiting for age 0 never came
		slash(p, 0, now);
		CLAW_RUNS.put(p.getUUID(), new long[] { now, 1L });
	}

	private static void tickClaws(ServerPlayer p, long[] run, long now) {
		int next = (int) run[1];
		if (now - run[0] >= (long) next * BlackPantherConfig.CLAW_STEP) {
			slash(p, next, now);
			if (next >= 2) {
				CLAW_RUNS.remove(p.getUUID());
			} else {
				run[1] = next + 1;
			}
		}
	}

	/** One slash (0..2) of the combo; the third leaves a bleed. */
	private static void slash(ServerPlayer p, int slash, long now) {
		boolean last = slash == 2;
		float damage = last ? BlackPantherConfig.CLAW_FINISH_DAMAGE : BlackPantherConfig.CLAW_DAMAGE;
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 c = eye.add(look.scale(BlackPantherConfig.CLAW_REACH * 0.6));
		for (LivingEntity e : BlackPantherCombat.within(p, c, BlackPantherConfig.CLAW_REACH * 0.7, e -> BlackPantherCombat.isTarget(p, e))) {
			if (BlackPantherCombat.strike(p, e, p.position(), damage, last ? 0.5 : 0.1, 0.0) && last) {
				BLEEDS.put(e.getId(), new Bleed(p.getUUID(), now + BlackPantherConfig.BLEED_TICKS));
			}
		}
		level(p).sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y - 0.3, c.z, 1, 0, 0, 0, 0);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.5f + slash * 0.15f);
	}

	private static void tickBleeds(ServerPlayer p, long now) {
		for (Iterator<Map.Entry<Integer, Bleed>> it = BLEEDS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Bleed> b = it.next();
			if (!b.getValue().owner().equals(p.getUUID())) {
				continue;
			}
			Entity e = p.level().getEntity(b.getKey());
			if (!(e instanceof LivingEntity le) || !le.isAlive() || b.getValue().until() <= now) {
				it.remove();
			} else if (now % 20 == 0) {
				AbilityHelpers.hurtBurst(p, le, p.level().damageSources().playerAttack(p), BlackPantherConfig.BLEED_DAMAGE);
				level(p).sendParticles(BlackPanther.BLOOD, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 8, 0.2, 0.3, 0.2, 0.0);
			}
		}
	}

	public static void pounce(ServerPlayer p) {
		if (POUNCES.containsKey(p.getUUID()) || !begin(p, POUNCE)) {
			return;
		}
		LivingEntity target = AbilityHelpers.raycastEntity(p, BlackPantherConfig.POUNCE_RANGE);
		if (target == null || !BlackPantherCombat.isTarget(p, target)) {
			BlackPanther.say(p, "message.projecthero.black_panther.pounce_nothing", ChatFormatting.GRAY);
			return;
		}
		cooldown(p, POUNCE, BlackPantherConfig.POUNCE_COOLDOWN);
		anim(p, BlackPantherState.ANIM_POUNCE);
		POUNCES.put(p.getUUID(), new Pounce(target.getId(), p.level().getGameTime(), 0L));
		sound(p, SoundEvents.PHANTOM_SWOOP, 1.0f, 1.3f);
	}

	private static void tickPounce(ServerPlayer p, Pounce run, long now) {
		Entity e = p.level().getEntity(run.target());
		if (!(e instanceof LivingEntity target) || !target.isAlive()) {
			POUNCES.remove(p.getUUID());
			return;
		}
		if (run.landedAt() == 0L) {
			Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(p.position());
			if (to.length() <= Math.max(2.0, target.getBbWidth() + 1.2)) {
				AbilityHelpers.launchSelf(p, Vec3.ZERO);
				BlackPantherCombat.strike(p, target, p.position(), BlackPantherConfig.POUNCE_LAND_DAMAGE, 0.0, 0.0);
				AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, BlackPantherConfig.POUNCE_PIN_TICKS, 6);
				POUNCES.put(p.getUUID(), new Pounce(run.target(), run.start(), now));
				sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.8f);
			} else if (now - run.start() > 16) {
				POUNCES.remove(p.getUUID());
			} else {
				AbilityHelpers.launchSelf(p, to.normalize().scale(BlackPantherConfig.POUNCE_SPEED));
			}
			return;
		}
		if (now - run.landedAt() >= BlackPantherConfig.POUNCE_PIN_TICKS) {
			BlackPantherCombat.strike(p, target, p.position(), BlackPantherConfig.POUNCE_STRIKE_DAMAGE, 0.8, 0.2);
			sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
			POUNCES.remove(p.getUUID());
		}
	}

	// ---------------------------------------------------------------- X: movement

	public static void leap(ServerPlayer p) {
		if (!begin(p, LEAP)) {
			return;
		}
		cooldown(p, LEAP, BlackPantherConfig.LEAP_COOLDOWN);
		Vec3 f = flatLook(p).scale(BlackPantherConfig.LEAP_FORWARD);
		AbilityHelpers.launchSelf(p, new Vec3(f.x, BlackPantherConfig.LEAP_SPEED, f.z));
		LEAPS.put(p.getUUID(), p.level().getGameTime() + BlackPantherConfig.LEDGE_TICKS);
		level(p).sendParticles(BlackPanther.PURPLE, p.getX(), p.getY(), p.getZ(), 14, 0.3, 0.1, 0.3, 0.0);
		sound(p, SoundEvents.ENDER_DRAGON_FLAP, 0.6f, 1.6f);
	}

	/** Falling past a wall with open air on top of it: he catches the ledge and pulls himself up. */
	private static void tickLeap(ServerPlayer p, long until, long now) {
		if (now > until || p.onGround()) {
			LEAPS.remove(p.getUUID());
			return;
		}
		if (p.getDeltaMovement().y > 0.1) {
			return;
		}
		Vec3 f = flatLook(p);
		BlockPos wall = BlockPos.containing(p.getX() + f.x * 0.9, p.getEyeY(), p.getZ() + f.z * 0.9);
		ServerLevel level = level(p);
		boolean solid = !level.getBlockState(wall).getCollisionShape(level, wall).isEmpty();
		boolean clear = level.getBlockState(wall.above()).getCollisionShape(level, wall.above()).isEmpty()
				&& level.getBlockState(wall.above(2)).getCollisionShape(level, wall.above(2)).isEmpty();
		if (solid && clear) {
			AbilityHelpers.launchSelf(p, new Vec3(f.x * 0.35, 0.72, f.z * 0.35));
			sound(p, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.9f, 1.2f);
			LEAPS.remove(p.getUUID());
		}
	}

	public static void dropStrike(ServerPlayer p) {
		if (!begin(p, DROP)) {
			return;
		}
		if (p.onGround()) {
			BlackPanther.say(p, "message.projecthero.black_panther.drop_grounded", ChatFormatting.GRAY);
			return;
		}
		cooldown(p, DROP, BlackPantherConfig.DROP_COOLDOWN);
		anim(p, BlackPantherState.ANIM_DROP);
		LEAPS.remove(p.getUUID());
		AbilityHelpers.launchSelf(p, new Vec3(0, -BlackPantherConfig.DROP_SPEED, 0));
		DROPS.put(p.getUUID(), true);
		sound(p, SoundEvents.PHANTOM_SWOOP, 1.0f, 0.8f);
	}

	private static void tickDrop(ServerPlayer p) {
		p.resetFallDistance();
		if (!p.onGround() && !p.isInWater()) {
			return;
		}
		DROPS.remove(p.getUUID());
		Vec3 c = p.position().add(0, 0.5, 0);
		for (LivingEntity e : BlackPantherCombat.within(p, c, BlackPantherConfig.DROP_RADIUS, e -> BlackPantherCombat.isTarget(p, e))) {
			BlackPantherCombat.strike(p, e, c, BlackPantherConfig.DROP_DAMAGE, 0.9, 0.45);
		}
		shockwave(p, (float) BlackPantherConfig.DROP_RADIUS);
		level(p).sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 2, 0.8, 0.1, 0.8, 0.0);
		level(p).sendParticles(BlackPanther.PURPLE_BIG, c.x, c.y, c.z, 24, 1.5, 0.2, 1.5, 0.0);
		sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 1.3f);
	}

	// ---------------------------------------------------------------- Z: thrown

	public static void dagger(ServerPlayer p) {
		if (!begin(p, DAGGER)) {
			return;
		}
		cooldown(p, DAGGER, BlackPantherConfig.DAGGER_COOLDOWN);
		anim(p, BlackPantherState.ANIM_THROW);
		VibraniumDaggerEntity.shoot(p);
		sound(p, SoundEvents.TRIDENT_THROW.value(), 0.9f, 1.5f);
	}

	public static void kimoyo(ServerPlayer p) {
		if (!begin(p, KIMOYO)) {
			return;
		}
		cooldown(p, KIMOYO, BlackPantherConfig.FLASH_COOLDOWN);
		anim(p, BlackPantherState.ANIM_THROW);
		KimoyoBeadEntity.shoot(p);
		sound(p, SoundEvents.SNOWBALL_THROW, 0.8f, 1.3f);
	}

	// ---------------------------------------------------------------- V: the hunter

	public static void toggleStalk(ServerPlayer p) {
		if (BlackPanther.stalking(p)) {
			endStalk(p);
			return;
		}
		if (!begin(p, STALK)) {
			return;
		}
		BlackPantherState n = BlackPanther.state(p).copy();
		n.stalking = true;
		BlackPanther.save(p, n);
		sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.3f, 1.7f);
	}

	public static void endStalk(ServerPlayer p) {
		BlackPantherState s = BlackPanther.state(p);
		if (!s.stalking) {
			return;
		}
		BlackPantherState n = s.copy();
		n.stalking = false;
		n.abilityReadyAt.put(STALK, p.level().getGameTime() + BlackPantherConfig.STALK_COOLDOWN);
		BlackPanther.save(p, n);
	}

	/** Sneaking, out of a mob's short sight: it loses him. Bosses keep their threat table. */
	private static void tickStalk(ServerPlayer p) {
		if (!p.isShiftKeyDown()) {
			return;
		}
		for (LivingEntity e : BlackPantherCombat.within(p, p.position(), 32.0, e -> e instanceof Mob m && m.getTarget() == p)) {
			if (!BlackPantherCombat.isBoss(e) && e.distanceTo(p) > BlackPantherConfig.STALK_SEEN_WITHIN) {
				((Mob) e).setTarget(null);
			}
		}
	}

	/** A toggle with no cooldown: on until pressed again or the suit comes off. */
	public static void senses(ServerPlayer p) {
		if (BlackPanther.sensing(p)) {
			BlackPantherState n = BlackPanther.state(p).copy();
			n.senses = false;
			BlackPanther.save(p, n);
			TRACKS.remove(p.getUUID());
			ServerPlayNetworking.send(p, new PantherSensesPayload(List.of(), 0)); // the outlines fade at once
			sound(p, SoundEvents.ENDER_EYE_DEATH, 0.5f, 1.2f);
			return;
		}
		if (!begin(p, SENSES)) {
			return;
		}
		BlackPantherState n = BlackPanther.state(p).copy();
		n.senses = true;
		BlackPanther.save(p, n);
		TRACKS.put(p.getUUID(), new HashMap<>());
		sendSenses(p);
		sound(p, SoundEvents.ENDER_EYE_DEATH, 0.8f, 0.7f);
	}

	private static void sendSenses(ServerPlayer p) {
		List<Integer> ids = new ArrayList<>();
		for (LivingEntity e : BlackPantherCombat.within(p, p.position(), BlackPantherConfig.SENSES_RANGE, e -> e instanceof Mob)) {
			ids.add(e.getId());
		}
		// a fresh list twice a second, so a creature walking into range shows too
		ServerPlayNetworking.send(p, new PantherSensesPayload(ids.size() > 512 ? ids.subList(0, 512) : ids, 15));
	}

	private static void tickSenses(ServerPlayer p, long now) {
		if (now % 10 != 0) {
			return;
		}
		sendSenses(p);
		Map<Integer, Deque<Vec3>> tracks = TRACKS.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
		for (LivingEntity e : BlackPantherCombat.within(p, p.position(), BlackPantherConfig.SENSES_RANGE, e -> e instanceof Mob)) {
			Deque<Vec3> feet = tracks.computeIfAbsent(e.getId(), k -> new ArrayDeque<>());
			if (feet.isEmpty() || feet.peekLast().distanceToSqr(e.position()) > 0.25) {
				feet.addLast(e.position());
				if (feet.size() > BlackPantherConfig.FOOTPRINTS) {
					feet.pollFirst();
				}
			}
			// only he sees the prints
			for (Vec3 f : feet) {
				level(p).sendParticles(p, BlackPanther.PURPLE, true, f.x, f.y + 0.1, f.z, 2, 0.1, 0.0, 0.1, 0.0);
			}
		}
	}

	// ---------------------------------------------------------------- C: the suit takes it

	public static void raiseStance(ServerPlayer p) {
		if (BlackPanther.absorbing(p) || !begin(p, ABSORB)) {
			return;
		}
		BlackPantherState n = BlackPanther.state(p).copy();
		n.absorbing = true;
		n.absorbed = 0f;
		BlackPanther.save(p, n);
		BlackPanther.reconcile(p);
		STANCES.put(p.getUUID(), p.level().getGameTime());
		sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.8f, 1.5f);
	}

	public static void lowerStance(ServerPlayer p) {
		if (!BlackPanther.absorbing(p)) {
			return;
		}
		STANCES.remove(p.getUUID());
		BlackPantherState n = BlackPanther.state(p).copy();
		n.absorbing = false;
		n.abilityReadyAt.put(ABSORB, p.level().getGameTime() + BlackPantherConfig.ABSORB_COOLDOWN);
		BlackPanther.save(p, n);
		BlackPanther.reconcile(p);
	}

	/** The stance has taken all it can. */
	static void breakStance(ServerPlayer p) {
		lowerStance(p);
		sound(p, SoundEvents.SHIELD_BREAK, 0.9f, 1.2f);
	}

	public static void kineticReturn(ServerPlayer p) {
		if (!begin(p, RETURN)) {
			return;
		}
		BlackPantherDamage.LastShot shot = BlackPantherDamage.lastShot(p);
		long now = p.level().getGameTime();
		Entity shooter = shot == null || now - shot.at() > BlackPantherConfig.RETURN_WINDOW ? null : level(p).getEntity(shot.shooter());
		if (!(shooter instanceof LivingEntity target) || !target.isAlive() || target.distanceTo(p) > BlackPantherConfig.RETURN_RANGE
				|| !BlackPantherCombat.isTarget(p, target)) {
			BlackPanther.say(p, "message.projecthero.black_panther.return_nothing", ChatFormatting.GRAY);
			return;
		}
		cooldown(p, RETURN, BlackPantherConfig.RETURN_COOLDOWN);
		anim(p, BlackPantherState.ANIM_RETURN);
		// the shot comes back harder, and the stored bar rides along with it
		float spent = Math.min(BlackPantherConfig.RETURN_MAX_SPEND, BlackPanther.kinetic(p));
		if (spent > 0f) {
			BlackPanther.setKinetic(p, BlackPanther.kinetic(p) - spent);
		}
		float damage = returnDamage(shot.damage(), spent);
		Vec3 from = p.getEyePosition().add(0, -0.3, 0);
		Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
		AbilityHelpers.line(level(p), from, to, spent > 0f ? BlackPanther.PURPLE_BIG : BlackPanther.PURPLE, 3.0);
		BlackPantherCombat.strike(p, target, from, damage, 0.6 + spent / 80.0, 0.0);
		sound(p, SoundEvents.TRIDENT_THROW.value(), 1.0f, 1.2f);
	}

	/** Kinetic Return: max(4, the shot x1.25), plus 0.25 per Kinetic point spent (up to 40 spent). */
	public static float returnDamage(float shotDamage, float kineticSpent) {
		return Math.max(BlackPantherConfig.RETURN_MIN_DAMAGE, shotDamage * BlackPantherConfig.RETURN_FACTOR)
				+ Math.min(BlackPantherConfig.RETURN_MAX_SPEND, Math.max(0f, kineticSpent)) * BlackPantherConfig.RETURN_PER_KINETIC;
	}

	// ---------------------------------------------------------------- tick

	static void tick(ServerPlayer p) {
		UUID id = p.getUUID();
		long now = p.level().getGameTime();
		long[] clawRun = CLAW_RUNS.get(id);
		if (clawRun != null) {
			tickClaws(p, clawRun, now);
		}
		Pounce run = POUNCES.get(id);
		if (run != null) {
			tickPounce(p, run, now);
		}
		Long leap = LEAPS.get(id);
		if (leap != null) {
			tickLeap(p, leap, now);
		}
		if (DROPS.containsKey(id)) {
			tickDrop(p);
		}
		Long stance = STANCES.get(id);
		if (stance != null && now - stance >= BlackPantherConfig.ABSORB_MAX_TICKS) {
			breakStance(p);
		}
		BlackPantherState s = BlackPanther.state(p);
		if (s.stalking && now % 5 == 0) {
			tickStalk(p);
		}
		if (s.senses && s.suited) {
			tickSenses(p, now);
		}
		if (!BLEEDS.isEmpty()) {
			tickBleeds(p, now);
		}
	}
}
