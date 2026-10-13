package com.projecthero.mod.galactus.entity;

import java.util.UUID;

import com.projecthero.mod.galactus.GalactusAnims;
import com.projecthero.mod.galactus.GalactusAnims.Clip;
import com.projecthero.mod.galactus.GalactusCombat;
import com.projecthero.mod.galactus.GalactusFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: <b>the Hand of Galactus</b> -- the giant hand of the Hand Grab, a separate entity (its own render-only model,
 * {@code galactus_hand}) that Galactus moves: down over its shadow, closed on a victim, up to his face. While it holds
 * someone it can be hit: the grip's health ({@link com.projecthero.mod.galactus.GalactusConfig.Grab#gripHealth}) breaks
 * and the victim drops free. Never saved (a reload releases the grip).
 */
public class GalactusHandEntity extends GalactusMob {
	private static final EntityDataAccessor<Boolean> DATA_GRIPPING = SynchedEntityData.defineId(GalactusHandEntity.class,
			EntityDataSerializers.BOOLEAN);

	private UUID ownerId;

	public GalactusHandEntity(EntityType<? extends GalactusHandEntity> type, Level level) {
		super(type, level);
		setNoGravity(true);
		noPhysics = true;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 100.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_GRIPPING, false);
	}

	@Override
	public String model() {
		return GalactusAnims.HAND;
	}

	@Override
	protected Clip idleClip() {
		return gripping() ? GalactusAnims.HAND_GRIP : GalactusAnims.HAND_IDLE;
	}

	public void configure(UUID owner, double gripHealth) {
		this.ownerId = owner;
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(gripHealth);
		setHealth((float) gripHealth);
	}

	public boolean gripping() {
		return entityData.get(DATA_GRIPPING);
	}

	public void setGripping(boolean gripping) {
		entityData.set(DATA_GRIPPING, gripping);
	}

	public GalactusEntity owner() {
		return ownerId != null && level() instanceof ServerLevel s && s.getEntity(ownerId) instanceof GalactusEntity g ? g : null;
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel && tickCount > 5 && owner() == null) {
			discard();
		}
	}

	@Override
	public void aiStep() {
		setDeltaMovement(Vec3.ZERO);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!(level() instanceof ServerLevel server) || isRemoved() || isDeadOrDying()) {
			return false;
		}
		if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurt(source, amount);
		}
		if (!gripping() || source.getEntity() instanceof LivingEntity a && GalactusCombat.isCosmic(a)) {
			return false;
		}
		GalactusEntity g = owner();
		if (g != null && source.getEntity() == g.grabbedEntity(server)) {
			return false; // the victim can't punch their own way out -- that's what allies are for
		}
		invulnerableTime = 0;
		boolean hurt = super.hurt(source, amount);
		if (hurt) {
			server.sendParticles(ParticleTypes.CRIT, getX(), getY() + 2, getZ(), 8, 1.2, 1.2, 1.2, 0.2);
			if (g != null) {
				g.threat().record(source, amount);
			}
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel server) {
			GalactusFx.burst(server, position().add(0, 2, 0), 2.0);
			GalactusEntity g = owner();
			if (g != null) {
				g.onGripBroken(server);
			}
			discard();
		}
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		return getBoundingBox().inflate(6.0);
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
	}
}
