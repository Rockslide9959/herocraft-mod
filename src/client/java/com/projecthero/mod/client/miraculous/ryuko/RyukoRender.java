package com.projecthero.mod.client.miraculous.ryuko;

import java.io.InputStream;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.miraculous.MiraculousSequenceClient;
import com.projecthero.mod.client.miraculous.queenbee.QueenBeeRender;
import com.projecthero.mod.client.render.OverlayStrips;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.MiraculousConfig;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.ryuko.Ryuko;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
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
 * v0.17.3: Ryuko drawn over a player -- the user's suit skin (planetminecraft.com/skin/ryuko-miraculous, a 128 x 128 SLIM skin,
 * cut to the suit only by {@code scratchpad/ryuko/gen_ryuko_suit.js}) in a slim and a wide version picked by the wearer's skin
 * model, OVER the wearer's own skin, so their face and hair show. The models are Queen Bee's ({@link QueenBeeRender#base},
 * {@link QueenBeeRender#over}), with the hat layer on: the mask's side pieces are there.
 *
 * <p><b>The transformation</b> ({@link #cover}): the suit rises from her boots to her collar, texel by texel, the mask last
 * (the storm climbs her in the long scene, {@code RyukoSequence}); detransforming runs it backwards.
 *
 * <p>Also the sword ({@link #renderSword}) and the Dragon Choker ({@link #renderChoker}), drawn from lit boxes.
 */
public final class RyukoRender {
	public static final ResourceLocation SUIT_WIDE = ProjectHeroMod.id("textures/entity/miraculous/ryuko/suit_wide.png");
	public static final ResourceLocation SUIT_SLIM = ProjectHeroMod.id("textures/entity/miraculous/ryuko/suit_slim.png");
	private static final int FRAMES = 32;

	public static final int RED = 0xD22A28, RED_D = 0x8E1A20, GOLD = 0xF4BE3C, GOLD_L = 0xFFE690, STEEL = 0xDDE4EE, STEEL_D = 0x9AA6B8, BLACK = 0x17151A;

	/** The long scene's storm-climb edge: hot red-gold (the quick change uses her element's colour, {@link #edgeColor}). */
	public static final int HOT_EDGE = 0xFFBE5A;

	/** The reveal frames, per skin model and edge colour (slim bit 24 | RGB). */
	private static final java.util.Map<Integer, ResourceLocation[]> FRAMES_BY_EDGE = new java.util.HashMap<>();
	private static float[] orders;

	private RyukoRender() {
	}

	public static boolean slim(Player player) {
		return QueenBeeRender.slim(player);
	}

	/** How much of the suit shows on {@code player} now, 0..1. */
	public static float cover(Player player, float partial) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null || !s.hasPower || !Kwami.LONGG.id.equals(s.kwami) || player.level() == null) {
			return 0f;
		}
		float seq = MiraculousSequenceClient.suitCover(player, s, partial);
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

	/** When base texel ({@code u}, {@code v}, 64 x 64) turns into the suit, 0..1 (2 = unused). */
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
		// the mask last, wiped across her face (the long scene's hand sweep)
		if (v < 16) {
			return Mth.clamp(0.88f + 0.1f * (u - 7) / 9f, 0.88f, 0.98f);
		}
		// from her boots up to her collar (height counts down from the crown: 8 = shoulders, 32 = soles)
		float t = Mth.clamp((32f - h) / 24f, 0f, 1f);
		return Mth.clamp(0.03f + 0.75f * t + 0.08f * hash(u, v), 0f, 0.86f);
	}

	private static float hash(int x, int y) {
		int h = x * 374761393 + y * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFF) / 65535f;
	}

	/**
	 * v0.17.3 (tester: the suit-up "could use some work"): the colour of the suit's rising frontier -- her current element's
	 * ({@link Ryuko#color}) for the quick change and the way back; the long scene keeps its hot red-gold storm edge.
	 */
	public static int edgeColor(Player player, float partial) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null || MiraculousSequenceClient.suitCover(player, s, partial) >= 0f) {
			return HOT_EDGE;
		}
		return Ryuko.color(Ryuko.element(player));
	}

	private static ResourceLocation texture(boolean slim, float cover, int edgeRgb) {
		ResourceLocation full = slim ? SUIT_SLIM : SUIT_WIDE;
		if (cover >= 0.999f) {
			return full;
		}
		int key = (slim ? 1 << 24 : 0) | (edgeRgb & 0xFFFFFF);
		ResourceLocation[] f = FRAMES_BY_EDGE.get(key);
		if (f == null) {
			f = build(full, (slim ? "slim_" : "wide_") + Integer.toHexString(edgeRgb & 0xFFFFFF), edgeRgb);
			FRAMES_BY_EDGE.put(key, f);
		}
		return f.length == 0 ? full : f[Math.max(0, Math.min(FRAMES, Math.round(cover * FRAMES)))];
	}

	private static ResourceLocation[] build(ResourceLocation tex, String name, int edgeRgb) {
		Minecraft mc = Minecraft.getInstance();
		Optional<Resource> res = mc.getResourceManager().getResource(tex);
		if (res.isEmpty()) {
			return new ResourceLocation[0];
		}
		NativeImage src;
		try (InputStream in = res.get().open()) {
			src = NativeImage.read(in);
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[ryuko] could not read the suit for the reveal", e);
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
						img.setPixelRGBA(x, y, edge(px, (line - o) / 0.07f, edgeRgb));
					} else {
						img.setPixelRGBA(x, y, px);
					}
				}
			}
			out[k] = mc.getTextureManager().register("projecthero_ryuko_" + name + "_" + k, new DynamicTexture(img));
		}
		src.close();
		return out;
	}

	/** The suit's moving edge: a band of {@code rgb}, brightest at the very front ({@code t} = 0), fading into the suit. */
	private static int edge(int abgr, float t, int rgb) {
		int a = (abgr >>> 24) & 0xFF, b = (abgr >>> 16) & 0xFF, g = (abgr >>> 8) & 0xFF, r = abgr & 0xFF;
		float k = 1f - t;
		// the leading texels run a little hotter than the colour itself, so even the pale wind reads as a glow
		float hot = Mth.clamp(1f - t * 3f, 0f, 1f) * 0.35f;
		int er = (int) Mth.lerp(hot, (rgb >> 16) & 0xFF, 255);
		int eg = (int) Mth.lerp(hot, (rgb >> 8) & 0xFF, 255);
		int eb = (int) Mth.lerp(hot, rgb & 0xFF, 255);
		r = (int) Mth.lerp(k, r, er);
		g = (int) Mth.lerp(k, g, eg);
		b = (int) Mth.lerp(k, b, eb);
		return (a << 24) | (b << 16) | (g << 8) | r;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static PlayerModel<LivingEntity> posed(PlayerModel<LivingEntity> m, HumanoidModel<?> parent, boolean base) {
		((HumanoidModel) parent).copyPropertiesTo(m);
		m.jacket.copyFrom(m.body);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftPants.copyFrom(m.leftLeg);
		m.rightPants.copyFrom(m.rightLeg);
		m.hat.copyFrom(m.head);
		m.head.visible = base;
		m.body.visible = base;
		m.rightArm.visible = base;
		m.leftArm.visible = base;
		m.rightLeg.visible = base;
		m.leftLeg.visible = base;
		m.hat.visible = !base;
		m.jacket.visible = !base;
		m.rightSleeve.visible = !base;
		m.leftSleeve.visible = !base;
		m.rightPants.visible = !base;
		m.leftPants.visible = !base;
		return m;
	}

	public static void renderSuit(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, boolean slim, float cover, int edgeRgb) {
		if (cover <= 0f) {
			return;
		}
		ResourceLocation tex = texture(slim, cover, edgeRgb);
		posed(QueenBeeRender.base(slim), parent, true).renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		posed(QueenBeeRender.over(slim), parent, false).renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
	}

	/** First person: the suit sleeve over the bare arm vanilla just drew. */
	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, ModelPart arm, boolean right, boolean slim, float cover, int edgeRgb) {
		ResourceLocation tex = texture(slim, cover, edgeRgb);
		ModelPart a = right ? QueenBeeRender.base(slim).rightArm : QueenBeeRender.base(slim).leftArm;
		ModelPart sleeve = right ? QueenBeeRender.over(slim).rightSleeve : QueenBeeRender.over(slim).leftSleeve;
		a.copyFrom(arm);
		sleeve.copyFrom(arm);
		a.visible = true;
		sleeve.visible = true;
		a.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
	}

	private static VertexConsumer boxes(MultiBufferSource buffers) {
		return buffers.getBuffer(RenderType.entityCutoutNoCull(com.projecthero.mod.client.maxsteel.TurboDraw.WHITE));
	}

	/**
	 * The sword centred on the pose's origin -- the middle of the grip, where the fist closes -- the blade running along -z,
	 * in model pixels (scaled 1/16 here): a red wrapped grip with gold bands and a pommel cap, a round gold guard, a slim
	 * single-edged steel blade (its edge, +y, carries her element's colour) to a clipped katana point.
	 *
	 * <p>v0.17.3 (tester: the sword "is bugged as well"): the grip used to start AT the origin and run 4.4 px backwards, so
	 * the hilt hung out of the bottom of her hand / through her forearm with the fist round the guard; it is centred now,
	 * and the whole sword is a little shorter (16.5 px, a katana to her 32 px height).
	 */
	public static void renderSword(PoseStack pose, MultiBufferSource buffers, int light, int elementRgb) {
		VertexConsumer vc = boxes(buffers);
		pose.pushPose();
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		// the grip: z -2.3 .. +2.3, through the fist; the pommel cap behind it
		QueenBeeRender.box(vc, pose, 0f, 0f, 0f, 0.4f, 0.5f, 2.3f, RED_D, light);
		QueenBeeRender.box(vc, pose, 0f, 0f, -1.3f, 0.44f, 0.54f, 0.12f, GOLD, light);
		QueenBeeRender.box(vc, pose, 0f, 0f, 1.3f, 0.44f, 0.54f, 0.12f, GOLD, light);
		QueenBeeRender.box(vc, pose, 0f, 0f, 2.55f, 0.48f, 0.58f, 0.25f, GOLD, light);
		// the guard (tsuba) and the collar in front of it
		QueenBeeRender.box(vc, pose, 0f, 0f, -2.5f, 0.95f, 1.05f, 0.2f, GOLD, light);
		QueenBeeRender.box(vc, pose, 0f, 0f, -2.85f, 0.3f, 0.6f, 0.15f, GOLD_L, light);
		// the blade: z -3.0 .. -13.4, then the point
		QueenBeeRender.box(vc, pose, 0f, -0.08f, -8.2f, 0.16f, 0.5f, 5.2f, STEEL, light);
		QueenBeeRender.box(vc, pose, 0f, 0.5f, -8.2f, 0.1f, 0.12f, 5.2f, elementRgb, LightTexture.FULL_BRIGHT);
		QueenBeeRender.box(vc, pose, 0f, 0.05f, -13.85f, 0.14f, 0.45f, 0.45f, STEEL_D, light);
		QueenBeeRender.box(vc, pose, 0f, 0.2f, -14.45f, 0.1f, 0.25f, 0.2f, STEEL_D, light);
		pose.popPose();
	}

	/**
	 * The Dragon Choker round her collar, in the posed body's space (it follows the body: sneaking, turning). {@code on}: it
	 * glows as the Kwami goes in.
	 *
	 * <p>v0.17.3 (tester: the choker "is bugged slightly"): it used to be two loose 8.1 px planks floating 0.4 px off the front
	 * and back of the chest -- wider than the torso, nothing on the sides, the clasp hanging below them -- so from above it
	 * read as a red strip lying across the chest rather than a band round the neck. Now a slim band wraps all four sides of
	 * the collar flush over the skin's second layer (and the suit), with a small gold clasp at the throat.
	 */
	public static void renderChoker(PoseStack pose, MultiBufferSource buffers, int light, ModelPart body, boolean on) {
		VertexConsumer vc = boxes(buffers);
		pose.pushPose();
		body.translateAndRotate(pose);
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		// transformed, a deeper red, so the band still reads against the red suit
		int band = on ? RED_D : RED;
		int bandBack = on ? 0x6E1218 : RED_D;
		// the second layer (jacket / suit) sits 0.25 px proud of the 8 x 12 x 4 torso; the band 0.06 px over that, 0.12 thick
		float g = 0.31f, th = 0.06f;
		float y = 0.55f, hy = 0.45f; // y 0.1 .. 1.0, just under the chin
		float fx = 4f + g, fz = 2f + g;
		QueenBeeRender.box(vc, pose, 0f, y, -fz - th, fx + 2f * th, hy, th, band, light); // front
		QueenBeeRender.box(vc, pose, 0f, y, fz + th, fx + 2f * th, hy, th, bandBack, light); // back
		QueenBeeRender.box(vc, pose, -fx - th, y, 0f, th, hy, fz, bandBack, light); // sides
		QueenBeeRender.box(vc, pose, fx + th, y, 0f, th, hy, fz, bandBack, light);
		// the gold clasp at the throat, a dragon-red gem in it
		float cz = -fz - 2f * th;
		QueenBeeRender.box(vc, pose, 0f, y, cz - 0.08f, 0.75f, 0.62f, 0.08f, on ? GOLD_L : GOLD, on ? LightTexture.FULL_BRIGHT : light);
		QueenBeeRender.box(vc, pose, 0f, y, cz - 0.2f, 0.32f, 0.3f, 0.05f, on ? 0xFF6A50 : RED_D, on ? LightTexture.FULL_BRIGHT : light);
		pose.popPose();
	}
}
