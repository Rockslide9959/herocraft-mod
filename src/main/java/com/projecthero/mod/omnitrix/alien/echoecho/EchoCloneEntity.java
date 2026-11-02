package com.projecthero.mod.omnitrix.alien.echoecho;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.omnitrix.Omnitrix;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: one of Echo Echo's duplicates -- a small, plain (non-living) entity that keeps formation behind its owner, faces where he
 * faces and copies his moves' poses (the client renders it with Echo Echo's body, {@code EchoCloneRenderer}). The kit makes its
 * screams; it only follows, dashes where the kit sends it and breaks after {@link #MAX_HP} damage. Never saved; it disappears the
 * moment its owner is not a transformed Echo Echo any more.
 */
public class EchoCloneEntity extends Entity {
	private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(EchoCloneEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> SLOT = SynchedEntityData.defineId(EchoCloneEntity.class, EntityDataSerializers.INT);

	public static final float MAX_HP = 6f;

	private float hp = MAX_HP;
	@Nullable
	private Vec3 goal;
	private int goalTicks;

	public EchoCloneEntity(EntityType<? extends EchoCloneEntity> type, Level level) {
		super(type, level);
	}

	public static EchoCloneEntity spawn(Player owner, int slot) {
		EchoCloneEntity e = new EchoCloneEntity(EchoEchoKit.CLONE, owner.level());
		e.entityData.set(OWNER, owner.getId());
		e.entityData.set(SLOT, slot);
		Vec3 at = e.slotPos(owner);
		e.moveTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), 0f);
		e.setPos(at.x, owner.getY(), at.z);
		owner.level().addFreshEntity(e);
		return e;
	}

	public int ownerId() {
		return entityData.get(OWNER);
	}

	public int slot() {
		return entityData.get(SLOT);
	}

	@Nullable
	public Player owner() {
		return level().getEntity(ownerId()) instanceof Player p ? p : null;
	}

	/** Where this clone stands in formation: a fan behind and beside its owner. */
	public Vec3 slotPos(Player owner) {
		float yaw = owner.getYRot() * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		double side = switch (slot()) {
			case 0 -> -1.3;
			case 1 -> 1.3;
			default -> 0.0;
		};
		double back = slot() >= 2 ? 1.6 : 0.8;
		return owner.position().add(right.scale(side)).subtract(fwd.scale(back));
	}

	/** Sends the clone running to {@code at} for {@code ticks} (Echo Assault). */
	public void dashTo(Vec3 at, int ticks) {
		goal = at;
		goalTicks = ticks;
	}

	/** A sound wave's mouth: the clone's eye. */
	public Vec3 mouth() {
		return position().add(0, getBbHeight() * 0.85, 0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(OWNER, -1);
		builder.define(SLOT, 0);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			return;
		}
		Player owner = owner();
		if (owner == null || !owner.isAlive() || !EchoEchoKit.ID.equals(Omnitrix.state(owner).active) || !Omnitrix.state(owner).transformed()
				|| owner.level() != level() || distanceToSqr(owner) > 48 * 48) {
			discard();
			return;
		}
		Vec3 target;
		if (goal != null && goalTicks > 0) {
			goalTicks--;
			target = goal;
		} else {
			goal = null;
			target = slotPos(owner);
		}
		Vec3 cur = position();
		Vec3 d = target.subtract(cur);
		double k = goal != null ? 0.45 : 0.3;
		Vec3 next = cur.add(d.x * k, 0, d.z * k);
		// keep to the owner's height (no pathing for a basic duplicate), easing toward it
		double y = cur.y + (target.y - cur.y) * 0.4;
		setPos(next.x, y, next.z);
		float yaw = goal != null && d.horizontalDistanceSqr() > 0.05 ? (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG) : owner.getYRot();
		setYRot(yaw);
		setXRot(owner.getXRot());
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (level().isClientSide || isRemoved() || isInvulnerableTo(source)) {
			return false;
		}
		Entity by = source.getEntity();
		if (by != null && by.getId() == ownerId()) {
			return false;
		}
		hp -= amount;
		if (hp <= 0f) {
			if (level() instanceof ServerLevel l) {
				l.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.5, getZ(), 8, 0.2, 0.3, 0.2, 0.02);
			}
			discard();
		}
		return true;
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
