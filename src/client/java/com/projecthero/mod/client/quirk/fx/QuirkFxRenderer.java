package com.projecthero.mod.client.quirk.fx;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.quirk.fx.QuirkFx;
import com.projecthero.mod.quirk.fx.QuirkFxEntity;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Quirk framework: draws every {@link QuirkFxEntity} kind from code geometry ({@link QuirkFxDraw}). Each kind runs
 * its own little timeline off the entity's age, so the server sends nothing after the spawn:
 * <ul>
 * <li>BLAST -- a white flash, a noise-wobbled fire shell that swells and cools from colour A to B, a ring of flipbook
 * fireballs, a ground shock ring racing out, a dark smoke crown rolling up;</li>
 * <li>RING -- an expanding ring (a bright leading edge and a soft trailing glow; steam puffs; a soft wash);</li>
 * <li>RIBBON -- a tapered, camera-facing strip whose head shoots out and whose tail follows (A to B), or the recorded path
 * of a followed entity (a trail);</li>
 * <li>CONE -- a flickering flame / debris cone or a straight beam along the aim;</li>
 * <li>SPIKE -- a jagged, kinked six-sided spike textured from its ground block, rising with an overshoot and sinking back,
 * a crack decal at its base;</li>
 * <li>SHARDS -- block-textured fragments that burst, hang and fall or fly back together;</li>
 * <li>DECAL -- a ground mark that fades in, holds and fades out;</li>
 * <li>FLIPBOOK -- an animated 8-frame billboard.</li>
 * </ul>
 */
public class QuirkFxRenderer extends EntityRenderer<QuirkFxEntity> {
	/** Cached ground offset (world y of the ground under the spawn point minus the effect's y). */
	private static final Map<QuirkFxEntity, Float> GROUND = new WeakHashMap<>();
	/** A trail's recorded path (world x, y, z, age). */
	private static final Map<QuirkFxEntity, ArrayDeque<float[]>> TRAILS = new WeakHashMap<>();

	public QuirkFxRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0f;
	}

	@Override
	public ResourceLocation getTextureLocation(QuirkFxEntity e) {
		return QuirkFxDraw.GLOW;
	}

	@Override
	public boolean shouldRender(QuirkFxEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
		return e.kind() != QuirkFx.Kind.NONE && super.shouldRender(e, frustum, x, y, z);
	}

	@Override
	public void render(QuirkFxEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float age = e.tickCount + partial;
		float t = Mth.clamp(age / Math.max(1, e.life()), 0f, 1f);
		if (age > e.life() + 1) {
			return;
		}
		Vec3 origin = e.getPosition(partial);
		switch (e.kind()) {
			case BLAST -> blast(e, t, age, pose, buffers, light);
			case RING -> ring(e, t, age, pose, buffers, light);
			case RIBBON -> ribbon(e, t, age, partial, origin, pose, buffers, light);
			case CONE -> cone(e, t, age, pose, buffers, light);
			case SPIKE -> spike(e, t, pose, buffers, light);
			case SHARDS -> shards(e, t, age, pose, buffers, light);
			case DECAL -> decal(e, t, age, pose, buffers, light);
			case FLIPBOOK -> flipbook(e, t, pose, buffers, light);
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- shared

	private static Quaternionf cam() {
		return Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	/** The ground under the effect, relative to it (0 if none within 4 blocks below / 1 above). */
	private static float ground(QuirkFxEntity e) {
		Float g = GROUND.get(e);
		if (g == null) {
			g = 0f;
			Level level = e.level();
			BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
			for (int dy = 1; dy >= -4; dy--) {
				bp.set(Mth.floor(e.getX()), Mth.floor(e.getY()) + dy, Mth.floor(e.getZ()));
				BlockState st = level.getBlockState(bp);
				var shape = st.getCollisionShape(level, bp);
				if (!shape.isEmpty() && level.getBlockState(bp.above()).getCollisionShape(level, bp.above()).isEmpty()) {
					g = (float) (bp.getY() + shape.max(Direction.Axis.Y) - e.getY());
					break;
				}
			}
			GROUND.put(e, g);
		}
		return g;
	}

	private static Quaternionf alignY(Vector3f dir) {
		Vector3f d = new Vector3f(dir);
		if (d.lengthSquared() < 1.0e-6f) {
			return new Quaternionf();
		}
		d.normalize();
		return new Quaternionf().rotationTo(new Vector3f(0, 1, 0), d);
	}

	private static TextureAtlasSprite sprite(int blockStateId) {
		BlockState st = blockStateId == 0 ? null : Block.stateById(blockStateId);
		if (st == null || st.isAir()) {
			return null;
		}
		return Minecraft.getInstance().getBlockRenderer().getBlockModel(st).getParticleIcon();
	}

	// ---------------------------------------------------------------- BLAST

	private static void blast(QuirkFxEntity e, float t, float age, PoseStack pose, MultiBufferSource buffers, int light) {
		float r = e.size();
		float g = e.ease().apply(t);
		int a = e.colourA(), b = e.colourB();
		long seed = e.getId() * 7919L;
		Quaternionf cam = cam();
		PoseStack.Pose last = pose.last();
		VertexConsumer glow = QuirkFxDraw.glow(buffers, QuirkFxDraw.GLOW);
		// a camera inside the ball (the caster's own blast in first person) sees it thinned out, not a white wall
		Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		float camD = (float) camPos.distanceTo(e.getPosition(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)));
		float inside = Mth.clamp(camD / Math.max(0.5f, r * 1.1f), 0.18f, 1f);
		// the flash: a white-hot burst over the first quarter
		if (t < 0.3f) {
			float f = (1f - t / 0.3f) * inside;
			QuirkFxDraw.billboard(glow, last, cam, new Vector3f(), r * (0.7f + 0.8f * (1f - f)), 0f, 0, 1, a, f, true, light);
			QuirkFxDraw.billboard(glow, last, cam, new Vector3f(), r * 1.8f, 0f, 0, 1, b, f * 0.55f, true, light);
		}
		// the fire shell (white flat colour texture: the glow texture would mottle it)
		VertexConsumer flat = QuirkFxDraw.glow(buffers, QuirkFxDraw.WHITE);
		float fade = (float) Math.pow(1f - t, 1.35f) * inside;
		int shellRgb = QuirkFxDraw.lerpRgb(t * 1.4f, a, b);
		QuirkFxDraw.shell(flat, last, r * 0.78f * g, 0.2f, seed, age * 0.5f, shellRgb, 0.55f * fade);
		QuirkFxDraw.shell(flat, last, r * 0.5f * g, 0.25f, seed + 3, age * 0.7f, QuirkFxDraw.lerpRgb(t, 0xFFFFFF, a), 0.45f * fade);
		// flipbook fireballs bursting out from the core
		VertexConsumer book = QuirkFxDraw.glow(buffers, QuirkFxDraw.BOOK_FIREBALL);
		int frame = Math.min(7, (int) (t * 8f));
		for (int i = 0; i < 8; i++) {
			Vector3f d = dir(seed, i, 0.25f);
			float dist = r * (0.35f + 0.35f * QuirkFxDraw.hash(seed, i, 9)) * g;
			Vector3f c = new Vector3f(d).mul(dist);
			float s = r * (0.35f + 0.2f * QuirkFxDraw.hash(seed, i, 4)) * (0.6f + 0.9f * t);
			QuirkFxDraw.billboard(book, last, cam, c, s, QuirkFxDraw.hash(seed, i, 2) * Mth.TWO_PI, frame / 8f, (frame + 1) / 8f,
					QuirkFxDraw.lerpRgb(0.3f, 0xFFFFFF, a), 0.9f * (1f - t * 0.6f) * inside, true, light);
		}
		// the ground shock ring
		if (!e.flag(QuirkFx.F_NO_RING)) {
			float gy = ground(e);
			if (gy > -3.5f && gy < 1.5f) {
				pose.pushPose();
				pose.translate(0, gy + 0.06f, 0);
				float rOut = r * (0.35f + 1.75f * g), rIn = rOut * 0.78f;
				float ra = (float) Math.pow(1f - t, 1.1f) * 0.95f;
				// (fetch the buffer again: asking the buffer source for another render type ended the earlier one)
				flat = QuirkFxDraw.glow(buffers, QuirkFxDraw.WHITE);
				int edge = QuirkFxDraw.lerpRgb(0.35f + 0.5f * t, a, b);
				QuirkFxDraw.ring(flat, pose.last(), rIn, rOut, 40, b, 0f, edge, ra);
				QuirkFxDraw.ring(flat, pose.last(), rOut, rOut * 1.07f, 40, edge, ra * 0.8f, b, 0f);
				pose.popPose();
			}
		}
		// the smoke crown rolling up
		if (!e.flag(QuirkFx.F_NO_SMOKE) && t > 0.12f) {
			float st = (t - 0.12f) / 0.88f;
			VertexConsumer smoke = QuirkFxDraw.matter(buffers, QuirkFxDraw.SMOKE);
			for (int i = 0; i < 10; i++) {
				Vector3f d = dir(seed + 11, i, 0.1f);
				d.y = Math.abs(d.y) * 0.6f + 0.2f;
				Vector3f c = new Vector3f(d).mul(r * (0.45f + 0.5f * st)).add(0, r * (0.15f + 0.9f * st), 0);
				float s = r * (0.35f + 0.45f * st) * (0.8f + 0.4f * QuirkFxDraw.hash(seed, i, 8));
				int grey = QuirkFxDraw.lerpRgb(st, 0x8A7466, 0x5C5856);
				QuirkFxDraw.billboard(smoke, last, cam, c, s, QuirkFxDraw.hash(seed, i, 5) * Mth.TWO_PI + age * 0.02f, 0, 1, grey,
						0.5f * smooth(st * 4f) * (1f - smooth(st)), false, light);
			}
		}
	}

	/** A seeded unit direction (y biased up by {@code up}). */
	private static Vector3f dir(long seed, int i, float up) {
		float th = QuirkFxDraw.hash(seed, i, 1) * Mth.TWO_PI;
		float y = QuirkFxDraw.hash(seed, i, 3) * 2f - 1f + up;
		y = Mth.clamp(y, -1f, 1f);
		float rr = Mth.sqrt(Math.max(0f, 1f - y * y));
		return new Vector3f(Mth.cos(th) * rr, y, Mth.sin(th) * rr);
	}

	// ---------------------------------------------------------------- RING

	private static void ring(QuirkFxEntity e, float t, float age, PoseStack pose, MultiBufferSource buffers, int light) {
		float g = e.ease().apply(t);
		float radius = e.size() * g;
		float th = Math.max(0.05f, e.size2());
		float fade = (float) Math.pow(1f - t, 1.25f);
		int a = e.colourA(), b = e.colourB();
		pose.pushPose();
		if (e.flag(QuirkFx.F_GROUND)) {
			pose.translate(0, ground(e) + 0.05f, 0);
		}
		if (e.flag(QuirkFx.F_VERTICAL)) {
			pose.mulPose(alignY(e.vec()));
		}
		PoseStack.Pose last = pose.last();
		switch (e.style()) {
			case QuirkFx.RING_STEAM -> {
				VertexConsumer smoke = QuirkFxDraw.matter(buffers, QuirkFxDraw.SMOKE);
				Quaternionf cam = cam();
				long seed = e.getId() * 131L;
				int n = Math.max(12, (int) (radius * 5f));
				for (int i = 0; i < n; i++) {
					float ang = Mth.TWO_PI * i / n + QuirkFxDraw.hash(seed, i, 1) * 0.3f;
					Vector3f c = new Vector3f(Mth.cos(ang) * radius, th * 0.4f + age * 0.02f, Mth.sin(ang) * radius);
					QuirkFxDraw.billboard(smoke, last, cam, c, th * (0.9f + 0.8f * t), ang, 0, 1, a, 0.75f * fade, false, light);
				}
			}
			case QuirkFx.RING_SOFT -> {
				VertexConsumer flat = QuirkFxDraw.glow(buffers, QuirkFxDraw.WHITE);
				QuirkFxDraw.ring(flat, last, Math.max(0f, radius - th), radius, 48, a, 0.5f * fade, b, 0f);
			}
			default -> {
				VertexConsumer flat = QuirkFxDraw.glow(buffers, QuirkFxDraw.WHITE);
				float rIn = Math.max(0f, radius - th);
				QuirkFxDraw.ring(flat, last, rIn, radius, 48, b, 0f, a, fade);
				QuirkFxDraw.ring(flat, last, radius, radius + th * 0.35f, 48, a, fade * 0.85f, b, 0f);
			}
		}
		pose.popPose();
	}

	// ---------------------------------------------------------------- RIBBON

	private static void ribbon(QuirkFxEntity e, float t, float age, float partial, Vec3 origin, PoseStack pose, MultiBufferSource buffers,
			int light) {
		Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		Vector3f camL = new Vector3f((float) (camPos.x - origin.x), (float) (camPos.y - origin.y), (float) (camPos.z - origin.z));
		int style = e.style();
		float alpha = 1f - smooth((t - 0.7f) / 0.3f);
		java.util.List<Vector3f> pts = new java.util.ArrayList<>();
		java.util.List<Float> fr = new java.util.ArrayList<>();
		if (e.flag(QuirkFx.F_TRAIL)) {
			Entity f = e.followId() == 0 ? null : e.level().getEntity(e.followId());
			ArrayDeque<float[]> path = TRAILS.computeIfAbsent(e, k -> new ArrayDeque<>());
			if (f != null) {
				Vec3 at = attach(f, e.vec(), partial);
				float[] lastP = path.peekLast();
				if (lastP == null || age - lastP[3] >= 0.5f
						|| Math.abs(lastP[0] - at.x) + Math.abs(lastP[1] - at.y) + Math.abs(lastP[2] - at.z) > 0.15f) {
					path.addLast(new float[] { (float) at.x, (float) at.y, (float) at.z, age });
				}
			}
			float span = Math.max(2f, e.size2());
			while (!path.isEmpty() && age - path.peekFirst()[3] > span) {
				path.removeFirst();
			}
			if (path.size() < 2) {
				return;
			}
			for (float[] p : path) {
				pts.add(new Vector3f((float) (p[0] - origin.x), (float) (p[1] - origin.y), (float) (p[2] - origin.z)));
				fr.add(1f - (age - p[3]) / span); // 1 = newest
			}
		} else {
			Vector3f end = new Vector3f(e.vec()).sub((float) origin.x, (float) origin.y, (float) origin.z);
			float head = e.ease().apply(Mth.clamp(t / 0.35f, 0f, 1f));
			float tail = smooth((t - 0.35f) / 0.65f) * 0.92f;
			if (head <= tail + 0.01f) {
				return;
			}
			float len = end.length();
			Vector3f d = new Vector3f(end).normalize();
			Vector3f side = new Vector3f(d).cross(0, 1, 0);
			if (side.lengthSquared() < 1.0e-4f) {
				side.set(1, 0, 0);
			}
			side.normalize();
			Vector3f up = new Vector3f(side).cross(d).normalize();
			int n = Math.max(8, Math.min(40, (int) (len * 3f)));
			for (int i = 0; i <= n; i++) {
				float f = Mth.lerp(i / (float) n, tail, head);
				Vector3f p = new Vector3f(end).mul(f);
				float belly = 4f * f * (1f - f);
				float wave = switch (style) {
					case QuirkFx.RIBBON_CLOTH -> Mth.sin(f * Mth.PI * 3f - age * 1.8f) * Math.min(1.0f, len * 0.08f) * (1f - smooth(t * 2f));
					case QuirkFx.RIBBON_SHADOW -> Mth.sin(f * Mth.PI * 2f + age * 0.6f) * Math.min(0.8f, len * 0.06f);
					case QuirkFx.RIBBON_FLAME -> (QuirkFxDraw.noise(e.getId(), i, 0, age * 0.8f) - 0.5f) * 0.25f;
					default -> 0f;
				};
				p.add(new Vector3f(side).mul(wave * belly)).add(new Vector3f(up).mul(wave * 0.4f * belly));
				pts.add(p);
				fr.add((f - tail) / Math.max(1.0e-3f, head - tail)); // 1 = the head
			}
		}
		boolean glowLook = style == QuirkFx.RIBBON_FLAME || style == QuirkFx.RIBBON_LIGHT;
		VertexConsumer vc = glowLook ? QuirkFxDraw.glow(buffers, QuirkFxDraw.RIBBON)
				: QuirkFxDraw.matter(buffers, style == QuirkFx.RIBBON_CLOTH ? QuirkFxDraw.WHITE : QuirkFxDraw.RIBBON);
		PoseStack.Pose last = pose.last();
		float w = e.size();
		for (int i = 0; i < pts.size() - 1; i++) {
			Vector3f p0 = pts.get(i), p1 = pts.get(i + 1);
			float f0 = fr.get(i), f1 = fr.get(i + 1);
			Vector3f s0 = sideOf(pts, i, camL), s1 = sideOf(pts, i + 1, camL);
			float w0 = w * width(f0, e.flag(QuirkFx.F_TRAIL)), w1 = w * width(f1, e.flag(QuirkFx.F_TRAIL));
			int c0 = QuirkFxDraw.lerpRgb(1f - f0, e.colourA(), e.colourB()), c1 = QuirkFxDraw.lerpRgb(1f - f1, e.colourA(), e.colourB());
			float a0 = alpha * Math.min(1f, f0 * 3f + 0.2f), a1 = alpha * Math.min(1f, f1 * 3f + 0.2f);
			float u0 = i / (float) (pts.size() - 1), u1 = (i + 1) / (float) (pts.size() - 1);
			float[][] q = { { p0.x - s0.x * w0, p0.y - s0.y * w0, p0.z - s0.z * w0, u0, 0 }, { p1.x - s1.x * w1, p1.y - s1.y * w1, p1.z - s1.z * w1, u1, 0 },
					{ p1.x + s1.x * w1, p1.y + s1.y * w1, p1.z + s1.z * w1, u1, 1 }, { p0.x + s0.x * w0, p0.y + s0.y * w0, p0.z + s0.z * w0, u0, 1 } };
			if (glowLook) {
				QuirkFxDraw.gquad(vc, last, q, new int[] { c0, c1, c1, c0 }, new float[] { a0, a1, a1, a0 });
			} else {
				float aa0 = style == QuirkFx.RIBBON_CLOTH ? Math.min(1f, a0 * 1.2f) : a0 * 0.85f;
				float aa1 = style == QuirkFx.RIBBON_CLOTH ? Math.min(1f, a1 * 1.2f) : a1 * 0.85f;
				int[] cs = { c0, c1, c1, c0 };
				float[] as = { aa0, aa1, aa1, aa0 };
				for (int k = 0; k < 4; k++) {
					QuirkFxDraw.mv(vc, last, q[k][0], q[k][1], q[k][2], q[k][3], q[k][4], cs[k], as[k], light, 0, 1, 0);
				}
			}
		}
	}

	/** Width profile along a ribbon ({@code f}: 0 tail .. 1 head). */
	private static float width(float f, boolean trail) {
		if (trail) {
			return 0.15f + 0.85f * f;
		}
		return 0.25f + 0.75f * (float) Math.pow(Math.sin(Math.PI * Mth.clamp(f * 0.85f + 0.1f, 0f, 1f)), 0.6);
	}

	private static Vector3f sideOf(java.util.List<Vector3f> pts, int i, Vector3f cam) {
		Vector3f a = pts.get(Math.max(0, i - 1)), b = pts.get(Math.min(pts.size() - 1, i + 1));
		Vector3f tan = new Vector3f(b).sub(a);
		Vector3f to = new Vector3f(cam).sub(pts.get(i));
		Vector3f s = tan.cross(to, new Vector3f());
		if (s.lengthSquared() < 1.0e-8f) {
			s.set(0, 1, 0);
		}
		return s.normalize().mul(0.5f);
	}

	/** Where a trail attaches to a followed entity: {@code off} is (right, up, forward) in its body frame. */
	private static Vec3 attach(Entity f, Vector3f off, float partial) {
		float yaw = f instanceof LivingEntity le ? Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot) : f.getYRot();
		double rad = Math.toRadians(yaw);
		Vec3 fwd = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
		Vec3 right = new Vec3(-Math.cos(rad), 0, -Math.sin(rad));
		return f.getPosition(partial).add(right.scale(off.x)).add(0, off.y, 0).add(fwd.scale(off.z));
	}

	// ---------------------------------------------------------------- CONE

	private static void cone(QuirkFxEntity e, float t, float age, PoseStack pose, MultiBufferSource buffers, int light) {
		Vector3f d = new Vector3f(e.vec());
		if (d.lengthSquared() < 1.0e-6f) {
			return;
		}
		d.normalize();
		int a = e.colourA(), b = e.colourB();
		long seed = e.getId() * 433L;
		pose.pushPose();
		pose.mulPose(alignY(d));
		PoseStack.Pose last = pose.last();
		Quaternionf cam = cam();
		if (e.style() == QuirkFx.CONE_BEAM) {
			float grow = smooth(t / 0.15f);
			float fade = 1f - smooth((t - 0.7f) / 0.3f);
			float len = e.size2() * grow;
			float flick = 1f + 0.08f * Mth.sin(age * 2.7f);
			VertexConsumer flat = QuirkFxDraw.glow(buffers, QuirkFxDraw.WHITE);
			tube(flat, last, e.size() * flick, len, b, 0.45f * fade);
			tube(flat, last, e.size() * 0.45f * flick, len, a, 0.9f * fade);
			VertexConsumer glow = QuirkFxDraw.glow(buffers, QuirkFxDraw.GLOW);
			QuirkFxDraw.billboard(glow, last, cam, new Vector3f(0, 0.1f, 0), e.size() * 2.4f, 0, 0, 1, a, 0.8f * fade, true, light);
			QuirkFxDraw.billboard(glow, last, cam, new Vector3f(0, len, 0), e.size() * 3.0f, 0, 0, 1, b, 0.7f * fade, true, light);
			pose.popPose();
			return;
		}
		float g = e.ease().apply(t);
		float len = e.size2() * g;
		float rad = e.size() * (0.4f + 0.6f * g);
		float fade = (float) Math.pow(1f - t, 1.2f);
		if (e.style() == QuirkFx.CONE_DEBRIS) {
			VertexConsumer smoke = QuirkFxDraw.matter(buffers, QuirkFxDraw.SMOKE);
			for (int i = 0; i < 14; i++) {
				float f = (i + 1) / 14f;
				float ang = QuirkFxDraw.hash(seed, i, 1) * Mth.TWO_PI;
				float rr = rad * f * QuirkFxDraw.hash(seed, i, 2);
				Vector3f c = new Vector3f(Mth.cos(ang) * rr, len * f, Mth.sin(ang) * rr);
				QuirkFxDraw.billboard(smoke, last, cam, c, rad * (0.25f + 0.35f * f), ang + age * 0.03f, 0, 1, QuirkFxDraw.lerpRgb(f, a, b),
						0.7f * fade, false, light);
			}
			pose.popPose();
			return;
		}
		// flame: a flickering cone sheet, white-hot at the apex
		VertexConsumer flat = QuirkFxDraw.glow(buffers, QuirkFxDraw.WHITE);
		int segs = 14, rings = 6;
		for (int k = 0; k < rings; k++) {
			float f0 = k / (float) rings, f1 = (k + 1) / (float) rings;
			for (int s = 0; s < segs; s++) {
				float t0 = Mth.TWO_PI * s / segs, t1 = Mth.TWO_PI * (s + 1) / segs;
				float[] v00 = conePt(rad, len, f0, t0, seed, k, s, age), v01 = conePt(rad, len, f0, t1, seed, k, (s + 1) % segs, age);
				float[] v10 = conePt(rad, len, f1, t0, seed, k + 1, s, age), v11 = conePt(rad, len, f1, t1, seed, k + 1, (s + 1) % segs, age);
				int c0 = QuirkFxDraw.lerpRgb(f0 * 1.3f, a, b), c1 = QuirkFxDraw.lerpRgb(f1 * 1.3f, a, b);
				float a0 = fade * (0.55f - 0.35f * f0), a1 = fade * (0.55f - 0.35f * f1) * (k == rings - 1 ? 0.2f : 1f);
				QuirkFxDraw.gquad(flat, last, new float[][] { v00, v10, v11, v01 }, new int[] { c0, c1, c1, c0 }, new float[] { a0, a1, a1, a0 });
			}
		}
		VertexConsumer book = QuirkFxDraw.glow(buffers, QuirkFxDraw.BOOK_FIREBALL);
		int frame = Math.min(7, (int) (t * 8f));
		for (int i = 0; i < 5; i++) {
			float f = 0.35f + 0.65f * i / 4f;
			float ang = QuirkFxDraw.hash(seed, i, 7) * Mth.TWO_PI;
			Vector3f c = new Vector3f(Mth.cos(ang) * rad * f * 0.4f, len * f, Mth.sin(ang) * rad * f * 0.4f);
			QuirkFxDraw.billboard(book, last, cam, c, rad * f * 0.75f, ang, frame / 8f, (frame + 1) / 8f, 0xFFFFFF, fade * 0.85f, true, light);
		}
		pose.popPose();
	}

	private static float[] conePt(float rad, float len, float f, float ang, long seed, int k, int s, float age) {
		float n = QuirkFxDraw.noise(seed, k, s, age * 0.9f);
		float r = rad * (float) Math.pow(f, 0.8) * (0.85f + 0.3f * n);
		return new float[] { Mth.cos(ang) * r, len * f * (0.95f + 0.1f * n), Mth.sin(ang) * r, 0.5f, 0.5f };
	}

	/** A glowing open cylinder along +Y. */
	private static void tube(VertexConsumer vc, PoseStack.Pose pose, float r, float len, int rgb, float a) {
		int segs = 12;
		for (int s = 0; s < segs; s++) {
			float t0 = Mth.TWO_PI * s / segs, t1 = Mth.TWO_PI * (s + 1) / segs;
			float c0 = Mth.cos(t0) * r, s0 = Mth.sin(t0) * r, c1 = Mth.cos(t1) * r, s1 = Mth.sin(t1) * r;
			QuirkFxDraw.gquad(vc, pose, new float[][] { { c0, 0, s0, 0, 0 }, { c0, len, s0, 0, 1 }, { c1, len, s1, 1, 1 }, { c1, 0, s1, 1, 0 } },
					new int[] { rgb, rgb, rgb, rgb }, new float[] { a, a, a, a });
		}
	}

	// ---------------------------------------------------------------- SPIKE

	private static void spike(QuirkFxEntity e, float t, PoseStack pose, MultiBufferSource buffers, int light) {
		float rise = e.ease().apply(Mth.clamp(t / 0.22f, 0f, 1f));
		float sink = smooth((t - 0.8f) / 0.2f);
		float h = e.size() * rise * (1f - sink);
		float base = e.size2();
		if (h < 0.02f) {
			return;
		}
		long seed = e.getId() * 2654435761L;
		pose.pushPose();
		if (e.flag(QuirkFx.F_GROUND)) {
			pose.translate(0, ground(e) - 0.15f, 0);
		}
		// the crack decal at the base (flat on the ground)
		VertexConsumer crack = QuirkFxDraw.matter(buffers, QuirkFxDraw.DECALS[QuirkFx.DECAL_CRACK]);
		float cr = base * 3.2f;
		PoseStack.Pose flatPose = pose.last();
		float ca = 0.85f * (1f - sink);
		QuirkFxDraw.mv(crack, flatPose, -cr, 0.17f, -cr, 0, 0, 0x201A16, ca, light, 0, 1, 0);
		QuirkFxDraw.mv(crack, flatPose, -cr, 0.17f, cr, 0, 1, 0x201A16, ca, light, 0, 1, 0);
		QuirkFxDraw.mv(crack, flatPose, cr, 0.17f, cr, 1, 1, 0x201A16, ca, light, 0, 1, 0);
		QuirkFxDraw.mv(crack, flatPose, cr, 0.17f, -cr, 1, 0, 0x201A16, ca, light, 0, 1, 0);

		pose.mulPose(alignY(e.vec()));
		pose.mulPose(Axis.YP.rotation(QuirkFxDraw.hash(seed, 0, 0) * Mth.TWO_PI));
		PoseStack.Pose last = pose.last();
		TextureAtlasSprite sp = sprite(e.blockStateId());
		VertexConsumer vc;
		float u0, u1, v0, v1;
		int rgb;
		if (sp != null) {
			vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
			u0 = sp.getU0();
			u1 = sp.getU1();
			v0 = sp.getV0();
			v1 = sp.getV1();
			rgb = 0xFFFFFF;
		} else {
			vc = buffers.getBuffer(RenderType.entityCutoutNoCull(QuirkFxDraw.WHITE));
			u0 = 0;
			u1 = 1;
			v0 = 0;
			v1 = 1;
			rgb = e.colourA();
		}
		int sides = 6;
		// three rings (base, a kinked middle, a jagged shoulder) and the tip
		float[][] ringR = { { 1f, 0f }, { 0.62f, 0.42f }, { 0.3f, 0.74f } };
		Vector3f kink = new Vector3f((QuirkFxDraw.hash(seed, 1, 1) - 0.5f) * base * 0.6f, 0, (QuirkFxDraw.hash(seed, 1, 2) - 0.5f) * base * 0.6f);
		Vector3f[][] ring = new Vector3f[3][sides];
		for (int k = 0; k < 3; k++) {
			for (int s = 0; s < sides; s++) {
				float ang = Mth.TWO_PI * s / sides + (k * 0.35f);
				float rr = base * ringR[k][0] * (0.75f + 0.5f * QuirkFxDraw.hash(seed, k + 2, s));
				ring[k][s] = new Vector3f(Mth.cos(ang) * rr, h * ringR[k][1], Mth.sin(ang) * rr).add(new Vector3f(kink).mul(k * 0.6f));
			}
		}
		Vector3f tip = new Vector3f(kink).mul(1.4f).add(0, h, 0);
		for (int k = 0; k < 3; k++) {
			for (int s = 0; s < sides; s++) {
				Vector3f a0 = ring[k][s], a1 = ring[k][(s + 1) % sides];
				Vector3f b0 = k < 2 ? ring[k + 1][s] : tip, b1 = k < 2 ? ring[k + 1][(s + 1) % sides] : tip;
				Vector3f n = new Vector3f(a1).sub(a0).cross(new Vector3f(b0).sub(a0)).normalize();
				float shade = 0.75f + 0.25f * (s % 2);
				int c = QuirkFxDraw.lerpRgb(1f - shade, rgb, 0x000000);
				float vb = v0 + (v1 - v0) * (k / 3f), vt = v0 + (v1 - v0) * ((k + 1) / 3f);
				float ua = Mth.lerp(s / (float) sides, u0, u1), ub = Mth.lerp((s + 1) / (float) sides, u0, u1);
				QuirkFxDraw.mv(vc, last, a0.x, a0.y, a0.z, ua, vb, c, 1f, light, n.x, n.y, n.z);
				QuirkFxDraw.mv(vc, last, a1.x, a1.y, a1.z, ub, vb, c, 1f, light, n.x, n.y, n.z);
				QuirkFxDraw.mv(vc, last, b1.x, b1.y, b1.z, ub, vt, c, 1f, light, n.x, n.y, n.z);
				QuirkFxDraw.mv(vc, last, b0.x, b0.y, b0.z, ua, vt, c, 1f, light, n.x, n.y, n.z);
			}
		}
		pose.popPose();
	}

	// ---------------------------------------------------------------- SHARDS

	private static void shards(QuirkFxEntity e, float t, float age, PoseStack pose, MultiBufferSource buffers, int light) {
		int n = Math.max(1, e.count() == 0 ? 20 : e.count());
		long seed = e.getId() * 977L;
		TextureAtlasSprite sp = sprite(e.blockStateId());
		VertexConsumer vc = sp != null ? buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS))
				: buffers.getBuffer(RenderType.entityTranslucent(QuirkFxDraw.WHITE));
		boolean back = e.flag(QuirkFx.F_REASSEMBLE);
		for (int i = 0; i < n; i++) {
			Vector3f d = dir(seed, i, 0.45f);
			float dist = e.size() * (0.5f + 0.5f * QuirkFxDraw.hash(seed, i, 6));
			Vector3f p;
			float scale = 1f;
			if (t < 0.35f) {
				p = new Vector3f(d).mul(dist * e.ease().apply(t / 0.35f));
			} else if (t < 0.6f) {
				float h = (t - 0.35f) / 0.25f;
				p = new Vector3f(d).mul(dist * (1f + 0.06f * h)).add(0, 0.1f * Mth.sin(h * Mth.PI + i), 0);
			} else if (back) {
				float k = (t - 0.6f) / 0.4f;
				float in = k * k * k;
				p = new Vector3f(d).mul(dist * 1.06f * (1f - in));
				scale = 1f - smooth((k - 0.8f) / 0.2f);
			} else {
				float k = (t - 0.6f) / 0.4f;
				p = new Vector3f(d).mul(dist * (1.06f + 0.2f * k)).add(0, -k * k * e.size() * 2.2f, 0);
				scale = 1f - smooth((k - 0.6f) / 0.4f);
			}
			if (scale <= 0.01f) {
				continue;
			}
			float s = (0.06f + 0.1f * QuirkFxDraw.hash(seed, i, 7)) * scale;
			pose.pushPose();
			pose.translate(p.x, p.y, p.z);
			Vector3f axis = dir(seed + 5, i, 0f);
			float spin = (t < 0.35f ? age * 0.5f : age * 0.15f) * (0.5f + QuirkFxDraw.hash(seed, i, 8));
			pose.mulPose(new Quaternionf().rotateAxis(spin, axis.x, axis.y, axis.z));
			if (sp != null) {
				float fu = QuirkFxDraw.hash(seed, i, 11) * 0.6f, fv = QuirkFxDraw.hash(seed, i, 12) * 0.6f;
				float du = (sp.getU1() - sp.getU0()), dv = (sp.getV1() - sp.getV0());
				QuirkFxDraw.cube(vc, pose.last(), s, sp.getU0() + du * fu, sp.getU0() + du * (fu + 0.4f), sp.getV0() + dv * fv, sp.getV0() + dv * (fv + 0.4f),
						0xFFFFFF, 1f, light);
			} else {
				QuirkFxDraw.cube(vc, pose.last(), s, 0, 1, 0, 1, QuirkFxDraw.lerpRgb(QuirkFxDraw.hash(seed, i, 9), e.colourA(), e.colourB()), 1f, light);
			}
			pose.popPose();
		}
	}

	// ---------------------------------------------------------------- DECAL

	private static void decal(QuirkFxEntity e, float t, float age, PoseStack pose, MultiBufferSource buffers, int light) {
		int style = Mth.clamp(e.style(), 0, QuirkFxDraw.DECALS.length - 1);
		float alpha = Math.min(1f, age / 4f) * (1f - smooth((t - 0.7f) / 0.3f));
		float r = e.size() * (0.85f + 0.15f * smooth(age / 6f));
		pose.pushPose();
		pose.translate(0, (e.flag(QuirkFx.F_GROUND) ? ground(e) : 0f) + 0.02f + (e.getId() % 7) * 0.002f, 0);
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		PoseStack.Pose last = pose.last();
		if (style == QuirkFx.DECAL_EMBLEM) {
			VertexConsumer vc = QuirkFxDraw.matter(buffers, QuirkFxDraw.DECALS[QuirkFx.DECAL_CRACK]);
			float cr = r * 1.45f;
			int c = e.colourB();
			float ca = alpha * 0.85f;
			QuirkFxDraw.mv(vc, last, -cr, 0, -cr, 0, 0, c, ca, light, 0, 1, 0);
			QuirkFxDraw.mv(vc, last, -cr, 0, cr, 0, 1, c, ca, light, 0, 1, 0);
			QuirkFxDraw.mv(vc, last, cr, 0, cr, 1, 1, c, ca, light, 0, 1, 0);
			QuirkFxDraw.mv(vc, last, cr, 0, -cr, 1, 0, c, ca, light, 0, 1, 0);
			float pulse = alpha * (0.75f + 0.25f * Mth.sin(age * 0.45f)) * (age < 3f ? 1.6f - 0.2f * age : 1f);
			pose.translate(0, 0.004f, 0);
			PoseStack.Pose top = pose.last();
			VertexConsumer g = QuirkFxDraw.glow(buffers, QuirkFxDraw.DECALS[style]);
			int ga = e.colourA();
			QuirkFxDraw.gquad(g, top, new float[][] { { -r, 0, -r, 0, 0 }, { -r, 0, r, 0, 1 }, { r, 0, r, 1, 1 }, { r, 0, -r, 1, 0 } },
					new int[] { ga, ga, ga, ga }, new float[] { pulse, pulse, pulse, pulse });
		} else if (style == QuirkFx.DECAL_PRINT) {
			VertexConsumer g = QuirkFxDraw.glow(buffers, QuirkFxDraw.DECALS[style]);
			QuirkFxDraw.gquad(g, last, new float[][] { { -r, 0, -r, 0, 0 }, { -r, 0, r, 0, 1 }, { r, 0, r, 1, 1 }, { r, 0, -r, 1, 0 } },
					new int[] { e.colourA(), e.colourA(), e.colourA(), e.colourA() }, new float[] { alpha, alpha, alpha, alpha });
		} else {
			VertexConsumer vc = QuirkFxDraw.matter(buffers, QuirkFxDraw.DECALS[style]);
			int c = e.colourA();
			float a = alpha * (style == QuirkFx.DECAL_POOL ? 0.9f : 0.8f);
			QuirkFxDraw.mv(vc, last, -r, 0, -r, 0, 0, c, a, light, 0, 1, 0);
			QuirkFxDraw.mv(vc, last, -r, 0, r, 0, 1, c, a, light, 0, 1, 0);
			QuirkFxDraw.mv(vc, last, r, 0, r, 1, 1, c, a, light, 0, 1, 0);
			QuirkFxDraw.mv(vc, last, r, 0, -r, 1, 0, c, a, light, 0, 1, 0);
		}
		pose.popPose();
	}

	// ---------------------------------------------------------------- FLIPBOOK

	private static void flipbook(QuirkFxEntity e, float t, PoseStack pose, MultiBufferSource buffers, int light) {
		int frame = Math.min(7, (int) (t * 8f));
		float s = e.size() * (0.8f + 0.4f * e.ease().apply(t));
		Quaternionf cam = cam();
		float roll = QuirkFxDraw.hash(e.getId(), 0, 0) * Mth.TWO_PI;
		switch (e.style()) {
			case QuirkFx.BOOK_SMOKE -> QuirkFxDraw.billboard(QuirkFxDraw.matter(buffers, QuirkFxDraw.BOOK_SMOKE), pose.last(), cam, new Vector3f(), s, roll,
					frame / 8f, (frame + 1) / 8f, e.colourA(), 0.9f, false, light);
			case QuirkFx.BOOK_INK -> QuirkFxDraw.billboard(QuirkFxDraw.matter(buffers, QuirkFxDraw.BOOK_INK), pose.last(), cam, new Vector3f(), s, roll,
					frame / 8f, (frame + 1) / 8f, e.colourA() == 0xFFFFFF ? 0xFFFFFF : e.colourA(), 0.95f, false, light);
			default -> QuirkFxDraw.billboard(QuirkFxDraw.glow(buffers, QuirkFxDraw.BOOK_FIREBALL), pose.last(), cam, new Vector3f(), s, roll,
					frame / 8f, (frame + 1) / 8f, e.colourA(), 1f, true, light);
		}
	}

	/** The camera (for kits drawing their own effects near the lens). */
	public static Camera camera() {
		return Minecraft.getInstance().gameRenderer.getMainCamera();
	}
}
