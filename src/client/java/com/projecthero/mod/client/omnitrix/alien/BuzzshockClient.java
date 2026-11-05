package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.alien.buzzshock.BuzzshockCopyRenderer;
import com.projecthero.mod.client.omnitrix.alien.buzzshock.BuzzshockFx;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.buzzshock.BuzzshockKit;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.2 Buzzshock's client kit (user: "Also build buzzshock please heres the skin:
 * https://www.planetminecraft.com/skin/buzzshock-ben-10-5321154/" ... "remove the pixel omnitrix from his skin model and replace it
 * with our omnitrix badge please"). The user's skin on the player body at his tiny size (x0.4), its painted chest Omnitrix painted out
 * ({@code scratchpad/ben10_v0171/gen_buzzshock.js}) and the mod's badge on his chest in its place -- the classic series wears it there.
 * Over the skin a flickering full-bright pass ({@code buzzshock_glow.png}): the green vent on his head, his eyes and the lights on his
 * limbs, and his white stripe faintly -- he is living electricity. His bolts, the Spark Bomb and the crackle round him are
 * {@link BuzzshockFx}; his copies {@link BuzzshockCopyRenderer} (the same body, glow and badge).
 */
public final class BuzzshockClient implements AlienClientKit {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/buzzshock.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/omnitrix/buzzshock_glow.png");
	/** His body scale (the kit's stat), for a copy whose owner is out of sight. */
	public static final float SIZE = 0.4f;
	/** The chest badge (body-bone pixels): where the skin painted it (jacket texels 23..24 x 37..38), just proud of the jacket layer. */
	public static final float[] BADGE = { 0f, 2.0f, -2.3f, 2.6f };

	private static boolean registered;

	public static void register() {
		AlienClients.register(Aliens.BUZZSHOCK.id(), new BuzzshockClient());
		if (!registered) {
			registered = true;
			EntityRendererRegistry.register(BuzzshockKit.COPY, BuzzshockCopyRenderer::new);
			BuzzshockFx.register();
		}
	}

	// ---------------------------------------------------------------- poses (QuirkKeyframes: tick, rArm xyz, lArm xyz, body xy, rLeg xyz, lLeg xyz, head x)
	// v0.17.2 round 3 (user: "edit and update animations on all moves please"): he is a jittery little live wire -- every clip hits its
	// shape inside two or three ticks (the move lands on the press), recoils past it, then settles; legs and hips move with the arms.
	private static float[] f(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float by, float rlx, float rly, float rlz, float llx,
			float lly, float llz, float hx) {
		return QuirkKeyframes.f(t, rax, ray, raz, lax, lay, laz, bx, by, rlx, rly, rlz, llx, lly, llz, hx);
	}

	/** Lightning Bolt: a straight-arm flick from the shoulder, the bolt kicks the arm up and his whole body twists back behind it. */
	private static final float[][] BOLT = { QuirkKeyframes.rest(0), f(2, -1.72f, 0.12f, 0, 0.45f, 0, -0.3f, 0.14f, -0.42f, -0.28f, 0, 0, 0.32f, 0, 0, 0.05f),
			f(4, -2.0f, 0.12f, 0.1f, 0.5f, 0, -0.34f, 0.02f, -0.5f, -0.2f, 0, 0, 0.26f, 0, 0, -0.08f),
			f(7, -1.75f, 0.1f, 0.05f, 0.35f, 0, -0.26f, 0.08f, -0.34f, -0.16f, 0, 0, 0.2f, 0, 0, 0), QuirkKeyframes.rest(12) };
	/** Chain Lightning: both hands thrown at the first creature, then dragged across as the bolt jumps from one to the next. */
	private static final float[][] CHAIN = { QuirkKeyframes.rest(0), f(2, -1.62f, 0.5f, 0, -1.62f, 0.15f, 0, 0.12f, 0.45f, -0.25f, 0, 0, 0.25f, 0, 0, 0),
			f(6, -1.66f, -0.1f, 0, -1.66f, -0.45f, 0, 0.14f, -0.1f, -0.25f, 0, 0, 0.25f, 0, 0, 0),
			f(11, -1.6f, -0.65f, 0, -1.6f, -0.95f, 0, 0.1f, -0.65f, 0.25f, 0, 0, -0.25f, 0, 0, 0),
			f(14, -1.2f, -0.65f, 0.1f, -1.2f, -0.9f, -0.1f, 0.06f, -0.55f, 0.15f, 0, 0, -0.15f, 0, 0, 0), QuirkKeyframes.rest(19) };
	/** Static Touch: a lunge, both palms driven in at arm's length, and the shove back off them. */
	private static final float[][] TOUCH = { QuirkKeyframes.rest(0), f(2, -1.55f, -0.28f, 0, -1.55f, 0.28f, 0, 0.5f, 0, -0.75f, 0, 0, 0.5f, 0, 0, -0.35f),
			f(5, -1.72f, -0.28f, 0, -1.72f, 0.28f, 0, 0.42f, 0, -0.7f, 0, 0, 0.45f, 0, 0, -0.3f),
			f(8, -1.1f, -0.1f, 0.25f, -1.1f, 0.1f, -0.25f, 0.05f, 0, -0.2f, 0, 0, 0.15f, 0, 0, 0), QuirkKeyframes.rest(13) };
	/** Spark Bomb: an overarm lob, the off hand aiming it, and the throwing arm following through across his hip. */
	private static final float[][] BOMB = { QuirkKeyframes.rest(0), f(1, -3.05f, 0, 0.3f, -1.1f, 0, -0.2f, -0.28f, 0.4f, 0.2f, 0, 0, -0.35f, 0, 0, -0.1f),
			f(4, -0.9f, -0.15f, 0, 0.2f, 0, -0.15f, 0.45f, -0.45f, -0.5f, 0, 0, 0.45f, 0, 0, -0.25f),
			f(7, 0.25f, -0.5f, -0.1f, 0.35f, 0, -0.2f, 0.35f, -0.55f, -0.4f, 0, 0, 0.4f, 0, 0, -0.2f), QuirkKeyframes.rest(13) };
	/** Spark Zip: he lands out of the streak still stretched flat, then skids upright with his arms thrown out for balance. */
	private static final float[][] ZIP = { f(0, 1.0f, 0, 0.3f, 1.0f, 0, -0.3f, 0.75f, 0, 0.55f, 0, 0, 0.7f, 0, 0, -0.6f),
			f(4, 0.9f, 0, 0.4f, 0.9f, 0, -0.4f, 0.65f, 0, 0.45f, 0, 0, 0.6f, 0, 0, -0.5f),
			f(8, -0.5f, 0, 1.1f, -0.5f, 0, -1.1f, -0.22f, 0, -0.55f, 0, 0.1f, 0.3f, 0, -0.1f, 0.1f),
			f(11, -0.2f, 0, 0.6f, -0.2f, 0, -0.6f, 0.06f, 0, -0.2f, 0, 0, 0.1f, 0, 0, 0), QuirkKeyframes.rest(15) };
	/** Overload: flung into a star as it goes off, shaking with it, sagging when it is spent. */
	private static final float[][] OVERLOAD = { QuirkKeyframes.rest(0), f(2, -0.2f, 0, 1.75f, -0.2f, 0, -1.75f, -0.38f, 0, 0.1f, 0, 0.42f, 0.1f, 0, -0.42f, -0.4f),
			f(5, -0.3f, 0, 1.55f, -0.1f, 0, -1.65f, -0.3f, 0.08f, 0.1f, 0, 0.36f, 0.1f, 0, -0.36f, -0.3f),
			f(8, -0.1f, 0, 1.68f, -0.3f, 0, -1.52f, -0.34f, -0.08f, 0.1f, 0, 0.4f, 0.1f, 0, -0.4f, -0.34f),
			f(11, -0.25f, 0, 1.5f, -0.15f, 0, -1.6f, -0.26f, 0.05f, 0.08f, 0, 0.32f, 0.08f, 0, -0.32f, -0.26f),
			f(15, 0.2f, 0, 0.5f, 0.2f, 0, -0.5f, 0.28f, 0, -0.2f, 0, 0.1f, 0.2f, 0, -0.1f, 0.3f), QuirkKeyframes.rest(20) };
	/** Power Line Ride: he bursts up out of the conductor, arms first, and drops into a crouch-ready stance. */
	private static final float[][] LINE = { f(0, -3.0f, 0, 0.1f, -3.0f, 0, -0.1f, 0.1f, 0, 0.2f, 0, 0, 0.2f, 0, 0, -0.35f),
			f(4, -2.9f, 0, 0.45f, -2.9f, 0, -0.45f, -0.3f, 0, 0.1f, 0, 0.2f, 0.1f, 0, -0.2f, -0.3f),
			f(8, -0.4f, 0, 1.2f, -0.4f, 0, -1.2f, 0.4f, 0, -0.6f, 0, 0.15f, -0.5f, 0, -0.15f, -0.25f),
			f(11, -0.1f, 0, 0.6f, -0.1f, 0, -0.6f, 0.15f, 0, -0.25f, 0, 0, -0.15f, 0, 0, 0), QuirkKeyframes.rest(15) };
	/** Split: he pinches in on himself, then tears apart sideways as the copy leaves him. */
	private static final float[][] SPLIT = { QuirkKeyframes.rest(0), f(2, -1.1f, -0.8f, 0, -1.1f, 0.8f, 0, 0.4f, 0, -0.35f, 0, 0, 0.35f, 0, 0, 0.25f),
			f(5, -0.1f, 0, 1.45f, -0.1f, 0, -1.45f, -0.3f, 0, 0.1f, 0, 0.42f, 0.1f, 0, -0.42f, -0.2f),
			f(8, 0, 0, 1.15f, 0, 0, -1.15f, -0.2f, 0, 0.05f, 0, 0.28f, 0.05f, 0, -0.28f, -0.1f), QuirkKeyframes.rest(13) };
	/** Merge: arms open to take the copies in, then folded into his chest as they arrive, and a shiver as they land. */
	private static final float[][] MERGE = { QuirkKeyframes.rest(0), f(3, -0.5f, 0, 1.2f, -0.5f, 0, -1.2f, -0.25f, 0, 0, 0, 0.12f, 0, 0, -0.12f, -0.2f),
			f(8, -1.3f, -0.95f, 0, -1.3f, 0.95f, 0, 0.4f, 0, -0.3f, 0, 0, 0.3f, 0, 0, 0.3f),
			f(11, -1.25f, -0.9f, 0.1f, -1.25f, 0.9f, -0.1f, 0.32f, 0.06f, -0.25f, 0, 0, 0.25f, 0, 0, 0.2f), QuirkKeyframes.rest(16) };
	/** Megawatt Swarm: both arms thrown at the sky, the copies pouring off him, held there like a conductor. */
	private static final float[][] SWARM = { QuirkKeyframes.rest(0), f(3, -3.0f, 0, 0.35f, -3.0f, 0, -0.35f, -0.35f, 0, 0.1f, 0, 0.25f, 0.1f, 0, -0.25f, -0.45f),
			f(8, -2.75f, 0, 0.55f, -2.75f, 0, -0.55f, -0.28f, 0, 0.1f, 0, 0.25f, 0.1f, 0, -0.25f, -0.35f),
			f(14, -2.95f, 0, 0.4f, -2.95f, 0, -0.4f, -0.32f, 0, 0.1f, 0, 0.25f, 0.1f, 0, -0.25f, -0.4f),
			f(18, -0.6f, 0, 0.9f, -0.6f, 0, -0.9f, 0.1f, 0, -0.15f, 0, 0.1f, 0.1f, 0, -0.1f, 0), QuirkKeyframes.rest(23) };
	/** Megavolt: he points the swarm at it and holds, then both fists clench down as they all go off together. */
	private static final float[][] MEGAVOLT = { QuirkKeyframes.rest(0), f(2, -1.7f, 0.1f, 0, 0.3f, 0, -0.3f, 0.14f, -0.4f, -0.3f, 0, 0, 0.3f, 0, 0, 0),
			f(7, -1.74f, 0.1f, 0, 0.3f, 0, -0.3f, 0.16f, -0.4f, -0.3f, 0, 0, 0.3f, 0, 0, 0),
			f(10, 0.35f, 0, 0.75f, 0.35f, 0, -0.75f, -0.32f, 0, 0.05f, 0, 0.3f, 0.05f, 0, -0.3f, -0.35f),
			f(15, 0.2f, 0, 0.55f, 0.2f, 0, -0.55f, -0.2f, 0, 0.05f, 0, 0.2f, 0.05f, 0, -0.2f, -0.2f), QuirkKeyframes.rest(20) };
	/** Static Fists: a little jab with the whole body behind it. */
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0), f(2, -1.7f, 0.2f, 0, 0.3f, 0, -0.2f, 0.2f, -0.45f, -0.35f, 0, 0, 0.3f, 0, 0, 0), QuirkKeyframes.rest(7) };
	/** Spark Flight's take-off: a crouch and a kick up off the floor, into the flying pose. */
	private static final float[][] TAKEOFF = { QuirkKeyframes.rest(0), f(2, 0.7f, 0, 0.4f, 0.7f, 0, -0.4f, 0.4f, 0, -0.6f, 0, 0, -0.6f, 0, 0, -0.2f),
			f(5, -2.6f, 0, 0.1f, 0.5f, 0, -0.3f, 0.1f, 0, 0.3f, 0, 0, 0.3f, 0, 0, 0), QuirkKeyframes.rest(9) };
	/** Coming out of Spark Flight: arms out and legs down to catch himself. */
	private static final float[][] LAND = { QuirkKeyframes.rest(0), f(3, -0.5f, 0, 1.0f, -0.5f, 0, -1.0f, -0.1f, 0, -0.35f, 0, 0.1f, 0.2f, 0, -0.1f, 0), QuirkKeyframes.rest(9) };

	// v0.17.2 round 3: every move has a first-person clip now. Each one a hand move you can read: the punch out at the crosshair, the
	// overarm lob from above the screen, the arms flung to the edges of the view.
	private static final FirstPersonClip FP_BOLT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -26, 8, 0, 0.06f, 0.14f, -0.42f),
			FirstPersonClip.f(4, -38, 8, 0, 0.06f, 0.22f, -0.3f), FirstPersonClip.f(7, -24, 6, 0, 0.05f, 0.12f, -0.3f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_CHAIN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -22, 30, 0, 0.1f, 0.12f, -0.34f),
			FirstPersonClip.f(6, -22, 5, 0, 0.0f, 0.12f, -0.34f), FirstPersonClip.f(11, -22, -34, 0, -0.12f, 0.12f, -0.3f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_TOUCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -18, 12, 0, 0.12f, 0.14f, -0.5f),
			FirstPersonClip.f(5, -24, 12, 0, 0.12f, 0.18f, -0.42f), FirstPersonClip.f(8, -12, 4, 0, 0.04f, 0.05f, -0.2f), FirstPersonClip.f(13, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_BOMB = FirstPersonClip.of(FirstPersonClip.f(0, -40, 0, 0, 0, 0.1f, 0), FirstPersonClip.f(1, -110, 0, 0, 0.02f, 0.4f, 0.1f),
			FirstPersonClip.f(4, -28, 4, 0, 0.05f, 0.12f, -0.42f), FirstPersonClip.f(7, 24, 10, 0, 0.1f, -0.25f, -0.25f), FirstPersonClip.f(13, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_ZIP = FirstPersonClip.of(FirstPersonClip.f(0, 34, 0, 0, 0, -0.45f, 0.1f), FirstPersonClip.f(4, 30, 0, 0, 0, -0.4f, 0.1f),
			FirstPersonClip.f(8, -14, -24, -16, -0.12f, 0.08f, -0.14f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_OVERLOAD = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -16, -40, -30, -0.2f, 0.12f, -0.16f),
			FirstPersonClip.f(5, -22, -34, -26, -0.17f, 0.15f, -0.18f), FirstPersonClip.f(8, -14, -42, -30, -0.21f, 0.1f, -0.15f),
			FirstPersonClip.f(11, -20, -34, -26, -0.17f, 0.14f, -0.17f), FirstPersonClip.f(15, 12, -10, -8, -0.05f, -0.2f, 0), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_LINE = FirstPersonClip.of(FirstPersonClip.f(0, -46, -10, -6, -0.14f, 0.3f, 0.12f), FirstPersonClip.f(4, -52, -18, -12, -0.2f, 0.34f, 0.14f),
			FirstPersonClip.f(8, -14, -20, -14, -0.1f, 0.05f, -0.14f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SPLIT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -26, 26, 10, 0.16f, 0.08f, -0.22f),
			FirstPersonClip.f(5, -16, -38, -26, -0.2f, 0.1f, -0.16f), FirstPersonClip.f(8, -12, -30, -22, -0.15f, 0.08f, -0.14f), FirstPersonClip.f(13, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_MERGE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -16, -30, -22, -0.16f, 0.1f, -0.16f),
			FirstPersonClip.f(8, -30, 30, 12, 0.18f, 0.02f, -0.16f), FirstPersonClip.f(11, -26, 28, 12, 0.16f, 0.0f, -0.14f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SWARM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -50, -16, -10, -0.2f, 0.32f, 0.14f),
			FirstPersonClip.f(8, -44, -20, -14, -0.24f, 0.28f, 0.14f), FirstPersonClip.f(14, -50, -16, -10, -0.2f, 0.32f, 0.14f), FirstPersonClip.f(23, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_MEGAVOLT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -26, 8, 0, 0.06f, 0.14f, -0.4f),
			FirstPersonClip.f(7, -28, 8, 0, 0.06f, 0.16f, -0.42f), FirstPersonClip.f(10, 26, -4, 0, -0.02f, -0.3f, -0.08f), FirstPersonClip.f(14, 18, 0, 0, 0, -0.22f, -0.05f),
			FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -14, 10, 0, 0.08f, 0.1f, -0.42f),
			FirstPersonClip.f(7, 0, 0, 0, 0, 0, 0));
	/** Spark Flight, for as long as it lasts: the leading fist out ahead of him (a clip this long ends when {@link BuzzshockKit#ANIM_LAND} replaces it). */
	private static final FirstPersonClip FP_FLY = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(5, -30, 10, 0, 0.1f, 0.2f, -0.36f),
			FirstPersonClip.f(100000, -30, 10, 0, 0.1f, 0.2f, -0.36f));
	private static final FirstPersonClip FP_LAND = FirstPersonClip.of(FirstPersonClip.f(0, -30, 10, 0, 0.1f, 0.2f, -0.36f), FirstPersonClip.f(8, 0, 0, 0, 0, 0, 0));

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public float[] badge() {
		// v0.17.2 (user: "remove the pixel omnitrix from his skin model and replace it with our omnitrix badge please"): where the skin
		// painted it, on his chest
		return BADGE;
	}

	/** The glow's strength {@code age} ticks in: a steady hum with quick flickers, never fully dark. */
	public static float flicker(float age, int seed) {
		float k = 0.78f + 0.12f * Mth.sin(age * 0.7f + seed) + 0.1f * Mth.sin(age * 2.3f + seed * 3);
		long tick = (long) age;
		if (((tick * 2654435761L + seed * 40503L) >>> 7) % 23 == 0) {
			k = 1.2f; // a crackle
		}
		return Mth.clamp(k, 0.45f, 1.2f);
	}

	/** The full-bright electricity pass over an already posed model. */
	public static void glow(PoseStack pose, MultiBufferSource buffers, HumanoidModel<?> model, float age, int seed, boolean wet) {
		float k = flicker(age, seed) * (wet ? 0.35f : 1f);
		int c = Mth.clamp((int) (255 * Math.min(1f, k)), 0, 255);
		int argb = 0xFF000000 | (c << 16) | (c << 8) | c;
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, argb);
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model,
			float partial, float age) {
		boolean wet = (Omnitrix.state(p).kitFlags & BuzzshockKit.F_WET) != 0;
		glow(pose, buffers, model, age, p.getId(), wet);
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		ThrustFlightPose.tick(p, BuzzshockKit.holdFlying(p));
		BuzzshockFx.tickFlight(p);
		// a little crackle round him now and then (more while flying)
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
			return; // never a spark in your own eyes
		}
		var s = Omnitrix.state(p);
		boolean fly = (s.kitFlags & BuzzshockKit.F_FLY) != 0;
		if (p.getRandom().nextInt(fly ? 2 : 7) == 0 && (s.kitFlags & BuzzshockKit.F_WET) == 0) {
			float sc = p.getScale();
			p.level().addParticle(ParticleTypes.ELECTRIC_SPARK, p.getX() + (p.getRandom().nextDouble() - 0.5) * 0.5 * sc,
					p.getY() + p.getRandom().nextDouble() * p.getBbHeight(), p.getZ() + (p.getRandom().nextDouble() - 0.5) * 0.5 * sc, 0, 0.02, 0);
		}
	}

	/** The pose for {@code animId}, {@code ticks} in -- shared with the copies, which split and swarm with him. */
	public static boolean posed(HumanoidModel<?> m, int animId, float ticks) {
		float[][] clip = switch (animId) {
			case BuzzshockKit.ANIM_BOLT -> BOLT;
			case BuzzshockKit.ANIM_CHAIN -> CHAIN;
			case BuzzshockKit.ANIM_TOUCH -> TOUCH;
			case BuzzshockKit.ANIM_BOMB -> BOMB;
			case BuzzshockKit.ANIM_ZIP -> ZIP;
			case BuzzshockKit.ANIM_OVERLOAD -> OVERLOAD;
			case BuzzshockKit.ANIM_LINE -> LINE;
			case BuzzshockKit.ANIM_SPLIT -> SPLIT;
			case BuzzshockKit.ANIM_MERGE -> MERGE;
			case BuzzshockKit.ANIM_SWARM -> SWARM;
			case BuzzshockKit.ANIM_MEGAVOLT -> MEGAVOLT;
			case BuzzshockKit.ANIM_PUNCH -> PUNCH;
			case BuzzshockKit.ANIM_TAKEOFF -> TAKEOFF;
			case BuzzshockKit.ANIM_LAND -> LAND;
			default -> null;
		};
		return clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		// the flying pose goes on first and a clip plays over it, so a bolt mid-flight blends back into the flying pose
		float air = ThrustFlightPose.amount(p, age - Mth.floor(age));
		if (air > 0f) {
			flightPose(p, m, age, air);
		}
		return posed(m, animId, ticks) || air > 0f;
	}

	/** Spark Flight: one fist leading, the other arm and both legs streaming behind him, a current shivering down the body. */
	private static void flightPose(AbstractClientPlayer p, HumanoidModel<?> m, float age, float k) {
		float jit = Mth.sin(age * 2.1f) * 0.03f + Mth.sin(age * 3.7f) * 0.02f;
		m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -3.0f + jit);
		m.rightArm.yRot = Mth.lerp(k, m.rightArm.yRot, 0f);
		m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, 0.08f);
		m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, 0.3f - jit);
		m.leftArm.yRot = Mth.lerp(k, m.leftArm.yRot, 0f);
		m.leftArm.zRot = Mth.lerp(k, m.leftArm.zRot, -0.22f);
		m.rightLeg.xRot = Mth.lerp(k, m.rightLeg.xRot, 0.08f + Mth.sin(age * 0.9f) * 0.06f);
		m.leftLeg.xRot = Mth.lerp(k, m.leftLeg.xRot, 0.2f - Mth.sin(age * 0.9f) * 0.06f);
		m.rightLeg.zRot = Mth.lerp(k, m.rightLeg.zRot, 0.03f);
		m.leftLeg.zRot = Mth.lerp(k, m.leftLeg.zRot, -0.03f);
		ThrustFlightPose.headOnAim(p, m, age - Mth.floor(age));
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case BuzzshockKit.ANIM_BOLT -> FP_BOLT;
			case BuzzshockKit.ANIM_CHAIN -> FP_CHAIN;
			case BuzzshockKit.ANIM_TOUCH -> FP_TOUCH;
			case BuzzshockKit.ANIM_BOMB -> FP_BOMB;
			case BuzzshockKit.ANIM_ZIP -> FP_ZIP;
			case BuzzshockKit.ANIM_OVERLOAD -> FP_OVERLOAD;
			case BuzzshockKit.ANIM_LINE -> FP_LINE;
			case BuzzshockKit.ANIM_SPLIT -> FP_SPLIT;
			case BuzzshockKit.ANIM_MERGE -> FP_MERGE;
			case BuzzshockKit.ANIM_SWARM -> FP_SWARM;
			case BuzzshockKit.ANIM_MEGAVOLT -> FP_MEGAVOLT;
			case BuzzshockKit.ANIM_PUNCH -> FP_PUNCH;
			case BuzzshockKit.ANIM_TAKEOFF -> FP_FLY;
			case BuzzshockKit.ANIM_LAND -> FP_LAND;
			default -> null;
		};
	}
}
