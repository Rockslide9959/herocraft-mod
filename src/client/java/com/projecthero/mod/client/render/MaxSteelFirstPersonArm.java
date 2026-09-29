package com.projecthero.mod.client.render;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.armor.SuperheroArmorVisuals;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
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
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.2: the Max Steel first-person sleeve. The suit is now the user's own plain player rig on 128x128 textures (the
 * skin in the top-left quadrant, form armour in the rest), so the shared {@link SuperheroFirstPersonArm} (64x64,
 * crimson_vanguard panel UVs) no longer lines up. This is the same two cubes per arm as {@code geo/max_steel.geo.json}
 * -- base undersuit + shell, same UVs, same inflation -- baked against a 128x128 sheet, drawn with the nanotech
 * composite so the sleeve forms / swaps in first person exactly like the body does.
 */
public final class MaxSteelFirstPersonArm {
	private static final ModelLayerLocation LAYER =
			new ModelLayerLocation(ProjectHeroMod.id("max_steel_first_person_arm"), "main");

	private static ModelPart rightArm;
	private static ModelPart leftArm;

	private MaxSteelFirstPersonArm() {
	}

	public static void initialize() {
		EntityModelLayerRegistry.registerModelLayer(LAYER, MaxSteelFirstPersonArm::createLayer);
	}

	private static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("right_arm", CubeListBuilder.create()
				.texOffs(40, 16).addBox(-3.0f, -2.0f, -2.0f, 4.0f, 12.0f, 4.0f, new CubeDeformation(0.35f))
				.texOffs(40, 32).addBox(-3.0f, -2.0f, -2.0f, 4.0f, 12.0f, 4.0f, new CubeDeformation(0.51f)),
				PartPose.offset(-5.0f, 2.0f, 0.0f));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create()
				.texOffs(32, 48).addBox(-1.0f, -2.0f, -2.0f, 4.0f, 12.0f, 4.0f, new CubeDeformation(0.35f))
				.texOffs(48, 48).addBox(-1.0f, -2.0f, -2.0f, 4.0f, 12.0f, 4.0f, new CubeDeformation(0.51f)),
				PartPose.offset(5.0f, 2.0f, 0.0f));
		return LayerDefinition.create(mesh, 128, 128);
	}

	private static boolean ensureBaked() {
		if (rightArm != null) {
			return true;
		}
		try {
			ModelPart root = Minecraft.getInstance().getEntityModels().bakeLayer(LAYER);
			rightArm = root.getChild("right_arm");
			leftArm = root.getChild("left_arm");
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	public static void render(PoseStack pose, MultiBufferSource buffers, int light, ModelPart vanillaArm,
			boolean rightSide, String armorSetId, Player player) {
		if (!ensureBaked()) {
			return;
		}
		ModelPart arm = rightSide ? rightArm : leftArm;
		arm.copyFrom(vanillaArm);
		arm.visible = true;
		float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		ResourceLocation texture = com.projecthero.mod.client.maxsteel.MaxSteelNano.texture(player,
				SuperheroArmorVisuals.get(armorSetId).texture(), pt);
		arm.render(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY);
	}
}
