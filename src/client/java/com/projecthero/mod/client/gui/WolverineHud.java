package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.wolverine.Wolverine;
import com.projecthero.mod.wolverine.WolverineAbilityManager;
import com.projecthero.mod.wolverine.WolverineConfig;
import com.projecthero.mod.wolverine.data.WolverineState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The Wolverine HUD. v0.17.3: on the vanilla pieces ({@link VanillaHud.Stack}), bottom-right like the newest powers:
 * <pre>
 *   Claws: Deployed  RAGE 12s  Full HP to heal        status tags
 *   WOLVERINE                                         title
 *   Execution [==========]  READY                     while Z charges the Adamantium Execution
 *   Skin      [=====|----]   45%                      Death Surge skin recovery (the half mark: debuffs gone past it)
 *   Healing   [========--]   80%                      the Healing Factor pool
 *   +--+--+--+--+--+--+                               R G Z X C V: the plain move (Shift: the Shift move) with its cooldown;
 *                                                     the other move's cooldown is the durability bar. C (Berserker Rage) has
 *                                                     no cooldown: its key fills with the rage meter, framed while it burns.
 * </pre>
 * Hold Left-Alt for the move names. Only drawn while the player is Wolverine.
 */
public final class WolverineHud {
	private static final String[] NAME_KEYS = {
			"projecthero.wolverine.ability.claw_slash", "projecthero.wolverine.ability.cross_slash",
			"projecthero.wolverine.ability.claw_dash", "projecthero.wolverine.ability.berserker_plunge",
			"projecthero.wolverine.ability.frenzy", "projecthero.wolverine.ability.berserker_rage"
	};

	/** v0.16.1: each key's Shift move (indexed by slot, R G X Z V C), named under Alt and shown as the durability bar. */
	private static final String[] SHIFT_NAME_KEYS = {
			"projecthero.wolverine.ability.rising_uppercut", "projecthero.wolverine.ability.whirlwind",
			"projecthero.wolverine.ability.pounce", "projecthero.wolverine.ability.adamantium_execution",
			"projecthero.wolverine.ability.bleed_flurry", "projecthero.wolverine.ability.feral_roar"
	};

	private WolverineHud() {
	}

	private static float frac(int cd, int max) {
		return cd <= 0 || max <= 0 ? 0f : Math.min(1f, cd / (float) max);
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || mc.level == null || !Wolverine.hasPower(mc.player)) {
			return;
		}
		WolverineState s = mc.player.getAttachedOrElse(ModAttachments.WOLVERINE_STATE, null);
		if (s == null) {
			return;
		}
		LocalPlayer player = mc.player;
		long now = mc.level.getGameTime();
		boolean shift = player.isShiftKeyDown();

		VanillaHud.Stack st = VanillaHud.Stack.bottomRight(g, 6).keys(6);
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			int pcd = Wolverine.cooldownRemaining(player, WolverineAbilityManager.abilityIdOf(slot));
			int scd = Wolverine.cooldownRemaining(player, WolverineAbilityManager.shiftAbilityIdOf(slot));
			float pFrac = frac(pcd, WolverineAbilityManager.maxCooldown(slot));
			float sFrac = frac(scd, WolverineAbilityManager.shiftMaxCooldown(slot));
			if (slot == AbilitySlot.SLOT_6) {
				// Berserker Rage has no cooldown: the key fills with the rage meter (or drains with the rage)
				boolean burning = s.rageUntil > now;
				float rage = burning ? (s.rageUntil - now) / (float) WolverineConfig.RAGE_TICKS : s.rageMeter / WolverineConfig.RAGE_BAR_MAX;
				st.slot(i, VanillaHud.keyLabel(slot), true, shift, shift ? sFrac : 0f, shift ? VanillaHud.seconds(scd) : 0,
						shift ? 0f : sFrac, burning);
				st.hold(i, Math.max(0f, Math.min(1f, rage)));
				continue;
			}
			st.slot(i, VanillaHud.keyLabel(slot), true, shift, shift ? sFrac : pFrac, VanillaHud.seconds(shift ? scd : pcd),
					shift ? pFrac : sFrac, false);
		}

		// v0.12.41: the Healing Factor pool (v0.15.18: no Death Surge pixel or 30-cost tick any more)
		float pool = Math.max(0f, Math.min(WolverineConfig.HEAL_POOL_MAX, s.healPool));
		float pf = pool / WolverineConfig.HEAL_POOL_MAX;
		st.meter(Component.translatable("hud.projecthero.wolverine.healing_label"), pf, pool <= 0f ? 0xFFB02020 : 0xFF3ADB5A,
				(int) Math.floor(pf * 100.0f + 1.0e-3f) + "%", pool <= 0f ? VanillaHud.RED : VanillaHud.GREEN);

		// v0.15.18: Death Surge skin recovery -- only advances at full HP ("Full HP to heal" while paused)
		boolean skinPaused = mc.player.getHealth() < mc.player.getMaxHealth();
		if (s.skinRecovery < 1.0f) {
			float skin = Math.max(0f, Math.min(1f, s.skinRecovery));
			Component label = Component.translatable("hud.projecthero.wolverine.skin_label");
			String value = (int) Math.floor(skin * 100.0f + 1.0e-3f) + "%";
			st.meter(label, skin, skinPaused ? 0xFF8A5A40 : 0xFFE0A070, value, skinPaused ? VanillaHud.RED : VanillaHud.GOLD);
			MeterMarks.marks(st, label, value, 0xFFFFFFFF, WolverineConfig.SURGE_DEBUFF_UNTIL); // past here the debuffs are gone
		}

		// the Adamantium Execution charge (hold Shift+Z)
		boolean chargeReady = false;
		if (s.chargeStartedAt != 0L) {
			float frac = Math.min(1f, (now - s.chargeStartedAt) / (float) WolverineConfig.EXECUTION_CHARGE_TICKS);
			chargeReady = frac >= 1f;
			st.meter(Component.translatable("hud.projecthero.wolverine.execution_label"), frac, frac >= 1f ? 0xFFFFD34A : 0xFFE08A00,
					chargeReady ? Component.translatable("hud.projecthero.wolverine.ready_value").getString() : (int) (frac * 100) + "%",
					VanillaHud.GOLD);
		}

		st.title(Component.translatable("hud.projecthero.wolverine.title"), VanillaHud.GOLD);
		List<VanillaHud.Tag> tags = new ArrayList<>();
		tags.add(new VanillaHud.Tag(Component.translatable(s.clawsOut ? "hud.projecthero.wolverine.claws_deployed"
				: "hud.projecthero.wolverine.claws_retracted").getString(), s.clawsOut ? 0xFFE8E8E8 : 0xFF909090));
		if (s.rageUntil > now) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.wolverine.rage", (int) ((s.rageUntil - now + 19) / 20)).getString(),
					VanillaHud.RED));
		}
		if (s.skinRecovery < 1.0f && skinPaused) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.wolverine.skin_paused_tag").getString(), VanillaHud.RED));
		}
		st.tags(tags);
		if (chargeReady) {
			st.wrapped(Component.translatable("hud.projecthero.wolverine.charge_ready"), VanillaHud.GOLD); // "... release Z", wrapped
		}

		// v0.16.6: the shared move list (each key: plain move + Shift move), stacked above the column
		List<MoveListOverlay.Row> rows = new ArrayList<>();
		for (AbilitySlot slot : VanillaHud.ORDER) {
			rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable(NAME_KEYS[slot.index()]),
					Component.translatable(SHIFT_NAME_KEYS[slot.index()])));
		}
		st.moveList(rows);
	}
}
