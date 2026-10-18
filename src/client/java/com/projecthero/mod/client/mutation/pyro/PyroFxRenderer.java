package com.projecthero.mod.client.mutation.pyro;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.hero.power.p08.PyroFxEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Pyrokinesis: the fire shapes of {@link PyroFxEntity}.
 * <ul>
 * <li><b>Flame Wall</b> -- two staggered rows of vanilla's animated fire, crossed sheets taller in the middle, flickering
 * column by column, over a glowing ember bed; it roars up over a few ticks and gutters out at the end;</li>
 * <li><b>Nova</b> -- a ring of flame sheets rolling outward over the ground with a glowing shock ring and a flash;</li>
 * <li><b>Heat Wave</b> -- three shells of shimmering heat rolling out across the cone;</li>
 * <li><b>Meteor blast</b> -- a ball of fire swelling and fading, a ground shock ring and a crown of flames;</li>
 * <li>v0.16.6 <b>Ring of Fire</b> -- two staggered rings of fire sheets with a swell rolling round them, over a glowing ember
 * band;</li>
 * <li>v0.16.6 <b>Solar Flare</b> -- a white-hot sun swelling out of the caster with a corona of long rays and a shock
 * ring.</li>
 * </ul>
 */
public class PyroFxRenderer extends EntityRenderer<PyroFxEntity> {
	public PyroFxRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public boolean shouldRender(PyroFxEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
		return true; // much bigger than its box
	}

	@Override
	public void render(PyroFxEntity e, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float age = e.age() + partial;
		switch (e.kind()) {
			case PyroFxEntity.WALL -> wall(e, age, pose, buffers);
			case PyroFxEntity.NOVA -> nova(e, age, pose, buffers);
			case PyroFxEntity.WAVE -> wave(e, age, pose, buffers);
			case PyroFxEntity.BLAST -> blast(e, age, pose, buffers);
			case PyroFxEntity.RING -> ring(e, age, pose, buffers);
			case PyroFxEntity.FLARE -> flare(e, age, pose, buffers);
			default -> {
			}
		}
		super.render(e, entityYaw, partial, pose, buffers, light);
	}

	private static float ease(float t) {
		t = Mth.clamp(t, 0.0f, 1.0f);
		return 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
	}

	// ------------------------------------------------------------------ Flame Wall

	private static void wall(PyroFxEntity e, float age, PoseStack pose, MultiBufferSource buffers) {
		float life = e.life();
		float grow = ease(age / 6.0f);
		float die = Mth.clamp((life - age) / 14.0f, 0.0f, 1.0f);
		float h = 2.7f * grow * (0.35f + 0.65f * die);
		if (h < 0.05f) {
			return;
		}
		float half = e.size() * 0.5f;
		float yaw = e.getYRot() * Mth.DEG_TO_RAD;
		Vec3 along = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
		// the ember bed and the glow along the base (camera-facing glows: done in the unrotated frame)
		VertexConsumer glow = PyroDraw.glowBuffer(buffers);
		int cols = Math.round(e.size() * 2) + 1;
		for (int i = 0; i < cols; i += 2) {
			float x = -half + i * (e.size() / (cols - 1));
			float flick = 0.8f + 0.2f * Mth.sin(age * 0.7f + i);
			PyroDraw.glow(pose, glow, along.scale(x).add(0, 0.5 * h / 2.7, 0), 1.3f * grow, PyroDraw.dim(0xFFFF6A10, 0.55f * die * flick));
		}
		pose.pushPose();
		pose.mulPose(new Quaternionf().rotationY(-yaw));
		VertexConsumer add = PyroDraw.additive(buffers);
		// the bed: a strip of hot ground
		pose.pushPose();
		pose.translate(0, 0.03, 0);
		PoseStack.Pose last = pose.last();
		int bed = PyroDraw.dim(0xFFFF5010, 0.9f * die);
		int edge = 0xFF000000;
		strip(add, last, -half - 0.3f, half + 0.3f, -0.55f, 0.0f, edge, bed);
		strip(add, last, -half - 0.3f, half + 0.3f, 0.0f, 0.55f, bed, edge);
		pose.popPose();
		VertexConsumer fire = PyroDraw.fireBuffer(buffers);
		int frame = (int) (age / 3);
		for (int row = 0; row < 2; row++) {
			float z = row == 0 ? -0.18f : 0.18f;
			float off = row == 0 ? 0.0f : 0.5f * e.size() / (cols - 1);
			for (int i = 0; i < cols - row; i++) {
				float x = -half + off + i * (e.size() / (cols - 1));
				float mid = 1.0f - 0.35f * Math.abs(x) / half; // taller in the middle
				float flick = 0.72f + 0.28f * Mth.sin(age * 0.45f + i * 1.9f + row * 2.3f);
				float ch = h * mid * flick * (row == 0 ? 1.0f : 0.8f);
				pose.pushPose();
				pose.translate(x, 0, z);
				PyroDraw.fireCross(pose, fire, 0.95f, ch, frame + i + row, 0.6f * i + row);
				pose.popPose();
			}
		}
		pose.popPose();
	}

	/** A flat strip on the ground from x0..x1, z0..z1: colour {@code c0} along z0, {@code c1} along z1 (both faces). */
	private static void strip(VertexConsumer vc, PoseStack.Pose last, float x0, float x1, float z0, float z1, int c0, int c1) {
		vc.addVertex(last, x0, 0, z0).setColor(c0).setUv(0, 0).setOverlay(0).setLight(PyroDraw.FULL).setNormal(last, 0, 1, 0);
		vc.addVertex(last, x0, 0, z1).setColor(c1).setUv(0, 1).setOverlay(0).setLight(PyroDraw.FULL).setNormal(last, 0, 1, 0);
		vc.addVertex(last, x1, 0, z1).setColor(c1).setUv(1, 1).setOverlay(0).setLight(PyroDraw.FULL).setNormal(last, 0, 1, 0);
		vc.addVertex(last, x1, 0, z0).setColor(c0).setUv(1, 0).setOverlay(0).setLight(PyroDraw.FULL).setNormal(last, 0, 1, 0);
	}

	// ------------------------------------------------------------------ Nova

	private static void nova(PyroFxEntity e, float age, PoseStack pose, MultiBufferSource buffers) {
		float life = e.life();
		float f = ease(age / (life * 0.6f));
		float k = 1.0f - Mth.clamp((age - life * 0.45f) / (life * 0.55f), 0.0f, 1.0f);
		float r = Math.max(0.4f, e.size() * f);
		VertexConsumer add = PyroDraw.additive(buffers);
		// the flash at the start, and the shock ring on the ground
		PyroDraw.annulus(pose, add, Math.max(0.0f, r - 1.6f), r + 0.25f, 0xFF000000, PyroDraw.dim(0xFFFF7A20, k), 0.06f);
		PyroDraw.annulus(pose, add, 0.0f, Math.max(0.1f, r - 1.6f), PyroDraw.dim(0xFFFFD080, (1 - f) * 0.9f), 0xFF000000, 0.05f);
		pose.pushPose();
		pose.translate(0, 0.6f * k, 0);
		PyroDraw.band(pose, add, r, 0.6f * k, PyroDraw.dim(0xFFFF6010, 0.8f * k), 0xFF000000);
		pose.popPose();
		PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), new Vec3(0, 1.0, 0), 2.5f + r * 0.5f, PyroDraw.dim(0xFFFF9030, (1 - f) * 0.9f));
		// the ring of flames, leaning outward as it rolls
		VertexConsumer fire = PyroDraw.fireBuffer(buffers);
		int n = Math.max(16, Math.round(r * 9));
		int frame = (int) (age / 2);
		for (int i = 0; i < n; i++) {
			float a = Mth.TWO_PI * i / n;
			float h = (1.4f + 0.5f * Mth.sin(i * 2.7f + age * 0.6f)) * k * (0.6f + 0.4f * f);
			if (h < 0.05f) {
				continue;
			}
			pose.pushPose();
			pose.translate(Mth.cos(a) * r, 0, Mth.sin(a) * r);
			// face outward (the sheet's plane is tangent to the ring), lean out
			pose.mulPose(new Quaternionf().rotationY(-a + Mth.HALF_PI));
			pose.mulPose(new Quaternionf().rotationX(-0.35f));
			PyroDraw.fireSheet(pose, fire, 1.1f, h, frame + i);
			pose.popPose();
		}
	}

	// ------------------------------------------------------------------ Heat Wave

	private static void wave(PyroFxEntity e, float age, PoseStack pose, MultiBufferSource buffers) {
		float life = e.life();
		float f = age / life;
		if (f >= 1.0f) {
			return;
		}
		pose.pushPose();
		pose.mulPose(new Quaternionf().rotationY(-e.getYRot() * Mth.DEG_TO_RAD));
		pose.mulPose(new Quaternionf().rotationX(e.getXRot() * Mth.DEG_TO_RAD));
		VertexConsumer add = PyroDraw.additive(buffers);
		PoseStack.Pose last = pose.last();
		for (int j = 0; j < 3; j++) {
			float rr = (ease(f) * 1.1f - j * 0.16f) * e.size();
			if (rr < 0.6f || rr > e.size()) {
				continue;
			}
			float k = (1.0f - rr / e.size()) * (1.0f - f * 0.6f) * (j == 0 ? 1.0f : 0.6f);
			cap(add, last, rr, 0.95f, 0.5f, PyroDraw.dim(0xFFFF7020, 0.28f * k), PyroDraw.dim(0xFFFFB060, 0.5f * k), age + j);
		}
		pose.popPose();
	}

	/** A shell of radius {@code r} spanning +-{@code ha} across and +-{@code va} up, centred on +z; brighter rim, rippling. */
	private static void cap(VertexConsumer vc, PoseStack.Pose last, float r, float ha, float va, int fill, int rim, float time) {
		int nu = 14;
		int nv = 6;
		for (int i = 0; i < nu; i++) {
			for (int j = 0; j < nv; j++) {
				float a0 = -ha + 2 * ha * i / nu, a1 = -ha + 2 * ha * (i + 1) / nu;
				float b0 = -va + 2 * va * j / nv, b1 = -va + 2 * va * (j + 1) / nv;
				cv(vc, last, r, a0, b0, ha, va, fill, rim, time);
				cv(vc, last, r, a1, b0, ha, va, fill, rim, time);
				cv(vc, last, r, a1, b1, ha, va, fill, rim, time);
				cv(vc, last, r, a0, b1, ha, va, fill, rim, time);
				// back face
				cv(vc, last, r, a0, b1, ha, va, fill, rim, time);
				cv(vc, last, r, a1, b1, ha, va, fill, rim, time);
				cv(vc, last, r, a1, b0, ha, va, fill, rim, time);
				cv(vc, last, r, a0, b0, ha, va, fill, rim, time);
			}
		}
	}

	private static void cv(VertexConsumer vc, PoseStack.Pose last, float r, float a, float b, float ha, float va, int fill, int rim,
			float time) {
		float rr = r * (1.0f + 0.04f * Mth.sin(a * 9.0f + time * 1.3f) * Mth.cos(b * 7.0f - time));
		float x = Mth.sin(a) * Mth.cos(b) * rr;
		float y = Mth.sin(b) * rr;
		float z = Mth.cos(a) * Mth.cos(b) * rr;
		float edge = Math.max(Math.abs(a) / ha, Math.abs(b) / va);
		// fades to black at the very edge; brightest just inside it
		int c = edge > 0.92f ? 0xFF000000 : PyroDraw.lerpRgb(fill, rim, edge * edge);
		vc.addVertex(last, x, y, z).setColor(c).setUv(0.5f, 0.5f).setOverlay(0).setLight(PyroDraw.FULL).setNormal(last, 0, 0, 1);
	}

	// ------------------------------------------------------------------ Meteor blast

	private static void blast(PyroFxEntity e, float age, PoseStack pose, MultiBufferSource buffers) {
		float life = e.life();
		float f = Mth.clamp(age / life, 0.0f, 1.0f);
		float swell = ease(age / 6.0f);
		float k = (1.0f - f) * (1.0f - f);
		float r = e.size() * 0.75f * swell;
		VertexConsumer add = PyroDraw.additive(buffers);
		// a burst of fireballs flung out of the impact, shrinking as they go
		for (int i = 0; i < 9; i++) {
			float a = i * 2.39996f;
			float up = 0.25f + 0.55f * ((i * 37) % 9) / 9.0f;
			Vec3 dir = new Vec3(Mth.cos(a) * (1 - up), up, Mth.sin(a) * (1 - up)).normalize();
			pose.pushPose();
			Vec3 at = dir.scale(r * 0.85f).add(0, 0.4, 0);
			pose.translate(at.x, at.y, at.z);
			PyroDraw.fireball(pose, buffers, (0.35f + 0.5f * (1 - f)) * (1.0f - 0.6f * f), age + i * 3, dir, true);
			pose.popPose();
		}
		pose.pushPose();
		pose.translate(0, 0.8f, 0);
		PyroDraw.sphere(pose, add, r * 0.45f * (1.0f - 0.5f * f), PyroDraw.dim(0xFFFFE090, k), PyroDraw.dim(0xFFB03000, k), 0.2f, age * 2.6f,
				PyroDraw.FULL);
		PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), Vec3.ZERO, r * 1.4f + 1.0f, PyroDraw.dim(0xFFFF7020, k * 0.8f));
		pose.popPose();
		float ring = e.size() * 1.3f * ease(age / (life * 0.7f));
		PyroDraw.annulus(pose, add, Math.max(0.0f, ring - 1.2f), ring, 0xFF000000, PyroDraw.dim(0xFFFF8030, k), 0.08f);
		VertexConsumer fire = PyroDraw.fireBuffer(buffers);
		int n = 14;
		for (int i = 0; i < n; i++) {
			float a = Mth.TWO_PI * i / n + 0.3f;
			float rr = r * 0.9f + 0.3f;
			float h = (1.6f + 0.8f * Mth.sin(i * 1.9f)) * k * swell;
			if (h < 0.05f) {
				continue;
			}
			pose.pushPose();
			pose.translate(Mth.cos(a) * rr, 0, Mth.sin(a) * rr);
			PyroDraw.fireCross(pose, fire, 1.0f, h, (int) (age / 2) + i, a);
			pose.popPose();
		}
	}

	// ------------------------------------------------------------------ v0.16.6 Ring of Fire

	private static void ring(PyroFxEntity e, float age, PoseStack pose, MultiBufferSource buffers) {
		float life = e.life();
		float grow = ease(age / 8.0f);
		float die = Mth.clamp((life - age) / 14.0f, 0.0f, 1.0f);
		float k = grow * die;
		if (k < 0.02f) {
			return;
		}
		float r = e.size();
		VertexConsumer add = PyroDraw.additive(buffers);
		// the ember bed: a glowing band of scorched ground under the flames
		PyroDraw.annulus(pose, add, r - 0.7f, r, 0xFF000000, PyroDraw.dim(0xFFFF5010, 0.95f * k), 0.04f);
		PyroDraw.annulus(pose, add, r, r + 0.7f, PyroDraw.dim(0xFFFF5010, 0.95f * k), 0xFF000000, 0.04f);
		// a low wall of heat light round the circle
		pose.pushPose();
		pose.translate(0, 0.55f, 0);
		PyroDraw.band(pose, add, r, 0.55f * k, PyroDraw.dim(0xFFFF6A10, 0.55f * k), 0xFF000000);
		pose.popPose();
		// the flames: two staggered rings of crossed fire sheets, a swell rolling round them
		VertexConsumer fire = PyroDraw.fireBuffer(buffers);
		int n = Math.max(24, Math.round(Mth.TWO_PI * r * 1.7f));
		int frame = (int) (age / 3);
		for (int row = 0; row < 2; row++) {
			float rr = r + (row == 0 ? -0.18f : 0.18f);
			for (int i = 0; i < n; i++) {
				float a = Mth.TWO_PI * (i + row * 0.5f) / n + age * 0.004f;
				float flick = 0.7f + 0.3f * Mth.sin(age * 0.45f + i * 1.9f + row * 2.3f);
				float roll = 0.85f + 0.25f * Mth.sin(a * 3.0f - age * 0.2f);
				float h = 2.2f * k * flick * roll * (row == 0 ? 1.0f : 0.8f);
				if (h < 0.05f) {
					continue;
				}
				pose.pushPose();
				pose.translate(Mth.cos(a) * rr, 0, Mth.sin(a) * rr);
				PyroDraw.fireCross(pose, fire, 0.9f, h, frame + i + row, a + i * 0.4f);
				pose.popPose();
			}
		}
		// glows round the ring (camera-facing)
		VertexConsumer glow = PyroDraw.glowBuffer(buffers);
		for (int i = 0; i < 12; i++) {
			float a = Mth.TWO_PI * i / 12 + age * 0.01f;
			float flick = 0.75f + 0.25f * Mth.sin(age * 0.6f + i * 2.1f);
			PyroDraw.glow(pose, glow, new Vec3(Mth.cos(a) * r, 0.9, Mth.sin(a) * r), 1.6f * k, PyroDraw.dim(0xFFFF6A10, 0.45f * k * flick));
		}
	}

	// ------------------------------------------------------------------ v0.16.6 Solar Flare

	private static void flare(PyroFxEntity e, float age, PoseStack pose, MultiBufferSource buffers) {
		float life = e.life();
		float f = Mth.clamp(age / life, 0.0f, 1.0f);
		float swell = ease(age / 4.0f);
		float k = (1.0f - f) * (1.0f - f);
		VertexConsumer add = PyroDraw.additive(buffers);
		VertexConsumer glow = PyroDraw.glowBuffer(buffers);
		// the sun: a white-hot churning core in a yellow photosphere, swelling out and burning down
		float r = (0.6f + 1.9f * swell) * (1.0f - 0.45f * f);
		VertexConsumer solid = PyroDraw.solidBuffer(buffers);
		if (f < 0.55f) {
			PyroDraw.sphere(pose, solid, r * 0.55f, 0xFFFFFFFF, 0xFFFFF0B0, 0.06f, age * 2.0f, PyroDraw.FULL);
		}
		PyroDraw.sphere(pose, add, r * 0.8f, PyroDraw.dim(0xFFFFFCE0, k), PyroDraw.dim(0xFFFFB030, k), 0.14f, age * 2.6f, PyroDraw.FULL);
		PyroDraw.sphere(pose, add, r, PyroDraw.dim(0xFFFF9020, k * 0.9f), PyroDraw.dim(0xFF601000, k * 0.6f), 0.3f, -age * 1.7f + 4.0f,
				PyroDraw.FULL);
		// the corona: long rays flung out in every direction, growing and thinning
		int rays = 22;
		for (int i = 0; i < rays; i++) {
			float a = i * 2.39996f;
			float y = 1.0f - 2.0f * (i + 0.5f) / rays;
			float rad = Mth.sqrt(Math.max(0.0f, 1.0f - y * y));
			Vec3 dir = new Vec3(Mth.cos(a) * rad, y, Mth.sin(a) * rad);
			float len = (2.0f + 5.5f * ease(age / 8.0f)) * (0.7f + 0.3f * Mth.sin(i * 3.7f)) * (1.0f - 0.3f * f);
			pose.pushPose();
			pose.translate(dir.x * r * 0.7, dir.y * r * 0.7, dir.z * r * 0.7);
			PyroDraw.alignY(pose, dir);
			PyroDraw.cone(pose, add, 0.32f * (1.0f - 0.5f * f), len, PyroDraw.dim(0xFFFFF0C0, k), 0xFF000000);
			pose.popPose();
		}
		// the shock ring rolling out to the edge of the flare
		float ring = e.size() * ease(age / (life * 0.6f));
		pose.pushPose();
		pose.translate(0, -1.1f, 0);
		PyroDraw.annulus(pose, add, Math.max(0.0f, ring - 1.4f), ring, 0xFF000000, PyroDraw.dim(0xFFFFD080, k), 0.06f);
		pose.popPose();
		PyroDraw.glow(pose, glow, Vec3.ZERO, 3.0f + 7.0f * swell, PyroDraw.dim(0xFFFFE6A0, k));
		PyroDraw.glow(pose, glow, Vec3.ZERO, 1.5f + 3.0f * swell, PyroDraw.dim(0xFFFFFFFF, k));
	}

	@Override
	public ResourceLocation getTextureLocation(PyroFxEntity entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
