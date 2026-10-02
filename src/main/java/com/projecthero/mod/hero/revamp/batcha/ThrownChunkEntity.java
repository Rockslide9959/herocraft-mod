package com.projecthero.mod.hero.revamp.batcha;

import java.util.UUID;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.22 (Super Strength): a chunk of the world held or hurled by a strongman -- a single block ripped out with
 * Grab &amp; Throw (V), or the boulder torn up by Rip &amp; Hurl (N). The block it is made of and its size are synced
 * so the client draws the real block, scaled up (see {@code ThrownChunkRenderer}).
 *
 * <p>While held it floats wherever its owner's handler parks it each tick; if nobody refreshes it for a second it
 * crumbles. Once {@link #launch launched} it flies under gravity, tumbling, and shatters on the first creature,
 * wall or floor it meets: {@code damage} to a creature it strikes directly, {@code splashDamage} to everything
 * within {@code splashRadius}. Never saved.
 */
public class ThrownChunkEntity extends Entity {
	private static final EntityDataAccessor<BlockState> BLOCK = SynchedEntityData.defineId(ThrownChunkEntity.class,
			EntityDataSerializers.BLOCK_STATE);
	private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(ThrownChunkEntity.class,
			EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(ThrownChunkEntity.class,
			EntityDataSerializers.BOOLEAN);

	private UUID owner;
	private float damage = 10f;
	private float splashDamage = 0f;
	private double splashRadius = 0.0;
	private int flightTicks;
	/** Game-time tick the holder last parked it (held chunks with a stale holder crumble). */
	private long lastHeld;

	public ThrownChunkEntity(EntityType<? extends ThrownChunkEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public static ThrownChunkEntity create(ServerLevel level, ServerPlayer owner, BlockState state, float scale) {
		ThrownChunkEntity e = new ThrownChunkEntity(BatchAEntities.THROWN_CHUNK, level);
		e.owner = owner.getUUID();
		e.entityData.set(BLOCK, state);
		e.entityData.set(SCALE, scale);
		e.lastHeld = level.getGameTime();
		return e;
	}

	public BlockState blockState() {
		return this.entityData.get(BLOCK);
	}

	public float scale() {
		return this.entityData.get(SCALE);
	}

	public boolean flying() {
		return this.entityData.get(FLYING);
	}

	public UUID ownerId() {
		return owner;
	}

	/** Parks the (held) chunk at {@code pos}; keeps it alive for another second. */
	public void hold(Vec3 pos) {
		this.setPos(pos.x, pos.y, pos.z);
		this.setDeltaMovement(Vec3.ZERO);
		this.lastHeld = this.level().getGameTime();
	}

	public void launch(Vec3 velocity, float damage, float splashDamage, double splashRadius) {
		this.damage = damage;
		this.splashDamage = splashDamage;
		this.splashRadius = splashRadius;
		this.entityData.set(FLYING, true);
		this.noPhysics = false;
		this.setDeltaMovement(velocity);
		this.hasImpulse = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(BLOCK, Blocks.STONE.defaultBlockState());
		builder.define(SCALE, 1.0f);
		builder.define(FLYING, false);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			return;
		}
		ServerLevel level = (ServerLevel) this.level();
		ServerPlayer thrower = owner != null && level.getPlayerByUUID(owner) instanceof ServerPlayer sp ? sp : null;
		if (!flying()) {
			if (thrower == null || level.getGameTime() - lastHeld > 20 || tickCount > 20 * 30) {
				crumble(level);
			}
			return;
		}
		flightTicks++;
		Vec3 v = this.getDeltaMovement().add(0, -0.045, 0);
		this.setDeltaMovement(v);
		this.move(MoverType.SELF, v);
		if (flightTicks % 2 == 0) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, blockState()), getX(), getY() + 0.3, getZ(),
					2, 0.2, 0.2, 0.2, 0.02);
		}
		double r = 0.35 + scale() * 0.25;
		LivingEntity struck = null;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r),
				e -> com.projecthero.mod.combat.HeroTargets.canHarm(thrower, e))) { // v0.14.20: rule 1
			struck = e;
			break;
		}
		if (struck != null || horizontalCollision || verticalCollision || onGround() || isInWater() || flightTicks > 160) {
			shatter(level, thrower, struck);
		}
	}

	private void shatter(ServerLevel level, ServerPlayer thrower, LivingEntity struck) {
		Vec3 at = position().add(0, 0.4 * scale(), 0);
		if (thrower != null) {
			if (struck != null) {
				AbilityHelpers.hurtBurst(thrower, struck, damage);
				AbilityHelpers.knockbackFrom(struck, position().subtract(getDeltaMovement()), 1.4);
			}
			if (splashRadius > 0 && splashDamage > 0) {
				for (LivingEntity e : AbilityHelpers.enemiesAround(thrower, at, splashRadius)) {
					if (e == struck) {
						continue;
					}
					AbilityHelpers.hurt(thrower, e, splashDamage);
					AbilityHelpers.knockbackFrom(e, at, 1.0);
					AbilityHelpers.push(e, new Vec3(0, 0.35, 0));
				}
			}
		}
		BlockState state = blockState();
		int n = (int) (25 * scale());
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, n, 0.4 * scale(),
				0.4 * scale(), 0.4 * scale(), 0.25);
		level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.05);
		if (scale() > 1.2f) {
			level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		}
		level.playSound(null, at.x, at.y, at.z, state.getSoundType().getBreakSound(), SoundSource.PLAYERS, 1.6f, 0.7f);
		level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
				0.35f + 0.25f * scale(), 1.3f);
		discard();
	}

	/** Dropped without a throw: it just falls apart. */
	public void crumble(ServerLevel level) {
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, blockState()), getX(), getY() + 0.3, getZ(),
				20, 0.4, 0.4, 0.4, 0.1);
		level.playSound(null, getX(), getY(), getZ(), blockState().getSoundType().getBreakSound(), SoundSource.PLAYERS, 1.0f, 0.9f);
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
