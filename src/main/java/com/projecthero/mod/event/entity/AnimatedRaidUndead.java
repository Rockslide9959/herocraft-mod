package com.projecthero.mod.event.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.16.1: a {@link RaidUndead} drawn with its own GeckoLib model ({@link RaidAnimated}, {@link RaidAnims}): the Risen /
 * Armoured / grave-child ({@link RaidZombie}), the Acid Zombie, the Juggernaut, the Cursed Zombie and the Powered Zombie
 * Boss. The synced clip id and the strike timing live in {@link RaidAnimator}; the AI is untouched. (The Titan's entities
 * extend {@link RaidUndead} directly and are not affected; neither is any vanilla zombie.)
 */
public abstract class AnimatedRaidUndead extends RaidUndead implements RaidAnimated {
	private static final EntityDataAccessor<Integer> DATA_RAID_ANIM = SynchedEntityData.defineId(AnimatedRaidUndead.class, EntityDataSerializers.INT);

	private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
	private RaidAnimator raidAnimator;

	protected AnimatedRaidUndead(EntityType<? extends Zombie> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		RaidAnimator.define(builder, DATA_RAID_ANIM);
	}

	@Override
	public RaidAnimator raidAnimator() {
		if (raidAnimator == null) {
			raidAnimator = new RaidAnimator(this, DATA_RAID_ANIM);
		}
		return raidAnimator;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_RAID_ANIM.equals(key)) {
			raidAnimator().onSynced(key);
		}
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!level().isClientSide()) {
			raidAnimator().tick();
			if (usesMeleeAnimTiming()) {
				raidAnimator().tickMelee();
			}
		}
	}

	/** Whether {@link RaidAnimator#tickMelee} drives this mob's melee wind-up (not for ranged-only raiders). */
	protected boolean usesMeleeAnimTiming() {
		return true;
	}

	@Override
	public void swing(InteractionHand hand, boolean updateSelf) {
		super.swing(hand, updateSelf);
		if (!level().isClientSide() && hand == InteractionHand.MAIN_HAND && usesMeleeAnimTiming()) {
			raidAnimator().onMeleeSwing();
		}
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return geoCache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		raidAnimator().registerControllers(controllers, this);
	}
}
