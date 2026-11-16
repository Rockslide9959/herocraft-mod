package com.projecthero.mod.client.sentry;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.sentry.Sentry;
import com.projecthero.mod.sentry.SentryConfig;
import com.projecthero.mod.sentry.data.SentryState;
import com.projecthero.mod.sentry.item.SentrySuitItem;

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
 * v0.17.5 Sentry: his look, drawn over the wearer's own skin (every viewer: the state and the armour are synced).
 *
 * <p>v0.17.5 rework (user: "dont put the suit on automatically when the player gets the power ... If the player is wearing the sentry suit
 * and uses the let it out move make the players suit look like its slowly fading into this suit ... When the void consumes the players
 * body make the entire players body go black and their cape if theyre wearing the suit and only have tiny glowing white pixels for eyes"):
 * <ul>
 *   <li><b>The Sentry Suit item</b> ({@link SentrySuitItem}) is what draws the suit now -- the power alone draws nothing. The top draws
 *   the body and arms, the trousers the legs; the head is never covered (his own face and hair).</li>
 *   <li><b>Merged with the Void</b> (a won Let It Out) the suit dissolves into the Void suit over {@link SentryConfig#FORM_FADE_TICKS}
 *   ticks (per pixel, outward from the heart: {@link SentrySuitTextures#step}); leaving the mode runs it back. Without the suit, merging
 *   fades the original Void Form body in over the bare wearer the same way.</li>
 *   <li><b>The takeover</b>: the whole body black ({@code void_body.png}: head, body, arms, legs, every outer layer hidden, armour hidden
 *   by {@code QuirkSuitArmourMixin}) with two tiny white pixel eyes drawn full bright.</li>
 * </ul>
 * Each has an emissive pass; the Sentry suit's glow flickers dark from 50 Void, the Void's pulses. Wide and slim arms follow the
 * wearer's skin.
 */
public class SentrySuitLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static PlayerModel<LivingEntity> wide;
	private static PlayerModel<LivingEntity> slim;

	public SentrySuitLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
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

	/** How far into the Void the look is, 0 (Sentry) .. 1 (merged), easing over the fade. Client-safe. */
	public static float voidFade(Player player, float partial) {
		SentryState s = Sentry.peek(player);
		if (s == null || !s.hasPower) {
			return 0f;
		}
		if (s.inTakeover()) {
			return 1f;
		}
		float k = Mth.clamp((player.level().getGameTime() + partial - s.formChangedAt) / SentryConfig.FORM_FADE_TICKS, 0f, 1f);
		return s.voidForm ? k : 1f - k;
	}

	/** What is drawn on this player right now. */
	enum Mode {
		NONE, SUIT, BARE, BLACK
	}

	static Mode mode(Player player, float partial) {
		if (player.isInvisible()) {
			return Mode.NONE;
		}
		if (Sentry.inTakeover(player)) {
			return Mode.BLACK;
		}
		if (SentrySuitItem.topWorn(player) || SentrySuitItem.trousersWorn(player)) {
			return Mode.SUIT;
		}
		if (Sentry.hasPower(player) && voidFade(player, partial) > 0f) {
			return Mode.BARE;
		}
		return Mode.NONE;
	}

	/** {base, glow} for this player and mode at this point of the fade. */
	static ResourceLocation[] textures(Player player, Mode mode, boolean slimArms, float partial) {
		if (mode == Mode.BLACK) {
			return new ResourceLocation[] { SentrySuitTextures.base(SentrySuitTextures.Look.BLACK, slimArms),
					SentrySuitTextures.glow(SentrySuitTextures.Look.BLACK, slimArms) };
		}
		int k = Math.round(voidFade(player, partial) * SentrySuitTextures.STEPS);
		if (mode == Mode.SUIT) {
			return SentrySuitTextures.step(SentrySuitTextures.Look.SUIT, SentrySuitTextures.Look.VOID_SUIT, slimArms, k);
		}
		return SentrySuitTextures.step(null, SentrySuitTextures.Look.VOID_BODY, slimArms, k);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		Mode mode = mode(player, partialTick);
		if (mode == Mode.NONE) {
			return;
		}
		boolean slimArms = player.getSkin().model() == PlayerSkin.Model.SLIM;
		PlayerModel<LivingEntity> m = posed(getParentModel(), slimArms, mode, player);
		ResourceLocation[] tex = textures(player, mode, slimArms, partialTick);
		if (tex[0] != null) {
			m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex[0])), light, OverlayTexture.NO_OVERLAY);
		}
		if (tex[1] != null) {
			int glow = mode == Mode.BLACK ? 0xFFFFFFFF : glowColour(player, ageInTicks, voidFade(player, partialTick) >= 0.5f);
			if (glow != 0) {
				m.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(tex[1])), 0xF000F0, OverlayTexture.NO_OVERLAY, glow);
			}
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
		if (Sentry.hasPower(player) && vd >= SentryConfig.VOID_WHISPERS) {
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
		Mode mode = mode(player, 1f);
		if (mode == Mode.NONE || (mode == Mode.SUIT && !SentrySuitItem.topWorn(player))) {
			return;
		}
		boolean slimArms = player.getSkin().model() == PlayerSkin.Model.SLIM;
		PlayerModel<LivingEntity> m = model(slimArms);
		ModelPart a = right ? m.rightArm : m.leftArm;
		ModelPart sleeve = right ? m.rightSleeve : m.leftSleeve;
		a.copyFrom(arm);
		sleeve.copyFrom(arm);
		a.visible = true;
		sleeve.visible = true;
		ResourceLocation[] tex = textures(player, mode, slimArms, 1f);
		if (tex[0] != null) {
			var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex[0]));
			a.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
			sleeve.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
		}
		if (tex[1] != null) {
			int glow = mode == Mode.BLACK ? 0xFFFFFFFF : glowColour(player, player.tickCount, voidFade(player, 1f) >= 0.5f);
			if (glow != 0) {
				a.render(pose, buffers.getBuffer(RenderType.eyes(tex[1])), 0xF000F0, OverlayTexture.NO_OVERLAY, glow);
			}
		}
	}

	/**
	 * From {@code PlayerModelMixin}: hides the wearer's outer layers under what is drawn (true: handled). The suit top hides the jacket and
	 * sleeves, the trousers the trouser layer; the bare Void Form and the takeover hide every outer layer, the hat included.
	 */
	public static boolean hideOverlay(Player player, PlayerModel<?> model) {
		Mode mode = mode(player, 1f);
		switch (mode) {
			case NONE -> {
				return false;
			}
			case SUIT -> {
				boolean top = SentrySuitItem.topWorn(player);
				boolean legs = SentrySuitItem.trousersWorn(player);
				if (top) {
					model.jacket.visible = false;
					model.leftSleeve.visible = false;
					model.rightSleeve.visible = false;
				}
				if (legs) {
					model.leftPants.visible = false;
					model.rightPants.visible = false;
				}
				return true;
			}
			default -> {
				model.hat.visible = false;
				model.jacket.visible = false;
				model.leftSleeve.visible = false;
				model.rightSleeve.visible = false;
				model.leftPants.visible = false;
				model.rightPants.visible = false;
				return true;
			}
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	static PlayerModel<LivingEntity> posed(HumanoidModel<?> parent, boolean slimArms, Mode mode, Player player) {
		PlayerModel<LivingEntity> m = model(slimArms);
		((HumanoidModel) parent).copyPropertiesTo(m);
		m.setAllVisible(true);
		boolean head = mode != Mode.SUIT; // the suit never covers the head (and its head texels are clear anyway)
		m.head.visible = head;
		m.hat.visible = head;
		if (mode == Mode.SUIT) {
			boolean top = SentrySuitItem.topWorn(player);
			boolean legs = SentrySuitItem.trousersWorn(player);
			m.body.visible = top;
			m.jacket.visible = top;
			m.leftArm.visible = top;
			m.rightArm.visible = top;
			m.leftSleeve.visible = top;
			m.rightSleeve.visible = top;
			m.leftLeg.visible = legs;
			m.rightLeg.visible = legs;
			m.leftPants.visible = legs;
			m.rightPants.visible = legs;
		}
		m.jacket.copyFrom(m.body);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftPants.copyFrom(m.leftLeg);
		m.rightPants.copyFrom(m.rightLeg);
		return m;
	}

	/** (Kept for callers that only need the flag.) The suit or a Void look hides some of the wearer's outer layer. */
	public static boolean hidesSkinOverlay(@Nullable Player player) {
		return player != null && mode(player, 1f) != Mode.NONE;
	}
}
