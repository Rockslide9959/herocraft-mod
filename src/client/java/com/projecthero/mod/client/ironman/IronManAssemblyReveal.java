package com.projecthero.mod.client.ironman;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.joml.Vector3f;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.suit.IronManAssemblyPlan;
import com.projecthero.mod.ironman.suit.IronManMk5Suitcase;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * v0.14.21 self-assembly: the <b>panel-tile</b> texture fill of an Iron Man piece -- replaces the {@code ArmorSweepReveal}
 * sweep line for every mark (Thor / Green Lantern / Flash keep the sweep). While a bone flies in
 * ({@link IronManAssemblyPlan}), its surface fills in as 2x2-texel plate tiles flipping on in a scattered order that runs
 * outward from the arc reactor (or, for the Mark V case build, from the right hand); each tile shows a hot cyan-white seam
 * for the frame it appears and a cyan tint for two more. A texel's flip time is the bone's own start in the timetable plus
 * its tile's place in the bone's order, so a bone that has not started yet is fully transparent and a snapped one fully
 * plated. The release plays the same frames backwards (tiles flip off in reverse).
 *
 * <p>Built lazily per (mark geometry, texture, piece, style), {@link #FRAMES} frames each, kept for the session. Texels
 * no bone of the piece uses keep their alpha (nothing draws them in that slot's pass anyway). A texel shared by several
 * bones (the faceplate reuses the helmet shell's front UV) flips with the latest of them, so nothing shows early.
 */
public final class IronManAssemblyReveal {
	public static final int FRAMES = 40;
	private static final int TILE = 2;
	private static final int SEAM_HOT = 0xFFE8FFFF;  // white-hot seam, newest tiles
	private static final int SEAM_CYAN = 0xFF4FE3FF; // cooling cyan, the frames after
	private static final Map<String, ResourceLocation[]> CACHE = new HashMap<>();

	private IronManAssemblyReveal() {
	}

	/** The texture for piece {@code bit} at piece progress {@code p} (0..1), or {@code base} when complete / unreadable. */
	public static ResourceLocation texture(ResourceLocation geometry, ResourceLocation base, float p, int bit, boolean fromCase) {
		if (p >= 0.999f || bit < 0) {
			return base;
		}
		String key = geometry + "|" + base + "|" + bit + "|" + fromCase;
		ResourceLocation[] frames = CACHE.computeIfAbsent(key, k -> build(geometry, base, bit, fromCase));
		if (frames.length == 0) {
			return base;
		}
		int step = Math.max(0, Math.min(frames.length - 1, (int) Math.floor(p * FRAMES)));
		return frames[step];
	}

	// ---------------- v0.14.27: the 3 s build-on ----------------

	/** v0.14.27: one frame per tick of the {@code BUILD_TICKS} window, so the shell visibly grows a few texels at a time. */
	public static final int BUILD_FRAMES = 60;
	private static final int HALF_EDGE = 0xFFBFF4FF;   // the bright leading edge of a closing base half
	private static final Map<String, ResourceLocation[]> BUILD_CACHE = new HashMap<>();

	/**
	 * v0.14.27: the texture for piece {@code bit} at build progress {@code p} of an ordinary suit-up -- the base layer
	 * closing in two halves (a bright leading edge on each), then the outer shell flipping on one texel at a time with a
	 * hot seam on the newest ones. {@code base} once complete / unreadable.
	 */
	public static ResourceLocation buildTexture(ResourceLocation geometry, ResourceLocation base, float p, int bit) {
		return buildTexture(geometry, base, p, bit, false);
	}

	/**
	 * v0.14.29: as {@link #buildTexture(ResourceLocation, ResourceLocation, float, int)}; {@code mk5} re-times each
	 * texel into its build-order step's sub-window ({@link IronManMk5Suitcase}) -- the chestplate's torso before its arms,
	 * the helmet before its faceplate -- and nothing at all shows at progress 0.
	 */
	public static ResourceLocation buildTexture(ResourceLocation geometry, ResourceLocation base, float p, int bit, boolean mk5) {
		if (p >= 0.999f || bit < 0) {
			return base;
		}
		String key = geometry + "|" + base + "|" + bit + "|build" + (mk5 ? "|mk5" : "");
		ResourceLocation[] frames = BUILD_CACHE.computeIfAbsent(key, k -> buildFrames(geometry, base, bit, mk5));
		if (frames.length == 0) {
			return base;
		}
		int step = Math.max(0, Math.min(frames.length - 1, (int) Math.floor(p * BUILD_FRAMES)));
		return frames[step];
	}

	private static ResourceLocation[] buildFrames(ResourceLocation geometry, ResourceLocation base, int bit, boolean mk5) {
		Minecraft mc = Minecraft.getInstance();
		Optional<Resource> texRes = mc.getResourceManager().getResource(base);
		Optional<Resource> geoRes = mc.getResourceManager().getResource(geometry);
		if (texRes.isEmpty() || geoRes.isEmpty()) {
			return new ResourceLocation[0];
		}
		NativeImage src;
		JsonObject geo;
		try (InputStream in = texRes.get().open(); Reader reader = new InputStreamReader(geoRes.get().open(), StandardCharsets.UTF_8)) {
			src = NativeImage.read(in);
			geo = JsonParser.parseReader(reader).getAsJsonObject();
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not build the Iron Man build-on for {}: {}", base, e.toString());
			return new ResourceLocation[0];
		}
		int w = src.getWidth();
		int h = src.getHeight();
		List<Sample> samples = new ArrayList<>();
		try {
			collect(geo, w, h, bit, samples);
		} catch (RuntimeException e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not read {} for the Iron Man build-on: {}", geometry, e.toString());
			src.close();
			return new ResourceLocation[0];
		}
		BuildTimes bt = buildTimes(samples, w * h, bit, mk5);

		ResourceLocation[] frames = new ResourceLocation[BUILD_FRAMES];
		String tag = base.getPath().replaceAll("[^a-z0-9_]", "_") + "_" + bit + "_build" + (mk5 ? "_mk5" : "");
		float frame = 1f / BUILD_FRAMES;
		for (int k = 0; k < BUILD_FRAMES; k++) {
			float p = k / (float) BUILD_FRAMES;
			NativeImage img = new NativeImage(w, h, true);
			img.copyFrom(src);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int i = y * w + x;
					float tb = bt.base[i];
					float ts = bt.shell[i];
					if (Float.isNaN(tb) && Float.isNaN(ts)) {
						continue; // not this piece's texel
					}
					int orig = src.getPixelRGBA(x, y);
					boolean opaque = ((orig >>> 24) & 0xFF) != 0;
					if (!Float.isNaN(tb) && (Float.isNaN(ts) || !bt.dark)) {
						// base layer: the two halves grow toward the seam; the leading edge glows
						if (p < tb) {
							img.setPixelRGBA(x, y, 0);
						} else if (opaque && p - tb < 1.5f * frame) {
							img.setPixelRGBA(x, y, blendAbgr(orig, argbToAbgr(HALF_EDGE), 0.75f));
						}
						continue;
					}
					if (!Float.isNaN(tb) && p >= tb && p < ts) {
						// a piece with no base layer (the boots): its halves close as a dark under-plate first
						if (opaque) {
							img.setPixelRGBA(x, y, p - tb < 1.5f * frame ? blendAbgr(orig, argbToAbgr(HALF_EDGE), 0.75f)
									: darken(orig, 0.32f));
						}
						continue;
					}
					// the shell: one texel at a time, the newest white-hot, then cooling cyan
					if (p < ts) {
						img.setPixelRGBA(x, y, 0);
					} else if (opaque) {
						float age = p - ts;
						if (age < frame) {
							img.setPixelRGBA(x, y, argbToAbgr(SEAM_HOT));
						} else if (age < 3f * frame) {
							img.setPixelRGBA(x, y, blendAbgr(orig, argbToAbgr(SEAM_CYAN), age < 2f * frame ? 0.6f : 0.3f));
						}
					}
				}
			}
			ResourceLocation id = ProjectHeroMod.id("dynamic/ironman_build/" + tag + "_" + k);
			mc.getTextureManager().register(id, new DynamicTexture(img));
			frames[k] = id;
		}
		src.close();
		return frames;
	}

	/** Per texel: base-layer time, shell time (NaN = none), and whether the base is a dark under-plate (no base cubes). */
	record BuildTimes(float[] base, float[] shell, boolean dark) {
	}

	/**
	 * v0.14.27: base texels (of a {@code base_*} cube) get their half-closing time from their distance to the cube's seam;
	 * every other texel of the piece is ranked one after another, outward from {@link IronManAssemblyPlan#shellOrigin}
	 * with a per-texel scatter, so the shell builds pixel by pixel. A texel shared by a base cube and a shell cube goes
	 * with the base (it is the inside of a plate). A piece with no base cube (the boots) closes its own cubes in halves
	 * as a dark under-plate, and the shell pass then colours them in.
	 */
	static BuildTimes buildTimes(List<Sample> samples, int size, int bit) {
		return buildTimes(samples, size, bit, false);
	}

	/**
	 * v0.14.29: {@code mk5} -- every texel belongs to a build-order step (a texel shared by several bones goes with the
	 * latest step, so the faceplate's eye slits never show early); the shell is ranked within each step on its own and
	 * every time is re-timed into that step's sub-window ({@link IronManMk5Suitcase#remapStep}).
	 */
	static BuildTimes buildTimes(List<Sample> samples, int size, int bit, boolean mk5) {
		if (mk5) {
			int[] stepOf = new int[size];
			Arrays.fill(stepOf, -1);
			for (Sample s : samples) {
				stepOf[s.index()] = Math.max(stepOf[s.index()], IronManMk5Suitcase.step(bit, s.bone()));
			}
			float[] base = new float[size];
			float[] shell = new float[size];
			Arrays.fill(base, Float.NaN);
			Arrays.fill(shell, Float.NaN);
			boolean dark = true;
			for (int step = IronManMk5Suitcase.STEP_CHEST; step <= IronManMk5Suitcase.STEP_FACEPLATE; step++) {
				List<Sample> group = new ArrayList<>();
				for (Sample s : samples) {
					if (stepOf[s.index()] == step) {
						group.add(s);
					}
				}
				if (group.isEmpty()) {
					continue;
				}
				BuildTimes g = buildTimes(group, size, bit, false);
				dark &= g.dark();
				for (Sample s : group) {
					int i = s.index();
					if (!Float.isNaN(g.base()[i])) {
						base[i] = IronManMk5Suitcase.remapStep(step, g.base()[i]);
					}
					if (!Float.isNaN(g.shell()[i])) {
						shell[i] = IronManMk5Suitcase.remapStep(step, g.shell()[i]);
					}
				}
			}
			return new BuildTimes(base, shell, dark);
		}
		float[] base = new float[size];
		float[] shell = new float[size];
		Arrays.fill(base, Float.NaN);
		Arrays.fill(shell, Float.NaN);
		boolean pieceHasBase = false;
		for (Sample s : samples) {
			pieceHasBase |= s.baseCube();
		}
		Map<Integer, Vector3f> shellPos = new HashMap<>();
		for (Sample s : samples) {
			int i = s.index();
			if (s.baseCube() || !pieceHasBase) {
				float t = IronManAssemblyPlan.baseTexelTime(s.fromSeam());
				base[i] = Float.isNaN(base[i]) ? t : Math.min(base[i], t);
			}
			if (!s.baseCube()) {
				shellPos.putIfAbsent(i, s.pos());
			}
		}
		float[] o = IronManAssemblyPlan.shellOrigin(bit);
		Vector3f origin = new Vector3f(o[0], o[1], o[2]);
		float maxD = 1e-3f;
		for (Vector3f v : shellPos.values()) {
			maxD = Math.max(maxD, v.distance(origin));
		}
		List<float[]> order = new ArrayList<>();
		for (Map.Entry<Integer, Vector3f> e : shellPos.entrySet()) {
			if (pieceHasBase && !Float.isNaN(base[e.getKey()])) {
				continue; // shared with a base cube: it closes with the base
			}
			float key = 0.72f * e.getValue().distance(origin) / maxD + 0.28f * IronManAssemblyPlan.hash("texel", e.getKey());
			order.add(new float[] { key, e.getKey() });
		}
		order.sort((a, b) -> Float.compare(a[0], b[0]));
		for (int r = 0; r < order.size(); r++) {
			shell[(int) order.get(r)[1]] = IronManAssemblyPlan.shellTexelTime(r, order.size());
		}
		return new BuildTimes(base, shell, !pieceHasBase);
	}

	private static int darken(int abgr, float f) {
		int a = (abgr >>> 24) & 0xFF;
		int r = 0;
		for (int sh = 0; sh <= 16; sh += 8) {
			r |= Math.round(((abgr >> sh) & 0xFF) * f) << sh;
		}
		return (a << 24) | r;
	}

	/** The bone a planned texel belongs to: the blades flip on with their gauntlets. */
	private static String planBone(String bone) {
		if ("right_blade".equals(bone)) {
			return "right_gauntlet";
		}
		if ("left_blade".equals(bone)) {
			return "left_gauntlet";
		}
		return bone;
	}

	/** One texel sample: bone, texel index, rest position (bedrock coordinates). */
	private record Sample(String bone, int index, int tile, Vector3f pos, boolean baseCube, float fromSeam) {
	}

	private static ResourceLocation[] build(ResourceLocation geometry, ResourceLocation base, int bit, boolean fromCase) {
		Minecraft mc = Minecraft.getInstance();
		Optional<Resource> texRes = mc.getResourceManager().getResource(base);
		Optional<Resource> geoRes = mc.getResourceManager().getResource(geometry);
		if (texRes.isEmpty() || geoRes.isEmpty()) {
			return new ResourceLocation[0];
		}
		NativeImage src;
		JsonObject geo;
		try (InputStream in = texRes.get().open(); Reader reader = new InputStreamReader(geoRes.get().open(), StandardCharsets.UTF_8)) {
			src = NativeImage.read(in);
			geo = JsonParser.parseReader(reader).getAsJsonObject();
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not build the Iron Man assembly for {}: {}", base, e.toString());
			return new ResourceLocation[0];
		}
		int w = src.getWidth();
		int h = src.getHeight();
		List<Sample> samples = new ArrayList<>();
		try {
			collect(geo, w, h, bit, samples);
		} catch (RuntimeException e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not read {} for the Iron Man assembly: {}", geometry, e.toString());
			src.close();
			return new ResourceLocation[0];
		}
		float[] time = timeTable(samples, w * h, bit, fromCase);

		ResourceLocation[] frames = new ResourceLocation[FRAMES];
		String tag = base.getPath().replaceAll("[^a-z0-9_]", "_") + "_" + bit + (fromCase ? "_case" : "");
		float frame = 1f / FRAMES;
		for (int k = 0; k < FRAMES; k++) {
			float p = k / (float) FRAMES;
			NativeImage img = new NativeImage(w, h, true);
			img.copyFrom(src);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					float t = time[y * w + x];
					if (Float.isNaN(t)) {
						continue;
					}
					int orig = src.getPixelRGBA(x, y);
					if (t > p) {
						img.setPixelRGBA(x, y, 0);
					} else if (((orig >>> 24) & 0xFF) != 0) {
						float age = p - t;
						if (age < frame) {
							img.setPixelRGBA(x, y, argbToAbgr(SEAM_HOT));
						} else if (age < 3f * frame) {
							img.setPixelRGBA(x, y, blendAbgr(orig, argbToAbgr(SEAM_CYAN), age < 2f * frame ? 0.6f : 0.3f));
						}
					}
				}
			}
			ResourceLocation id = ProjectHeroMod.id("dynamic/ironman_assembly/" + tag + "_" + k);
			mc.getTextureManager().register(id, new DynamicTexture(img));
			frames[k] = id;
		}
		src.close();
		return frames;
	}

	/**
	 * Each texel's flip-on time in piece progress: the bone's start + {@link IronManAssemblyPlan#TILE_END} of its motion x
	 * the tile's place in the bone's order (distance from the reactor / the case hand, 75%, plus a 25% scatter).
	 */
	static float[] timeTable(List<Sample> samples, int size, int bit, boolean fromCase) {
		float[] origin = fromCase ? IronManAssemblyPlan.CASE_HAND : IronManAssemblyPlan.REACTOR;
		Vector3f o = new Vector3f(origin[0], origin[1], origin[2]);
		// per (bone, tile): mean distance
		Map<String, Map<Integer, float[]>> tiles = new HashMap<>();
		for (Sample s : samples) {
			float[] acc = tiles.computeIfAbsent(s.bone(), b -> new HashMap<>()).computeIfAbsent(s.tile(), t -> new float[2]);
			acc[0] += s.pos().distance(o);
			acc[1] += 1f;
		}
		Map<String, float[]> range = new HashMap<>();
		tiles.forEach((bone, m) -> {
			float lo = Float.POSITIVE_INFINITY;
			float hi = Float.NEGATIVE_INFINITY;
			for (float[] acc : m.values()) {
				float d = acc[0] / acc[1];
				lo = Math.min(lo, d);
				hi = Math.max(hi, d);
			}
			range.put(bone, new float[] { lo, hi });
		});
		float[] time = new float[size];
		Arrays.fill(time, Float.NaN);
		for (Sample s : samples) {
			float start = IronManAssemblyPlan.start(bit, s.bone(), fromCase);
			float t;
			if (start < 0f) {
				t = IronManAssemblyPlan.END; // an unplanned bone of this piece: plated at the very end
			} else {
				float[] acc = tiles.get(s.bone()).get(s.tile());
				float[] r = range.get(s.bone());
				float k = r[1] - r[0] < 1e-4f ? 0f : (acc[0] / acc[1] - r[0]) / (r[1] - r[0]);
				float scatter = IronManAssemblyPlan.hash(s.bone(), 100 + s.tile());
				k = Math.min(1f, Math.max(0f, 0.75f * k + 0.25f * scatter));
				t = start + IronManAssemblyPlan.MOTION * IronManAssemblyPlan.TILE_END * k;
			}
			int i = s.index();
			if (Float.isNaN(time[i]) || t > time[i]) {
				time[i] = t;
			}
		}
		return time;
	}

	// ---------------- geometry -> per-texel bone + rest position ----------------

	private static void collect(JsonObject root, int texW, int texH, int bit, List<Sample> out) {
		JsonObject model = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
		JsonObject desc = model.getAsJsonObject("description");
		float geoW = desc.has("texture_width") ? desc.get("texture_width").getAsFloat() : 64f;
		float geoH = desc.has("texture_height") ? desc.get("texture_height").getAsFloat() : 64f;
		float su = texW / geoW;
		float sv = texH / geoH;
		for (JsonElement b : model.getAsJsonArray("bones")) {
			JsonObject bone = b.getAsJsonObject();
			String name = planBone(bone.get("name").getAsString());
			int piece = IronManAssemblyPlan.pieceOf(name);
			if (!bone.has("cubes") || piece != bit) {
				continue;
			}
			for (JsonElement ce : bone.getAsJsonArray("cubes")) {
				JsonObject cube = ce.getAsJsonObject();
				Vector3f o = vec(cube.getAsJsonArray("origin"));
				Vector3f s = vec(cube.getAsJsonArray("size"));
				float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0f;
				Vector3f lo = new Vector3f(o).sub(inflate, inflate, inflate);
				Vector3f hi = new Vector3f(o).add(s).add(inflate, inflate, inflate);
				boolean baseCube = IronManAssemblyPlan.isBaseCube(cube.has("name") ? cube.get("name").getAsString() : null);
				for (Face f : faces(cube, s)) {
					paint(f, lo, hi, su, sv, texW, texH, name, baseCube, out);
				}
			}
		}
	}

	private record Face(float u0, float v0, float du, float dv, float[] corner, float[] alongU, float[] alongV) {
	}

	private static Face[] faces(JsonObject cube, Vector3f s) {
		float[] tnw = {0, 1, 0};
		float[] tne = {1, 1, 0};
		float[] tsw = {0, 1, 1};
		float[] tse = {1, 1, 1};
		float[] xPos = {1, 0, 0};
		float[] xNeg = {-1, 0, 0};
		float[] zPos = {0, 0, 1};
		float[] zNeg = {0, 0, -1};
		float[] down = {0, -1, 0};
		JsonElement uv = cube.get("uv");
		if (uv != null && uv.isJsonArray()) {
			float u = uv.getAsJsonArray().get(0).getAsFloat();
			float v = uv.getAsJsonArray().get(1).getAsFloat();
			float sx = s.x, sy = s.y, sz = s.z;
			return new Face[] {
					new Face(u, v + sz, sz, sy, tse, zNeg, down),
					new Face(u + sz, v + sz, sx, sy, tne, xNeg, down),
					new Face(u + sz + sx, v + sz, sz, sy, tnw, zPos, down),
					new Face(u + 2 * sz + sx, v + sz, sx, sy, tsw, xPos, down),
					new Face(u + sz, v, sx, sz, tsw, xPos, zNeg),
					new Face(u + sz + sx, v, sx, sz, new float[] {0, 0, 1}, xPos, zNeg),
			};
		}
		if (uv == null || !uv.isJsonObject()) {
			return new Face[0];
		}
		JsonObject per = uv.getAsJsonObject();
		List<Face> out = new ArrayList<>();
		addPerFace(out, per, "east", tse, zNeg, down);
		addPerFace(out, per, "north", tne, xNeg, down);
		addPerFace(out, per, "west", tnw, zPos, down);
		addPerFace(out, per, "south", tsw, xPos, down);
		addPerFace(out, per, "up", tsw, xPos, zNeg);
		addPerFace(out, per, "down", new float[] {0, 0, 1}, xPos, zNeg);
		return out.toArray(new Face[0]);
	}

	private static void addPerFace(List<Face> out, JsonObject per, String name, float[] corner, float[] au, float[] av) {
		if (!per.has(name)) {
			return;
		}
		JsonObject f = per.getAsJsonObject(name);
		JsonArray uv = f.getAsJsonArray("uv");
		JsonArray size = f.getAsJsonArray("uv_size");
		if (uv == null || size == null) {
			return;
		}
		out.add(new Face(uv.get(0).getAsFloat(), uv.get(1).getAsFloat(), size.get(0).getAsFloat(), size.get(1).getAsFloat(),
				corner, au, av));
	}

	private static void paint(Face f, Vector3f lo, Vector3f hi, float su, float sv, int texW, int texH, String bone,
			boolean baseCube, List<Sample> out) {
		float u0 = Math.min(f.u0, f.u0 + f.du) * su;
		float u1 = Math.max(f.u0, f.u0 + f.du) * su;
		float v0 = Math.min(f.v0, f.v0 + f.dv) * sv;
		float v1 = Math.max(f.v0, f.v0 + f.dv) * sv;
		Vector3f ext = new Vector3f(hi).sub(lo);
		// v0.14.27 halves: a wide cube (head, torso) splits left | right down its centre line, a limb top | bottom
		boolean splitX = ext.x >= 7.5f;
		for (int ty = (int) Math.floor(v0); ty < (int) Math.ceil(v1); ty++) {
			for (int tx = (int) Math.floor(u0); tx < (int) Math.ceil(u1); tx++) {
				if (tx < 0 || ty < 0 || tx >= texW || ty >= texH) {
					continue;
				}
				float fu = f.du == 0 ? 0.5f : ((tx + 0.5f) / su - f.u0) / f.du;
				float fv = f.dv == 0 ? 0.5f : ((ty + 0.5f) / sv - f.v0) / f.dv;
				fu = Math.max(0f, Math.min(1f, fu));
				fv = Math.max(0f, Math.min(1f, fv));
				float cx = f.corner[0] + f.alongU[0] * fu + f.alongV[0] * fv;
				float cy = f.corner[1] + f.alongU[1] * fu + f.alongV[1] * fv;
				float cz = f.corner[2] + f.alongU[2] * fu + f.alongV[2] * fv;
				Vector3f p = new Vector3f(lo.x + ext.x * cx, lo.y + ext.y * cy, lo.z + ext.z * cz);
				int tile = (ty / TILE) * 1024 + (tx / TILE);
				float fromSeam = splitX ? Math.abs(cx - 0.5f) * 2f : Math.abs(cy - 0.5f) * 2f;
				out.add(new Sample(bone, ty * texW + tx, tile, p, baseCube, fromSeam));
			}
		}
	}

	private static Vector3f vec(JsonArray a) {
		return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
	}

	private static int argbToAbgr(int argb) {
		int a = argb >>> 24;
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		return (a << 24) | (b << 16) | (g << 8) | r;
	}

	private static int blendAbgr(int x, int y, float f) {
		int a = (x >>> 24) & 0xFF;
		int r = 0;
		for (int sh = 0; sh <= 16; sh += 8) {
			int cx = (x >> sh) & 0xFF;
			int cy = (y >> sh) & 0xFF;
			r |= Math.round(cx + (cy - cx) * f) << sh;
		}
		return (a << 24) | r;
	}
}
