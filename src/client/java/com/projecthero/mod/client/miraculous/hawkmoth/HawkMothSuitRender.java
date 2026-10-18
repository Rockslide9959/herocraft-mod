package com.projecthero.mod.client.miraculous.hawkmoth;

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
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: Hawk Moth's suit over a player -- the user's chosen NovaSkin skin (post 5192383343) cut down to the suit
 * ({@code scratchpad/make_suit_skins.js}: the silver mask covers the head, the eyes and mouth are see-through, the gaps
 * filled), in a wide (Steve) and a slim (Alex) cut picked per wearer. Drawn as a second player model a hair larger than
 * the wearer's (the Nova technique: base +0.04, outer +0.29) so his own face shows through the mask's eye holes; the mask
 * itself sits at +0.08 (v0.16.6: was +0.62 outside the hat layer, which looked bulky), and the wearer's own hat layer is
 * hidden under it as the mask closes.
 *
 * <p><b>The transformation</b>: a purple wave washes out from the brooch on his chest -- every body texel turns into the
 * suit in order of how far it sits from the brooch (up the chest, down the arms and legs), a bright violet rim on the
 * newest ones; the mask closes last, from the neck up. Detransforming runs it backwards. The wearer's jacket, sleeves
 * and trousers go texel by texel in step ({@link OverlayStrips}).
 */
public final class HawkMothSuitRender {
	public static final ResourceLocation WIDE = ProjectHeroMod.id("textures/entity/miraculous/hawk_moth/suit_wide.png");
	public static final ResourceLocation SLIM = ProjectHeroMod.id("textures/entity/miraculous/hawk_moth/suit_slim.png");
	private static final int FRAMES = 32;
	/** Model pixels from the top of the head to the brooch. */
	private static final float BROOCH = 9.5f;
	/** The body is done by this point of the change; the mask takes the rest. */
	private static final float BODY_END = 0.72f;
	/** v0.16.6: how far the silver mask sits off the head (a hair over the base layer, not a bulky shell). */
	private static final float HELMET_INFLATE = 0.08f;

	private static PlayerModel<LivingEntity> wide;
	private static PlayerModel<LivingEntity> slim;
	private static ResourceLocation[] wideFrames;
	private static ResourceLocation[] slimFrames;
	private static float[] orders;

	private HawkMothSuitRender() {
	}

	private static PlayerModel<LivingEntity> build(boolean slimArms) {
		MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(0.04f), slimArms);
		// v0.16.6 (user: the helmet was bulky): the mask sits right on the face (+0.08, was +0.62 outside the hat layer); the
		// wearer's own hat layer is hidden under it (HawkMothLayer), so only the see-through eyes and mouth show his face
		mesh.getRoot().addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4f, -8f, -4f, 8f, 8f, 8f,
				new CubeDeformation(HELMET_INFLATE)), PartPose.offset(0f, 0f, 0f));
		return new PlayerModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot(), slimArms);
	}

	public static boolean isSlim(Player player) {
		return player instanceof AbstractClientPlayer acp && acp.getSkin().model() == PlayerSkin.Model.SLIM;
	}

	public static PlayerModel<LivingEntity> model(boolean slimArms) {
		if (slimArms) {
			if (slim == null) {
				slim = build(true);
			}
			return slim;
		}
		if (wide == null) {
			wide = build(false);
		}
		return wide;
	}

	// ---------------------------------------------------------------- the change

	/** How much of the suit is on, 0..1 (1 = all of it; the transformation / detransformation run it up / down). */
	public static float progress(Player player, float partial) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null || !s.hasPower || !Kwami.NOOROO.id.equals(s.kwami) || player.level() == null) {
			return 0f;
		}
		float total = MiraculousConfig.TRANSFORM_TICKS;
		float age = player.level().getGameTime() - s.formChangeAt + partial;
		if (age < 0f && age > -40f) {
			age = 0f; // the client clock can trail the server by a few ticks
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

	public static boolean hidesSkinOverlay(Player player) {
		return progress(player, 0f) > 0f;
	}

	/** When base texel ({@code u}, {@code v}) (64x64; overlay texels map to the base under them) turns into the suit; 2 = unused. */
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
			u -= 32; // hat -> head
		} else if (v >= 32 && v < 48) {
			v -= 16; // jacket / right sleeve / right trousers -> body / right arm / right leg
		} else if (v >= 48 && u < 16) {
			u += 16; // left trousers -> left leg
		} else if (v >= 48 && u >= 48) {
			u -= 16; // left sleeve -> left arm
		}
		float h = OverlayStrips.height(u, v);
		if (h < 0f) {
			return 2f;
		}
		float n = hash(u, v);
		if (v < 16) {
			// the mask closes from the neck up, last
			return Math.min(0.99f, BODY_END + (1f - BODY_END) * (0.85f * (8f - Math.min(8f, h)) / 8f + 0.12f * n));
		}
		float dist = Math.abs(h - BROOCH) / (32f - BROOCH);
		return Math.min(BODY_END - 0.01f, BODY_END * 0.92f * dist + 0.06f * n);
	}

	private static float hash(int x, int y) {
		int h = x * 374761393 + y * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFF) / 65535f;
	}

	// ---------------------------------------------------------------- drawing

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

	public static void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, HumanoidModel<?> parent,
			float cover) {
		boolean slimArms = isSlim(player);
		PlayerModel<LivingEntity> m = posed(parent, slimArms);
		ResourceLocation tex = cover >= 0.999f ? (slimArms ? SLIM : WIDE) : frame(slimArms, cover);
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
	}

	/** First person: the suit's sleeve over the arm vanilla just drew. */
	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, Player player, ModelPart arm, boolean right, float cover) {
		boolean slimArms = isSlim(player);
		PlayerModel<LivingEntity> m = model(slimArms);
		ModelPart a = right ? m.rightArm : m.leftArm;
		ModelPart sleeve = right ? m.rightSleeve : m.leftSleeve;
		a.copyFrom(arm);
		sleeve.copyFrom(arm);
		a.visible = true;
		sleeve.visible = true;
		ResourceLocation tex = cover >= 0.999f ? (slimArms ? SLIM : WIDE) : frame(slimArms, cover);
		a.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
	}

	// ---------------------------------------------------------------- the frames of the change

	private static ResourceLocation frame(boolean slimArms, float cover) {
		if (slimArms ? slimFrames == null : wideFrames == null) {
			ResourceLocation[] f = buildFrames(slimArms ? SLIM : WIDE, slimArms ? "slim" : "wide");
			if (slimArms) {
				slimFrames = f;
			} else {
				wideFrames = f;
			}
		}
		ResourceLocation[] f = slimArms ? slimFrames : wideFrames;
		if (f.length == 0) {
			return slimArms ? SLIM : WIDE;
		}
		return f[Math.max(0, Math.min(FRAMES, Math.round(cover * FRAMES)))];
	}

	/** Frame {@code k} shows every texel whose {@link #order} is at most {@code k / FRAMES}; the newest ones glow violet. */
	private static ResourceLocation[] buildFrames(ResourceLocation skin, String tag) {
		Minecraft mc = Minecraft.getInstance();
		Optional<Resource> res = mc.getResourceManager().getResource(skin);
		if (res.isEmpty()) {
			return new ResourceLocation[0];
		}
		NativeImage src;
		try (InputStream in = res.get().open()) {
			src = NativeImage.read(in);
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[hawk_moth] could not read the suit for the change", e);
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
						img.setPixelRGBA(x, y, glow(px));
					} else {
						img.setPixelRGBA(x, y, px);
					}
				}
			}
			out[k] = mc.getTextureManager().register("projecthero_hawk_moth_change_" + tag + "_" + k, new DynamicTexture(img));
		}
		src.close();
		return out;
	}

	/** The wave's rim: the texel washed toward bright violet (NativeImage pixels are ABGR). */
	private static int glow(int abgr) {
		int a = (abgr >>> 24) & 0xFF, b = (abgr >>> 16) & 0xFF, g = (abgr >>> 8) & 0xFF, r = abgr & 0xFF;
		r = (r + 200) / 2;
		g = (g + 110) / 2;
		b = (b + 255) / 2;
		return (a << 24) | (b << 16) | (g << 8) | r;
	}
}
