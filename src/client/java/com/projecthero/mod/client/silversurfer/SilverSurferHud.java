package com.projecthero.mod.client.silversurfer;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.silversurfer.SilverSurfer;
import com.projecthero.mod.silversurfer.SilverSurferAbilityManager;
import com.projecthero.mod.silversurfer.SilverSurferConfig;
import com.projecthero.mod.silversurfer.data.SilverSurferState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.17.3: the Silver Surfer HUD on the Minecraft-style look of the newest powers ({@link VanillaHud.Stack}, bottom-right):
 * <pre>
 *   SURFING                                   status tag (LIGHT-SPEED while it runs)
 *   SILVER SURFER
 *   Light-Speed [Hairline ===--]  4s          only while Light-Speed runs
 *   Barrier     [Hairline ====-]  30          only while the barrier is up
 *   Beam        [Hairline =====]  60%         the beam's time left, only once the beam is past its hold-up
 *   Cosmic      [Hairline =====]  84          the Power Cosmic (silver-blue), in the Hairline style it always had
 *   +----+----+----+----+----+----+           R G Z X C V: shown move's cooldown (sweep + seconds), the Shift move's as the
 *   | R  | G  | Z  | X  | C  | V  |           durability bar (it used to be the strip under each box); lit while the move runs
 *   +----+----+----+----+----+----+
 * </pre>
 * No H / N boxes. Hold Left-Alt for the move names.
 */
public final class SilverSurferHud {
	private static final int NAME = 0xFFE8EEF8;
	private static final int COSMIC = 0xFFB8D4FF;
	private static final int BEAM = 0xFFF2F8FF;
	private static final int BARRIER = 0xFF9FC0FF;
	private static final int LIGHT = 0xFFFFFFFF;

	private SilverSurferHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.options.hideGui || !SilverSurfer.hasPower(p)) {
			return;
		}
		SilverSurferState s = SilverSurfer.peek(p);
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
			String[] ids = SilverSurferAbilityManager.idsOf(slot);
			if (ids.length == 0) {
				continue;
			}
			String shown = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			int cd = SilverSurfer.cooldownRemaining(p, shown);
			float frac = cd / (float) Math.max(1, SilverSurferAbilityManager.maxCooldown(shown));
			float otherFrac = other == null ? 0f
					: SilverSurfer.cooldownRemaining(p, other) / (float) Math.max(1, SilverSurferAbilityManager.maxCooldown(other));
			boolean active = (slot == AbilitySlot.SLOT_1 && s.beamStart != 0L)
					|| (slot == AbilitySlot.SLOT_2 && s.ramUntil > now)
					|| (slot == AbilitySlot.SLOT_3 && s.lightSpeedUntil > now)
					|| (slot == AbilitySlot.SLOT_4 && (s.barrierUntil > now || s.phaseUntil > now))
					|| (slot == AbilitySlot.SLOT_5 && s.awareness);
			st.slot(i, VanillaHud.keyLabel(slot), true, shift, Math.min(1f, frac), VanillaHud.seconds(cd), Math.min(1f, otherFrac), active);
		}

		st.meter(Component.translatable("hud.projecthero.silver_surfer.cosmic"), s.cosmic / SilverSurferConfig.COSMIC_MAX, COSMIC,
				String.valueOf(Math.round(s.cosmic)), COSMIC, MutationMeters.Style.HAIRLINE);
		if (s.beamStart != 0L && now - s.beamStart >= SilverSurferConfig.BEAM_HOLD_TICKS) {
			float left = Mth.clamp(1f - (now - s.beamStart - SilverSurferConfig.BEAM_HOLD_TICKS) / (float) SilverSurferConfig.BEAM_MAX_TICKS, 0f, 1f);
			st.meter(Component.translatable("hud.projecthero.silver_surfer.beam"), left, BEAM, Math.round(left * 100) + "%", BEAM,
					MutationMeters.Style.HAIRLINE);
		}
		if (s.barrierUntil > now && s.barrierHp > 0f) {
			st.meter(Component.translatable("hud.projecthero.silver_surfer.barrier"), s.barrierHp / SilverSurferConfig.BARRIER_HP, BARRIER,
					String.valueOf(Math.round(s.barrierHp)), BARRIER, MutationMeters.Style.HAIRLINE);
		}
		boolean light = s.lightSpeedUntil > now;
		if (light) {
			long left = s.lightSpeedUntil - now;
			st.meter(Component.translatable("hud.projecthero.silver_surfer.light"), left / (float) SilverSurferConfig.LIGHT_SPEED_TICKS, LIGHT,
					VanillaHud.seconds((int) left) + "s", LIGHT, MutationMeters.Style.HAIRLINE);
		}
		st.title(Component.translatable("hud.projecthero.silver_surfer.title"), NAME);
		if (light || s.surfing) {
			st.tags(List.of(new VanillaHud.Tag(
					Component.translatable(light ? "hud.projecthero.silver_surfer.light_speed" : "hud.projecthero.silver_surfer.surfing").getString(),
					light ? LIGHT : COSMIC)));
		}

		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : VanillaHud.ORDER) {
				String[] ids = SilverSurferAbilityManager.idsOf(slot);
				if (ids.length > 0) {
					rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.silver_surfer.ability." + ids[0]),
							ids.length > 1 ? Component.translatable("projecthero.silver_surfer.ability." + ids[1]) : null));
				}
			}
			st.moveList(rows);
		}
	}
}
