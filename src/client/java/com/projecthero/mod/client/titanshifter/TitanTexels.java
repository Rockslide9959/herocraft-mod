package com.projecthero.mod.client.titanshifter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.16.7 (user, Titan revamp) -- the per-texel data of one Titan body, baked by the asset generator:
 * {@code titan_texels/<model>.bin} (one record per cube face: atlas rect, bone, layer, rest-pose corners) and
 * {@code titan_texels/<model>_times.png} (R = build time, G = dissolve time, B = layer x 100, A = 255 on used texels).
 * <p>
 * Every texel knows which bone it is on, which layer (0 skeleton, 1 muscle, 2 skin), where it sits in the rest pose
 * (model px, geo space), when it appears during the transformation (the skeleton from the spine outward, then the
 * muscle, then the skin) and when it leaves the corpse (skin, muscle, skeleton). Loaded lazily, once per model.
 */
public final class TitanTexels {
	public static final int LIMB_R_ARM = 1, LIMB_L_ARM = 2, LIMB_R_LEG = 4, LIMB_L_LEG = 8;

	public final int w;
	public final int h;
	public final String[] boneNames;
	public final Map<String, Integer> boneIndex = new HashMap<>();
	/** Anchor name -> {bone index, x, y, z} (rest pose, model px). */
	public final Map<String, float[]> anchors = new HashMap<>();
	public final int count;
	/** Atlas pixel index (y * w + x) of each texel. */
	public final int[] index;
	public final byte[] bone;
	public final byte[] layer;
	/** The limb bit ({@link #LIMB_R_ARM}...) a texel belongs to, or 0. */
	public final byte[] limb;
	public final float[] build;
	public final float[] dissolve;
	public final float[] px;
	public final float[] py;
	public final float[] pz;
	/** Texel ids sorted by build / dissolve time, and those times in that order (for binary searches). */
	public final int[] byBuild;
	public final float[] buildSorted;
	public final int[] byDissolve;
	public final float[] dissolveSorted;
	/** Per limb bit (index 0..3): the earliest build time of its texels, so a regrowing limb starts from its own first bone. */
	public final float[] limbBuildMin = new float[4];

	private static final Map<String, TitanTexels> CACHE = new HashMap<>();

	private TitanTexels(int w, int h, String[] boneNames, int count) {
		this.w = w;
		this.h = h;
		this.boneNames = boneNames;
		for (int i = 0; i < boneNames.length; i++) {
			boneIndex.put(boneNames[i], i);
		}
		this.count = count;
		index = new int[count];
		bone = new byte[count];
		layer = new byte[count];
		limb = new byte[count];
		build = new float[count];
		dissolve = new float[count];
		px = new float[count];
		py = new float[count];
		pz = new float[count];
		byBuild = new int[count];
		buildSorted = new float[count];
		byDissolve = new int[count];
		dissolveSorted = new float[count];
	}

	/** The data of {@code model} (e.g. "titan_form"), or null if it is missing or broken. */
	public static TitanTexels get(String model) {
		if (CACHE.containsKey(model)) {
			return CACHE.get(model);
		}
		TitanTexels t = null;
		try {
			t = load(model);
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not load the Titan texel data for {}: {}", model, e.toString());
		}
		CACHE.put(model, t);
		return t;
	}

	public static void clear() {
		CACHE.clear();
	}

	public static int limbBitOf(String boneName) {
		if (boneName.startsWith("right_arm") || boneName.equals("right_forearm")) {
			return boneName.endsWith("stump") ? 0 : LIMB_R_ARM;
		}
		if (boneName.startsWith("left_arm") || boneName.equals("left_forearm")) {
			return boneName.endsWith("stump") ? 0 : LIMB_L_ARM;
		}
		if (boneName.equals("right_leg") || boneName.equals("right_shin") || boneName.equals("right_foot")) {
			return LIMB_R_LEG;
		}
		if (boneName.equals("left_leg") || boneName.equals("left_shin") || boneName.equals("left_foot")) {
			return LIMB_L_LEG;
		}
		return 0;
	}

	public static int limbSlot(int bit) {
		return Integer.numberOfTrailingZeros(bit);
	}

	private static TitanTexels load(String model) throws IOException {
		Minecraft mc = Minecraft.getInstance();
		ResourceLocation binId = ProjectHeroMod.id("titan_texels/" + model + ".bin");
		ResourceLocation timesId = ProjectHeroMod.id("titan_texels/" + model + "_times.png");
		byte[] raw;
		try (InputStream in = mc.getResourceManager().getResourceOrThrow(binId).open()) {
			raw = in.readAllBytes();
		}
		ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
		byte[] magic = new byte[4];
		b.get(magic);
		if (magic[0] != 'T' || magic[1] != 'T' || magic[2] != 'X' || magic[3] != '1') {
			throw new IOException("bad magic");
		}
		b.getInt(); // version
		int w = b.getInt(), h = b.getInt();
		String[] names = new String[b.getInt()];
		for (int i = 0; i < names.length; i++) {
			names[i] = str(b);
		}
		int anchorCount = b.getInt();
		Map<String, float[]> anchors = new HashMap<>();
		for (int i = 0; i < anchorCount; i++) {
			String n = str(b);
			int bone = b.get() & 0xFF;
			anchors.put(n, new float[] { bone, b.getFloat(), b.getFloat(), b.getFloat() });
		}
		int faces = b.getInt();
		int[][] rect = new int[faces][];
		float[][] corners = new float[faces][];
		for (int f = 0; f < faces; f++) {
			int u = b.getShort() & 0xFFFF, v = b.getShort() & 0xFFFF, fw = b.getShort() & 0xFFFF, fh = b.getShort() & 0xFFFF;
			int bone = b.get() & 0xFF, layer = b.get() & 0xFF;
			b.get(); // corpse-only flag (the renderer hides those bones on the living Titan)
			rect[f] = new int[] { u, v, fw, fh, bone, layer };
			float[] c = new float[9];
			for (int k = 0; k < 9; k++) {
				c[k] = b.getFloat();
			}
			corners[f] = c;
		}
		NativeImage times;
		try (InputStream in = mc.getResourceManager().getResourceOrThrow(timesId).open()) {
			times = NativeImage.read(in);
		}
		try {
			int n = 0;
			for (int[] r : rect) {
				for (int j = 0; j < r[3]; j++) {
					for (int i = 0; i < r[2]; i++) {
						if ((times.getPixelRGBA(r[0] + i, r[1] + j) >>> 24) != 0) {
							n++;
						}
					}
				}
			}
			TitanTexels t = new TitanTexels(w, h, names, n);
			t.anchors.putAll(anchors);
			byte[] limbOfBone = new byte[names.length];
			for (int i = 0; i < names.length; i++) {
				limbOfBone[i] = (byte) limbBitOf(names[i]);
			}
			Arrays.fill(t.limbBuildMin, 1.0f);
			int k = 0;
			for (int f = 0; f < faces; f++) {
				int[] r = rect[f];
				float[] c = corners[f];
				for (int j = 0; j < r[3]; j++) {
					for (int i = 0; i < r[2]; i++) {
						int x = r[0] + i, y = r[1] + j;
						int abgr = times.getPixelRGBA(x, y);
						if ((abgr >>> 24) == 0) {
							continue;
						}
						int idx = y * w + x;
						float s = (i + 0.5f) / r[2], tt = (j + 0.5f) / r[3];
						t.index[k] = idx;
						t.bone[k] = (byte) r[4];
						t.layer[k] = (byte) r[5];
						t.limb[k] = limbOfBone[r[4]];
						// 8-bit times, spread inside their step by a per-texel hash so nothing pops in batches
						float jit = hash(idx);
						t.build[k] = ((abgr & 0xFF) + jit) / 256.0f;
						t.dissolve[k] = (((abgr >> 8) & 0xFF) + hash(idx * 31 + 7)) / 256.0f;
						t.px[k] = c[0] + s * (c[3] - c[0]) + tt * (c[6] - c[0]);
						t.py[k] = c[1] + s * (c[4] - c[1]) + tt * (c[7] - c[1]);
						t.pz[k] = c[2] + s * (c[5] - c[2]) + tt * (c[8] - c[2]);
						if (t.limb[k] != 0) {
							int slot = limbSlot(t.limb[k]);
							t.limbBuildMin[slot] = Math.min(t.limbBuildMin[slot], t.build[k]);
						}
						k++;
					}
				}
			}
			sort(t.build, t.byBuild, t.buildSorted);
			sort(t.dissolve, t.byDissolve, t.dissolveSorted);
			return t;
		} finally {
			times.close();
		}
	}

	private static void sort(float[] key, int[] order, float[] sorted) {
		int n = key.length;
		long[] packed = new long[n];
		for (int i = 0; i < n; i++) {
			packed[i] = ((long) Float.floatToIntBits(key[i]) << 32) | i; // keys are >= 0, so the bits sort like the floats
		}
		Arrays.sort(packed);
		for (int i = 0; i < n; i++) {
			order[i] = (int) packed[i];
			sorted[i] = key[order[i]];
		}
	}

	/** Index of the first entry in {@code sorted} strictly greater than {@code v}. */
	public static int upper(float[] sorted, float v) {
		int lo = 0, hi = sorted.length;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (sorted[mid] <= v) {
				lo = mid + 1;
			} else {
				hi = mid;
			}
		}
		return lo;
	}

	private static float hash(int v) {
		int x = v * 0x9E3779B1;
		x ^= x >>> 15;
		x *= 0x85EBCA6B;
		x ^= x >>> 13;
		return (x >>> 8) / 16777216.0f;
	}

	private static String str(ByteBuffer b) {
		int n = b.get() & 0xFF;
		byte[] s = new byte[n];
		b.get(s);
		return new String(s, java.nio.charset.StandardCharsets.US_ASCII);
	}

}
