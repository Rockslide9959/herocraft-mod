package com.projecthero.mod.client.miraculous.queenbee;

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
import com.projecthero.mod.miraculous.MiraculousConfig;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
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
 * v0.17.2: Queen Bee drawn over a player -- the user's suit skin (minecraftskins.com 23083151, a SLIM skin; cut to the
 * suit only by {@code scratchpad/queen_bee/gen_queen_bee_suit.js}: the hair, face and skin go, a yellow mask with open eye
 * holes stays) in a slim (as drawn) and a wide version picked by the wearer's skin model, drawn OVER the wearer's own
 * skin, so their face and hair show. The base layer sits {@link #BASE_INFLATE} over the body, the second layer at
 * vanilla's offsets; while the suit shows the wearer's jacket / sleeves / trousers are hidden (their hat layer -- their
 * hair -- stays, as Viperion's).
 *
 * <p><b>The transformation</b> ({@link #cover}): the suit runs down from her collar, under the Bee Comb, texel by texel, its
 * edge a band of honey-gold light, and the mask comes last; detransforming runs it backwards. "Pollen, buzz on!" in full
 * paces it with {@code QueenBeeSequence}.
 *
 * <p>Also the spinning top ({@link #renderTop}) and the Bee Comb ({@link #renderComb}), drawn from lit boxes.
 */
public final class QueenBeeRender {
	public static final ResourceLocation SUIT_WIDE = ProjectHeroMod.id("textures/entity/miraculous/queen_bee/suit_wide.png");
	public static final ResourceLocation SUIT_SLIM = ProjectHeroMod.id("textures/entity/miraculous/queen_bee/suit_slim.png");
	public static final float BASE_INFLATE = 0.03f;
	private static final int FRAMES = 32;

	public static final int GOLD = 0xF5C518, GOLD_L = 0xFFE35A, GOLD_D = 0xC48F0E, BLACK = 0x18140F, BLACK_L = 0x3A322A, STRING = 0xE8D27A,
			VENOM = 0xFFF27A;

	private static PlayerModel<LivingEntity> slimBase;
	private static PlayerModel<LivingEntity> wideBase;
	private static PlayerModel<LivingEntity> slimOver;
	private static PlayerModel<LivingEntity> wideOver;
	private static ResourceLocation[] slimFrames;
	private static ResourceLocation[] wideFrames;

	private QueenBeeRender() {
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
		if (s == null || !s.hasPower || !Kwami.POLLEN.id.equals(s.kwami) || player.level() == null) {
			return 0f;
		}
		// the long transformation runs the change at its own pace (QueenBeeSequence)
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
		// the mask last, wiped across her face from her right eye to her left (the long transformation's hand sweep)
		if (v < 16) {
			return Mth.clamp(0.88f + 0.1f * (u - 7) / 9f, 0.88f, 0.98f);
		}
		// from the collar down to her boots (height counts down from the crown: 8 = shoulders, 32 = soles), always under
		// QueenBeeSequence.BODY so the last band ends with the body covered
		float t = Mth.clamp((h - 8f) / 24f, 0f, 1f);
		return Mth.clamp(0.03f + 0.73f * t + 0.1f * hash(u, v), 0f, 0.86f);
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
			ProjectHeroMod.LOGGER.warn("[queen_bee] could not read the suit for the reveal", e);
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
			out[k] = mc.getTextureManager().register("projecthero_queen_bee_" + name + "_" + k, new DynamicTexture(img));
		}
		src.close();
		return out;
	}

	/** The leading edge: honey-gold light fading into the texel (NativeImage is ABGR). */
	private static int light(int abgr, float t) {
		int a = (abgr >>> 24) & 0xFF, b = (abgr >>> 16) & 0xFF, g = (abgr >>> 8) & 0xFF, r = abgr & 0xFF;
		float k = 1f - t;
		r = (int) Mth.lerp(k, r, 255);
		g = (int) Mth.lerp(k, g, 236);
		b = (int) Mth.lerp(k, b, 140);
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

	// ---------------------------------------------------------------- the spinning top

	/**
	 * The spinning top at the pose's origin, in model pixels (scaled 1/16 here), its axis along Y (the point down at
	 * y -3.2, the knob up at y 2.8): a black point, a gold-and-black striped double cone, a gold knob with a black stem.
	 * {@code spin} turns it about its axis (radians); {@code venom} 0..1 makes it glow pale gold, full bright.
	 */
	public static void renderTop(PoseStack pose, MultiBufferSource buffers, int light, float spin, float venom) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(com.projecthero.mod.client.maxsteel.TurboDraw.WHITE));
		int lit = venom > 0f ? LightTexture.FULL_BRIGHT : light;
		pose.pushPose();
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		pose.mulPose(Axis.YP.rotation(spin));
		box(vc, pose, 0f, -2.9f, 0f, 0.35f, 0.35f, 0.35f, tint(BLACK, venom), lit);
		box(vc, pose, 0f, -2.2f, 0f, 0.9f, 0.4f, 0.9f, tint(BLACK_L, venom), lit);
		box(vc, pose, 0f, -1.4f, 0f, 1.6f, 0.45f, 1.6f, tint(GOLD, venom), lit);
		box(vc, pose, 0f, -0.55f, 0f, 2.2f, 0.42f, 2.2f, tint(BLACK, venom), lit);
		box(vc, pose, 0f, 0.25f, 0f, 2.4f, 0.4f, 2.4f, tint(GOLD_L, venom), lit);
		box(vc, pose, 0f, 0.95f, 0f, 1.9f, 0.32f, 1.9f, tint(BLACK, venom), lit);
		box(vc, pose, 0f, 1.5f, 0f, 1.2f, 0.25f, 1.2f, tint(GOLD, venom), lit);
		box(vc, pose, 0f, 2.05f, 0f, 0.5f, 0.35f, 0.5f, tint(BLACK, venom), lit);
		box(vc, pose, 0f, 2.6f, 0f, 0.65f, 0.22f, 0.65f, tint(GOLD_D, venom), lit);
		// a glint on the rim so the spin reads
		box(vc, pose, 2.15f, 0.25f, 0f, 0.3f, 0.42f, 0.5f, tint(0xFFF6C8, venom), lit);
		box(vc, pose, -1.5f, -1.4f, 0.6f, 0.2f, 0.47f, 0.4f, tint(GOLD_D, venom), lit);
		pose.popPose();
	}

	private static int tint(int rgb, float venom) {
		if (venom <= 0f) {
			return rgb;
		}
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		float k = Math.min(1f, venom) * 0.65f;
		r = (int) Mth.lerp(k, r, 255);
		g = (int) Mth.lerp(k, g, 242);
		b = (int) Mth.lerp(k, b, 122);
		return (r << 16) | (g << 8) | b;
	}

	// ---------------------------------------------------------------- the Bee Comb

	/**
	 * The Bee Comb in her hair at the back of the head (head space, model pixels): a striped gold-and-black spine across
	 * the back, three teeth into the hair. {@code on}: transformed (the gold brighter).
	 */
	public static void renderComb(PoseStack pose, MultiBufferSource buffers, int light, ModelPart head, boolean on) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(com.projecthero.mod.client.maxsteel.TurboDraw.WHITE));
		pose.pushPose();
		head.translateAndRotate(pose);
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		float z = 4.85f; // just proud of the hair layer (the hat sits 0.5 out)
		float y = -6.4f;
		int gold = on ? GOLD_L : GOLD;
		box(vc, pose, 0f, y, z, 2.4f, 0.55f, 0.35f, gold, light);
		box(vc, pose, 0f, y + 0.85f, z, 1.9f, 0.3f, 0.3f, BLACK, light);
		box(vc, pose, 0f, y + 1.3f, z, 1.3f, 0.2f, 0.28f, gold, light);
		box(vc, pose, -1.6f, y, z + 0.05f, 0.35f, 0.6f, 0.38f, BLACK, light);
		box(vc, pose, 1.6f, y, z + 0.05f, 0.35f, 0.6f, 0.38f, BLACK, light);
		for (int i = -1; i <= 1; i++) {
			box(vc, pose, i * 1.4f, y - 0.95f, z - 0.15f, 0.22f, 0.5f, 0.22f, GOLD_D, light);
		}
		pose.popPose();
	}

	// ---------------------------------------------------------------- boxes

	/** A string (or any rod) from a to b, {@code r} thick, in the current pose. */
	public static void segment(VertexConsumer vc, PoseStack pose, float ax, float ay, float az, float bx, float by, float bz, float r, int rgb, int light) {
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
	public static void box(VertexConsumer vc, PoseStack pose, float x, float y, float z, float hx, float hy, float hz, int rgb, int light) {
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
