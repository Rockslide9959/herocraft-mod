package com.projecthero.mod.carnage.entity;

import java.util.UUID;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * v0.14.25: Carnage's brood -- knee-high copies of him (the player model at 0.55x in his skin) that pour out of his
 * cocoon when he splits. Fast, leaping, weak to fire like he is. Kill them all to break him out of the cocoon reeling;
 * whatever is still alive after 20 s crawls back into him. They never drop anything and disappear if he dies.
 */
public class CrimsonSpawnEntity extends Monster {
	private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.75f, 0.04f, 0.06f), 0.8f);
	private UUID parent;

	public CrimsonSpawnEntity(EntityType<? extends CrimsonSpawnEntity> type, Level level) {
		super(type, level);
		this.xpReward = 3;
		refreshDimensions();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.MOVEMENT_SPEED, 0.38)
				.add(Attributes.ATTACK_DAMAGE, 5.0)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.SAFE_FALL_DISTANCE, 12.0)
				.add(Attributes.SCALE, 0.55);
	}

	public void setParent(UUID parent) {
		this.parent = parent;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.45f));
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, false));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.9));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0f));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this, CarnageEntity.class, CrimsonSpawnEntity.class));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.getEntity() instanceof CarnageEntity || source.getEntity() instanceof CrimsonSpawnEntity) {
			return false;
		}
		return super.hurt(source, source.is(DamageTypeTags.IS_FIRE) ? amount * 2.0f : amount);
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel server) {
			if (tickCount % 8 == 0) {
				server.sendParticles(BLOOD, getX(), getY() + 0.5, getZ(), 1, 0.2, 0.2, 0.2, 0.0);
			}
			// orphaned (their Carnage is gone): they dissolve
			if (tickCount % 40 == 0 && parent != null && !(server.getEntity(parent) instanceof CarnageEntity c && c.isAlive())) {
				server.sendParticles(BLOOD, getX(), getY() + 0.4, getZ(), 12, 0.2, 0.3, 0.2, 0.0);
				discard();
			}
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SLIME_SQUISH_SMALL;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SLIME_HURT_SMALL;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SLIME_DEATH_SMALL;
	}

	@Override
	public float getVoicePitch() {
		return 1.3f;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (parent != null) {
			tag.putUUID("Parent", parent);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		parent = tag.hasUUID("Parent") ? tag.getUUID("Parent") : null;
	}
}
