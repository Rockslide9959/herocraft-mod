package com.projecthero.mod.client.undead;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadAbilityManager;
import com.projecthero.mod.undead.UndeadBoneSword;
import com.projecthero.mod.undead.UndeadConfig;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.17.3 (user: "can you give undead the minecraft style GUI please"): the Undead HUD, bottom-right, drawn from the game's own pieces
 * ({@link VanillaHud}), like the Omnitrix and the Stands:
 * <pre>
 *   Undead                        [body]     the title (Victor in red while he is out), the little pixel body: each part shaded
 *   Regen: Normal                          by its HP, gone parts dark, regrowing ones filling from the joint
 *   Regrowth [boss bar ====---] 64%        while a part regrows: a pink boss bar
 *   Victor   [boss bar ======-] 21s        while Victor is out: a red boss bar
 *   +----+----+----+----+----+----+        the six keys, a short hotbar: R G Z X C V, yellow while Shift shows the Shift moves;
 *   | R  | G  | Z  | X  | C  | V  |        the shown move's cooldown as the item-cooldown sweep and a stack count, the other
 *   +----+----+----+----+----+----+        move's as a durability bar, the selection frame on a move that is running;
 *                                          holding V for the Bone Sword fills the V box red (and a meter)
 * </pre>
 * Hold Left-Alt for the move names.
 */
public final class UndeadHud {
	private static final int MARGIN = 4;
	private static final int WIDTH = VanillaHud.width(6);
	private static final int REGROWTH = 0xFF6FA0;
	private static final int VICTOR = 0xE0303A;

	/** Set only by the client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	/** Boxes left to right: R G Z X C V (slots 1, 2, 4, 3, 6, 5). */
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };
	/**
	 * The little body: x, y, w, h (in HUD px) of the ten segments, seen from the front -- head, body, right arm, left arm, right
	 * leg, left leg (each down to the wrist / ankle), right hand, left hand, right foot, left foot.
	 */
	private static final int[][] FIGURE = { { 4, 0, 8, 8 }, { 4, 8, 8, 12 }, { 0, 8, 4, 8 }, { 12, 8, 4, 8 }, { 4, 20, 4, 8 }, { 8, 20, 4, 8 },
			{ 0, 16, 4, 4 }, { 12, 16, 4, 4 }, { 4, 28, 4, 4 }, { 8, 28, 4, 4 } };
	/** A regrowing segment's fill: bone, then flesh, then skin (NBC's stages). */
	private static final int BONE = 0xFFE4DCC4, FLESH = 0xFFA11E26, SKIN = 0xFFFFD8D0;

	private UndeadHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		UndeadState s = Undead.peek(p);
		if (s == null || !s.hasPower) {
			return;
		}
		var exp = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		float now = mc.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false);
		int x0 = g.guiWidth() - MARGIN - WIDTH;
		int bottom = g.guiHeight() - MARGIN;
		// on a narrow window it would sit on the right end of the hotbar (and the food bar above it): lift it clear
		if (x0 < g.guiWidth() / 2 + 91 + 2) {
			bottom = g.guiHeight() - 41;
		}
		int y0 = bottom - VanillaHud.HEIGHT;
		boolean victor = UndeadConfig.VICTOR_ENABLED && s.victorUntil > now;
		keys(g, p, s, victor, x0, y0, now);

		// the meters, stacked up from the keys
		int by = y0 - 8;
		if (s.animId == UndeadState.ANIM_SWORD_STRUGGLE && !s.boneSword()) {
			// v0.17.3 hold V: how far the arm has given
			float give = Mth.clamp((now - s.animStart) / (UndeadConfig.BONE_SWORD_HOLD - UndeadConfig.BONE_SWORD_TAP), 0f, 1f);
			VanillaHud.meter(g, x0, by, WIDTH, Component.translatable("projecthero.undead.ability." + UndeadBoneSword.BONE_SWORD), give, 0xE0303A,
					Math.round(give * 100f) + "%", VanillaHud.RED);
			by -= 11;
		}
		if (s.boneSword()) {
			// v0.17.3: how long the Bone Sword has left
			float left = Math.max(0f, 1f - (now - s.boneSwordSince()) / UndeadConfig.BONE_SWORD_MAX);
			VanillaHud.meter(g, x0, by, WIDTH, Component.translatable("hud.projecthero.undead.bone_sword"), left, 0xE6DFC8,
					((int) Math.ceil(left * UndeadConfig.BONE_SWORD_MAX / 20f)) + "s", VanillaHud.WHITE);
			by -= 11;
		}
		if (victor) {
			float left = (s.victorUntil - now) / UndeadConfig.VICTOR_TICKS;
			VanillaHud.meter(g, x0, by, WIDTH, Component.translatable("hud.projecthero.undead.victor"), left, VICTOR,
					((int) Math.ceil((s.victorUntil - now) / 20f)) + "s", VanillaHud.RED);
			by -= 11;
		}
		// v0.17.3 (NBC's segments): the whole body's regrowth, frames grown of frames gone
		float grown = 0f, gone = 0f;
		for (int i = 0; i < UndeadState.SEGS; i++) {
			if (s.broken(i)) {
				grown += s.frame(i, now);
				gone += UndeadConfig.FRAMES[i];
			}
		}
		if (gone > 0f) {
			float regrow = grown / gone;
			VanillaHud.meter(g, x0, by, WIDTH, Component.translatable("hud.projecthero.undead.regrowth"), regrow, REGROWTH,
					Math.round(regrow * 100f) + "%", VanillaHud.WHITE);
			by -= 11;
		}

		// the text lines and the little body, right-aligned above the meters
		int fy = by - 36;
		int fx = x0 + WIDTH - 17;
		figure(g, s, now, victor, fx, fy);
		int textW = WIDTH - 20;
		int ty = fy + 2;
		ty += 9; // title and speed centred on the body's height
		Component title =Component.translatable(victor ? "hud.projecthero.undead.victor" : "hud.projecthero.undead.title");
		g.drawString(mc.font, title, x0, ty, victor ? VanillaHud.RED : VanillaHud.WHITE, true);
		if (s.reformUntil > now) {
			g.drawString(mc.font, Component.translatable("hud.projecthero.undead.reforming"), x0, ty - 11, VanillaHud.GOLD, true);
		}
		// v0.17.3: auto-regrowth on or off (Shift+V, Toggle Regen); the Regen Menu open says so in gold
		boolean off = s.flag(UndeadState.FLAG_REGEN_OFF);
		Component mode = Component.translatable(off ? "projecthero.undead.regen.manual" : "projecthero.undead.regen.auto");
		g.drawString(mc.font, mc.font.plainSubstrByWidth(Component.translatable("hud.projecthero.undead.regen", mode).getString(), textW), x0,
				ty + 11, off ? VanillaHud.RED : VanillaHud.GREY, true);
		if (s.flag(UndeadState.FLAG_REGEN_MENU) && s.reformUntil <= now) {
			g.drawString(mc.font, mc.font.plainSubstrByWidth(Component.translatable("hud.projecthero.undead.menu").getString(), textW), x0, ty - 11,
					VanillaHud.GOLD, true);
		}

		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : SLOTS) {
				String[] ids = UndeadAbilityManager.idsOf(p, slot);
				if (ids.length > 0) {
					// v0.17.3: V's third move is held, not shifted: "Hold: Bone Sword" (when the sword is not already out); in the
					// Regen Menu "Hold: Regenerate Whole Body"
					boolean menu = s.flag(UndeadState.FLAG_REGEN_MENU);
					Component extra = slot == AbilitySlot.SLOT_5 && (menu || !s.boneSword())
							? Component.translatable("hud.projecthero.undead.hold", Component.translatable("projecthero.undead.ability."
									+ (menu ? com.projecthero.mod.undead.UndeadRegenMenu.WHOLE : UndeadBoneSword.BONE_SWORD)))
							: null;
					rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.undead.ability." + ids[0]),
							ids.length > 1 ? Component.translatable("projecthero.undead.ability." + ids[1]) : null, extra));
				}
			}
			MoveListOverlay.draw(g, x0 + WIDTH, fy - 4, rows);
		}
	}

	/**
	 * The little body with a 1-px dark frame behind it, like an item in a slot: the ten segments, each whole (shaded by its
	 * part's HP), gone (dark) or regrowing -- filling from the joint outward in the colour of its stage (bone, flesh, skin).
	 */
	private static void figure(GuiGraphics g, UndeadState s, float now, boolean victor, int fx, int fy) {
		for (int i = 0; i < UndeadState.SEGS; i++) {
			int[] f = FIGURE[i];
			int px = fx + f[0], py = fy + f[1];
			g.fill(px - 1, py - 1, px + f[2] + 1, py + f[3] + 1, 0xFF1A1A1A);
		}
		for (int i = 0; i < UndeadState.SEGS; i++) {
			int[] f = FIGURE[i];
			int px = fx + f[0], py = fy + f[1];
			if (!s.broken(i)) {
				int hpPart = UndeadState.limbOf(i);
				g.fill(px, py, px + f[2], py + f[3], partColour(s.hp(hpPart) / UndeadConfig.PART_HP[hpPart], victor));
				g.fill(px, py, px + f[2], py + 1, 0x40FFFFFF); // a lit top edge, like the inventory's slot shading
			} else {
				g.fill(px, py, px + f[2], py + f[3], 0xFF3A0A0A);
				float r = s.regrowth(i, now);
				if (r > 0f) {
					int h = Math.max(1, Math.round(f[3] * r));
					int stage = s.stage(i, now);
					int c = stage == UndeadState.STAGE_BONE ? BONE : stage == UndeadState.STAGE_FLESH ? FLESH : SKIN;
					// from the joint outward: legs and arms fill downward, the head upward
					if (i == UndeadState.HEAD) {
						g.fill(px, py + f[3] - h, px + f[2], py + f[3], c);
					} else {
						g.fill(px, py, px + f[2], py + h, c);
					}
				}
			}
		}
	}

	private static void keys(GuiGraphics g, LocalPlayer p, UndeadState s, boolean victor, int x0, int y0, float now) {
		boolean shift = forceShift || p.isShiftKeyDown();
		VanillaHud.strip(g, x0, y0, SLOTS.length);
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			String[] ids = UndeadAbilityManager.idsOf(p, slot);
			if (ids.length == 0) {
				continue;
			}
			String shown = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			int cd = Undead.cooldownRemaining(p, shown);
			float frac = Math.min(1f, cd / (float) Math.max(1, UndeadAbilityManager.maxCooldown(shown)));
			float otherFrac = other == null ? 0f
					: Math.min(1f, Undead.cooldownRemaining(p, other) / (float) Math.max(1, UndeadAbilityManager.maxCooldown(other)));
			boolean active = (slot == AbilitySlot.SLOT_3 && s.flag(UndeadState.FLAG_PROPELLING))
					|| (slot == AbilitySlot.SLOT_1 && s.flag(UndeadState.FLAG_PUNCH))
					|| (slot == AbilitySlot.SLOT_6 && (s.flag(UndeadState.FLAG_DEAD_LINE) || s.flag(UndeadState.FLAG_DEAD_ROAD)))
					|| (slot == AbilitySlot.SLOT_5 && (victor || s.boneSword()));
			VanillaHud.slot(g, x0, y0, i, keyLabel(slot), true, shift, frac, cd > 0 ? (cd + 19) / 20 : 0, otherFrac, active);
			if (slot == AbilitySlot.SLOT_5 && s.animId == UndeadState.ANIM_SWORD_STRUGGLE && !s.boneSword()) {
				// v0.17.3 hold V: the charge -- a red fill rising up the box as the arm gives
				float give = Mth.clamp((now - s.animStart) / (UndeadConfig.BONE_SWORD_HOLD - UndeadConfig.BONE_SWORD_TAP), 0f, 1f);
				int ix = x0 + 3 + i * VanillaHud.SLOT, iy = y0 + 3;
				int top = iy + 16 - Math.round(16f * give);
				g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), ix, top, ix + 16, iy + 16, 0x90C01020);
				g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(), ix, top, ix + 16, Math.min(iy + 16, top + 1), 0xFFFF4040);
			}
		}
	}

	/** Whole: pale; hurt: toward red. */
	private static int partColour(float hp, boolean victor) {
		hp = Math.max(0f, Math.min(1f, hp));
		int r = 0xE6, gb = (int) (0x50 + (0xD8 - 0x50) * hp);
		if (victor) {
			gb = Math.min(gb, 0x90);
		}
		return 0xFF000000 | (r << 16) | (gb << 8) | gb;
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}
}
