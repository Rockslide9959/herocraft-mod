package com.projecthero.mod.client.omnitrix.alien;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienBodyTurn;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.alien.ditto.DittoCopyRenderer;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.ditto.DittoCopyEntity;
import com.projecthero.mod.omnitrix.alien.ditto.DittoKit;
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
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Ditto's client kit (user: "Use this skin: https://www.planetminecraft.com/skin/ditto-ben-10-5303343/ remove the pixel
 * omnitrix from his skin model and replace it with our omnitrix badge please"). The user's skin on the player body at his small
 * size (x0.55), its painted forehead Omnitrix painted out ({@code scratchpad/ben10_v0171/gen_ditto.js}) and the mod's badge on his
 * forehead fin in its place -- like the show, where Ditto wears the hourglass on his forehead. His copies
 * ({@link DittoCopyRenderer}) are the same body, badge and all.
 * <p>
 * v0.17.2 round 3 (user: "edit and update animations on all moves please"): every move has its own third- and first-person clip, and
 * the big ones move the whole body ({@link #turn}): the Pinwheel Kick is a real spin (user: "right now his torso just twists").
 */
public final class DittoClient implements AlienClientKit, AlienBodyTurn {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/ditto.png");
	/** His body scale (the kit's stat), for a copy whose owner is out of sight. */
	public static final float SIZE = 0.55f;
	/** The forehead badge (head-bone pixels: the cube spans x -4..4, y -8..0, z -4..4; just in front of the hat layer's fin). */
	public static final float[] BADGE = { 0f, -6.0f, -4.56f, 3.0f };
	/** The Pinwheel Kick's length, and the part of it he spends flat out. */
	public static final float KICK_TICKS = 18f, SPIN_FROM = 2f, SPIN_TO = 15f;

	private static boolean registered;

	public static void register() {
		AlienClients.register(Aliens.DITTO.id(), new DittoClient());
		if (!registered) {
			registered = true;
			EntityRendererRegistry.register(DittoKit.COPY, DittoCopyRenderer::new);
			WorldRenderEvents.AFTER_ENTITIES.register(DittoClient::renderWorld);
		}
	}

	// ---------------------------------------------------------------- poses (QuirkKeyframes: tick, rArm xyz, lArm xyz, body xy, rLeg xyz, lLeg xyz, headX)
	private static float[] f(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float by, float rlx, float llx) {
		return QuirkKeyframes.f(t, rax, ray, raz, lax, lay, laz, bx, by, rlx, 0, 0, llx, 0, 0, 0);
	}

	/** His melee: a quick jab, the shoulder behind it. */
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0), f(2, -1.7f, 0.2f, 0, 0.3f, 0, 0, 0.12f, 0.35f, 0, 0), QuirkKeyframes.rest(6) };
	/**
	 * Pinwheel Kick: he tucks, then throws himself flat and spins on the kick -- one leg driven out straight, the other knee tucked,
	 * the arms flung wide for balance. The limbs hold while {@link #turn} spins the whole of him.
	 */
	private static final float[][] KICK = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.9f, 0, -0.3f, -0.9f, 0, 0.3f, 0.4f, 0, -0.9f, 0, 0, -0.9f, 0, 0, 0.15f),
			QuirkKeyframes.f(5, -0.25f, 0, 1.45f, -0.25f, 0, -1.45f, 0, 0, -0.1f, 0, 0.75f, -1.75f, 0, -0.1f, -0.2f),
			QuirkKeyframes.f(13, -0.25f, 0, 1.45f, -0.25f, 0, -1.45f, 0, 0, -0.1f, 0, 0.75f, -1.75f, 0, -0.1f, -0.2f), QuirkKeyframes.rest(KICK_TICKS) };
	/** Split: he braces and strains as something pulls out of him, then jolts upright as it parts. */
	private static final float[][] SPLIT = { QuirkKeyframes.rest(0), f(3, -0.5f, 0, 0.7f, -0.5f, 0, -0.7f, 0.3f, 0, 0.35f, -0.35f),
			f(8, -0.2f, 0, 1.3f, -0.2f, 0, -1.3f, -0.1f, 0.25f, 0.45f, -0.45f), f(11, -0.1f, 0, 1.45f, -0.1f, 0, -1.45f, -0.3f, -0.15f, 0.3f, -0.3f),
			f(14, -0.2f, 0, 0.6f, -0.2f, 0, -0.6f, 0.15f, 0, 0.1f, -0.1f), QuirkKeyframes.rest(19) };
	/** A copy going in: he hugs himself as it sinks in, then shakes it off. */
	private static final float[][] MERGE = { QuirkKeyframes.rest(0), f(3, -1.0f, 0.5f, -0.3f, -1.0f, -0.5f, 0.3f, 0.3f, 0, 0.1f, -0.1f),
			f(9, -1.2f, 0.7f, -0.4f, -1.2f, -0.7f, 0.4f, 0.4f, 0, 0.1f, -0.1f), f(12, -0.1f, 0, 0.5f, -0.1f, 0, -0.5f, -0.2f, 0, 0, 0), QuirkKeyframes.rest(16) };
	/** Merge: he beckons the nearest copy in. */
	private static final float[][] CALL = { QuirkKeyframes.rest(0), f(3, -1.5f, -0.2f, 0, 0.1f, 0, -0.1f, 0.05f, -0.2f, 0, 0), f(7, -2.1f, -0.2f, 0, 0.1f, 0, -0.1f, -0.05f, -0.2f, 0, 0),
			f(10, -1.5f, -0.2f, 0, 0.1f, 0, -0.1f, 0.05f, -0.2f, 0, 0), QuirkKeyframes.rest(14) };
	private static final float[][] LEAP = { QuirkKeyframes.rest(0), f(2, 0.8f, 0, 0.3f, 0.8f, 0, -0.3f, 0.45f, 0, -1.0f, -1.0f),
			f(5, -2.9f, 0, 0.15f, -2.9f, 0, -0.15f, 0.1f, 0, 0.5f, -0.3f), f(13, -2.9f, 0, 0.15f, -2.9f, 0, -0.15f, 0.05f, 0, 0.4f, -0.2f), QuirkKeyframes.rest(19) };
	/** Swarm Strike: "get it" -- a throw of the arm, and they go. */
	private static final float[][] SIC = { QuirkKeyframes.rest(0), f(3, -2.4f, 0.2f, 0, 0.4f, 0, -0.2f, -0.1f, 0.4f, 0.2f, -0.2f),
			f(6, -1.45f, -0.25f, 0, 0.6f, 0, -0.3f, 0.3f, -0.35f, -0.2f, 0.2f), f(12, -1.5f, -0.2f, 0, 0.3f, 0, -0.3f, 0.2f, -0.3f, -0.1f, 0.1f), QuirkKeyframes.rest(16) };
	/** Dogpile: both arms drive forward, sending the pack in. */
	private static final float[][] SHOVE = { QuirkKeyframes.rest(0), f(3, -0.7f, 0.5f, 0, -0.7f, -0.5f, 0, -0.1f, 0, 0.3f, -0.3f),
			f(6, -1.65f, -0.15f, 0, -1.65f, 0.15f, 0, 0.35f, 0, -0.45f, 0.35f), f(13, -1.55f, -0.15f, 0, -1.55f, 0.15f, 0, 0.25f, 0, -0.35f, 0.3f), QuirkKeyframes.rest(17) };
	/** Pile-Up: the arm goes up, the pack goes up; it chops down, they come down. */
	private static final float[][] CHOP = { QuirkKeyframes.rest(0), f(3, -2.95f, 0.1f, 0, 0.2f, 0, -0.2f, -0.2f, 0.2f, 0.1f, -0.1f),
			f(10, -2.95f, 0.1f, 0, 0.2f, 0, -0.2f, -0.2f, 0.2f, 0.1f, -0.1f), f(14, -0.9f, 0, 0, 0.3f, 0, -0.2f, 0.45f, -0.25f, -0.2f, 0.2f), QuirkKeyframes.rest(19) };
	/** Split Burst: he balls up, then explodes out of it. */
	private static final float[][] BURST = { QuirkKeyframes.rest(0), f(2, -1.0f, 0.5f, -0.4f, -1.0f, -0.5f, 0.4f, 0.6f, 0, -0.9f, -0.9f),
			f(5, 0.2f, 0, 1.75f, 0.2f, 0, -1.75f, -0.35f, 0, 0.55f, -0.55f), f(9, 0.1f, 0, 1.5f, 0.1f, 0, -1.5f, -0.2f, 0, 0.4f, -0.4f), QuirkKeyframes.rest(14) };
	/** Ditto Army: arms up, straining, the whole pack pouring out of him. */
	private static final float[][] ARMY = { QuirkKeyframes.rest(0), f(3, -0.9f, 0, 0.8f, -0.9f, 0, -0.8f, 0.4f, 0, 0.5f, -0.5f),
			f(8, -2.7f, 0, 0.5f, -2.7f, 0, -0.5f, -0.3f, 0, 0.4f, -0.4f), f(24, -2.8f, 0, 0.45f, -2.8f, 0, -0.45f, -0.3f, 0, 0.4f, -0.4f), QuirkKeyframes.rest(30) };
	/** Copy Toss: he crouches onto the copy's hands, then springs off them flat out. */
	private static final float[][] TOSS = { QuirkKeyframes.rest(0), f(4, 0.5f, 0, 0.3f, 0.5f, 0, -0.3f, 0.55f, 0, -1.1f, -1.1f),
			f(7, -2.9f, 0, 0.15f, -2.9f, 0, -0.15f, 0.05f, 0, 0.3f, -0.2f), f(16, -2.9f, 0, 0.15f, -2.9f, 0, -0.15f, 0.05f, 0, 0.3f, -0.2f), QuirkKeyframes.rest(22) };
	/** Shell Game: a duck and a weave, gone. */
	private static final float[][] SHUFFLE = { QuirkKeyframes.rest(0), f(2, -1.5f, 0.6f, 0, -1.5f, -0.6f, 0, 0.5f, 0.5f, -0.5f, -0.5f),
			f(5, -1.0f, -0.6f, 0, -1.0f, 0.6f, 0, 0.3f, -0.5f, -0.3f, -0.3f), QuirkKeyframes.rest(10) };
	/** Switcheroo: hoists the copy in both hands, leans back, and heaves it. */
	private static final float[][] THROW = { QuirkKeyframes.rest(0), f(3, -3.0f, 0, 0.2f, -3.0f, 0, -0.2f, -0.05f, 0, 0.2f, -0.2f),
			f(7, -3.1f, 0, 0.2f, -3.1f, 0, -0.2f, -0.35f, 0, 0.3f, -0.35f), f(10, -1.2f, 0, 0.1f, -1.2f, 0, -0.1f, 0.45f, 0, -0.45f, 0.35f),
			f(14, -0.9f, 0, 0.1f, -0.9f, 0, -0.1f, 0.3f, 0, -0.3f, 0.2f), QuirkKeyframes.rest(18) };
	/** Switcheroo, the other end: he lands where it landed, in a crouch. */
	private static final float[][] ARRIVE = { QuirkKeyframes.f(0, 0.2f, 0, 0.9f, 0.2f, 0, -0.9f, 0.5f, 0, -0.8f, 0, 0, -0.8f, 0, 0, 0.1f),
			QuirkKeyframes.f(5, 0.1f, 0, 0.6f, 0.1f, 0, -0.6f, 0.3f, 0, -0.4f, 0, 0, -0.4f, 0, 0, 0), QuirkKeyframes.rest(11) };

	// a copy's own: how it pulls out of him and sinks back in (eye of the storm: it faces away from him, then toward him)
	private static final float[][] PULLING = { QuirkKeyframes.f(0, -2.4f, 0, 0.2f, -2.4f, 0, -0.2f, 0.3f, 0, 0.1f, 0, 0, -0.1f, 0, 0, -0.2f),
			QuirkKeyframes.f(10, -2.0f, 0, 0.25f, -2.0f, 0, -0.25f, 0.35f, 0, 0.75f, 0, 0, -0.35f, 0, 0, -0.35f),
			QuirkKeyframes.f(14, -1.4f, 0, 0.95f, -1.4f, 0, -0.95f, -0.2f, 0, -0.55f, 0, 0, 0.5f, 0, 0, 0.1f),
			QuirkKeyframes.f(16, -0.5f, 0, 0.5f, -0.5f, 0, -0.5f, 0.1f, 0, 0.1f, 0, 0, -0.1f, 0, 0, 0), QuirkKeyframes.rest(18) };
	private static final float[][] SINKING = { QuirkKeyframes.rest(0), f(4, -1.5f, 0, 0.15f, -1.5f, 0, -0.15f, 0.35f, 0, 0.1f, -0.1f),
			f(10, -1.9f, 0, 0.1f, -1.9f, 0, -0.1f, 0.5f, 0, 0.7f, 0.5f) };

	private static final FirstPersonClip FP_SIC = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -40, 5, 0, 0.05f, 0.3f, 0f),
			FirstPersonClip.f(6, -14, 12, 0, 0.1f, 0.12f, -0.32f), FirstPersonClip.f(12, -14, 12, 0, 0.1f, 0.12f, -0.3f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SHOVE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 5, 10, 0, 0.1f, -0.05f, 0.15f),
			FirstPersonClip.f(6, -18, 22, 0, 0.22f, 0.15f, -0.38f), FirstPersonClip.f(13, -18, 22, 0, 0.22f, 0.15f, -0.34f), FirstPersonClip.f(17, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_CHOP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -75, 5, 0, 0.1f, 0.55f, 0.05f),
			FirstPersonClip.f(10, -75, 5, 0, 0.1f, 0.55f, 0.05f), FirstPersonClip.f(14, 25, 8, 0, 0.1f, -0.15f, -0.2f), FirstPersonClip.f(19, 0, 0, 0, 0, 0, 0));
	/** The kick: he cannot see his legs, so his arms whip across as he goes round. */
	private static final FirstPersonClip FP_KICK = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 15, 15, 0, 0.1f, -0.1f, 0.1f),
			FirstPersonClip.f(5, -20, -55, -30, -0.35f, 0.1f, 0f), FirstPersonClip.f(9, -20, 55, 30, 0.45f, 0.1f, 0f), FirstPersonClip.f(13, -20, -55, -30, -0.35f, 0.1f, 0f),
			FirstPersonClip.f(KICK_TICKS, 0, 0, 0, 0, 0, 0)).both();
	/** The split: both hands take hold and haul apart. */
	private static final FirstPersonClip FP_SPLIT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -25, 28, 0, 0.3f, 0.05f, -0.15f),
			FirstPersonClip.f(10, -18, -22, -18, -0.22f, 0.1f, -0.1f), FirstPersonClip.f(14, -5, -30, -25, -0.32f, 0.02f, 0f), FirstPersonClip.f(19, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_CALL = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -28, 12, 0, 0.1f, 0.18f, -0.22f),
			FirstPersonClip.f(7, -58, 12, 0, 0.1f, 0.3f, -0.05f), FirstPersonClip.f(10, -28, 12, 0, 0.1f, 0.18f, -0.22f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_MERGE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, 20, 30, 0, 0.32f, -0.1f, 0.05f),
			FirstPersonClip.f(10, 25, 34, 0, 0.36f, -0.14f, 0.05f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_LEAP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 30, 5, 0, 0.05f, -0.35f, 0.15f),
			FirstPersonClip.f(5, -40, 10, 0, 0.12f, 0.3f, -0.25f), FirstPersonClip.f(13, -40, 10, 0, 0.12f, 0.3f, -0.25f), FirstPersonClip.f(19, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_BURST = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 25, 30, 0, 0.3f, -0.15f, 0.1f),
			FirstPersonClip.f(5, -15, -40, -30, -0.4f, 0.1f, 0f), FirstPersonClip.f(9, -12, -38, -28, -0.38f, 0.08f, 0f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_ARMY = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 15, -15, -10, -0.15f, -0.1f, 0f),
			FirstPersonClip.f(8, -68, -8, 8, -0.05f, 0.5f, 0.05f), FirstPersonClip.f(24, -70, -8, 8, -0.05f, 0.52f, 0.05f), FirstPersonClip.f(30, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_TOSS = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, 30, 5, 0, 0.05f, -0.35f, 0.15f),
			FirstPersonClip.f(7, -55, 8, 0, 0.1f, 0.42f, -0.25f), FirstPersonClip.f(16, -55, 8, 0, 0.1f, 0.42f, -0.25f), FirstPersonClip.f(22, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SHUFFLE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -20, 34, 8, 0.34f, 0.1f, -0.1f),
			FirstPersonClip.f(5, -14, -30, -8, -0.3f, 0.06f, -0.1f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_THROW = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -72, 6, 0, 0.1f, 0.55f, 0.05f),
			FirstPersonClip.f(7, -85, 6, 0, 0.1f, 0.62f, 0.12f), FirstPersonClip.f(10, -8, 14, 0, 0.15f, 0.05f, -0.42f), FirstPersonClip.f(14, -5, 14, 0, 0.15f, 0.02f, -0.34f),
			FirstPersonClip.f(18, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_ARRIVE = FirstPersonClip.of(FirstPersonClip.f(0, 30, -20, -15, -0.2f, -0.2f, 0.1f), FirstPersonClip.f(11, 0, 0, 0, 0, 0, 0)).both();

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public float[] badge() {
		// v0.17.2 (user: "remove the pixel omnitrix from his skin model and replace it with our omnitrix badge please"): where the skin
		// painted it, on his forehead fin
		return BADGE;
	}

	@Override
	public int badgeBone() {
		return BADGE_HEAD;
	}

	/** The pose for {@code animId}, {@code ticks} in -- shared with the copies, which spin with him. */
	public static boolean posed(HumanoidModel<?> m, int animId, float ticks) {
		float[][] clip = switch (animId) {
			case DittoKit.ANIM_PUNCH -> PUNCH;
			case DittoKit.ANIM_KICK -> KICK;
			case DittoKit.ANIM_SPLIT -> SPLIT;
			case DittoKit.ANIM_MERGE -> MERGE;
			case DittoKit.ANIM_CALL -> CALL;
			case DittoKit.ANIM_LEAP -> LEAP;
			case DittoKit.ANIM_SIC -> SIC;
			case DittoKit.ANIM_SHOVE -> SHOVE;
			case DittoKit.ANIM_CHOP -> CHOP;
			case DittoKit.ANIM_BURST -> BURST;
			case DittoKit.ANIM_ARMY -> ARMY;
			case DittoKit.ANIM_TOSS -> TOSS;
			case DittoKit.ANIM_SHUFFLE -> SHUFFLE;
			case DittoKit.ANIM_THROW -> THROW;
			case DittoKit.ANIM_ARRIVE -> ARRIVE;
			default -> null;
		};
		boolean on = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		if (on && animId == DittoKit.ANIM_ARMY && ticks > 8f && ticks < 24f) {
			// every copy that tears out jolts him
			m.body.yRot += Mth.sin(ticks * 2.6f) * 0.12f;
			m.rightArm.xRot += Mth.sin(ticks * 3.1f) * 0.1f;
			m.leftArm.xRot += Mth.cos(ticks * 2.9f) * 0.1f;
		}
		return on;
	}

	/** A copy, {@code u} (0..1) of the way through pulling out of him. */
	public static void pulling(HumanoidModel<?> m, float u) {
		QuirkKeyframes.apply(m, QuirkKeyframes.sample(PULLING, u * 18f * 0.999f), 1f);
	}

	/** A copy, {@code u} (0..1) of the way through sinking back in. */
	public static void sinking(HumanoidModel<?> m, float u) {
		QuirkKeyframes.apply(m, QuirkKeyframes.sample(SINKING, u * 10f * 0.999f), 1f);
	}

	/** A copy heaving him up (Copy Toss), {@code ticks} after it starts: from its knees to the sky. */
	public static void heave(HumanoidModel<?> m, float ticks) {
		float k = Mth.clamp(ticks / 3f, 0f, 1f);
		float out = 1f - Mth.clamp((ticks - 5f) / 3f, 0f, 1f);
		m.rightArm.xRot = Mth.lerp(k, 0.3f, -3.0f) * out;
		m.leftArm.xRot = Mth.lerp(k, 0.3f, -3.0f) * out;
		m.rightLeg.xRot = -0.4f * (1f - k) * out;
		m.leftLeg.xRot = 0.3f * (1f - k) * out;
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		if (posed(m, animId, ticks)) {
			return true;
		}
		// Wall Crawl: hand over hand up the wall
		if (p == Minecraft.getInstance().player && p.horizontalCollision && !p.onGround() && p.getDeltaMovement().y > 0.02) {
			m.rightArm.xRot = -2.6f + Mth.sin(age * 0.8f) * 0.4f;
			m.leftArm.xRot = -2.6f - Mth.sin(age * 0.8f) * 0.4f;
			m.rightLeg.xRot = -Mth.sin(age * 0.8f) * 0.6f;
			m.leftLeg.xRot = Mth.sin(age * 0.8f) * 0.6f;
			return true;
		}
		return false;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case DittoKit.ANIM_SIC -> FP_SIC;
			case DittoKit.ANIM_SHOVE -> FP_SHOVE;
			case DittoKit.ANIM_CHOP -> FP_CHOP;
			case DittoKit.ANIM_KICK -> FP_KICK;
			case DittoKit.ANIM_SPLIT -> FP_SPLIT;
			case DittoKit.ANIM_CALL -> FP_CALL;
			case DittoKit.ANIM_MERGE -> FP_MERGE;
			case DittoKit.ANIM_LEAP -> FP_LEAP;
			case DittoKit.ANIM_BURST -> FP_BURST;
			case DittoKit.ANIM_ARMY -> FP_ARMY;
			case DittoKit.ANIM_TOSS -> FP_TOSS;
			case DittoKit.ANIM_SHUFFLE -> FP_SHUFFLE;
			case DittoKit.ANIM_THROW -> FP_THROW;
			case DittoKit.ANIM_ARRIVE -> FP_ARRIVE;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- the whole body

	private static float bell(float t, float from, float to) {
		return Mth.sin(Mth.clamp((t - from) / (to - from), 0f, 1f) * Mth.PI);
	}

	private static float ease(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	/** Squash about the feet: {@code k} above 0 flattens and widens him, below 0 stretches him tall. */
	private static void squash(PoseStack pose, float k) {
		pose.scale(1f + k * 0.6f, 1f - k, 1f + k * 0.6f);
	}

	private static void pivot(PoseStack pose, float pitch) {
		pose.translate(0f, 0.9f, 0f);
		pose.mulPose(Axis.XP.rotationDegrees(pitch));
		pose.translate(0f, -0.9f, 0f);
	}

	/** The whole body, {@code t} ticks into {@code animId}: y up, feet at the origin, 1.8 tall, facing -z. Shared with the copies. */
	public static void turn(PoseStack pose, int animId, float t) {
		switch (animId) {
			case DittoKit.ANIM_KICK -> {
				if (t < SPIN_FROM || t > SPIN_TO + 2f) {
					return;
				}
				// two whole turns, tipped nearly flat so the leg sweeps round him at knee height
				float k = (t - SPIN_FROM) / (SPIN_TO - SPIN_FROM);
				float spin = 720f * ease(Math.min(1f, k));
				float tilt = 74f * Math.min(ease(k * 3.5f), ease((SPIN_TO + 2f - t) / 4f));
				pose.mulPose(Axis.YP.rotationDegrees(-spin));
				pose.translate(0f, 0.9f + 0.18f * bell(t, SPIN_FROM, SPIN_TO + 2f), 0f);
				pose.mulPose(Axis.ZP.rotationDegrees(tilt));
				pose.translate(0f, -0.9f, 0f);
			}
			case DittoKit.ANIM_SPLIT -> {
				float wobble = t > 11f ? Mth.sin((t - 11f) * 1.2f) * Math.max(0f, 1f - (t - 11f) / 8f) * 0.09f : 0f;
				squash(pose, 0.16f * bell(t, 0f, 14f) + wobble);
			}
			case DittoKit.ANIM_MERGE -> squash(pose, 0.14f * bell(t, 2f, 9f) - 0.08f * bell(t, 9f, 15f));
			case DittoKit.ANIM_BURST -> squash(pose, 0.3f * bell(t, 0f, 4f) - 0.2f * bell(t, 3f, 11f));
			case DittoKit.ANIM_ARMY -> squash(pose, 0.1f * bell(t, 0f, 8f) + (t > 8f && t < 24f ? Mth.sin(t * 2.4f) * 0.05f : 0f));
			case DittoKit.ANIM_LEAP -> {
				squash(pose, 0.25f * bell(t, 0f, 3.5f));
				pivot(pose, -62f * bell(t, 2f, 19f));
			}
			case DittoKit.ANIM_TOSS -> {
				squash(pose, 0.25f * bell(t, 0f, 6f));
				pivot(pose, -72f * bell(t, 5f, 22f));
			}
			case DittoKit.ANIM_THROW -> pivot(pose, 16f * bell(t, 2f, 8f) - 30f * bell(t, 7f, 17f));
			case DittoKit.ANIM_SHUFFLE -> {
				pose.mulPose(Axis.YP.rotationDegrees(360f * ease(t / 8f)));
				squash(pose, 0.2f * bell(t, 0f, 8f));
			}
			case DittoKit.ANIM_ARRIVE -> squash(pose, 0.26f * Math.max(0f, 1f - t / 8f));
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

	// ---------------------------------------------------------------- the world pass: the kick's swoosh

	private static void renderWorld(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		List<Entity> copies = null;
		for (Player pl : mc.level.players()) {
			if (!Omnitrix.hasPower(pl)) {
				continue;
			}
			OmnitrixState st = Omnitrix.state(pl);
			if (!DittoKit.ID.equals(st.active) || !st.transformed() || st.animId != DittoKit.ANIM_KICK) {
				continue;
			}
			float t = pl.level().getGameTime() - st.animStart + partial;
			if (t < SPIN_FROM || t > SPIN_TO + 2f) {
				continue;
			}
			swoosh(pl.getPosition(partial), 2.2, pl.yBodyRot, t, cam, context.matrixStack(), context.consumers());
			if (copies == null) {
				copies = new ArrayList<>();
				for (Entity e : mc.level.entitiesForRendering()) {
					if (e instanceof DittoCopyEntity c && c.ownerId() == pl.getId() && !c.busy() && c.state() == DittoCopyEntity.NORMAL) {
						copies.add(c);
					}
				}
			}
			for (Entity c : copies) {
				swoosh(c.getPosition(partial), 1.6, c.getYRot(), t, cam, context.matrixStack(), context.consumers());
			}
		}
	}

	/** A crescent of air trailing the foot as it comes round: bright at the foot, gone a third of a turn behind it. */
	private static void swoosh(Vec3 at, double radius, float yaw, float t, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		float k = Mth.clamp((t - SPIN_FROM) / (SPIN_TO - SPIN_FROM), 0f, 1f);
		float foot = (yaw + 90f - 720f * ease(k)) * Mth.DEG_TO_RAD;
		float fade = Math.min(1f, Math.min((t - SPIN_FROM) / 2f, (SPIN_TO + 2f - t) / 3f));
		Vec3 c = at.add(0, 0.42, 0).subtract(cam);
		List<Vec3> pts = new ArrayList<>();
		int n = 18;
		for (int i = 0; i <= n; i++) {
			double a = foot + i / (double) n * 2.4;
			pts.add(c.add(Math.cos(a) * radius, Math.sin(i * 0.6) * 0.03, Math.sin(a) * radius));
		}
		VertexConsumer add = PyroDraw.additive(buffers);
		PyroDraw.ribbon(pose, add, pts, u -> 0.14 * (1.0 - u), u -> PyroDraw.dim(0xFFD9E8F6, fade * (float) (1.0 - u) * 0.22f));
		PyroDraw.ribbon(pose, add, pts, u -> 0.04 * (1.0 - u), u -> PyroDraw.dim(0xFFFFFFFF, fade * (float) (1.0 - u) * 0.5f));
	}
}
