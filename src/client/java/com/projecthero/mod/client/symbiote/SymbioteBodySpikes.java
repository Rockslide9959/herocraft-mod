package com.projecthero.mod.client.symbiote;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteAnim;
import com.projecthero.mod.symbiote.SymbioteVitals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.15: living black spikes bursting out of the host's back, shoulders and arms while the Symbiote's spike moves
 * are out -- they grow out when Symbiote Spike (G), Spike Fan (Shift+G) or Symbiote Spikes (C) fires, hold for a beat
 * and draw back in ({@link #window} ticks), and while Symbiote Spikes' Thorns mode is switched on they stay out.
 *
 * <p>The same thorn texture as the flying {@code SymbioteSpikeEntity} ({@link SymbioteSpikeRenderer}), built from
 * {@link GooMesh} tubes in each vanilla bone's own space (after {@link ModelPart#translateAndRotate}; 1 unit = 1 block,
 * +Y runs down the bone, -Z is the front), so they follow every pose -- the GeckoLib suit copies the same bones. Third
 * person (and every other viewer) through {@code SymbioteBladeRenderer.Layer}, first person through
 * {@code PlayerRendererSymbioteBladeMixin}. Driven by the synced {@link SymbioteVitals#animId} / {@link SymbioteVitals#animStart}
 * / {@link SymbioteVitals#thornsMode}: no new packets.
 */
public final class SymbioteBodySpikes {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/symbiote_spike.png");
	private static final float PX = 1.0f / 16.0f;
	/** Ticks a spike takes to grow fully out, and to draw back in. */
	private static final float GROW = 5.0f;
	private static final float RETRACT = 8.0f;
	/** Each successive spike starts this many ticks after the one before -- they ripple out across the body. */
	private static final float STAGGER = 0.5f;

	/** {x, y, z, dirX, dirY, dirZ, length} in model pixels, on the body bone (back = +Z). */
	private static final float[][] BACK = {
			{ 0.0f, 5.0f, 2.4f, 0.0f, -0.25f, 1.0f, 8.0f },
			{ -2.2f, 2.6f, 2.4f, -0.4f, -0.5f, 1.0f, 7.0f },
			{ 2.2f, 2.6f, 2.4f, 0.4f, -0.5f, 1.0f, 7.0f },
			{ -2.6f, 7.8f, 2.4f, -0.45f, 0.05f, 1.0f, 6.0f },
			{ 2.6f, 7.8f, 2.4f, 0.45f, 0.05f, 1.0f, 6.0f },
			{ 0.0f, 10.4f, 2.4f, 0.0f, 0.35f, 1.0f, 4.5f },
			{ -3.4f, 0.4f, 1.2f, -0.55f, -1.0f, 0.55f, 5.0f },
			{ 3.4f, 0.4f, 1.2f, 0.55f, -1.0f, 0.55f, 5.0f },
	};
	/** The right arm's spikes (bone space, outer side = -X); the left arm mirrors X. {x, y, z, dx, dy, dz, len}. */
	private static final float[][] ARM = {
			{ -1.4f, -2.4f, 0.4f, -0.45f, -1.0f, 0.3f, 6.0f },  // shoulder, up and out
			{ -3.4f, 0.4f, 0.6f, -1.0f, -0.45f, 0.35f, 5.0f },  // upper arm, out
			{ -3.4f, 4.6f, 0.8f, -1.0f, 0.1f, 0.55f, 4.5f },    // forearm, out and back
			{ -1.2f, 6.8f, 2.4f, -0.35f, 0.3f, 1.0f, 4.0f },    // forearm, back
	};

	/**
	 * First person only: the first-person arm shows the camera its inner side and its top, not the outer side the
	 * third-person spikes grow from -- so the sleeve the player sees gets its own ring of thorns round the forearm.
	 */
	private static final float[][] FP_ARM = {
			{ 1.3f, 7.0f, -1.0f, 1.0f, 0.6f, -0.4f, 5.0f },     // inner side, raked towards the fist
			{ 1.3f, 9.0f, 0.8f, 1.0f, 0.7f, 0.3f, 4.0f },
			{ -1.0f, 6.5f, -2.4f, -0.2f, 0.6f, -1.0f, 5.0f },   // front face
			{ 0.2f, 8.8f, -2.4f, 0.35f, 0.7f, -1.0f, 4.0f },
			{ -1.2f, 7.5f, 2.4f, -0.3f, 0.6f, 1.0f, 5.0f },     // back face
			{ -3.4f, 8.0f, 0.0f, -1.0f, 0.6f, 0.0f, 5.0f },     // outer side
	};

	private SymbioteBodySpikes() {
	}

	/** How long (ticks) a move's spikes stay out, or 0 if the move does not raise them. */
	public static int window(int animId) {
		return switch (animId) {
			case SymbioteAnim.SPIKE_SHOT -> 30;
			case SymbioteAnim.SPIKE_FAN, SymbioteAnim.SPIKES_FLEX -> 36;
			default -> 0;
		};
	}

	/** Ticks since the spikes came out (fractional), or -1 if they are not out. Thorns mode = held out indefinitely. */
	static float age(Player player, float partialTick) {
		if (player.isInvisible() || !Symbiote.hasSymbiote(player)) {
			return -1.0f;
		}
		SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		if (v == null) {
			return -1.0f;
		}
		int window = window(v.animId);
		float since = player.level().getGameTime() - v.animStart + partialTick;
		if (window > 0 && since >= 0.0f && since <= window) {
			return since;
		}
		return v.thornsMode ? 1000.0f : -1.0f;
	}

	/** Growth of the {@code i}-th spike, 0..1. */
	private static float growth(Player player, float age, int i) {
		if (age >= 999.0f) {
			return 1.0f; // Thorns mode: they stay out
		}
		SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
		int window = v == null ? 0 : window(v.animId);
		boolean held = v != null && v.thornsMode;
		if (held && v.animId != SymbioteAnim.SPIKES_FLEX) {
			return 1.0f; // already out for Thorns mode: a G shot does not make them pop back in and regrow
		}
		float a = age - i * STAGGER;
		if (a <= 0.0f) {
			return 0.0f;
		}
		float out = Math.min(1.0f, a / GROW);
		out = 1.0f - (1.0f - out) * (1.0f - out); // ease out
		if (!held) {
			float back = Math.min(1.0f, Math.max(0.0f, (window - age) / RETRACT));
			out = Math.min(out, back);
		}
		return out;
	}

	/** Third person, and how every other player sees it. */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
			PlayerModel<AbstractClientPlayer> model, float partialTick) {
		float age = age(player, partialTick);
		if (age < 0.0f) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
		boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
		pose.pushPose();
		model.body.translateAndRotate(pose);
		for (int i = 0; i < BACK.length; i++) {
			spike(vc, pose.last(), light, BACK[i], false, 0.0f, growth(player, age, i));
		}
		pose.popPose();
		for (int side = 0; side < 2; side++) {
			boolean left = side == 1;
			pose.pushPose();
			(left ? model.leftArm : model.rightArm).translateAndRotate(pose);
			for (int i = 0; i < ARM.length; i++) {
				spike(vc, pose.last(), light, ARM[i], left, slim ? 1.0f : 0.0f, growth(player, age, 2 + i));
			}
			pose.popPose();
		}
	}

	/** First person: the spikes on the arm the player sees. */
	public static void renderFirstPerson(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
			ModelPart arm, boolean rightArm) {
		float age = age(player, net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
		if (age < 0.0f) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
		boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
		pose.pushPose();
		arm.translateAndRotate(pose);
		for (int i = 0; i < FP_ARM.length; i++) {
			spike(vc, pose.last(), light, FP_ARM[i], !rightArm, slim ? 1.0f : 0.0f, growth(player, age, 1 + i));
		}
		pose.popPose();
	}

	/**
	 * One hooked thorn rooted at {@code s} (model pixels), {@code g} of the way grown. {@code mirror} flips X (the left
	 * arm), {@code inset} pulls an outer-side root in towards the bone (slim arms are a pixel narrower).
	 */
	private static void spike(VertexConsumer vc, PoseStack.Pose last, int light, float[] s, boolean mirror, float inset, float g) {
		if (g <= 0.01f) {
			return;
		}
		float sign = mirror ? -1.0f : 1.0f;
		float rx = s[0];
		if (inset > 0.0f && Math.abs(rx) > 3.0f) {
			rx += rx < 0 ? inset : -inset;
		}
		Vec3 root = new Vec3(sign * rx * PX, s[1] * PX, s[2] * PX);
		Vec3 dir = new Vec3(sign * s[3], s[4], s[5]).normalize();
		double len = s[6] * PX * g;
		// a slight hook: the tip curls back the way the spike leans (towards +Z / up the body)
		Vec3 hook = new Vec3(0.0, -0.35, 0.25).normalize().scale(len * 0.12);
		Vec3[] pts = {
				root.subtract(dir.scale(0.6 * PX)), // sunk a little into the suit so the base never floats
				root.add(dir.scale(len * 0.35)),
				root.add(dir.scale(len * 0.7)).add(hook.scale(0.4)),
				root.add(dir.scale(len)).add(hook),
		};
		float w = (float) Math.sqrt(g);
		float[] radii = { 0.05f * w, 0.034f * w, 0.017f * w, 0.0015f };
		GooMesh.tube(vc, last, light, pts, radii, 5, 0.0f);
	}
}
