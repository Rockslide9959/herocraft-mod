package com.projecthero.mod.client.miraculous.pegasus;

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
 * v0.16.6: Pegasus drawn over a player -- the user's suit skin (minecraftskins.com 15258841, "pegasus", slim arms; cut
 * to suit-only, his face and eyes transparent, its own black glasses taken off the hat layer; v0.16.7: made by
 * {@code scratchpad/suits/make_suit_skins.js}, which paints the suit's GOGGLES there instead -- turquoise lenses in a
 * light frame, a strap round the head -- and fills the faces the source left empty; the core's Horseshoe Glasses jewel
 * is hidden while the suit covers the face, see {@code MiraculousJewelLayer#GOGGLES_COVER}) in a slim and a
 * wide version picked by the wearer's skin model, drawn OVER the wearer's own skin: the cream mane hood, the dark suit
 * with its white stripes. Not bulky (user): the base layer sits {@link #BASE_INFLATE} over the body, the second layer at
 * exactly vanilla's own overlay offsets (+0.25, the head +0.5) -- the wearer's own second layer is hidden while the suit
 * shows, hat (hair) included, since the mane hood replaces it.
 *
 * <p>Also the Horseshoe: a real 3D golden horseshoe (seven arc segments and two heels round an open U) at his right hip,
 * in his right hand during a move, spinning when thrown.
 *
 * <p><b>The transformation</b> ({@link #cover}): over {@code MiraculousConfig.TRANSFORM_TICKS} the suit spreads texel by
 * texel from the glasses on his face out over the head and down to the boots, its leading edge a band of white-gold
 * light; the wearer's own second layer goes in step ({@link OverlayStrips}). Detransforming runs it backwards.
 */
public final class PegasusRender {
	public static final ResourceLocation SUIT = ProjectHeroMod.id("textures/entity/miraculous/pegasus/suit.png");
	public static final ResourceLocation SUIT_WIDE = ProjectHeroMod.id("textures/entity/miraculous/pegasus/suit_wide.png");
	public static final ResourceLocation SHOE = ProjectHeroMod.id("textures/entity/miraculous/pegasus/horseshoe.png");
	/** The base suit layer's inflation (model pixels): a hair over the body, never bulky. */
	public static final float BASE_INFLATE = 0.02f;

	private static final int FRAMES = 32;

	private static PlayerModel<LivingEntity> slimBase;
	private static PlayerModel<LivingEntity> wideBase;
	private static PlayerModel<LivingEntity> slimOver;
	private static PlayerModel<LivingEntity> wideOver;
	private static ModelPart shoe;
	private static ResourceLocation[] slimFrames;
	private static ResourceLocation[] wideFrames;

	private PegasusRender() {
	}

	// ---------------------------------------------------------------- the models

	/** The base-layer model ({@link #BASE_INFLATE}); its overlay parts are not drawn. */
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

	/** The second-layer model: vanilla's own offsets exactly (+0.25, hat +0.5); its base parts are not drawn. */
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

	/**
	 * The Horseshoe: an open U of seven 2x2x1.5 segments round a 3.4-pixel arc, two heel blocks at the open ends; centred
	 * on the origin, lying in the XY plane (the open end down -Y... +Y is the toe), 1.5 px thick along Z. Texture
	 * {@link #SHOE} 32x32.
	 */
	public static ModelPart shoe() {
		if (shoe == null) {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			int n = 7;
			float r = 3.4f;
			for (int k = 0; k < n; k++) {
				// from -115 degrees round over the top to +115 (0 = straight up)
				float a = (float) Math.toRadians(-115 + 230.0 * k / (n - 1));
				float x = Mth.sin(a) * r, y = -Mth.cos(a) * r;
				root.addOrReplaceChild("seg" + k, CubeListBuilder.create().texOffs(0, k % 2 == 0 ? 8 : 0).addBox(-1.1f, -1f, -0.75f, 2.2f, 2f, 1.5f),
						PartPose.offsetAndRotation(x, y, 0f, 0f, 0f, a));
			}
			for (int side = -1; side <= 1; side += 2) {
				float a = (float) Math.toRadians(side * 115);
				root.addOrReplaceChild(side < 0 ? "heel_r" : "heel_l", CubeListBuilder.create().texOffs(0, 16).addBox(-1.2f, -0.8f, -0.9f, 2.4f, 1.6f, 1.8f),
						PartPose.offsetAndRotation(Mth.sin(a) * (r + 0.2f), -Mth.cos(a) * (r + 0.2f) + 0.6f, 0f, 0f, 0f, 0f));
			}
			shoe = LayerDefinition.create(mesh, 32, 32).bakeRoot();
		}
		return shoe;
	}

	// ---------------------------------------------------------------- the reveal

	/** How much of the suit shows on {@code player} now, 0..1. */
	public static float cover(Player player, float partial) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null || !s.hasPower || !Kwami.KAALKI.id.equals(s.kwami) || player.level() == null) {
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
	 * When base texel ({@code u}, {@code v}) turns into the suit, 0..1 -- by height from the glasses on the face (model y
	 * 4) outwards, the head first, then down to the boots, with a little grain; 2 = unused.
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
		float t = Math.abs(h - 4f) / 28f;
		return Mth.clamp(0.03f + 0.85f * (float) Math.sqrt(t) + 0.1f * hash(u, v), 0f, 0.995f);
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

	/** Frame k shows every texel whose order is at most k / FRAMES; the newest ones glow white-gold. */
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
			ProjectHeroMod.LOGGER.warn("[pegasus] could not read the suit for the reveal", e);
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
			out[k] = mc.getTextureManager().register("projecthero_pegasus_" + name + "_" + k, new DynamicTexture(img));
		}
		src.close();
		return out;
	}

	/** The leading edge of the light: white-gold fading into the texel. NativeImage is ABGR. */
	private static int light(int abgr, float t) {
		int a = (abgr >>> 24) & 0xFF, b = (abgr >>> 16) & 0xFF, g = (abgr >>> 8) & 0xFF, r = abgr & 0xFF;
		float k = 1f - t;
		r = (int) Mth.lerp(k, r, 255);
		g = (int) Mth.lerp(k, g, 236);
		b = (int) Mth.lerp(k, b, 170);
		return (a << 24) | (b << 16) | (g << 8) | r;
	}

	// ---------------------------------------------------------------- drawing

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
		m.hat.visible = !baseParts;
		m.jacket.visible = !baseParts;
		m.rightSleeve.visible = !baseParts;
		m.leftSleeve.visible = !baseParts;
		m.rightPants.visible = !baseParts;
		m.leftPants.visible = !baseParts;
		return m;
	}

	/** The suit over {@code parent}: its base layer a hair over the body, its second layer at vanilla's offsets. */
	public static void renderSuit(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, boolean slim, float cover) {
		if (cover <= 0f) {
			return;
		}
		ResourceLocation tex = texture(slim, cover);
		int color = FastColor.ARGB32.color(255, 255, 255, 255);
		posed(base(slim), parent, true).renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY, color);
		posed(over(slim), parent, false).renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY, color);
	}

	/** Where the Horseshoe is. */
	public enum ShoeAt {
		NONE, BELT, HAND
	}

	/** The Horseshoe at {@code parent}'s right hip (clipped flat to the side) or in its right hand (held up, toe forward). */
	public static void renderShoe(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, ShoeAt at, boolean slim) {
		if (at == ShoeAt.NONE) {
			return;
		}
		pose.pushPose();
		if (at == ShoeAt.BELT) {
			parent.body.translateAndRotate(pose);
			pose.translate(-4.35f / 16f, 11.2f / 16f, 0.2f / 16f);
			pose.mulPose(Axis.YP.rotationDegrees(90f));
			pose.mulPose(Axis.ZP.rotationDegrees(180f));
			pose.scale(0.62f, 0.62f, 0.62f);
		} else {
			parent.rightArm.translateAndRotate(pose);
			pose.translate((slim ? -0.5f : -1f) / 16f, 10.6f / 16f, -0.6f / 16f);
			pose.mulPose(Axis.XP.rotationDegrees(-90f));
			pose.mulPose(Axis.YP.rotationDegrees(90f));
			pose.translate(0f, 3.0f / 16f, 0f);
			pose.scale(0.85f, 0.85f, 0.85f);
		}
		renderShoeModel(pose, buffers, light);
		pose.popPose();
	}

	/** The bare Horseshoe model at the pose's origin (lying in its XY plane, toe up +Y... see {@link #shoe}). */
	public static void renderShoeModel(PoseStack pose, MultiBufferSource buffers, int light) {
		shoe().render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(SHOE)), light, OverlayTexture.NO_OVERLAY);
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
}
