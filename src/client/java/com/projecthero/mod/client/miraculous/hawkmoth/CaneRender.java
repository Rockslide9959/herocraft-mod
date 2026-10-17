package com.projecthero.mod.client.miraculous.hawkmoth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: Hawk Moth's cane -- a real little 3D model (hand-built cubes, {@code cane.png}): a silver shaft, a dark wrapped
 * grip, and a silver butterfly for a handle (four flat wings round a body with a purple gem). A sword is hidden in the
 * shaft: the blade is always there, inside the sheath, and Cane Sword <b>slides the sheath off it</b> down the cane's
 * line ({@link HawkMothPose#slide}) -- then the sheath rides in the off hand until the blade goes back in.
 *
 * <p>Cane space: the origin is the grip (in the fist), +y runs down the shaft, the handle is above (-y). In hand it is set
 * {@link HawkMothPose#tilt} back from the line of the forearm. Drawn by {@link HawkMothLayer} (everyone sees it) and, in
 * first person, by {@code HawkMothFirstPersonMixin}.
 */
public final class CaneRender {
	public static final ResourceLocation TEX = ProjectHeroMod.id("textures/entity/miraculous/hawk_moth/cane.png");
	/** How far the sheath slides off the blade, model pixels. */
	private static final float SHEATH_TRAVEL = 10.5f;

	private static ModelPart handle;
	private static ModelPart blade;
	private static ModelPart sheath;

	private CaneRender() {
	}

	private static void build() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition h = root.addOrReplaceChild("handle", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-0.5f, -1f, -0.5f, 1f, 4f, 1f, new CubeDeformation(0.25f))
				.texOffs(6, 0).addBox(-0.5f, -4.5f, -0.5f, 1f, 3f, 1f, new CubeDeformation(0.1f)), PartPose.ZERO);
		h.addOrReplaceChild("upper_left", CubeListBuilder.create().texOffs(12, 0).addBox(0.5f, -5.2f, 0f, 3f, 3f, 0f), PartPose.ZERO);
		h.addOrReplaceChild("upper_right", CubeListBuilder.create().texOffs(20, 0).addBox(-3.5f, -5.2f, 0f, 3f, 3f, 0f), PartPose.ZERO);
		h.addOrReplaceChild("lower_left", CubeListBuilder.create().texOffs(12, 4).addBox(0.5f, -2.4f, 0f, 2f, 2f, 0f), PartPose.ZERO);
		h.addOrReplaceChild("lower_right", CubeListBuilder.create().texOffs(20, 4).addBox(-2.5f, -2.4f, 0f, 2f, 2f, 0f), PartPose.ZERO);
		root.addOrReplaceChild("blade", CubeListBuilder.create().texOffs(6, 8).addBox(-0.5f, 2.5f, -0.5f, 1f, 10f, 1f,
				new CubeDeformation(-0.05f, 0f, -0.3f)), PartPose.ZERO);
		PartDefinition s = root.addOrReplaceChild("sheath", CubeListBuilder.create().texOffs(0, 8).addBox(-0.5f, 3f, -0.5f, 1f, 10f, 1f,
				new CubeDeformation(0.22f)), PartPose.ZERO);
		s.addOrReplaceChild("ferrule", CubeListBuilder.create().texOffs(12, 8).addBox(-0.5f, 13f, -0.5f, 1f, 1f, 1f, new CubeDeformation(0.3f)),
				PartPose.ZERO);
		ModelPart baked = LayerDefinition.create(mesh, 32, 32).bakeRoot();
		handle = baked.getChild("handle");
		blade = baked.getChild("blade");
		sheath = baked.getChild("sheath");
	}

	/** Moves {@code pose} from the model's space to the grip in {@code side}'s fist, the cane tilted {@code tilt} back. */
	public static void toGrip(PoseStack pose, PlayerModel<?> model, HumanoidArm side, float tilt) {
		model.translateToHand(side, pose);
		pose.translate((side == HumanoidArm.RIGHT ? -1f : 1f) / 16f, 8.5f / 16f, 0f);
		pose.mulPose(Axis.XP.rotation(tilt));
	}

	/** The cane in his hands (and the sheath in the other once the blade is out). */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, PlayerModel<?> model, Player player, float partial) {
		if (handle == null) {
			build();
		}
		HumanoidArm mainSide = player.getMainArm();
		float tilt = HawkMothPose.tilt(player, partial);
		float slide = HawkMothPose.slide(player, partial);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));
		pose.pushPose();
		toGrip(pose, model, mainSide, tilt);
		draw(pose, vc, light, slide, true);
		pose.popPose();
		if (slide >= 1f) {
			pose.pushPose();
			toGrip(pose, model, mainSide.getOpposite(), HawkMothPose.HOLD_CANE * 0.7f);
			sheath.y = -4.5f;
			sheath.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
			sheath.y = 0f;
			pose.popPose();
		}
	}

	/** First person: the cane in the fist of the arm vanilla just drew ({@code pose} is at that arm's parent). */
	public static void renderFirstPerson(PoseStack pose, MultiBufferSource buffers, int light, PlayerModel<?> model, Player player, boolean right,
			float partial) {
		if (handle == null) {
			build();
		}
		HumanoidArm side = right ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
		if (side != player.getMainArm()) {
			return;
		}
		float slide = HawkMothPose.slide(player, partial);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));
		pose.pushPose();
		toGrip(pose, model, side, HawkMothPose.tilt(player, partial) + 0.35f);
		draw(pose, vc, light, slide, true);
		pose.popPose();
	}

	private static void draw(PoseStack pose, VertexConsumer vc, int light, float slide, boolean withSheath) {
		handle.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
		if (slide > 0f) {
			blade.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
		}
		if (withSheath && slide < 1f) {
			sheath.y = slide * SHEATH_TRAVEL;
			sheath.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
			sheath.y = 0f;
		}
	}
}
