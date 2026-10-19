package com.projecthero.mod.miraculous.ladybug.entity;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.miraculous.ladybug.LadybugConfig;
import com.projecthero.mod.miraculous.ladybug.LadybugMoves;
import com.projecthero.mod.miraculous.ladybug.item.LadybugItems;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: the thrown spotted TNT (Lucky Charm). Drawn as its item ({@code ThrownItemRenderer}); bursts where it lands:
 * {@link LadybugConfig#CHARM_TNT_DAMAGE} at the centre falling to half at {@link LadybugConfig#CHARM_TNT_RADIUS}, to
 * everything its thrower may hit -- never her, her squad or their pets -- and no block is broken.
 */
public class LuckyTntEntity extends ThrowableItemProjectile {
	public LuckyTntEntity(EntityType<? extends LuckyTntEntity> type, Level level) {
		super(type, level);
	}

	public LuckyTntEntity(Level level, LivingEntity owner) {
		super(LadybugEntityTypes.LUCKY_TNT, owner, level);
	}

	@Override
	protected Item getDefaultItem() {
		return LadybugItems.LUCKY_TNT;
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (level().isClientSide() || isRemoved()) {
			return;
		}
		ServerLevel level = (ServerLevel) level();
		Vec3 c = position();
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(LadybugMoves.RED, c.x, c.y + 0.5, c.z, 40, 1.5, 1.0, 1.5, 0.0);
		level.sendParticles(LadybugMoves.BLACK, c.x, c.y + 0.5, c.z, 25, 1.5, 1.0, 1.5, 0.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6f, 1.1f);
		if (getOwner() instanceof ServerPlayer owner) {
			double r = LadybugConfig.CHARM_TNT_RADIUS;
			for (LivingEntity e : AbilityHelpers.enemiesAround(owner, c, r)) {
				double d = Math.sqrt(AbilityHelpers.distanceSqToBox(e, c));
				float dmg = (float) (LadybugConfig.CHARM_TNT_DAMAGE * (1.0 - 0.5 * Math.min(1.0, d / r)));
				AbilityHelpers.hurtBurst(owner, e, level.damageSources().explosion(this, owner), dmg);
				AbilityHelpers.knockbackFrom(e, c, LadybugConfig.CHARM_TNT_KNOCKBACK);
			}
		}
		discard();
	}
}
