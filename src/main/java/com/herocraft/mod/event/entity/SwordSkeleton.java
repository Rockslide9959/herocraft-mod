package com.herocraft.mod.event.entity;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * A skeleton that closes instead of kiting: sword in hand, no bow goal at all, and quick enough to
 * actually reach a ranged player. Its job in the wave table is to punish standing still and shooting
 * (spec section 17).
 *
 * <p>It extends vanilla {@link Skeleton} rather than {@link AbstractSkeleton} for a mundane but firm
 * reason: {@code AbstractSkeleton#getStepSound} is package-private, so a subclass outside
 * {@code net.minecraft.world.entity.monster} cannot implement it. {@code Skeleton} already does, and
 * brings the model, sounds and undead type with it. What has to change is its weapon behaviour:
 * {@code reassessWeaponGoal} would re-add a bow goal whenever the held item changes, so it is
 * overridden to a no-op and the melee goal is installed once, here.
 */
public class SwordSkeleton extends Skeleton {
	public SwordSkeleton(EntityType<? extends SwordSkeleton> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return AbstractSkeleton.createAttributes()
				.add(Attributes.MAX_HEALTH, 26.0)
				.add(Attributes.ATTACK_DAMAGE, 5.0)
				.add(Attributes.MOVEMENT_SPEED, 0.29)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.ARMOR, 3.0);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, false));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	/**
	 * No-op on purpose. The base class uses this to swap between a bow goal and a melee goal based on
	 * the held item; leaving it in would let a Sword Skeleton pick up a dropped bow mid-raid and turn
	 * back into an ordinary archer, which is precisely the behaviour this mob exists to replace.
	 */
	@Override
	public void reassessWeaponGoal() {
	}

	@Override
	protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, DifficultyInstance difficulty) {
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		// Its shield is the reason it can pressure a ranged player at all; both are event kit, not loot.
		setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		setDropChance(EquipmentSlot.OFFHAND, 0.0f);
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
			MobSpawnType reason, SpawnGroupData data) {
		SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
		populateDefaultEquipmentSlots(level.getRandom(), difficulty);
		return result;
	}

	/**
	 * Raid mobs must not immolate themselves -- a raid very often starts in daylight because it is
	 * scheduled by a curse timer, not by nightfall.
	 */
	@Override
	protected boolean isSunBurnTick() {
		return false;
	}

	/**
	 * Vanilla skeletons block with a shield through {@code LivingEntity}'s normal use-item handling,
	 * which mobs do not drive on their own. Raising the shield whenever a target is close and in front
	 * gives the "can occasionally defend/block attacks" the design asks for, without inventing a new
	 * combat system for it.
	 */
	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide()) {
			return;
		}
		LivingEntity target = getTarget();
		boolean shouldBlock = target != null && target.isAlive()
				&& distanceToSqr(target) < 36.0
				&& getOffhandItem().is(Items.SHIELD)
				&& (tickCount / 30) % 3 == 0; // roughly a third of the time, in readable blocks
		if (shouldBlock && !isUsingItem()) {
			startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
		} else if (!shouldBlock && isUsingItem()) {
			stopUsingItem();
		}
	}

}
