package com.projecthero.mod.client.raven;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.raven.entity.DarkBoltEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Raven, R: the Dark Bolt -- a black core in a flickering violet glow, with a tail of shrinking shadow orbs behind it along its
 * flight (the server moves it; this only draws it).
 */
public class DarkBoltRenderer extends EntityRenderer<DarkBoltEntity> {
	public DarkBoltRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Override
	public void render(DarkBoltEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float t = e.tickCount + partial;
		Vec3 c = new Vec3(0, 0.2, 0);
		Vec3 v = e.getDeltaMovement();
		Vec3 back = v.lengthSqr() < 1.0e-6 ? Vec3.ZERO : v.normalize().scale(-1);
		float flick = 1f + 0.12f * Mth.sin(t * 2.3f);
		PoseStack.Pose last = pose.last();
		RavenDraw.sphere(RavenDraw.glow(buffers), last, c, 0.42 * flick, 6, 10, 0x706A2BD0, true);
		RavenDraw.sphere(RavenDraw.shadow(buffers), last, c, 0.2, 6, 10, 0xF0050208, false);
		for (int i = 1; i <= 5; i++) {
			Vec3 p = c.add(back.scale(i * 0.32));
			double r = 0.17 * (1.0 - i / 6.5);
			RavenDraw.sphere(RavenDraw.shadow(buffers), last, p, r, 4, 8, RavenDraw.fade(0xC0080310, 1f - i / 6f), false);
		}
		super.render(e, yaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(DarkBoltEntity e) {
		return RavenDraw.WHITE;
	}
}
