package com.projecthero.mod.client.raven;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.raven.Raven;
import com.projecthero.mod.raven.RavenAbilityManager;
import com.projecthero.mod.raven.RavenConfig;
import com.projecthero.mod.raven.RavenMoves;
import com.projecthero.mod.raven.RavenSpell;
import com.projecthero.mod.raven.data.RavenState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Raven HUD (revamp). v0.17.4 (user: "make the dark energy bar the Minecraft style bar"): the whole column is now the mod's
 * Minecraft-style HUD ({@link VanillaHud.Stack}, the same one the other powers use), so Dark Energy is a real boss bar like the game's
 * own, not the old Slab:
 * <pre>
 *   Ascension 0:12  Soul Shield 24  Debris 6    status tags (wrap onto more lines when they don't fit)
 *   Raven                                        the title
 *   Dark Energy [purple boss bar ======---]  74  the Dark Energy bar (red when low)
 *   Calm  [hairline boss bar ==========---]  92  Composure as a hairline cut of the boss bar, named by its tier (Calm / Unsettled /
 *   +----+----+----+----+----+----+              RAGE / Wrath 0:12), white calm, blue, yellow Unsettled, flashing red Rage
 *   | R  | G  | Z  | X  | C  |icon|              R G Z X C V: cooldowns; greyed when she can't pay / Shift+Z without Rage; yellow
 *   +----+----+----+----+----+----+              while Shift shows the Shift moves. The V box shows the picked spell's icon.
 * </pre>
 * Bottom-right like every power's column, lifted clear of the hotbar and food bar on a narrow window. Hold Left-Alt for the move list.
 * Every text is cut or wrapped to the column's width (no sprawl).
 */
public final class RavenHud {
	private static final AbilitySlot[] SLOTS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_3,
			AbilitySlot.SLOT_6, AbilitySlot.SLOT_5 };
	private static final int TITLE = 0xFFB98CF0;
	private static final int ENERGY = 0xFF7A3CC8;
	private static final int TAG = 0xFFC8B0F0;
	private static final int ASCEND = 0xFFE6D2FF;

	/** Set only by the client screenshot harness: the HUD draws as if the player were sneaking. */
	public static volatile boolean forceShift;

	private RavenHud() {
	}

	static String clock(int ticks) {
		int s = (ticks + 19) / 20;
		return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
	}

	/** The Composure bar's colour (the boss bar takes the nearest of its own colours). */
	public static int composureColour(float composure, long now) {
		if (composure < RavenConfig.RAGE_BELOW) {
			return (now / 4) % 2 == 0 ? 0xFFFF2030 : 0xFFB01020;
		}
		if (composure < RavenConfig.UNSETTLED_BELOW) {
			return 0xFFE0C03A;
		}
		if (composure >= 90f) {
			return 0xFFEDEBFF;
		}
		return 0xFF5A7CE0;
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		RavenState s = Raven.peek(p);
		if (s == null || !s.hasPower) {
			return;
		}
		var exp = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		if (exp != null && !exp.activePower.isEmpty()) {
			return;
		}
		Font font = mc.font;
		long now = mc.level.getGameTime();
		VanillaHud.Stack hud = VanillaHud.Stack.bottomRight(g, SLOTS.length);
		hud.keys(SLOTS.length);
		keys(hud, p, s);

		// Composure: a hairline of the boss bar, named by its tier
		String mood;
		int moodColour;
		if (Raven.inWrath(p)) {
			mood = Component.translatable("hud.projecthero.raven.wrath").getString() + " " + clock(Raven.wrathTicksLeft(p));
			moodColour = 0xFFFF3040;
		} else {
			switch (Raven.tier(p)) {
				case RAGE -> {
					mood = Component.translatable("hud.projecthero.raven.rage").getString();
					moodColour = composureColour(s.composure, now);
				}
				case UNSETTLED -> {
					mood = Component.translatable("hud.projecthero.raven.unsettled").getString();
					moodColour = VanillaHud.YELLOW;
				}
				default -> {
					mood = Component.translatable("hud.projecthero.raven.calm").getString();
					moodColour = 0xFF9AA8D8;
				}
			}
		}
		hud.meter(Component.literal(mood), s.composure / RavenConfig.COMPOSURE_MAX, composureColour(s.composure, now),
				String.valueOf(Math.round(s.composure)), moodColour, MutationMeters.Style.HAIRLINE);
		// Dark Energy: the boss bar
		float energy = s.energy / RavenConfig.ENERGY_MAX;
		hud.meter(Component.translatable("hud.projecthero.raven.energy"), energy, energy < 0.15f ? 0xFFB03050 : ENERGY,
				String.valueOf(Math.round(s.energy)), energy < 0.15f ? VanillaHud.RED : VanillaHud.WHITE);
		hud.title(Component.translatable("hud.projecthero.raven.title"), TITLE);

		// status tags, wrapped to the column's width
		List<VanillaHud.Tag> tags = new ArrayList<>();
		if (Raven.ascended(p)) {
			int pulse = (now / 5) % 2 == 0 ? ASCEND : 0xFFC9A6FF;
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.raven.ascension").getString() + " " + clock(Raven.ascendTicksLeft(p)), pulse));
		}
		if (s.meditating) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.raven.meditating").getString(), 0xFFEDEBFF));
		}
		if (s.flying) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.raven.flying").getString(), TAG));
		}
		if (Raven.guarding(p)) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.raven.soul_shield").getString() + " " + Math.round(s.guardHp), TAG));
		}
		if (Raven.veiled(p)) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.raven.veiled").getString() + " " + clock((int) Math.max(0, s.veilUntil - now)), TAG));
		}
		if (RavenMoves.soulOut(p)) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.raven.soul_out").getString(), TAG));
		}
		if (s.debris > 0) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.raven.debris").getString() + " " + s.debris, TAG));
		}
		if (s.tkTarget >= 0) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.raven.holding").getString(), TAG));
		}
		hud.tags(tags);

		List<MoveListOverlay.Row> rows = new ArrayList<>();
		if (MoveListOverlay.held()) {
			for (AbilitySlot slot : SLOTS) {
				String[] ids = RavenAbilityManager.idsOf(slot);
				if (ids.length == 0) {
					continue;
				}
				rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), name(p, ids[0]), ids.length > 1 ? name(p, ids[1]) : null));
			}
		}
		hud.moveList(rows);

		// the meditation's soft chant across the middle of the screen
		if (s.meditating) {
			int line = (int) ((now / 30) % 3);
			String chant = Component.translatable("message.projecthero.raven.chant." + line).getString();
			float k = ((now % 30) + delta.getGameTimeDeltaPartialTick(false)) / 30f;
			int alpha = Mth.clamp((int) (200 * Mth.sin(k * Mth.PI)), 8, 200);
			chant = font.plainSubstrByWidth(chant, Math.min(220, g.guiWidth() - 20));
			g.drawString(font, chant, (g.guiWidth() - font.width(chant)) / 2, g.guiHeight() / 2 - 40, (alpha << 24) | 0xD8C8FF, true);
		}
	}

	/** A slot id's name for the move list (V: "Cast: <picked spell>"). */
	private static Component name(LocalPlayer p, String id) {
		if (RavenAbilityManager.CAST_SPELL.equals(id)) {
			return Component.translatable("projecthero.raven.ability.cast_spell.named", Component.translatable(Raven.selectedSpell(p).langKey()));
		}
		return Component.translatable("projecthero.raven.ability." + id);
	}

	private static void keys(VanillaHud.Stack hud, LocalPlayer p, RavenState s) {
		GuiGraphics g = hud.g;
		int x0 = hud.stripX(), y0 = hud.stripY();
		boolean shift = forceShift || p.isShiftKeyDown();
		for (int i = 0; i < SLOTS.length; i++) {
			AbilitySlot slot = SLOTS[i];
			String[] ids = RavenAbilityManager.idsOf(slot);
			if (ids.length == 0) {
				continue;
			}
			String shown = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			int cd = RavenAbilityManager.cooldownRemaining(p, shown);
			float frac = Math.min(1f, cd / (float) Math.max(1, RavenAbilityManager.maxCooldown(p, shown)));
			float otherFrac = other == null ? 0f
					: Math.min(1f, RavenAbilityManager.cooldownRemaining(p, other) / (float) Math.max(1, RavenAbilityManager.maxCooldown(p, other)));
			boolean usable = !s.meditating && RavenAbilityManager.allowed(p, shown)
					&& (p.getAbilities().instabuild || s.energy + 1.0e-3f >= RavenAbilityManager.costNow(p, shown));
			if (RavenAbilityManager.MEDITATE.equals(shown)) {
				usable = !Raven.inWrath(p);
			}
			boolean active = (RavenAbilityManager.SOUL_SHIELD.equals(shown) && Raven.guarding(p))
					|| (RavenAbilityManager.MEDITATE.equals(shown) && s.meditating)
					|| (RavenAbilityManager.TELEKINESIS.equals(shown) && s.tkTarget >= 0)
					|| (RavenAbilityManager.SOUL_SELF.equals(shown) && RavenMoves.soulOut(p))
					|| (RavenAbilityManager.ASCENSION.equals(shown) && Raven.ascended(p))
					|| (RavenAbilityManager.CAST_SPELL.equals(shown) && s.debris > 0 && Raven.selectedSpell(p) == RavenSpell.DEBRIS_STORM)
					|| (RavenAbilityManager.TRIGONS_WRATH.equals(shown) && Raven.inWrath(p));
			boolean spell = RavenAbilityManager.CAST_SPELL.equals(shown);
			if (spell) {
				// the picked spell's icon under the cooldown sweep, the key letter small in the corner
				int ix = x0 + 3 + i * VanillaHud.SLOT, iy = y0 + 3;
				RenderSystem.enableBlend();
				float tint = usable ? 1f : 0.45f;
				g.setColor(tint, tint, tint, 1f);
				g.blit(RavenSpellWheelScreen.ICONS, ix, iy, Raven.selectedSpell(p).ordinal() * 16, 0, 16, 16, 16 * RavenSpell.count(), 16);
				g.setColor(1f, 1f, 1f, 1f);
				hud.slot(i, "", usable, shift, frac, cd > 0 ? (cd + 19) / 20 : 0, otherFrac, active);
				String k = keyLabel(slot);
				g.pose().pushPose();
				g.pose().translate(ix - 1, iy - 1, 210f);
				g.pose().scale(0.6f, 0.6f, 1f);
				g.drawString(Minecraft.getInstance().font, k, 0, 0, usable ? 0xFFFFFFFF : 0xFF777777, true);
				g.pose().popPose();
			} else {
				hud.slot(i, keyLabel(slot), usable, shift, frac, cd > 0 ? (cd + 19) / 20 : 0, otherFrac, active);
			}
		}
	}

	private static String keyLabel(AbilitySlot slot) {
		String text = ModKeyBindings.ABILITY_SLOTS[slot.ordinal()].getTranslatedKeyMessage().getString();
		return text.length() > 2 ? text.substring(0, 2) : text;
	}
}
