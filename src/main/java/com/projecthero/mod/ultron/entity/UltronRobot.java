package com.projecthero.mod.ultron.entity;

import java.util.UUID;

import com.projecthero.mod.ultron.UltronCombat;
import com.projecthero.mod.ultron.UltronFx;
import com.projecthero.mod.ultron.UltronUprising;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;

/**
 * v0.15.12: what every one of Ultron's robots shares -- the drones, the Heavy, the Sniper Frame, the relay pylons and
 * Ultron's own bodies.
 * <ul>
 *   <li><b>The uprising link</b>: a robot sent by an {@link UltronUprising} remembers it and removes itself if the uprising
 *       no longer exists (ended while its chunk was unloaded) -- the "orphan guard".</li>
 *   <li><b>Damage taken</b> ({@link UltronCombat#multiplier}): lightning / electricity x1.5, Hulk or a heavy melee hit
 *       x1.25. Immune to poison and wither, no fall damage, and never hurt by another of Ultron's own (or their missiles).</li>
 *   <li><b>EMP stun</b>: a destroyed pylon's burst freezes nearby robots for a few seconds ({@link #stun}).</li>
 *   <li>{@link #action}: a synced "what am I doing" byte the client reads for poses.</li>
 * </ul>
 * They are drawn with the player model in their Skindex skins ({@link #skin}).
 */
public abstract class UltronRobot extends Monster {
	private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(UltronRobot.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_STUNNED = SynchedEntityData.defineId(UltronRobot.class, EntityDataSerializers.BOOLEAN);

	public static final byte ACTION_NONE = 0;
	public static final byte ACTION_AIM = 1;

	private UUID uprisingId;
	private int stunTicks;

	protected UltronRobot(EntityType<? extends UltronRobot> type, Level level) {
		super(type, level);
		this.xpReward = 8;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTION, ACTION_NONE);
		builder.define(DATA_STUNNED, false);
	}

	/** The skin this robot wears. */
	public abstract UltronSkin skin();

	public byte action() {
		return entityData.get(DATA_ACTION);
	}

	public void setAction(byte action) {
		if (entityData.get(DATA_ACTION) != action) {
			entityData.set(DATA_ACTION, action);
		}
	}

	public UUID uprisingId() {
		return uprisingId;
	}

	public void bindToUprising(UUID id) {
		this.uprisingId = id;
	}

	/** The uprising this robot belongs to, if it is still running. */
	public UltronUprising uprising() {
		return uprisingId != null && level() instanceof ServerLevel server ? UltronUprising.find(server, uprisingId) : null;
	}

	// ---------------------------------------------------------------- stun

	/** EMP: frozen for {@code ticks} (no moving, no attacking). */
	public void stun(int ticks) {
		stunTicks = Math.max(stunTicks, ticks);
		entityData.set(DATA_STUNNED, true);
		getNavigation().stop();
	}

	public boolean isStunned() {
		return level().isClientSide() ? entityData.get(DATA_STUNNED) : stunTicks > 0;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel server) || isDeadOrDying()) {
			return;
		}
		if (tickCount % 40 == 0 && uprisingId != null && UltronUprising.find(server, uprisingId) == null) {
			discard();
			return;
		}
		if (stunTicks > 0) {
			stunTicks--;
			getNavigation().stop();
			setDeltaMovement(getDeltaMovement().multiply(0.3, 1.0, 0.3));
			if (tickCount % 3 == 0) {
				server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight() * 0.7, getZ(), 2, getBbWidth() * 0.4,
						getBbHeight() * 0.3, getBbWidth() * 0.4, 0.05);
			}
			if (stunTicks == 0) {
				entityData.set(DATA_STUNNED, false);
			}
		}
	}

	@Override
	protected void customServerAiStep() {
		if (stunTicks > 0) {
			return; // the brain is offline
		}
		super.customServerAiStep();
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!level().isClientSide()) {
			amount *= UltronCombat.multiplier(source, amount);
		}
		boolean hurt = super.hurt(source, amount);
		if (hurt && level() instanceof ServerLevel server && amount > 3f) {
			server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight() * 0.6, getZ(), 3, 0.2, 0.2, 0.2, 0.1);
		}
		return hurt;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		Entity attacker = source.getEntity();
		if (attacker instanceof UltronRobot && attacker != this) {
			return true;
		}
		if (source.getDirectEntity() instanceof Projectile proj && proj.getOwner() instanceof UltronRobot) {
			return true;
		}
		return super.isInvulnerableTo(source);
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return other instanceof UltronRobot || super.isAlliedTo(other);
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return !(target instanceof UltronRobot) && super.canAttack(target);
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		if (effect.is(MobEffects.POISON) || effect.is(MobEffects.WITHER) || effect.is(MobEffects.REGENERATION)) {
			return false;
		}
		return super.canBeAffected(effect);
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return uprisingId == null && super.removeWhenFarAway(distanceSq);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 1.2f + random.nextFloat() * 0.2f;
	}

	@Override
	protected void tickDeath() {
		++deathTime;
		if (deathTime >= 20 && !level().isClientSide() && !isRemoved()) {
			if (level() instanceof ServerLevel server) {
				UltronFx.wreck(server, position().add(0, getBbHeight() * 0.5, 0), Math.max(0.6, getBbHeight() * 0.4));
			}
			remove(Entity.RemovalReason.KILLED);
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (uprisingId != null) {
			tag.putUUID("UltronUprising", uprisingId);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		uprisingId = tag.hasUUID("UltronUprising") ? tag.getUUID("UltronUprising") : null;
	}
}
