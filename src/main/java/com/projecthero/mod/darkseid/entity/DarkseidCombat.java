package com.projecthero.mod.darkseid.entity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.darkseid.DarkseidConfig;
import com.projecthero.mod.darkseid.DarkseidDamage;
import com.projecthero.mod.darkseid.DarkseidFx;
import com.projecthero.mod.darkseid.DarkseidSounds;
import com.projecthero.mod.darkseid.raid.DarkseidRaid;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * Every one of Darkseid's attacks, and how he picks them.
 *
 * <h2>Shape</h2>
 * One attack runs at a time ({@link #current}), scripted tick by tick against the clip timings in
 * {@link DarkseidAnims} so that each blow lands on the frame the animation shows it landing. Between attacks a
 * phase-scaled global cooldown gives players a readable gap; each attack also has its own cooldown. The choice is
 * a weighted pick from whatever is legal at the current range, height and phase, with a soft anti-repeat -- so the
 * pattern is learnable (close in: fists and the slam; back off: beams, barrage, grip; kite: charge and teleport)
 * without being a fixed rotation.
 *
 * <h2>Fairness rules every move follows</h2>
 * <ul>
 *   <li>Every move has a tell: an animation wind-up plus a particle/sound telegraph, and the targeted ones mark
 *       their victim (Glowing + a message).</li>
 *   <li>Nothing is instant or unavoidable: beams steer only so fast and are stopped by blocks, the barrage and
 *       Annihilation hit where the warning was drawn, the charge locks its direction before it starts, the grip
 *       needs line of sight at the moment it closes, and the sweep can be jumped, flown over or hidden from.</li>
 *   <li>Flight is useful but not a hard counter: the beams, grip, barrage and teleport all reach airborne
 *       targets, and the slam's shockwave reaches {@code groundSlamVerticalRange} blocks up.</li>
 * </ul>
 * Targeted attacks only ever pick official raid participants ({@link DarkseidRaid#combatTargets}); area attacks
 * hurt whoever stands in them.
 */
public final class DarkseidCombat {
	public enum Attack {
		MELEE_1, MELEE_2, MELEE_COMBO, GROUND_SLAM, OMEGA_BEAMS, OMEGA_BARRAGE, GRIP, TELEPORT, CHARGE,
		REINFORCEMENTS, OMEGA_SWEEP, OMEGA_ANNIHILATION
	}

	private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0f, 0.08f, 0.04f), 1.8f);
	private static final DustParticleOptions RED_BIG = new DustParticleOptions(new Vector3f(1.0f, 0.05f, 0.02f), 3.0f);
	private static final DustParticleOptions WARN = new DustParticleOptions(new Vector3f(1.0f, 0.35f, 0.1f), 1.4f);
	private static final DustParticleOptions GRIP_PURPLE = new DustParticleOptions(new Vector3f(0.55f, 0.1f, 0.9f), 1.6f);

	/** Ticks the chosen target is kept before targeting is reconsidered. */
	private static final int TARGET_STICKY_TICKS = 200;

	private final DarkseidEntity boss;

	private Attack current;
	private int t;
	private Attack last;
	private int globalCooldown = 40;
	private final EnumMap<Attack, Long> readyAt = new EnumMap<>(Attack.class);
	private int flinchTicks;
	private int staggerTicks;
	private int approachRepath;

	private LivingEntity target;
	private long targetChosenAt;
	/** Recent damage dealt to him, per player -- the tank draws his attention. Decays every second. */
	private final Map<UUID, Float> threat = new HashMap<>();

	// ---- per-attack state
	private LivingEntity locked;
	private Vec3 lockedDir = Vec3.ZERO;
	private final Set<Integer> hitOnce = new HashSet<>();
	private boolean rushing;
	private double chargeTravelled;
	private int slamWaveTick = -1;
	private final List<Strike> strikes = new ArrayList<>();
	private LivingEntity gripVictim;
	private int gripStage;
	private int gripHold;
	private float gripBreakDamage;
	private final Map<UUID, Long> gripImmuneUntil = new HashMap<>();
	private float sweepYaw;
	private int sweepTicks;
	private int sweepTotal;
	private int sweepBeams;
	private final Map<Integer, Integer> sweepHitAt = new HashMap<>();
	private long nextAnnihilationAt = -1;
	private LivingEntity annihilationTarget;
	private float annihilationDamage;
	private float annihilationThreshold;
	private int teleportHover;
	private final List<LivingEntity> marked = new ArrayList<>();

	/** A pending Omega Barrage impact. */
	private record Strike(Vec3 pos, long detonateAt, long placedAt) {
	}

	DarkseidCombat(DarkseidEntity boss) {
		this.boss = boss;
	}

	// ================================================================ queries

	public boolean isAttacking() {
		return current != null;
	}

	public Attack currentAttack() {
		return current;
	}

	public boolean isStaggered() {
		return staggerTicks > 0;
	}

	/** A short flinch (a hurt reaction, a charge into a wall, a broken grip) -- also holds the pose. */
	public boolean isFlinching() {
		return flinchTicks > 0;
	}

	public boolean isChargingAnnihilation() {
		return current == Attack.OMEGA_ANNIHILATION && t < DarkseidConfig.abilities().omegaAnnihilationChargeTime;
	}

	/** 0..1 -- how much of the damage needed to interrupt Omega Annihilation the team has dealt. */
	public float annihilationInterruptProgress() {
		return annihilationThreshold <= 0 ? 0.0f : Math.min(1.0f, annihilationDamage / annihilationThreshold);
	}

	public LivingEntity target() {
		return target;
	}

	public int pendingStrikes() {
		return strikes.size();
	}

	// ================================================================ multipliers

	private int phase() {
		return boss.getPhase();
	}

	private int enrage() {
		DarkseidRaid raid = boss.raid();
		return raid == null ? 0 : raid.enrageLevel();
	}

	float damageMultiplier() {
		DarkseidConfig.Boss cfg = DarkseidConfig.boss();
		double m = switch (phase()) {
			case 2 -> cfg.damageMultiplierPhase2;
			case 3 -> cfg.damageMultiplierPhase3;
			default -> 1.0;
		};
		return (float) (m * (1.0 + cfg.enrageDamagePerStep * enrage()));
	}

	private double cooldownMultiplier() {
		DarkseidConfig.Boss cfg = DarkseidConfig.boss();
		double m = switch (phase()) {
			case 2 -> cfg.cooldownMultiplierPhase2;
			case 3 -> cfg.cooldownMultiplierPhase3;
			default -> 1.0;
		};
		return m * Math.pow(cfg.enrageCooldownPerStep, enrage());
	}

	private double knockbackMultiplier() {
		return phase() >= 3 ? DarkseidConfig.boss().knockbackMultiplierPhase3 : 1.0;
	}

	private boolean ready(Attack a, long now) {
		return readyAt.getOrDefault(a, 0L) <= now;
	}

	private void cooldown(Attack a, long now, int ticks) {
		readyAt.put(a, now + Math.max(10L, Math.round(ticks * cooldownMultiplier())));
	}

	// ================================================================ tick

	void tick(ServerLevel server) {
		long now = server.getGameTime();
		tickStrikes(server, now);
		if (slamWaveTick >= 0) {
			tickSlamWave(server);
		}
		if (teleportHover > 0 && --teleportHover == 0) {
			boss.setNoGravity(false);
		}
		if (boss.tickCount % 20 == 0) {
			threat.replaceAll((k, v) -> v * 0.85f);
			threat.values().removeIf(v -> v < 0.5f);
			gripImmuneUntil.values().removeIf(until -> until < now);
		}
		if (staggerTicks > 0) {
			tickStagger(server);
			return;
		}
		if (flinchTicks > 0) {
			flinchTicks--;
			return;
		}
		refreshTarget(server, now);
		if (current != null) {
			t++;
			runAttack(server, now);
			return;
		}
		if (globalCooldown > 0) {
			globalCooldown--;
		}
		if (target == null) {
			boss.getNavigation().stop();
			return;
		}
		approach(server);
		if (globalCooldown <= 0) {
			Attack next = choose(server, now);
			if (next != null) {
				begin(server, next, now);
			}
		}
	}

	// ================================================================ targeting

	private List<? extends LivingEntity> candidates(ServerLevel server) {
		DarkseidRaid raid = boss.raid();
		if (raid != null) {
			return raid.combatTargets();
		}
		return server.getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(48.0),
				p -> DarkseidDamage.isValidVictim(p));
	}

	private boolean stillValid(LivingEntity e, List<? extends LivingEntity> pool) {
		return e != null && e.isAlive() && !e.isRemoved() && e.level() == boss.level() && pool.contains(e);
	}

	private void refreshTarget(ServerLevel server, long now) {
		List<? extends LivingEntity> pool = candidates(server);
		if (pool.isEmpty()) {
			target = null;
			boss.setTarget(null);
			return;
		}
		if (stillValid(target, pool) && now - targetChosenAt < TARGET_STICKY_TICKS) {
			return;
		}
		LivingEntity best = null;
		double bestScore = -1;
		for (LivingEntity e : pool) {
			double d = Math.sqrt(e.distanceToSqr(boss));
			double score = threat.getOrDefault(e.getUUID(), 0.0f) * 0.6 + 40.0 / (1.0 + d) + boss.getRandom().nextDouble() * 2.0;
			if (e == target) {
				score += 3.0;
			}
			if (score > bestScore) {
				bestScore = score;
				best = e;
			}
		}
		target = best;
		targetChosenAt = now;
		boss.setTarget(best);
	}

	private double hDist(Entity e) {
		double dx = e.getX() - boss.getX();
		double dz = e.getZ() - boss.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private double meleeReach(LivingEntity e) {
		return 2.4 + boss.getBbWidth() * 0.5 + e.getBbWidth() * 0.5;
	}

	private static boolean airborne(LivingEntity e) {
		return !e.onGround() && !e.isInWater();
	}

	private boolean lineOfSight(ServerLevel server, Vec3 from, Vec3 to) {
		return server.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss))
				.getType() == HitResult.Type.MISS;
	}

	private void face(Vec3 at) {
		double dx = at.x - boss.getX();
		double dz = at.z - boss.getZ();
		if (dx * dx + dz * dz < 1.0e-4) {
			return;
		}
		float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
		boss.setYRot(yaw);
		boss.yBodyRot = yaw;
		boss.yHeadRot = yaw;
	}

	private void approach(ServerLevel server) {
		boss.getLookControl().setLookAt(target, 30.0f, 30.0f);
		DarkseidRaid raid = boss.raid();
		if (raid != null) {
			BlockPos c = raid.center();
			double dx = boss.getX() - (c.getX() + 0.5);
			double dz = boss.getZ() - (c.getZ() + 0.5);
			if (dx * dx + dz * dz > raid.radius() * raid.radius()) {
				if (--approachRepath <= 0) {
					approachRepath = 20;
					boss.getNavigation().moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 1.0);
				}
				return;
			}
		}
		if (hDist(target) <= meleeReach(target) * 0.8) {
			boss.getNavigation().stop();
			return;
		}
		if (--approachRepath <= 0 || boss.getNavigation().isDone()) {
			approachRepath = 10;
			if (airborne(target) && target.getY() - boss.getY() > 4.0) {
				boss.getNavigation().moveTo(target.getX(), boss.getY(), target.getZ(), 1.0);
			} else {
				boss.getNavigation().moveTo(target, 1.0);
			}
		}
	}

	// ================================================================ choosing

	private Attack choose(ServerLevel server, long now) {
		DarkseidConfig.Abilities cfg = DarkseidConfig.abilities();
		int phase = phase();
		if (phase >= 3) {
			if (nextAnnihilationAt < 0) {
				nextAnnihilationAt = now + 15 * 20; // the first one comes early, so everyone learns it
			}
			if (now >= nextAnnihilationAt && !candidates(server).isEmpty()) {
				return Attack.OMEGA_ANNIHILATION;
			}
		}
		double d = hDist(target);
		double dy = target.getY() - boss.getY();
		boolean air = airborne(target) && dy > 3.0;
		double reach = meleeReach(target);
		boolean shielded = phase == 0;
		DarkseidRaid raid = boss.raid();

		Map<Attack, Double> w = new EnumMap<>(Attack.class);
		if (d <= reach && Math.abs(dy) < 3.5) {
			w.put(Attack.MELEE_1, 4.0);
			w.put(Attack.MELEE_2, 3.0);
			if (!shielded) {
				w.put(Attack.MELEE_COMBO, 3.0);
			}
		}
		if (d <= cfg.groundSlamRadius * 0.7 && dy < cfg.groundSlamVerticalRange) {
			w.put(Attack.GROUND_SLAM, d <= reach ? 2.0 : 3.0);
		}
		if (d >= 3.0 || air) {
			// v0.13.19: his signature move comes up far more often (was 6.0 / 4.5); v0.14.26: more again, from 3 blocks
			// out, and even behind the Mother Box shield
			w.put(Attack.OMEGA_BEAMS, air ? cfg.omegaBeamAirWeight : cfg.omegaBeamWeight);
		}
		if (d >= 5.0 || air) {
			w.put(Attack.OMEGA_BARRAGE, shielded ? 4.0 : 3.0);
		}
		LivingEntity gripCandidate = gripCandidate(server, now);
		if (gripCandidate != null) {
			w.put(Attack.GRIP, shielded ? 5.0 : 3.0);
		}
		if (!shielded && (d > cfg.teleportMinDistance || (air && dy > 8.0))) {
			w.put(Attack.TELEPORT, 6.0);
		}
		if (!shielded && d >= 8.0 && d <= cfg.chargeDistance * 0.85 && !air && Math.abs(dy) < 3.0) {
			w.put(Attack.CHARGE, 3.0);
		}
		if (!shielded && raid != null && raid.enemiesAlive(server) < raid.enemyCap() / 2) {
			w.put(Attack.REINFORCEMENTS, cfg.reinforcementWeight + (phase >= 2 ? 1.0 : 0.0)); // v0.13.19: was 2.0
		}
		if (phase >= 2) {
			w.put(Attack.OMEGA_SWEEP, 2.5);
		}

		double total = 0;
		for (Iterator<Map.Entry<Attack, Double>> it = w.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Attack, Double> e = it.next();
			if (!ready(e.getKey(), now)) {
				it.remove();
				continue;
			}
			if (e.getKey() == last) {
				e.setValue(e.getValue() * 0.3);
			}
			total += e.getValue();
		}
		if (total <= 0) {
			return null;
		}
		double roll = boss.getRandom().nextDouble() * total;
		for (Map.Entry<Attack, Double> e : w.entrySet()) {
			roll -= e.getValue();
			if (roll <= 0) {
				if (e.getKey() == Attack.GRIP) {
					locked = gripCandidate;
				}
				return e.getKey();
			}
		}
		return null;
	}

	/** A grip needs range and line of sight, and never picks someone just released. Channelers go first. */
	private LivingEntity gripCandidate(ServerLevel server, long now) {
		if (!ready(Attack.GRIP, now)) {
			return null;
		}
		DarkseidRaid raid = boss.raid();
		List<LivingEntity> order = new ArrayList<>();
		if (raid != null) {
			order.addAll(raid.channelers(server));
		}
		order.add(target);
		double range = DarkseidConfig.abilities().gripRange;
		for (LivingEntity e : order) {
			if (e == null || !e.isAlive() || gripImmuneUntil.getOrDefault(e.getUUID(), 0L) > now) {
				continue;
			}
			double dist = e.distanceTo(boss);
			if (dist < 5.0 || dist > range) {
				continue;
			}
			if (lineOfSight(server, boss.eyePosition(), e.getEyePosition())) {
				return e;
			}
		}
		return null;
	}

	// ================================================================ begin / end

	private void begin(ServerLevel server, Attack attack, long now) {
		current = attack;
		t = 0;
		last = attack;
		hitOnce.clear();
		rushing = false;
		if (attack != Attack.GRIP) {
			locked = target;
		}
		boss.getNavigation().stop();
		DarkseidConfig.Abilities cfg = DarkseidConfig.abilities();
		switch (attack) {
			case MELEE_1 -> {
				boss.triggerAnim("action", DarkseidAnims.MELEE_1);
				cooldown(attack, now, 20); // v0.14.26: faster (was 30)
			}
			case MELEE_2 -> {
				boss.triggerAnim("action", DarkseidAnims.MELEE_2);
				cooldown(attack, now, 24); // v0.14.26: faster (was 36)
			}
			case MELEE_COMBO -> {
				boss.triggerAnim("action", DarkseidAnims.MELEE_COMBO);
				cooldown(attack, now, 60); // v0.14.26: faster (was 90)
			}
			case GROUND_SLAM -> {
				boss.triggerAnim("action", DarkseidAnims.GROUND_SLAM);
				server.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.5f, 0.6f);
				cooldown(attack, now, cfg.groundSlamCooldown);
			}
			case OMEGA_BEAMS -> {
				boss.triggerAnim("action", DarkseidAnims.BEAM_CHARGE);
				boss.setOmegaState(1);
				server.playSound(null, boss.blockPosition(), DarkseidSounds.OMEGA_CHARGE, SoundSource.HOSTILE, 3.0f, 0.7f);
				mark(locked, cfg.omegaBeamChargeTicks + 30);
				tell(locked, "message.projecthero.darkseid.omega_mark", ChatFormatting.RED);
				cooldown(attack, now, cfg.omegaBeamCooldown);
			}
			case OMEGA_BARRAGE -> {
				boss.triggerAnim("action", DarkseidAnims.BARRAGE);
				server.playSound(null, boss.blockPosition(), DarkseidSounds.OMEGA_CHARGE, SoundSource.HOSTILE, 2.0f, 1.1f);
				cooldown(attack, now, cfg.omegaBarrageCooldown);
			}
			case GRIP -> {
				gripVictim = locked;
				gripStage = 0;
				gripHold = 0;
				gripBreakDamage = 0;
				boss.triggerAnim("action", DarkseidAnims.GRIP);
				server.playSound(null, boss.blockPosition(), DarkseidSounds.GRIP, SoundSource.HOSTILE, 2.5f, 0.5f);
				tell(gripVictim, "message.projecthero.darkseid.grip_warn", ChatFormatting.LIGHT_PURPLE);
				cooldown(attack, now, cfg.gripCooldown);
			}
			case TELEPORT -> {
				boss.triggerAnim("action", DarkseidAnims.TELEPORT);
				server.playSound(null, boss.blockPosition(), DarkseidSounds.TELEPORT, SoundSource.HOSTILE, 2.5f, 0.6f);
				cooldown(attack, now, cfg.teleportCooldown);
			}
			case CHARGE -> {
				Vec3 to = locked.position().subtract(boss.position());
				lockedDir = new Vec3(to.x, 0, to.z).normalize();
				boss.triggerAnim("action", DarkseidAnims.CHARGE);
				server.playSound(null, boss.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5f, 0.55f);
				cooldown(attack, now, cfg.chargeCooldown);
			}
			case REINFORCEMENTS -> {
				boss.triggerAnim("action", DarkseidAnims.SUMMON);
				server.playSound(null, boss.blockPosition(), DarkseidSounds.PHASE, SoundSource.HOSTILE, 2.0f, 0.9f);
				cooldown(attack, now, cfg.reinforcementCooldown);
			}
			case OMEGA_SWEEP -> {
				boss.triggerAnim("action", DarkseidAnims.BEAM_CHARGE);
				boss.setOmegaState(1);
				Vec3 to = locked.position().subtract(boss.position());
				sweepYaw = (float) (Mth.atan2(to.z, to.x) * (180.0 / Math.PI)) - 90.0f;
				sweepBeams = phase() >= 3 ? 2 : 1;
				sweepTotal = (int) Math.ceil(360.0 / Math.max(0.5, cfg.omegaSweepRotationSpeed));
				sweepTicks = 0;
				sweepHitAt.clear();
				server.playSound(null, boss.blockPosition(), DarkseidSounds.OMEGA_CHARGE, SoundSource.HOSTILE, 4.0f, 0.5f);
				DarkseidRaid raid = boss.raid();
				for (LivingEntity e : candidates(server)) {
					tell(e, "message.projecthero.darkseid.sweep_warn", ChatFormatting.RED);
				}
				if (raid == null) {
					tell(locked, "message.projecthero.darkseid.sweep_warn", ChatFormatting.RED);
				}
				cooldown(attack, now, cfg.omegaSweepCooldown);
			}
			case OMEGA_ANNIHILATION -> beginAnnihilation(server, now);
		}
	}

	private void end(ServerLevel server) {
		Attack was = current;
		current = null;
		t = 0;
		rushing = false;
		boss.setOmegaState(0);
		if (was == Attack.OMEGA_SWEEP) {
			boss.setSweep(Float.NaN, 1);
		}
		boss.setNoGravity(teleportHover > 0);
		int base = DarkseidConfig.boss().globalCooldownTicks;
		globalCooldown = (int) Math.max(8, Math.round(base * cooldownMultiplier()));
	}

	/** Abort everything (phase transition, death, removal). Releases a gripped player and clears telegraphs. */
	void cancel(ServerLevel server) {
		if (gripVictim != null) {
			gripVictim = null;
		}
		strikes.clear();
		slamWaveTick = -1;
		unmarkAll();
		current = null;
		t = 0;
		rushing = false;
		staggerTicks = 0;
		boss.setOmegaState(0);
		boss.setSweep(Float.NaN, 1);
		boss.setNoGravity(false);
		teleportHover = 0;
		globalCooldown = 30;
	}

	void flinch(int ticks) {
		if (current == null && staggerTicks <= 0) {
			flinchTicks = Math.max(flinchTicks, ticks);
		}
	}

	// ================================================================ incoming damage hooks

	void noteDamageTaken(ServerLevel server, DamageSource source, float taken) {
		if (taken <= 0) {
			return;
		}
		Entity attacker = source.getEntity();
		if (attacker instanceof Player player) {
			threat.merge(player.getUUID(), taken, Float::sum);
		}
		if (isChargingAnnihilation()) {
			annihilationDamage += taken;
			if (annihilationDamage >= annihilationThreshold) {
				interruptAnnihilation(server);
			}
		}
		if (current == Attack.GRIP && gripStage == 1) {
			gripBreakDamage += taken;
		}
	}

	// ================================================================ attack scripts

	private void runAttack(ServerLevel server, long now) {
		switch (current) {
			case MELEE_1 -> {
				trackBeforeImpact(DarkseidAnims.MELEE_1_IMPACT);
				if (t == DarkseidAnims.MELEE_1_IMPACT) {
					meleeArc(server, 1.0f, 1.2, 0.3, 110.0);
				}
				if (t >= DarkseidAnims.MELEE_1_TICKS) {
					end(server);
				}
			}
			case MELEE_2 -> {
				trackBeforeImpact(DarkseidAnims.MELEE_2_IMPACT);
				if (t == DarkseidAnims.MELEE_2_IMPACT) {
					meleeArc(server, 1.1f, 1.7, 0.4, 130.0);
				}
				if (t >= DarkseidAnims.MELEE_2_TICKS) {
					end(server);
				}
			}
			case MELEE_COMBO -> runCombo(server);
			case GROUND_SLAM -> runSlam(server);
			case OMEGA_BEAMS -> runBeams(server);
			case OMEGA_BARRAGE -> runBarrage(server, now);
			case GRIP -> runGrip(server, now);
			case TELEPORT -> runTeleport(server);
			case CHARGE -> runCharge(server);
			case REINFORCEMENTS -> runReinforcements(server);
			case OMEGA_SWEEP -> runSweep(server);
			case OMEGA_ANNIHILATION -> runAnnihilation(server, now);
		}
	}

	/** Keeps facing the locked target until a few ticks before the blow -- after that the swing is committed. */
	private void trackBeforeImpact(int impact) {
		if (locked != null && t < impact - 3) {
			face(locked.position());
		}
	}

	// ---------------------------------------------------------------- melee

	private void runCombo(ServerLevel server) {
		int[] impacts = DarkseidAnims.MELEE_COMBO_IMPACTS;
		for (int i = 0; i < impacts.length; i++) {
			if (t == impacts[i] - 4 && locked != null) {
				face(locked.position());
				boss.setDeltaMovement(boss.forward().scale(0.3).add(0, boss.getDeltaMovement().y, 0));
			}
			if (t == impacts[i]) {
				hitOnce.clear();
				boolean last = i == impacts.length - 1;
				meleeArc(server, last ? 1.4f : 0.8f, last ? 2.2 : 0.9, last ? 0.7 : 0.25, last ? 150.0 : 110.0);
				if (last) {
					Vec3 f = boss.position().add(boss.forward().scale(2.5));
					groundBurst(server, f, 2.5);
					DarkseidFx.shake(server, boss.position(), 24.0, 0.6f, 8);
				}
			}
		}
		if (t >= DarkseidAnims.MELEE_COMBO_TICKS) {
			end(server);
		}
	}

	/** One fist: everything in front of him within reach, inside {@code arcDegrees}. */
	private void meleeArc(ServerLevel server, float damageScale, double knock, double lift, double arcDegrees) {
		Vec3 f = boss.forward();
		double reach = 2.4 + boss.getBbWidth() * 0.5 + 0.6;
		double cos = Math.cos(Math.toRadians(arcDegrees / 2.0));
		// v0.14.26: flat damage tiers -- jabs tier 1, the heavy hook tier 2, the combo finisher tier 3
		float damage = damageScale >= 1.3f ? DarkseidConfig.tier(3) : damageScale >= 1.05f ? DarkseidConfig.tier(2) : DarkseidConfig.tier(1);
		boolean any = false;
		for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class,
				boss.getBoundingBox().inflate(reach, 2.0, reach), DarkseidDamage::isValidVictim)) {
			double dx = e.getX() - boss.getX();
			double dz = e.getZ() - boss.getZ();
			double dist = Math.sqrt(dx * dx + dz * dz);
			// measured horizontally from his feet (the Oathbreaker lesson: a tall boss's mid-height misses huggers)
			if (dist > reach + e.getBbWidth() * 0.5 || !hitOnce.add(e.getId())) {
				continue;
			}
			if (dist > 0.8 && (dx * f.x + dz * f.z) / dist < cos) {
				continue;
			}
			if (e.hurt(server.damageSources().mobAttack(boss), damage)) {
				DarkseidDamage.knockAway(e, boss.position(), knock * knockbackMultiplier(), lift);
				any = true;
			}
		}
		server.playSound(null, boss.blockPosition(), any ? DarkseidSounds.PUNCH : SoundEvents.PLAYER_ATTACK_SWEEP,
				SoundSource.HOSTILE, 2.0f, any ? 0.7f : 0.5f);
		Vec3 fist = boss.position().add(f.scale(reach * 0.8)).add(0, boss.getBbHeight() * 0.5, 0);
		server.sendParticles(ParticleTypes.SWEEP_ATTACK, fist.x, fist.y, fist.z, 1, 0, 0, 0, 0);
		server.sendParticles(RED, fist.x, fist.y, fist.z, 6, 0.4, 0.4, 0.4, 0.0);
	}

	private void groundBurst(ServerLevel server, Vec3 at, double radius) {
		BlockPos below = BlockPos.containing(at.x, boss.getY() - 0.5, at.z);
		var state = server.getBlockState(below);
		if (!state.isAir()) {
			server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, boss.getY() + 0.2, at.z,
					30, radius * 0.4, 0.2, radius * 0.4, 0.2);
		}
		server.sendParticles(ParticleTypes.EXPLOSION, at.x, boss.getY() + 0.3, at.z, 2, 0.5, 0.1, 0.5, 0.0);
	}

	// ---------------------------------------------------------------- Godly Ground Slam

	private void runSlam(ServerLevel server) {
		DarkseidConfig.Abilities cfg = DarkseidConfig.abilities();
		trackBeforeImpact(DarkseidAnims.GROUND_SLAM_IMPACT);
		if (t < DarkseidAnims.GROUND_SLAM_IMPACT && t % 5 == 0) {
			// the telegraph: the shockwave's full reach drawn on the ground, pulsing
			DarkseidFx.ring(server, WARN, boss.position().add(0, 0.2, 0), cfg.groundSlamRadius, 36);
			server.sendParticles(RED, boss.getX(), boss.getY() + boss.getBbHeight() * 0.8, boss.getZ(), 6, 0.6, 0.3, 0.6, 0.0);
		}
		if (t == DarkseidAnims.GROUND_SLAM_IMPACT) {
			slamWaveTick = 0;
			hitOnce.clear();
			server.playSound(null, boss.blockPosition(), DarkseidSounds.SLAM, SoundSource.HOSTILE, 4.0f, 0.6f);
			server.playSound(null, boss.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5f, 0.5f);
			DarkseidFx.shake(server, boss.position(), 40.0, 1.4f, 16);
			groundBurst(server, boss.position().add(boss.forward().scale(1.5)), 4.0);
			server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, boss.getX(), boss.getY() + 0.5, boss.getZ(), 1, 0, 0, 0, 0);
		}
		if (t >= DarkseidAnims.GROUND_SLAM_TICKS) {
			end(server);
		}
	}

	/** The slam's shockwave: a ring racing out over 12 ticks, hitting each victim once as its front passes. */
	private void tickSlamWave(ServerLevel server) {
		DarkseidConfig.Abilities cfg = DarkseidConfig.abilities();
		slamWaveTick++;
		double max = cfg.groundSlamRadius;
		double r = max * Math.min(1.0, slamWaveTick / 12.0);
		Vec3 c = boss.position();
		DarkseidFx.ring(server, ParticleTypes.CLOUD, c.add(0, 0.3, 0), r, Math.max(10, (int) (r * 3)));
		if (slamWaveTick % 3 == 0) {
			DarkseidFx.ring(server, RED, c.add(0, 0.6, 0), r, Math.max(8, (int) (r * 2)));
		}
		float base = DarkseidConfig.tier(2); // v0.14.26
		double vertical = cfg.groundSlamVerticalRange;
		for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(c.x - max, c.y - 3, c.z - max,
				c.x + max, c.y + vertical + 1, c.z + max), DarkseidDamage::isValidVictim)) {
			double dx = e.getX() - c.x;
			double dz = e.getZ() - c.z;
			double d = Math.sqrt(dx * dx + dz * dz);
			double dy = e.getY() - c.y;
			if (d > r || dy < -2.5 || dy > vertical || !hitOnce.add(e.getId())) {
				continue;
			}
			float dmg = (float) (base * (1.0 - 0.5 * d / max));
			if (airborne(e) && dy > 1.5) {
				dmg *= (float) cfg.groundSlamAirborneMultiplier;
			}
			if (e.hurt(server.damageSources().mobAttack(boss), dmg)) {
				DarkseidDamage.knockAway(e, c, 1.8 * knockbackMultiplier(), 0.7);
			}
		}
		if (slamWaveTick >= 12) {
			slamWaveTick = -1;
		}
	}

	// ---------------------------------------------------------------- Omega Beams

	private void runBeams(ServerLevel server) {
		DarkseidConfig.Abilities cfg = DarkseidConfig.abilities();
		int charge = cfg.omegaBeamChargeTicks;
		if (t < charge) {
			if (locked != null) {
				face(locked.position());
				boss.getLookControl().setLookAt(locked, 30.0f, 30.0f);
				if (t % 4 == 0) {
					DarkseidFx.column(server, RED, locked.position().add(0, locked.getBbHeight() + 0.4, 0), 2.5, 6);
				}
			}
			if (t % 2 == 0) {
				Vec3 eyes = boss.eyePosition();
				server.sendParticles(RED_BIG, eyes.x, eyes.y, eyes.z, 3, 0.2, 0.08, 0.2, 0.0);
			}
			return;
		}
		if (t == charge) {
			LivingEntity tgt = locked != null && locked.isAlive() ? locked : target;
			unmarkAll();
			if (tgt == null) {
				end(server);
				return;
			}
			boss.triggerAnim("action", DarkseidAnims.BEAM_FIRE);
			boss.setOmegaState(0);
			Vec3 eyes = boss.eyePosition();
			Vec3 f = boss.forward();
			Vec3 side = new Vec3(-f.z, 0, f.x);
			Vec3 toTarget = tgt.position().add(0, tgt.getBbHeight() * 0.5, 0).subtract(eyes).normalize();
			double turn = cfg.omegaBeamTurnDegrees + (phase() - 1) * 1.5;
			float dmg = DarkseidConfig.tier(2); // v0.14.26
			for (int s = -1; s <= 1; s += 2) {
				Vec3 from = eyes.add(side.scale(0.22 * s));
				// they leave splayed outward, then snake toward the target in sharp zig-zags (v0.13.19) before homing in
				Vec3 dir = toTarget.add(side.scale(0.55 * s)).add(0, 0.2, 0);
				OmegaBeamEntity.fire(server, boss, from, dir, tgt, dmg, cfg.omegaBeamSpeed, turn, cfg.omegaBeamTrackingTime,
						cfg.omegaBeamZigZagTurns);
			}
			server.playSound(null, boss.blockPosition(), DarkseidSounds.OMEGA_FIRE, SoundSource.HOSTILE, 4.0f, 0.8f);
			DarkseidFx.shake(server, boss.position(), 32.0, 0.5f, 8);
		}
		if (t >= charge + DarkseidAnims.BEAM_FIRE_TICKS) {
			end(server);
		}
	}

	// ---------------------------------------------------------------- Omega Barrage

	private void runBarrage(ServerLevel server, long now) {
		DarkseidConfig.Abilities cfg = DarkseidConfig.abilities();
		if (t == DarkseidAnims.BARRAGE_RELEASE) {
			List<? extends LivingEntity> pool = candidates(server);
			int max = 3 + Math.max(0, phase());
			int n = 0;
			Vec3 eyes = boss.eyePosition();
			for (LivingEntity e : pool) {
				if (n >= max || e.distanceTo(boss) > 56.0) {
					continue;
				}
				n++;
				Vec3 here = e.position();
				// one where they are, one where they are heading -- standing still or running straight both lose
				Vec3 vel = e.getDeltaMovement();
				Vec3 lead = new Vec3(vel.x, airborne(e) ? vel.y : 0.0, vel.z).scale(cfg.omegaBarrageWarningTicks * 0.6);
				if (lead.length() > 7.0) {
					lead = lead.normalize().scale(7.0);
				}
				placeStrike(server, here, now, cfg.omegaBarrageWarningTicks);
				if (lead.length() > 1.5) {
					placeStrike(server, here.add(lead), now, cfg.omegaBarrageWarningTicks + 6);
				}
				DarkseidFx.line(server, RED, eyes, here.add(0, 1.0, 0), 1.2, 40);
			}
			server.playSound(null, boss.blockPosition(), DarkseidSounds.OMEGA_FIRE, SoundSource.HOSTILE, 3.0f, 1.3f);
		}
		if (t >= DarkseidAnims.BARRAGE_TICKS) {
			end(server);
		}
	}

	private void placeStrike(ServerLevel server, Vec3 pos, long now, int warning) {
		// a strike on the ground sits on the ground; one in the air stays in the air (a flyer is not exempt)
		strikes.add(new Strike(pos, now + warning, now));
	}

	private void tickStrikes(ServerLevel server, long now) {
		if (strikes.isEmpty()) {
			return;
		}
		DarkseidConfig.Abilities cfg = DarkseidConfig.abilities();
		double radius = cfg.omegaBarrageRadius;
		Iterator<Strike> it = strikes.iterator();
		while (it.hasNext()) {
			Strike s = it.next();
			long left = s.detonateAt() - now;
			if (left > 0) {
				if (now % 3 == 0) {
					DarkseidFx.ring(server, WARN, s.pos().add(0, 0.15, 0), radius, 18);
					double inner = radius * (left / (double) Math.max(1, s.detonateAt() - s.placedAt()));
					DarkseidFx.ring(server, RED, s.pos().add(0, 0.2, 0), Math.max(0.3, radius - inner), 10);
				}
				continue;
			}
			it.remove();
			Vec3 p = s.pos();
			DarkseidFx.line(server, RED_BIG, p.add(0, 14, 0), p, 1.0, 16);
			server.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y + 0.5, p.z, 3, radius * 0.4, 0.3, radius * 0.4, 0.0);
			server.sendParticles(RED, p.x, p.y + 0.5, p.z, 20, radius * 0.5, 0.5, radius * 0.5, 0.0);
			server.playSound(null, p.x, p.y, p.z, DarkseidSounds.OMEGA_IMPACT, SoundSource.HOSTILE, 2.0f, 0.9f);
			float dmg = DarkseidConfig.tier(1); // v0.14.26
			for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class,
					new AABB(p, p).inflate(radius, 2.5, radius), DarkseidDamage::isValidVictim)) {
				double dx = e.getX() - p.x;
				double dz = e.getZ() - p.z;
				double dy = e.getY() - p.y;
				if (dx * dx + dz * dz > radius * radius || dy < -2.0 || dy > 2.5) {
					continue;
				}
				if (e.hurt(DarkseidDamage.omega(server, boss, boss), dmg)) {
					DarkseidDamage.knockAway(e, p, 0.8, 0.4);
				}
			}
		}
	}

	// ---------------------------------------------------------------- Darkseid's Grip

	private Vec3 holdPoint() {
		return boss.position().add(boss.forward().scale(boss.getBbWidth() * 0.5 + 2.6)).add(0, boss.getBbHeight() * 0.7, 0);
	}

	private void runGrip(ServerLevel server, long now) {
		DarkseidConfig.Abilities cfg = DarkseidConfig.abilities();
		LivingEntity v = gripVictim;
		if (v == null || !v.isAlive() || v.isRemoved() || v.level() != boss.level()) {
			gripVictim = null;
			end(server);
			return;
		}
		switch (gripStage) {
			case 0 -> {
				// reaching: the telegraph is the purple line from his hand to the victim
				face(v.position());
				if (t % 2 == 0) {
					DarkseidFx.line(server, GRIP_PURPLE, holdPoint(), v.position().add(0, v.getBbHeight() * 0.5, 0), 1.5, 20);
				}
				if (t >= DarkseidAnims.GRIP_REACH_TICKS) {
					boolean inReach = v.distanceTo(boss) <= cfg.gripRange + 2.0;
					if (inReach && lineOfSight(server, boss.eyePosition(), v.getEyePosition())) {
						gripStage = 1;
						gripHold = 0;
						gripBreakDamage = 0;
						server.playSound(null, v.blockPosition(), DarkseidSounds.GRIP, SoundSource.HOSTILE, 2.0f, 0.7f);
						tell(v, "message.projecthero.darkseid.gripped", ChatFormatting.LIGHT_PURPLE);
					} else {
						// broke line of sight in time -- the grip closes on nothing
						server.sendParticles(GRIP_PURPLE, v.getX(), v.getY() + 1, v.getZ(), 10, 0.4, 0.5, 0.4, 0.0);
						gripVictim = null;
						end(server);
					}
				}
			}
			case 1 -> {
				face(v.position());
				Vec3 hold = holdPoint();
				Vec3 pull = hold.subtract(v.position());
				double len = pull.length();
				Vec3 vel = len > 1.1 ? pull.scale(1.1 / len) : pull.scale(0.35);
				v.setDeltaMovement(vel);
				v.hurtMarked = true;
				v.fallDistance = 0.0f;
				if (gripHold % 5 == 0) {
					v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 3, true, false));
					server.sendParticles(GRIP_PURPLE, v.getX(), v.getY() + v.getBbHeight() * 0.5, v.getZ(), 6, 0.35, 0.5, 0.35, 0.0);
				}
				int steps = Math.max(1, cfg.gripDuration / 10);
				if (gripHold > 0 && gripHold % 10 == 0) {
					v.invulnerableTime = 0;
					v.hurt(server.damageSources().mobAttack(boss), DarkseidConfig.tier(1) / steps);
					v.setDeltaMovement(vel);
					v.hurtMarked = true;
				}
				gripHold++;
				double breakAt = boss.getMaxHealth() * cfg.gripBreakDamageFraction;
				if (gripBreakDamage >= breakAt) {
					// the team broke his concentration: he drops them and reels
					gripImmuneUntil.put(v.getUUID(), now + cfg.gripImmunityTicks);
					v.setDeltaMovement(0, -0.2, 0);
					v.hurtMarked = true;
					for (LivingEntity e : candidates(server)) {
						tell(e, "message.projecthero.darkseid.grip_broken", ChatFormatting.GOLD);
					}
					server.playSound(null, boss.blockPosition(), SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 2.0f, 0.6f);
					gripVictim = null;
					end(server);
					boss.triggerAnim("action", DarkseidAnims.HURT);
					flinch(24);
					return;
				}
				if (gripHold >= cfg.gripDuration) {
					gripStage = 2;
					t = 0;
					boss.triggerAnim("action", DarkseidAnims.GRIP_THROW);
				}
			}
			default -> {
				if (t < DarkseidAnims.GRIP_THROW_RELEASE) {
					v.setDeltaMovement(holdPoint().subtract(v.position()).scale(0.4));
					v.hurtMarked = true;
					v.fallDistance = 0.0f;
				}
				if (t == DarkseidAnims.GRIP_THROW_RELEASE) {
					Vec3 f = boss.forward();
					double k = 2.4 * knockbackMultiplier();
					v.setDeltaMovement(f.x * k, 0.9, f.z * k);
					v.hurtMarked = true;
					v.fallDistance = 0.0f;
					gripImmuneUntil.put(v.getUUID(), now + cfg.gripImmunityTicks);
					server.playSound(null, boss.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 2.0f, 0.5f);
					server.sendParticles(ParticleTypes.CLOUD, v.getX(), v.getY() + 1, v.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
				}
				if (t >= DarkseidAnims.GRIP_THROW_TICKS) {
					gripVictim = null;
					end(server);
				}
			}
		}
	}

	// ---------------------------------------------------------------- Omega Teleport

	private void runTeleport(ServerLevel server) {
		int arrive = DarkseidAnims.TELEPORT_TICKS;
		if (t < arrive) {
			if (t % 2 == 0) {
				server.sendParticles(ParticleTypes.REVERSE_PORTAL, boss.getX(), boss.getY() + boss.getBbHeight() * 0.5, boss.getZ(),
						12, boss.getBbWidth() * 0.5, boss.getBbHeight() * 0.4, boss.getBbWidth() * 0.5, 0.05);
			}
			return;
		}
		if (t == arrive) {
			LivingEntity tgt = locked != null && locked.isAlive() ? locked : target;
			Vec3 dest = tgt == null ? null : teleportDestination(server, tgt);
			if (dest == null) {
				end(server);
				return;
			}
			Vec3 from = boss.position();
			BoomTubeEntity.open(server, from.add(0, boss.getBbHeight() * 0.5, 0), BoomTubeEntity.Kind.FLASH, 2.5f, 16, boss.raidId());
			boss.teleportTo(dest.x, dest.y, dest.z);
			boss.setDeltaMovement(Vec3.ZERO);
			boss.getNavigation().stop();
			if (airborne(tgt) && tgt.getY() - dest.y < 3.0 && !server.getBlockState(BlockPos.containing(dest).below()).isSolid()) {
				boss.setNoGravity(true);
				teleportHover = 28;
			}
			BoomTubeEntity.open(server, dest.add(0, boss.getBbHeight() * 0.5, 0), BoomTubeEntity.Kind.FLASH, 2.5f, 16, boss.raidId());
			face(tgt.position());
			boss.triggerAnim("action", DarkseidAnims.TELEPORT_ARRIVE);
			server.playSound(null, from.x, from.y, from.z, DarkseidSounds.TELEPORT, SoundSource.HOSTILE, 3.0f, 0.5f);
			server.playSound(null, dest.x, dest.y, dest.z, DarkseidSounds.BOOM_TUBE, SoundSource.HOSTILE, 3.0f, 0.9f);
			server.sendParticles(ParticleTypes.FLASH, dest.x, dest.y + 2, dest.z, 1, 0, 0, 0, 0);
			locked = tgt;
		}
		if (t == arrive + 6 && locked != null) {
			face(locked.position());
			hitOnce.clear();
			meleeArc(server, 1.1f, 1.8, 0.5, 140.0);
		}
		if (t >= arrive + DarkseidAnims.TELEPORT_ARRIVE_TICKS) {
			end(server);
		}
	}

	/**
	 * Where to arrive: just beside the target, on the ground if they are near it, otherwise hanging in the air at
	 * their height (he hovers briefly and swings). Never further than {@code teleportMaxDistance} from the arena.
	 */
	private Vec3 teleportDestination(ServerLevel server, LivingEntity tgt) {
		DarkseidRaid raid = boss.raid();
		if (raid != null) {
			BlockPos c = raid.center();
			double max = DarkseidConfig.abilities().teleportMaxDistance;
			if (tgt.distanceToSqr(c.getX() + 0.5, tgt.getY(), c.getZ() + 0.5) > max * max) {
				return null;
			}
		}
		Vec3 look = tgt.getLookAngle();
		Vec3 back = new Vec3(-look.x, 0, -look.z);
		if (back.lengthSqr() < 1.0e-3) {
			back = new Vec3(1, 0, 0);
		}
		back = back.normalize();
		for (int i = 0; i < 8; i++) {
			double ang = i * (Math.PI / 4.0);
			Vec3 dir = new Vec3(back.x * Math.cos(ang) - back.z * Math.sin(ang), 0, back.x * Math.sin(ang) + back.z * Math.cos(ang));
			Vec3 xz = tgt.position().add(dir.scale(3.2));
			// the ground under them if they are near it, otherwise their own height
			Vec3 spot = null;
			for (int dy = 1; dy >= -6; dy--) {
				BlockPos p = BlockPos.containing(xz.x, tgt.getY() + dy, xz.z);
				if (server.getBlockState(p.below()).isFaceSturdy(server, p.below(), net.minecraft.core.Direction.UP)) {
					spot = new Vec3(xz.x, p.getY(), xz.z);
					break;
				}
			}
			if (spot == null) {
				spot = new Vec3(xz.x, tgt.getY() - 1.0, xz.z);
			}
			AABB box = boss.getBoundingBox().move(spot.subtract(boss.position()));
			if (server.noCollision(boss, box) && server.isLoaded(BlockPos.containing(spot))) {
				return spot;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- Apokoliptian Charge

	private void runCharge(ServerLevel server) {
		DarkseidConfig.Abilities cfg = DarkseidConfig.abilities();
		int windup = DarkseidAnims.CHARGE_WINDUP_TICKS;
		if (!rushing) {
			face(boss.position().add(lockedDir));
			if (t % 3 == 0) {
				// the lane he is about to run down, drawn on the ground
				Vec3 start = boss.position().add(0, 0.15, 0);
				DarkseidFx.line(server, WARN, start, start.add(lockedDir.scale(cfg.chargeDistance)), 1.5, 24);
				server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, boss.getX(), boss.getY() + 0.2, boss.getZ(), 2, 0.5, 0.0, 0.5, 0.01);
			}
			if (t >= windup) {
				rushing = true;
				chargeTravelled = 0;
				hitOnce.clear();
				boss.triggerAnim("action", DarkseidAnims.CHARGE_RUSH);
				server.playSound(null, boss.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0f, 0.7f);
			}
			return;
		}
		double speed = cfg.chargeSpeed;
		boss.setDeltaMovement(lockedDir.x * speed, boss.getDeltaMovement().y, lockedDir.z * speed);
		chargeTravelled += speed;
		face(boss.position().add(lockedDir));
		if (t % 3 == 0) {
			server.playSound(null, boss.blockPosition(), DarkseidSounds.STEP, SoundSource.HOSTILE, 2.0f, 0.8f);
			groundBurst(server, boss.position(), 1.5);
		}
		float dmg = DarkseidConfig.tier(3); // v0.14.26
		for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, boss.getBoundingBox().inflate(0.9, 0.2, 0.9),
				DarkseidDamage::isValidVictim)) {
			if (!hitOnce.add(e.getId())) {
				continue;
			}
			if (e.hurt(server.damageSources().mobAttack(boss), dmg)) {
				DarkseidDamage.knockAway(e, boss.position().subtract(lockedDir.scale(2)), 2.4 * knockbackMultiplier(), 0.6);
				server.playSound(null, e.blockPosition(), DarkseidSounds.PUNCH, SoundSource.HOSTILE, 2.0f, 0.6f);
				server.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY() + 1, e.getZ(), 1, 0, 0, 0, 0);
			}
		}
		boolean wall = boss.horizontalCollision && chargeTravelled > 2.0;
		if (chargeTravelled >= cfg.chargeDistance || wall || t > windup + 45) {
			boss.setDeltaMovement(Vec3.ZERO.add(0, boss.getDeltaMovement().y, 0));
			if (wall) {
				server.playSound(null, boss.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 0.7f);
				DarkseidFx.shake(server, boss.position(), 24.0, 0.8f, 10);
				groundBurst(server, boss.position().add(lockedDir.scale(1.5)), 3.0);
			}
			end(server);
			if (wall) {
				// he ran into something hard -- a moment to punish him
				boss.triggerAnim("action", DarkseidAnims.HURT);
				flinch(16);
			}
		}
	}

	// ---------------------------------------------------------------- Boom Tube Reinforcements

	private void runReinforcements(ServerLevel server) {
		if (t < DarkseidAnims.SUMMON_OPEN && t % 3 == 0) {
			server.sendParticles(ParticleTypes.END_ROD, boss.getX(), boss.getY() + boss.getBbHeight(), boss.getZ(), 4,
					0.5, 0.3, 0.5, 0.05);
		}
		if (t == DarkseidAnims.SUMMON_OPEN) {
			DarkseidRaid raid = boss.raid();
			int tubes = phase() >= 2 ? 2 : 1;
			if (enrage() > 0) {
				tubes++;
			}
			if (raid != null) {
				raid.requestReinforcements(server, tubes);
			}
			server.playSound(null, boss.blockPosition(), DarkseidSounds.BOOM_TUBE, SoundSource.HOSTILE, 3.0f, 0.7f);
		}
		if (t >= DarkseidAnims.SUMMON_TICKS) {
			end(server);
		}
	}

	// ---------------------------------------------------------------- Omega Beam Sweep

	private static final int SWEEP_WINDUP = 30;

	private void runSweep(ServerLevel server) {
		DarkseidConfig.Abilities cfg = DarkseidConfig.abilities();
		if (t < SWEEP_WINDUP) {
			setYaw(sweepYaw);
			if (t % 3 == 0) {
				for (int b = 0; b < sweepBeams; b++) {
					Vec3 dir = yawDir(sweepYaw + b * 180.0f);
					Vec3 start = boss.position().add(0, 0.2, 0).add(dir.scale(boss.getBbWidth()));
					DarkseidFx.line(server, WARN, start, start.add(dir.scale(Math.min(24.0, cfg.omegaSweepLength))), 1.5, 16);
				}
				Vec3 eyes = boss.eyePosition();
				server.sendParticles(RED_BIG, eyes.x, eyes.y, eyes.z, 3, 0.2, 0.1, 0.2, 0.0);
			}
			return;
		}
		if (t == SWEEP_WINDUP) {
			boss.triggerAnim("action", DarkseidAnims.SWEEP);
			server.playSound(null, boss.blockPosition(), DarkseidSounds.OMEGA_FIRE, SoundSource.HOSTILE, 4.0f, 0.6f);
		}
		boss.getNavigation().stop();
		boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
		sweepYaw += (float) cfg.omegaSweepRotationSpeed;
		setYaw(sweepYaw);
		boss.setSweep(Mth.wrapDegrees(sweepYaw), sweepBeams);
		if (sweepTicks % 10 == 0) {
			server.playSound(null, boss.blockPosition(), DarkseidSounds.OMEGA_CHARGE, SoundSource.HOSTILE, 1.5f, 1.4f);
		}
		double beamY = boss.getY() + 0.6;
		float dmg = DarkseidConfig.tier(1); // v0.14.26
		for (int b = 0; b < sweepBeams; b++) {
			Vec3 dir = yawDir(sweepYaw + b * 180.0f);
			Vec3 origin = new Vec3(boss.getX(), beamY, boss.getZ()).add(dir.scale(boss.getBbWidth() * 0.6));
			// scorch where the beam's end touches -- also shows its reach
			Vec3 tip = origin.add(dir.scale(cfg.omegaSweepLength));
			var clip = server.clip(new ClipContext(origin, tip, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss));
			Vec3 end = clip.getType() == HitResult.Type.MISS ? tip : clip.getLocation();
			if (sweepTicks % 2 == 0) {
				server.sendParticles(ParticleTypes.LAVA, end.x, end.y, end.z, 1, 0.1, 0.1, 0.1, 0.0);
			}
			double reach = origin.distanceTo(end);
			for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, boss.getBoundingBox().inflate(reach + 1, 3, reach + 1),
					DarkseidDamage::isValidVictim)) {
				double ex = e.getX() - origin.x;
				double ez = e.getZ() - origin.z;
				double along = ex * dir.x + ez * dir.z;
				if (along < 0 || along > reach) {
					continue;
				}
				double lateral = Math.abs(ex * dir.z - ez * dir.x);
				if (lateral > 0.8 + e.getBbWidth() * 0.5) {
					continue;
				}
				// vertical overlap with the beam band [beamY-0.45, beamY+0.45] -- jump or fly over it
				if (e.getY() > beamY + 0.45 || e.getY() + e.getBbHeight() < beamY - 0.45) {
					continue;
				}
				int lastHit = sweepHitAt.getOrDefault(e.getId(), -100);
				if (sweepTicks - lastHit < 10) {
					continue;
				}
				sweepHitAt.put(e.getId(), sweepTicks);
				if (e.hurt(DarkseidDamage.omega(server, boss, boss), dmg)) {
					Vec3 side = new Vec3(-dir.z, 0, dir.x);
					DarkseidDamage.knockAway(e, e.position().subtract(side), 0.9, 0.35);
					server.sendParticles(ParticleTypes.FLASH, e.getX(), beamY, e.getZ(), 1, 0, 0, 0, 0);
				}
			}
		}
		sweepTicks++;
		if (sweepTicks >= sweepTotal) {
			end(server);
		}
	}

	private void setYaw(float yaw) {
		boss.setYRot(yaw);
		boss.yBodyRot = yaw;
		boss.yHeadRot = yaw;
	}

	/** Unit horizontal vector for a Minecraft yaw (0 = +Z / south). */
	public static Vec3 yawDir(float yaw) {
		float r = yaw * ((float) Math.PI / 180.0f);
		return new Vec3(-Math.sin(r), 0.0, Math.cos(r));
	}

	// ---------------------------------------------------------------- Omega Annihilation

	private void beginAnnihilation(ServerLevel server, long now) {
		DarkseidConfig.Abilities cfg = DarkseidConfig.abilities();
		List<? extends LivingEntity> pool = candidates(server);
		annihilationTarget = pool.isEmpty() ? target : pool.get(boss.getRandom().nextInt(pool.size()));
		annihilationDamage = 0;
		annihilationThreshold = (float) (boss.getMaxHealth() * cfg.omegaAnnihilationInterruptFraction);
		nextAnnihilationAt = now + cfg.omegaAnnihilationInterval;
		boss.triggerAnim("action", DarkseidAnims.ANNIHILATION_CHARGE);
		boss.setOmegaState(2);
		mark(annihilationTarget, cfg.omegaAnnihilationChargeTime + 30);
		DarkseidRaid raid = boss.raid();
		Component title = Component.translatable("title.projecthero.darkseid.annihilation").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
		Component sub = Component.translatable("title.projecthero.darkseid.annihilation.sub",
				annihilationTarget == null ? "?" : annihilationTarget.getDisplayName().getString()).withStyle(ChatFormatting.GOLD);
		if (raid != null) {
			raid.announce(server, title, sub);
		}
		tell(annihilationTarget, "message.projecthero.darkseid.annihilation_marked", ChatFormatting.DARK_RED);
		server.playSound(null, boss.blockPosition(), DarkseidSounds.ANNIHILATION, SoundSource.HOSTILE, 5.0f, 0.5f);
	}

	private void runAnnihilation(ServerLevel server, long now) {
		DarkseidConfig.Abilities cfg = DarkseidConfig.abilities();
		int charge = cfg.omegaAnnihilationChargeTime;
		boss.getNavigation().stop();
		boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
		if (t < charge) {
			float k = t / (float) charge;
			if (annihilationTarget != null) {
				face(annihilationTarget.position());
				if (t % 3 == 0) {
					DarkseidFx.column(server, RED_BIG, annihilationTarget.position(), 6.0, 10);
					DarkseidFx.ring(server, RED, annihilationTarget.position().add(0, 0.2, 0), 1.6, 12);
				}
			}
			if (t % 2 == 0) {
				// energy drawn in from all around him, faster and tighter as it builds
				double r = 9.0 * (1.0 - k) + 1.5;
				DarkseidFx.ring(server, RED, boss.position().add(0, boss.getBbHeight() * (0.3 + 0.4 * k), 0), r, 14);
				server.sendParticles(ParticleTypes.ELECTRIC_SPARK, boss.getX(), boss.getY() + boss.getBbHeight() * 0.6, boss.getZ(),
						2 + (int) (k * 6), boss.getBbWidth() * 0.6, boss.getBbHeight() * 0.3, boss.getBbWidth() * 0.6, 0.2);
			}
			if (t % 20 == 0) {
				server.playSound(null, boss.blockPosition(), DarkseidSounds.OMEGA_CHARGE, SoundSource.HOSTILE, 4.0f, 0.5f + k);
				DarkseidFx.shake(server, boss.position(), 96.0, 0.3f + k * 0.8f, 20);
			}
			return;
		}
		if (t == charge) {
			boss.triggerAnim("action", DarkseidAnims.ANNIHILATION_RELEASE);
		}
		if (t == charge + DarkseidAnims.ANNIHILATION_BLAST) {
			annihilate(server);
		}
		if (t >= charge + DarkseidAnims.ANNIHILATION_RELEASE_TICKS) {
			annihilationTarget = null;
			end(server);
		}
	}

	/** The failed-interrupt payoff: an arena-wide Omega detonation, worst for the marked target. */
	private void annihilate(ServerLevel server) {
		DarkseidConfig.Abilities cfg = DarkseidConfig.abilities();
		unmarkAll();
		Vec3 eyes = boss.eyePosition();
		Vec3 c = boss.position();
		double radius = cfg.omegaAnnihilationRadius;
		float base = DarkseidConfig.tier(3); // v0.14.26
		if (annihilationTarget != null && annihilationTarget.isAlive()) {
			DarkseidFx.line(server, RED_BIG, eyes, annihilationTarget.position().add(0, 1, 0), 0.6, 80);
		}
		for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, boss.getBoundingBox().inflate(radius, radius * 0.5, radius),
				DarkseidDamage::isValidVictim)) {
			double d = e.distanceTo(boss);
			if (d > radius) {
				continue;
			}
			boolean isTarget = e == annihilationTarget;
			float dmg = isTarget ? base : (float) (base * 0.6 * (1.0 - 0.5 * d / radius));
			// cover halves it -- the reward for having hidden behind something when it went off
			if (!lineOfSight(server, eyes, e.getEyePosition())) {
				dmg *= 0.5f;
			}
			e.invulnerableTime = 0;
			if (e.hurt(DarkseidDamage.omega(server, boss, boss), dmg)) {
				DarkseidDamage.knockAway(e, c, 1.8 * knockbackMultiplier(), 0.8);
			}
		}
		server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 3, 2.0, 1.0, 2.0, 0.0);
		server.sendParticles(ParticleTypes.FLASH, c.x, c.y + 3, c.z, 3, 1.0, 1.0, 1.0, 0.0);
		for (double r = 6; r <= radius; r += 10) {
			DarkseidFx.ring(server, RED_BIG, c.add(0, 0.5, 0), r, (int) (r * 2));
		}
		server.playSound(null, boss.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 5.0f, 0.4f);
		server.playSound(null, boss.blockPosition(), DarkseidSounds.OMEGA_FIRE, SoundSource.HOSTILE, 5.0f, 0.4f);
		DarkseidFx.shake(server, c, 128.0, 2.5f, 40);
		DarkseidFx.zoom(server, c, 96.0, 0.15f, 20);
	}

	private void interruptAnnihilation(ServerLevel server) {
		DarkseidConfig.Abilities cfg = DarkseidConfig.abilities();
		unmarkAll();
		annihilationTarget = null;
		current = null;
		t = 0;
		boss.setOmegaState(0);
		staggerTicks = cfg.omegaAnnihilationStaggerTicks;
		boss.triggerAnim("action", DarkseidAnims.STAGGER);
		boss.getNavigation().stop();
		server.playSound(null, boss.blockPosition(), SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 4.0f, 0.4f);
		server.playSound(null, boss.blockPosition(), DarkseidSounds.HURT, SoundSource.HOSTILE, 4.0f, 0.4f);
		server.sendParticles(ParticleTypes.EXPLOSION, boss.getX(), boss.getY() + boss.getBbHeight() * 0.6, boss.getZ(), 4,
				boss.getBbWidth() * 0.4, boss.getBbHeight() * 0.3, boss.getBbWidth() * 0.4, 0.0);
		DarkseidFx.shake(server, boss.position(), 64.0, 1.0f, 14);
		DarkseidRaid raid = boss.raid();
		if (raid != null) {
			raid.announce(server, Component.translatable("title.projecthero.darkseid.staggered").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
					Component.translatable("title.projecthero.darkseid.staggered.sub").withStyle(ChatFormatting.YELLOW));
		}
	}

	// ---------------------------------------------------------------- stagger

	private void tickStagger(ServerLevel server) {
		staggerTicks--;
		boss.getNavigation().stop();
		boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
		if (boss.tickCount % 5 == 0) {
			server.sendParticles(ParticleTypes.CRIT, boss.getX(), boss.getY() + boss.getBbHeight() * 0.9, boss.getZ(), 4,
					boss.getBbWidth() * 0.3, 0.2, boss.getBbWidth() * 0.3, 0.1);
			server.sendParticles(RED, boss.getX(), boss.getY() + boss.getBbHeight() * 0.5, boss.getZ(), 3,
					boss.getBbWidth() * 0.4, boss.getBbHeight() * 0.3, boss.getBbWidth() * 0.4, 0.0);
		}
		if (staggerTicks <= 0) {
			staggerTicks = 0;
			boss.triggerAnim("action", DarkseidAnims.STAGGER_RECOVER);
			flinchTicks = DarkseidAnims.STAGGER_RECOVER_TICKS;
			globalCooldown = 20;
		}
	}

	/** Force a stagger (tests / commands). */
	public void forceStagger(int ticks) {
		current = null;
		staggerTicks = ticks;
		boss.triggerAnim("action", DarkseidAnims.STAGGER);
	}

	// ---------------------------------------------------------------- marks / messages

	private void mark(LivingEntity e, int ticks) {
		if (e == null) {
			return;
		}
		// ambient + no icon: our own mark, removed again by unmarkAll -- never a real Glowing potion someone drank
		e.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, true, false, false));
		marked.add(e);
	}

	private void unmarkAll() {
		for (LivingEntity e : marked) {
			MobEffectInstance inst = e.getEffect(MobEffects.GLOWING);
			if (inst != null && inst.isAmbient() && !inst.isVisible()) {
				e.removeEffect(MobEffects.GLOWING);
			}
		}
		marked.clear();
	}

	private static void tell(LivingEntity e, String key, ChatFormatting color) {
		if (e instanceof ServerPlayer player) {
			player.displayClientMessage(Component.translatable(key).withStyle(color, ChatFormatting.BOLD), true);
		}
	}

	// ---------------------------------------------------------------- debug

	/** Start {@code attack} right now against {@code victim}, whoever it is (tests: mock players count as creative). */
	public boolean forceAttack(ServerLevel server, Attack attack, LivingEntity victim) {
		if (current != null || staggerTicks > 0 || victim == null) {
			return false;
		}
		target = victim;
		locked = victim;
		targetChosenAt = server.getGameTime();
		begin(server, attack, server.getGameTime());
		if (attack == Attack.OMEGA_ANNIHILATION && annihilationTarget == null) {
			annihilationTarget = victim;
		}
		return true;
	}

	/** Start {@code attack} right now regardless of cooldowns (tests / the debug command). */
	public boolean forceAttack(ServerLevel server, Attack attack) {
		if (current != null || staggerTicks > 0) {
			return false;
		}
		refreshTarget(server, server.getGameTime());
		if (target == null && attack != Attack.REINFORCEMENTS) {
			return false;
		}
		if (attack == Attack.GRIP) {
			locked = target;
		}
		begin(server, attack, server.getGameTime());
		return true;
	}
}
