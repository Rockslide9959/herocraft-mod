package com.projecthero.mod.client.gui;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hulk.Hulk;
import com.projecthero.mod.hulk.HulkAbilities;
import com.projecthero.mod.hulk.HulkAbilityManager;
import com.projecthero.mod.hulk.HulkConfig;
import com.projecthero.mod.hulk.data.HulkState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The Hulk HUD (v0.13.14 layout), bottom-right:
 * <pre>
 *   Banner:        (nothing -- v0.16.4   Hulk:   [R][G][X][Z][V][C]
 *                   Banner has no rage;
 *                   only "Hulk ready in                  Hulk Form
 *                   42s" after a willing                 Rage 62% - Angry
 *                   change back)                         ============
 * </pre>
 * Mid-screen: the change / death sequence, the hold-H progress and the HULK SMASH wind-up. The rampage warning, the
 * rampage view and its ring minigame are {@code HulkRampageClient}'s. Only drawn for a player with the Gamma power.
 */
public final class HulkHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int HAIRLINE = 3;

	private static final int COLOR_BG = 0x80000000;
	private static final int COLOR_RAGE = 0xFF3FAF3A;
	private static final int COLOR_RAGE_READY = 0xFF7CFF4A;
	private static final int COLOR_RAGE_HULK = 0xFF9BFF3A;
	private static final int COLOR_LABEL = 0xFF9FD890;
	private static final int COLOR_EXHAUSTED = 0xFF8A8A8A;
	/** v0.15.18: indexed by {@link HulkConfig#rageTier}. */
	private static final String[] TIER_KEYS = { "hud.projecthero.hulk.tier.calm", "hud.projecthero.hulk.tier.angry",
			"hud.projecthero.hulk.tier.enraged" };
	private static final ChatFormatting[] TIER_STYLES = { ChatFormatting.GRAY, ChatFormatting.GOLD, ChatFormatting.RED };

	private HulkHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.options.hideGui || mc.level == null) {
			return;
		}
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = mc.level.getGameTime();
		if (com.projecthero.mod.client.hulk.HulkRampageClient.viewTaken()) {
			return; // v0.16.4: the rampage view owns the screen (its ring minigame sits in the black strip)
		}
		int totalW = 6 * BOX + 5 * GAP;
		int x0 = g.guiWidth() - MARGIN - totalW;
		int bottom = g.guiHeight() - MARGIN;

		// v0.16.4 (user): "Rage bar only in Hulk form" -- Banner has no rage, and no bar. v0.16.4 (user): no timer for the hold-H
		// cooldown either ("it also shouldnt show a timer for the 1 minute transformation cooldown")
		if (!s.hulk) {
			renderCentre(g, mc, player, s, now);
			return;
		}

		// rage: label and the bar ("Rage 62% - Angry"); v0.16.4: it also sets his size and his damage (+0..+50%)
		int rageBar = bottom - HAIRLINE;
		int pct = Math.round(Math.max(0.0f, Math.min(1.0f, s.rage / HulkConfig.RAGE_MAX)) * 100.0f);
		int tier = HulkConfig.rageTier(s.rage);
		Component label = Component.translatable("hud.projecthero.hulk.rage_tier", pct,
				Component.translatable(TIER_KEYS[tier]).withStyle(TIER_STYLES[tier]));
		if (!s.worldBreaker(now)) { // v0.16.4: World Breaker writes its countdown here instead
			g.drawString(mc.font, label, x0, rageBar - 11, COLOR_LABEL, false);
		}
		int bx = x0;
		int bw = totalW;
		int fill = tier == HulkConfig.TIER_CALM ? COLOR_RAGE : COLOR_RAGE_HULK;
		if (s.rage > HulkConfig.RAGE_TIER_ENRAGED) {
			// an Enraged Hulk: the bar throbs, faster as it nears 100
			float heat = (s.rage - HulkConfig.RAGE_TIER_ENRAGED) / (HulkConfig.RAGE_MAX - HulkConfig.RAGE_TIER_ENRAGED);
			float t = (now + delta.getGameTimeDeltaPartialTick(false)) * (0.25f + 0.35f * heat);
			fill = (Math.sin(t) > 0.3) ? 0xFFB8FF7A : COLOR_RAGE_READY;
		}
		boolean worldBreaker = s.worldBreaker(now);
		float pt = delta.getGameTimeDeltaPartialTick(false);
		if (worldBreaker) {
			// v0.16.4 (user): World Breaker -- the rage bar becomes its 30 s countdown, pulsing deep green
			int left = com.projecthero.mod.hulk.WorldBreaker.ticksLeft(player);
			float f = Math.max(0.0f, Math.min(1.0f, left / (float) Math.max(1, HulkConfig.worldBreaker().durationTicks)));
			float pulse = 0.5f + 0.5f * (float) Math.sin((now + pt) * 0.4f);
			int col = lerpColour(0xFF2FD12A, 0xFFD8FFB0, pulse);
			g.fill(bx, rageBar - 1, bx + bw, rageBar + HAIRLINE + 1, COLOR_BG);
			g.fill(bx, rageBar - 1, bx + Math.round(bw * f), rageBar + HAIRLINE + 1, col);
		} else {
			g.fill(bx, rageBar, bx + bw, rageBar + HAIRLINE, COLOR_BG);
			g.fill(bx, rageBar, bx + Math.round(bw * pct / 100.0f), rageBar + HAIRLINE, fill);
			// v0.15.18: where the Angry and Enraged tiers start
			for (float at : new float[] { HulkConfig.RAGE_TIER_ANGRY, HulkConfig.RAGE_TIER_ENRAGED }) {
				int mark = bx + Math.round(bw * (at / HulkConfig.RAGE_MAX));
				g.fill(mark, rageBar - 1, mark + 1, rageBar + HAIRLINE + 1, 0xC0FFFFFF);
			}
		}

		if (s.hulk) {
			int formY = rageBar - 22;
			boolean gladiator = com.projecthero.mod.hulk.gladiator.GladiatorAbilities.active(player);
			// v0.16.4 (user): World Breaker's line sits where the form name is, right-aligned with the boxes
			Component wbLine = null;
			int wbColour = 0;
			if (worldBreaker) {
				wbLine = Component.translatable("hud.projecthero.hulk.world_breaker_active").withStyle(ChatFormatting.BOLD);
				wbColour = (now / 4L) % 2L == 0L ? 0xFF9BFF3A : 0xFFE8FFD8;
			} else if (com.projecthero.mod.hulk.WorldBreaker.available(player)) {
				// the prompt: H unleashes it (pulsing)
				float pulse = 0.5f + 0.5f * (float) Math.sin((now + pt) * 0.35f);
				wbLine = Component.translatable("hud.projecthero.hulk.world_breaker_ready").withStyle(ChatFormatting.BOLD);
				wbColour = lerpColour(0xFF3FAF3A, 0xFFE8FFD8, pulse);
			} else if (com.projecthero.mod.hulk.WorldBreaker.coolingDown(player)) {
				// v0.16.4 (user dislikes timers on Hulk lockouts): just "burned out", greyed
				wbLine = Component.translatable("hud.projecthero.hulk.world_breaker_burnout");
				wbColour = COLOR_EXHAUSTED;
			}
			if (wbLine != null) {
				int right = x0 + totalW;
				int ww = mc.font.width(wbLine);
				g.drawString(mc.font, wbLine, right - ww, formY, wbColour, true);
				formY -= 10; // the form name moves up a line, over the boxes
				if (worldBreaker) {
					// the countdown on the rage bar's line (a running form, not a lockout)
					int secs = (com.projecthero.mod.hulk.WorldBreaker.ticksLeft(player) + 19) / 20;
					Component cd = Component.translatable("hud.projecthero.hulk.world_breaker_left", secs);
					g.drawString(mc.font, cd, x0, rageBar - 11, 0xFF9BFF3A, false);
				}
			}
			if (gladiator) {
				// v0.15.3: the full gladiator gear swaps in the weapon kit
				g.drawString(mc.font, Component.translatable("hud.projecthero.hulk.gladiator_form").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
						x0, formY, 0xFFFFC34A, true);
				renderGladiatorKeys(g, mc, player, s, x0, formY - 3 - BOX, now);
			} else {
				g.drawString(mc.font, Component.translatable("hud.projecthero.hulk.form").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
						x0, formY, 0xFF55FF55, true);
				renderKeys(g, mc, player, s, x0, formY - 3 - BOX, now);
			}
			// v0.16.4 (user): the Super Leap charge sits right above his ability boxes
			com.projecthero.mod.client.hulk.HulkMovesClient.renderLeapBar(g, x0, formY - 3 - BOX - 3, totalW);
			// v0.15.21: he can't see his own back -- who is riding it
			Player rider = com.projecthero.mod.hulk.HulkRiding.rider(player);
			int hudTop = formY - 3 - BOX - 3 - 2; // above the boxes and the Super Leap bar
			if (rider != null) {
				Component carrying = Component.translatable("hud.projecthero.hulk.carrying", rider.getDisplayName())
						.withStyle(ChatFormatting.GREEN);
				java.util.List<net.minecraft.util.FormattedCharSequence> rows = mc.font.split(carrying, totalW);
				int cy = formY - 3 - BOX - 4 - rows.size() * 9;
				hudTop = Math.min(hudTop, cy);
				for (net.minecraft.util.FormattedCharSequence row : rows) {
					g.drawString(mc.font, row, g.guiWidth() - MARGIN - mc.font.width(row), cy, 0xFF9CFF9C, true);
					cy += 9;
				}
			}
			// v0.16.6: Left-Alt -> the shared move list (each key: tap move + Shift move), stacked above the whole HUD
			if (MoveListOverlay.held()) {
				java.util.List<MoveListOverlay.Row> moves = new java.util.ArrayList<>();
				for (int i = 0; i < 6; i++) {
					AbilitySlot slot = AbilitySlot.byNumber(i + 1);
					if (gladiator) {
						String tap = com.projecthero.mod.hulk.gladiator.GladiatorAbilities.tapId(slot);
						String alt = com.projecthero.mod.hulk.gladiator.GladiatorAbilities.shiftId(slot);
						moves.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot),
								Component.translatable(com.projecthero.mod.hulk.gladiator.GladiatorAbilities.langName(tap)),
								alt == null || alt.equals(tap) ? null
										: Component.translatable(com.projecthero.mod.hulk.gladiator.GladiatorAbilities.langName(alt))));
					} else {
						String tap = HulkAbilityManager.abilityIdOf(slot);
						String alt = HulkAbilityManager.shiftIdOf(slot);
						moves.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.hulk.ability." + tap),
								alt == null || alt.equals(tap) ? null : Component.translatable("projecthero.hulk.ability." + alt)));
					}
				}
				MoveListOverlay.draw(g, x0 + totalW, hudTop, moves);
			}
		}
		renderCentre(g, mc, player, s, now);
	}

	/** v0.16.4: a straight blend of two ARGB colours ({@code t} 0..1). */
	private static int lerpColour(int a, int b, float t) {
		t = Math.max(0.0f, Math.min(1.0f, t));
		int out = 0;
		for (int shift = 0; shift <= 24; shift += 8) {
			int ca = (a >>> shift) & 0xFF;
			int cb = (b >>> shift) & 0xFF;
			out |= (Math.round(ca + (cb - ca) * t) & 0xFF) << shift;
		}
		return out;
	}

	private static void renderKeys(GuiGraphics g, Minecraft mc, Player player, HulkState s, int x0, int y0, long now) {
		// v0.16.4 (user): the rebuilt kit -- each box shows the tap move (or, while Shift is held, the Shift move) and a strip
		// along its bottom for the other one; Alt or Shift lists the names above the boxes. The leap bar is HulkMovesClient's.
		long win = mc.getWindow().getWindow();
		boolean shift = player.isShiftKeyDown() || org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
				== org.lwjgl.glfw.GLFW.GLFW_PRESS;
		boolean locked = s.rampaging(now) || s.combat.calming;
		for (int i = 0; i < 6; i++) {
			AbilitySlot slot = AbilitySlot.byNumber(i + 1);
			int x = x0 + i * (BOX + GAP);
			String tap = HulkAbilityManager.abilityIdOf(slot);
			String alt = HulkAbilityManager.shiftIdOf(slot);
			String main = shift ? alt : tap;
			String other = shift ? tap : alt;
			boolean active = (slot == AbilitySlot.SLOT_5 && s.combat.holding) || (slot == AbilitySlot.SLOT_4 && s.combat.chargeUntil > now)
					|| (slot == AbilitySlot.SLOT_3 && (s.leapChargeStart > 0L || s.leaping));
			g.fill(x, y0, x + BOX, y0 + BOX, shift ? 0xC0142414 : 0xC0101A10);
			g.renderOutline(x, y0, BOX, BOX, active ? 0xFF7CFF4A : (shift ? 0xFF4F9A5E : 0xFF2E6A3E));
			g.drawString(mc.font, String.valueOf(slot.defaultKey()), x + 2, y0 + 2, locked ? 0xFF6A7A6A : 0xFFD8F0DC, false);
			if (shift) {
				g.fill(x + BOX - 5, y0 + 2, x + BOX - 2, y0 + 5, 0xFF7CFF4A); // a dot: these are the Shift moves
			}
			int cd = HulkAbilities.cooldownRemaining(player, main);
			int max = HulkAbilityManager.maxCooldown(main);
			if (locked) {
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, 0x90000000);
			} else if (cd > 0 && max > 0) {
				int h = (int) ((BOX - 2) * Math.min(1f, cd / (float) max));
				g.fill(x + 1, y0 + BOX - 1 - h, x + BOX - 1, y0 + BOX - 1, 0xB0000000);
				g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2 + 2, y0 + 10, 0xFFFFFFFF);
			}
			if (!other.equals(main)) {
				int ocd = HulkAbilities.cooldownRemaining(player, other);
				int omax = HulkAbilityManager.maxCooldown(other);
				float ready = omax <= 0 ? 1.0f : 1.0f - Math.min(1.0f, ocd / (float) omax);
				g.fill(x + 1, y0 + BOX - 3, x + 1 + Math.round((BOX - 2) * ready), y0 + BOX - 1, ocd > 0 ? 0xFF4F7F2A : 0xFFB8FF9A);
			}
		}
		if (shift && !MoveListOverlay.held()) { // v0.16.6: Left-Alt is the shared move list (render)
			// one short line per key, stacked above the boxes and right-aligned with them (clear of the hotbar)
			int right = x0 + 6 * BOX + 5 * GAP;
			int lineY = y0 - 4 - 6 * 10;
			for (int i = 0; i < 6; i++) {
				AbilitySlot slot = AbilitySlot.byNumber(i + 1);
				String id = shift ? HulkAbilityManager.shiftIdOf(slot) : HulkAbilityManager.abilityIdOf(slot);
				Component name = Component.literal(slot.defaultKey() + " ").withStyle(ChatFormatting.GREEN)
						.append(Component.translatable("projecthero.hulk.ability." + id).withStyle(ChatFormatting.WHITE));
				g.drawString(mc.font, name, right - mc.font.width(name), lineY + i * 10, 0xFFFFFFFF, true);
			}
		}
	}

	/**
	 * v0.15.3: the Gladiator kit's six boxes. Each shows the tap move's cooldown -- or, while Shift is held, the Shift
	 * move's -- and a thin strip along its bottom for the other one. X
	 * above the boxes, so the HUD itself stays six small boxes.
	 */
	private static void renderGladiatorKeys(GuiGraphics g, Minecraft mc, Player player, HulkState s, int x0, int y0, long now) {
		long win = mc.getWindow().getWindow();
		boolean shift = player.isShiftKeyDown() || org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
				== org.lwjgl.glfw.GLFW.GLFW_PRESS;
		boolean locked = s.rampaging(now) || s.combat.calming;
		boolean busy = com.projecthero.mod.hulk.gladiator.GladiatorAbilities.busy(player);
		for (int i = 0; i < 6; i++) {
			AbilitySlot slot = AbilitySlot.byNumber(i + 1);
			int x = x0 + i * (BOX + GAP);
			String tap = com.projecthero.mod.hulk.gladiator.GladiatorAbilities.tapId(slot);
			String alt2 = com.projecthero.mod.hulk.gladiator.GladiatorAbilities.shiftId(slot);
			String main = shift ? alt2 : tap;
			String other = shift ? tap : alt2;
			boolean active = switch (slot) {
				case SLOT_3 -> busy && !com.projecthero.mod.hulk.gladiator.GladiatorAbilities.whirling(player)
						&& !com.projecthero.mod.hulk.gladiator.GladiatorAbilities.grappling(player);
				case SLOT_4 -> com.projecthero.mod.hulk.gladiator.GladiatorAbilities.roaring(player);
				case SLOT_5 -> com.projecthero.mod.hulk.gladiator.GladiatorAbilities.whirling(player)
						|| com.projecthero.mod.hulk.gladiator.GladiatorAbilities.grappling(player);
				case SLOT_6 -> com.projecthero.mod.hulk.gladiator.GladiatorAbilities.axeAway(player)
						|| com.projecthero.mod.hulk.gladiator.GladiatorAbilities.hammerAway(player);
				default -> false;
			};
			g.fill(x, y0, x + BOX, y0 + BOX, shift ? 0xC0201808 : 0xC0181408);
			g.renderOutline(x, y0, BOX, BOX, active ? 0xFFFFD866 : (shift ? 0xFFB07A2A : 0xFF7A5A26));
			g.drawString(mc.font, String.valueOf(slot.defaultKey()), x + 2, y0 + 2, locked ? 0xFF6A6A6A : 0xFFF2E2C0, false);
			if (shift) {
				g.fill(x + BOX - 5, y0 + 2, x + BOX - 2, y0 + 5, 0xFFFFC34A); // a dot: these are the Shift moves
			}
			int cd = com.projecthero.mod.hulk.HulkAbilities.cooldownRemaining(player, main);
			int max = com.projecthero.mod.hulk.gladiator.GladiatorAbilities.maxCooldown(main);
			if (locked) {
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, 0x90000000);
			} else if (cd > 0 && max > 0) {
				int h = (int) ((BOX - 2) * Math.min(1f, cd / (float) max));
				g.fill(x + 1, y0 + BOX - 1 - h, x + BOX - 1, y0 + BOX - 1, 0xB0000000);
				g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2 + 2, y0 + 10, 0xFFFFFFFF);
			}
			// the other move on this key: a strip along the bottom, bright when it is ready
			int ocd = com.projecthero.mod.hulk.HulkAbilities.cooldownRemaining(player, other);
			int omax = com.projecthero.mod.hulk.gladiator.GladiatorAbilities.maxCooldown(other);
			float ready = omax <= 0 ? 1.0f : 1.0f - Math.min(1.0f, ocd / (float) omax);
			g.fill(x + 1, y0 + BOX - 3, x + 1 + Math.round((BOX - 2) * ready), y0 + BOX - 1, ocd > 0 ? 0xFF6A4A1A : 0xFFFFD27A);
		}
		if (shift && !MoveListOverlay.held()) { // v0.16.6: Left-Alt is the shared move list (render)
			// the names, one short line per key, stacked above the boxes and right-aligned with them (clear of the hotbar)
			int right = x0 + 6 * BOX + 5 * GAP;
			int lineY = y0 - 4 - 6 * 10;
			if (shift) {
				Component head = Component.translatable("hud.projecthero.hulk.gladiator_shift").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
				g.drawString(mc.font, head, right - mc.font.width(head), lineY - 11, 0xFFFFFFFF, true);
			}
			for (int i = 0; i < 6; i++) {
				AbilitySlot slot = AbilitySlot.byNumber(i + 1);
				String id = shift ? com.projecthero.mod.hulk.gladiator.GladiatorAbilities.shiftId(slot)
						: com.projecthero.mod.hulk.gladiator.GladiatorAbilities.tapId(slot);
				Component name = Component.literal(slot.defaultKey() + " ")
						.withStyle(ChatFormatting.GOLD)
						.append(Component.translatable(com.projecthero.mod.hulk.gladiator.GladiatorAbilities.langName(id))
								.withStyle(ChatFormatting.WHITE));
				g.drawString(mc.font, name, right - mc.font.width(name), lineY + i * 10, 0xFFFFFFFF, true);
			}
		}
	}

	/**
	 * Mid-screen: the death sequence and the HULK SMASH wind-up (v0.16.4: no change / hold-H bar any more). (v0.16.4: the rampage
	 * warning and the ring minigame are {@code HulkRampageClient}'s; the keep-control prompt and the calm-down are gone.)
	 */
	private static void renderCentre(GuiGraphics g, Minecraft mc, Player player, HulkState s, long now) {
		int cx = g.guiWidth() / 2;
		int y = g.guiHeight() / 2 - 62; // above the crosshair, clear of the action bar and the hotbar
		if (s.dying()) {
			int col = (now / 8L) % 2L == 0L ? 0xFFFF5544 : 0xFF8A2A2A;
			g.drawCenteredString(mc.font, Component.translatable("hud.projecthero.hulk.dying").withStyle(ChatFormatting.BOLD), cx, y, col);
			return;
		}
		// v0.16.4 (user): "remove the bar that shows up while youre transforming ... remove the text above that bar aswell" --
		// no "changing" / hold-H progress bar or text; the change itself (pose, morph, sound) shows it
		if (Hulk.changing(s, now) || Hulk.holdProgress(player) > 0.0f || (!s.hulk && s.combat.strainStart != 0L)) {
			return;
		}
		if (com.projecthero.mod.hulk.HulkControl.warning(s, now)) {
			return; // the arrow string is up (HulkRampageClient)
		}
		float smash = HulkAbilities.hulkSmashCharge(player);
		if (smash > 0.0f) {
			int w = 120;
			g.drawCenteredString(mc.font, Component.translatable("hud.projecthero.hulk.hulk_smash_charge", Math.round(smash * 100.0f))
					.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), cx, y, 0xFF7CFF4A);
			g.fill(cx - w / 2, y + 11, cx + w / 2, y + 14, COLOR_BG);
			g.fill(cx - w / 2, y + 11, cx - w / 2 + Math.round(w * smash), y + 14, smash >= 1.0f ? 0xFFFFFFFF : 0xFF7CFF4A);
			y += 20;
		}
	}
}
