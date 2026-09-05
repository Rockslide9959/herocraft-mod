package com.herocraft.mod.client.gui;

import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.hero.AbilitySlot;
import com.herocraft.mod.maxsteel.MaxSteel;
import com.herocraft.mod.maxsteel.MaxSteelConfig;
import com.herocraft.mod.maxsteel.MaxSteelMode;
import com.herocraft.mod.maxsteel.data.MaxSteelState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The Max Steel HUD (v0.9.2 rework). Now built like {@link ThorHud} / {@link SpiderHud}: the six
 * abilities as keybind boxes in the bottom-right corner with cooldown shading, the active Turbo Mode
 * highlighted, a cyan T.U.R.B.O. Energy meter beneath, the current mode name and an IN COMBAT /
 * READY / OVERLOAD status line. Only drawn while the suit is on.
 *
 * <p>Also keeps the brief directional threat marker fed by
 * {@link com.herocraft.mod.network.MaxSteelWarningPayload} (see {@link #flashWarning}).
 */
public final class MaxSteelHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;

	private static final int CYAN = 0xFF35E0F0;
	private static final int CYAN_DIM = 0xFF1A6A78;
	private static final int LOW = 0xFFFF5A5A;
	private static final int BOX_BG = 0xC0071820;
	private static final int BORDER = 0xFF1E5A66;
	private static final int BORDER_ACTIVE = 0xFF35E0F0;
	private static final int COOLDOWN = 0xB0000000;
	private static final int KEY = 0xFFCFF4F8;

	/** Slot 1..6 -> the ability id whose cooldown that box should show (or null). */
	private static final String[] SLOT_COOLDOWNS = {
			"turbo_blast", "turbo_slam", "turbo_dash", null, "turbo_stealth", "turbo_cannon"
	};
	private static final MaxSteelMode[] SLOT_MODE = {
			null, MaxSteelMode.STRENGTH, MaxSteelMode.SPEED, MaxSteelMode.FLIGHT, MaxSteelMode.STEALTH, null
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

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.options.hideGui) {
			return;
		}
		MaxSteelState s = player.getAttachedOrElse(ModAttachments.MAX_STEEL_STATE, null);
		if (s == null || !s.hasPower || !s.transformed) {
			return;
		}

		long now = mc.level != null ? mc.level.getGameTime() : 0L;
		MaxSteelMode mode = s.modeEnum();
		float energy = Math.max(0f, Math.min(MaxSteelConfig.MAX_TURBO_ENERGY, s.turboEnergy));
		boolean overloaded = s.lockoutUntil != 0L && energy < MaxSteelConfig.OVERLOAD_RECOVER_ENERGY;

		int totalW = 6 * BOX + 5 * GAP;
		int x0 = g.guiWidth() - MARGIN - totalW;
		int y0 = g.guiHeight() - MARGIN - BOX - 20;

		g.drawString(mc.font, Component.translatable("herocraft.guide.max_steel")
				.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), x0, y0 - 10, CYAN);

		for (int i = 0; i < 6; i++) {
			AbilitySlot slot = AbilitySlot.byNumber(i + 1);
			int x = x0 + i * (BOX + GAP);
			boolean active = SLOT_MODE[i] != null && SLOT_MODE[i] == mode;

			g.fill(x, y0, x + BOX, y0 + BOX, BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, active ? BORDER_ACTIVE : BORDER);
			g.drawString(mc.font, String.valueOf(slot.defaultKey()), x + 2, y0 + 2, KEY, false);

			int cd = SLOT_COOLDOWNS[i] != null ? MaxSteel.cooldownRemaining(player, SLOT_COOLDOWNS[i]) : 0;
			if (overloaded) {
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, COOLDOWN);
			} else if (cd > 0) {
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, COOLDOWN);
				g.drawCenteredString(mc.font, String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(cd / 20.0f)),
						x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			}
		}

		// T.U.R.B.O. Energy bar below the row.
		float frac = energy / MaxSteelConfig.MAX_TURBO_ENERGY;
		boolean low = energy < MaxSteelConfig.LOW_ENERGY_WARN;
		boolean pulseOff = (low || overloaded) && (now % 10) < 4;
		int barY = y0 + BOX + 4;
		g.fill(x0 - 1, barY - 1, x0 + totalW + 1, barY + 5, BORDER);
		g.fill(x0, barY, x0 + totalW, barY + 4, 0xAA071820);
		if (!pulseOff) {
			g.fill(x0, barY, x0 + Math.round(totalW * frac), barY + 4, low ? LOW : CYAN);
		}
		if (overloaded) {
			// mark where regen has to reach for the lock-out to lift
			int mark = x0 + Math.round(totalW * (MaxSteelConfig.OVERLOAD_RECOVER_ENERGY / MaxSteelConfig.MAX_TURBO_ENERGY));
			g.fill(mark, barY - 1, mark + 1, barY + 5, 0xFFFFDD33);
		}
		g.drawString(mc.font, String.format(java.util.Locale.ROOT, "%d / %d", Math.round(energy),
				Math.round(MaxSteelConfig.MAX_TURBO_ENERGY)), x0, barY + 5, low ? LOW : 0xFFA8DDE6, false);

		// mode name + status, above the label / row.
		Component modeName = Component.translatable("herocraft.max_steel.mode." + mode.lower())
				.withStyle(ChatFormatting.AQUA);
		Component status = overloaded
				? Component.translatable("message.herocraft.max_steel.overloaded").withStyle(ChatFormatting.RED)
				: Component.translatable(now < s.combatUntil
						? "hud.herocraft.max_steel.in_combat" : "hud.herocraft.max_steel.ready")
						.withStyle(now < s.combatUntil ? ChatFormatting.RED : ChatFormatting.GREEN);
		int sw = mc.font.width(status);
		g.drawString(mc.font, modeName, x0, y0 - 20, CYAN_DIM, false);
		g.drawString(mc.font, status, x0 + totalW - sw, y0 - 20, 0xFFFFFFFF, false);

		// suit-up / suit-down progress cue
		if (s.transformDir != MaxSteelState.DIR_IDLE) {
			long elapsed = now - s.transformStartTick;
			float p = Math.max(0f, Math.min(1f, (float) elapsed / Math.max(1, s.transformDurationTicks)));
			g.fill(x0, y0 - 24, x0 + Math.round(totalW * p), y0 - 22, 0xFFFFFFFF);
		}

		renderCannonCharge(g, mc, player, x0, y0, totalW);

		// directional threat marker
		if (now < warningUntil) {
			float rel = net.minecraft.util.Mth.wrapDegrees(warningYaw - player.getYRot());
			int cx = g.guiWidth() / 2;
			int cy = g.guiHeight() / 2;
			double rad = Math.toRadians(rel);
			int mx = cx + (int) (Math.sin(rad) * 40);
			int my = cy - (int) (Math.cos(rad) * 40);
			g.fill(mx - 3, my - 3, mx + 3, my + 3, ((now % 6) < 3) ? 0xFFFFDD33 : 0xFFFF6633);
		}
	}

	/**
	 * v0.9.3: the Turbo Cannon charge bar. Drawn only while the player is charging the cannon, sitting
	 * above the ability row / mode-name line in the bottom-right corner. Fills left-to-right as the
	 * 5-second charge window builds; turns gold and shows "FULL" once it is maxed.
	 */
	private static void renderCannonCharge(GuiGraphics g, Minecraft mc, Player player, int x0, int y0, int totalW) {
		int ticks = player.getAttachedOrElse(ModAttachments.MAX_STEEL_CANNON_CHARGE, 0);
		if (ticks <= 0) {
			return;
		}
		float ratio = Math.min(1f, ticks / (float) MaxSteelConfig.CANNON_MAX_CHARGE_TICKS);
		boolean full = ratio >= 1f;
		int h = 5;
		int y = y0 - 40;

		Component label = Component.translatable(full
				? "hud.herocraft.max_steel.cannon_full" : "hud.herocraft.max_steel.cannon_charge")
				.withStyle(full ? ChatFormatting.YELLOW : ChatFormatting.AQUA);
		g.drawCenteredString(mc.font, label, x0 + totalW / 2, y - 10, 0xFFFFFFFF);

		g.fill(x0 - 1, y - 1, x0 + totalW + 1, y + h + 1, BORDER);
		g.fill(x0, y, x0 + totalW, y + h, 0xAA071820);
		g.fill(x0, y, x0 + Math.round(totalW * ratio), y + h, full ? BORDER_ACTIVE : CYAN);
	}
}
