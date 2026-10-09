package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.punisher.PunisherGunPose;
import com.projecthero.mod.firearm.item.FirearmItem;
import com.projecthero.mod.punisher.item.PunisherItems;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * v0.15.16, third person: the Punisher gun poses ({@link PunisherGunPose}). The held gun is re-pitched so its barrel
 * points where the pose says (the models are built for a hanging arm), canted during a reload and scaled a touch; the
 * off hand gets the reload magazine / the Adrenaline syringe while those moves run.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerGunMixin {
	@Inject(method = "renderArmWithItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
	private void projecthero$gunGrip(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, HumanoidArm arm,
			PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (!(entity instanceof Player player) || !(stack.getItem() instanceof FirearmItem) || arm != player.getMainArm()) {
			return;
		}
		float[] c = PunisherGunPose.item(player);
		if (c == null) {
			return;
		}
		// Turn the gun about the grip so its barrel points exactly where the pose wants it (pitch c[0], yaw c[5]), whatever
		// the arm is doing: find where the barrel points now (through the model's own third-person transform and this
		// frame), and rotate from there to the wanted direction, both expressed in this frame.
		var model = Minecraft.getInstance().getItemRenderer().getModel(stack, player.level(), player, player.getId());
		org.joml.Vector3f r = model.getTransforms().getTransform(ctx).rotation;
		org.joml.Vector3f barrel = new org.joml.Quaternionf()
				.rotationXYZ(r.x * net.minecraft.util.Mth.DEG_TO_RAD, r.y * net.minecraft.util.Mth.DEG_TO_RAD, r.z * net.minecraft.util.Mth.DEG_TO_RAD)
				.transform(new org.joml.Vector3f(0f, 0f, -1f));
		net.minecraft.world.phys.Vec3 w = net.minecraft.world.phys.Vec3.directionFromRotation(c[0] * net.minecraft.util.Mth.RAD_TO_DEG, c[5]);
		org.joml.Matrix3f inv = new org.joml.Matrix3f(pose.last().pose()).invert();
		org.joml.Vector3f want = inv.transform(new org.joml.Vector3f((float) w.x, (float) w.y, (float) w.z)).normalize();
		// slide it back along the barrel so the pistol grip, not the receiver, sits in the fist (stock back toward the shoulder)
		float back = com.projecthero.mod.client.punisher.PunisherGunPose.gripShift(stack) / 16f;
		pose.translate(-want.x * back, -want.y * back, -want.z * back);
		// ...and lift it off the fist (the receiver rides above the hand, the stock passes over the forearm to the shoulder)
		// and a touch out to the gun's right, so it never runs through the arm
		org.joml.Vector3f up = inv.transform(new org.joml.Vector3f(0f, 1f, 0f));
		up.sub(new org.joml.Vector3f(want).mul(up.dot(want))).normalize();
		org.joml.Vector3f side = new org.joml.Vector3f(want).cross(up).normalize();
		float lift = com.projecthero.mod.client.punisher.PunisherGunPose.gripLift(stack) * c[6] / 16f;
		float out = (arm == HumanoidArm.RIGHT ? 0.9f : -0.9f) / 16f;
		pose.translate(up.x * lift + side.x * out, up.y * lift + side.y * out, up.z * lift + side.z * out);
		pose.mulPose(new org.joml.Quaternionf().rotationTo(barrel, want));
		if (c[1] != 0f) {
			pose.mulPose(new org.joml.Quaternionf().rotationAxis(c[1] * net.minecraft.util.Mth.DEG_TO_RAD * (arm == HumanoidArm.RIGHT ? 1f : -1f), want.x, want.y, want.z));
		}
		if (c[4] != 1f) {
			pose.scale(c[4], c[4], c[4]);
		}
		// v0.15.16: the muzzle flash, at the muzzle through the model's own third-person transform
		float flash = com.projecthero.mod.client.punisher.GunAnim.flash(player, net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
		if (flash > 0f) {
			pose.pushPose();
			model.getTransforms().getTransform(ctx).apply(arm == HumanoidArm.LEFT, pose);
			pose.translate(-0.5f, -0.5f, -0.5f);
			com.projecthero.mod.client.firearm.MuzzleFlash.draw(pose, buffers, com.projecthero.mod.client.punisher.GunAnim.kind(stack), flash,
					com.projecthero.mod.client.punisher.GunAnim.shotSeed(player));
			pose.popPose();
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
			at = @At("TAIL"))
	private void projecthero$gunProps(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
		if (!(entity instanceof Player player)) {
			return;
		}
		float[] c = PunisherGunPose.item(player);
		if (c == null || (c[2] == 0f && c[3] == 0f)) {
			return;
		}
		HumanoidArm offArm = player.getMainArm().getOpposite();
		ItemStack prop = new ItemStack(c[3] != 0f ? PunisherItems.ADRENALINE_SYRINGE : PunisherItems.GUN_MAGAZINE);
		ArmedModel model = (ArmedModel) ((RenderLayer) (Object) this).getParentModel();
		float side = offArm == HumanoidArm.LEFT ? 1f : -1f;
		pose.pushPose();
		model.translateToHand(offArm, pose);
		// model space here: +Y runs down the arm to the fist; the fist's middle is ~8.5 px down
		pose.translate(side / 16f, 8.5f / 16f, 0f);
		if (c[3] != 0f) {
			pose.mulPose(Axis.XP.rotationDegrees(180f)); // needle out of the bottom of the fist
			pose.scale(0.62f, 0.62f, 0.62f);
		} else {
			pose.translate(0f, 0.08f, -0.05f);
			pose.mulPose(Axis.XP.rotationDegrees(180f)); // feed lips back toward the gun
			pose.scale(0.7f, 0.7f, 0.7f);
		}
		Minecraft.getInstance().getItemRenderer().renderStatic(player, prop, ItemDisplayContext.NONE, false, pose, buffers,
				player.level(), light, OverlayTexture.NO_OVERLAY, player.getId());
		pose.popPose();
	}
}
