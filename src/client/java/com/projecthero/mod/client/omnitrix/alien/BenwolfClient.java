package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.benwolf.BenwolfKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.3 Benwolf's client kit (user: "make aliens based on their abilities and stuff they do in the show please, replace the pixel
 * omnitrix on their skin with our omnitrix badge please"). The user's skin on the player body (x1.12), its painted belt Omnitrix painted
 * out ({@code scratchpad/omnitrix_new/gen_skins.js}) and the mod's badge on his belt buckle in its place. On the head: a wolf's muzzle
 * and pointed ears in the skin's own fur -- and the muzzle is the Loboan jaw: four quarters, hinged at the face, that split open like a
 * flower for every howl ({@link BenwolfKit#jawOpen}), showing the throat, the fangs on each quarter and the glow of the sound building,
 * with rings of sound rolling out of it. Tracking Senses outlines every creature for him alone; the HUD shows the Shockwave's charge
 * and the senses' time.
 */
public final class BenwolfClient implements AlienClientKit {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/benwolf.png");
	/** The belt badge: where the skin painted it, the buckle (body texels 23..24 x 29..30 = body y 9..11). */
	public static final float[] BADGE = { 0f, 9.9f, -2.3f, 2.6f };

	/** Skin texels: the black fur, the grey of his muzzle and nose. */
	private static final float[] FUR = AlienHorns.pixel(10, 9, 64), GREY = AlienHorns.pixel(11, 12, 64), FUR_EDGE = AlienHorns.pixel(8, 9, 64);
	private static final int THROAT = 0xFF2A0A10, GUM = 0xFF6A1A22, FANG = 0xFFF2F0E2;

	public static void register() {
		AlienClients.register(Aliens.BENWOLF.id(), new BenwolfClient());
	}

	// ---------------------------------------------------------------- poses (QuirkKeyframes: tick, rArm xyz, lArm xyz, body xy, rLeg x, lLeg x, head x, crouch)
	private static float[] g(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float by, float rlx, float llx, float hx, float crouch) {
		return QuirkKeyframes.fc(t, rax, ray, raz, lax, lay, laz, bx, by, rlx, 0, 0, llx, 0, 0, hx, crouch);
	}

	private static float[] rest(float t) {
		return QuirkKeyframes.restC(t);
	}

	private static float[] at(float t, float[] frame) {
		float[] o = frame.clone();
		o[0] = t;
		return o;
	}

	/** Claw Swipe: a raking slash from high right down across. */
	private static final float[][] CLAW = { rest(0), g(2, -2.6f, 0.4f, 0.4f, 0.3f, 0, -0.3f, 0.1f, 0.35f, -0.2f, 0.2f, 0, 0.1f),
			g(5, -0.7f, -0.6f, -0.2f, 0.35f, 0, -0.3f, 0.25f, -0.45f, -0.2f, 0.2f, 0.1f, 0.15f), rest(10) };
	/** Pounce: low and coiled, then flung forward claws first, legs trailing. */
	private static final float[] POUNCE_AIR = g(0, -2.4f, 0.2f, 0.2f, -2.4f, -0.2f, -0.2f, 0.45f, 0, 0.7f, 0.5f, -0.2f, 0);
	private static final float[][] POUNCE = { rest(0), g(2, 0.6f, 0, 0.3f, 0.6f, 0, -0.3f, 0.5f, 0, -0.4f, 0.4f, 0.2f, 0.9f), at(5, POUNCE_AIR), at(30, POUNCE_AIR), rest(34) };
	/** Landing: a crouch on all fours, claws down. */
	private static final float[][] LAND = { rest(0), g(2, -1.2f, 0, 0.25f, -1.2f, 0, -0.25f, 0.55f, 0, -0.5f, 0.5f, 0.3f, 1f), rest(10) };
	/** Sonic Howl: chest out, head thrown up and forward, arms flung back and wide. */
	private static final float[] HOWL_HELD = g(0, 0.8f, 0, 0.9f, 0.8f, 0, -0.9f, -0.25f, 0, -0.3f, 0.35f, -0.15f, 0.15f);
	private static final float[][] HOWL = { rest(0), g(2, 0.3f, 0, 0.5f, 0.3f, 0, -0.5f, 0.2f, 0, 0, 0, 0.3f, 0.2f), at(4, HOWL_HELD), at(12, HOWL_HELD), rest(18) };
	/** Focused Howl: low and braced, the head driven forward along the beam, arms pinned back. */
	/** v0.17.4 Disorienting Howl: crouched low, head swaying side to side as the howl wavers (the sway is added in {@link #pose}). */
	private static final float[] DISORIENT_HELD = g(0, 0.7f, 0, 0.8f, 0.7f, 0, -0.8f, 0.3f, 0, -0.35f, 0.35f, 0.05f, 0.5f);
	private static final float[][] DISORIENT = { rest(0), at(3, DISORIENT_HELD), at(20, DISORIENT_HELD), rest(26) };
	/** v0.17.4 Wall Bound: a twisting kick off the wall, legs driving, arms flung up and back. */
	private static final float[][] BOUND = { rest(0), g(2, 0.8f, 0, 0.5f, 0.8f, 0, -0.5f, 0.3f, 0.6f, -0.9f, 0.6f, -0.2f, 0.4f),
			g(6, -2.4f, 0, 0.4f, -2.4f, 0, -0.4f, -0.2f, -0.3f, 0.5f, -0.4f, -0.3f, 0), g(14, -1.0f, 0, 0.4f, -1.0f, 0, -0.4f, 0.1f, 0, 0.2f, -0.2f, 0, 0), rest(20) };
	/** v0.17.4 Hunt Sprint (held): down on all fours, body pitched forward, arms reaching for the ground (the gallop is added in {@link #pose}). */
	private static final float[] SPRINT_HELD = g(0, -1.25f, 0, 0.1f, -1.25f, 0, -0.1f, 1.15f, 0, 0.35f, -0.35f, -1.0f, 0.5f);
	/** v0.17.4 Moon Howl: head thrown back to the sky, chest out, arms spread low. */
	private static final float[] MOON_HELD = g(0, 0.5f, 0, 0.7f, 0.5f, 0, -0.7f, -0.35f, 0, 0.05f, -0.05f, -0.9f, 0.1f);
	private static final float[][] MOON = { rest(0), at(4, MOON_HELD), at(28, MOON_HELD), rest(34) };
	/** v0.17.4 Scent Mark: head down and forward, sniffing, one hand pointing it out. */
	private static final float[][] SCENT = { rest(0), g(3, -1.4f, 0.2f, 0, 0.2f, 0, -0.2f, 0.35f, -0.2f, -0.2f, 0.2f, 0.45f, 0.3f),
			g(12, -1.35f, 0.2f, 0, 0.2f, 0, -0.2f, 0.3f, -0.15f, -0.2f, 0.2f, 0.35f, 0.25f), rest(18) };
	/** v0.17.4 Maul: both claws up over his head, then driven down onto it. */
	private static final float[][] MAUL = { rest(0), g(2, -2.8f, 0.2f, 0.2f, -2.8f, -0.2f, -0.2f, -0.25f, 0, 0.1f, -0.1f, -0.3f, 0),
			g(5, -0.8f, 0, 0.2f, -0.8f, 0, -0.2f, 0.6f, 0, -0.5f, 0.5f, 0.3f, 0.8f), g(12, -0.7f, 0, 0.2f, -0.7f, 0, -0.2f, 0.5f, 0, -0.4f, 0.4f, 0.25f, 0.6f), rest(18) };
	/** The Shockwave's charge: hunched over, arms drawn in, head down, gathering it (held while Z is). */
	private static final float[] CHARGE_HELD = g(0, -0.5f, 0.6f, 0.2f, -0.5f, -0.6f, -0.2f, 0.45f, 0, -0.3f, 0.3f, 0.45f, 0.8f);
	private static final float[][] CHARGE = { rest(0), at(6, CHARGE_HELD), at(4000, CHARGE_HELD) };
	/** The Shockwave: everything flung open at once, head back, up on his toes. */
	private static final float[][] SHOCKWAVE = { at(0, CHARGE_HELD), g(2, -0.6f, 0, 1.7f, -0.6f, 0, -1.7f, -0.4f, 0, 0.1f, -0.1f, -0.55f, 0),
			g(12, -0.5f, 0, 1.55f, -0.5f, 0, -1.55f, -0.35f, 0, 0.1f, -0.1f, -0.45f, 0), rest(20) };
	/** Wolf Leap: bounding off both legs, arms reaching ahead. */
	private static final float[][] LEAP = { rest(0), g(2, 0.9f, 0, 0.3f, 0.9f, 0, -0.3f, 0.5f, 0, 0.5f, -0.6f, -0.3f, 0.6f), g(6, -2.2f, 0, 0.3f, -2.2f, 0, -0.3f, 0.3f, 0, 0.6f, 0.3f, -0.3f, 0),
			g(14, -1.2f, 0, 0.5f, -1.2f, 0, -0.5f, 0.2f, 0, -0.4f, 0.3f, 0, 0), rest(20) };
	/** Frenzy: both arms raking in turn, six times. */
	private static final float[][] FRENZY = { rest(0), g(2, -2.4f, 0.4f, 0.3f, 0.2f, 0, -0.3f, 0.2f, 0.3f, -0.2f, 0.2f, 0.1f, 0.3f),
			g(4, 0.1f, 0, 0.3f, -2.4f, -0.4f, -0.3f, 0.2f, -0.3f, -0.2f, 0.2f, 0.1f, 0.3f), g(6, -2.4f, 0.4f, 0.3f, 0.2f, 0, -0.3f, 0.2f, 0.3f, -0.2f, 0.2f, 0.1f, 0.3f),
			g(8, 0.1f, 0, 0.3f, -2.4f, -0.4f, -0.3f, 0.2f, -0.3f, -0.2f, 0.2f, 0.1f, 0.3f), g(10, -2.4f, 0.4f, 0.3f, 0.2f, 0, -0.3f, 0.2f, 0.3f, -0.2f, 0.2f, 0.1f, 0.3f),
			g(12, 0.1f, 0, 0.3f, -2.4f, -0.4f, -0.3f, 0.2f, -0.3f, -0.2f, 0.2f, 0.1f, 0.3f), g(16, -1.0f, 0, 0.3f, -1.0f, 0, -0.3f, 0.2f, 0, -0.2f, 0.2f, 0.1f, 0.2f), rest(22) };
	/** Tracking Senses: head up, sniffing the air side to side. */
	private static final float[][] SENSES = { rest(0), g(4, 0, 0, 0.15f, 0, 0, -0.15f, -0.1f, 0.25f, 0, 0, -0.4f, 0), g(10, 0, 0, 0.15f, 0, 0, -0.15f, -0.1f, -0.25f, 0, 0, -0.35f, 0),
			g(15, 0, 0, 0.1f, 0, 0, -0.1f, 0, 0, 0, 0, -0.2f, 0), rest(20) };
	/** Loboan Claws: a quick hooking swipe. */
	private static final float[][] PUNCH = { rest(0), g(2, -1.8f, -0.4f, 0.2f, 0.2f, 0, -0.2f, 0.15f, -0.4f, -0.2f, 0.2f, 0, 0), rest(7) };

	private static float[] fp(float t, float pitch, float yaw, float roll, float x, float y, float z) {
		return FirstPersonClip.f(t, pitch, yaw, roll, x, y, z);
	}

	private static final FirstPersonClip FP_CLAW = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(2, -50, 20, 30, 0.2f, 0.3f, -0.1f), fp(5, 10, -40, -20, -0.3f, -0.1f, -0.2f),
			fp(10, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_POUNCE = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(5, -30, 10, 0, 0.1f, 0.2f, -0.4f), fp(30, -30, 10, 0, 0.1f, 0.2f, -0.4f),
			fp(34, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_HOWL = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(4, 40, -20, 0, -0.1f, -0.4f, 0.15f), fp(12, 40, -20, 0, -0.1f, -0.4f, 0.15f),
			fp(18, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_CHARGE = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(6, -10, 30, 10, 0.1f, 0f, -0.1f), fp(4000, -10, 30, 10, 0.1f, 0f, -0.1f)).both();
	private static final FirstPersonClip FP_SHOCKWAVE = FirstPersonClip.of(fp(0, -10, 30, 10, 0.1f, 0f, -0.1f), fp(2, -20, -44, -34, -0.24f, 0.14f, -0.14f),
			fp(12, -20, -40, -30, -0.2f, 0.14f, -0.14f), fp(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_LEAP = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(2, 30, 0, 0, 0, -0.4f, 0.1f), fp(6, -30, 0, 0, 0.05f, 0.2f, -0.3f),
			fp(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_FRENZY = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(2, -50, 20, 30, 0.2f, 0.3f, -0.1f), fp(4, 10, -40, -20, -0.3f, -0.1f, -0.2f),
			fp(6, -50, 20, 30, 0.2f, 0.3f, -0.1f), fp(8, 10, -40, -20, -0.3f, -0.1f, -0.2f), fp(10, -50, 20, 30, 0.2f, 0.3f, -0.1f), fp(12, 10, -40, -20, -0.3f, -0.1f, -0.2f),
			fp(22, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(2, -30, 20, 20, 0.15f, 0.15f, -0.3f), fp(7, 0, 0, 0, 0, 0, 0));

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public float[] badge() {
		return BADGE;
	}

	@Override
	public boolean glows(LocalPlayer viewer, Entity e) {
		return BenwolfKit.senses(viewer, e) || BenwolfKit.scented(viewer, e); // v0.17.4: his Scent Mark, at any range
	}

	private static final FirstPersonClip FP_SPRINT = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(4, 40, 0, 0, 0, -0.5f, 0.15f), fp(4000, 40, 0, 0, 0, -0.5f, 0.15f))
			.both();
	private static final FirstPersonClip FP_MAUL = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(2, -70, 10, 0, 0.05f, 0.55f, 0f), fp(5, 30, 0, 0, 0, -0.3f, -0.1f),
			fp(14, 0, 0, 0, 0, 0, 0)).both();

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		int flags = Omnitrix.state(p).kitFlags;
		if ((flags & BenwolfKit.F_SPRINT) != 0) {
			QuirkKeyframes.apply(m, SPRINT_HELD, Mth.clamp(ticks / 4f, 0f, 1f));
			// the gallop: forelegs and hind legs reaching and pushing in turn, quicker with his speed
			float gait = age * (0.55f + p.walkAnimation.speed() * 0.35f);
			float swing = Mth.sin(gait) * 0.55f * Mth.clamp(p.walkAnimation.speed() * 2f, 0.25f, 1f);
			m.rightArm.xRot += swing;
			m.leftArm.xRot += swing * 0.8f;
			m.rightLeg.xRot -= swing;
			m.leftLeg.xRot -= swing * 0.8f;
			return true;
		}
		if ((flags & BenwolfKit.F_CHARGE) != 0) {
			QuirkKeyframes.apply(m, CHARGE_HELD, Mth.clamp(ticks / 6f, 0f, 1f));
			m.head.xRot += Mth.sin(age * 1.3f) * 0.03f; // the strain of it
			return true;
		}
		float[][] clip = switch (animId) {
			case BenwolfKit.ANIM_CLAW -> CLAW;
			case BenwolfKit.ANIM_POUNCE -> (flags & BenwolfKit.F_POUNCE) != 0 ? POUNCE : null;
			case BenwolfKit.ANIM_LAND -> LAND;
			case BenwolfKit.ANIM_HOWL -> HOWL;
			case BenwolfKit.ANIM_DISORIENT -> DISORIENT;
			case BenwolfKit.ANIM_BOUND -> BOUND;
			case BenwolfKit.ANIM_MOON -> MOON;
			case BenwolfKit.ANIM_SCENT -> SCENT;
			case BenwolfKit.ANIM_MAUL -> MAUL;
			case BenwolfKit.ANIM_CHARGE -> null;
			case BenwolfKit.ANIM_SHOCKWAVE -> SHOCKWAVE;
			case BenwolfKit.ANIM_LEAP -> LEAP;
			case BenwolfKit.ANIM_FRENZY -> FRENZY;
			case BenwolfKit.ANIM_SENSES -> SENSES;
			case BenwolfKit.ANIM_PUNCH -> PUNCH;
			default -> null;
		};
		boolean on = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		if (on && animId == BenwolfKit.ANIM_DISORIENT && ticks > 2f && ticks < 22f) {
			m.head.yRot += Mth.sin(ticks * 0.9f) * 0.35f; // the howl wavers
			m.body.yRot += Mth.sin(ticks * 0.9f) * 0.1f;
		}
		return on;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case BenwolfKit.ANIM_CLAW -> FP_CLAW;
			case BenwolfKit.ANIM_POUNCE -> FP_POUNCE;
			case BenwolfKit.ANIM_HOWL, BenwolfKit.ANIM_DISORIENT, BenwolfKit.ANIM_MOON -> FP_HOWL;
			case BenwolfKit.ANIM_SPRINT -> FP_SPRINT;
			case BenwolfKit.ANIM_MAUL -> FP_MAUL;
			case BenwolfKit.ANIM_BOUND -> FP_LEAP;
			case BenwolfKit.ANIM_SCENT -> FP_PUNCH;
			case BenwolfKit.ANIM_CHARGE -> FP_CHARGE;
			case BenwolfKit.ANIM_SHOCKWAVE -> FP_SHOCKWAVE;
			case BenwolfKit.ANIM_LEAP -> FP_LEAP;
			case BenwolfKit.ANIM_FRENZY -> FP_FRENZY;
			case BenwolfKit.ANIM_PUNCH -> FP_PUNCH;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- the head: muzzle, four-way jaw, ears

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		OmnitrixState s = Omnitrix.state(p);
		float t = p.level().getGameTime() + partial - s.animStart;
		float open = BenwolfKit.jawOpen(p, t);
		boolean howling = s.animId == BenwolfKit.ANIM_HOWL || s.animId == BenwolfKit.ANIM_DISORIENT || s.animId == BenwolfKit.ANIM_MOON
				|| s.animId == BenwolfKit.ANIM_SHOCKWAVE;
		boolean charging = (s.kitFlags & BenwolfKit.F_CHARGE) != 0;
		AlienParts.on(pose, model.head, () -> {
			head(pose, buffers, light, open);
			if (open > 0.05f && (howling || charging)) {
				sound(pose, buffers, open, t, age, howling && s.animId != BenwolfKit.ANIM_SHOCKWAVE, false, charging);
			}
		});
	}

	/** The muzzle's centre line on the face (head-bone pixels): its middle height and where it meets the face. */
	private static final float CY = -2.4f, FACE = -4.0f, TIP = -7.4f, HALF_W = 2.3f, HALF_H = 1.95f;

	/** The four-way jaw: it grows out of his face and splits open by {@code open} (nothing while it is shut). */
	private static void head(PoseStack pose, MultiBufferSource buffers, int light, float open) {
		// fetched again before every box: the skin and the flat colour share the immediate buffer, and switching ends the other
		java.util.function.Supplier<VertexConsumer> skin = () -> buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN));
		// v0.17.3 (user: "remove the snout and ears from blitzwolfer"): no ears, and no snout while the mouth is shut -- his face is the
		// skin's own; the four-way jaw only grows out of his face as it opens for a howl
		if (open <= 0.02f) {
			return;
		}
		final float TIP = FACE + (BenwolfClient.TIP - FACE) * Math.min(1f, open * 1.6f);
		float ang = open * 72f;
		java.util.function.Supplier<VertexConsumer> flat = () -> PyroDraw.solidBuffer(buffers);
		if (open > 0.02f) {
			// the throat behind the quarters: dark, with the gums round it
			box(pose, flat.get(), -HALF_W + 0.4f, CY - HALF_H + 0.4f, FACE - 0.6f, HALF_W - 0.4f, CY + HALF_H - 0.4f, FACE - 0.05f, null, GUM, light);
			box(pose, flat.get(), -HALF_W + 0.9f, CY - HALF_H + 0.9f, FACE - 0.7f, HALF_W - 0.9f, CY + HALF_H - 0.9f, FACE - 0.1f, null, THROAT, light);
		}
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sy = -1; sy <= 1; sy += 2) {
				pose.pushPose();
				// hinged at the face, on the quarter's outer edge: it swings out along its own diagonal
				float px = sx * HALF_W * 0.5f, py = CY + sy * HALF_H * 0.5f;
				pose.translate(px / 16f, py / 16f, FACE / 16f);
				float ax = sy, ay = -sx;
				float len = Mth.sqrt(ax * ax + ay * ay);
				pose.mulPose(Axis.of(new org.joml.Vector3f(ax / len, ay / len, 0f)).rotationDegrees(ang));
				pose.translate(-px / 16f, -py / 16f, -FACE / 16f);
				float x0 = Math.min(0, sx * HALF_W), x1 = Math.max(0, sx * HALF_W);
				float y0 = Math.min(CY, CY + sy * HALF_H), y1 = Math.max(CY, CY + sy * HALF_H);
				// the quarter, tapering a little to the tip: a root block and a front block
				box(pose, skin.get(), x0, y0, (FACE + TIP) * 0.5f, x1, y1, FACE, sy < 0 ? FUR : FUR_EDGE, light);
				float ix0 = sx < 0 ? x0 + 0.3f : x0, ix1 = sx < 0 ? x1 : x1 - 0.3f;
				float iy0 = sy < 0 ? y0 + 0.3f : y0, iy1 = sy < 0 ? y1 : y1 - 0.3f;
				box(pose, skin.get(), ix0, iy0, TIP, ix1, iy1, (FACE + TIP) * 0.5f, sy < 0 ? FUR : FUR_EDGE, light);
				if (sy < 0) {
					// the nose, split down the middle with the top quarters
					box(pose, skin.get(), sx < 0 ? -0.9f : 0f, CY - HALF_H - 0.25f, TIP - 0.2f, sx < 0 ? 0f : 0.9f, CY - HALF_H + 0.7f, TIP + 0.9f, GREY, light);
				}
				if (open > 0.02f) {
					// fangs along the quarter's inner edges, near the tip
					float fy = sy < 0 ? CY - 0.05f : CY + 0.05f;
					for (int k = 0; k < 2; k++) {
						float fx = sx * (0.5f + k * 1.0f);
						box(pose, flat.get(), fx - 0.22f, sy < 0 ? fy - 0.1f : fy - 0.65f, TIP + 0.3f + k * 0.9f, fx + 0.22f, sy < 0 ? fy + 0.65f : fy + 0.1f, TIP + 0.75f + k * 0.9f,
								null, FANG, light);
					}
					float fx = sx < 0 ? -0.15f : 0.15f;
					box(pose, flat.get(), fx - 0.2f, sy < 0 ? CY - 1.2f : CY + 0.6f, TIP + 0.35f, fx + 0.2f, sy < 0 ? CY - 0.6f : CY + 1.2f, TIP + 0.8f, null, FANG, light);
				}
				pose.popPose();
			}
		}
	}

	/** One box: on the skin ({@code uv} a texel) or flat-coloured ({@code uv} null, {@code argb}). */
	private static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float[] uv, int light) {
		AlienParts.box(pose, vc, x0, y0, z0, x1, y1, z1, uv[0], uv[1], uv[2], uv[3], 0xFFFFFFFF, light);
	}

	private static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float[] uv, int argb, int light) {
		AlienParts.box(pose, vc, x0, y0, z0, x1, y1, z1, 0.5f, 0.5f, 0.5f, 0.5f, argb, light);
	}

	/**
	 * The sound: a glow building in the split jaw, and rings of it rolling out ahead -- wide for the Sonic Howl, tight for the Focused Howl
	 * ({@code beam}), drawn on the head so they leave his mouth wherever he looks.
	 */
	private static void sound(PoseStack pose, MultiBufferSource buffers, float open, float t, float age, boolean howl, boolean beam, boolean charging) {
		VertexConsumer add = PyroDraw.additive(buffers);
		pose.pushPose();
		pose.translate(0, CY / 16f, (FACE - 0.6f) / 16f);
		PyroDraw.sphere(pose, add, 0.06f + 0.07f * open, PyroDraw.dim(0xFFFFFF, 0.7f * open), PyroDraw.dim(BenwolfKit.SONIC, 0.6f * open), 0.1f, age, LightTexture.FULL_BRIGHT);
		pose.popPose();
		if (charging || !howl) {
			return;
		}
		// three rings in flight at a time, each from the jaw out to ~3 blocks, fading
		for (int i = 0; i < 3; i++) {
			float ph = (t * 0.22f + i / 3f) % 1f;
			if (t * 0.22f + i / 3f < 1f / 3f) {
				continue; // none before the first is out
			}
			float k = (1f - ph) * open;
			float r = (0.15f + ph * (beam ? 0.4f : 1.4f));
			pose.pushPose();
			pose.translate(0, CY / 16f, (TIP / 16f) - ph * 3.0f);
			pose.mulPose(Axis.XP.rotationDegrees(90f));
			PyroDraw.annulus(pose, add, r * 0.78f, r, PyroDraw.dim(BenwolfKit.SONIC, k * 0.15f), PyroDraw.dim(0xFFFFFF, k * 0.75f), 0f);
			PyroDraw.annulus(pose, add, r, r * 1.12f, PyroDraw.dim(0xFFFFFF, k * 0.75f), 0xFF000000, 0f);
			pose.popPose();
		}
	}

	// ---------------------------------------------------------------- the HUD: the Shockwave's charge, the senses' time

	@Override
	public int hudHeight() {
		return 10;
	}

	@Override
	public void hud(net.minecraft.client.gui.GuiGraphics g, LocalPlayer p, int x, int y, int w, float partial) {
		Minecraft mc = Minecraft.getInstance();
		OmnitrixState s = Omnitrix.state(p);
		long now = p.level().getGameTime();
		if ((s.kitFlags & BenwolfKit.F_CHARGE) != 0) {
			long start = s.readyAt(BenwolfKit.ID + "." + BenwolfKit.CHARGE_START);
			float k = Mth.clamp((now - start) / (float) BenwolfKit.CHARGE_FULL, 0f, 1f);
			boolean full = k >= 1f;
			com.projecthero.mod.client.omnitrix.OmnitrixHud.meter(g, mc, x, y - 6, Component.translatable("hud.projecthero.omnitrix.benwolf.howl"), k,
					0xFF000000 | BenwolfKit.SONIC, full && (now / 6) % 2 == 0 ? "MAX" : Math.round(k * 100) + "%", full ? 0xFFFFFFFF : 0xFFE2E2E2);
		} else if ((s.kitFlags & BenwolfKit.F_SENSES) != 0) {
			long until = s.readyAt(BenwolfKit.ID + "." + BenwolfKit.SENSES_UNTIL);
			float left = Math.max(0, until - now) / 20f;
			com.projecthero.mod.client.omnitrix.OmnitrixHud.meter(g, mc, x, y - 6, Component.translatable("hud.projecthero.omnitrix.benwolf.senses"),
					left / (BenwolfKit.SENSES_TICKS / 20f), 0xFFC8D2D2, String.format(java.util.Locale.ROOT, "%.0fs", left), 0xFFE2E2E2);
		}
	}
}
