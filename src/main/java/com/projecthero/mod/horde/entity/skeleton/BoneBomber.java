package com.projecthero.mod.horde.entity.skeleton;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * v0.14.16 (the Skeleton Horde's acid spitter): a charred, ember-cracked skeleton with a block of TNT strapped on for a
 * head. It draws slowly (smoke pours off it while it aims) and fires <b>Blast Arrows</b> -- burning arrows that burst
 * where they land, hurting everyone in 2.5 blocks (but never the horde). When it dies, it goes off itself.
 */
public class BoneBomber extends HordeSkeleton {
	static final double DEATH_BLAST_RADIUS = 3.0;
	static final float DEATH_BLAST_DAMAGE = 8.0f;

	public BoneBomber(EntityType<? extends BoneBomber> type, Level level) {
		super(type, level);
		this.xpReward = 10;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return AbstractSkeleton.createAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FOLLOW_RANGE, 40.0);
	}

	@Override
	protected void equip(RandomSource random) {
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.TNT));
	}

	/** Slower to shoot than an ordinary skeleton (each shot is a bomb). */
	@Override
	protected int getHardAttackInterval() {
		return 50;
	}

	@Override
	protected int getAttackInterval() {
		return 70;
	}

	@Override
	protected AbstractArrow getArrow(ItemStack arrowItem, float velocity, ItemStack weapon) {
		BlastArrow arrow = new BlastArrow(level(), this, 6.0f + hordeWave() * 0.5f);
		arrow.setBaseDamageFromMob(velocity);
		return arrow;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!level().isClientSide() && isUsingItem() && tickCount % 3 == 0 && level() instanceof ServerLevel server) {
			// the fuse: smoke pouring off it while it draws
			server.sendParticles(ParticleTypes.SMOKE, getX(), getEyeY() + 0.4, getZ(), 2, 0.15, 0.1, 0.15, 0.01);
		}
	}

	@Override
	public void die(DamageSource source) {
		boolean first = !dead && !isRemoved();
		super.die(source);
		if (first && level() instanceof ServerLevel server) {
			blast(server, this, position().add(0, 0.5, 0), DEATH_BLAST_RADIUS, DEATH_BLAST_DAMAGE, 0.9);
		}
	}
}
