package com.projecthero.mod.client.quirk.erasure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.quirk.erasure.ErasureSleepingBag;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.1 Erasure revamp: Aizawa's Sleeping Bag round anyone asleep in one ({@link ErasureSleepingBag#inBag}) -- the puffy
 * yellow cocoon from the neck down, its hood rolled behind the head, only the face showing. Drawn in the body's own space, so
 * it lies with the sleeper. Every viewer sees it (the sleeping pose and the bag in hand are synced).
 */
public class SleepingBagLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/quirk/sleeping_bag.png");
	private static ModelPart bag;

	public SleepingBagLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	private static void build() {
		if (bag != null) {
			return;
		}
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition b = root.addOrReplaceChild("bag", CubeListBuilder.create()
				// the body of the bag: shoulders to past the feet, puffy
				.texOffs(0, 0).addBox(-5.5f, -0.5f, -3.5f, 11, 26, 7, new CubeDeformation(0.4f))
				// the foot end, rounded off
				.texOffs(0, 34).addBox(-4.5f, 25.5f, -3.0f, 9, 2, 6, new CubeDeformation(0.2f)), PartPose.ZERO);
		// the hood: a padded roll round the back and sides of the head
		b.addOrReplaceChild("hood", CubeListBuilder.create()
				.texOffs(0, 44).addBox(-5.0f, -8.5f, 2.6f, 10, 9, 2, new CubeDeformation(0.3f))
				.texOffs(24, 44).addBox(-5.6f, -8.0f, -3.0f, 1, 8, 6, new CubeDeformation(0.25f))
				.texOffs(24, 44).addBox(4.6f, -8.0f, -3.0f, 1, 8, 6, new CubeDeformation(0.25f)), PartPose.ZERO);
		bag = LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("bag");
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !ErasureSleepingBag.inBag(player)) {
			return;
		}
		build();
		int overlay = LivingEntityRenderer.getOverlayCoords(player, 0f);
		pose.pushPose();
		getParentModel().body.translateAndRotate(pose);
		bag.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, overlay);
		pose.popPose();
	}
}
