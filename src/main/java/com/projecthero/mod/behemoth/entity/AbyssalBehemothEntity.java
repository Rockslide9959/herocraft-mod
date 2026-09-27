package com.projecthero.mod.behemoth.entity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.behemoth.BehemothCombat;
import com.projecthero.mod.behemoth.BehemothConfig;
import com.projecthero.mod.event.EventBossBar;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * THE ABYSSAL BEHEMOTH -- a very rare, endgame Nether world boss (spec: a "massively enhanced, mutated,
 * ancient Ghast-like creature", never a re-skinned Ghast). Fully custom GeckoLib model/animations
 * ({@code geo/abyssal_behemoth.geo.json}), fully custom flight and combat AI -- it registers no vanilla
 * goals at all (see {@link #registerGoals}), the same "a boss this size cannot afford a vanilla A*
 * search" discipline {@code TitanEntity} already established for this mod.
 *
 * <h2>Movement</h2>
 * {@link #aiStep} decides <em>where it wants to be</em> (a hover point relative to its target, shaped
 * by {@link #phase}/{@link #lowAltitudeTicks}); {@link #travel} is the only place that turns that into
 * real velocity, by direct steering (no navigation, no path). This mirrors the AI/physics split vanilla
 * flying mobs already use, just written out explicitly instead of inherited.
 *
 * <h2>Combat</h2>
 * One tick-driven state machine ({@link #chooseAbility}/{@link #tickAbility}), exactly like
 * {@code TitanEntity}'s own {@code Attack} enum -- windup, active, recovery, one shared cooldown map,
 * one shared global lock so two abilities can never overlap. The actual effect of each ability (damage,
 * particles, projectiles) lives in {@link BehemothCombat}; this class only ever decides which one to run
 * and when.
 */
public class AbyssalBehemothEntity extends Monster implements GeoEntity {
	/** Bounding-box size in blocks -- ~2.25x a vanilla Ghast's 4x4x4. A fixed constant, like {@code TitanEntity.SCALE}:
	 *  an {@link EntityType}'s hit-box cannot change at runtime, so this cannot be a live-reloadable config value. */
	public static final float SIZE = 9.0f;
	/** The reference height the geo model itself was built at (blocks) -- {@code BehemothRenderer} scales
	 *  the whole model by {@code SIZE / MODEL_HEIGHT}, the same trick {@code TitanFormRenderer} uses. */
	public static final float MODEL_HEIGHT = 4.0f;

	public enum Ability { FIREBALL, BARRAGE, MAGMA_RAIN, BEAM, CINDER_TETHER, NETHERSTORM, HELLWIND, SOVEREIGN_DESCENT, SKYFALL }

	private enum Stage { NONE, WINDUP, ACTIVE, RECOVER }

	private record Task(long due, Runnable run) {
	}

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final List<Task> tasks = new ArrayList<>();
	private final Map<Ability, Long> abilityReadyAt = new EnumMap<>(Ability.class);

	private EventBossBar bossBar;
	private int phase = 1;
	private boolean enraged;
	private int combatTicks;

	private Ability activeAbility;
	private Stage stage = Stage.NONE;
	private int stageTicks;
	private Ability lastAbility;
	private long globalLockUntil;

	/** >0 while descended for melee (Cinder Tether pull or Sovereign Descent's Ground Assault window). */
	private int lowAltitudeTicks;
	private int netherstormTicksLeft;
	private int barrageShotsLeft;
	private int magmaImpactsLeft;
	private int skyfallShotsLeft;
	private double beamStartYaw;
	private double beamEndYaw;
	private int airborneTargetTicks;

	public AbyssalBehemothEntity(EntityType<? extends AbyssalBehemothEntity> type, Level level) {
		super(type, level);
		this.xpReward = 500;
	}

	public static AttributeSupplier.Builder createAttributes() {
		var stats = BehemothConfig.stats();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, stats.health)
				.add(Attributes.ATTACK_DAMAGE, stats.meleeDamage)
				.add(Attributes.MOVEMENT_SPEED, stats.flightSpeed)
				.add(Attributes.FOLLOW_RANGE, stats.followRange)
				.add(Attributes.KNOCKBACK_RESISTANCE, stats.knockbackResistance);
	}

	// ---------------------------------------------------------------- no vanilla AI at all

	@Override
	protected void registerGoals() {
		// Deliberately empty: no vanilla Goal ever runs any pathfinding search for this boss (see class
		// javadoc). Everything -- targeting, movement, attacks -- is driven from #aiStep/#travel below.
	}

	@Override
	protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
		return new net.minecraft.world.entity.ai.navigation.FlyingPathNavigation(this, level) {
			@Override
			public boolean isStableDestination(net.minecraft.core.BlockPos pos) {
				return true; // never actually pathfinds -- only exists because Mob requires a non-null navigation
			}
		};
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean isBaby() {
		return false;
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return EntityDimensions.scalable(SIZE, SIZE);
	}

	@Override
	public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
		return false; // no vanilla contact-melee -- melee only ever happens through Ground Attack (BehemothCombat)
	}

	// ---------------------------------------------------------------- incoming damage

	@Override
	protected void actuallyHurt(DamageSource source, float amount) {
		super.actuallyHurt(source, amount);
		if (this.random.nextInt(3) == 0) {
			triggerAnim("action", "hit");
		}
		// Refresh the boss bar the instant a hit lands, rather than waiting up to a tick for the next
		// aiStep -- players reported the bar feeling like it lagged behind the boss's real health.
		if (level() instanceof ServerLevel server) {
			updateBossBar(server);
		}
	}

	// ---------------------------------------------------------------- per-tick

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		acquireTarget(server);
		updatePhaseAndEnrage(server);
		tickAirborneTracking();
		runScheduledTasks(server);
		if (lowAltitudeTicks > 0) {
			lowAltitudeTicks--;
		}
		if (netherstormTicksLeft > 0) {
			netherstormTicksLeft--;
			if (tickCount % 10 == 0) {
				BehemothCombat.netherstormPulse(this);
			}
		}
		tickCombat(server);
		updateBossBar(server);
		ambientAura(server);
	}

	private void ambientAura(ServerLevel server) {
		if (tickCount % 6 != 0) {
			return;
		}
		double w = getBbWidth();
		Vector3f color = phase >= 3 ? new Vector3f(1.0f, 0.25f, 0.05f) : new Vector3f(0.85f, 0.15f, 0.35f);
		for (int i = 0; i < (phase >= 2 ? 3 : 1); i++) {
			double ox = (server.random.nextDouble() - 0.5) * w;
			double oy = server.random.nextDouble() * getBbHeight();
			double oz = (server.random.nextDouble() - 0.5) * w;
			server.sendParticles(new DustParticleOptions(color, 2.6f), getX() + ox, getY() + oy, getZ() + oz, 1, 0.0, 0.01, 0.0, 0.0);
		}
	}

	// ---------------------------------------------------------------- targeting (spec 26)

	private void acquireTarget(ServerLevel server) {
		LivingEntity current = getTarget();
		if (current != null && current.isAlive() && isValidTarget(current)
				&& current.distanceToSqr(this) <= BehemothConfig.stats().followRange * BehemothConfig.stats().followRange) {
			return;
		}
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (Player p : server.players()) {
			if (!isValidTarget(p)) {
				continue;
			}
			double d = p.distanceToSqr(this);
			if (d < BehemothConfig.stats().detectionRange * BehemothConfig.stats().detectionRange && d < bestDist) {
				bestDist = d;
				best = p;
			}
		}
		setTarget(best);
	}

	private boolean isValidTarget(LivingEntity e) {
		return e.isAlive() && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
	}

	/** Players within {@code radius} of {@code target} -- used to prefer group-hitting abilities (spec 26). */
	private int countNearby(LivingEntity target, double radius) {
		return level().getEntitiesOfClass(Player.class, target.getBoundingBox().inflate(radius),
				this::isValidTarget).size();
	}

	/** Ticks every server tick regardless of the combat state machine, so "stayed airborne too long"
	 *  (spec 9: Skyfall) is measured in real time, not in however often {@link #chooseAbility} happens to run. */
	private void tickAirborneTracking() {
		LivingEntity target = getTarget();
		boolean airborne = target != null && !target.onGround() && target.getY() - groundLevelBelow(target) > 4.0;
		airborneTargetTicks = airborne ? airborneTargetTicks + 1 : 0;
	}

	// ---------------------------------------------------------------- phases + enrage (spec 17-20)

	private void updatePhaseAndEnrage(ServerLevel server) {
		double frac = getHealth() / getMaxHealth();
		var ph = BehemothConfig.phases();
		int newPhase = frac <= ph.phase3HealthFraction ? 3 : frac <= ph.phase2HealthFraction ? 2 : 1;
		if (newPhase != phase) {
			phase = newPhase;
			triggerAnim("action", "phase_transition");
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4.0f, 0.6f);
			BehemothCombat.hellwind(this); // the roar itself pushes everyone back a little -- reads as a real escalation
		}
		if (getTarget() != null) {
			combatTicks++;
			if (!enraged && combatTicks > ph.enrageTicks) {
				enraged = true;
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0f, 0.8f);
			}
		} else if (combatTicks > 0) {
			combatTicks = Math.max(0, combatTicks - 5); // cools down, doesn't instantly reset -- leaving briefly shouldn't wipe enrage progress
		}
	}

	private double cooldownMultiplier() {
		var ph = BehemothConfig.phases();
		double m = phase == 3 ? ph.phase3CooldownMultiplier : phase == 2 ? ph.phase2CooldownMultiplier : 1.0;
		return enraged ? m * ph.enrageCooldownMultiplier : m;
	}

	private double speedMultiplier() {
		var ph = BehemothConfig.phases();
		double m = phase == 3 ? ph.phase3SpeedMultiplier : phase == 2 ? ph.phase2SpeedMultiplier : 1.0;
		return enraged ? m * ph.enrageSpeedMultiplier : m;
	}

	private float damageMultiplier() {
		var ph = BehemothConfig.phases();
		return (float) (phase == 3 ? ph.phase3DamageMultiplier : phase == 2 ? ph.phase2DamageMultiplier : 1.0);
	}

	// ---------------------------------------------------------------- movement (AI decision only -- see #travel)

	private Vec3 desiredVelocity = Vec3.ZERO;

	private void tickMovementIntent(ServerLevel server) {
		if (activeAbility == Ability.SOVEREIGN_DESCENT && stage == Stage.ACTIVE) {
			return; // the dive itself drives velocity directly in #tickAbility -- don't fight it here
		}
		LivingEntity target = getTarget();
		Vec3 desiredPos;
		if (target == null) {
			desiredPos = position().add(0, Math.sin(tickCount * 0.02) * 0.02, 0); // idle hover in place
		} else {
			double altitude = lowAltitudeTicks > 0 ? 4.0 : BehemothConfig.stats().preferredAerialAltitude;
			Vec3 toSelfFlat = position().subtract(target.position());
			toSelfFlat = new Vec3(toSelfFlat.x, 0, toSelfFlat.z);
			double horizDist = toSelfFlat.length();
			double preferredHoriz = lowAltitudeTicks > 0 ? 9.0 : 14.0;
			Vec3 dirFlat = horizDist > 1.0e-3 ? toSelfFlat.scale(1.0 / horizDist) : new Vec3(1, 0, 0);
			// slow orbit around the target rather than sitting dead still
			double orbit = tickCount * 0.01;
			Vec3 tangent = new Vec3(-dirFlat.z, 0, dirFlat.x).scale(Math.sin(orbit) * 3.0);
			desiredPos = target.position().add(dirFlat.scale(preferredHoriz)).add(tangent).add(0, altitude, 0);
		}
		Vec3 toDesired = desiredPos.subtract(position());
		double maxSpeed = (target != null ? BehemothConfig.stats().aggressiveFlightSpeed : BehemothConfig.stats().flightSpeed) * speedMultiplier();
		Vec3 accel = toDesired.length() > 1.0e-3 ? toDesired.normalize().scale(Math.min(toDesired.length() * 0.06, maxSpeed)) : Vec3.ZERO;
		Vec3 wanted = desiredVelocity.scale(0.82).add(accel.scale(0.3));
		if (wanted.length() > maxSpeed) {
			wanted = wanted.normalize().scale(maxSpeed);
		}
		// a cheap "don't fly into the wall" probe -- nudge upward rather than pathing around
		if (!level().noCollision(this, getBoundingBox().move(wanted.x * 3, wanted.y * 3, wanted.z * 3))) {
			wanted = new Vec3(wanted.x * 0.3, Math.max(wanted.y, 0.15), wanted.z * 0.3);
		}
		desiredVelocity = wanted;
		if (desiredVelocity.horizontalDistance() > 1.0e-3) {
			double yaw = Math.toDegrees(Math.atan2(-desiredVelocity.x, desiredVelocity.z));
			setYRot((float) yaw);
			setYBodyRot((float) yaw);
		}
	}

	@Override
	public void travel(Vec3 movementInput) {
		if (level().isClientSide()) {
			super.travel(movementInput);
			return;
		}
		tickMovementIntent((ServerLevel) level());
		setDeltaMovement(desiredVelocity);
		move(MoverType.SELF, getDeltaMovement());
	}

	// ---------------------------------------------------------------- combat state machine (spec 6, 17-19, 26)

	private void tickCombat(ServerLevel server) {
		LivingEntity target = getTarget();
		if (stage != Stage.NONE) {
			tickAbility(server, target);
			return;
		}
		if (target == null || server.getGameTime() < globalLockUntil) {
			return;
		}
		Ability choice = chooseAbility(server, target);
		if (choice == null) {
			return;
		}
		startAbility(server, choice);
	}

	private boolean ready(ServerLevel server, Ability a) {
		Long t = abilityReadyAt.get(a);
		return t == null || server.getGameTime() >= t;
	}

	/**
	 * Weighted contextual selection (spec 6/26): builds the list of abilities that are off cooldown and
	 * make sense right now, then picks one at random, favouring whichever the situation calls for and
	 * never immediately repeating {@link #lastAbility} unless it is the only option.
	 */
	private Ability chooseAbility(ServerLevel server, LivingEntity target) {
		boolean targetAirborne = !target.onGround() && target.getY() - groundLevelBelow(target) > 4.0;
		boolean targetGrounded = target.onGround();
		boolean underneath = Math.abs(target.getX() - getX()) < 3.0 && Math.abs(target.getZ() - getZ()) < 3.0 && target.getY() < getY();
		int groupSize = countNearby(target, 12.0);
		boolean lowHealth = getHealth() / getMaxHealth() < 0.25;

		List<Ability> weighted = new ArrayList<>();
		add(weighted, server, Ability.FIREBALL, 3);
		add(weighted, server, Ability.BARRAGE, targetGrounded ? 1 : 2);
		add(weighted, server, Ability.MAGMA_RAIN, targetGrounded ? 3 : 1, groupSize >= 2 ? 3 : 0);
		add(weighted, server, Ability.BEAM, targetAirborne ? 3 : 1, lowHealth ? 2 : 0);
		add(weighted, server, Ability.CINDER_TETHER, targetGrounded ? 3 : 1);
		add(weighted, server, Ability.NETHERSTORM, groupSize >= 2 ? 3 : 1);
		add(weighted, server, Ability.HELLWIND, underneath ? 4 : 1);
		add(weighted, server, Ability.SOVEREIGN_DESCENT, targetGrounded ? 3 : 1, underneath ? 2 : 0);
		if (airborneTargetTicks > BehemothConfig.abilities().skyfallAirborneTicksTrigger) {
			add(weighted, server, Ability.SKYFALL, 5);
		}
		if (weighted.isEmpty()) {
			return null;
		}
		if (weighted.size() > 1) {
			weighted.removeIf(a -> a == lastAbility && weighted.stream().anyMatch(o -> o != lastAbility));
		}
		return weighted.get(server.random.nextInt(weighted.size()));
	}

	private void add(List<Ability> list, ServerLevel server, Ability a, int weight) {
		add(list, server, a, weight, 0);
	}

	private void add(List<Ability> list, ServerLevel server, Ability a, int weight, int bonus) {
		if (!ready(server, a)) {
			return;
		}
		for (int i = 0; i < weight + bonus; i++) {
			list.add(a);
		}
	}

	private void startAbility(ServerLevel server, Ability a) {
		activeAbility = a;
		lastAbility = a;
		stage = Stage.WINDUP;
		stageTicks = 0;
		globalLockUntil = server.getGameTime() + BehemothConfig.abilities().globalAbilityLockTicks;
		String anim = switch (a) {
			case FIREBALL -> "charge_fireball";
			case BARRAGE -> "hellfire_barrage";
			case MAGMA_RAIN -> "magma_rain";
			case BEAM -> "charge_beam";
			case CINDER_TETHER -> "cinder_tether";
			case NETHERSTORM -> "netherstorm";
			case HELLWIND -> "hellwind";
			case SOVEREIGN_DESCENT -> "sovereign_descent";
			case SKYFALL -> "hellfire_barrage";
		};
		triggerAnim("action", anim);
		if (a == Ability.MAGMA_RAIN) {
			magmaImpactsLeft = BehemothConfig.abilities().magmaRainCount;
		}
		if (a == Ability.BARRAGE || a == Ability.SKYFALL) {
			barrageShotsLeft = a == Ability.SKYFALL ? BehemothConfig.abilities().skyfallCount : BehemothConfig.abilities().hellfireCount;
		}
	}

	/** Windup -> active -> recovery for {@link #activeAbility}. Every ability's numbers live in {@link BehemothConfig}. */
	private void tickAbility(ServerLevel server, LivingEntity target) {
		var cfg = BehemothConfig.abilities();
		stageTicks++;
		switch (activeAbility) {
			case FIREBALL -> {
				if (stage == Stage.WINDUP && stageTicks >= 14) {
					if (target != null) {
						BehemothCombat.fireFireball(this, target);
					}
					enterRecovery(server, Ability.FIREBALL, 20, cfg.fireballCooldownTicks);
				}
			}
			case BARRAGE, SKYFALL -> {
				if (stage == Stage.WINDUP && stageTicks >= 10) {
					stage = Stage.ACTIVE;
					stageTicks = 0;
				} else if (stage == Stage.ACTIVE) {
					int total = activeAbility == Ability.SKYFALL ? cfg.skyfallCount : cfg.hellfireCount;
					if (stageTicks % 5 == 0 && barrageShotsLeft > 0 && target != null) {
						int index = total - barrageShotsLeft;
						if (activeAbility == Ability.SKYFALL) {
							BehemothCombat.fireSkyfallVolley(this, target, index);
						} else {
							BehemothCombat.fireBarrageShot(this, target, index, total);
						}
						barrageShotsLeft--;
					}
					if (barrageShotsLeft <= 0) {
						enterRecovery(server, activeAbility, 15,
								activeAbility == Ability.SKYFALL ? cfg.skyfallCooldownTicks : cfg.hellfireCooldownTicks);
					}
				}
			}
			case MAGMA_RAIN -> {
				if (stage == Stage.WINDUP && stageTicks >= 16) {
					stage = Stage.ACTIVE;
					stageTicks = 0;
					if (target != null) {
						for (int i = 0; i < cfg.magmaRainCount; i++) {
							double ang = server.random.nextDouble() * Math.PI * 2;
							double dist = server.random.nextDouble() * cfg.magmaRainRadius;
							Vec3 point = target.position().add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);
							Vec3 ground = groundedPoint(point);
							scheduleWarned(server, ground);
						}
					}
				}
				if (magmaImpactsLeft <= 0 && stage == Stage.ACTIVE) {
					enterRecovery(server, Ability.MAGMA_RAIN, 10, cfg.magmaRainCooldownTicks);
				}
			}
			case BEAM -> {
				if (stage == Stage.WINDUP) {
					if (stageTicks == 1 && target != null) {
						Vec3 dir = target.position().subtract(position());
						beamStartYaw = Math.atan2(dir.x, dir.z) - Math.toRadians(35);
						beamEndYaw = Math.atan2(dir.x, dir.z) + Math.toRadians(35);
					}
					if (stageTicks >= cfg.beamChargeTicks) {
						stage = Stage.ACTIVE;
						stageTicks = 0;
						triggerAnim("action", "beam_fire");
						server.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 3.0f, 0.6f);
					} else if (stageTicks % 4 == 0) {
						server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY(), getZ(), (int) (6 + 10.0 * stageTicks / cfg.beamChargeTicks), 0.6, 0.6, 0.6, 0.05);
					}
				} else if (stage == Stage.ACTIVE) {
					double t = Math.min(1.0, stageTicks / (double) cfg.beamFireTicks);
					double yaw = beamStartYaw + (beamEndYaw - beamStartYaw) * t;
					Vec3 dir = new Vec3(Math.sin(yaw), 0, Math.cos(yaw));
					BehemothCombat.beamTick(this, dir);
					if (stageTicks >= cfg.beamFireTicks) {
						enterRecovery(server, Ability.BEAM, 20, cfg.beamCooldownTicks);
					}
				}
			}
			case CINDER_TETHER -> {
				if (stage == Stage.WINDUP && stageTicks >= 20) {
					int hits = BehemothCombat.fireCinderTether(this);
					if (hits > 0) {
						lowAltitudeTicks = Math.max(lowAltitudeTicks, cfg.lowAltitudeTicks);
					}
					enterRecovery(server, Ability.CINDER_TETHER, 10, cfg.cinderTetherCooldownTicks);
				}
			}
			case NETHERSTORM -> {
				if (stage == Stage.WINDUP && stageTicks >= 20) {
					netherstormTicksLeft = cfg.netherstormDurationTicks;
					enterRecovery(server, Ability.NETHERSTORM, 5, cfg.netherstormCooldownTicks);
				}
			}
			case HELLWIND -> {
				if (stage == Stage.WINDUP && stageTicks >= 18) {
					BehemothCombat.hellwind(this);
					enterRecovery(server, Ability.HELLWIND, 15, cfg.hellwindCooldownTicks);
				}
			}
			case SOVEREIGN_DESCENT -> {
				if (stage == Stage.WINDUP && stageTicks >= 16) {
					stage = Stage.ACTIVE;
					stageTicks = 0;
				} else if (stage == Stage.ACTIVE) {
					// a fast, visible dive rather than a teleport -- travel() keeps steering the body,
					// this just overrides the vertical component while the dive is in progress
					setDeltaMovement(getDeltaMovement().x * 0.5, -1.1, getDeltaMovement().z * 0.5);
					move(MoverType.SELF, getDeltaMovement());
					if (onGround() || stageTicks > 40) {
						BehemothCombat.sovereignDescentImpact(this);
						triggerAnim("action", "ground_land");
						lowAltitudeTicks = Math.max(lowAltitudeTicks, cfg.groundAssaultTicks);
						enterRecovery(server, Ability.SOVEREIGN_DESCENT, 20, cfg.sovereignDescentCooldownTicks);
					}
				}
			}
		}
	}

	private void enterRecovery(ServerLevel server, Ability a, int recoverTicks, int cooldownTicks) {
		abilityReadyAt.put(a, server.getGameTime() + (long) (cooldownTicks * cooldownMultiplier()));
		stage = Stage.RECOVER;
		stageTicks = 0;
		schedule(server, recoverTicks, () -> {
			stage = Stage.NONE;
			activeAbility = null;
		});
	}

	/** Snaps a horizontal point down onto the Nether floor beneath it (for Magma Rain's impact markers). */
	private Vec3 groundedPoint(Vec3 point) {
		var pos = net.minecraft.core.BlockPos.containing(point);
		int y = level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
		return new Vec3(point.x, Math.min(point.y, y), point.z);
	}

	private double groundLevelBelow(LivingEntity e) {
		return level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				(int) e.getX(), (int) e.getZ());
	}

	private void scheduleWarned(ServerLevel server, Vec3 pos) {
		BehemothCombat.magmaWarning(this, pos);
		schedule(server, BehemothConfig.abilities().magmaRainWarningTicks, () -> {
			BehemothCombat.magmaImpact(this, pos);
			magmaImpactsLeft--;
		});
	}

	// ---------------------------------------------------------------- small per-entity task queue (mirrors AllMightAbilities)

	private void schedule(ServerLevel server, int delayTicks, Runnable run) {
		tasks.add(new Task(server.getGameTime() + Math.max(0, delayTicks), run));
	}

	private void runScheduledTasks(ServerLevel server) {
		if (tasks.isEmpty()) {
			return;
		}
		long now = server.getGameTime();
		for (Task t : new ArrayList<>(tasks)) {
			if (t.due() <= now) {
				tasks.remove(t);
				if (isAlive()) {
					t.run().run();
				}
			}
		}
	}

	// ---------------------------------------------------------------- boss bar (spec 4)

	private EventBossBar bossBar() {
		if (bossBar == null) {
			bossBar = new EventBossBar(getUUID(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS, false);
		}
		return bossBar;
	}

	private void updateBossBar(ServerLevel server) {
		if (isRemoved() || isDeadOrDying()) {
			return;
		}
		bossBar().update(server, blockPosition(), BehemothConfig.stats().detectionRange * 1.4,
				Component.translatable("entity.projecthero.abyssal_behemoth").withStyle(net.minecraft.ChatFormatting.DARK_RED),
				getHealth() / getMaxHealth());
	}

	// ---------------------------------------------------------------- death (spec 25)

	private int deathTicks = -1;

	@Override
	public void die(DamageSource source) {
		if (deathTicks >= 0) {
			return;
		}
		setTarget(null);
		setDeltaMovement(Vec3.ZERO);
		setNoAi(true);
		deathTicks = 60; // 3s cinematic collapse before removal
		triggerAnim("action", "death");
		if (level() instanceof ServerLevel server) {
			server.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 4.0f, 0.5f);
			server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + getBbHeight() * 0.5, getZ(), 1, 0, 0, 0, 0);
			server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + getBbHeight() * 0.5, getZ(), 60, getBbWidth() * 0.5, getBbHeight() * 0.4, getBbWidth() * 0.5, 0.1);
			bossBar().clear(server);
			com.projecthero.mod.behemoth.BehemothSpawner.onKilled(server);
			dropLoot(server, source);
		}
	}

	private void dropLoot(ServerLevel server, DamageSource source) {
		var lootTable = server.getServer().reloadableRegistries().getLootTable(
				net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
						com.projecthero.mod.ProjectHeroMod.id("entities/abyssal_behemoth")));
		LootParams.Builder params = new LootParams.Builder(server)
				.withParameter(LootContextParams.THIS_ENTITY, this)
				.withParameter(LootContextParams.ORIGIN, position())
				.withParameter(LootContextParams.DAMAGE_SOURCE, source)
				.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
				.withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity())
				.withLuck(0);
		lootTable.getRandomItems(params.create(LootContextParamSets.ENTITY), this.getLootTableSeed(), stack -> spawnAtLocation(stack));
	}

	@Override
	public void tick() {
		super.tick();
		if (deathTicks < 0) {
			return;
		}
		if (level() instanceof ServerLevel server) {
			if (deathTicks % 6 == 0) {
				server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() * 0.4, getZ(),
						20, getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.04);
			}
			if (--deathTicks <= 0) {
				remove(Entity.RemovalReason.KILLED);
			}
		}
	}

	// ---------------------------------------------------------------- GeckoLib

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		AnimationController<AbyssalBehemothEntity> action = new AnimationController<>(this, "action", 6, state -> PlayState.STOP);
		for (String name : new String[] { "charge_fireball", "fireball_attack", "hellfire_barrage", "charge_beam", "beam_fire",
				"magma_rain", "cinder_tether", "sovereign_descent", "ground_land", "ground_attack", "hellwind", "netherstorm",
				"hit", "phase_transition", "death" }) {
			action.triggerableAnim(name, RawAnimation.begin().thenPlay("animation.abyssal_behemoth." + name));
		}
		controllers.add(new AnimationController<>(this, "main", 8, this::mainPredicate));
		controllers.add(action);
	}

	private PlayState mainPredicate(AnimationState<AbyssalBehemothEntity> state) {
		if (isDeadOrDying() || deathTicks >= 0) {
			return PlayState.STOP; // the "action" controller's death trigger owns this instead
		}
		if (lowAltitudeTicks > 0) {
			return state.setAndContinue(RawAnimation.begin().thenLoop("animation.abyssal_behemoth.ground_idle"));
		}
		if (enraged) {
			return state.setAndContinue(RawAnimation.begin().thenLoop("animation.abyssal_behemoth.enraged_idle"));
		}
		if (getTarget() != null) {
			return state.setAndContinue(RawAnimation.begin().thenLoop("animation.abyssal_behemoth.fly"));
		}
		return state.setAndContinue(RawAnimation.begin().thenLoop("animation.abyssal_behemoth.idle"));
	}

	// ---------------------------------------------------------------- persistence (phase/enrage survive a reload)

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("Phase", phase);
		tag.putBoolean("Enraged", enraged);
		tag.putInt("CombatTicks", combatTicks);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		phase = tag.contains("Phase") ? tag.getInt("Phase") : 1;
		enraged = tag.getBoolean("Enraged");
		combatTicks = tag.getInt("CombatTicks");
	}
}
