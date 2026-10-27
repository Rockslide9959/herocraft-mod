package com.projecthero.mod.client.quirk.zerogravity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.meter.QuirkBand;
import com.projecthero.mod.quirk.meter.QuirkMeters;
import com.projecthero.mod.quirk.zerogravity.ZeroGravity;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1 Zero Gravity revamp, the screen side of Nausea's bands (docs 3.7) and the touch-mark list:
 * <ul>
 *   <li>the sway ({@code ZeroGravityNauseaSwayMixin}): Queasy (50-84) a gentle roll and bob of the world view, Pushing it
 *       (85-99) a strong one; scaled by vanilla's Distortion Effects slider;</li>
 *   <li>a sickly green wobble at the screen's edge while Pushing it / Sick;</li>
 *   <li>the floated entities near each Zero Gravity user ({@code ZeroGravityMarksPayload}), for {@link ZeroGravityPrints}.</li>
 * </ul>
 */
public final class ZeroGravityHud {
	/** Owner entity id -> [the ids she floats], and when the list goes stale. */
	private static final Map<Integer, int[]> MARKS = new HashMap<>();
	private static final Map<Integer, Long> MARKS_UNTIL = new HashMap<>();

	private ZeroGravityHud() {
	}

	static void receive(int owner, List<Integer> ids) {
		Minecraft mc = Minecraft.getInstance();
		synchronized (MARKS) {
			if (ids.isEmpty()) {
				MARKS.remove(owner);
				MARKS_UNTIL.remove(owner);
				return;
			}
			int[] a = new int[ids.size()];
			for (int i = 0; i < a.length; i++) {
				a[i] = ids.get(i);
			}
			MARKS.put(owner, a);
			MARKS_UNTIL.put(owner, mc.level == null ? 0L : mc.level.getGameTime() + 40L);
		}
	}

	static void reset() {
		synchronized (MARKS) {
			MARKS.clear();
			MARKS_UNTIL.clear();
		}
	}

	/** Every marked entity id right now (stale lists dropped). */
	static int[][] marks() {
		Minecraft mc = Minecraft.getInstance();
		long now = mc.level == null ? 0L : mc.level.getGameTime();
		synchronized (MARKS) {
			MARKS_UNTIL.entrySet().removeIf(e -> {
				if (e.getValue() < now) {
					MARKS.remove(e.getKey());
					return true;
				}
				return false;
			});
			return MARKS.values().toArray(new int[0][]);
		}
	}

	/** How strongly the local player's screen sways (0 none, 1 Queasy, 2 Pushing it / Sick). */
	private static int swayLevel(Player p) {
		if (p == null || !ZeroGravity.is(p)) {
			return 0;
		}
		if (Quirks.overloaded(p)) {
			return 2;
		}
		QuirkBand b = QuirkMeters.clientBand(p);
		return switch (b) {
			case SWEET -> 1;
			case RED, BEYOND -> 2;
			default -> 0;
		};
	}

	private static float scale() {
		return Minecraft.getInstance().options.screenEffectScale().get().floatValue();
	}

	/** Degrees of roll on the world view this frame. */
	public static float swayRoll(float partial) {
		Minecraft mc = Minecraft.getInstance();
		int lvl = swayLevel(mc.player);
		if (lvl == 0 || mc.player == null) {
			return 0f;
		}
		float t = mc.player.tickCount + partial;
		float amp = (lvl == 1 ? 1.3f : 3.2f) * scale();
		return amp * (Mth.sin(t * 0.07f) * 0.7f + Mth.sin(t * 0.131f + 1.3f) * 0.3f);
	}

	/** Blocks of bob on the world view this frame. */
	public static float swayBob(float partial) {
		Minecraft mc = Minecraft.getInstance();
		int lvl = swayLevel(mc.player);
		if (lvl == 0 || mc.player == null) {
			return 0f;
		}
		float t = mc.player.tickCount + partial;
		return (lvl == 1 ? 0.015f : 0.04f) * scale() * Mth.sin(t * 0.09f);
	}

	static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (p == null || mc.options.hideGui || swayLevel(p) < 2) {
			return;
		}
		int w = g.guiWidth(), h = g.guiHeight();
		float t = p.tickCount + delta.getGameTimeDeltaPartialTick(false);
		float pulse = 0.55f + 0.25f * Mth.sin(t * 0.11f);
		int band = Math.max(20, Math.min(w, h) / 6);
		int wob = (int) (Mth.sin(t * 0.07f) * band * 0.25f);
		int a = (int) (pulse * 110 * Math.max(0.35f, scale()));
		int edge = (a << 24) | 0x6FB040;
		int clear = 0x006FB040;
		g.fillGradient(0, 0, w, band + wob, edge, clear);
		g.fillGradient(0, h - band + wob, w, h, clear, edge);
	}
}
