package com.projecthero.mod.hero.power.p22;

import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.squad.Squads;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.22 (batch E): a thorn spat by a {@link ThornSentryEntity}. Flies almost flat, hits for a few points plus a
 * short Poison, and can never hit its owner, a squad-mate, a sentry, or (without PvP) any player.
 */
public class ThornProjectile extends ThrowableProjectile {
	private float damage = ThornSentryEntity.THORN_DAMAGE;

	public ThornProjectile(EntityType<? extends ThornProjectile> type, Level level) {
		super(type, level);
	}

	/** Spawns a thorn at {@code from} flying at {@code at}. */
	public static ThornProjectile fire(ServerLevel level, ServerPlayer owner, Entity shooter, Vec3 from, Vec3 at, float damage) {
		ThornProjectile t = new ThornProjectile(PlantEntities.THORN, level);
		t.setPos(from.x, from.y, from.z);
		t.setOwner(owner);
		t.damage = damage;
		Vec3 d = at.subtract(from);
		t.shoot(d.x, d.y, d.z, 1.7f, 0.5f);
		level.addFreshEntity(t);
		return t;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	protected double getDefaultGravity() {
		return 0.005;
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			if (tickCount > 60) {
				discard();
			} else if (tickCount % 2 == 0 && level() instanceof ServerLevel sl) {
				sl.sendParticles(ParticleTypes.COMPOSTER, getX(), getY(), getZ(), 1, 0.02, 0.02, 0.02, 0.0);
			}
		}
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		if (!super.canHitEntity(e) || e instanceof ThornSentryEntity || e instanceof ThornProjectile) {
			return false;
		}
		Entity owner = getOwner();
		if (e == owner || Squads.areAllies(owner, e)) {
			return false;
		}
		if (e instanceof Player) {
			return owner instanceof ServerPlayer sp && sp.getServer() != null && sp.getServer().isPvpAllowed()
					&& HeroConfig.get().abilityPvpDamage;
		}
		return true;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (level().isClientSide()) {
			return;
		}
		if (hit.getEntity() instanceof LivingEntity le) {
			if (getOwner() instanceof ServerPlayer sp) {
				AbilityHelpers.hurt(sp, le, damage);
			} else {
				le.hurt(damageSources().thrown(this, getOwner()), damage);
			}
			AbilityHelpers.applyControl(le, MobEffects.POISON, 50, 0);
			if (level() instanceof ServerLevel sl) {
				sl.sendParticles(ParticleTypes.COMPOSTER, getX(), getY(), getZ(), 8, 0.15, 0.15, 0.15, 0.05);
			}
		}
		discard();
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		super.onHitBlock(hit);
		if (!level().isClientSide()) {
			if (level() instanceof ServerLevel sl) {
				sl.sendParticles(ParticleTypes.COMPOSTER, getX(), getY(), getZ(), 4, 0.1, 0.1, 0.1, 0.02);
			}
			discard();
		}
	}
}
