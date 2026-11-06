package com.projecthero.mod.client.omnitrix.alien;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.client.omnitrix.alien.greymatter.GreyMatterGadgets;
import com.projecthero.mod.omnitrix.alien.greymatter.GreyMatterKit;
import com.projecthero.mod.omnitrix.alien.greymatter.GreyMatterMechEntity;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1: Grey Matter's client kit -- the tiny grey Galvan. The whole body is a third of a player (the scale attribute does
 * it); on top of that the HEAD is drawn half again as big (the player's head is hidden and redrawn from the skin at x1.55 on
 * the same bone), and the legs splay like a frog's. A pose for every move.
 *
 * <p>v0.17.2 revamp: Inside Job (he shrinks into the creature over 4 ticks and is hidden while inside; the creature shudders and
 * bulges in pulses -- {@code GreyMatterInsideRenderMixin}; he pops out with a spin), Rewire's pointing finger and the flat-to-wall
 * crawl.
 *
 * <p>v0.17.2 round 3 (user: "edit and update animations on all moves please"): every clip below reworked with a wind-up, a hit and a
 * settle, a first-person clip of its own for each move, and the base pose always under the clip so nothing snaps when one ends. The
 * machines are {@link com.projecthero.mod.client.omnitrix.alien.greymatter.GreyMatterGadgets}; in the Block Mech he sits on the seat
 * working two levers, and the levers answer each move.
 */
public final class GreyMatterClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/grey_matter.png");
	public static final float HEAD_SCALE = 1.55f;

	private final ModelPart bigHead;

	// rArm xyz, lArm xyz, bodyX, bodyY, rLeg xyz, lLeg xyz, headX
	/** Nerve Pinch: up on his toes, the arm cocked back, two fingers driven in, a squeeze and a twist, and let go. */
	/**
	 * v0.17.2 (user: "add animations for grey matter building his mechs and sentrys and stuff please, they should take a few seconds"):
	 * clip {@code c} with frames {@code from..to-1} worked through {@code repeats} more times, {@code period} ticks a round.
	 */
	private static float[][] stretch(float[][] c, int from, int to, int period, int repeats) {
		java.util.List<float[]> out = new java.util.ArrayList<>(java.util.Arrays.asList(c).subList(0, to));
		for (int r = 1; r <= repeats; r++) {
			for (int i = from; i < to; i++) {
				float[] f = c[i].clone();
				f[0] += r * period;
				out.add(f);
			}
		}
		for (int i = to; i < c.length; i++) {
			float[] f = c[i].clone();
			f[0] += repeats * period;
			out.add(f);
		}
		return out.toArray(new float[0][]);
	}

	private static final float[][] PINCH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.5f, 0.5f, 0.3f, 0.3f, 0, -0.15f, -0.12f, 0.4f, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(4, -2.15f, -0.2f, 0, 0.4f, 0, -0.2f, 0.25f, -0.35f, -0.25f, 0, 0.1f, 0.25f, 0, -0.1f, -0.3f),
			QuirkKeyframes.f(7, -2.05f, 0.1f, 0.15f, 0.35f, 0, -0.2f, 0.2f, -0.3f, -0.2f, 0, 0.1f, 0.2f, 0, -0.1f, -0.25f),
			QuirkKeyframes.f(10, -1.2f, 0.25f, 0.25f, 0.1f, 0, -0.1f, 0.05f, -0.1f, 0, 0, 0, 0, 0, 0, -0.05f), QuirkKeyframes.rest(14) };
	/** Weak-Spot Strike: both fists overhead, on one leg, then a hammer blow down on the exact spot. */
	private static final float[][] STRIKE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -3.0f, 0.1f, 0.2f, -3.0f, -0.1f, -0.2f, -0.28f, 0, 0.2f, 0, 0.1f, -0.5f, 0, -0.1f, -0.35f),
			QuirkKeyframes.f(4, -1.5f, 0.25f, 0, -1.5f, -0.25f, 0, 0.45f, 0, -0.5f, 0, 0.1f, 0.3f, 0, -0.1f, 0.2f),
			QuirkKeyframes.f(6, -0.65f, 0.25f, -0.05f, -0.65f, -0.25f, 0.05f, 0.65f, 0, -0.55f, 0, 0.1f, 0.35f, 0, -0.1f, 0.35f),
			QuirkKeyframes.f(9, -0.75f, 0.2f, 0, -0.75f, -0.2f, 0, 0.5f, 0, -0.4f, 0, 0.1f, 0.25f, 0, -0.1f, 0.25f), QuirkKeyframes.rest(15) };
	/** Analyze: a finger to the temple, the other hand out at the creature, sizing it up with small nods. */
	private static final float[][] ANALYZE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.4f, -0.85f, 0, -0.9f, 0.2f, -0.15f, -0.05f, 0, 0, 0, 0, 0, 0, 0, 0.1f),
			QuirkKeyframes.f(9, -2.35f, -0.8f, 0, -1.3f, 0.1f, -0.1f, 0, 0.12f, 0, 0, 0, 0, 0, 0, -0.08f),
			QuirkKeyframes.f(15, -2.4f, -0.85f, 0, -1.1f, 0.3f, -0.1f, 0, -0.12f, 0, 0, 0, 0, 0, 0, 0.1f),
			QuirkKeyframes.f(21, -2.35f, -0.8f, 0, -1.4f, 0.05f, -0.1f, 0, 0, 0, 0, 0, 0, 0, 0, -0.08f), QuirkKeyframes.rest(27) };
	/** Galvan Sentry: squats down, hands flying over the parts in turn, tightens the last bolt with a twist, one arm flung up: done. */
	private static final float[][] TURRET = stretch(new float[][] { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.0f, -0.3f, 0, -1.2f, 0.3f, 0, 0.55f, 0, -0.55f, 0, 0.18f, -0.55f, 0, -0.18f, 0.45f),
			QuirkKeyframes.f(6, -0.65f, -0.5f, 0, -1.45f, 0.25f, 0, 0.55f, 0.2f, -0.55f, 0, 0.18f, -0.55f, 0, -0.18f, 0.45f),
			QuirkKeyframes.f(9, -1.45f, -0.2f, 0, -0.7f, 0.5f, 0, 0.55f, -0.2f, -0.55f, 0, 0.18f, -0.55f, 0, -0.18f, 0.45f),
			QuirkKeyframes.f(12, -1.2f, -0.6f, 0.3f, -1.2f, 0.6f, -0.3f, 0.55f, 0, -0.55f, 0, 0.18f, -0.55f, 0, -0.18f, 0.5f),
			QuirkKeyframes.f(15, -2.95f, 0, 0.35f, 0.25f, 0, -0.1f, -0.1f, 0, 0, 0, 0, 0, 0, 0, -0.25f), QuirkKeyframes.f(19, -2.8f, 0, 0.3f, 0.2f, 0, -0.1f, 0, 0, 0, 0, 0, 0, 0, 0, -0.15f),
			QuirkKeyframes.rest(24) }, 1, 4, 9, 3);
	/** Big Leap: squash, burst off both feet with the arms thrown back, tuck like a frog in the air, reach for the landing. */
	private static final float[][] LEAP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(1, 0.5f, 0, 0.4f, 0.5f, 0, -0.4f, 0.6f, 0, -0.8f, 0, 0.3f, -0.8f, 0, -0.3f, -0.2f),
			QuirkKeyframes.f(3, 1.1f, 0, 0.35f, 1.1f, 0, -0.35f, -0.25f, 0, 0.45f, 0, 0.12f, 0.45f, 0, -0.12f, -0.3f),
			QuirkKeyframes.f(9, 0.5f, 0, 0.9f, 0.5f, 0, -0.9f, 0.2f, 0, -1.1f, 0, 0.35f, -1.1f, 0, -0.35f, -0.15f),
			QuirkKeyframes.f(16, -0.7f, 0, 0.8f, -0.7f, 0, -0.8f, 0.1f, 0, -0.7f, 0, 0.3f, -0.7f, 0, -0.3f, 0.15f), QuirkKeyframes.rest(22) };
	/** Scurry: bolt upright for a beat, then low and flat out, arms pumping back (the legs keep the walk cycle). */
	private static final float[][] SCURRY = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.6f, 0, 0.5f, -0.6f, 0, -0.5f, -0.2f, 0, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(5, 1.25f, 0, 0.3f, 1.25f, 0, -0.3f, 0.6f, 0, 0, 0, 0, 0, 0, 0, -0.45f), QuirkKeyframes.f(96, 1.25f, 0, 0.3f, 1.25f, 0, -0.3f, 0.6f, 0, 0, 0, 0, 0, 0, 0, -0.45f),
			QuirkKeyframes.rest(100) };
	/** Snare Trap: down on one knee, stringing the wire hand over hand, a tug to test it. */
	private static final float[][] TRAP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.9f, 0, -0.2f, -0.7f, 0, 0.2f, 0.6f, 0, -0.7f, 0, 0.12f, -0.4f, 0, -0.12f, 0.5f),
			QuirkKeyframes.f(6, -0.55f, 0.3f, -0.2f, -1.05f, -0.3f, 0.2f, 0.6f, 0.15f, -0.7f, 0, 0.12f, -0.4f, 0, -0.12f, 0.5f),
			QuirkKeyframes.f(9, -1.05f, 0.3f, -0.2f, -0.55f, -0.3f, 0.2f, 0.6f, -0.15f, -0.7f, 0, 0.12f, -0.4f, 0, -0.12f, 0.5f),
			QuirkKeyframes.f(12, -0.3f, 0.2f, 0.1f, -0.3f, -0.2f, -0.1f, 0.4f, 0, -0.5f, 0, 0.12f, -0.3f, 0, -0.12f, 0.35f), QuirkKeyframes.rest(17) };
	/** Inside Job: a headlong dive, arms first (seen for the 4 ticks he shrinks in). */
	private static final float[][] DIVE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(1, 0.7f, 0, 0.4f, 0.7f, 0, -0.4f, 0.5f, 0, -0.6f, 0, 0.2f, -0.6f, 0, -0.2f, -0.2f),
			QuirkKeyframes.f(3, -3.05f, 0, 0.12f, -3.05f, 0, -0.12f, 1.0f, 0, 0.7f, 0, 0.08f, 0.7f, 0, -0.08f, -0.6f),
			QuirkKeyframes.f(6, -3.05f, 0, 0.12f, -3.05f, 0, -0.12f, 1.0f, 0, 0.7f, 0, 0.08f, 0.7f, 0, -0.08f, -0.6f), QuirkKeyframes.rest(10) };
	/** Inside Job's exit: out with a little spin (the body's yaw, once round) and arms flung wide. */
	private static final float[][] POP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.3f, 0, 1.4f, -0.3f, 0, -1.4f, 0, 2.2f, 0, 0, 0.3f, 0, 0, -0.3f, -0.2f),
			QuirkKeyframes.f(7, -0.3f, 0, 1.2f, -0.3f, 0, -1.2f, 0, 4.8f, 0, 0, 0.3f, 0, 0, -0.3f, 0.1f),
			QuirkKeyframes.f(10, 0, 0, 0.4f, 0, 0, -0.4f, 0, 6.2832f, -0.35f, 0, 0.2f, -0.35f, 0, -0.2f, 0.2f), QuirkKeyframes.rest(15) };
	/** Rewire: a beat of thought, a finger at the temple, then the arm snaps out and points at the block. */
	private static final float[][] REWIRE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.3f, 0, 0.2f, -2.4f, 0.8f, 0, -0.05f, 0, 0, 0, 0, 0, 0, 0, -0.1f),
			QuirkKeyframes.f(4, -1.65f, -0.1f, 0, -2.4f, 0.8f, 0, 0.05f, -0.15f, 0, 0, 0, 0, 0, 0, 0.1f), QuirkKeyframes.f(6, -1.5f, -0.1f, 0, -2.4f, 0.8f, 0, 0, -0.15f, 0, 0, 0, 0, 0, 0, 0.1f),
			QuirkKeyframes.f(11, -1.55f, -0.1f, 0, -2.3f, 0.8f, 0, 0, -0.1f, 0, 0, 0, 0, 0, 0, 0.1f), QuirkKeyframes.rest(16) };
	/** Genius Repair: over the job, one hand holding it, the other working quickly, then a flick of the wrist. */
	private static final float[][] REPAIR = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.3f, -0.4f, 0, -1.3f, 0.4f, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(6, -0.95f, -0.55f, 0.1f, -1.4f, 0.3f, 0, 0.2f, 0.1f, 0, 0, 0, 0, 0, 0, 0.3f), QuirkKeyframes.f(9, -1.5f, -0.3f, -0.1f, -1.2f, 0.45f, 0, 0.2f, -0.1f, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(12, -0.95f, -0.55f, 0.1f, -1.4f, 0.3f, 0, 0.2f, 0.1f, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(15, -1.5f, -0.3f, -0.1f, -1.2f, 0.45f, 0, 0.2f, -0.1f, 0, 0, 0, 0, 0, 0, 0.3f), QuirkKeyframes.f(18, -2.0f, 0, 0.4f, -0.9f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0, 0, -0.1f),
			QuirkKeyframes.rest(23) };
	/** Mind Map: both hands to the temples, the big head tipped back as it all comes in, and a slow turn to take it in. */
	private static final float[][] MIND = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -2.5f, -0.9f, 0, -2.5f, 0.9f, 0, -0.1f, 0, 0, 0, 0, 0, 0, 0, -0.35f),
			QuirkKeyframes.f(12, -2.5f, -0.9f, 0.05f, -2.5f, 0.9f, -0.05f, -0.1f, 0.25f, 0, 0, 0, 0, 0, 0, -0.3f),
			QuirkKeyframes.f(20, -2.5f, -0.9f, 0.05f, -2.5f, 0.9f, -0.05f, -0.1f, -0.25f, 0, 0, 0, 0, 0, 0, -0.3f), QuirkKeyframes.f(26, -2.5f, -0.9f, 0, -2.5f, 0.9f, 0, 0, 0, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.rest(32) };
	/** Precision Jab: a snap from the shoulder and straight back. */
	private static final float[][] JAB = { QuirkKeyframes.rest(0), QuirkKeyframes.f(1, -0.5f, 0.3f, 0.2f, 0.3f, 0, -0.1f, -0.08f, 0.3f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(3, -1.8f, 0.2f, 0, 0.45f, 0, -0.1f, 0.15f, -0.4f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(8) };
	/** Quick Read: a palm laid on it, a tilt of the head. */
	private static final float[][] READ = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.6f, 0, 0, 0.2f, 0, -0.1f, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0.15f),
			QuirkKeyframes.f(6, -1.55f, 0.1f, 0, 0.2f, 0, -0.1f, 0.05f, 0.12f, 0, 0, 0, 0, 0, 0, 0.05f), QuirkKeyframes.rest(11) };
	/** Pocket Workshop: rummages in thin air with both hands, then spreads them over the bench. */
	private static final float[][] WORKSHOP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.1f, 0.35f, 0f, -0.9f, -0.35f, 0f, 0.25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.4f),
			QuirkKeyframes.f(5, -0.8f, 0.2f, 0f, -1.3f, -0.45f, 0f, 0.25f, 0.12f, 0f, 0f, 0f, 0f, 0f, 0f, 0.4f),
			QuirkKeyframes.f(8, -1.3f, 0.45f, 0f, -0.8f, -0.2f, 0f, 0.25f, -0.12f, 0f, 0f, 0f, 0f, 0f, 0f, 0.4f), QuirkKeyframes.f(11, -1.2f, -0.3f, 0.3f, -1.2f, 0.3f, -0.3f, 0.2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.35f),
			QuirkKeyframes.rest(15) };
	/** The Block Mech goes up round him: head down over the console, hands racing between the panels, then both grab the levers. */
	private static final float[][] MECH_BUILD = stretch(new float[][] { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.5f, -0.5f, 0, -0.8f, 0.5f, 0, 0.3f, 0.3f, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(6, -0.8f, -0.5f, 0, -1.6f, 0.5f, 0, 0.3f, -0.3f, 0, 0, 0, 0, 0, 0, 0.3f), QuirkKeyframes.f(9, -2.3f, -0.4f, 0.3f, -0.8f, 0.3f, 0, 0.1f, 0.2f, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(12, -0.8f, -0.3f, 0, -2.3f, 0.4f, -0.3f, 0.1f, -0.2f, 0, 0, 0, 0, 0, 0, -0.2f), QuirkKeyframes.f(15, -1.5f, -0.5f, 0, -1.5f, 0.5f, 0, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.25f),
			QuirkKeyframes.f(19, -1.15f, -0.25f, 0.1f, -1.15f, 0.25f, -0.1f, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(23) }, 1, 5, 12, 3);
	// the levers: a punch throws one forward and hauls the other back, the slam hauls both back then rams them home, the jets pull both up
	private static final float[][] LEVER_PUNCH_R = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.65f, -0.25f, 0.1f, -1.5f, 0.25f, -0.1f, -0.1f, 0.3f, 0, 0, 0, 0, 0, 0, -0.1f),
			QuirkKeyframes.f(5, -1.75f, -0.25f, 0.1f, -0.75f, 0.25f, -0.1f, 0.25f, -0.35f, 0, 0, 0, 0, 0, 0, 0.1f), QuirkKeyframes.rest(13) };
	private static final float[][] LEVER_PUNCH_L = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.5f, -0.25f, 0.1f, -0.65f, 0.25f, -0.1f, -0.1f, -0.3f, 0, 0, 0, 0, 0, 0, -0.1f),
			QuirkKeyframes.f(5, -0.75f, -0.25f, 0.1f, -1.75f, 0.25f, -0.1f, 0.25f, 0.35f, 0, 0, 0, 0, 0, 0, 0.1f), QuirkKeyframes.rest(13) };
	private static final float[][] LEVER_SLAM = { QuirkKeyframes.rest(0), QuirkKeyframes.f(6, -0.5f, -0.25f, 0.1f, -0.5f, 0.25f, -0.1f, -0.2f, 0, 0, 0, 0, 0, 0, 0, -0.3f),
			QuirkKeyframes.f(9, -1.8f, -0.25f, 0.1f, -1.8f, 0.25f, -0.1f, 0.4f, 0, 0, 0, 0, 0, 0, 0, 0.25f), QuirkKeyframes.f(14, -1.7f, -0.25f, 0.1f, -1.7f, 0.25f, -0.1f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.15f),
			QuirkKeyframes.rest(22) };
	private static final float[][] LEVER_JET = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.45f, -0.25f, 0.1f, -0.45f, 0.25f, -0.1f, -0.25f, 0, 0, 0, 0, 0, 0, 0, -0.35f),
			QuirkKeyframes.f(10, -0.55f, -0.25f, 0.1f, -0.55f, 0.25f, -0.1f, -0.2f, 0, 0, 0, 0, 0, 0, 0, -0.25f), QuirkKeyframes.rest(16) };

	// first person: {tick, pitch, yaw, roll, x, y, z}
	private static final FirstPersonClip FP_PINCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 10, -10, 0, 0, -0.08f, 0.1f),
			FirstPersonClip.f(4, -32, 25, 0, 0.16f, 0.17f, -0.3f), FirstPersonClip.f(8, -30, 22, 25, 0.15f, 0.15f, -0.28f), FirstPersonClip.f(13, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_READ = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -18, 20, 0, 0.12f, 0.1f, -0.22f),
			FirstPersonClip.f(7, -16, 22, 5, 0.12f, 0.1f, -0.22f), FirstPersonClip.f(11, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_CHOP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -85, 5, 0, 0.05f, 0.55f, 0.2f),
			FirstPersonClip.f(5, 30, 15, 0, 0.1f, -0.3f, -0.12f), FirstPersonClip.f(8, 22, 12, 0, 0.1f, -0.22f, -0.1f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_THINK = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -60, 45, 20, 0.3f, 0.45f, 0.1f),
			FirstPersonClip.f(9, -56, 42, 15, 0.28f, 0.42f, 0.08f), FirstPersonClip.f(15, -60, 45, 20, 0.3f, 0.45f, 0.1f), FirstPersonClip.f(21, -56, 42, 15, 0.28f, 0.42f, 0.08f),
			FirstPersonClip.f(27, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_TINKER = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, 25, 0, 0.18f, 0.1f, -0.05f),
			FirstPersonClip.f(6, -32, 18, 12, 0.14f, 0.16f, -0.1f), FirstPersonClip.f(9, -16, 28, -8, 0.2f, 0.08f, -0.03f), FirstPersonClip.f(12, -30, 20, 30, 0.15f, 0.14f, -0.08f),
			FirstPersonClip.f(16, -55, 10, 0, 0.1f, 0.3f, 0f), FirstPersonClip.f(23, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_STRING = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 12, 25, 0, 0.18f, -0.12f, -0.1f),
			FirstPersonClip.f(6, 20, 15, 10, 0.12f, -0.18f, -0.15f), FirstPersonClip.f(9, 12, 30, -10, 0.2f, -0.12f, -0.1f), FirstPersonClip.f(12, 2, 20, 0, 0.14f, -0.05f, 0.02f),
			FirstPersonClip.f(17, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_REPAIR = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -22, 25, 0, 0.18f, 0.1f, -0.06f),
			FirstPersonClip.f(6, -30, 16, 15, 0.14f, 0.15f, -0.09f), FirstPersonClip.f(9, -14, 30, -10, 0.2f, 0.07f, -0.03f), FirstPersonClip.f(12, -30, 16, 15, 0.14f, 0.15f, -0.09f),
			FirstPersonClip.f(15, -14, 30, -10, 0.2f, 0.07f, -0.03f), FirstPersonClip.f(18, -50, 10, 40, 0.08f, 0.26f, -0.05f), FirstPersonClip.f(23, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_TEMPLES = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -45, 22, 12, 0.1f, 0.3f, 0.05f),
			FirstPersonClip.f(16, -48, 24, 14, 0.1f, 0.32f, 0.05f), FirstPersonClip.f(26, -46, 22, 12, 0.1f, 0.3f, 0.05f), FirstPersonClip.f(32, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_LEAP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(1, 20, 0, 0, 0, -0.12f, 0.1f),
			FirstPersonClip.f(3, 40, 10, 0, 0.05f, -0.45f, 0.2f), FirstPersonClip.f(16, -10, -10, -15, -0.08f, 0.05f, -0.05f), FirstPersonClip.f(22, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_RUN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 35, 10, 0, 0.05f, -0.4f, 0.2f),
			FirstPersonClip.f(16, 35, 10, 0, 0.05f, -0.4f, 0.2f), FirstPersonClip.f(22, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_JAB = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(1, 8, -5, 0, 0, -0.04f, 0.08f),
			FirstPersonClip.f(3, -20, 25, 0, 0.15f, 0.08f, -0.26f), FirstPersonClip.f(8, 0, 0, 0, 0, 0, 0));
	/** Rewire's point -- a beat, then the hand out straight at the block. */
	private static final FirstPersonClip FP_POINT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 6, 0, 0, 0, -0.05f, 0.05f),
			FirstPersonClip.f(4, -12, 8, 0, 0.05f, 0.18f, -0.34f), FirstPersonClip.f(11, -12, 8, 0, 0.05f, 0.18f, -0.3f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0));
	/** Inside Job's dive -- both hands thrust forward. */
	private static final FirstPersonClip FP_DIVE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(1, 20, 0, 0, 0, -0.1f, 0.1f),
			FirstPersonClip.f(3, -35, 0, 0, 0f, 0.25f, -0.5f), FirstPersonClip.f(6, -35, 0, 0, 0f, 0.25f, -0.5f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_POP = FirstPersonClip.of(FirstPersonClip.f(0, -35, 0, 0, 0f, 0.2f, -0.3f), FirstPersonClip.f(4, -15, -40, 30, -0.25f, 0.2f, 0f),
			FirstPersonClip.f(10, -10, -30, 20, -0.15f, 0.1f, 0f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_WORKSHOP = FirstPersonClip.of(FirstPersonClip.f(0, 0f, 0f, 0f, 0f, 0f, 0f), FirstPersonClip.f(2, -15f, 25f, 0f, 0.12f, 0.05f, -0.1f),
			FirstPersonClip.f(5, -5f, 15f, 0f, 0.06f, 0f, -0.05f), FirstPersonClip.f(8, -15f, 25f, 0f, 0.12f, 0.05f, -0.1f), FirstPersonClip.f(11, -20f, -10f, 0f, -0.05f, 0.08f, -0.08f),
			FirstPersonClip.f(15, 0f, 0f, 0f, 0f, 0f, 0f)).both();
	/** In the Block Mech: the lever coming up into view and being shoved. */
	private static final FirstPersonClip FP_LEVER = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -8, 10, 0, 0.05f, 0.1f, 0.12f),
			FirstPersonClip.f(5, -25, 18, 0, 0.1f, 0.14f, -0.22f), FirstPersonClip.f(13, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_LEVER_BOTH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(6, -5, 10, 0, 0.05f, 0.08f, 0.2f),
			FirstPersonClip.f(9, -28, 18, 0, 0.1f, 0.14f, -0.28f), FirstPersonClip.f(14, -24, 16, 0, 0.1f, 0.12f, -0.24f), FirstPersonClip.f(22, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_LEVER_UP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, 12, 0, 0.06f, 0.2f, 0.14f),
			FirstPersonClip.f(10, -18, 12, 0, 0.06f, 0.18f, 0.12f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_CONSOLE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -22, 25, 0, 0.18f, 0.1f, -0.06f),
			FirstPersonClip.f(7, -40, 15, 10, 0.12f, 0.24f, -0.08f), FirstPersonClip.f(11, -18, 28, -8, 0.2f, 0.06f, -0.04f), FirstPersonClip.f(15, -32, 18, 8, 0.14f, 0.16f, -0.06f),
			FirstPersonClip.f(23, 0, 0, 0, 0, 0, 0)).both();

	/** v0.17.2 Inside Job: creature entity id -> the game time its shudder runs to (kept up from every inside Grey Matter's tick). */
	private static final Map<Integer, Long> SHAKING = new ConcurrentHashMap<>();
	private static boolean renderersRegistered;

	public GreyMatterClient() {
		ModelPart root = AlienParts.bake(64, 64, r -> r.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8)
				// v0.17.1: the skin's hat layer on the big head too (its second layer)
				.texOffs(32, 0).addBox(-4, -8, -4, 8, 8, 8, new net.minecraft.client.model.geom.builders.CubeDeformation(0.5f)),
				PartPose.ZERO));
		bigHead = root.getChild("head");
		if (!renderersRegistered) {
			renderersRegistered = true;
			GreyMatterGadgets.register(); // v0.17.2 round 3: the Galvan Sentry and the Block Mech
		}
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		AlienParts.on(pose, model.head, () -> {
			pose.pushPose();
			pose.scale(HEAD_SCALE, HEAD_SCALE, HEAD_SCALE);
			// one buffer at a time: fetching another render type ends the previous one
			bigHead.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN)), light, OverlayTexture.NO_OVERLAY);
			// v0.17.1 (user: "Remove these big eyes from grey matter please, his skin already has eyes"): no eye boxes over the face
			pose.popPose();
		});
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		// the big head replaces the player's (renderExtras redraws it on the same bone)
		m.head.visible = false;
		m.hat.visible = false;
		GreyMatterMechEntity mech = GreyMatterMechEntity.mechOf(p);
		float[][] clip = switch (animId) {
			case GreyMatterKit.ANIM_PINCH -> PINCH;
			case GreyMatterKit.ANIM_STRIKE -> STRIKE;
			case GreyMatterKit.ANIM_ANALYZE -> ANALYZE;
			case GreyMatterKit.ANIM_TURRET -> TURRET;
			case GreyMatterKit.ANIM_LEAP -> LEAP;
			case GreyMatterKit.ANIM_TRAP -> TRAP;
			case GreyMatterKit.ANIM_DIVE -> DIVE;
			case GreyMatterKit.ANIM_POP -> POP;
			case GreyMatterKit.ANIM_REWIRE -> REWIRE;
			case GreyMatterKit.ANIM_REPAIR -> REPAIR;
			case GreyMatterKit.ANIM_MIND -> MIND;
			case GreyMatterKit.ANIM_JAB -> JAB;
			case GreyMatterKit.ANIM_READ -> READ;
			case GreyMatterKit.ANIM_WORKSHOP -> WORKSHOP;
			case GreyMatterKit.ANIM_MECH_BUILD -> MECH_BUILD;
			case GreyMatterKit.ANIM_LEVER_PUNCH -> mech != null && mech.action() == GreyMatterMechEntity.ACT_PUNCH_L ? LEVER_PUNCH_L : LEVER_PUNCH_R;
			case GreyMatterKit.ANIM_LEVER_SLAM -> LEVER_SLAM;
			case GreyMatterKit.ANIM_LEVER_JET -> LEVER_JET;
			default -> null;
		};
		OmnitrixState s = Omnitrix.state(p);
		if (animId == GreyMatterKit.ANIM_LEAP && (s.kitFlags & GreyMatterKit.F_LEAP) == 0 && ticks > 4) {
			clip = null;
		}
		// v0.17.2: riding a player's shoulder (OmnitrixRiding) -- a move's clip still plays over it
		boolean shoulder = com.projecthero.mod.omnitrix.OmnitrixRiding.onShoulder(p);
		if (shoulder) {
			com.projecthero.mod.client.omnitrix.OmnitrixRidePoses.shoulder(m, p, age);
		}
		// v0.17.2 Sticky Climber: flat to the wall, limbs splayed, hands walking up it
		// (the synced flag for everyone else; his own client knows its physics this frame)
		boolean climbing = !shoulder && !p.isPassenger() && ((s.kitFlags & GreyMatterKit.F_CLIMB) != 0
				|| p instanceof net.minecraft.client.player.LocalPlayer lp && !lp.onGround() && !lp.isInWater() && lp.onClimbable());
		if (climbing) {
			float sw = Mth.sin(age * 0.45f) * 0.35f;
			m.rightArm.xRot = -2.7f + sw;
			m.leftArm.xRot = -2.7f - sw;
			m.rightArm.zRot = 0.45f;
			m.leftArm.zRot = -0.45f;
			m.rightLeg.xRot = -0.35f - sw;
			m.leftLeg.xRot = -0.35f + sw;
			m.rightLeg.zRot = 0.5f;
			m.leftLeg.zRot = -0.5f;
			m.head.xRot = -0.35f;
		} else if (mech != null) {
			// v0.17.2 round 3: on the seat with a lever in each hand; they rock as the mech walks
			float walk = mech.walkAnimation.speed() > 0.05f ? Mth.sin(mech.walkAnimation.position() * 1.1f) * 0.18f : 0f;
			m.rightArm.xRot = -1.15f + walk;
			m.leftArm.xRot = -1.15f - walk;
			m.rightArm.yRot = -0.25f;
			m.leftArm.yRot = 0.25f;
			m.rightArm.zRot = 0.1f;
			m.leftArm.zRot = -0.1f;
		} else if (!shoulder) {
			// a frog's splayed stance
			m.rightLeg.zRot += 0.14f;
			m.leftLeg.zRot -= 0.14f;
			m.rightArm.zRot += 0.1f;
			m.leftArm.zRot -= 0.1f;
		}
		// the base pose lies under every clip, so a clip ending hands back to it instead of snapping
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		if (animId == GreyMatterKit.ANIM_SCURRY && ((s.kitFlags & GreyMatterKit.F_SCURRY) != 0 || ticks <= 4)) {
			// arms, back and head only: his legs keep the walk cycle, flat out
			float[] f = QuirkKeyframes.sample(SCURRY, Math.min(ticks, 90f));
			if (f != null) {
				int mask = 0;
				for (int c = QuirkKeyframes.R_ARM_X; c <= QuirkKeyframes.BODY_Y; c++) {
					mask |= QuirkKeyframes.bit(c);
				}
				QuirkKeyframes.applyMasked(m, f, Math.min(1f, ticks / 2f), mask | QuirkKeyframes.bit(QuirkKeyframes.HEAD_X));
				played = true;
			}
		}
		return played || climbing;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case GreyMatterKit.ANIM_PINCH -> FP_PINCH;
			case GreyMatterKit.ANIM_READ -> FP_READ;
			case GreyMatterKit.ANIM_STRIKE -> FP_CHOP;
			case GreyMatterKit.ANIM_ANALYZE -> FP_THINK;
			case GreyMatterKit.ANIM_TURRET -> FP_TINKER;
			case GreyMatterKit.ANIM_TRAP -> FP_STRING;
			case GreyMatterKit.ANIM_REPAIR -> FP_REPAIR;
			case GreyMatterKit.ANIM_MIND -> FP_TEMPLES;
			case GreyMatterKit.ANIM_LEAP -> FP_LEAP;
			case GreyMatterKit.ANIM_SCURRY -> FP_RUN;
			case GreyMatterKit.ANIM_JAB -> FP_JAB;
			case GreyMatterKit.ANIM_WORKSHOP -> FP_WORKSHOP;
			case GreyMatterKit.ANIM_REWIRE -> FP_POINT;
			case GreyMatterKit.ANIM_DIVE -> FP_DIVE;
			case GreyMatterKit.ANIM_POP -> FP_POP;
			case GreyMatterKit.ANIM_MECH_BUILD -> FP_CONSOLE;
			case GreyMatterKit.ANIM_LEVER_PUNCH -> FP_LEVER;
			case GreyMatterKit.ANIM_LEVER_SLAM -> FP_LEVER_BOTH;
			case GreyMatterKit.ANIM_LEVER_JET -> FP_LEVER_UP;
			default -> null;
		};
	}

	/** v0.17.2 round 3: inside the mech vanilla's camera (a tiny body's) would sit in the hull; pull back to see the whole machine. */
	@Override
	public float cameraDistance(AbstractClientPlayer p) {
		return GreyMatterKit.piloting(p) ? 3.6f : 0f;
	}

	@Override
	public int hudHeight() {
		var p = Minecraft.getInstance().player;
		return p != null && GreyMatterKit.piloting(p) ? 10 : 0;
	}

	/** v0.17.2 round 3: the Block Mech's health and the seconds left, over the name line. */
	@Override
	public void hud(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.player.LocalPlayer p, int x, int y, int w, float partial) {
		GreyMatterMechEntity mech = GreyMatterMechEntity.mechOf(p);
		if (mech == null) {
			return;
		}
		float frac = Mth.clamp(mech.getHealth() / Math.max(1f, mech.getMaxHealth()), 0f, 1f);
		int colour = frac < 0.3f ? 0xFFFF5A3C : 0xFF7FE6FF;
		long left = Math.max(0L, (mech.endAt() - p.level().getGameTime() + 19) / 20);
		com.projecthero.mod.client.omnitrix.OmnitrixHud.meter(g, Minecraft.getInstance(), x, y - 8, net.minecraft.network.chat.Component.translatable("hud.projecthero.omnitrix.grey_matter.mech"), frac,
				colour, Math.round(mech.getHealth()) + " HP  " + left + "s", colour);
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		long now = p.level().getGameTime();
		float t = now - s.animStart;
		var r = p.getRandom();
		if ((s.animId == GreyMatterKit.ANIM_ANALYZE || s.animId == GreyMatterKit.ANIM_MIND) && t < 26 && r.nextInt(3) == 0) {
			p.level().addParticle(ParticleTypes.ENCHANT, p.getX() + (r.nextDouble() - 0.5) * 0.4, p.getY() + p.getBbHeight() + 0.2,
					p.getZ() + (r.nextDouble() - 0.5) * 0.4, (r.nextDouble() - 0.5) * 0.4, 0.3, (r.nextDouble() - 0.5) * 0.4);
		}
		// v0.17.2 Inside Job: keep the creature he is in shuddering (the server's synced timer holds its entity id + 1)
		if ((s.kitFlags & GreyMatterKit.F_INSIDE) != 0) {
			long target = s.readyAt(GreyMatterKit.ID + "." + GreyMatterKit.INSIDE_TARGET);
			if (target > 0) {
				SHAKING.put((int) (target - 1), now + 3);
			}
		}
		if (!SHAKING.isEmpty()) {
			SHAKING.values().removeIf(until -> until < now);
		}
		// v0.17.2 round 3: the block's shards pulled in while the mech goes up; a puff from its stacks as it runs
		GreyMatterMechEntity mech = GreyMatterMechEntity.mechOf(p);
		if (mech != null) {
			if (mech.state() == GreyMatterMechEntity.STATE_ASSEMBLING) {
				// vanilla has just told him Sneak gets off; here Sneak is the Shift+key modifier
				if (p == Minecraft.getInstance().player) {
					Minecraft.getInstance().gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.omnitrix.grey_matter.mech_seat"), false);
				}
				var shard = new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, mech.material());
				for (int i = 0; i < 3; i++) {
					double a = r.nextDouble() * Math.PI * 2, d = 0.7 + r.nextDouble() * 0.6;
					p.level().addParticle(shard, mech.getX() + Math.cos(a) * d, mech.getY() + r.nextDouble() * 1.4, mech.getZ() + Math.sin(a) * d, -Math.cos(a) * 0.3, 0.02, -Math.sin(a) * 0.3);
				}
			} else if (mech.active() && now % 7 == 0) {
				float yaw = mech.yBodyRot * Mth.DEG_TO_RAD;
				double bx = Mth.sin(yaw) * 0.29, bz = -Mth.cos(yaw) * 0.29;
				p.level().addParticle(ParticleTypes.SMOKE, mech.getX() + bx, mech.getY() + 1.24, mech.getZ() + bz, 0, 0.04, 0);
			}
		}
	}

	// ---------------------------------------------------------------- v0.17.2 Inside Job render hooks (GreyMatterInsideRenderMixin)

	/** Ticks since Inside Job's dive began, or -1 if {@code e} is not a Grey Matter inside a creature. */
	private static float insideTicks(LivingEntity e, float partial) {
		if (!(e instanceof Player p) || !GreyMatterKit.insideNow(p)) {
			return -1f;
		}
		OmnitrixState s = Omnitrix.state(p);
		return s.animId == GreyMatterKit.ANIM_DIVE ? p.level().getGameTime() + partial - s.animStart : 99f;
	}

	/** He is not drawn once he has shrunk all the way in (4 ticks into the dive). */
	public static boolean hiddenInside(LivingEntity e, float partial) {
		return insideTicks(e, partial) >= 4f;
	}

	/** The 4-tick shrink into the creature (on him), and the creature's shudder and bulges (on it). */
	public static void insideJitter(LivingEntity e, PoseStack pose, float partial) {
		float in = insideTicks(e, partial);
		if (in >= 0f) {
			float k = Math.max(0.02f, 1f - in / 4f);
			pose.scale(k, k, k);
			return;
		}
		if (SHAKING.isEmpty() || !SHAKING.containsKey(e.getId())) {
			return;
		}
		float t = e.level().getGameTime() + partial;
		// a bulge with every pulse (each half second), then small squirms in between
		float phase = t % 10f;
		float bulge = (float) Math.exp(-phase * 0.55f) * 0.11f + Mth.sin(t * 2.3f) * 0.015f;
		pose.translate(Mth.sin(t * 3.1f) * 0.03f, 0f, Mth.cos(t * 2.7f) * 0.03f);
		pose.scale(1f + bulge, 1f - bulge * 0.4f, 1f + bulge);
	}

	@Override
	public float[] badge() {
		// v0.17.1 (user: "move his omnitrix badge to his back"): over the spot on his back where the skin's own symbol was (painted
		// out), facing backward (the 2)
		return new float[] { 0f, 4.0f, 2.3f, 8.0f, 2f };
	}
}
