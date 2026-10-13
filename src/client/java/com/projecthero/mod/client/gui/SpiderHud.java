package com.projecthero.mod.client.gui;

import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.spider.SpiderManConfig;
import com.projecthero.mod.spider.SpiderMoves;
import com.projecthero.mod.spider.SpiderWebReserve;
import com.projecthero.mod.spider.data.SpiderManState;
import com.projecthero.mod.spider.data.SpiderMoveState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Spider-Man HUD (v0.16.1): six key boxes (R G X Z V C) showing each key's tap move and its cooldown, with two thin
 * bars along the bottom of every box for that key's Shift move (blue) and hold move (red); hold Left Alt for every
 * key's three move names. Under the row: "Spider-Man", the Web Reserve, and a status line for Weave Mode / the
 * tightrope's anchor A / a charging move. Also the Spider-Sense danger markers and (v0.16.1) the web-alarm arrow
 * pointing at whatever just touched one of his webs.
 */
public final class SpiderHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	/** Height of the label stack under the key row (name, web %, bar, status) -- also lifts the whole HUD. */
	static final int BOTTOM_STACK = 48; // package-private (v0.13.21): SymbioteHud stacks the Black Suit panel on it

	private static final int COLOR_BOX_BG = 0xC0180C10;
	private static final int COLOR_BORDER = 0xFF4A1E28;
	private static final int COLOR_ACTIVE = 0xFFFF6070;
	private static final int COLOR_COOLDOWN = 0xB0000000;
	private static final int COLOR_KEY = 0xFFE8B0B8;
	private static final int COLOR_WEB = 0xFFE8E8F4;
	private static final int COLOR_WEB_TEXT = 0xFFE8E8F4;
	private static final int COLOR_WEB_LOW = 0xFFFF7A3C;
	private static final int BAR_TRACK = 0xC0101018;
	private static final int SHIFT_COOLING = 0xFF4E6A8C;
	private static final int SHIFT_READY = 0xFFA8D2FF;
	private static final int HOLD_COOLING = 0xFF8C3A46;
	private static final int HOLD_READY = 0xFFFF8A96;
	/** Widest an Alt-panel line may be before it wraps (TooltipWrap rule: UI text never sprawls). */
	private static final int NAMES_WIDTH = 190;

	// ---- Spider-Sense warning (v0.6.17; v0.6.19: one marker per kind, held until the danger passes) ----
	private static final int WARN_KINDS = 6;
	private static final int WARN_LINGER = 22;
	private static final long[] warnUntil = new long[WARN_KINDS];
	private static final float[] warnYaw = new float[WARN_KINDS];
	private static final int[] warnVertical = new int[WARN_KINDS];

	// ---- v0.16.1 web alarm ----
	private static final int ALARM_TICKS = 80;
	private static Vec3 alarmAt;
	private static long alarmUntil;

	private SpiderHud() {
	}

	/** Called from the network handler when a Spider-Sense warning arrives. */
	public static void flashWarning(int kind, float yaw, int vertical) {
		if (kind < 0 || kind >= WARN_KINDS) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		warnUntil[kind] = (mc.level != null ? mc.level.getGameTime() : 0L) + WARN_LINGER;
		warnYaw[kind] = yaw;
		warnVertical[kind] = vertical;
	}

	/** v0.16.1: something that is not him or his squad touched one of his webs at {@code at}. */
	public static void webAlarm(Vec3 at) {
		Minecraft mc = Minecraft.getInstance();
		alarmAt = at;
		alarmUntil = (mc.level != null ? mc.level.getGameTime() : 0L) + ALARM_TICKS;
		if (mc.player != null) {
			mc.player.playSound(com.projecthero.mod.spider.SpiderSounds.SENSE, 0.9f, 1.4f);
		}
	}

	private static int warningColor(int kind) {
		return switch (kind) {
			case 2 -> 0xFFFF5533; // explosion
			case 4 -> 0xFFFF8833; // environment
			default -> 0xFFFF3355; // melee / projectile / targeted
		};
	}

	private static void renderWarning(GuiGraphics g, Minecraft client, Player player) {
		long now = client.level != null ? client.level.getGameTime() : 0L;
		boolean any = false;
		for (int k = 0; k < WARN_KINDS; k++) {
			if (now < warnUntil[k]) {
				any = true;
				break;
			}
		}
		if (!any) {
			return;
		}
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		boolean blink = (now % 6) < 3;
		int pulseCol = 0xFFFF3355;
		for (int k : new int[] {2, 4, 5, 1, 0}) {
			if (now < warnUntil[k]) {
				pulseCol = warningColor(k);
				break;
			}
		}
		if (blink) {
			g.renderOutline(cx - 10, cy - 10, 20, 20, pulseCol);
			g.renderOutline(cx - 14, cy - 14, 28, 28, (pulseCol & 0x00FFFFFF) | 0x66000000);
		}
		for (int k = 0; k < WARN_KINDS; k++) {
			if (now >= warnUntil[k]) {
				continue;
			}
			int col = warningColor(k);
			float rel = Mth.wrapDegrees(warnYaw[k] - player.getYRot());
			double rad = Math.toRadians(rel);
			int mx = cx + (int) (Math.sin(rad) * 44);
			int my = cy - (int) (Math.cos(rad) * 44);
			if (warnVertical[k] == 1) {
				my = cy - 46;
			} else if (warnVertical[k] == 2) {
				my = cy + 46;
			}
			g.fill(mx - 3, my - 3, mx + 3, my + 3, blink ? col : (col & 0x00FFFFFF) | 0x88000000);
		}
	}

	/** The web alarm: an arrow round the crosshair pointing at the touch, with the distance, for four seconds. */
	private static void renderAlarm(GuiGraphics g, Minecraft mc, Player player, float partial) {
		long now = mc.level != null ? mc.level.getGameTime() : 0L;
		if (alarmAt == null || now >= alarmUntil) {
			return;
		}
		Vec3 eye = player.getEyePosition(partial);
		Vec3 d = alarmAt.subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float rel = Mth.wrapDegrees(yaw - player.getViewYRot(partial));
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		float fade = Math.min(1f, (alarmUntil - now) / 20f);
		int alpha = (int) (255 * fade) << 24;
		boolean blink = (now % 8) < 5;
		int col = (blink ? 0xFFFFFF : 0xE0C8FF) | alpha;
		g.pose().pushPose();
		g.pose().translate(cx, cy, 0);
		g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rel));
		// shaft + head, pointing "up" (= ahead) before the rotation
		g.fill(-1, -62, 1, -52, col);
		for (int i = 0; i < 5; i++) {
			g.fill(-i - 1, -66 + i, i + 1, -65 + i, col);
		}
		g.pose().popPose();
		double rad = Math.toRadians(rel);
		int lx = cx + (int) (Math.sin(rad) * 78);
		int ly = cy - (int) (Math.cos(rad) * 78) - 4;
		Component label = Component.translatable("hud.projecthero.spider_man.web_alarm", (int) Math.round(d.length()));
		g.drawCenteredString(mc.font, label, lx, ly, (0xFFE0E8 | alpha));
	}

	public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		if (player == null || client.options.hideGui) {
			return;
		}
		SpiderManState state = player.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
		if (state == null || !state.hasPower) {
			return;
		}
		float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
		// The Spider-Sense warning and the web alarm show regardless of which power holds the ability slots.
		renderWarning(graphics, client, player);
		renderAlarm(graphics, client, player, partial);
		renderBlossomCharge(graphics, client, player);
		// Mirrors SpiderManAbilityManager.hasContext: a mutation selected in the wheel owns the slots.
		ExperimentalState experimental = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (experimental != null && !experimental.activePower.isEmpty()) {
			return;
		}
		if (com.projecthero.mod.power.ThorPowers.wieldsThorWeapon(player) || wearingIronMan(player)) {
			return;
		}

		SpiderMoveState move = player.getAttachedOrElse(ModAttachments.SPIDER_MOVE_STATE, new SpiderMoveState());
		long gameTime = client.level != null ? client.level.getGameTime() : 0L;
		int screenW = graphics.guiWidth();
		int screenH = graphics.guiHeight();
		int totalW = 6 * BOX + 5 * GAP;
		int x0 = screenW - MARGIN - totalW;
		int y0 = screenH - MARGIN - BOX - BOTTOM_STACK;
		boolean expanded = org.lwjgl.glfw.GLFW.glfwGetKey(client.getWindow().getWindow(),
				org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

		for (int i = 0; i < 6; i++) {
			AbilitySlot slot = AbilitySlot.byNumber(i + 1);
			int x = x0 + i * (BOX + GAP);
			String tap = SpiderMoves.TAP[i];
			int cd = SpiderMan.abilityCooldownRemaining(player, tap);
			int max = Math.max(1, SpiderMoves.cfg(tap).cooldown);
			graphics.fill(x, y0, x + BOX, y0 + BOX, COLOR_BOX_BG);
			graphics.renderOutline(x, y0, BOX, BOX, active(move, i, state) ? COLOR_ACTIVE : COLOR_BORDER);
			if (cd > 0) {
				int h = (int) (BOX * Math.min(1f, cd / (float) max));
				graphics.fill(x + 1, y0 + BOX - h, x + BOX - 1, y0 + BOX - 1, COLOR_COOLDOWN);
				graphics.drawCenteredString(client.font, String.valueOf((cd + 19) / 20), x + BOX / 2, y0 + 5, 0xFFFFFFFF);
			} else {
				graphics.drawString(client.font, String.valueOf(slot.defaultKey()), x + 7, y0 + 5, COLOR_KEY, true);
			}
			bar(graphics, player, SpiderMoves.SHIFT[i], x, y0 + BOX - 5, SHIFT_COOLING, SHIFT_READY);
			bar(graphics, player, SpiderMoves.HOLD[i], x, y0 + BOX - 3, HOLD_COOLING, HOLD_READY);
		}
		if (expanded) {
			renderNames(graphics, client, x0, y0);
		}

		// Web Reserve. The ceiling doubles once bonded with a Symbiote (v0.9.10).
		float webMax = SpiderWebReserve.maxFor(player);
		float ratio = Math.max(0.0f, Math.min(1.0f, state.webReserve / webMax));
		int nameY = y0 + BOX + 3;
		graphics.drawString(client.font, Component.translatable("projecthero.spider_man.name")
				.withStyle(ChatFormatting.RED, ChatFormatting.BOLD), x0, nameY, 0xFFFF6070);
		int webY = nameY + 10;
		graphics.drawString(client.font, Component.translatable("hud.projecthero.spider_man.web_reserve",
				(int) Math.ceil(ratio * 100.0f)), x0, webY, COLOR_WEB_TEXT, false);
		// v0.16.1: the N wall-crawl switch, right-aligned on the Web line
		boolean crawlOn = !player.getAttachedOrElse(ModAttachments.SPIDER_WALL_CRAWL_OFF, false);
		Component crawl = Component.translatable(crawlOn ? "hud.projecthero.spider_man.wall_crawl_on" : "hud.projecthero.spider_man.wall_crawl_off");
		graphics.drawString(client.font, crawl, x0 + totalW - client.font.width(crawl), webY, crawlOn ? 0xFFA8E0A8 : 0xFFB0A0A0, false);
		int barY = webY + 10;
		graphics.fill(x0, barY, x0 + totalW, barY + 3, 0xAA180C10);
		graphics.fill(x0, barY, x0 + Math.round(totalW * ratio), barY + 3, ratio < 0.2f ? COLOR_WEB_LOW : COLOR_WEB);

		Component status = status(move, gameTime);
		if (status != null) {
			List<FormattedCharSequence> rows = client.font.split(status, totalW);
			for (int r = 0; r < Math.min(2, rows.size()); r++) {
				graphics.drawString(client.font, rows.get(r), x0, barY + 6 + r * 9, 0xFFFFE0A0, false);
			}
		}
	}

	/** The key's stance is running (outline it). */
	private static boolean active(SpiderMoveState m, int i, SpiderManState s) {
		return switch (i) {
			case 0 -> m.has(SpiderMoveState.FLAG_CHARGING_IMPACT);
			case 2 -> m.has(SpiderMoveState.FLAG_COUNTER) || m.has(SpiderMoveState.FLAG_AIR_COMBO);
			case 3 -> m.has(SpiderMoveState.FLAG_SWING_THROW);
			case 4 -> m.has(SpiderMoveState.FLAG_CHARGING_BLOSSOM);
			case 5 -> m.has(SpiderMoveState.FLAG_WEAVING) || m.has(SpiderMoveState.FLAG_ANCHOR_A);
			default -> false;
		};
	}

	/** A Shift / hold cooldown bar along the bottom of a box: refills, bright once ready. */
	private static void bar(GuiGraphics g, Player player, String id, int x, int y, int cooling, int ready) {
		int cd = SpiderMan.abilityCooldownRemaining(player, id);
		int max = Math.max(1, SpiderMoves.cfg(id).cooldown);
		float f = cd <= 0 ? 1f : 1f - Math.min(1f, cd / (float) max);
		g.fill(x + 1, y, x + BOX - 1, y + 2, BAR_TRACK);
		g.fill(x + 1, y, x + 1 + Math.round((BOX - 2) * f), y + 2, f >= 1f ? ready : cooling);
	}

	/** Alt held: every key's tap / Shift / hold names, wrapped, stacked above-left of the row. */
	private static void renderNames(GuiGraphics g, Minecraft mc, int x0, int y0) {
		java.util.List<FormattedCharSequence> lines = new java.util.ArrayList<>();
		java.util.List<Integer> colours = new java.util.ArrayList<>();
		for (int i = 0; i < 6; i++) {
			Component line = Component.literal(AbilitySlot.byNumber(i + 1).defaultKey() + "  ").withStyle(ChatFormatting.GOLD)
					.append(Component.translatable("hud.projecthero.spider_man.move_triple",
							Component.translatable("projecthero.spider_man.ability." + SpiderMoves.TAP[i]),
							Component.translatable("projecthero.spider_man.ability." + SpiderMoves.SHIFT[i]),
							Component.translatable("projecthero.spider_man.ability." + SpiderMoves.HOLD[i]))
							.withStyle(ChatFormatting.WHITE));
			for (FormattedCharSequence row : mc.font.split(line, NAMES_WIDTH)) {
				lines.add(row);
				colours.add(0xFFE0C8CC);
			}
		}
		lines.add(mc.font.split(Component.translatable("hud.projecthero.spider_man.traversal_hint"), NAMES_WIDTH).get(0));
		colours.add(0xFFA0A0B0);
		int width = 0;
		for (FormattedCharSequence l : lines) {
			width = Math.max(width, mc.font.width(l));
		}
		int x = Math.max(2, x0 - 8 - width);
		int y = y0 + BOX - lines.size() * 10;
		g.fill(x - 3, y - 3, x + width + 3, y + lines.size() * 10 + 1, 0x90100608);
		for (int i = 0; i < lines.size(); i++) {
			g.drawString(mc.font, lines.get(i), x, y + i * 10, colours.get(i), true);
		}
	}

	/** Weave Mode / anchor A / charging status for the line under the Web Reserve, or null. */
	private static Component status(SpiderMoveState m, long now) {
		if (m.has(SpiderMoveState.FLAG_WEAVING)) {
			return Component.translatable("hud.projecthero.spider_man.status.weave", m.strands,
					SpiderManConfig.webs().weaveMaxStrands);
		}
		if (m.has(SpiderMoveState.FLAG_ANCHOR_A)) {
			return Component.translatable("hud.projecthero.spider_man.status.anchor");
		}
		if (m.has(SpiderMoveState.FLAG_CHARGING_IMPACT)) {
			int pct = (int) (100 * Math.min(1f, (now - m.chargeStart) / (float) Math.max(1, SpiderManConfig.moves().impactChargeTicks)));
			return Component.translatable("hud.projecthero.spider_man.status.impact", pct);
		}
		if (m.has(SpiderMoveState.FLAG_COUNTER)) {
			return Component.translatable("hud.projecthero.spider_man.status.counter");
		}
		if (m.has(SpiderMoveState.FLAG_AIR_COMBO)) {
			return Component.translatable("hud.projecthero.spider_man.status.air_combo");
		}
		if (m.has(SpiderMoveState.FLAG_HANGING)) {
			return Component.translatable("hud.projecthero.spider_man.status.hanging");
		}
		return null;
	}

	/** The Web Blossom buildup bar, drawn while V is held. Sits above the key row. */
	private static void renderBlossomCharge(GuiGraphics g, Minecraft client, Player player) {
		int ticks = player.getAttachedOrElse(ModAttachments.SPIDER_BLOSSOM_CHARGE, 0);
		if (ticks <= 0) {
			return;
		}
		float ratio = Math.min(1.0f, ticks / (float) com.projecthero.mod.spider.SpiderAbilities.blossomChargeTicks(false));
		boolean full = ratio >= 1.0f;
		int w = 6 * BOX + 5 * GAP;
		int h = 3;
		int x = g.guiWidth() - MARGIN - w;
		int boxesY = g.guiHeight() - MARGIN - BOX - BOTTOM_STACK;
		int y = boxesY - 14 - h - SymbioteHud.blackSuitPanelLift(player);
		g.drawCenteredString(client.font, Component.translatable("hud.projecthero.spider_man.web_blossom"),
				x + w / 2, y - 10, full ? 0xFFFFF0A0 : 0xFFE8E8F4);
		g.fill(x, y, x + w, y + h, 0xC0180C10);
		g.fill(x, y, x + Math.round(w * ratio), y + h, full ? 0xFFFF6070 : COLOR_WEB);
	}

	private static boolean wearingIronMan(Player player) {
		for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[] {
				net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
				net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET }) {
			if (player.getItemBySlot(slot).getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem) {
				return true;
			}
		}
		return false;
	}
}
