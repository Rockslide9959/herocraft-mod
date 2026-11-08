package com.projecthero.mod.client.titanshifter;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.render.VanillaSkeleton;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

/**
 * v0.17.3 (user: "for the player's skeleton use the same model as the skeleton mob in Minecraft, do the same for the Titan
 * corpse skeletons"): the bones under a Titan corpse's (and a severed limb chunk's) dissolving flesh are the vanilla
 * skeleton mob ({@link VanillaSkeleton}: skull, ribcage, thin arm and leg bones, skeleton.png) instead of the rig's own
 * skeleton-layer cubes, which are no longer drawn there.
 *
 * <p>Every Titan rig is player-proportioned (32 px: an 8 px head split into head + jaw, a 12 px body, 12 px arms split at
 * the elbow, 12 px legs split at the knee and ankle), so each vanilla part maps 1:1 onto it: a rig bone draws the band of
 * the matching vanilla part that lies inside it ({@link #SEGMENTS}), in that bone's animated pose -- the skeleton kneels,
 * bows and lies exactly as the corpse does, bent at the elbows and knees, at the corpse's scale. The skull and the ribcage
 * are scaled in a little so the rig's muscle layer covers them until it dissolves.
 *
 * <p>While a renderer draws, {@link #collect} records each bone's pose; after the model is drawn {@link #draw} draws the
 * bones in one batch. The skeleton leaves last, like the rig's own did: over the span of dissolve times of the rig's
 * skeleton texels, the vanilla texture's texels go in a fixed scattered order (limbs from the far end in, then the body,
 * the skull last), through a set of shared 64 x 32 frames.
 */
public final class TitanBones {
	/** A rig bone's share of a vanilla part: the band {@code y0..y1} (vanilla part px), moved {@code dx} px sideways. */
	private record Segment(int part, float y0, float y1, float dx) {
	}

	private static final Map<String, Segment> SEGMENTS = Map.ofEntries(
			Map.entry("head", new Segment(VanillaSkeleton.HEAD, -8f, -2f, 0f)),
			Map.entry("jaw", new Segment(VanillaSkeleton.HEAD, -2f, 0f, 0f)),
			Map.entry("body", new Segment(VanillaSkeleton.BODY, 0f, 12f, 0f)),
			// the rig's arms are centred 6 px out (the player's), the skeleton's 5
			Map.entry("right_arm", new Segment(VanillaSkeleton.RIGHT_ARM, -2f, 4f, -1f)),
			Map.entry("right_forearm", new Segment(VanillaSkeleton.RIGHT_ARM, 4f, 10f, -1f)),
			Map.entry("left_arm", new Segment(VanillaSkeleton.LEFT_ARM, -2f, 4f, 1f)),
			Map.entry("left_forearm", new Segment(VanillaSkeleton.LEFT_ARM, 4f, 10f, 1f)),
			// and its legs 1.9 out, the skeleton's 2
			Map.entry("right_leg", new Segment(VanillaSkeleton.RIGHT_LEG, 0f, 6f, 0.1f)),
			Map.entry("right_shin", new Segment(VanillaSkeleton.RIGHT_LEG, 6f, 10.5f, 0.1f)),
			Map.entry("right_foot", new Segment(VanillaSkeleton.RIGHT_LEG, 10.5f, 12f, 0.1f)),
			Map.entry("left_leg", new Segment(VanillaSkeleton.LEFT_LEG, 0f, 6f, -0.1f)),
			Map.entry("left_shin", new Segment(VanillaSkeleton.LEFT_LEG, 6f, 10.5f, -0.1f)),
			Map.entry("left_foot", new Segment(VanillaSkeleton.LEFT_LEG, 10.5f, 12f, -0.1f)));

	/** The skull and ribcage fit inside the rig's muscle layer (inset 0.3 px from the 8 px head / body). */
	private static final float SKULL = 0.86f, RIBS_X = 0.86f, RIBS_Z = 0.78f;

	/** Dissolve frames of the vanilla texture (shared by every corpse). */
	private static final int STEPS = 64;
	private static final ResourceLocation[] FRAMES = new ResourceLocation[STEPS + 1];
	private static NativeImage source;
	private static float[] order;

	/** Per rig: is a cube one of its skeleton-layer cubes? (by the layer of the texels its faces use) */
	private static final Map<GeoCube, Boolean> SKELETON_CUBE = new WeakHashMap<>();
	private static final Map<TitanTexels, float[]> RANGES = new IdentityHashMap<>();

	private TitanBones() {
	}

	/** The bone poses of one render. */
	public static final class Pending {
		private final List<Matrix4f> poses = new ArrayList<>();
		private final List<Matrix3f> normals = new ArrayList<>();
		private final List<Segment> segments = new ArrayList<>();

		public void clear() {
			poses.clear();
			normals.clear();
			segments.clear();
		}
	}

	/** True for a rig cube of the skeleton layer (no longer drawn on a corpse or a severed limb: the vanilla bones are). */
	public static boolean isSkeletonCube(TitanTexels tx, GeoCube cube) {
		Boolean b = SKELETON_CUBE.get(cube);
		if (b == null) {
			b = classify(tx, cube);
			SKELETON_CUBE.put(cube, b);
		}
		return b;
	}

	private static boolean classify(TitanTexels tx, GeoCube cube) {
		if (tx == null) {
			return false;
		}
		int skeleton = 0, other = 0;
		boolean[] layer0 = skeletonPixels(tx);
		for (GeoQuad q : cube.quads()) {
			if (q == null || q.vertices().length == 0) {
				continue;
			}
			float u = 0f, v = 0f;
			for (GeoVertex vx : q.vertices()) {
				u += vx.texU();
				v += vx.texV();
			}
			int x = (int) (u / q.vertices().length * tx.w), y = (int) (v / q.vertices().length * tx.h);
			if (x < 0 || y < 0 || x >= tx.w || y >= tx.h) {
				continue;
			}
			int i = y * tx.w + x;
			if (layer0[i]) {
				skeleton++;
			} else if (usedPixels(tx)[i]) {
				other++;
			}
		}
		return skeleton > 0 && skeleton >= other;
	}

	private static final Map<TitanTexels, boolean[][]> PIXELS = new IdentityHashMap<>();

	private static boolean[][] pixels(TitanTexels tx) {
		return PIXELS.computeIfAbsent(tx, t -> {
			boolean[] l0 = new boolean[t.w * t.h], used = new boolean[t.w * t.h];
			for (int k = 0; k < t.count; k++) {
				used[t.index[k]] = true;
				if (t.layer[k] == 0) {
					l0[t.index[k]] = true;
				}
			}
			return new boolean[][] { l0, used };
		});
	}

	private static boolean[] skeletonPixels(TitanTexels tx) {
		return pixels(tx)[0];
	}

	private static boolean[] usedPixels(TitanTexels tx) {
		return pixels(tx)[1];
	}

	/**
	 * The span of corpse-dissolve times of the rig's skeleton texels: {@code [min, max]} for the whole body ({@code limb} 0)
	 * or one limb bit's.
	 */
	private static float[] range(TitanTexels tx, int limb) {
		float[] all = RANGES.computeIfAbsent(tx, t -> {
			float[] r = new float[10];
			for (int i = 0; i < 5; i++) {
				r[i * 2] = 1f;
				r[i * 2 + 1] = 0f;
			}
			for (int k = 0; k < t.count; k++) {
				if (t.layer[k] != 0) {
					continue;
				}
				float d = t.dissolve[k];
				int slot = t.limb[k] == 0 ? -1 : TitanTexels.limbSlot(t.limb[k]);
				r[0] = Math.min(r[0], d);
				r[1] = Math.max(r[1], d);
				if (slot >= 0) {
					int s = slot + 1;
					r[s * 2] = Math.min(r[s * 2], d);
					r[s * 2 + 1] = Math.max(r[s * 2 + 1], d);
				}
			}
			for (int i = 0; i < 5; i++) {
				if (r[i * 2] > r[i * 2 + 1]) {
					r[i * 2] = 0.7f; // no skeleton texels: a sensible span anyway
					r[i * 2 + 1] = 1f;
				}
			}
			return r;
		});
		int s = limb == 0 ? 0 : TitanTexels.limbSlot(limb) + 1;
		return new float[] { all[s * 2], all[s * 2 + 1] };
	}

	/** How far through the skeleton's own dissolve a corpse-dissolve time {@code t} is (0 whole .. 1 gone). */
	public static float skeletonGone(TitanTexels tx, int limb, float t) {
		if (tx == null || t <= 0f) {
			return 0f;
		}
		if (t >= 1f) {
			return 1f;
		}
		float[] r = range(tx, limb);
		return Math.max(0f, Math.min(1f, (t - r[0]) / Math.max(1.0e-4f, r[1] - r[0])));
	}

	/** Records {@code bone}'s pose (the pose stack as GeckoLib has it for the bone's cubes) if it carries a vanilla bone. */
	public static void collect(Pending pending, GeoBone bone, PoseStack poseStack) {
		Segment s = SEGMENTS.get(bone.getName());
		if (s == null || bone.isHidden()) {
			return;
		}
		pending.poses.add(new Matrix4f(poseStack.last().pose()));
		pending.normals.add(new Matrix3f(poseStack.last().normal()));
		pending.segments.add(s);
	}

	/** Draws the recorded bones, {@code gone} (0..1) of the skeleton dissolved away. */
	public static void draw(Pending pending, PoseStack poseStack, MultiBufferSource buffers, int light, float gone) {
		if (pending.segments.isEmpty() || gone >= 1f) {
			pending.clear();
			return;
		}
		ResourceLocation tex = frame(gone);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
		for (int i = 0; i < pending.segments.size(); i++) {
			Segment s = pending.segments.get(i);
			poseStack.pushPose();
			poseStack.last().pose().set(pending.poses.get(i));
			poseStack.last().normal().set(pending.normals.get(i));
			// rig space (geo px with x mirrored, y up from the feet) -> the vanilla model's (y down from the neck)
			poseStack.translate(0f, 1.5f, 0f);
			poseStack.scale(-1f, -1f, 1f);
			float[] o = VanillaSkeleton.offset(s.part);
			poseStack.translate((o[0] + s.dx) / 16f, o[1] / 16f, o[2] / 16f);
			if (s.part == VanillaSkeleton.HEAD) {
				poseStack.translate(0f, -4f / 16f, 0f);
				poseStack.scale(SKULL, SKULL, SKULL);
				poseStack.translate(0f, 4f / 16f, 0f);
			} else if (s.part == VanillaSkeleton.BODY) {
				poseStack.translate(0f, 6f / 16f, 0f);
				poseStack.scale(RIBS_X, 0.97f, RIBS_Z);
				poseStack.translate(0f, -6f / 16f, 0f);
			}
			VanillaSkeleton.draw(poseStack.last(), vc, s.part, s.y0, s.y1, light, OverlayTexture.NO_OVERLAY, -1);
			poseStack.popPose();
		}
		pending.clear();
	}

	// ---------------------------------------------------------------- the vanilla texture dissolving

	private static ResourceLocation frame(float gone) {
		int k = Math.max(0, Math.min(STEPS, (int) Math.floor(gone * STEPS)));
		if (k == 0) {
			return VanillaSkeleton.TEXTURE;
		}
		if (FRAMES[k] != null) {
			return FRAMES[k];
		}
		if (!loadSource()) {
			return VanillaSkeleton.TEXTURE;
		}
		NativeImage img = new NativeImage(source.getWidth(), source.getHeight(), true);
		img.copyFrom(source);
		float cut = k / (float) STEPS;
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				if (order[y * img.getWidth() + x] < cut) {
					img.setPixelRGBA(x, y, 0);
				}
			}
		}
		ResourceLocation id = ProjectHeroMod.id("titan_bones/frame_" + k);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
		FRAMES[k] = id;
		return id;
	}

	private static boolean loadSource() {
		if (source != null) {
			return true;
		}
		try (InputStream in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(VanillaSkeleton.TEXTURE).open()) {
			NativeImage img = NativeImage.read(in);
			int w = img.getWidth(), h = img.getHeight();
			float[] o = new float[w * h];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					o[y * w + x] = order(x * 64 / w, y * 32 / h);
				}
			}
			order = o;
			source = img;
			return true;
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not read the skeleton texture: {}", e.toString());
			return false;
		}
	}

	/** When a texel of skeleton.png (in its 64 x 32 layout) goes: limbs from the far end in, then the body, the skull last. */
	private static float order(int x, int y) {
		float hash = (float) ((((x * 73856093) ^ (y * 19349663) ^ 0x5bd1e995) * 0x27d4eb2d >>> 8) & 0xFFFF) / 65535f;
		float bias;
		if (y < 16) {
			bias = 0.8f; // the skull
		} else if (x < 16 || (x >= 40 && x < 56)) {
			bias = 0.55f * (1f - (y - 16) / 16f); // a leg or an arm: the far end (low on the strip) first
		} else {
			bias = 0.55f; // the ribcage
		}
		return Math.max(0f, Math.min(0.999f, 0.6f * bias + 0.4f * hash));
	}

	/** Resource reload: the frames are rebuilt on demand. */
	public static void reset() {
		for (int i = 0; i < FRAMES.length; i++) {
			if (FRAMES[i] != null) {
				Minecraft.getInstance().getTextureManager().release(FRAMES[i]);
				FRAMES[i] = null;
			}
		}
		if (source != null) {
			source.close();
			source = null;
		}
		RANGES.clear();
		PIXELS.clear();
		SKELETON_CUBE.clear();
	}
}
