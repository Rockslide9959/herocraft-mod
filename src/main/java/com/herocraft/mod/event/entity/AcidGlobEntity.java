package com.herocraft.mod.event.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The Acid Zombie's projectile: a slow, arcing, clearly visible glob that deals modest direct damage
 * and leaves a short-lived acid pool where it lands.
 *
 * <h2>The pool is a vanilla {@link AreaEffectCloud}, on purpose</h2>
 * Writing a bespoke pool entity would have meant writing its own lifetime, its own renderer and its
 * own cleanup -- and "leave expired acid pools" and "excessive temporary entities" are both explicitly
 * on the spec's do-not list (sections 17 and 46). {@code AreaEffectCloud} already expires itself on a
 * fixed duration, already applies effects on a timer rather than every tick, already renders and
 * syncs efficiently, and cannot outlive its duration even if the raid it belongs to is abandoned.
 * That makes it the leak-proof choice rather than merely the cheap one.
 *
 * <p>The acid never touches terrain, which satisfies "acid should not permanently destroy terrain"
 * by construction.
 */
public class AcidGlobEntity extends ThrowableItemProjectile {
	/** Direct hit damage. Low on purpose -- the pressure is the area denial, not the bolt. */
	private static final float IMPACT_DAMAGE = 4.0f;
	private static final int POOL_DURATION_TICKS = 8 * 20;
	private static final float POOL_RADIUS = 2.0f;

	public AcidGlobEntity(EntityType<? extends AcidGlobEntity> type, Level level) {
		super(type, level);
	}

	public AcidGlobEntity(Level level, LivingEntity shooter) {
		super(RaidEntityTypes.ACID_GLOB, shooter, level);
	}

	@Override
	protected Item getDefaultItem() {
		// Only used for the fallback item-render; the entity ships its own renderer.
		return Items.SLIME_BALL;
	}

	private ParticleOptions trailParticle() {
		return ParticleTypes.ITEM_SLIME;
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel server && tickCount % 2 == 0) {
			server.sendParticles(trailParticle(), getX(), getY(), getZ(), 2, 0.05, 0.05, 0.05, 0.0);
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		Entity target = result.getEntity();
		if (target == getOwner()) {
			return;
		}
		target.hurt(damageSources().indirectMagic(this, getOwner()), IMPACT_DAMAGE);
	}


	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		spawnPool(server);
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 0.8f, 0.7f);
		server.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 20, 0.4, 0.1, 0.4, 0.05);
		discard();
	}

	/**
	 * The pool. Owned by the shooter so it never hurts the raid's own mobs, and given a fixed duration
	 * with no refresh path, so it always disappears on its own.
	 */
	private void spawnPool(ServerLevel level) {
		AreaEffectCloud pool = new AreaEffectCloud(level, getX(), getY(), getZ());
		if (getOwner() instanceof LivingEntity shooter) {
			pool.setOwner(shooter);
		}
		pool.setRadius(POOL_RADIUS);
		pool.setDuration(POOL_DURATION_TICKS);
		pool.setRadiusPerTick(-POOL_RADIUS / POOL_DURATION_TICKS); // visibly shrinks away
		pool.setWaitTime(0);
		pool.setParticle(ParticleTypes.ITEM_SLIME);
		pool.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
		pool.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
		level.addFreshEntity(pool);
	}

}
