package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.supersoldier.SuperSoldierPose;
import com.projecthero.mod.supersoldier.SuperSoldier;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.7, first person: the Super Soldier's moves play on his empty hands ({@link SuperSoldierPose#firstPerson}) -- the Barrage's
 * blows and haymaker, the Assemble fist, the Counter guard and strike, the Storm's throw, catch and slam -- with vanilla's own punch
 * swing and equip dip held off while a move owns them. Only while the main hand is empty; a shield in the off hand keeps its own
 * first-person placement ({@code CapShieldFirstPersonMixin}).
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererSuperSoldierMixin {
	@Shadow
	protected abstract void renderPlayerArm(PoseStack pose, MultiBufferSource buffers, int light, float equipProgress, float swingProgress,
			HumanoidArm arm);

	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$superSoldierHands(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
			float swingProgress, ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (!stack.isEmpty() || player.isInvisible() || player.isScoping() || !player.getMainHandItem().isEmpty() || !SuperSoldier.hasPower(player)) {
			return;
		}
		float w = SuperSoldierPose.firstPersonWeight(player, partialTick);
		if (w <= 0.001f) {
			return;
		}
		boolean main = hand == InteractionHand.MAIN_HAND;
		HumanoidArm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
		pose.pushPose();
		SuperSoldierPose.firstPerson(player, arm, partialTick, pose);
		float keep = 1f - Math.min(1f, w);
		renderPlayerArm(pose, buffers, light, main ? equipProgress * keep : 0f, main ? swingProgress * keep : 0f, arm);
		pose.popPose();
		ci.cancel();
	}
}
