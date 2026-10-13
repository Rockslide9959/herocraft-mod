package com.projecthero.mod.frostgiant.entity;

import com.projecthero.mod.frostgiant.FrostCombat;
import com.projecthero.mod.frostgiant.FrostGiantAnims;
import com.projecthero.mod.frostgiant.FrostGiantAnims.Clip;
import com.projecthero.mod.frostgiant.FrostGiantConfig;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
 * v0.16.1: a <b>Jotun Raider</b> -- the rank and file, 4.5 blocks tall with an ice spear. They come down the beam in
 * pairs and <b>war-cry</b> before they charge. Up close: a spear thrust (10 damage, reach 4). At 8-24 blocks: a one-second
 * wind-up (spear raised, ice forming on it) then an <b>ice javelin</b> (9 damage + 60 freeze ticks, shatters). With no
 * fighter near them they go for the Rune Braziers.
 */
public class JotunRaiderEntity extends FrostGiantEntity {
	private boolean criedOut;
	private int javelinCooldown = 40;

	public JotunRaiderEntity(EntityType<? extends JotunRaiderEntity> type, Level level) {
		super(type, level);
		this.xpReward = 15;
	}

	public static AttributeSupplier.Builder createAttributes() {
		FrostGiantConfig.Raider cfg = FrostGiantConfig.raider();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, cfg.health)
				.add(Attributes.ARMOR, cfg.armor)
				.add(Attributes.ATTACK_DAMAGE, cfg.spearDamage)
				.add(Attributes.MOVEMENT_SPEED, cfg.speed)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.STEP_HEIGHT, 1.5);
	}

	@Override
	public String model() {
		return FrostGiantAnims.RAIDER;
	}

	@Override
	protected boolean huntsBraziers() {
		return true;
	}

	@Override
	protected Clip idleClip() {
		return FrostGiantAnims.RAIDER_IDLE;
	}

	@Override
	protected Clip walkClip() {
		return FrostGiantAnims.RAIDER_WALK;
	}

	@Override
	protected Clip runClip() {
		return FrostGiantAnims.RAIDER_RUN;
	}

	@Override
	protected Clip deathClip() {
		return FrostGiantAnims.RAIDER_DEATH;
	}

	@Override
	protected Clip hurtClip() {
		return FrostGiantAnims.RAIDER_HURT;
	}

	@Override
	protected void tickCombat(ServerLevel level, LivingEntity target) {
		FrostGiantConfig.Raider cfg = FrostGiantConfig.raider();
		if (javelinCooldown > 0) {
			javelinCooldown--;
		}
		if (move != null) {
			if (target != null) {
				face(target.position());
			}
			if (move == FrostGiantAnims.RAIDER_WAR_CRY && moveTick == move.hit()) {
				warCrySound(level);
				addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1, false, false));
			} else if (move == FrostGiantAnims.RAIDER_SPEAR_THRUST && moveTick == move.hit() && target != null && gapTo(target) <= cfg.spearReach + 0.6) {
				FrostCombat.hit(level, this, target, cfg.spearDamage, 0);
				level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
			} else if (move == FrostGiantAnims.RAIDER_JAVELIN_WINDUP) {
				Vec3 hand = handPos();
				if (moveTick % 3 == 0) {
					level.sendParticles(ParticleTypes.SNOWFLAKE, hand.x, hand.y, hand.z, 3, 0.2, 0.4, 0.2, 0.01);
				}
				if (moveTick >= move.length()) {
					LivingEntity t = target;
					startMove(FrostGiantAnims.RAIDER_JAVELIN_THROW, t);
					return;
				}
			} else if (move == FrostGiantAnims.RAIDER_JAVELIN_THROW && moveTick == move.hit() && target != null) {
				Vec3 from = handPos();
				Vec3 aim = target.position().add(0, target.getBbHeight() * 0.55, 0).add(target.getDeltaMovement().scale(6));
				Vec3 v = aim.subtract(from);
				double dist = v.length();
				v = v.normalize().scale(1.7).add(0, dist * 0.004, 0);
				IceJavelinEntity.throwFrom(level, this, from, v, cfg.javelinDamage, cfg.javelinFreezeTicks);
				javelinCooldown = cfg.javelinCooldownTicks;
			}
			if (move != null && moveTick >= move.length() && move != FrostGiantAnims.RAIDER_JAVELIN_WINDUP) {
				endMove(move == FrostGiantAnims.RAIDER_SPEAR_THRUST ? 16 : 10);
			}
			return;
		}
		if (target == null) {
			getNavigation().stop();
			return;
		}
		if (!criedOut && target instanceof Player && distanceTo(target) < 26) {
			criedOut = true;
			startMove(FrostGiantAnims.RAIDER_WAR_CRY, target);
			return;
		}
		double gap = gapTo(target);
		if (cooldown <= 0 && gap <= cfg.spearReach) {
			startMove(FrostGiantAnims.RAIDER_SPEAR_THRUST, target);
			return;
		}
		double dist = distanceTo(target);
		if (cooldown <= 0 && javelinCooldown <= 0 && target instanceof Player && dist >= cfg.javelinMinRange && dist <= cfg.javelinMaxRange
				&& hasLineOfSight(target)) {
			startMove(FrostGiantAnims.RAIDER_JAVELIN_WINDUP, target);
			return;
		}
		approach(target, 1.0);
	}

	private Vec3 handPos() {
		Vec3 side = Vec3.directionFromRotation(0, yBodyRot - 90f).scale(getBbWidth() * 0.6);
		return position().add(side).add(0, getBbHeight() * 0.85, 0);
	}
}
