package com.projecthero.mod.client.andy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.andy.Andy;
import com.projecthero.mod.andy.AndyConfig;
import com.projecthero.mod.andy.AndyParts;
import com.projecthero.mod.andy.data.AndyState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * v0.17.3: Andy's two beams, drawn for every viewer from the synced flags: Dead Line (C), a thin pressurised jet of blood from
 * the fingertip, and Victor's Dead Road, a thick spiral of blood. Hidden while a beam clash draws the struggle instead.
 */
public final class AndyEffectsRenderer {
	private AndyEffectsRenderer() {
	}

	static void render(WorldRenderContext context) {
		PixelCubes.render(context);
		BloodFx.render(context);
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		renderWaves(context, pose, cam, mc.level.getGameTime() + partial);
		renderJets(context, pose, cam, mc, partial);
		for (Player player : mc.level.players()) {
			AndyState s = Andy.peek(player);
			if (s == null || !s.hasPower || (!s.flag(AndyState.FLAG_DEAD_LINE) && !s.flag(AndyState.FLAG_DEAD_ROAD))) {
				continue;
			}
			if (com.projecthero.mod.client.beamclash.BeamClashClient.hides(player)) {
				continue;
			}
			boolean road = s.flag(AndyState.FLAG_DEAD_ROAD);
			Vec3[] seg = beam(player, partial, road ? AndyConfig.DEAD_ROAD_RANGE : AndyConfig.DEAD_LINE_RANGE);
			float age = mc.level.getGameTime() - s.holdStart + partial;
			pose.pushPose();
			pose.translate(seg[0].x - cam.x, seg[0].y - cam.y, seg[0].z - cam.z);
			Matrix4f m = pose.last().pose();
			Vec3 d = seg[1].subtract(seg[0]);
			float len = (float) d.length();
			if (len > 0.05f) {
				Vector3f dir = new Vector3f((float) d.x, (float) d.y, (float) d.z).normalize();
				Vector3f a = new Vector3f(dir).cross(Math.abs(dir.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
				Vector3f b = new Vector3f(dir).cross(a).normalize();
				float grow = Mth.clamp(age / 3f, 0f, 1f);
				float reach = len * grow;
				if (!road) {
					tube(context.consumers().getBuffer(RenderType.debugQuads()), m, dir, a, b, reach, 0.035f, 6, 0.42f, 0.0f, 0.03f, 0.97f);
					tube(context.consumers().getBuffer(RenderType.lightning()), m, dir, a, b, reach, 0.085f, 6, 0.9f, 0.08f, 0.08f, 0.45f);
				} else {
					tube(context.consumers().getBuffer(RenderType.debugQuads()), m, dir, a, b, reach, 0.22f, 10, 0.5f, 0.0f, 0.04f, 0.9f);
					tube(context.consumers().getBuffer(RenderType.lightning()), m, dir, a, b, reach, 0.42f, 10, 0.95f, 0.1f, 0.1f, 0.4f);
					// the spiral: three strands winding round the core
					for (int k = 0; k < 3; k++) {
						helix(context.consumers().getBuffer(RenderType.debugQuads()), m, dir, a, b, reach, 0.5f, age * 0.6f + k * Mth.TWO_PI / 3f, 0.7f, 0.04f, 0.06f, 0.95f);
					}
				}
			}
			pose.popPose();
		}
	}

	// ---------------------------------------------------------------- the flight's jets

	/**
	 * The core of each blood jet while Andy flies: a tapered, flickering cone out of every stump (the hands', the feet', or the
	 * neck), a hot bright-red core in a darker sheath, longer and fatter while boosting. The spray round it is {@link BloodFx}.
	 */
	private static void renderJets(WorldRenderContext context, PoseStack pose, Vec3 cam, Minecraft mc, float partial) {
		float now = mc.level.getGameTime() + partial;
		for (Player player : mc.level.players()) {
			AndyState s = Andy.peek(player);
			if (s == null || !s.hasPower || !s.flag(AndyState.FLAG_PROPELLING)) {
				continue;
			}
			if (player == mc.player && mc.options.getCameraType().isFirstPerson()) {
				continue; // drawn on the first-person arms instead
			}
			boolean boost = s.flag(AndyState.FLAG_BOOST);
			float len = boost ? 1.7f : 1.05f, rad = boost ? 0.1f : 0.075f;
			if (s.headOnly(now)) {
				Vec3[] neck = AndyBlood.stump(player, AndyState.HEAD);
				cone(context, pose, cam, neck[0], neck[1].scale(-1), len * 1.1f, rad * 1.2f, now, player.getId());
				continue;
			}
			for (int part = AndyState.RIGHT_ARM; part <= AndyState.LEFT_LEG; part++) {
				if (!s.broken(part)) {
					AndyCuts.Cut cut = AndyCuts.of(s, part, now);
					cone(context, pose, cam, AndyBlood.endOf(player, part, cut != null ? cut.rows() : 12f), AndyBlood.axis(player, part), len, rad, now,
							player.getId() * 7 + part);
				} else if (!s.broken(AndyState.BODY)) {
					Vec3[] st = AndyBlood.stump(player, part);
					cone(context, pose, cam, st[0], st[1], len * 0.8f, rad, now, player.getId() * 7 + part);
				}
			}
			if (s.broken(AndyState.BODY)) {
				Vec3[] neck = AndyBlood.stump(player, AndyState.HEAD);
				cone(context, pose, cam, neck[0], neck[1].scale(-1), len, rad * 1.2f, now, player.getId());
			}
		}
	}

	private static void cone(WorldRenderContext context, PoseStack pose, Vec3 cam, Vec3 from, Vec3 dirV, float len, float rad, float now, int seed) {
		pose.pushPose();
		pose.translate(from.x - cam.x, from.y - cam.y, from.z - cam.z);
		Matrix4f m = pose.last().pose();
		Vector3f dir = new Vector3f((float) dirV.x, (float) dirV.y, (float) dirV.z);
		if (dir.lengthSquared() < 1.0e-6f) {
			pose.popPose();
			return;
		}
		dir.normalize();
		Vector3f a = new Vector3f(dir).cross(Math.abs(dir.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
		Vector3f b = new Vector3f(dir).cross(a).normalize();
		float flick = 0.85f + 0.15f * Mth.sin(now * 2.7f + seed) + 0.1f * Mth.sin(now * 6.1f + seed * 1.7f);
		float l = len * flick;
		jetCone(context.consumers().getBuffer(RenderType.lightning()), m, dir, a, b, l, rad * 1.7f, 0.55f, 0.02f, 0.03f, 0.55f);
		jetCone(context.consumers().getBuffer(RenderType.debugQuads()), m, dir, a, b, l * 0.8f, rad, 0.62f, 0.03f, 0.05f, 0.95f);
		jetCone(context.consumers().getBuffer(RenderType.lightning()), m, dir, a, b, l * 0.55f, rad * 0.45f, 1.0f, 0.25f, 0.2f, 0.8f);
		pose.popPose();
	}

	/** A cone from the origin along {@code dir}: {@code r} wide at the root, a point at {@code len}; fading toward the tip. */
	private static void jetCone(VertexConsumer vc, Matrix4f m, Vector3f dir, Vector3f a, Vector3f b, float len, float r, float cr, float cg, float cb, float ca) {
		int sides = 7;
		Vector3f tip = new Vector3f(dir).mul(len);
		for (int i = 0; i < sides; i++) {
			float t0 = i * Mth.TWO_PI / sides, t1 = (i + 1) * Mth.TWO_PI / sides;
			Vector3f p0 = new Vector3f(a).mul(Mth.cos(t0) * r).add(new Vector3f(b).mul(Mth.sin(t0) * r));
			Vector3f p1 = new Vector3f(a).mul(Mth.cos(t1) * r).add(new Vector3f(b).mul(Mth.sin(t1) * r));
			// a widening bell: the jet spreads a little before it thins out
			Vector3f q0 = new Vector3f(p0).mul(1.6f).add(new Vector3f(dir).mul(len * 0.45f));
			Vector3f q1 = new Vector3f(p1).mul(1.6f).add(new Vector3f(dir).mul(len * 0.45f));
			vc.addVertex(m, p0.x, p0.y, p0.z).setColor(cr, cg, cb, ca);
			vc.addVertex(m, p1.x, p1.y, p1.z).setColor(cr, cg, cb, ca);
			vc.addVertex(m, q1.x, q1.y, q1.z).setColor(cr, cg, cb, ca * 0.7f);
			vc.addVertex(m, q0.x, q0.y, q0.z).setColor(cr, cg, cb, ca * 0.7f);
			vc.addVertex(m, q0.x, q0.y, q0.z).setColor(cr, cg, cb, ca * 0.7f);
			vc.addVertex(m, q1.x, q1.y, q1.z).setColor(cr, cg, cb, ca * 0.7f);
			vc.addVertex(m, tip.x, tip.y, tip.z).setColor(cr, cg, cb, 0f);
			vc.addVertex(m, tip.x, tip.y, tip.z).setColor(cr, cg, cb, 0f);
		}
	}

	static void jetConePublic(VertexConsumer vc, Matrix4f m, Vector3f dir, Vector3f a, Vector3f b, float len, float r, float cr, float cg, float cb,
			float ca) {
		jetCone(vc, m, dir, a, b, len, r, cr, cg, cb, ca);
	}

	/** From the client tick: the blood the waves and the beams throw about. */
	static void tickBlood(long now) {
		for (Waves w : WAVES) {
			float age = now - w.start() - 5f;
			if (age < 0f) {
				continue;
			}
			float d = 1.2f + age * 1.1f;
			if (d > AndyConfig.GREAT_CRESCENT_RANGE) {
				continue;
			}
			Vec3 front = w.from().add(w.dir().scale(d));
			Vec3 side = new Vec3(-w.dir().z, 0, w.dir().x);
			for (int k = 0; k < 4; k++) {
				double sd = (Math.random() - 0.5) * 2.6;
				Vec3 at = front.add(side.scale(sd)).add(0, 1.6 + Math.random() * 1.2, 0);
				BloodFx.spurt(at, w.dir().add(0, 0.8, 0), 2, 0.25, 0.5, Vec3.ZERO);
			}
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		for (Player player : mc.level.players()) {
			AndyState s = Andy.peek(player);
			if (s == null || !s.hasPower || (!s.flag(AndyState.FLAG_DEAD_LINE) && !s.flag(AndyState.FLAG_DEAD_ROAD))) {
				continue;
			}
			boolean road = s.flag(AndyState.FLAG_DEAD_ROAD);
			Vec3[] seg = beam(player, 1f, road ? AndyConfig.DEAD_ROAD_RANGE : AndyConfig.DEAD_LINE_RANGE);
			Vec3 back = seg[0].subtract(seg[1]);
			BloodFx.spurt(seg[1], back, road ? 6 : 3, road ? 0.3 : 0.18, 0.7, Vec3.ZERO);
			if (now % 3 == 0) {
				BloodFx.mist(seg[1], 1, road ? 0.5f : 0.25f, Vec3.ZERO);
			}
		}
	}

	// ---------------------------------------------------------------- Great Deep-Crimson Crescent Moon

	/** One set of blood waves: where they started, which way they run, when. */
	private record Waves(Vec3 from, Vec3 dir, long start) {
	}

	private static final java.util.List<Waves> WAVES = new java.util.ArrayList<>();
	private static final java.util.Map<java.util.UUID, Long> SEEN_ANIM = new java.util.HashMap<>();

	static void reset() {
		WAVES.clear();
		SEEN_ANIM.clear();
	}

	/** From the client tick: a Great Crescent that just started launches its waves from where he stood. */
	static void watch(Player player, AndyState s, long now) {
		Long seen = SEEN_ANIM.put(player.getUUID(), s.animStart);
		if (seen != null && seen != s.animStart && s.animId == AndyState.ANIM_GREAT_CRESCENT && now - s.animStart < 10) {
			Vec3 look = Vec3.directionFromRotation(0f, player.getYRot());
			WAVES.add(new Waves(player.position(), look.normalize(), s.animStart));
		}
		WAVES.removeIf(w -> now - w.start() > 30);
	}

	private static void renderWaves(WorldRenderContext context, PoseStack pose, Vec3 cam, float now) {
		for (Waves w : WAVES) {
			float age = now - w.start() - 5f;
			if (age < 0f) {
				continue;
			}
			for (int k = 0; k < 3; k++) {
				// three crests, the first the biggest, running out along the line
				float d = 1.2f + (age - k * 2.2f) * 1.1f;
				if (d < 0.8f || d > AndyConfig.GREAT_CRESCENT_RANGE) {
					continue;
				}
				float fade = Mth.clamp((float) ((AndyConfig.GREAT_CRESCENT_RANGE - d) / 4.0), 0f, 1f) * Mth.clamp(d / 1.5f, 0f, 1f);
				float scale = 1f - k * 0.22f;
				Vec3 front = w.from().add(w.dir().scale(d));
				pose.pushPose();
				pose.translate(front.x - cam.x, front.y - cam.y, front.z - cam.z);
				wave(context.consumers().getBuffer(RenderType.debugQuads()), pose.last().pose(), w.dir(), (1.5f + d * 0.06f) * scale,
						(2.8f + d * 0.08f) * scale, fade, now * 0.5f + k);
				pose.popPose();
			}
		}
	}

	/** A tall crescent wall of blood, curling back at its ends and lower there; dark at the root, bright at the crest. */
	private static void wave(VertexConsumer vc, Matrix4f m, Vec3 dir, float width, float height, float alpha, float ripple) {
		float dx = (float) dir.x, dz = (float) dir.z;
		float sx = -dz, sz = dx;
		int n = 16, rows = 5;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < rows; j++) {
				float[][] p = new float[4][];
				int c = 0;
				for (int[] q : new int[][] { { i, j }, { i + 1, j }, { i + 1, j + 1 }, { i, j + 1 } }) {
					float s = q[0] / (float) n * 2f - 1f;
					float v = q[1] / (float) rows;
					float back = s * s * 1.1f - v * v * 0.6f; // the crest leans forward over the root
					float h = v * height * (1f - 0.5f * s * s) + Mth.sin(ripple + s * 5f) * 0.06f;
					p[c++] = new float[] { sx * s * width - dx * back, h, sz * s * width - dz * back, v };
				}
				for (int side = 0; side < 2; side++) {
					for (int e = 0; e < 4; e++) {
						float[] v = p[side == 0 ? e : 3 - e];
						float r = 0.45f + 0.5f * v[3], g = 0.01f + 0.12f * v[3] * v[3], b = 0.03f + 0.1f * v[3] * v[3];
						vc.addVertex(m, v[0], v[1], v[2]).setColor(r, g, b, alpha * (0.92f - 0.35f * v[3]));
					}
				}
			}
		}
	}

	/** Where the beam runs this frame: the fingertip to the first block or creature (as the server works it out). */
	static Vec3[] beam(Player p, float partial, double range) {
		Vec3 pos = p.getPosition(partial);
		Vec3 eye = pos.add(0, p.getEyeHeight(), 0);
		Vec3 look = p.getViewVector(partial);
		Vec3 r = AndyParts.right(p);
		double side = Andy.mainArm(p) == AndyState.RIGHT_ARM ? 1 : -1;
		Vec3 from = eye.add(r.scale(0.32 * side)).add(0, -0.32, 0).add(look.scale(0.55));
		Vec3 far = eye.add(look.scale(range));
		BlockHitResult bhr = p.level().clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p, from, end, new AABB(from, end).inflate(1.0),
				e -> e instanceof LivingEntity && e.isPickable() && !(e instanceof com.projecthero.mod.andy.entity.AndyCloneEntity), from.distanceToSqr(end));
		if (ehr != null) {
			end = ehr.getLocation();
		}
		return new Vec3[] { from, end };
	}

	private static void tube(VertexConsumer vc, Matrix4f m, Vector3f dir, Vector3f a, Vector3f b, float len, float r, int sides, float cr, float cg,
			float cb, float ca) {
		for (int i = 0; i < sides; i++) {
			float t0 = i * Mth.TWO_PI / sides, t1 = (i + 1) * Mth.TWO_PI / sides;
			Vector3f p0 = new Vector3f(a).mul(Mth.cos(t0) * r).add(new Vector3f(b).mul(Mth.sin(t0) * r));
			Vector3f p1 = new Vector3f(a).mul(Mth.cos(t1) * r).add(new Vector3f(b).mul(Mth.sin(t1) * r));
			Vector3f e = new Vector3f(dir).mul(len);
			quad(vc, m, p0, p1, new Vector3f(p1).add(e), new Vector3f(p0).add(e), cr, cg, cb, ca);
		}
	}

	private static void helix(VertexConsumer vc, Matrix4f m, Vector3f dir, Vector3f a, Vector3f b, float len, float r, float phase, float cr, float cg,
			float cb, float ca) {
		int steps = Math.max(4, (int) (len * 6));
		float w = 0.07f;
		for (int i = 0; i < steps; i++) {
			float s0 = i / (float) steps * len, s1 = (i + 1) / (float) steps * len;
			float t0 = phase + s0 * 1.6f, t1 = phase + s1 * 1.6f;
			Vector3f p0 = new Vector3f(a).mul(Mth.cos(t0) * r).add(new Vector3f(b).mul(Mth.sin(t0) * r)).add(new Vector3f(dir).mul(s0));
			Vector3f p1 = new Vector3f(a).mul(Mth.cos(t1) * r).add(new Vector3f(b).mul(Mth.sin(t1) * r)).add(new Vector3f(dir).mul(s1));
			Vector3f off0 = new Vector3f(a).mul(Mth.cos(t0)).add(new Vector3f(b).mul(Mth.sin(t0))).mul(w);
			Vector3f off1 = new Vector3f(a).mul(Mth.cos(t1)).add(new Vector3f(b).mul(Mth.sin(t1))).mul(w);
			quad(vc, m, new Vector3f(p0).sub(off0), new Vector3f(p1).sub(off1), new Vector3f(p1).add(off1), new Vector3f(p0).add(off0), cr, cg, cb, ca);
		}
	}

	private static void quad(VertexConsumer vc, Matrix4f m, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float r, float g, float b, float a) {
		vc.addVertex(m, p0.x, p0.y, p0.z).setColor(r, g, b, a);
		vc.addVertex(m, p1.x, p1.y, p1.z).setColor(r, g, b, a);
		vc.addVertex(m, p2.x, p2.y, p2.z).setColor(r, g, b, a);
		vc.addVertex(m, p3.x, p3.y, p3.z).setColor(r, g, b, a);
		vc.addVertex(m, p3.x, p3.y, p3.z).setColor(r, g, b, a);
		vc.addVertex(m, p2.x, p2.y, p2.z).setColor(r, g, b, a);
		vc.addVertex(m, p1.x, p1.y, p1.z).setColor(r, g, b, a);
		vc.addVertex(m, p0.x, p0.y, p0.z).setColor(r, g, b, a);
	}
}
