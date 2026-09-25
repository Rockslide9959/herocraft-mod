package com.projecthero.mod.client.spider;

import com.projecthero.mod.ProjectHeroMod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * The Impact Web (v0.12.20): a dense knot of webbing -- a tight core wrapped in three crossed web discs (one
 * per axis), so from any angle it reads as a spun ball of web. Texture is a 32x32 sheet:
 * the core sits at (0,0), and each disc's two faces carry the radial-and-ring web pattern at (0,12).
 */
public final class ImpactWebModel {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ProjectHeroMod.id("impact_web"), "main");
	public static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/impact_web.png");

	private final ModelPart root;

	public ImpactWebModel(ModelPart root) {
		this.root = root;
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("core", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F), PartPose.ZERO);
		CubeListBuilder disc = CubeListBuilder.create().texOffs(0, 12).addBox(-7.0F, -7.0F, -0.5F, 14.0F, 14.0F, 1.0F);
		root.addOrReplaceChild("disc_z", disc, PartPose.ZERO);
		root.addOrReplaceChild("disc_x", disc, PartPose.rotation(0.0F, (float) (Math.PI / 2.0), 0.0F));
		root.addOrReplaceChild("disc_y", disc, PartPose.rotation((float) (Math.PI / 2.0), 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 32, 32);
	}

	public void render(PoseStack pose, VertexConsumer buffer, int light, int overlay) {
		root.render(pose, buffer, light, overlay);
	}
}
