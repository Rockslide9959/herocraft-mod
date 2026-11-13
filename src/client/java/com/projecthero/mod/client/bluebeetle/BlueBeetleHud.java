package com.projecthero.mod.client.bluebeetle;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.bluebeetle.BlueBeetle;
import com.projecthero.mod.bluebeetle.BlueBeetleAbilityManager;
import com.projecthero.mod.bluebeetle.BlueBeetleConfig;
import com.projecthero.mod.bluebeetle.data.BlueBeetleState;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.visual.MutationMeters;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.17.3: the Blue Beetle HUD on the Minecraft-style look of the newest powers ({@link VanillaHud.Stack}, bottom-right):
 * <pre>
 *   Adapted: fire                             status tag while an Adaptation runs
 *   BLUE BEETLE                               the title ("Khaji-Da has control" in red while unleashed)
 *   Adapt    [Hairline ===---]  12s           the Adaptation timer, only while it runs
 *   Cannon   [Hairline =====-]  70%           the Mega Cannon charge, only while it charges
 *   Shield   [Hairline ======]  30            the held shield's pool, only while it is up
 *   Khaji-Da [Hairline ====--]  8s            the unleash timer, only while Khaji-Da is unleashed
 *   Scarab   [Hairline ======]  84            Scarab Energy (blue; dim while it refills slowly; full and red while unleashed)
 *   +----+----+----+----+----+----+           R G Z X C V: shown move's cooldown (sweep + seconds), the Shift move's as the
 *   | R  | G  | Z  | X  | C  | V  |           durability bar; lit while that key's move runs
 *   +----+----+----+----+----+----+
 * </pre>
 * Hairline was the style of every bar here before. v0.16.5 rework: no blue screen flash on the bonding (user: no blue glow).
 */
public final class BlueBeetleHud {
	private static final int NAME = 0xFFE2E2E2;
	private static final int ENERGY = 0xFF2E7BFF;
	private static final int ENERGY_SLOW = 0xFF1E3E80;
	private static final int UNLEASHED = 0xFFFF3A2E;
	private static final int CHARGE = 0xFFCFE6FF;
	private static final int ADAPT = 0xFF7FD4FF;
	private static final int SHIELD = 0xFF9FD8FF;

	private BlueBeetleHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || !BlueBeetle.hasPower(p)) {
			return;
		}
		BlueBeetleState s = p.getAttachedOrElse(ModAttachments.BLUE_BEETLE_STATE, null);
		if (s == null) {
			return;
		}
		long now = mc.level.getGameTime();
		if (mc.options.hideGui || !s.suited) {
			return;
		}
		var exp = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		boolean unleashed = s.unleashedUntil > now;
		boolean shift = p.isShiftKeyDown();
		VanillaHud.Stack st = VanillaHud.Stack.bottomRight(g, VanillaHud.ORDER.length).keys(VanillaHud.ORDER.length);
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			String[] ids = BlueBeetleAbilityManager.idsOf(slot);
			if (ids.length == 0) {
				continue;
			}
			String shown = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			int cd = BlueBeetle.cooldownRemaining(p, shown);
			float frac = cd / (float) Math.max(1, BlueBeetleAbilityManager.maxCooldown(shown));
			float otherFrac = other == null ? 0f
					: BlueBeetle.cooldownRemaining(p, other) / (float) Math.max(1, BlueBeetleAbilityManager.maxCooldown(other));
			boolean active = (slot == AbilitySlot.SLOT_1 && (s.fx.chargeStart != 0L || s.fx.blasting))
					|| (slot == AbilitySlot.SLOT_2 && s.fx.grabTarget != -1)
					|| (slot == AbilitySlot.SLOT_3 && s.fx.bubbleTarget != -1)
					|| (slot == AbilitySlot.SLOT_4 && s.fx.spikeStart != 0L)
					|| (slot == AbilitySlot.SLOT_5 && (s.fx.shield || s.camo))
					|| (slot == AbilitySlot.SLOT_6 && (s.fx.blade || unleashed));
			st.slot(i, VanillaHud.keyLabel(slot), true, shift, Math.min(1f, frac), VanillaHud.seconds(cd), Math.min(1f, otherFrac), active);
		}

		int energyColour = unleashed ? UNLEASHED : s.slowRegenUntil > now ? ENERGY_SLOW : ENERGY;
		st.meter(Component.translatable("hud.projecthero.blue_beetle.energy"), unleashed ? 1f : s.energy / BlueBeetleConfig.ENERGY_MAX, energyColour,
				String.valueOf(Math.round(unleashed ? BlueBeetleConfig.ENERGY_MAX : s.energy)), unleashed ? UNLEASHED : ENERGY_TEXT,
				MutationMeters.Style.HAIRLINE);
		if (unleashed) {
			long left = s.unleashedUntil - now;
			st.meter(Component.translatable("hud.projecthero.blue_beetle.unleash"), left / (float) BlueBeetleConfig.UNLEASH_TICKS, UNLEASHED,
					VanillaHud.seconds((int) left) + "s", UNLEASHED, MutationMeters.Style.HAIRLINE);
		}
		if (s.fx.shield) {
			st.meter(Component.translatable("hud.projecthero.blue_beetle.shield"), s.fx.shieldHp / BlueBeetleConfig.SHIELD_HP, SHIELD,
					String.valueOf(Math.round(s.fx.shieldHp)), SHIELD, MutationMeters.Style.HAIRLINE);
		}
		if (s.fx.chargeStart != 0L) {
			float f = Mth.clamp((now - s.fx.chargeStart) / (float) BlueBeetleConfig.CANNON_CHARGE_TICKS, 0f, 1f);
			st.meter(Component.translatable("hud.projecthero.blue_beetle.charge"), f, CHARGE, Math.round(f * 100) + "%", CHARGE,
					MutationMeters.Style.HAIRLINE);
		}
		boolean adapting = s.adaptUntil > now && !s.adaptType.isEmpty();
		if (adapting) {
			long left = s.adaptUntil - now;
			st.meter(Component.translatable("hud.projecthero.blue_beetle.adapt"), left / (float) BlueBeetleConfig.ADAPT_TICKS, ADAPT,
					VanillaHud.seconds((int) left) + "s", ADAPT, MutationMeters.Style.HAIRLINE);
		}
		st.title(Component.translatable(unleashed ? "hud.projecthero.blue_beetle.unleashed" : "hud.projecthero.blue_beetle.title"),
				unleashed ? UNLEASHED : NAME);
		if (adapting) {
			st.tags(List.of(new VanillaHud.Tag(
					Component.translatable("hud.projecthero.blue_beetle.adapted", s.adaptType.replace('_', ' ')).getString(), ADAPT)));
		}

		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : VanillaHud.ORDER) {
				String[] ids = BlueBeetleAbilityManager.idsOf(slot);
				if (ids.length > 0) {
					rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.blue_beetle.ability." + ids[0]),
							ids.length > 1 ? Component.translatable("projecthero.blue_beetle.ability." + ids[1]) : null));
				}
			}
			st.moveList(rows);
		}
	}

	/** The energy number: a lighter blue than the bar so it reads on any background. */
	private static final int ENERGY_TEXT = 0xFF6EA8FF;
}
