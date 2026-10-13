package com.projecthero.mod.ironman.entity;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.combat.HeroTargets;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.16.1: the Hulkbuster's ROCKET FIST -- its right forearm + fist launched off the arm on the {@code rocket_fist} clip's
 * launch tick (the body hides {@code forearm_r} while it is away), flying straight out along the aim. The first thing it
 * hits takes the full damage and a burst hits everything within 2.5 blocks for a third of it, with a big shove; then (or
 * at its range) it turns round and flies back to the owner's right shoulder, arriving on the clip's {@code fist_return}
 * tick, and is gone. Drawn by the client as the body's own {@code forearm_r} / {@code hand_r} bones. Never saved.
 */
public class HulkbusterFistEntity extends Entity implements GeoEntity {
	private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(HulkbusterFistEntity.class,
			EntityDataSerializers.OPTIONAL_UUID);
	private static final EntityDataAccessor<Boolean> RETURNING = SynchedEntityData.defineId(HulkbusterFistEntity.class,
			EntityDataSerializers.BOOLEAN);
	/** Out-going speed, blocks / tick. */
	public static final double SPEED = 2.0;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private Vec3 dir = new Vec3(0, 0, 1);
	private float damage;
	private int totalTicks = 24;
	private boolean struck;
	private final Set<Integer> hit = new HashSet<>();

	public HulkbusterFistEntity(EntityType<? extends HulkbusterFistEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/** Launch one from {@code from} along {@code dir}; it is back with its owner after {@code totalTicks}. */
	public static HulkbusterFistEntity launch(ServerPlayer owner, Vec3 from, Vec3 dir, float damage, int totalTicks) {
		HulkbusterFistEntity f = new HulkbusterFistEntity(IronManEntityTypes.HULKBUSTER_FIST, owner.level());
		f.entityData.set(OWNER, Optional.of(owner.getUUID()));
		f.dir = dir.normalize();
		f.damage = damage;
		f.totalTicks = Math.max(6, totalTicks);
		f.setPos(from.x, from.y - 0.45, from.z);
		f.setYRot((float) (Math.atan2(-f.dir.x, f.dir.z) * 180.0 / Math.PI));
		f.setXRot((float) (-Math.asin(f.dir.y) * 180.0 / Math.PI));
		owner.level().addFreshEntity(f);
		return f;
	}

	public boolean returning() {
		return entityData.get(RETURNING);
	}

	public UUID ownerId() {
		return entityData.get(OWNER).orElse(null);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(OWNER, Optional.empty());
		builder.define(RETURNING, false);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			level().addParticle(ParticleTypes.FLAME, getX(), getY() + 0.3, getZ(), 0, 0, 0);
			level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.3, getZ(), 0, 0.02, 0);
			return;
		}
		ServerLevel level = (ServerLevel) level();
		ServerPlayer owner = ownerId() == null ? null : level.getServer().getPlayerList().getPlayer(ownerId());
		if (owner == null || owner.level() != level || tickCount > totalTicks + 20) {
			discard();
			return;
		}
		int outTicks = totalTicks / 2;
		if (!returning() && (struck || tickCount >= outTicks)) {
			entityData.set(RETURNING, true);
		}
		Vec3 pos = position();
		Vec3 next;
		if (!returning()) {
			next = pos.add(dir.scale(SPEED));
			var block = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
			if (block.getType() != HitResult.Type.MISS) {
				next = block.getLocation();
				impact(owner, next);
			}
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().expandTowards(next.subtract(pos)).inflate(0.4),
					e -> e != owner && e.isAlive() && HeroTargets.canHarm(owner, e))) {
				impact(owner, e.position().add(0, e.getBbHeight() * 0.5, 0));
				break;
			}
		} else {
			// home on the owner's right shoulder so it arrives on the clip's return tick
			Vec3 home = owner.getEyePosition().add(0, -0.6 * owner.getScale(), 0)
					.add(rightOf(owner).scale(0.9 * owner.getScale()));
			int left = Math.max(1, totalTicks - tickCount);
			Vec3 to = home.subtract(pos);
			if (to.length() < 1.0 || tickCount >= totalTicks) {
				discard();
				return;
			}
			next = pos.add(to.scale(1.0 / left));
		}
		setPos(next.x, next.y, next.z);
		Vec3 motion = next.subtract(pos);
		if (motion.lengthSqr() > 1.0e-4) {
			Vec3 face = returning() ? motion.scale(-1) : motion;
			face = face.normalize();
			setYRot((float) (Math.atan2(-face.x, face.z) * 180.0 / Math.PI));
			setXRot((float) (-Math.asin(Math.max(-1, Math.min(1, face.y))) * 180.0 / Math.PI));
		}
		level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.3, getZ(), 2, 0.1, 0.1, 0.1, 0.01);
	}

	private static Vec3 rightOf(ServerPlayer p) {
		double yaw = Math.toRadians(p.getYRot());
		return new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
	}

	private void impact(ServerPlayer owner, Vec3 at) {
		if (struck) {
			return;
		}
		struck = true;
		ServerLevel level = (ServerLevel) level();
		boolean first = true;
		for (LivingEntity e : AbilityHelpers.enemiesAround(owner, at, 2.5)) {
			if (e == owner || !hit.add(e.getId())) {
				continue;
			}
			DamageSource src = level.damageSources().playerAttack(owner);
			AbilityHelpers.hurtBurst(owner, e, src, first ? damage : damage / 3f);
			first = false;
			AbilityHelpers.knockbackFrom(e, position().subtract(dir), 2.4);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 20, 0.5, 0.5, 0.5, 0.3);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0f, 0.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7f, 1.1f);
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 128 * 128;
	}
}
