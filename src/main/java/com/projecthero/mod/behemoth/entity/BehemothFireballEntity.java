package com.projecthero.mod.behemoth.entity;

import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Abyssal Behemoth's own fireball -- shared by Abyssal Fireball, Hellfire Barrage and Skyfall (spec
 * sections 7, 8 and 9), which differ only in count, spread, speed, damage and whether {@link #homing}
 * is set. Extends vanilla {@link Fireball} (what a Ghast's own fireball is) rather than reusing
 * {@code LargeFireball} directly, purely so it renders through vanilla's own {@code ThrownItemRenderer}
 * (a spinning fire charge -- see {@code ModEntityRenderers}) without a bespoke model; its damage,
 * explosion size and in-flight tracking are this boss's own numbers, never a Ghast's.
 *
 * <p>Impact never breaks blocks ({@code Level.ExplosionInteraction.MOB}, the same choice vanilla's own
 * Ghast fireball makes) -- the Nether floor around a Behemoth fight should not slowly disappear over a
 * long encounter.
 */
public class BehemothFireballEntity extends Fireball {
	private float damage = 20.0f;
	private float explosionRadius = 2.0f;
	/** Skyfall only: nudges its course toward the target a few times in flight instead of snapping onto it. */
	private boolean homing;
	private int homingPulsesLeft;
	private boolean exploded;

	public BehemothFireballEntity(EntityType<? extends BehemothFireballEntity> type, Level level) {
		super(type, level);
	}

	public BehemothFireballEntity(Level level, LivingEntity owner, Vec3 direction, double speed, float damage, float explosionRadius, boolean homing) {
		super(BehemothEntityTypes.BEHEMOTH_FIREBALL, owner, direction.normalize().scale(speed), level);
		this.setItem(new ItemStack(Items.FIRE_CHARGE));
		this.damage = damage;
		this.explosionRadius = explosionRadius;
		this.homing = homing;
		this.homingPulsesLeft = homing ? 50 : 0;
	}

	@Override
	protected float getInertia() {
		return 1.0f; // no vanilla fireball drag -- these are meant to feel like they carry real force
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (homing && homingPulsesLeft > 0 && tickCount % 4 == 0
				&& getOwner() instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()) {
			homingPulsesLeft--;
			Vec3 toTarget = mob.getTarget().position().add(0, mob.getTarget().getBbHeight() * 0.5, 0).subtract(position()).normalize();
			Vec3 blended = getDeltaMovement().normalize().scale(0.85).add(toTarget.scale(0.15)).normalize().scale(getDeltaMovement().length());
			setDeltaMovement(blended);
			hasImpulse = true;
		}
		server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.0);
		server.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 2, 0.08, 0.08, 0.08, 0.01);
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		Entity target = result.getEntity();
		if (target == getOwner()) {
			return;
		}
		if (target instanceof LivingEntity living) {
			float dmg = TitanCombat.isBoss(living) ? Math.min(damage, (float) (living.getMaxHealth() * 0.10)) : damage;
			target.hurt(damageSources().mobProjectile(this, getOwner() instanceof LivingEntity le ? le : null), dmg);
		}
		explodeAt(server);
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (level() instanceof ServerLevel server && result.getType() != HitResult.Type.ENTITY) {
			explodeAt(server);
		}
	}

	private void explodeAt(ServerLevel server) {
		if (exploded) {
			return;
		}
		exploded = true;
		server.explode(this, null, null, getX(), getY(), getZ(), explosionRadius, false, Level.ExplosionInteraction.MOB);
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.2f, 0.75f);
		server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		discard();
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 65536.0; // 256 blocks -- a big, dangerous projectile should stay visible from far off
	}
}
