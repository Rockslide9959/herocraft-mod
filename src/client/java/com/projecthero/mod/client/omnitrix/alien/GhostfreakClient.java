package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakKit;
import com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakPossessPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1: Ghostfreak's client kit -- the pale, black-striped Ectonurite skin (solid; see-through only while phased), NO LEGS: a
 * wispy tail of shrinking, swaying segments trails from the waist instead, one big glowing purple eye, and a drifting
 * ghost-hands pose. A pose for every move.
 *
 * <p>v0.17.2 revamp 2: <b>True Form</b> -- the chest skin is two flaps that swing open over the peel and shut over the last
 * second (the wrap), the striped true body underneath, four black-and-white segmented tentacles swaying out of it and a bone
 * jaw under the eye; the eye's purple rim while Creature of the Dark is on. And the <b>possessed player's</b> side of a
 * possession ({@link GhostfreakPossessPayload}): while the stream keeps coming, this client walks and looks by Ghostfreak's keys
 * and takes none of its own.
 */
public final class GhostfreakClient implements AlienClientKit {
	// v0.17.1 (user: "add abilities to the V key for every alien"): the V move's pose and first-person clip
	private static final float[][] SIGHT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.2f, -0.6f, 0f, 0.1f, 0f, 0f, 0f, 0.2f, 0f, 0f, 0f, 0f, 0f, 0f, 0.1f),
			QuirkKeyframes.f(10, -2.1f, -0.6f, 0f, 0.1f, 0f, 0f, 0f, 0.2f, 0f, 0f, 0f, 0f, 0f, 0f, 0.1f), QuirkKeyframes.rest(15) };
	private static final FirstPersonClip FP_SIGHT = FirstPersonClip.of(FirstPersonClip.f(0, 0f, 0f, 0f, 0f, 0f, 0f), FirstPersonClip.f(3, -70f, 35f, 0f, 0.1f, 0.4f, 0.1f),
			FirstPersonClip.f(10, -68f, 35f, 0f, 0.1f, 0.4f, 0.1f), FirstPersonClip.f(15, 0f, 0f, 0f, 0f, 0f, 0f));

	/** v0.17.1: Spirit Sight -- every living thing within 32 blocks outlined, on the wearer's own screen only. */
	@Override
	public boolean glows(LocalPlayer viewer, net.minecraft.world.entity.Entity e) {
		return (Omnitrix.state(viewer).kitFlags & GhostfreakKit.F_SIGHT) != 0 && e instanceof net.minecraft.world.entity.LivingEntity le && le.isAlive()
				&& !(e instanceof net.minecraft.world.entity.decoration.ArmorStand)
				&& e.distanceToSqr(viewer) <= GhostfreakKit.SIGHT_RANGE * GhostfreakKit.SIGHT_RANGE;
	}

	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/ghostfreak.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/ghostfreak_extras.png");
	private static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/mutation/white.png");

	private static final float[] EYE = { 0.5f, 0f, 1f, 0.5f };
	private static final int TRUE_BLACK = 0xFF15141A;
	private static final int TRUE_WHITE = 0xFFE9E6DE;
	private static final int SKIN_TONE = 0xFFC8D2D2;

	private static final float[][] CLAW_SWIPE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -2.7f, 0.6f, 0.5f, -0.3f, 0, 0, -0.1f, 0.4f, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(5, -0.9f, -0.8f, -0.3f, -0.4f, 0, 0, 0.35f, -0.4f, 0, 0, 0, 0, 0, 0, 0.2f), QuirkKeyframes.rest(10) };
	private static final float[][] SCREAM = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.6f, 0, 0.7f, 0.6f, 0, -0.7f, -0.3f, 0, 0, 0, 0, 0, 0, 0, -0.6f),
			QuirkKeyframes.f(6, 0.9f, 0, 0.9f, 0.9f, 0, -0.9f, 0.35f, 0, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(16, 0.9f, 0, 0.9f, 0.9f, 0, -0.9f, 0.35f, 0, 0, 0, 0, 0, 0, 0, -0.2f), QuirkKeyframes.rest(20) };
	private static final float[][] POSSESS = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.0f, -0.2f, 0.2f, -2.0f, 0.2f, -0.2f, 0.7f, 0, 0, 0, 0, 0, 0, 0, -0.5f),
			QuirkKeyframes.f(9, -1.6f, -0.3f, 0, -1.6f, 0.3f, 0, 0.9f, 0, 0, 0, 0, 0, 0, 0, -0.6f), QuirkKeyframes.rest(14) };
	/** v0.17.2: Phantom Grasp -- the right claw driven forward and in, the fingers closing inside them, then wrenched back. */
	private static final float[][] GRASP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.7f, 0.15f, 0, -0.2f, 0, -0.2f, 0.3f, -0.35f, 0, 0, 0, 0, 0, 0, 0.1f),
			QuirkKeyframes.f(7, -1.6f, 0.1f, 0.3f, -0.2f, 0, -0.2f, 0.3f, -0.35f, 0, 0, 0, 0, 0, 0, 0.1f),
			QuirkKeyframes.f(10, -0.9f, -0.2f, 0, -0.3f, 0, -0.2f, -0.1f, 0.2f, 0, 0, 0, 0, 0, 0, -0.1f), QuirkKeyframes.rest(15) };
	private static final float[][] PHASE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.2f, 0.9f, 0, -1.2f, -0.9f, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0.4f),
			QuirkKeyframes.f(7, -0.3f, 0, 0.9f, -0.3f, 0, -0.9f, -0.1f, 0, 0, 0, 0, 0, 0, 0, -0.2f), QuirkKeyframes.rest(12) };
	private static final float[][] FLIGHT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.5f, 0, 1.0f, -0.5f, 0, -1.0f, 0.1f, 0, 0, 0, 0, 0, 0, 0, -0.3f),
			QuirkKeyframes.rest(12) };
	private static final float[][] FADE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -1.0f, 1.0f, -0.2f, -1.0f, -1.0f, 0.2f, 0.25f, 0, 0, 0, 0, 0, 0, 0, 0.5f),
			QuirkKeyframes.f(12, -1.0f, 1.0f, -0.2f, -1.0f, -1.0f, 0.2f, 0.25f, 0, 0, 0, 0, 0, 0, 0, 0.5f), QuirkKeyframes.rest(16) };
	private static final float[][] AURA = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.4f, 0, 1.7f, -0.4f, 0, -1.7f, -0.3f, 0, 0, 0, 0, 0, 0, 0, -0.6f),
			QuirkKeyframes.f(16, -0.5f, 0, 1.8f, -0.5f, 0, -1.8f, -0.3f, 0, 0, 0, 0, 0, 0, 0, -0.6f), QuirkKeyframes.rest(22) };
	/** v0.17.2: True Form's peel -- both claws dig into the chest and tear it open, arms flung wide on the reveal. */
	private static final float[][] PEEL = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -1.3f, 0.7f, -0.2f, -1.3f, -0.7f, 0.2f, 0.35f, 0, 0, 0, 0, 0, 0, 0, 0.4f),
			QuirkKeyframes.f(14, -1.1f, -0.4f, 0.5f, -1.1f, 0.4f, -0.5f, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(20, -0.6f, 0, 1.6f, -0.6f, 0, -1.6f, -0.35f, 0, 0, 0, 0, 0, 0, 0, -0.6f),
			QuirkKeyframes.f(28, -0.6f, 0, 1.5f, -0.6f, 0, -1.5f, -0.3f, 0, 0, 0, 0, 0, 0, 0, -0.5f), QuirkKeyframes.rest(34) };
	/** v0.17.2: the wrap -- the claws pull the skin shut across the chest. */
	private static final float[][] WRAP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -1.2f, -0.6f, 0.4f, -1.2f, 0.6f, -0.4f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(10, -1.2f, 0.6f, -0.3f, -1.2f, -0.6f, 0.3f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.2f), QuirkKeyframes.rest(16) };
	/** v0.17.2: Lights Out -- both arms raised, then swept down as the dark falls. */
	private static final float[][] LIGHTS_OUT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -2.9f, 0, 0.4f, -2.9f, 0, -0.4f, -0.2f, 0, 0, 0, 0, 0, 0, 0, -0.5f),
			QuirkKeyframes.f(10, 0.3f, 0, 0.9f, 0.3f, 0, -0.9f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.3f), QuirkKeyframes.rest(18) };
	/** v0.17.2: Tendril Snatch -- the chest thrust out, the arms pulled back as the tentacle shoots. */
	private static final float[][] SNATCH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.5f, 0, 0.5f, 0.5f, 0, -0.5f, -0.35f, 0, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(8, -0.4f, 0, 0.6f, -0.4f, 0, -0.6f, 0.25f, 0, 0, 0, 0, 0, 0, 0, 0.1f), QuirkKeyframes.rest(14) };
	// v0.17.2 revamp: the combo's backhand (the left claw), the Reaping Rake (both claws, a low wide sweep), the Ecto Rush (arms swept
	// back, leaning in), the burst out of a host (flung wide), and the chest tentacles (arms thrown open, chest pushed out)
	private static final float[][] CLAW_BACK = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.3f, 0, 0, -2.7f, -0.6f, -0.5f, -0.1f, -0.4f, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(5, -0.4f, 0, 0, -0.9f, 0.8f, 0.3f, 0.35f, 0.4f, 0, 0, 0, 0, 0, 0, 0.2f), QuirkKeyframes.rest(10) };
	private static final float[][] RAKE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.9f, 0.4f, 0.9f, -2.9f, -0.4f, -0.9f, -0.35f, 0, 0, 0, 0, 0, 0, 0, -0.4f),
			QuirkKeyframes.f(6, -0.6f, -0.9f, 0.2f, -0.6f, 0.9f, -0.2f, 0.6f, 0, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(11, -0.6f, -0.9f, 0.2f, -0.6f, 0.9f, -0.2f, 0.5f, 0, 0, 0, 0, 0, 0, 0, 0.2f), QuirkKeyframes.rest(16) };
	private static final float[][] RUSH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.9f, 0, 0.3f, 0.9f, 0, -0.3f, 0.7f, 0, 0, 0, 0, 0, 0, 0, -0.5f),
			QuirkKeyframes.f(7, 0.9f, 0, 0.3f, 0.9f, 0, -0.3f, 0.7f, 0, 0, 0, 0, 0, 0, 0, -0.5f), QuirkKeyframes.rest(12) };
	private static final float[][] BURST = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.6f, 0, 1.2f, -2.6f, 0, -1.2f, -0.3f, 0, 0, 0, 0, 0, 0, 0, -0.6f),
			QuirkKeyframes.f(9, -2.4f, 0, 1.0f, -2.4f, 0, -1.0f, -0.2f, 0, 0, 0, 0, 0, 0, 0, -0.4f), QuirkKeyframes.rest(14) };
	private static final float[][] TENTACLES = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.2f, 0, 1.6f, -0.2f, 0, -1.6f, -0.35f, 0, 0, 0, 0, 0, 0, 0, -0.3f),
			QuirkKeyframes.f(30, -0.3f, 0, 1.5f, -0.3f, 0, -1.5f, -0.3f, 0, 0, 0, 0, 0, 0, 0, -0.25f), QuirkKeyframes.rest(40) };
	private static final float[][] TOUCH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.5f, 0.2f, 0, 0, 0, 0, 0.15f, -0.3f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(8) };
	private static final float[][] BOLT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.6f, 0.05f, 0, 0.2f, 0, 0, 0.05f, -0.2f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(10) };

	private static final FirstPersonClip FP_CLAW = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -60, -30, -30, -0.1f, 0.4f, 0.05f),
			FirstPersonClip.f(5, 10, 45, 30, 0.35f, -0.2f, -0.25f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SCREAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, 35, -35, 0, -0.25f, -0.25f, 0.1f),
			FirstPersonClip.f(16, 35, -35, 0, -0.25f, -0.25f, 0.1f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_REACH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -30, 25, 0, 0.18f, 0.15f, -0.35f),
			FirstPersonClip.f(10, -28, 25, 0, 0.18f, 0.15f, -0.35f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_GRASP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -25, 22, 0, 0.15f, 0.12f, -0.45f),
			FirstPersonClip.f(7, -25, 22, 25, 0.15f, 0.12f, -0.5f), FirstPersonClip.f(10, -10, 10, 0, 0.1f, 0.05f, -0.1f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_CROSS = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, 45, 0, 0.3f, 0.15f, 0.05f),
			FirstPersonClip.f(12, -20, 45, 0, 0.3f, 0.15f, 0.05f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_WIDE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -20, -40, 30, -0.25f, 0.2f, 0),
			FirstPersonClip.f(16, -20, -40, 30, -0.25f, 0.2f, 0), FirstPersonClip.f(22, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_DOWN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -80, 5, 0, 0.05f, 0.5f, 0.2f),
			FirstPersonClip.f(9, 30, 10, 0, 0.05f, -0.3f, -0.1f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	/** v0.17.2: the peel -- claws to the chest (just under the view), then thrown wide. */
	private static final FirstPersonClip FP_PEEL = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(5, 30, -45, 0, -0.3f, -0.35f, 0.15f),
			FirstPersonClip.f(16, 30, -40, 10, -0.25f, -0.35f, 0.15f), FirstPersonClip.f(21, -20, -40, 30, -0.25f, 0.2f, 0), FirstPersonClip.f(34, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_TOUCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -20, 25, 0, 0.15f, 0.08f, -0.2f),
			FirstPersonClip.f(8, 0, 0, 0, 0, 0, 0));

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	/** v0.17.1 (user: "dont make him transparent like he is right now"): solid -- only Phase makes him see-through (the cue it is on). */
	@Override
	public int bodyAlpha(AbstractClientPlayer p) {
		// v0.17.2: the Ecto Rush is see-through too
		return (Omnitrix.state(p).kitFlags & (GhostfreakKit.F_PHASE | GhostfreakKit.F_RUSH)) != 0 ? 130 : 255;
	}

	/** A kit timer of his, client side (0 if unset). */
	private static long timer(AbstractClientPlayer p, String name) {
		return Omnitrix.state(p).readyAt(GhostfreakKit.ID + "." + name);
	}

	/** How far open his chest is, 0..1: the peel swings it open, the last second of True Form swings it shut. */
	public static float trueOpen(AbstractClientPlayer p, float partial) {
		if ((Omnitrix.state(p).kitFlags & GhostfreakKit.F_TRUE) == 0) {
			return 0f;
		}
		float now = p.level().getGameTime() + partial;
		long start = timer(p, "true_start");
		long end = timer(p, "true_end");
		float open = start <= 0 ? 1f : Mth.clamp((now - start) / GhostfreakKit.PEEL_TICKS, 0f, 1f);
		if (end > 0) {
			open = Math.min(open, Mth.clamp((end - now) / 20f, 0f, 1f));
		}
		return open * open * (3f - 2f * open);
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		int flags = Omnitrix.state(p).kitFlags;
		boolean phased = (flags & (GhostfreakKit.F_PHASE | GhostfreakKit.F_RUSH)) != 0;
		int alpha = phased ? 130 : 255;
		// the wispy tail in place of legs -- v0.17.2 (user: "Make the ghostfreak tail better please ... make it react to the direciton
		// that the player is moving in and never go through blocks or the player unless the player is phasing"): a simulated rope
		// that trails his motion, ripples, and keeps out of blocks and his body (GhostfreakTail), in the second skin's colours
		GhostfreakTail.render(pose, buffers, light, p, model, partial, alpha);
		// v0.17.2 True Form: the skin flaps, the striped body under them, the tentacles, the bone jaw
		float open = trueOpen(p, partial);
		if (open > 0.001f) {
			renderTrueForm(pose, buffers.getBuffer(RenderType.entityTranslucent(WHITE)), light, model, open, age, alpha);
		}
		// v0.17.2 Creature of the Dark: a purple rim round the eye
		if ((flags & GhostfreakKit.F_NIGHT) != 0) {
			VertexConsumer rim = buffers.getBuffer(RenderType.eyes(WHITE));
			float pulse = 0.75f + 0.25f * Mth.sin(age * 0.2f);
			int c = ((int) (pulse * 255) << 24) | (GhostfreakKit.PURPLE & 0xFFFFFF);
			AlienParts.on(pose, model.head, () -> AlienParts.box(pose, rim, -2.6f, -6.8f, -4.2f, 2.6f, -2.0f, -4.1f, 0f, 0f, 1f, 1f, c, LightTexture.FULL_BRIGHT));
		}
		// the one great purple eye (last: fetching the eyes buffer ends the translucent one)
		VertexConsumer eye = buffers.getBuffer(RenderType.eyes(EXTRAS));
		float blink = (age % 90) < 3 ? 0.3f : 1f;
		AlienParts.on(pose, model.head, () -> AlienParts.box(pose, eye, -2.0f, -6.2f + (1 - blink) * 1.8f, -4.3f, 2.0f, -2.6f - (1 - blink) * 1.8f, -4.05f, EYE[0],
				EYE[1], EYE[2], EYE[3], 0xFFFFFFFF, LightTexture.FULL_BRIGHT));
	}

	/** The bare Ectonurite showing through the open skin ({@code open} 0..1). */
	private static void renderTrueForm(PoseStack pose, VertexConsumer vc, int light, PlayerModel<AbstractClientPlayer> model, float open, float age, int alpha) {
		int a = (alpha << 24);
		AlienParts.on(pose, model.body, () -> {
			// the striped true body behind the skin: black, with white rib stripes
			float half = 3.6f * open;
			AlienParts.box(pose, vc, -half, 0.8f, -2.12f, half, 9.4f, -2.02f, 0f, 0f, 1f, 1f, a | (TRUE_BLACK & 0xFFFFFF), light);
			for (int i = 0; i < 4; i++) {
				float y = 1.8f + i * 2.0f;
				float w = half * (0.85f - i * 0.1f);
				AlienParts.box(pose, vc, -w, y, -2.2f, w, y + 0.55f, -2.12f, 0f, 0f, 1f, 1f, a | (TRUE_WHITE & 0xFFFFFF), light);
			}
			// the two skin flaps, hinged at the chest's sides, swinging out
			for (int side = -1; side <= 1; side += 2) {
				pose.pushPose();
				pose.translate(side * 4.0f / 16f, 0, -2.0f / 16f);
				pose.mulPose(Axis.YP.rotationDegrees(side * -115f * open));
				float x0 = side < 0 ? 0f : -4.0f;
				float x1 = side < 0 ? 4.0f : 0f;
				AlienParts.box(pose, vc, x0, 0.3f, -0.45f, x1, 10.0f, -0.05f, 0f, 0f, 1f, 1f, a | (SKIN_TONE & 0xFFFFFF), light);
				// a black stripe across each flap (his markings)
				AlienParts.box(pose, vc, x0 + 0.4f, 4.6f, -0.5f, x1 - 0.4f, 5.4f, -0.4f, 0f, 0f, 1f, 1f, a | (TRUE_BLACK & 0xFFFFFF), light);
				pose.popPose();
			}
			// four segmented tentacles, black and white, swaying out of the chest
			float[][] roots = { { -1.6f, 3.0f }, { 1.6f, 3.0f }, { -1.4f, 6.4f }, { 1.4f, 6.4f } };
			int segs = 8;
			for (int t = 0; t < roots.length; t++) {
				pose.pushPose();
				pose.translate(roots[t][0] / 16f, roots[t][1] / 16f, -2.1f / 16f);
				float out = roots[t][0] < 0 ? -1f : 1f;
				pose.mulPose(Axis.YP.rotation(out * (0.35f + 0.15f * t)));
				pose.mulPose(Axis.XP.rotation(t < 2 ? -0.25f : 0.3f));
				for (int s = 0; s < segs; s++) {
					float len = 2.0f * open;
					float r = 0.75f - s * 0.06f;
					int col = (s % 2 == 0 ? TRUE_BLACK : TRUE_WHITE) & 0xFFFFFF;
					AlienParts.box(pose, vc, -r, -r, -len, r, r, 0f, 0f, 0f, 1f, 1f, a | col, light);
					pose.translate(0, 0, -len / 16f);
					float sway = Mth.sin(age * 0.18f + t * 1.7f + s * 0.6f) * 0.22f;
					pose.mulPose(Axis.YP.rotation(sway * out));
					pose.mulPose(Axis.XP.rotation(Mth.cos(age * 0.13f + t + s * 0.5f) * 0.18f + 0.06f));
				}
				pose.popPose();
			}
		});
		if (open > 0.5f) {
			// the skull's bone jaw under the eye, black teeth gaps
			int boneA = (int) (Math.min(1f, (open - 0.5f) * 2f) * alpha) << 24;
			AlienParts.on(pose, model.head, () -> {
				AlienParts.box(pose, vc, -3.0f, -2.3f, -4.22f, 3.0f, -0.4f, -4.06f, 0f, 0f, 1f, 1f, boneA | (TRUE_WHITE & 0xFFFFFF), light);
				for (int i = -2; i <= 2; i++) {
					AlienParts.box(pose, vc, i * 1.1f - 0.15f, -1.9f, -4.28f, i * 1.1f + 0.15f, -0.6f, -4.2f, 0f, 0f, 1f, 1f, boneA | (TRUE_BLACK & 0xFFFFFF), light);
				}
			});
		}
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		// v0.17.1: no claws (the skin's own arm)
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		// no legs: he floats on the tail (setModelProperties makes every part visible again before each render)
		m.rightLeg.visible = false;
		m.leftLeg.visible = false;
		if (m instanceof PlayerModel<?> pm) {
			pm.rightPants.visible = false;
			pm.leftPants.visible = false;
		}
		float[][] clip = switch (animId) {
			case GhostfreakKit.ANIM_CLAW -> CLAW_SWIPE;
			case GhostfreakKit.ANIM_SCREAM -> SCREAM;
			case GhostfreakKit.ANIM_POSSESS -> POSSESS;
			case GhostfreakKit.ANIM_GRASP -> GRASP;
			case GhostfreakKit.ANIM_PHASE -> PHASE;
			case GhostfreakKit.ANIM_FLIGHT -> FLIGHT;
			case GhostfreakKit.ANIM_FADE -> FADE;
			case GhostfreakKit.ANIM_TENTACLES -> TENTACLES;
			case GhostfreakKit.ANIM_CLAW_BACK -> CLAW_BACK;
			case GhostfreakKit.ANIM_RAKE -> RAKE;
			case GhostfreakKit.ANIM_RUSH -> RUSH;
			case GhostfreakKit.ANIM_BURST -> BURST;
			case GhostfreakKit.ANIM_AURA -> AURA;
			case GhostfreakKit.ANIM_PEEL -> PEEL;
			case GhostfreakKit.ANIM_WRAP -> WRAP;
			case GhostfreakKit.ANIM_LIGHTS_OUT -> LIGHTS_OUT;
			case GhostfreakKit.ANIM_SNATCH -> SNATCH;
			case GhostfreakKit.ANIM_TOUCH -> TOUCH;
			case GhostfreakKit.ANIM_BOLT -> BOLT;
			case GhostfreakKit.ANIM_SIGHT -> SIGHT;
			default -> null;
		};
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		if (!played) {
			boolean bare = (Omnitrix.state(p).kitFlags & GhostfreakKit.F_TRUE) != 0;
			// ghost hands: drifting forward, slowly -- in True Form held wide and low, hunched over the open chest
			float reach = bare ? -0.3f : -0.55f;
			m.rightArm.xRot = m.rightArm.xRot * 0.3f + reach + Mth.sin(age * 0.08f) * 0.08f;
			m.leftArm.xRot = m.leftArm.xRot * 0.3f + reach + Mth.sin(age * 0.08f + 1.3f) * 0.08f;
			m.rightArm.zRot += bare ? 0.6f : 0.1f;
			m.leftArm.zRot -= bare ? 0.6f : 0.1f;
			if (bare) {
				m.body.xRot += 0.2f;
				m.head.xRot += 0.15f;
			}
		}
		return true;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case GhostfreakKit.ANIM_CLAW, GhostfreakKit.ANIM_CLAW_BACK -> FP_CLAW;
			case GhostfreakKit.ANIM_RAKE, GhostfreakKit.ANIM_BURST -> FP_SCREAM;
			case GhostfreakKit.ANIM_RUSH -> FP_CROSS;
			case GhostfreakKit.ANIM_SCREAM -> FP_SCREAM;
			case GhostfreakKit.ANIM_POSSESS -> FP_REACH;
			case GhostfreakKit.ANIM_GRASP, GhostfreakKit.ANIM_BOLT -> FP_GRASP;
			case GhostfreakKit.ANIM_PHASE, GhostfreakKit.ANIM_FADE, GhostfreakKit.ANIM_WRAP -> FP_CROSS;
			case GhostfreakKit.ANIM_FLIGHT, GhostfreakKit.ANIM_AURA, GhostfreakKit.ANIM_TENTACLES, GhostfreakKit.ANIM_SNATCH -> FP_WIDE;
			case GhostfreakKit.ANIM_PEEL -> FP_PEEL;
			case GhostfreakKit.ANIM_LIGHTS_OUT -> FP_DOWN;
			case GhostfreakKit.ANIM_TOUCH -> FP_TOUCH;
			case GhostfreakKit.ANIM_SIGHT -> FP_SIGHT;
			default -> null;
		};
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		var r = p.getRandom();
		var level = p.level();
		// v0.17.2: the tail's rope (through walls with him only while he is phasing)
		GhostfreakTail.tick(p, (Omnitrix.state(p).kitFlags & (GhostfreakKit.F_PHASE | GhostfreakKit.F_RUSH)) != 0);
		if (p.isInvisible()) {
			return; // faded: not even a wisp gives him away
		}
		if (r.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.WHITE_ASH, p.getX() + (r.nextDouble() - 0.5) * 0.5, p.getY() + 0.2 + r.nextDouble() * 0.6,
					p.getZ() + (r.nextDouble() - 0.5) * 0.5, 0, -0.01, 0);
		}
		int flags = Omnitrix.state(p).kitFlags;
		if ((flags & GhostfreakKit.F_PHASE) != 0 && r.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.SOUL, p.getX() + (r.nextDouble() - 0.5) * 0.7, p.getY() + r.nextDouble() * p.getBbHeight(),
					p.getZ() + (r.nextDouble() - 0.5) * 0.7, 0, 0.02, 0);
		}
		if ((flags & GhostfreakKit.F_AURA) != 0 && r.nextInt(2) == 0) {
			double a = r.nextDouble() * Math.PI * 2;
			level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, p.getX() + Math.cos(a) * 2.5, p.getY() + 0.1, p.getZ() + Math.sin(a) * 2.5, 0, 0.03, 0);
		}
	}

	@Override
	public float[] badge() {
		// v0.17.1 (user: "position our alien badges more on top of those alien badges"): over the skin's own symbol (now painted
		// out), just left of the middle of his chest. v0.17.2: the second skin's symbol is in the middle of his chest (jacket
		// 23..24 x 39..40, painted out by gen_ghostfreak_v2.js)
		return new float[] { 0f, 4.0f, -2.06f, 3.0f };
	}

	// ---------------------------------------------------------------- v0.17.2: the possessed player's client

	/** The possession this client is under (possessorId -1 = none), as the last packet said. */
	private static int possessorId = -1;
	private static long until;
	private static float forward;
	private static float strafe;
	private static boolean jump;
	private static float yaw;
	private static float pitch;
	/** Client ticks counted here, and the one the last packet came in (the stream is the lease). */
	private static int clientTicks;
	private static int heardAt = -1000;
	/** Let go if no packet has come for this long. */
	private static final int LEASE_TICKS = 20;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(GhostfreakPossessPayload.TYPE, (payload, context) -> context.client().execute(() -> receive(payload)));
		ClientTickEvents.START_CLIENT_TICK.register(GhostfreakClient::startTick);
		ClientTickEvents.END_CLIENT_TICK.register(GhostfreakClient::endTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	static void receive(GhostfreakPossessPayload p) {
		possessorId = p.possessorId();
		until = p.until();
		forward = p.forward();
		strafe = p.strafe();
		jump = p.jump();
		yaw = p.yaw();
		pitch = p.pitch();
		heardAt = clientTicks;
	}

	static void reset() {
		possessorId = -1;
		until = 0L;
		heardAt = -1000;
	}

	/** This client's player is possessed by a Ghostfreak right now -- its keys, mouse and hands are his. */
	public static boolean localPossessed() {
		if (possessorId < 0) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || !mc.player.isAlive() || clientTicks - heardAt > LEASE_TICKS || mc.level.getGameTime() > until + 40L) {
			reset(); // released, or the stream stopped: control comes back
			return false;
		}
		return true;
	}

	/** From {@code GhostfreakPossessedInputMixin}: while possessed, the movement keys this tick are Ghostfreak's. */
	public static void possessedInput(Input in) {
		if (!localPossessed()) {
			return;
		}
		in.forwardImpulse = Mth.clamp(forward, -1f, 1f);
		in.leftImpulse = Mth.clamp(strafe, -1f, 1f);
		in.up = forward > 0.05f;
		in.down = forward < -0.05f;
		in.left = strafe > 0.05f;
		in.right = strafe < -0.05f;
		in.jumping = jump;
		in.shiftKeyDown = false;
	}

	/** Before the keys are read: none of this player's own presses count (attack, use, hotbar, inventory, every power key). */
	private static void startTick(Minecraft mc) {
		clientTicks++;
		if (!localPossessed()) {
			return;
		}
		KeyMapping.releaseAll();
		for (KeyMapping k : mc.options.keyMappings) {
			while (k.consumeClick()) {
				// swallowed
			}
		}
		mc.player.setSprinting(false);
	}

	/** After the tick: the possessed player faces where Ghostfreak looks (set after the tick, so the turn is smoothed between frames). */
	private static void endTick(Minecraft mc) {
		if (!localPossessed()) {
			return;
		}
		LocalPlayer pl = mc.player;
		pl.setYRot(yaw);
		pl.setXRot(Mth.clamp(pitch, -90f, 90f));
		pl.setYHeadRot(yaw);
	}
}
