package com.projecthero.mod.nova;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.nova.data.NovaState;
import com.projecthero.mod.nova.network.NovaScanPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Nova's twelve moves (v0.15.13). Each one is gated by {@link #begin} (suited, off cooldown, enough Nova Force -- free
 * during the Overload), runs as a small per-player session ticked from {@link #tick}, and deals its damage through
 * {@link NovaCombat} (so squadmates, their pets and PvP-off players are always spared).
 *
 * <pre>
 *   R (1)  Nova Blast (hold)       Shift+R  Nova Bolt Volley
 *   G (2)  Gravimetric Pulse       Shift+G  Gravity Slam
 *   Z (4)  Force Shield            Shift+Z  NOVA OVERLOAD (ultimate)
 *   X (3)  Comet Dash              Shift+X  Orbital Launch
 *   C (6)  Gravity Well            Shift+C  Gravity Lock
 *   V (5)  Worldmind Scan          Shift+V  Nova Force Transfer
 * </pre>
 */
public final class NovaAbilities {
	public static final String BLAST = "nova_blast";
	public static final String VOLLEY = "bolt_volley";
	public static final String PULSE = "gravimetric_pulse";
	public static final String SLAM = "gravity_slam";
	public static final String SHIELD = "force_shield";
	public static final String OVERLOAD = "nova_overload";
	public static final String DASH = "comet_dash";
	public static final String LAUNCH = "orbital_launch";
	public static final String WELL = "gravity_well";
	public static final String LOCK = "gravity_lock";
	public static final String SCAN = "worldmind_scan";
	public static final String TRANSFER = "force_transfer";

	private static final class Bolt {
		Vec3 pos;
		Vec3 vel;
		int age;
		int targetId;

		Bolt(Vec3 pos, Vec3 vel, int targetId) {
			this.pos = pos;
			this.vel = vel;
			this.targetId = targetId;
		}
	}

	private static final class Slam {
		final long start;
		final double startY;
		final double groundY;

		Slam(long start, double startY, double groundY) {
			this.start = start;
			this.startY = startY;
			this.groundY = groundY;
		}
	}

	private static final class Dash {
		final Vec3 dir;
		final long start;
		final boolean wasFlying;
		Vec3 last;
		double travelled;
		final Set<Integer> hit = new HashSet<>();

		Dash(Vec3 dir, long start, boolean wasFlying, Vec3 last) {
			this.dir = dir;
			this.start = start;
			this.wasFlying = wasFlying;
			this.last = last;
		}
	}

	private static final class Launch {
		final int targetId;
		final long start;
		final double startY;
		boolean spiking;
		long spikeStart;
		double groundY;

		Launch(int targetId, long start, double startY) {
			this.targetId = targetId;
			this.start = start;
			this.startY = startY;
		}
	}

	private static final class Lock {
		final long start;
		final Map<Integer, Vec3[]> held = new HashMap<>(); // id -> {from, to}

		Lock(long start) {
			this.start = start;
		}
	}

	private static final Map<UUID, Long> BLASTS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Bolt>> BOLTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Slam> SLAMS = new ConcurrentHashMap<>();
	private static final Map<UUID, Dash> DASHES = new ConcurrentHashMap<>();
	private static final Map<UUID, Launch> LAUNCHES = new ConcurrentHashMap<>();
	private static final Map<UUID, Lock> LOCKS = new ConcurrentHashMap<>();
	/** Players whose Overload is running (the burst fires when it ends). */
	private static final Set<UUID> OVERLOADING = ConcurrentHashMap.newKeySet();
	/** Suit-up wrap effect start times. */
	private static final Map<UUID, Long> SUIT_FX = new ConcurrentHashMap<>();
	/** Worldmind marks: entity id -> game time the +25% ends. */
	private static final Map<Integer, Long> MARKS = new ConcurrentHashMap<>();

	private NovaAbilities() {
	}

	static void clearSessionState() {
		BLASTS.clear();
		BOLTS.clear();
		SLAMS.clear();
		DASHES.clear();
		LAUNCHES.clear();
		LOCKS.clear();
		OVERLOADING.clear();
		SUIT_FX.clear();
		MARKS.clear();
	}

	/** Ends every running move of this player (suit-down, death, logout, revoke). */
	public static void clear(ServerPlayer p) {
		UUID id = p.getUUID();
		BLASTS.remove(id);
		BOLTS.remove(id);
		SLAMS.remove(id);
		DASHES.remove(id);
		Launch l = LAUNCHES.remove(id);
		if (l != null) {
			Entity t = p.level().getEntity(l.targetId);
			if (t instanceof LivingEntity le) {
				le.setNoGravity(false);
			}
		}
		Lock lock = LOCKS.remove(id);
		if (lock != null) {
			releaseLock(p, lock);
		}
		OVERLOADING.remove(id);
		SUIT_FX.remove(id);
	}

	/** Is this move's session running for {@code p}? */
	public static boolean running(ServerPlayer p, String id) {
		UUID u = p.getUUID();
		return switch (id) {
			case BLAST -> BLASTS.containsKey(u);
			case VOLLEY -> BOLTS.containsKey(u) && !BOLTS.get(u).isEmpty();
			case SLAM -> SLAMS.containsKey(u);
			case DASH -> DASHES.containsKey(u);
			case LAUNCH -> LAUNCHES.containsKey(u);
			case LOCK -> LOCKS.containsKey(u);
			case OVERLOAD -> OVERLOADING.contains(u);
			default -> false;
		};
	}

	/** The entity Orbital Launch is carrying, or null. */
	public static LivingEntity launchVictim(ServerPlayer p) {
		Launch l = LAUNCHES.get(p.getUUID());
		return l != null && p.level().getEntity(l.targetId) instanceof LivingEntity le ? le : null;
	}

	/** Is {@code e} held in a Gravity Lock right now? */
	public static boolean isLocked(Entity e) {
		for (Lock l : LOCKS.values()) {
			if (l.held.containsKey(e.getId())) {
				return true;
			}
		}
		return false;
	}

	/** Worldmind mark: the damage multiplier on {@code e} (1.25 while marked). */
	public static float markMultiplier(Entity e) {
		Long until = MARKS.get(e.getId());
		if (until == null) {
			return 1.0f;
		}
		if (until < e.level().getGameTime()) {
			MARKS.remove(e.getId());
			return 1.0f;
		}
		return NovaConfig.SCAN_MARK_MULTIPLIER;
	}

	public static boolean isMarked(Entity e) {
		return markMultiplier(e) > 1.0f;
	}

	// ---------------------------------------------------------------- the gate

	/** Suited, off cooldown, enough Nova Force (free while overloaded) -- then spends it. */
	static boolean begin(ServerPlayer p, String id, float cost) {
		if (!Nova.canAct(p)) {
			return false;
		}
		int cd = Nova.cooldownRemaining(p, id);
		if (cd > 0) {
			Nova.say(p, "message.projecthero.nova.cooldown", ChatFormatting.GRAY,
					Component.translatable("projecthero.nova.ability." + id), String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		if (!Nova.spendForce(p, cost)) {
			Nova.say(p, "message.projecthero.nova.low_force", ChatFormatting.YELLOW, (int) Math.ceil(cost), (int) Math.floor(Nova.force(p)));
			return false;
		}
		return true;
	}

	/** Starts a cooldown (halved if started during the Overload). */
	static void cooldown(ServerPlayer p, String id, int ticks) {
		int t = Nova.overloaded(p) ? Math.round(ticks * NovaConfig.OVERLOAD_COOLDOWN_MULTIPLIER) : ticks;
		NovaState n = Nova.state(p).copy();
		n.abilityReadyAt.put(id, p.level().getGameTime() + t);
		Nova.save(p, n);
	}

	private static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	/** Where the moves leave from: in front of the right hand, roughly. */
	static Vec3 hand(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		return eye.add(look.scale(0.6)).add(right.scale(0.35)).add(0, -0.3, 0);
	}

	// ================================================================ R: Nova Blast (held)

	public static void startBlast(ServerPlayer p) {
		if (BLASTS.containsKey(p.getUUID())) {
			return;
		}
		if (!Nova.canAct(p)) {
			return;
		}
		if (!Nova.overloaded(p) && Nova.force(p) + 1.0e-3f < NovaConfig.BLAST_COST_PER_SECOND * 0.5f) {
			Nova.say(p, "message.projecthero.nova.low_force", ChatFormatting.YELLOW, (int) Math.ceil(NovaConfig.BLAST_COST_PER_SECOND * 0.5f),
					(int) Math.floor(Nova.force(p)));
			return;
		}
		if (!begin(p, BLAST, 0f)) {
			return;
		}
		BLASTS.put(p.getUUID(), p.level().getGameTime());
		setBlastFlag(p, true);
		level(p).playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.9f, 1.8f);
	}

	public static void stopBlast(ServerPlayer p) {
		if (BLASTS.remove(p.getUUID()) == null) {
			return;
		}
		setBlastFlag(p, false);
		cooldown(p, BLAST, NovaConfig.BLAST_COOLDOWN);
	}

	private static void setBlastFlag(ServerPlayer p, boolean on) {
		NovaState s = Nova.state(p);
		if (s.blasting != on) {
			NovaState n = s.copy();
			n.blasting = on;
			Nova.save(p, n);
		}
	}

	/** The beam's end point this tick: the first block or creature on the look ray. */
	public static Vec3 blastEnd(ServerPlayer p, LivingEntity[] hitOut) {
		Vec3 eye = p.getEyePosition();
		Vec3 dir = p.getLookAngle();
		Vec3 far = eye.add(dir.scale(NovaConfig.BLAST_RANGE));
		BlockHitResult bhr = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e instanceof LivingEntity le && NovaCombat.isTarget(p, le) && e.isPickable(), eye.distanceToSqr(end));
		if (ehr != null && ehr.getEntity() instanceof LivingEntity le) {
			if (hitOut != null) {
				hitOut[0] = le;
			}
			return ehr.getLocation();
		}
		return end;
	}

	private static void tickBlast(ServerPlayer p, long start, long now) {
		long age = now - start;
		if (age >= NovaConfig.BLAST_MAX_TICKS || !Nova.suited(p) || !p.isAlive()) {
			stopBlast(p);
			return;
		}
		// 6 a second, paid 3 at a time every half-second (from the first half-second on)
		if (age % 10 == 5 && !Nova.spendForce(p, NovaConfig.BLAST_COST_PER_SECOND * 0.5f)) {
			Nova.say(p, "message.projecthero.nova.blast_empty", ChatFormatting.YELLOW);
			stopBlast(p);
			return;
		}
		ServerLevel level = level(p);
		LivingEntity[] hit = new LivingEntity[1];
		Vec3 end = blastEnd(p, hit);
		if (age > 0 && age % NovaConfig.BLAST_HIT_INTERVAL == 0 && hit[0] != null) {
			float perHit = NovaConfig.BLAST_DAMAGE_PER_SECOND * NovaConfig.BLAST_HIT_INTERVAL / 20f;
			NovaCombat.strike(p, hit[0], p.getEyePosition(), perHit, 0.15, 0.0);
		}
		if (age % 2 == 0) {
			level.sendParticles(Nova.GOLD_BIG, end.x, end.y, end.z, 3, 0.15, 0.15, 0.15, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 1, 0.1, 0.1, 0.1, 0.05);
		}
		if (age % 10 == 0) {
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0f, 2.0f);
		}
	}

	// ================================================================ Shift+R: Nova Bolt Volley

	public static void volley(ServerPlayer p) {
		if (!begin(p, VOLLEY, NovaConfig.VOLLEY_COST)) {
			return;
		}
		cooldown(p, VOLLEY, NovaConfig.VOLLEY_COOLDOWN);
		Nova.setAnim(p, NovaState.ANIM_VOLLEY);
		ServerLevel level = level(p);
		Vec3 from = hand(p);
		Vec3 look = p.getLookAngle();
		// the creatures nearest the crosshair get a bolt each (the rest double up)
		List<LivingEntity> prey = new ArrayList<>(NovaCombat.within(p, p.getEyePosition(), NovaConfig.VOLLEY_SEEK_RANGE,
				e -> NovaCombat.isHostile(p, e)));
		prey.sort(Comparator.comparingDouble(e -> -e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(p.getEyePosition()).normalize().dot(look)));
		List<Bolt> bolts = BOLTS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
		for (int i = 0; i < NovaConfig.VOLLEY_BOLTS; i++) {
			double spread = (i - (NovaConfig.VOLLEY_BOLTS - 1) * 0.5) * 0.22;
			Vec3 right = look.cross(new Vec3(0, 1, 0));
			right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
			Vec3 dir = look.add(right.scale(spread)).add(0, 0.12 + Math.abs(spread) * 0.2, 0).normalize();
			int target = prey.isEmpty() ? -1 : prey.get(i % prey.size()).getId();
			bolts.add(new Bolt(from, dir.scale(NovaConfig.VOLLEY_SPEED), target));
		}
		level.sendParticles(Nova.GOLD_BIG, from.x, from.y, from.z, 10, 0.15, 0.15, 0.15, 0.02);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0f, 1.6f);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 1.4f);
	}

	private static void tickBolts(ServerPlayer p, List<Bolt> bolts) {
		ServerLevel level = level(p);
		bolts.removeIf(b -> {
			b.age++;
			if (b.age > NovaConfig.VOLLEY_LIFETIME) {
				level.sendParticles(Nova.GOLD, b.pos.x, b.pos.y, b.pos.z, 4, 0.1, 0.1, 0.1, 0.0);
				return true;
			}
			// home in
			Entity t = b.targetId >= 0 ? level.getEntity(b.targetId) : null;
			if (!(t instanceof LivingEntity lt) || !lt.isAlive()) {
				LivingEntity next = null;
				double best = Double.MAX_VALUE;
				for (LivingEntity e : NovaCombat.within(p, b.pos, NovaConfig.VOLLEY_SEEK_RANGE * 0.6, e -> NovaCombat.isHostile(p, e))) {
					double d = e.distanceToSqr(b.pos);
					if (d < best) {
						best = d;
						next = e;
					}
				}
				b.targetId = next == null ? -1 : next.getId();
				t = next;
			}
			if (t instanceof LivingEntity lt && b.age > 2) {
				Vec3 want = lt.position().add(0, lt.getBbHeight() * 0.55, 0).subtract(b.pos).normalize();
				Vec3 cur = b.vel.normalize();
				b.vel = cur.add(want.subtract(cur).scale(NovaConfig.VOLLEY_TURN)).normalize().scale(NovaConfig.VOLLEY_SPEED);
			}
			if (t instanceof LivingEntity close && close.isAlive() && b.age > 1
					&& close.getBoundingBox().inflate(0.5).contains(b.pos.add(b.vel.scale(0.5)))) {
				// close enough: a homing bolt never orbits its prey
				NovaCombat.strike(p, close, b.pos, NovaConfig.VOLLEY_DAMAGE, 0.4, 0.1);
				level.sendParticles(Nova.GOLD_BIG, b.pos.x, b.pos.y, b.pos.z, 8, 0.2, 0.2, 0.2, 0.0);
				level.sendParticles(ParticleTypes.FLASH, b.pos.x, b.pos.y, b.pos.z, 1, 0.0, 0.0, 0.0, 0.0);
				return true;
			}
			Vec3 next = b.pos.add(b.vel);
			// blocks
			BlockHitResult bhr = level.clip(new ClipContext(b.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			Vec3 stop = bhr.getType() == HitResult.Type.MISS ? next : bhr.getLocation();
			// creatures
			EntityHitResult ehr = ProjectileUtil.getEntityHitResult(level, p, b.pos, stop, new AABB(b.pos, stop).inflate(0.6),
					e -> e instanceof LivingEntity le && NovaCombat.isTarget(p, le), 0.3f);
			if (ehr != null && ehr.getEntity() instanceof LivingEntity victim) {
				NovaCombat.strike(p, victim, b.pos, NovaConfig.VOLLEY_DAMAGE, 0.4, 0.1);
				Vec3 at = ehr.getLocation();
				level.sendParticles(Nova.GOLD_BIG, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.0);
				level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.8f, 1.5f);
				return true;
			}
			if (bhr.getType() != HitResult.Type.MISS) {
				level.sendParticles(Nova.GOLD_BIG, stop.x, stop.y, stop.z, 6, 0.15, 0.15, 0.15, 0.0);
				return true;
			}
			// the trail
			for (int i = 0; i < 3; i++) {
				Vec3 q = b.pos.lerp(next, i / 3.0);
				level.sendParticles(Nova.GOLD, q.x, q.y, q.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
			level.sendParticles(ParticleTypes.END_ROD, next.x, next.y, next.z, 1, 0.0, 0.0, 0.0, 0.0);
			if (b.age % 3 == 0) {
				level.sendParticles(Nova.CYAN, next.x, next.y, next.z, 1, 0.02, 0.02, 0.02, 0.0);
			}
			b.pos = next;
			return false;
		});
	}

	// ================================================================ G: Gravimetric Pulse

	public static void pulse(ServerPlayer p) {
		if (!begin(p, PULSE, NovaConfig.PULSE_COST)) {
			return;
		}
		cooldown(p, PULSE, NovaConfig.PULSE_COOLDOWN);
		Nova.setAnim(p, NovaState.ANIM_PULSE);
		ServerLevel level = level(p);
		Vec3 c = p.position().add(0, 0.6, 0);
		NovaCombat.radial(p, c, NovaConfig.PULSE_RADIUS, NovaConfig.PULSE_DAMAGE, NovaConfig.PULSE_KNOCKBACK, NovaConfig.PULSE_LIFT, null);
		NovaCombat.shockwave(level, p.position().add(0, 0.15, 0), NovaConfig.PULSE_RADIUS);
		level.sendParticles(Nova.GOLD_BIG, c.x, c.y + 0.4, c.z, 40, 0.8, 0.6, 0.8, 0.0);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 0.5, c.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7f, 1.6f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2f, 0.6f);
		NovaCombat.shake(level, c, 0.3f, 8, 14.0);
	}

	// ================================================================ Shift+G: Gravity Slam

	/** Ground below {@code p} within 64 blocks (its top surface Y), or NaN if none. */
	static double groundBelow(ServerPlayer p) {
		Vec3 from = p.position().add(0, 0.05, 0);
		BlockHitResult bhr = p.level().clip(new ClipContext(from, from.subtract(0, 64, 0), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.ANY, p));
		return bhr.getType() == HitResult.Type.MISS ? Double.NaN : bhr.getLocation().y;
	}

	public static void gravitySlam(ServerPlayer p) {
		if (SLAMS.containsKey(p.getUUID()) || !Nova.canAct(p)) {
			return;
		}
		double ground = groundBelow(p);
		if (p.onGround() || (!Double.isNaN(ground) && p.getY() - ground < NovaConfig.SLAM_MIN_HEIGHT)) {
			Nova.say(p, "message.projecthero.nova.slam_needs_air", ChatFormatting.GRAY);
			return;
		}
		if (!begin(p, SLAM, NovaConfig.SLAM_COST)) {
			return;
		}
		cooldown(p, SLAM, NovaConfig.SLAM_COOLDOWN);
		NovaFlight.stop(p, false);
		Nova.setAnim(p, NovaState.ANIM_SLAM);
		NovaState n = Nova.state(p).copy();
		n.slamming = true;
		Nova.save(p, n);
		SLAMS.put(p.getUUID(), new Slam(p.level().getGameTime(), p.getY(), Double.isNaN(ground) ? p.getY() - 64 : ground));
		AbilityHelpers.launchSelf(p, new Vec3(0, -NovaConfig.SLAM_DIVE_SPEED, 0));
		level(p).playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.0f, 0.6f);
	}

	/** Damage of a Gravity Slam from {@code drop} blocks up. */
	public static float slamDamage(double drop) {
		return (float) Math.max(NovaConfig.SLAM_MIN_DAMAGE, Math.min(NovaConfig.SLAM_MAX_DAMAGE,
				NovaConfig.SLAM_MIN_DAMAGE + NovaConfig.SLAM_DAMAGE_PER_BLOCK * drop));
	}

	private static void tickSlam(ServerPlayer p, Slam s, long now) {
		long age = now - s.start;
		ServerLevel level = level(p);
		p.resetFallDistance();
		boolean landed = (p.onGround() && age >= 1) || p.getY() <= s.groundY + 0.25 || p.isInWater();
		if (landed || age > NovaConfig.SLAM_MAX_TICKS) {
			SLAMS.remove(p.getUUID());
			NovaState n = Nova.state(p).copy();
			n.slamming = false;
			n.animId = NovaState.ANIM_PULSE; // the landing crouch
			n.animStart = now;
			Nova.save(p, n);
			double drop = Math.max(0.0, s.startY - p.getY());
			Vec3 c = p.position();
			NovaCombat.radial(p, c.add(0, 0.5, 0), NovaConfig.SLAM_RADIUS, slamDamage(drop), 0.9, 0.6, null);
			// the crater is only light and dust -- no blocks are broken
			NovaCombat.shockwave(level, c.add(0, 0.15, 0), NovaConfig.SLAM_RADIUS);
			BlockState under = level.getBlockState(BlockPos.containing(c.x, c.y - 0.5, c.z));
			if (!under.isAir()) {
				for (int i = 0; i < 24; i++) {
					double a = Math.PI * 2 * i / 24;
					double r = 1.5 + (i % 4) * 1.6;
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), c.x + Math.cos(a) * r, c.y + 0.2,
							c.z + Math.sin(a) * r, 4, 0.2, 0.3, 0.2, 0.15);
				}
			}
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.3, c.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(Nova.GOLD_BIG, c.x, c.y + 0.5, c.z, 60, 2.5, 0.4, 2.5, 0.0);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4f, 0.7f);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 0.5f);
			NovaCombat.shake(level, c, 0.6f, 12, 20.0);
			return;
		}
		AbilityHelpers.launchSelf(p, new Vec3(0, -NovaConfig.SLAM_DIVE_SPEED, 0));
		level.sendParticles(Nova.GOLD_BIG, p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.3, 0.6, 0.3, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 2.0, p.getZ(), 2, 0.2, 0.4, 0.2, 0.0);
	}

	// ================================================================ Z: Force Shield

	public static void shield(ServerPlayer p) {
		if (!begin(p, SHIELD, NovaConfig.SHIELD_COST)) {
			return;
		}
		cooldown(p, SHIELD, NovaConfig.SHIELD_COOLDOWN);
		NovaState n = Nova.state(p).copy();
		n.shieldUntil = p.level().getGameTime() + NovaConfig.SHIELD_TICKS;
		Nova.save(p, n);
		ServerLevel level = level(p);
		level.sendParticles(Nova.GOLD_BIG, p.getX(), p.getY() + 1.0, p.getZ(), 40, 1.0, 1.0, 1.0, 0.0);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.8f);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 1.2f);
	}

	/** While the shield is up: every projectile that comes within the bubble is absorbed. */
	private static void tickShield(ServerPlayer p) {
		ServerLevel level = level(p);
		Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
		double r = NovaConfig.SHIELD_RADIUS + 0.6;
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(r))) {
			if (proj.getOwner() == p || proj.position().distanceToSqr(c) > r * r) {
				continue;
			}
			Vec3 at = proj.position();
			level.sendParticles(Nova.GOLD_BIG, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.0);
			level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.6f);
			proj.discard();
		}
		if (p.tickCount % 4 == 0) {
			for (int i = 0; i < 6; i++) {
				double a = p.getRandom().nextDouble() * Math.PI * 2;
				double b = Math.acos(2 * p.getRandom().nextDouble() - 1);
				double rr = NovaConfig.SHIELD_RADIUS;
				level.sendParticles(Nova.GOLD, c.x + rr * Math.sin(b) * Math.cos(a), c.y + rr * Math.cos(b), c.z + rr * Math.sin(b) * Math.sin(a),
						1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}

	// ================================================================ Shift+Z: NOVA OVERLOAD (ultimate)

	public static void overload(ServerPlayer p) {
		if (OVERLOADING.contains(p.getUUID()) || !Nova.canAct(p)) {
			return;
		}
		int cd = Nova.cooldownRemaining(p, OVERLOAD);
		if (cd > 0) {
			Nova.say(p, "message.projecthero.nova.cooldown", ChatFormatting.GRAY, Component.translatable("projecthero.nova.ability." + OVERLOAD),
					String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return;
		}
		if (Nova.force(p) + 1.0e-3f < NovaConfig.OVERLOAD_MIN_FORCE) {
			Nova.say(p, "message.projecthero.nova.overload_needs_full", ChatFormatting.YELLOW, (int) Math.floor(Nova.force(p)));
			return;
		}
		long now = p.level().getGameTime();
		NovaState n = Nova.state(p).copy();
		n.force = 0f; // it takes all of it
		n.abilityReadyAt.put(OVERLOAD, now + NovaConfig.OVERLOAD_COOLDOWN);
		n.overloadUntil = now + NovaConfig.OVERLOAD_TICKS;
		n.animId = NovaState.ANIM_OVERLOAD;
		n.animStart = now;
		Nova.save(p, n);
		OVERLOADING.add(p.getUUID());
		ServerLevel level = level(p);
		Vec3 c = p.position().add(0, 1.0, 0);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0.2, 0.2, 0.2, 0.0);
		level.sendParticles(Nova.GOLD_BIG, c.x, c.y, c.z, 50, 1.2, 1.4, 1.2, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 60, 0.4, 0.8, 0.4, 0.3);
		NovaCombat.shockwave(level, p.position().add(0, 0.1, 0), 5.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0f, 0.6f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
		NovaCombat.shake(level, c, 0.4f, 16, 24.0);
		p.displayClientMessage(Component.translatable("message.projecthero.nova.overload").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
	}

	private static void tickOverload(ServerPlayer p, long now) {
		NovaState s = Nova.state(p);
		ServerLevel level = level(p);
		if (s.overloadUntil > now && s.suited) {
			if (p.tickCount % 3 == 0) {
				level.sendParticles(Nova.GOLD, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.45, 0.8, 0.45, 0.0);
				level.sendParticles(Nova.CYAN, p.getX(), p.getY() + 1.2, p.getZ(), 1, 0.4, 0.6, 0.4, 0.0);
			}
			return;
		}
		OVERLOADING.remove(p.getUUID());
		if (!s.suited) {
			return; // taken off mid-Overload: no burst
		}
		overloadBurst(p);
	}

	/** The end of the Overload: a 30-damage nova burst all around. */
	static void overloadBurst(ServerPlayer p) {
		ServerLevel level = level(p);
		Vec3 c = p.position().add(0, 1.0, 0);
		NovaState n = Nova.state(p).copy();
		n.overloadUntil = 0L;
		n.animId = NovaState.ANIM_BURST;
		n.animStart = p.level().getGameTime();
		Nova.save(p, n);
		NovaCombat.radial(p, c, NovaConfig.OVERLOAD_BURST_RADIUS, NovaConfig.OVERLOAD_BURST_DAMAGE, 1.6, 0.7, null);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 3, 0.5, 0.5, 0.5, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);
		for (int i = 0; i < 160; i++) {
			double a = p.getRandom().nextDouble() * Math.PI * 2;
			double b = Math.acos(2 * p.getRandom().nextDouble() - 1);
			Vec3 d = new Vec3(Math.sin(b) * Math.cos(a), Math.cos(b), Math.sin(b) * Math.sin(a));
			level.sendParticles(i % 4 == 0 ? Nova.CYAN : Nova.GOLD_BIG, c.x + d.x, c.y + d.y, c.z + d.z, 0, d.x, d.y, d.z, 0.9);
			if (i % 3 == 0) {
				level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 0, d.x, d.y, d.z, 0.7);
			}
		}
		NovaCombat.shockwave(level, p.position().add(0, 0.1, 0), NovaConfig.OVERLOAD_BURST_RADIUS);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0f, 0.6f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5f, 0.8f);
		NovaCombat.shake(level, c, 0.9f, 20, 32.0);
	}

	// ================================================================ X: Comet Dash

	public static void cometDash(ServerPlayer p) {
		if (DASHES.containsKey(p.getUUID()) || !begin(p, DASH, NovaConfig.DASH_COST)) {
			return;
		}
		cooldown(p, DASH, NovaConfig.DASH_COOLDOWN);
		Nova.setAnim(p, NovaState.ANIM_DASH);
		boolean flying = Nova.isFlying(p);
		Vec3 dir = p.getLookAngle();
		if (!flying) {
			dir = new Vec3(dir.x, Math.max(0.05, Math.min(dir.y, 0.35)), dir.z); // on foot it stays low
		}
		dir = dir.normalize();
		if (flying) {
			NovaFlight.stop(p, false);
		}
		Dash d = new Dash(dir, p.level().getGameTime(), flying, p.position());
		DASHES.put(p.getUUID(), d);
		dashHits(p, d, p.position(), p.position().add(dir.scale(1.5)));
		AbilityHelpers.launchSelf(p, dir.scale(NovaConfig.DASH_SPEED));
		ServerLevel level = level(p);
		level.sendParticles(Nova.GOLD_BIG, p.getX(), p.getY() + 0.9, p.getZ(), 20, 0.3, 0.5, 0.3, 0.0);
		level.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 0.9, p.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.2f, 0.9f);
	}

	/** Rams everything within {@link NovaConfig#DASH_HIT_RADIUS} of the segment {@code a -> b} (each creature once). */
	private static void dashHits(ServerPlayer p, Dash d, Vec3 a, Vec3 b) {
		double len = a.distanceTo(b);
		int steps = Math.max(1, (int) Math.ceil(len / 0.5));
		AABB box = new AABB(a, b).inflate(NovaConfig.DASH_HIT_RADIUS + 1.0, NovaConfig.DASH_HIT_RADIUS + 2.0, NovaConfig.DASH_HIT_RADIUS + 1.0);
		for (LivingEntity e : NovaCombat.targets(p, box)) {
			if (d.hit.contains(e.getId())) {
				continue;
			}
			AABB eb = e.getBoundingBox().inflate(NovaConfig.DASH_HIT_RADIUS);
			for (int i = 0; i <= steps; i++) {
				Vec3 q = a.lerp(b, i / (double) steps).add(0, p.getBbHeight() * 0.5, 0);
				if (eb.contains(q)) {
					d.hit.add(e.getId());
					NovaCombat.strike(p, e, q.subtract(d.dir), NovaConfig.DASH_DAMAGE, NovaConfig.DASH_KNOCKBACK, 0.4);
					break;
				}
			}
		}
	}

	private static void tickDash(ServerPlayer p, Dash d, long now) {
		long age = now - d.start;
		Vec3 pos = p.position();
		double step = pos.distanceTo(d.last);
		dashHits(p, d, d.last, pos);
		d.travelled += step;
		d.last = pos;
		ServerLevel level = level(p);
		Vec3 mid = pos.add(0, p.getBbHeight() * 0.5, 0);
		level.sendParticles(Nova.GOLD_BIG, mid.x, mid.y, mid.z, 4, 0.25, 0.35, 0.25, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, mid.x, mid.y, mid.z, 2, 0.15, 0.25, 0.15, 0.0);
		level.sendParticles(Nova.CYAN, mid.x, mid.y, mid.z, 1, 0.1, 0.2, 0.1, 0.0);
		boolean blocked = age >= 2 && step < 0.3;
		if (d.travelled >= NovaConfig.DASH_DISTANCE || blocked || age > NovaConfig.DASH_MAX_TICKS) {
			DASHES.remove(p.getUUID());
			AbilityHelpers.launchSelf(p, d.dir.scale(0.3));
			if (d.wasFlying && Nova.suited(p) && !p.onGround()) {
				NovaFlight.start(p);
			}
			return;
		}
		if (age >= 1) {
			AbilityHelpers.launchSelf(p, d.dir.scale(NovaConfig.DASH_SPEED));
		}
	}

	// ================================================================ Shift+X: Orbital Launch

	/** The nearest creature Orbital Launch would grab (hostile, not a player, not a boss), or null. */
	public static LivingEntity launchTarget(ServerPlayer p) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : NovaCombat.within(p, p.position().add(0, 1, 0), NovaConfig.LAUNCH_GRAB_RANGE,
				e -> NovaCombat.isHostile(p, e) && NovaCombat.isMovableMob(p, e) && !e.isPassenger())) {
			double d = e.distanceToSqr(p);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	public static void orbitalLaunch(ServerPlayer p) {
		if (LAUNCHES.containsKey(p.getUUID()) || !Nova.canAct(p)) {
			return;
		}
		LivingEntity target = launchTarget(p);
		if (target == null) {
			Nova.say(p, "message.projecthero.nova.launch_no_target", ChatFormatting.GRAY);
			return;
		}
		if (!begin(p, LAUNCH, NovaConfig.LAUNCH_COST)) {
			return;
		}
		cooldown(p, LAUNCH, NovaConfig.LAUNCH_COOLDOWN);
		NovaFlight.stop(p, false);
		Nova.setAnim(p, NovaState.ANIM_LAUNCH);
		long now = p.level().getGameTime();
		LAUNCHES.put(p.getUUID(), new Launch(target.getId(), now, target.getY()));
		target.setNoGravity(true);
		ServerLevel level = level(p);
		level.sendParticles(Nova.GOLD_BIG, target.getX(), target.getY() + 0.5, target.getZ(), 30, 0.5, 0.2, 0.5, 0.0);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 2.0f, 0.5f);
		AbilityHelpers.launchSelf(p, new Vec3(0, NovaConfig.LAUNCH_CLIMB_SPEED, 0));
	}

	private static void tickLaunch(ServerPlayer p, Launch l, long now) {
		ServerLevel level = level(p);
		Entity e = level.getEntity(l.targetId);
		if (!(e instanceof LivingEntity t) || !t.isAlive()) {
			LAUNCHES.remove(p.getUUID());
			if (e instanceof LivingEntity dead) {
				dead.setNoGravity(false);
			}
			return;
		}
		long age = now - l.start;
		if (!l.spiking) {
			// the climb: the victim is carried straight up (the Nova flies with it)
			double y = Math.min(l.startY + NovaConfig.LAUNCH_HEIGHT, t.getY() + NovaConfig.LAUNCH_CLIMB_SPEED);
			t.setPos(t.getX(), y, t.getZ());
			t.setDeltaMovement(Vec3.ZERO);
			t.resetFallDistance();
			t.hurtMarked = true;
			if (p.getY() < y + 1.0 && age < 40) {
				AbilityHelpers.launchSelf(p, new Vec3(0, NovaConfig.LAUNCH_CLIMB_SPEED, 0));
			}
			level.sendParticles(Nova.GOLD_BIG, t.getX(), t.getY(), t.getZ(), 3, 0.3, 0.1, 0.3, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, t.getX(), t.getY() - 0.5, t.getZ(), 2, 0.2, 0.3, 0.2, 0.0);
			if (y >= l.startY + NovaConfig.LAUNCH_HEIGHT - 0.01 || age > 40) {
				// the spike
				l.spiking = true;
				l.spikeStart = now;
				Vec3 from = t.position();
				BlockHitResult bhr = level.clip(new ClipContext(from, from.subtract(0, 128, 0), ClipContext.Block.COLLIDER,
						ClipContext.Fluid.ANY, t));
				l.groundY = bhr.getType() == HitResult.Type.MISS ? from.y - 128 : bhr.getLocation().y;
				t.setNoGravity(false);
				t.setDeltaMovement(0, -NovaConfig.LAUNCH_SPIKE_SPEED, 0);
				t.hurtMarked = true;
				Nova.setAnim(p, NovaState.ANIM_SLAM);
				level.sendParticles(ParticleTypes.FLASH, t.getX(), t.getY(), t.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
				level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 2.0f, 0.6f);
				if (Nova.suited(p)) {
					NovaFlight.start(p); // he hovers up there and watches it fall
					AbilityHelpers.launchSelf(p, Vec3.ZERO);
				}
			}
			return;
		}
		// the spike down
		long spikeAge = now - l.spikeStart;
		boolean down = (t.onGround() && spikeAge >= 1) || t.getY() <= l.groundY + 0.3 || t.isInWater() || spikeAge > 80;
		if (!down) {
			if (t.getDeltaMovement().y > -NovaConfig.LAUNCH_SPIKE_SPEED) {
				t.setDeltaMovement(0, -NovaConfig.LAUNCH_SPIKE_SPEED, 0);
				t.hurtMarked = true;
			}
			level.sendParticles(Nova.GOLD_BIG, t.getX(), t.getY() + 1.0, t.getZ(), 3, 0.2, 0.5, 0.2, 0.0);
			return;
		}
		LAUNCHES.remove(p.getUUID());
		Vec3 c = t.position();
		NovaCombat.strike(p, t, c.add(0, 1, 0), NovaConfig.LAUNCH_SPIKE_DAMAGE, 0.0, 0.0);
		Set<Integer> skip = new HashSet<>();
		skip.add(t.getId());
		NovaCombat.radial(p, c, NovaConfig.LAUNCH_IMPACT_RADIUS, 6f, 0.8, 0.4, skip);
		NovaCombat.shockwave(level, c.add(0, 0.15, 0), NovaConfig.LAUNCH_IMPACT_RADIUS + 1.0);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5f, 0.8f);
		NovaCombat.shake(level, c, 0.5f, 10, 20.0);
	}

	// ================================================================ C: Gravity Well

	public static void gravityWell(ServerPlayer p) {
		if (!begin(p, WELL, NovaConfig.WELL_COST)) {
			return;
		}
		cooldown(p, WELL, NovaConfig.WELL_COOLDOWN);
		Nova.setAnim(p, NovaState.ANIM_WELL);
		Vec3 at = wellSpot(p);
		NovaState n = Nova.state(p).copy();
		n.wellPos = List.of(at.x, at.y, at.z);
		n.wellUntil = p.level().getGameTime() + NovaConfig.WELL_TICKS;
		Nova.save(p, n);
		ServerLevel level = level(p);
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.7f, 1.8f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.5f);
	}

	/** Where the singularity opens: the creature or block under the crosshair (up to 24 blocks), raised to chest height. */
	static Vec3 wellSpot(ServerPlayer p) {
		LivingEntity[] hit = new LivingEntity[1];
		Vec3 eye = p.getEyePosition();
		Vec3 dir = p.getLookAngle();
		Vec3 far = eye.add(dir.scale(NovaConfig.WELL_RANGE));
		BlockHitResult bhr = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e instanceof LivingEntity le && NovaCombat.isTarget(p, le), eye.distanceToSqr(end));
		if (ehr != null) {
			hit[0] = (LivingEntity) ehr.getEntity();
			return hit[0].position().add(0, 1.2, 0);
		}
		if (bhr.getType() == HitResult.Type.BLOCK) {
			return end.subtract(dir.scale(0.5)).add(0, 1.2, 0);
		}
		return end;
	}

	private static void tickWell(ServerPlayer p, NovaState s, long now) {
		if (s.wellPos.size() != 3) {
			return;
		}
		Vec3 c = new Vec3(s.wellPos.get(0), s.wellPos.get(1), s.wellPos.get(2));
		ServerLevel level = level(p);
		if (now >= s.wellUntil) {
			// the collapse
			NovaState n = s.copy();
			n.wellUntil = 0L;
			n.wellPos = List.of();
			Nova.save(p, n);
			NovaCombat.radial(p, c, NovaConfig.WELL_CRUSH_RADIUS, NovaConfig.WELL_CRUSH_DAMAGE, 0.0, 0.0, null);
			level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 3, 0.4, 0.4, 0.4, 0.0);
			level.sendParticles(Nova.GOLD_BIG, c.x, c.y, c.z, 50, 1.0, 1.0, 1.0, 0.0);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 80, 0.2, 0.2, 0.2, 0.6);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 0.5f);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8f, 0.5f);
			NovaCombat.shake(level, c, 0.4f, 10, 18.0);
			return;
		}
		// the pull
		for (LivingEntity e : NovaCombat.within(p, c, NovaConfig.WELL_PULL_RADIUS, e -> NovaCombat.isMovableMob(p, e))) {
			Vec3 to = c.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
			double dist = to.length();
			if (dist < 0.4) {
				e.setDeltaMovement(e.getDeltaMovement().scale(0.3));
			} else {
				Vec3 v = e.getDeltaMovement().scale(0.6).add(to.normalize().scale(Math.min(dist, 1.0) * NovaConfig.WELL_PULL_STRENGTH * 2.0));
				e.setDeltaMovement(v);
			}
			e.resetFallDistance();
			e.hurtMarked = true;
		}
		// the swirl
		long age = NovaConfig.WELL_TICKS - (s.wellUntil - now);
		for (int i = 0; i < 4; i++) {
			double a = age * 0.45 + i * Math.PI / 2;
			double r = 3.2 - (age % 20) * 0.14;
			level.sendParticles(Nova.GOLD, c.x + Math.cos(a) * r, c.y + Math.sin(age * 0.2 + i) * 0.3, c.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 4, 0.8, 0.8, 0.8, 0.02);
		if (age % 10 == 0) {
			level.playSound(null, c.x, c.y, c.z, SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 0.6f, 2.0f);
		}
	}

	// ================================================================ Shift+C: Gravity Lock

	public static void gravityLock(ServerPlayer p) {
		if (LOCKS.containsKey(p.getUUID()) || !begin(p, LOCK, NovaConfig.LOCK_COST)) {
			return;
		}
		cooldown(p, LOCK, NovaConfig.LOCK_COOLDOWN);
		Nova.setAnim(p, NovaState.ANIM_LOCK);
		long now = p.level().getGameTime();
		Lock lock = new Lock(now);
		for (LivingEntity e : NovaCombat.within(p, p.position().add(0, 1, 0), NovaConfig.LOCK_RADIUS, e -> NovaCombat.isMovableMob(p, e))) {
			if (isLocked(e)) {
				continue;
			}
			Vec3 from = e.position();
			lock.held.put(e.getId(), new Vec3[] { from, from.add(0, NovaConfig.LOCK_LIFT, 0) });
			e.setNoGravity(true);
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, NovaConfig.LOCK_TICKS + 5, 9, false, false, false));
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, NovaConfig.LOCK_TICKS + 5, 9, false, false, false));
		}
		LOCKS.put(p.getUUID(), lock);
		ServerLevel level = level(p);
		NovaCombat.shockwave(level, p.position().add(0, 0.1, 0), NovaConfig.LOCK_RADIUS);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5f, 0.5f);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 1.2f);
	}

	private static void tickLock(ServerPlayer p, Lock lock, long now) {
		long age = now - lock.start;
		ServerLevel level = level(p);
		if (age >= NovaConfig.LOCK_TICKS) {
			LOCKS.remove(p.getUUID());
			releaseLock(p, lock);
			return;
		}
		float rise = Math.min(1f, age / (float) NovaConfig.LOCK_RISE_TICKS);
		lock.held.entrySet().removeIf(en -> {
			Entity e = level.getEntity(en.getKey());
			if (!(e instanceof LivingEntity le) || !le.isAlive()) {
				return true;
			}
			Vec3 at = en.getValue()[0].lerp(en.getValue()[1], rise);
			le.setPos(at.x, at.y, at.z);
			le.setDeltaMovement(Vec3.ZERO);
			le.resetFallDistance();
			le.hurtMarked = true;
			if (age % 3 == 0) {
				level.sendParticles(Nova.CYAN, at.x, at.y + le.getBbHeight() * 0.5, at.z, 2, le.getBbWidth() * 0.6, le.getBbHeight() * 0.4,
						le.getBbWidth() * 0.6, 0.0);
				level.sendParticles(Nova.GOLD, at.x, at.y - 0.1, at.z, 2, 0.3, 0.0, 0.3, 0.0);
			}
			return false;
		});
	}

	private static void releaseLock(ServerPlayer p, Lock lock) {
		ServerLevel level = level(p);
		for (Integer id : lock.held.keySet()) {
			Entity e = level.getEntity(id);
			if (e instanceof LivingEntity le) {
				le.setNoGravity(false);
				le.setDeltaMovement(0, -0.6, 0);
				le.hurtMarked = true;
				le.removeEffect(MobEffects.WEAKNESS);
			}
		}
		lock.held.clear();
	}

	// ================================================================ V: Worldmind Scan

	public static void scan(ServerPlayer p) {
		if (!begin(p, SCAN, NovaConfig.SCAN_COST)) {
			return;
		}
		cooldown(p, SCAN, NovaConfig.SCAN_COOLDOWN);
		Nova.setAnim(p, NovaState.ANIM_SCAN);
		ServerLevel level = level(p);
		long now = level.getGameTime();
		List<LivingEntity> found = NovaCombat.within(p, p.getEyePosition(), NovaConfig.SCAN_RANGE,
				e -> e != p && e instanceof Mob && e.isAlive());
		LivingEntity strongest = null;
		for (boolean hostileOnly : new boolean[] { true, false }) {
			for (LivingEntity e : found) {
				if ((hostileOnly && !NovaCombat.isHostile(p, e)) || (!hostileOnly && !NovaCombat.isTarget(p, e))) {
					continue;
				}
				if (strongest == null || e.getMaxHealth() > strongest.getMaxHealth()
						|| (e.getMaxHealth() == strongest.getMaxHealth() && e.getHealth() > strongest.getHealth())) {
					strongest = e;
				}
			}
			if (strongest != null) {
				break;
			}
		}
		if (strongest != null) {
			MARKS.put(strongest.getId(), now + NovaConfig.SCAN_TICKS);
			Vec3 m = strongest.position().add(0, strongest.getBbHeight() + 0.4, 0);
			level.sendParticles(ParticleTypes.FLASH, m.x, m.y, m.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		List<Integer> ids = new ArrayList<>();
		for (LivingEntity e : found) {
			if (ids.size() < 512) {
				ids.add(e.getId());
			}
		}
		if (ServerPlayNetworking.canSend(p, NovaScanPayload.TYPE)) {
			ServerPlayNetworking.send(p, new NovaScanPayload(ids, strongest == null ? -1 : strongest.getId(), NovaConfig.SCAN_TICKS));
		}
		// the pulse itself, seen by everyone: a cyan ring rolling out
		Vec3 c = p.position().add(0, 1.0, 0);
		for (int r = 2; r <= 12; r += 2) {
			NovaCombat.ring(level, Nova.CYAN, c, r, 12 + r * 3);
		}
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2f, 2.0f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 1.6f);
		p.displayClientMessage(Component.translatable("message.projecthero.nova.scan", found.size(),
				strongest == null ? Component.translatable("message.projecthero.nova.scan_none") : strongest.getDisplayName())
				.withStyle(ChatFormatting.AQUA), true);
	}

	// ================================================================ Shift+V: Nova Force Transfer

	public static void forceTransfer(ServerPlayer p) {
		if (!begin(p, TRANSFER, NovaConfig.TRANSFER_COST)) {
			return;
		}
		cooldown(p, TRANSFER, NovaConfig.TRANSFER_COOLDOWN);
		Nova.setAnim(p, NovaState.ANIM_TRANSFER);
		ServerLevel level = level(p);
		List<Player> healed = new ArrayList<>();
		healed.add(p);
		for (Player other : level.players()) {
			if (other != p && other.isAlive() && !other.isSpectator() && other.distanceToSqr(p) <= NovaConfig.TRANSFER_RADIUS * NovaConfig.TRANSFER_RADIUS
					&& com.projecthero.mod.squad.Squads.areAllies(p, other)) {
				healed.add(other);
			}
		}
		Vec3 from = p.position().add(0, 1.2, 0);
		for (Player h : healed) {
			h.heal(NovaConfig.TRANSFER_HEAL);
			Vec3 to = h.position().add(0, 1.0, 0);
			if (h != p) {
				AbilityHelpers.line(level, from, to, Nova.GOLD, 3.0);
			}
			level.sendParticles(ParticleTypes.HEART, to.x, to.y + 0.8, to.z, 3, 0.3, 0.2, 0.3, 0.0);
			level.sendParticles(Nova.GOLD_BIG, to.x, to.y, to.z, 16, 0.35, 0.6, 0.35, 0.0);
			level.sendParticles(Nova.CYAN, to.x, to.y, to.z, 6, 0.3, 0.5, 0.3, 0.0);
		}
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2f, 1.6f);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.8f);
	}

	// ================================================================ the suit-up wrap

	static void beginSuitUpFx(ServerPlayer p) {
		SUIT_FX.put(p.getUUID(), p.level().getGameTime());
	}

	/** Golden energy spirals up the body while the uniform forms (the client draws the texel wrap itself). */
	private static void tickSuitFx(ServerPlayer p, long start, long now) {
		long age = now - start;
		if (age > NovaConfig.SUIT_UP_TICKS) {
			SUIT_FX.remove(p.getUUID());
			ServerLevel level = level(p);
			level.sendParticles(Nova.CYAN, p.getX(), p.getY() + 1.4, p.getZ(), 10, 0.25, 0.25, 0.25, 0.0);
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 2.0f);
			return;
		}
		ServerLevel level = level(p);
		double h = (age / (double) NovaConfig.SUIT_UP_TICKS) * 2.0;
		// a light double helix of gold rising with the wrap -- sparse, so the suit forming stays visible
		for (int i = 0; i < 2; i++) {
			double a = age * 0.9 + i * Math.PI;
			level.sendParticles(Nova.GOLD, p.getX() + Math.cos(a) * 0.7, p.getY() + h, p.getZ() + Math.sin(a) * 0.7, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (age % 6 == 0) {
			level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + h, p.getZ(), 2, 0.3, 0.05, 0.3, 0.02);
		}
	}

	// ================================================================ tick

	static void tick(ServerPlayer p) {
		long now = p.level().getGameTime();
		UUID id = p.getUUID();
		NovaState s = Nova.state(p);
		Long suitFx = SUIT_FX.get(id);
		if (suitFx != null) {
			tickSuitFx(p, suitFx, now);
		}
		if (!s.suited) {
			if (s.blasting || s.wellUntil != 0L) {
				clear(p);
			}
			return;
		}
		Long blast = BLASTS.get(id);
		if (blast != null) {
			tickBlast(p, blast, now);
		} else if (s.blasting) {
			setBlastFlag(p, false);
		}
		List<Bolt> bolts = BOLTS.get(id);
		if (bolts != null) {
			tickBolts(p, bolts);
			if (bolts.isEmpty()) {
				BOLTS.remove(id);
			}
		}
		Slam slam = SLAMS.get(id);
		if (slam != null) {
			tickSlam(p, slam, now);
		}
		if (Nova.state(p).shieldUntil > now) {
			tickShield(p);
		}
		if (OVERLOADING.contains(id)) {
			tickOverload(p, now);
		}
		Dash dash = DASHES.get(id);
		if (dash != null) {
			tickDash(p, dash, now);
		}
		Launch launch = LAUNCHES.get(id);
		if (launch != null) {
			tickLaunch(p, launch, now);
		}
		NovaState cur = Nova.state(p);
		if (cur.wellUntil != 0L) {
			tickWell(p, cur, now);
		}
		Lock lock = LOCKS.get(id);
		if (lock != null) {
			tickLock(p, lock, now);
		}
		if (now % 100 == 0) {
			MARKS.entrySet().removeIf(e -> e.getValue() < now);
		}
	}
}
