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
 *   ticks (per pixel, outward from the heart: {@link SentrySuitTextures#step}); leaving the mode runs it back. Round 3 (user: "If player
 *   enters the controlled void mode then the red and black sentry suit should appear on the player even if the player isnt wearing the
 *   suit, the suit then disappears after player returns to normal unless they were wearing the suit before"): without the suit, merging
 *   fades the same red-and-black Void suit in over the bare wearer (his own face and hair still show, the black cape unrolls with it), and
 *   returning fades it away to his own skin; with the suit on it fades back to the comics suit. Which one is {@code Sentry.look}, from
 *   the armour worn right now (a visual only: no item is given).</li>
 *   <li><b>The takeover</b>: the whole body black ({@code void_body.png}: head, body, arms, legs, every outer layer hidden, armour hidden
 *   by {@code QuirkSuitArmourMixin}). Round 3 (user: "make the white eyes a lot smaller, literal tiny glowing specs"): the eyes are no
 *   longer texture pixels but two full-bright specks a third of a pixel wide on the face, each in a faint halo ({@link #eyeSpecks}).</li>
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
		if (!s.voidForm && s.formChangedAt == 0L) {
			return 0f; // never merged yet (a fresh world's clock can be under the fade's length)
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
		return switch (Sentry.look(player, voidFade(player, partial))) {
			case BLACK -> Mode.BLACK;
			case SUIT, VOID_OVER_SUIT -> Mode.SUIT;
			case VOID_OVER_SKIN -> Mode.BARE;
			default -> Mode.NONE;
		};
	}

	/** {base, glow} for this player and mode at this point of the fade. */
	static ResourceLocation[] textures(Player player, Mode mode, boolean slimArms, float partial) {
		if (mode == Mode.BLACK) {
			// round 3: no glow texture -- the eyes are the specks drawn by eyeSpecks
			return new ResourceLocation[] { SentrySuitTextures.base(SentrySuitTextures.Look.BLACK, slimArms), null };
		}
		int k = Math.round(voidFade(player, partial) * SentrySuitTextures.STEPS);
		if (mode == Mode.SUIT) {
			return SentrySuitTextures.step(SentrySuitTextures.Look.SUIT, SentrySuitTextures.Look.VOID_SUIT, slimArms, k);
		}
		// round 3: without the suit, the red-and-black Void suit itself fades in over his skin (was the original Void Form body)
		return SentrySuitTextures.step(null, SentrySuitTextures.Look.VOID_SUIT, slimArms, k);
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
		if (mode == Mode.BLACK && m.head.visible) {
			eyeSpecks(pose, buffers, m.head, ageInTicks);
		}
	}

	/** Round 3: the eyes' size (model pixels) -- the core speck and its faint halo. */
	static final float EYE_SPECK = 0.32f, EYE_HALO = 1.1f;

	/**
	 * Round 3 (user: "When the void takes over the player make the white eyes a lot smaller, literal tiny glowing specs on the players
	 * face"): two full-bright white specks a third of a pixel across, where the eyes are (face pixels 2 and 5 of 8, row 4), just in front of
	 * the face, each with a soft halo that breathes a little. Drawn additively in the head's own frame, so they turn and nod with it.
	 */
	static void eyeSpecks(PoseStack pose, MultiBufferSource buffers, ModelPart head, float age) {
		pose.pushPose();
		head.translateAndRotate(pose);
		var vc = buffers.getBuffer(RenderType.lightning());
		var last = pose.last();
		float z = -4.12f / 16f;
		float y = -3.5f / 16f;
		float halo = 0.22f + 0.06f * Mth.sin(age * 0.15f);
		for (float x : new float[] { -1.5f / 16f, 1.5f / 16f }) {
			quad(vc, last, x, y, z, EYE_HALO / 32f, 1f, 1f, 1f, halo * 0.5f);
			quad(vc, last, x, y, z - 0.0005f, EYE_HALO / 64f, 1f, 1f, 1f, halo);
			quad(vc, last, x, y, z - 0.001f, EYE_SPECK / 32f, 1f, 1f, 1f, 1f);
		}
		pose.popPose();
	}

	private static void quad(com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack.Pose last, float x, float y, float z, float h, float r,
			float g, float b, float a) {
		vc.addVertex(last, x - h, y - h, z).setColor(r, g, b, a);
		vc.addVertex(last, x - h, y + h, z).setColor(r, g, b, a);
		vc.addVertex(last, x + h, y + h, z).setColor(r, g, b, a);
		vc.addVertex(last, x + h, y - h, z).setColor(r, g, b, a);
		// and the other winding (whichever way the cull reads it after the renderer's flip)
		vc.addVertex(last, x + h, y - h, z).setColor(r, g, b, a);
		vc.addVertex(last, x + h, y + h, z).setColor(r, g, b, a);
		vc.addVertex(last, x - h, y + h, z).setColor(r, g, b, a);
		vc.addVertex(last, x - h, y - h, z).setColor(r, g, b, a);
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

	/**
	 * The first-person arm (from {@code SuitFirstPersonArmMixin}), posed like the vanilla arm just drawn, matching the third-person look:
	 * the Sentry Suit top's sleeve (bare hand: the wearer's own skin shows through), merged the same per-pixel dissolve into the Void suit
	 * sleeve, merged without the suit the Void Form body's arm, the takeover all black; nothing without suit or Void (the normal arm).
	 * The wearer's own outer sleeve is hidden by {@link #hideOverlay} (the hand's {@code setupAnim} runs it too). Slim / wide arms each
	 * have their textures.
	 */
	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm, boolean right,
			float partial) {
		Mode mode = mode(player, partial);
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
		ResourceLocation[] tex = textures(player, mode, slimArms, partial);
		if (tex[0] != null) {
			var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex[0]));
			a.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
			sleeve.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
		}
		if (tex[1] != null) {
			int glow = mode == Mode.BLACK ? 0xFFFFFFFF : glowColour(player, player.tickCount + partial, voidFade(player, partial) >= 0.5f);
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
			case BARE -> {
				// round 3: the Void suit over his skin leaves his own face and hair (the hat layer) showing
				model.jacket.visible = false;
				model.leftSleeve.visible = false;
				model.rightSleeve.visible = false;
				model.leftPants.visible = false;
				model.rightPants.visible = false;
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
