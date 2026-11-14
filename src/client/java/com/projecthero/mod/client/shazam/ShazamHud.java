package com.projecthero.mod.client.shazam;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.shazam.Shazam;
import com.projecthero.mod.shazam.ShazamAbilityManager;
import com.projecthero.mod.shazam.ShazamConfig;
import com.projecthero.mod.shazam.data.ShazamState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Shazam HUD, bottom-right, on the shared Minecraft-style column ({@link VanillaHud.Stack}, like Raven's since v0.17.4):
 * <pre>
 *   Flying  Achilles 0:03  Family 9:12          status tags (wrap onto more lines when they don't fit)
 *   SHAZAM!
 *   Lightning [boss bar ======---]  64          the Lightning bar: a Minecraft boss bar, gold (v0.17.4; was a Slab)
 *   +----+----+----+----+----+----+             R G Z X C V while transformed: cooldowns; a move he can't pay for (or, family,
 *   | R  | G  | Z  | X  | C  | V  |             can't use) is greyed; yellow while Shift shows the Shift moves
 *   +----+----+----+----+----+----+
 * </pre>
 * Not transformed, only the title, the bar and the hint (say the word in chat). The world going dark under an Eternity Strike nearby
 * and Wisdom of Solomon's crosshair hint are drawn here too. Hold Left-Alt for the move list.
 */
public final class ShazamHud {
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };
	private static final int GOLD_TEXT = 0xFFFFC83A;
	private static final int RED_TEXT = 0xFFFF5A5A;

	/** Set only by the client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	private ShazamHud() {
	}

	static String clock(long ticks) {
		long s = (ticks + 19) / 20;
		return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		eternityDark(g, mc, partial);
		if (mc.options.hideGui) {
			return;
		}
		ShazamState s = Shazam.peek(p);
		if (s == null || !Shazam.canSpeak(p)) {
			return;
		}
		var exp = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		boolean on = Shazam.transformed(p);
		if (on && Shazam.wise(p)) {
			ShazamWisdom.crosshairHint(g, mc);
		}
		long now = mc.level.getGameTime();
		VanillaHud.Stack hud = VanillaHud.Stack.bottomRight(g, SLOTS.length);
		if (on) {
			hud.keys(SLOTS.length);
			keys(g, p, s, hud.stripX(), hud.stripY());
		} else {
			// Billy: the hint where the keys would be, wrapped to the column's width
			hud.wrapped(Component.translatable("hud.projecthero.shazam.hint"), VanillaHud.GREY);
		}
		// the Lightning bar: a Minecraft boss bar (v0.17.4, user: "make the bar the Minecraft style bar"), gold
		float frac = Mth.clamp(s.lightning / ShazamConfig.LIGHTNING_MAX, 0f, 1f);
		hud.meter(Component.translatable("hud.projecthero.shazam.lightning"), frac, 0xFFFFC83A, String.valueOf(Math.round(s.lightning)),
				VanillaHud.WHITE);
		// the title
		hud.title(Component.translatable(on ? (Shazam.isFamily(p) ? "hud.projecthero.shazam.title_family" : "hud.projecthero.shazam.title")
				: "hud.projecthero.shazam.title_billy"), on ? GOLD_TEXT : VanillaHud.WHITE);
		// status tags, wrapped to the column's width
		List<VanillaHud.Tag> tags = new ArrayList<>();
		if (Shazam.isFlying(p)) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.shazam.flying").getString(), VanillaHud.WHITE));
		}
		if (Shazam.standing(p)) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.shazam.stand").getString() + " " + clock(s.standUntil - now), GOLD_TEXT));
		}
		if (Shazam.storming(p)) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.shazam.storm").getString() + " " + clock(s.stormUntil - now), GOLD_TEXT));
		}
		if (Shazam.wise(p)) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.shazam.wisdom").getString() + " " + clock(s.wisdomUntil - now), 0xFF9AD0FF));
		}
		if (Shazam.isFamily(p)) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.shazam.family").getString() + " " + clock(s.familyUntil - now), GOLD_TEXT));
		} else if (s.hasPower && s.familySize(now) > 0) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.shazam.family_size", s.familySize(now)).getString(), GOLD_TEXT));
		}
		long gap = s.lastWord + ShazamConfig.WORD_GAP - now;
		if (gap > 0 && s.lastWord != 0) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.shazam.gap").getString() + " " + clock(gap), RED_TEXT));
		}
		hud.tags(tags);
		List<MoveListOverlay.Row> rows = new ArrayList<>();
		if (on && MoveListOverlay.held()) {
			for (AbilitySlot slot : SLOTS) {
				String[] ids = ShazamAbilityManager.idsOf(slot);
				if (ids.length == 0) {
					continue;
				}
				rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.shazam.ability." + ids[0]),
						ids.length > 1 ? Component.translatable("projecthero.shazam.ability." + ids[1]) : null));
			}
		}
		hud.moveList(rows);
	}

	private static void keys(GuiGraphics g, LocalPlayer p, ShazamState s, int x0, int y0) {
		boolean shift = forceShift || p.isShiftKeyDown();
		VanillaHud.strip(g, x0, y0, SLOTS.length);
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			String[] ids = ShazamAbilityManager.idsOf(slot);
			if (ids.length == 0) {
				continue;
			}
			String shown = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			int cd = ShazamAbilityManager.cooldownRemaining(p, shown);
			float frac = Math.min(1f, cd / (float) Math.max(1, ShazamAbilityManager.maxCooldown(shown)));
			float otherFrac = other == null ? 0f
					: Math.min(1f, ShazamAbilityManager.cooldownRemaining(p, other) / (float) Math.max(1, ShazamAbilityManager.maxCooldown(other)));
			boolean usable = ShazamAbilityManager.allowed(p, shown)
					&& (p.getAbilities().instabuild || s.lightning + 1.0e-3f >= ShazamAbilityManager.cost(shown));
			boolean active = (ShazamAbilityManager.ACHILLES_STAND.equals(shown) && Shazam.standing(p))
					|| (ShazamAbilityManager.ZEUS_STORM.equals(shown) && Shazam.storming(p))
					|| (ShazamAbilityManager.WISDOM.equals(shown) && Shazam.wise(p))
					|| (ShazamAbilityManager.ETERNITY_STRIKE.equals(shown) && Shazam.eternityAge(p) >= 0);
			VanillaHud.slot(g, x0, y0, i, keyLabel(slot), usable, shift, frac, cd > 0 ? (cd + 19) / 20 : 0, otherFrac, active);
		}
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}

	/** Eternity Strike nearby: the sky (here, the whole view) darkens while he rises, flashes as the bolt hits, clears after the slam. */
	private static void eternityDark(GuiGraphics g, Minecraft mc, float partial) {
		float dark = 0f;
		float flash = 0f;
		for (AbstractClientPlayer other : mc.level.players()) {
			int age = Shazam.eternityAge(other);
			if (age < 0 || other.distanceToSqr(mc.player) > 64 * 64) {
				continue;
			}
			float t = age + partial;
			dark = Math.max(dark, Mth.clamp(t / ShazamConfig.ETERNITY_RISE_TICKS, 0f, 1f) * 0.55f);
			float sinceBolt = t - ShazamConfig.ETERNITY_RISE_TICKS;
			if (sinceBolt >= 0f && sinceBolt < 6f) {
				flash = Math.max(flash, 1f - sinceBolt / 6f);
			}
		}
		if (dark > 0.01f) {
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (dark * 255f) << 24) | 0x0A0A1A);
		}
		if (flash > 0.01f) {
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (flash * 0.6f * 255f) << 24) | 0xF4F8FF);
		}
	}
}
