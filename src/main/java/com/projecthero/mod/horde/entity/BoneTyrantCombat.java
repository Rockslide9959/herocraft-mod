package com.projecthero.mod.horde.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.joml.Vector3f;

import com.projecthero.mod.hero.power.TempBlocks;
import com.projecthero.mod.horde.entity.skeleton.HordeSkeleton;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.16: every attack of the rebuilt {@link BoneTyrant}, as one tick-driven state machine. One attack runs at a time
 * ({@link #current}); each has a wind-up the player can read -- its own animation clip, a sound, and particles drawn where
 * it will land -- before anything hurts. Lingering effects (spike lines and rings, arrow storms, bone cages) run as
 * {@link Hazard}s alongside whatever he does next.
 * <ul>
 *   <li><b>Phase 1</b> (above 66%): Greatsword Sweep, Bone Cleave (a line of bone spikes runs out from the blade), Bone
 *       Quake (rings of spikes -- jump them), Bone Volley, Raise the Dead, Grave Step (sinks into the ground and rises
 *       beside a far-off target -- also how he gets unstuck).</li>
 *   <li><b>Phase 2</b> (roars at 66%): adds Arrow Storm (on up to four fighters at once), Bone Cage (traps a fighter,
 *       then crushes them unless they break out) and the Tyrant's Charge; cleaves run three spikes wide and quakes ring
 *       twice.</li>
 *   <li><b>Phase 3</b> (roars at 33%, enraged): adds the Soul Beam (a slow-tracking beam of soul fire from his core),
 *       a Wither aura, poisoned volleys, faster recovery and cooldowns, 15% more damage.</li>
 * </ul>
 * No hit from him takes more than 85% of a player's health before armour (like the Titan, he must never one-shot).
 */
public final class BoneTyrantCombat {
	/** Clip lengths and contact ticks -- these must match the generated animation file (scratchpad/gen_skeleton_horde_v01416.js). */
	public static final int SWEEP_CONTACT = 16;
	public static final int CLEAVE_LOCK = 18;
	public static final int CLEAVE_CONTACT = 24;
	public static final int QUAKE_CONTACT = 18;
	public static final int VOLLEY_RELEASE = 14;
	public static final int STORM_RELEASE = 12;
	public static final int SUMMON_CONTACT = 26;
	public static final int CAGE_CONTACT = 12;
	public static final int CHARGE_WINDUP = 20;
	public static final int CHARGE_RUN_MAX = 30;
	public static final int CHARGE_END = 16;
	public static final int BEAM_START = 24;
	public static final int BEAM_END = 60;
	public static final int STEP_AT = 14;
	public static final int ROAR_CONTACT = 20;

	public static final double SWEEP_RANGE = 7.5;
	public static final float SWEEP_DAMAGE = 24.0f;
	public static final int CLEAVE_LENGTH = 18;
	public static final float CLEAVE_DAMAGE = 22.0f;
	public static final float QUAKE_DAMAGE = 16.0f;
	public static final float CHARGE_DAMAGE = 36.0f;
	public static final double CHARGE_SPEED = 0.85;
	public static final float BEAM_DAMAGE = 5.0f;
	public static final double BEAM_LENGTH = 32.0;
	public static final float CAGE_CRUSH_DAMAGE = 14.0f;
	public static final int CAGE_CRUSH_DELAY = 60;
	public static final int CAGE_TICKS = 100;
	public static final double PLAYER_DAMAGE_CAP = 0.85;

	public enum Attack {
		SWEEP("sweep", 34, 1, 0.0, 8.0, 50, 10, true),
		CLEAVE("cleave", 40, 1, 0.0, 20.0, 110, 7, true),
		QUAKE("quake", 34, 1, 0.0, 6.5, 120, 8, true),
		VOLLEY("volley", 26, 1, 6.0, 40.0, 90, 7, false),
		SUMMON("summon", 40, 1, 0.0, 48.0, 500, 4, false),
		GRAVE_STEP("grave_step", 30, 1, 20.0, 96.0, 160, 9, false),
		STORM("storm", 30, 2, 0.0, 40.0, 240, 6, false),
		CAGE("cage", 24, 2, 3.0, 28.0, 300, 5, false),
		CHARGE("charge_windup", CHARGE_WINDUP + CHARGE_RUN_MAX + CHARGE_END, 2, 8.0, 30.0, 180, 7, false),
		SOUL_BEAM("soul_beam", 70, 3, 4.0, 32.0, 260, 8, false),
		/** Only ever forced, on a phase change. */
		ROAR("roar", 50, 99, 0.0, 0.0, 0, 0, false);

		public final String clip;
		public final int length;
		public final int minPhase;
		public final double minRange;
		public final double maxRange;
		public final int cooldown;
		final int weight;
		final boolean melee;

		Attack(String clip, int length, int minPhase, double minRange, double maxRange, int cooldown, int weight, boolean melee) {
			this.clip = clip;
			this.length = length;
			this.minPhase = minPhase;
			this.minRange = minRange;
			this.maxRange = maxRange;
			this.cooldown = cooldown;
			this.weight = weight;
			this.melee = melee;
		}
	}

	private static final DustParticleOptions SOUL_DUST = new DustParticleOptions(new Vector3f(0.4f, 0.9f, 1.0f), 1.2f);
	private static final DustParticleOptions BONE_DUST = new DustParticleOptions(new Vector3f(0.92f, 0.88f, 0.76f), 1.4f);
	private static final BlockState BONE = Blocks.BONE_BLOCK.defaultBlockState();

	private final BoneTyrant boss;
	private Attack current;
	private Attack last;
	private int t;
	private LivingEntity victim;
	private Vec3 lockedDir = new Vec3(0, 0, 1);
	private Vec3 beamDir;
	private Vec3 stepTo;
	private Vec3 chargeStart;
	private final Set<UUID> hitOnce = new HashSet<>();
	private final int[] cooldowns = new int[Attack.values().length];
	private int globalCooldown = 40;
	private int phase = 1;
	private int stuckTicks;
	private Vec3 stuckCheck;
	private final List<Hazard> hazards = new ArrayList<>();
	/** Every attack started since spawning (the tests read it). */
	private final Set<Attack> used = EnumSet.noneOf(Attack.class);

	BoneTyrantCombat(BoneTyrant boss) {
		this.boss = boss;
	}

	public Attack current() {
		return current;
	}

	public int phase() {
		return phase;
	}

	public Set<Attack> used() {
		return used;
	}

	public boolean isAttacking() {
		return current != null;
	}

	public boolean isRoaring() {
		return current == Attack.ROAR;
	}

	public int hazardCount() {
		return hazards.size();
	}

	void setPhaseSilently(int p) {
		phase = Mth.clamp(p, 1, 3);
	}

	/** Damage scale for the current phase (enraged = +15%). */
	float scale() {
		return phase >= 3 ? 1.15f : 1.0f;
	}

	// ---------------------------------------------------------------- the loop

	public void tick(ServerLevel level) {
		tickHazards(level);
		for (int i = 0; i < cooldowns.length; i++) {
			if (cooldowns[i] > 0) {
				cooldowns[i]--;
			}
		}
		if (boss.isDeadOrDying()) {
			return;
		}
		checkPhase(level);
		if (phase >= 3 && boss.tickCount % 40 == 0) {
			witherAura(level);
		}
		if (current != null) {
			t++;
			run(level);
			if (current != null && t >= current.length) {
				finish();
			}
			return;
		}
		if (globalCooldown > 0) {
			globalCooldown--;
		}
		LivingEntity target = boss.getTarget();
		if (target == null || !isFoe(target)) {
			if (target != null) {
				boss.setTarget(null); // a creative player or an ally: let his target goals find someone real
			}
			return;
		}
		double dist = hdist(target.position());
		approach(target, dist);
		trackStuck(dist);
		if (globalCooldown > 0) {
			return;
		}
		Attack next = stuckTicks >= 80 && cooldowns[Attack.GRAVE_STEP.ordinal()] <= 0 ? Attack.GRAVE_STEP : choose(level, target, dist);
		if (next != null) {
			start(level, next, target);
		}
	}

	private void approach(LivingEntity target, double dist) {
		if (dist > 5.0) {
			if (boss.tickCount % 10 == 0 || boss.getNavigation().isDone()) {
				boss.getNavigation().moveTo(target, 1.0);
			}
		} else {
			boss.getNavigation().stop();
			face(target.position(), 10f);
		}
	}

	private void trackStuck(double dist) {
		if (boss.tickCount % 40 != 0) {
			return;
		}
		Vec3 now = boss.position();
		if (stuckCheck != null && dist > 10.0 && now.distanceTo(stuckCheck) < 1.0) {
			stuckTicks += 40;
		} else {
			stuckTicks = 0;
		}
		stuckCheck = now;
	}

	/** A weighted pick among the attacks this phase allows, off cooldown and in range of {@code target}. */
	Attack choose(ServerLevel level, LivingEntity target, double dist) {
		List<Attack> pool = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		int total = 0;
		boolean sight = boss.hasLineOfSight(target);
		for (Attack a : Attack.values()) {
			if (a == Attack.ROAR || phase < a.minPhase || cooldowns[a.ordinal()] > 0 || dist < a.minRange || dist > a.maxRange) {
				continue;
			}
			if ((a == Attack.VOLLEY || a == Attack.SOUL_BEAM || a == Attack.CHARGE) && !sight) {
				continue;
			}
			if (a == Attack.SUMMON && boss.liveMinions(level) >= BoneTyrant.MAX_MINIONS) {
				continue;
			}
			int w = a.weight;
			if (a.melee && dist < 7.0) {
				w *= 2;
			}
			if (a == last) {
				w = Math.max(1, w / 3);
			}
			pool.add(a);
			weights.add(w);
			total += w;
		}
		if (total <= 0) {
			return null;
		}
		int roll = boss.getRandom().nextInt(total);
		for (int i = 0; i < pool.size(); i++) {
			roll -= weights.get(i);
			if (roll < 0) {
				return pool.get(i);
			}
		}
		return pool.get(pool.size() - 1);
	}

	/** Starts {@code a} against {@code target} now (the tests force attacks through this). */
	public void start(ServerLevel level, Attack a, LivingEntity target) {
		current = a;
		last = a;
		t = 0;
		victim = target;
		hitOnce.clear();
		used.add(a);
		stuckTicks = 0;
		stepTo = null;
		beamDir = null;
		float cd = phase >= 3 ? 0.7f : phase == 2 ? 0.85f : 1.0f;
		cooldowns[a.ordinal()] = (int) (a.cooldown * cd);
		boss.getNavigation().stop();
		boss.getMoveControl().setWantedPosition(boss.getX(), boss.getY(), boss.getZ(), 0.0);
		if (target != null) {
			lockedDir = flatDir(target.position());
		}
		boss.playClip(a.clip);
	}

	private void finish() {
		current = null;
		victim = null;
		globalCooldown = phase == 1 ? 24 : phase == 2 ? 16 : 10;
		boss.syncBusy();
	}

	/** Drops whatever he was doing (death, a phase roar). Lingering hazards keep going unless {@code hazardsToo}. */
	public void cancel(boolean hazardsToo) {
		current = null;
		victim = null;
		if (hazardsToo) {
			hazards.clear();
		}
		boss.syncBusy();
	}

	private void checkPhase(ServerLevel level) {
		float frac = boss.getHealth() / boss.getMaxHealth();
		int want = frac > 0.66f ? 1 : frac > 0.33f ? 2 : 3;
		if (want > phase && current != Attack.ROAR) {
			phase = want;
			boss.onPhaseChanged(level, phase);
			start(level, Attack.ROAR, boss.getTarget());
		}
	}

	// ---------------------------------------------------------------- one tick of the running attack

	private void run(ServerLevel level) {
		LivingEntity v = victim != null && victim.isAlive() ? victim : null;
		switch (current) {
			case SWEEP -> sweep(level, v);
			case CLEAVE -> cleave(level, v);
			case QUAKE -> quake(level, v);
			case VOLLEY -> volley(level, v);
			case SUMMON -> summon(level);
			case GRAVE_STEP -> graveStep(level, v);
			case STORM -> storm(level, v);
			case CAGE -> cage(level, v);
			case CHARGE -> charge(level, v);
			case SOUL_BEAM -> soulBeam(level, v);
			case ROAR -> roar(level);
		}
	}

	private void sweep(ServerLevel level, LivingEntity v) {
		if (t < SWEEP_CONTACT && v != null) {
			face(v.position(), 10f);
		}
		if (t == 2) {
			sound(level, SoundEvents.WITHER_SKELETON_AMBIENT, 2.0f, 0.5f);
		}
		Vec3 fwd = facing();
		if (t >= 4 && t < SWEEP_CONTACT && t % 3 == 0) {
			// the arc it is about to cut, drawn on the ground
			for (int i = -6; i <= 6; i++) {
				Vec3 d = rotateY(fwd, i * 0.3);
				Vec3 p = boss.position().add(d.scale(SWEEP_RANGE * 0.8));
				level.sendParticles(BONE_DUST, p.x, p.y + 0.2, p.z, 1, 0.1, 0, 0.1, 0);
			}
		}
		if (t == SWEEP_CONTACT) {
			sound(level, SoundEvents.PLAYER_ATTACK_SWEEP, 2.5f, 0.5f);
			for (int i = -5; i <= 5; i++) {
				Vec3 p = boss.position().add(rotateY(fwd, i * 0.35).scale(4.5)).add(0, 2.0, 0);
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
			}
			for (LivingEntity e : foes(level, boss.position(), SWEEP_RANGE)) {
				Vec3 to = flat(e.position().subtract(boss.position()));
				if (to.lengthSqr() > 1.0e-4 && to.normalize().dot(fwd) < -0.25) {
					continue; // behind him
				}
				if (Math.abs(e.getY() - boss.getY()) > 6.0) {
					continue;
				}
				strike(e, SWEEP_DAMAGE, 1.5, 0.45);
			}
		}
	}

	private void cleave(ServerLevel level, LivingEntity v) {
		if (t < CLEAVE_LOCK && v != null) {
			face(v.position(), 8f);
			lockedDir = flatDir(v.position());
		}
		if (t == 2) {
			sound(level, SoundEvents.SKELETON_HORSE_DEATH, 2.0f, 0.5f);
		}
		if (t >= CLEAVE_LOCK && t < CLEAVE_CONTACT && t % 2 == 0) {
			int lanes = phase >= 2 ? 1 : 0;
			Vec3 perp = new Vec3(-lockedDir.z, 0, lockedDir.x);
			for (int i = 4; i < 4 + CLEAVE_LENGTH; i += 2) {
				for (int lane = -lanes; lane <= lanes; lane++) {
					Vec3 p = boss.position().add(lockedDir.scale(i)).add(perp.scale(lane * 1.6));
					level.sendParticles(BONE_DUST, p.x, p.y + 0.15, p.z, 1, 0.15, 0, 0.15, 0);
				}
			}
		}
		if (t == CLEAVE_CONTACT) {
			Vec3 impact = boss.position().add(lockedDir.scale(4.5));
			sound(level, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 0.6f);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BONE), impact.x, impact.y + 0.3, impact.z, 50, 1.0, 0.3, 1.0, 0.2);
			for (LivingEntity e : foes(level, impact, 2.5)) {
				strike(e, CLEAVE_DAMAGE, 0.6, 0.6);
			}
			hazards.add(new SpikeLine(boss.position().add(lockedDir.scale(5.0)), lockedDir, CLEAVE_LENGTH, phase >= 2 ? 1 : 0,
					CLEAVE_DAMAGE));
		}
	}

	private void quake(ServerLevel level, LivingEntity v) {
		if (t < QUAKE_CONTACT && v != null) {
			face(v.position(), 6f);
		}
		if (t == 1) {
			sound(level, SoundEvents.RAVAGER_ROAR, 1.4f, 0.4f);
		}
		if (t < QUAKE_CONTACT && t % 3 == 0) {
			HordeSkeleton.ring(level, ParticleTypes.WHITE_ASH, boss.position(), 2.0 + t * 0.6, 24);
		}
		if (t == QUAKE_CONTACT) {
			sound(level, SoundEvents.GENERIC_EXPLODE.value(), 1.8f, 0.5f);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BONE), boss.getX(), boss.getY() + 0.2, boss.getZ(), 80, 2.5, 0.2, 2.5, 0.2);
			for (LivingEntity e : foes(level, boss.position(), 3.0)) {
				strike(e, QUAKE_DAMAGE + 2, 1.2, 0.8);
			}
			double reach = phase >= 3 ? 16.0 : 13.0;
			hazards.add(new SpikeRing(boss.position(), 3.0, reach, QUAKE_DAMAGE, 0));
			if (phase >= 2) {
				hazards.add(new SpikeRing(boss.position(), 3.0, reach, QUAKE_DAMAGE, 9));
			}
		}
	}

	private void volley(ServerLevel level, LivingEntity v) {
		if (t < VOLLEY_RELEASE && v != null) {
			face(v.position(), 10f);
		}
		Vec3 hand = boss.position().add(0, boss.getBbHeight() * 0.55, 0).add(rotateY(facing(), -1.2).scale(1.4));
		if (t == 3) {
			sound(level, SoundEvents.SKELETON_HORSE_AMBIENT, 2.0f, 0.4f);
		}
		if (t < VOLLEY_RELEASE) {
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, hand.x, hand.y, hand.z, 2, 0.2, 0.2, 0.2, 0.01);
		}
		if (t == VOLLEY_RELEASE && v != null) {
			int n = phase >= 3 ? 13 : phase == 2 ? 11 : 9;
			Vec3 aim = v.getEyePosition().subtract(hand);
			double horizontal = Math.sqrt(aim.x * aim.x + aim.z * aim.z);
			double baseYaw = Math.atan2(aim.z, aim.x);
			for (int i = 0; i < n; i++) {
				double yaw = baseYaw + (i - (n - 1) / 2.0) * 0.1;
				Arrow arrow = arrow(level, hand);
				if (phase >= 3) {
					arrow.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
				}
				arrow.shoot(Math.cos(yaw) * horizontal, aim.y + horizontal * 0.12, Math.sin(yaw) * horizontal, 2.3f, 1.0f);
				level.addFreshEntity(arrow);
			}
			sound(level, SoundEvents.SKELETON_SHOOT, 2.0f, 0.5f);
		}
	}

	private void summon(ServerLevel level) {
		if (t == 1) {
			sound(level, SoundEvents.EVOKER_PREPARE_SUMMON, 2.0f, 0.5f);
		}
		if (t < SUMMON_CONTACT && t % 3 == 0) {
			HordeSkeleton.ring(level, ParticleTypes.SOUL, boss.position(), 4.0, 16);
		}
		if (t == SUMMON_CONTACT) {
			sound(level, SoundEvents.EVOKER_CAST_SPELL, 2.0f, 0.4f);
			boss.raiseMinions(level, 3 + phase, phase);
		}
	}

	private void graveStep(ServerLevel level, LivingEntity v) {
		if (t == 1) {
			stepTo = v == null ? null : findStepSpot(level, v);
			if (stepTo == null) {
				current = null; // nowhere to rise: give it up
				boss.syncBusy();
				return;
			}
			sound(level, SoundEvents.SOUL_ESCAPE.value(), 2.0f, 0.5f);
		}
		if (stepTo == null) {
			return;
		}
		if (t < STEP_AT && t % 2 == 0) {
			HordeSkeleton.ring(level, ParticleTypes.SOUL, stepTo, 2.0, 14);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, boss.getX(), boss.getY() + 0.3, boss.getZ(), 4, 1.0, 0.2, 1.0, 0.01);
		}
		if (t == STEP_AT) {
			level.sendParticles(ParticleTypes.SOUL, boss.getX(), boss.getY() + 1, boss.getZ(), 30, 1.0, 1.0, 1.0, 0.05);
			boss.teleportTo(stepTo.x, stepTo.y, stepTo.z);
			boss.getNavigation().stop();
			if (v != null) {
				face(v.position(), 180f);
			}
			sound(level, SoundEvents.SOUL_ESCAPE.value(), 2.0f, 0.4f);
		}
		if (t == STEP_AT + 4) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BONE), boss.getX(), boss.getY() + 0.2, boss.getZ(), 60, 2.0, 0.2, 2.0, 0.2);
			for (LivingEntity e : foes(level, boss.position(), 4.0)) {
				strike(e, 12.0f, 1.4, 0.6);
			}
		}
	}

	private void storm(ServerLevel level, LivingEntity v) {
		if (t < STORM_RELEASE && v != null) {
			face(v.position(), 8f);
		}
		if (t == 1) {
			sound(level, SoundEvents.SKELETON_HORSE_DEATH, 2.0f, 0.4f);
		}
		if (t == STORM_RELEASE) {
			List<LivingEntity> marks = new ArrayList<>();
			if (v != null) {
				marks.add(v);
			}
			for (LivingEntity e : foes(level, boss.position(), 40.0)) {
				if (marks.size() >= 4) {
					break;
				}
				if (!marks.contains(e) && e instanceof Player) {
					marks.add(e);
				}
			}
			for (LivingEntity e : marks) {
				hazards.add(new StormZone(e.position(), phase >= 3 ? 4 : 3));
			}
			sound(level, SoundEvents.ARROW_SHOOT, 2.0f, 0.4f);
		}
	}

	private void cage(ServerLevel level, LivingEntity v) {
		if (v == null) {
			return;
		}
		if (t < CAGE_CONTACT) {
			face(v.position(), 10f);
			if (t == 1) {
				sound(level, SoundEvents.EVOKER_PREPARE_ATTACK, 2.0f, 0.5f);
			}
			// the bones gathering round the prey -- a second to step away
			HordeSkeleton.ring(level, ParticleTypes.SOUL, v.position().add(0, t * 0.15, 0), 1.5, 10);
		}
		if (t == CAGE_CONTACT && hdist(v.position()) < 32.0) {
			BlockPos base = v.blockPosition();
			for (int dy = 0; dy <= 3; dy++) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dy < 3 && dx == 0 && dz == 0) {
							continue;
						}
						TempBlocks.place(level, base.offset(dx, dy, dz), BONE, CAGE_TICKS);
					}
				}
			}
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BONE), base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5, 40, 1.0, 1.0, 1.0, 0.1);
			level.playSound(null, base, SoundEvents.BONE_BLOCK_PLACE, SoundSource.HOSTILE, 2.0f, 0.6f);
			hazards.add(new Cage(v, Vec3.atBottomCenterOf(base)));
		}
	}

	private void charge(ServerLevel level, LivingEntity v) {
		int runEnd = CHARGE_WINDUP + CHARGE_RUN_MAX;
		if (t < CHARGE_WINDUP) {
			if (v != null) {
				face(v.position(), 12f);
			}
			if (t == 1) {
				sound(level, SoundEvents.RAVAGER_ROAR, 2.5f, 0.6f);
			}
			level.sendParticles(ParticleTypes.CLOUD, boss.getX(), boss.getY() + 0.1, boss.getZ(), 2, 0.8, 0.0, 0.8, 0.01);
			return;
		}
		if (t == CHARGE_WINDUP) {
			if (v != null) {
				lockedDir = flatDir(v.position());
			}
			chargeStart = boss.position();
			boss.playClip("charge_run");
		}
		if (t < runEnd) {
			face(boss.position().add(lockedDir), 180f);
			boss.setDeltaMovement(lockedDir.x * CHARGE_SPEED, boss.getDeltaMovement().y, lockedDir.z * CHARGE_SPEED);
			boss.hasImpulse = true;
			if (t % 4 == 0) {
				sound(level, SoundEvents.SKELETON_STEP, 2.0f, 0.4f);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BONE), boss.getX(), boss.getY() + 0.1, boss.getZ(), 6, 0.6, 0, 0.6, 0.1);
			}
			for (LivingEntity e : foes(level, boss.position(), boss.getBbWidth() * 0.5 + 1.8)) {
				if (Math.abs(e.getY() - boss.getY()) < 4.0 && hitOnce.add(e.getUUID())) {
					strike(e, CHARGE_DAMAGE, 2.0, 0.7);
				}
			}
			boolean wall = t > CHARGE_WINDUP + 2 && boss.horizontalCollision;
			if (wall || (chargeStart != null && boss.position().distanceTo(chargeStart) > 30.0)) {
				if (wall) {
					sound(level, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.8f);
					level.sendParticles(ParticleTypes.EXPLOSION, boss.getX() + lockedDir.x * 1.5, boss.getY() + 1.5, boss.getZ() + lockedDir.z * 1.5,
							2, 0.5, 0.5, 0.5, 0);
				}
				t = runEnd;
				endCharge();
			}
			return;
		}
		if (t == runEnd) {
			endCharge();
		}
	}

	private void endCharge() {
		boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
		boss.playClip("charge_end");
	}

	private void soulBeam(ServerLevel level, LivingEntity v) {
		Vec3 core = boss.position().add(0, boss.getBbHeight() * 0.62, 0);
		if (t < BEAM_START) {
			if (v != null) {
				face(v.position(), 6f);
			}
			if (t == 1) {
				sound(level, SoundEvents.BEACON_ACTIVATE, 3.0f, 0.5f);
			}
			// the core drawing in souls, and a thin aim line where the beam will go
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, core.x, core.y, core.z, 4, 1.2, 1.2, 1.2, -0.08);
			if (v != null && t % 2 == 0) {
				Vec3 to = v.getEyePosition().subtract(core);
				double len = Math.min(BEAM_LENGTH, to.length());
				Vec3 dir = to.normalize();
				for (double d = 1.5; d < len; d += 1.5) {
					Vec3 p = core.add(dir.scale(d));
					level.sendParticles(SOUL_DUST, p.x, p.y, p.z, 1, 0, 0, 0, 0);
				}
			}
			if (t == BEAM_START - 4 && v != null) {
				beamDir = v.getEyePosition().subtract(core).normalize();
			}
			return;
		}
		if (t >= BEAM_END) {
			return;
		}
		if (beamDir == null) {
			beamDir = facing();
		}
		if (v != null) {
			Vec3 want = v.getEyePosition().subtract(core).normalize();
			beamDir = turnToward(beamDir, want, Math.toRadians(phase >= 3 ? 3.0 : 2.2));
		}
		face(core.add(beamDir), 180f);
		Vec3 end = core.add(beamDir.scale(BEAM_LENGTH));
		var hit = level.clip(new ClipContext(core, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss));
		if (hit.getType() != HitResult.Type.MISS) {
			end = hit.getLocation();
		}
		if (t % 2 == 0) {
			double len = end.distanceTo(core);
			for (double d = 1.0; d < len; d += 0.7) {
				Vec3 p = core.add(beamDir.scale(d));
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0.08, 0.08, 0.08, 0.0);
			}
			level.sendParticles(ParticleTypes.SOUL, end.x, end.y, end.z, 4, 0.3, 0.3, 0.3, 0.02);
		}
		if (t % 10 == 0) {
			sound(level, SoundEvents.BLAZE_SHOOT, 2.0f, 0.4f);
		}
		if (t % 4 == 0) {
			AABB box = new AABB(core, end).inflate(1.5);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, this::isFoe)) {
				Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
				if (segmentDistance(core, end, c) < 1.1 + e.getBbWidth() * 0.5) {
					e.hurt(boss.damageSources().indirectMagic(boss, boss), cap(e, BEAM_DAMAGE * scale()));
					e.igniteForSeconds(3);
				}
			}
		}
	}

	private void roar(ServerLevel level) {
		if (t == 1) {
			sound(level, SoundEvents.RAVAGER_ROAR, 4.0f, 0.4f);
			sound(level, SoundEvents.WITHER_AMBIENT, 3.0f, 0.5f);
		}
		if (t < ROAR_CONTACT && t % 3 == 0) {
			HordeSkeleton.ring(level, ParticleTypes.SOUL_FIRE_FLAME, boss.position(), 14.0 * t / ROAR_CONTACT, 32);
		}
		if (t == ROAR_CONTACT) {
			sound(level, SoundEvents.WITHER_BREAK_BLOCK, 2.5f, 0.5f);
			for (LivingEntity e : foes(level, boss.position(), 14.0)) {
				double d = hdist(e.position());
				strike(e, 10.0f, 2.2 * (1.0 - d / 16.0), 0.6);
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), boss);
			}
			boss.raiseMinions(level, 2 + phase, phase);
		}
	}

	private void witherAura(ServerLevel level) {
		HordeSkeleton.ring(level, ParticleTypes.SOUL, boss.position(), 10.0, 28);
		for (LivingEntity e : foes(level, boss.position(), 10.0)) {
			e.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0), boss);
		}
	}

	// ---------------------------------------------------------------- hazards

	private void tickHazards(ServerLevel level) {
		Iterator<Hazard> it = hazards.iterator();
		while (it.hasNext()) {
			if (!it.next().tick(level)) {
				it.remove();
			}
		}
	}

	private abstract static class Hazard {
		/** False when it is spent. */
		abstract boolean tick(ServerLevel level);
	}

	/** A line of bone spikes running out from a cleave, a block a tick. */
	private final class SpikeLine extends Hazard {
		private final Vec3 origin;
		private final Vec3 dir;
		private final int length;
		private final int lanes;
		private final float damage;
		private final Set<UUID> hit = new HashSet<>();
		private int step;

		SpikeLine(Vec3 origin, Vec3 dir, int length, int lanes, float damage) {
			this.origin = origin;
			this.dir = dir;
			this.length = length;
			this.lanes = lanes;
			this.damage = damage;
		}

		@Override
		boolean tick(ServerLevel level) {
			Vec3 perp = new Vec3(-dir.z, 0, dir.x);
			for (int lane = -lanes; lane <= lanes; lane++) {
				spike(level, origin.add(dir.scale(step)).add(perp.scale(lane * 1.6)), damage, hit, true);
			}
			if (step % 3 == 0) {
				Vec3 p = origin.add(dir.scale(step));
				level.playSound(null, p.x, p.y, p.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, 1.4f, 0.6f);
			}
			return ++step < length;
		}
	}

	/** A ring of spikes racing outward from a quake -- jump it. */
	private final class SpikeRing extends Hazard {
		private final Vec3 center;
		private final double to;
		private final float damage;
		private final Set<UUID> hit = new HashSet<>();
		private double radius;
		private int delay;

		SpikeRing(Vec3 center, double from, double to, float damage, int delay) {
			this.center = center;
			this.radius = from;
			this.to = to;
			this.damage = damage;
			this.delay = delay;
		}

		@Override
		boolean tick(ServerLevel level) {
			if (delay > 0) {
				delay--;
				return true;
			}
			int points = Math.max(8, (int) Math.ceil(2 * Math.PI * radius / 1.4));
			for (int i = 0; i < points; i++) {
				double a = i * Math.PI * 2 / points;
				Vec3 p = center.add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
				spike(level, p, 0, null, i % 2 == 0);
			}
			for (LivingEntity e : foes(level, center, radius + 1.0)) {
				double d = Math.sqrt(Math.pow(e.getX() - center.x, 2) + Math.pow(e.getZ() - center.z, 2));
				if (Math.abs(d - radius) > 0.9 || hit.contains(e.getUUID())) {
					continue;
				}
				// it runs along the ground: anyone in the air when it passes is spared
				BlockPos ground = groundAt(level, e.position().add(0, 0.5, 0));
				if (ground != null && e.getY() - ground.getY() > 0.6) {
					continue;
				}
				hit.add(e.getUUID());
				strike(e, damage, 0.4, 0.8);
			}
			radius += 1.0;
			return radius <= to;
		}
	}

	/** Arrows raining on one spot: a second's warning ring, then two and a half seconds of arrows. */
	private final class StormZone extends Hazard {
		static final double RADIUS = 4.5;
		static final int WARN = 20;
		static final int RAIN = 50;
		private final Vec3 at;
		private final int perTick;
		private int age;

		StormZone(Vec3 at, int perTick) {
			this.at = at;
			this.perTick = perTick;
		}

		@Override
		boolean tick(ServerLevel level) {
			age++;
			if (age <= WARN) {
				if (age % 2 == 0) {
					HordeSkeleton.ring(level, ParticleTypes.CRIT, at, RADIUS, 18);
				}
				return true;
			}
			for (int i = 0; i < perTick; i++) {
				double x = at.x + (boss.getRandom().nextDouble() * 2 - 1) * RADIUS;
				double z = at.z + (boss.getRandom().nextDouble() * 2 - 1) * RADIUS;
				Arrow arrow = arrow(level, new Vec3(x, at.y + 18, z));
				arrow.setDeltaMovement(0, -2.4, 0);
				level.addFreshEntity(arrow);
			}
			return age < WARN + RAIN;
		}
	}

	/** A trapped fighter: crushed when the time runs out unless they have broken (or blinked) out. */
	private final class Cage extends Hazard {
		private final LivingEntity prisoner;
		private final Vec3 center;
		private int age;

		Cage(LivingEntity prisoner, Vec3 center) {
			this.prisoner = prisoner;
			this.center = center;
		}

		@Override
		boolean tick(ServerLevel level) {
			age++;
			if (age % 10 == 0) {
				level.sendParticles(ParticleTypes.SOUL, center.x, center.y + 1.5, center.z, 3, 0.5, 0.8, 0.5, 0.01);
			}
			if (age < CAGE_CRUSH_DELAY) {
				return prisoner.isAlive();
			}
			double dx = prisoner.getX() - center.x;
			double dz = prisoner.getZ() - center.z;
			if (prisoner.isAlive() && dx * dx + dz * dz < 1.44 && Math.abs(prisoner.getY() - center.y) < 2.0) {
				prisoner.hurt(boss.damageSources().mobAttack(boss), cap(prisoner, CAGE_CRUSH_DAMAGE * scale()));
				prisoner.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1), boss);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BONE), center.x, center.y + 1, center.z, 40, 0.6, 1.0, 0.6, 0.1);
				level.playSound(null, center.x, center.y, center.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, 2.0f, 0.5f);
			}
			return false;
		}
	}

	/** One bone spike at {@code p}: dust, maybe a short-lived bone block, and (if {@code hit} is given) a hit. */
	private void spike(ServerLevel level, Vec3 p, float damage, Set<UUID> hit, boolean place) {
		BlockPos ground = groundAt(level, p);
		if (ground == null) {
			return;
		}
		double gy = ground.getY();
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BONE), p.x, gy + 0.4, p.z, 5, 0.25, 0.3, 0.25, 0.1);
		level.sendParticles(ParticleTypes.CRIT, p.x, gy + 0.8, p.z, 1, 0.1, 0.3, 0.1, 0.1);
		AABB box = new AABB(p.x - 0.9, gy - 0.5, p.z - 0.9, p.x + 0.9, gy + 2.5, p.z + 0.9);
		if (hit != null) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, this::isFoe)) {
				if (hit.add(e.getUUID())) {
					strike(e, damage, 0.2, 0.95);
				}
			}
		}
		if (place && level.getEntitiesOfClass(LivingEntity.class, new AABB(ground).inflate(0.3, 1.0, 0.3)).isEmpty()) {
			TempBlocks.place(level, ground, BONE, 24);
		}
	}

	/** The first open block above solid ground near {@code p} (searching 3 up to 6 down), or null. */
	static BlockPos groundAt(ServerLevel level, Vec3 p) {
		BlockPos.MutableBlockPos m = BlockPos.containing(p.x, p.y + 3, p.z).mutable();
		for (int i = 0; i < 10; i++) {
			BlockPos below = m.below();
			BlockState here = level.getBlockState(m);
			if (here.getCollisionShape(level, m).isEmpty() && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
				return m.immutable();
			}
			m.move(0, -1, 0);
		}
		return null;
	}

	private Vec3 findStepSpot(ServerLevel level, LivingEntity v) {
		for (int i = 0; i < 12; i++) {
			double a = boss.getRandom().nextDouble() * Math.PI * 2;
			double r = 4.0 + boss.getRandom().nextDouble() * 3.0;
			BlockPos ground = groundAt(level, v.position().add(Math.cos(a) * r, 0, Math.sin(a) * r));
			if (ground == null) {
				continue;
			}
			// room for a 7-block giant
			boolean clear = true;
			for (int dy = 0; dy < 7 && clear; dy++) {
				clear = level.getBlockState(ground.above(dy)).getCollisionShape(level, ground.above(dy)).isEmpty();
			}
			if (clear) {
				return Vec3.atBottomCenterOf(ground);
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- helpers

	boolean isFoe(LivingEntity e) {
		return e != boss && HordeSkeleton.enemy(e) && BoneTyrant.enemy(e);
	}

	private List<LivingEntity> foes(ServerLevel level, Vec3 center, double radius) {
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius, 8.0, radius), this::isFoe)) {
			double dx = e.getX() - center.x;
			double dz = e.getZ() - center.z;
			double reach = radius + e.getBbWidth() * 0.5;
			if (dx * dx + dz * dz <= reach * reach) {
				out.add(e);
			}
		}
		return out;
	}

	/** Hurts {@code e} (scaled for the phase, capped for players) and throws it away from him. */
	private void strike(LivingEntity e, float damage, double shove, double lift) {
		e.hurt(boss.damageSources().mobAttack(boss), cap(e, damage * scale()));
		Vec3 away = flat(e.position().subtract(boss.position()));
		away = away.lengthSqr() < 1.0e-4 ? facing() : away.normalize();
		e.push(away.x * shove, lift, away.z * shove);
		e.hurtMarked = true;
	}

	static float cap(LivingEntity e, float amount) {
		if (e instanceof Player p) {
			return (float) Math.min(amount, p.getMaxHealth() * PLAYER_DAMAGE_CAP);
		}
		return amount;
	}

	/** v0.14.17: every arrow he fires is gone this long after it was fired -- Arrow Storm used to litter the arena. */
	public static final int ARROW_LIFETIME_TICKS = 5 * 20;

	private Arrow arrow(ServerLevel level, Vec3 at) {
		return bossArrow(level, boss, at);
	}

	/** One of his arrows: 3 damage, never picked up, gone {@link #ARROW_LIFETIME_TICKS} after it is fired. */
	public static Arrow bossArrow(ServerLevel level, LivingEntity owner, Vec3 at) {
		Arrow arrow = new Arrow(level, owner, new ItemStack(Items.ARROW), null) {
			@Override
			public void tick() {
				if (tickCount >= ARROW_LIFETIME_TICKS) {
					discard(); // flying, stuck in the ground or stuck in a wall alike
					return;
				}
				super.tick();
			}

			@Override
			public boolean shouldBeSaved() {
				return false; // never written to the world, so an unloaded chunk cannot keep a stray one
			}
		};
		arrow.setPos(at.x, at.y, at.z);
		arrow.setBaseDamage(3.0);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		return arrow;
	}

	private void sound(ServerLevel level, SoundEvent s, float volume, float pitch) {
		level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), s, SoundSource.HOSTILE, volume, pitch);
	}

	private void face(Vec3 at, float maxTurn) {
		double dx = at.x - boss.getX();
		double dz = at.z - boss.getZ();
		if (dx * dx + dz * dz < 1.0e-4) {
			return;
		}
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
		float next = Mth.approachDegrees(boss.getYRot(), yaw, maxTurn);
		boss.setYRot(next);
		boss.yBodyRot = next;
		boss.yHeadRot = next;
	}

	private Vec3 facing() {
		return Vec3.directionFromRotation(0, boss.getYRot());
	}

	private double hdist(Vec3 p) {
		double dx = p.x - boss.getX();
		double dz = p.z - boss.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private Vec3 flatDir(Vec3 to) {
		Vec3 d = flat(to.subtract(boss.position()));
		return d.lengthSqr() < 1.0e-4 ? facing() : d.normalize();
	}

	private static Vec3 flat(Vec3 v) {
		return new Vec3(v.x, 0, v.z);
	}

	static Vec3 rotateY(Vec3 v, double radians) {
		double c = Math.cos(radians);
		double s = Math.sin(radians);
		return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
	}

	static Vec3 turnToward(Vec3 from, Vec3 to, double maxRadians) {
		double dot = Mth.clamp(from.dot(to), -1.0, 1.0);
		double angle = Math.acos(dot);
		if (angle <= maxRadians || angle < 1.0e-6) {
			return to;
		}
		double f = maxRadians / angle;
		double sin = Math.sin(angle);
		return from.scale(Math.sin((1 - f) * angle) / sin).add(to.scale(Math.sin(f * angle) / sin)).normalize();
	}

	static double segmentDistance(Vec3 a, Vec3 b, Vec3 p) {
		Vec3 ab = b.subtract(a);
		double len2 = ab.lengthSqr();
		if (len2 < 1.0e-8) {
			return p.distanceTo(a);
		}
		double k = Mth.clamp(p.subtract(a).dot(ab) / len2, 0.0, 1.0);
		return p.distanceTo(a.add(ab.scale(k)));
	}
}
