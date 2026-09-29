package com.projecthero.mod.client.maxsteel;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.maxsteel.MaxSteel;
import com.projecthero.mod.maxsteel.MaxSteelConfig;
import com.projecthero.mod.maxsteel.data.MaxSteelFx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.2: the hand-held T.U.R.B.O. gear on any Max Steel in view (third person) -- the Turbo Blast charge orb
 * growing in the right fist, and the Turbo Cannon's arm cannon with its charge glowing at the muzzle. Read off the
 * synced {@link MaxSteelFx}, so everyone sees another player charging. {@link #renderFirstPerson} draws the same
 * two things on the local player's own first-person arm.
 *
 * <p>Replaces v0.6.17's tinted-elytra wings layer: Turbo Flight's form now has its own wings in the suit model.
 */
public class MaxSteelGearLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public MaxSteelGearLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !MaxSteel.isTransformed(player)) {
			return;
		}
		drawGear(pose, buffers, light, player, getParentModel().rightArm, partialTick);
	}

	/** First person: called from {@code PlayerRendererHandMixin} with the vanilla right arm it just drew. */
	public static void renderFirstPerson(PoseStack pose, MultiBufferSource buffers, int light, Player player, ModelPart arm) {
		if (!MaxSteel.isTransformed(player)) {
			return;
		}
		float pt = net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		drawGear(pose, buffers, light, player, arm, pt);
	}

	private static void drawGear(PoseStack pose, MultiBufferSource buffers, int light, Player player, ModelPart arm, float pt) {
		MaxSteelFx fx = player.getAttachedOrElse(ModAttachments.MAX_STEEL_FX, null);
		if (fx == null) {
			return;
		}
		long now = player.level().getGameTime();
		float time = now + pt;

		float formed = MaxSteelArmCannon.formed(player, pt);
		MaxSteelArmCannon.render(pose, buffers, light, arm, formed);

		if (fx.cannonChargeStart() != 0L && formed >= 0.999f) {
			float charge = Math.min(1f, (now - fx.cannonChargeStart() + pt) / MaxSteelConfig.CANNON_MAX_CHARGE_TICKS);
			pose.pushPose();
			arm.translateAndRotate(pose);
			pose.translate(MaxSteelArmCannon.MUZZLE_X / 16.0f, MaxSteelArmCannon.MUZZLE_Y / 16.0f, 0.0f);
			float pulse = 1f + 0.12f * (float) Math.sin(time * (0.6f + charge));
			VertexConsumer vc = TurboDraw.buffer(buffers);
			TurboDraw.orb(vc, pose, (0.05f + 0.09f * charge) * pulse, TurboDraw.BLUE, time * 18f, 0.9f);
			pose.popPose();
		} else if (fx.blastChargeStart() != 0L) {
			float held = now - fx.blastChargeStart() + pt;
			if (held < 2f) {
				return;
			}
			float charge = Math.min(1f, held / MaxSteelConfig.BLAST_MAX_CHARGE_TICKS);
			int stage = 0;
			for (float s : MaxSteelConfig.BLAST_STAGES) {
				if (charge >= s) {
					stage++;
				}
			}
			pose.pushPose();
			arm.translateAndRotate(pose);
			pose.translate(-1.0f / 16.0f, 12.0f / 16.0f, 0.0f);
			float pulse = 1f + (stage >= 3 ? 0.18f : 0.08f) * (float) Math.sin(time * 1.3f);
			int colour = TurboDraw.toWhite(TurboDraw.CYAN, stage >= 3 ? 0.35f : 0.0f);
			VertexConsumer vc = TurboDraw.buffer(buffers);
			TurboDraw.orb(vc, pose, (0.04f + 0.035f * stage + 0.03f * charge) * pulse, colour, time * (12f + 10f * stage), 0.95f);
			// stage 2+: orbiting sparks
			for (int k = 0; k < stage * 2; k++) {
				pose.pushPose();
				pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(time * 30f + k * 360f / (stage * 2)));
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(k * 57f));
				pose.translate(0.12f + 0.03f * stage, 0f, 0f);
				TurboDraw.box(vc, pose.last(), 0.012f, 0.012f, 0.05f, 0xFFFFFF, 0.9f);
				pose.popPose();
			}
			pose.popPose();
		}
	}
}
