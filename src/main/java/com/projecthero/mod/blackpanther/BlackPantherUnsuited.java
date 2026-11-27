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
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.4 (user: "he should have a new moveset for when he is out of his suit"): T'Challa without the suit -- a Heart-Shaped
 * Herb fighter: grounded, hand-to-hand and acrobatic, no Kinetic Energy, no claws, no vibranium. The keys hand over the moment
 * the suit comes on or off ({@link BlackPantherAbilityManager}). v0.17.7: the user's numbers and four new moves.
 *
 * <pre>
 *   R  Warrior's Combo     jab 5, cross 7, spinning kick 10       Shift+R  Leg Sweep      7 round him, trips 1.5 s
 *   G  Takedown            grab within 3, vault and slam: 10      Shift+G  Ground Pound   hop and slam: 9 within 3.5, Slowness II
 *   X  Vault               an acrobatic forward flip              Shift+X  Flying Kick    dash where he aims, 10 on the first hit
 *   Z  Throwing Knife      8, one target, comes back              Shift+Z  Kimoyo Scan    marks a creature 10 s: outlined, +25%
 *   V  Rising Uppercut     9 and launched (bosses: the damage)    Shift+V  Hunter's Tracking  toggle: outlines + footprints, 24
 *   C  Block (hold)        every hit from the front -60%          Shift+C  Ancestral Resolve  Regeneration II 4 s + Strength I 6 s
 * </pre>
 * The Warrior's Combo is R in the suit too (8 / 9 / 10 there): {@link #combo} picks the numbers by the suit.
 * Every running move here is dropped by {@link #clear} (suit on / off, death, a world change) and {@link #clearSessionState}.
 */
public final class BlackPantherUnsuited {
	public static final String COMBO = "warriors_combo";
	public static final String SWEEP = "leg_sweep";
	public static final String TAKEDOWN = "takedown";
	public static final String POUND = "ground_pound";
	public static final String VAULT = "vault";
	public static final String KICK = "flying_kick";
	public static final String KNIFE = "throwing_knife";
	public static final String SCAN = "kimoyo_scan";
	public static final String UPPERCUT = "rising_uppercut";
	public static final String TRACKING = "hunters_tracking";
	public static final String BLOCK = "block";
	public static final String RESOLVE = "ancestral_resolve";

	private record Mark(UUID owner, long until) {
	}

	/** Warrior's Combo: the press time and the next hit to land (1 or 2). */
	private static final Map<UUID, long[]> COMBOS = new ConcurrentHashMap<>();
	/** Takedown: the grabbed creature's id and the grab time. */
	private static final Map<UUID, long[]> TAKEDOWNS = new ConcurrentHashMap<>();
	/** Vault in the air: until when the landing is softened. */
	private static final Map<UUID, Long> FLIPS = new ConcurrentHashMap<>();
	/** Ground Pound: when he hopped. */
	private static final Map<UUID, Long> POUNDS = new ConcurrentHashMap<>();
	/** Flying Kick: when the dash started, and its aimed direction (x, y, z) as raw long bits. */
	private static final Map<UUID, long[]> KICKS = new ConcurrentHashMap<>();
	/** Block: when it was raised. */
	private static final Map<UUID, Long> BLOCKS = new ConcurrentHashMap<>();
	/** Entity id -> its Kimoyo Scan mark. */
	private static final Map<Integer, Mark> MARKS = new ConcurrentHashMap<>();

	private BlackPantherUnsuited() {
	}

	static void clearSessionState() {
		COMBOS.clear();
		TAKEDOWNS.clear();
		FLIPS.clear();
		POUNDS.clear();
		KICKS.clear();
		BLOCKS.clear();
		MARKS.clear();
	}

	static void forget(UUID id) {
		COMBOS.remove(id);
		TAKEDOWNS.remove(id);
		FLIPS.remove(id);
		POUNDS.remove(id);
		KICKS.remove(id);
		BLOCKS.remove(id);
		MARKS.values().removeIf(m -> m.owner().equals(id));
	}

	/** Ends every running unsuited move (a mark already placed runs its course). */
	public static void clear(ServerPlayer p) {
		UUID id = p.getUUID();
		COMBOS.remove(id);
		TAKEDOWNS.remove(id);
		POUNDS.remove(id);
		KICKS.remove(id);
		BLOCKS.remove(id);
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
		return BlackPanther.canActBare(p) && ready(p, id);
	}

	private static boolean ready(ServerPlayer p, String id) {
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

	/** R, both kits (v0.17.7: the suited R is the Warrior's Combo too, with its own numbers). */
	public static void combo(ServerPlayer p) {
		if (COMBOS.containsKey(p.getUUID())) {
			return;
		}
		boolean suited = BlackPanther.suitedKit(p);
		if (suited ? !BlackPanther.canAct(p) : !BlackPanther.canActBare(p)) {
			return;
		}
		if (!ready(p, COMBO)) {
			return;
		}
		cooldown(p, COMBO, BlackPantherConfig.COMBO_COOLDOWN);
		anim(p, BlackPantherState.ANIM_COMBO);
		long now = p.level().getGameTime();
		hit(p, 0); // the jab lands on the press
		COMBOS.put(p.getUUID(), new long[] { now, 1L });
	}

	/** One hit of the Warrior's Combo's damage: 0 the jab, 1 the cross, 2 the spinning kick; suited or not. */
	public static float comboDamage(int n, boolean suited) {
		if (suited) {
			return n == 0 ? BlackPantherConfig.SUITED_COMBO_JAB : n == 1 ? BlackPantherConfig.SUITED_COMBO_CROSS : BlackPantherConfig.SUITED_COMBO_KICK;
		}
		return n == 0 ? BlackPantherConfig.COMBO_JAB : n == 1 ? BlackPantherConfig.COMBO_CROSS : BlackPantherConfig.COMBO_KICK;
	}

	/** One hit of the Warrior's Combo: 0 the jab, 1 the cross, 2 the spinning kick (wider, harder, a shove). */
	private static void hit(ServerPlayer p, int n) {
		boolean kick = n == 2;
		float damage = comboDamage(n, BlackPanther.suitedKit(p));
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

	// ---------------------------------------------------------------- G: grappling and the slam

	public static void takedown(ServerPlayer p) {
		if (TAKEDOWNS.containsKey(p.getUUID()) || !begin(p, TAKEDOWN)) {
			return;
		}
		LivingEntity target = inFront(p, BlackPantherConfig.TAKEDOWN_RANGE);
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

	/** v0.17.7 Ground Pound (Shift+G, user: "replace this ability with like a ground pound ability"): a short hop... */
	public static void groundPound(ServerPlayer p) {
		if (POUNDS.containsKey(p.getUUID()) || !begin(p, POUND)) {
			return;
		}
		cooldown(p, POUND, BlackPantherConfig.POUND_COOLDOWN);
		anim(p, BlackPantherState.ANIM_GROUND_POUND);
		AbilityHelpers.launchSelf(p, new Vec3(0, BlackPantherConfig.POUND_UP, 0).add(flatLook(p).scale(0.15)));
		POUNDS.put(p.getUUID(), p.level().getGameTime());
		level(p).sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 8, 0.3, 0.02, 0.3, 0.02);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 0.7f);
	}

	/** ...then he drives down from the top of it, and the slam lands as he touches down (or once it has run its time). */
	private static void tickPound(ServerPlayer p, long start, long now) {
		long age = now - start;
		p.resetFallDistance();
		if (age == BlackPantherConfig.POUND_DIVE_AT && !p.onGround()) {
			AbilityHelpers.launchSelf(p, new Vec3(0, -BlackPantherConfig.POUND_DOWN, 0));
			sound(p, SoundEvents.PHANTOM_SWOOP, 0.7f, 1.2f);
		}
		boolean landed = age >= 3 && (p.onGround() || p.isInWater());
		if (!landed && age < BlackPantherConfig.POUND_MAX_TICKS) {
			return;
		}
		POUNDS.remove(p.getUUID());
		slam(p);
	}

	/** The Ground Pound's slam: 9 to everything within 3.5, knocked up and back, Slowness II 1.5 s (bosses: the damage). */
	static void slam(ServerPlayer p) {
		Vec3 c = p.position().add(0, 0.3, 0);
		for (LivingEntity e : BlackPantherCombat.within(p, c, BlackPantherConfig.POUND_RADIUS, e -> BlackPantherCombat.isTarget(p, e))) {
			if (BlackPantherCombat.strike(p, e, c, BlackPantherConfig.POUND_DAMAGE, 0.8, 0.45) && !BlackPantherCombat.isBoss(e)) {
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, BlackPantherConfig.POUND_SLOW_TICKS, 1);
			}
		}
		BlackPantherState n = BlackPanther.state(p).copy();
		n.pulseStart = p.level().getGameTime(); // the shockwave ring (BlackPantherEffectsRenderer), and the landing pose
		n.pulseRadius = (float) BlackPantherConfig.POUND_RADIUS;
		n.pulsePos = new ArrayList<>(List.of(p.getX(), p.getY() + 0.15, p.getZ()));
		BlackPanther.save(p, n);
		ServerLevel level = level(p);
		BlockState under = level.getBlockState(p.blockPosition().below());
		if (!under.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), c.x, p.getY() + 0.1, c.z, 40, 1.4, 0.05, 1.4, 0.15);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 2, 0.7, 0.05, 0.7, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, c.x, p.getY() + 0.1, c.z, 18, 1.4, 0.02, 1.4, 0.06);
		sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.55f, 1.4f);
		sound(p, SoundEvents.ANVIL_LAND, 0.35f, 0.6f);
		sound(p, SoundEvents.GENERIC_BIG_FALL, 1.0f, 0.7f);
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

	private static void tickFlip(ServerPlayer p, long until, long now) {
		if (now > until || (p.onGround() && now > until - 56)) {
			FLIPS.remove(p.getUUID());
			return;
		}
		p.resetFallDistance(); // a flip always lands on its feet
	}

	/**
	 * v0.17.7 Flying Kick (Shift+X, user: "change this into a dashing flying kick ability, 10 damage"; then "make the flying kick ability go where the player aims instead of just straight ahead"): he dashes along his aim, a foot
	 * out in front, and the first creature he comes within reach of takes the kick (10, knocked back) and ends the dash.
	 */
	public static void flyingKick(ServerPlayer p) {
		if (KICKS.containsKey(p.getUUID()) || !begin(p, KICK)) {
			return;
		}
		cooldown(p, KICK, BlackPantherConfig.KICK_COOLDOWN);
		anim(p, BlackPantherState.ANIM_FLYING_KICK);
		Vec3 f = kickDirection(p);
		long now = p.level().getGameTime();
		KICKS.put(p.getUUID(), new long[] { now, Double.doubleToRawLongBits(f.x), Double.doubleToRawLongBits(f.y), Double.doubleToRawLongBits(f.z) });
		FLIPS.put(p.getUUID(), now + 60); // the landing is soft, however high the kick took him
		sound(p, SoundEvents.PHANTOM_SWOOP, 0.8f, 1.5f);
		// someone already right in front of him takes it at once
		if (kickHit(p, f)) {
			return;
		}
		if (kickBlocked(p, f)) {
			stopKick(p, f, true, now);
			return;
		}
		Vec3 v = f.scale(BlackPantherConfig.KICK_SPEED);
		if (p.onGround() && v.y < BlackPantherConfig.KICK_HOP) {
			v = new Vec3(v.x, BlackPantherConfig.KICK_HOP, v.z); // off the ground first, so a flat kick skims instead of scraping
		}
		AbilityHelpers.launchSelf(p, v);
	}

	/**
	 * v0.17.7 (user: "make the flying kick ability go where the player aims instead of just straight ahead please"): the whole look,
	 * pitch included -- aim up and he kicks upward, aim down and he dives foot first. Standing on the ground, a downward aim kicks
	 * straight ahead instead (there is nowhere to dive).
	 */
	public static Vec3 kickDirection(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		if (look.lengthSqr() < 1.0e-6 || (p.onGround() && look.y < 0.0)) {
			return flatLook(p);
		}
		return look.normalize();
	}

	/** The next step of the dash would run into a block: a wall ahead, a ceiling over an upward kick, the ground under a dive. */
	private static boolean kickBlocked(ServerPlayer p, Vec3 f) {
		Vec3 step = f.scale(BlackPantherConfig.KICK_SPEED);
		net.minecraft.world.phys.AABB box = p.getBoundingBox();
		boolean wall = (step.x * step.x + step.z * step.z) > 0.0025 && !p.level().noCollision(p, box.move(step.x, 0.0, step.z));
		boolean roofOrFloor = Math.abs(step.y) > 0.05 && !p.level().noCollision(p, box.move(0.0, step.y, 0.0));
		return wall || roofOrFloor;
	}

	/**
	 * The dash ends: run into a block ({@code blocked}, he stops dead where he is, nothing carried into the wall) or simply out of
	 * distance (he keeps a little of the way, never the full dash speed, so an upward kick does not sail on). Either way the fall
	 * after it is soft.
	 */
	private static void stopKick(ServerPlayer p, Vec3 f, boolean blocked, long now) {
		KICKS.remove(p.getUUID());
		FLIPS.put(p.getUUID(), now + 60);
		Vec3 v = blocked ? new Vec3(0.0, Math.min(0.0, p.getDeltaMovement().y) * 0.25, 0.0)
				: new Vec3(f.x * 0.35, net.minecraft.util.Mth.clamp(f.y * BlackPantherConfig.KICK_SPEED, -0.4, 0.25), f.z * 0.35);
		AbilityHelpers.launchSelf(p, v);
	}

	private static void tickKick(ServerPlayer p, long[] run, long now) {
		Vec3 f = new Vec3(Double.longBitsToDouble(run[1]), Double.longBitsToDouble(run[2]), Double.longBitsToDouble(run[3]));
		long age = now - run[0];
		if (!KICKS.containsKey(p.getUUID())) {
			return;
		}
		if (age > BlackPantherConfig.KICK_TICKS) {
			stopKick(p, f, false, now);
			return;
		}
		if (kickHit(p, f)) {
			return;
		}
		if (kickBlocked(p, f)) {
			stopKick(p, f, true, now);
			return;
		}
		AbilityHelpers.launchSelf(p, f.scale(BlackPantherConfig.KICK_SPEED));
		level(p).sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.5, p.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
	}

	/** The first creature within reach in front of him takes the kick; true (and the dash ends) if one did. */
	private static boolean kickHit(ServerPlayer p, Vec3 dir) {
		Vec3 foot = p.position().add(0, 0.8, 0).add(dir.scale(0.6));
		LivingEntity best = null;
		double bestSq = Double.MAX_VALUE;
		for (LivingEntity e : BlackPantherCombat.within(p, foot, BlackPantherConfig.KICK_REACH, e -> BlackPantherCombat.isTarget(p, e))) {
			double d = e.position().distanceToSqr(p.position());
			if (d < bestSq) {
				bestSq = d;
				best = e;
			}
		}
		if (best == null) {
			return false;
		}
		KICKS.remove(p.getUUID());
		BlackPantherCombat.strike(p, best, p.position(), BlackPantherConfig.KICK_DAMAGE, 1.1, 0.3);
		// he rebounds off them (back along the flat of the dash, a little up), and lands softly
		AbilityHelpers.launchSelf(p, new Vec3(-dir.x * 0.35, 0.3, -dir.z * 0.35));
		FLIPS.put(p.getUUID(), p.level().getGameTime() + 60);
		Vec3 c = best.position().add(0, best.getBbHeight() * 0.6, 0);
		level(p).sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level(p).sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 12, 0.3, 0.3, 0.3, 0.3);
		sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.8f);
		sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
		return true;
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

	// ---------------------------------------------------------------- V: the uppercut and the hunter

	/**
	 * v0.17.7 Rising Uppercut (V, user: "replace with another combat ability"): he drops low and drives up through the creature in
	 * front of him (within 3): 9 damage, launched into the air and dazed for 1.5 s. A boss only takes the damage.
	 */
	public static void uppercut(ServerPlayer p) {
		if (!begin(p, UPPERCUT)) {
			return;
		}
		LivingEntity target = inFront(p, BlackPantherConfig.UPPERCUT_RANGE);
		if (target == null) {
			BlackPanther.say(p, "message.projecthero.black_panther.grab_nothing", ChatFormatting.GRAY);
			return;
		}
		cooldown(p, UPPERCUT, BlackPantherConfig.UPPERCUT_COOLDOWN);
		anim(p, BlackPantherState.ANIM_UPPERCUT);
		boolean boss = BlackPantherCombat.isBoss(target);
		if (BlackPantherCombat.strike(p, target, p.position(), BlackPantherConfig.UPPERCUT_DAMAGE, 0.2, 0.0) && !boss) {
			AbilityHelpers.push(target, new Vec3(0, BlackPantherConfig.UPPERCUT_LIFT, 0));
			AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, BlackPantherConfig.UPPERCUT_STUN_TICKS, 2);
		}
		// he rises a little with the blow
		AbilityHelpers.launchSelf(p, new Vec3(0, 0.3, 0));
		FLIPS.put(p.getUUID(), p.level().getGameTime() + 30);
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.4, 0);
		level(p).sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level(p).sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 10, 0.2, 0.5, 0.2, 0.25);
		sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.1f);
		sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 0.8f);
	}

	/** Shift+V (v0.17.7, was V): Hunter's Tracking on or off. */
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

	// ---------------------------------------------------------------- C: the guard

	/** v0.17.7 Block (hold C, user: "a blocking ability please that reduces incoming damage"): the guard comes up. */
	public static void raiseBlock(ServerPlayer p) {
		if (BLOCKS.containsKey(p.getUUID()) || !begin(p, BLOCK)) {
			return;
		}
		BlackPantherState n = BlackPanther.state(p).copy();
		n.absorbing = true;
		BlackPanther.save(p, n);
		BlackPanther.reconcile(p);
		BLOCKS.put(p.getUUID(), p.level().getGameTime());
		sound(p, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.9f, 1.4f);
	}

	public static void lowerBlock(ServerPlayer p) {
		if (BLOCKS.remove(p.getUUID()) == null) {
			return;
		}
		BlackPantherState n = BlackPanther.state(p).copy();
		n.absorbing = false;
		n.abilityReadyAt.put(BLOCK, p.level().getGameTime() + BlackPantherConfig.BLOCK_COOLDOWN);
		BlackPanther.save(p, n);
		BlackPanther.reconcile(p);
	}

	/** The Block is up right now. */
	public static boolean blocking(ServerPlayer p) {
		return BLOCKS.containsKey(p.getUUID());
	}

	/** A hit from the front met the Block (the damage hook cuts it): the clang and a puff off the forearms. */
	static void blocked(ServerPlayer p) {
		Vec3 c = p.getEyePosition().add(p.getLookAngle().scale(0.6));
		level(p).sendParticles(ParticleTypes.CRIT, c.x, c.y - 0.3, c.z, 8, 0.2, 0.2, 0.2, 0.15);
		sound(p, SoundEvents.SHIELD_BLOCK, 1.0f, 1.2f);
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

	/** How many creatures are hunting him right now. */
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
		Long pound = POUNDS.get(id);
		if (pound != null) {
			tickPound(p, pound, now);
		}
		long[] kick = KICKS.get(id);
		if (kick != null) {
			tickKick(p, kick, now);
		}
		Long flip = FLIPS.get(id);
		if (flip != null) {
			tickFlip(p, flip, now);
		}
		Long block = BLOCKS.get(id);
		if (block != null && now - block >= BlackPantherConfig.BLOCK_MAX_TICKS) {
			lowerBlock(p);
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
