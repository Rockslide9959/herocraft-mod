package com.projecthero.mod.sentry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.sentry.data.SentryState;
import com.projecthero.mod.sentry.network.SentryFxPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 the Void's kit (merged with the Void, or the takeover at full strength), moveset 2 (2026-11-18, user: "also create a new
 * moveset for the void"). The comics' Void -- shadow tendrils, mind horror, nightmare manifestations, people swallowed into darkness --
 * and Thunderbolts* -- people turned into shadow imprints, the shadow spreading across surfaces, the black void spaces.
 * <ul>
 *   <li><b>R Tendril Lash</b>: two whips through a 100-degree cone, then the Impale (one creature further out, pinned and pulled in).</li>
 *   <li><b>Shift+R Erase</b>: the creature is seized, darkens for 0.75 s, and is wiped into a burnt shadow imprint.</li>
 *   <li><b>G Shadow Pool</b>: shadow spreads over the ground where he looks; anything standing in it is held down and hurt.</li>
 *   <li><b>Shift+G Swallow</b>: the creature is shut inside a black void sphere (held, blind, hurt), then spat out; dying inside it is
 *   absorbed.</li>
 *   <li><b>Z Nightmare</b>: a wave of horror; mobs flee, players see darkness and reel, figures of shadow stand among them.</li>
 *   <li><b>Shift+Z Shadow Eclipse</b>: he rises, a black sun with a gold corona opens over him; everything under it is drained while he
 *   regenerates, and it ends in a collapse.</li>
 *   <li><b>C Blot Out</b>: drains life and light around him to heal (his remaining Sun burns into health).</li>
 *   <li><b>Shift+C Shadow Clones</b>: three shadow doubles rise from his shadow and hunt.</li>
 *   <li><b>X Shadow Step</b>: a blink along his look through a smear of shadow, hurting what it passes.</li>
 *   <li><b>V Umbral Shroud</b> (round 3, new): a cloak of darkness -- hits cut 40%, projectiles swallowed, melee attackers withered.</li>
 *   <li><b>Shift+V Shadow Meld</b> (round 3, new): he sinks into the shadows -- invisible, faster, hunters lose him -- until he attacks.</li>
 *   <li><b>H</b> (round 3): back to the Sentry, below 90 Void ({@link #returnToSentry}).</li>
 * </ul>
 * Each move is a plain method the takeover's brain calls too ({@link VoidTakeover}); {@link #use} adds the player's cooldowns and the
 * Void each move feeds. The running effects ({@link Zone}s) keep the owner id, the dimension key, a position and an entity id only (never
 * a world or an entity), and are cleared on server stop.
 */
public final class VoidMoves {
	static final int POOL = 0, SPHERE = 1, ECLIPSE = 2, FEAR = 3, ERASE = 4, CLONE = 5;

	/** A running effect: who, which kind, where, since / until, the creature it holds (-1 none). Clones move ({@link #at}). */
	static final class Zone {
		final UUID owner;
		final ResourceKey<Level> dim;
		final int kind;
		final long start;
		final long until;
		final int target;
		/** Round 3: a stable id (the clones' figures are smoothed by it on the client). */
		final int id = NEXT_ID.incrementAndGet() & 0xFFFFF;
		Vec3 at;
		long next;

		Zone(UUID owner, ResourceKey<Level> dim, int kind, Vec3 at, long start, long until, int target) {
			this.owner = owner;
			this.dim = dim;
			this.kind = kind;
			this.at = at;
			this.start = start;
			this.until = until;
			this.target = target;
		}
	}

	private static final List<Zone> ZONES = new CopyOnWriteArrayList<>();
	private static final java.util.concurrent.atomic.AtomicInteger NEXT_ID = new java.util.concurrent.atomic.AtomicInteger();
	/** The {@code owner} field of a clone's SHADE payload: this plus its zone id (a nightmare figure sends -1). */
	public static final int CLONE_FX_ID = 1 << 24;
	/** Tendril Lash's combo step and when it last swung. */
	private static final Map<UUID, long[]> LASH = new ConcurrentHashMap<>();

	private VoidMoves() {
	}

	static void clearSessionState() {
		ZONES.clear();
		LASH.clear();
	}

	static void forget(UUID id) {
		ZONES.removeIf(z -> z.owner.equals(id));
		LASH.remove(id);
	}

	/** How many effects are running (tests). */
	public static int zones() {
		return ZONES.size();
	}

	/** How many effects of this player's of one kind are running (tests: {@link #POOL}...). */
	public static int zones(Player p, int kind) {
		return (int) ZONES.stream().filter(z -> z.owner.equals(p.getUUID()) && z.kind == kind).count();
	}

	// ---------------------------------------------------------------- input

	static void use(ServerPlayer p, String id) {
		if (SentryAbilityManager.RETURN.equals(id)) {
			returnToSentry(p);
			return;
		}
		if (Sentry.cooldownRemaining(p, id) > 0) {
			return;
		}
		boolean attack = java.util.Arrays.asList(SentryAbilityManager.VOID_ATTACKS).contains(id);
		if (fire(p, id, null)) {
			Sentry.startCooldown(p, id, cooldownOf(p, id));
			Sentry.addVoid(p, voidOf(id));
			if (attack) {
				endMeld(p); // an attack brings him out of the shadows
			}
		}
	}

	/** A Void move's cooldown: 25% shorter merged with the Void. */
	public static int cooldownOf(ServerPlayer p, String id) {
		int max = SentryAbilityManager.maxCooldown(id);
		return Sentry.merged(p) ? Math.max(1, Math.round(max * SentryConfig.MERGED_COOLDOWN)) : max;
	}

	/** Above 75 Void a Sentry key fires this instead (with its cooldown and Void). */
	static void misfire(ServerPlayer p, String id) {
		if (Sentry.cooldownRemaining(p, id) > 0) {
			return;
		}
		if (fire(p, id, null)) {
			Sentry.startCooldown(p, id, SentryAbilityManager.maxCooldown(id));
			Sentry.addVoid(p, voidOf(id));
		}
	}

	/** Fires a Void move ({@code target} given by the takeover's brain, else what he looks at). True if it went off. */
	public static boolean fire(ServerPlayer p, String id, LivingEntity target) {
		return switch (id) {
			case SentryAbilityManager.TENDRIL_LASH -> lash(p, target) >= 0;
			case SentryAbilityManager.ERASE -> erase(p, target);
			case SentryAbilityManager.SHADOW_POOL -> pool(p, target == null ? null : target.position());
			case SentryAbilityManager.SWALLOW -> swallow(p, target);
			case SentryAbilityManager.NIGHTMARE -> nightmare(p) >= 0;
			case SentryAbilityManager.SHADOW_ECLIPSE -> eclipse(p);
			case SentryAbilityManager.BLOT_OUT -> blotOut(p) >= 0;
			case SentryAbilityManager.SHADOW_CLONES -> clones(p);
			case SentryAbilityManager.SHADOW_STEP -> shadowStep(p);
			case SentryAbilityManager.UMBRAL_SHROUD -> shroud(p);
			case SentryAbilityManager.SHADOW_MELD -> meld(p);
			default -> false;
		};
	}

	static float voidOf(String id) {
		return switch (id) {
			case SentryAbilityManager.TENDRIL_LASH -> SentryConfig.LASH_VOID;
			case SentryAbilityManager.ERASE -> SentryConfig.ERASE_VOID;
			case SentryAbilityManager.SHADOW_POOL -> SentryConfig.POOL_VOID;
			case SentryAbilityManager.SWALLOW -> SentryConfig.SPHERE_VOID;
			case SentryAbilityManager.NIGHTMARE -> SentryConfig.NIGHTMARE_VOID;
			case SentryAbilityManager.SHADOW_ECLIPSE -> SentryConfig.ECLIPSE_VOID;
			case SentryAbilityManager.BLOT_OUT -> SentryConfig.BLOT_VOID;
			case SentryAbilityManager.SHADOW_CLONES -> SentryConfig.CLONE_VOID;
			case SentryAbilityManager.SHADOW_STEP -> SentryConfig.STEP_VOID;
			case SentryAbilityManager.UMBRAL_SHROUD -> SentryConfig.SHROUD_VOID;
			case SentryAbilityManager.SHADOW_MELD -> SentryConfig.MELD_VOID;
			default -> 0f;
		};
	}

	private static Vec3 centre(LivingEntity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	private static void add(ServerPlayer p, int kind, Vec3 at, long until, int target) {
		ZONES.add(new Zone(p.getUUID(), p.level().dimension(), kind, at, p.level().getGameTime(), until, target));
	}

	/** The creature in his sights (the takeover's given target first). */
	private static LivingEntity aim(ServerPlayer p, LivingEntity given, double range) {
		LivingEntity t = given != null && given.isAlive() && given.distanceTo(p) <= range + 2.0 ? given : SentryMoves.target(p, range);
		return t != null && Sentry.areaVictim(p, t) ? t : null;
	}

	// ---------------------------------------------------------------- R: Tendril Lash

	/** The combo step this swing is (0, 1 whips, 2 the Impale), advancing it. */
	private static int lashStep(ServerPlayer p) {
		long now = p.level().getGameTime();
		long[] st = LASH.computeIfAbsent(p.getUUID(), k -> new long[] { 0, Long.MIN_VALUE / 2 });
		if (now - st[1] > SentryConfig.LASH_COMBO_WINDOW) {
			st[0] = 0;
		}
		int step = (int) st[0];
		st[0] = (st[0] + 1) % 3;
		st[1] = now;
		return step;
	}

	/** One swing: a whip through the cone, or (every 3rd) the Impale. Returns how many it hit (never fails: a miss swings). */
	public static int lash(ServerPlayer p, LivingEntity given) {
		if (given != null && given.distanceTo(p) > SentryConfig.LASH_RANGE) {
			return impale(p, given); // the takeover's brain: out of whip reach, it spears
		}
		int step = lashStep(p);
		return step == 2 ? impale(p, given) : whip(p, step);
	}

	/** A whip of tendrils through a 100-degree cone out to 6. */
	public static int whip(ServerPlayer p, int side) {
		ServerLevel level = p.serverLevel();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double r = SentryConfig.LASH_RANGE;
		double cos = Math.cos(Math.toRadians(50));
		int n = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), e -> Sentry.areaVictim(p, e))) {
			Vec3 to = centre(e).subtract(eye);
			double d = to.length();
			if (d > r + e.getBbWidth() * 0.5 || (d > 1.0 && to.normalize().dot(look) < cos)) {
				continue;
			}
			if (Sentry.hit(p, e, SentryConfig.LASH_DAMAGE, true)) {
				n++;
			}
			AbilityHelpers.knockbackFrom(e, p.position(), 0.6);
			SentryFx.send(level, SentryFxPayload.TENDRIL, eye.add(0, -0.4, 0), 0.25f, 6, centre(e), p.getId());
		}
		// the sweep, from one side to the other (alternating)
		for (int i = -2; i <= 2; i++) {
			int k = side % 2 == 0 ? i : -i;
			Vec3 sweep = look.yRot((float) Math.toRadians(k * 22)).scale(r * 0.85);
			Vec3 at = eye.add(sweep);
			level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y - 0.3, at.z, 3, 0.2, 0.2, 0.2, 0.01);
		}
		Vec3 a = eye.add(look.yRot((float) Math.toRadians(side % 2 == 0 ? 45 : -45)).scale(r * 0.8)).add(0, -0.4, 0);
		Vec3 b = eye.add(look.yRot((float) Math.toRadians(side % 2 == 0 ? -45 : 45)).scale(r * 0.8)).add(0, -0.4, 0);
		SentryFx.send(level, SentryFxPayload.TENDRIL, eye.add(0, -0.4, 0), 0.22f, 5, a, p.getId());
		SentryFx.send(level, SentryFxPayload.TENDRIL, eye.add(0, -0.4, 0), 0.22f, 5, b, p.getId());
		p.swing(side % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
		Sentry.sound(p, SoundEvents.WARDEN_ATTACK_IMPACT, 0.8f, 1.3f);
		return n;
	}

	/** The Impale: a tendril spears one creature out to 9, pins it and drags it in. */
	public static int impale(ServerPlayer p, LivingEntity given) {
		ServerLevel level = p.serverLevel();
		Sentry.pose(p, SentryState.POSE_IMPALE, 8);
		p.swing(InteractionHand.MAIN_HAND, true);
		LivingEntity t = aim(p, given, SentryConfig.IMPALE_RANGE);
		if (t == null) {
			// nothing in line: the nearest in front within reach
			Vec3 look = p.getLookAngle();
			t = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(SentryConfig.IMPALE_RANGE), e -> Sentry.areaVictim(p, e)
					&& e.distanceTo(p) <= SentryConfig.IMPALE_RANGE && centre(e).subtract(p.getEyePosition()).normalize().dot(look) > 0.8)
					.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).orElse(null);
		}
		Vec3 from = p.getEyePosition().add(0, -0.3, 0);
		if (t == null) {
			SentryFx.send(level, SentryFxPayload.TENDRIL, from, 0.35f, 8, from.add(p.getLookAngle().scale(SentryConfig.IMPALE_RANGE)), p.getId());
			Sentry.sound(p, SoundEvents.TRIDENT_THROW.value(), 0.8f, 0.5f);
			return 0;
		}
		Vec3 c = centre(t);
		SentryFx.send(level, SentryFxPayload.TENDRIL, from, 0.4f, 12, c, p.getId());
		int n = Sentry.hit(p, t, SentryConfig.IMPALE_DAMAGE, true) ? 1 : 0;
		if (t.isAlive()) {
			AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, SentryConfig.IMPALE_PIN_TICKS, 3);
			if (SentryMoves.liftable(t)) {
				Vec3 in = p.position().subtract(t.position());
				t.setDeltaMovement(in.normalize().scale(Math.min(1.0, in.length() * 0.12)).add(0, 0.2, 0));
				t.hurtMarked = true;
			}
		}
		level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 14, 0.2, 0.3, 0.2, 0.08);
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, c.x, c.y, c.z, 4, 0.2, 0.2, 0.2, 0.1);
		Sentry.sound(p, SoundEvents.TRIDENT_HIT, 1.0f, 0.5f);
		Sentry.sound(p, SoundEvents.WARDEN_ATTACK_IMPACT, 0.9f, 0.7f);
		return n;
	}

	// ---------------------------------------------------------------- Shift+R: Erase

	/** The creature is seized and darkens; {@link SentryConfig#ERASE_DELAY_TICKS} later it is wiped out. False with nothing in sight. */
	public static boolean erase(ServerPlayer p, LivingEntity given) {
		LivingEntity t = aim(p, given, SentryConfig.ERASE_RANGE);
		if (t == null) {
			Sentry.say(p, "message.projecthero.sentry.no_target", ChatFormatting.GRAY);
			return false;
		}
		ServerLevel level = p.serverLevel();
		long now = level.getGameTime();
		add(p, ERASE, t.position(), now + SentryConfig.ERASE_DELAY_TICKS, t.getId());
		Sentry.pose(p, SentryState.POSE_ERASE, SentryConfig.ERASE_DELAY_TICKS + 6);
		SentryFx.send(level, SentryFxPayload.TENDRIL, p.getEyePosition().add(0, -0.3, 0), 0.18f, SentryConfig.ERASE_DELAY_TICKS, centre(t), p.getId());
		Sentry.sound(p, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.7f, 0.5f);
		return true;
	}

	private static void tickErase(ServerLevel level, ServerPlayer owner, Zone z, long now) {
		Entity e = level.getEntity(z.target);
		if (!(e instanceof LivingEntity t) || !t.isAlive()) {
			ZONES.remove(z);
			return;
		}
		Vec3 c = centre(t);
		if (now < z.until) {
			// seized: still, darkening, the shadow already on the ground
			t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
			t.hurtMarked = true;
			level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 4, t.getBbWidth() * 0.4, t.getBbHeight() * 0.4, t.getBbWidth() * 0.4, 0.0);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 2, t.getBbWidth() * 0.3, t.getBbHeight() * 0.4, t.getBbWidth() * 0.3, 0.0);
			return;
		}
		ZONES.remove(z);
		SentryFx.send(level, SentryFxPayload.ERASE, c, t.getBbHeight(), 20, owner.getEyePosition(), owner.getId());
		Sentry.hit(owner, t, SentryConfig.ERASE_DAMAGE, true);
		if (!t.isAlive() || t.isDeadOrDying()) {
			Sentry.shadow(level, t);
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 30, t.getBbWidth() * 0.5, t.getBbHeight() * 0.5, t.getBbWidth() * 0.5, 0.02);
		level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 20, 0.3, 0.5, 0.3, 0.05);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8f, 0.5f);
	}

	// ---------------------------------------------------------------- G: Shadow Pool

	/** A pool of shadow at {@code at} (or on the ground where he looks, 24 out). */
	public static boolean pool(ServerPlayer p, Vec3 at) {
		ServerLevel level = p.serverLevel();
		Vec3 c = at;
		if (c == null) {
			BlockHitResult bh = AbilityHelpers.raycastBlock(p, SentryConfig.POOL_RANGE);
			if (bh != null && bh.getType() == HitResult.Type.BLOCK) {
				c = bh.getDirection() == net.minecraft.core.Direction.UP ? bh.getLocation() : ground(level, bh.getLocation());
			} else {
				c = ground(level, p.getEyePosition().add(p.getLookAngle().scale(SentryConfig.POOL_RANGE * 0.5)));
			}
		} else {
			c = ground(level, c.add(0, 0.5, 0));
		}
		long now = level.getGameTime();
		add(p, POOL, c, now + SentryConfig.POOL_TICKS, -1);
		Sentry.pose(p, SentryState.POSE_POOL, 14);
		SentryFx.send(level, SentryFxPayload.POOL, c.add(0, 0.03, 0), (float) SentryConfig.POOL_RADIUS, SentryConfig.POOL_TICKS, null, p.getId());
		SentryFx.send(level, SentryFxPayload.TENDRIL, p.getEyePosition().add(0, -0.4, 0), 0.2f, 8, c, p.getId());
		level.playSound(null, c.x, c.y, c.z, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.PLAYERS, 1.5f, 0.5f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.2f, 0.6f);
		return true;
	}

	/** The ground under a point (down to 12 blocks), else the point. */
	static Vec3 ground(ServerLevel level, Vec3 from) {
		BlockHitResult bh = level.clip(new ClipContext(from, from.add(0, -12, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
				net.minecraft.world.phys.shapes.CollisionContext.empty()));
		return bh.getType() == HitResult.Type.BLOCK ? bh.getLocation() : from;
	}

	/** The pool's radius this tick: it spreads from 2 to 6 over 2 s. */
	public static double poolRadius(long age) {
		double k = Math.min(1.0, Math.max(0, age) / (double) SentryConfig.POOL_GROW_TICKS);
		return SentryConfig.POOL_START_RADIUS + (SentryConfig.POOL_RADIUS - SentryConfig.POOL_START_RADIUS) * k;
	}

	private static void tickPool(ServerLevel level, ServerPlayer owner, Zone z, long now) {
		double r = poolRadius(now - z.start);
		boolean bite = (now - z.start) % SentryConfig.POOL_DAMAGE_INTERVAL == 0 && now > z.start;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(z.at, z.at).inflate(r, 2.5, r), e -> Sentry.areaVictim(owner, e))) {
			double dx = e.getX() - z.at.x;
			double dz = e.getZ() - z.at.z;
			double dy = e.getY() - z.at.y;
			if (dx * dx + dz * dz > r * r || dy < -1.0 || dy > 2.0) {
				continue;
			}
			// held down: no jumping, dragged slow, sinking a little
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 15, 3);
			Vec3 v = e.getDeltaMovement();
			e.setDeltaMovement(v.x * 0.4, Math.min(v.y, -0.08), v.z * 0.4);
			e.hurtMarked = true;
			if (bite) {
				Sentry.hit(owner, e, SentryConfig.POOL_DAMAGE, true);
				level.sendParticles(ParticleTypes.SQUID_INK, e.getX(), e.getY() + 0.2, e.getZ(), 4, 0.3, 0.1, 0.3, 0.02);
			}
		}
		if (now % 3 == 0) {
			level.sendParticles(ParticleTypes.SQUID_INK, z.at.x, z.at.y + 0.1, z.at.z, 3, r * 0.5, 0.02, r * 0.5, 0.0);
			level.sendParticles(ParticleTypes.SCULK_SOUL, z.at.x, z.at.y + 0.1, z.at.z, 1, r * 0.4, 0.02, r * 0.4, 0.01);
		}
	}

	// ---------------------------------------------------------------- Shift+G: Swallow

	/** Shuts the creature (the given one, or the one he looks at within 16) in a black void sphere. False with nothing in sight. */
	public static boolean swallow(ServerPlayer p, LivingEntity given) {
		LivingEntity t = aim(p, given, SentryConfig.SPHERE_RANGE);
		if (t == null) {
			Sentry.say(p, "message.projecthero.sentry.no_target", ChatFormatting.GRAY);
			return false;
		}
		ServerLevel level = p.serverLevel();
		long now = level.getGameTime();
		Vec3 hold = SentryMoves.liftable(t) ? t.position().add(0, 0.6, 0) : t.position();
		add(p, SPHERE, hold, now + SentryConfig.SPHERE_TICKS, t.getId());
		Sentry.pose(p, SentryState.POSE_SPHERE, 20);
		float r = 0.8f + Math.max(t.getBbWidth(), t.getBbHeight()) * 0.65f;
		SentryFx.send(level, SentryFxPayload.SPHERE, centre(t), r, SentryConfig.SPHERE_TICKS, null, t.getId());
		SentryFx.send(level, SentryFxPayload.TENDRIL, p.getEyePosition().add(0, -0.4, 0), 0.25f, 10, centre(t), p.getId());
		level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.6f, 0.4f);
		level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.0f, 0.6f);
		return true;
	}

	private static void tickSphere(ServerLevel level, ServerPlayer owner, Zone z, long now) {
		Entity e = level.getEntity(z.target);
		if (!(e instanceof LivingEntity t) || !t.isAlive()) {
			// it died inside: absorbed
			ZONES.remove(z);
			owner.heal(SentryConfig.SPHERE_ABSORB_HEAL);
			level.sendParticles(ParticleTypes.SCULK_SOUL, z.at.x, z.at.y + 0.5, z.at.z, 12, 0.4, 0.5, 0.4, 0.03);
			SentryFx.send(level, SentryFxPayload.TENDRIL, z.at.add(0, 0.5, 0), 0.2f, 10, owner.position().add(0, 1.0, 0), owner.getId());
			return;
		}
		if (now >= z.until) {
			// spat out
			ZONES.remove(z);
			Sentry.hit(owner, t, SentryConfig.SPHERE_SPIT_DAMAGE, true);
			AbilityHelpers.knockbackFrom(t, owner.position(), 1.4);
			AbilityHelpers.push(t, new Vec3(0, 0.4, 0));
			Vec3 c = centre(t);
			level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 30, 0.6, 0.6, 0.6, 0.12);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 20, 0.5, 0.5, 0.5, 0.1);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7f, 0.8f);
			return;
		}
		// held inside, blind
		if (SentryMoves.liftable(t)) {
			if (t.distanceToSqr(z.at) > 0.04) {
				t.teleportTo(z.at.x, z.at.y, z.at.z);
			}
			t.setDeltaMovement(Vec3.ZERO);
		} else {
			t.setDeltaMovement(t.getDeltaMovement().multiply(0.1, 1.0, 0.1));
		}
		t.hurtMarked = true;
		t.resetFallDistance();
		if ((now - z.start) % 10 == 0) {
			t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false, true));
			t.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false, true));
		}
		if (now > z.start && (now - z.start) % SentryConfig.SPHERE_DAMAGE_INTERVAL == 0) {
			Sentry.hit(owner, t, SentryConfig.SPHERE_DAMAGE, true);
		}
		if (now % 3 == 0) {
			Vec3 c = centre(t);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 3, 0.6, 0.6, 0.6, 0.0);
		}
	}

	// ---------------------------------------------------------------- Z: Nightmare

	/** The wave of horror. Returns how many it caught. */
	public static int nightmare(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		double r = SentryConfig.NIGHTMARE_RADIUS;
		long now = level.getGameTime();
		int n = 0;
		List<LivingEntity> caught = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
				e -> Sentry.areaVictim(p, e) && e.distanceToSqr(p) <= r * r));
		for (LivingEntity e : caught) {
			Sentry.hit(p, e, SentryConfig.NIGHTMARE_DAMAGE, true);
			n++;
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SentryConfig.NIGHTMARE_TICKS, 0, false, true, true));
			if (e instanceof Player) {
				e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, SentryConfig.NIGHTMARE_TICKS, 0, false, true, true));
				e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, SentryConfig.NIGHTMARE_TICKS, 0, false, true, true));
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, SentryConfig.NIGHTMARE_TICKS, 0);
			} else if (e instanceof Mob mob) {
				mob.setTarget(null);
				add(p, FEAR, p.position(), now + SentryConfig.NIGHTMARE_TICKS, e.getId());
			}
		}
		// shadow figures among them: nightmare manifestations
		for (int i = 0; i < Math.min(4, Math.max(2, caught.size())); i++) {
			LivingEntity near = caught.isEmpty() ? null : caught.get(i % caught.size());
			double a = p.getRandom().nextDouble() * Math.PI * 2.0;
			Vec3 base = near != null ? near.position() : p.position();
			Vec3 at = ground(level, base.add(Math.cos(a) * 2.2, 1.0, Math.sin(a) * 2.2));
			SentryFx.send(level, SentryFxPayload.SHADE, at, 2.4f, 40, near != null ? centre(near) : p.getEyePosition(), -1);
		}
		Sentry.pose(p, SentryState.POSE_NIGHTMARE, 18);
		SentryFx.send(level, SentryFxPayload.NIGHTMARE, p.position().add(0, 0.2, 0), (float) r, 26, null, p.getId());
		level.sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 1.0, p.getZ(), 20, r * 0.4, 0.8, r * 0.4, 0.02);
		Sentry.sound(p, SoundEvents.ELDER_GUARDIAN_CURSE, 1.0f, 0.6f);
		Sentry.sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.5f, 0.6f);
		return n;
	}

	/** Every 10 ticks a frightened mob runs away from where the Void stood. */
	private static void tickFear(ServerLevel level, Zone z, long now) {
		Entity e = level.getEntity(z.target);
		if (!(e instanceof Mob mob) || !mob.isAlive()) {
			ZONES.remove(z);
			return;
		}
		mob.setTarget(null);
		if ((now - z.start) % 10 == 0 && mob instanceof PathfinderMob pm) {
			Vec3 away = pm.position().subtract(z.at);
			away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : new Vec3(away.x, 0, away.z).normalize();
			Vec3 to = pm.position().add(away.scale(10.0));
			pm.getNavigation().moveTo(to.x, to.y, to.z, 1.5);
		}
		if (now % 8 == 0) {
			level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY() + mob.getBbHeight() + 0.2, mob.getZ(), 2, 0.15, 0.1, 0.15, 0.0);
		}
	}

	// ---------------------------------------------------------------- Shift+Z: Shadow Eclipse

	public static boolean eclipse(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		long now = level.getGameTime();
		long until = now + SentryConfig.ECLIPSE_WINDUP_TICKS + SentryConfig.ECLIPSE_TICKS;
		add(p, ECLIPSE, p.position(), until, -1);
		SentryState n = Sentry.state(p).copy();
		n.nightfallUntil = until;
		Sentry.save(p, n);
		Sentry.pose(p, SentryState.POSE_ECLIPSE, SentryConfig.ECLIPSE_WINDUP_TICKS + 20);
		SentryFx.send(level, SentryFxPayload.ECLIPSE, p.position(), (float) SentryConfig.ECLIPSE_RADIUS,
				SentryConfig.ECLIPSE_WINDUP_TICKS + SentryConfig.ECLIPSE_TICKS, null, p.getId());
		Sentry.sound(p, SoundEvents.WARDEN_EMERGE, 1.2f, 0.6f);
		return true;
	}

	/** Whether the eclipse is past its wind-up (tests). */
	public static boolean eclipseOpen(ServerPlayer p) {
		long now = p.level().getGameTime();
		return ZONES.stream().anyMatch(z -> z.owner.equals(p.getUUID()) && z.kind == ECLIPSE && now - z.start >= SentryConfig.ECLIPSE_WINDUP_TICKS);
	}

	private static void tickEclipse(ServerLevel level, ServerPlayer owner, Zone z, long now) {
		z.at = owner.position();
		long age = now - z.start;
		double r = SentryConfig.ECLIPSE_RADIUS;
		if (age < SentryConfig.ECLIPSE_WINDUP_TICKS) {
			// the wind-up: he rises while the dark gathers
			Vec3 v = owner.getDeltaMovement();
			AbilityHelpers.launchSelf(owner, new Vec3(v.x * 0.3, 0.12, v.z * 0.3));
			if (age % 2 == 0) {
				level.sendParticles(ParticleTypes.SQUID_INK, owner.getX(), owner.getY() + 1.0, owner.getZ(), 8, 2.5, 2.5, 2.5, -0.05);
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, owner.getX(), owner.getY() + 1.0, owner.getZ(), 6, 3.0, 3.0, 3.0, 0.0);
			}
			if (age == SentryConfig.ECLIPSE_WINDUP_TICKS - 1) {
				Sentry.sound(owner, SoundEvents.WARDEN_SONIC_BOOM, 1.2f, 0.4f);
			}
			return;
		}
		boolean end = now >= z.until - 1;
		if (end || age % 20 == 0) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(r), e -> e.distanceToSqr(owner) <= r * r)) {
				if (e == owner) {
					if (!end) {
						owner.heal(SentryConfig.ECLIPSE_HEAL);
					}
				} else if (Sentry.areaVictim(owner, e)) {
					Sentry.hit(owner, e, end ? SentryConfig.ECLIPSE_COLLAPSE_DAMAGE : SentryConfig.ECLIPSE_DAMAGE, true);
					e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, true));
					if (end) {
						Vec3 in = owner.position().subtract(e.position());
						AbilityHelpers.push(e, in.normalize().scale(0.8));
					}
				}
			}
		}
		if (end) {
			ZONES.remove(z);
			SentryFx.send(level, SentryFxPayload.NIGHTMARE, owner.position().add(0, 0.5, 0), (float) r, 20, null, owner.getId());
			level.sendParticles(ParticleTypes.SQUID_INK, owner.getX(), owner.getY() + 1.0, owner.getZ(), 120, r * 0.4, 2.0, r * 0.4, 0.2);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, owner.getX(), owner.getY() + 1.0, owner.getZ(), 1, 0, 0, 0, 0);
			level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0f, 0.4f);
			return;
		}
		if (now % 4 == 0) {
			level.sendParticles(ParticleTypes.SQUID_INK, owner.getX(), owner.getY() + 1.0, owner.getZ(), 10, r * 0.6, 2.0, r * 0.6, 0.0);
		}
		if (age % 40 == 0) {
			Sentry.sound(owner, SoundEvents.WARDEN_HEARTBEAT, 2.0f, 0.5f);
		}
	}

	// ---------------------------------------------------------------- C: Blot Out

	/** Drains life and light around him to heal. Returns how many it drained. */
	public static int blotOut(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		double r = SentryConfig.BLOT_RADIUS;
		int n = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), e -> Sentry.areaVictim(p, e)
				&& e.distanceToSqr(p) <= r * r)) {
			if (Sentry.hit(p, e, SentryConfig.BLOT_DAMAGE, true)) {
				n++;
			}
			e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false, true));
			SentryFx.send(level, SentryFxPayload.TENDRIL, centre(e), 0.15f, 14, p.position().add(0, 1.0, 0), p.getId());
		}
		float heal = Math.min(SentryConfig.BLOT_HEAL_MAX, n * SentryConfig.BLOT_HEAL_PER_VICTIM);
		// his own light goes too: what is left of the Sun burns into health
		SentryState s = Sentry.state(p);
		if (s.sun > 0f && !Sentry.free(p)) {
			heal += s.sun / SentryConfig.BLOT_SUN_PER_HEALTH;
			Sentry.setSun(p, 0f);
		}
		if (heal > 0f) {
			p.heal(heal);
		}
		Sentry.pose(p, SentryState.POSE_BLOT, 18);
		SentryFx.send(level, SentryFxPayload.NIGHTMARE, p.position().add(0, 0.2, 0), (float) r, 16, null, p.getId());
		level.sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 1.0, p.getZ(), 14, 0.5, 0.8, 0.5, 0.03);
		Sentry.sound(p, SoundEvents.SCULK_CATALYST_BLOOM, 1.4f, 0.5f);
		Sentry.sound(p, SoundEvents.BEACON_DEACTIVATE, 1.0f, 0.5f);
		return n;
	}

	// ---------------------------------------------------------------- Shift+C: Shadow Clones

	public static boolean clones(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		long now = level.getGameTime();
		for (int i = 0; i < SentryConfig.CLONE_COUNT; i++) {
			double a = Math.toRadians(p.getYRot() + 90 + i * 360.0 / SentryConfig.CLONE_COUNT);
			Vec3 at = p.position().add(-Math.sin(a) * 1.8, 0, Math.cos(a) * 1.8);
			Zone z = new Zone(p.getUUID(), level.dimension(), CLONE, at, now, now + SentryConfig.CLONE_TICKS, -1);
			z.next = now + 10 + i * 4;
			ZONES.add(z);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.0, at.z, 16, 0.3, 0.8, 0.3, 0.02);
			level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.2, at.z, 10, 0.4, 0.1, 0.4, 0.02);
		}
		Sentry.pose(p, SentryState.POSE_CLONES, 18);
		Sentry.sound(p, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.8f, 0.4f);
		return true;
	}

	private static void tickClone(ServerLevel level, ServerPlayer owner, Zone z, long now) {
		double hunt = SentryConfig.CLONE_HUNT_RANGE;
		LivingEntity t = level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(hunt), e -> Sentry.areaVictim(owner, e)
				&& e.distanceToSqr(owner) <= hunt * hunt).stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(z.at))).orElse(null);
		Vec3 want = t != null ? t.position() : owner.position().add(Vec3.directionFromRotation(0f, owner.getYRot() + 180f).scale(1.6));
		Vec3 to = want.subtract(z.at);
		double flat = Math.sqrt(to.x * to.x + to.z * to.z);
		double step = Math.min(SentryConfig.CLONE_SPEED, Math.max(0, flat - (t != null ? 1.2 : 0.3)));
		Vec3 move = flat > 1.0e-4 ? new Vec3(to.x / flat * step, 0, to.z / flat * step) : Vec3.ZERO;
		double y = z.at.y + Math.max(-0.5, Math.min(0.5, to.y)) * 0.3;
		z.at = new Vec3(z.at.x + move.x, y, z.at.z + move.z);
		if (t != null && now >= z.next && t.distanceToSqr(z.at) <= 2.4 * 2.4) {
			z.next = now + SentryConfig.CLONE_STRIKE_INTERVAL;
			Sentry.hit(owner, t, SentryConfig.CLONE_DAMAGE, true);
			AbilityHelpers.knockbackFrom(t, z.at, 0.5);
			SentryFx.send(level, SentryFxPayload.TENDRIL, z.at.add(0, 1.2, 0), 0.2f, 6, centre(t), -1);
			level.playSound(null, z.at.x, z.at.y, z.at.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 0.6f, 1.5f);
		}
		if (now % 2 == 0) {
			Vec3 face = t != null ? centre(t) : z.at.add(Vec3.directionFromRotation(0f, owner.getYRot()).scale(3)).add(0, 1.5, 0);
			SentryFx.send(level, SentryFxPayload.SHADE, z.at, 1.9f, 2, face, CLONE_FX_ID + z.id);
		}
		if (now % 4 == 0) {
			level.sendParticles(ParticleTypes.SMOKE, z.at.x, z.at.y + 1.0, z.at.z, 2, 0.2, 0.5, 0.2, 0.0);
		}
		if (now >= z.until - 1) {
			ZONES.remove(z);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, z.at.x, z.at.y + 1.0, z.at.z, 12, 0.3, 0.8, 0.3, 0.02);
		}
	}

	/** The clones' positions (tests). */
	public static List<Vec3> clonePositions(Player p) {
		List<Vec3> out = new ArrayList<>();
		for (Zone z : ZONES) {
			if (z.owner.equals(p.getUUID()) && z.kind == CLONE) {
				out.add(z.at);
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- X: Shadow Step

	/** A blink along his look, up to 24 (short of walls); what it passes is hit. False when there is no room to go. */
	public static boolean shadowStep(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		Vec3 eye = p.getEyePosition();
		Vec3 dir = p.getLookAngle();
		BlockHitResult bh = level.clip(new ClipContext(eye, eye.add(dir.scale(SentryConfig.STEP_RANGE)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, p));
		double dist = bh.getType() == HitResult.Type.MISS ? SentryConfig.STEP_RANGE : bh.getLocation().distanceTo(eye) - 0.8;
		Vec3 from = p.position();
		Vec3 dest = null;
		for (double d = dist; d >= 1.5; d -= 0.5) {
			Vec3 cand = from.add(dir.scale(d));
			if (level.noCollision(p, p.getBoundingBox().move(cand.subtract(from)))) {
				dest = cand;
				break;
			}
		}
		if (dest == null) {
			return false;
		}
		// the smear of shadow it leaves, and what it passes through
		final Vec3 a = from;
		final Vec3 b = dest;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(1.5), e -> Sentry.areaVictim(p, e))) {
			if (segDist(centre(e), a.add(0, 1.0, 0), b.add(0, 1.0, 0)) <= 1.5 + e.getBbWidth() * 0.5) {
				Sentry.hit(p, e, SentryConfig.STEP_DAMAGE, true);
				e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 30, 0, false, false, true));
			}
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y + 1.0, from.z, 20, 0.3, 0.8, 0.3, 0.02);
		SentryFx.send(level, SentryFxPayload.TENDRIL, a.add(0, 1.0, 0), 0.55f, 12, b.add(0, 1.0, 0), p.getId());
		p.teleportTo(dest.x, dest.y, dest.z);
		p.resetFallDistance();
		level.sendParticles(ParticleTypes.LARGE_SMOKE, dest.x, dest.y + 1.0, dest.z, 20, 0.3, 0.8, 0.3, 0.02);
		level.sendParticles(ParticleTypes.SQUID_INK, dest.x, dest.y + 0.5, dest.z, 12, 0.3, 0.5, 0.3, 0.02);
		level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.5f);
		return true;
	}

	private static double segDist(Vec3 pt, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double len2 = ab.lengthSqr();
		double t = len2 < 1.0e-6 ? 0.0 : Math.max(0.0, Math.min(1.0, pt.subtract(a).dot(ab) / len2));
		return pt.distanceTo(a.add(ab.scale(t)));
	}

	// ---------------------------------------------------------------- V: Umbral Shroud (round 3)

	/** A cloak of darkness for 8 s: hits cut 40% ({@code SentryDamage}), projectiles swallowed ({@link #tick}), melee attackers withered. */
	public static boolean shroud(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		SentryState n = Sentry.state(p).copy();
		n.shroudUntil = level.getGameTime() + SentryConfig.SHROUD_TICKS;
		Sentry.save(p, n);
		Sentry.pose(p, SentryState.POSE_SHROUD, 14);
		SentryFx.send(level, SentryFxPayload.SHROUD, p.position(), (float) SentryConfig.SHROUD_RADIUS, SentryConfig.SHROUD_TICKS, null, p.getId());
		level.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.6, 0.9, 0.6, 0.04);
		Sentry.sound(p, SoundEvents.WARDEN_NEARBY_CLOSER, 1.0f, 0.6f);
		Sentry.sound(p, SoundEvents.SCULK_CATALYST_BLOOM, 1.0f, 0.4f);
		return true;
	}

	/** The shroud is up. Client-safe. */
	public static boolean shrouded(Player p) {
		SentryState s = Sentry.peek(p);
		return s != null && s.hasPower && s.shroudUntil > p.level().getGameTime();
	}

	/** Melee on him while the shroud is up: the attacker is withered and blinded (from {@code SentryDamage}). */
	static void retaliate(ServerPlayer victim, net.minecraft.world.damagesource.DamageSource source) {
		if (!shrouded(victim) || !(source.getDirectEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != source.getEntity()
				|| attacker == victim || attacker.distanceToSqr(victim) > 5.0 * 5.0 || !Sentry.areaVictim(victim, attacker)) {
			return;
		}
		attacker.addEffect(new MobEffectInstance(MobEffects.WITHER, SentryConfig.SHROUD_WITHER_TICKS, 0, false, true, true));
		attacker.addEffect(new MobEffectInstance(MobEffects.DARKNESS, SentryConfig.SHROUD_WITHER_TICKS, 0, false, false, true));
		if (!(attacker instanceof Player)) {
			AbilityHelpers.applyControl(attacker, MobEffects.BLINDNESS, SentryConfig.SHROUD_WITHER_TICKS, 0);
		}
		Vec3 c = centre(attacker);
		victim.serverLevel().sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 8, 0.3, 0.4, 0.3, 0.04);
	}

	// ---------------------------------------------------------------- Shift+V: Shadow Meld (round 3)

	/** He sinks into the shadows for 6 s: invisible, Speed II, every mob hunting him within 32 loses him. Ends early on a Void attack. */
	public static boolean meld(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		SentryState n = Sentry.state(p).copy();
		n.meldUntil = level.getGameTime() + SentryConfig.MELD_TICKS;
		Sentry.save(p, n);
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, SentryConfig.MELD_TICKS, 0, false, false, true));
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SentryConfig.MELD_TICKS, SentryConfig.MELD_SPEED_AMP, false, false, true));
		loseHunters(p);
		Sentry.pose(p, SentryState.POSE_MELD, 10);
		SentryFx.send(level, SentryFxPayload.MELD, p.position(), 1.6f, 14, null, p.getId());
		level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 0.8, p.getZ(), 24, 0.4, 0.6, 0.4, 0.02);
		level.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 0.2, p.getZ(), 20, 0.5, 0.1, 0.5, 0.02);
		Sentry.sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.6f, 0.4f);
		return true;
	}

	/** Melded right now. Client-safe. */
	public static boolean melded(Player p) {
		SentryState s = Sentry.peek(p);
		return s != null && s.hasPower && s.meldUntil > p.level().getGameTime();
	}

	/** Every mob within {@link SentryConfig#MELD_RADIUS} that hunts him forgets him. Returns how many (tests). */
	public static int loseHunters(ServerPlayer p) {
		int n = 0;
		double r = SentryConfig.MELD_RADIUS;
		for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(r), m -> m.getTarget() == p)) {
			m.setTarget(null);
			m.getNavigation().stop();
			n++;
		}
		return n;
	}

	/** Out of the shadows (a Void attack, or the time is up): visible again. */
	static void endMeld(ServerPlayer p) {
		SentryState s = Sentry.state(p);
		if (s.meldUntil == 0L) {
			return;
		}
		SentryState n = s.copy();
		n.meldUntil = 0L;
		Sentry.save(p, n);
		p.removeEffect(MobEffects.INVISIBILITY);
		p.removeEffect(MobEffects.MOVEMENT_SPEED);
		p.serverLevel().sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 0.8, p.getZ(), 12, 0.3, 0.6, 0.3, 0.02);
	}

	// ---------------------------------------------------------------- H: Return (round 3: H; was Shift+V / V)

	static void returnToSentry(ServerPlayer p) {
		SentryState s = Sentry.state(p);
		if (s.voidLevel >= SentryConfig.RETURN_MAX_VOID) {
			Sentry.say(p, "message.projecthero.sentry.return_failed", ChatFormatting.DARK_RED);
			return;
		}
		Sentry.setVoidForm(p, false);
		ServerLevel level = p.serverLevel();
		level.sendParticles(Sentry.dust(Sentry.GOLD, 1.6f), p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.5, 1.0, 0.5, 0.0);
		Sentry.sound(p, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.5f);
	}

	// ---------------------------------------------------------------- tick (effects and the form's look)

	public static void tick(ServerPlayer p) {
		if (Sentry.voidForm(p) && p.tickCount % 3 == 0) {
			p.serverLevel().sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.0, p.getZ(), 3, 0.3, 0.7, 0.3, 0.01);
		}
		SentryState s = Sentry.state(p);
		long now = p.level().getGameTime();
		if (s.nightfallUntil != 0L && s.nightfallUntil <= now) {
			SentryState n = s.copy();
			n.nightfallUntil = 0L;
			Sentry.save(p, n);
		}
		s = Sentry.state(p);
		if (s.shroudUntil != 0L) {
			if (s.shroudUntil <= now) {
				SentryState n = s.copy();
				n.shroudUntil = 0L;
				Sentry.save(p, n);
			} else {
				// projectiles reaching the shroud are swallowed by the dark
				ServerLevel level = p.serverLevel();
				double r = SentryConfig.SHROUD_RADIUS;
				Vec3 c = p.position().add(0, 1.0, 0);
				for (net.minecraft.world.entity.projectile.Projectile proj : level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
						new AABB(c, c).inflate(r + 1.0), e -> e.isAlive() && e.distanceToSqr(c) <= (r + 0.5) * (r + 0.5) && SentryMoves.hostileShot(p, e))) {
					level.sendParticles(ParticleTypes.SQUID_INK, proj.getX(), proj.getY(), proj.getZ(), 8, 0.15, 0.15, 0.15, 0.02);
					proj.discard();
				}
				if (now % 5 == 0) {
					level.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.0, p.getZ(), 4, r * 0.4, 0.8, r * 0.4, 0.0);
				}
			}
		}
		s = Sentry.state(p);
		if (s.meldUntil != 0L) {
			if (s.meldUntil <= now) {
				endMeld(p);
			} else if (now % 10 == 0) {
				loseHunters(p);
			}
		}
	}

	/** Every effect, once per server tick per world (from {@code SentryMod}). */
	public static void tickLevel(ServerLevel level) {
		if (ZONES.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		for (Zone z : ZONES) {
			if (!z.dim.equals(level.dimension())) {
				continue;
			}
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(z.owner);
			boolean timed = z.kind != ERASE && z.kind != SPHERE && z.kind != ECLIPSE && z.kind != CLONE; // these end themselves
			if ((timed && now >= z.until) || now > z.until + 2 || owner == null || owner.level() != level || !owner.isAlive()) {
				ZONES.remove(z);
				continue;
			}
			switch (z.kind) {
				case POOL -> tickPool(level, owner, z, now);
				case SPHERE -> tickSphere(level, owner, z, now);
				case ECLIPSE -> tickEclipse(level, owner, z, now);
				case FEAR -> tickFear(level, z, now);
				case ERASE -> tickErase(level, owner, z, now);
				case CLONE -> tickClone(level, owner, z, now);
				default -> ZONES.remove(z);
			}
		}
	}
}
