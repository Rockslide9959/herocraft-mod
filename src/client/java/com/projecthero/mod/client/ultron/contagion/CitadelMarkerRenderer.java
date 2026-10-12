package com.projecthero.mod.client.ultron.contagion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.ultron.contagion.entity.CitadelMarkerEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the Sokovia Protocol's marker -- a red beam from the ground up past the clouds (seen from far away), and a
 * pulsing cyan base: the Stark Lift (right-click it).
 */
public class CitadelMarkerRenderer extends EntityRenderer<CitadelMarkerEntity> {
	private static final ResourceLocation NONE = ProjectHeroMod.id("textures/entity/mutation/white.png");

	public CitadelMarkerRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public boolean shouldRender(CitadelMarkerEntity entity, Frustum frustum, double x, double y, double z) {
		return true;
	}

	@Override
	public void render(CitadelMarkerEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		float time = e.tickCount + partialTick;
		Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(e.getPosition(partialTick));
		PoseStack.Pose p = pose.last();
		float flick = 0.8f + 0.2f * Mth.sin(time * 0.7f);
		Vec3 a = new Vec3(0, 0, 0), b = new Vec3(0, 400, 0);
		BeamDraw.beam(buffers.getBuffer(RenderType.debugQuads()), true, p, a, b, cam, 0.45f, 0xFF1A10, flick * 0.8f);
		BeamDraw.beam(buffers.getBuffer(RenderType.lightning()), false, p, a, b, cam, 0.45f, 0xFFA090, flick);
		pose.pushPose();
		pose.translate(0, 0.6, 0);
		TurboDraw.sphere(TurboDraw.buffer(buffers), pose, 0.7f + 0.1f * Mth.sin(time * 0.2f), 0x40B8FF, 0.35f);
		pose.popPose();
		super.render(e, yaw, partialTick, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(CitadelMarkerEntity entity) {
		return NONE;
	}
}
