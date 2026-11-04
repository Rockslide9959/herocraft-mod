package com.projecthero.mod.client.omnitrix.alien;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.alien.ditto.DittoCopyRenderer;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.ditto.DittoKit;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.2 Ditto's client kit (user: "Use this skin: https://www.planetminecraft.com/skin/ditto-ben-10-5303343/ remove the pixel
 * omnitrix from his skin model and replace it with our omnitrix badge please"). The user's skin on the player body at his small
 * size (x0.55), its painted forehead Omnitrix painted out ({@code scratchpad/ben10_v0171/gen_ditto.js}) and the mod's badge on his
 * forehead fin in its place -- like the show, where Ditto wears the hourglass on his forehead. His copies
 * ({@link DittoCopyRenderer}) are the same body, badge and all.
 */
public final class DittoClient implements AlienClientKit {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/ditto.png");
	/** His body scale (the kit's stat), for a copy whose owner is out of sight. */
	public static final float SIZE = 0.55f;
	/** The forehead badge (head-bone pixels: the cube spans x -4..4, y -8..0, z -4..4; just in front of the hat layer's fin). */
	public static final float[] BADGE = { 0f, -6.0f, -4.56f, 3.0f };

	private static boolean registered;

	public static void register() {
		AlienClients.register(Aliens.DITTO.id(), new DittoClient());
		if (!registered) {
			registered = true;
			EntityRendererRegistry.register(DittoKit.COPY, DittoCopyRenderer::new);
		}
	}

	// ---------------------------------------------------------------- poses (QuirkKeyframes: tick, rArm xyz, lArm xyz, body xy, rLeg xyz, lLeg xyz, headX)
	private static float[] f(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float by, float rlx, float llx) {
		return QuirkKeyframes.f(t, rax, ray, raz, lax, lay, laz, bx, by, rlx, 0, 0, llx, 0, 0, 0);
	}

	private static final float[][] PUNCH = { QuirkKeyframes.rest(0), f(2, -1.6f, 0.2f, 0, 0, 0, 0, 0.1f, 0, 0, 0), QuirkKeyframes.rest(6) };
	/** Pinwheel Kick: a full spin on one leg, the other kicked out. */
	private static final float[][] KICK = { QuirkKeyframes.rest(0), f(2, 0, 0, 1.3f, 0, 0, -1.3f, 0, 1.6f, -1.4f, 0),
			f(4, 0, 0, 1.3f, 0, 0, -1.3f, 0, 3.2f, -1.4f, 0), f(6, 0, 0, 1.3f, 0, 0, -1.3f, 0, 4.8f, -1.4f, 0),
			f(8, 0, 0, 1.3f, 0, 0, -1.3f, 0, 6.28f, -1.4f, 0), QuirkKeyframes.rest(11) };
	/** Split: arms flung wide, body arched -- a copy pops out. */
	private static final float[][] SPLIT = { QuirkKeyframes.rest(0), f(3, 0, 0, 1.2f, 0, 0, -1.2f, -0.25f, 0, 0.3f, -0.3f),
			f(7, 0, 0, 1.0f, 0, 0, -1.0f, -0.2f, 0, 0.25f, -0.25f), QuirkKeyframes.rest(11) };
	private static final float[][] MERGE = { QuirkKeyframes.rest(0), f(3, -0.8f, 0, -0.5f, -0.8f, 0, 0.5f, 0.35f, 0, 0, 0),
			f(9, -0.8f, 0, -0.5f, -0.8f, 0, 0.5f, 0.35f, 0, 0, 0), QuirkKeyframes.rest(13) };
	private static final float[][] LEAP = { QuirkKeyframes.rest(0), f(2, -2.6f, 0, 0.2f, -2.6f, 0, -0.2f, 0.2f, 0, 0.6f, -0.6f),
			f(10, 0.6f, 0, 0.5f, 0.6f, 0, -0.5f, 0.35f, 0, -0.9f, -0.9f), QuirkKeyframes.rest(16) };
	/** Swarm Strike / Dogpile / Pile-Up: points the pack at it. */
	private static final float[][] POINT = { QuirkKeyframes.rest(0), f(3, -1.6f, -0.1f, 0, 0.2f, 0, -0.3f, 0.1f, -0.2f, 0, 0),
			f(10, -1.6f, -0.1f, 0, 0.2f, 0, -0.3f, 0.1f, -0.2f, 0, 0), QuirkKeyframes.rest(14) };
	private static final float[][] BURST = { QuirkKeyframes.rest(0), f(2, -0.6f, 0, -0.4f, -0.6f, 0, 0.4f, 0.45f, 0, 0, 0),
			f(5, 0.2f, 0, 1.6f, 0.2f, 0, -1.6f, -0.3f, 0, 0.5f, -0.5f), QuirkKeyframes.rest(12) };
	private static final float[][] ARMY = { QuirkKeyframes.rest(0), f(3, -2.9f, 0, 0.3f, -2.9f, 0, -0.3f, -0.25f, 0, 0, 0),
			f(14, -2.9f, 0, 0.3f, -2.9f, 0, -0.3f, -0.25f, 0, 0, 0), QuirkKeyframes.rest(20) };
	private static final float[][] TOSS = { QuirkKeyframes.rest(0), f(3, -2.8f, 0, 0.2f, -2.8f, 0, -0.2f, -0.3f, 0, -0.4f, -0.4f),
			f(12, -2.8f, 0, 0.2f, -2.8f, 0, -0.2f, 0.2f, 0, -0.4f, -0.4f), QuirkKeyframes.rest(18) };
	private static final float[][] SHUFFLE = { QuirkKeyframes.rest(0), f(3, -0.3f, 0, 0.7f, -0.3f, 0, -0.7f, 0.5f, 0, 0, 0), QuirkKeyframes.rest(9) };
	private static final float[][] CARRY = { QuirkKeyframes.rest(0), f(3, -2.9f, 0, 0.15f, -2.9f, 0, -0.15f, 0, 0, 0, 0),
			f(20, -2.9f, 0, 0.15f, -2.9f, 0, -0.15f, 0, 0, 0, 0), f(23, -1.2f, 0, 0.15f, -1.2f, 0, -0.15f, 0.4f, 0, 0, 0), QuirkKeyframes.rest(28) };

	private static final FirstPersonClip FP_POINT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, 5, 0, 0.05f, 0.1f, -0.2f),
			FirstPersonClip.f(12, -20, 5, 0, 0.05f, 0.1f, -0.2f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_UP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -70, 0, 0, 0.05f, 0.5f, 0.1f),
			FirstPersonClip.f(12, -70, 0, 0, 0.05f, 0.5f, 0.1f), FirstPersonClip.f(18, 0, 0, 0, 0, 0, 0)).both();

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

	/** The pose for {@code animId}, {@code ticks} in -- shared with the copies, which move with him. */
	public static boolean posed(HumanoidModel<?> m, int animId, float ticks) {
		float[][] clip = switch (animId) {
			case DittoKit.ANIM_PUNCH -> PUNCH;
			case DittoKit.ANIM_KICK -> KICK;
			case DittoKit.ANIM_SPLIT -> SPLIT;
			case DittoKit.ANIM_MERGE -> MERGE;
			case DittoKit.ANIM_LEAP -> LEAP;
			case DittoKit.ANIM_POINT -> POINT;
			case DittoKit.ANIM_BURST -> BURST;
			case DittoKit.ANIM_ARMY -> ARMY;
			case DittoKit.ANIM_TOSS -> TOSS;
			case DittoKit.ANIM_SHUFFLE -> SHUFFLE;
			case DittoKit.ANIM_CARRY -> CARRY;
			default -> null;
		};
		return clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		return posed(m, animId, ticks);
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case DittoKit.ANIM_POINT -> FP_POINT;
			case DittoKit.ANIM_ARMY, DittoKit.ANIM_CARRY, DittoKit.ANIM_TOSS -> FP_UP;
			default -> null;
		};
	}
}
