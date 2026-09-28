package com.projecthero.mod.symbiote;

import com.projecthero.mod.spider.SpiderMan;

import net.minecraft.world.entity.player.Player;

/**
 * Which of the two Symbiote host variants a bonded player currently is. Deliberately derived live
 * from {@link SpiderMan#hasPower}, never stored on {@link SymbioteState} -- Spider-Man possession
 * is already the one authoritative signal ({@link SymbioteCompatibility#isSpiderMan} reads the
 * same thing), so there is nothing to keep in sync and nothing that can go stale across a bond that
 * outlives, in the one edge case that matters, the Spider-Man power itself (see {@link #NORMAL}).
 */
public enum SymbioteHostType {
	/**
	 * A bonded player who does not currently hold Spider-Man. Gets the plain "living black armour"
	 * ({@code SymbioteHostArmorItem}) and the six tendril/mobility/defence abilities
	 * ({@link SymbioteAbilityManager}). This is also what a bonded Spider-Man demotes to if the
	 * Spider-Man power is ever removed from them while still bonded -- the Symbiote bond itself is
	 * never lost just because its host stopped being Spider-Man, only the variant changes.
	 */
	NORMAL,
	/**
	 * A bonded player who currently holds Spider-Man -- Black Suit Spider-Man. Gets the GeckoLib
	 * black-suit armour ({@code SymbioteArmorItem}) and enhancements layered onto the existing
	 * {@code SpiderManAbilityManager}/{@code SpiderAbilities}, never new keybinds.
	 */
	SPIDER_MAN,
	/**
	 * v0.13.11: a bonded player who holds the Punisher (and not Spider-Man, who wins if a player holds
	 * both) -- Agent Venom. Gets the Agent Venom suit ({@code AgentVenomArmorItem}) and keeps the whole
	 * Punisher kit, with sneak-modified Symbiote extras layered onto it
	 * ({@link SymbioteAgentVenomAbilities}) -- the same "enhance, never new keybinds" model as the Black
	 * Suit. Like the Black Suit it has none of the Normal host's Biomass-bar passives.
	 */
	AGENT_VENOM;

	public static SymbioteHostType of(Player player) {
		if (SpiderMan.hasPower(player)) {
			return SPIDER_MAN;
		}
		return com.projecthero.mod.punisher.Punisher.hasPower(player) ? AGENT_VENOM : NORMAL;
	}
}
