package com.projecthero.mod.client.miraculous.viperion;

import java.io.InputStream;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.render.OverlayStrips;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.MiraculousConfig;
import com.projecthero.mod.miraculous.data.MiraculousState;

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
 * v0.16.7: Viperion drawn over a player -- the user's suit skin (minecraftskins.com 20294512, a wide skin; cut to the
 * suit only by {@code scratchpad/viperion/gen_viperion_suit.js}: the hair, face and skin go, a teal visor with open eye
 * holes is painted over the eyes) in a wide (as drawn) and a slim version picked by the wearer's skin model, drawn OVER
 * the wearer's own skin, so their face and hair show. The base layer sits {@link #BASE_INFLATE} over the body, the second
 * layer at vanilla's offsets; while the suit shows the wearer's jacket / sleeves / trousers are hidden (their hat layer
 * -- their hair -- stays).
 *
 * <p><b>The transformation</b> ({@link #cover}): over {@code MiraculousConfig.TRANSFORM_TICKS} the suit coils on texel by
 * texel from the bracelet on the right wrist out over the body (by distance from the wrist, a little grain), its edge a
 * band of teal light; detransforming runs it backwards.
 *
 * <p>Also the Lyre ({@link #renderLyre}) and the Snake Bracelet ({@link #renderBracelet}), drawn from lit boxes.
 */
public final class ViperionRender {
	public static final ResourceLocation SUIT_WIDE = ProjectHeroMod.id("textures/entity/miraculous/viperion/suit_wide.png");
	public static final ResourceLocation SUIT_SLIM = ProjectHeroMod.id("textures/entity/miraculous/viperion/suit_slim.png");
	public static final float BASE_INFLATE = 0.03f;
	private static final int FRAMES = 32;

	private static PlayerModel<LivingEntity> slimBase;
	private static PlayerModel<LivingEntity> wideBase;
	private static PlayerModel<LivingEntity> slimOver;
	private static PlayerModel<LivingEntity> wideOver;
	private static ResourceLocation[] slimFrames;
	private static ResourceLocation[] wideFrames;

	private ViperionRender() {
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
		if (s == null || !s.hasPower || !Kwami.SASS.id.equals(s.kwami) || player.level() == null) {
			return 0f;
		}
		// v0.17.1: the long transformation runs the change at its own pace (the kit's *Sequence)
		float seq = com.projecthero.mod.client.miraculous.MiraculousSequenceClient.suitCover(player, s, partial);
		if (seq >= 0f) {
			return seq;
		}
		float total = MiraculousConfig.TRANSFORM_TICKS;
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
		// the overlay regions map onto their base parts
		boolean rightArm = (u >= 40 && u < 56 && v >= 16 && v < 48);
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
		// from the bracelet (the right wrist, model height ~14): the right arm first, then out over the body
		float t = rightArm ? Math.abs(h - 14f) / 40f : 0.2f + Math.abs(h - 18f) / 22f;
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
			ProjectHeroMod.LOGGER.warn("[viperion] could not read the suit for the reveal", e);
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
			out[k] = mc.getTextureManager().register("projecthero_viperion_" + name + "_" + k, new DynamicTexture(img));
		}
		src.close();
		return out;
	}

	/** The leading edge: pale teal light fading into the texel (NativeImage is ABGR). */
	private static int light(int abgr, float t) {
		int a = (abgr >>> 24) & 0xFF, b = (abgr >>> 16) & 0xFF, g = (abgr >>> 8) & 0xFF, r = abgr & 0xFF;
		float k = 1f - t;
		r = (int) Mth.lerp(k, r, 170);
		g = (int) Mth.lerp(k, g, 255);
		b = (int) Mth.lerp(k, b, 236);
		return (a << 24) | (b << 16) | (g << 8) | r;
	}

	// ---------------------------------------------------------------- drawing the suit

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static PlayerModel<LivingEntity> posed(PlayerModel<LivingEntity> m, HumanoidModel<?> parent, boolean baseParts) {
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
		m.hat.visible = false; // the suit's hat layer is empty: the wearer's own hair shows
		m.jacket.visible = !baseParts;
		m.rightSleeve.visible = !baseParts;
		m.leftSleeve.visible = !baseParts;
		m.rightPants.visible = !baseParts;
		m.leftPants.visible = !baseParts;
		return m;
	}

	public static void renderSuit(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, boolean slim, float cover) {
		if (cover <= 0f) {
			return;
		}
		ResourceLocation tex = texture(slim, cover);
		posed(base(slim), parent, true).renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		posed(over(slim), parent, false).renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
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

	// ---------------------------------------------------------------- the Lyre

	private static final int FRAME = 0x1D9086, FRAME_D = 0x0F3E3B, FRAME_L = 0x2FB7B0, GOLD = 0xF2D84A, GOLD_D = 0xB89A24, STR = 0xDDFFF6,
			BLACK = 0x14181A, EYE = 0xF6E25A;

	/**
	 * The Lyre at the pose's origin, in model pixels (scaled 1/16 here): the grip / club end at the origin running down
	 * -Y, the instrument up +Y -- a rounded sound bowl, two curving arms that end in snake heads, the gold crossbar and
	 * five strings; its face toward -Z. {@code strum} 0..1 makes the strings shimmer.
	 */
	public static void renderLyre(PoseStack pose, MultiBufferSource buffers, int light, float strum) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(com.projecthero.mod.client.maxsteel.TurboDraw.WHITE));
		pose.pushPose();
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		// the grip (the club end): a dark teal handle with gold bands and a heavy pommel
		box(vc, pose, 0f, -2.6f, 0f, 0.55f, 2.4f, 0.55f, FRAME_D, light);
		box(vc, pose, 0f, -1.0f, 0f, 0.62f, 0.2f, 0.62f, GOLD_D, light);
		box(vc, pose, 0f, -4.4f, 0f, 0.62f, 0.2f, 0.62f, GOLD_D, light);
		box(vc, pose, 0f, -5.4f, 0f, 0.95f, 0.8f, 0.95f, FRAME, light);
		// the sound bowl
		box(vc, pose, 0f, 0.9f, 0f, 2.6f, 1.1f, 0.8f, FRAME, light);
		box(vc, pose, 0f, 0.3f, 0f, 1.9f, 0.6f, 0.75f, FRAME_D, light);
		box(vc, pose, 0f, 1.95f, -0.05f, 2.4f, 0.12f, 0.82f, GOLD, light);
		box(vc, pose, 0f, 0.95f, -0.85f, 0.7f, 0.5f, 0.06f, FRAME_L, light); // the scale emblem
		// the two arms, curving out and up, each in three pieces
		for (int s = -1; s <= 1; s += 2) {
			float[][] seg = { { 2.7f, 2.9f, 12f }, { 3.3f, 5.0f, 2f }, { 2.9f, 7.1f, -16f } };
			for (float[] q : seg) {
				pose.pushPose();
				pose.translate(s * q[0], q[1], 0f);
				pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-s * q[2]));
				box(vc, pose, 0f, 0f, 0f, 0.5f, 1.15f, 0.55f, FRAME, light);
				box(vc, pose, -s * 0.42f, 0f, -0.3f, 0.1f, 1.1f, 0.3f, FRAME_L, light);
				pose.popPose();
			}
			// the snake head at the top of the arm, turned outward, gold eyes, black stripe
			box(vc, pose, s * 2.4f, 8.6f, 0f, 0.9f, 0.6f, 0.7f, FRAME, light);
			box(vc, pose, s * 3.15f, 8.75f, 0f, 0.45f, 0.4f, 0.55f, FRAME_L, light);
			box(vc, pose, s * 2.6f, 8.9f, -0.72f, 0.12f, 0.12f, 0.04f, EYE, light);
			box(vc, pose, s * 2.6f, 8.9f, 0.72f, 0.12f, 0.12f, 0.04f, EYE, light);
			box(vc, pose, s * 2.0f, 8.6f, 0f, 0.12f, 0.62f, 0.72f, BLACK, light);
		}
		// the crossbar
		box(vc, pose, 0f, 8.0f, 0f, 2.1f, 0.3f, 0.45f, GOLD, light);
		box(vc, pose, 0f, 8.0f, -0.46f, 1.9f, 0.08f, 0.02f, GOLD_D, light);
		// the five strings
		for (int i = -2; i <= 2; i++) {
			float x = i * 0.75f;
			float wob = strum > 0f ? 0.08f * strum * Mth.sin((i + 3) * 1.7f + strum * 40f) : 0f;
			box(vc, pose, x + wob, 5.0f, -0.05f, 0.06f, 2.95f, 0.06f, STR, LIGHT_FULL);
		}
		pose.popPose();
	}

	private static final int LIGHT_FULL = 0xF000F0;

	// ---------------------------------------------------------------- the Snake Bracelet

	private static final int[] BRACELET_PLAIN = { 0x2EB078, 0x1E7A50, 0x16181C, 0x2A2E34, 0xF4D44A };
	private static final int[] BRACELET_ON = { 0x3CE0A0, 0x24A06A, 0x0E1012, 0x22262C, 0xFFE860 };

	/**
	 * The Snake Bracelet round the RIGHT wrist: a band of green and black scales hugging the arm (wide or slim) and, on
	 * the outer face, the snake's head biting its own tail -- gold eyes. Brighter while transformed, a little further off
	 * the arm to sit on the suit. {@code arm} is the right arm part (already posed).
	 */
	public static void renderBracelet(PoseStack pose, MultiBufferSource buffers, int light, ModelPart arm, boolean slim, boolean on) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(com.projecthero.mod.client.maxsteel.TurboDraw.WHITE));
		int[] c = on ? BRACELET_ON : BRACELET_PLAIN;
		pose.pushPose();
		arm.translateAndRotate(pose);
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		float gap = on ? 0.5f : 0.12f;
		// the right arm's box: x -3..1 (wide) or -2..1 (slim), z -2..2; the outer face is -x
		float cx = slim ? -0.5f : -1.0f;
		float hx = (slim ? 1.5f : 2.0f) + gap, hz = 2.0f + gap;
		float y = 7.8f;
		// the band: four faces of alternating green / black scale blocks
		int n = 4;
		for (int i = 0; i < n; i++) {
			float t = (i + 0.5f) / n * 2f - 1f;
			int col = i % 2 == 0 ? c[0] : c[2];
			int col2 = i % 2 == 0 ? c[2] : c[0];
			box(vc, pose, cx + t * hx, y, -hz, hx / n + 0.02f, 0.42f, 0.14f, col, light);
			box(vc, pose, cx + t * hx, y, hz, hx / n + 0.02f, 0.42f, 0.14f, col2, light);
			box(vc, pose, cx - hx, y, t * hz, 0.14f, 0.42f, hz / n + 0.02f, col2, light);
			box(vc, pose, cx + hx, y, t * hz, 0.14f, 0.42f, hz / n + 0.02f, col, light);
		}
		// a lighter scale ridge along the top edge
		box(vc, pose, cx, y - 0.4f, -hz - 0.02f, hx, 0.04f, 0.12f, c[1], light);
		box(vc, pose, cx, y - 0.4f, hz + 0.02f, hx, 0.04f, 0.12f, c[1], light);
		// the head on the outer face: green, a black stripe, gold eyes, the tail tip in its mouth
		float ox = cx - hx;
		box(vc, pose, ox - 0.3f, y, 0.4f, 0.22f, 0.6f, 0.8f, c[0], light);
		box(vc, pose, ox - 0.45f, y - 0.2f, 0.75f, 0.06f, 0.1f, 0.1f, c[4], LIGHT_FULL);
		box(vc, pose, ox - 0.45f, y - 0.2f, 0.05f, 0.06f, 0.1f, 0.1f, c[4], LIGHT_FULL);
		box(vc, pose, ox - 0.42f, y + 0.2f, 0.4f, 0.04f, 0.08f, 0.7f, c[2], light);
		box(vc, pose, ox - 0.22f, y, -0.75f, 0.16f, 0.3f, 0.4f, c[3], light);
		pose.popPose();
	}

	/** A lit solid box centred at (x, y, z) with half extents, in the current pose (model space: +y down, -z the front). */
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
