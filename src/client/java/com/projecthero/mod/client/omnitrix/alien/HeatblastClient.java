package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.ghostrider.GhostRiderRender;
import com.projecthero.mod.client.mutation.PyrokinesisClient;
import com.projecthero.mod.client.mutation.pyro.PyroBoltRenderer;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.heatblast.HeatblastKit;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Heatblast's client kit -- the user's magma-rock skin with an animated burning overlay (lava pulsing through the
 * cracks, flames licking up the rock), Ghost Rider's fire-sprite blaze for a head, embers and heat shimmer, flames under the feet while the Fire Jet flies, and a pose (third and first person)
 * for every move.
 *
 * <p>v0.17.2 (user: "upgrade the vfx on all of heatblasts abilities and make the animations better please"): every move's pose
 * is a wind-up, a release and a follow-through (the throws aimed down the look), the Fire Stream recoils and the Supernova's
 * charge trembles; on the body the Fire Jet wears Pyrokinesis' Jet Flight flames ({@link PyrokinesisClient#jetFlames}), a
 * fireball swells in his hand for the Pyro Bomb and between his hands for the Supernova and the smelt, his palms flare as
 * the shots leave them, the Heat Shield is a churning shell of heat round him and the Heat Aura glows on the ground.
 *
 * <p>v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): every move now draws with Pyrokinesis'
 * own code -- the shots are its Fireball / Meteor ({@link PyroBoltRenderer#draw}), the Fire Stream its Flamethrower cone
 * ({@link PyrokinesisClient#flamethrower}), the Jet Dash its Flame Dash fire, the Heat Shield its Flame Body limb flames, the
 * Heat Aura its white-hot hands; the server side spawns its novas, blasts, Ring of Fire and Solar Flare ({@code PyroFxEntity}).
 */
public final class HeatblastClient implements AlienClientKit {
	private static final float A = QuirkKeyframes.AIM;

	// v0.17.1 (user: "add abilities to the V key for every alien"): the V move's pose and first-person clip
	// v0.17.2: a short curl in, then the palms pushed out and down as the heat rolls off him
	private static final float[][] AURA = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.7f, 0.4f, 0f, -0.7f, -0.4f, 0f, 0.25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.3f),
			QuirkKeyframes.f(5, -0.3f, 0f, 1.0f, -0.3f, 0f, -1.0f, -0.2f, 0f, 0f, 0f, 0.18f, 0f, 0f, -0.18f, -0.35f),
			QuirkKeyframes.f(14, -0.3f, 0f, 0.9f, -0.3f, 0f, -0.9f, -0.12f, 0f, 0f, 0f, 0.15f, 0f, 0f, -0.15f, -0.25f), QuirkKeyframes.rest(20) };
	private static final FirstPersonClip FP_AURA = FirstPersonClip.of(FirstPersonClip.f(0, 0f, 0f, 0f, 0f, 0f, 0f), FirstPersonClip.f(2, -15f, 30f, 0f, 0.18f, 0f, 0.05f),
			FirstPersonClip.f(5, -20f, -30f, 25f, -0.2f, 0.1f, 0f), FirstPersonClip.f(14, -18f, -28f, 22f, -0.18f, 0.08f, 0f), FirstPersonClip.f(20, 0f, 0f, 0f, 0f, 0f, 0f))
			.both();
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/heatblast.png");
	private static final ResourceLocation[] GLOW = new ResourceLocation[32];

	static {
		for (int i = 0; i < GLOW.length; i++) {
			GLOW[i] = ProjectHeroMod.id(String.format("textures/entity/omnitrix/heatblast_glow_%02d.png", i));
		}
	}

	// {tick, rArm xyz, lArm xyz, body xy, rLeg xyz, lLeg xyz, headX} (radians); A = along the look
	// v0.17.2: R Fire Blast -- the right arm cocked back with the torso turned, the palm thrust out down the aim, held a beat
	private static final float[][] BLAST = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(2, 0.55f, 0.5f, 0.35f, -0.7f, 0f, -0.15f, -0.05f, 0.45f, 0.3f, 0f, 0.05f, -0.3f, 0f, -0.05f, 0f),
			QuirkKeyframes.f(4, A, A, 0f, 0.35f, 0f, -0.2f, 0.15f, -0.3f, 0.35f, 0f, 0f, -0.35f, 0f, 0f, 0f),
			QuirkKeyframes.f(7, A, A, 0f, 0.3f, 0f, -0.15f, 0.1f, -0.25f, 0.3f, 0f, 0f, -0.3f, 0f, 0f, 0f), QuirkKeyframes.rest(13) };
	// v0.17.2: Use's small bolt -- a quick flick from the hip
	private static final float[][] BOLT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(1, -0.6f, 0.2f, 0.2f, 0f, 0f, 0f, 0f, 0.15f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
			QuirkKeyframes.f(3, A, A, 0f, 0.1f, 0f, -0.1f, 0.05f, -0.15f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
			QuirkKeyframes.f(5, A, A, 0f, 0.1f, 0f, -0.1f, 0.05f, -0.12f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), QuirkKeyframes.rest(9) };
	// v0.17.2: Shift+R Fire Stream -- palms drawn in to the chest, then both thrust down the aim, a wide braced stance (the
	// recoil shake is added in pose())
	private static final float[][] STREAM = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(2, -1.0f, 0f, 0.4f, -1.0f, 0f, -0.4f, -0.1f, 0f, 0.35f, 0f, 0.08f, -0.35f, 0f, -0.08f, -0.1f),
			QuirkKeyframes.f(5, A, A, 0f, A, A, 0f, 0.15f, 0f, 0.45f, 0f, 0.1f, -0.45f, 0f, -0.1f, 0f),
			QuirkKeyframes.f(48, A, A, 0f, A, A, 0f, 0.18f, 0f, 0.45f, 0f, 0.1f, -0.45f, 0f, -0.1f, 0f), QuirkKeyframes.rest(54) };
	// v0.17.2: G Pyro Bomb -- wound up high behind the head (leaning back, looking up), whipped over and down, followed through
	private static final float[][] BOMB = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(2, -2.9f, 0.2f, 0.35f, -0.9f, 0f, -0.2f, -0.2f, 0.35f, 0.35f, 0f, 0f, -0.4f, 0f, 0f, -0.25f),
			QuirkKeyframes.f(4, -1.9f, 0f, 0.1f, 0.2f, 0f, -0.1f, 0.1f, 0.05f, 0.4f, 0f, 0f, -0.4f, 0f, 0f, 0f),
			QuirkKeyframes.f(6, -0.9f, -0.2f, 0f, 0.5f, 0f, -0.15f, 0.3f, -0.3f, 0.4f, 0f, 0f, -0.35f, 0f, 0f, 0.05f),
			QuirkKeyframes.f(10, -0.6f, -0.25f, 0f, 0.35f, 0f, -0.1f, 0.22f, -0.25f, 0.35f, 0f, 0f, -0.3f, 0f, 0f, 0f), QuirkKeyframes.rest(16) };
	// v0.17.2: Shift+G Heat Shield -- arms crossed, hunched over, then flung wide with the head thrown back as it flares
	private static final float[][] SHIELD = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(3, -1.3f, -0.9f, 0.1f, -1.3f, 0.9f, -0.1f, 0.3f, 0f, 0.3f, 0f, 0.1f, -0.3f, 0f, -0.1f, 0.25f),
			QuirkKeyframes.f(6, -0.4f, 0f, 1.4f, -0.4f, 0f, -1.4f, -0.15f, 0f, 0f, 0f, 0.25f, 0f, 0f, -0.25f, -0.3f),
			QuirkKeyframes.f(10, -0.3f, 0f, 1.2f, -0.3f, 0f, -1.2f, -0.05f, 0f, 0f, 0f, 0.2f, 0f, 0f, -0.2f, -0.15f), QuirkKeyframes.rest(16) };
	// v0.17.2: Shift+X Jet Dash -- a coil, then flat out like a rocket, arms swept back (jets in the palms), legs trailing
	private static final float[][] DASH = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(1, -0.4f, 0f, 0.2f, -0.4f, 0f, -0.2f, 0.35f, 0f, -0.4f, 0f, 0f, 0.3f, 0f, 0f, -0.2f),
			QuirkKeyframes.f(3, 1.1f, 0f, 0.25f, 1.1f, 0f, -0.25f, 0.65f, 0f, 0.25f, 0f, 0.05f, 0.45f, 0f, -0.05f, -0.5f),
			QuirkKeyframes.f(9, 1.0f, 0f, 0.3f, 1.0f, 0f, -0.3f, 0.6f, 0f, 0.2f, 0f, 0.05f, 0.4f, 0f, -0.05f, -0.45f), QuirkKeyframes.rest(14) };
	// v0.17.2: Z Flame Ring -- both fists raised overhead, then slammed down to the ground on a lunge as the ring erupts
	private static final float[][] RING = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(3, -2.8f, 0f, 0.3f, -2.8f, 0f, -0.3f, -0.2f, 0f, 0.1f, 0f, 0.1f, -0.1f, 0f, -0.1f, -0.3f),
			QuirkKeyframes.f(5, -0.6f, 0f, 0.15f, -0.6f, 0f, -0.15f, 0.65f, 0f, -0.7f, 0f, 0.1f, 0.5f, 0f, -0.1f, -0.4f),
			QuirkKeyframes.f(10, -0.55f, 0f, 0.15f, -0.55f, 0f, -0.15f, 0.6f, 0f, -0.65f, 0f, 0.1f, 0.45f, 0f, -0.1f, -0.35f), QuirkKeyframes.rest(16) };
	// v0.17.2: Shift+Z Flare -- the light gathered in cupped hands at the chest, then thrown up and apart, head back
	private static final float[][] FLARE = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(3, -0.9f, -0.5f, 0f, -0.9f, 0.5f, 0f, 0.3f, 0f, 0.2f, 0f, 0f, -0.2f, 0f, 0f, 0.35f),
			QuirkKeyframes.f(5, -2.9f, 0f, 0.7f, -2.9f, 0f, -0.7f, -0.25f, 0f, 0f, 0f, 0.15f, 0f, 0f, -0.15f, -0.5f),
			QuirkKeyframes.f(10, -2.8f, 0f, 0.6f, -2.8f, 0f, -0.6f, -0.2f, 0f, 0f, 0f, 0.12f, 0f, 0f, -0.12f, -0.45f), QuirkKeyframes.rest(16) };
	// v0.17.2: C Fireball Nova -- curled in tight for a moment, then bursting out into a star
	private static final float[][] NOVA = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(1.5f, -0.6f, 0.7f, -0.3f, -0.6f, -0.7f, 0.3f, 0.5f, 0f, -0.4f, 0f, 0f, -0.4f, 0f, 0f, 0.45f),
			QuirkKeyframes.f(3.5f, -0.5f, 0f, 1.9f, -0.5f, 0f, -1.9f, -0.35f, 0f, 0.1f, 0f, 0.4f, 0.1f, 0f, -0.4f, -0.5f),
			QuirkKeyframes.f(11, -0.45f, 0f, 1.7f, -0.45f, 0f, -1.7f, -0.25f, 0f, 0.05f, 0f, 0.35f, 0.05f, 0f, -0.35f, -0.4f), QuirkKeyframes.rest(18) };
	// v0.17.2: Shift+C Supernova's charge -- hunched over the core cupped at his chest, tightening (and trembling, in pose())
	private static final float[][] CHARGE = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(4, -0.9f, 0.75f, -0.2f, -0.9f, -0.75f, 0.2f, 0.4f, 0f, -0.3f, 0f, 0.15f, -0.3f, 0f, -0.15f, 0.35f),
			QuirkKeyframes.f(25, -1.0f, 0.8f, -0.25f, -1.0f, -0.8f, 0.25f, 0.5f, 0f, -0.35f, 0f, 0.18f, -0.35f, 0f, -0.18f, 0.4f),
			QuirkKeyframes.f(27, -1.0f, 0.8f, -0.25f, -1.0f, -0.8f, 0.25f, 0.5f, 0f, -0.35f, 0f, 0.18f, -0.35f, 0f, -0.18f, 0.4f) };
	// ...and the blast: thrown open into a huge star, held while it rolls out
	private static final float[][] SUPERNOVA = { QuirkKeyframes.f(0, -1.0f, 0.8f, -0.25f, -1.0f, -0.8f, 0.25f, 0.5f, 0f, -0.35f, 0f, 0.18f, -0.35f, 0f, -0.18f, 0.4f),
			QuirkKeyframes.f(2, -0.6f, 0f, 2.3f, -0.6f, 0f, -2.3f, -0.45f, 0f, 0.15f, 0f, 0.5f, 0.15f, 0f, -0.5f, -0.7f),
			QuirkKeyframes.f(14, -0.5f, 0f, 2.1f, -0.5f, 0f, -2.1f, -0.35f, 0f, 0.1f, 0f, 0.45f, 0.1f, 0f, -0.45f, -0.6f), QuirkKeyframes.rest(24) };
	// v0.17.2: the flaming punch -- cocked back, driven out along the look with the hips, held through the hit
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(1, 0.4f, 0.3f, 0.2f, -0.5f, 0f, -0.1f, 0f, 0.35f, 0.2f, 0f, 0f, -0.2f, 0f, 0f, 0f),
			QuirkKeyframes.f(3, A, A, 0f, 0.45f, 0f, -0.1f, 0.15f, -0.45f, 0.3f, 0f, 0f, -0.3f, 0f, 0f, 0f),
			QuirkKeyframes.f(5, A, A, 0f, 0.4f, 0f, -0.1f, 0.12f, -0.4f, 0.28f, 0f, 0f, -0.28f, 0f, 0f, 0f), QuirkKeyframes.rest(10) };
	// v0.17.2: smelting -- hands cupped round the heat, kneading it
	private static final float[][] SMELT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.2f, -0.45f, 0f, -1.2f, 0.45f, 0f, 0.15f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.35f),
			QuirkKeyframes.f(7, -1.05f, -0.52f, 0f, -1.05f, 0.52f, 0f, 0.18f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.4f),
			QuirkKeyframes.f(12, -1.2f, -0.45f, 0f, -1.2f, 0.45f, 0f, 0.15f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.35f), QuirkKeyframes.rest(16) };
	// v0.17.2: X Fire Jet lift-off -- knees bent and palms down, then sprung straight up off the jets
	private static final float[][] LIFTOFF = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(2, 0.35f, 0f, 0.5f, 0.35f, 0f, -0.5f, 0.2f, 0f, -0.5f, 0f, 0.05f, -0.5f, 0f, -0.05f, 0f),
			QuirkKeyframes.f(4, 0.15f, 0f, 0.35f, 0.15f, 0f, -0.35f, -0.05f, 0f, 0.15f, 0f, 0f, 0.15f, 0f, 0f, -0.15f), QuirkKeyframes.rest(10) };

	// first person {tick, pitch, yaw, roll, x, y, z}: each pulled back a touch first, then thrust, then eased home
	private static final FirstPersonClip FP_BLAST = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 15, -10, 0, -0.05f, -0.05f, 0.12f),
			FirstPersonClip.f(4, -28, 18, 0, 0.14f, 0.12f, -0.16f), FirstPersonClip.f(7, -24, 16, 0, 0.12f, 0.1f, -0.12f), FirstPersonClip.f(13, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_BOLT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(1, 8, -5, 0, 0f, -0.03f, 0.06f),
			FirstPersonClip.f(3, -22, 14, 0, 0.1f, 0.08f, -0.12f), FirstPersonClip.f(9, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_STREAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 5, 10, 0, 0.1f, -0.02f, 0.1f),
			FirstPersonClip.f(5, -30, 22, 0, 0.18f, 0.16f, -0.14f), FirstPersonClip.f(48, -28, 20, 0, 0.17f, 0.15f, -0.12f), FirstPersonClip.f(54, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_BOMB = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -75, 0, 12, 0, 0.38f, 0.18f),
			FirstPersonClip.f(4, -10, 22, 0, 0.12f, 0.05f, -0.22f), FirstPersonClip.f(8, 5, 15, 0, 0.08f, -0.05f, -0.12f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SHIELD = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -35, 45, 0, 0.25f, 0.1f, 0.05f),
			FirstPersonClip.f(6, -25, -35, 25, -0.2f, 0.2f, 0), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_RING = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -80, 0, 10, 0, 0.4f, 0.1f),
			FirstPersonClip.f(5, 30, 10, 0, 0.05f, -0.35f, -0.1f), FirstPersonClip.f(10, 28, 10, 0, 0.05f, -0.33f, -0.1f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_FLARE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, 35, 0, 0.22f, 0.05f, 0.05f),
			FirstPersonClip.f(5, -85, -15, 20, -0.1f, 0.45f, 0), FirstPersonClip.f(10, -80, -12, 18, -0.1f, 0.42f, 0), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_NOVA = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(1.5f, -20, 35, 0, 0.2f, 0, 0.05f),
			FirstPersonClip.f(3.5f, -35, -40, 30, -0.25f, 0.2f, 0), FirstPersonClip.f(11, -32, -36, 26, -0.22f, 0.18f, 0), FirstPersonClip.f(18, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SUPERNOVA = FirstPersonClip.of(FirstPersonClip.f(0, -18, 38, 0, 0.22f, 0.0f, 0.05f),
			FirstPersonClip.f(2, -45, -50, 35, -0.3f, 0.3f, 0), FirstPersonClip.f(14, -40, -45, 30, -0.27f, 0.27f, 0), FirstPersonClip.f(24, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(1, 10, -8, 0, -0.03f, -0.05f, 0.1f),
			FirstPersonClip.f(3, -22, 26, 0, 0.16f, 0.08f, -0.22f), FirstPersonClip.f(5, -20, 24, 0, 0.15f, 0.08f, -0.18f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_CUP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, 30, 0, 0.18f, 0.12f, -0.05f),
			FirstPersonClip.f(7, -16, 33, 0, 0.2f, 0.1f, -0.03f), FirstPersonClip.f(12, -20, 30, 0, 0.18f, 0.12f, -0.05f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_CHARGE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -15, 35, 0, 0.2f, 0.0f, 0.05f),
			FirstPersonClip.f(27, -18, 38, 0, 0.22f, 0.0f, 0.05f)).both();
	private static final FirstPersonClip FP_DASH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(1, 10, 0, 0, 0, -0.05f, 0.1f),
			FirstPersonClip.f(3, 40, -10, 0, -0.05f, -0.3f, 0.15f), FirstPersonClip.f(9, 38, -10, 0, -0.05f, -0.28f, 0.14f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_LIFTOFF = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 35, 0, 0, 0, -0.3f, 0.1f),
			FirstPersonClip.f(4, 20, 0, 0, 0, -0.2f, 0.05f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0)).both();

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	/** The burning overlay frame for {@code age} (32 frames, 2 ticks each: lava pulsing through the cracks, flames up the rock). */
	private static ResourceLocation glow(float age) {
		return GLOW[((int) (age / 2f) % GLOW.length + GLOW.length) % GLOW.length];
	}

	/** Ticks since the synced move animation started. */
	private static float animTicks(AbstractClientPlayer p, float partial) {
		return (p.level().getGameTime() - Omnitrix.state(p).animStart) + partial;
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		// v0.17.1 (user: "add like a burning fire texture to the rest of his body"): the whole model again, additively, with the
		// burning overlay -- the skin's pale cracks run with lava, flames lick up the rock, the head glows from inside
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(glow(age))), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		// v0.17.1 (user: "make heatblasts head flames the same way you made ghost riders head flames ... make it flame
		// textures"): the burning-skull fire -- the vanilla animated fire sprites in a ring round the crown plus crossed sheets
		// rising off the top -- blazing up taller in the big bursts
		var st = Omnitrix.state(p);
		int anim = st.animId;
		float ticks = animTicks(p, partial);
		boolean big = anim == HeatblastKit.ANIM_NOVA || anim == HeatblastKit.ANIM_SUPERNOVA || anim == HeatblastKit.ANIM_FLARE || anim == HeatblastKit.ANIM_CHARGE;
		GhostRiderRender.renderSkullFire(pose, buffers, model.head, big ? 1.4f : 1f, age);

		// v0.17.2 (user: "give him the same flaming fire flight vfx as the pyrokinesis power please"): Pyrokinesis' Jet Flight
		// flames out of both palms and both soles while the Fire Jet flies.
		// v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): the Jet Dash is Pyrokinesis' Flame
		// Dash -- the whole figure wreathed in fire with flames streaming back off every limb (was the jets, bigger)
		boolean dashing = anim == HeatblastKit.ANIM_DASH && ticks < 11f;
		if (dashing) {
			PyrokinesisClient.bodyFire(pose, buffers, age, 1.1f);
			PyrokinesisClient.limbFlames(pose, buffers, model, false, age, 1.6f, 0.9f);
		} else if (HeatblastKit.flying(p)) {
			PyrokinesisClient.jetFlames(pose, buffers, model, false, age, 1.0f);
		}
		// (the Fire Stream is Pyrokinesis' Flamethrower cone, drawn in the world pass: renderWorld)
		// v0.17.2: the shots' palm flare (Pyrokinesis' jet cone), the punch's burning fist
		if (anim == HeatblastKit.ANIM_BLAST && ticks < 6f) {
			palmFire(pose, buffers, model.rightArm, -1f, 0.9f * (1f - ticks / 6f) + 0.2f, age);
		} else if (anim == HeatblastKit.ANIM_BOLT && ticks < 4f) {
			palmFire(pose, buffers, model.rightArm, -1f, 0.6f * (1f - ticks / 4f) + 0.15f, age);
		} else if (anim == HeatblastKit.ANIM_PUNCH && ticks < 5f) {
			handGlow(pose, buffers, model.rightArm, -1f, 0.32f * (1f - ticks / 5f) + 0.1f);
		}
		// v0.17.2: the Pyro Bomb swelling in the raised hand until it leaves it -- v0.17.2 Pyrokinesis pass: as a Meteor, the
		// way the bomb now flies
		if (anim == HeatblastKit.ANIM_BOMB && ticks < 4.5f) {
			pose.pushPose();
			model.rightArm.translateAndRotate(pose);
			float r = 0.14f + 0.16f * Mth.clamp(ticks / 2.5f, 0f, 1f);
			pose.translate(-1f / 16f, 11f / 16f + r * 0.6f, 0f);
			PyroDraw.fireball(pose, buffers, r, age, new Vec3(0, -1, 0), true);
			pose.popPose();
		}
		// v0.17.2: the Supernova's core growing between his cupped hands (Pyrokinesis' Meteor charging, held at the chest);
		// the smelt's small flame. (The white glow as it went off is gone -- the blast is Pyrokinesis' now.)
		if ((st.kitFlags & HeatblastKit.F_CHARGING) != 0) {
			float k = Mth.clamp(ticks / HeatblastKit.SUPERNOVA_CHARGE, 0f, 1f);
			chestBall(pose, buffers, model, (0.12f + 0.4f * k) * (0.94f + 0.06f * Mth.sin(age * 1.7f)), age, 0.42f);
		} else if (anim == HeatblastKit.ANIM_SMELT && ticks < 13f) {
			chestBall(pose, buffers, model, 0.1f + 0.02f * Mth.sin(age * 0.9f), age, 0.5f);
		}
		// v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): the Heat Shield is Pyrokinesis' Flame
		// Body -- real fire licking up off every limb (was a churning sphere of heat round him)
		if ((st.kitFlags & HeatblastKit.F_SHIELD) != 0) {
			PyrokinesisClient.limbFlames(pose, buffers, model, false, age, 1.25f, 0f);
		}
		// v0.17.2: the Heat Aura -- the ground round him glowing like a hotplate, out to the aura's edge; v0.17.2 Pyrokinesis
		// pass: and his hands white-hot, as a Pyrokinesis user's at full Heat
		if ((st.kitFlags & HeatblastKit.F_AURA) != 0) {
			float g = 0.3f + 0.08f * Mth.sin(age * 0.2f);
			PyroDraw.annulus(pose, PyroDraw.additive(buffers), 0f, (float) HeatblastKit.AURA_RADIUS, PyroDraw.dim(0xFFFF5A10, g), 0xFF000000, 1.48f);
			PyrokinesisClient.whiteHotHands(pose, buffers, model, 0.85f + 0.15f * (0.5f + 0.5f * Mth.sin(age * Mth.TWO_PI / 18f)));
		}
	}

	// ---------------------------------------------------------------- v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power")

	/** From {@code OmnitrixClient}: the shots drawn as Pyrokinesis' fireballs, the Fire Stream as its Flamethrower. */
	public static void init() {
		OmnitrixRenderers.projectileLook("heatblast_blast", (e, partial, pose, buffers) -> shot(e, partial, pose, buffers, false));
		OmnitrixRenderers.projectileLook("heatblast_bolt", (e, partial, pose, buffers) -> shot(e, partial, pose, buffers, false));
		OmnitrixRenderers.projectileLook("heatblast_bomb", (e, partial, pose, buffers) -> shot(e, partial, pose, buffers, true));
		WorldRenderEvents.AFTER_ENTITIES.register(HeatblastClient::renderWorld);
	}

	/** A shot in flight: Pyrokinesis' Fireball (Fire Blast, the Use bolt) or Meteor (the Pyro Bomb), at the shot's size. */
	private static void shot(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers, boolean meteor) {
		pose.pushPose();
		pose.translate(0.0, e.getBbHeight() * 0.5, 0.0);
		PyroBoltRenderer.draw(pose, buffers, e.size(), e.tickCount + partial, e.getDeltaMovement(), meteor, false);
		pose.popPose();
	}

	/** The Fire Stream of every Heatblast in view: Pyrokinesis' Flamethrower cone out of his palms. */
	private static void renderWorld(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		for (net.minecraft.world.entity.player.Player pl : mc.level.players()) {
			if (!Omnitrix.hasPower(pl)) {
				continue;
			}
			var st = Omnitrix.state(pl);
			if (HeatblastKit.ID.equals(st.active) && (st.kitFlags & HeatblastKit.F_STREAM) != 0 && !pl.isInWaterOrBubble()) {
				PyrokinesisClient.flamethrower(pl, partial, context.camera().getPosition(), context.matrixStack(), context.consumers(),
						HeatblastKit.STREAM_FX_RANGE);
			}
		}
	}

	/**
	 * v0.17.2: fire pouring out of a palm along the arm -- the jets' white-hot core and orange cone, a fire sprite and a glow.
	 * {@code side} -1 the right arm, +1 the left (the wide arm's centre line); {@code k} sizes it.
	 */
	private static void palmFire(PoseStack pose, MultiBufferSource buffers, ModelPart arm, float side, float k, float age) {
		float flick = 0.8f + 0.25f * Mth.sin(age * 2.4f + side) + 0.1f * Mth.sin(age * 5.1f);
		pose.pushPose();
		arm.translateAndRotate(pose);
		pose.translate(side / 16f, 10f / 16f + 0.01f, 0f);
		VertexConsumer add = PyroDraw.additive(buffers);
		PyroDraw.cone(pose, add, 0.11f * k, 0.6f * k * flick, 0xFFFFF4D0, 0xFF000000);
		PyroDraw.cone(pose, add, 0.2f * k, 1.0f * k * flick, 0xFFFF7A20, 0xFF000000);
		PyroDraw.fireCross(pose, PyroDraw.fireBuffer(buffers), 0.4f * k, 0.7f * k * flick, (int) (age / 2), age * 0.3f);
		PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), new Vec3(0, 0.12, 0), 0.38f * k, PyroDraw.dim(0xFFFF9040, 0.8f));
		pose.popPose();
	}

	/** v0.17.2: a soft glow round a fist. */
	private static void handGlow(PoseStack pose, MultiBufferSource buffers, ModelPart arm, float side, float r) {
		pose.pushPose();
		arm.translateAndRotate(pose);
		PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), new Vec3(side / 16f, 10.5 / 16f, 0), r, 0xFFFFA040);
		pose.popPose();
	}

	/** v0.17.2: a fireball held in front of the chest ({@code y} down the body, blocks), its tongues licking up. */
	private static void chestBall(PoseStack pose, MultiBufferSource buffers, PlayerModel<AbstractClientPlayer> model, float r, float age, float y) {
		pose.pushPose();
		model.body.translateAndRotate(pose);
		pose.translate(0f, y, -0.3f - r);
		PyroDraw.fireball(pose, buffers, r, age, new Vec3(0, 1, 0), false);
		pose.popPose();
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		// the burning overlay on the first-person arm too (the pose is already the arm's: draw its cube unmoved)
		float age = p.tickCount + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		PartPose was = arm.storePose();
		arm.setPos(0, 0, 0);
		arm.setRotation(0, 0, 0);
		arm.render(pose, buffers.getBuffer(RenderType.eyes(glow(age))), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
		// v0.17.2: the same palm flames in first person -- a flare as a shot leaves (v0.17.2 Pyrokinesis pass: the Fire Stream
		// is its Flamethrower cone, drawn in the world pass, so no palm fire for it here)
		var st = Omnitrix.state(p);
		float ticks = animTicks(p, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
		float side = right ? -1f : 1f;
		if (right && st.animId == HeatblastKit.ANIM_BLAST && ticks < 6f) {
			palmFire(pose, buffers, arm, side, 0.7f * (1f - ticks / 6f) + 0.15f, age);
		} else if (right && st.animId == HeatblastKit.ANIM_BOLT && ticks < 4f) {
			palmFire(pose, buffers, arm, side, 0.45f * (1f - ticks / 4f) + 0.1f, age);
		}
		arm.loadPose(was);
	}

	/** Plays a keyframe table, its {@link QuirkKeyframes#AIM} arm columns resolved to point down the look. */
	private static boolean playAimed(HumanoidModel<?> m, float[][] clip, float ticks) {
		float[] f = QuirkKeyframes.sample(clip, ticks, -Mth.HALF_PI + m.head.xRot, m.head.yRot);
		if (f == null) {
			return false;
		}
		QuirkKeyframes.apply(m, f, QuirkKeyframes.weight(ticks, QuirkKeyframes.length(clip)));
		return true;
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		float[][] clip = switch (animId) {
			case HeatblastKit.ANIM_BLAST -> BLAST;
			case HeatblastKit.ANIM_BOLT -> BOLT;
			case HeatblastKit.ANIM_STREAM -> STREAM;
			case HeatblastKit.ANIM_BOMB -> BOMB;
			case HeatblastKit.ANIM_SHIELD -> SHIELD;
			case HeatblastKit.ANIM_DASH -> DASH;
			case HeatblastKit.ANIM_RING -> RING;
			case HeatblastKit.ANIM_FLARE -> FLARE;
			case HeatblastKit.ANIM_NOVA -> NOVA;
			case HeatblastKit.ANIM_CHARGE -> CHARGE;
			case HeatblastKit.ANIM_SUPERNOVA -> SUPERNOVA;
			case HeatblastKit.ANIM_PUNCH -> PUNCH;
			case HeatblastKit.ANIM_SMELT -> SMELT;
			case HeatblastKit.ANIM_AURA -> AURA;
			case HeatblastKit.ANIM_LIFTOFF -> LIFTOFF;
			default -> null;
		};
		// v0.17.1 (user: "make heatblasts flying animation look better"): on the Fire Jet. The shared flight lean tips the body
		// (upright hovering, flat out fast); the limbs blend between a hover -- palms pushed down and out like jets, knees loose,
		// a slow bob -- and a rocket -- arms swept back along his sides, legs together and trailing, a flutter in the jet wash.
		// v0.17.2: laid down FIRST, so a move used in the air blends in and out of it instead of snapping back to it
		boolean flying = HeatblastKit.flying(p);
		if (flying) {
			float w = Mth.clamp((Math.abs(com.projecthero.mod.client.FlightPoseHelper.lean(p, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false))) - 8f) / 60f, 0f, 1f);
			float bob = Mth.sin(age * 0.18f);
			float flutter = Mth.sin(age * 0.9f) * 0.04f;
			m.rightArm.xRot = Mth.lerp(w, 0.12f + bob * 0.04f, 0.28f);
			m.leftArm.xRot = Mth.lerp(w, 0.12f - bob * 0.04f, 0.28f);
			m.rightArm.yRot = Mth.lerp(w, -0.1f, 0f);
			m.leftArm.yRot = Mth.lerp(w, 0.1f, 0f);
			m.rightArm.zRot = Mth.lerp(w, 0.62f, 0.16f);
			m.leftArm.zRot = Mth.lerp(w, -0.62f, -0.16f);
			m.rightLeg.xRot = Mth.lerp(w, -0.22f + bob * 0.06f, 0.06f + flutter);
			m.leftLeg.xRot = Mth.lerp(w, 0.18f - bob * 0.06f, 0.06f - flutter);
			m.rightLeg.zRot = Mth.lerp(w, 0.1f, 0.02f);
			m.leftLeg.zRot = Mth.lerp(w, -0.1f, -0.02f);
			m.rightLeg.yRot = 0f;
			m.leftLeg.yRot = 0f;
		}
		boolean played = clip != null && playAimed(m, clip, ticks);
		if (played) {
			float w = QuirkKeyframes.weight(ticks, QuirkKeyframes.length(clip));
			if (animId == HeatblastKit.ANIM_STREAM && ticks > 5f && ticks < 48f) {
				// the palms converge on the stream and kick with its recoil
				float kick = Mth.sin(age * 2.3f) * 0.035f + Mth.sin(age * 3.7f) * 0.015f;
				m.rightArm.yRot -= 0.18f * w;
				m.leftArm.yRot += 0.18f * w;
				m.rightArm.xRot += kick * w;
				m.leftArm.xRot -= kick * w;
			} else if (animId == HeatblastKit.ANIM_CHARGE) {
				// a tremble that builds as the core swells
				float k = Mth.clamp(ticks / HeatblastKit.SUPERNOVA_CHARGE, 0f, 1f) * w;
				float shake = Mth.sin(age * 3.3f) * 0.06f * k;
				m.rightArm.zRot += shake;
				m.leftArm.zRot -= shake;
				m.rightArm.xRot += Mth.sin(age * 4.1f) * 0.03f * k;
				m.leftArm.xRot += Mth.sin(age * 4.1f + 1f) * 0.03f * k;
				m.head.xRot += Mth.sin(age * 2.9f) * 0.025f * k;
				m.hat.copyFrom(m.head);
			} else if (animId == HeatblastKit.ANIM_SUPERNOVA && ticks < 14f) {
				// shuddering with the blast
				float shake = Mth.sin(age * 3.0f) * 0.05f * (1f - ticks / 14f) * w;
				m.rightArm.zRot += shake;
				m.leftArm.zRot -= shake;
			}
		}
		return played || flying;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case HeatblastKit.ANIM_BLAST -> FP_BLAST;
			case HeatblastKit.ANIM_BOLT -> FP_BOLT;
			case HeatblastKit.ANIM_STREAM -> FP_STREAM;
			case HeatblastKit.ANIM_BOMB -> FP_BOMB;
			case HeatblastKit.ANIM_SHIELD -> FP_SHIELD;
			case HeatblastKit.ANIM_RING -> FP_RING;
			case HeatblastKit.ANIM_FLARE -> FP_FLARE;
			case HeatblastKit.ANIM_NOVA -> FP_NOVA;
			case HeatblastKit.ANIM_SUPERNOVA -> FP_SUPERNOVA;
			case HeatblastKit.ANIM_CHARGE -> FP_CHARGE;
			case HeatblastKit.ANIM_PUNCH -> FP_PUNCH;
			case HeatblastKit.ANIM_SMELT -> FP_CUP;
			case HeatblastKit.ANIM_AURA -> FP_AURA;
			case HeatblastKit.ANIM_DASH -> FP_DASH;
			case HeatblastKit.ANIM_LIFTOFF -> FP_LIFTOFF;
			default -> null;
		};
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		var level = p.level();
		var r = p.getRandom();
		double top = p.getY() + p.getBbHeight();
		if (p.isInWaterOrBubble()) {
			if (r.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.BUBBLE, p.getX(), top, p.getZ(), 0, 0.1, 0);
			}
			return;
		}
		// flames off the head, embers drifting from the body
		if (r.nextInt(2) == 0) {
			level.addParticle(ParticleTypes.FLAME, p.getX() + (r.nextDouble() - 0.5) * 0.35, top - 0.05, p.getZ() + (r.nextDouble() - 0.5) * 0.35, 0, 0.04, 0);
		}
		if (r.nextInt(5) == 0) {
			level.addParticle(ParticleTypes.SMALL_FLAME, p.getX() + (r.nextDouble() - 0.5) * 0.6, p.getY() + r.nextDouble() * p.getBbHeight(),
					p.getZ() + (r.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
		}
		// v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): the Fire Jet, the Fire Stream and the
		// Heat Aura no longer spray client particles of their own (the flight's smoke trail, lava, flame rings and palm drips,
		// the stream's gout, the aura's shimmer): like Pyrokinesis, the jets / Flamethrower cone / white-hot hands are drawn as
		// models and the server sends Pyrokinesis' own exhaust, spray and shimmer
	}


	@Override
	public float[] badge() {
		// v0.17.1 (user: "position our alien badges more on top of those alien badges"): over the skin's own symbol (now painted
		// out), the middle of his chest
		return new float[] { 0f, 4.0f, -2.06f, 3.0f };
	}
}
