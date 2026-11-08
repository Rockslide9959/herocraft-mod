package com.projecthero.mod.andy;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.andy.data.AndyState;
import com.projecthero.mod.andy.entity.AndyCloneEntity;
import com.projecthero.mod.andy.entity.AndyPartEntity;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: Andy's two X launches. The key itself (tap / hold / Shift + hold) is read in {@link AndyAbilityManager}.
 * <ul>
 *   <li><b>Leg Launch</b> (tap X): both legs break off at the hips and stay standing where he was (then topple and lie there
 *       until they regrow), blood bursts from the stumps and his upper body is thrown far forward along the look.</li>
 *   <li><b>Head Launch</b> (hold X for {@link AndyConfig#HEAD_LAUNCH_HOLD}): after a wind-up his head shoots off on a long arc
 *       and the player goes with it -- the whole body below is left behind and every part but the head is broken, so all of it
 *       grows back. The head hits like the Parts Bullet Head ({@link #headHit}, shared with it).</li>
 * </ul>
 * The parts go through {@link Andy#breakPart} ({@code KIND_LAUNCHED}: the dropped pieces are the parts, so nothing bursts);
 * the regrowth and the legless / head-only movement pick that up as for any broken part. The flight is a scripted arc: the
 * server sets the velocity every tick and sends it ({@link AbilityHelpers#launchSelf}), the client moves the player as usual,
 * so there is no rubber-banding; it ends on landing, against a wall or after {@code *_MAX}. No fall damage until shortly
 * after. Every map here is dropped by {@link #clearSessionState} and {@link #forget}.
 */
public final class AndyLaunch {
	public static final String LEG_LAUNCH = "leg_launch";
	public static final String HEAD_LAUNCH = "head_launch";

	/** A launch in the air: which, the flat heading, the start speeds, when, who it has hit, where it was last tick. */
	private static final class Run {
		final boolean head;
		final Vec3 dir;
		final double speed;
		final double lift;
		final long start;
		final Set<Integer> hit = new HashSet<>();
		Vec3 last;
		/** Ticks in a row it has barely moved (a wall). */
		int still;

		Run(boolean head, Vec3 dir, double speed, double lift, long start, Vec3 at) {
			this.head = head;
			this.dir = dir;
			this.speed = speed;
			this.lift = lift;
			this.start = start;
			this.last = at;
		}
	}

	private static final Map<UUID, Run> RUNS = new ConcurrentHashMap<>();
	/** No fall damage until this game time (a little past the landing). */
	private static final Map<UUID, Long> SAFE_UNTIL = new ConcurrentHashMap<>();

	private AndyLaunch() {
	}

	static void clearSessionState() {
		RUNS.clear();
		SAFE_UNTIL.clear();
	}

	static void forget(UUID id) {
		RUNS.remove(id);
		SAFE_UNTIL.remove(id);
	}

	/** In a launch (or just landed from one): falls do nothing. From {@link AndyDamage#allowDamage}. */
	public static boolean fallImmune(ServerPlayer p) {
		if (RUNS.containsKey(p.getUUID())) {
			return true;
		}
		Long until = SAFE_UNTIL.get(p.getUUID());
		return until != null && p.level().getGameTime() <= until;
	}

	/** A launch is in the air (tests). */
	public static boolean flying(ServerPlayer p) {
		return RUNS.containsKey(p.getUUID());
	}

	// ---------------------------------------------------------------- shared

	private static boolean ready(ServerPlayer p, String id, boolean quiet) {
		if (quiet) {
			AndyState s = Andy.state(p);
			return s.hasPower && p.isAlive() && !p.isSpectator() && s.reformUntil <= p.level().getGameTime() && Andy.cooldownRemaining(p, id) <= 0;
		}
		if (!Andy.canAct(p)) {
			return false;
		}
		int cd = Andy.cooldownRemaining(p, id);
		if (cd > 0) {
			Andy.say(p, "message.projecthero.andy.cooldown", ChatFormatting.GRAY, Component.translatable("projecthero.andy.ability." + id),
					String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		return true;
	}

	private static void begin(ServerPlayer p, String id, int cooldown, int animId) {
		long now = p.level().getGameTime();
		AndyState n = Andy.state(p).copy();
		n.abilityReadyAt.put(id, now + cooldown);
		n.animId = animId;
		n.animStart = now;
		Andy.save(p, n);
	}

	private static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	private static void sound(ServerPlayer p, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		return flat.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : flat.normalize();
	}

	/**
	 * The Parts Bullet Head's hit, shared by the flying head entity and the Head Launch: the damage, a heavy knockback and the
	 * knock of a skull on bone.
	 */
	public static boolean headHit(ServerPlayer owner, LivingEntity target, Vec3 from, float damage) {
		boolean hit = AndyAbilities.strike(owner, target, from, damage, 1.4, 0.3);
		if (hit) {
			AndySfx.headRoll(owner.level(), target.position().add(0, target.getBbHeight() * 0.6, 0), 1.0f);
		}
		return hit;
	}

	// ---------------------------------------------------------------- the wind-up

	/** From the X hold, {@link AndyConfig#LAUNCH_CHARGE_SHOW} in: his head tips back and he tenses (only if the launch is ready). */
	static void charge(ServerPlayer p) {
		if (!ready(p, HEAD_LAUNCH, true) || Andy.missing(p, AndyState.HEAD)) {
			return;
		}
		AndyState n = Andy.state(p).copy();
		n.animId = AndyState.ANIM_HEAD_CHARGE;
		n.animStart = p.level().getGameTime();
		Andy.save(p, n);
		sound(p, SoundEvents.WARDEN_HEARTBEAT, 0.9f, 1.3f);
	}

	/** Each tick of the wind-up: a rising heartbeat and blood beading at the neck. */
	static void charging(ServerPlayer p, long held) {
		AndyState s = Andy.state(p);
		if (s.animId != AndyState.ANIM_HEAD_CHARGE) {
			return;
		}
		if (held % 5 == 0) {
			sound(p, SoundEvents.WARDEN_HEARTBEAT, 0.9f, 1.3f + held * 0.03f);
		}
		Vec3 neck = AndyParts.centre(p, AndyState.HEAD).add(0, -0.25, 0);
		level(p).sendParticles(Andy.BLOOD, neck.x, neck.y, neck.z, 1 + (int) (held / 8), 0.18, 0.04, 0.18, 0.0);
	}

	/** X let go before the Head Launch: the wind-up pose ends (unless a launch replaced it). */
	static void endCharge(ServerPlayer p) {
		AndyState s = Andy.state(p);
		if (s.animId == AndyState.ANIM_HEAD_CHARGE) {
			AndyState n = s.copy();
			n.animId = AndyState.ANIM_NONE;
			Andy.save(p, n);
		}
	}

	// ---------------------------------------------------------------- Leg Launch

	public static void legLaunch(ServerPlayer p) {
		if (!ready(p, LEG_LAUNCH, false)) {
			endCharge(p);
			return;
		}
		if (Andy.missing(p, AndyState.BODY)) {
			Andy.say(p, "message.projecthero.andy.no_body", ChatFormatting.GRAY);
			endCharge(p);
			return;
		}
		if (Andy.bothLegsMissing(p)) {
			Andy.say(p, "message.projecthero.andy.no_legs", ChatFormatting.GRAY);
			endCharge(p);
			return;
		}
		Vec3 at = p.position();
		float yaw = p.yBodyRot;
		Vec3 dir = flatLook(p);
		// the legs stay where he stood, kicked back a touch by the launch
		for (int leg = AndyState.RIGHT_LEG; leg <= AndyState.LEFT_LEG; leg++) {
			if (Andy.missing(p, leg)) {
				continue;
			}
			Andy.breakPart(p, leg, AndyState.KIND_LAUNCHED);
			AndyPartEntity.drop(p, leg, AndyPartEntity.STYLE_LEFT_LEG, at, yaw, dir.scale(-0.04));
		}
		begin(p, LEG_LAUNCH, AndyConfig.LEG_LAUNCH_COOLDOWN, AndyState.ANIM_LEG_LAUNCH);
		double lift = Math.max(0.25, Math.min(0.9, AndyConfig.LEG_LAUNCH_LIFT + p.getLookAngle().y * 0.5));
		start(p, false, dir, AndyConfig.LEG_LAUNCH_SPEED, lift);
		// blood bursts from the hip stumps
		ServerLevel level = level(p);
		Vec3 hips = at.add(0, 0.75, 0);
		level.sendParticles(Andy.BLOOD_BIG, hips.x, hips.y, hips.z, 10, 0.2, 0.08, 0.2, 0.0);
		level.sendParticles(Andy.BLOOD, hips.x, hips.y, hips.z, 18, 0.35, 0.25, 0.35, 0.0);
		level.sendParticles(Andy.BLOOD_DARK, hips.x, hips.y - 0.1, hips.z, 6, 0.15, 0.05, 0.15, 0.0);
		AndySfx.partBreak(level, hips);
		AndySfx.bloodBurst(level, hips, 1.0f);
		AndySfx.launch(level, hips);
	}

	// ---------------------------------------------------------------- Head Launch

	public static void headLaunch(ServerPlayer p) {
		if (!ready(p, HEAD_LAUNCH, false)) {
			endCharge(p);
			return;
		}
		if (Andy.missing(p, AndyState.HEAD)) {
			Andy.say(p, "message.projecthero.andy.no_head", ChatFormatting.GRAY);
			endCharge(p);
			return;
		}
		Vec3 at = p.position();
		float yaw = p.yBodyRot;
		Vec3 dir = flatLook(p);
		boolean legless = Andy.bothLegsMissing(p);
		// the body stays behind and falls as one; every part but the head goes (and so all of it grows back)
		for (int part = AndyState.BODY; part < AndyState.PARTS; part++) {
			if (Andy.missing(p, part)) {
				continue;
			}
			Andy.breakPart(p, part, AndyState.KIND_LAUNCHED);
			AndyPartEntity.drop(p, part, legless ? AndyPartEntity.STYLE_LEFT_BODY_LEGLESS : AndyPartEntity.STYLE_LEFT_BODY, at, yaw, Vec3.ZERO);
		}
		begin(p, HEAD_LAUNCH, AndyConfig.HEAD_LAUNCH_COOLDOWN, AndyState.ANIM_HEAD_LAUNCH);
		double lift = Math.max(0.5, Math.min(1.6, AndyConfig.HEAD_LAUNCH_LIFT + p.getLookAngle().y * 0.8));
		start(p, true, dir, AndyConfig.HEAD_LAUNCH_SPEED, lift);
		ServerLevel level = level(p);
		Vec3 neck = at.add(0, legless ? 0.8 : 1.5, 0);
		level.sendParticles(Andy.BLOOD_BIG, neck.x, neck.y, neck.z, 14, 0.15, 0.1, 0.15, 0.0);
		level.sendParticles(Andy.BLOOD, neck.x, neck.y + 0.2, neck.z, 24, 0.3, 0.4, 0.3, 0.0);
		AndySfx.partBreak(level, neck.add(0, -0.4, 0));
		AndySfx.bloodBurst(level, neck, 1.5f);
		AndySfx.launch(level, neck);
	}

	// ---------------------------------------------------------------- the flight

	private static void start(ServerPlayer p, boolean head, Vec3 dir, double speed, double lift) {
		long now = p.level().getGameTime();
		Run r = new Run(head, dir, speed, lift, now, p.position());
		RUNS.put(p.getUUID(), r);
		push(p, r, 0);
	}

	private static void push(ServerPlayer p, Run r, long age) {
		Vec3 v = r.dir.scale(r.speed).add(0, r.lift - AndyConfig.LAUNCH_GRAVITY * age, 0);
		AbilityHelpers.launchSelf(p, v);
	}

	private static void land(ServerPlayer p, long now) {
		Run r = RUNS.remove(p.getUUID());
		if (r != null && r.head) {
			AndySfx.headRoll(p.level(), p.position().add(0, 0.25, 0), 1.0f);
		}
		SAFE_UNTIL.put(p.getUUID(), now + 40);
		p.resetFallDistance();
	}

	/** Every tick, from {@link AndyAbilityManager#tickHold}. */
	static void tick(ServerPlayer p) {
		Run r = RUNS.get(p.getUUID());
		long now = p.level().getGameTime();
		if (r == null) {
			Long until = SAFE_UNTIL.get(p.getUUID());
			if (until != null && now > until) {
				SAFE_UNTIL.remove(p.getUUID());
			}
			return;
		}
		long age = now - r.start;
		int max = r.head ? AndyConfig.HEAD_LAUNCH_MAX : AndyConfig.LEG_LAUNCH_MAX;
		Vec3 pos = p.position();
		r.still = age >= 3 && pos.distanceToSqr(r.last) < 0.04 ? r.still + 1 : 0;
		boolean stalled = r.still >= 2;
		if (!p.isAlive() || p.isSpectator() || age > max || (age >= 3 && (p.onGround() || p.isInWater())) || stalled || age < 0) {
			land(p, now);
			return;
		}
		p.resetFallDistance();
		// what it passes through this tick
		AABB swept = p.getBoundingBox().minmax(p.getBoundingBox().move(r.last.subtract(pos))).inflate(0.6);
		for (LivingEntity e : level(p).getEntitiesOfClass(LivingEntity.class, swept,
				e -> e != p && !(e instanceof AndyCloneEntity) && HeroTargets.canHarm(p, e))) {
			if (!r.hit.add(e.getId())) {
				continue;
			}
			if (r.head) {
				headHit(p, e, r.last, AndyConfig.HEAD_BULLET_DAMAGE);
			} else {
				AndyAbilities.strike(p, e, r.last, AndyConfig.LEG_LAUNCH_DAMAGE, 0.9, 0.3);
			}
		}
		r.last = pos;
		push(p, r, age);
		// a trail of blood from the stumps (hips) or the neck
		ServerLevel level = level(p);
		double y = r.head ? 0.15 : 0.3;
		Vec3 trail = pos.add(0, y, 0).subtract(r.dir.scale(0.4));
		level.sendParticles(Andy.BLOOD, trail.x, trail.y, trail.z, r.head ? 3 : 4, 0.12, 0.08, 0.12, 0.0);
		if (age % 2 == 0) {
			level.sendParticles(Andy.BLOOD_BIG, trail.x, trail.y, trail.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		if (age % 6 == 0) {
			level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.3f, r.head ? 1.8f : 1.4f);
		}
	}
}
