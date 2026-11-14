package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.omnitrix.alien.jetray.RecalFx;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.shockrock.ShockRockKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

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
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Shock Rock's client kit (user: "make aliens based on their abilities and stuff they do in the show please, replace the pixel
 * omnitrix on their skin with our omnitrix badge please"). The user's skin on the player body (x1.1), its painted chest Omnitrix painted
 * out ({@code scratchpad/omnitrix_new/gen_skins.js}) and the mod's badge on his chest in its place. His energy -- every bright cyan texel
 * of the skin -- is drawn again full-bright from {@code shock_rock_glow.png}, so the energy glows and the rock stays matte; Overload burns
 * it brighter and pulsing. The Energy Blade on his right arm, the Shield Construct in front of him, the Rock Burst's chunks flying off
 * and slamming back, and his bolts; the HUD shows his Energy.
 */
public final class ShockRockClient implements AlienClientKit {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/shock_rock.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/omnitrix/shock_rock_glow.png");
	/** The chest badge: where the skin painted it, on the dark rock band across his upper chest (jacket texels 23..24 x 38..39). */
	public static final float[] BADGE = { 0f, 3.0f, -2.3f, 2.6f };
	/** Skin texels: his rock (dark, and a lighter edge). */
	private static final float[] ROCK = AlienHorns.pixel(19, 22, 64), ROCK_LIGHT = AlienHorns.pixel(22, 23, 64);
	private static final int ENERGY = ShockRockKit.ENERGY, HOT = ShockRockKit.ENERGY_HOT;

	private static boolean registered;

	public static void register() {
		AlienClients.register(Aliens.SHOCK_ROCK.id(), new ShockRockClient());
		if (!registered) {
			registered = true;
			// his bolts: a short streak of cyan light with a white core
			OmnitrixRenderers.projectileLook("shock_rock_bolt", (e, partial, pose, buffers) -> {
				Vec3 v = e.getDeltaMovement();
				Vec3 dir = v.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : v.normalize();
				Vec3 c = new Vec3(0, e.getBbHeight() * 0.5, 0);
				float w = Math.max(0.12f, e.size() * 0.45f);
				RecalFx.beam(pose, buffers, c.subtract(dir.scale(0.9)), c, w, 0xFFFFFF, ENERGY, 1f);
				PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), c, w * 2.2f, PyroDraw.dim(ENERGY, 0.6f));
			});
			com.projecthero.mod.client.omnitrix.alien.shockrock.ShockRockFx.register(); // v0.17.4: Blade Wave, Rock Barrage, Energy Lash, Energy Dome
		}
	}

	/** v0.17.4: the skin's rock texels (the Rock Barrage's plates are cut from them). */
	public static float[] rockUv() {
		return ROCK;
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

	/** Energy Blast: a heavy straight punch that fires it. */
	private static final float[][] BLAST = { rest(0), g(2, -1.65f, 0.1f, 0, 0.4f, 0, -0.2f, 0.12f, -0.4f, -0.2f, 0.2f, 0, 0.1f), g(6, -1.5f, 0.1f, 0, 0.3f, 0, -0.2f, 0.06f, -0.3f, -0.1f, 0.1f, 0, 0),
			rest(12) };
	/** Rapid Bolts: both fists pumping, one after the other. */
	private static final float[][] RAPID = { rest(0), g(1, -1.6f, 0.1f, 0, -0.6f, 0, 0, 0.1f, -0.2f, -0.2f, 0.2f, 0, 0.1f), g(3, -0.6f, 0, 0, -1.6f, -0.1f, 0, 0.1f, 0.2f, -0.2f, 0.2f, 0, 0.1f),
			g(5, -1.6f, 0.1f, 0, -0.6f, 0, 0, 0.1f, -0.2f, -0.2f, 0.2f, 0, 0.1f), g(7, -0.6f, 0, 0, -1.6f, -0.1f, 0, 0.1f, 0.2f, -0.2f, 0.2f, 0, 0.1f),
			g(9, -1.6f, 0.1f, 0, -0.6f, 0, 0, 0.1f, -0.2f, -0.2f, 0.2f, 0, 0.1f), rest(15) };
	/** Energy Blade: the arm held out and the blade drawn along it. */
	private static final float[][] BLADE = { rest(0), g(3, -1.4f, -0.6f, 0.3f, -0.6f, 0.6f, 0, 0.1f, 0.3f, 0, 0, 0, 0.1f), g(8, -0.4f, 0, 0.9f, 0.1f, 0, -0.2f, 0, -0.2f, 0, 0, 0, 0),
			rest(14) };
	/** A blade slash: a wide horizontal cut. */
	private static final float[][] SLASH = { rest(0), g(2, -1.5f, 0.9f, 0.4f, 0.3f, 0, -0.3f, 0.1f, 0.5f, -0.2f, 0.2f, 0, 0.1f),
			g(5, -1.4f, -0.9f, -0.2f, 0.3f, 0, -0.3f, 0.15f, -0.55f, -0.2f, 0.2f, 0, 0.15f), rest(10) };
	/** Rock Burst: hunched as the armour loosens, then thrown wide as it flies off, and pulled back in. */
	private static final float[][] BURST = { rest(0), g(3, -0.4f, 0.5f, 0.3f, -0.4f, -0.5f, -0.3f, 0.4f, 0, -0.3f, 0.3f, 0.3f, 0.7f),
			g(6, -0.3f, 0, 1.6f, -0.3f, 0, -1.6f, -0.35f, 0, 0.1f, -0.1f, -0.4f, 0), g(16, -0.3f, 0, 1.5f, -0.3f, 0, -1.5f, -0.3f, 0, 0.1f, -0.1f, -0.35f, 0),
			g(20, -0.5f, 0.4f, 0.4f, -0.5f, -0.4f, -0.4f, 0.3f, 0, 0, 0, 0.2f, 0.4f), rest(26) };
	/** Energy Dash: leaning hard into it, fists back. */
	private static final float[] DASH_HELD = g(0, 0.9f, 0, 0.35f, 0.9f, 0, -0.35f, 0.5f, 0, 0.6f, -0.3f, -0.3f, 0);
	private static final float[][] DASH = { rest(0), at(2, DASH_HELD), at(8, DASH_HELD), rest(14) };
	/** The Shield Construct: the left forearm up across him behind it (held while it lasts). */
	private static final float[] SHIELD_HELD = g(0, -0.3f, 0, 0.1f, -1.45f, 0.75f, 0, 0.1f, 0.15f, -0.15f, 0.15f, 0, 0.2f);
	private static final float[][] SHIELD = { rest(0), at(3, SHIELD_HELD), at(4000, SHIELD_HELD) };
	private static final float[][] SHIELD_END = { at(0, SHIELD_HELD), rest(6) };
	/** Overload: fists clenched at his sides, chest out, head back as the energy flares. */
	private static final float[][] OVERLOAD = { rest(0), g(3, 0.2f, 0, 0.6f, 0.2f, 0, -0.6f, -0.3f, 0, -0.1f, 0.1f, -0.45f, 0.3f),
			g(10, 0.2f, 0, 0.55f, 0.2f, 0, -0.55f, -0.25f, 0, -0.1f, 0.1f, -0.35f, 0.3f), rest(16) };
	/** Rock Fists: a short heavy hook. */
	private static final float[][] PUNCH = { rest(0), g(2, -1.6f, -0.5f, 0.2f, 0.2f, 0, -0.2f, 0.15f, -0.45f, -0.2f, 0.2f, 0, 0), rest(8) };

	private static float[] fp(float t, float pitch, float yaw, float roll, float x, float y, float z) {
		return FirstPersonClip.f(t, pitch, yaw, roll, x, y, z);
	}

	private static final FirstPersonClip FP_BLAST = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(2, -20, 6, 0, 0.05f, 0.12f, -0.45f), fp(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_RAPID = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(1, -20, 6, 0, 0.05f, 0.12f, -0.4f), fp(3, -6, 0, 0, 0, 0.02f, -0.1f),
			fp(5, -20, 6, 0, 0.05f, 0.12f, -0.4f), fp(7, -6, 0, 0, 0, 0.02f, -0.1f), fp(9, -20, 6, 0, 0.05f, 0.12f, -0.4f), fp(15, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_BLADE = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(3, -30, 30, 20, 0.15f, 0.2f, -0.2f), fp(14, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SLASH = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(2, -30, 40, 30, 0.3f, 0.15f, -0.2f), fp(5, -20, -40, -30, -0.3f, 0.1f, -0.3f),
			fp(10, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_BURST = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(3, 30, 0, 0, 0, -0.3f, 0.1f), fp(6, -20, -44, -30, -0.26f, 0.16f, -0.1f),
			fp(16, -20, -40, -28, -0.24f, 0.14f, -0.1f), fp(26, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_DASH = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(2, 40, 0, 0, 0, -0.5f, 0.15f), fp(8, 40, 0, 0, 0, -0.5f, 0.15f), fp(14, 0, 0, 0, 0, 0, 0))
			.both();
	private static final FirstPersonClip FP_OVERLOAD = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(3, 30, -10, 0, -0.05f, -0.35f, 0.1f), fp(10, 30, -10, 0, -0.05f, -0.35f, 0.1f),
			fp(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(2, -16, 10, 0, 0.08f, 0.1f, -0.44f), fp(8, 0, 0, 0, 0, 0, 0));

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public float[] badge() {
		return BADGE;
	}

	// ---------------------------------------------------------------- v0.17.4 poses
	/** Charged Bolt (held): the right fist drawn back to the hip, the left arm out to aim, braced low. */
	private static final float[] CHARGE_HELD = g(0, 0.7f, 0.3f, 0.25f, -1.4f, 0.2f, 0, 0.15f, 0.55f, -0.3f, 0.35f, 0, 0.35f);
	private static final float[][] CHARGE = { rest(0), at(5, CHARGE_HELD), at(4000, CHARGE_HELD) };
	/** The Charged Bolt let go: the fist driven straight out, the body turning through it. */
	private static final float[][] CHARGED = { at(0, CHARGE_HELD), g(2, -1.7f, 0.1f, 0, 0.5f, 0, -0.3f, 0.15f, -0.5f, -0.3f, 0.3f, 0, 0.15f),
			g(7, -1.55f, 0.1f, 0, 0.4f, 0, -0.25f, 0.08f, -0.4f, -0.2f, 0.2f, 0, 0.05f), rest(14) };
	/** Blade Wave: a backhand sweep across the body that throws the crescent off the arm. */
	private static final float[][] WAVE = { rest(0), g(2, -1.4f, 1.1f, 0.6f, 0.3f, 0, -0.3f, 0.05f, 0.6f, -0.2f, 0.2f, 0, 0.1f),
			g(5, -1.5f, -1.0f, -0.3f, 0.3f, 0, -0.3f, 0.12f, -0.6f, -0.2f, 0.2f, 0, 0.15f), rest(11) };
	/** Energy Lash (held): the arm thrown out along the tether, then hauling it in hand over hand (the haul is added in {@link #pose}). */
	private static final float[] LASH_HELD = g(0, -1.5f, 0.1f, 0, -0.9f, -0.1f, 0, -0.15f, 0.25f, 0.25f, -0.35f, 0, 0.2f);
	private static final float[][] LASH = { rest(0), at(3, LASH_HELD), at(4000, LASH_HELD) };
	/** Updraft Jet: arms driven down at his sides, legs together, head up as he rockets off. */
	private static final float[][] UPDRAFT = { rest(0), g(2, 0.25f, 0, 0.35f, 0.25f, 0, -0.35f, -0.1f, 0, 0.05f, -0.05f, -0.5f, 0.4f),
			g(5, 0.15f, 0, 0.5f, 0.15f, 0, -0.5f, -0.15f, 0, 0.1f, 0.1f, -0.45f, 0), g(16, 0.15f, 0, 0.45f, 0.15f, 0, -0.45f, -0.1f, 0, 0.1f, 0.1f, -0.3f, 0), rest(22) };
	/** Rock Barrage: hunched, then both arms flung forward as the plates fly off his chest. */
	private static final float[][] BARRAGE = { rest(0), g(2, -0.4f, 0.5f, 0.3f, -0.4f, -0.5f, -0.3f, 0.3f, 0, -0.2f, 0.2f, 0.2f, 0.3f),
			g(5, -1.5f, -0.5f, 0.6f, -1.5f, 0.5f, -0.6f, -0.2f, 0, -0.3f, 0.3f, -0.2f, 0.1f), g(10, -1.4f, -0.45f, 0.55f, -1.4f, 0.45f, -0.55f, -0.15f, 0, -0.25f, 0.25f, -0.15f, 0),
			rest(16) };
	/** Core Overload Burst (held): hunched round his blazing core, arms clawed in, sinking lower as it builds. */
	private static final float[] CORE_HELD = g(0, -0.5f, 0.6f, 0.35f, -0.5f, -0.6f, -0.35f, 0.4f, 0, -0.35f, 0.35f, 0.35f, 0.8f);
	private static final float[][] CORE = { rest(0), at(6, CORE_HELD), at(4000, CORE_HELD) };
	/** Armour Reform: arms crossed as the plates pull in, then thrown wide as they lock on. */
	private static final float[][] REFORM = { rest(0), g(3, -1.3f, -0.9f, 0, -1.3f, 0.9f, 0, 0.25f, 0, -0.1f, 0.1f, 0.2f, 0.3f),
			g(9, -0.3f, 0, 1.0f, -0.3f, 0, -1.0f, -0.2f, 0, 0, 0, -0.25f, 0), rest(16) };
	/** Energy Channel (held): palms up and out, head back, drinking the energy in. */
	private static final float[] CHANNEL_HELD = g(0, -0.6f, 0.5f, 0.75f, -0.6f, -0.5f, -0.75f, -0.15f, 0, 0, 0, -0.35f, 0.15f);
	private static final float[][] CHANNEL = { rest(0), at(5, CHANNEL_HELD), at(4000, CHANNEL_HELD) };
	/** Energy Dome: both arms swept up and out as the shell rises round him. */
	private static final float[][] DOME = { rest(0), g(3, -1.6f, 0, 0.3f, -1.6f, 0, -0.3f, 0.1f, 0, -0.2f, 0.2f, 0, 0.4f),
			g(8, -2.7f, 0, 0.9f, -2.7f, 0, -0.9f, -0.2f, 0, 0, 0, -0.3f, 0), g(14, -2.6f, 0, 0.9f, -2.6f, 0, -0.9f, -0.15f, 0, 0, 0, -0.25f, 0), rest(20) };

	private static final FirstPersonClip FP_CHARGE = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(5, 30, 10, 0, 0.1f, -0.3f, 0.25f), fp(4000, 30, 10, 0, 0.1f, -0.3f, 0.25f));
	private static final FirstPersonClip FP_CHARGED = FirstPersonClip.of(fp(0, 30, 10, 0, 0.1f, -0.3f, 0.25f), fp(2, -22, 6, 0, 0.05f, 0.14f, -0.5f), fp(14, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_HELD_OUT = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(3, -20, 6, 0, 0.05f, 0.1f, -0.42f), fp(4000, -20, 6, 0, 0.05f, 0.1f, -0.42f));
	private static final FirstPersonClip FP_UP = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(4, -70, 10, 0, 0.05f, 0.55f, 0.0f), fp(14, -72, 10, 0, 0.05f, 0.56f, 0.0f),
			fp(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_HUNCH = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(6, 30, 0, 0, 0, -0.3f, 0.1f), fp(4000, 30, 0, 0, 0, -0.3f, 0.1f)).both();
	private static final FirstPersonClip FP_SHIELD = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(3, -10, -40, 0, -0.25f, 0.05f, -0.1f), fp(4000, -10, -40, 0, -0.25f, 0.05f, -0.1f));

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		int flags = Omnitrix.state(p).kitFlags;
		if ((flags & ShockRockKit.F_CHARGE) != 0) {
			boolean on = QuirkKeyframes.play(m, CHARGE, Math.min(ticks, 5f), 1f);
			m.rightArm.zRot += Mth.sin(age * 2.1f) * 0.03f * ShockRockKit.boltCharge(p, 0f); // the fist trembles with it
			return on;
		}
		if ((flags & ShockRockKit.F_CORE) != 0) {
			boolean on = QuirkKeyframes.play(m, CORE, Math.min(ticks, 6f), 1f);
			float k = ShockRockKit.coreCharge(p, 0f);
			m.body.yRot += Mth.sin(age * 2.7f) * 0.04f * k;
			m.head.xRot += Mth.sin(age * 3.1f) * 0.04f * k;
			return on;
		}
		if ((flags & ShockRockKit.F_LASH) != 0) {
			boolean on = QuirkKeyframes.play(m, LASH, Math.min(ticks, 3f), 1f);
			m.rightArm.xRot += Mth.sin(age * 0.6f) * 0.25f; // hauling on the tether
			m.leftArm.xRot -= Mth.sin(age * 0.6f) * 0.25f;
			return on;
		}
		if ((flags & ShockRockKit.F_GLIDE) != 0) {
			QuirkKeyframes.apply(m, DASH_HELD, Mth.clamp(ticks / 3f, 0f, 1f));
			return true;
		}
		if ((flags & ShockRockKit.F_CHANNEL) != 0) {
			return QuirkKeyframes.play(m, CHANNEL, Math.min(ticks, 5f), 1f);
		}
		boolean held = false;
		if ((flags & (ShockRockKit.F_SHIELD | ShockRockKit.F_REFLECT)) != 0) {
			QuirkKeyframes.apply(m, SHIELD_HELD, 1f);
			held = true;
		}
		float[][] clip = switch (animId) {
			case ShockRockKit.ANIM_BLAST -> BLAST;
			case ShockRockKit.ANIM_RAPID -> RAPID;
			case ShockRockKit.ANIM_BLADE -> BLADE;
			case ShockRockKit.ANIM_SLASH -> SLASH;
			case ShockRockKit.ANIM_BURST -> BURST;
			case ShockRockKit.ANIM_DASH -> DASH;
			case ShockRockKit.ANIM_SHIELD, ShockRockKit.ANIM_REFLECT -> (flags & (ShockRockKit.F_SHIELD | ShockRockKit.F_REFLECT)) != 0 ? SHIELD : null;
			case ShockRockKit.ANIM_SHIELD_END -> SHIELD_END;
			case ShockRockKit.ANIM_OVERLOAD -> OVERLOAD;
			case ShockRockKit.ANIM_PUNCH -> PUNCH;
			case ShockRockKit.ANIM_CHARGED -> CHARGED;
			case ShockRockKit.ANIM_WAVE -> WAVE;
			case ShockRockKit.ANIM_UPDRAFT -> UPDRAFT;
			case ShockRockKit.ANIM_BARRAGE -> BARRAGE;
			case ShockRockKit.ANIM_REFORM -> REFORM;
			case ShockRockKit.ANIM_DOME -> DOME;
			default -> null;
		};
		return clip != null && QuirkKeyframes.play(m, clip, ticks, 1f) || held;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case ShockRockKit.ANIM_BLAST -> FP_BLAST;
			case ShockRockKit.ANIM_RAPID -> FP_RAPID;
			case ShockRockKit.ANIM_BLADE -> FP_BLADE;
			case ShockRockKit.ANIM_SLASH, ShockRockKit.ANIM_WAVE -> FP_SLASH;
			case ShockRockKit.ANIM_BURST, ShockRockKit.ANIM_BARRAGE, ShockRockKit.ANIM_REFORM -> FP_BURST;
			case ShockRockKit.ANIM_DASH, ShockRockKit.ANIM_GLIDE, ShockRockKit.ANIM_UPDRAFT -> FP_DASH;
			case ShockRockKit.ANIM_OVERLOAD, ShockRockKit.ANIM_CHANNEL -> FP_OVERLOAD;
			case ShockRockKit.ANIM_PUNCH -> FP_PUNCH;
			case ShockRockKit.ANIM_CHARGE -> FP_CHARGE;
			case ShockRockKit.ANIM_CHARGED -> FP_CHARGED;
			case ShockRockKit.ANIM_LASH -> FP_HELD_OUT;
			case ShockRockKit.ANIM_DOME -> FP_UP;
			case ShockRockKit.ANIM_CORE -> FP_HUNCH;
			case ShockRockKit.ANIM_SHIELD, ShockRockKit.ANIM_REFLECT -> FP_SHIELD;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- the body: glow, constructs, the burst

	/** The glow pass's tint: soft at rest, bright and pulsing in Overload, flaring white as the Rock Burst bares his core. */
	private static int glowTint(AbstractClientPlayer p, float age, float burst) {
		float k = ShockRockKit.overloaded(p) ? 0.85f + 0.15f * Mth.sin(age * 0.5f) : 0.6f;
		k = Math.max(k, burst);
		int c = (int) (255 * Mth.clamp(k, 0f, 1f));
		return 0xFF000000 | (c << 16) | (c << 8) | c;
	}

	/** 0..1..0 over the Rock Burst: how far the chunks are flung out. */
	private static float burstOut(OmnitrixState s, float t) {
		if (s.animId != ShockRockKit.ANIM_BURST || t < 0f || t > ShockRockKit.BURST_TICKS) {
			return 0f;
		}
		float u = t / ShockRockKit.BURST_TICKS;
		return u < 0.25f ? 1f - (1f - u / 0.25f) * (1f - u / 0.25f) : u < 0.6f ? 1f : Mth.clamp(1f - (u - 0.6f) / 0.4f, 0f, 1f);
	}

	/** Twelve chunks off his body: {x, y, z} on the body bone (px) and the way each flies. */
	private static final float[][] CHUNKS = { { -3, 1, -2 }, { 3, 1, -2 }, { 0, 5, -2 }, { -3, 9, -2 }, { 3, 9, -2 }, { 0, 2, 2 }, { -3, 6, 2 }, { 3, 6, 2 }, { -4, 3, 0 },
			{ 4, 3, 0 }, { -4, 8, 0 }, { 4, 8, 0 } };

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		OmnitrixState s = Omnitrix.state(p);
		float t = p.level().getGameTime() + partial - s.animStart;
		float out = burstOut(s, t);
		// the energy: full-bright over the skin's own cyan
		float core = Math.max(ShockRockKit.coreCharge(p, partial), (s.kitFlags & ShockRockKit.F_CHANNEL) != 0 ? 0.5f + 0.2f * Mth.sin(age * 0.4f) : 0f);
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, glowTint(p, age, Math.max(out, core)));
		if (out > 0.01f) {
			burst(pose, buffers, light, model.body, out, age);
		}
		if ((s.kitFlags & ShockRockKit.F_BLADE) != 0) {
			float grow = s.animId == ShockRockKit.ANIM_BLADE ? Mth.clamp((t - 2f) / 5f, 0f, 1f) : 1f;
			AlienParts.on(pose, model.rightArm, () -> blade(pose, buffers, grow, age));
		}
		if ((s.kitFlags & (ShockRockKit.F_SHIELD | ShockRockKit.F_REFLECT)) != 0) {
			float grow = s.animId == ShockRockKit.ANIM_SHIELD || s.animId == ShockRockKit.ANIM_REFLECT ? Mth.clamp(t / 4f, 0f, 1f) : 1f;
			AlienParts.on(pose, model.body, () -> shield(pose, buffers, grow, age));
		}
		// v0.17.4: a ball of energy swelling in the charging fist, and the core blazing through his chest while it overloads
		float charge = ShockRockKit.boltCharge(p, partial);
		if (charge > 0f) {
			AlienParts.on(pose, model.rightArm, () -> {
				pose.translate(-1f / 16f, 11f / 16f, 0);
				PyroDraw.sphere(pose, PyroDraw.additive(buffers), 0.12f + 0.16f * charge, PyroDraw.dim(HOT, 0.9f), PyroDraw.dim(ENERGY, 0.7f), 0.1f, age,
						LightTexture.FULL_BRIGHT);
			});
		}
		if (core > 0.05f && (s.kitFlags & ShockRockKit.F_CORE) != 0) {
			AlienParts.on(pose, model.body, () -> {
				pose.translate(0, 5f / 16f, -1f / 16f);
				PyroDraw.sphere(pose, PyroDraw.additive(buffers), 0.2f + 0.3f * core, PyroDraw.dim(HOT, 0.9f * core), PyroDraw.dim(ENERGY, 0.6f * core), 0.1f, age,
						LightTexture.FULL_BRIGHT);
			});
		}
	}

	/** The Rock Burst: chunks of his armour thrown out from the body and slammed back, the bared core flaring between them. */
	private static void burst(PoseStack pose, MultiBufferSource buffers, int light, ModelPart body, float out, float age) {
		AlienParts.on(pose, body, () -> {
			VertexConsumer skin = buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN));
			for (int i = 0; i < CHUNKS.length; i++) {
				float[] c = CHUNKS[i];
				float dx = c[0], dy = c[1] - 5f, dz = c[2] == 0 ? 0 : c[2];
				float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
				float d = out * (14f + (i % 3) * 5f);
				float x = c[0] + dx / len * d, y = c[1] + dy / len * d, z = c[2] + dz / len * d;
				float r = 1.6f + (i % 2) * 0.6f;
				pose.pushPose();
				pose.translate(x / 16f, y / 16f, z / 16f);
				pose.mulPose(com.mojang.math.Axis.XP.rotation(out * (i + 1) * 1.3f));
				pose.mulPose(com.mojang.math.Axis.YP.rotation(out * (i + 2) * 0.9f));
				float[] uv = i % 2 == 0 ? ROCK : ROCK_LIGHT;
				AlienParts.box(pose, skin, -r, -r, -r, r, r, r, uv[0], uv[1], uv[2], uv[3], 0xFFFFFFFF, light);
				pose.popPose();
			}
			pose.pushPose();
			pose.translate(0, 5f / 16f, 0);
			PyroDraw.sphere(pose, PyroDraw.additive(buffers), 0.25f + 0.2f * out, PyroDraw.dim(HOT, 0.9f * out), PyroDraw.dim(ENERGY, 0.6f * out), 0.08f, age,
					LightTexture.FULL_BRIGHT);
			pose.popPose();
		});
	}

	/** The Energy Blade: a long flat blade of light along his right forearm and past his fist. */
	private static void blade(PoseStack pose, MultiBufferSource buffers, float grow, float age) {
		VertexConsumer add = PyroDraw.additive(buffers);
		float flick = 0.9f + 0.1f * Mth.sin(age * 1.7f);
		float end = 9f + 15f * grow;
		AlienParts.box(pose, add, -1.5f, 7f, -1.6f, -0.5f, end, 1.6f, 0.5f, 0.5f, 0.5f, 0.5f, PyroDraw.dim(ENERGY, 0.6f * flick), LightTexture.FULL_BRIGHT);
		AlienParts.box(pose, add, -1.2f, 7.5f, -0.6f, -0.8f, end - 0.5f, 0.6f, 0.5f, 0.5f, 0.5f, 0.5f, PyroDraw.dim(HOT, 0.9f * flick), LightTexture.FULL_BRIGHT);
		// the point
		AlienParts.box(pose, add, -1.3f, end, -0.8f, -0.7f, end + 2.5f * grow, 0.8f, 0.5f, 0.5f, 0.5f, 0.5f, PyroDraw.dim(ENERGY, 0.5f * flick), LightTexture.FULL_BRIGHT);
	}

	/** The Shield Construct: a hexagonal plate of energy a little way in front of him, bright at the rim. */
	private static void shield(PoseStack pose, MultiBufferSource buffers, float grow, float age) {
		VertexConsumer add = PyroDraw.additive(buffers);
		float pulse = 0.85f + 0.15f * Mth.sin(age * 0.8f);
		float r = 10f * grow, cy = 5f, z = -9f;
		PoseStack.Pose last = pose.last();
		float s = 1f / 16f;
		for (int i = 0; i < 6; i++) {
			float a0 = Mth.TWO_PI * i / 6f + Mth.PI / 6f, a1 = Mth.TWO_PI * (i + 1) / 6f + Mth.PI / 6f;
			float x0 = Mth.cos(a0) * r, y0 = cy + Mth.sin(a0) * r, x1 = Mth.cos(a1) * r, y1 = cy + Mth.sin(a1) * r;
			int fill = PyroDraw.dim(ENERGY, 0.22f * pulse);
			// the plate (a fan) both ways round, and its rim
			AlienParts.quad(add, last, fill, LightTexture.FULL_BRIGHT, 0, cy * s, z * s, x0 * s, y0 * s, z * s, x1 * s, y1 * s, z * s, 0, cy * s, z * s, 0, 0, -1, 0.5f, 0.5f, 0.5f,
					0.5f);
			AlienParts.quad(add, last, fill, LightTexture.FULL_BRIGHT, 0, cy * s, z * s, x1 * s, y1 * s, z * s, x0 * s, y0 * s, z * s, 0, cy * s, z * s, 0, 0, 1, 0.5f, 0.5f, 0.5f,
					0.5f);
			float ix0 = x0 * 0.88f, iy0 = cy + (y0 - cy) * 0.88f, ix1 = x1 * 0.88f, iy1 = cy + (y1 - cy) * 0.88f;
			int rim = PyroDraw.dim(HOT, 0.8f * pulse);
			AlienParts.quad(add, last, rim, LightTexture.FULL_BRIGHT, ix0 * s, iy0 * s, z * s, x0 * s, y0 * s, z * s, x1 * s, y1 * s, z * s, ix1 * s, iy1 * s, z * s, 0, 0, -1, 0.5f,
					0.5f, 0.5f, 0.5f);
			AlienParts.quad(add, last, rim, LightTexture.FULL_BRIGHT, ix1 * s, iy1 * s, z * s, x1 * s, y1 * s, z * s, x0 * s, y0 * s, z * s, ix0 * s, iy0 * s, z * s, 0, 0, 1, 0.5f,
					0.5f, 0.5f, 0.5f);
		}
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		// the energy on his first-person arm glows too, and the blade shows on it
		arm.render(pose, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, glowTint(p, p.tickCount, 0f));
		OmnitrixState s = Omnitrix.state(p);
		if (right && (s.kitFlags & ShockRockKit.F_BLADE) != 0) {
			pose.pushPose();
			arm.translateAndRotate(pose);
			blade(pose, buffers, 1f, p.tickCount);
			pose.popPose();
		}
	}

	// ---------------------------------------------------------------- the HUD: his Energy

	@Override
	public int hudHeight() {
		return 10;
	}

	@Override
	public void hud(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.player.LocalPlayer p, int x, int y, int w, float partial) {
		Minecraft mc = Minecraft.getInstance();
		int e = ShockRockKit.energy(p);
		boolean over = ShockRockKit.overloaded(p);
		boolean flash = (p.level().getGameTime() / 6) % 2 == 0;
		String value = over && flash ? "OVER" : String.valueOf(e);
		com.projecthero.mod.client.omnitrix.OmnitrixHud.meter(g, mc, x, y - 6, Component.translatable("hud.projecthero.omnitrix.shock_rock.meter"),
				e / (float) ShockRockKit.MAX_ENERGY, 0xFF000000 | (over ? HOT : ENERGY), value, over ? 0xFFCFFFFF : 0xFFE2E2E2);
	}
}
