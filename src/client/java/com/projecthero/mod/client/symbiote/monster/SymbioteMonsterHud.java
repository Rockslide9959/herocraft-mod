package com.projecthero.mod.client.symbiote.monster;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.symbiote.SymbioteVitals;
import com.projecthero.mod.symbiote.SymbioteVitalsManager;
import com.projecthero.mod.symbiote.monster.SymbioteMonster;
import com.projecthero.mod.symbiote.monster.SymbioteMonsterMoves;
import com.projecthero.mod.symbiote.monster.SymbioteMonsterState;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.5 Symbiote Monster form: the Symbiote HUD column while transformed (called first from {@code SymbioteHud.renderNormalHost},
 * which draws nothing else when this returns true). The same Minecraft-style column: the six keys are the monster's moves (their
 * cooldowns from the synced {@link SymbioteMonsterState#ready}, a key lit while its move plays, Z yellow with Shift = end the form),
 * the form's 40 s timer on a Slab bar (the transformation / melt progress while those run), the Biomass Hairline, the title, and the
 * Left-Alt move list of the monster's moves.
 */
public final class SymbioteMonsterHud {
	private static final int ACCENT = 0xFFD0384A;
	private static final int LABEL = 0xFFB090F0;
	/** Slot ordinal (R G X Z V C) -> the monster move's id and its cooldown (ticks). */
	private static final String[] MOVES = { "monster_swipe", "monster_pound", "monster_leap", "monster_roar", "monster_pull", "monster_devour" };
	private static final int[] COOLDOWNS = { SymbioteMonsterMoves.SWIPE_COOLDOWN, SymbioteMonsterMoves.POUND_COOLDOWN,
			SymbioteMonsterMoves.LEAP_COOLDOWN, SymbioteMonsterMoves.ROAR_COOLDOWN, SymbioteMonsterMoves.PULL_COOLDOWN,
			SymbioteMonsterMoves.DEVOUR_COOLDOWN };
	private static final int[] MOVE_OF_SLOT = { SymbioteMonsterState.MOVE_SWIPE, SymbioteMonsterState.MOVE_POUND, SymbioteMonsterState.MOVE_LEAP,
			SymbioteMonsterState.MOVE_ROAR, SymbioteMonsterState.MOVE_PULL, SymbioteMonsterState.MOVE_DEVOUR };

	private SymbioteMonsterHud() {
	}

	/** @return true when the monster column was drawn (the normal column must not be). */
	public static boolean render(GuiGraphics g, Player player, SymbioteVitals v, long now, boolean shift, boolean owned) {
		SymbioteMonsterState s = SymbioteMonster.state(player);
		if (!s.inForm()) {
			return false;
		}
		boolean monster = s.phase() == SymbioteMonsterState.MONSTER;
		long age = now - s.phaseStart();
		VanillaHud.Stack st = (owned ? VanillaHud.Stack.bottomRightOwned(g, 6) : VanillaHud.Stack.bottomRight(g, 6)).keys(6);
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			int o = slot.ordinal();
			int cd = (int) Math.max(0L, s.readyAt(o) - now);
			float frac = Math.min(1f, cd / (float) Math.max(1, COOLDOWNS[o]));
			boolean playing = monster && s.move() == MOVE_OF_SLOT[o] && now - s.moveStart() < 20
					|| monster && o == AbilitySlot.SLOT_3.ordinal() && s.move() == SymbioteMonsterState.MOVE_LEAP && !player.onGround();
			st.slot(i, VanillaHud.keyLabel(slot), monster, shift && o == AbilitySlot.SLOT_4.ordinal(), frac, VanillaHud.seconds(cd), 0f, playing);
		}
		// the form's clock
		Component label;
		float fill;
		String value;
		if (s.phase() == SymbioteMonsterState.COCOON) {
			label = Component.translatable("hud.projecthero.symbiote.monster.transforming");
			fill = Math.min(1f, age / (float) SymbioteMonster.COCOON_TICKS);
			value = Math.round(fill * 100f) + "%";
		} else if (monster) {
			label = Component.translatable("hud.projecthero.symbiote.monster.timer");
			long left = Math.max(0L, SymbioteMonster.FORM_TICKS - age);
			fill = left / (float) SymbioteMonster.FORM_TICKS;
			value = (left + 19) / 20 + "s";
		} else {
			label = Component.translatable("hud.projecthero.symbiote.monster.melting");
			fill = 1f - Math.min(1f, age / (float) SymbioteMonster.MELT_TICKS);
			value = "";
		}
		int colour = monster && fill < 0.2f && (now / 5) % 2 == 0 ? 0xFFFFFFFF : ACCENT;
		st.meter(label, fill, colour, value, VanillaHud.WHITE, MutationMeters.Style.SLAB);
		float ratio = Math.max(0f, Math.min(1f, v.hp / SymbioteVitalsManager.MAX_HP));
		st.meter(Component.translatable("hud.projecthero.symbiote.hp"), ratio, 0xFF8A5FE0, Math.round(ratio * 100f) + "%", LABEL,
				MutationMeters.Style.HAIRLINE);
		st.title(Component.translatable("hud.projecthero.symbiote.monster.title"), ACCENT);
		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slot : VanillaHud.ORDER) {
				int o = slot.ordinal();
				Component shiftMove = o == AbilitySlot.SLOT_4.ordinal() ? Component.translatable("projecthero.symbiote.ability.monster_end") : null;
				rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable("projecthero.symbiote.ability." + MOVES[o]),
						shiftMove));
			}
			st.moveList(rows);
		}
		return true;
	}
}
