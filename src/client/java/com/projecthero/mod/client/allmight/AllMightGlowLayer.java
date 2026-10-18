package com.projecthero.mod.client.allmight;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * v0.16.6: One For All's Output, drawn on the body. In the Power Form his arms glow gold-white -- faintly at 20%, clearly at 45%,
 * blazing at 100% (and through Plus Ultra) -- as nested additive shells around each arm (and the costume over it), brighter toward
 * the fists; at 100% jagged gold arcs crackle over both arms, re-struck every two ticks (Plus Ultra also wraps the torso in light). The
 * steam venting off the arms is server particles ({@code AllMight.armSteam}). Nothing glows in the recoil -- his arms are spent.
 * Every viewer sees it: it reads the synced {@code AllMightState}.
 */
public class AllMightGlowLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	/** Base glow strength at 20% / 45% / 100%. */
	private static final float[] STRENGTH = { 0.16f, 0.4f, 0.72f };
	/** Shell inflation in model pixels beyond the vanilla arm (the full-form costume arm is inflated 1.8). */
	private static final float[] SHELLS = { 2.0f, 2.6f, 3.3f };
	private static final float[] SHELL_ALPHA = { 0.5f, 0.26f, 0.12f };

	public AllMightGlowLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible()) {
			return;
		}
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		if (s == null || !s.hasPower || !s.fullPower || s.recoilUntil > player.level().getGameTime()) {
			return;
		}
		float k = strength(s, ageInTicks);
		if (k <= 0.01f) {
			return;
		}
		boolean full = s.plusUltra || s.output >= AllMightState.OUTPUT_100;
		// gold at low Output, near-white gold at 100%
		float t = s.plusUltra ? 1f : s.output / 2f;
		float r = 1.0f;
		float g = Mth.lerp(t, 0.72f, 0.93f);
		float b = Mth.lerp(t, 0.22f, 0.68f);
		VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
		PlayerModel<AbstractClientPlayer> m = getParentModel();
		for (int side = 0; side < 2; side++) {
			ModelPart arm = side == 0 ? m.rightArm : m.leftArm;
			if (!arm.visible) {
				continue;
			}
			pose.pushPose();
			arm.translateAndRotate(pose);
			float x0 = side == 0 ? -3f : -1f;
			for (int i = 0; i < SHELLS.length; i++) {
				float a = k * SHELL_ALPHA[i];
				box(vc, pose.last().pose(), x0 - SHELLS[i], -2f - SHELLS[i], -2f - SHELLS[i], x0 + 4f + SHELLS[i], 10f + SHELLS[i], 2f + SHELLS[i],
						r, g, b, a * 0.45f, a);
			}
			if (full) {
				arcs(vc, pose.last().pose(), player.getId() * 31 + side * 7 + (int) (ageInTicks / 2f), x0);
			}
			pose.popPose();
		}
		if (s.plusUltra) { // Plus Ultra: the light spills over his whole torso
			pose.pushPose();
			m.body.translateAndRotate(pose);
			float a = k * 0.16f;
			box(vc, pose.last().pose(), -4f - 2.6f, -2.6f, -2f - 2.6f, 4f + 2.6f, 12f + 2.6f, 2f + 2.6f, r, g, b, a, a * 0.6f);
			pose.popPose();
		}
	}

	/** The glow strength of this state at this moment (a slow pulse; a flicker at 100%). */
	static float strength(AllMightState s, float ageInTicks) {
		int level = s.plusUltra ? AllMightState.OUTPUT_100 : Mth.clamp(s.output, 0, 2);
		float k = STRENGTH[level] * (0.9f + 0.1f * Mth.sin(ageInTicks * 0.25f));
		if (level == AllMightState.OUTPUT_100) {
			k += 0.1f * Mth.sin(ageInTicks * 1.7f) + (s.plusUltra ? 0.12f : 0f);
		}
		return Math.min(1f, k);
	}

	/** An axis-aligned box in model pixels, both windings, top faces at {@code aTop} alpha fading to {@code aBottom} (the fist end). */
	private static void box(VertexConsumer vc, Matrix4f mat, float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b,
			float aTop, float aBottom) {
		float s = 1f / 16f;
		x0 *= s;
		y0 *= s;
		z0 *= s;
		x1 *= s;
		y1 *= s;
		z1 *= s;
		// model Y grows downward: y0 is the shoulder, y1 the fist
		quad(vc, mat, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, aTop, aBottom); // north
		quad(vc, mat, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, aTop, aBottom); // south
		quad(vc, mat, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, aTop, aBottom); // west
		quad(vc, mat, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r, g, b, aTop, aBottom); // east
		quad(vc, mat, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, aTop, aTop); // shoulder end
		quad(vc, mat, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, b, aBottom, aBottom); // fist end
	}

	/** A quad whose first two corners are the top edge; emitted in both windings so whichever side faces the camera is drawn. */
	private static void quad(VertexConsumer vc, Matrix4f mat, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
			float dx, float dy, float dz, float r, float g, float b, float aTop, float aBottom) {
		vc.addVertex(mat, ax, ay, az).setColor(r, g, b, aTop);
		vc.addVertex(mat, bx, by, bz).setColor(r, g, b, aTop);
		vc.addVertex(mat, cx, cy, cz).setColor(r, g, b, aBottom);
		vc.addVertex(mat, dx, dy, dz).setColor(r, g, b, aBottom);
		vc.addVertex(mat, dx, dy, dz).setColor(r, g, b, aBottom);
		vc.addVertex(mat, cx, cy, cz).setColor(r, g, b, aBottom);
		vc.addVertex(mat, bx, by, bz).setColor(r, g, b, aTop);
		vc.addVertex(mat, ax, ay, az).setColor(r, g, b, aTop);
	}

	/** Two or three jagged gold arcs crawling over the arm, re-struck from {@code seed} (changes every two ticks). */
	private static void arcs(VertexConsumer vc, Matrix4f mat, int seed, float x0) {
		RandomSource rnd = RandomSource.create(seed * 341873128712L + 132897987541L);
		int n = 2 + rnd.nextInt(2);
		for (int a = 0; a < n; a++) {
			// start somewhere on the arm's surface, crawl down / around it in short kinks just off the costume
			float px = x0 + 2f + (rnd.nextFloat() - 0.5f) * 7f;
			float py = -1f + rnd.nextFloat() * 9f;
			float pz = (rnd.nextFloat() - 0.5f) * 7f;
			for (int seg = 0; seg < 4; seg++) {
				float nx = px + (rnd.nextFloat() - 0.5f) * 3.2f;
				float ny = py + 0.8f + rnd.nextFloat() * 2.2f;
				float nz = pz + (rnd.nextFloat() - 0.5f) * 3.2f;
				bolt(vc, mat, px, py, pz, nx, ny, nz);
				px = nx;
				py = ny;
				pz = nz;
			}
		}
	}

	/** One arc segment: two thin crossed quads (so it reads from any side), bright gold-white. */
	private static void bolt(VertexConsumer vc, Matrix4f mat, float ax, float ay, float az, float bx, float by, float bz) {
		float s = 1f / 16f;
		float w = 0.22f;
		float r = 1.0f;
		float g = 0.92f;
		float b = 0.6f;
		float al = 0.95f;
		quad(vc, mat, (ax - w) * s, ay * s, az * s, (ax + w) * s, ay * s, az * s, (bx + w) * s, by * s, bz * s, (bx - w) * s, by * s, bz * s, r, g, b, al, al);
		quad(vc, mat, ax * s, ay * s, (az - w) * s, ax * s, ay * s, (az + w) * s, bx * s, by * s, (bz + w) * s, bx * s, by * s, (bz - w) * s, r, g, b, al, al);
	}
}
