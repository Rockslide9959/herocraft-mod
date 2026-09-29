package com.projecthero.mod.moonknight.ability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Eye of Khonshu's sky sign: Khonshu's bird skull, seen from the front, with Moon Knight's crescent above it --
 * as an ordered list of 2D points (skull units, x right / y up, origin between the eye sockets). The order is the
 * drawing order, so emitting a growing prefix over time looks like the skull being traced across the sky:
 * <ol>
 *   <li>the crescent crown,</li>
 *   <li>the cranium dome and its sides sweeping in to the cheekbones,</li>
 *   <li>the brow ridge and the two deep eye sockets,</li>
 *   <li>the long, slightly curved ibis beak with its nostril slits, and the cheek lines.</li>
 * </ol>
 * The skull is about 11 units wide and 23 tall (crescent to beak tip); {@link #EYES} are the socket centres, which the
 * client fills with a cold glow. Pure data -- used by the client renderer and the gametests alike.
 */
public final class MoonKnightSkull {
	/** Distance between neighbouring outline points, in skull units. */
	public static final double SPACING = 0.3;
	/** Socket centres (x, y). */
	public static final double[][] EYES = { { -2.15, 2.0 }, { 2.15, 2.0 } };

	private static final List<double[]> POINTS = Collections.unmodifiableList(build());

	private MoonKnightSkull() {
	}

	/** The outline, in drawing order ({x, y} pairs). */
	public static List<double[]> points() {
		return POINTS;
	}

	private static List<double[]> build() {
		List<double[]> out = new ArrayList<>();
		// 1. the crescent crown: an outer "smile" arc and a thinner inner one, joined at the horns
		arc(out, 0.0, 12.0, 2.6, 200, 340);
		line(out, 2.44, 11.11, 1.99, 11.97);
		arc(out, 0.0, 12.9, 2.2, 335, 205);
		line(out, -1.99, 11.97, -2.44, 11.11);
		// 2. the cranium: a dome, then each side curving in to the cheekbone
		arc(out, 0.0, 2.5, 5.2, 180, 0);
		bezier(out, 5.2, 2.5, 5.45, -0.6, 3.6, -1.8);
		bezier(out, -5.2, 2.5, -5.45, -0.6, -3.6, -1.8);
		// 3. the brow ridge (a shallow V -- a scowl) and the eye sockets
		bezier(out, -4.2, 3.9, 0.0, 3.4, 4.2, 3.9);
		arc(out, EYES[0][0], EYES[0][1], 1.55, 90, 450);
		arc(out, EYES[1][0], EYES[1][1], 1.55, 90, -270);
		// 4. the beak: two edges meeting at a point that hooks a little to one side, like an ibis
		bezier(out, -1.8, 0.1, -1.3, -5.0, 0.9, -11.0);
		bezier(out, 1.8, 0.1, 2.0, -5.5, 0.9, -11.0);
		line(out, -0.45, -1.3, -0.35, -3.0);
		line(out, 0.45, -1.3, 0.35, -3.0);
		// cheek lines from the cheekbones to the beak
		line(out, -3.6, -1.8, -1.55, -2.3);
		line(out, 3.6, -1.8, 1.7, -2.4);
		return out;
	}

	/** Points on a circular arc from {@code fromDeg} to {@code toDeg} (either direction). */
	private static void arc(List<double[]> out, double cx, double cy, double r, double fromDeg, double toDeg) {
		double span = Math.toRadians(toDeg - fromDeg);
		int n = Math.max(2, (int) Math.ceil(Math.abs(span) * r / SPACING));
		for (int i = 0; i <= n; i++) {
			double a = Math.toRadians(fromDeg) + span * i / n;
			out.add(new double[] { cx + Math.cos(a) * r, cy + Math.sin(a) * r });
		}
	}

	private static void line(List<double[]> out, double x0, double y0, double x1, double y1) {
		double len = Math.hypot(x1 - x0, y1 - y0);
		int n = Math.max(1, (int) Math.ceil(len / SPACING));
		for (int i = 0; i <= n; i++) {
			double t = (double) i / n;
			out.add(new double[] { x0 + (x1 - x0) * t, y0 + (y1 - y0) * t });
		}
	}

	/** A quadratic Bezier from (x0, y0) through control (cx, cy) to (x1, y1). */
	private static void bezier(List<double[]> out, double x0, double y0, double cx, double cy, double x1, double y1) {
		double approx = Math.hypot(cx - x0, cy - y0) + Math.hypot(x1 - cx, y1 - cy);
		int n = Math.max(2, (int) Math.ceil(approx / SPACING));
		for (int i = 0; i <= n; i++) {
			double t = (double) i / n;
			double u = 1.0 - t;
			out.add(new double[] { u * u * x0 + 2 * u * t * cx + t * t * x1, u * u * y0 + 2 * u * t * cy + t * t * y1 });
		}
	}
}
