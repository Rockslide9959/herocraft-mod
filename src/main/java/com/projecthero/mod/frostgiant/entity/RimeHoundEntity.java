package com.projecthero.mod.frostgiant.entity;

import com.projecthero.mod.frostgiant.FrostCombat;
import com.projecthero.mod.frostgiant.FrostGiantAnims;
import com.projecthero.mod.frostgiant.FrostGiantAnims.Clip;
import com.projecthero.mod.frostgiant.FrostGiantConfig;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: a <b>Rime Hound</b> -- a frost wolf of Jotunheim, 1.6 blocks tall, in packs of 3-5. Fast. It circles its prey
 * at a few blocks, then darts in to bite (5 damage + 40 freeze ticks) or pounces from 4-7 blocks. The first hound of a
 * pack to spot a fighter <b>howls</b>, and every hound in earshot rushes in.
 */
public class RimeHoundEntity extends FrostGiantEntity {
	private boolean howled;
	private float circleSide = 1f;

	public RimeHoundEntity(EntityType<? extends RimeHoundEntity> type, Level level) {
		super(type, level);
		this.xpReward = 6;
		this.circleSide = random.nextBoolean() ? 1f : -1f;
	}

	public static AttributeSupplier.Builder createAttributes() {
		FrostGiantConfig.Hound cfg = FrostGiantConfig.hound();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, cfg.health)
				.add(Attributes.ARMOR, cfg.armor)
				.add(Attributes.ATTACK_DAMAGE, cfg.biteDamage)
				.add(Attributes.MOVEMENT_SPEED, cfg.speed)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	public String model() {
		return FrostGiantAnims.HOUND;
	}

	@Override
	protected Clip idleClip() {
		return FrostGiantAnims.HOUND_IDLE;
	}

	@Override
	protected Clip walkClip() {
		return FrostGiantAnims.HOUND_WALK;
	}

	@Override
	protected Clip runClip() {
		return FrostGiantAnims.HOUND_RUN;
	}

	@Override
	protected Clip deathClip() {
		return FrostGiantAnims.HOUND_DEATH;
	}

	@Override
	protected Clip hurtClip() {
		return FrostGiantAnims.HOUND_HURT;
	}

	@Override
	protected double runAbove() {
		return 0.16;
	}

	@Override
	protected void tickCombat(ServerLevel level, LivingEntity target) {
		FrostGiantConfig.Hound cfg = FrostGiantConfig.hound();
		if (move != null) {
			if (target != null) {
				face(target.position());
			}
			if (move == FrostGiantAnims.HOUND_HOWL && moveTick == move.hit()) {
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOLF_HOWL, SoundSource.HOSTILE, 2.5f, 0.6f);
				for (RimeHoundEntity h : level.getEntitiesOfClass(RimeHoundEntity.class, getBoundingBox().inflate(24))) {
					h.howled = true;
					if (target != null && h.getTarget() == null) {
						h.setTarget(target);
					}
					h.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, false, false));
				}
			} else if (move == FrostGiantAnims.HOUND_BITE && moveTick == move.hit() && target != null && gapTo(target) <= 1.4) {
				FrostCombat.hit(level, this, target, cfg.biteDamage, cfg.biteFreezeTicks);
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOLF_GROWL, SoundSource.HOSTILE, 1.2f, 0.6f);
			} else if (move == FrostGiantAnims.HOUND_POUNCE) {
				if (moveTick == 4 && target != null) {
					Vec3 d = target.position().subtract(position()).multiply(1, 0, 1);
					Vec3 v = d.lengthSqr() < 1.0e-4 ? Vec3.ZERO : d.normalize().scale(Math.min(1.3, d.length() * 0.2));
					setDeltaMovement(v.x, 0.45, v.z);
				}
				if (moveTick == move.hit() && target != null && gapTo(target) <= 1.8) {
					FrostCombat.hit(level, this, target, cfg.pounceDamage, cfg.biteFreezeTicks);
				}
			}
			if (moveTick >= move.length()) {
				endMove(move == FrostGiantAnims.HOUND_HOWL ? 10 : 20 + random.nextInt(20));
			}
			return;
		}
		if (target == null) {
			getNavigation().stop();
			return;
		}
		if (!howled && target instanceof Player && distanceTo(target) < 24) {
			boolean packHowling = false;
			for (RimeHoundEntity h : level.getEntitiesOfClass(RimeHoundEntity.class, getBoundingBox().inflate(24))) {
				packHowling |= h != this && (h.howled || h.move == FrostGiantAnims.HOUND_HOWL);
			}
			howled = true;
			if (!packHowling) {
				startMove(FrostGiantAnims.HOUND_HOWL, target);
				return;
			}
		}
		double gap = gapTo(target);
		if (cooldown <= 0 && gap <= 1.0) {
			startMove(FrostGiantAnims.HOUND_BITE, target);
			return;
		}
		if (cooldown <= 0 && gap > 3.5 && gap < 7.0 && random.nextInt(20) == 0) {
			startMove(FrostGiantAnims.HOUND_POUNCE, target);
			return;
		}
		if (cooldown > 0 && gap < 7.0) {
			// circle: a point round the target, a little off to one side
			Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
			if (away.lengthSqr() < 1.0e-3) {
				away = new Vec3(1, 0, 0);
			}
			Vec3 side = away.normalize().yRot(circleSide * 0.7f).scale(5.0);
			Vec3 to = target.position().add(side);
			if (tickCount % 10 == 0) {
				getNavigation().moveTo(to.x, to.y, to.z, 1.1);
			}
			if (random.nextInt(80) == 0) {
				circleSide = -circleSide;
			}
			getLookControl().setLookAt(target, 30f, 30f);
			return;
		}
		approach(target, 1.35);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.WOLF_GROWL;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WOLF_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WOLF_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.7f + random.nextFloat() * 0.1f;
	}
}
