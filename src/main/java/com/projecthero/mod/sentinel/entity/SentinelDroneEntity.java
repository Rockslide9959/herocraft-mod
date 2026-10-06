package com.projecthero.mod.sentinel.entity;

import com.projecthero.mod.sentinel.SentinelConfig;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * v0.15.1: a <b>Sentinel Drone</b> -- the Sentinel Program's flying scout. Small, fragile (16 health) and always airborne:
 * it circles its prey a few blocks above head height, bobbing and switching direction, and snaps a thin red laser at it
 * every two seconds (4 damage, stopped by cover). v0.15.3: the laser is a {@link SentinelAimedShot} -- a thin red aiming
 * line trails its prey, locks 0.45 s before it fires, and a target that moves off the line in time is missed. Carrier Sentinels release them in pairs, and the first wave of every
 * purge is all drones. Flight is a hand-rolled steer (the same approach as the Parademons), not a path-finder.
 */
public class SentinelDroneEntity extends SentinelRobot {
	public static final int DEATH_TICKS = 20;

	private int laserCooldown = 30;
	private SentinelAimedShot laser;
	private boolean shotAnimPlayed;
	private int orbitDir = 1;
	private int orbitFlipTicks = 40;
	private final float bobPhase;
	private Vec3 home;

	public SentinelDroneEntity(EntityType<? extends SentinelDroneEntity> type, Level level) {
		super(type, level);
		this.xpReward = 5;
		this.bobPhase = random.nextFloat() * 100f;
		this.orbitDir = random.nextBoolean() ? 1 : -1;
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		SentinelConfig.Drone cfg = SentinelConfig.drone();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, cfg.health)
				.add(Attributes.ARMOR, cfg.armor)
				.add(Attributes.ATTACK_DAMAGE, 2.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FLYING_SPEED, 0.5)
				.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0f));
	}

	@Override
	protected double huntRange() {
		return 40.0;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel server) || isDeadOrDying()) {
			return;
		}
		setNoGravity(true);
		fallDistance = 0;
		if (home == null) {
			home = position();
		}
		LivingEntity target = getTarget();
		if (--orbitFlipTicks <= 0 || horizontalCollision) {
			orbitDir = -orbitDir;
			orbitFlipTicks = 30 + random.nextInt(50);
		}
		Vec3 want;
		if (target != null) {
			Vec3 aim = target.position();
			Vec3 away = position().subtract(aim);
			double ang = Math.atan2(away.z, away.x) + orbitDir * 0.35;
			double radius = 6.5;
			double up = target.getBbHeight() + 3.0 + Math.sin((tickCount + bobPhase) * 0.08) * 1.5;
			want = aim.add(Math.cos(ang) * radius, up, Math.sin(ang) * radius);
			getLookControl().setLookAt(target, 40f, 40f);
			float yaw = (float) (Math.atan2(aim.z - getZ(), aim.x - getX()) * (180.0 / Math.PI)) - 90.0f;
			setYRot(yaw);
			yBodyRot = yaw;
			yHeadRot = yaw;
			tickLaser(server, target);
		} else {
			if (laser != null) {
				tickLaser(server, null);
			}
			double a = (tickCount + bobPhase) * 0.03 * orbitDir;
			int ground = server.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(home.x), (int) Math.floor(home.z));
			want = new Vec3(home.x + Math.cos(a) * 5, Math.max(home.y, ground + 6), home.z + Math.sin(a) * 5);
		}
		Vec3 dir = want.subtract(position());
		double len = dir.length();
		double speed = 0.42;
		Vec3 desired = len < 0.3 ? Vec3.ZERO : dir.scale(Math.min(speed, len * 0.3) / len);
		setDeltaMovement(getDeltaMovement().lerp(desired, 0.2));
		if (tickCount % 6 == 0) {
			server.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY() + 0.25, getZ(), 1, 0.15, 0.02, 0.15, 0.0);
		}
		if (tickCount % 30 == 0) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEE_LOOP_AGGRESSIVE, SoundSource.HOSTILE, 0.25f, 0.5f);
		}
	}

	/** v0.15.3: the laser is an aimed shot -- a red aiming line that trails the target, locks, then fires. */
	public static final int LASER_FIRE = 18, LASER_LOCK = 9;

	private void tickLaser(ServerLevel server, LivingEntity target) {
		if (laser != null) {
			if (laser.state() == SentinelAimedShot.STATE_FIRE && laser.lastPulseTick() > 0 && !shotAnimPlayed) {
				shotAnimPlayed = true;
				triggerAnim("action", "shoot");
			}
			if (laser.tick(server)) {
				laser = null;
			}
			return;
		}
		if (--laserCooldown > 0 || target == null) {
			return;
		}
		SentinelConfig.Drone cfg = SentinelConfig.drone();
		if (distanceTo(target) > cfg.laserRange || !hasLineOfSight(target)) {
			laserCooldown = 10;
			return;
		}
		laserCooldown = cfg.laserCooldownTicks + random.nextInt(15);
		shotAnimPlayed = false;
		laser = new SentinelAimedShot(this, target, SentinelBeamEntity.Kind.LASER, this::laserOrigin, cfg.laserDamage, LASER_FIRE, LASER_LOCK,
				1, 1, cfg.laserRange + 6.0, 0.5);
		laser.tick(server);
	}

	/** Where the laser leaves: under its chin. */
	public Vec3 laserOrigin() {
		return position().add(0, getBbHeight() * 0.3, 0);
	}

	/** The laser shot in progress, if any (tests). */
	public SentinelAimedShot currentShot() {
		return laser;
	}

	/** Test hook: start a shot right away. */
	public void fireNow() {
		laserCooldown = 0;
	}

	@Override
	public void travel(Vec3 input) {
		// a flyer: move by its own velocity, with air drag, ignoring the walking input
		if (isControlledByLocalInstance()) {
			move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
			setDeltaMovement(getDeltaMovement().scale(0.91));
		}
		calculateEntityAnimation(false);
	}

	@Override
	public boolean onClimbable() {
		return false;
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos) {
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.BEACON_AMBIENT;
	}

	@Override
	public float getVoicePitch() {
		return 1.8f;
	}

	@Override
	protected float getSoundVolume() {
		return 0.6f;
	}

	@Override
	public void die(DamageSource source) {
		if (dead || isRemoved()) {
			return;
		}
		if (laser != null) {
			laser = null;
			syncShot(SentinelAimedShot.STATE_OFF, SentinelBeamEntity.Kind.LASER, Vec3.ZERO, Vec3.ZERO);
		}
		super.die(source);
		setNoGravity(false);
		triggerAnim("action", "death");
	}

	@Override
	protected void tickDeath() {
		++deathTime;
		if (level() instanceof ServerLevel server) {
			if (deathTime % 3 == 0) {
				server.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.4, getZ(), 2, 0.1, 0.1, 0.1, 0.01);
			}
			if ((deathTime >= DEATH_TICKS || onGround()) && !isRemoved()) {
				server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.4, getZ(), 1, 0, 0, 0, 0);
				server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.4, getZ(), 12, 0.3, 0.3, 0.3, 0.2);
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.6f, 1.6f);
				remove(Entity.RemovalReason.KILLED);
			}
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		setNoGravity(true);
	}

	// ---------------------------------------------------------------- GeckoLib

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.sentinel_drone.idle");
	private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.sentinel_drone.fly");

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "main", 4, this::mainPredicate));
		AnimationController<SentinelDroneEntity> action = new AnimationController<>(this, "action", 2, state -> PlayState.STOP);
		action.triggerableAnim("shoot", RawAnimation.begin().thenPlay("animation.sentinel_drone.shoot"));
		action.triggerableAnim("death", RawAnimation.begin().thenPlay("animation.sentinel_drone.death"));
		controllers.add(action);
	}

	private PlayState mainPredicate(AnimationState<SentinelDroneEntity> state) {
		if (isDeadOrDying()) {
			return PlayState.STOP;
		}
		return state.setAndContinue(getDeltaMovement().horizontalDistanceSqr() > 0.01 ? FLY : IDLE);
	}
}
