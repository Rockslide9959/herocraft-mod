package com.projecthero.mod.client.andy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * v0.17.3: the loose pixels of Andy's body -- one little cube per skin texel of a broken part, each textured with exactly
 * that texel of the owner's skin, flung out, falling, bouncing and spinning, then shrinking away after a second or two.
 * A client-side particle system (one list, one buffer per skin texture), not entities; capped at {@link #MAX} cubes.
 * Fed by {@link AndyClient} when a synced break arrives, and by a launched part or clone as it disappears.
 */
public final class PixelCubes {
	public static final int MAX = 9000;

	private static final class Cube {
		ResourceLocation tex;
		float u0, v0, u1, v1;
		double x, y, z, px, py, pz;
		double vx, vy, vz;
		float ax, ay, az; // spin axis
		float angle, prevAngle, spin;
		float size;
		int age, life;
		int argb;
		int light;
		boolean resting;
	}

	private static final List<Cube> CUBES = new ArrayList<>();

	private PixelCubes() {
	}

	public static void reset() {
		CUBES.clear();
	}

	public static int count() {
		return CUBES.size();
	}

	/**
	 * Bursts one part: every visible texel of {@code part} becomes a cube at its place in the world ({@code partToWorld} maps
	 * the part's own space, in blocks, to world coordinates) and flies out from the part's centre.
	 *
	 * @param push the hit's direction and strength (added to every cube), may be zero
	 * @param tint ARGB multiplied into the skin (0xFFFFFFFF for none)
	 * @param power 1 for a normal break; more for the Undead shatter
	 */
	public static void burst(ResourceLocation tex, boolean slim, int part, Matrix4f partToWorld, Vec3 push, int tint, float power) {
		List<AndyTexels.Texel> texels = AndyTexels.of(part, slim);
		float[] b = AndyTexels.box(part, slim);
		Vector3f centre = partToWorld.transformPosition(new Vector3f((b[0] + b[3] * 0.5f) / 16f, (b[1] + b[4] * 0.5f) / 16f, (b[2] + b[5] * 0.5f) / 16f));
		RandomSource rnd = RandomSource.create();
		ClientLevel level = Minecraft.getInstance().level;
		float scale = partToWorld.getScale(new Vector3f()).x;
		for (AndyTexels.Texel t : texels) {
			if (CUBES.size() >= MAX) {
				break;
			}
			if (!SkinPixels.shows(tex, t.u, t.v)) {
				continue;
			}
			// the cube sits just inside the skin surface, so the burst starts as the exact shape of the part
			float inset = 0.5f;
			Vector3f p = partToWorld.transformPosition(new Vector3f((t.x - t.nx * inset) / 16f, (t.y - t.ny * inset) / 16f, (t.z - t.nz * inset) / 16f));
			Cube c = new Cube();
			c.tex = tex;
			c.u0 = (t.u + 0.05f) / 64f;
			c.v0 = (t.v + 0.05f) / 64f;
			c.u1 = (t.u + 0.95f) / 64f;
			c.v1 = (t.v + 0.95f) / 64f;
			c.x = c.px = p.x;
			c.y = c.py = p.y;
			c.z = c.pz = p.z;
			Vector3f out = new Vector3f(p.x - centre.x, p.y - centre.y, p.z - centre.z);
			if (out.lengthSquared() < 1.0e-6f) {
				out.set(0, 1, 0);
			}
			out.normalize();
			double speed = (0.03 + rnd.nextDouble() * 0.07) * power;
			c.vx = out.x * speed + (rnd.nextDouble() - 0.5) * 0.03 * power + push.x;
			c.vy = out.y * speed * 0.6 + 0.04 + rnd.nextDouble() * 0.09 * power + push.y;
			c.vz = out.z * speed + (rnd.nextDouble() - 0.5) * 0.03 * power + push.z;
			Vector3f axis = new Vector3f(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f);
			if (axis.lengthSquared() < 1.0e-4f) {
				axis.set(0, 1, 0);
			}
			axis.normalize();
			c.ax = axis.x;
			c.ay = axis.y;
			c.az = axis.z;
			c.spin = (rnd.nextFloat() - 0.5f) * 0.9f;
			c.size = 1f / 16f * scale; // exactly one skin texel
			c.life = 28 + rnd.nextInt(22);
			c.argb = tint;
			c.light = level != null ? LevelRenderer.getLightColor(level, BlockPos.containing(c.x, c.y, c.z)) : 0xF000F0;
			CUBES.add(c);
		}
	}

	/** A few cubes of one texel (a spent finger). */
	public static void sprinkle(ResourceLocation tex, int u, int v, Vec3 at, int count, int tint) {
		RandomSource rnd = RandomSource.create();
		ClientLevel level = Minecraft.getInstance().level;
		for (int i = 0; i < count && CUBES.size() < MAX; i++) {
			Cube c = new Cube();
			c.tex = tex;
			int du = i % 2, dv = (i / 2) % 2;
			c.u0 = (u + du * 0.5f + 0.05f) / 64f;
			c.v0 = (v + dv * 0.5f + 0.05f) / 64f;
			c.u1 = c.u0 + 0.4f / 64f;
			c.v1 = c.v0 + 0.4f / 64f;
			c.x = c.px = at.x;
			c.y = c.py = at.y;
			c.z = c.pz = at.z;
			c.vx = (rnd.nextDouble() - 0.5) * 0.2;
			c.vy = rnd.nextDouble() * 0.18;
			c.vz = (rnd.nextDouble() - 0.5) * 0.2;
			c.ax = 0.6f;
			c.ay = 0.8f;
			c.az = 0f;
			c.spin = (rnd.nextFloat() - 0.5f);
			c.size = 0.035f;
			c.life = 20 + rnd.nextInt(10);
			c.argb = tint;
			c.light = level != null ? LevelRenderer.getLightColor(level, BlockPos.containing(at)) : 0xF000F0;
			CUBES.add(c);
		}
	}

	public static void tick() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			CUBES.clear();
			return;
		}
		Iterator<Cube> it = CUBES.iterator();
		while (it.hasNext()) {
			Cube c = it.next();
			if (++c.age > c.life) {
				it.remove();
				continue;
			}
			c.px = c.x;
			c.py = c.y;
			c.pz = c.z;
			c.prevAngle = c.angle;
			if (c.resting) {
				continue;
			}
			c.vy -= 0.045;
			c.vx *= 0.985;
			c.vy *= 0.985;
			c.vz *= 0.985;
			double nx = c.x + c.vx, ny = c.y + c.vy, nz = c.z + c.vz;
			double half = c.size * 0.5;
			BlockPos below = BlockPos.containing(nx, ny - half, nz);
			BlockState st = level.getBlockState(below);
			if (!st.isAir()) {
				VoxelShape shape = st.getCollisionShape(level, below);
				if (!shape.isEmpty()) {
					double top = below.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
					if (c.y - half >= top - 0.05 && ny - half < top) {
						// the floor: bounce, lose spin and speed
						ny = top + half;
						c.vy = -c.vy * 0.38;
						c.vx *= 0.45;
						c.vz *= 0.45;
						c.spin *= 0.55;
						if (Math.abs(c.vy) < 0.035 && c.vx * c.vx + c.vz * c.vz < 0.0004) {
							c.vy = 0;
							c.resting = true;
							// lie flat-ish: settle the spin
							c.angle = Math.round(c.angle / Mth.HALF_PI) * Mth.HALF_PI;
						}
					} else if (ny - half < top) {
						// a wall: knock back the horizontal motion
						nx = c.x;
						nz = c.z;
						c.vx = -c.vx * 0.3;
						c.vz = -c.vz * 0.3;
					}
				}
			}
			c.x = nx;
			c.y = ny;
			c.z = nz;
			c.angle += c.spin;
			if (c.age % 5 == 0) {
				c.light = LevelRenderer.getLightColor(level, BlockPos.containing(c.x, c.y, c.z));
			}
		}
	}

	public static void render(WorldRenderContext context) {
		if (CUBES.isEmpty() || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		PoseStack.Pose last = pose.last();
		// one texture at a time: asking the buffer source for another type ends the last one
		Map<ResourceLocation, List<Cube>> groups = new HashMap<>();
		for (Cube c : CUBES) {
			groups.computeIfAbsent(c.tex, k -> new ArrayList<>()).add(c);
		}
		Quaternionf q = new Quaternionf();
		Vector3f[] corners = new Vector3f[8];
		for (int i = 0; i < 8; i++) {
			corners[i] = new Vector3f();
		}
		Vector3f n = new Vector3f();
		for (Map.Entry<ResourceLocation, List<Cube>> group : groups.entrySet()) {
		VertexConsumer vc = context.consumers().getBuffer(RenderType.entityCutoutNoCull(group.getKey()));
		for (Cube c : group.getValue()) {
			float x = (float) (Mth.lerp(partial, c.px, c.x) - cam.x);
			float y = (float) (Mth.lerp(partial, c.py, c.y) - cam.y);
			float z = (float) (Mth.lerp(partial, c.pz, c.z) - cam.z);
			float age = c.age + partial;
			float shrink = Mth.clamp((c.life - age) / 12f, 0f, 1f);
			float pop = Mth.clamp(age / 2f, 0.85f, 1f);
			float h = c.size * 0.5f * shrink * pop;
			if (h <= 0.0005f) {
				continue;
			}
			q.setAngleAxis(Mth.lerp(partial, c.prevAngle, c.angle), c.ax, c.ay, c.az);
			for (int i = 0; i < 8; i++) {
				corners[i].set((i & 1) == 0 ? -h : h, (i & 2) == 0 ? -h : h, (i & 4) == 0 ? -h : h).rotate(q).add(x, y, z);
			}
			int a = (c.argb >>> 24) & 0xFF, r = (c.argb >>> 16) & 0xFF, g = (c.argb >>> 8) & 0xFF, b = c.argb & 0xFF;
			// six faces: -x, +x, -y, +y, -z, +z
			face(vc, last, corners, 0, 4, 6, 2, n.set(-1, 0, 0).rotate(q), c, r, g, b, a);
			face(vc, last, corners, 1, 3, 7, 5, n.set(1, 0, 0).rotate(q), c, r, g, b, a);
			face(vc, last, corners, 0, 1, 5, 4, n.set(0, -1, 0).rotate(q), c, r, g, b, a);
			face(vc, last, corners, 2, 6, 7, 3, n.set(0, 1, 0).rotate(q), c, r, g, b, a);
			face(vc, last, corners, 0, 2, 3, 1, n.set(0, 0, -1).rotate(q), c, r, g, b, a);
			face(vc, last, corners, 4, 5, 7, 6, n.set(0, 0, 1).rotate(q), c, r, g, b, a);
		}
		}
	}

	private static void face(VertexConsumer vc, PoseStack.Pose pose, Vector3f[] p, int i0, int i1, int i2, int i3, Vector3f n, Cube c, int r, int g,
			int b, int a) {
		Matrix4f m = pose.pose();
		vc.addVertex(m, p[i0].x, p[i0].y, p[i0].z).setColor(r, g, b, a).setUv(c.u0, c.v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(c.light)
				.setNormal(pose, n.x, n.y, n.z);
		vc.addVertex(m, p[i1].x, p[i1].y, p[i1].z).setColor(r, g, b, a).setUv(c.u1, c.v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(c.light)
				.setNormal(pose, n.x, n.y, n.z);
		vc.addVertex(m, p[i2].x, p[i2].y, p[i2].z).setColor(r, g, b, a).setUv(c.u1, c.v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(c.light)
				.setNormal(pose, n.x, n.y, n.z);
		vc.addVertex(m, p[i3].x, p[i3].y, p[i3].z).setColor(r, g, b, a).setUv(c.u0, c.v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(c.light)
				.setNormal(pose, n.x, n.y, n.z);
	}
}
