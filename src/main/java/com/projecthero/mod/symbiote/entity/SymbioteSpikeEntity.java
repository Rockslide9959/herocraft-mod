package com.projecthero.mod.symbiote.entity;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.symbiote.SymbioteSounds;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.19: a Symbiote Spike -- a real projectile now, with its own model ({@code SymbioteSpikeRenderer}),
 * instead of a particle line that looked exactly like Tendril Strike. Shot from the host's hand by G (one)
 * and Shift+G (a fan of five). It flies fast and almost flat, so it can miss: whatever it hits first takes
 * {@link #DAMAGE} and is poisoned with Wither V for {@link #WITHER_TICKS}.
 */
public class SymbioteSpikeEntity extends ThrowableProjectile {
	public static final float DAMAGE = 14.0f;
	/** Wither V = amplifier 4. */
	public static final int WITHER_AMPLIFIER = 4;
	public static final int WITHER_TICKS = 100;
	public static final float SPEED = 3.2f;
	private static final int MAX_LIFE_TICKS = 40;
	/** v0.14.25: Carnage's spikes are red (and weaker -- he fires them in fans). */
	private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_CRIMSON =
			SynchedEntityData.defineId(SymbioteSpikeEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
	private float damage = DAMAGE;
	private int witherAmplifier = WITHER_AMPLIFIER;
	private int witherTicks = WITHER_TICKS;

	public SymbioteSpikeEntity(EntityType<? extends SymbioteSpikeEntity> type, Level level) {
		super(type, level);
	}

	/** Fire one spike from the host's hand along {@code dir}. */
	public static SymbioteSpikeEntity shoot(ServerPlayer owner, Vec3 from, Vec3 dir) {
		SymbioteSpikeEntity spike = new SymbioteSpikeEntity(SymbioteEntityTypes.SPIKE, owner.level());
		spike.setOwner(owner);
		spike.setPos(from.x, from.y, from.z);
		spike.shoot(dir.x, dir.y, dir.z, SPEED, 0.0f);
		owner.level().addFreshEntity(spike);
		return spike;
	}

	/** v0.14.25: a crimson spike from any living owner (Carnage): {@code damage} on a hit and a short Wither II. */
	public static SymbioteSpikeEntity shootCrimson(LivingEntity owner, Vec3 from, Vec3 dir, float damage, float speed) {
		SymbioteSpikeEntity spike = new SymbioteSpikeEntity(SymbioteEntityTypes.SPIKE, owner.level());
		spike.setOwner(owner);
		spike.entityData.set(DATA_CRIMSON, true);
		spike.damage = damage;
		spike.witherAmplifier = 1;
		spike.witherTicks = 60;
		spike.setPos(from.x, from.y, from.z);
		spike.shoot(dir.x, dir.y, dir.z, speed, 0.0f);
		owner.level().addFreshEntity(spike);
		return spike;
	}

	public boolean crimson() {
		return entityData.get(DATA_CRIMSON);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_CRIMSON, false);
	}

	@Override
	protected double getDefaultGravity() {
		return 0.008;
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide && tickCount > MAX_LIFE_TICKS) {
			discard();
			return;
		}
		if (level().isClientSide && tickCount % 2 == 0) {
			level().addParticle(crimson() ? new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.8f, 0.05f, 0.08f), 0.9f)
					: ParticleTypes.SQUID_INK, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
		}
	}

	@Override
	protected boolean canHitEntity(Entity target) {
		// v0.13.21: a spike flies straight past its owner's squadmates
		if (crimson() && getOwner() instanceof com.projecthero.mod.carnage.entity.CarnageEntity
				&& (target instanceof com.projecthero.mod.carnage.entity.CarnageEntity || target instanceof com.projecthero.mod.carnage.entity.CrimsonSpawnEntity)) {
			return false; // Carnage never spikes his own spawn
		}
		return super.canHitEntity(target) && target != getOwner()
				&& !com.projecthero.mod.squad.Squads.areAllies(getOwner(), target);
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		if (!(level() instanceof ServerLevel level) || !(result.getEntity() instanceof LivingEntity target)) {
			return;
		}
		Entity owner = getOwner();
		boolean hit;
		if (owner instanceof ServerPlayer player) {
			hit = AbilityHelpers.hurtLands(player, target, damage);
		} else {
			hit = target.hurt(damageSources().thrown(this, owner), damage);
		}
		if (hit) {
			target.addEffect(new MobEffectInstance(MobEffects.WITHER, witherTicks, witherAmplifier, false, true, true), owner);
			Vec3 v = getDeltaMovement();
			double len = Math.max(1.0e-4, v.horizontalDistance());
			target.knockback(0.35, -v.x / len, -v.z / len);
		}
		level.sendParticles(ParticleTypes.SQUID_INK, target.getX(), target.getY() + target.getBbHeight() * 0.5,
				target.getZ(), 12, 0.25, 0.3, 0.25, 0.04);
		level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5,
				target.getZ(), 6, 0.2, 0.2, 0.2, 0.2);
		SymbioteSounds.organic(level, getX(), getY(), getZ(), 0.7f, 1.3f);
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		if (level() instanceof ServerLevel level) {
			Vec3 p = result.getLocation();
			level.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 6, 0.1, 0.1, 0.1, 0.02);
		}
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (!level().isClientSide) {
			discard();
		}
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
