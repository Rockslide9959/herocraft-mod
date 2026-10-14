package com.projecthero.mod.client.syndicate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.syndicate.entity.SyndicateBulletEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2: a Syndicate bullet in flight, drawn exactly like the head of a Punisher tracer ({@code client.firearm.GunTracers}):
 * a camera-facing streak trailing back from the bullet along its velocity, a warm 0xFFC860 sheath round a white-hot
 * 0xFFF4D8 core, both fading toward the tail -- thinner for shotgun pellets, heavier for the sniper. Additive and
 * full-bright ({@link RenderType#lightning()}). The streak is about one tick of travel long (and never reaches back past
 * the muzzle), so you can see the shot coming and step out of its way.
 */
public class SyndicateBulletRenderer extends EntityRenderer<SyndicateBulletEntity> {
	public SyndicateBulletRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public void render(SyndicateBulletEntity e, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		Vec3 v = e.getDeltaMovement();
		double speed = v.length();
		if (speed > 1.0e-4) {
			int kind = e.gunKind();
			// the Punisher tracer widths per gun (GunTracers)
			float width = kind == 2 ? 0.018f : kind == 3 ? 0.045f : 0.028f;
			Vec3 dir = v.scale(1.0 / speed);
			double flown = (e.tickCount + partialTick) * speed;
			double streak = Math.min(Math.min(speed, kind == 3 ? 3.5 : 2.4), flown);
			double mid = e.getBbHeight() * 0.5;
			Vec3 head = new Vec3(0, mid, 0);
			Vec3 tail = head.subtract(dir.scale(streak));
			Vec3 at = new Vec3(Mth.lerp(partialTick, e.xo, e.getX()), Mth.lerp(partialTick, e.yo, e.getY()),
					Mth.lerp(partialTick, e.zo, e.getZ()));
			Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(at);
			VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
			PoseStack.Pose last = pose.last();
			BeamDraw.segment(vc, last, tail, head, cam, width * 0.4f, width, 0xFFC860, 0.0f, 0.9f);
			BeamDraw.segment(vc, last, tail, head, cam, width * 0.15f, width * 0.4f, 0xFFF4D8, 0.0f, 1.0f);
		}
		super.render(e, entityYaw, partialTick, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(SyndicateBulletEntity entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
