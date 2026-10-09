package com.projecthero.mod.client.gui;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.wolverine.Wolverine;
import com.projecthero.mod.wolverine.WolverineAbilityManager;
import com.projecthero.mod.wolverine.WolverineConfig;
import com.projecthero.mod.wolverine.data.WolverineState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The Wolverine HUD (v0.12.1): the six abilities with their keys + cooldowns, laid out exactly like
 * {@link PunisherHud} / {@link AbilityHud} (bottom-right; hold Left-Alt for the move names), plus the
 * "WOLVERINE" label with the claw state and a Rage timer. Only drawn while the player is Wolverine.
 */
public final class WolverineHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int COLOR_BOX_BG = 0xC0121212;
	private static final int COLOR_BORDER = 0xFF3A3A3A;
	private static final int COLOR_COOLDOWN = 0xB0000000;
	private static final int COLOR_KEY = 0xFFD8D8D8;
	private static final int COLOR_NAME = 0xFFE0E0E0;

	private static final String[] NAME_KEYS = {
			"projecthero.wolverine.ability.claw_slash", "projecthero.wolverine.ability.cross_slash",
			"projecthero.wolverine.ability.claw_dash", "projecthero.wolverine.ability.adamantium_execution",
			"projecthero.wolverine.ability.frenzy", "projecthero.wolverine.ability.berserker_rage"
	};

	private WolverineHud() {
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
		long now = mc.level.getGameTime();
		int totalW = BOX * 6 + GAP * 5;
		int x0 = g.guiWidth() - MARGIN - totalW;
		int y0 = g.guiHeight() - BOX - MARGIN - 26 - 24;
		// v0.15.18: the skin-recovery readout (wrapped text + thin bar) pushes the whole panel up so nothing falls off the screen
		float skin = Math.max(0f, Math.min(1f, s.skinRecovery));
		boolean skinPaused = mc.player.getHealth() < mc.player.getMaxHealth();
		java.util.List<net.minecraft.util.FormattedCharSequence> skinRows = s.skinRecovery < 1.0f
				? mc.font.split(Component.translatable(skinPaused ? "hud.projecthero.wolverine.skin_paused" : "hud.projecthero.wolverine.skin",
						(int) Math.floor(skin * 100.0f + 1.0e-3f)).withStyle(skinPaused ? ChatFormatting.RED : ChatFormatting.GOLD), totalW)
				: java.util.List.of();
		if (!skinRows.isEmpty()) {
			y0 -= skinRows.size() * 9 + 6;
		}
		boolean expanded = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
				org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

		for (int i = 0; i < 6; i++) {
			int x = x0 + i * (BOX + GAP);
			g.fill(x, y0, x + BOX, y0 + BOX, COLOR_BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, COLOR_BORDER);
			AbilitySlot slot = AbilitySlot.byNumber(i + 1);
			if (i == 5) {
				// Berserker Rage has no cooldown: the box fills with the rage bar (or drains with the rage)
				boolean burning = s.rageUntil > now;
				float frac = burning ? (s.rageUntil - now) / (float) WolverineConfig.RAGE_TICKS
						: s.rageMeter / WolverineConfig.RAGE_BAR_MAX;
				int h = (int) (BOX * Math.max(0f, Math.min(1f, frac)));
				g.fill(x + 1, y0 + BOX - h, x + BOX - 1, y0 + BOX - 1, burning ? 0xA0FF3030 : (frac >= 1f ? 0xA0FF6A00 : 0x70A02020));
				g.drawString(mc.font, String.valueOf(slot.defaultKey()), x + 7, y0 + 6, frac >= 1f || burning ? 0xFFFFFFFF : COLOR_KEY, true);
				if (expanded) {
					Component name = Component.translatable(NAME_KEYS[i]);
					g.drawString(mc.font, name, x0 - 8 - mc.font.width(name), y0 + i * 10 - 52, COLOR_NAME, true);
				}
				continue;
			}
			int cd = Wolverine.cooldownRemaining(mc.player, WolverineAbilityManager.abilityIdOf(slot));
			int max = WolverineAbilityManager.maxCooldown(slot);
			if (cd > 0 && max > 0) {
				int h = (int) (BOX * Math.min(1f, cd / (float) max));
				g.fill(x, y0 + BOX - h, x + BOX, y0 + BOX, COLOR_COOLDOWN);
				g.drawString(mc.font, String.valueOf((cd + 19) / 20), x + 5, y0 + 6, 0xFFFFFFFF, true);
			} else {
				g.drawString(mc.font, String.valueOf(slot.defaultKey()), x + 7, y0 + 6, COLOR_KEY, true);
			}
			if (expanded) {
				Component name = Component.translatable(NAME_KEYS[i]);
				int tw = mc.font.width(name);
				g.drawString(mc.font, name, x0 - 8 - tw, y0 + i * 10 - 52, COLOR_NAME, true);
			}
		}

		int line = y0 + BOX + 2;
		g.drawString(mc.font, Component.translatable("hud.projecthero.wolverine.title")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), x0, line, 0xFFFFAA00, true);
		Component claws = Component.translatable(s.clawsOut ? "hud.projecthero.wolverine.claws_deployed"
				: "hud.projecthero.wolverine.claws_retracted");
		line += 10;
		g.drawString(mc.font, claws, x0, line, s.clawsOut ? 0xFFE8E8E8 : 0xFF909090, true);
		line += 10;
		// v0.12.41: Healing Factor pool: "Healing Factor 80%", then [Death Surge pixel][1 px gap][pool bar]
		float pool = Math.max(0f, Math.min(WolverineConfig.HEAL_POOL_MAX, s.healPool));
		float pf = pool / WolverineConfig.HEAL_POOL_MAX;
		g.drawString(mc.font, Component.translatable("hud.projecthero.wolverine.healing",
				(int) Math.floor(pf * 100.0f + 1.0e-3f)).withStyle(pool <= 0f ? ChatFormatting.RED : ChatFormatting.GREEN),
				x0, line, 0xFF55FF77, true);
		line += 10;
		// v0.15.18: the Death Surge marker is lit while the pool can pay for a surge (30 Healing Factor)
		boolean surgeReady = com.projecthero.mod.wolverine.WolverinePassives.surgeAffordable(s);
		g.fill(x0, line, x0 + 3, line + 3, surgeReady ? 0xFFFF8A00 : 0xFF2A2A2A);
		int bx = x0 + 4; // 3 px Death Surge marker, 1 px gap
		int bw = totalW - 4;
		g.fill(bx, line, bx + bw, line + 3, COLOR_BOX_BG);
		g.fill(bx, line, bx + (int) (bw * pf), line + 3, pool <= 0f ? 0xFFB02020 : 0xFF3ADB5A);
		int costX = bx + (int) (bw * WolverineConfig.SURGE_HEAL_POOL_COST / WolverineConfig.HEAL_POOL_MAX);
		g.fill(costX, line, costX + 1, line + 3, 0xFFFF8A00); // a surge needs the pool past this tick
		line += 6;
		if (!skinRows.isEmpty()) {
			// v0.15.18: Death Surge skin recovery -- only advances at full HP ("Full HP to heal" while paused)
			for (var row : skinRows) {
				g.drawString(mc.font, row, x0, line, 0xFFFFAA00, true);
				line += 9;
			}
			line += 1;
			g.fill(x0, line, x0 + totalW, line + 2, COLOR_BOX_BG);
			g.fill(x0, line, x0 + (int) (totalW * skin), line + 2, skinPaused ? 0xFF8A5A40 : 0xFFE0A070);
			int half = x0 + (int) (totalW * WolverineConfig.SURGE_DEBUFF_UNTIL);
			g.fill(half, line, half + 1, line + 2, 0xFFFFFFFF); // past here the debuffs are gone
			line += 5;
		}
		if (s.chargeStartedAt != 0L) {
			float frac = Math.min(1f, (now - s.chargeStartedAt) / (float) WolverineConfig.EXECUTION_CHARGE_TICKS);
			g.drawString(mc.font, Component.translatable(frac >= 1f ? "hud.projecthero.wolverine.charge_ready"
					: "hud.projecthero.wolverine.charging", (int) (frac * 100)).withStyle(ChatFormatting.GOLD), x0, line, 0xFFFFAA00, true);
			line += 10;
			g.fill(x0, line, x0 + totalW, line + 3, COLOR_BOX_BG);
			g.fill(x0, line, x0 + (int) (totalW * frac), line + 3, frac >= 1f ? 0xFFFFD34A : 0xFFE08A00);
			line += 6;
		}
		if (s.rageUntil > now) {
			g.drawString(mc.font, Component.translatable("hud.projecthero.wolverine.rage",
					(int) ((s.rageUntil - now + 19) / 20)).withStyle(ChatFormatting.RED), x0, line, 0xFFFF5555, true);
			line += 10;
		}
	}
}
