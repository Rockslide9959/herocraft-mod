package com.projecthero.mod.client.shazam.journey;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Shazam acquisition: <b>the illusion of the subway stairway</b>, drawn only for the player it appears to.
 *
 * <p>v2 (user: "it look like a green lantern construct ... make it look better"): a real, solid, old city subway entrance that is
 * only subtly otherworldly. In its own frame (origin at the top step's near edge on the ground, +z down the stairs):
 * <ul>
 * <li>a stone-brick kerb with a smooth-stone coping round three sides and a flush stone threshold at the entrance, so the grass
 * meets stone;</li>
 * <li>black-green wrought-iron railings on the kerb (balusters with spear finials, posts with ball finials);</li>
 * <li>two cast-iron lamp posts at the entrance with warm glowing globes and a soft halo;</li>
 * <li>a crossbar between them with a dark-green enamel sign, cream border and cream "SUBWAY", and a small cream "Eternity
 * Line" plate hung under it;</li>
 * <li>below ground, seen through a depth "hole" ({@link JourneyRenderTypes#HOLE}): eight worn stone steps (smooth-stone treads,
 * slab-side risers) between white subway-tiled walls with a green tile band, a landing, a stone-framed doorway and a short
 * corridor beyond. The light falls off with depth per vertex (both the colour and the lightmap), daylight at the top,
 * shadowed tiles, then darkness, so the corridor melts into black instead of ending in a hard rectangle; a faint warm
 * light glimmers deep inside, as if the station is far below.</li>
 * <li>otherworldly, gently: a gold shimmer rippling up off the kerb's edges, slow golden motes drifting up out of the
 * stairwell.</li>
 * </ul>
 * Everything is drawn at full opacity once it has materialised: it comes out of a gold shimmer with a few flickers over about a
 * second and a half ({@link #materialise}) and fades out softly. Lit by the world (the sky light at the spot, plus the lamps'
 * own warm block light), so it sits in daylight and glows at night.
 */
final class IllusionRenderer {
	private static final ResourceLocation KERB = ResourceLocation.withDefaultNamespace("textures/block/stone_bricks.png");
	private static final ResourceLocation COPING = ResourceLocation.withDefaultNamespace("textures/block/smooth_stone.png");
	private static final ResourceLocation TREAD = ResourceLocation.withDefaultNamespace("textures/block/smooth_stone.png");
	private static final ResourceLocation RISER = ResourceLocation.withDefaultNamespace("textures/block/smooth_stone_slab_side.png");
	private static final ResourceLocation TILES = ResourceLocation.withDefaultNamespace("textures/block/quartz_bricks.png");
	private static final ResourceLocation FLOOR = ResourceLocation.withDefaultNamespace("textures/block/polished_andesite.png");
	private static final ResourceLocation FRAME = ResourceLocation.withDefaultNamespace("textures/block/polished_deepslate.png");
	private static final ResourceLocation IRON = ResourceLocation.withDefaultNamespace("textures/block/iron_block.png");
	private static final ResourceLocation PAINT = ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
	private static final ResourceLocation DARK = ResourceLocation.withDefaultNamespace("textures/block/black_concrete.png");

	static final int STEP_COUNT = 8;
	static final float STEP = 0.5f;
	static final float HALF_W = 1.5f;
	static final float DEPTH = STEP_COUNT * STEP;
	static final float LANDING = STEP_COUNT * STEP + 1.6f;
	/** The corridor beyond the doorway, fading to black. */
	static final float CORRIDOR = 3.6f;
	static final float DOOR_HALF = 0.7f;
	static final float DOOR_TOP = -DEPTH + 2.15f;
	static final float KERB_W = 0.3f;
	static final float KERB_H = 0.3f;
	static final float RAIL_X = HALF_W + KERB_W / 2f;
	static final float RAIL_H = 0.95f;
	/** Ticks the illusion takes to come out of its shimmer. */
	static final float MATERIALISE = 32f;

	private static final int IRON_RGB = 0x1E2A24; // black-green paint on iron
	private static final int CREAM = 0xEDE3C2;
	private static final int ENAMEL = 0x1B4A34;
	private static final int GOLD = 0xFFD27A;
	private static final int WARM = 0xFFC877;

	private IllusionRenderer() {
	}

	/** The lighting the illusion stands in: the spot's sky and block light (0..15) and how dark the sky is (0 day .. 1 night). */
	record Lighting(int sky, int block, float night) {
	}

	/**
	 * 0..1, how solid it is {@code age} ticks after it appeared: out of a shimmer (nothing for the first few ticks), eased in with
	 * a few flickers early on.
	 */
	static float materialise(float age) {
		float k = Mth.clamp((age - 6f) / (MATERIALISE - 6f), 0f, 1f);
		float m = k * k * (3 - 2 * k);
		if (k > 0f && k < 0.85f) {
			double h = hash(Math.floor(age / 1.5) * 1.37 + 0.5);
			if (h < 0.32 * (1 - k)) {
				m *= 0.35f; // a flicker as it tries to hold
			}
		}
		return m;
	}

	/**
	 * Draws the illusion at {@code at} descending toward {@code yaw}. {@code solid} 0..1 is how materialised it is (the
	 * stairs, kerb, railings and lamps), {@code shimmer} 0..1 the gold shimmer's strength, {@code t} its age in ticks (the
	 * ripple, the motes, the lamps).
	 */
	static void draw(PoseStack ps, MultiBufferSource.BufferSource buffers, Camera camera, Vec3 at, float yaw, float solid, float shimmer, float t,
			Lighting lit) {
		if (solid <= 0.01f && shimmer <= 0.01f) {
			return;
		}
		Vec3 cam = camera.getPosition();
		ps.pushPose();
		ps.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		ps.mulPose(Axis.YP.rotationDegrees(-yaw));
		PoseStack.Pose pose = ps.last();
		float a = Mth.clamp(solid, 0f, 1f);
		// the lamps' warm light on everything near the surface (they come on with it)
		int lampBlock = Math.round(11 * a);
		int surfBlock = Math.max(lit.block, lampBlock);
		int surface = LightTexture.pack(surfBlock, lit.sky);

		if (a > 0.01f) {
			stairwell(pose, buffers, a, t, lit, lampBlock);
			kerb(pose, buffers, a, surface);
			railings(pose, buffers, a, surface);
			lamps(pose, buffers, a, surface);
			sign(ps, buffers, a, surface);
		}
		ps.popPose();

		// ---- the light: halos, the glimmer below, the gold shimmer and the motes (additive, camera-relative, unrotated)
		ps.pushPose();
		ps.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		Quaternionf rot = new Quaternionf().rotationY((float) Math.toRadians(-yaw));
		Vector3f left = new Vector3f(camera.getLeftVector()), up = new Vector3f(camera.getUpVector());
		Matrix4f m = ps.last().pose();
		VertexConsumer glow = buffers.getBuffer(JourneyRenderTypes.GLOW);
		if (a > 0.01f) {
			float halo = a * (0.16f + 0.34f * lit.night) * (0.94f + 0.06f * Mth.sin(t * 0.31f));
			for (int side = -1; side <= 1; side += 2) {
				halo(glow, m, local(rot, side * RAIL_X, 2.82f, -0.15f), left, up, 1.05f, WARM, halo);
				halo(glow, m, local(rot, side * RAIL_X, 2.82f, -0.15f), left, up, 0.38f, 0xFFF1D0, halo * 1.3f);
			}
			// the station far below: a faint warm glimmer deep in the corridor, breathing slowly
			float deep = a * (0.30f + 0.12f * Mth.sin(t * 0.07f) + 0.05f * Mth.sin(t * 0.53f));
			halo(glow, m, local(rot, 0f, -DEPTH + 0.85f, LANDING + CORRIDOR - 0.25f), left, up, 0.9f, 0xFFB45A, deep);
			halo(glow, m, local(rot, 0f, -DEPTH + 0.85f, LANDING + CORRIDOR - 0.25f), left, up, 0.3f, 0xFFE0A0, deep * 0.9f);
		}
		edgeShimmer(glow, m, rot, shimmer, t);
		motes(glow, m, rot, left, up, Math.max(a, shimmer) * 0.85f, t);
		buffers.endBatch(JourneyRenderTypes.GLOW);
		ps.popPose();
	}

	// ---------------------------------------------------------------- below ground

	private static void stairwell(PoseStack.Pose pose, MultiBufferSource.BufferSource buffers, float a, float t, Lighting lit, int lampBlock) {
		// push the depth back to the far plane inside the opening, so what follows shows only through it, like a real hole
		Matrix4f mat = pose.pose();
		VertexConsumer hole = buffers.getBuffer(JourneyRenderTypes.HOLE);
		hole.addVertex(mat, -HALF_W, 0.003f, 0f).setColor(0, 0, 0, 255);
		hole.addVertex(mat, HALF_W, 0.003f, 0f).setColor(0, 0, 0, 255);
		hole.addVertex(mat, HALF_W, 0.003f, LANDING).setColor(0, 0, 0, 255);
		hole.addVertex(mat, -HALF_W, 0.003f, LANDING).setColor(0, 0, 0, 255);
		buffers.endBatch(JourneyRenderTypes.HOLE);

		Under u = new Under(pose, a, lit, lampBlock);

		// the side walls: stone under the kerb, a green tile band, then white subway tiles down to the steps
		RenderType tilesType = RenderType.entityTranslucent(TILES);
		VertexConsumer tiles = buffers.getBuffer(tilesType);
		RenderType kerbType = RenderType.entityTranslucent(KERB);
		for (int side = -1; side <= 1; side += 2) {
			float x = side * HALF_W;
			boolean facePlus = side < 0;
			for (int i = 0; i <= STEP_COUNT; i++) {
				float z0 = i * STEP, z1 = i < STEP_COUNT ? (i + 1) * STEP : LANDING;
				float bottom = -Math.min(DEPTH, (i + 1) * STEP);
				// white tiles, small (two tiles per texture repeat over half a block)
				u.wallX(tiles, x, facePlus, z0, z1, bottom, -0.42f, 0xF2F0E8, 2f);
				u.wallX(tiles, x, facePlus, z0, z1, -0.42f, -0.24f, 0x3E7A58, 2f);
			}
		}
		// the end wall round the doorway, in tiles
		u.wallZ(tiles, LANDING, false, -HALF_W, -DOOR_HALF, -DEPTH, -0.24f, 0xF2F0E8, 2f);
		u.wallZ(tiles, LANDING, false, DOOR_HALF, HALF_W, -DEPTH, -0.24f, 0xF2F0E8, 2f);
		u.wallZ(tiles, LANDING, false, -DOOR_HALF, DOOR_HALF, DOOR_TOP, -0.42f, 0xF2F0E8, 2f);
		u.wallZ(tiles, LANDING, false, -HALF_W, HALF_W, -0.42f, -0.24f, 0x3E7A58, 2f);
		// the corridor's walls and ceiling, the same tiles sinking into the dark
		u.corridorWall(tiles, -DOOR_HALF, true);
		u.corridorWall(tiles, DOOR_HALF, false);
		u.corridorFlat(tiles, DOOR_TOP, false, 0xF2F0E8, 2f);
		buffers.endBatch(tilesType);

		VertexConsumer kerbIn = buffers.getBuffer(kerbType);
		for (int side = -1; side <= 1; side += 2) {
			u.wallX(kerbIn, side * HALF_W, side < 0, 0f, LANDING, -0.24f, 0f, 0xC8C8C8, 1f);
		}
		u.wallZ(kerbIn, LANDING, false, -HALF_W, HALF_W, -0.24f, 0f, 0xC8C8C8, 1f);
		buffers.endBatch(kerbType);

		// the doorway's stone frame and the floors
		RenderType frameType = RenderType.entityTranslucent(FRAME);
		VertexConsumer frame = buffers.getBuffer(frameType);
		float fz = LANDING - 0.06f, fw = 0.14f;
		u.box(frame, -DOOR_HALF - fw, -DEPTH, fz, -DOOR_HALF, DOOR_TOP + fw, LANDING, 0x8A8A90);
		u.box(frame, DOOR_HALF, -DEPTH, fz, DOOR_HALF + fw, DOOR_TOP + fw, LANDING, 0x8A8A90);
		u.box(frame, -DOOR_HALF - fw, DOOR_TOP, fz, DOOR_HALF + fw, DOOR_TOP + fw, LANDING, 0x8A8A90);
		buffers.endBatch(frameType);
		RenderType floorType = RenderType.entityTranslucent(FLOOR);
		VertexConsumer floor = buffers.getBuffer(floorType);
		u.flat(floor, -HALF_W, DEPTH, HALF_W, LANDING, -DEPTH, true, 0xB8B4AC, 1f);
		u.corridorFlat(floor, -DEPTH, true, 0xB8B4AC, 1f);
		buffers.endBatch(floorType);

		// the far end of the corridor: black, the same as the corridor's last colour, so there's no edge to see
		RenderType darkType = RenderType.entityTranslucent(DARK);
		VertexConsumer dark = buffers.getBuffer(darkType);
		u.wallZ(dark, LANDING + CORRIDOR, false, -DOOR_HALF, DOOR_HALF, -DEPTH, DOOR_TOP, 0x000000, 1f);
		buffers.endBatch(darkType);

		// the steps: worn smooth-stone treads (a darker path down the middle), slab-side risers
		RenderType treadType = RenderType.entityTranslucent(TREAD);
		VertexConsumer tread = buffers.getBuffer(treadType);
		for (int i = STEP_COUNT - 1; i >= 0; i--) {
			float z0 = i * STEP, z1 = (i + 1) * STEP, top = -(i + 1) * STEP;
			// so the steps read from above: the back of each tread lies in the shadow of the step above it, its worn front
			// edge (the nosing) catches the light
			float zs = z0 + 0.14f, zn = z1 - 0.07f;
			u.flat(tread, -HALF_W, z0, HALF_W, zs, top, true, 0x6E6B66, 1f);
			u.flat(tread, -HALF_W, zs, -0.55f, zn, top, true, 0xD6D3CC, 1f);
			u.flat(tread, -0.55f, zs, 0.55f, zn, top, true, 0xB9B5AD, 1f);
			u.flat(tread, 0.55f, zs, HALF_W, zn, top, true, 0xD6D3CC, 1f);
			u.flat(tread, -HALF_W, zn, HALF_W, z1, top, true, 0xF4F1EA, 1f);
		}
		buffers.endBatch(treadType);
		RenderType riserType = RenderType.entityTranslucent(RISER);
		VertexConsumer riser = buffers.getBuffer(riserType);
		for (int i = STEP_COUNT - 1; i >= 0; i--) {
			float z0 = i * STEP, top = -(i + 1) * STEP;
			u.wallZ(riser, z0, true, -HALF_W, HALF_W, top, top + STEP, 0xC4C0B8, 1f); // faces down the stairs
		}
		buffers.endBatch(riserType);
	}

	/** Quads below ground: per-vertex colour and light both fall off with depth (and into the corridor, to black). */
	private static final class Under {
		final PoseStack.Pose pose;
		final float a;
		final Lighting lit;
		final int lampBlock;

		Under(PoseStack.Pose pose, float a, Lighting lit, int lampBlock) {
			this.pose = pose;
			this.a = a;
			this.lit = lit;
			this.lampBlock = lampBlock;
		}

		/** 0 at the surface .. 1 at the landing. */
		private static float depth(float y) {
			return Mth.clamp(-y / DEPTH, 0f, 1f);
		}

		/** 0 at the doorway .. 1 at the corridor's end. */
		private static float into(float z) {
			return Mth.clamp((z - LANDING) / CORRIDOR, 0f, 1f);
		}

		void vertex(VertexConsumer vc, float x, float y, float z, float u, float v, int rgb, float nx, float ny, float nz) {
			float d = depth(y), k = into(z);
			float fall = (1f - 0.62f * (float) Math.pow(d, 1.25)) * (1f - k) * (1f - k);
			// a touch of warmth creeps in towards the bottom, from the station below
			float warm = 0.06f * d * (1f - k);
			int r = Mth.clamp((int) (((rgb >> 16) & 0xFF) * fall + 255 * warm), 0, 255);
			int g = Mth.clamp((int) (((rgb >> 8) & 0xFF) * fall + 170 * warm), 0, 255);
			int b = Mth.clamp((int) ((rgb & 0xFF) * fall + 80 * warm), 0, 255);
			int sky = Math.round(lit.sky * (1f - 0.7f * d) * (1f - k));
			int block = Math.round(Math.max(Math.max(lit.block, lampBlock) * (1f - 0.8f * d), 3f * d) * (1f - k));
			vc.addVertex(pose.pose(), x, y, z).setColor(r, g, b, Mth.clamp((int) (a * 255), 0, 255)).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(LightTexture.pack(Mth.clamp(block, 0, 15), Mth.clamp(sky, 0, 15))).setNormal(pose, nx, ny, nz);
		}

		/** A wall in the plane x, from z0..z1 and y0..y1, facing +x if {@code plus}; {@code rep} texture repeats per block. */
		void wallX(VertexConsumer vc, float x, boolean plus, float z0, float z1, float y0, float y1, int rgb, float rep) {
			float u0 = z0 * rep, u1 = z1 * rep, v0 = -y1 * rep, v1 = -y0 * rep;
			float n = plus ? 1 : -1;
			if (plus) {
				vertex(vc, x, y0, z1, u1, v1, rgb, n, 0, 0);
				vertex(vc, x, y0, z0, u0, v1, rgb, n, 0, 0);
				vertex(vc, x, y1, z0, u0, v0, rgb, n, 0, 0);
				vertex(vc, x, y1, z1, u1, v0, rgb, n, 0, 0);
			} else {
				vertex(vc, x, y0, z0, u0, v1, rgb, n, 0, 0);
				vertex(vc, x, y0, z1, u1, v1, rgb, n, 0, 0);
				vertex(vc, x, y1, z1, u1, v0, rgb, n, 0, 0);
				vertex(vc, x, y1, z0, u0, v0, rgb, n, 0, 0);
			}
		}

		/** A wall in the plane z, x0..x1 and y0..y1, facing +z if {@code plus}. */
		void wallZ(VertexConsumer vc, float z, boolean plus, float x0, float x1, float y0, float y1, int rgb, float rep) {
			float u0 = x0 * rep, u1 = x1 * rep, v0 = -y1 * rep, v1 = -y0 * rep;
			float n = plus ? 1 : -1;
			if (plus) {
				vertex(vc, x0, y0, z, u0, v1, rgb, 0, 0, n);
				vertex(vc, x1, y0, z, u1, v1, rgb, 0, 0, n);
				vertex(vc, x1, y1, z, u1, v0, rgb, 0, 0, n);
				vertex(vc, x0, y1, z, u0, v0, rgb, 0, 0, n);
			} else {
				vertex(vc, x1, y0, z, u1, v1, rgb, 0, 0, n);
				vertex(vc, x0, y0, z, u0, v1, rgb, 0, 0, n);
				vertex(vc, x0, y1, z, u0, v0, rgb, 0, 0, n);
				vertex(vc, x1, y1, z, u1, v0, rgb, 0, 0, n);
			}
		}

		/** A floor (up) or ceiling in the plane y. */
		void flat(VertexConsumer vc, float x0, float z0, float x1, float z1, float y, boolean upFace, int rgb, float rep) {
			float u0 = x0 * rep, u1 = x1 * rep, v0 = z0 * rep, v1 = z1 * rep;
			if (upFace) {
				vertex(vc, x0, y, z1, u0, v1, rgb, 0, 1, 0);
				vertex(vc, x1, y, z1, u1, v1, rgb, 0, 1, 0);
				vertex(vc, x1, y, z0, u1, v0, rgb, 0, 1, 0);
				vertex(vc, x0, y, z0, u0, v0, rgb, 0, 1, 0);
			} else {
				vertex(vc, x0, y, z0, u0, v0, rgb, 0, -1, 0);
				vertex(vc, x1, y, z0, u1, v0, rgb, 0, -1, 0);
				vertex(vc, x1, y, z1, u1, v1, rgb, 0, -1, 0);
				vertex(vc, x0, y, z1, u0, v1, rgb, 0, -1, 0);
			}
		}

		/** The corridor's side wall at x (in slices, so the fade to black is smooth). */
		void corridorWall(VertexConsumer vc, float x, boolean plus) {
			for (int i = 0; i < 4; i++) {
				float z0 = LANDING + CORRIDOR * i / 4f, z1 = LANDING + CORRIDOR * (i + 1) / 4f;
				wallX(vc, x, plus, z0, z1, -DEPTH, DOOR_TOP, 0xF2F0E8, 2f);
			}
		}

		void corridorFlat(VertexConsumer vc, float y, boolean upFace, int rgb, float rep) {
			for (int i = 0; i < 4; i++) {
				float z0 = LANDING + CORRIDOR * i / 4f, z1 = LANDING + CORRIDOR * (i + 1) / 4f;
				flat(vc, -DOOR_HALF, z0, DOOR_HALF, z1, y, upFace, rgb, rep);
			}
		}

		void box(VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
			wallZ(vc, z0, false, x0, x1, y0, y1, rgb, 1f);
			wallX(vc, x0, false, z0, z1, y0, y1, rgb, 1f);
			wallX(vc, x1, true, z0, z1, y0, y1, rgb, 1f);
			flat(vc, x0, z0, x1, z1, y0, false, rgb, 1f);
		}
	}

	// ---------------------------------------------------------------- above ground

	/** The stone kerb round three sides, its coping, and the flush threshold at the entrance. */
	private static void kerb(PoseStack.Pose pose, MultiBufferSource.BufferSource buffers, float a, int light) {
		RenderType kerbType = RenderType.entityTranslucent(KERB);
		VertexConsumer kerb = buffers.getBuffer(kerbType);
		int stone = argb(a, 0xC6C6C6);
		float x0 = HALF_W, x1 = HALF_W + KERB_W, far = LANDING + KERB_W;
		for (int side = -1; side <= 1; side += 2) {
			float lo = side < 0 ? -x1 : x0, hi = side < 0 ? -x0 : x1;
			box(kerb, pose, lo, 0f, -0.05f, hi, KERB_H - 0.05f, far, stone, light, 2f);
		}
		box(kerb, pose, -HALF_W, 0f, LANDING, HALF_W, KERB_H - 0.05f, far, stone, light, 2f);
		buffers.endBatch(kerbType);
		RenderType copeType = RenderType.entityTranslucent(COPING);
		VertexConsumer cope = buffers.getBuffer(copeType);
		int top = argb(a, 0xD8D6D0);
		for (int side = -1; side <= 1; side += 2) {
			float lo = side < 0 ? -x1 - 0.03f : x0 - 0.03f, hi = side < 0 ? -x0 + 0.03f : x1 + 0.03f;
			box(cope, pose, lo, KERB_H - 0.05f, -0.08f, hi, KERB_H, far + 0.03f, top, light, 1f);
		}
		box(cope, pose, -x0 + 0.03f, KERB_H - 0.05f, LANDING - 0.03f, x0 - 0.03f, KERB_H, far + 0.03f, top, light, 1f);
		// the threshold: a flush stone strip where the path meets the top step, wider than the opening
		box(cope, pose, -x1 - 0.05f, 0f, -0.45f, x1 + 0.05f, 0.018f, 0f, argb(a, 0xC9C6BE), light, 1f);
		buffers.endBatch(copeType);
	}

	/** Black-green wrought iron: balusters with spear finials, rails, and posts with ball finials, on the kerb. */
	private static void railings(PoseStack.Pose pose, MultiBufferSource.BufferSource buffers, float a, int light) {
		RenderType ironType = RenderType.entityTranslucent(IRON);
		VertexConsumer iron = buffers.getBuffer(ironType);
		int c = argb(a, IRON_RGB);
		float base = KERB_H, topY = base + RAIL_H;
		float end = LANDING + KERB_W / 2f;
		for (int side = -1; side <= 1; side += 2) {
			float x = side * RAIL_X;
			// rails: bottom, a decorative band, the top rail
			box(iron, pose, x - 0.03f, base + 0.04f, 0.1f, x + 0.03f, base + 0.09f, end, c, light, 1f);
			box(iron, pose, x - 0.025f, topY - 0.2f, 0.1f, x + 0.025f, topY - 0.17f, end, c, light, 1f);
			box(iron, pose, x - 0.045f, topY - 0.06f, 0.1f, x + 0.045f, topY, end, c, light, 1f);
			// balusters with spears
			for (float z = 0.3f; z < end - 0.1f; z += 0.22f) {
				box(iron, pose, x - 0.018f, base, z - 0.018f, x + 0.018f, topY, z + 0.018f, c, light, 0.1f);
				spear(iron, pose, x, topY, z, 0.035f, 0.13f, c, light);
			}
			// posts with ball finials: the entrance, the middle, the corner
			for (float z : new float[] { 0.1f, end / 2f, end }) {
				box(iron, pose, x - 0.055f, base, z - 0.055f, x + 0.055f, topY + 0.08f, z + 0.055f, c, light, 0.2f);
				ball(iron, pose, x, topY + 0.15f, z, 0.075f, c, light);
			}
		}
		// across the far end
		box(iron, pose, -RAIL_X, base + 0.04f, end - 0.03f, RAIL_X, base + 0.09f, end + 0.03f, c, light, 1f);
		box(iron, pose, -RAIL_X, topY - 0.2f, end - 0.025f, RAIL_X, topY - 0.17f, end + 0.025f, c, light, 1f);
		box(iron, pose, -RAIL_X, topY - 0.06f, end - 0.045f, RAIL_X, topY, end + 0.045f, c, light, 1f);
		for (float x = -RAIL_X + 0.24f; x < RAIL_X - 0.12f; x += 0.22f) {
			box(iron, pose, x - 0.018f, base, end - 0.018f, x + 0.018f, topY, end + 0.018f, c, light, 0.1f);
			spear(iron, pose, x, topY, end, 0.035f, 0.13f, c, light);
		}
		buffers.endBatch(ironType);
	}

	/** The two cast-iron lamp posts at the entrance, their crossbar, and the warm globes. */
	private static void lamps(PoseStack.Pose pose, MultiBufferSource.BufferSource buffers, float a, int light) {
		RenderType ironType = RenderType.entityTranslucent(IRON);
		VertexConsumer iron = buffers.getBuffer(ironType);
		int c = argb(a, IRON_RGB);
		float z = -0.15f;
		for (int side = -1; side <= 1; side += 2) {
			float x = side * RAIL_X;
			box(iron, pose, x - 0.17f, 0f, z - 0.17f, x + 0.17f, 0.12f, z + 0.17f, c, light, 0.3f); // foot
			box(iron, pose, x - 0.12f, 0.12f, z - 0.12f, x + 0.12f, 0.5f, z + 0.12f, c, light, 0.3f); // plinth
			box(iron, pose, x - 0.15f, 0.5f, z - 0.15f, x + 0.15f, 0.56f, z + 0.15f, c, light, 0.3f);
			box(iron, pose, x - 0.055f, 0.56f, z - 0.055f, x + 0.055f, 2.52f, z + 0.055f, c, light, 0.1f); // shaft
			box(iron, pose, x - 0.09f, 1.3f, z - 0.09f, x + 0.09f, 1.36f, z + 0.09f, c, light, 0.2f); // ring
			box(iron, pose, x - 0.13f, 2.52f, z - 0.13f, x + 0.13f, 2.6f, z + 0.13f, c, light, 0.3f); // collar
			box(iron, pose, x - 0.08f, 3.04f, z - 0.08f, x + 0.08f, 3.09f, z + 0.08f, c, light, 0.2f); // cap
			spear(iron, pose, x, 3.09f, z, 0.05f, 0.12f, c, light);
		}
		// the crossbar the sign hangs from, and its brackets
		box(iron, pose, -RAIL_X, 2.42f, z - 0.035f, RAIL_X, 2.49f, z + 0.035f, c, light, 1f);
		for (float x : new float[] { -0.9f, 0.9f }) {
			box(iron, pose, x - 0.015f, 2.36f, z - 0.015f, x + 0.015f, 2.42f, z + 0.015f, c, light, 0.1f);
		}
		buffers.endBatch(ironType);
		// the globes: stacked boxes rounded into a ball, glowing warm (full bright)
		RenderType glowType = JourneyRenderTypes.ghost(PAINT);
		VertexConsumer globe = buffers.getBuffer(glowType);
		int warm = argb(a, 0xFFE6B0);
		int full = LightTexture.FULL_BRIGHT;
		for (int side = -1; side <= 1; side += 2) {
			float x = side * RAIL_X, cy = 2.82f;
			box(globe, pose, x - 0.15f, cy - 0.22f, z - 0.15f, x + 0.15f, cy + 0.22f, z + 0.15f, warm, full, 1f);
			box(globe, pose, x - 0.22f, cy - 0.15f, z - 0.15f, x + 0.22f, cy + 0.15f, z + 0.15f, warm, full, 1f);
			box(globe, pose, x - 0.15f, cy - 0.15f, z - 0.22f, x + 0.15f, cy + 0.15f, z + 0.22f, warm, full, 1f);
			box(globe, pose, x - 0.19f, cy - 0.19f, z - 0.19f, x + 0.19f, cy + 0.19f, z + 0.19f, warm, full, 1f);
		}
		buffers.endBatch(glowType);
	}

	/** The enamel sign: dark green, a cream border and cream "SUBWAY"; under it a small cream "Eternity Line" plate. */
	private static void sign(PoseStack ps, MultiBufferSource.BufferSource buffers, float a, int light) {
		PoseStack.Pose pose = ps.last();
		RenderType paintType = RenderType.entityTranslucent(PAINT);
		VertexConsumer paint = buffers.getBuffer(paintType);
		float z = -0.15f;
		// the main sign: cream border, then the green field in front of it
		box(paint, pose, -1.18f, 1.9f, z - 0.03f, 1.18f, 2.36f, z + 0.03f, argb(a, CREAM), light, 1f);
		box(paint, pose, -1.13f, 1.94f, z - 0.04f, 1.13f, 2.32f, z + 0.04f, argb(a, ENAMEL), light, 1f);
		// the plate, hung on two short chains
		box(paint, pose, -0.6f, 1.62f, z - 0.025f, 0.6f, 1.8f, z + 0.025f, argb(a, ENAMEL), light, 1f);
		box(paint, pose, -0.57f, 1.64f, z - 0.03f, 0.57f, 1.78f, z + 0.03f, argb(a, CREAM), light, 1f);
		buffers.endBatch(paintType);
		RenderType ironType = RenderType.entityTranslucent(IRON);
		VertexConsumer iron = buffers.getBuffer(ironType);
		for (float x : new float[] { -0.42f, 0.42f }) {
			box(iron, pose, x - 0.012f, 1.8f, z - 0.012f, x + 0.012f, 1.9f, z + 0.012f, argb(a, IRON_RGB), light, 0.1f);
		}
		buffers.endBatch(ironType);

		// the words, on the face toward whoever approaches (-z)
		Font font = Minecraft.getInstance().font;
		int alpha = Mth.clamp((int) (a * 255), 8, 255);
		ps.pushPose();
		ps.translate(0, 2.13, z - 0.045);
		ps.mulPose(Axis.YP.rotationDegrees(180f));
		float scale = 0.03f;
		ps.scale(scale, -scale, scale);
		Component title = Component.translatable("sign.projecthero.journey.illusion.title").withStyle(ChatFormatting.BOLD);
		font.drawInBatch(title, -font.width(title) / 2f, -4.5f, (alpha << 24) | CREAM, false, ps.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0,
				light);
		ps.popPose();
		ps.pushPose();
		ps.translate(0, 1.71, z - 0.035);
		ps.mulPose(Axis.YP.rotationDegrees(180f));
		float small = 0.011f;
		ps.scale(small, -small, small);
		Component sub = Component.translatable("sign.projecthero.journey.illusion.line");
		font.drawInBatch(sub, -font.width(sub) / 2f, -4.5f, (alpha << 24) | ENAMEL, false, ps.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0,
				light);
		ps.popPose();
		buffers.endBatch();
	}

	// ---------------------------------------------------------------- the light

	/** A soft round glow facing the camera: a fan from {@code rgb} at {@code alpha} in the middle to nothing at the rim. */
	private static void halo(VertexConsumer vc, Matrix4f m, Vector3f c, Vector3f left, Vector3f up, float r, int rgb, float alpha) {
		if (alpha <= 0.005f) {
			return;
		}
		int ca = Mth.clamp((int) (alpha * 255), 0, 255);
		int cr = (rgb >> 16) & 0xFF, cg = (rgb >> 8) & 0xFF, cb = rgb & 0xFF;
		int n = 12;
		for (int i = 0; i < n; i++) {
			double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n, am = (a0 + a1) / 2;
			Vector3f e0 = rim(c, left, up, r, a0), e1 = rim(c, left, up, r, a1), em = rim(c, left, up, r, am);
			vc.addVertex(m, c.x, c.y, c.z).setColor(cr, cg, cb, ca);
			vc.addVertex(m, e0.x, e0.y, e0.z).setColor(cr, cg, cb, 0);
			vc.addVertex(m, em.x, em.y, em.z).setColor(cr, cg, cb, 0);
			vc.addVertex(m, e1.x, e1.y, e1.z).setColor(cr, cg, cb, 0);
		}
	}

	private static Vector3f rim(Vector3f c, Vector3f left, Vector3f up, float r, double ang) {
		float cs = (float) Math.cos(ang) * r, sn = (float) Math.sin(ang) * r;
		return new Vector3f(c.x + left.x * cs + up.x * sn, c.y + left.y * cs + up.y * sn, c.z + left.z * cs + up.z * sn);
	}

	/**
	 * A faint gold shimmer rising off the kerb's outer edges, rippling in slices -- the only sign, once it has materialised,
	 * that this entrance isn't really there. Strong while it materialises and fades.
	 */
	private static void edgeShimmer(VertexConsumer vc, Matrix4f m, Quaternionf rot, float strength, float t) {
		if (strength <= 0.005f) {
			return;
		}
		float ox = HALF_W + KERB_W + 0.04f, z0 = -0.5f, z1 = LANDING + KERB_W + 0.04f;
		float[][] runs = { { -ox, z0, -ox, z1 }, { ox, z0, ox, z1 }, { -ox, z1, ox, z1 }, { -ox, z0, ox, z0 } };
		int cr = (GOLD >> 16) & 0xFF, cg = (GOLD >> 8) & 0xFF, cb = GOLD & 0xFF;
		int seg = 0;
		for (float[] run : runs) {
			double len = Math.hypot(run[2] - run[0], run[3] - run[1]);
			int n = Math.max(1, (int) Math.ceil(len / 0.3));
			for (int i = 0; i < n; i++, seg++) {
				float k0 = i / (float) n, k1 = (i + 1) / (float) n;
				float x0 = Mth.lerp(k0, run[0], run[2]), zz0 = Mth.lerp(k0, run[1], run[3]);
				float x1 = Mth.lerp(k1, run[0], run[2]), zz1 = Mth.lerp(k1, run[1], run[3]);
				float ripple = 0.55f + 0.45f * Mth.sin(t * 0.13f + seg * 0.9f) * Mth.sin(t * 0.051f + seg * 0.37f);
				float h = 0.55f + 0.35f * ripple + 0.4f * strength;
				int al = Mth.clamp((int) (255 * strength * ripple * 0.5f), 0, 255);
				float sway = 0.04f * Mth.sin(t * 0.21f + seg);
				Vector3f b0 = local(rot, x0, 0.02f, zz0), b1 = local(rot, x1, 0.02f, zz1);
				Vector3f t1 = local(rot, x1 + sway, h, zz1), t0 = local(rot, x0 + sway, h, zz0);
				vc.addVertex(m, b0.x, b0.y, b0.z).setColor(cr, cg, cb, al);
				vc.addVertex(m, b1.x, b1.y, b1.z).setColor(cr, cg, cb, al);
				vc.addVertex(m, t1.x, t1.y, t1.z).setColor(cr, cg, cb, 0);
				vc.addVertex(m, t0.x, t0.y, t0.z).setColor(cr, cg, cb, 0);
			}
		}
	}

	/** A dozen slow golden motes drifting up out of the stairwell and over the entrance. */
	private static void motes(VertexConsumer vc, Matrix4f m, Quaternionf rot, Vector3f left, Vector3f up, float strength, float t) {
		if (strength <= 0.01f) {
			return;
		}
		for (int i = 0; i < 14; i++) {
			double s = i * 7.31 + 0.5;
			float span = 5.2f;
			float phase = (float) ((hash(s) + t * (0.0035 + 0.002 * hash(s + 1))) % 1.0);
			float y = -3.2f + phase * span;
			float x = (float) ((hash(s + 2) - 0.5) * 2.6) + 0.15f * Mth.sin(t * 0.04f + i);
			float z = (float) (0.3 + hash(s + 3) * (LANDING - 0.6)) + 0.12f * Mth.cos(t * 0.033f + i * 1.7f);
			float life = Mth.sin(phase * Mth.PI);
			float twinkle = 0.6f + 0.4f * Mth.sin(t * 0.25f + i * 2.1f);
			halo(vc, m, local(rot, x, y, z), left, up, 0.07f, GOLD, strength * life * twinkle * 0.9f);
		}
	}

	private static Vector3f local(Quaternionf rot, float x, float y, float z) {
		return rot.transform(new Vector3f(x, y, z));
	}

	// ---------------------------------------------------------------- shapes

	/** A box with every face's texture repeating {@code rep} times per block (so textures keep their scale). */
	private static void box(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, int argb, int light, float rep) {
		float w = (x1 - x0) * rep, h = (y1 - y0) * rep, d = (z1 - z0) * rep;
		Quads.wallZ(vc, pose, x0, y0, x1, y1, z1, true, 0, 0, w, h, argb, light);
		Quads.wallZ(vc, pose, x0, y0, x1, y1, z0, false, 0, 0, w, h, argb, light);
		Quads.wallX(vc, pose, z0, y0, z1, y1, x1, true, 0, 0, d, h, argb, light);
		Quads.wallX(vc, pose, z0, y0, z1, y1, x0, false, 0, 0, d, h, argb, light);
		Quads.flatY(vc, pose, x0, z0, x1, z1, y1, true, 0, 0, w, d, argb, light);
		Quads.flatY(vc, pose, x0, z0, x1, z1, y0, false, 0, 0, w, d, argb, light);
	}

	/** A four-sided spear point standing on (x, y, z). */
	private static void spear(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float r, float h, int argb, int light) {
		float tx = x, ty = y + h, tz = z;
		Quads.quad(vc, pose, x - r, y, z + r, x + r, y, z + r, tx, ty, tz, tx, ty, tz, 0, 0, 0.1f, 0.1f, argb, light, 0, 0.5f, 1);
		Quads.quad(vc, pose, x + r, y, z - r, x - r, y, z - r, tx, ty, tz, tx, ty, tz, 0, 0, 0.1f, 0.1f, argb, light, 0, 0.5f, -1);
		Quads.quad(vc, pose, x + r, y, z + r, x + r, y, z - r, tx, ty, tz, tx, ty, tz, 0, 0, 0.1f, 0.1f, argb, light, 1, 0.5f, 0);
		Quads.quad(vc, pose, x - r, y, z - r, x - r, y, z + r, tx, ty, tz, tx, ty, tz, 0, 0, 0.1f, 0.1f, argb, light, -1, 0.5f, 0);
	}

	/** A small ball finial (three crossed boxes). */
	private static void ball(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float r, int argb, int light) {
		float s = r * 0.7f;
		box(vc, pose, x - s, y - r, z - s, x + s, y + r, z + s, argb, light, 0.2f);
		box(vc, pose, x - r, y - s, z - s, x + r, y + s, z + s, argb, light, 0.2f);
		box(vc, pose, x - s, y - s, z - r, x + s, y + s, z + r, argb, light, 0.2f);
	}

	private static double hash(double seed) {
		return Math.abs(Math.sin(seed * 12.9898) * 43758.5453) % 1.0;
	}

	private static int argb(float a, int rgb) {
		return (Mth.clamp((int) (a * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
	}
}
