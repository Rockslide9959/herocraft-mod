package com.projecthero.mod.client.gui;

import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.spider.SpiderManConfig;
import com.projecthero.mod.spider.SpiderMoves;
import com.projecthero.mod.spider.SpiderWebReserve;
import com.projecthero.mod.spider.data.SpiderManState;
import com.projecthero.mod.spider.data.SpiderMoveState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Spider-Man HUD. v0.17.3 (user: "I really like the Minecraft-style HUD and ability keybinds ... apply this to all the
 * powers"): the corner block is now the shared Minecraft-style column ({@link VanillaHud.Stack}), bottom-right:
 * <pre>
 *   Weave: 2/4 anchors -- let go of C        status line (wrapped)
 *   Wall Crawl: ON  Sounds: OFF  ...          tags (+ the Black Suit's tier / revive / Take Over words)
 *   SPIDER-MAN                                title
 *   Hunger [Hairline ===|=====|==]  86%       Black Suit only (SymbioteHud.blackSuitRows)
 *   Blossom [Meter =====----]  60%            while V charges Web Blossom
 *   Web [Meter ==========-]  92%              Web Reserve
 *   +--+--+--+--+--+--+                       R G Z X C V: tap move (Shift move while Shift is held) sweep + seconds, the other
 *   | R| G| Z| X| C| V|                       on the durability bar, the hold move on the thin top bar; purple corner = a key
 *   +--+--+--+--+--+--+                       the black suit swapped; selection frame = a running stance
 * </pre>
 * Hold Left Alt for every key's three move names. The stealth eye sits left of the strip. Also the Spider-Sense danger
 * markers, the Web Pin cue and (v0.16.1) the web-alarm arrow, round the crosshair as before.
 */
public final class SpiderHud {
	/** v0.16.2: the mark on a key the black suit has swapped for symbiote moves. */
	private static final int COLOR_SYMBIOTE = 0xFF9A6CF0;
	private static final int COLOR_WEB = 0xFFE8E8F4;
	private static final int COLOR_WEB_TEXT = 0xFFE8E8F4;
	private static final int COLOR_WEB_LOW = 0xFFFF7A3C;

	// ---- Spider-Sense warning (v0.6.17; v0.6.19: one marker per kind, held until the danger passes) ----
	private static final int WARN_KINDS = 7;
	private static final int WARN_LINGER = 22;
	private static final long[] warnUntil = new long[WARN_KINDS];
	private static final float[] warnYaw = new float[WARN_KINDS];
	private static final int[] warnVertical = new int[WARN_KINDS];

	// ---- v0.16.1 web alarm ----
	private static final int ALARM_TICKS = 80;
	private static Vec3 alarmAt;
	private static long alarmUntil;

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

	/** v0.16.1: something that is not him or his squad touched one of his webs at {@code at}. */
	public static void webAlarm(Vec3 at) {
		Minecraft mc = Minecraft.getInstance();
		alarmAt = at;
		alarmUntil = (mc.level != null ? mc.level.getGameTime() : 0L) + ALARM_TICKS;
		if (mc.player != null && !com.projecthero.mod.spider.SpiderMoves.soundsOff(mc.player)) {
			mc.player.playSound(com.projecthero.mod.spider.SpiderSounds.SENSE, 0.9f, 1.4f);
		}
	}

	/** v0.16.1: red = an attack about to land, orange = a hostile closing in / a weapon aimed, yellow = a hazard. */
	private static int warningColor(int kind) {
		return switch (kind) {
			case 0, 1 -> 0xFF3344; // a blow / a projectile about to land
			case 5, 6 -> 0xFF9922; // a hostile closing in / a weapon drawn on him
			default -> 0xFFE040; // explosion / environment
		};
	}

	private static int priority(int kind) {
		return switch (kind) {
			case 0, 1 -> 3;
			case 5, 6 -> 2;
			default -> 1;
		};
	}

	// 7 x 7 kind icons: fist (blows / hostiles), arrow (projectiles / aimed weapons), explosion, hazard
	private static final String[] ICON_FIST = {".###...", "#####..", "######.", "######.", ".#####.", "..###..", "..###.."};
	private static final String[] ICON_ARROW = {"...#...", "..###..", ".#.#.#.", "...#...", "...#...", "..#.#..", ".#...#."};
	private static final String[] ICON_BLAST = {"#..#..#", ".#.#.#.", "..###..", "#######", "..###..", ".#.#.#.", "#..#..#"};
	private static final String[] ICON_HAZARD = {"...#...", "..#.#..", "..#.#..", ".#.#.#.", ".#...#.", "#..#..#", "#######"};

	private static String[] icon(int kind) {
		return switch (kind) {
			case 1, 6 -> ICON_ARROW;
			case 2 -> ICON_BLAST;
			case 4 -> ICON_HAZARD;
			default -> ICON_FIST;
		};
	}

	private static void drawIcon(GuiGraphics g, String[] rows, int x, int y, int argb) {
		for (int r = 0; r < rows.length; r++) {
			for (int c = 0; c < rows[r].length(); c++) {
				if (rows[r].charAt(c) == '#') {
					g.fill(x + c, y + r, x + c + 1, y + r + 1, argb);
				}
			}
		}
	}

	private static final int SEGMENTS = 16;
	private static final int RING_R = 30;

	/**
	 * v0.16.1 Spider-Sense ring: soft segments round the crosshair (never over it). The segment facing each sensed threat
	 * glows its colour and pulses, fading as the threat passes; a kind icon sits just outside it, with a chevron when the
	 * threat is above / below. Several at once share the ring without clutter (one icon per segment, the most urgent wins).
	 * Plus a soft ripple along the screen edge on the side of the most urgent threat. All client-side, his eyes only.
	 */
	private static void renderWarning(GuiGraphics g, Minecraft client, Player player) {
		long now = client.level != null ? client.level.getGameTime() : 0L;
		float partial = client.getTimer().getGameTimeDeltaPartialTick(false);
		float[] segGlow = new float[SEGMENTS];
		int[] segColor = new int[SEGMENTS];
		int[] segKind = new int[SEGMENTS];
		int[] segPrio = new int[SEGMENTS];
		int[] segVert = new int[SEGMENTS];
		boolean any = false;
		int bestKind = -1;
		float bestRel = 0f;
		float bestFade = 0f;
		for (int k = 0; k < WARN_KINDS; k++) {
			if (now >= warnUntil[k]) {
				continue;
			}
			any = true;
			float fade = Math.min(1f, (warnUntil[k] - now - partial) / 10f);
			float rel = Mth.wrapDegrees(warnYaw[k] - player.getViewYRot(partial));
			int seg = Math.floorMod(Math.round(rel / (360f / SEGMENTS)), SEGMENTS);
			if (priority(k) > segPrio[seg] || (priority(k) == segPrio[seg] && fade > segGlow[seg])) {
				segPrio[seg] = priority(k);
				segGlow[seg] = Math.max(segGlow[seg], fade);
				segColor[seg] = warningColor(k);
				segKind[seg] = k;
				segVert[seg] = warnVertical[k];
			}
			if (bestKind < 0 || priority(k) > priority(bestKind)) {
				bestKind = k;
				bestRel = rel;
				bestFade = fade;
			}
		}
		if (!any) {
			return;
		}
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		float t = now + partial;
		float pulse = 0.7f + 0.3f * Mth.sin(t * 0.55f);
		for (int s = 0; s < SEGMENTS; s++) {
			float centre = s * (360f / SEGMENTS);
			boolean lit = segGlow[s] > 0f;
			int rgb = lit ? segColor[s] : 0xE8E8F0;
			float alpha = lit ? 0.85f * segGlow[s] * pulse : 0.16f;
			if (!lit) {
				// the idle ring: a few faint dots per segment
				for (float d = -8.5f; d <= 8.5f; d += 1.7f) {
					double ang = Math.toRadians(centre + d);
					int px = cx + (int) Math.round(Math.sin(ang) * RING_R);
					int py = cy - (int) Math.round(Math.cos(ang) * RING_R);
					int a = (int) (255 * alpha * (1f - Math.abs(d) / 11f));
					g.fill(px - 1, py - 1, px + 1, py + 1, (a << 24) | rgb);
				}
			} else {
				// a lit arc: a solid band (bright core, softer rim and outer glow), tapering at its ends
				for (float d = -11f; d <= 11f; d += 0.8f) {
					double ang = Math.toRadians(centre + d);
					float edge = 1f - (Math.abs(d) / 12f) * (Math.abs(d) / 12f);
					double sx = Math.sin(ang);
					double sy = Math.cos(ang);
					for (int rr = -1; rr <= 4; rr++) {
						float ring = rr <= 1 ? 1f : rr == 2 ? 0.55f : rr == 3 ? 0.3f : 0.15f;
						int px = cx + (int) Math.round(sx * (RING_R + rr));
						int py = cy - (int) Math.round(sy * (RING_R + rr));
						int a = (int) (255 * Math.min(1f, 0.95f * segGlow[s] * pulse) * edge * ring);
						g.fill(px, py, px + 1, py + 1, (a << 24) | rgb);
					}
				}
			}
			if (lit) {
				double ang = Math.toRadians(centre);
				int ix = cx + (int) Math.round(Math.sin(ang) * (RING_R + 13)) - 3;
				int iy = cy - (int) Math.round(Math.cos(ang) * (RING_R + 13)) - 3;
				int ia = (int) (255 * Math.min(1f, segGlow[s] * (0.75f + 0.25f * pulse)));
				drawIcon(g, icon(segKind[s]), ix + 1, iy + 1, (ia / 2 << 24));
				drawIcon(g, icon(segKind[s]), ix, iy, (ia << 24) | segColor[s]);
				if (segVert[s] != 0) {
					int chevY = segVert[s] == 1 ? iy - 5 : iy + 9;
					for (int i = 0; i < 3; i++) {
						int dy = segVert[s] == 1 ? i : -i;
						g.fill(ix + 3 - i, chevY + dy, ix + 4 - i, chevY + dy + 1, (ia << 24) | segColor[s]);
						g.fill(ix + 3 + i, chevY + dy, ix + 4 + i, chevY + dy + 1, (ia << 24) | segColor[s]);
					}
				}
			}
		}
		// the ripple along the screen edge on the side of the most urgent threat
		if (bestKind >= 0) {
			int rgb = warningColor(bestKind);
			int w = g.guiWidth();
			int h = g.guiHeight();
			int band = 22;
			for (int i = 0; i < band; i += 2) {
				float ripple = 0.5f + 0.5f * Mth.sin(t * 0.5f - i * 0.45f);
				int a = (int) (110 * bestFade * (1f - i / (float) band) * (0.55f + 0.45f * ripple));
				int col = (a << 24) | rgb;
				if (bestRel < -45f && bestRel > -135f) {
					g.fill(i, 0, i + 2, h, col); // left
				} else if (bestRel > 45f && bestRel < 135f) {
					g.fill(w - i - 2, 0, w - i, h, col); // right
				} else if (Math.abs(bestRel) <= 45f) {
					g.fill(0, i, w, i + 2, col); // ahead: the top edge
				} else {
					g.fill(0, h - i - 2, w, h - i, col); // behind: the bottom edge
				}
			}
		}
	}

	/** The web alarm: an arrow round the crosshair pointing at the touch, with the distance, for four seconds. */
	private static void renderAlarm(GuiGraphics g, Minecraft mc, Player player, float partial) {
		long now = mc.level != null ? mc.level.getGameTime() : 0L;
		if (alarmAt == null || now >= alarmUntil) {
			return;
		}
		Vec3 eye = player.getEyePosition(partial);
		Vec3 d = alarmAt.subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float rel = Mth.wrapDegrees(yaw - player.getViewYRot(partial));
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		float fade = Math.min(1f, (alarmUntil - now) / 20f);
		int alpha = (int) (255 * fade) << 24;
		boolean blink = (now % 8) < 5;
		int col = (blink ? 0xFFFFFF : 0xE0C8FF) | alpha;
		g.pose().pushPose();
		g.pose().translate(cx, cy, 0);
		g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rel));
		// shaft + head, pointing "up" (= ahead) before the rotation
		g.fill(-1, -62, 1, -52, col);
		for (int i = 0; i < 5; i++) {
			g.fill(-i - 1, -66 + i, i + 1, -65 + i, col);
		}
		g.pose().popPose();
		double rad = Math.toRadians(rel);
		int lx = cx + (int) (Math.sin(rad) * 78);
		int ly = cy - (int) (Math.cos(rad) * 78) - 4;
		Component label = Component.translatable("hud.projecthero.spider_man.web_alarm", (int) Math.round(d.length()));
		g.drawCenteredString(mc.font, label, lx, ly, (0xFFE0E8 | alpha));
	}

	/**
	 * v0.16.2 Web Pin: while a target is marked, a cue under the crosshair -- "Pin: aim at a surface -- Shift+V" -- with a
	 * thin bar for the time left on the mark.
	 */
	private static void renderPinCue(GuiGraphics g, Minecraft mc, Player player) {
		SpiderMoveState m = player.getAttachedOrElse(ModAttachments.SPIDER_MOVE_STATE, null);
		long now = mc.level != null ? mc.level.getGameTime() : 0L;
		if (m == null || m.pinUntil <= now) {
			return;
		}
		int cx = g.guiWidth() / 2;
		int y = g.guiHeight() / 2 + 14;
		Component cue = Component.translatable("hud.projecthero.spider_man.pin_cue");
		int w = mc.font.width(cue);
		g.fill(cx - w / 2 - 3, y - 2, cx + w / 2 + 3, y + 13, 0x88000000);
		g.drawCenteredString(mc.font, cue, cx, y, 0xFFF4F4F4);
		float left = Math.min(1f, (m.pinUntil - now) / (float) com.projecthero.mod.spider.SpiderWebPin.MARK_TICKS);
		int bw = Math.round((w + 6) * left);
		g.fill(cx - w / 2 - 3, y + 11, cx - w / 2 - 3 + bw, y + 13, 0xFFE03040);
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
		float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
		// The Spider-Sense warning and the web alarm show regardless of which power holds the ability slots.
		renderWarning(graphics, client, player);
		renderAlarm(graphics, client, player, partial);
		renderPinCue(graphics, client, player);
		// Mirrors SpiderManAbilityManager.hasContext: a mutation selected in the wheel owns the slots.
		ExperimentalState experimental = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (experimental != null && !experimental.activePower.isEmpty()) {
			return;
		}
		if (com.projecthero.mod.power.ThorPowers.wieldsThorWeapon(player) || wearingIronMan(player)) {
			return;
		}

		SpiderMoveState move = player.getAttachedOrElse(ModAttachments.SPIDER_MOVE_STATE, new SpiderMoveState());
		long gameTime = client.level != null ? client.level.getGameTime() : 0L;
		boolean shift = forceShift || player.isShiftKeyDown();

		// v0.17.3: the Minecraft-style key strip, R G Z X C V. The slot shows the tap move (the Shift move while Shift is held):
		// its cooldown sweep + seconds; the other of the two on the durability bar; the hold move's cooldown on the thin bar
		// along the top of the cell. A running stance lights the selection frame; a key the black suit has swapped for a
		// symbiote move carries a purple corner mark; R / V fill while Impact Web / Web Blossom charge.
		VanillaHud.Stack s = VanillaHud.Stack.bottomRight(graphics, 6).keys(6);
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			int o = slot.ordinal();
			String tap = SpiderMoves.idFor(player, o, 0);
			String sh = SpiderMoves.idFor(player, o, 1);
			String hold = SpiderMoves.idFor(player, o, 2);
			boolean swapped = !tap.equals(SpiderMoves.TAP[o]) || !sh.equals(SpiderMoves.SHIFT[o]) || !hold.equals(SpiderMoves.HOLD[o]);
			String shown = shift ? sh : tap;
			int cd = SpiderMan.abilityCooldownRemaining(player, shown);
			s.slot(i, VanillaHud.keyLabel(slot), true, shift, cdFrac(player, shown), VanillaHud.seconds(cd),
					cdFrac(player, shift ? tap : sh), active(move, o, state));
			VanillaHudExtras.topBar(s, i, cdFrac(player, hold));
			if (swapped) {
				VanillaHudExtras.corner(s, i, COLOR_SYMBIOTE);
			}
			if (o == 0 && move.has(SpiderMoveState.FLAG_CHARGING_IMPACT)) {
				s.hold(i, Math.min(1f, (gameTime - move.chargeStart) / (float) Math.max(1, SpiderManConfig.moves().impactChargeTicks)));
			}
			if (o == 4) {
				int blossom = player.getAttachedOrElse(ModAttachments.SPIDER_BLOSSOM_CHARGE, 0);
				if (blossom > 0) {
					s.hold(i, Math.min(1f, blossom / (float) com.projecthero.mod.spider.SpiderAbilities.blossomChargeTicks(false)));
				}
			}
		}
		stealthEye(graphics, player, s.stripX() - 15, s.stripY() + VanillaHud.HEIGHT / 2 - 3);

		// Web Reserve. The ceiling doubles once bonded with a Symbiote (v0.9.10).
		float webMax = SpiderWebReserve.maxFor(player);
		float ratio = Math.max(0.0f, Math.min(1.0f, state.webReserve / webMax));
		s.meter(Component.translatable("hud.projecthero.vanilla.spider_man.web"), ratio, ratio < 0.2f ? COLOR_WEB_LOW : COLOR_WEB,
				(int) Math.ceil(ratio * 100.0f) + "%", ratio < 0.2f ? COLOR_WEB_LOW : COLOR_WEB_TEXT);
		// the Web Blossom buildup while V is held
		int blossom = player.getAttachedOrElse(ModAttachments.SPIDER_BLOSSOM_CHARGE, 0);
		if (blossom > 0) {
			float b = Math.min(1.0f, blossom / (float) com.projecthero.mod.spider.SpiderAbilities.blossomChargeTicks(false));
			s.meter(Component.translatable("hud.projecthero.vanilla.spider_man.blossom"), b, b >= 1f ? 0xFFFF6070 : COLOR_WEB,
					Math.round(b * 100f) + "%", b >= 1f ? 0xFFFFF0A0 : VanillaHud.WHITE);
		}
		// v0.16.2 Black Suit: the Hunger bar and its status words ride on this column
		List<VanillaHud.Tag> suitTags = new java.util.ArrayList<>();
		SymbioteHud.blackSuitRows(s, player, gameTime, suitTags);

		s.title(Component.translatable("projecthero.spider_man.name"), 0xFFFF6070);
		List<VanillaHud.Tag> tags = new java.util.ArrayList<>();
		// v0.16.1: the N wall-crawl switch, and Shift+N's muted notification sounds
		boolean crawlOn = !player.getAttachedOrElse(ModAttachments.SPIDER_WALL_CRAWL_OFF, false);
		tags.add(new VanillaHud.Tag(Component.translatable(crawlOn ? "hud.projecthero.spider_man.wall_crawl_on"
				: "hud.projecthero.spider_man.wall_crawl_off").getString(), crawlOn ? VanillaHud.GREEN : VanillaHud.GREY));
		if (com.projecthero.mod.spider.SpiderMoves.soundsOff(player)) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.spider_man.sounds_off").getString(), VanillaHud.GREY));
		}
		tags.addAll(suitTags);
		s.tags(tags);
		Component status = status(move, gameTime);
		if (status != null) {
			s.wrapped(status, VanillaHud.YELLOW);
		}
		if (MoveListOverlay.held()) {
			s.moveList(names(player)); // v0.16.6: the shared move list, above everything
		}
	}

	/** Set only by a client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	/** 0..1 of move {@code id}'s cooldown still to run. */
	private static float cdFrac(Player player, String id) {
		int cd = SpiderMan.abilityCooldownRemaining(player, id);
		return cd <= 0 ? 0f : Math.min(1f, cd / (float) Math.max(1, SpiderMoves.maxCooldown(id)));
	}

	/**
	 * v0.16.2: the stealth eye (11 x 7 px, left of the row) -- wide open and red when he is exposed (he just fought,
	 * or is swinging), open when walking, half shut when sneaking, a closed line when hidden (crawling / perched /
	 * hanging, or sneaking in the dark).
	 */
	private static void stealthEye(GuiGraphics g, Player player, int x, int y) {
		int level = player.getAttachedOrElse(ModAttachments.SPIDER_STEALTH, 1);
		int outline = switch (level) {
			case 0 -> 0xFFFF5050;
			case 1 -> 0xFFE8E0E0;
			case 2 -> 0xFFB8C8D8;
			default -> 0xFF7FA88A;
		};
		if (level >= 3) {
			// closed: a lid line with lashes
			g.fill(x + 1, y + 3, x + 10, y + 4, outline);
			g.fill(x + 2, y + 4, x + 3, y + 6, outline);
			g.fill(x + 5, y + 4, x + 6, y + 6, outline);
			g.fill(x + 8, y + 4, x + 9, y + 6, outline);
			return;
		}
		// the almond outline
		g.fill(x + 3, y, x + 8, y + 1, outline);
		g.fill(x + 1, y + 1, x + 3, y + 2, outline);
		g.fill(x + 8, y + 1, x + 10, y + 2, outline);
		g.fill(x, y + 2, x + 1, y + 5, outline);
		g.fill(x + 10, y + 2, x + 11, y + 5, outline);
		g.fill(x + 1, y + 5, x + 3, y + 6, outline);
		g.fill(x + 8, y + 5, x + 10, y + 6, outline);
		g.fill(x + 3, y + 6, x + 8, y + 7, outline);
		// the iris
		g.fill(x + 4, y + 2, x + 7, y + 5, level == 0 ? 0xFFFF5050 : 0xFF303040);
		if (level == 2) {
			// half shut: the upper lid drawn down over the top half
			g.fill(x + 1, y + 1, x + 10, y + 3, 0xFF5A6070);
		}
	}

	/** The key's stance is running (outline it). */
	private static boolean active(SpiderMoveState m, int i, SpiderManState s) {
		return switch (i) {
			case 0 -> m.has(SpiderMoveState.FLAG_CHARGING_IMPACT);
			case 2 -> m.has(SpiderMoveState.FLAG_COUNTER) || m.has(SpiderMoveState.FLAG_AIR_COMBO);
			case 3 -> m.has(SpiderMoveState.FLAG_SWING_THROW);
			case 4 -> m.has(SpiderMoveState.FLAG_CHARGING_BLOSSOM);
			case 5 -> m.has(SpiderMoveState.FLAG_WEAVING) || m.has(SpiderMoveState.FLAG_ANCHOR_A);
			default -> false;
		};
	}

	/** v0.16.6: Left-Alt -> the shared move list (tap move, Shift move, then the hold move); v0.17.3: in R G Z X C V order. */
	private static List<MoveListOverlay.Row> names(Player player) {
		List<MoveListOverlay.Row> rows = new java.util.ArrayList<>();
		String hold = Component.translatable("projecthero.guide.spider_man.how.hold").getString();
		hold = hold.isEmpty() ? hold : Character.toUpperCase(hold.charAt(0)) + hold.substring(1);
		for (AbilitySlot slot : VanillaHud.ORDER) {
			int i = slot.ordinal();
			rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot),
					Component.translatable("projecthero.spider_man.ability." + SpiderMoves.idFor(player, i, 0)),
					Component.translatable("projecthero.spider_man.ability." + SpiderMoves.idFor(player, i, 1)),
					Component.literal(hold + ": ").append(
							Component.translatable("projecthero.spider_man.ability." + SpiderMoves.idFor(player, i, 2)))));
		}
		rows.add(MoveListOverlay.Row.of("", Component.translatable("hud.projecthero.spider_man.traversal_hint"), null));
		return rows;
	}

	/** Weave Mode / anchor A / charging status for the line under the Web Reserve, or null. */
	private static Component status(SpiderMoveState m, long now) {
		if (m.has(SpiderMoveState.FLAG_WEAVING)) {
			return Component.translatable("hud.projecthero.spider_man.status.weave", m.strands,
					SpiderManConfig.webs().weaveMaxStrands);
		}
		if (m.has(SpiderMoveState.FLAG_ANCHOR_A)) {
			return Component.translatable("hud.projecthero.spider_man.status.anchor");
		}
		if (m.has(SpiderMoveState.FLAG_CHARGING_IMPACT)) {
			int pct = (int) (100 * Math.min(1f, (now - m.chargeStart) / (float) Math.max(1, SpiderManConfig.moves().impactChargeTicks)));
			return Component.translatable("hud.projecthero.spider_man.status.impact", pct);
		}
		if (m.has(SpiderMoveState.FLAG_COUNTER)) {
			return Component.translatable("hud.projecthero.spider_man.status.counter");
		}
		if (m.has(SpiderMoveState.FLAG_AIR_COMBO)) {
			return Component.translatable("hud.projecthero.spider_man.status.air_combo");
		}
		if (m.has(SpiderMoveState.FLAG_HANGING) && m.has(SpiderMoveState.FLAG_HANG_UPSIDE)) {
			return Component.translatable("hud.projecthero.spider_man.status.hanging_upside"); // v0.16.2
		}
		if (m.has(SpiderMoveState.FLAG_HANGING)) {
			return Component.translatable("hud.projecthero.spider_man.status.hanging");
		}
		return null;
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
