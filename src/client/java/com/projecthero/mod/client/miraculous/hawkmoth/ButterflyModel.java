package com.projecthero.mod.client.miraculous.hawkmoth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.16.6: one little butterfly as a real 3D model -- a body and two wings (flat 6x6 planes hinged on the body) beating
 * up and down. The akumas use the dark-purple skin ({@link #AKUMA}); every white butterfly of Hawk Moth's swarm (flight,
 * dash, shield, Swarm Blind, the one in his cupped hands) uses {@link #WHITE}, tinted by the vertex colour when it darkens.
 *
 * <p>Model space is world-up (+y up); the butterfly heads along +z. {@link #render} draws it at the pose's origin.
 */
public final class ButterflyModel {
	public static final ResourceLocation AKUMA = ProjectHeroMod.id("textures/entity/miraculous/hawk_moth/akuma.png");
	public static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/miraculous/hawk_moth/white_butterfly.png");

	private static ModelPart body;
	private static ModelPart left;
	private static ModelPart right;

	private ButterflyModel() {
	}

	private static void build() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 12).addBox(-0.5f, -0.5f, -2f, 1f, 1f, 4f), PartPose.ZERO);
		root.addOrReplaceChild("left", CubeListBuilder.create().texOffs(0, 0).addBox(0f, 0f, -3f, 6f, 0f, 6f), PartPose.offset(0.5f, 0f, 0f));
		root.addOrReplaceChild("right", CubeListBuilder.create().texOffs(0, 6).addBox(-6f, 0f, -3f, 6f, 0f, 6f), PartPose.offset(-0.5f, 0f, 0f));
		ModelPart baked = LayerDefinition.create(mesh, 32, 32).bakeRoot();
		body = baked.getChild("body");
		left = baked.getChild("left");
		right = baked.getChild("right");
	}

	/**
	 * One butterfly at the pose's origin, heading {@code yawDeg} (0 = +z) and pitched {@code pitchDeg}, wings at beat phase
	 * {@code beat} (radians), {@code scale} blocks per 16 model pixels, colour {@code argb} multiplied in.
	 */
	public static void render(PoseStack pose, VertexConsumer vc, int light, float yawDeg, float pitchDeg, float beat, float scale, int argb) {
		if (body == null) {
			build();
		}
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(yawDeg));
		pose.mulPose(Axis.XP.rotationDegrees(-pitchDeg));
		pose.scale(scale, scale, scale);
		float flap = 0.25f + 0.95f * Mth.sin(beat);
		left.zRot = flap;
		right.zRot = -flap;
		body.render(pose, vc, light, OverlayTexture.NO_OVERLAY, argb);
		left.render(pose, vc, light, OverlayTexture.NO_OVERLAY, argb);
		right.render(pose, vc, light, OverlayTexture.NO_OVERLAY, argb);
		pose.popPose();
	}
}
