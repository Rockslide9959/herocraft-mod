package com.projecthero.mod.client.saiyan;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.saiyan.Saiyan;
import com.projecthero.mod.saiyan.SaiyanAbilityManager;
import com.projecthero.mod.saiyan.SaiyanConfig;
import com.projecthero.mod.saiyan.SaiyanForm;
import com.projecthero.mod.saiyan.data.SaiyanState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * v0.17.3: the Saiyan HUD, bottom-right, drawn from the game's own pieces ({@link VanillaHud}) like Andy's and the Omnitrix's:
 * <pre>
 *   Super Saiyan 2                        the form, in its hair colour (red while exhausted)
 *   Mastery  [boss bar ====---]  SSJ3     time toward the next form (gone once all are unlocked)
 *   Ki       [boss bar =======-]  82      the Ki bar (pulses while charging)
 *   +----+----+----+----+----+----+       R G Z X C V: the shown move's cooldown as the item sweep, the Shift move's as a
 *   | R  | G  | Z  | X  | C  | V  |       durability bar; yellow while Shift shows the Shift moves; frame on X while flying
 *   +----+----+----+----+----+----+
 * </pre>
 * Hold Left-Alt for the move names (and H / Q).
 */
public final class SaiyanHud {
	private static final int MARGIN = 4;
	private static final int WIDTH = VanillaHud.width(6);
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };

	/** Set only by the client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	private SaiyanHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		SaiyanState s = Saiyan.peek(p);
		if (s == null || !s.hasPower) {
			return;
		}
		var exp = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		long now = mc.level.getGameTime();
		int x0 = g.guiWidth() - MARGIN - WIDTH;
		int bottom = g.guiHeight() - MARGIN;
		if (x0 < g.guiWidth() / 2 + 91 + 2) {
			bottom = g.guiHeight() - 41; // a narrow window: clear of the hotbar and food bar
		}
		int y0 = bottom - VanillaHud.HEIGHT;
		keys(g, p, s, x0, y0);

		SaiyanForm f = s.form();
		int by = y0 - 8;
		float ki = s.ki / SaiyanConfig.KI_MAX;
		int kiColour = s.charging && (now / 4) % 2 == 0 ? 0xFFFFFFFF : f == SaiyanForm.BASE ? 0xFFFFE066 : f.auraColor;
		VanillaHud.meter(g, x0, by, WIDTH, Component.translatable("hud.projecthero.saiyan.ki"), ki, kiColour & 0xFFFFFF,
				String.valueOf(Math.round(s.ki)), ki < 0.15f ? VanillaHud.RED : VanillaHud.WHITE);
		by -= 11;
		SaiyanForm next = Saiyan.nextLocked(p);
		if (next != null && next != SaiyanForm.INSTINCT) {
			float m = Saiyan.masteryProgress(p, next);
			VanillaHud.meter(g, x0, by, WIDTH, Component.translatable("hud.projecthero.saiyan.mastery"), m, 0xA060FF,
					Component.translatable(next.nameKey() + ".short").getString(), VanillaHud.GREY);
			by -= 11;
		}
		boolean exhausted = s.exhaustedUntil > now;
		Component title = exhausted ? Component.translatable("hud.projecthero.saiyan.exhausted")
				: Component.translatable(f.nameKey());
		int titleColour = exhausted ? VanillaHud.RED : f == SaiyanForm.BASE ? VanillaHud.WHITE : 0xFF000000 | (f.hairColor & 0xFFFFFF);
		g.drawString(mc.font, mc.font.plainSubstrByWidth(title.getString(), WIDTH), x0, by - 2, titleColour, true);

		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : SLOTS) {
				String[] ids = SaiyanAbilityManager.idsOf(slot);
				if (ids.length > 0) {
					rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.saiyan.ability." + ids[0]),
							ids.length > 1 ? Component.translatable("projecthero.saiyan.ability." + ids[1]) : null));
				}
			}
			rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(AbilitySlot.SLOT_7), Component.translatable("projecthero.saiyan.ability.form_wheel"), null));
			rows.add(MoveListOverlay.Row.of("2x Jump", Component.translatable("projecthero.saiyan.ability.flight"), null));
			MoveListOverlay.draw(g, x0 + WIDTH, by - 6, rows);
		}
	}

	private static void keys(GuiGraphics g, LocalPlayer p, SaiyanState s, int x0, int y0) {
		boolean shift = forceShift || p.isShiftKeyDown();
		VanillaHud.strip(g, x0, y0, SLOTS.length);
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			String[] ids = SaiyanAbilityManager.idsOf(slot);
			if (ids.length == 0) {
				continue;
			}
			String shown = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			int cd = SaiyanAbilityManager.cooldownRemaining(p, shown);
			float frac = Math.min(1f, cd / (float) Math.max(1, SaiyanAbilityManager.maxCooldown(shown)));
			float otherFrac = other == null ? 0f
					: Math.min(1f, SaiyanAbilityManager.cooldownRemaining(p, other) / (float) Math.max(1, SaiyanAbilityManager.maxCooldown(other)));
			boolean active = slot == AbilitySlot.SLOT_5 && s.charging;
			VanillaHud.slot(g, x0, y0, i, keyLabel(slot), true, shift, frac, cd > 0 ? (cd + 19) / 20 : 0, otherFrac, active);
		}
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}
}
