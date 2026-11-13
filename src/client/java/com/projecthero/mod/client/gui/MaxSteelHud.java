package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.maxsteel.MaxSteel;
import com.projecthero.mod.maxsteel.MaxSteelConfig;
import com.projecthero.mod.maxsteel.MaxSteelMode;
import com.projecthero.mod.maxsteel.data.MaxSteelFx;
import com.projecthero.mod.maxsteel.data.MaxSteelState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The Max Steel HUD. v0.17.3: on the vanilla pieces ({@link VanillaHud.Stack}), bottom-right like the newest powers; Max Steel
 * stays <b>blue</b> in every mode (every boss bar here is the blue one, white only when a charge is full):
 * <pre>
 *   READY  TURBO MODE Strength  LOCKED     tags: IN COMBAT / READY / OVERLOADED, the mode he is in, the cannon lock
 *   MAX STEEL                               title
 *   Going Turbo   [Hairline ======-]  64%   while the suit forms / retracts
 *   Blast (2)     [Hairline ==|==|=-] 45%   while R is held (the stage notches)
 *   Cannon        [Hairline ========] READY / 3.4s / 45% / FULL
 *   T.U.R.B.O.    [Hairline =====|--]  82%  energy (the overload recovery mark while locked out)
 *   +--+--+--+--+--+--+                     R G Z X C V: cooldowns; the Turbo Mode he is in lights its key; greyed while overloaded
 * </pre>
 * Every bar is a <b>Hairline</b> bar, as before. Hold Left-Alt for the move names. Unsuited, a compact version shows the title,
 * "MODE Normal" and the energy, which regenerates in Normal form too.
 *
 * <p>Also keeps the brief directional threat marker fed by
 * {@link com.projecthero.mod.network.MaxSteelWarningPayload} (see {@link #flashWarning}).
 */
public final class MaxSteelHud {
	private static final int CYAN = 0xFF35E0F0;
	private static final int LOW = 0xFFFF5A5A;
	private static final int DIM = 0xFF6F8A90;
	private static final int BLUE = 0xFF3FA8FF;
	private static final int WHITE = 0xFFE8FFFF;
	private static final MutationMeters.Style HAIRLINE = MutationMeters.Style.HAIRLINE;

	/** Slot 1..6 -> the ability id whose cooldown that key shows (or null), and its full length. */
	private static final String[] SLOT_COOLDOWNS = {
			"turbo_blast", "turbo_slam", "turbo_dash", null, "turbo_stealth", "turbo_cannon"
	};
	private static final int[] SLOT_MAX = {
			MaxSteelConfig.BLAST_COOLDOWN_TICKS, MaxSteelConfig.STRENGTH_SLAM_COOLDOWN_TICKS,
			MaxSteelConfig.TURBO_DASH_COOLDOWN_TICKS, 1, MaxSteelConfig.STEALTH_COOLDOWN_TICKS,
			MaxSteelConfig.CANNON_COOLDOWN_TICKS
	};
	private static final MaxSteelMode[] SLOT_MODE = {
			null, MaxSteelMode.STRENGTH, MaxSteelMode.SPEED, MaxSteelMode.FLIGHT, MaxSteelMode.STEALTH, null
	};
	private static final String[] SLOT_NAMES = {
			"turbo_blast", "turbo_strength", "turbo_speed", "turbo_flight", "turbo_stealth", "turbo_cannon"
	};

	private static long warningUntil;
	private static float warningYaw;

	private MaxSteelHud() {
	}

	/** Called from the network handler when a Steel warning arrives. */
	public static void flashWarning(float yawToThreat) {
		Minecraft mc = Minecraft.getInstance();
		warningUntil = (mc.level != null ? mc.level.getGameTime() : 0L) + 20L;
		warningYaw = yawToThreat;
	}

	/** The colour the active mode lights up in. */
	public static int modeColour(MaxSteelMode mode) {
		return CYAN; // v0.14.2: Max Steel stays blue in every mode
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.options.hideGui || mc.level == null) {
			return;
		}
		MaxSteelState s = player.getAttachedOrElse(ModAttachments.MAX_STEEL_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = mc.level.getGameTime();
		float pt = delta.getGameTimeDeltaPartialTick(false);
		float energy = Math.max(0f, Math.min(MaxSteelConfig.MAX_TURBO_ENERGY, s.turboEnergy));
		boolean overloaded = s.lockoutUntil != 0L && energy < MaxSteelConfig.OVERLOAD_RECOVER_ENERGY;

		if (!s.transformed) {
			// compact (v0.14.4): the pool (it regenerates here too), the title, "MODE Normal"
			VanillaHud.Stack st = VanillaHud.Stack.bottomRight(g, 6);
			energyRow(st, energy, overloaded, now);
			st.title(Component.translatable("hud.projecthero.max_steel.title"), CYAN);
			st.tags(List.of(new VanillaHud.Tag(Component.translatable("hud.projecthero.max_steel.mode_label_normal").getString(), DIM),
					new VanillaHud.Tag(Component.translatable("hud.projecthero.max_steel.mode_row.normal").getString(), 0xFFE0E0E0)));
			// v0.16.6: the shared move list -- only H (Go Turbo) does anything out of the suit
			st.moveList(List.of(hRow()));
			return;
		}

		MaxSteelFx fx = player.getAttachedOrElse(ModAttachments.MAX_STEEL_FX, null);
		if (fx == null) {
			fx = MaxSteelFx.EMPTY;
		}
		boolean blastCharging = fx.blastChargeStart() != 0L && now - fx.blastChargeStart() >= 2;
		boolean forming = s.transformDir != MaxSteelState.DIR_IDLE;
		MaxSteelMode mode = MaxSteel.formMode(player); // v0.14.4: the target mode already, while suiting up into one

		// ---- ability keys (R G Z X C V)
		VanillaHud.Stack st = VanillaHud.Stack.bottomRight(g, 6).keys(6);
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			int k = slot.index();
			boolean active = SLOT_MODE[k] != null && SLOT_MODE[k] == mode;
			int cd = SLOT_COOLDOWNS[k] != null && !overloaded ? MaxSteel.cooldownRemaining(player, SLOT_COOLDOWNS[k]) : 0;
			st.slot(i, VanillaHud.keyLabel(slot), !overloaded, false, cd > 0 ? Math.min(1f, cd / (float) SLOT_MAX[k]) : 0f,
					VanillaHud.seconds(cd), 0f, active);
		}

		// ---- T.U.R.B.O. energy
		energyRow(st, energy, overloaded, now);

		// ---- Turbo Cannon: charging / recharging / ready
		Component cannon = Component.translatable("hud.projecthero.max_steel.cannon_label");
		int cannonCd = MaxSteel.cooldownRemaining(player, "turbo_cannon");
		Boolean locked = null;
		if (fx.cannonChargeStart() != 0L) {
			float c = Math.min(1f, (now - fx.cannonChargeStart() + pt) / MaxSteelConfig.CANNON_MAX_CHARGE_TICKS);
			locked = fx.cannonTarget() >= 0;
			st.meter(cannon, c, c >= 1f ? WHITE : BLUE, c >= 1f ? Component.translatable("hud.projecthero.max_steel.full_value").getString()
					: pct(c) + "%", c >= 1f ? WHITE : BLUE, HAIRLINE);
		} else if (cannonCd > 0) {
			float c = 1f - cannonCd / (float) MaxSteelConfig.CANNON_COOLDOWN_TICKS;
			st.meter(cannon, c, 0xFF1F5A80, String.format(java.util.Locale.ROOT, "%.1fs", cannonCd / 20f), DIM, HAIRLINE);
		} else {
			st.meter(cannon, 1f, BLUE, Component.translatable("hud.projecthero.max_steel.ready").getString(), BLUE, HAIRLINE);
		}

		// ---- Turbo Blast charge (while R is held)
		if (blastCharging) {
			float c = Math.min(1f, (now - fx.blastChargeStart() + pt) / MaxSteelConfig.BLAST_MAX_CHARGE_TICKS);
			int stage = 0;
			for (float stp : MaxSteelConfig.BLAST_STAGES) {
				if (c >= stp) {
					stage++;
				}
			}
			Component label = Component.translatable("hud.projecthero.max_steel.blast_label", stage);
			String value = c >= 1f ? Component.translatable("hud.projecthero.max_steel.full_value").getString() : pct(c) + "%";
			st.meter(label, c, c >= 1f ? WHITE : CYAN, value, c >= 1f ? 0xFFFFFFFF : CYAN, HAIRLINE);
			MeterMarks.marks(st, label, value, 0xFF071820, MaxSteelConfig.BLAST_STAGES); // stage notches
		}

		// ---- suit forming / retracting
		if (forming) {
			float p = Math.max(0f, Math.min(1f, (now - s.transformStartTick + pt) / Math.max(1, s.transformDurationTicks)));
			boolean up = s.transformDir == MaxSteelState.DIR_SUITING_UP;
			st.meter(Component.translatable(up ? "hud.projecthero.max_steel.going_turbo_label" : "hud.projecthero.max_steel.powering_down_label"),
					up ? p : 1f - p, CYAN, pct(p) + "%", VanillaHud.WHITE, HAIRLINE);
		}

		// ---- title + status tags
		st.title(Component.translatable("hud.projecthero.max_steel.title"), modeColour(mode));
		List<VanillaHud.Tag> tags = new ArrayList<>();
		if (overloaded) {
			tags.add(new VanillaHud.Tag(Component.translatable("message.projecthero.max_steel.overloaded").getString(), VanillaHud.RED));
		} else {
			boolean combat = now < s.combatUntil;
			tags.add(new VanillaHud.Tag(Component.translatable(combat ? "hud.projecthero.max_steel.in_combat" : "hud.projecthero.max_steel.ready")
					.getString(), combat ? VanillaHud.RED : VanillaHud.GREEN));
		}
		// v0.14.3: only the Turbo Mode he is in right now
		tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.max_steel.mode_label").getString(), DIM));
		tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.max_steel.mode_row." + mode.lower()).getString(), modeColour(mode)));
		if (locked != null) {
			tags.add(new VanillaHud.Tag(Component.translatable(locked ? "hud.projecthero.max_steel.cannon_locked"
					: "hud.projecthero.max_steel.cannon_free").getString(), locked ? WHITE : DIM));
		}
		st.tags(tags);

		// v0.16.6: Left-Alt -> the shared move list (the six keys, then H), stacked above the column
		List<MoveListOverlay.Row> rows = new ArrayList<>();
		for (AbilitySlot slot : VanillaHud.ORDER) {
			rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot),
					Component.translatable("projecthero.max_steel.ability." + SLOT_NAMES[slot.index()]), null));
		}
		rows.add(hRow());
		st.moveList(rows);

		// directional threat marker
		if (now < warningUntil) {
			float rel = net.minecraft.util.Mth.wrapDegrees(warningYaw - player.getYRot());
			int cx = g.guiWidth() / 2;
			int cy = g.guiHeight() / 2;
			double rad = Math.toRadians(rel);
			int wx = cx + (int) (Math.sin(rad) * 40);
			int wy = cy - (int) (Math.cos(rad) * 40);
			g.fill(wx - 3, wy - 3, wx + 3, wy + 3, ((now % 6) < 3) ? 0xFFFFDD33 : 0xFFFF6633);
		}
	}

	/** v0.16.6: the H row of the move list ("Go Turbo / Power Down"; the lang line spells its own "H", so that is dropped). */
	private static MoveListOverlay.Row hRow() {
		String text = Component.translatable("hud.projecthero.max_steel.key_h").getString().replaceFirst("^\\S{1,3}\\s+", "");
		return MoveListOverlay.Row.of(MoveListOverlay.key(6), Component.literal(text), null);
	}

	/**
	 * "T.U.R.B.O. [Hairline] 82%", the bar always blue (it blinks out while low / overloaded, the value turns red), with the
	 * overload recovery mark while locked out.
	 */
	private static void energyRow(VanillaHud.Stack st, float energy, boolean overloaded, long now) {
		float frac = energy / MaxSteelConfig.MAX_TURBO_ENERGY;
		boolean low = energy < MaxSteelConfig.LOW_ENERGY_WARN;
		boolean pulseOff = (low || overloaded) && (now % 10) < 4;
		Component label = Component.translatable("hud.projecthero.max_steel.turbo_label");
		String value = pct(frac) + "%";
		st.meter(label, pulseOff ? 0f : frac, CYAN, value, low || overloaded ? LOW : 0xFFA8DDE6, HAIRLINE);
		if (overloaded) {
			MeterMarks.marks(st, label, value, 0xFFFFDD33, MaxSteelConfig.OVERLOAD_RECOVER_ENERGY / MaxSteelConfig.MAX_TURBO_ENERGY);
		}
	}

	private static int pct(float frac) {
		return (int) Math.floor(Math.max(0f, Math.min(1f, frac)) * 100f + 1.0e-3f);
	}
}
