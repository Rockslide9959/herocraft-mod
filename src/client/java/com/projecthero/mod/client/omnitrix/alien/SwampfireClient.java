package com.projecthero.mod.client.omnitrix.alien;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.swampfire.SwampfireKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Swampfire's client kit (basic version) -- the player body in his mossy, bark-streaked skin with a maroon bloom of petals
 * on his head and seed pods on his shoulders. Rendered effects (world pass): the Methane Torch's cone of fire, the jet under his
 * feet, the Vine Snare's vines wound round the creature, the swing vine, the methane cloud, the Ignite / Inferno Bloom fireballs
 * and ground ring, the Root Eruption's spikes, the Regrowth's green rings. Particles are only small accents.
 */
public final class SwampfireClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/swampfire.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/swampfire_extras.png");
	private static final float S = 1f / 64f;

	// ---------------------------------------------------------------- third-person poses
	// f(tick, rArm xyz, lArm xyz, body x y, rLeg xyz, lLeg xyz, head x)
	private static final float[][] BLAST = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.55f, -0.1f, 0, 0.3f, 0, 0, 0.1f, -0.35f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(5, -1.5f, -0.1f, 0, 0.3f, 0, 0, 0.1f, -0.35f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(10) };
	private static final float[][] TORCH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.5f, 0.25f, 0, -1.5f, -0.25f, 0, 0.15f, 0, -0.2f, 0, 0, 0.2f, 0, 0, 0),
			QuirkKeyframes.f(14, -1.45f, 0.25f, 0, -1.45f, -0.25f, 0, 0.15f, 0, -0.2f, 0, 0, 0.2f, 0, 0, 0), QuirkKeyframes.rest(18) };
	private static final float[][] LOB = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.9f, 0, 0.2f, 0.4f, 0, 0, -0.15f, 0, 0, 0, 0, 0, 0, 0, -0.1f),
			QuirkKeyframes.f(6, -0.7f, 0, 0, 0.2f, 0, 0, 0.3f, -0.2f, 0, 0, 0, 0, 0, 0, 0.1f), QuirkKeyframes.rest(12) };
	private static final float[][] SNARE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.6f, 0, 0, -1.6f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(6, -2.6f, 0, 0.3f, -2.6f, 0, -0.3f, -0.1f, 0, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(14, -2.6f, 0, 0.3f, -2.6f, 0, -0.3f, -0.1f, 0, 0, 0, 0, 0, 0, 0, -0.2f), QuirkKeyframes.rest(20) };
	private static final float[][] JET = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.6f, 0, 0.6f, 0.6f, 0, -0.6f, 0.1f, 0, 0.4f, 0, 0, -0.4f, 0, 0, -0.2f),
			QuirkKeyframes.f(12, 0.5f, 0, 0.5f, 0.5f, 0, -0.5f, 0.1f, 0, 0.3f, 0, 0, -0.3f, 0, 0, -0.1f), QuirkKeyframes.rest(18) };
	private static final float[][] SWING = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -2.6f, 0, 0, 0.5f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -0.3f),
			QuirkKeyframes.f(14, -2.8f, 0, 0, 0.6f, 0, -0.3f, 0, 0, -0.5f, 0, 0, 0.3f, 0, 0, -0.3f), QuirkKeyframes.rest(20) };
	private static final float[][] CLOUD = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.4f, 0, 1.2f, -0.4f, 0, -1.2f, -0.15f, 0, 0, 0, 0, 0, 0, 0, -0.3f),
			QuirkKeyframes.f(14, -0.4f, 0, 1.3f, -0.4f, 0, -1.3f, -0.15f, 0, 0, 0, 0, 0, 0, 0, -0.3f), QuirkKeyframes.rest(20) };
	private static final float[][] SNAP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -2.0f, -0.3f, 0, 0, 0, 0, 0, -0.3f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(5, -1.6f, 0.2f, 0, 0, 0, 0, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(10) };
	private static final float[][] REGROW = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.2f, 0, 0.5f, -0.2f, 0, -0.5f, 0, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.4f),
			QuirkKeyframes.f(56, -0.2f, 0, 0.5f, -0.2f, 0, -0.5f, 0, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.4f), QuirkKeyframes.rest(62) };
	private static final float[][] BLOOM = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.8f, 0, 0.9f, -0.8f, 0, -0.9f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(12, -0.8f, 0, 0.9f, -0.8f, 0, -0.9f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.3f), QuirkKeyframes.rest(16) };
	private static final float[][] ROOTS = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.9f, 0, 0.1f, -2.9f, 0, -0.1f, -0.1f, 0, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(6, -0.6f, 0, 0, -0.6f, 0, 0, 0.6f, 0, -0.4f, 0, 0, 0.3f, 0, 0, 0.3f),
			QuirkKeyframes.f(14, -0.6f, 0, 0, -0.6f, 0, 0, 0.6f, 0, -0.4f, 0, 0, 0.3f, 0, 0, 0.3f), QuirkKeyframes.rest(20) };
	private static final float[][] CHARGE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(5, -0.3f, 0, 0.3f, -0.3f, 0, -0.3f, 0.45f, 0, -0.3f, 0, 0.2f, -0.3f, 0, -0.2f, 0.4f),
			QuirkKeyframes.f(20, -0.4f, 0, 0.4f, -0.4f, 0, -0.4f, 0.5f, 0, -0.3f, 0, 0.2f, -0.3f, 0, -0.2f, 0.4f) };
	private static final float[][] INFERNO = { QuirkKeyframes.f(0, -0.4f, 0, 0.4f, -0.4f, 0, -0.4f, 0.5f, 0, -0.3f, 0, 0.2f, -0.3f, 0, -0.2f, 0.4f),
			QuirkKeyframes.f(3, -2.8f, 0, 1.0f, -2.8f, 0, -1.0f, -0.25f, 0, 0, 0, 0.25f, 0, 0, -0.25f, -0.5f),
			QuirkKeyframes.f(14, -2.8f, 0, 1.0f, -2.8f, 0, -1.0f, -0.25f, 0, 0, 0, 0.25f, 0, 0, -0.25f, -0.5f), QuirkKeyframes.rest(20) };
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.6f, 0.2f, 0, 0, 0, 0, 0.1f, -0.4f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.rest(7) };
	// v0.17.2 revamp: the vine lash (a whip of the right arm), the seed fan (a sidearm fling), the yank (both arms hauling back),
	// the reform (rising out of a crouch, arms spreading)
	private static final float[][] LASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -2.4f, 0.4f, 0.3f, 0.2f, 0, 0, 0, -0.3f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(5, -1.2f, -0.5f, 0, 0.2f, 0, 0, 0.1f, 0.3f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(10) };
	private static final float[][] FAN = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.0f, 1.0f, 0.6f, 0.3f, 0, 0, 0, 0.4f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(6, -1.4f, -0.9f, 0, 0.3f, 0, 0, 0.1f, -0.4f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(12) };
	private static final float[][] YANK = { QuirkKeyframes.f(0, -1.6f, 0, 0, -1.6f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(4, -0.4f, 0, 0.4f, -0.4f, 0, -0.4f, -0.25f, 0, 0.3f, 0, 0, -0.3f, 0, 0, -0.1f), QuirkKeyframes.rest(12) };
	private static final float[][] REFORM = { QuirkKeyframes.f(0, -0.2f, 0, 0.4f, -0.2f, 0, -0.4f, 0.6f, 0, -0.6f, 0, 0.2f, -0.6f, 0, -0.2f, 0.5f),
			QuirkKeyframes.f(8, -0.6f, 0, 1.1f, -0.6f, 0, -1.1f, -0.1f, 0, 0, 0, 0, 0, 0, 0, -0.3f), QuirkKeyframes.rest(16) };

	// ---------------------------------------------------------------- first-person clips
	private static final FirstPersonClip FP_PALM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -20, 15, 0, 0.12f, 0.15f, -0.3f),
			FirstPersonClip.f(5, -18, 15, 0, 0.12f, 0.15f, -0.3f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_TWO = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -15, 25, 0, 0.18f, 0.15f, -0.3f),
			FirstPersonClip.f(14, -15, 25, 0, 0.18f, 0.15f, -0.3f), FirstPersonClip.f(18, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_LOB = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -70, 5, 0, 0.05f, 0.5f, 0.15f),
			FirstPersonClip.f(6, 10, 15, 0, 0.1f, -0.1f, -0.3f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_RAISE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(5, -60, 10, 10, 0.1f, 0.5f, 0f),
			FirstPersonClip.f(14, -60, 10, 10, 0.1f, 0.5f, 0f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_DOWN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 35, 10, 0, 0.05f, -0.4f, 0.2f),
			FirstPersonClip.f(12, 35, 10, 0, 0.05f, -0.4f, 0.2f), FirstPersonClip.f(18, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SPREAD = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -10, -25, -20, -0.15f, 0.05f, 0f),
			FirstPersonClip.f(14, -10, -25, -20, -0.15f, 0.05f, 0f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SLAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -80, 5, 0, 0.05f, 0.55f, 0.2f),
			FirstPersonClip.f(6, 30, 10, 0, 0.05f, -0.4f, -0.1f), FirstPersonClip.f(14, 30, 10, 0, 0.05f, -0.4f, -0.1f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0))
			.both();
	private static final FirstPersonClip FP_SNAP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -30, 20, 0, 0.15f, 0.2f, -0.2f),
			FirstPersonClip.f(5, -20, 10, 15, 0.1f, 0.15f, -0.2f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -20, 25, 0, 0.15f, 0.08f, -0.25f),
			FirstPersonClip.f(7, 0, 0, 0, 0, 0, 0));

	private static boolean registered;
	/** v0.17.2 revamp: per player, how long the torch had been held (for its fade after the release) and when it was let go. */
	private static final java.util.Map<Integer, Float> TORCH_HELD = new java.util.HashMap<>();
	private static final java.util.Map<Integer, Long> TORCH_END = new java.util.HashMap<>();

	private SwampfireClient() {
	}

	/** One line in {@code OmnitrixClient.initialize}. */
	public static void register() {
		AlienClients.register(Aliens.SWAMPFIRE.id(), new SwampfireClient());
		if (!registered) {
			registered = true;
			OmnitrixRenderers.projectileLook("swampfire_blast", (e, partial, pose, buffers) -> {
				pose.pushPose();
				pose.translate(0.0, e.getBbHeight() * 0.5, 0.0);
				PyroDraw.fireball(pose, buffers, e.size() * 0.75f, e.tickCount + partial, e.getDeltaMovement(), false);
				pose.popPose();
			});
			OmnitrixRenderers.projectileLook("swampfire_seed", SwampfireClient::seed);
			OmnitrixRenderers.projectileLook("swampfire_scatter", SwampfireClient::seed);
			WorldRenderEvents.AFTER_ENTITIES.register(SwampfireClient::renderWorld);
			net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(SwampfireClient::stenchHaze); // v0.17.2 revamp
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
		if ((Omnitrix.state(p).kitFlags & SwampfireKit.F_MULCH) != 0) {
			return; // a heap of mulch (drawn in the world pass)
		}
		float sway = Mth.sin(age * 0.08f) * 4f;
		// v0.17.2 revamp (user's skin, planetminecraft "Swampfire - Ben 10 Alien Force"): the head is a red flower bud with an
		// orange flame on its brow; the extras follow it -- five short red petals opening round the crown (the skin's own reds),
		// and a flickering flame at the top of the bud
		AlienParts.on(pose, model.head, () -> {
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS));
			for (int i = 0; i < 5; i++) {
				pose.pushPose();
				pose.translate(0, -7.6f / 16f, 0);
				pose.mulPose(Axis.YP.rotationDegrees(i * 72f + 36f));
				pose.translate(0, 0, 3.2f / 16f);
				pose.mulPose(Axis.XP.rotationDegrees(-30f - sway));
				AlienParts.box(pose, vc, -1.4f, -3.2f, -0.35f, 1.4f, 0f, 0.35f, 0f, 0f, 8 * S, 8 * S, 0xFFFFFFFF, light);
				pose.popPose();
			}
			float flick = 1f + Mth.sin(age * 0.9f) * 0.15f + Mth.sin(age * 2.3f) * 0.08f;
			pose.pushPose();
			pose.translate(0, -8f / 16f, -1.2f / 16f);
			pose.scale(1f, flick, 1f);
			AlienParts.box(pose, vc, -1.0f, -2.6f, -0.6f, 1.0f, 0f, 0.6f, 12 * S, 0f, 16 * S, 4 * S, 0xFFFFFFFF, LightTexture.FULL_BRIGHT);
			AlienParts.box(pose, vc, -0.45f, -3.6f, -0.3f, 0.45f, -2.4f, 0.3f, 13 * S, S, 15 * S, 3 * S, 0xFFFFFFFF, LightTexture.FULL_BRIGHT);
			pose.popPose();
		});
		// seed pods on the shoulders (olive with the skin's red spots)
		AlienParts.on(pose, model.body, () -> {
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS));
			AlienParts.box(pose, vc, -5.4f, -0.8f, -1.2f, -3.2f, 1.2f, 1.0f, 16 * S, 0f, 24 * S, 8 * S, 0xFFFFFFFF, light);
			AlienParts.box(pose, vc, 3.2f, -0.8f, -1.2f, 5.4f, 1.2f, 1.0f, 16 * S, 0f, 24 * S, 8 * S, 0xFFFFFFFF, light);
			// a vine strand hanging down the back
			AlienParts.box(pose, vc, -0.6f, 0.5f, 2.0f, 0.6f, 9.0f, 2.8f, 8 * S, 0f, 12 * S, 8 * S, 0xFFFFFFFF, light);
		});
	}

	// ---------------------------------------------------------------- poses

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		int flags = Omnitrix.state(p).kitFlags;
		if ((flags & SwampfireKit.F_TORCH) != 0) {
			return QuirkKeyframes.play(m, TORCH, Math.min(ticks, 10f), 1f); // both palms held out while Shift+R is down
		}
		if ((flags & SwampfireKit.F_SWING) != 0) {
			return QuirkKeyframes.play(m, SWING, Math.min(ticks, 10f), 1f); // hanging on the vine
		}
		float[][] clip = switch (animId) {
			case SwampfireKit.ANIM_LASH -> LASH;
			case SwampfireKit.ANIM_SCATTER -> FAN;
			case SwampfireKit.ANIM_YANK -> YANK;
			case SwampfireKit.ANIM_REFORM -> REFORM;
			case SwampfireKit.ANIM_BLAST -> BLAST;
			case SwampfireKit.ANIM_TORCH -> TORCH;
			case SwampfireKit.ANIM_SEED -> LOB;
			case SwampfireKit.ANIM_SNARE -> SNARE;
			case SwampfireKit.ANIM_JET -> JET;
			case SwampfireKit.ANIM_SWING -> SWING;
			case SwampfireKit.ANIM_CLOUD -> CLOUD;
			case SwampfireKit.ANIM_IGNITE -> SNAP;
			case SwampfireKit.ANIM_REGROW -> REGROW;
			case SwampfireKit.ANIM_BLOOM -> BLOOM;
			case SwampfireKit.ANIM_ROOTS -> ROOTS;
			case SwampfireKit.ANIM_CHARGE -> CHARGE;
			case SwampfireKit.ANIM_INFERNO -> INFERNO;
			case SwampfireKit.ANIM_PUNCH -> PUNCH;
			default -> null;
		};
		return clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case SwampfireKit.ANIM_BLAST -> FP_PALM;
			case SwampfireKit.ANIM_TORCH -> FP_TWO;
			case SwampfireKit.ANIM_SEED -> FP_LOB;
			case SwampfireKit.ANIM_SNARE, SwampfireKit.ANIM_SWING -> FP_RAISE;
			case SwampfireKit.ANIM_JET, SwampfireKit.ANIM_REGROW -> FP_DOWN;
			case SwampfireKit.ANIM_CLOUD, SwampfireKit.ANIM_BLOOM, SwampfireKit.ANIM_CHARGE -> FP_SPREAD;
			case SwampfireKit.ANIM_IGNITE -> FP_SNAP;
			case SwampfireKit.ANIM_ROOTS, SwampfireKit.ANIM_INFERNO -> FP_SLAM;
			case SwampfireKit.ANIM_PUNCH -> FP_PUNCH;
			case SwampfireKit.ANIM_LASH -> FP_SNAP;
			case SwampfireKit.ANIM_SCATTER -> FP_LOB;
			case SwampfireKit.ANIM_YANK -> FP_RAISE;
			case SwampfireKit.ANIM_REFORM -> FP_SPREAD;
			default -> null;
		};
	}

	/** v0.17.2 revamp, Reassembly: no body while he is a heap of mulch; it fades back in over the 0.6 s reform. */
	@Override
	public int bodyAlpha(AbstractClientPlayer p) {
		OmnitrixState st = Omnitrix.state(p);
		if ((st.kitFlags & SwampfireKit.F_MULCH) != 0) {
			return 0;
		}
		if (st.animId == SwampfireKit.ANIM_REFORM) {
			float t = animTicks(p, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
			if (t >= 0f && t < 12f) {
				return (int) Mth.clamp(30 + 225 * t / 12f, 30, 255);
			}
		}
		return 255;
	}

	/** v0.17.2 revamp: the Reassembly row -- ready, or the time until it is. */
	@Override
	public void hud(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.player.LocalPlayer p, int x, int y, int w, float partial) {
		if (!(com.projecthero.mod.omnitrix.alien.Aliens.kit(SwampfireKit.ID) instanceof SwampfireKit k)) {
			return;
		}
		int left = k.cooldownLeft(p, SwampfireKit.REASSEMBLY);
		OmnitrixState st = Omnitrix.state(p);
		boolean mulch = (st.kitFlags & SwampfireKit.F_MULCH) != 0;
		float frac = left <= 0 ? 1f : 1f - left / (float) SwampfireKit.REASSEMBLY_COOLDOWN;
		String value = mulch ? "..." : left <= 0 ? "Ready" : String.format(java.util.Locale.ROOT, "%.0fs", Math.ceil(left / 20f));
		int colour = left <= 0 ? 0xFF5AC05A : 0xFF6A7A40;
		if ((st.kitFlags & SwampfireKit.F_SURGE) != 0) {
			long until = st.readyAt(SwampfireKit.ID + "." + SwampfireKit.SURGE_UNTIL);
			float sl = Math.max(0f, until - (p.level().getGameTime() + partial));
			com.projecthero.mod.client.omnitrix.OmnitrixHud.meter(g, Minecraft.getInstance(), x, y - 16,
					net.minecraft.network.chat.Component.translatable("hud.projecthero.omnitrix.swampfire.surge"), sl / SwampfireKit.SURGE_TICKS, 0xFFFF8A2A,
					String.format(java.util.Locale.ROOT, "%.0fs", Math.ceil(sl / 20f)), 0xFFFFFFFF);
		}
		com.projecthero.mod.client.omnitrix.OmnitrixHud.meter(g, Minecraft.getInstance(), x, y - 6,
				net.minecraft.network.chat.Component.translatable("hud.projecthero.omnitrix.swampfire.reassembly"), frac, colour, value, 0xFFFFFFFF);
	}

	@Override
	public int hudHeight() {
		return Minecraft.getInstance().player != null && (Omnitrix.state(Minecraft.getInstance().player).kitFlags & SwampfireKit.F_SURGE) != 0 ? 20 : 10;
	}

	/**
	 * v0.17.2 revamp, Swamp Stench: no particles -- a faint green haze over the screen of a player standing in his smell (nauseous,
	 * within a few blocks of a Swampfire who is not himself).
	 */
	private static void stenchHaze(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui || !mc.player.hasEffect(net.minecraft.world.effect.MobEffects.CONFUSION)) {
			return;
		}
		double best = Double.MAX_VALUE;
		for (Player pl : mc.level.players()) {
			if (pl == mc.player || !Omnitrix.hasPower(pl)) {
				continue;
			}
			OmnitrixState st = Omnitrix.state(pl);
			if (SwampfireKit.ID.equals(st.active) && st.transformed()) {
				best = Math.min(best, pl.distanceTo(mc.player));
			}
		}
		if (best > 5.0) {
			return;
		}
		float k = (float) Mth.clamp(1.0 - (best - 2.0) / 3.0, 0.0, 1.0);
		float pulse = 0.8f + 0.2f * Mth.sin((mc.player.tickCount + delta.getGameTimeDeltaPartialTick(false)) * 0.15f);
		int a = (int) (60 * k * pulse);
		if (a > 0) {
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | 0x5A7A1C);
		}
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		var r = p.getRandom();
		// a faint methane haze and the odd ember off his body
		if (r.nextInt(14) == 0) {
			p.level().addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, p.getX() + (r.nextDouble() - 0.5) * 0.6, p.getY() + 0.4 + r.nextDouble() * 1.4,
					p.getZ() + (r.nextDouble() - 0.5) * 0.6, 0, 0.01, 0);
		}
	}

	// ---------------------------------------------------------------- the world pass

	private static float animTicks(Player p, float partial) {
		return p.level().getGameTime() - Omnitrix.state(p).animStart + partial;
	}

	/** The right hand (or between both) in world space, aimed along the look. */
	static Vec3 hand(Player p, float partial, boolean both) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 look = p.getViewVector(partial);
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
			return p.getEyePosition(partial).add(look.scale(0.6)).add(right.scale(both ? 0.05 : 0.3)).subtract(up.scale(0.3));
		}
		float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 bodyRight = new Vec3(-Math.cos(bodyYaw), 0, -Math.sin(bodyYaw));
		Vec3 shoulder = p.getPosition(partial).add(0, 1.38 * p.getScale(), 0).add(bodyRight.scale(both ? 0.0 : 0.36 * p.getScale()));
		return shoulder.add(look.scale(0.7 * p.getScale()));
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
		for (Player pl : mc.level.players()) {
			if (!Omnitrix.hasPower(pl)) {
				continue;
			}
			OmnitrixState st = Omnitrix.state(pl);
			if (!SwampfireKit.ID.equals(st.active) || !st.transformed()) {
				continue;
			}
			float t = animTicks(pl, partial);
			float time = pl.tickCount + partial;
			long now = pl.level().getGameTime();
			int flags = st.kitFlags;
			// v0.17.2 revamp: the held torch -- the cone grows with the hold, and fades out over a few ticks after the release
			if ((flags & SwampfireKit.F_TORCH) != 0) {
				float held = now - st.readyAt(SwampfireKit.ID + "." + SwampfireKit.TORCH_START) + partial;
				torch(pl, partial, held, time, cam, pose, buffers, 1f);
				TORCH_HELD.put(pl.getId(), held);
				TORCH_END.remove(pl.getId());
			} else if (TORCH_HELD.containsKey(pl.getId())) {
				long end = TORCH_END.computeIfAbsent(pl.getId(), k -> now);
				float f = 1f - (now + partial - end) / 4f;
				if (f <= 0f || now < end) {
					TORCH_HELD.remove(pl.getId());
					TORCH_END.remove(pl.getId());
				} else {
					torch(pl, partial, TORCH_HELD.get(pl.getId()), time, cam, pose, buffers, f);
				}
			}
			// the swing tether while Shift+X is held
			if ((flags & SwampfireKit.F_SWING) != 0) {
				Vec3 a = SwampfireKit.pos(pl, SwampfireKit.P_ANCHOR);
				if (a != null) {
					vine(hand(pl, partial, false), a, Math.min(1f, t / 3f), 1f, time, cam, pose, buffers);
				}
			}
			if ((flags & SwampfireKit.F_TANGLE) != 0) {
				Vec3 c = SwampfireKit.pos(pl, SwampfireKit.P_TANGLE);
				if (c != null) {
					float left = st.readyAt(SwampfireKit.ID + "." + SwampfireKit.TANGLE_UNTIL) - (now + partial);
					tangle(c, Math.min(1f, (SwampfireKit.TANGLE_TICKS - left) / 5f), Math.min(1f, left / 6f), time, cam, pose, buffers);
				}
			}
			for (int i = 0; i < SwampfireKit.MAX_MINES; i++) {
				long until = st.readyAt(SwampfireKit.ID + "." + SwampfireKit.MINE_UNTIL + i);
				Vec3 m = until > now ? SwampfireKit.pos(pl, SwampfireKit.P_MINE + i) : null;
				if (m != null) {
					mine(m, until - (now + partial), time + i * 7, cam, pose, buffers);
				}
			}
			float snapT = now - st.readyAt(SwampfireKit.ID + ".snap_at") + partial;
			if (snapT >= 0f && snapT < 12f) {
				Vec3 c = SwampfireKit.pos(pl, SwampfireKit.P_SNAP);
				if (c != null) {
					tangle(c, Math.min(1f, snapT / 2f), Math.min(1f, (12f - snapT) / 4f), time, cam, pose, buffers);
				}
			}
			if ((flags & SwampfireKit.F_MULCH) != 0) {
				mulch(pl.getPosition(partial), 1f, time, cam, pose, buffers);
			}
			if ((flags & SwampfireKit.F_SURGE) != 0 && (flags & SwampfireKit.F_MULCH) == 0) {
				surge(pl, partial, time, cam, pose, buffers);
			}
			switch (st.animId) {
				case SwampfireKit.ANIM_LASH, SwampfireKit.ANIM_YANK -> {
					Vec3 e = SwampfireKit.pos(pl, SwampfireKit.P_LASH);
					if (e != null && t < 8) {
						float out = st.animId == SwampfireKit.ANIM_YANK ? 1f : Math.min(1f, t / 2f);
						float f = t > 5 ? Math.max(0f, 1f - (t - 5) / 3f) : 1f;
						vine(hand(pl, partial, false), e, out, f, time, cam, pose, buffers);
					}
				}
				case SwampfireKit.ANIM_REFORM -> {
					if (t < 14) {
						mulch(pl.getPosition(partial), Math.max(0f, 1f - t / 10f), time, cam, pose, buffers);
						reformRing(pl.getPosition(partial), t / 14f, cam, pose, buffers);
					}
				}
				case SwampfireKit.ANIM_JET -> {
					if (t < 10) {
						jet(pl, partial, t, cam, pose, buffers);
					}
				}
				case SwampfireKit.ANIM_SWING -> {
					Vec3 a = SwampfireKit.pos(pl, SwampfireKit.P_ANCHOR);
					if (a != null && t < 14 && (flags & SwampfireKit.F_SWING) == 0) {
						vine(hand(pl, partial, false), a, Math.min(1f, t / 3f), t > 10 ? 1f - (t - 10) / 4f : 1f, time, cam, pose, buffers);
					}
				}
				case SwampfireKit.ANIM_ROOTS -> {
					Vec3 o = SwampfireKit.pos(pl, SwampfireKit.P_ROOTS);
					Vec3 d = SwampfireKit.rootsDir(pl);
					if (o != null && d != null && t < SwampfireKit.ROOTS_SPIKES * 2 + 16) {
						roots(o, d, t, cam, pose, buffers);
					}
				}
				case SwampfireKit.ANIM_IGNITE -> {
					Vec3 b = SwampfireKit.pos(pl, SwampfireKit.P_BLAST);
					if (b != null && t < 14) {
						burst(b, SwampfireKit.IGNITE_RADIUS, t, 14f, time, cam, pose, buffers);
					}
				}
				case SwampfireKit.ANIM_INFERNO -> {
					Vec3 b = SwampfireKit.pos(pl, SwampfireKit.P_BLAST);
					if (b != null && t < 20) {
						burst(b, SwampfireKit.INFERNO_RADIUS, t, 20f, time, cam, pose, buffers);
					}
				}
				default -> {
				}
			}
			if ((st.kitFlags & SwampfireKit.F_CLOUD) != 0) {
				Vec3 c = SwampfireKit.pos(pl, SwampfireKit.P_CLOUD);
				if (c != null) {
					cloud(c, st.readyAt(SwampfireKit.ID + "." + SwampfireKit.CLOUD_UNTIL) - (pl.level().getGameTime() + partial), time, cam, pose, buffers);
				}
			}
			if ((st.kitFlags & SwampfireKit.F_SNARE) != 0) {
				long id = st.readyAt(SwampfireKit.ID + "." + SwampfireKit.SNARE_TARGET);
				Entity e = id > 0 ? mc.level.getEntity((int) (id - 1)) : null;
				if (e != null) {
					long until = st.readyAt(SwampfireKit.ID + "." + SwampfireKit.SNARE_UNTIL);
					float left = until - (pl.level().getGameTime() + partial);
					snare(e, partial, Math.min(1f, (SwampfireKit.SNARE_TICKS - left) / 6f), Math.min(1f, left / 6f), time, cam, pose, buffers);
				}
			}
			if ((st.kitFlags & SwampfireKit.F_REGROW) != 0) {
				regrow(pl, partial, time, cam, pose, buffers);
			}
			if ((st.kitFlags & SwampfireKit.F_CHARGE) != 0) {
				charge(pl, partial, t, time, cam, pose, buffers);
			}
		}
	}

	/**
	 * The Methane Torch: a widening cone of burning gas from his palms, white-yellow at the hands, orange and red out at the end.
	 * v0.17.2 revamp: held -- its length follows the server's reach ({@link SwampfireKit#torchReach}), {@code fade} after the release.
	 */
	private static void torch(Player pl, float partial, float held, float time, Vec3 cam, PoseStack pose, MultiBufferSource buffers, float fade) {
		float grow = Mth.clamp(held / 3f, 0f, 1f);
		Vec3 look = pl.getViewVector(partial);
		Vec3 h = hand(pl, partial, true);
		float len = (float) SwampfireKit.torchReach(held) * grow;
		Vec3 end = h.add(look.scale(len)).subtract(cam);
		VertexConsumer add = PyroDraw.additive(buffers);
		pose.pushPose();
		pose.translate(end.x, end.y, end.z);
		PyroDraw.alignY(pose, look.scale(-1));
		float flick = 1f + Mth.sin(time * 1.7f) * 0.06f;
		PyroDraw.cone(pose, add, 2.6f * grow * flick, len, PyroDraw.dim(0xFF7A1C08, fade * 0.8f), PyroDraw.dim(0xFFFF9A30, fade));
		PyroDraw.cone(pose, add, 1.3f * grow * flick, len, PyroDraw.dim(0xFFB04010, fade * 0.8f), PyroDraw.dim(0xFFFFE8A0, fade));
		pose.popPose();
		// a few licks of real fire along it
		VertexConsumer fire = PyroDraw.fireBuffer(buffers);
		for (int i = 1; i <= 4; i++) {
			float u = i / 4.5f;
			Vec3 at = h.add(look.scale(len * u)).subtract(cam).add(0, -0.4 * u, 0);
			float s = 0.5f + 1.6f * u;
			PyroDraw.fireBillboard(pose, fire, at, Vec3.ZERO, s * fade, s * 1.2f * fade, (int) (time / 2) + i);
		}
		PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), h.subtract(cam), 0.5f, PyroDraw.dim(0xFFFFC060, fade));
	}

	/** The Methane Jet: a short blast of fire downward from his feet. */
	private static void jet(Player pl, float partial, float t, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		float fade = 1f - t / 10f;
		Vec3 feet = pl.getPosition(partial).subtract(cam);
		VertexConsumer add = PyroDraw.additive(buffers);
		pose.pushPose();
		pose.translate(feet.x, feet.y - 1.8, feet.z);
		PyroDraw.cone(pose, add, 0.8f, 1.9f, PyroDraw.dim(0xFF902000, fade), PyroDraw.dim(0xFFFFD070, fade));
		PyroDraw.cone(pose, add, 0.35f, 1.9f, PyroDraw.dim(0xFFFF8030, fade), PyroDraw.dim(0xFFFFF4C0, fade));
		pose.popPose();
	}

	/** A vine from {@code a} to {@code b}, grown out {@code out} of the way, with a slight sag and a twist. */
	private static void vine(Vec3 a, Vec3 b, float out, float fade, float time, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		Vec3 ca = a.subtract(cam);
		Vec3 cb = ca.lerp(b.subtract(cam), out);
		double dist = ca.distanceTo(cb);
		List<Vec3> pts = new ArrayList<>();
		int n = 20;
		for (int i = 0; i <= n; i++) {
			double u = i / (double) n;
			double sag = Math.sin(u * Math.PI) * (0.05 * dist) * (1.2 - out);
			double wig = Math.sin(u * 11 + time * 0.6) * 0.05 * Math.sin(u * Math.PI);
			pts.add(ca.lerp(cb, u).add(wig, -sag, wig));
		}
		VertexConsumer solid = PyroDraw.solidBuffer(buffers);
		final float f = fade;
		PyroDraw.ribbon(pose, solid, pts, u -> 0.16 * f, u -> PyroDraw.lerpRgb(0xFF1E4A1C, 0xFF3C8A3E, (float) u));
		PyroDraw.ribbon(pose, solid, pts, u -> 0.06 * f, u -> 0xFF0E160C);
	}

	/** The Vine Snare: three vines spiralling up out of the ground round the creature. */
	private static void snare(Entity e, float partial, float grow, float fade, float time, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		Vec3 base = e.getPosition(partial).subtract(cam);
		double r = e.getBbWidth() * 0.65 + 0.15;
		double hgt = e.getBbHeight() * 0.85 * Math.min(grow, fade);
		VertexConsumer solid = PyroDraw.solidBuffer(buffers);
		for (int k = 0; k < 3; k++) {
			List<Vec3> pts = new ArrayList<>();
			int n = 18;
			for (int i = 0; i <= n; i++) {
				double u = i / (double) n;
				double a = k * Mth.TWO_PI / 3 + u * 5.5 + time * 0.02;
				double rr = r * (1.0 - 0.25 * u);
				pts.add(base.add(Math.cos(a) * rr, u * hgt, Math.sin(a) * rr));
			}
			PyroDraw.ribbon(pose, solid, pts, u -> 0.17 * (1.0 - 0.5 * u), u -> PyroDraw.lerpRgb(0xFF16301A, 0xFF4FA048, (float) u));
		}
	}

	/** The Root Eruption: a spike of roots bursting up at each step of the line, then sinking back. */
	private static void roots(Vec3 from, Vec3 dir, float t, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		VertexConsumer solid = PyroDraw.solidBuffer(buffers);
		for (int i = 0; i < SwampfireKit.ROOTS_SPIKES; i++) {
			float lt = t - i * 2f;
			if (lt < 0f || lt > 16f) {
				continue;
			}
			float h = lt < 3f ? lt / 3f : lt > 11f ? Math.max(0f, 1f - (lt - 11f) / 5f) : 1f;
			Vec3 at = from.add(dir.scale(SwampfireKit.ROOTS_SPACING * (i + 1))).subtract(cam);
			pose.pushPose();
			pose.translate(at.x, at.y - 0.2, at.z);
			PyroDraw.cone(pose, solid, 0.42f, 2.0f * h, 0xFF2A1C10, 0xFF4F9A40);
			for (int k = 0; k < 3; k++) {
				pose.pushPose();
				pose.mulPose(Axis.YP.rotationDegrees(k * 120f + i * 37f));
				pose.mulPose(Axis.ZP.rotationDegrees(28f));
				PyroDraw.cone(pose, solid, 0.2f, 1.2f * h, 0xFF2A1C10, 0xFF3C7A30);
				pose.popPose();
			}
			pose.popPose();
		}
	}

	/** The methane cloud: a slow, churning haze of yellow-green gas. */
	private static void cloud(Vec3 c, float left, float time, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		float fade = Mth.clamp(left / 20f, 0f, 1f);
		float grow = Mth.clamp((SwampfireKit.CLOUD_TICKS - left) / 10f, 0.2f, 1f);
		Vec3 at = c.subtract(cam);
		VertexConsumer add = PyroDraw.additive(buffers);
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		pose.scale(1f, 0.55f, 1f);
		float r = (float) SwampfireKit.CLOUD_RADIUS * grow;
		PyroDraw.sphere(pose, add, r, PyroDraw.dim(0xFF2A3A0C, fade), PyroDraw.dim(0xFF0E1604, fade), 0.3f, time * 0.4f, PyroDraw.FULL);
		PyroDraw.sphere(pose, add, r * 0.6f, PyroDraw.dim(0xFF34420E, fade), PyroDraw.dim(0xFF141E06, fade), 0.35f, -time * 0.5f + 4f, PyroDraw.FULL);
		pose.popPose();
	}

	/** Ignite / Inferno Bloom: a fireball swelling to {@code radius}, and a ring of fire running out along the ground. */
	private static void burst(Vec3 c, float radius, float t, float life, float time, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		float k = t / life;
		float r = radius * (float) Math.sin(Math.min(1.0, k * 1.6) * Math.PI * 0.5) * (k > 0.6f ? 1f - (k - 0.6f) / 0.4f : 1f);
		Vec3 at = c.subtract(cam);
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		if (r > 0.05f) {
			PyroDraw.fireball(pose, buffers, r * 0.7f, time, null, true);
		}
		float ring = radius * Math.min(1f, k * 1.4f);
		float fade = 1f - k;
		PyroDraw.annulus(pose, PyroDraw.additive(buffers), Math.max(0f, ring - 1.2f), ring, PyroDraw.dim(0xFF301004, fade), PyroDraw.dim(0xFFFF8A2A, fade),
				-0.9f);
		pose.popPose();
	}

	/** Regrowth: rings of green light rising round him. */
	private static void regrow(Player pl, float partial, float time, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		Vec3 at = pl.getPosition(partial).subtract(cam);
		VertexConsumer add = PyroDraw.additive(buffers);
		float hgt = pl.getBbHeight();
		for (int i = 0; i < 3; i++) {
			float u = ((time * 0.05f) + i / 3f) % 1f;
			pose.pushPose();
			pose.translate(at.x, at.y + u * hgt, at.z);
			PyroDraw.band(pose, add, 0.55f + 0.1f * (1f - u), 0.06f, PyroDraw.dim(0xFF5AE06A, 1f - u), 0xFF000000);
			pose.popPose();
		}
	}

	/** Inferno Bloom's build-up: fire rings closing in on him. */
	private static void charge(Player pl, float partial, float t, float time, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		Vec3 at = pl.getPosition(partial).subtract(cam);
		VertexConsumer add = PyroDraw.additive(buffers);
		float k = Mth.clamp(t / SwampfireKit.CHARGE_TICKS, 0f, 1f);
		for (int i = 0; i < 2; i++) {
			float r = 3.5f * (1f - ((k + i * 0.5f) % 1f)) + 0.4f;
			pose.pushPose();
			pose.translate(at.x, at.y + 0.1 + i * 0.9, at.z);
			PyroDraw.band(pose, add, r, 0.1f, PyroDraw.dim(0xFFFF8A2A, 0.4f + 0.6f * k), 0xFF000000);
			pose.popPose();
		}
		PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), at.add(0, pl.getBbHeight() * 0.5, 0), 1.2f + k, PyroDraw.dim(0xFFFF7A2A, 0.6f * k));
	}

	/** v0.17.2 revamp: the Seed Bomb's tangle (and a burst seed's snapping vines) -- five vines curling up out of the ground in a ring. */
	private static void tangle(Vec3 c, float grow, float fade, float time, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		Vec3 base = c.subtract(cam);
		VertexConsumer solid = PyroDraw.solidBuffer(buffers);
		float k = Math.max(0f, Math.min(grow, fade));
		for (int v = 0; v < 5; v++) {
			double a0 = v * Mth.TWO_PI / 5 + 0.3;
			double r0 = SwampfireKit.TANGLE_RADIUS * 0.6;
			List<Vec3> pts = new ArrayList<>();
			int n = 12;
			for (int i = 0; i <= n; i++) {
				double u = i / (double) n;
				double a = a0 + u * 2.2 + Math.sin(time * 0.15 + v) * 0.1;
				double r = r0 * (1.0 - 0.6 * u);
				pts.add(base.add(Math.cos(a) * r, -0.1 + u * 1.5 * k, Math.sin(a) * r));
			}
			PyroDraw.ribbon(pose, solid, pts, u -> 0.15 * k * (1.0 - 0.6 * u), u -> PyroDraw.lerpRgb(0xFF1E3410, 0xFF5C8A2A, (float) u));
		}
	}

	/** v0.17.2 revamp: a planted Seed Scatter seed -- a knobbly pod sunk in the ground, pulsing slowly. */
	private static void mine(Vec3 at, float left, float time, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		float fade = Mth.clamp(left / 10f, 0f, 1f);
		Vec3 c = at.subtract(cam);
		float pulse = 0.85f + 0.15f * Mth.sin(time * 0.12f);
		pose.pushPose();
		pose.translate(c.x, c.y + 0.08, c.z);
		pose.scale(1f, 0.7f, 1f);
		PyroDraw.sphere(pose, PyroDraw.solidBuffer(buffers), 0.2f * pulse * fade, 0xFF6A8A2A, 0xFF2A3A10, 0.25f, time * 0.3f, LightTexture.FULL_BRIGHT);
		pose.popPose();
		PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), c.add(0, 0.15, 0), 0.35f * pulse, PyroDraw.dim(0xFF7A1010, 0.5f * fade));
	}

	/** v0.17.2 revamp, Reassembly: a mound of swamp mulch with vine tendrils, {@code k} of its full size. */
	private static void mulch(Vec3 at, float k, float time, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		if (k <= 0.02f) {
			return;
		}
		Vec3 c = at.subtract(cam);
		VertexConsumer solid = PyroDraw.solidBuffer(buffers);
		pose.pushPose();
		pose.translate(c.x, c.y + 0.05, c.z);
		pose.scale(1f, 0.45f, 1f);
		PyroDraw.sphere(pose, solid, 0.75f * k, 0xFF4C5C1F, 0xFF2A2412, 0.35f, time * 0.05f, LightTexture.FULL_BRIGHT);
		pose.popPose();
		// a red bloom on top (his head, folded shut) and two lumps (his hands)
		pose.pushPose();
		pose.translate(c.x + 0.15, c.y + 0.3 * k, c.z - 0.1);
		PyroDraw.sphere(pose, solid, 0.22f * k, 0xFFC11320, 0xFF780810, 0.25f, time * 0.04f, LightTexture.FULL_BRIGHT);
		pose.popPose();
		for (int i = 0; i < 2; i++) {
			pose.pushPose();
			pose.translate(c.x + (i == 0 ? -0.5 : 0.45) * k, c.y + 0.08, c.z + (i == 0 ? 0.3 : -0.35) * k);
			PyroDraw.sphere(pose, solid, 0.15f * k, 0xFF556723, 0xFF2F3914, 0.3f, time * 0.06f + i, LightTexture.FULL_BRIGHT);
			pose.popPose();
		}
		for (int v = 0; v < 4; v++) {
			double a0 = v * Mth.TWO_PI / 4;
			List<Vec3> pts = new ArrayList<>();
			for (int i = 0; i <= 8; i++) {
				double u = i / 8.0;
				double r = 0.5 + 0.5 * u;
				pts.add(c.add(Math.cos(a0 + u * 0.8) * r * k, 0.05 + Math.sin(u * Math.PI) * 0.15 * k, Math.sin(a0 + u * 0.8) * r * k));
			}
			final float kk = k;
			PyroDraw.ribbon(pose, solid, pts, u -> 0.07 * kk, u -> PyroDraw.lerpRgb(0xFF2F3914, 0xFF556723, (float) u));
		}
	}

	/** v0.17.2 revamp: the reform's single green ring. */
	private static void reformRing(Vec3 at, float k, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		Vec3 c = at.subtract(cam);
		float r = 0.4f + 1.8f * k;
		pose.pushPose();
		pose.translate(c.x, c.y + 0.05, c.z);
		PyroDraw.annulus(pose, PyroDraw.additive(buffers), Math.max(0f, r - 0.35f), r, 0xFF000000, PyroDraw.dim(0xFF5AE06A, 1f - k), 0f);
		pose.popPose();
	}

	/** v0.17.2 revamp, Methane Surge: his palms burn (fire licking off both hands) while it lasts. */
	private static void surge(Player pl, float partial, float time, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		float bodyYaw = Mth.rotLerp(partial, pl.yBodyRotO, pl.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Math.cos(bodyYaw), 0, -Math.sin(bodyYaw));
		Vec3 base = pl.getPosition(partial);
		VertexConsumer fire = PyroDraw.fireBuffer(buffers);
		VertexConsumer glow = PyroDraw.glowBuffer(buffers);
		Minecraft mc = Minecraft.getInstance();
		boolean fp = pl == mc.player && mc.options.getCameraType().isFirstPerson();
		for (int s = -1; s <= 1; s += 2) {
			Vec3 at = fp ? hand(pl, partial, false).add(right.scale(s < 0 ? -0.55 : 0)) : base.add(right.scale(0.38 * s)).add(0, 0.75, 0);
			Vec3 c = at.subtract(cam);
			PyroDraw.fireBillboard(pose, fire, c, Vec3.ZERO, fp ? 0.25f : 0.35f, fp ? 0.35f : 0.5f, (int) (time / 2) + s);
			PyroDraw.glow(pose, glow, c, 0.4f, PyroDraw.dim(0xFFFF8A2A, 0.6f));
		}
	}

	/** The Seed Bomb in flight: a knobbly green pod with a faint glow. */
	private static void seed(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		float time = e.tickCount + partial;
		pose.pushPose();
		pose.translate(0.0, e.getBbHeight() * 0.5, 0.0);
		pose.mulPose(Axis.YP.rotationDegrees(time * 20f));
		PyroDraw.sphere(pose, PyroDraw.solidBuffer(buffers), e.size() * 0.55f, 0xFF7CC466, 0xFF2A5A20, 0.18f, time, PyroDraw.FULL);
		PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), Vec3.ZERO, e.size() * 1.2f, 0xFF2A5010);
		pose.popPose();
	}
}
