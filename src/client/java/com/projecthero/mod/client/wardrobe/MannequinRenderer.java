package com.projecthero.mod.client.wardrobe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.wardrobe.MannequinEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * v0.16.5: draws a {@link MannequinEntity} -- a blank wooden player-shaped figure ({@code textures/entity/mannequin.png},
 * made by {@code scratchpad/v0165_suits/gen_wardrobe.js}) on a vanilla armour-stand base plate, wearing its armour
 * (vanilla and GeckoLib), the skin-tight Spider-Man suits, held items, an elytra and a head item. It wobbles when hit and
 * shows its name only when told to, like an armour stand.
 */
public class MannequinRenderer extends LivingEntityRenderer<MannequinEntity, MannequinModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ProjectHeroMod.id("mannequin"), "main");
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/mannequin.png");
	/** The figure stands on the base plate, so it is lifted by the plate's one pixel. */
	private static final float PLATE = 1.0f / 16.0f;

	public MannequinRenderer(EntityRendererProvider.Context context) {
		super(context, new MannequinModel(context.bakeLayer(LAYER)), 0.0f);
		addLayer(new BasePlateLayer(this));
		addLayer(new HumanoidArmorLayer<>(this, new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
				new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()));
		addLayer(new StandSpiderSuitLayer<>(this));
		addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
		addLayer(new ElytraLayer<>(this, context.getModelSet()));
		addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
	}

	public static LayerDefinition createLayer() {
		return LayerDefinition.create(net.minecraft.client.model.PlayerModel.createMesh(
				net.minecraft.client.model.geom.builders.CubeDeformation.NONE, false), 64, 64);
	}

	@Override
	public void render(MannequinEntity mannequin, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		pose.pushPose();
		if (!mannequin.isNoBasePlate()) {
			pose.translate(0.0f, PLATE, 0.0f);
		}
		super.render(mannequin, yaw, partialTick, pose, buffers, light);
		pose.popPose();
	}

	@Override
	public ResourceLocation getTextureLocation(MannequinEntity mannequin) {
		return TEXTURE;
	}

	@Override
	protected void setupRotations(MannequinEntity mannequin, PoseStack pose, float bob, float bodyYaw, float partialTick, float scale) {
		pose.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
		float sinceHit = (float) (mannequin.level().getGameTime() - mannequin.lastHit) + partialTick;
		if (sinceHit < 5.0f) {
			pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(sinceHit / 1.5f * (float) Math.PI) * 3.0f));
		}
	}

	@Override
	protected boolean shouldShowName(MannequinEntity mannequin) {
		Entity camera = Minecraft.getInstance().crosshairPickEntity;
		return mannequin.isCustomNameVisible() || (mannequin.hasCustomName() && camera == mannequin
				&& super.shouldShowName(mannequin));
	}

	/** The vanilla armour stand's 12x1x12 wooden base plate, under the mannequin's feet. */
	private static final class BasePlateLayer extends RenderLayer<MannequinEntity, MannequinModel> {
		private static final ResourceLocation WOOD = ResourceLocation.withDefaultNamespace("textures/entity/armorstand/wood.png");
		private static final ModelPart PLATE_PART;

		static {
			MeshDefinition mesh = new MeshDefinition();
			mesh.getRoot().addOrReplaceChild("base_plate", CubeListBuilder.create().texOffs(0, 32).addBox(-6.0f, 0.0f, -6.0f, 12.0f, 1.0f, 12.0f),
					PartPose.offset(0.0f, 24.0f, 0.0f));
			PLATE_PART = LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("base_plate");
		}

		BasePlateLayer(RenderLayerParent<MannequinEntity, MannequinModel> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffers, int light, MannequinEntity mannequin, float limbSwing,
				float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
			if (mannequin.isNoBasePlate() || mannequin.isInvisible()) {
				return;
			}
			PLATE_PART.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(WOOD)), light, OverlayTexture.NO_OVERLAY);
		}
	}
}
