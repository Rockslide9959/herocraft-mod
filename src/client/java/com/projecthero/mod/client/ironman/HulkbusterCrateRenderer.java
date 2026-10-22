package com.projecthero.mod.client.ironman;

import static com.projecthero.mod.ironman.crate.HulkbusterCrateEntity.P_DROP;
import static com.projecthero.mod.ironman.crate.HulkbusterCrateEntity.P_FOLD_DROP;
import static com.projecthero.mod.ironman.crate.HulkbusterCrateEntity.P_LIFT;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.crate.HulkbusterCrateEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.16.7 (user) Era II stage 2: draws the Hulkbuster drop crate ({@link HulkbusterCrateEntity}) entirely off its synced
 * clock -- the drop height, the walls (hinged at the floor), the lid (hinged at the back wall's top), the landing squash,
 * the floor plate, the scorched ring with its embers, and the re-entry streak / thruster plume. Geometry in model pixels
 * from {@code scratchpad/ironman_era2_fold.js.txt} ({@code crate}, {@code CRATE_OPEN}): walls 36 px square, 55 px tall, a
 * 52 px floor plate. Every face is one flat texel of {@code textures/entity/hulkbuster_crate.png}.
 */
public class HulkbusterCrateRenderer extends EntityRenderer<HulkbusterCrateEntity> {
	public static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/hulkbuster_crate.png");
	private static final int RED = 0, RED_DARK = 1, GOLD = 2, BLUE = 3, FLOOR = 4, FLOOR_LINE = 5, INNER = 1, GOLD_DARK = 8;
	private static final float PX = 1f / 16f;

	public HulkbusterCrateRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0f;
	}

	@Override
	public ResourceLocation getTextureLocation(HulkbusterCrateEntity entity) {
		return TEXTURE;
	}

	@Override
	public void render(HulkbusterCrateEntity e, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		int phase = e.phase();
		float age = e.phaseAge(partial);
		float h = HulkbusterCrateEntity.height(phase, age, e.dropHeight());
		float[] wall = new float[3];
		for (int w = 0; w < 3; w++) {
			wall[w] = HulkbusterCrateEntity.wallOpen(w, phase, age);
		}
		float lid = HulkbusterCrateEntity.lidOpen(phase, age);
		float floor = HulkbusterCrateEntity.floor(phase, age);
		float squash = HulkbusterCrateEntity.squash(phase, age);
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		renderScorch(e, phase, age, partial, pose, buffers, light);
		pose.translate(0, h, 0);
		// in flight: a little wobble; lighting up for the lift: a shudder
		if (phase == P_DROP || phase == P_FOLD_DROP || (phase == P_LIFT && age > HulkbusterCrateEntity.IGNITE_TICKS)) {
			float k = phase == P_FOLD_DROP ? 0.6f : 1f;
			pose.translate(0, HulkbusterCrateEntity.WALL_H * 0.5f, 0);
			pose.mulPose(Axis.XP.rotationDegrees(1.6f * k * Mth.sin(age * 0.7f)));
			pose.mulPose(Axis.ZP.rotationDegrees(1.2f * k * Mth.cos(age * 0.55f)));
			pose.translate(0, -HulkbusterCrateEntity.WALL_H * 0.5f, 0);
		} else if (phase == P_LIFT && age > 3) {
			int t = (int) age;
			pose.translate(0.018f * (hash(t, 1) - 0.5f), 0.012f * hash(t, 2), 0.018f * (hash(t, 3) - 0.5f));
		}
		float bulge = 1f + (1f - squash) * 0.5f;
		pose.scale(bulge, squash, bulge);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
		int glow = LightTexture.FULL_BRIGHT;
		float pulse = 0.78f + 0.22f * Mth.sin((e.tickCount + partial) * 0.18f);

		// ---- the floor plate (a fold crate unfolds it on landing)
		if (floor > 0.001f) {
			pose.pushPose();
			pose.scale(floor, 1f, floor);
			box(pose, vc, -26, -0.05f, -26, 26, 0.25f, 26, FLOOR, light, 1f);
			for (float[] s : new float[][] { { -20, -20, 20, -19 }, { -20, 19, 20, 20 }, { -20, -19, -19, 19 }, { 19, -19, 20, 19 } }) {
				box(pose, vc, s[0], 0.25f, s[1], s[2], 0.4f, s[3], GOLD, light, 1f);
			}
			box(pose, vc, -24, 0.25f, -24, 24, 0.32f, -23.5f, FLOOR_LINE, light, 1f);
			for (int i = 0; i < 3; i++) { // the blue pad marks behind its heels
				box(pose, vc, -4.5f + i * 3.5f, 0.25f, -11, -3f + i * 3.5f, 0.42f, -7, BLUE, glow, pulse);
			}
			pose.popPose();
		}
		// ---- the back wall (stands) and its trim
		box(pose, vc, -18, 0, 16.5f, 18, 55, 18, RED_DARK, light, 1f);
		box(pose, vc, -18, 0, 15.5f, 18, 1.6f, 16.5f, GOLD_DARK, light, 1f);
		box(pose, vc, -18.4f, 0, 16.5f, -16.4f, 55, 18.4f, GOLD, light, 1f);
		box(pose, vc, 16.4f, 0, 16.5f, 18.4f, 55, 18.4f, GOLD, light, 1f);
		box(pose, vc, -0.6f, 6, 18.1f, 0.6f, 49, 18.3f, BLUE, glow, pulse);
		// ---- the front wall: hinged at the floor's front edge, falls out flat (-z)
		pose.pushPose();
		hinge(pose, 0, 0, -18, Axis.XP.rotationDegrees(-90f * wall[0]));
		box(pose, vc, -18, 0, -18, 18, 55, -16.5f, RED, light, 1f);
		box(pose, vc, -16.5f, 1.6f, -16.5f, 16.5f, 53.4f, -16.3f, INNER, light, 1f);
		for (float x : new float[] { -18, 16.4f }) {
			box(pose, vc, x, 0, -18.4f, x + 1.6f, 55, -16.5f, GOLD, light, 1f);
		}
		for (float y : new float[] { 0, 53.4f }) {
			box(pose, vc, -18, y, -18.4f, 18, y + 1.6f, -16.5f, GOLD, light, 1f);
		}
		box(pose, vc, -0.6f, 6, -18.5f, 0.6f, 49, -18.3f, BLUE, glow, pulse);
		box(pose, vc, -6, 34, -18.5f, 6, 38, -18.3f, GOLD, light, 1f);
		pose.popPose();
		// ---- the side walls: hinged at the floor's side edges, fall out flat (-x / +x)
		pose.pushPose();
		hinge(pose, -18, 0, 0, Axis.ZP.rotationDegrees(90f * wall[1]));
		box(pose, vc, -18, 0, -16.5f, -16.5f, 55, 16.5f, RED, light, 1f);
		box(pose, vc, -16.5f, 1.6f, -15, -16.3f, 53.4f, 15, INNER, light, 1f);
		box(pose, vc, -18.4f, 0, -16.5f, -18f, 1.6f, 16.5f, GOLD, light, 1f);
		box(pose, vc, -18.4f, 53.4f, -16.5f, -18f, 55, 16.5f, GOLD, light, 1f);
		box(pose, vc, -18.3f, 20, -10, -18.1f, 22, 10, BLUE, glow, pulse);
		pose.popPose();
		pose.pushPose();
		hinge(pose, 18, 0, 0, Axis.ZP.rotationDegrees(-90f * wall[2]));
		box(pose, vc, 16.5f, 0, -16.5f, 18, 55, 16.5f, RED, light, 1f);
		box(pose, vc, 16.3f, 1.6f, -15, 16.5f, 53.4f, 15, INNER, light, 1f);
		box(pose, vc, 18f, 0, -16.5f, 18.4f, 1.6f, 16.5f, GOLD, light, 1f);
		box(pose, vc, 18f, 53.4f, -16.5f, 18.4f, 55, 16.5f, GOLD, light, 1f);
		box(pose, vc, 18.1f, 20, -10, 18.3f, 22, 10, BLUE, glow, pulse);
		pose.popPose();
		// ---- the lid: hinged on top of the back wall, flips back 70 degrees
		pose.pushPose();
		hinge(pose, 0, 55, 18, Axis.XP.rotationDegrees(70f * lid));
		box(pose, vc, -18.4f, 55, -18.4f, 18.4f, 57, 18.4f, GOLD, light, 1f);
		box(pose, vc, -12, 57, -12, 12, 57.4f, 12, GOLD_DARK, light, 1f);
		box(pose, vc, -1, 57.4f, -8, 1, 57.6f, 8, BLUE, glow, pulse);
		pose.popPose();

		renderStreak(phase, age, h, e.dropHeight(), pose, buffers);
		pose.popPose();
		super.render(e, entityYaw, partial, pose, buffers, light);
	}

	/** Rotate about the hinge line through {@code (x, y, z)} px. */
	private static void hinge(PoseStack pose, float x, float y, float z, org.joml.Quaternionf rot) {
		pose.translate(x * PX, y * PX, z * PX);
		pose.mulPose(rot);
		pose.translate(-x * PX, -y * PX, -z * PX);
	}

	/** The scorched ring on the ground (and its cooling embers), drawn at the crate's spot whatever its height. */
	private static void renderScorch(HulkbusterCrateEntity e, int phase, float age, float partial, PoseStack pose, MultiBufferSource buffers,
			int light) {
		float a = switch (phase) {
			case P_DROP, P_FOLD_DROP -> 0f;
			case P_LIFT -> 1f - Mth.clamp((age - HulkbusterCrateEntity.IGNITE_TICKS) / 30f, 0f, 1f) * 0.85f;
			default -> 1f;
		};
		if (a <= 0.01f) {
			return;
		}
		float r = 2.7f;
		quad(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), r, 0.006f, 0f, 32f, a, light);
		float ember = Mth.clamp(1f - e.sinceLanded(partial) / 60f, 0f, 1f);
		if (phase == P_LIFT) {
			ember = Math.max(ember, 0.6f * Mth.clamp((age - 6f) / 6f, 0f, 1f) * (1f - Mth.clamp((age - 30f) / 12f, 0f, 1f)));
		}
		if (ember > 0.01f) {
			quad(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE)), r, 0.01f, 32f, 32f, ember, LightTexture.FULL_BRIGHT);
		}
	}

	/** A flat ground quad {@code 2r} square showing the 32x32 texture region at (u, v) px. */
	private static void quad(PoseStack pose, VertexConsumer vc, float r, float y, float u, float v, float alpha, int light) {
		PoseStack.Pose p = pose.last();
		float u0 = u / 64f, u1 = (u + 32f) / 64f, v0 = v / 64f, v1 = (v + 32f) / 64f;
		int al = Math.round(Mth.clamp(alpha, 0f, 1f) * 255f);
		vc.addVertex(p, -r, y, -r).setColor(255, 255, 255, al).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
		vc.addVertex(p, -r, y, r).setColor(255, 255, 255, al).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
		vc.addVertex(p, r, y, r).setColor(255, 255, 255, al).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
		vc.addVertex(p, r, y, -r).setColor(255, 255, 255, al).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
	}

	/** The glowing re-entry streak above a falling crate, the thruster plume under a lifting / braking one. */
	private static void renderStreak(int phase, float age, float h, float dropHeight, PoseStack pose, MultiBufferSource buffers) {
		VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
		float top = HulkbusterCrateEntity.WALL_H + 0.25f;
		if (phase == P_DROP) {
			float u = Mth.clamp(age / HulkbusterCrateEntity.DROP_TICKS, 0f, 1f);
			float len = 4f + 14f * u; // longer as it speeds up
			float fade = Mth.clamp((h - 0.5f) / 7f, 0f, 1f); // gone by the time it hits (no glow left standing on the crate)
			if (fade > 0f) {
				column(pose, vc, 1.0f, 0.3f, top, top + len, 255, 120, 40, 0.32f * fade, 0f);
				column(pose, vc, 0.55f, 0.12f, top, top + len * 0.55f, 255, 200, 120, 0.3f * fade, 0f);
			}
		} else if (phase == P_LIFT && age > HulkbusterCrateEntity.IGNITE_TICKS - 4) {
			float on = Mth.clamp((age - (HulkbusterCrateEntity.IGNITE_TICKS - 4)) / 4f, 0f, 1f);
			float u = Mth.clamp((age - HulkbusterCrateEntity.IGNITE_TICKS) / (float) (HulkbusterCrateEntity.LIFT_TICKS - HulkbusterCrateEntity.IGNITE_TICKS), 0f, 1f);
			float len = (1.2f + 9f * u) * on;
			for (int c = 0; c < 4; c++) {
				float cx = (c & 1) == 0 ? -0.8f : 0.8f, cz = (c & 2) == 0 ? -0.8f : 0.8f;
				pose.pushPose();
				pose.translate(cx, 0, cz);
				column(pose, vc, 0.28f, 0.05f, -0.02f, -0.02f - len, 160, 220, 255, 0.7f * on, 0f);
				column(pose, vc, 0.14f, 0.03f, -0.02f, -0.02f - len * 0.6f, 255, 255, 255, 0.6f * on, 0f);
				pose.popPose();
			}
			if (u > 0f) {
				column(pose, vc, 1.0f, 0.3f, -0.1f, -0.1f - Math.min(h, 3f + 12f * u), 255, 170, 90, 0.3f, 0f);
			}
		} else if (phase == P_FOLD_DROP) {
			float brake = Mth.clamp(age / 8f, 0f, 1f) * (1f - Mth.clamp((age - (HulkbusterCrateEntity.FOLD_DROP_TICKS - 3)) / 3f, 0f, 1f));
			for (int c = 0; c < 4; c++) {
				float cx = (c & 1) == 0 ? -HulkbusterCrateEntity.HALF : HulkbusterCrateEntity.HALF;
				float cz = (c & 2) == 0 ? -HulkbusterCrateEntity.HALF : HulkbusterCrateEntity.HALF;
				pose.pushPose();
				pose.translate(cx, 0, cz);
				column(pose, vc, 0.16f, 0.03f, 0f, -1.3f * brake, 160, 220, 255, 0.75f * brake, 0f);
				pose.popPose();
			}
		}
	}

	/** An additive tapered square column from y0 (half-width w0, alpha a0) to y1 (half-width w1, alpha a1). */
	private static void column(PoseStack pose, VertexConsumer vc, float w0, float w1, float y0, float y1, int r, int g, int b, float a0,
			float a1) {
		PoseStack.Pose p = pose.last();
		int al0 = Math.round(Mth.clamp(a0, 0f, 1f) * 255f), al1 = Math.round(Mth.clamp(a1, 0f, 1f) * 255f);
		float[][] sides = { { -1, -1, 1, -1 }, { 1, -1, 1, 1 }, { 1, 1, -1, 1 }, { -1, 1, -1, -1 } };
		for (float[] s : sides) {
			// both windings: the lightning type may cull
			for (int pass = 0; pass < 2; pass++) {
				float[][] v = { { s[0] * w0, y0, s[1] * w0, al0 }, { s[2] * w0, y0, s[3] * w0, al0 }, { s[2] * w1, y1, s[3] * w1, al1 },
						{ s[0] * w1, y1, s[1] * w1, al1 } };
				for (int k = 0; k < 4; k++) {
					float[] q = v[pass == 0 ? k : 3 - k];
					vc.addVertex(p, q[0], q[1], q[2]).setColor(r, g, b, (int) q[3]);
				}
			}
		}
	}

	/** A box in model px, every face the flat texel of {@code swatch}. */
	private static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int swatch,
			int light, float bright) {
		float ax = x0 * PX, ay = y0 * PX, az = z0 * PX, bx = x1 * PX, by = y1 * PX, bz = z1 * PX;
		float u = (swatch * 4 + 2) / 64f, v = 2f / 64f;
		int c = Math.round(Mth.clamp(bright, 0f, 1f) * 255f);
		PoseStack.Pose p = pose.last();
		float[][] f = {
				{ 0, 0, 1, ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz },
				{ 0, 0, -1, bx, ay, az, ax, ay, az, ax, by, az, bx, by, az },
				{ 1, 0, 0, bx, ay, bz, bx, ay, az, bx, by, az, bx, by, bz },
				{ -1, 0, 0, ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az },
				{ 0, 1, 0, ax, by, bz, bx, by, bz, bx, by, az, ax, by, az },
				{ 0, -1, 0, ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz } };
		for (float[] q : f) {
			for (int k = 0; k < 4; k++) {
				vc.addVertex(p, q[3 + k * 3], q[4 + k * 3], q[5 + k * 3]).setColor(c, c, c, 255).setUv(u, v)
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, q[0], q[1], q[2]);
			}
		}
	}

	private static float hash(int i, int salt) {
		int h = i * 374761393 + salt * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0x1000000;
	}
}
