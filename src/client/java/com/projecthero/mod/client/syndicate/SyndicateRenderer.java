package com.projecthero.mod.client.syndicate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.syndicate.SyndicateEntityTypes;
import com.projecthero.mod.syndicate.entity.KingpinEntity;
import com.projecthero.mod.syndicate.entity.SyndicateCriminal;
import com.projecthero.mod.syndicate.entity.SyndicateGunman;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.14.25: draws every Syndicate member with the player model in his Skindex skin ({@code SyndicateSkin}), swapping to
 * the slim-armed model for slim skins, with held items (the guns, the clubs, the Kingpin's cane) and armour. The Kingpin
 * is drawn a little broader than he is tall on top of his SCALE.
 */
public class SyndicateRenderer extends HumanoidMobRenderer<SyndicateCriminal, SyndicateModel> {
	private final SyndicateModel wide;
	private final SyndicateModel slim;

	public SyndicateRenderer(EntityRendererProvider.Context ctx) {
		this(ctx, new SyndicateModel(ctx.bakeLayer(ModelLayers.PLAYER), false), new SyndicateModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true));
	}

	private SyndicateRenderer(EntityRendererProvider.Context ctx, SyndicateModel wide, SyndicateModel slim) {
		super(ctx, wide, 0.5f);
		this.wide = wide;
		this.slim = slim;
		addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
				new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
	}

	public static void initialize() {
		EntityRendererRegistry.register(SyndicateEntityTypes.THUG, SyndicateRenderer::new);
		EntityRendererRegistry.register(SyndicateEntityTypes.GUNMAN, SyndicateRenderer::new);
		EntityRendererRegistry.register(SyndicateEntityTypes.ENFORCER, SyndicateRenderer::new);
		EntityRendererRegistry.register(SyndicateEntityTypes.KINGPIN, SyndicateRenderer::new);
		EntityRendererRegistry.register(SyndicateEntityTypes.BULLET, SyndicateBulletRenderer::new); // v0.16.2
	}

	@Override
	public void render(SyndicateCriminal entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		this.model = entity.skin().slim() ? slim : wide;
		super.render(entity, yaw, partialTick, pose, buffers, light);
		if (entity instanceof SyndicateGunman g) {
			Vec3 aim = g.laserAim();
			if (aim != null) {
				renderLaser(g, aim, partialTick, pose, buffers);
			}
		}
	}

	@Override
	public boolean shouldRender(SyndicateCriminal entity, Frustum frustum, double camX, double camY, double camZ) {
		// a sniper's laser can cross the screen while he himself is off it
		if (entity instanceof SyndicateGunman g && g.laserAim() != null && entity.distanceToSqr(camX, camY, camZ) < 64 * 64) {
			return true;
		}
		return super.shouldRender(entity, frustum, camX, camY, camZ);
	}

	/**
	 * v0.16.7 (perf): the sniper's laser sight, a thin red beam from his muzzle ({@code SyndicateGunfire.muzzle}, from
	 * half a block out) to the synced aim point -- it used to be a dotted line of up to 80 dust particles a tick, each its
	 * own packet. Soft red glow (alpha-blended, so it stays red against the sky) round a bright additive core.
	 */
	private void renderLaser(SyndicateGunman g, Vec3 aim, float partialTick, PoseStack pose, MultiBufferSource buffers) {
		Vec3 at = new Vec3(Mth.lerp(partialTick, g.xo, g.getX()), Mth.lerp(partialTick, g.yo, g.getY()), Mth.lerp(partialTick, g.zo, g.getZ()));
		float s = g.getScale();
		Vec3 look = g.getViewVector(partialTick);
		Vec3 right = new Vec3(-look.z, 0, look.x);
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 muzzle = new Vec3(0, g.getEyeHeight(), 0).add(look.scale(0.6 * s)).add(right.scale(0.3 * s)).add(0, -0.25 * s, 0);
		Vec3 end = aim.subtract(at);
		Vec3 dir = end.subtract(muzzle);
		double len = dir.length();
		if (len < 0.6) {
			return;
		}
		Vec3 start = muzzle.add(dir.scale(0.5 / len));
		Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(at);
		PoseStack.Pose last = pose.last();
		float flicker = 0.85f + 0.15f * Mth.sin((g.tickCount + partialTick) * 1.7f);
		// glow first, core second -- never interleave the two buffers (BeamDraw#beam)
		VertexConsumer glow = buffers.getBuffer(RenderType.debugQuads());
		BeamDraw.segment(glow, last, start, end, cam, 0.03f, 0.03f, 0xFF1010, 0.35f * flicker, 0.3f * flicker);
		VertexConsumer core = buffers.getBuffer(RenderType.lightning());
		BeamDraw.segment(core, last, start, end, cam, 0.011f, 0.011f, 0xFF2020, 0.95f * flicker, 0.85f * flicker);
	}

	@Override
	protected void scale(SyndicateCriminal entity, PoseStack pose, float partialTick) {
		super.scale(entity, pose, partialTick);
		// the player model is 15/16 of a block wide at the shoulders like vanilla's player renderer draws it
		pose.scale(0.9375f, 0.9375f, 0.9375f);
		if (entity instanceof KingpinEntity) {
			pose.scale(1.15f, 1.0f, 1.12f);
		}
	}

	@Override
	public ResourceLocation getTextureLocation(SyndicateCriminal entity) {
		return entity.skin().texture();
	}
}
