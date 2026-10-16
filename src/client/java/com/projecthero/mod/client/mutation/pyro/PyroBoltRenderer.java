package com.projecthero.mod.client.mutation.pyro;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.hero.power.p08.PyroBoltEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Pyrokinesis: the Fireball and the Meteor -- a layered ball of fire ({@link PyroDraw#fireball}): a solid molten
 * core, two churning additive shells, a soft glow and flame tongues streaming back along the flight, plus a fading trail of
 * glow behind it. The Meteor is bigger, darker-cored and trails longer.
 */
public class PyroBoltRenderer extends EntityRenderer<PyroBoltEntity> {
	public PyroBoltRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public void render(PyroBoltEntity e, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float t = e.tickCount + partial;
		float r = e.size();
		Vec3 v = e.getDeltaMovement();
		pose.pushPose();
		pose.translate(0.0, e.getBbHeight() * 0.5, 0.0);
		// grows in over the first two ticks so it doesn't pop out of the hand
		float in = Math.min(1.0f, t / 2.0f);
		PyroDraw.fireball(pose, buffers, r * (0.5f + 0.5f * in), t, v, e.meteor());
		// a fading trail of glow behind it
		var glow = PyroDraw.glowBuffer(buffers);
		int n = e.meteor() ? 7 : 5;
		for (int i = 1; i <= n; i++) {
			Vec3 at = v.scale(-0.55 * i);
			float k = 1.0f - i / (float) (n + 1);
			PyroDraw.glow(pose, glow, at, r * (1.6f * k + 0.3f), PyroDraw.dim(e.meteor() ? 0xFFC04010 : 0xFFFF7020, k * 0.8f));
		}
		pose.popPose();
		super.render(e, entityYaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(PyroBoltEntity entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
