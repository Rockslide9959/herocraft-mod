package com.projecthero.mod.client.ghostrider;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.ghostrider.GhostRider;
import com.projecthero.mod.ghostrider.GhostRiderAbilityManager;
import com.projecthero.mod.ghostrider.GhostRiderConfig;
import com.projecthero.mod.ghostrider.data.GhostRiderState;
import com.projecthero.mod.ghostrider.entity.HellCycleEntity;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.visual.MutationMeters;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * v0.17.3: the Ghost Rider HUD on the Minecraft-style look of the newest powers ({@link VanillaHud.Stack}, bottom-right):
 * <pre>
 *   bound till dawn                           red status tag after a cheated death
 *   GHOST RIDER
 *   Vengeance [Hairline ====---]  40          Vengeance (red)
 *   Hellfire  [Hairline ======-]  84          Hellfire (orange); both in the Hairline style they always had
 *   +----+----+----+----+----+----+           R G Z X C V: shown move's cooldown (sweep + seconds), the Shift move's as the
 *   | R  | G  | Z  | X  | C  | V  |           durability bar; lit while that key's move runs (blast, spin, bike, stare, rush,
 *   +----+----+----+----+----+----+           shield); yellow while Shift shows the Shift moves
 * </pre>
 * No H / N boxes: H is the change. Shown whenever you have the power, transformed or not (the human moves still use the keys).
 * Hold Left-Alt for the move names.
 */
public final class GhostRiderHud {
	private static final int NAME = 0xFFE2E2E2;
	private static final int HELLFIRE = 0xFFFF7A1A;
	private static final int VENGEANCE = 0xFFC81E1E;
	private static final int VENGEANCE_TEXT = 0xFFFF5050;

	private GhostRiderHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.options.hideGui || !GhostRider.hasPower(p)) {
			return;
		}
		GhostRiderState s = p.getAttachedOrElse(ModAttachments.GHOST_RIDER_STATE, null);
		if (s == null) {
			return;
		}
		var exp = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		long now = mc.level.getGameTime();
		boolean shift = p.isShiftKeyDown();
		VanillaHud.Stack st = VanillaHud.Stack.bottomRight(g, VanillaHud.ORDER.length).keys(VanillaHud.ORDER.length);
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			String[] ids = GhostRiderAbilityManager.idsOf(slot);
			if (ids.length == 0) {
				continue;
			}
			String shown = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			int cd = GhostRider.cooldownRemaining(p, shown);
			float frac = cd / (float) Math.max(1, GhostRiderAbilityManager.maxCooldown(shown));
			float otherFrac = other == null ? 0f
					: GhostRider.cooldownRemaining(p, other) / (float) Math.max(1, GhostRiderAbilityManager.maxCooldown(other));
			boolean active = (slot == AbilitySlot.SLOT_1 && s.blasting)
					|| (slot == AbilitySlot.SLOT_2 && s.spinUntil > now)
					|| (slot == AbilitySlot.SLOT_3 && p.getVehicle() instanceof HellCycleEntity)
					|| (slot == AbilitySlot.SLOT_4 && s.stareTarget != -1)
					|| (slot == AbilitySlot.SLOT_5 && s.rushing)
					|| (slot == AbilitySlot.SLOT_6 && s.shieldUntil > now);
			st.slot(i, VanillaHud.keyLabel(slot), true, shift, Math.min(1f, frac), VanillaHud.seconds(cd), Math.min(1f, otherFrac), active);
		}

		st.meter(Component.translatable("hud.projecthero.ghost_rider.hellfire"), s.hellfire / GhostRiderConfig.HELLFIRE_MAX, HELLFIRE,
				String.valueOf(Math.round(s.hellfire)), HELLFIRE, MutationMeters.Style.HAIRLINE);
		st.meter(Component.translatable("hud.projecthero.ghost_rider.vengeance"), s.vengeance / GhostRiderConfig.VENGEANCE_MAX, VENGEANCE,
				String.valueOf(Math.round(s.vengeance)), VENGEANCE_TEXT, MutationMeters.Style.HAIRLINE);
		st.title(Component.translatable("hud.projecthero.ghost_rider.title"), NAME);
		if (s.dayLockUntil > 0L) {
			st.tags(List.of(new VanillaHud.Tag(Component.translatable("hud.projecthero.ghost_rider.bound").getString(), VENGEANCE_TEXT)));
		}

		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : VanillaHud.ORDER) {
				String[] ids = GhostRiderAbilityManager.idsOf(slot);
				if (ids.length > 0) {
					rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.ghost_rider.ability." + ids[0]),
							ids.length > 1 ? Component.translatable("projecthero.ghost_rider.ability." + ids[1]) : null));
				}
			}
			st.moveList(rows);
		}
	}
}
