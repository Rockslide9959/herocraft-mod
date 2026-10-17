package com.projecthero.mod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.maxsteel.TurboDraw;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * v0.14.13: ONE ring finger for every power ring -- the Green Lantern's Power Ring and the Flash Ring are drawn by the
 * same code at the same spot, in both views (third person through their render layers, first person through
 * {@code GreenLanternRingHandMixin}), so they always sit the same way on the hand.
 *
 * <p>Geometry is in the right arm bone's pixel space (y runs down the arm, the fist ends at y = 10, the front face is
 * z = -2, the INNER face -- the side of the hand closest to the body -- is x = +1 on both wide and slim arms). The ring
 * sits on the knuckles right at the body side of the fist, its band wrapping round onto the inner face: in third person
 * it is on the side of the hand nearest the body, and in first person (where the knuckle face is the one you see on the
 * left of your own arm) it sits at the top of the hand, in plain view.
 *
 * <p>v0.16.6: a real little ring instead of a few flat slabs. The ring is built round a notional finger one pixel wide
 * that runs down the hand (along y) and lies half sunk into the knuckle face: an octagonal band of eight bevelled
 * segments wraps it, and a setting sits on top of it -- the band's colours, the setting and the {@link Emblem} come from
 * the {@link Palette}. Every face is shaded by which way it faces in the ring's own frame (the setting's face brightest,
 * the underside darkest), so the ring reads as a solid metal object even though it is drawn full-bright. The finger
 * position, the per-ring {@link RingPlacement}s and the slim / wide arm shift are unchanged, so it still sits where the
 * old ring did on both arm widths.
 */
public final class HandRing {
	/** Where the band sits down the arm (y) -- just above the end of the fist. */
	public static final float RING_Y = 8.5f;
	/** How far in from the inner edge of the fist the finger's centre is (on the knuckle face). */
	public static final float FINGER_IN = 0.75f;

	/**
	 * A ring's colours: band, band highlight, bezel, gem (core) and the gem's rim. v0.14.23: plus its id, which keys its
	 * {@link RingPlacement}.
	 */
	public record Palette(String id, int band, int highlight, int bezel, int gemCore, int gem) {
	}

	/**
	 * v0.16.6: what is set on top of the band.
	 * <ul>
	 *   <li>{@link #GEM}: a square bezel ({@code bezel}) holding a stepped domed gem ({@code gem} rim, {@code gemCore}
	 *   crown) -- the default for any ring without an emblem of its own.</li>
	 *   <li>{@link #LANTERN}: the Green Lantern's chunky square bezel with a bright raised rim ({@code highlight}), a dark
	 *   face ({@code bezel}) and the Lantern emblem -- a circle between two bars -- raised on it ({@code gem}, with a
	 *   {@code gemCore} heart).</li>
	 *   <li>{@link #BOLT}: the Flash Ring's collet ({@code highlight}), a stepped red dome ({@code bezel}) and a lightning
	 *   bolt raised on top ({@code gem}).</li>
	 *   <li>{@link #PAW}: a round flat face ({@code bezel}) with a glowing paw print on it ({@code gem}) -- for a
	 *   Cat-style Miraculous ring.</li>
	 * </ul>
	 */
	public enum Emblem {
		GEM, LANTERN, BOLT, PAW
	}

	public static final Palette GREEN_LANTERN = new Palette("green_lantern", 0x1F9A48, 0x9CFFB8, 0x0B3D1C, 0xEFFFF2, 0x5CFF8E);
	public static final Palette FLASH = new Palette("flash", 0xD9A521, 0xFFE58A, 0xC41A1A, 0xFFF4B0, 0xFFD21E);

	/** The ring's lift off bare skin. */
	public static final float SKIN_GAP = 0.08f;
	/**
	 * Set while the first-person hand is drawn ({@code GreenLanternRingHandMixin}). The first-person suit sleeve
	 * ({@code SuperheroFirstPersonArm}) is flush with the arm, not puffed out like the third-person suit model, so there the
	 * ring always sits at {@link #SKIN_GAP}.
	 */
	public static boolean firstPerson;

	/**
	 * v0.14.24: whether the arm being drawn is a slim (Alex) arm. Its outer face is x = -2, a wide (Steve) arm's is x = -3,
	 * so a {@link RingPlacement} tuned on one is moved by that pixel on the other (callers set this from the player's skin
	 * via {@link #arm}).
	 */
	public static boolean slimArm;

	/** v0.14.24: sets {@link #slimArm} for {@code player}'s skin before their ring is drawn. */
	public static void arm(net.minecraft.client.player.AbstractClientPlayer player) {
		slimArm = player.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
	}

	// v0.16.6 ring geometry, in arm pixels. "Out" is away from the knuckle face (-z before the placement turns the ring).
	/** The notional finger's radius, and how far its axis is sunk below the knuckle face. */
	private static final float FINGER_R = 0.46f, FINGER_SINK = 0.3f;
	/** How far the setting's top stands out from the knuckle face -- {@link #gemCentre} sits here. */
	private static final float SETTING_TOP = 0.66f;

	/** v0.16.6: set while an emblem that glows is drawn -- its faces skip the metal shading (all full colour). */
	private static boolean glow;

	private HandRing() {
	}

	private static float lift(float gap) {
		return firstPerson ? SKIN_GAP : gap;
	}

	/**
	 * v0.15.15: x of the fist's inner face as the ring's pivot sees it. Both shipped {@link RingPlacement}s turn the ring
	 * 90 degrees about Y, which puts the band on the OUTER face of the hand, whose outward normal is -x -- so a thicker
	 * suit shell has to move the pivot toward -x. (Before this the gap was added toward +x, burying the ring inside a
	 * suit's gauntlet -- "the ring doesn't look like it's on the player's body" when suited.) Identical to the old value
	 * for bare skin (gap = SKIN_GAP).
	 */
	private static float innerFace(float gap) {
		return 1f + 2f * SKIN_GAP - gap;
	}

	/** v0.16.6: the emblem a ring wears by default -- the Lantern's and the Flash's own, a plain gem for anything else. */
	public static Emblem emblemFor(Palette c) {
		if (GREEN_LANTERN.id().equals(c.id())) {
			return Emblem.LANTERN;
		}
		if (FLASH.id().equals(c.id())) {
			return Emblem.BOLT;
		}
		return Emblem.GEM;
	}

	/**
	 * Draws the ring. {@code pose} must already be in the arm's space ({@code arm.translateAndRotate}); {@code gap} lifts
	 * it off a suit's gauntlet. Callers add their own extras (the Lantern's halo) round {@link #gemCentre}. v0.16.6: the
	 * setting is {@link #emblemFor}{@code (c)}; {@link #draw(PoseStack, VertexConsumer, float, Palette, Emblem)} picks it.
	 */
	public static void draw(PoseStack pose, VertexConsumer vc, float gap, Palette c) {
		draw(pose, vc, gap, c, emblemFor(c));
	}

	/** v0.16.6: {@link #draw(PoseStack, VertexConsumer, float, Palette)} with the setting chosen by the caller. */
	public static void draw(PoseStack pose, VertexConsumer vc, float gap, Palette c, Emblem emblem) {
		gap = lift(gap);
		pose.pushPose();
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		float inner = innerFace(gap);     // the fist's inner face (toward the body), see innerFace
		float front = -2f - gap;          // its front (knuckle) face
		float fx = inner - FINGER_IN;     // finger centre, x: on the knuckles, right by the inner edge
		float y = RING_Y;
		RingPlacement.apply(pose, c.id(), fx, y, front);
		// from here on the ring's own frame: origin on the knuckle face at the finger, x across the finger, y along it,
		// -z out of the hand
		pose.translate(fx, y, front);
		switch (emblem) {
			case LANTERN -> lantern(vc, pose, c);
			case BOLT -> bolt(vc, pose, c);
			case PAW -> paw(vc, pose, c);
			default -> gem(vc, pose, c);
		}
		pose.popPose();
	}

	/**
	 * v0.14.23: for extras drawn round the gem ({@link #gemCentre}) -- applies {@code c}'s hand-tuned placement so they
	 * move with the ring. {@code pose} must already be scaled to arm pixels.
	 */
	public static void place(PoseStack pose, Palette c, float gap) {
		gap = lift(gap);
		RingPlacement.apply(pose, c.id(), innerFace(gap) - FINGER_IN, RING_Y, -2f - gap);
	}

	/** The centre of the gem, in arm pixels (x, y, z), for extras drawn round it. v0.16.6: the top of the new setting. */
	public static float[] gemCentre(float gap) {
		gap = lift(gap);
		return new float[] { innerFace(gap) - FINGER_IN, RING_Y, -2f - gap - SETTING_TOP };
	}

	/**
	 * The buffer to draw a ring into. v0.16.6: an OPAQUE entity buffer (white texture, full-bright), no longer the shared
	 * translucent one -- translucent quads are depth-sorted one by one, so the Lantern's halo box round the setting was
	 * drawn before parts of the emblem it encloses and hid them. Fetch it before any translucent extras.
	 */
	public static VertexConsumer buffer(net.minecraft.client.renderer.MultiBufferSource buffers) {
		return buffers.getBuffer(net.minecraft.client.renderer.RenderType.entitySolid(TurboDraw.WHITE));
	}

	public static void box(VertexConsumer vc, PoseStack pose, float x, float y, float z, float hx, float hy, float hz, int rgb,
			float alpha) {
		pose.pushPose();
		pose.translate(x, y, z);
		TurboDraw.box(vc, pose.last(), hx, hy, hz, rgb, alpha);
		pose.popPose();
	}

	// ---------------------------------------------------------------- v0.16.6 ring parts

	/**
	 * The band: eight segments round the finger (axis along y, sunk {@link #FINGER_SINK} into the hand), each a little
	 * longer than its octagon side so the corners close. {@code t} = thickness, {@code hw} = half its width along the
	 * finger. The segment facing straight out gets the highlight colour.
	 */
	private static void band(VertexConsumer vc, PoseStack pose, Palette c, float t, float hw) {
		float r = FINGER_R + t / 2f;
		float half = (FINGER_R + t) * 0.4142f * 1.06f;
		for (int k = 0; k < 8; k++) {
			double th = Math.toRadians(k * 45.0);
			float u = (float) (r * Math.cos(th));
			float w = (float) (r * Math.sin(th)) - FINGER_SINK; // out of the hand
			pose.pushPose();
			pose.translate(u, 0f, -w);
			// turn the segment's x along the octagon's tangent; its local +z then points radially out
			pose.mulPose(Axis.YP.rotationDegrees(k * 45f + 90f));
			shaded(vc, pose, 0f, 0f, 0f, half, hw, t / 2f, k == 2 ? c.highlight() : c.band(), 1f, true);
			pose.popPose();
		}
		// a bright edge line round the outward half of the band, so its rim catches the light
		float top = FINGER_R + t - FINGER_SINK;
		shaded(vc, pose, 0f, -hw + 0.03f, -top - 0.005f, half * 0.9f, 0.04f, 0.02f, c.highlight(), 1f, false);
		shaded(vc, pose, 0f, hw - 0.03f, -top - 0.005f, half * 0.9f, 0.04f, 0.02f, c.highlight(), 1f, false);
	}

	/** The outward distance of the band's top, where a setting starts. */
	private static float bandTop(float t) {
		return FINGER_R + t - FINGER_SINK;
	}

	/** A slab centred on the finger, standing {@code w0..w1} out from the knuckle face. */
	private static void slab(VertexConsumer vc, PoseStack pose, float u, float v, float hu, float hv, float w0, float w1, int rgb) {
		shaded(vc, pose, u, v, -(w0 + w1) / 2f, hu, hv, (w1 - w0) / 2f, rgb, 1f, false);
	}

	/** A round-ish plate: two crossed slabs (a square with its corners cut). */
	private static void disc(VertexConsumer vc, PoseStack pose, float h, float cut, float w0, float w1, int rgb) {
		slab(vc, pose, 0f, 0f, h, h - cut, w0, w1, rgb);
		slab(vc, pose, 0f, 0f, h - cut, h, w0, w1, rgb);
	}

	/** A thin stroke of length {@code len}, turned {@code deg} in the setting's face plane, standing {@code w0..w1} out. */
	private static void stroke(VertexConsumer vc, PoseStack pose, float u, float v, float len, float thick, float deg, float w0,
			float w1, int rgb) {
		pose.pushPose();
		pose.translate(u, v, -(w0 + w1) / 2f);
		pose.mulPose(Axis.ZP.rotationDegrees(deg));
		shaded(vc, pose, 0f, 0f, 0f, len / 2f, thick / 2f, (w1 - w0) / 2f, rgb, 1f, false);
		pose.popPose();
	}

	/** Eight strokes round a circle of radius {@code r} in the face plane. */
	private static void circle(VertexConsumer vc, PoseStack pose, float r, float thick, float w0, float w1, int rgb) {
		float len = 2f * r * 0.4142f * 1.25f;
		for (int k = 0; k < 8; k++) {
			double th = Math.toRadians(k * 45.0);
			stroke(vc, pose, (float) (r * Math.cos(th)), (float) (r * Math.sin(th)), len, thick, k * 45f + 90f, w0, w1, rgb);
		}
	}

	/** The Green Lantern: a chunky band, a square bezel with a bright rim, a dark face and the raised Lantern emblem. */
	private static void lantern(VertexConsumer vc, PoseStack pose, Palette c) {
		float t = 0.26f;
		band(vc, pose, c, t, 0.36f);
		float b = bandTop(t);
		slab(vc, pose, 0f, 0f, 0.44f, 0.42f, b - 0.12f, b + 0.06f, c.band());           // shoulders
		slab(vc, pose, 0f, 0f, 0.6f, 0.6f, b + 0.06f, b + 0.24f, c.band());             // bezel block
		float f = b + 0.24f, rim = 0.1f;
		slab(vc, pose, 0f, -0.6f + rim / 2f, 0.6f, rim / 2f, f, f + 0.09f, c.highlight());  // raised rim
		slab(vc, pose, 0f, 0.6f - rim / 2f, 0.6f, rim / 2f, f, f + 0.09f, c.highlight());
		slab(vc, pose, -0.6f + rim / 2f, 0f, rim / 2f, 0.6f - rim, f, f + 0.09f, c.highlight());
		slab(vc, pose, 0.6f - rim / 2f, 0f, rim / 2f, 0.6f - rim, f, f + 0.09f, c.highlight());
		slab(vc, pose, 0f, 0f, 0.5f, 0.5f, f - 0.01f, f + 0.02f, c.bezel());            // dark face
		// the emblem: a circle between two bars (the bars run across the finger)
		float e0 = f + 0.02f, e1 = f + 0.1f;
		glow = true;
		circle(vc, pose, 0.22f, 0.08f, e0, e1, c.gem());
		slab(vc, pose, 0f, -0.3f, 0.36f, 0.045f, e0, e1, c.gem());
		slab(vc, pose, 0f, 0.3f, 0.36f, 0.045f, e0, e1, c.gem());
		glow = false;
	}

	/** The Flash: a slim gold band, a gold collet, a stepped red dome and a gold lightning bolt raised on top. */
	private static void bolt(VertexConsumer vc, PoseStack pose, Palette c) {
		float t = 0.2f;
		band(vc, pose, c, t, 0.28f);
		float b = bandTop(t);
		slab(vc, pose, 0f, 0f, 0.3f, 0.3f, b - 0.1f, b + 0.04f, c.band());              // shoulders
		disc(vc, pose, 0.5f, 0.16f, b + 0.02f, b + 0.12f, c.highlight());                // collet
		disc(vc, pose, 0.44f, 0.14f, b + 0.12f, b + 0.22f, c.bezel());                   // the dome
		disc(vc, pose, 0.34f, 0.11f, b + 0.22f, b + 0.3f, c.bezel());
		disc(vc, pose, 0.22f, 0.07f, b + 0.3f, b + 0.35f, TurboDraw.toWhite(c.bezel(), 0.25f));
		// the bolt: down the finger -- a stroke down-across, a short jog back, another stroke down-across
		float w0 = b + 0.26f, w1 = b + 0.4f;
		stroke(vc, pose, 0.07f, -0.17f, 0.36f, 0.11f, 58f, w0, w1, c.gem());
		stroke(vc, pose, 0f, -0.01f, 0.32f, 0.09f, 0f, w0, w1, c.gem());
		stroke(vc, pose, -0.07f, 0.16f, 0.34f, 0.09f, 58f, w0, w1, c.gem());
		stroke(vc, pose, -0.15f, 0.31f, 0.12f, 0.06f, 58f, w0, w1 - 0.03f, c.gemCore());  // the tapering tip
	}

	/** A Cat-style ring: a band, a round flat face and a paw print -- one big pad and four toes. */
	private static void paw(VertexConsumer vc, PoseStack pose, Palette c) {
		float t = 0.22f;
		band(vc, pose, c, t, 0.3f);
		float b = bandTop(t);
		slab(vc, pose, 0f, 0f, 0.3f, 0.3f, b - 0.1f, b + 0.04f, c.band());
		disc(vc, pose, 0.5f, 0.15f, b + 0.02f, b + 0.16f, c.bezel());
		float w0 = b + 0.16f, w1 = b + 0.22f;
		glow = true;
		disc(vc, pose, 0.15f, 0.05f, w0, w1, c.gem());
		slab(vc, pose, 0f, 0.03f, 0.08f, 0.07f, w1, w1 + 0.02f, TurboDraw.toWhite(c.gem(), 0.3f));
		slab(vc, pose, -0.24f, -0.06f, 0.055f, 0.055f, w0, w1, c.gem());
		slab(vc, pose, -0.09f, -0.22f, 0.055f, 0.055f, w0, w1, c.gem());
		slab(vc, pose, 0.09f, -0.22f, 0.055f, 0.055f, w0, w1, c.gem());
		slab(vc, pose, 0.24f, -0.06f, 0.055f, 0.055f, w0, w1, c.gem());
		glow = false;
	}

	/** The default setting: a square bezel holding a stepped domed gem. */
	private static void gem(VertexConsumer vc, PoseStack pose, Palette c) {
		float t = 0.22f;
		band(vc, pose, c, t, 0.32f);
		float b = bandTop(t);
		slab(vc, pose, 0f, 0f, 0.32f, 0.32f, b - 0.1f, b + 0.04f, c.band());
		slab(vc, pose, 0f, 0f, 0.45f, 0.45f, b + 0.02f, b + 0.16f, c.bezel());
		disc(vc, pose, 0.32f, 0.1f, b + 0.16f, b + 0.26f, c.gem());
		disc(vc, pose, 0.18f, 0.06f, b + 0.26f, b + 0.32f, c.gemCore());
	}

	/**
	 * A box at {@code (x, y, z)} (half-extents {@code hx, hy, hz}) with each face shaded by the way it faces: the face
	 * pointing out of the hand (-z, or +z when {@code outwardPlusZ}) full colour, the sides darker, the face toward the
	 * hand darkest.
	 */
	private static void shaded(VertexConsumer vc, PoseStack pose, float x, float y, float z, float hx, float hy, float hz,
			int rgb, float alpha, boolean outwardPlusZ) {
		PoseStack.Pose p = pose.last();
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
		float x0 = x - hx, x1 = x + hx, y0 = y - hy, y1 = y + hy, z0 = z - hz, z1 = z + hz;
		float out = 1f, in = glow ? 1f : 0.5f;
		float side = glow ? 1f : 0.76f, top = glow ? 1f : 0.86f, bottom = glow ? 1f : 0.68f;
		// -z / +z (out of / into the hand)
		quad(vc, p, rgb, outwardPlusZ ? in : out, a, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
		quad(vc, p, rgb, outwardPlusZ ? out : in, a, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		// -y / +y (toward the shoulder / the fingertips)
		quad(vc, p, rgb, top, a, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(vc, p, rgb, bottom, a, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		// -x / +x
		quad(vc, p, rgb, side, a, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		quad(vc, p, rgb, side, a, 1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose p, int rgb, float shade, int a, float nx, float ny, float nz,
			float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
		int r = Math.round(((rgb >> 16) & 0xFF) * shade);
		int g = Math.round(((rgb >> 8) & 0xFF) * shade);
		int b = Math.round((rgb & 0xFF) * shade);
		vertex(vc, p, ax, ay, az, 0, 0, r, g, b, a, nx, ny, nz);
		vertex(vc, p, bx, by, bz, 0, 1, r, g, b, a, nx, ny, nz);
		vertex(vc, p, cx, cy, cz, 1, 1, r, g, b, a, nx, ny, nz);
		vertex(vc, p, dx, dy, dz, 1, 0, r, g, b, a, nx, ny, nz);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int r, int g,
			int b, int a, float nx, float ny, float nz) {
		vc.addVertex(p, x, y, z)
				.setColor(r, g, b, a)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT)
				.setNormal(p, nx, ny, nz);
	}
}
