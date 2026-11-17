package com.projecthero.mod.client.symbiote;

import com.mojang.blaze3d.vertex.PoseStack;

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
 * v0.17.5: the first-person sleeve of the new Symbiote suit -- the same two boxes as the suit's arm bone (base inflated 0.3,
 * raised shell 0.55, player-skin UVs, so the 128 px texture shows at full detail), in the wearer's colours
 * ({@link SymbioteSuitTint}), spreading on / off with the suit ({@link SymbioteSpread}), with the sheen and vein passes on top
 * ({@link SymbioteSuitFx#renderSleeve}). Baked once from a code-built mesh; no model layer to register.
 */
public final class SymbioteSuitSleeve {
	private static ModelPart right;
	private static ModelPart left;

	private SymbioteSuitSleeve() {
	}

	private static void ensure() {
		if (right != null) {
			return;
		}
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("right", CubeListBuilder.create()
				.texOffs(40, 16).addBox(-3f, -2f, -2f, 4f, 12f, 4f, new CubeDeformation(0.3f))
				.texOffs(40, 32).addBox(-3f, -2f, -2f, 4f, 12f, 4f, new CubeDeformation(0.55f)),
				PartPose.offset(-5f, 2f, 0f));
		root.addOrReplaceChild("left", CubeListBuilder.create()
				.texOffs(32, 48).addBox(-1f, -2f, -2f, 4f, 12f, 4f, new CubeDeformation(0.3f))
				.texOffs(48, 48).addBox(-1f, -2f, -2f, 4f, 12f, 4f, new CubeDeformation(0.55f)),
				PartPose.offset(5f, 2f, 0f));
		ModelPart baked = LayerDefinition.create(mesh, 64, 64).bakeRoot();
		right = baked.getChild("right");
		left = baked.getChild("left");
	}

	public static void render(PoseStack pose, MultiBufferSource buffers, int light, ModelPart vanillaArm, boolean rightSide, Player player) {
		ensure();
		ModelPart arm = rightSide ? right : left;
		arm.copyFrom(vanillaArm);
		arm.visible = true;
		ResourceLocation texture = SymbioteSuitTint.suit(player);
		texture = SymbioteSpread.firstPerson("symbiote_host", texture);
		arm.render(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY);
		SymbioteSuitFx.renderSleeve(pose, buffers, light, arm, player);
	}
}
