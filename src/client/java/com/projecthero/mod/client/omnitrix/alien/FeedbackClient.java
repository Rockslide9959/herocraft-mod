package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.alien.feedback.FeedbackFx;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.feedback.FeedbackKit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.2 Feedback's client kit (user: "build a feedback alien please ... use this skin:
 * https://www.planetminecraft.com/skin/feedback-ben-10-5251468/ Give him the tail and long ear things with the plug things at the end
 * please"). The user's skin on the player body (x1.05, lanky), its painted chest Omnitrix painted out
 * ({@code scratchpad/ben10_v0171/gen_feedback.js}) and the mod's badge in its place on his white chest panel; his one green eye drawn
 * full-bright ({@code feedback_glow.png}). The two antenna cables, the tail, their gold plugs and his gold finger and toe caps are
 * {@link FeedbackCables}; his bolts {@link FeedbackFx}. The HUD shows his Charge.
 */
public final class FeedbackClient implements AlienClientKit {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/feedback.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/omnitrix/feedback_glow.png");
	/** The chest badge (body-bone pixels): where the skin painted it (jacket texels 23..24 x 37..39), on the white panel. */
	public static final float[] BADGE = { 0f, 2.5f, -2.3f, 2.6f };

	private static boolean registered;

	public static void register() {
		AlienClients.register(Aliens.FEEDBACK.id(), new FeedbackClient());
		if (!registered) {
			registered = true;
			FeedbackFx.register();
		}
	}

	// ---------------------------------------------------------------- poses (QuirkKeyframes: tick, rArm xyz, lArm xyz, body x, body y, rLeg x, lLeg x)
	private static float[] f(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float by, float rlx, float llx) {
		return QuirkKeyframes.f(t, rax, ray, raz, lax, lay, laz, bx, by, rlx, 0, 0, llx, 0, 0, 0);
	}

	/** Feedback Blast: the right hand thrown out, fingertips at it. */
	private static final float[][] BLAST = { QuirkKeyframes.rest(0), f(2, -1.65f, 0.1f, 0, 0.35f, 0, -0.25f, 0.05f, -0.25f, 0, 0),
			f(6, -1.6f, 0.1f, 0, 0.35f, 0, -0.25f, 0.05f, -0.25f, 0, 0), QuirkKeyframes.rest(10) };
	/** Rapid Fire: right, left, right, left, right. */
	private static final float[][] RAPID = { QuirkKeyframes.rest(0), f(2, -1.6f, 0.15f, 0, 0.2f, 0, 0, 0.05f, -0.3f, 0, 0), f(5, 0.2f, 0, 0, -1.6f, -0.15f, 0, 0.05f, 0.3f, 0, 0),
			f(8, -1.6f, 0.15f, 0, 0.2f, 0, 0, 0.05f, -0.3f, 0, 0), f(11, 0.2f, 0, 0, -1.6f, -0.15f, 0, 0.05f, 0.3f, 0, 0), f(14, -1.6f, 0.15f, 0, 0.2f, 0, 0, 0.05f, -0.3f, 0, 0),
			QuirkKeyframes.rest(19) };
	/** Absorb (held while braced, see {@link #pose}): arms spread wide and forward, palms out to take it. */
	private static final float[] ABSORB_HOLD = f(0, -1.0f, 0.35f, 0.95f, -1.0f, -0.35f, -0.95f, -0.12f, 0, -0.15f, 0.25f);
	private static final float[][] ABSORB = { QuirkKeyframes.rest(0), f(3, -1.0f, 0.35f, 0.95f, -1.0f, -0.35f, -0.95f, -0.12f, 0, -0.15f, 0.25f) };
	/** An absorbed hit: a quick recoil, arms still out. */
	private static final float[][] TAKE = { f(0, -1.0f, 0.35f, 0.95f, -1.0f, -0.35f, -0.95f, -0.12f, 0, -0.15f, 0.25f), f(2, -0.7f, 0.35f, 1.2f, -0.7f, -0.35f, -1.2f, -0.35f, 0, -0.3f, 0.35f),
			f(6, -1.0f, 0.35f, 0.95f, -1.0f, -0.35f, -0.95f, -0.12f, 0, -0.15f, 0.25f) };
	/** Redirect: both hands thrust out together. */
	private static final float[][] REDIRECT = { QuirkKeyframes.rest(0), f(2, -0.6f, 0, 0.9f, -0.6f, 0, -0.9f, -0.15f, 0, 0, 0), f(5, -1.6f, -0.2f, 0, -1.6f, 0.2f, 0, 0.2f, 0, -0.3f, 0.3f),
			f(10, -1.55f, -0.2f, 0, -1.55f, 0.2f, 0, 0.2f, 0, -0.3f, 0.3f), QuirkKeyframes.rest(15) };
	/** Conductoid Leap: crouched spring, then arms swept back and knees tucked. */
	private static final float[][] LEAP = { QuirkKeyframes.rest(0), f(2, 0.9f, 0, 0.3f, 0.9f, 0, -0.3f, 0.45f, 0, -0.9f, -0.9f), f(10, 0.6f, 0, 0.5f, 0.6f, 0, -0.5f, 0.3f, 0, -0.6f, -0.4f),
			QuirkKeyframes.rest(16) };
	/** Cable Swing: the left arm up after the cable, the right out for balance. */
	private static final float[][] SWING = { QuirkKeyframes.rest(0), f(3, -0.3f, 0, 0.6f, -2.8f, 0, -0.25f, 0.2f, 0.2f, -0.5f, 0.3f), f(14, -0.3f, 0, 0.6f, -2.8f, 0, -0.25f, 0.2f, 0.2f, -0.5f, 0.3f),
			QuirkKeyframes.rest(18) };
	/** Plug Whip: the body winds up and twists through as the tail lashes round. */
	private static final float[][] WHIP = { QuirkKeyframes.rest(0), f(2, 0.2f, 0, 0.7f, -0.6f, 0, -0.5f, 0.05f, 0.7f, 0.2f, -0.2f), f(6, -0.5f, 0, 0.9f, 0.3f, 0, -0.9f, 0.05f, -0.8f, -0.2f, 0.2f),
			f(9, -0.3f, 0, 0.7f, 0.2f, 0, -0.7f, 0, -0.5f, 0, 0), QuirkKeyframes.rest(14) };
	/** Plug In: braced, hands reaching toward it. */
	private static final float[] PLUG_HOLD = f(0, -1.25f, 0.3f, 0.15f, -1.25f, -0.3f, -0.15f, 0.15f, 0, -0.35f, 0.35f);
	private static final float[][] PLUG = { QuirkKeyframes.rest(0), f(3, -1.25f, 0.3f, 0.15f, -1.25f, -0.3f, -0.15f, 0.15f, 0, -0.35f, 0.35f) };
	/** Power Feed: hands drawn into the chest. */
	private static final float[][] FEED = { QuirkKeyframes.rest(0), f(3, -1.3f, -0.9f, 0, -1.3f, 0.9f, 0, -0.1f, 0, 0, 0), f(12, -1.3f, -0.9f, 0, -1.3f, 0.9f, 0, -0.1f, 0, 0, 0),
			QuirkKeyframes.rest(16) };
	/** Overclock: arched back, fists down and out, crackling. */
	private static final float[][] OVERCLOCK = { QuirkKeyframes.rest(0), f(3, 0.3f, 0, 0.55f, 0.3f, 0, -0.55f, -0.3f, 0, 0, 0), f(12, 0.3f, 0, 0.55f, 0.3f, 0, -0.55f, -0.3f, 0, 0, 0),
			QuirkKeyframes.rest(16) };
	/** Full Discharge: hunched to gather it, then flung wide. */
	private static final float[][] DISCHARGE = { QuirkKeyframes.rest(0), f(4, -0.9f, -0.6f, 0, -0.9f, 0.6f, 0, 0.45f, 0, -0.4f, -0.4f), f(7, -0.4f, 0, 1.6f, -0.4f, 0, -1.6f, -0.3f, 0, 0.3f, -0.3f),
			f(16, -0.4f, 0, 1.6f, -0.4f, 0, -1.6f, -0.3f, 0, 0.3f, -0.3f), QuirkKeyframes.rest(22) };
	/** Feedback Cannon: both hands together, braced, held through the beam. */
	private static final float[][] CANNON = { QuirkKeyframes.rest(0), f(3, -1.6f, -0.35f, 0, -1.6f, 0.35f, 0, 0.1f, 0, -0.5f, 0.5f), f(14, -1.6f, -0.35f, 0, -1.6f, 0.35f, 0, 0.15f, 0, -0.5f, 0.5f),
			QuirkKeyframes.rest(19) };
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0), f(2, -1.6f, 0.2f, 0, 0, 0, 0, 0.1f, -0.2f, 0, 0), QuirkKeyframes.rest(6) };

	private static final FirstPersonClip FP_THROW = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -20, 5, 0, 0.05f, 0.1f, -0.25f),
			FirstPersonClip.f(8, -20, 5, 0, 0.05f, 0.1f, -0.25f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_BOTH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -15, 0, 0, 0.05f, 0.1f, -0.3f),
			FirstPersonClip.f(10, -15, 0, 0, 0.05f, 0.1f, -0.3f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public float[] badge() {
		return BADGE;
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		// the cords, plugs and caps first (solid), then the full-bright eye
		FeedbackCables.render(pose, buffers, light, p, model, partial, age);
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFF8C8C8C); // a soft glow: the skin's own green shows through
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		// his plug-capped fingers on the bare arm too
		FeedbackCables.hand(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(ProjectHeroMod.id("textures/entity/mutation/white.png"))), light, arm, right);
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		FeedbackCables.tick(p);
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		int flags = Omnitrix.state(p).kitFlags;
		float[][] clip = switch (animId) {
			case FeedbackKit.ANIM_BLAST -> BLAST;
			case FeedbackKit.ANIM_RAPID -> RAPID;
			case FeedbackKit.ANIM_REDIRECT -> REDIRECT;
			case FeedbackKit.ANIM_LEAP -> LEAP;
			case FeedbackKit.ANIM_SWING -> SWING;
			case FeedbackKit.ANIM_WHIP -> WHIP;
			case FeedbackKit.ANIM_FEED -> FEED;
			case FeedbackKit.ANIM_OVERCLOCK -> OVERCLOCK;
			case FeedbackKit.ANIM_DISCHARGE -> DISCHARGE;
			case FeedbackKit.ANIM_CANNON -> CANNON;
			case FeedbackKit.ANIM_PUNCH -> PUNCH;
			case FeedbackKit.ANIM_TAKE -> (flags & FeedbackKit.F_ABSORB) != 0 ? TAKE : null;
			case FeedbackKit.ANIM_ABSORB -> (flags & FeedbackKit.F_ABSORB) != 0 && ticks < 3 ? ABSORB : null;
			case FeedbackKit.ANIM_PLUG -> (flags & FeedbackKit.F_PLUG) != 0 && ticks < 3 ? PLUG : null;
			default -> null;
		};
		if (clip != null && QuirkKeyframes.play(m, clip, ticks, 1f)) {
			return true;
		}
		// held stances
		if ((flags & FeedbackKit.F_ABSORB) != 0) {
			QuirkKeyframes.apply(m, ABSORB_HOLD, 1f);
			m.rightArm.zRot += Mth.sin(age * 0.6f) * 0.04f;
			m.leftArm.zRot -= Mth.sin(age * 0.6f) * 0.04f;
			return true;
		}
		if ((flags & FeedbackKit.F_PLUG) != 0) {
			QuirkKeyframes.apply(m, PLUG_HOLD, 1f);
			return true;
		}
		return false;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case FeedbackKit.ANIM_BLAST, FeedbackKit.ANIM_RAPID, FeedbackKit.ANIM_PUNCH -> FP_THROW;
			case FeedbackKit.ANIM_REDIRECT, FeedbackKit.ANIM_CANNON, FeedbackKit.ANIM_PLUG, FeedbackKit.ANIM_ABSORB -> FP_BOTH;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- the HUD: his Charge

	@Override
	public int hudHeight() {
		return 10;
	}

	@Override
	public void hud(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.player.LocalPlayer p, int x, int y, int w, float partial) {
		Minecraft mc = Minecraft.getInstance();
		int c = FeedbackKit.charge(p);
		boolean full = c >= FeedbackKit.MAX_CHARGE;
		boolean flash = (p.level().getGameTime() / 6) % 2 == 0;
		String value = full && flash ? "FULL" : String.valueOf(c);
		com.projecthero.mod.client.omnitrix.OmnitrixHud.meter(g, mc, x, y - 6, Component.translatable("hud.projecthero.omnitrix.feedback.meter"),
				c / (float) FeedbackKit.MAX_CHARGE, 0xFF000000 | FeedbackKit.COLOUR, value, full ? 0xFFFFF0A0 : 0xFFE2E2E2);
	}
}
