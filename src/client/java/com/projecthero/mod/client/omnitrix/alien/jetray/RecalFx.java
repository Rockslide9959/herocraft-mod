package com.projecthero.mod.client.omnitrix.alien.jetray;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.mutation.MutationRender;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienParts;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (Recalibrated Omnitrix, group B: Jetray, Big Chill, Chromastone): the rendered effects the three basic client kits
 * share -- glowing beams (an additive core inside a wider glow ribbon), breath / blast cones, ground rings, translucent shells
 * and solid crystal spikes. Everything is drawn in the world pass ({@code WorldRenderEvents.AFTER_ENTITIES}) in camera-relative
 * space, on top of {@link PyroDraw}'s primitives; particles are only accents.
 */
public final class RecalFx {
	private RecalFx() {
	}

	/** Where a beam from {@code eye} along {@code dir} stops on this client: the first block, else {@code range} out. */
	public static Vec3 traceEnd(Player p, Vec3 eye, Vec3 dir, double range) {
		Vec3 end = eye.add(dir.normalize().scale(range));
		var hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	/** The fade of a short effect {@code t} ticks into its {@code life}: a quick rise, then out. */
	public static float fade(float t, float life) {
		if (t < 0 || t > life) {
			return 0f;
		}
		float in = Math.min(1f, t / 1.5f);
		float out = Math.min(1f, (life - t) / Math.max(1f, life * 0.4f));
		return Mth.clamp(Math.min(in, out), 0f, 1f);
	}

	/**
	 * A beam from {@code a} to {@code b} (camera-relative): a soft {@code glow} ribbon {@code width} wide round a bright
	 * {@code core} a third as wide, both faded by {@code k}. Additive, so it lights itself.
	 */
	public static void beam(PoseStack pose, MultiBufferSource buffers, Vec3 a, Vec3 b, float width, int core, int glow, float k) {
		if (k <= 0.01f) {
			return;
		}
		VertexConsumer vc = PyroDraw.additive(buffers);
		List<Vec3> pts = List.of(a, a.lerp(b, 0.5), b);
		PyroDraw.ribbon(pose, vc, pts, s -> width * (0.85 + 0.15 * Math.sin(s * 9.0)), s -> PyroDraw.dim(glow, k * 0.75f));
		PyroDraw.ribbon(pose, vc, pts, s -> width * 0.35, s -> PyroDraw.dim(core, k));
		PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), a, width * 1.2f, PyroDraw.dim(glow, k));
		PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), b, width * 1.6f, PyroDraw.dim(glow, k * 0.8f));
	}

	/** A jagged beam (a zig-zag of {@code kinks} segments, re-rolled with {@code seed}): neuroshock / electric. */
	public static void bolt(PoseStack pose, MultiBufferSource buffers, Vec3 a, Vec3 b, float width, int core, int glow, float k, long seed) {
		if (k <= 0.01f) {
			return;
		}
		Vec3 d = b.subtract(a);
		Vec3 side = d.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(d).normalize();
		int kinks = Math.max(3, (int) (d.length() * 0.8));
		java.util.ArrayList<Vec3> pts = new java.util.ArrayList<>();
		java.util.Random r = new java.util.Random(seed);
		for (int i = 0; i <= kinks; i++) {
			double s = i / (double) kinks;
			double j = (i == 0 || i == kinks) ? 0 : 0.18;
			pts.add(a.add(d.scale(s)).add(side.scale((r.nextDouble() - 0.5) * j * 2)).add(up.scale((r.nextDouble() - 0.5) * j * 2)));
		}
		VertexConsumer vc = PyroDraw.additive(buffers);
		PyroDraw.ribbon(pose, vc, pts, s -> width, s -> PyroDraw.dim(glow, k * 0.7f));
		PyroDraw.ribbon(pose, vc, pts, s -> width * 0.3, s -> PyroDraw.dim(core, k));
		PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), b, width * 1.8f, PyroDraw.dim(glow, k * 0.8f));
	}

	/** A cone from {@code mouth} along {@code dir}: narrow at the mouth, {@code r} wide at {@code len}. Additive, layered. */
	public static void cone(PoseStack pose, MultiBufferSource buffers, Vec3 mouth, Vec3 dir, float r, float len, int inner, int outer, float k) {
		if (k <= 0.01f) {
			return;
		}
		VertexConsumer vc = PyroDraw.additive(buffers);
		Vec3 far = mouth.add(dir.normalize().scale(len));
		pose.pushPose();
		pose.translate(far.x, far.y, far.z);
		PyroDraw.alignY(pose, dir.scale(-1)); // +y back toward the mouth: the cone's wide base is out at the far end
		PyroDraw.cone(pose, vc, r, len, PyroDraw.dim(outer, k * 0.35f), PyroDraw.dim(outer, k));
		PyroDraw.cone(pose, vc, r * 0.55f, len * 0.95f, PyroDraw.dim(inner, k * 0.3f), PyroDraw.dim(inner, k));
		pose.popPose();
	}

	/** An upright glowing band round {@code at} (camera-relative), radius {@code r}, {@code h} tall. */
	public static void ring(PoseStack pose, MultiBufferSource buffers, Vec3 at, float r, float h, int rgb, float k) {
		if (k <= 0.01f) {
			return;
		}
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		PyroDraw.band(pose, PyroDraw.additive(buffers), r, h * 0.5f, PyroDraw.dim(rgb, k), 0xFF000000);
		PyroDraw.annulus(pose, PyroDraw.additive(buffers), Math.max(0f, r - 0.6f), r, 0xFF000000, PyroDraw.dim(rgb, k * 0.8f), 0.02f);
		pose.popPose();
	}

	/** A see-through shell (ice, crystal) round {@code box} (world coords), {@code grow} blocks out. */
	public static void shell(PoseStack pose, MultiBufferSource buffers, Vec3 cam, AABB box, float grow, int argb, int light) {
		AABB b = box.inflate(grow).move(-cam.x, -cam.y, -cam.z);
		AlienParts.box(pose, buffers.getBuffer(RenderType.entityTranslucent(MutationRender.WHITE)), (float) b.minX * 16f, (float) b.minY * 16f, (float) b.minZ * 16f,
				(float) b.maxX * 16f, (float) b.maxY * 16f, (float) b.maxZ * 16f, 0f, 0f, 1f, 1f, argb, light);
	}

	/**
	 * A solid four-sided crystal spike standing on {@code base} (camera-relative), {@code r} across, {@code h} tall, leaning
	 * along {@code tilt}: two lit faces and two shaded ones.
	 */
	public static void spike(PoseStack pose, MultiBufferSource buffers, Vec3 base, Vec3 tilt, float r, float h, int lit, int shade, float spin) {
		VertexConsumer vc = PyroDraw.solidBuffer(buffers);
		pose.pushPose();
		pose.translate(base.x, base.y, base.z);
		PyroDraw.alignY(pose, tilt);
		pose.mulPose(com.mojang.math.Axis.YP.rotation(spin));
		PoseStack.Pose last = pose.last();
		float[][] c = { { r, 0, 0 }, { 0, 0, r }, { -r, 0, 0 }, { 0, 0, -r } };
		for (int i = 0; i < 4; i++) {
			float[] a = c[i], b = c[(i + 1) % 4];
			int col = i % 2 == 0 ? lit : shade;
			float nx = (a[0] + b[0]) * 0.5f, nz = (a[2] + b[2]) * 0.5f;
			AlienParts.quad(vc, last, col, LightTexture.FULL_BRIGHT, a[0], 0, a[2], b[0], 0, b[2], 0, h, 0, 0, h, 0, nx, 0.4f, nz, 0, 0, 1, 1);
			AlienParts.quad(vc, last, col, LightTexture.FULL_BRIGHT, 0, h, 0, 0, h, 0, b[0], 0, b[2], a[0], 0, a[2], -nx, -0.4f, -nz, 0, 0, 1, 1);
		}
		pose.popPose();
	}

	/** The seven colours of a prism, red to violet. */
	public static final int[] SPECTRUM = { 0xFFFF3030, 0xFFFF9020, 0xFFFFE830, 0xFF40FF50, 0xFF30C8FF, 0xFF4060FF, 0xFFC040FF };

	/** A rainbow beam: seven thin coloured ribbons fanned round the line (offset by {@code spread}) and a white core. */
	public static void rainbow(PoseStack pose, MultiBufferSource buffers, Vec3 a, Vec3 b, float width, float spread, float k, float time) {
		if (k <= 0.01f) {
			return;
		}
		Vec3 d = b.subtract(a);
		Vec3 side = d.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(d).normalize();
		VertexConsumer vc = PyroDraw.additive(buffers);
		for (int i = 0; i < SPECTRUM.length; i++) {
			double ang = time * 0.25 + i * (Math.PI * 2 / SPECTRUM.length);
			Vec3 off = side.scale(Math.cos(ang) * spread).add(up.scale(Math.sin(ang) * spread));
			int col = SPECTRUM[i];
			PyroDraw.ribbon(pose, vc, List.of(a, a.add(d.scale(0.5)).add(off), b.add(off.scale(1.6))), s -> width * 0.45, s -> PyroDraw.dim(col, k));
		}
		PyroDraw.ribbon(pose, vc, List.of(a, b), s -> width * 0.5, s -> PyroDraw.dim(0xFFFFFFFF, k));
		PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), a, width * 1.5f, PyroDraw.dim(0xFFFFF0FF, k));
	}
}
