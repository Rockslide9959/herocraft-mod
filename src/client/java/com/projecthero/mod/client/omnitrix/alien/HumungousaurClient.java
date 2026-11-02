package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.MutationRender;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.humungousaur.HumungousaurKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Humungousaur's client kit -- the player body in the user's Planet Minecraft skin (5136728, "Humungousaur | Ben 10 |
 * Alien Force"; aliens always draw on the wide arm model, so it fits classic and slim players alike) with a thick tail, back
 * plates and a brow in the skin's own browns; at the biggest size step the plates become bone spikes and the tail grows a club.
 * v0.17.2 revamp: the Spike Burst's bone spikes grow out of back, shoulders and tail (and sink back), the held Brace pose, the
 * Earthshaker's rear-up-and-three-stomps clip, the Heavy Hurl's slab of real ground blocks. Rendered effects (world pass): dust
 * walls behind the Ground Pound / landings / Earthshaker stomps (their shock rings and cracks are QuirkFx from the server), the
 * Tail Club's arc, the Primal Roar's rings, the Grow / Shrink burst. Dust particles are accents only.
 */
public final class HumungousaurClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/humungousaur.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/humungousaur_extras.png");
	private static final float S = 1f / 64f;
	private static final int DUST = 0xB58A6A44;

	// f(tick, rArm xyz, lArm xyz, body x y, rLeg xyz, lLeg xyz, head x)
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.0f, 0.4f, 0.6f, 0, 0, 0, 0, 0.45f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(4, -1.65f, -0.15f, 0, 0.3f, 0, 0, 0.15f, -0.4f, -0.2f, 0, 0, 0.2f, 0, 0, 0), QuirkKeyframes.rest(10) };
	private static final float[][] UPPER = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.4f, 0, 0.2f, 0, 0, 0, 0.35f, 0.3f, -0.3f, 0, 0, 0.3f, 0, 0, 0.2f),
			QuirkKeyframes.f(6, -2.9f, -0.2f, 0, 0.3f, 0, 0, -0.2f, -0.3f, 0, 0, 0, 0, 0, 0, -0.3f), QuirkKeyframes.rest(12) };
	private static final float[][] POUND = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -3.0f, 0, 0.15f, -3.0f, 0, -0.15f, -0.2f, 0, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(7, -0.9f, 0, 0, -0.9f, 0, 0, 0.7f, 0, -0.5f, 0, 0.2f, -0.5f, 0, -0.2f, 0.3f),
			QuirkKeyframes.f(14, -0.9f, 0, 0, -0.9f, 0, 0, 0.7f, 0, -0.5f, 0, 0.2f, -0.5f, 0, -0.2f, 0.3f), QuirkKeyframes.rest(20) };
	private static final float[][] TAIL = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.3f, 0, 1.1f, -0.3f, 0, -1.1f, 0.2f, 1.6f, 0, 0, 0.2f, 0, 0, -0.2f, 0),
			QuirkKeyframes.f(7, -0.3f, 0, 1.1f, -0.3f, 0, -1.1f, 0.2f, 4.7f, 0, 0, 0.2f, 0, 0, -0.2f, 0),
			QuirkKeyframes.f(10, 0, 0, 0.3f, 0, 0, -0.3f, 0, 6.2832f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(14) };
	private static final float[][] CHARGE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.6f, 0, 0.3f, 0.6f, 0, -0.3f, 0.6f, 0, -0.8f, 0, 0, 0.6f, 0, 0, 0.5f),
			QuirkKeyframes.f(6, 0.6f, 0, 0.3f, 0.6f, 0, -0.3f, 0.6f, 0, 0.6f, 0, 0, -0.8f, 0, 0, 0.5f),
			QuirkKeyframes.f(9, 0.6f, 0, 0.3f, 0.6f, 0, -0.3f, 0.6f, 0, -0.8f, 0, 0, 0.6f, 0, 0, 0.5f),
			QuirkKeyframes.f(12, 0.6f, 0, 0.3f, 0.6f, 0, -0.3f, 0.6f, 0, 0.6f, 0, 0, -0.8f, 0, 0, 0.5f),
			QuirkKeyframes.f(15, 0.6f, 0, 0.3f, 0.6f, 0, -0.3f, 0.6f, 0, -0.8f, 0, 0, 0.6f, 0, 0, 0.5f),
			QuirkKeyframes.f(18, 0.6f, 0, 0.3f, 0.6f, 0, -0.3f, 0.6f, 0, 0.6f, 0, 0, -0.8f, 0, 0, 0.5f), QuirkKeyframes.rest(24) };
	private static final float[][] LEAP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.9f, 0, 0.4f, 0.9f, 0, -0.4f, 0.3f, 0, -0.7f, 0, 0, -0.7f, 0, 0, -0.2f),
			QuirkKeyframes.f(20, -2.4f, 0, 0.3f, -2.4f, 0, -0.3f, -0.1f, 0, -0.2f, 0, 0, 0.2f, 0, 0, -0.3f), QuirkKeyframes.rest(26) };
	private static final float[][] LAND = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.6f, 0, 0.5f, -0.6f, 0, -0.5f, 0.5f, 0, -0.6f, 0, 0.2f, -0.6f, 0, -0.2f, 0.2f),
			QuirkKeyframes.rest(12) };
	private static final float[][] TOSS = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, 0.5f, 0, 0.3f, 0.5f, 0, -0.3f, 0.5f, 0, -0.4f, 0, 0, -0.4f, 0, 0, 0.3f),
			QuirkKeyframes.f(8, -3.0f, 0, 0.2f, -3.0f, 0, -0.2f, -0.25f, 0, 0, 0, 0, 0, 0, 0, -0.3f),
			QuirkKeyframes.f(11, -1.2f, 0, 0, -1.2f, 0, 0, 0.35f, 0, -0.3f, 0, 0, 0.2f, 0, 0, 0.1f), QuirkKeyframes.rest(16) };
	private static final float[][] GRAB = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.6f, -0.2f, 0, -1.6f, 0.2f, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(7, -2.8f, 0, 0.4f, -2.8f, 0, -0.4f, -0.2f, 0.8f, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(10, -1.0f, 0, 0, -1.0f, 0, 0, 0.3f, -0.6f, -0.3f, 0, 0, 0.2f, 0, 0, 0.1f), QuirkKeyframes.rest(15) };
	private static final float[][] GROW = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.6f, 0, 1.3f, -0.6f, 0, -1.3f, -0.15f, 0, 0, 0, 0.25f, 0, 0, -0.25f, -0.35f),
			QuirkKeyframes.f(14, -0.7f, 0, 1.4f, -0.7f, 0, -1.4f, -0.15f, 0, 0, 0, 0.25f, 0, 0, -0.25f, -0.35f), QuirkKeyframes.rest(20) };
	private static final float[][] SHRINK = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.5f, 0.5f, 0, -0.5f, -0.5f, 0, 0.4f, 0, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.rest(12) };
	private static final float[][] ROAR = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.4f, 0, 0.9f, 0.4f, 0, -0.9f, -0.3f, 0, -0.2f, 0, 0.15f, 0.2f, 0, -0.15f, -0.6f),
			QuirkKeyframes.f(18, 0.4f, 0, 1.0f, 0.4f, 0, -1.0f, -0.3f, 0, -0.2f, 0, 0.15f, 0.2f, 0, -0.15f, -0.6f), QuirkKeyframes.rest(24) };
	// v0.17.2 revamp (user: Earthshaker -- "he rears up and stomps three times"): rear up on one leg, stomp, the other leg, stomp,
	// rear right up, and the big two-footed stomp with the arms flung down
	private static final float[] REAR = QuirkKeyframes.f(0, -2.6f, 0, 0.4f, -2.6f, 0, -0.4f, -0.35f, 0, -1.2f, 0, 0.15f, 0, 0, -0.15f, -0.5f);
	private static final float[] STOMP_R = QuirkKeyframes.f(0, -0.6f, 0, 0.5f, -0.6f, 0, -0.5f, 0.4f, 0, 0.2f, 0, 0.25f, -0.3f, 0, -0.25f, 0.25f);
	private static final float[] RAISE_L = QuirkKeyframes.f(0, -2.0f, 0, 0.4f, -2.0f, 0, -0.4f, -0.25f, 0, 0, 0, 0.15f, -1.2f, 0, -0.15f, -0.4f);
	private static final float[] STOMP_L = QuirkKeyframes.f(0, -0.6f, 0, 0.5f, -0.6f, 0, -0.5f, 0.4f, 0, -0.3f, 0, 0.25f, 0.2f, 0, -0.25f, 0.25f);
	private static final float[] BIG = QuirkKeyframes.f(0, -0.3f, 0, 0.7f, -0.3f, 0, -0.7f, 0.6f, 0, 0.3f, 0, 0.3f, -0.4f, 0, -0.3f, 0.4f);
	private static final float[][] QUAKE = { QuirkKeyframes.rest(0), at(8, REAR), at(12, STOMP_R), at(18, RAISE_L), at(24, STOMP_L), at(31, REAR),
			at(36, BIG), at(44, BIG), QuirkKeyframes.rest(50) };
	private static final float[][] CRASH = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(2, -1.0f, 0, 0.6f, -1.0f, 0, -0.6f, -0.3f, 0, -0.3f, 0, 0.2f, 0.3f, 0, -0.2f, -0.4f), QuirkKeyframes.rest(14) };
	private static final float[][] CLEAVE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.8f, 0.9f, 0.4f, 0, 0, 0, 0, 0.5f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(5, -1.3f, -0.8f, 0, 0, 0, 0, 0.15f, -0.5f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(10) };
	/** The Brace: forearms crossed in front, feet planted wide. */
	private static final float[][] BRACE = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(4, -1.5f, 0.6f, 0.1f, -1.5f, -0.6f, -0.1f, 0.15f, 0, 0, 0, 0.3f, 0, 0, -0.3f, 0.1f) };
	private static final float[][] SLAM = { QuirkKeyframes.f(0, -3.0f, 0, 0.2f, -3.0f, 0, -0.2f, -0.2f, 0, -0.5f, 0, 0, -0.5f, 0, 0, -0.3f),
			QuirkKeyframes.f(3, -0.8f, 0, 0, -0.8f, 0, 0, 0.8f, 0, -0.7f, 0, 0.25f, -0.7f, 0, -0.25f, 0.3f),
			QuirkKeyframes.f(14, -0.8f, 0, 0, -0.8f, 0, 0, 0.8f, 0, -0.7f, 0, 0.25f, -0.7f, 0, -0.25f, 0.3f), QuirkKeyframes.rest(22) };

	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 10, -10, 0, 0, -0.1f, 0.15f),
			FirstPersonClip.f(4, -20, 25, 0, 0.15f, 0.08f, -0.35f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_UPPER = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 30, 15, 0, 0.1f, -0.35f, 0f),
			FirstPersonClip.f(6, -75, 15, 0, 0.1f, 0.5f, -0.2f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SLAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -80, 5, 0, 0.05f, 0.55f, 0.2f),
			FirstPersonClip.f(7, 30, 10, 0, 0.05f, -0.4f, -0.1f), FirstPersonClip.f(14, 30, 10, 0, 0.05f, -0.4f, -0.1f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0))
			.both();
	private static final FirstPersonClip FP_SWEEP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -10, -35, -20, -0.25f, 0f, 0f),
			FirstPersonClip.f(8, -10, 40, 20, 0.3f, 0f, -0.1f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_RUN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 35, 10, 0, 0.05f, -0.4f, 0.2f),
			FirstPersonClip.f(18, 35, 10, 0, 0.05f, -0.4f, 0.2f), FirstPersonClip.f(24, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_THROW = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, 20, 10, 0, 0.05f, -0.3f, 0.1f),
			FirstPersonClip.f(8, -75, 10, 0, 0.05f, 0.55f, 0.15f), FirstPersonClip.f(11, -10, 20, 0, 0.12f, 0.1f, -0.3f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0))
			.both();
	private static final FirstPersonClip FP_FLEX = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -50, -20, -30, -0.1f, 0.3f, 0f),
			FirstPersonClip.f(14, -50, -20, -30, -0.1f, 0.3f, 0f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_QUAKE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(8, -60, 10, 10, 0.1f, 0.45f, 0f),
			FirstPersonClip.f(12, 25, 10, 0, 0.05f, -0.35f, -0.1f), FirstPersonClip.f(18, -45, 10, 10, 0.1f, 0.35f, 0f),
			FirstPersonClip.f(24, 25, 10, 0, 0.05f, -0.35f, -0.1f), FirstPersonClip.f(31, -70, 10, 10, 0.1f, 0.55f, 0f),
			FirstPersonClip.f(36, 35, 10, 0, 0.05f, -0.45f, -0.15f), FirstPersonClip.f(44, 35, 10, 0, 0.05f, -0.45f, -0.15f),
			FirstPersonClip.f(50, 0, 0, 0, 0, 0, 0)).both();

	private static boolean registered;

	private static float[] at(float tick, float[] frame) {
		float[] f = frame.clone();
		f[0] = tick;
		return f;
	}

	private HumungousaurClient() {
	}

	/** One line in {@code OmnitrixClient.initialize}. */
	public static void register() {
		AlienClients.register(Aliens.HUMUNGOUSAUR.id(), new HumungousaurClient());
		if (!registered) {
			registered = true;
			OmnitrixRenderers.projectileLook("humungousaur_slab", HumungousaurClient::slab);
			WorldRenderEvents.AFTER_ENTITIES.register(HumungousaurClient::renderWorld);
		}
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	// ---------------------------------------------------------------- body extras

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		int stage = HumungousaurKit.stage(p);
		float burst = spikeOut(p, partial);
		float sway = Mth.sin(age * 0.1f) * 6f + Mth.cos(p.walkAnimation.position(partial) * 0.6f) * 8f * p.walkAnimation.speed(partial);
		// v0.17.2 (the user's PMC skin): extras sheet in the skin's own browns -- tail hide (0..8), plates (8..12), brow (12..16),
		// bone (16..20), the club (20..24)
		AlienParts.on(pose, model.body, () -> {
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS));
			// the tail: three tapering segments off the base of his back, swaying
			pose.pushPose();
			pose.translate(0, 10.5f / 16f, 1.6f / 16f);
			pose.mulPose(Axis.XP.rotationDegrees(-62f));
			pose.mulPose(Axis.YP.rotationDegrees(sway));
			float[] w = { 3.2f, 2.5f, 1.8f };
			float[] len = { 5.5f, 5.0f, 4.5f };
			for (int i = 0; i < 3; i++) {
				AlienParts.box(pose, vc, -w[i], -w[i] * 0.9f, 0f, w[i], w[i] * 0.9f, len[i] + 0.3f, 0f, 0f, 8 * S, 8 * S, 0xFFFFFFFF, light);
				if (burst > 0f) {
					// Spike Burst: a bone spike up out of each tail segment
					float h = 2.6f * burst;
					AlienParts.box(pose, vc, -0.5f, -w[i] * 0.9f - h, len[i] * 0.3f, 0.5f, -w[i] * 0.9f, len[i] * 0.3f + 1.0f, 16 * S, 0f, 20 * S, 4 * S,
							0xFFFFFFFF, light);
				}
				pose.translate(0, 0, len[i] / 16f);
				pose.mulPose(Axis.XP.rotationDegrees(-10f));
				pose.mulPose(Axis.YP.rotationDegrees(sway * 0.6f));
			}
			if (stage >= 2) {
				// the tail club, studded with bone
				AlienParts.box(pose, vc, -2.4f, -2.2f, -0.5f, 2.4f, 2.2f, 3.2f, 20 * S, 0f, 24 * S, 4 * S, 0xFFFFFFFF, light);
				for (int k = 0; k < 4; k++) {
					float x = k % 2 == 0 ? -2.4f : 2.4f, y = k < 2 ? -2.2f : 2.2f;
					AlienParts.box(pose, vc, x - 0.6f, y - 0.6f, 0.6f, x + 0.6f, y + 0.6f, 2.0f, 16 * S, 0f, 20 * S, 4 * S, 0xFFFFFFFF, light);
				}
			}
			pose.popPose();
			// back plates down his spine; bone spikes once he has grown (or while the Spike Burst is out)
			for (int i = 0; i < 4; i++) {
				float y = 1.0f + i * 2.6f;
				float h = stage >= 2 ? 3.2f - i * 0.3f : stage == 1 ? 2.0f : 1.2f;
				h += 2.4f * burst;
				boolean bone = stage >= 2 || burst > 0.5f;
				float u0 = bone ? 16 * S : 8 * S;
				AlienParts.box(pose, vc, -0.8f, y, 2.0f, 0.8f, y + 1.8f, 2.0f + h, u0, 0f, u0 + 4 * S, 4 * S, 0xFFFFFFFF, light);
				if (burst > 0f) {
					// a second row, splayed out to either side
					float s = 1.8f * burst;
					AlienParts.box(pose, vc, -3.2f, y + 0.4f, 2.0f, -2.4f, y + 1.4f, 2.0f + s, 16 * S, 0f, 20 * S, 4 * S, 0xFFFFFFFF, light);
					AlienParts.box(pose, vc, 2.4f, y + 0.4f, 2.0f, 3.2f, y + 1.4f, 2.0f + s, 16 * S, 0f, 20 * S, 4 * S, 0xFFFFFFFF, light);
				}
			}
		});
		for (var arm : new net.minecraft.client.model.geom.ModelPart[] { model.rightArm, model.leftArm }) {
			boolean right = arm == model.rightArm;
			AlienParts.on(pose, arm, () -> {
				VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS));
				if (stage >= 2) {
					// spikes on his forearms
					AlienParts.box(pose, vc, -0.6f, 4.0f, 2.0f, 0.6f, 5.4f, 4.2f, 16 * S, 0f, 20 * S, 4 * S, 0xFFFFFFFF, light);
				}
				if (burst > 0f) {
					// Spike Burst: shoulder spikes standing up off the top of each arm
					float h = 3.0f * burst;
					float x = right ? -1.5f : 1.5f;
					AlienParts.box(pose, vc, x - 0.6f, -2.0f - h, -0.6f, x + 0.6f, -2.0f, 0.6f, 16 * S, 0f, 20 * S, 4 * S, 0xFFFFFFFF, light);
				}
			});
		}
		// a heavy brow ridge
		AlienParts.on(pose, model.head, () -> {
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS));
			AlienParts.box(pose, vc, -4.3f, -6.6f, -4.6f, 4.3f, -5.4f, -3.8f, 12 * S, 0f, 16 * S, 4 * S, 0xFFFFFFFF, light);
		});
	}

	/** v0.17.2: how far the Spike Burst's spikes are out, 0..1 -- up over 4 ticks, back down over the last 4. */
	private static float spikeOut(Player p, float partial) {
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & HumungousaurKit.F_SPIKES) == 0) {
			return 0f;
		}
		float now = p.level().getGameTime() + partial;
		float in = (now - s.readyAt(HumungousaurKit.ID + "." + HumungousaurKit.SPIKES_START)) / 4f;
		float out = (s.readyAt(HumungousaurKit.ID + "." + HumungousaurKit.SPIKES_END) - now) / 4f;
		return Mth.clamp(Math.min(in, out), 0f, 1f);
	}

	// ---------------------------------------------------------------- poses

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & HumungousaurKit.F_BRACE) != 0) {
			// v0.17.2 Brace: held while the flag is up (forearms crossed, feet wide)
			float since = p.level().getGameTime() - s.readyAt(HumungousaurKit.ID + "." + HumungousaurKit.BRACE_START);
			return QuirkKeyframes.play(m, BRACE, Mth.clamp(since, 0f, 4f), 1f);
		}
		float[][] clip = switch (animId) {
			case HumungousaurKit.ANIM_PUNCH -> PUNCH;
			case HumungousaurKit.ANIM_UPPER -> UPPER;
			case HumungousaurKit.ANIM_POUND -> POUND;
			case HumungousaurKit.ANIM_TAIL -> TAIL;
			case HumungousaurKit.ANIM_CHARGE -> CHARGE;
			case HumungousaurKit.ANIM_LEAP -> (s.kitFlags & HumungousaurKit.F_LEAP) != 0 || ticks < 20 ? LEAP : null;
			case HumungousaurKit.ANIM_LAND -> LAND;
			case HumungousaurKit.ANIM_HURL -> GRAB;
			case HumungousaurKit.ANIM_CRASH -> CRASH;
			case HumungousaurKit.ANIM_GROW, HumungousaurKit.ANIM_SPIKES -> GROW;
			case HumungousaurKit.ANIM_SHRINK -> SHRINK;
			case HumungousaurKit.ANIM_ROAR -> ROAR;
			case HumungousaurKit.ANIM_QUAKE -> QUAKE;
			case HumungousaurKit.ANIM_TITAN_LAND -> SLAM;
			case HumungousaurKit.ANIM_CLEAVE -> CLEAVE;
			default -> null;
		};
		// a held Stampede keeps the gallop going (the clip's middle beats loop)
		float at = clip == CHARGE && (s.kitFlags & HumungousaurKit.F_CHARGE) != 0 && ticks > 15 ? 3f + (ticks - 3f) % 12f : ticks;
		boolean played = clip != null && QuirkKeyframes.play(m, clip, at, 1f);
		if (!played) {
			// a heavy, hunched stance: arms a little out
			m.rightArm.zRot += 0.12f;
			m.leftArm.zRot -= 0.12f;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case HumungousaurKit.ANIM_PUNCH -> FP_PUNCH;
			case HumungousaurKit.ANIM_UPPER -> FP_UPPER;
			case HumungousaurKit.ANIM_POUND, HumungousaurKit.ANIM_TITAN_LAND, HumungousaurKit.ANIM_LAND -> FP_SLAM;
			case HumungousaurKit.ANIM_TAIL, HumungousaurKit.ANIM_CLEAVE -> FP_SWEEP;
			case HumungousaurKit.ANIM_CHARGE, HumungousaurKit.ANIM_LEAP, HumungousaurKit.ANIM_CRASH -> FP_RUN;
			case HumungousaurKit.ANIM_HURL -> FP_THROW;
			case HumungousaurKit.ANIM_GROW, HumungousaurKit.ANIM_SHRINK, HumungousaurKit.ANIM_ROAR, HumungousaurKit.ANIM_SPIKES -> FP_FLEX;
			case HumungousaurKit.ANIM_QUAKE -> FP_QUAKE;
			default -> null;
		};
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & HumungousaurKit.F_CHARGE) != 0 && p.onGround()) {
			// dust kicked up behind the stampede
			BlockState under = p.level().getBlockState(p.blockPosition().below());
			if (!under.isAir()) {
				var r = p.getRandom();
				p.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, under), p.getX() + (r.nextDouble() - 0.5) * p.getBbWidth(), p.getY() + 0.1,
						p.getZ() + (r.nextDouble() - 0.5) * p.getBbWidth(), 0, 0.1, 0);
			}
		}
	}

	@Override
	public float[] badge() {
		// on the chest, above the belly plates
		return new float[] { 0f, 3.5f, -2.05f, 3.4f };
	}

	// ---------------------------------------------------------------- the world pass

	private static void renderWorld(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		MultiBufferSource buffers = context.consumers();
		for (Player pl : mc.level.players()) {
			if (!Omnitrix.hasPower(pl)) {
				continue;
			}
			OmnitrixState st = Omnitrix.state(pl);
			if (!HumungousaurKit.ID.equals(st.active) || !st.transformed()) {
				continue;
			}
			float t = pl.level().getGameTime() - st.animStart + partial;
			int stage = HumungousaurKit.stage(pl);
			Vec3 feet = pl.getPosition(partial);
			switch (st.animId) {
				case HumungousaurKit.ANIM_POUND -> {
					if (t >= 6 && t < 22) {
						shockwave(feet, (float) (HumungousaurKit.POUND_RADIUS + 1.5 * stage), t - 6, 16f, cam, pose, buffers);
					}
				}
				case HumungousaurKit.ANIM_LAND -> {
					if (t < 14) {
						shockwave(feet, (float) (HumungousaurKit.LAND_RADIUS + stage), t, 14f, cam, pose, buffers);
					}
				}
				case HumungousaurKit.ANIM_TITAN_LAND -> {
					if (t < 22) {
						shockwave(feet, (float) HumungousaurKit.TITAN_LAND_RADIUS, t, 22f, cam, pose, buffers);
					}
				}
				case HumungousaurKit.ANIM_QUAKE -> {
					// the Earthshaker: a dust wall racing out behind each stomp's QuirkFx ring
					for (int i = 0; i < HumungousaurKit.QUAKE_STOMPS.length; i++) {
						boolean last = i == HumungousaurKit.QUAKE_STOMPS.length - 1;
						float k = t - HumungousaurKit.QUAKE_STOMPS[i];
						float life = last ? 22f : 16f;
						if (k >= 0 && k < life) {
							float r = (float) ((last ? HumungousaurKit.QUAKE_BIG_RADIUS : HumungousaurKit.QUAKE_SMALL_RADIUS) + stage);
							shockwave(feet, r, k, life, cam, pose, buffers);
						}
					}
				}
				case HumungousaurKit.ANIM_TAIL -> {
					if (t < 12) {
						sweep(pl, feet, (float) (HumungousaurKit.TAIL_RADIUS + stage), t, 12f, cam, pose, buffers);
					}
				}
				case HumungousaurKit.ANIM_ROAR -> {
					if (t < 18) {
						roar(pl, partial, (float) (HumungousaurKit.ROAR_RANGE + stage * 2), t, 18f, cam, pose, buffers);
					}
				}
				case HumungousaurKit.ANIM_GROW, HumungousaurKit.ANIM_SHRINK -> {
					if (t < 12) {
						shockwave(feet, 1.5f + pl.getBbWidth() * 2f, t, 12f, cam, pose, buffers);
					}
				}
				default -> {
				}
			}
		}
	}

	/**
	 * A shockwave: a flat ring of dust racing out along the ground to {@code radius}, with a low wall of churned earth standing up at
	 * its front.
	 */
	private static void shockwave(Vec3 at, float radius, float t, float life, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		float k = Mth.clamp(t / (life * 0.75f), 0f, 1f);
		float fade = t > life * 0.5f ? Math.max(0f, 1f - (t - life * 0.5f) / (life * 0.5f)) : 1f;
		float r = radius * (float) Math.sin(k * Math.PI * 0.5);
		Vec3 o = at.subtract(cam);
		int a = (int) (0xB5 * fade);
		int dust = (a << 24) | (DUST & 0xFFFFFF);
		int clear = DUST & 0xFFFFFF;
		pose.pushPose();
		pose.translate(o.x, o.y + 0.06, o.z);
		VertexConsumer tr = buffers.getBuffer(RenderType.entityTranslucent(MutationRender.WHITE));
		PyroDraw.annulus(pose, tr, Math.max(0f, r - 1.4f), r, clear, dust, 0f);
		pose.translate(0, 0.35 * fade, 0);
		VertexConsumer tr2 = buffers.getBuffer(RenderType.entityTranslucent(MutationRender.WHITE));
		PyroDraw.band(pose, tr2, r, 0.35f * fade, dust, clear);
		pose.popPose();
	}

	/** The Tail Sweep: a pale arc whipping round him at hip height. */
	private static void sweep(Player pl, Vec3 at, float radius, float t, float life, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		float fade = 1f - t / life;
		Vec3 o = at.subtract(cam);
		pose.pushPose();
		pose.translate(o.x, o.y + pl.getBbHeight() * 0.3, o.z);
		VertexConsumer tr = buffers.getBuffer(RenderType.entityTranslucent(MutationRender.WHITE));
		int a = (int) (0x90 * fade);
		PyroDraw.band(pose, tr, radius * 0.85f, 0.25f, (a << 24) | 0xE8DCC0, 0x00E8DCC0);
		pose.popPose();
	}

	/** The Primal Roar: rings of shaken air rolling out ahead of his jaws. */
	private static void roar(Player pl, float partial, float range, float t, float life, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		Vec3 mouth = pl.getEyePosition(partial).add(0, -0.15 * pl.getScale(), 0);
		Vec3 dir = pl.getViewVector(partial);
		float fade = t > life * 0.6f ? Math.max(0f, 1f - (t - life * 0.6f) / (life * 0.4f)) : 1f;
		float front = t * 2.2f;
		Vec3 o = mouth.subtract(cam);
		pose.pushPose();
		pose.translate(o.x, o.y, o.z);
		PyroDraw.alignY(pose, dir);
		VertexConsumer tr = buffers.getBuffer(RenderType.entityTranslucent(MutationRender.WHITE));
		for (float s = front % 1.4f; s <= Math.min(front, range); s += 1.4f) {
			float k = s / range;
			int a = (int) (0x80 * fade * (1f - 0.7f * k));
			pose.pushPose();
			pose.translate(0, s, 0);
			PyroDraw.band(pose, tr, 0.5f + 4.5f * k, 0.12f, (a << 24) | 0xF0E8D8, 0x00F0E8D8);
			pose.popPose();
		}
		pose.popPose();
	}

	/**
	 * v0.17.2 Heavy Hurl in flight: the slab of ground he tore up -- a real 3x1x2 chunk of the block he stood on (its id rides in the
	 * projectile's colour), spinning slowly, with a few clods of dirt clinging underneath.
	 */
	private static void slab(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		var blocks = Minecraft.getInstance().getBlockRenderer();
		int light = LevelRenderer.getLightColor(e.level(), e.blockPosition());
		float age = e.tickCount + partial;
		BlockState top = Block.stateById(e.colour());
		if (top.isAir()) {
			top = Blocks.DIRT.defaultBlockState();
		}
		float s = e.size() * 0.75f;
		pose.pushPose();
		pose.translate(0, e.getBbHeight() * 0.5, 0);
		pose.mulPose(Axis.YP.rotationDegrees(age * 4f));
		pose.mulPose(Axis.XP.rotationDegrees(age * 6f));
		pose.scale(s, s, s);
		pose.translate(-1.5, -0.5, -1.0);
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 2; z++) {
				pose.pushPose();
				pose.translate(x, 0, z);
				blocks.renderSingleBlock(top, pose, buffers, light, OverlayTexture.NO_OVERLAY);
				pose.popPose();
			}
		}
		// clods torn up with it
		for (int i = 0; i < 3; i++) {
			pose.pushPose();
			pose.translate(0.4 + i * 0.9, -0.45, 0.3 + (i % 2) * 0.7);
			pose.scale(0.55f, 0.5f, 0.55f);
			blocks.renderSingleBlock(Blocks.DIRT.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
			pose.popPose();
		}
		pose.popPose();
	}
}
