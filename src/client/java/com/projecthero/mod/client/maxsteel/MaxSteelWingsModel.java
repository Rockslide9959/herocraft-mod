package com.projecthero.mod.client.maxsteel;

import com.projecthero.mod.ProjectHeroMod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The blue extended wings Max Steel's Turbo Flight mode deploys (v0.6.17). Geometry is a straight copy
 * of vanilla's elytra wing cubes so the vanilla {@code elytra.png} maps onto it cleanly; the pose is
 * held permanently in vanilla's <b>fully-deployed gliding</b> position (v0.6.18 -- {@code xRot 0.35},
 * {@code zRot ∓π/2}, exactly what {@code ElytraModel.setupAnim} produces at full flap while flying)
 * and the whole thing is tinted blue at render time.
 */
public final class MaxSteelWingsModel {
	public static final ModelLayerLocation LAYER =
			new ModelLayerLocation(ProjectHeroMod.id("max_steel_wings"), "main");

	private final ModelPart root;
	private final ModelPart leftWing;
	private final ModelPart rightWing;

	public MaxSteelWingsModel(ModelPart root) {
		this.root = root;
		this.leftWing = root.getChild("left_wing");
		this.rightWing = root.getChild("right_wing");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition parts = mesh.getRoot();
		CubeDeformation grow = new CubeDeformation(1.0F);
		parts.addOrReplaceChild("left_wing", CubeListBuilder.create()
				.texOffs(22, 0).addBox(-10.0F, 0.0F, 0.0F, 10.0F, 20.0F, 2.0F, grow),
				PartPose.offset(5.0F, 0.0F, 0.0F));
		parts.addOrReplaceChild("right_wing", CubeListBuilder.create()
				.texOffs(22, 0).mirror().addBox(0.0F, 0.0F, 0.0F, 10.0F, 20.0F, 2.0F, grow),
				PartPose.offset(-5.0F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 64, 32);
	}

	/** Render the wings in vanilla's fully-deployed glide pose, tinted by {@code argb}. */
	public void render(PoseStack pose, VertexConsumer buffer, int light, int overlay, int argb) {
		leftWing.xRot = 0.34906584F;   // ~20 deg forward tilt
		leftWing.yRot = 0.0F;
		leftWing.zRot = -1.5707964F;   // wings swing from "down" to straight out to the side
		rightWing.xRot = 0.34906584F;
		rightWing.yRot = 0.0F;
		rightWing.zRot = 1.5707964F;
		root.render(pose, buffer, light, overlay, argb);
	}
}
