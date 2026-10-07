package com.projecthero.mod.client.ultron;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.ultron.UltronFx;
import com.projecthero.mod.ultron.block.UltronBlocks;
import com.projecthero.mod.ultron.entity.UltronPylonEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.12: a relay pylon -- three stacked dark-steel segments with red light channels and a head block (the
 * {@code ultron_pylon_segment} / {@code ultron_pylon_head} block models), rising out of the ground when it is raised; a
 * pulsing red eye on top; and a thin red beam from the eye to the uplink. Hurt, it flashes red like any mob. Glow first,
 * core second (BeamDraw ordering).
 */
public class UltronPylonRenderer extends EntityRenderer<UltronPylonEntity> {
	public UltronPylonRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.shadowRadius = 0.8f;
	}

	@Override
	public boolean shouldRender(UltronPylonEntity entity, Frustum frustum, double camX, double camY, double camZ) {
		// the beam to the uplink must not be culled with the tower's box
		return entity.distanceToSqr(camX, camY, camZ) < 160 * 160;
	}

	@Override
	public void render(UltronPylonEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		float rise = entity.rise(partialTick);
		float t = entity.tickCount + partialTick;
		float sink = entity.deathTime > 0 ? (entity.deathTime + partialTick) * 0.12f : 0f;
		float drop = (1f - rise) * UltronPylonEntity.EYE_HEIGHT + sink;
		BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
		int overlay = OverlayTexture.pack(0, entity.hurtTime > 0 || entity.deathTime > 0);
		pose.pushPose();
		pose.translate(0, -drop, 0);
		pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-Mth.lerp(partialTick, entity.yRotO, entity.getYRot())));
		float w = 1.0f;
		for (int i = 0; i < 3; i++) {
			pose.pushPose();
			pose.translate(-w / 2, i * 1.05f, -w / 2);
			pose.scale(w, 1.05f, w);
			blocks.renderSingleBlock(UltronBlocks.PYLON_SEGMENT.defaultBlockState(), pose, buffers, light, overlay);
			pose.popPose();
		}
		pose.pushPose();
		pose.translate(-0.35, 3.15, -0.35);
		pose.scale(0.7f, 0.7f, 0.7f);
		blocks.renderSingleBlock(UltronBlocks.PYLON_HEAD.defaultBlockState(), pose, buffers, light, overlay);
		pose.popPose();
		pose.popPose();

		// the eye and the beam (entity-relative, not rotated)
		if (entity.deathTime > 0) {
			super.render(entity, yaw, partialTick, pose, buffers, light);
			return;
		}
		Vec3 base = new Vec3(Mth.lerp(partialTick, entity.xo, entity.getX()), Mth.lerp(partialTick, entity.yo, entity.getY()),
				Mth.lerp(partialTick, entity.zo, entity.getZ()));
		Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(base);
		Vec3 eye = new Vec3(0, UltronPylonEntity.EYE_HEIGHT - drop + 0.1, 0);
		BlockPos up = entity.uplink();
		Vec3 uplink = Vec3.atCenterOf(up).add(0, 0.3, 0).subtract(base);
		boolean beam = rise >= 1f && !up.equals(BlockPos.ZERO);
		float pulse = 0.65f + 0.35f * Mth.sin(t * 0.25f);
		float er = 0.22f + 0.05f * pulse;
		PoseStack.Pose last = pose.last();
		VertexConsumer glow = buffers.getBuffer(RenderType.debugQuads());
		BeamDraw.segment(glow, last, eye.add(0, -er, 0), eye.add(0, er, 0), cam, er * 1.3f, er * 1.3f, UltronBeamClient.GLOW, 0.75f * pulse,
				0.75f * pulse);
		if (beam) {
			UltronBeamClient.draw(glow, true, last, eye, uplink, cam, UltronFx.TETHER, 0.9f, t);
		}
		VertexConsumer core = buffers.getBuffer(RenderType.lightning());
		BeamDraw.segment(core, last, eye.add(0, -er * 0.45, 0), eye.add(0, er * 0.45, 0), cam, er * 0.5f, er * 0.5f, UltronBeamClient.CORE,
				pulse, pulse);
		if (beam) {
			UltronBeamClient.draw(core, false, last, eye, uplink, cam, UltronFx.TETHER, 0.9f, t);
		}
		super.render(entity, yaw, partialTick, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(UltronPylonEntity entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
