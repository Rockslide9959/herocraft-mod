package com.projecthero.mod.thoracquire.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: a Storm Spirit, the Stormwarden's summon -- a crackling wisp of lightning (no body: the renderer draws a glowing
 * core and arcs). It drifts around its target, then darts at it; touching it deals {@link #DAMAGE} and the spirit bursts.
 * One hit kills it ({@link #HEALTH} health); it fades after {@link #LIFE} ticks.
 */
public class StormSpiritEntity extends Monster {
	public static final float DAMAGE = 5.0f;
	public static final double HEALTH = 4.0;
	public static final int LIFE = 400;
	public static final int DART_EVERY = 50;

	private int dartTicks;
	private Vec3 dartDir = Vec3.ZERO;

	public StormSpiritEntity(EntityType<? extends StormSpiritEntity> type, Level level) {
		super(type, level);
		setNoGravity(true);
		this.xpReward = 2;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FOLLOW_RANGE, 40.0);
	}

	@Override
	protected void registerGoals() {
		// all movement is in aiStep
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING)) {
			return false;
		}
		return super.hurt(source, amount);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (tickCount > LIFE) {
			burst(level);
			return;
		}
		LivingEntity t = getTarget();
		if (t == null || !t.isAlive() || t instanceof Player p && (p.getAbilities().instabuild || p.isSpectator())) {
			Player near = level.getNearestPlayer(this, 32);
			setTarget(near != null && !near.getAbilities().instabuild && !near.isSpectator() ? near : null);
			t = getTarget();
		}
		if (t == null) {
			setDeltaMovement(getDeltaMovement().scale(0.8));
			return;
		}
		Vec3 aim = t.position().add(0, t.getBbHeight() * 0.6, 0);
		if (dartTicks > 0) {
			dartTicks--;
			setDeltaMovement(dartDir.scale(0.55));
		} else {
			// circle the target 3 blocks out, bobbing
			double a = tickCount * 0.09 + getId();
			Vec3 want = aim.add(Math.cos(a) * 3.0, 1.2 + Math.sin(tickCount * 0.2) * 0.5, Math.sin(a) * 3.0);
			Vec3 to = want.subtract(position());
			double d = to.length();
			setDeltaMovement(d < 1.0e-3 ? Vec3.ZERO : to.scale(Math.min(0.28, d * 0.2) / d));
			if ((tickCount + getId()) % DART_EVERY == 0) {
				Vec3 dir = aim.subtract(position());
				dartDir = dir.lengthSqr() < 1.0e-3 ? Vec3.ZERO : dir.normalize();
				dartTicks = 14;
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.HOSTILE, 0.6f, 1.8f);
			}
		}
		hasImpulse = true;
		if (getBoundingBox().inflate(0.25).intersects(t.getBoundingBox())) {
			t.hurt(damageSources().mobAttack(this), DAMAGE);
			burst(level);
			return;
		}
		if (tickCount % 3 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.3, getZ(), 1, 0.15, 0.15, 0.15, 0.02);
		}
	}

	private void burst(ServerLevel level) {
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.3, getZ(), 20, 0.3, 0.3, 0.3, 0.2);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 0.8f, 1.6f);
		discard();
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return true;
	}
}
