package com.projecthero.mod.client.symbiote;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteAnim;
import com.projecthero.mod.symbiote.SymbioteColours;
import com.projecthero.mod.symbiote.SymbioteVitals;
import com.projecthero.mod.symbiote.SymbioteVitalsManager;
import com.projecthero.mod.symbiote.SymbioteWeapon;
import com.projecthero.mod.symbiote.SymbioteWeaponMoves;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 (user: "improve the symbiote blade model ... just a tiny blade coming from the hand, create an actual blade model and create
 * other weapons like axes and stuff"): the four weapons a Normal host's arm grows, as procedural living meshes (the {@link GooMesh}
 * technique the tendrils use -- they bend, grow and melt every frame, so they are built in code rather than a model file).
 *
 * <p>Drawn in the arm bone's own space (after {@code ModelPart#translateAndRotate}: one unit = one block, +Y runs down the arm to the
 * fist at 10 px, -Z is the arm's front, the arm's outer side is -X on the right arm), so every swing and pose carries them -- the
 * GeckoLib suit copies the same vanilla arm. Slim (3 px) arms put the centre line half a pixel in.
 * <ul>
 *   <li><b>Blade</b> -- the Venom-film crescent scythe: a lens-section plate ~1.6 blocks round the arc that roots on the outer forearm,
 *       sweeps down past the fist and hooks forward to a point; the half near the arm is an organic lattice (holes with walled rims, the
 *       way the film's blade is woven from tendrils), the outer edge a pale cutting edge in the emblem colour.</li>
 *   <li><b>Axe</b> -- a thick tendril haft over the fist and a single heavy bearded bit with a convex edge, lattice holes by the haft
 *       and a spine spike behind.</li>
 *   <li><b>Mace</b> -- a lumpy ball of hardened symbiote on a thick tendril, sixteen conical spikes with pale tips.</li>
 *   <li><b>Whip</b> -- a barbed tendril coiled twice round the forearm, its tail swaying from the fist; on Lash it cracks out to 7
 *       blocks down the aim and recoils.</li>
 * </ul>
 * Every weapon grows from the forearm over {@link #GROW_TICKS} (tendrils weave out to the growth front, the shape fills in behind
 * them) and the old one melts back the same way, read from the synced {@link SymbioteVitals#formSince} / {@link SymbioteVitals#prevForm},
 * so every viewer sees it. Colours: the goo texture in the host's body colour ({@link SymbioteSuitTint#goo}), the cutting edges and
 * spike tips in the emblem colour mixed into the body colour.
 */
public final class SymbioteWeaponRenderer {
	static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/symbiote_weapon.png");
	static final ResourceLocation EDGE = ProjectHeroMod.id("textures/entity/symbiote_weapon_edge.png");
	public static final int GROW_TICKS = 6;

	private SymbioteWeaponRenderer() {
	}

	// ------------------------------------------------------------------ what to draw

	/** A weapon out or melting back on {@code player}? */
	public static boolean visible(Player player) {
		if (player.isInvisible() || !Symbiote.hasSymbiote(player)) {
			return false;
		}
		SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		if (v == null) {
			return false;
		}
		return v.bladeActive || melting(v, player.level().getGameTime(), 0f) != null;
	}

	private static float age(SymbioteVitals v, long now, float partial) {
		return v.formSince <= 0L ? 1000f : (now - v.formSince) + partial;
	}

	/** The weapon melting back right now (the previous Form's), or null. */
	private static SymbioteWeapon melting(SymbioteVitals v, long now, float partial) {
		SymbioteWeapon prev = SymbioteVitalsManager.weaponOf(v.prevForm);
		if (prev == null || age(v, now, partial) >= GROW_TICKS) {
			return null;
		}
		if (v.bladeActive && prev.ordinal() == v.weapon) {
			return null;
		}
		return prev;
	}

	/**
	 * Draws whatever the arm is growing, in arm-bone space. {@code firstPerson} draws a touch smaller (the weapon is right in front of
	 * the camera there).
	 */
	public static void draw(PoseStack pose, MultiBufferSource buffers, int light, Player owner, boolean rightArm, boolean slim,
			boolean firstPerson) {
		SymbioteVitals v = owner.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		if (v == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		long now = owner.level().getGameTime();
		float a = age(v, now, partial);
		SymbioteWeapon old = melting(v, now, partial);
		if (old != null) {
			drawOne(pose, buffers, light, owner, rightArm, slim, firstPerson, now + partial, v, old, 1f - Mth.clamp(a / GROW_TICKS, 0f, 1f));
		}
		if (v.bladeActive) {
			drawOne(pose, buffers, light, owner, rightArm, slim, firstPerson, now + partial, v, SymbioteWeapon.byOrdinal(v.weapon),
					Mth.clamp(a / GROW_TICKS, 0f, 1f));
		}
	}

	/**
	 * Third-person size of each weapon, scaled about the fist (filming pass: at 1x they read as a small claw on the 2.7-block suited
	 * host). The Mace stays smaller so its hanging ball clears the ground. First person keeps the 0.85x it was framed at.
	 */
	static float scaleOf(SymbioteWeapon w, boolean firstPerson) {
		if (firstPerson) {
			return 0.85f;
		}
		return w == SymbioteWeapon.MACE ? 1.25f : 1.5f;
	}

	private static void drawOne(PoseStack pose, MultiBufferSource buffers, int light, Player owner, boolean rightArm, boolean slim,
			boolean firstPerson, float time, SymbioteVitals v, SymbioteWeapon w, float grow) {
		float s = scaleOf(w, firstPerson);
		double xc = (rightArm ? -1.0 : 1.0) * (slim ? 0.5 : 1.0) / 16.0;
		double pivotY = firstPerson ? 0.45 : 0.55;
		pose.pushPose();
		pose.translate(xc, pivotY, 0);
		pose.scale(s, s, s);
		pose.translate(-xc, -pivotY, 0);
		Ctx c = new Ctx(pose, buffers, light, owner, rightArm, slim, time, v);
		c.wscale = s;
		c.render(w, grow);
		pose.popPose();
	}

	// ------------------------------------------------------------------ the meshes

	private static final class Ctx {
		final PoseStack.Pose pose;
		// v0.17.5 fix: the buffers are fetched right before each write -- asking a MultiBufferSource for another render type ends the
		// previous one ("Not building!" crash when a held VertexConsumer is written to after another type was fetched)
		final MultiBufferSource buffers;
		final RenderType bodyType;
		final RenderType edgeType;
		final int light;
		final int edgeRgb;
		final double xc;
		final double side; // -1 on the right arm (its outer side is -X), +1 on the left
		final float time;
		final Player owner;
		final SymbioteVitals vitals;
		/** The pose scale this weapon is drawn at (the Lash reaches its real 7 blocks whatever it is). */
		float wscale = 1f;

		Ctx(PoseStack pose, MultiBufferSource buffers, int light, Player owner, boolean rightArm, boolean slim, float time, SymbioteVitals v) {
			this.pose = pose.last();
			this.buffers = buffers;
			this.bodyType = RenderType.entityCutoutNoCull(SymbioteSuitTint.goo(owner, TEXTURE));
			this.edgeType = RenderType.entityCutoutNoCull(EDGE);
			this.light = light;
			this.side = rightArm ? -1.0 : 1.0;
			this.xc = side * (slim ? 0.5 : 1.0) / 16.0;
			this.time = time;
			this.owner = owner;
			this.vitals = v;
			SymbioteColours col = SymbioteSuitTint.colours(owner);
			this.edgeRgb = mix(col.primary(), col.secondary(), 0.6f);
		}

		void render(SymbioteWeapon w, float grow) {
			if (grow <= 0.001f) {
				return;
			}
			bindings(grow);
			switch (w) {
				case BLADE -> blade(grow);
				case AXE -> axe(grow);
				case MACE -> mace(grow);
				case WHIP -> whip(grow);
			}
		}

		VertexConsumer body() {
			return buffers.getBuffer(bodyType);
		}

		VertexConsumer edge() {
			return buffers.getBuffer(edgeType);
		}

		Vec3 p(double x, double y, double z) {
			return new Vec3(x, y, z);
		}

		void tube(Vec3[] pts, float[] radii, int sides, boolean edgeTex) {
			GooMesh.tube(edgeTex ? edge() : body(), pose, light, edgeTex ? edgeRgb : 0xFFFFFF, pts, radii, sides, 0.3f);
		}

		/** A tapering tube along {@code pts} from radius {@code r0} to {@code r1}. */
		void strand(Vec3[] pts, float r0, float r1) {
			float[] radii = new float[pts.length];
			for (int i = 0; i < pts.length; i++) {
				radii[i] = Mth.lerp((float) i / Math.max(1, pts.length - 1), r0, r1);
			}
			tube(pts, radii, 5, false);
		}

		/** A cone from {@code base} along {@code dir} ({@code len} long, {@code r} at the base); the last third in the edge colour. */
		void spike(Vec3 base, Vec3 dir, double len, float r, boolean paleTip) {
			Vec3 d = dir.normalize();
			Vec3 mid = base.add(d.scale(len * 0.62));
			Vec3 tip = base.add(d.scale(len));
			tube(new Vec3[]{base, mid}, new float[]{r, r * 0.42f}, 5, false);
			tube(new Vec3[]{mid, tip}, new float[]{r * 0.42f, 0.002f}, 5, paleTip);
		}

		/**
		 * The tendrils that bind every weapon to the forearm: three strands spiralling round the sleeve from the elbow end to the fist
		 * (they grow first), and while the weapon grows, three thin ones racing ahead to its growth front.
		 */
		void bindings(float grow) {
			double r = 0.168;
			int n = 14;
			for (int k = 0; k < 3; k++) {
				double len = Mth.clamp(grow * 2.2f, 0f, 1f);
				int count = Math.max(2, (int) Math.round(n * len));
				Vec3[] pts = new Vec3[count];
				for (int i = 0; i < count; i++) {
					double t = (double) i / (n - 1);
					double ang = k * 2.094 + t * 5.2 + Math.sin(time * 0.05 + k) * 0.08;
					double rr = r + 0.012 * Math.sin(t * 9 + k);
					pts[i] = p(xc + Math.cos(ang) * rr, 0.26 + t * 0.42, Math.sin(ang) * rr);
				}
				strand(pts, 0.026f, 0.018f);
			}
		}

		/** Growth tendrils weaving out from the fist to {@code front} while {@code grow} < 1. */
		void weave(float grow, Vec3 front) {
			if (grow >= 0.999f) {
				return;
			}
			Vec3 from = p(xc, 0.6, 0);
			for (int k = 0; k < 3; k++) {
				int n = 10;
				Vec3[] pts = new Vec3[n];
				Vec3 span = front.subtract(from);
				Vec3 sideV = span.cross(new Vec3(1, 0, 0));
				sideV = sideV.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : sideV.normalize();
				Vec3 sideX = new Vec3(1, 0, 0);
				for (int i = 0; i < n; i++) {
					double t = (double) i / (n - 1);
					double ang = k * 2.094 + t * 7 + time * 0.6;
					double amp = 0.06 * Math.sin(Math.PI * t);
					pts[i] = from.add(span.scale(t)).add(sideV.scale(Math.cos(ang) * amp)).add(sideX.scale(Math.sin(ang) * amp));
				}
				strand(pts, 0.02f, 0.006f);
			}
		}

		// ---------------- plates (Blade, Axe): a lens-section sheet with holes, rims and a pale edge band ----------------

		interface Surface {
			/** Mid-surface point at (s along, u across 0..1). */
			Vec3 at(double s, double u);

			/** Full thickness there. */
			double thick(double s, double u);

			boolean hole(double s, double u);
		}

		/**
		 * Draws the sheet over s in [0, sMax] (growth), u in [0, 1], {@code ns} x {@code nu} cells; the plate's normal is X. Cells with
		 * u above {@code 1 - edgeBand} use the edge texture and colour (the cutting edge).
		 */
		void plate(Surface sf, int ns, int nu, double sMax, double edgeBand) {
			int is = Math.max(1, (int) Math.ceil(ns * Math.min(1.0, sMax)));
			Vec3[][] top = new Vec3[is + 1][nu + 1];
			Vec3[][] bot = new Vec3[is + 1][nu + 1];
			boolean[][] solid = new boolean[is][nu];
			for (int i = 0; i <= is; i++) {
				double s = Math.min(sMax, (double) i / ns);
				for (int j = 0; j <= nu; j++) {
					double u = (double) j / nu;
					Vec3 m = sf.at(s, u);
					double t = sf.thick(s, u) * 0.5;
					top[i][j] = m.add(t, 0, 0);
					bot[i][j] = m.add(-t, 0, 0);
				}
			}
			for (int i = 0; i < is; i++) {
				for (int j = 0; j < nu; j++) {
					solid[i][j] = !sf.hole((i + 0.5) / ns, (j + 0.5) / nu);
				}
			}
			for (int pass = 0; pass < 2; pass++) {
			VertexConsumer vc = pass == 0 ? body() : edge();
			for (int i = 0; i < is; i++) {
				float v0 = (float) i / ns, v1 = (float) (i + 1) / ns;
				for (int j = 0; j < nu; j++) {
					if (!solid[i][j]) {
						continue;
					}
					float u0 = (float) j / nu, u1 = (float) (j + 1) / nu;
					boolean e = (j + 0.5) / nu > 1.0 - edgeBand;
					if (e != (pass == 1)) {
						continue;
					}
					int rgb = e ? edgeRgb : 0xFFFFFF;
					GooMesh.quad(vc, pose, light, rgb, top[i][j], top[i][j + 1], top[i + 1][j + 1], top[i + 1][j], u0, v0, u1, v1);
					GooMesh.quad(vc, pose, light, rgb, bot[i][j], bot[i + 1][j], bot[i + 1][j + 1], bot[i][j + 1], u0, v0, u1, v1);
					// rims: wherever the neighbour is a hole (or the sheet ends), close the gap between the faces
					if (j == 0 || !solid[i][j - 1]) {
						wall(vc, top[i][j], top[i + 1][j], bot[i + 1][j], bot[i][j], v0, v1);
					}
					if (j == nu - 1 || !solid[i][j + 1]) {
						wall(vc, top[i + 1][j + 1], top[i][j + 1], bot[i][j + 1], bot[i + 1][j + 1], v0, v1);
					}
					if (i == 0 || !solid[i - 1][j]) {
						wall(vc, top[i][j + 1], top[i][j], bot[i][j], bot[i][j + 1], u0, u1);
					}
					if (i == is - 1 || !solid[i + 1][j]) {
						wall(vc, top[i + 1][j], top[i + 1][j + 1], bot[i + 1][j + 1], bot[i + 1][j], u0, u1);
					}
				}
			}
			}
		}

		void wall(VertexConsumer vc, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float t0, float t1) {
			if (a.distanceToSqr(d) < 1.0e-7 && b.distanceToSqr(c) < 1.0e-7) {
				return;
			}
			GooMesh.quad(vc, pose, light, 0x9A9A9A, a, b, c, d, t0, 0.2f, t1, 0.28f);
		}

		// ---------------- Blade: the crescent scythe ----------------

		private static final double[][] BLADE_HOLES = {
				// s, u, radius in s, radius in u
				{ 0.06, 0.52, 0.045, 0.22 }, { 0.15, 0.36, 0.04, 0.17 }, { 0.17, 0.72, 0.035, 0.15 }, { 0.255, 0.52, 0.045, 0.2 },
				{ 0.33, 0.3, 0.03, 0.14 }, { 0.35, 0.66, 0.035, 0.14 }, { 0.425, 0.48, 0.03, 0.13 }, { 0.49, 0.32, 0.018, 0.09 },
				{ 0.5, 0.62, 0.02, 0.1 }, { 0.56, 0.47, 0.014, 0.07 } };

		void blade(float grow) {
			double bx = xc + side * 0.035;
			double cy = 0.43, cz = -0.42, R = 0.5;
			double th0 = Math.toRadians(102), th1 = Math.toRadians(-74);
			double sMax = grow * 1.06;
			Surface sf = new Surface() {
				double width(double s) {
					return (0.085 + 0.31 * Math.pow(Math.sin(Math.PI * Math.pow(s, 0.75)), 0.85)) * Math.pow(1.0 - s, 0.38);
				}

				@Override
				public Vec3 at(double s, double u) {
					double th = th0 + (th1 - th0) * s;
					double w = width(s);
					double r = R - 0.4 * w + u * w + 0.008 * Math.sin(s * 31 + u * 4);
					return new Vec3(bx, cy + r * Math.cos(th), cz + r * Math.sin(th));
				}

				@Override
				public double thick(double s, double u) {
					double lens = Math.pow(Math.max(0.0, 1.0 - Math.pow(2 * u - 1, 2)), 0.55);
					double ridge = 1.0 + 0.18 * Math.sin(s * 23 + u * 7) * (s < 0.6 ? 1 : 0.3);
					return (0.064 * Math.pow(1.0 - s, 0.55) + 0.012) * lens * ridge + 0.004;
				}

				@Override
				public boolean hole(double s, double u) {
					for (double[] h : BLADE_HOLES) {
						double ds = (s - h[0]) / h[2], du = (u - h[1]) / h[3];
						if (ds * ds + du * du < 1.0) {
							return true;
						}
					}
					return false;
				}
			};
			plate(sf, 56, 12, sMax, 0.13);
			// the lattice's tendril strands: running along the inner and middle of the woven half, and binding the root to the arm
			for (int k = 0; k < 3; k++) {
				double u = 0.18 + k * 0.32;
				int n = 12;
				int count = Math.max(2, (int) Math.round(n * Math.min(1.0, sMax / 0.62)));
				Vec3[] pts = new Vec3[count];
				for (int i = 0; i < count; i++) {
					double s = 0.62 * i / (n - 1);
					pts[i] = sf.at(s, u + 0.06 * Math.sin(s * 20 + k)).add(side * (k == 1 ? 0.03 : -0.028), 0, 0);
				}
				strand(pts, 0.024f, 0.01f);
			}
			// spines along the outer back of the crescent's root, pointing back up the arm
			for (int k = 0; k < 4 && sMax > 0.12 + k * 0.09; k++) {
				double s = 0.08 + k * 0.09;
				Vec3 base = sf.at(s, 0.02);
				Vec3 tangentIn = sf.at(s, 0.0).subtract(sf.at(s, 0.3)).normalize();
				spike(base, tangentIn.add(0, -0.4, 0), 0.07 + 0.02 * k, 0.018f, true);
			}
			weave(grow, sf.at(Math.min(1.0, sMax), 0.5));
		}

		// ---------------- Axe ----------------

		void axe(float grow) {
			double x0 = xc;
			// the haft: a thick tendril over the fist with a knot at the end
			double haftLen = Mth.clamp(grow / 0.35f, 0f, 1f);
			int hn = 8;
			int hc = Math.max(2, (int) Math.round(hn * haftLen));
			Vec3[] haft = new Vec3[hc];
			float[] hr = new float[hc];
			for (int i = 0; i < hc; i++) {
				double t = (double) i / (hn - 1);
				haft[i] = p(x0, 0.42 + t * 0.53, 0.012 * Math.sin(t * 6));
				hr[i] = (float) (0.074 - 0.014 * t + 0.008 * Math.sin(t * 13));
			}
			tube(haft, hr, 7, false);
			if (haftLen >= 1f) {
				tube(new Vec3[]{p(x0, 0.94, 0), p(x0, 0.99, 0), p(x0, 1.03, 0)}, new float[]{0.07f, 0.075f, 0.01f}, 7, false);
			}
			// helix strands round the haft
			for (int k = 0; k < 2; k++) {
				int n = 10;
				int count = Math.max(2, (int) Math.round(n * haftLen));
				Vec3[] pts = new Vec3[count];
				for (int i = 0; i < count; i++) {
					double t = (double) i / (n - 1);
					double ang = k * Math.PI + t * 9;
					pts[i] = p(x0 + Math.cos(ang) * 0.078, 0.46 + t * 0.47, Math.sin(ang) * 0.078);
				}
				strand(pts, 0.018f, 0.014f);
			}
			double headGrow = Mth.clamp((grow - 0.3f) / 0.7f, 0f, 1f);
			if (headGrow <= 0.0) {
				weave(grow, haft[hc - 1]);
				return;
			}
			// the bit: s runs down its height, u from the haft (0) out to the edge (1)
			Surface sf = new Surface() {
				@Override
				public Vec3 at(double s, double u) {
					double uu = u * headGrow;
					double top = 0.62 - 0.25 * Math.pow(uu, 1.5);
					double bottom = 0.86 + 0.31 * Math.pow(uu, 1.5);
					double y = top + s * (bottom - top);
					double z = -(0.065 + uu * 0.56) - 0.07 * Math.sin(Math.PI * s) * uu * uu;
					return new Vec3(x0, y, z);
				}

				@Override
				public double thick(double s, double u) {
					double uu = u * headGrow;
					double edgeTaper = Math.pow(1.0 - uu, 0.75);
					double rim = s < 0.06 || s > 0.94 ? 0.6 : 1.0;
					return (0.12 * edgeTaper + 0.006) * rim * (1.0 + 0.12 * Math.sin(s * 17 + uu * 9));
				}

				@Override
				public boolean hole(double s, double u) {
					double uu = u * headGrow;
					double[][] holes = { { 0.5, 0.25, 0.2, 0.075 }, { 0.2, 0.42, 0.07, 0.05 }, { 0.8, 0.45, 0.08, 0.05 }, { 0.5, 0.48, 0.07, 0.04 } };
					for (double[] h : holes) {
						double ds = (s - h[0]) / h[2], du = (uu - h[1]) / h[3];
						if (ds * ds + du * du < 1.0) {
							return true;
						}
					}
					return false;
				}
			};
			plate(sf, 18, 12, 1.0, headGrow > 0.85 ? 0.12 : 0.0);
			// veins across both faces, from the haft fanning to the edge
			for (int k = 0; k < 3; k++) {
				double s = 0.2 + k * 0.3;
				for (int f = -1; f <= 1; f += 2) {
					int n = 6;
					Vec3[] pts = new Vec3[n];
					for (int i = 0; i < n; i++) {
						double u = 0.05 + 0.7 * i / (n - 1);
						pts[i] = sf.at(s + (s - 0.5) * u * 0.4, u).add(f * sf.thick(s, u) * 0.5, 0, 0);
					}
					strand(pts, 0.016f, 0.006f);
				}
			}
			// the spine spike behind the haft, and a smaller one under it
			if (headGrow > 0.4) {
				double g = (headGrow - 0.4) / 0.6;
				spike(p(x0, 0.69, 0.05), new Vec3(0, -0.12, 1), 0.42 * g, 0.068f, true);
				spike(p(x0, 0.83, 0.05), new Vec3(0, 0.25, 1), 0.22 * g, 0.045f, true);
			}
			weave(grow, sf.at(0.5, 1.0));
		}

		// ---------------- Mace ----------------

		void mace(float grow) {
			double x0 = xc;
			double cord = Mth.clamp(grow / 0.4f, 0f, 1f);
			int n = 9;
			int count = Math.max(2, (int) Math.round(n * cord));
			Vec3[] pts = new Vec3[count];
			float[] radii = new float[count];
			for (int i = 0; i < count; i++) {
				double t = (double) i / (n - 1);
				pts[i] = p(x0 + 0.015 * Math.sin(t * 5 + time * 0.08), 0.46 + t * 0.4, -0.04 * t * t);
				radii[i] = (float) (0.085 - 0.02 * t + 0.01 * Math.sin(t * 11));
			}
			tube(pts, radii, 7, false);
			// the strands wrapping the cord
			for (int k = 0; k < 3; k++) {
				Vec3[] s = new Vec3[Math.max(2, (int) Math.round(10 * cord))];
				for (int i = 0; i < s.length; i++) {
					double t = (double) i / 9;
					double ang = k * 2.094 + t * 8;
					s[i] = p(x0 + Math.cos(ang) * 0.085, 0.5 + t * 0.36, -0.04 * t * t + Math.sin(ang) * 0.085);
				}
				strand(s, 0.02f, 0.014f);
			}
			double ballGrow = Mth.clamp((grow - 0.3f) / 0.7f, 0f, 1f);
			if (ballGrow <= 0.0) {
				weave(grow, pts[count - 1]);
				return;
			}
			Vec3 c = p(x0, 1.06, -0.05);
			double R = 0.23 * (0.25 + 0.75 * ballGrow);
			int rings = 10, segs = 14;
			Vec3[][] sph = new Vec3[rings + 1][segs + 1];
			for (int i = 0; i <= rings; i++) {
				double th = Math.PI * i / rings;
				for (int j = 0; j <= segs; j++) {
					double ph = Math.PI * 2 * j / segs;
					double lump = 1.0 + 0.09 * Math.sin(th * 5 + ph * 3) + 0.06 * Math.sin(th * 9 - ph * 4);
					Vec3 d = new Vec3(Math.sin(th) * Math.cos(ph), Math.cos(th), Math.sin(th) * Math.sin(ph));
					sph[i][j] = c.add(d.scale(R * lump));
				}
			}
			VertexConsumer ball = body();
			for (int i = 0; i < rings; i++) {
				for (int j = 0; j < segs; j++) {
					GooMesh.quad(ball, pose, light, 0xFFFFFF, sph[i][j], sph[i][j + 1], sph[i + 1][j + 1], sph[i + 1][j],
							(float) j / segs, (float) i / rings, (float) (j + 1) / segs, (float) (i + 1) / rings);
				}
			}
			// sixteen spikes (Fibonacci sphere), none pointing back up the cord
			int spikes = 18;
			double golden = Math.PI * (3 - Math.sqrt(5));
			for (int k = 0; k < spikes; k++) {
				double y = 1 - 2.0 * (k + 0.5) / spikes;
				double rr = Math.sqrt(1 - y * y);
				double ph = golden * k;
				Vec3 d = new Vec3(rr * Math.cos(ph), y, rr * Math.sin(ph));
				if (d.y < -0.72) {
					continue; // that way is the cord
				}
				double len = (0.17 + 0.05 * Math.sin(k * 2.3)) * ballGrow;
				spike(c.add(d.scale(R * 0.92)), d, R * 0.08 + len, (float) (0.05 * (0.4 + 0.6 * ballGrow)), true);
			}
			weave(grow, c);
		}

		// ---------------- Whip ----------------

		void whip(float grow) {
			double x0 = xc;
			// coiled twice round the forearm
			int coilN = 16;
			int coilCount = Math.max(2, (int) Math.round(coilN * Mth.clamp(grow / 0.4f, 0f, 1f)));
			Vec3[] coil = new Vec3[coilCount];
			for (int i = 0; i < coilCount; i++) {
				double t = (double) i / (coilN - 1);
				double ang = t * Math.PI * 4 + 1.2;
				coil[i] = p(x0 + Math.cos(ang) * 0.185, 0.3 + t * 0.34, Math.sin(ang) * 0.185);
			}
			strand(coil, 0.05f, 0.048f);
			double tailGrow = Mth.clamp((grow - 0.3f) / 0.7f, 0f, 1f);
			if (tailGrow <= 0.0) {
				return;
			}
			Vec3 root = coil[coilCount - 1];
			// the lash: the arm is aimed (SymbiotePose), so the whip cracks out along +Y to 7 blocks and recoils
			double lashT = -1;
			if (vitals.animId == SymbioteAnim.WHIP_LASH) {
				lashT = time - vitals.animStart;
			}
			int n = 30;
			Vec3[] pts = new Vec3[n];
			float[] radii = new float[n];
			double restLen = 1.0 * tailGrow;
			double scale = Math.max(1.0, owner.getScale());
			double fullLen = SymbioteWeaponMoves.LASH_RANGE * scale / Math.max(0.01, owner.getScale()) / wscale;
			for (int i = 0; i < n; i++) {
				double s = (double) i / (n - 1);
				Vec3 q;
				if (lashT >= 0 && lashT < 14) {
					q = lashPoint(root, s, lashT, restLen, fullLen);
				} else {
					// a loop: down from the fist, round, and back up in front of the forearm, swaying
					double sway = Math.sin(time * 0.09 + s * 3) * 0.08 * s;
					double sway2 = Math.cos(time * 0.07 + s * 2.4) * 0.06 * s;
					double ang = s * Math.PI * 1.15;
					q = root.add(sway2 * side - 0.05 * side * Math.sin(ang), 0.36 * restLen * Math.sin(ang), -0.42 * restLen * (1 - Math.cos(ang)) * 0.5 + sway);
				}
				pts[i] = q;
				radii[i] = (float) (0.048 * (1.0 - 0.75 * s) + 0.006);
			}
			tube(pts, radii, 6, false);
			// barbs every few segments, raking back toward the root on alternating sides
			for (int i = 3; i < n - 2; i += 2) {
				Vec3 dir = pts[i - 1].subtract(pts[i + 1]).normalize();
				Vec3 out = dir.cross(new Vec3(1, 0, 0));
				out = out.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : out.normalize();
				Vec3 outX = new Vec3(1, 0, 0);
				double ang = i * 1.7;
				Vec3 o = out.scale(Math.cos(ang)).add(outX.scale(Math.sin(ang)));
				spike(pts[i].add(o.scale(radii[i] * 0.8)), dir.scale(0.8).add(o), 0.05 + 0.03 * (1.0 - (double) i / n), radii[i] * 0.7f, false);
			}
			// the barbed head
			Vec3 tip = pts[n - 1];
			Vec3 back = pts[n - 3].subtract(tip).normalize();
			Vec3 o1 = back.cross(new Vec3(1, 0, 0));
			o1 = o1.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : o1.normalize();
			Vec3 fwd = back.scale(-1);
			spike(tip, fwd, 0.13, 0.03f, true);
			spike(tip.add(fwd.scale(0.03)), back.add(o1.scale(1.2)), 0.11, 0.022f, true);
			spike(tip.add(fwd.scale(0.03)), back.add(o1.scale(-1.2)), 0.11, 0.022f, true);
			weave(grow, pts[n - 1]);
		}

		/** One point of the whip during Lash at {@code t} ticks: curl back, crack out to {@code full}, recoil to the rest length. */
		private Vec3 lashPoint(Vec3 root, double s, double t, double restLen, double full) {
			double len;
			double curl;
			int d = SymbioteWeaponMoves.LASH_DELAY;
			if (t < d) {
				len = restLen;
				curl = t / d; // drawn back over the shoulder
			} else if (t < d + 3) {
				double k = (t - d) / 3.0;
				len = Mth.lerp(k * k, restLen, full);
				curl = 1.0 - k;
			} else {
				double k = Math.min(1.0, (t - d - 3) / 8.0);
				len = Mth.lerp(1 - (1 - k) * (1 - k), full, restLen);
				curl = 0;
			}
			double wave = Math.sin(s * 9 - t * 2.2) * 0.12 * s * (len / full);
			double y = s * len * Math.cos(curl * 2.4 * s);
			double z = s * len * Math.sin(curl * 2.4 * s) + wave; // curled back toward +Z (the arm's back), then straight out
			return root.add(0, y, z);
		}

		private static int mix(int a, int b, float t) {
			int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
			int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
			int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
			return (r << 16) | (g << 8) | bl;
		}
	}
}
