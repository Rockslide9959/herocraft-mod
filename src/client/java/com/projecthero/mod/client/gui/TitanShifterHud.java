package com.projecthero.mod.client.gui;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.titanshifter.TitanAbilities;
import com.projecthero.mod.titanshifter.TitanPhase;
import com.projecthero.mod.titanshifter.TitanShifter;
import com.projecthero.mod.titanshifter.TitanShifterAbilityManager;
import com.projecthero.mod.titanshifter.TitanShifterConfig;
import com.projecthero.mod.titanshifter.data.TitanShifterState;
import com.projecthero.mod.titanshifter.entity.TitanFormEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The Titan Shifter HUD (v0.12.31), laid out like {@link WolverineHud}: bottom-right, the six ability boxes with
 * their keys and cooldowns (hold Left-Alt for the move names) plus a Hardening box, the Titan health bar and the
 * phase / transformation-cooldown line. Outside the Titan it is just the status line and the Titan Shift key.
 */
public final class TitanShifterHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int COLOR_BOX_BG = 0xC0121212;
	private static final int COLOR_BORDER = 0xFF3A3A3A;
	private static final int COLOR_COOLDOWN = 0xB0000000;
	private static final int COLOR_KEY = 0xFFD8D8D8;
	private static final int COLOR_NAME = 0xFFE0E0E0;

	private static final String[] NAME_KEYS = {
			"projecthero.titan_shifter.ability.punch", "projecthero.titan_shifter.ability.heavy_smash",
			"projecthero.titan_shifter.ability.stomp", "projecthero.titan_shifter.ability.leap",
			"projecthero.titan_shifter.ability.roar", "projecthero.titan_shifter.ability.regeneration"
	};

	private TitanShifterHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || mc.level == null || !TitanShifter.isShifter(mc.player)) {
			return;
		}
		TitanShifterState s = mc.player.getAttachedOrElse(ModAttachments.TITAN_SHIFTER_STATE, null);
		if (s == null) {
			return;
		}
		long now = mc.level.getGameTime();
		TitanPhase phase = s.phase();
		int totalW = BOX * 7 + GAP * 6;
		int x0 = g.guiWidth() - MARGIN - totalW;
		int y0 = g.guiHeight() - BOX - MARGIN - 26 - 24;
		boolean expanded = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
				org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
		boolean inside = phase == TitanPhase.TITAN;

		if (inside) {
			for (int i = 0; i < 7; i++) {
				int x = x0 + i * (BOX + GAP);
				g.fill(x, y0, x + BOX, y0 + BOX, COLOR_BOX_BG);
				g.renderOutline(x, y0, BOX, BOX, COLOR_BORDER);
				String id;
				String key;
				String nameKey;
				if (i < 6) {
					AbilitySlot slot = AbilitySlot.byNumber(i + 1);
					id = TitanShifterAbilityManager.abilityIdOf(slot);
					key = String.valueOf(slot.defaultKey());
					nameKey = i == 5 && TitanShifterConfig.controls().slot6IsHardening
							? "projecthero.titan_shifter.ability.hardening" : NAME_KEYS[i];
				} else {
					// Utility 1 (H): the other of Regeneration / Hardening
					id = TitanShifterConfig.controls().slot6IsHardening ? TitanAbilities.REGEN : TitanAbilities.HARDEN;
					key = "H";
					nameKey = TitanShifterConfig.controls().slot6IsHardening
							? "projecthero.titan_shifter.ability.regeneration" : "projecthero.titan_shifter.ability.hardening";
				}
				boolean active = (TitanAbilities.HARDEN.equals(id) && s.hardenUntil > now)
						|| (TitanAbilities.REGEN.equals(id) && s.regenUntil > now);
				int cd = TitanShifter.cooldownRemaining(mc.player, id);
				int max = TitanShifterAbilityManager.maxCooldown(id);
				if (active) {
					g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, TitanAbilities.HARDEN.equals(id) ? 0x805AB0FF : 0x8040E060);
				}
				if (cd > 0 && max > 0) {
					int h = (int) (BOX * Math.min(1f, cd / (float) max));
					g.fill(x, y0 + BOX - h, x + BOX, y0 + BOX, COLOR_COOLDOWN);
					g.drawString(mc.font, String.valueOf((cd + 19) / 20), x + 5, y0 + 6, 0xFFFFFFFF, true);
				} else {
					g.drawString(mc.font, key, x + 7, y0 + 6, COLOR_KEY, true);
				}
				if (expanded) {
					Component name = Component.translatable(nameKey);
					g.drawString(mc.font, name, x0 - 8 - mc.font.width(name), y0 + i * 10 - 62, COLOR_NAME, true);
				}
			}
		}

		int line = y0 + BOX + 2;
		g.drawString(mc.font, Component.translatable("hud.projecthero.titan_shifter.title")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), x0, line, 0xFFFFAA00, true);
		line += 10;

		// health bar above the ability boxes (live from the Titan the player is riding, else the synced mirror)
		float hp = s.titanHealth;
		float max = s.titanMaxHealth;
		if (mc.player.getVehicle() instanceof TitanFormEntity form) {
			hp = form.getHealth();
			max = form.getMaxHealth();
		}
		if (phase.insideForm() && max > 0f) {
			float frac = Math.max(0f, Math.min(1f, hp / max));
			boolean hard = s.hardenUntil > now;
			boolean regen = s.regenUntil > now;
			int fill = hard ? 0xFF5AB0FF : regen ? 0xFF40E060 : frac > 0.5f ? 0xFFE0A020 : frac > 0.25f ? 0xFFE06020 : 0xFFD02020;
			int by = y0 - 12;
			g.fill(x0, by, x0 + totalW, by + 9, COLOR_BOX_BG);
			g.fill(x0, by, x0 + (int) (totalW * frac), by + 9, fill);
			g.renderOutline(x0 - 1, by - 1, totalW + 2, 11, COLOR_BORDER);
			Component txt = Component.translatable("hud.projecthero.titan_shifter.health", (int) Math.ceil(hp), (int) max);
			g.drawString(mc.font, txt, x0 + (totalW - mc.font.width(txt)) / 2, by + 1, 0xFFFFFFFF, true);
		}

		Component status = switch (phase) {
			case HUMAN -> {
				int cd = TitanShifter.transformCooldownRemaining(mc.player);
				yield cd > 0
						? Component.translatable("hud.projecthero.titan_shifter.cooldown", (cd + 19) / 20).withStyle(ChatFormatting.RED)
						: Component.translatable("hud.projecthero.titan_shifter.ready", ModKeyBindings.TITAN_SHIFT.getTranslatedKeyMessage())
								.withStyle(ChatFormatting.GREEN);
			}
			case TRANSFORMING -> Component.translatable("hud.projecthero.titan_shifter.transforming").withStyle(ChatFormatting.GOLD);
			case TITAN -> Component.translatable("hud.projecthero.titan_shifter.titan", ModKeyBindings.TITAN_SHIFT.getTranslatedKeyMessage())
					.withStyle(ChatFormatting.YELLOW);
			case REVERTING -> Component.translatable("hud.projecthero.titan_shifter.reverting").withStyle(ChatFormatting.GRAY);
			case DEFEATED -> Component.translatable("hud.projecthero.titan_shifter.defeated").withStyle(ChatFormatting.DARK_RED);
			case RECOVERING -> Component.translatable("hud.projecthero.titan_shifter.recovering",
					(int) Math.max(0, (s.phaseUntil - now + 19) / 20)).withStyle(ChatFormatting.GRAY);
		};
		g.drawString(mc.font, status, x0, line, 0xFFFFFFFF, true);
		line += 10;
		if (phase == TitanPhase.HUMAN) {
			int cd = TitanShifter.transformCooldownRemaining(mc.player);
			if (cd > 0) {
				float frac = Math.min(1f, cd / (float) TitanShifterConfig.transformation().cooldownTicks);
				g.fill(x0, line, x0 + totalW, line + 3, COLOR_BOX_BG);
				g.fill(x0, line, x0 + (int) (totalW * (1f - frac)), line + 3, 0xFFE08A00);
			}
		}
	}
}
