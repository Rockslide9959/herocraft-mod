package com.projecthero.mod.horde.entity.skeleton;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.16 (the Skeleton Horde's baby zombie): a small, bleached, red-eyed skeleton -- 70% size, very fast, a stone
 * dagger, low health -- that <b>pounces</b>: from 3 to 8 blocks it leaps straight at its target every few seconds.
 */
public class BoneRunner extends HordeSkeleton {
	public static final float SCALE = 0.7f;
	static final int POUNCE_COOLDOWN = 50;
	private int pounceCooldown = 30;

	public BoneRunner(EntityType<? extends BoneRunner> type, Level level) {
		super(type, level);
		this.xpReward = 6;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return AbstractSkeleton.createAttributes()
				.add(Attributes.MAX_HEALTH, 14.0)
				.add(Attributes.MOVEMENT_SPEED, 0.36)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.SCALE, SCALE);
	}

	@Override
	protected void equip(RandomSource random) {
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (pounceCooldown > 0) {
			pounceCooldown--;
			return;
		}
		LivingEntity target = getTarget();
		if (target == null || !target.isAlive() || !onGround() || !(level() instanceof ServerLevel level)) {
			return;
		}
		double d = distanceTo(target);
		if (d < 3.0 || d > 8.0 || !getSensing().hasLineOfSight(target)) {
			return;
		}
		pounce(level, target);
	}

	/** Leaps at {@code target}: a flat, fast lob that lands on (or just short of) them. */
	public void pounce(ServerLevel level, LivingEntity target) {
		Vec3 to = target.position().subtract(position()).multiply(1, 0, 1);
		Vec3 dir = to.lengthSqr() < 1.0e-4 ? Vec3.ZERO : to.normalize();
		setDeltaMovement(dir.x * 0.95, 0.42, dir.z * 0.95);
		hasImpulse = true;
		pounceCooldown = POUNCE_COOLDOWN + getRandom().nextInt(30);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.SKELETON_AMBIENT, SoundSource.HOSTILE, 1.0f, 1.8f);
		level.sendParticles(ParticleTypes.WHITE_ASH, getX(), getY() + 0.3, getZ(), 8, 0.2, 0.1, 0.2, 0.02);
	}

	public int pounceCooldown() {
		return pounceCooldown;
	}
}
