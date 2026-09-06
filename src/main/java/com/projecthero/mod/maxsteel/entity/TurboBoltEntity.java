package com.projecthero.mod.maxsteel.entity;

import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.maxsteel.MaxSteelConfig;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Turbo Blast bolt (v0.9.2). Turbo Blast used to be an instant hitscan; it is now a real, fast
 * cyan T.U.R.B.O. projectile -- a thick energy bolt with a slightly-larger-than-a-snowball hitbox
 * that flies dead straight (inertia 1.0, no gravity) and, on a full charge, detonates a small
 * entity-only impact burst. Terrain is never damaged.
 *
 * <p>Server-authoritative: only {@link com.projecthero.mod.maxsteel.MaxSteelBlast} spawns it and only
 * this class resolves the hit; a modified client cannot claim the kill.
 */
public class TurboBoltEntity extends AbstractHurtingProjectile {
	private int life;
	private float damage = MaxSteelConfig.BLAST_DAMAGE;
	private float charge; // 0..1
	private float knockback = 0.35f;

	public TurboBoltEntity(EntityType<? extends TurboBoltEntity> type, Level level) {
		super(type, level);
		this.accelerationPower = 0.0; // both sides: no vanilla accelerate-from-slow (also the client ctor)
	}

	public TurboBoltEntity(Level level, LivingEntity owner, Vec3 movement) {
		super(MaxSteelEntityTypes.TURBO_BOLT, owner, movement, level);
		this.accelerationPower = 0.0; // v0.9.4: no vanilla accelerate-from-slow -- see tick()
	}

	/** Set once, right after construction, before spawning. */
	public TurboBoltEntity configure(float damage, float charge) {
		this.damage = damage;
		this.charge = Math.max(0f, Math.min(1f, charge));
		this.knockback = 0.35f + 0.4f * this.charge;
		return this;
	}

	@Override
	protected boolean shouldBurn() {
		return false;
	}

	@Override
	protected float getInertia() {
		return 1.0f; // v0.9.4: no decay at all -- speed is held constant in tick()
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	public void tick() {
		if (!level().isClientSide()) {
			// v0.9.4: hold the bolt at a constant cruising speed the entire flight (keeps its direction,
			// including after a deflection) so it never reads as a slow lob off the muzzle.
			Vec3 v = getDeltaMovement();
			double len = v.length();
			if (len > 1.0e-4 && Math.abs(len - MaxSteelConfig.BLAST_PROJECTILE_SPEED) > 0.02) {
				setDeltaMovement(v.scale(MaxSteelConfig.BLAST_PROJECTILE_SPEED / len));
			}
		}
		super.tick();
		if (level().isClientSide()) {
			level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0, 0, 0);
			level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY(), getZ(), 0, 0, 0);
			return;
		}
		if (++life > MaxSteelConfig.BLAST_PROJECTILE_LIFE_TICKS) {
			discard();
			return;
		}
		ServerLevel level = (ServerLevel) level();
		float spread = 0.1f + 0.12f * charge;
		level.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 2 + (int) (charge * 3),
				spread, spread, spread, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 3 + (int) (charge * 4),
				spread, spread, spread, 0.02);
	}

	private boolean canTargetLiving(LivingEntity e) {
		if (!e.isAlive() || e == getOwner() || e instanceof ArmorStand) {
			return false;
		}
		if (e instanceof Player) {
			return HeroConfig.get().abilityPvpDamage
					&& level().getServer() != null && level().getServer().isPvpAllowed();
		}
		return true;
	}

	@Override
	protected boolean canHitEntity(net.minecraft.world.entity.Entity entity) {
		return super.canHitEntity(entity)
				&& (!(entity instanceof LivingEntity le) || canTargetLiving(le));
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (level().isClientSide() || !(hit.getEntity() instanceof LivingEntity target) || !canTargetLiving(target)) {
			return;
		}
		if (getOwner() instanceof net.minecraft.server.level.ServerPlayer shooter) {
			AbilityHelpers.hurt(shooter, target, damage);
			AbilityHelpers.knockbackFrom(target, position(), knockback);
		} else {
			target.hurt(damageSources().mobProjectile(this, getOwner() instanceof LivingEntity o ? o : null), damage);
		}
		detonate(target);
	}

	@Override
	protected void onHitBlock(net.minecraft.world.phys.BlockHitResult hit) {
		super.onHitBlock(hit);
		detonate(null);
	}

	@Override
	protected void onHit(HitResult hit) {
		if (hit.getType() != HitResult.Type.MISS) {
			super.onHit(hit);
		}
	}

	private void detonate(LivingEntity directHit) {
		if (level().isClientSide() || !isAlive()) {
			return;
		}
		ServerLevel level = (ServerLevel) level();
		Vec3 at = position();
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 10 + (int) (charge * 10), 0.14, 0.14, 0.14, 0.05);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 12 + (int) (charge * 12), 0.2, 0.2, 0.2, 0.1);

		if (charge >= 0.98f && getOwner() instanceof net.minecraft.server.level.ServerPlayer shooter) {
			for (LivingEntity e : AbilityHelpers.enemiesAround(shooter, at, MaxSteelConfig.CHARGED_BLAST_BURST_RADIUS)) {
				if (e == directHit) {
					continue;
				}
				AbilityHelpers.hurt(shooter, e, MaxSteelConfig.CHARGED_BLAST_MAX_DAMAGE * 0.5f);
				AbilityHelpers.knockbackFrom(e, at, 0.5);
			}
			level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS,
				0.5f, charge > 0.4f ? 0.7f : 1.5f);
		discard();
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.contains("Damage")) {
			damage = tag.getFloat("Damage");
		}
		charge = tag.getFloat("Charge");
		knockback = tag.contains("Knockback") ? tag.getFloat("Knockback") : 0.35f;
		life = tag.getInt("Life");
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putFloat("Damage", damage);
		tag.putFloat("Charge", charge);
		tag.putFloat("Knockback", knockback);
		tag.putInt("Life", life);
	}
}
