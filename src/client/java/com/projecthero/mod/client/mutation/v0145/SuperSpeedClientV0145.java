package com.projecthero.mod.client.mutation.v0145;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.gui.AbilityHudExtras;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow;
import com.projecthero.mod.hero.revamp.v0145.SuperSpeedV0145;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.5 Super Speed rework: client registration.
 *
 * <ul>
 *   <li>HUD: black-and-gray theme, and the current speed mode ("Regular" / "Speed" / "Overdrive") right of the
 *       power name.</li>
 *   <li>After-image trail: every tick a running speedster with the {@code p04.trail} flag (Speed Mode or
 *       Overdrive) leaves a translucent copy of their body behind -- yellow, red in Overdrive -- that fades out
 *       over one second. Every client records its own copies for every such player it can see.</li>
 *   <li>Time Slow: the client mirror of the server's 1-in-20 entity ticks ({@link #skipClientTick}), and a faint
 *       cold tint for the caster and anyone caught in the field.</li>
 * </ul>
 */
public final class SuperSpeedClientV0145 {
	private static final int TRAIL_EVERY = 1; // v0.14.5: every tick, for a continuous trail (was 5)
	private static final int TRAIL_LIFE = 20;
	private static final int YELLOW = 0xFFD83A;
	private static final int RED = 0xFF3030;

	private record Snapshot(long time, double x, double y, double z, float bodyYaw, float headYaw, float pitch,
			float limbPos, float limbSpeed, boolean crouching, int rgb) {
	}

	private static final Map<UUID, Deque<Snapshot>> TRAILS = new HashMap<>();
	/** Time Slow casters in the client level, refreshed each client tick (read by SuperSpeedClientLevelMixin). */
	private static List<Player> casters = List.of();

	private SuperSpeedClientV0145() {
	}

	public static void init() {
		AbilityHudExtras.mono(SuperSpeedHandlers.KEY);
		AbilityHudExtras.registerDecor(SuperSpeedHandlers.KEY, (g, client, state, x, y) -> {
			long now = client.level != null ? client.level.getGameTime() : 0L;
			Float until = state.resources.get(SuperSpeedHandlers.KEY + "/" + SuperSpeedHandlers.OVERDRIVE_UNTIL);
			boolean overdrive = until != null && until > now;
			boolean speed = state.activeToggles.contains(SuperSpeedHandlers.KEY + "/speed_mode");
			String text = overdrive ? "Overdrive" : speed ? "Speed" : "Regular";
			int color = overdrive ? 0xFFFF4040 : speed ? 0xFFFFD83A : 0xFF9A9A9A;
			g.drawString(client.font, "- " + text, x, y, color);
		});
		ClientTickEvents.END_CLIENT_TICK.register(SuperSpeedClientV0145::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(SuperSpeedClientV0145::renderTrails);
		HudRenderCallback.EVENT.register((g, delta) -> renderTint(g));
	}

	// ---- Time Slow ------------------------------------------------------------------------------

	/** Whether the client should skip this tick of {@code e} (same rule as the server's SuperSpeedTimeSlow). */
	public static boolean skipClientTick(Entity e) {
		List<Player> list = casters;
		if (list.isEmpty() || e instanceof Player) {
			return false;
		}
		if ((e.level().getGameTime() + e.getId()) % SuperSpeedTimeSlow.TICK_DIVISOR == 0) {
			return false;
		}
		if (e.hasPassenger(x -> x instanceof Player)) {
			return false;
		}
		double r2 = SuperSpeedTimeSlow.RADIUS * SuperSpeedTimeSlow.RADIUS;
		for (Player p : list) {
			if (!p.isRemoved() && p.level() == e.level() && p.distanceToSqr(e) <= r2) {
				return true;
			}
		}
		return false;
	}

	private static void renderTint(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui) {
			return;
		}
		boolean caster = MutationVisuals.hasFlag(mc.player, SuperSpeedV0145.TIME_SLOW);
		boolean slowed = SuperSpeedTimeSlow.slowedByAttribute(mc.player);
		if (!caster && !slowed) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		g.fill(0, 0, w, h, slowed ? 0x2A405070 : 0x1A506080);
		int edge = Math.max(12, h / 6);
		g.fillGradient(0, 0, w, edge, 0x50101828, 0x00101828);
		g.fillGradient(0, h - edge, w, h, 0x00101828, 0x50101828);
	}

	// ---- After-image trail ----------------------------------------------------------------------

	private static void tick(Minecraft mc) {
		if (mc.level == null) {
			TRAILS.clear();
			casters = List.of();
			return;
		}
		List<Player> found = new ArrayList<>();
		long now = mc.level.getGameTime();
		java.util.Set<UUID> present = new java.util.HashSet<>();
		for (AbstractClientPlayer p : mc.level.players()) {
			if (MutationVisuals.hasFlag(p, SuperSpeedV0145.TIME_SLOW)) {
				found.add(p);
			}
			present.add(p.getUUID());
			if (!MutationVisuals.hasFlag(p, SuperSpeedV0145.TRAIL) || p.isSpectator() || p.tickCount % TRAIL_EVERY != 0) {
				continue;
			}
			double dx = p.getX() - p.xo;
			double dz = p.getZ() - p.zo;
			if (dx * dx + dz * dz < 0.05 * 0.05) {
				continue; // only while actually moving
			}
			boolean red = MutationVisuals.state(p).value(SuperSpeedV0145.TRAIL_RED, 0f) > 0.5f;
			// an after-image is left where the player WAS (last tick), never where they are about to be -- the rendered
			// player is interpolated between xo and x, so a copy at x would pop up just ahead of them
			TRAILS.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>()).addLast(new Snapshot(now, p.xo, p.yo, p.zo,
					p.yBodyRotO, p.yHeadRotO, p.xRotO, p.walkAnimation.position(0f), p.walkAnimation.speed(0f),
					p.isCrouching(), red ? RED : YELLOW));
		}
		casters = found.isEmpty() ? List.of() : found;
		for (Iterator<Map.Entry<UUID, Deque<Snapshot>>> it = TRAILS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Deque<Snapshot>> e = it.next();
			Deque<Snapshot> q = e.getValue();
			while (!q.isEmpty() && now - q.peekFirst().time() > TRAIL_LIFE) {
				q.pollFirst();
			}
			if (q.isEmpty() || !present.contains(e.getKey())) {
				it.remove();
			}
		}
	}

	private static void renderTrails(WorldRenderContext context) {
		if (TRAILS.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		PoseStack pose = context.matrixStack();
		MultiBufferSource buffers = context.consumers();
		if (mc.level == null || pose == null || buffers == null) {
			return;
		}
		Vec3 cam = context.camera().getPosition();
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();
		for (Map.Entry<UUID, Deque<Snapshot>> e : TRAILS.entrySet()) {
			Player player = mc.level.getPlayerByUUID(e.getKey());
			if (!(player instanceof AbstractClientPlayer acp)) {
				continue;
			}
			EntityRenderer<? super AbstractClientPlayer> r = mc.getEntityRenderDispatcher().getRenderer(acp);
			if (!(r instanceof PlayerRenderer pr)) {
				continue;
			}
			PlayerModel<AbstractClientPlayer> model = pr.getModel();
			ResourceLocation skin = acp.getSkin().texture();
			boolean own = player == mc.player;
			Vec3 at = acp.getPosition(partial);
			Vec3 heading = at.subtract(acp.xo, acp.yo, acp.zo);
			for (Snapshot s : e.getValue()) {
				// only copies the player has left BEHIND: at least 0.6 blocks back, never ahead along their movement
				Vec3 off = new Vec3(s.x(), s.y(), s.z()).subtract(at);
				if (off.lengthSqr() < 0.36 || (heading.lengthSqr() > 1.0e-4 && off.dot(heading) > 0.0)) {
					continue;
				}
				float age = (now - s.time()) + partial;
				float fade = 1.0f - age / TRAIL_LIFE;
				if (fade <= 0.0f) {
					continue;
				}
				// never draw a fresh copy over your own first-person camera
				if (own && firstPerson && cam.distanceToSqr(s.x(), s.y() + 1.0, s.z()) < 2.25) {
					continue;
				}
				int alpha = Math.round(Math.min(1.0f, fade) * 0.4f * 255.0f);
				int color = (alpha << 24) | s.rgb();
				pose.pushPose();
				pose.translate(s.x() - cam.x, s.y() - cam.y, s.z() - cam.z);
				pose.mulPose(Axis.YP.rotationDegrees(180.0f - s.bodyYaw()));
				pose.scale(-1.0f, -1.0f, 1.0f);
				pose.scale(0.9375f, 0.9375f, 0.9375f);
				pose.translate(0.0f, -1.501f, 0.0f);
				model.attackTime = 0.0f;
				model.riding = false;
				model.young = false;
				model.crouching = s.crouching();
				model.setupAnim(acp, s.limbPos(), s.limbSpeed(), acp.tickCount + partial,
						s.headYaw() - s.bodyYaw(), s.pitch());
				model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(skin)),
						LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
				pose.popPose();
			}
		}
	}
}
