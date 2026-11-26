package com.projecthero.mod.client.blackpanther;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.blackpanther.BlackPanther;
import com.projecthero.mod.blackpanther.BlackPantherAbilityManager;
import com.projecthero.mod.blackpanther.BlackPantherConfig;
import com.projecthero.mod.blackpanther.data.BlackPantherState;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.visual.MutationMeters;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.17.3: the Black Panther HUD on the Minecraft-style look of the newest powers ({@link VanillaHud.Stack}, bottom-right):
 * <pre>
 *   Black Panther                       v0.17.7 (user): always this title, suit or no suit, and no status words
 *   Absorb  [Hairline ====---]  60%    what the suit can still absorb, only while absorbing
 *   Kinetic [Meter ======-]  84        the Kinetic charge (purple), v0.17.7 (user): in the Meter style
 *   +----+----+----+----+----+----+    R G Z X C V: shown move's cooldown (sweep + seconds), the Shift move's as the durability
 *   | R  | G  | Z  | X  | C  | V  |    bar; V lit while stalking / Senses run, C while absorbing; yellow while Shift shows the
 *   +----+----+----+----+----+----+    Shift moves
 * </pre>
 * Kept as it was: the Ancestral Plane (a purple sky over the whole screen for the ten seconds after the soup).
 */
public final class BlackPantherHud {
	private static final int NAME = 0xFFE2E2E2;
	private static final int KINETIC = 0xFF9A4CFF;
	private static final int KINETIC_TEXT = 0xFFC08CFF;
	private static final int ABSORB = 0xFFD9B8FF;

	private BlackPantherHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		// v0.17.5 (user): a Secondary power with the keys (Power Armour, Falcon gear, the Symbiote suit over a Primary) owns the HUD
		if (com.projecthero.mod.client.gui.VanillaHud.secondaryOwnsHud()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			return;
		}
		BlackPantherState s = p.getAttachedOrElse(ModAttachments.BLACK_PANTHER_STATE, null);
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
		var exp = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		// v0.17.4: both movesets have a HUD -- the keys show whichever kit is live (the suit decides)
		boolean suited = s.suited;
		boolean shift = p.isShiftKeyDown();
		VanillaHud.Stack st = VanillaHud.Stack.bottomRight(g, VanillaHud.ORDER.length).keys(VanillaHud.ORDER.length);
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			String[] ids = BlackPantherAbilityManager.idsOf(slot, suited);
			if (ids.length == 0) {
				continue;
			}
			String shown = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			int cd = BlackPanther.cooldownRemaining(p, shown);
			float frac = cd / (float) Math.max(1, BlackPantherAbilityManager.maxCooldown(shown));
			float otherFrac = other == null ? 0f
					: BlackPanther.cooldownRemaining(p, other) / (float) Math.max(1, BlackPantherAbilityManager.maxCooldown(other));
			boolean active = (slot == AbilitySlot.SLOT_5 && (s.stalking || s.senses)) || (slot == AbilitySlot.SLOT_6 && s.absorbing);
			st.slot(i, VanillaHud.keyLabel(slot), true, shift, Math.min(1f, frac), VanillaHud.seconds(cd), Math.min(1f, otherFrac), active);
		}

		if (suited) {
			st.meter(Component.translatable("hud.projecthero.black_panther.kinetic"), s.kinetic / BlackPantherConfig.KINETIC_MAX, KINETIC,
					String.valueOf(Math.round(s.kinetic)), KINETIC_TEXT, MutationMeters.Style.METER);
			if (s.absorbing) {
				float left = Mth.clamp(1f - s.absorbed / BlackPantherConfig.ABSORB_MAX, 0f, 1f);
				st.meter(Component.translatable("hud.projecthero.black_panther.absorb"), left, ABSORB, Math.round(left * 100) + "%", ABSORB,
						MutationMeters.Style.HAIRLINE);
			}
		}
		// v0.17.7 (user): "the only text on the hud should be the name of the ability" -- no T'Challa title, no status words
		st.title(Component.translatable("hud.projecthero.black_panther.title"), NAME);

		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : VanillaHud.ORDER) {
				String[] ids = BlackPantherAbilityManager.idsOf(slot, suited);
				if (ids.length > 0) {
					rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.black_panther.ability." + ids[0]),
							ids.length > 1 ? Component.translatable("projecthero.black_panther.ability." + ids[1]) : null));
				}
			}
			// N has no HUD box (the project rule): the suit's mask (Shift+N) is listed here instead; the claws are always out
			if (suited) {
				rows.add(MoveListOverlay.Row.of("Shift+" + MoveListOverlay.key(AbilitySlot.SLOT_8), Component.translatable("projecthero.black_panther.ability.mask"),
						null));
			}
			if (suited) {
				// v0.17.7: Shift+R becomes Kinetic Overload on a full bar
				rows.add(MoveListOverlay.Row.of("Shift+" + MoveListOverlay.key(AbilitySlot.SLOT_1) + " (full)",
						Component.translatable("projecthero.black_panther.ability.kinetic_overload"), null));
			}
			st.moveList(rows);
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
}
