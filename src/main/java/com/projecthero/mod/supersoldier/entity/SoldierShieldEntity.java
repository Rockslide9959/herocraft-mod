package com.projecthero.mod.supersoldier.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.supersoldier.SuperSoldier;
import com.projecthero.mod.supersoldier.SuperSoldierAbilities;
import com.projecthero.mod.supersoldier.SuperSoldierConfig;

import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * G: the thrown Soldier's Shield (v0.14.8). It flies straight and fast; the first enemy it strikes takes the hit and the
 * shield ricochets into the nearest other enemy it can see (up to {@link SuperSoldierConfig#SHIELD_MAX_HITS} in all),
 * glancing off walls into a new target the same way. Out of targets, out of range or out of ricochets, it turns round and
 * flies back to the thrower -- straight through blocks -- and is caught. It never lands, never drops and is never saved:
 * one that outlives its thrower (logout, death, a lost power) just vanishes.
 *
 * <p>Server-authoritative: it moves itself every tick and is tracked every tick; the client only extrapolates along the
 * synced velocity between updates ({@code SoldierShieldRenderer} spins it flat like a discus).
 */
public class SoldierShieldEntity extends Entity {
	private static final EntityDataAccessor<Boolean> RETURNING = SynchedEntityData.defineId(SoldierShieldEntity.class,
			EntityDataSerializers.BOOLEAN);

	// server-only
	private UUID ownerId;
	private Vec3 origin;
	private int targetId = -1;
	private int hitsLeft = SuperSoldierConfig.SHIELD_MAX_HITS;
	private final Set<Integer> hit = new HashSet<>();

	public SoldierShieldEntity(EntityType<? extends SoldierShieldEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/** Throw one shield from {@code from} along {@code dir}. */
	public static SoldierShieldEntity launch(ServerPlayer owner, Vec3 from, Vec3 dir) {
		SoldierShieldEntity shield = new SoldierShieldEntity(SuperSoldierEntities.SOLDIER_SHIELD, owner.level());
		shield.ownerId = owner.getUUID();
		shield.origin = from;
		shield.setPos(from.x, from.y, from.z);
		shield.setDeltaMovement(dir.normalize().scale(SuperSoldierConfig.SHIELD_SPEED));
		owner.level().addFreshEntity(shield);
		return shield;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(RETURNING, false);
	}

	public boolean isReturning() {
		return entityData.get(RETURNING);
	}

	/** How many more enemies this throw can still hit (tests). */
	public int hitsLeft() {
		return hitsLeft;
	}

	public int hitCount() {
		return hit.size();
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			Vec3 v = getDeltaMovement();
			setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
			if (tickCount % 2 == 0) {
				level().addParticle(new DustParticleOptions(new Vector3f(0.3f, 0.5f, 1.0f), 0.8f), getX(), getY(), getZ(), 0, 0, 0);
			}
			return;
		}
		ServerPlayer owner = ownerId == null ? null
				: (level().getPlayerByUUID(ownerId) instanceof ServerPlayer sp ? sp : null);
		if (owner == null || !owner.isAlive() || owner.level() != level() || !SuperSoldier.hasPower(owner)
				|| tickCount > SuperSoldierConfig.SHIELD_MAX_LIFE) {
			discard();
			return;
		}
		if (isReturning()) {
			tickReturn(owner);
			return;
		}
		Vec3 pos = position();
		Vec3 vel = getDeltaMovement();
		if (targetId >= 0) {
			Entity t = level().getEntity(targetId);
			if (t instanceof LivingEntity living && living.isAlive()) {
				vel = living.getBoundingBox().getCenter().subtract(pos).normalize().scale(SuperSoldierConfig.SHIELD_SPEED);
			} else {
				targetId = -1;
				if (!pickNext(owner)) {
					startReturn();
					tickReturn(owner);
					return;
				}
			}
		}
		Vec3 next = pos.add(vel);
		LivingEntity struck = firstEntity(owner, pos, next);
		BlockHitResult block = level().clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		boolean blockFirst = block.getType() != HitResult.Type.MISS
				&& (struck == null || block.getLocation().distanceToSqr(pos) < struck.getBoundingBox().getCenter().distanceToSqr(pos));
		setDeltaMovement(vel);
		if (struck != null && !blockFirst) {
			Vec3 at = struck.getBoundingBox().getCenter();
			setPos(at.x, at.y, at.z);
			onStrike(owner, struck);
			return;
		}
		if (blockFirst) {
			Vec3 at = block.getLocation().subtract(vel.normalize().scale(0.2));
			setPos(at.x, at.y, at.z);
			level().playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 1.9f);
			((ServerLevel) level()).sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.2);
			if (!pickNext(owner)) {
				startReturn();
			}
			return;
		}
		setPos(next.x, next.y, next.z);
		if (origin != null && next.distanceTo(origin) > SuperSoldierConfig.SHIELD_RANGE) {
			startReturn();
		}
	}

	private void onStrike(ServerPlayer owner, LivingEntity target) {
		hit.add(target.getId());
		hitsLeft--;
		targetId = -1;
		Vec3 from = position().subtract(getDeltaMovement());
		SuperSoldierAbilities.strike(owner, target, SuperSoldierConfig.SHIELD_DAMAGE, from, SuperSoldierConfig.SHIELD_KNOCKBACK, 0.1);
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 1.7f);
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.2f);
		((ServerLevel) level()).sendParticles(new DustParticleOptions(new Vector3f(0.3f, 0.5f, 1.0f), 1.2f),
				getX(), getY(), getZ(), 10, 0.3, 0.3, 0.3, 0.0);
		if (hitsLeft <= 0 || !pickNext(owner)) {
			startReturn();
		}
	}

	/** The nearest enemy it can see within ricochet range that it has not hit yet. False if there is none. */
	private boolean pickNext(ServerPlayer owner) {
		if (hitsLeft <= 0) {
			return false;
		}
		Vec3 pos = position();
		double r = SuperSoldierConfig.SHIELD_RICOCHET_RANGE;
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(r),
				e -> !hit.contains(e.getId()) && (e instanceof Enemy || e instanceof Player) && SuperSoldierAbilities.canTarget(owner, e))) {
			Vec3 c = e.getBoundingBox().getCenter();
			double d = c.distanceToSqr(pos);
			if (d > r * r || d >= bestD) {
				continue;
			}
			if (level().clip(new ClipContext(pos, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) {
				continue;
			}
			best = e;
			bestD = d;
		}
		if (best == null) {
			return false;
		}
		targetId = best.getId();
		setDeltaMovement(best.getBoundingBox().getCenter().subtract(pos).normalize().scale(SuperSoldierConfig.SHIELD_SPEED));
		return true;
	}

	private LivingEntity firstEntity(ServerPlayer owner, Vec3 from, Vec3 to) {
		AABB sweep = new AABB(from, to).inflate(0.6);
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, sweep,
				e -> !hit.contains(e.getId()) && SuperSoldierAbilities.canTarget(owner, e))) {
			AABB box = e.getBoundingBox().inflate(0.35);
			if (!box.contains(from) && box.clip(from, to).isEmpty()) {
				continue;
			}
			double d = e.getBoundingBox().getCenter().distanceToSqr(from);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	private void startReturn() {
		entityData.set(RETURNING, true);
		targetId = -1;
	}

	private void tickReturn(ServerPlayer owner) {
		Vec3 home = owner.getEyePosition().add(0, -0.4, 0);
		Vec3 to = home.subtract(position());
		double dist = to.length();
		if (dist < 1.6) {
			level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 1.0f, 1.2f);
			discard();
			return;
		}
		double speed = Math.max(SuperSoldierConfig.SHIELD_SPEED, Math.min(3.0, dist * 0.25));
		Vec3 vel = to.normalize().scale(Math.min(speed, dist));
		setDeltaMovement(vel);
		setPos(getX() + vel.x, getY() + vel.y, getZ() + vel.z);
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
	protected void readAdditionalSaveData(CompoundTag tag) {
		// never saved: a reloaded shield has no thrower and vanishes on its first tick
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
