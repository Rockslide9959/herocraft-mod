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

import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.13.14: the chunk of earth the Hulk tears out of the ground (Shift+V). While held it is parked over his head by
 * {@code HulkGrab}; once thrown ({@link #launch}) it flies under gravity, tumbling, and explodes on the first thing it
 * hits -- ground, wall or mob: {@code boulderDamage} to everything within {@code boulderRadius} and a small crater.
 * Never saved (a boulder in flight at a save simply is not there after).
 *
 * <p>v0.16.4 (user): it is made of the ground he tore it from -- the three block states ({@link #block}: the surface, what
 * lay under it, and deeper) are synced and the renderer builds the chunk as a jagged cluster of those real blocks; its
 * dust and its explosion use them too.
 */
public class HulkBoulderEntity extends Entity {
	/** v0.16.4: its fall per tick in flight (no drag) -- the charged V throw solves its arc with this. */
	public static final double GRAVITY = 0.05;
	private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(HulkBoulderEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<BlockState> TOP = SynchedEntityData.defineId(HulkBoulderEntity.class, EntityDataSerializers.BLOCK_STATE);
	private static final EntityDataAccessor<BlockState> MID = SynchedEntityData.defineId(HulkBoulderEntity.class, EntityDataSerializers.BLOCK_STATE);
	private static final EntityDataAccessor<BlockState> DEEP = SynchedEntityData.defineId(HulkBoulderEntity.class, EntityDataSerializers.BLOCK_STATE);

	private UUID owner;
	private int flightTicks;
	/** v0.16.4: the charged throw's planned distance (server only; 0 = not a charged throw). */
	private double plannedBlocks;

	public HulkBoulderEntity(EntityType<? extends HulkBoulderEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public void setOwner(ServerPlayer player) {
		this.owner = player.getUUID();
	}

	/** v0.16.4: the ground it is made of -- top (the surface), middle, deep. */
	public void setBlocks(BlockState top, BlockState mid, BlockState deep) {
		this.entityData.set(TOP, top);
		this.entityData.set(MID, mid);
		this.entityData.set(DEEP, deep);
	}

	/** Layer 0 = the surface block, 1 = under it, 2 = deeper. */
	public BlockState block(int layer) {
		return this.entityData.get(layer <= 0 ? TOP : layer == 1 ? MID : DEEP);
	}

	public boolean flying() {
		return this.entityData.get(FLYING);
	}

	/** Thrown: from now on it flies under gravity and explodes on impact. */
	public void launch(Vec3 velocity) {
		launch(velocity, 0.0);
	}

	/** v0.16.4 (user): {@code blocks} = how far the charged throw meant it to go -- 100+ craters the landing like a thrown mob. */
	public void launch(Vec3 velocity, double blocks) {
		this.plannedBlocks = blocks;
		this.entityData.set(FLYING, true);
		this.noPhysics = false;
		this.setDeltaMovement(velocity);
		this.hasImpulse = true;
	}

	/** Dropped without a throw (he changed back, died, logged out): it just falls apart. */
	public void crumble() {
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block(1)), getX(), getY() + 0.6, getZ(),
					30, 0.5, 0.5, 0.5, 0.1);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
		}
		discard();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(FLYING, false);
		builder.define(TOP, Blocks.GRASS_BLOCK.defaultBlockState());
		builder.define(MID, Blocks.DIRT.defaultBlockState());
		builder.define(DEEP, Blocks.STONE.defaultBlockState());
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
		if (flightTicks % 10 == 1) {
			com.projecthero.mod.hulk.HulkGrab.keepLoaded(level, position(), getDeltaMovement()); // v0.16.4: a long throw keeps its chunks ticking
		}
		Vec3 v = this.getDeltaMovement().add(0, -GRAVITY, 0);
		this.setDeltaMovement(v);
		this.move(MoverType.SELF, v);
		if (flightTicks % 2 == 0) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block(flightTicks % 3)), getX(), getY() + 0.5, getZ(),
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
			// v0.16.4 (user): World Breaker doubles the blast; a throw of 100+ blocks hits harder and craters far bigger
			HulkCombat.Hit hit = new HulkCombat.Hit(cfg.boulderDamage * com.projecthero.mod.hulk.HulkGrab.longThrowDamageScale(plannedBlocks), 1.8, 0.6);
			HulkCombat.radial(thrower, at, cfg.boulderRadius * com.projecthero.mod.hulk.WorldBreaker.aoe(thrower), hit, true);
			HulkCombat.crater(thrower, at.add(0, -0.8, 0), 2.2, 30);
			com.projecthero.mod.hulk.HulkGrab.longThrowImpact(thrower, position(), plannedBlocks, hit);
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block(0)), at.x, at.y, at.z,
				60, 1.2, 0.6, 1.2, 0.3);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block(2)), at.x, at.y, at.z,
				30, 1.0, 0.6, 1.0, 0.3);
		level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, block(1)), at.x, at.y - 0.4, at.z, 30, 1.2, 0.1, 1.2, 0.4);
		com.projecthero.mod.hulk.GroundCracks.star(level, at, cfg.boulderRadius, 7, 100);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0f, 0.8f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 2.0f, 0.6f);
		level.playSound(null, at.x, at.y, at.z, block(1).getSoundType().getBreakSound(), SoundSource.PLAYERS, 2.0f, 0.6f);
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
}
