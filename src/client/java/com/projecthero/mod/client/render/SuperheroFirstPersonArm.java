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

/**
 * The first-person forearm for every {@link com.projecthero.mod.armor.SuperheroArmorItem} set. It draws
 * <em>over</em> the bare player hand (the second/sleeve layer is hidden by
 * {@link com.projecthero.mod.client.mixin.PlayerModelMixin}) whenever the local player wears a superhero
 * chestplate -- see {@link com.projecthero.mod.client.mixin.PlayerRendererHandMixin}.
 *
 * <p>The mesh is a direct rebuild of the {@code armorRightArm} / {@code armorLeftArm} bone chain from
 * {@code crimson_vanguard.geo.json} (shoulder pad + upper arm + forearm + gauntlet), with the box
 * UVs transcribed to {@code texOffs} values, so it samples the same per-set texture
 * ({@code textures/armor/<set>.png}) as the in-world GeckoLib model and matches it panel-for-panel.
 * It occupies the same volume as the vanilla arm, so copying the vanilla arm's animated pose onto it
 * ({@link ModelPart#copyFrom}) makes it track every swing / bob / place / eat animation for free --
 * the same proven approach the old Iron-Man-only gauntlet overlay used, just with the new geometry
 * and texture layout.
 */
public final class SuperheroFirstPersonArm {
	private static final ModelLayerLocation LAYER =
			new ModelLayerLocation(ProjectHeroMod.id("superhero_first_person_arm"), "main");

	private static ModelPart rightArm;
	private static ModelPart leftArm;

	private SuperheroFirstPersonArm() {
	}

	public static void initialize() {
		EntityModelLayerRegistry.registerModelLayer(LAYER, SuperheroFirstPersonArm::createLayer);
	}

	private static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		// Right arm -- armorRightArm bone chain, pivot at geo (-5, 22, 0) => vanilla arm PartPose.
		root.addOrReplaceChild("right_arm", CubeListBuilder.create()
				.texOffs(40, 16).addBox(-3.0f, -2.0f, -2.0f, 4.0f, 6.0f, 4.0f)          // right_upper_arm
				.texOffs(40, 22).addBox(-3.0f, 4.0f, -2.0f, 4.0f, 4.0f, 4.0f)           // right_forearm
				.texOffs(40, 26).addBox(-3.0f, 8.0f, -2.0f, 4.0f, 2.0f, 4.0f)           // right_gauntlet
				.texOffs(40, 32).addBox(-3.0f, -2.0f, -2.0f, 4.0f, 4.0f, 4.0f, new CubeDeformation(0.3f)), // right_shoulder pad
				PartPose.offset(-5.0f, 2.0f, 0.0f));

		// Left arm -- armorLeftArm bone chain, pivot at geo (5, 22, 0).
		root.addOrReplaceChild("left_arm", CubeListBuilder.create()
				.texOffs(32, 48).addBox(-1.0f, -2.0f, -2.0f, 4.0f, 6.0f, 4.0f)          // left_upper_arm
				.texOffs(32, 54).addBox(-1.0f, 4.0f, -2.0f, 4.0f, 4.0f, 4.0f)           // left_forearm
				.texOffs(32, 58).addBox(-1.0f, 8.0f, -2.0f, 4.0f, 2.0f, 4.0f)           // left_gauntlet
				.texOffs(48, 48).addBox(-1.0f, -2.0f, -2.0f, 4.0f, 4.0f, 4.0f, new CubeDeformation(0.3f)), // left_shoulder pad
				PartPose.offset(5.0f, 2.0f, 0.0f));

		return LayerDefinition.create(mesh, 64, 64);
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
			return false; // models not ready this frame -- let the bare vanilla hand show
		}
	}

	/**
	 * Draw the worn suit's forearm in place of the first-person hand. {@code vanillaArm} is the
	 * fully-animated arm {@link ModelPart} the caller was about to render; its pose is copied straight
	 * across so swing / bob / place animations carry over exactly.
	 *
	 * @param armorSetId {@link com.projecthero.mod.armor.SuperheroArmorItem#armorSetId()} of the worn chestplate
	 */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, ModelPart vanillaArm,
			boolean rightSide, String armorSetId) {
		if (!ensureBaked()) {
			return;
		}
		ModelPart arm = rightSide ? rightArm : leftArm;
		arm.copyFrom(vanillaArm);
		arm.visible = true;
		ResourceLocation texture = SuperheroArmorVisuals.get(armorSetId).texture();
		arm.render(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY);
	}
}
