package com.projecthero.mod.hulk.entity;

import java.util.UUID;

import com.projecthero.mod.hulk.HulkCombat;
import com.projecthero.mod.hulk.HulkConfig;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.13.14: the chunk of earth the Hulk tears out of the ground (Shift+V). While held it is parked over his head by
 * {@code HulkGrab}; once thrown ({@link #launch}) it flies under gravity, tumbling, and explodes on the first thing it
 * hits -- ground, wall or mob: {@code boulderDamage} to everything within {@code boulderRadius} and a small crater.
 * Never saved (a boulder in flight at a save simply is not there after).
 */
public class HulkBoulderEntity extends Entity implements GeoEntity {
	private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(HulkBoulderEntity.class, EntityDataSerializers.BOOLEAN);
	private static final RawAnimation TUMBLE = RawAnimation.begin().thenLoop("animation.hulk_boulder.tumble");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private UUID owner;
	private int flightTicks;

	public HulkBoulderEntity(EntityType<? extends HulkBoulderEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public void setOwner(ServerPlayer player) {
		this.owner = player.getUUID();
	}

	public boolean flying() {
		return this.entityData.get(FLYING);
	}

	/** Thrown: from now on it flies under gravity and explodes on impact. */
	public void launch(Vec3 velocity) {
		this.entityData.set(FLYING, true);
		this.noPhysics = false;
		this.setDeltaMovement(velocity);
		this.hasImpulse = true;
	}

	/** Dropped without a throw (he changed back, died, logged out): it just falls apart. */
	public void crumble() {
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), getX(), getY() + 0.6, getZ(),
					30, 0.5, 0.5, 0.5, 0.1);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
		}
		discard();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(FLYING, false);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			return;
		}
		ServerLevel level = (ServerLevel) this.level();
		ServerPlayer thrower = owner == null ? null : (ServerPlayer) level.getPlayerByUUID(owner);
		if (!flying()) {
			// held: HulkGrab moves it every tick; if nobody is holding it any more, it falls apart
			if (thrower == null || tickCount > 20 * 60 || !com.projecthero.mod.hulk.HulkGrab.isHeld(this)) {
				if (tickCount > 2) {
					crumble();
				}
			}
			return;
		}
		flightTicks++;
		Vec3 v = this.getDeltaMovement().add(0, -0.05, 0);
		this.setDeltaMovement(v);
		this.move(MoverType.SELF, v);
		if (flightTicks % 2 == 0) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), getX(), getY() + 0.5, getZ(),
					3, 0.3, 0.3, 0.3, 0.02);
		}
		boolean hitMob = false;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.3),
				e -> com.projecthero.mod.combat.HeroTargets.canHarm(thrower, e))) { // v0.14.20: rule 1
			hitMob = true;
			break;
		}
		if (hitMob || horizontalCollision || verticalCollision || onGround() || isInWater() || flightTicks > 200) {
			explode(level, thrower);
		}
	}

	private void explode(ServerLevel level, ServerPlayer thrower) {
		Vec3 at = position().add(0, 0.5, 0);
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		if (thrower != null) {
			HulkCombat.radial(thrower, at, cfg.boulderRadius, new HulkCombat.Hit(cfg.boulderDamage, 1.8, 0.6), true);
			HulkCombat.crater(thrower, at.add(0, -0.8, 0), 2.2, 30);
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), at.x, at.y, at.z,
				60, 1.2, 0.6, 1.2, 0.3);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), at.x, at.y, at.z,
				30, 1.0, 0.6, 1.0, 0.3);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0f, 0.8f);
		HulkCombat.shake(level, at, 0.6f, 12, 24.0);
		discard();
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
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
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "tumble", 0, state -> flying() ? state.setAndContinue(TUMBLE) : PlayState.STOP));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
