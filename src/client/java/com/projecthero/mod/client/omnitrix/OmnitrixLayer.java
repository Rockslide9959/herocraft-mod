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
	/** v0.17.1: the red flash -- the same badge (rim, grey ring) in the red face's colours, so the flashes swap colour, not shape. */
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
			if (com.projecthero.mod.omnitrix.alien.upgrade.UpgradeKit.sliding(p)
					&& com.projecthero.mod.omnitrix.alien.AlienKit.activeKit(p) instanceof com.projecthero.mod.omnitrix.alien.upgrade.UpgradeKit) {
				return; // v0.17.2: Upgrade's Liquid Form is only the puddle -- no badge floating on it
			}
			float[] b = kit.badge();
			boolean red = OmnitrixWatch.red(p, partial);
			int boneId = kit.badgeBone();
			net.minecraft.client.model.geom.ModelPart bone = boneId == AlienClientKit.BADGE_LEFT_ARM ? model.leftArm
					: boneId == AlienClientKit.BADGE_HEAD ? model.head : model.body;
			AlienParts.on(pose, bone, () -> {
				// v0.17.1 (user: "make all the omnitrix badges white ... thats how it is in the show"): the white symbol
				// (badge.png); the red flash is badge_red.png, the same badge in red
				VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(red ? BADGE_RED : BADGE));
				// v0.17.2 (user: "make the omnitrix badge a bit smaller ... maybe make it 2x2 pixels", then "make the omnitrix badge 3x3 pixels"): every badge is 3 px across
				// (Grey Matter's 8 px -- 2.6 at his 0.33 body scale -- is his whole chest; 9 would hang off it)
				float s = b[3] / 2f / 16f;
				float y = b[1] / 16f;
				if (b.length > 4 && b[4] == 1f) {
					// v0.17.1 (user: "four arms badge needs to be on the side of his top left arm"): facing out of the bone's +x side,
					// just outside the sleeve layer
					float x = Math.max(b[0], 3.3f) / 16f, z = b[2] / 16f;
					AlienParts.quad(vc, pose.last(), 0xFFFFFFFF, LightTexture.FULL_BRIGHT, x, y - s, z + s, x, y - s, z - s, x, y + s, z - s, x, y + s, z + s,
							1, 0, 0, 0f, 0f, 1f, 1f);
					return;
				}
				if (b.length > 4 && b[4] == 2f) {
					// v0.17.1 (Grey Matter): on the back, facing backward, just behind the jacket layer
					float x = b[0] / 16f, z = Math.max(b[2], 2.3f) / 16f;
					AlienParts.quad(vc, pose.last(), 0xFFFFFFFF, LightTexture.FULL_BRIGHT, x + s, y - s, z, x - s, y - s, z, x - s, y + s, z, x + s, y + s, z,
							0, 0, 1, 0f, 0f, 1f, 1f);
					return;
				}
				// v0.17.1: just in front of the jacket overlay (-2.25 px), which hid the badge -- and its red beep blinks (the head's
				// z is its own: in front of the hat layer)
				float x = b[0] / 16f, z = (boneId == AlienClientKit.BADGE_BODY ? Math.min(b[2], -2.3f) : b[2]) / 16f;
				AlienParts.quad(vc, pose.last(), 0xFFFFFFFF, LightTexture.FULL_BRIGHT, x - s, y - s, z, x + s, y - s, z, x + s, y + s, z, x - s, y + s, z,
						0, 0, -1, 0f, 0f, 1f, 1f);
			});
			return;
		}
		boolean slim = p.getSkin().model() == PlayerSkin.Model.SLIM;
		AlienParts.on(pose, model.leftArm, () -> OmnitrixWatch.render(pose, buffers, light, p, partial, slim, false));
	}
}
