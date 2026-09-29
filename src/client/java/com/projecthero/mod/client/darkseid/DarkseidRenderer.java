package com.projecthero.mod.client.darkseid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.darkseid.DarkseidConfig;
import com.projecthero.mod.darkseid.entity.DarkseidCombat;
import com.projecthero.mod.darkseid.entity.DarkseidEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

/**
 * Renders Darkseid through GeckoLib. The geo is authored at {@link DarkseidEntity#MODEL_HEIGHT} and stretched to the
 * real hit-box here (only on the main pass -- the Oathbreaker's lesson: layers re-render inside the already-scaled
 * stack). The glowmask ({@code darkseid_glowmask.png}, picked up by {@link AutoGlowingGeoLayer}) keeps his eyes and
 * the Omega sigil burning red in the dark. On top of the model:
 * <ul>
 *   <li>while charging Omega Annihilation he pulses red;</li>
 *   <li>the <b>Omega Beam Sweep</b> is drawn here, from the synced sweep yaw: a beam from his eyes down to the
 *       ground just in front of him, then a long knee-high blade of red light out to the first block it hits;</li>
 *   <li>during the death sequence he fades out into the Boom Tube behind him.</li>
 * </ul>
 */
public class DarkseidRenderer extends GeoEntityRenderer<DarkseidEntity> {
	public DarkseidRenderer(EntityRendererProvider.Context context) {
		super(context, new Model());
		this.shadowRadius = 1.4f;
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
	}

	/** Model / texture / animation files: {@code geo/darkseid.geo.json} etc. */
	static final class Model extends GeoModel<DarkseidEntity> {
		private static final ResourceLocation MODEL = ProjectHeroMod.id("geo/darkseid.geo.json");
		private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/darkseid.png");
		private static final ResourceLocation ANIMATION = ProjectHeroMod.id("animations/darkseid.animation.json");

		@Override
		public ResourceLocation getModelResource(DarkseidEntity animatable) {
			return MODEL;
		}

		@Override
		public ResourceLocation getTextureResource(DarkseidEntity animatable) {
			return TEXTURE;
		}

		@Override
		public ResourceLocation getAnimationResource(DarkseidEntity animatable) {
			return ANIMATION;
		}
	}

	@Override
	public void render(DarkseidEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
		super.render(entity, yaw, partialTick, poseStack, buffers, light);
		float sweep = entity.sweepYaw();
		if (!Float.isNaN(sweep) && entity.deathTime <= 0) {
			renderSweep(entity, sweep, partialTick, poseStack, buffers);
		}
	}

	private void renderSweep(DarkseidEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers) {
		Vec3 base = new Vec3(Mth.lerp(partialTick, entity.xo, entity.getX()), Mth.lerp(partialTick, entity.yo, entity.getY()),
				Mth.lerp(partialTick, entity.zo, entity.getZ()));
		Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(base);
		PoseStack.Pose pose = poseStack.last();
		// the synced yaw steps 20 times a second; carry it on through the partial tick so the sweep turns smoothly
		float smooth = yaw + (float) DarkseidConfig.abilities().omegaSweepRotationSpeed * partialTick;
		double length = DarkseidConfig.abilities().omegaSweepLength;
		float flicker = 0.85f + 0.15f * Mth.sin((entity.tickCount + partialTick) * 1.7f);
		Vec3 eyes = new Vec3(0, entity.getBbHeight() * 0.9, 0);
		java.util.List<Vec3[]> segments = new java.util.ArrayList<>();
		for (int b = 0; b < entity.sweepBeams(); b++) {
			Vec3 dir = DarkseidCombat.yawDir(smooth + b * 180.0f);
			Vec3 start = new Vec3(0, 0.6, 0).add(dir.scale(entity.getBbWidth() * 0.6));
			Vec3 worldStart = base.add(start);
			Vec3 worldTip = worldStart.add(dir.scale(length));
			var hit = entity.level().clip(new ClipContext(worldStart, worldTip, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
			Vec3 end = (hit.getType() == HitResult.Type.MISS ? worldTip : hit.getLocation()).subtract(base);
			Vec3 eye = eyes.add(dir.scale(entity.getBbWidth() * 0.3));
			segments.add(new Vec3[] { eye, start, new Vec3(0.18, 0, 0) });
			segments.add(new Vec3[] { start, end, new Vec3(0.45, 0, 0) });
		}
		VertexConsumer glow = buffers.getBuffer(RenderType.debugQuads());
		for (Vec3[] s : segments) {
			BeamDraw.beam(glow, true, pose, s[0], s[1], cam, (float) s[2].x, 0xD00800, flicker);
		}
		VertexConsumer hot = buffers.getBuffer(RenderType.lightning());
		for (Vec3[] s : segments) {
			BeamDraw.beam(hot, false, pose, s[0], s[1], cam, (float) s[2].x, 0xFF4020, flicker);
		}
	}

	@Override
	public void preRender(PoseStack poseStack, DarkseidEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
			VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
		if (isReRender) {
			return;
		}
		float scale = animatable.getBbHeight() / DarkseidEntity.MODEL_HEIGHT;
		if (Math.abs(scale - 1.0f) > 0.01f) {
			poseStack.scale(scale, scale, scale);
		}
	}

	@Override
	protected float getDeathMaxRotation(DarkseidEntity animatable) {
		return 0.0f; // the death clip kneels him instead of vanilla's tip-over
	}

	@Override
	public Color getRenderColor(DarkseidEntity animatable, float partialTick, int packedLight) {
		float alpha = animatable.deathAlpha(partialTick);
		if (animatable.omegaState() == 2) {
			// Omega Annihilation charging: a red pulse through the whole body
			float p = 0.5f + 0.5f * Mth.sin((animatable.tickCount + partialTick) * 0.6f);
			return Color.ofRGBA(1.0f, 1.0f - 0.45f * p, 1.0f - 0.5f * p, alpha);
		}
		return alpha >= 1.0f ? super.getRenderColor(animatable, partialTick, packedLight) : Color.ofRGBA(1.0f, 1.0f, 1.0f, alpha);
	}

	@Override
	public RenderType getRenderType(DarkseidEntity animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
		return animatable.deathAlpha(partialTick) < 1.0f ? RenderType.entityTranslucent(texture)
				: super.getRenderType(animatable, texture, bufferSource, partialTick);
	}

}
