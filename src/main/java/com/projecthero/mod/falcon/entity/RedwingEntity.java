package com.projecthero.mod.falcon.entity;

import java.util.UUID;

import com.projecthero.mod.falcon.Falcon;
import com.projecthero.mod.falcon.FalconCombat;
import com.projecthero.mod.falcon.FalconConfig;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Redwing, the Falcon's drone -- a small red-and-black bird-shaped drone that rides at his shoulder (it keeps up
 * with him at full boost) while he is the Falcon. Z sends it at a target to fire {@link FalconConfig#LASER_SHOTS} laser
 * bolts; Shift+Z sends it on a bombing run over the aimed spot (micro-bombs, then an EMP that stuns). Can't be hit, never
 * saved, gone when its owner stops being the Falcon. Driven entirely on the server; the client only draws it.
 */
public class RedwingEntity extends Entity {
	public static final int MODE_FOLLOW = 0;
	public static final int MODE_STRIKE = 1;
	public static final int MODE_BOMB = 2;

	private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(RedwingEntity.class, EntityDataSerializers.INT);

	private UUID owner;
	private int targetId = -1;
	private int shotsLeft;
	private int nextShot;
	private Vec3 bombAt;
	private Vec3 bombDir;
	private int bombStage;
	private int bombsLeft;
	private int nextBomb;
	private int modeTicks;
	private float orbit;

	public RedwingEntity(EntityType<? extends RedwingEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
	}

	public void setOwner(ServerPlayer owner) {
		this.owner = owner.getUUID();
	}

	public UUID owner() {
		return owner;
	}

	public int mode() {
		return entityData.get(MODE);
	}

	/** On a strike or a bombing run. */
	public boolean busy() {
		return mode() != MODE_FOLLOW;
	}

	public void strike(LivingEntity target) {
		targetId = target.getId();
		shotsLeft = FalconConfig.LASER_SHOTS;
		nextShot = 6;
		modeTicks = 0;
		entityData.set(MODE, MODE_STRIKE);
	}

	public void bomb(Vec3 at, Vec3 look) {
		bombAt = at;
		Vec3 flat = new Vec3(look.x, 0, look.z);
		bombDir = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		bombStage = 0;
		bombsLeft = FalconConfig.BOMBS;
		nextBomb = 0;
		modeTicks = 0;
		entityData.set(MODE, MODE_BOMB);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(MODE, MODE_FOLLOW);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level)) {
			return; // the client follows the synced position
		}
		ServerPlayer p = owner == null ? null : (ServerPlayer) level.getPlayerByUUID(owner);
		if (p == null || !Falcon.hasPower(p) || !p.isAlive() || p.level() != level) {
			discard();
			return;
		}
		modeTicks++;
		orbit += 0.08f;
		Vec3 goal;
		double maxStep;
		switch (mode()) {
			case MODE_STRIKE -> {
				LivingEntity target = level.getEntity(targetId) instanceof LivingEntity le && le.isAlive() && FalconCombat.isTarget(p, le) ? le : null;
				if (target == null || shotsLeft <= 0 || modeTicks > 120) {
					entityData.set(MODE, MODE_FOLLOW);
					goal = followPoint(p);
					maxStep = 1.2;
					break;
				}
				Vec3 tc = target.position().add(0, target.getBbHeight() * 0.5, 0);
				goal = target.position().add(Math.cos(orbit * 3) * 4.0, target.getBbHeight() + 2.5, Math.sin(orbit * 3) * 4.0);
				maxStep = 1.6;
				if (--nextShot <= 0 && position().distanceTo(tc) < 14.0) {
					nextShot = FalconConfig.LASER_INTERVAL;
					shotsLeft--;
					AbilityHelpers.line(level, position(), tc, Falcon.RED, 3.0);
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, tc.x, tc.y, tc.z, 5, 0.2, 0.2, 0.2, 0.1);
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5f, 2.0f);
					FalconCombat.strike(p, target, position(), FalconConfig.LASER_DAMAGE, 0.05, 0.0);
				}
			}
			case MODE_BOMB -> {
				if (bombAt == null || modeTicks > 200) {
					entityData.set(MODE, MODE_FOLLOW);
					goal = followPoint(p);
					maxStep = 1.2;
					break;
				}
				// stage 0: fly to the run's start, 7 blocks up and behind the spot; stage 1: the run across it, dropping bombs
				double runLen = FalconConfig.BOMBS * FalconConfig.BOMB_INTERVAL * 0.45;
				Vec3 start = bombAt.add(bombDir.scale(-runLen * 0.5)).add(0, 7, 0);
				if (bombStage == 0) {
					goal = start;
					maxStep = 1.8;
					if (position().distanceTo(start) < 1.5) {
						bombStage = 1;
						modeTicks = 0;
						level.playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 1.0f, 1.6f);
					}
				} else {
					goal = position().add(bombDir.scale(0.45));
					goal = new Vec3(goal.x, bombAt.y + 6, goal.z);
					maxStep = 0.5;
					if (--nextBomb <= 0 && bombsLeft > 0) {
						nextBomb = FalconConfig.BOMB_INTERVAL;
						bombsLeft--;
						dropBomb(level, p);
					}
					if (bombsLeft <= 0 && nextBomb <= 0) {
						emp(level, p);
						entityData.set(MODE, MODE_FOLLOW);
					}
				}
			}
			default -> {
				goal = followPoint(p);
				maxStep = 3.0;
			}
		}
		Vec3 to = goal.subtract(position());
		double d = to.length();
		if (d > 48.0) {
			setPos(goal);
			return;
		}
		double step = Math.min(maxStep, Math.max(d * 0.45, Math.min(d, 0.05)));
		Vec3 v = d < 1.0e-3 ? Vec3.ZERO : to.scale(step / d);
		setDeltaMovement(v);
		setPos(position().add(v));
		Vec3 face = mode() == MODE_FOLLOW ? p.getLookAngle() : v;
		if (face.horizontalDistanceSqr() > 1.0e-4) {
			setYRot((float) Math.toDegrees(Math.atan2(-face.x, face.z)));
		}
		if (tickCount % 3 == 0) {
			Vec3 back = position().subtract(Vec3.directionFromRotation(0, getYRot()).scale(0.3));
			level.sendParticles(Falcon.RED, back.x, back.y, back.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	/** At his shoulder: up and to the right, a little behind. */
	private Vec3 followPoint(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 base = p.position().add(right.scale(1.1)).add(0, 2.1 + Math.sin(orbit * 2) * 0.12, 0);
		Vec3 flat = new Vec3(look.x, 0, look.z);
		return base.subtract(flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize().scale(0.6));
	}

	private void dropBomb(ServerLevel level, ServerPlayer p) {
		Vec3 from = position();
		BlockHitResult hit = level.clip(new ClipContext(from, from.add(0, -24, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
		Vec3 at = hit.getType() == HitResult.Type.MISS ? from.add(0, -12, 0) : hit.getLocation();
		AbilityHelpers.line(level, from, at, ParticleTypes.SMOKE, 1.5);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 6, 0.6, 0.2, 0.6, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7f, 1.4f);
		bombBlast(p, at);
	}

	/** One micro-bomb going off at {@code at}: {@link FalconConfig#BOMB_DAMAGE} to everything in the blast. Public for the gametests. */
	public static void bombBlast(ServerPlayer p, Vec3 at) {
		for (LivingEntity e : FalconCombat.within(p, at.add(0, 0.5, 0), FalconConfig.BOMB_RADIUS, e -> FalconCombat.isTarget(p, e))) {
			FalconCombat.strike(p, e, at, FalconConfig.BOMB_DAMAGE, 0.5, 0.25);
		}
	}

	private void emp(ServerLevel level, ServerPlayer p) {
		Vec3 c = bombAt;
		for (int i = 0; i < 40; i++) {
			double a = i * Math.PI * 2 / 40;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x + Math.cos(a) * FalconConfig.EMP_RADIUS, c.y + 0.6,
					c.z + Math.sin(a) * FalconConfig.EMP_RADIUS, 1, 0, 0.1, 0, 0.0);
		}
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2f, 1.6f);
		for (LivingEntity e : FalconCombat.within(p, c.add(0, 0.5, 0), FalconConfig.EMP_RADIUS, e -> FalconCombat.isTarget(p, e))) {
			if (FalconCombat.isBoss(e)) {
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, FalconConfig.EMP_STUN_TICKS / 2, 1); // bosses: only slowed
				continue;
			}
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, FalconConfig.EMP_STUN_TICKS, 5);
			AbilityHelpers.applyControl(e, MobEffects.WEAKNESS, FalconConfig.EMP_STUN_TICKS, 1);
			if (e instanceof Mob mob) {
				mob.setTarget(null);
			}
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 8, 0.3, 0.4, 0.3, 0.1);
		}
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public void push(Entity entity) {
	}
}
