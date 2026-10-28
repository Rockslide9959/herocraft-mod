package com.projecthero.mod.client.omnitrix;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;

/**
 * v0.17.1: the Omnitrix in first person.
 * <ul>
 *   <li>{@link #renderRaised}: V brings the left wrist up into the centre-low view -- the forearm comes in from the left edge,
 *       the watch's face turned to the eye, the core popping up and the hologram over it (the watch itself is drawn by the
 *       {@code renderHand} hook, {@link OmnitrixWatch}). While the slap plays the wrist dips under the blow.</li>
 *   <li>{@link #clip}: an alien kit's first-person arm clip for the move playing now.</li>
 * </ul>
 */
public final class OmnitrixFirstPerson {
	/** True while vanilla's first-person {@code renderHand} runs (its setupAnim must not get the third-person poses). */
	public static boolean inHandRender;

	private OmnitrixFirstPerson() {
	}

	/** The left wrist raised to the face. Draws nothing when the watch is down. */
	public static void renderRaised(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float partial) {
		float raise = OmnitrixWatch.raise(p, partial);
		if (raise <= 0.001f) {
			return;
		}
		OmnitrixState s = Omnitrix.state(p);
		float slap = s.animId == Omnitrix.ANIM_SLAP ? p.level().getGameTime() + partial - s.animStart : 99f;
		float dip = slap < Omnitrix.SLAP_TICKS + 2 ? Mth.sin(Mth.clamp(slap / (Omnitrix.SLAP_TICKS + 2), 0f, 1f) * Mth.PI) * 0.05f : 0f;
		pose.pushPose();
		// where the shoulder sits in camera space (x right, y up, -z ahead), sliding up from below the screen
		pose.translate(-0.5f, -0.24f - (1f - raise) * 0.9f - dip, -0.95f);
		pose.mulPose(Axis.ZP.rotationDegrees(14f));   // the wrist a little higher than the elbow
		pose.mulPose(Axis.YP.rotationDegrees(18f));   // the face turned round toward the eye
		pose.mulPose(Axis.XP.rotationDegrees(-18f));  // and tipped up a little
		// arm space -> camera space: down the arm (+y) = right, the arm's outer side (+x, the dial) = toward the eye
		Matrix4f m = new Matrix4f(0, 0, 1, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1);
		pose.mulPose(m);
		pose.translate(-5f / 16f, -2f / 16f, 0f); // undo the arm's own shoulder pivot
		PlayerRenderer renderer = (PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(p);
		renderer.renderLeftHand(pose, buffers, light, p);
		pose.popPose();
	}

	/** The kit clip playing on {@code p} now, if any. */
	public static FirstPersonClip clip(AbstractClientPlayer p) {
		if (!Omnitrix.transformed(p)) {
			return null;
		}
		AlienClientKit kit = AlienClients.shownKit(p);
		OmnitrixState s = Omnitrix.state(p);
		return kit == null || s.animId <= 0 || s.animId >= 1000 ? null : kit.firstPerson(s.animId);
	}

	public static float clipTicks(AbstractClientPlayer p, float partial) {
		return p.level().getGameTime() + partial - Omnitrix.state(p).animStart;
	}
}
