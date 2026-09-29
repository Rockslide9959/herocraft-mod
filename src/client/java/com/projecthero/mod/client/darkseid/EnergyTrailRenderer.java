package com.projecthero.mod.client.darkseid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.projecthero.mod.darkseid.entity.EnergyProjectile;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws an {@link EnergyProjectile} (an Omega Beam, a Parademon bolt) as the glowing ribbon of everywhere it has just
 * been -- its client-side {@link EnergyProjectile#trail} -- tapering and fading toward the tail, with a hot core.
 * That is what makes a curving Omega Beam look like a bent beam of light rather than a flying dot, at no network
 * cost at all.
 */
public class EnergyTrailRenderer<T extends EnergyProjectile> extends EntityRenderer<T> {
	public EnergyTrailRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(T entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
		Vec3 base = new Vec3(Mth.lerp(partialTick, entity.xo, entity.getX()), Mth.lerp(partialTick, entity.yo, entity.getY()),
				Mth.lerp(partialTick, entity.zo, entity.getZ()));
		Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(base);
		PoseStack.Pose pose = poseStack.last();
		float width = entity.trailWidth();
		int n = entity.trailCount;
		Vec3 d = entity.travelDirection().scale(width * 1.5);
		// two passes, never interleaved -- see BeamDraw#beam
		for (int pass = 0; pass < 2; pass++) {
			boolean glow = pass == 0;
			VertexConsumer vc = buffers.getBuffer(glow ? RenderType.debugQuads() : RenderType.lightning());
			Vec3 prev = Vec3.ZERO;
			for (int i = 1; i < n; i++) {
				Vec3 p = entity.trail[i];
				if (p == null) {
					break;
				}
				Vec3 rel = p.subtract(base);
				float fa = 1.0f - (i - 1) / (float) n;
				float fb = 1.0f - i / (float) n;
				if (glow) {
					BeamDraw.segment(vc, pose, prev, rel, cam, width * (0.4f + 0.6f * fa), width * (0.4f + 0.6f * fb), entity.trailColor(),
							0.75f * fa, 0.75f * fb);
				} else {
					BeamDraw.segment(vc, pose, prev, rel, cam, width * 0.35f * fa, width * 0.35f * fb, entity.coreColor(), fa, fb);
				}
				prev = rel;
			}
			// the glowing head
			if (glow) {
				BeamDraw.segment(vc, pose, d.scale(-1), d, cam, width * 1.4f, width * 1.4f, entity.trailColor(), 0.9f, 0.9f);
			} else {
				BeamDraw.segment(vc, pose, d.scale(-0.6), d.scale(0.6), cam, width * 0.8f, width * 0.8f, entity.coreColor(), 1.0f, 1.0f);
			}
		}
		super.render(entity, yaw, partialTick, poseStack, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(T entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
