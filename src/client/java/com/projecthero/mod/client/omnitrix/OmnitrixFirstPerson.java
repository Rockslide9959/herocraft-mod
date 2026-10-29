package com.projecthero.mod.client.omnitrix;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;

/**
 * v0.17.1: the Omnitrix in first person.
 * <ul>
 *   <li>{@link #renderRaised}: R brings the left wrist up into the centre-low view and the right hand works it ({@link OmnitrixHands}) -- the forearm comes in from the left edge,
 *       the watch's face turned to the eye, the core popping up and the hologram over it (the watch itself is drawn by the
 *       {@code renderHand} hook, {@link OmnitrixWatch}). While the slap plays the wrist dips under the blow.</li>
 *   <li>{@link #clip}: an alien kit's first-person arm clip for the move playing now.</li>
 * </ul>
 */
public final class OmnitrixFirstPerson {
	/** True while vanilla's first-person {@code renderHand} runs (its setupAnim must not get the third-person poses). */
	public static boolean inHandRender;
	/**
	 * v0.17.1 (Wildmutt rework 2, user: "the arms dont follow the player in first person"): the pose vanilla had when it began
	 * drawing the current hand ({@code renderArmWithItem}'s HEAD) -- the view bob, the hurt shake and the hands' sway / lag from
	 * {@code renderHandsWithItems}, before the arm's own placement. A custom-body alien lays its hands out from here.
	 */
	public static final org.joml.Matrix4f HAND_BASE = new org.joml.Matrix4f();
	public static final org.joml.Matrix3f HAND_BASE_NORMAL = new org.joml.Matrix3f();

	private OmnitrixFirstPerson() {
	}

	/** v0.17.1: true while {@code p} is an alien that cannot hold or use items (Wildmutt's paws): held items are not drawn. */
	public static boolean pawsOnly(net.minecraft.world.entity.player.Player p) {
		return com.projecthero.mod.omnitrix.alien.AlienKit.pawsOnlyNow(p);
	}

	/**
	 * The left wrist raised to the face, and the right hand working the watch ({@link OmnitrixHands}: in from the bottom right to
	 * press the side button, then gripping the dial's rim, twisting it on each scroll, slapping the core). Draws nothing when the
	 * watch is down. Both arms are the player's own (skin, sleeve layer, wide or slim) via the renderer's first-person hands.
	 */
	public static void renderRaised(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float partial) {
		OmnitrixHands.Rig rig = OmnitrixWatch.rig(p, partial);
		float raise = rig.raise;
		if (raise <= 0.001f) {
			return;
		}
		OmnitrixState s = Omnitrix.state(p);
		float slap = OmnitrixHands.slapClock(p, s, partial);
		// the wrist gives under the palm as it lands
		float dip = slap < Omnitrix.SLAP_TICKS + 2
				? Mth.sin(Mth.clamp((slap - OmnitrixHands.SLAP_HIT + 1f) / (Omnitrix.SLAP_TICKS + 1f), 0f, 1f) * Mth.PI) * 0.05f : 0f;
		PoseStack rel = new PoseStack();
		// where the shoulder sits in camera space (x right, y up, -z ahead), sliding up from below the screen
		rel.translate(-0.5f, -0.24f - (1f - raise) * 0.9f - dip, -0.95f);
		rel.mulPose(Axis.ZP.rotationDegrees(14f));   // the wrist a little higher than the elbow
		rel.mulPose(Axis.YP.rotationDegrees(18f));   // the face turned round toward the eye
		rel.mulPose(Axis.XP.rotationDegrees(-18f));  // and tipped up a little
		// arm space -> camera space: down the arm (+y) = right, the arm's outer side (+x, the dial) = toward the eye
		rel.mulPose(new Matrix4f(0, 0, 1, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1));
		rel.translate(-5f / 16f, -2f / 16f, 0f); // undo the arm's own shoulder pivot
		Matrix4f leftBase = new Matrix4f(rel.last().pose());
		PlayerRenderer renderer = (PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(p);
		pose.pushPose();
		pose.mulPose(leftBase);
		renderer.renderLeftHand(pose, buffers, light, p);
		pose.popPose();
		float[] h = rig.hand;
		if (h == null) {
			return;
		}
		// the watch's space (the left arm's, as the watch is drawn) relative to here
		PlayerModel<AbstractClientPlayer> model = renderer.getModel();
		PoseStack ws = new PoseStack();
		ws.last().pose().set(leftBase);
		model.leftArm.translateAndRotate(ws);
		Matrix4f watch = new Matrix4f(ws.last().pose());
		// the right hand's frame in the watch's space: its +x (the palm) = N, +y (down the arm to the fingers) = F
		Vector3f f = new Vector3f(h[3], h[4], h[5]).normalize();
		Vector3f n = new Vector3f(h[6], h[7], h[8]);
		n.sub(new Vector3f(f).mul(n.dot(f))).normalize();
		Vector3f z = new Vector3f(n).cross(f);
		boolean slim = p.getSkin().model() == PlayerSkin.Model.SLIM;
		float tipX = slim ? -0.5f : -1f; // the hand's centre line (the right arm's cube is x -3..1 wide, -2..1 slim)
		Matrix4f hand = new Matrix4f().translate(h[0] / 16f, h[1] / 16f, h[2] / 16f)
				.mul(new Matrix4f(n.x, n.y, n.z, 0f, f.x, f.y, f.z, 0f, z.x, z.y, z.z, 0f, 0f, 0f, 0f, 1f))
				.translate(-tipX / 16f, -HAND_TIP / 16f, HAND_FRONT / 16f);
		// undo the right arm's own placement in renderHand (its pivot and the idle bob setupAnim gives it; xRot is zeroed there)
		ModelPart ra = model.rightArm;
		Matrix4f armPart = new Matrix4f().translate(ra.x / 16f, ra.y / 16f, ra.z / 16f).rotateZYX(ra.zRot, ra.yRot, 0f);
		Matrix4f m = new Matrix4f(watch).mul(hand).mul(new Matrix4f(armPart).invert());
		pose.pushPose();
		pose.mulPose(m);
		renderer.renderRightHand(pose, buffers, light, p);
		pose.popPose();
	}

	/** The right hand's fingertip point on its arm, px from the shoulder pivot: down the arm, and toward the thumb side. */
	private static final float HAND_TIP = 10f, HAND_FRONT = 1f;

	/** How far the vanilla first-person arm / item on the RIGHT side is pushed down out of view while the hand works the watch. */
	public static float rightSideHide(AbstractClientPlayer p, float partial) {
		if (!Omnitrix.hasPower(p) || Omnitrix.transformed(p)) {
			return 0f;
		}
		return OmnitrixWatch.rig(p, partial).mainHide;
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
