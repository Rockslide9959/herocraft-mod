package com.projecthero.mod.client.stand;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.stand.StandAbilityManager;
import com.projecthero.mod.stand.StandConfig;
import com.projecthero.mod.stand.Stands;
import com.projecthero.mod.stand.data.StandState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * v0.17.2 (user: "give stand powers the new Minecraft style hud that the omnitrix has"): the Stand HUD, bottom-right, drawn from the
 * game's own pieces ({@link VanillaHud}):
 * <pre>
 *   Stamina [boss bar ========-----] 87    a purple boss bar and shadowed text
 *   +----+----+----+----+----+----+        the six keys, a short hotbar: R G Z X C V, yellow while Shift shows the Shift moves;
 *   | R  | G  | Z  | X  | C  | V  |        the shown move's cooldown as the item-cooldown sweep and a stack count, the other
 *   +----+----+----+----+----+----+        move's as a durability bar, the selection frame on a move that is running
 * </pre>
 * No H / N slots: H summons, N poses. Hold Left-Alt for the move names.
 */
public final class StandHud {
	private static final int MARGIN = 4;
	private static final int WIDTH = VanillaHud.width(6);
	private static final int STAMINA = 0x9A5CFF;

	/** Set only by the client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	/** Boxes left to right: R G Z X C V (slots 1, 2, 4, 3, 6, 5). */
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };

	private StandHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.options.hideGui || !Stands.hasPower(p)) {
			return;
		}
		StandState s = Stands.peek(p);
		if (s == null) {
			return;
		}
		var exp = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		long now = mc.level.getGameTime();
		int x0 = g.guiWidth() - MARGIN - WIDTH;
		int bottom = g.guiHeight() - MARGIN;
		// on a narrow window it would sit on the right end of the hotbar (and the food bar above it): lift it clear
		if (x0 < g.guiWidth() / 2 + 91 + 2) {
			bottom = g.guiHeight() - 41;
		}
		int y0 = bottom - VanillaHud.HEIGHT;
		keys(g, p, s, now, x0, y0);
		int by = y0 - 8;
		VanillaHud.meter(g, x0, by, WIDTH, Component.translatable("hud.projecthero.stand.stamina"), s.stamina / StandConfig.STAMINA_MAX, STAMINA,
				String.valueOf(Math.round(s.stamina)), VanillaHud.WHITE);
		int top = by - 4;
		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : SLOTS) {
				String[] ids = StandAbilityManager.idsOf(slot);
				if (ids.length > 0) {
					rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.stand.move." + ids[0]),
							ids.length > 1 ? Component.translatable("projecthero.stand.move." + ids[1]) : null));
				}
			}
			MoveListOverlay.draw(g, x0 + WIDTH, top, rows);
		}
	}

	private static void keys(GuiGraphics g, LocalPlayer p, StandState s, long now, int x0, int y0) {
		boolean shift = forceShift || p.isShiftKeyDown();
		VanillaHud.strip(g, x0, y0, SLOTS.length);
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			String[] ids = StandAbilityManager.idsOf(slot);
			String shown = ids[shift ? 1 : 0], other = ids[shift ? 0 : 1];
			int cd = Stands.cooldownRemaining(p, shown);
			float frac = Math.min(1f, cd / (float) Math.max(1, StandAbilityManager.maxCooldown(shown)));
			float otherFrac = Math.min(1f, Stands.cooldownRemaining(p, other) / (float) Math.max(1, StandAbilityManager.maxCooldown(other)));
			boolean active = (slot == AbilitySlot.SLOT_1 && s.rushing)
					|| (slot == AbilitySlot.SLOT_2 && (s.grabbedEntity != -1 || s.grabbedBlock != 0))
					|| (slot == AbilitySlot.SLOT_3 && (s.leapRebounds >= 0 || s.dashUntil > now))
					|| (slot == AbilitySlot.SLOT_4 && s.chargeStart != 0L)
					|| (slot == AbilitySlot.SLOT_5 && (s.guarding || s.sightUntil > now))
					|| (slot == AbilitySlot.SLOT_6 && s.inhaleUntil > now);
			VanillaHud.slot(g, x0, y0, i, keyLabel(slot), true, shift, frac, cd > 0 ? (cd + 19) / 20 : 0, otherFrac, active);
		}
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}
}
