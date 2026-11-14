package com.projecthero.mod.blackpanther;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.blackpanther.data.BlackPantherState;
import com.projecthero.mod.blackpanther.entity.VibraniumDaggerEntity;
import com.projecthero.mod.blackpanther.network.PantherSensesPayload;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.4 (user: "he should have a new moveset for when he is out of his suit"): T'Challa without the suit -- a Heart-Shaped
 * Herb fighter. Grounded, hand-to-hand and acrobatic, and clearly weaker than the suited kit ({@link BlackPantherAbilities}):
 * no Kinetic Energy, no claws, no vibranium. The keys hand over the moment the suit comes on or off
 * ({@link BlackPantherAbilityManager}).
 *
 * <pre>
 *   R  Warrior's Combo     jab 3, cross 3, spinning kick 5        Shift+R  Leg Sweep      4 round him, trips 1.5 s
 *   G  Takedown            vault over and slam: 7, dazed 2 s      Shift+G  Disarm         2, knocks the weapon out, Weakness II
 *   X  Vault               an acrobatic forward flip              Shift+X  Backflip       back-flip, the next hit in 0.6 s misses
 *   Z  Throwing Knife      5, one target, comes back              Shift+Z  Kimoyo Scan    marks a creature 10 s: outlined, +25%
 *   V  Hunter's Tracking   toggle: outlines + footprints, 24      Shift+V  Shadow Step    sneak to lose mobs over 8 blocks; x2 from behind
 *   C  Parry (hold)        front melee -60%, perfect in 0.3 s     Shift+C  Ancestral Resolve  Regeneration II 4 s + Strength I 6 s
 * </pre>
 * Every running move here is dropped by {@link #clear} (suit on / off, death, a world change) and {@link #clearSessionState}.
 */
public final class BlackPantherUnsuited {
	public static final String COMBO = "warriors_combo";
	public static final String SWEEP = "leg_sweep";
	public static final String TAKEDOWN = "takedown";
	public static final String DISARM = "disarm";
	public static final String VAULT = "vault";
	public static final String BACKFLIP = "backflip";
	public static final String KNIFE = "throwing_knife";
	public static final String SCAN = "kimoyo_scan";
	public static final String TRACKING = "hunters_tracking";
	public static final String SHADOW = "shadow_step";
	public static final String PARRY = "parry";
	public static final String RESOLVE = "ancestral_resolve";

	private record Mark(UUID owner, long until) {
	}

	/** Warrior's Combo: the press time and the next hit to land (1 or 2). */
	private static final Map<UUID, long[]> COMBOS = new ConcurrentHashMap<>();
	/** Takedown: the grabbed creature's id and the grab time. */
	private static final Map<UUID, long[]> TAKEDOWNS = new ConcurrentHashMap<>();
	/** Vault / Backflip in the air: until when the landing is softened. */
	private static final Map<UUID, Long> FLIPS = new ConcurrentHashMap<>();
	/** Backflip: until when the next hit misses. */
	private static final Map<UUID, Long> EVADES = new ConcurrentHashMap<>();
	/** Parry: when it was raised. */
	private static final Map<UUID, Long> PARRIES = new ConcurrentHashMap<>();
	/** Entity id -> its Kimoyo Scan mark. */
	private static final Map<Integer, Mark> MARKS = new ConcurrentHashMap<>();

	private BlackPantherUnsuited() {
	}

	static void clearSessionState() {
		COMBOS.clear();
		TAKEDOWNS.clear();
		FLIPS.clear();
		EVADES.clear();
		PARRIES.clear();
		MARKS.clear();
	}

	static void forget(UUID id) {
		COMBOS.remove(id);
		TAKEDOWNS.remove(id);
		FLIPS.remove(id);
		EVADES.remove(id);
		PARRIES.remove(id);
		MARKS.values().removeIf(m -> m.owner().equals(id));
	}

	/** Ends every running unsuited move (a mark already placed runs its course). */
	public static void clear(ServerPlayer p) {
		UUID id = p.getUUID();
		COMBOS.remove(id);
		TAKEDOWNS.remove(id);
		EVADES.remove(id);
		PARRIES.remove(id);
		BlackPantherState s = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.BLACK_PANTHER_STATE, null);
		if (s != null && !s.suited && (s.absorbing || s.stalking || s.senses)) {
			BlackPantherState n = s.copy();
			n.absorbing = false;
			n.stalking = false;
			n.senses = false;
			BlackPanther.save(p, n);
			BlackPanther.reconcile(p);
		}
	}

	// ---------------------------------------------------------------- the gate

	private static boolean begin(ServerPlayer p, String id) {
		if (!BlackPanther.canActBare(p)) {
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

	/** The creature in front of him within {@code range}: the one aimed at, or else the nearest in a narrow cone. */
	private static LivingEntity inFront(ServerPlayer p, double range) {
		LivingEntity aimed = AbilityHelpers.raycastEntity(p, range);
		if (aimed != null && BlackPantherCombat.isTarget(p, aimed)) {
			return aimed;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		LivingEntity best = null;
		double bestSq = Double.MAX_VALUE;
		for (LivingEntity e : BlackPantherCombat.within(p, eye, range, e -> BlackPantherCombat.isTarget(p, e))) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.lengthSqr();
			if (d > 1.0e-4 && to.normalize().dot(look) > 0.8 && d < bestSq) {
				bestSq = d;
				best = e;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- R: hand to hand

	public static void combo(ServerPlayer p) {
		if (COMBOS.containsKey(p.getUUID()) || !begin(p, COMBO)) {
			return;
		}
		cooldown(p, COMBO, BlackPantherConfig.COMBO_COOLDOWN);
		anim(p, BlackPantherState.ANIM_COMBO);
		long now = p.level().getGameTime();
		hit(p, 0); // the jab lands on the press
		COMBOS.put(p.getUUID(), new long[] { now, 1L });
	}

	/** One hit of the Warrior's Combo: 0 the jab, 1 the cross, 2 the spinning kick (wider, harder, a shove). */
	private static void hit(ServerPlayer p, int n) {
		boolean kick = n == 2;
		float damage = n == 0 ? BlackPantherConfig.COMBO_JAB : n == 1 ? BlackPantherConfig.COMBO_CROSS : BlackPantherConfig.COMBO_KICK;
		Vec3 eye = p.getEyePosition();
		Vec3 c = eye.add(p.getLookAngle().scale(BlackPantherConfig.COMBO_REACH * 0.55)).add(0, kick ? -0.3 : 0, 0);
		double r = kick ? 1.9 : 1.4;
		boolean landed = false;
		for (LivingEntity e : BlackPantherCombat.within(p, c, r, e -> BlackPantherCombat.isTarget(p, e))) {
			landed |= BlackPantherCombat.strike(p, e, p.position(), damage, kick ? 0.9 : 0.15, kick ? 0.2 : 0.0);
		}
		if (kick) {
			level(p).sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		}
		sound(p, landed ? (kick ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_STRONG) : SoundEvents.PLAYER_ATTACK_WEAK, 0.9f,
				1.0f + n * 0.12f);
	}

	private static void tickCombo(ServerPlayer p, long[] run, long now) {
		int next = (int) run[1];
		if (now - run[0] >= (long) next * BlackPantherConfig.COMBO_STEP) {
			hit(p, next);
			if (next >= 2) {
				COMBOS.remove(p.getUUID());
			} else {
				run[1] = next + 1;
			}
		}
	}

	public static void sweep(ServerPlayer p) {
		if (!begin(p, SWEEP)) {
			return;
		}
		cooldown(p, SWEEP, BlackPantherConfig.SWEEP_COOLDOWN);
		anim(p, BlackPantherState.ANIM_SWEEP);
		Vec3 c = p.position().add(0, 0.4, 0);
		for (LivingEntity e : BlackPantherCombat.within(p, c, BlackPantherConfig.SWEEP_RADIUS, e -> BlackPantherCombat.isTarget(p, e))) {
			if (BlackPantherCombat.strike(p, e, c, BlackPantherConfig.SWEEP_DAMAGE, 0.25, 0.3) && !BlackPantherCombat.isBoss(e)) {
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, BlackPantherConfig.SWEEP_TRIP_TICKS, 3);
			}
		}
		level(p).sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 6, 1.2, 0.05, 1.2, 0.0);
		level(p).sendParticles(ParticleTypes.CLOUD, c.x, p.getY() + 0.1, c.z, 10, 1.0, 0.02, 1.0, 0.02);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
	}

	// ---------------------------------------------------------------- G: grappling

	public static void takedown(ServerPlayer p) {
		if (TAKEDOWNS.containsKey(p.getUUID()) || !begin(p, TAKEDOWN)) {
			return;
		}
		LivingEntity target = inFront(p, BlackPantherConfig.GRAB_RANGE);
		if (target == null) {
			BlackPanther.say(p, "message.projecthero.black_panther.grab_nothing", ChatFormatting.GRAY);
			return;
		}
		cooldown(p, TAKEDOWN, BlackPantherConfig.TAKEDOWN_COOLDOWN);
		anim(p, BlackPantherState.ANIM_TAKEDOWN);
		TAKEDOWNS.put(p.getUUID(), new long[] { target.getId(), p.level().getGameTime() });
		if (!BlackPantherCombat.isBoss(target)) {
			AbilityHelpers.push(target, new Vec3(0, 0.45, 0)); // hauled up off its feet with him
		}
		// he springs off them: a small hop that carries him up and over
		AbilityHelpers.launchSelf(p, new Vec3(0, 0.42, 0).add(flatLook(p).scale(0.12)));
		sound(p, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0f, 0.8f);
	}

	private static void tickTakedown(ServerPlayer p, long[] run, long now) {
		Entity e = p.level().getEntity((int) run[0]);
		if (!(e instanceof LivingEntity target) || !target.isAlive() || target.distanceTo(p) > 6.0) {
			TAKEDOWNS.remove(p.getUUID());
			return;
		}
		boolean boss = BlackPantherCombat.isBoss(target);
		long age = now - run[1];
		if (age < BlackPantherConfig.TAKEDOWN_SLAM_AT) {
			if (!boss) {
				target.setDeltaMovement(0, target.getDeltaMovement().y, 0); // held: it only goes up and down with him
				target.hurtMarked = true;
			}
			return;
		}
		TAKEDOWNS.remove(p.getUUID());
		if (!boss) {
			// flipped over his shoulder and slammed down in front of him
			Vec3 at = p.position().add(flatLook(p).scale(1.3));
			if (p.level().noCollision(target, target.getBoundingBox().move(at.subtract(target.position())))) {
				target.teleportTo(at.x, Math.min(target.getY(), p.getY() + 0.2), at.z);
			}
			target.setDeltaMovement(0, -0.6, 0);
			target.hurtMarked = true;
		}
		if (BlackPantherCombat.strike(p, target, p.position(), BlackPantherConfig.TAKEDOWN_DAMAGE, 0.0, 0.0) && !boss) {
			AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, BlackPantherConfig.TAKEDOWN_DAZE_TICKS, 2);
			AbilityHelpers.applyControl(target, MobEffects.WEAKNESS, BlackPantherConfig.TAKEDOWN_DAZE_TICKS, 0);
		}
		Vec3 c = target.position();
		level(p).sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.2, c.z, 1, 0, 0, 0, 0);
		level(p).sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.1, c.z, 12, 0.6, 0.05, 0.6, 0.03);
		sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.7f);
		sound(p, SoundEvents.GENERIC_BIG_FALL, 1.0f, 0.8f);
	}

	public static void disarm(ServerPlayer p) {
		if (!begin(p, DISARM)) {
			return;
		}
		LivingEntity target = inFront(p, BlackPantherConfig.GRAB_RANGE);
		if (target == null) {
			BlackPanther.say(p, "message.projecthero.black_panther.grab_nothing", ChatFormatting.GRAY);
			return;
		}
		cooldown(p, DISARM, BlackPantherConfig.DISARM_COOLDOWN);
		anim(p, BlackPantherState.ANIM_DISARM);
		BlackPantherCombat.strike(p, target, p.position(), BlackPantherConfig.DISARM_DAMAGE, 0.3, 0.0);
		ItemStack held = target.getMainHandItem();
		if (target instanceof Mob && !BlackPantherCombat.isBoss(target) && !held.isEmpty()) {
			// a creature's weapon is wrenched out of its hand and lands at its feet (a player keeps theirs: Weakness only)
			ItemStack drop = held.copy();
			target.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			target.spawnAtLocation(drop, 0.5f);
			sound(p, SoundEvents.ITEM_BREAK, 0.6f, 1.6f);
		}
		target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, BlackPantherConfig.DISARM_WEAKNESS_TICKS, 1, false, true, true));
		sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 1.3f);
	}

	// ---------------------------------------------------------------- X: acrobatics

	public static void vault(ServerPlayer p) {
		if (!begin(p, VAULT)) {
			return;
		}
		cooldown(p, VAULT, BlackPantherConfig.VAULT_COOLDOWN);
		anim(p, BlackPantherState.ANIM_VAULT);
		Vec3 f = flatLook(p).scale(BlackPantherConfig.VAULT_FORWARD);
		AbilityHelpers.launchSelf(p, new Vec3(f.x, BlackPantherConfig.VAULT_UP, f.z));
		FLIPS.put(p.getUUID(), p.level().getGameTime() + 60);
		level(p).sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 6, 0.2, 0.02, 0.2, 0.02);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 1.8f);
	}

	public static void backflip(ServerPlayer p) {
		if (!begin(p, BACKFLIP)) {
			return;
		}
		cooldown(p, BACKFLIP, BlackPantherConfig.BACKFLIP_COOLDOWN);
		anim(p, BlackPantherState.ANIM_BACKFLIP);
		Vec3 b = flatLook(p).scale(-BlackPantherConfig.BACKFLIP_BACK);
		AbilityHelpers.launchSelf(p, new Vec3(b.x, BlackPantherConfig.BACKFLIP_UP, b.z));
		long now = p.level().getGameTime();
		FLIPS.put(p.getUUID(), now + 60);
		EVADES.put(p.getUUID(), now + BlackPantherConfig.EVADE_TICKS);
		level(p).sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 6, 0.2, 0.02, 0.2, 0.02);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 1.5f);
	}

	/** The Backflip's evade: true (and used up) if a hit landing now should miss him. */
	static boolean evade(ServerPlayer p) {
		Long until = EVADES.get(p.getUUID());
		if (until == null || until < p.level().getGameTime()) {
			return false;
		}
		EVADES.remove(p.getUUID());
		level(p).sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
		sound(p, SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.0f, 1.4f);
		return true;
	}

	/** A Backflip's evade is live right now. */
	public static boolean evading(ServerPlayer p) {
		Long until = EVADES.get(p.getUUID());
		return until != null && until >= p.level().getGameTime();
	}

	private static void tickFlip(ServerPlayer p, long until, long now) {
		if (now > until || (p.onGround() && now > until - 56)) {
			FLIPS.remove(p.getUUID());
			return;
		}
		p.resetFallDistance(); // a flip always lands on its feet
	}

	// ---------------------------------------------------------------- Z: the knife and the beads

	public static void knife(ServerPlayer p) {
		if (!begin(p, KNIFE)) {
			return;
		}
		cooldown(p, KNIFE, BlackPantherConfig.KNIFE_COOLDOWN);
		anim(p, BlackPantherState.ANIM_KNIFE);
		VibraniumDaggerEntity.shootKnife(p);
		sound(p, SoundEvents.TRIDENT_THROW.value(), 0.7f, 1.8f);
	}

	public static void scan(ServerPlayer p) {
		if (!begin(p, SCAN)) {
			return;
		}
		LivingEntity target = AbilityHelpers.raycastEntity(p, BlackPantherConfig.SCAN_RANGE);
		if (target == null || !BlackPantherCombat.isTarget(p, target)) {
			BlackPanther.say(p, "message.projecthero.black_panther.scan_nothing", ChatFormatting.GRAY);
			return;
		}
		cooldown(p, SCAN, BlackPantherConfig.SCAN_COOLDOWN);
		anim(p, BlackPantherState.ANIM_SCAN);
		long now = p.level().getGameTime();
		MARKS.put(target.getId(), new Mark(p.getUUID(), now + BlackPantherConfig.SCAN_TICKS));
		ServerPlayNetworking.send(p, new PantherSensesPayload(List.of(target.getId()), BlackPantherConfig.SCAN_TICKS, PantherSensesPayload.MARK));
		Vec3 from = p.getEyePosition().add(0, -0.4, 0);
		AbilityHelpers.line(level(p), from, target.position().add(0, target.getBbHeight() * 0.6, 0), BlackPanther.PURPLE, 2.0);
		sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.8f);
		sound(p, SoundEvents.BEACON_POWER_SELECT, 0.4f, 2.0f);
	}

	/** {@code target} carries {@code owner}'s live Kimoyo Scan mark. */
	public static boolean marked(ServerPlayer owner, LivingEntity target) {
		Mark m = MARKS.get(target.getId());
		return m != null && m.owner().equals(owner.getUUID()) && m.until() >= owner.level().getGameTime();
	}

	// ---------------------------------------------------------------- V: the hunter

	public static void toggleTracking(ServerPlayer p) {
		BlackPantherState s = BlackPanther.state(p);
		if (!s.hasPower || s.suited) {
			return;
		}
		BlackPantherState n = s.copy();
		n.senses = !s.senses;
		BlackPanther.save(p, n);
		if (!n.senses) {
			ServerPlayNetworking.send(p, new PantherSensesPayload(List.of(), 0));
		}
		sound(p, SoundEvents.ENDER_EYE_DEATH, 0.6f, n.senses ? 0.9f : 1.3f);
	}

	public static void toggleShadow(ServerPlayer p) {
		BlackPantherState s = BlackPanther.state(p);
		if (s.stalking && !s.suited) {
			endShadow(p);
			return;
		}
		if (!begin(p, SHADOW)) {
			return;
		}
		BlackPantherState n = s.copy();
		n.stalking = true;
		BlackPanther.save(p, n);
		sound(p, SoundEvents.WOOL_STEP, 0.6f, 0.8f);
	}

	public static void endShadow(ServerPlayer p) {
		BlackPantherState s = BlackPanther.state(p);
		if (!s.stalking) {
			return;
		}
		BlackPantherState n = s.copy();
		n.stalking = false;
		n.abilityReadyAt.put(SHADOW, p.level().getGameTime() + BlackPantherConfig.STALK_COOLDOWN);
		BlackPanther.save(p, n);
	}

	// ---------------------------------------------------------------- C: the guard

	public static void raiseParry(ServerPlayer p) {
		if (PARRIES.containsKey(p.getUUID()) || !begin(p, PARRY)) {
			return;
		}
		BlackPantherState n = BlackPanther.state(p).copy();
		n.absorbing = true;
		BlackPanther.save(p, n);
		BlackPanther.reconcile(p);
		PARRIES.put(p.getUUID(), p.level().getGameTime());
		sound(p, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.9f, 1.4f);
	}

	public static void lowerParry(ServerPlayer p) {
		if (PARRIES.remove(p.getUUID()) == null) {
			return;
		}
		BlackPantherState n = BlackPanther.state(p).copy();
		n.absorbing = false;
		n.abilityReadyAt.put(PARRY, p.level().getGameTime() + BlackPantherConfig.PARRY_COOLDOWN);
		BlackPanther.save(p, n);
		BlackPanther.reconcile(p);
	}

	/**
	 * A melee hit from the front met the raised Parry: inside its first 6 ticks it is turned wholly aside, the attacker staggers
	 * and takes the riposte. False when it was too late for that (the hit is then only cut).
	 */
	static boolean perfectParry(ServerPlayer p, LivingEntity attacker) {
		Long raised = PARRIES.get(p.getUUID());
		long now = p.level().getGameTime();
		if (raised == null || now - raised > BlackPantherConfig.PARRY_PERFECT_TICKS) {
			return false;
		}
		anim(p, BlackPantherState.ANIM_RIPOSTE);
		if (!BlackPantherCombat.isBoss(attacker)) {
			AbilityHelpers.applyControl(attacker, MobEffects.MOVEMENT_SLOWDOWN, BlackPantherConfig.RIPOSTE_STAGGER_TICKS, 2);
			AbilityHelpers.applyControl(attacker, MobEffects.WEAKNESS, BlackPantherConfig.RIPOSTE_STAGGER_TICKS, 0);
		}
		BlackPantherCombat.strike(p, attacker, p.position(), BlackPantherConfig.RIPOSTE_DAMAGE, 0.7, 0.1);
		Vec3 c = p.getEyePosition().add(p.getLookAngle().scale(0.8));
		level(p).sendParticles(ParticleTypes.CRIT, c.x, c.y - 0.3, c.z, 14, 0.25, 0.25, 0.25, 0.25);
		sound(p, SoundEvents.SHIELD_BLOCK, 1.0f, 1.6f);
		sound(p, SoundEvents.ANVIL_LAND, 0.3f, 2.0f);
		// a perfect parry lets him go straight into the next one
		PARRIES.put(p.getUUID(), now);
		return true;
	}

	public static void resolve(ServerPlayer p) {
		if (!begin(p, RESOLVE)) {
			return;
		}
		cooldown(p, RESOLVE, BlackPantherConfig.RESOLVE_COOLDOWN);
		anim(p, BlackPantherState.ANIM_RESOLVE);
		p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, BlackPantherConfig.RESOLVE_REGEN_TICKS, 1, false, true, true));
		p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, BlackPantherConfig.RESOLVE_STRENGTH_TICKS, 0, false, true, true));
		p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		p.removeEffect(MobEffects.WEAKNESS);
		Vec3 c = p.position().add(0, 1.0, 0);
		level(p).sendParticles(BlackPanther.PURPLE_BIG, c.x, c.y, c.z, 26, 0.45, 0.7, 0.45, 0.0);
		level(p).sendParticles(ParticleTypes.ENCHANT, c.x, c.y + 0.6, c.z, 30, 0.4, 0.4, 0.4, 0.4);
		sound(p, SoundEvents.BEACON_ACTIVATE, 0.6f, 1.6f);
		sound(p, SoundEvents.PLAYER_BREATH, 0.8f, 0.8f);
	}

	// ---------------------------------------------------------------- passive: Hunter's Instinct

	/** Every half second, the creatures hunting him within 20 blocks are outlined red, for him alone (suit on or off). */
	private static void tickInstinct(ServerPlayer p) {
		List<Integer> ids = new ArrayList<>();
		for (LivingEntity e : BlackPantherCombat.within(p, p.position(), BlackPantherConfig.INSTINCT_RANGE, e -> e instanceof Mob m && m.getTarget() == p)) {
			ids.add(e.getId());
			if (ids.size() >= 64) {
				break;
			}
		}
		if (!ids.isEmpty()) {
			ServerPlayNetworking.send(p, new PantherSensesPayload(ids, 15, PantherSensesPayload.INSTINCT));
		}
	}

	/** How many creatures are hunting him right now (the HUD's Hunted tag reads the client's copy). */
	public static int hunters(ServerPlayer p) {
		return BlackPantherCombat.within(p, p.position(), BlackPantherConfig.INSTINCT_RANGE, e -> e instanceof Mob m && m.getTarget() == p).size();
	}

	// ---------------------------------------------------------------- tick

	static void tick(ServerPlayer p) {
		UUID id = p.getUUID();
		long now = p.level().getGameTime();
		if (now % 10 == 0) {
			tickInstinct(p);
		}
		long[] combo = COMBOS.get(id);
		if (combo != null) {
			tickCombo(p, combo, now);
		}
		long[] takedown = TAKEDOWNS.get(id);
		if (takedown != null) {
			tickTakedown(p, takedown, now);
		}
		Long flip = FLIPS.get(id);
		if (flip != null) {
			tickFlip(p, flip, now);
		}
		Long parry = PARRIES.get(id);
		if (parry != null && now - parry >= BlackPantherConfig.PARRY_MAX_TICKS) {
			lowerParry(p);
		}
		if (!MARKS.isEmpty() && now % 20 == 0) {
			for (Iterator<Map.Entry<Integer, Mark>> it = MARKS.entrySet().iterator(); it.hasNext();) {
				Mark m = it.next().getValue();
				if (m.owner().equals(id) && m.until() < now) {
					it.remove();
				}
			}
		}
	}
}
