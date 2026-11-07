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

	private static ResourceLocation[] slimFrames;
	private static ResourceLocation[] wideFrames;
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

	private static ResourceLocation texture(boolean slim, float cover) {
		ResourceLocation full = slim ? SUIT_SLIM : SUIT_WIDE;
		if (cover >= 0.999f) {
			return full;
		}
		ResourceLocation[] f = slim ? slimFrames : wideFrames;
		if (f == null) {
			f = build(full, slim ? "slim" : "wide");
			if (slim) {
				slimFrames = f;
			} else {
				wideFrames = f;
			}
		}
		return f.length == 0 ? full : f[Math.max(0, Math.min(FRAMES, Math.round(cover * FRAMES)))];
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
						img.setPixelRGBA(x, y, edge(px, (line - o) / 0.07f));
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

	/** The suit's moving edge: a band of hot red-gold. */
	private static int edge(int abgr, float t) {
		int a = (abgr >>> 24) & 0xFF, b = (abgr >>> 16) & 0xFF, g = (abgr >>> 8) & 0xFF, r = abgr & 0xFF;
		float k = 1f - t;
		r = (int) Mth.lerp(k, r, 255);
		g = (int) Mth.lerp(k, g, 190);
		b = (int) Mth.lerp(k, b, 90);
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

	public static void renderSuit(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, boolean slim, float cover) {
		if (cover <= 0f) {
			return;
		}
		ResourceLocation tex = texture(slim, cover);
		posed(QueenBeeRender.base(slim), parent, true).renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		posed(QueenBeeRender.over(slim), parent, false).renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
	}

	/** First person: the suit sleeve over the bare arm vanilla just drew. */
	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, ModelPart arm, boolean right, boolean slim, float cover) {
		ResourceLocation tex = texture(slim, cover);
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
	 * The sword at the pose's origin, its grip there, the blade running along -z, in model pixels (scaled 1/16 here): a red
	 * wrapped grip, a gold guard, a slim steel blade with an edge of its element's colour.
	 */
	public static void renderSword(PoseStack pose, MultiBufferSource buffers, int light, int elementRgb) {
		VertexConsumer vc = boxes(buffers);
		pose.pushPose();
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		QueenBeeRender.box(vc, pose, 0f, 0f, 1.2f, 0.42f, 0.55f, 2.2f, RED_D, light);
		QueenBeeRender.box(vc, pose, 0f, 0f, 3.6f, 0.5f, 0.62f, 0.4f, GOLD, light);
		QueenBeeRender.box(vc, pose, 0f, 0f, -1.2f, 0.5f, 1.5f, 0.28f, GOLD, light);
		QueenBeeRender.box(vc, pose, 0f, 0f, -8.6f, 0.2f, 0.62f, 7.2f, STEEL, light);
		QueenBeeRender.box(vc, pose, 0f, 0.5f, -8.6f, 0.12f, 0.16f, 7.2f, elementRgb, LightTexture.FULL_BRIGHT);
		QueenBeeRender.box(vc, pose, 0f, 0f, -16.4f, 0.17f, 0.42f, 0.7f, STEEL_D, light);
		pose.popPose();
	}

	/** The Dragon Choker round the neck, in the posed body's space. {@code on}: it glows as the Kwami goes in. */
	public static void renderChoker(PoseStack pose, MultiBufferSource buffers, int light, ModelPart body, boolean on) {
		VertexConsumer vc = boxes(buffers);
		pose.pushPose();
		body.translateAndRotate(pose);
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		int band = on ? 0xFF5A48 : RED;
		// a block body has no neck: the band runs round the collar, on the shoulders' top edge, just proud of the skin (and of a
		// worn layer)
		float y = 0.62f;
		QueenBeeRender.box(vc, pose, 0f, y, -2.42f, 4.05f, 0.62f, 0.2f, band, light);
		QueenBeeRender.box(vc, pose, 0f, y, 2.42f, 4.05f, 0.62f, 0.2f, RED_D, light);
		// the gold clasp at the throat
		QueenBeeRender.box(vc, pose, 0f, y + 0.5f, -2.7f, 0.95f, 0.85f, 0.16f, on ? GOLD_L : GOLD, on ? LightTexture.FULL_BRIGHT : light);
		QueenBeeRender.box(vc, pose, 0f, y + 1.6f, -2.7f, 0.42f, 0.42f, 0.14f, GOLD, light);
		pose.popPose();
	}
}
