package com.projecthero.mod.undead;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadCloneEntity;
import com.projecthero.mod.undead.entity.UndeadPartEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: Undead's X launches. The key (tap / Shift / hold) is read in {@link UndeadAbilityManager}.
 * <ul>
 *   <li><b>Eject Feet</b> (tap X; user: "flung with the force to make them travel 10 blocks ... still lose momentum like how
 *       normal Minecraft works ... like the other dashes in the mod"): the feet blow off with half the calves ({@link UndeadEject})
 *       and the blast throws him along the look in ONE impulse ({@link AbilityHelpers#ballisticLaunch}: a flat launch lands about
 *       {@link UndeadEjectConfig#EJECT_FEET_DISTANCE} blocks away), vanilla drag and gravity after it. No fall damage for the
 *       landing; the feet grow back once he is down.</li>
 *   <li><b>Combustion Punch</b> (Shift+X): locks onto the creature under (or nearest) the crosshair within
 *       {@link UndeadEjectConfig#COMBUSTION_RANGE}, blows off everything but his head, the top third of the torso and one arm
 *       (the rest left standing where he was) and rockets at it; the punch hits for {@link UndeadEjectConfig#COMBUSTION_DAMAGE}
 *       and knocks it back about {@link UndeadEjectConfig#COMBUSTION_KNOCKBACK} blocks. Then the body regrows.</li>
 *   <li>The old Head Launch survives only as the Regen Menu's "Eject Head" ({@link #headLaunch}).</li>
 * </ul>
 * Every map here is dropped by {@link #clearSessionState} and {@link #forget}.
 */
public final class UndeadLaunch {
	public static final String EJECT_FEET = "eject_feet";
	public static final String COMBUSTION = "combustion_punch";
	public static final String HEAD_LAUNCH = "head_launch";

	/** A Combustion Punch in the air: the target, when, where last tick, ticks barely moving, and the punch once thrown. */
	private static final class Run {
		final int target;
		final long start;
		Vec3 last;
		int still;
		long punchedAt = -1L;
		/** The Regen Menu's Eject Head: a straight dash with no target. */
		final boolean head;
		final Vec3 dir;
		final Vec3 from;

		Run(int target, long start, Vec3 at, boolean head, Vec3 dir) {
			this.from = at;
			this.target = target;
			this.start = start;
			this.last = at;
			this.head = head;
			this.dir = dir;
		}
	}

	private static final Map<UUID, Run> RUNS = new ConcurrentHashMap<>();
	/** No fall damage until this game time (pushed on while he is still in the air after an Eject Feet). */
	private static final Map<UUID, Long> SAFE_UNTIL = new ConcurrentHashMap<>();
	/** Eject Feet in the air: when it was thrown (the feet grow back once he lands). */
	private static final Map<UUID, Long> FEET_AIR = new ConcurrentHashMap<>();

	private UndeadLaunch() {
	}

	static void clearSessionState() {
		RUNS.clear();
		SAFE_UNTIL.clear();
		FEET_AIR.clear();
	}

	static void forget(UUID id) {
		RUNS.remove(id);
		SAFE_UNTIL.remove(id);
		FEET_AIR.remove(id);
	}

	/** In a launch (or just landed from one): falls do nothing. From {@link UndeadDamage#allowDamage}. */
	public static boolean fallImmune(ServerPlayer p) {
		if (RUNS.containsKey(p.getUUID()) || FEET_AIR.containsKey(p.getUUID()) || UndeadEject.holding(p, UndeadEject.Hold.WHIRL)) {
			return true;
		}
		Long until = SAFE_UNTIL.get(p.getUUID());
		return until != null && p.level().getGameTime() <= until;
	}

	/**
	 * A launch is in the air or an ejection move is running: {@code Undead.tick} starts no regrowth then (the pieces come back
	 * once the move is over).
	 */
	public static boolean flying(ServerPlayer p) {
		return RUNS.containsKey(p.getUUID()) || UndeadEject.holding(p);
	}

	/** A Combustion Punch (or Eject Head) is in the air (tests). */
	public static boolean launching(ServerPlayer p) {
		return RUNS.containsKey(p.getUUID());
	}

	// ---------------------------------------------------------------- shared

	private static boolean ready(ServerPlayer p, String id) {
		if (!Undead.canAct(p)) {
			return false;
		}
		int cd = Undead.cooldownRemaining(p, id);
		if (cd > 0) {
			Undead.say(p, "message.projecthero.undead.cooldown", ChatFormatting.GRAY, Component.translatable("projecthero.undead.ability." + id),
					String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		return true;
	}

	private static void begin(ServerPlayer p, String id, int cooldown, int animId) {
		long now = p.level().getGameTime();
		UndeadState n = Undead.state(p).copy();
		n.abilityReadyAt.put(id, now + cooldown);
		n.animId = animId;
		n.animStart = now;
		Undead.save(p, n);
	}

	private static void anim(ServerPlayer p, int animId) {
		UndeadState n = Undead.state(p).copy();
		n.animId = animId;
		n.animStart = p.level().getGameTime();
		Undead.save(p, n);
	}

	private static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	/**
	 * The Parts Bullet Head's hit, shared by the flying head entity and the Eject Head: the damage, a heavy knockback and the
	 * knock of a skull on bone.
	 */
	public static boolean headHit(ServerPlayer owner, LivingEntity target, Vec3 from, float damage) {
		boolean hit = UndeadAbilities.strike(owner, target, from, damage, 1.4, 0.3);
		if (hit) {
			UndeadSfx.headRoll(owner.level(), target.position().add(0, target.getBbHeight() * 0.6, 0), 1.0f);
		}
		return hit;
	}

	// ---------------------------------------------------------------- tap X: Eject Feet

	public static boolean ejectFeet(ServerPlayer p) {
		if (!ready(p, EJECT_FEET)) {
			return false;
		}
		if (Undead.missing(p, UndeadState.BODY) || Undead.bothLegsMissing(p)) {
			Undead.say(p, "message.projecthero.undead.no_legs", ChatFormatting.GRAY);
			return false;
		}
		UndeadFlight.stop(p); // (from the flight: the feet are already off -- the blast throws him all the same)
		boolean grounded = p.onGround();
		UndeadEject.hold(p, UndeadEject.Hold.FEET);
		UndeadEject.eject(p, UndeadEject.FEET, true);
		begin(p, EJECT_FEET, UndeadEjectConfig.EJECT_FEET_COOLDOWN, UndeadState.ANIM_EJECT_FEET);
		// one impulse that carries him the distance on a vanilla arc (drag and gravity from here on, like the other dashes)
		Vec3 v = AbilityHelpers.ballisticLaunch(p.getLookAngle(), UndeadEjectConfig.EJECT_FEET_DISTANCE, grounded);
		AbilityHelpers.launchSelf(p, v);
		long now = p.level().getGameTime();
		FEET_AIR.put(p.getUUID(), now);
		SAFE_UNTIL.put(p.getUUID(), now + UndeadEjectConfig.EJECT_FEET_MAX_AIR);
		ServerLevel level = level(p);
		Vec3 feet = p.position().add(0, 0.2, 0);
		level.sendParticles(Undead.BLOOD, feet.x, feet.y, feet.z, 8, 0.25, 0.1, 0.25, 0.0);
		UndeadSfx.launch(level, feet);
		UndeadSfx.bloodBurst(level, feet, 0.5f);
		return true;
	}

	/** The test hook: the launch velocity a flat Eject Feet from the ground uses. */
	public static Vec3 ejectFeetVelocity(Vec3 look, boolean grounded) {
		return AbilityHelpers.ballisticLaunch(look, UndeadEjectConfig.EJECT_FEET_DISTANCE, grounded);
	}

	private static void tickFeet(ServerPlayer p, long now) {
		Long at = FEET_AIR.get(p.getUUID());
		if (at == null) {
			return;
		}
		long age = now - at;
		p.resetFallDistance();
		boolean down = age >= 3 && (p.onGround() || p.isInWater() || p.onClimbable()) || age > UndeadEjectConfig.EJECT_FEET_MAX_AIR || !p.isAlive();
		if (down) {
			FEET_AIR.remove(p.getUUID());
			SAFE_UNTIL.put(p.getUUID(), now + UndeadEjectConfig.EJECT_FEET_SAFE_AFTER_LANDING);
			UndeadEject.release(p, UndeadEject.Hold.FEET);
			if (p.onGround()) {
				UndeadSfx.splat(p.level(), p.position());
			}
			return;
		}
		// a trail of blood from the calf stumps for the first moments of the flight
		if (age < 14 && age % 2 == 0) {
			Vec3 tail = p.position().add(0, 0.35, 0);
			level(p).sendParticles(Undead.BLOOD, tail.x, tail.y, tail.z, 2, 0.1, 0.05, 0.1, 0.0);
		}
	}

	// ---------------------------------------------------------------- Shift+X: Combustion Punch

	/** The creature the crosshair is on within the range, else the nearest to the crosshair line (in a narrow cone); or null. */
	public static LivingEntity combustionTarget(ServerPlayer p) {
		double range = UndeadEjectConfig.COMBUSTION_RANGE;
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 far = eye.add(look.scale(range));
		HitResult block = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e instanceof LivingEntity le && valid(p, le), eye.distanceToSqr(end));
		if (ehr != null && ehr.getEntity() instanceof LivingEntity le) {
			return le;
		}
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (LivingEntity e : level(p).getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), e -> valid(p, e))) {
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			Vec3 to = c.subtract(eye);
			double d = to.length();
			if (d > range || d < 1.0e-3) {
				continue;
			}
			double cos = to.scale(1.0 / d).dot(look);
			if (cos < 0.86) {
				continue; // about 30 degrees off the crosshair at most
			}
			if (p.level().clip(new ClipContext(eye, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) {
				continue;
			}
			double score = (1.0 - cos) * 40.0 + d * 0.05;
			if (score < bestScore) {
				bestScore = score;
				best = e;
			}
		}
		return best;
	}

	private static boolean valid(ServerPlayer p, LivingEntity e) {
		return e != p && e.isAlive() && !e.isSpectator() && e.isPickable() && !(e instanceof UndeadCloneEntity) && HeroTargets.canHarm(p, e);
	}

	public static boolean combustionPunch(ServerPlayer p) {
		if (!ready(p, COMBUSTION)) {
			return false;
		}
		UndeadState s = Undead.state(p);
		if (s.broken(UndeadState.HEAD) || s.broken(UndeadState.BODY)) {
			Undead.say(p, "message.projecthero.undead.no_body", ChatFormatting.GRAY);
			return false;
		}
		int main = Undead.mainArm(p);
		int arm = !s.broken(main) ? main : !s.broken(Undead.otherArm(main)) ? Undead.otherArm(main) : -1;
		if (arm < 0) {
			Undead.say(p, "message.projecthero.undead.no_arm", ChatFormatting.GRAY);
			return false;
		}
		LivingEntity target = combustionTarget(p);
		if (target == null) {
			Undead.say(p, "message.projecthero.undead.no_target", ChatFormatting.GRAY);
			return false;
		}
		UndeadFlight.stop(p);
		Vec3 at = p.position();
		float yaw = p.yBodyRot;
		// the punching arm is the one he keeps (FLAG_OFF_ARM says which, for every viewer's pose)
		UndeadState n = Undead.state(p).copy();
		n.setFlag(UndeadState.FLAG_OFF_ARM, arm != main);
		Undead.save(p, n);
		UndeadEject.hold(p, UndeadEject.Hold.COMBUSTION);
		UndeadEject.cutTorso(p, true);
		// the rest of him stays standing where he was: both legs, the other arm and the lower two thirds of the torso
		int keepWith = -1;
		for (int part : new int[] { UndeadState.RIGHT_LEG, UndeadState.LEFT_LEG, Undead.otherArm(arm) }) {
			if (Undead.missing(p, part)) {
				continue;
			}
			Undead.breakPart(p, part, UndeadState.KIND_LAUNCHED);
			UndeadEject.leaveBehind(p, part, UndeadPartEntity.STYLE_LEFT_BODY, at, yaw, Vec3.ZERO);
			if (keepWith < 0) {
				keepWith = part;
			}
		}
		UndeadEject.leaveBehind(p, keepWith < 0 ? UndeadState.BODY : keepWith, UndeadPartEntity.STYLE_EJECT_TORSO, at, yaw, Vec3.ZERO);
		begin(p, COMBUSTION, UndeadEjectConfig.COMBUSTION_COOLDOWN, UndeadState.ANIM_COMBUSTION);
		Run r = new Run(target.getId(), p.level().getGameTime(), p.position(), false, Vec3.ZERO);
		RUNS.put(p.getUUID(), r);
		push(p, r, target);
		ServerLevel level = level(p);
		Vec3 c = at.add(0, 1.0, 0);
		level.sendParticles(Undead.BLOOD_BIG, c.x, c.y, c.z, 10, 0.25, 0.3, 0.25, 0.0);
		level.sendParticles(Undead.BLOOD, c.x, c.y, c.z, 16, 0.35, 0.4, 0.35, 0.0);
		UndeadSfx.shatter(level, c);
		UndeadSfx.launch(level, c);
		return true;
	}

	/** Toward the target's chest at {@link UndeadEjectConfig#COMBUSTION_SPEED} (the last stretch exactly onto it). */
	private static void push(ServerPlayer p, Run r, LivingEntity target) {
		Vec3 aim = target.position().add(0, target.getBbHeight() * 0.55, 0);
		// he is about a crawl box now: his middle sits ~0.3 above his feet
		Vec3 me = p.position().add(0, 0.3, 0);
		Vec3 d = aim.subtract(me);
		double len = d.length();
		double speed = Math.min(UndeadEjectConfig.COMBUSTION_SPEED, Math.max(0.2, len - 0.6));
		Vec3 v = len < 1.0e-3 ? Vec3.ZERO : d.scale(speed / len);
		AbilityHelpers.launchSelf(p, v);
	}

	/** The punch lands: the damage, the knockback (about 5 blocks), the burst. */
	private static void punch(ServerPlayer p, Run r, LivingEntity target, long now) {
		r.punchedAt = now;
		anim(p, UndeadState.ANIM_COMBUSTION_PUNCH);
		Vec3 from = p.position();
		boolean boss = UndeadAbilities.isBoss(target);
		if (UndeadAbilities.strike(p, target, from, UndeadEjectConfig.COMBUSTION_DAMAGE, 0.0, 0.0) && !boss) {
			Vec3 flat = new Vec3(target.getX() - p.getX(), 0, target.getZ() - p.getZ());
			if (flat.lengthSqr() < 1.0e-4) {
				flat = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
			}
			Vec3 v = AbilityHelpers.ballisticLaunch(flat.normalize(), UndeadEjectConfig.COMBUSTION_KNOCKBACK, target.onGround());
			target.setDeltaMovement(v);
			target.hurtMarked = true;
			target.hasImpulse = true;
		}
		// he stops dead on the hit (a small bounce back)
		Vec3 back = p.getDeltaMovement().scale(-0.12).add(0, 0.12, 0);
		AbilityHelpers.launchSelf(p, back);
		ServerLevel level = level(p);
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.55, 0);
		level.sendParticles(Undead.BLOOD_BIG, c.x, c.y, c.z, 12, 0.3, 0.3, 0.3, 0.0);
		level.sendParticles(Undead.BLOOD, c.x, c.y, c.z, 24, 0.45, 0.45, 0.45, 0.0);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.3);
		UndeadSfx.crimsonImpact(level, c);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8f, 0.7f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.35f, 1.5f);
	}

	/** The Combustion Punch is over (hit or not): the cut torso goes too and the whole body grows back from his head. */
	private static void endCombustion(ServerPlayer p, long now) {
		RUNS.remove(p.getUUID());
		SAFE_UNTIL.put(p.getUUID(), now + 40);
		p.resetFallDistance();
		boolean wasCut = Undead.state(p).ex(UndeadState.X_EJECTED) != 0L;
		UndeadEject.cutTorso(p, false);
		if (wasCut && !Undead.missing(p, UndeadState.BODY)) {
			// what is left of the torso goes with the rest (no burst): the regrowth builds it back from the neck
			Undead.breakPart(p, UndeadState.BODY, UndeadState.KIND_LAUNCHED);
		}
		UndeadEject.release(p, UndeadEject.Hold.COMBUSTION);
	}

	// ---------------------------------------------------------------- the Regen Menu's Eject Head

	/**
	 * The Regen Menu's "Eject Head" (the old Head Launch): every part but the head comes off and stays where he was, the head
	 * flies straight along the look (hitting like the Parts Bullet Head) and the body grows back from it.
	 */
	public static void headLaunch(ServerPlayer p) {
		if (!ready(p, HEAD_LAUNCH)) {
			return;
		}
		if (Undead.missing(p, UndeadState.HEAD)) {
			Undead.say(p, "message.projecthero.undead.no_head", ChatFormatting.GRAY);
			return;
		}
		Vec3 at = p.position();
		float yaw = p.yBodyRot;
		Vec3 dir = p.getLookAngle().normalize();
		boolean legless = Undead.bothLegsMissing(p);
		for (int part = UndeadState.BODY; part < UndeadState.PARTS; part++) {
			if (Undead.missing(p, part)) {
				continue;
			}
			Undead.breakPart(p, part, UndeadState.KIND_LAUNCHED);
			UndeadEject.leaveBehind(p, part, legless ? UndeadPartEntity.STYLE_LEFT_BODY_LEGLESS : UndeadPartEntity.STYLE_LEFT_BODY, at, yaw, Vec3.ZERO);
		}
		begin(p, HEAD_LAUNCH, UndeadConfig.HEAD_LAUNCH_COOLDOWN, UndeadState.ANIM_HEAD_LAUNCH);
		Run r = new Run(-1, p.level().getGameTime(), p.position(), true, dir);
		RUNS.put(p.getUUID(), r);
		AbilityHelpers.launchSelf(p, dir.scale(UndeadConfig.HEAD_LAUNCH_DASH_SPEED));
		ServerLevel level = level(p);
		Vec3 neck = at.add(0, legless ? 0.8 : 1.5, 0);
		level.sendParticles(Undead.BLOOD_BIG, neck.x, neck.y, neck.z, 14, 0.15, 0.1, 0.15, 0.0);
		UndeadSfx.bloodBurst(level, neck, 1.0f);
		UndeadSfx.launch(level, neck);
	}

	// ---------------------------------------------------------------- tick

	/** Every tick, from {@link UndeadAbilityManager#tickHold}. */
	public static void tick(ServerPlayer p) {
		long now = p.level().getGameTime();
		Long until = SAFE_UNTIL.get(p.getUUID());
		if (until != null && now > until) {
			SAFE_UNTIL.remove(p.getUUID());
		}
		tickFeet(p, now);
		Run r = RUNS.get(p.getUUID());
		if (r == null) {
			return;
		}
		long age = now - r.start;
		Vec3 pos = p.position();
		r.still = age >= 3 && pos.distanceToSqr(r.last) < 0.01 ? r.still + 1 : 0;
		p.resetFallDistance();
		if (r.head) {
			tickHead(p, r, now, age, pos);
			return;
		}
		if (Undead.state(p).reformUntil > now || !p.isAlive() || p.isSpectator()) {
			endCombustion(p, now);
			return;
		}
		if (r.punchedAt >= 0L) {
			if (now - r.punchedAt >= UndeadEjectConfig.COMBUSTION_PUNCH_TICKS) {
				endCombustion(p, now);
			}
			return;
		}
		Entity e = p.level().getEntity(r.target);
		LivingEntity target = e instanceof LivingEntity le && le.isAlive() && le.level() == p.level() ? le : null;
		if (target == null || age > UndeadEjectConfig.COMBUSTION_MAX_TICKS || r.still >= 3) {
			endCombustion(p, now); // the target is gone, out of reach in time, or a wall stopped him: no punch
			return;
		}
		// reached it: the fist (ahead of his middle) at the box
		Vec3 fist = pos.add(0, 0.3, 0).add(p.getDeltaMovement().normalize().scale(0.5));
		if (target.getBoundingBox().inflate(UndeadEjectConfig.COMBUSTION_REACH).contains(fist)
				|| target.getBoundingBox().inflate(0.4).intersects(p.getBoundingBox().inflate(0.3))) {
			punch(p, r, target, now);
			return;
		}
		r.last = pos;
		push(p, r, target);
		// blood streaming off the stumps behind him
		Vec3 tail = pos.add(0, 0.3, 0).subtract(p.getDeltaMovement().normalize().scale(0.5));
		level(p).sendParticles(Undead.BLOOD, tail.x, tail.y, tail.z, 3, 0.12, 0.08, 0.12, 0.0);
		if (age % 6 == 0) {
			level(p).playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.25f, 1.4f);
		}
	}

	private static void tickHead(ServerPlayer p, Run r, long now, long age, Vec3 pos) {
		double range = UndeadConfig.HEAD_LAUNCH_RANGE;
		boolean far = pos.distanceToSqr(r.from) > range * range;
		if (!p.isAlive() || p.isSpectator() || age > UndeadConfig.HEAD_LAUNCH_MAX || far || r.still >= 2 || (age >= 3 && p.isInWater())) {
			RUNS.remove(p.getUUID());
			SAFE_UNTIL.put(p.getUUID(), now + 40);
			UndeadSfx.headRoll(p.level(), p.position().add(0, 0.25, 0), 1.0f);
			return;
		}
		AABB swept = p.getBoundingBox().inflate(0.6);
		for (LivingEntity e : level(p).getEntitiesOfClass(LivingEntity.class, swept, e -> valid(p, e))) {
			if (e.invulnerableTime <= 0) {
				headHit(p, e, pos, UndeadConfig.HEAD_BULLET_DAMAGE);
			}
		}
		r.last = pos;
		AbilityHelpers.launchSelf(p, r.dir.scale(UndeadConfig.HEAD_LAUNCH_DASH_SPEED));
	}
}
