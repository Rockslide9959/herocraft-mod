package com.projecthero.mod.client.omnitrix.alien;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.OmnitrixHud;
import com.projecthero.mod.client.omnitrix.alien.goop.GoopFx;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.goop.GoopKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.2 (Recalibrated Omnitrix): Goop's client kit. v0.17.2 revamp (user: "delete all the alien models and only use the skins i
 * provided please no extra things untill i say"): the body is ONLY the player model in the user's Goop skin -- no projector cube
 * over his head, no stretched-arm boxes, no drips. The skin has see-through pixels, so he is drawn on the translucent entity type
 * ({@link #bodyAlpha} under 255) and a little glassy, like slime. His moves are rendered effects (QuirkFx ribbons, cones, rings and
 * pools spawned by the kit; the acid blobs, puddles and the projector lying on the ground in {@link GoopFx}); a pose and a
 * first-person clip per move; one HUD row for the projector.
 */
public final class GoopClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/goop.png");

	private GoopClient() {
	}

	public static void register() {
		AlienClients.register(Aliens.GOOP.id(), new GoopClient());
		GoopFx.register();
	}

	private static float[] f(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float hx) {
		return QuirkKeyframes.f(t, rax, ray, raz, lax, lay, laz, bx, 0, 0, 0, 0, 0, 0, 0, hx);
	}

	private static final float[][] SPIT = { QuirkKeyframes.rest(0), f(2, 0.4f, 0, 0.2f, 0.4f, 0, -0.2f, 0.3f, 0.35f), QuirkKeyframes.rest(8) };
	private static final float[][] GLOB = { QuirkKeyframes.rest(0), f(3, -2.8f, 0, 0.2f, 0.2f, 0, 0, -0.15f, 0), f(6, -0.9f, 0, 0, 0.2f, 0, 0, 0.25f, 0),
			QuirkKeyframes.rest(12) };
	private static final float[][] WHIP = { QuirkKeyframes.rest(0), f(2, -2.4f, 0, 0.1f, 0, 0, 0, 0, 0), f(5, -1.55f, 0.05f, 0, 0, 0, 0, 0.15f, 0),
			f(10, -1.5f, 0.05f, 0, 0, 0, 0, 0.1f, 0), QuirkKeyframes.rest(14) };
	private static final float[][] ENGULF = { QuirkKeyframes.rest(0), f(3, -1.6f, -0.9f, 0, -1.6f, 0.9f, 0, 0.2f, 0), f(8, -1.5f, 0.35f, 0, -1.5f, -0.35f, 0, 0.1f, 0),
			f(56, -1.3f, 0.35f, 0, -1.3f, -0.35f, 0, 0.1f, 0), QuirkKeyframes.rest(62) };
	private static final float[][] SPIT_OUT = { QuirkKeyframes.rest(0), f(2, -0.6f, 0.5f, 0, -0.6f, -0.5f, 0, -0.25f, -0.3f),
			f(5, -1.6f, -0.2f, 0, -1.6f, 0.2f, 0, 0.35f, 0.25f), QuirkKeyframes.rest(12) };
	private static final float[][] BOUNCE = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(2, -2.6f, 0, 0.5f, -2.6f, 0, -0.5f, -0.1f, 0, -0.4f, 0, 0.2f, -0.4f, 0, -0.2f, -0.2f),
			QuirkKeyframes.f(10, -2.5f, 0, 0.5f, -2.5f, 0, -0.5f, -0.1f, 0, -0.4f, 0, 0.2f, -0.4f, 0, -0.2f, -0.2f), QuirkKeyframes.rest(14) };
	private static final float[][] SQUEEZE = { QuirkKeyframes.rest(0), f(3, -0.3f, 0, 0.1f, -0.3f, 0, -0.1f, 0.6f, 0.3f),
			f(116, -0.3f, 0, 0.1f, -0.3f, 0, -0.1f, 0.6f, 0.3f), QuirkKeyframes.rest(120) };
	private static final float[][] SLAM = { QuirkKeyframes.rest(0), f(4, -3.0f, 0, 0.2f, -3.0f, 0, -0.2f, -0.2f, -0.2f), f(7, -0.5f, 0, 0.3f, -0.5f, 0, -0.3f, 0.5f, 0.2f),
			QuirkKeyframes.rest(14) };
	private static final float[][] SPRAY = { QuirkKeyframes.rest(0), f(3, -1.55f, -0.15f, 0, -1.55f, 0.15f, 0, 0.15f, 0), f(28, -1.5f, -0.15f, 0, -1.5f, 0.15f, 0, 0.15f, 0),
			QuirkKeyframes.rest(33) };
	private static final float[][] REFORM = { QuirkKeyframes.rest(0), f(4, -0.3f, 0, 1.2f, -0.3f, 0, -1.2f, -0.1f, -0.3f), f(10, -0.6f, 0.4f, 0.2f, -0.6f, -0.4f, -0.2f, 0.1f, 0),
			QuirkKeyframes.rest(16) };
	/** Slime Chute: spread flat, arms and legs out like a sheet. */
	private static final float[] CHUTE = QuirkKeyframes.f(0, -0.1f, 0, 1.5f, -0.1f, 0, -1.5f, 0, 0, 0, 0, 0.5f, 0, 0, -0.5f, 0);
	/** Acid Tide: thrown forward, arms out ahead, flowing. */
	private static final float[][] TIDE = { QuirkKeyframes.rest(0), f(3, -2.9f, -0.2f, 0.2f, -2.9f, 0.2f, -0.2f, 0.6f, 0.3f), f(10, -2.9f, -0.2f, 0.2f, -2.9f, 0.2f, -0.2f, 0.6f, 0.3f),
			QuirkKeyframes.rest(16) };
	private static final float[][] CANNON = { QuirkKeyframes.rest(0), f(4, -1.6f, -0.2f, 0, -1.6f, 0.2f, 0, -0.2f, 0), f(7, -1.8f, -0.1f, 0, -1.8f, 0.1f, 0, -0.35f, -0.1f),
			QuirkKeyframes.rest(14) };
	private static final float[][] KNOCKED = { QuirkKeyframes.rest(0), f(3, -0.2f, 0, 1.0f, -0.2f, 0, -1.0f, -0.3f, -0.4f), QuirkKeyframes.rest(20) };
	/** Projector Fling: an overarm throw, then he melts after it. */
	private static final float[][] FLING = { QuirkKeyframes.rest(0), f(3, -2.9f, 0, 0.2f, -0.3f, 0, -0.2f, -0.2f, -0.2f), f(6, -1.2f, 0, -0.1f, 0.2f, 0, -0.2f, 0.4f, 0.1f),
			QuirkKeyframes.rest(10) };
	/** Dissolve: a palm laid on the block. */
	private static final float[][] MELT = { QuirkKeyframes.rest(0), f(3, -1.5f, -0.1f, 0, 0, 0, 0, 0.1f, 0.2f), f(10, -1.45f, -0.1f, 0, 0, 0, 0, 0.1f, 0.2f),
			QuirkKeyframes.rest(14) };
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0), f(2, -1.7f, -0.1f, 0, 0.3f, 0, 0, 0.1f, 0), QuirkKeyframes.rest(7) };

	private static final FirstPersonClip FP_SPIT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 25, 0, 0, 0, -0.3f, 0.1f),
			FirstPersonClip.f(8, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_THROW = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -80, 5, 0, 0.05f, 0.55f, 0.15f),
			FirstPersonClip.f(6, -10, 10, 0, 0.05f, 0.05f, -0.3f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_REACH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -12, 8, 0, 0.05f, 0.12f, -0.9f),
			FirstPersonClip.f(8, -12, 8, 0, 0.05f, 0.12f, -0.8f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_BOTH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -18, 15, 0, 0.12f, 0.12f, -0.3f),
			FirstPersonClip.f(10, -16, 14, 0, 0.12f, 0.12f, -0.28f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SPRAY = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -18, 15, 0, 0.12f, 0.12f, -0.3f),
			FirstPersonClip.f(28, -16, 14, 0, 0.12f, 0.12f, -0.28f), FirstPersonClip.f(33, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SLAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -80, 10, 0, 0.05f, 0.6f, 0.1f),
			FirstPersonClip.f(7, 30, 10, 0, 0.05f, -0.35f, -0.1f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_WIDE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -20, -30, 25, -0.2f, 0.2f, 0.05f),
			FirstPersonClip.f(10, -20, -30, 25, -0.2f, 0.2f, 0.05f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -10, 5, 0, 0f, 0.05f, -0.35f),
			FirstPersonClip.f(7, 0, 0, 0, 0, 0, 0));

	/**
	 * v0.17.2 (user: "add the projector above his head please"): his anti-gravity projector floating a little over his head, bobbing
	 * and slowly turning, its green light shining down on him -- gone while it is knocked off (it lies on the ground then, GoopFx).
	 */
	@Override
	public void renderExtras(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light,
			net.minecraft.client.player.AbstractClientPlayer p, net.minecraft.client.model.PlayerModel<net.minecraft.client.player.AbstractClientPlayer> model, float partial,
			float age) {
		if ((com.projecthero.mod.omnitrix.Omnitrix.state(p).kitFlags & com.projecthero.mod.omnitrix.alien.goop.GoopKit.F_OFF) != 0) {
			return;
		}
		com.projecthero.mod.client.omnitrix.AlienParts.on(pose, model.head, () -> {
			pose.pushPose();
			pose.translate(0f, (-11.5f + 0.4f * net.minecraft.util.Mth.sin(age * 0.12f)) / 16f, 0f);
			pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(age * 1.5f));
			pose.scale(0.75f, 0.75f, 0.75f);
			com.projecthero.mod.client.omnitrix.alien.goop.GoopFx.drawSaucer(pose, buffers, light, 0.7f + 0.3f * net.minecraft.util.Mth.sin(age * 0.25f));
			pose.popPose();
		});
	}

	/** v0.17.2 (user: "make goops model slim aswell please instead of filling in the gaps"): his skin is laid out for slim (3 px) arms. */
	@Override
	public boolean slimArms(net.minecraft.client.player.AbstractClientPlayer p) {
		return true;
	}

	/** v0.17.2 (user: "raise our omnitrix badge slightly"): a pixel higher than the default, where the skin's own symbol was. */
	@Override
	public float[] badge() {
		return new float[] { 0f, 3.5f, -2.05f, 3.2f };
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	/** Translucent (the user's skin has see-through pixels) and a little glassy; fainter as a puddle. */
	@Override
	public int bodyAlpha(AbstractClientPlayer p) {
		return (Omnitrix.state(p).kitFlags & (GoopKit.F_OFF | GoopKit.F_POUR | GoopKit.F_TIDE)) != 0 ? 150 : 225;
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & GoopKit.F_GLIDE) != 0) {
			QuirkKeyframes.apply(m, CHUTE, 1f);
			float w = Mth.sin(age * 0.4f) * 0.08f;
			m.rightArm.zRot += w;
			m.leftArm.zRot -= w;
			return true;
		}
		float[][] clip = switch (animId) {
			case GoopKit.ANIM_SPIT -> SPIT;
			case GoopKit.ANIM_GLOB -> GLOB;
			case GoopKit.ANIM_WHIP -> WHIP;
			case GoopKit.ANIM_ENGULF -> ENGULF;
			case GoopKit.ANIM_SPIT_OUT -> SPIT_OUT;
			case GoopKit.ANIM_BOUNCE -> BOUNCE;
			case GoopKit.ANIM_SQUEEZE -> SQUEEZE;
			case GoopKit.ANIM_SLAM -> SLAM;
			case GoopKit.ANIM_SPRAY -> SPRAY;
			case GoopKit.ANIM_REFORM -> REFORM;
			case GoopKit.ANIM_FLOOD -> TIDE;
			case GoopKit.ANIM_CANNON -> CANNON;
			case GoopKit.ANIM_KNOCKED -> KNOCKED;
			case GoopKit.ANIM_FLING -> FLING;
			case GoopKit.ANIM_MELT -> MELT;
			case GoopKit.ANIM_PUNCH -> PUNCH;
			default -> null;
		};
		if ((animId == GoopKit.ANIM_SQUEEZE && (s.kitFlags & GoopKit.F_SQUEEZE) == 0 && ticks > 4)
				|| (animId == GoopKit.ANIM_SPRAY && (s.kitFlags & GoopKit.F_SPRAY) == 0 && ticks > 4)
				|| (animId == GoopKit.ANIM_ENGULF && (s.kitFlags & GoopKit.F_ENGULF) == 0 && ticks > 10)) {
			clip = null;
		}
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		// his whole body wobbles like jelly; slumped flat while the projector is off (or while he pours / surges)
		float w = Mth.sin(age * 0.25f) * 0.05f;
		m.rightArm.zRot += w;
		m.leftArm.zRot -= w;
		if ((s.kitFlags & (GoopKit.F_OFF | GoopKit.F_POUR)) != 0 && !played) {
			m.rightArm.zRot += 1.0f;
			m.leftArm.zRot -= 1.0f;
			m.rightLeg.zRot += 0.6f;
			m.leftLeg.zRot -= 0.6f;
			m.head.xRot += 0.4f;
			return true;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case GoopKit.ANIM_SPIT -> FP_SPIT;
			case GoopKit.ANIM_GLOB, GoopKit.ANIM_FLING -> FP_THROW;
			case GoopKit.ANIM_WHIP, GoopKit.ANIM_MELT -> FP_REACH;
			case GoopKit.ANIM_SPRAY -> FP_SPRAY;
			case GoopKit.ANIM_ENGULF, GoopKit.ANIM_CANNON, GoopKit.ANIM_SPIT_OUT -> FP_BOTH;
			case GoopKit.ANIM_SLAM, GoopKit.ANIM_BOUNCE, GoopKit.ANIM_FLOOD -> FP_SLAM;
			case GoopKit.ANIM_REFORM, GoopKit.ANIM_SQUEEZE, GoopKit.ANIM_KNOCKED -> FP_WIDE;
			case GoopKit.ANIM_PUNCH -> FP_PUNCH;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- HUD: the projector row

	@Override
	public int hudHeight() {
		return 10;
	}

	/** One row: "Projector" -- on (full, green), or knocked off with the seconds until it floats back by itself. */
	@Override
	public void hud(GuiGraphics g, LocalPlayer p, int x, int y, int w, float partial) {
		Minecraft mc = Minecraft.getInstance();
		OmnitrixState s = Omnitrix.state(p);
		Component label = Component.translatable("hud.projecthero.omnitrix.goop.projector");
		if ((s.kitFlags & GoopKit.F_OFF) != 0) {
			long since = s.readyAt(GoopKit.ID + "." + GoopKit.OFF_SINCE);
			float t = since > 0 ? p.level().getGameTime() + partial - since : 0f;
			float frac = Mth.clamp(t / GoopKit.PROJECTOR_RETURN, 0f, 1f);
			String value = String.format(java.util.Locale.ROOT, "%.0fs", Math.max(0, Math.ceil((GoopKit.PROJECTOR_RETURN - t) / 20f)));
			OmnitrixHud.meter(g, mc, x, y - 6, label, frac, 0xFFC03A2A, value, 0xFFFF7060);
			return;
		}
		boolean squeezed = (s.kitFlags & GoopKit.F_SQUEEZE) != 0;
		if (squeezed) {
			long start = s.readyAt(GoopKit.ID + "." + GoopKit.SQUEEZE_START);
			float t = p.level().getGameTime() + partial - start;
			OmnitrixHud.meter(g, mc, x, y - 6, Component.translatable("hud.projecthero.omnitrix.goop.squeeze"),
					Mth.clamp(1f - t / GoopKit.SQUEEZE_MAX, 0f, 1f), 0xFF7CE03A, String.format(java.util.Locale.ROOT, "%.1fs",
							Math.max(0f, (GoopKit.SQUEEZE_MAX - t) / 20f)), 0xFFFFFFFF);
			return;
		}
		OmnitrixHud.meter(g, mc, x, y - 6, label, 1f, 0xFF7CE03A, Component.translatable("hud.projecthero.omnitrix.goop.on").getString(), 0xFFB8F890);
	}
}
