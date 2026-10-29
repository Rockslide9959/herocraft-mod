package com.projecthero.mod.client.omnitrix;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.omnitrix.Omnitrix;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;

/**
 * v0.17.1: on every player renderer -- the watch on a human wearer's left wrist ({@link OmnitrixWatch}), and on an alien its
 * kit's extra cubes ({@link AlienClientKit#renderExtras}) and the glowing white Omnitrix badge on the chest (flashing red in the
 * last 15 s).
 */
public final class OmnitrixLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	/** The alien's chest badge: the show's white Omnitrix symbol (scratchpad/gen_omnitrix_badge.js). */
	public static final net.minecraft.resources.ResourceLocation BADGE = com.projecthero.mod.ProjectHeroMod.id("textures/entity/omnitrix/badge.png");
	/** The badge's red flash (the whole texture, same rim / ring as {@link #BADGE}). */
	public static final net.minecraft.resources.ResourceLocation BADGE_RED = com.projecthero.mod.ProjectHeroMod.id("textures/entity/omnitrix/badge_red.png");

	public OmnitrixLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
			float partial, float age, float netHeadYaw, float headPitch) {
		if (!Omnitrix.hasPower(p) || p.isInvisible()) {
			return;
		}
		PlayerModel<AbstractClientPlayer> model = getParentModel();
		AlienClientKit kit = AlienClients.shownKit(p);
		if (kit != null) {
			kit.renderExtras(pose, buffers, light, p, model, partial, age);
			float[] b = kit.badge();
			boolean red = OmnitrixWatch.red(p, partial);
			AlienParts.on(pose, model.body, () -> {
				// v0.17.1 (user: "make all the omnitrix badges white ... thats how it is in the show"): the white symbol
				// (badge.png, the whole texture); the red flash still shows the red watch face (its 64,64 quadrant)
				VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(red ? OmnitrixWatch.TEX_RED : BADGE));
				float u0 = red ? 0.5f : 0f, v0 = red ? 0.5f : 0f;
				float s = b[3] / 2f / 16f;
				// v0.17.1: just in front of the jacket overlay (-2.25 px), which hid the badge -- and its red beep blinks
				float x = b[0] / 16f, y = b[1] / 16f, z = Math.min(b[2], -2.3f) / 16f;
				AlienParts.quad(vc, pose.last(), 0xFFFFFFFF, LightTexture.FULL_BRIGHT, x - s, y - s, z, x + s, y - s, z, x + s, y + s, z, x - s, y + s, z,
						0, 0, -1, u0, v0, 1f, 1f);
			});
			return;
		}
		boolean slim = p.getSkin().model() == PlayerSkin.Model.SLIM;
		AlienParts.on(pose, model.leftArm, () -> OmnitrixWatch.render(pose, buffers, light, p, partial, slim, false));
	}
}
