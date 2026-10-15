package com.projecthero.mod.hulk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hulk.data.HulkState;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.4 (user): "for the hulk rampage i think he struggles to like hit things like m1, upgrade his aiming when it comes to
 * that and make him use moves more aswell". How a rampaging Hulk fights one target, every tick:
 * <ul>
 *   <li><b>Aim</b> -- the server turns him at the target's middle (led by its velocity, {@link #LEAD_TICKS}) every tick, and
 *       the owner's client does the same from the synced {@code Wild.target} (HulkRampageClient), so the two never fight
 *       over his rotation and every cone / raycast move goes where the target is.</li>
 *   <li><b>Punches</b> -- not the client's crosshair: when the target's box (grown a little for its speed) is inside his
 *       reach -- which grows with his size -- from his chest or his eyes, and nothing solid is in between, he lands a
 *       full-strength {@code player.attack} (the swing timer is topped up first, so it is never a weak 20% tap).</li>
 *   <li><b>Moves</b> -- every {@code rampageMoveMinTicks..MaxTicks} (2-4 s) one of the kit, picked by range: Super Punch,
 *       Ground Smash, Launch Uppercut, Thunderclap or Grab + throw up close; Super Punch / Thunderclap / Fault-line Stomp at
 *       middle range; Fault-line Stomp or the Bulldozer Charge to close a gap; a Super Leap when it is far. Each has its own
 *       rampage cooldown, so it stays varied. They run through the real ability code ({@link HulkAbilities#driven}) so every
 *       animation, sound and effect plays.</li>
 * </ul>
 */
final class HulkRampageAI {
	/** How far ahead (ticks) he leads a moving target. */
	static final double LEAD_TICKS = 3.0;

	/** Per-move rampage cooldowns (ticks), separate from the player's own. */
	private static final Map<String, Integer> COOLDOWNS = Map.of(
			HulkAbilities.SUPER_PUNCH, 100,
			HulkAbilities.GROUND_SMASH, 140,
			HulkAbilities.UPPERCUT, 120,
			HulkAbilities.THUNDERCLAP, 160,
			HulkAbilities.FAULT_STOMP, 140,
			HulkAbilities.BULLDOZE, 160,
			HulkAbilities.GRAB, 300);

	/** What the AI remembers between ticks (one per rampaging player, inside HulkControl's bookkeeping). */
	static final class Brain {
		long nextSwing;
		long nextMove;
		long nextLeap;
		int stuckTicks;
		Vec3 lastPos;
		long grabbedAt = -1L;
		/** The last move he used (tests). */
		String lastMove;
		/** How many punches landed (tests). */
		int punchesLanded;
		final Map<String, Long> ready = new HashMap<>();
	}

	private HulkRampageAI() {
	}

	// ---------------------------------------------------------------- aim

	/** Where to aim at {@code t}: its middle, led by its velocity. */
	static Vec3 aimPoint(LivingEntity t) {
		Vec3 v = t.getDeltaMovement();
		return t.position().add(v.x * LEAD_TICKS, t.getBbHeight() * 0.5 + Math.max(-0.5, Math.min(0.5, v.y * LEAD_TICKS)), v.z * LEAD_TICKS);
	}

	/** Turns him to look at {@code at} -- from his eyes, or ({@code fromEye} false) from his chest -- pitch no lower than {@code minPitch} below level. */
	static void aim(ServerPlayer p, Vec3 at, boolean fromEye, float minPitchDown) {
		Vec3 from = fromEye ? p.getEyePosition() : HulkAbilities.chest(p);
		Vec3 d = at.subtract(from);
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		float yaw = flat < 1.0e-4 ? p.getYRot() : (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0f;
		float pitch = (float) (-Mth.atan2(d.y, Math.max(1.0e-4, flat)) * Mth.RAD_TO_DEG);
		pitch = Mth.clamp(pitch, -80.0f, Math.min(85.0f, minPitchDown));
		face(p, yaw, pitch);
	}

	static void face(ServerPlayer p, float yaw, float pitch) {
		p.setYRot(yaw);
		p.setYHeadRot(yaw);
		p.yBodyRot = yaw;
		p.setXRot(pitch);
	}

	// ---------------------------------------------------------------- the punch

	/** The target's box, grown a little for its speed (a fleeing mob is still caught). */
	private static AABB reachBox(LivingEntity t) {
		Vec3 v = t.getDeltaMovement();
		double grow = 0.3 + Math.min(1.0, Math.sqrt(v.x * v.x + v.z * v.z) * 2.0);
		return t.getBoundingBox().inflate(grow, 0.3, grow);
	}

	private static double distSq(AABB b, Vec3 p) {
		double dx = Math.max(Math.max(b.minX - p.x, 0.0), p.x - b.maxX);
		double dy = Math.max(Math.max(b.minY - p.y, 0.0), p.y - b.maxY);
		double dz = Math.max(Math.max(b.minZ - p.z, 0.0), p.z - b.maxZ);
		return dx * dx + dy * dy + dz * dz;
	}

	/** His reach right now (it follows his size) plus a little slack for a big model. */
	static double reach(ServerPlayer p) {
		return p.entityInteractionRange() + 0.5;
	}

	/** {@code t} is inside his reach from his chest, eyes or knees -- high or low targets alike. */
	static boolean inReach(ServerPlayer p, LivingEntity t) {
		AABB box = reachBox(t);
		double r = reach(p);
		double r2 = r * r;
		Vec3 feet = p.position();
		double h = p.getBbHeight();
		for (double f : new double[] { 0.55, 0.9, 0.2 }) {
			if (distSq(box, feet.add(0, h * f, 0)) <= r2) {
				return true;
			}
		}
		return false;
	}

	/** Nothing solid between him and {@code t} (chest to its middle, or eyes to its eyes). No punching through walls. */
	static boolean clearPath(ServerPlayer p, LivingEntity t) {
		Vec3 mid = t.position().add(0, t.getBbHeight() * 0.5, 0);
		return clear(p, HulkAbilities.chest(p), mid) || clear(p, p.getEyePosition(), t.getEyePosition())
				|| clear(p, p.position().add(0, Math.min(1.0, p.getBbHeight() * 0.2), 0), t.position().add(0, Math.min(0.5, t.getBbHeight() * 0.3), 0));
	}

	private static boolean clear(ServerPlayer p, Vec3 from, Vec3 to) {
		HitResult hit = p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(from) >= from.distanceToSqr(to) - 0.25;
	}

	/**
	 * A full-strength punch: the swing timer is topped up so {@code player.attack} lands its whole damage (the old rampage
	 * reset it first, so every blow was a 20% tap -- and Fabric's AttackEntityCallback, which fires inside attack(), was
	 * refusing every rampage punch outright via HulkDamage). True if it hurt the target.
	 */
	static boolean punch(ServerPlayer p, LivingEntity t) {
		if (!inReach(p, t) || !clearPath(p, t)) {
			return false;
		}
		aim(p, aimPoint(t), true, 85.0f);
		((com.projecthero.mod.mixin.LivingEntityAccessor) p).projecthero$setAttackStrengthTicker(1000);
		float before = t.getHealth();
		int hurtBefore = t.hurtTime;
		// driven: Fabric's AttackEntityCallback runs inside attack() and HulkDamage refuses a rampaging player's own clicks
		HulkAbilities.driven(p, () -> p.attack(t));
		p.swing(com.projecthero.mod.hulk.gladiator.GladiatorSwing.next(p, InteractionHand.MAIN_HAND), true);
		return !t.isAlive() || t.getHealth() < before || t.hurtTime > hurtBefore;
	}

	// ---------------------------------------------------------------- one tick of the fight

	/**
	 * One tick of fighting {@code target}. {@code others} picks somewhere to throw what he grabbed. Movement included unless he
	 * is mid-move (a leap, the charge, a slam).
	 */
	static void fight(ServerPlayer p, Brain b, LivingEntity target, long now, java.util.function.Supplier<LivingEntity> others) {
		HulkConfig.Control cfg = HulkConfig.control();
		HulkState s = Hulk.state(p);
		Vec3 at = aimPoint(target);
		// v0.16.4 (user): "upgrade his aiming" -- face it exactly, every tick (synced through Wild.target for the client)
		aim(p, at, false, 30.0f);
		if (HulkGrab.holding(p)) {
			if (b.grabbedAt >= 0L && now - b.grabbedAt >= 14L && !HulkAbilities.busy(p)) {
				LivingEntity next = others.get();
				Vec3 throwAt = next != null ? aimPoint(next)
						: p.getEyePosition().add(Vec3.directionFromRotation(0.0f, p.getYRot() + (float) (p.getRandom().nextGaussian() * 60.0)).scale(20.0));
				aim(p, throwAt, true, 10.0f);
				HulkAbilities.driven(p, () -> HulkGrab.press(p, false));
				b.grabbedAt = -1L;
				b.lastMove = "throw";
				b.nextMove = now + 30L;
			}
			return;
		}
		boolean reach = inReach(p, target);
		boolean busy = HulkAbilities.busy(p) || s.leaping;
		if (reach && !busy && now >= b.nextSwing) {
			b.nextSwing = now + Math.max(4, cfg.rampageSwingTicks);
			if (punch(p, target)) {
				b.punchesLanded++;
			}
			aim(p, at, false, 30.0f);
		}
		Vec3 to = target.position().subtract(p.position());
		double flat = Math.sqrt(to.x * to.x + to.z * to.z);
		double gap = Math.max(0.0, flat - p.getBbWidth() * 0.5 - target.getBbWidth() * 0.5);
		if (!busy && p.onGround() && now >= b.nextMove) {
			if (tryMove(p, b, target, gap, to.y, reach, now)) {
				int min = Math.max(10, cfg.rampageMoveMinTicks);
				int max = Math.max(min, cfg.rampageMoveMaxTicks);
				b.nextMove = now + min + p.getRandom().nextInt(max - min + 1);
				return;
			}
			b.nextMove = now + 10L; // nothing fits from here: look again soon
		}
		if (busy || HulkAbilities.charging(p)) {
			return; // mid-leap / mid-charge: the move carries him
		}
		move(p, b, target, at, gap, to.y, reach, now);
	}

	/** Picks and fires one move that suits the range. True if one fired. */
	private static boolean tryMove(ServerPlayer p, Brain b, LivingEntity target, double gap, double dy, boolean reach, long now) {
		HulkConfig.Abilities a = HulkConfig.abilities();
		List<String> options = new ArrayList<>();
		boolean level = Math.abs(dy) < 2.5;
		if (reach) {
			options.add(HulkAbilities.SUPER_PUNCH);
			options.add(HulkAbilities.UPPERCUT);
			options.add(HulkAbilities.THUNDERCLAP);
			if (level && gap <= a.groundSmashRadius) {
				options.add(HulkAbilities.GROUND_SMASH);
			}
			if (HulkGrab.grabbable(p, target) && gap <= a.grabRange) {
				options.add(HulkAbilities.GRAB);
			}
		} else if (gap <= a.superPunchRange) {
			options.add(HulkAbilities.SUPER_PUNCH);
			options.add(HulkAbilities.THUNDERCLAP);
			if (level) {
				options.add(HulkAbilities.FAULT_STOMP);
			}
		} else if (gap <= Math.max(a.faultLength, a.bulldozeBlocks + 4.0)) {
			if (level) {
				options.add(HulkAbilities.FAULT_STOMP);
				options.add(HulkAbilities.BULLDOZE);
			}
			if (gap <= a.clapRange) {
				options.add(HulkAbilities.THUNDERCLAP);
			}
		}
		options.removeIf(id -> b.ready.getOrDefault(id, Long.MIN_VALUE) > now);
		while (!options.isEmpty()) {
			String id = options.remove(p.getRandom().nextInt(options.size()));
			if (fire(p, b, target, id, now)) {
				b.ready.put(id, now + COOLDOWNS.getOrDefault(id, 120));
				b.lastMove = id;
				return true;
			}
		}
		return false;
	}

	/** Fires one move through the real ability code. True if it started. */
	static boolean fire(ServerPlayer p, Brain b, LivingEntity target, String id, long now) {
		Vec3 at = aimPoint(target);
		switch (id) {
			case HulkAbilities.SUPER_PUNCH -> {
				aim(p, at, false, 20.0f); // never straight down into the ground
				HulkAbilities.driven(p, () -> HulkAbilities.superPunch(p));
			}
			case HulkAbilities.GROUND_SMASH -> HulkAbilities.driven(p, () -> HulkAbilities.groundSmash(p));
			case HulkAbilities.UPPERCUT -> {
				aim(p, at, true, 85.0f);
				HulkAbilities.driven(p, () -> HulkAbilities.uppercut(p));
			}
			case HulkAbilities.THUNDERCLAP -> {
				aim(p, at, true, 40.0f);
				HulkAbilities.driven(p, () -> HulkAbilities.thunderclap(p));
			}
			case HulkAbilities.FAULT_STOMP -> HulkAbilities.driven(p, () -> HulkAbilities.faultStomp(p));
			case HulkAbilities.BULLDOZE -> {
				HulkAbilities.driven(p, () -> HulkAbilities.bulldoze(p));
				return HulkAbilities.charging(p);
			}
			case HulkAbilities.GRAB -> {
				aim(p, at, true, 85.0f);
				HulkAbilities.driven(p, () -> HulkGrab.press(p, false));
				if (HulkGrab.holding(p)) {
					b.grabbedAt = now;
					return true;
				}
				return false;
			}
			default -> {
				return false;
			}
		}
		HulkState s = Hulk.state(p);
		return s.animStart == now && s.animId != HulkState.ANIM_NONE;
	}

	/** Closing in: leap when it is far, run otherwise, stand and pound when it is in reach; hop or smash through when stuck. */
	private static void move(ServerPlayer p, Brain b, LivingEntity target, Vec3 at, double gap, double dy, boolean reach, long now) {
		Vec3 to = at.subtract(p.position());
		Vec3 flat = new Vec3(to.x, 0, to.z);
		double dist = flat.length();
		Vec3 dir = dist < 1.0e-3 ? HulkAbilities.flatLook(p) : flat.scale(1.0 / dist);
		if (reach) {
			Vec3 v = p.getDeltaMovement();
			AbilityHelpers.launchSelf(p, new Vec3(v.x * 0.3, Math.min(v.y, 0.0), v.z * 0.3));
			b.stuckTicks = 0;
			return;
		}
		if (p.onGround() && now >= b.nextLeap && (gap > 16.0 || (dy > 3.0 && gap < 10.0))) {
			// v0.16.4 (user): the Super Leap -- at it, up a cliff to it, across a gap
			leapAt(p, dir, gap, dy);
			b.nextLeap = now + 50L;
			b.stuckTicks = 0;
			b.lastMove = HulkAbilities.SUPER_LEAP;
			return;
		}
		runAt(p, b, dir, now);
	}

	/** A Super Leap {@code gap} blocks along {@code dir} (steeper when the target is above him). */
	static void leapAt(ServerPlayer p, Vec3 dir, double gap, double dy) {
		HulkState n = Hulk.state(p).copy();
		double blocks = Math.max(8.0, Math.min(140.0, gap + 1.0));
		double rise = Math.max(0.2, Math.min(1.5, dy / Math.max(4.0, gap)));
		HulkAbilities.launch(p, n, blocks, new Vec3(dir.x, rise, dir.z).normalize());
		Hulk.save(p, n);
		if (dy > 2.0) {
			// the arc solves for level ground: add the climb
			Vec3 v = p.getDeltaMovement();
			AbilityHelpers.launchSelf(p, new Vec3(v.x, v.y + Math.min(1.4, Math.sqrt(2.0 * p.getGravity() * dy)), v.z));
		}
	}

	/** Runs along {@code dir}, faster the bigger he is; hops when blocked, smashes through when truly stuck. */
	static void runAt(ServerPlayer p, Brain b, Vec3 dir, long now) {
		double speed = 0.4 + 0.06 * Math.max(0.0, Hulk.scaleBonus(p));
		double vy = p.onGround() ? -0.05 : Math.max(p.getDeltaMovement().y - 0.08, -1.2);
		Vec3 here = p.position();
		if (b.lastPos != null && p.onGround()) {
			double moved = Math.sqrt(Math.pow(here.x - b.lastPos.x, 2) + Math.pow(here.z - b.lastPos.z, 2));
			b.stuckTicks = moved < speed * 0.25 ? b.stuckTicks + 1 : Math.max(0, b.stuckTicks - 2);
		}
		b.lastPos = here;
		if (b.stuckTicks >= 12 && p.onGround()) {
			if (b.stuckTicks < 30) {
				vy = 0.75; // a hop over it first
			} else {
				// truly stuck: smash through what is in the way (the only blocks a normal rampage breaks)
				HulkAbilities.breakAhead(p, dir, true);
				b.stuckTicks = 12;
				ServerLevel level = (ServerLevel) p.level();
				level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS, 0.8f, 0.6f);
			}
		}
		AbilityHelpers.launchSelf(p, new Vec3(dir.x * speed, vy, dir.z * speed));
	}
}
