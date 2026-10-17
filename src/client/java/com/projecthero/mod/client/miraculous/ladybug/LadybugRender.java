package com.projecthero.mod.client.miraculous.ladybug;

import java.io.InputStream;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.client.model.geom.builders.MeshDefinition;
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
 * v0.16.6: Ladybug's suit -- the user's chosen skin (NovaSkin post 5143214044, "Ladybug new outfit, season 4") with its
 * face, skin and hair cut out (the user: "so that the player's skin can show up instead"; {@code scratchpad/make_suit_skins.js}):
 * only the bodysuit, gloves, boots and the spotted mask round the eyes (the skin's own, eye holes open) are left, in a
 * wide-arm and a slim-arm cut so the suit's arms always line up with the wearer's. It is drawn as an overlay a hair over the
 * wearer's own skin (the Nova technique; the slim or wide cut and model to match their skin), so their face and hair show through. While
 * it shows, the wearer's jacket / sleeve / trouser layers are hidden (they would poke through) but the hat layer stays
 * ({@link LadybugLayer}).
 *
 * <p><b>The transformation</b> ({@link #anim}): the suit spreads down from the earrings -- every texel appears in order
 * of its height on the body (top of the head first, the boots last) with a little noise so the edge is ragged, the
 * newest texels flashing a pale magic pink. Detransforming runs it backwards in {@link #DETRANSFORM_TICKS}. The
 * wearer's own second layer goes texel by texel in step ({@link OverlayStrips} with {@link #order}).
 */
public final class LadybugRender {
	/** The suit for wide (4 px) arms and for slim (3 px) arms: each wearer gets the one matching their own skin. */
	public static final ResourceLocation SKIN_WIDE = ProjectHeroMod.id("textures/entity/miraculous/ladybug/suit_wide.png");
	public static final ResourceLocation SKIN_SLIM = ProjectHeroMod.id("textures/entity/miraculous/ladybug/suit_slim.png");
	/** The suit coming off (the core only times the transformation). */
	public static final int DETRANSFORM_TICKS = 20;
	private static final int FRAMES = 40;

	private static PlayerModel<LivingEntity> wide;
	private static PlayerModel<LivingEntity> slim;
	private static ResourceLocation[] framesWide;
	private static ResourceLocation[] framesSlim;
	private static float[] orders;

	private LadybugRender() {
	}

	/** {@code cover}: 0..1, every texel with {@link #order} at or below it is suit. {@code fresh}: a change is running. */
	public record Anim(float cover, boolean fresh) {
		static final Anim NONE = new Anim(0f, false);
		static final Anim FULL = new Anim(1f, false);
	}

	public static PlayerModel<LivingEntity> model(boolean slimArms) {
		if (slimArms) {
			if (slim == null) {
				slim = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.04f), true), 64, 64).bakeRoot(), true);
			}
			return slim;
		}
		if (wide == null) {
			wide = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.04f), false), 64, 64).bakeRoot(), false);
		}
		return wide;
	}

	private static boolean slim(Player p) {
		return p instanceof net.minecraft.client.player.AbstractClientPlayer cp && cp.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
	}

	public static Anim anim(Player player, float partial) {
		if (player.level() == null || !Miraculous.holds(player, Kwami.TIKKI)) {
			return Anim.NONE;
		}
		MiraculousState s = Miraculous.peek(player);
		if (s == null) {
			return Anim.NONE;
		}
		float age = player.level().getGameTime() - s.formChangeAt + partial;
		if (age < 0f && age > -40f) {
			age = 0f;
		}
		if (s.transformed) {
			float total = MiraculousConfig.TRANSFORM_TICKS;
			if (s.formChangeAt <= 0L || age >= total || age < 0f) {
				return Anim.FULL;
			}
			return new Anim(Mth.clamp(age / total, 0f, 1f), true);
		}
		float total = DETRANSFORM_TICKS;
		if (s.formChangeAt <= 0L || age >= total || age < 0f) {
			return Anim.NONE;
		}
		return new Anim(1f - Mth.clamp(age / total, 0f, 1f), true);
	}

	public static float progress(Player player, float partial) {
		return anim(player, partial).cover();
	}

	public static boolean hidesSkinOverlay(Player player) {
		return progress(player, 0f) > 0f;
	}

	// ---------------------------------------------------------------- the order of the change

	/** When base-layer texel ({@code u}, {@code v}) (64x64; overlay texels ask about the base under them) turns to suit; 2 = unused. */
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
		// down from the earrings: the head first, the boots last; a ragged edge
		float n = 0.6f * hash(u / 2, v / 2) + 0.4f * hash(u, v + 31);
		return Mth.clamp(0.02f + 0.82f * (h / 32f) + 0.14f * n, 0f, 0.999f);
	}

	private static float hash(int x, int y) {
		int h = x * 374761393 + y * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFF) / 65535f;
	}

	// ---------------------------------------------------------------- the body

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static PlayerModel<LivingEntity> posed(HumanoidModel<?> parent, boolean slimArms) {
		PlayerModel<LivingEntity> m = model(slimArms);
		((HumanoidModel) parent).copyPropertiesTo(m);
		m.jacket.copyFrom(m.body);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftPants.copyFrom(m.leftLeg);
		m.rightPants.copyFrom(m.rightLeg);
		m.hat.copyFrom(m.head);
		m.setAllVisible(true);
		return m;
	}

	public static void render(PoseStack pose, MultiBufferSource buffers, int light, Player player, HumanoidModel<?> parent, Anim a) {
		if (a.cover() <= 0f) {
			return;
		}
		boolean sl = slim(player);
		PlayerModel<LivingEntity> m = posed(parent, sl);
		ResourceLocation tex = a.cover() >= 0.999f ? skin(sl) : frame(sl, a.cover());
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
	}

	/** First person: the suit's sleeve over the arm vanilla just drew. */
	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, Player player, ModelPart arm, boolean right, float progress) {
		boolean sl = slim(player);
		PlayerModel<LivingEntity> m = model(sl);
		ModelPart a = right ? m.rightArm : m.leftArm;
		ModelPart sleeve = right ? m.rightSleeve : m.leftSleeve;
		a.copyFrom(arm);
		sleeve.copyFrom(arm);
		a.visible = true;
		sleeve.visible = true;
		ResourceLocation tex = progress >= 0.999f ? skin(sl) : frame(sl, progress);
		a.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
	}

	// ---------------------------------------------------------------- the frames

	private static ResourceLocation skin(boolean slimArms) {
		return slimArms ? SKIN_SLIM : SKIN_WIDE;
	}

	private static ResourceLocation frame(boolean slimArms, float cover) {
		if (framesWide == null) {
			framesWide = build(SKIN_WIDE, "wide");
			framesSlim = build(SKIN_SLIM, "slim");
		}
		ResourceLocation[] frames = slimArms ? framesSlim : framesWide;
		if (frames.length == 0) {
			return skin(slimArms);
		}
		return frames[Math.max(0, Math.min(FRAMES, Math.round(cover * FRAMES)))];
	}

	/** Frame {@code k}: every texel with {@link #order} at most {@code k / FRAMES}; the newest flash pale pink. */
	private static ResourceLocation[] build(ResourceLocation skinTex, String tag) {
		Minecraft mc = Minecraft.getInstance();
		Optional<Resource> res = mc.getResourceManager().getResource(skinTex);
		if (res.isEmpty()) {
			return new ResourceLocation[0];
		}
		NativeImage src;
		try (InputStream in = res.get().open()) {
			src = NativeImage.read(in);
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[ladybug] could not read the suit for the transformation", e);
			return new ResourceLocation[0];
		}
		int size = src.getWidth();
		int scale = Math.max(1, size / 64);
		ResourceLocation[] frames = new ResourceLocation[FRAMES + 1];
		for (int k = 0; k <= FRAMES; k++) {
			float line = k / (float) FRAMES;
			NativeImage img = new NativeImage(size, size, true);
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					int px = src.getPixelRGBA(x, y);
					float o = order(x / scale, y / scale);
					if (o > line || k == 0) {
						img.setPixelRGBA(x, y, 0);
					} else if (k < FRAMES && line - o < 0.05f && ((px >>> 24) & 0xFF) > 0) {
						img.setPixelRGBA(x, y, magic(px));
					} else {
						img.setPixelRGBA(x, y, px);
					}
				}
			}
			frames[k] = mc.getTextureManager().register("projecthero_ladybug_change_" + tag + "_" + k, new DynamicTexture(img));
		}
		src.close();
		return frames;
	}

	/** The magic edge: the texel washed toward pale pink (NativeImage pixels are ABGR). */
	private static int magic(int abgr) {
		int a = (abgr >>> 24) & 0xFF, b = (abgr >>> 16) & 0xFF, g = (abgr >>> 8) & 0xFF, r = abgr & 0xFF;
		r = (r + 255 * 2) / 3;
		g = (g + 190 * 2) / 3;
		b = (b + 210 * 2) / 3;
		return (a << 24) | (b << 16) | (g << 8) | r;
	}
}
