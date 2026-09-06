package com.projecthero.mod.event.entity;

import com.projecthero.mod.event.EventConfig;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The heavy. Visibly larger than a normal zombie, very hard to kill, hits hard, throws players a long
 * way, and moves slowly enough that its threat is positional rather than a chase.
 *
 * <h2>The charge</h2>
 * A three-stage cycle with an explicitly readable wind-up (spec section 17: "clearly telegraphed",
 * "must be dodgeable"):
 * <ol>
 *   <li><b>Wind-up, {@value #WINDUP_TICKS} ticks.</b> It roots itself, roars, and throws up angry
 *       particles. The player has a full second and a quarter to move.</li>
 *   <li><b>Rush, up to {@value #CHARGE_TICKS} ticks.</b> Locked to the direction it committed to at
 *       the end of the wind-up -- it does <em>not</em> home in mid-charge, which is what makes
 *       sidestepping work.</li>
 *   <li><b>Recovery.</b> Cooldown before it may charge again.</li>
 * </ol>
 * Anything it runs into during the rush takes the charge damage and a large knockback, once per
 * charge.
 *
 * <h2>No terrain destruction</h2>
 * The design allows "optionally break specifically allowed weak blocks" but says to disable
 * environmental destruction entirely if limited, reliable destruction cannot be guaranteed. There is
 * no way to bound "weak blocks" that does not eventually eat part of someone's base -- a raid centred
 * on a player's home would be exactly where it happened -- so this Juggernaut breaks nothing at all.
 * That is a deliberate design decision, not an omission.
 */
public class JuggernautZombie extends RaidUndead {
	private static final int WINDUP_TICKS = 25;
	private static final int CHARGE_TICKS = 22;
	private static final int CHARGE_COOLDOWN_TICKS = 7 * 20;
	private static final double CHARGE_SPEED = 0.72;
	private static final double CHARGE_TRIGGER_MIN = 5.0;
	private static final double CHARGE_TRIGGER_MAX = 16.0;
	private static final float CHARGE_DAMAGE = 9.0f;
	private static final double CHARGE_KNOCKBACK = 2.4;
	/** Renders and collides at this multiple of a normal zombie. */
	public static final float SCALE = 1.55f;

	private int windupTicks;
	private int chargeTicks;
	private int chargeCooldown;
	private Vec3 chargeDirection = Vec3.ZERO;
	private boolean chargeConnected;

	public JuggernautZombie(EntityType<? extends JuggernautZombie> type, Level level) {
		super(type, level);
		this.xpReward = 20;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Zombie.createAttributes()
				.add(Attributes.MAX_HEALTH, 120.0)
				.add(Attributes.ATTACK_DAMAGE, 8.0)
				.add(Attributes.MOVEMENT_SPEED, 0.19)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.ARMOR, 10.0)
				.add(Attributes.ARMOR_TOUGHNESS, 4.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.85)
				.add(Attributes.ATTACK_KNOCKBACK, 1.6);
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return super.getDefaultDimensions(pose).scale(SCALE);
	}

	@Override
	public boolean isBaby() {
		return false;
	}

	@Override
	public void aiStep() {
		if (!level().isClientSide()) {
			tickCharge();
		}
		super.aiStep();
	}

	private void tickCharge() {
		if (chargeCooldown > 0) {
			chargeCooldown--;
		}

		if (windupTicks > 0) {
			windupTicks--;
			// Rooted during the tell so the wind-up genuinely is a window to move out of the way.
			setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
			getNavigation().stop();
			if (level() instanceof ServerLevel server && windupTicks % 4 == 0) {
				server.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + getBbHeight(), getZ(),
						2, 0.4, 0.2, 0.4, 0.0);
				server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(),
						4, 0.5, 0.05, 0.5, 0.01);
			}
			if (windupTicks == 0) {
				beginCharge();
			}
			return;
		}

		if (chargeTicks > 0) {
			chargeTicks--;
			setDeltaMovement(chargeDirection.x * CHARGE_SPEED, getDeltaMovement().y, chargeDirection.z * CHARGE_SPEED);
			hasImpulse = true;
			if (level() instanceof ServerLevel server) {
				server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 2, 0.3, 0.05, 0.3, 0.0);
			}
			if (!chargeConnected) {
				resolveChargeImpact();
			}
			if (chargeTicks == 0) {
				chargeCooldown = chargeCooldownTicks();
			}
			return;
		}

		maybeStartWindup();
	}

	private int chargeCooldownTicks() {
		double multiplier = Math.max(0.25, EventConfig.raid().bossCooldownMultiplier);
		return (int) Math.max(20, CHARGE_COOLDOWN_TICKS * multiplier);
	}

	private void maybeStartWindup() {
		if (chargeCooldown > 0 || !onGround()) {
			return;
		}
		LivingEntity target = getTarget();
		if (target == null || !target.isAlive() || !hasLineOfSight(target)) {
			return;
		}
		double d = distanceTo(target);
		if (d < CHARGE_TRIGGER_MIN || d > CHARGE_TRIGGER_MAX) {
			return;
		}
		windupTicks = WINDUP_TICKS;
		chargeConnected = false;
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.2f, 0.6f);
	}

	private void beginCharge() {
		LivingEntity target = getTarget();
		Vec3 dir = target != null
				? new Vec3(target.getX() - getX(), 0.0, target.getZ() - getZ())
				: Vec3.directionFromRotation(0.0f, getYRot());
		// Direction is fixed here and never recomputed: the charge is a committed rush, not a homing
		// missile. Sidestepping it has to work.
		chargeDirection = dir.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0.0f, getYRot()) : dir.normalize();
		chargeTicks = CHARGE_TICKS;
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.4f, 0.5f);
	}

	/** One hit per charge, on whatever the rush actually runs into. */
	private void resolveChargeImpact() {
		for (Entity entity : level().getEntities(this, getBoundingBox().inflate(0.4),
				e -> e instanceof LivingEntity && e.isAlive() && !(e instanceof RaidUndead)
						&& !(e instanceof SwordSkeleton))) {
			entity.hurt(damageSources().mobAttack(this), CHARGE_DAMAGE);
			// A Gravekeeper Shield is specifically meant to hold a Juggernaut charge, so its resistance is
			// applied to the push itself -- the charge does not go through the knockback attribute.
			double resisted = CHARGE_KNOCKBACK
					* com.projecthero.mod.grave.GraveboundEvents.chargeKnockbackFactor(entity);
			Vec3 push = chargeDirection.scale(resisted);
			entity.setDeltaMovement(entity.getDeltaMovement().add(push.x, 0.55 * Math.max(0.15, resisted / CHARGE_KNOCKBACK), push.z));
			entity.hurtMarked = true;
			chargeConnected = true;
			level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK,
					SoundSource.HOSTILE, 1.0f, 0.6f);
			chargeTicks = Math.min(chargeTicks, 4); // it stops shortly after connecting
			return;
		}
	}

	/** True while the charge tell is showing -- read by the renderer for the wind-up pose. */
	public boolean isWindingUp() {
		return windupTicks > 0;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("ChargeCooldown", chargeCooldown);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		chargeCooldown = tag.getInt("ChargeCooldown");
	}
}
