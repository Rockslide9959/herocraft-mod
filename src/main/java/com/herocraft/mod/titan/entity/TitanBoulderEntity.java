package com.herocraft.mod.titan.entity;

import com.herocraft.mod.titan.TitanConfig;
import com.herocraft.mod.titan.TitanTerrain;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Titan's "ripped-up terrain" throw -- a visual illusion (spec section 10): the block the Titan
 * appeared to tear out of the ground is never actually removed, only its impact does real (bounded)
 * destruction via {@link TitanTerrain}. Renders as a plain spinning item ({@code ThrownItemRenderer},
 * same technique this mod's Acid Zombie glob already uses) rather than a bespoke model -- the spec
 * explicitly says not to spend time on a fancy projectile model.
 */
public class TitanBoulderEntity extends ThrowableItemProjectile {
	private final Item displayItem;

	public TitanBoulderEntity(EntityType<? extends TitanBoulderEntity> type, Level level) {
		super(type, level);
		this.displayItem = Items.COBBLESTONE;
	}

	public TitanBoulderEntity(Level level, LivingEntity shooter, Item displayItem) {
		super(TitanEntityTypes.TITAN_BOULDER, shooter, level);
		// "changes 23" crash fix: super()'s Entity constructor calls defineSynchedData() -> getDefaultItem()
		// BEFORE this line ever runs (a field assignment in a constructor body always happens after the
		// super() call returns), so getDefaultItem() must never depend on `displayItem` being set yet --
		// see the null-safe override below. This assignment still matters: it is what the renderer's
		// getItem() reflects for the rest of the entity's life via the explicit setItem() call.
		this.displayItem = displayItem == null ? Items.COBBLESTONE : displayItem;
		setItem(new net.minecraft.world.item.ItemStack(this.displayItem));
	}

	/** Picks a boulder "flavour" item off whatever block the Titan is standing on -- purely cosmetic. */
	public static Item itemForGround(net.minecraft.world.level.block.state.BlockState ground) {
		var block = ground.getBlock();
		if (block == Blocks.SAND || block == Blocks.RED_SAND) {
			return Items.SANDSTONE;
		}
		if (block == Blocks.GRASS_BLOCK || block == Blocks.DIRT || block == Blocks.PODZOL || block == Blocks.MYCELIUM) {
			return Items.DIRT;
		}
		return Items.COBBLESTONE;
	}

	/**
	 * Never allowed to return null. {@code Entity}'s own constructor calls this (via
	 * {@code defineSynchedData}) before either constructor above has had a chance to assign
	 * {@link #displayItem} -- returning it unguarded crashed the server with an NPE the instant a Titan
	 * ever tried a Boulder attack (a null {@code Item} reaching {@code new ItemStack(Item)}).
	 */
	@Override
	protected Item getDefaultItem() {
		return displayItem == null ? Items.COBBLESTONE : displayItem;
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel server && tickCount % 2 == 0) {
			server.sendParticles(ParticleTypes.POOF, getX(), getY(), getZ(), 1, 0.08, 0.08, 0.08, 0.0);
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		Entity target = result.getEntity();
		if (target == getOwner()) {
			return;
		}
		double dmg = TitanConfig.attacks().boulderDamage;
		target.hurt(damageSources().mobProjectile(this, getOwner() instanceof LivingEntity le ? le : null), (float) dmg);
		if (target instanceof LivingEntity living) {
			living.knockback(1.6, getX() - target.getX(), getZ() - target.getZ());
		}
		impact();
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		impact();
	}

	private boolean impacted;

	private void impact() {
		if (impacted || !(level() instanceof ServerLevel server)) {
			return;
		}
		impacted = true;
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 0.7f);
		server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(), getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		server.sendParticles(ParticleTypes.POOF, getX(), getY(), getZ(), 40, 1.0, 0.6, 1.0, 0.08);
		// AoE blast: everything in radius takes boulder damage, falling off with distance, so a near-miss
		// still hurts. This is what makes the Titan's ranged attack an area attack, not a single-target one.
		double radius = TitanConfig.attacks().boulderAoeRadius;
		double baseDamage = TitanConfig.attacks().boulderDamage;
		var owner = getOwner() instanceof LivingEntity le ? le : null;
		for (LivingEntity victim : server.getEntitiesOfClass(LivingEntity.class,
				getBoundingBox().inflate(radius), e -> e.isAlive() && e != owner)) {
			double d = Math.sqrt(victim.distanceToSqr(getX(), getY(), getZ()));
			if (d > radius) {
				continue;
			}
			float dmg = (float) (baseDamage * (1.0 - 0.6 * (d / radius)));
			victim.hurt(damageSources().mobProjectile(this, owner), dmg);
			Vec3 push = victim.position().subtract(position()).normalize();
			victim.setDeltaMovement(victim.getDeltaMovement().add(push.x * 1.2, 0.45, push.z * 1.2));
			victim.hurtMarked = true;
		}
		TitanTerrain.breakCluster(server, blockPosition(), TitanConfig.attacks().boulderImpactRadius);
		discard();
	}
}
