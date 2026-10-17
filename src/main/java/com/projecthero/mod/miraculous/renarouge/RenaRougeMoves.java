package com.projecthero.mod.miraculous.renarouge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.renarouge.entity.FakeWallEntity;
import com.projecthero.mod.miraculous.renarouge.entity.FluteBoomerangEntity;
import com.projecthero.mod.miraculous.renarouge.entity.MirageBubbleEntity;
import com.projecthero.mod.miraculous.renarouge.entity.RenaIllusionEntity;
import com.projecthero.mod.miraculous.renarouge.entity.RenaRougeEntityTypes;
import com.projecthero.mod.punisher.PunisherControl;
import com.projecthero.mod.squad.Squads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: Rena Rouge's twelve moves (see {@code docs/MIRACULOUS_RENA_ROUGE.md}) and the per-player sessions of the ones
 * that run over time (the Lure, the Mirage's melody / bubble / confusion, the Pounce in the air). Every public move is
 * called by {@link RenaRougeKit#handle} once its cooldown is ready, and by the gametests directly.
 *
 * <p>The sessions are static and keyed by player; they hold entity ids, never entities, and are dropped by
 * {@link RenaRougeKit#forget} / {@link RenaRougeKit#clearSessionState}.
 */
public final class RenaRougeMoves {
	/** A move running over time for one player. */
	static final class Session {
		// Melody Lure
		long lureUntil;
		Vec3 lurePoint;
		final Set<Integer> lured = new HashSet<>();
		// the Mirage
		long mirageBubbleAt;
		long mirageUntil;
		final Map<Integer, Long> confused = new HashMap<>();
		final Map<Integer, Long> rethinkAt = new HashMap<>();
		// Fox Pounce
		long pounceAt;
		// a little flute tune: game time -> pitch
		final Map<Long, Float> notes = new HashMap<>();
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private RenaRougeMoves() {
	}

	static void clearSessionState() {
		SESSIONS.clear();
	}

	static void forget(UUID id) {
		SESSIONS.remove(id);
	}

	private static Session session(ServerPlayer p) {
		return SESSIONS.computeIfAbsent(p.getUUID(), k -> new Session());
	}

	static @Nullable Session peekSession(ServerPlayer p) {
		return SESSIONS.get(p.getUUID());
	}

	private static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : f.normalize();
	}

	private static Vec3 rightOf(Vec3 flat) {
		return new Vec3(-flat.z, 0, flat.x);
	}

	private static void sound(ServerPlayer p, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	// ================================================================ R: Flute Strike / Shift+R: Flute Sweep

	/** The three-hit staff combo: forehand, backhand, overhead (the third knocks back). Returns who was hit, or null. */
	public static @Nullable LivingEntity strike(ServerPlayer p) {
		long now = now(p);
		long last = Miraculous.state(p).val(RenaRouge.V_COMBO_AT);
		int step = (int) Miraculous.state(p).val(RenaRouge.V_COMBO);
		step = last > 0 && now - last <= RenaRougeConfig.STRIKE_COMBO_WINDOW ? step % 3 + 1 : 1;
		Miraculous.setVal(p, RenaRouge.V_COMBO, step);
		Miraculous.setVal(p, RenaRouge.V_COMBO_AT, now);
		Miraculous.setAnim(p, RenaRouge.ANIM_STRIKE_1 + step - 1);
		Miraculous.setCooldown(p, RenaRougeConfig.STRIKE, RenaRougeConfig.STRIKE_COOLDOWN);
		LivingEntity target = AbilityHelpers.raycastEntity(p, RenaRougeConfig.STRIKE_REACH);
		if (target == null || !RenaRougeCombat.isTarget(p, target)) {
			target = inFront(p, RenaRougeConfig.STRIKE_REACH, 0.5);
		}
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.3f + 0.15f * step);
		if (target == null) {
			return null;
		}
		boolean finisher = step == 3;
		if (RenaRougeCombat.strike(p, target, p.position(), RenaRougeConfig.STRIKE_DAMAGE, finisher ? RenaRougeConfig.STRIKE_FINISHER_KNOCKBACK : 0.25,
				finisher ? 0.15 : 0.0)) {
			sound(p, finisher ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.9f, 1.2f);
			return target;
		}
		return null;
	}

	/** The nearest hittable creature within {@code reach} roughly in front of her (dot of the look above {@code cone}). */
	private static @Nullable LivingEntity inFront(ServerPlayer p, double reach, double cone) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, eye, reach + 1.0)) {
			if (e instanceof RenaIllusionEntity) {
				continue;
			}
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			Vec3 to = c.subtract(eye);
			double d = Math.sqrt(AbilityHelpers.distanceSqToBox(e, eye));
			if (d > reach || to.lengthSqr() < 1.0e-6 || to.normalize().dot(look) < cone) {
				continue;
			}
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** A full 360 with the staff held out: everything within {@link RenaRougeConfig#SWEEP_RADIUS} is hit and thrown back. */
	public static int sweep(ServerPlayer p) {
		Miraculous.setAnim(p, RenaRouge.ANIM_SWEEP);
		Miraculous.setCooldown(p, RenaRougeConfig.SWEEP, RenaRougeConfig.SWEEP_COOLDOWN);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, 0.9, 0);
		for (int i = 0; i < 16; i++) {
			double a = Math.PI * 2 * i / 16;
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x + Math.cos(a) * 2.2, c.y, c.z + Math.sin(a) * 2.2, 1, 0, 0, 0, 0);
			level.sendParticles(RenaRougeCombat.ORANGE, c.x + Math.cos(a) * 3.0, c.y, c.z + Math.sin(a) * 3.0, 1, 0, 0, 0, 0);
		}
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.9f);
		return RenaRougeCombat.radial(p, c, RenaRougeConfig.SWEEP_RADIUS, RenaRougeConfig.SWEEP_DAMAGE, RenaRougeConfig.SWEEP_KNOCKBACK,
				RenaRougeConfig.SWEEP_LIFT, new HashSet<>());
	}

	// ================================================================ G: Flute Throw / Shift+G: Melody Lure

	/** Throws the Flute like a boomerang (refused while it is still out). */
	public static @Nullable FluteBoomerangEntity throwFlute(ServerPlayer p) {
		if (Miraculous.state(p).val(RenaRouge.V_FLUTE_OUT) != 0L) {
			Miraculous.say(p, "message.projecthero.rena_rouge.flute_out", ChatFormatting.GOLD);
			return null;
		}
		FluteBoomerangEntity f = RenaRougeEntityTypes.FLUTE.create(p.serverLevel());
		if (f == null) {
			return null;
		}
		Vec3 look = p.getLookAngle();
		Vec3 from = p.getEyePosition().add(0, -0.25, 0).add(look.scale(0.6));
		f.launch(p, from, look);
		p.serverLevel().addFreshEntity(f);
		Miraculous.setVal(p, RenaRouge.V_FLUTE_OUT, 1L);
		Miraculous.setAnim(p, RenaRouge.ANIM_THROW);
		RenaRouge.breakCamouflage(p);
		sound(p, SoundEvents.TRIDENT_THROW.value(), 0.8f, 1.5f);
		return f;
	}

	/** The Flute is back in her hand; the throw's cooldown starts now. */
	public static void fluteCaught(ServerPlayer p, FluteBoomerangEntity f) {
		if (!Miraculous.holds(p, Kwami.TRIXX)) {
			return;
		}
		Miraculous.setVal(p, RenaRouge.V_FLUTE_OUT, 0L);
		if (Miraculous.transformed(p)) {
			Miraculous.setAnim(p, RenaRouge.ANIM_CATCH);
			Miraculous.setCooldown(p, RenaRougeConfig.THROW, RenaRougeConfig.THROW_COOLDOWN);
		}
		sound(p, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8f, 1.4f);
	}

	/** Every mob within {@link RenaRougeConfig#LURE_RADIUS} walks to where she looks for {@link RenaRougeConfig#LURE_TICKS}. */
	public static int lure(ServerPlayer p) {
		Session s = session(p);
		long now = now(p);
		BlockHitResult hit = AbilityHelpers.raycastBlock(p, RenaRougeConfig.LURE_RANGE);
		Vec3 point = hit.getType() == HitResult.Type.MISS ? p.getEyePosition().add(p.getLookAngle().scale(RenaRougeConfig.LURE_RANGE * 0.6))
				: hit.getLocation();
		s.lurePoint = ground(p.serverLevel(), point);
		s.lureUntil = now + RenaRougeConfig.LURE_TICKS;
		s.lured.clear();
		for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(RenaRougeConfig.LURE_RADIUS),
				m -> m instanceof PathfinderMob && m.distanceToSqr(p) <= RenaRougeConfig.LURE_RADIUS * RenaRougeConfig.LURE_RADIUS
						&& !(m instanceof RenaIllusionEntity) && HeroTargets.canHarm(p, m) && !RenaRougeCombat.isBoss(m))) {
			s.lured.add(m.getId());
			PunisherControl.suppressTarget(m, RenaRougeConfig.LURE_TICKS);
			m.getNavigation().moveTo(s.lurePoint.x, s.lurePoint.y, s.lurePoint.z, 1.1);
		}
		Miraculous.setVal(p, RenaRouge.V_LURE, s.lureUntil);
		Miraculous.setVal(p, RenaRouge.V_PLAY, now + 40);
		Miraculous.setAnim(p, RenaRouge.ANIM_LURE);
		Miraculous.setCooldown(p, RenaRougeConfig.LURE, RenaRougeConfig.LURE_COOLDOWN);
		tune(s, now, new float[] { 1.19f, 1.33f, 1.5f, 1.33f, 1.78f, 1.5f, 1.19f }, 5);
		return s.lured.size();
	}

	/** Queues a short melody on the flute: one note every {@code gap} ticks. */
	private static void tune(Session s, long start, float[] pitches, int gap) {
		for (int i = 0; i < pitches.length; i++) {
			s.notes.put(start + (long) i * gap, pitches[i]);
		}
	}

	/** The point on the ground at or under {@code v} (up to 6 blocks down). */
	private static Vec3 ground(ServerLevel level, Vec3 v) {
		BlockPos pos = BlockPos.containing(v.x, v.y + 0.5, v.z);
		for (int i = 0; i < 8; i++) {
			BlockPos below = pos.below();
			if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty() && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return new Vec3(v.x, pos.getY(), v.z);
			}
			pos = below;
		}
		return v;
	}

	// ================================================================ X: Fox Dash / Shift+X: Illusion Swap

	/** Darts {@link RenaRougeConfig#DASH_DISTANCE} along the look, leaving a decoy behind that the mobs keep attacking. */
	public static @Nullable RenaIllusionEntity dash(ServerPlayer p) {
		Vec3 start = p.position();
		RenaIllusionEntity decoy = RenaRougeIllusions.spawn(p, RenaIllusionEntity.DECOY, start, p.getYRot(), RenaRougeConfig.DASH_DECOY_TICKS, 99);
		if (decoy != null) {
			redirectFrom(p, decoy, 24.0);
		}
		Vec3 dir = p.getLookAngle();
		if (Math.abs(dir.y) < 0.35) {
			dir = flatLook(p); // a near-level look darts along the ground
		}
		Vec3 end = start;
		AABB bb = p.getBoundingBox();
		for (double d = 0.5; d <= RenaRougeConfig.DASH_DISTANCE; d += 0.5) {
			Vec3 at = start.add(dir.scale(d));
			if (!p.level().noCollision(p, bb.move(at.subtract(start)))) {
				break;
			}
			end = at;
		}
		ServerLevel level = p.serverLevel();
		AbilityHelpers.line(level, start.add(0, 1.0, 0), end.add(0, 1.0, 0), RenaRougeCombat.ORANGE, 3.0);
		p.teleportTo(level, end.x, end.y, end.z, p.getYRot(), p.getXRot());
		p.setDeltaMovement(dir.scale(0.3));
		p.hurtMarked = true;
		p.resetFallDistance();
		RenaRougeCombat.glow(level, end.add(0, 1.0, 0), 8, 0.3);
		Miraculous.setAnim(p, RenaRouge.ANIM_DASH);
		Miraculous.setCooldown(p, RenaRougeConfig.DASH, RenaRougeConfig.DASH_COOLDOWN);
		sound(p, SoundEvents.FOX_TELEPORT, 1.0f, 1.2f);
		return decoy;
	}

	/** Monsters (and bosses) within {@code range} that are after her turn on {@code illusion}. */
	private static void redirectFrom(ServerPlayer p, RenaIllusionEntity illusion, double range) {
		for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(range), m -> m.getTarget() == p)) {
			if (RenaRougeCombat.isBoss(m)) {
				RenaRougeIllusions.taunt(m, illusion);
			} else if (RenaRougeCombat.isFoolable(p, m)) {
				RenaRougeIllusions.retarget(m, illusion);
			}
		}
	}

	/** Trades places with her Illusion Double (first choice) or her nearest other illusion. False: none in range. */
	public static boolean swap(ServerPlayer p) {
		List<RenaIllusionEntity> own = RenaRougeIllusions.own(p, RenaRougeConfig.SWAP_RANGE);
		RenaIllusionEntity pick = null;
		double best = Double.MAX_VALUE;
		for (RenaIllusionEntity e : own) {
			if (e.kind() == RenaIllusionEntity.FEINT) {
				continue;
			}
			double d = e.distanceToSqr(p) - (e.kind() == RenaIllusionEntity.DOUBLE ? 1.0e6 : 0.0);
			if (d < best) {
				best = d;
				pick = e;
			}
		}
		if (pick == null) {
			Miraculous.say(p, "message.projecthero.rena_rouge.no_illusion", ChatFormatting.GOLD);
			return false;
		}
		ServerLevel level = p.serverLevel();
		Vec3 mine = p.position();
		Vec3 theirs = pick.position();
		float myYaw = p.getYRot();
		RenaRougeCombat.glow(level, mine.add(0, 1.0, 0), 12, 0.35);
		RenaRougeCombat.glow(level, theirs.add(0, 1.0, 0), 12, 0.35);
		p.teleportTo(level, theirs.x, theirs.y, theirs.z, p.getYRot(), p.getXRot());
		p.resetFallDistance();
		pick.moveTo(mine.x, mine.y, mine.z, myYaw, 0f);
		pick.getNavigation().stop();
		redirectFrom(p, pick, 24.0);
		Miraculous.setAnim(p, RenaRouge.ANIM_SWAP);
		Miraculous.setCooldown(p, RenaRougeConfig.SWAP, RenaRougeConfig.SWAP_COOLDOWN);
		sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0f, 1.3f);
		return true;
	}

	// ================================================================ Z: Illusion Double / Shift+Z: Mirage

	/** A clone of her that fights at half her damage for {@link RenaRougeConfig#DOUBLE_TICKS}; mobs go for it. */
	public static @Nullable RenaIllusionEntity illusionDouble(ServerPlayer p) {
		for (RenaIllusionEntity old : RenaRougeIllusions.own(p, 256.0)) {
			if (old.kind() == RenaIllusionEntity.DOUBLE) {
				old.dissolve();
			}
		}
		Vec3 side = rightOf(flatLook(p)).scale(1.4);
		Vec3 at = p.position().add(side);
		if (!p.level().noCollision(p, p.getBoundingBox().move(side))) {
			at = p.position().subtract(side);
			if (!p.level().noCollision(p, p.getBoundingBox().move(side.scale(-1)))) {
				at = p.position();
			}
		}
		RenaIllusionEntity d = RenaRougeIllusions.spawn(p, RenaIllusionEntity.DOUBLE, at, p.getYRot(), RenaRougeConfig.DOUBLE_TICKS,
				RenaRougeConfig.DOUBLE_HITS);
		if (d == null) {
			return null;
		}
		redirectFrom(p, d, 24.0);
		Miraculous.setVal(p, RenaRouge.V_DOUBLE, now(p) + RenaRougeConfig.DOUBLE_TICKS);
		Miraculous.setAnim(p, RenaRouge.ANIM_DOUBLE);
		Miraculous.setCooldown(p, RenaRougeConfig.DOUBLE, RenaRougeConfig.DOUBLE_COOLDOWN);
		sound(p, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.0f, 1.2f);
		return d;
	}

	/**
	 * Shift+Z: she plays the Flute ({@link RenaRougeConfig#MIRAGE_PLAY_TICKS}), an orange bubble floats from it and pops
	 * ({@link #miragePop}). False: a Mirage is already running.
	 */
	public static boolean mirage(ServerPlayer p) {
		Session s = session(p);
		long now = now(p);
		if (s.mirageUntil > now || s.mirageBubbleAt != 0) {
			Miraculous.say(p, "message.projecthero.rena_rouge.mirage_running", ChatFormatting.GOLD);
			return false;
		}
		s.mirageBubbleAt = now + RenaRougeConfig.MIRAGE_PLAY_TICKS;
		Miraculous.setVal(p, RenaRouge.V_PLAY, now + RenaRougeConfig.MIRAGE_PLAY_TICKS + 6);
		Miraculous.setAnim(p, RenaRouge.ANIM_MIRAGE);
		Miraculous.setCooldown(p, RenaRougeConfig.MIRAGE, RenaRougeConfig.MIRAGE_COOLDOWN);
		tune(s, now, new float[] { 0.94f, 1.12f, 1.33f, 1.5f, 1.78f, 2.0f, 1.78f, 2.0f }, 3);
		return true;
	}

	/** Where the bubble leaves the Flute: in front of her face, a little to the right. */
	private static Vec3 fluteTip(ServerPlayer p) {
		Vec3 f = flatLook(p);
		return p.getEyePosition().add(f.scale(0.6)).add(rightOf(f).scale(0.55)).add(0, -0.05, 0);
	}

	/**
	 * The bubble pops into the big illusion: {@link RenaRougeConfig#MIRAGE_COPIES} copies of her round her that draw all
	 * the aggro (bosses too), a {@link RenaRougeConfig#MIRAGE_WALL_WIDTH}x{@link RenaRougeConfig#MIRAGE_WALL_HEIGHT} wall of
	 * fake blocks ahead, and every monster within {@link RenaRougeConfig#MIRAGE_RADIUS} confused -- wandering, and
	 * fighting each other -- for {@link RenaRougeConfig#MIRAGE_TICKS}. The Miraculous countdown starts here.
	 */
	public static void miragePop(ServerPlayer p, Vec3 at) {
		if (!Miraculous.transformedAs(p, Kwami.TRIXX)) {
			return;
		}
		Session s = session(p);
		long now = now(p);
		s.mirageBubbleAt = 0L;
		s.mirageUntil = now + RenaRougeConfig.MIRAGE_TICKS;
		Miraculous.startCountdown(p);
		Miraculous.setVal(p, RenaRouge.V_MIRAGE, s.mirageUntil);
		ServerLevel level = p.serverLevel();
		level.sendParticles(RenaRougeCombat.ORANGE_BIG, at.x, at.y, at.z, 60, 1.2, 1.0, 1.2, 0.0);
		level.sendParticles(ParticleTypes.WAX_ON, at.x, at.y, at.z, 40, 2.5, 1.5, 2.5, 1.0);
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.PLAYERS, 2.0f, 0.8f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.2f, 1.1f);
		// the copies, in a ring round her
		List<RenaIllusionEntity> copies = new ArrayList<>();
		Vec3 f = flatLook(p);
		for (int i = 0; i < RenaRougeConfig.MIRAGE_COPIES; i++) {
			double a = Math.atan2(f.z, f.x) + Math.PI * 2 * (i + 0.5) / RenaRougeConfig.MIRAGE_COPIES;
			Vec3 spot = p.position().add(Math.cos(a) * 3.5, 0, Math.sin(a) * 3.5);
			spot = ground(level, spot.add(0, 1.5, 0));
			if (!level.noCollision(p, p.getBoundingBox().move(spot.subtract(p.position())))) {
				spot = p.position();
			}
			float yaw = (float) (Math.toDegrees(Math.atan2(spot.z - p.getZ(), spot.x - p.getX())) - 90.0);
			RenaIllusionEntity c = RenaRougeIllusions.spawn(p, RenaIllusionEntity.MIRAGE, spot, yaw, RenaRougeConfig.MIRAGE_TICKS,
					RenaRougeConfig.MIRAGE_HITS);
			if (c != null) {
				copies.add(c);
			}
		}
		// the wall
		wall(p, RenaRougeConfig.MIRAGE_WALL_WIDTH, RenaRougeConfig.MIRAGE_WALL_HEIGHT, 6.0, RenaRougeConfig.MIRAGE_TICKS);
		// the confusion, and every boss near her fooled onto a copy
		double r = RenaRougeConfig.MIRAGE_RADIUS;
		for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(r),
				m -> m instanceof net.minecraft.world.entity.monster.Enemy && m.distanceToSqr(p) <= r * r && !(m instanceof RenaIllusionEntity))) {
			if (RenaRougeCombat.isBoss(m)) {
				if (!copies.isEmpty() && HeroTargets.canHarm(p, m)) {
					RenaRougeIllusions.taunt(m, copies.get(level.random.nextInt(copies.size())));
				}
				continue;
			}
			if (RenaRougeCombat.isFoolable(p, m)) {
				s.confused.put(m.getId(), s.mirageUntil);
				s.rethinkAt.put(m.getId(), now);
			}
		}
	}

	// ================================================================ V: Fox Camouflage / Shift+V: Fake Wall

	/** Invisible (armour, held items, everything) until she attacks or {@link RenaRougeConfig#CAMO_TICKS} run out; V again ends it. */
	public static void camouflage(ServerPlayer p) {
		if (Miraculous.state(p).val(RenaRouge.V_CAMO) != 0L) {
			RenaRouge.breakCamouflage(p);
			return;
		}
		long until = now(p) + RenaRougeConfig.CAMO_TICKS;
		Miraculous.setVal(p, RenaRouge.V_CAMO, until);
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, RenaRougeConfig.CAMO_TICKS, 0, false, false, true));
		p.setInvisible(true); // at once (the effect only sets the flag on her next effect tick)
		loseHer(p);
		Miraculous.setAnim(p, RenaRouge.ANIM_CAMO);
		RenaRougeCombat.glow(p.serverLevel(), p.position().add(0, 1.0, 0), 16, 0.35);
		sound(p, SoundEvents.FOX_SNIFF, 1.0f, 1.3f);
		sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.7f, 1.6f);
	}

	/** Monsters after her lose her (bosses too: they fall back on whoever else is fighting them). */
	private static void loseHer(ServerPlayer p) {
		for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32.0), m -> m.getTarget() == p)) {
			m.setTarget(null);
			if (m.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET)) {
				m.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
			}
			m.getNavigation().stop();
		}
	}

	/** Shift+V: the {@link RenaRougeConfig#WALL_WIDTH}x{@link RenaRougeConfig#WALL_HEIGHT} illusion wall ahead of her. */
	public static @Nullable FakeWallEntity fakeWall(ServerPlayer p) {
		FakeWallEntity w = wall(p, RenaRougeConfig.WALL_WIDTH, RenaRougeConfig.WALL_HEIGHT, RenaRougeConfig.WALL_DISTANCE, RenaRougeConfig.WALL_TICKS);
		if (w != null) {
			Miraculous.setVal(p, RenaRouge.V_WALL, now(p) + RenaRougeConfig.WALL_TICKS);
			Miraculous.setAnim(p, RenaRouge.ANIM_WALL);
			Miraculous.setCooldown(p, RenaRougeConfig.WALL, RenaRougeConfig.WALL_COOLDOWN);
			sound(p, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0f, 1.3f);
		}
		return w;
	}

	/** A wall {@code w} x {@code h} across her look, {@code dist} blocks ahead, standing on the ground there. */
	private static @Nullable FakeWallEntity wall(ServerPlayer p, int w, int h, double dist, int life) {
		ServerLevel level = p.serverLevel();
		Vec3 f = flatLook(p);
		boolean alongX = Math.abs(f.z) >= Math.abs(f.x);
		Vec3 c = p.position().add(f.scale(dist));
		BlockPos base = BlockPos.containing(ground(level, c.add(0, 1.0, 0)));
		BlockPos under = base.below();
		BlockState look = level.getBlockState(under);
		if (look.isAir() || !look.getFluidState().isEmpty() || !look.isSolidRender(level, under) || look.hasBlockEntity()) {
			look = Blocks.STONE.defaultBlockState();
		}
		FakeWallEntity e = RenaRougeEntityTypes.FAKE_WALL.create(level);
		if (e == null) {
			return null;
		}
		e.setup(p.getUUID(), base, alongX, w, h, look, life);
		level.addFreshEntity(e);
		FakeWallEntity.ACTIVE.add(e);
		AABB b = e.wallBox();
		level.sendParticles(RenaRougeCombat.ORANGE_BIG, b.getCenter().x, b.getCenter().y, b.getCenter().z, 40, b.getXsize() * 0.4, b.getYsize() * 0.4,
				b.getZsize() * 0.4, 0.0);
		level.sendParticles(ParticleTypes.WAX_ON, b.getCenter().x, b.getCenter().y, b.getCenter().z, 20, b.getXsize() * 0.4, b.getYsize() * 0.4,
				b.getZsize() * 0.4, 0.8);
		return e;
	}

	// ================================================================ C: Fox Pounce / Shift+C: Fox Feint

	/** A high leap; the landing stuns the mobs below ({@link #pounceLand}). */
	public static void pounce(ServerPlayer p) {
		Vec3 f = flatLook(p);
		AbilityHelpers.launchSelf(p, new Vec3(f.x * RenaRougeConfig.POUNCE_FORWARD, RenaRougeConfig.POUNCE_UP, f.z * RenaRougeConfig.POUNCE_FORWARD));
		session(p).pounceAt = now(p);
		Miraculous.setVal(p, RenaRouge.V_POUNCE, 1L);
		Miraculous.setAnim(p, RenaRouge.ANIM_POUNCE);
		Miraculous.setCooldown(p, RenaRougeConfig.POUNCE, RenaRougeConfig.POUNCE_COOLDOWN);
		sound(p, SoundEvents.FOX_AGGRO, 1.0f, 1.2f);
	}

	/** The pounce lands: everything she may hit within {@link RenaRougeConfig#POUNCE_RADIUS} is stunned. Returns how many. */
	public static int pounceLand(ServerPlayer p) {
		Session s = peekSession(p);
		if (s != null) {
			s.pounceAt = 0L;
		}
		Miraculous.setVal(p, RenaRouge.V_POUNCE, 0L);
		ServerLevel level = p.serverLevel();
		int n = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), RenaRougeConfig.POUNCE_RADIUS)) {
			if (e instanceof RenaIllusionEntity) {
				continue;
			}
			if (RenaRougeCombat.isBoss(e)) {
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, RenaRougeConfig.POUNCE_STUN_TICKS, 2);
			} else {
				PunisherControl.stun(e, RenaRougeConfig.POUNCE_STUN_TICKS);
			}
			level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() + 0.2, e.getZ(), 8, 0.3, 0.1, 0.3, 0.1);
			n++;
		}
		Vec3 c = p.position();
		for (int i = 0; i < 20; i++) {
			double a = Math.PI * 2 * i / 20;
			level.sendParticles(RenaRougeCombat.ORANGE, c.x + Math.cos(a) * RenaRougeConfig.POUNCE_RADIUS * 0.8, c.y + 0.1,
					c.z + Math.sin(a) * RenaRougeConfig.POUNCE_RADIUS * 0.8, 1, 0, 0, 0, 0);
		}
		level.sendParticles(ParticleTypes.POOF, c.x, c.y + 0.1, c.z, 12, 0.8, 0.05, 0.8, 0.05);
		sound(p, SoundEvents.GENERIC_SMALL_FALL, 1.0f, 0.8f);
		if (n > 0) {
			RenaRouge.breakCamouflage(p);
			sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.8f);
		}
		return n;
	}

	/** Shift+C: for {@link RenaRougeConfig#FEINT_TICKS}, attacks on her land on an illusion that pops in where she stood. */
	public static void feint(ServerPlayer p) {
		Miraculous.setVal(p, RenaRouge.V_FEINT, now(p) + RenaRougeConfig.FEINT_TICKS);
		Miraculous.setAnim(p, RenaRouge.ANIM_FEINT);
		Miraculous.setCooldown(p, RenaRougeConfig.FEINT, RenaRougeConfig.FEINT_COOLDOWN);
		RenaRougeCombat.glow(p.serverLevel(), p.position().add(0, 1.0, 0), 10, 0.4);
		sound(p, SoundEvents.FOX_SCREECH, 0.6f, 1.6f);
	}

	/**
	 * From the damage veto: an attack lands on her while Fox Feint runs -- an illusion pops in where she stood and
	 * dissolves, she slips a step aside. True: the hit is cancelled.
	 */
	public static boolean feintHit(ServerPlayer p, DamageSource source) {
		if (!RenaRouge.is(p) || !RenaRouge.running(p, RenaRouge.V_FEINT)) {
			return false;
		}
		if (source.getEntity() == null && source.getDirectEntity() == null) {
			return false; // only attacks: never a fall, fire, drowning or /kill
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.getEntity() == p) {
			return false;
		}
		ServerLevel level = p.serverLevel();
		Vec3 stood = p.position();
		RenaIllusionEntity puff = RenaRougeIllusions.spawn(p, RenaIllusionEntity.FEINT, stood, p.getYRot(), RenaRougeConfig.DISSOLVE_TICKS + 4, 1);
		if (puff != null) {
			puff.dissolve();
		}
		// a step aside, away from the attacker
		Entity from = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
		Vec3 away = stood.subtract(from.position());
		away = new Vec3(away.x, 0, away.z);
		Vec3 step = away.lengthSqr() < 1.0e-4 ? rightOf(flatLook(p)) : rightOf(away.normalize());
		if (level.random.nextBoolean()) {
			step = step.scale(-1);
		}
		step = step.scale(1.5);
		if (level.noCollision(p, p.getBoundingBox().move(step))) {
			p.teleportTo(level, stood.x + step.x, stood.y, stood.z + step.z, p.getYRot(), p.getXRot());
		}
		level.playSound(null, stood.x, stood.y, stood.z, SoundEvents.FOX_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.5f);
		return true;
	}

	// ================================================================ the tick

	static void tick(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		long now = now(p);
		// camouflage running out, mobs that found her again losing her
		long camo = Miraculous.state(p).val(RenaRouge.V_CAMO);
		if (camo != 0L) {
			if (now >= camo) {
				RenaRouge.breakCamouflage(p);
			} else if (p.tickCount % 10 == 0) {
				loseHer(p);
			}
		}
		long feint = Miraculous.state(p).val(RenaRouge.V_FEINT);
		if (feint != 0L && now >= feint) {
			Miraculous.setVal(p, RenaRouge.V_FEINT, 0L);
		}
		if (s == null) {
			return;
		}
		ServerLevel level = p.serverLevel();
		// the melody
		Float pitch = s.notes.remove(now);
		if (pitch != null) {
			level.playSound(null, p.getX(), p.getEyeY(), p.getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1.0f, pitch);
			Vec3 tip = fluteTip(p);
			level.sendParticles(ParticleTypes.NOTE, tip.x, tip.y + 0.2, tip.z, 0, (pitch - 0.5f) / 1.5f, 0, 0, 1.0);
			level.sendParticles(ParticleTypes.NOTE, tip.x + (level.random.nextDouble() - 0.5), tip.y + 0.5, tip.z + (level.random.nextDouble() - 0.5),
					0, level.random.nextDouble(), 0, 0, 1.0);
		}
		if (!s.notes.isEmpty()) {
			s.notes.keySet().removeIf(t -> t < now - 2);
		}
		// Melody Lure
		if (s.lureUntil > 0) {
			if (now >= s.lureUntil) {
				s.lureUntil = 0;
				s.lured.clear();
				Miraculous.setVal(p, RenaRouge.V_LURE, 0L);
			} else if (now % 10 == 0 && s.lurePoint != null) {
				for (int id : s.lured) {
					if (level.getEntity(id) instanceof Mob m && m.isAlive()) {
						if (m.getTarget() != null) {
							m.setTarget(null);
						}
						m.getNavigation().moveTo(s.lurePoint.x, s.lurePoint.y, s.lurePoint.z, 1.1);
						level.sendParticles(ParticleTypes.NOTE, m.getX(), m.getY() + m.getBbHeight() + 0.4, m.getZ(), 0, level.random.nextDouble(), 0, 0,
								1.0);
					}
				}
			}
		}
		// the Mirage: the bubble leaves the flute
		if (s.mirageBubbleAt > 0 && now >= s.mirageBubbleAt) {
			s.mirageBubbleAt = 0L;
			MirageBubbleEntity b = RenaRougeEntityTypes.BUBBLE.create(level);
			if (b != null) {
				Vec3 tip = fluteTip(p);
				b.launch(p, tip, p.getLookAngle());
				level.addFreshEntity(b);
				// keep the guard up until it pops
				s.mirageBubbleAt = -1L;
			}
			level.playSound(null, p.getX(), p.getEyeY(), p.getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.4f);
		}
		// the Mirage: the confusion
		if (s.mirageUntil > 0) {
			if (now >= s.mirageUntil) {
				s.mirageUntil = 0;
				s.confused.clear();
				s.rethinkAt.clear();
				Miraculous.setVal(p, RenaRouge.V_MIRAGE, 0L);
			} else {
				confuse(p, s, level, now);
			}
		}
		// Fox Pounce: the landing
		if (s.pounceAt > 0) {
			long air = now - s.pounceAt;
			if (air > 3 && (p.onGround() || p.isInWater())) {
				pounceLand(p);
			} else if (air > RenaRougeConfig.POUNCE_MAX_TICKS) {
				s.pounceAt = 0;
				Miraculous.setVal(p, RenaRouge.V_POUNCE, 0L);
			}
		}
	}

	/** The confused monsters: each rethinks every {@link RenaRougeConfig#CONFUSE_RETHINK} ticks -- another monster, an illusion, or nothing. */
	private static void confuse(ServerPlayer p, Session s, ServerLevel level, long now) {
		List<Mob> mobs = new ArrayList<>();
		for (int id : s.confused.keySet()) {
			if (level.getEntity(id) instanceof Mob m && m.isAlive()) {
				mobs.add(m);
			}
		}
		s.confused.keySet().removeIf(id -> !(level.getEntity(id) instanceof Mob m) || !m.isAlive());
		for (Mob m : mobs) {
			LivingEntity t = m.getTarget();
			boolean onUs = t == p || (t instanceof net.minecraft.world.entity.player.Player tp && Squads.areAllies(p, tp));
			Long at = s.rethinkAt.get(m.getId());
			if (!onUs && at != null && now < at) {
				continue;
			}
			s.rethinkAt.put(m.getId(), now + RenaRougeConfig.CONFUSE_RETHINK + level.random.nextInt(10));
			float roll = level.random.nextFloat();
			Mob other = null;
			if (roll < 0.5f && mobs.size() > 1) {
				for (int tries = 0; tries < 4 && other == null; tries++) {
					Mob o = mobs.get(level.random.nextInt(mobs.size()));
					if (o != m && o.distanceToSqr(m) < 16 * 16) {
						other = o;
					}
				}
			}
			if (other != null) {
				RenaRougeIllusions.retarget(m, other);
			} else if (roll < 0.85f && RenaRougeIllusions.nearest(p, m, 24.0) instanceof RenaIllusionEntity ill) {
				RenaRougeIllusions.retarget(m, ill);
			} else {
				m.setTarget(null);
				Vec3 wander = m.position().add(level.random.nextInt(13) - 6, 0, level.random.nextInt(13) - 6);
				m.getNavigation().moveTo(wander.x, wander.y, wander.z, 1.0);
			}
			Vec3 head = m.position().add(0, m.getBbHeight() + 0.3, 0);
			for (int i = 0; i < 3; i++) {
				double a = (now * 0.4) + i * Math.PI * 2 / 3;
				level.sendParticles(RenaRougeCombat.ORANGE, head.x + Mth.cos((float) a) * 0.35, head.y, head.z + Mth.sin((float) a) * 0.35, 1, 0, 0, 0, 0);
			}
		}
	}

	/** Test / debug hook: where the running Melody Lure pulls to (null when none runs). */
	public static @Nullable Vec3 lurePoint(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null || s.lureUntil <= now(p) ? null : s.lurePoint;
	}

	/** Test / debug hook: is this mob currently confused by her Mirage? */
	public static boolean isConfused(ServerPlayer p, Mob m) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.confused.containsKey(m.getId()) && s.mirageUntil > now(p);
	}

	/** Detransformation / logout: every running move of hers ends, everything she conjured goes. */
	static void endAll(ServerPlayer p) {
		RenaRouge.breakCamouflage(p);
		RenaRougeIllusions.dissolveAll(p);
		ServerLevel level = p.serverLevel();
		AABB all = p.getBoundingBox().inflate(256.0);
		for (FluteBoomerangEntity f : level.getEntitiesOfClass(FluteBoomerangEntity.class, all, f -> f.ownerEntityId() == p.getId())) {
			f.discard();
		}
		for (FakeWallEntity w : level.getEntitiesOfClass(FakeWallEntity.class, all, w -> p.getUUID().equals(w.ownerId()))) {
			w.discard();
		}
		for (MirageBubbleEntity b : level.getEntitiesOfClass(MirageBubbleEntity.class, all, b -> p.getUUID().equals(b.ownerId()))) {
			b.discard();
		}
		SESSIONS.remove(p.getUUID());
	}
}
