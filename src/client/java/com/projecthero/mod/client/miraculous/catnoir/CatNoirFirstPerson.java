package com.projecthero.mod.client.miraculous.catnoir;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.miraculous.catnoir.CatNoir;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: Cat Noir in first person.
 * <ul>
 *   <li>{@code CatNoirItemInHandMixin}: while the baton is out in the hand (or the hand is charged with CATACLYSM) the
 *       main hand is drawn as his bare (gloved) arm instead of whatever it holds, with vanilla's swing.</li>
 *   <li>{@code CatNoirFirstPersonArmMixin} (TAIL of {@code renderHand}, the pose stack where the arm was just drawn): the
 *       suit sleeve over it, then the baton in the fist -- out to staff length, whirling for the Spin -- and the CATACLYSM
 *       wrap.</li>
 * </ul>
 */
public final class CatNoirFirstPerson {
	private CatNoirFirstPerson() {
	}

	/** Draw the main hand as his bare arm (the baton in it, or the charged hand)? */
	public static boolean drawsArm(AbstractClientPlayer player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !CatNoir.active(player) || player.isInvisible() || player.isScoping()) {
			return false;
		}
		if (player.isUsingItem() && player.getUsedItemHand() == hand) {
			return false;
		}
		CatNoirRender.BatonAt at = CatNoirRender.baton(player, 0f).at();
		return at == CatNoirRender.BatonAt.HAND || at == CatNoirRender.BatonAt.SPIN || CatNoir.charged(player);
	}

	/** Is {@code arm} (a part of {@code player}'s model) his main arm? */
	public static boolean isMainArm(Player player, boolean rightArm) {
		return (player.getMainArm() == HumanoidArm.RIGHT) == rightArm;
	}

	/** The baton in the first-person fist; {@code arm} still holds the pose it was just drawn with. */
	public static void renderBaton(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm,
			boolean rightArm, float ageInTicks, float partial) {
		if (!isMainArm(player, rightArm)) {
			return;
		}
		CatNoirRender.Baton b = CatNoirRender.baton(player, partial);
		if (b.at() != CatNoirRender.BatonAt.HAND && b.at() != CatNoirRender.BatonAt.SPIN) {
			return;
		}
		boolean rotted = CatNoir.rotted(player);
		float f = rightArm ? 1f : -1f;
		pose.pushPose();
		arm.translateAndRotate(pose);
		pose.translate(-f * 1.0f / 16f, 10.0f / 16f, -0.5f / 16f);
		float e = b.extension();
		if (b.at() == CatNoirRender.BatonAt.SPIN) {
			pose.mulPose(Axis.YP.rotationDegrees((ageInTicks + partial) * 47f));
			pose.mulPose(Axis.XP.rotationDegrees(-90f));
			float side = CatNoirBaton.COLLAPSED + (CatNoirRender.SPIN_SIDE - CatNoirBaton.COLLAPSED) * e;
			CatNoirBaton.render(pose, buffers, light, side, CatNoirRender.SPIN_SIDE, side, CatNoirRender.SPIN_SIDE, rotted);
		} else {
			// up out of the fist and tipped in toward the middle of the view
			pose.mulPose(Axis.XP.rotationDegrees(-90f));
			pose.mulPose(Axis.ZP.rotationDegrees(f * 12f));
			float front = CatNoirBaton.COLLAPSED + (CatNoirRender.HAND_FRONT - CatNoirBaton.COLLAPSED) * e;
			float back = CatNoirBaton.COLLAPSED + (CatNoirRender.HAND_BACK - CatNoirBaton.COLLAPSED) * e;
			CatNoirBaton.render(pose, buffers, light, front, CatNoirRender.HAND_FRONT, back, CatNoirRender.HAND_BACK, rotted);
		}
		pose.popPose();
	}
}
