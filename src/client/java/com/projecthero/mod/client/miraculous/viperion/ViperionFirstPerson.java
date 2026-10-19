package com.projecthero.mod.client.miraculous.viperion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.viperion.Viperion;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

/**
 * v0.16.7: Viperion in first person -- {@code ViperionItemInHandMixin} draws the main hand as his bare (gloved) arm
 * while a move has the Lyre in it; {@code ViperionFirstPersonArmMixin} (TAIL of {@code renderHand}) puts the suit sleeve
 * over the arm, the Snake Bracelet on the right wrist (transformed or not) and the Lyre in the fist.
 */
public final class ViperionFirstPerson {
	private ViperionFirstPerson() {
	}

	/** Draw the main hand as his bare arm (the Lyre in it)? */
	public static boolean drawsArm(AbstractClientPlayer player, InteractionHand hand, float partial) {
		if (hand != InteractionHand.MAIN_HAND || !Viperion.is(player) || player.isInvisible() || player.isScoping()
				|| ViperionRender.cover(player, partial) < 0.999f) {
			return false;
		}
		if (player.isUsingItem() && player.getUsedItemHand() == hand) {
			return false;
		}
		return ViperionPose.inHand(player, partial) || Viperion.shielding(player);
	}

	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm, boolean right,
			float ageInTicks, float partial) {
		if (!Miraculous.holds(player, Kwami.SASS)) {
			return;
		}
		boolean slim = ViperionRender.slim(player);
		float cover = ViperionRender.cover(player, partial);
		if (cover > 0f) {
			ViperionRender.renderArm(pose, buffers, light, arm, right, slim, cover);
		}
		if (right) {
			ViperionRender.renderBracelet(pose, buffers, light, arm, slim, Miraculous.transformed(player));
		}
		boolean main = (player.getMainArm() == HumanoidArm.RIGHT) == right;
		if (main && drawsArm(player, InteractionHand.MAIN_HAND, partial)) {
			float f = right ? 1f : -1f;
			pose.pushPose();
			arm.translateAndRotate(pose);
			pose.translate(-f * 1.0f / 16f, 10.0f / 16f, -0.5f / 16f);
			if (Viperion.shielding(player)) {
				pose.mulPose(Axis.XP.rotationDegrees(-90f));
				pose.translate(0f, 3f / 16f, -2f / 16f);
				pose.mulPose(Axis.YP.rotationDegrees((ageInTicks + partial) * 38f));
			} else {
				pose.mulPose(Axis.XP.rotationDegrees(-90f));
				pose.mulPose(Axis.ZP.rotationDegrees(f * 14f));
			}
			pose.scale(0.75f, 0.75f, 0.75f);
			ViperionRender.renderLyre(pose, buffers, light, ViperionLayer.strum(player, partial));
			pose.popPose();
		}
	}
}
