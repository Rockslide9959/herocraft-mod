package com.projecthero.mod.client.mutation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.13.22: drawing helpers for mutation overlays (see {@link MutationOverlays}).
 *
 * <ul>
 *   <li>{@link #shell} -- the whole body again, slightly inflated, with a 64x64 skin-layout texture: stone skin,
 *       crystal plating, frost armour, a flame body, a shadow silhouette. {@code THIN} sits just over the skin,
 *       {@code THICK} reads as armour. Emissive shells glow in the dark.</li>
 *   <li>{@link #eyes} -- two glowing eye pixels (Laser Vision, Telekinesis, Electrokinesis ...).</li>
 *   <li>{@link #onPart} -- any small model you bake with {@link #bake} (a ring, a crystal shard, a blade) drawn in a
 *       body part's frame, so it follows every pose. Part-local axes: +Y runs DOWN the limb toward the hand/foot,
 *       the arm pivot is the shoulder (a hand is at about y = 10), -Z is the front of the body.</li>
 * </ul>
 */
public final class MutationRender {
	public enum Shell { THIN, THICK }

	/** A 4x4 plain white texture, for tinted solid / emissive geometry. */
	public static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/mutation/white.png");

	private static PlayerModel<AbstractClientPlayer> thinWide;
	private static PlayerModel<AbstractClientPlayer> thinSlim;
	private static PlayerModel<AbstractClientPlayer> thickWide;
	private static PlayerModel<AbstractClientPlayer> thickSlim;
	private static ModelPart eyes;

	private MutationRender() {
	}

	private static PlayerModel<AbstractClientPlayer> shellModel(Shell shell, boolean slim) {
		if (thinWide == null) {
			thinWide = shellOf(0.12f, false);
			thinSlim = shellOf(0.12f, true);
			thickWide = shellOf(0.45f, false);
			thickSlim = shellOf(0.45f, true);
		}
		return shell == Shell.THIN ? (slim ? thinSlim : thinWide) : (slim ? thickSlim : thickWide);
	}

	private static PlayerModel<AbstractClientPlayer> shellOf(float inflate, boolean slim) {
		MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(inflate), slim);
		PlayerModel<AbstractClientPlayer> m = new PlayerModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot(), slim);
		// only the base body parts: the outer skin layers are never posed by us and would sit wrong
		m.hat.visible = false;
		m.jacket.visible = false;
		m.leftSleeve.visible = false;
		m.rightSleeve.visible = false;
		m.leftPants.visible = false;
		m.rightPants.visible = false;
		return m;
	}

	/**
	 * Draws the body again over the player with {@code texture} (64x64, standard player-skin layout; transparent
	 * pixels leave the skin showing). {@code argb} tints it (alpha = opacity).
	 */
	public static void shell(MutationOverlays.Context ctx, ResourceLocation texture, Shell shell, int argb, boolean emissive) {
		boolean slim = "slim".equals(ctx.player().getSkin().model().id());
		PlayerModel<AbstractClientPlayer> m = shellModel(shell, slim);
		HumanoidModel<AbstractClientPlayer> parent = ctx.model();
		parent.copyPropertiesTo(m);
		m.hat.visible = false;
		RenderType type = emissive ? RenderType.eyes(texture) : RenderType.entityTranslucent(texture);
		m.renderToBuffer(ctx.pose(), ctx.buffers().getBuffer(type), emissive ? 0xF000F0 : ctx.light(),
				OverlayTexture.NO_OVERLAY, argb);
	}

	/** Two glowing eyes on the face, tinted {@code argb}. */
	public static void eyes(MutationOverlays.Context ctx, int argb) {
		if (eyes == null) {
			MeshDefinition mesh = new MeshDefinition();
			mesh.getRoot().addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(0, 0)
					.addBox(-3.0f, -4.0f, -4.25f, 2.0f, 1.0f, 0.2f)
					.addBox(1.0f, -4.0f, -4.25f, 2.0f, 1.0f, 0.2f), PartPose.ZERO);
			eyes = LayerDefinition.create(mesh, 4, 4).bakeRoot();
		}
		PoseStack pose = ctx.pose();
		pose.pushPose();
		ctx.model().head.translateAndRotate(pose);
		eyes.render(pose, ctx.buffers().getBuffer(RenderType.eyes(WHITE)), 0xF000F0, OverlayTexture.NO_OVERLAY, argb);
		pose.popPose();
	}

	/** Draws {@code part} in the frame of body part {@code bone} (e.g. {@code ctx.model().rightArm}). */
	public static void onPart(MutationOverlays.Context ctx, ModelPart bone, ModelPart part, ResourceLocation texture,
			int argb, boolean emissive) {
		PoseStack pose = ctx.pose();
		pose.pushPose();
		bone.translateAndRotate(pose);
		RenderType type = emissive ? RenderType.eyes(texture) : RenderType.entityTranslucentCull(texture);
		part.render(pose, ctx.buffers().getBuffer(type), emissive ? 0xF000F0 : ctx.light(), OverlayTexture.NO_OVERLAY, argb);
		pose.popPose();
	}

	/** Bakes a small standalone model (no model-layer registration needed). */
	public static ModelPart bake(CubeListBuilder cubes, int texW, int texH) {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("part", cubes, PartPose.ZERO);
		return LayerDefinition.create(mesh, texW, texH).bakeRoot();
	}

	/** Packs a colour; {@code a,r,g,b} in 0..255. */
	public static int argb(int a, int r, int g, int b) {
		return (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
	}

	/** A slow 0..1 pulse for breathing glows. */
	public static float pulse(MutationOverlays.Context ctx, float periodTicks) {
		return 0.5f + 0.5f * (float) Math.sin((ctx.ageInTicks() + ctx.partialTick()) * (Math.PI * 2 / periodTicks));
	}
}
