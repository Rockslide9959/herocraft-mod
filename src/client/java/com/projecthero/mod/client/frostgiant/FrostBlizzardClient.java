package com.projecthero.mod.client.frostgiant;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.frostgiant.FrostBlizzardPayload;
import com.projecthero.mod.frostgiant.entity.RuneBrazierEntity;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the Frost Giant Assault's sky on the client, from the server's {@link FrostBlizzardPayload}:
 * <ul>
 *   <li><b>the rift</b> -- a swirling blue-white vortex 40 blocks over the anchor with crackling lightning-white edges,
 *       wider when Ymir reaches through, collapsing to nothing when Laufey falls;</li>
 *   <li><b>the blizzard</b> -- snow driven round the local player, and fog pulled in to ~48 blocks (32 with the Casket
 *       open) by {@code FogRendererMixin} via {@link #fogEnd()} (the pale sky tint itself is the shared raid sky);</li>
 *   <li><b>the HUD</b> -- a small Warm / Cold readout under the crosshair while inside the arena, and the relight
 *       progress when holding use on a cold Rune Brazier.</li>
 * </ul>
 * One static state for the one assault a player can be near; cleared on disconnect.
 */
public final class FrostBlizzardClient {
	/** The server's default blizzard fog distances (FrostGiantConfig.arena fogBlocks / fogBlocksCasketOpen). */
	private static final float FOG = 48f, FOG_CASKET = 32f;

	private static FrostBlizzardPayload state;
	private static long riftCollapseStart = -1;
	private static float riftRadius;

	private FrostBlizzardClient() {
	}

	public static void accept(FrostBlizzardPayload p) {
		if (!p.active()) {
			if (state != null && state.x() == p.x() && state.z() == p.z()) {
				state = null;
				riftCollapseStart = -1;
			}
			return;
		}
		if (p.rift() == 3 && (state == null || state.rift() != 3)) {
			riftCollapseStart = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
		}
		state = p;
	}

	public static void reset() {
		state = null;
		riftCollapseStart = -1;
		riftRadius = 0f;
	}

	public static boolean active() {
		return state != null;
	}

	/** 0..1: how far inside the arena the local player is (1 inside, fading out over 24 blocks past the edge). */
	public static float strength() {
		FrostBlizzardPayload s = state;
		Minecraft mc = Minecraft.getInstance();
		if (s == null || mc.player == null) {
			return 0f;
		}
		double dx = mc.player.getX() - (s.x() + 0.5);
		double dz = mc.player.getZ() - (s.z() + 0.5);
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d <= s.radius()) {
			return 1f;
		}
		return (float) Mth.clamp(1.0 - (d - s.radius()) / 24.0, 0.0, 1.0);
	}

	/** Fog end distance to force, or -1 for none. */
	public static float fogEnd() {
		FrostBlizzardPayload s = state;
		if (s == null || s.blizzard() <= 0) {
			return -1f;
		}
		float k = strength();
		if (k <= 0f) {
			return -1f;
		}
		return Mth.lerp(k, 256f, s.blizzard() >= 2 ? FOG_CASKET : FOG);
	}

	// ---------------------------------------------------------------- snow

	public static void tick(Minecraft mc) {
		FrostBlizzardPayload s = state;
		if (s == null || mc.level == null || mc.player == null || mc.isPaused()) {
			return;
		}
		float k = strength();
		var rnd = mc.level.random;
		if (k > 0f && s.blizzard() > 0) {
			int n = (int) ((s.blizzard() >= 2 ? 22 : 12) * k);
			Vec3 p = mc.player.position();
			double windX = 0.25 + 0.1 * Mth.sin(mc.level.getGameTime() * 0.01f);
			for (int i = 0; i < n; i++) {
				double x = p.x + (rnd.nextDouble() - 0.5) * 24;
				double y = p.y + rnd.nextDouble() * 12 - 2;
				double z = p.z + (rnd.nextDouble() - 0.5) * 24;
				mc.level.addParticle(i % 3 == 0 ? ParticleTypes.WHITE_ASH : ParticleTypes.SNOWFLAKE, x, y, z, windX, -0.12 - rnd.nextDouble() * 0.1,
						0.05);
			}
		}
		// the rift's crackling edge
		if (s.rift() > 0 && riftRadius > 1f && rnd.nextInt(3) == 0) {
			double a = rnd.nextDouble() * Math.PI * 2;
			double x = s.x() + 0.5 + Math.cos(a) * riftRadius;
			double z = s.z() + 0.5 + Math.sin(a) * riftRadius;
			double y = s.y() + s.riftHeight();
			mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0, -0.05, 0);
			mc.level.addParticle(ParticleTypes.SNOWFLAKE, x, y - 0.5, z, 0, -0.2, 0);
		}
	}

	// ---------------------------------------------------------------- the rift

	public static void render(WorldRenderContext ctx) {
		FrostBlizzardPayload s = state;
		Minecraft mc = Minecraft.getInstance();
		if (s == null || s.rift() <= 0 || mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			riftRadius = 0f;
			return;
		}
		float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		float time = mc.level.getGameTime() + pt;
		float target = switch (s.rift()) {
			case 2 -> 17f;
			case 3 -> riftCollapseStart < 0 ? 0f : Math.max(0f, 17f * (1f - (time - riftCollapseStart) / 80f));
			default -> 10f;
		};
		riftRadius += (target - riftRadius) * (s.rift() == 3 ? 0.25f : 0.03f);
		if (riftRadius < 0.3f) {
			return;
		}
		Vec3 cam = ctx.camera().getPosition();
		PoseStack pose = ctx.matrixStack();
		pose.pushPose();
		pose.translate(s.x() + 0.5 - cam.x, s.y() + s.riftHeight() - cam.y, s.z() + 0.5 - cam.z);
		Matrix4f m = pose.last().pose();
		MultiBufferSource buffers = ctx.consumers();
		// pass 1: the swirling translucent vortex
		VertexConsumer glow = buffers.getBuffer(RenderType.debugQuads());
		int rings = 9;
		int segs = 40;
		for (int i = 0; i < rings; i++) {
			float r0 = riftRadius * i / rings;
			float r1 = riftRadius * (i + 1) / rings;
			float spin = time * (0.045f - i * 0.003f) + i * 0.35f;
			for (int j = 0; j < segs; j++) {
				float a0 = (float) (j * Math.PI * 2 / segs) + spin;
				float a1 = (float) ((j + 1) * Math.PI * 2 / segs) + spin;
				float arm = 0.5f + 0.5f * Mth.sin(a0 * 4f - i * 0.9f + time * 0.05f);
				float alpha = (0.18f + 0.55f * arm) * (1f - i / (float) rings * 0.6f);
				int r = (int) Mth.lerp(arm, 120, 235);
				int g = (int) Mth.lerp(arm, 180, 250);
				quad(glow, m, r0, r1, a0, a1, 0f, r, g, 255, alpha);
			}
		}
		// pass 2: the white-hot core and the lightning edge
		VertexConsumer hot = buffers.getBuffer(RenderType.lightning());
		for (int j = 0; j < segs; j++) {
			float a0 = (float) (j * Math.PI * 2 / segs) - time * 0.06f;
			float a1 = (float) ((j + 1) * Math.PI * 2 / segs) - time * 0.06f;
			quad(hot, m, 0f, riftRadius * 0.22f, a0, a1, -0.05f, 230, 245, 255, 0.85f);
			float flick = Mth.sin(j * 7.3f + time * 0.9f);
			if (flick > 0.55f) {
				float out = riftRadius * (1.0f + 0.12f * flick);
				quad(hot, m, riftRadius * 0.96f, out, a0, a1, -0.05f, 200, 230, 255, 0.9f);
			}
		}
		pose.popPose();
	}

	/** A ring segment (both windings, so it shows from above and below). */
	private static void quad(VertexConsumer vc, Matrix4f m, float r0, float r1, float a0, float a1, float y, int r, int g, int b, float alpha) {
		float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
		int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255);
		vc.addVertex(m, c0 * r0, y, s0 * r0).setColor(r, g, b, a);
		vc.addVertex(m, c1 * r0, y, s1 * r0).setColor(r, g, b, a);
		vc.addVertex(m, c1 * r1, y, s1 * r1).setColor(r, g, b, a);
		vc.addVertex(m, c0 * r1, y, s0 * r1).setColor(r, g, b, a);
		vc.addVertex(m, c0 * r1, y, s0 * r1).setColor(r, g, b, a);
		vc.addVertex(m, c1 * r1, y, s1 * r1).setColor(r, g, b, a);
		vc.addVertex(m, c1 * r0, y, s1 * r0).setColor(r, g, b, a);
		vc.addVertex(m, c0 * r0, y, s0 * r0).setColor(r, g, b, a);
	}

	// ---------------------------------------------------------------- HUD

	public static void hud(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (state == null || p == null || mc.options.hideGui || strength() < 1f || p.isSpectator()) {
			return;
		}
		int cx = g.guiWidth() / 2;
		int y = g.guiHeight() / 2 + 14;
		boolean warm = false;
		RuneBrazierEntity relighting = null;
		for (RuneBrazierEntity b : p.level().getEntitiesOfClass(RuneBrazierEntity.class, new AABB(p.blockPosition()).inflate(12))) {
			if (b.warms(p)) {
				warm = true;
			}
			if (!b.isLit() && b.relightProgress() > 0f && b.distanceToSqr(p) < 36) {
				relighting = b;
			}
		}
		Component line;
		int color;
		if (state.blizzard() <= 0) {
			return;
		} else if (warm) {
			line = Component.translatable("hud.projecthero.frost_giants.warm");
			color = 0xFFFFB050;
		} else {
			int pct = Math.round(p.getPercentFrozen() * 100);
			line = Component.translatable("hud.projecthero.frost_giants.cold", pct);
			color = pct >= 100 ? 0xFFB0F0FF : 0xFF8FC4EE;
		}
		g.drawCenteredString(mc.font, line, cx, y, color);
		HitResult hit = mc.hitResult;
		if (relighting == null && hit instanceof EntityHitResult eh && eh.getEntity() instanceof RuneBrazierEntity b && !b.isLit()) {
			g.drawCenteredString(mc.font, Component.translatable("hud.projecthero.frost_giants.relight_hint"), cx, y + 11, 0xFFD0D0D0);
		}
		if (relighting != null) {
			int w = 60;
			int x0 = cx - w / 2;
			int yy = y + 11;
			g.fill(x0 - 1, yy - 1, x0 + w + 1, yy + 4, 0xC0000000);
			g.fill(x0, yy, x0 + (int) (w * relighting.relightProgress()), yy + 3, 0xFFFF9A30);
		}
	}
}
