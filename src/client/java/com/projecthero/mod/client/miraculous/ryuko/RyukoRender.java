package com.projecthero.mod.client.miraculous.ryuko;

import java.io.InputStream;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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

	/** v0.17.4 sword / choker colours: the black silk wrap, the crimson ray-skin it shows through, the frosted hamon, the tsuba's iron. */
	private static final int ITO = 0x141216, SAME = 0xB0202A, HAMON = 0xF6F9FC, IRON = 0x1E1B22, SHINOGI = 0xB9C3D2;

	/**
	 * The sword centred on the pose's origin -- the middle of her fist -- the blade running along -z with its edge on +y (down in the
	 * hand), in model pixels (scaled 1/16 here).
	 *
	 * <p>v0.17.4 (user: "Make the katana model look better please"): a real katana, 19.4 px long (a katana to her 32 px height). The fist
	 * closes on the tsuka just behind the guard: a black silk wrap (tsuka-ito) whose crossings leave crimson ray-skin diamonds on all four
	 * faces, gold menuki under the wrap on each side, a gold fuchi collar and kashira cap. Then the seppa washers, a round black iron
	 * tsuba with a gold rim, the gold habaki, and a CURVED blade (10 degrees of sori over twelve links): steel faces, a darker spine
	 * (mune) and ridge line (shinogi), a frosted wavy hamon along the edge with the edge itself glowing her element's colour, and a
	 * proper kissaki whose edge sweeps up to the spine past the yokote line.
	 */
	public static void renderSword(PoseStack pose, MultiBufferSource buffers, int light, int elementRgb) {
		VertexConsumer vc = boxes(buffers);
		pose.pushPose();
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		// ---- the tsuka (z +3.6 .. -1.6): the wrap, its diamonds, the menuki; the kashira behind and the fuchi in front
		float hz0 = 3.6f, hz1 = -1.55f, hzc = (hz0 + hz1) * 0.5f, hhz = (hz0 - hz1) * 0.5f;
		QueenBeeRender.box(vc, pose, 0f, 0f, hzc, 0.42f, 0.52f, hhz, ITO, light);
		for (int i = 0; i < 6; i++) {
			float z = hz0 - 0.5f - i * 0.84f;
			for (int s = -1; s <= 1; s += 2) {
				pose.pushPose(); // on the side faces: squares turned 45 degrees about x
				pose.translate(s * 0.43f, 0f, z);
				pose.mulPose(Axis.XP.rotationDegrees(45f));
				QueenBeeRender.box(vc, pose, 0f, 0f, 0f, 0.03f, 0.22f, 0.22f, SAME, light);
				pose.popPose();
				pose.pushPose(); // on the top and bottom faces: turned about y
				pose.translate(0f, s * 0.53f, z);
				pose.mulPose(Axis.YP.rotationDegrees(45f));
				QueenBeeRender.box(vc, pose, 0f, 0f, 0f, 0.21f, 0.03f, 0.21f, SAME, light);
				pose.popPose();
			}
		}
		QueenBeeRender.box(vc, pose, 0.47f, 0f, 1.25f, 0.05f, 0.2f, 0.55f, GOLD, light); // menuki
		QueenBeeRender.box(vc, pose, -0.47f, 0f, 0.45f, 0.05f, 0.2f, 0.55f, GOLD, light);
		QueenBeeRender.box(vc, pose, 0f, 0f, hz0 + 0.18f, 0.46f, 0.56f, 0.2f, GOLD, light); // kashira
		QueenBeeRender.box(vc, pose, 0f, 0f, hz0 + 0.42f, 0.36f, 0.44f, 0.06f, RED_D, light);
		QueenBeeRender.box(vc, pose, 0f, 0f, hz1 - 0.16f, 0.47f, 0.57f, 0.17f, GOLD, light); // fuchi
		// ---- seppa, the round tsuba (a disc of four overlapping boxes; black iron in front of a slightly larger gold rim), seppa
		QueenBeeRender.box(vc, pose, 0f, 0f, -1.92f, 0.62f, 0.72f, 0.05f, GOLD_L, light);
		float tz = -2.12f;
		disc(vc, pose, tz, 1.1f, 0.07f, GOLD, light);
		disc(vc, pose, tz, 0.97f, 0.12f, IRON, light);
		// two crimson inlays on each face of the tsuba (the dragon's colours)
		for (int s = -1; s <= 1; s += 2) {
			QueenBeeRender.box(vc, pose, s * 0.55f, -0.55f, tz, 0.16f, 0.16f, 0.14f, SAME, light);
			QueenBeeRender.box(vc, pose, -s * 0.55f, 0.55f, tz, 0.16f, 0.16f, 0.14f, SAME, light);
		}
		QueenBeeRender.box(vc, pose, 0f, 0f, -2.32f, 0.5f, 0.66f, 0.05f, GOLD_L, light);
		// ---- the habaki round the blade's root
		QueenBeeRender.box(vc, pose, 0f, -0.02f, -2.7f, 0.17f, 0.58f, 0.33f, GOLD, light);
		// ---- the blade: twelve links from z -2.4, each turned a little toward the spine (-y), the last becoming the kissaki
		pose.pushPose();
		pose.translate(0f, 0f, -2.4f);
		final int N = 12;
		final float LEN = 13.0f, L = LEN / N, SORI = 10f;
		for (int i = 0; i < N; i++) {
			float t = i / (float) (N - 1);
			float h = Mth.lerp(t, 1.0f, 0.78f); // spine fixed at y -0.5, the edge at -0.5 + h
			float spine = -0.5f, edge = spine + h;
			if (i < N - 1) {
				QueenBeeRender.box(vc, pose, 0f, spine + h * 0.5f, -L * 0.5f, 0.085f, h * 0.5f, L * 0.5f + 0.03f, STEEL, light);
				QueenBeeRender.box(vc, pose, 0f, spine + 0.07f, -L * 0.5f, 0.1f, 0.07f, L * 0.5f + 0.03f, STEEL_D, light); // mune
				QueenBeeRender.box(vc, pose, 0f, spine + h * 0.36f, -L * 0.5f, 0.092f, 0.025f, L * 0.5f + 0.03f, SHINOGI, light); // shinogi
				// the hamon: a frosted band along the edge, its upper line rising and falling twice a link (gunome)
				for (int k = 0; k < 2; k++) {
					float hh = 0.27f + 0.07f * Mth.sin((i * 2 + k) * 1.9f);
					QueenBeeRender.box(vc, pose, 0f, edge - hh * 0.5f, -L * (0.25f + 0.5f * k), 0.093f, hh * 0.5f, L * 0.25f + 0.02f, HAMON, light);
				}
				QueenBeeRender.box(vc, pose, 0f, edge - 0.035f, -L * 0.5f, 0.05f, 0.05f, L * 0.5f + 0.03f, elementRgb, LightTexture.FULL_BRIGHT);
			} else {
				// the kissaki: the yokote line, then steps whose edge sweeps up to meet the spine at the point
				QueenBeeRender.box(vc, pose, 0f, spine + h * 0.5f, -0.04f, 0.095f, h * 0.5f, 0.04f, STEEL_D, light);
				int steps = 6;
				float sl = (L + 0.6f) / steps;
				for (int j = 0; j < steps; j++) {
					float f = 1f - (j + 0.5f) / steps;
					float hj = h * (float) Math.sqrt(f) + 0.04f;
					float z = -0.08f - sl * (j + 0.5f);
					QueenBeeRender.box(vc, pose, 0f, spine + hj * 0.5f, z, 0.085f - 0.008f * j, hj * 0.5f, sl * 0.5f + 0.02f, STEEL, light);
					QueenBeeRender.box(vc, pose, 0f, spine + hj - Math.min(0.2f, hj * 0.4f) * 0.5f, z, 0.088f - 0.008f * j, Math.min(0.2f, hj * 0.4f) * 0.5f,
							sl * 0.5f + 0.02f, HAMON, light);
					QueenBeeRender.box(vc, pose, 0f, spine + 0.06f, z, 0.095f - 0.008f * j, 0.06f, sl * 0.5f + 0.02f, STEEL_D, light);
				}
			}
			pose.translate(0f, 0f, -L);
			pose.mulPose(Axis.XP.rotationDegrees(-SORI / (N - 1)));
		}
		pose.popPose();
		pose.popPose();
	}

	/** A round plate facing along z, radius {@code r} px: four overlapping boxes (wide, tall and two in between) round off a square. */
	private static void disc(VertexConsumer vc, PoseStack pose, float z, float r, float hz, int rgb, int light) {
		QueenBeeRender.box(vc, pose, 0f, 0f, z, r, r * 0.42f, hz, rgb, light);
		QueenBeeRender.box(vc, pose, 0f, 0f, z, r * 0.42f, r, hz, rgb, light);
		QueenBeeRender.box(vc, pose, 0f, 0f, z, r * 0.9f, r * 0.68f, hz, rgb, light);
		QueenBeeRender.box(vc, pose, 0f, 0f, z, r * 0.68f, r * 0.9f, hz, rgb, light);
	}

	/**
	 * The Dragon Choker at her throat, in the posed body's space (it follows the body: sneaking, turning). {@code on}: it glows as the
	 * Kwami goes in.
	 *
	 * <p>v0.17.4 (user: "make the choker/necklace model look better please"): no more square ring round the whole 8 x 4 collar. A block
	 * body has no neck, so the band is a curved choker that hugs where her neck would be -- an arc of twelve short links round the front
	 * of the throat, standing just proud of the chest at the middle and tucking into the collar at the sides -- deep red with gold piping
	 * along both edges, and the Dragon Miraculous hanging from it: a gold bail and dragon-head cap, a red dragon ball held in four gold
	 * claws, with a bright glint (glowing as the Kwami goes in).
	 */
	public static void renderChoker(PoseStack pose, MultiBufferSource buffers, int light, ModelPart body, boolean on) {
		VertexConsumer vc = boxes(buffers);
		pose.pushPose();
		body.translateAndRotate(pose);
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		int band = on ? 0x7E141C : RED_D, pipe = on ? GOLD_L : GOLD;
		// the arc: an ellipse round the throat (x 3.1, z 2.78), y 0.2 .. 1.0 just under the chin; links from -78 to +78 degrees
		float rx = 3.1f, rz = 2.78f, y = 0.6f;
		int links = 12;
		float a0 = -78f, a1 = 78f;
		for (int i = 0; i < links; i++) {
			float am = Mth.lerp((i + 0.5f) / links, a0, a1) * Mth.DEG_TO_RAD;
			float aa = Mth.lerp(i / (float) links, a0, a1) * Mth.DEG_TO_RAD, ab = Mth.lerp((i + 1f) / links, a0, a1) * Mth.DEG_TO_RAD;
			float xa = rx * Mth.sin(aa), za = -rz * Mth.cos(aa), xb = rx * Mth.sin(ab), zb = -rz * Mth.cos(ab);
			float len = Mth.sqrt((xb - xa) * (xb - xa) + (zb - za) * (zb - za));
			float yaw = (float) Math.atan2(zb - za, xb - xa);
			// the front dips a little (a choker sits lower at the throat than at the sides)
			float dip = 0.18f * Mth.cos(am);
			pose.pushPose();
			pose.translate(rx * Mth.sin(am), y + dip, -rz * Mth.cos(am));
			pose.mulPose(Axis.YP.rotation(-yaw));
			QueenBeeRender.box(vc, pose, 0f, 0f, 0f, len * 0.5f + 0.04f, 0.4f, 0.13f, band, light);
			QueenBeeRender.box(vc, pose, 0f, -0.4f, 0f, len * 0.5f + 0.04f, 0.06f, 0.17f, pipe, light);
			QueenBeeRender.box(vc, pose, 0f, 0.4f, 0f, len * 0.5f + 0.04f, 0.06f, 0.17f, pipe, light);
			pose.popPose();
		}
		// the pendant, hanging from the middle of the band
		float pz = -rz - 0.3f, top = y + 0.18f + 0.45f;
		int glow = on ? LightTexture.FULL_BRIGHT : light;
		QueenBeeRender.box(vc, pose, 0f, top + 0.2f, pz, 0.18f, 0.28f, 0.12f, pipe, light); // bail
		QueenBeeRender.box(vc, pose, 0f, top + 0.58f, pz - 0.05f, 0.52f, 0.14f, 0.24f, pipe, light); // dragon-head cap
		QueenBeeRender.box(vc, pose, 0f, top + 0.5f, pz - 0.32f, 0.2f, 0.1f, 0.06f, RED_D, light); // its brow gem
		float cy = top + 1.45f, cz = pz - 0.15f, r = 0.72f;
		int ball = on ? 0xFF4A3A : 0xD0262C, ballLit = on ? 0xFFB09A : 0xF0605A;
		QueenBeeRender.box(vc, pose, 0f, cy, cz, r, r * 0.68f, r * 0.68f, ball, glow); // the dragon ball: a rounded cube of three boxes
		QueenBeeRender.box(vc, pose, 0f, cy, cz, r * 0.68f, r, r * 0.68f, ball, glow);
		QueenBeeRender.box(vc, pose, 0f, cy, cz, r * 0.68f, r * 0.68f, r, ball, glow);
		QueenBeeRender.box(vc, pose, -0.24f, cy - 0.26f, cz - r - 0.01f, 0.14f, 0.14f, 0.02f, ballLit, glow); // the glint
		QueenBeeRender.box(vc, pose, -0.34f, cy - 0.1f, cz - r - 0.01f, 0.06f, 0.1f, 0.02f, 0xFFF0E8, glow);
		for (int sx = -1; sx <= 1; sx += 2) { // four gold claws gripping it
			for (int sy = -1; sy <= 1; sy += 2) {
				QueenBeeRender.box(vc, pose, sx * r * 0.78f, cy + sy * r * 0.62f, cz - r * 0.5f, 0.09f, 0.22f, 0.1f, pipe, light);
				QueenBeeRender.box(vc, pose, sx * r * 0.55f, cy + sy * r * 0.82f, cz - r * 0.62f, 0.14f, 0.08f, 0.09f, pipe, light);
			}
		}
		pose.popPose();
	}
}
