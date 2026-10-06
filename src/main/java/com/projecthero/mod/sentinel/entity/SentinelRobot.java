package com.projecthero.mod.sentinel.entity;

import java.util.UUID;

import com.projecthero.mod.sentinel.SentinelPurgeEvent;
import com.projecthero.mod.sentinel.SentinelTargets;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.15.1: what every Sentinel Program robot shares -- the Drone, the Sentinel and Master Mold.
 * <ul>
 *   <li><b>The purge link</b>: a robot sent by a {@link SentinelPurgeEvent} remembers it and removes itself if the purge
 *       no longer exists (ended while its chunk was unloaded) -- the same "orphan guard" as the Parademons.</li>
 *   <li><b>Target priority</b>: every second it re-picks its prey with {@link SentinelTargets#pickTarget} -- mutants
 *       first, then superhumans, then anyone else -- and announces the lock-on ("MUTANT DETECTED").</li>
 *   <li><b>Machines</b>: they never hurt each other, take no fall damage, can't burn or be poisoned.</li>
 * </ul>
 */
public abstract class SentinelRobot extends Monster implements GeoEntity {
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private UUID purgeId;
	private UUID lastAnnouncedTarget;

	protected SentinelRobot(EntityType<? extends SentinelRobot> type, Level level) {
		super(type, level);
	}

	public UUID purgeId() {
		return purgeId;
	}

	public void bindToPurge(UUID id) {
		this.purgeId = id;
	}

	/** How far this robot looks for prey. */
	protected double huntRange() {
		return 48.0;
	}

	/** The name used for its voice lines. */
	protected Component speakerName() {
		return getDisplayName();
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel server) || isDeadOrDying()) {
			return;
		}
		if (tickCount % 40 == 0 && purgeId != null && SentinelPurgeEvent.find(server, purgeId) == null) {
			discard();
			return;
		}
		LivingEntity target = getTarget();
		if (target != null && !SentinelTargets.canTarget(target)) {
			setTarget(null);
			target = null;
		}
		if (tickCount % 20 == 0) {
			Player best = SentinelTargets.pickTarget(server, this, huntRange());
			if (best != null && best != target && (target == null || !(target instanceof Player)
					|| SentinelTargets.classify(best).ordinal() > SentinelTargets.classify((Player) target).ordinal()
					|| distanceToSqr(target) > huntRange() * huntRange())) {
				setTarget(best);
				target = best;
			}
		}
		if (target instanceof Player p && !p.getUUID().equals(lastAnnouncedTarget)) {
			lastAnnouncedTarget = p.getUUID();
			SentinelTargets.announceLock(server, speakerName(), p);
		}
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return other instanceof SentinelRobot || super.isAlliedTo(other);
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		Entity attacker = source.getEntity();
		if (attacker instanceof SentinelRobot && attacker != this) {
			return true;
		}
		return super.isInvulnerableTo(source);
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		if (effect.is(MobEffects.POISON) || effect.is(MobEffects.WITHER) || effect.is(MobEffects.REGENERATION)) {
			return false;
		}
		return super.canBeAffected(effect);
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
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (purgeId != null) {
			tag.putUUID("SentinelPurge", purgeId);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		purgeId = tag.hasUUID("SentinelPurge") ? tag.getUUID("SentinelPurge") : null;
	}
}
