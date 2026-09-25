package com.projecthero.mod.client.spider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.spider.data.SpiderManState;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the web line, for every swinging player the client can see -- and (v0.12.20) every fading strand from
 * {@link SpiderStrands}: Web Zip, the Combat Mode moves, and the remnant of a released swing, all in the same
 * line look so a zip reads as the same web the swing throws.
 *
 * <p>Purely a render: a handful of line segments straight into the world-render buffer. No entity of any kind
 * is created, so there is nothing to accumulate, nothing to leak and nothing left behind when the line ends --
 * the state flips and the line simply stops being drawn (or fades, for strands).
 *
 * <p>The line sags slightly toward its middle rather than being a taut straight segment, which reads far more
 * like webbing and costs one interpolation per segment. Both ends are pinned to real positions every frame: the
 * near end to the tracked fist ({@link SpiderStrands#handPosition}), the far end to the anchor / target.
 */
public final class SpiderWebLineRenderer {
	private static final int SEGMENTS = 10;
	private static final float R = 0.94f;
	private static final float G = 0.96f;
	private static final float B = 1.0f;
	private static final float BASE_ALPHA = 0.9f;

	/** playerId -> anchor + hand for players seen swinging last frame, so a release can be noticed. */
	private static final Map<Integer, SwingMemo> LAST_SWING = new HashMap<>();

	private record SwingMemo(Vec3 anchor, boolean rightHand, Vec3 hand) {
	}

	private SpiderWebLineRenderer() {
	}

	public static void initialize() {
		WorldRenderEvents.AFTER_ENTITIES.register(SpiderWebLineRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || context.consumers() == null) {
			return;
		}
		Camera camera = context.camera();
		MultiBufferSource consumers = context.consumers();
		PoseStack poseStack = context.matrixStack();
		if (poseStack == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);

		// Swings: draw live lines; notice releases and hand them to the strand list to fade out.
		Set<Integer> swingingNow = new HashSet<>();
		for (Player player : client.level.players()) {
			SpiderManState state = player.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
			if (state == null || !state.swinging) {
				continue;
			}
			Vec3 anchor = new Vec3(state.anchorX, state.anchorY, state.anchorZ);
			swingingNow.add(player.getId());
			if (!LAST_SWING.containsKey(player.getId())) {
				// a fresh swing replaces the previous swing's fading remnant
				SpiderStrands.removeSlot(player.getId(), SpiderStrands.SLOT_SWING_REMNANT);
			}
			Vec3 hand = SpiderStrands.handPosition(player, state.swingHandRight, partial, camera);
			LAST_SWING.put(player.getId(), new SwingMemo(anchor, state.swingHandRight, hand));
			drawLine(poseStack, consumers, camera, hand, anchor, BASE_ALPHA);
		}
		LAST_SWING.entrySet().removeIf(e -> {
			if (swingingNow.contains(e.getKey())) {
				return false;
			}
			SpiderStrands.swingReleased(e.getKey(), e.getValue().anchor(), e.getValue().rightHand(), e.getValue().hand());
			return true;
		});

		// Strands.
		SpiderStrands.prune(partial);
		double now = client.level.getGameTime() + partial;
		for (SpiderStrands.Strand s : SpiderStrands.strands()) {
			Player owner = client.level.getEntity(s.playerId) instanceof Player p ? p : null;
			if (owner == null) {
				continue;
			}
			float alpha = s.alpha(now) * BASE_ALPHA;
			if (alpha <= 0.0f) {
				continue;
			}
			// Attached to the hand only while the web is being held; the moment it starts to fade the near end
			// is frozen where it was, so a fading web never trails the player's hand around.
			Vec3 hand = s.frozenHand;
			if (hand == null) {
				hand = SpiderStrands.handPosition(owner, s.rightHand, partial, camera);
				if (now - s.startTick >= s.hold) {
					s.frozenHand = hand;
				}
			}
			drawLine(poseStack, consumers, camera, hand, SpiderStrands.endPoint(s, partial), alpha);
		}
	}

	private static void drawLine(PoseStack poseStack, MultiBufferSource consumers, Camera camera,
			Vec3 hand, Vec3 anchor, float alpha) {
		Vec3 cam = camera.getPosition();
		poseStack.pushPose();
		poseStack.translate(-cam.x, -cam.y, -cam.z);
		var pose = poseStack.last();
		// Segment pairs (not a line strip): several strands share one buffer and must not be joined together.
		VertexConsumer buffer = consumers.getBuffer(RenderType.lines());

		double slack = Math.min(0.9, hand.distanceTo(anchor) * 0.035);
		Vec3 prev = null;
		for (int i = 0; i <= SEGMENTS; i++) {
			double t = (double) i / SEGMENTS;
			Vec3 p = hand.lerp(anchor, t);
			// a parabola that is zero at both ends and deepest in the middle
			p = p.subtract(0, slack * (4.0 * t * (1.0 - t)), 0);
			if (prev != null) {
				Vec3 d = p.subtract(prev);
				double len = Math.max(1.0e-6, d.length());
				float nx = (float) (d.x / len);
				float ny = (float) (d.y / len);
				float nz = (float) (d.z / len);
				buffer.addVertex(pose.pose(), (float) prev.x, (float) prev.y, (float) prev.z)
						.setColor(R, G, B, alpha).setNormal(pose, nx, ny, nz);
				buffer.addVertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
						.setColor(R, G, B, alpha).setNormal(pose, nx, ny, nz);
			}
			prev = p;
		}
		poseStack.popPose();
	}
}
