// v0.17.1 Green Lantern Construct Menu icons: one 32x32 hard-light icon per catalogue entry (named by entry id), a
// generic fallback, and the Lantern Corps emblem the menu draws faintly behind the grid. Re-runnable:
//   node scratchpad/gl_icons_v0171/make_icons.js            (from the repo root)
// Writes src/main/resources/assets/projecthero/textures/gui/green_lantern/icons/*.png and a contact sheet at
// docs/screenshots/v0171/gl_icons_sheet.png.
//
// Each icon is a shape (a predicate over continuous 0..32 coordinates, supersampled 4x4) plus optional "cut" lines
// (dark inner detail) and "hi" accents (near-white). The style pass gives every silhouette a bright mint outline, a
// lit top-left inner edge, an emerald top-to-bottom gradient, a dark 1 px rim outside and a two-step green glow.
'use strict';
const fs = require('fs');
const path = require('path');
const { encode } = require('../suits/skin_tools.js');

const ROOT = path.resolve(__dirname, '..', '..');
const OUT = path.join(ROOT, 'src/main/resources/assets/projecthero/textures/gui/green_lantern/icons');
const SHEET = path.join(ROOT, 'docs/screenshots/v0171/gl_icons_sheet.png');
const N = 32;

// ------------------------------------------------------------------ shape primitives (predicates)
const rect = (x0, y0, x1, y1) => (x, y) => x >= x0 && x <= x1 && y >= y0 && y <= y1;
const rrect = (x0, y0, x1, y1, r) => (x, y) => {
	if (x < x0 || x > x1 || y < y0 || y > y1) return false;
	const cx = Math.max(x0 + r, Math.min(x1 - r, x)), cy = Math.max(y0 + r, Math.min(y1 - r, y));
	return (x - cx) ** 2 + (y - cy) ** 2 <= r * r;
};
const circle = (cx, cy, r) => (x, y) => (x - cx) ** 2 + (y - cy) ** 2 <= r * r;
const ellipse = (cx, cy, rx, ry) => (x, y) => ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1;
const ring = (cx, cy, r0, r1) => (x, y) => { const d = (x - cx) ** 2 + (y - cy) ** 2; return d >= r0 * r0 && d <= r1 * r1; };
const poly = pts => (x, y) => {
	let inside = false;
	for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
		const [xi, yi] = pts[i], [xj, yj] = pts[j];
		if ((yi > y) !== (yj > y) && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside;
	}
	return inside;
};
const seg = (x0, y0, x1, y1, w) => (x, y) => {
	const dx = x1 - x0, dy = y1 - y0, l2 = dx * dx + dy * dy || 1;
	const t = Math.max(0, Math.min(1, ((x - x0) * dx + (y - y0) * dy) / l2));
	return (x - x0 - t * dx) ** 2 + (y - y0 - t * dy) ** 2 <= (w / 2) ** 2;
};
const polyline = (pts, w) => union(...pts.slice(1).map((p, i) => seg(pts[i][0], pts[i][1], p[0], p[1], w)));
/** An arc band of radius r and width w from a0 to a1 degrees (0 = right, 90 = down). */
const arc = (cx, cy, r, w, a0, a1) => (x, y) => {
	const d = Math.hypot(x - cx, y - cy);
	if (Math.abs(d - r) > w / 2) return false;
	let a = (Math.atan2(y - cy, x - cx) * 180) / Math.PI;
	while (a < a0) a += 360;
	while (a >= a0 + 360) a -= 360;
	return a <= a1;
};
const union = (...s) => (x, y) => s.some(f => f(x, y));
const diff = (a, ...b) => (x, y) => a(x, y) && !b.some(f => f(x, y));
const inter = (a, b) => (x, y) => a(x, y) && b(x, y);
const rot = (s, deg, cx = 16, cy = 16) => {
	const r = (-deg * Math.PI) / 180, c = Math.cos(r), sn = Math.sin(r);
	return (x, y) => s(cx + (x - cx) * c - (y - cy) * sn, cy + (x - cx) * sn + (y - cy) * c);
};
const tr = (s, dx, dy) => (x, y) => s(x - dx, y - dy);
const scl = (s, k, cx = 16, cy = 16) => (x, y) => s(cx + (x - cx) / k, cy + (y - cy) / k);
const flipX = s => (x, y) => s(32 - x, y);
const star = (cx, cy, r0, r1, n, phase = 0) => {
	const pts = [];
	for (let i = 0; i < n * 2; i++) {
		const a = phase + (i * Math.PI) / n, r = i % 2 ? r0 : r1;
		pts.push([cx + Math.cos(a) * r, cy + Math.sin(a) * r]);
	}
	return poly(pts);
};
const NONE = () => false;

// ------------------------------------------------------------------ the icons
// Each: { shape, cut?, hi? }. Keep silhouettes inside ~3..29 so the glow has room.
const missile = (len) => union(
	rrect(-len / 2, -2.4, len / 2 - 3, 2.4, 1.8), poly([[len / 2 - 3.5, -2.4], [len / 2 + 2, 0], [len / 2 - 3.5, 2.4]]),
	poly([[-len / 2, -2], [-len / 2 - 2.5, -5], [-len / 2 + 3.5, -2]]), poly([[-len / 2, 2], [-len / 2 - 2.5, 5], [-len / 2 + 3.5, 2]]));
const place = (s, x, y, deg) => (px, py) => {
	const r = (-deg * Math.PI) / 180, c = Math.cos(r), sn = Math.sin(r);
	const dx = px - x, dy = py - y;
	return s(dx * c - dy * sn, dx * sn + dy * c);
};
const emblem = (cx, cy, k) => union(ring(cx, cy, 4.2 * k, 7 * k), rect(cx - 9 * k, cy - 8.2 * k, cx + 9 * k, cy - 6 * k), rect(cx - 9 * k, cy + 6 * k, cx + 9 * k, cy + 8.2 * k));
const chainLink = (cx, cy, deg) => rot(diff(rrect(cx - 5, cy - 3, cx + 5, cy + 3, 3), rrect(cx - 3, cy - 1, cx + 3, cy + 1, 1)), deg, cx, cy);
const wheel = (cx, cy, r) => diff(circle(cx, cy, r), circle(cx, cy, r * 0.38));

const ICONS = {
	// ---------------- ring moves and the old fixed-key attacks
	construct_fist: {
		shape: union(rrect(7, 7, 25, 19, 3.5), rrect(7, 14, 19, 22, 3), rrect(10, 19, 22, 27, 1.5)),
		cut: union(rect(12, 8, 12.9, 14), rect(16.5, 8, 17.4, 14), rect(21, 8, 21.9, 15), rect(8, 18.5, 19, 19.4), rect(10, 22.6, 22, 23.4)),
		hi: union(rect(9, 9, 10, 10), rect(13.5, 9, 14.5, 10), rect(18, 9, 19, 10), rect(22.5, 9, 23.5, 10)),
	},
	war_hammer: {
		shape: rot(union(rrect(6, 4, 26, 13, 1.5), rect(14.2, 13, 17.8, 29), rect(13, 26, 19, 29)), -30),
		cut: rot(union(rect(11, 4, 11.9, 13), rect(20.1, 4, 21, 13), rect(14.2, 18, 17.8, 18.9)), -30),
		hi: rot(rect(7.5, 5.5, 10, 6.5), -30),
	},
	emerald_gatling: {
		shape: union(rrect(3, 9, 14, 22, 2.5), rect(14, 9.5, 28, 12.5), rect(14, 14.5, 29, 17.5), rect(14, 19.5, 28, 22.5),
			rect(19, 8, 22, 24), poly([[5, 22], [11, 22], [9, 28], [5, 28]])),
		cut: union(rect(14, 13, 28, 13.9), rect(14, 18, 28, 18.9), rect(6, 12.5, 11, 13.4)),
		hi: union(rect(27, 15.3, 28.5, 16.7)),
	},
	missile_barrage: {
		shape: union(place(missile(13), 9, 22, -45), place(missile(13), 17, 15, -45), place(missile(13), 23, 25, -45)),
		hi: union(circle(13.6, 17.4, 1), circle(21.6, 10.4, 1), circle(27.6, 20.4, 1)),
	},
	giant_hand: {
		shape: union(rrect(9, 14, 24, 28, 4), rrect(9, 5, 12.6, 18, 1.8), rrect(13.4, 3, 17, 18, 1.8), rrect(17.8, 4, 21.4, 18, 1.8),
			rrect(22, 8, 25.4, 19, 1.7), rot(rrect(1, 15, 13, 19.5, 2.2), -40, 9, 21)),
		cut: union(rect(12.6, 7, 13.4, 16), rect(17, 7, 17.8, 16), rect(21.4, 10, 22, 16)),
		hi: union(rect(10, 6, 11, 7), rect(14.5, 4, 15.5, 5), rect(19, 5, 20, 6)),
	},
	ring_bolt: {
		shape: union(circle(21, 11, 6.5), poly([[16.4, 6.4], [2, 26], [25.6, 15.6]]), poly([[3, 30], [9, 20], [12, 23]])),
		cut: union(seg(6, 24, 15, 13, 0.9), seg(11, 25, 19, 16, 0.9)),
		hi: union(circle(21, 11, 3.2)),
	},
	continuous_beam: {
		shape: union(ring(7, 25, 2.2, 5), rect(5.5, 19, 8.5, 21), rot(poly([[10, 23], [32, 19], [32, 30], [10, 27]]), -42, 7, 25)),
		hi: union(rot(rect(12, 24.4, 32, 25.6), -42, 7, 25), circle(7, 19.6, 1)),
	},
	blast_wave: {
		shape: union(circle(16, 16, 4), arc(16, 16, 8.5, 2.6, 0, 360), arc(16, 16, 13, 2.2, 200, 340), arc(16, 16, 13, 2.2, 20, 160)),
		hi: circle(15, 15, 1.6),
	},
	shield: {
		shape: poly([[5, 5], [16, 3], [27, 5], [27, 15], [16, 29], [5, 15]]),
		cut: ring(16, 14, 3.3, 4.3),
		hi: union(rect(7, 6.5, 12, 7.5), rect(14, 6, 18, 7)),
	},
	dome: {
		shape: union(inter(ellipse(16, 24, 13, 18), rect(0, 0, 32, 24)), rrect(2, 23, 30, 27, 1.5)),
		cut: union(inter(ellipse(16, 24, 6, 18), diff(rect(0, 0, 32, 23), ellipse(16, 24, 5.1, 17.2))), rect(4.5, 16.6, 27.5, 17.5), rect(9, 11, 23, 11.9)),
		hi: union(arc(16, 24, 11, 1.3, 205, 240)),
	},
	oath: {
		shape: union(rrect(9, 3, 23, 7, 1.5), rect(15, 1.5, 17, 4), rrect(8, 7, 24, 23, 2.5), rrect(6, 23, 26, 28, 1.5)),
		cut: union(emblem(16, 15, 0.62), rect(8, 22.5, 24, 23.4)),
		hi: union(rect(10, 9, 11, 13)),
	},
	suit: {
		shape: poly([[10, 4], [13, 6], [19, 6], [22, 4], [29, 9], [26, 15], [23, 13], [23, 28], [9, 28], [9, 13], [6, 15], [3, 9]]),
		cut: union(ring(16, 15, 2.6, 3.6), rect(9, 22, 23, 22.9)),
		hi: circle(16, 15, 1.4),
	},
	ring_scan: {
		shape: union(ring(16, 16, 10.5, 13), circle(16, 16, 2.2), inter(circle(16, 16, 10.5), poly([[16, 16], [30, 2], [30, 16]]))),
		cut: union(rect(3, 15.6, 29, 16.4), rect(15.6, 3, 16.4, 29), ring(16, 16, 6, 6.9)),
		hi: union(circle(22, 12, 1.3), circle(10, 21, 1)),
	},
	quick_deploy: {
		shape: poly([[19, 2], [7, 18], [15, 18], [12, 30], [25, 13], [17, 13], [21, 2]]),
		hi: poly([[18.5, 4], [10, 16.5], [11.5, 16.5], [19.5, 5]]),
	},

	// ---------------- the constructs (entry id = "c_" + ConstructType name)
	c_energy_blade: {
		shape: rot(union(poly([[14, 2], [18, 2], [18, 20], [16, 23], [14, 20]]), rrect(9, 20, 23, 23, 1.2), rect(14.5, 23, 17.5, 28), circle(16, 29, 2.2)), 45),
		hi: rot(rect(15.5, 4, 16.5, 19), 45),
	},
	c_containment_cage: {
		shape: union(rrect(4, 4, 28, 8, 1.5), rrect(4, 24, 28, 28, 1.5), rect(6, 8, 8.5, 24), rect(11, 8, 13.5, 24), rect(18.5, 8, 21, 24), rect(23.5, 8, 26, 24),
			rrect(14, 12, 18, 20, 2)),
		hi: union(rect(6, 5, 10, 6)),
	},
	c_sentry_turret: {
		shape: union(poly([[8, 29], [13, 19], [19, 19], [24, 29], [21, 29], [16, 22], [11, 29]]), inter(circle(16, 18, 8), rect(0, 0, 32, 18)), rrect(7, 16, 25, 20, 1),
			rect(19, 10, 30, 13.5)),
		cut: union(rect(10, 12.6, 22, 13.4)),
		hi: union(circle(14, 13.5, 1.6), rect(28.5, 10.7, 29.5, 12.8)),
	},
	c_battering_ram: {
		shape: union(rrect(7, 11, 23, 21, 3), rrect(21, 7, 30, 25, 2)),
		cut: union(rect(12, 11, 12.9, 21), rect(17, 11, 17.9, 21), rect(21, 7, 21.9, 25)),
		hi: union(rect(8.5, 12.5, 11, 13.5), rect(23, 9, 26, 10)),
		glowExtra: union(rect(1, 12, 5, 13), rect(0, 15.5, 5, 16.5), rect(1, 19, 5, 20)),
	},
	c_hard_light_wall: {
		shape: rrect(4, 5, 28, 27, 1.5),
		cut: union(rect(4, 10.1, 28, 11), rect(4, 15.6, 28, 16.5), rect(4, 21.1, 28, 22),
			rect(11, 5, 11.9, 10.1), rect(20, 5, 20.9, 10.1), rect(7, 11, 7.9, 15.6), rect(16, 11, 16.9, 15.6), rect(24, 11, 24.9, 15.6),
			rect(11, 16.5, 11.9, 21.1), rect(20, 16.5, 20.9, 21.1), rect(7, 22, 7.9, 27), rect(16, 22, 16.9, 27), rect(24, 22, 24.9, 27)),
		hi: union(rect(5.5, 6.5, 9, 7.5)),
	},
	c_platform: {
		shape: union(poly([[3, 15], [11, 10], [29, 10], [21, 15]]), poly([[3, 15], [21, 15], [21, 19], [3, 19]]), poly([[21, 15], [29, 10], [29, 14], [21, 19]])),
		cut: union(rect(3, 15, 21, 15.9), seg(21, 15.4, 29, 10.4, 0.9)),
		hi: union(rect(13, 11.3, 26, 12.2)),
		glowExtra: union(rect(6, 22, 8, 23), rect(12, 23, 14, 24), rect(18, 22, 20, 23), rect(9, 26, 11, 27), rect(15, 26.5, 17, 27.5)),
	},
	c_bridge: {
		shape: union(diff(rect(2, 12, 30, 24), ellipse(16, 25, 9.5, 8.5)), rect(2, 9, 30, 12), rect(2, 24, 6, 28), rect(26, 24, 30, 28)),
		cut: union(rect(2, 12, 30, 12.9), rect(9, 9, 9.9, 12), rect(16, 9, 16.9, 12), rect(23, 9, 23.9, 12)),
		hi: union(rect(3, 10, 8, 11)),
	},
	c_stair_ramp: {
		shape: poly([[3, 28], [3, 22], [9, 22], [9, 16], [15, 16], [15, 10], [21, 10], [21, 4], [29, 4], [29, 28]]),
		cut: union(rect(9, 22, 29, 22.9), rect(15, 16, 29, 16.9), rect(21, 10, 29, 10.9)),
		hi: union(rect(4, 23, 7, 24), rect(10, 17, 13, 18), rect(16, 11, 19, 12), rect(22, 5, 27, 6)),
	},
	c_mining_drill: {
		shape: union(rrect(10, 2, 22, 9, 2), poly([[8, 9], [24, 9], [16, 30]])),
		cut: union(seg(9, 13, 23, 10.5, 0.9), seg(10.5, 17.5, 22, 15, 0.9), seg(12.2, 21.5, 20.3, 19.5, 0.9), seg(14, 25.5, 18.5, 24, 0.9)),
		hi: union(rect(12, 3.5, 15, 4.5)),
	},
	c_lantern_light: {
		shape: union(circle(16, 16, 6.5), ...[0, 45, 90, 135, 180, 225, 270, 315].map(a => rot(rrect(14.8, 2, 17.2, 7, 1.2), a))),
		hi: union(circle(14.5, 14.5, 2.6)),
	},
	c_atmosphere_bubble: {
		shape: circle(16, 16, 12.5),
		cut: union(ring(16, 16, 9.2, 10), rect(13, 22, 19, 22.9)),
		hi: union(arc(16, 16, 7.5, 1.6, 195, 250), circle(20.5, 10.5, 1.2), circle(16, 18, 2.2), rect(14, 20.5, 18, 22)),
	},
	c_rescue_tether: {
		shape: union(polyline([[3, 3], [7, 7], [9, 10], [13, 12]], 2.2), ring(19, 19, 7.6, 10), circle(19, 15.5, 2.4), rrect(16, 18.5, 22, 25, 2)),
		hi: union(circle(3.6, 3.6, 1.1), circle(18.3, 14.8, 0.8)),
	},
	c_carry_platform: {
		shape: union(rrect(3, 21, 29, 26, 1.5), poly([[16, 3], [24, 11], [19.5, 11], [19.5, 18], [12.5, 18], [12.5, 11], [8, 11]])),
		cut: union(rect(3, 23.1, 29, 24)),
		hi: union(rect(4.5, 22, 9, 23), rect(15.5, 5, 16.5, 7)),
	},
	c_hard_light_tools: {
		shape: union(rot(union(rect(14.6, 6, 17.4, 30), arc(16, 18, 12, 3.2, 222, 318)), -40), rot(union(rect(14.8, 2, 17.2, 20), rrect(11, 18, 21, 29, 3)), 40)),
		hi: union(rot(rect(15.5, 20, 16.5, 27), 40)),
	},
	c_buzzsaw: {
		shape: diff(star(16, 16, 10.5, 14, 12), circle(16, 16, 2.8)),
		cut: union(ring(16, 16, 6.2, 7), rect(10, 15.6, 13.2, 16.4), rect(18.8, 15.6, 22, 16.4), rect(15.6, 10, 16.4, 13.2), rect(15.6, 18.8, 16.4, 22)),
		hi: arc(16, 16, 9, 1.2, 200, 250),
	},
	c_anvil_drop: {
		shape: union(poly([[3, 14], [27, 14], [29, 12], [29, 19], [23, 20], [20, 23], [20, 25], [24, 25], [24, 29], [8, 29], [8, 25], [12, 25], [12, 23], [9, 20], [6, 20]]),
			rect(7, 3, 9, 10), rect(15, 2, 17, 10), rect(23, 4, 25, 10)),
		cut: union(rect(8, 24.1, 24, 25)),
		hi: union(rect(5, 15, 12, 16)),
	},
	c_chain_snare: {
		shape: union(chainLink(8, 24, -45), chainLink(16, 16, 45), chainLink(24, 8, -45)),
		hi: union(circle(9.5, 21, 0.8), circle(25.5, 5.5, 0.8)),
	},
	c_launch_pad: {
		shape: union(ellipse(16, 25, 13, 4), polyline([[11, 21], [21, 19], [11, 17], [21, 15]], 2), poly([[16, 2], [24, 10], [19, 10], [19, 13], [13, 13], [13, 10], [8, 10]])),
		cut: ring(16, 25, 7, 7.8),
		hi: union(rect(5, 23.5, 9, 24.5), rect(15.5, 4.5, 16.5, 7)),
	},
	c_emerald_warrior: {
		shape: union(rrect(9, 3, 23, 18, 5), poly([[3, 29], [5, 20], [11, 17], [21, 17], [27, 20], [29, 29]]), rect(15, 0.5, 17, 4)),
		cut: union(rect(10, 10, 22, 11.6), rect(15.6, 11.6, 16.4, 17), rect(9, 19.6, 23, 20.4)),
		hi: union(rect(11, 5, 13, 6), rect(12, 10.3, 14, 11.3)),
	},
	c_sniper_rifle: {
		shape: rot(union(rect(1, 14.5, 31, 17), rrect(9, 10, 18, 13, 1.5), rect(12, 13, 14, 14.5), poly([[3, 14.5], [9, 14.5], [8, 21], [3, 22]]), poly([[18, 17], [21, 17], [20, 22], [18, 22]])), -30),
		hi: rot(union(rect(10, 10.8, 13, 11.6), rect(29, 15, 31, 16.5)), -30),
	},
	c_boxing_glove: {
		shape: union(rrect(12, 6, 29, 24, 6.5), rrect(8, 9, 13, 21, 1.5), polyline([[2, 15], [4, 11], [6, 19], [8, 13]], 1.8), rrect(13, 19, 22, 26, 3)),
		cut: union(rect(13, 8, 13.9, 22), seg(17, 19.4, 25, 19.4, 0.9)),
		hi: union(rect(16, 8, 22, 9), rect(24, 9.5, 25.5, 11)),
	},
	c_fly_swatter: {
		shape: rot(union(rrect(8, 2, 24, 17, 2), rect(14.8, 17, 17.2, 31)), 35),
		cut: rot(union(rect(11.6, 4, 12.4, 15), rect(15.6, 4, 16.4, 15), rect(19.6, 4, 20.4, 15), rect(10, 6.6, 22, 7.4), rect(10, 10.6, 22, 11.4)), 35),
		hi: rot(rect(9.5, 3.5, 13, 4.5), 35),
	},
	c_catapult: {
		shape: union(rect(4, 22, 26, 25), wheel(8, 26.5, 3.5), wheel(22, 26.5, 3.5), poly([[12, 22], [15, 15], [18, 22]]), seg(15, 17, 25, 6, 2.4), circle(25, 6, 4.2)),
		cut: union(ring(25, 6, 2, 2.8)),
		hi: union(circle(23.6, 4.6, 0.9), rect(5, 22.5, 10, 23.4)),
	},
	c_emerald_tentacles: {
		shape: union(rrect(3, 26, 29, 29, 1.5),
			polyline([[7, 27], [5, 21], [8, 16], [6, 10], [8, 6]], 3.2), polyline([[16, 27], [18, 21], [15, 14], [17, 7], [15, 3]], 3.6), polyline([[25, 27], [27, 21], [24, 16], [26, 11], [24, 8]], 3.2)),
		hi: union(circle(7.4, 7, 0.8), circle(15.6, 4, 0.8), circle(24.4, 9, 0.8)),
	},
	c_wrecking_ball: {
		shape: union(rect(3, 2, 18, 4), polyline([[13, 4], [15, 10], [17, 14]], 2), circle(19, 21, 8)),
		cut: union(rect(13.6, 6, 15.4, 6.8), rect(15, 9.6, 16.8, 10.4), arc(19, 21, 5.5, 0.9, 20, 110)),
		hi: union(circle(16, 18, 2), circle(14.3, 16.8, 0.7)),
	},
	c_emerald_cannon: {
		shape: union(rot(union(rrect(6, 11, 27, 18, 3), rrect(25, 10, 29, 19, 1)), -25, 15, 17), wheel(13, 23, 5.5), rect(4, 27, 22, 29)),
		cut: rot(union(rect(14, 11, 14.9, 18)), -25, 15, 17),
		hi: rot(rect(8, 12.2, 13, 13.2), -25, 15, 17),
	},
	c_emerald_bow: {
		shape: union(arc(26, 16, 18, 3, 128, 232), rect(9.6, 4.5, 10.4, 27.5), rect(5, 15, 27, 17), poly([[27, 13], [31, 16], [27, 19]]), poly([[3, 13], [7, 15], [7, 17], [3, 19], [5, 16]])),
		hi: union(arc(26, 16, 18, 0.9, 150, 175)),
	},
	c_bulldozer: {
		shape: union(rrect(9, 14, 29, 22, 1.5), rrect(18, 5, 28, 15, 1.5), poly([[1, 10], [5, 12], [5, 26], [1, 28]]), rect(5, 17, 9, 19.5), rrect(8, 21, 30, 29, 4)),
		cut: union(rect(20.5, 7.5, 25.5, 11.5), rect(8, 23.6, 30, 24.4), ring(12.5, 26.4, 1.2, 2.1), ring(19, 26.4, 1.2, 2.1), ring(25.5, 26.4, 1.2, 2.1)),
		hi: union(rect(10.5, 15.5, 15, 16.5), rect(2, 12, 3, 16)),
	},
	c_bear_trap: {
		shape: union(inter(union(ring(16, 10, 10, 13), diff(circle(16, 10, 10.2), star(16, 10, 5.2, 10.4, 12, Math.PI / 12))), rect(0, 10, 32, 32)),
			rrect(1, 8, 6, 11, 1), rrect(26, 8, 31, 11, 1), rrect(5, 25, 27, 28, 1.2), rect(14.5, 22, 17.5, 25), polyline([[27, 27], [30, 29]], 1.6)),
		cut: union(rect(5, 26.1, 27, 26.9)),
		hi: union(arc(16, 10, 11.5, 0.9, 120, 160), rect(6.5, 25.5, 10, 26)),
	},
	c_mirror_shield: {
		shape: circle(17, 17, 12),
		cut: union(ring(17, 17, 9, 9.9)),
		hi: union(seg(11, 19, 18, 12, 1.5), seg(15, 22, 21, 16, 1), polyline([[2, 3], [9, 10]], 1)),
		glowExtra: union(poly([[2, 3], [6, 4], [3, 7]])),
	},
	c_grappling_claw: {
		shape: union(polyline([[2, 2], [6, 5], [8, 9], [12, 12]], 1.8), rot(rrect(11, 11, 19, 18, 2), 45, 15, 14.5),
			polyline([[16, 18], [17, 25], [22, 29]], 2.8), polyline([[18, 16], [26, 26]], 2.8), polyline([[19, 14], [26, 15], [29, 20]], 2.8)),
		hi: union(circle(15, 14.5, 1.3)),
	},
	c_fighter_jet: {
		shape: poly([[16, 1.5], [18.5, 9], [18.5, 13], [29, 21], [29, 24], [18.5, 21], [18, 26], [22, 29], [22, 30.5], [16, 29], [10, 30.5], [10, 29], [14, 26], [13.5, 21], [3, 24], [3, 21], [13.5, 13], [13.5, 9]]),
		cut: union(rect(15.6, 14, 16.4, 27)),
		hi: union(ellipse(16, 9.5, 1.3, 3)),
	},
	c_emerald_dragon: {
		shape: union(poly([[2, 15], [9, 12], [17, 9], [24, 11], [28, 16], [30, 23], [30, 30], [21, 30], [19, 24], [13, 22], [9, 23], [3, 22], [6, 19], [2, 18]]),
			poly([[17, 10], [26, 1], [22, 11]]), poly([[22, 11], [31, 6], [25, 14]]), poly([[27, 17], [31, 16], [29, 21]])),
		cut: union(poly([[2, 18.2], [13, 18.6], [6, 19.6]]), seg(20, 23, 27, 22, 0.9), seg(21, 27, 29, 26, 0.9)),
		hi: union(rect(16, 13, 18.5, 14.5), rect(4, 14.5, 5, 15.5)),
	},
	c_emerald_mace: {
		shape: union(seg(5, 27, 17, 15, 3), circle(20, 12, 6.2), ...[0, 60, 120, 180, 240, 300].map(a => rot(poly([[18.4, 6], [20, 1.5], [21.6, 6]]), a, 20, 12)), circle(4.5, 27.5, 2.4)),
		cut: union(ring(20, 12, 3, 3.8)),
		hi: union(circle(18.5, 10.5, 1.2)),
	},
	c_med_bay: {
		shape: union(poly([[16, 3], [30, 26], [2, 26]]), rrect(1, 26, 31, 29, 1)),
		cut: union(rect(13, 11, 19, 25), rect(9, 15, 23, 21)),
		hi: union(rect(14.2, 12.2, 17.8, 23.8), rect(10.2, 16.2, 21.8, 19.8)),
	},
	c_battering_ram_unused: null,

	// ---------------- constructs other branches are adding in parallel (wired up after the merge)
	c_hard_light_mech: {
		shape: union(rrect(12, 2, 20, 8, 1.5), rrect(8, 8, 24, 19, 2), rrect(3, 9, 8, 21, 2), rrect(24, 9, 29, 21, 2), rect(10, 19, 14.5, 29), rect(17.5, 19, 22, 29), rect(8, 27, 15, 30), rect(17, 27, 24, 30)),
		cut: union(rect(13, 4.5, 19, 5.5), rect(8, 12.6, 24, 13.4), rect(3, 15.6, 8, 16.4), rect(24, 15.6, 29, 16.4)),
		hi: union(circle(16, 16, 1.6), rect(14, 4.5, 15, 5.5), rect(17, 4.5, 18, 5.5)),
	},
	c_hard_light_chest: {
		shape: union(rrect(4, 7, 28, 14, 3), rect(4, 14, 28, 27)),
		cut: union(rect(4, 13.6, 28, 14.6), rect(8, 7, 8.9, 27), rect(23.1, 7, 24, 27)),
		hi: union(rrect(14, 12, 18, 18, 0.8), rect(10, 8.5, 14, 9.5)),
	},
	c_scaffold_tower: {
		shape: union(rect(6, 2, 9, 30), rect(23, 2, 26, 30), rect(4, 2, 28, 4.5), rect(6, 11, 26, 13), rect(6, 20, 26, 22), rect(3, 28, 29, 30),
			seg(8, 4.5, 24, 11, 1.8), seg(24, 13, 8, 20, 1.8), seg(8, 22, 24, 28, 1.8)),
		hi: union(rect(5, 2.5, 9, 3.5)),
	},
	c_item_net: {
		shape: union(ring(13, 13, 8.5, 10.8), inter(circle(13, 13, 8.5), union(...[6.5, 10.5, 14.5, 18.5].map(v => rect(v, 0, v + 1, 32)), ...[6.5, 10.5, 14.5, 18.5].map(v => rect(0, v, 32, v + 1)))),
			seg(20.5, 20.5, 29, 29, 3)),
		hi: union(arc(13, 13, 9.6, 1, 200, 250), circle(29, 29, 0.8)),
	},
	c_tunneller: {
		shape: union(rrect(2, 10, 18, 25, 2), rect(16, 12, 20, 23), circle(23, 17.5, 8.5), rrect(3, 25, 17, 29, 2)),
		cut: union(ring(23, 17.5, 3, 3.8), ...[0, 60, 120].map(a => rot(rect(14, 17.1, 32, 17.9), a, 23, 17.5)), rect(5, 13, 12, 17)),
		hi: union(circle(23, 17.5, 1.4), rect(4, 26, 7, 27)),
	},
	c_carry_platform_car: {
		shape: union(poly([[2, 18], [4, 14], [10, 13], [13, 7], [22, 7], [26, 13], [30, 14], [30, 22], [2, 22]]), wheel(9, 22.5, 4.5), wheel(23, 22.5, 4.5)),
		cut: union(poly([[12.5, 12.8], [15, 8.5], [18.5, 8.5], [18.5, 12.8]]), poly([[20, 12.8], [20, 8.5], [21.5, 8.5], [24.3, 12.8]]), rect(2, 16.1, 30, 17)),
		hi: union(rect(27.5, 15, 29.5, 16), rect(14, 9, 15, 10)),
	},
	c_carry_platform_jet: {
		shape: union(poly([[2, 15], [7, 13], [25, 12.5], [30, 15.5], [25, 18.5], [7, 18], [2, 16.5]]), poly([[3, 14], [5, 5], [9, 5], [11, 13.5]]), poly([[12, 17], [21, 17], [13, 27], [9, 27]]), poly([[3, 17], [8, 17], [5, 21], [3, 21]])),
		cut: union(rect(7, 15, 22, 15.8)),
		hi: union(poly([[21, 13.2], [25.5, 13.4], [27.5, 15], [21, 15]])),
		glowExtra: union(rect(0, 15, 1.5, 16.5)),
	},

	// ---------------- the fallback for a catalogue entry with no icon of its own
	_generic: {
		shape: diff(poly([[16, 2], [28, 9], [28, 23], [16, 30], [4, 23], [4, 9]]), NONE),
		cut: union(emblem(16, 16, 0.85)),
		hi: union(rect(9, 9.5, 12, 10.5)),
	},
};
delete ICONS.c_battering_ram_unused;

// ------------------------------------------------------------------ raster + style
function raster(fn, size, thr = 0.5) {
	const m = new Uint8Array(size * size);
	if (!fn) return m;
	const k = 32 / size;
	for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
		let c = 0;
		for (let sy = 0; sy < 4; sy++) for (let sx = 0; sx < 4; sx++) if (fn((x + (sx + 0.5) / 4) * k, (y + (sy + 0.5) / 4) * k)) c++;
		m[y * size + x] = c / 16 >= thr ? 1 : 0;
	}
	return m;
}
const lerp = (a, b, t) => Math.round(a + (b - a) * t);
function style(icon, size = N) {
	const M = raster(icon.shape, size), C = raster(icon.cut, size, 0.45), H = raster(icon.hi, size, 0.45), G = raster(icon.glowExtra, size, 0.45);
	const at = (m, x, y) => (x < 0 || y < 0 || x >= size || y >= size ? 0 : m[y * size + x]);
	// distance (chebyshev, up to 3) from the silhouette, for the rim and glow
	const dist = new Uint8Array(size * size).fill(9);
	for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
		if (at(M, x, y)) { dist[y * size + x] = 0; continue; }
		for (let r = 1; r <= 3; r++) {
			let hit = false;
			for (let dy = -r; dy <= r && !hit; dy++) for (let dx = -r; dx <= r && !hit; dx++) {
				if (Math.max(Math.abs(dx), Math.abs(dy)) === r && (Math.abs(dx) + Math.abs(dy) <= r + 1) && at(M, x + dx, y + dy)) hit = true;
			}
			if (hit) { dist[y * size + x] = r; break; }
		}
	}
	const px = Buffer.alloc(size * size * 4);
	const put = (x, y, r, g, b, a) => { const i = (y * size + x) * 4; px[i] = r; px[i + 1] = g; px[i + 2] = b; px[i + 3] = a; };
	for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
		const d = dist[y * size + x];
		if (d === 0) {
			const up = !at(M, x, y - 1), lf = !at(M, x - 1, y), dn = !at(M, x, y + 1), rt = !at(M, x + 1, y);
			if (at(H, x, y)) put(x, y, 238, 255, 242, 255);
			else if (up || lf) put(x, y, 214, 255, 226, 255);
			else if (dn || rt) put(x, y, 128, 242, 166, 255);
			else if (at(C, x, y)) put(x, y, 12, 92, 42, 255);
			else {
				const lit = !at(M, x - 1, y - 1) || !at(M, x, y - 2) || !at(M, x - 2, y);
				const t = Math.min(1, Math.max(0, (y * 0.75 + x * 0.25) / size));
				if (lit) put(x, y, 112, 255, 158, 255);
				else put(x, y, lerp(66, 20, t), lerp(232, 146, t), lerp(116, 66, t), 248);
			}
		} else if (at(G, x, y)) put(x, y, 150, 255, 185, 210);
		else if (d === 1) put(x, y, 6, 46, 22, 225);
		else if (d === 2) put(x, y, 53, 240, 117, 78);
		else if (d === 3) put(x, y, 53, 240, 117, 26);
	}
	return { w: size, h: size, px };
}

// the Lantern Corps emblem, 128x128, white-green (the menu tints it and draws it faintly)
function emblemImage() {
	const s = 128;
	const shape = scl(union(ring(16, 16, 6.2, 9.6), rect(3, 3.2, 29, 6.2), rect(3, 25.8, 29, 28.8)), 1);
	const m = raster(shape, s);
	const px = Buffer.alloc(s * s * 4);
	for (let i = 0; i < s * s; i++) if (m[i]) { px[i * 4] = 200; px[i * 4 + 1] = 255; px[i * 4 + 2] = 215; px[i * 4 + 3] = 255; }
	return { w: s, h: s, px };
}

// ------------------------------------------------------------------ contact sheet (with a tiny 3x5 label font)
const FONT = {
	A: '010101111101101', B: '110101110101110', C: '011100100100011', D: '110101101101110', E: '111100110100111', F: '111100110100100',
	G: '011100101101011', H: '101101111101101', I: '111010010010111', J: '001001001101010', K: '101101110101101', L: '100100100100111',
	M: '101111111101101', N: '110101101101101', O: '010101101101010', P: '110101110100100', Q: '010101101110011', R: '110101110101101',
	S: '011100010001110', T: '111010010010010', U: '101101101101111', V: '101101101101010', W: '101101111111101', X: '101101010101101',
	Y: '101101010010010', Z: '111001010100111', _: '000000000000111', 0: '111101101101111', 1: '010110010010111', 2: '110001010100111',
	3: '110001010001110', 4: '101101111001001', 5: '111100110001110', 6: '011100111101111', 7: '111001010010010', 8: '111101111101111', 9: '111101111001110',
};
function sheet(names, imgs) {
	const SC = 3, CELLW = 32 * SC + 16, CELLH = 32 * SC + 26, COLS = 8, ROWS = Math.ceil(names.length / COLS);
	const W = COLS * CELLW + 8, H = ROWS * CELLH + 8;
	const px = Buffer.alloc(W * H * 4);
	for (let i = 0; i < W * H; i++) { px[i * 4] = 7; px[i * 4 + 1] = 22; px[i * 4 + 2] = 13; px[i * 4 + 3] = 255; }
	const set = (x, y, r, g, b) => { if (x < 0 || y < 0 || x >= W || y >= H) return; const i = (y * W + x) * 4; px[i] = r; px[i + 1] = g; px[i + 2] = b; };
	names.forEach((n, k) => {
		const cx = 8 + (k % COLS) * CELLW, cy = 8 + Math.floor(k / COLS) * CELLH, img = imgs[k];
		for (let y = 0; y < 32 * SC + 8; y++) for (let x = 0; x < 32 * SC + 8; x++) set(cx + x - 4, cy + y - 4, 12, 40, 22);
		for (let y = 0; y < 32 * SC; y++) for (let x = 0; x < 32 * SC; x++) {
			const s = ((y / SC | 0) * 32 + (x / SC | 0)) * 4, a = img.px[s + 3] / 255;
			const i = ((cy + y) * W + cx + x) * 4;
			for (let c = 0; c < 3; c++) px[i + c] = Math.round(img.px[s + c] * a + px[i + c] * (1 - a));
		}
		const label = n.replace(/^c_/, '').toUpperCase().slice(0, 24);
		let lx = cx - 2;
		for (const ch of label) {
			const g = FONT[ch];
			if (g) for (let gy = 0; gy < 5; gy++) for (let gx = 0; gx < 3; gx++) if (g[gy * 3 + gx] === '1') set(lx + gx, cy + 32 * SC + 8 + gy, 168, 255, 192);
			lx += 4;
		}
	});
	return encode(W, H, px);
}

// ------------------------------------------------------------------ write
fs.mkdirSync(OUT, { recursive: true });
fs.mkdirSync(path.dirname(SHEET), { recursive: true });
const names = Object.keys(ICONS), imgs = [];
for (const n of names) {
	const img = style(ICONS[n]);
	imgs.push(img);
	fs.writeFileSync(path.join(OUT, n + '.png'), encode(img.w, img.h, img.px));
}
const em = emblemImage();
fs.writeFileSync(path.join(OUT, '_emblem.png'), encode(em.w, em.h, em.px));
fs.writeFileSync(SHEET, sheet(names, imgs));
console.log('wrote ' + names.length + ' icons + _emblem.png to ' + path.relative(ROOT, OUT));
