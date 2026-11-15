package com.projecthero.mod.powerarmour.entity;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Power Armour: a Missile Pod missile. It pops up out of the pod, turns towards its target (or the point the wearer looked
 * at) and bursts on contact: {@code damage} to every harmable creature within 2 blocks, no block damage, never its owner.
 */
public class ArmourMissileEntity extends Projectile {
	private int targetId = -1;
	private Vec3 aimPoint;
	private float damage = 6f;
	private int age;

	public ArmourMissileEntity(EntityType<? extends ArmourMissileEntity> type, Level level) {
		super(type, level);
	}

	public void setup(LivingEntity target, Vec3 aimPoint, float damage) {
		this.targetId = target == null ? -1 : target.getId();
		this.aimPoint = aimPoint;
		this.damage = damage;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		age++;
		Vec3 v = getDeltaMovement();
		if (level().isClientSide()) {
			level().addParticle(ParticleTypes.SMOKE, getX() - v.x * 0.5, getY() - v.y * 0.5, getZ() - v.z * 0.5, 0, 0.01, 0);
			if (age % 2 == 0) {
				level().addParticle(ParticleTypes.FLAME, getX() - v.x * 0.3, getY() - v.y * 0.3, getZ() - v.z * 0.3, 0, 0, 0);
			}
		} else {
			if (age > 100) {
				burst();
				return;
			}
			if (age > 5) {
				Vec3 goal = aimPoint;
				Entity t = targetId >= 0 ? level().getEntity(targetId) : null;
				if (t instanceof LivingEntity le && le.isAlive()) {
					goal = le.position().add(0, le.getBbHeight() * 0.5, 0);
				}
				if (goal != null) {
					Vec3 want = goal.subtract(position()).normalize();
					v = v.scale(0.78).add(want.scale(0.42));
					double sp = v.length();
					if (sp > 1.25) {
						v = v.scale(1.25 / sp);
					}
				}
			} else {
				v = v.scale(0.92);
			}
			setDeltaMovement(v);
			HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
			if (hit.getType() != HitResult.Type.MISS) {
				burst();
				return;
			}
		}
		setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
		ProjectileUtil.rotateTowardsMovement(this, 0.5f);
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		Entity owner = getOwner();
		return e != owner && e instanceof LivingEntity && super.canHitEntity(e) && (owner == null || HeroTargets.canHarm(owner, e));
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		burst();
	}

	private void burst() {
		if (!(level() instanceof ServerLevel level) || isRemoved()) {
			return;
		}
		if (getOwner() instanceof ServerPlayer owner) {
			for (LivingEntity e : AbilityHelpers.enemiesAround(owner, position(), 2.2)) {
				if (AbilityHelpers.hurtBurst(owner, e, damage)) {
					AbilityHelpers.knockbackFrom(e, position(), 0.5);
				}
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 10, 0.3, 0.3, 0.3, 0.05);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7f, 1.4f);
		discard();
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putFloat("Damage", damage);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		damage = tag.getFloat("Damage");
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 128 * 128;
	}
}
