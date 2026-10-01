package com.projecthero.mod.horde.entity;

import com.projecthero.mod.hero.power.TempBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * v0.14.12: the web a Spider Horde spider (or the Brood Queen) spits. A small hit, a heavy slow, and a cobweb that
 * pins whoever it lands on for a few seconds -- placed through {@link TempBlocks}, so every web cleans itself up and
 * none outlives a raid. Never hurts spiders.
 */
public class WebShotEntity extends ThrowableItemProjectile {
	private static final float DAMAGE = 2.0f;
	private static final int WEB_TICKS = 5 * 20;
	/** The Brood Queen's are bigger: more damage, a 3x3 patch of web. */
	private boolean queen;

	public WebShotEntity(EntityType<? extends WebShotEntity> type, Level level) {
		super(type, level);
	}

	public WebShotEntity(Level level, LivingEntity shooter, boolean queen) {
		super(HordeEntityTypes.WEB_SHOT, shooter, level);
		this.queen = queen;
	}

	@Override
	protected Item getDefaultItem() {
		return Items.COBWEB;
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel server && tickCount % 2 == 0) {
			server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COBWEB)),
					getX(), getY(), getZ(), 1, 0.03, 0.03, 0.03, 0.0);
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		Entity target = result.getEntity();
		if (target == getOwner() || target instanceof Spider) {
			return;
		}
		target.hurt(damageSources().mobProjectile(this, getOwner() instanceof LivingEntity l ? l : null), queen ? DAMAGE * 3 : DAMAGE);
		if (target instanceof LivingEntity living) {
			living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, queen ? 80 : 50, queen ? 3 : 2));
		}
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		BlockPos at = result instanceof EntityHitResult e ? e.getEntity().blockPosition() : BlockPos.containing(result.getLocation());
		int r = queen ? 1 : 0;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, 0, -r), at.offset(r, queen ? 1 : 0, r))) {
			if (server.getBlockState(p).isAir()) {
				TempBlocks.place(server, p.immutable(), Blocks.COBWEB.defaultBlockState(), WEB_TICKS);
			}
		}
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.HOSTILE, 0.7f, 1.6f);
		server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COBWEB)),
				getX(), getY(), getZ(), 12, 0.3, 0.3, 0.3, 0.05);
		discard();
	}
}
