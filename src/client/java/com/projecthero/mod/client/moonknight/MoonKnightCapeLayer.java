package com.projecthero.mod.client.moonknight;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.data.MoonKnightAction;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Moon Knight's cape (v0.13.19): its own render layer, drawn for anyone who is transformed, so every player sees it.
 *
 * <p>It moves with exactly the physics vanilla gives a cape -- the same lagging "cloak" position, body yaw, walk bob
 * and crouch terms {@code CapeLayer} reads -- but it is longer (20 px) and wider (about 1.5x), hangs off the back of
 * the armour, has a hood-like top edge rising behind the head, and is made of a centre panel plus two folding side
 * panels. Those folds are what let it change shape:
 * <ul>
 *   <li><b>Cape Glide</b> (X tap): the panels spread out wide like wings and ripple in the wind;</li>
 *   <li><b>Cape Shroud</b> (X hold): the side panels fold forward and wrap around the front of the player.</li>
 * </ul>
 * Both blend in and out smoothly (per-player eased factors). Original off-white texture with a faint crescent pattern
 * ({@code textures/entity/moon_knight_cape.png}, made by {@code scratchpad/gen_moonknight.js}).
 */
public class MoonKnightCapeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/moon_knight_cape.png");
	private static final double LENGTH = 1.25;
	private static final double CENTRE_HALF = 0.19;
	private static final double SIDE_SEG = 0.15;
	private static final double THICK = 0.03;
	private static final int ROWS = 10;

	/** Per player: eased {glide, shroud} factors and the last frame time. */
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
		MoonKnightAction action = MoonKnightAnim.action(player);
		double[] ease = EASE.computeIfAbsent(player.getUUID(), k -> new double[]{0, 0, System.nanoTime()});
		double dt = Math.min(0.2, (System.nanoTime() - ease[2]) / 1.0e9);
		ease[2] = System.nanoTime();
		double k = 1.0 - Math.exp(-dt * 8.0);
		ease[0] += ((action.has(MoonKnightAction.FLAG_GLIDING) ? 1 : 0) - ease[0]) * k;
		ease[1] += ((action.has(MoonKnightAction.FLAG_SHROUD) ? 1 : 0) - ease[1]) * k;
		double glide = ease[0];
		double shroud = ease[1];

		pose.pushPose();
		pose.translate(0.0F, 0.0F, 0.125F + 0.07F); // behind the back, clear of the suit's inflated body
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
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
		// a shroud hangs straight and close; a glide streams back only a little (the body is already flat)
		swing = (float) Mth.lerp(shroud, swing, 2.0F);
		swing = (float) Mth.lerp(glide, swing, 14.0F);
		pose.mulPose(Axis.XP.rotationDegrees(swing));
		pose.mulPose(Axis.ZP.rotationDegrees(side / 2.0F * (float) (1.0 - shroud)));
		pose.mulPose(Axis.YP.rotationDegrees(-side / 2.0F * (float) (1.0 - shroud)));

		drawCape(vc, pose.last(), light, glide, shroud, ageInTicks + pt, player.getDeltaMovement().horizontalDistance());
		pose.popPose();
	}

	/**
	 * One row's cross-section, left edge to right edge, in the cape's own frame (x across, z behind; 0 = the back).
	 * The side panels are two-segment chains hinged at the centre panel's edges: flat and slightly curled normally,
	 * folded forward round the body for the shroud, stretched out wide for the glide.
	 */
	private static Vec3[] section(double y, double glide, double shroud, double flutter) {
		double down = y / LENGTH;
		double width = 1.0 + glide * (1.4 - 0.5 * down) + 0.12 * down; // the hem flares a touch
		double a1 = Mth.lerp(shroud, 0.25, 1.55) - glide * 0.35;
		double a2 = Mth.lerp(shroud, 0.5, 3.0) - glide * 0.25;
		double seg1 = SIDE_SEG * width * (1.0 + 1.4 * shroud);
		double seg2 = SIDE_SEG * width * (0.6 + 1.4 * shroud + 0.3 * glide);
		double c = CENTRE_HALF * (1.0 + glide * 0.6 + shroud * 0.6);
		Vec3[] pts = new Vec3[6];
		for (int sideSign = -1; sideSign <= 1; sideSign += 2) {
			double hx = sideSign * c;
			double x1 = hx + sideSign * seg1 * Math.cos(a1);
			double z1 = -seg1 * Math.sin(a1);
			double x2 = x1 + sideSign * seg2 * Math.cos(a2);
			double z2 = z1 - seg2 * Math.sin(a2);
			int o = sideSign < 0 ? 0 : 3;
			if (sideSign < 0) {
				pts[0] = new Vec3(x2, y, z2 + flutter * 0.6);
				pts[1] = new Vec3(x1, y, z1 + flutter * 0.8);
				pts[2] = new Vec3(hx, y, flutter);
			} else {
				pts[o] = new Vec3(hx, y, flutter);
				pts[o + 1] = new Vec3(x1, y, z1 + flutter * 0.8);
				pts[o + 2] = new Vec3(x2, y, z2 + flutter * 0.6);
			}
		}
		return pts;
	}

	private static void drawCape(VertexConsumer vc, PoseStack.Pose pose, int light, double glide, double shroud, float time,
			double speed) {
		Vec3[][] rows = new Vec3[ROWS + 1][];
		for (int r = 0; r <= ROWS; r++) {
			double y = LENGTH * r / ROWS;
			double amp = (0.012 + Math.min(0.05, speed * 0.15) + glide * 0.05) * (r / (double) ROWS);
			double flutter = Math.sin(time * (0.25 + glide * 0.5) + r * 0.9) * amp;
			rows[r] = section(y, glide, shroud, flutter);
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
