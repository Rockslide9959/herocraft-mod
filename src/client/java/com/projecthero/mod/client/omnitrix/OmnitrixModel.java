package com.projecthero.mod.client.omnitrix;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * v0.17.1 polish (user: "not so bulky ... the white straps dont wrap around the entire watch ... not so AI generated"): the
 * Omnitrix's geometry, drawn in the LEFT arm's space (pixels; +x = the back of the wrist, +y = down the arm toward the hand,
 * z across). Five deliberate parts in a four-colour palette (charcoal, black, grey, white, plus the lime lights):
 * <ul>
 *   <li>the <b>cuff</b>: one smooth band with rounded corners, swept round the sleeve and only ~0.45 px proud of it;</li>
 *   <li>the <b>bezel</b>: a low black cylinder on the back of the wrist with four small lime lights on its rim;</li>
 *   <li>the <b>core</b>: the grey dial with the green hourglass (or the selection screen) -- it pops up ({@code lift}) and
 *       twists ({@code twist});</li>
 *   <li>four white <b>prongs</b>: continuous bands that start under the bezel, run over the top edge, down the sides and curl
 *       round under the wrist, each standing a hair off the cuff at the side like the show's claws;</li>
 *   <li>the round <b>button</b> with its green lens on the front (-z) side.</li>
 * </ul>
 * Swept surfaces are ribbons: the long edges carry the swatch's dark outline and lit bevel (fixed 0.1 px), the run along the
 * path is seamless, so the bands read as one piece instead of a comb of boxes. Caps of the cylinders get a rim outline. Used
 * by the wrist ({@link OmnitrixWatch}), the item ({@link OmnitrixItemRenderer}) and the pod's reveal.
 */
public final class OmnitrixModel {
	private static final float T = 1f / 128f;
	private static final float S = 1f / 16f;
	/** The cuff's centre down the arm. */
	public static final float CY = 7.1f;
	/** The core's radius (the face quad's half size). */
	public static final float CORE_R = 1.5f;
	/** The fixed outline + bevel width on every surface, px. */
	private static final float B = 0.1f;

	// swatch origins (texels) in the 128x128 sheet
	static final int CUFF = 0, PLATE = 1, BLACK = 2, GREY = 3, WHITE = 4, GREEN = 5, INNER = 6, RING = 7, DIMGREEN = 8;
	private static final int[][] SW = { { 0, 0 }, { 16, 0 }, { 32, 0 }, { 48, 0 }, { 0, 16 }, { 16, 16 }, { 32, 16 }, { 48, 16 }, { 0, 32 } };

	// the cuff's cross-section: a rounded rectangle round the sleeve (x -1.25..L+0.25, z +-2.25), clearance 0.15
	// v0.17.1 (user: "the watch still doesnt properly touch the players arm"): the arm is a square box, so a round 0.5 inner
	// corner let its corners poke through; a near-square inner corner (0.1) needs only a 0.03 clearance to clear the sleeve's
	// corners, so the cuff hugs every face -- the outside stays rounded (0.1 + WALL)
	private static final float GAP = 0.03f, CORNER = 0.1f, WALL = 0.3f, HALF_LEN = 2.1f;
	private static final float PRONG_W = 0.4f, PRONG_T = 0.32f, PRONG_Y = 1.22f;

	/**
	 * v0.17.2 (user: "the omnitrix then flashes green and black before slowly reforming"): the colour every vertex is drawn in --
	 * white (as textured) except while the Recalibration Chip's reform fades a silhouette in ({@link #tint}). Render thread only.
	 */
	private static int TR = 255, TG = 255, TB = 255, TA = 255;

	/** Draws everything after this in {@code argb} (multiplied with the texture) until {@link #untint}. */
	public static void tint(int argb) {
		TA = (argb >>> 24) & 255;
		TR = (argb >> 16) & 255;
		TG = (argb >> 8) & 255;
		TB = argb & 255;
	}

	public static void untint() {
		TR = TG = TB = TA = 255;
	}

	private OmnitrixModel() {
	}

	/**
	 * v0.17.2 (user: "the dial of the omnitrix pops up and a little slot reveals open up too, the player then slots the recalibration
	 * chip into the slots and then the slot closes"): the chip's slot, in the side of the circuit riser the popped-up dial stands on,
	 * on the button's side (-z, where the right hand reaches). A small grey door covers it; {@code open} 0..1 slides the door down into
	 * the cuff, showing the dark slot with a lit green slit along it. Needs the dial up ({@code lift}, px); {@code vc} on the watch sheet.
	 */
	public static void chipSlot(PoseStack pose, VertexConsumer vc, int light, float L, float lift, float open) {
		if (lift < 0.5f) {
			return;
		}
		int lit = LightTexture.FULL_BRIGHT;
		float xo = cuffTop(L);
		float zf = -RISER_R; // the riser's face on the button's side
		float x0 = xo + 0.1f, x1 = xo + lift - 0.1f, yh = SLOT_HALF;
		float cy = CY;
		PoseStack.Pose p = pose.last();
		if (open > 0.01f) {
			// the dark slot, and the lit slit along its middle (the chip goes in here)
			plain(vc, p, light, SW[BLACK], x0, cy - yh, zf - 0.012f, x1, cy - yh, zf - 0.012f, x1, cy + yh, zf - 0.012f, x0, cy + yh, zf - 0.012f, 0, 0, -1);
			float xm = (x0 + x1) * 0.5f, sh = 0.06f + 0.06f * open;
			plain(vc, p, lit, SW[GREEN], xm - sh, cy - yh + 0.1f, zf - 0.02f, xm + sh, cy - yh + 0.1f, zf - 0.02f, xm + sh, cy + yh - 0.1f, zf - 0.02f,
					xm - sh, cy + yh - 0.1f, zf - 0.02f, 0, 0, -1);
		}
		// the door: slides down into the cuff as it opens
		float drop = open * (lift + 0.2f);
		float d0 = Math.max(xo + 0.02f, x0 - 0.06f - drop), d1 = x1 + 0.06f - drop;
		if (d1 > d0 + 0.04f) {
			box(pose, vc, d0, cy - yh - 0.06f, zf - 0.1f, d1, cy + yh + 0.06f, zf + 0.04f, GREY, light);
			// a little green tell-tale on it
			float dm = (d0 + d1) * 0.5f;
			if (d1 - d0 > 0.3f) {
				plain(vc, pose.last(), lit, SW[GREEN], dm - 0.08f, cy - 0.14f, zf - 0.105f, dm + 0.08f, cy - 0.14f, zf - 0.105f, dm + 0.08f, cy + 0.14f,
						zf - 0.105f, dm - 0.08f, cy + 0.14f, zf - 0.105f, 0, 0, -1);
			}
		}
	}

	/** v0.17.2: the chip slot's half-length along the arm (px). */
	public static final float SLOT_HALF = 0.8f;

	/** x of the cuff's outer top surface (the bezel sits on it). */
	public static float cuffTop(float L) {
		return L + GAP + WALL;
	}

	/** x of the core's top (the face) for a lift, so callers can place the silhouette over it. */
	public static float coreTop(float L, float lift) {
		return cuffTop(L) + 0.55f + lift;
	}

	/**
	 * Draws the watch. {@code L}: the arm's outer x (3 wide, 2 slim). {@code lift}: how far the core stands out of the bezel
	 * (0 at rest, ~1.0 popped, negative while slapped in). {@code twist}: the dial's turn, degrees. {@code screen}: the face
	 * shows the dark selection screen instead of the hourglass. {@code vc}: an entity cutout buffer on the watch texture.
	 */
	public static void draw(PoseStack pose, VertexConsumer vc, int light, float L, float lift, float twist, boolean screen) {
		draw(pose, vc, light, L, lift, twist, screen, true);
	}

	/**
	 * The face's radius when the wrist draws it itself ({@code face} false): a little inside the core so the dial ring with its
	 * lime notches shows (and visibly turns) round it.
	 */
	public static final float FACE_R = CORE_R - 0.26f;

	/**
	 * {@code face} false: the core's cap gets the dial ring (eight lime notches, turning with {@code twist}) and the face quad is
	 * left to the caller ({@link OmnitrixWatch}: the morph frames and the dial icon, upright while the ring turns). The core's
	 * dark side carries eight lit lime slots, hidden in the bezel at rest and seen as it pops up.
	 */
	public static void draw(PoseStack pose, VertexConsumer vc, int light, float L, float lift, float twist, boolean screen, boolean face) {
		draw(pose, vc, light, L, lift, twist, screen, face, 0f);
	}

	/** v0.17.1: how far (degrees) each half of the cuff swings out when the watch's bottom opens to leap ({@code open} 1). */
	public static final float JAW_DEG = 42f;

	/**
	 * v0.17.1 (user: "the bottom of the watch opens, and the Omnitrix jumps out ... onto the player's wrist"): {@code open} 0..1
	 * splits the cuff at the bottom of the wrist and swings its two halves (each with its pair of prongs) out about the top,
	 * like jaws, by up to {@link #JAW_DEG} -- the pod's watch springing open to leap, and clamping shut as it lands.
	 */
	public static void draw(PoseStack pose, VertexConsumer vc, int light, float L, float lift, float twist, boolean screen, boolean face, float open) {
		draw(pose, vc, light, L, lift, twist, screen, face, open, false);
	}

	/** v0.17.2: {@code recal} -- the Recalibrated Omnitrix's own slim model ({@link #drawRecal}); its colours are {@code watch_recal.png}. */
	public static void draw(PoseStack pose, VertexConsumer vc, int light, float L, float lift, float twist, boolean screen, boolean face, float open,
			boolean recal) {
		if (recal) {
			drawRecal(pose, vc, light, L, lift, twist, screen, face);
			return;
		}
		int lit = LightTexture.FULL_BRIGHT;
		float cy = CY;
		// v0.17.1 (user: "i dont want any gaps between the players arm and the omnitrix"): sized to the ARM itself, not its
		// sleeve layer -- a sleeve, when the skin has one, sits hidden inside the cuff wall (WALL 0.3 > the sleeve's 0.25)
		Section cuff = new Section(-1f - GAP, L + GAP, 2f + GAP, CORNER);
		float xo = cuffTop(L);
		Section outer = cuff.grow(WALL);
		// v0.17.1 (user: "omnitrix straps dont connect at the bottom ... make them connect"): each side's prongs run on to the middle
		// under the wrist (they stopped 0.9 px short of it, leaving a gap), so the two meet in one band
		float startZ = 0.95f, endZ = 0f;
		float s0 = startZ, s1 = outer.sAtBottom(endZ);
		if (open <= 0.001f) {
			// ---- the cuff: one band swept all the way round
			ribbon(pose, vc, cuff, 0f, cuff.perimeter(), true, 0f, 0f, WALL, cy - HALF_LEN, cy + HALF_LEN, CUFF, light);
		}
		// ---- the prongs: four white bands from under the bezel, over the edge, down the side and curling under the wrist
		for (int side = -1; side <= 1; side += 2) {
			pose.pushPose();
			if (open > 0.001f) {
				// this jaw swings out about the top of the wrist (the bezel's foot)
				pose.translate(xo * S, 0f, 0f);
				pose.mulPose(Axis.YP.rotationDegrees(side * JAW_DEG * open));
				pose.translate(-xo * S, 0f, 0f);
				float sb = cuff.sAtBottom(0f);
				if (side > 0) {
					ribbon(pose, vc, cuff, 0f, sb, false, 0f, 0f, WALL, cy - HALF_LEN, cy + HALF_LEN, CUFF, light);
				} else {
					ribbon(pose, vc, cuff, sb, cuff.perimeter(), false, 0f, 0f, WALL, cy - HALF_LEN, cy + HALF_LEN, CUFF, light);
				}
			}
			if (side < 0) {
				pose.scale(1f, 1f, -1f);
			}
			for (int end = -1; end <= 1; end += 2) {
				float yc = cy + end * PRONG_Y;
				ribbon(pose, vc, outer, s0, s1, false, 0f, 0f, PRONG_T, yc - PRONG_W, yc + PRONG_W, WHITE, light);
			}
			pose.popPose();
		}
		// ---- the button on the front (-z) side: a grey ring and a green lens
		float bx = (-1f + L) * 0.5f;
		pose.pushPose();
		pose.translate(bx * S, cy * S, -(2f + GAP + WALL) * S);
		pose.mulPose(Axis.YP.rotationDegrees(90f));
		cylinder(pose, vc, -0.05f, 0.2f, 0f, 0f, 0.62f, RING, light, 16);
		cylinder(pose, vc, 0.15f, 0.3f, 0f, 0f, 0.4f, GREEN, lit, 12);
		pose.popPose();
		// ---- the bezel: a low black cylinder, four lime lights round its rim. v0.17.1 (user: "when the omnitrix pops up the dial
		// with the green dots must also pop up"): the bezel, its lights and the core rise together by {@code lift}; the circuit
		// riser it stands on ({@link #riser}) is drawn by the wrist
		float bezelR = 2.0f, bezelTop = xo + 0.4f + lift;
		// v0.17.1 (user: "this dial with the 4 dots needs to rotate when the player switches aliens"): the bezel and its four
		// lights turn with the dial spin, not just the core
		pose.pushPose();
		pose.translate(0f, cy * S, 0f);
		pose.mulPose(Axis.XP.rotationDegrees(twist));
		pose.translate(0f, -cy * S, 0f);
		cylinder(pose, vc, xo - 0.05f + Math.max(0f, lift), bezelTop, cy, 0f, bezelR, BLACK, light, 28);
		// v0.17.1 (user's sketch): the four lights at the top, bottom, left and right of the face (were on the diagonals),
		// out on the rim just past the core's edge
		// v0.17.2 (user: "can we make the green dots on the dial circles please"): round studs (were square boxes)
		float lr = 1.8f, ls = 0.2f;
		for (int k = 0; k < 4; k++) {
			double a = k * Math.PI / 2;
			float ly = cy + (float) Math.cos(a) * lr, lz = (float) Math.sin(a) * lr;
			cylinder(pose, vc, bezelTop - 0.06f, bezelTop + 0.1f, ly, lz, ls, GREEN, lit, 14);
		}
		pose.popPose();
		// ---- the core: pops up out of the bezel, twists a notch per dial turn
		float top = coreTop(L, lift);
		pose.pushPose();
		pose.translate(0f, cy * S, 0f);
		pose.mulPose(Axis.XP.rotationDegrees(twist));
		pose.translate(0f, -cy * S, 0f);
		float cr = CORE_R;
		if (!face) {
			// the core: a short dark ring standing on the bezel. v0.17.1 (user: "why are their 2 dials now? there should only be
			// one"): no second ring of lime notches on the cap -- the bezel's four lights are the only ones
			cylinder(pose, vc, bezelTop - 0.2f, top, cy, 0f, cr, BLACK, light, 24);
			pose.popPose();
			return;
		}
		cylinder(pose, vc, bezelTop - 0.2f, top, cy, 0f, cr, GREY, light, 24);
		// the face: the hourglass (or the dark selection screen) -- u down the arm, v across it (v 0 at +z)
		float fx = (top + 0.01f) * S;
		float u0 = 64 * T, v0 = screen ? 0 : 64 * T, u1 = 1f, v1 = screen ? 64 * T : 1f;
		float fr = cr - 0.08f;
		float a = (cy - fr) * S, b = (cy + fr) * S, zp = fr * S;
		quad(vc, pose.last(), lit, fx, a, zp, fx, b, zp, fx, b, -zp, fx, a, -zp, 1, 0, 0, u0, v0, u1, v1);
		pose.popPose();
	}

	// ================================================================ v0.17.2: the Recalibrated Omnitrix

	/** The recalibrated watch's bezel: an eight-sided grey ring, angled -- outer radius at its foot / top, inner radius, height. */
	private static final float RB = 1.5f, RT = 1.32f, RI = 1.08f, BEZEL_H = 0.24f;
	/** v0.17.2 round 2 (user: "make the wrist band thinner"): the recalibrated band's wall and its half-width along the arm. */
	private static final float RECAL_WALL = 0.16f, RECAL_HALF_LEN = 1.0f;
	/** How high the recalibrated face sits over the cuff (recessed below the bezel's top), and its radius. */
	private static final float RECAL_FACE_X = 0.16f;
	/** v0.17.2 (user's reference: the dial sits on a round green pedestal): how high the pedestal lifts the bezel off the band. */
	private static final float PED = 0.32f; // v0.17.2 (user: "make the base slightly taller")
	public static final float RECAL_FACE_R = RI - 0.18f; // v0.17.2: a black ring round the hourglass
	/**
	 * v0.17.2 (user: "the dial also has to pop up when the omnitrix is activated"): the whole dial -- bezel, face and all -- pops up
	 * out of the band on a black stem, nearly as far as the prototype's core.
	 */
	public static final float RECAL_LIFT_SCALE = 0.8f;

	/** x of the recalibrated face for a lift (the hologram and the caller's face quad stand on it). */
	public static float recalFaceTop(float L, float lift) {
		return L + GAP + RECAL_WALL + PED + RECAL_FACE_X + lift;
	}

	/**
	 * v0.17.2 (user, with the show's line art and the toy: "you made the recalibrated omnitrix too thick ... try to make the recalibrated
	 * omnitrix how it look in the show"): low and slim on the wrist --
	 * <ul>
	 *   <li>the <b>band</b>: green, a black stripe running round its middle;</li>
		 *   <li>the <b>bezel</b>: a thin eight-sided grey ring, its outer wall angled in, every segment outlined (the notches);</li>
	 *   <li>the <b>face</b>: the green-and-black hourglass, recessed in the ring, on a black collar it rises out of when primed;</li>
	 *   <li>two small grey <b>knobs</b> out of the bezel's sides, across the arm.</li>
	 * </ul>
	 */
	private static void drawRecal(PoseStack pose, VertexConsumer vc, int light, float L, float lift, float twist, boolean screen, boolean face) {
		int lit = LightTexture.FULL_BRIGHT;
		float cy = CY;
		Section cuff = new Section(-1f - GAP, L + GAP, 2f + GAP, CORNER);
		float xo = L + GAP + RECAL_WALL;
		Section outer = cuff.grow(RECAL_WALL);
		// ---- the band and its black stripe
		ribbon(pose, vc, cuff, 0f, cuff.perimeter(), true, 0f, 0f, RECAL_WALL, cy - RECAL_HALF_LEN, cy + RECAL_HALF_LEN, CUFF, light);
		ribbon(pose, vc, outer, 0f, outer.perimeter(), true, 0f, 0f, 0.05f, cy - 0.3f, cy + 0.3f, WHITE, light);
		// ---- v0.17.2 (user: "add that little green and silver part underneath the watch face and dial", then "make it look 100% in
		// the reference images"): the housing the dial sits in, as the show draws it -- a green base round the dial's foot (the
		// stem rises out of it), and the band rising into two curved green brackets that hug the bezel along the arm, a dark green
		// edge round their outside, a grey tab set in the middle of each; silver mounts on the sides carry the knobs
		// v0.17.2 (user: "make the base slightly taller and then put a black band around it ... Rotate the base aswell please so that
		// the little dips on the bottom and top are on the sides ... Make the base of the watch only extend like 1 pixel out"): a
		// taller, slimmer pedestal with a black band round its middle; its two green brackets now hug the dial across the arm (the
		// dips between them along it), only just past the bezel
		float hr = RB + 0.08f;
		cylinder(pose, vc, xo - 0.04f, xo + 0.05f, cy, 0f, hr + 0.04f, DIMGREEN, light, 32);
		cylinder(pose, vc, xo - 0.03f, xo + PED, cy, 0f, hr, CUFF, light, 32);
		cylinder(pose, vc, xo + PED * 0.42f, xo + PED * 0.72f, cy, 0f, hr + 0.025f, WHITE, light, 32);
		float bIn = RB - 0.06f, bOut = RB + 0.2f, bTop = xo + PED * 0.36f, span = (float) Math.toRadians(58);
		for (int end = 0; end < 2; end++) {
			float mid = (float) (Math.PI / 2 + end * Math.PI);
			arc(pose, vc, light, SW[DIMGREEN], xo - 0.03f, bTop - 0.06f, bOut - 0.02f, bOut + 0.05f, mid - span, mid + span, cy, 10);
			arc(pose, vc, light, SW[CUFF], xo - 0.03f, bTop, bIn, bOut, mid - span, mid + span, cy, 10);
		}
		// v0.17.2 (user: "Move the grey cylinders to the sides of the watch and move the side grey buttons to the bottom and top"):
		// the grey tabs now sit in the brackets across the arm, and the knobs (on their silver mounts) stick out along it
		for (int side = -1; side <= 1; side += 2) {
			float z0 = side * (RB - 0.12f), z1 = side * (RB + 0.26f);
			box(pose, vc, xo - 0.02f, cy - 0.36f, Math.min(z0, z1), bTop + 0.06f, cy + 0.36f, Math.max(z0, z1), PLATE, light);
		}
		for (int end = -1; end <= 1; end += 2) {
			float y0 = cy + end * (RB - 0.1f), y1 = cy + end * (RB + 0.16f);
			box(pose, vc, xo + PED - 0.02f, Math.min(y0, y1), -0.3f, xo + PED + 0.24f, Math.max(y0, y1), 0.3f, PLATE, light);
			pose.pushPose();
			pose.translate((xo + PED + 0.12f) * S, (cy + end * (RB + 0.12f)) * S, 0f);
			pose.mulPose(Axis.ZP.rotationDegrees(end * 90f));
			cylinder(pose, vc, 0f, 0.36f, 0f, 0f, 0.17f, PLATE, light, 12);
			cylinder(pose, vc, 0.36f, 0.44f, 0f, 0f, 0.12f, CUFF, light, 12); // the green tip
			pose.popPose();
		}
		// ---- the bezel, the collar and the face turn with the dial
		pose.pushPose();
		pose.translate(0f, cy * S, 0f);
		pose.mulPose(Axis.XP.rotationDegrees(twist));
		pose.translate(0f, -cy * S, 0f);
		PoseStack.Pose p = pose.last();
		int[] g = SW[GREY], k = SW[BLACK];
		float x0 = xo - 0.04f + PED + lift, x1 = xo + BEZEL_H + PED + lift;
		int seg = 32;
		int[] topSw = SW[RING];
		for (int i = 0; i < seg; i++) {
			double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg, am = (a0 + a1) / 2;
			float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
			float cm = (float) Math.cos(am), sm = (float) Math.sin(am);
			// the angled outer wall: from the foot (RB) in to the top (RT); its normal leans outward and up
			float nx = (RB - RT) / BEZEL_H, nl = (float) Math.sqrt(1 + nx * nx);
			plain(vc, p, light, g, x0, cy + c0 * RB, s0 * RB, x0, cy + c1 * RB, s1 * RB, x1, cy + c1 * RT, s1 * RT, x1, cy + c0 * RT, s0 * RT,
					nx / nl, cm / nl, sm / nl);
			// the underside (the ring overhangs the band), then the flat top ring with a dark line round each edge
			plain(vc, p, light, g, x0, cy + c0 * RI, s0 * RI, x0, cy + c1 * RI, s1 * RI, x0, cy + c1 * RB, s1 * RB, x0, cy + c0 * RB, s0 * RB, -1, 0, 0);
			plain(vc, p, light, topSw, x1, cy + c0 * RT, s0 * RT, x1, cy + c1 * RT, s1 * RT, x1, cy + c1 * RI, s1 * RI, x1, cy + c0 * RI, s0 * RI, 1, 0, 0);
			float ro = RT - 0.05f, ri = RI + 0.05f, xe = x1 + 0.004f;
			plain(vc, p, light, k, xe, cy + c0 * RT, s0 * RT, xe, cy + c1 * RT, s1 * RT, xe, cy + c1 * ro, s1 * ro, xe, cy + c0 * ro, s0 * ro, 1, 0, 0);
			plain(vc, p, light, k, xe, cy + c0 * ri, s0 * ri, xe, cy + c1 * ri, s1 * ri, xe, cy + c1 * RI, s1 * RI, xe, cy + c0 * RI, s0 * RI, 1, 0, 0);
			// the inner wall down to the face
			float xf = xo + PED + RECAL_FACE_X + lift;
			plain(vc, p, light, k, x1, cy + c0 * RI, s0 * RI, x1, cy + c1 * RI, s1 * RI, xf - 0.02f, cy + c1 * RI, s1 * RI, xf - 0.02f, cy + c0 * RI, s0 * RI,
					0, -cm, -sm);
		}
		// v0.17.2 (user, with the show's line art: "add in the missing parts of the watch face and dial"): the four notches cut
		// across the bezel's top ring on the diagonals, as the show's bezel is four grey panels
		for (int i = 0; i < 4; i++) {
			double an = Math.PI / 4 + i * Math.PI / 2;
			float c = (float) Math.cos(an), sn = (float) Math.sin(an), w = 0.07f;
			float px = -sn * w, pz = c * w;
			segQuad(vc, p, lit, k, x1 + 0.006f, cy + c * RI + px, sn * RI + pz, x1 + 0.006f, cy + c * RI - px, sn * RI - pz, x1 + 0.006f,
					cy + c * (RT + 0.02f) - px, sn * (RT + 0.02f) - pz, x1 + 0.006f, cy + c * (RT + 0.02f) + px, sn * (RT + 0.02f) + pz, 1, 0, 0);
		}
		// the black stem the dial stands on while it is popped up
		if (lift > 0.02f) {
			cylinder(pose, vc, xo + PED - 0.02f, x0 + 0.02f, cy, 0f, RB - 0.18f, BLACK, light, 24);
		}
		// the black collar the face sits in
		float top = recalFaceTop(L, lift);
		cylinder(pose, vc, xo + PED, top, cy, 0f, RI - 0.02f, BLACK, light, 24);
		if (face) {
			float fx = (top + 0.01f) * S;
			float u0 = 64 * T, v0 = screen ? 0 : 64 * T, u1 = 1f, v1 = screen ? 64 * T : 1f;
			// a black ring left round the hourglass, inside the grey bezel (the show's face)
			float fr = RECAL_FACE_R;
			float a = (cy - fr) * S, b = (cy + fr) * S, zp = fr * S;
			quad(vc, pose.last(), lit, fx, a, zp, fx, b, zp, fx, b, -zp, fx, a, -zp, 1, 0, 0, u0, v0, u1, v1);
		}
		pose.popPose();
	}

	/** A four-cornered panel (px) in the flat middle colour of swatch {@code sw} (no outline): the round bezel's smooth faces. */
	private static void plain(VertexConsumer vc, PoseStack.Pose p, int light, int[] sw, float ax, float ay, float az, float bx, float by, float bz, float cx,
			float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz) {
		float u = (sw[0] + 8) * T, v = (sw[1] + 8) * T;
		quad(vc, p, light, ax * S, ay * S, az * S, bx * S, by * S, bz * S, cx * S, cy * S, cz * S, dx * S, dy * S, dz * S, nx, ny, nz, u, v, u, v);
	}

	/**
	 * A curved block round the dial's axis (px): from x0 up to x1, between radii rIn and rOut, from angle a0 to a1 (0 = along the
	 * arm toward +y), in {@code seg} steps -- its top, both walls and the two ends, flat-coloured.
	 */
	private static void arc(PoseStack pose, VertexConsumer vc, int light, int[] sw, float x0, float x1, float rIn, float rOut, float a0, float a1, float cy,
			int seg) {
		PoseStack.Pose p = pose.last();
		for (int i = 0; i < seg; i++) {
			double b0 = a0 + (a1 - a0) * i / (double) seg, b1 = a0 + (a1 - a0) * (i + 1) / (double) seg;
			float c0 = (float) Math.cos(b0), s0 = (float) Math.sin(b0), c1 = (float) Math.cos(b1), s1 = (float) Math.sin(b1);
			plain(vc, p, light, sw, x1, cy + c0 * rOut, s0 * rOut, x1, cy + c1 * rOut, s1 * rOut, x1, cy + c1 * rIn, s1 * rIn, x1, cy + c0 * rIn, s0 * rIn, 1, 0, 0);
			plain(vc, p, light, sw, x0, cy + c0 * rOut, s0 * rOut, x0, cy + c1 * rOut, s1 * rOut, x1, cy + c1 * rOut, s1 * rOut, x1, cy + c0 * rOut, s0 * rOut,
					0, (c0 + c1) / 2, (s0 + s1) / 2);
			plain(vc, p, light, sw, x1, cy + c0 * rIn, s0 * rIn, x1, cy + c1 * rIn, s1 * rIn, x0, cy + c1 * rIn, s1 * rIn, x0, cy + c0 * rIn, s0 * rIn,
					0, -(c0 + c1) / 2, -(s0 + s1) / 2);
		}
		for (float a : new float[] { a0, a1 }) {
			float c = (float) Math.cos(a), sn = (float) Math.sin(a);
			plain(vc, p, light, sw, x0, cy + c * rIn, sn * rIn, x0, cy + c * rOut, sn * rOut, x1, cy + c * rOut, sn * rOut, x1, cy + c * rIn, sn * rIn,
					0, -sn, c);
		}
	}

	/** A four-cornered panel (px) mapped to the whole of swatch {@code sw}, so its outline frames it (the bezel's notches). */
	private static void segQuad(VertexConsumer vc, PoseStack.Pose p, int light, int[] sw, float ax, float ay, float az, float bx, float by, float bz, float cx,
			float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz) {
		float e = 0.02f * T;
		quad(vc, p, light, ax * S, ay * S, az * S, bx * S, by * S, bz * S, cx * S, cy * S, cz * S, dx * S, dy * S, dz * S, nx, ny, nz, sw[0] * T + e,
				sw[1] * T + e, (sw[0] + 16) * T - e, (sw[1] + 16) * T - e);
	}

	/** The circuit riser's radius (just inside the bezel). */
	public static final float RISER_R = 1.86f;

	/**
	 * v0.17.1 (user's reference: "the dial is raised and then that bit underneath is the omnitrix circuit pattern"): the black
	 * cylinder with the glowing lime circuit traces revealed under the bezel as the dial rises {@code lift} px. {@code vc}: a
	 * buffer on the circuit strip ({@code circuit.png}: u round the side, v from the bezel down to the cuff), drawn full-bright.
	 */
	public static void riser(PoseStack pose, VertexConsumer vc, float L, float lift) {
		riser(pose, vc, cuffTop(L) - 0.04f, cuffTop(L) - 0.04f + lift + 0.02f, RISER_R, lift);
	}

	/**
	 * v0.17.2 (user: "Add the Omnitrix details to the cylinder part of the raised watch aswell please same as how it is for the
	 * prototype"): the Recalibrated Omnitrix's popped-up dial stands on the same circuit riser -- from the band up to the bezel's
	 * foot, just inside it.
	 */
	public static void recalRiser(PoseStack pose, VertexConsumer vc, float L, float lift) {
		float xo = L + GAP + RECAL_WALL;
		riser(pose, vc, xo + PED - 0.02f, xo + PED - 0.04f + lift + 0.02f, RB - 0.16f, lift);
	}

	private static void riser(PoseStack pose, VertexConsumer vc, float x0, float x1, float r, float lift) {
		if (lift <= 0.02f) {
			return;
		}
		PoseStack.Pose p = pose.last();
		float cy = CY;
		int seg = 32;
		// the strip (128 x 16) covers a quarter of the round (four repeats); v spans the revealed height, rising with it
		float vTop = 0f, vBot = 1f;
		int l = LightTexture.FULL_BRIGHT;
		for (int i = 0; i < seg; i++) {
			double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
			float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
			float u0 = (i % 8) / 8f, u1 = (i % 8 + 1) / 8f;
			vc.addVertex(p, x1 * S, (cy + c0 * r) * S, s0 * r * S).setColor(255, 255, 255, 255).setUv(u0, vTop).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(l).setNormal(p, 0, c0, s0);
			vc.addVertex(p, x1 * S, (cy + c1 * r) * S, s1 * r * S).setColor(255, 255, 255, 255).setUv(u1, vTop).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(l).setNormal(p, 0, c1, s1);
			vc.addVertex(p, x0 * S, (cy + c1 * r) * S, s1 * r * S).setColor(255, 255, 255, 255).setUv(u1, vBot).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(l).setNormal(p, 0, c1, s1);
			vc.addVertex(p, x0 * S, (cy + c0 * r) * S, s0 * r * S).setColor(255, 255, 255, 255).setUv(u0, vBot).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(l).setNormal(p, 0, c0, s0);
		}
	}

	// ================================================================ the cross-section path

	/**
	 * A rounded rectangle in the (x, z) plane: x from {@code xa} to {@code xb}, z within +-{@code zh}, corner radius {@code r}.
	 * Arc length {@code s} runs from the top centre (x = xb, z = 0) toward +z, round the +z side, along the bottom and back up
	 * the -z side.
	 */
	private record Section(float xa, float xb, float zh, float r) {
		Section grow(float d) {
			return new Section(xa - d, xb + d, zh + d, r + d);
		}

		private float[] legs() {
			float arc = (float) (Math.PI / 2) * r;
			float h = zh - r, w = xb - xa - 2 * r;
			return new float[] { h, arc, w, arc, 2 * h, arc, w, arc, h };
		}

		float perimeter() {
			float p = 0;
			for (float l : legs()) {
				p += l;
			}
			return p;
		}

		/** s of the point on the bottom edge at z = {@code z} (on the +z half). */
		float sAtBottom(float z) {
			float[] l = legs();
			return l[0] + l[1] + l[2] + l[3] + (zh - r - z);
		}

		/** {x, z, nx, nz} at arc length s (wrapped). */
		float[] at(float s) {
			float p = perimeter();
			s = ((s % p) + p) % p;
			float[] l = legs();
			float h = zh - r;
			int leg = 0;
			while (leg < l.length - 1 && s > l[leg]) {
				s -= l[leg];
				leg++;
			}
			return switch (leg) {
				case 0 -> new float[] { xb, s, 1, 0 };
				case 1 -> arc(xb - r, h, s / r, 0);
				case 2 -> new float[] { xb - r - s, zh, 0, 1 };
				case 3 -> arc(xa + r, h, s / r, (float) (Math.PI / 2));
				case 4 -> new float[] { xa, h - s, -1, 0 };
				case 5 -> arc(xa + r, -h, s / r, (float) Math.PI);
				case 6 -> new float[] { xa + r + s, -zh, 0, -1 };
				case 7 -> arc(xb - r, -h, s / r, (float) (Math.PI * 1.5));
				default -> new float[] { xb, -h + s, 1, 0 };
			};
		}

		private float[] arc(float cx, float cz, float t, float a0) {
			float a = a0 + t;
			float c = (float) Math.cos(a), sn = (float) Math.sin(a);
			return new float[] { cx + c * r, cz + sn * r, c, sn };
		}
	}

	/**
	 * A band swept along {@code sec} from arc length s0 to s1 (closed: the whole loop), spanning y0..y1, from the path pushed
	 * out by {@code off} to {@code off + t}; {@code bulge} lifts the middle of an open band off the surface (a sine over its
	 * length). Outer face and both rims are drawn (the inside lies on the arm or the cuff), plus end caps when open.
	 */
	private static void ribbon(PoseStack pose, VertexConsumer vc, Section sec, float s0, float s1, boolean closed, float off, float bulge, float t,
			float y0, float y1, int sw, int light) {
		PoseStack.Pose p = pose.last();
		int[] o = SW[sw];
		// sample points: dense on the corners, sparse on the straights
		java.util.List<Float> ss = new java.util.ArrayList<>();
		float step = 0.18f;
		int n = Math.max(2, (int) Math.ceil((s1 - s0) / step));
		for (int i = 0; i <= n; i++) {
			ss.add(s0 + (s1 - s0) * i / n);
		}
		int m = ss.size();
		float[][] in = new float[m][], out = new float[m][];
		for (int i = 0; i < m; i++) {
			float f = (ss.get(i) - s0) / Math.max(1e-4f, s1 - s0);
			float lift = closed ? 0f : bulge * (float) Math.sin(Math.PI * Math.min(1f, f * 1.15f));
			float[] q = sec.at(ss.get(i));
			float d = off + lift;
			in[i] = new float[] { q[0] + q[2] * d, q[1] + q[3] * d, q[2], q[3] };
			out[i] = new float[] { q[0] + q[2] * (d + t), q[1] + q[3] * (d + t), q[2], q[3] };
		}
		float bv = Math.min(B, (y1 - y0) / 3f), bt = Math.min(B, t / 3f);
		float[] vy = { y0, y0 + bv, y1 - bv, y1 };
		float[] vt = { 0, bt, t - bt, t };
		float[] tv = { o[1] * T + 0.02f * T, (o[1] + 2) * T, (o[1] + 14) * T, (o[1] + 16) * T - 0.02f * T };
		float uc = (o[0] + 8) * T;
		for (int i = 0; i < m - 1; i++) {
			float[] a = out[i], b = out[i + 1];
			// the outer face: three strips across y (outline + bevel at both rims)
			for (int j = 0; j < 3; j++) {
				vtx(vc, p, light, a[0], vy[j], a[1], a[2], 0, a[3], uc, tv[j]);
				vtx(vc, p, light, b[0], vy[j], b[1], b[2], 0, b[3], uc, tv[j]);
				vtx(vc, p, light, b[0], vy[j + 1], b[1], b[2], 0, b[3], uc, tv[j + 1]);
				vtx(vc, p, light, a[0], vy[j + 1], a[1], a[2], 0, a[3], uc, tv[j + 1]);
			}
			// the rims at y0 and y1: three strips from the inside out
			float[] ai = in[i], bi = in[i + 1];
			for (int rim = 0; rim < 2; rim++) {
				float y = rim == 0 ? y0 : y1, ny = rim == 0 ? -1 : 1;
				for (int j = 0; j < 3; j++) {
					float f0 = vt[j] / t, f1 = vt[j + 1] / t;
					vtx(vc, p, light, lerp(f0, ai[0], a[0]), y, lerp(f0, ai[1], a[1]), 0, ny, 0, uc, tv[j]);
					vtx(vc, p, light, lerp(f0, bi[0], b[0]), y, lerp(f0, bi[1], b[1]), 0, ny, 0, uc, tv[j]);
					vtx(vc, p, light, lerp(f1, bi[0], b[0]), y, lerp(f1, bi[1], b[1]), 0, ny, 0, uc, tv[j + 1]);
					vtx(vc, p, light, lerp(f1, ai[0], a[0]), y, lerp(f1, ai[1], a[1]), 0, ny, 0, uc, tv[j + 1]);
				}
			}
		}
		if (!closed) {
			// the end caps, fully outlined
			for (int e = 0; e < 2; e++) {
				int i = e == 0 ? 0 : m - 1;
				float[] a = in[i], b = out[i];
				// the tangent's sign: the cap faces away from the band
				int k = e == 0 ? 1 : m - 2;
				float tx = in[i][0] - in[k][0], tz = in[i][1] - in[k][1];
				float tl = (float) Math.sqrt(tx * tx + tz * tz);
				face(vc, p, light, o, true, a[0] * S, y0 * S, a[1] * S, b[0] * S, y0 * S, b[1] * S, a[0] * S, y1 * S, a[1] * S, tx / tl, 0, tz / tl);
			}
		}
	}

	private static float lerp(float f, float a, float b) {
		return a + (b - a) * f;
	}

	/**
	 * A cylinder along x from x0 to x1, centred on (y, z) = (cy, cz), radius r, {@code seg} sides: the wall (outline + bevel
	 * along both rims) and the top cap (x1, a dark rim ring round a flat middle).
	 */
	static void cylinder(PoseStack pose, VertexConsumer vc, float x0, float x1, float cy, float cz, float r, int sw, int light, int seg) {
		PoseStack.Pose p = pose.last();
		int[] o = SW[sw];
		float h = x1 - x0, bt = Math.min(B, h / 3f);
		float[] vx = { x1, x1 - bt, x0 + bt, x0 };
		float[] tv = { o[1] * T + 0.02f * T, (o[1] + 2) * T, (o[1] + 14) * T, (o[1] + 16) * T - 0.02f * T };
		float uc = (o[0] + 8) * T, um = (o[0] + 8) * T, vm = (o[1] + 8) * T;
		float ri = r - B, rm = r - 2 * B;
		for (int i = 0; i < seg; i++) {
			double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
			float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
			for (int j = 0; j < 3; j++) {
				vtx(vc, p, light, vx[j], cy + c0 * r, cz + s0 * r, 0, c0, s0, uc, tv[j]);
				vtx(vc, p, light, vx[j], cy + c1 * r, cz + s1 * r, 0, c1, s1, uc, tv[j]);
				vtx(vc, p, light, vx[j + 1], cy + c1 * r, cz + s1 * r, 0, c1, s1, uc, tv[j + 1]);
				vtx(vc, p, light, vx[j + 1], cy + c0 * r, cz + s0 * r, 0, c0, s0, uc, tv[j + 1]);
			}
			// the cap: the rim ring (outline, then bevel) and the flat middle
			float[] rr = { r, ri, rm };
			for (int j = 0; j < 2; j++) {
				vtx(vc, p, light, x1, cy + c0 * rr[j], cz + s0 * rr[j], 1, 0, 0, uc, tv[j]);
				vtx(vc, p, light, x1, cy + c1 * rr[j], cz + s1 * rr[j], 1, 0, 0, uc, tv[j]);
				vtx(vc, p, light, x1, cy + c1 * rr[j + 1], cz + s1 * rr[j + 1], 1, 0, 0, uc, tv[j + 1]);
				vtx(vc, p, light, x1, cy + c0 * rr[j + 1], cz + s0 * rr[j + 1], 1, 0, 0, uc, tv[j + 1]);
			}
			vtx(vc, p, light, x1, cy + c0 * rm, cz + s0 * rm, 1, 0, 0, um, vm);
			vtx(vc, p, light, x1, cy + c1 * rm, cz + s1 * rm, 1, 0, 0, um, vm);
			vtx(vc, p, light, x1, cy, cz, 1, 0, 0, um, vm);
			vtx(vc, p, light, x1, cy, cz, 1, 0, 0, um, vm);
		}
	}

	/** One vertex at (x, y, z) px. */
	private static void vtx(VertexConsumer vc, PoseStack.Pose p, int light, float x, float y, float z, float nx, float ny, float nz, float u, float v) {
		vc.addVertex(p, x * S, y * S, z * S).setColor(TR, TG, TB, TA).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(p, nx, ny, nz);
	}

	static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int sw, int light) {
		box(pose, vc, x0, y0, z0, x1, y1, z1, sw, light, true);
	}

	/**
	 * A box (pixels) with every face nine-sliced from swatch {@code sw}: outline + bevel border of fixed size, stretched middle.
	 * {@code bevel} false: plain faces.
	 */
	static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int sw, int light, boolean bevel) {
		x0 *= S;
		y0 *= S;
		z0 *= S;
		x1 *= S;
		y1 *= S;
		z1 *= S;
		PoseStack.Pose p = pose.last();
		int[] o = SW[sw];
		face(vc, p, light, o, bevel, x0, y0, z0, x1, y0, z0, x0, y1, z0, 0, 0, -1);
		face(vc, p, light, o, bevel, x1, y0, z1, x0, y0, z1, x1, y1, z1, 0, 0, 1);
		face(vc, p, light, o, bevel, x0, y0, z1, x0, y0, z0, x0, y1, z1, -1, 0, 0);
		face(vc, p, light, o, bevel, x1, y0, z0, x1, y0, z1, x1, y1, z0, 1, 0, 0);
		face(vc, p, light, o, bevel, x0, y0, z1, x1, y0, z1, x0, y0, z0, 0, -1, 0);
		face(vc, p, light, o, bevel, x0, y1, z0, x1, y1, z0, x0, y1, z1, 0, 1, 0);
	}

	/** One face from corner A, with edge A->B (u) and A->D (v), nine-sliced: a 2-texel border mapped to ~0.1 px. */
	private static void face(VertexConsumer vc, PoseStack.Pose p, int light, int[] sw, boolean bevel, float ax, float ay, float az, float bx, float by, float bz,
			float dx, float dy, float dz, float nx, float ny, float nz) {
		float ux = bx - ax, uy = by - ay, uz = bz - az, vx = dx - ax, vy = dy - ay, vz = dz - az;
		float lu = (float) Math.sqrt(ux * ux + uy * uy + uz * uz), lv = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
		if (lu < 1e-5f || lv < 1e-5f) {
			return;
		}
		float su = sw[0] * T, sv = sw[1] * T, e = 0.02f * T;
		if (!bevel) {
			quad(vc, p, light, ax, ay, az, bx, by, bz, bx + vx, by + vy, bz + vz, dx, dy, dz, nx, ny, nz, su + 3 * T, sv + 3 * T, su + 13 * T, sv + 13 * T);
			return;
		}
		float bord = B / 16f;
		float fu = Math.min(0.3f, bord / lu), fv = Math.min(0.3f, bord / lv);
		float[] pu = { 0, fu, 1 - fu, 1 }, pv = { 0, fv, 1 - fv, 1 };
		float[] tu = { su + e, su + 2 * T, su + 14 * T, su + 16 * T - e }, tv = { sv + e, sv + 2 * T, sv + 14 * T, sv + 16 * T - e };
		for (int j = 0; j < 3; j++) {
			for (int i = 0; i < 3; i++) {
				float a0 = pu[i], a1 = pu[i + 1], b0 = pv[j], b1 = pv[j + 1];
				quad(vc, p, light, ax + ux * a0 + vx * b0, ay + uy * a0 + vy * b0, az + uz * a0 + vz * b0, ax + ux * a1 + vx * b0, ay + uy * a1 + vy * b0,
						az + uz * a1 + vz * b0, ax + ux * a1 + vx * b1, ay + uy * a1 + vy * b1, az + uz * a1 + vz * b1, ax + ux * a0 + vx * b1,
						ay + uy * a0 + vy * b1, az + uz * a0 + vz * b1, nx, ny, nz, tu[i], tv[j], tu[i + 1], tv[j + 1]);
			}
		}
	}

	static void quad(VertexConsumer vc, PoseStack.Pose p, int light, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
			float cz, float dx, float dy, float dz, float nx, float ny, float nz, float u0, float v0, float u1, float v1) {
		vc.addVertex(p, ax, ay, az).setColor(TR, TG, TB, TA).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, bx, by, bz).setColor(TR, TG, TB, TA).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, cx, cy, cz).setColor(TR, TG, TB, TA).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, dx, dy, dz).setColor(TR, TG, TB, TA).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}
}
