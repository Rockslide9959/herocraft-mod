package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.data.GreenLanternFx;
import com.projecthero.mod.greenlantern.data.GreenLanternState;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * The Green Lantern HUD. v0.14.4: back to the simpler pre-v0.14.3 look the user preferred -- no framed panel, no
 * borders around anything but the key boxes themselves -- keeping what the v0.14.3 kit needs (v0.15.15: the H and N boxes are
 * gone again -- user rule, no H / N boxes -- and every box got a Shift-move cooldown bar like the Nova HUD's; the Oath
 * timer, Gatling / Missile Barrage / Giant Hand cooldowns and glows, taking the ring off). Bottom-right, stacking upward:
 * <pre>
 *   (Alt held: every key's move names, plain text)
 *   ■ Buzzsaw  12s                        one line per construct on cooldown
 *   SHIELD / DOME / BARRIER + thin bar     while a barrier is up or its meter refills
 *   TAKING OFF THE RING 60% + thin bar     while Sneak + N is held
 *   Buzzsaw                     35 charge  the selected construct
 *   Green Lantern             OATH 18s     title + Charging / Oath / Reciting / Flying / Boost
 *   [R][G][X][Z][V][C]                     tap-move cooldown shades the box, lit outline while a move runs,
 *                                          a small green bar along each box's bottom = its Shift move's cooldown
 *   ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬               Ring Charge, 3 px, no border, the emergency reserve marked
 *   87%
 * </pre>
 * Drawn whenever the player has the power at all -- the ring works unsuited, so the HUD is not suit-gated. Hidden while
 * the construct wheel is open. {@link #icon} and the atlas constants stay here because the wheel draws with them.
 */
public final class GreenLanternHud {
	public static final ResourceLocation ICONS = ProjectHeroMod.id("textures/gui/green_lantern/constructs.png");
	public static final int EMBLEM_ICON = 31;

	private static final int BOX = 24; // v0.15.15: six bigger keys (was eight at 20) -- room for the Shift bar
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	/** Vertical spacing between stacked label rows above the ability-key boxes. */
	private static final int LINE = 10;
	/** v0.15.15: six keys -- the H and N boxes are gone (user rule: no H / N boxes on the HUD). */
	private static final int KEYS = 6;

	private static final int GREEN = 0xFF35F075;
	private static final int GREEN_DIM = 0xFF1A7838;
	/** The selected construct's name -- a mid green that still reads over grass (v0.14.4; was the darker GREEN_DIM). */
	private static final int SELECTED = 0xFF5FD08A;
	private static final int LOW = 0xFFFF5A5A;
	private static final int GOLD = 0xFFFFD23A;
	private static final int BOX_BG = 0xC00A2412;
	private static final int BORDER = 0xFF1E661E;
	private static final int BORDER_ACTIVE = 0xFF35F075;
	private static final int COOLDOWN = 0xB0000000;
	private static final int KEY = 0xFFCFF8D4;
	private static final int TRACK = 0xAA0A2412;

	private static final String[] KEY_LABELS = {"R", "G", "X", "Z", "V", "C"};
	/** Alt names: {@code projecthero.guide.green_lantern.ability.<key>} per box. */
	private static final String[] KEY_NAMES = {
			"ring_bolt", "construct_fist", "oath", "shield", "giant_hand", "construct" // v0.15.15: V = Giant Hand
	};
	/** v0.15.15: the cooldown ids of each key's Shift move, shown as a small green bar along the bottom of its box. */
	private static final String[][] SHIFT_COOLDOWNS = {
			{"blast_wave"}, {"war_hammer_slam"}, {"emerald_gatling"}, {"protective_dome"}, {"ring_scan"}, {"missile_barrage"}
	};
	/** Shift bar colours: filling while it cools down, bright once ready. */
	private static final int SHIFT_BAR_COOLING = 0xFF1E8A44;
	private static final int SHIFT_BAR_READY = 0xFF5CFF8E;
	/** Longest cooldown seen per id since it last started (the bars' full length -- some moves set different lengths). */
	private static final java.util.Map<String, Integer> PEAK = new java.util.HashMap<>();

	private GreenLanternHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.options.hideGui || mc.level == null
				|| mc.screen instanceof GreenLanternConstructWheelScreen) {
			return;
		}
		GreenLanternState s = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		// The keys belong to Green Lantern only while no mutation is selected (GreenLanternAbilityManager.hasContext) --
		// mirror that here, or a bonded Lantern with a mutation active gets these boxes drawn on top of AbilityHud's.
		com.projecthero.mod.hero.data.ExperimentalState experimental =
				player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (experimental != null && !experimental.activePower.isEmpty()) {
			return;
		}
		long now = mc.level.getGameTime();
		float pt = delta.getGameTimeDeltaPartialTick(false);
		GreenLanternFx fx = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FX, GreenLanternFx.EMPTY);

		int totalW = KEYS * BOX + (KEYS - 1) * GAP;
		int x0 = g.guiWidth() - MARGIN - totalW;
		int right = x0 + totalW;
		int y0 = g.guiHeight() - MARGIN - BOX - 20;

		long oathUntil = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_OATH_UNTIL, 0L);
		boolean oathActive = oathUntil > now;
		boolean oathReciting = !oathActive && player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_OATH_RECITING_SINCE, 0L) > 0L;

		// ---- title row, just above the keys: the name, and what is running right now on the right
		int labelY = y0 - LINE;
		Component title = Component.translatable("projecthero.guide.green_lantern").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
		g.drawString(mc.font, title, x0, labelY, GREEN, true);
		Component status = status(player, oathActive, oathReciting, oathUntil, now);
		if (status != null && mc.font.width(title) + 6 + mc.font.width(status) <= totalW) {
			g.drawString(mc.font, status, right - mc.font.width(status), labelY, 0xFFFFFFFF, true);
		}
		labelY -= LINE;

		// ---- the selected construct, with its cost (or its cooldown) on the right
		ConstructType sel = ConstructType.byOrdinal(s.selectedConstruct);
		g.drawString(mc.font, Component.translatable(sel.translationKey()), x0, labelY, SELECTED, true);
		int selCd = GreenLanternConstructs.cooldownRemainingFor(player, sel);
		Component costLine = selCd > 0
				? Component.translatable("hud.projecthero.green_lantern.cooldown_short", (selCd + 19) / 20).withStyle(ChatFormatting.RED)
				: Component.translatable("hud.projecthero.green_lantern.cost", Math.round(sel.initialCost())).withStyle(ChatFormatting.GRAY);
		g.drawString(mc.font, costLine, right - mc.font.width(costLine), labelY, 0xFFFFFFFF, true);
		labelY -= LINE;

		// ---- the keys
		boolean barrierUp = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_HP, 0f) > 0f;
		for (int i = 0; i < KEYS; i++) {
			int x = x0 + i * (BOX + GAP);
			// glow while the key's move is running: beam, Oath / Gatling, shield / dome, suit, Giant Hand, ring removal
			boolean active = switch (i) {
				case 0 -> fx.has(GreenLanternFx.CH_BEAM);
				case 2 -> oathActive || oathReciting || fx.has(GreenLanternFx.CH_GATLING);
				case 3 -> barrierUp;
				case 4 -> fx.has(GreenLanternFx.CH_HAND); // v0.15.15: Giant Hand moved to V, the suit to H
				default -> false;
			};
			boolean flash = fx.anim() != GreenLanternFx.ANIM_NONE && now - fx.animStart() < 6 && animKey(fx.anim()) == i;
			g.fill(x, y0, x + BOX, y0 + BOX, BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, active || flash ? BORDER_ACTIVE : BORDER);
			g.drawString(mc.font, KEY_LABELS[i], x + 2, y0 + 2, KEY, false);

			if (i == 2 && oathActive) {
				// empowered: a green (not the ordinary dark cooldown) overlay counting down, so it reads as a buff
				int remaining = (int) Math.max(0L, oathUntil - now);
				g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, 0x8010A040);
				g.drawCenteredString(mc.font, String.valueOf((remaining + 19) / 20), x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			} else if (i == 2 && oathReciting) {
				g.drawCenteredString(mc.font, "...", x + BOX / 2, y0 + BOX / 2 - 4, GREEN);
			} else {
				int cd = cooldownFor(player, i, s);
				if (cd > 0) {
					g.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, COOLDOWN);
					g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
				}
			}
			// v0.15.15: the Shift move's cooldown -- a small green bar along the bottom of the box that refills as it
			// cools down (like the Nova HUD), bright green once it's ready
			float ready = shiftReady(player, i);
			g.fill(x + 1, y0 + BOX - 3, x + BOX - 1, y0 + BOX - 1, 0xC0062010);
			g.fill(x + 1, y0 + BOX - 3, x + 1 + Math.round((BOX - 2) * ready), y0 + BOX - 1,
					ready >= 1f ? SHIFT_BAR_READY : SHIFT_BAR_COOLING);
		}

		// ---- Ring Charge below the keys: a thin 3 px bar, no border, pulsing faster the lower it gets
		float frac = Mth.clamp(s.ringCharge, 0f, GreenLanternConfig.MAX_RING_CHARGE) / GreenLanternConfig.MAX_RING_CHARGE;
		int severity = com.projecthero.mod.greenlantern.GreenLanternEnergy.severityTier(frac);
		boolean low = severity > 0;
		int pulsePeriod = Math.max(3, 16 - severity * 2);
		boolean pulseOff = low && (now % pulsePeriod) < Math.max(1, pulsePeriod / 3);
		int barY = y0 + BOX + 4;
		g.fill(x0, barY, right, barY + 3, TRACK);
		if (!pulseOff) {
			g.fill(x0, barY, x0 + Math.round(totalW * frac), barY + 3, low ? LOW : GREEN);
		}
		int reserveMark = x0 + Math.round(totalW * (GreenLanternConfig.EMERGENCY_RESERVE / GreenLanternConfig.MAX_RING_CHARGE));
		g.fill(reserveMark, barY - 1, reserveMark + 1, barY + 4, 0xFFFFDD33);
		g.drawString(mc.font, Math.round(frac * 100.0f) + "%", x0, barY + 5, low ? LOW : 0xFFA8E6B8, true);

		// ---- stacked above the title rows
		labelY = renderRingRemoval(g, mc, fx, now, pt, x0, labelY, totalW);
		labelY = renderBarrier(g, mc, player, x0, labelY, totalW);
		labelY = renderConstructCooldowns(g, mc, player, x0, labelY, totalW);
		if (Screen.hasAltDown()) {
			renderAltNames(g, mc, x0, labelY, totalW);
		}
	}

	/** What is running right now, for the right end of the title row (most important first), or null. */
	private static Component status(Player player, boolean oathActive, boolean oathReciting, long oathUntil, long now) {
		if (player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FX, GreenLanternFx.EMPTY).has(GreenLanternFx.CH_CHARGE)) {
			return Component.translatable("hud.projecthero.green_lantern.tag.charging").withStyle(ChatFormatting.GREEN);
		}
		if (oathActive) {
			return Component.translatable("hud.projecthero.green_lantern.tag.oath", (int) Math.ceil((oathUntil - now) / 20.0))
					.withStyle(ChatFormatting.GOLD);
		}
		if (oathReciting) {
			return Component.translatable("hud.projecthero.green_lantern.tag.reciting").withStyle(ChatFormatting.YELLOW);
		}
		if (player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FLYING, false)) {
			return Component.translatable(player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BOOSTING, false)
					? "hud.projecthero.green_lantern.tag.boost" : "hud.projecthero.green_lantern.tag.flying")
					.withStyle(ChatFormatting.AQUA);
		}
		return null;
	}

	/** Which key box a move animation belongs to (so the box flashes as the move goes off). */
	private static int animKey(int anim) {
		return switch (anim) {
			case GreenLanternFx.ANIM_BOLT, GreenLanternFx.ANIM_BLAST -> 0;
			case GreenLanternFx.ANIM_FIST, GreenLanternFx.ANIM_HAMMER -> 1;
			case GreenLanternFx.ANIM_OATH -> 2;
			case GreenLanternFx.ANIM_DOME -> 3;
			case GreenLanternFx.ANIM_SCAN -> 4;
			case GreenLanternFx.ANIM_MISSILES, GreenLanternFx.ANIM_CONSTRUCT -> 5;
			case GreenLanternFx.ANIM_GRAB, GreenLanternFx.ANIM_THROW -> 4;
			default -> -1;
		};
	}

	/** Draws cell {@code index} of the construct icon atlas at {@code size} px (used by the construct wheel). */
	public static void icon(GuiGraphics g, int index, int x, int y, int size) {
		RenderSystem.enableBlend();
		g.blit(ICONS, x, y, size, size, (index % 8) * 16f, (index / 8) * 16f, 16, 16, 128, 64);
		RenderSystem.disableBlend();
	}

	/** Sneak + hold N: a label and a thin gold bar filling toward the ring coming off. Returns the y above it. */
	private static int renderRingRemoval(GuiGraphics g, Minecraft mc, GreenLanternFx fx, long now, float pt, int x0, int topY,
			int w) {
		if (!fx.has(GreenLanternFx.CH_RING_REMOVE) || fx.ringRemoveStart() == 0L) {
			return topY;
		}
		float f = Mth.clamp((now - fx.ringRemoveStart() + pt) / GreenLanternConfig.RING_REMOVE_HOLD_TICKS, 0f, 1f);
		int barY = topY + 4;
		int labelY = barY - 10;
		Component label = Component.translatable("hud.projecthero.green_lantern.ring_remove")
				.append(" " + Math.round(f * 100) + "%");
		g.drawCenteredString(mc.font, label, x0 + w / 2, labelY, GOLD);
		g.fill(x0, barY, x0 + w, barY + 3, TRACK);
		g.fill(x0, barY, x0 + Math.round(w * f), barY + 3, GOLD);
		return labelY - LINE - 2;
	}

	/**
	 * The shield / dome uptime meter (v0.11.8 semantics: time in use, not damage taken) -- a centred label and a thin
	 * bar, shown while a barrier is up or the meter is refilling, hidden once neither is true. Returns the y above it.
	 */
	private static int renderBarrier(GuiGraphics g, Minecraft mc, Player player, int x0, int topY, int w) {
		boolean active = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_HP, 0f) > 0f;
		float meter = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_METER, 1f);
		if (!active && meter >= 1f) {
			return topY;
		}
		boolean dome = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_IS_DOME, false);
		int barY = topY + 4;
		int labelY = barY - 10;
		Component label = Component.translatable(active
				? (dome ? "hud.projecthero.green_lantern.dome" : "hud.projecthero.green_lantern.shield")
				: "hud.projecthero.green_lantern.barrier").withStyle(ChatFormatting.AQUA);
		g.drawCenteredString(mc.font, label, x0 + w / 2, labelY, 0xFFFFFFFF);
		g.fill(x0, barY, x0 + w, barY + 3, TRACK);
		g.fill(x0, barY, x0 + Math.round(w * meter), barY + 3, active ? 0xFF35C8F0 : 0xFF1E7A94);
		return labelY - LINE - 2;
	}

	/** One right-aligned line per construct on its post-use cooldown, with the seconds left. Returns the y above them. */
	private static int renderConstructCooldowns(GuiGraphics g, Minecraft mc, Player player, int x0, int topY, int w) {
		List<ConstructType> onCooldown = new ArrayList<>();
		for (ConstructType type : ConstructType.values()) {
			if (GreenLanternConstructs.cooldownRemainingFor(player, type) > 0) {
				onCooldown.add(type);
			}
		}
		if (onCooldown.isEmpty()) {
			return topY;
		}
		int y = topY + LINE;
		for (ConstructType type : onCooldown) {
			y -= LINE;
			int seconds = (GreenLanternConstructs.cooldownRemainingFor(player, type) + 19) / 20;
			Component label = Component.literal("■ ").withStyle(st -> st.withColor(GREEN_DIM))
					.append(Component.translatable(type.translationKey()).withStyle(ChatFormatting.GRAY))
					.append(Component.literal("  " + seconds + "s").withStyle(ChatFormatting.WHITE));
			g.drawString(mc.font, label, x0 + w - mc.font.width(label), y, 0xFFFFFFFF, true);
		}
		return y - LINE - 2;
	}

	/**
	 * Alt held: every key's move names as plain lines (no panel), right-aligned with the key row and growing upward.
	 * Several lines are wider than the key row, so right-anchoring keeps them on screen.
	 */
	private static void renderAltNames(GuiGraphics g, Minecraft mc, int x0, int topY, int w) {
		int right = x0 + w;
		int y = topY + LINE - KEYS * LINE;
		for (int i = 0; i < KEYS; i++) {
			Component label = Component.literal(KEY_LABELS[i] + "  ").withStyle(ChatFormatting.GOLD)
					.append(Component.translatable("projecthero.guide.green_lantern.ability." + KEY_NAMES[i]).withStyle(ChatFormatting.WHITE));
			g.drawString(mc.font, label, Math.max(2, right - mc.font.width(label)), y, 0xFFFFFFFF, true);
			y += LINE;
		}
	}

	private static int cooldownFor(Player player, int slot, GreenLanternState s) {
		return switch (slot) {
			case 0 -> Math.max(GreenLantern.cooldownRemaining(player, "ring_bolt"),
					GreenLantern.cooldownRemaining(player, "continuous_beam"));
			// v0.15.15: the box shows the tap move; each Shift move has its own bar along the bottom (shiftReady)
			case 1 -> GreenLantern.cooldownRemaining(player, "construct_fist");
			case 2 -> GreenLantern.cooldownRemaining(player, "oath_mode");
			case 3 -> GreenLantern.cooldownRemaining(player, "directional_shield");
			case 4 -> GreenLantern.cooldownRemaining(player, "giant_hand"); // v0.15.15: V = Giant Hand (its Shift move is the bar)
			case 5 -> GreenLanternConstructs.cooldownRemainingFor(player, ConstructType.byOrdinal(s.selectedConstruct));
			default -> 0;
		};
	}

	/** v0.15.15: how far key {@code slot}'s Shift move has cooled down, 0..1 (1 = ready). */
	private static float shiftReady(Player player, int slot) {
		int cd = 0;
		String id = null;
		for (String c : SHIFT_COOLDOWNS[slot]) {
			int r = GreenLantern.cooldownRemaining(player, c);
			if (r > cd) {
				cd = r;
				id = c;
			}
		}
		if (id == null) {
			for (String c : SHIFT_COOLDOWNS[slot]) {
				PEAK.remove(c);
			}
			return 1f;
		}
		int peak = Math.max(cd, PEAK.getOrDefault(id, 0));
		PEAK.put(id, peak);
		return 1f - cd / (float) Math.max(1, peak);
	}
}
