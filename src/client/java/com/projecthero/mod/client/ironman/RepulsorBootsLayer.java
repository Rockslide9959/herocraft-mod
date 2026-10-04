package com.projecthero.mod.client.ironman;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ironman.IronManBoxes.Box;
import com.projecthero.mod.ironman.item.RepulsorItem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * v0.14.21 round two: a {@link RepulsorItem} worn in the boots slot finally shows on the body -- a small silver / red
 * thruster unit clamped over each foot (silver shell, red ankle ring and side pods, a heel thruster and a dark nozzle
 * sole). While Repulsor Boots flight is on (synced {@code REPULSOR_BOOTS_FLYING}) the soles light up fullbright.
 *
 * <p>The sole's centre is leg-local (0, {@value #SOLE_Y}) px, exactly where {@code IronManFlightFxClient} puts the bare
 * boots' jet nozzles, so the flight jets come straight out of the lit soles.
 */
public class RepulsorBootsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/armor/repulsor_boots.png");
	private static final ResourceLocation GLOW = ProjectHeroMod.id("textures/misc/repulsor_palm_glow.png");
	/** Bottom face of the sole plate, leg-local px (the jet nozzle sits just below it). */
	public static final float SOLE_Y = 12.6f;

	// swatches in repulsor_boots.png (8x8 each)
	private static final float[] SILVER = { 0, 0 }, RED = { 8, 0 }, DARK = { 16, 0 }, GOLD = { 24, 0 }, NOZZLE = { 0, 8 };

	/** Right-foot parts (leg-local px; the leg is x -2..2, y 0..12, z -2..2, -x outward, -z forward). */
	static final List<Box> RIGHT = List.of(
			sw(-2.35f, 8.9f, -2.65f, 2.35f, 12.4f, 2.4f, SILVER),     // shell over the foot
			sw(-2.5f, 8.5f, -2.5f, 2.5f, 9.3f, 2.5f, RED),             // ankle ring
			sw(-2.2f, 10.2f, -2.95f, 2.2f, 12.35f, -2.6f, SILVER),     // toe cap
			sw(-1.0f, 10.6f, -3.0f, 1.0f, 11.4f, -2.9f, GOLD),         // toe stripe
			sw(-1.3f, 9.8f, 2.35f, 1.3f, 12.1f, 3.05f, DARK),          // heel thruster pod
			sw(-1.0f, 10.1f, 3.0f, 1.0f, 11.8f, 3.2f, RED),            // heel pod cap
			sw(-2.8f, 9.9f, -1.1f, -2.3f, 12.0f, 1.1f, RED),           // side thruster pods
			sw(2.3f, 9.9f, -1.1f, 2.8f, 12.0f, 1.1f, RED),
			sw(-2.15f, 12.35f, -2.4f, 2.15f, SOLE_Y, 2.2f, DARK),      // sole plate
			Box.swatch(-1.2f, SOLE_Y - 0.05f, -1.3f, 1.2f, SOLE_Y + 0.02f, 1.1f, NOZZLE[0], NOZZLE[1], 8, 8)); // nozzle ring

	private static Box sw(float x0, float y0, float z0, float x1, float y1, float z1, float[] swatch) {
		return Box.swatch(x0, y0, z0, x1, y1, z1, swatch[0] + 1, swatch[1] + 1, 6, 6);
	}

	public RepulsorBootsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	public static boolean wearing(net.minecraft.world.entity.LivingEntity entity) {
		return entity.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof RepulsorItem;
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (!wearing(player) || player.isInvisible()) {
			return;
		}
		boolean flying = player.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false);
		int overlay = LivingEntityRenderer.getOverlayCoords(player, 0f);
		VertexConsumer vc = buffers.getBuffer(RenderType.armorCutoutNoCull(TEXTURE));
		PlayerModel<AbstractClientPlayer> model = getParentModel();
		drawFoot(pose, vc, model.rightLeg, true, light, overlay);
		drawFoot(pose, vc, model.leftLeg, false, light, overlay);
		if (flying) {
			VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW));
			float flicker = 0.85f + 0.15f * (float) Math.sin((player.tickCount + partialTick) * 1.7f);
			int a = Math.round(255 * flicker);
			for (ModelPart leg : new ModelPart[] { model.rightLeg, model.leftLeg }) {
				pose.pushPose();
				leg.translateAndRotate(pose);
				IronManBoxes.glowQuad(pose, glow, 1, 1, 0f, SOLE_Y + 0.06f, -0.1f, 2.3f, LightTexture.FULL_BRIGHT,
						OverlayTexture.NO_OVERLAY, (a << 24) | 0xFFFFFF);
				pose.popPose();
			}
		}
	}

	private static void drawFoot(PoseStack pose, VertexConsumer vc, ModelPart leg, boolean right, int light, int overlay) {
		pose.pushPose();
		leg.translateAndRotate(pose);
		for (Box b : RIGHT) {
			IronManBoxes.draw(pose, vc, right ? b : b.mirrored(), 32, 32, light, overlay, 0xFFFFFFFF);
		}
		pose.popPose();
	}
}
