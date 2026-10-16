package com.projecthero.mod.hero.power.p09;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * v0.16.5 Cryokinesis: the visible half of {@link FrostStacks} -- one per frosted creature, never saved. It follows its
 * target and syncs the Frost count (drawn as a ring of ice pips over the head) and the freeze (drawn as a block of ice
 * around the target's whole body that forms, holds and cracks; thicker and darker for Ice Prison). The renderer draws
 * everything at the target's own interpolated position, so the shell never lags behind.
 */
public class FrostShellEntity extends Entity {
	private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(FrostShellEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> STACKS = SynchedEntityData.defineId(FrostShellEntity.class, EntityDataSerializers.INT);
	/** Game time the freeze began (0 = not frozen). */
	private static final EntityDataAccessor<Integer> FREEZE_START = SynchedEntityData.defineId(FrostShellEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> FREEZE_LEN = SynchedEntityData.defineId(FrostShellEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> PRISON = SynchedEntityData.defineId(FrostShellEntity.class, EntityDataSerializers.BOOLEAN);

	public FrostShellEntity(EntityType<? extends FrostShellEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	static FrostShellEntity spawn(ServerLevel level, LivingEntity target) {
		FrostShellEntity e = new FrostShellEntity(CryoEntities.FROST_SHELL, level);
		e.entityData.set(TARGET, target.getId());
		e.moveTo(target.getX(), target.getY(), target.getZ());
		return level.addFreshEntity(e) ? e : null;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(TARGET, -1);
		builder.define(STACKS, 0);
		builder.define(FREEZE_START, 0);
		builder.define(FREEZE_LEN, 0);
		builder.define(PRISON, false);
	}

	void setStacks(int n) {
		if (entityData.get(STACKS) != n) {
			entityData.set(STACKS, n);
		}
	}

	void startFreeze(long now, int ticks, boolean prison) {
		entityData.set(FREEZE_START, (int) (now & 0x3FFFFFFF) | 1);
		entityData.set(FREEZE_LEN, ticks);
		entityData.set(PRISON, prison);
	}

	void endFreeze() {
		if (entityData.get(FREEZE_START) != 0) {
			entityData.set(FREEZE_START, 0);
			entityData.set(PRISON, false);
		}
	}

	public int targetId() {
		return entityData.get(TARGET);
	}

	public int stacks() {
		return entityData.get(STACKS);
	}

	public boolean prison() {
		return entityData.get(PRISON);
	}

	/** Ticks since the freeze began (fractional with {@code partial}), or -1 when not frozen. */
	public float freezeAge(float partial) {
		int start = entityData.get(FREEZE_START);
		if (start == 0) {
			return -1.0f;
		}
		long now = level().getGameTime() & 0x3FFFFFFF;
		return Math.max(0.0f, (float) Math.floorMod(now - start, 0x40000000L) + partial);
	}

	public int freezeLength() {
		return entityData.get(FREEZE_LEN);
	}

	@Override
	public void tick() {
		super.tick();
		Entity t = level().getEntity(targetId());
		if (!level().isClientSide) {
			if (!(t instanceof LivingEntity le) || !le.isAlive() || tickCount > 20 * 60 * 5) {
				discard();
				return;
			}
		}
		if (t != null) {
			setPos(t.getX(), t.getY(), t.getZ());
		}
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isAttackable() {
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
}
