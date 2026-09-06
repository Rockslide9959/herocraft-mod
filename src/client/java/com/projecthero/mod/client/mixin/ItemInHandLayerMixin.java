package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.FlightPoseHelper;
import com.projecthero.mod.item.ModItems;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps the held hammer looking correctly gripped while the arm is easing back down out of the
 * FAST (sprint) raised pose toward HOVER or SLOW's ordinary grip.
 *
 * <p>{@code HumanoidModelMixin} raises the hammer arm (only ever for {@link FlightPoseHelper.Tier
 * #FAST} now -- HOVER keeps the ordinary grip untouched, see its class javadoc) by rotating it a
 * half turn about the arm's own X axis, which inverts the hand's frame; whatever orientation the
 * ordinary grip produces would hang upside down in that raised hand unless it is undone here. This
 * correction is purely "cancel the arm's own half turn" -- it does not depend on which way the base
 * grip points, so it stays valid across changes to the model's display transform.
 * {@link #projecthero$uprightWhileRaised} is that correction, blended by the same weight the arm
 * rises by so it tracks smoothly through the whole transition. There is deliberately no spin
 * layered on top any more -- the hammer just sits still, matching the "no spin, ever" rule the
 * thrown/returning hammer also follows.
 *
 * <p>The sprint pose itself needs no correction (the 90-degree body tilt applied by
 * {@code PlayerRendererMixin} already reorients the item frame correctly on its own for FAST), so
 * the injection below gates itself to {@link FlightPoseHelper.Tier#HOVER} -- applying it
 * unconditionally (as an earlier version of this mixin did) double-flipped the hammer during
 * sprint. Since HOVER's own arm-raise target is 0, in practice this correction now only ever
 * matters transiently, while {@link FlightPoseHelper#armRaise} eases down from FAST's raised pose
 * after {@code tier} has already flipped back to HOVER -- it smooths that decay rather than
 * correcting any steady-state hover pose, because there no longer is one.
 *
 * <p>The correction is injected immediately before vanilla renders the item -- i.e. inside vanilla's
 * own {@code pushPose()}/{@code popPose()} pair -- so it can't leak into anything rendered
 * afterward. First person is untouched: that path goes through {@code ItemInHandRenderer} directly,
 * not this layer.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
	@Inject(method = "renderArmWithItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
	private void projecthero$keepHammerUprightWhileRaised(LivingEntity entity, ItemStack stack,
			ItemDisplayContext displayContext, HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer,
			int packedLight, CallbackInfo ci) {
		if (!stack.is(ModItems.MJOLNIR) || !(entity instanceof Player player)) {
			return;
		}

		// HOVER only -- see the class javadoc. FAST (sprint) also raises the arm, via the same
		// ARM_RAISED_XROT in HumanoidModelMixin, but PlayerRendererMixin's 90-degree body tilt for
		// that pose already reorients the item frame correctly on its own; applying this correction
		// there too was double-flipping the hammer, which is what showed up in-game as it hanging
		// upside down while sprint-flying even though the ordinary (unraised) grip was correct.
		if (FlightPoseHelper.tier(player) != FlightPoseHelper.Tier.HOVER) {
			return;
		}

		float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float raise = FlightPoseHelper.armRaise(player, partialTick);
		if (raise <= 0.001f) {
			return;
		}

		projecthero$uprightWhileRaised(poseStack, raise);
	}

	/**
	 * Cancels the arm-raise out of the held item's orientation.
	 *
	 * <p>{@code HumanoidModelMixin} raises the hammer arm by rotating it a half turn about the arm's
	 * own X axis. At this point in the pose stack vanilla has already composed the fixed hand
	 * transform, so that half turn shows up as both the Y and Z axes of the item frame being flipped
	 * -- which is exactly what a 180-degree rotation about X undoes. Without it, the hammer would
	 * hang inverted from the raised hand.
	 *
	 * <p>Scaled by the raise weight so it tracks the arm through the transition rather than snapping
	 * the hammer over the moment the pose starts.
	 */
	private static void projecthero$uprightWhileRaised(PoseStack poseStack, float raise) {
		poseStack.mulPose(Axis.XP.rotationDegrees(180.0f * raise));
	}
}
