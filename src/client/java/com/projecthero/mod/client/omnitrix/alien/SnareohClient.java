package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.alien.snareoh.SnareohBandages;
import com.projecthero.mod.client.omnitrix.alien.snareoh.SnareohLineClient;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.snareoh.SnareohKit;
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
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;

/**
 * v0.17.3 Snare-oh's client kit (user: "make aliens based on their abilities and stuff they do in the show please, replace the pixel
 * omnitrix on their skin with our omnitrix badge please"). The user's skin on the player body, its painted chest Omnitrix painted out
 * (scratchpad/omnitrix_new/gen_textures_snare_oh.js: the two glowing bands carry on across his chest) and the mod's badge in its place; his lime
 * eyes and bands drawn full-bright ({@code snare_oh_glow.png}); loose bandage ends trailing off his wrists. His bandages -- whips, binds,
 * the grapple, the swing line, the flying hand -- are {@link SnareohBandages}; the swing and zip move him through
 * {@link SnareohLineClient}.
 */
public final class SnareohClient implements AlienClientKit {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/snare_oh.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/omnitrix/snare_oh_glow.png");
	/** The chest badge (body-bone pixels): where the skin painted its symbol (texels 22..25 x 22..26), out past the jacket's wraps. */
	public static final float[] BADGE = { 0f, 4.5f, -2.32f, 3.0f };

	private static boolean registered;

	public static void register() {
		AlienClients.register(Aliens.SNARE_OH.id(), new SnareohClient());
		if (!registered) {
			registered = true;
			SnareohBandages.register();
			SnareohLineClient.register();
		}
	}

	// ---------------------------------------------------------------- poses (QuirkKeyframes: tick, rArm xyz, lArm xyz, body xy, rLeg xyz, lLeg xyz, head x)
	private static float[] f(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float by, float rlx, float rly, float rlz, float llx,
			float lly, float llz, float hx) {
		return QuirkKeyframes.f(t, rax, ray, raz, lax, lay, laz, bx, by, rlx, rly, rlz, llx, lly, llz, hx);
	}

	/** Bandage Whip: the arm cocked back over the shoulder, then cracked forward and down. */
	private static final float[][] WHIP = { QuirkKeyframes.rest(0), f(2, -2.6f, 0.3f, 0.2f, 0.4f, 0, -0.2f, -0.05f, 0.35f, 0.1f, 0, 0, -0.1f, 0, 0, 0),
			f(4, -1.3f, -0.2f, 0, 0.5f, 0, -0.3f, 0.12f, -0.4f, -0.3f, 0, 0, 0.3f, 0, 0, 0), f(8, -1.0f, -0.1f, 0, 0.3f, 0, -0.2f, 0.06f, -0.2f, -0.15f, 0, 0, 0.15f, 0, 0, 0),
			QuirkKeyframes.rest(13) };
	/** Bandage Bind: a two-handed cast, then the hands twist as the wrap winds tight. */
	private static final float[][] BIND = { QuirkKeyframes.rest(0), f(3, -1.6f, -0.3f, 0, -1.6f, 0.3f, 0, 0.1f, 0, -0.3f, 0, 0, 0.3f, 0, 0, 0),
			f(7, -1.4f, 0.3f, 0.3f, -1.4f, -0.3f, -0.3f, 0.06f, 0, -0.2f, 0, 0, 0.2f, 0, 0, 0), f(11, -1.5f, -0.2f, 0, -1.5f, 0.2f, 0, 0.08f, 0, -0.2f, 0, 0, 0.2f, 0, 0, 0),
			QuirkKeyframes.rest(16) };
	/** Grapple Pull: the arm flung out, then hauled back hand over hand. */
	private static final float[][] PULL = { QuirkKeyframes.rest(0), f(2, -1.7f, 0, 0, 0.3f, 0, -0.2f, 0.12f, -0.3f, -0.3f, 0, 0, 0.3f, 0, 0, 0),
			f(5, 0.4f, 0, 0.2f, -1.4f, 0, 0, -0.18f, 0.3f, 0.2f, 0, 0, -0.35f, 0, 0, -0.1f), f(9, -1.2f, 0, 0, 0.4f, 0, -0.2f, -0.1f, -0.2f, 0.2f, 0, 0, -0.2f, 0, 0, -0.05f),
			QuirkKeyframes.rest(14) };
	/** The swing: one arm up the bandage, legs trailing. */
	private static final float[][] SWING = { QuirkKeyframes.rest(0), f(3, -2.9f, 0.1f, 0.2f, -0.3f, 0, -0.4f, 0, 0, 0.35f, 0, 0.05f, -0.2f, 0, -0.05f, -0.2f) };
	/** The zip: both arms up the bandage, reeled in. */
	private static final float[][] ZIP = { QuirkKeyframes.rest(0), f(3, -2.8f, 0.15f, 0.1f, -2.6f, -0.15f, -0.1f, 0, 0, 0.3f, 0, 0, 0.1f, 0, 0, -0.25f) };
	/** Mummy Wrap: hunched in, then thrown wide open as the bandages burst out of him. */
	private static final float[][] WRAP = { QuirkKeyframes.rest(0), f(4, -0.6f, 0.6f, -0.3f, -0.6f, -0.6f, 0.3f, 0.35f, 0, -0.3f, 0, 0, -0.3f, 0, 0, 0.3f),
			f(7, -0.4f, 0, 1.6f, -0.4f, 0, -1.6f, -0.3f, 0, 0, 0, 0.3f, 0, 0, -0.3f, -0.4f), f(14, -0.4f, 0, 1.5f, -0.4f, 0, -1.5f, -0.25f, 0, 0, 0, 0.3f, 0, 0, -0.3f, -0.3f),
			QuirkKeyframes.rest(20) };
	/** Unravel Spin: arms flung out level for the spin (the body turn is {@link #bodySpin}). */
	private static final float[][] SPIN = { QuirkKeyframes.rest(0), f(2, -0.2f, 0, 1.45f, -0.2f, 0, -1.45f, 0, 0, 0, 0, 0.15f, 0, 0, -0.15f, 0),
			f(15, -0.2f, 0, 1.45f, -0.2f, 0, -1.45f, 0, 0, 0, 0, 0.15f, 0, 0, -0.15f, 0), QuirkKeyframes.rest(20) };
	/** v0.17.4 Lash Flurry (looped while R is held): the right arm cracks a whip, then the left, again and again. */
	private static final float[][] LASH = { f(0, -2.5f, 0.3f, 0.2f, 0.3f, 0, -0.2f, -0.04f, 0.3f, 0.1f, 0, 0, -0.1f, 0, 0, 0),
			f(2, -1.2f, -0.2f, 0, -0.6f, 0, -0.2f, 0.1f, -0.35f, -0.2f, 0, 0, 0.2f, 0, 0, 0), f(5, 0.3f, 0, 0.2f, -2.5f, -0.3f, -0.2f, -0.04f, -0.3f, -0.1f, 0, 0, 0.1f, 0, 0, 0),
			f(7, -0.6f, 0, 0.2f, -1.2f, 0.2f, 0, 0.1f, 0.35f, 0.2f, 0, 0, -0.2f, 0, 0, 0), f(10, -2.5f, 0.3f, 0.2f, 0.3f, 0, -0.2f, -0.04f, 0.3f, 0.1f, 0, 0, -0.1f, 0, 0, 0) };
	/** v0.17.4 Bandage Slam: a cast, both hands hauling the bandage up overhead, then whipped down hard as it slams. */
	private static final float[][] SLAM = { QuirkKeyframes.rest(0), f(2, -1.6f, -0.2f, 0, -1.6f, 0.2f, 0, 0.1f, 0, -0.3f, 0, 0, 0.3f, 0, 0, 0),
			f(5, -2.9f, -0.2f, 0.1f, -2.9f, 0.2f, -0.1f, -0.25f, 0, 0.1f, 0, 0.1f, -0.1f, 0, -0.1f, -0.35f),
			f(8, -2.9f, -0.2f, 0.1f, -2.9f, 0.2f, -0.1f, -0.3f, 0, 0.1f, 0, 0.1f, -0.1f, 0, -0.1f, -0.4f),
			f(10, -0.7f, -0.1f, 0, -0.7f, 0.1f, 0, 0.45f, 0, -0.4f, 0, 0.1f, 0.4f, 0, -0.1f, 0.3f), f(15, -0.6f, 0, 0, -0.6f, 0, 0, 0.35f, 0, -0.3f, 0, 0.1f, 0.3f, 0, -0.1f, 0.2f),
			QuirkKeyframes.rest(21) };
	/** v0.17.4 Tether Throw (held): the right arm up, whirling the bandage round over his head (the circling is added in {@link #pose}). */
	private static final float[] TETHER_HELD = f(0, -2.7f, 0, 0.4f, -0.3f, 0, -0.5f, -0.08f, 0, -0.2f, 0, 0.15f, 0.2f, 0, -0.15f, -0.15f);
	private static final float[][] TETHER = { QuirkKeyframes.rest(0), f(3, -1.6f, 0, 0, 0.3f, 0, -0.2f, 0.1f, -0.3f, -0.3f, 0, 0, 0.3f, 0, 0, 0), at(6, TETHER_HELD),
			at(4000, TETHER_HELD) };
	/** v0.17.4 the fling: the whirling arm brought over and down, the body turning into it. */
	private static final float[][] FLING = { at(0, TETHER_HELD), f(3, -1.2f, -0.4f, 0, 0.4f, 0, -0.3f, 0.3f, -0.6f, -0.4f, 0, 0, 0.4f, 0, 0, 0.1f),
			f(7, -0.8f, -0.3f, 0, 0.3f, 0, -0.2f, 0.2f, -0.4f, -0.3f, 0, 0, 0.3f, 0, 0, 0), QuirkKeyframes.rest(13) };
	/** v0.17.4 Recoil: snatched backwards off his feet, arms flung forward, legs trailing ahead. */
	private static final float[][] RECOIL = { QuirkKeyframes.rest(0), f(2, -1.4f, 0.2f, 0.3f, -1.4f, -0.2f, -0.3f, -0.35f, 0, -0.9f, 0, 0.1f, -0.6f, 0, -0.1f, 0.3f),
			f(9, -1.2f, 0.2f, 0.3f, -1.2f, -0.2f, -0.3f, -0.3f, 0, -0.8f, 0, 0.1f, -0.5f, 0, -0.1f, 0.2f), QuirkKeyframes.rest(15) };
	/** v0.17.4 Sand Coffin: both hands thrown out, then the fists clenched and wrenched in as the wrappings crush. */
	private static final float[][] COFFIN = { QuirkKeyframes.rest(0), f(3, -1.5f, 0.6f, 0.4f, -1.5f, -0.6f, -0.4f, -0.1f, 0, -0.2f, 0, 0, 0.2f, 0, 0, -0.1f),
			f(6, -1.2f, -0.5f, -0.2f, -1.2f, 0.5f, 0.2f, 0.25f, 0, -0.2f, 0, 0.1f, 0.2f, 0, -0.1f, 0.2f),
			f(12, -1.1f, -0.6f, -0.2f, -1.1f, 0.6f, 0.2f, 0.3f, 0, -0.2f, 0, 0.1f, 0.2f, 0, -0.1f, 0.25f), QuirkKeyframes.rest(18) };
	/** v0.17.4 Cocoon (held): arms wrapped tight across his chest, hunched over, legs together. */
	private static final float[] COCOON_HELD = f(0, -1.3f, -0.9f, 0, -1.3f, 0.9f, 0, 0.25f, 0, 0, 0, 0.04f, 0, 0, -0.04f, 0.35f);
	private static final float[][] COCOON = { QuirkKeyframes.rest(0), at(5, COCOON_HELD), at(4000, COCOON_HELD) };
	/** v0.17.4 Bandage Net: a two-handed throw over the head, flinging the net out wide. */
	private static final float[][] NET = { QuirkKeyframes.rest(0), f(3, -2.8f, 0.3f, -0.2f, -2.8f, -0.3f, 0.2f, -0.2f, 0, 0.2f, 0, 0, -0.2f, 0, 0, -0.2f),
			f(6, -1.3f, -0.5f, 0.5f, -1.3f, 0.5f, -0.5f, 0.2f, 0, -0.3f, 0, 0, 0.3f, 0, 0, 0), f(11, -1.1f, -0.4f, 0.4f, -1.1f, 0.4f, -0.4f, 0.12f, 0, -0.2f, 0, 0, 0.2f, 0, 0, 0),
			QuirkKeyframes.rest(16) };
	/** v0.17.4 Sand Gust: rearing back, then the head thrust forward as the sand pours out, arms swept back. */
	private static final float[][] GUST = { QuirkKeyframes.rest(0), f(3, 0.4f, 0, 0.3f, 0.4f, 0, -0.3f, -0.25f, 0, 0.1f, 0, 0, -0.1f, 0, 0, -0.5f),
			f(6, 0.7f, 0, 0.35f, 0.7f, 0, -0.35f, 0.3f, 0, -0.3f, 0, 0, 0.3f, 0, 0, 0.15f), f(14, 0.6f, 0, 0.3f, 0.6f, 0, -0.3f, 0.25f, 0, -0.25f, 0, 0, 0.25f, 0, 0, 0.1f),
			QuirkKeyframes.rest(20) };
	/** v0.17.4 Whirlwind (held): arms flung out level while he spins and walks on (the turn is {@link #bodySpin}). */
	private static final float[] WHIRL_HELD = f(0, -0.2f, 0, 1.45f, -0.2f, 0, -1.45f, 0, 0, 0, 0, 0.15f, 0, 0, -0.15f, 0);
	private static final float[][] WHIRL = { QuirkKeyframes.rest(0), at(2, WHIRL_HELD), at(4000, WHIRL_HELD) };

	private static float[] at(float t, float[] frame) {
		float[] o = frame.clone();
		o[0] = t;
		return o;
	}

	/** Reform: he pulls himself back together, arms wrapping in. */
	private static final float[][] REFORM = { f(0, -0.4f, 0, 1.4f, -0.4f, 0, -1.4f, -0.2f, 0, 0, 0, 0.2f, 0, 0, -0.2f, -0.3f),
			f(5, -0.8f, 0.6f, -0.4f, -0.8f, -0.6f, 0.4f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.2f), QuirkKeyframes.rest(11) };
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0), f(2, -1.5f, 0.15f, 0, 0.3f, 0, -0.1f, 0.12f, -0.45f, -0.3f, 0, 0, 0.3f, 0, 0, 0), QuirkKeyframes.rest(7) };

	private static final FirstPersonClip FP_WHIP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -60, 10, 0, 0.05f, 0.3f, 0.05f),
			FirstPersonClip.f(4, -20, -6, 0, 0.0f, 0.12f, -0.4f), FirstPersonClip.f(9, -12, -4, 0, 0, 0.06f, -0.3f), FirstPersonClip.f(13, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_CAST = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -22, 8, 0, 0.08f, 0.14f, -0.44f),
			FirstPersonClip.f(10, -18, 8, 0, 0.06f, 0.1f, -0.38f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PULL = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -24, 8, 0, 0.06f, 0.15f, -0.4f),
			FirstPersonClip.f(6, 20, 5, 0, 0.0f, -0.2f, 0.2f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_UP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -70, 10, 0, 0.05f, 0.55f, 0.0f),
			FirstPersonClip.f(10, -72, 10, 0, 0.05f, 0.56f, 0.0f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_WIDE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -25, -20, 15, -0.1f, 0.2f, -0.1f),
			FirstPersonClip.f(14, -25, -20, 15, -0.1f, 0.2f, -0.1f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -14, 10, 0, 0.08f, 0.1f, -0.44f),
			FirstPersonClip.f(7, 0, 0, 0, 0, 0, 0));

	/** v0.17.4: the Lash Flurry's alternating whips (looped), the slam's haul and yank, the tether's whirl, the net's throw. */
	private static final FirstPersonClip FP_LASH = FirstPersonClip.of(FirstPersonClip.f(0, -60, 10, 0, 0.05f, 0.3f, 0.05f), FirstPersonClip.f(2, -20, -6, 0, 0, 0.12f, -0.4f),
			FirstPersonClip.f(5, -60, 10, 0, 0.05f, 0.3f, 0.05f), FirstPersonClip.f(7, -20, -6, 0, 0, 0.12f, -0.4f), FirstPersonClip.f(10, -60, 10, 0, 0.05f, 0.3f, 0.05f),
			FirstPersonClip.f(12, -20, -6, 0, 0, 0.12f, -0.4f), FirstPersonClip.f(15, -60, 10, 0, 0.05f, 0.3f, 0.05f), FirstPersonClip.f(17, -20, -6, 0, 0, 0.12f, -0.4f),
			FirstPersonClip.f(20, -60, 10, 0, 0.05f, 0.3f, 0.05f), FirstPersonClip.f(22, -20, -6, 0, 0, 0.12f, -0.4f), FirstPersonClip.f(25, -60, 10, 0, 0.05f, 0.3f, 0.05f),
			FirstPersonClip.f(27, -20, -6, 0, 0, 0.12f, -0.4f), FirstPersonClip.f(30, -60, 10, 0, 0.05f, 0.3f, 0.05f), FirstPersonClip.f(32, -20, -6, 0, 0, 0.12f, -0.4f),
			FirstPersonClip.f(35, -60, 10, 0, 0.05f, 0.3f, 0.05f), FirstPersonClip.f(37, -20, -6, 0, 0, 0.12f, -0.4f), FirstPersonClip.f(48, -60, 10, 0, 0.05f, 0.3f, 0.05f))
			.both();
	private static final FirstPersonClip FP_SLAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(5, -70, 10, 0, 0.05f, 0.55f, 0.0f),
			FirstPersonClip.f(8, -72, 10, 0, 0.05f, 0.56f, 0.0f), FirstPersonClip.f(10, 30, 0, 0, 0, -0.3f, 0.1f), FirstPersonClip.f(18, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_TETHER = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -70, 20, 10, 0.1f, 0.5f, 0.0f),
			FirstPersonClip.f(4000, -70, 20, 10, 0.1f, 0.5f, 0.0f));
	private static final FirstPersonClip FP_FLING = FirstPersonClip.of(FirstPersonClip.f(0, -70, 20, 10, 0.1f, 0.5f, 0.0f), FirstPersonClip.f(3, -10, -10, 0, 0, 0.05f, -0.45f),
			FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_HUG = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(5, 10, -40, 0, -0.25f, -0.15f, 0.05f),
			FirstPersonClip.f(4000, 10, -40, 0, -0.25f, -0.15f, 0.05f)).both();
	private static final FirstPersonClip FP_GUST = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, 40, 0, 0, 0, -0.5f, 0.15f),
			FirstPersonClip.f(14, 40, 0, 0, 0, -0.5f, 0.15f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public float[] badge() {
		return BADGE;
	}

	@Override
	public boolean showBadge(AbstractClientPlayer p) {
		return !SnareohKit.scattered(p);
	}

	@Override
	public int bodyAlpha(AbstractClientPlayer p) {
		// scattered: nothing of him but the swirl of bandages (SnareohBandages); fading back in as he reforms
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & SnareohKit.F_SCATTER) != 0) {
			return 0;
		}
		if (s.animId == SnareohKit.ANIM_REFORM) {
			float t = p.level().getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false) - s.animStart;
			if (t >= 0f && t < 5f) {
				return (int) Mth.clamp(40 + 215 * t / 5f, 40, 255);
			}
		}
		return 255;
	}

	/** Unravel Spin's body turn: three whole turns over the spin, easing out. */
	public static float spinDegrees(AbstractClientPlayer p, float partial) {
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & SnareohKit.F_WHIRL) != 0) {
			// v0.17.4 Whirlwind: a steady turn and a half a second for as long as C is held
			float t = p.level().getGameTime() + partial - s.animStart;
			return (t * 27f) % 360f;
		}
		if (s.animId != SnareohKit.ANIM_SPIN) {
			return 0f;
		}
		float t = p.level().getGameTime() + partial - s.animStart;
		float len = SnareohKit.SPIN_HITS * SnareohKit.SPIN_GAP + 2;
		if (t < 0f || t > len) {
			return 0f;
		}
		float k = t / len;
		return 1080f * (1f - (1f - k) * (1f - k));
	}

	@Override
	public float bodySpin(AbstractClientPlayer p, float partial) {
		return spinDegrees(p, partial);
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		if (SnareohKit.scattered(p)) {
			return;
		}
		// loose bandage ends off both wrists, swaying with his arms
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(SnareohBandages.STRIP));
		tail(pose, vc, light, model.rightArm, -1f, age, p.walkAnimation.speed(partial));
		tail(pose, vc, light, model.leftArm, 1f, age + 7f, p.walkAnimation.speed(partial));
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFB0B0B0);
	}

	/** One loose end: two thin links hanging off the outside of the wrist, lifted and fluttering as he moves. */
	private static void tail(PoseStack pose, VertexConsumer vc, int light, ModelPart arm, float side, float age, float walk) {
		AlienParts.on(pose, arm, () -> {
			pose.translate(side * 1.9f / 16f, 8.5f / 16f, 0.6f / 16f);
			float lift = 0.35f + walk * 0.9f;
			pose.mulPose(com.mojang.math.Axis.XP.rotation(lift + Mth.sin(age * 0.21f) * 0.12f));
			pose.mulPose(com.mojang.math.Axis.ZP.rotation(side * (0.18f + Mth.sin(age * 0.17f) * 0.1f)));
			AlienParts.box(pose, vc, -0.9f, 0f, -0.15f, 0.9f, 4.5f, 0.15f, 0f, 0f, 1f, 0.3f, 0xFFFFFFFF, light);
			pose.translate(0f, 4.3f / 16f, 0f);
			pose.mulPose(com.mojang.math.Axis.XP.rotation(0.25f + Mth.sin(age * 0.3f + 1f) * 0.25f + walk * 0.5f));
			AlienParts.box(pose, vc, -0.8f, 0f, -0.12f, 0.8f, 4f, 0.12f, 0f, 0.3f, 1f, 0.6f, 0xFFFFFFFF, light);
		});
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		// sand trickling out of him now and then (more while scattered: the server's)
		if (p.tickCount % 17 == 0 && !p.isInvisible() && !SnareohKit.scattered(p)) {
			p.level().addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()), p.getX() + (p.getRandom().nextDouble() - 0.5) * 0.5,
					p.getY() + 0.4 + p.getRandom().nextDouble() * 1.2, p.getZ() + (p.getRandom().nextDouble() - 0.5) * 0.5, 0, 0, 0);
		}
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		int flags = Omnitrix.state(p).kitFlags;
		if ((flags & SnareohKit.F_SWING) != 0) {
			return QuirkKeyframes.play(m, SWING, Math.min(ticks, 3f), 1f);
		}
		if ((flags & SnareohKit.F_ZIP) != 0) {
			return QuirkKeyframes.play(m, ZIP, Math.min(ticks, 3f), 1f);
		}
		if ((flags & SnareohKit.F_COCOON) != 0) {
			return QuirkKeyframes.play(m, COCOON, Math.min(ticks, 5f), 1f);
		}
		if ((flags & SnareohKit.F_LASH) != 0) {
			// fade in over the first lash, then loop the two-armed flurry
			QuirkKeyframes.apply(m, QuirkKeyframes.sample(LASH, ticks % 10f), Mth.clamp(ticks / 2f, 0f, 1f));
			return true;
		}
		if ((flags & SnareohKit.F_TETHER) != 0) {
			boolean on = QuirkKeyframes.play(m, TETHER, Math.min(ticks, 6f), 1f);
			if (ticks >= 4f) {
				// the hand circles over his head with the creature
				m.rightArm.zRot += Mth.sin(age * 0.5f) * 0.35f;
				m.rightArm.xRot += Mth.cos(age * 0.5f) * 0.2f;
			}
			return on;
		}
		if ((flags & SnareohKit.F_WHIRL) != 0) {
			return QuirkKeyframes.play(m, WHIRL, Math.min(ticks, 2f), 1f);
		}
		float[][] clip = switch (animId) {
			case SnareohKit.ANIM_WHIP -> WHIP;
			case SnareohKit.ANIM_BIND -> BIND;
			case SnareohKit.ANIM_PULL -> PULL;
			case SnareohKit.ANIM_WRAP, SnareohKit.ANIM_BURST -> WRAP;
			case SnareohKit.ANIM_SPIN -> SPIN;
			case SnareohKit.ANIM_REFORM -> REFORM;
			case SnareohKit.ANIM_PUNCH -> PUNCH;
			case SnareohKit.ANIM_SLAM -> SLAM;
			case SnareohKit.ANIM_FLING -> FLING;
			case SnareohKit.ANIM_RECOIL -> RECOIL;
			case SnareohKit.ANIM_COFFIN -> COFFIN;
			case SnareohKit.ANIM_NET -> NET;
			case SnareohKit.ANIM_GUST -> GUST;
			default -> null;
		};
		return clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case SnareohKit.ANIM_WHIP -> FP_WHIP;
			case SnareohKit.ANIM_BIND, SnareohKit.ANIM_NET -> FP_CAST;
			case SnareohKit.ANIM_PULL, SnareohKit.ANIM_RECOIL -> FP_PULL;
			case SnareohKit.ANIM_SWING, SnareohKit.ANIM_ZIP -> FP_UP;
			case SnareohKit.ANIM_WRAP, SnareohKit.ANIM_SPIN, SnareohKit.ANIM_WHIRL, SnareohKit.ANIM_BURST, SnareohKit.ANIM_COFFIN -> FP_WIDE;
			case SnareohKit.ANIM_PUNCH -> FP_PUNCH;
			case SnareohKit.ANIM_LASH -> FP_LASH;
			case SnareohKit.ANIM_SLAM -> FP_SLAM;
			case SnareohKit.ANIM_TETHER -> FP_TETHER;
			case SnareohKit.ANIM_FLING -> FP_FLING;
			case SnareohKit.ANIM_COCOON -> FP_HUG;
			case SnareohKit.ANIM_GUST -> FP_GUST;
			default -> null;
		};
	}

}
