package com.projecthero.mod.client.nova;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.nova.Nova;
import com.projecthero.mod.nova.NovaAbilityManager;
import com.projecthero.mod.nova.NovaConfig;
import com.projecthero.mod.nova.data.NovaState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

/**
 * v0.15.13: the Nova HUD (bottom-right):
 * <pre>
 *   NOVA                       the name (OVERLOAD + seconds left while it runs)
 *   [ Nova Force: 80 / 100 ]   a Slab bar (9 px, 1 px border, the numbers inside)
 *   [R] [G] [Z] [X] [C] [V]    the six keys
 * </pre>
 * Each box shows its tap move's cooldown -- or, while Shift is held, its Shift move's (a gold dot marks the Shift set) --
 * and a thin strip along its bottom for the other move on that key (bright when ready). Holding Shift or Left-Alt lists
 * the moves above the boxes. No H / N boxes: H is the suit, N does nothing. Unsuited, the keys are dimmed and a hint says
 * H puts the uniform on.
 */
public final class NovaHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int SLAB_H = 9;

	private static final int GOLD = 0xFFFFC83C;
	private static final int GOLD_DARK = 0xFF8A6418;
	private static final int CYAN = 0xFF8BF8FF;
	private static final int BOX_BG = 0xC0101418;
	private static final int BOX_BG_SHIFT = 0xC0201808;
	private static final int BORDER = 0xFF6A5420;
	private static final int BORDER_SHIFT = 0xFFB07A2A;
	private static final int BORDER_ACTIVE = 0xFFFFE27A;
	private static final int KEY = 0xFFF2E2C0;
	private static final int KEY_DIM = 0xFF6A6A6A;

	/** Boxes left to right: R G Z X C V (slots 1, 2, 4, 3, 6, 5). */
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };
	private static final String[] LETTERS = { "R", "G", "Z", "X", "C", "V" };

	private NovaHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui || !Nova.hasPower(mc.player)) {
			return;
		}
		NovaState s = mc.player.getAttachedOrElse(ModAttachments.NOVA_STATE, null);
		if (s == null) {
			return;
		}
		var exp = mc.player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return; // a selected mutation owns the keys and draws its own HUD
		}
		long now = mc.level.getGameTime();
		boolean suited = s.suited;
		boolean overload = suited && s.overloadUntil > now;
		int totalW = BOX * SLOTS.length + GAP * (SLOTS.length - 1);
		int x0 = g.guiWidth() - MARGIN - totalW;
		int y0 = g.guiHeight() - MARGIN - BOX;
		if (x0 < g.guiWidth() / 2 + 91 + 4) {
			y0 -= 40; // a narrow GUI: sit above the hotbar, health and food rows instead of on top of them
		}

		// ---- the Slab bar
		int sy = y0 - 3 - SLAB_H;
		float ratio = Math.max(0f, Math.min(1f, s.force / NovaConfig.FORCE_MAX));
		g.fill(x0, sy, x0 + totalW, sy + SLAB_H, GOLD_DARK);
		g.fill(x0 + 1, sy + 1, x0 + totalW - 1, sy + SLAB_H - 1, 0xE0141008);
		int fillW = Math.round((totalW - 2) * ratio);
		int fill = overload ? pulse(now, mc) : GOLD;
		if (fillW > 0) {
			g.fill(x0 + 1, sy + 1, x0 + 1 + fillW, sy + SLAB_H - 1, fill);
			g.fill(x0 + 1, sy + 1, x0 + 1 + fillW, sy + 2, 0x60FFFFFF); // a highlight along the top
		}
		if (s.force >= NovaConfig.FORCE_MAX - 0.01f && !overload) {
			g.renderOutline(x0, sy, totalW, SLAB_H, CYAN); // full: the Overload is ready
		}
		Component text = overload
				? Component.translatable("hud.projecthero.nova.overload", (s.overloadUntil - now + 19) / 20)
				: Component.translatable("hud.projecthero.nova.force", (int) Math.floor(s.force), (int) NovaConfig.FORCE_MAX);
		float scale = 0.75f;
		int tw = Math.round(mc.font.width(text) * scale);
		g.pose().pushPose();
		g.pose().translate(x0 + (totalW - tw) / 2f, sy + 2f, 0);
		g.pose().scale(scale, scale, 1f);
		g.drawString(mc.font, text, 0, 0, 0xFFFFFFFF, true);
		g.pose().popPose();

		// ---- the name
		Component name = Component.translatable("hud.projecthero.nova.title").withStyle(ChatFormatting.BOLD);
		g.drawString(mc.font, name, x0, sy - 10, suited ? GOLD : 0xFFB0A080, true);
		if (!suited) {
			Component hint = Component.translatable("hud.projecthero.nova.suit_hint");
			g.drawString(mc.font, hint, x0 + totalW - mc.font.width(hint), sy - 10, 0xFF9A9A9A, true);
		} else if (s.flying) {
			Component fly = Component.translatable("hud.projecthero.nova.flying");
			g.drawString(mc.font, fly, x0 + totalW - mc.font.width(fly), sy - 10, CYAN, true);
		}

		// ---- the keys
		long win = mc.getWindow().getWindow();
		boolean alt = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;
		boolean shift = mc.player.isShiftKeyDown() || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			int x = x0 + i * (BOX + GAP);
			String[] ids = NovaAbilityManager.idsOf(slot);
			String main = shift ? ids[1] : ids[0];
			String other = shift ? ids[0] : ids[1];
			boolean active = suited && switch (slot) {
				case SLOT_1 -> s.blasting;
				case SLOT_4 -> s.shieldUntil > now || overload;
				case SLOT_6 -> s.wellUntil > now;
				case SLOT_2 -> s.slamming;
				default -> false;
			};
			g.fill(x, y0, x + BOX, y0 + BOX, shift ? BOX_BG_SHIFT : BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, active ? BORDER_ACTIVE : (shift ? BORDER_SHIFT : BORDER));
			g.drawString(mc.font, keyLabel(slot), x + 2, y0 + 2, suited ? KEY : KEY_DIM, false);
			if (shift) {
				g.fill(x + BOX - 5, y0 + 2, x + BOX - 2, y0 + 5, GOLD); // a dot: these are the Shift moves
			}
			int cd = Nova.cooldownRemaining(mc.player, main);
			int max = Math.max(1, NovaAbilityManager.maxCooldown(main));
			if (!suited) {
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, 0x90000000);
			} else if (cd > 0) {
				int h = (int) ((BOX - 2) * Math.min(1f, cd / (float) max));
				g.fill(x + 1, y0 + BOX - 1 - h, x + BOX - 1, y0 + BOX - 1, 0xB0000000);
				g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2 + 2, y0 + 10, 0xFFFFFFFF);
			} else if (!overload && s.force + 1.0e-3f < NovaAbilityManager.cost(main) * (main.equals(com.projecthero.mod.nova.NovaAbilities.BLAST) ? 0.5f : 1f)) {
				g.fill(x + 1, y0 + BOX - 3, x + BOX - 1, y0 + BOX - 1, 0xFF7A2A2A); // not enough Nova Force
			}
			int ocd = Nova.cooldownRemaining(mc.player, other);
			int omax = Math.max(1, NovaAbilityManager.maxCooldown(other));
			float ready = 1.0f - Math.min(1.0f, ocd / (float) omax);
			if (suited) {
				g.fill(x + 1, y0 + BOX - 3, x + 1 + Math.round((BOX - 2) * ready), y0 + BOX - 1, ocd > 0 ? 0xFF6A4A1A : 0xFFFFD27A);
			}
		}
		if (shift || alt) {
			int right = x0 + totalW;
			int lineY = sy - 14 - 6 * 10;
			if (shift) {
				Component head = Component.translatable("hud.projecthero.nova.shift").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
				g.drawString(mc.font, head, right - mc.font.width(head), lineY - 11, 0xFFFFFFFF, true);
			}
			for (int i = 0; i < SLOTS.length; i++) {
				String[] ids = NovaAbilityManager.idsOf(SLOTS[i]);
				String id = shift ? ids[1] : ids[0];
				Component line = Component.literal(LETTERS[i] + " ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.nova.ability." + id).withStyle(ChatFormatting.WHITE));
				g.drawString(mc.font, line, right - mc.font.width(line), lineY + i * 10, 0xFFFFFFFF, true);
			}
		}
	}

	private static int pulse(long now, Minecraft mc) {
		float t = (now + mc.getTimer().getGameTimeDeltaPartialTick(false)) * 0.3f;
		float k = 0.5f + 0.5f * (float) Math.sin(t);
		int r = Math.round(255 * (1 - k) + 139 * k);
		int gg = Math.round(200 * (1 - k) + 248 * k);
		int b = Math.round(60 * (1 - k) + 255 * k);
		return 0xFF000000 | (r << 16) | (gg << 8) | b;
	}

	/** The key actually bound to the slot (tracks rebinds), at most two characters. */
	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}
}
