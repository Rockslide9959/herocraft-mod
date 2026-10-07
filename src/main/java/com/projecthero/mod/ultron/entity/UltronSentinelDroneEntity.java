package com.projecthero.mod.ultron.entity;

import com.projecthero.mod.ultron.UltronCombat;
import com.projecthero.mod.ultron.UltronConfig;
import com.projecthero.mod.ultron.UltronFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.12: an <b>Ultron Sentinel Drone</b> -- the melee runner. Fast on the ground (45 health), it sprints in and dives at
 * its prey for claw strikes. When it is destroyed it beeps three times and blows up (6 damage round it, no terrain
 * damage) -- step away when you hear it.
 */
public class UltronSentinelDroneEntity extends UltronRobot {
	public static final int FUSE_TICKS = 18;

	public UltronSentinelDroneEntity(EntityType<? extends UltronSentinelDroneEntity> type, Level level) {
		super(type, level);
		this.xpReward = 7;
	}

	public static AttributeSupplier.Builder createAttributes() {
		UltronConfig.SentinelDrone cfg = UltronConfig.sentinelDrone();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, cfg.health)
				.add(Attributes.ARMOR, cfg.armor)
				.add(Attributes.ATTACK_DAMAGE, cfg.clawDamage)
				.add(Attributes.MOVEMENT_SPEED, cfg.speed)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	public UltronSkin skin() {
		return UltronSkin.SENTINEL_DRONE;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.5f));
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.3, true));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.9));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12.0f));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this, UltronRobot.class));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		boolean hit = super.doHurtTarget(target);
		if (hit && target instanceof net.minecraft.server.level.ServerPlayer sp) {
			UltronCombat.drainSuit(sp, (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
		}
		if (hit && level() instanceof ServerLevel server) {
			server.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 4, 0.2, 0.2, 0.2, 0.1);
		}
		return hit;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (isNoAi()) {
			return;
		}
		LivingEntity t = getTarget();
		setAction(t != null && distanceToSqr(t) < 16 ? ACTION_AIM : ACTION_NONE);
	}

	@Override
	protected void tickDeath() {
		++deathTime;
		if (!(level() instanceof ServerLevel server) || isRemoved()) {
			return;
		}
		if (deathTime == 1 || deathTime == 7 || deathTime == 13) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.HOSTILE, 1.4f, 1.9f);
			server.sendParticles(UltronFx.RED, getX(), getY() + 1.0, getZ(), 6, 0.3, 0.3, 0.3, 0.0);
		}
		if (deathTime >= FUSE_TICKS) {
			explode(server);
			remove(Entity.RemovalReason.KILLED);
		}
	}

	/** The death blast: damage round it (not to other robots), no blocks broken. */
	void explode(ServerLevel server) {
		UltronConfig.SentinelDrone cfg = UltronConfig.sentinelDrone();
		Vec3 c = position().add(0, 0.8, 0);
		server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		UltronFx.wreck(server, c, 1.0);
		server.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2f, 1.2f);
		DamageSource src = server.damageSources().explosion(this, this);
		for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(cfg.explosionRadius), UltronCombat::canTarget)) {
			if (e.distanceToSqr(c) <= cfg.explosionRadius * cfg.explosionRadius) {
				UltronCombat.hit(server, src, e, cfg.explosionDamage);
				Vec3 push = e.position().subtract(c).normalize().scale(0.6);
				e.push(push.x, 0.35, push.z);
				e.hurtMarked = true;
			}
		}
	}
}
