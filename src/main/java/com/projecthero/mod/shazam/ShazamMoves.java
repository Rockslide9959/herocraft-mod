package com.projecthero.mod.shazam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.shazam.data.ShazamState;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Shazam: every move (keys in {@link ShazamAbilityManager}, numbers in {@link ShazamConfig}). Damage goes through
 * {@link Shazam#hit} (a family member's halved there); areas never touch him or a squadmate ({@link Shazam#areaVictim}). Lightning
 * is drawn with real, visual-only bolts ({@link Shazam#bolt}); fire and broken glass only with mob griefing on.
 *
 * <p>Per player there is a small server session (the creature held up by Atlas Lift, the creatures flying from a Hercules Punch or a
 * throw, fall safety after a dash or tackle, Eternity Strike's beats). It holds entity ids, never entities, and is dropped by
 * {@code ServerStateReset} through {@link #clearSessionState}.
 */
public final class ShazamMoves {
	/** A creature in flight from his punch or throw: it takes {@code damage} more if it hits a wall (or, thrown, the ground). */
	private record Flight(long until, float damage, boolean groundToo, long since) {
	}

	private static final class Session {
		int lifted = -1;
		long liftUntil;
		final Map<Integer, Flight> flying = new HashMap<>();
		long fallSafeUntil;
		boolean eternityBolted;
		boolean eternitySlamming;
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private ShazamMoves() {
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

	private static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	/** No fall damage just after a dash, a tackle or the Eternity slam. */
	public static boolean fallSafe(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.fallSafeUntil > now(p);
	}

	/** The creature Atlas Lift is holding up (null when none). For the tests and the HUD. */
	public static Entity lifted(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null || s.lifted < 0 ? null : p.level().getEntity(s.lifted);
	}

	// ---------------------------------------------------------------- dispatch

	/** Fires move {@code id}. True if it went off (cooldown and cost taken). */
	public static boolean use(ServerPlayer p, String id) {
		if (!Shazam.transformed(p)) {
			return false;
		}
		if (ShazamAbilityManager.championOnly(id) && Shazam.isFamily(p)) {
			Shazam.say(p, "message.projecthero.shazam.champion_only", ChatFormatting.GRAY);
			return false;
		}
		// Atlas Lift's second press throws, whatever the cooldown says
		if (ShazamAbilityManager.ATLAS_LIFT.equals(id) && lifted(p) != null) {
			return hurl(p);
		}
		if (Shazam.cooldownRemaining(p, id) > 0) {
			return false;
		}
		return switch (id) {
			case ShazamAbilityManager.LIGHTNING_HAND -> lightningHand(p);
			case ShazamAbilityManager.CALL_LIGHTNING -> callLightning(p);
			case ShazamAbilityManager.HERCULES_PUNCH -> herculesPunch(p);
			case ShazamAbilityManager.ATLAS_LIFT -> atlasLift(p);
			case ShazamAbilityManager.ZEUS_STORM -> zeusStorm(p);
			case ShazamAbilityManager.ETERNITY_STRIKE -> eternityStrike(p);
			case ShazamAbilityManager.MERCURY_DASH -> mercuryDash(p);
			case ShazamAbilityManager.THUNDER_TACKLE -> thunderTackle(p);
			case ShazamAbilityManager.THUNDERCLAP -> thunderclap(p);
			case ShazamAbilityManager.ACHILLES_STAND -> achillesStand(p);
			case ShazamAbilityManager.WISDOM -> wisdom(p);
			case ShazamAbilityManager.SHARE_POWER -> ShazamFamily.shareAimed(p);
			default -> false;
		};
	}

	// ---------------------------------------------------------------- aiming

	/** Where his look meets a block within {@code range} (or the end of the range). */
	static Vec3 lookEnd(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getViewVector(1f).scale(range));
		BlockHitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	/** The first creature along his look within {@code range} (blocks in the way stop it), boxes grown by {@code pad}. */
	static LivingEntity aimed(ServerPlayer p, double range, double pad, Predicate<LivingEntity> filter) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = lookEnd(p, range);
		AABB sweep = new AABB(eye, end).inflate(pad + 1.0);
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, sweep, e -> e != p && e.isAlive() && !e.isSpectator() && filter.test(e))) {
			AABB box = e.getBoundingBox().inflate(pad);
			Vec3 at = box.contains(eye) ? eye : box.clip(eye, end).orElse(null);
			if (at == null) {
				continue;
			}
			double d = eye.distanceToSqr(at);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** Something his moves may hit on purpose (not a squadmate, not protected). */
	private static boolean foe(ServerPlayer p, LivingEntity e) {
		return Shazam.areaVictim(p, e);
	}

	// ---------------------------------------------------------------- effects

	/** A jagged lightning arc from {@code a} to {@code b}. */
	static void arc(ServerLevel level, Vec3 a, Vec3 b) {
		Vec3 d = b.subtract(a);
		int steps = Math.max(4, (int) (d.length() * 2.5));
		net.minecraft.util.RandomSource r = level.random;
		for (int i = 0; i <= steps; i++) {
			double t = i / (double) steps;
			double j = (i == 0 || i == steps) ? 0 : 0.25;
			Vec3 at = a.add(d.scale(t)).add((r.nextDouble() - 0.5) * j, (r.nextDouble() - 0.5) * j, (r.nextDouble() - 0.5) * j);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			if (i % 2 == 0) {
				level.sendParticles(Shazam.dust(Shazam.BOLT_WHITE, 0.8f), at.x, at.y, at.z, 1, 0, 0, 0, 0);
			}
		}
	}

	private static Vec3 hand(ServerPlayer p) {
		Vec3 look = p.getViewVector(1f);
		float yaw = p.getYRot() * ((float) Math.PI / 180f);
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		return p.getEyePosition().add(0, -0.4, 0).add(look.scale(0.5)).add(right.scale(0.35));
	}

	private static void crackle(ServerPlayer p, float volume, float pitch) {
		Shazam.sound(p, ShazamSounds.CRACKLE, volume, pitch);
	}

	private static boolean griefing(ServerLevel level) {
		return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
	}

	// ================================================================ R: Lightning Hand

	static boolean lightningHand(ServerPlayer p) {
		if (!Shazam.spend(p, ShazamConfig.HAND_COST)) {
			return false;
		}
		ServerLevel level = p.serverLevel();
		Vec3 from = hand(p);
		LivingEntity first = aimed(p, ShazamConfig.HAND_RANGE, 0.4, e -> foe(p, e));
		if (first == null) {
			arc(level, from, lookEnd(p, ShazamConfig.HAND_RANGE));
		} else {
			List<LivingEntity> struck = new ArrayList<>();
			Vec3 last = from;
			LivingEntity next = first;
			while (next != null && struck.size() <= ShazamConfig.HAND_CHAINS) {
				Vec3 at = next.position().add(0, next.getBbHeight() * 0.5, 0);
				arc(level, last, at);
				Shazam.hit(p, next, ShazamConfig.HAND_DAMAGE, true);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 8, 0.3, 0.4, 0.3, 0.1);
				struck.add(next);
				last = at;
				next = nearestChain(p, next, struck);
			}
		}
		crackle(p, 0.6f, 1.0f);
		Shazam.startCooldown(p, ShazamAbilityManager.LIGHTNING_HAND, ShazamConfig.HAND_COOLDOWN);
		return true;
	}

	/** The next creature the chain jumps to: the nearest threat within 6 blocks of {@code from} not struck yet. */
	private static LivingEntity nearestChain(ServerPlayer p, LivingEntity from, List<LivingEntity> struck) {
		double r = ShazamConfig.HAND_CHAIN_RANGE;
		LivingEntity best = null;
		double bestD = r * r;
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(r),
				e -> !struck.contains(e) && foe(p, e) && HeroTargets.isHostile(p, e))) {
			double d = e.distanceToSqr(from);
			if (d <= bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	// ================================================================ Shift+R: Call the Lightning

	static boolean callLightning(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		LivingEntity aimedAt = aimed(p, ShazamConfig.CALL_RANGE, 0.5, e -> foe(p, e));
		Vec3 at = aimedAt != null ? aimedAt.position() : lookEnd(p, ShazamConfig.CALL_RANGE);
		if (!Shazam.spend(p, ShazamConfig.CALL_COST)) {
			return false;
		}
		strike(p, at, ShazamConfig.CALL_DAMAGE, ShazamConfig.CALL_RADIUS);
		if (griefing(level)) {
			BlockPos pos = BlockPos.containing(at);
			BlockState fire = Blocks.FIRE.defaultBlockState();
			if (level.getBlockState(pos).isAir() && fire.canSurvive(level, pos)) {
				level.setBlockAndUpdate(pos, fire);
			}
		}
		Shazam.startCooldown(p, ShazamAbilityManager.CALL_LIGHTNING, ShazamConfig.CALL_COOLDOWN);
		return true;
	}

	/** A bolt at {@code at}: {@code damage} to every foe within {@code radius}. Returns how many it hit. */
	static int strike(ServerPlayer p, Vec3 at, float damage, double radius) {
		ServerLevel level = p.serverLevel();
		Shazam.bolt(level, at);
		int n = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, radius + 2.0, radius),
				e -> foe(p, e) && e.position().distanceToSqr(at) <= (radius + 0.5) * (radius + 0.5) + 4.0)) {
			if (Shazam.hit(p, e, damage, true)) {
				n++;
			}
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.5, at.z, 20, 0.6, 0.5, 0.6, 0.2);
		return n;
	}

	// ================================================================ G: Hercules Punch

	static boolean herculesPunch(ServerPlayer p) {
		LivingEntity target = aimed(p, ShazamConfig.PUNCH_REACH, 0.3, e -> foe(p, e));
		if (target == null) {
			Shazam.say(p, "message.projecthero.shazam.no_target", ChatFormatting.GRAY);
			return false;
		}
		if (!Shazam.spend(p, ShazamConfig.PUNCH_COST)) {
			return false;
		}
		Shazam.hit(p, target, ShazamConfig.PUNCH_DAMAGE, false);
		Vec3 look = p.getViewVector(1f);
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		launch(p, target, flat.scale(ShazamConfig.PUNCH_LAUNCH).add(0, ShazamConfig.PUNCH_LIFT, 0),
				ShazamConfig.WALL_DAMAGE, false);
		ServerLevel level = p.serverLevel();
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(Shazam.dust(Shazam.GOLD, 1.2f), c.x, c.y, c.z, 12, 0.3, 0.3, 0.3, 0.0);
		Shazam.sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.6f);
		Shazam.sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.35f, 1.4f);
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		Shazam.startCooldown(p, ShazamAbilityManager.HERCULES_PUNCH, ShazamConfig.PUNCH_COOLDOWN);
		return true;
	}

	/** Sends {@code target} flying at {@code velocity}, tracked so a wall (or, thrown, the ground) hurts it. */
	static void launch(ServerPlayer p, LivingEntity target, Vec3 velocity, float impactDamage, boolean groundToo) {
		if (!target.isAlive()) {
			return;
		}
		target.setDeltaMovement(velocity);
		target.hurtMarked = true;
		target.hasImpulse = true;
		long now = now(p);
		session(p).flying.put(target.getId(), new Flight(now + ShazamConfig.LAUNCH_TICKS, impactDamage, groundToo, now));
	}

	/** The creature flying from {@code p}'s punch / throw (for the tests). */
	public static boolean inFlight(ServerPlayer p, Entity e) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.flying.containsKey(e.getId());
	}

	// ================================================================ Shift+G: Atlas Lift

	static boolean atlasLift(ServerPlayer p) {
		LivingEntity target = aimed(p, ShazamConfig.LIFT_REACH, 0.5, e -> foe(p, e) && !(e instanceof EnderDragon));
		if (target == null) {
			Shazam.say(p, "message.projecthero.shazam.no_target", ChatFormatting.GRAY);
			return false;
		}
		if (!Shazam.spend(p, ShazamConfig.LIFT_COST)) {
			return false;
		}
		Session s = session(p);
		s.lifted = target.getId();
		s.liftUntil = now(p) + ShazamConfig.LIFT_HOLD_TICKS;
		holdUp(p, target);
		Shazam.sound(p, SoundEvents.IRON_GOLEM_ATTACK, 0.8f, 0.8f);
		return true;
	}

	private static void holdUp(ServerPlayer p, Entity e) {
		Vec3 look = p.getViewVector(1f);
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize().scale(0.3);
		Vec3 at = p.position().add(flat).add(0, p.getBbHeight() + 0.2, 0);
		e.teleportTo(at.x, at.y, at.z);
		e.setDeltaMovement(Vec3.ZERO);
		e.resetFallDistance();
		e.hurtMarked = true;
	}

	/** Throws the held creature where he looks. */
	static boolean hurl(ServerPlayer p) {
		Session s = session(p);
		Entity e = lifted(p);
		s.lifted = -1;
		if (!(e instanceof LivingEntity le) || !le.isAlive()) {
			return false;
		}
		launch(p, le, p.getViewVector(1f).scale(ShazamConfig.LIFT_THROW_SPEED).add(0, 0.2, 0), ShazamConfig.LIFT_IMPACT_DAMAGE, true);
		Shazam.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.6f);
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		Shazam.startCooldown(p, ShazamAbilityManager.ATLAS_LIFT, ShazamConfig.LIFT_COOLDOWN);
		return true;
	}

	// ================================================================ Z: Zeus Storm

	static boolean zeusStorm(ServerPlayer p) {
		if (!Shazam.spend(p, ShazamConfig.STORM_COST)) {
			return false;
		}
		ShazamState n = Shazam.state(p).copy();
		n.stormUntil = now(p) + ShazamConfig.STORM_TICKS;
		Shazam.save(p, n);
		Shazam.sound(p, ShazamSounds.WORD, 0.4f, 1.3f);
		Shazam.startCooldown(p, ShazamAbilityManager.ZEUS_STORM, ShazamConfig.STORM_COOLDOWN);
		return true;
	}

	/** One of Zeus Storm's half-second bolts: a random threat within 16 blocks. Returns who was struck (null: none near). */
	public static LivingEntity stormBolt(ServerPlayer p) {
		double r = ShazamConfig.STORM_RANGE;
		List<LivingEntity> foes = p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
				e -> foe(p, e) && HeroTargets.isHostile(p, e) && e.distanceToSqr(p) <= r * r);
		if (foes.isEmpty()) {
			return null;
		}
		LivingEntity e = foes.get(p.getRandom().nextInt(foes.size()));
		Shazam.bolt(p.serverLevel(), e.position());
		Shazam.hit(p, e, ShazamConfig.STORM_DAMAGE, true);
		return e;
	}

	// ================================================================ Shift+Z: Eternity Strike

	static boolean eternityStrike(ServerPlayer p) {
		if (Shazam.eternityAge(p) >= 0) {
			return false;
		}
		if (!Shazam.hasLightning(p, ShazamConfig.ETERNITY_COST)) {
			Shazam.say(p, "message.projecthero.shazam.needs_full", ChatFormatting.GRAY);
			return false;
		}
		Shazam.spend(p, ShazamConfig.ETERNITY_COST);
		ShazamState n = Shazam.state(p).copy();
		n.eternityStart = now(p);
		Shazam.save(p, n);
		Session s = session(p);
		s.eternityBolted = false;
		s.eternitySlamming = false;
		Shazam.sound(p, ShazamSounds.WORD, 0.5f, 0.6f);
		Shazam.startCooldown(p, ShazamAbilityManager.ETERNITY_STRIKE, ShazamConfig.ETERNITY_COOLDOWN);
		return true;
	}

	private static void eternityTick(ServerPlayer p, int age) {
		Session s = session(p);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		if (age < ShazamConfig.ETERNITY_RISE_TICKS) {
			p.setDeltaMovement(p.getDeltaMovement().x * 0.5, 0.55, p.getDeltaMovement().z * 0.5);
			p.hurtMarked = true;
			p.resetFallDistance();
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 1.0, c.z, 4, 0.4, 0.8, 0.4, 0.05);
			return;
		}
		if (!s.eternityBolted) {
			s.eternityBolted = true;
			// the huge bolt hits him: three at once, harmless to him
			for (int i = 0; i < 3; i++) {
				Shazam.bolt(level, c.add((i - 1) * 0.3, 0, (i - 1) * 0.3));
			}
			level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1, c.z, 2, 0, 0, 0, 0);
			level.sendParticles(Shazam.dust(Shazam.GOLD, 2.0f), c.x, c.y + 1, c.z, 40, 0.6, 1.2, 0.6, 0.0);
			p.setDeltaMovement(0, 0, 0);
			p.hurtMarked = true;
			return;
		}
		if (!s.eternitySlamming && age >= ShazamConfig.ETERNITY_RISE_TICKS + 6) {
			s.eternitySlamming = true;
			ShazamFlight.stop(p);
		}
		if (s.eternitySlamming) {
			p.setDeltaMovement(0, -3.0, 0);
			p.hurtMarked = true;
			p.resetFallDistance();
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 1.0, c.z, 6, 0.3, 0.8, 0.3, 0.05);
			if (p.onGround() || age >= ShazamConfig.ETERNITY_MAX_TICKS) {
				eternityBlast(p);
			}
		}
	}

	/** The slam lands: 50 to every foe within 10 blocks, the bar emptied. Returns how many it hit. */
	public static int eternityBlast(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		double r = ShazamConfig.ETERNITY_RADIUS;
		int n = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
				e -> foe(p, e) && e.distanceToSqr(p) <= r * r)) {
			if (Shazam.hit(p, e, ShazamConfig.ETERNITY_DAMAGE, true)) {
				n++;
			}
			Vec3 away = e.position().subtract(c);
			away = away.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : new Vec3(away.x, 0, away.z).normalize();
			e.setDeltaMovement(e.getDeltaMovement().add(away.scale(1.2)).add(0, 0.6, 0));
			e.hurtMarked = true;
		}
		for (int i = 0; i < 4; i++) {
			double a = i * Math.PI / 2;
			Shazam.bolt(level, c.add(Math.cos(a) * 5, 0, Math.sin(a) * 5));
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.5, c.z, 80, 4.0, 0.5, 4.0, 0.3);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 0.7f);
		ShazamState st = Shazam.state(p).copy();
		st.eternityStart = 0L;
		st.lightning = 0f;
		Shazam.save(p, st);
		Session s = session(p);
		s.eternityBolted = false;
		s.eternitySlamming = false;
		s.fallSafeUntil = now(p) + 40;
		p.resetFallDistance();
		return n;
	}

	// ================================================================ X: Mercury Dash

	static boolean mercuryDash(ServerPlayer p) {
		Vec3 from = p.position();
		Vec3 to = dashTo(p, ShazamConfig.DASH_DISTANCE);
		if (to.distanceToSqr(from) < 1.0) {
			return false;
		}
		p.teleportTo(to.x, to.y, to.z);
		Vec3 dir = to.subtract(from).normalize();
		p.setDeltaMovement(dir.scale(0.4));
		p.hurtMarked = true;
		blur(p.serverLevel(), from, to, p.getBbHeight());
		crackle(p, 0.5f, 1.4f);
		session(p).fallSafeUntil = now(p) + 30;
		p.resetFallDistance();
		Shazam.startCooldown(p, ShazamAbilityManager.MERCURY_DASH, ShazamConfig.DASH_COOLDOWN);
		return true;
	}

	/** The farthest point along his look, up to {@code distance}, his body fits at (blocks stop him). */
	static Vec3 dashTo(ServerPlayer p, double distance) {
		Vec3 look = p.getViewVector(1f);
		Vec3 from = p.position();
		AABB box = p.getBoundingBox();
		Vec3 best = from;
		for (double d = 0.25; d <= distance + 1.0e-6; d += 0.25) {
			Vec3 off = look.scale(d);
			if (!p.level().noCollision(p, box.move(off))) {
				break;
			}
			best = from.add(off);
		}
		return best;
	}

	/** The red blur and its crackle trail from {@code a} to {@code b}. */
	static void blur(ServerLevel level, Vec3 a, Vec3 b, double height) {
		Vec3 d = b.subtract(a);
		int steps = Math.max(2, (int) (d.length() * 3));
		for (int i = 0; i <= steps; i++) {
			Vec3 at = a.add(d.scale(i / (double) steps)).add(0, height * 0.5, 0);
			level.sendParticles(Shazam.dust(Shazam.RED, 1.6f), at.x, at.y, at.z, 2, 0.15, height * 0.3, 0.15, 0.0);
			if (i % 3 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1, 0.2, 0.3, 0.2, 0.02);
			}
		}
	}

	// ================================================================ Shift+X: Thunder Tackle

	static boolean thunderTackle(ServerPlayer p) {
		LivingEntity target = aimed(p, ShazamConfig.TACKLE_RANGE, 0.8, e -> foe(p, e) && !(e instanceof EnderDragon));
		ServerLevel level = p.serverLevel();
		Vec3 from = p.position();
		if (target == null) {
			// nobody in line: a long flying dash (free)
			Vec3 to = dashTo(p, ShazamConfig.TACKLE_RANGE);
			if (to.distanceToSqr(from) < 1.0) {
				return false;
			}
			p.teleportTo(to.x, to.y, to.z);
			blur(level, from, to, p.getBbHeight());
			session(p).fallSafeUntil = now(p) + 40;
			Shazam.startCooldown(p, ShazamAbilityManager.THUNDER_TACKLE, ShazamConfig.DASH_COOLDOWN);
			return true;
		}
		if (!Shazam.spend(p, ShazamConfig.TACKLE_COST)) {
			return false;
		}
		// carry it down into the ground
		Vec3 top = target.position();
		BlockHitResult down = level.clip(new ClipContext(top.add(0, 0.1, 0), top.add(0, -32, 0), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, target));
		Vec3 ground = down.getType() == HitResult.Type.MISS ? top : down.getLocation();
		target.teleportTo(ground.x, ground.y, ground.z);
		target.setDeltaMovement(0, -0.5, 0);
		target.hurtMarked = true;
		Vec3 back = from.subtract(ground);
		back = new Vec3(back.x, 0, back.z);
		back = back.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : back.normalize();
		Vec3 land = ground.add(back.scale(target.getBbWidth() * 0.5 + 0.6));
		if (level.noCollision(p, p.getBoundingBox().move(land.subtract(p.position())))) {
			p.teleportTo(land.x, land.y, land.z);
		}
		blur(level, from, p.position(), p.getBbHeight());
		Shazam.hit(p, target, ShazamConfig.TACKLE_DAMAGE, true);
		Shazam.bolt(level, ground);
		level.sendParticles(ParticleTypes.EXPLOSION, ground.x, ground.y + 0.3, ground.z, 2, 0.4, 0.1, 0.4, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, ground.x, ground.y + 0.5, ground.z, 30, 1.0, 0.4, 1.0, 0.2);
		Shazam.sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.4f, 1.2f);
		session(p).fallSafeUntil = now(p) + 40;
		p.resetFallDistance();
		Shazam.startCooldown(p, ShazamAbilityManager.THUNDER_TACKLE, ShazamConfig.TACKLE_COOLDOWN);
		return true;
	}

	// ================================================================ C: Thunderclap

	/** Inside the clap's cone from his eyes. */
	static boolean inCone(ServerPlayer p, Vec3 point) {
		Vec3 eye = p.getEyePosition();
		Vec3 to = point.subtract(eye);
		double len = to.length();
		if (len > ShazamConfig.CLAP_RANGE || len < 1.0e-4) {
			return len < 1.0e-4;
		}
		double cos = to.scale(1.0 / len).dot(p.getViewVector(1f));
		return cos >= Math.cos(Math.toRadians(ShazamConfig.CLAP_HALF_ANGLE));
	}

	static boolean thunderclap(ServerPlayer p) {
		if (!Shazam.spend(p, ShazamConfig.CLAP_COST)) {
			return false;
		}
		ServerLevel level = p.serverLevel();
		double r = ShazamConfig.CLAP_RANGE;
		Vec3 look = p.getViewVector(1f);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
				e -> foe(p, e) && (inCone(p, e.position().add(0, e.getBbHeight() * 0.5, 0)) || inCone(p, e.getEyePosition())))) {
			Shazam.hit(p, e, ShazamConfig.CLAP_DAMAGE, false);
			e.setDeltaMovement(e.getDeltaMovement().add(look.x * ShazamConfig.CLAP_KNOCKBACK, 0.35, look.z * ShazamConfig.CLAP_KNOCKBACK));
			e.hurtMarked = true;
		}
		if (griefing(level)) {
			int broken = 0;
			BlockPos c = BlockPos.containing(p.getEyePosition());
			int ir = (int) Math.ceil(r);
			outer:
			for (BlockPos pos : BlockPos.betweenClosed(c.offset(-ir, -ir, -ir), c.offset(ir, ir, ir))) {
				BlockState st = level.getBlockState(pos);
				if ((st.is(ConventionalBlockTags.GLASS_BLOCKS) || st.is(ConventionalBlockTags.GLASS_PANES)) && inCone(p, Vec3.atCenterOf(pos))) {
					level.destroyBlock(pos.immutable(), false, p);
					if (++broken >= ShazamConfig.CLAP_GLASS_MAX) {
						break outer;
					}
				}
			}
		}
		// the shockwave: rings of cloud and sparks out along the cone
		Vec3 eye = p.getEyePosition();
		for (int i = 1; i <= 8; i++) {
			Vec3 at = eye.add(look.scale(i));
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 3 + i, i * 0.15, i * 0.15, i * 0.15, 0.02);
			if (i % 2 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 3, i * 0.15, i * 0.15, i * 0.15, 0.05);
			}
		}
		level.sendParticles(ParticleTypes.SONIC_BOOM, eye.x + look.x, eye.y + look.y, eye.z + look.z, 1, 0, 0, 0, 0);
		Shazam.sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.4f, 1.6f);
		Shazam.sound(p, ShazamSounds.WORD, 0.3f, 1.6f);
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		Shazam.startCooldown(p, ShazamAbilityManager.THUNDERCLAP, ShazamConfig.CLAP_COOLDOWN);
		return true;
	}

	// ================================================================ Shift+C: Achilles Stand

	static boolean achillesStand(ServerPlayer p) {
		if (!Shazam.spend(p, ShazamConfig.STAND_COST)) {
			return false;
		}
		ShazamState n = Shazam.state(p).copy();
		n.standUntil = now(p) + ShazamConfig.STAND_TICKS;
		Shazam.save(p, n);
		taunt(p);
		Vec3 c = p.position();
		p.serverLevel().sendParticles(Shazam.dust(Shazam.GOLD, 1.5f), c.x, c.y + 1, c.z, 30, 0.8, 1.0, 0.8, 0.0);
		Shazam.sound(p, SoundEvents.SHIELD_BLOCK, 1.0f, 0.7f);
		Shazam.sound(p, SoundEvents.RAVAGER_ROAR, 0.4f, 1.5f);
		Shazam.startCooldown(p, ShazamAbilityManager.ACHILLES_STAND, ShazamConfig.STAND_COOLDOWN);
		return true;
	}

	/** Every hostile within 16 blocks turns on him (bosses through their threat table). Returns how many. */
	public static int taunt(ServerPlayer p) {
		double r = ShazamConfig.STAND_RANGE;
		int n = 0;
		for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(r),
				m -> m.isAlive() && m.distanceToSqr(p) <= r * r && HeroTargets.canHarm(p, m) && (m instanceof Enemy || HeroTargets.isHostile(p, m)))) {
			if (com.projecthero.mod.miraculous.renarouge.RenaRougeCombat.isBoss(m)) {
				com.projecthero.mod.miraculous.renarouge.RenaRougeIllusions.taunt(m, p); // the shared boss threat table
			} else {
				m.setTarget(p);
				if (m.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
					m.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, p);
				}
			}
			n++;
		}
		return n;
	}

	// ================================================================ V: Wisdom of Solomon

	static boolean wisdom(ServerPlayer p) {
		ShazamState n = Shazam.state(p).copy();
		n.wisdomUntil = now(p) + ShazamConfig.WISDOM_TICKS;
		Shazam.save(p, n);
		Shazam.sound(p, SoundEvents.ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
		Vec3 c = p.getEyePosition();
		p.serverLevel().sendParticles(ParticleTypes.ENCHANT, c.x, c.y, c.z, 30, 0.5, 0.5, 0.5, 0.5);
		Shazam.startCooldown(p, ShazamAbilityManager.WISDOM, ShazamConfig.WISDOM_COOLDOWN);
		return true;
	}

	// ================================================================ per tick (transformed)

	static void tick(ServerPlayer p) {
		long now = now(p);
		ShazamState st = Shazam.state(p);
		Session s = SESSIONS.get(p.getUUID());
		// Zeus Storm: a bolt every half second
		if (st.stormUntil > now && (st.stormUntil - now) % ShazamConfig.STORM_EVERY == 0) {
			stormBolt(p);
		}
		// Achilles Stand: keep the hostiles on him
		if (st.standUntil > now && (st.standUntil - now) % 20 == 0) {
			taunt(p);
		}
		int age = Shazam.eternityAge(p);
		if (age >= 0) {
			eternityTick(p, age);
		}
		if (s == null) {
			return;
		}
		// Atlas Lift: held overhead, thrown when time is up
		if (s.lifted >= 0) {
			Entity e = p.level().getEntity(s.lifted);
			if (e == null || !e.isAlive() || e.distanceToSqr(p) > 64.0) {
				s.lifted = -1;
				Shazam.startCooldown(p, ShazamAbilityManager.ATLAS_LIFT, ShazamConfig.LIFT_COOLDOWN);
			} else if (now >= s.liftUntil) {
				hurl(p);
			} else {
				holdUp(p, e);
			}
		}
		// creatures flying from a punch or a throw
		if (!s.flying.isEmpty()) {
			s.flying.entrySet().removeIf(entry -> flightTick(p, entry.getKey(), entry.getValue(), now));
		}
	}

	/** True when the flight is over. */
	private static boolean flightTick(ServerPlayer p, int id, Flight f, long now) {
		Entity e = p.level().getEntity(id);
		if (!(e instanceof LivingEntity le) || !le.isAlive() || now > f.until()) {
			return true;
		}
		if (now - f.since() < 2) {
			return false;
		}
		boolean wall = le.horizontalCollision;
		boolean ground = f.groundToo() && le.onGround();
		if (wall || ground) {
			Shazam.hit(p, le, f.damage(), false);
			ServerLevel level = p.serverLevel();
			level.sendParticles(ParticleTypes.EXPLOSION, le.getX(), le.getY() + 0.5, le.getZ(), 1, 0, 0, 0, 0);
			level.playSound(null, le.getX(), le.getY(), le.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.3f, 1.5f);
			return true;
		}
		return !f.groundToo() && le.onGround() && now - f.since() > 6;
	}

	/** For the tests: a thrown / punched creature's wall hit, as if it had just collided. */
	public static boolean impact(ServerPlayer p, LivingEntity e) {
		Session s = SESSIONS.get(p.getUUID());
		Flight f = s == null ? null : s.flying.remove(e.getId());
		if (f == null) {
			return false;
		}
		return Shazam.hit(p, e, f.damage(), false);
	}

	/** Never targets a squadmate (Share the Power aims at them instead). */
	static boolean squadmate(ServerPlayer p, Entity e) {
		return e instanceof ServerPlayer other && other != p && Squads.areAllies(p, other);
	}
}
