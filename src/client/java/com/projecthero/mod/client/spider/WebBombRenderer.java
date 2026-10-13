package com.projecthero.mod.client.spider;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.spider.entity.SpiderWebBombEntity;

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

/**
 * v0.16.1: the Web Bomb glob -- bigger and denser than the Web Shot: a fat ball of webbing wrapped in three crossed
 * sheaths, with loose strands sticking out every way, tumbling and gently pulsing as it arcs. Shares the Web Shot's
 * web texture.
 */
public class WebBombRenderer extends EntityRenderer<SpiderWebBombEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(ProjectHeroMod.id("web_bomb"), "main");

	private final ModelPart root;

	public WebBombRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.root = context.bakeLayer(LAYER);
		this.shadowRadius = 0.25f;
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 0).addBox(-3f, -3f, -3f, 6f, 6f, 6f), PartPose.ZERO);
		root.addOrReplaceChild("sheath_a", CubeListBuilder.create().texOffs(0, 6).addBox(-4f, -1f, -3.5f, 8f, 2f, 7f),
				PartPose.rotation(0f, 0f, 0.785f));
		root.addOrReplaceChild("sheath_b", CubeListBuilder.create().texOffs(0, 6).addBox(-4f, -1f, -3.5f, 8f, 2f, 7f),
				PartPose.rotation(0f, 0f, -0.785f));
		root.addOrReplaceChild("sheath_c", CubeListBuilder.create().texOffs(0, 6).addBox(-3.5f, -1f, -4f, 7f, 2f, 8f),
				PartPose.rotation(0.785f, 0.785f, 0f));
		// loose strands poking out (each a thin rod from the surface outward along its own direction)
		float[][] dirs = {{0.3f, 0.2f}, {2.6f, -0.4f}, {-1.4f, 1.9f}, {1.1f, 3.6f}, {-2.3f, -1.2f}, {0.9f, -2.5f}};
		for (int i = 0; i < dirs.length; i++) {
			root.addOrReplaceChild("strand_" + i, CubeListBuilder.create().texOffs(18, 0).addBox(-0.5f, -0.5f, 2.5f, 1f, 1f, 4f + (i % 3)),
					PartPose.rotation(dirs[i][0], dirs[i][1], 0f));
		}
		return LayerDefinition.create(mesh, 64, 16);
	}

	@Override
	public void render(SpiderWebBombEntity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
		float age = entity.tickCount + partialTicks;
		float pulse = 1.0f + 0.07f * Mth.sin(age * 0.7f);
		pose.pushPose();
		pose.translate(0.0, 0.3, 0.0);
		pose.mulPose(Axis.YP.rotationDegrees(age * 23.0f));
		pose.mulPose(Axis.XP.rotationDegrees(age * 31.0f));
		float s = 1.0f * pulse;
		pose.scale(s, s, s);
		root.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(WebShotRenderer.TEXTURE)),
				Math.max(light, LightTexture.pack(10, Mth.clamp(LightTexture.sky(light), 0, 15))), OverlayTexture.NO_OVERLAY);
		pose.popPose();
		super.render(entity, yaw, partialTicks, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(SpiderWebBombEntity entity) {
		return WebShotRenderer.TEXTURE;
	}
}
