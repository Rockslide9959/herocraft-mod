package com.projecthero.mod.client.blackpanther;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.blackpanther.BlackPanther;
import com.projecthero.mod.blackpanther.BlackPantherAbilityManager;
import com.projecthero.mod.blackpanther.BlackPantherConfig;
import com.projecthero.mod.blackpanther.data.BlackPantherState;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.hero.AbilitySlot;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.17.3: the Black Panther HUD (bottom-right), mono style: the Kinetic hairline, the stance and Senses timers, the six keys.
 * Also the Ancestral Plane: a purple sky over the whole screen for the ten seconds after the soup.
 */
public final class BlackPantherHud {
	private static final int BOX = 20;
	private static final int GAP = 2;
	private static final int MARGIN = 4;
	private static final int BAR_H = 3;
	private static final int BAR_STEP = 5;

	private static final int BOX_BG = 0xD0080808;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int BORDER_ACTIVE = 0xFFA05CFF;
	private static final int KEY = 0xFFB4B4B4;
	private static final int NAME = 0xFFE2E2E2;
	private static final int BAR_BG = 0xAA0C0C0C;
	private static final int COOLDOWN = 0xB0000000;
	private static final int KINETIC = 0xFF9A4CFF;
	private static final int ABSORB = 0xFFD9B8FF;

	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };

	private record Bar(float ratio, int color) {
	}

	private BlackPantherHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		BlackPantherState s = mc.player.getAttachedOrElse(ModAttachments.BLACK_PANTHER_STATE, null);
		if (s == null) {
			return;
		}
		if (!s.hasPower && s.visionUntil > 0L) {
			renderVision(g, s, mc.level.getGameTime(), delta.getGameTimeDeltaPartialTick(false));
			return;
		}
		if (!s.hasPower || mc.options.hideGui) {
			return;
		}
		float hold = BlackPantherClient.holdProgress();
		if (hold > 0.1f) {
			// holding H: a thin bar over the hotbar fills toward the necklace coming off (or going back on)
			int w = 82;
			int x = g.guiWidth() / 2 - w / 2;
			int y = g.guiHeight() - 62;
			g.fill(x, y, x + w, y + BAR_H, BAR_BG);
			g.fill(x, y, x + Math.round(w * hold), y + BAR_H, KINETIC);
			g.drawCenteredString(mc.font, Component.translatable(s.necklaceOff ? "hud.projecthero.black_panther.necklace_on"
					: "hud.projecthero.black_panther.necklace_off"), g.guiWidth() / 2, y - 10, NAME);
		}
		if (!s.suited) {
			return;
		}
		var exp = mc.player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		long now = mc.level.getGameTime();
		int totalW = BOX * SLOTS.length + GAP * (SLOTS.length - 1);
		int x0 = g.guiWidth() - MARGIN - totalW;
		int y0 = g.guiHeight() - MARGIN - BOX;

		List<Bar> bars = new ArrayList<>();
		bars.add(new Bar(s.kinetic / BlackPantherConfig.KINETIC_MAX, KINETIC));
		if (s.absorbing) {
			bars.add(new Bar(1f - s.absorbed / BlackPantherConfig.ABSORB_MAX, ABSORB));
		}
		int by = y0 - 2 - BAR_H;
		for (Bar b : bars) {
			float r = Math.max(0f, Math.min(1f, b.ratio()));
			g.fill(x0, by, x0 + totalW, by + BAR_H, BAR_BG);
			g.fill(x0, by, x0 + Math.round(totalW * r), by + BAR_H, b.color());
			by -= BAR_STEP;
		}
		g.drawString(mc.font, Component.translatable("hud.projecthero.black_panther.title"), x0, by - 7, NAME, false);

		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : SLOTS) {
				String[] ids = BlackPantherAbilityManager.idsOf(slot);
				if (ids.length > 0) {
					rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.black_panther.ability." + ids[0]),
							ids.length > 1 ? Component.translatable("projecthero.black_panther.ability." + ids[1]) : null));
				}
			}
			// N has no HUD box (the project rule): the claws and the mask are listed here instead
			rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(AbilitySlot.SLOT_8), Component.translatable("projecthero.black_panther.ability.claws"),
					Component.translatable("projecthero.black_panther.ability.mask")));
			rows.add(MoveListOverlay.Row.of("Hold " + MoveListOverlay.key(AbilitySlot.SLOT_7),
					Component.translatable("projecthero.black_panther.ability.necklace"), null));
			MoveListOverlay.draw(g, x0 + totalW, by - 7, rows);
		}
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			int x = x0 + i * (BOX + GAP);
			int cd = 0;
			int max = 1;
			for (String id : BlackPantherAbilityManager.idsOf(slot)) {
				int c = BlackPanther.cooldownRemaining(mc.player, id);
				if (c > cd) {
					cd = c;
					max = Math.max(1, BlackPantherAbilityManager.maxCooldown(id));
				}
			}
			boolean active = (slot == AbilitySlot.SLOT_5 && (s.stalking || s.senses)) || (slot == AbilitySlot.SLOT_6 && s.absorbing);
			g.fill(x, y0, x + BOX, y0 + BOX, BOX_BG);
			g.renderOutline(x, y0, BOX, BOX, active ? BORDER_ACTIVE : BORDER);
			g.drawString(mc.font, keyLabel(slot), x + 2, y0 + 2, KEY, false);
			if (cd > 0) {
				int h = (int) ((BOX - 2) * Math.min(1f, cd / (float) max));
				g.fill(x + 1, y0 + BOX - 1 - h, x + BOX - 1, y0 + BOX - 1, COOLDOWN);
				g.drawCenteredString(mc.font, String.valueOf((cd + 19) / 20), x + BOX / 2, y0 + BOX / 2 - 4, 0xFFFFFFFF);
			}
		}
	}

	/** The Ancestral Plane: a violet sky with slow stars, fading in and out. */
	private static void renderVision(GuiGraphics g, BlackPantherState s, long now, float partial) {
		float left = s.visionUntil - now - partial;
		float age = BlackPantherConfig.VISION_TICKS - left;
		float fade = Mth.clamp(Math.min(age, left) / 30f, 0f, 1f);
		int w = g.guiWidth();
		int h = g.guiHeight();
		int a = Math.round(fade * 245);
		g.fillGradient(0, 0, w, h, (a << 24) | 0x12062E, (a << 24) | 0x4A1A8C);
		for (int i = 0; i < 90; i++) {
			float sx = Mth.frac(Mth.sin(i * 12.9898f) * 43758.547f);
			float sy = Mth.frac(Mth.sin(i * 78.233f) * 24634.633f);
			float drift = (age * (0.0004f + sx * 0.0006f)) % 1f;
			int x = Math.round(((sx + drift) % 1f) * w);
			int y = Math.round(sy * h * 0.85f);
			float twinkle = 0.5f + 0.5f * Mth.sin(age * 0.09f + i);
			int sa = Math.round(fade * (110 + 145 * twinkle));
			g.fill(x, y, x + 1 + i % 2, y + 1 + i % 2, (sa << 24) | 0xE9D8FF);
		}
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}
}
