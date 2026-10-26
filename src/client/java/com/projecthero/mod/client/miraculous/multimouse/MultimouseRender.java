package com.projecthero.mod.client.miraculous.multimouse;

import java.io.InputStream;
import java.util.Optional;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.render.OverlayStrips;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.multimouse.Multimouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1: Multimouse drawn over a player (or a mini copy) -- the user-approved suit skins
 * ({@code scratchpad/multimouse/gen_multimouse_suit.js}: the silver hooded bodysuit, the lilac-pink jacket front, the
 * mask; wide and slim, picked by the wearer's skin model) drawn OVER the wearer's own skin so their face shows under the
 * mask. Unlike Viperion's, her suit's hat layer is the hood, so it is drawn and the wearer's own hat layer (hair) hides.
 *
 * <p>The 3D extras (spec in {@code scratchpad/multimouse/README.md}) are lit boxes on the vanilla parts: the two round
 * mouse ears on the hood (grey, pink inside), the jump-rope belt with its handle at the front right hip, and the long
 * rope tail from the knot at the back, ending in the other pink handle -- swaying as she walks, flaring back as she runs
 * so it clears her legs.
 *
 * <p><b>The transformation</b> ({@link #cover}): the suit spreads texel by texel from the Mouse Pendant at the collar,
 * its edge a band of pink light, over {@code Multimouse.transformTicks}; the long transformation
 * ({@code MultimouseSequenceClient}) runs it at its own pace, raises the hood on its own beat and times the ears, the
 * belt and the tail itself.
 */
public final class MultimouseRender {
	public static final ResourceLocation SUIT_WIDE = ProjectHeroMod.id("textures/entity/miraculous/multimouse/suit_wide.png");
	public static final ResourceLocation SUIT_SLIM = ProjectHeroMod.id("textures/entity/miraculous/multimouse/suit_slim.png");
	public static final float BASE_INFLATE = 0.03f;
	private static final int FRAMES = 32;

	// the palette (from gen_multimouse_sheet.js)
	static final int GREY_D = 0x464958, GREY = 0x8A8E9E, GREY_L = 0xC4C8D4, GREY_H = 0xECEEF6;
	static final int PINK_D = 0x962866, PINK = 0xD64E98, PINK_L = 0xF48CC4, PINK_H = 0xFCC4E2;
	static final int INNER = 0xF096C4, INNER_L = 0xFCC4DE;

	private static PlayerModel<LivingEntity> slimBase;
	private static PlayerModel<LivingEntity> wideBase;
	private static PlayerModel<LivingEntity> slimOver;
	private static PlayerModel<LivingEntity> wideOver;
	private static ResourceLocation[] slimFrames;
	private static ResourceLocation[] wideFrames;

	private MultimouseRender() {
	}

	public static boolean slim(Player player) {
		return player instanceof net.minecraft.client.player.AbstractClientPlayer cp && cp.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
	}

	// ---------------------------------------------------------------- the models

	public static PlayerModel<LivingEntity> base(boolean slim) {
		if (slim) {
			if (slimBase == null) {
				slimBase = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(BASE_INFLATE), true), 64, 64).bakeRoot(), true);
			}
			return slimBase;
		}
		if (wideBase == null) {
			wideBase = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(BASE_INFLATE), false), 64, 64).bakeRoot(), false);
		}
		return wideBase;
	}

	public static PlayerModel<LivingEntity> over(boolean slim) {
		if (slim) {
			if (slimOver == null) {
				slimOver = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, true), 64, 64).bakeRoot(), true);
			}
			return slimOver;
		}
		if (wideOver == null) {
			wideOver = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot(), false);
		}
		return wideOver;
	}

	// ---------------------------------------------------------------- the reveal

	/** How much of the suit shows on {@code player} now, 0..1. */
	public static float cover(Player player, float partial) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null || !s.hasPower || !Kwami.MULLO.id.equals(s.kwami) || player.level() == null) {
			return 0f;
		}
		// v0.17.1: the long transformation runs the change at its own pace (MultimouseSequence)
		float seq = com.projecthero.mod.client.miraculous.MiraculousSequenceClient.suitCover(player, s, partial);
		if (seq >= 0f) {
			return seq;
		}
		float total = Multimouse.transformTicks(s);
		float age = player.level().getGameTime() - s.formChangeAt + partial;
		if (age < 0f && age > -40f) {
			age = 0f;
		}
		if (s.transformed) {
			if (s.formChangeAt <= 0L || age >= total || age < 0f) {
				return 1f;
			}
			return Mth.clamp(age / total, 0f, 1f);
		}
		if (s.formChangeAt <= 0L || age >= total || age < 0f) {
			return 0f;
		}
		return 1f - Mth.clamp(age / total, 0f, 1f);
	}

	private static float[] orders;

	/** When base texel ({@code u}, {@code v}) turns into the suit, 0..1 (2 = unused). */
	public static float order(int u, int v) {
		if (orders == null) {
			float[] o = new float[64 * 64];
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					o[y * 64 + x] = computeOrder(x, y);
				}
			}
			orders = o;
		}
		return u < 0 || v < 0 || u >= 64 || v >= 64 ? 2f : orders[v * 64 + u];
	}

	private static float computeOrder(int u, int v) {
		if (v < 16 && u >= 32) {
			u -= 32;
		} else if (v >= 32 && v < 48) {
			v -= 16;
		} else if (v >= 48 && u < 16) {
			u += 16;
		} else if (v >= 48 && u >= 48) {
			u -= 16;
		}
		float h = OverlayStrips.height(u, v);
		if (h < 0f) {
			return 2f;
		}
		// from the pendant at the collar (model height ~8.5 from the top of the head) out up over the hood and down
		float t = Math.abs(h - 8.5f) / 24f;
		return Mth.clamp(0.03f + 0.82f * t + 0.12f * hash(u, v), 0f, 0.995f);
	}

	private static float hash(int x, int y) {
		int h = x * 374761393 + y * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFF) / 65535f;
	}

	public static ResourceLocation texture(boolean slim, float cover) {
		if (cover >= 0.999f) {
			return slim ? SUIT_SLIM : SUIT_WIDE;
		}
		ResourceLocation[] f = slim ? slimFrames : wideFrames;
		if (f == null) {
			f = build(slim ? SUIT_SLIM : SUIT_WIDE, slim ? "slim" : "wide");
			if (slim) {
				slimFrames = f;
			} else {
				wideFrames = f;
			}
		}
		if (f.length == 0) {
			return slim ? SUIT_SLIM : SUIT_WIDE;
		}
		return f[Math.max(0, Math.min(FRAMES, Math.round(cover * FRAMES)))];
	}

	private static ResourceLocation[] build(ResourceLocation tex, String name) {
		Minecraft mc = Minecraft.getInstance();
		Optional<Resource> res = mc.getResourceManager().getResource(tex);
		if (res.isEmpty()) {
			return new ResourceLocation[0];
		}
		NativeImage src;
		try (InputStream in = res.get().open()) {
			src = NativeImage.read(in);
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[multimouse] could not read the suit for the reveal", e);
			return new ResourceLocation[0];
		}
		int size = src.getWidth();
		int scale = Math.max(1, size / 64);
		ResourceLocation[] out = new ResourceLocation[FRAMES + 1];
		for (int k = 0; k <= FRAMES; k++) {
			float line = k / (float) FRAMES;
			NativeImage img = new NativeImage(size, size, true);
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					int px = src.getPixelRGBA(x, y);
					float o = order(x / scale, y / scale);
					if (o > line || k == 0) {
						img.setPixelRGBA(x, y, 0);
					} else if (k < FRAMES && line - o < 0.07f && ((px >>> 24) & 0xFF) > 0) {
						img.setPixelRGBA(x, y, light(px, (line - o) / 0.07f));
					} else {
						img.setPixelRGBA(x, y, px);
					}
				}
			}
			out[k] = mc.getTextureManager().register("projecthero_multimouse_" + name + "_" + k, new DynamicTexture(img));
		}
		src.close();
		return out;
	}

	/** The leading edge: pale pink light fading into the texel (NativeImage is ABGR). */
	private static int light(int abgr, float t) {
		int a = (abgr >>> 24) & 0xFF, b = (abgr >>> 16) & 0xFF, g = (abgr >>> 8) & 0xFF, r = abgr & 0xFF;
		float k = 1f - t;
		r = (int) Mth.lerp(k, r, 255);
		g = (int) Mth.lerp(k, g, 196);
		b = (int) Mth.lerp(k, b, 230);
		return (a << 24) | (b << 16) | (g << 8) | r;
	}

	// ---------------------------------------------------------------- drawing the suit

	private static PlayerModel<LivingEntity> posed(PlayerModel<LivingEntity> m, HumanoidModel<?> parent, boolean baseParts) {
		return posed(m, parent, baseParts, 1f);
	}

	/** {@code hood} below 1: the hood (and the mask under it) not up yet -- folded back down her neck, rising over her head. */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static PlayerModel<LivingEntity> posed(PlayerModel<LivingEntity> m, HumanoidModel<?> parent, boolean baseParts, float hood) {
		((HumanoidModel) parent).copyPropertiesTo(m);
		m.jacket.copyFrom(m.body);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftPants.copyFrom(m.leftLeg);
		m.rightPants.copyFrom(m.rightLeg);
		m.hat.copyFrom(m.head);
		m.head.visible = baseParts;
		m.body.visible = baseParts;
		m.rightArm.visible = baseParts;
		m.leftArm.visible = baseParts;
		m.rightLeg.visible = baseParts;
		m.leftLeg.visible = baseParts;
		m.hat.visible = !baseParts; // the hood
		m.jacket.visible = !baseParts;
		m.rightSleeve.visible = !baseParts;
		m.leftSleeve.visible = !baseParts;
		m.rightPants.visible = !baseParts;
		m.leftPants.visible = !baseParts;
		if (hood < 1f) {
			m.head.visible = baseParts && hood > 0.85f;
			m.hat.visible = !baseParts && hood > 0f;
			m.hat.xRot = m.head.xRot - 2.1f * (1f - hood);
		}
		return m;
	}

	public static void renderSuit(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, boolean slim, float cover, int overlay) {
		renderSuit(pose, buffers, light, parent, slim, cover, overlay, 1f);
	}

	/** {@code hood} 0..1: how far up the hood is (the long transformation's beat; 1 = on). */
	public static void renderSuit(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, boolean slim, float cover, int overlay,
			float hood) {
		if (cover <= 0f) {
			return;
		}
		ResourceLocation tex = texture(slim, cover);
		posed(base(slim), parent, true, hood).renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, overlay, 0xFFFFFFFF);
		posed(over(slim), parent, false, hood).renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, overlay, 0xFFFFFFFF);
	}

	/** First person: the suit sleeve over the bare arm vanilla just drew. */
	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, ModelPart arm, boolean right, boolean slim, float cover) {
		ResourceLocation tex = texture(slim, cover);
		ModelPart a = right ? base(slim).rightArm : base(slim).leftArm;
		ModelPart sleeve = right ? over(slim).rightSleeve : over(slim).leftSleeve;
		a.copyFrom(arm);
		sleeve.copyFrom(arm);
		a.visible = true;
		sleeve.visible = true;
		a.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
	}

	// ---------------------------------------------------------------- the 3D extras

	/** The rounded-octagon disc: four overlapping boxes ([width, height] fractions of the diameter). */
	private static final float[][] DISC = { { 1f, 0.44f }, { 0.93f, 0.72f }, { 0.72f, 0.93f }, { 0.44f, 1f } };

	/**
	 * The ears, the rope belt and the tail on {@code m} (already posed). {@code swing} 0..1 is how fast she is moving
	 * (limbSwingAmount): the tail flares back with it so it clears her legs.
	 */
	public static void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> m, float cover, float ageInTicks, float swing,
			float limbSwing) {
		if (cover < 0.35f) {
			return;
		}
		float grow = Mth.clamp((cover - 0.35f) / 0.5f, 0f, 1f);
		renderExtras(pose, buffers, light, m, ageInTicks, swing, limbSwing, grow, grow, true, grow, 0f);
	}

	/**
	 * The extras with each part timed on its own (the long transformation): {@code ears} the ears' size (a pop can
	 * overshoot 1), {@code belt} whether the rope is wound round her waist, {@code tail} the tail's length, {@code kick} an
	 * extra swing of the tail (radians) as it flicks out.
	 */
	public static void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> m, float ageInTicks, float swing,
			float limbSwing, float grow, float ears, boolean belt, float tail, float kick) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(com.projecthero.mod.client.maxsteel.TurboDraw.WHITE));
		if (ears > 0f) {
			// ---- the ears (on the hood)
			pose.pushPose();
			m.head.translateAndRotate(pose);
			pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
			for (int s = -1; s <= 1; s += 2) {
				pose.pushPose();
				pose.translate(s * 3.0f, -8.2f, 0.4f);
				// a little twitch now and then
				float twitch = (Mth.sin(ageInTicks * 0.11f + s) > 0.97f) ? 6f : 0f;
				pose.mulPose(Axis.ZP.rotationDegrees(s * (20f + twitch)));
				pose.mulPose(Axis.XP.rotationDegrees(-8f));
				pose.scale(ears, ears, ears);
				float cy = -3.2f; // the disc's centre, above the pivot (35.4 - 32.2)
				float r = 3.4f, th = 0.45f;
				for (float[] d : DISC) {
					box(vc, pose, 0f, cy, 0f, r * d[0], r * d[1], th, GREY_L, light);
				}
				// the rolled rim, a shade lighter, just behind the face
				for (float[] d : DISC) {
					box(vc, pose, 0f, cy, 0.05f, r * d[0] + 0.12f, r * d[1] + 0.12f, th * 0.6f, GREY_H, light);
				}
				// the darker back (seen from behind)
				for (float[] d : DISC) {
					box(vc, pose, 0f, cy, 0.3f, r * d[0] - 0.4f, r * d[1] - 0.4f, 0.2f, GREY, light);
				}
				// pink inside: a darker edge, a lighter middle, on the front face
				float ri = 2.3f;
				for (float[] d : DISC) {
					box(vc, pose, 0f, cy + 0.15f, -th - 0.03f, ri * d[0], ri * d[1], 0.06f, PINK, light);
				}
				for (float[] d : DISC) {
					box(vc, pose, 0f, cy + 0.15f, -th - 0.1f, (ri - 0.45f) * d[0], (ri - 0.45f) * d[1], 0.05f, INNER, light);
				}
				box(vc, pose, -s * 0.7f, cy - 0.6f, -th - 0.16f, 0.35f, 0.35f, 0.02f, INNER_L, light);
				pose.popPose();
			}
			pose.popPose();
		}
		if (!belt) {
			return;
		}

		// ---- the rope belt, the hip handle, the tail
		pose.pushPose();
		m.body.translateAndRotate(pose);
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		// the belt: alternating strands round the waist (body row 8), a knot at the back
		int segs = 9;
		for (int i = 0; i < segs; i++) {
			float x = -4.45f + (i + 0.5f) * (8.9f / segs);
			int c = i % 3 == 0 ? PINK_D : i % 3 == 1 ? PINK : PINK_L;
			box(vc, pose, x, 8.5f, -2.45f, 8.9f / segs / 2f + 0.01f, 0.65f, 0.08f, c, light);
			box(vc, pose, x, 8.5f, 2.45f, 8.9f / segs / 2f + 0.01f, 0.65f, 0.08f, (i % 3 == 2 ? PINK_D : i % 3 == 0 ? PINK : PINK_L), light);
		}
		for (int i = 0; i < 4; i++) {
			float z = -2.45f + (i + 0.5f) * (4.9f / 4f);
			int c = i % 2 == 0 ? PINK : PINK_L;
			box(vc, pose, -4.45f, 8.5f, z, 0.08f, 0.65f, 4.9f / 8f + 0.01f, c, light);
			box(vc, pose, 4.45f, 8.5f, z, 0.08f, 0.65f, 4.9f / 8f + 0.01f, c, light);
		}
		box(vc, pose, 0f, 8.5f, 2.65f, 0.8f, 0.8f, 0.45f, PINK_L, light);
		box(vc, pose, 0f, 8.5f, 3.05f, 0.45f, 0.45f, 0.1f, PINK, light);
		// the handle at the front of the right hip
		segment(vc, pose, -2.9f, 9.0f, -2.6f, -3.1f, 10.1f, -2.75f, 0.21f, PINK, light);
		segment(vc, pose, -3.1f, 10.1f, -2.75f, -3.15f, 11.1f, -2.7f, 0.21f, PINK_L, light);
		handle(vc, pose, -3.15f, 11.3f, -2.7f, light);
		// the tail: its own bone at the knot, swaying, flaring back with her speed
		if (tail > 0f) {
			pose.pushPose();
			pose.translate(0f, 8.5f, 2.9f);
			float sway = Mth.sin(ageInTicks * 0.09f) * 0.07f + Mth.sin(limbSwing * 0.6662f) * 0.12f * swing;
			pose.mulPose(Axis.XP.rotation(0.12f + 0.75f * Mth.clamp(swing, 0f, 1f)));
			pose.mulPose(Axis.ZP.rotation(sway + kick));
			pose.mulPose(Axis.XP.rotation(-0.6f * kick));
			pose.scale(grow, tail, grow);
			float[][] pts = { { 0f, 15.5f, 2.9f }, { 0.1f, 13.6f, 3.9f }, { 0.4f, 10.8f, 4.7f }, { 0.9f, 7.6f, 5.1f }, { 1.4f, 4.9f, 4.9f },
					{ 1.7f, 3.3f, 4.4f } };
			for (int i = 0; i + 1 < pts.length; i++) {
				float[] a = pts[i], b = pts[i + 1];
				segment(vc, pose, a[0], 15.5f - a[1], a[2] - 2.9f, b[0], 15.5f - b[1], b[2] - 2.9f, 0.31f, i % 2 == 0 ? PINK : PINK_L, light);
			}
			float[] end = pts[pts.length - 1];
			float ex = end[0], ey = 15.5f - end[1], ez = end[2] - 2.9f;
			box(vc, pose, ex, ey + 0.35f, ez, 0.55f, 0.28f, 0.55f, PINK_H, light);
			box(vc, pose, ex, ey + 1.95f, ez, 0.43f, 1.35f, 0.43f, PINK, light);
			box(vc, pose, ex - 0.3f, ey + 1.95f, ez - 0.44f, 0.08f, 1.2f, 0.02f, PINK_H, light);
			box(vc, pose, ex, ey + 3.5f, ez, 0.58f, 0.3f, 0.58f, PINK_L, light);
			pose.popPose();
		}
		pose.popPose();
	}

	/** A jump-rope handle hanging from {@code (x, y, z)}: a bead, the ridged pink grip, a bead. */
	private static void handle(VertexConsumer vc, PoseStack pose, float x, float y, float z, int light) {
		box(vc, pose, x, y, z, 0.5f, 0.25f, 0.5f, PINK_H, light);
		box(vc, pose, x, y + 1.5f, z, 0.4f, 1.3f, 0.4f, PINK, light);
		box(vc, pose, x - 0.28f, y + 1.5f, z - 0.41f, 0.07f, 1.15f, 0.02f, PINK_H, light);
		box(vc, pose, x, y + 2.95f, z, 0.5f, 0.25f, 0.5f, PINK_L, light);
	}

	// ---------------------------------------------------------------- the Mouse Pendant (worn, detransformed)

	private static final int PENDANT = 0xC8CCD8, PENDANT_D = 0x7E8292, CHAIN = 0x5A5E6A;

	/** The Mouse Pendant at the collar on a fine chain: a round silver medallion with two little ear bumps and a pink gem. */
	public static void renderPendant(PoseStack pose, MultiBufferSource buffers, int light, ModelPart body) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(com.projecthero.mod.client.maxsteel.TurboDraw.WHITE));
		pose.pushPose();
		body.translateAndRotate(pose);
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		float front = -2.38f;
		for (int side = -1; side <= 1; side += 2) {
			pose.pushPose();
			pose.translate(side * 1.1f, 1.0f, front);
			pose.mulPose(Axis.ZP.rotationDegrees(side * -50f));
			box(vc, pose, 0f, 0f, 0f, 0.1f, 1.3f, 0.1f, CHAIN, light);
			pose.popPose();
		}
		float y = 2.6f;
		box(vc, pose, 0f, y, front - 0.08f, 0.75f, 0.55f, 0.12f, PENDANT, light);
		box(vc, pose, 0f, y, front - 0.08f, 0.55f, 0.75f, 0.12f, PENDANT, light);
		box(vc, pose, -0.6f, y - 0.75f, front - 0.08f, 0.28f, 0.28f, 0.1f, PENDANT_D, light);
		box(vc, pose, 0.6f, y - 0.75f, front - 0.08f, 0.28f, 0.28f, 0.1f, PENDANT_D, light);
		box(vc, pose, 0f, y + 0.05f, front - 0.22f, 0.25f, 0.25f, 0.04f, PINK, 0xF000F0);
		pose.popPose();
	}

	// ---------------------------------------------------------------- boxes

	/** A box from a to b (model pixels), {@code r} half thick: a piece of rope. */
	static void segment(VertexConsumer vc, PoseStack pose, float ax, float ay, float az, float bx, float by, float bz, float r, int rgb, int light) {
		Vector3f d = new Vector3f(bx - ax, by - ay, bz - az);
		float len = d.length();
		if (len < 1.0e-4f) {
			return;
		}
		pose.pushPose();
		pose.translate((ax + bx) / 2f, (ay + by) / 2f, (az + bz) / 2f);
		pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), d.normalize()));
		box(vc, pose, 0f, 0f, 0f, r, len / 2f + r * 0.6f, r, rgb, light);
		pose.popPose();
	}

	/** A lit solid box centred at (x, y, z) with half extents, in the current pose. */
	static void box(VertexConsumer vc, PoseStack pose, float x, float y, float z, float hx, float hy, float hz, int rgb, int light) {
		PoseStack.Pose p = pose.last();
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		float x0 = x - hx, x1 = x + hx, y0 = y - hy, y1 = y + hy, z0 = z - hz, z1 = z + hz;
		quad(vc, p, r, g, b, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(vc, p, r, g, b, light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
		quad(vc, p, r, g, b, light, 0, 0, -1, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0);
		quad(vc, p, r, g, b, light, 0, 0, 1, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1);
		quad(vc, p, r, g, b, light, -1, 0, 0, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1);
		quad(vc, p, r, g, b, light, 1, 0, 0, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0);
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose p, int r, int g, int b, int light, float nx, float ny, float nz,
			float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
		vc.addVertex(p, ax, ay, az).setColor(r, g, b, 255).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, bx, by, bz).setColor(r, g, b, 255).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, cx, cy, cz).setColor(r, g, b, 255).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, dx, dy, dz).setColor(r, g, b, 255).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}
}
