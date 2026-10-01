package com.projecthero.mod.horde.entity.skeleton;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * v0.14.16 (the Skeleton Horde's Juggernaut): a hulking, yellowed skeleton at 1.6x size swinging a mace -- tough, slow,
 * hard to shift, and every few seconds it rears up for a <b>Ground Slam</b>: it stops, raises the mace for 1.2 s (ash
 * swirls round its feet, it bellows), then brings it down -- 12 damage and a throw to everyone within 4.5 blocks.
 * Step out of the ring and it whiffs.
 */
public class BoneBrute extends HordeSkeleton {
	public static final float SCALE = 1.6f;
	public static final int SLAM_WINDUP = 24;
	static final int SLAM_COOLDOWN = 120;
	static final double SLAM_RADIUS = 4.5;
	static final float SLAM_DAMAGE = 12.0f;
	private int slamCooldown = 60;
	private int windup = -1;

	public BoneBrute(EntityType<? extends BoneBrute> type, Level level) {
		super(type, level);
		this.xpReward = 25;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return AbstractSkeleton.createAttributes()
				.add(Attributes.MAX_HEALTH, 90.0)
				.add(Attributes.MOVEMENT_SPEED, 0.23)
				.add(Attributes.ATTACK_DAMAGE, 9.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.2)
				.add(Attributes.ARMOR, 8.0)
				.add(Attributes.ARMOR_TOUGHNESS, 2.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.STEP_HEIGHT, 1.0)
				.add(Attributes.SCALE, SCALE);
	}

	@Override
	protected void equip(RandomSource random) {
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.MACE));
	}

	public boolean isWindingUp() {
		return windup >= 0;
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		return !isWindingUp() && super.doHurtTarget(target);
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (windup >= 0) {
			tickSlam(level);
			return;
		}
		if (slamCooldown > 0) {
			slamCooldown--;
			return;
		}
		LivingEntity target = getTarget();
		if (target != null && target.isAlive() && distanceTo(target) < SLAM_RADIUS) {
			startSlam(level);
		}
	}

	/** Rears up: the slam lands {@link #SLAM_WINDUP} ticks from now. */
	public void startSlam(ServerLevel level) {
		windup = 0;
		getNavigation().stop();
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0f, 1.5f);
	}

	/** One tick of the wind-up; the slam lands on the last. */
	public void tickSlam(ServerLevel level) {
		if (windup < 0) {
			return;
		}
		windup++;
		getNavigation().stop();
		if (windup % 3 == 0) {
			ring(level, ParticleTypes.WHITE_ASH, position(), SLAM_RADIUS * (windup / (double) SLAM_WINDUP), 14);
		}
		if (windup < SLAM_WINDUP) {
			return;
		}
		windup = -1;
		slamCooldown = SLAM_COOLDOWN + getRandom().nextInt(40);
		swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
				getX(), getY() + 0.2, getZ(), 60, SLAM_RADIUS * 0.5, 0.2, SLAM_RADIUS * 0.5, 0.15);
		blast(level, this, position(), SLAM_RADIUS, SLAM_DAMAGE, 1.2);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("SlamCooldown", slamCooldown);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		slamCooldown = tag.getInt("SlamCooldown");
	}
}
