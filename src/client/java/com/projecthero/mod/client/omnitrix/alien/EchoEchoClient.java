package com.projecthero.mod.client.omnitrix.alien;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienBodyTurn;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.alien.echoecho.EchoCloneRenderer;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.echoecho.EchoCloneEntity;
import com.projecthero.mod.omnitrix.alien.echoecho.EchoEchoKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Echo Echo's client kit -- the player body in his white shell skin with the black face plate and nothing else (v0.17.2
 * revamp: the ear discs and back speaker are gone, user: "only use the skins i provided"); his clones ({@link EchoCloneRenderer}) wear the same body and copy his poses. Rendered effects (world pass): sound waves as
 * rings travelling out along the scream (from him and every clone), the blast's widening cone of rings, the cannon's tight rings and
 * beam, the Echo Ring and the Disorient hum's ground rings, the Sound Barrier's spinning shell, the Ultimate Chorus' build-up and its
 * dome of sound.
 * <p>
 * v0.17.2 round 3 (user: "edit and update animations on all moves please"): he screams with his whole small body -- chin out, arms thrown
 * back, the sound shoving him a step backward -- and each move has its own pose, first-person clip and whole-body turn ({@link #turn}).
 * Clones slide out of him on a ring of sound and rush back in on a Merge.
 */
public final class EchoEchoClient implements AlienClientKit, AlienBodyTurn {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/echo_echo.png");
	/** His body size (the kit's scale stat); the clones are drawn at it too. */
	public static final float SIZE = 0.6f;
	private static final int WAVE = 0xFFCFF4FF;

	// f(tick, rArm xyz, lArm xyz, body x y, rLeg xyz, lLeg xyz, head x)
	private static float[] f(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float by, float rlx, float llx, float hx) {
		return QuirkKeyframes.f(t, rax, ray, raz, lax, lay, laz, bx, by, rlx, 0, 0.1f * Math.signum(rlx - llx), llx, 0, -0.1f * Math.signum(rlx - llx), hx);
	}

	/** Sonic Scream: a breath in, then the chin thrusts out and the arms are thrown back by what comes out of him. */
	private static final float[][] SCREAM = { QuirkKeyframes.rest(0), f(1.5f, -0.5f, 0, 0.3f, -0.5f, 0, -0.3f, -0.15f, 0, 0.1f, -0.1f, 0.2f),
			f(3.5f, 0.75f, 0, 0.55f, 0.75f, 0, -0.55f, 0.3f, 0, -0.4f, 0.25f, -0.35f), f(9, 0.7f, 0, 0.6f, 0.7f, 0, -0.6f, 0.25f, 0, -0.35f, 0.25f, -0.3f), QuirkKeyframes.rest(13) };
	/** Wall of Sound: dug in, arms back, pouring it out for as long as the key is down. */
	private static final float[][] BLAST = { QuirkKeyframes.rest(0), f(3, 0.9f, 0, 0.75f, 0.9f, 0, -0.75f, 0.4f, 0, -0.6f, 0.4f, -0.45f),
			f(40, 0.9f, 0, 0.8f, 0.9f, 0, -0.8f, 0.4f, 0, -0.6f, 0.4f, -0.45f), QuirkKeyframes.rest(44) };
	private static final float[][] WALL_END = { f(0, 0.9f, 0, 0.8f, 0.9f, 0, -0.8f, 0.4f, 0, -0.6f, 0.4f, -0.45f), QuirkKeyframes.rest(7) };
	/** Echo Ring: he balls in on himself, then flings everything wide as the ring goes out. */
	private static final float[][] RING = { QuirkKeyframes.rest(0), f(2, -1.0f, 0.5f, -0.3f, -1.0f, -0.5f, 0.3f, 0.55f, 0, -0.5f, -0.5f, 0.3f),
			f(4.5f, 0.1f, 0, 1.7f, 0.1f, 0, -1.7f, -0.3f, 0, 0.4f, -0.4f, -0.45f), f(10, 0.1f, 0, 1.6f, 0.1f, 0, -1.6f, -0.2f, 0, 0.35f, -0.35f, -0.35f), QuirkKeyframes.rest(14) };
	/** Echolocation: he cups one hand at his mouth for the ping, the other at the side of his head to hear it come back. */
	private static final float[][] HUM = { QuirkKeyframes.rest(0), f(4, -2.1f, -0.75f, 0, -2.5f, 0.1f, -0.75f, 0.05f, 0.25f, 0.1f, -0.1f, 0.05f),
			f(18, -2.1f, -0.75f, 0, -2.5f, 0.1f, -0.75f, 0.05f, 0.35f, 0.1f, -0.1f, 0.2f), QuirkKeyframes.rest(24) };
	/** Sonic Leap: crouched over the blast, then thrown up it, arms streaming behind him. */
	private static final float[][] LEAP = { QuirkKeyframes.rest(0), f(2, 0.7f, 0, 0.5f, 0.7f, 0, -0.5f, 0.6f, 0, -1.0f, -1.0f, 0.35f),
			f(5, 0.85f, 0, 0.3f, 0.85f, 0, -0.3f, -0.1f, 0, 0.4f, -0.25f, -0.2f), f(14, 0.6f, 0, 0.4f, 0.6f, 0, -0.4f, 0, 0, 0.3f, -0.2f, 0), QuirkKeyframes.rest(20) };
	/** Echo Swap: he pops out of one place and into the other, landing with his arms out. */
	private static final float[][] POP = { f(0, -0.2f, 0, 1.4f, -0.2f, 0, -1.4f, 0.35f, 0, -0.6f, -0.6f, 0), f(4, -0.1f, 0, 0.9f, -0.1f, 0, -0.9f, 0.15f, 0, -0.2f, -0.2f, 0),
			QuirkKeyframes.rest(10) };
	/** Sound Barrier: palms out, braced behind the shell. */
	private static final float[][] BARRIER = { QuirkKeyframes.rest(0), f(3, -1.6f, 0.6f, 0, -1.6f, -0.6f, 0, 0.2f, 0, -0.3f, 0.2f, 0.2f),
			f(56, -1.6f, 0.6f, 0, -1.6f, -0.6f, 0, 0.2f, 0, -0.3f, 0.2f, 0.2f), QuirkKeyframes.rest(60) };
	private static final float[][] BARRIER_END = { f(0, -1.6f, 0.6f, 0, -1.6f, -0.6f, 0, 0.2f, 0, -0.3f, 0.2f, 0.2f), QuirkKeyframes.rest(6) };
	/** Surround Sound, the order: a sweep of the arm sends them out. */
	private static final float[][] POINT = { QuirkKeyframes.rest(0), f(3, -2.2f, 0.3f, 0, 0.3f, 0, -0.2f, -0.1f, 0.35f, 0.1f, -0.1f, 0),
			f(7, -1.5f, -0.4f, 0, 0.3f, 0, -0.2f, 0.15f, -0.3f, -0.1f, 0.1f, 0), f(14, -1.5f, -0.3f, 0, 0.3f, 0, -0.2f, 0.1f, -0.3f, -0.1f, 0.1f, 0) };
	/** Duplicate: he leans one way as a second him slides out the other. */
	private static final float[][] SPLIT = { QuirkKeyframes.rest(0), f(2, -0.3f, 0, 1.0f, -0.3f, 0, -1.0f, 0, 0.6f, 0.4f, -0.4f, 0),
			f(6, -0.3f, 0, 1.0f, -0.3f, 0, -1.0f, 0, -0.6f, 0.4f, -0.4f, 0), QuirkKeyframes.rest(10) };
	/** Merge: arms wide to take them back, then folded in as they arrive. */
	private static final float[][] MERGE = { QuirkKeyframes.rest(0), f(3, -0.5f, 0, 1.2f, -0.5f, 0, -1.2f, -0.15f, 0, 0.2f, -0.2f, -0.2f),
			f(9, -1.2f, 0.8f, 0, -1.2f, -0.8f, 0, 0.3f, 0, 0.1f, -0.1f, 0.2f), QuirkKeyframes.rest(14) };
	/** Sonic Cannon: both hands cupped at his mouth, and the kick of it drives him back. */
	private static final float[][] CANNON = { QuirkKeyframes.rest(0), f(2, -1.9f, -0.6f, 0, -1.9f, 0.6f, 0, -0.2f, 0, 0.1f, -0.1f, 0.15f),
			f(4, -1.75f, -0.55f, 0, -1.75f, 0.55f, 0, 0.35f, 0, -0.6f, 0.35f, -0.25f), f(13, -1.75f, -0.55f, 0, -1.75f, 0.55f, 0, 0.3f, 0, -0.5f, 0.3f, -0.2f), QuirkKeyframes.rest(18) };
	/** Shatter Note: one hand held at his ear for the pitch, the other points the note home. */
	private static final float[][] NOTE = { QuirkKeyframes.rest(0), f(3, -1.55f, -0.1f, 0, -2.5f, 0.1f, -0.75f, 0.05f, -0.25f, 0.15f, -0.15f, -0.1f),
			f(12, -1.6f, -0.1f, 0, -2.5f, 0.1f, -0.75f, 0.05f, -0.3f, 0.15f, -0.15f, -0.1f), QuirkKeyframes.rest(17) };
	/** Ultimate Chorus, the build-up: folded in, gathering it, shaking harder. */
	private static final float[][] CHARGE = { QuirkKeyframes.rest(0), f(5, -0.9f, 0.7f, 0, -0.9f, -0.7f, 0, 0.5f, 0, 0.3f, -0.3f, 0.4f),
			f(20, -1.1f, 0.8f, 0, -1.1f, -0.8f, 0, 0.6f, 0, 0.3f, -0.3f, 0.5f) };
	private static final float[][] CHORUS = { f(0, -1.1f, 0.8f, 0, -1.1f, -0.8f, 0, 0.6f, 0, 0.3f, -0.3f, 0.5f), f(3, 0.5f, 0, 1.5f, 0.5f, 0, -1.5f, -0.4f, 0, 0.45f, -0.45f, -0.6f),
			f(16, 0.5f, 0, 1.5f, 0.5f, 0, -1.5f, -0.35f, 0, 0.45f, -0.45f, -0.6f), QuirkKeyframes.rest(22) };
	/** Chorus Jab: a short, sharp punch. */
	private static final float[][] JAB = { QuirkKeyframes.rest(0), f(2, -1.7f, 0.15f, 0, 0.3f, 0, 0, 0.12f, 0.3f, -0.1f, 0.1f, 0), QuirkKeyframes.rest(7) };

	private static final FirstPersonClip FP_SCREAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(1.5f, -12, 10, 0, 0.1f, 0.05f, -0.05f),
			FirstPersonClip.f(3.5f, 28, -32, -25, -0.3f, -0.22f, 0.22f), FirstPersonClip.f(9, 28, -30, -25, -0.3f, -0.2f, 0.2f), FirstPersonClip.f(13, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_WALL = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 30, -36, -28, -0.34f, -0.25f, 0.25f),
			FirstPersonClip.f(40, 30, -36, -28, -0.34f, -0.25f, 0.25f), FirstPersonClip.f(44, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_WALL_END = FirstPersonClip.of(FirstPersonClip.f(0, 30, -36, -28, -0.34f, -0.25f, 0.25f), FirstPersonClip.f(7, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_RING = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 25, 30, 0, 0.3f, -0.12f, 0.08f),
			FirstPersonClip.f(4.5f, -10, -42, -30, -0.42f, 0.08f, 0f), FirstPersonClip.f(10, -8, -40, -28, -0.4f, 0.06f, 0f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_CANNON = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -30, 34, 0, 0.32f, 0.25f, -0.1f),
			FirstPersonClip.f(4, -26, 34, 0, 0.32f, 0.2f, 0.1f), FirstPersonClip.f(13, -26, 34, 0, 0.32f, 0.22f, 0.02f), FirstPersonClip.f(18, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_NOTE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -14, 10, 0, 0.1f, 0.16f, -0.34f),
			FirstPersonClip.f(12, -14, 10, 0, 0.1f, 0.16f, -0.34f), FirstPersonClip.f(17, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_RAISE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -48, 30, 12, 0.3f, 0.38f, 0.02f),
			FirstPersonClip.f(18, -48, 30, 12, 0.3f, 0.38f, 0.02f), FirstPersonClip.f(24, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_LEAP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 35, 10, 0, 0.05f, -0.42f, 0.2f),
			FirstPersonClip.f(5, 42, -12, -8, -0.1f, -0.48f, 0.3f), FirstPersonClip.f(14, 35, -8, -6, -0.05f, -0.4f, 0.22f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_CHARGE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(5, 18, 36, 0, 0.38f, -0.15f, 0.1f),
			FirstPersonClip.f(20, 24, 42, 0, 0.44f, -0.2f, 0.12f)).both();
	private static final FirstPersonClip FP_CHORUS = FirstPersonClip.of(FirstPersonClip.f(0, 24, 42, 0, 0.44f, -0.2f, 0.12f), FirstPersonClip.f(3, 20, -40, -30, -0.42f, -0.12f, 0.15f),
			FirstPersonClip.f(16, 20, -38, -28, -0.4f, -0.1f, 0.15f), FirstPersonClip.f(22, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_POINT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -34, -8, 0, -0.05f, 0.28f, -0.1f),
			FirstPersonClip.f(7, -12, 14, 0, 0.12f, 0.16f, -0.32f), FirstPersonClip.f(14, -12, 14, 0, 0.12f, 0.16f, -0.3f), FirstPersonClip.f(18, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SPREAD = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -10, -25, -20, -0.15f, 0.05f, 0f),
			FirstPersonClip.f(8, -10, -25, -20, -0.15f, 0.05f, 0f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_MERGE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -6, -30, -22, -0.3f, 0.05f, 0f),
			FirstPersonClip.f(9, 20, 34, 0, 0.36f, -0.12f, 0.05f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_GUARD = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -30, 35, 10, 0.25f, 0.25f, -0.15f),
			FirstPersonClip.f(56, -30, 35, 10, 0.25f, 0.25f, -0.15f), FirstPersonClip.f(60, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_GUARD_END = FirstPersonClip.of(FirstPersonClip.f(0, -30, 35, 10, 0.25f, 0.25f, -0.15f), FirstPersonClip.f(6, 0, 0, 0, 0, 0, 0)).both();

	private static boolean registered;
	/** How bright the rings being drawn are: ten clones' stacked at full strength wash out to solid white. */
	private static float strength = 1f;
	private static final float CLONE_WAVES = 0.35f;

	private EchoEchoClient() {
	}

	/** One line in {@code OmnitrixClient.initialize}. */
	public static void register() {
		AlienClients.register(Aliens.ECHO_ECHO.id(), new EchoEchoClient());
		if (!registered) {
			registered = true;
			EntityRendererRegistry.register(EchoEchoKit.CLONE, EchoCloneRenderer::new);
			WorldRenderEvents.AFTER_ENTITIES.register(EchoEchoClient::renderWorld);
		}
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	// v0.17.2 (user: "delete all the alien models and only use the skins i provided please no extra things untill i say"): no ear
	// discs, crest or back speaker any more -- Echo Echo (and every clone) is the player model in his skin, nothing else.

	/** The move pose for {@code animId} -- on him and on each clone. */
	public static boolean posed(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks) {
		float[][] clip = switch (animId) {
			case EchoEchoKit.ANIM_SCREAM -> SCREAM;
			case EchoEchoKit.ANIM_ASSAULT -> ticks < 14 ? POINT : SCREAM;
			case EchoEchoKit.ANIM_BLAST -> BLAST;
			case EchoEchoKit.ANIM_WALL_END -> WALL_END;
			case EchoEchoKit.ANIM_RING -> RING;
			case EchoEchoKit.ANIM_HUM -> HUM;
			case EchoEchoKit.ANIM_LEAP -> LEAP;
			case EchoEchoKit.ANIM_SWAP -> POP;
			case EchoEchoKit.ANIM_BARRIER -> BARRIER;
			case EchoEchoKit.ANIM_BARRIER_END -> BARRIER_END;
			case EchoEchoKit.ANIM_SPLIT -> SPLIT;
			case EchoEchoKit.ANIM_MERGE -> MERGE;
			case EchoEchoKit.ANIM_CANNON -> CANNON;
			case EchoEchoKit.ANIM_NOTE -> NOTE;
			case EchoEchoKit.ANIM_CHARGE -> CHARGE;
			case EchoEchoKit.ANIM_CHORUS -> CHORUS;
			case EchoEchoKit.ANIM_JAB -> JAB;
			default -> null;
		};
		float t = animId == EchoEchoKit.ANIM_ASSAULT && ticks >= 14 ? ticks - 12 : ticks;
		boolean on = clip != null && QuirkKeyframes.play(m, clip, t, 1f);
		if (on) {
			// the sound shakes him: a tremor through the head while it pours out
			float shake = switch (animId) {
				case EchoEchoKit.ANIM_SCREAM, EchoEchoKit.ANIM_CANNON, EchoEchoKit.ANIM_RING -> t > 3 && t < 10 ? 0.05f : 0f;
				case EchoEchoKit.ANIM_ASSAULT -> ticks >= 14 ? 0.05f : 0f;
				case EchoEchoKit.ANIM_BLAST, EchoEchoKit.ANIM_CHORUS -> t > 3 ? 0.07f : 0f;
				case EchoEchoKit.ANIM_CHARGE -> 0.02f + 0.08f * Mth.clamp(t / EchoEchoKit.CHARGE_TICKS, 0f, 1f);
				default -> 0f;
			};
			if (shake > 0f) {
				m.head.xRot += Mth.sin(ticks * 3.3f) * shake;
				m.head.yRot += Mth.cos(ticks * 2.7f) * shake;
				m.rightArm.zRot += Mth.sin(ticks * 3.7f) * shake;
				m.leftArm.zRot -= Mth.sin(ticks * 3.1f) * shake;
			}
		}
		return on;
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		return posed(p, m, animId, ticks);
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case EchoEchoKit.ANIM_SCREAM -> FP_SCREAM;
			case EchoEchoKit.ANIM_BLAST -> FP_WALL;
			case EchoEchoKit.ANIM_WALL_END -> FP_WALL_END;
			case EchoEchoKit.ANIM_RING -> FP_RING;
			case EchoEchoKit.ANIM_CANNON -> FP_CANNON;
			case EchoEchoKit.ANIM_NOTE -> FP_NOTE;
			case EchoEchoKit.ANIM_HUM -> FP_RAISE;
			case EchoEchoKit.ANIM_LEAP -> FP_LEAP;
			case EchoEchoKit.ANIM_CHARGE -> FP_CHARGE;
			case EchoEchoKit.ANIM_CHORUS -> FP_CHORUS;
			case EchoEchoKit.ANIM_ASSAULT -> FP_POINT;
			case EchoEchoKit.ANIM_SPLIT, EchoEchoKit.ANIM_SWAP -> FP_SPREAD;
			case EchoEchoKit.ANIM_MERGE -> FP_MERGE;
			case EchoEchoKit.ANIM_BARRIER -> FP_GUARD;
			case EchoEchoKit.ANIM_BARRIER_END -> FP_GUARD_END;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- the whole body

	private static float bell(float t, float from, float to) {
		return Mth.sin(Mth.clamp((t - from) / (to - from), 0f, 1f) * Mth.PI);
	}

	private static void squash(PoseStack pose, float k) {
		pose.scale(1f + k * 0.6f, 1f - k, 1f + k * 0.6f);
	}

	/** Pitch about his middle: positive throws him back, negative tips him forward. */
	private static void pivot(PoseStack pose, float pitch) {
		pose.translate(0f, 0.9f, 0f);
		pose.mulPose(Axis.XP.rotationDegrees(pitch));
		pose.translate(0f, -0.9f, 0f);
	}

	/** The whole body, {@code t} ticks into {@code animId}: y up, feet at the origin, 1.8 tall, facing -z. Shared with the clones. */
	public static void turn(PoseStack pose, int animId, float t) {
		switch (animId) {
			case EchoEchoKit.ANIM_SCREAM -> pivot(pose, 13f * bell(t, 2f, 13f));
			case EchoEchoKit.ANIM_ASSAULT -> pivot(pose, 11f * bell(t, 14f, 26f));
			case EchoEchoKit.ANIM_BLAST -> pivot(pose, 12f * Mth.clamp(t / 3f, 0f, 1f) + Mth.sin(t * 1.9f) * 1.5f);
			case EchoEchoKit.ANIM_WALL_END -> pivot(pose, 12f * (1f - Mth.clamp(t / 7f, 0f, 1f)));
			case EchoEchoKit.ANIM_RING -> squash(pose, 0.22f * bell(t, 0f, 4.5f) - 0.14f * bell(t, 4f, 12f));
			case EchoEchoKit.ANIM_LEAP -> {
				squash(pose, 0.28f * bell(t, 0f, 3.5f) - 0.16f * bell(t, 3f, 12f));
				pivot(pose, -38f * bell(t, 3f, 20f));
			}
			case EchoEchoKit.ANIM_SWAP -> squash(pose, 0.3f * Math.max(0f, 1f - t / 4f) - 0.12f * bell(t, 3f, 9f));
			case EchoEchoKit.ANIM_CANNON -> pivot(pose, 18f * bell(t, 3f, 18f));
			case EchoEchoKit.ANIM_NOTE -> pivot(pose, 6f * bell(t, 2f, 15f));
			case EchoEchoKit.ANIM_MERGE -> squash(pose, 0.12f * bell(t, 7f, 14f));
			case EchoEchoKit.ANIM_CHARGE -> {
				float k = Mth.clamp(t / EchoEchoKit.CHARGE_TICKS, 0f, 1f);
				squash(pose, 0.14f * k);
				pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(t * 2.6f) * (1f + 4f * k)));
			}
			case EchoEchoKit.ANIM_CHORUS -> {
				squash(pose, 0.14f * Math.max(0f, 1f - t / 2f) - 0.15f * bell(t, 1f, 16f));
				pivot(pose, 10f * bell(t, 1f, 20f));
			}
			default -> {
			}
		}
	}

	@Override
	public void bodyTurn(AbstractClientPlayer p, PoseStack pose, float partial) {
		if (Omnitrix.hasPower(p)) {
			OmnitrixState s = Omnitrix.state(p);
			if (s.animId != 0) {
				turn(pose, s.animId, p.level().getGameTime() - s.animStart + partial);
			}
		}
	}

	// ---------------------------------------------------------------- the world pass

	private static Vec3 aim(Player p, double range, float partial) {
		Vec3 eye = p.getEyePosition(partial);
		Vec3 end = eye.add(p.getViewVector(partial).scale(range));
		var hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	private static Vec3 mouth(Player p, float partial) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 eye = p.getEyePosition(partial);
		if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
			return eye.add(p.getViewVector(partial).scale(0.5)).add(0, -0.15, 0);
		}
		return eye.add(p.getViewVector(partial).scale(0.2)).add(0, -0.08, 0);
	}

	private static Vec3 cloneMouth(EchoCloneEntity c, float partial) {
		return c.getPosition(partial).add(0, c.getBbHeight() * 0.8, 0);
	}

	private static void renderWorld(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		MultiBufferSource buffers = context.consumers();
		Map<Integer, List<EchoCloneEntity>> clones = null;
		for (Player pl : mc.level.players()) {
			if (!Omnitrix.hasPower(pl)) {
				continue;
			}
			OmnitrixState st = Omnitrix.state(pl);
			if (!EchoEchoKit.ID.equals(st.active) || !st.transformed()) {
				continue;
			}
			if (clones == null) {
				clones = new HashMap<>();
				for (Entity e : mc.level.entitiesForRendering()) {
					if (e instanceof EchoCloneEntity c) {
						clones.computeIfAbsent(c.ownerId(), k -> new ArrayList<>()).add(c);
					}
				}
			}
			List<EchoCloneEntity> mine = clones.getOrDefault(pl.getId(), List.of());
			strength = 1f;
			float t = pl.level().getGameTime() - st.animStart + partial;
			float time = pl.tickCount + partial;
			switch (st.animId) {
				case EchoEchoKit.ANIM_SCREAM -> {
					if (t < 14) {
						Vec3 end = aim(pl, EchoEchoKit.SCREAM_RANGE, partial);
						waves(mouth(pl, partial), end, t, 14f, 0.25f, 1.2f, 1.3f, cam, pose, buffers);
						strength = CLONE_WAVES;
						for (EchoCloneEntity c : mine) {
							waves(cloneMouth(c, partial), end, t, 14f, 0.2f, 0.8f, 1.6f, cam, pose, buffers);
						}
					}
				}
				case EchoEchoKit.ANIM_BLAST -> {
					// v0.17.2 revamp: the Wall of Sound is held -- the waves keep rolling while its flag is up
					if ((st.kitFlags & EchoEchoKit.F_WALL) != 0) {
						t = t % 10f;
					}
					if (t < 16) {
						Vec3 m = mouth(pl, partial);
						Vec3 end = m.add(pl.getViewVector(partial).scale(EchoEchoKit.WALL_RANGE));
						waves(m, end, t, 16f, 0.4f, 4.6f, 1.0f, cam, pose, buffers);
						strength = CLONE_WAVES;
						for (EchoCloneEntity c : mine) {
							waves(cloneMouth(c, partial), end, t, 16f, 0.3f, 2.2f, 1.4f, cam, pose, buffers);
						}
					}
				}
				case EchoEchoKit.ANIM_CANNON -> {
					if (t < 18) {
						Vec3 m = mouth(pl, partial);
						Vec3 end = aim(pl, EchoEchoKit.CANNON_RANGE, partial);
						beam(m, end, t, 18f, cam, pose, buffers);
						waves(m, end, t, 18f, 0.5f, 0.6f, 0.7f, cam, pose, buffers);
						strength = CLONE_WAVES;
						for (EchoCloneEntity c : mine) {
							waves(cloneMouth(c, partial), end, t, 18f, 0.2f, 0.4f, 1.2f, cam, pose, buffers);
						}
					}
				}
				case EchoEchoKit.ANIM_RING -> {
					if (t < 14) {
						groundRings(pl.getPosition(partial), (float) EchoEchoKit.RING_RADIUS, t, 14f, cam, pose, buffers);
						strength = CLONE_WAVES;
						for (EchoCloneEntity c : mine) {
							groundRings(c.getPosition(partial), (float) EchoEchoKit.RING_CLONE_RADIUS, t, 14f, cam, pose, buffers);
						}
					}
				}
				case EchoEchoKit.ANIM_HUM -> {
					if (t < 24) {
						hum(pl, partial, t, cam, pose, buffers);
					}
				}
				case EchoEchoKit.ANIM_LEAP -> {
					if (t < 10) {
						groundRings(pl.getPosition(partial).add(0, -0.4, 0), 2.0f, t, 10f, cam, pose, buffers);
					}
				}
				case EchoEchoKit.ANIM_SPLIT, EchoEchoKit.ANIM_MERGE, EchoEchoKit.ANIM_SWAP -> {
					if (t < 10) {
						puff(pl.getPosition(partial).add(0, pl.getBbHeight() * 0.5, 0), t, 10f, cam, pose, buffers);
					}
				}
				case EchoEchoKit.ANIM_ASSAULT -> {
					long id = st.readyAt(EchoEchoKit.ID + "." + EchoEchoKit.AIM_TARGET);
					Entity tgt = id > 0 ? mc.level.getEntity((int) (id - 1)) : null;
					if (tgt != null && t < 18) {
						Vec3 at = tgt.getPosition(partial).add(0, tgt.getBbHeight() * 0.5, 0);
						strength = CLONE_WAVES;
						for (EchoCloneEntity c : mine) {
							waves(cloneMouth(c, partial), at, t, 18f, 0.25f, 0.9f, 1.8f, cam, pose, buffers);
						}
					}
				}
				case EchoEchoKit.ANIM_CHORUS -> {
					if (t < 18) {
						dome(pl.getPosition(partial).add(0, 0.5, 0), t, 18f, time, cam, pose, buffers);
					}
				}
				default -> {
				}
			}
			strength = 1f;
			for (EchoCloneEntity c : mine) {
				// a ring of sound as a clone comes out of him, and as one rushes back
				if (c.tickCount < 10) {
					puff(c.getPosition(partial).add(0, c.getBbHeight() * 0.5, 0), c.tickCount + partial, 10f, cam, pose, buffers);
				} else if (c.homing()) {
					puff(c.getPosition(partial).add(0, c.getBbHeight() * 0.5, 0), 7f, 10f, cam, pose, buffers);
				}
			}
			if ((st.kitFlags & EchoEchoKit.F_BARRIER) != 0) {
				shell(pl, partial, time, cam, pose, buffers);
			}
			if ((st.kitFlags & EchoEchoKit.F_CHARGE) != 0) {
				charge(pl, partial, t, cam, pose, buffers);
				for (EchoCloneEntity c : mine) {
					charge(c, partial, t, cam, pose, buffers);
				}
			}
		}
	}

	/**
	 * Sound waves: rings travelling from {@code from} to {@code to}, perpendicular to the way they go, widening from {@code r0} to
	 * {@code r1}, one every {@code spacing} blocks, fading out over {@code life} ticks.
	 */
	private static void waves(Vec3 from, Vec3 to, float t, float life, float r0, float r1, float spacing, Vec3 cam, PoseStack pose,
			MultiBufferSource buffers) {
		Vec3 d = to.subtract(from);
		float len = (float) d.length();
		if (len < 0.1f) {
			return;
		}
		float fade = t > life * 0.6f ? Math.max(0f, 1f - (t - life * 0.6f) / (life * 0.4f)) : 1f;
		float front = t * 2.6f; // how far the sound has gone
		VertexConsumer add = PyroDraw.additive(buffers);
		Vec3 o = from.subtract(cam);
		pose.pushPose();
		pose.translate(o.x, o.y, o.z);
		PyroDraw.alignY(pose, d);
		for (float s = front % spacing; s <= Math.min(front, len); s += spacing) {
			float k = s / len;
			float r = r0 + (r1 - r0) * k;
			float a = strength * fade * (1f - 0.6f * k);
			pose.pushPose();
			pose.translate(0, s, 0);
			PyroDraw.band(pose, add, r, 0.05f + 0.04f * k, PyroDraw.dim(WAVE, a), 0xFF000000);
			pose.popPose();
		}
		pose.popPose();
	}

	/** The Sonic Cannon's core: a bright line of compressed sound. */
	private static void beam(Vec3 from, Vec3 to, float t, float life, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		float out = Math.min(1f, t / 3f);
		float fade = t > life * 0.6f ? Math.max(0f, 1f - (t - life * 0.6f) / (life * 0.4f)) : 1f;
		Vec3 a = from.subtract(cam);
		Vec3 b = a.lerp(to.subtract(cam), out);
		List<Vec3> pts = List.of(a, a.lerp(b, 0.5), b);
		VertexConsumer add = PyroDraw.additive(buffers);
		PyroDraw.ribbon(pose, add, pts, u -> 0.4, u -> PyroDraw.dim(0xFF3A7A9A, fade));
		PyroDraw.ribbon(pose, add, pts, u -> 0.14, u -> PyroDraw.dim(0xFFE8FCFF, fade));
	}

	/** A ring of sound sweeping out along the ground to {@code radius}, a second one just behind it. */
	private static void groundRings(Vec3 at, float radius, float t, float life, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		Vec3 o = at.subtract(cam);
		VertexConsumer add = PyroDraw.additive(buffers);
		for (int i = 0; i < 2; i++) {
			float k = Mth.clamp((t - i * 2.5f) / (life * 0.7f), 0f, 1f);
			if (k <= 0f) {
				continue;
			}
			float r = radius * k;
			float a = strength * (1f - k);
			pose.pushPose();
			pose.translate(o.x, o.y + 0.3 + i * 0.25, o.z);
			PyroDraw.band(pose, add, r, 0.18f, PyroDraw.dim(WAVE, a), 0xFF000000);
			pose.popPose();
		}
	}

	/** The Disorient hum: slow rings pulsing out round his head and along the ground. */
	private static void hum(Player pl, float partial, float t, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		Vec3 head = pl.getEyePosition(partial).subtract(cam);
		VertexConsumer add = PyroDraw.additive(buffers);
		float fade = t > 18 ? 1f - (t - 18) / 6f : 1f;
		for (int i = 0; i < 3; i++) {
			float k = ((t / 8f) + i / 3f) % 1f;
			pose.pushPose();
			pose.translate(head.x, head.y, head.z);
			PyroDraw.band(pose, add, 0.3f + 1.2f * k, 0.04f, PyroDraw.dim(0xFFB8E0FF, fade * (1f - k)), 0xFF000000);
			pose.popPose();
		}
		groundRings(pl.getPosition(partial), 12f, t % 12f, 12f, cam, pose, buffers);
	}

	/** A small burst of rings (a clone splitting off / merging back / a swap). */
	private static void puff(Vec3 at, float t, float life, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		Vec3 o = at.subtract(cam);
		float k = t / life;
		VertexConsumer add = PyroDraw.additive(buffers);
		pose.pushPose();
		pose.translate(o.x, o.y, o.z);
		PyroDraw.band(pose, add, 0.2f + 0.7f * k, 0.25f * (1f - k), PyroDraw.dim(WAVE, 1f - k), 0xFF000000);
		pose.popPose();
	}

	/** The Sound Barrier: three rings spinning round him at different tilts, a faint shell between them. */
	private static void shell(Player pl, float partial, float time, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		Vec3 c = pl.getPosition(partial).add(0, pl.getBbHeight() * 0.5, 0).subtract(cam);
		VertexConsumer add = PyroDraw.additive(buffers);
		float r = 1.3f;
		pose.pushPose();
		pose.translate(c.x, c.y, c.z);
		PyroDraw.sphere(pose, add, r, 0xFF1A3440, 0xFF06101A, 0.06f, time * 0.5f, PyroDraw.FULL);
		for (int i = 0; i < 3; i++) {
			pose.pushPose();
			pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(time * 9f + i * 60f));
			pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(i * 60f + 30f));
			PyroDraw.band(pose, add, r + 0.02f, 0.05f, PyroDraw.dim(WAVE, 0.8f), 0xFF000000);
			pose.popPose();
		}
		pose.popPose();
	}

	/** The Ultimate Chorus' build-up: rings closing in round him (and each clone). */
	private static void charge(Entity e, float partial, float t, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		Vec3 c = e.getPosition(partial).add(0, e.getBbHeight() * 0.5, 0).subtract(cam);
		VertexConsumer add = PyroDraw.additive(buffers);
		float k = Mth.clamp(t / EchoEchoKit.CHARGE_TICKS, 0f, 1f);
		for (int i = 0; i < 3; i++) {
			float u = 1f - ((t / 7f + i / 3f) % 1f);
			pose.pushPose();
			pose.translate(c.x, c.y, c.z);
			PyroDraw.band(pose, add, 0.3f + 2.2f * u, 0.05f, PyroDraw.dim(WAVE, (0.3f + 0.7f * k) * (1f - u)), 0xFF000000);
			pose.popPose();
		}
	}

	/** The Ultimate Chorus: a dome of sound swelling out to its full radius. */
	private static void dome(Vec3 at, float t, float life, float time, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		float k = t / life;
		float r = EchoEchoKit.CHORUS_RADIUS * (float) Math.sin(Math.min(1.0, k * 1.5) * Math.PI * 0.5);
		float fade = 1f - k;
		Vec3 o = at.subtract(cam);
		VertexConsumer add = PyroDraw.additive(buffers);
		pose.pushPose();
		pose.translate(o.x, o.y, o.z);
		PyroDraw.sphere(pose, add, r, PyroDraw.dim(0xFF4A8AAA, fade * 0.6f), PyroDraw.dim(0xFF0A1A2A, fade * 0.6f), 0.05f, time, PyroDraw.FULL);
		for (int i = 0; i < 4; i++) {
			pose.pushPose();
			pose.translate(0, -0.4 + i * r * 0.2, 0);
			float rr = (float) Math.sqrt(Math.max(0.0, r * r - Math.pow(i * r * 0.2, 2)));
			PyroDraw.band(pose, add, rr, 0.12f, PyroDraw.dim(WAVE, fade), 0xFF000000);
			pose.popPose();
		}
		PyroDraw.annulus(pose, add, Math.max(0f, r - 1.5f), r, 0xFF000000, PyroDraw.dim(WAVE, fade), -0.45f);
		pose.popPose();
	}
}
