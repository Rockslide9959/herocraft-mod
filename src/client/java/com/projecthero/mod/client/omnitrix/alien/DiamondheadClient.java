package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadBlocks;
import com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1: Diamondhead's client kit -- the faceted green Petrosapien skin, a pointed crystal head (a stepped diamond turned 45
 * degrees, cheek fins), four faceted crystal spikes out of his back (two long, two short). A pose for every move. Also hands
 * his crystal block to the translucent render layer.
 *
 * <p>v0.17.2 round 3 (user: "make the shield always show up on the players left hand, only make them hold the shield in front of them
 * when they are right clicking, only have 1 blade on the right arm"): Combat Mode's long faceted crystal BLADE out of the right forearm,
 * and the Crystal SHIELD -- a faceted green tower shield strapped along the left forearm for as long as Combat Mode lasts, swung round
 * in front of him (upright, facing ahead, whatever the arm does) only while right-click is held, cracking as it takes damage. Third
 * and first person alike. Also the Crystal Maul's hammer and the Crystal Spear in his hand before it is thrown.
 */
public final class DiamondheadClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/diamondhead.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/diamondhead_extras.png");

	private static final float[] CRYSTAL = { 0f, 0f, 0.5f, 0.5f };
	private static final float[] DARK = { 0.5f, 0f, 0.75f, 0.25f };
	private static final float[] GLOW = { 0.75f, 0f, 1f, 0.25f };
	private static final float[] PALE = { 0.5f, 0.25f, 0.75f, 0.5f };

	/** The Shard Volley's stance (arms level, along the look: pose adds the head's pitch): his legs stay his own, so he can walk and fire. */
	private static final float[] VOLLEY_AIM = QuirkKeyframes.f(0, -1.42f, -0.1f, 0, -1.42f, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
	private static final int VOLLEY_MASK = QuirkKeyframes.bit(QuirkKeyframes.R_ARM_X) | QuirkKeyframes.bit(QuirkKeyframes.R_ARM_Y) | QuirkKeyframes.bit(QuirkKeyframes.R_ARM_Z)
			| QuirkKeyframes.bit(QuirkKeyframes.L_ARM_X) | QuirkKeyframes.bit(QuirkKeyframes.L_ARM_Y) | QuirkKeyframes.bit(QuirkKeyframes.L_ARM_Z)
			| QuirkKeyframes.bit(QuirkKeyframes.BODY_Y);
	// each clump: the throwing hand punches out as the shoulders turn behind it and the other hand draws back, then the stance again
	private static final float[][] VOLLEY_R = { VOLLEY_AIM, QuirkKeyframes.f(2, -1.64f, -0.18f, 0, -1.2f, 0.16f, 0, 0, -0.32f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(5, -1.42f, -0.1f, 0, -1.42f, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.f(14, -1.42f, -0.1f, 0, -1.42f, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0) };
	private static final float[][] VOLLEY_L = { VOLLEY_AIM, QuirkKeyframes.f(2, -1.2f, -0.16f, 0, -1.64f, 0.18f, 0, 0, 0.32f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(5, -1.42f, -0.1f, 0, -1.42f, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.f(14, -1.42f, -0.1f, 0, -1.42f, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0) };
	// the Crystal Spear: side-on, the free arm pointing the way as the right arm goes back with the spear, then the whole body behind the throw
	private static final float[][] SPEAR = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -2.95f, 0.3f, 0.3f, -1.35f, 0.25f, -0.2f, -0.2f, 0.65f, 0.25f, 0, 0, -0.35f, 0, 0, -0.2f),
			QuirkKeyframes.f(5, -2.95f, 0.3f, 0.3f, -1.35f, 0.25f, -0.2f, -0.2f, 0.65f, 0.25f, 0, 0, -0.35f, 0, 0, -0.2f),
			QuirkKeyframes.f(8, -1.05f, -0.25f, 0, 0.35f, 0, -0.1f, 0.45f, -0.5f, -0.35f, 0, 0, 0.45f, 0, 0, 0.25f), QuirkKeyframes.rest(16) };
	// the Spike Line: both fists raised overhead, then driven down into the ground in a crouch (at 6, when the first spike bursts up)
	private static final float[][] SHATTER = { QuirkKeyframes.restC(0), QuirkKeyframes.fc(3, -2.9f, 0, 0.2f, -2.9f, 0, -0.2f, -0.2f, 0, 0, 0, 0, 0, 0, 0, -0.3f, 0),
			QuirkKeyframes.fc(6, -0.5f, 0, 0.1f, -0.5f, 0, -0.1f, 0.7f, 0, -0.6f, 0, 0.2f, -0.6f, 0, -0.2f, 0.4f, 0.8f),
			QuirkKeyframes.fc(14, -0.4f, 0, 0.1f, -0.4f, 0, -0.1f, 0.6f, 0, -0.5f, 0, 0.2f, -0.5f, 0, -0.2f, 0.3f, 0.7f), QuirkKeyframes.restC(20) };
	private static final float[][] SLIDE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.2f, 0, 1.3f, -0.2f, 0, -1.3f, 0.2f, 0.9f, -0.4f, 0.3f, 0.2f, 0.3f, 0.3f, -0.2f, 0),
			QuirkKeyframes.f(18, -0.2f, 0, 1.2f, -0.2f, 0, -1.2f, 0.2f, 0.9f, -0.4f, 0.3f, 0.2f, 0.3f, 0.3f, -0.2f, 0), QuirkKeyframes.rest(22) };
	// the Crystal Vault: riding the pillar up, one fist punched at the sky, the other arm down at his side, knees bent under him
	private static final float[][] VAULT = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(3, -3.05f, 0, 0.12f, 0.12f, 0, -0.3f, -0.12f, 0, 0.35f, 0, 0.1f, -0.45f, 0, -0.1f, -0.45f),
			QuirkKeyframes.f(14, -2.95f, 0, 0.16f, 0.2f, 0, -0.35f, -0.08f, 0, 0.3f, 0, 0.1f, -0.35f, 0, -0.1f, -0.35f), QuirkKeyframes.rest(20) };
	// the Crystal Construct: crouched with both palms low, then heaving up as the crystal climbs in front of him
	private static final float[][] WALL = { QuirkKeyframes.restC(0),
			QuirkKeyframes.fc(3, -0.45f, 0, 0.3f, -0.45f, 0, -0.3f, 0.5f, 0, 0, 0, 0.12f, 0, 0, -0.12f, -0.2f, 0.8f),
			QuirkKeyframes.fc(9, -1.95f, -0.2f, 0.1f, -1.95f, 0.2f, -0.1f, -0.12f, 0, -0.3f, 0, 0.12f, 0.3f, 0, -0.12f, 0.1f, 0f),
			QuirkKeyframes.fc(14, -1.85f, -0.2f, 0.1f, -1.85f, 0.2f, -0.1f, -0.08f, 0, -0.3f, 0, 0.12f, 0.3f, 0, -0.12f, 0.1f, 0f), QuirkKeyframes.restC(19) };
	/**
	 * v0.17.2 (user: "lift up 3 giant diamond shards from the ground and then throw them at his target ... good animations"): the
	 * Titan Shards -- he crouches, palms to the ground (the shards crack up at 4 / 10 / 16), heaves his arms up as they climb,
	 * holds them high, then hurls: the right arm at 42, the left at 50, and both arms down and forward for the big one at 58.
	 * {tick, rArm xyz, lArm xyz, body xy, rLeg xyz, lLeg xyz, headX}
	 */
	private static final float[][] BARRAGE = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(5, -0.45f, 0, 0.55f, -0.45f, 0, -0.55f, 0.35f, 0, -0.35f, 0, 0.12f, 0.25f, 0, -0.12f, 0.45f),
			QuirkKeyframes.f(12, -0.6f, 0, 0.6f, -0.6f, 0, -0.6f, 0.3f, 0, -0.3f, 0, 0.12f, 0.2f, 0, -0.12f, 0.35f),
			QuirkKeyframes.f(28, -2.5f, 0, -0.45f, -2.5f, 0, 0.45f, -0.12f, 0, 0, 0, 0.15f, 0, 0, -0.15f, -0.35f),
			QuirkKeyframes.f(38, -2.95f, 0, -0.38f, -2.95f, 0, 0.38f, -0.2f, 0, 0.05f, 0, 0.15f, 0.05f, 0, -0.15f, -0.3f),
			QuirkKeyframes.f(41, -3.05f, 0.2f, -0.3f, -2.95f, 0, 0.38f, -0.22f, -0.35f, 0.1f, 0, 0.15f, 0, 0, -0.15f, -0.2f),
			QuirkKeyframes.f(44, -1.35f, -0.25f, 0.05f, -2.85f, 0, 0.38f, 0.25f, 0.4f, -0.2f, 0, 0.15f, 0.25f, 0, -0.15f, 0),
			QuirkKeyframes.f(48, -1.0f, 0, 0.3f, -3.05f, -0.2f, 0.3f, -0.2f, 0.35f, 0.1f, 0, 0.15f, 0, 0, -0.15f, -0.2f),
			QuirkKeyframes.f(52, -1.0f, 0, 0.3f, -1.35f, 0.25f, -0.05f, 0.25f, -0.4f, 0.25f, 0, 0.15f, -0.2f, 0, -0.15f, 0),
			QuirkKeyframes.f(56, -3.05f, 0, -0.2f, -3.05f, 0, 0.2f, -0.3f, 0, 0.1f, 0, 0.15f, 0.1f, 0, -0.15f, -0.35f),
			QuirkKeyframes.f(60, -1.2f, 0, 0.1f, -1.2f, 0, -0.1f, 0.4f, 0, -0.45f, 0, 0.15f, 0.35f, 0, -0.15f, 0.15f),
			QuirkKeyframes.f(66, -1.1f, 0, 0.1f, -1.1f, 0, -0.1f, 0.3f, 0, -0.35f, 0, 0.12f, 0.25f, 0, -0.12f, 0.1f), QuirkKeyframes.rest(76) };
	/** The Titan Shards in first person: hands low, then heaved up out of sight as the shards climb, thrown right, left, both. */
	private static final FirstPersonClip FP_TITAN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0),
			FirstPersonClip.f(6, 25, 0, 0, 0.05f, -0.25f, 0), FirstPersonClip.f(28, -55, 0, 10, -0.05f, 0.5f, 0.1f),
			FirstPersonClip.f(40, -62, 0, 10, -0.05f, 0.55f, 0.12f), FirstPersonClip.f(44, 15, 0, 0, 0, 0.05f, -0.35f),
			FirstPersonClip.f(48, -55, 0, 10, -0.05f, 0.5f, 0.1f), FirstPersonClip.f(52, 15, 0, 0, 0, 0.05f, -0.35f),
			FirstPersonClip.f(56, -65, 0, 10, -0.05f, 0.6f, 0.12f), FirstPersonClip.f(60, 25, 0, 0, 0, -0.05f, -0.4f),
			FirstPersonClip.f(72, 0, 0, 0, 0, 0, 0)).both();
	// the Crystal Maul: the right arm hauled up and back as the hammer swells on it, the left arm reaching up to help, up on his toes; at 11 the
	// whole body comes down behind it into a deep crouch, held a moment while the ground cracks
	private static final float[][] MAUL = { QuirkKeyframes.restC(0),
			QuirkKeyframes.fc(5, -2.5f, 0.2f, 0.35f, -1.5f, -0.2f, -0.3f, -0.2f, 0.25f, 0.1f, 0, 0.1f, -0.2f, 0, -0.1f, -0.2f, 0f),
			QuirkKeyframes.fc(10, -3.1f, 0.1f, 0.2f, -2.4f, -0.1f, -0.25f, -0.38f, 0.1f, 0.2f, 0, 0.1f, -0.3f, 0, -0.1f, -0.3f, 0f),
			QuirkKeyframes.fc(12, -0.7f, -0.1f, 0.1f, -0.55f, 0.1f, -0.3f, 0.7f, -0.1f, -0.6f, 0, 0.15f, 0.25f, 0, -0.1f, 0.45f, 0.95f),
			QuirkKeyframes.fc(18, -0.65f, -0.1f, 0.1f, -0.5f, 0.1f, -0.3f, 0.62f, -0.1f, -0.55f, 0, 0.15f, 0.2f, 0, -0.1f, 0.4f, 0.85f), QuirkKeyframes.restC(25) };
	// the Prism Spire: crouched, palms to the ground, then both hands drawn up as the crystal climbs out of it, and held there a moment
	private static final float[][] SPIRE = { QuirkKeyframes.restC(0),
			QuirkKeyframes.fc(5, -0.35f, 0, 0.4f, -0.35f, 0, -0.4f, 0.6f, 0, -0.2f, 0, 0.15f, 0.2f, 0, -0.15f, 0.35f, 0.95f),
			QuirkKeyframes.fc(13, -2.55f, 0, 0.3f, -2.55f, 0, -0.3f, -0.22f, 0, -0.1f, 0, 0.12f, 0.1f, 0, -0.12f, -0.3f, 0f),
			QuirkKeyframes.fc(18, -2.45f, 0, 0.32f, -2.45f, 0, -0.32f, -0.18f, 0, -0.1f, 0, 0.12f, 0.1f, 0, -0.12f, -0.25f, 0f), QuirkKeyframes.restC(24) };
	// the Crystal Pillar: one hand raised and driven down at the ground (at 6), the other out for balance, the head up on the target, then
	// wrenched back up as the pillar bursts
	private static final float[][] PILLAR = { QuirkKeyframes.restC(0),
			QuirkKeyframes.fc(4, -2.85f, 0.1f, 0.3f, -0.7f, 0, -0.55f, -0.2f, 0.3f, 0.1f, 0, 0.1f, -0.2f, 0, -0.1f, -0.2f, 0.1f),
			QuirkKeyframes.fc(7, -0.3f, 0, 0.1f, -0.9f, 0, -0.7f, 0.72f, -0.2f, -0.75f, 0, 0.15f, 0.25f, 0, -0.1f, -0.1f, 0.95f),
			QuirkKeyframes.fc(11, -0.35f, 0, 0.1f, -0.85f, 0, -0.65f, 0.65f, -0.2f, -0.7f, 0, 0.15f, 0.2f, 0, -0.1f, -0.1f, 0.85f),
			QuirkKeyframes.fc(15, -1.6f, 0, 0.25f, -0.5f, 0, -0.3f, 0.05f, 0.1f, -0.2f, 0, 0.1f, 0.1f, 0, -0.1f, 0, 0.1f), QuirkKeyframes.restC(21) };
	private static final float[][] SLASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.9f, 0.9f, 0.4f, 0.2f, 0, 0, 0, 0.5f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(5, -1.2f, -1.0f, -0.2f, 0.3f, 0, 0, 0.2f, -0.5f, 0.2f, 0, 0, -0.2f, 0, 0, 0), QuirkKeyframes.rest(10) };
	private static final float[][] PLACE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.1f, 0, 0, 0, 0, 0, 0.15f, 0, 0, 0, 0, 0, 0, 0, 0.3f), QuirkKeyframes.rest(8) };
	// v0.17.2 (user: "Make Z an aoe spike ability"): the Crystal Eruption -- both fists hauled up overhead, then a dropping
	// slam into the ground on one knee-deep crouch, held while the rings burst out
	private static final float[][] ERUPT = { QuirkKeyframes.restC(0),
			QuirkKeyframes.fc(4, -3.0f, 0.15f, 0.35f, -3.0f, -0.15f, -0.35f, -0.25f, 0, 0.1f, 0, 0, 0.1f, 0, 0, -0.35f, 0.1f),
			QuirkKeyframes.fc(7, -0.35f, 0, 0.15f, -0.35f, 0, -0.15f, 0.75f, 0, -0.7f, 0, 0.15f, 0.2f, 0, -0.1f, 0.45f, 1.0f),
			QuirkKeyframes.fc(17, -0.3f, 0, 0.15f, -0.3f, 0, -0.15f, 0.7f, 0, -0.65f, 0, 0.15f, 0.2f, 0, -0.1f, 0.4f, 0.95f), QuirkKeyframes.restC(24) };
	// ---- v0.17.2 revamp
	// Combat Mode on: the right forearm held across the chest as the blade grows out of it, the left arm up as the shield forms on it, then
	// the blade flung out to his side and the shield dropped to guard
	private static final float[][] COMBAT_ON = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -1.35f, -0.95f, 0, -1.0f, 0.55f, -0.1f, 0.15f, 0.2f, 0, 0, 0, 0, 0, 0, 0.2f),
			QuirkKeyframes.f(9, -0.65f, 0.55f, 0.75f, -0.5f, 0, -0.3f, -0.05f, -0.2f, -0.2f, 0, 0.1f, 0.2f, 0, -0.1f, -0.1f), QuirkKeyframes.rest(15) };
	// off: a shake of both arms as the crystal shatters away
	private static final float[][] COMBAT_OFF = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.9f, 0, 0.35f, -0.9f, 0, -0.35f, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0.15f),
			QuirkKeyframes.f(6, -0.4f, 0, 0.15f, -0.4f, 0, -0.15f, 0, 0, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(10) };
	// the shield going up: a brace behind it (the left arm's raise comes from shieldRaise)
	private static final float[][] SHIELD = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0, 0, 0, -1.1f, 0.55f, 0, 0.15f, 0.1f, -0.25f, 0, 0, 0.25f, 0, 0, 0),
			QuirkKeyframes.rest(7) };
	// the Shield Bash: lunging forward low behind it
	private static final float[][] BASH = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(3, -0.5f, 0, 0.25f, -1.35f, 0.3f, 0, 0.35f, 0.15f, 0.55f, 0, 0, -0.7f, 0, 0, -0.2f),
			QuirkKeyframes.f(10, -0.5f, 0, 0.25f, -1.35f, 0.3f, 0, 0.35f, 0.15f, -0.6f, 0, 0, 0.6f, 0, 0, -0.2f), QuirkKeyframes.rest(14) };
	// the Blade Stab: weight dropped back with the blade drawn to the hip, then a full lunge, the arm driven straight along the look and held in
	// the wound a moment before it is pulled out (the blow lands at 4)
	private static final float[][] STAB = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.55f, 0.35f, 0.3f, -0.55f, 0, -0.25f, -0.08f, 0.55f, 0.3f, 0, 0, -0.25f, 0, 0, 0),
			QuirkKeyframes.f(5, -1.62f, -0.12f, 0, -0.3f, 0, -0.35f, 0.32f, -0.4f, -0.6f, 0, 0, 0.6f, 0, 0, -0.15f),
			QuirkKeyframes.f(11, -1.58f, -0.12f, 0, -0.3f, 0, -0.35f, 0.3f, -0.4f, -0.55f, 0, 0, 0.55f, 0, 0, -0.15f), QuirkKeyframes.rest(16) };
	// the Crystal Cyclone: the blade held straight out at his right, the shield tucked in, knees bent -- spinDegrees turns all of him round
	private static final float[][] CYCLONE = { QuirkKeyframes.restC(0),
			QuirkKeyframes.fc(2, -1.55f, 1.25f, 0, -0.65f, 0, -0.2f, 0.12f, 0, -0.15f, 0, 0.18f, 0.15f, 0, -0.18f, -0.1f, 0.55f),
			QuirkKeyframes.fc(11, -1.55f, 1.05f, 0, -0.65f, 0, -0.2f, 0.12f, 0, -0.15f, 0, 0.18f, 0.15f, 0, -0.18f, -0.1f, 0.55f), QuirkKeyframes.restC(15) };
	// the shield shattering: knocked back a step, the arm flung wide
	private static final float[][] SHIELD_BREAK = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.3f, 0, 0.4f, -0.6f, 0, -1.1f, -0.25f, 0, -0.3f, 0, 0, 0.3f, 0, 0, -0.3f),
			QuirkKeyframes.rest(12) };

	// the Shard Volley: both hands out ahead, the one throwing punched deeper
	private static float[] fpAim(float t) {
		return FirstPersonClip.f(t, -38, 14, 0, 0.13f, 0.14f, -0.2f);
	}

	private static float[] fpThrust(float t) {
		return FirstPersonClip.f(t, -50, 22, 0, 0.17f, 0.2f, -0.5f);
	}

	private static final FirstPersonClip FP_VOLLEY = FirstPersonClip.of(fpAim(0), fpThrust(2), fpAim(5), fpAim(14));
	private static final FirstPersonClip FP_VOLLEY_FIRST = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), fpThrust(2), fpAim(5), fpAim(14));
	private static final FirstPersonClip FP_VOLLEY_IDLE = FirstPersonClip.of(fpAim(0), fpAim(14));
	private static final FirstPersonClip FP_VOLLEY_RISE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), fpAim(4), fpAim(14));
	private static final FirstPersonClip FP_VOLLEY_END = FirstPersonClip.of(fpAim(0), FirstPersonClip.f(7, 0, 0, 0, 0, 0, 0)).both();
	/** The left arm brought up behind the raised shield (run by the raise, 0..4). */
	private static final FirstPersonClip FP_SHIELD_UP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -40, 5, 0, 0.08f, 0.16f, 0.12f));
	private static final FirstPersonClip FP_SPEAR = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -80, 0, 15, 0.05f, 0.5f, 0.25f),
			FirstPersonClip.f(5, -80, 0, 15, 0.05f, 0.5f, 0.25f), FirstPersonClip.f(8, -10, 25, 0, 0.15f, 0.05f, -0.4f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SLAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -80, 5, 0, 0.05f, 0.5f, 0.2f),
			FirstPersonClip.f(6, 35, 10, 0, 0.05f, -0.35f, -0.1f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_HEAVE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 25, 10, 0, 0, -0.25f, 0),
			FirstPersonClip.f(9, -45, 25, 0, 0.2f, 0.25f, -0.3f), FirstPersonClip.f(14, -43, 25, 0, 0.2f, 0.22f, -0.3f), FirstPersonClip.f(19, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_MAUL = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(5, -60, 0, 8, 0.02f, 0.35f, 0.15f),
			FirstPersonClip.f(10, -105, 0, 10, 0.05f, 0.72f, 0.3f), FirstPersonClip.f(12, 42, 6, 0, 0.1f, -0.42f, -0.3f), FirstPersonClip.f(18, 38, 6, 0, 0.1f, -0.4f, -0.28f),
			FirstPersonClip.f(25, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SPIRE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(5, 35, 10, 0, 0.02f, -0.35f, 0),
			FirstPersonClip.f(13, -72, -12, 18, -0.05f, 0.42f, 0.05f), FirstPersonClip.f(18, -70, -12, 18, -0.05f, 0.4f, 0.05f), FirstPersonClip.f(24, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PILLAR = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -85, 5, 0, 0.05f, 0.55f, 0.2f),
			FirstPersonClip.f(7, 48, 10, 0, 0.08f, -0.45f, -0.25f), FirstPersonClip.f(11, 44, 10, 0, 0.08f, -0.42f, -0.22f), FirstPersonClip.f(15, -30, 5, 0, 0.05f, 0.1f, 0),
			FirstPersonClip.f(21, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_RAISE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -80, -10, 12, -0.05f, 0.5f, 0.12f),
			FirstPersonClip.f(14, -78, -10, 12, -0.05f, 0.48f, 0.12f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SURF = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 10, -45, 40, -0.35f, 0.05f, 0.1f),
			FirstPersonClip.f(18, 10, -45, 40, -0.35f, 0.05f, 0.1f), FirstPersonClip.f(22, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SLASH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -40, -40, -20, -0.2f, 0.25f, 0),
			FirstPersonClip.f(5, -10, 50, 20, 0.35f, 0.0f, -0.25f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_PLACE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -15, 15, 0, 0.1f, 0.0f, -0.2f),
			FirstPersonClip.f(8, 0, 0, 0, 0, 0, 0));
	/** v0.17.2: the Crystal Eruption's slam -- both fists up out of view, then driven down hard past the bottom of the screen. */
	private static final FirstPersonClip FP_ERUPT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -95, 5, 0, 0.05f, 0.6f, 0.25f),
			FirstPersonClip.f(7, 50, 10, 0, 0.05f, -0.5f, -0.2f), FirstPersonClip.f(17, 45, 10, 0, 0.05f, -0.45f, -0.18f), FirstPersonClip.f(24, 0, 0, 0, 0, 0, 0)).both();
	// ---- v0.17.2 revamp
	private static final FirstPersonClip FP_COMBAT_ON = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -50, 35, 0, 0.18f, 0.25f, 0),
			FirstPersonClip.f(9, -20, -20, 0, -0.1f, 0.05f, 0), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_COMBAT_OFF = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -30, 20, 0, 0.1f, 0.1f, 0),
			FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0)).both();
	/** The Blade Stab: drawn back to the hip, then driven straight into the screen and held there a moment. */
	private static final FirstPersonClip FP_STAB = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 12, 0, 0, 0, -0.12f, 0.3f),
			FirstPersonClip.f(5, -28, 8, 0, 0.14f, 0.14f, -0.75f), FirstPersonClip.f(11, -27, 8, 0, 0.14f, 0.14f, -0.7f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_CYCLONE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -25, -55, -35, -0.35f, 0.1f, 0),
			FirstPersonClip.f(7, -25, 70, 35, 0.5f, 0.1f, -0.2f), FirstPersonClip.f(11, -25, 70, 35, 0.75f, 0.1f, -0.1f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_BASH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -25, 20, 0, 0.1f, 0.08f, -0.35f),
			FirstPersonClip.f(10, -25, 20, 0, 0.1f, 0.08f, -0.35f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SHIELD_BREAK = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 20, -15, 0, -0.12f, -0.2f, 0.2f),
			FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();

	public DiamondheadClient() {
		// his conjured crystal is see-through
		net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(DiamondheadBlocks.CRYSTAL, RenderType.translucent());
		// v0.17.2 (user: "for diamondhead make spike projectiles for his spike projectile abilities instead of just glowing orbs")
		OmnitrixRenderers.projectileLook("diamondhead_shard", (e, partial, pose, buffers) -> shardShot(e, partial, pose, buffers, false));
		OmnitrixRenderers.projectileLook("diamondhead_spear", (e, partial, pose, buffers) -> shardShot(e, partial, pose, buffers, true));
		// v0.17.2: the Titan Shards -- the same cut crystal, giant
		OmnitrixRenderers.projectileLook("diamondhead_titan", (e, partial, pose, buffers) -> shardShot(e, partial, pose, buffers, false));
		// v0.17.2 round 3: the Prism Spire -- the same crystal again, standing
		OmnitrixRenderers.projectileLook("diamondhead_spire", (e, partial, pose, buffers) -> crystalShot(e, partial, pose, buffers, false, new net.minecraft.world.phys.Vec3(0, 1, 0)));
	}

	// ---------------------------------------------------------------- v0.17.2: the shard and spear projectiles

	private static final ResourceLocation GLOW_TEX = ProjectHeroMod.id("textures/entity/omnitrix/glow.png");
	/** A flung shard's profile along its flight, tail (0) to point (1): {t, radius scale} -- a short butt, the widest facet ring, the long point. */
	private static final float[][] SHARD_PROFILE = { { 0f, 0f }, { 0.1f, 0.72f }, { 0.28f, 1.0f }, { 0.55f, 0.82f }, { 1f, 0f } };
	/** The spear's: a long, slender shaft between its butt and its blade. */
	private static final float[][] SPEAR_PROFILE = { { 0f, 0f }, { 0.06f, 0.55f }, { 0.4f, 0.62f }, { 0.58f, 1.0f }, { 0.72f, 0.85f }, { 1f, 0f } };

	/**
	 * A Diamondhead shard / Crystal Spear in flight: a faceted hexagonal crystal (see-through green-teal, every facet shaded by
	 * which way it faces) pointed along its velocity and turning slowly about it, a glowing core inside, and a glint that
	 * flashes across it now and then. The spear is a long slender crystal with three smaller shards flaring off its butt.
	 */
	private static void shardShot(com.projecthero.mod.omnitrix.entity.AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers,
			boolean spear) {
		crystalShot(e, partial, pose, buffers, spear, null);
	}

	/** As above; {@code pointing}, if given, is the way the crystal points (the Prism Spire stands straight up, whatever the entity's turn). */
	private static void crystalShot(com.projecthero.mod.omnitrix.entity.AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers,
			boolean spear, net.minecraft.world.phys.Vec3 pointing) {
		net.minecraft.world.phys.Vec3 v = pointing != null ? pointing : e.getDeltaMovement();
		if (v.lengthSqr() < 1.0e-6) {
			v = net.minecraft.world.phys.Vec3.directionFromRotation(e.getXRot(), e.getYRot());
		}
		net.minecraft.world.phys.Vec3 dir = v.normalize();
		float age = e.tickCount + partial;
		float s = e.size();
		// grows out of his hand over the first two ticks rather than popping in whole
		float grow = Mth.clamp(0.45f + age * 0.28f, 0.45f, 1f);
		float len = (spear ? s * 3.9f : s * 3.4f) * grow;
		float r = (spear ? s * 0.32f : s * 0.42f) * grow;
		int block = Math.max(10, LightTexture.block(net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getPackedLightCoords(e, partial)));
		int light = LightTexture.pack(block, 15);
		pose.pushPose();
		pose.translate(0.0, e.getBbHeight() * 0.5, 0.0);
		// the glint: a four-point star flashing across the crystal (camera-facing; drawn before the crystal turns the pose)
		float flash = Mth.sin(age * (spear ? 0.35f : 0.55f) + e.getId());
		if (flash > 0.55f) {
			float k = (flash - 0.55f) / 0.45f;
			float along = (spear ? 0.22f : 0.12f) * len;
			pose.pushPose();
			pose.translate(dir.x * along, dir.y * along, dir.z * along);
			glint(pose, buffers, (spear ? 0.75f : 0.38f) * k * grow, (int) (230 * k));
			pose.popPose();
		}
		pose.mulPose(Axis.YP.rotation((float) Math.atan2(dir.x, dir.z)));
		pose.mulPose(Axis.XP.rotation((float) -Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z))));
		pose.mulPose(Axis.ZP.rotation(age * (spear ? 0.12f : 0.35f) + e.getId() * 0.7f));
		float tail = -len * (spear ? 0.55f : 0.32f);
		float[][] profile = spear ? SPEAR_PROFILE : SHARD_PROFILE;
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(EXTRAS));
		// the crystal itself, a touch flattened so it reads as a cut shard, not a cone
		facetted(pose.last(), vc, profile, tail, len, r, 0.78f, 0xC8D8FFF2, light, true);
		if (spear) {
			// three small shards flaring back off the butt like fletching
			for (int k = 0; k < 3; k++) {
				pose.pushPose();
				pose.mulPose(Axis.ZP.rotation(k * Mth.TWO_PI / 3f));
				pose.translate(0, r * 0.45f, tail + len * 0.16f);
				// pointing back and out
				pose.mulPose(Axis.XP.rotation(Mth.PI + 0.5f));
				facetted(pose.last(), vc, SHARD_PROFILE, -len * 0.08f, len * 0.26f, r * 0.42f, 0.8f, 0xC8D8FFF2, light, true);
				pose.popPose();
			}
		}
		// the bright core inside (additive, so it glows through the see-through facets)
		float pulse = 0.75f + 0.25f * Mth.sin(age * 0.6f);
		int c = (int) (255 * pulse);
		int core = 0xFF000000 | ((c * 0x8C / 255) << 16) | (c << 8) | (c * 0xE0 / 255);
		facetted(pose.last(), buffers.getBuffer(RenderType.eyes(EXTRAS)), profile, tail * 0.8f, len * 0.86f, r * 0.45f, 0.78f, core, LightTexture.FULL_BRIGHT,
				false);
		pose.popPose();
	}

	/**
	 * A crystal along +Z from {@code z0}, {@code len} long, {@code r} wide ({@code flat} times as deep): six facets round each
	 * span of {@code profile}, each wound outward and shaded from its facing (pale lit, base, dark shadowed texels) unless
	 * {@code shaded} is false (the core: one glowing texel).
	 */
	private static void facetted(PoseStack.Pose p, VertexConsumer vc, float[][] profile, float z0, float len, float r, float flat, int argb, int light,
			boolean shaded) {
		int n = 6;
		float[][][] ring = new float[profile.length][n][3];
		for (int k = 0; k < profile.length; k++) {
			float rs = profile[k][1] * r;
			double twist = k * 0.12;
			for (int i = 0; i < n; i++) {
				double a = i * Math.PI * 2 / n + twist;
				ring[k][i][0] = (float) Math.cos(a) * rs;
				ring[k][i][1] = (float) Math.sin(a) * rs * flat;
				ring[k][i][2] = z0 + profile[k][0] * len;
			}
		}
		for (int k = 0; k < ring.length - 1; k++) {
			for (int i = 0; i < n; i++) {
				float[] a = ring[k][i], b = ring[k][(i + 1) % n], c = ring[k + 1][(i + 1) % n], d = ring[k + 1][i];
				// the facet's normal from its two diagonals (sound even where a ring closes to a point)
				float ux = c[0] - a[0], uy = c[1] - a[1], uz = c[2] - a[2];
				float wx = d[0] - b[0], wy = d[1] - b[1], wz = d[2] - b[2];
				float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
				float nl = Math.max(1e-7f, Mth.sqrt(nx * nx + ny * ny + nz * nz));
				nx /= nl;
				ny /= nl;
				nz /= nl;
				// outward: away from the crystal's axis
				float mx = (a[0] + b[0] + c[0] + d[0]) * 0.25f, my = (a[1] + b[1] + c[1] + d[1]) * 0.25f;
				boolean flip = nx * mx + ny * my < 0;
				if (flip) {
					nx = -nx;
					ny = -ny;
					nz = -nz;
				}
				float[] uv;
				if (shaded) {
					// lit from above and ahead: the facets catch the light as the shard turns
					float lit = ny * 0.6f + nx * 0.25f + nz * 0.3f;
					uv = lit > 0.25f ? PALE : lit > -0.3f ? CRYSTAL : DARK;
				} else {
					uv = GLOW;
				}
				if (flip) {
					AlienParts.quad(vc, p, argb, light, a[0], a[1], a[2], d[0], d[1], d[2], c[0], c[1], c[2], b[0], b[1], b[2], nx, ny, nz, uv[0], uv[1], uv[2], uv[3]);
				} else {
					AlienParts.quad(vc, p, argb, light, a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2], nx, ny, nz, uv[0], uv[1], uv[2], uv[3]);
				}
			}
		}
	}

	/** A camera-facing four-point star of light: two crossed thin streaks and a small round bloom. */
	private static void glint(PoseStack pose, MultiBufferSource buffers, float size, int alpha) {
		pose.pushPose();
		pose.mulPose(net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
		VertexConsumer vc = buffers.getBuffer(RenderType.eyes(GLOW_TEX));
		int argb = (alpha << 24) | ((0xE8 * alpha / 255) << 16) | ((0xFF * alpha / 255) << 8) | (0xF6 * alpha / 255);
		PoseStack.Pose p = pose.last();
		float thin = size * 0.12f;
		pose.mulPose(Axis.ZP.rotation(0.6f));
		p = pose.last();
		AlienParts.quad(vc, p, argb, LightTexture.FULL_BRIGHT, -size, -thin, 0, size, -thin, 0, size, thin, 0, -size, thin, 0, 0, 0, 1, 0, 0, 1, 1);
		AlienParts.quad(vc, p, argb, LightTexture.FULL_BRIGHT, -thin, -size, 0, thin, -size, 0, thin, size, 0, -thin, size, 0, 0, 0, 1, 0, 0, 1, 1);
		float b = size * 0.35f;
		AlienParts.quad(vc, p, argb, LightTexture.FULL_BRIGHT, -b, -b, 0, b, -b, 0, b, b, 0, -b, b, 0, 0, 0, 1, 0, 0, 1, 1);
		pose.popPose();
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	// a crystal's profile along its length: {t, radius scale} -- a base, a facet bulge a third of the way up, the long taper
	private static final float[][] CRYSTAL_PROFILE = { { 0f, 0.85f }, { 0.12f, 1.0f }, { 0.38f, 1.12f }, { 0.78f, 0.55f }, { 1f, 0f } };

	/**
	 * A faceted crystal spike: a flattened hexagonal prism from {@code (x, y, z)} pointing up its local -Y (tipped by {@code rx}
	 * then {@code rz}), {@code r} wide ({@code flat} times as deep), {@code len} long, every facet shaded by which way it faces.
	 */
	private static void crystal(PoseStack pose, VertexConsumer vc, float x, float y, float z, float rx, float rz, float r, float flat, float len, int light) {
		pose.pushPose();
		pose.translate(x / 16f, y / 16f, z / 16f);
		pose.mulPose(Axis.XP.rotation(rx));
		pose.mulPose(Axis.ZP.rotation(rz));
		PoseStack.Pose p = pose.last();
		int n = 6;
		float[][][] ring = new float[CRYSTAL_PROFILE.length][n][3];
		for (int k = 0; k < CRYSTAL_PROFILE.length; k++) {
			float t = CRYSTAL_PROFILE[k][0], rs = CRYSTAL_PROFILE[k][1] * r;
			// each ring a little twisted against the last: the facets read as cut, not lathed
			double twist = k * 0.18;
			for (int i = 0; i < n; i++) {
				double a = i * Math.PI * 2 / n + twist;
				ring[k][i][0] = (float) Math.cos(a) * rs / 16f;
				ring[k][i][1] = -t * len / 16f;
				ring[k][i][2] = (float) Math.sin(a) * rs * flat / 16f;
			}
		}
		for (int k = 0; k < ring.length - 1; k++) {
			for (int i = 0; i < n; i++) {
				float[] a = ring[k][i], b = ring[k][(i + 1) % n], c = ring[k + 1][(i + 1) % n], d = ring[k + 1][i];
				// the facet's outward normal (from the lower edge and the side), and its shade from that: light from the top / front
				float ex = b[0] - a[0], ey = b[1] - a[1], ez = b[2] - a[2];
				float fx = d[0] - a[0], fy = d[1] - a[1], fz = d[2] - a[2];
				float nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
				float nl = Mth.sqrt(nx * nx + ny * ny + nz * nz);
				if (nl < 1e-7f) {
					// the tip ring is one point: take the side edge against the upper edge instead
					fx = c[0] - b[0];
					fy = c[1] - b[1];
					fz = c[2] - b[2];
					nx = ey * fz - ez * fy;
					ny = ez * fx - ex * fz;
					nz = ex * fy - ey * fx;
					nl = Math.max(1e-7f, Mth.sqrt(nx * nx + ny * ny + nz * nz));
				}
				nx /= nl;
				ny /= nl;
				nz /= nl;
				float lit = -ny * 0.5f + nx * 0.35f - nz * 0.15f;
				float[] uv = lit > 0.08f ? PALE : lit > -0.4f ? CRYSTAL : DARK;
				AlienParts.quad(vc, p, 0xFFFFFFFF, light, a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2], nx, ny, nz, uv[0], uv[1], uv[2],
						uv[3]);
			}
		}
		pose.popPose();
	}

	/**
	 * The glowing heart of a crystal: the same hexagonal profile as {@link #crystal} (pointing up its local -Y, here straight
	 * along the bone), unshaded and additive, brightness {@code pulse}.
	 */
	private static void crystalCore(PoseStack pose, VertexConsumer vc, float x, float y, float z, float r, float flat, float len, float pulse) {
		pose.pushPose();
		pose.translate(x / 16f, y / 16f, z / 16f);
		pose.mulPose(Axis.XP.rotation(Mth.PI));
		PoseStack.Pose p = pose.last();
		int n = 6;
		int a = (int) (220 * pulse);
		int argb = (a << 24) | 0xFFFFFF;
		for (int k = 0; k + 1 < CRYSTAL_PROFILE.length; k++) {
			float t0 = CRYSTAL_PROFILE[k][0], r0 = CRYSTAL_PROFILE[k][1] * r, t1 = CRYSTAL_PROFILE[k + 1][0], r1 = CRYSTAL_PROFILE[k + 1][1] * r;
			for (int i = 0; i < n; i++) {
				double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
				float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
				AlienParts.quad(vc, p, argb, LightTexture.FULL_BRIGHT, c0 * r0 / 16f, -t0 * len / 16f, s0 * r0 * flat / 16f, c1 * r0 / 16f, -t0 * len / 16f,
						s1 * r0 * flat / 16f, c1 * r1 / 16f, -t1 * len / 16f, s1 * r1 * flat / 16f, c0 * r1 / 16f, -t1 * len / 16f, s0 * r1 * flat / 16f, c0, 0,
						s0, GLOW[0], GLOW[1], GLOW[2], GLOW[3]);
			}
		}
		pose.popPose();
	}

	// ---------------------------------------------------------------- v0.17.2 revamp: state read on the client

	private static DiamondheadKit kit() {
		return Aliens.kit(DiamondheadKit.ID) instanceof DiamondheadKit k ? k : null;
	}

	/** Ticks since the synced pose {@code animId} started, or -1 if another pose is playing. */
	private static float since(AbstractClientPlayer p, int animId, float partial) {
		OmnitrixState s = Omnitrix.state(p);
		return s.animId == animId ? p.level().getGameTime() + partial - s.animStart : -1f;
	}

	private static boolean combat(AbstractClientPlayer p) {
		return (Omnitrix.state(p).kitFlags & DiamondheadKit.F_COMBAT) != 0;
	}

	/**
	 * How far the shield is raised, 0 (strapped along the forearm, at his side) to 1 (held out in front), eased over a few ticks: it is up
	 * only while right-click is held (F_SHIELD) or he is bashing.
	 */
	private static float shieldRaise(AbstractClientPlayer p, float partial) {
		float k;
		if ((Omnitrix.state(p).kitFlags & (DiamondheadKit.F_SHIELD | DiamondheadKit.F_BASH)) != 0) {
			float t = since(p, DiamondheadKit.ANIM_SHIELD, partial);
			k = t < 0 ? 1f : Mth.clamp(t / 3f, 0f, 1f);
		} else {
			float t = since(p, DiamondheadKit.ANIM_SHIELD_DOWN, partial);
			k = t < 0 ? 0f : 1f - Mth.clamp(t / 4f, 0f, 1f);
		}
		return k * k * (3f - 2f * k);
	}

	/** The blade's and shield's size now: 0 without Combat Mode, growing in over 6 ticks, shrinking away over 5 as it ends. */
	private static float combatGrow(AbstractClientPlayer p, float partial) {
		if (!combat(p)) {
			float off = since(p, DiamondheadKit.ANIM_COMBAT_OFF, partial);
			return off >= 0 && off < 5 ? 1f - off / 5f : 0f;
		}
		float on = since(p, DiamondheadKit.ANIM_COMBAT_ON, partial);
		return on < 0 ? 1f : Mth.clamp(on / 6f, 0.05f, 1f);
	}

	/** The blade: gone while the Crystal Maul has that arm. */
	private static float bladeGrow(AbstractClientPlayer p, float partial) {
		return maulGrow(p, partial) > 0f ? 0f : combatGrow(p, partial);
	}

	/** The shield: as {@link #combatGrow}, but shattered it is gone, and grows back over the last 8 ticks of its regrowth. */
	private static float shieldGrow(AbstractClientPlayer p, float partial) {
		float g = combatGrow(p, partial);
		DiamondheadKit k = kit();
		int left = k == null ? 0 : k.cooldownLeft(p, DiamondheadKit.SHIELD);
		return left <= 0 ? g : Math.min(g, Mth.clamp(1f - (left - partial) / 8f, 0f, 1f));
	}

	/** The blade thrust out longer through the Blade Stab. */
	private static float bladeReach(AbstractClientPlayer p, float partial) {
		float t = since(p, DiamondheadKit.ANIM_STAB, partial);
		return t < 3f || t > 13f ? 1f : 1f + 0.5f * Mth.sin((t - 3f) / 10f * Mth.PI);
	}

	/** The Crystal Maul's hammer: swelling as the arm goes up, whole for the blow, crumbling after. */
	private static float maulGrow(AbstractClientPlayer p, float partial) {
		float t = since(p, DiamondheadKit.ANIM_MAUL, partial);
		if (t < 0f) {
			return 0f;
		}
		float end = DiamondheadKit.MAUL_WINDUP + 8f;
		return t < 7f ? Mth.clamp(t / 7f, 0.05f, 1f) : t < end ? 1f : Mth.clamp(1f - (t - end) / 4f, 0f, 1f);
	}

	/** The Crystal Spear in his hand, growing there until it is thrown. */
	private static float spearGrow(AbstractClientPlayer p, float partial) {
		float t = since(p, DiamondheadKit.ANIM_SPEAR, partial);
		return t < 0f || t >= DiamondheadKit.SPEAR_WINDUP ? 0f : Mth.clamp(0.25f + t / 3.5f, 0f, 1f);
	}

	/**
	 * v0.17.2 round 3: the Crystal Cyclone turns all of him, costume and blade (degrees; {@code DiamondheadSpinMixin}): one and a bit
	 * eased turns to his left, the blade leading.
	 */
	public static float spinDegrees(AbstractClientPlayer p, float partial) {
		if (!Omnitrix.transformed(p) || !DiamondheadKit.ID.equals(Omnitrix.state(p).active)) {
			return 0f;
		}
		float t = since(p, DiamondheadKit.ANIM_CYCLONE, partial);
		if (t < 1f || t > 12f) {
			return 0f;
		}
		float k = (t - 1f) / 11f;
		return k * k * (3f - 2f * k) * 360f;
	}

	private static float shieldHealth(AbstractClientPlayer p) {
		DiamondheadKit k = kit();
		return k == null ? 1f : k.shieldHp(p) / DiamondheadKit.SHIELD_HP;
	}

	private static float partial() {
		return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(EXTRAS));
		// v0.17.1 (user: "remove the spikes on diamondheads head and shoulders"): no crystal crest, cheek or shoulder shards -- the
		// skin's own head; only the four back crystals (and the blades / shield) are drawn
		// v0.17.1 (user: "he has 4 spikes coming out of his back, 2 longer ones and 2 smaller ones"): two long faceted crystals
		// rising from his shoulder blades in a V past his head, two short chunky ones under them
		AlienParts.on(pose, model.body, () -> {
			for (int side = -1; side <= 1; side += 2) {
				crystal(pose, vc, side * 2.1f, 2.6f, 1.6f, -0.42f, side * 0.5f, 1.7f, 1.1f, 17f, light);
				crystal(pose, vc, side * 1.7f, 7.6f, 1.7f, -0.95f, side * 0.75f, 1.35f, 1.0f, 4.6f, light);
			}
		});
		float blades = bladeGrow(p, partial), reach = bladeReach(p, partial), maul = maulGrow(p, partial), spear = spearGrow(p, partial);
		float grow = shieldGrow(p, partial);
		boolean shield = grow > 0f;
		float hp = shield ? shieldHealth(p) : 1f;
		// v0.17.2 round 3 (user: "only have 1 blade on the right arm please")
		if (blades > 0f) {
			AlienParts.on(pose, model.rightArm, () -> blade(pose, vc, -1f, blades, reach, light));
		}
		if (maul > 0f) {
			AlienParts.on(pose, model.rightArm, () -> maul(pose, vc, maul, light));
		}
		if (spear > 0f) {
			AlienParts.on(pose, model.rightArm, () -> heldSpear(pose, vc, spear, false, light));
		}
		if (shield) {
			float raise = shieldRaise(p, partial);
			AlienParts.on(pose, model.leftArm, () -> {
				shieldOnArm(pose, model.leftArm, raise);
				shield(pose, vc, grow, hp, light);
			});
		}
		// last: the glowing cores fetch another buffer type, which ends the translucent one
		VertexConsumer glow = buffers.getBuffer(RenderType.eyes(EXTRAS));
		float pulse = 0.75f + 0.25f * Mth.sin(age * 0.2f);
		if (blades > 0f) {
			AlienParts.on(pose, model.rightArm, () -> bladeCore(pose, glow, -1f, blades, reach, pulse));
		}
		if (maul > 0f) {
			AlienParts.on(pose, model.rightArm, () -> maulCore(pose, glow, maul, pulse));
		}
		if (shield) {
			float raise = shieldRaise(p, partial);
			AlienParts.on(pose, model.leftArm, () -> {
				shieldOnArm(pose, model.leftArm, raise);
				shieldCore(pose, glow, grow, hp, pulse);
			});
		}
	}

	/** Strapped on: along the outside of the left forearm, its face turned out to his left, a little smaller. In arm-bone space. */
	private static void shieldRest(PoseStack pose) {
		pose.translate(3.9f / 16f, 6.2f / 16f, 0f);
		pose.mulPose(Axis.YP.rotation(-Mth.HALF_PI));
		pose.scale(SHIELD_REST_SCALE, SHIELD_REST_SCALE, SHIELD_REST_SCALE);
	}

	private static final float SHIELD_REST_SCALE = 0.78f;

	/**
	 * Held out: from the left arm's bone space to a frame on its forearm, a little in front of it, turned back upright and facing
	 * where the body faces (the arm's own turn undone), so the tower shield stands straight whatever the arm swings through.
	 */
	private static void uprightOnForearm(PoseStack pose, ModelPart arm) {
		pose.translate(1f / 16f, 8.5f / 16f, 0f);
		pose.mulPose(Axis.XP.rotation(-arm.xRot));
		pose.mulPose(Axis.YP.rotation(-arm.yRot));
		pose.mulPose(Axis.ZP.rotation(-arm.zRot));
		// out in front of the forearm, turned a touch outward (to his left)
		pose.translate(0.6f / 16f, -0.5f / 16f, -3.4f / 16f);
		pose.mulPose(Axis.YP.rotation(-0.18f));
	}

	/** The shield's frame, {@code raise} of the way from strapped on to held out. */
	private static void shieldOnArm(PoseStack pose, ModelPart arm, float raise) {
		pose.pushPose();
		shieldRest(pose);
		org.joml.Matrix4f rest = new org.joml.Matrix4f(pose.last().pose());
		pose.popPose();
		pose.pushPose();
		uprightOnForearm(pose, arm);
		org.joml.Matrix4f held = new org.joml.Matrix4f(pose.last().pose());
		pose.popPose();
		blend(pose, rest, held, raise);
	}

	/** Sets the pose to the rigid blend of two frames: their places lerped, their turns slerped, so the shield swings round whole. */
	private static void blend(PoseStack pose, org.joml.Matrix4f a, org.joml.Matrix4f b, float k) {
		org.joml.Quaternionf q = a.getNormalizedRotation(new org.joml.Quaternionf()).slerp(b.getNormalizedRotation(new org.joml.Quaternionf()), k);
		org.joml.Vector3f at = a.getTranslation(new org.joml.Vector3f()).lerp(b.getTranslation(new org.joml.Vector3f()), k);
		org.joml.Vector3f sa = a.getScale(new org.joml.Vector3f()), sb = b.getScale(new org.joml.Vector3f());
		float scale = Mth.lerp(k, sa.x, sb.x);
		pose.last().pose().translation(at).rotate(q).scale(scale);
		pose.last().normal().set(q);
	}

	// ---------------------------------------------------------------- the blades

	/**
	 * The forearm blade (arm-bone space, pixels; {@code side} -1 the right arm): a long faceted crystal running down the forearm from
	 * just above the wrist and out past the fist, broad front-to-back and thin across, {@code g} grown, {@code reach} times its length.
	 */
	private static void blade(PoseStack pose, VertexConsumer vc, float side, float g, float reach, int light) {
		pose.pushPose();
		pose.translate(side / 16f, 0f, 0f);
		// the crystal's broad side turned to face out of the arm
		pose.mulPose(Axis.YP.rotation(Mth.HALF_PI));
		crystal(pose, vc, 0f, 4.5f, 0f, Mth.PI, 0f, 2.3f * (0.45f + 0.55f * g), 0.32f, 19f * g * reach, light);
		// a second, shorter spur along the back of the forearm
		crystal(pose, vc, 1.4f, 6.5f, 0f, Mth.PI - 0.32f, 0f, 1.0f * g, 0.4f, 7f * g, light);
		pose.popPose();
	}

	/** The blade's glowing heart (additive). */
	private static void bladeCore(PoseStack pose, VertexConsumer glow, float side, float g, float reach, float pulse) {
		pose.pushPose();
		pose.translate(side / 16f, 0f, 0f);
		pose.mulPose(Axis.YP.rotation(Mth.HALF_PI));
		crystalCore(pose, glow, 0f, 5.5f, 0f, 0.7f * g, 0.3f, 16.5f * g * reach, pulse);
		pose.popPose();
	}

	// ---------------------------------------------------------------- the Crystal Maul and the spear in his hand

	/** Where the hammer's head sits down the arm (px), {@code g} grown. */
	private static float maulHead(float g) {
		return 9f + 7f * g;
	}

	/**
	 * The hammer on the right arm (arm-bone space, pixels): a faceted haft out of the fist, and across its end, along the line of the
	 * swing, two great crystals back to back with stubby spurs round the join, {@code g} grown.
	 */
	private static void maul(PoseStack pose, VertexConsumer vc, float g, int light) {
		float y = maulHead(g), x = -1f;
		crystal(pose, vc, x, 7f, 0f, Mth.PI, 0f, 2.2f * g, 1f, 11f * g, light);
		crystal(pose, vc, x, y, 0f, Mth.HALF_PI, 0f, 5.2f * g, 0.9f, 12.5f * g, light);
		crystal(pose, vc, x, y, 0f, -Mth.HALF_PI, 0f, 5.2f * g, 0.9f, 12.5f * g, light);
		crystal(pose, vc, x, y, 0f, 0f, Mth.HALF_PI, 2.6f * g, 0.95f, 7f * g, light);
		crystal(pose, vc, x, y, 0f, 0f, -Mth.HALF_PI, 2.6f * g, 0.95f, 7f * g, light);
		crystal(pose, vc, x, y, 0f, Mth.PI, 0f, 2.8f * g, 0.95f, 7.5f * g, light);
	}

	private static void maulCore(PoseStack pose, VertexConsumer glow, float g, float pulse) {
		crystalCore(pose, glow, -1f, maulHead(g) - 4f * g, 0f, 1.6f * g, 0.9f, 9f * g, pulse);
	}

	/**
	 * The Crystal Spear gripped in the right hand (arm-bone space): at the top of the throw its point (+z) is what leads; in first person
	 * ({@code along}) it runs on down the arm, out of the fist and away ahead of the eye.
	 */
	private static void heldSpear(PoseStack pose, VertexConsumer vc, float g, boolean along, int light) {
		pose.pushPose();
		pose.translate(-1f / 16f, 10.5f / 16f, 0f);
		if (along) {
			pose.mulPose(Axis.XP.rotation(-Mth.HALF_PI));
		}
		float len = 2.6f * g;
		facetted(pose.last(), vc, SPEAR_PROFILE, -len * 0.42f, len, 0.15f * g, 0.78f, 0xC8D8FFF2, light, true);
		pose.popPose();
	}

	// ---------------------------------------------------------------- the Crystal Shield

	/** The shield's outline (pixels, its own plane: x across, y down), from the top point round clockwise. */
	private static final float[][] RIM = { { 0f, -10.5f }, { 5.5f, -7f }, { 6.2f, 0.5f }, { 4.2f, 7.5f }, { 0f, 10.5f }, { -4.2f, 7.5f }, { -6.2f, 0.5f },
			{ -5.5f, -7f } };
	/** How far each part of the face stands out (-z): the centre boss, the inner ring of facets, the rim; and the back. */
	private static final float BOSS = -2.6f, INNER = -1.8f, EDGE = -0.5f, BACK = 0.6f;
	/** The cracks, worst last: polylines on the face (pixels), each appears as the shield's health drops past a quarter. */
	private static final float[][][] CRACKS = {
			{ { 0.4f, -0.8f, 2.6f, -4.2f }, { 2.6f, -4.2f, 3.3f, -7.4f }, { 2.6f, -4.2f, 4.8f, -3.4f } },
			{ { -0.5f, 0.5f, -3.0f, 2.6f }, { -3.0f, 2.6f, -4.2f, 6.2f }, { -3.0f, 2.6f, -5.4f, 1.4f } },
			{ { 0f, 0f, 1.6f, 4.2f }, { 1.6f, 4.2f, 0.8f, 8.8f }, { 0f, 0f, -2.1f, -3.6f }, { -2.1f, -3.6f, -1.4f, -8.4f }, { 1.6f, 4.2f, 4.0f, 5.2f } } };

	/** The face's depth (-z) at a point of the plane: the boss falling to the inner ring, then to the rim. */
	private static float faceZ(float x, float y) {
		float r = Math.max(Math.abs(x) / 6.2f, Math.abs(y) / 10.5f);
		return r < 0.5f ? Mth.lerp(r / 0.5f, BOSS, INNER) : Mth.lerp(Math.min(1f, (r - 0.5f) / 0.5f), INNER, EDGE);
	}

	/**
	 * The tower shield (its frame: x across, y down, the face toward -z; pixels): a faceted gem face -- a centre boss, a ring
	 * of eight facets round it and eight more out to the rim, each shaded by which way it faces -- a flat back, the edge band,
	 * and the cracks of the damage it has taken. {@code g} grown, {@code hp} 0..1.
	 */
	private static void shield(PoseStack pose, VertexConsumer vc, float g, float hp, int light) {
		pose.pushPose();
		float s = g / 16f;
		pose.scale(s, s, s);
		PoseStack.Pose p = pose.last();
		int n = RIM.length;
		int tint = 0xE8FFFFFF;
		float[] c = { 0f, 0f, BOSS };
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			float[] ri = { RIM[i][0], RIM[i][1], EDGE }, rj = { RIM[j][0], RIM[j][1], EDGE };
			float[] ii = { RIM[i][0] * 0.5f, RIM[i][1] * 0.5f, INNER }, ij = { RIM[j][0] * 0.5f, RIM[j][1] * 0.5f, INNER };
			// the inner facet (a triangle from the boss) and the outer one (the band from the inner ring to the rim)
			facet(vc, p, tint, light, c, ii, ij, ij, true);
			facet(vc, p, tint, light, ii, ri, rj, ij, true);
			// the back and the edge band
			float[] bi = { RIM[i][0], RIM[i][1], BACK }, bj = { RIM[j][0], RIM[j][1], BACK };
			float[] bc = { 0f, 0f, BACK };
			AlienParts.quad(vc, p, 0xF0B0C8C0, light, bc[0], bc[1], bc[2], bj[0], bj[1], bj[2], bi[0], bi[1], bi[2], bi[0], bi[1], bi[2], 0, 0, 1, DARK[0], DARK[1],
					DARK[2], DARK[3]);
			float ex = rj[1] - ri[1], ey = -(rj[0] - ri[0]);
			float el = Math.max(1e-5f, Mth.sqrt(ex * ex + ey * ey));
			AlienParts.quad(vc, p, tint, light, ri[0], ri[1], ri[2], rj[0], rj[1], rj[2], bj[0], bj[1], bj[2], bi[0], bi[1], bi[2], ex / el, ey / el, 0,
					CRYSTAL[0], CRYSTAL[1], CRYSTAL[2], CRYSTAL[3]);
		}
		// the cracks, a hair in front of the face
		int shown = hp < 0.25f ? 3 : hp < 0.5f ? 2 : hp < 0.75f ? 1 : 0;
		for (int k = 0; k < shown; k++) {
			for (float[] seg : CRACKS[k]) {
				crack(vc, p, light, seg[0], seg[1], seg[2], seg[3]);
			}
		}
		pose.popPose();
	}

	/** One facet a..d (d may repeat c for a triangle), its normal turned out of the face (-z) and its shade from that. */
	private static void facet(VertexConsumer vc, PoseStack.Pose p, int argb, int light, float[] a, float[] b, float[] c, float[] d, boolean front) {
		float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
		float wx = c[0] - a[0], wy = c[1] - a[1], wz = c[2] - a[2];
		float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
		float nl = Math.max(1e-7f, Mth.sqrt(nx * nx + ny * ny + nz * nz));
		nx /= nl;
		ny /= nl;
		nz /= nl;
		if (front == nz > 0) {
			nx = -nx;
			ny = -ny;
			nz = -nz;
		}
		// lit from above (-y) and ahead (-z), a little from his right
		float lit = -ny * 0.55f - nz * 0.35f - nx * 0.25f;
		float[] uv = lit > 0.42f ? PALE : lit > 0.18f ? CRYSTAL : DARK;
		AlienParts.quad(vc, p, argb, light, a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2], nx, ny, nz, uv[0], uv[1], uv[2], uv[3]);
	}

	/** A crack from (x0, y0) to (x1, y1) on the face: a thin dark sliver following the facets' depth. */
	private static void crack(VertexConsumer vc, PoseStack.Pose p, int light, float x0, float y0, float x1, float y1) {
		float dx = x1 - x0, dy = y1 - y0;
		float l = Math.max(1e-5f, Mth.sqrt(dx * dx + dy * dy));
		float w = 0.28f;
		float ox = -dy / l * w, oy = dx / l * w;
		float z0 = faceZ(x0, y0) - 0.12f, z1 = faceZ(x1, y1) - 0.12f;
		AlienParts.quad(vc, p, 0xFF263A33, light, x0 - ox, y0 - oy, z0, x1 - ox, y1 - oy, z1, x1 + ox, y1 + oy, z1, x0 + ox, y0 + oy, z0, 0, 0, -1, DARK[0],
				DARK[1], DARK[2], DARK[3]);
	}

	/** The glowing heart behind the boss, and a faint light along the cracks (they let the core's light out). */
	private static void shieldCore(PoseStack pose, VertexConsumer glow, float g, float hp, float pulse) {
		pose.pushPose();
		float s = g / 16f;
		pose.scale(s, s, s);
		PoseStack.Pose p = pose.last();
		int a = (int) (200 * pulse * (0.55f + 0.45f * hp));
		int argb = (a << 24) | 0xFFFFFF;
		float z = BOSS + 0.25f;
		AlienParts.quad(glow, p, argb, LightTexture.FULL_BRIGHT, 0f, -3.2f, z, 2.2f, 0f, z, 0f, 3.2f, z, -2.2f, 0f, z, 0, 0, -1, GLOW[0], GLOW[1], GLOW[2], GLOW[3]);
		pose.popPose();
	}

	// ---------------------------------------------------------------- first person

	@Override
	public boolean bothHandsInFirstPerson(AbstractClientPlayer p) {
		return shieldGrow(p, partial()) > 0f || bladeGrow(p, partial()) > 0f || (Omnitrix.state(p).kitFlags & DiamondheadKit.F_VOLLEY) != 0;
	}

	/**
	 * v0.17.2 round 3: the left arm in first person -- brought up behind the shield as it is raised (user: no hand "moving on nothing"), and
	 * out ahead with the right through the Shard Volley, throwing on its own turns.
	 */
	@Override
	public void poseFirstPersonOffHand(PoseStack pose, AbstractClientPlayer p, float partial, float side) {
		float raise = shieldRaise(p, partial);
		if (raise > 0f) {
			FP_SHIELD_UP.apply(pose, 4f * raise, side);
		}
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & DiamondheadKit.F_VOLLEY) != 0) {
			FirstPersonClip clip = s.animId == DiamondheadKit.ANIM_VOLLEY_L ? FP_VOLLEY : s.animId == DiamondheadKit.ANIM_VOLLEY_FIRST ? FP_VOLLEY_RISE : FP_VOLLEY_IDLE;
			clip.apply(pose, Math.min(clip.length(), p.level().getGameTime() + partial - s.animStart), side);
		}
	}

	@Override
	public boolean swingsOnUseAir(AbstractClientPlayer p) {
		return !combat(p); // the shield's right-click: no hand waved at nothing
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		float partial = partial();
		float blades = right ? bladeGrow(p, partial) : 0f, reach = bladeReach(p, partial);
		float maul = right ? maulGrow(p, partial) : 0f, spear = right ? spearGrow(p, partial) : 0f;
		float grow = right ? 0f : shieldGrow(p, partial);
		boolean shield = grow > 0f;
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(EXTRAS));
		if (blades > 0f) {
			blade(pose, vc, -1f, blades, reach, light);
		}
		if (maul > 0f) {
			maul(pose, vc, maul, light);
		}
		if (spear > 0f) {
			heldSpear(pose, vc, spear, true, light);
		}
		float hp = shield ? shieldHealth(p) : 1f;
		float raise = shield ? shieldRaise(p, partial) : 0f;
		if (shield) {
			pose.pushPose();
			firstPersonShield(pose, raise);
			shield(pose, vc, grow, hp, light);
			pose.popPose();
		}
		VertexConsumer glow = buffers.getBuffer(RenderType.eyes(EXTRAS));
		float pulse = 0.75f + 0.25f * Mth.sin((p.tickCount + partial) * 0.2f);
		if (blades > 0f) {
			bladeCore(pose, glow, -1f, blades, reach, pulse);
		}
		if (maul > 0f) {
			maulCore(pose, glow, maul, pulse);
		}
		if (shield) {
			pose.pushPose();
			firstPersonShield(pose, raise);
			shieldCore(pose, glow, grow, hp, pulse);
			pose.popPose();
		}
	}

	/**
	 * The shield in first person: strapped along the forearm (down in the corner of the view) at {@code raise} 0; at 1 held up on the left of
	 * the view, placed from the hand's starting pose (camera space with vanilla's bob and sway, {@code OmnitrixFirstPerson.HAND_BASE}) rather
	 * than the arm, so it stands upright facing the eye, its face turned a little toward the centre.
	 */
	private static void firstPersonShield(PoseStack pose, float raise) {
		pose.pushPose();
		shieldRest(pose);
		org.joml.Matrix4f rest = new org.joml.Matrix4f(pose.last().pose());
		pose.popPose();
		pose.pushPose();
		pose.last().pose().set(com.projecthero.mod.client.omnitrix.OmnitrixFirstPerson.HAND_BASE);
		pose.last().normal().set(com.projecthero.mod.client.omnitrix.OmnitrixFirstPerson.HAND_BASE_NORMAL);
		pose.translate(-0.46f, -0.08f, -0.52f);
		pose.mulPose(Axis.YP.rotationDegrees(22f));
		// the shield's frame (y down, face toward -z) turned to the camera's (y up, the eye at +z)
		pose.mulPose(Axis.XP.rotation(Mth.PI));
		pose.scale(0.5f, 0.5f, 0.5f);
		org.joml.Matrix4f held = new org.joml.Matrix4f(pose.last().pose());
		pose.popPose();
		blend(pose, rest, held, raise);
	}

	// ---------------------------------------------------------------- poses

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		int flags = Omnitrix.state(p).kitFlags;
		if (volleyPose(p, m, animId, ticks, flags)) {
			return true;
		}
		float[][] clip = switch (animId) {
			case DiamondheadKit.ANIM_SPEAR -> SPEAR;
			case DiamondheadKit.ANIM_COMBAT_ON -> COMBAT_ON;
			case DiamondheadKit.ANIM_COMBAT_OFF -> COMBAT_OFF;
			case DiamondheadKit.ANIM_SHATTER -> SHATTER;
			case DiamondheadKit.ANIM_SLIDE -> SLIDE;
			case DiamondheadKit.ANIM_VAULT -> VAULT;
			case DiamondheadKit.ANIM_WALL -> WALL;
			case DiamondheadKit.ANIM_BARRAGE -> BARRAGE;
			case DiamondheadKit.ANIM_MAUL -> MAUL;
			case DiamondheadKit.ANIM_SPIRE -> SPIRE;
			case DiamondheadKit.ANIM_SLASH -> SLASH;
			case DiamondheadKit.ANIM_PLACE -> PLACE;
			case DiamondheadKit.ANIM_ERUPT -> ERUPT;
			case DiamondheadKit.ANIM_PILLAR -> PILLAR;
			case DiamondheadKit.ANIM_SHIELD -> SHIELD;
			case DiamondheadKit.ANIM_BASH -> BASH;
			case DiamondheadKit.ANIM_STAB -> STAB;
			case DiamondheadKit.ANIM_CYCLONE -> CYCLONE;
			case DiamondheadKit.ANIM_SHIELD_BREAK -> SHIELD_BREAK;
			default -> null;
		};
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		if (!played && (flags & DiamondheadKit.F_SLIDING) != 0) {
			// v0.17.2: the surfer's crouch held for the whole ride
			return QuirkKeyframes.play(m, SLIDE, 10f, 1f);
		}
		boolean combat = (flags & DiamondheadKit.F_COMBAT) != 0;
		if (!played && combat) {
			// a fighter's guard: the blade held a little forward and out from the body, the shield arm at his side
			m.rightArm.xRot = m.rightArm.xRot * 0.4f - 0.55f;
			m.rightArm.zRot += 0.22f;
			m.leftArm.xRot = m.leftArm.xRot * 0.4f - 0.2f;
			m.leftArm.zRot -= 0.1f;
		}
		float raise = shieldRaise(p, partial());
		if (raise > 0f && animId != DiamondheadKit.ANIM_BASH) {
			// v0.17.2 round 3 (user: "only make them hold the shield in front of them when they are right clicking"): the left forearm brought up
			// and across (the shield itself is stood upright by shieldOnArm, see uprightOnForearm)
			m.leftArm.xRot = Mth.lerp(raise, m.leftArm.xRot, -1.2f);
			m.leftArm.yRot = Mth.lerp(raise, m.leftArm.yRot, 0.28f);
			m.leftArm.zRot = Mth.lerp(raise, m.leftArm.zRot, 0f);
			played = true;
		}
		return played || combat;
	}

	/**
	 * The Shard Volley's held pose, procedural so the clumps never drop the arms between them: the stance, with the hand that threw last
	 * punching out and back, both arms following the look; it fades in over the first few ticks and out as R is let go.
	 */
	private static boolean volleyPose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, int flags) {
		float w;
		float[] frame;
		if ((flags & DiamondheadKit.F_VOLLEY) != 0) {
			DiamondheadKit k = kit();
			long start = k == null ? 0L : k.timer(p, "volley_start");
			w = Mth.clamp((p.level().getGameTime() + partial() - start) / 3f, 0f, 1f);
			float[][] clip = animId == DiamondheadKit.ANIM_VOLLEY_L ? VOLLEY_L : VOLLEY_R;
			boolean clump = animId == DiamondheadKit.ANIM_VOLLEY || animId == DiamondheadKit.ANIM_VOLLEY_L || animId == DiamondheadKit.ANIM_VOLLEY_FIRST;
			frame = clump ? QuirkKeyframes.sample(clip, Math.min(ticks, QuirkKeyframes.length(clip))) : VOLLEY_AIM;
		} else if (animId == DiamondheadKit.ANIM_VOLLEY_END && ticks < 6f) {
			float t = ticks / 6f;
			w = 1f - t * t * (3f - 2f * t);
			frame = VOLLEY_AIM;
		} else {
			return false;
		}
		if (frame == null) {
			frame = VOLLEY_AIM;
		}
		QuirkKeyframes.applyMasked(m, frame, w, VOLLEY_MASK);
		m.rightArm.xRot += m.head.xRot * w;
		m.leftArm.xRot += m.head.xRot * w;
		return true;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case DiamondheadKit.ANIM_VOLLEY -> FP_VOLLEY;
			case DiamondheadKit.ANIM_VOLLEY_L -> FP_VOLLEY_IDLE;
			case DiamondheadKit.ANIM_VOLLEY_FIRST -> FP_VOLLEY_FIRST;
			case DiamondheadKit.ANIM_VOLLEY_END -> FP_VOLLEY_END;
			case DiamondheadKit.ANIM_BARRAGE -> FP_TITAN;
			case DiamondheadKit.ANIM_SPEAR -> FP_SPEAR;
			case DiamondheadKit.ANIM_COMBAT_ON -> FP_COMBAT_ON;
			case DiamondheadKit.ANIM_COMBAT_OFF -> FP_COMBAT_OFF;
			case DiamondheadKit.ANIM_SHATTER -> FP_SLAM;
			case DiamondheadKit.ANIM_WALL -> FP_HEAVE;
			case DiamondheadKit.ANIM_VAULT -> FP_RAISE;
			case DiamondheadKit.ANIM_SLIDE -> FP_SURF;
			case DiamondheadKit.ANIM_SLASH -> FP_SLASH;
			case DiamondheadKit.ANIM_PLACE -> FP_PLACE;
			case DiamondheadKit.ANIM_MAUL -> FP_MAUL;
			case DiamondheadKit.ANIM_SPIRE -> FP_SPIRE;
			case DiamondheadKit.ANIM_ERUPT -> FP_ERUPT;
			case DiamondheadKit.ANIM_PILLAR -> FP_PILLAR;
			case DiamondheadKit.ANIM_BASH -> FP_BASH;
			case DiamondheadKit.ANIM_STAB -> FP_STAB;
			case DiamondheadKit.ANIM_CYCLONE -> FP_CYCLONE;
			case DiamondheadKit.ANIM_SHIELD_BREAK -> FP_SHIELD_BREAK;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- HUD: the shield meter

	@Override
	public void hud(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.player.LocalPlayer p, int x, int y, int w, float partial) {
		DiamondheadKit k = kit();
		if (k == null) {
			return;
		}
		boolean broken = k.shieldBroken(p);
		float hp = k.shieldHp(p);
		if (!broken && hp >= DiamondheadKit.SHIELD_HP && (Omnitrix.state(p).kitFlags & (DiamondheadKit.F_SHIELD | DiamondheadKit.F_BASH)) == 0) {
			return; // full and lowered: nothing to show
		}
		float frac;
		String value;
		int colour;
		if (broken) {
			int left = k.cooldownLeft(p, DiamondheadKit.SHIELD);
			frac = 1f - left / (float) DiamondheadKit.SHIELD_BROKEN_TICKS;
			value = String.format(java.util.Locale.ROOT, "%.0fs", Math.ceil(left / 20f));
			colour = 0xFF7A8C88;
		} else {
			frac = hp / DiamondheadKit.SHIELD_HP;
			value = String.valueOf(Math.round(hp));
			colour = 0xFF000000 | DiamondheadKit.COLOUR;
		}
		com.projecthero.mod.client.omnitrix.OmnitrixHud.meter(g, Minecraft.getInstance(), x, y - 6,
				net.minecraft.network.chat.Component.translatable("hud.projecthero.omnitrix.diamondhead.shield"), frac, colour, value, 0xFFFFFFFF);
	}

	@Override
	public int hudHeight() {
		return 10;
	}

	/** The Crystal Slide's eased ride velocity (CryokinesisClient.ride). */
	private static final double[] RIDE = { Double.NaN, 0 };

	@Override
	public void tick(AbstractClientPlayer p) {
		var r = p.getRandom();
		// (not round his own eyes in first person: a sparkle there fills the screen)
		boolean ownView = p == Minecraft.getInstance().getCameraEntity() && Minecraft.getInstance().options.getCameraType().isFirstPerson();
		if (r.nextInt(6) == 0 && !ownView) {
			p.level().addParticle(ParticleTypes.WAX_OFF, p.getX() + (r.nextDouble() - 0.5) * 0.8, p.getY() + r.nextDouble() * p.getBbHeight(),
					p.getZ() + (r.nextDouble() - 0.5) * 0.8, 0, 0.02, 0);
		}
		int flags = Omnitrix.state(p).kitFlags;
		if (p instanceof net.minecraft.client.player.LocalPlayer me) {
			// v0.17.2 (user: "work similar to cryokinesis ice slide"): the Ice Slide's ride, on his own client
			com.projecthero.mod.client.mutation.CryokinesisClient.ride(me, (flags & DiamondheadKit.F_SLIDING) != 0, RIDE);
		}
		if ((flags & DiamondheadKit.F_SLIDING) != 0 && p.onGround()) {
			p.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, DiamondheadBlocks.CRYSTAL.defaultBlockState()), p.getX(), p.getY() + 0.1,
					p.getZ(), 0, 0.1, 0);
		}
	}

	@Override
	public float[] badge() {
		// v0.17.1 (user: "position our alien badges more on top of those alien badges"): over the skin's own symbol (now painted
		// out), high on his left breast
		// v0.17.2 (user: "its not covering the badge thats on the skin and also make the badge slightly bigger"): the symbol is
		// jacket texels 25..26 (the paint-out had cleared 26..27, a texel to its right), so 1 px in; 3 px across (was 2.4)
		return new float[] { 2.0f, 2.0f, -2.06f, 3.0f };
	}
}
