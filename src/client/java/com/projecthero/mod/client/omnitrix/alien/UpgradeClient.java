package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.omnitrix.alien.upgrade.UpgradeTakeover;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.upgrade.UpgradeKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Upgrade's client kit -- the black Mechamorph skin with its green circuit lines GLOWING (an emissive copy of the body,
 * pulsing slowly), the one round green eye, an arm that stretches out like liquid metal for the grab / lash / grapple, a flat
 * slick puddle pose while he slides, and dripping liquid. A pose for every move.
 *
 * <p>v0.17.2 (user: "please upgrade upgrades vfx aswell please"): the world effects -- the merge coat and its takeover, the
 * beams, tendrils, arcs, the EMP shockwave and the Liquid Form's puddle -- are {@link UpgradeFx}'s; here the circuits surge
 * brighter as he charges / fires, the Overclock and Upgrade Tool spin circuit rings round his hand, the Jolt flares at the fist,
 * and in the Liquid Form the body is hidden (he is the puddle).
 *
 * <p>v0.17.2 round 3 (user: "edit and update animations on all moves please"): every move's third- and first-person clip redone
 * with a wind-up and a follow-through. The held Optic Beam and the Stretch Grapple hold a pose for as long as they run. The
 * Stretch Fist and Stretch Grapple are his real right arm ({@link #reach}): the model part lengthened the way
 * {@code ElasticityClient} does it, aimed at the point it goes for, a smooth liquid-metal fist (or open gripping hand) on its end
 * ({@link #hand}); in first person the whole body is drawn while it is out ({@code FirstPersonBody}), as for Elasticity.
 */
public class UpgradeClient implements AlienClientKit {
	// v0.17.1 (user: "add abilities to the V key for every alien"): the V move's pose and first-person clip
	private static final FirstPersonClip FP_OVERCLOCK = FirstPersonClip.of(FirstPersonClip.f(0, 0f, 0f, 0f, 0f, 0f, 0f), FirstPersonClip.f(3, -30f, 15f, -20f, 0.05f, 0.12f, -0.2f),
			FirstPersonClip.f(8, -36f, 18f, 24f, 0.06f, 0.14f, -0.22f), FirstPersonClip.f(11, -24f, 12f, -8f, 0.04f, 0.08f, -0.14f), FirstPersonClip.f(16, 0f, 0f, 0f, 0f, 0f, 0f));
	/** v0.17.2: arm columns aimed down the look (the tendril leaves the hand along it, UpgradeFx). */
	private static final float A = QuirkKeyframes.AIM;
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/upgrade.png");
	private static final ResourceLocation GLOW_SKIN = ProjectHeroMod.id("textures/entity/omnitrix/upgrade_glow.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/upgrade_extras.png");
	private static final DustParticleOptions DRIP = new DustParticleOptions(new Vector3f(0.06f, 0.065f, 0.07f), 0.9f);

	private static final float[] EYE = { 0.5f, 0f, 1f, 0.5f };
	private static final float[] PUDDLE = { 0f, 0.5f, 0.5f, 1f };

	/** Long enough for any hold: the held clips sit on their last frame until the kit plays the release. */
	private static final float HELD = 72000f;
	/** The arms, the torso and the head (the legs keep walking). */
	private static final int UPPER = QuirkKeyframes.bit(QuirkKeyframes.R_ARM_X) | QuirkKeyframes.bit(QuirkKeyframes.R_ARM_Y) | QuirkKeyframes.bit(QuirkKeyframes.R_ARM_Z)
			| QuirkKeyframes.bit(QuirkKeyframes.L_ARM_X) | QuirkKeyframes.bit(QuirkKeyframes.L_ARM_Y) | QuirkKeyframes.bit(QuirkKeyframes.L_ARM_Z)
			| QuirkKeyframes.bit(QuirkKeyframes.BODY_X) | QuirkKeyframes.bit(QuirkKeyframes.BODY_Y) | QuirkKeyframes.bit(QuirkKeyframes.HEAD_X);
	// the held beam: head thrust a touch forward, shoulders back, hands open and low as the eye burns; he can walk with it (UPPER)
	private static final float[] BEAM_F = { 0, 0.18f, 0, 0.42f, 0.18f, 0, -0.42f, -0.08f, 0, 0, 0, 0, 0, 0, 0, 0.04f };
	private static final float[][] BEAM = { QuirkKeyframes.rest(0), held(3, BEAM_F), held(HELD, BEAM_F) };
	private static final float[][] BEAM_END = { held(0, BEAM_F), QuirkKeyframes.f(3, -0.15f, 0, 0.1f, -0.15f, 0, -0.1f, 0.14f, 0, 0, 0, 0, 0, 0, 0, 0.1f), QuirkKeyframes.rest(9) };
	// the charge: both hands come up beside the head and close in on the eye, the feet brace, the knees give
	private static final float[][] CHARGE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(5, -2.25f, -0.6f, 0.1f, -2.25f, 0.6f, -0.1f, 0.1f, 0.05f, -0.3f, 0, 0.12f, 0.3f, 0, -0.12f, 0.05f),
			QuirkKeyframes.f(20, -2.45f, -0.75f, 0.05f, -2.45f, 0.75f, -0.05f, 0.2f, 0.05f, -0.35f, 0, 0.15f, 0.35f, 0, -0.15f, 0.12f),
			QuirkKeyframes.f(22, -2.45f, -0.75f, 0.05f, -2.45f, 0.75f, -0.05f, 0.2f, 0.05f, -0.35f, 0, 0.15f, 0.35f, 0, -0.15f, 0.12f) };
	// the shot throws the hands wide and rocks him back onto the rear foot, then he settles forward
	private static final float[][] CHARGED = { QuirkKeyframes.f(0, -2.45f, -0.75f, 0.05f, -2.45f, 0.75f, -0.05f, 0.2f, 0.05f, -0.35f, 0, 0.15f, 0.35f, 0, -0.15f, 0.12f),
			QuirkKeyframes.f(2, 0.5f, 0.2f, 1.0f, 0.5f, -0.2f, -1.0f, -0.28f, 0, 0.35f, 0, 0.15f, -0.45f, 0, -0.15f, -0.16f),
			QuirkKeyframes.f(11, 0.4f, 0.2f, 0.9f, 0.4f, -0.2f, -0.9f, -0.2f, 0, 0.3f, 0, 0.15f, -0.4f, 0, -0.15f, -0.1f),
			QuirkKeyframes.f(15, -0.1f, 0, 0.2f, -0.1f, 0, -0.2f, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0.05f), QuirkKeyframes.rest(20) };
	// Liquid Engulf: drawn back, flung down the look, the fingers close and he hauls on it as the metal sets
	private static final float[][] GRAB = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.55f, 0.3f, 0.2f, -0.35f, 0, -0.1f, 0.04f, 0.45f, 0.2f, 0, 0, -0.2f, 0, 0, 0),
			QuirkKeyframes.f(4, A, A, 0, 0.45f, 0, -0.2f, 0.2f, -0.45f, -0.3f, 0, 0, 0.3f, 0, 0, 0), QuirkKeyframes.f(9, A, A, 0, 0.5f, 0, -0.2f, 0.22f, -0.45f, -0.3f, 0, 0, 0.3f, 0, 0, 0),
			QuirkKeyframes.f(12, -1.05f, 0.1f, 0.1f, 0.35f, 0, -0.15f, 0.02f, -0.2f, 0.1f, 0, 0, -0.1f, 0, 0, -0.05f), QuirkKeyframes.rest(18) };
	// Circuit Lash: the arm cocks out to his right, the torso winds with it, then it cracks all the way across and follows through
	private static final float[][] LASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.35f, 0.95f, 0.45f, 0.35f, 0, -0.25f, 0.05f, 0.5f, 0.15f, 0, 0, -0.15f, 0, 0, -0.05f),
			QuirkKeyframes.f(5, -1.5f, 0f, 0.1f, 0.4f, 0, -0.3f, 0.2f, -0.1f, -0.15f, 0, 0, 0.2f, 0, 0, 0.03f),
			QuirkKeyframes.f(7, -1.25f, -1.05f, -0.1f, 0.45f, 0, -0.4f, 0.22f, -0.6f, -0.3f, 0, 0, 0.3f, 0, 0, 0.05f),
			QuirkKeyframes.f(10, -0.8f, -1.2f, -0.05f, 0.3f, 0, -0.3f, 0.12f, -0.5f, -0.2f, 0, 0, 0.2f, 0, 0, 0), QuirkKeyframes.rest(16) };
	private static final float[][] SLIDE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.9f, 0, 0.2f, -2.9f, 0, -0.2f, 1.25f, 0, 0.9f, 0, 0.1f, 0.9f, 0, -0.1f, -1.2f),
			QuirkKeyframes.f(40, -2.9f, 0, 0.2f, -2.9f, 0, -0.2f, 1.25f, 0, 0.9f, 0, 0.1f, 0.9f, 0, -0.1f, -1.2f), QuirkKeyframes.rest(44) };
	// the Stretch Grapple: the right arm is aimed at the anchor (aimArm); the rest hauls and then trails behind him
	private static final float[] GRAPPLE_F = { 0, A, A, 0, 0.75f, 0, -0.45f, 0.32f, -0.3f, 0.4f, 0, 0.05f, 0.75f, 0, -0.05f, -0.2f };
	private static final float[][] GRAPPLE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, A, A, 0, 0.4f, 0, -0.2f, 0.15f, -0.4f, -0.2f, 0, 0, 0.3f, 0, 0, -0.1f), held(8, GRAPPLE_F), held(HELD, GRAPPLE_F) };
	private static final float[][] GRAPPLE_END = { QuirkKeyframes.f(0, -1.5f, 0, 0, 0.75f, 0, -0.45f, 0.32f, -0.3f, 0.4f, 0, 0.05f, 0.75f, 0, -0.05f, -0.2f),
			QuirkKeyframes.f(4, -0.5f, 0, 0.5f, -0.5f, 0, -0.5f, 0.05f, 0, -0.5f, 0, 0.1f, 0.15f, 0, -0.1f, 0), QuirkKeyframes.rest(10) };
	// the living-target Takeover: both palms shoved at it, a kick back as the arc goes in, the hands crackling
	private static final float[][] OVERLOAD = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.4f, 0, 0.3f, 0.4f, 0, -0.3f, -0.08f, 0, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(4, A, A, 0, -1.3f, 0.55f, 0, 0.2f, -0.2f, -0.2f, 0, 0, 0.2f, 0, 0, 0), QuirkKeyframes.f(9, A, A, 0, -1.25f, 0.55f, 0, 0.14f, -0.2f, -0.2f, 0, 0, 0.2f, 0, 0, 0),
			QuirkKeyframes.rest(15) };
	// EMP Burst: he folds in on himself and everything bursts outward, chest open, head back
	private static final float[][] EMP = { QuirkKeyframes.fc(0, -0.9f, -0.5f, 0.1f, -0.9f, 0.5f, -0.1f, 0.5f, 0, -0.4f, 0, 0.2f, -0.4f, 0, -0.2f, 0.4f, 0.8f),
			QuirkKeyframes.fc(3, 0.35f, 0, 1.9f, 0.35f, 0, -1.9f, -0.3f, 0, 0, 0, 0.4f, 0, 0, -0.4f, -0.5f, 0),
			QuirkKeyframes.fc(11, 0.25f, 0, 1.7f, 0.25f, 0, -1.7f, -0.22f, 0, 0, 0, 0.35f, 0, 0, -0.35f, -0.4f, 0),
			QuirkKeyframes.fc(15, -0.2f, 0, 0.4f, -0.2f, 0, -0.4f, 0.12f, 0, 0, 0, 0, 0, 0, 0, 0.1f, 0.15f), QuirkKeyframes.restC(20) };
	// Merge: he dips and reaches in with both hands, pouring himself forward
	private static final float[][] MERGE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.3f, 0, 0.2f, 0.3f, 0, -0.2f, 0.1f, 0, 0.2f, 0, 0, -0.2f, 0, 0, 0),
			QuirkKeyframes.f(5, -1.55f, -0.3f, 0, -1.55f, 0.3f, 0, 0.65f, 0, -0.55f, 0, 0, 0.45f, 0, 0, 0.35f), QuirkKeyframes.f(9, -1.75f, -0.1f, 0, -1.75f, 0.1f, 0, 0.8f, 0, -0.65f, 0, 0, 0.55f, 0, 0, 0.45f),
			QuirkKeyframes.rest(14) };
	// Upgrade Tool: the off hand cups the work, the other hand passes over it twice
	private static final float[][] REPAIR = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.0f, -0.5f, 0, -1.1f, 0.6f, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0.5f),
			QuirkKeyframes.f(8, -1.25f, -0.25f, 0, -1.1f, 0.6f, 0, 0.22f, 0.08f, 0, 0, 0, 0, 0, 0, 0.5f), QuirkKeyframes.f(13, -0.95f, -0.6f, 0, -1.15f, 0.6f, 0, 0.22f, -0.08f, 0, 0, 0, 0, 0, 0, 0.5f),
			QuirkKeyframes.rest(19) };
	// the Circuit Jolt: a short cock and a straight jab
	private static final float[][] JOLT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(1, 0.25f, 0.2f, 0.1f, 0.1f, 0, 0, 0, 0.3f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(3, -1.7f, 0.25f, 0, 0.4f, 0, -0.1f, 0.15f, -0.45f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(9) };
	// Stretch Fist: shoulder drawn back with the torso turned away, then the whole body thrown behind the arm, and the recoil as it snaps home
	private static final float[][] FIST = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.75f, 0.3f, 0.3f, -0.5f, 0.2f, 0, 0.04f, 0.6f, 0.25f, 0, 0, -0.25f, 0, 0, 0),
			QuirkKeyframes.f(4, A, A, 0, 0.55f, 0, -0.2f, 0.26f, -0.55f, -0.35f, 0, 0, 0.4f, 0, 0, -0.06f),
			QuirkKeyframes.f(8, A, A, 0, 0.55f, 0, -0.2f, 0.26f, -0.55f, -0.35f, 0, 0, 0.4f, 0, 0, -0.06f),
			QuirkKeyframes.f(12, -0.7f, 0, 0.15f, 0.2f, 0, -0.1f, 0.02f, -0.1f, 0.15f, 0, 0, -0.1f, 0, 0, 0), QuirkKeyframes.rest(16) };
	// Tech Takeover: a flick of the wrist, pointed, the other hand steadying it
	private static final float[][] TAKEOVER = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -2.0f, 0.2f, 0, -0.5f, 0.3f, 0, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(4, A, A, 0, -1.0f, 0.55f, 0, 0.14f, -0.3f, -0.15f, 0, 0, 0.15f, 0, 0, 0), QuirkKeyframes.f(12, A, A, 0, -0.95f, 0.55f, 0, 0.12f, -0.3f, -0.15f, 0, 0, 0.15f, 0, 0, 0),
			QuirkKeyframes.rest(17) };
	// Overclock: the tool held up and tilted as he runs into it
	private static final float[][] OVERCLOCK2 = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.5f, 0.1f, 0f, 0.2f, 0f, 0f, 0.1f, -0.3f, 0f, 0f, 0f, 0f, 0f, 0f, 0.1f),
			QuirkKeyframes.f(8, -1.75f, 0.2f, -0.25f, 0.2f, 0f, 0f, 0.12f, -0.3f, 0f, 0f, 0f, 0f, 0f, 0f, 0.15f), QuirkKeyframes.f(11, -1.6f, 0f, 0.1f, 0.15f, 0f, 0f, 0.06f, -0.15f, 0f, 0f, 0f, 0f, 0f, 0f, 0.1f),
			QuirkKeyframes.rest(16) };
	private static final float[][] GEYSER = { QuirkKeyframes.f(0, -2.9f, 0, 0.3f, -2.9f, 0, -0.3f, -0.2f, 0, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(5, -2.7f, 0, 0.5f, -2.7f, 0, -0.5f, -0.15f, 0, -0.3f, 0, 0, 0.2f, 0, 0, 0.2f), QuirkKeyframes.rest(12) };
	// rising out of the puddle: arms drawn up out of it first, then he shakes the shape back into himself
	private static final float[][] RISE = { QuirkKeyframes.fc(0, -2.7f, 0, 0.3f, -2.7f, 0, -0.3f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.35f, 1f),
			QuirkKeyframes.fc(5, -1.0f, 0, 0.5f, -1.0f, 0, -0.5f, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0.1f, 0.35f), QuirkKeyframes.restC(11) };
	private static final FirstPersonClip FP_FIST = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 20, -10, 0, -0.05f, -0.1f, 0.15f),
			FirstPersonClip.f(4, -20, 15, 0, 0.12f, 0.1f, -0.5f), FirstPersonClip.f(10, -20, 15, 0, 0.12f, 0.1f, -0.5f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_GEYSER = FirstPersonClip.of(FirstPersonClip.f(0, -70, 10, 0, 0.1f, 0.35f, -0.2f), FirstPersonClip.f(5, -40, 10, 0, 0.1f, 0.2f, -0.2f),
			FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_RISE = FirstPersonClip.of(FirstPersonClip.f(0, 20, 10, 0, 0.1f, -0.5f, 0), FirstPersonClip.f(6, -20, 10, 0, 0.1f, 0.05f, -0.15f),
			FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();
	/** v0.17.2 revamp (Tech Sense): a machine's outline -- faint green; one an Upgrade holds, bright. */
	public static final int SENSE_FAINT = 0x58A127, SENSE_HELD = 0x90E255;

	// the held beam: the hands stay low at the edges of the view, a little apart, so nothing sits over the beam
	private static final FirstPersonClip FP_BEAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 14, -14, 6, -0.08f, -0.14f, 0.04f),
			FirstPersonClip.f(HELD, 14, -14, 6, -0.08f, -0.14f, 0.04f)).both();
	private static final FirstPersonClip FP_BEAM_END = FirstPersonClip.of(FirstPersonClip.f(0, 14, -14, 6, -0.08f, -0.14f, 0.04f), FirstPersonClip.f(3, -8, 4, 0, 0, 0.03f, -0.05f),
			FirstPersonClip.f(9, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_CHARGED = FirstPersonClip.of(FirstPersonClip.f(0, -48, 44, 0, 0.26f, 0.28f, 0.05f), FirstPersonClip.f(2, 25, -30, 12, -0.16f, -0.2f, 0.1f),
			FirstPersonClip.f(11, 20, -26, 10, -0.14f, -0.18f, 0.08f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_CHARGE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(5, -40, 36, 0, 0.2f, 0.2f, 0.05f),
			FirstPersonClip.f(22, -48, 44, 0, 0.26f, 0.28f, 0.05f)).both();
	private static final FirstPersonClip FP_REACH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 16, -12, 0, -0.05f, -0.08f, 0.14f),
			FirstPersonClip.f(4, -25, 22, 0, 0.15f, 0.12f, -0.38f), FirstPersonClip.f(9, -25, 22, 0, 0.15f, 0.12f, -0.38f), FirstPersonClip.f(12, -12, 12, 0, 0.08f, 0.04f, -0.1f),
			FirstPersonClip.f(18, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_MERGE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 10, 0, 0, 0, -0.06f, 0.08f),
			FirstPersonClip.f(5, -30, 24, 0, 0.16f, 0.1f, -0.42f), FirstPersonClip.f(9, -34, 26, 0, 0.18f, 0.08f, -0.5f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	// the lash: cocked out at the right of the view, then all the way across and through
	private static final FirstPersonClip FP_LASH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -48, -36, 18, -0.3f, 0.2f, 0.05f),
			FirstPersonClip.f(5, -34, 16, 0, 0.05f, 0.18f, -0.3f), FirstPersonClip.f(7, -26, 62, -14, 0.6f, 0.12f, -0.2f), FirstPersonClip.f(10, -18, 70, -10, 0.72f, 0.02f, -0.05f),
			FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_GRAPPLE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -55, 20, 0, 0.12f, 0.35f, -0.25f),
			FirstPersonClip.f(HELD, -55, 20, 0, 0.12f, 0.35f, -0.25f));
	private static final FirstPersonClip FP_GRAPPLE_END = FirstPersonClip.of(FirstPersonClip.f(0, -55, 20, 0, 0.12f, 0.35f, -0.25f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_POINT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -55, 12, 0, 0.1f, 0.3f, -0.05f),
			FirstPersonClip.f(4, -25, 25, 0, 0.18f, 0.12f, -0.34f), FirstPersonClip.f(12, -25, 25, 0, 0.18f, 0.12f, -0.3f), FirstPersonClip.f(17, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SHOVE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 18, -10, 0, -0.04f, -0.08f, 0.12f),
			FirstPersonClip.f(4, -24, 24, 0, 0.16f, 0.14f, -0.36f), FirstPersonClip.f(9, -22, 22, 0, 0.14f, 0.12f, -0.28f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0)).both();
	// the EMP: hands snatched in to the middle, then flung out of the view
	private static final FirstPersonClip FP_WIDE = FirstPersonClip.of(FirstPersonClip.f(0, -30, 40, 0, 0.3f, 0.02f, -0.1f), FirstPersonClip.f(3, -35, -46, 30, -0.3f, 0.2f, 0),
			FirstPersonClip.f(11, -30, -42, 26, -0.26f, 0.16f, 0), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_DIVE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -60, 10, 0, 0.1f, 0.3f, -0.3f),
			FirstPersonClip.f(7, 30, 10, 0, 0.1f, -0.7f, -0.1f), FirstPersonClip.f(8, 30, 10, 0, 0.1f, -0.9f, -0.1f)).both();
	private static final FirstPersonClip FP_CUP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, 30, 0, 0.18f, 0.12f, -0.05f),
			FirstPersonClip.f(8, -28, 40, 8, 0.24f, 0.17f, -0.08f), FirstPersonClip.f(13, -18, 26, -6, 0.16f, 0.1f, -0.04f), FirstPersonClip.f(19, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_JOLT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(1, 10, -6, 0, -0.03f, -0.04f, 0.08f),
			FirstPersonClip.f(3, -20, 25, 0, 0.15f, 0.08f, -0.22f), FirstPersonClip.f(9, 0, 0, 0, 0, 0, 0));

	/** A held frame (a 16-column frame) at {@code tick}. */
	private static float[] held(float tick, float[] f) {
		float[] o = f.clone();
		o[0] = tick;
		return o;
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	private static UpgradeMachineBody machineBody;

	/**
	 * v0.17.2 (user: "When the player takes over a machine they should become the machine"): the kit {@code p} is drawn with -- the
	 * upgraded machine's body while he carries one, else this. From {@code AlienClients.shownKit}.
	 */
	public AlienClientKit shownAs(net.minecraft.world.entity.player.Player p) {
		if (this instanceof UpgradeMachineBody || UpgradeMachineBody.kindOf(p) == null) {
			return this;
		}
		if (machineBody == null) {
			machineBody = new UpgradeMachineBody();
		}
		return machineBody;
	}

	// ---------------------------------------------------------------- v0.17.2 round 3: the arm itself stretches

	/** The model's render scale: a pixel of the player model is this many blocks. */
	private static final float PX = 0.9375f / 16f;

	/**
	 * The stretched right arm right now.
	 *
	 * @param tip   where the arm goes (world)
	 * @param ext   how far out it is (0 at the shoulder's natural length, 1 at {@code tip})
	 * @param size  the hand's size (1 = his own hand, the fist grows from it)
	 * @param open  0 a fist, 1 the fingers spread to grab
	 * @param wobble the rubbery shake as it snaps back
	 */
	public record Reach(Vec3 tip, float ext, float size, float open, float wobble) {
	}

	private static float ease(float k) {
		k = Mth.clamp(k, 0f, 1f);
		return k * k * (3f - 2f * k);
	}

	private static long timer(OmnitrixState s, String name) {
		return s.readyAt(UpgradeKit.ID + "." + name);
	}

	/** The arm is drawn only on his own body: inside a golem, or as a machine, UpgradeFx's strand stands in for it. */
	static boolean armShown(net.minecraft.world.entity.player.Player p) {
		return !p.isInvisible() && UpgradeMachineBody.kindOf(p) == null;
	}

	/** What the arm is doing on {@code p} now, from the synced state; null while it is just an arm. */
	@Nullable
	public static Reach reach(net.minecraft.world.entity.player.Player p, float partial) {
		if (!Omnitrix.hasPower(p) || !armShown(p)) {
			return null;
		}
		OmnitrixState s = Omnitrix.state(p);
		if (!UpgradeKit.ID.equals(s.active) || !s.transformed()) {
			return null;
		}
		float now = p.level().getGameTime() + partial;
		float t = now - s.animStart;
		if ((s.kitFlags & UpgradeKit.F_GRAPPLE) != 0 || s.animId == UpgradeKit.ANIM_GRAPPLE_END) {
			long x = timer(s, UpgradeKit.T_GRAPPLE_X);
			if (x <= 0) {
				return null;
			}
			Vec3 tip = new Vec3(UpgradeKit.decode(x), UpgradeKit.decode(timer(s, UpgradeKit.T_GRAPPLE_Y)), UpgradeKit.decode(timer(s, UpgradeKit.T_GRAPPLE_Z)));
			if ((s.kitFlags & UpgradeKit.F_GRAPPLE) != 0) {
				long at = timer(s, UpgradeKit.T_GRAPPLE_AT), go = timer(s, UpgradeKit.T_GRAPPLE_GO);
				float k = Mth.clamp((now - at) / Math.max(1f, go - at), 0f, 1f);
				// the fingers spread on the way, and bite as it lands
				float grip = Mth.clamp((now - go) / 2f, 0f, 1f);
				return new Reach(tip, k, 1.3f + 0.25f * Mth.sin(k * Mth.PI), 1f - grip * 0.55f, 0f);
			}
			if (t > 7f) {
				return null;
			}
			return new Reach(tip, 1f - ease(t / 6f), 1.3f - 0.3f * ease(t / 6f), 0.45f, Mth.sin(t * 2.4f) * 0.14f * (1f - t / 7f));
		}
		if (s.animId == UpgradeKit.ANIM_FIST && t >= 0f && t <= 17f) {
			long x = timer(s, UpgradeKit.T_FX_X);
			if (x <= 0) {
				return null;
			}
			Vec3 tip = new Vec3(UpgradeKit.decode(x), UpgradeKit.decode(timer(s, UpgradeKit.T_FX_Y)), UpgradeKit.decode(timer(s, UpgradeKit.T_FX_Z)));
			float land = UpgradeKit.FIST_LAND;
			// the hand swells as the shoulder draws back, the arm shoots out, the fist hits, holds, and snaps back with a wobble
			float ext = t < 1.5f ? 0f : t < land ? ease((t - 1.5f) / (land - 1.5f)) : t < 9f ? 1f : 1f - ease((t - 9f) / 6f);
			float size = 1f + 0.7f * ease(t / 3f) - 0.7f * ease((t - 12f) / 5f);
			if (t >= land && t < land + 4f) {
				size *= 1f + 0.3f * Mth.sin((t - land) / 4f * Mth.PI); // it balloons on impact
			}
			return new Reach(tip, ext, size, 0f, t > 9f ? Mth.sin((t - 9f) * 2.2f) * 0.16f * (1f - (t - 9f) / 8f) : 0f);
		}
		return null;
	}

	/** For the full-body first-person view: while the arm is out the real arm is in view, as for Elasticity. */
	public static boolean armOut(AbstractClientPlayer p) {
		return reach(p, partial()) != null;
	}

	private static float partial() {
		return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
	}

	/**
	 * Turns the right arm to the point it goes for and lengthens it (the {@code ElasticityClient} way: the model part itself, in
	 * his own skin, the glow and the armour following). Blends from the keyframed arm as it leaves and comes home.
	 */
	private static void aimArm(AbstractClientPlayer p, HumanoidModel<?> m, Reach r) {
		float partial = partial();
		ModelPart arm = m.rightArm;
		float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
		// the shoulder in the world: the model's pivot through the player renderer's flip and turn
		double lx = -arm.x * PX, ly = (24f - arm.y) * PX, lz = arm.z * PX;
		float th = (180f - bodyYaw) * Mth.DEG_TO_RAD;
		Vec3 shoulder = p.getPosition(partial).add(lx * Mth.cos(th) + lz * Mth.sin(th), ly, -lx * Mth.sin(th) + lz * Mth.cos(th));
		Vec3 d = r.tip().subtract(shoulder);
		double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
		float yawTo = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
		float pitchTo = (float) -(Mth.atan2(d.y, horiz) * Mth.RAD_TO_DEG);
		float w = Mth.clamp(r.ext() * 3f, 0f, 1f);
		arm.xRot = Mth.lerp(w, arm.xRot, pitchTo * Mth.DEG_TO_RAD - Mth.HALF_PI);
		arm.yRot = Mth.lerp(w, arm.yRot, Mth.wrapDegrees(yawTo - bodyYaw) * Mth.DEG_TO_RAD);
		arm.zRot = Mth.lerp(w, arm.zRot, 0f);
		// stretchArm's hand ends 1.2 x the length it is given, less the 2 px above the pivot
		float wanted = 10f + r.ext() * Math.max(0f, (float) d.length() / PX - 10f);
		float girth = Mth.lerp(Mth.clamp(wanted / 260f, 0f, 1f), 1f, 0.68f);
		com.projecthero.mod.client.mutation.ElasticityClient.stretchArm(arm, (wanted + 2f) / 1.2f * (1f + r.wobble() * 0.5f), girth * (1f - r.wobble()));
	}

	/** Pixels; a smooth lump of liquid metal (a superellipsoid) with its circuits lit, in the current pose. */
	private static void lump(PoseStack pose, VertexConsumer vc, float cx, float cy, float cz, float rx, float ry, float rz, int argb, int light) {
		int rings = 7, segs = 10;
		PoseStack.Pose last = pose.last();
		float s = 1f / 16f;
		for (int i = 0; i < rings; i++) {
			for (int j = 0; j < segs; j++) {
				for (int k = 0; k < 4; k++) {
					int a = i + (k == 1 || k == 2 ? 1 : 0), b = j + (k >= 2 ? 1 : 0);
					float lat = Mth.PI * a / rings - Mth.HALF_PI, lon = Mth.TWO_PI * b / segs;
					float cl = Mth.cos(lat), sl = Mth.sin(lat), co = Mth.cos(lon), so = Mth.sin(lon);
					// squarer than a sphere: a knuckle, not a ball
					float e = 0.72f;
					float px = sg(cl) * pow(cl, e) * sg(co) * pow(co, e), py = sg(sl) * pow(sl, e), pz = sg(cl) * pow(cl, e) * sg(so) * pow(so, e);
					vc.addVertex(last, (cx + px * rx) * s, (cy + py * ry) * s, (cz + pz * rz) * s).setColor(argb).setUv(0.05f + 0.4f * b / segs + cx * 0.02f, 0.05f + 0.4f * a / rings + cz * 0.02f)
							.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, cl * co, sl, cl * so);
				}
			}
		}
	}

	private static float sg(float v) {
		return v < 0 ? -1f : 1f;
	}

	private static float pow(float v, float e) {
		return (float) Math.pow(Math.abs(v), e);
	}

	/**
	 * The hand on the end of the stretched arm, down the pose's +y (the way the arm runs): a palm and four knuckles curled over it
	 * with the thumb across ({@code open} 0), or the fingers spread out ahead of it ({@code open} 1). {@code size} scales it.
	 */
	private static void hand(PoseStack pose, MultiBufferSource buffers, Reach r, int light, float age) {
		pose.pushPose();
		pose.scale(r.size(), r.size(), r.size());
		float o = r.open();
		for (int pass = 0; pass < 2; pass++) {
			VertexConsumer vc = buffers.getBuffer(pass == 0 ? RenderType.entityTranslucent(UpgradeFx.TECHNO) : RenderType.eyes(UpgradeFx.TECHNO_GLOW));
			int col = pass == 0 ? 0xFFFFFFFF : 0xFF000000 | (int) (170 + 60 * Mth.sin(age * 0.3f)) * 0x010101;
			float g = pass * 0.03f;
			lump(pose, vc, 0f, 0.6f, 0f, 3.1f + g, 3.3f + g, 2.7f + g, col, light);
			for (int i = 0; i < 4; i++) {
				float sp = i - 1.5f;
				// knuckle (closed: curled round the front of the palm; open: straight out, fanned)
				float x = sp * (1.55f + o * 0.75f), y = Mth.lerp(o, 3.5f, 5.6f), z = Mth.lerp(o, -1.5f, -0.3f);
				lump(pose, vc, x, y, z, 0.95f + g, Mth.lerp(o, 1.2f, 2.6f) + g, Mth.lerp(o, 1.6f, 0.95f) + g, col, light);
			}
			lump(pose, vc, Mth.lerp(o, 1.1f, 4.6f), Mth.lerp(o, 1.4f, 2.4f), Mth.lerp(o, -3.1f, -0.8f), Mth.lerp(o, 2.1f, 1.0f) + g, 1.05f + g, Mth.lerp(o, 1.05f, 1.2f) + g, col, light);
		}
		pose.popPose();
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		OmnitrixState st = Omnitrix.state(p);
		if ((st.kitFlags & UpgradeKit.F_SLIDE) != 0) {
			// v0.17.2: the Liquid Form is all puddle (UpgradeFx draws it in the world pass); the body is hidden (bodyAlpha)
			return;
		}
		// the circuits glow (the whole body again, emissive, only the lines and the eye lit), pulsing slowly -- v0.17.2: brighter
		// while he charges, overclocks or fires
		float ticks = p.level().getGameTime() + partial - st.animStart;
		float surge = (st.kitFlags & (UpgradeKit.F_CHARGING | UpgradeKit.F_OVERCLOCK | UpgradeKit.F_BEAM)) != 0 ? 1f
				: (st.animId == UpgradeKit.ANIM_EMP || st.animId == UpgradeKit.ANIM_CHARGED || st.animId == UpgradeKit.ANIM_OVERLOAD) && ticks < 10f ? 1f - ticks / 10f : 0f;
		int g = (int) Mth.clamp(200 + 55 * Mth.sin(age * (0.12f + surge * 0.6f)) + surge * 40, 0, 255);
		int colour = 0xFF000000 | (g << 16) | (g << 8) | g;
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW_SKIN)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, colour);
		// v0.17.2 round 3: the fist (or grabbing hand) on the end of the stretched arm
		Reach r = reach(p, partial);
		if (r != null) {
			ModelPart arm = model.rightArm;
			pose.pushPose();
			pose.translate(arm.x / 16f, arm.y / 16f, arm.z / 16f);
			pose.mulPose(new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot));
			boolean slim = "slim".equals(p.getSkin().model().id());
			pose.translate((slim ? -0.5f : -1f) / 16f, 10f * arm.yScale / 16f - 2f / 16f, 0f);
			hand(pose, buffers, r, light, age);
			pose.popPose();
		}
		// v0.17.2 VFX: V Overclock -- the held tool hums with his circuitry (a green glow and two spinning rings at the hand);
		// Shift+C the same rings, tighter, while he works on the tool
		boolean overclock = (st.kitFlags & UpgradeKit.F_OVERCLOCK) != 0;
		boolean repair = st.animId == UpgradeKit.ANIM_REPAIR && ticks < 18f;
		if (overclock || repair) {
			float k = repair ? Mth.sin(Mth.clamp(ticks / 18f, 0f, 1f) * Mth.PI) : 0.75f + 0.25f * Mth.sin(age * 0.5f);
			pose.pushPose();
			model.rightArm.translateAndRotate(pose);
			pose.translate(-1f / 16f, 11f / 16f, 0f);
			PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), Vec3.ZERO, 0.3f * k + 0.1f, PyroDraw.dim(UpgradeFx.GREEN, 0.7f * k));
			for (int i = 0; i < 2; i++) {
				pose.pushPose();
				pose.mulPose(com.mojang.math.Axis.YP.rotation(age * (i == 0 ? 0.35f : -0.5f)));
				pose.mulPose(com.mojang.math.Axis.XP.rotation(i == 0 ? 0.5f : -0.7f));
				PyroDraw.band(pose, PyroDraw.additive(buffers), (repair ? 0.22f : 0.3f) + 0.03f * Mth.sin(age * 0.3f + i), 0.018f,
						PyroDraw.dim(UpgradeFx.PALE, 0.8f * k), 0xFF000000);
				pose.popPose();
			}
			// v0.17.2 revamp (weapon boost): a bow, crossbow or gun gets a green muzzle ring out past the hand, pulsing outward
			if (overclock && (p.getMainHandItem().getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem
					|| com.projecthero.mod.firearm.Firearms.of(p.getMainHandItem()) != null)) {
				float pulse = (age * 0.08f) % 1f;
				pose.pushPose();
				pose.translate(0f, 0.5f, 0f);
				PyroDraw.band(pose, PyroDraw.additive(buffers), 0.14f + 0.08f * pulse, 0.02f, PyroDraw.dim(UpgradeFx.GREEN, 0.9f * (1f - pulse)), 0xFF000000);
				PyroDraw.band(pose, PyroDraw.additive(buffers), 0.12f, 0.015f, PyroDraw.dim(UpgradeFx.PALE, 0.7f), 0xFF000000);
				pose.popPose();
			}
			pose.popPose();
		}
		// v0.17.2 VFX: the Circuit Jolt -- a crackle of green round the fist as it lands
		if (st.animId == UpgradeKit.ANIM_JOLT && ticks < 5f) {
			pose.pushPose();
			model.rightArm.translateAndRotate(pose);
			PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), new Vec3(-1f / 16f, 10.5f / 16f, 0), 0.35f * (1f - ticks / 5f), PyroDraw.dim(UpgradeFx.GREEN, 0.9f));
			pose.popPose();
		}
		// v0.17.1 (user's minecraftskins.com Upgrade skin): the skin has its own eye -- it glows through the glow sheet above, so
		// the old round eye prop is gone
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		// the circuit line down the arm, glowing
		VertexConsumer eyes = buffers.getBuffer(RenderType.eyes(EXTRAS));
		AlienParts.box(pose, eyes, right ? -1.25f : 0.75f, -1.5f, -2.05f, right ? -0.75f : 1.25f, 9.5f, -2.0f, EYE[0], EYE[1], EYE[0] + 0.05f, EYE[1] + 0.05f,
				0xFFFFFFFF, LightTexture.FULL_BRIGHT);
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		float[][] clip = switch (animId) {
			case UpgradeKit.ANIM_BEAM_END -> BEAM_END;
			case UpgradeKit.ANIM_CHARGE -> CHARGE;
			case UpgradeKit.ANIM_CHARGED -> CHARGED;
			case UpgradeKit.ANIM_GRAB -> GRAB;
			case UpgradeKit.ANIM_LASH -> LASH;
			case UpgradeKit.ANIM_SLIDE -> null;
			case UpgradeKit.ANIM_GRAPPLE -> GRAPPLE;
			case UpgradeKit.ANIM_GRAPPLE_END -> GRAPPLE_END;
			case UpgradeKit.ANIM_OVERLOAD -> OVERLOAD;
			case UpgradeKit.ANIM_EMP -> EMP;
			case UpgradeKit.ANIM_MERGE -> MERGE;
			case UpgradeKit.ANIM_REPAIR -> REPAIR;
			case UpgradeKit.ANIM_JOLT -> JOLT;
			case UpgradeKit.ANIM_OVERCLOCK -> OVERCLOCK2;
			case UpgradeKit.ANIM_FIST -> FIST;
			case UpgradeKit.ANIM_TAKEOVER -> TAKEOVER;
			case UpgradeKit.ANIM_GEYSER -> GEYSER;
			case UpgradeKit.ANIM_RISE -> RISE;
			default -> null;
		};
		int flags = Omnitrix.state(p).kitFlags;
		if ((flags & UpgradeKit.F_SLIDE) != 0) {
			// a slick: flat out, arms ahead
			QuirkKeyframes.play(m, SLIDE, Math.min(ticks, 20f), 1f);
			return true;
		}
		boolean played;
		if ((flags & UpgradeKit.F_BEAM) != 0 && animId == UpgradeKit.ANIM_BEAM) {
			// the held beam: the upper body only (he walks with it), a tremble from the force of it
			float[] f = QuirkKeyframes.sample(BEAM, ticks, 0f, 0f);
			played = f != null;
			if (played) {
				QuirkKeyframes.applyMasked(m, f, Mth.clamp(ticks / 2f, 0f, 1f), UPPER);
				float shake = Mth.sin(age * 2.7f) * 0.018f;
				m.head.xRot += shake;
				m.rightArm.zRot += shake;
				m.leftArm.zRot -= shake;
			}
		} else {
			played = clip != null && playAimed(m, clip, ticks);
		}
		if (!played) {
			// liquid: a slow sway
			m.rightArm.zRot += Mth.sin(age * 0.09f) * 0.03f;
			m.leftArm.zRot -= Mth.sin(age * 0.09f + 1f) * 0.03f;
		}
		return played;
	}

	/**
	 * From {@code HumanoidModelMixin}, beside Elasticity's: the arm itself goes out. Not from {@link #pose}: the model's part scales
	 * are reset after the Omnitrix poses run. Not in the first-person hand pass either: the body is drawn instead.
	 */
	public static void applyArm(net.minecraft.world.entity.player.Player player, HumanoidModel<?> m) {
		if (player instanceof AbstractClientPlayer p && !com.projecthero.mod.client.ironman.IronManFlightPose.firstPersonHand) {
			Reach r = reach(p, partial());
			if (r != null) {
				aimArm(p, m, r);
			}
		}
	}

	/** Plays a keyframe table, its {@link QuirkKeyframes#AIM} arm columns resolved to point down the look. */
	private static boolean playAimed(HumanoidModel<?> m, float[][] clip, float ticks) {
		// (the aimed sample is 16 columns: a crouching 17-column table has no aimed arms, and keeps its crouch)
		float[] f = clip[0].length > QuirkKeyframes.COLS ? QuirkKeyframes.sample(clip, ticks) : QuirkKeyframes.sample(clip, ticks, -Mth.HALF_PI + m.head.xRot, m.head.yRot);
		if (f == null) {
			return false;
		}
		QuirkKeyframes.apply(m, f, QuirkKeyframes.weight(ticks, QuirkKeyframes.length(clip)));
		return true;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case UpgradeKit.ANIM_BEAM -> FP_BEAM;
			case UpgradeKit.ANIM_BEAM_END -> FP_BEAM_END;
			case UpgradeKit.ANIM_CHARGED -> FP_CHARGED;
			case UpgradeKit.ANIM_CHARGE -> FP_CHARGE;
			case UpgradeKit.ANIM_GRAB -> FP_REACH;
			case UpgradeKit.ANIM_MERGE -> FP_MERGE;
			case UpgradeKit.ANIM_LASH -> FP_LASH;
			case UpgradeKit.ANIM_GRAPPLE -> FP_GRAPPLE;
			case UpgradeKit.ANIM_GRAPPLE_END -> FP_GRAPPLE_END;
			case UpgradeKit.ANIM_OVERLOAD -> FP_SHOVE;
			case UpgradeKit.ANIM_EMP -> FP_WIDE;
			case UpgradeKit.ANIM_SLIDE -> FP_DIVE;
			case UpgradeKit.ANIM_REPAIR -> FP_CUP;
			case UpgradeKit.ANIM_JOLT -> FP_JOLT;
			case UpgradeKit.ANIM_OVERCLOCK -> FP_OVERCLOCK;
			case UpgradeKit.ANIM_FIST -> FP_FIST;
			case UpgradeKit.ANIM_TAKEOVER -> FP_POINT;
			case UpgradeKit.ANIM_GEYSER -> FP_GEYSER;
			case UpgradeKit.ANIM_RISE -> FP_RISE;
			default -> null;
		};
	}

	/**
	 * v0.17.2: in the Liquid Form he is only the puddle UpgradeFx draws. v0.17.2 revamp: after the Geyser he reforms out of the
	 * column over 6 ticks (fading in).
	 */
	@Override
	public int bodyAlpha(AbstractClientPlayer p) {
		OmnitrixState st = Omnitrix.state(p);
		if ((st.kitFlags & UpgradeKit.F_SLIDE) != 0) {
			return 0;
		}
		if (st.animId == UpgradeKit.ANIM_GEYSER || st.animId == UpgradeKit.ANIM_RISE) {
			float t = p.level().getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false) - st.animStart;
			if (t >= 0f && t < 6f) {
				return (int) Mth.clamp(40 + 215 * t / 6f, 40, 255);
			}
		}
		return 255;
	}

	// ---------------------------------------------------------------- v0.17.2 revamp: Tech Sense

	/**
	 * The outline colour {@code viewer} (a transformed Upgrade) sees on {@code e}, or -1 for none: a machine within
	 * {@link UpgradeFx#SENSE_RANGE} faint green, one an Upgrade holds bright green.
	 */
	public static int senseColour(LocalPlayer viewer, Entity e) {
		if (e == viewer || !e.isAlive() || e.distanceToSqr(viewer) > UpgradeFx.SENSE_RANGE * UpgradeFx.SENSE_RANGE) {
			return -1;
		}
		OmnitrixState s = Omnitrix.state(viewer);
		if (!UpgradeKit.ID.equals(s.active) || !s.transformed()) {
			return -1;
		}
		if (UpgradeFx.heldBy(e) >= 0) {
			return SENSE_HELD;
		}
		return UpgradeTakeover.mechanical(e) ? SENSE_FAINT : -1;
	}

	@Override
	public boolean glows(LocalPlayer viewer, Entity e) {
		return senseColour(viewer, e) >= 0;
	}

	/** For the outline colour mixin: whether {@code viewer} is shown as Upgrade. */
	public static boolean shown(LocalPlayer viewer) {
		return AlienClients.shownKit(viewer) instanceof UpgradeClient;
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		var r = p.getRandom();
		if ((Omnitrix.state(p).kitFlags & UpgradeKit.F_SLIDE) != 0) {
			return; // the puddle has its own glints and drips (UpgradeFx)
		}
		if (r.nextInt(8) == 0) {
			p.level().addParticle(DRIP, p.getX() + (r.nextDouble() - 0.5) * 0.5, p.getY() + r.nextDouble() * p.getBbHeight() * 0.8,
					p.getZ() + (r.nextDouble() - 0.5) * 0.5, 0, -0.05, 0);
		}
		int flags = Omnitrix.state(p).kitFlags;
		if ((flags & UpgradeKit.F_CHARGING) != 0 && r.nextInt(2) == 0) {
			var eye = p.getEyePosition().add(p.getLookAngle().scale(UpgradeFx.firstPersonView(p) ? 2.2 : 0.5)).add(0, UpgradeFx.firstPersonView(p) ? -0.42 : 0, 0);
			p.level().addParticle(ParticleTypes.ELECTRIC_SPARK, eye.x, eye.y, eye.z, (r.nextDouble() - 0.5) * 0.2, (r.nextDouble() - 0.5) * 0.2,
					(r.nextDouble() - 0.5) * 0.2);
		}
	}

	@Override
	public float[] badge() {
		// v0.17.1 (user: "position our alien badges more on top of those alien badges"): over the skin's own symbol (now painted
		// out), the middle of his chest
		return new float[] { 0f, 5.0f, -2.06f, 3.0f };
	}
}
