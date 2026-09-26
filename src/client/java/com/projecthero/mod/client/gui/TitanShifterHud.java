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
 * The Titan Shifter HUD (bottom-right). v0.12.34 layout (v0.12.35: the thin bars have no border, the energy bar is yellow):
 *
 * <pre>
 *   Base form:   Titan Shifter / Titan Energy NN% + thin bar / "Titan Shift Ready [H]" (only at 100%)
 *   Titan form:  Titan Shifter / the six ability keys (R G X Z V C) / Titan HP + thin bar / "Revert Form [H]"
 * </pre>
 *
 * Shift+C (Hardening) and N (grab / bite / set down) are deliberately not drawn as keys. Hold Left-Alt for the move names.
 */
public final class TitanShifterHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int BAR_H = 3;
	private static final int COLOR_BOX_BG = 0xC0121212;
	private static final int COLOR_BORDER = 0xFF3A3A3A;
	private static final int COLOR_COOLDOWN = 0xB0000000;
	private static final int COLOR_KEY = 0xFFD8D8D8;
	private static final int COLOR_NAME = 0xFFE0E0E0;
	private static final int COLOR_ENERGY = 0xFFFFD53A; // v0.12.35: yellow, and it stays yellow when full

	/** The six boxes in slot order (R G X Z V C). */
	private static final String[] NAME_KEYS = {
			"projecthero.titan_shifter.ability.punch", "projecthero.titan_shifter.ability.heavy_smash",
			"projecthero.titan_shifter.ability.leap", "projecthero.titan_shifter.ability.stomp",
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
		boolean inside = phase == TitanPhase.TITAN;
		int totalW = BOX * 6 + GAP * 5;
		int x0 = g.guiWidth() - MARGIN - totalW;
		Component key = ModKeyBindings.POWER_SELECT.getTranslatedKeyMessage();

		// total height of the block, so it can be stacked upward from the bottom edge
		int height = 11; // title
		if (inside) {
			height += BOX + 3 + 11 + BAR_H + 3 + 11; // keys, HP label + bar, revert line
		} else {
			height += 11 + BAR_H + 3 + 11; // energy label + bar, ready / status line
		}
		int y = g.guiHeight() - 12 - height;

		g.drawString(mc.font, Component.translatable("hud.projecthero.titan_shifter.title")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), x0, y, 0xFFFFAA00, true);
		y += 11;

		if (inside) {
			boolean expanded = org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(),
					org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
			for (int i = 0; i < 6; i++) {
				int x = x0 + i * (BOX + GAP);
				g.fill(x, y, x + BOX, y + BOX, COLOR_BOX_BG);
				g.renderOutline(x, y, BOX, BOX, COLOR_BORDER);
				AbilitySlot slot = AbilitySlot.byNumber(i + 1);
				String id = TitanShifterAbilityManager.abilityIdOf(slot);
				boolean active = (TitanAbilities.REGEN.equals(id) && s.regenUntil > now)
						|| (TitanAbilities.HARDEN.equals(id) && s.hardenUntil > now);
				int cd = TitanShifter.cooldownRemaining(mc.player, id);
				int max = TitanShifterAbilityManager.maxCooldown(id);
				if (active) {
					g.fill(x + 1, y + 1, x + BOX - 1, y + BOX - 1, 0x8040E060);
				}
				if (cd > 0 && max > 0) {
					int h = (int) (BOX * Math.min(1f, cd / (float) max));
					g.fill(x, y + BOX - h, x + BOX, y + BOX, COLOR_COOLDOWN);
					g.drawString(mc.font, String.valueOf((cd + 19) / 20), x + 5, y + 6, 0xFFFFFFFF, true);
				} else {
					g.drawString(mc.font, String.valueOf(slot.defaultKey()), x + 7, y + 6, COLOR_KEY, true);
				}
				if (expanded) {
					Component name = Component.translatable(NAME_KEYS[i]);
					g.drawString(mc.font, name, x0 - 8 - mc.font.width(name), y + i * 10 - 30, COLOR_NAME, true);
				}
			}
			y += BOX + 3;

			// Titan HP: label + thin bar (live from the Titan being ridden, else the synced mirror)
			float hp = s.titanHealth;
			float max = s.titanMaxHealth;
			if (mc.player.getVehicle() instanceof TitanFormEntity form) {
				hp = form.getHealth();
				max = form.getMaxHealth();
			}
			float frac = max > 0f ? Math.max(0f, Math.min(1f, hp / max)) : 0f;
			boolean hard = s.hardenUntil > now;
			boolean regen = s.regenUntil > now;
			int fill = hard ? 0xFF5AB0FF : regen ? 0xFF40E060 : frac > 0.5f ? 0xFFE0A020 : frac > 0.25f ? 0xFFE06020 : 0xFFD02020;
			Component hpLabel = Component.translatable("hud.projecthero.titan_shifter.hp");
			g.drawString(mc.font, hpLabel, x0, y, 0xFFE8E8E8, true);
			g.drawString(mc.font, " " + (int) Math.ceil(hp) + " / " + (int) Math.ceil(max), x0 + mc.font.width(hpLabel), y, 0xFFFFFFFF, true);
			y += 11;
			thinBar(g, x0, y, totalW, frac, fill);
			y += BAR_H + 3;
			g.drawString(mc.font, Component.translatable("hud.projecthero.titan_shifter.revert", key).withStyle(ChatFormatting.YELLOW),
					x0, y, 0xFFFFFFFF, true);
			return;
		}

		// base form: Titan Energy as a percentage on a thin bar
		double eMax = TitanShifterConfig.energy().max;
		float eFrac = (float) Math.max(0.0, Math.min(1.0, s.energy / eMax));
		int pct = (int) Math.floor(eFrac * 100.0f + 1.0e-3f);
		boolean full = eFrac + 1.0e-4f >= 1.0f;
		g.drawString(mc.font, Component.translatable("hud.projecthero.titan_shifter.energy_label"), x0, y, 0xFFE8E8E8, true);
		String pctText = pct + "%";
		g.drawString(mc.font, pctText, x0 + totalW - mc.font.width(pctText), y, 0xFFFFFFFF, true);
		y += 11;
		thinBar(g, x0, y, totalW, eFrac, COLOR_ENERGY);
		y += BAR_H + 3;

		Component status = switch (phase) {
			case HUMAN -> {
				int cd = TitanShifter.transformCooldownRemaining(mc.player);
				if (cd > 0) {
					yield Component.translatable("hud.projecthero.titan_shifter.cooldown", (cd + 19) / 20).withStyle(ChatFormatting.RED);
				}
				// only at a full bar
				yield full ? Component.translatable("hud.projecthero.titan_shifter.ready", key).withStyle(ChatFormatting.GREEN) : null;
			}
			case TRANSFORMING -> Component.translatable("hud.projecthero.titan_shifter.transforming").withStyle(ChatFormatting.GOLD);
			case TITAN -> null;
			case REVERTING -> Component.translatable("hud.projecthero.titan_shifter.reverting").withStyle(ChatFormatting.GRAY);
			case DEFEATED -> Component.translatable("hud.projecthero.titan_shifter.defeated").withStyle(ChatFormatting.DARK_RED);
			case RECOVERING -> Component.translatable("hud.projecthero.titan_shifter.recovering",
					(int) Math.max(0, (s.phaseUntil - now + 19) / 20)).withStyle(ChatFormatting.GRAY);
		};
		if (status != null) {
			g.drawString(mc.font, status, x0, y, 0xFFFFFFFF, true);
		}
	}

	private static void thinBar(GuiGraphics g, int x, int y, int w, float frac, int color) {
		g.fill(x, y, x + w, y + BAR_H, COLOR_BOX_BG);
		g.fill(x, y, x + (int) (w * frac), y + BAR_H, color);
	}
}
