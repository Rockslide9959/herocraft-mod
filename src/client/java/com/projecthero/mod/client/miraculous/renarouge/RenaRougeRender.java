package com.projecthero.mod.client.miraculous.renarouge;

import java.io.InputStream;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.render.OverlayStrips;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.MiraculousConfig;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.renarouge.RenaRouge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: Rena Rouge drawn over a player (or one of her illusions) -- the user's suit skin (minecraftskins.com 17505838,
 * "rena rogue edit", slim arms) cut out by {@code scratchpad/make_suit_skins.js} (Alya's face, skin and hair are
 * transparent), in a slim and a wide version picked by the wearer's own skin model, both layers, on a second player
 * model a hair larger than the wearer's -- an overlay, so her own face, hair and bare skin show through it; a 3D
 * bushy fox tail (white tip, swaying) and fox ears; and the Flute (orange and white, finger holes, a white tip) on her
 * back, in her hand or at her lips.
 *
 * <p><b>The transformation</b> ({@link #cover}): over {@code MiraculousConfig.TRANSFORM_TICKS} the suit spreads texel by
 * texel from the Necklace at her throat, up over the face and down to the boots, its leading edge a band of bright
 * orange light; the wearer's own second layer goes in step ({@link OverlayStrips}). Detransforming runs it backwards.
 * An illusion dissolving runs the same frames out, faster, fading.
 */
public final class RenaRougeRender {
	public static final ResourceLocation SUIT = ProjectHeroMod.id("textures/entity/miraculous/rena_rouge/suit.png");
	public static final ResourceLocation SUIT_WIDE = ProjectHeroMod.id("textures/entity/miraculous/rena_rouge/suit_wide.png");
	public static final ResourceLocation FOX = ProjectHeroMod.id("textures/entity/miraculous/rena_rouge/fox_parts.png");
	public static final ResourceLocation FLUTE = ProjectHeroMod.id("textures/entity/miraculous/rena_rouge/flute.png");
	public static final ResourceLocation BUBBLE = ProjectHeroMod.id("textures/entity/miraculous/rena_rouge/bubble.png");
	/** v0.16.6: whether the 3D ears are drawn (the suit skin paints its own ear tufts on the hat layer). */
	public static boolean drawEars = true;

	private static final int FRAMES = 32;

	private static PlayerModel<LivingEntity> slimModel;
	private static PlayerModel<LivingEntity> wideModel;
	private static ModelPart fox;
	private static ModelPart flute;
	private static ResourceLocation[] slimFrames;
	private static ResourceLocation[] wideFrames;

	private RenaRougeRender() {
	}

	// ---------------------------------------------------------------- the models

	public static PlayerModel<LivingEntity> model(boolean slim) {
		if (slim) {
			if (slimModel == null) {
				slimModel = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.04f), true), 64, 64).bakeRoot(), true);
			}
			return slimModel;
		}
		if (wideModel == null) {
			wideModel = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.04f), false), 64, 64).bakeRoot(), false);
		}
		return wideModel;
	}

	/** The tail (five hanging segments, each a child of the last) and the two ears; texture {@link #FOX}, 64x32. */
	private static ModelPart fox() {
		if (fox == null) {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			// segment k: w (x), length (y, along the tail), d (z); uv as painted by the generator
			int[][] seg = { { 3, 4, 3, 0, 0 }, { 4, 4, 4, 13, 0 }, { 5, 6, 5, 30, 0 }, { 5, 5, 5, 0, 12 }, { 4, 4, 4, 21, 12 } };
			PartDefinition parent = root.addOrReplaceChild("tail0", CubeListBuilder.create().texOffs(seg[0][3], seg[0][4])
					.addBox(-seg[0][0] / 2f, 0f, -seg[0][2] / 2f, seg[0][0], seg[0][1], seg[0][2]), PartPose.ZERO);
			for (int k = 1; k < seg.length; k++) {
				parent = parent.addOrReplaceChild("tail" + k, CubeListBuilder.create().texOffs(seg[k][3], seg[k][4])
						.addBox(-seg[k][0] / 2f, -0.5f, -seg[k][2] / 2f, seg[k][0], seg[k][1], seg[k][2]), PartPose.offset(0f, seg[k - 1][1] - 0.5f, 0f));
			}
			for (int side = -1; side <= 1; side += 2) {
				PartDefinition ear = root.addOrReplaceChild(side < 0 ? "ear_r" : "ear_l", CubeListBuilder.create().texOffs(0, 24)
						.addBox(-2f, -3f, -0.5f, 4, 3, 1), PartPose.ZERO);
				ear.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(12, 24).addBox(-1f, -2f, -0.5f, 2, 2, 1), PartPose.offset(0f, -3f, 0f));
			}
			fox = LayerDefinition.create(mesh, 64, 32).bakeRoot();
		}
		return fox;
	}

	/** The Flute along its own Y: the mouthpiece ring at y -11, the shaft to +10, the white tip to +13. Texture {@link #FLUTE}, 32x32. */
	public static ModelPart flute() {
		if (flute == null) {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			root.addOrReplaceChild("shaft", CubeListBuilder.create().texOffs(0, 0).addBox(-1f, -10f, -1f, 2, 20, 2), PartPose.ZERO);
			root.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(10, 0).addBox(-1.5f, 10f, -1.5f, 3, 3, 3), PartPose.ZERO);
			root.addOrReplaceChild("ring", CubeListBuilder.create().texOffs(10, 8).addBox(-1.5f, -11f, -1.5f, 3, 1, 3), PartPose.ZERO);
			flute = LayerDefinition.create(mesh, 32, 32).bakeRoot();
		}
		return flute;
	}

	// ---------------------------------------------------------------- the reveal

	/** How much of the suit shows on {@code player} now, 0..1 (1 = all of it). */
	public static float cover(Player player, float partial) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null || !s.hasPower || !Kwami.TRIXX.id.equals(s.kwami) || player.level() == null) {
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

	/**
	 * When base texel ({@code u}, {@code v}) (64x64; overlay texels map to the base texel under them) turns into the
	 * suit, 0..1 -- by height from the Necklace at the throat (model y 8) up and down, with a little grain; 2 = unused.
	 */
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
		float dist = Math.abs(h - 8f) / (h < 8f ? 8f : 24f);
		// the head fills a little slower than the body: the light reaches the crown with the boots
		float t = h < 8f ? dist : dist;
		return Mth.clamp(0.03f + 0.85f * t + 0.1f * hash(u, v), 0f, 0.995f);
	}

	private static float hash(int x, int y) {
		int h = x * 374761393 + y * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFF) / 65535f;
	}

	/** The suit texture for this much cover (a reveal frame while it is part-way). */
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

	/** Frame k shows every texel whose order is at most k / FRAMES; the newest ones glow orange-white (the light). */
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
			ProjectHeroMod.LOGGER.warn("[rena_rouge] could not read the suit for the reveal", e);
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
			out[k] = mc.getTextureManager().register("projecthero_rena_rouge_" + name + "_" + k, new DynamicTexture(img));
		}
		src.close();
		return out;
	}

	/** The leading edge of the light: hot orange-white fading into the texel ({@code t} 0 = brightest). NativeImage is ABGR. */
	private static int light(int abgr, float t) {
		int a = (abgr >>> 24) & 0xFF, b = (abgr >>> 16) & 0xFF, g = (abgr >>> 8) & 0xFF, r = abgr & 0xFF;
		float k = 1f - t;
		r = (int) Mth.lerp(k, r, 255);
		g = (int) Mth.lerp(k, g, 200);
		b = (int) Mth.lerp(k, b, 110);
		return (a << 24) | (b << 16) | (g << 8) | r;
	}

	// ---------------------------------------------------------------- drawing

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static PlayerModel<LivingEntity> posed(HumanoidModel<?> parent, boolean slim) {
		PlayerModel<LivingEntity> m = model(slim);
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

	/** The suit over {@code parent} ({@code alpha} 0..1, {@code tint} an RGB multiplier). */
	public static void renderSuit(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, boolean slim, float cover, float alpha,
			int tint) {
		if (cover <= 0f) {
			return;
		}
		PlayerModel<LivingEntity> m = posed(parent, slim);
		int a = Math.round(255 * Mth.clamp(alpha, 0f, 1f));
		int color = FastColor.ARGB32.color(a, (tint >> 16) & 0xFF, (tint >> 8) & 0xFF, tint & 0xFF);
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(texture(slim, cover))), light, OverlayTexture.NO_OVERLAY, color);
	}

	/**
	 * The tail on the lower back and the ears on the head of {@code parent}. {@code grow} 0..1 scales them in (the end of
	 * the transformation); {@code swing} / {@code speed} drive the sway.
	 */
	public static void renderFoxParts(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, float grow, float ageInTicks,
			float walk, float alpha, int tint) {
		if (grow <= 0.01f) {
			return;
		}
		ModelPart f = fox();
		int a = Math.round(255 * Mth.clamp(alpha, 0f, 1f));
		int color = FastColor.ARGB32.color(a, (tint >> 16) & 0xFF, (tint >> 8) & 0xFF, tint & 0xFF);
		var vc = buffers.getBuffer(RenderType.entityTranslucent(FOX));
		// the tail: from the small of the back, hanging back and down, curling up at the end and swaying
		pose.pushPose();
		parent.body.translateAndRotate(pose);
		pose.translate(0f, 10.5f / 16f, 1.9f / 16f);
		pose.scale(grow, grow, grow);
		float sway = Mth.sin(ageInTicks * 0.11f) * 0.22f + Mth.sin(ageInTicks * 0.6f) * 0.12f * walk;
		ModelPart seg = f.getChild("tail0");
		seg.xRot = 1.15f - 0.3f * walk;
		seg.yRot = 0f;
		seg.zRot = sway;
		for (int k = 1; k < 5; k++) {
			seg = seg.getChild("tail" + k);
			seg.xRot = -0.26f - 0.04f * k;
			seg.yRot = 0f;
			seg.zRot = sway * (0.6f + 0.15f * k);
		}
		f.getChild("tail0").render(pose, vc, light, OverlayTexture.NO_OVERLAY, color);
		pose.popPose();
		if (drawEars) {
			pose.pushPose();
			parent.head.translateAndRotate(pose);
			for (int side = -1; side <= 1; side += 2) {
				ModelPart ear = f.getChild(side < 0 ? "ear_r" : "ear_l");
				ear.x = side * 2.6f;
				ear.y = -8.3f;
				ear.z = 0.2f;
				ear.xRot = -0.08f;
				ear.yRot = 0f;
				ear.zRot = side * 0.28f + side * 0.04f * Mth.sin(ageInTicks * 0.2f);
				pose.pushPose();
				pose.translate(ear.x / 16f, ear.y / 16f, ear.z / 16f);
				pose.scale(grow, grow, grow);
				pose.translate(-ear.x / 16f, -ear.y / 16f, -ear.z / 16f);
				ear.render(pose, vc, light, OverlayTexture.NO_OVERLAY, color);
				pose.popPose();
			}
			pose.popPose();
		}
	}

	/** Where the Flute is. */
	public enum FluteAt {
		NONE, BACK, HAND, LIPS
	}

	/** The Flute on {@code parent}'s back, in its right hand, or at its lips (played sideways, to the right). */
	public static void renderFlute(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, FluteAt at, boolean slim, float alpha) {
		if (at == FluteAt.NONE) {
			return;
		}
		pose.pushPose();
		switch (at) {
			case BACK -> {
				parent.body.translateAndRotate(pose);
				pose.translate(0f, 5.5f / 16f, 2.9f / 16f);
				pose.mulPose(Axis.ZP.rotation(0.75f));
			}
			case HAND -> {
				parent.rightArm.translateAndRotate(pose);
				pose.translate((slim ? -0.5f : -1f) / 16f, 10f / 16f, 0f);
				pose.mulPose(Axis.XP.rotation(-(float) Math.PI / 2f));
				pose.translate(0f, -1f / 16f, 0f);
			}
			case LIPS -> {
				parent.head.translateAndRotate(pose);
				pose.translate(-10f / 16f, -1.8f / 16f, -5.2f / 16f);
				pose.mulPose(Axis.ZP.rotation((float) Math.PI / 2f));
			}
			default -> {
			}
		}
		renderFluteModel(pose, buffers, light, alpha);
		pose.popPose();
	}

	/** The bare Flute model at the pose's origin (its own Y axis along the flute). */
	public static void renderFluteModel(PoseStack pose, MultiBufferSource buffers, int light, float alpha) {
		int a = Math.round(255 * Mth.clamp(alpha, 0f, 1f));
		flute().render(pose, buffers.getBuffer(RenderType.entityTranslucent(FLUTE)), light, OverlayTexture.NO_OVERLAY,
				FastColor.ARGB32.color(a, 255, 255, 255));
	}

	/** First person: the suit sleeve over the bare arm vanilla just drew. */
	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, ModelPart arm, boolean right, boolean slim, float cover) {
		PlayerModel<LivingEntity> m = model(slim);
		ModelPart a = right ? m.rightArm : m.leftArm;
		ModelPart sleeve = right ? m.rightSleeve : m.leftSleeve;
		a.copyFrom(arm);
		sleeve.copyFrom(arm);
		a.visible = true;
		sleeve.visible = true;
		ResourceLocation tex = texture(slim, cover);
		a.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
	}
}
