package com.projecthero.mod.client.mutation.v0145;

import java.util.concurrent.ThreadLocalRandom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.hero.revamp.v0145.SuperSpeedV0145;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.8 Super Speed Shift+C Phase: the speedster vibrates so fast they pass through walls, and it has to look like
 * it. v0.14.8: the same vibration runs while a speedster holds Z to charge Time Slow. For every phasing player (the synced {@code p04.phase} flag, so every viewer sees it):
 * <ul>
 *   <li>the body is drawn at a new random offset every <b>frame</b> ({@link #jitter}, applied through the player
 *       renderer's render offset), up to {@link #JITTER} blocks each way;</li>
 *   <li>{@link #GHOSTS} translucent pale-yellow copies flicker around it, each frame somewhere else
 *       ({@link #renderGhosts});</li>
 *   <li>in first person the phaser's own hands buzz a little ({@link #handJitter}).</li>
 * </ul>
 */
public final class SpeedPhaseFx {
	/** Whole-body offset per frame, blocks each way (horizontal; half that vertically). */
	public static final double JITTER = 0.12;
	private static final double HAND_JITTER = 0.018;
	private static final int GHOSTS = 3;
	private static final double GHOST_SPREAD = 0.28;
	private static final int GHOST_RGB = 0xFFF0A0;

	private SpeedPhaseFx() {
	}

	public static void init() {
		WorldRenderEvents.AFTER_ENTITIES.register(SpeedPhaseFx::renderGhosts);
	}

	/**
	 * Whether {@code p} is vibrating: phasing, or (v0.14.8) charging Time Slow -- the synced flags, or (no sync lag)
	 * the local player's own state.
	 */
	public static boolean phasing(Player p) {
		if (p == null) {
			return false;
		}
		if (MutationVisuals.hasFlag(p, SuperSpeedV0145.PHASE) || MutationVisuals.hasFlag(p, SuperSpeedV0145.TS_CHARGING)) {
			return true;
		}
		return p == Minecraft.getInstance().player && (SuperSpeedHandlers.phasing(p) || SuperSpeedHandlers.timeSlowCharging(p));
	}

	/** A fresh random offset for this frame (zero when {@code p} is not phasing). */
	public static Vec3 jitter(Player p) {
		if (!phasing(p)) {
			return Vec3.ZERO;
		}
		ThreadLocalRandom r = ThreadLocalRandom.current();
		return new Vec3(r.nextDouble(-JITTER, JITTER), r.nextDouble(-JITTER, JITTER) * 0.5, r.nextDouble(-JITTER, JITTER));
	}

	/** First person: the phaser's own hands buzz (a much smaller shake, so the view stays usable). */
	public static void handJitter(PoseStack pose, Player p) {
		if (!phasing(p)) {
			return;
		}
		ThreadLocalRandom r = ThreadLocalRandom.current();
		pose.translate(r.nextDouble(-HAND_JITTER, HAND_JITTER), r.nextDouble(-HAND_JITTER, HAND_JITTER),
				r.nextDouble(-HAND_JITTER, HAND_JITTER));
	}

	private static void renderGhosts(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		PoseStack pose = context.matrixStack();
		MultiBufferSource buffers = context.consumers();
		if (mc.level == null || pose == null || buffers == null) {
			return;
		}
		Vec3 cam = context.camera().getPosition();
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (AbstractClientPlayer p : mc.level.players()) {
			if (p.isSpectator() || p.isInvisible() || !phasing(p) || (p == mc.player && firstPerson)) {
				continue;
			}
			EntityRenderer<? super AbstractClientPlayer> r = mc.getEntityRenderDispatcher().getRenderer(p);
			if (!(r instanceof PlayerRenderer pr)) {
				continue;
			}
			PlayerModel<AbstractClientPlayer> model = pr.getModel();
			Vec3 at = p.getPosition(partial);
			float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
			float headYaw = Mth.rotLerp(partial, p.yHeadRotO, p.yHeadRot);
			float pitch = Mth.lerp(partial, p.xRotO, p.getXRot());
			float limbPos = p.walkAnimation.position(partial);
			float limbSpeed = p.walkAnimation.speed(partial);
			var buffer = buffers.getBuffer(RenderType.entityTranslucent(p.getSkin().texture()));
			for (int i = 0; i < GHOSTS; i++) {
				if (rnd.nextFloat() < 0.25f) {
					continue; // flicker: a copy is missing from some frames
				}
				double ox = rnd.nextDouble(-GHOST_SPREAD, GHOST_SPREAD);
				double oy = rnd.nextDouble(-GHOST_SPREAD, GHOST_SPREAD) * 0.4;
				double oz = rnd.nextDouble(-GHOST_SPREAD, GHOST_SPREAD);
				int alpha = 40 + rnd.nextInt(70);
				pose.pushPose();
				pose.translate(at.x + ox - cam.x, at.y + oy - cam.y, at.z + oz - cam.z);
				pose.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw + (float) rnd.nextDouble(-6.0, 6.0)));
				pose.scale(-1.0f, -1.0f, 1.0f);
				pose.scale(0.9375f, 0.9375f, 0.9375f);
				pose.translate(0.0f, -1.501f, 0.0f);
				model.attackTime = 0.0f;
				model.riding = false;
				model.young = false;
				model.crouching = false;
				model.setupAnim(p, limbPos, limbSpeed, p.tickCount + partial, headYaw - bodyYaw, pitch);
				model.renderToBuffer(pose, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, (alpha << 24) | GHOST_RGB);
				pose.popPose();
			}
		}
	}
}
