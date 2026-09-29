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
 * The Green Lantern HUD -- v0.14.3 redesign. One framed panel in the bottom-right corner:
 * <pre>
 *   [emblem] GREEN LANTERN              OATH 18s · FLYING
 *   RING CHARGE                                     87%
 *   ████████████████████████░░░░░  (Gauge: segmented every 10%, the emergency reserve marked)
 *   [R][G][X][Z][V][C][H][N]        cooldowns drain from the top, lit outlines while a move is running
 *   [icon] Buzzsaw                          35 charge
 * </pre>
 * Above the panel, stacking upward: taking the ring off (a filling Gauge), the shield / dome meter, and every
 * construct on cooldown (icon, name, seconds, a Hairline bar running down). Hold Alt for every key's move names.
 * Drawn whenever the player has the power at all -- the ring works unsuited, so the HUD is not suit-gated.
 */
public final class GreenLanternHud {
	public static final ResourceLocation ICONS = ProjectHeroMod.id("textures/gui/green_lantern/constructs.png");
	public static final int EMBLEM_ICON = 31;

	private static final int BOX = 18;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int PAD = 4;
	private static final int LINE = 10;
	private static final int KEYS = 8;

	private static final int GREEN = 0xFF35F075;
	private static final int PALE = 0xFFA8FFC0;
	private static final int DIM = 0xFF6FA882;
	private static final int DEEP = 0xFF1A7838;
	private static final int LOW = 0xFFFF5A5A;
	private static final int GOLD = 0xFFFFD23A;
	private static final int PANEL = 0xB8050F08;
	private static final int FRAME = 0xFF1E661E;
	private static final int BOX_BG = 0xCC0A2412;
	private static final int COOLDOWN = 0xB0000000;
	private static final int TRACK = 0xCC06180C;

	private static final String[] KEY_LABELS = {"R", "G", "X", "Z", "V", "C", "H", "N"};
	/** Alt panel: {@code projecthero.guide.green_lantern.ability.<key>} per box. */
	private static final String[] KEY_NAMES = {
			"ring_bolt", "construct_fist", "oath", "shield", "suit", "construct", "giant_hand", "dismiss"
	};

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
		// The keys belong to Green Lantern only while no mutation is selected (GreenLanternAbilityManager.hasContext).
		com.projecthero.mod.hero.data.ExperimentalState experimental =
				player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (experimental != null && !experimental.activePower.isEmpty()) {
			return;
		}
		long now = mc.level.getGameTime();
		float pt = delta.getGameTimeDeltaPartialTick(false);
		GreenLanternFx fx = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FX, GreenLanternFx.EMPTY);

		int innerW = KEYS * BOX + (KEYS - 1) * GAP;
		int panelW = innerW + PAD * 2;
		int panelH = PAD + 12 + LINE + 6 + 4 + BOX + 4 + 12 + PAD;
		int px = g.guiWidth() - MARGIN - panelW;
		int py = g.guiHeight() - MARGIN - panelH;
		int x0 = px + PAD;
		int right = x0 + innerW;

		// ---- the frame
		g.fill(px, py, px + panelW, py + panelH, PANEL);
		g.renderOutline(px, py, panelW, panelH, FRAME);
		g.fill(px + 1, py, px + panelW - 1, py + 1, GREEN);
		g.fill(px + 1, py + 1, px + panelW - 1, py + 2, 0x6035F075);

		// ---- title row: emblem, name, status tags
		int y = py + PAD;
		icon(g, EMBLEM_ICON, x0, y - 1, 12);
		g.drawString(mc.font, Component.translatable("projecthero.guide.green_lantern").withStyle(ChatFormatting.BOLD),
				x0 + 15, y + 1, GREEN, true);
		long oathUntil = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_OATH_UNTIL, 0L);
		boolean oathActive = oathUntil > now;
		boolean oathReciting = !oathActive && player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_OATH_RECITING_SINCE, 0L) > 0L;
		List<Component> tags = new ArrayList<>();
		if (oathActive) {
			int secs = (int) Math.ceil((oathUntil - now) / 20.0);
			tags.add(Component.translatable("hud.projecthero.green_lantern.tag.oath", secs).withStyle(ChatFormatting.GOLD));
		} else if (oathReciting) {
			tags.add(Component.translatable("hud.projecthero.green_lantern.tag.reciting").withStyle(ChatFormatting.YELLOW));
		}
		if (player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FLYING, false)) {
			tags.add(Component.translatable(player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BOOSTING, false)
					? "hud.projecthero.green_lantern.tag.boost" : "hud.projecthero.green_lantern.tag.flying")
					.withStyle(ChatFormatting.AQUA));
		}
		if (s.suited) {
			tags.add(Component.translatable("hud.projecthero.green_lantern.tag.suited").withStyle(ChatFormatting.DARK_GREEN));
		}
		// never run into the title: drop the least important tags (the list is in priority order) until they fit
		int room = innerW - 15 - mc.font.width(Component.translatable("projecthero.guide.green_lantern")
				.withStyle(ChatFormatting.BOLD)) - 6;
		while (!tags.isEmpty() && tagsWidth(mc, tags) > room) {
			tags.remove(tags.size() - 1);
		}
		int tx = right;
		for (int i = tags.size() - 1; i >= 0; i--) {
			Component t = tags.get(i);
			tx -= mc.font.width(t);
			g.drawString(mc.font, t, tx, y + 1, 0xFFFFFFFF, true);
			tx -= 6;
		}
		y += 12;

		// ---- Ring Charge (Gauge)
		float charge = Mth.clamp(s.ringCharge, 0f, GreenLanternConfig.MAX_RING_CHARGE);
		float frac = charge / GreenLanternConfig.MAX_RING_CHARGE;
		int severity = com.projecthero.mod.greenlantern.GreenLanternEnergy.severityTier(frac);
		boolean low = severity > 0;
		int pulsePeriod = Math.max(3, 16 - severity * 2);
		boolean pulseOff = low && (now % pulsePeriod) < Math.max(1, pulsePeriod / 3);
		g.drawString(mc.font, Component.translatable("hud.projecthero.green_lantern.ring_charge"), x0, y, DIM, true);
		String pct = Math.round(frac * 100f) + "%";
		g.drawString(mc.font, pct, right - mc.font.width(pct), y, low ? LOW : PALE, true);
		y += LINE;
		int barH = 4;
		g.fill(x0 - 1, y - 1, right + 1, y + barH + 1, FRAME);
		g.fill(x0, y, right, y + barH, TRACK);
		if (!pulseOff) {
			int fillW = Math.round(innerW * frac);
			g.fillGradient(x0, y, x0 + fillW, y + barH, low ? 0xFFFF8080 : 0xFF8CFFAE, low ? LOW : 0xFF1FBF55);
			if (fillW > 1) {
				g.fill(x0 + fillW - 1, y, x0 + fillW, y + barH, 0xFFFFFFFF);
			}
		}
		for (int k = 1; k < 10; k++) {
			int sx = x0 + innerW * k / 10;
			g.fill(sx, y, sx + 1, y + barH, 0x80000000);
		}
		int reserve = x0 + Math.round(innerW * (GreenLanternConfig.EMERGENCY_RESERVE / GreenLanternConfig.MAX_RING_CHARGE));
		g.fill(reserve, y - 1, reserve + 1, y + barH + 1, GOLD);
		y += barH + 6;

		// ---- the keys
		boolean barrierUp = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_HP, 0f) > 0f;
		for (int i = 0; i < KEYS; i++) {
			int bx = x0 + i * (BOX + GAP);
			boolean active = switch (i) {
				case 0 -> fx.has(GreenLanternFx.CH_BEAM);
				case 2 -> oathActive || oathReciting || fx.has(GreenLanternFx.CH_GATLING);
				case 3 -> barrierUp;
				case 4 -> s.suited;
				case 6 -> fx.has(GreenLanternFx.CH_HAND);
				case 7 -> fx.has(GreenLanternFx.CH_RING_REMOVE);
				default -> false;
			};
			boolean flash = fx.anim() != GreenLanternFx.ANIM_NONE && now - fx.animStart() < 6 && animKey(fx.anim()) == i;
			g.fill(bx, y, bx + BOX, y + BOX, BOX_BG);
			if (active || flash) {
				g.fill(bx + 1, y + 1, bx + BOX - 1, y + BOX - 1, active ? 0x5035F075 : 0x3035F075);
			}
			g.renderOutline(bx, y, BOX, BOX, active || flash ? GREEN : FRAME);
			int cd = cooldownFor(player, i, s);
			int max = cooldownMax(i, s);
			if (i == 2 && oathActive) {
				int secs = (int) Math.ceil((oathUntil - now) / 20.0);
				g.fill(bx + 1, y + 1, bx + BOX - 1, y + BOX - 1, 0x8010A040);
				g.drawCenteredString(mc.font, String.valueOf(secs), bx + BOX / 2, y + 5, GOLD);
			} else if (cd > 0) {
				int h = Math.max(1, Math.round((BOX - 2) * Math.min(1f, cd / (float) Math.max(1, max))));
				g.fill(bx + 1, y + 1, bx + BOX - 1, y + 1 + h, COOLDOWN);
				g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), bx + BOX / 2, y + 5, 0xFFFFFFFF);
			} else {
				g.drawCenteredString(mc.font, KEY_LABELS[i], bx + BOX / 2, y + 5, active ? 0xFFFFFFFF : PALE);
			}
		}
		y += BOX + 4;

		// ---- the selected construct
		ConstructType sel = ConstructType.byOrdinal(s.selectedConstruct);
		icon(g, sel.ordinal(), x0, y, 12);
		g.drawString(mc.font, Component.translatable(sel.translationKey()), x0 + 15, y + 2, 0xFFE8FFEE, true);
		int selCd = GreenLanternConstructs.cooldownRemainingFor(player, sel);
		Component costLine = selCd > 0
				? Component.translatable("hud.projecthero.green_lantern.cooldown_short", (selCd + 19) / 20).withStyle(ChatFormatting.RED)
				: Component.translatable("hud.projecthero.green_lantern.cost", Math.round(sel.initialCost())).withStyle(ChatFormatting.GRAY);
		g.drawString(mc.font, costLine, right - mc.font.width(costLine), y + 2, 0xFFFFFFFF, true);

		// ---- stacked above the panel
		int top = py - 3;
		top = renderRingRemoval(g, mc, fx, now, pt, px, top, panelW);
		top = renderBarrier(g, mc, player, px, top, panelW);
		top = renderConstructCooldowns(g, mc, player, px, top, panelW);
		if (Screen.hasAltDown()) {
			renderAltPanel(g, mc, px, top, panelW);
		}
	}

	private static int tagsWidth(Minecraft mc, List<Component> tags) {
		int w = 0;
		for (Component t : tags) {
			w += mc.font.width(t) + 6;
		}
		return w - 6;
	}

	/** Which key box a move animation belongs to (so the box flashes as the move goes off). */
	private static int animKey(int anim) {
		return switch (anim) {
			case GreenLanternFx.ANIM_BOLT -> 0;
			case GreenLanternFx.ANIM_FIST, GreenLanternFx.ANIM_HAMMER -> 1;
			case GreenLanternFx.ANIM_OATH -> 2;
			case GreenLanternFx.ANIM_DOME -> 3;
			case GreenLanternFx.ANIM_SCAN -> 4;
			case GreenLanternFx.ANIM_MISSILES, GreenLanternFx.ANIM_CONSTRUCT -> 5;
			case GreenLanternFx.ANIM_GRAB, GreenLanternFx.ANIM_THROW -> 6;
			default -> -1;
		};
	}

	/** Draws cell {@code index} of the construct icon atlas at {@code size} px. */
	public static void icon(GuiGraphics g, int index, int x, int y, int size) {
		RenderSystem.enableBlend();
		g.blit(ICONS, x, y, size, size, (index % 8) * 16f, (index / 8) * 16f, 16, 16, 128, 64);
		RenderSystem.disableBlend();
	}

	/** Shift + hold N: a Gauge filling toward the ring coming off. */
	private static int renderRingRemoval(GuiGraphics g, Minecraft mc, GreenLanternFx fx, long now, float pt, int px, int top,
			int w) {
		if (!fx.has(GreenLanternFx.CH_RING_REMOVE) || fx.ringRemoveStart() == 0L) {
			return top;
		}
		float f = Mth.clamp((now - fx.ringRemoveStart() + pt) / GreenLanternConfig.RING_REMOVE_HOLD_TICKS, 0f, 1f);
		int barY = top - 4;
		int labelY = barY - 11;
		g.fill(px, labelY - 3, px + w, barY + 7, PANEL);
		Component label = Component.translatable("hud.projecthero.green_lantern.ring_remove");
		g.drawString(mc.font, label, px + PAD, labelY, GOLD, true);
		String pct = Math.round(f * 100) + "%";
		g.drawString(mc.font, pct, px + w - PAD - mc.font.width(pct), labelY, 0xFFFFFFFF, true);
		int x0 = px + PAD;
		int x1 = px + w - PAD;
		g.fill(x0 - 1, barY - 1, x1 + 1, barY + 4, FRAME);
		g.fill(x0, barY, x1, barY + 3, TRACK);
		g.fill(x0, barY, x0 + Math.round((x1 - x0) * f), barY + 3, GOLD);
		return labelY - 6;
	}

	/**
	 * The shield / dome uptime meter (v0.11.8 semantics: time in use, not damage taken) -- shown while a barrier is up or
	 * the meter is refilling, as a Gauge with its label above.
	 */
	private static int renderBarrier(GuiGraphics g, Minecraft mc, Player player, int px, int top, int w) {
		boolean active = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_HP, 0f) > 0f;
		float meter = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_METER, 1f);
		if (!active && meter >= 1f) {
			return top;
		}
		boolean dome = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_IS_DOME, false);
		int barY = top - 4;
		int labelY = barY - 11;
		g.fill(px, labelY - 3, px + w, barY + 7, PANEL);
		Component label = Component.translatable(active
				? (dome ? "hud.projecthero.green_lantern.dome" : "hud.projecthero.green_lantern.shield")
				: "hud.projecthero.green_lantern.barrier");
		g.drawString(mc.font, label, px + PAD, labelY, 0xFF7FE8FF, true);
		String pct = Math.round(meter * 100) + "%";
		g.drawString(mc.font, pct, px + w - PAD - mc.font.width(pct), labelY, 0xFFFFFFFF, true);
		int x0 = px + PAD;
		int x1 = px + w - PAD;
		g.fill(x0 - 1, barY - 1, x1 + 1, barY + 4, FRAME);
		g.fill(x0, barY, x1, barY + 3, TRACK);
		g.fill(x0, barY, x0 + Math.round((x1 - x0) * meter), barY + 3, active ? 0xFF35C8F0 : 0xFF1E7A94);
		return labelY - 6;
	}

	/** One row per construct on cooldown: icon, name, seconds, and a Hairline bar running down under it. */
	private static int renderConstructCooldowns(GuiGraphics g, Minecraft mc, Player player, int px, int top, int w) {
		List<ConstructType> onCooldown = new ArrayList<>();
		for (ConstructType type : ConstructType.values()) {
			if (GreenLanternConstructs.cooldownRemainingFor(player, type) > 0) {
				onCooldown.add(type);
			}
		}
		if (onCooldown.isEmpty()) {
			return top;
		}
		int rowH = 14;
		int h = onCooldown.size() * rowH + 4;
		int y = top - h;
		g.fill(px, y, px + w, top, PANEL);
		int ry = y + 3;
		for (ConstructType type : onCooldown) {
			int cd = GreenLanternConstructs.cooldownRemainingFor(player, type);
			int max = constructCooldownMax(type);
			icon(g, type.ordinal(), px + PAD, ry, 10);
			g.drawString(mc.font, Component.translatable(type.translationKey()), px + PAD + 13, ry + 1, 0xFFB8D8C0, true);
			String secs = ((cd + 19) / 20) + "s";
			g.drawString(mc.font, secs, px + w - PAD - mc.font.width(secs), ry + 1, 0xFFFFFFFF, true);
			int bx0 = px + PAD + 13;
			int bx1 = px + w - PAD;
			g.fill(bx0, ry + 10, bx1, ry + 11, 0x60000000);
			g.fill(bx0, ry + 10, bx0 + Math.round((bx1 - bx0) * Math.min(1f, cd / (float) Math.max(1, max))), ry + 11, DEEP | 0xFF000000);
			ry += rowH;
		}
		return y - 3;
	}

	/** Alt held: every key's move names, right-aligned with the panel, growing upward. */
	private static void renderAltPanel(GuiGraphics g, Minecraft mc, int px, int top, int panelW) {
		Component[] labels = new Component[KEYS];
		int w = 0;
		for (int i = 0; i < KEYS; i++) {
			labels[i] = Component.literal(KEY_LABELS[i] + "  ").withStyle(ChatFormatting.GOLD)
					.append(Component.translatable("projecthero.guide.green_lantern.ability." + KEY_NAMES[i]).withStyle(ChatFormatting.WHITE));
			w = Math.max(w, mc.font.width(labels[i]));
		}
		w = Math.min(Math.max(w + 8, panelW), g.guiWidth() - 6);
		int h = KEYS * LINE + 6;
		int x = Math.max(2, px + panelW - w);
		int y = top - h - 2;
		g.fill(x, y, x + w, y + h, 0xE0050F08);
		g.renderOutline(x, y, w, h, FRAME);
		int ly = y + 4;
		for (Component label : labels) {
			g.drawString(mc.font, label, x + 4, ly, 0xFFFFFFFF, false);
			ly += LINE;
		}
	}

	private static int cooldownFor(Player player, int slot, GreenLanternState s) {
		return switch (slot) {
			case 0 -> Math.max(GreenLantern.cooldownRemaining(player, "ring_bolt"),
					GreenLantern.cooldownRemaining(player, "continuous_beam"));
			case 1 -> Math.max(GreenLantern.cooldownRemaining(player, "construct_fist"),
					GreenLantern.cooldownRemaining(player, "war_hammer_slam"));
			case 2 -> Math.max(GreenLantern.cooldownRemaining(player, "oath_mode"),
					GreenLantern.cooldownRemaining(player, "emerald_gatling"));
			case 3 -> Math.max(GreenLantern.cooldownRemaining(player, "directional_shield"),
					GreenLantern.cooldownRemaining(player, "protective_dome"));
			case 4 -> GreenLantern.cooldownRemaining(player, "ring_scan");
			case 5 -> Math.max(GreenLanternConstructs.cooldownRemainingFor(player, ConstructType.byOrdinal(s.selectedConstruct)),
					GreenLantern.cooldownRemaining(player, "missile_barrage"));
			case 6 -> GreenLantern.cooldownRemaining(player, "giant_hand");
			default -> 0;
		};
	}

	/** The full length of whatever is most likely draining that box, so the shade drains at the right pace. */
	private static int cooldownMax(int slot, GreenLanternState s) {
		return switch (slot) {
			case 0 -> GreenLanternConfig.BEAM_FORCED_COOLDOWN_TICKS;
			case 1 -> GreenLanternConfig.HAMMER_COOLDOWN_TICKS;
			case 2 -> GreenLanternConfig.OATH_MODE_COOLDOWN_TICKS;
			case 3 -> GreenLanternConfig.DOME_COOLDOWN_TICKS;
			case 4 -> GreenLanternConfig.SCAN_COOLDOWN_TICKS;
			case 5 -> Math.max(GreenLanternConfig.MISSILE_COOLDOWN_TICKS, constructCooldownMax(ConstructType.byOrdinal(s.selectedConstruct)));
			case 6 -> GreenLanternConfig.HAND_COOLDOWN_TICKS;
			default -> 1;
		};
	}

	private static int constructCooldownMax(ConstructType type) {
		return switch (type) {
			case SENTRY_TURRET -> GreenLanternConfig.TURRET_COOLDOWN_TICKS;
			case HARD_LIGHT_WALL -> GreenLanternConfig.WALL_COOLDOWN_TICKS;
			case BATTERING_RAM -> GreenLanternConfig.RAM_COOLDOWN_TICKS;
			case RESCUE_TETHER -> GreenLanternConfig.TETHER_COOLDOWN_TICKS;
			case BUZZSAW -> GreenLanternConfig.BUZZSAW_COOLDOWN_TICKS;
			case ANVIL_DROP -> GreenLanternConfig.ANVIL_COOLDOWN_TICKS;
			case CHAIN_SNARE -> GreenLanternConfig.CHAINS_COOLDOWN_TICKS;
			case EMERALD_WARRIOR -> GreenLanternConfig.WARRIOR_COOLDOWN_TICKS;
			default -> 20;
		};
	}
}
