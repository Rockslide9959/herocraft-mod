package com.projecthero.mod.client.ironman;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.gui.IronManGui;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.ironman.scan.IronManScan;
import com.projecthero.mod.ironman.scan.IronManThreat;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.ui.IronManUiLayout;
import com.projecthero.mod.ironman.ui.IronManUiLayout.Rect;
import com.projecthero.mod.network.IronManLockPayload;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.FormattedCharSequence;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.26: the Iron Man helmet's target scanner. Every Mark from the Mark II up (visor closed) reads out whatever living
 * thing is under the crosshair -- out to {@link #range} (100 blocks; the Mark II's older optics reach 25): its name, health
 * (numbers and a bar), armour, distance and threat class. The Mark III's targeting system shows its <b>lock</b> instead
 * (the target the suit auto-aims at), with a rotating red-and-gold lock reticle drawn on the target in the world.
 */
public final class IronManTargetScanner {
	private static int lockId = -1;

	private IronManTargetScanner() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(IronManLockPayload.TYPE, (payload, context) -> lockId = payload.entityId());
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
			lockId = -1;
			assessment = null; // v0.16.7 (user)
			requestedId = -1;
			reticleId = -1;
		});
		// v0.16.7 (user): the server's threat assessment of the scanned target
		ClientPlayNetworking.registerGlobalReceiver(IronManScan.Result.TYPE, (payload, context) -> {
			assessment = payload;
			Minecraft mc = Minecraft.getInstance();
			assessedAt = mc.level != null ? mc.level.getGameTime() : 0L;
		});
		WorldRenderEvents.AFTER_ENTITIES.register(IronManTargetScanner::renderReticle);
	}

	/** How far this suit's scanner reads: 25 for the Mark II, 100 for everything newer. */
	public static double range(IronManSuit suit) {
		return "mark_2".equals(suit.id()) ? 25.0 : 100.0;
	}

	public static LivingEntity lockTarget() {
		Minecraft mc = Minecraft.getInstance();
		if (lockId < 0 || mc.level == null) {
			return null;
		}
		Entity e = mc.level.getEntity(lockId);
		return e instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	/** The living thing under the crosshair within {@code range}, not hidden behind blocks. */
	public static LivingEntity pick(Player player, double range, float partial) {
		Vec3 eye = player.getEyePosition(partial);
		Vec3 look = player.getViewVector(partial);
		Vec3 end = eye.add(look.scale(range));
		var block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double reach = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);
		Vec3 stop = eye.add(look.scale(reach));
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (Entity e : player.level().getEntities(player, new AABB(eye, stop).inflate(1.5),
				e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator() && !e.isInvisible())) {
			AABB box = e.getBoundingBox().inflate(Math.max(0.3, e.distanceTo(player) * 0.004));
			var hit = box.clip(eye, stop);
			if (hit.isPresent()) {
				double d = hit.get().distanceToSqr(eye);
				if (d < bestD) {
					bestD = d;
					best = (LivingEntity) e;
				}
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ v0.16.7 (user): the threat assessment cache

	/** The latest server assessment ({@link IronManScan.Result}) and when it arrived. */
	private static IronManScan.Result assessment;
	private static long assessedAt;
	private static int requestedId = -1;
	private static long requestedAt = -100L;

	/** What the world reticle draws: the scanned / locked target, its colour, and when the HUD last saw it. */
	private static int reticleId = -1;
	private static int reticleColour = IronManGui.CYAN;
	private static boolean reticleLocked;
	private static long reticleSeenAt;

	/** Ask the server about {@code target} when it changes, then once a second while it stays under the crosshair. */
	private static IronManScan.Result assessmentFor(LivingEntity target, long now) {
		if (target.getId() != requestedId || now - requestedAt >= 20L || now < requestedAt) {
			requestedId = target.getId();
			requestedAt = now;
			if (ClientPlayNetworking.canSend(IronManScan.Request.TYPE)) {
				ClientPlayNetworking.send(new IronManScan.Request(target.getId()));
			}
		}
		return assessment != null && assessment.entityId() == target.getId() && now - assessedAt < 60L ? assessment : null;
	}

	private static String t(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	/**
	 * v0.16.7 (user): the target card, beside the crosshair (never over it; left of it when the key column is in the
	 * way). Name and distance, a health bar with numbers and armour, the THREAT level as a coloured label with the
	 * reasons behind it (what its hits do to you, how many of yours it takes, boss / armed / explodes / neutral), and for
	 * a player with a power: the power and its moves.
	 */
	public static void renderPanel(GuiGraphics g, Font font, Player player, IronManSuit suit, float partial) {
		LivingEntity locked = com.projecthero.mod.ironman.IronManTargeting.hasTargeting(suit) ? lockTarget() : null;
		LivingEntity t = locked != null ? locked : pick(player, range(suit), partial);
		long now = player.level().getGameTime();
		if (t == null) {
			return;
		}
		IronManScan.Result a = assessmentFor(t, now);
		IronManThreat.Level level = a == null ? null : IronManThreat.Level.of(a.level());
		int threatColour = level == null ? IronManGui.CYAN : level.colour;
		reticleId = t.getId();
		reticleColour = threatColour;
		reticleLocked = locked != null;
		reticleSeenAt = System.currentTimeMillis();

		int w = 150;
		int inner = w - 10;
		// ---- measure: the reason lines and the power lines
		List<FormattedCharSequence> reasons = new ArrayList<>();
		List<Boolean> reasonStarts = new ArrayList<>();
		List<FormattedCharSequence> power = new ArrayList<>();
		if (a != null) {
			List<String> rs = new ArrayList<>();
			float maxI = Math.max(1f, suit.maxIntegrity());
			String hullPct = a.hull() <= 0.01f ? "0%" : a.hull() / maxI * 100f < 1f ? "<1%"
					: Math.round(a.hull() / maxI * 100f) + "%";
			if (a.has(IronManScan.EXPLODES)) {
				rs.add(t("hud.projecthero.ironman.threat.explodes"));
			}
			if (a.toYou() <= 0.01f && a.hull() <= 0.01f) {
				rs.add(t("hud.projecthero.ironman.threat.harmless"));
			} else if (a.toYou() <= 0.01f) {
				rs.add(t("hud.projecthero.ironman.threat.soaked", hullPct));
			} else {
				// the hull share only when it is worth reading (1% or more of the suit)
				rs.add(a.hull() / maxI * 100f < 1f ? t("hud.projecthero.ironman.threat.hits_you_short", IronManThreat.hearts(a.toYou()))
						: t("hud.projecthero.ironman.threat.hits_you", IronManThreat.hearts(a.toYou()), hullPct));
			}
			rs.add(a.yourHits() <= 0 ? t("hud.projecthero.ironman.threat.cant_hurt")
					: a.yourHits() == 1 ? t("hud.projecthero.ironman.threat.one_hit")
					: t("hud.projecthero.ironman.threat.you_down", a.yourHits()));
			if (a.has(IronManScan.BOSS)) {
				rs.add(t("hud.projecthero.ironman.threat.boss", Math.round(t.getMaxHealth())));
			}
			if (!a.weapon().isEmpty()) {
				rs.add(t("hud.projecthero.ironman.threat.armed", Component.translatable(a.weapon())));
			}
			if (!a.has(IronManScan.HOSTILE) && !(t instanceof Player) && a.toYou() > 0.01f) {
				rs.add(t("hud.projecthero.ironman.threat.neutral"));
			}
			for (String r : rs) {
				List<FormattedCharSequence> parts = font.split(Component.literal(r), inner - 7);
				for (int k = 0; k < parts.size(); k++) {
					reasons.add(parts.get(k));
					reasonStarts.add(k == 0);
				}
			}
			if (!a.identity().isEmpty()) {
				power.addAll(font.split(Component.translatable("hud.projecthero.ironman.scan.power_line",
						Component.translatable(a.identity()).withStyle(s -> s.withColor(IronManGui.GOLD & 0xFFFFFF))), inner));
				if (!a.moves().isEmpty()) {
					StringBuilder sb = new StringBuilder();
					for (String m : a.moves()) {
						if (sb.length() > 0) {
							sb.append(", ");
						}
						sb.append(Component.translatable(m).getString());
					}
					List<FormattedCharSequence> mv = font.split(Component.translatable("hud.projecthero.ironman.scan.moves", sb.toString()), inner);
					power.addAll(mv.subList(0, Math.min(3, mv.size())));
				}
			}
		}
		int h = 4 + 10 + 10 + 7 + 13 + reasons.size() * 9 + (power.isEmpty() ? 0 : 3 + power.size() * 9) + 3;
		Rect card = IronManUiLayout.targetCard(g.guiWidth(), g.guiHeight(), w, h);
		int x = card.x();
		int y = card.y();

		// ---- frame: a faint wash, hairline, corner brackets in the threat colour, a red tick
		g.fill(x, y, x + w, y + h, 0xB0040C14);
		g.renderOutline(x, y, w, h, IronManGui.alpha(IronManGui.CYAN_DIM, 0.45f));
		IronManGui.brackets(g, x, y, w, h, 5, IronManGui.alpha(threatColour, 0.95f));
		g.fill(x + w - 12, y - 1, x + w - 8, y, IronManGui.alpha(IronManGui.RED, 0.85f));
		int px = x + 5;
		int py = y + 4;

		// ---- name + distance (LOCK while the Mark III targeting holds it)
		String dist = t("hud.projecthero.ironman.scan.dist", String.format(java.util.Locale.ROOT, "%.0f", player.distanceTo(t)));
		String tag = locked != null ? t("hud.projecthero.ironman.scan.locked") : dist;
		String name = IronManGui.fit(font, t.getName().getString(), inner - font.width(tag) - 6);
		g.drawString(font, name, px, py, 0xFFFFFFFF, true);
		g.drawString(font, tag, px + inner - font.width(tag), py, locked != null ? 0xFFFF5050 : IronManGui.TEXT_DIM, false);
		py += 10;

		// ---- health + armour, then the bar
		float hp = t.getHealth();
		float max = Math.max(1f, t.getMaxHealth());
		double armour = t.getAttributes().hasAttribute(Attributes.ARMOR) ? t.getAttributeValue(Attributes.ARMOR) : 0.0;
		String hpText = t("hud.projecthero.ironman.scan.hp_line", Math.round(hp), Math.round(max));
		String arText = t("hud.projecthero.ironman.scan.armour", (int) Math.round(armour));
		g.drawString(font, IronManGui.fit(font, hpText, inner - font.width(arText) - 6), px, py, IronManGui.TEXT, false);
		g.drawString(font, arText, px + inner - font.width(arText), py, IronManGui.TEXT_DIM, false);
		py += 10;
		float frac = Math.max(0f, Math.min(1f, hp / max));
		int barColour = frac > 0.6f ? 0xFF50D060 : frac > 0.3f ? 0xFFE0B030 : 0xFFE04040;
		g.fill(px, py, px + inner, py + 3, 0x50103040);
		g.fill(px, py, px + Math.round(inner * frac), py + 3, barColour);
		for (int k = 1; k < 10; k++) {
			int sx = px + inner * k / 10;
			g.fill(sx, py, sx + 1, py + 3, 0x90020810);
		}
		py += 7;

		// ---- THREAT: a coloured pill with the level
		String title = t("hud.projecthero.ironman.threat.title");
		String lv = level == null ? t("hud.projecthero.ironman.threat.analysing") : t(level.langKey());
		g.drawString(font, title, px, py + 1, IronManGui.TEXT_DIM, false);
		int pillX = px + font.width(title) + 5;
		int pillW = Math.min(inner - (pillX - px), font.width(lv) + 8);
		g.fill(pillX, py, pillX + pillW, py + 10, IronManGui.alpha(threatColour, 0.22f));
		g.renderOutline(pillX, py, pillW, 10, IronManGui.alpha(threatColour, 0.9f));
		g.drawString(font, IronManGui.fit(font, lv, pillW - 6), pillX + 4, py + 1, threatColour, true);
		if (level != null) {
			// five pips: how far up the scale it sits
			for (int k = 0; k < 5; k++) {
				int pxk = px + inner - 5 * 5 + k * 5;
				g.fill(pxk, py + 3, pxk + 4, py + 7, k <= level.ordinal() ? IronManThreat.Level.of(k).colour : 0x40FFFFFF);
			}
		}
		py += 13;
		for (int k = 0; k < reasons.size(); k++) {
			if (reasonStarts.get(k)) {
				// a small bullet in the threat colour; wrapped lines stay indented under the text
				g.fill(px + 1, py + 3, px + 4, py + 6, IronManGui.alpha(threatColour, 0.9f));
			}
			g.drawString(font, reasons.get(k), px + 7, py, IronManGui.TEXT_DIM, false);
			py += 9;
		}
		if (!power.isEmpty()) {
			py += 1;
			g.fill(px, py, px + inner, py + 1, IronManGui.alpha(IronManGui.CYAN_DIM, 0.5f));
			py += 2;
			for (FormattedCharSequence l : power) {
				g.drawString(font, l, px, py, IronManGui.TEXT, false);
				py += 9;
			}
		}
	}

	/** v0.16.7 (user): the reticle on whatever the HUD scanned this frame (or the Mark III lock), in the threat colour. */
	private static LivingEntity reticleTarget() {
		Minecraft mc = Minecraft.getInstance();
		if (reticleId < 0 || mc.level == null || System.currentTimeMillis() - reticleSeenAt > 250L) {
			return null;
		}
		Entity e = mc.level.getEntity(reticleId);
		return e instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	/**
	 * v0.16.7 (user): the holographic target reticle (it replaces the plain gold corner brackets) -- four corner brackets
	 * framing the target, a slowly turning segmented ring with small red ticks, all in the threat colour (cyan while the
	 * assessment is still coming in, then green / amber / orange / red). The Mark III lock adds the red diamond and dot.
	 */
	private static void renderReticle(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		LivingEntity target = reticleTarget();
		if (target == null) {
			target = lockTarget();
		}
		if (target == null || mc.player == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		boolean locked = reticleLocked || (lockTarget() == target);
		int rgb = reticleColour & 0xFFFFFF;
		float pt = context.tickCounter().getGameTimeDeltaPartialTick(false);
		float time = mc.level.getGameTime() + pt;
		Camera camera = context.camera();
		Vec3 cam = camera.getPosition();
		Vec3 centre = target.getPosition(pt).add(0, target.getBbHeight() * 0.5, 0);
		float size = Math.max(target.getBbWidth(), target.getBbHeight()) * 0.55f + 0.25f;
		PoseStack pose = context.matrixStack();
		pose.pushPose();
		pose.translate(centre.x - cam.x, centre.y - cam.y, centre.z - cam.z);
		pose.mulPose(camera.rotation());
		float dist = (float) cam.distanceTo(centre);
		float t = 0.018f + dist * 0.0011f;
		VertexConsumer vc = TurboDraw.buffer(context.consumers());
		// four fixed corner brackets
		float arm = size * 0.32f;
		for (int k = 0; k < 4; k++) {
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(k * 90f));
			pose.pushPose();
			pose.translate(size - arm * 0.5f, size, 0f);
			TurboDraw.box(vc, pose.last(), arm * 0.5f, t, t * 0.5f, rgb, 0.9f);
			pose.popPose();
			pose.pushPose();
			pose.translate(size, size - arm * 0.5f, 0f);
			TurboDraw.box(vc, pose.last(), t, arm * 0.5f, t * 0.5f, rgb, 0.9f);
			pose.popPose();
			pose.popPose();
		}
		// a turning segmented ring (12 arcs), and four small red ticks just outside it
		float ring = size * 0.78f;
		pose.pushPose();
		pose.mulPose(Axis.ZP.rotationDegrees(time * 2f));
		for (int k = 0; k < 12; k++) {
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(k * 30f));
			pose.translate(0f, ring, 0f);
			TurboDraw.box(vc, pose.last(), ring * 0.17f, t * 0.6f, t * 0.3f, rgb, 0.6f);
			pose.popPose();
		}
		for (int k = 0; k < 4; k++) {
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(k * 90f + 15f));
			pose.translate(0f, ring + t * 4f, 0f);
			TurboDraw.box(vc, pose.last(), t * 0.7f, t * 2.2f, t * 0.3f, 0xFF3030, 0.9f);
			pose.popPose();
		}
		pose.popPose();
		if (locked) {
			// the Mark III lock: a red diamond pulsing the other way, and a centre dot
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(45f - time * 6f));
			float in = size * (0.4f + 0.05f * (float) Math.sin(time * 0.4f));
			for (int k = 0; k < 4; k++) {
				pose.pushPose();
				pose.mulPose(Axis.ZP.rotationDegrees(k * 90f));
				pose.translate(0f, in, 0f);
				TurboDraw.box(vc, pose.last(), in * 0.35f, t * 0.8f, t * 0.4f, 0xE03030, 0.85f);
				pose.popPose();
			}
			pose.popPose();
			TurboDraw.box(vc, pose.last(), t * 1.4f, t * 1.4f, t * 0.5f, 0xFF5050, 0.9f);
		} else {
			TurboDraw.box(vc, pose.last(), t * 0.9f, t * 0.9f, t * 0.4f, rgb, 0.8f);
		}
		pose.popPose();
	}
}
