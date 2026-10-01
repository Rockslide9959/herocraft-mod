package com.projecthero.mod.client.render;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.ProjectHeroMod;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * v0.13.21: a suit that sweeps onto the body one pixel row at a time, top to bottom -- the geometry-aware sibling of
 * {@code SymbioteDissolve} (same trick: pre-built copies of the suit texture with some pixels cleared, swapped in by
 * {@code SuperheroArmorRenderer#getRenderType}; nothing about the model or the Symbiote / Moon Knight paths changes).
 *
 * <p>The difference is the reveal order. A texture's UV layout says nothing about where a pixel sits on the body, so
 * each texel's height is derived from the armour's own Bedrock geometry: for every cube, each face's UV rectangle is
 * mapped back onto the 3D face (box UV or per-face UV, inflate, cube rotation about its pivot, and the parent-bone rest
 * rotations), and a texel takes the height of the point it lands on. Heights are bucketed into model-pixel rows
 * counted down from the top of the suit; frame {@code k} shows rows {@code 0..k-1}, and the newest row is painted as
 * a bright {@link #EDGE_ARGB} scan line so the sweep reads as light being laid down. {@link #texture} with progress
 * running 1 -> 0 plays it backwards (the suit dissolves from the feet up).
 *
 * <p>Built lazily on the render thread, once per (geometry, texture) pair, and kept for the session. Texels no cube
 * face uses keep their original alpha in every frame -- nothing renders them anyway.
 */
public final class ArmorSweepReveal {
	/** Lantern-green scan line (ARGB), drawn over the newest revealed row. */
	public static final int EDGE_ARGB = 0xFF9DFFB8;
	private static final Map<String, ResourceLocation[]> CACHE = new HashMap<>();

	private ArmorSweepReveal() {
	}

	/**
	 * The texture to draw for a suit that is {@code progress} (0..1) of the way on. Returns {@code base} unchanged at
	 * full progress, or if the geometry/texture could not be read.
	 */
	public static ResourceLocation texture(ResourceLocation geometry, ResourceLocation base, float progress) {
		return texture(geometry, base, progress, Sweep.WHOLE_SUIT_DOWN);
	}

	/**
	 * v0.14.4: how a sweep runs -- which bones take part ({@code null} = all; a bone is in if it or any ancestor is
	 * listed, and texels of other bones keep their original alpha), which way it travels, and the colours of the newest
	 * row ({@code edgeArgb}) and the row just behind it ({@code trailArgb}, 0 = none). {@code key} names the cache
	 * entry. Used by Thor's Armour to sweep each piece up the body on its own, with a crackling blue-white edge.
	 */
	public record Sweep(String key, java.util.Set<String> bones, boolean bottomUp, int edgeArgb, int trailArgb, Vector3f origin) {
		public static final Sweep WHOLE_SUIT_DOWN = new Sweep("all", null, false, EDGE_ARGB, 0);

		public Sweep(String key, java.util.Set<String> bones, boolean bottomUp, int edgeArgb, int trailArgb) {
			this(key, bones, bottomUp, edgeArgb, trailArgb, null);
		}

		/**
		 * v0.14.11: a sweep that spreads outward from {@code origin} (model units, the geometry's own space) by straight-line
		 * distance instead of running up or down the body -- the Flash Suit pouring out of the ring on the right hand.
		 */
		public static Sweep radial(String key, Vector3f origin, int edgeArgb, int trailArgb) {
			return new Sweep(key, null, false, edgeArgb, trailArgb, origin);
		}
	}

	/** {@link #texture(ResourceLocation, ResourceLocation, float)} with an explicit {@link Sweep}. */
	public static ResourceLocation texture(ResourceLocation geometry, ResourceLocation base, float progress, Sweep sweep) {
		if (progress >= 0.999f) {
			return base;
		}
		ResourceLocation[] frames = CACHE.computeIfAbsent(geometry + "|" + base + "|" + sweep.key(), k -> build(geometry, base, sweep));
		if (frames.length == 0) {
			return base;
		}
		int step = Math.max(0, Math.min(frames.length - 1, (int) Math.floor(progress * frames.length)));
		return frames[step];
	}

	private static ResourceLocation[] build(ResourceLocation geometry, ResourceLocation base, Sweep sweep) {
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
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not build the suit sweep for {}: {}", base, e.toString());
			return new ResourceLocation[0];
		}
		int w = src.getWidth();
		int h = src.getHeight();
		float[] height = new float[w * h];
		java.util.Arrays.fill(height, Float.NaN);
		try {
			mapHeights(geo, w, h, height, sweep.bones(), sweep.origin());
		} catch (RuntimeException e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not read the geometry {} for the suit sweep: {}", geometry, e.toString());
			src.close();
			return new ResourceLocation[0];
		}
		float top = Float.NEGATIVE_INFINITY;
		float bottom = Float.POSITIVE_INFINITY;
		for (float v : height) {
			if (!Float.isNaN(v)) {
				top = Math.max(top, v);
				bottom = Math.min(bottom, v);
			}
		}
		if (top == Float.NEGATIVE_INFINITY) {
			src.close();
			return new ResourceLocation[0];
		}
		int rows = Math.max(1, (int) Math.ceil(top - bottom) + 1);
		int[] row = new int[w * h];
		for (int i = 0; i < row.length; i++) {
			float fromStart = sweep.bottomUp() ? height[i] - bottom : top - height[i];
			row[i] = Float.isNaN(height[i]) ? -1 : Math.min(rows - 1, (int) Math.floor(fromStart));
		}
		ResourceLocation[] frames = new ResourceLocation[rows + 1];
		String tag = base.getNamespace() + "_" + base.getPath().replaceAll("[^a-z0-9_]", "_")
				+ ("all".equals(sweep.key()) ? "" : "_" + sweep.key().replaceAll("[^a-z0-9_]", "_"));
		for (int k = 0; k <= rows; k++) {
			NativeImage img = new NativeImage(w, h, true);
			img.copyFrom(src);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int r = row[y * w + x];
					if (r < 0) {
						continue;
					}
					boolean opaque = ((src.getPixelRGBA(x, y) >>> 24) & 0xFF) != 0;
					if (r >= k) {
						img.setPixelRGBA(x, y, 0);
					} else if (r == k - 1 && opaque) {
						img.setPixelRGBA(x, y, argbToAbgr(sweep.edgeArgb()));
					} else if (r == k - 2 && opaque && sweep.trailArgb() != 0 && k < rows) {
						img.setPixelRGBA(x, y, argbToAbgr(sweep.trailArgb()));
					}
				}
			}
			ResourceLocation id = ProjectHeroMod.id("dynamic/suit_sweep/" + tag + "_" + k);
			mc.getTextureManager().register(id, new DynamicTexture(img));
			frames[k] = id;
		}
		src.close();
		return frames;
	}

	/** NativeImage#setPixelRGBA actually takes ABGR. */
	private static int argbToAbgr(int argb) {
		int a = argb >>> 24;
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		return (a << 24) | (b << 16) | (g << 8) | r;
	}

	// ---------------- geometry -> texel heights ----------------

	private static void mapHeights(JsonObject root, int texW, int texH, float[] height, java.util.Set<String> only,
			Vector3f origin) {
		JsonObject model = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
		JsonObject desc = model.getAsJsonObject("description");
		float geoW = desc.has("texture_width") ? desc.get("texture_width").getAsFloat() : 64f;
		float geoH = desc.has("texture_height") ? desc.get("texture_height").getAsFloat() : 64f;
		float su = texW / geoW;
		float sv = texH / geoH;

		Map<String, JsonObject> bones = new HashMap<>();
		JsonArray boneList = model.getAsJsonArray("bones");
		for (JsonElement b : boneList) {
			bones.put(b.getAsJsonObject().get("name").getAsString(), b.getAsJsonObject());
		}
		for (JsonElement b : boneList) {
			JsonObject bone = b.getAsJsonObject();
			if (!bone.has("cubes") || (only != null && !inSet(bone, bones, only))) {
				continue;
			}
			Matrix4f boneMatrix = boneRest(bone, bones);
			for (JsonElement ce : bone.getAsJsonArray("cubes")) {
				JsonObject cube = ce.getAsJsonObject();
				Vector3f o = vec(cube.getAsJsonArray("origin"));
				Vector3f s = vec(cube.getAsJsonArray("size"));
				float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0f;
				Matrix4f m = new Matrix4f(boneMatrix);
				if (cube.has("rotation")) {
					Vector3f pivot = cube.has("pivot") ? vec(cube.getAsJsonArray("pivot")) : new Vector3f(o).add(s.x / 2, s.y / 2, s.z / 2);
					m.mul(rotationAbout(pivot, vec(cube.getAsJsonArray("rotation"))));
				}
				Vector3f lo = new Vector3f(o).sub(inflate, inflate, inflate);
				Vector3f hi = new Vector3f(o).add(s).add(inflate, inflate, inflate);
				for (Face f : faces(cube, s)) {
					paintFace(f, lo, hi, m, su, sv, texW, texH, height, origin);
				}
			}
		}
	}

	/** Is {@code bone}, or any of its ancestors, one of {@code names}? */
	private static boolean inSet(JsonObject bone, Map<String, JsonObject> bones, java.util.Set<String> names) {
		int guard = 0;
		for (JsonObject b = bone; b != null && guard++ < 64; b = b.has("parent") ? bones.get(b.get("parent").getAsString()) : null) {
			if (names.contains(b.get("name").getAsString())) {
				return true;
			}
		}
		return false;
	}

	/** The accumulated rest rotation of {@code bone} and its parents (each about its own pivot). */
	private static Matrix4f boneRest(JsonObject bone, Map<String, JsonObject> bones) {
		Matrix4f m = new Matrix4f();
		java.util.ArrayDeque<JsonObject> chain = new java.util.ArrayDeque<>();
		for (JsonObject b = bone; b != null; b = b.has("parent") ? bones.get(b.get("parent").getAsString()) : null) {
			chain.push(b);
			if (chain.size() > 64) {
				break;
			}
		}
		for (JsonObject b : chain) {
			if (b.has("rotation")) {
				Vector3f pivot = b.has("pivot") ? vec(b.getAsJsonArray("pivot")) : new Vector3f();
				m.mul(rotationAbout(pivot, vec(b.getAsJsonArray("rotation"))));
			}
		}
		return m;
	}

	private static Matrix4f rotationAbout(Vector3f pivot, Vector3f degrees) {
		return new Matrix4f().translate(pivot)
				.rotateZ((float) Math.toRadians(degrees.z))
				.rotateY((float) Math.toRadians(degrees.y))
				.rotateX((float) Math.toRadians(degrees.x))
				.translate(-pivot.x, -pivot.y, -pivot.z);
	}

	/**
	 * One face: its UV rectangle (u0,v0 = the corner that lands on {@code corner}, du/dv signed), and the two 3D axes
	 * that walking along u / v moves across, as fractions of the cube (0..1 along x/y/z of lo..hi).
	 */
	private record Face(float u0, float v0, float du, float dv, float[] corner, float[] alongU, float[] alongV) {
	}

	private static Face[] faces(JsonObject cube, Vector3f s) {
		// fractional cube coordinates: {x, y, z} with 0 = lo, 1 = hi
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
					// Bedrock box UV: [east][north][west][south] strip below [up][down]
					new Face(u, v + sz, sz, sy, tse, zNeg, down),               // east (+x), walking from south to north
					new Face(u + sz, v + sz, sx, sy, tne, xNeg, down),          // north (-z), from +x to -x
					new Face(u + sz + sx, v + sz, sz, sy, tnw, zPos, down),     // west (-x), from north to south
					new Face(u + 2 * sz + sx, v + sz, sx, sy, tsw, xPos, down), // south (+z), from -x to +x
					new Face(u + sz, v, sx, sz, tsw, xPos, zNeg),               // up
					new Face(u + sz + sx, v, sx, sz, new float[] {0, 0, 1}, xPos, zNeg), // down
			};
		}
		if (uv == null || !uv.isJsonObject()) {
			return new Face[0];
		}
		JsonObject per = uv.getAsJsonObject();
		java.util.List<Face> out = new java.util.ArrayList<>();
		addPerFace(out, per, "east", tse, zNeg, down);
		addPerFace(out, per, "north", tne, xNeg, down);
		addPerFace(out, per, "west", tnw, zPos, down);
		addPerFace(out, per, "south", tsw, xPos, down);
		addPerFace(out, per, "up", tsw, xPos, zNeg);
		addPerFace(out, per, "down", new float[] {0, 0, 1}, xPos, zNeg);
		return out.toArray(new Face[0]);
	}

	private static void addPerFace(java.util.List<Face> out, JsonObject per, String name, float[] corner, float[] au, float[] av) {
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

	/**
	 * Writes each texel's sweep value: its height, or (v0.14.11, {@code origin} given) minus its distance from
	 * {@code origin}, so "highest" is always "first" and the top-down bucketing works unchanged.
	 */
	private static void paintFace(Face f, Vector3f lo, Vector3f hi, Matrix4f m, float su, float sv, int texW, int texH,
			float[] height, Vector3f origin) {
		float u0 = Math.min(f.u0, f.u0 + f.du) * su;
		float u1 = Math.max(f.u0, f.u0 + f.du) * su;
		float v0 = Math.min(f.v0, f.v0 + f.dv) * sv;
		float v1 = Math.max(f.v0, f.v0 + f.dv) * sv;
		Vector3f ext = new Vector3f(hi).sub(lo);
		for (int ty = (int) Math.floor(v0); ty < (int) Math.ceil(v1); ty++) {
			for (int tx = (int) Math.floor(u0); tx < (int) Math.ceil(u1); tx++) {
				if (tx < 0 || ty < 0 || tx >= texW || ty >= texH) {
					continue;
				}
				// fraction across the face, measured from the texel that sits on the face's anchor corner
				float fu = f.du == 0 ? 0.5f : ((tx + 0.5f) / su - f.u0) / f.du;
				float fv = f.dv == 0 ? 0.5f : ((ty + 0.5f) / sv - f.v0) / f.dv;
				fu = Math.max(0f, Math.min(1f, fu));
				fv = Math.max(0f, Math.min(1f, fv));
				float cx = f.corner[0] + f.alongU[0] * fu + f.alongV[0] * fv;
				float cy = f.corner[1] + f.alongU[1] * fu + f.alongV[1] * fv;
				float cz = f.corner[2] + f.alongU[2] * fu + f.alongV[2] * fv;
				Vector3f p = new Vector3f(lo.x + ext.x * cx, lo.y + ext.y * cy, lo.z + ext.z * cz);
				m.transformPosition(p);
				int i = ty * texW + tx;
				float value = origin == null ? p.y : -p.distance(origin);
				// a texel shared by several faces appears with the highest of them
				if (Float.isNaN(height[i]) || value > height[i]) {
					height[i] = value;
				}
			}
		}
	}

	private static Vector3f vec(JsonArray a) {
		return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
	}
}
