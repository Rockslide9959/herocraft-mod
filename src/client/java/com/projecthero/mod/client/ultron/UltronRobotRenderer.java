package com.projecthero.mod.client.ultron;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.ultron.UltronFx;
import com.projecthero.mod.ultron.entity.UltronDroneEntity;
import com.projecthero.mod.ultron.entity.UltronHijackerEntity;
import com.projecthero.mod.ultron.entity.UltronLancerEntity;
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
		addLayer(new LancerShield(this));
	}

	/** v0.16.1: a cloaked Hijacker is only a shimmer (vanilla's 15% ghost pass) unless this viewer's Mind Stone / Iron Man highlight reveals it. */
	@Override
	protected boolean isBodyVisible(UltronRobot entity) {
		if (entity instanceof UltronHijackerEntity h && h.cloaked() && !UltronClient.revealsCloaked(h)) {
			return false;
		}
		return super.isBodyVisible(entity);
	}

	static boolean shimmering(UltronRobot entity) {
		return entity instanceof UltronHijackerEntity h && h.cloaked() && !UltronClient.revealsCloaked(h);
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
		if (entity instanceof UltronLancerEntity l && !l.isDeadOrDying()) {
			renderLance(l, partialTick, pose, buffers);
		}
	}

	/** v0.16.1: the Lancer's energy lance -- a red rod out of its right hand, thrust long while it lunges. */
	private void renderLance(UltronLancerEntity l, float pt, PoseStack pose, MultiBufferSource buffers) {
		float bodyYaw = Mth.rotLerp(pt, l.yBodyRotO, l.yBodyRot);
		Vec3 fwd = Vec3.directionFromRotation(0, bodyYaw);
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		float s = l.getScale();
		boolean lunge = l.action() == UltronLancerEntity.ACTION_LUNGE;
		boolean windup = l.action() == UltronLancerEntity.ACTION_WINDUP;
		Vec3 hand = new Vec3(0, l.getBbHeight() * (lunge ? 0.62 : 0.5), 0).add(right.scale(0.36 * s)).add(fwd.scale((lunge ? 0.7 : 0.25) * s));
		double len = lunge ? 3.4 : windup ? 1.1 : 1.6;
		Vec3 butt = hand.subtract(fwd.scale(windup ? 0.9 : 0.5));
		Vec3 tip = hand.add(fwd.scale(len)).add(0, lunge ? 0 : 0.15, 0);
		Vec3 base = lerped(l, pt);
		Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(base);
		float t = l.tickCount + pt;
		PoseStack.Pose last = pose.last();
		VertexConsumer glow = buffers.getBuffer(RenderType.debugQuads());
		BeamDraw.beam(glow, true, last, butt, tip, cam, lunge ? 0.11f : 0.07f, UltronBeamClient.GLOW, 0.85f + 0.15f * Mth.sin(t * 0.7f));
		VertexConsumer core = buffers.getBuffer(RenderType.lightning());
		BeamDraw.beam(core, false, last, butt, tip, cam, lunge ? 0.06f : 0.035f, UltronBeamClient.CORE, 0.95f);
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
	static void shell(VertexConsumer vc, PoseStack.Pose pose, Vec3 c, double radius, float alpha) {
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

	/**
	 * v0.16.1: the Lancer's front energy shield -- a translucent red hex plane, full-bright, carried on the body a little in
	 * front of the chest (model space: -Z is forward, +Y is down). Drawn only while the shield is up; it flickers out after a
	 * lunge.
	 */
	static final class LancerShield extends RenderLayer<UltronRobot, UltronModel> {
		static final ResourceLocation TEXTURE = com.projecthero.mod.ProjectHeroMod.id("textures/entity/ultron/lancer_shield.png");

		LancerShield(RenderLayerParent<UltronRobot, UltronModel> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffers, int light, UltronRobot entity, float limbSwing, float limbSwingAmount,
				float partialTick, float age, float headYaw, float headPitch) {
			if (!(entity instanceof UltronLancerEntity l) || !l.shieldUp() || l.isDeadOrDying()) {
				return;
			}
			pose.pushPose();
			getParentModel().body.translateAndRotate(pose);
			float pulse = 0.72f + 0.18f * Mth.sin(age * 0.35f);
			int a = (int) (pulse * 255);
			VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
			PoseStack.Pose last = pose.last();
			float x0 = -0.62f, x1 = 0.62f, y0 = -0.42f, y1 = 1.1f, z = -0.5f;
			float v1 = (y1 - y0) / (x1 - x0);
			int full = 0xF000F0;
			// front face and back face (both windings)
			quad(vc, last, x0, y0, z, x1, y1, a, v1, full, -1);
			quad(vc, last, x1, y0, z, x0, y1, a, v1, full, 1);
			pose.popPose();
		}

		private static void quad(VertexConsumer vc, PoseStack.Pose p, float xa, float y0, float z, float xb, float y1, int a, float v1, int light,
				float nz) {
			vc.addVertex(p, xa, y0, z).setColor(255, 255, 255, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, nz);
			vc.addVertex(p, xa, y1, z).setColor(255, 255, 255, a).setUv(0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, nz);
			vc.addVertex(p, xb, y1, z).setColor(255, 255, 255, a).setUv(1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, nz);
			vc.addVertex(p, xb, y0, z).setColor(255, 255, 255, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, nz);
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
			if (entity.isInvisible() || shimmering(entity) || entity.isStunned() && (entity.tickCount / 3) % 2 == 0) {
				return; // a stunned robot's eyes flicker; a cloaked Hijacker shows nothing but the shimmer
			}
			VertexConsumer vc = buffers.getBuffer(RenderType.eyes(entity.skin().eyes()));
			getParentModel().renderToBuffer(pose, vc, 0xF00000, OverlayTexture.NO_OVERLAY);
		}
	}
}
