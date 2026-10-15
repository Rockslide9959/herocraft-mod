package com.projecthero.mod.client.ghostrider;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ghostrider.entity.HellfireBlastEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * v0.16.5 rework: the Hellfire Blast bolt -- a ball of skull fire. Three layers:
 * <ol>
 *   <li>a white-hot core inside orange and ember-red glows (additive camera-facing discs);</li>
 *   <li>the vanilla animated fire sprites: one facing the camera over the ball and four tongues streaming back from
 *       it, slowly turning round the line of flight;</li>
 *   <li>a short trail of fading glows through the last few positions it has been at ({@link HellfireBlastEntity#trail}).</li>
 * </ol>
 * {@code RenderType.lightning} is additive and culled, so every glow disc is drawn in both windings. The fire sprites
 * carry the normal (0, 1, 0): this pose is plain world space, where up is +Y (the skull fire's (0, -1, 0) is "up" only
 * because a player model's space is flipped; here it would light the flames from below, dark brown).
 */
public class HellfireBlastRenderer extends EntityRenderer<HellfireBlastEntity> {
	private static final int SEGMENTS = 14;

	public HellfireBlastRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0f;
	}

	@Override
	public void render(HellfireBlastEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float t = e.tickCount + partial;
		Vec3 here = new Vec3(Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.yo, e.getY()), Mth.lerp(partial, e.zo, e.getZ()));
		Vec3 v = e.getDeltaMovement();
		Vec3 dir = v.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : v.normalize();

		pose.pushPose();
		pose.translate(0, e.getBbHeight() * 0.5, 0);

		// ---- the trail: fading glows through where it has been
		VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
		// (a bolt covers 1.8 blocks a tick: three glows a step close the gaps)
		for (int i = 1; i < e.trailCount; i++) {
			Vec3 a = e.trail[i - 1], b = e.trail[i];
			if (a == null || b == null) {
				continue;
			}
			for (int j = 0; j < 3; j++) {
				float f = (i - 1 + (j + 1) / 3f) / (float) HellfireBlastEntity.TRAIL_LENGTH;
				float k = 1f - f;
				Vec3 off = a.add(b.subtract(a).scale((j + 1) / 3.0)).subtract(here);
				pose.pushPose();
				pose.translate(off.x, off.y, off.z);
				pose.mulPose(entityRenderDispatcher.cameraOrientation());
				disc(glow, pose.last().pose(), 0.30f * k + 0.06f, 255, 110, 20, (int) (120 * k), 0);
				pose.popPose();
			}
		}

		// ---- the fire: tongues streaming back, turning round the line of flight
		VertexConsumer fire = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
		TextureAtlasSprite fire0 = ModelBakery.FIRE_0.sprite();
		TextureAtlasSprite fire1 = ModelBakery.FIRE_1.sprite();
		Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 side = dir.cross(up).normalize();
		up = side.cross(dir).normalize();
		Matrix4f m = pose.last().pose();
		PoseStack.Pose last = pose.last();
		for (int k = 0; k < 4; k++) {
			double a = k * Math.PI / 2 + t * 0.25;
			Vec3 r = side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
			float len = 0.85f + 0.15f * Mth.sin(t * 0.9f + k * 1.9f);
			float w = 0.30f;
			Vec3 front = dir.scale(0.12);
			Vec3 back = dir.scale(-len);
			Vec3 b0 = front.add(r.scale(-w)), b1 = front.add(r.scale(w));
			Vec3 t1 = back.add(r.scale(w * 0.45)), t0 = back.add(r.scale(-w * 0.45));
			fireQuad(fire, m, last, (k & 1) == 0 ? fire0 : fire1, b0, b1, t1, t0);
		}
		// one sheet facing the camera, rising off the ball
		pose.pushPose();
		pose.mulPose(entityRenderDispatcher.cameraOrientation());
		Matrix4f mb = pose.last().pose();
		PoseStack.Pose lb = pose.last();
		float s = 0.36f + 0.04f * Mth.sin(t * 1.3f);
		fireQuad(fire, mb, lb, ((int) (t / 3) & 1) == 0 ? fire0 : fire1, new Vec3(-s, -s * 0.9, 0), new Vec3(s, -s * 0.9, 0),
				new Vec3(s, s * 1.3, 0), new Vec3(-s, s * 1.3, 0));

		// ---- the core: white-hot inside orange inside ember red
		glow = buffers.getBuffer(RenderType.lightning()); // (fetched again: switching buffers ends the last batch)
		float pulse = 1f + 0.08f * Mth.sin(t * 1.7f);
		disc(glow, mb, 0.62f * pulse, 200, 40, 10, 90, 0);
		disc(glow, mb, 0.40f * pulse, 255, 120, 20, 200, 30);
		disc(glow, mb, 0.20f * pulse, 255, 240, 170, 255, 200);
		pose.popPose();

		pose.popPose();
		super.render(e, yaw, partial, pose, buffers, light);
	}

	/** A soft disc in the XY plane: {@code a0} alpha at the centre, {@code a1} at the rim. Both windings. */
	private static void disc(VertexConsumer vc, Matrix4f m, float radius, int r, int g, int b, int a0, int a1) {
		for (int i = 0; i < SEGMENTS; i++) {
			float x0 = Mth.cos(i * Mth.TWO_PI / SEGMENTS) * radius, y0 = Mth.sin(i * Mth.TWO_PI / SEGMENTS) * radius;
			float x1 = Mth.cos((i + 1) * Mth.TWO_PI / SEGMENTS) * radius, y1 = Mth.sin((i + 1) * Mth.TWO_PI / SEGMENTS) * radius;
			// a quad degenerate at the centre (lightning is a QUADS mode)
			vc.addVertex(m, 0f, 0f, 0f).setColor(r, g, b, a0);
			vc.addVertex(m, x0, y0, 0f).setColor(r, g, b, a1);
			vc.addVertex(m, x1, y1, 0f).setColor(r, g, b, a1);
			vc.addVertex(m, 0f, 0f, 0f).setColor(r, g, b, a0);
			vc.addVertex(m, 0f, 0f, 0f).setColor(r, g, b, a0);
			vc.addVertex(m, x1, y1, 0f).setColor(r, g, b, a1);
			vc.addVertex(m, x0, y0, 0f).setColor(r, g, b, a1);
			vc.addVertex(m, 0f, 0f, 0f).setColor(r, g, b, a0);
		}
	}

	/** A fire-sprite quad: b0-b1 the flame's base, t1-t0 its tips. */
	private static void fireQuad(VertexConsumer vc, Matrix4f m, PoseStack.Pose last, TextureAtlasSprite sp, Vec3 b0, Vec3 b1, Vec3 t1, Vec3 t0) {
		float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
		vertex(vc, m, last, b0, u0, v1);
		vertex(vc, m, last, b1, u1, v1);
		vertex(vc, m, last, t1, u1, v0);
		vertex(vc, m, last, t0, u0, v0);
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose last, Vec3 p, float u, float v) {
		vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT).setNormal(last, 0f, 1f, 0f);
	}

	@Override
	public ResourceLocation getTextureLocation(HellfireBlastEntity e) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
