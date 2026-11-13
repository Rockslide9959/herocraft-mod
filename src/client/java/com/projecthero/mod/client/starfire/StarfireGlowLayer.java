package com.projecthero.mod.client.starfire;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.MutationRender;
import com.projecthero.mod.starfire.Starfire;
import com.projecthero.mod.starfire.StarfireAbilityManager;
import com.projecthero.mod.starfire.StarfireConfig;
import com.projecthero.mod.starfire.data.StarfireState;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Starfire's glow overlays, on her own body, every viewer. The power never changes the skin: these are emissive extras.
 * <ul>
 *   <li><b>Green eyes</b>, always: two glowing pixels on the face. At full Fury they blaze -- bigger, white-hot, flickering.</li>
 *   <li><b>Glowing hands</b> while a starbolt is ready (Starbolt to pay for one, R off cooldown): two soft additive shells fitted to
 *   the hand end of each arm (wide or slim), their faces fading to nothing at the edges so the glow has no hard square outline.
 *   The same glow is drawn on the first-person arm ({@code StarfireFirstPersonArmMixin}).</li>
 * </ul>
 * v0.17.3 tester round: the flame hair she had in flight was removed (user: "remove the Starfire hair please, I never wanted
 * that"), and the hand glow -- a 4-pixel cube offset a pixel sideways and hanging below the fist -- was refitted to the hand.
 */
public class StarfireGlowLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	/**
	 * The soft-face texture: 64x64 pixels mapped as a 16x16 UV sheet; the wide hand box sits at (0,0), the slim one at (0,8), every
	 * face a white centre fading to black at its edges ({@code scratchpad/titans/gen_starfire_hand_glow.js}).
	 */
	static final ResourceLocation HAND_GLOW = ProjectHeroMod.id("textures/entity/starfire/hand_glow.png");
	private static final float INNER = 0.25f;
	private static final float OUTER = 1.1f;

	private static ModelPart eyes;
	private static ModelPart blaze;
	/** [wide?0:1][right?0:1][inner?0:1] */
	private static final ModelPart[][][] HANDS = new ModelPart[2][2][2];

	public StarfireGlowLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	private static void bake() {
		if (eyes != null) {
			return;
		}
		eyes = MutationRender.bake(CubeListBuilder.create().texOffs(0, 0)
				.addBox(-3.0f, -4.0f, -4.26f, 2.0f, 1.0f, 0.2f)
				.addBox(1.0f, -4.0f, -4.26f, 2.0f, 1.0f, 0.2f), 4, 4);
		blaze = MutationRender.bake(CubeListBuilder.create().texOffs(0, 0)
				.addBox(-3.5f, -4.5f, -4.4f, 3.0f, 2.0f, 0.2f)
				.addBox(0.5f, -4.5f, -4.4f, 3.0f, 2.0f, 0.2f), 4, 4);
		for (int slim = 0; slim < 2; slim++) {
			float w = slim == 0 ? 4f : 3f;
			for (int right = 0; right < 2; right++) {
				// the vanilla arm cubes: wide right (-3..1) / left (-1..3), slim right (-2..1) / left (-1..2); the hand is the bottom
				// 4 pixels (y 6..10)
				float x0 = right == 0 ? (slim == 0 ? -3f : -2f) : -1f;
				for (int inner = 0; inner < 2; inner++) {
					float grow = inner == 0 ? INNER : OUTER;
					HANDS[slim][right][inner] = MutationRender.bake(CubeListBuilder.create().texOffs(0, slim == 0 ? 0 : 8)
							.addBox(x0, 6.0f, -2.0f, w, 4.0f, 4.0f, new CubeDeformation(grow)), 16, 16);
				}
			}
		}
	}

	/** Is a starbolt ready (or a burst charging / the eye beams on)? Then her fists glow. */
	static boolean handsGlow(AbstractClientPlayer player, StarfireState s) {
		return s.starbolt >= Starfire.boltCost(player, StarfireConfig.BOLT_COST) && Starfire.cooldownRemaining(player, StarfireAbilityManager.STARBOLT) <= 0
				|| s.chargeMove == StarfireState.CHARGE_BURST || s.beams;
	}

	/**
	 * The glow round one hand, in the frame the arm is drawn in (call after {@code arm.translateAndRotate}). Additive, so the
	 * brightness is the colour: the pulse scales the RGB.
	 */
	static void renderHand(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player, boolean right, float t) {
		bake();
		boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
		float pulse = 0.82f + 0.18f * Mth.sin(t * 0.35f);
		VertexConsumer vc = buffers.getBuffer(RenderType.eyes(HAND_GLOW));
		ModelPart[] parts = HANDS[slim ? 1 : 0][right ? 0 : 1];
		parts[0].render(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, scaled(0x6CFF64, 0.85f * pulse));
		parts[1].render(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, scaled(0x2CC834, 0.6f * pulse));
	}

	private static int scaled(int rgb, float k) {
		int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * k));
		int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * k));
		int b = Math.min(255, Math.round((rgb & 0xFF) * k));
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		StarfireState s = Starfire.peek(player);
		if (s == null || !s.hasPower || player.isInvisible()) {
			return;
		}
		bake();
		PlayerModel<AbstractClientPlayer> model = getParentModel();
		VertexConsumer vc = buffers.getBuffer(RenderType.eyes(MutationRender.WHITE));
		float t = ageInTicks + partialTick;
		boolean fury = s.mood >= StarfireConfig.FURY_FULL;
		// the eyes
		pose.pushPose();
		model.head.translateAndRotate(pose);
		eyes.render(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, fury ? 0xFFE8FFD8 : 0xFF5CFF5A);
		if (fury) {
			int a = 150 + (int) (80 * (0.5f + 0.5f * Mth.sin(t * 0.9f)));
			blaze.render(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, (a << 24) | 0x3CFF3A);
		}
		pose.popPose();
		// the hands
		if (handsGlow(player, s)) {
			for (int i = 0; i < 2; i++) {
				ModelPart arm = i == 0 ? model.rightArm : model.leftArm;
				if (!arm.visible) {
					continue;
				}
				pose.pushPose();
				arm.translateAndRotate(pose);
				renderHand(pose, buffers, player, i == 0, t);
				pose.popPose();
			}
		}
	}
}
