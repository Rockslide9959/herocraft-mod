package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.punisher.GunFirstPerson;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * v0.15.16, first person: a Punisher gun in the main hand is drawn by {@link GunFirstPerson} (both arms on the gun,
 * aim down sights, sprint / reload / roll / Adrenaline animations) instead of vanilla's lone item; the off-hand item is
 * not drawn meanwhile, since that hand is on the gun.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererGunMixin {
	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$gunRig(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
			float swingProgress, ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		if (!GunFirstPerson.active(player)) {
			return;
		}
		if (hand == InteractionHand.MAIN_HAND) {
			// the renderer's own copy of the held item lags a slot change (the equip animation), so the stack being drawn
			// may not be the gun yet -- vanilla draws it then
			if (com.projecthero.mod.client.punisher.GunAnim.kind(stack) == null) {
				return;
			}
			GunFirstPerson.render(player, partialTick, stack, equipProgress, pose, buffers, light);
		}
		ci.cancel();
	}

	/**
	 * v0.15.18: the Punisher's Brutal Strike without a gun in hand -- the main arm (empty or holding anything else) jabs
	 * straight out toward the crosshair. Applied just inside the method's own push so it never leaks to the other hand.
	 */
	@Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
	private void projecthero$punisherJab(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
			float swingProgress, ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		if (hand != InteractionHand.MAIN_HAND || GunFirstPerson.active(player)
				|| com.projecthero.mod.client.punisher.GunAnim.meleeKind(player) != com.projecthero.mod.punisher.ability.PunisherMelee.ANIM_PUNCH) {
			return;
		}
		float t = com.projecthero.mod.client.punisher.GunAnim.melee(player, partialTick);
		if (t < 0f) {
			return;
		}
		float k = com.projecthero.mod.client.punisher.GunAnim.keys(t, 0f, 0f, 1.2f, -0.3f, 2.2f, 1f, 4f, 0.85f, 8f, 0f);
		float out = Math.max(0f, k);
		float side = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1f : -1f;
		pose.translate(-0.16f * out * side, 0.2f * out, -0.45f * k);
		pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(6f * out));
		pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(14f * out * side));
	}
}
