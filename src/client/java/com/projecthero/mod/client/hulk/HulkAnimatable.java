package com.projecthero.mod.client.hulk;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hulk.HulkConfig;
import com.projecthero.mod.hulk.data.HulkState;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;

import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.13.12 (Hulk Phase 3): the GeckoLib animatable drawn in place of a player while he is the Hulk. One singleton for
 * every Hulk -- GeckoLib keys each player's animation state by entity id. What plays is read from the player's synced
 * {@link HulkState}: the growth ({@code transform}), a charging / airborne Super Leap, Thunderclap ({@code clap}) and
 * Ground Smash ({@code smash}) on their impact clock, otherwise {@code run} / {@code walk} / {@code idle}; a melee
 * swing layers {@code punch} over the right arm.
 */
public final class HulkAnimatable implements GeoReplacedEntity {
	public static final HulkAnimatable INSTANCE = new HulkAnimatable();

	private static final String P = "animation.hulk.";
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
	private static final RawAnimation RUN = RawAnimation.begin().thenLoop(P + "run");
	private static final RawAnimation CLAP = RawAnimation.begin().thenPlay(P + "clap");
	private static final RawAnimation SMASH = RawAnimation.begin().thenPlay(P + "smash");
	private static final RawAnimation LEAP_CHARGE = RawAnimation.begin().thenPlayAndHold(P + "leap_charge");
	private static final RawAnimation LEAP = RawAnimation.begin().thenPlayAndHold(P + "leap");
	private static final RawAnimation TRANSFORM = RawAnimation.begin().thenPlay(P + "transform");
	private static final RawAnimation PUNCH = RawAnimation.begin().thenPlay(P + "punch");

	/** Ticks each one-shot owns the body after it starts (a touch longer than the clip, so it finishes). */
	private static final int CLAP_TICKS = 14;
	private static final int SMASH_TICKS = 20;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private HulkAnimatable() {
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return EntityType.PLAYER;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "main", 3, this::main));
		controllers.add(new AnimationController<>(this, "swing", 1, this::swing));
	}

	private PlayState main(AnimationState<HulkAnimatable> state) {
		if (!(state.getData(DataTickets.ENTITY) instanceof Player player)) {
			return PlayState.STOP;
		}
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		if (s == null) {
			return state.setAndContinue(IDLE);
		}
		long now = player.level().getGameTime();
		if (s.hulk && now - s.formChangedAt < HulkConfig.GROWTH_TICKS) {
			return state.setAndContinue(TRANSFORM);
		}
		if (s.leapChargeStart > 0L) {
			return state.setAndContinue(LEAP_CHARGE);
		}
		if (s.animId == HulkState.ANIM_CLAP && now - s.animStart < CLAP_TICKS) {
			return state.setAndContinue(CLAP);
		}
		if (s.animId == HulkState.ANIM_SMASH && now - s.animStart < SMASH_TICKS) {
			return state.setAndContinue(SMASH);
		}
		if (s.leaping && !player.onGround()) {
			return state.setAndContinue(LEAP);
		}
		if (state.getLimbSwingAmount() > 0.05f) {
			return state.setAndContinue(player.isSprinting() ? RUN : WALK);
		}
		return state.setAndContinue(IDLE);
	}

	private PlayState swing(AnimationState<HulkAnimatable> state) {
		if (state.getData(DataTickets.ENTITY) instanceof Player player && player.swinging) {
			return state.setAndContinue(PUNCH);
		}
		state.getController().forceAnimationReset();
		return PlayState.STOP;
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
