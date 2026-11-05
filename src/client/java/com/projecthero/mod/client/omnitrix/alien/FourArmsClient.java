package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.fourarms.FourArmsKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1: Four Arms' client kit -- the user's red Tetramand skin (four eyes, the black-and-white outfit), a SECOND PAIR OF ARMS (the skin's
 * own arm UVs, a touch thinner, under the first pair: they follow the upper arms with a lag and take their own swing in the
 * punches, the clap, the smashes and the holds), and a pose (third and first person) for every move.
 *
 * <p>v0.17.2 round 3 (user: "edit and update animations on all moves please"): every move is two keyframe tables that run together -- the
 * model's limbs and the lower pair ({@link #lowerClip}: {tick, right xyz, left xyz}, absolute angles in the body's space), so all four arms
 * wind up and land on the beat instead of the lower pair stepping between two poses; punches go down the aim with the hips turned
 * into them, and on the move the legs stay on the walk cycle.
 */
public final class FourArmsClient implements AlienClientKit {
	// v0.17.1 (user: "add abilities to the V key for every alien"): the V move's pose and first-person clip
	private static final float[][] CLIMB = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.6f, 0f, 0.3f, -2.6f, 0f, -0.3f, 0.1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -0.3f),
			QuirkKeyframes.f(7, -2.4f, 0f, 0.5f, -2.4f, 0f, -0.5f, 0.1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -0.3f), QuirkKeyframes.rest(12) };
	private static final FirstPersonClip FP_CLIMB = FirstPersonClip.of(FirstPersonClip.f(0, 0f, 0f, 0f, 0f, 0f, 0f), FirstPersonClip.f(3, -78f, 10f, 0f, 0.08f, 0.46f, 0.16f),
			FirstPersonClip.f(7, -70f, 8f, 6f, 0.08f, 0.4f, 0.16f), FirstPersonClip.f(12, 0f, 0f, 0f, 0f, 0f, 0f)).both();

	/** Wall Climb on and on a wall: all four hands reaching up in turn. */
	private static boolean climbing(AbstractClientPlayer p) {
		return (Omnitrix.state(p).kitFlags & FourArmsKit.F_CLIMB) != 0 && p.onClimbable() && !p.onGround();
	}
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/four_arms.png");

	private final ModelPart lowerRight;
	private final ModelPart lowerLeft;

	private static final float A = QuirkKeyframes.AIM;

	// ---- the punches: wound up across the body, driven down the aim with the hips, the other pair drawn back to balance it
	private static final float[][] PUNCH_R = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(1.2f, -0.3f, 0.35f, 0.25f, -0.9f, 0.3f, -0.1f, 0.06f, 0.4f, 0.18f, 0, 0, -0.18f, 0, 0, 0),
			QuirkKeyframes.f(3, A, A, 0, 0.35f, 0, -0.15f, 0.2f, -0.5f, 0.3f, 0, 0, -0.32f, 0, 0, 0.02f),
			QuirkKeyframes.f(5, A, A, 0, 0.3f, 0, -0.15f, 0.18f, -0.45f, 0.28f, 0, 0, -0.3f, 0, 0, 0), QuirkKeyframes.rest(10) };
	private static final float[][] PUNCH_L = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(1.2f, -0.9f, -0.3f, 0.1f, -0.3f, -0.35f, -0.25f, 0.06f, -0.4f, -0.18f, 0, 0, 0.18f, 0, 0, 0),
			QuirkKeyframes.f(3, 0.35f, 0, 0.15f, A, A, 0, 0.2f, 0.5f, -0.32f, 0, 0, 0.3f, 0, 0, 0.02f),
			QuirkKeyframes.f(5, 0.3f, 0, 0.15f, A, A, 0, 0.18f, 0.45f, -0.3f, 0, 0, 0.28f, 0, 0, 0), QuirkKeyframes.rest(10) };
	private static final float[][] LOW_PUNCH_R = { low(0, 0, 0, 0.3f, 0, 0, -0.3f), low(1.2f, -0.6f, 0, 0.3f, 0.25f, 0, -0.3f), low(3, 0.5f, 0, 0.35f, -0.55f, 0, -0.3f),
			low(10, 0, 0, 0.3f, 0, 0, -0.3f) };
	private static final float[][] LOW_PUNCH_L = { low(0, 0, 0, 0.3f, 0, 0, -0.3f), low(1.2f, 0.25f, 0, 0.3f, -0.6f, 0, -0.3f), low(3, -0.55f, 0, 0.3f, 0.5f, 0, -0.35f),
			low(10, 0, 0, 0.3f, 0, 0, -0.3f) };
	// the passive cycle's body blows: the upper pair up as a guard, a lower fist driven up into the gut
	private static final float[][] PUNCH_LOW_R = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(1.5f, -0.9f, -0.4f, 0.1f, -0.7f, 0.3f, -0.1f, 0.05f, 0.3f, 0.12f, 0, 0, -0.12f, 0, 0, 0),
			QuirkKeyframes.f(3.5f, -1.0f, -0.4f, 0.1f, -0.8f, 0.3f, -0.1f, 0.25f, -0.35f, 0.3f, 0, 0, -0.3f, 0, 0, 0.05f), QuirkKeyframes.rest(10) };
	private static final float[][] PUNCH_LOW_L = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(1.5f, -0.7f, -0.3f, 0.1f, -0.9f, 0.4f, -0.1f, 0.05f, -0.3f, -0.12f, 0, 0, 0.12f, 0, 0, 0),
			QuirkKeyframes.f(3.5f, -0.8f, -0.3f, 0.1f, -1.0f, 0.4f, -0.1f, 0.25f, 0.35f, -0.3f, 0, 0, 0.3f, 0, 0, 0.05f), QuirkKeyframes.rest(10) };
	private static final float[][] LOW_LOW_R = { low(0, 0, 0, 0.3f, 0, 0, -0.3f), low(1.5f, 0.8f, 0, 0.35f, 0.2f, 0, -0.3f), low(3.5f, -1.8f, -0.1f, 0.15f, 0.5f, 0, -0.3f),
			low(10, 0, 0, 0.3f, 0, 0, -0.3f) };
	private static final float[][] LOW_LOW_L = { low(0, 0, 0, 0.3f, 0, 0, -0.3f), low(1.5f, 0.2f, 0, 0.3f, 0.8f, 0, -0.35f), low(3.5f, 0.5f, 0, 0.3f, -1.8f, 0.1f, -0.15f),
			low(10, 0, 0, 0.3f, 0, 0, -0.3f) };
	/** The combo's third blow: both lower fists drawn back and driven in together, the upper pair guarding the face. */
	private static final float[][] COMBO_LOW = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(1.5f, -1.1f, -0.4f, 0.1f, -1.1f, 0.4f, -0.1f, -0.05f, 0, 0.1f, 0, 0, -0.1f, 0, 0, 0.1f),
			QuirkKeyframes.f(3.5f, -1.35f, -0.55f, 0.1f, -1.35f, 0.55f, -0.1f, 0.38f, 0, 0.3f, 0, 0, -0.35f, 0, 0, -0.2f),
			QuirkKeyframes.f(6, -1.3f, -0.55f, 0.1f, -1.3f, 0.55f, -0.1f, 0.34f, 0, 0.28f, 0, 0, -0.32f, 0, 0, -0.18f), QuirkKeyframes.rest(11) };
	private static final float[][] LOW_COMBO_LOW = { low(0, 0, 0, 0.3f, 0, 0, -0.3f), low(1.5f, 0.8f, 0, 0.35f, 0.8f, 0, -0.35f), low(3.5f, -1.75f, -0.15f, 0.1f, -1.75f, 0.15f, -0.1f),
			low(6, -1.6f, -0.15f, 0.1f, -1.6f, 0.15f, -0.1f), low(11, 0, 0, 0.3f, 0, 0, -0.3f) };
	/** The double-fist hammer (and the combo's last blow): up high on his toes, then the whole body behind it. */
	private static final float[][] HAMMER_PUNCH = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(3, -3.0f, 0.15f, 0.2f, -3.0f, -0.15f, -0.2f, -0.28f, 0, 0.1f, 0, 0.1f, -0.1f, 0, -0.1f, -0.3f),
			QuirkKeyframes.f(5.5f, -1.05f, -0.2f, 0, -1.05f, 0.2f, 0, 0.5f, 0, 0.35f, 0, 0, -0.4f, 0, 0, 0.1f),
			QuirkKeyframes.f(8, -0.95f, -0.2f, 0, -0.95f, 0.2f, 0, 0.45f, 0, 0.32f, 0, 0, -0.36f, 0, 0, 0.1f), QuirkKeyframes.rest(14) };
	private static final float[][] LOW_HAMMER = { low(0, 0, 0, 0.3f, 0, 0, -0.3f), low(3, -1.9f, 0, 0.35f, -1.9f, 0, -0.35f), low(5.5f, -0.9f, 0, 0.2f, -0.9f, 0, -0.2f),
			low(14, 0, 0, 0.3f, 0, 0, -0.3f) };
	private static final float[][] LOW_FINISH = { low(0, 0, 0, 0.3f, 0, 0, -0.3f), low(3, -2.7f, 0.1f, 0.3f, -2.7f, -0.1f, -0.3f), low(5.5f, -1.15f, 0, 0.15f, -1.15f, 0, -0.15f),
			low(8, -1.0f, 0, 0.15f, -1.0f, 0, -0.15f), low(14, 0, 0, 0.3f, 0, 0, -0.3f) };
	private static final float[][] HAMMER_SMASH = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(4, -3.0f, 0, 0.3f, -3.0f, 0, -0.3f, -0.35f, 0, 0, 0, 0.2f, 0, 0, -0.2f, -0.4f),
			QuirkKeyframes.f(6, -0.5f, 0, 0.15f, -0.5f, 0, -0.15f, 0.85f, 0, -0.9f, 0, 0.2f, -0.9f, 0, -0.2f, 0.5f),
			QuirkKeyframes.f(14, -0.4f, 0, 0.15f, -0.4f, 0, -0.15f, 0.75f, 0, -0.8f, 0, 0.2f, -0.8f, 0, -0.2f, 0.4f), QuirkKeyframes.rest(20) };
	private static final float[][] LOW_SMASH = { low(0, 0, 0, 0.3f, 0, 0, -0.3f), low(4, -2.6f, 0, 0.4f, -2.6f, 0, -0.4f), low(6, -0.75f, 0, 0.15f, -0.75f, 0, -0.15f),
			low(14, -0.65f, 0, 0.15f, -0.65f, 0, -0.15f), low(20, 0, 0, 0.3f, 0, 0, -0.3f) };
	/** Grab: both hands out along the aim, then the catch hoisted up over his head. */
	private static final float[][] GRAB = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2.5f, A, A, 0, A, A, 0, 0.22f, 0, 0.2f, 0, 0, -0.2f, 0, 0, 0.1f),
			QuirkKeyframes.f(5, A, A, 0, A, A, 0, 0.18f, 0, 0.18f, 0, 0, -0.18f, 0, 0, 0.05f),
			QuirkKeyframes.f(10, -2.9f, 0, 0.25f, -2.9f, 0, -0.25f, -0.12f, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.2f),
			QuirkKeyframes.f(13, -2.9f, 0, 0.25f, -2.9f, 0, -0.25f, -0.12f, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.2f) };
	private static final float[][] LOW_GRAB = { low(0, 0, 0, 0.3f, 0, 0, -0.3f), low(2.5f, -1.5f, 0, 0.2f, -1.5f, 0, -0.2f), low(5, -1.5f, 0, 0.2f, -1.5f, 0, -0.2f),
			low(10, -2.5f, 0, 0.5f, -2.5f, 0, -0.5f), low(13, -2.5f, 0, 0.5f, -2.5f, 0, -0.5f) };
	private static final float[][] THROW = { QuirkKeyframes.f(0, -2.9f, 0, 0.25f, -2.9f, 0, -0.25f, -0.12f, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.2f),
			QuirkKeyframes.f(4, -1.0f, 0, 0, -1.0f, 0, 0, 0.45f, 0, 0.42f, 0, 0, -0.34f, 0, 0, 0.2f),
			QuirkKeyframes.f(7, -0.8f, 0, 0, -0.8f, 0, 0, 0.35f, 0, 0.36f, 0, 0, -0.3f, 0, 0, 0.1f), QuirkKeyframes.rest(13) };
	private static final float[][] LOW_THROW = { low(0, -2.5f, 0, 0.5f, -2.5f, 0, -0.5f), low(4, -0.9f, 0, 0.2f, -0.9f, 0, -0.2f), low(13, 0, 0, 0.3f, 0, 0, -0.3f) };
	/** Boulder Rip: bent over, fingers into the ground; the chunk hauled up overhead; hurled on the windup's last tick. */
	private static final float[][] BOULDER = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(2.5f, -1.0f, 0, 0.3f, -1.0f, 0, -0.3f, 0.75f, 0, -0.6f, 0, 0.15f, -0.6f, 0, -0.15f, 0.3f),
			QuirkKeyframes.f(FourArmsKit.BOULDER_WINDUP - 1, -2.95f, 0, 0.3f, -2.95f, 0, -0.3f, -0.25f, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.3f),
			QuirkKeyframes.f(FourArmsKit.BOULDER_WINDUP, -2.95f, 0, 0.3f, -2.95f, 0, -0.3f, -0.28f, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.3f),
			QuirkKeyframes.f(FourArmsKit.BOULDER_WINDUP + 3, -1.0f, 0, 0, -1.0f, 0, 0, 0.45f, 0, 0.4f, 0, 0, -0.3f, 0, 0, 0.2f),
			QuirkKeyframes.rest(FourArmsKit.BOULDER_WINDUP + 10) };
	private static final float[][] LOW_BOULDER = { low(0, 0, 0, 0.3f, 0, 0, -0.3f), low(2.5f, -1.0f, 0, 0.2f, -1.0f, 0, -0.2f), low(FourArmsKit.BOULDER_WINDUP - 1, -2.4f, 0, 0.5f, -2.4f, 0, -0.5f),
			low(FourArmsKit.BOULDER_WINDUP, -2.4f, 0, 0.5f, -2.4f, 0, -0.5f), low(FourArmsKit.BOULDER_WINDUP + 3, -0.9f, 0, 0.2f, -0.9f, 0, -0.2f),
			low(FourArmsKit.BOULDER_WINDUP + 10, 0, 0, 0.3f, 0, 0, -0.3f) };
	/** The leap: down into a squat with all four arms swung back, then flung up, reaching. */
	private static final float[][] LEAP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(1.5f, 0.6f, 0, 0.35f, 0.6f, 0, -0.35f, 0.3f, 0, -0.5f, 0, 0, -0.5f, 0, 0, 0.1f),
			QuirkKeyframes.f(4, -2.8f, 0, 0.4f, -2.8f, 0, -0.4f, -0.2f, 0, 0.3f, 0, 0, -0.5f, 0, 0, -0.4f),
			QuirkKeyframes.f(16, -2.6f, 0, 0.5f, -2.6f, 0, -0.5f, -0.1f, 0, -0.5f, 0, 0, 0.4f, 0, 0, -0.3f), QuirkKeyframes.rest(24) };
	private static final float[][] LOW_LEAP = { low(0, 0, 0, 0.3f, 0, 0, -0.3f), low(1.5f, 0.8f, 0, 0.4f, 0.8f, 0, -0.4f), low(4, -2.2f, 0, 0.5f, -2.2f, 0, -0.5f), low(16, -2.0f, 0, 0.6f, -2.0f, 0, -0.6f),
			low(24, 0, 0, 0.3f, 0, 0, -0.3f) };
	/** Meteor Slam's take-off: fists to the sky, legs tucked. */
	private static final float[][] METEOR_RISE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -3.0f, 0, 0.15f, -3.0f, 0, -0.15f, -0.25f, 0, -0.9f, 0, 0, -0.4f, 0, 0, -0.5f),
			QuirkKeyframes.f(26, -3.0f, 0, 0.2f, -3.0f, 0, -0.2f, -0.2f, 0, -0.9f, 0, 0, -0.4f, 0, 0, -0.5f), QuirkKeyframes.rest(30) };
	private static final float[][] LOW_METEOR_RISE = { low(0, 0, 0, 0.3f, 0, 0, -0.3f), low(3, -2.7f, 0, 0.4f, -2.7f, 0, -0.4f), low(26, -2.7f, 0, 0.45f, -2.7f, 0, -0.45f),
			low(30, 0, 0, 0.3f, 0, 0, -0.3f) };
	/** Tetramand Charge: head down, shoulder first, the lower fists pumping (the legs run in pose()). */
	private static final float[][] CHARGE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.6f, 0, 0.2f, -0.9f, 0.4f, 0, 0.6f, 0.5f, 0.4f, 0, 0, -0.3f, 0, 0, -0.4f),
			QuirkKeyframes.f(18, 0.6f, 0, 0.2f, -0.9f, 0.4f, 0, 0.6f, 0.5f, -0.4f, 0, 0, 0.4f, 0, 0, -0.4f), QuirkKeyframes.rest(22) };
	private static final float[][] LOW_CHARGE = { low(0, 0, 0, 0.3f, 0, 0, -0.3f), low(2, 0.5f, 0, 0.3f, -1.0f, 0.3f, -0.1f), low(18, 0.5f, 0, 0.3f, -1.0f, 0.3f, -0.1f), low(22, 0, 0, 0.3f, 0, 0, -0.3f) };
	/** Haymaker: thrown forward with both upper fists cocked back behind him... */
	private static final float[][] HAYMAKER = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.7f, 0, 0.45f, 0.7f, 0, -0.45f, 0.42f, 0, 0.5f, 0, 0, -0.6f, 0, 0, -0.3f),
			QuirkKeyframes.f(FourArmsKit.HAYMAKER_TICKS + 3, 0.8f, 0, 0.45f, 0.8f, 0, -0.45f, 0.46f, 0, 0.5f, 0, 0, -0.6f, 0, 0, -0.3f) };
	/** ...and both slammed down the aim. */
	private static final float[][] HAYMAKER_HIT = { QuirkKeyframes.f(0, 0.8f, 0, 0.45f, 0.8f, 0, -0.45f, 0.46f, 0, 0.5f, 0, 0, -0.6f, 0, 0, -0.3f),
			QuirkKeyframes.f(2.5f, A, A, 0, A, A, 0, 0.36f, 0, 0.42f, 0, 0, -0.46f, 0, 0, 0f), QuirkKeyframes.f(6, A, A, 0, A, A, 0, 0.3f, 0, 0.38f, 0, 0, -0.42f, 0, 0, 0f),
			QuirkKeyframes.rest(13) };
	private static final float[][] LOW_HAYMAKER = { low(0, 0, 0, 0.3f, 0, 0, -0.3f), low(3, 0.9f, 0, 0.4f, 0.9f, 0, -0.4f), low(FourArmsKit.HAYMAKER_TICKS + 3, 0.95f, 0, 0.4f, 0.95f, 0, -0.4f) };
	private static final float[][] LOW_HAYMAKER_HIT = { low(0, 0.95f, 0, 0.4f, 0.95f, 0, -0.4f), low(2.5f, -1.3f, -0.1f, 0.15f, -1.3f, 0.1f, -0.15f), low(6, -1.2f, -0.1f, 0.15f, -1.2f, 0.1f, -0.15f),
			low(13, 0, 0, 0.3f, 0, 0, -0.3f) };
	/** Thunder Clap: all four arms thrown wide, then slammed together. */
	private static final float[][] CLAP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -1.4f, 1.0f, 0.2f, -1.4f, -1.0f, -0.2f, -0.2f, 0, 0, 0, 0.15f, 0, 0, -0.15f, -0.15f),
			QuirkKeyframes.f(6, -1.5f, -0.3f, 0, -1.5f, 0.3f, 0, 0.3f, 0, 0.3f, 0, 0, -0.3f, 0, 0, 0.15f),
			QuirkKeyframes.f(12, -1.45f, -0.25f, 0, -1.45f, 0.25f, 0, 0.25f, 0, 0.25f, 0, 0, -0.25f, 0, 0, 0.1f), QuirkKeyframes.rest(16) };
	private static final float[][] LOW_CLAP = { low(0, 0, 0, 0.3f, 0, 0, -0.3f), low(4, -1.1f, 0.9f, 0.5f, -1.1f, -0.9f, -0.5f), low(6, -1.35f, -0.35f, 0.1f, -1.35f, 0.35f, -0.1f),
			low(12, -1.25f, -0.3f, 0.1f, -1.25f, 0.3f, -0.1f), low(16, 0, 0, 0.3f, 0, 0, -0.3f) };
	private static final float[][] DIVE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -3.0f, 0, 0.15f, -3.0f, 0, -0.15f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.6f),
			QuirkKeyframes.f(30, -3.0f, 0, 0.15f, -3.0f, 0, -0.15f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.6f), QuirkKeyframes.rest(34) };
	private static final float[][] LOW_DIVE = { low(0, 0, 0, 0.3f, 0, 0, -0.3f), low(3, -2.8f, 0, 0.2f, -2.8f, 0, -0.2f), low(30, -2.8f, 0, 0.2f, -2.8f, 0, -0.2f), low(34, 0, 0, 0.3f, 0, 0, -0.3f) };
	/** Four-Arm Guard: the upper arms crossed in front of the face, the lower pair crossed under them, braced low. */
	private static final float[][] GUARD = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.7f, -0.9f, 0, -1.7f, 0.9f, 0, 0.25f, 0, -0.3f, 0, 0.35f, -0.3f, 0, -0.35f, 0.2f),
			QuirkKeyframes.f(60, -1.7f, -0.9f, 0, -1.7f, 0.9f, 0, 0.25f, 0, -0.3f, 0, 0.35f, -0.3f, 0, -0.35f, 0.2f), QuirkKeyframes.rest(64) };
	private static final float[][] LOW_GUARD = { low(0, 0, 0, 0.3f, 0, 0, -0.3f), low(3, -1.35f, -0.75f, 0.15f, -1.35f, 0.75f, -0.15f), low(60, -1.35f, -0.75f, 0.15f, -1.35f, 0.75f, -0.15f),
			low(64, 0, 0, 0.3f, 0, 0, -0.3f) };
	/** Rampage: gathered in, fists to his chest, then thrown open in a chest-out roar. */
	private static final float[][] RAMPAGE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.2f, -0.7f, 0, -1.2f, 0.7f, 0, 0.32f, 0, -0.2f, 0, 0.12f, -0.2f, 0, -0.12f, 0.3f),
			QuirkKeyframes.f(7, -0.55f, 0, 1.35f, -0.55f, 0, -1.35f, -0.35f, 0, 0, 0, 0.22f, 0, 0, -0.22f, -0.75f),
			QuirkKeyframes.f(22, -0.6f, 0, 1.4f, -0.6f, 0, -1.4f, -0.3f, 0, 0, 0, 0.2f, 0, 0, -0.2f, -0.7f), QuirkKeyframes.rest(28) };
	private static final float[][] LOW_RAMPAGE = { low(0, 0, 0, 0.3f, 0, 0, -0.3f), low(3, -1.0f, -0.5f, 0.1f, -1.0f, 0.5f, -0.1f), low(7, 0.45f, 0, 1.05f, 0.45f, 0, -1.05f), low(22, 0.5f, 0, 1.1f, 0.5f, 0, -1.1f),
			low(28, 0, 0, 0.3f, 0, 0, -0.3f) };
	/** Bear Hug: arms wrapped round in front, squeezing (a slow throb in pose()). */
	private static final float[][] HUG = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.35f, -0.75f, 0, -1.35f, 0.75f, 0, 0.1f, 0, 0, 0, 0.1f, 0, 0, -0.1f, 0.1f),
			QuirkKeyframes.f(60, -1.35f, -0.75f, 0, -1.35f, 0.75f, 0, 0.1f, 0, 0, 0, 0.1f, 0, 0, -0.1f, 0.1f), QuirkKeyframes.rest(64) };
	private static final float[][] LOW_HUG = { low(0, 0, 0, 0.3f, 0, 0, -0.3f), low(3, -1.3f, -0.6f, 0.05f, -1.3f, 0.6f, -0.05f), low(60, -1.3f, -0.6f, 0.05f, -1.3f, 0.6f, -0.05f), low(64, 0, 0, 0.3f, 0, 0, -0.3f) };
	/** ...then the whole hug driven down into the ground in front of him. */
	private static final float[][] HUG_SLAM = { QuirkKeyframes.f(0, -1.35f, -0.75f, 0, -1.35f, 0.75f, 0, 0.1f, 0, 0, 0, 0.1f, 0, 0, -0.1f, 0.1f),
			QuirkKeyframes.f(3, -0.6f, -0.2f, 0.1f, -0.6f, 0.2f, -0.1f, 0.8f, 0, -0.8f, 0, 0.2f, -0.8f, 0, -0.2f, 0.5f),
			QuirkKeyframes.f(8, -0.55f, -0.2f, 0.1f, -0.55f, 0.2f, -0.1f, 0.72f, 0, -0.72f, 0, 0.2f, -0.72f, 0, -0.2f, 0.42f), QuirkKeyframes.rest(15) };
	private static final float[][] LOW_HUG_SLAM = { low(0, -1.3f, -0.6f, 0.05f, -1.3f, 0.6f, -0.05f), low(3, -0.65f, -0.1f, 0.15f, -0.65f, 0.1f, -0.15f), low(8, -0.6f, -0.1f, 0.15f, -0.6f, 0.1f, -0.15f),
			low(15, 0, 0, 0.3f, 0, 0, -0.3f) };

	/** One frame of a lower-pair table: {tick, right xyz, left xyz} (absolute, in the body's space; rest is 0, 0, +-0.3). */
	private static float[] low(float tick, float rx, float ry, float rz, float lx, float ly, float lz) {
		return new float[] { tick, rx, ry, rz, lx, ly, lz };
	}

	private static final FirstPersonClip FP_HAMMER = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -78, 10, 0, 0.08f, 0.46f, 0.2f),
			FirstPersonClip.f(5.5f, 8, 22, 0, 0.16f, 0.02f, -0.28f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(1.2f, 10, -10, 0, -0.04f, -0.05f, 0.12f),
			FirstPersonClip.f(3, -30, 28, 0, 0.34f, 0.34f, -0.46f), FirstPersonClip.f(5, -28, 26, 0, 0.32f, 0.32f, -0.42f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0));
	/** Both hands dip down and punch in low. */
	private static final FirstPersonClip FP_COMBO_LOW = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(1.5f, 30, 0, 0, 0, -0.22f, 0.12f),
			FirstPersonClip.f(3.5f, -14, 24, 0, 0.18f, -0.02f, -0.34f), FirstPersonClip.f(6, -12, 22, 0, 0.16f, -0.02f, -0.3f), FirstPersonClip.f(11, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_GRAB = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2.5f, -30, 28, 0, 0.34f, 0.34f, -0.44f),
			FirstPersonClip.f(5, -28, 26, 0, 0.32f, 0.32f, -0.42f), FirstPersonClip.f(10, -78, 10, 0, 0.08f, 0.46f, 0.2f), FirstPersonClip.f(13, -78, 10, 0, 0.08f, 0.46f, 0.2f)).both();
	private static final FirstPersonClip FP_CLAP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -26, -30, 0, -0.2f, 0.22f, -0.05f),
			FirstPersonClip.f(6, -28, 38, 0, 0.32f, 0.28f, -0.24f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_POUND = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -78, 10, 0, 0.08f, 0.46f, 0.2f),
			FirstPersonClip.f(7, 35, 10, 0, 0.05f, -0.3f, -0.1f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_THROW = FirstPersonClip.of(FirstPersonClip.f(0, -78, 10, 0, 0.08f, 0.46f, 0.2f), FirstPersonClip.f(4, -14, 24, 0, 0.14f, 0.14f, -0.3f),
			FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_LEAP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(1.5f, 30, 0, 0, 0, -0.3f, 0.1f),
			FirstPersonClip.f(4, -78, 10, 0, 0.08f, 0.46f, 0.2f), FirstPersonClip.f(16, -72, 8, 4, 0.08f, 0.42f, 0.18f), FirstPersonClip.f(24, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_BOULDER = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2.5f, 40, 10, 0, 0.05f, -0.4f, -0.1f),
			FirstPersonClip.f(FourArmsKit.BOULDER_WINDUP, -78, 10, 0, 0.08f, 0.46f, 0.2f), FirstPersonClip.f(FourArmsKit.BOULDER_WINDUP + 3, -14, 24, 0, 0.14f, 0.14f, -0.3f),
			FirstPersonClip.f(FourArmsKit.BOULDER_WINDUP + 10, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SWING = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -30, 32, -14, 0.26f, 0.22f, -0.2f),
			FirstPersonClip.f(30, -30, 32, -14, 0.26f, 0.22f, -0.2f), FirstPersonClip.f(34, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_GUARD = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -32, 44, 0, 0.28f, 0.28f, 0),
			FirstPersonClip.f(60, -32, 44, 0, 0.28f, 0.28f, 0), FirstPersonClip.f(64, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_CHARGE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -28, 42, 0, 0.26f, 0.2f, 0),
			FirstPersonClip.f(18, -28, 42, 0, 0.26f, 0.2f, 0), FirstPersonClip.f(22, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_HAYMAKER = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 30, -12, 0, -0.1f, -0.12f, 0.25f),
			FirstPersonClip.f(FourArmsKit.HAYMAKER_TICKS + 3, 32, -12, 0, -0.1f, -0.14f, 0.27f)).both();
	private static final FirstPersonClip FP_HAYMAKER_HIT = FirstPersonClip.of(FirstPersonClip.f(0, 32, -12, 0, -0.1f, -0.14f, 0.27f), FirstPersonClip.f(2.5f, -30, 28, 0, 0.34f, 0.34f, -0.48f),
			FirstPersonClip.f(6, -28, 26, 0, 0.32f, 0.32f, -0.44f), FirstPersonClip.f(13, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_HUG = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -24, 50, 0, 0.3f, 0.22f, -0.05f),
			FirstPersonClip.f(60, -24, 55, 0, 0.32f, 0.22f, -0.05f), FirstPersonClip.f(64, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_ROAR = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -15, 40, 0, 0.22f, 0.02f, 0.05f),
			FirstPersonClip.f(7, -20, -40, 30, -0.25f, 0.2f, 0), FirstPersonClip.f(22, -20, -40, 30, -0.25f, 0.2f, 0), FirstPersonClip.f(28, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_RISE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -78, 10, 0, 0.08f, 0.46f, 0.2f),
			FirstPersonClip.f(26, -78, 10, 0, 0.08f, 0.46f, 0.2f), FirstPersonClip.f(30, 0, 0, 0, 0, 0, 0)).both();

	public FourArmsClient() {
		ModelPart root = AlienParts.bake(64, 64, r -> {
			// the second pair: the skin's own arm UVs (right arm at 40,16, left at 32,48) and their sleeve layer (40,32 / 48,48),
			// shoulder pivots under the first pair's
			r.addOrReplaceChild("lower_right", CubeListBuilder.create().texOffs(40, 16).addBox(-3, -2, -2, 4, 11, 4, new net.minecraft.client.model.geom.builders.CubeDeformation(-0.25f))
						.texOffs(40, 32).addBox(-3, -2, -2, 4, 11, 4, new net.minecraft.client.model.geom.builders.CubeDeformation(0f)),
					PartPose.ZERO);
			r.addOrReplaceChild("lower_left", CubeListBuilder.create().texOffs(32, 48).addBox(-1, -2, -2, 4, 11, 4, new net.minecraft.client.model.geom.builders.CubeDeformation(-0.25f))
						.texOffs(48, 48).addBox(-1, -2, -2, 4, 11, 4, new net.minecraft.client.model.geom.builders.CubeDeformation(0f)),
					PartPose.ZERO);
		});
		lowerRight = root.getChild("lower_right");
		lowerLeft = root.getChild("lower_left");
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	private static float[][] upperClip(int animId) {
		return switch (animId) {
			case FourArmsKit.ANIM_PUNCH_R -> PUNCH_R;
			case FourArmsKit.ANIM_PUNCH_L -> PUNCH_L;
			case FourArmsKit.ANIM_PUNCH_LOW_R -> PUNCH_LOW_R;
			case FourArmsKit.ANIM_PUNCH_LOW_L -> PUNCH_LOW_L;
			case FourArmsKit.ANIM_COMBO_LOW -> COMBO_LOW;
			case FourArmsKit.ANIM_HAMMER_PUNCH, FourArmsKit.ANIM_COMBO_FINISH -> HAMMER_PUNCH;
			case FourArmsKit.ANIM_HAMMER_SMASH -> HAMMER_SMASH;
			case FourArmsKit.ANIM_GRAB -> GRAB;
			case FourArmsKit.ANIM_THROW -> THROW;
			case FourArmsKit.ANIM_BOULDER -> BOULDER;
			case FourArmsKit.ANIM_LEAP -> LEAP;
			case FourArmsKit.ANIM_CHARGE -> CHARGE;
			case FourArmsKit.ANIM_HAYMAKER -> HAYMAKER;
			case FourArmsKit.ANIM_HAYMAKER_HIT -> HAYMAKER_HIT;
			case FourArmsKit.ANIM_CLAP -> CLAP;
			case FourArmsKit.ANIM_METEOR_RISE -> METEOR_RISE;
			case FourArmsKit.ANIM_DIVE -> DIVE;
			case FourArmsKit.ANIM_CLIMB -> CLIMB;
			case FourArmsKit.ANIM_GUARD -> GUARD;
			case FourArmsKit.ANIM_RAMPAGE -> RAMPAGE;
			case FourArmsKit.ANIM_HUG -> HUG;
			case FourArmsKit.ANIM_HUG_SLAM -> HUG_SLAM;
			default -> null;
		};
	}

	private static float[][] lowerClip(int animId) {
		return switch (animId) {
			case FourArmsKit.ANIM_PUNCH_R -> LOW_PUNCH_R;
			case FourArmsKit.ANIM_PUNCH_L -> LOW_PUNCH_L;
			case FourArmsKit.ANIM_PUNCH_LOW_R -> LOW_LOW_R;
			case FourArmsKit.ANIM_PUNCH_LOW_L -> LOW_LOW_L;
			case FourArmsKit.ANIM_COMBO_LOW -> LOW_COMBO_LOW;
			case FourArmsKit.ANIM_HAMMER_PUNCH -> LOW_HAMMER;
			case FourArmsKit.ANIM_COMBO_FINISH -> LOW_FINISH;
			case FourArmsKit.ANIM_HAMMER_SMASH -> LOW_SMASH;
			case FourArmsKit.ANIM_GRAB -> LOW_GRAB;
			case FourArmsKit.ANIM_THROW -> LOW_THROW;
			case FourArmsKit.ANIM_BOULDER -> LOW_BOULDER;
			case FourArmsKit.ANIM_LEAP -> LOW_LEAP;
			case FourArmsKit.ANIM_CHARGE -> LOW_CHARGE;
			case FourArmsKit.ANIM_HAYMAKER -> LOW_HAYMAKER;
			case FourArmsKit.ANIM_HAYMAKER_HIT -> LOW_HAYMAKER_HIT;
			case FourArmsKit.ANIM_CLAP -> LOW_CLAP;
			case FourArmsKit.ANIM_METEOR_RISE -> LOW_METEOR_RISE;
			case FourArmsKit.ANIM_DIVE -> LOW_DIVE;
			case FourArmsKit.ANIM_GUARD -> LOW_GUARD;
			case FourArmsKit.ANIM_RAMPAGE -> LOW_RAMPAGE;
			case FourArmsKit.ANIM_HUG -> LOW_HUG;
			case FourArmsKit.ANIM_HUG_SLAM -> LOW_HUG_SLAM;
			default -> null;
		};
	}

	/** A clip whose first frame is the end of the one before it: no fade in from the walk cycle, only the fade out. */
	private static boolean chained(int animId) {
		return animId == FourArmsKit.ANIM_THROW || animId == FourArmsKit.ANIM_HAYMAKER_HIT || animId == FourArmsKit.ANIM_HUG_SLAM;
	}

	/** The arm-work moves: on the move the legs stay on the walk cycle. */
	private static boolean armsOnly(int animId) {
		return switch (animId) {
			case FourArmsKit.ANIM_PUNCH_R, FourArmsKit.ANIM_PUNCH_L, FourArmsKit.ANIM_PUNCH_LOW_R, FourArmsKit.ANIM_PUNCH_LOW_L, FourArmsKit.ANIM_COMBO_LOW, FourArmsKit.ANIM_HAMMER_PUNCH,
					FourArmsKit.ANIM_COMBO_FINISH, FourArmsKit.ANIM_CLAP, FourArmsKit.ANIM_GRAB, FourArmsKit.ANIM_THROW, FourArmsKit.ANIM_RAMPAGE -> true;
			default -> false;
		};
	}

	/** The fade weight of {@code clip} at {@code t}. */
	private static float weight(float[][] clip, float t, int animId) {
		float len = QuirkKeyframes.length(clip);
		return chained(animId) ? Mth.clamp((len - t) / QuirkKeyframes.FADE_OUT, 0f, 1f) : QuirkKeyframes.weight(t, len);
	}

	/** A held state's lower-arm pose {right xyz, left xyz} (what they settle to between the moves), or null for the walk. */
	private static float[] heldLower(AbstractClientPlayer p, float partial) {
		int f = Omnitrix.state(p).kitFlags;
		if ((f & FourArmsKit.F_HUGGING) != 0) {
			return new float[] { -1.3f, -0.6f, 0.05f, -1.3f, 0.6f, -0.05f }; // the lower pair locks round the waist
		}
		if ((f & FourArmsKit.F_SWINGING) != 0) {
			return new float[] { -1.5f, -0.25f, 0.1f, -1.5f, 0.25f, -0.1f }; // all four hands on the victim
		}
		if ((f & FourArmsKit.F_HOLDING) != 0) {
			return new float[] { -2.5f, 0f, 0.5f, -2.5f, 0f, -0.5f }; // up under the load with the others
		}
		if (climbing(p)) {
			float c = Mth.sin((p.tickCount + partial) * 0.6f);
			return new float[] { -2.2f - c * 0.6f, 0f, 0.3f, -2.2f + c * 0.6f, 0f, -0.3f }; // the lower pair climbs out of step with the upper
		}
		if ((f & FourArmsKit.F_GUARD) != 0) {
			return new float[] { -1.35f, -0.75f, 0.15f, -1.35f, 0.75f, -0.15f }; // crossed under the upper pair
		}
		if ((f & FourArmsKit.F_LEAP_CHARGE) != 0) {
			return new float[] { -0.75f, 0f, 0.45f, -0.75f, 0f, -0.45f }; // all four fists braced on the ground
		}
		if ((f & FourArmsKit.F_RAMPAGE) != 0) {
			float c = Mth.sin((p.tickCount + partial) * 0.4f) * 0.08f;
			return new float[] { -0.3f + c, 0f, 0.65f, -0.3f - c, 0f, -0.65f }; // fists clenched, flexed out
		}
		return null;
	}

	/** The lower arms' keyframed pose for the move playing {right xyz, left xyz, weight}, or null when none is. */
	private static float[] moveLower(AbstractClientPlayer p, float partial) {
		OmnitrixState s = Omnitrix.state(p);
		float t = p.level().getGameTime() + partial - s.animStart;
		float[][] clip = lowerClip(s.animId);
		if (clip == null || (s.kitFlags & FourArmsKit.F_SWINGING) != 0) {
			return null;
		}
		float[] v = QuirkKeyframes.sampleN(clip, t, 7);
		if (v == null) {
			return null;
		}
		return new float[] { v[1], v[2], v[3], v[4], v[5], v[6], weight(clip, t, s.animId) };
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN));
		// v0.17.1 (user: "make four arms extra arms not look so weird ... I dont mind if the arms overlap a bit"): the lower pair
		// hangs from the ribs right under the upper pair, nearly parallel to them (a slight flare, not splayed out), a touch behind
		// so the two pairs layer instead of crossing; they swing with the walk half a beat behind the upper arms
		float lag = Mth.sin(p.walkAnimation.position(partial) * 0.6662f - 0.9f) * 0.9f * p.walkAnimation.speed(partial);
		float[] base = heldLower(p, partial);
		if (base == null) {
			base = new float[] { lag * 0.85f + model.rightArm.xRot * 0.15f, model.rightArm.yRot * 0.4f, 0.3f + model.rightArm.zRot * 0.35f, -lag * 0.85f + model.leftArm.xRot * 0.15f,
					model.leftArm.yRot * 0.4f, -0.3f + model.leftArm.zRot * 0.35f };
		}
		float[] move = moveLower(p, partial);
		float mw = move == null ? 0f : move[6];
		lowerRight.x = -5.4f;
		lowerRight.y = 5.4f;
		lowerRight.z = 1.0f;
		lowerRight.xRot = move == null ? base[0] : Mth.lerp(mw, base[0], move[0]);
		lowerRight.yRot = move == null ? base[1] : Mth.lerp(mw, base[1], move[1]);
		lowerRight.zRot = move == null ? base[2] : Mth.lerp(mw, base[2], move[2]);
		lowerLeft.x = 5.4f;
		lowerLeft.y = 5.4f;
		lowerLeft.z = 1.0f;
		lowerLeft.xRot = move == null ? base[3] : Mth.lerp(mw, base[3], move[3]);
		lowerLeft.yRot = move == null ? base[4] : Mth.lerp(mw, base[4], move[4]);
		lowerLeft.zRot = move == null ? base[5] : Mth.lerp(mw, base[5], move[5]);
		// v0.17.2: the long transformation -- the second pair splits out of the first (from inside the upper arms, sliding down to the
		// ribs) and takes the beats' poses
		float[] seq = com.projecthero.mod.client.omnitrix.OmnitrixSequenceClient.lowerArms(p, model, partial);
		if (seq != null) {
			lowerRight.x = seq[0];
			lowerRight.y = seq[1];
			lowerRight.z = seq[2];
			lowerRight.xRot = seq[3];
			lowerRight.yRot = seq[4];
			lowerRight.zRot = seq[5];
			lowerLeft.x = -seq[0];
			lowerLeft.y = Mth.lerp(seq[6], model.leftArm.y, 5.4f);
			lowerLeft.z = Mth.lerp(seq[6], model.leftArm.z, 1.0f);
			lowerLeft.xRot = Mth.lerp(seq[6], model.leftArm.xRot, seq[3]);
			lowerLeft.yRot = -seq[4];
			lowerLeft.zRot = Mth.lerp(seq[6], model.leftArm.zRot, -seq[5]);
		}
		AlienParts.on(pose, model.body, () -> {
			// the body bone's pivot is the neck; the lower arms hang from the ribs
			lowerRight.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
			lowerLeft.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
			if (seq != null) {
				com.projecthero.mod.client.omnitrix.OmnitrixSequenceClient.renderSplitGlow(pose, buffers, p, new ModelPart[] { lowerRight, lowerLeft }, partial);
			}
		});
		// the Boulder Rip's chunk of ground, held in all four hands on its way up
		if (com.projecthero.mod.omnitrix.alien.Aliens.kit(FourArmsKit.ID) instanceof FourArmsKit k) {
			net.minecraft.world.level.block.state.BlockState rock = k.boulderHeld(p);
			if (rock != null) {
				org.joml.Vector3f r = new org.joml.Quaternionf().rotationZYX(model.rightArm.zRot, model.rightArm.yRot, model.rightArm.xRot).transform(new org.joml.Vector3f(1f, 12f, 0f));
				org.joml.Vector3f l = new org.joml.Quaternionf().rotationZYX(model.leftArm.zRot, model.leftArm.yRot, model.leftArm.xRot).transform(new org.joml.Vector3f(-1f, 12f, 0f));
				pose.pushPose();
				pose.translate((model.rightArm.x + r.x + model.leftArm.x + l.x) / 32f, (model.rightArm.y + r.y + model.leftArm.y + l.y) / 32f - 0.25f, (model.rightArm.z + r.z + model.leftArm.z + l.z) / 32f);
				com.projecthero.mod.client.mutation.geo.GeoModels.centred(pose, buffers, light, rock, 0.85f, 0.75f, 0.85f);
				pose.popPose();
			}
		}
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		// the lower arm of the same side, just under and behind the upper one
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN));
		ModelPart lower = right ? lowerRight : lowerLeft;
		lower.x = right ? -1.0f : 1.0f;
		lower.y = 2.5f;
		lower.z = 3.2f;
		lower.xRot = 0.1f;
		lower.yRot = 0f;
		lower.zRot = right ? 0.12f : -0.12f;
		float[] move = moveLower(p, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
		if (move != null) {
			lower.xRot += (right ? move[0] : move[3]) * 0.25f * move[6];
		}
		lower.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
	}

	/** Every column but the legs'. */
	private static final int NO_LEGS;

	static {
		int mask = QuirkKeyframes.bit(QuirkKeyframes.HEAD_X);
		for (int c = QuirkKeyframes.R_ARM_X; c <= QuirkKeyframes.BODY_Y; c++) {
			mask |= QuirkKeyframes.bit(c);
		}
		NO_LEGS = mask;
	}

	/** Plays a table, its {@link QuirkKeyframes#AIM} columns pointing down the look. */
	private static boolean play(AbstractClientPlayer p, HumanoidModel<?> m, float[][] clip, int animId, float t) {
		float[] f = QuirkKeyframes.sample(clip, t, -Mth.HALF_PI + m.head.xRot, m.head.yRot);
		if (f == null) {
			return false;
		}
		float w = weight(clip, t, animId);
		if (armsOnly(animId) && p.walkAnimation.speed() > 0.08f) {
			QuirkKeyframes.applyMasked(m, f, w, NO_LEGS);
		} else {
			QuirkKeyframes.apply(m, f, w);
		}
		return true;
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		if (chargePose(p, m)) {
			return true;
		}
		int flags = Omnitrix.state(p).kitFlags;
		if ((flags & FourArmsKit.F_SWINGING) != 0) {
			// v0.17.2: the Giant Swing -- leaning back against the weight on wide-planted feet, all four hands on the victim at arm's length;
			// the body itself turns with it (tick())
			float in = Mth.clamp(ticks / 4f, 0f, 1f);
			m.rightArm.xRot = Mth.lerp(in, m.rightArm.xRot, -1.5f);
			m.leftArm.xRot = Mth.lerp(in, m.leftArm.xRot, -1.5f);
			m.rightArm.yRot = -0.32f;
			m.leftArm.yRot = 0.32f;
			m.rightArm.zRot = 0f;
			m.leftArm.zRot = 0f;
			com.projecthero.mod.client.HipBend.pitch(m, in, -0.2f);
			m.rightLeg.zRot = 0.22f;
			m.leftLeg.zRot = -0.22f;
			m.rightLeg.xRot = 0.12f;
			m.leftLeg.xRot = -0.12f;
			m.head.xRot = 0.1f;
			m.head.yRot = 0f;
			m.hat.copyFrom(m.head);
			return true;
		}
		float[][] clip = upperClip(animId);
		boolean played = clip != null && play(p, m, clip, animId, ticks);
		if (played && animId == FourArmsKit.ANIM_CHARGE && ticks > 2f && ticks < FourArmsKit.CHARGE_TICKS) {
			// the legs pounding under him
			float s = Mth.sin(ticks * 1.5f) * 0.85f;
			m.rightLeg.xRot = s;
			m.leftLeg.xRot = -s;
		}
		if ((flags & FourArmsKit.F_HUGGING) != 0) {
			// v0.17.2: the Bear Hug's squeeze -- a slow throb of the crossed arms
			float sq = Mth.sin(age * 0.6f) * 0.08f;
			float w = played ? QuirkKeyframes.weight(ticks, QuirkKeyframes.length(HUG)) : 1f;
			m.rightArm.yRot += -sq * w;
			m.leftArm.yRot += sq * w;
			m.body.xRot += sq * 0.25f * w;
			if (!played) {
				m.rightArm.xRot = -1.35f;
				m.leftArm.xRot = -1.35f;
				m.rightArm.yRot = -0.75f - sq;
				m.leftArm.yRot = 0.75f + sq;
				m.body.xRot = 0.1f;
			}
			return true;
		}
		if ((flags & FourArmsKit.F_HOLDING) != 0 && !played) {
			// holding something overhead, feeling its weight
			float h = Mth.sin(age * 0.18f) * 0.03f;
			m.rightArm.xRot = -2.9f + h;
			m.leftArm.xRot = -2.9f + h;
			m.rightArm.zRot = 0.25f;
			m.leftArm.zRot = -0.25f;
			return true;
		}
		if (!played && (flags & FourArmsKit.F_GUARD) != 0) {
			m.rightArm.xRot = -1.7f;
			m.leftArm.xRot = -1.7f;
			m.rightArm.yRot = -0.9f;
			m.leftArm.yRot = 0.9f;
			return true;
		}
		if (!played && climbing(p)) {
			// v0.17.1: Wall Climb -- hand over hand up the wall, the legs pushing
			float c = Mth.sin(age * 0.6f);
			m.rightArm.xRot = -2.6f + c * 0.6f;
			m.leftArm.xRot = -2.6f - c * 0.6f;
			m.rightArm.zRot = 0.15f;
			m.leftArm.zRot = -0.15f;
			m.rightLeg.xRot = -0.5f - c * 0.5f;
			m.leftLeg.xRot = -0.5f + c * 0.5f;
			m.head.xRot = -0.4f;
			return true;
		}
		if (!played) {
			// a heavy walk: the arms hang a little wider -- much wider and hunched in a Rampage
			boolean rage = (flags & FourArmsKit.F_RAMPAGE) != 0;
			m.rightArm.zRot += rage ? 0.3f : 0.08f;
			m.leftArm.zRot -= rage ? 0.3f : 0.08f;
			if (rage) {
				m.body.xRot += 0.15f;
				m.head.xRot -= 0.1f;
			}
			m.head.xRot += Mth.sin(age * 0.05f) * 0.01f;
		}
		return played;
	}

	/** v0.17.1: holding X -- the long leap's crouch, deeper as it charges, all four fists braced on the ground. */
	private static boolean chargePose(AbstractClientPlayer p, HumanoidModel<?> m) {
		if (!(com.projecthero.mod.omnitrix.alien.Aliens.kit(FourArmsKit.ID) instanceof FourArmsKit k) || !k.flag(p, FourArmsKit.F_LEAP_CHARGE)) {
			return false;
		}
		float c = k.leapCharge(p);
		float shake = c > 0.95f ? Mth.sin(p.tickCount * 2.1f) * 0.03f : 0f;
		m.body.xRot = 0.35f + 0.25f * c;
		m.head.xRot = Math.min(m.head.xRot, 0f) - 0.2f;
		m.rightArm.xRot = -0.55f - 0.3f * c + shake;
		m.leftArm.xRot = -0.55f - 0.3f * c - shake;
		m.rightArm.zRot = 0.25f;
		m.leftArm.zRot = -0.25f;
		m.rightLeg.xRot = -0.5f - 0.35f * c;
		m.leftLeg.xRot = -0.5f - 0.35f * c;
		m.rightLeg.zRot = 0.12f;
		m.leftLeg.zRot = -0.12f;
		return true;
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		// v0.17.2 (user: "edit and update animations on all moves"): the Giant Swing turns his whole body after the victim (the same angle the
		// server whirls it through, FourArmsKit.tickHeld), so he is always facing what he is swinging
		if (com.projecthero.mod.omnitrix.alien.Aliens.kit(FourArmsKit.ID) instanceof FourArmsKit sk && sk.flag(p, FourArmsKit.F_SWINGING)) {
			long t = p.level().getGameTime() - sk.timer(p, "swing_start");
			float was = p.yBodyRot;
			float now = p.getYRot() + (float) Math.toDegrees(t * (0.35 + t * 0.012));
			p.yBodyRotO = was;
			p.yBodyRot = now;
			p.yHeadRotO = was;
			p.yHeadRot = now;
		}
		// the long leap flies drag-free on its owner's client (as the Hulk's Super Leap): the launch's horizontal speed held,
		// vanilla's vertical drag taken back off -- until he hits something, lands or swims
		if (p != Minecraft.getInstance().player || !(com.projecthero.mod.omnitrix.alien.Aliens.kit(FourArmsKit.ID) instanceof FourArmsKit k)) {
			return;
		}
		net.minecraft.world.phys.Vec3 v = k.farLeapVelocity(p);
		// v0.17.1 (user: "four arms leap sometimes doesn't launch him in the direction that the player aims"): the take-off tick
		// counts too -- he is still on the ground then, and the ground's friction and the movement keys bent the launch (strafing
		// skewed it); from the first tick the launch's own heading is held
		boolean takeoff = v != null && p.level().getGameTime() - k.timer(p, "air_at") <= 3;
		if (v == null || (p.onGround() && !takeoff) || p.horizontalCollision || p.isInWater() || p.isInLava() || p.getAbilities().flying || p.isPassenger()) {
			return;
		}
		net.minecraft.world.phys.Vec3 d = p.getDeltaMovement();
		p.setDeltaMovement(v.x, d.y / com.projecthero.mod.hulk.HulkLeap.VERTICAL_DRAG, v.z);
	}

	@Override
	public void hud(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.player.LocalPlayer p, int x, int y, int w, float partial) {
		if (!(com.projecthero.mod.omnitrix.alien.Aliens.kit(FourArmsKit.ID) instanceof FourArmsKit k) || !k.flag(p, FourArmsKit.F_LEAP_CHARGE)) {
			return;
		}
		float c = k.leapCharge(p);
		// v0.17.1 (user: "convert everything to blocks"): the distance in blocks, not metres
		String value = net.minecraft.network.chat.Component.translatable("hud.projecthero.omnitrix.blocks", Math.round(FourArmsKit.leapBlocks(c))).getString();
		com.projecthero.mod.client.omnitrix.OmnitrixHud.meter(g, Minecraft.getInstance(), x, y - 6,
				net.minecraft.network.chat.Component.translatable("hud.projecthero.omnitrix.four_arms.leap"), c, c >= 1f ? 0xFFFFE060 : 0xFFE04030, value, 0xFFFFFFFF);
	}

	@Override
	public int hudHeight() {
		return 10;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case FourArmsKit.ANIM_PUNCH_R, FourArmsKit.ANIM_PUNCH_L, FourArmsKit.ANIM_PUNCH_LOW_R, FourArmsKit.ANIM_PUNCH_LOW_L -> FP_PUNCH;
			case FourArmsKit.ANIM_COMBO_LOW -> FP_COMBO_LOW;
			case FourArmsKit.ANIM_GRAB -> FP_GRAB;
			case FourArmsKit.ANIM_HAMMER_PUNCH, FourArmsKit.ANIM_COMBO_FINISH -> FP_HAMMER;
			case FourArmsKit.ANIM_HAMMER_SMASH, FourArmsKit.ANIM_DIVE, FourArmsKit.ANIM_HUG_SLAM -> FP_POUND;
			case FourArmsKit.ANIM_CLAP -> FP_CLAP;
			case FourArmsKit.ANIM_THROW -> FP_THROW;
			case FourArmsKit.ANIM_LEAP -> FP_LEAP;
			case FourArmsKit.ANIM_BOULDER -> FP_BOULDER;
			case FourArmsKit.ANIM_HAYMAKER -> FP_HAYMAKER;
			case FourArmsKit.ANIM_HAYMAKER_HIT -> FP_HAYMAKER_HIT;
			case FourArmsKit.ANIM_SWING -> FP_SWING;
			case FourArmsKit.ANIM_METEOR_RISE -> FP_RISE;
			case FourArmsKit.ANIM_GUARD -> FP_GUARD;
			case FourArmsKit.ANIM_CHARGE -> FP_CHARGE;
			case FourArmsKit.ANIM_HUG -> FP_HUG;
			case FourArmsKit.ANIM_RAMPAGE -> FP_ROAR;
			case FourArmsKit.ANIM_CLIMB -> FP_CLIMB;
			default -> null;
		};
	}

	@Override
	public float[] badge() {
		// v0.17.1 (user: "four arms badge needs to be on the side of his top left arm"): the outer side of his upper left arm, near
		// the shoulder (arm-bone pixels: the cube spans x -1..3, y -2..10, z -2..2; the 1 = facing out of +x)
		return new float[] { 3.3f, 1.2f, 0f, 3.0f, 1f };
	}

	@Override
	public int badgeBone() {
		return BADGE_LEFT_ARM;
	}
}
