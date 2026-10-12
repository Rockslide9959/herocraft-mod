package com.projecthero.mod.client.ultron.contagion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.ultron.contagion.entity.ContagionStructureEntity;
import com.projecthero.mod.ultron.contagion.entity.LiftEngineEntity;
import com.projecthero.mod.ultron.contagion.entity.UltronNexusEntity;
import com.projecthero.mod.ultron.contagion.entity.UplinkAntennaEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * v0.16.1: the Contagion's standing machines through GeckoLib ({@code geo/ultron/<name>.geo.json}, texture + glowmask):
 * <ul>
 *   <li><b>Ultron Nexus</b>: rises out of the ground while it grows, with a red beam up to the clouds; its shield is a red
 *       shell (a capture turns it cyan-flickering, an Override blue).</li>
 *   <li><b>Lift Engine</b>: a red shell while its guard lives.</li>
 *   <li><b>Uplink Antenna</b>: rises out of the floor.</li>
 * </ul>
 */
public class ContagionGeoRenderer<T extends ContagionStructureEntity> extends GeoEntityRenderer<T> {
	public ContagionGeoRenderer(EntityRendererProvider.Context context, String name, float shadow) {
		super(context, new Model<>(name));
		this.shadowRadius = shadow;
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
	}

	static final class Model<E extends ContagionStructureEntity> extends GeoModel<E> {
		private final ResourceLocation model;
		private final ResourceLocation texture;
		private final ResourceLocation animation;

		Model(String name) {
			this.model = ProjectHeroMod.id("geo/ultron/" + name + ".geo.json");
			this.texture = ProjectHeroMod.id("textures/entity/ultron/" + name + ".png");
			this.animation = ProjectHeroMod.id("animations/ultron/" + name + ".animation.json");
		}

		@Override
		public ResourceLocation getModelResource(E animatable) {
			return model;
		}

		@Override
		public ResourceLocation getTextureResource(E animatable) {
			return texture;
		}

		@Override
		public ResourceLocation getAnimationResource(E animatable) {
			return animation;
		}
	}

	@Override
	public void preRender(PoseStack poseStack, T animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
			boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
		if (isReRender) {
			return;
		}
		float rise = 1f;
		if (animatable instanceof UltronNexusEntity n) {
			rise = n.growth(partialTick);
		} else if (animatable instanceof UplinkAntennaEntity a) {
			rise = a.rise(partialTick);
		}
		if (rise < 1f) {
			poseStack.translate(0, -(1f - rise) * animatable.getBbHeight(), 0);
		}
	}

	@Override
	public void render(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		super.render(entity, yaw, partialTick, pose, buffers, light);
		float time = entity.tickCount + partialTick;
		if (entity instanceof UltronNexusEntity n) {
			float g = n.growth(partialTick);
			Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(entity.getPosition(partialTick));
			if (g < 1f) {
				// the growing beam, up to the clouds
				PoseStack.Pose p = pose.last();
				float flick = 0.8f + 0.2f * Mth.sin(time * 0.9f);
				Vec3 a = new Vec3(0, 0.5, 0), b = new Vec3(0, 260 - entity.getY(), 0);
				BeamDraw.beam(buffers.getBuffer(RenderType.debugQuads()), true, p, a, b, cam, 0.5f, 0xFF1A10, flick * 0.8f);
				BeamDraw.beam(buffers.getBuffer(RenderType.lightning()), false, p, a, b, cam, 0.5f, 0xFFA090, flick);
			}
			if (g >= 1f) {
				int colour = n.overridden() ? 0x40A8FF : n.capturing() && ((int) time / 4) % 2 == 0 ? 0x60E0FF : 0xFF2010;
				float alpha = n.overridden() ? 0.10f : 0.14f + 0.04f * Mth.sin(time * 0.2f);
				pose.pushPose();
				pose.translate(0, 4.5, 0);
				pose.scale(1f, 1.9f, 1f);
				TurboDraw.sphere(TurboDraw.buffer(buffers), pose, 2.6f, colour, alpha);
				pose.popPose();
			}
		} else if (entity instanceof LiftEngineEntity e && e.guarded()) {
			pose.pushPose();
			pose.translate(0, 3.0, 0);
			pose.scale(1f, 1.5f, 1f);
			TurboDraw.sphere(TurboDraw.buffer(buffers), pose, 2.4f, 0xFF2010, 0.12f + 0.04f * Mth.sin(time * 0.25f));
			pose.popPose();
		}
	}

	@Override
	public boolean shouldShowName(T entity) {
		return false;
	}
}
