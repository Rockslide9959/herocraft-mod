package com.projecthero.mod.client.sentry;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.sentry.Sentry;
import com.projecthero.mod.sentry.SentryConfig;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.5 Sentry: the suit and the Void Form, drawn over the wearer's own skin (every viewer: the state is synced). Textures are
 * original ({@code scratchpad/gen_sentry_v0175.js}): the gold-and-blue Sentry suit with an original sun-burst chest emblem (the head
 * left alone: his own face and hair), and the Void Form -- a black body webbed with gold veins, black hair and black eyes, smoke on
 * the hat layer -- which covers the head too. Each has an emissive pass ({@code *_glow}): the emblem and trims shine, and from 50 Void
 * the glow flickers dark; the Void's veins pulse. The wearer's outer skin layer is hidden while either is on
 * ({@link #hidesSkinOverlay}); in Void Form the hat layer too ({@link #hidesHat}). Wide and slim arms follow the wearer's skin.
 */
public class SentrySuitLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final ResourceLocation SUIT = tex("sentry_suit");
	private static final ResourceLocation SUIT_SLIM = tex("sentry_suit_slim");
	private static final ResourceLocation SUIT_GLOW = tex("sentry_glow");
	private static final ResourceLocation SUIT_GLOW_SLIM = tex("sentry_glow_slim");
	private static final ResourceLocation VOID = tex("void_form");
	private static final ResourceLocation VOID_SLIM = tex("void_form_slim");
	private static final ResourceLocation VOID_GLOW = tex("void_glow");
	private static final ResourceLocation VOID_GLOW_SLIM = tex("void_glow_slim");

	private static PlayerModel<LivingEntity> wide;
	private static PlayerModel<LivingEntity> slim;

	public SentrySuitLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	private static ResourceLocation tex(String name) {
		return ProjectHeroMod.id("textures/entity/sentry/" + name + ".png");
	}

	private static PlayerModel<LivingEntity> model(boolean slimArms) {
		if (slimArms) {
			if (slim == null) {
				MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(0.05f), true);
				slim = new PlayerModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot(), true);
			}
			return slim;
		}
		if (wide == null) {
			MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(0.05f), false);
			wide = new PlayerModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot(), false);
		}
		return wide;
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !Sentry.hasPower(player)) {
			return;
		}
		boolean slimArms = player.getSkin().model() == PlayerSkin.Model.SLIM;
		boolean dark = Sentry.voidForm(player);
		PlayerModel<LivingEntity> m = posed(getParentModel(), slimArms, dark);
		ResourceLocation base = dark ? (slimArms ? VOID_SLIM : VOID) : (slimArms ? SUIT_SLIM : SUIT);
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(base)), light, OverlayTexture.NO_OVERLAY);
		int glow = glowColour(player, ageInTicks, dark);
		if (glow != 0) {
			ResourceLocation g = dark ? (slimArms ? VOID_GLOW_SLIM : VOID_GLOW) : (slimArms ? SUIT_GLOW_SLIM : SUIT_GLOW);
			m.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(g)), 0xF000F0, OverlayTexture.NO_OVERLAY, glow);
		}
	}

	/**
	 * The emissive pass's tint (0 = skip it this frame): the Sentry's glow steady, flickering dark from 50 Void (more often the higher it
	 * gets); the Void's veins pulse slowly.
	 */
	static int glowColour(Player player, float age, boolean dark) {
		if (dark) {
			float pulse = 0.55f + 0.45f * Mth.sin(age * 0.12f);
			int v = (int) (255 * pulse);
			return 0xFF000000 | (v << 16) | (v << 8) | v;
		}
		float vd = Sentry.voidLevel(player);
		if (vd >= SentryConfig.VOID_WHISPERS) {
			// a flicker: some frames the golden glow just isn't there
			float k = (vd - SentryConfig.VOID_WHISPERS) / (SentryConfig.VOID_MAX - SentryConfig.VOID_WHISPERS);
			int slot = (int) (age * 0.5f) + player.getId() * 7;
			int h = (slot * 1103515245 + 12345) >>> 16;
			if ((h % 100) < 20 + k * 45) {
				return 0;
			}
		}
		return 0xFFFFFFFF;
	}

	/** The suit's sleeve in first person (from {@code SuitFirstPersonArmMixin}), posed like the vanilla arm just drawn. */
	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm, boolean right) {
		if (player.isInvisible() || !Sentry.hasPower(player)) {
			return;
		}
		boolean slimArms = player.getSkin().model() == PlayerSkin.Model.SLIM;
		boolean dark = Sentry.voidForm(player);
		PlayerModel<LivingEntity> m = model(slimArms);
		ModelPart a = right ? m.rightArm : m.leftArm;
		ModelPart sleeve = right ? m.rightSleeve : m.leftSleeve;
		a.copyFrom(arm);
		sleeve.copyFrom(arm);
		a.visible = true;
		sleeve.visible = true;
		ResourceLocation base = dark ? (slimArms ? VOID_SLIM : VOID) : (slimArms ? SUIT_SLIM : SUIT);
		var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(base));
		a.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
		int glow = glowColour(player, player.tickCount, dark);
		if (glow != 0) {
			ResourceLocation g = dark ? (slimArms ? VOID_GLOW_SLIM : VOID_GLOW) : (slimArms ? SUIT_GLOW_SLIM : SUIT_GLOW);
			a.render(pose, buffers.getBuffer(RenderType.eyes(g)), 0xF000F0, OverlayTexture.NO_OVERLAY, glow);
		}
	}

	/** From {@code PlayerModelMixin}: the wearer's jacket / sleeve / trouser layers are hidden while the suit or the Void is on. */
	public static boolean hidesSkinOverlay(Player player) {
		return !player.isInvisible() && Sentry.hasPower(player);
	}

	/** In Void Form the head is the Void's too: the wearer's hat layer goes. */
	public static boolean hidesHat(Player player) {
		return !player.isInvisible() && Sentry.voidForm(player);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	static PlayerModel<LivingEntity> posed(HumanoidModel<?> parent, boolean slimArms, boolean head) {
		PlayerModel<LivingEntity> m = model(slimArms);
		((HumanoidModel) parent).copyPropertiesTo(m);
		m.setAllVisible(true);
		m.head.visible = head;
		m.hat.visible = head;
		m.jacket.copyFrom(m.body);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftPants.copyFrom(m.leftLeg);
		m.rightPants.copyFrom(m.rightLeg);
		return m;
	}
}
