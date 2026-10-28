package com.projecthero.mod.diagnostics;

import com.projecthero.mod.hero.power.ConjuredStructures;
import com.projecthero.mod.hero.power.TempBlocks;
import com.projecthero.mod.hero.power.p07.ElectrokinesisHandlers;
import com.projecthero.mod.hero.power.p19.ShadowManipulationHandlers;
import com.projecthero.mod.hero.power.p26.MagneticHandlers;
import com.projecthero.mod.ironman.suit.IronManSuitCall;
import com.projecthero.mod.worldgen.CraterAmbience;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.MinecraftServer;

/**
 * One place that drops every piece of <em>static</em> server-side scratch state when a server stops,
 * plus a slow sweep that prunes the couple of caches which would otherwise only be pruned while some
 * player happens to have the owning power selected.
 *
 * <h2>Why this exists (the "long session gets laggy" report)</h2>
 * Several systems keep their live bookkeeping in {@code static} maps/queues rather than in
 * {@link net.minecraft.world.level.saveddata.SavedData} -- correctly, since none of it should be
 * persisted. But a static field outlives the server that filled it, and a singleplayer client keeps
 * one JVM for every world it opens in a session. Those entries hold hard references to
 * {@link net.minecraft.server.level.ServerLevel}, {@link net.minecraft.world.entity.Entity} and
 * {@link net.minecraft.world.level.block.state.BlockState} objects, so quitting to the title screen
 * and opening another world used to pin the <em>entire previous world</em> in memory: every level, its
 * chunk map and its entities stayed reachable and uncollectable. Do that a few times in one sitting
 * and the heap fills with dead worlds, the GC starts working much harder for the same allocation
 * rate, and the game gets progressively choppier the longer the session runs -- which is exactly the
 * shape of "it lags after playing for a long time".
 *
 * <p>Two of those maps ({@code ElectrokinesisHandlers.SHOCKED}, {@code ShadowManipulationHandlers.BOUND})
 * had a second, smaller version of the same problem while a single world was running: entries are
 * added whenever those abilities hit something, but the code that expires them only runs inside the
 * owning power's passive tick. Switch away from the power (or log out) with entries still in the map
 * and nothing ever removes them again. {@link #sweep} gives them a cheap, unconditional owner, and
 * Spider-Man's cocoon markers ({@code SpiderWebs}) are swept from there for the same reason.
 *
 * <p>{@link TempBlocks} deserves its own note: its queue expires entries by comparing against
 * {@code server.overworld().getGameTime()}. A new world starts near game time 0, so entries left over
 * from a previous world have an {@code expiresAt} far in the future and would never be reached --
 * they would sit at the head of the deque holding a dead {@code ServerLevel} until the 4096-entry cap
 * eventually pushed them out.
 */
public final class ServerStateReset {
	/** Ticks between sweeps. Deliberately slow -- everything it touches is tiny and expiry-stamped. */
	private static final int SWEEP_INTERVAL_TICKS = 200; // 10s

	private ServerStateReset() {
	}

	public static void initialize() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearAll());
		ServerTickEvents.END_SERVER_TICK.register(ServerStateReset::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % SWEEP_INTERVAL_TICKS != 0) {
			return;
		}
		sweep(server);
	}

	/** Expire anything time-stamped that nothing else is currently responsible for expiring. */
	private static void sweep(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		ElectrokinesisHandlers.pruneExpired(now);
		ShadowManipulationHandlers.pruneExpired(now);
		com.projecthero.mod.spider.SpiderWebs.pruneExpired(now);
		com.projecthero.mod.combat.SonicVulnerability.pruneExpired(now);
	}

	/**
	 * Drop every static scratch collection. Safe to call at any point after a server has stopped --
	 * none of it is persisted state, and everything that owns it re-populates on demand.
	 */
	public static void clearAll() {
		com.projecthero.mod.omnitrix.OmnitrixMod.clearSessionState(); // v0.17.1
		com.projecthero.mod.darkseid.entity.BoomTubePortalEntity.clearSessionState(); // v0.17.1
		// Moon Knight Phase 3-4 (R / X / G / Z static maps)
		com.projecthero.mod.moonknight.ability.MoonKnightCombat.clearSessionState();
		// v0.14.27 Iron Man landing slam (motion history + cooldowns)
		com.projecthero.mod.ironman.IronManLandingSlam.clearSessionState();
		com.projecthero.mod.ironman.ability.IronManSpaceBoost.clearSessionState(); // v0.16.7 (user): the Mk VIII Flight Boost
		com.projecthero.mod.ironman.HulkbusterLanding.clearSessionState(); // v0.16.7 (user): the Hulkbuster landing
		com.projecthero.mod.ironman.IronManFallLanding.clearSessionState(); // v0.16.7 (user): the fall landing, every suit
		CraterAmbience.clearSessionState();
		TempBlocks.clearSessionState();
		com.projecthero.mod.hero.power.p09.CryoIce.clearSessionState(); // v0.16.5: Cryokinesis ice (melted on SERVER_STOPPING)
		com.projecthero.mod.hero.power.p05.GeoTerrain.clearSessionState(); // v0.16.5: the wall / sinkhole ledger (restored on STOPPING)
		ConjuredStructures.clearSessionState();
		com.projecthero.mod.hero.power.ComboMoves.reset();
		MagneticHandlers.clearSessionState();
		ElectrokinesisHandlers.clearSessionState();
		ShadowManipulationHandlers.clearSessionState();
		IronManSuitCall.clearPending();
		com.projecthero.mod.flight.FlightLanding.clearSessionState(); // v0.15.19: take-off ticks for the landing rule
		com.projecthero.mod.ironman.IronManSuitArrows.clearSessionState(); // v0.15.9: last suit signature per player
		com.projecthero.mod.ironman.suit.PortableDeploy.clearSessionState(); // v0.16.7 (user) Era II: Shift+C fold holds
		com.projecthero.mod.ironman.crate.HulkbusterCrate.clearSessionState(); // v0.16.7 (user) Era II: Hulkbuster crate folds
		com.projecthero.mod.ironman.HulkbusterShield.clearSessionState(); // v0.16.7 (user): Hulkbuster hit i-frames
		com.projecthero.mod.ironman.entity.HulkbusterPodEntity.clearSessionState(); // v0.16.7 (user): Hulkbuster mid-air builds
		com.projecthero.mod.ironman.gantry.StarkGantry.clearSessionState(); // v0.15.4: where each suit last came from
		com.projecthero.mod.ironman.drone.IronManDrones.clearSessionState(); // v0.14.29 agent E: Remote Pilot links
		com.projecthero.mod.ironman.entity.IronManSentryEntity.clearSessionState(); // v0.15.9: Sentry Mode step-in immunity
		com.projecthero.mod.ironman.ability.IronManMark3.clearSessionState(); // v0.14.27 (agent D)
		com.projecthero.mod.ironman.ability.IronManMark6.clearSessionState(); // v0.14.29 (agent C)
		com.projecthero.mod.ironman.ability.IronManMark7.clearSessionState();
		com.projecthero.mod.ironman.ability.IronManHeldBeam.clearSessionState();
		// v0.16.1 Iron Man comic rework: running comic moves / areas / follow-ups, held keys, the combat clock
		com.projecthero.mod.ironman.ability.IronManComicState.clearSessionState();
		com.projecthero.mod.ironman.ability.IronManMark9.clearSessionState(); // v0.16.7 Era II: Mk IX heat sinks, magma bombs + pools
		com.projecthero.mod.ironman.ability.era1.EraOneMk5Stealth.clearSessionState(); // v0.16.7 (user): Mk V Active Camo / Silent Running / sleepers
		com.projecthero.mod.ironman.ability.IronManComicRouter.clearSessionState();
		com.projecthero.mod.ironman.ability.era1.EraOneMk34State.clearSessionState(); // v0.16.7 Era I: Mk III / Mk IV running moves, mines, charges, lamps
		com.projecthero.mod.ironman.ability.era1.EraOneMk12State.clearSessionState(); // v0.16.7 Era I Mk I / Mk II running moves
		com.projecthero.mod.ironman.IronManIntegrity.clearSessionState();
		com.projecthero.mod.ironman.ability.era1.EraOneEject.clearSessionState(); // v0.16.7: the Shift+C eject holds + launches
		com.projecthero.mod.ironman.crate.CrateSuitUp.clearSessionState(); // v0.16.7 (user): who is suiting up from which Suit Crate
		com.projecthero.mod.event.entity.CursedZombieSpawns.clearSessionState();
		com.projecthero.mod.event.raid.ZombieRaidNetworking.clearSessionState();
		com.projecthero.mod.worldgen.GraveyardTracker.clearSessionState();
		com.projecthero.mod.power.ThorTwinThunder.clearSessionState(); // v0.16.1: queued Twin Thunder Strikes
		com.projecthero.mod.power.ThunderField.clearSessionState(); // v0.16.1: Thunder Field throws + live fields
		com.projecthero.mod.spider.SpiderWebs.clearSessionState();
		com.projecthero.mod.spider.SpiderCombat.clearSessionState();
		com.projecthero.mod.spider.web.WebStructures.clearSessionState(); // v0.16.1: the web structure registry
		com.projecthero.mod.spider.SpiderWrap.clearSessionState(); // v0.16.1: cocoon pins
		com.projecthero.mod.spider.SpiderStruggle.clearSessionState(); // v0.16.4: cocoon struggle progress
		com.projecthero.mod.spider.SpiderMoves.clearSessionState(); // v0.16.1: key holds, combos, stances, grabs
		com.projecthero.mod.spider.SpiderTripMines.clearSessionState(); // v0.16.2: each player's Trip Mines (the 6 cap)
		com.projecthero.mod.spider.SpiderFreerun.clearSessionState(); // v0.16.2: wall runs + trick combos
		com.projecthero.mod.maxsteel.MaxSteelAbilityManager.clearSessionState();
		com.projecthero.mod.maxsteel.MaxSteelCannon.clearSessionState();
		com.projecthero.mod.maxsteel.worldgen.SteelCrashAmbience.clearSessionState();
		com.projecthero.mod.symbiote.worldgen.SymbioteWorldgen.clearSessionState();
		com.projecthero.mod.firearm.FirearmManager.clearSessionState();
		com.projecthero.mod.punisher.PunisherControl.clearSessionState(); // v0.15.18: stuns / no-target windows
		com.projecthero.mod.punisher.ability.PunisherMark.clearSessionState(); // v0.15.18
		com.projecthero.mod.punisher.ability.PunisherSmoke.clearSessionState(); // v0.15.18
		com.projecthero.mod.punisher.ability.PunisherWarzone.clearSessionState(); // v0.15.18
		com.projecthero.mod.punisher.PunisherAbilityManager.clearSessionState();
		com.projecthero.mod.behemoth.BehemothSpawner.clearSessionState();
		com.projecthero.mod.carnage.CarnageSpawner.clearSessionState(); // v0.14.25
		com.projecthero.mod.crime.CrimeScheduler.clearSessionState(); // v0.16.2: per-player / per-village crime cooldowns
		com.projecthero.mod.city.City.clearSessionState(); // v0.16.2 City part B: city chunks, blueprint cache, damage queue, jobs, cops
		com.projecthero.mod.sentinel.SentinelSpawner.clearSessionState(); // v0.15.1
		com.projecthero.mod.sentinel.SentinelTargets.clearSessionState(); // v0.15.1
		com.projecthero.mod.ultron.MindStoneCharm.clearSessionState(); // v0.15.12: Mind Stone charms
		com.projecthero.mod.ultron.UltronJam.clearSessionState(); // v0.16.1: jammed ability keys (Hijacker / Network Override)
		com.projecthero.mod.frostgiant.entity.IcePrisonEntity.clearSessionState(); // v0.16.1: who is inside Laufey's ice
		com.projecthero.mod.ultron.contagion.SuitHijack.clearSessionState(); // v0.16.1: hijack attempts / locks
		com.projecthero.mod.ultron.contagion.MindStoneBeam.clearSessionState(); // v0.16.1: Mind Stone Beam holds
		com.projecthero.mod.ultron.contagion.NaniteCorruption.clearSessionState(); // v0.16.1: revert-queue cursor
		com.projecthero.mod.ironman.IronManTargeting.clearSessionState(); // v0.14.26
		com.projecthero.mod.ironman.ability.IronManAbilities.clearAutoHighlightState(); // v0.15.1
		com.projecthero.mod.ironman.ability.IronManDash.clearSessionState(); // v0.14.27
		com.projecthero.mod.ironman.ability.IronManFlares.clearSessionState(); // v0.14.27
		com.projecthero.mod.ironman.IronManCombo.clearSessionState(); // v0.14.29 agent F
		com.projecthero.mod.ironman.JarvisDialogue.clearSessionState(); // v0.14.29 agent F
		com.projecthero.mod.oathbreaker.OathbreakerSummon.clearSessionState();
		com.projecthero.mod.punisher.PunisherArmorGate.clearSessionState();
		com.projecthero.mod.punisher.ability.PunisherWeaponAbilities.clearSessionState(); // v0.15.18: Weapon Ability (V) effects
		com.projecthero.mod.combat.SonicVulnerability.clearSessionState();
		com.projecthero.mod.symbiote.SymbioteAbilityManager.clearSessionState();
		com.projecthero.mod.symbiote.SymbioteBlackSuitAbilities.clearSessionState();
		com.projecthero.mod.spider.SpiderStealth.clearSessionState();
		com.projecthero.mod.spider.SpiderTakedown.clearSessionState(); // v0.16.2
		com.projecthero.mod.spider.SpiderPerfectDodge.clearSessionState(); // v0.16.2
		com.projecthero.mod.spider.SpiderWebs.clearGunk();
		com.projecthero.mod.symbiote.SymbioteAgentVenomAbilities.clearSessionState();
		com.projecthero.mod.symbiote.SymbioteVitalsManager.clearSessionState();
		com.projecthero.mod.symbiote.SymbioteDialogue.clearSessionState();
		com.projecthero.mod.symbiote.SymbioteBondGame.clearSessionState();
		com.projecthero.mod.symbiote.Symbiote.clearSessionState();
		com.projecthero.mod.hero.power.p11.TeleportationHandlers.clearSessionState();
		com.projecthero.mod.hero.power.p27.SizeHandlers.clearSessionState();
		com.projecthero.mod.wolverine.WolverineAbilityManager.clearSessionState();
		com.projecthero.mod.titanshifter.TitanShifterAbilityManager.clearSessionState();
		com.projecthero.mod.allmight.AllMightAbilityManager.clearSessionState();
		com.projecthero.mod.moonknight.ability.MoonKnightAbilityManager.clearSessionState();
		// Moon Knight Phases 5 + 6: Fist / Vanish timers, Eye holds, Judgement marks, the resurrection shield
		com.projecthero.mod.moonknight.ability.MoonKnightAlters.clearSessionState();
		com.projecthero.mod.moonknight.ability.MoonKnightKhonshu.clearSessionState();
		com.projecthero.mod.moonknight.MoonKnight.clearSessionState(); // v0.14.4: combat tracking for the Vengeance regen
		// v0.14.8: Kryptonian move sessions, flight / kryptonite trackers, and the Kryptonite Meteor schedule
		com.projecthero.mod.kryptonian.KryptonianMod.clearSessionState();
		// v0.15.13: Nova move sessions (beam, bolts, slam, dash, launch, lock), Worldmind marks, flight / toggle trackers
		com.projecthero.mod.nova.NovaMod.clearSessionState();
		// v0.16.5: Ghost Rider move sessions, bolts, guilt, Judgement Marks, summoned bikes
		com.projecthero.mod.ghostrider.GhostRiderMod.clearSessionState();
		// v0.16.6: the Miraculous core + kits (move sessions, summoned things)
		com.projecthero.mod.miraculous.MiraculousMod.clearSessionState();
		// v0.16.7: the Quirk core + kits, and Erasure's power suppression
		com.projecthero.mod.quirk.QuirkMod.clearSessionState();
		// v0.16.6: open spawn-in calling offers
		com.projecthero.mod.calling.SpawnCallings.clearSessionState();
		// v0.16.6: keys waiting to be resolved as tap or hold
		com.projecthero.mod.hero.HoldTap.clearSessionState();
		// v0.16.5: Cyborg move sessions, hacked machines, hover flight grants
		com.projecthero.mod.cyborg.CyborgMod.clearSessionState();
		// v0.17.1: Falcon move sessions, Redwing drones, carries, last positions
		com.projecthero.mod.falcon.FalconMod.clearSessionState();
		// v0.17.1: Silver Surfer move sessions, statues, lent board flight
		com.projecthero.mod.silversurfer.SilverSurferMod.clearSessionState();
		// v0.17.1: Speed Force Speedster runs (momentum readings, Rewind history), remnants, rescues, upgrade candidates
		com.projecthero.mod.speedforce.SpeedForceMod.clearSessionState();
		// v0.16.5: Blue Beetle move sessions, bubbles, Adaptation counts, Khaji-Da's lines, lent flight
		com.projecthero.mod.bluebeetle.BlueBeetleMod.clearSessionState();
		com.projecthero.mod.hulk.Hulk.clearSessionState();
		com.projecthero.mod.supersoldier.SuperSoldierAbilityManager.clearSessionState(); // v0.14.8: move timers, ultimate, marks
		com.projecthero.mod.moonknight.temple.KhonshuRitual.clearSessionState();
		com.projecthero.mod.hero.power.p23.GravityHandlers.clearSessionState();
		com.projecthero.mod.hero.power.p22.PlantManipulationHandlers.clearSessionState();
		com.projecthero.mod.greenlantern.GreenLanternAbilityManager.clearSessionState();
		com.projecthero.mod.greenlantern.GreenLanternCombat.clearSessionState();
		com.projecthero.mod.greenlantern.GreenLanternBattery.clearSessionState();
		com.projecthero.mod.greenlantern.GreenLanternEnergy.clearSessionState();
		com.projecthero.mod.greenlantern.GreenLanternFlight.clearSessionState();
		com.projecthero.mod.greenlantern.GreenLanternShield.clearSessionState();
		com.projecthero.mod.greenlantern.construct.GreenLanternConstructs.clearSessionState();
		com.projecthero.mod.greenlantern.GreenLanternTrial.clearSessionState();
		com.projecthero.mod.hero.UniquePowers.clearSessionState(); // v0.16.1: refusal-message throttle
		com.projecthero.mod.flight.landing.HeroLanding.clearSessionState(); // v0.17.1: hero landing tracks / holds
	}
}
