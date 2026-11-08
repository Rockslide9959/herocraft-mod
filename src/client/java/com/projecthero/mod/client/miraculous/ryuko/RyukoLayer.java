package com.projecthero.mod.client.miraculous.ryuko;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.render.OverlayStrips;
import com.projecthero.mod.miraculous.ryuko.Ryuko;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** v0.17.3: Ryuko's suit and sword on every player who has them out, for every viewer ({@link RyukoRender}). */
public class RyukoLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public RyukoLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible()) {
			return;
		}
		float cover = RyukoRender.cover(player, partialTick);
		if (cover <= 0f) {
			return;
		}
		RyukoRender.renderSuit(pose, buffers, light, getParentModel(), RyukoRender.slim(player), cover, RyukoRender.edgeColor(player, partialTick));
		if (cover < 0.999f) {
			OverlayStrips.render(pose, buffers, light, player, getParentModel(), (u, v) -> RyukoRender.order(u, v) > cover, false);
		}
		if (cover > 0.99f && !Ryuko.swordAway(player) && RyukoSequenceClient.swordOut(player, partialTick)) {
			pose.pushPose();
			getParentModel().rightArm.translateAndRotate(pose);
			placeInHand(pose, getParentModel().rightArm.xRot, RyukoRender.slim(player));
			RyukoRender.renderSword(pose, buffers, light, Ryuko.color(Ryuko.element(player)));
			pose.popPose();
		}
	}

	/** Below this forward raise of the arm the blade rests forward-up; above {@link #RAISE_HIGH} it follows the arm. */
	private static final float RAISE_LOW = 0.35f, RAISE_HIGH = 1.4f;
	/** The blade above the arm's forward axis at rest (degrees), and above it with the arm raised (pointing along the arm, a little high). */
	private static final float TILT_REST = 26f, TILT_RAISED = -58f;

	/**
	 * From the arm's shoulder pivot to the middle of her fist (the sword's grip is centred there, {@link RyukoRender#renderSword}),
	 * the blade angled by the wrist.
	 *
	 * <p>v0.17.3 (tester: the sword "is bugged"): the wrist used to be a fixed 42 degrees up from the arm's forward axis, so
	 * at rest the blade stood almost upright and every move that lifts the arm forward (the strikes, lunge, parry, Riposte,
	 * Element Strike) swung it back over her head, hilt first. The wrist now follows the arm: forward-up like a held katana at
	 * rest, along the arm (a little high) once the arm is raised to strike or thrust.
	 *
	 * @param armXRot the arm's x rotation this frame (negative = raised forward)
	 */
	public static void placeInHand(PoseStack pose, float armXRot, boolean slim) {
		pose.translate((slim ? -0.5f : -1f) / 16f, 9.2f / 16f, 0f);
		pose.mulPose(Axis.XP.rotationDegrees(-wristTilt(armXRot)));
	}

	/** The blade's angle above the arm's forward axis, in degrees, for an arm at {@code armXRot}. */
	public static float wristTilt(float armXRot) {
		float raise = net.minecraft.util.Mth.wrapDegrees((float) Math.toDegrees(-armXRot)) * (float) (Math.PI / 180.0);
		float k = net.minecraft.util.Mth.clamp((raise - RAISE_LOW) / (RAISE_HIGH - RAISE_LOW), 0f, 1f);
		k = k * k * (3f - 2f * k);
		return net.minecraft.util.Mth.lerp(k, TILT_REST, TILT_RAISED);
	}
}
