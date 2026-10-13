package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.spider.SpiderPose;
import com.projecthero.mod.client.spider.SpiderSwingPose;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.1: Spider-Man's ledge perch in first person -- BOTH hands come down to the bottom of the view, planted on the
 * ledge just ahead of his feet (the off hand is drawn here too, since vanilla never draws an empty off-hand arm).
 */
@Mixin(ItemInHandRenderer.class)
public abstract class SpiderPerchHandMixin {
	@Shadow
	protected abstract void renderPlayerArm(PoseStack pose, MultiBufferSource buffers, int light, float equippedProgress,
			float swingProgress, HumanoidArm side);

	@Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IFFLnet/minecraft/world/entity/HumanoidArm;)V"))
	private void projecthero$perchHands(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
			float swingProgress, ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		if (hand != InteractionHand.MAIN_HAND) {
			return;
		}
		float k = SpiderPose.perchAmount(player);
		if (k <= 0f) {
			return;
		}
		HumanoidArm main = player.getMainArm();
		float side = main == HumanoidArm.RIGHT ? 1f : -1f;
		// the off hand, mirrored (renderPlayerArm places it on its own side of the view)
		pose.pushPose();
		pose.translate(side * 0.42f * k, 0.07f * k, -0.12f * k);
		pose.mulPose(Axis.XP.rotationDegrees(6f * k));
		pose.mulPose(Axis.ZP.rotationDegrees(side * 14f * k));
		renderPlayerArm(pose, buffers, light, 0.0f, 0.0f, main.getOpposite());
		pose.popPose();
		// the main hand
		pose.translate(-side * 0.42f * k, 0.07f * k, -0.12f * k);
		pose.mulPose(Axis.XP.rotationDegrees(6f * k));
		pose.mulPose(Axis.ZP.rotationDegrees(-side * 14f * k));
	}

	/**
	 * v0.16.1: the living web swing in first person -- the web hand reaches up along the line toward the anchor (tracking
	 * it every frame, with a slight bob that follows the arc); the free hand comes into view at the screen edge on the
	 * upswing (and joins the line on a two-hand swing). The off hand is drawn here when it is the web hand, since vanilla
	 * never draws an empty off-hand arm.
	 */
	@Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IFFLnet/minecraft/world/entity/HumanoidArm;)V"))
	private void projecthero$swingHands(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
			float swingProgress, ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		if (hand != InteractionHand.MAIN_HAND) {
			return;
		}
		SpiderSwingPose.Rig r = SpiderSwingPose.rig(player, partialTick);
		if (r == null || r.w <= 0.01f || r.aimDir() == null) {
			return;
		}
		net.minecraft.client.Camera cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
		org.joml.Vector3f look = cam.getLookVector();
		org.joml.Vector3f up = cam.getUpVector();
		org.joml.Vector3f left = cam.getLeftVector();
		net.minecraft.world.phys.Vec3 d = r.aimDir();
		float fwd = (float) (d.x * look.x() + d.y * look.y() + d.z * look.z());
		float upc = (float) (d.x * up.x() + d.y * up.y() + d.z * up.z());
		float rightc = -(float) (d.x * left.x() + d.y * left.y() + d.z * left.z());
		float elev = Mth.clamp((float) Math.atan2(upc, Math.max(fwd, 0.05f)), -0.3f, 1.25f);
		float yaw = Mth.clamp((float) Math.atan2(rightc, Math.max(fwd, 0.05f)), -0.8f, 0.8f);
		float bob = Mth.sin(r.phase) * 0.03f * (0.4f + r.fast);
		HumanoidArm main = player.getMainArm();
		HumanoidArm webSide = r.rightHand() ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
		float k = r.w;
		// the first-person web line leaves from the raised hand
		r.fpLift = (0.4f * Mth.clamp(elev, 0f, 1.2f) + bob) * k;
		if (webSide == main) {
			HumanoidArm off = main.getOpposite();
			float peek = Math.max(r.peek, r.twoHand);
			if (peek > 0.03f) {
				pose.pushPose();
				projecthero$freeHand(pose, off == HumanoidArm.RIGHT ? 1f : -1f, peek * k, r, elev, yaw, bob);
				renderPlayerArm(pose, buffers, light, 0.0f, 0.0f, off);
				pose.popPose();
			}
			projecthero$reach(pose, main == HumanoidArm.RIGHT ? 1f : -1f, elev, yaw, bob, k);
		} else {
			pose.pushPose();
			projecthero$reach(pose, webSide == HumanoidArm.RIGHT ? 1f : -1f, elev, yaw, bob, k);
			renderPlayerArm(pose, buffers, light, 0.0f, 0.0f, webSide);
			pose.popPose();
			projecthero$freeHand(pose, main == HumanoidArm.RIGHT ? 1f : -1f, Math.max(r.peek, r.twoHand) * k + (1f - k), r,
					elev, yaw, bob);
		}
	}

	/** The web hand: swung up about the shoulder toward the anchor, leaning toward its side, bobbing with the arc. */
	private static void projecthero$reach(PoseStack pose, float s, float elev, float yaw, float bob, float k) {
		// raised (not swung back toward the camera): lifted and pushed out along the view, then tilted up about a pivot
		// well below the screen so the forearm points up the line
		float e = Mth.clamp(elev, 0f, 1.2f);
		float px = s * 0.62f;
		float py = -0.75f;
		float pz = -0.7f;
		pose.translate(s * 0.05f * e * k, (0.4f * e + bob) * k, -0.05f * e * k);
		pose.translate(px, py, pz);
		// stood up toward the vertical and leaning to the anchor's side (vanilla's arm leans in toward the crosshair)
		pose.mulPose(Axis.ZP.rotation(-(s * 0.38f * e + yaw * 0.5f) * k));
		pose.mulPose(Axis.XP.rotation(-0.15f * e * k));
		pose.translate(-px, -py, -pz);
	}

	/** The free hand: mostly below the screen edge, coming up into view by {@code peek} (and onto the line on two hands). */
	private static void projecthero$freeHand(PoseStack pose, float s, float peek, SpiderSwingPose.Rig r, float elev,
			float yaw, float bob) {
		peek = Mth.clamp(peek, 0f, 1f);
		pose.translate(s * 0.06f * (1f - peek), -0.7f * (1f - peek) - bob, 0.0f);
		pose.mulPose(Axis.ZP.rotationDegrees(s * 18f * (1f - peek)));
		if (r.twoHand > 0.02f) {
			projecthero$reach(pose, s, elev, yaw, -bob, r.twoHand * 0.85f);
		} else {
			pose.mulPose(Axis.XP.rotation(-0.25f * r.up));
		}
	}
}
