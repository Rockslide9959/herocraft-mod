package com.projecthero.mod.client.symbiote;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteVitals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.19: the Symbiote Blade's model -- a single curved black blade growing out of the host's main hand,
 * replacing the particle sheath that used to pour off the arm the whole time it was out. A slim diamond
 * cross-section with a sharp front edge, widest a little past the fist, sweeping gently forward to the point.
 *
 * <p>Drawn in the arm bone's own space (after {@link ModelPart#translateAndRotate}, one unit = one block, +Y
 * runs down the arm to the fist at 10 px, -Z is the arm's front), so it follows every swing and pose for free
 * -- including the GeckoLib suit, which copies the same vanilla arm. Third person via {@link Layer}, first
 * person via {@code PlayerRendererSymbioteBladeMixin}. Driven by the synced {@link SymbioteVitals#bladeActive},
 * so every viewer sees it.
 */
public final class SymbioteBladeRenderer {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/symbiote_blade.png");
	/** Where the blade roots, inside the fist. */
	private static final double ROOT_Y = 0.5;
	private static final int SECTIONS = 10;

	private SymbioteBladeRenderer() {
	}

	/** Blade out? (bonded, the toggle on, not invisible.) */
	public static boolean visible(Player player) {
		if (player.isInvisible() || !Symbiote.hasSymbiote(player)) {
			return false;
		}
		SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		return v != null && v.bladeActive;
	}

	/** First person: {@code arm} is the arm being drawn; only the main arm grows the blade. */
	public static void renderFirstPerson(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
			ModelPart arm, boolean rightArm) {
		if (rightArm != (player.getMainArm() == HumanoidArm.RIGHT)) {
			return;
		}
		pose.pushPose();
		arm.translateAndRotate(pose);
		draw(pose, buffers, light, rightArm, player.getSkin().model() == PlayerSkin.Model.SLIM, 1.05);
		pose.popPose();
	}

	/** One blade in arm-bone space. */
	static void draw(PoseStack pose, MultiBufferSource buffers, int light, boolean rightArm, boolean slim, double length) {
		double xc = (rightArm ? -1.0 : 1.0) * (slim ? 0.5 : 1.0) / 16.0;
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
		PoseStack.Pose last = pose.last();
		Vec3[][] ring = new Vec3[SECTIONS + 1][4];
		for (int i = 0; i <= SECTIONS; i++) {
			double s = (double) i / SECTIONS;
			double y = ROOT_Y + s * length;
			double centre = -0.14 * s * s;                       // sweeps forward toward the point
			double half = 0.095 * Math.pow(1.0 - s, 0.8) + 0.025 * Math.sin(Math.PI * s);
			double front = centre - half * 1.15;                   // the cutting edge
			double back = centre + half * 0.85;                    // the spine
			double t = 0.03 * (1.0 - 0.85 * s);                    // thickness
			ring[i][0] = new Vec3(xc, y, back);
			ring[i][1] = new Vec3(xc + t, y, centre);
			ring[i][2] = new Vec3(xc, y, front);
			ring[i][3] = new Vec3(xc - t, y, centre);
		}
		for (int i = 0; i < SECTIONS; i++) {
			float v0 = (float) i / SECTIONS;
			float v1 = (float) (i + 1) / SECTIONS;
			for (int k = 0; k < 4; k++) {
				int k2 = (k + 1) % 4;
				GooMesh.quad(vc, last, light, ring[i][k], ring[i][k2], ring[i + 1][k2], ring[i + 1][k],
						k * 0.25f, v0, (k + 1) * 0.25f, v1);
			}
		}
	}

	/** Third person, and how every other player sees it. */
	public static class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
		public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
				float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
			if (!visible(player)) {
				return;
			}
			boolean right = player.getMainArm() == HumanoidArm.RIGHT;
			boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
			pose.pushPose();
			(right ? getParentModel().rightArm : getParentModel().leftArm).translateAndRotate(pose);
			draw(pose, buffers, packedLight, right, slim, 0.8);
			pose.popPose();
		}
	}
}
