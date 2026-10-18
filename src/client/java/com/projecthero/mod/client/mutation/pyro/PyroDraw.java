package com.projecthero.mod.client.mutation.pyro;

import java.util.List;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.MutationRender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Pyrokinesis: the drawing primitives every fire model is built from -- a molten, wobbling sphere (vertex-coloured
 * from white-hot to deep orange), vanilla's own animated fire sprite as crossed sheets, soft additive glow billboards,
 * camera-facing ribbons along a curve (the whip, the lasso rope) and glowing cones (the jets). Additive parts use
 * {@link RenderType#eyes} so they glow at full bright and black means invisible: colour fades are done by darkening.
 */
public final class PyroDraw {
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/mutation/p08_glow.png");
	public static final int FULL = LightTexture.FULL_BRIGHT;

	private PyroDraw() {
	}

	private static boolean soul;
	private static final ResourceLocation SOUL_0 = ResourceLocation.withDefaultNamespace("block/soul_fire_0");
	private static final ResourceLocation SOUL_1 = ResourceLocation.withDefaultNamespace("block/soul_fire_1");

	/**
	 * v0.16.6: while on, every fire sprite drawn is vanilla's blue soul fire (the Blue Flame). Render thread only -- turn it
	 * off again straight after.
	 */
	public static void soul(boolean on) {
		soul = on;
	}

	public static TextureAtlasSprite fire(int frame) {
		if (soul) {
			return Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)
					.apply((frame & 1) == 0 ? SOUL_0 : SOUL_1);
		}
		return (frame & 1) == 0 ? ModelBakery.FIRE_0.sprite() : ModelBakery.FIRE_1.sprite();
	}

	public static VertexConsumer additive(MultiBufferSource buffers) {
		return new Lazy(buffers, RenderType.eyes(MutationRender.WHITE));
	}

	public static VertexConsumer glowBuffer(MultiBufferSource buffers) {
		return new Lazy(buffers, RenderType.eyes(GLOW));
	}

	public static VertexConsumer fireBuffer(MultiBufferSource buffers) {
		return new Lazy(buffers, Sheets.cutoutBlockSheet());
	}

	public static VertexConsumer solidBuffer(MultiBufferSource buffers) {
		return new Lazy(buffers, RenderType.entityCutoutNoCull(MutationRender.WHITE));
	}

	/**
	 * A consumer that fetches its buffer afresh for every vertex, so several render types can be drawn interleaved (a
	 * shared buffer source ends one type's buffer as soon as another is asked for -- writing to the old one then crashes
	 * with "Not building!"). Every primitive here writes whole quads, so a switch only ever falls between quads.
	 */
	private static final class Lazy implements VertexConsumer {
		private final MultiBufferSource buffers;
		private final RenderType type;
		private VertexConsumer cur;

		Lazy(MultiBufferSource buffers, RenderType type) {
			this.buffers = buffers;
			this.type = type;
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			cur = buffers.getBuffer(type);
			cur.addVertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			cur.setColor(r, g, b, a);
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			cur.setUv(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			cur.setUv1(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			cur.setUv2(u, v);
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			cur.setNormal(x, y, z);
			return this;
		}
	}

	/** Scales an RGB colour by {@code k} (additive fades). */
	public static int dim(int rgb, float k) {
		k = Mth.clamp(k, 0.0f, 1.0f);
		int r = (int) (((rgb >> 16) & 255) * k);
		int g = (int) (((rgb >> 8) & 255) * k);
		int b = (int) ((rgb & 255) * k);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	public static int lerpRgb(int a, int b, float t) {
		t = Mth.clamp(t, 0.0f, 1.0f);
		int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
		int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
		int bl = (int) Mth.lerp(t, a & 255, b & 255);
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}

	private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, int argb, float u, float vv, int light,
			float nx, float ny, float nz) {
		vc.addVertex(pose, x, y, z).setColor(argb).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(pose, nx, ny, nz);
	}

	// ------------------------------------------------------------------ spheres

	/**
	 * A UV sphere of radius {@code r} round the pose origin. {@code wobble} pushes the surface in and out with a rolling
	 * noise (0 = smooth); {@code hot} and {@code cool} colour it: the hot colour where the noise peaks, so the surface
	 * looks molten and churns with {@code time}.
	 */
	public static void sphere(PoseStack pose, VertexConsumer vc, float r, int hot, int cool, float wobble, float time, int light) {
		PoseStack.Pose last = pose.last();
		int rings = 10;
		int segs = 16;
		for (int i = 0; i < rings; i++) {
			float a0 = Mth.PI * i / rings - Mth.HALF_PI;
			float a1 = Mth.PI * (i + 1) / rings - Mth.HALF_PI;
			for (int j = 0; j < segs; j++) {
				float b0 = Mth.TWO_PI * j / segs;
				float b1 = Mth.TWO_PI * (j + 1) / segs;
				sv(vc, last, r, a0, b0, hot, cool, wobble, time, light);
				sv(vc, last, r, a1, b0, hot, cool, wobble, time, light);
				sv(vc, last, r, a1, b1, hot, cool, wobble, time, light);
				sv(vc, last, r, a0, b1, hot, cool, wobble, time, light);
			}
		}
	}

	private static void sv(VertexConsumer vc, PoseStack.Pose last, float r, float lat, float lon, int hot, int cool, float wobble,
			float time, int light) {
		float cl = Mth.cos(lat);
		float nx = cl * Mth.cos(lon);
		float ny = Mth.sin(lat);
		float nz = cl * Mth.sin(lon);
		float n = 0.5f + 0.25f * Mth.sin(lat * 3.1f + time * 0.37f) + 0.25f * Mth.sin(lon * 3.0f - time * 0.29f + lat * 1.7f);
		float rr = r * (1.0f + wobble * (n - 0.5f) * 2.0f);
		int c = lerpRgb(cool, hot, n);
		v(vc, last, nx * rr, ny * rr, nz * rr, c, 0.5f, 0.5f, light, nx, ny, nz);
	}

	// ------------------------------------------------------------------ the fire sprite

	/**
	 * Two crossed upright sheets of vanilla's animated fire, {@code w} wide and {@code h} tall, standing on the pose origin
	 * (y up). Cutout, full bright.
	 */
	public static void fireCross(PoseStack pose, VertexConsumer vc, float w, float h, int frame, float yaw) {
		TextureAtlasSprite s = fire(frame);
		pose.pushPose();
		pose.mulPose(new Quaternionf().rotationY(yaw));
		PoseStack.Pose last = pose.last();
		float hw = w * 0.5f;
		for (int k = 0; k < 2; k++) {
			float ax = k == 0 ? hw : 0.0f;
			float az = k == 0 ? 0.0f : hw;
			float u0 = s.getU0(), u1 = s.getU1(), v0 = s.getV0(), v1 = s.getV1();
			// both faces, so it reads from behind too
			fv(vc, last, -ax, 0, -az, u1, v1);
			fv(vc, last, ax, 0, az, u0, v1);
			fv(vc, last, ax, h, az, u0, v0);
			fv(vc, last, -ax, h, -az, u1, v0);
			fv(vc, last, -ax, h, -az, u1, v0);
			fv(vc, last, ax, h, az, u0, v0);
			fv(vc, last, ax, 0, az, u0, v1);
			fv(vc, last, -ax, 0, -az, u1, v1);
		}
		pose.popPose();
	}

	/** One flat upright fire sheet in the pose's XY plane ({@code w} wide, centred on x = 0, rising {@code h}), both faces. */
	public static void fireSheet(PoseStack pose, VertexConsumer vc, float w, float h, int frame) {
		TextureAtlasSprite s = fire(frame);
		PoseStack.Pose last = pose.last();
		float hw = w * 0.5f;
		float u0 = s.getU0(), u1 = s.getU1(), v0 = s.getV0(), v1 = s.getV1();
		fv(vc, last, -hw, 0, 0, u1, v1);
		fv(vc, last, hw, 0, 0, u0, v1);
		fv(vc, last, hw, h, 0, u0, v0);
		fv(vc, last, -hw, h, 0, u1, v0);
		fv(vc, last, -hw, h, 0, u1, v0);
		fv(vc, last, hw, h, 0, u0, v0);
		fv(vc, last, hw, 0, 0, u0, v1);
		fv(vc, last, -hw, 0, 0, u1, v1);
	}

	private static void fv(VertexConsumer vc, PoseStack.Pose last, float x, float y, float z, float u, float vv) {
		vc.addVertex(last, x, y, z).setColor(-1).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL)
				.setNormal(last, 0.0f, 1.0f, 0.0f);
	}

	/** A fire sheet at world-relative {@code at} (camera-relative pose space), turned about y to face the camera. */
	public static void fireBillboard(PoseStack pose, VertexConsumer vc, Vec3 at, Vec3 cam, float w, float h, int frame) {
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		float yaw = (float) Math.atan2(cam.x - at.x, cam.z - at.z);
		pose.mulPose(new Quaternionf().rotationY(yaw));
		fireSheet(pose, vc, w, h, frame);
		pose.popPose();
	}

	// ------------------------------------------------------------------ glow billboards

	/**
	 * Undoes the pose's rotation (and any mirroring) so what follows is drawn facing the camera, whatever space the pose is
	 * in -- world, an entity renderer or a player model.
	 */
	public static void faceCamera(PoseStack pose) {
		org.joml.Matrix3f inv = new org.joml.Matrix3f(pose.last().normal());
		float det = inv.determinant();
		if (Math.abs(det) < 1.0e-8f) {
			return;
		}
		inv.invert(); // the normal matrix is the pose's rotation without its scale, so the scale is kept
		Matrix4f m = new Matrix4f().set(inv);
		pose.mulPose(m);
	}

	/** A soft round glow of radius {@code r} at {@code at} (in the pose's space), facing the camera. Additive. */
	public static void glow(PoseStack pose, VertexConsumer vc, Vec3 at, float r, int rgb) {
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		faceCamera(pose);
		PoseStack.Pose last = pose.last();
		v(vc, last, -r, -r, 0, rgb, 0, 1, FULL, 0, 0, 1);
		v(vc, last, r, -r, 0, rgb, 1, 1, FULL, 0, 0, 1);
		v(vc, last, r, r, 0, rgb, 1, 0, FULL, 0, 0, 1);
		v(vc, last, -r, r, 0, rgb, 0, 0, FULL, 0, 0, 1);
		// and the back face (eyes render types cull)
		v(vc, last, -r, r, 0, rgb, 0, 0, FULL, 0, 0, -1);
		v(vc, last, r, r, 0, rgb, 1, 0, FULL, 0, 0, -1);
		v(vc, last, r, -r, 0, rgb, 1, 1, FULL, 0, 0, -1);
		v(vc, last, -r, -r, 0, rgb, 0, 1, FULL, 0, 0, -1);
		pose.popPose();
	}

	/** A glow disc lying in the pose's XZ plane (ground glow), radius {@code r}. Additive. */
	public static void groundGlow(PoseStack pose, VertexConsumer vc, float r, int rgb) {
		PoseStack.Pose last = pose.last();
		v(vc, last, -r, 0, -r, rgb, 0, 0, FULL, 0, 1, 0);
		v(vc, last, -r, 0, r, rgb, 0, 1, FULL, 0, 1, 0);
		v(vc, last, r, 0, r, rgb, 1, 1, FULL, 0, 1, 0);
		v(vc, last, r, 0, -r, rgb, 1, 0, FULL, 0, 1, 0);
	}

	// ------------------------------------------------------------------ ribbons

	/**
	 * A camera-facing ribbon along {@code pts} (camera-relative), {@code width.apply(s)} wide at fraction {@code s} along it,
	 * coloured {@code colour.apply(s)}. Additive (WHITE texture).
	 */
	public static void ribbon(PoseStack pose, VertexConsumer vc, List<Vec3> pts, java.util.function.DoubleUnaryOperator width,
			java.util.function.DoubleToIntFunction colour) {
		if (pts.size() < 2) {
			return;
		}
		PoseStack.Pose last = pose.last();
		Vec3 prevL = null, prevR = null;
		int prevC = 0;
		for (int i = 0; i < pts.size(); i++) {
			double s = i / (double) (pts.size() - 1);
			Vec3 p = pts.get(i);
			Vec3 tan = (i < pts.size() - 1 ? pts.get(i + 1) : p).subtract(i > 0 ? pts.get(i - 1) : p);
			Vec3 toCam = p.scale(-1.0); // the camera is at the origin of camera-relative space
			Vec3 side = tan.cross(toCam);
			side = side.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : side.normalize();
			double w = width.applyAsDouble(s) * 0.5;
			Vec3 l = p.add(side.scale(w));
			Vec3 r = p.subtract(side.scale(w));
			int c = colour.applyAsInt(s);
			if (prevL != null) {
				v(vc, last, (float) prevL.x, (float) prevL.y, (float) prevL.z, prevC, 0, 0, FULL, 0, 1, 0);
				v(vc, last, (float) prevR.x, (float) prevR.y, (float) prevR.z, prevC, 0, 1, FULL, 0, 1, 0);
				v(vc, last, (float) r.x, (float) r.y, (float) r.z, c, 1, 1, FULL, 0, 1, 0);
				v(vc, last, (float) l.x, (float) l.y, (float) l.z, c, 1, 0, FULL, 0, 1, 0);
				// the back face (eyes render types cull)
				v(vc, last, (float) l.x, (float) l.y, (float) l.z, c, 1, 0, FULL, 0, -1, 0);
				v(vc, last, (float) r.x, (float) r.y, (float) r.z, c, 1, 1, FULL, 0, -1, 0);
				v(vc, last, (float) prevR.x, (float) prevR.y, (float) prevR.z, prevC, 0, 1, FULL, 0, -1, 0);
				v(vc, last, (float) prevL.x, (float) prevL.y, (float) prevL.z, prevC, 0, 0, FULL, 0, -1, 0);
			}
			prevL = l;
			prevR = r;
			prevC = c;
		}
	}

	/**
	 * A flat band (not camera-facing) round the y axis of the pose: a ring of radius {@code r}, {@code half} tall each side
	 * of y = 0, both faces. Additive.
	 */
	public static void band(PoseStack pose, VertexConsumer vc, float r, float half, int rgb, int edge) {
		PoseStack.Pose last = pose.last();
		int segs = 28;
		for (int j = 0; j < segs; j++) {
			float a0 = Mth.TWO_PI * j / segs;
			float a1 = Mth.TWO_PI * (j + 1) / segs;
			float x0 = Mth.cos(a0) * r, z0 = Mth.sin(a0) * r, x1 = Mth.cos(a1) * r, z1 = Mth.sin(a1) * r;
			// lower half: edge colour at the rim, full at the middle; upper half the same
			for (int k = 0; k < 2; k++) {
				float ya = k == 0 ? -half : 0.0f;
				float yb = k == 0 ? 0.0f : half;
				int ca = k == 0 ? edge : rgb;
				int cb = k == 0 ? rgb : edge;
				v(vc, last, x0, ya, z0, ca, 0, 0, FULL, x0, 0, z0);
				v(vc, last, x0, yb, z0, cb, 0, 1, FULL, x0, 0, z0);
				v(vc, last, x1, yb, z1, cb, 1, 1, FULL, x1, 0, z1);
				v(vc, last, x1, ya, z1, ca, 1, 0, FULL, x1, 0, z1);
				v(vc, last, x1, ya, z1, ca, 1, 0, FULL, -x1, 0, -z1);
				v(vc, last, x1, yb, z1, cb, 1, 1, FULL, -x1, 0, -z1);
				v(vc, last, x0, yb, z0, cb, 0, 1, FULL, -x0, 0, -z0);
				v(vc, last, x0, ya, z0, ca, 0, 0, FULL, -x0, 0, -z0);
			}
		}
	}

	/** A flat ring on the ground (XZ plane) from {@code r0} to {@code r1}: {@code inner} colour inside, {@code outer} at the rim. */
	public static void annulus(PoseStack pose, VertexConsumer vc, float r0, float r1, int inner, int outer, float y) {
		PoseStack.Pose last = pose.last();
		int segs = 40;
		for (int j = 0; j < segs; j++) {
			float a0 = Mth.TWO_PI * j / segs;
			float a1 = Mth.TWO_PI * (j + 1) / segs;
			float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
			v(vc, last, c0 * r0, y, s0 * r0, inner, 0, 0, FULL, 0, 1, 0);
			v(vc, last, c0 * r1, y, s0 * r1, outer, 0, 1, FULL, 0, 1, 0);
			v(vc, last, c1 * r1, y, s1 * r1, outer, 1, 1, FULL, 0, 1, 0);
			v(vc, last, c1 * r0, y, s1 * r0, inner, 1, 0, FULL, 0, 1, 0);
			// underside
			v(vc, last, c1 * r0, y, s1 * r0, inner, 1, 0, FULL, 0, -1, 0);
			v(vc, last, c1 * r1, y, s1 * r1, outer, 1, 1, FULL, 0, -1, 0);
			v(vc, last, c0 * r1, y, s0 * r1, outer, 0, 1, FULL, 0, -1, 0);
			v(vc, last, c0 * r0, y, s0 * r0, inner, 0, 0, FULL, 0, -1, 0);
		}
	}

	// ------------------------------------------------------------------ cones

	/**
	 * A cone from the pose origin along +y, {@code r} at the base, {@code len} long: {@code base} colour at the mouth fading to
	 * {@code tip} at the point. Additive, both faces.
	 */
	public static void cone(PoseStack pose, VertexConsumer vc, float r, float len, int base, int tip) {
		PoseStack.Pose last = pose.last();
		int segs = 12;
		for (int j = 0; j < segs; j++) {
			float a0 = Mth.TWO_PI * j / segs;
			float a1 = Mth.TWO_PI * (j + 1) / segs;
			float x0 = Mth.cos(a0) * r, z0 = Mth.sin(a0) * r, x1 = Mth.cos(a1) * r, z1 = Mth.sin(a1) * r;
			v(vc, last, x0, 0, z0, base, 0, 0, FULL, x0, 0, z0);
			v(vc, last, 0, len, 0, tip, 0, 1, FULL, x0, 0, z0);
			v(vc, last, 0, len, 0, tip, 1, 1, FULL, x1, 0, z1);
			v(vc, last, x1, 0, z1, base, 1, 0, FULL, x1, 0, z1);
			v(vc, last, x1, 0, z1, base, 1, 0, FULL, -x1, 0, -z1);
			v(vc, last, 0, len, 0, tip, 1, 1, FULL, -x1, 0, -z1);
			v(vc, last, 0, len, 0, tip, 0, 1, FULL, -x0, 0, -z0);
			v(vc, last, x0, 0, z0, base, 0, 0, FULL, -x0, 0, -z0);
		}
	}

	/** Rotates the pose so +y points along {@code dir}. */
	public static void alignY(PoseStack pose, Vec3 dir) {
		Vector3f d = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
		if (d.lengthSquared() < 1.0e-8f) {
			return;
		}
		d.normalize();
		pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), d));
	}

	// ------------------------------------------------------------------ a whole fireball

	/**
	 * A ball of fire of radius {@code r} round the pose origin: a churning white-hot core, an orange molten shell, a ragged
	 * red outer flame layer and a soft glow, with flame tongues licking off it. {@code dir} (pose space, may be null) is the
	 * way it flies: the tongues stream back from it.
	 */
	public static void fireball(PoseStack pose, MultiBufferSource buffers, float r, float time, Vec3 dir, boolean meteor) {
		fireball(pose, buffers, r, time, dir, meteor, false);
	}

	/** {@code blue}: the v0.16.6 Blue Flame -- a white-blue core in blue shells, with soul-fire tongues. */
	public static void fireball(PoseStack pose, MultiBufferSource buffers, float r, float time, Vec3 dir, boolean meteor, boolean blue) {
		VertexConsumer add = additive(buffers);
		// the solid molten body, so it is a real ball and not just light
		VertexConsumer solid = solidBuffer(buffers);
		if (blue) {
			sphere(pose, solid, r * 0.62f, 0xFFF6FBFF, 0xFF9CCBFF, 0.08f, time * 1.3f, FULL);
			sphere(pose, add, r * 0.8f, 0xFF70B8FF, 0xFF0A2C9A, 0.16f, time * 1.7f + 3.0f, FULL);
			sphere(pose, add, r * 1.0f, 0xFF16388A, 0xFF02061A, 0.28f, -time * 1.1f + 7.0f, FULL);
			glow(pose, glowBuffer(buffers), Vec3.ZERO, r * 2.6f, 0xFF2A64E0);
		} else {
			sphere(pose, solid, r * 0.62f, meteor ? 0xFFFFE6A0 : 0xFFFFF4C8, meteor ? 0xFFE0501A : 0xFFFF9A2A, 0.08f, time * 1.3f, FULL);
			sphere(pose, add, r * 0.8f, 0xFFFFB040, 0xFF902000, 0.16f, time * 1.7f + 3.0f, FULL);
			sphere(pose, add, r * 1.0f, 0xFF7A2200, 0xFF140300, 0.28f, -time * 1.1f + 7.0f, FULL);
			glow(pose, glowBuffer(buffers), Vec3.ZERO, r * 2.4f, meteor ? 0xFFB04010 : 0xFF904010);
		}
		// the tongues: fire sheets streaming back along the flight
		VertexConsumer fire = fireBuffer(buffers);
		soul(blue);
		Vec3 back = dir == null || dir.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : dir.normalize().scale(-1.0);
		int n = meteor ? 8 : 5;
		for (int i = 0; i < n; i++) {
			pose.pushPose();
			alignY(pose, back);
			float spin = i * (Mth.TWO_PI / n) + time * 0.15f;
			pose.mulPose(new Quaternionf().rotationY(spin));
			pose.translate(r * 0.35f, r * 0.2f, 0.0f);
			pose.mulPose(new Quaternionf().rotationZ(-0.35f));
			float len = r * (1.6f + 0.6f * Mth.sin(time * 0.9f + i * 1.7f)) * (meteor ? 1.1f : 1.0f);
			// the fire sheet stands along +y; a tongue streams back = along +y here
			fireSheet(pose, fire, r * 1.1f, len, i + (int) time);
			pose.popPose();
		}
		soul(false);
	}

	// ------------------------------------------------------------------ blades (feathers, wing panels)

	/**
	 * v0.16.6: a flat tapered blade in the pose's space -- a feather, a wing panel -- from {@code root} to {@code tip}, bowed
	 * through {@code bow} (a quadratic curve), {@code w0} wide at the root and {@code w1} at the tip across {@code side},
	 * coloured {@code c0} at the root to {@code c1} at the tip. Additive, both faces.
	 */
	public static void blade(PoseStack pose, VertexConsumer vc, Vec3 root, Vec3 bow, Vec3 tip, Vec3 side, float w0, float w1, int c0,
			int c1) {
		PoseStack.Pose last = pose.last();
		int n = 6;
		Vec3 pl = null, pr = null;
		int pc = 0;
		for (int i = 0; i <= n; i++) {
			double u = i / (double) n;
			Vec3 p = root.scale((1 - u) * (1 - u)).add(bow.scale(2 * u * (1 - u))).add(tip.scale(u * u));
			double w = (w0 + (w1 - w0) * u) * 0.5;
			Vec3 l = p.add(side.scale(w));
			Vec3 r = p.subtract(side.scale(w));
			int c = lerpRgb(c0, c1, (float) u);
			if (pl != null) {
				v(vc, last, (float) pl.x, (float) pl.y, (float) pl.z, pc, 0, 0, FULL, 0, 1, 0);
				v(vc, last, (float) pr.x, (float) pr.y, (float) pr.z, pc, 0, 1, FULL, 0, 1, 0);
				v(vc, last, (float) r.x, (float) r.y, (float) r.z, c, 1, 1, FULL, 0, 1, 0);
				v(vc, last, (float) l.x, (float) l.y, (float) l.z, c, 1, 0, FULL, 0, 1, 0);
				v(vc, last, (float) l.x, (float) l.y, (float) l.z, c, 1, 0, FULL, 0, -1, 0);
				v(vc, last, (float) r.x, (float) r.y, (float) r.z, c, 1, 1, FULL, 0, -1, 0);
				v(vc, last, (float) pr.x, (float) pr.y, (float) pr.z, pc, 0, 1, FULL, 0, -1, 0);
				v(vc, last, (float) pl.x, (float) pl.y, (float) pl.z, pc, 0, 0, FULL, 0, -1, 0);
			}
			pl = l;
			pr = r;
			pc = c;
		}
	}
}
