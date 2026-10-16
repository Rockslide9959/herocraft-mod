package com.projecthero.mod.client.mutation.cryo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.hero.power.p09.IceSpikeEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * v0.16.5 Cryokinesis: an erupting ice spike -- one tall packed-ice crystal leaning away from the caster with two or
 * three smaller clear-ice crystals jutting from its foot and a cracked ice collar on the ground. It bursts up out of the
 * ground (overshooting a touch) when its delay runs out, stands, then sinks back down as it melts.
 */
public class IceSpikeRenderer extends EntityRenderer<IceSpikeEntity> {
	public IceSpikeRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public void render(IceSpikeEntity e, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float age = e.tickCount + partial;
		float rise = IceSpikeEntity.rise(age, e.delay(), e.life());
		if (rise <= 0.001f) {
			return;
		}
		float h = e.height();
		int lit = LightTexture.pack(Math.max(LightTexture.block(light), 8), LightTexture.sky(light));
		RandomSource rnd = RandomSource.create(e.getId() * 0x9E3779B97F4A7C15L);
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		// the collar of cracked ice round its foot
		float cr = 0.45f + h * 0.08f;
		IceDraw.box(pose, buffers.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), -cr, -0.04f, -cr, cr, 0.08f * Math.min(1.0f, rise * 2), cr, 0.5f, 0xB0EAF8FF, lit);
		// the main spike, sunk by (1 - rise) of its height and leaning away
		pose.pushPose();
		pose.translate(0, -(1.0f - rise) * h, 0);
		pose.mulPose(Axis.XP.rotation(e.tilt()));
		pose.mulPose(Axis.YP.rotation(e.spin()));
		IceDraw.crystal(pose, buffers.getBuffer(RenderType.entityTranslucent(IceDraw.PACKED)), 0.42f + h * 0.08f, h, h * 0.25f, 0xF2FFFFFF, lit);
		IceDraw.crystal(pose, buffers.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 0.2f, h * 0.92f, h * 0.3f, 0x70FFFFFF, LightTexture.FULL_BRIGHT);
		pose.popPose();
		// the small crystals at its foot
		int n = 2 + rnd.nextInt(2);
		for (int i = 0; i < n; i++) {
			float a = rnd.nextFloat() * Mth.TWO_PI;
			float sh = h * (0.3f + rnd.nextFloat() * 0.25f);
			pose.pushPose();
			pose.translate(Mth.cos(a) * 0.28f, -(1.0f - Math.min(1.0f, rise * 1.2f)) * sh, Mth.sin(a) * 0.28f);
			pose.mulPose(Axis.YP.rotation(-a));
			pose.mulPose(Axis.ZP.rotation(0.5f + rnd.nextFloat() * 0.3f));
			IceDraw.crystal(pose, buffers.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 0.18f, sh, sh * 0.2f, 0xE0F0FBFF, lit);
			pose.popPose();
		}
		pose.popPose();
		super.render(e, entityYaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(IceSpikeEntity entity) {
		return IceDraw.PACKED;
	}
}
