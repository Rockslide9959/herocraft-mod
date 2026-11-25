package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.thor.ThorKitPose;
import com.projecthero.mod.power.ThorState;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.7 Thor Cosmic revamp, first person: Unarmed Thor's moves play on his bare hands ({@link ThorKitPose#firstPerson}) -- the
 * Thunder Fist charge and punch, Storm Rush's blows, the palm, the burst, the pound -- with vanilla's own punch swing and equip dip
 * held off while a move owns them. Only while both hands are empty and he has Thor's kit (a weapon in hand goes through
 * {@code ItemInHandRendererComboMixin}).
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererThorMixin {
	@Shadow
	protected abstract void renderPlayerArm(PoseStack pose, MultiBufferSource buffers, int light, float equipProgress, float swingProgress,
			HumanoidArm arm);

	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$thorHands(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress,
			ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (!stack.isEmpty() || player.isInvisible() || player.isScoping() || !player.getMainHandItem().isEmpty()
				|| ThorState.of(player) != ThorState.UNARMED || !ThorState.hasKit(player)) {
			return;
		}
		float w = ThorKitPose.firstPersonWeight(player, partialTick);
		if (w <= 0.001f) {
			return;
		}
		boolean main = hand == InteractionHand.MAIN_HAND;
		HumanoidArm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
		pose.pushPose();
		ThorKitPose.firstPerson(player, main, arm, partialTick, pose);
		float keep = 1f - Math.min(1f, w);
		renderPlayerArm(pose, buffers, light, main ? equipProgress * keep : 0f, main ? swingProgress * keep : 0f, arm);
		pose.popPose();
		ci.cancel();
	}
}
