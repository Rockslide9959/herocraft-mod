package com.projecthero.mod.ultron.contagion.entity;

import com.projecthero.mod.ultron.UltronFx;
import com.projecthero.mod.ultron.contagion.ContagionConfig;
import com.projecthero.mod.ultron.contagion.SokoviaFinale;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: an <b>Uplink Antenna</b> -- phase 4 of the Omega fight. Three rise at the citadel's edge when Ultron tries to
 * escape into the network (300 health + 100 per extra fighter). Destroy all three before the 35 s upload finishes and he
 * is stranded. It rises out of the floor over 2 s.
 */
public class UplinkAntennaEntity extends ContagionStructureEntity {
	public static final int RISE_TICKS = 40;
	private static final EntityDataAccessor<Long> DATA_BORN = SynchedEntityData.defineId(UplinkAntennaEntity.class, EntityDataSerializers.LONG);

	public UplinkAntennaEntity(EntityType<? extends UplinkAntennaEntity> type, Level level) {
		super(type, level);
		this.xpReward = 30;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, ContagionConfig.omega().antennaHealth)
				.add(Attributes.ARMOR, 4.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.FOLLOW_RANGE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_BORN, 0L);
	}

	@Override
	public String modelName() {
		return "uplink_antenna";
	}

	public void configure(int fighters) {
		ContagionConfig.Omega o = ContagionConfig.omega();
		double hp = o.antennaHealth + o.antennaHealthPerExtraFighter * Math.max(0, fighters - 1);
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
		setHealth((float) hp);
		entityData.set(DATA_BORN, level().getGameTime());
	}

	/** 0..1 through its rise out of the floor (for the renderer). */
	public float rise(float partialTick) {
		long born = entityData.get(DATA_BORN);
		return born == 0L ? 1f : Math.max(0f, Math.min(1f, (level().getGameTime() - born + partialTick) / RISE_TICKS));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level() instanceof ServerLevel server && !isDeadOrDying() && tickCount % 4 == 0) {
			Vec3 tip = position().add(0, 6.6 * rise(0f), 0);
			server.sendParticles(UltronFx.RED, tip.x, tip.y, tip.z, 1, 0.1, 0.1, 0.1, 0.0);
			if (tickCount % 20 == 0) {
				UltronFx.beam(server, tip, tip.add(0, 40, 0), UltronFx.TETHER, 21);
			}
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel server) {
			Vec3 c = position().add(0, 4, 0);
			server.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 3, 0.5, 1.5, 0.5, 0);
			server.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 1.0f);
			SokoviaFinale.onAntennaDestroyed(server, this);
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putLong("Born", entityData.get(DATA_BORN));
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		entityData.set(DATA_BORN, tag.getLong("Born"));
		setNoGravity(true);
	}
}
