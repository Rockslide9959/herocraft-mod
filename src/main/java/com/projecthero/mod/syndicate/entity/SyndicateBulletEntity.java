package com.projecthero.mod.syndicate.entity;

import com.projecthero.mod.syndicate.SyndicateEntityTypes;
import com.projecthero.mod.syndicate.SyndicateGunfire;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2: a Syndicate gunman's (or the Kingpin's cane-gun's) bullet. The crooks used to fire hit-scan rays; now each
 * shot is one of these, aimed the way a skeleton aims (straight at where you are when he fires, no lead, with a
 * difficulty-based inaccuracy through {@link Projectile#shoot}) and flying fast but visibly -- 2-3.5 blocks a tick
 * against an arrow's 1.6 -- so a strafing player can get out of the way. No gravity, no drag; it dies at its range.
 *
 * <p>Hits only living things, never another {@link SyndicateCriminal} when a criminal fired it (the crew shoots past
 * each other), never a creative player. Damage goes through {@link SyndicateGunfire#strike} (mob-attack damage wrapped
 * in the {@code Gunfire.hit} marker, so a raised shield blocks it and bulletproof armour sees it); a block takes a
 * bullet hole and a puff of debris ({@link SyndicateGunfire#impact}). Drawn client-side as a thin full-bright streak.
 */
public class SyndicateBulletEntity extends Projectile {
	/** Client-side safety: nothing in the crew's arsenal flies longer than this. */
	private static final int MAX_LIFE = 60;

	private static final EntityDataAccessor<Byte> GUN_KIND = SynchedEntityData.defineId(SyndicateBulletEntity.class, EntityDataSerializers.BYTE);

	private float damage = 4.0f;
	private double range = 24.0;
	private double travelled;

	public SyndicateBulletEntity(EntityType<? extends SyndicateBulletEntity> type, Level level) {
		super(type, level);
		setNoGravity(true);
	}

	/**
	 * A bullet from {@code shooter} at {@code muzzle}, already fired toward {@code dir} at {@code speed} blocks a tick
	 * with vanilla {@code inaccuracy}, and added to the world. Returns null only if the type failed to create.
	 */
	public static SyndicateBulletEntity fire(ServerLevel level, LivingEntity shooter, Vec3 muzzle, Vec3 dir, float speed,
			float inaccuracy, float damage, double range, int gunKind) {
		SyndicateBulletEntity b = SyndicateEntityTypes.BULLET.create(level);
		if (b == null) {
			return null;
		}
		b.setOwner(shooter);
		b.setPos(muzzle.x, muzzle.y, muzzle.z);
		b.damage = damage;
		b.range = range;
		b.entityData.set(GUN_KIND, (byte) gunKind);
		b.shoot(dir.x, dir.y, dir.z, speed, inaccuracy);
		level.addFreshEntity(b);
		return b;
	}

	public float damage() {
		return damage;
	}

	public double range() {
		return range;
	}

	/** The gun that fired it, as {@code GunFx} counts: 0 pistol, 1 rifle (the Kingpin's cane), 2 shotgun, 3 sniper. */
	public int gunKind() {
		return entityData.get(GUN_KIND);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(GUN_KIND, (byte) 0);
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 v = getDeltaMovement();
		boolean server = !level().isClientSide;
		if (v.lengthSqr() < 1.0e-6 || tickCount > MAX_LIFE) {
			if (server) {
				discard();
			}
			return;
		}
		HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
		if (hit.getType() != HitResult.Type.MISS) {
			if (server) {
				hitTargetOrDeflectSelf(hit);
				if (isRemoved()) {
					return;
				}
				v = getDeltaMovement(); // a deflection may have turned it round
			} else if (hit.getType() == HitResult.Type.BLOCK) {
				// the client's copy stops at the wall until the server's removal arrives
				setPos(hit.getLocation());
				setDeltaMovement(Vec3.ZERO);
				return;
			}
		}
		setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
		travelled += v.length();
		if (server && travelled >= range) {
			discard();
		}
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		if (!(e instanceof LivingEntity) || !e.isAlive() || e.isSpectator()) {
			return false;
		}
		if (e instanceof SyndicateCriminal && getOwner() instanceof SyndicateCriminal) {
			return false; // the crew shoots past each other
		}
		if (e instanceof Player p && p.isCreative()) {
			return false;
		}
		return super.canHitEntity(e);
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (level() instanceof ServerLevel server && result.getEntity() instanceof LivingEntity target) {
			LivingEntity shooter = getOwner() instanceof LivingEntity l ? l : null;
			if (SyndicateGunfire.strike(server, shooter, target, damage)) {
				SyndicateGunfire.bulletFlesh(server, target, result.getLocation(), ownerId(), gunKind(), getDeltaMovement().normalize());
			}
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		// no block interactions (bells, targets, pots): a bullet just leaves a hole
		if (level() instanceof ServerLevel server) {
			SyndicateGunfire.bulletImpact(server, result, ownerId(), gunKind(), getDeltaMovement().normalize());
		}
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (!level().isClientSide) {
			discard();
		}
	}

	private int ownerId() {
		Entity owner = getOwner();
		return owner != null ? owner.getId() : getId();
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 96.0 * 96.0;
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putFloat("Damage", damage);
		tag.putDouble("Range", range);
		tag.putDouble("Travelled", travelled);
		tag.putByte("GunKind", (byte) gunKind());
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		damage = tag.getFloat("Damage");
		range = tag.getDouble("Range");
		travelled = tag.getDouble("Travelled");
		entityData.set(GUN_KIND, tag.getByte("GunKind"));
	}
}
