package com.projecthero.mod.client.quirk.overhaul;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.quirk.overhaul.Overhaul;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * v0.16.7 (revamp: spike limbs -- thorns on the gauntlets, shoulders, back and the extra arms' fists; and the gloves,
 * drawn on the forearms while they are on): the Chronostasis Fusion form on every viewer -- the body (already scaled up by the synced SCALE attribute) is plated in
 * the stone he absorbed: pauldrons, gauntlets, a breastplate and back hump, knee plates, and two extra stone arms
 * growing from his back that sway with his breathing. Drawn with the block atlas's deepslate / stone sprites, so the
 * plates read as the real ground he took in.
 */
public class OverhaulFusionLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final ResourceLocation PLATE = ResourceLocation.withDefaultNamespace("block/cobbled_deepslate");
	private static final ResourceLocation TRIM = ResourceLocation.withDefaultNamespace("block/polished_deepslate");
	private static final ResourceLocation ARM = ResourceLocation.withDefaultNamespace("block/stone");
	private static final ResourceLocation SPIKE = ResourceLocation.withDefaultNamespace("block/dripstone_block");
	private static final ResourceLocation GLOVE = ResourceLocation.withDefaultNamespace("block/black_wool");
	/** The vertex tint of the box being drawn (stone: almost white; the gloves: a dark plum). */
	private static float tr = 0.92f, tg = 0.9f, tb = 0.9f;

	public OverhaulFusionLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !Overhaul.is(player)) {
			return;
		}
		var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
		if (!Overhaul.fused(player)) {
			if (Overhaul.gloved(player)) {
				gloves(pose, buffers, light, player, atlas.apply(GLOVE));
			}
			return;
		}
		TextureAtlasSprite plate = atlas.apply(PLATE);
		TextureAtlasSprite trim = atlas.apply(TRIM);
		TextureAtlasSprite arm = atlas.apply(ARM);
		TextureAtlasSprite spike = atlas.apply(SPIKE);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
		PlayerModel<AbstractClientPlayer> m = getParentModel();
		boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
		float aw = slim ? 3f : 4f;

		// right arm: x -3..(aw-3), y -2..10, z -2..2 -- a pauldron and a gauntlet
		part(pose, m.rightArm, () -> {
			box(pose, vc, plate, light, -3.9f, -2.9f, -2.9f, aw - 2.1f, 3.2f, 2.9f);
			box(pose, vc, trim, light, -4.3f, -2.4f, -1.6f, -3.6f, 1.8f, 1.6f);
			box(pose, vc, plate, light, -3.7f, 5.2f, -2.7f, aw - 2.3f, 10.6f, 2.7f);
			// spike limbs: stone thorns jut from the gauntlet and the shoulder
			spike(pose, vc, spike, light, -3.8f, 7.5f, 0f, -1.5f, 0.15f, 3.2f);
			spike(pose, vc, spike, light, -3.6f, 9.6f, -1.2f, -1.4f, 0.4f, 2.4f);
			spike(pose, vc, spike, light, -2.5f, -3f, 0f, -0.4f, -1.4f, 2.6f);
		});
		part(pose, m.leftArm, () -> {
			box(pose, vc, plate, light, -aw + 2.1f, -2.9f, -2.9f, 3.9f, 3.2f, 2.9f);
			box(pose, vc, trim, light, 3.6f, -2.4f, -1.6f, 4.3f, 1.8f, 1.6f);
			box(pose, vc, plate, light, -aw + 2.3f, 5.2f, -2.7f, 3.7f, 10.6f, 2.7f);
			spike(pose, vc, spike, light, 3.8f, 7.5f, 0f, 1.5f, 0.15f, 3.2f);
			spike(pose, vc, spike, light, 3.6f, 9.6f, -1.2f, 1.4f, 0.4f, 2.4f);
			spike(pose, vc, spike, light, 2.5f, -3f, 0f, 0.4f, -1.4f, 2.6f);
		});
		part(pose, m.body, () -> {
			// breastplate, its ridge, the back hump
			box(pose, vc, plate, light, -4.7f, -0.6f, -2.9f, 4.7f, 7.4f, 2.9f);
			box(pose, vc, trim, light, -1.2f, 0.2f, -3.5f, 1.2f, 6.8f, -2.8f);
			box(pose, vc, plate, light, -3.6f, -0.2f, 2.8f, 3.6f, 6.0f, 4.4f);
			// two extra stone arms from the back, swaying with his breath
			float sway = Mth.sin(ageInTicks * 0.09f) * 0.12f;
			extraArm(pose, vc, arm, plate, spike, light, -1f, sway);
			extraArm(pose, vc, arm, plate, spike, light, 1f, -sway);
			// a ridge of thorns down the back hump
			spike(pose, vc, spike, light, 0f, 1.2f, 4.2f, 0f, -0.5f, 1.6f);
			spike(pose, vc, spike, light, 0f, 3.6f, 4.2f, 0f, -0.3f, 1.4f);
		});
		part(pose, m.rightLeg, () -> box(pose, vc, plate, light, -2.6f, 3.2f, -2.8f, 2.6f, 7.0f, 2.4f));
		part(pose, m.leftLeg, () -> box(pose, vc, plate, light, -2.6f, 3.2f, -2.8f, 2.6f, 7.0f, 2.4f));
	}

	private static void extraArm(PoseStack pose, VertexConsumer vc, TextureAtlasSprite arm, TextureAtlasSprite plate, TextureAtlasSprite spike, int light,
			float side, float sway) {
		pose.pushPose();
		pose.translate(side * 3.4f / 16f, 1.5f / 16f, 3.8f / 16f);
		pose.mulPose(Axis.ZP.rotation(side * (0.75f + sway)));
		pose.mulPose(Axis.XP.rotation(-0.55f));
		// upper arm, forearm (bent forward), a heavy fist
		box(pose, vc, arm, light, -1.6f, 0f, -1.6f, 1.6f, 6.5f, 1.6f);
		pose.translate(0, 6.2f / 16f, 0);
		pose.mulPose(Axis.XP.rotation(0.9f));
		box(pose, vc, arm, light, -1.4f, 0f, -1.4f, 1.4f, 6.0f, 1.4f);
		box(pose, vc, plate, light, -1.9f, 5.4f, -1.9f, 1.9f, 8.4f, 1.9f);
		// the fist is a spiked club
		spike(pose, vc, spike, light, side * 1.9f, 7f, 0f, side * 1.5f, 0.3f, 2.2f);
		spike(pose, vc, spike, light, 0f, 8.4f, 0f, 0f, 1.5f, 2.4f);
		pose.popPose();
	}

	/**
	 * A stone thorn from (x, y, z) pointing along (dx, dy, dz) (model px; y grows downward), about {@code |d|} px long:
	 * three stacked boxes narrowing to a point.
	 */
	private static void spike(PoseStack pose, VertexConsumer vc, TextureAtlasSprite s, int light, float x, float y, float z, float dx, float dy, float dz) {
		float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		float l = Math.max(1.5f, len) * 1.2f;
		pose.pushPose();
		pose.translate(x / 16f, y / 16f, z / 16f);
		// turn local -Y onto the direction
		pose.mulPose(new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0f, -1f, 0f), new org.joml.Vector3f(dx, dy, dz).normalize()));
		box(pose, vc, s, light, -0.9f, -l * 0.4f, -0.9f, 0.9f, 0.4f, 0.9f);
		box(pose, vc, s, light, -0.55f, -l * 0.75f, -0.55f, 0.55f, -l * 0.4f + 0.1f, 0.55f);
		box(pose, vc, s, light, -0.25f, -l, -0.25f, 0.25f, -l * 0.75f + 0.1f, 0.25f);
		pose.popPose();
	}

	/** v0.16.7 revamp: the gloves, drawn over both forearms and hands while they are on (a dark plum). */
	private void gloves(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, TextureAtlasSprite s) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
		PlayerModel<AbstractClientPlayer> m = getParentModel();
		boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
		float aw = slim ? 3f : 4f;
		tr = 0.42f;
		tg = 0.34f;
		tb = 0.48f;
		try {
			part(pose, m.rightArm, () -> {
				box(pose, vc, s, light, -3.35f, 6.4f, -2.35f, aw - 2.65f, 10.35f, 2.35f);
				box(pose, vc, s, light, -3.6f, 5.6f, -2.6f, aw - 2.4f, 6.6f, 2.6f);
			});
			part(pose, m.leftArm, () -> {
				box(pose, vc, s, light, -aw + 2.65f, 6.4f, -2.35f, 3.35f, 10.35f, 2.35f);
				box(pose, vc, s, light, -aw + 2.4f, 5.6f, -2.6f, 3.6f, 6.6f, 2.6f);
			});
		} finally {
			tr = 0.92f;
			tg = 0.9f;
			tb = 0.9f;
		}
	}

	private static void part(PoseStack pose, ModelPart part, Runnable draw) {
		if (!part.visible) {
			return;
		}
		pose.pushPose();
		part.translateAndRotate(pose);
		draw.run();
		pose.popPose();
	}

	/** One textured box in model pixels (1/16 block) in the current part's space. */
	static void box(PoseStack pose, VertexConsumer vc, TextureAtlasSprite s, int light, float x0, float y0, float z0, float x1, float y1, float z1) {
		float a = x0 / 16f, b = y0 / 16f, c = z0 / 16f, d = x1 / 16f, e = y1 / 16f, f = z1 / 16f;
		float sx = Math.min(16f, x1 - x0), sy = Math.min(16f, y1 - y0), sz = Math.min(16f, z1 - z0);
		PoseStack.Pose p = pose.last();
		// -Z (front of the model) / +Z
		quad(vc, p, s, light, sx, sy, a, e, c, d, e, c, d, b, c, a, b, c, 0, 0, -1);
		quad(vc, p, s, light, sx, sy, d, e, f, a, e, f, a, b, f, d, b, f, 0, 0, 1);
		// -X / +X
		quad(vc, p, s, light, sz, sy, a, e, f, a, e, c, a, b, c, a, b, f, -1, 0, 0);
		quad(vc, p, s, light, sz, sy, d, e, c, d, e, f, d, b, f, d, b, c, 1, 0, 0);
		// -Y (the model's top: y grows downward) / +Y
		quad(vc, p, s, light, sx, sz, a, b, c, d, b, c, d, b, f, a, b, f, 0, -1, 0);
		quad(vc, p, s, light, sx, sz, a, e, f, d, e, f, d, e, c, a, e, c, 0, 1, 0);
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose p, TextureAtlasSprite s, int light, float w, float h,
			float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
			float nx, float ny, float nz) {
		float u0 = s.getU0(), v0 = s.getV0();
		float u1 = s.getU(w / 16f), v1 = s.getV(h / 16f);
		vertex(vc, p, x0, y0, z0, u0, v1, light, nx, ny, nz);
		vertex(vc, p, x1, y1, z1, u1, v1, light, nx, ny, nz);
		vertex(vc, p, x2, y2, z2, u1, v0, light, nx, ny, nz);
		vertex(vc, p, x3, y3, z3, u0, v0, light, nx, ny, nz);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int light, float nx, float ny, float nz) {
		vc.addVertex(p, x, y, z).setColor(tr, tg, tb, 1f).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}
}
