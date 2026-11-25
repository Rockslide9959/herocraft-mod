package com.projecthero.mod.client.shazam.journey;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.shazam.journey.JourneyGeometry;
import com.projecthero.mod.shazam.journey.SubwayTrainEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.7 Shazam acquisition: <b>the subway train</b>, drawn from quads on our own 256 px atlas
 * ({@code scratchpad/v0177/gen_shazam_journey_assets.js}): two 16-block stainless-steel cars with a red Shazam stripe and a gold
 * pinstripe, a window band of tinted glass, three pairs of sliding doors a side (they slide apart with the synced door clock),
 * a ribbed roof with equipment boxes, a dark skirt and two bogies with wheels under each car, and a bellows gangway between them.
 * The leading cab carries a lit amber destination sign ("ROCK OF ETERNITY"), two headlights with soft beams that reach down the
 * tunnel, and the trailing cab red marker lights. Inside, seen through the glass and the open doors: speckled floor, cream walls
 * with advert cards, a ceiling with bright light strips, orange bench seats along both walls, steel poles at every door and grab
 * rails -- lit from within, so the car glows as it goes by.
 */
public class SubwayTrainRenderer extends EntityRenderer<SubwayTrainEntity> {
	public static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/shazam/subway_train.png");

	private static final float HW = (float) JourneyGeometry.CAR_HALF_W;
	private static final float L = (float) JourneyGeometry.CAR_HALF;
	private static final float FLOOR = (float) JourneyGeometry.CAR_FLOOR;
	private static final float ROOF = (float) JourneyGeometry.CAR_ROOF;
	private static final float WIN_LO = 2.0f;
	private static final float DOOR_TOP = (float) JourneyGeometry.DOOR_TOP;
	private static final float SKIRT = 0.45f;
	private static final int WHITE = 0xFFFFFFFF;

	public SubwayTrainRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		shadowRadius = 0f;
	}

	@Override
	public ResourceLocation getTextureLocation(SubwayTrainEntity entity) {
		return TEXTURE;
	}

	@Override
	public boolean shouldRender(SubwayTrainEntity entity, Frustum frustum, double camX, double camY, double camZ) {
		return frustum.isVisible(entity.getBoundingBox().inflate(2.0, 1.0, 2.0));
	}

	/** UV rect of atlas tile (col, row): {u0, v0, u1, v1}. */
	private static float[] tile(int col, int row) {
		return new float[] { col * 0.25f + 0.002f, row * 0.25f + 0.002f, (col + 1) * 0.25f - 0.002f, (row + 1) * 0.25f - 0.002f };
	}

	private static final float[] SIDE = tile(0, 0), WINDOW = tile(1, 0), DOOR = tile(2, 0), ROOF_T = tile(3, 0);
	private static final float[] FLOOR_T = tile(0, 1), CEIL = tile(1, 1), SEAT = tile(2, 1), WALL = tile(3, 1);
	private static final float[] CAB = tile(0, 2), BOGIE = tile(1, 2), POLE = tile(2, 2), GLOW = tile(3, 2);
	private static final float[] RUBBER = tile(0, 3), END = tile(1, 3), BELLOWS = tile(2, 3), BEAM = tile(3, 3);

	@Override
	public void render(SubwayTrainEntity train, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
		double t = train.elapsed(partialTick);
		float doors = (float) train.doorsAt(t);
		float inside = LightTexture.pack(13, 0);
		int lit = Math.max(light, (int) inside);
		VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
		float off = (float) JourneyGeometry.CAR_OFFSET;
		for (int car = 0; car < 2; car++) {
			float cx = car == 0 ? -off : off;
			ps.pushPose();
			ps.translate(cx, 0, 0);
			PoseStack.Pose pose = ps.last();
			shell(solid, pose, light, car == 1, car == 0);
			interior(solid, pose, lit);
			ps.popPose();
		}
		// the gangway between the cars
		PoseStack.Pose pose = ps.last();
		float g = off - L;
		Quads.wallZ(solid, pose, -g, FLOOR, g, 3.6f, 0.9f, true, BELLOWS[0], BELLOWS[1], BELLOWS[2], BELLOWS[3], WHITE, light);
		Quads.wallZ(solid, pose, -g, FLOOR, g, 3.6f, -0.9f, false, BELLOWS[0], BELLOWS[1], BELLOWS[2], BELLOWS[3], WHITE, light);
		Quads.flatY(solid, pose, -g, -0.9f, g, 0.9f, 3.6f, true, BELLOWS[0], BELLOWS[1], BELLOWS[2], BELLOWS[3], WHITE, light);

		// glass and doors (translucent)
		VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
		for (int car = 0; car < 2; car++) {
			float cx = car == 0 ? -off : off;
			ps.pushPose();
			ps.translate(cx, 0, 0);
			windows(glass, ps.last(), light, doors);
			ps.popPose();
		}
		// full-bright: the ceiling light strips, headlights, marker lights, the beams
		VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
		lights(glow, ps, off, train.speed(partialTick));
		// the destination sign
		Font font = getFont();
		ps.pushPose();
		ps.translate(off + L + 0.02, 3.62, 0);
		ps.mulPose(Axis.YP.rotationDegrees(90f));
		float s = 0.0105f;
		ps.scale(s, -s, s);
		Component dest = Component.translatable("sign.projecthero.journey.train_destination").withStyle(ChatFormatting.BOLD);
		font.drawInBatch(dest, -font.width(dest) / 2f, -4, 0xFFFFB040, false, ps.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
		ps.popPose();
		super.render(train, yaw, partialTick, ps, buffers, light);
	}

	/** The outside of one car: side panels between the doors, the panel above, skirt, roof, ends, bogies. */
	private static void shell(VertexConsumer vc, PoseStack.Pose pose, int light, boolean leadCab, boolean tailCab) {
		float[][] segs = segments();
		for (int side = -1; side <= 1; side += 2) {
			float z = side * HW;
			boolean front = side > 0;
			for (float[] sg : segs) {
				tiledZ(vc, pose, sg[0], FLOOR - 0.1f, sg[1], WIN_LO, z, front, SIDE, 2.0f, light);
			}
			// above the windows and doors, the full length
			tiledZ(vc, pose, -L, DOOR_TOP, L, ROOF, z, front, new float[] { SIDE[0], SIDE[1], SIDE[2], SIDE[1] + 0.03f }, 2.0f, light);
			// the skirt under the floor
			tiledZ(vc, pose, -L + 0.3f, SKIRT, L - 0.3f, FLOOR - 0.1f, z * 0.97f, front, RUBBER, 2.0f, light);
			// door jambs (the thin inner faces of each doorway)
			for (double d : JourneyGeometry.DOORS) {
				float d0 = (float) d - 1f, d1 = (float) d + 1f;
				Quads.wallX(vc, pose, side > 0 ? HW - 0.08f : -HW, FLOOR, side > 0 ? HW : -HW + 0.08f, DOOR_TOP, d0, true, RUBBER[0], RUBBER[1], RUBBER[2],
						RUBBER[3], WHITE, light);
				Quads.wallX(vc, pose, side > 0 ? HW - 0.08f : -HW, FLOOR, side > 0 ? HW : -HW + 0.08f, DOOR_TOP, d1, false, RUBBER[0], RUBBER[1], RUBBER[2],
						RUBBER[3], WHITE, light);
			}
		}
		// the roof: the top, a raised centre with equipment boxes, rounded-off edges
		Quads.flatY(vc, pose, -L, -HW, L, HW, ROOF, true, ROOF_T[0], ROOF_T[1], ROOF_T[2], ROOF_T[3], WHITE, light);
		Quads.box(vc, pose, -L + 0.4f, ROOF, -HW + 0.35f, L - 0.4f, ROOF + 0.14f, HW - 0.35f, ROOF_T[0], ROOF_T[1], ROOF_T[2], ROOF_T[3], WHITE, light);
		for (float x : new float[] { -4.5f, 4.5f }) {
			Quads.box(vc, pose, x - 1.2f, ROOF + 0.14f, -0.7f, x + 1.2f, ROOF + 0.42f, 0.7f, ROOF_T[0], ROOF_T[1], ROOF_T[2], ROOF_T[3], 0xFFB0B0B4, light);
		}
		// underneath
		Quads.flatY(vc, pose, -L, -HW, L, HW, SKIRT, false, RUBBER[0], RUBBER[1], RUBBER[2], RUBBER[3], WHITE, light);
		for (float x : new float[] { -5.6f, 5.6f }) {
			Quads.box(vc, pose, x - 1.3f, 0.06f, -1.12f, x + 1.3f, SKIRT + 0.15f, 1.12f, BOGIE[0], BOGIE[1], BOGIE[2], BOGIE[3], WHITE, light);
		}
		// the ends
		for (int e = -1; e <= 1; e += 2) {
			float x = e * L;
			boolean outward = e > 0;
			boolean cab = (e > 0 && leadCab) || (e < 0 && tailCab);
			float[] face = cab ? CAB : END;
			Quads.wallX(vc, pose, -HW, FLOOR - 0.1f, HW, ROOF, x, outward, face[0], face[1], face[2], face[3], WHITE, light);
			Quads.wallX(vc, pose, -HW, SKIRT, HW, FLOOR - 0.1f, x, outward, RUBBER[0], RUBBER[1], RUBBER[2], RUBBER[3], WHITE, light);
			if (cab) {
				// the anticlimber and coupler at the nose
				Quads.box(vc, pose, x + e * 0.0f, 0.55f, -1.2f, x + e * 0.22f, 0.8f, 1.2f, RUBBER[0], RUBBER[1], RUBBER[2], RUBBER[3], WHITE, light);
			}
		}
	}

	/** Side spans between the doorways (car-local x ranges). */
	private static float[][] segments() {
		float d0 = (float) JourneyGeometry.DOORS[0], d1 = (float) JourneyGeometry.DOORS[1], d2 = (float) JourneyGeometry.DOORS[2];
		float h = (float) JourneyGeometry.DOOR_HALF;
		return new float[][] { { -L, d0 - h }, { d0 + h, d1 - h }, { d1 + h, d2 - h }, { d2 + h, L } };
	}

	/** The inside: floor, walls under and over the windows, ceiling, benches, poles, grab rails, end walls. */
	private static void interior(VertexConsumer vc, PoseStack.Pose pose, int lit) {
		float in = HW - 0.05f;
		tiledFlat(vc, pose, -L + 0.05f, -in, L - 0.05f, in, FLOOR, true, FLOOR_T, 2.0f, lit);
		tiledFlat(vc, pose, -L + 0.05f, -in, L - 0.05f, in, ROOF - 0.1f, false, CEIL, 2.0f, LightTexture.FULL_BRIGHT);
		float[][] segs = segments();
		for (int side = -1; side <= 1; side += 2) {
			float z = side * in;
			boolean faceIn = side < 0; // the wall at +z faces -z
			for (float[] sg : segs) {
				tiledZ(vc, pose, sg[0], FLOOR, sg[1], WIN_LO, z, faceIn, new float[] { WALL[0], WALL[1] + 0.13f, WALL[2], WALL[3] }, 2.0f, lit);
				// the bench: seat top, front, back rest
				float sz0 = side * (in - 0.5f), sz1 = z;
				Quads.flatY(vc, pose, sg[0] + 0.05f, Math.min(sz0, sz1), sg[1] - 0.05f, Math.max(sz0, sz1), FLOOR + 0.45f, true, SEAT[0], SEAT[1], SEAT[2],
						SEAT[3], WHITE, lit);
				tiledZ(vc, pose, sg[0] + 0.05f, FLOOR, sg[1] - 0.05f, FLOOR + 0.45f, sz0, faceIn, SEAT, 1.0f, lit);
				tiledZ(vc, pose, sg[0] + 0.05f, FLOOR + 0.45f, sg[1] - 0.05f, WIN_LO - 0.05f, z - side * 0.04f, faceIn, SEAT, 1.0f, lit);
			}
			tiledZ(vc, pose, -L, DOOR_TOP, L, ROOF - 0.1f, z, faceIn, WALL, 2.0f, lit);
			// grab rail along the ceiling
			float rz = side * 0.72f;
			Quads.box(vc, pose, -L + 0.4f, 3.48f, rz - 0.03f, L - 0.4f, 3.54f, rz + 0.03f, POLE[0], POLE[1], POLE[2], POLE[3], WHITE, lit);
		}
		for (double d : JourneyGeometry.DOORS) {
			float x = (float) d;
			Quads.box(vc, pose, x - 0.035f, FLOOR, -0.035f, x + 0.035f, ROOF - 0.1f, 0.035f, POLE[0], POLE[1], POLE[2], POLE[3], WHITE, lit);
		}
		Quads.wallX(vc, pose, -in, FLOOR, in, ROOF - 0.1f, L - 0.05f, false, END[0], END[1], END[2], END[3], WHITE, lit);
		Quads.wallX(vc, pose, -in, FLOOR, in, ROOF - 0.1f, -L + 0.05f, true, END[0], END[1], END[2], END[3], WHITE, lit);
	}

	/** The window band (tinted glass, both faces) and the door leaves, slid apart by {@code open}. */
	private static void windows(VertexConsumer vc, PoseStack.Pose pose, int light, float open) {
		for (int side = -1; side <= 1; side += 2) {
			float z = side * HW;
			for (float[] sg : segments()) {
				tiledZ(vc, pose, sg[0], WIN_LO, sg[1], DOOR_TOP, z, side > 0, WINDOW, 1.25f, light);
				tiledZ(vc, pose, sg[0], WIN_LO, sg[1], DOOR_TOP, z - side * 0.02f, side < 0, WINDOW, 1.25f, light);
			}
			float slide = open * 0.96f;
			float dz = z + side * 0.015f;
			for (double d : JourneyGeometry.DOORS) {
				float c = (float) d;
				// left leaf c-1..c slides to -x, right leaf c..c+1 to +x
				Quads.wallZ(vc, pose, c - 1f - slide, FLOOR, c - slide, DOOR_TOP, dz, side > 0, DOOR[0], DOOR[1], DOOR[2], DOOR[3], WHITE, light);
				Quads.wallZ(vc, pose, c + slide, FLOOR, c + 1f + slide, DOOR_TOP, dz, side > 0, DOOR[2], DOOR[1], DOOR[0], DOOR[3], WHITE, light);
				Quads.wallZ(vc, pose, c - 1f - slide, FLOOR, c - slide, DOOR_TOP, dz - side * 0.01f, side < 0, DOOR[0], DOOR[1], DOOR[2], DOOR[3], WHITE, light);
				Quads.wallZ(vc, pose, c + slide, FLOOR, c + 1f + slide, DOOR_TOP, dz - side * 0.01f, side < 0, DOOR[2], DOOR[1], DOOR[0], DOOR[3], WHITE, light);
			}
		}
	}

	/** Ceiling light strips, the headlights and their beams, the tail markers (full bright). */
	private static void lights(VertexConsumer vc, PoseStack ps, float off, double speed) {
		int fb = LightTexture.FULL_BRIGHT;
		PoseStack.Pose pose = ps.last();
		float nose = off + L + 0.03f;
		// headlights on the leading cab, with soft beams down the track
		for (int s = -1; s <= 1; s += 2) {
			float z = s * 0.95f;
			Quads.wallX(vc, pose, z - 0.3f, 1.35f - 0.3f, z + 0.3f, 1.35f + 0.3f, nose, true, GLOW[0], GLOW[1], GLOW[2], GLOW[3], 0xFFFFFFFF, fb);
			float len = 12f;
			Quads.quad(vc, pose, nose, 1.15f, z, nose + len, 0.5f, z, nose + len, 2.6f, z, nose, 1.55f, z, BEAM[0], BEAM[1], BEAM[2], BEAM[3], 0xFFFFFFFF, fb,
					0, 0, 1);
			Quads.quad(vc, pose, nose, 1.35f, z - 0.2f, nose + len, 1.35f, z - 1.6f, nose + len, 1.35f, z + 1.6f, nose, 1.35f, z + 0.2f, BEAM[0], BEAM[1],
					BEAM[2], BEAM[3], 0xFFFFFFFF, fb, 0, 1, 0);
		}
		// red markers on the trailing cab
		float tail = -off - L - 0.03f;
		for (int s = -1; s <= 1; s += 2) {
			float z = s * 1.1f;
			Quads.wallX(vc, pose, z - 0.18f, 0.95f, z + 0.18f, 1.31f, tail, false, GLOW[0], GLOW[1], GLOW[2], GLOW[3], 0xFFFF3030, fb);
		}
		// the ceiling light strips glow (a bloom quad just under each car's ceiling)
		for (int car = 0; car < 2; car++) {
			float cx = car == 0 ? -off : off;
			Quads.flatY(vc, pose, cx - L + 0.4f, -0.22f, cx + L - 0.4f, 0.22f, ROOF - 0.13f, false, CEIL[0] + 0.09f, CEIL[1], CEIL[0] + 0.15f, CEIL[3],
					0xFFFFFFFF, fb);
		}
	}

	/** A wall in z = {@code z} from x0 to x1, the tile repeated every {@code unit} blocks along x. */
	private static void tiledZ(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z, boolean front, float[] uv, float unit,
			int light) {
		for (float x = x0; x < x1 - 1.0e-3f; x += unit) {
			float xe = Math.min(x1, x + unit);
			float u1 = uv[0] + (uv[2] - uv[0]) * ((xe - x) / unit);
			Quads.wallZ(vc, pose, x, y0, xe, y1, z, front, uv[0], uv[1], u1, uv[3], WHITE, light);
		}
	}

	private static void tiledFlat(VertexConsumer vc, PoseStack.Pose pose, float x0, float z0, float x1, float z1, float y, boolean up, float[] uv, float unit,
			int light) {
		for (float x = x0; x < x1 - 1.0e-3f; x += unit) {
			float xe = Math.min(x1, x + unit);
			float u1 = uv[0] + (uv[2] - uv[0]) * ((xe - x) / unit);
			Quads.flatY(vc, pose, x, z0, xe, z1, y, up, uv[0], uv[1], u1, uv[3], WHITE, light);
		}
	}
}
