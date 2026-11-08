package com.projecthero.mod.andy.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import com.projecthero.mod.andy.Andy;
import com.projecthero.mod.andy.AndyAbilities;
import com.projecthero.mod.andy.AndyConfig;
import com.projecthero.mod.andy.data.AndyState;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: a piece of Andy flying as a weapon -- a fingertip (Parts Bullet and its kin), his twisted arm (Ten Twists: Vortex
 * Bullet, a spinning drill) or his own head (Parts Bullet Head). Drawn with the owner's own skin. A finger is spent on the
 * first hit; the arm drills through up to {@link AndyConfig#VORTEX_PIERCE} and sticks in the wall it reaches; the head knocks
 * into its target, drops and lies there. A launched arm or head lasts until the part regrows on his body (then it bursts into
 * pixels) and never more than {@link AndyConfig#SEVERED_LIFE}. Never saved.
 */
public class AndyPartEntity extends Projectile {
	public static final int STYLE_FINGER = 0;
	public static final int STYLE_CRIMSON = 1;
	public static final int STYLE_VULCAN = 2;
	public static final int STYLE_DIVISION = 3;
	public static final int STYLE_DRILL = 4;
	public static final int STYLE_HEAD = 5;

	/** Set by the client: bursts the piece into pixels as it disappears. */
	public static Consumer<AndyPartEntity> clientRemoval = e -> {
	};

	private static final EntityDataAccessor<Integer> STYLE = SynchedEntityData.defineId(AndyPartEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> PART = SynchedEntityData.defineId(AndyPartEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> STUCK = SynchedEntityData.defineId(AndyPartEntity.class, EntityDataSerializers.BOOLEAN);

	private final Set<Integer> struck = new HashSet<>();
	private int life;
	private float damage;
	private boolean falling;

	public AndyPartEntity(EntityType<? extends AndyPartEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	public static AndyPartEntity shoot(ServerPlayer owner, int style, int part, Vec3 from, Vec3 velocity, float damage) {
		AndyPartEntity e = new AndyPartEntity(AndyEntityTypes.PART, owner.level());
		e.setOwner(owner);
		e.entityData.set(STYLE, style);
		e.entityData.set(PART, part);
		e.damage = damage;
		e.moveTo(from.x, from.y, from.z, 0f, 0f);
		e.setDeltaMovement(velocity);
		e.faceVelocity(velocity);
		owner.level().addFreshEntity(e);
		return e;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(STYLE, STYLE_FINGER);
		builder.define(PART, -1);
		builder.define(STUCK, false);
	}

	public int style() {
		return this.entityData.get(STYLE);
	}

	/** The body part this is ({@link AndyState#HEAD}, an arm), or -1 for a finger. */
	public int bodyPart() {
		return this.entityData.get(PART);
	}

	public boolean stuck() {
		return this.entityData.get(STUCK);
	}

	public boolean ownerIs(Player p) {
		Entity o = this.getOwner();
		return o != null && o.getUUID().equals(p.getUUID());
	}

	public boolean isFinger() {
		return this.style() <= STYLE_DIVISION;
	}

	private void faceVelocity(Vec3 v) {
		if (v.lengthSqr() < 1.0e-6) {
			return;
		}
		double flat = Math.sqrt(v.x * v.x + v.z * v.z);
		this.setYRot((float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG));
		this.setXRot((float) (Mth.atan2(v.y, flat) * Mth.RAD_TO_DEG));
		this.yRotO = this.getYRot();
		this.xRotO = this.getXRot();
	}

	private int maxLife() {
		return switch (this.style()) {
			case STYLE_DRILL, STYLE_HEAD -> AndyConfig.SEVERED_LIFE;
			case STYLE_CRIMSON -> 60;
			default -> 40;
		};
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide) {
			Vec3 v = this.getDeltaMovement();
			if (!this.stuck()) {
				this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
			}
			return;
		}
		if (++this.life > this.maxLife() || !(this.getOwner() instanceof ServerPlayer owner) || owner.level() != this.level()) {
			this.discard();
			return;
		}
		if (this.bodyPart() >= 0 && !Andy.missing(owner, this.bodyPart())) {
			this.discard(); // regrown (or the power ended)
			return;
		}
		if (this.stuck()) {
			return;
		}
		Vec3 v = this.getDeltaMovement();
		if (this.falling) {
			// the head after its hit: a dropped ball that rolls to a stop
			this.move(MoverType.SELF, v);
			v = this.getDeltaMovement();
			if (this.onGround()) {
				v = new Vec3(v.x * 0.6, Math.abs(v.y) > 0.12 ? -v.y * 0.35 : 0, v.z * 0.6);
			} else {
				v = new Vec3(v.x * 0.97, v.y - 0.045, v.z * 0.97);
			}
			this.setDeltaMovement(v);
			this.hurtMarked = true;
			return;
		}
		HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
		if (hit.getType() != HitResult.Type.MISS) {
			this.onHit(hit);
			if (this.isRemoved() || this.stuck() || this.falling) {
				return;
			}
			v = this.getDeltaMovement();
		}
		if (this.style() == STYLE_DRILL && this.level() instanceof ServerLevel level && this.life % 2 == 0) {
			level.sendParticles(Andy.BLOOD, this.getX(), this.getY(), this.getZ(), 2, 0.12, 0.12, 0.12, 0.0);
		} else if (this.style() == STYLE_CRIMSON && this.level() instanceof ServerLevel level) {
			level.sendParticles(Andy.BLOOD_BIG, this.getX(), this.getY(), this.getZ(), 2, 0.05, 0.05, 0.05, 0.0);
		}
		this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
		this.faceVelocity(v);
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		if (!super.canHitEntity(e) || !(e instanceof LivingEntity le) || this.struck.contains(e.getId())) {
			return false;
		}
		return !(this.getOwner() instanceof ServerPlayer p) || com.projecthero.mod.combat.HeroTargets.canHarm(p, le);
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (!(this.getOwner() instanceof ServerPlayer p) || !(result.getEntity() instanceof LivingEntity le)) {
			return;
		}
		this.struck.add(le.getId());
		Vec3 v = this.getDeltaMovement();
		switch (this.style()) {
			case STYLE_DRILL -> {
				AndyAbilities.strike(p, le, this.position().subtract(v), this.damage, 0.6, 0.15);
				this.level().playSound(null, le.getX(), le.getY(), le.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0f, 0.6f);
				if (this.struck.size() >= AndyConfig.VORTEX_PIERCE) {
					this.setDeltaMovement(v.scale(0.8));
				}
			}
			case STYLE_HEAD -> {
				AndyAbilities.strike(p, le, this.position().subtract(v), this.damage, 1.4, 0.3);
				com.projecthero.mod.andy.AndySfx.headRoll(this.level(), le.position().add(0, le.getBbHeight() * 0.6, 0), 1.0f); // v0.17.3 Andy sounds
				this.falling = true;
				this.setNoGravity(false);
				this.setDeltaMovement(v.scale(-0.25).add(0, 0.25, 0));
			}
			case STYLE_CRIMSON -> {
				AndyAbilities.strike(p, le, this.position().subtract(v), this.damage, 1.6, 0.25);
				com.projecthero.mod.andy.AndySfx.crimsonImpact(this.level(), le.position().add(0, le.getBbHeight() * 0.5, 0)); // v0.17.3 Andy sounds
			}
			case STYLE_DIVISION -> {
				AndyAbilities.strike(p, le, this.position().subtract(v), this.damage, 0.3, 0.0);
				AndyAbilities.divisionClone(p, this.position());
				this.discard();
			}
			default -> {
				AndyAbilities.strike(p, le, this.position().subtract(v), this.damage, 0.25, 0.0);
				com.projecthero.mod.andy.AndySfx.splat(this.level(), this.position()); // v0.17.3 Andy sounds
				this.discard();
			}
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		Vec3 at = result.getLocation();
		switch (this.style()) {
			case STYLE_DRILL -> {
				Vec3 v = this.getDeltaMovement().normalize();
				this.setPos(at.x - v.x * 0.25, at.y - v.y * 0.25, at.z - v.z * 0.25);
				this.setDeltaMovement(Vec3.ZERO);
				this.entityData.set(STUCK, true);
				this.level().playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 1.0f, 0.8f);
			}
			case STYLE_HEAD -> {
				this.falling = true;
				this.setNoGravity(false);
				Vec3 v = this.getDeltaMovement();
				this.setDeltaMovement(v.scale(-0.2).add(0, 0.15, 0));
				com.projecthero.mod.andy.AndySfx.headRoll(this.level(), at, (float) Math.min(1.0, v.length())); // v0.17.3 Andy sounds
			}
			case STYLE_DIVISION -> {
				if (this.getOwner() instanceof ServerPlayer p) {
					AndyAbilities.divisionClone(p, at.add(0, 0.1, 0));
				}
				this.discard();
			}
			default -> this.discard();
		}
	}

	@Override
	public void onClientRemoval() {
		clientRemoval.accept(this);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 128 * 128;
	}

	@Override
	public boolean isPickable() {
		return false;
	}
}
