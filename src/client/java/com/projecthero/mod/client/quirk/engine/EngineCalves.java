package com.projecthero.mod.client.quirk.engine;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.quirk.engine.Engine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user, with Iida's calves as the reference): the <b>calf engines</b> -- on everyone who holds the Engine Quirk,
 * three round exhaust nozzles stick out of the BACK of each calf (v0.16.7 user: not the sides), a little toward the outer edge, like the anime: dark grey metal tubes flaring to a
 * lighter collar and a bright rim, with a dark hollow centre, swept back a little and slightly protruding (far enough to
 * stand out over trousers, a Quirk suit or armour). Drawn as real geometry in the leg's own frame, so they follow every
 * stride, kick and pose.
 * <ul>
 *   <li>The hollow glows blue as the engines work (gear and momentum); in <b>Recipro Turbo</b> the nozzles blaze -- a
 *       white-blue core, an additive halo and a flickering flame tongue -- and the body wears a blue speed aura
 *       ({@link EngineCalvesLayer}). Stalled, they smoulder a dull orange.</li>
 *   <li>Every frame the nozzles' world positions and directions are kept ({@link #capture}), so the exhaust trails, steam
 *       and flames ({@link EngineFx}) come out of these very nozzles.</li>
 * </ul>
 * Drawn by {@link EngineCalvesLayer} for every viewer and, in first person, by {@link EngineFirstPersonLegs}.
 */
public final class EngineCalves {
	/** Right-leg nozzle centres in leg-local model pixels {y down the leg, z toward the back}; the left leg mirrors x. */
	private static final float[][] AT = { { 4.9f, 1.3f }, { 7.1f, 1.3f }, { 9.3f, 1.3f } };
	/** Sideways offset of the column from the leg's centre line, toward the outer edge (the back face is z = 2; the tube
	 *  starts just inside it, so it reads as set into the calf). */
	private static final float BASE_X = 0.85f;
	private static final float LENGTH = 2.35f;
	/** Where the lighter collar starts (fraction of the length). */
	private static final float COLLAR = 0.6f;
	private static final float R_BASE = 0.86f;
	private static final float R_COLLAR = 0.96f;
	private static final float R_TIP = 1.08f;
	private static final float R_IN = 0.7f;
	private static final float DEPTH = 0.85f;
	private static final int SEG = 16;
	/** The right leg's nozzle axis (leg-local): straight out of the back of the calf, a touch outward and down. */
	private static final Vector3f AXIS_R = new Vector3f(-0.28f, 0.1f, 1f).normalize();

	private static final int TUBE = 0xFF3D4047;
	private static final int TUBE_COLLAR = 0xFF6E747D;
	private static final int RIM = 0xFFCDD2D9;
	private static final int INNER = 0xFF1A1B1E;
	private static final int HOLE = 0xFF060607;

	/** One captured frame: the six nozzle mouths in world space, their blow directions, when. */
	public record Nozzles(Vec3[] at, Vec3[] dir, long tick) {
	}

	private static final Map<Integer, Nozzles> CAPTURED = new HashMap<>();
	private static @Nullable ResourceLocation white;

	private EngineCalves() {
	}

	static void reset() {
		CAPTURED.clear();
	}

	/** Drops captures of players no longer drawn (called each client tick). */
	static void prune(long now) {
		CAPTURED.values().removeIf(n -> now - n.tick() > 40 || n.tick() > now + 5);
	}

	/** This player's six nozzle mouths (right leg 0..2, left 3..5), if they were drawn within the last few ticks. */
	public static @Nullable Nozzles nozzles(Player p) {
		Nozzles n = CAPTURED.get(p.getId());
		if (n == null || p.level() == null) {
			return null;
		}
		long age = p.level().getGameTime() - n.tick();
		return age >= 0 && age <= 3 ? n : null;
	}

	private static ResourceLocation white() {
		if (white == null) {
			NativeImage img = new NativeImage(4, 4, false);
			img.fillRect(0, 0, 4, 4, 0xFFFFFFFF);
			white = Minecraft.getInstance().getTextureManager().register("projecthero_engine_white", new DynamicTexture(img));
		}
		return white;
	}

	/** How bright the hollows burn, 0..1 (and whether it's the stall's dull orange). */
	static float glow(Player p) {
		if (Engine.stalled(p)) {
			return 0f;
		}
		if (Engine.turbo(p)) {
			return 1f;
		}
		float d = Engine.drive(p);
		if (d <= 0f) {
			return 0f;
		}
		return Mth.clamp(0.12f + 0.1f * d + 0.15f * Engine.momentum(p), 0f, 0.65f);
	}

	// ---------------------------------------------------------------- drawing

	/**
	 * Draws the nozzles on both legs of {@code model} (already posed; {@code pose} in the model's frame, as a render layer
	 * gets it) and records where they are. {@code toWorld}: the matrix taking this pose's space to world space (camera
	 * position + the pose, for a layer), or null to not capture.
	 */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, Player player, HumanoidModel<?> model, @Nullable Matrix4f toWorld,
			float partial) {
		float g = glow(player);
		boolean stalled = Engine.stalled(player);
		boolean turbo = Engine.turbo(player);
		float t = player.tickCount + partial;
		Vec3[] at = new Vec3[6];
		Vec3[] dir = new Vec3[6];
		boolean captured = toWorld != null;
		for (int leg = 0; leg < 2; leg++) {
			ModelPart part = leg == 0 ? model.rightLeg : model.leftLeg;
			if (!part.visible) {
				captured = false;
				continue;
			}
			float side = leg == 0 ? -1f : 1f;
			pose.pushPose();
			part.translateAndRotate(pose);
			pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
			PoseStack.Pose last = pose.last();
			// fetched per leg: the glow below switches the (immediate) buffer source to another render type
			VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(white()));
			Vector3f axis = new Vector3f(side < 0 ? AXIS_R.x : -AXIS_R.x, AXIS_R.y, AXIS_R.z);
			for (int i = 0; i < AT.length; i++) {
				Vector3f base = new Vector3f(side * BASE_X, AT[i][0], AT[i][1]);
				nozzle(solid, last, base, axis, light, g, stalled, t + i * 3.7f + leg * 11f);
				if (toWorld != null) {
					Vector3f mouth = new Vector3f(axis).mul(LENGTH + 0.4f).add(base);
					Vector4f w = new Vector4f(mouth.x, mouth.y, mouth.z, 1f);
					last.pose().transform(w);
					new Matrix4f(toWorld).transform(w);
					Vector4f w2 = new Vector4f(mouth.x + axis.x, mouth.y + axis.y, mouth.z + axis.z, 1f);
					last.pose().transform(w2);
					new Matrix4f(toWorld).transform(w2);
					at[leg * 3 + i] = new Vec3(w.x, w.y, w.z);
					Vec3 d = new Vec3(w2.x - w.x, w2.y - w.y, w2.z - w.z);
					dir[leg * 3 + i] = d.lengthSqr() < 1.0e-8 ? Vec3.ZERO : d.normalize();
				}
			}
			if (g > 0.25f) {
				VertexConsumer add = buffers.getBuffer(RenderType.lightning());
				for (int i = 0; i < AT.length; i++) {
					Vector3f base = new Vector3f(side * BASE_X, AT[i][0], AT[i][1]);
					halo(add, last.pose(), base, axis, g, turbo, t + i * 2.3f + leg * 5f);
				}
			}
			pose.popPose();
		}
		if (captured) {
			// a sanity check: a frame whose space wasn't what we assumed (another mod's camera) must not move the exhaust
			Vec3 feet = player.getPosition(partial);
			boolean ok = true;
			for (Vec3 v : at) {
				if (v == null || v.distanceToSqr(feet) > 6.25 * player.getScale() * player.getScale()) {
					ok = false;
					break;
				}
			}
			if (ok) {
				CAPTURED.put(player.getId(), new Nozzles(at, dir, player.level().getGameTime()));
			}
		}
	}

	/** One nozzle: the dark tube, the lighter collar, the bright rim, the dark hollow (glowing as the engines work). */
	private static void nozzle(VertexConsumer vc, PoseStack.Pose pose, Vector3f base, Vector3f axis, int light, float g, boolean stalled, float t) {
		Vector3f u = new Vector3f(axis).cross(0f, 0f, 1f);
		if (u.lengthSquared() < 1.0e-4f) {
			u = new Vector3f(axis).cross(0f, 1f, 0f);
		}
		u.normalize();
		Vector3f v = new Vector3f(axis).cross(u).normalize();
		Vector3f collar = new Vector3f(axis).mul(LENGTH * COLLAR).add(base);
		Vector3f tip = new Vector3f(axis).mul(LENGTH).add(base);
		Vector3f bottom = new Vector3f(axis).mul(LENGTH - DEPTH).add(base);
		// the hollow's colour: dark, glowing blue with the engines (white-hot core in Turbo), a dull orange while stalled
		int holeCol = HOLE;
		int holeLight = light;
		if (stalled) {
			float s = 0.18f + 0.08f * Mth.sin(t * 0.15f);
			holeCol = lerp(HOLE, 0xFFB04A1A, s);
			holeLight = LightTexture.FULL_BRIGHT;
		} else if (g > 0.01f) {
			int hot = g >= 1f ? 0xFFDDF2FF : 0xFF5AB4FF;
			holeCol = lerp(HOLE, hot, Math.min(1f, g * 1.3f));
			holeLight = LightTexture.FULL_BRIGHT;
		}
		for (int k = 0; k < SEG; k++) {
			float a0 = Mth.TWO_PI * k / SEG;
			float a1 = Mth.TWO_PI * (k + 1) / SEG;
			Vector3f d0 = ring(u, v, a0);
			Vector3f d1 = ring(u, v, a1);
			// the tube (base -> collar), then the flared collar (collar -> tip)
			band(vc, pose, base, collar, d0, d1, R_BASE, R_COLLAR, TUBE, light);
			band(vc, pose, collar, tip, d0, d1, R_COLLAR, R_TIP, TUBE_COLLAR, light);
			// the rim: an annulus at the mouth
			quad(vc, pose, p(tip, d0, R_TIP), p(tip, d1, R_TIP), p(tip, d1, R_IN), p(tip, d0, R_IN), axis, RIM, light);
			// the inside of the hollow, down to its floor
			Vector3f in0 = new Vector3f(d0).negate();
			int wall = lerp(INNER, holeCol, 0.35f);
			quadN(vc, pose, p(tip, d0, R_IN), p(tip, d1, R_IN), p(bottom, d1, R_IN), p(bottom, d0, R_IN), in0, new Vector3f(d1).negate(), wall,
					holeLight == light ? light : blendLight(light));
			// (the wall picks up a little of the glow's light, never full bright: the hollow keeps its depth)
			// the floor of the hollow (a fan of quads)
			quad(vc, pose, bottom, bottom, p(bottom, d1, R_IN), p(bottom, d0, R_IN), axis, holeCol, holeLight);
		}
	}

	/** Additive glow at the mouth: a halo, and in Turbo a flickering blue flame tongue out of the nozzle. */
	private static void halo(VertexConsumer vc, Matrix4f m, Vector3f base, Vector3f axis, float g, boolean turbo, float t) {
		Vector3f u = new Vector3f(axis).cross(0f, 0f, 1f);
		if (u.lengthSquared() < 1.0e-4f) {
			u = new Vector3f(axis).cross(0f, 1f, 0f);
		}
		u.normalize();
		Vector3f v = new Vector3f(axis).cross(u).normalize();
		Vector3f mouth = new Vector3f(axis).mul(LENGTH + 0.05f).add(base);
		float flick = 0.85f + 0.15f * Mth.sin(t * 1.7f) + 0.1f * Mth.sin(t * 4.3f);
		float r = R_TIP * (turbo ? 2.1f : 1.5f) * flick;
		int ca = (int) (255 * Math.min(1f, g) * (turbo ? 0.85f : 0.45f));
		for (int k = 0; k < SEG; k++) {
			Vector3f d0 = ring(u, v, Mth.TWO_PI * k / SEG);
			Vector3f d1 = ring(u, v, Mth.TWO_PI * (k + 1) / SEG);
			glowQuad(vc, m, mouth, mouth, p(mouth, d1, r), p(mouth, d0, r), 120, 200, 255, ca, 30, 110, 255, 0);
		}
		if (turbo) {
			// the flame tongue: a cone out of the hollow, flickering in length
			float len = (2.6f + 1.2f * Mth.sin(t * 2.1f) + 0.8f * Mth.sin(t * 5.3f)) * flick;
			Vector3f apex = new Vector3f(axis).mul(len).add(mouth);
			for (int k = 0; k < SEG; k++) {
				Vector3f d0 = ring(u, v, Mth.TWO_PI * k / SEG);
				Vector3f d1 = ring(u, v, Mth.TWO_PI * (k + 1) / SEG);
				glowQuad(vc, m, p(mouth, d0, R_IN * 1.1f), p(mouth, d1, R_IN * 1.1f), apex, apex, 170, 225, 255, 200, 40, 120, 255, 0);
			}
		}
	}

	// ---------------------------------------------------------------- geometry helpers

	private static Vector3f ring(Vector3f u, Vector3f v, float a) {
		return new Vector3f(u).mul(Mth.cos(a)).add(new Vector3f(v).mul(Mth.sin(a)));
	}

	private static Vector3f p(Vector3f c, Vector3f dir, float r) {
		return new Vector3f(dir).mul(r).add(c);
	}

	private static void band(VertexConsumer vc, PoseStack.Pose pose, Vector3f a, Vector3f b, Vector3f d0, Vector3f d1, float ra, float rb, int col,
			int light) {
		quadN(vc, pose, p(a, d0, ra), p(a, d1, ra), p(b, d1, rb), p(b, d0, rb), d0, d1, col, light);
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose pose, Vector3f a, Vector3f b, Vector3f c, Vector3f d, Vector3f n, int col, int light) {
		quadN(vc, pose, a, b, c, d, n, n, col, light);
	}

	/** A quad with per-edge normals (a, d use {@code n0}; b, c use {@code n1}) for smooth round shading. */
	private static void quadN(VertexConsumer vc, PoseStack.Pose pose, Vector3f a, Vector3f b, Vector3f c, Vector3f d, Vector3f n0, Vector3f n1,
			int col, int light) {
		vert(vc, pose, a, n0, col, light);
		vert(vc, pose, b, n1, col, light);
		vert(vc, pose, c, n1, col, light);
		vert(vc, pose, d, n0, col, light);
	}

	private static void vert(VertexConsumer vc, PoseStack.Pose pose, Vector3f p, Vector3f n, int col, int light) {
		vc.addVertex(pose, p.x, p.y, p.z).setColor(col).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(pose, n.x, n.y, n.z);
	}

	/** A two-sided additive quad: a / b in the inner colour, c / d in the outer. */
	private static void glowQuad(VertexConsumer vc, Matrix4f m, Vector3f a, Vector3f b, Vector3f c, Vector3f d, int r0, int g0, int b0, int a0, int r1,
			int g1, int b1, int a1) {
		vc.addVertex(m, a.x, a.y, a.z).setColor(r0, g0, b0, a0);
		vc.addVertex(m, b.x, b.y, b.z).setColor(r0, g0, b0, a0);
		vc.addVertex(m, c.x, c.y, c.z).setColor(r1, g1, b1, a1);
		vc.addVertex(m, d.x, d.y, d.z).setColor(r1, g1, b1, a1);
		vc.addVertex(m, d.x, d.y, d.z).setColor(r1, g1, b1, a1);
		vc.addVertex(m, c.x, c.y, c.z).setColor(r1, g1, b1, a1);
		vc.addVertex(m, b.x, b.y, b.z).setColor(r0, g0, b0, a0);
		vc.addVertex(m, a.x, a.y, a.z).setColor(r0, g0, b0, a0);
	}

	private static int blendLight(int light) {
		int block = Math.max(LightTexture.block(light), 9);
		return LightTexture.pack(block, LightTexture.sky(light));
	}

	private static int lerp(int c0, int c1, float f) {
		f = Mth.clamp(f, 0f, 1f);
		int a = (int) Mth.lerp(f, (c0 >>> 24) & 0xFF, (c1 >>> 24) & 0xFF);
		int r = (int) Mth.lerp(f, (c0 >> 16) & 0xFF, (c1 >> 16) & 0xFF);
		int gg = (int) Mth.lerp(f, (c0 >> 8) & 0xFF, (c1 >> 8) & 0xFF);
		int b = (int) Mth.lerp(f, c0 & 0xFF, c1 & 0xFF);
		return a << 24 | r << 16 | gg << 8 | b;
	}
}
