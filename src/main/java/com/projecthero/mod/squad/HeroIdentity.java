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
		if (com.projecthero.mod.punisher.PunisherAbilityManager.hasContext(player)
				&& com.projecthero.mod.symbiote.SymbioteAgentVenomAbilities.agentVenom(player)) {
			return "projecthero.squad.identity.agent_venom";
		}
		if (com.projecthero.mod.punisher.PunisherAbilityManager.hasContext(player)) {
			return "projecthero.squad.identity.punisher";
		}
		if (com.projecthero.mod.greenlantern.GreenLantern.hasPower(player)) {
			return "projecthero.squad.identity.green_lantern";
		}
		if (com.projecthero.mod.titanshifter.TitanShifter.isShifter(player)) {
			return "projecthero.squad.identity.titan_shifter";
		}
		if (com.projecthero.mod.allmight.AllMightAbilityManager.hasContext(player)) {
			return "projecthero.squad.identity.all_might";
		}
		if (com.projecthero.mod.moonknight.MoonKnight.isTransformed(player)) {
			return "projecthero.squad.identity.moon_knight";
		}
		if (com.projecthero.mod.hulk.Hulk.hasPower(player)) {
			return com.projecthero.mod.hulk.Hulk.isHulk(player) ? "projecthero.squad.identity.hulk" : "projecthero.squad.identity.banner";
		}
		if (com.projecthero.mod.supersoldier.SuperSoldierAbilityManager.hasContext(player)) {
			return "projecthero.squad.identity.super_soldier";
		}
		if (com.projecthero.mod.kryptonian.Kryptonian.hasPower(player)) {
			return "projecthero.squad.identity.kryptonian";
		}
		if (com.projecthero.mod.nova.Nova.hasPower(player)) {
			return "projecthero.squad.identity.nova";
		}
		if (com.projecthero.mod.ghostrider.GhostRider.hasPower(player)) {
			return "projecthero.squad.identity.ghost_rider";
		}
		if (com.projecthero.mod.silversurfer.SilverSurfer.hasPower(player)) {
			return "projecthero.squad.identity.silver_surfer";
		}
		if (com.projecthero.mod.speedforce.SpeedForceSpeedster.hasPower(player)) {
			return "projecthero.squad.identity.speed_force_speedster";
		}
		if (com.projecthero.mod.cyborg.Cyborg.hasPower(player)) {
			return "projecthero.squad.identity.cyborg";
		}
		if (com.projecthero.mod.bluebeetle.BlueBeetle.hasPower(player)) {
			return "projecthero.squad.identity.blue_beetle";
		}
		if (com.projecthero.mod.falcon.Falcon.hasPower(player)) {
			return "projecthero.squad.identity.falcon"; // v0.17.1
		}
		// v0.16.6: Ladybug / Cat Noir / Rena Rouge (by the Kwami held)
		com.projecthero.mod.miraculous.Kwami kwami = com.projecthero.mod.miraculous.Miraculous.kwami(player);
		if (kwami != null) {
			return "projecthero.squad.identity." + kwami.heroKey;
		}
		// v0.16.7: a Quirk holder (by the hero name: Endeavor, Uravity...)
		com.projecthero.mod.quirk.Quirk quirk = com.projecthero.mod.quirk.Quirks.quirk(player);
		if (quirk != null) {
			return "projecthero.quirk." + quirk.id + ".hero";
		}
		if (com.projecthero.mod.wolverine.WolverineAbilityManager.hasContext(player)) {
			return "projecthero.squad.identity.wolverine";
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
