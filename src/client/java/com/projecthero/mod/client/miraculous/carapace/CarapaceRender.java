package com.projecthero.mod.client.miraculous.carapace;

import java.io.InputStream;
import java.util.Optional;

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
 * v0.16.6: Carapace drawn over a player -- the user's chosen skin (minecraftskins.com 19407493 "carapace"), cut to a
 * suit-only overlay by {@code scratchpad/make_suit_skins.js} (the face's skin, the red cap and the hair are transparent;
 * the dark mask with its yellow-green lenses, the green shell hood on the hat layer and the green suit stay), in a slim
 * and a wide version picked by the wearer's own skin model -- an overlay, so his own lower face shows through.
 *
 * <p>v0.16.6 (user: "make sure this suit model isn't bulky"): the base layer sits a hair off the body
 * ({@link #BASE_INFLATE}); the second layer (the hood, jacket, sleeves, trousers) at vanilla's own overlay offsets,
 * nothing added -- two models ({@link #model} / {@link #overlayModel}). The wearer's own second layer, hat included,
 * is hidden while the suit shows (the reveal redraws it texel by texel).
 *
 * <p><b>The Shield</b> ({@link #renderShieldModel}) is a real mesh, not cubes: a round disc slightly domed in front
 * (rings of quads, the turtle-shell texture projected flat onto it), a rim band and a flat back with a leather strap.
 *
 * <p><b>The transformation</b> ({@link #cover}): over {@code MiraculousConfig.TRANSFORM_TICKS} the suit spreads texel by
 * texel from the chest up over the hood and down to the boots, its leading edge a band of bright green light; the
 * wearer's own second layer goes in step ({@link OverlayStrips}); detransforming runs it backwards.
 */
public final class CarapaceRender {
	public static final ResourceLocation SUIT = ProjectHeroMod.id("textures/entity/miraculous/carapace/suit.png");
	public static final ResourceLocation SUIT_WIDE = ProjectHeroMod.id("textures/entity/miraculous/carapace/suit_wide.png");
	public static final ResourceLocation SHIELD = ProjectHeroMod.id("textures/entity/miraculous/carapace/shield.png");
	public static final ResourceLocation HEX = ProjectHeroMod.id("textures/entity/miraculous/carapace/hex.png");

	private static final int FRAMES = 32;

	/** The Shield's radius, its dome's height and its thickness at the rim (blocks). */
	public static final float SHIELD_R = 7.5f / 16f;
	public static final float SHIELD_DOME = 2.6f / 16f;
	public static final float SHIELD_THICK = 1.2f / 16f;

	/** The base layer's offset off the body (model pixels): just enough to clear the wearer's own skin. */
	public static final float BASE_INFLATE = 0.02f;

	private static PlayerModel<LivingEntity> slimModel;
	private static PlayerModel<LivingEntity> wideModel;
	private static PlayerModel<LivingEntity> slimOverlay;
	private static PlayerModel<LivingEntity> wideOverlay;
	private static ResourceLocation[] slimFrames;
	private static ResourceLocation[] wideFrames;

	private CarapaceRender() {
	}

	// ---------------------------------------------------------------- the suit models

	private static PlayerModel<LivingEntity> bake(float inflate, boolean slim) {
		return new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(inflate), slim), 64, 64).bakeRoot(), slim);
	}

	/** The base layer (only its head, body, arms and legs are drawn): {@link #BASE_INFLATE} off the body. */
	public static PlayerModel<LivingEntity> model(boolean slim) {
		if (slim) {
			if (slimModel == null) {
				slimModel = bake(BASE_INFLATE, true);
			}
			return slimModel;
		}
		if (wideModel == null) {
			wideModel = bake(BASE_INFLATE, false);
		}
		return wideModel;
	}

	/** The second layer (only its hat, jacket, sleeves and trousers are drawn): exactly vanilla's +0.5 / +0.25. */
	public static PlayerModel<LivingEntity> overlayModel(boolean slim) {
		if (slim) {
			if (slimOverlay == null) {
				slimOverlay = bake(0f, true);
			}
			return slimOverlay;
		}
		if (wideOverlay == null) {
			wideOverlay = bake(0f, false);
		}
		return wideOverlay;
	}

	// ---------------------------------------------------------------- the reveal

	/** How much of the suit shows on {@code player} now, 0..1 (1 = all of it). */
	public static float cover(Player player, float partial) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null || !s.hasPower || !Kwami.WAYZZ.id.equals(s.kwami) || player.level() == null) {
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

	/** When base texel ({@code u}, {@code v}) turns into the suit, 0..1 -- by height from the chest (model y 10) up and down, with grain; 2 = unused. */
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
		float dist = Math.abs(h - 10f) / (h < 10f ? 10f : 22f);
		return Mth.clamp(0.03f + 0.85f * dist + 0.1f * hash(u, v), 0f, 0.995f);
	}

	private static float hash(int x, int y) {
		int h = x * 374761393 + y * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFF) / 65535f;
	}

	public static ResourceLocation texture(boolean slim, float cover) {
		if (cover >= 0.999f) {
			return slim ? SUIT : SUIT_WIDE;
		}
		ResourceLocation[] f = slim ? slimFrames : wideFrames;
		if (f == null) {
			f = build(slim ? SUIT : SUIT_WIDE, slim ? "slim" : "wide");
			if (slim) {
				slimFrames = f;
			} else {
				wideFrames = f;
			}
		}
		if (f.length == 0) {
			return slim ? SUIT : SUIT_WIDE;
		}
		return f[Math.max(0, Math.min(FRAMES, Math.round(cover * FRAMES)))];
	}

	/** Frame k shows every texel whose order is at most k / FRAMES; the newest glow green-white (the light). */
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
			ProjectHeroMod.LOGGER.warn("[carapace] could not read the suit for the reveal", e);
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
			out[k] = mc.getTextureManager().register("projecthero_carapace_" + name + "_" + k, new DynamicTexture(img));
		}
		src.close();
		return out;
	}

	/** The leading edge of the light: hot green-white fading into the texel ({@code t} 0 = brightest). NativeImage is ABGR. */
	private static int light(int abgr, float t) {
		int a = (abgr >>> 24) & 0xFF, b = (abgr >>> 16) & 0xFF, g = (abgr >>> 8) & 0xFF, r = abgr & 0xFF;
		float k = 1f - t;
		r = (int) Mth.lerp(k, r, 190);
		g = (int) Mth.lerp(k, g, 255);
		b = (int) Mth.lerp(k, b, 190);
		return (a << 24) | (b << 16) | (g << 8) | r;
	}

	// ---------------------------------------------------------------- drawing the suit

	/** {@code m} posed like {@code parent}; only its base parts ({@code overlay} false) or only its second layer shown. */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static PlayerModel<LivingEntity> posed(PlayerModel<LivingEntity> m, HumanoidModel<?> parent, boolean overlay) {
		((HumanoidModel) parent).copyPropertiesTo(m);
		m.jacket.copyFrom(m.body);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftPants.copyFrom(m.leftLeg);
		m.rightPants.copyFrom(m.rightLeg);
		m.hat.copyFrom(m.head);
		m.head.visible = m.body.visible = m.leftArm.visible = m.rightArm.visible = m.leftLeg.visible = m.rightLeg.visible = !overlay;
		m.hat.visible = m.jacket.visible = m.leftSleeve.visible = m.rightSleeve.visible = m.leftPants.visible = m.rightPants.visible = overlay;
		return m;
	}

	public static void renderSuit(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, boolean slim, float cover) {
		if (cover <= 0f) {
			return;
		}
		var vc = buffers.getBuffer(RenderType.entityTranslucent(texture(slim, cover)));
		posed(model(slim), parent, false).renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		posed(overlayModel(slim), parent, true).renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
	}

	/** First person: the suit sleeve and glove over the bare arm vanilla just drew. */
	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, ModelPart arm, boolean right, boolean slim, float cover) {
		PlayerModel<LivingEntity> m = model(slim);
		PlayerModel<LivingEntity> o = overlayModel(slim);
		ModelPart a = right ? m.rightArm : m.leftArm;
		ModelPart sleeve = right ? o.rightSleeve : o.leftSleeve;
		a.copyFrom(arm);
		sleeve.copyFrom(arm);
		a.visible = true;
		sleeve.visible = true;
		ResourceLocation tex = texture(slim, cover);
		a.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
	}

	// ---------------------------------------------------------------- the Shield

	/** Where the Shield is drawn on a player. */
	public enum ShieldAt {
		NONE, BACK, ARM, GUARD, FEET, ROLL
	}

	/**
	 * The Shield on {@code parent}: on its back (strapped over the shell), on its left forearm (the face out to the side
	 * and front), raised in front for Shell Guard, flat under the feet for Shield Surf, or big over the curled back for
	 * Shell Roll.
	 */
	public static void renderShield(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, ShieldAt at, boolean slim) {
		if (at == ShieldAt.NONE) {
			return;
		}
		pose.pushPose();
		switch (at) {
			case BACK -> {
				parent.body.translateAndRotate(pose);
				pose.translate(0f, 6f / 16f, 2.6f / 16f);
				// the face out of his back (+Z of the body)
				pose.mulPose(Axis.ZP.rotationDegrees(12f));
			}
			case ROLL -> {
				parent.body.translateAndRotate(pose);
				pose.translate(0f, 5.5f / 16f, 2.4f / 16f);
				pose.scale(1.45f, 1.45f, 1.3f);
			}
			case GUARD -> {
				// held up square in front of the chest (the face forward, -Z of the body)
				parent.body.translateAndRotate(pose);
				pose.translate(1.2f / 16f, 4.5f / 16f, -6.5f / 16f);
				pose.mulPose(Axis.YP.rotationDegrees(180f));
				pose.scale(1.15f, 1.15f, 1.15f);
			}
			case ARM -> {
				parent.leftArm.translateAndRotate(pose);
				// the outer side of the left forearm is +X; the disc stands on it, its face out
				pose.translate((slim ? 2.6f : 3.1f) / 16f, 7f / 16f, 0f);
				pose.mulPose(Axis.YP.rotationDegrees(90f));
			}
			case FEET -> {
				// under the feet in the body's frame (the model's origin is at the hips; the soles at y 24 px)
				pose.translate(0f, 24.2f / 16f, 0f);
				pose.mulPose(Axis.XP.rotationDegrees(90f));
				pose.scale(1.35f, 1.6f, 1.0f);
			}
			default -> {
			}
		}
		renderShieldModel(pose, buffers, light, 1f);
		pose.popPose();
	}

	private static final int SEGMENTS = 28;
	private static final int RINGS = 6;

	/**
	 * The bare Shield at the pose's origin: the disc in the X/Y plane, the domed face toward +Z (out), the flat
	 * back at z = -{@link #SHIELD_THICK}. Radius {@link #SHIELD_R}.
	 */
	public static void renderShieldModel(PoseStack pose, MultiBufferSource buffers, int light, float alpha) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(SHIELD));
		PoseStack.Pose last = pose.last();
		int a = Math.round(255 * Mth.clamp(alpha, 0f, 1f));
		float r = SHIELD_R, dome = SHIELD_DOME, back = -SHIELD_THICK;
		float uvR = 30.5f / 64f;
		for (int ring = 0; ring < RINGS; ring++) {
			float r0 = r * ring / RINGS, r1 = r * (ring + 1) / RINGS;
			float z0 = dome * (1f - (r0 / r) * (r0 / r)), z1 = dome * (1f - (r1 / r) * (r1 / r));
			for (int seg = 0; seg < SEGMENTS; seg++) {
				float a0 = (float) (Math.PI * 2 * seg / SEGMENTS), a1 = (float) (Math.PI * 2 * (seg + 1) / SEGMENTS);
				float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
				// the dome's normal tilts outward with the slope
				float n0 = 2f * dome * r0 / (r * r), n1 = 2f * dome * r1 / (r * r);
				vtx(vc, last, r0 * c0, r0 * s0, z0, 0.5f + uvR * (r0 / r) * c0, 0.5f + uvR * (r0 / r) * s0, n0 * c0, n0 * s0, 1f, a, light);
				vtx(vc, last, r1 * c0, r1 * s0, z1, 0.5f + uvR * (r1 / r) * c0, 0.5f + uvR * (r1 / r) * s0, n1 * c0, n1 * s0, 1f, a, light);
				vtx(vc, last, r1 * c1, r1 * s1, z1, 0.5f + uvR * (r1 / r) * c1, 0.5f + uvR * (r1 / r) * s1, n1 * c1, n1 * s1, 1f, a, light);
				vtx(vc, last, r0 * c1, r0 * s1, z0, 0.5f + uvR * (r0 / r) * c1, 0.5f + uvR * (r0 / r) * s1, n0 * c1, n0 * s1, 1f, a, light);
			}
		}
		// the rim band (the rim's side patch of the texture) and the flat back (the back patch, the strap across it)
		float ru0 = 57f / 64f, ru1 = 63f / 64f, rv0 = 1f / 64f, rv1 = 7f / 64f;
		float bu0 = 1f / 64f, bu1 = 7f / 64f, bv0 = 1f / 64f, bv1 = 7f / 64f;
		for (int seg = 0; seg < SEGMENTS; seg++) {
			float a0 = (float) (Math.PI * 2 * seg / SEGMENTS), a1 = (float) (Math.PI * 2 * (seg + 1) / SEGMENTS);
			float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
			vtx(vc, last, r * c0, r * s0, 0f, ru0, rv0, c0, s0, 0f, a, light);
			vtx(vc, last, r * c0, r * s0, back, ru0, rv1, c0, s0, 0f, a, light);
			vtx(vc, last, r * c1, r * s1, back, ru1, rv1, c1, s1, 0f, a, light);
			vtx(vc, last, r * c1, r * s1, 0f, ru1, rv0, c1, s1, 0f, a, light);
			vtx(vc, last, 0f, 0f, back, bu0, bv0, 0f, 0f, -1f, a, light);
			vtx(vc, last, r * c1, r * s1, back, bu1, bv0, 0f, 0f, -1f, a, light);
			vtx(vc, last, r * c0, r * s0, back, bu1, bv1, 0f, 0f, -1f, a, light);
			vtx(vc, last, 0f, 0f, back, bu0, bv1, 0f, 0f, -1f, a, light);
		}
		// the strap: a band across the back, a hair proud of it
		float su0 = 1f / 64f, su1 = 7f / 64f, sv0 = 57f / 64f, sv1 = 63f / 64f;
		float sw = 1.3f / 16f, sl = r * 0.75f, sz = back - 0.4f / 16f;
		vtx(vc, last, -sl, -sw, sz, su0, sv0, 0f, 0f, -1f, a, light);
		vtx(vc, last, -sl, sw, sz, su0, sv1, 0f, 0f, -1f, a, light);
		vtx(vc, last, sl, sw, sz, su1, sv1, 0f, 0f, -1f, a, light);
		vtx(vc, last, sl, -sw, sz, su1, sv0, 0f, 0f, -1f, a, light);
	}

	private static void vtx(VertexConsumer vc, PoseStack.Pose last, float x, float y, float z, float u, float v, float nx, float ny, float nz, int a,
			int light) {
		float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
		if (len < 1.0e-4f) {
			nz = 1f;
			len = 1f;
		}
		vc.addVertex(last, x, y, z).setColor(255, 255, 255, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(last, nx / len, ny / len, nz / len);
	}
}
