package com.projecthero.mod.client.nova;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.nova.Nova;
import com.projecthero.mod.nova.NovaAbilityManager;
import com.projecthero.mod.nova.NovaConfig;
import com.projecthero.mod.nova.data.NovaState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

/**
 * v0.17.3: the Nova HUD on the Minecraft-style look of the newest powers ({@link VanillaHud.Stack}, bottom-right):
 * <pre>
 *   Flying  Overload ready                     status tags (unsuited: "H: suit up")
 *   NOVA                                       the title (gold; dull while unsuited)
 *   Slow     [Hairline ====----]  41s          the 60 s of half-speed refill after an Overload, draining
 *   Force    [Slab =========---]  80           Nova Force in the Slab style it always had (dim while refilling slowly);
 *                                              "Overload" + seconds, full and pulsing, while the Overload runs
 *   +----+----+----+----+----+----+            R G Z X C V: shown move's cooldown (sweep + seconds), the Shift move's as the
 *   | R  | G  | Z  | X  | C  | V  |            durability bar; greyed while unsuited or short of Nova Force; lit while the
 *   +----+----+----+----+----+----+            blast / slam / shield (or Overload) / gravity well runs; yellow while Shift
 * </pre>
 * Holding Shift lists the Shift moves above the HUD (as before); Left-Alt lists every move. No H / N boxes: H is the suit, and
 * the helmet's Shift + hold N has no box of its own (it never had one).
 */
public final class NovaHud {
	private static final int GOLD = 0xFFFFC83C;
	/** The bar while it refills at half speed after an Overload. */
	private static final int GOLD_DIM = 0xFF8C7232;
	private static final int CYAN = 0xFF8BF8FF;
	private static final int SLOW = 0xFFB04A2A;

	private NovaHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.options.hideGui || !Nova.hasPower(p)) {
			return;
		}
		NovaState s = p.getAttachedOrElse(ModAttachments.NOVA_STATE, null);
		if (s == null) {
			return;
		}
		var exp = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return; // a selected mutation owns the keys and draws its own HUD
		}
		long now = mc.level.getGameTime();
		boolean suited = s.suited;
		boolean overload = suited && s.overloadUntil > now;
		long win = mc.getWindow().getWindow();
		boolean shift = p.isShiftKeyDown() || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;

		VanillaHud.Stack st = VanillaHud.Stack.bottomRight(g, VanillaHud.ORDER.length).keys(VanillaHud.ORDER.length);
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			String[] ids = NovaAbilityManager.idsOf(slot);
			if (ids.length == 0) {
				continue;
			}
			String main = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			boolean active = suited && switch (slot) {
				case SLOT_1 -> s.blasting;
				case SLOT_4 -> s.shieldUntil > now || overload;
				case SLOT_6 -> s.wellUntil > now;
				case SLOT_2 -> s.slamming;
				default -> false;
			};
			int cd = Nova.cooldownRemaining(p, main);
			float frac = cd / (float) Math.max(1, NovaAbilityManager.maxCooldown(main));
			float otherFrac = other == null ? 0f : Nova.cooldownRemaining(p, other) / (float) Math.max(1, NovaAbilityManager.maxCooldown(other));
			boolean usable = suited && (overload || s.force + 1.0e-3f >= NovaAbilityManager.minForce(main));
			st.slot(i, VanillaHud.keyLabel(slot), usable || active, shift, suited ? Math.min(1f, frac) : 0f, suited ? VanillaHud.seconds(cd) : 0,
					suited ? Math.min(1f, otherFrac) : 0f, active);
		}

		// Nova Force (Slab): infinite (a full, pulsing bar) while the Overload runs; dimmed while it refills at half speed after one
		int slow = Nova.slowRegenRemaining(p);
		if (overload) {
			int pulse = pulse(now, mc);
			st.meter(Component.translatable("hud.projecthero.nova.overload_label"), 1f, pulse, VanillaHud.seconds((int) (s.overloadUntil - now)) + "s", pulse,
					MutationMeters.Style.SLAB);
		} else {
			int c = slow > 0 ? GOLD_DIM : GOLD;
			st.meter(Component.translatable("hud.projecthero.nova.force_label"), s.force / NovaConfig.FORCE_MAX, c, String.valueOf((int) Math.floor(s.force)),
					s.force >= NovaConfig.FORCE_MAX - 0.01f ? CYAN : GOLD, MutationMeters.Style.SLAB);
		}
		if (slow > 0 && !overload) {
			// the half-speed refill drains away
			st.meter(Component.translatable("hud.projecthero.nova.slow_label"), slow / (float) NovaConfig.OVERLOAD_SLOW_REGEN_TICKS, SLOW,
					VanillaHud.seconds(slow) + "s", VanillaHud.RED, MutationMeters.Style.HAIRLINE);
		}
		st.title(Component.translatable("hud.projecthero.nova.title").withStyle(ChatFormatting.BOLD), suited ? GOLD : 0xFFB0A080);

		List<VanillaHud.Tag> tags = new ArrayList<>();
		if (!suited) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.nova.suit_hint").getString(), VanillaHud.GREY));
		} else {
			if (s.flying) {
				tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.nova.flying").getString(), CYAN));
			}
			if (!overload && s.force >= NovaConfig.FORCE_MAX - 0.01f) {
				tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.nova.overload_ready").getString(), CYAN));
			}
		}
		st.tags(tags);

		if (MoveListOverlay.held()) {
			// Left-Alt: the shared move list (plain move + Shift move per key)
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : VanillaHud.ORDER) {
				String[] ids = NovaAbilityManager.idsOf(slot);
				if (ids.length > 0) {
					rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.nova.ability." + ids[0]),
							ids.length > 1 ? Component.translatable("projecthero.nova.ability." + ids[1]) : null));
				}
			}
			st.moveList(rows);
		} else if (shift) {
			// Shift: the Shift moves' names above the HUD (headed "Shift moves", as before)
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			rows.add(MoveListOverlay.Row.of("", Component.translatable("hud.projecthero.nova.shift").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), null));
			for (AbilitySlot slot : VanillaHud.ORDER) {
				String[] ids = NovaAbilityManager.idsOf(slot);
				if (ids.length > 1) {
					rows.add(MoveListOverlay.Row.of(VanillaHud.keyLabel(slot), Component.translatable("projecthero.nova.ability." + ids[1]), null));
				}
			}
			MoveListOverlay.draw(g, st.x0 + st.width, st.top() - 4, rows);
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
}
