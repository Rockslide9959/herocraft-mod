package com.projecthero.mod.bluebeetle;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.bluebeetle.data.BlueBeetleState;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 (user: "make the ai actually take over the users body, give it a smart ai that automatically attacks mobs
 * hostile to the player, has increased damage and lasts 15 seconds, give it good pathfinding, make it use the fly if it
 * needs to and make it use the abilities"). Khaji-Da Unleashed (Shift+C) is now a takeover: for its 15 s the server
 * drives the body and the player's own movement, keys and clicks do nothing (the camera follows from behind).
 * <ul>
 *   <li><b>Target</b> -- the nearest creature hostile to the player within 32 blocks ({@code HeroTargets.isHostile}: never a
 *       squadmate or a pet), kept while it lives and stays in range.</li>
 *   <li><b>Moving</b> -- a walking path ({@link BeetlePath}, A*: jumps up blocks, drops down, goes round walls) re-planned as
 *       the target moves; the wings come out when the target is up high, there is no way on foot, or he is stuck, and
 *       he flies straight at it, climbing over what is in the way. The motion is sent to his client every tick (the
 *       Hulk-rampage technique), so it plays smoothly.</li>
 *   <li><b>Fighting</b> -- the real kit, cooldowns respected (halved and free while unleashed): Scarab Hammer and the
 *       Energy Blade up close, Shield Pulse when surrounded, Plasma Spikes along level ground, Scarab Grab, the Mega
 *       Cannon and the Plasma Blaster at range, the Scarab Swarm on the way in. The Energy Shield goes up to block what is
 *       shot at him or a hostile about to hit, and comes down again to attack ({@link #guard}).</li>
 *   <li><b>The end</b> -- after the 15 s, or early once nothing hostile is left nearby (Khaji-Da says so in chat).</li>
 * </ul>
 */
public final class KhajiDaTakeover {
	/** What the AI remembers between ticks. */
	static final class Brain {
		int target = -1;
		long noTargetSince = -1L;
		List<BlockPos> path;
		int step;
		BlockPos pathGoal;
		long repathAt;
		boolean noWalk;
		Vec3 lastPos;
		int stuck;
		long nextSwing;
		long blastUntil;
		long nextBlast;
		long nextMove;
		/** The shield: when it went up, when the last threat was seen, and how often it was raised (tests). */
		long shieldSince = -1L;
		long lastThreat = -1000L;
		int blocks;
		/** The last move it used and how many blade swings landed (tests). */
		String lastMove;
		int swings;
	}

	private static final Map<UUID, Brain> BRAINS = new ConcurrentHashMap<>();

	private KhajiDaTakeover() {
	}

	static void clearSessionState() {
		BRAINS.clear();
	}

	static void forget(UUID id) {
		BRAINS.remove(id);
	}

	/** Khaji-Da has the body (the takeover runs exactly while he is unleashed). */
	public static boolean active(net.minecraft.world.entity.player.Player player) {
		return BlueBeetle.unleashed(player);
	}

	/** Tests: the last move the takeover used (null if none yet). */
	public static String lastMove(ServerPlayer p) {
		Brain b = BRAINS.get(p.getUUID());
		return b == null ? null : b.lastMove;
	}

	/** Tests: how many blade swings the takeover has landed. */
	public static int swings(ServerPlayer p) {
		Brain b = BRAINS.get(p.getUUID());
		return b == null ? 0 : b.swings;
	}

	static void start(ServerPlayer p) {
		BRAINS.put(p.getUUID(), new Brain());
	}

	/** The takeover ended (time up, cleared, suited down): let go of anything held. */
	static void stop(ServerPlayer p) {
		if (BRAINS.remove(p.getUUID()) != null) {
			BlueBeetleAbilities.stopBlast(p);
			BlueBeetleAbilities.lowerShield(p);
			if (BlueBeetle.state(p).fx.takeTarget != -1) {
				BlueBeetleState n = BlueBeetle.state(p).copy();
				n.fx.takeTarget = -1;
				BlueBeetle.save(p, n);
			}
		}
	}

	// ---------------------------------------------------------------- the tick

	static void tick(ServerPlayer p) {
		if (!active(p) || !p.isAlive()) {
			stop(p);
			return;
		}
		Brain b = BRAINS.computeIfAbsent(p.getUUID(), k -> new Brain());
		long now = p.level().getGameTime();
		LivingEntity t = pickTarget(p, b);
		syncTarget(p, t);
		if (t == null) {
			BlueBeetleAbilities.stopBlast(p);
			b.blastUntil = 0L;
			brake(p);
			if (b.noTargetSince < 0L) {
				b.noTargetSince = now;
			} else if (now - b.noTargetSince >= BlueBeetleConfig.TAKEOVER_CLEAR_TICKS) {
				KhajiDa.speak(p, KhajiDa.Topic.TAKEOVER_CLEAR);
				BlueBeetleAbilities.endUnleashed(p, false);
				stop(p);
			}
			return;
		}
		b.noTargetSince = -1L;
		aim(p, t);
		Vec3 to = t.position().subtract(p.position());
		double gap = Math.max(0.0, Math.sqrt(to.x * to.x + to.z * to.z) - p.getBbWidth() * 0.5 - t.getBbWidth() * 0.5);
		if (guard(p, b, now)) {
			move(p, b, t, gap, to.y, now);
			return; // behind the shield: no attacking until it comes down
		}
		fight(p, b, t, gap, to.y, now);
		aim(p, t);
		move(p, b, t, gap, to.y, now);
	}

	private static LivingEntity pickTarget(ServerPlayer p, Brain b) {
		if (b.target != -1 && p.level().getEntity(b.target) instanceof LivingEntity cur && cur.isAlive()
				&& cur.distanceToSqr(p) <= Math.pow(BlueBeetleConfig.TAKEOVER_RANGE + 8.0, 2) && BlueBeetleCombat.isHostile(p, cur)) {
			return cur;
		}
		LivingEntity best = null;
		double bestSq = Double.MAX_VALUE;
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(BlueBeetleConfig.TAKEOVER_RANGE),
				e -> e != p && e.isAlive() && BlueBeetleCombat.isHostile(p, e))) {
			double d = e.distanceToSqr(p);
			if (d < bestSq && d <= BlueBeetleConfig.TAKEOVER_RANGE * BlueBeetleConfig.TAKEOVER_RANGE) {
				bestSq = d;
				best = e;
			}
		}
		if (best == null || best.getId() != b.target) {
			b.path = null; // a new target: plan afresh
			b.repathAt = 0L;
			b.noWalk = false;
		}
		b.target = best == null ? -1 : best.getId();
		return best;
	}

	private static void syncTarget(ServerPlayer p, LivingEntity t) {
		int id = t == null ? -1 : t.getId();
		if (BlueBeetle.state(p).fx.takeTarget != id) {
			BlueBeetleState n = BlueBeetle.state(p).copy();
			n.fx.takeTarget = id;
			BlueBeetle.save(p, n);
		}
	}

	/** Turns him at the target's middle (its eyes when it is close), led a little by its movement. */
	static void aim(ServerPlayer p, LivingEntity t) {
		Vec3 v = t.getDeltaMovement();
		Vec3 at = t.position().add(v.x * 2.0, t.getBbHeight() * 0.6, v.z * 2.0);
		Vec3 d = at.subtract(p.getEyePosition());
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		float yaw = flat < 1.0e-4 ? p.getYRot() : (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0f;
		float pitch = Mth.clamp((float) (-Mth.atan2(d.y, Math.max(1.0e-4, flat)) * Mth.RAD_TO_DEG), -85.0f, 85.0f);
		p.setYRot(yaw);
		p.setYHeadRot(yaw);
		p.yBodyRot = yaw;
		p.setXRot(pitch);
	}

	// ---------------------------------------------------------------- blocking

	/** A threat the shield should take: an incoming projectile, or a hostile closing in to hit him. */
	private record Threat(Vec3 from, boolean projectile) {
	}

	/**
	 * v0.16.5 (user: "make the khaji-da ai use the shield to block attacks aswell"): the shield goes up (when it is off
	 * cooldown) as something is shot at him or a hostile next to him is about to hit, turned toward it; it comes down
	 * again to attack -- after a moment behind it, once nothing is incoming -- or when things calm down. True while it
	 * is held (no attacking then).
	 */
	private static boolean guard(ServerPlayer p, Brain b, long now) {
		Threat threat = threat(p);
		if (threat != null) {
			b.lastThreat = now;
		}
		if (!BlueBeetle.shieldUp(p)) {
			b.shieldSince = -1L;
			if (threat == null || !ready(p, BlueBeetleAbilities.SHIELD) || busy(p)) {
				return false;
			}
			BlueBeetleAbilities.stopBlast(p);
			b.blastUntil = 0L;
			face(p, threat.from());
			BlueBeetleAbilities.raiseShield(p);
			if (!BlueBeetle.shieldUp(p)) {
				return false;
			}
			b.shieldSince = now;
			b.blocks++;
			b.lastMove = BlueBeetleAbilities.SHIELD;
		}
		if (threat != null) {
			face(p, threat.from());
		}
		boolean calm = now - b.lastThreat > 6;
		boolean strike = now - b.shieldSince >= 15 && (threat == null || !threat.projectile());
		if (calm || strike) {
			BlueBeetleAbilities.lowerShield(p); // down to fight back
			return false;
		}
		return true;
	}

	private static Threat threat(ServerPlayer p) {
		Vec3 me = p.position().add(0, p.getBbHeight() * 0.5, 0);
		for (net.minecraft.world.entity.projectile.Projectile proj : p.level().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
				p.getBoundingBox().inflate(12.0), pr -> pr.getOwner() != p && pr.isAlive())) {
			Vec3 v = proj.getDeltaMovement();
			if (v.lengthSqr() < 0.09) {
				continue; // lying still (an arrow in the ground)
			}
			Vec3 rel = me.subtract(proj.position());
			Vec3 dir = v.normalize();
			double along = rel.dot(dir);
			if (along <= 0 || along > 12.0) {
				continue; // going away, or still far off
			}
			if (rel.subtract(dir.scale(along)).length() < 1.6) {
				return new Threat(proj.position(), true);
			}
		}
		for (net.minecraft.world.entity.Mob m : p.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(3.0),
				m -> m.isAlive() && m.getTarget() == p && BlueBeetleCombat.isHostile(p, m))) {
			double reach = Math.sqrt(m.distanceToSqr(p)) - m.getBbWidth() * 0.5 - p.getBbWidth() * 0.5;
			if (reach <= 1.6 || m.swinging) {
				return new Threat(m.position().add(0, m.getBbHeight() * 0.5, 0), false);
			}
		}
		return null;
	}

	/** Turns him to face {@code at} (so the shield, which only covers the front, takes it). */
	private static void face(ServerPlayer p, Vec3 at) {
		Vec3 d = at.subtract(p.getEyePosition());
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		if (flat < 1.0e-4) {
			return;
		}
		float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0f;
		p.setYRot(yaw);
		p.setYHeadRot(yaw);
		p.yBodyRot = yaw;
		p.setXRot(Mth.clamp((float) (-Mth.atan2(d.y, flat) * Mth.RAD_TO_DEG), -85.0f, 85.0f));
	}

	/** Tests: how many times the takeover raised the shield. */
	public static int blocks(ServerPlayer p) {
		Brain b = BRAINS.get(p.getUUID());
		return b == null ? 0 : b.blocks;
	}

	// ---------------------------------------------------------------- fighting

	private static boolean ready(ServerPlayer p, String id) {
		return BlueBeetle.cooldownRemaining(p, id) <= 0;
	}

	private static boolean busy(ServerPlayer p) {
		BlueBeetleState s = BlueBeetle.state(p);
		return s.fx.chargeStart != 0L || BlueBeetleAbilities.running(p, BlueBeetleAbilities.HAMMER)
				|| BlueBeetleAbilities.running(p, BlueBeetleAbilities.SPIKES) || BlueBeetleAbilities.running(p, BlueBeetleAbilities.GRAB);
	}

	private static void fight(ServerPlayer p, Brain b, LivingEntity t, double gap, double dy, long now) {
		if (b.blastUntil > now) {
			if (gap < 2.0 || !clearLine(p, t)) {
				BlueBeetleAbilities.stopBlast(p);
				b.blastUntil = 0L;
			}
			return; // keep firing
		}
		if (b.blastUntil != 0L) {
			BlueBeetleAbilities.stopBlast(p);
			b.blastUntil = 0L;
		}
		if (busy(p)) {
			return;
		}
		boolean melee = gap <= 2.2 && Math.abs(dy) < 2.5;
		// up close the blade is always out, and swings between the moves
		if (melee) {
			if (!BlueBeetle.state(p).fx.blade && ready(p, BlueBeetleAbilities.BLADE)) {
				BlueBeetleAbilities.toggleBlade(p);
			}
			if (now >= b.nextSwing && BlueBeetle.state(p).fx.blade) {
				b.nextSwing = now + BlueBeetleConfig.TAKEOVER_SWING_TICKS;
				float dmg = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE);
				if (BlueBeetleCombat.strike(p, t, p.position(), dmg, 0.4, 0.05)) {
					b.swings++;
				}
				p.swing(InteractionHand.MAIN_HAND, true);
			}
		}
		if (now < b.nextMove) {
			return;
		}
		String used = null;
		if (surrounded(p) >= 3 && ready(p, BlueBeetleAbilities.PULSE)) {
			BlueBeetleAbilities.pulse(p);
			used = BlueBeetleAbilities.PULSE;
		} else if (melee && ready(p, BlueBeetleAbilities.HAMMER)) {
			BlueBeetleAbilities.hammer(p);
			used = BlueBeetleAbilities.HAMMER;
		} else if (!melee && p.onGround() && t.onGround() && Math.abs(dy) < 1.5 && gap <= 12.0 && ready(p, BlueBeetleAbilities.SPIKES)) {
			BlueBeetleAbilities.spikes(p);
			used = BlueBeetleAbilities.SPIKES;
		} else if (!melee && gap >= 3.0 && gap <= BlueBeetleConfig.GRAB_RANGE - 1.0 && clearLine(p, t) && ready(p, BlueBeetleAbilities.GRAB)) {
			BlueBeetleAbilities.grab(p);
			used = BlueBeetleAbilities.GRAB;
		} else if (gap >= 8.0 && clearLine(p, t) && ready(p, BlueBeetleAbilities.CANNON)) {
			BlueBeetleAbilities.cannon(p);
			used = BlueBeetleAbilities.CANNON;
		} else if (gap >= 4.0 && ready(p, BlueBeetleAbilities.SWARM)) {
			BlueBeetleAbilities.swarm(p);
			used = BlueBeetleAbilities.SWARM;
		} else if (gap >= 3.0 && clearLine(p, t) && now >= b.nextBlast && ready(p, BlueBeetleAbilities.BLAST)) {
			BlueBeetleAbilities.startBlast(p);
			b.blastUntil = now + 22L;
			b.nextBlast = now + 50L;
			used = BlueBeetleAbilities.BLAST;
		}
		if (used != null) {
			b.lastMove = used;
			b.nextMove = now + 8L;
		}
	}

	private static int surrounded(ServerPlayer p) {
		return p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(3.5),
				e -> e != p && e.isAlive() && BlueBeetleCombat.isHostile(p, e)).size();
	}

	static boolean clearLine(ServerPlayer p, LivingEntity t) {
		Vec3 from = p.getEyePosition();
		Vec3 to = t.position().add(0, t.getBbHeight() * 0.6, 0);
		HitResult hit = p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS;
	}

	// ---------------------------------------------------------------- moving

	/** Velocity for this tick, sent to his client too (it owns his movement). */
	private static void drive(ServerPlayer p, Vec3 v) {
		AbilityHelpers.launchSelf(p, v);
	}

	private static void brake(ServerPlayer p) {
		Vec3 v = p.getDeltaMovement();
		boolean flying = BlueBeetle.state(p).flying;
		drive(p, new Vec3(v.x * 0.3, flying ? v.y * 0.3 : Math.min(v.y, 0.0), v.z * 0.3));
	}

	private static void move(ServerPlayer p, Brain b, LivingEntity t, double gap, double dy, long now) {
		BlueBeetleState s = BlueBeetle.state(p);
		// stand and shoot / charge / smash: no running about mid-move
		boolean planted = b.blastUntil > now || s.fx.chargeStart != 0L || BlueBeetleAbilities.running(p, BlueBeetleAbilities.HAMMER)
				|| BlueBeetleAbilities.running(p, BlueBeetleAbilities.SPIKES);
		Vec3 here = p.position();
		if (b.lastPos != null) {
			double moved = here.distanceTo(b.lastPos);
			boolean meantTo = !planted && gap > 2.0;
			b.stuck = meantTo && moved < 0.04 ? b.stuck + 1 : Math.max(0, b.stuck - 2);
		}
		b.lastPos = here;
		if (s.flying) {
			if (planted) {
				brake(p);
			} else {
				fly(p, b, t, gap, dy);
			}
			return;
		}
		if (planted || gap <= 1.6) {
			brake(p);
			return;
		}
		boolean wantWings = dy > 2.6 || b.noWalk || b.stuck > 25 || p.isInWater();
		if (wantWings) {
			takeOff(p);
			fly(p, b, t, gap, dy);
			return;
		}
		walk(p, b, t, now);
	}

	private static void takeOff(ServerPlayer p) {
		BlueBeetleState n = BlueBeetle.state(p).copy();
		n.flying = true;
		BlueBeetle.save(p, n);
		BlueBeetle.openWings(p);
	}

	/** Straight at it through the air, climbing over whatever is in the way; settles beside it at its height. */
	private static void fly(ServerPlayer p, Brain b, LivingEntity t, double gap, double dy) {
		Vec3 goal = t.position().add(0, Math.max(0.2, t.getBbHeight() * 0.3), 0);
		Vec3 d = goal.subtract(p.position());
		Vec3 flat = new Vec3(d.x, 0, d.z);
		double speed = BlueBeetleConfig.TAKEOVER_FLY_SPEED;
		Vec3 v;
		if (gap <= 1.6) {
			v = new Vec3(0, Mth.clamp(d.y * 0.2, -0.3, 0.3), 0);
		} else {
			Vec3 dir = d.normalize();
			v = dir.scale(Math.min(speed, 0.15 + gap * 0.12));
			Vec3 fdir = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
			// a wall ahead: go up and over it
			Vec3 chest = p.position().add(0, 1.0, 0);
			HitResult hit = p.level().clip(new ClipContext(chest, chest.add(fdir.scale(2.0)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			Vec3 feet = p.position().add(0, 0.2, 0);
			HitResult low = p.level().clip(new ClipContext(feet, feet.add(fdir.scale(1.6)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			if (hit.getType() != HitResult.Type.MISS || low.getType() != HitResult.Type.MISS || b.stuck > 10) {
				v = new Vec3(fdir.x * speed * 0.3, speed * 0.8, fdir.z * speed * 0.3);
			}
		}
		drive(p, v);
		if (b.stuck > 60) {
			b.stuck = 0;
		}
	}

	/** Along the A* path: re-planned every second or when the target has moved off the path's end. */
	private static void walk(ServerPlayer p, Brain b, LivingEntity t, long now) {
		BlockPos goal = t.blockPosition();
		boolean replan = b.path == null || now >= b.repathAt || b.pathGoal == null || b.pathGoal.distSqr(goal) > 4;
		if (replan && p.onGround()) {
			b.path = BeetlePath.find(p.level(), p.blockPosition(), goal, 1.5, BlueBeetleConfig.TAKEOVER_PATH_NODES);
			b.step = 0;
			b.pathGoal = goal;
			b.repathAt = now + 20L;
			b.noWalk = b.path == null;
			if (b.noWalk) {
				return; // no way on foot: the wings next tick
			}
		}
		Vec3 here = p.position();
		Vec3 aim = t.position();
		BlockPos next = null;
		if (b.path != null) {
			while (b.step < b.path.size()) {
				BlockPos c = b.path.get(b.step);
				double dx = c.getX() + 0.5 - here.x, dz = c.getZ() + 0.5 - here.z;
				if (dx * dx + dz * dz < 0.35 * 0.35 && Math.abs(c.getY() - here.y) < 1.2) {
					b.step++;
				} else {
					break;
				}
			}
			if (b.step < b.path.size()) {
				next = b.path.get(b.step);
				aim = new Vec3(next.getX() + 0.5, next.getY(), next.getZ() + 0.5);
			}
		}
		Vec3 d = aim.subtract(here);
		Vec3 flat = new Vec3(d.x, 0, d.z);
		Vec3 dir = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
		double speed = BlueBeetleConfig.TAKEOVER_RUN_SPEED;
		double vy;
		Vec3 v = p.getDeltaMovement();
		if (p.onGround()) {
			boolean climb = next != null && next.getY() > Math.floor(here.y + 0.01) + 0.5;
			vy = climb || b.stuck > 8 ? 0.45 : -0.08;
		} else {
			vy = Math.max(v.y - 0.08, -2.0) * 0.98;
		}
		drive(p, new Vec3(dir.x * speed, vy, dir.z * speed));
		p.setSprinting(true);
	}

	/** Tests: the walk path the brain is following (null if none). */
	public static List<BlockPos> path(ServerPlayer p) {
		Brain b = BRAINS.get(p.getUUID());
		return b == null ? null : b.path;
	}
}
