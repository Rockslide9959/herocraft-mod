package com.projecthero.mod.omnitrix.alien.buzzshock;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.omnitrix.alien.ditto.DittoKit;

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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: one of Buzzshock's copies (the show: a Nosedeenian splits into more of himself) -- a tiny, plain (non-living) entity in his
 * body, built the way Ditto's copies are ({@code DittoCopyEntity}), but a spark: it FLIES. It hovers in a loose swarm round its owner,
 * darts at whatever he fights (the kit's order, or whatever he last hit or was hit by -- {@link DittoKit#foe}) and shocks it from a
 * few blocks away once a second ({@link BuzzshockKit#copyZap}). Fragile ({@link #MAX_HP}); water shorts it out at once (his weakness).
 * Never saved; it fizzles the moment its owner is not a transformed Buzzshock any more.
 */
public class BuzzshockCopyEntity extends Entity {
	private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(BuzzshockCopyEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> SLOT = SynchedEntityData.defineId(BuzzshockCopyEntity.class, EntityDataSerializers.INT);
	/** Client: the last zap's time (entity event 4), for the arm thrust. */
	public long zapAt = -100;
	/** Client: the last hit's time (entity event 2), for the red flash. */
	public long hurtAt = -100;

	public static final float MAX_HP = 4f;
	/** Ticks between two zaps of one copy. */
	public static final int ZAP_EVERY = 20;
	/** How far its shock reaches. */
	public static final double ZAP_RANGE = 6.0;
	public static final double FAST = 0.5, SLOW = 0.28;

	private float hp = MAX_HP;
	@Nullable
	private LivingEntity target;
	private long targetUntil;
	@Nullable
	private Vec3 goal;
	private int goalTicks;
	private int zapCooldown;

	public BuzzshockCopyEntity(EntityType<? extends BuzzshockCopyEntity> type, Level level) {
		super(type, level);
		noPhysics = false;
	}

	public static BuzzshockCopyEntity spawn(Player owner, int slot, Vec3 at) {
		BuzzshockCopyEntity e = new BuzzshockCopyEntity(BuzzshockKit.COPY, owner.level());
		e.entityData.set(OWNER, owner.getId());
		e.entityData.set(SLOT, slot);
		e.moveTo(at.x, at.y, at.z, owner.getYRot(), 0f);
		e.zapCooldown = 4 + slot * 3; // staggered, so a swarm's shocks crackle one after another
		owner.level().addFreshEntity(e);
		return e;
	}

	public int ownerId() {
		return entityData.get(OWNER);
	}

	public int slot() {
		return entityData.get(SLOT);
	}

	public float hp() {
		return hp;
	}

	@Nullable
	public Player owner() {
		return level().getEntity(ownerId()) instanceof Player p ? p : null;
	}

	@Nullable
	public LivingEntity target() {
		return target;
	}

	/** Shock {@code e} for {@code ticks}. */
	public void attack(LivingEntity e, int ticks) {
		target = e;
		targetUntil = level().getGameTime() + ticks;
		goal = null;
	}

	/** Darts to {@code at} for {@code ticks}, ignoring targets meanwhile. */
	public void dashTo(Vec3 at, int ticks) {
		goal = at;
		goalTicks = ticks;
	}

	/** Each slot's angle off his facing: beside him and ahead, never behind (where the camera is). */
	private static final float[] ARC = { 0.8f, -0.8f, 1.5f, -1.5f, 2.0f, -2.0f };

	/** Its place in the swarm round its owner: an arc beside and ahead of him at shoulder height, drifting and bobbing. */
	public Vec3 swarmPos(Player owner) {
		float a = owner.getYRot() * Mth.DEG_TO_RAD + ARC[slot() % ARC.length] + Mth.sin(tickCount * 0.05f + slot()) * 0.15f;
		double r = 1.0 + (slot() / ARC.length) * 0.6;
		double y = owner.getBbHeight() * 0.8 + 0.25 + Mth.sin(tickCount * 0.15f + slot()) * 0.15;
		return owner.position().add(-Mth.sin(a) * r, y, Mth.cos(a) * r);
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
		if (!(owner instanceof ServerPlayer sp) || !owner.isAlive() || !BuzzshockKit.isBuzzshock(owner) || owner.level() != level()) {
			fizzle();
			return;
		}
		if (isInWater()) {
			fizzle(); // his weakness: water shorts a spark out
			return;
		}
		if (distanceToSqr(owner) > 32 * 32) {
			Vec3 at = swarmPos(owner);
			setPos(at.x, at.y, at.z); // left far behind: it arcs back to him
			setDeltaMovement(Vec3.ZERO);
		}
		long now = level().getGameTime();
		if (zapCooldown > 0) {
			zapCooldown--;
		}
		if (target != null && (!target.isAlive() || target.isRemoved() || now > targetUntil || target.distanceToSqr(owner) > 24 * 24)) {
			target = null;
		}
		LivingEntity fight = target != null ? target : DittoKit.foe(sp);
		Vec3 to;
		double speed;
		if (goal != null && goalTicks > 0) {
			goalTicks--;
			to = goal;
			speed = FAST;
		} else {
			goal = null;
			if (fight != null) {
				// hover a few blocks off it, at its middle, on its own side of the ring
				Vec3 c = fight.position().add(0, fight.getBbHeight() * 0.6, 0);
				float a = slot() * 1.3f + tickCount * 0.05f;
				double r = Math.max(2.2, fight.getBbWidth() * 0.5 + 2.0);
				to = c.add(Mth.cos(a) * r, 0.4, Mth.sin(a) * r);
				speed = FAST;
			} else {
				to = swarmPos(owner);
				speed = distanceToSqr(to) > 9 ? FAST : SLOW;
			}
		}
		Vec3 d = to.subtract(position());
		double dist = d.length();
		Vec3 v = dist > 0.05 ? d.scale(Math.min(speed, dist * 0.5) / dist) : Vec3.ZERO;
		setDeltaMovement(getDeltaMovement().scale(0.4).add(v.scale(0.6)));
		if (horizontalCollision) {
			setDeltaMovement(getDeltaMovement().add(0, 0.12, 0)); // over whatever it bumps
		}
		move(MoverType.SELF, getDeltaMovement());
		fallDistance = 0f;
		Vec3 face = fight != null && goal == null ? fight.position().subtract(position()) : d.horizontalDistanceSqr() > 0.04 ? d : Vec3.ZERO;
		float yaw = face.horizontalDistanceSqr() > 0.01 ? (float) (Mth.atan2(-face.x, face.z) * Mth.RAD_TO_DEG) : owner.getYRot();
		setYRot(Mth.rotLerp(0.5f, getYRot(), yaw));
		setXRot(0f);
		if (fight != null && goal == null && zapCooldown <= 0 && distanceToSqr(fight) < ZAP_RANGE * ZAP_RANGE && hasLineOfSight(fight)) {
			zapCooldown = ZAP_EVERY;
			level().broadcastEntityEvent(this, (byte) 4);
			fight.invulnerableTime = 0; // a swarm hits as a swarm
			BuzzshockKit.copyZap(sp, this, fight);
		}
		if (tickCount % 6 == slot() % 6 && level() instanceof ServerLevel l) {
			l.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight() * 0.5, getZ(), 1, 0.1, 0.15, 0.1, 0.02);
		}
	}

	private boolean hasLineOfSight(Entity e) {
		Vec3 a = position().add(0, getBbHeight() * 0.5, 0);
		Vec3 b = e.position().add(0, e.getBbHeight() * 0.5, 0);
		return level().clip(new net.minecraft.world.level.ClipContext(a, b, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	/** Client: ticks of the red hit flash left. */
	public int hurtTime() {
		return (int) Math.max(0, 8 - (level().getGameTime() - hurtAt));
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == 4) {
			zapAt = level().getGameTime();
			return;
		}
		if (id == 2) {
			hurtAt = level().getGameTime();
			return;
		}
		super.handleEntityEvent(id);
	}

	/** Gone in a fizz of sparks (its owner turned back, merged it, it broke or it got wet). */
	public void fizzle() {
		if (level() instanceof ServerLevel l && !isRemoved()) {
			l.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.3, getZ(), 8, 0.12, 0.2, 0.12, 0.08);
		}
		discard();
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return isAlive();
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (level().isClientSide || isRemoved() || isInvulnerableTo(source) || amount <= 0f) {
			return false;
		}
		if (source.is(net.minecraft.world.damagesource.DamageTypes.LIGHTNING_BOLT)) {
			return false; // living electricity
		}
		Entity by = source.getEntity();
		Player owner = owner();
		if (by != null && (by.getId() == ownerId() || by instanceof BuzzshockCopyEntity)) {
			return false; // never his own shocks, nor a brother's
		}
		if (owner != null && by != null && com.projecthero.mod.squad.Squads.areAllies(owner, by) && !com.projecthero.mod.squad.Squads.friendlyFireOn(owner)) {
			return false;
		}
		level().broadcastEntityEvent(this, (byte) 2);
		hp -= amount;
		if (by instanceof LivingEntity le && le.isAlive() && le != owner) {
			attack(le, 100); // shock back
		}
		if (hp <= 0f) {
			fizzle();
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
