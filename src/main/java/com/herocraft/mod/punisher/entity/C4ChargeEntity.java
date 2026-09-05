package com.herocraft.mod.punisher.entity;

import java.util.UUID;

import com.herocraft.mod.punisher.PunisherConfig;
import com.herocraft.mod.punisher.ability.PunisherC4;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A placed Punisher explosive charge (spec section 27). Sits on a surface, remembers its owner, and
 * detonates only when that owner triggers it (sneak + C). Up to {@link PunisherConfig#C4_MAX_ACTIVE}
 * per player -- enforced in {@link PunisherC4}. Stronger than the Frag Grenade with moderate terrain
 * damage; it must not obliterate large sections of a build.
 */
public class C4ChargeEntity extends Entity {
	private UUID ownerId;
	private int age;

	public C4ChargeEntity(EntityType<? extends C4ChargeEntity> type, Level level) {
		super(type, level);
		this.noPhysics = false;
		setNoGravity(true);
	}

	public C4ChargeEntity(Level level, Vec3 pos, LivingEntity owner) {
		this(PunisherEntityTypes.C4_CHARGE, level);
		setPos(pos.x, pos.y, pos.z);
		this.ownerId = owner.getUUID();
	}

	public UUID ownerId() {
		return ownerId;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		setDeltaMovement(Vec3.ZERO);
		if (level().isClientSide()) {
			if (age % 20 == 0) {
				level().addParticle(ParticleTypes.SMALL_FLAME, getX(), getY() + 0.12, getZ(), 0, 0.005, 0);
			}
		}
		age++;
	}

	/** Blow this charge. Safe to call from any thread-context that has a ServerLevel. */
	public void detonate() {
		if (level().isClientSide() || !isAlive()) {
			return;
		}
		ServerLevel level = (ServerLevel) level();
		boolean grief = com.herocraft.mod.hero.power.AbilityHelpers.canGrief();
		level.explode(this, getX(), getY() + 0.1, getZ(), grief ? PunisherConfig.C4_BLOCK_POWER : 0f,
				grief ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.1, getZ(), 1, 0, 0, 0, 0);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(),
				SoundSource.PLAYERS, 1.8f, 0.8f);

		LivingEntity owner = ownerId != null ? resolveOwner(level) : null;
		var src = owner instanceof net.minecraft.server.level.ServerPlayer sp
				? level.damageSources().playerAttack(sp)
				: level.damageSources().explosion(this, owner);
		double r = PunisherConfig.C4_RADIUS;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
				new AABB(getX() - r, getY() - r, getZ() - r, getX() + r, getY() + r, getZ() + r),
				e -> e.isAlive() && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
			double dist = Math.sqrt(e.distanceToSqr(getX(), getY() + 0.1, getZ()));
			if (dist > r) {
				continue;
			}
			if (e instanceof net.minecraft.world.entity.player.Player p && p != owner
					&& (getServer() == null || !getServer().isPvpAllowed()
					|| !com.herocraft.mod.hero.HeroConfig.get().abilityPvpDamage)) {
				continue;
			}
			float falloff = (float) Math.max(0.2, 1.0 - dist / r);
			e.hurt(src, PunisherConfig.C4_DAMAGE * falloff);
			double kx = e.getX() - getX();
			double kz = e.getZ() - getZ();
			double kd = Math.max(0.1, Math.sqrt(kx * kx + kz * kz));
			e.knockback(0.8 * falloff, -kx / kd, -kz / kd);
			e.hurtMarked = true;
		}
		discard();
	}

	private LivingEntity resolveOwner(ServerLevel level) {
		Entity e = level.getEntity(ownerId);
		return e instanceof LivingEntity le ? le : null;
	}

	@Override
	public void remove(RemovalReason reason) {
		if (!level().isClientSide() && ownerId != null) {
			PunisherC4.forget(ownerId, getId());
		}
		super.remove(reason);
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		if (tag.hasUUID("Owner")) {
			ownerId = tag.getUUID("Owner");
		}
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		if (ownerId != null) {
			tag.putUUID("Owner", ownerId);
		}
	}
}
