package com.projecthero.mod.client.kryptonian;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.kryptonian.Kryptonian;
import com.projecthero.mod.kryptonian.KryptonianAbilityManager;
import com.projecthero.mod.kryptonian.KryptonianConfig;
import com.projecthero.mod.kryptonian.data.KryptonianState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.17.3: the Kryptonian HUD on the Minecraft-style look of the newest powers ({@link VanillaHud.Stack}, bottom-right):
 * <pre>
 *   KRYPTONIAN                               the title (green while weakened by Kryptonite, grey while burnt out)
 *   Burnout [Hairline =====---]  12s         only while the Solar Flare burn-out runs
 *   Flare   [Hairline =======-]  70%         only while the Solar Flare charges
 *   Solar   [Hairline ========]  84          Solar Energy (out of 100), the Hairline style it always had
 *   +----+----+----+----+----+----+          R G Z X C V: the shown move's cooldown (sweep + seconds), the Shift move's as the
 *   | R  | G  | Z  | X  | C  | V  |          durability bar; lit while Heat Vision / Freeze Breath (or the Flare charge) / X-Ray
 *   +----+----+----+----+----+----+          is on; greyed while weakened or burnt out; yellow while Shift shows the Shift moves
 * </pre>
 * No H / N boxes: H is the power wheel and N does nothing for him. Hold Left-Alt for the move names.
 */
public final class KryptonianHud {
	private static final int SOLAR = 0xFFFFC83C;
	private static final int SOLAR_KRYPTONITE = 0xFF5CFF4A;
	private static final int FLARE = 0xFFFFF2C0;
	private static final int BURNT_OUT = 0xFF7A7A7A;
	private static final int NAME = 0xFFE2E2E2;

	private KryptonianHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.options.hideGui || !Kryptonian.hasPower(p)) {
			return;
		}
		KryptonianState s = p.getAttachedOrElse(ModAttachments.KRYPTONIAN_STATE, null);
		if (s == null) {
			return;
		}
		// a selected mutation owns the slots (and draws its own HUD) -- nothing to show for the Kryptonian keys then
		var exp = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		long now = mc.level.getGameTime();
		boolean weak = s.weakened;
		boolean burnt = s.depoweredUntil > now;
		boolean shift = p.isShiftKeyDown();
		VanillaHud.Stack st = VanillaHud.Stack.bottomRight(g, VanillaHud.ORDER.length).keys(VanillaHud.ORDER.length);
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			String[] ids = KryptonianAbilityManager.idsOf(slot);
			if (ids.length == 0) {
				continue;
			}
			String shown = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			int cd = Kryptonian.cooldownRemaining(p, shown);
			float frac = cd / (float) Math.max(1, KryptonianAbilityManager.maxCooldown(shown));
			float otherFrac = other == null ? 0f
					: Kryptonian.cooldownRemaining(p, other) / (float) Math.max(1, KryptonianAbilityManager.maxCooldown(other));
			boolean active = (slot == AbilitySlot.SLOT_2 && s.heatVision)
					|| (slot == AbilitySlot.SLOT_4 && (s.breathing || s.flareChargeStart > 0L))
					|| (slot == AbilitySlot.SLOT_5 && s.xray);
			st.slot(i, VanillaHud.keyLabel(slot), !(weak || burnt), shift, Math.min(1f, frac), VanillaHud.seconds(cd), Math.min(1f, otherFrac), active);
		}

		// Hairline meters above the keys, bottom-up: Solar, then the Flare charge and the burn-out while they run
		int solarColour = weak ? SOLAR_KRYPTONITE : SOLAR;
		st.meter(Component.translatable("hud.projecthero.kryptonian.solar"), s.solar / KryptonianConfig.SOLAR_MAX, solarColour,
				String.valueOf(Math.round(s.solar)), solarColour, MutationMeters.Style.HAIRLINE);
		if (s.flareChargeStart > 0L) {
			float f = Mth.clamp((now - s.flareChargeStart) / (float) KryptonianConfig.FLARE_CHARGE_TICKS, 0f, 1f);
			st.meter(Component.translatable("hud.projecthero.kryptonian.flare"), f, FLARE, Math.round(f * 100) + "%", FLARE,
					MutationMeters.Style.HAIRLINE);
		}
		if (burnt) {
			long left = s.depoweredUntil - now;
			st.meter(Component.translatable("hud.projecthero.kryptonian.burnout"), left / (float) KryptonianConfig.FLARE_DEPOWER_TICKS, BURNT_OUT,
					VanillaHud.seconds((int) left) + "s", VanillaHud.GREY, MutationMeters.Style.HAIRLINE);
		}
		st.title(Component.translatable("hud.projecthero.kryptonian.title"), weak ? SOLAR_KRYPTONITE : burnt ? BURNT_OUT : NAME);

		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : VanillaHud.ORDER) {
				String[] ids = KryptonianAbilityManager.idsOf(slot);
				if (ids.length > 0) {
					rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.kryptonian.ability." + ids[0]),
							ids.length > 1 ? Component.translatable("projecthero.kryptonian.ability." + ids[1]) : null));
				}
			}
			st.moveList(rows);
		}
	}
}
