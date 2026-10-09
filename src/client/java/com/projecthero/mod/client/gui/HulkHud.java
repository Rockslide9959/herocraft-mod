package com.projecthero.mod.client.gui;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hulk.Hulk;
import com.projecthero.mod.hulk.HulkAbilities;
import com.projecthero.mod.hulk.HulkAbilityManager;
import com.projecthero.mod.hulk.HulkConfig;
import com.projecthero.mod.hulk.HulkControl;
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
 *   Banner:        Rage 62%              Hulk:   [R][G][X][Z][V][C]
 *     (or Exhausted 7s, the bar
 *      running down the timer)
 *                  ============                  Hulk Form
 *                                                Rage 62%
 *                                                ============
 *                                                (Control 45% / bar -- only while control is slipping)
 * </pre>
 * Mid-screen: the keep-control prompt, the rampage timer, the HULK SMASH wind-up and the calm-down hold. Only drawn
 * for a player with the Gamma power.
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
	private static final int COLOR_CONTROL = 0xFF8FC8FF;
	private static final String[] KEY_NAMES = { "", "W", "A", "S", "D" };
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
		int totalW = 6 * BOX + 5 * GAP;
		int x0 = g.guiWidth() - MARGIN - totalW;
		int bottom = g.guiHeight() - MARGIN;

		// control (Hulk only, and only while it is slipping)
		boolean rampage = s.rampaging(now);
		if (s.hulk && (s.combat.control < 99.5f || rampage)) {
			int cBar = bottom - HAIRLINE;
			float c = rampage ? 0.0f : s.combat.control / 100.0f;
			g.drawString(mc.font, Component.translatable(rampage ? "hud.projecthero.hulk.control_lost" : "hud.projecthero.hulk.control",
					Math.round(c * 100.0f)), x0, cBar - 10, rampage ? 0xFFFF5544 : COLOR_CONTROL, false);
			g.fill(x0, cBar, x0 + totalW, cBar + HAIRLINE, COLOR_BG);
			g.fill(x0, cBar, x0 + Math.round(totalW * c), cBar + HAIRLINE, c < 0.3f ? 0xFFFF7755 : COLOR_CONTROL);
			bottom = cBar - 14;
		}

		// rage: label and the bar
		int rageBar = bottom - HAIRLINE;
		boolean exhausted = s.exhaustedUntil > now;
		int pct = Math.round(Math.max(0.0f, Math.min(1.0f, s.rage / HulkConfig.RAGE_MAX)) * 100.0f);
		// v0.13.15: Banner's bar is "Rage 62%", or -- while he is worn out -- "Exhausted 7s" with the bar running down the timer
		// v0.15.18: the Hulk's reads "Rage 62% - Angry" (his rage tier)
		int tier = HulkConfig.rageTier(s.rage);
		Component label = exhausted
				? Component.translatable("hud.projecthero.hulk.exhausted", (int) Math.ceil((s.exhaustedUntil - now) / 20.0))
				: s.hulk
						? Component.translatable("hud.projecthero.hulk.rage_tier", pct,
								Component.translatable(TIER_KEYS[tier]).withStyle(TIER_STYLES[tier]))
						: Component.translatable("hud.projecthero.hulk.rage", pct);
		g.drawString(mc.font, label, x0, rageBar - 11, exhausted ? COLOR_EXHAUSTED : COLOR_LABEL, false);
		// v0.13.19: the death-save dot is gone -- the save has no cooldown, so it was always lit
		int bx = x0;
		int bw = totalW;
		int fill = s.hulk ? (tier == HulkConfig.TIER_CALM ? COLOR_RAGE : COLOR_RAGE_HULK)
				: (s.rage >= HulkConfig.MANUAL_TRANSFORM_RAGE ? COLOR_RAGE_READY : COLOR_RAGE);
		float glowFrom = s.hulk ? HulkConfig.RAGE_TIER_ENRAGED : HulkConfig.BANNER_GLOW_RAGE;
		if (s.rage > glowFrom && !exhausted) {
			// Banner past 75 (the forced change is near) / an Enraged Hulk: the bar throbs, faster as it nears 100
			float heat = (s.rage - glowFrom) / (HulkConfig.RAGE_MAX - glowFrom);
			float t = (now + delta.getGameTimeDeltaPartialTick(false)) * (0.25f + 0.35f * heat);
			fill = (Math.sin(t) > 0.3) ? 0xFFB8FF7A : COLOR_RAGE_READY;
		}
		float barFrac = exhausted
				? Math.min(1.0f, (s.exhaustedUntil - now) / (float) HulkConfig.EXHAUSTED_TICKS)
				: pct / 100.0f;
		g.fill(bx, rageBar, bx + bw, rageBar + HAIRLINE, COLOR_BG);
		g.fill(bx, rageBar, bx + Math.round(bw * barFrac), rageBar + HAIRLINE, exhausted ? COLOR_EXHAUSTED : fill);
		if (!s.hulk && !exhausted) {
			int mark = bx + Math.round(bw * (HulkConfig.MANUAL_TRANSFORM_RAGE / HulkConfig.RAGE_MAX));
			g.fill(mark, rageBar - 1, mark + 1, rageBar + HAIRLINE + 1, 0xFFFFFFFF);
		} else if (s.hulk) {
			// v0.15.18: where the Angry and Enraged tiers start
			for (float at : new float[] { HulkConfig.RAGE_TIER_ANGRY, HulkConfig.RAGE_TIER_ENRAGED }) {
				int mark = bx + Math.round(bw * (at / HulkConfig.RAGE_MAX));
				g.fill(mark, rageBar - 1, mark + 1, rageBar + HAIRLINE + 1, 0xC0FFFFFF);
			}
		}

		if (s.hulk) {
			int formY = rageBar - 22;
			boolean gladiator = com.projecthero.mod.hulk.gladiator.GladiatorAbilities.active(player);
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
		}
		renderCentre(g, mc, player, s, now);
	}

	private static void renderKeys(GuiGraphics g, Minecraft mc, Player player, HulkState s, int x0, int y0, long now) {
		boolean expanded = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
				org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
		String[] names = { "power_punch", "ground_smash", "super_leap", "thunderclap", "grab", "charge" };
		boolean locked = s.rampaging(now) || s.combat.calming;
		for (int i = 0; i < 6; i++) {
			AbilitySlot slot = AbilitySlot.byNumber(i + 1);
			int x = x0 + i * (BOX + GAP);
			String id = HulkAbilityManager.abilityIdOf(slot);
			boolean active = (slot == AbilitySlot.SLOT_5 && s.combat.holding) || (slot == AbilitySlot.SLOT_6 && s.combat.chargeUntil > now)
					|| (slot == AbilitySlot.SLOT_4 && s.combat.smashChargeStart > 0L);
			g.fill(x, y0, x + BOX, y0 + BOX, 0xC0101A10);
			g.renderOutline(x, y0, BOX, BOX, active ? 0xFF7CFF4A : 0xFF2E6A3E);
			g.drawString(mc.font, String.valueOf(slot.defaultKey()), x + 2, y0 + 2, locked ? 0xFF6A7A6A : 0xFFD8F0DC, false);
			int cd = HulkAbilities.cooldownRemaining(player, id);
			int max = HulkAbilityManager.maxCooldown(id);
			if (locked) {
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, 0x90000000);
			} else if (cd > 0 && max > 0) {
				int h = (int) ((BOX - 2) * Math.min(1f, cd / (float) max));
				g.fill(x + 1, y0 + BOX - 1 - h, x + BOX - 1, y0 + BOX - 1, 0xB0000000);
				g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2 + 2, y0 + 10, 0xFFFFFFFF);
			}
			if (slot == AbilitySlot.SLOT_4) {
				// HULK SMASH (Shift+Z) has its own long cooldown: a thin strip along the bottom of the Z box
				int scd = HulkAbilities.cooldownRemaining(player, HulkAbilities.HULK_SMASH);
				int smax = HulkAbilityManager.maxCooldown(HulkAbilities.HULK_SMASH);
				float ready = smax <= 0 ? 1.0f : 1.0f - Math.min(1.0f, scd / (float) smax);
				g.fill(x + 1, y0 + BOX - 3, x + 1 + Math.round((BOX - 2) * ready), y0 + BOX - 1, scd > 0 ? 0xFF4F7F2A : 0xFFB8FF9A);
			}
			if (expanded) {
				Component name = Component.translatable("projecthero.hulk.ability." + names[i]);
				g.drawString(mc.font, name, x0 - 8 - mc.font.width(name), y0 + i * 10 - 52, 0xFFCFE8CF, true);
			}
		}
		float leap = HulkAbilities.leapCharge(player);
		if (leap > 0.0f) {
			int w = 6 * BOX + 5 * GAP;
			g.fill(x0, y0 - 6, x0 + w, y0 - 6 + HAIRLINE, COLOR_BG);
			g.fill(x0, y0 - 6, x0 + Math.round(w * leap), y0 - 6 + HAIRLINE, leap >= 1.0f ? 0xFFFFFFFF : COLOR_RAGE_READY);
		}
	}

	/**
	 * v0.15.3: the Gladiator kit's six boxes. Each shows the tap move's cooldown -- or, while Shift is held, the Shift
	 * move's -- and a thin strip along its bottom for the other one. X
	 * above the boxes, so the HUD itself stays six small boxes.
	 */
	private static void renderGladiatorKeys(GuiGraphics g, Minecraft mc, Player player, HulkState s, int x0, int y0, long now) {
		long win = mc.getWindow().getWindow();
		boolean alt = org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
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
		if (shift || alt) {
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

	/** Mid-screen: the keep-control prompt, the rampage, the HULK SMASH wind-up, the calm-down hold. */
	private static void renderCentre(GuiGraphics g, Minecraft mc, Player player, HulkState s, long now) {
		int cx = g.guiWidth() / 2;
		int y = g.guiHeight() / 2 - 62; // above the crosshair, clear of the action bar and the hotbar
		if (Hulk.changing(s, now)) {
			// v0.13.15: the unwilling change -- nothing to do but ride it out
			float f = Math.min(1.0f, (now - s.formChangedAt) / (float) HulkConfig.FORCED_CHANGE_TICKS);
			int w = 120;
			int col = (now / 6L) % 2L == 0L ? 0xFF7CFF4A : 0xFF3FAF3A;
			g.drawCenteredString(mc.font, Component.translatable("hud.projecthero.hulk.changing").withStyle(ChatFormatting.BOLD), cx, y, col);
			g.fill(cx - w / 2, y + 11, cx + w / 2, y + 13, COLOR_BG);
			g.fill(cx - w / 2, y + 11, cx - w / 2 + Math.round(w * f), y + 13, 0xFF7CFF4A);
			return;
		}
		float strain = Hulk.strainProgress(player);
		if (strain > 0.0f || (!s.hulk && s.combat.strainStart != 0L)) {
			// v0.15.18: holding H below 50 rage -- Banner strains the Hulk out
			int w = 120;
			int col = (now / 5L) % 2L == 0L ? 0xFF7CFF4A : 0xFFB8FF7A;
			g.drawCenteredString(mc.font, Component.translatable("hud.projecthero.hulk.strain", Math.round(strain * 100.0f))
					.withStyle(ChatFormatting.BOLD), cx, y, col);
			g.fill(cx - w / 2, y + 11, cx + w / 2, y + 13, COLOR_BG);
			g.fill(cx - w / 2, y + 11, cx - w / 2 + Math.round(w * strain), y + 13, 0xFF7CFF4A);
			return;
		}
		if (s.rampaging(now)) {
			int secs = (int) Math.ceil((s.combat.rampageUntil - now) / 20.0);
			int col = (now / 4L) % 2L == 0L ? 0xFFFF5544 : 0xFF7CFF4A;
			g.drawCenteredString(mc.font, Component.translatable("hud.projecthero.hulk.rampage", secs)
					.withStyle(ChatFormatting.BOLD), cx, y, col);
			return;
		}
		if (s.hulk && s.combat.promptKey > 0 && s.combat.promptKey < KEY_NAMES.length && now <= s.combat.promptUntil) {
			float left = Math.max(0.0f, (s.combat.promptUntil - now) / (float) Math.max(1, HulkConfig.control().promptWindowTicks));
			String key = keyName(mc, s.combat.promptKey);
			g.drawCenteredString(mc.font, Component.translatable("hud.projecthero.hulk.prompt", key).withStyle(ChatFormatting.BOLD),
					cx, y, 0xFFFFE070);
			int w = 80;
			g.fill(cx - w / 2, y + 11, cx + w / 2, y + 13, COLOR_BG);
			g.fill(cx - w / 2, y + 11, cx - w / 2 + Math.round(w * left), y + 13, 0xFFFFE070);
			y += 20;
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
		float calm = com.projecthero.mod.client.hulk.HulkClient.calmHoldProgress();
		if (calm > 0.02f && !s.combat.calming) {
			int w = 80;
			g.drawCenteredString(mc.font, Component.translatable("hud.projecthero.hulk.calm_hold"), cx, y, 0xFF9FC8E8);
			g.fill(cx - w / 2, y + 11, cx + w / 2, y + 13, COLOR_BG);
			g.fill(cx - w / 2, y + 11, cx - w / 2 + Math.round(w * calm), y + 13, 0xFF9FC8E8);
		}
	}

	/** The player's actual bound key for prompt 1-4 (forward / left / back / right). */
	private static String keyName(Minecraft mc, int prompt) {
		var mapping = switch (prompt) {
			case HulkControl.KEY_FORWARD -> mc.options.keyUp;
			case HulkControl.KEY_LEFT -> mc.options.keyLeft;
			case HulkControl.KEY_BACK -> mc.options.keyDown;
			default -> mc.options.keyRight;
		};
		return mapping.getTranslatedKeyMessage().getString().toUpperCase(java.util.Locale.ROOT);
	}
}
