package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.ironman.GantryClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * v0.15.5, user request ("don't zoom the player's view in"): a Stark Gantry sequence pins its wearer by zeroing their
 * movement speed, and vanilla derives the FOV from movement speed -- the view zoomed right in for the whole suit-up.
 * Hold the FOV neutral while the gantry holds the local player (the same fix as the Hulk's kneeling change).
 */
@Mixin(AbstractClientPlayer.class)
public abstract class GantryFovMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("HEAD"), cancellable = true)
	private void projecthero$noGantryZoom(CallbackInfoReturnable<Float> cir) {
		if ((Object) this == Minecraft.getInstance().player && (GantryClient.holdFov() || projecthero$suitingByHand())) {
			cir.setReturnValue(1.0f);
		}
	}

	/**
	 * v0.15.15, explicit user request ("don't zoom in the player when they are equipping / unequipping the suit after
	 * pressing C"): the hand-built suit-up and the C suit-down removal hold the wearer still with the same movement lock
	 * (the Suit Platform's transient -100% speed modifier, synced to its own client), which zoomed the view right in. Hold
	 * the FOV neutral while that lock is on, or while one of those poses runs.
	 */
	private boolean projecthero$suitingByHand() {
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		net.minecraft.world.entity.ai.attributes.AttributeInstance speed =
				self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
		if (speed != null && speed.hasModifier(
				com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity.FREEZE_ID)) {
			return true;
		}
		com.projecthero.mod.ironman.suit.IronManSuitFx fx = com.projecthero.mod.ironman.suit.IronManSuitFx.of(self);
		int kind = fx.poseKind();
		boolean byHand = kind == com.projecthero.mod.ironman.suit.IronManSuitFx.POSE_MK1_BUILD
				|| kind == com.projecthero.mod.ironman.suit.IronManSuitFx.POSE_MANUAL_UP
				|| kind == com.projecthero.mod.ironman.suit.IronManSuitFx.POSE_MK1_OFF
				|| kind == com.projecthero.mod.ironman.suit.IronManSuitFx.POSE_SLEEK_OFF
				|| kind == com.projecthero.mod.ironman.suit.IronManSuitFx.POSE_SUIT_DOWN;
		return byHand && self.level() != null && fx.poseAge(self.level().getGameTime(), 0f) >= 0f;
	}
}
