package com.projecthero.mod.client.gui;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.maxsteel.MaxSteel;
import com.projecthero.mod.maxsteel.MaxSteelConfig;
import com.projecthero.mod.maxsteel.MaxSteelMode;
import com.projecthero.mod.maxsteel.data.MaxSteelFx;
import com.projecthero.mod.maxsteel.data.MaxSteelState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The Max Steel HUD, v0.14.2 layout (bottom-right, top to bottom):
 * <pre>
 *   [R][G][X][Z][V][C]                    ability keys -- cooldowns drain up from the bottom of each box
 *   MAX STEEL                    READY    title + IN COMBAT / READY / OVERLOAD
 *                         TURBO MODE  Strength   v0.14.3: only the mode he is in, lit blue
 *   T.U.R.B.O. 82%                         energy, then its <b>hairline</b> bar
 *   Turbo Cannon READY / Recharging 3.4s / Charging 45%, then its hairline bar
 *   (Turbo Blast 67% while R is held · Going Turbo / Powering Down while the suit forms)
 * </pre>
 * Every bar is a <b>Hairline</b> bar (3 px, no border, the label on the line above). Hold Left-Alt for the move names.
 * Unsuited, a compact version shows just the title, the "press H" hint and the energy.
 *
 * <p>Also keeps the brief directional threat marker fed by
 * {@link com.projecthero.mod.network.MaxSteelWarningPayload} (see {@link #flashWarning}).
 */
public final class MaxSteelHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int HAIR = 3;

	private static final int CYAN = 0xFF35E0F0;
	private static final int LOW = 0xFFFF5A5A;
	private static final int TRACK = 0x90071820;
	private static final int BOX_BG = 0xC0071820;
	private static final int BORDER = 0xFF1E5A66;
	private static final int COOLDOWN = 0xB0000000;
	private static final int KEY = 0xFFCFF4F8;
	private static final int DIM = 0xFF6F8A90;
	private static final int BLUE = 0xFF3FA8FF;
	private static final int WHITE = 0xFFE8FFFF;

	/** Slot 1..6 -> the ability id whose cooldown that box shows (or null), and its full length. */
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
		int totalW = 6 * BOX + 5 * GAP;
		int x0 = g.guiWidth() - MARGIN - totalW;
		int right = x0 + totalW;

		if (!s.transformed) {
			// compact: title, the H hint and the pool
			int y = g.guiHeight() - MARGIN - 34;
			g.drawString(mc.font, Component.translatable("hud.projecthero.max_steel.title")
					.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), x0, y, CYAN, true);
			y += 10;
			g.drawString(mc.font, Component.translatable("hud.projecthero.max_steel.press_h"), x0, y, DIM, true);
			y += 10;
			energyBar(g, mc, x0, y, totalW, energy, overloaded, now);
			return;
		}

		MaxSteelFx fx = player.getAttachedOrElse(ModAttachments.MAX_STEEL_FX, null);
		if (fx == null) {
			fx = MaxSteelFx.EMPTY;
		}
		boolean blastCharging = fx.blastChargeStart() != 0L && now - fx.blastChargeStart() >= 2;
		boolean forming = s.transformDir != MaxSteelState.DIR_IDLE;
		// height: boxes + title + modes + energy(label+bar) + cannon(label+bar) [+ blast] [+ forming]
		int height = BOX + 2 + 10 + 11 + 10 + HAIR + 3 + 10 + HAIR + (blastCharging ? 3 + 10 + HAIR : 0) + (forming ? 3 + 10 + HAIR : 0);
		int y0 = g.guiHeight() - MARGIN - height;
		MaxSteelMode mode = s.modeEnum();
		boolean expanded = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
				org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

		// ---- ability keys
		for (int i = 0; i < 6; i++) {
			AbilitySlot slot = AbilitySlot.byNumber(i + 1);
			int x = x0 + i * (BOX + GAP);
			boolean active = SLOT_MODE[i] != null && SLOT_MODE[i] == mode;
			int accent = active ? modeColour(mode) : BORDER;
			g.fill(x, y0, x + BOX, y0 + BOX, BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, accent);
			if (active) {
				g.fill(x + 1, y0 + BOX - 2, x + BOX - 1, y0 + BOX - 1, accent); // lit underline for the active mode
			}
			int cd = SLOT_COOLDOWNS[i] != null ? MaxSteel.cooldownRemaining(player, SLOT_COOLDOWNS[i]) : 0;
			if (overloaded) {
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, COOLDOWN);
				g.drawString(mc.font, String.valueOf(slot.defaultKey()), x + 7, y0 + 6, DIM, true);
			} else if (cd > 0) {
				int h = (int) (BOX * Math.min(1f, cd / (float) SLOT_MAX[i]));
				g.fill(x, y0 + BOX - h, x + BOX, y0 + BOX, COOLDOWN);
				g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2, y0 + 6, 0xFFFFFFFF);
			} else {
				g.drawString(mc.font, String.valueOf(slot.defaultKey()), x + 7, y0 + 6, active ? 0xFFFFFFFF : KEY, true);
			}
			if (expanded) {
				Component name = Component.translatable("projecthero.max_steel.ability." + SLOT_NAMES[i]);
				g.drawString(mc.font, name, x0 - 8 - mc.font.width(name), y0 + i * 10, 0xFFE0E0E0, true);
			}
		}
		if (expanded) {
			Component h = Component.translatable("hud.projecthero.max_steel.key_h");
			g.drawString(mc.font, h, x0 - 8 - mc.font.width(h), y0 + 60, 0xFFE0E0E0, true);
		}

		// ---- title + status
		int y = y0 + BOX + 2;
		g.drawString(mc.font, Component.translatable("hud.projecthero.max_steel.title")
				.withStyle(ChatFormatting.BOLD), x0, y, modeColour(mode), true);
		Component status = overloaded
				? Component.translatable("message.projecthero.max_steel.overloaded").withStyle(ChatFormatting.RED)
				: Component.translatable(now < s.combatUntil
						? "hud.projecthero.max_steel.in_combat" : "hud.projecthero.max_steel.ready")
						.withStyle(now < s.combatUntil ? ChatFormatting.RED : ChatFormatting.GREEN);
		g.drawString(mc.font, status, right - mc.font.width(status), y, 0xFFFFFFFF, true);
		y += 10;

		// ---- v0.14.3: only the Turbo Mode he is in right now (was all five with the active one lit)
		Component modeTag = Component.translatable("hud.projecthero.max_steel.mode_label");
		Component modeName = Component.translatable("hud.projecthero.max_steel.mode_row." + mode.lower())
				.withStyle(ChatFormatting.BOLD);
		int nameW = mc.font.width(modeName);
		int mx = right - nameW;
		g.drawString(mc.font, modeTag, mx - 4 - mc.font.width(modeTag), y, DIM, true);
		g.drawString(mc.font, modeName, mx, y, modeColour(mode), true);
		g.fill(mx, y + 9, mx + nameW, y + 10, modeColour(mode));
		y += 11;

		// ---- T.U.R.B.O. energy
		energyBar(g, mc, x0, y, totalW, energy, overloaded, now);
		y += 10 + HAIR + 3;

		// ---- Turbo Cannon: charging / recharging / ready
		int cannonCd = MaxSteel.cooldownRemaining(player, "turbo_cannon");
		if (fx.cannonChargeStart() != 0L) {
			float c = Math.min(1f, (now - fx.cannonChargeStart() + pt) / MaxSteelConfig.CANNON_MAX_CHARGE_TICKS);
			boolean locked = fx.cannonTarget() >= 0;
			Component label = Component.translatable(c >= 1f ? "hud.projecthero.max_steel.cannon_full"
					: "hud.projecthero.max_steel.cannon_charging", pct(c));
			g.drawString(mc.font, label, x0, y, c >= 1f ? WHITE : BLUE, true);
			Component lock = Component.translatable(locked ? "hud.projecthero.max_steel.cannon_locked"
					: "hud.projecthero.max_steel.cannon_free");
			g.drawString(mc.font, lock, right - mc.font.width(lock), y, locked ? WHITE : DIM, true);
			hairline(g, x0, y + 10, totalW, c, c >= 1f ? WHITE : BLUE);
		} else if (cannonCd > 0) {
			float c = 1f - cannonCd / (float) MaxSteelConfig.CANNON_COOLDOWN_TICKS;
			g.drawString(mc.font, Component.translatable("hud.projecthero.max_steel.cannon_recharging",
					String.format(java.util.Locale.ROOT, "%.1f", cannonCd / 20f)), x0, y, DIM, true);
			hairline(g, x0, y + 10, totalW, c, 0xFF1F5A80);
		} else {
			g.drawString(mc.font, Component.translatable("hud.projecthero.max_steel.cannon_ready"), x0, y, BLUE, true);
			hairline(g, x0, y + 10, totalW, 1f, BLUE);
		}
		y += 10 + HAIR;

		// ---- Turbo Blast charge (while R is held)
		if (blastCharging) {
			y += 3;
			float c = Math.min(1f, (now - fx.blastChargeStart() + pt) / MaxSteelConfig.BLAST_MAX_CHARGE_TICKS);
			int stage = 0;
			for (float st : MaxSteelConfig.BLAST_STAGES) {
				if (c >= st) {
					stage++;
				}
			}
			g.drawString(mc.font, Component.translatable(c >= 1f ? "hud.projecthero.max_steel.blast_full"
					: "hud.projecthero.max_steel.blast_charging", pct(c), stage), x0, y, c >= 1f ? 0xFFFFFFFF : CYAN, true);
			hairline(g, x0, y + 10, totalW, c, c >= 1f ? 0xFFE8FFFF : CYAN);
			for (float st : MaxSteelConfig.BLAST_STAGES) {
				int sx = x0 + Math.round(totalW * st) - 1;
				g.fill(sx, y + 10, sx + 1, y + 10 + HAIR, 0xFF071820); // stage notches
			}
			y += 10 + HAIR;
		}

		// ---- suit forming / retracting
		if (forming) {
			y += 3;
			float p = Math.max(0f, Math.min(1f, (now - s.transformStartTick + pt) / Math.max(1, s.transformDurationTicks)));
			boolean up = s.transformDir == MaxSteelState.DIR_SUITING_UP;
			g.drawString(mc.font, Component.translatable(up ? "hud.projecthero.max_steel.going_turbo"
					: "hud.projecthero.max_steel.powering_down", pct(p)), x0, y, 0xFFFFFFFF, true);
			hairline(g, x0, y + 10, totalW, up ? p : 1f - p, CYAN);
		}

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

	/** "T.U.R.B.O. 82%" then its hairline, with the overload recovery mark while locked out. */
	private static void energyBar(GuiGraphics g, Minecraft mc, int x0, int y, int w, float energy, boolean overloaded, long now) {
		float frac = energy / MaxSteelConfig.MAX_TURBO_ENERGY;
		boolean low = energy < MaxSteelConfig.LOW_ENERGY_WARN;
		boolean pulseOff = (low || overloaded) && (now % 10) < 4;
		g.drawString(mc.font, Component.translatable("hud.projecthero.max_steel.turbo_pct", pct(frac)), x0, y,
				low || overloaded ? LOW : 0xFFA8DDE6, true);
		hairline(g, x0, y + 10, w, pulseOff ? 0f : frac, low ? LOW : CYAN);
		if (overloaded) {
			int mark = x0 + Math.round(w * (MaxSteelConfig.OVERLOAD_RECOVER_ENERGY / MaxSteelConfig.MAX_TURBO_ENERGY));
			g.fill(mark, y + 9, mark + 1, y + 10 + HAIR + 1, 0xFFFFDD33);
		}
	}

	/** A Hairline bar: 3 px, no border, no text -- a dim track and the fill. */
	private static void hairline(GuiGraphics g, int x, int y, int w, float frac, int colour) {
		g.fill(x, y, x + w, y + HAIR, TRACK);
		int fw = Math.round(w * Math.max(0f, Math.min(1f, frac)));
		if (fw > 0) {
			g.fill(x, y, x + fw, y + HAIR, colour);
		}
	}

	private static int pct(float frac) {
		return (int) Math.floor(Math.max(0f, Math.min(1f, frac)) * 100f + 1.0e-3f);
	}
}
