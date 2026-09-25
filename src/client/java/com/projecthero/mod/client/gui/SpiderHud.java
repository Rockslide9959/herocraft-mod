package com.projecthero.mod.client.gui;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.spider.SpiderAbilities;
import com.projecthero.mod.spider.SpiderWebReserve;
import com.projecthero.mod.spider.data.SpiderManState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The Spider-Man HUD: the Web Reserve meter and the six web abilities with their keys and cooldowns.
 *
 * <p>Laid out to match {@link AbilityHud} rather than inventing a second visual language -- the same
 * six boxes in the same corner, the same cooldown shading, the same meter bar underneath -- because
 * from the player's side this is one more power occupying the same six slots, and it should read that
 * way. It is only drawn while Spider-Man actually holds the slots; select one of your mutations from
 * the power wheel and the ordinary ability HUD comes back.
 */
public final class SpiderHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	/** Height of the label stack under the key row (name, web %, bar, mode) -- also lifts the whole HUD. */
	private static final int BOTTOM_STACK = 48;

	private static final int COLOR_BOX_BG = 0xC0180C10;
	private static final int COLOR_BORDER = 0xFF4A1E28;
	private static final int COLOR_COOLDOWN = 0xB0000000;
	private static final int COLOR_KEY = 0xFFE8B0B8;
	private static final int COLOR_WEB = 0xFFE8E8F4;
	/** Dark maroon so the percentage stays readable over bright terrain. */
	private static final int COLOR_WEB_TEXT = 0xFFE8E8F4; // same grey-white as the web bar
	private static final int COLOR_WEB_LOW = 0xFFFF7A3C;

	/** Slot order 1..6 mapped to the ability that sits there -- see {@code SpiderAbilities}. */
	private static final String[] SLOT_ABILITIES = {
			SpiderAbilities.WEB_SWING, SpiderAbilities.WEB_ZIP, SpiderAbilities.WEB_YANK,
			SpiderAbilities.WEB_SHOT, SpiderAbilities.WEB_NET, SpiderAbilities.WALL_CRAWL
	};
	/** v0.12.20: the Combat Mode layout (N). */
	private static final String[] COMBAT_ABILITIES = {
			com.projecthero.mod.spider.SpiderCombat.WEB_STRIKE, SpiderAbilities.WEB_ZIP,
			com.projecthero.mod.spider.SpiderCombat.WEB_THROW, com.projecthero.mod.spider.SpiderCombat.IMPACT_WEB,
			SpiderAbilities.WEB_NET, SpiderAbilities.WALL_CRAWL
	};

	// ---- Spider-Sense warning (v0.6.17; v0.6.19: one marker per kind, held until the danger passes) ----
	private static final int WARN_KINDS = 6;
	/** How long a marker lingers after its last refresh. The scan re-sends every 5-10 ticks while the
	 *  threat is present, so 22 keeps the marker continuously lit and fades it ~1s after danger ends. */
	private static final int WARN_LINGER = 22;
	private static final long[] warnUntil = new long[WARN_KINDS];
	private static final float[] warnYaw = new float[WARN_KINDS];
	private static final int[] warnVertical = new int[WARN_KINDS];

	private SpiderHud() {
	}

	/** Called from the network handler when a Spider-Sense warning arrives. */
	public static void flashWarning(int kind, float yaw, int vertical) {
		if (kind < 0 || kind >= WARN_KINDS) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		warnUntil[kind] = (mc.level != null ? mc.level.getGameTime() : 0L) + WARN_LINGER;
		warnYaw[kind] = yaw;
		warnVertical[kind] = vertical;
	}

	private static int warningColor(int kind) {
		return switch (kind) {
			case 2 -> 0xFFFF5533; // explosion
			case 4 -> 0xFFFF8833; // environment
			default -> 0xFFFF3355; // melee / projectile / targeted
		};
	}

	private static void renderWarning(GuiGraphics g, Minecraft client, Player player) {
		long now = client.level != null ? client.level.getGameTime() : 0L;
		boolean any = false;
		for (int k = 0; k < WARN_KINDS; k++) {
			if (now < warnUntil[k]) {
				any = true;
				break;
			}
		}
		if (!any) {
			return;
		}
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		boolean blink = (now % 6) < 3;

		// A single crosshair pulse for "danger", coloured by the most urgent active kind.
		int pulseCol = 0xFFFF3355;
		for (int k : new int[] {2, 4, 5, 1, 0}) {
			if (now < warnUntil[k]) {
				pulseCol = warningColor(k);
				break;
			}
		}
		if (blink) {
			g.renderOutline(cx - 10, cy - 10, 20, 20, pulseCol);
			g.renderOutline(cx - 14, cy - 14, 28, 28, (pulseCol & 0x00FFFFFF) | 0x66000000);
		}

		// One directional marker per still-active kind.
		for (int k = 0; k < WARN_KINDS; k++) {
			if (now >= warnUntil[k]) {
				continue;
			}
			int col = warningColor(k);
			float rel = net.minecraft.util.Mth.wrapDegrees(warnYaw[k] - player.getYRot());
			double rad = Math.toRadians(rel);
			int mx = cx + (int) (Math.sin(rad) * 44);
			int my = cy - (int) (Math.cos(rad) * 44);
			if (warnVertical[k] == 1) {
				my = cy - 46;
			} else if (warnVertical[k] == 2) {
				my = cy + 46;
			}
			g.fill(mx - 3, my - 3, mx + 3, my + 3, blink ? col : (col & 0x00FFFFFF) | 0x88000000);
		}
	}

	public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		if (player == null || client.options.hideGui) {
			return;
		}
		SpiderManState state = player.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
		if (state == null || !state.hasPower) {
			return;
		}
		// The Spider-Sense warning shows regardless of which power holds the ability slots -- danger is
		// danger whether or not you are also holding Mjolnir.
		renderWarning(graphics, client, player);
		renderBlossomCharge(graphics, client, player, state.combatMode);
		// Mirrors SpiderManAbilityManager.hasContext: a mutation selected in the wheel owns the slots.
		ExperimentalState experimental = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (experimental != null && !experimental.activePower.isEmpty()) {
			return;
		}
		// Thor and Iron Man both outrank Spider-Man in the router, so neither of their contexts should
		// be showing a web-ability row underneath their own HUD.
		if (player.getMainHandItem().is(com.projecthero.mod.item.ModItems.MJOLNIR)
				|| player.getOffhandItem().is(com.projecthero.mod.item.ModItems.MJOLNIR)
				|| wearingIronMan(player)) {
			return;
		}

		long gameTime = client.level != null ? client.level.getGameTime() : 0L;
		int screenW = graphics.guiWidth();
		int screenH = graphics.guiHeight();
		int totalW = 6 * BOX + 5 * GAP;
		int x0 = screenW - MARGIN - totalW;
		// v0.12.21 layout, top to bottom: ability keys / Spider-Man / Web % + bar / mode [N]
		int y0 = screenH - MARGIN - BOX - BOTTOM_STACK;
		String[] layout = state.combatMode ? COMBAT_ABILITIES : SLOT_ABILITIES;

		boolean expanded = org.lwjgl.glfw.GLFW.glfwGetKey(client.getWindow().getWindow(),
				org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

		for (int i = 0; i < 6; i++) {
			AbilitySlot slot = AbilitySlot.byNumber(i + 1);
			String ability = layout[i];
			int x = x0 + i * (BOX + GAP);

			Long readyAt = state.abilityReadyAt.get(ability);
			int cdRemain = readyAt == null ? 0 : (int) Math.max(0L, readyAt - gameTime);
			boolean active = (ability.equals(SpiderAbilities.WEB_SWING) && state.swinging)
					|| (ability.equals(SpiderAbilities.WALL_CRAWL) && state.wallCrawlEnabled);

			graphics.fill(x, y0, x + BOX, y0 + BOX, COLOR_BOX_BG);
			graphics.renderOutline(x, y0, BOX, BOX, active ? 0xFFFF6070 : COLOR_BORDER);
			graphics.drawString(client.font, String.valueOf(slot.defaultKey()), x + 2, y0 + 2, COLOR_KEY, false);

			if (cdRemain > 0) {
				graphics.fill(x + 1, y0 + 1, x + BOX - 1, y0 + BOX - 1, COLOR_COOLDOWN);
				graphics.drawCenteredString(client.font,
						String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(cdRemain / 20.0f)),
						x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			}
			if (expanded) {
				Component name = Component.translatable("projecthero.spider_man.ability." + ability);
				int tw = client.font.width(name);
				graphics.drawString(client.font, name, x0 - 8 - tw, y0 + i * 10 - 52, 0xFFE0C8CC);
			}
		}

		// Web Reserve bar. The ceiling doubles while the Symbiote is active (v0.9.10).
		float webMax = client.player != null ? SpiderWebReserve.maxFor(client.player) : SpiderWebReserve.MAX;
		float ratio = Math.max(0.0f, Math.min(1.0f, state.webReserve / webMax));
		int nameY = y0 + BOX + 3;
		graphics.drawString(client.font, Component.translatable("projecthero.spider_man.name")
				.withStyle(ChatFormatting.RED, ChatFormatting.BOLD), x0, nameY, 0xFFFF6070);
		int webY = nameY + 10;
		graphics.drawString(client.font, Component.translatable("hud.projecthero.spider_man.web_reserve",
				(int) Math.ceil(ratio * 100.0f)), x0, webY, COLOR_WEB_TEXT, false);
		// Thin bar in the Wolverine style: 3px, no border.
		int barY = webY + 10;
		graphics.fill(x0, barY, x0 + totalW, barY + 3, 0xAA180C10);
		graphics.fill(x0, barY, x0 + Math.round(totalW * ratio), barY + 3,
				ratio < 0.2f ? COLOR_WEB_LOW : COLOR_WEB);
		graphics.drawString(client.font, Component.translatable(state.combatMode
				? "hud.projecthero.spider_man.mode_combat" : "hud.projecthero.spider_man.mode_traversal"),
				x0, barY + 6, COLOR_WEB_TEXT, false);

		// v0.6.19: the double-jump cooldown is no longer shown -- it is a 1-second passive and the HUD
		// clutter was not worth it.
	}

	/**
	 * The 3-second Web Blossom buildup bar, drawn while sneak + V is held. Sits directly above the
	 * ability keybind row in the bottom-right corner -- clear of the Web Reserve bar (which is below
	 * the row) and of the "Spider-Man" label (just above it), so nothing in this HUD overlaps.
	 */
	private static void renderBlossomCharge(GuiGraphics g, Minecraft client, Player player, boolean combatMode) {
		int ticks = player.getAttachedOrElse(ModAttachments.SPIDER_BLOSSOM_CHARGE, 0);
		if (ticks <= 0) {
			return;
		}
		float ratio = Math.min(1.0f, ticks / (float) com.projecthero.mod.spider.SpiderAbilities.blossomChargeTicks(combatMode));
		boolean full = ratio >= 1.0f;
		int w = 6 * BOX + 5 * GAP;
		int h = 3;
		int x = g.guiWidth() - MARGIN - w;
		// The keybind boxes start here; the "Spider-Man" label sits 10px above that. Stack the bar and
		// its own label above the label, with a small gap.
		int boxesY = g.guiHeight() - MARGIN - BOX - BOTTOM_STACK;
		int y = boxesY - 14 - h;
		g.drawCenteredString(client.font, Component.translatable("hud.projecthero.spider_man.web_blossom"),
				x + w / 2, y - 10, full ? 0xFFFFF0A0 : 0xFFE8E8F4);
		g.fill(x, y, x + w, y + h, 0xC0180C10);
		g.fill(x, y, x + Math.round(w * ratio), y + h, full ? 0xFFFF6070 : COLOR_WEB);
	}

	private static boolean wearingIronMan(Player player) {
		for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[] {
				net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
				net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET }) {
			if (player.getItemBySlot(slot).getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem) {
				return true;
			}
		}
		return false;
	}
}
