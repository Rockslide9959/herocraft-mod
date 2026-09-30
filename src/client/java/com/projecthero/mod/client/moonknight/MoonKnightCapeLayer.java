package com.projecthero.mod.client.moonknight;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAlter;
import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.data.MoonKnightAction;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector4f;

/**
 * Moon Knight's cape (v0.13.19): its own render layer, drawn for anyone who is transformed, so every player sees it.
 *
 * <p>It moves with exactly the physics vanilla gives a cape -- the same lagging "cloak" position, body yaw, walk bob
 * and crouch terms {@code CapeLayer} reads -- but it is longer (20 px) and wider (about 1.5x), hangs off the back of
 * the armour, has a hood-like top edge rising behind the head, and is made of a centre panel plus two folding side
 * panels. Those folds are what let it change shape:
 * <ul>
 *   <li><b>Cape Block</b> (hold right click; the old Cape Shroud): the side panels fold forward and wrap around the
 *       front of the player;</li>
 *   <li><b>Cape Glide</b> (v0.13.21): the cape becomes webbing -- a triangle on each side from the wrist to the
 *       shoulder down to the ankle, and the back panel between them, stretched between the spread arms and legs of
 *       the flat gliding body ({@link #drawWebbing}). Its corners are read off the model's own arm and leg parts
 *       every frame, so it always spans exactly where the limbs are.</li>
 * </ul>
 * Each alter wears his own cape colour (v0.13.21, {@link MoonKnightAlter#capeTexture}): Marc the original off-white,
 * Steven a crisp white, Jake charcoal. During an alter swap it changes over halfway through the suit's
 * rematerialisation. Textures from {@code scratchpad/gen_moonknight.js} / {@code gen_moonknight_alters.js}.
 */
public class MoonKnightCapeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final ResourceLocation[] TEXTURES = new ResourceLocation[MoonKnightAlter.values().length];
	static {
		for (MoonKnightAlter a : MoonKnightAlter.values()) {
			TEXTURES[a.ordinal()] = ProjectHeroMod.id(a.capeTexture());
		}
	}
	private static final double LENGTH = 1.25;
	private static final double CENTRE_HALF = 0.19;
	private static final double SIDE_SEG = 0.15;
	private static final double THICK = 0.03;
	private static final int ROWS = 10;
	/** Glide webbing: rows from the top edge (arms / shoulders) to the feet, and columns per section. */
	private static final int WEB_ROWS = 8;
	private static final int WEB_SECTION_COLS = 4;
	/** How far behind the limbs' centre lines the webbing sits (px), clear of the inflated suit. */
	private static final float WEB_BACK = 3.1f;

	/** Per player: eased {glide (unused since v0.13.21), shroud} factors and the last frame time. */
	private static final Map<UUID, double[]> EASE = new ConcurrentHashMap<>();

	public MoonKnightCapeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float pt, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !MoonKnight.isTransformed(player)) {
			return;
		}
		MoonKnightAlter shown = shownAlter(player, pt);
		if (!shown.hasCape()) {
			return; // v0.14.4: Steven's Mr. Knight suit has no cape (it goes / comes back halfway through a swap)
		}
		MoonKnightAction action = MoonKnightAnim.action(player);
		double[] ease = EASE.computeIfAbsent(player.getUUID(), k -> new double[]{0, 0, System.nanoTime()});
		double dt = Math.min(0.2, (System.nanoTime() - ease[2]) / 1.0e9);
		ease[2] = System.nanoTime();
		double k = 1.0 - Math.exp(-dt * 8.0);
		ease[1] += ((action.has(MoonKnightAction.FLAG_CAPE_BLOCK) ? 1 : 0) - ease[1]) * k;
		double shroud = ease[1];

		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURES[shown.ordinal()]));
		if (action.has(MoonKnightAction.FLAG_GLIDING)) {
			drawWebbing(vc, pose.last(), light, getParentModel(), ageInTicks + pt);
			pose.pushPose();
			pose.translate(0.0F, 0.0F, 0.125F + 0.07F);
			drawHood(vc, pose.last(), light);
			pose.popPose();
			return;
		}

		pose.pushPose();
		pose.translate(0.0F, 0.0F, 0.125F + 0.07F); // behind the back, clear of the suit's inflated body
		drawHood(vc, pose.last(), light);

		// ---- vanilla's cape swing (CapeLayer), unchanged
		double dx = Mth.lerp(pt, player.xCloakO, player.xCloak) - Mth.lerp(pt, player.xo, player.getX());
		double dy = Mth.lerp(pt, player.yCloakO, player.yCloak) - Mth.lerp(pt, player.yo, player.getY());
		double dz = Mth.lerp(pt, player.zCloakO, player.zCloak) - Mth.lerp(pt, player.zo, player.getZ());
		float body = Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot);
		double sin = Mth.sin(body * ((float) Math.PI / 180F));
		double cos = -Mth.cos(body * ((float) Math.PI / 180F));
		float lift = Mth.clamp((float) dy * 10.0F, -6.0F, 32.0F);
		float back = Mth.clamp((float) (dx * sin + dz * cos) * 100.0F, 0.0F, 150.0F);
		float side = Mth.clamp((float) (dx * cos - dz * sin) * 100.0F, -20.0F, 20.0F);
		float bob = Mth.lerp(pt, player.oBob, player.bob);
		lift += Mth.sin(Mth.lerp(pt, player.walkDistO, player.walkDist) * 6.0F) * 32.0F * bob;
		if (player.isCrouching()) {
			lift += 25.0F;
		}
		float swing = 6.0F + back / 2.0F + lift;
		// a raised block hangs straight and close
		swing = (float) Mth.lerp(shroud, swing, 2.0F);
		pose.mulPose(Axis.XP.rotationDegrees(swing));
		pose.mulPose(Axis.ZP.rotationDegrees(side / 2.0F * (float) (1.0 - shroud)));
		pose.mulPose(Axis.YP.rotationDegrees(-side / 2.0F * (float) (1.0 - shroud)));

		drawCape(vc, pose.last(), light, shroud, ageInTicks + pt, player.getDeltaMovement().horizontalDistance());
		pose.popPose();
	}

	/** Whose cape is shown: this alter's; during an alter swap the old one until halfway through. */
	private static MoonKnightAlter shownAlter(AbstractClientPlayer player, float pt) {
		MoonKnightAlter from = MoonKnightReveal.swapFrom(player);
		if (from != null && MoonKnightReveal.swapProgress(player, pt) < 0.5f) {
			return from;
		}
		return MoonKnight.alter(player);
	}

	// ---------------------------------------------------------------- the glide webbing (v0.13.21)

	/** A point given in a model part's own space (px), in the model-root space the layer draws in (blocks). */
	private static Vec3 at(ModelPart part, float px, float py, float pz) {
		PoseStack ps = new PoseStack();
		part.translateAndRotate(ps);
		Vector4f v = ps.last().pose().transform(new Vector4f(px / 16.0f, py / 16.0f, pz / 16.0f, 1.0f));
		return new Vec3(v.x, v.y, v.z);
	}

	/**
	 * The gliding cape: one sheet from the wrists and shoulders (the top edge) down to the ankles. Columns, right to
	 * left: right wrist, right shoulder, left shoulder, left wrist; the two wrist columns run down to their own ankle,
	 * so each wing is a triangle (wrist -- shoulder -- ankle) and the back panel between the shoulders tapers to the
	 * feet. The wings belly out a little in the wind (away from the back) and ripple.
	 */
	private static void drawWebbing(VertexConsumer vc, PoseStack.Pose pose, int light, PlayerModel<?> m, float time) {
		Vec3 wristR = at(m.rightArm, -1.0f, 9.5f, WEB_BACK);
		Vec3 wristL = at(m.leftArm, 1.0f, 9.5f, WEB_BACK);
		Vec3 shoulderR = at(m.body, -4.0f, 0.5f, WEB_BACK);
		Vec3 shoulderL = at(m.body, 4.0f, 0.5f, WEB_BACK);
		Vec3 ankleR = at(m.rightLeg, -0.5f, 11.0f, WEB_BACK);
		Vec3 ankleL = at(m.leftLeg, 0.5f, 11.0f, WEB_BACK);
		Vec3[] top = { wristR, shoulderR, shoulderL, wristL };
		Vec3[] bottom = { ankleR, ankleR, ankleL, ankleL };
		// the back of the body: which way is "out" (behind him) for the billow
		Vec3 outward = at(m.body, 0.0f, 6.0f, 10.0f).subtract(at(m.body, 0.0f, 6.0f, 0.0f)).normalize();
		int sections = top.length - 1;
		int cols = sections * WEB_SECTION_COLS;
		Vec3[][] grid = new Vec3[WEB_ROWS + 1][cols + 1];
		for (int c = 0; c <= cols; c++) {
			int sec = Math.min(sections - 1, c / WEB_SECTION_COLS);
			double s = (c - sec * WEB_SECTION_COLS) / (double) WEB_SECTION_COLS;
			Vec3 t0 = top[sec].lerp(top[sec + 1], s);
			Vec3 b0 = bottom[sec].lerp(bottom[sec + 1], s);
			boolean wing = sec != 1;
			for (int r = 0; r <= WEB_ROWS; r++) {
				double t = r / (double) WEB_ROWS;
				Vec3 p = t0.lerp(b0, t);
				if (wing) {
					double belly = Math.sin(Math.PI * s) * Math.sin(Math.PI * t);
					double ripple = Math.sin(time * 0.9 + r * 0.8 + c * 0.6) * 0.018;
					p = p.add(outward.scale(belly * (0.10 + ripple)));
				} else {
					p = p.add(outward.scale(Math.sin(time * 0.7 + r * 0.9) * 0.006 * t));
				}
				grid[r][c] = p;
			}
		}
		Vec3 off = outward.scale(THICK);
		for (int r = 0; r < WEB_ROWS; r++) {
			float v0 = 0.12f + 0.84f * r / WEB_ROWS;
			float v1 = 0.12f + 0.84f * (r + 1) / WEB_ROWS;
			for (int c = 0; c < cols; c++) {
				float u0 = (float) c / cols;
				float u1 = (float) (c + 1) / cols;
				Vec3 a = grid[r][c];
				Vec3 b = grid[r][c + 1];
				Vec3 cc = grid[r + 1][c + 1];
				Vec3 d = grid[r + 1][c];
				// inside lining (right half of the texture) against the body, the outside (left half) a hair further out
				quad(vc, pose, light, a, b, cc, d, 0.5f + u0 * 0.5f, v0, 0.5f + u1 * 0.5f, v1);
				quad(vc, pose, light, a.add(off), b.add(off), cc.add(off), d.add(off), u0 * 0.5f, v0, u1 * 0.5f, v1);
			}
		}
	}

	// ---------------------------------------------------------------- the hanging cape

	/**
	 * One row's cross-section, left edge to right edge, in the cape's own frame (x across, z behind; 0 = the back).
	 * The side panels are two-segment chains hinged at the centre panel's edges: flat and slightly curled normally,
	 * folded forward round the body for the block.
	 */
	private static Vec3[] section(double y, double shroud, double flutter) {
		double down = y / LENGTH;
		double width = 1.0 + 0.12 * down; // the hem flares a touch
		double a1 = Mth.lerp(shroud, 0.25, 1.55);
		double a2 = Mth.lerp(shroud, 0.5, 3.0);
		double seg1 = SIDE_SEG * width * (1.0 + 1.4 * shroud);
		double seg2 = SIDE_SEG * width * (0.6 + 1.4 * shroud);
		double c = CENTRE_HALF * (1.0 + shroud * 0.6);
		Vec3[] pts = new Vec3[6];
		for (int sideSign = -1; sideSign <= 1; sideSign += 2) {
			double hx = sideSign * c;
			double x1 = hx + sideSign * seg1 * Math.cos(a1);
			double z1 = -seg1 * Math.sin(a1);
			double x2 = x1 + sideSign * seg2 * Math.cos(a2);
			double z2 = z1 - seg2 * Math.sin(a2);
			if (sideSign < 0) {
				pts[0] = new Vec3(x2, y, z2 + flutter * 0.6);
				pts[1] = new Vec3(x1, y, z1 + flutter * 0.8);
				pts[2] = new Vec3(hx, y, flutter);
			} else {
				pts[3] = new Vec3(hx, y, flutter);
				pts[4] = new Vec3(x1, y, z1 + flutter * 0.8);
				pts[5] = new Vec3(x2, y, z2 + flutter * 0.6);
			}
		}
		return pts;
	}

	private static void drawCape(VertexConsumer vc, PoseStack.Pose pose, int light, double shroud, float time, double speed) {
		Vec3[][] rows = new Vec3[ROWS + 1][];
		for (int r = 0; r <= ROWS; r++) {
			double y = LENGTH * r / ROWS;
			double amp = (0.012 + Math.min(0.05, speed * 0.15)) * (r / (double) ROWS);
			double flutter = Math.sin(time * 0.25 + r * 0.9) * amp;
			rows[r] = section(y, shroud, flutter);
		}
		int cols = rows[0].length;
		for (int r = 0; r < ROWS; r++) {
			float v0 = (float) r / ROWS;
			float v1 = (float) (r + 1) / ROWS;
			for (int cIdx = 0; cIdx < cols - 1; cIdx++) {
				float u0 = (float) cIdx / (cols - 1);
				float u1 = (float) (cIdx + 1) / (cols - 1);
				Vec3 a = rows[r][cIdx];
				Vec3 b = rows[r][cIdx + 1];
				Vec3 cc = rows[r + 1][cIdx + 1];
				Vec3 d = rows[r + 1][cIdx];
				// inside lining (right half of the texture), then the outside a hair further back (left half)
				quad(vc, pose, light, a, b, cc, d, 0.5f + u0 * 0.5f, v0, 0.5f + u1 * 0.5f, v1);
				Vec3 off = new Vec3(0, 0, THICK);
				quad(vc, pose, light, a.add(off), b.add(off), cc.add(off), d.add(off), u0 * 0.5f, v0, u1 * 0.5f, v1);
			}
		}
	}

	/** The hood: a curved flap rising from the shoulders behind the head, drawn before the swing so it stays put. */
	private static void drawHood(VertexConsumer vc, PoseStack.Pose pose, int light) {
		int n = 4;
		Vec3[][] rim = new Vec3[n + 1][5];
		for (int r = 0; r <= n; r++) {
			double t = (double) r / n; // 0 at the shoulders, 1 at the top of the hood
			double y = -0.34 * t;
			double z = 0.02 + 0.11 * Math.sin(t * Math.PI * 0.5);
			double half = 0.33 - 0.08 * t * t;
			for (int c = 0; c <= 4; c++) {
				double u = c / 4.0;
				double ang = (u - 0.5) * 2.2;
				rim[r][c] = new Vec3(Math.sin(ang) * half, y, z - (1.0 - Math.cos(ang)) * half * 0.5);
			}
		}
		for (int r = 0; r < n; r++) {
			for (int c = 0; c < 4; c++) {
				float u0 = c / 4.0f * 0.5f;
				float u1 = (c + 1) / 4.0f * 0.5f;
				float v0 = 0.02f + r * 0.02f;
				float v1 = 0.02f + (r + 1) * 0.02f;
				quad(vc, pose, light, rim[r][c], rim[r][c + 1], rim[r + 1][c + 1], rim[r + 1][c], u0, v0, u1, v1);
			}
		}
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose pose, int light, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
			float u0, float v0, float u1, float v1) {
		Vec3 n = b.subtract(a).cross(d.subtract(a));
		double len = n.length();
		if (len < 1e-6) {
			n = c.subtract(b).cross(d.subtract(b)); // a collapsed corner (the wing's tip at the ankle)
			len = n.length();
		}
		float nx = len < 1e-6 ? 0 : (float) (n.x / len);
		float ny = len < 1e-6 ? 0 : (float) (n.y / len);
		float nz = len < 1e-6 ? 1 : (float) (n.z / len);
		v(vc, pose, light, a, u0, v0, nx, ny, nz);
		v(vc, pose, light, b, u1, v0, nx, ny, nz);
		v(vc, pose, light, c, u1, v1, nx, ny, nz);
		v(vc, pose, light, d, u0, v1, nx, ny, nz);
	}

	private static void v(VertexConsumer vc, PoseStack.Pose pose, int light, Vec3 p, float u, float vv, float nx, float ny,
			float nz) {
		vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(255, 255, 255, 255).setUv(u, vv)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}

	/** Forget a player's easing (world change). */
	public static void clear() {
		EASE.clear();
	}
}
