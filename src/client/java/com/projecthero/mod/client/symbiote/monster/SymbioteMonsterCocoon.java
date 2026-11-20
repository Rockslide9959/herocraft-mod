package com.projecthero.mod.client.symbiote.monster;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.symbiote.SymbioteSuitTint;
import com.projecthero.mod.symbiote.monster.SymbioteMonster;
import com.projecthero.mod.symbiote.monster.SymbioteMonsterState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Symbiote Monster form: the transformation and the melt, drawn by every client from the synced {@link SymbioteMonsterState}
 * (no packets) in world space after the entities, in the host's goo colour (the tendril texture through {@code SymbioteSuitTint.goo}).
 *
 * <pre>
 *   COCOON (2.5 s)
 *     0-16   twelve tendrils erupt from the ground in a ring round the feet and spiral up round the host -- turning faster and faster
 *     6-28   a living shell weaves itself shut from the ground up between them (a lumpy surface of revolution, ragged top edge),
 *            criss-crossed by thinner strands; a pool of goo spreads under it
 *     14-42  the cocoon swells from 3 to 7.2 blocks tall (the hitbox grows with it, server side)
 *     30-46  four heartbeats, quickening, each one swelling the shell
 *     46-50  it strains and splits
 *   MONSTER 0-16   the burst: torn strands whip outward and drop, the pool shrinks away
 *   MELT    0-24   the monster melts into a spreading pool, goo dripping off it; 12-30 tendrils rise from the pool and pull the host
 *                  back up out of it, then the pool is gone
 * </pre>
 */
public final class SymbioteMonsterCocoon {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/symbiote_tendril.png");
	private static final int STRANDS = 12;

	private SymbioteMonsterCocoon() {
	}

	public static void initialize() {
		WorldRenderEvents.AFTER_ENTITIES.register(SymbioteMonsterCocoon::render);
	}

	private static final class Draw {
		PoseStack pose;
		MultiBufferSource buffers;
		Vec3 cam;
		Level level;
		VertexConsumer vc;

		int light(Vec3 at) {
			return LevelRenderer.getLightColor(level, BlockPos.containing(at));
		}

		void tube(Vec3[] pts, float[] radii, int sides, int rgb) {
			Vec3[] local = new Vec3[pts.length];
			for (int i = 0; i < pts.length; i++) {
				local[i] = pts[i].subtract(cam);
			}
			MonsterMesh.tube(vc, pose.last(), light(pts[pts.length / 2]), rgb, local, radii, sides, 0.3f);
		}

		void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0, float v0, float u1, float v1, int light, int rgb) {
			MonsterMesh.quad(vc, pose.last(), light, rgb, a.subtract(cam), b.subtract(cam), c.subtract(cam), d.subtract(cam), u0, v0, u1, v1);
		}
	}

	private static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
		Draw d = null;
		for (Player p : mc.level.players()) {
			SymbioteMonsterState s = SymbioteMonster.state(p);
			if (!s.inForm() || p.distanceToSqr(ctx.camera().getPosition()) > 128 * 128) {
				continue;
			}
			if (p.isInvisible() && mc.player != null && p.isInvisibleTo(mc.player)) {
				continue;
			}
			float age = mc.level.getGameTime() - s.phaseStart() + partial;
			if (s.phase() == SymbioteMonsterState.MONSTER && age > 18) {
				continue;
			}
			if (d == null) {
				d = new Draw();
				d.pose = ctx.matrixStack();
				d.buffers = ctx.consumers();
				d.cam = ctx.camera().getPosition();
				d.level = mc.level;
			}
			d.vc = d.buffers.getBuffer(RenderType.entityCutoutNoCull(SymbioteSuitTint.goo(p, TEXTURE)));
			Vec3 feet = p.getPosition(partial);
			int seed = p.getId() * 31;
			switch (s.phase()) {
				case SymbioteMonsterState.COCOON -> cocoon(d, feet, age, seed);
				case SymbioteMonsterState.MONSTER -> burst(d, feet, age, seed);
				case SymbioteMonsterState.MELT -> melt(d, feet, age, seed, p);
				default -> {
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ shape helpers

	private static float smooth(float a, float b, float x) {
		float t = Mth.clamp((x - a) / (b - a), 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	private static float hash(int i) {
		int h = i * 374761393 + 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0xFFFFFF;
	}

	/** The heartbeats: a sharp swell at each beat, quickening. */
	private static float pulse(float age) {
		float p = 0f;
		for (float beat : new float[] { 30f, 37f, 42f, 46f }) {
			float k = 1f - Math.abs(age - beat) / 2.6f;
			if (k > 0) {
				p = Math.max(p, k * k);
			}
		}
		return p;
	}

	private static float height(float age) {
		return Mth.lerp(smooth(14f, 42f, age), 3.0f, 7.2f);
	}

	private static float maxRadius(float age) {
		return Mth.lerp(smooth(14f, 42f, age), 1.05f, 2.15f);
	}

	/** The cocoon's radius at height fraction {@code t} round angle {@code a}: a lumpy egg, wider low down, closing at the top. */
	private static float radius(float age, float t, double a, float time) {
		float prof = (float) Math.pow(Math.max(0.0, Math.sin(Math.PI * (0.04 + 0.92 * Mth.clamp(t, 0f, 1f)))), 0.6) * (1f + 0.14f * (1f - t));
		float lump = 1f + 0.06f * (float) Math.sin(a * 3 + t * 7 + time * 0.08) + 0.04f * (float) Math.sin(a * 7 - t * 11 + time * 0.13);
		float beat = 1f + 0.085f * pulse(age) * (0.5f + t);
		float strain = 1f + 0.12f * smooth(45f, 50f, age);
		return maxRadius(age) * prof * lump * beat * strain;
	}

	private static float spin(float age) {
		return 0.0036f * age * age;
	}

	// ------------------------------------------------------------------------------------------------ COCOON

	private static void cocoon(Draw d, Vec3 feet, float age, int seed) {
		float h = height(age);
		float time = age;
		pool(d, feet, maxRadius(age) + 0.7f * smooth(0f, 10f, age), age);
		// the shell, woven shut from the ground up
		float cover = smooth(6f, 28f, age);
		if (cover > 0.02f) {
			int rings = 20, segs = 22;
			int light = d.light(feet.add(0, 1, 0));
			Vec3[][] grid = new Vec3[rings + 1][segs + 1];
			float[] topCut = new float[segs + 1];
			for (int k = 0; k <= segs; k++) {
				topCut[k] = cover * (1.03f + 0.08f * (hash(seed + k % segs) - 0.5f)) + (cover > 0.97f ? 0.1f : 0f);
			}
			for (int i = 0; i <= rings; i++) {
				float t = i / (float) rings;
				for (int k = 0; k <= segs; k++) {
					double a = Math.PI * 2 * k / segs + spin(age) * 0.15;
					float tt = Math.min(t, topCut[k]);
					float r = radius(age, tt, a, time);
					if (tt >= 0.995f) {
						r *= 0.2f;
					}
					grid[i][k] = feet.add(Math.cos(a) * r, tt * h, Math.sin(a) * r);
				}
			}
			for (int i = 0; i < rings; i++) {
				for (int k = 0; k < segs; k++) {
					float t = i / (float) rings;
					if (t > topCut[k] && t > topCut[k + 1]) {
						continue;
					}
					float u0 = (k % 4) * 0.25f, v0 = (i % 4) * 0.25f;
					d.quad(grid[i][k], grid[i][k + 1], grid[i + 1][k + 1], grid[i + 1][k], u0, v0, u0 + 0.25f, v0 + 0.25f, light, 0xFFFFFF);
				}
			}
		}
		// the great tendrils: erupt from the ground in a ring and spiral up round the host, faster and faster
		float erupt = smooth(0f, 16f, age);
		for (int n = 0; n < STRANDS; n++) {
			double a0 = Math.PI * 2 * n / STRANDS + hash(seed + n) * 0.3;
			int pts = 22;
			int count = Math.max(2, Math.round(pts * erupt));
			Vec3[] line = new Vec3[count];
			float[] rad = new float[count];
			for (int i = 0; i < count; i++) {
				float s = i / (float) (pts - 1);
				float t = s * 1.04f;
				double a = a0 + s * Math.PI * 1.7 + spin(age);
				float r = (t > 1f ? radius(age, 1f, a, time) * (1f - (t - 1f) * 18f) : radius(age, t, a, time)) * 1.05f + 0.08f;
				r = Math.max(0.05f, r);
				if (s < 0.08f) {
					r = Mth.lerp(s / 0.08f, maxRadius(age) * 1.25f, r); // planted wide at the root
				}
				line[i] = feet.add(Math.cos(a) * r, Math.min(t, 1.0f) * h + 0.03, Math.sin(a) * r);
				rad[i] = Mth.lerp(s, 0.2f, 0.06f) * (1f + 0.25f * pulse(age));
			}
			d.tube(line, rad, 6, 0xFFFFFF);
		}
		// the weave: thinner strands the other way round
		if (age > 6f) {
			float w = smooth(6f, 22f, age);
			for (int n = 0; n < 18; n++) {
				double a0 = Math.PI * 2 * n / 18;
				int pts = 16;
				int count = Math.max(2, Math.round(pts * w));
				Vec3[] line = new Vec3[count];
				float[] rad = new float[count];
				for (int i = 0; i < count; i++) {
					float t = i / (float) (pts - 1) * 0.98f;
					double a = a0 - t * Math.PI * 1.2 - spin(age) * 0.7;
					float r = radius(age, t, a, time) * 1.03f;
					line[i] = feet.add(Math.cos(a) * r, t * h, Math.sin(a) * r);
					rad[i] = 0.07f;
				}
				d.tube(line, rad, 4, 0xFFFFFF);
			}
		}
	}

	/** The goo pool under the cocoon / the melt: a flat lumpy disc. */
	private static void pool(Draw d, Vec3 feet, float r, float time) {
		if (r < 0.05f) {
			return;
		}
		int segs = 20;
		int light = d.light(feet.add(0, 0.5, 0));
		Vec3 c = feet.add(0, 0.03, 0);
		for (int k = 0; k < segs; k++) {
			double a0 = Math.PI * 2 * k / segs, a1 = Math.PI * 2 * (k + 1) / segs;
			float r0 = r * (1f + 0.12f * (float) Math.sin(a0 * 5 + time * 0.05)), r1 = r * (1f + 0.12f * (float) Math.sin(a1 * 5 + time * 0.05));
			Vec3 p0 = c.add(Math.cos(a0) * r0, 0, Math.sin(a0) * r0), p1 = c.add(Math.cos(a1) * r1, 0, Math.sin(a1) * r1);
			d.quad(c, c, p1, p0, 0.5f, 0.5f, 0.75f, 0.75f, light, 0xD8D8D8);
		}
	}

	// ------------------------------------------------------------------------------------------------ the burst

	private static void burst(Draw d, Vec3 feet, float age, int seed) {
		float out = smooth(0f, 3.5f, age);
		float back = smooth(7f, 18f, age);
		float reach = out * (1f - back);
		pool(d, feet, 2.9f * (1f - smooth(2f, 18f, age)), age);
		if (reach < 0.02f) {
			return;
		}
		for (int n = 0; n < 18; n++) {
			double a = Math.PI * 2 * n / 18 + hash(seed + n * 7) * 0.4;
			float y0 = 1.0f + hash(seed + n * 3) * 5.6f;
			Vec3 from = feet.add(Math.cos(a) * 1.4, y0, Math.sin(a) * 1.4);
			float far = 4.0f + hash(seed + n) * 2.5f;
			Vec3 to = feet.add(Math.cos(a) * far, Math.max(0.2, y0 * 0.45 - age * 0.12), Math.sin(a) * far);
			int pts = 10;
			int count = Math.max(2, Math.round(pts * reach));
			Vec3[] line = new Vec3[count];
			float[] rad = new float[count];
			for (int i = 0; i < count; i++) {
				float s = i / (float) (pts - 1);
				Vec3 p = from.add(to.subtract(from).scale(s));
				line[i] = p.add(0, Math.sin(Math.PI * s) * 0.8 + Math.sin(s * 9 + age) * 0.12, 0);
				rad[i] = Mth.lerp(s, 0.24f, 0.05f);
			}
			d.tube(line, rad, 5, 0xFFFFFF);
		}
	}

	// ------------------------------------------------------------------------------------------------ the melt

	private static void melt(Draw d, Vec3 feet, float age, int seed, Player p) {
		float spread = smooth(0f, 18f, age) * (1f - smooth(20f, 30f, age));
		pool(d, feet, 1.4f + 2.0f * spread, age);
		// goo dripping off the slumping monster
		float drip = 1f - smooth(16f, 24f, age);
		if (drip > 0.02f) {
			for (int n = 0; n < 10; n++) {
				double a = Math.PI * 2 * n / 10 + hash(seed + n) * 0.5;
				float y = (2.0f + hash(seed + n * 5) * 3.5f) * (1f - smooth(0f, 22f, age) * 0.85f);
				Vec3 top = feet.add(Math.cos(a) * 1.5, y, Math.sin(a) * 1.5);
				Vec3 bottom = feet.add(Math.cos(a) * (1.8 + spread), 0.05, Math.sin(a) * (1.8 + spread));
				d.tube(new Vec3[] { top, top.add(bottom.subtract(top).scale(0.5)).add(0, -0.2, 0), bottom }, new float[] { 0.2f * drip, 0.14f * drip, 0.1f * drip }, 5,
						0xFFFFFF);
			}
		}
		// the host re-forming: tendrils from the pool pull back into them
		float pull = smooth(12f, 18f, age) * (1f - smooth(22f, 30f, age));
		if (pull > 0.02f) {
			Vec3 chest = feet.add(0, p.getBbHeight() * 0.55, 0);
			for (int n = 0; n < 8; n++) {
				double a = Math.PI * 2 * n / 8 + age * 0.05;
				Vec3 root = feet.add(Math.cos(a) * (1.2 + 1.6 * spread), 0.05, Math.sin(a) * (1.2 + 1.6 * spread));
				int pts = 9;
				Vec3[] line = new Vec3[pts];
				float[] rad = new float[pts];
				for (int i = 0; i < pts; i++) {
					float s = i / (float) (pts - 1) * pull;
					Vec3 q = root.add(chest.subtract(root).scale(s));
					line[i] = q.add(0, Math.sin(Math.PI * s) * 0.5, 0);
					rad[i] = Mth.lerp(i / (float) (pts - 1), 0.16f, 0.05f);
				}
				d.tube(line, rad, 5, 0xFFFFFF);
			}
		}
	}
}
