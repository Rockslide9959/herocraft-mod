package com.projecthero.mod.omnitrix.alien.echoecho;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.CloneSpacing;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: one of Echo Echo's duplicates -- a small, plain (non-living) entity that keeps its own spot in the formation behind its owner
 * ({@link CloneSpacing}; user: "make them each have their own position, they shouldnt stand inside each other"), faces where he
 * faces and copies his moves' poses (the client renders it with Echo Echo's body, {@code EchoCloneRenderer}). The kit makes its
 * screams; it only follows, dashes where the kit sends it and breaks after {@link #MAX_HP} damage. Never saved; it disappears the
 * moment its owner is not a transformed Echo Echo any more.
 */
public class EchoCloneEntity extends Entity {
	private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(EchoCloneEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> SLOT = SynchedEntityData.defineId(EchoCloneEntity.class, EntityDataSerializers.INT);
	/** Merge: rushing back into him (synced -- drawn shrinking as it arrives). */
	private static final EntityDataAccessor<Boolean> HOMING = SynchedEntityData.defineId(EchoCloneEntity.class, EntityDataSerializers.BOOLEAN);

	public static final float MAX_HP = 6f;

	private float hp = MAX_HP;
	@Nullable
	private Vec3 goal;
	private int goalTicks;
	/** Surround Sound: the creature it looks in at, and for how long. */
	@Nullable
	private Entity faces;
	private int faceTicks;

	public EchoCloneEntity(EntityType<? extends EchoCloneEntity> type, Level level) {
		super(type, level);
	}

	public static EchoCloneEntity spawn(Player owner, int slot) {
		EchoCloneEntity e = new EchoCloneEntity(EchoEchoKit.CLONE, owner.level());
		e.entityData.set(OWNER, owner.getId());
		e.entityData.set(SLOT, slot);
		// it slides out of him and on to its spot (the tick's easing does it)
		e.moveTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), 0f);
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

	/** Where this clone stands in formation: its own spot in the fan behind its owner, never inside a wall. */
	public Vec3 slotPos(Player owner) {
		return CloneSpacing.clear(level(), getType(), CloneSpacing.fan(owner, slot()), owner.position());
	}

	/** Sends the clone running to {@code at} for {@code ticks} (Echo Assault). */
	public void dashTo(Vec3 at, int ticks) {
		goal = at;
		goalTicks = ticks;
	}

	/** Surround Sound: looks in at {@code e} for {@code ticks}. */
	public void faceAt(Entity e, int ticks) {
		faces = e;
		faceTicks = ticks;
	}

	/** Merge: it rushes back into him and is gone. */
	public void recall() {
		entityData.set(HOMING, true);
		goal = null;
	}

	public boolean homing() {
		return entityData.get(HOMING);
	}

	/** A sound wave's mouth: the clone's eye. */
	public Vec3 mouth() {
		return position().add(0, getBbHeight() * 0.85, 0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(OWNER, -1);
		builder.define(SLOT, 0);
		builder.define(HOMING, false);
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
		if (homing()) {
			Vec3 to = owner.position().subtract(position());
			double dist = to.length();
			if (dist < 0.35) {
				discard();
				return;
			}
			setPos(position().add(to.scale(Math.min(1.0, Math.max(0.3, 0.3 / dist)))));
			setYRot((float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG));
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
		if (owner instanceof ServerPlayer sp) {
			var brothers = EchoEchoKit.clones(sp);
			// on the way there it steps round its brothers; nearly there with one in the way (a wall has squeezed two spots together) it stops
			boolean give = d.horizontalDistanceSqr() < 0.36 && CloneSpacing.crowded(this, brothers);
			next = (give ? cur : next).add(CloneSpacing.apart(this, brothers));
		}
		// keep to the owner's height (no pathing for a basic duplicate), easing toward it
		double y = cur.y + (target.y - cur.y) * 0.4;
		setPos(next.x, y, next.z);
		float yaw = goal != null && d.horizontalDistanceSqr() > 0.05 ? (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG) : owner.getYRot();
		if (faceTicks > 0) {
			faceTicks--;
			if (faces != null && faces.isAlive() && d.horizontalDistanceSqr() <= 0.05) {
				Vec3 in = faces.position().subtract(cur);
				yaw = (float) (Mth.atan2(-in.x, in.z) * Mth.RAD_TO_DEG);
			}
		} else {
			faces = null;
		}
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
