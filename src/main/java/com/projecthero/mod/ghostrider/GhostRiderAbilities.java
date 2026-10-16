package com.projecthero.mod.ghostrider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ghostrider.data.GhostRiderState;
import com.projecthero.mod.ghostrider.entity.GhostRiderEntityTypes;
import com.projecthero.mod.ghostrider.entity.HellCycleEntity;
import com.projecthero.mod.ghostrider.entity.HellfireBlastEntity;
import com.projecthero.mod.ghostrider.network.GhostRiderMarkPayload;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ghost Rider's twelve moves (v0.16.5). Each one is gated by {@link #begin} (transformed, off cooldown, enough Hellfire),
 * runs as a small per-player session ticked from {@link #tick}, and deals its damage through {@link GhostRiderCombat}.
 *
 * <pre>
 *   R (1)  Hellfire Blast (hold)   Shift+R  Hellfire Wave
 *   G (2)  Chain Whip              Shift+G  Chain Spin
 *   X (3)  Hell Cycle / riding: Cycle Jump      Shift+X  send the bike back
 *   Z (4)  Judgement Mark          Shift+Z  Penance Stare
 *   V (5)  Hellfire Rush           Shift+V  Hellfire Eruption
 *   C (6)  Soul Shield             Shift+C  Rider's Wrath
 * </pre>
 */
public final class GhostRiderAbilities {
	public static final String BLAST = "hellfire_blast";
	public static final String WAVE = "hellfire_wave";
	public static final String WHIP = "chain_whip";
	public static final String SPIN = "chain_spin";
	public static final String CYCLE = "hell_cycle";
	public static final String CYCLE_JUMP = "cycle_jump";
	public static final String MARK = "judgement_mark";
	public static final String STARE = "penance_stare";
	public static final String RUSH = "hellfire_rush";
	public static final String ERUPTION = "hellfire_eruption";
	public static final String SHIELD = "soul_shield";
	public static final String WRATH = "riders_wrath";

	private static final class Wave {
		final Vec3 origin;
		final Vec3 dir;
		final long start;
		final Set<Integer> hit = new HashSet<>();

		Wave(Vec3 origin, Vec3 dir, long start) {
			this.origin = origin;
			this.dir = dir;
			this.start = start;
		}
	}

	private static final class Rush {
		final Vec3 dir;
		final long start;
		Vec3 last;
		double travelled;
		final Set<Integer> hit = new HashSet<>();

		Rush(Vec3 dir, long start, Vec3 last) {
			this.dir = dir;
			this.start = start;
			this.last = last;
		}
	}

	private static final class Eruption {
		final Vec3 center;
		final long start;

		Eruption(Vec3 center, long start) {
			this.center = center;
			this.start = start;
		}
	}

	private static final class Stare {
		final int targetId;
		final long start;

		Stare(int targetId, long start) {
			this.targetId = targetId;
			this.start = start;
		}
	}

	/** Held Hellfire Blast: when it was pressed. */
	private static final Map<UUID, Long> BLASTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Wave> WAVES = new ConcurrentHashMap<>();
	private static final Map<UUID, Rush> RUSHES = new ConcurrentHashMap<>();
	private static final Map<UUID, Eruption> ERUPTIONS = new ConcurrentHashMap<>();
	private static final Map<UUID, Stare> STARES = new ConcurrentHashMap<>();
	/** Transformation effect start times. */
	private static final Map<UUID, Long> TRANSFORM_FX = new ConcurrentHashMap<>();
	/** The Hell Cycle each rider summoned (entity id). */
	private static final Map<UUID, Integer> CYCLES = new ConcurrentHashMap<>();

	private GhostRiderAbilities() {
	}

	static void clearSessionState() {
		BLASTS.clear();
		WAVES.clear();
		RUSHES.clear();
		ERUPTIONS.clear();
		STARES.clear();
		TRANSFORM_FX.clear();
		CYCLES.clear();
	}

	/** Ends every running move of this player (revert, death, logout, revoke). The bike is {@link #dismissCycle}. */
	public static void clear(ServerPlayer p) {
		UUID id = p.getUUID();
		BLASTS.remove(id);
		WAVES.remove(id);
		RUSHES.remove(id);
		ERUPTIONS.remove(id);
		STARES.remove(id);
		TRANSFORM_FX.remove(id);
	}

	/** Is this move's session running for {@code p}? (Tests.) */
	public static boolean running(ServerPlayer p, String id) {
		UUID u = p.getUUID();
		return switch (id) {
			case BLAST -> BLASTS.containsKey(u);
			case WAVE -> WAVES.containsKey(u);
			case RUSH -> RUSHES.containsKey(u);
			case ERUPTION -> ERUPTIONS.containsKey(u);
			case STARE -> STARES.containsKey(u);
			case SPIN -> GhostRider.state(p).spinUntil > p.level().getGameTime();
			case SHIELD -> GhostRider.shieldUp(p);
			case CYCLE -> cycleOf(p) != null;
			default -> false;
		};
	}

	// ---------------------------------------------------------------- the gate

	static boolean begin(ServerPlayer p, String id, float cost) {
		if (!GhostRider.canAct(p)) {
			return false;
		}
		int cd = GhostRider.cooldownRemaining(p, id);
		if (cd > 0) {
			GhostRider.say(p, "message.projecthero.ghost_rider.cooldown", ChatFormatting.GRAY,
					Component.translatable("projecthero.ghost_rider.ability." + id), String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		if (!GhostRider.spendHellfire(p, cost)) {
			GhostRider.say(p, "message.projecthero.ghost_rider.low_hellfire", ChatFormatting.GOLD, (int) Math.ceil(cost),
					(int) Math.floor(GhostRider.hellfire(p)));
			return false;
		}
		return true;
	}

	static void cooldown(ServerPlayer p, String id, int ticks) {
		GhostRiderState n = GhostRider.state(p).copy();
		n.abilityReadyAt.put(id, p.level().getGameTime() + ticks);
		GhostRider.save(p, n);
	}

	private static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	private static void sound(ServerPlayer p, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
		level(p).playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	/** In front of the right hand, roughly. */
	static Vec3 hand(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		return eye.add(look.scale(0.7)).add(right.scale(0.35)).add(0, -0.35, 0);
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : f.normalize();
	}

	// ================================================================ the transformation effect

	static void beginTransformFx(ServerPlayer p) {
		TRANSFORM_FX.put(p.getUUID(), p.level().getGameTime());
		sound(p, SoundEvents.FIRECHARGE_USE, 1.2f, 0.5f);
		sound(p, SoundEvents.RAVAGER_ROAR, 0.7f, 0.6f);
	}

	private static void tickTransformFx(ServerPlayer p, long start, long now) {
		long age = now - start;
		ServerLevel level = level(p);
		if (age > GhostRiderConfig.TRANSFORM_TICKS) {
			TRANSFORM_FX.remove(p.getUUID());
			return;
		}
		float prog = age / (float) GhostRiderConfig.TRANSFORM_TICKS;
		Vec3 back = p.position().add(0, 1.1, 0).subtract(Vec3.directionFromRotation(0f, p.yBodyRot).scale(0.35));
		if (prog < GhostRiderConfig.FOLD_END && age % 3 == 0) {
			// the leather wraps round him from the back: dark smoke off the spine
			level.sendParticles(ParticleTypes.SMOKE, back.x, back.y, back.z, 3, 0.15, 0.5, 0.15, 0.01);
		} else if (prog >= GhostRiderConfig.BURN_START && prog < GhostRiderConfig.BURN_END && age % 3 == 0) {
			// the face burns away (slowly, v0.16.5 rework): embers and ash off the head
			level.sendParticles(ParticleTypes.SMALL_FLAME, p.getX(), p.getEyeY(), p.getZ(), 2, 0.18, 0.15, 0.18, 0.01);
			level.sendParticles(ParticleTypes.ASH, p.getX(), p.getEyeY(), p.getZ(), 3, 0.2, 0.2, 0.2, 0.0);
			if (age % 12 == 0) {
				sound(p, SoundEvents.FIRE_AMBIENT, 1.2f, 0.6f);
			}
		}
		if (age == Math.round(GhostRiderConfig.TRANSFORM_TICKS * GhostRiderConfig.ERUPT)) {
			// the skull ignites -- flames only (v0.16.5 rework: no orange dust, no lava pops)
			level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getEyeY() + 0.3, p.getZ(), 70, 0.25, 0.45, 0.25, 0.09);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getEyeY() + 0.3, p.getZ(), 16, 0.2, 0.35, 0.2, 0.05);
			sound(p, SoundEvents.BLAZE_SHOOT, 1.4f, 0.5f);
			sound(p, SoundEvents.GHAST_SCREAM, 0.6f, 0.5f);
			GhostRiderCombat.shake(level, p.position(), 0.6f, 10, 12.0);
		}
	}

	// ================================================================ R: Hellfire Blast (held)

	public static void startBlast(ServerPlayer p) {
		if (BLASTS.containsKey(p.getUUID()) || !begin(p, BLAST, 0f)) {
			return;
		}
		if (GhostRider.hellfire(p) + 1.0e-3f < GhostRiderConfig.BLAST_COST) {
			GhostRider.say(p, "message.projecthero.ghost_rider.low_hellfire", ChatFormatting.GOLD, (int) GhostRiderConfig.BLAST_COST,
					(int) Math.floor(GhostRider.hellfire(p)));
			return;
		}
		BLASTS.put(p.getUUID(), p.level().getGameTime());
		setFlag(p, true);
		fireBolt(p); // the first bolt leaves the moment R goes down
	}

	public static void stopBlast(ServerPlayer p) {
		if (BLASTS.remove(p.getUUID()) == null) {
			return;
		}
		setFlag(p, false);
		cooldown(p, BLAST, GhostRiderConfig.BLAST_COOLDOWN);
	}

	private static void setFlag(ServerPlayer p, boolean on) {
		GhostRiderState s = GhostRider.state(p);
		if (s.blasting != on) {
			GhostRiderState n = s.copy();
			n.blasting = on;
			GhostRider.save(p, n);
		}
	}

	private static void tickBlast(ServerPlayer p, long start, long now) {
		if (!GhostRider.hasPower(p) || !p.isAlive()) { // v0.16.5: a human can hold the blast too (at 60%)
			stopBlast(p);
			return;
		}
		if (now == start || (now - start) % GhostRiderConfig.BLAST_INTERVAL != 0) {
			return;
		}
		fireBolt(p);
	}

	/**
	 * One hellfire bolt from the hand toward the aim point, if there is the Hellfire for it (else the blast stops). v0.16.5
	 * rework: a real projectile ({@link HellfireBlastEntity}), drawn as a ball of skull fire.
	 */
	private static void fireBolt(ServerPlayer p) {
		if (!GhostRider.spendHellfire(p, GhostRiderConfig.BLAST_COST)) {
			GhostRider.say(p, "message.projecthero.ghost_rider.blast_empty", ChatFormatting.GOLD);
			stopBlast(p);
			return;
		}
		Vec3 from = hand(p);
		Vec3 aim = AbilityHelpers.aimPoint(p, GhostRiderConfig.BLAST_RANGE);
		Vec3 dir = aim.subtract(from);
		dir = dir.lengthSqr() < 1.0e-4 ? p.getLookAngle() : dir.normalize();
		HellfireBlastEntity.fire(p, from, dir.scale(GhostRiderConfig.BLAST_SPEED));
		sound(p, SoundEvents.BLAZE_SHOOT, 1.0f, 0.7f + p.getRandom().nextFloat() * 0.2f);
		sound(p, SoundEvents.FIRECHARGE_USE, 0.6f, 1.2f);
	}

	// ================================================================ Shift+R: Hellfire Wave

	public static void wave(ServerPlayer p) {
		if (!begin(p, WAVE, GhostRiderConfig.WAVE_COST)) {
			return;
		}
		cooldown(p, WAVE, GhostRiderConfig.WAVE_COOLDOWN);
		GhostRider.setAnim(p, GhostRiderState.ANIM_WAVE);
		WAVES.put(p.getUUID(), new Wave(p.position(), flatLook(p), p.level().getGameTime()));
		sound(p, SoundEvents.BLAZE_SHOOT, 1.2f, 0.5f);
		sound(p, SoundEvents.FIRECHARGE_USE, 1.0f, 0.5f);
	}

	private static void tickWave(ServerPlayer p, Wave w, long now) {
		long age = now - w.start - 3; // the hand sweeps down first
		if (age < 0) {
			return;
		}
		double d = Math.min(GhostRiderConfig.WAVE_LENGTH, 1.0 + age * 1.2);
		ServerLevel level = level(p);
		Vec3 side = new Vec3(-w.dir.z, 0, w.dir.x);
		for (int s = -1; s <= 1; s++) {
			Vec3 at = w.origin.add(w.dir.scale(d)).add(side.scale(s * 0.9));
			double y = groundY(level, at);
			level.sendParticles(ParticleTypes.FLAME, at.x, y + 0.3, at.z, 6, 0.3, 0.4, 0.3, 0.03);
			level.sendParticles(GhostRider.HELLFIRE, at.x, y + 0.6, at.z, 2, 0.2, 0.4, 0.2, 0.0);
		}
		Vec3 c = w.origin.add(w.dir.scale(d));
		AABB box = new AABB(c, c).inflate(1.8, 2.0, 1.8);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> GhostRiderCombat.isTarget(p, e))) {
			if (w.hit.add(e.getId())) {
				GhostRiderCombat.strike(p, e, w.origin, GhostRiderConfig.WAVE_DAMAGE, 0.6, 0.45, 4);
			}
		}
		if (d >= GhostRiderConfig.WAVE_LENGTH) {
			WAVES.remove(p.getUUID());
		}
	}

	private static double groundY(ServerLevel level, Vec3 at) {
		BlockPos pos = BlockPos.containing(at.x, at.y + 2, at.z);
		for (int i = 0; i < 6; i++) {
			if (!level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
				return pos.getY();
			}
			pos = pos.below();
		}
		return at.y;
	}

	// ================================================================ G: Chain Whip

	public static void whip(ServerPlayer p) {
		if (!begin(p, WHIP, GhostRiderConfig.WHIP_COST)) {
			return;
		}
		cooldown(p, WHIP, GhostRiderConfig.WHIP_COOLDOWN);
		GhostRider.setAnim(p, GhostRiderState.ANIM_WHIP);
		ServerLevel level = level(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 far = eye.add(look.scale(GhostRiderConfig.WHIP_RANGE));
		BlockHitResult bhr = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		LivingEntity target = null;
		double best = Double.MAX_VALUE;
		for (Entity e : level.getEntities(p, new AABB(eye, end).inflate(1.0),
				e -> e instanceof LivingEntity le && GhostRiderCombat.isTarget(p, le) && e.isPickable())) {
			AABB box = e.getBoundingBox().inflate(0.6);
			Vec3 at = box.contains(eye) ? eye : box.clip(eye, end).orElse(null);
			if (at != null && eye.distanceToSqr(at) < best) {
				best = eye.distanceToSqr(at);
				target = (LivingEntity) e;
			}
		}
		Vec3 tip = target != null ? target.position().add(0, target.getBbHeight() * 0.6, 0) : end;
		GhostRiderState n = GhostRider.state(p).copy();
		n.chainUntil = p.level().getGameTime() + 8;
		n.chainTarget = List.of(tip.x, tip.y, tip.z);
		GhostRider.save(p, n);
		sound(p, SoundEvents.CHAIN_PLACE, 1.4f, 0.6f);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
		AbilityHelpers.line(level, hand(p), tip, ParticleTypes.FLAME, 2.0);
		if (target != null) {
			if (GhostRiderCombat.strike(p, target, p.position(), GhostRiderConfig.WHIP_DAMAGE, 0.0, 0.0, 3)) {
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 1.4f, 0.7f);
				if (GhostRiderCombat.isMovable(p, target) && target.getBbWidth() <= GhostRiderConfig.WHIP_YANK_MAX_WIDTH
						&& !(target instanceof Player)) {
					Vec3 pull = p.position().subtract(target.position());
					double dist = pull.length();
					if (dist > 1.5) {
						Vec3 v = pull.normalize().scale(Math.min(1.6, 0.35 + dist * 0.16)).add(0, 0.35, 0);
						target.setDeltaMovement(v);
						target.hurtMarked = true;
					}
				}
			}
		}
	}

	// ================================================================ Shift+G: Chain Spin

	public static void spin(ServerPlayer p) {
		if (!begin(p, SPIN, GhostRiderConfig.SPIN_COST)) {
			return;
		}
		cooldown(p, SPIN, GhostRiderConfig.SPIN_COOLDOWN);
		long now = p.level().getGameTime();
		GhostRiderState n = GhostRider.state(p).copy();
		n.spinUntil = now + GhostRiderConfig.SPIN_TICKS;
		n.chainUntil = now + GhostRiderConfig.SPIN_TICKS;
		n.chainTarget = List.of();
		n.animId = GhostRiderState.ANIM_SPIN;
		n.animStart = now;
		GhostRider.save(p, n);
		sound(p, SoundEvents.CHAIN_PLACE, 1.4f, 0.5f);
	}

	private static void tickSpin(ServerPlayer p, GhostRiderState s, long now) {
		long left = s.spinUntil - now;
		long age = GhostRiderConfig.SPIN_TICKS - left;
		ServerLevel level = level(p);
		double a = age * 0.9;
		for (int i = 0; i < 2; i++) {
			double ang = a + Math.PI * i;
			Vec3 at = p.position().add(Math.cos(ang) * GhostRiderConfig.SPIN_RADIUS * 0.85, 1.0, Math.sin(ang) * GhostRiderConfig.SPIN_RADIUS * 0.85);
			level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 3, 0.1, 0.1, 0.1, 0.01);
		}
		if (age % GhostRiderConfig.SPIN_HIT_INTERVAL == 0) {
			// v0.16.5 rework: the full 13 every hit, out to the edge
			GhostRiderCombat.radial(p, p.position().add(0, 1.0, 0), GhostRiderConfig.SPIN_RADIUS, GhostRiderConfig.SPIN_DAMAGE, 0.6, 0.1, 3, null,
					false);
			sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.5f + age * 0.01f);
			sound(p, SoundEvents.CHAIN_HIT, 0.7f, 0.8f);
		}
	}

	// ================================================================ X: Hell Cycle

	public static HellCycleEntity cycleOf(ServerPlayer p) {
		Integer id = CYCLES.get(p.getUUID());
		if (id == null) {
			return null;
		}
		Entity e = p.level().getEntity(id);
		if (e instanceof HellCycleEntity c && c.isAlive()) {
			return c;
		}
		CYCLES.remove(p.getUUID());
		return null;
	}

	/**
	 * X (v0.16.5 rework): riding his bike, the Cycle Jump; otherwise a bike under him (one left standing somewhere is
	 * called back to hell first). Shift+X is {@link #sendCycleBack}.
	 */
	public static void cycleKey(ServerPlayer p) {
		HellCycleEntity bike = cycleOf(p);
		if (bike != null && p.getVehicle() == bike) {
			cycleJump(p);
		} else {
			cycle(p);
		}
	}

	/** Shift+X: the bike goes back to hell (v0.16.5 rework). */
	public static void sendCycleBack(ServerPlayer p) {
		if (cycleOf(p) == null) {
			GhostRider.say(p, "message.projecthero.ghost_rider.no_cycle", ChatFormatting.GRAY);
			return;
		}
		dismissCycle(p);
	}

	/** Summons the Hell Cycle under him (replacing one left standing). */
	public static void cycle(ServerPlayer p) {
		if (!begin(p, CYCLE, 0f)) {
			return;
		}
		if (p.isPassenger()) {
			GhostRider.say(p, "message.projecthero.ghost_rider.cycle_busy", ChatFormatting.GRAY);
			return;
		}
		cooldown(p, CYCLE, GhostRiderConfig.CYCLE_COOLDOWN);
		if (cycleOf(p) != null) {
			dismissCycle(p);
		}
		ServerLevel level = level(p);
		HellCycleEntity bike = GhostRiderEntityTypes.HELL_CYCLE.create(level);
		if (bike == null) {
			return;
		}
		bike.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0f);
		bike.setOwner(p.getUUID());
		level.addFreshEntity(bike);
		p.startRiding(bike, true);
		CYCLES.put(p.getUUID(), bike.getId());
		GhostRider.setAnim(p, GhostRiderState.ANIM_SUMMON);
		if (GhostRider.transformed(p)) {
			level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.5, p.getZ(), 50, 0.8, 0.4, 0.8, 0.06);
			sound(p, SoundEvents.BLAZE_SHOOT, 1.2f, 0.4f);
		} else {
			// v0.16.5: a human's bike arrives cold -- smoke, no fire
			sound(p, SoundEvents.ANVIL_PLACE, 0.6f, 0.6f);
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 0.5, p.getZ(), 6, 0.8, 0.3, 0.8, 0.02);
		sound(p, SoundEvents.RAVAGER_ROAR, 0.6f, 1.6f);
	}

	/** Sends the bike back to hell (Shift+X, revert, death, logout). */
	public static void dismissCycle(ServerPlayer p) {
		HellCycleEntity bike = cycleOf(p);
		CYCLES.remove(p.getUUID());
		if (bike == null) {
			return;
		}
		ServerLevel level = (ServerLevel) bike.level();
		level.sendParticles(ParticleTypes.FLAME, bike.getX(), bike.getY() + 0.5, bike.getZ(), 30, 0.7, 0.3, 0.7, 0.04);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, bike.getX(), bike.getY() + 0.5, bike.getZ(), 15, 0.6, 0.3, 0.6, 0.02);
		level.playSound(null, bike.getX(), bike.getY(), bike.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.6f);
		bike.ejectPassengers();
		bike.discard();
	}

	// ================================================================ X while riding: Cycle Jump

	public static void cycleJump(ServerPlayer p) {
		HellCycleEntity bike = cycleOf(p);
		if (bike == null || p.getVehicle() != bike) {
			GhostRider.say(p, "message.projecthero.ghost_rider.not_riding", ChatFormatting.GRAY);
			return;
		}
		if (!begin(p, CYCLE_JUMP, GhostRiderConfig.CYCLE_JUMP_COST)) {
			return;
		}
		cooldown(p, CYCLE_JUMP, GhostRiderConfig.CYCLE_JUMP_COOLDOWN);
		GhostRider.setAnim(p, GhostRiderState.ANIM_CYCLE_JUMP);
		Vec3 look = flatLook(p).add(0, 0.55, 0);
		Vec3 v = AbilityHelpers.ballisticLaunch(look, GhostRiderConfig.CYCLE_JUMP_DISTANCE, bike.onGround());
		bike.leap(v, p);
		sound(p, SoundEvents.BLAZE_SHOOT, 1.2f, 0.5f);
		sound(p, SoundEvents.RAVAGER_ROAR, 0.5f, 1.8f);
	}

	/** The bike came down from a Cycle Jump: a burning landing. */
	public static void cycleLanded(ServerPlayer p, Vec3 at) {
		ServerLevel level = level(p);
		GhostRiderCombat.radial(p, at, GhostRiderConfig.CYCLE_JUMP_RADIUS, GhostRiderConfig.CYCLE_JUMP_DAMAGE, 1.0, 0.5, 5, null);
		for (int i = 0; i < 24; i++) {
			double a = Math.PI * 2 * i / 24;
			level.sendParticles(ParticleTypes.FLAME, at.x + Math.cos(a) * 2.5, at.y + 0.2, at.z + Math.sin(a) * 2.5, 3, 0.2, 0.2, 0.2, 0.03);
		}
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.2, at.z, 10, 1.0, 0.2, 1.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 0.8f);
		GhostRiderCombat.shake(level, at, 0.8f, 10, 16.0);
	}

	// ================================================================ Z: Judgement Mark

	public static void mark(ServerPlayer p) {
		List<LivingEntity> guilty = GhostRiderCombat.within(p, p.getEyePosition(), GhostRiderConfig.MARK_RANGE,
				e -> GhostRiderCombat.isTarget(p, e) && GhostRiderDamage.guilt(e) > 0);
		if (guilty.isEmpty()) {
			if (GhostRider.canAct(p)) {
				GhostRider.say(p, "message.projecthero.ghost_rider.no_guilty", ChatFormatting.GRAY);
			}
			return;
		}
		if (!begin(p, MARK, GhostRiderConfig.MARK_COST)) {
			return;
		}
		cooldown(p, MARK, GhostRiderConfig.MARK_COOLDOWN);
		GhostRider.setAnim(p, GhostRiderState.ANIM_MARK);
		ServerLevel level = level(p);
		List<Integer> ids = new ArrayList<>();
		for (LivingEntity e : guilty) {
			GhostRiderDamage.mark(p, e, GhostRiderConfig.MARK_TICKS);
			ids.add(e.getId());
			Vec3 c = e.position().add(0, e.getBbHeight() + 0.3, 0);
			level.sendParticles(GhostRider.EMBER, c.x, c.y, c.z, 12, 0.2, 0.2, 0.2, 0.0);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y, c.z, 6, 0.2, 0.2, 0.2, 0.02);
		}
		ServerPlayNetworking.send(p, new GhostRiderMarkPayload(ids, GhostRiderConfig.MARK_TICKS));
		sound(p, SoundEvents.WITHER_AMBIENT, 0.8f, 0.6f);
		sound(p, SoundEvents.BELL_RESONATE, 0.8f, 0.5f);
		GhostRider.say(p, "message.projecthero.ghost_rider.marked", ChatFormatting.RED, ids.size());
	}

	// ================================================================ Shift+Z: Penance Stare

	public static void stare(ServerPlayer p) {
		if (STARES.containsKey(p.getUUID())) {
			return;
		}
		LivingEntity target = AbilityHelpers.raycastEntity(p, GhostRiderConfig.STARE_RANGE);
		if (target == null || !GhostRiderCombat.isTarget(p, target)) {
			if (GhostRider.canAct(p)) {
				GhostRider.say(p, "message.projecthero.ghost_rider.no_stare_target", ChatFormatting.GRAY);
			}
			return;
		}
		if (!begin(p, STARE, GhostRiderConfig.STARE_COST)) {
			return;
		}
		long now = p.level().getGameTime();
		STARES.put(p.getUUID(), new Stare(target.getId(), now));
		GhostRiderState n = GhostRider.state(p).copy();
		n.stareTarget = target.getId();
		n.animId = GhostRiderState.ANIM_STARE;
		n.animStart = now;
		GhostRider.save(p, n);
		sound(p, SoundEvents.WITHER_AMBIENT, 1.0f, 0.4f);
		level(p).playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5f, 0.6f);
		if (target instanceof ServerPlayer tp) {
			tp.displayClientMessage(Component.translatable("message.projecthero.ghost_rider.stared_at").withStyle(ChatFormatting.DARK_RED), true);
		}
	}

	private static void endStare(ServerPlayer p) {
		STARES.remove(p.getUUID());
		GhostRiderState s = GhostRider.state(p);
		if (s.stareTarget != -1) {
			GhostRiderState n = s.copy();
			n.stareTarget = -1;
			GhostRider.save(p, n);
		}
	}

	private static void tickStare(ServerPlayer p, Stare st, long now) {
		Entity e = p.level().getEntity(st.targetId);
		if (!(e instanceof LivingEntity target) || !target.isAlive() || !GhostRider.hasPower(p)
				|| target.distanceToSqr(p) > (GhostRiderConfig.STARE_RANGE + 4) * (GhostRiderConfig.STARE_RANGE + 4)
				|| !p.hasLineOfSight(target)) {
			endStare(p);
			cooldown(p, STARE, GhostRiderConfig.STARE_COOLDOWN / 4);
			return;
		}
		long age = now - st.start;
		ServerLevel level = level(p);
		// the target is held in the burning gaze
		if (!GhostRiderCombat.isBoss(target)) {
			target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
			target.hurtMarked = true;
			if (target instanceof Mob mob) {
				mob.getNavigation().stop();
				mob.getLookControl().setLookAt(p, 90f, 90f);
			} else {
				AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, 5, 6);
			}
		}
		Vec3 from = p.getEyePosition();
		Vec3 to = target.getEyePosition();
		if (age % 2 == 0) {
			AbilityHelpers.line(level, from.add(to.subtract(from).normalize().scale(0.8)), to, ParticleTypes.SOUL_FIRE_FLAME, 1.2);
		}
		double r = 0.6 + target.getBbWidth() * 0.5;
		double a = age * 0.6;
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX() + Math.cos(a) * r, target.getY() + target.getBbHeight() * (age / 40.0),
				target.getZ() + Math.sin(a) * r, 2, 0.05, 0.05, 0.05, 0.0);
		level.sendParticles(GhostRider.EMBER, target.getX(), target.getEyeY(), target.getZ(), 2, 0.25, 0.25, 0.25, 0.0);
		if (age < GhostRiderConfig.STARE_TICKS) {
			return;
		}
		// judgement
		float dmg = penanceDamage(p, target);
		STARES.remove(p.getUUID());
		GhostRiderCombat.strike(p, target, p.position(), dmg, 0.0, 0.0, 6);
		// all the Vengeance is spent (after the hit, which would otherwise feed some straight back)
		GhostRiderState n = GhostRider.state(p).copy();
		n.vengeance = 0f;
		n.stareTarget = -1;
		GhostRider.save(p, n);
		cooldown(p, STARE, GhostRiderConfig.STARE_COOLDOWN);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 50, 0.4,
				target.getBbHeight() * 0.4, 0.4, 0.06);
		level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getEyeY(), target.getZ(), 12, 0.3, 0.3, 0.3, 0.04);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 2.0f, 0.4f);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 1.2f, 0.5f);
		if (target instanceof Mob mob && !GhostRiderCombat.isBoss(target) && target.isAlive()) {
			mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
		}
	}

	/** How hard the Penance Stare burns {@code target} for this Ghost Rider (tests read it too). */
	public static float penanceDamage(ServerPlayer p, LivingEntity target) {
		if (target.getType().is(EntityTypeTags.UNDEAD) || GhostRiderCombat.isBoss(target)) {
			return GhostRiderConfig.STARE_FLAT;
		}
		float dmg;
		if (target instanceof ServerPlayer tp) {
			int kills = tp.getStats().getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS));
			dmg = GhostRiderConfig.STARE_BASE + kills * GhostRiderConfig.STARE_PER_PLAYER_KILL;
		} else {
			dmg = GhostRiderConfig.STARE_BASE + GhostRider.vengeance(p) * GhostRiderConfig.STARE_PER_VENGEANCE
					+ GhostRiderDamage.guilt(target) * GhostRiderConfig.STARE_PER_GUILT;
		}
		return Math.min(GhostRiderConfig.STARE_MAX, dmg);
	}

	// ================================================================ V: Hellfire Rush

	public static void rush(ServerPlayer p) {
		if (RUSHES.containsKey(p.getUUID()) || !begin(p, RUSH, GhostRiderConfig.RUSH_COST)) {
			return;
		}
		if (p.isPassenger()) {
			p.stopRiding();
		}
		cooldown(p, RUSH, GhostRiderConfig.RUSH_COOLDOWN);
		long now = p.level().getGameTime();
		RUSHES.put(p.getUUID(), new Rush(flatLook(p), now, p.position()));
		GhostRiderState n = GhostRider.state(p).copy();
		n.rushing = true;
		n.animId = GhostRiderState.ANIM_RUSH;
		n.animStart = now;
		GhostRider.save(p, n);
		sound(p, SoundEvents.BLAZE_SHOOT, 1.4f, 0.4f);
		sound(p, SoundEvents.RAVAGER_ROAR, 0.6f, 1.2f);
	}

	private static void endRush(ServerPlayer p) {
		RUSHES.remove(p.getUUID());
		GhostRiderState s = GhostRider.state(p);
		if (s.rushing) {
			GhostRiderState n = s.copy();
			n.rushing = false;
			GhostRider.save(p, n);
		}
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().multiply(0.2, 1.0, 0.2));
	}

	private static void tickRush(ServerPlayer p, Rush r, long now) {
		long age = now - r.start;
		r.travelled += p.position().subtract(r.last).horizontalDistance();
		r.last = p.position();
		if (r.travelled >= GhostRiderConfig.RUSH_DISTANCE || age > 20 || (age > 2 && p.horizontalCollision) || !p.isAlive()) {
			endRush(p);
			return;
		}
		AbilityHelpers.launchSelf(p, new Vec3(r.dir.x * GhostRiderConfig.RUSH_SPEED, Math.min(0.0, p.getDeltaMovement().y), r.dir.z * GhostRiderConfig.RUSH_SPEED));
		ServerLevel level = level(p);
		level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 14, 0.4, 0.6, 0.4, 0.03);
		level.sendParticles(GhostRider.HELLFIRE_BIG, p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.3, 0.5, 0.3, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 0.5, p.getZ(), 2, 0.3, 0.2, 0.3, 0.01);
		AABB box = p.getBoundingBox().inflate(1.2, 0.5, 1.2).expandTowards(r.dir.scale(1.2));
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> GhostRiderCombat.isTarget(p, e))) {
			if (r.hit.add(e.getId())) {
				if (GhostRiderCombat.strike(p, e, p.position().subtract(r.dir), GhostRiderConfig.RUSH_DAMAGE, GhostRiderConfig.RUSH_KNOCKBACK, 0.55, 5)) {
					level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2f, 0.6f);
					GhostRiderCombat.shake(level, e.position(), 0.4f, 6, 10.0);
				}
			}
		}
	}

	// ================================================================ Shift+V: Hellfire Eruption

	public static void eruption(ServerPlayer p) {
		if (ERUPTIONS.containsKey(p.getUUID()) || !begin(p, ERUPTION, GhostRiderConfig.ERUPTION_COST)) {
			return;
		}
		cooldown(p, ERUPTION, GhostRiderConfig.ERUPTION_COOLDOWN);
		GhostRider.setAnim(p, GhostRiderState.ANIM_ERUPTION);
		ERUPTIONS.put(p.getUUID(), new Eruption(p.position(), p.level().getGameTime()));
		sound(p, SoundEvents.RAVAGER_ROAR, 0.8f, 0.5f);
	}

	private static void tickEruption(ServerPlayer p, Eruption er, long now) {
		long age = now - er.start;
		ServerLevel level = level(p);
		Vec3 c = er.center;
		double r = GhostRiderConfig.ERUPTION_RADIUS;
		if (age == 7) {
			// the fist hits the ground: pillars burst up all round
			GhostRiderCombat.radial(p, c.add(0, 0.5, 0), r, GhostRiderConfig.ERUPTION_DAMAGE, 0.4, GhostRiderConfig.ERUPTION_LIFT, 6, null);
			for (int i = 0; i < 14; i++) {
				double a = Math.PI * 2 * i / 14 + p.getRandom().nextDouble() * 0.3;
				double d = 1.5 + p.getRandom().nextDouble() * (r - 1.5);
				pillar(level, c.add(Math.cos(a) * d, 0, Math.sin(a) * d));
			}
			pillar(level, c);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4f, 0.6f);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.6f, 0.4f);
			GhostRiderCombat.shake(level, c, 1.2f, 16, 24.0);
		}
		if (age > 7) {
			// the ground keeps burning
			if (age % 3 == 0) {
				for (int i = 0; i < 6; i++) {
					double a = p.getRandom().nextDouble() * Math.PI * 2;
					double d = Math.sqrt(p.getRandom().nextDouble()) * r;
					Vec3 at = c.add(Math.cos(a) * d, 0, Math.sin(a) * d);
					double y = groundY(level, at);
					level.sendParticles(ParticleTypes.FLAME, at.x, y + 0.1, at.z, 2, 0.15, 0.05, 0.15, 0.02);
				}
			}
			if (age % 20 == 0) {
				for (LivingEntity e : GhostRiderCombat.within(p, c.add(0, 0.5, 0), r, e -> GhostRiderCombat.isHostile(p, e) && e.onGround())) {
					GhostRiderCombat.strike(p, e, c, GhostRiderConfig.ERUPTION_GROUND_DAMAGE, 0.0, 0.0, 2);
				}
			}
		}
		if (age >= 7 + GhostRiderConfig.ERUPTION_BURN_TICKS) {
			ERUPTIONS.remove(p.getUUID());
		}
	}

	private static void pillar(ServerLevel level, Vec3 at) {
		double y = groundY(level, at);
		for (int k = 0; k < 7; k++) {
			level.sendParticles(ParticleTypes.FLAME, at.x, y + k * 0.5, at.z, 5, 0.2, 0.2, 0.2, 0.04);
		}
		level.sendParticles(GhostRider.HELLFIRE_BIG, at.x, y + 1.5, at.z, 6, 0.25, 1.2, 0.25, 0.0);
		level.sendParticles(ParticleTypes.LAVA, at.x, y + 0.5, at.z, 2, 0.2, 0.2, 0.2, 0.0);
	}

	// ================================================================ C: Soul Shield

	public static void shield(ServerPlayer p) {
		if (GhostRider.shieldUp(p) || !begin(p, SHIELD, GhostRiderConfig.SHIELD_COST)) {
			return;
		}
		cooldown(p, SHIELD, GhostRiderConfig.SHIELD_COOLDOWN);
		long now = p.level().getGameTime();
		GhostRiderState n = GhostRider.state(p).copy();
		n.shieldUntil = now + GhostRiderConfig.SHIELD_TICKS;
		n.animId = GhostRiderState.ANIM_SHIELD;
		n.animStart = now;
		GhostRider.save(p, n);
		sound(p, SoundEvents.FIRECHARGE_USE, 1.2f, 0.6f);
		sound(p, SoundEvents.SOUL_ESCAPE.value(), 1.4f, 0.8f);
	}

	private static void tickShield(ServerPlayer p, long now) {
		ServerLevel level = level(p);
		double a = now * 0.5;
		for (int i = 0; i < 4; i++) {
			double ang = a + Math.PI * 0.5 * i;
			level.sendParticles(ParticleTypes.FLAME, p.getX() + Math.cos(ang) * 1.3, p.getY() + 0.2 + (now % 20) * 0.1,
					p.getZ() + Math.sin(ang) * 1.3, 1, 0.02, 0.02, 0.02, 0.0);
		}
		if (now % 3 == 0) {
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.7, 0.6, 0.7, 0.0);
		}
	}

	// ================================================================ Shift+C: Rider's Wrath

	public static void wrath(ServerPlayer p) {
		if (!begin(p, WRATH, GhostRiderConfig.WRATH_COST)) {
			return;
		}
		cooldown(p, WRATH, GhostRiderConfig.WRATH_COOLDOWN);
		GhostRider.setAnim(p, GhostRiderState.ANIM_WRATH);
		ServerLevel level = level(p);
		int n = 0;
		for (Player ally : level.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(GhostRiderConfig.WRATH_RADIUS),
				e -> e.isAlive() && !e.isSpectator() && (e == p || Squads.areAllies(p, e)))) {
			ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, GhostRiderConfig.WRATH_TICKS, 1));
			ally.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, GhostRiderConfig.WRATH_TICKS, 0));
			level.sendParticles(ParticleTypes.FLAME, ally.getX(), ally.getY() + 1.0, ally.getZ(), 30, 0.4, 0.8, 0.4, 0.04);
			n++;
		}
		for (int i = 0; i < 32; i++) {
			double a = Math.PI * 2 * i / 32;
			level.sendParticles(ParticleTypes.FLAME, p.getX() + Math.cos(a) * GhostRiderConfig.WRATH_RADIUS, p.getY() + 0.2,
					p.getZ() + Math.sin(a) * GhostRiderConfig.WRATH_RADIUS, 2, 0.05, 0.3, 0.05, 0.02);
		}
		level.sendParticles(GhostRider.HELLFIRE_BIG, p.getX(), p.getEyeY() + 0.6, p.getZ(), 30, 0.3, 0.8, 0.3, 0.0);
		sound(p, SoundEvents.RAVAGER_ROAR, 1.0f, 0.7f);
		sound(p, SoundEvents.BLAZE_SHOOT, 1.2f, 0.5f);
		GhostRider.say(p, "message.projecthero.ghost_rider.wrath", ChatFormatting.GOLD, n);
	}

	// ================================================================ tick

	static void tick(ServerPlayer p) {
		UUID u = p.getUUID();
		long now = p.level().getGameTime();
		Long fx = TRANSFORM_FX.get(u);
		if (fx != null) {
			tickTransformFx(p, fx, now);
		}
		Long blast = BLASTS.get(u);
		if (blast != null) {
			tickBlast(p, blast, now);
		}
		Wave w = WAVES.get(u);
		if (w != null) {
			tickWave(p, w, now);
		}
		Rush r = RUSHES.get(u);
		if (r != null) {
			tickRush(p, r, now);
		}
		Eruption er = ERUPTIONS.get(u);
		if (er != null) {
			tickEruption(p, er, now);
		}
		Stare st = STARES.get(u);
		if (st != null) {
			tickStare(p, st, now);
		}
		GhostRiderState s = GhostRider.state(p);
		if (s.spinUntil > now) {
			tickSpin(p, s, now);
		}
		if (s.shieldUntil > now) {
			tickShield(p, now);
		}
		if (s.chainUntil != 0L && s.chainUntil <= now) {
			GhostRiderState n = s.copy();
			n.chainUntil = 0L;
			n.chainTarget = List.of();
			GhostRider.save(p, n);
		}
		// a bike whose rider got off (or that lost its rider) goes back to hell after a moment
		HellCycleEntity bike = cycleOf(p);
		if (bike != null && bike.getFirstPassenger() != p && bike.tickCount % 20 == 0 && bike.idleTicks() > 200) {
			dismissCycle(p);
		}
	}
}
