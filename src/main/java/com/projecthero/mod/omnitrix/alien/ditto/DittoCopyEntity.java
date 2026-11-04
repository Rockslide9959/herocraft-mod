package com.projecthero.mod.omnitrix.alien.ditto;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.omnitrix.Omnitrix;

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
 * v0.17.2: one of Ditto's copies -- a small, plain (non-living) entity in Ditto's body (the client draws it in his skin, at his size,
 * badge and all: {@code DittoCopyRenderer}). It runs on its own: it falls, climbs whatever it runs into, keeps a loose pack round
 * its owner, and swarms -- it chases its target (the kit's order, or whatever Ditto last hit or was hit by) and punches it once a
 * second. It is fragile ({@link #MAX_HP}) and, as in the show, what it feels Ditto feels: half the damage it takes reaches him
 * ({@link DittoKit#feedback}). Never saved; it pops the moment its owner is not a transformed Ditto any more.
 */
public class DittoCopyEntity extends Entity {
	private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(DittoCopyEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> SLOT = SynchedEntityData.defineId(DittoCopyEntity.class, EntityDataSerializers.INT);
	/** Copy Carry: holding something up (synced -- drawn with its arms raised). */
	private static final EntityDataAccessor<Boolean> HOLDING = SynchedEntityData.defineId(DittoCopyEntity.class, EntityDataSerializers.BOOLEAN);
	/** Client: the last swing's start (entity event 4). */
	public long swingAt = -100;
	/** Client: the last hit's time (entity event 2), for the red flash. */
	public long hurtAt = -100;

	public static final float MAX_HP = 6f;
	/** Ticks between two punches of one copy. */
	public static final int PUNCH_EVERY = 20;
	public static final double RUN = 0.34, WALK = 0.24;

	private float hp = MAX_HP;
	@Nullable
	private LivingEntity target;
	private long targetUntil;
	@Nullable
	private Vec3 goal;
	private int goalTicks;
	private int punchCooldown;
	/** Being carried along (Copy Carry): it stays where the kit puts it. */
	private boolean holding;

	public DittoCopyEntity(EntityType<? extends DittoCopyEntity> type, Level level) {
		super(type, level);
	}

	public static DittoCopyEntity spawn(Player owner, int slot, Vec3 at) {
		DittoCopyEntity e = new DittoCopyEntity(DittoKit.COPY, owner.level());
		e.entityData.set(OWNER, owner.getId());
		e.entityData.set(SLOT, slot);
		e.moveTo(at.x, at.y, at.z, owner.getYRot(), 0f);
		e.punchCooldown = slot * 3; // staggered, so a pack's punches land one after another
		owner.level().addFreshEntity(e);
		return e;
	}

	public int ownerId() {
		return entityData.get(OWNER);
	}

	/** Copy Carry: holding something up over its head (synced). */
	public boolean holding() {
		return entityData.get(HOLDING);
	}

	public void setHolding(boolean on) {
		holding = on;
		entityData.set(HOLDING, on);
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

	/** Swarm {@code e} for {@code ticks}. */
	public void attack(LivingEntity e, int ticks) {
		target = e;
		targetUntil = level().getGameTime() + ticks;
		goal = null;
	}

	/** Runs (or leaps) to {@code at} for {@code ticks}, ignoring targets meanwhile. */
	public void dashTo(Vec3 at, int ticks) {
		goal = at;
		goalTicks = ticks;
	}

	/** Its place in the loose pack round its owner: a ring behind and beside him. */
	public Vec3 packPos(Player owner) {
		float yaw = owner.getYRot() * Mth.DEG_TO_RAD + (slot() % 6) * (Mth.TWO_PI / 6f) + Mth.PI * 0.5f;
		double r = 1.1 + (slot() / 6) * 0.8;
		return owner.position().add(-Mth.sin(yaw) * r, 0, Mth.cos(yaw) * r);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(OWNER, -1);
		builder.define(SLOT, 0);
		builder.define(HOLDING, false);
	}

	@Override
	public float maxUpStep() {
		return 0.6f;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			return;
		}
		Player owner = owner();
		if (!(owner instanceof ServerPlayer sp) || !owner.isAlive() || !DittoKit.isDitto(owner) || owner.level() != level()) {
			pop();
			return;
		}
		if (distanceToSqr(owner) > 28 * 28) {
			Vec3 at = packPos(owner);
			setPos(at.x, owner.getY(), at.z); // left far behind: it catches up (a quick Splixson)
			setDeltaMovement(Vec3.ZERO);
		}
		if (holding) {
			return; // Copy Carry places it
		}
		long now = level().getGameTime();
		if (punchCooldown > 0) {
			punchCooldown--;
		}
		// whom to swarm: the kit's order, else whatever Ditto just fought
		if (target != null && (!target.isAlive() || target.isRemoved() || now > targetUntil || target.distanceToSqr(owner) > 24 * 24)) {
			target = null;
		}
		LivingEntity fight = target != null ? target : DittoKit.foe(sp);
		Vec3 to;
		double speed;
		if (goal != null && goalTicks > 0) {
			goalTicks--;
			to = goal;
			speed = RUN;
		} else {
			goal = null;
			if (fight != null) {
				to = fight.position();
				speed = RUN;
			} else {
				to = packPos(owner);
				speed = distanceToSqr(to) > 9 ? RUN : WALK;
			}
		}
		Vec3 d = new Vec3(to.x - getX(), 0, to.z - getZ());
		double dist = d.length();
		Vec3 v = getDeltaMovement();
		double vx = 0, vz = 0;
		double stop = fight != null && goal == null ? Math.max(0.5, fight.getBbWidth() * 0.5 + 0.35) : 0.25;
		if (dist > stop) {
			Vec3 n = d.scale(1.0 / dist);
			double s = Math.min(speed, dist - stop + 0.05);
			vx = n.x * s;
			vz = n.z * s;
		}
		double vy = v.y;
		if (horizontalCollision && dist > stop) {
			vy = 0.22; // a great climber: up whatever it runs into
		} else if (!onGround()) {
			vy = (vy - 0.08) * 0.98;
		} else if (vy < 0) {
			vy = 0;
		}
		if (goal != null && onGround() && to.y > getY() + 0.8) {
			vy = 0.5; // a hop up to the goal
		}
		setDeltaMovement(vx, vy, vz);
		move(MoverType.SELF, getDeltaMovement());
		fallDistance = 0f;
		// face where it runs, or its foe, or where its owner looks
		Vec3 face = fight != null && goal == null ? fight.position().subtract(position()) : dist > stop + 0.15 ? d : Vec3.ZERO; // settled: face where he faces
		float yaw = face.horizontalDistanceSqr() > 0.01 ? (float) (Mth.atan2(-face.x, face.z) * Mth.RAD_TO_DEG) : owner.getYRot();
		setYRot(Mth.rotLerp(0.5f, getYRot(), yaw));
		setXRot(0f);
		// a punch when in reach
		if (fight != null && goal == null && punchCooldown <= 0 && reach(fight)) {
			punchCooldown = PUNCH_EVERY;
			level().broadcastEntityEvent(this, (byte) 4);
			fight.invulnerableTime = 0; // a pack hits as a pack
			DittoKit.copyHit(sp, this, fight);
		}
	}

	/** Client: ticks of the red hit flash left. */
	public int hurtTime() {
		return (int) Math.max(0, 8 - (level().getGameTime() - hurtAt));
	}

	/** Its fists reach {@code e} (a small alien's reach, from its edge to the creature's). */
	public boolean reach(LivingEntity e) {
		return getBoundingBox().inflate(0.55, 0.3, 0.55).intersects(e.getBoundingBox());
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == 4) {
			swingAt = level().getGameTime();
			return;
		}
		if (id == 2) {
			hurtAt = level().getGameTime();
			return;
		}
		super.handleEntityEvent(id);
	}

	/** Gone in a puff (its owner turned back, merged it, or it broke). */
	public void pop() {
		if (level() instanceof ServerLevel l && !isRemoved()) {
			l.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.4, getZ(), 5, 0.12, 0.2, 0.12, 0.01);
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
		Entity by = source.getEntity();
		Player owner = owner();
		if (by != null && (by.getId() == ownerId() || by instanceof DittoCopyEntity)) {
			return false; // never his own fists, nor a brother's
		}
		if (owner != null && by != null && com.projecthero.mod.squad.Squads.areAllies(owner, by) && !com.projecthero.mod.squad.Squads.friendlyFireOn(owner)) {
			return false;
		}
		float taken = Math.min(amount, hp);
		level().broadcastEntityEvent(this, (byte) 2);
		hp -= amount;
		if (owner instanceof ServerPlayer sp) {
			DittoKit.feedback(sp, taken);
		}
		if (by instanceof LivingEntity le && le.isAlive() && le != owner) {
			attack(le, 100); // hit back
		}
		if (hp <= 0f) {
			pop();
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
