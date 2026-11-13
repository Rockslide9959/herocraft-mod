package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

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
 * The Punisher HUD. v0.17.3: on the vanilla pieces ({@link VanillaHud.Stack}), bottom-right like the newest powers:
 * <pre>
 *   (training objectives, wrapped)
 *   MARKED 23s  CALLING WARZONE  Glock 17  45/45 reserve  CYCLE     status tags
 *   PUNISHER                                                        title
 *   Warzone   [=========---]  75%      while Shift+Z is held: the 5 s call filling up
 *   Reloading [======------]  50%      the held gun (FirearmHud.stackRows): reload progress, then the magazine
 *   Mag       [============] 12/30
 *   +--+--+--+--+--+--+                R G Z X C V: the plain move (Shift: the Shift move) with its cooldown sweep + seconds;
 *                                      the other move's cooldown is the durability bar. V = the held gun's Weapon Ability.
 * </pre>
 * N (the Tactical Satchel) is never shown -- no H / N boxes on the HUD. {@link #lastTop} is the top of the column, so the Agent
 * Venom panel ({@code SymbioteHud}) can stack itself above it.
 */
public final class PunisherHud {
	private static final int MARK_RED = 0xFFFF4040;

	/** Alt names per key (R G Z X C V): the plain move, then the Shift move ("" = none). */
	private static final String[][] NAMES = {
			{ "mark", "threat" }, { "strike", "kick" }, { "grenade", "warzone" }, { "roll", "advance" },
			{ "smoke", "flashbang" }, { "weapon", "" }
	};

	/**
	 * v0.17.3: the top edge of the Punisher's column last frame (everything above it is free), for panels that stack above it
	 * (Agent Venom). The screen height when nothing is drawn.
	 */
	public static volatile int lastTop = Integer.MAX_VALUE;

	/** Client mirror of the server's Warzone call: game time Shift+Z went down, or -1. */
	private static long warzoneChargeStart = -1L;
	private static boolean zWasDown;

	private PunisherHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		lastTop = g.guiHeight();
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
		boolean shift = player.isShiftKeyDown();
		boolean venom = com.projecthero.mod.symbiote.SymbioteAgentVenomAbilities.agentVenom(player);

		VanillaHud.Stack st = VanillaHud.Stack.bottomRight(g, 6).keys(6);
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			String label = VanillaHud.keyLabel(slot);
			if (slot == AbilitySlot.SLOT_5) {
				weaponAbility(st, i, label, shift); // v0.15.18: V = Weapon Ability (per held gun)
				continue;
			}
			String plain = PunisherAbilityManager.abilityIdOf(slot);
			String alt = PunisherAbilityManager.shiftAbilityIdOf(slot);
			// Agent Venom's Sneak extras ride on X / Z (its own panel shows them): those keys keep showing the plain move
			boolean showAlt = shift && !alt.isEmpty() && !(venom && SymbioteHud.agentVenomShift(slot) != null);
			String shown = showAlt ? alt : plain;
			String other = showAlt ? plain : (venom && SymbioteHud.agentVenomShift(slot) != null ? "" : alt);
			int cd = shown.isEmpty() ? 0 : Punisher.cooldownRemaining(player, shown);
			st.slot(i, label, true, showAlt, frac(cd, PunisherAbilityManager.cooldownTicksOf(shown)), VanillaHud.seconds(cd),
					other.isEmpty() ? 0f : frac(Punisher.cooldownRemaining(player, other), PunisherAbilityManager.cooldownTicksOf(other)),
					false);
		}

		// the held gun (FirearmHud): Mag / Reloading rows, its tags
		List<VanillaHud.Tag> gunTags = FirearmHud.stackRows(st);
		Component warzone = warzoneRow(st, mc, player, now);

		st.title(Component.translatable("hud.projecthero.punisher.title"), VanillaHud.WHITE);
		List<VanillaHud.Tag> tags = new ArrayList<>();
		int markLeft = PunisherIntelClient.markTicksLeft(now);
		if (markLeft > 0) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.punisher.marked", (markLeft + 19) / 20).getString(), MARK_RED));
		}
		if (warzone != null) {
			tags.add(new VanillaHud.Tag(warzone.getString(), MARK_RED));
		}
		tags.addAll(gunTags);
		st.tags(tags);
		if (s.trainingActive) {
			st.wrapped(VigilanteTraining.objectivesSummary(s).withStyle(ChatFormatting.GRAY), VanillaHud.GREY);
		}
		lastTop = st.top();

		if (MoveListOverlay.held()) {
			// v0.16.6: the shared move list, stacked above the column (and above the Agent Venom panel while it is up)
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (int i = 0; i < NAMES.length; i++) {
				Component alt = venom ? SymbioteHud.agentVenomShift(VanillaHud.ORDER[i]) : null;
				if (alt == null && !NAMES[i][1].isEmpty()) {
					alt = Component.translatable("hud.projecthero.punisher.name." + NAMES[i][1]);
				}
				rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(VanillaHud.ORDER[i]),
						Component.translatable("hud.projecthero.punisher.name." + NAMES[i][0]), alt));
			}
			int top = venom ? Math.min(st.top(), SymbioteHud.agentVenomPanelTop(g)) : st.top();
			MoveListOverlay.draw(g, st.x0 + st.width, top - 4, rows);
		}
	}

	private static float frac(int cd, int max) {
		return cd <= 0 || max <= 0 ? 0f : Math.min(1f, cd / (float) max);
	}

	/**
	 * v0.15.18: the V key = the held gun's Weapon Ability -- its plain-V cooldown (Shift: the Shift+V one) as the sweep + seconds,
	 * the other one as the durability bar. Greyed while no gun is held.
	 */
	private static void weaponAbility(VanillaHud.Stack st, int i, String label, boolean shift) {
		Minecraft mc = Minecraft.getInstance();
		int[] tap = com.projecthero.mod.punisher.ability.PunisherWeaponAbilities.hudCooldown(mc.player, false);
		int[] alt = com.projecthero.mod.punisher.ability.PunisherWeaponAbilities.hudCooldown(mc.player, true);
		if (tap == null || alt == null) {
			st.slot(i, label, false, shift, 0f, 0, 0f, false);
			return;
		}
		int[] shown = shift ? alt : tap;
		int[] other = shift ? tap : alt;
		st.slot(i, label, true, shift, frac(shown[0], shown[1]), VanillaHud.seconds(shown[0]), frac(other[0], other[1]), false);
	}

	/**
	 * Warzone (hold Shift+Z for 5 s): mirrors the server's rule on this client -- the call starts when Z goes down with Shift held
	 * and Warzone ready, and is off the moment either key is let go -- and draws it filling up as a meter row. Returns the status
	 * word (CALLING WARZONE / WARZONE INBOUND) while it is up, else null.
	 */
	private static Component warzoneRow(VanillaHud.Stack st, Minecraft mc, LocalPlayer player, long now) {
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
			return null;
		}
		float frac = Math.min(1f, (now - warzoneChargeStart) / (float) PunisherConfig.WARZONE_CHARGE_TICKS);
		if (frac >= 1f && now - warzoneChargeStart > PunisherConfig.WARZONE_CHARGE_TICKS + 20) {
			return null; // called in -- the bar has done its job
		}
		st.meter(Component.translatable("hud.projecthero.punisher.warzone_label"), frac, 0xFFC03030, Math.round(frac * 100f) + "%",
				frac >= 1f ? 0xFFFF5050 : MARK_RED);
		return Component.translatable(frac >= 1f ? "hud.projecthero.punisher.warzone_inbound" : "hud.projecthero.punisher.warzone_calling");
	}
}
