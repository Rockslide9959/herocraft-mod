package com.projecthero.mod.client.cyborg;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.cyborg.Cyborg;
import com.projecthero.mod.cyborg.CyborgAbilityManager;
import com.projecthero.mod.cyborg.CyborgConfig;
import com.projecthero.mod.cyborg.data.CyborgState;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.visual.MutationMeters;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * v0.17.3: the Cyborg HUD on the Minecraft-style look of the newest powers ({@link VanillaHud.Stack}, bottom-right):
 * <pre>
 *   CYBORG
 *   Matrix [Hairline =====---]  40      the Defense Matrix's pool, only while it is up
 *   Cannon [Hairline =======-]  70%     the Sonic Cannon charge, only while it charges
 *   Core   [Hairline ========]  84      the Power Core (blue), in the Hairline style it always had
 *   +----+----+----+----+----+----+     R G Z X C V: shown move's cooldown (sweep + seconds), the Shift move's as the durability
 *   | R  | G  | Z  | X  | C  | V  |     bar; lit while the cannon charges / the saw spins / the jet dash / the scan / the
 *   +----+----+----+----+----+----+     matrix runs; yellow while Shift shows the Shift moves
 * </pre>
 * While the Scan runs, the creature under the crosshair gets a readout under the crosshair (unchanged): name, health, armour.
 * Shown only in combat form. Hold Left-Alt for the move names.
 */
public final class CyborgHud {
	private static final int NAME = 0xFFE2E2E2;
	private static final int CORE = 0xFF4FB8FF;
	private static final int CHARGE = 0xFFE8F8FF;
	private static final int MATRIX = 0xFF8FD8FF;
	private static final int SCAN = 0xFF5CC8FF;

	private CyborgHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.options.hideGui || !Cyborg.combat(p)) {
			return;
		}
		CyborgState s = p.getAttachedOrElse(ModAttachments.CYBORG_STATE, null);
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
			String[] ids = CyborgAbilityManager.idsOf(slot);
			if (ids.length == 0) {
				continue;
			}
			String shown = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			int cd = Cyborg.cooldownRemaining(p, shown);
			float frac = cd / (float) Math.max(1, CyborgAbilityManager.maxCooldown(shown));
			float otherFrac = other == null ? 0f : Cyborg.cooldownRemaining(p, other) / (float) Math.max(1, CyborgAbilityManager.maxCooldown(other));
			boolean active = (slot == AbilitySlot.SLOT_1 && s.chargeStart != 0L)
					|| (slot == AbilitySlot.SLOT_2 && s.fx.sawUntil > now)
					|| (slot == AbilitySlot.SLOT_3 && s.fx.jetDashUntil > now)
					|| (slot == AbilitySlot.SLOT_4 && s.scanUntil > now)
					|| (slot == AbilitySlot.SLOT_6 && s.shieldUntil > now && s.shieldHp > 0f);
			st.slot(i, VanillaHud.keyLabel(slot), true, shift, Math.min(1f, frac), VanillaHud.seconds(cd), Math.min(1f, otherFrac), active);
		}

		st.meter(Component.translatable("hud.projecthero.cyborg.core"), s.core / CyborgConfig.CORE_MAX, CORE, String.valueOf(Math.round(s.core)), CORE,
				MutationMeters.Style.HAIRLINE);
		if (s.chargeStart != 0L) {
			float f = Mth.clamp((now - s.chargeStart) / (float) CyborgConfig.CANNON_CHARGE_TICKS, 0f, 1f);
			st.meter(Component.translatable("hud.projecthero.cyborg.charge"), f, CHARGE, Math.round(f * 100) + "%", CHARGE, MutationMeters.Style.HAIRLINE);
		}
		if (s.shieldUntil > now && s.shieldHp > 0f) {
			st.meter(Component.translatable("hud.projecthero.cyborg.matrix"), s.shieldHp / CyborgConfig.MATRIX_HP, MATRIX,
					String.valueOf(Math.round(s.shieldHp)), MATRIX, MutationMeters.Style.HAIRLINE);
		}
		st.title(Component.translatable("hud.projecthero.cyborg.title"), NAME);

		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : VanillaHud.ORDER) {
				String[] ids = CyborgAbilityManager.idsOf(slot);
				if (ids.length > 0) {
					rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.cyborg.ability." + ids[0]),
							ids.length > 1 ? Component.translatable("projecthero.cyborg.ability." + ids[1]) : null));
				}
			}
			st.moveList(rows);
		}

		// the Scan readout under the crosshair (kept as it was)
		if (s.scanUntil > now && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY
				&& ((EntityHitResult) mc.hitResult).getEntity() instanceof LivingEntity t) {
			int cx = g.guiWidth() / 2;
			int cy = g.guiHeight() / 2 + 12;
			Component name = t.getDisplayName();
			String stats = String.format(java.util.Locale.ROOT, "%.0f / %.0f HP   %d armour", t.getHealth(), t.getMaxHealth(), t.getArmorValue());
			int w = Math.max(mc.font.width(name), mc.font.width(stats)) + 8;
			g.fill(cx - w / 2, cy, cx + w / 2, cy + 22, 0xA0060A10);
			g.renderOutline(cx - w / 2, cy, w, 22, SCAN);
			g.drawCenteredString(mc.font, name, cx, cy + 2, SCAN);
			g.drawCenteredString(mc.font, stats, cx, cy + 12, 0xFFE2E2E2);
		}
	}
}
