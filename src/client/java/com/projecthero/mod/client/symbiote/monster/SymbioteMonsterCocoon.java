package com.projecthero.mod.client.symbiote.monster;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.symbiote.SymbioteSuitTint;
import com.projecthero.mod.symbiote.SymbioteColours;
import com.projecthero.mod.symbiote.monster.SymbioteMonster;
import com.projecthero.mod.symbiote.monster.SymbioteMonsterState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Symbiote Monster form: the transformation and the melt, drawn by every client from the synced {@link SymbioteMonsterState}
 * (no packets) in world space after the entities.
 *
 * <pre>
 *   COCOON (2.5 s)
 *     0-16   twelve thick tendrils erupt from the ground in a ring round the feet and spiral up round the host -- turning faster and
 *            faster, writhing as they go
 *     6-28   a living shell grows shut from the ground up between them (a lumpy egg, ragged top edge), wrapped in thinner strands
 *            that keep crawling over it with swells running along them; a pool of goo spreads under it
 *     14-42  the cocoon swells from 3 to 7.2 blocks tall (the hitbox grows with it, server side)
 *     30-47  four heartbeats (lub-dub), quickening: the shell swells and stretches, the veins flush, the wet shine brightens
 *     44-50  it strains -- shivering, the strands drawn tight -- then tears open along five seams, light from inside showing through
 *            while strands across each tear stretch thin and snap
 *   MONSTER 0-16   the burst: shell shards and torn strands fly outward and drop, the pool shrinks away
 *   MELT    0-24   the monster melts into a spreading pool, goo dripping off it; 12-30 tendrils rise from the pool and pull the host
 *                  back up out of it, then the pool is gone
 * </pre>
 *
 * <p>Polish pass (user: "it reads as a dark woven basket-egg"): everything is drawn with {@link SymbioteMonsterTint#cocoon} -- a
 * wet sinew-and-vein surface baked in the host's suit colours (primary body, emblem-colour veins), opaque everywhere, so no foreign
 * colour shows anywhere (the old goo texture was purple). Two additive layers on top give it gloss within Minecraft's limits: a wet
 * shine whose strength follows a per-vertex specular term (it slides over the surface as the camera moves, and the streak texture drifts
 * slowly downward) and the veins, which flush with each heartbeat. Geometry first, then one emit per pass ({@link MonsterMesh.Batch}).
 */
public final class SymbioteMonsterCocoon {
	private static final int STRANDS = 8;
	private static final int RINGS = 22, SEGS = 24;
	private static final int TEARS = 5;
	private static final Vec3 SUN = new Vec3(0.35, 1.0, 0.25).normalize();
	private static final MonsterMesh.Batch BATCH = new MonsterMesh.Batch();

	private SymbioteMonsterCocoon() {
	}

	public static void initialize() {
		WorldRenderEvents.AFTER_ENTITIES.register(SymbioteMonsterCocoon::render);
	}

	private static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
		Vec3 cam = ctx.camera().getPosition();
		for (Player p : mc.level.players()) {
			SymbioteMonsterState s = SymbioteMonster.state(p);
			if (!s.inForm() || p.distanceToSqr(cam) > 128 * 128) {
				continue;
			}
			if (p.isInvisible() && mc.player != null && p.isInvisibleTo(mc.player)) {
				continue;
			}
			float age = mc.level.getGameTime() - s.phaseStart() + partial;
			if (s.phase() == SymbioteMonsterState.MONSTER && age > 18) {
				continue;
			}
			Vec3 feet = p.getPosition(partial);
			Vec3 o = feet.subtract(cam);
			int seed = p.getId() * 31;
			BATCH.clear();
			float pulse = 0f, open = 0f;
			switch (s.phase()) {
				case SymbioteMonsterState.COCOON -> {
					cocoon(o, age, seed);
					pulse = pulse(age);
					open = smooth(45f, 49.5f, age);
				}
				case SymbioteMonsterState.MONSTER -> burst(o, age, seed);
				case SymbioteMonsterState.MELT -> melt(o, age, seed, p);
				default -> {
				}
			}
			if (BATCH.quads == 0) {
				continue;
			}
			emit(ctx.matrixStack(), ctx.consumers(), mc.level, feet, p, pulse, open);
		}
	}

	/** The three passes: the opaque base, the wet shine, the veins (+ the light inside the tears). */
	private static void emit(PoseStack pose, MultiBufferSource buffers, Level level, Vec3 feet, Player p, float pulse, float open) {
		int light = LevelRenderer.getLightColor(level, BlockPos.containing(feet.add(0, 1.5, 0)));
		float lf = Mth.clamp(Math.max(LightTexture.block(light), LightTexture.sky(light) - level.getSkyDarken()) / 15f, 0.12f, 1f);
		SymbioteColours c = SymbioteSuitTint.colours(p);
		if (c == null) {
			c = SymbioteColours.CLASSIC;
		}
		int prim = c.primary(), sec = c.secondary(), eyes = c.eyes();
		// the shine: a near-white of the body's hue (as the suit's gloss is), so black reads blue-white wet and red reads pink-wet
		float[] hue = hue(prim);
		float[] shine = { 0.72f + 0.28f * hue[0], 0.72f + 0.28f * hue[1], 0.72f + 0.28f * hue[2] };
		// the veins flush in the emblem colour when it is bright, else in a hot version of the body colour
		float[] vein = max(sec) > 0.35f ? rgb(sec) : new float[] { 0.25f + 0.75f * hue[0], 0.12f + 0.6f * hue[1], 0.12f + 0.6f * hue[2] };
		float[] glow = rgb(eyes);
		PoseStack.Pose last = pose.last();

		BATCH.emit(buffers.getBuffer(RenderType.entityCutoutNoCull(SymbioteMonsterTint.cocoon(p))), last, light,
				(kind, x, y, z, nx, ny, nz) -> kind == MonsterMesh.INNER ? 0x161616 : 0xFFFFFF);

		final float beat = pulse;
		BATCH.emit(buffers.getBuffer(RenderType.eyes(SymbioteMonsterTint.cocoonGloss())), last, light, (kind, x, y, z, nx, ny, nz) -> {
			if (kind == MonsterMesh.INNER) {
				return -1;
			}
			double len = Math.sqrt(x * x + y * y + z * z);
			double vx = -x / len, vy = -y / len, vz = -z / len;
			double hx = vx + SUN.x, hy = vy + SUN.y, hz = vz + SUN.z;
			double hl = Math.sqrt(hx * hx + hy * hy + hz * hz);
			double nh = Math.max(0, (nx * hx + ny * hy + nz * hz) / hl);
			double nv = Math.abs(nx * vx + ny * vy + nz * vz);
			double spec = Math.pow(nh, 10);
			double rim = Math.pow(1 - nv, 3);
			double k = (0.1 + 0.8 * spec + 0.3 * rim + (kind == MonsterMesh.SHELL ? 0.35 * beat : 0)) * lf * (kind == MonsterMesh.POOL ? 0.6 : 1);
			return pack(shine, (float) Math.min(1.0, k));
		});

		float vk = (0.12f + 0.88f * pulse) * Math.max(lf, 0.45f);
		BATCH.emit(buffers.getBuffer(RenderType.eyes(SymbioteMonsterTint.cocoonVeins())), last, light,
				(kind, x, y, z, nx, ny, nz) -> kind == MonsterMesh.SHELL ? pack(vein, vk) : -1);

		if (open > 0.01f) {
			BATCH.emit(buffers.getBuffer(RenderType.eyes(SymbioteMonsterTint.cocoonGlow())), last, light,
					(kind, x, y, z, nx, ny, nz) -> kind == MonsterMesh.INNER ? pack(glow, Math.min(1f, open * 1.3f)) : -1);
		}
	}

	private static float max(int rgb) {
		return Math.max(((rgb >> 16) & 0xFF), Math.max(((rgb >> 8) & 0xFF), (rgb & 0xFF))) / 255f;
	}

	private static float[] rgb(int rgb) {
		return new float[] { ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f };
	}

	private static float[] hue(int rgb) {
		float[] c = rgb(rgb);
		float m = Math.max(c[0], Math.max(c[1], c[2]));
		return m < 0.03f ? new float[] { 0.82f, 0.86f, 1.0f } : new float[] { c[0] / m, c[1] / m, c[2] / m };
	}

	private static int pack(float[] c, float k) {
		int r = Mth.clamp(Math.round(c[0] * k * 255f), 0, 255), g = Mth.clamp(Math.round(c[1] * k * 255f), 0, 255),
				b = Mth.clamp(Math.round(c[2] * k * 255f), 0, 255);
		return r << 16 | g << 8 | b;
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

	/** The heartbeats: a lub-dub swell at each beat, quickening. */
	static float pulse(float age) {
		float p = 0f;
		for (float beat : new float[] { 30f, 37f, 42f, 46f }) {
			p = Math.max(p, spike(age - beat, 2.3f));
			p = Math.max(p, 0.55f * spike(age - beat - 2.0f, 1.6f));
		}
		return p;
	}

	private static float spike(float dt, float w) {
		float k = 1f - Math.abs(dt) / w;
		return k > 0 ? k * k : 0f;
	}

	private static float height(float age) {
		return Mth.lerp(smooth(14f, 42f, age), 3.0f, 7.2f) * (1f + 0.035f * pulse(age));
	}

	private static float maxRadius(float age) {
		return Mth.lerp(smooth(14f, 42f, age), 1.05f, 2.15f);
	}

	private static float strain(float age) {
		return smooth(44f, 50f, age);
	}

	/** The cocoon's radius at height fraction {@code t} round angle {@code a}: a lumpy egg, wider low down, closing at the top. */
	private static float radius(float age, float t, double a) {
		float prof = (float) Math.pow(Math.max(0.0, Math.sin(Math.PI * (0.04 + 0.92 * Mth.clamp(t, 0f, 1f)))), 0.6) * (1f + 0.14f * (1f - t));
		float lump = 1f + 0.06f * (float) Math.sin(a * 3 + t * 7 + age * 0.08) + 0.04f * (float) Math.sin(a * 7 - t * 11 + age * 0.13);
		float beat = 1f + 0.13f * pulse(age) * (0.45f + t);
		float st = strain(age);
		float strainK = 1f + 0.1f * st + 0.045f * st * (float) Math.sin(age * 9 + a * 13 + t * 17);
		return maxRadius(age) * prof * lump * beat * strainK;
	}

	private static float spin(float age) {
		return 0.0036f * age * age;
	}

	/** A point on the cocoon's surface (camera-relative) at height fraction {@code t}, angle {@code a}, pushed out by {@code lift}. */
	private static Vec3 surface(Vec3 o, float age, float t, double a, float lift) {
		float r = radius(age, t, a) + lift;
		return o.add(Math.cos(a) * r, t * height(age), Math.sin(a) * r);
	}

	// ------------------------------------------------------------------------------------------------ COCOON

	private static void cocoon(Vec3 o, float age, int seed) {
		float h = height(age);
		pool(o, maxRadius(age) + 0.7f * smooth(0f, 10f, age), age);
		float cover = smooth(6f, 28f, age);
		float open = smooth(45f, 49.5f, age);
		double rot = spin(age) * 0.15;
		// the tear seams: a column each, centred at a random height, splitting open from the middle out
		int[] seamK = new int[TEARS];
		float[] seamT = new float[TEARS], seamH = new float[TEARS];
		for (int n = 0; n < TEARS; n++) {
			seamK[n] = (int) (SEGS * (n + hash(seed + 91 * n) * 0.6f) / TEARS) % SEGS;
			seamT[n] = 0.42f + 0.22f * hash(seed + 17 * n);
			seamH[n] = 0.12f + 0.34f * open;
		}
		if (cover > 0.02f) {
			Vec3[][] grid = new Vec3[RINGS + 1][SEGS + 1];
			float[] topCut = new float[SEGS + 1];
			for (int k = 0; k <= SEGS; k++) {
				topCut[k] = cover * (1.03f + 0.08f * (hash(seed + k % SEGS) - 0.5f)) + (cover > 0.97f ? 0.1f : 0f);
			}
			float[] ts = new float[RINGS + 1];
			for (int i = 0; i <= RINGS; i++) {
				float t = i / (float) RINGS;
				ts[i] = t;
				for (int k = 0; k <= SEGS; k++) {
					double a = Math.PI * 2 * k / SEGS + rot;
					float tt = Math.min(t, topCut[k]);
					float r = radius(age, tt, a);
					if (tt >= 0.995f) {
						r *= 0.2f;
					}
					grid[i][k] = o.add(Math.cos(a) * r, tt * h, Math.sin(a) * r);
				}
			}
			// smooth normals from the neighbours (outward)
			Vec3[][] nrm = new Vec3[RINGS + 1][SEGS + 1];
			for (int i = 0; i <= RINGS; i++) {
				for (int k = 0; k <= SEGS; k++) {
					Vec3 dT = grid[Math.min(RINGS, i + 1)][k].subtract(grid[Math.max(0, i - 1)][k]);
					int kp = k == SEGS ? 1 : k + 1, km = k == 0 ? SEGS - 1 : k - 1;
					Vec3 dA = grid[i][kp].subtract(grid[i][km]);
					Vec3 n = dT.cross(dA);
					if (n.lengthSqr() < 1.0e-10) {
						double a = Math.PI * 2 * k / SEGS + rot;
						n = new Vec3(Math.cos(a), i == RINGS ? 1 : 0, Math.sin(a));
					}
					nrm[i][k] = n.normalize();
				}
			}
			for (int i = 0; i < RINGS; i++) {
				float t = ts[i], t1 = ts[i + 1];
				for (int k = 0; k < SEGS; k++) {
					if (t > topCut[k] && t > topCut[k + 1]) {
						continue;
					}
					// two wraps round, two up: the texture stretches with the shell as it swells
					float u0 = 2f * k / SEGS, u1 = 2f * (k + 1) / SEGS, v0 = 2f * t, v1 = 2f * t1;
					float gap = 0f;
					for (int n = 0; n < TEARS; n++) {
						if (seamK[n] == k && open > 0f) {
							float x = ((t + t1) * 0.5f - seamT[n]) / seamH[n];
							if (Math.abs(x) < 1f) {
								gap = Math.max(gap, open * (1f - x * x));
							}
						}
					}
					if (gap <= 0.01f) {
						BATCH.quad(MonsterMesh.SHELL, grid[i][k], grid[i][k + 1], grid[i + 1][k + 1], grid[i + 1][k], nrm[i][k], nrm[i][k + 1],
								nrm[i + 1][k + 1], nrm[i + 1][k], u0, v0, u1, v1);
						continue;
					}
					// torn: the two lips pull apart, the dark inside (lit from within) shows in the gap
					float lip = (1f - Math.min(0.95f, gap)) * 0.5f;
					Vec3 a0 = lerp(grid[i][k], grid[i][k + 1], lip), a1 = lerp(grid[i + 1][k], grid[i + 1][k + 1], lip);
					Vec3 b0 = lerp(grid[i][k], grid[i][k + 1], 1f - lip), b1 = lerp(grid[i + 1][k], grid[i + 1][k + 1], 1f - lip);
					float ul = u0 + (u1 - u0) * lip, ur = u1 - (u1 - u0) * lip;
					BATCH.quad(MonsterMesh.SHELL, grid[i][k], a0, a1, grid[i + 1][k], nrm[i][k], nrm[i][k], nrm[i + 1][k], nrm[i + 1][k], u0, v0, ul,
							v1);
					BATCH.quad(MonsterMesh.SHELL, b0, grid[i][k + 1], grid[i + 1][k + 1], b1, nrm[i][k + 1], nrm[i][k + 1], nrm[i + 1][k + 1],
							nrm[i + 1][k + 1], ur, v0, u1, v1);
					Vec3 c0 = o.add(grid[i][k].add(grid[i][k + 1]).scale(0.5).subtract(o).multiply(0.86, 1, 0.86));
					Vec3 c1 = o.add(grid[i + 1][k].add(grid[i + 1][k + 1]).scale(0.5).subtract(o).multiply(0.86, 1, 0.86));
					BATCH.flat(MonsterMesh.INNER, lerp(c0, a0, 0.2f), lerp(c0, b0, 0.2f), lerp(c1, b1, 0.2f), lerp(c1, a1, 0.2f), 0.2f, v0, 0.8f, v1);
				}
			}
			// strands across each tear stretch thin and snap
			if (open > 0.02f) {
				for (int n = 0; n < TEARS; n++) {
					double a = Math.PI * 2 * (seamK[n] + 0.5) / SEGS + rot;
					for (int j = 0; j < 4; j++) {
						float t = seamT[n] + seamH[n] * (-0.6f + 0.4f * j) * 0.9f;
						float snap = 47.2f + hash(seed + n * 13 + j * 5) * 2.2f;
						float lipA = (float) (Math.PI * 2 / SEGS) * 0.5f * Math.min(0.95f, open);
						Vec3 l = surface(o, age, t, a - lipA, 0.02f), r = surface(o, age, t, a + lipA, 0.02f);
						Vec3 mid = l.add(r).scale(0.5);
						Vec3 out = mid.subtract(o).multiply(1, 0, 1).normalize();
						if (age < snap) {
							float thin = 1f - smooth(45f, snap, age) * 0.75f;
							Vec3 bow = mid.add(out.scale(0.08 * thin));
							BATCH.tube(MonsterMesh.STRAND, new Vec3[] { l, bow, r }, new float[] { 0.07f * thin, 0.035f * thin, 0.07f * thin }, 5, 1f, 2f, 0f);
						} else {
							float rec = smooth(snap, snap + 2f, age);
							for (Vec3 end : new Vec3[] { l, r }) {
								Vec3 tip = end.add(mid.subtract(end).scale(0.7 - 0.4 * rec)).add(out.scale(0.15 * rec)).add(0, -0.25 * rec, 0);
								BATCH.tube(MonsterMesh.STRAND, new Vec3[] { end, end.add(tip.subtract(end).scale(0.5)).add(out.scale(0.05)), tip },
										new float[] { 0.06f, 0.04f, 0.012f }, 5, 1f, 2f, 0f);
							}
						}
					}
				}
			}
		}
		// the great tendrils: erupt from the ground in a ring and spiral up round the host, faster and faster, writhing
		float erupt = smooth(0f, 16f, age);
		float tight = 1f - 0.25f * strain(age);
		for (int n = 0; n < STRANDS; n++) {
			double a0 = Math.PI * 2 * n / STRANDS + hash(seed + n) * 0.3;
			int pts = 24;
			int count = Math.max(2, Math.round(pts * erupt));
			Vec3[] line = new Vec3[count];
			float[] rad = new float[count];
			for (int i = 0; i < count; i++) {
				float s = i / (float) (pts - 1);
				float t = s * 1.04f;
				double a = a0 + s * Math.PI * 1.1 + spin(age) + 0.09 * Math.sin(s * 9 - age * 0.45 + n * 1.7);
				float writhe = 0.07f * (float) Math.sin(s * 13 - age * 0.6 + n) * (1f - strain(age));
				float r = (t > 1f ? radius(age, 1f, a) * (1f - (t - 1f) * 18f) : radius(age, t, a)) * 1.04f + 0.1f + writhe;
				r = Math.max(0.05f, r);
				if (s < 0.08f) {
					r = Mth.lerp(s / 0.08f, maxRadius(age) * 1.25f, r); // planted wide at the root
				}
				line[i] = o.add(Math.cos(a) * r, Math.min(t, 1.0f) * h + 0.03, Math.sin(a) * r);
				rad[i] = Mth.lerp(s, 0.32f, 0.12f) * (1f + 0.3f * pulse(age)) * tight;
			}
			BATCH.tube(MonsterMesh.STRAND, line, rad, 7, 1f, 0.9f, n * 0.37f);
		}
		// the wrapping strands: thick ropes crawling up over the shell the same way round, a swell running up each
		if (age > 6f) {
			float w = smooth(6f, 22f, age);
			for (int n = 0; n < 8; n++) {
				double a0 = Math.PI * 2 * (n + 0.5) / 8 + age * 0.012 * (n % 2 == 0 ? 1 : -1);
				int pts = 20;
				int count = Math.max(2, Math.round(pts * w));
				Vec3[] line = new Vec3[count];
				float[] rad = new float[count];
				float bulge = (age * 0.035f + n * 0.37f) % 1.3f;
				for (int i = 0; i < count; i++) {
					float s = i / (float) (pts - 1);
					float t = s * 0.98f;
					double a = a0 + t * Math.PI * 0.9 + spin(age) * 0.8 + 0.07 * Math.sin(t * 11 + age * 0.3 + n);
					float b = (float) Math.exp(-(s - bulge) * (s - bulge) / 0.006);
					line[i] = surface(o, age, t, a, 0.06f + 0.08f * b);
					rad[i] = (0.14f + 0.1f * b) * tight;
				}
				BATCH.tube(MonsterMesh.STRAND, line, rad, 5, 1f, 1.2f, n * 0.53f);
			}
		}
	}

	private static Vec3 lerp(Vec3 a, Vec3 b, float t) {
		return a.add(b.subtract(a).scale(t));
	}

	/** The goo pool under the cocoon / the melt: a flat lumpy disc. */
	private static void pool(Vec3 o, float r, float time) {
		if (r < 0.05f) {
			return;
		}
		int segs = 24;
		Vec3 c = o.add(0, 0.03, 0);
		Vec3 up = new Vec3(0, 1, 0);
		for (int k = 0; k < segs; k++) {
			double a0 = Math.PI * 2 * k / segs, a1 = Math.PI * 2 * (k + 1) / segs;
			float r0 = r * (1f + 0.12f * (float) Math.sin(a0 * 5 + time * 0.05)), r1 = r * (1f + 0.12f * (float) Math.sin(a1 * 5 + time * 0.05));
			Vec3 p0 = c.add(Math.cos(a0) * r0, 0, Math.sin(a0) * r0), p1 = c.add(Math.cos(a1) * r1, 0, Math.sin(a1) * r1);
			float u0 = 0.5f + 0.5f * (float) Math.cos(a0) * r0 * 0.4f, v0 = 0.5f + 0.5f * (float) Math.sin(a0) * r0 * 0.4f;
			float u1 = 0.5f + 0.5f * (float) Math.cos(a1) * r1 * 0.4f, v1 = 0.5f + 0.5f * (float) Math.sin(a1) * r1 * 0.4f;
			BATCH.quadUv(MonsterMesh.POOL, c, c, p1, p0, up, new float[] { 0.5f, 0.5f, 0.5f, 0.5f, u1, v1, u0, v0 });
		}
	}

	// ------------------------------------------------------------------------------------------------ the burst

	private static void burst(Vec3 o, float age, int seed) {
		float out = smooth(0f, 3.5f, age);
		float back = smooth(7f, 18f, age);
		float reach = out * (1f - back);
		pool(o, 2.9f * (1f - smooth(2f, 18f, age)), age);
		// shards of the shell flung outward, tumbling down and shrinking away
		float life = 1f - smooth(6f, 15f, age);
		if (life > 0.02f) {
			for (int n = 0; n < 12; n++) {
				double a0 = Math.PI * 2 * (n + hash(seed + n * 11) * 0.7) / 12;
				float t0 = 0.2f + 0.6f * hash(seed + n * 19);
				float speed = 0.32f + 0.18f * hash(seed + n * 23);
				Vec3 dir = new Vec3(Math.cos(a0), 0, Math.sin(a0));
				Vec3 move = dir.scale(speed * age).add(0, 0.25 * age - 0.05 * age * age, 0);
				float shrink = life;
				Vec3[][] g = new Vec3[3][3];
				for (int i = 0; i < 3; i++) {
					for (int k = 0; k < 3; k++) {
						float t = t0 + (i - 1) * 0.07f * shrink;
						double a = a0 + (k - 1) * 0.22 * shrink;
						float r = 2.15f * (float) Math.pow(Math.max(0.0, Math.sin(Math.PI * (0.04 + 0.92 * t))), 0.6) * (1f + 0.14f * (1f - t)) * 1.12f;
						g[i][k] = o.add(Math.cos(a) * r, t * 7.2 * (1 - 0.15 * age / 15f), Math.sin(a) * r).add(move);
					}
				}
				for (int i = 0; i < 2; i++) {
					for (int k = 0; k < 2; k++) {
						BATCH.flat(MonsterMesh.SHELL, g[i][k], g[i][k + 1], g[i + 1][k + 1], g[i + 1][k], k * 0.5f, i * 0.5f, k * 0.5f + 0.5f,
								i * 0.5f + 0.5f);
					}
				}
			}
		}
		if (reach < 0.02f) {
			return;
		}
		for (int n = 0; n < 18; n++) {
			double a = Math.PI * 2 * n / 18 + hash(seed + n * 7) * 0.4;
			float y0 = 1.0f + hash(seed + n * 3) * 5.6f;
			Vec3 from = o.add(Math.cos(a) * 1.4, y0, Math.sin(a) * 1.4);
			float far = 4.0f + hash(seed + n) * 2.5f;
			Vec3 to = o.add(Math.cos(a) * far, Math.max(0.2, y0 * 0.45 - age * 0.12), Math.sin(a) * far);
			int pts = 10;
			int count = Math.max(2, Math.round(pts * reach));
			Vec3[] line = new Vec3[count];
			float[] rad = new float[count];
			for (int i = 0; i < count; i++) {
				float s = i / (float) (pts - 1);
				Vec3 p = to.subtract(from).scale(s).add(from);
				line[i] = p.add(0, Math.sin(Math.PI * s) * 0.8 + Math.sin(s * 9 + age) * 0.12, 0);
				rad[i] = Mth.lerp(s, 0.26f, 0.05f);
			}
			BATCH.tube(MonsterMesh.STRAND, line, rad, 6, 1f, 0.9f, n * 0.3f);
		}
	}

	// ------------------------------------------------------------------------------------------------ the melt

	private static void melt(Vec3 o, float age, int seed, Player p) {
		float spread = smooth(0f, 18f, age) * (1f - smooth(20f, 30f, age));
		pool(o, 1.4f + 2.0f * spread, age);
		// goo dripping off the slumping monster
		float drip = 1f - smooth(16f, 24f, age);
		if (drip > 0.02f) {
			for (int n = 0; n < 10; n++) {
				double a = Math.PI * 2 * n / 10 + hash(seed + n) * 0.5;
				float y = (2.0f + hash(seed + n * 5) * 3.5f) * (1f - smooth(0f, 22f, age) * 0.85f);
				Vec3 top = o.add(Math.cos(a) * 1.5, y, Math.sin(a) * 1.5);
				Vec3 bottom = o.add(Math.cos(a) * (1.8 + spread), 0.05, Math.sin(a) * (1.8 + spread));
				BATCH.tube(MonsterMesh.STRAND, new Vec3[] { top, top.add(bottom.subtract(top).scale(0.5)).add(0, -0.2, 0), bottom },
						new float[] { 0.2f * drip, 0.14f * drip, 0.1f * drip }, 6, 1f, 0.9f, n * 0.3f);
			}
		}
		// the host re-forming: tendrils from the pool pull back into them
		float pull = smooth(12f, 18f, age) * (1f - smooth(22f, 30f, age));
		if (pull > 0.02f) {
			Vec3 chest = o.add(0, p.getBbHeight() * 0.55, 0);
			for (int n = 0; n < 8; n++) {
				double a = Math.PI * 2 * n / 8 + age * 0.05;
				Vec3 root = o.add(Math.cos(a) * (1.2 + 1.6 * spread), 0.05, Math.sin(a) * (1.2 + 1.6 * spread));
				int pts = 9;
				Vec3[] line = new Vec3[pts];
				float[] rad = new float[pts];
				for (int i = 0; i < pts; i++) {
					float s = i / (float) (pts - 1) * pull;
					Vec3 q = chest.subtract(root).scale(s).add(root);
					line[i] = q.add(0, Math.sin(Math.PI * s) * 0.5, 0);
					rad[i] = Mth.lerp(i / (float) (pts - 1), 0.16f, 0.05f);
				}
				BATCH.tube(MonsterMesh.STRAND, line, rad, 6, 1f, 0.9f, n * 0.3f);
			}
		}
	}
}
