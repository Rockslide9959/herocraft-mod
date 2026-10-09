package com.projecthero.mod.client.gui;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.punisher.PunisherIntelClient;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.punisher.Punisher;
import com.projecthero.mod.punisher.PunisherAbilityManager;
import com.projecthero.mod.punisher.PunisherConfig;
import com.projecthero.mod.punisher.VigilanteTraining;
import com.projecthero.mod.punisher.ability.PunisherWarzone;
import com.projecthero.mod.punisher.data.PunisherState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The Punisher HUD, bottom-right like every other power's ability row. v0.15.18 kit:
 * <pre>
 *   (Alt held: each key's move, and its Shift move under it, left of the row)
 *   WARZONE  ▬▬▬▬▬▬▬▬▬▬▬▬               while Shift+Z is held: the 5 s call filling up
 *   [R ] [G ] [Z ] [X ] [C ] [V ]           each box carries its key letter in its top-left corner (the key actually
 *                                           bound, like every other hero HUD); the plain move's cooldown shades the
 *                                           box with the seconds bottom-right; a thin red bar along each box's bottom
 *                                           is its Shift move's cooldown (bright once ready)
 *   MARKED 23s                              while a Target Designation mark is live
 *   (training objectives, if any)
 * </pre>
 * N (the Tactical Satchel) is never shown -- no H / N boxes on the HUD. V belongs to the weapon abilities (a separate
 * change): its box is drawn with no cooldown of its own here. The row's geometry is unchanged from earlier versions
 * because the Agent Venom panel ({@code SymbioteHud}) stacks itself just above it.
 */
public final class PunisherHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int COLOR_BOX_BG = 0xC0121212;
	private static final int COLOR_BORDER = 0xFF3A3A3A;
	private static final int COLOR_COOLDOWN = 0xB0000000;
	private static final int COLOR_KEY = 0xFFD8D8D8;
	private static final int COLOR_NAME = 0xFFE0E0E0;
	private static final int COLOR_SHIFT_NAME = 0xFFA0A0A0;
	private static final int SHIFT_BAR_TRACK = 0xC0200808;
	private static final int SHIFT_BAR_COOLING = 0xFF7A2222;
	private static final int SHIFT_BAR_READY = 0xFFE04848;
	private static final int MARK_RED = 0xFFFF4040;

	/** The boxes, left to right, in the controls screen's "Ability 1..6" order: R, G, Z, X, C, V. */
	private static final AbilitySlot[] ORDER = {
			AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3, AbilitySlot.SLOT_6, AbilitySlot.SLOT_5
	};
	/** Alt names per box: the plain move, then the Shift move ("" = none). */
	private static final String[][] NAMES = {
			{ "mark", "threat" }, { "strike", "kick" }, { "grenade", "warzone" }, { "roll", "advance" },
			{ "smoke", "flashbang" }, { "weapon", "" }
	};

	/** Client mirror of the server's Warzone call: game time Shift+Z went down, or -1. */
	private static long warzoneChargeStart = -1L;
	private static boolean zWasDown;

	private PunisherHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || mc.level == null) {
			return;
		}
		PunisherState s = mc.player.getAttachedOrElse(ModAttachments.PUNISHER_STATE, null);
		if (s == null) {
			return;
		}
		// Mid Vigilante Training (no power yet): show only the objectives block, top-left, so the
		// player can watch their progress without opening the info screen.
		if (!s.hasPower && s.trainingActive) {
			int ty = 6;
			g.drawString(mc.font, Component.translatable("hud.projecthero.punisher.training")
					.withStyle(ChatFormatting.GOLD), 6, ty, 0xFFE0C060, true);
			g.drawString(mc.font, VigilanteTraining.objectivesSummary(s).withStyle(ChatFormatting.GRAY),
					6, ty + 10, 0xFFBBBBBB, true);
			return;
		}
		if (!s.hasPower) {
			return;
		}
		LocalPlayer player = mc.player;
		long now = mc.level.getGameTime();

		int totalW = BOX * 6 + GAP * 5;
		int x0 = g.guiWidth() - MARGIN - totalW;
		int y0 = g.guiHeight() - BOX - MARGIN - 26;

		boolean expanded = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
				org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

		for (int i = 0; i < ORDER.length; i++) {
			AbilitySlot slot = ORDER[i];
			int x = x0 + i * (BOX + GAP);
			g.fill(x, y0, x + BOX, y0 + BOX, COLOR_BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, COLOR_BORDER);
			if (slot == AbilitySlot.SLOT_5) {
				drawWeaponAbility(g, mc, x, y0); // v0.15.18: V = Weapon Ability (per held gun)
				drawKey(g, mc, slot, x, y0);
				continue;
			}
			String plain = PunisherAbilityManager.abilityIdOf(slot);
			int cd = plain.isEmpty() ? 0 : Punisher.cooldownRemaining(player, plain);
			int max = PunisherAbilityManager.cooldownTicksOf(plain);
			if (cd > 0 && max > 0) {
				int h = (int) ((BOX - 2) * Math.min(1f, cd / (float) max));
				g.fill(x + 1, y0 + BOX - 1 - h, x + BOX - 1, y0 + BOX - 1, COLOR_COOLDOWN);
				drawSeconds(g, mc, cd, x, y0);
			}
			// the Shift move's own cooldown: a thin bar along the bottom that refills, bright once ready
			String shift = PunisherAbilityManager.shiftAbilityIdOf(slot);
			if (!shift.isEmpty()) {
				int scd = Punisher.cooldownRemaining(player, shift);
				int smax = Math.max(1, PunisherAbilityManager.cooldownTicksOf(shift));
				float ready = scd <= 0 ? 1f : 1f - Math.min(1f, scd / (float) smax);
				g.fill(x + 1, y0 + BOX - 3, x + BOX - 1, y0 + BOX - 1, SHIFT_BAR_TRACK);
				g.fill(x + 1, y0 + BOX - 3, x + 1 + Math.round((BOX - 2) * ready), y0 + BOX - 1,
						ready >= 1f ? SHIFT_BAR_READY : SHIFT_BAR_COOLING);
			}
			drawKey(g, mc, slot, x, y0);
		}

		if (expanded) {
			renderNames(g, mc, x0, y0 + BOX);
		}

		renderWarzoneCall(g, mc, player, now, x0, y0 - 22, totalW);

		int line = y0 + BOX + 2;
		int markLeft = PunisherIntelClient.markTicksLeft(now);
		if (markLeft > 0) {
			g.drawString(mc.font, Component.translatable("hud.projecthero.punisher.marked", (markLeft + 19) / 20),
					x0, line, MARK_RED, true);
			line += 10;
		}
		if (s.trainingActive) {
			g.drawString(mc.font, VigilanteTraining.objectivesSummary(s).withStyle(ChatFormatting.GRAY),
					x0, line, 0xFFAAAAAA, true);
		}
	}

	/**
	 * v0.15.18: the V box = the held gun's Weapon Ability -- its plain-V cooldown like any other box, and its Shift+V
	 * cooldown as a thin bar along the bottom (refilling while it cools down, bright once ready), like the Green
	 * Lantern / Nova HUDs. Nothing drawn inside while no gun is held.
	 */
	private static void drawWeaponAbility(GuiGraphics g, Minecraft mc, int x, int y) {
		int[] tap = com.projecthero.mod.punisher.ability.PunisherWeaponAbilities.hudCooldown(mc.player, false);
		int[] alt = com.projecthero.mod.punisher.ability.PunisherWeaponAbilities.hudCooldown(mc.player, true);
		if (tap == null || alt == null) {
			return;
		}
		if (tap[0] > 0 && tap[1] > 0) {
			int h = (int) ((BOX - 2) * Math.min(1f, tap[0] / (float) tap[1]));
			g.fill(x + 1, y + BOX - 1 - h, x + BOX - 1, y + BOX - 1, COLOR_COOLDOWN);
			drawSeconds(g, mc, tap[0], x, y);
		}
		float ready = alt[1] <= 0 ? 1f : 1f - Math.min(1f, alt[0] / (float) alt[1]);
		g.fill(x + 1, y + BOX - 3, x + BOX - 1, y + BOX - 1, 0xC0301008);
		g.fill(x + 1, y + BOX - 3, x + 1 + Math.round((BOX - 2) * ready), y + BOX - 1,
				ready >= 1f ? 0xFFFF6A3D : 0xFF8A3A1E);
	}

	/**
	 * The box's key letter, top-left inside the box (playtest v0.15.18: it used to sit above the box, where it read as
	 * part of whatever was drawn over the row -- every other hero HUD puts it in the box). Drawn last, so a cooldown
	 * shade never covers it.
	 */
	private static void drawKey(GuiGraphics g, Minecraft mc, AbilitySlot slot, int x, int y) {
		g.drawString(mc.font, keyLabel(slot), x + 2, y + 2, COLOR_KEY, true);
	}

	/** A running cooldown's seconds, bottom-right inside the box, clear of the key letter. */
	private static void drawSeconds(GuiGraphics g, Minecraft mc, int ticks, int x, int y) {
		String secs = String.valueOf((ticks + 19) / 20);
		g.drawString(mc.font, secs, x + BOX - 2 - mc.font.width(secs), y + BOX - 12, 0xFFFFFFFF, true);
	}

	/** The key actually bound to {@code slot} (tracks rebinds), at most two characters, upper case. */
	private static String keyLabel(AbilitySlot slot) {
		String k = ModKeyBindings.ABILITY_SLOTS[slot.index()].getTranslatedKeyMessage().getString().toUpperCase(java.util.Locale.ROOT);
		return k.length() > 2 ? k.substring(0, 2) : k;
	}

	/** Alt held: "R  Target Designation" with "Shift  Threat Assessment" under it, per key, right-aligned left of the row. */
	private static void renderNames(GuiGraphics g, Minecraft mc, int x0, int bottom) {
		int y = bottom - 10;
		for (int i = NAMES.length - 1; i >= 0; i--) {
			String key = keyLabel(ORDER[i]);
			if (!NAMES[i][1].isEmpty()) {
				Component sh = Component.translatable("hud.projecthero.punisher.shift_key",
						key, Component.translatable("hud.projecthero.punisher.name." + NAMES[i][1]));
				g.drawString(mc.font, sh, x0 - 8 - mc.font.width(sh), y, COLOR_SHIFT_NAME, true);
				y -= 10;
			}
			Component name = Component.literal(key + "  ").append(
					Component.translatable("hud.projecthero.punisher.name." + NAMES[i][0]));
			g.drawString(mc.font, name, x0 - 8 - mc.font.width(name), y, COLOR_NAME, true);
			y -= 11;
		}
	}

	/**
	 * Warzone (hold Shift+Z for 5 s): mirrors the server's rule on this client -- the call starts when Z goes down with
	 * Shift held and Warzone ready, and is off the moment either key is let go -- and draws it filling up.
	 */
	private static void renderWarzoneCall(GuiGraphics g, Minecraft mc, LocalPlayer player, long now, int x0, int y, int w) {
		boolean zDown = mc.screen == null && ModKeyBindings.ABILITY_4.isDown();
		boolean shift = player.isShiftKeyDown();
		if (zDown && !zWasDown) {
			boolean eligible = shift && Punisher.cooldownRemaining(player, PunisherWarzone.ABILITY) <= 0
					&& !com.projecthero.mod.symbiote.SymbioteAgentVenomAbilities.agentVenom(player);
			warzoneChargeStart = eligible ? now : -1L;
		}
		if (!zDown || !shift) {
			warzoneChargeStart = -1L;
		}
		zWasDown = zDown;
		if (warzoneChargeStart < 0L) {
			return;
		}
		float frac = Math.min(1f, (now - warzoneChargeStart) / (float) PunisherConfig.WARZONE_CHARGE_TICKS);
		if (frac >= 1f && now - warzoneChargeStart > PunisherConfig.WARZONE_CHARGE_TICKS + 20) {
			return; // called in -- the bar has done its job
		}
		Component label = Component.translatable(frac >= 1f ? "hud.projecthero.punisher.warzone_inbound"
				: "hud.projecthero.punisher.warzone_calling");
		g.drawString(mc.font, label, x0, y, MARK_RED, true);
		int barY = y + 10;
		g.fill(x0, barY, x0 + w, barY + 3, 0xAA200808);
		g.fill(x0, barY, x0 + Math.round(w * frac), barY + 3, frac >= 1f ? 0xFFFF5050 : 0xFFC03030);
	}
}
