package com.projecthero.mod.squad;

import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * "What is this player, right now?" -- a one-line description of whichever hero identity or mutation
 * currently owns a player's six ability slots, for the squad screen's roster.
 *
 * <p>The order matches {@link com.projecthero.mod.hero.AbilityRouter}'s own context priority, so what
 * this reports is genuinely what pressing R would do, not merely what the player owns. The result is a
 * translation key so the screen reads in the player's own language.
 */
public final class HeroIdentity {
	private HeroIdentity() {
	}

	/** A translation key naming this player's active identity, or {@code ""} for an ordinary human. */
	public static String describe(ServerPlayer player) {
		if (com.projecthero.mod.hero.AbilityRouter.hasThorContext(player)) {
			return "projecthero.squad.identity.thor";
		}
		if (com.projecthero.mod.ironman.ability.IronManAbilityManager.hasContext(player)) {
			return "projecthero.squad.identity.iron_man";
		}
		if (com.projecthero.mod.spider.SpiderManAbilityManager.hasContext(player)) {
			return "projecthero.squad.identity.spider_man";
		}
		if (com.projecthero.mod.maxsteel.MaxSteelAbilityManager.hasContext(player)) {
			return "projecthero.squad.identity.max_steel";
		}
		if (com.projecthero.mod.punisher.PunisherAbilityManager.hasContext(player)) {
			return "projecthero.squad.identity.punisher";
		}
		if (com.projecthero.mod.symbiote.SymbioteAbilityManager.hasContext(player)) {
			return "projecthero.squad.identity.symbiote";
		}
		Power active = ExperimentalPowers.getActive(player);
		if (active != null && ExperimentalPowers.owns(player, active)) {
			return active.nameKey();
		}
		return "";
	}

	/** Render a key from {@link #describe} for display, falling back to a plain "no powers" line. */
	public static Component render(String key) {
		return key == null || key.isEmpty()
				? Component.translatable("projecthero.squad.identity.none")
				: Component.translatable(key);
	}
}
