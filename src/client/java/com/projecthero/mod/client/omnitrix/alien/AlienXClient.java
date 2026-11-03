package com.projecthero.mod.client.omnitrix.alien;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.OmnitrixHud;
import com.projecthero.mod.client.omnitrix.alien.alienx.AlienXFx;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.alienx.AlienXKit;
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
 * v0.17.2: Alien X's client kit. v0.17.2 revamp (user: "delete all the alien models and only use the skins i provided please no
 * extra things untill i say"): the body is ONLY the player model, a little taller (the kit's 1.15 scale), in the user's Alien X
 * skin -- no horns, no star shimmer pass, no glowing-eye pass. A still "debating" pose (head bowed, arms out, trembling a little)
 * while Serena and Bellicus argue, a pleading pose when Ben argues, a pose and a first-person clip per move, and the HUD: the
 * debate row (who speaks, and how each voted once they have) and the two voices' Council Favour. The move effects are
 * {@link AlienXFx} (the extras sheet is only its starfield texture for the rifts now).
 */
public final class AlienXClient implements AlienClientKit {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/alien_x.png");
	/** The rifts' starfield texture (AlienXFx) -- not drawn on the body. */
	public static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/alien_x_extras.png");
	/** Extras-sheet UV rectangles (0..1): the starfield patch, the star-black backing. */
	public static final float[] STARFIELD = { 32 / 64f, 0f, 1f, 0.5f };
	public static final float[] VOID = { 0f, 32 / 64f, 16 / 64f, 48 / 64f };

	public static final int SERENA_COLOUR = 0xFF9FE8FF, BELLICUS_COLOUR = 0xFFFF5040, BEN_COLOUR = 0xFF7CFF6A;

	// ---------------------------------------------------------------- third-person poses
	// f(tick, rArm xyz, lArm xyz, body xy, rLeg xyz, lLeg xyz, head x)
	/** Debating: still, head bowed, arms held out and down, palms open. */
	private static final float[] DEBATE_POSE = QuirkKeyframes.f(0, -0.25f, 0.1f, 0.55f, -0.25f, -0.1f, -0.55f, 0.05f, 0, 0, 0, 0.06f, 0, 0, -0.06f, 0.5f);
	/** Reality Bolt: one arm thrust out, two fingers forward. */
	private static final float[][] BOLT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.65f, -0.1f, 0, 0.2f, 0, -0.15f, 0.05f, -0.15f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(8, -1.6f, -0.1f, 0, 0.2f, 0, -0.15f, 0.05f, -0.15f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(13) };
	/** Cosmic Ray: both palms forward, braced. */
	private static final float[][] RAY = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.55f, 0.25f, 0, -1.55f, -0.25f, 0, 0.1f, 0, 0.2f, 0, 0.1f, -0.25f, 0, -0.1f, 0),
			QuirkKeyframes.f(16, -1.5f, 0.25f, 0, -1.5f, -0.25f, 0, 0.1f, 0, 0.2f, 0, 0.1f, -0.25f, 0, -0.1f, 0), QuirkKeyframes.rest(21) };
	/** Telekinetic Hurl: a hand raised high, then flung forward. */
	private static final float[][] HURL = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.9f, 0, 0.2f, -0.4f, 0, -0.2f, -0.1f, 0.2f, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(7, -1.0f, 0, -0.1f, 0.2f, 0, -0.2f, 0.2f, -0.3f, 0, 0, 0, 0, 0, 0, 0.1f), QuirkKeyframes.rest(13) };
	/** Gravity Crush: both hands pressed down to the ground. */
	private static final float[][] CRUSH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.6f, 0, 0.3f, -2.6f, 0, -0.3f, 0, 0, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(7, -0.6f, 0, 0.2f, -0.6f, 0, -0.2f, 0.35f, 0, 0, 0, 0.1f, 0, 0, -0.1f, 0.4f), QuirkKeyframes.rest(15) };
	/** Blink: a snap of the fingers, the other arm tucked. */
	private static final float[][] BLINK = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -2.0f, -0.5f, 0.2f, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.rest(9) };
	/** Cosmic Flight: arms out, rising. */
	private static final float[][] FLIGHT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.3f, 0, 1.1f, -0.3f, 0, -1.1f, -0.1f, 0, 0.15f, 0, 0.1f, 0.15f, 0, -0.1f, -0.3f),
			QuirkKeyframes.rest(14) };
	/** Time Stop: one palm up and out -- halt. */
	private static final float[][] STOP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.7f, 0, 0.5f, -1.7f, 0, -0.5f, 0, 0, 0, 0, 0, 0, 0, 0, -0.1f),
			QuirkKeyframes.f(18, -1.7f, 0, 0.5f, -1.7f, 0, -0.5f, 0, 0, 0, 0, 0, 0, 0, 0, -0.1f), QuirkKeyframes.rest(24) };
	/** Reality Rift: both hands tear the air apart. */
	private static final float[][] RIFT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.6f, 0.4f, 0, -1.6f, -0.4f, 0, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(9, -1.45f, -0.7f, 0.3f, -1.45f, 0.7f, -0.3f, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(16) };
	/** Restore: hands to the chest, then opened upward. */
	private static final float[][] RESTORE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -1.2f, -0.6f, 0, -1.2f, 0.6f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.2f),
			QuirkKeyframes.f(12, -2.5f, 0, 0.6f, -2.5f, 0, -0.6f, -0.1f, 0, 0, 0, 0, 0, 0, 0, -0.4f), QuirkKeyframes.rest(20) };
	/** Rewind: one hand turning a slow circle. */
	private static final float[][] REWIND = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.5f, 0.5f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(7, -1.9f, -0.2f, -0.1f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.f(11, -1.3f, -0.4f, 0.4f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.rest(16) };
	/** Cosmic Shockwave: arms swept wide from the chest. */
	private static final float[][] SHOCKWAVE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.4f, -0.8f, 0, -1.4f, 0.8f, 0, 0.15f, 0, 0, 0, 0, 0, 0, 0, 0.2f),
			QuirkKeyframes.f(6, -1.2f, 0.9f, 0.6f, -1.2f, -0.9f, -0.6f, -0.15f, 0, 0, 0, 0.12f, 0, 0, -0.12f, -0.2f), QuirkKeyframes.rest(14) };
	/** Big Bang: hands clasped at the chest, then flung wide and up, head thrown back. */
	private static final float[][] BANG = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.3f, -0.7f, 0, -1.3f, 0.7f, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(7, -2.7f, 0.3f, 1.0f, -2.7f, -0.3f, -1.0f, -0.25f, 0, 0, 0, 0.15f, 0, 0, -0.15f, -0.6f),
			QuirkKeyframes.f(20, -2.6f, 0.3f, 1.0f, -2.6f, -0.3f, -1.0f, -0.2f, 0, 0, 0, 0.15f, 0, 0, -0.15f, -0.5f), QuirkKeyframes.rest(28) };
	/** The motion is denied: a slump, arms dropped. */
	private static final float[][] DENIED = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.15f, 0, 0.1f, 0.15f, 0, -0.1f, 0.15f, 0, 0, 0, 0, 0, 0, 0, 0.6f),
			QuirkKeyframes.f(12, 0.15f, 0, 0.1f, 0.15f, 0, -0.1f, 0.15f, 0, 0, 0, 0, 0, 0, 0, 0.55f), QuirkKeyframes.rest(18) };
	/** The opening debate is over: he straightens up, arms out. */
	private static final float[][] SEATED = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.2f, 0, 0.7f, -0.2f, 0, -0.7f, -0.1f, 0, 0, 0, 0, 0, 0, 0, -0.3f),
			QuirkKeyframes.rest(14) };
	/** v0.17.2 revamp, Argue: hands open and out, head up -- Ben pleading his case. */
	private static final float[] ARGUE_POSE = QuirkKeyframes.f(0, -1.0f, -0.4f, 0.35f, -1.0f, 0.4f, -0.35f, -0.05f, 0, 0, 0, 0.06f, 0, 0, -0.06f, -0.25f);

	// ---------------------------------------------------------------- first-person clips
	private static final FirstPersonClip FP_DEBATE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, 25, 0, 10, 0, -0.25f, 0.1f),
			FirstPersonClip.f(26, 25, 0, 10, 0, -0.25f, 0.1f), FirstPersonClip.f(30, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_POINT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -18, 12, 0, 0.08f, 0.15f, -0.3f),
			FirstPersonClip.f(8, -18, 12, 0, 0.08f, 0.15f, -0.3f), FirstPersonClip.f(13, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_PALMS = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, 22, 0, 0.12f, 0.18f, -0.3f),
			FirstPersonClip.f(16, -20, 22, 0, 0.12f, 0.18f, -0.3f), FirstPersonClip.f(21, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_THROW = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -70, 10, 0, 0.05f, 0.45f, 0.15f),
			FirstPersonClip.f(7, -10, 20, 0, 0.1f, 0.05f, -0.35f), FirstPersonClip.f(13, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_PRESS = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -60, 15, 0, 0.05f, 0.4f, 0f),
			FirstPersonClip.f(7, 30, 15, 0, 0.05f, -0.35f, -0.1f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SNAP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -35, 20, 20, 0.1f, 0.25f, -0.15f),
			FirstPersonClip.f(4, -30, 20, -10, 0.1f, 0.22f, -0.15f), FirstPersonClip.f(9, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_HALT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -40, 25, -15, 0.1f, 0.3f, -0.3f),
			FirstPersonClip.f(18, -40, 25, -15, 0.1f, 0.3f, -0.3f), FirstPersonClip.f(24, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_TEAR = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, 35, 0, 0.25f, 0.15f, -0.3f),
			FirstPersonClip.f(9, -20, -15, 0, -0.1f, 0.15f, -0.3f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_RISE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -15, 30, 0, 0.2f, 0.05f, -0.1f),
			FirstPersonClip.f(12, -75, 10, 0, 0.05f, 0.45f, 0f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_CIRCLE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -25, 15, 0, 0.15f, 0.2f, -0.25f),
			FirstPersonClip.f(7, -45, 0, 0, 0f, 0.35f, -0.25f), FirstPersonClip.f(11, -25, -10, 0, -0.1f, 0.2f, -0.25f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SWEEP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, 35, 0, 0.25f, 0.1f, -0.2f),
			FirstPersonClip.f(6, -15, -30, 0, -0.25f, 0.1f, -0.2f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_BANG = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -25, 35, 0, 0.25f, 0.1f, -0.15f),
			FirstPersonClip.f(7, -80, -25, 0, -0.2f, 0.5f, 0f), FirstPersonClip.f(20, -78, -25, 0, -0.2f, 0.5f, 0f), FirstPersonClip.f(28, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SLUMP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 30, 0, 0, 0, -0.3f, 0.1f),
			FirstPersonClip.f(12, 30, 0, 0, 0, -0.3f, 0.1f), FirstPersonClip.f(18, 0, 0, 0, 0, 0, 0)).both();

	private AlienXClient() {
	}

	/** One line in {@code OmnitrixClient.initialize}. */
	public static void register() {
		AlienClients.register(Aliens.ALIEN_X.id(), new AlienXClient());
		AlienXFx.init();
	}

	/** His skin's own black (the body). */
	private static final float[] BLACK = AlienHorns.pixel(21, 22, 64);

	/**
	 * v0.17.2 (user: "add the head spikes for alien x please i think itll look good, make it the same black as his skin"): his three
	 * horns in his skin's black -- a tall one out of the middle of the crown sweeping back then curling forward like a crescent, and
	 * a short one either side angled up and out, curving in.
	 */
	@Override
	public void renderExtras(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light,
			net.minecraft.client.player.AbstractClientPlayer p, net.minecraft.client.model.PlayerModel<net.minecraft.client.player.AbstractClientPlayer> model, float partial,
			float age) {
		var vc = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(SKIN));
		com.projecthero.mod.client.omnitrix.AlienParts.on(pose, model.head, () -> {
			pose.pushPose();
			pose.translate(0f, -8f / 16f, 0.5f / 16f);
			AlienHorns.horn(pose, vc, light, 7, 1.3f, 1.5f, -50f, 0f, 21f, BLACK);
			pose.popPose();
			for (int side = -1; side <= 1; side += 2) {
				pose.pushPose();
				pose.translate(side * 2.9f / 16f, -7.6f / 16f, -0.6f / 16f);
				AlienHorns.horn(pose, vc, light, 4, 1.3f, 1.2f, -5f, side * 32f, -12f, BLACK);
				pose.popPose();
			}
		});
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

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & AlienXKit.F_DEBATE) != 0 && animId == AlienXKit.ANIM_ARGUE && ticks < 14) {
			QuirkKeyframes.apply(m, ARGUE_POSE, 1f);
			float sh = 0.12f * Mth.sin(age * 0.9f);
			m.rightArm.xRot += sh;
			m.leftArm.xRot -= sh;
			return true;
		}
		if ((s.kitFlags & AlienXKit.F_DEBATE) != 0) {
			// still as a statue but for a faint tremble -- the argument going on inside
			QuirkKeyframes.apply(m, DEBATE_POSE, 1f);
			float tr = 0.025f * Mth.sin(age * 1.7f);
			m.rightArm.zRot += tr;
			m.leftArm.zRot -= tr;
			m.head.zRot = 0.06f * Mth.sin(age * 0.23f);
			return true;
		}
		float[][] clip = switch (animId) {
			case AlienXKit.ANIM_BOLT -> BOLT;
			case AlienXKit.ANIM_RAY -> RAY;
			case AlienXKit.ANIM_HURL -> HURL;
			case AlienXKit.ANIM_CRUSH -> CRUSH;
			case AlienXKit.ANIM_BLINK -> BLINK;
			case AlienXKit.ANIM_FLIGHT -> FLIGHT;
			case AlienXKit.ANIM_STOP -> STOP;
			case AlienXKit.ANIM_RIFT -> RIFT;
			case AlienXKit.ANIM_RESTORE -> RESTORE;
			case AlienXKit.ANIM_REWIND -> REWIND;
			case AlienXKit.ANIM_SHOCKWAVE -> SHOCKWAVE;
			case AlienXKit.ANIM_BANG -> BANG;
			case AlienXKit.ANIM_DENIED -> DENIED;
			case AlienXKit.ANIM_SEATED -> SEATED;
			default -> null;
		};
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		if (!played && (s.kitFlags & AlienXKit.F_FLIGHT) != 0 && !p.onGround()) {
			// floating: legs together, arms a little out
			m.rightLeg.xRot = 0.1f;
			m.leftLeg.xRot = 0.1f;
			m.rightArm.zRot = 0.25f;
			m.leftArm.zRot = -0.25f;
			return true;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case AlienXKit.ANIM_DEBATE, AlienXKit.ANIM_ARGUE -> FP_DEBATE;
			case AlienXKit.ANIM_BOLT, AlienXKit.ANIM_HURL -> animId == AlienXKit.ANIM_HURL ? FP_THROW : FP_POINT;
			case AlienXKit.ANIM_RAY -> FP_PALMS;
			case AlienXKit.ANIM_CRUSH -> FP_PRESS;
			case AlienXKit.ANIM_BLINK -> FP_SNAP;
			case AlienXKit.ANIM_FLIGHT, AlienXKit.ANIM_RESTORE, AlienXKit.ANIM_SEATED -> FP_RISE;
			case AlienXKit.ANIM_STOP -> FP_HALT;
			case AlienXKit.ANIM_RIFT -> FP_TEAR;
			case AlienXKit.ANIM_REWIND -> FP_CIRCLE;
			case AlienXKit.ANIM_SHOCKWAVE -> FP_SWEEP;
			case AlienXKit.ANIM_BANG -> FP_BANG;
			case AlienXKit.ANIM_DENIED -> FP_SLUMP;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- the HUD: the voices' favour, the debate row

	@Override
	public int hudHeight() {
		return 30;
	}

	/**
	 * Three meter rows over the name: Serena's and Bellicus's Council Favour (v0.17.2 revamp), then "Debate" with the debate's
	 * progress and who is speaking (Ben's motion in green, Serena in pale blue, Bellicus in red) -- and once a voice has spoken,
	 * how it voted -- or "Council" with an empty bar and "seated" when nobody argues.
	 */
	@Override
	public void hud(GuiGraphics g, LocalPlayer p, int x, int y, int w, float partial) {
		Minecraft mc = Minecraft.getInstance();
		OmnitrixState s = Omnitrix.state(p);
		long now = p.level().getGameTime();
		int fs = AlienXKit.favour(p, true), fb = AlienXKit.favour(p, false);
		OmnitrixHud.meter(g, mc, x, y - 26, Component.translatable("projecthero.omnitrix.alien_x.voice.serena"), fs / 100f, 0xFF6FC8F0,
				String.valueOf(fs), 0xFF9FE8FF);
		OmnitrixHud.meter(g, mc, x, y - 16, Component.translatable("projecthero.omnitrix.alien_x.voice.bellicus"), fb / 100f, 0xFFE04030,
				String.valueOf(fb), 0xFFFF7060);
		boolean debating = (s.kitFlags & AlienXKit.F_DEBATE) != 0;
		if (!debating) {
			OmnitrixHud.meter(g, mc, x, y - 6, Component.translatable("hud.projecthero.omnitrix.alien_x.council"), 0f, 0xFF7CFF6A,
					Component.translatable("hud.projecthero.omnitrix.alien_x.seated").getString(), 0xFF9A9A9A);
			return;
		}
		long start = s.readyAt(AlienXKit.ID + "." + AlienXKit.T_DEBATE_START);
		long end = s.readyAt(AlienXKit.ID + "." + AlienXKit.T_DEBATE_END);
		float elapsed = now - start + partial;
		float frac = end > start ? Mth.clamp(elapsed / (end - start), 0f, 1f) : 1f;
		boolean opening = (s.kitFlags & AlienXKit.F_OPENING) != 0;
		boolean serenaFirst = s.readyAt(AlienXKit.ID + ".debate_first") != 2L;
		int who = AlienXKit.speaker(opening, serenaFirst, elapsed);
		String name = Component.translatable("projecthero.omnitrix.alien_x.voice." + (who == 0 ? "ben" : who == 1 ? "serena" : "bellicus")).getString();
		int col = who == 0 ? BEN_COLOUR : who == 1 ? SERENA_COLOUR : BELLICUS_COLOUR;
		// the bar blends between the two voices as the argument swings
		int bar = who == 1 ? 0xFF6FC8F0 : who == 2 ? 0xFFE04030 : 0xFF5CD04A;
		// v0.17.2 revamp: once a voice has spoken, its vote shows next to its name
		int votes = AlienXKit.votes(p);
		if (!opening && votes >= 0 && who != 0) {
			boolean yes = (votes & (who == 1 ? 1 : 2)) != 0;
			name = name + " " + Component.translatable(yes ? "hud.projecthero.omnitrix.alien_x.aye" : "hud.projecthero.omnitrix.alien_x.nay").getString();
		}
		OmnitrixHud.meter(g, mc, x, y - 6, Component.translatable("hud.projecthero.omnitrix.alien_x.debate"), frac, bar, name, col);
	}
}
