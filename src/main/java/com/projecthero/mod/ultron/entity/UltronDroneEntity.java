package com.projecthero.mod.ultron.entity;

import java.util.UUID;

import com.projecthero.mod.ultron.UltronCombat;
import com.projecthero.mod.ultron.UltronConfig;
import com.projecthero.mod.ultron.UltronFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.12: an <b>Ultron Drone</b> -- the swarm. A flying Ultron body (30 health) that hovers 4-8 blocks up, circling its
 * prey, and snaps red repulsor bolts at it (hit-scan, a short arm-raise first, 4 damage). Flight is a hand-rolled steer
 * (the same approach as the Sentinel Drones), not a path-finder.
 *
 * <p>A <b>shield carrier</b> ({@link #carryShieldFor}) instead orbits the Ultron Sentry and feeds its shield through a red
 * tether -- kill all of them to break it.
 */
public class UltronDroneEntity extends UltronRobot {
	private static final EntityDataAccessor<Integer> DATA_TETHER = SynchedEntityData.defineId(UltronDroneEntity.class, EntityDataSerializers.INT);

	private int boltCooldown = 30;
	private int aim;
	private int orbitDir;
	private int orbitFlipTicks = 40;
	private final float bobPhase;
	private Vec3 home;
	private UUID shieldFor;

	public UltronDroneEntity(EntityType<? extends UltronDroneEntity> type, Level level) {
		super(type, level);
		this.xpReward = 6;
		this.bobPhase = random.nextFloat() * 100f;
		this.orbitDir = random.nextBoolean() ? 1 : -1;
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		UltronConfig.Drone cfg = UltronConfig.drone();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, cfg.health)
				.add(Attributes.ARMOR, cfg.armor)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FLYING_SPEED, 0.5)
				.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_TETHER, -1);
	}

	@Override
	public UltronSkin skin() {
		return UltronSkin.DRONE;
	}

	@Override
	protected void registerGoals() {
	}

	/** Makes this drone one of the Sentry's shield carriers. */
	public void carryShieldFor(LivingEntity sentry) {
		this.shieldFor = sentry.getUUID();
		entityData.set(DATA_TETHER, sentry.getId());
	}

	public boolean isShieldCarrier() {
		return shieldFor != null || entityData.get(DATA_TETHER) >= 0;
	}

	public UUID shieldFor() {
		return shieldFor;
	}

	/** The client-side tether target's entity id (-1 for none). */
	public int tetherId() {
		return entityData.get(DATA_TETHER);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel server) || isDeadOrDying() || isNoAi()) {
			return;
		}
		setNoGravity(true);
		fallDistance = 0;
		if (home == null) {
			home = position();
		}
		if (isStunned()) {
			setDeltaMovement(getDeltaMovement().add(0, -0.02, 0));
			return;
		}
		if (shieldFor != null) {
			tickCarrier(server);
			return;
		}
		LivingEntity target = getTarget();
		if (target != null && (!UltronCombat.canTarget(target) || distanceToSqr(target) > 48 * 48)) {
			setTarget(null);
			target = null;
		}
		if (target == null && tickCount % 20 == 0) {
			Player p = UltronCombat.nearestPlayer(server, position(), 40);
			if (p != null) {
				setTarget(p);
				target = p;
			}
		}
		if (--orbitFlipTicks <= 0 || horizontalCollision) {
			orbitDir = -orbitDir;
			orbitFlipTicks = 30 + random.nextInt(50);
		}
		Vec3 want;
		if (target != null) {
			Vec3 aimAt = target.position();
			Vec3 away = position().subtract(aimAt);
			double ang = Math.atan2(away.z, away.x) + orbitDir * 0.3;
			double radius = 7.0;
			double up = 4.0 + 2.0 * (0.5 + 0.5 * Math.sin((tickCount + bobPhase) * 0.05)) + Math.sin((tickCount + bobPhase) * 0.13) * 0.5;
			want = aimAt.add(Math.cos(ang) * radius, up, Math.sin(ang) * radius);
			faceTowards(target);
			tickBolt(server, target);
		} else {
			setAction(ACTION_NONE);
			aim = 0;
			double a = (tickCount + bobPhase) * 0.03 * orbitDir;
			int ground = server.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(home.x), (int) Math.floor(home.z));
			want = new Vec3(home.x + Math.cos(a) * 5, Math.max(home.y, ground + 5), home.z + Math.sin(a) * 5);
		}
		steer(want, 0.4);
		thrusterFx(server);
	}

	private void tickCarrier(ServerLevel server) {
		Entity sentry = server.getEntity(shieldFor);
		if (!(sentry instanceof LivingEntity s) || !s.isAlive()) {
			shieldFor = null;
			entityData.set(DATA_TETHER, -1);
			return;
		}
		if (entityData.get(DATA_TETHER) != s.getId()) {
			entityData.set(DATA_TETHER, s.getId());
		}
		double a = (tickCount + bobPhase) * 0.06 * orbitDir + bobPhase;
		double r = s.getBbWidth() * 0.5 + 3.5;
		Vec3 want = s.position().add(Math.cos(a) * r, s.getBbHeight() * 0.55 + Math.sin((tickCount + bobPhase) * 0.1), Math.sin(a) * r);
		steer(want, 0.5);
		faceTowards(s);
		thrusterFx(server);
	}

	private void faceTowards(Entity target) {
		getLookControl().setLookAt(target, 40f, 40f);
		float yaw = (float) (Math.atan2(target.getZ() - getZ(), target.getX() - getX()) * (180.0 / Math.PI)) - 90.0f;
		setYRot(yaw);
		yBodyRot = yaw;
		yHeadRot = yaw;
	}

	private void steer(Vec3 want, double speed) {
		Vec3 dir = want.subtract(position());
		double len = dir.length();
		Vec3 desired = len < 0.3 ? Vec3.ZERO : dir.scale(Math.min(speed, len * 0.3) / len);
		setDeltaMovement(getDeltaMovement().lerp(desired, 0.2));
	}

	private void thrusterFx(ServerLevel server) {
		if (tickCount % 4 == 0) {
			server.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY() - 0.1, getZ(), 1, 0.12, 0.02, 0.12, 0.0);
		}
		if (tickCount % 40 == 0) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 0.35f, 1.8f);
		}
	}

	/** Raise the palm for a moment, then a hit-scan red bolt. */
	private void tickBolt(ServerLevel server, LivingEntity target) {
		UltronConfig.Drone cfg = UltronConfig.drone();
		if (aim > 0) {
			aim++;
			if (aim >= 10) {
				aim = 0;
				setAction(ACTION_NONE);
				Vec3 from = palm();
				Vec3 at = target.position().add(0, target.getBbHeight() * 0.55, 0);
				Vec3 dir = at.subtract(from).normalize().add(random.nextGaussian() * 0.02, random.nextGaussian() * 0.02, random.nextGaussian() * 0.02);
				UltronCombat.hitscan(server, this, from, dir, cfg.boltRange, cfg.boltDamage, UltronFx.BOLT, 5, 0.25f);
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 0.6f, 2.0f);
			}
			return;
		}
		if (--boltCooldown > 0) {
			return;
		}
		if (distanceTo(target) > cfg.boltRange || !hasLineOfSight(target)) {
			boltCooldown = 10;
			return;
		}
		boltCooldown = cfg.boltCooldownTicks + random.nextInt(20);
		aim = 1;
		setAction(ACTION_AIM);
	}

	/** Where the bolt leaves: the right palm, roughly. */
	public Vec3 palm() {
		Vec3 look = getViewVector(1.0f);
		Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
		return position().add(0, getBbHeight() * 0.72, 0).add(look.scale(0.6)).add(right.scale(0.35));
	}

	/** Test hook: start a bolt right away. */
	public void fireNow() {
		boltCooldown = 0;
	}

	@Override
	public void travel(Vec3 input) {
		if (isControlledByLocalInstance()) {
			move(MoverType.SELF, getDeltaMovement());
			setDeltaMovement(getDeltaMovement().scale(0.91));
		}
		calculateEntityAnimation(false);
	}

	@Override
	public boolean onClimbable() {
		return false;
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
	}

	@Override
	public void die(net.minecraft.world.damagesource.DamageSource source) {
		super.die(source);
		setNoGravity(false);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (shieldFor != null) {
			tag.putUUID("ShieldFor", shieldFor);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		shieldFor = tag.hasUUID("ShieldFor") ? tag.getUUID("ShieldFor") : null;
		setNoGravity(true);
	}
}
