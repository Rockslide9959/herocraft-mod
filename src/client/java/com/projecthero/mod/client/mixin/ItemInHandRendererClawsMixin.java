package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.wolverine.WolverineClawsModel;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * First person: while Wolverine's claws are out, the (otherwise hidden) empty off hand pops up too, drawn
 * exactly like the main arm -- so {@link PlayerRendererClawsMixin} then adds its mirrored claws. v0.13.9:
 * while the right-click claw guard is up, both arms are drawn raised and angled in so the claws cross.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererClawsMixin {
	/** Claw guard, first person (view units ~ 480 px each at 854x480): each forearm is tipped this far in
	 * about the view axis, then pulled toward the middle and down so the two cross in an X over the centre;
	 * the off arm sits a touch further back so the crossing doesn't z-fight. */
	private static final float GUARD_TILT = 60.0f;
	private static final float GUARD_IN = 0.56f;
	private static final float GUARD_DOWN = 0.42f;
	private static final float GUARD_STAGGER = 0.03f;

	@Shadow
	protected abstract void renderPlayerArm(PoseStack pose, MultiBufferSource buffers, int light, float equipProgress,
			float swingProgress, HumanoidArm arm);

	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$wolverineOffHand(AbstractClientPlayer player, float partialTick, float pitch,
			InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack pose,
			MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (!stack.isEmpty() || player.isInvisible() || !WolverineClawsModel.visible(player, partialTick)) {
			return;
		}
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		if (com.projecthero.mod.wolverine.WolverineBlock.isBlocking(player)) {
			// the guard: each forearm pulled in toward the middle and up, tipped inward so the blades cross
			float f = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
			pose.pushPose();
			pose.translate(-f * GUARD_IN, -GUARD_DOWN, hand == InteractionHand.MAIN_HAND ? 0.0f : -GUARD_STAGGER);
			pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(f * GUARD_TILT));
			renderPlayerArm(pose, buffers, light, 0.0f, 0.0f, arm);
			pose.popPose();
			ci.cancel();
			return;
		}
		if (hand != InteractionHand.OFF_HAND) {
			return;
		}
		pose.pushPose();
		renderPlayerArm(pose, buffers, light, equipProgress, swingProgress, arm);
		pose.popPose();
		ci.cancel();
	}
}
