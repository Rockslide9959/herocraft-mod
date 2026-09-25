package com.projecthero.mod.spider.entity;

import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.spider.SpiderCombat;
import com.projecthero.mod.spider.SpiderWebs;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Combat Mode's Impact Web (v0.12.20): one heavy, fast, straight-flying ball of webbing. On an entity it deals
 * {@link SpiderCombat#IMPACT_DAMAGE} and a hard knockback, and wraps it in a web cocoon for twelve seconds
 * ({@link SpiderCombat#IMPACT_PIN_TICKS}); if the knockback also slams the target into a block,
 * {@link SpiderCombat#watchImpact} pins it to that wall and renews the cocoon. Terrain is never touched.
 *
 * <p>Server-authoritative, like {@code TurboBoltEntity}: only {@link SpiderCombat#impactWeb} spawns it and only
 * this class resolves the hit.
 */
public class ImpactWebEntity extends AbstractHurtingProjectile {
	private int life;

	public ImpactWebEntity(EntityType<? extends ImpactWebEntity> type, Level level) {
		super(type, level);
		this.accelerationPower = 0.0;
	}

	public ImpactWebEntity(Level level, LivingEntity owner, Vec3 movement) {
		super(SpiderEntityTypes.IMPACT_WEB, owner, movement, level);
		this.accelerationPower = 0.0;
	}

	@Override
	protected boolean shouldBurn() {
		return false;
	}

	@Override
	protected float getInertia() {
		return 1.0f;
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	public void tick() {
		if (!level().isClientSide()) {
			Vec3 v = getDeltaMovement();
			double len = v.length();
			if (len > 1.0e-4 && Math.abs(len - SpiderCombat.IMPACT_SPEED) > 0.02) {
				setDeltaMovement(v.scale(SpiderCombat.IMPACT_SPEED / len));
			}
		}
		super.tick();
		if (level().isClientSide()) {
			level().addParticle(ParticleTypes.ITEM_COBWEB, getX(), getY(), getZ(), 0, 0, 0);
			return;
		}
		if (++life > SpiderCombat.IMPACT_LIFE_TICKS) {
			discard();
			return;
		}
		((ServerLevel) level()).sendParticles(ParticleTypes.ITEM_COBWEB, getX(), getY(), getZ(), 2, 0.12, 0.12, 0.12, 0.0);
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
	protected boolean canHitEntity(Entity entity) {
		return super.canHitEntity(entity) && (!(entity instanceof LivingEntity le) || canTargetLiving(le));
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (level().isClientSide() || !(hit.getEntity() instanceof LivingEntity target) || !canTargetLiving(target)
				|| !(getOwner() instanceof ServerPlayer shooter)) {
			return;
		}
		Vec3 origin = position();
		AbilityHelpers.hurt(shooter, target, SpiderCombat.IMPACT_DAMAGE);
		AbilityHelpers.knockbackFrom(target, origin, SpiderCombat.IMPACT_KNOCKBACK);
		// v0.12.32: the ball itself cocoons whatever it hits (12 s), wall or no wall.
		SpiderWebs.cocoonFor(shooter, target, SpiderCombat.IMPACT_PIN_TICKS);
		SpiderCombat.watchImpact(shooter, target, getDeltaMovement());
		splat(origin);
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		super.onHitBlock(hit);
		if (!level().isClientSide()) {
			splat(hit.getLocation());
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		if (hit.getType() != HitResult.Type.MISS) {
			super.onHit(hit);
		}
	}

	private void splat(Vec3 at) {
		if (!isAlive()) {
			return;
		}
		ServerLevel level = (ServerLevel) level();
		level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 24, 0.35, 0.35, 0.35, 0.05);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.03);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 0.7f);
		discard();
	}

	@Override
	public boolean isPickable() {
		return false;
	}
}
