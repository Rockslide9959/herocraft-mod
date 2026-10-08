package com.projecthero.mod.client.symbiote;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.symbiote.entity.SymbioteEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The free Symbiote as a living puddle of glossy black goo (v0.13.19). Entirely procedural -- no model
 * file -- rebuilt every frame from a handful of sine waves, so it never sits still:
 * <ul>
 *   <li><b>Body</b>: a low dome on a polar grid ({@value #RINGS} rings x {@value #SEGS} segments). Its rim
 *       wobbles with three out-of-phase sine lobes, its height breathes, it stretches along its heading and
 *       ripples peristaltically while it crawls, rears up when a player reaches for it (held), flattens when
 *       it flees, and bristles into spikes when it recoils. Normals come from finite differences of the
 *       surface so vanilla entity lighting shades it properly.</li>
 *   <li><b>Skirt</b>: a thin fading ring of black around the rim, so it reads as goo spread on the floor.</li>
 *   <li><b>Pseudopods</b>: 3-6 tapered tubes (count seeded from the entity id) that rise, retract, curl
 *       and -- while it crawls or hunts -- lean and reach toward its heading.</li>
 *   <li><b>Wet sheen</b>: a few soft white / violet highlight patches that slide over the dome, sampled
 *       from the swatches in the corner of the texture (translucent, fading at the edges, drawn in a
 *       second pass after the opaque body).</li>
 * </ul>
 * Motion is smooth for every player: the entity lerps its own position between tracker updates
 * ({@link SymbioteEntity#lerpTo}) and the animation inputs (crawl / posture / bristle) are eased
 * client-side from the synced mood. Everything lives in the entity's own render pass; no static state.
 */
public class SymbioteEntityRenderer extends EntityRenderer<SymbioteEntity> {
	private static final ResourceLocation TEX = ProjectHeroMod.id("textures/entity/symbiote_goo.png");
	/** Body UVs stay left of the highlight swatches at x 60..63. */
	private static final float BODY_U_MAX = 58.0f / 64.0f;
	private static final float SHEEN_U = 61.5f / 64.0f;
	private static final float SHEEN_WHITE_V = 1.5f / 32.0f;
	private static final float SHEEN_VIOLET_V = 5.5f / 32.0f;

	/** Vertex tint over the texture: keeps the goo properly black, leaving the sheen patches to shine. */
	private static final int BODY_R = 170;
	private static final int BODY_G = 165;
	private static final int BODY_B = 185;

	private static final int RINGS = 7;
	private static final int SEGS = 24;
	private static final int TUBE_SIDES = 6;
	private static final int TUBE_SEGS = 7;
	private static final float TWO_PI = (float) (Math.PI * 2.0);

	// Per-frame scratch (the render thread is the only caller).
	private final float[] gx = new float[(RINGS + 1) * SEGS];
	private final float[] gy = new float[(RINGS + 1) * SEGS];
	private final float[] gz = new float[(RINGS + 1) * SEGS];
	private final float[] nx = new float[(RINGS + 1) * SEGS];
	private final float[] ny = new float[(RINGS + 1) * SEGS];
	private final float[] nz = new float[(RINGS + 1) * SEGS];
	private final float[] tmp = new float[3];
	private final float[] tmp2 = new float[3];
	private final float[][] tube = new float[TUBE_SEGS + 1][3];

	// Current frame's shape parameters (set in render before the surface is sampled).
	private float t;
	private float radius;
	private float height;
	private float stretchX;
	private float stretchZ;
	private float crawl;
	private float bristle;

	public SymbioteEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.5f;
		this.shadowStrength = 0.7f;
	}

	@Override
	public void render(SymbioteEntity entity, float yaw, float partialTicks, PoseStack pose,
			MultiBufferSource buffers, int light) {
		t = entity.tickCount + partialTicks + (entity.getId() % 97) * 37.0f;
		crawl = Mth.lerp(partialTicks, entity.crawlO, entity.crawl);
		bristle = Mth.lerp(partialTicks, entity.bristleO, entity.bristle);
		float alert = Mth.lerp(partialTicks, entity.alertO, entity.alert);
		float hunt = Mth.lerp(partialTicks, entity.huntO, entity.hunt);
		float rear = Math.max(0.0f, alert);
		float cower = Math.max(0.0f, -alert);
		// v0.15.15 Call Carnage: it reddens while being channelled, then (consumed) turns fully crimson and dissolves
		float crimsonAmt = entity.crimson();
		float dissolve = Mth.clamp(crimsonAmt - 1.0f, 0.0f, 1.0f);
		ResourceLocation tex = crimsonAmt > 0.01f ? crimsonTexture() : TEX;
		colorScale = crimsonAmt > 0.01f ? 0.12f + 0.88f * Math.min(1.0f, crimsonAmt) : 1.0f;

		float breathe = 1.0f + 0.07f * Mth.sin(t * 0.06f);
		radius = (0.52f - 0.07f * rear + 0.07f * cower) * (1.0f + 0.03f * Mth.sin(t * 0.045f + 1.3f));
		height = (0.30f + 0.18f * rear - 0.11f * cower + 0.03f * hunt) * breathe;
		stretchZ = 1.0f + 0.4f * crawl;
		stretchX = 1.0f - 0.18f * crawl;
		if (dissolve > 0.0f) {
			float keep = 1.0f - dissolve;
			radius *= 0.25f + 0.75f * keep;
			height *= keep * keep;
		}

		pose.pushPose();
		float bodyYaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
		pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
		PoseStack.Pose p = pose.last();
		buildDome();
		// Opaque body first, in its own pass, so the translucent film and sheen always blend over it
		// (in one shared translucent buffer a sheen quad could sort before the dome under it and punch a
		// see-through hole in it).
		VertexConsumer body = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
		emitDome(body, p, light);
		emitTendrils(body, p, light, entity.getId(), hunt, rear, cower);
		VertexConsumer film = buffers.getBuffer(RenderType.entityTranslucent(tex));
		emitSkirt(film, p, light);
		emitSheen(film, p, light);

		pose.popPose();
		colorScale = 1.0f;
		super.render(entity, yaw, partialTicks, pose, buffers, light);
	}

	// ---------------------------------------------------------------- body surface

	/** Surface point at radial fraction {@code f} (0 centre .. 1 rim) and angle {@code th}, local space (+Z = heading). */
	private void surface(float f, float th, float[] out) {
		float w = 1.0f + 0.10f * Mth.sin(3.0f * th + t * 0.08f) + 0.06f * Mth.sin(5.0f * th - t * 0.13f)
				+ 0.035f * Mth.sin(7.0f * th + t * 0.21f);
		float c = Mth.cos(th);
		// peristalsis: waves running back along the body while it crawls
		w *= 1.0f + 0.07f * crawl * Mth.sin(t * 0.45f - c * 3.0f);
		float r = radius * w * f;
		float x = Mth.sin(th) * r * stretchX;
		float z = c * r * stretchZ + crawl * 0.1f * (1.0f - f * f); // the mass leans into its heading
		float prof = 1.0f - f * f;
		float y = height * (float) Math.pow(Math.max(prof, 0.0f), 0.65)
				* (1.0f + 0.10f * Mth.sin(t * 0.09f + 2.0f * th) + 0.06f * Mth.sin(t * 0.23f - 3.0f * th));
		if (bristle > 0.01f) {
			float spike = Mth.sin(8.0f * th + t * 0.3f);
			spike = spike > 0.0f ? spike * spike * spike * spike * spike * spike : 0.0f;
			y += bristle * 0.2f * spike * Mth.sin((float) Math.PI * f);
		}
		out[0] = x;
		out[1] = y + 0.012f;
		out[2] = z;
	}

	private void buildDome() {
		for (int i = 0; i <= RINGS; i++) {
			float f = (float) i / RINGS;
			for (int j = 0; j < SEGS; j++) {
				surface(f, TWO_PI * j / SEGS, tmp);
				int k = i * SEGS + j;
				gx[k] = tmp[0];
				gy[k] = tmp[1];
				gz[k] = tmp[2];
			}
		}
		for (int i = 0; i <= RINGS; i++) {
			for (int j = 0; j < SEGS; j++) {
				int k = i * SEGS + j;
				if (i == 0) {
					nx[k] = 0.0f;
					ny[k] = 1.0f;
					nz[k] = 0.0f;
					continue;
				}
				int jp = i * SEGS + (j + 1) % SEGS;
				int jm = i * SEGS + (j + SEGS - 1) % SEGS;
				int ip = Math.min(i + 1, RINGS) * SEGS + j;
				int im = (i - 1) * SEGS + j;
				float rx = gx[ip] - gx[im], ry = gy[ip] - gy[im], rz = gz[ip] - gz[im];
				float tx = gx[jp] - gx[jm], ty = gy[jp] - gy[jm], tz = gz[jp] - gz[jm];
				float cx = ry * tz - rz * ty;
				float cy = rz * tx - rx * tz;
				float cz = rx * ty - ry * tx;
				float len = Mth.sqrt(cx * cx + cy * cy + cz * cz);
				if (len < 1.0e-6f) {
					cx = 0.0f;
					cy = 1.0f;
					cz = 0.0f;
					len = 1.0f;
				}
				nx[k] = cx / len;
				ny[k] = cy / len;
				nz[k] = cz / len;
			}
		}
	}

	private void emitDome(VertexConsumer vc, PoseStack.Pose p, int light) {
		float uShift = 0.02f + 0.02f * Mth.sin(t * 0.05f);
		for (int i = 0; i < RINGS; i++) {
			for (int j = 0; j < SEGS; j++) {
				int j1 = (j + 1) % SEGS;
				float u0 = uShift + (BODY_U_MAX - 0.04f) * j / SEGS;
				float u1 = uShift + (BODY_U_MAX - 0.04f) * (j + 1) / SEGS;
				float v0 = 0.02f + 0.94f * i / RINGS;
				float v1 = 0.02f + 0.94f * (i + 1) / RINGS;
				domeVert(vc, p, light, i, j, u0, v0);
				domeVert(vc, p, light, i + 1, j, u0, v1);
				domeVert(vc, p, light, i + 1, j1, u1, v1);
				domeVert(vc, p, light, i, j1, u1, v0);
			}
		}
	}

	private void domeVert(VertexConsumer vc, PoseStack.Pose p, int light, int i, int j, float u, float v) {
		int k = i * SEGS + j;
		// a faint violet cast creeping in toward the rim
		float rim = (float) i / RINGS;
		int r = (int) (BODY_R - 25 * rim);
		int g = (int) (BODY_G - 45 * rim);
		vert(vc, p, gx[k], gy[k], gz[k], r, g, BODY_B, 255, u, v, light, nx[k], ny[k], nz[k]);
	}

	/** A thin fading film of goo spread on the floor around the rim. */
	private void emitSkirt(VertexConsumer vc, PoseStack.Pose p, int light) {
		for (int j = 0; j < SEGS; j++) {
			int j1 = (j + 1) % SEGS;
			int a = RINGS * SEGS + j;
			int b = RINGS * SEGS + j1;
			float s0 = 1.22f + 0.06f * Mth.sin(t * 0.07f + j * 0.9f);
			float s1 = 1.22f + 0.06f * Mth.sin(t * 0.07f + j1 * 0.9f);
			float u = 0.1f + 0.3f * j / SEGS;
			vert(vc, p, gx[a], 0.008f, gz[a], 150, 140, 165, 235, u, 0.9f, light, 0.0f, 1.0f, 0.0f);
			vert(vc, p, gx[a] * s0, 0.004f, gz[a] * s0, 150, 140, 165, 0, u, 0.97f, light, 0.0f, 1.0f, 0.0f);
			vert(vc, p, gx[b] * s1, 0.004f, gz[b] * s1, 150, 140, 165, 0, u, 0.97f, light, 0.0f, 1.0f, 0.0f);
			vert(vc, p, gx[b], 0.008f, gz[b], 150, 140, 165, 235, u, 0.9f, light, 0.0f, 1.0f, 0.0f);
		}
	}

	// ---------------------------------------------------------------- pseudopods

	private void emitTendrils(VertexConsumer vc, PoseStack.Pose p, int light, int id, float hunt, float rear,
			float cower) {
		int count = 3 + Math.floorMod(id, 4);
		for (int k = 0; k < count; k++) {
			float seed = hash(id * 31 + k * 17);
			float phi = TWO_PI * k / count + seed * 0.8f;
			// each pseudopod rises and sinks back on its own slow cycle
			float cyc = 0.5f + 0.5f * Mth.sin(t * (0.045f + 0.012f * k) + seed * TWO_PI);
			cyc = cyc * cyc * (3.0f - 2.0f * cyc);
			float len = (0.12f + 0.34f * cyc) * (1.0f + 0.4f * hunt + 0.3f * rear) * (1.0f - 0.7f * cower)
					+ 0.18f * bristle;
			if (len < 0.06f) {
				continue;
			}
			float ox = Mth.sin(phi);
			float oz = Mth.cos(phi);
			// where on the dome it grows from (sunk a little into the body)
			surface(0.45f + 0.2f * seed, phi, tmp);
			float bx = tmp[0], by = tmp[1] - 0.04f, bz = tmp[2];

			// the direction it reaches for: outward and up, bent toward the heading when crawling/hunting
			float lean = 1.8f * hunt + 0.9f * crawl;
			float rx = ox * 0.7f, ry = 0.25f + 0.9f * rear, rz = oz * 0.7f + lean;
			float rl = Mth.sqrt(rx * rx + ry * ry + rz * rz);
			rx /= rl;
			ry /= rl;
			rz /= rl;
			// sideways curl axis
			float sx = oz, sz = -ox;

			float dx = ox * 0.4f, dy = 1.0f, dz = oz * 0.4f;
			float dl = Mth.sqrt(dx * dx + dy * dy + dz * dz);
			dx /= dl;
			dy /= dl;
			dz /= dl;
			float seg = len / TUBE_SEGS;
			tube[0][0] = bx;
			tube[0][1] = by;
			tube[0][2] = bz;
			for (int m = 1; m <= TUBE_SEGS; m++) {
				float s = (float) m / TUBE_SEGS;
				float curl = Mth.sin(t * 0.12f + k * 2.1f + s * 3.5f) * (0.5f + 0.5f * bristle);
				dx += (rx - dx) * 0.3f + sx * curl * 0.5f;
				dy += (ry - dy) * 0.3f - 0.12f * s * (1.0f - hunt);
				dz += (rz - dz) * 0.3f + sz * curl * 0.5f;
				dl = Mth.sqrt(dx * dx + dy * dy + dz * dz);
				dx /= dl;
				dy /= dl;
				dz /= dl;
				tube[m][0] = tube[m - 1][0] + dx * seg;
				tube[m][1] = Math.max(0.02f, tube[m - 1][1] + dy * seg);
				tube[m][2] = tube[m - 1][2] + dz * seg;
			}
			float thick = 0.045f + 0.02f * seed;
			emitTube(vc, p, light, thick);
		}
	}

	private void emitTube(VertexConsumer vc, PoseStack.Pose p, int light, float thick) {
		for (int m = 0; m <= TUBE_SEGS; m++) {
			float[] a = tube[Math.max(0, m - 1)];
			float[] b = tube[Math.min(TUBE_SEGS, m + 1)];
			float tx = b[0] - a[0], ty = b[1] - a[1], tz = b[2] - a[2];
			float tl = Mth.sqrt(tx * tx + ty * ty + tz * tz);
			if (tl < 1.0e-6f) {
				tx = 0.0f;
				ty = 1.0f;
				tz = 0.0f;
				tl = 1.0f;
			}
			tx /= tl;
			ty /= tl;
			tz /= tl;
			// reference axis for the frame: up, unless the tube is nearly vertical
			float fx = 0.0f, fy = 1.0f, fz = 0.0f;
			if (Math.abs(ty) > 0.9f) {
				fx = 1.0f;
				fy = 0.0f;
			}
			float n1x = ty * fz - tz * fy, n1y = tz * fx - tx * fz, n1z = tx * fy - ty * fx;
			float n1l = Mth.sqrt(n1x * n1x + n1y * n1y + n1z * n1z);
			n1x /= n1l;
			n1y /= n1l;
			n1z /= n1l;
			float n2x = ty * n1z - tz * n1y, n2y = tz * n1x - tx * n1z, n2z = tx * n1y - ty * n1x;

			float s = (float) m / TUBE_SEGS;
			// blunt, rounded pseudopods (a cap closes the tip below), not sharp horns
			float r = thick * (1.0f - 0.62f * s) + 0.004f;
			lastR = r;
			lastTX = tx;
			lastTY = ty;
			lastTZ = tz;
			float[] c = tube[m];
			float v = 0.1f + 0.8f * s;
			for (int q = 0; q < TUBE_SIDES; q++) {
				float ang = TWO_PI * q / TUBE_SIDES;
				float ca = Mth.cos(ang), sa = Mth.sin(ang);
				float ex = n1x * ca + n2x * sa, ey = n1y * ca + n2y * sa, ez = n1z * ca + n2z * sa;
				float px = c[0] + ex * r, py = c[1] + ey * r, pz = c[2] + ez * r;
				tmpRingX[q] = px;
				tmpRingY[q] = py;
				tmpRingZ[q] = pz;
				tmpRingNX[q] = ex;
				tmpRingNY[q] = ey;
				tmpRingNZ[q] = ez;
			}
			if (m > 0) {
				float vPrev = 0.1f + 0.8f * (m - 1) / TUBE_SEGS;
				for (int q = 0; q < TUBE_SIDES; q++) {
					int q1 = (q + 1) % TUBE_SIDES;
					float u0 = 0.3f + 0.25f * q / TUBE_SIDES;
					float u1 = 0.3f + 0.25f * (q + 1) / TUBE_SIDES;
					vert(vc, p, prevX[q], prevY[q], prevZ[q], BODY_R, BODY_G, BODY_B, 255, u0, vPrev, light,
							prevNX[q], prevNY[q], prevNZ[q]);
					vert(vc, p, tmpRingX[q], tmpRingY[q], tmpRingZ[q], BODY_R, BODY_G, BODY_B, 255, u0, v, light,
							tmpRingNX[q], tmpRingNY[q], tmpRingNZ[q]);
					vert(vc, p, tmpRingX[q1], tmpRingY[q1], tmpRingZ[q1], BODY_R, BODY_G, BODY_B, 255, u1, v, light,
							tmpRingNX[q1], tmpRingNY[q1], tmpRingNZ[q1]);
					vert(vc, p, prevX[q1], prevY[q1], prevZ[q1], BODY_R, BODY_G, BODY_B, 255, u1, vPrev, light,
							prevNX[q1], prevNY[q1], prevNZ[q1]);
				}
			}
			System.arraycopy(tmpRingX, 0, prevX, 0, TUBE_SIDES);
			System.arraycopy(tmpRingY, 0, prevY, 0, TUBE_SIDES);
			System.arraycopy(tmpRingZ, 0, prevZ, 0, TUBE_SIDES);
			System.arraycopy(tmpRingNX, 0, prevNX, 0, TUBE_SIDES);
			System.arraycopy(tmpRingNY, 0, prevNY, 0, TUBE_SIDES);
			System.arraycopy(tmpRingNZ, 0, prevNZ, 0, TUBE_SIDES);
		}
		// rounded cap: fan the last ring in to a point just beyond the end
		float[] end = tube[TUBE_SEGS];
		float tipX = end[0] + lastTX * lastR * 1.4f;
		float tipY = end[1] + lastTY * lastR * 1.4f;
		float tipZ = end[2] + lastTZ * lastR * 1.4f;
		for (int q = 0; q < TUBE_SIDES; q++) {
			int q1 = (q + 1) % TUBE_SIDES;
			vert(vc, p, prevX[q], prevY[q], prevZ[q], BODY_R, BODY_G, BODY_B, 255, 0.4f, 0.9f, light,
					prevNX[q], prevNY[q], prevNZ[q]);
			vert(vc, p, tipX, tipY, tipZ, BODY_R, BODY_G, BODY_B, 255, 0.42f, 0.92f, light, lastTX, lastTY, lastTZ);
			vert(vc, p, tipX, tipY, tipZ, BODY_R, BODY_G, BODY_B, 255, 0.42f, 0.92f, light, lastTX, lastTY, lastTZ);
			vert(vc, p, prevX[q1], prevY[q1], prevZ[q1], BODY_R, BODY_G, BODY_B, 255, 0.44f, 0.9f, light,
					prevNX[q1], prevNY[q1], prevNZ[q1]);
		}
	}

	private float lastR;
	private float lastTX;
	private float lastTY;
	private float lastTZ;

	private final float[] prevX = new float[TUBE_SIDES];
	private final float[] prevY = new float[TUBE_SIDES];
	private final float[] prevZ = new float[TUBE_SIDES];
	private final float[] prevNX = new float[TUBE_SIDES];
	private final float[] prevNY = new float[TUBE_SIDES];
	private final float[] prevNZ = new float[TUBE_SIDES];
	private final float[] tmpRingX = new float[TUBE_SIDES];
	private final float[] tmpRingY = new float[TUBE_SIDES];
	private final float[] tmpRingZ = new float[TUBE_SIDES];
	private final float[] tmpRingNX = new float[TUBE_SIDES];
	private final float[] tmpRingNY = new float[TUBE_SIDES];
	private final float[] tmpRingNZ = new float[TUBE_SIDES];

	// ---------------------------------------------------------------- wet sheen

	/** Soft highlight patches that slide slowly over the dome (two white glints, one broad violet sheen). */
	private void emitSheen(VertexConsumer vc, PoseStack.Pose p, int light) {
		sheenPatch(vc, p, light, 0.32f, 0.6f + 0.25f * Mth.sin(t * 0.021f), 0.10f, 0.35f, SHEEN_WHITE_V, 210);
		sheenPatch(vc, p, light, 0.55f, 2.4f + 0.3f * Mth.sin(t * 0.017f + 2.0f), 0.07f, 0.25f, SHEEN_WHITE_V, 160);
		sheenPatch(vc, p, light, 0.78f, 4.3f + 0.4f * Mth.sin(t * 0.013f + 4.0f), 0.12f, 0.6f, SHEEN_VIOLET_V, 110);
	}

	private void sheenPatch(VertexConsumer vc, PoseStack.Pose p, int light, float f, float th, float df, float dth,
			float sheenV, int alpha) {
		// centre + its normal (finite differences of the live surface)
		surface(f, th, tmp);
		float cx = tmp[0], cy = tmp[1], cz = tmp[2];
		surface(Math.min(1.0f, f + 0.02f), th, tmp);
		surface(Math.max(0.0f, f - 0.02f), th, tmp2);
		float rx = tmp[0] - tmp2[0], ry = tmp[1] - tmp2[1], rz = tmp[2] - tmp2[2];
		surface(f, th + 0.03f, tmp);
		surface(f, th - 0.03f, tmp2);
		float tx = tmp[0] - tmp2[0], ty = tmp[1] - tmp2[1], tz = tmp[2] - tmp2[2];
		float nxx = ry * tz - rz * ty, nyy = rz * tx - rx * tz, nzz = rx * ty - ry * tx;
		float nl = Mth.sqrt(nxx * nxx + nyy * nyy + nzz * nzz);
		if (nl < 1.0e-6f) {
			nxx = 0.0f;
			nyy = 1.0f;
			nzz = 0.0f;
			nl = 1.0f;
		}
		nxx /= nl;
		nyy /= nl;
		nzz /= nl;
		float lift = 0.007f;
		int n = 8;
		float lastX = 0, lastY = 0, lastZ = 0;
		for (int q = 0; q <= n; q++) {
			float a = TWO_PI * (q % n) / n;
			surface(Mth.clamp(f + df * Mth.cos(a), 0.0f, 1.0f), th + dth * Mth.sin(a), tmp);
			float px = tmp[0] + nxx * lift, py = tmp[1] + nyy * lift, pz = tmp[2] + nzz * lift;
			if (q > 0) {
				vert(vc, p, cx + nxx * lift, cy + nyy * lift, cz + nzz * lift, 255, 255, 255, alpha, SHEEN_U, sheenV,
						light, nxx, nyy, nzz);
				vert(vc, p, lastX, lastY, lastZ, 255, 255, 255, 0, SHEEN_U, sheenV, light, nxx, nyy, nzz);
				vert(vc, p, px, py, pz, 255, 255, 255, 0, SHEEN_U, sheenV, light, nxx, nyy, nzz);
				vert(vc, p, cx + nxx * lift, cy + nyy * lift, cz + nzz * lift, 255, 255, 255, alpha, SHEEN_U, sheenV,
						light, nxx, nyy, nzz);
			}
			lastX = px;
			lastY = py;
			lastZ = pz;
		}
	}

	// ---------------------------------------------------------------- util

	/** v0.15.15: vertex-colour multiplier for the reddening (render thread only). */
	private static float colorScale = 1.0f;
	private static ResourceLocation crimsonTex;

	/** v0.15.15: a crimson copy of the goo texture (brightness kept, hue turned to Carnage red), built once. */
	private static ResourceLocation crimsonTexture() {
		if (crimsonTex != null) {
			return crimsonTex;
		}
		crimsonTex = TEX;
		try (java.io.InputStream in = net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(TEX).orElseThrow().open()) {
			com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(in);
			for (int y = 0; y < img.getHeight(); y++) {
				for (int x = 0; x < img.getWidth(); x++) {
					int abgr = img.getPixelRGBA(x, y);
					int a = abgr >>> 24;
					int r = abgr & 0xFF;
					int g = (abgr >> 8) & 0xFF;
					int b = (abgr >> 16) & 0xFF;
					float lum = Math.max(r, Math.max(g, b)) / 255.0f;
					int nr = Math.min(255, (int) (90 + 165 * Math.pow(lum, 0.6)));
					int ng = (int) (6 + 40 * lum);
					int nb = (int) (10 + 40 * lum);
					img.setPixelRGBA(x, y, (a << 24) | (nb << 16) | (ng << 8) | nr);
				}
			}
			ResourceLocation id = ProjectHeroMod.id("dynamic/symbiote_goo_crimson");
			net.minecraft.client.Minecraft.getInstance().getTextureManager()
					.register(id, new net.minecraft.client.renderer.texture.DynamicTexture(img));
			crimsonTex = id;
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not build the crimson Symbiote texture: {}", e.toString());
		}
		return crimsonTex;
	}

	private static void vert(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, int r, int g, int b, int a,
			float u, float v, int light, float nx, float ny, float nz) {
		if (colorScale != 1.0f) {
			r = (int) (r * colorScale);
			g = (int) (g * colorScale);
			b = (int) (b * colorScale);
		}
		vc.addVertex(p, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light).setNormal(p, nx, ny, nz);
	}

	/** Cheap deterministic 0..1 hash so each Symbiote keeps its own pseudopod layout. */
	private static float hash(int n) {
		n = (n << 13) ^ n;
		int m = (n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff;
		return m / (float) 0x7fffffff;
	}

	@Override
	public ResourceLocation getTextureLocation(SymbioteEntity entity) {
		return TEX;
	}
}
