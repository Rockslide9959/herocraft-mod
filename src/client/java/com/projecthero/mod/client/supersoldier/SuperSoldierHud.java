package com.projecthero.mod.client.supersoldier;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.supersoldier.SuperSoldier;
import com.projecthero.mod.supersoldier.SuperSoldierAbilities;
import com.projecthero.mod.supersoldier.SuperSoldierAbilityManager;
import com.projecthero.mod.supersoldier.data.SuperSoldierState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * v0.17.3: the Super Soldier HUD on the Minecraft-style look of the newest powers ({@link VanillaHud.Stack}, bottom-right).
 * No bars (since v0.14.9), just the title and the keys:
 * <pre>
 *   SUPER SOLDIER
 *   +----+----+----+----+----+----+    R G Z X C V: shown move's cooldown (sweep + seconds), the Shift move's as the durability
 *   | R  | G  | Z  | X  | C  | V  |    bar; Z lit while Onslaught runs, V while Tactical Focus runs; yellow while Shift shows the
 *   +----+----+----+----+----+----+    Shift moves
 * </pre>
 * C (the shield throw) used to be a box stacked on V only while he held a shield or a throw was cooling down; it now sits in its
 * strip slot all the time and is greyed out when there is no shield in hand (its cooldown still shows, so the timer doesn't vanish
 * when the shield leaves his hand). No H / N boxes. Hold Left-Alt for the move names.
 */
public final class SuperSoldierHud {
	private static final int NAME = 0xFFE2E2E2;

	private SuperSoldierHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.options.hideGui || mc.level == null || !SuperSoldier.hasPower(p)) {
			return;
		}
		ExperimentalState ex = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (ex != null && !ex.activePower.isEmpty()) {
			return; // a selected mutation owns the keys (and draws its own HUD)
		}
		SuperSoldierState s = p.getAttachedOrElse(ModAttachments.SUPER_SOLDIER_STATE, null);
		if (s == null) {
			return;
		}
		long now = mc.level.getGameTime();
		boolean shield = SuperSoldierAbilities.shieldHand(p) != null;
		boolean shift = p.isShiftKeyDown();
		VanillaHud.Stack st = VanillaHud.Stack.bottomRight(g, VanillaHud.ORDER.length).keys(VanillaHud.ORDER.length);
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			String[] ids = SuperSoldierAbilityManager.idsOf(slot);
			if (ids.length == 0) {
				continue;
			}
			String shown = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			int cd = SuperSoldier.cooldownRemaining(p, shown);
			float frac = cd / (float) Math.max(1, SuperSoldierAbilityManager.maxCooldown(shown));
			float otherFrac = other == null ? 0f
					: SuperSoldier.cooldownRemaining(p, other) / (float) Math.max(1, SuperSoldierAbilityManager.maxCooldown(other));
			boolean active = (slot == AbilitySlot.SLOT_4 && s.onslaughtUntil > now) || (slot == AbilitySlot.SLOT_5 && s.focusUntil > now);
			boolean usable = slot != AbilitySlot.SLOT_6 || shield;
			st.slot(i, VanillaHud.keyLabel(slot), usable, shift, Math.min(1f, frac), VanillaHud.seconds(cd), Math.min(1f, otherFrac), active);
		}
		st.title(Component.translatable("hud.projecthero.super_soldier.title"), NAME);

		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : VanillaHud.ORDER) {
				String[] ids = SuperSoldierAbilityManager.idsOf(slot);
				if (ids.length > 0) {
					rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.super_soldier.ability." + ids[0]),
							ids.length > 1 ? Component.translatable("projecthero.super_soldier.ability." + ids[1]) : null));
				}
			}
			st.moveList(rows);
		}
	}
}
