package com.projecthero.mod.client.spider;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.spider.entity.SpiderWebShotEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the Web Shot glob -- a small white knot of webbing (a core and a crossed sheath) with four thin strands
 * trailing behind it, pointed along its flight and spinning about it. Texture {@code textures/entity/web_shot.png}
 * ({@code scratchpad/gen_spiderman_web_textures.js}).
 */
public class WebShotRenderer extends EntityRenderer<SpiderWebShotEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ProjectHeroMod.id("web_shot"), "main");
	public static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/web_shot.png");

	private final ModelPart root;

	public WebShotRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.root = context.bakeLayer(LAYER);
		this.shadowRadius = 0.0f;
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f), PartPose.ZERO);
		root.addOrReplaceChild("sheath_a", CubeListBuilder.create().texOffs(0, 6).addBox(-2.5f, -0.5f, -2.0f, 5f, 1f, 4f),
				PartPose.rotation(0f, 0f, 0.785f));
		root.addOrReplaceChild("sheath_b", CubeListBuilder.create().texOffs(0, 6).addBox(-2.5f, -0.5f, -2.0f, 5f, 1f, 4f),
				PartPose.rotation(0f, 0f, -0.785f));
		// four strands trailing behind (+Z is backwards once the model faces its flight along -Z)
		float[][] tilt = {{0.18f, 0.18f}, {-0.18f, 0.18f}, {0.18f, -0.18f}, {-0.18f, -0.22f}};
		for (int i = 0; i < 4; i++) {
			root.addOrReplaceChild("strand_" + i, CubeListBuilder.create().texOffs(18, 0).addBox(-0.5f, -0.5f, 1.0f, 1f, 1f, 8f),
					PartPose.rotation(tilt[i][0], tilt[i][1], 0f));
		}
		return LayerDefinition.create(mesh, 64, 16);
	}

	@Override
	public void render(SpiderWebShotEntity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
		Vec3 v = entity.getDeltaMovement();
		if (v.lengthSqr() < 1.0e-6) {
			return;
		}
		float age = entity.tickCount + partialTicks;
		float fyaw = (float) Math.toDegrees(Math.atan2(v.x, v.z));
		float fpitch = (float) Math.toDegrees(Math.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)));
		pose.pushPose();
		pose.translate(0.0, 0.15, 0.0);
		pose.mulPose(Axis.YP.rotationDegrees(fyaw + 180.0f));
		pose.mulPose(Axis.XP.rotationDegrees(fpitch));
		pose.mulPose(Axis.ZP.rotationDegrees(age * 40.0f));
		float s = 0.9f;
		pose.scale(s, s, s);
		root.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
				Math.max(light, LightTexture.pack(10, Mth.clamp(LightTexture.sky(light), 0, 15))), OverlayTexture.NO_OVERLAY);
		pose.popPose();
		super.render(entity, yaw, partialTicks, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(SpiderWebShotEntity entity) {
		return TEXTURE;
	}
}
