package com.projecthero.mod.oathbreaker.entity;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.oathbreaker.OathbreakerFx;
import com.projecthero.mod.oathbreaker.OathbreakerTuning;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Oathbreaker's attack state machine (v0.14.0; split out of {@link OathbreakerEntity}, which keeps
 * lifecycle, phase, poise, sync and animation). Server-only: every method here runs from the entity's
 * {@code aiStep} or {@code hurt} on the logical server.
 *
 * <p>Every attack is a sequence of timed <em>steps</em>: {@link #ticks} counts the current step down and
 * {@link #advance} moves to the next one when it hits zero. Every step length is an
 * {@link OathbreakerTuning} constant that the asset build script checks against its clip length, and
 * every step that swings plays its own clip on the entity's {@code action} controller. Damage always
 * resolves on the clip's <em>contact</em> frame (a few ticks into the strike clip), never on its first
 * frame, so what you see is what hits you.
 *
 * <p>Movement during attacks (dash lunge, backstep hop, leap) is a <em>scripted move</em>: a locked start
 * and end point, driven along a lerp (plus an optional arc) by setting the delta movement every tick,
 * with gravity off. Collisions still apply, but where he'll end up is decided at takeoff -- which is
 * exactly what makes a leap dodgeable.
 */
final class OathbreakerCombat {
	enum Attack { STANCE_DASH, COMBO, OATH_GUARD, RIPOSTE, BACKSTEP, LEAPING_CLEAVE, SOUL_REND, CHAINS, JUDGEMENT, EXECUTION, SOUL_SPEAR }

	// ---- anti-cheese state ----
	private int unreachableTicks;
	private int pathCheckTimer;
	private boolean lastPathOk = true;
	private int stuckTicks;
	private Vec3 stuckAnchor;
	/** A one-off cap on the next Leaping Cleave's distance (the short "get out of this pit" hop). */
	private double leapMaxOverride;

	/** Soul Spears in flight (hazards: they keep flying whatever he does next). */
	private static final class Spear {
		Vec3 pos;
		final Vec3 dir;
		double travelled;

		Spear(Vec3 pos, Vec3 dir) {
			this.pos = pos;
			this.dir = dir;
		}
	}

	private final List<Spear> spears = new ArrayList<>();

	/** Phase 3: their own cooldowns, separate from the shared one. */
	private int judgementCooldown;
	private int executionCooldown;
	/** Judgement: the landing spot -- tracks the target while he hangs, locks when the slam starts. */
	private Vec3 judgementLanding;
	/** Execution: who's held, and how much raw damage OTHER players have dealt him during the hold. */
	private LivingEntity executionVictim;
	private float escapeDamage;

	/** Phase 3 passive: a ghost repeats a Combo/Stance Dash arc from where he stood, 1s later. */
	private record Echo(Vec3 origin, Vec3 forward, double range, double arc, float damage, double knockback, int fireAt) {
	}

	private final List<Echo> echoes = new ArrayList<>();

	/** Judgement's lingering soul-fire circle. */
	private static final class SoulRing {
		final Vec3 center;
		int ticksLeft;

		SoulRing(Vec3 center, int ticksLeft) {
			this.center = center;
			this.ticksLeft = ticksLeft;
		}
	}

	private final List<SoulRing> soulRings = new ArrayList<>();

	private final OathbreakerEntity boss;

	private Attack active;
	private int step;
	private int ticks;
	private int cooldown;

	/** Combo: hits already resolved in the current combo, and how many this one has (4, or 5 from phase 2). */
	private int comboHit;
	private int comboHits;
	/** Combo, phase 2+: true while holding a wind-up for its random extra delay. */
	private boolean comboHolding;
	/** Stance Dash, phase 2+: already feinted once this dash. */
	private boolean feinted;

	/** Soul Rend: the line's direction, locked at the start of the telegraph. */
	private Vec3 rendDir;
	/** Eruption lines still walking outward -- ticked independently of the attack that launched them, so a
	 * stagger or a phase transition mid-line doesn't freeze eruptions that are already on their way. */
	private final List<RendLine> rendLines = new ArrayList<>();

	/** Chains: the flying head, its locked direction, how far it's gone, and who it caught. */
	private Vec3 chainHead;
	private Vec3 chainDir;
	private double chainTravel;
	private LivingEntity chainVictim;
	private int chainRollTimer;

	private static final class RendLine {
		final Vec3 origin;
		final Vec3 dir;
		int age;
		int next = 1;
		final java.util.Set<Integer> hit = new java.util.HashSet<>();

		RendLine(Vec3 origin, Vec3 dir) {
			this.origin = origin;
			this.dir = dir;
		}
	}
	/** Riposte: whoever got parried -- the thrust is aimed at them. */
	private LivingEntity riposteVictim;
	/** Leap: locked at takeoff. */
	private Vec3 leapTarget;

	/** Consecutive ticks the target has spent in the Leaping Cleave band. */
	private int kiteTicks;
	/** {@code boss.tickCount} of the last time the current target hit him (Oath Guard eligibility). */
	private int lastHitByTargetTick = -100000;

	// scripted move
	private Vec3 moveStart;
	private Vec3 moveEnd;
	private int moveTicks;
	private int moveElapsed;
	private double moveArc;

	OathbreakerCombat(OathbreakerEntity boss) {
		this.boss = boss;
	}

	boolean isAttacking() {
		return active != null;
	}

	Attack activeAttack() {
		return active;
	}

	// ---------------------------------------------------------------- per-tick

	void tick(ServerLevel server) {
		tickScriptedMove();
		if (judgementCooldown > 0) {
			judgementCooldown--;
		}
		if (executionCooldown > 0) {
			executionCooldown--;
		}
		LivingEntity target = boss.getTarget();
		if (target != null && !isValidTarget(target)) {
			// switched to creative/spectator (or died) mid-fight: stop chasing and swinging at them
			boss.setTarget(null);
			target = null;
		}
		trackKiting(target);
		trackCheese(target);
		if (active != null) {
			if (target != null && holdsLookOnTarget()) {
				boss.getLookControl().setLookAt(target, 30.0f, 30.0f);
			}
			Attack before = active;
			int stepBefore = step;
			onHold(server);
			// onHold can end the attack (an Execution victim who vanished, died or left the dimension) or
			// start a new step itself (a chain catching or missing). Either way, don't also count this tick
			// down: an ended attack has nothing to advance (that was a crash), and a step onHold just began
			// starts cleanly next tick, so its elapsed/clip timing lines up like every other step's.
			if (active != before || step != stepBefore) {
				return;
			}
			if (--ticks <= 0) {
				advance(server);
			}
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		if (!isValidTarget(target)) {
			return;
		}
		double dist = boss.distanceTo(target);
		if (stuckTicks >= OathbreakerTuning.STUCK_TICKS) {
			// wedged in a block or a pit and getting nowhere: a short hop toward them to get out
			stuckTicks = 0;
			stuckAnchor = null;
			leapMaxOverride = OathbreakerTuning.UNSTICK_LEAP_MAX_RANGE;
			begin(server, Attack.LEAPING_CLEAVE);
			return;
		}
		if (unreachableTicks >= OathbreakerTuning.UNREACHABLE_TICKS) {
			// pillared up, in water/lava, or simply no path: answer at range. Phase 2+ uses the chains,
			// which don't just hurt -- they drag the cheeser down to him.
			unreachableTicks = 0;
			begin(server, boss.getPhase() == OathbreakerEntity.Phase.KNIGHT ? Attack.SOUL_SPEAR : Attack.CHAINS);
			return;
		}
		if (kiteTicks >= OathbreakerTuning.LEAP_KITE_TICKS) {
			begin(server, Attack.LEAPING_CLEAVE);
			return;
		}
		boolean oathless = boss.getPhase() == OathbreakerEntity.Phase.OATHLESS;
		if (dist <= OathbreakerTuning.ATTACK_TRIGGER_RANGE) {
			// phase 3: Judgement / Execution whenever their own cooldowns are up, 50% each time they're offered
			if (oathless && judgementCooldown <= 0 && boss.getRandom().nextFloat() < OathbreakerTuning.JUDGEMENT_USE_CHANCE) {
				begin(server, Attack.JUDGEMENT);
			} else if (oathless && executionCooldown <= 0 && boss.getRandom().nextFloat() < OathbreakerTuning.EXECUTION_USE_CHANCE) {
				begin(server, Attack.EXECUTION);
			} else {
				begin(server, pickAttack());
			}
			return;
		}
		if (boss.getPhase() != OathbreakerEntity.Phase.KNIGHT
				&& dist >= OathbreakerTuning.CHAIN_MIN_RANGE && dist <= OathbreakerTuning.CHAIN_MAX_RANGE
				&& --chainRollTimer <= 0) {
			chainRollTimer = OathbreakerTuning.CHAIN_ROLL_INTERVAL_TICKS;
			// Judgement also answers range in phase 3 -- it tracks the target from the air anyway
			if (oathless && judgementCooldown <= 0 && boss.getRandom().nextFloat() < OathbreakerTuning.JUDGEMENT_USE_CHANCE) {
				begin(server, Attack.JUDGEMENT);
			} else if (boss.getRandom().nextFloat() < OathbreakerTuning.CHAIN_RANGED_CHANCE) {
				begin(server, Attack.CHAINS);
			}
		}
	}

	/**
	 * Anti-cheese bookkeeping, every tick. "Unreachable" = pillared more than {@link OathbreakerTuning#PILLAR_HEIGHT}
	 * above him, standing in water or lava, or no complete path to them (re-checked every
	 * {@link OathbreakerTuning#PATH_CHECK_INTERVAL_TICKS}). "Stuck" = not attacking, target out of melee
	 * reach, and he's barely moved (or is inside a block) for {@link OathbreakerTuning#STUCK_TICKS}.
	 */
	private void trackCheese(LivingEntity target) {
		if (!isValidTarget(target)) {
			unreachableTicks = 0;
			stuckTicks = 0;
			stuckAnchor = null;
			return;
		}
		if (active == null) {
			if (--pathCheckTimer <= 0) {
				pathCheckTimer = OathbreakerTuning.PATH_CHECK_INTERVAL_TICKS;
				net.minecraft.world.level.pathfinder.Path path = boss.getNavigation().createPath(target, 1);
				lastPathOk = path != null && path.canReach();
			}
			boolean pillared = target.getY() - boss.getY() > OathbreakerTuning.PILLAR_HEIGHT;
			boolean inLiquid = target.isInWater() || target.isInLava();
			unreachableTicks = (pillared || inLiquid || !lastPathOk) ? unreachableTicks + 1 : 0;
		}

		// Stuck keeps counting through ranged attacks (he's no less stuck while throwing spears from the
		// bottom of a pit -- harness: pausing it there made a 5 s unstick take 13 s). Only a scripted move
		// counts as getting somewhere.
		if (isScriptedMoving()) {
			stuckTicks = 0;
			stuckAnchor = null;
			return;
		}
		boolean outOfReach = boss.distanceTo(target) > OathbreakerTuning.ATTACK_TRIGGER_RANGE;
		if (!outOfReach) {
			stuckTicks = 0;
			stuckAnchor = null;
			return;
		}
		if (stuckAnchor == null || boss.position().distanceTo(stuckAnchor) > OathbreakerTuning.STUCK_MOVE_EPSILON) {
			stuckAnchor = boss.position();
			stuckTicks = boss.isInWall() ? stuckTicks + 1 : 0;
		} else {
			stuckTicks++;
		}
	}

	/** Eruption lines already launched keep walking whatever the boss is doing (stagger, transition). */
	void tickHazards(ServerLevel server) {
		for (java.util.Iterator<Spear> it = spears.iterator(); it.hasNext();) {
			if (tickSpear(server, it.next())) {
				it.remove();
			}
		}
		for (java.util.Iterator<RendLine> it = rendLines.iterator(); it.hasNext();) {
			RendLine line = it.next();
			line.age++;
			while (line.next <= OathbreakerTuning.SOUL_REND_LENGTH
					&& line.age >= line.next * OathbreakerTuning.SOUL_REND_TICKS_PER_BLOCK) {
				erupt(server, line, line.next);
				line.next++;
			}
			if (line.next > OathbreakerTuning.SOUL_REND_LENGTH) {
				it.remove();
			}
		}
		for (java.util.Iterator<Echo> it = echoes.iterator(); it.hasNext();) {
			Echo e = it.next();
			if (boss.tickCount >= e.fireAt()) {
				fireEcho(server, e);
				it.remove();
			}
		}
		for (java.util.Iterator<SoulRing> it = soulRings.iterator(); it.hasNext();) {
			SoulRing ring = it.next();
			tickSoulRing(server, ring);
			if (--ring.ticksLeft <= 0) {
				it.remove();
			}
		}
	}

	private void trackKiting(LivingEntity target) {
		if (!isValidTarget(target)) {
			kiteTicks = 0;
			return;
		}
		double d = boss.distanceTo(target);
		if (d >= OathbreakerTuning.LEAP_MIN_RANGE && d <= OathbreakerTuning.LEAP_MAX_RANGE) {
			kiteTicks++;
		} else {
			kiteTicks = 0;
		}
	}

	/** Wind-ups and guards track the target; committed strikes, locked lines and flight don't (that's the
	 * dodge). Soul Rend stops tracking the moment its line locks and the telegraph starts. */
	private boolean holdsLookOnTarget() {
		return switch (active) {
			case STANCE_DASH -> step == 0 || step == 3;
			case COMBO -> step % 2 == 0;
			case OATH_GUARD -> true;
			case LEAPING_CLEAVE -> step == 0;
			case SOUL_REND -> step == 0 && rendDir == null;
			case CHAINS -> step == 0;
			case JUDGEMENT -> step == 1;
			case EXECUTION -> step == 0;
			default -> false;
		};
	}

	static boolean isValidTarget(LivingEntity target) {
		return target != null && target.isAlive()
				&& !(target instanceof Player p && (p.isSpectator() || p.isCreative()));
	}

	// ---------------------------------------------------------------- selection

	/** Weighted pick from the current phase's melee pool (the Oath Guard only while the target has hit him
	 * recently). Chains and the Leaping Cleave aren't in here -- they're range-triggered, see {@link #tick}. */
	private Attack pickAttack() {
		List<Attack> pool = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		boolean guardEligible = boss.tickCount - lastHitByTargetTick <= OathbreakerTuning.GUARD_ELIGIBLE_AFTER_HIT_TICKS;
		if (boss.getPhase() == OathbreakerEntity.Phase.KNIGHT) {
			add(pool, weights, Attack.STANCE_DASH, OathbreakerTuning.WEIGHT_P1_STANCE_DASH);
			add(pool, weights, Attack.COMBO, OathbreakerTuning.WEIGHT_P1_COMBO);
			if (guardEligible) {
				add(pool, weights, Attack.OATH_GUARD, OathbreakerTuning.WEIGHT_P1_OATH_GUARD);
			}
		} else if (boss.getPhase() == OathbreakerEntity.Phase.FORSWORN) {
			add(pool, weights, Attack.STANCE_DASH, OathbreakerTuning.WEIGHT_P2_STANCE_DASH);
			add(pool, weights, Attack.COMBO, OathbreakerTuning.WEIGHT_P2_COMBO);
			add(pool, weights, Attack.SOUL_REND, OathbreakerTuning.WEIGHT_P2_SOUL_REND);
			if (guardEligible) {
				add(pool, weights, Attack.OATH_GUARD, OathbreakerTuning.WEIGHT_P2_OATH_GUARD);
			}
		} else {
			add(pool, weights, Attack.STANCE_DASH, OathbreakerTuning.WEIGHT_P3_STANCE_DASH);
			add(pool, weights, Attack.COMBO, OathbreakerTuning.WEIGHT_P3_COMBO);
			add(pool, weights, Attack.SOUL_REND, OathbreakerTuning.WEIGHT_P3_SOUL_REND);
			if (guardEligible) {
				add(pool, weights, Attack.OATH_GUARD, OathbreakerTuning.WEIGHT_P3_OATH_GUARD);
			}
		}
		int total = 0;
		for (int w : weights) {
			total += w;
		}
		int roll = boss.getRandom().nextInt(total);
		for (int i = 0; i < pool.size(); i++) {
			roll -= weights.get(i);
			if (roll < 0) {
				return pool.get(i);
			}
		}
		return Attack.COMBO;
	}

	private static void add(List<Attack> pool, List<Integer> weights, Attack attack, int weight) {
		if (weight > 0) {
			pool.add(attack);
			weights.add(weight);
		}
	}

	// ---------------------------------------------------------------- lifecycle

	void begin(ServerLevel server, Attack attack) {
		active = attack;
		step = 0;
		boss.setHyperArmor(true);
		boss.getNavigation().stop();
		// park the move control too: navigation.stop() leaves its last waypoint live, and it would keep
		// walking him toward it underneath the attack
		boss.getMoveControl().setWantedPosition(boss.getX(), boss.getY(), boss.getZ(), 0.0);
		LivingEntity target = boss.getTarget();
		if (target != null) {
			boss.getLookControl().setLookAt(target, 30.0f, 30.0f);
		}
		switch (attack) {
			case STANCE_DASH -> {
				feinted = false;
				ticks = OathbreakerTuning.STANCE_WINDUP_TICKS;
				anim("windup_dash");
				glint(server, 0.9);
				server.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 2.2f, 0.6f);
			}
			case COMBO -> {
				comboHit = 0;
				comboHits = boss.getPhase() == OathbreakerEntity.Phase.KNIGHT ? OathbreakerTuning.COMBO_HITS_PHASE1
						: OathbreakerTuning.COMBO_HITS_PHASE2;
				comboHolding = false;
				ticks = OathbreakerTuning.COMBO_WINDUP_TICKS;
				anim("combo_windup_1");
			}
			case SOUL_REND -> {
				rendDir = null;
				ticks = OathbreakerTuning.SOUL_REND_WINDUP_TICKS;
				anim("soul_rend_windup");
				server.playSound(null, boss.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.HOSTILE, 1.6f, 0.5f);
			}
			case JUDGEMENT -> {
				judgementCooldown = OathbreakerTuning.JUDGEMENT_COOLDOWN_TICKS;
				judgementLanding = null;
				ticks = OathbreakerTuning.JUDGEMENT_RISE_TICKS;
				anim("judgement_rise");
				startMove(boss.position().add(0, OathbreakerTuning.JUDGEMENT_RISE_HEIGHT, 0), OathbreakerTuning.JUDGEMENT_RISE_TICKS, 0.0);
				server.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.2f, 0.6f);
				server.sendParticles(ParticleTypes.CLOUD, boss.getX(), boss.getY() + 0.2, boss.getZ(), 20, 0.8, 0.1, 0.8, 0.06);
			}
			case EXECUTION -> {
				executionCooldown = OathbreakerTuning.EXECUTION_COOLDOWN_TICKS;
				executionVictim = null;
				escapeDamage = 0.0f;
				ticks = OathbreakerTuning.EXECUTION_WINDUP_TICKS;
				anim("execution_windup");
				// the one RED telegraph in his whole kit: this can't be blocked or parried-around -- get out
				Vec3 h = offHand();
				server.sendParticles(EXECUTION_FLASH, h.x, h.y, h.z, 40, 0.35, 0.35, 0.35, 0.0);
				server.sendParticles(ParticleTypes.CRIMSON_SPORE, h.x, h.y, h.z, 30, 0.5, 0.5, 0.5, 0.02);
				server.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 2.4f, 0.8f);
			}
			case SOUL_SPEAR -> {
				ticks = OathbreakerTuning.CHAIN_THROW_TICKS;
				anim("chain_throw");
				glint(server, 0.75);
				server.playSound(null, boss.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.8f, 0.8f);
			}
			case CHAINS -> {
				chainHead = null;
				chainVictim = null;
				ticks = OathbreakerTuning.CHAIN_THROW_TICKS;
				anim("chain_throw");
				glint(server, 0.75);
				server.playSound(null, boss.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 1.6f, 0.6f);
			}
			case OATH_GUARD -> {
				ticks = OathbreakerTuning.GUARD_STANCE_TICKS;
				anim("guard_stance");
				glint(server, 1.2);
				server.playSound(null, boss.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.HOSTILE, 1.8f, 0.7f);
			}
			case BACKSTEP -> {
				ticks = OathbreakerTuning.BACKSTEP_TICKS;
				anim("backstep");
				Vec3 away = target == null ? forward().scale(-1.0)
						: horizontal(boss.position().subtract(target.position()));
				startMove(boss.position().add(away.scale(OathbreakerTuning.BACKSTEP_DISTANCE)),
						OathbreakerTuning.BACKSTEP_HOP_TICKS, OathbreakerTuning.BACKSTEP_ARC_HEIGHT);
				server.playSound(null, boss.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.HOSTILE, 1.2f, 1.1f);
			}
			case LEAPING_CLEAVE -> {
				kiteTicks = 0;
				ticks = OathbreakerTuning.LEAP_WINDUP_TICKS;
				anim("leap_windup");
				glint(server, 0.5);
				server.playSound(null, boss.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.4f, 0.6f);
			}
			case RIPOSTE -> throw new IllegalStateException("riposte only starts from a parry");
		}
	}

	/** Ends the current attack normally: maybe a backstep, otherwise the phase's shared cooldown. */
	private void finish(ServerLevel server) {
		Attack ended = active;
		clear();
		LivingEntity target = boss.getTarget();
		if (ended != Attack.BACKSTEP && isValidTarget(target)
				&& boss.distanceTo(target) <= OathbreakerTuning.BACKSTEP_TRIGGER_RANGE
				&& boss.getRandom().nextFloat() < OathbreakerTuning.BACKSTEP_CHANCE) {
			begin(server, Attack.BACKSTEP);
			return;
		}
		cooldown = boss.attackCooldownTicksForPhase();
	}

	/** Hard stop from outside (stagger, phase transition, death): no backstep, cooldown still applies. A
	 * held Execution victim is always let go -- no stuck players. */
	void cancel() {
		if (active != null) {
			clear();
			cooldown = boss.attackCooldownTicksForPhase();
		}
		stopMove();
	}

	private void clear() {
		releaseVictim();
		active = null;
		step = 0;
		ticks = 0;
		comboHit = 0;
		comboHolding = false;
		riposteVictim = null;
		leapTarget = null;
		rendDir = null;
		chainHead = null;
		chainVictim = null;
		boss.setHyperArmor(false);
	}

	// ---------------------------------------------------------------- stepping

	/**
	 * Called every tick of an attack, before the step timer counts down. {@code elapsed} is ticks since the
	 * current step's clip was triggered (1 on the first tick after), so a contact frame at 0.10s in the
	 * clip is {@code elapsed == 2}.
	 *
	 * <p>Step numbering per attack: STANCE_DASH 0 wind-up / 1 dash / 2 post / 3 feint. COMBO even = wind-up
	 * (+hold), odd = strike. SOUL_REND 0 wind-up / 1 slash. CHAINS 0 throw wind-up / 1 flying / 2 pull /
	 * 3 follow-up strike / 4 miss recovery. LEAPING_CLEAVE 0 wind-up / 1 air / 2 land.
	 */
	private void onHold(ServerLevel server) {
		switch (active) {
			case STANCE_DASH -> {
				if (step == 1 && elapsed(OathbreakerTuning.STANCE_DASH_TICKS) == OathbreakerTuning.STANCE_DASH_CONTACT_TICKS) {
					resolveArc(server, OathbreakerTuning.STANCE_RANGE, OathbreakerTuning.STANCE_ARC_DEGREES,
							OathbreakerTuning.STANCE_DAMAGE, OathbreakerTuning.STANCE_KNOCKBACK, SoundEvents.ANVIL_LAND);
				}
			}
			case COMBO -> {
				if (step % 2 == 1 && elapsed(OathbreakerTuning.COMBO_STRIKE_HOLD_TICKS) == OathbreakerTuning.COMBO_STRIKE_CONTACT_TICKS) {
					comboContact(server, comboHit);
				}
			}
			case RIPOSTE -> {
				if (elapsed(OathbreakerTuning.RIPOSTE_TICKS) == OathbreakerTuning.RIPOSTE_CONTACT_TICKS) {
					riposteThrust(server);
				}
			}
			case LEAPING_CLEAVE -> {
				if (step == 1 && leapTarget != null && boss.tickCount % 2 == 0) {
					// the landing zone, marked on the ground for the whole flight -- move out of it
					OathbreakerFx.ring(server, ParticleTypes.SOUL_FIRE_FLAME, leapTarget.add(0, 0.1, 0),
							OathbreakerTuning.LEAP_RADIUS, 20, 0.0);
				}
			}
			case SOUL_REND -> {
				if (step == 0) {
					int e = elapsed(OathbreakerTuning.SOUL_REND_WINDUP_TICKS);
					if (rendDir == null && e > OathbreakerTuning.SOUL_REND_WINDUP_TICKS - OathbreakerTuning.SOUL_REND_TELEGRAPH_TICKS) {
						rendDir = forward(); // locked: from here on he stops turning, and the line shows
					}
					if (rendDir != null && boss.tickCount % 2 == 0) {
						rendTelegraph(server);
					}
				} else if (elapsed(OathbreakerTuning.SOUL_REND_STRIKE_TICKS) == OathbreakerTuning.SOUL_REND_CONTACT_TICKS) {
					soulTrail(server, rendDir, 3.0, 40.0);
					rendLines.add(new RendLine(boss.position(), rendDir));
					server.playSound(null, boss.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 2.0f, 0.7f);
					server.playSound(null, boss.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0f, 0.6f);
				}
			}
			case JUDGEMENT -> {
				if (step == 1) {
					// hanging: keep re-aiming the landing at the target, beam pointing at it, zone on the ground
					LivingEntity target = boss.getTarget();
					if (isValidTarget(target)) {
						judgementLanding = groundBelow(target.position());
					} else if (judgementLanding == null) {
						judgementLanding = groundBelow(boss.position());
					}
					if (boss.tickCount % 2 == 0) {
						OathbreakerFx.line(server, ParticleTypes.SOUL_FIRE_FLAME, boss.position(), judgementLanding.add(0, 0.2, 0), 0.7, 0.05);
						OathbreakerFx.ring(server, ParticleTypes.SOUL, judgementLanding.add(0, 0.15, 0),
								OathbreakerTuning.JUDGEMENT_RADIUS, 28, 0.0);
					}
				} else if (step == 2 && elapsed(OathbreakerTuning.JUDGEMENT_SLAM_TICKS) == OathbreakerTuning.JUDGEMENT_FALL_TICKS) {
					judgementImpact(server);
				}
			}
			case EXECUTION -> {
				if (step == 2) {
					tickExecutionHold(server);
				} else if (step == 3 && elapsed(OathbreakerTuning.EXECUTION_IMPALE_TICKS) == OathbreakerTuning.EXECUTION_IMPALE_CONTACT_TICKS) {
					impale(server);
				}
			}
			case CHAINS -> {
				if (step == 1) {
					tickChainFlight(server);
				} else if (step == 2) {
					tickChainPull(server);
				} else if (step == 3 && elapsed(OathbreakerTuning.COMBO_STRIKE_HOLD_TICKS) == OathbreakerTuning.COMBO_STRIKE_CONTACT_TICKS) {
					comboContact(server, 1);
				} else if (step == 4 && chainHead != null) {
					// retracting: the head reels back to his hand over the first few ticks of the recovery
					Vec3 hand = hand();
					chainHead = chainHead.lerp(hand, 0.35);
					drawChain(server, hand, chainHead);
					if (chainHead.distanceToSqr(hand) < 0.25) {
						chainHead = null;
					}
				}
			}
			default -> {
			}
		}
	}

	/** Ticks since the current step's clip started, given that step's full length. */
	private int elapsed(int stepTicks) {
		return stepTicks - ticks + 1;
	}

	private void comboContact(ServerLevel server, int hit) {
		boolean thrust = hit == OathbreakerTuning.COMBO_HITS_PHASE2;
		resolveArc(server, thrust ? OathbreakerTuning.COMBO_THRUST_RANGE : OathbreakerTuning.COMBO_RANGE,
				thrust ? OathbreakerTuning.COMBO_THRUST_ARC_DEGREES : OathbreakerTuning.COMBO_ARC_DEGREES,
				OathbreakerTuning.COMBO_DAMAGE_PER_HIT, OathbreakerTuning.COMBO_KNOCKBACK, SoundEvents.PLAYER_ATTACK_CRIT);
	}

	private void advance(ServerLevel server) {
		switch (active) {
			case STANCE_DASH -> {
				if (step == 0 && !feinted && boss.getPhase() != OathbreakerEntity.Phase.KNIGHT
						&& boss.getRandom().nextFloat() < OathbreakerTuning.STANCE_FEINT_CHANCE) {
					// phase 2+: shift the weight as if to go... and don't. The real dash comes 0.5s later.
					feinted = true;
					step = 3;
					ticks = OathbreakerTuning.STANCE_FEINT_TICKS;
					anim("dash_feint");
					server.playSound(null, boss.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.HOSTILE, 1.4f, 1.2f);
				} else if (step == 0 || step == 3) {
					if (step == 3) {
						glint(server, 0.9);
					}
					step = 1;
					ticks = OathbreakerTuning.STANCE_DASH_TICKS;
					anim("dash_attack");
					startMove(boss.position().add(forward().scale(OathbreakerTuning.STANCE_DASH_LUNGE_DISTANCE)),
							OathbreakerTuning.STANCE_DASH_LUNGE_TICKS, 0.0);
					server.playSound(null, boss.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0f, 0.7f);
				} else if (step == 1) {
					step = 2;
					ticks = OathbreakerTuning.STANCE_POST_TICKS;
					anim("post_dash");
					// the 2s held stance is the punish window -- he's committed, so drop hyper armor
					boss.setHyperArmor(false);
				} else {
					finish(server);
				}
			}
			case COMBO -> {
				if (step % 2 == 0) {
					// wind-up done. Phase 2+: an unpredictable extra hold first ("Elden Ring delay").
					if (!comboHolding && boss.getPhase() != OathbreakerEntity.Phase.KNIGHT) {
						int delay = boss.getRandom().nextInt(OathbreakerTuning.COMBO_DELAY_MAX_TICKS + 1);
						if (delay > 0) {
							comboHolding = true;
							ticks = delay;
							anim("combo_hold_" + (comboHit + 1));
							return;
						}
					}
					comboHolding = false;
					comboHit++;
					step++;
					ticks = OathbreakerTuning.COMBO_STRIKE_HOLD_TICKS;
					anim("combo_strike_" + comboHit);
					server.playSound(null, boss.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE,
							1.6f, 1.0f + comboHit * 0.05f);
				} else if (comboHit >= comboHits) {
					finish(server);
				} else {
					step++;
					ticks = OathbreakerTuning.COMBO_WINDUP_TICKS;
					anim("combo_windup_" + (comboHit + 1));
				}
			}
			case OATH_GUARD, RIPOSTE, BACKSTEP -> finish(server);
			case SOUL_SPEAR -> {
				launchSpear(server);
				finish(server);
			}
			case LEAPING_CLEAVE -> {
				if (step == 0) {
					takeOff(server);
				} else if (step == 1) {
					land(server);
				} else {
					finish(server);
				}
			}
			case SOUL_REND -> {
				if (step == 0) {
					if (rendDir == null) {
						rendDir = forward();
					}
					step = 1;
					ticks = OathbreakerTuning.SOUL_REND_STRIKE_TICKS;
					anim("soul_rend_strike");
				} else {
					finish(server);
				}
			}
			case JUDGEMENT -> {
				if (step == 0) {
					// risen: hang, tracking the target (a stationary scripted move keeps him aloft)
					step = 1;
					ticks = OathbreakerTuning.JUDGEMENT_HANG_TICKS;
					anim("judgement_hang");
					startMove(boss.position(), OathbreakerTuning.JUDGEMENT_HANG_TICKS + 1, 0.0);
					server.playSound(null, boss.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 2.5f, 0.6f);
				} else if (step == 1) {
					// the landing LOCKS here -- this is the moment to get clear
					if (judgementLanding == null) {
						judgementLanding = groundBelow(boss.position());
					}
					step = 2;
					ticks = OathbreakerTuning.JUDGEMENT_SLAM_TICKS;
					anim("judgement_slam");
					startMove(judgementLanding, OathbreakerTuning.JUDGEMENT_FALL_TICKS, 0.0);
				} else {
					finish(server);
				}
			}
			case EXECUTION -> {
				switch (step) {
					case 0 -> {
						step = 1;
						ticks = OathbreakerTuning.EXECUTION_LUNGE_TICKS;
						anim("execution_lunge");
						startMove(boss.position().add(forward().scale(OathbreakerTuning.EXECUTION_LUNGE_DISTANCE)),
								OathbreakerTuning.EXECUTION_LUNGE_TICKS, 0.0);
						server.playSound(null, boss.blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.8f, 0.6f);
					}
					case 1 -> tryGrab(server);
					case 2 -> {
						step = 3;
						ticks = OathbreakerTuning.EXECUTION_IMPALE_TICKS;
						anim("execution_impale");
					}
					default -> finish(server);
				}
			}
			case CHAINS -> {
				switch (step) {
					case 0 -> throwChain(server);
					case 1 -> chainMissed(server); // reach exhausted without a catch (the step timer is a failsafe)
					case 2 -> {
						// yanked in -- straight into combo strike 1
						step = 3;
						ticks = OathbreakerTuning.COMBO_STRIKE_HOLD_TICKS;
						chainVictim = null;
						anim("combo_strike_1");
						server.playSound(null, boss.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.6f, 1.0f);
					}
					default -> finish(server);
				}
			}
		}
	}

	// ---------------------------------------------------------------- Soul Rend

	/** Where on the ground eruption {@code k} of a line lands (searching a few blocks up/down for footing). */
	private Vec3 rendPoint(Vec3 origin, Vec3 dir, int k) {
		Vec3 p = origin.add(dir.scale(k));
		net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(p.x, origin.y + 0.5, p.z);
		net.minecraft.world.level.Level level = boss.level();
		for (int i = 0; i < 3 && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty(); i++) {
			pos = pos.above();
		}
		for (int i = 0; i < 3 && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty(); i++) {
			pos = pos.below();
		}
		return new Vec3(p.x, pos.getY(), p.z);
	}

	/** The 0.5s warning: soul particles flicker along the whole locked line before anything erupts. */
	private void rendTelegraph(ServerLevel server) {
		Vec3 origin = boss.position();
		for (int k = 1; k <= OathbreakerTuning.SOUL_REND_LENGTH; k++) {
			Vec3 p = rendPoint(origin, rendDir, k);
			server.sendParticles(ParticleTypes.SOUL, p.x, p.y + 0.15, p.z, 1, 0.25, 0.02, 0.25, 0.01);
		}
	}

	private void erupt(ServerLevel server, RendLine line, int k) {
		Vec3 p = rendPoint(line.origin, line.dir, k);
		server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y + 0.6, p.z, 14, 0.3, 0.6, 0.3, 0.06);
		server.sendParticles(ParticleTypes.SOUL, p.x, p.y + 1.2, p.z, 3, 0.2, 0.4, 0.2, 0.05);
		if (k % 3 == 1) {
			server.playSound(null, p.x, p.y, p.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 0.8f, 0.6f);
		}
		double r = OathbreakerTuning.SOUL_REND_RADIUS;
		AABB box = new AABB(p.x - r, p.y - 0.5, p.z - r, p.x + r, p.y + 2.5, p.z + r);
		for (LivingEntity victim : server.getEntitiesOfClass(LivingEntity.class, box,
				e -> e != boss && e.isAlive() && isValidTarget(e) && !line.hit.contains(e.getId()))) {
			if (horizontalDistSqr(victim.position(), p) > r * r) {
				continue;
			}
			line.hit.add(victim.getId());
			if (strike(victim, OathbreakerTuning.SOUL_REND_DAMAGE, line.dir, 0.3)) {
				victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(), OathbreakerTuning.SOUL_REND_FIRE_TICKS));
				victim.setDeltaMovement(victim.getDeltaMovement().add(0, 0.35, 0));
				victim.hurtMarked = true;
			}
		}
	}

	// ---------------------------------------------------------------- Soul Spear (anti-cheese, phase 1)

	/** Off the hand, aimed at where the target is at the moment of release -- then it flies straight, fast
	 * and visible, so stepping off the line dodges it. */
	private void launchSpear(ServerLevel server) {
		LivingEntity target = boss.getTarget();
		Vec3 from = offHand();
		Vec3 aim = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0)
				: from.add(forward().scale(OathbreakerTuning.SOUL_SPEAR_REACH));
		Vec3 d = aim.subtract(from);
		spears.add(new Spear(from, d.lengthSqr() < 1.0e-6 ? forward() : d.normalize()));
		server.playSound(null, boss.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.HOSTILE, 2.0f, 0.5f);
		server.playSound(null, boss.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.2f, 0.6f);
	}

	/** @return true once the spear is spent (hit something, a wall, or its reach). */
	private boolean tickSpear(ServerLevel server, Spear s) {
		Vec3 prev = s.pos;
		Vec3 next = prev.add(s.dir.scale(OathbreakerTuning.SOUL_SPEAR_SPEED));
		s.travelled += OathbreakerTuning.SOUL_SPEAR_SPEED;
		OathbreakerFx.line(server, ParticleTypes.SOUL_FIRE_FLAME, prev, next, 0.35, 0.02);
		double r = OathbreakerTuning.SOUL_SPEAR_HIT_RADIUS;
		for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(prev, next).inflate(r),
				e -> e != boss && e.isAlive() && isValidTarget(e))) {
			if (e.getBoundingBox().inflate(r).clip(prev, next).isPresent()) {
				strike(e, OathbreakerTuning.SOUL_SPEAR_DAMAGE, s.dir, 0.6);
				server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 16, 0.2, 0.3, 0.2, 0.08);
				server.playSound(null, e.blockPosition(), SoundEvents.TRIDENT_HIT, SoundSource.HOSTILE, 1.6f, 0.6f);
				return true;
			}
		}
		var block = server.clip(new net.minecraft.world.level.ClipContext(prev, next,
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, boss));
		if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
			Vec3 p = block.getLocation();
			server.sendParticles(ParticleTypes.SOUL, p.x, p.y, p.z, 8, 0.2, 0.2, 0.2, 0.03);
			return true;
		}
		s.pos = next;
		return s.travelled >= OathbreakerTuning.SOUL_SPEAR_REACH;
	}

	// ---------------------------------------------------------------- Chains of the Forsworn

	private Vec3 hand() {
		return boss.position().add(forward().scale(0.9)).add(0, boss.getBbHeight() * 0.6, 0);
	}

	/** Wind-up done: the chain leaves his hand aimed at where the target IS right now (locked -- step aside). */
	private void throwChain(ServerLevel server) {
		LivingEntity target = boss.getTarget();
		Vec3 from = hand();
		Vec3 aim = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0)
				: from.add(forward().scale(OathbreakerTuning.CHAIN_REACH));
		Vec3 d = aim.subtract(from);
		chainDir = d.lengthSqr() < 1.0e-6 ? forward() : d.normalize();
		chainHead = from;
		chainTravel = 0.0;
		step = 1;
		// failsafe only: the flight normally ends on a catch, a wall, or running out of reach
		ticks = (int) Math.ceil(OathbreakerTuning.CHAIN_REACH / OathbreakerTuning.CHAIN_SPEED) + 2;
		anim("chain_hold");
		server.playSound(null, boss.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.HOSTILE, 1.8f, 0.6f);
	}

	private void tickChainFlight(ServerLevel server) {
		Vec3 prev = chainHead;
		Vec3 next = prev.add(chainDir.scale(OathbreakerTuning.CHAIN_SPEED));
		chainTravel += OathbreakerTuning.CHAIN_SPEED;
		// a catch first (a player standing right in front of a wall still gets caught)...
		double r = OathbreakerTuning.CHAIN_HIT_RADIUS;
		LivingEntity caught = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(prev, next).inflate(r),
				e -> e != boss && e.isAlive() && isValidTarget(e))) {
			var hit = e.getBoundingBox().inflate(r).clip(prev, next);
			if (hit.isPresent()) {
				double dsq = hit.get().distanceToSqr(prev);
				if (dsq < best) {
					best = dsq;
					caught = e;
				}
			}
		}
		if (caught != null) {
			chainHead = caught.position().add(0, caught.getBbHeight() * 0.5, 0);
			drawChain(server, hand(), chainHead);
			chainCaught(server, caught);
			return;
		}
		// ...then walls
		var block = server.clip(new net.minecraft.world.level.ClipContext(prev, next,
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, boss));
		if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
			chainHead = block.getLocation();
			drawChain(server, hand(), chainHead);
			server.playSound(null, net.minecraft.core.BlockPos.containing(chainHead), SoundEvents.CHAIN_HIT, SoundSource.HOSTILE, 1.2f, 0.7f);
			chainMissed(server);
			return;
		}
		chainHead = next;
		drawChain(server, hand(), chainHead);
		if (chainTravel >= OathbreakerTuning.CHAIN_REACH) {
			chainMissed(server);
		}
	}

	private void chainCaught(ServerLevel server, LivingEntity victim) {
		chainVictim = victim;
		step = 2;
		ticks = OathbreakerTuning.CHAIN_PULL_TICKS;
		anim("chain_pull");
		server.playSound(null, victim.blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.HOSTILE, 1.8f, 0.6f);
		server.playSound(null, boss.blockPosition(), SoundEvents.TRIDENT_RETURN, SoundSource.HOSTILE, 1.6f, 0.6f);
		server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, chainHead.x, chainHead.y, chainHead.z, 12, 0.3, 0.4, 0.3, 0.05);
	}

	/** The yank: over {@link OathbreakerTuning#CHAIN_PULL_TICKS} the victim is dragged to a point just in
	 * front of him, arriving on the last tick. Stops cleanly if they die, vanish or leave the dimension. */
	private void tickChainPull(ServerLevel server) {
		LivingEntity v = chainVictim;
		if (v == null || !v.isAlive() || v.isRemoved() || v.level() != boss.level()) {
			chainVictim = null;
			return;
		}
		Vec3 dest = boss.position().add(forward().scale(OathbreakerTuning.CHAIN_PULL_STOP_DISTANCE));
		Vec3 to = dest.subtract(v.position());
		int remaining = Math.max(1, ticks);
		Vec3 vel = to.scale(1.0 / remaining);
		double max = 2.0;
		if (vel.length() > max) {
			vel = vel.normalize().scale(max);
		}
		v.setDeltaMovement(vel.x, Math.max(vel.y, 0.0) + 0.04, vel.z);
		v.hurtMarked = true;
		v.fallDistance = 0.0f;
		drawChain(server, hand(), v.position().add(0, v.getBbHeight() * 0.5, 0));
	}

	/** A miss: the chain reels back and he's left open for a full second. */
	private void chainMissed(ServerLevel server) {
		step = 4;
		ticks = OathbreakerTuning.CHAIN_RECOVER_TICKS;
		anim("chain_recover");
		boss.setHyperArmor(false);
		server.playSound(null, boss.blockPosition(), SoundEvents.TRIDENT_RETURN, SoundSource.HOSTILE, 1.4f, 0.8f);
	}

	/** The spectral chain itself: pale soul-blue links from his hand to the head, no model needed. */
	private static final net.minecraft.core.particles.DustParticleOptions CHAIN_LINK =
			new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.55f, 0.85f, 0.95f), 1.1f);

	private void drawChain(ServerLevel server, Vec3 from, Vec3 to) {
		OathbreakerFx.line(server, CHAIN_LINK, from, to, 0.45, 0.02);
		server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, to.x, to.y, to.z, 1, 0.05, 0.05, 0.05, 0.0);
	}

	// ---------------------------------------------------------------- Oath Guard / Riposte

	/** Notes that the current target just hit him -- the Oath Guard is only offered to someone who's been
	 * attacking recently. */
	void noteHurtBy(Entity attacker) {
		if (attacker != null && attacker == boss.getTarget()) {
			lastHitByTargetTick = boss.tickCount;
		}
	}

	/**
	 * Called from {@code OathbreakerEntity#hurt} before any damage applies. During the guard stance a
	 * <em>melee</em> hit from inside his front cone is negated outright and answered with an instant
	 * Riposte. Projectiles, anything from outside melee reach, and anything from behind go straight
	 * through -- reading the guard and circling round is the intended counterplay.
	 *
	 * @return true if the hit was parried (the caller must not apply it)
	 */
	boolean tryParry(ServerLevel server, DamageSource source) {
		if (active != Attack.OATH_GUARD) {
			return false;
		}
		if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker
				|| source.is(DamageTypeTags.IS_PROJECTILE)
				|| boss.distanceTo(attacker) > OathbreakerTuning.GUARD_MELEE_REACH) {
			return false;
		}
		if (!inFrontCone(attacker, OathbreakerTuning.GUARD_ARC_DEGREES)) {
			return false;
		}
		// Parried: the anvil-ping glint, then straight into the riposte.
		Vec3 sword = boss.position().add(forward().scale(0.9)).add(0, boss.getBbHeight() * 0.72, 0);
		server.sendParticles(ParticleTypes.ELECTRIC_SPARK, sword.x, sword.y, sword.z, 24, 0.25, 0.35, 0.25, 0.25);
		server.sendParticles(ParticleTypes.END_ROD, sword.x, sword.y, sword.z, 10, 0.1, 0.2, 0.1, 0.12);
		server.playSound(null, boss.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.3f, 2.0f);
		server.playSound(null, boss.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.5f, 0.8f);
		boss.lookAt(attacker, 360.0f, 360.0f);
		boss.setYBodyRot(boss.getYRot());
		active = Attack.RIPOSTE;
		step = 0;
		ticks = OathbreakerTuning.RIPOSTE_TICKS;
		riposteVictim = attacker;
		anim("riposte");
		return true;
	}

	private void riposteThrust(ServerLevel server) {
		boolean hit = false;
		soulTrail(server, forward(), OathbreakerTuning.RIPOSTE_RANGE, 12.0);
		for (LivingEntity victim : arcTargets(server, forward(), OathbreakerTuning.RIPOSTE_RANGE, OathbreakerTuning.RIPOSTE_ARC_DEGREES)) {
			if (strike(victim, OathbreakerTuning.RIPOSTE_DAMAGE, forward(), OathbreakerTuning.RIPOSTE_KNOCKBACK)) {
				hit = true;
				victim.setDeltaMovement(victim.getDeltaMovement().add(0, 0.35, 0));
				victim.hurtMarked = true;
			}
		}
		server.playSound(null, boss.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.HOSTILE, 1.6f, 0.7f);
		if (hit) {
			server.playSound(null, boss.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.6f, 0.8f);
		}
	}

	// ---------------------------------------------------------------- Leaping Cleave

	private void takeOff(ServerLevel server) {
		LivingEntity target = boss.getTarget();
		Vec3 land = target != null ? target.position() : boss.position().add(forward().scale(OathbreakerTuning.LEAP_MIN_RANGE));
		Vec3 flat = land.subtract(boss.position());
		double dist = Math.sqrt(flat.x * flat.x + flat.z * flat.z);
		double max = leapMaxOverride > 0 ? leapMaxOverride : OathbreakerTuning.LEAP_MAX_RANGE;
		leapMaxOverride = 0;
		if (dist > max) {
			land = boss.position().add(flat.scale(max / dist));
			dist = max;
		}
		leapTarget = land;
		step = 1;
		ticks = OathbreakerTuning.LEAP_AIR_TICKS;
		anim("leap_air");
		startMove(land, OathbreakerTuning.LEAP_AIR_TICKS,
				OathbreakerTuning.LEAP_ARC_HEIGHT_BASE + dist * OathbreakerTuning.LEAP_ARC_HEIGHT_PER_BLOCK);
		server.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 0.8f, 1.4f);
		server.sendParticles(ParticleTypes.CLOUD, boss.getX(), boss.getY() + 0.2, boss.getZ(), 16, 0.6, 0.1, 0.6, 0.05);
	}

	private void land(ServerLevel server) {
		stopMove();
		step = 2;
		ticks = OathbreakerTuning.LEAP_LAND_TICKS;
		anim("leap_land");
		Vec3 center = boss.position();
		double r = OathbreakerTuning.LEAP_RADIUS;
		boolean hit = false;
		for (LivingEntity victim : server.getEntitiesOfClass(LivingEntity.class, boss.getBoundingBox().inflate(r, 2.0, r),
				e -> e != boss && e.isAlive() && isValidTarget(e))) {
			double d = Math.sqrt(horizontalDistSqr(victim.position(), center));
			if (d > r) {
				continue;
			}
			float falloff = (float) (1.0 - (1.0 - OathbreakerTuning.LEAP_EDGE_DAMAGE_FRACTION) * (d / r));
			Vec3 out = horizontal(victim.position().subtract(center));
			hit |= strike(victim, OathbreakerTuning.LEAP_DAMAGE * falloff, out, OathbreakerTuning.LEAP_KNOCKBACK);
		}
		OathbreakerFx.shake(server, center, 0.7f, 12);
		OathbreakerFx.ring(server, ParticleTypes.SOUL_FIRE_FLAME, center.add(0, 0.2, 0), r * 0.5, 16, 0.08);
		OathbreakerFx.ring(server, ParticleTypes.SOUL_FIRE_FLAME, center.add(0, 0.2, 0), r, 28, 0.05);
		OathbreakerFx.ring(server, ParticleTypes.SOUL, center.add(0, 0.4, 0), r, 20, 0.03);
		server.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.5, center.z, 2, 0.6, 0.1, 0.6, 0.0);
		server.playSound(null, boss.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 2.2f, 0.7f);
		server.playSound(null, boss.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0f, 0.6f);
		if (hit) {
			server.playSound(null, boss.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2f, 0.9f);
		}
	}

	// ---------------------------------------------------------------- Judgement (phase 3)

	/** The first solid footing at or below {@code p} (up to 8 blocks down), as a standing position. */
	private Vec3 groundBelow(Vec3 p) {
		net.minecraft.world.level.Level level = boss.level();
		net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(p.x, p.y + 0.5, p.z);
		for (int i = 0; i < 8 && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty(); i++) {
			pos = pos.below();
		}
		return new Vec3(p.x, pos.getY(), p.z);
	}

	private void judgementImpact(ServerLevel server) {
		stopMove();
		Vec3 c = boss.position();
		double r = OathbreakerTuning.JUDGEMENT_RADIUS;
		for (LivingEntity victim : server.getEntitiesOfClass(LivingEntity.class, boss.getBoundingBox().inflate(r, 3.0, r),
				e -> e != boss && e.isAlive() && isValidTarget(e))) {
			double d = Math.sqrt(horizontalDistSqr(victim.position(), c));
			if (d > r) {
				continue;
			}
			float dmg = (float) (OathbreakerTuning.JUDGEMENT_DAMAGE_CENTER
					- (OathbreakerTuning.JUDGEMENT_DAMAGE_CENTER - OathbreakerTuning.JUDGEMENT_DAMAGE_EDGE) * (d / r));
			if (strike(victim, dmg, horizontal(victim.position().subtract(c)), OathbreakerTuning.JUDGEMENT_KNOCKBACK)) {
				victim.setDeltaMovement(victim.getDeltaMovement().add(0, 0.5, 0));
				victim.hurtMarked = true;
			}
		}
		OathbreakerFx.shake(server, c, OathbreakerTuning.JUDGEMENT_SHAKE_INTENSITY, OathbreakerTuning.JUDGEMENT_SHAKE_TICKS);
		OathbreakerFx.zoom(server, c, OathbreakerTuning.JUDGEMENT_ZOOM, OathbreakerTuning.JUDGEMENT_ZOOM_TICKS);
		server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
		OathbreakerFx.ring(server, ParticleTypes.SOUL_FIRE_FLAME, c.add(0, 0.2, 0), r * 0.5, 20, 0.12);
		OathbreakerFx.ring(server, ParticleTypes.SOUL_FIRE_FLAME, c.add(0, 0.2, 0), r, 40, 0.08);
		server.playSound(null, boss.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 3.0f, 0.5f);
		server.playSound(null, boss.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 0.5f);
		server.playSound(null, boss.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.HOSTILE, 2.5f, 0.5f);
		soulRings.add(new SoulRing(c, OathbreakerTuning.JUDGEMENT_RING_TICKS));
	}

	/** The lingering circle: everything inside burns (4/s) until it fades -- reposition or pay for it. */
	private void tickSoulRing(ServerLevel server, SoulRing ring) {
		double r = OathbreakerTuning.JUDGEMENT_RING_RADIUS;
		if (boss.tickCount % 3 == 0) {
			OathbreakerFx.ring(server, ParticleTypes.SOUL_FIRE_FLAME, ring.center.add(0, 0.1, 0), r, 32, 0.0);
			for (int i = 0; i < 6; i++) {
				double a = boss.getRandom().nextDouble() * Math.PI * 2.0;
				double d = Math.sqrt(boss.getRandom().nextDouble()) * r;
				server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, ring.center.x + Math.cos(a) * d, ring.center.y + 0.1,
						ring.center.z + Math.sin(a) * d, 1, 0.05, 0.05, 0.05, 0.0);
			}
		}
		if (ring.ticksLeft % 20 != 0) {
			return;
		}
		for (LivingEntity victim : server.getEntitiesOfClass(LivingEntity.class,
				new AABB(ring.center, ring.center).inflate(r, 2.5, r),
				e -> e != boss && e.isAlive() && isValidTarget(e) && horizontalDistSqr(e.position(), ring.center) <= r * r)) {
			victim.hurt(boss.damageSources().mobAttack(boss), OathbreakerTuning.JUDGEMENT_RING_DAMAGE_PER_SECOND);
		}
	}

	// ---------------------------------------------------------------- Execution (phase 3)

	private static final net.minecraft.core.particles.DustParticleOptions EXECUTION_FLASH =
			new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0f, 0.05f, 0.05f), 2.0f);

	private static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> EXECUTION_DAMAGE =
			net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
					com.projecthero.mod.ProjectHeroMod.id("oathbreaker_execution"));

	/** The grabbing hand (the off hand -- the sword stays in the other). */
	private Vec3 offHand() {
		Vec3 f = forward();
		Vec3 side = new Vec3(-f.z, 0, f.x);
		return boss.position().add(f.scale(1.0)).add(side.scale(-0.5)).add(0, boss.getBbHeight() * 0.55, 0);
	}

	boolean isExecutionVictim(Entity e) {
		return e != null && e == executionVictim;
	}

	/** End of the lunge: grab the nearest player in the cone, or whiff and eat a 1.5s recovery. */
	private void tryGrab(ServerLevel server) {
		Player grabbed = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : arcTargets(server, forward(), OathbreakerTuning.EXECUTION_GRAB_RANGE + boss.getBbWidth() * 0.5,
				OathbreakerTuning.EXECUTION_GRAB_ARC_DEGREES)) {
			if (e instanceof Player p && !p.isPassenger()) {
				double d = boss.distanceToSqr(p);
				if (d < best) {
					best = d;
					grabbed = p;
				}
			}
		}
		if (grabbed == null) {
			step = 4;
			ticks = OathbreakerTuning.EXECUTION_WHIFF_TICKS;
			anim("execution_whiff");
			boss.setHyperArmor(false);
			server.playSound(null, boss.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.HOSTILE, 1.6f, 0.5f);
			return;
		}
		executionVictim = grabbed;
		escapeDamage = 0.0f;
		grabbed.startRiding(boss, true);
		step = 2;
		ticks = OathbreakerTuning.EXECUTION_HOLD_TICKS;
		anim("execution_hold");
		server.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 2.0f, 0.6f);
		server.playSound(null, boss.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.5f, 0.5f);
	}

	/**
	 * Every tick of the hold: the victim has to still be a valid, present, same-dimension, living target --
	 * otherwise the hold simply ends (death, logout, dimension change, gamemode switch all land here). A
	 * victim who's just sneak-dismounted (vanilla lets any rider do that) is put straight back.
	 */
	private void tickExecutionHold(ServerLevel server) {
		LivingEntity v = executionVictim;
		if (v == null || !v.isAlive() || v.isRemoved() || v.level() != boss.level() || !isValidTarget(v)
				|| (v instanceof net.minecraft.server.level.ServerPlayer sp && sp.hasDisconnected())) {
			releaseVictim();
			finish(server);
			return;
		}
		if (v.getVehicle() != boss) {
			if (v.distanceTo(boss) > 6.0) {
				releaseVictim();
				finish(server);
				return;
			}
			v.startRiding(boss, true);
		}
		v.fallDistance = 0.0f;
		if (boss.tickCount % 4 == 0) {
			server.sendParticles(ParticleTypes.SOUL, v.getX(), v.getY() + v.getBbHeight() * 0.5, v.getZ(), 2, 0.2, 0.3, 0.2, 0.01);
		}
	}

	/** Called from {@code OathbreakerEntity#hurt} for every landed hit: during a hold, OTHER players' raw
	 * damage adds up, and at {@link OathbreakerTuning#EXECUTION_ESCAPE_DAMAGE} they rip the victim free and
	 * he staggers. */
	void noteDamageTaken(ServerLevel server, DamageSource source, float raw) {
		if (active != Attack.EXECUTION || step != 2 || executionVictim == null) {
			return;
		}
		if (!(source.getEntity() instanceof Player attacker) || attacker == executionVictim) {
			return;
		}
		escapeDamage += Math.max(0.0f, raw);
		if (escapeDamage >= OathbreakerTuning.EXECUTION_ESCAPE_DAMAGE) {
			LivingEntity freed = executionVictim;
			releaseVictim();
			freed.setDeltaMovement(freed.getDeltaMovement().add(forward().scale(0.6)).add(0, 0.3, 0));
			freed.hurtMarked = true;
			server.playSound(null, boss.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 2.0f, 1.2f);
			boss.breakPoise(server); // cancels the attack (and would release anyone still held)
		}
	}

	/** The impale: off the hand, the blade goes through, and he throws them back. Unblockable -- the
	 * damage type is tagged {@code minecraft:bypasses_shield}, but armour still counts. */
	private void impale(ServerLevel server) {
		LivingEntity v = executionVictim;
		releaseVictim();
		if (v == null || !v.isAlive() || v.level() != boss.level()) {
			return;
		}
		float damage = OathbreakerTuning.EXECUTION_DAMAGE;
		if (TitanCombat.isBoss(v)) {
			damage = Math.min(damage, (float) (v.getMaxHealth() * 0.10));
		}
		Vec3 f = forward();
		if (v.hurt(boss.damageSources().source(EXECUTION_DAMAGE, boss), damage)) {
			v.knockback(OathbreakerTuning.EXECUTION_THROW_KNOCKBACK, -f.x, -f.z);
			v.setDeltaMovement(v.getDeltaMovement().add(0, 0.5, 0));
			v.hurtMarked = true;
		}
		server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, v.getX(), v.getY() + v.getBbHeight() * 0.5, v.getZ(), 30, 0.3, 0.4, 0.3, 0.12);
		server.playSound(null, boss.blockPosition(), SoundEvents.TRIDENT_HIT, SoundSource.HOSTILE, 2.0f, 0.5f);
		server.playSound(null, boss.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 2.0f, 0.6f);
	}

	/** Lets go of whoever is held, if anyone. Safe to call any time, from anywhere (death, cancel, logout). */
	void releaseVictim() {
		LivingEntity v = executionVictim;
		executionVictim = null;
		if (v != null && v.getVehicle() == boss) {
			v.stopRiding();
		}
		for (Entity passenger : List.copyOf(boss.getPassengers())) {
			passenger.stopRiding();
		}
	}

	// ---------------------------------------------------------------- Phantom Echo (phase 3)

	private void scheduleEcho(Vec3 f, double range, double arc, float damage, double knockback) {
		echoes.add(new Echo(boss.position(), f, range, arc, damage * OathbreakerTuning.ECHO_DAMAGE_FRACTION, knockback,
				boss.tickCount + OathbreakerTuning.ECHO_DELAY_TICKS));
	}

	/** The ghost: a column of soul fire where he stood, the same arc traced again, the same check at 60%. */
	private void fireEcho(ServerLevel server, Echo e) {
		Vec3 o = e.origin();
		for (double y = 0.2; y < boss.getBbHeight(); y += 0.35) {
			server.sendParticles(ParticleTypes.SOUL, o.x, o.y + y, o.z, 1, 0.25, 0.05, 0.25, 0.0);
		}
		server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, o.x, o.y + boss.getBbHeight() * 0.5, o.z, 12, 0.3, 0.8, 0.3, 0.01);
		double half = Math.toRadians(e.arc() / 2.0);
		double base = Math.atan2(e.forward().z, e.forward().x);
		for (int i = 0; i < 10; i++) {
			double a = base - half + (2.0 * half) * i / 9.0;
			server.sendParticles(ParticleTypes.SOUL, o.x + Math.cos(a) * e.range() * 0.75, o.y + boss.getBbHeight() * 0.55,
					o.z + Math.sin(a) * e.range() * 0.75, 1, 0.05, 0.05, 0.05, 0.0);
		}
		server.playSound(null, o.x, o.y, o.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.4f, 0.5f);
		server.playSound(null, o.x, o.y, o.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.2f, 1.2f);
		for (LivingEntity victim : arcTargets(server, o, e.forward(), e.range(), e.arc())) {
			strike(victim, e.damage(), e.forward(), e.knockback());
		}
	}

	// ---------------------------------------------------------------- scripted movement

	private void startMove(Vec3 end, int durationTicks, double arcHeight) {
		moveStart = boss.position();
		moveEnd = end;
		moveTicks = Math.max(1, durationTicks);
		moveElapsed = 0;
		moveArc = arcHeight;
		boss.setNoGravity(true);
	}

	private void tickScriptedMove() {
		if (moveTicks <= 0) {
			return;
		}
		moveElapsed++;
		double s = Math.min(1.0, moveElapsed / (double) moveTicks);
		Vec3 want = moveStart.lerp(moveEnd, s).add(0, moveArc * 4.0 * s * (1.0 - s), 0);
		// Moved directly (with collisions) rather than via velocity, so this tick's position is exactly
		// on the path -- velocity would only apply next tick, and the last segment got lost at landing.
		boss.move(net.minecraft.world.entity.MoverType.SELF, want.subtract(boss.position()));
		boss.setDeltaMovement(Vec3.ZERO);
		if (s >= 1.0) {
			stopMove();
		}
	}

	private void stopMove() {
		if (moveTicks > 0 || boss.isNoGravity()) {
			moveTicks = 0;
			boss.setNoGravity(false);
			// settle onto whatever is directly below (the path ends exactly at ground height, which never
			// registers a collision on its own, so onGround would stay false until gravity next ticked)
			boss.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0, -0.1, 0));
			boss.setDeltaMovement(Vec3.ZERO);
			boss.fallDistance = 0.0f;
		}
	}

	boolean isScriptedMoving() {
		return moveTicks > 0;
	}

	// ---------------------------------------------------------------- shared helpers

	private void anim(String name) {
		boss.triggerAnim("action", name);
	}

	/** Yellow/white glint off the blade: this one can be dodged or parried-around. */
	private void glint(ServerLevel server, double heightFraction) {
		Vec3 p = boss.position().add(forward().scale(0.6)).add(0, boss.getBbHeight() * heightFraction, 0);
		server.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 6, 0.08, 0.08, 0.08, 0.04);
		server.sendParticles(ParticleTypes.WAX_OFF, p.x, p.y, p.z, 8, 0.2, 0.2, 0.2, 0.5);
	}

	Vec3 forward() {
		Vec3 look = boss.getLookAngle();
		Vec3 f = new Vec3(look.x, 0.0, look.z);
		return f.lengthSqr() < 1.0e-6 ? Vec3.directionFromRotation(0, boss.getYRot()) : f.normalize();
	}

	private static Vec3 horizontal(Vec3 v) {
		Vec3 f = new Vec3(v.x, 0.0, v.z);
		return f.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : f.normalize();
	}

	private static double horizontalDistSqr(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return dx * dx + dz * dz;
	}

	private boolean inFrontCone(Entity other, double arcDegrees) {
		Vec3 to = horizontal(other.position().subtract(boss.position()));
		return to.dot(forward()) >= Math.cos(Math.toRadians(arcDegrees / 2.0));
	}

	/** Phase 2+: the oath is broken and soul fire streams off the blade -- traced along the swing's arc on
	 * its contact frame (server-side, so every client sees the same thing). */
	private void soulTrail(ServerLevel server, Vec3 f, double range, double arcDegrees) {
		if (boss.getPhase() == OathbreakerEntity.Phase.KNIGHT) {
			return;
		}
		double half = Math.toRadians(arcDegrees / 2.0);
		double base = Math.atan2(f.z, f.x);
		Vec3 o = boss.position().add(0, boss.getBbHeight() * 0.55, 0);
		int n = 10;
		for (int i = 0; i < n; i++) {
			double a = base - half + (2.0 * half) * i / (n - 1);
			double r = range * 0.75;
			server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, o.x + Math.cos(a) * r, o.y + (i - n / 2.0) * 0.06,
					o.z + Math.sin(a) * r, 2, 0.05, 0.05, 0.05, 0.01);
		}
	}

	private void resolveArc(ServerLevel server, double range, double arc, float damage, double knockback,
			net.minecraft.sounds.SoundEvent hitSound) {
		boolean hit = false;
		Vec3 f = forward();
		soulTrail(server, f, range, arc);
		for (LivingEntity victim : arcTargets(server, f, range, arc)) {
			hit |= strike(victim, damage, f, knockback);
		}
		if (hit) {
			server.playSound(null, boss.blockPosition(), hitSound, SoundSource.HOSTILE, 1.3f, 1.1f);
		}
		if (boss.getPhase() == OathbreakerEntity.Phase.OATHLESS && (active == Attack.COMBO || active == Attack.STANCE_DASH)) {
			scheduleEcho(f, range, arc, damage, knockback);
		}
	}

	/** Every valid living target within {@code range} inside a forward cone {@code arcDegrees} wide, from where
	 * he stands. One AABB-bounded query. Never the boss itself, never a creative/spectator player. */
	List<LivingEntity> arcTargets(ServerLevel server, Vec3 forward, double range, double arcDegrees) {
		return arcTargets(server, boss.position(), forward, range, arcDegrees);
	}

	/**
	 * Same, from an arbitrary standing position (the Phantom Echo strikes from where he USED to stand).
	 *
	 * <p>The cone and the range are measured on the HORIZONTAL plane from his feet, with a vertical band
	 * from a little below his feet to a little above his head. The first version measured a 3D cone from his
	 * mid-height (2 blocks up on a 4-block knight): anyone standing close enough had to be looked at so
	 * steeply downward that they fell outside the cone -- hugging his legs made you immune to his combos,
	 * and the Execution's lunge carried him so close that the grab whiffed on a player right in front of him.
	 * Anyone practically inside his footprint counts as hit.
	 */
	List<LivingEntity> arcTargets(ServerLevel server, Vec3 feet, Vec3 forward, double range, double arcDegrees) {
		double cos = Math.cos(Math.toRadians(arcDegrees / 2.0));
		double reach = range + 1.0;
		AABB box = new AABB(feet.x - reach, feet.y - 1.5, feet.z - reach, feet.x + reach, feet.y + boss.getBbHeight() + 1.5,
				feet.z + reach);
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity candidate : server.getEntitiesOfClass(LivingEntity.class, box,
				e -> e != boss && e.isAlive() && e.isPickable() && isValidTarget(e))) {
			double dx = candidate.getX() - feet.x;
			double dz = candidate.getZ() - feet.z;
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist > range + candidate.getBbWidth() * 0.5) {
				continue;
			}
			if (dist < boss.getBbWidth() * 0.5 || (dx * forward.x + dz * forward.z) / dist >= cos) {
				out.add(candidate);
			}
		}
		return out;
	}

	boolean strike(LivingEntity target, float damage, Vec3 direction, double knockback) {
		if (TitanCombat.isBoss(target)) {
			damage = Math.min(damage, (float) (target.getMaxHealth() * 0.10));
		}
		if (!target.hurt(boss.damageSources().mobAttack(boss), Math.max(1.0f, damage))) {
			return false;
		}
		target.knockback(knockback, -direction.x, -direction.z);
		return true;
	}
}
