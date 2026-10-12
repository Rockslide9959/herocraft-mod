package com.projecthero.mod.ultron.entity;

import java.util.UUID;

import com.projecthero.mod.ultron.UltronCombat;
import com.projecthero.mod.ultron.UltronConfig;
import com.projecthero.mod.ultron.UltronFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the Ultron Sentry's <b>Fist Rocket</b> -- his own forearm, launched. It homes on its target for up to 2 s and
 * bursts for 18 damage (x1.3 like every Ultron hit). It is a target too: 60 health, and shooting it down stops it cold.
 * While it flies the Sentry's right arm is missing (the renderer reads {@link UltronSentryEntity#armOut()}).
 */
public class UltronFistRocketEntity extends UltronRobot {
	private UUID owner;
	private UUID target;
	private int flight;
	private boolean spent;

	public UltronFistRocketEntity(EntityType<? extends UltronFistRocketEntity> type, Level level) {
		super(type, level);
		this.xpReward = 0;
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, UltronConfig.sentry().fistRocketHealth)
				.add(Attributes.ARMOR, 0.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.FOLLOW_RANGE, 1.0);
	}

	@Override
	public UltronSkin skin() {
		return UltronSkin.SENTRY;
	}

	@Override
	protected void registerGoals() {
	}

	/** Arms it: who launched it, who it chases, and its health from the config. */
	public void launch(LivingEntity from, LivingEntity at, Vec3 velocity) {
		this.owner = from.getUUID();
		this.target = at == null ? null : at.getUUID();
		double hp = UltronConfig.sentry().fistRocketHealth;
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
		setHealth((float) hp);
		setDeltaMovement(velocity);
		faceAlong(velocity);
	}

	public UUID owner() {
		return owner;
	}

	private void faceAlong(Vec3 v) {
		if (v.lengthSqr() < 1.0e-6) {
			return;
		}
		float yaw = (float) (Math.atan2(v.z, v.x) * (180.0 / Math.PI)) - 90.0f;
		float pitch = (float) -(Math.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)) * (180.0 / Math.PI));
		setYRot(yaw);
		yBodyRot = yaw;
		yHeadRot = yaw;
		setXRot(pitch);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel server) || isDeadOrDying() || spent) {
			return;
		}
		setNoGravity(true);
		fallDistance = 0;
		flight++;
		UltronConfig.Sentry cfg = UltronConfig.sentry();
		Entity t = target == null ? null : server.getEntity(target);
		if (t instanceof LivingEntity le && le.isAlive()) {
			Vec3 aim = le.position().add(0, le.getBbHeight() * 0.55, 0);
			Vec3 want = aim.subtract(position().add(0, getBbHeight() * 0.5, 0));
			double len = want.length();
			if (len < 1.6) {
				detonate(server);
				return;
			}
			Vec3 v = getDeltaMovement().lerp(want.scale(1.15 / len), 0.14);
			setDeltaMovement(v);
		}
		faceAlong(getDeltaMovement());
		Vec3 tail = position().add(0, getBbHeight() * 0.5, 0).subtract(getDeltaMovement().normalize().scale(0.8));
		server.sendParticles(ParticleTypes.FLAME, tail.x, tail.y, tail.z, 2, 0.05, 0.05, 0.05, 0.01);
		server.sendParticles(UltronFx.RED_SMALL, tail.x, tail.y, tail.z, 2, 0.08, 0.08, 0.08, 0.0);
		if (flight % 4 == 0) {
			server.sendParticles(ParticleTypes.SMOKE, tail.x, tail.y, tail.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		if (flight % 10 == 1) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 0.9f, 0.5f);
		}
		if (flight >= cfg.fistRocketFlightTicks || horizontalCollision || verticalCollision && flight > 3) {
			detonate(server);
		}
	}

	/** The burst: 18 to everything (not Ultron's) within 2.2 blocks, then it's gone. */
	public void detonate(ServerLevel server) {
		if (spent) {
			return;
		}
		spent = true;
		Vec3 c = position().add(0, getBbHeight() * 0.5, 0);
		Entity o = owner == null ? null : server.getEntity(owner);
		LivingEntity from = o instanceof LivingEntity le ? le : this;
		server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		UltronFx.wreck(server, c, 1.2);
		server.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 0.8f);
		float dmg = UltronConfig.sentry().fistRocketDamage;
		for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.2), UltronCombat::canTarget)) {
			if (e.distanceToSqr(c) <= 2.2 * 2.2 + e.getBbHeight()) {
				UltronCombat.hit(server, from, e, dmg);
				Vec3 push = e.position().subtract(c);
				push = push.lengthSqr() < 1.0e-4 ? Vec3.ZERO : push.normalize().scale(0.8);
				e.push(push.x, 0.4, push.z);
				e.hurtMarked = true;
			}
		}
		discard();
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel server) {
			// shot down: it breaks up harmlessly
			spent = true;
			UltronFx.wreck(server, position().add(0, getBbHeight() * 0.5, 0), 1.0);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2f, 1.4f);
		}
	}

	@Override
	protected void tickDeath() {
		if (!level().isClientSide() && !isRemoved()) {
			remove(Entity.RemovalReason.KILLED);
		}
	}

	@Override
	public void travel(Vec3 input) {
		if (isControlledByLocalInstance()) {
			move(MoverType.SELF, getDeltaMovement());
		}
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void knockback(double strength, double x, double z) {
	}

	@Override
	public boolean shouldBeSaved() {
		return false; // a rocket in flight never survives a save
	}
}
