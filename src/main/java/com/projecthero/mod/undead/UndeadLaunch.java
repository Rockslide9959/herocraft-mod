package com.projecthero.mod.undead;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadCloneEntity;
import com.projecthero.mod.undead.entity.UndeadPartEntity;
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
 * v0.17.3: Undead's two X launches. The key itself (tap / hold / Shift + hold) is read in {@link UndeadAbilityManager}.
 * <ul>
 *   <li><b>Leg Launch</b> (tap X): both legs break off at the hips and stay standing where he was (then topple and lie there
 *       until they regrow), blood bursts from the stumps and his upper body is thrown far forward along the look.</li>
 *   <li><b>Head Launch</b> (hold X for {@link UndeadConfig#HEAD_LAUNCH_HOLD}): after a wind-up his head shoots off on a long arc
 *       and the player goes with it -- the whole body below is left behind and every part but the head is broken, so all of it
 *       grows back. The head hits like the Parts Bullet Head ({@link #headHit}, shared with it).</li>
 * </ul>
 * The parts go through {@link Undead#breakPart} ({@code KIND_LAUNCHED}: the dropped pieces are the parts, so nothing bursts);
 * the regrowth and the legless / head-only movement pick that up as for any broken part. The flight is a scripted arc: the
 * server sets the velocity every tick and sends it ({@link AbilityHelpers#launchSelf}), the client moves the player as usual,
 * so there is no rubber-banding; it ends on landing, against a wall or after {@code *_MAX}. No fall damage until shortly
 * after. Every map here is dropped by {@link #clearSessionState} and {@link #forget}.
 */
public final class UndeadLaunch {
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
		final Vec3 from;
		Vec3 last;
		/** Ticks in a row it has barely moved (a wall). */
		int still;

		Run(boolean head, Vec3 dir, double speed, double lift, long start, Vec3 at) {
			this.head = head;
			this.dir = dir;
			this.speed = speed;
			this.lift = lift;
			this.start = start;
			this.from = at;
			this.last = at;
		}
	}

	private static final Map<UUID, Run> RUNS = new ConcurrentHashMap<>();
	/** No fall damage until this game time (a little past the landing). */
	private static final Map<UUID, Long> SAFE_UNTIL = new ConcurrentHashMap<>();

	private UndeadLaunch() {
	}

	static void clearSessionState() {
		RUNS.clear();
		SAFE_UNTIL.clear();
	}

	static void forget(UUID id) {
		RUNS.remove(id);
		SAFE_UNTIL.remove(id);
	}

	/** In a launch (or just landed from one): falls do nothing. From {@link UndeadDamage#allowDamage}. */
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
			UndeadState s = Undead.state(p);
			return s.hasPower && p.isAlive() && !p.isSpectator() && s.reformUntil <= p.level().getGameTime() && Undead.cooldownRemaining(p, id) <= 0;
		}
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

	private static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	private static void sound(ServerPlayer p, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	/** Straight where he aims (v0.17.3): the full look, up and down included. */
	private static Vec3 aim(ServerPlayer p) {
		return p.getLookAngle().normalize();
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
		boolean hit = UndeadAbilities.strike(owner, target, from, damage, 1.4, 0.3);
		if (hit) {
			UndeadSfx.headRoll(owner.level(), target.position().add(0, target.getBbHeight() * 0.6, 0), 1.0f);
		}
		return hit;
	}

	// ---------------------------------------------------------------- the wind-up

	/** From the X hold, {@link UndeadConfig#LAUNCH_CHARGE_SHOW} in: his head tips back and he tenses (only if the launch is ready). */
	static void charge(ServerPlayer p) {
		if (!ready(p, HEAD_LAUNCH, true) || Undead.missing(p, UndeadState.HEAD)) {
			return;
		}
		UndeadState n = Undead.state(p).copy();
		n.animId = UndeadState.ANIM_HEAD_CHARGE;
		n.animStart = p.level().getGameTime();
		Undead.save(p, n);
		sound(p, SoundEvents.WARDEN_HEARTBEAT, 0.9f, 1.3f);
	}

	/** Each tick of the wind-up: a rising heartbeat and blood beading at the neck. */
	static void charging(ServerPlayer p, long held) {
		UndeadState s = Undead.state(p);
		if (s.animId != UndeadState.ANIM_HEAD_CHARGE) {
			return;
		}
		if (held % 5 == 0) {
			sound(p, SoundEvents.WARDEN_HEARTBEAT, 0.9f, 1.3f + held * 0.03f);
		}
		Vec3 neck = UndeadParts.centre(p, UndeadState.HEAD).add(0, -0.25, 0);
		level(p).sendParticles(Undead.BLOOD, neck.x, neck.y, neck.z, 1 + (int) (held / 8), 0.18, 0.04, 0.18, 0.0);
	}

	/** X let go before the Head Launch: the wind-up pose ends (unless a launch replaced it). */
	static void endCharge(ServerPlayer p) {
		UndeadState s = Undead.state(p);
		if (s.animId == UndeadState.ANIM_HEAD_CHARGE) {
			UndeadState n = s.copy();
			n.animId = UndeadState.ANIM_NONE;
			Undead.save(p, n);
		}
	}

	// ---------------------------------------------------------------- Leg Launch

	public static void legLaunch(ServerPlayer p) {
		if (!ready(p, LEG_LAUNCH, false)) {
			endCharge(p);
			return;
		}
		if (Undead.missing(p, UndeadState.BODY)) {
			Undead.say(p, "message.projecthero.undead.no_body", ChatFormatting.GRAY);
			endCharge(p);
			return;
		}
		if (Undead.bothLegsMissing(p)) {
			Undead.say(p, "message.projecthero.undead.no_legs", ChatFormatting.GRAY);
			endCharge(p);
			return;
		}
		Vec3 at = p.position();
		float yaw = p.yBodyRot;
		Vec3 dir = aim(p);
		// the legs stay where he stood, kicked back a touch by the launch
		for (int leg = UndeadState.RIGHT_LEG; leg <= UndeadState.LEFT_LEG; leg++) {
			if (Undead.missing(p, leg)) {
				continue;
			}
			Undead.breakPart(p, leg, UndeadState.KIND_LAUNCHED);
			UndeadPartEntity.drop(p, leg, UndeadPartEntity.STYLE_LEFT_LEG, at, yaw, dir.scale(-0.04));
		}
		begin(p, LEG_LAUNCH, UndeadConfig.LEG_LAUNCH_COOLDOWN, UndeadState.ANIM_LEG_LAUNCH);
		start(p, false, dir, UndeadConfig.LEG_LAUNCH_DASH_SPEED, 0.0);
		// blood bursts from the hip stumps
		ServerLevel level = level(p);
		Vec3 hips = at.add(0, 0.75, 0);
		level.sendParticles(Undead.BLOOD_BIG, hips.x, hips.y, hips.z, 10, 0.2, 0.08, 0.2, 0.0);
		level.sendParticles(Undead.BLOOD, hips.x, hips.y, hips.z, 18, 0.35, 0.25, 0.35, 0.0);
		level.sendParticles(Undead.BLOOD_DARK, hips.x, hips.y - 0.1, hips.z, 6, 0.15, 0.05, 0.15, 0.0);
		UndeadSfx.partBreak(level, hips);
		UndeadSfx.bloodBurst(level, hips, 1.0f);
		UndeadSfx.launch(level, hips);
	}

	// ---------------------------------------------------------------- Head Launch

	public static void headLaunch(ServerPlayer p) {
		if (!ready(p, HEAD_LAUNCH, false)) {
			endCharge(p);
			return;
		}
		if (Undead.missing(p, UndeadState.HEAD)) {
			Undead.say(p, "message.projecthero.undead.no_head", ChatFormatting.GRAY);
			endCharge(p);
			return;
		}
		Vec3 at = p.position();
		float yaw = p.yBodyRot;
		Vec3 dir = aim(p);
		boolean legless = Undead.bothLegsMissing(p);
		// the body stays behind and falls as one; every part but the head goes (and so all of it grows back)
		for (int part = UndeadState.BODY; part < UndeadState.PARTS; part++) {
			if (Undead.missing(p, part)) {
				continue;
			}
			Undead.breakPart(p, part, UndeadState.KIND_LAUNCHED);
			UndeadPartEntity.drop(p, part, legless ? UndeadPartEntity.STYLE_LEFT_BODY_LEGLESS : UndeadPartEntity.STYLE_LEFT_BODY, at, yaw, Vec3.ZERO);
		}
		begin(p, HEAD_LAUNCH, UndeadConfig.HEAD_LAUNCH_COOLDOWN, UndeadState.ANIM_HEAD_LAUNCH);
		start(p, true, dir, UndeadConfig.HEAD_LAUNCH_DASH_SPEED, 0.0);
		ServerLevel level = level(p);
		Vec3 neck = at.add(0, legless ? 0.8 : 1.5, 0);
		level.sendParticles(Undead.BLOOD_BIG, neck.x, neck.y, neck.z, 14, 0.15, 0.1, 0.15, 0.0);
		level.sendParticles(Undead.BLOOD, neck.x, neck.y + 0.2, neck.z, 24, 0.3, 0.4, 0.3, 0.0);
		UndeadSfx.partBreak(level, neck.add(0, -0.4, 0));
		UndeadSfx.bloodBurst(level, neck, 1.5f);
		UndeadSfx.launch(level, neck);
	}

	// ---------------------------------------------------------------- the flight

	private static void start(ServerPlayer p, boolean head, Vec3 dir, double speed, double lift) {
		long now = p.level().getGameTime();
		Run r = new Run(head, dir, speed, lift, now, p.position());
		RUNS.put(p.getUUID(), r);
		push(p, r, 0);
	}

	private static void push(ServerPlayer p, Run r, long age) {
		// a straight dash along the aim: no lift, no gravity
		AbilityHelpers.launchSelf(p, r.dir.scale(r.speed));
	}

	private static void land(ServerPlayer p, long now, boolean carrying) {
		Run r = RUNS.remove(p.getUUID());
		if (r != null && r.head) {
			UndeadSfx.headRoll(p.level(), p.position().add(0, 0.25, 0), 1.0f);
		}
		// still flying on its momentum: no fall damage until he touches down (then a moment more)
		SAFE_UNTIL.put(p.getUUID(), now + (carrying ? UndeadConfig.LAUNCH_CARRY_SAFE : 40));
		p.resetFallDistance();
	}

	/** Every tick, from {@link UndeadAbilityManager#tickHold}. */
	public static void tick(ServerPlayer p) {
		Run r = RUNS.get(p.getUUID());
		long now = p.level().getGameTime();
		if (r == null) {
			Long until = SAFE_UNTIL.get(p.getUUID());
			if (until != null && now > until) {
				SAFE_UNTIL.remove(p.getUUID());
			} else if (until != null && until > now + 10 && (p.onGround() || p.isInWater())) {
				SAFE_UNTIL.put(p.getUUID(), now + 10); // touched down after flying on: safe a moment longer
			}
			return;
		}
		long age = now - r.start;
		int max = r.head ? UndeadConfig.HEAD_LAUNCH_MAX : UndeadConfig.LEG_LAUNCH_MAX;
		Vec3 pos = p.position();
		r.still = age >= 3 && pos.distanceToSqr(r.last) < 0.04 ? r.still + 1 : 0;
		boolean stalled = r.still >= 2;
		double range = r.head ? UndeadConfig.HEAD_LAUNCH_RANGE : UndeadConfig.LEG_LAUNCH_RANGE;
		boolean far = pos.distanceToSqr(r.from) >= range * range;
		// aimed down into the ground it ends on landing; aimed flat or up it runs its range (or hits a wall)
		boolean grounded = age >= 2 && r.dir.y < -0.25 && p.onGround();
		if (!p.isAlive() || p.isSpectator() || age > max || far || grounded || (age >= 3 && p.isInWater()) || stalled || age < 0) {
			// v0.17.3 (user: "make the player keep the momentum"): at the end of its range (or time) in the open air the dash
			// lets go at full speed and ordinary physics (air drag, gravity) carry him on; only a wall, the ground or water stop it
			boolean open = (far || age > max) && !stalled && !grounded && !p.isInWater() && p.isAlive();
			if (open) {
				AbilityHelpers.launchSelf(p, r.dir.scale(r.speed));
			}
			land(p, now, open);
			return;
		}
		p.resetFallDistance();
		// what it passes through this tick
		AABB swept = p.getBoundingBox().minmax(p.getBoundingBox().move(r.last.subtract(pos))).inflate(0.6);
		for (LivingEntity e : level(p).getEntitiesOfClass(LivingEntity.class, swept,
				e -> e != p && !(e instanceof UndeadCloneEntity) && HeroTargets.canHarm(p, e))) {
			if (!r.hit.add(e.getId())) {
				continue;
			}
			if (r.head) {
				headHit(p, e, r.last, UndeadConfig.HEAD_BULLET_DAMAGE);
			} else {
				UndeadAbilities.strike(p, e, r.last, UndeadConfig.LEG_LAUNCH_DAMAGE, 0.9, 0.3);
			}
		}
		r.last = pos;
		push(p, r, age);
		// a trail of blood from the stumps (hips) or the neck
		ServerLevel level = level(p);
		double y = r.head ? 0.15 : 0.3;
		Vec3 trail = pos.add(0, y, 0).subtract(r.dir.scale(0.4));
		level.sendParticles(Undead.BLOOD, trail.x, trail.y, trail.z, r.head ? 3 : 4, 0.12, 0.08, 0.12, 0.0);
		if (age % 2 == 0) {
			level.sendParticles(Undead.BLOOD_BIG, trail.x, trail.y, trail.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		if (age % 6 == 0) {
			level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.3f, r.head ? 1.8f : 1.4f);
		}
	}
}
