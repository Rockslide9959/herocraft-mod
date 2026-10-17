package com.projecthero.mod.client.miraculous.catnoir;

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
import com.projecthero.mod.miraculous.catnoir.CatNoir;
import com.projecthero.mod.miraculous.catnoir.CatNoirConfig;
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
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: Cat Noir drawn over a player -- the suit cut out of the user's chosen skin (minecraftskins.com 23799210
 * "Chat Noir"; {@code scratchpad/make_suit_skins.js} made Adrien's face, skin and hair transparent, leaving the black
 * bodysuit, belt, bell, gloves, boots and the mask with its green lenses) on a second, wide player model a hair larger
 * than the wearer's (the Nova uniform technique), so the player's OWN face, hair and any bare skin show through. While
 * it shows, the wearer's jacket / sleeve / trouser layers are hidden but their hat layer (their own hair) is kept
 * ({@code CatNoirLayer} re-draws it). On top: real 3D parts -- cat ears on the head, the belt-tail swaying behind, the
 * golden bell at the collar -- and the Baton. (The Ring is the core's.)
 *
 * <p><b>The transformation</b> ({@link #cover}): the suit spreads texel by texel from the Ring on the right hand -- up the
 * arm, over the chest, out to the head, the other arm and the legs -- over {@link MiraculousConfig#TRANSFORM_TICKS}, the
 * newest texels flashing Plagg green; the ears, tail and bell pop on at the end. Detransforming runs it backwards.
 * The wearer's own second layer goes texel by texel in step ({@link OverlayStrips} with {@link #order}).
 *
 * <p><b>CATACLYSM</b>: while charged, the main hand is wrapped in a dark mottled skin and a purple energy swirl (third
 * and first person); the particles are {@code CatNoirClient}'s.
 */
public final class CatNoirRender {
	/** The suit for wide-armed (Steve) and slim-armed (Alex) wearers, so its arms always line up with the player's own. */
	public static final ResourceLocation SKIN_WIDE = ProjectHeroMod.id("textures/entity/miraculous/cat_noir/suit_wide.png");
	public static final ResourceLocation SKIN_SLIM = ProjectHeroMod.id("textures/entity/miraculous/cat_noir/suit_slim.png");
	public static final ResourceLocation PARTS = ProjectHeroMod.id("textures/entity/miraculous/cat_noir/parts.png");
	public static final ResourceLocation CATACLYSM_DARK = ProjectHeroMod.id("textures/entity/miraculous/cat_noir/cataclysm_dark.png");
	public static final ResourceLocation CATACLYSM_SWIRL = ProjectHeroMod.id("textures/entity/miraculous/cat_noir/cataclysm_swirl.png");
	private static final int FRAMES = 48;
	/** Where the ring sits (model pixels, rest pose): the bottom of the right hand. */
	private static final float RING_X = -6f, RING_Y = 12f, RING_Z = 0f;
	private static final float MAX_DIST = 21.5f;
	/** The ears / tail / bell pop on past this much of the change. */
	private static final float PARTS_AT = 0.9f;

	private static PlayerModel<LivingEntity> model;
	private static PlayerModel<LivingEntity> slimModel;
	private static PlayerModel<LivingEntity> handModel;
	private static ModelPart parts;
	private static ResourceLocation[] frames;
	private static ResourceLocation[] slimFrames;
	private static float[] orders;

	private CatNoirRender() {
	}

	// ---------------------------------------------------------------- state

	/** How much of the suit shows, 0..1 (the change runs both ways off the core's {@code formChangeAt}). */
	public static float cover(Player player, float partial) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null || !s.hasPower || !Kwami.PLAGG.id.equals(s.kwami) || player.level() == null) {
			return 0f;
		}
		float age = player.level().getGameTime() - s.formChangeAt + partial;
		float total = MiraculousConfig.TRANSFORM_TICKS;
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
		return cover(player, 0f) > 0f;
	}

	// ---------------------------------------------------------------- the order of the change

	/** When base texel ({@code u}, {@code v}) (overlay texels map to the base texel under them) turns into the suit, 0..1; 2 = unused. */
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

	/** Base cubes: u, v, width, height, depth and the rest-pose min corner (model pixels, y down). */
	private static final float[][] CUBES = {
			{ 0, 0, 8, 8, 8, -4, -8, -4 }, // head
			{ 16, 16, 8, 12, 4, -4, 0, -2 }, // body
			{ 40, 16, 4, 12, 4, -8, 0, -2 }, // right arm
			{ 32, 48, 4, 12, 4, 4, 0, -2 }, // left arm
			{ 0, 16, 4, 12, 4, -4, 12, -2 }, // right leg
			{ 16, 48, 4, 12, 4, 0, 12, -2 } }; // left leg

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
		float[] p = position(u, v);
		if (p == null) {
			return 2f;
		}
		float dx = p[0] - RING_X, dy = p[1] - RING_Y, dz = p[2] - RING_Z;
		float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / MAX_DIST;
		float n = 0.06f * (hash(u, v) - 0.5f);
		return Mth.clamp(0.02f + 0.9f * d + n, 0.01f, 0.97f);
	}

	/** The rest-pose position (model pixels) of base texel ({@code u}, {@code v}), or null for an unused texel. */
	static float[] position(int u, int v) {
		for (float[] c : CUBES) {
			int u0 = (int) c[0], v0 = (int) c[1], w = (int) c[2], h = (int) c[3], d = (int) c[4];
			float minX = c[5], minY = c[6], minZ = c[7], maxX = minX + w, maxZ = minZ + d;
			if (u < u0 || u >= u0 + 2 * (d + w) || v < v0 || v >= v0 + d + h) {
				continue;
			}
			if (v < v0 + d) {
				if (u >= u0 + d && u < u0 + d + w) {
					return new float[] { minX + (u - u0 - d) + 0.5f, minY, maxZ - (v - v0) - 0.5f };
				}
				if (u >= u0 + d + w && u < u0 + d + 2 * w) {
					return new float[] { minX + (u - u0 - d - w) + 0.5f, minY + h, maxZ - (v - v0) - 0.5f };
				}
				return null;
			}
			float y = minY + (v - v0 - d) + 0.5f;
			int k = u - u0;
			if (k < d) {
				return new float[] { minX, y, maxZ - k - 0.5f };
			}
			if (k < d + w) {
				return new float[] { minX + (k - d) + 0.5f, y, minZ };
			}
			if (k < 2 * d + w) {
				return new float[] { maxX, y, minZ + (k - d - w) + 0.5f };
			}
			return new float[] { maxX - (k - 2 * d - w) - 0.5f, y, maxZ };
		}
		return null;
	}

	private static float hash(int x, int y) {
		int h = x * 374761393 + y * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFF) / 65535f;
	}

	// ---------------------------------------------------------------- the models

	/** Does this wearer have slim (3-pixel) arms? */
	public static boolean slim(Player player) {
		return player instanceof net.minecraft.client.player.AbstractClientPlayer acp
				&& acp.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
	}

	public static ResourceLocation skin(boolean slim) {
		return slim ? SKIN_SLIM : SKIN_WIDE;
	}

	public static PlayerModel<LivingEntity> model(boolean slim) {
		if (slim) {
			if (slimModel == null) {
				MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(0.04f), true);
				slimModel = new PlayerModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot(), true);
			}
			return slimModel;
		}
		if (model == null) {
			MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(0.04f), false);
			model = new PlayerModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot(), false);
		}
		return model;
	}

	/** The CATACLYSM wrap: a player model a little larger again (only an arm of it is drawn). */
	private static PlayerModel<LivingEntity> handModel() {
		if (handModel == null) {
			MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(0.32f), false);
			handModel = new PlayerModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot(), false);
		}
		return handModel;
	}

	/**
	 * The 3D parts (texture {@code parts.png}, 32x32): two ears (head space), the belt-tail (four hanging segments and a
	 * silver tip, body space) and the bell (body space).
	 */
	static ModelPart parts() {
		if (parts == null) {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			for (int side = -1; side <= 1; side += 2) {
				root.addOrReplaceChild(side < 0 ? "ear_right" : "ear_left", CubeListBuilder.create()
						.texOffs(0, 0).addBox(-1.5f, -2f, -0.5f, 3, 2, 1)
						.texOffs(0, 4).addBox(-1f, -3f, -0.5f, 2, 1, 1)
						.texOffs(0, 7).addBox(-0.5f, -4f, -0.5f, 1, 1, 1),
						PartPose.offsetAndRotation(side * 2.6f, -8.3f, -0.8f, 0f, 0f, side * 0.22f));
			}
			PartDefinition parent = root;
			for (int i = 0; i < 4; i++) {
				CubeListBuilder seg = CubeListBuilder.create().texOffs(0, 16 + i * 4).addBox(-0.5f, 0f, -0.5f, 1, 4, 1);
				if (i == 3) {
					seg.texOffs(16, 0).addBox(-0.75f, 3.5f, -0.75f, 1.5f, 1.5f, 1.5f);
				}
				parent = parent.addOrReplaceChild("tail" + i, seg, i == 0 ? PartPose.offset(0.5f, 10.6f, 2.4f) : PartPose.offset(0f, 3.8f, 0f));
			}
			root.addOrReplaceChild("bell", CubeListBuilder.create().texOffs(16, 16).addBox(-1.4f, 0.4f, -3.25f, 1.8f, 1.8f, 1.1f), PartPose.ZERO);
			parts = LayerDefinition.create(mesh, 32, 32).bakeRoot();
		}
		return parts;
	}

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

	// ---------------------------------------------------------------- drawing

	/** The suit, its parts and the CATACLYSM hand (third person, every viewer). */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, Player player, HumanoidModel<?> parent, float cover,
			float limbSwing, float limbSwingAmount, float ageInTicks, float partial) {
		boolean slim = slim(player);
		PlayerModel<LivingEntity> m = posed(parent, slim);
		ResourceLocation tex = cover >= 0.999f ? skin(slim) : frame(slim, cover);
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		if (cover >= PARTS_AT) {
			renderParts(pose, buffers, light, m, limbSwing, limbSwingAmount, ageInTicks, (cover - PARTS_AT) / (1f - PARTS_AT));
		}
		if (CatNoir.charged(player)) {
			boolean right = player.getMainArm() == HumanoidArm.RIGHT;
			renderCataclysmArm(pose, buffers, right ? m.rightArm : m.leftArm, right, ageInTicks);
		}
	}

	private static void renderParts(PoseStack pose, MultiBufferSource buffers, int light, PlayerModel<LivingEntity> m, float limbSwing,
			float limbSwingAmount, float age, float grow) {
		ModelPart p = parts();
		var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(PARTS));
		float s = Mth.clamp(grow, 0f, 1f);
		// the ears ride the head
		pose.pushPose();
		m.head.translateAndRotate(pose);
		if (s < 1f) {
			pose.translate(0, -0.5f, 0);
			pose.scale(s, s, s);
			pose.translate(0, 0.5f, 0);
		}
		ModelPart er = p.getChild("ear_right"), el = p.getChild("ear_left");
		float twitch = Mth.sin(age * 0.05f) > 0.97f ? 0.15f * Mth.sin(age * 1.7f) : 0f; // the odd flick
		er.zRot = -0.22f - twitch;
		el.zRot = 0.22f + twitch * 0.5f;
		er.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
		el.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
		// the tail and the bell ride the body
		pose.pushPose();
		m.body.translateAndRotate(pose);
		ModelPart t0 = p.getChild("tail0");
		ModelPart seg = t0;
		float walk = Mth.clamp(limbSwingAmount, 0f, 1f);
		for (int i = 0; i < 4; i++) {
			float sway = Mth.sin(age * 0.09f + i * 0.7f) * (0.07f + 0.04f * i) + Mth.sin(limbSwing * 0.6662f + i * 0.5f) * walk * 0.25f;
			seg.xRot = (i == 0 ? 0.32f + walk * 0.35f - m.body.xRot * 0.5f : 0.06f + walk * 0.08f) + 0.03f * Mth.sin(age * 0.07f + i);
			seg.zRot = sway;
			seg.yRot = 0f;
			if (i < 3) {
				seg = seg.getChild("tail" + (i + 1));
			}
		}
		if (s < 1f) {
			pose.scale(1f, s, 1f);
		}
		t0.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
		p.getChild("bell").render(pose, vc, light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}

	/** CATACLYSM: the dark mottled wrap and the purple swirl over one arm (in that arm's current pose). */
	public static void renderCataclysmArm(PoseStack pose, MultiBufferSource buffers, ModelPart posedArm, boolean right, float ageInTicks) {
		PlayerModel<LivingEntity> hm = handModel();
		ModelPart arm = right ? hm.rightArm : hm.leftArm;
		arm.copyFrom(posedArm);
		arm.visible = true;
		float pulse = 0.75f + 0.25f * Mth.sin(ageInTicks * 0.6f);
		arm.render(pose, buffers.getBuffer(RenderType.entityTranslucent(CATACLYSM_DARK)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
				FastColor.ARGB32.color(Math.round(220 * pulse), 255, 255, 255));
		float t = ageInTicks * 0.02f;
		arm.render(pose, buffers.getBuffer(RenderType.energySwirl(CATACLYSM_SWIRL, t % 1f, (t * 0.7f) % 1f)), LightTexture.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color(255, Math.round(150 * pulse), Math.round(60 * pulse), Math.round(200 * pulse)));
	}

	/** First person: the suit sleeve over the arm vanilla just drew. */
	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, ModelPart arm, boolean right, float cover,
			boolean slim) {
		PlayerModel<LivingEntity> m = model(slim);
		ModelPart a = right ? m.rightArm : m.leftArm;
		ModelPart sleeve = right ? m.rightSleeve : m.leftSleeve;
		a.copyFrom(arm);
		sleeve.copyFrom(arm);
		a.visible = true;
		sleeve.visible = true;
		ResourceLocation tex = cover >= 0.999f ? skin(slim) : frame(slim, cover);
		a.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY);
	}

	// ---------------------------------------------------------------- the baton on the body

	/** Where the baton is this frame. */
	public enum BatonAt {
		NONE, BACK, HAND, SPIN
	}

	/** The baton's place and (for HAND / SPIN) how far it is extended, 0..1. */
	public record Baton(BatonAt at, float extension) {
	}

	public static Baton baton(Player player, float partial) {
		if (!CatNoir.active(player) || cover(player, partial) < 0.999f || CatNoir.thrownId(player) >= 0) {
			return new Baton(BatonAt.NONE, 0f);
		}
		MiraculousState s = Miraculous.peek(player);
		float age = player.level().getGameTime() - s.animStart + partial;
		int len = CatNoirPose.length(s.animId);
		boolean running = age >= 0f && age < len;
		if (running) {
			switch (s.animId) {
				case CatNoir.ANIM_EXTEND, CatNoir.ANIM_VAULT -> {
					return new Baton(BatonAt.NONE, 0f); // drawn in the world (CatNoirWorldFx)
				}
				case CatNoir.ANIM_STRIKE, CatNoir.ANIM_SWEEP, CatNoir.ANIM_POUNCE, CatNoir.ANIM_THROW -> {
					return new Baton(BatonAt.HAND, ramp(age, len, 2.5f, 3f));
				}
				case CatNoir.ANIM_SPIN -> {
					return new Baton(BatonAt.SPIN, ramp(age, len, 3f, 4f));
				}
				case CatNoir.ANIM_CATCH -> {
					return new Baton(BatonAt.HAND, 1f - Mth.clamp(age / len, 0f, 1f));
				}
				default -> {
				}
			}
		}
		return new Baton(BatonAt.BACK, 0f);
	}

	/** 0 -> 1 over {@code in} ticks, 1, then 1 -> 0 over the last {@code out}. */
	private static float ramp(float age, int len, float in, float out) {
		return Mth.clamp(Math.min(age / in, (len - age) / out), 0f, 1f);
	}

	/** The baton's length (each side) for an extension 0..1 in the hand: a staff gripped a third of the way up. */
	public static final float HAND_FRONT = 1.25f, HAND_BACK = 0.55f, SPIN_SIDE = 1.0f;

	/** Third person: the baton on the lower back, in the main hand, or spinning in front of it. */
	public static void renderBaton(PoseStack pose, MultiBufferSource buffers, int light, Player player, HumanoidModel<?> m, float ageInTicks,
			float partial) {
		Baton b = baton(player, partial);
		if (b.at() == BatonAt.NONE) {
			return;
		}
		boolean rotted = CatNoir.rotted(player);
		pose.pushPose();
		if (b.at() == BatonAt.BACK) {
			m.body.translateAndRotate(pose);
			pose.translate(0f, 10.2f / 16f, 2.7f / 16f);
			pose.mulPose(Axis.ZP.rotationDegrees(62f));
			CatNoirBaton.renderCollapsed(pose, buffers, light, rotted);
		} else {
			boolean right = player.getMainArm() == HumanoidArm.RIGHT;
			ModelPart arm = right ? m.rightArm : m.leftArm;
			arm.translateAndRotate(pose);
			pose.translate((right ? -1f : 1f) / 16f, 10.5f / 16f, 0f);
			float e = b.extension();
			if (b.at() == BatonAt.SPIN) {
				// gripped in the middle, whirling round the line of the arm
				pose.mulPose(Axis.YP.rotationDegrees((ageInTicks + partial) * 47f));
				pose.mulPose(Axis.XP.rotationDegrees(-90f));
				float side = CatNoirBaton.COLLAPSED + (SPIN_SIDE - CatNoirBaton.COLLAPSED) * e;
				CatNoirBaton.render(pose, buffers, light, side, SPIN_SIDE, side, SPIN_SIDE, rotted);
			} else {
				// out of the fist half way between the line of the arm and straight across it: forward-and-up with the arm
				// raised, so the swings cut and the smash comes down in front
				pose.mulPose(Axis.XP.rotationDegrees(-45f));
				float f = CatNoirBaton.COLLAPSED + (HAND_FRONT - CatNoirBaton.COLLAPSED) * e;
				float k = CatNoirBaton.COLLAPSED + (HAND_BACK - CatNoirBaton.COLLAPSED) * e;
				CatNoirBaton.render(pose, buffers, light, f, HAND_FRONT, k, HAND_BACK, rotted);
			}
		}
		pose.popPose();
	}

	// ---------------------------------------------------------------- the frames of the change

	private static ResourceLocation frame(boolean slim, float cover) {
		if (slim && slimFrames == null) {
			slimFrames = build(SKIN_SLIM, "slim");
		} else if (!slim && frames == null) {
			frames = build(SKIN_WIDE, "wide");
		}
		ResourceLocation[] set = slim ? slimFrames : frames;
		if (set.length == 0) {
			return skin(slim);
		}
		int k = Math.max(0, Math.min(FRAMES, Math.round(cover * FRAMES)));
		return set[k];
	}

	/** Frame {@code k} shows every texel whose {@link #order} is at most {@code k / FRAMES}; the newest flash Plagg green. */
	private static ResourceLocation[] build(ResourceLocation skin, String tag) {
		Minecraft mc = Minecraft.getInstance();
		Optional<Resource> res = mc.getResourceManager().getResource(skin);
		if (res.isEmpty()) {
			return new ResourceLocation[0];
		}
		NativeImage src;
		try (InputStream in = res.get().open()) {
			src = NativeImage.read(in);
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[cat_noir] could not read the suit for the change", e);
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
						continue;
					}
					float age = line - o;
					if (k < FRAMES && age < 0.05f && ((px >>> 24) & 0xFF) > 0) {
						img.setPixelRGBA(x, y, green(px, age / 0.05f));
					} else {
						img.setPixelRGBA(x, y, px);
					}
				}
			}
			frames[k] = mc.getTextureManager().register("projecthero_cat_noir_change_" + tag + "_" + k, new DynamicTexture(img));
		}
		src.close();
		return frames;
	}

	/** The magic's edge: the texel washed toward Plagg's green, fading as it settles (NativeImage pixels are ABGR). */
	private static int green(int abgr, float settle) {
		int a = (abgr >>> 24) & 0xFF, b = (abgr >>> 16) & 0xFF, g = (abgr >>> 8) & 0xFF, r = abgr & 0xFF;
		float w = 0.85f * (1f - settle);
		r = Math.round(r + (126 - r) * w);
		g = Math.round(g + (240 - g) * w);
		b = Math.round(b + (70 - b) * w);
		return (a << 24) | (b << 16) | (g << 8) | r;
	}

}
