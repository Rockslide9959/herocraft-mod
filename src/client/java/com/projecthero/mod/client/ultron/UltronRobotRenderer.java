package com.projecthero.mod.client.ultron;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.ultron.UltronFx;
import com.projecthero.mod.ultron.entity.UltronDroneEntity;
import com.projecthero.mod.ultron.entity.UltronRobot;
import com.projecthero.mod.ultron.entity.UltronSentryEntity;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.12: every Ultron robot body -- the player model in its Skindex skin ({@code UltronSkin}), the slim-armed model for
 * slim skins, scaled by the SCALE attribute like any living entity. Its red eyes glow in the dark (an emissive layer from
 * the skin's {@code _eyes} texture). On top: a shield drone's red tether to the Sentry, and the Sentry's own shield bubble
 * and chest-cannon glow -- all drawn glow-first, core-second.
 */
public class UltronRobotRenderer extends HumanoidMobRenderer<UltronRobot, UltronModel> {
	private final UltronModel wide;
	private final UltronModel slim;

	public UltronRobotRenderer(EntityRendererProvider.Context ctx) {
		this(ctx, new UltronModel(ctx.bakeLayer(ModelLayers.PLAYER), false), new UltronModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true));
	}

	private UltronRobotRenderer(EntityRendererProvider.Context ctx, UltronModel wide, UltronModel slim) {
		super(ctx, wide, 0.5f);
		this.wide = wide;
		this.slim = slim;
		addLayer(new Eyes(this));
	}

	@Override
	public void render(UltronRobot entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		this.model = entity.skin().slim() ? slim : wide;
		this.shadowRadius = 0.5f * entity.getScale();
		super.render(entity, yaw, partialTick, pose, buffers, light);
		if (entity instanceof UltronDroneEntity d && d.tetherId() >= 0 && !d.isDeadOrDying()) {
			renderTether(d, partialTick, pose, buffers);
		}
		if (entity instanceof UltronSentryEntity s && !s.isDeadOrDying()) {
			renderSentryFx(s, partialTick, pose, buffers);
		}
	}

	@Override
	protected void scale(UltronRobot entity, PoseStack pose, float partialTick) {
		super.scale(entity, pose, partialTick);
		pose.scale(0.9375f, 0.9375f, 0.9375f);
	}

	@Override
	public ResourceLocation getTextureLocation(UltronRobot entity) {
		return entity.skin().texture();
	}

	@Override
	protected float getFlipDegrees(UltronRobot entity) {
		return entity instanceof UltronSentryEntity ? 90f : super.getFlipDegrees(entity);
	}

	@Override
	public boolean shouldRender(UltronRobot entity, Frustum frustum, double camX, double camY, double camZ) {
		if (super.shouldRender(entity, frustum, camX, camY, camZ)) {
			return true;
		}
		return entity instanceof UltronDroneEntity d && d.tetherId() >= 0;
	}

	private static Vec3 lerped(Entity e, float pt) {
		return new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
	}

	/** A shield carrier's red line into the Sentry's chest. */
	private void renderTether(UltronDroneEntity d, float pt, PoseStack pose, MultiBufferSource buffers) {
		Entity target = d.level().getEntity(d.tetherId());
		if (target == null) {
			return;
		}
		Vec3 base = lerped(d, pt);
		Vec3 a = new Vec3(0, d.getBbHeight() * 0.62, 0);
		Vec3 b = lerped(target, pt).add(0, target.getBbHeight() * 0.62, 0).subtract(base);
		Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(base);
		float t = d.tickCount + pt;
		PoseStack.Pose last = pose.last();
		VertexConsumer glow = buffers.getBuffer(RenderType.debugQuads());
		UltronBeamClient.draw(glow, true, last, a, b, cam, UltronFx.TETHER, 1f, t);
		VertexConsumer core = buffers.getBuffer(RenderType.lightning());
		UltronBeamClient.draw(core, false, last, a, b, cam, UltronFx.TETHER, 1f, t);
	}

	/** The shield bubble (a faint red shell) and the chest cannon's gathering glow. */
	private void renderSentryFx(UltronSentryEntity s, float pt, PoseStack pose, MultiBufferSource buffers) {
		boolean shield = s.shielded();
		byte action = s.action();
		boolean chest = action == UltronSentryEntity.ACTION_CHARGE || action == UltronSentryEntity.ACTION_CANNON;
		if (!shield && !chest) {
			return;
		}
		float t = s.tickCount + pt;
		Vec3 base = lerped(s, pt);
		Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(base);
		PoseStack.Pose last = pose.last();
		float bodyYaw = Mth.rotLerp(pt, s.yBodyRotO, s.yBodyRot);
		Vec3 chestAt = new Vec3(0, s.getBbHeight() * 0.66, 0).add(Vec3.directionFromRotation(0, bodyYaw).scale(0.35));
		VertexConsumer glow = buffers.getBuffer(RenderType.debugQuads());
		if (shield) {
			shell(glow, last, new Vec3(0, s.getBbHeight() * 0.5, 0), s.getBbHeight() * 0.62, 0.16f + 0.05f * Mth.sin(t * 0.3f));
		}
		if (chest) {
			float r = action == UltronSentryEntity.ACTION_CANNON ? 0.7f : 0.3f + 0.25f * Mth.abs(Mth.sin(t * 0.5f));
			BeamDraw.segment(glow, last, chestAt.add(0, -r, 0), chestAt.add(0, r, 0), cam, r, r, UltronBeamClient.GLOW, 0.8f, 0.8f);
			VertexConsumer core = buffers.getBuffer(RenderType.lightning());
			BeamDraw.segment(core, last, chestAt.add(0, -r * 0.4, 0), chestAt.add(0, r * 0.4, 0), cam, r * 0.4f, r * 0.4f,
					UltronBeamClient.CORE, 0.9f, 0.9f);
		}
	}

	/** A latitude/longitude sphere of quads, translucent red, both windings (debugQuads doesn't cull anyway). */
	private static void shell(VertexConsumer vc, PoseStack.Pose pose, Vec3 c, double radius, float alpha) {
		int lat = 10;
		int lon = 16;
		int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255);
		var m = pose.pose();
		for (int i = 0; i < lat; i++) {
			double t0 = Math.PI * i / lat - Math.PI / 2;
			double t1 = Math.PI * (i + 1) / lat - Math.PI / 2;
			for (int j = 0; j < lon; j++) {
				double p0 = Math.PI * 2 * j / lon;
				double p1 = Math.PI * 2 * (j + 1) / lon;
				Vec3 v00 = c.add(Math.cos(t0) * Math.cos(p0) * radius, Math.sin(t0) * radius * 1.15, Math.cos(t0) * Math.sin(p0) * radius);
				Vec3 v01 = c.add(Math.cos(t0) * Math.cos(p1) * radius, Math.sin(t0) * radius * 1.15, Math.cos(t0) * Math.sin(p1) * radius);
				Vec3 v11 = c.add(Math.cos(t1) * Math.cos(p1) * radius, Math.sin(t1) * radius * 1.15, Math.cos(t1) * Math.sin(p1) * radius);
				Vec3 v10 = c.add(Math.cos(t1) * Math.cos(p0) * radius, Math.sin(t1) * radius * 1.15, Math.cos(t1) * Math.sin(p0) * radius);
				int edge = (i + j) % 2 == 0 ? a : (int) (a * 0.7f);
				vc.addVertex(m, (float) v00.x, (float) v00.y, (float) v00.z).setColor(255, 30, 20, edge);
				vc.addVertex(m, (float) v01.x, (float) v01.y, (float) v01.z).setColor(255, 30, 20, edge);
				vc.addVertex(m, (float) v11.x, (float) v11.y, (float) v11.z).setColor(255, 30, 20, edge);
				vc.addVertex(m, (float) v10.x, (float) v10.y, (float) v10.z).setColor(255, 30, 20, edge);
			}
		}
	}

	/** The glowing eyes: the skin's {@code _eyes} texture drawn full-bright over the model. */
	static final class Eyes extends RenderLayer<UltronRobot, UltronModel> {
		Eyes(RenderLayerParent<UltronRobot, UltronModel> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffers, int light, UltronRobot entity, float limbSwing, float limbSwingAmount,
				float partialTick, float age, float headYaw, float headPitch) {
			if (entity.isInvisible() || entity.isStunned() && (entity.tickCount / 3) % 2 == 0) {
				return; // a stunned robot's eyes flicker
			}
			VertexConsumer vc = buffers.getBuffer(RenderType.eyes(entity.skin().eyes()));
			getParentModel().renderToBuffer(pose, vc, 0xF00000, OverlayTexture.NO_OVERLAY);
		}
	}
}
