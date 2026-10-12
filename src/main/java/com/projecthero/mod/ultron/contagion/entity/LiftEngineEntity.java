package com.projecthero.mod.ultron.contagion.entity;

import java.util.UUID;

import com.projecthero.mod.ultron.UltronFx;
import com.projecthero.mod.ultron.contagion.ContagionConfig;
import com.projecthero.mod.ultron.contagion.SokoviaFinale;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: a <b>Lift Engine</b> -- one of the four spires at the sky-citadel's edge that hold it up. Shielded while its
 * guard (an Ultron Heavy) lives; 500 health. Each one destroyed takes {@link ContagionConfig.Finale#engineChargeRemoval}
 * (15%) off the Vibranium Core's charge.
 */
public class LiftEngineEntity extends ContagionStructureEntity {
	private static final EntityDataAccessor<Boolean> DATA_GUARDED = SynchedEntityData.defineId(LiftEngineEntity.class, EntityDataSerializers.BOOLEAN);

	private UUID guard;

	public LiftEngineEntity(EntityType<? extends LiftEngineEntity> type, Level level) {
		super(type, level);
		this.xpReward = 40;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, ContagionConfig.finale().engineHealth)
				.add(Attributes.ARMOR, 8.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.FOLLOW_RANGE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_GUARDED, true);
	}

	@Override
	public String modelName() {
		return "lift_engine";
	}

	public void setGuard(UUID guard) {
		this.guard = guard;
		entityData.set(DATA_GUARDED, guard != null);
	}

	public boolean guarded() {
		return entityData.get(DATA_GUARDED);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel server) || isDeadOrDying()) {
			return;
		}
		if (guard != null && tickCount % 10 == 0) {
			Entity g = server.getEntity(guard);
			if (!(g instanceof LivingEntity le) || !le.isAlive()) {
				guard = null;
				entityData.set(DATA_GUARDED, false);
				server.playSound(null, getX(), getY() + 3, getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.5f, 0.8f);
				UltronFx.forced(server, UltronFx.RED, getX(), getY() + 3, getZ(), 30, 1.0, 2.0, 1.0, 0.05);
			}
		}
		if (tickCount % 3 == 0) {
			server.sendParticles(ParticleTypes.FLAME, getX(), getY() - 0.2, getZ(), 2, 0.4, 0.05, 0.4, 0.02);
			server.sendParticles(UltronFx.RED_SMALL, getX(), getY() - 0.4, getZ(), 2, 0.5, 0.3, 0.5, 0.0);
		}
		if (guarded() && tickCount % 10 == 0) {
			Entity g = guard == null ? null : server.getEntity(guard);
			if (g != null) {
				UltronFx.beam(server, position().add(0, 3.5, 0), g.position().add(0, 1.2, 0), UltronFx.TETHER, 11);
			}
		}
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (guarded() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			if (source.getEntity() instanceof ServerPlayer p && tickCount % 4 == 0) {
				p.displayClientMessage(Component.translatable("message.projecthero.contagion.engine_guarded").withStyle(ChatFormatting.RED), true);
				level().playSound(null, getX(), getY() + 3, getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0f, 0.6f);
			}
			return false;
		}
		return super.hurt(source, amount);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel server) {
			Vec3 c = position().add(0, 3, 0);
			UltronFx.forced(server, ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			server.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.6f);
			SokoviaFinale.onEngineDestroyed(server, this);
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (guard != null) {
			tag.putUUID("Guard", guard);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		guard = tag.hasUUID("Guard") ? tag.getUUID("Guard") : null;
		entityData.set(DATA_GUARDED, guard != null);
		setNoGravity(true);
	}
}
