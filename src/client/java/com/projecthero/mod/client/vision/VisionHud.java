package com.projecthero.mod.client.vision;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.vision.Vision;
import com.projecthero.mod.vision.VisionAbilityManager;
import com.projecthero.mod.vision.VisionConfig;
import com.projecthero.mod.vision.data.VisionState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Vision HUD, bottom-right, built from the game's own pieces like the other recent powers':
 * <pre>
 *   Flying  Hologram  Intangible  Diamond     status tags (wrap onto more lines when they don't fit)
 *   VISION
 *   Mind Stone [Meter =========-]   64         the Mind Stone bar (the user's "Meter" style), gold
 *   +----+----+----+----+----+----+            R G Z X C V: cooldowns; a move he can't pay for (or can't use while intangible) is
 *   | R  | G  | Z  | X  | C  | V  |            greyed; a running toggle lights up; yellow while Shift shows the Shift moves
 *   +----+----+----+----+----+----+
 * </pre>
 * Hold Left-Alt for the move list.
 */
public final class VisionHud {
	private static final int MARGIN = 4;
	private static final int WIDTH = VanillaHud.width(6);
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };
	private static final int GOLD_TEXT = 0xFFFFD23C;
	private static final int PHASE_TEXT = 0xFFFFF0B0;
	private static final int DENSE_TEXT = 0xFFB8B8C8;

	/** Set only by the client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;
	/** Set only by the client screenshot harness: phasing sight off, for "before" shots. */
	public static volatile boolean phaseSightOff;

	/**
	 * Phasing sight is on for this player: Vision's Intangibility wall-phasing (no in-wall overlay, spectator-style culling, the
	 * gold edge glow).
	 */
	public static boolean phaseSight(net.minecraft.world.entity.player.Player player) {
		return !phaseSightOff && Vision.wallPhasing(player);
	}

	private VisionHud() {
	}

	private record Tag(String text, int colour) {
	}

	/**
	 * Phasing sight: while his head is inside a wall he is phasing through (the in-wall overlay and culling are lifted by
	 * {@code VisionPhaseSightMixin} / {@code VisionPhaseCullMixin}), a faint gold glow fades in from the screen edges so he knows
	 * he's inside something. First person only.
	 */
	private static void phaseTint(GuiGraphics g, Minecraft mc, LocalPlayer p) {
		if (!phaseSight(p) || !mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		BlockPos eye = BlockPos.containing(p.getEyePosition());
		BlockState in = mc.level.getBlockState(eye);
		if (!in.isViewBlocking(mc.level, eye)) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		int band = Math.max(8, Math.min(w, h) / 7);
		int steps = 12;
		float pulse = 0.85f + 0.15f * Mth.sin(p.tickCount * 0.12f);
		for (int i = 0; i < steps; i++) {
			int a = Math.round(70 * pulse * (1f - i / (float) steps) * (1f - i / (float) steps));
			if (a <= 0) {
				continue;
			}
			int c = (a << 24) | 0xFFC83C;
			int t0 = band * i / steps;
			int t1 = band * (i + 1) / steps;
			g.fill(0, t0, w, t1, c); // top
			g.fill(0, h - t1, w, h - t0, c); // bottom
			g.fill(t0, t1, t1, h - t1, c); // left (between the top and bottom strips already drawn)
			g.fill(w - t1, t1, w - t0, h - t1, c); // right
		}
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			return;
		}
		phaseTint(g, mc, p);
		if (mc.options.hideGui) {
			return;
		}
		VisionState s = Vision.peek(p);
		if (s == null || !s.hasPower) {
			return;
		}
		var exp = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		Font font = mc.font;
		int x0 = g.guiWidth() - MARGIN - WIDTH;
		int bottom = g.guiHeight() - MARGIN;
		if (x0 < g.guiWidth() / 2 + 91 + 2) {
			bottom = g.guiHeight() - 41; // a narrow window: clear of the hotbar and food bar
		}
		int y0 = bottom - VanillaHud.HEIGHT;
		keys(g, p, s, x0, y0);
		int by = y0 - 8;
		// the Mind Stone bar (Meter)
		float frac = Mth.clamp(s.energy / VisionConfig.ENERGY_MAX, 0f, 1f);
		VanillaHud.meter(g, x0, by, WIDTH, Component.translatable("hud.projecthero.vision.energy"), frac, 0xFFD23C, String.valueOf(Math.round(s.energy)),
				GOLD_TEXT);
		// the title
		int ty = by - 14;
		String title = Component.translatable("hud.projecthero.vision.title").getString();
		g.drawString(font, font.plainSubstrByWidth(title, WIDTH), x0, ty, GOLD_TEXT, true);
		// status tags, wrapped to the HUD's width (bottom line first, upward)
		List<Tag> tags = new ArrayList<>();
		if (Vision.isFlying(p)) {
			tags.add(new Tag(Component.translatable("hud.projecthero.vision.flying").getString(), VanillaHud.WHITE));
		}
		if (Vision.hologram(p)) {
			tags.add(new Tag(Component.translatable("hud.projecthero.vision.hologram").getString(), 0xFF9AD0FF));
		}
		if (Vision.intangible(p)) {
			tags.add(new Tag(Component.translatable("hud.projecthero.vision.intangible").getString(), PHASE_TEXT));
		}
		if (Vision.dense(p)) {
			tags.add(new Tag(Component.translatable("hud.projecthero.vision.dense").getString(), DENSE_TEXT));
		}
		if (Vision.unleashing(p)) {
			tags.add(new Tag(Component.translatable("hud.projecthero.vision.unleashed").getString(), GOLD_TEXT));
		}
		int top = ty;
		if (!tags.isEmpty()) {
			List<List<Tag>> lines = new ArrayList<>();
			List<Tag> line = new ArrayList<>();
			int w = 0;
			for (Tag t : tags) {
				String text = font.plainSubstrByWidth(t.text, WIDTH);
				int tw = font.width(text);
				if (!line.isEmpty() && w + 6 + tw > WIDTH) {
					lines.add(line);
					line = new ArrayList<>();
					w = 0;
				}
				line.add(new Tag(text, t.colour));
				w += (line.size() > 1 ? 6 : 0) + tw;
			}
			lines.add(line);
			for (List<Tag> l : lines) {
				top -= 10;
				int x = x0;
				for (Tag t : l) {
					g.drawString(font, t.text, x, top, t.colour, true);
					x += font.width(t.text) + 6;
				}
			}
		}
		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : SLOTS) {
				String[] ids = VisionAbilityManager.idsOf(slot);
				if (ids.length == 0) {
					continue;
				}
				rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.vision.ability." + ids[0]),
						ids.length > 1 ? Component.translatable("projecthero.vision.ability." + ids[1]) : null));
			}
			MoveListOverlay.draw(g, x0 + WIDTH, top - 4, rows);
		}
	}

	private static void keys(GuiGraphics g, LocalPlayer p, VisionState s, int x0, int y0) {
		boolean shift = forceShift || p.isShiftKeyDown();
		VanillaHud.strip(g, x0, y0, SLOTS.length);
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			String[] ids = VisionAbilityManager.idsOf(slot);
			if (ids.length == 0) {
				continue;
			}
			String shown = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			int cd = VisionAbilityManager.cooldownRemaining(p, shown);
			float frac = Math.min(1f, cd / (float) Math.max(1, VisionAbilityManager.maxCooldown(shown)));
			float otherFrac = other == null ? 0f
					: Math.min(1f, VisionAbilityManager.cooldownRemaining(p, other) / (float) Math.max(1, VisionAbilityManager.maxCooldown(other)));
			boolean usable = VisionAbilityManager.allowed(p, shown)
					&& (p.getAbilities().instabuild || s.energy + 1.0e-3f >= VisionAbilityManager.cost(shown));
			boolean active = (VisionAbilityManager.BEAM.equals(shown) && Vision.beaming(p))
					|| (VisionAbilityManager.UNLEASH.equals(shown) && Vision.unleashing(p))
					|| (VisionAbilityManager.HOLOGRAM.equals(shown) && Vision.hologram(p))
					|| (VisionAbilityManager.PHASE.equals(shown) && Vision.intangible(p))
					|| (VisionAbilityManager.DENSITY.equals(shown) && Vision.dense(p));
			VanillaHud.slot(g, x0, y0, i, keyLabel(slot), usable || active, shift, frac, cd > 0 ? (cd + 19) / 20 : 0, otherFrac, active);
		}
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}
}
