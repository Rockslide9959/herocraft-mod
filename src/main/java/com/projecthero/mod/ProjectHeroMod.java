package com.projecthero.mod;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.diagnostics.TickWatchdog;
import com.projecthero.mod.entity.ModEntityTypes;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.PowerPassives;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.HeroDamageRules;
import com.projecthero.mod.hero.power.HeroFlight;
import com.projecthero.mod.hero.power.HeroPowerHandlers;
import com.projecthero.mod.hero.power.TempBlocks;
import com.projecthero.mod.hero.device.ModDevices;
import com.projecthero.mod.hero.item.HeroPackItems;
import com.projecthero.mod.hero.mutation.ModBrewing;
import com.projecthero.mod.hero.mutation.ModMobEffects;
import com.projecthero.mod.hero.mutation.ModSerums;
import com.projecthero.mod.hero.mutation.MutationManager;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.IronManSuitTicker;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.entity.IronManEntityTypes;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.network.ModNetworking;
import com.projecthero.mod.power.ThorPassives;
import com.projecthero.mod.power.ThorPowers;
import com.projecthero.mod.sound.ProjectHeroSounds;
import com.projecthero.mod.worldgen.CraterAmbience;
import com.projecthero.mod.worldgen.ModStructurePieceTypes;
import com.projecthero.mod.worldgen.ModStructureTypes;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.resources.ResourceLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProjectHeroMod implements ModInitializer {
	public static final String MOD_ID = "projecthero";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		// Widen the shared MAX_HEALTH attribute ceiling before anything else touches it -- the Titan
		// needs a genuine 1500 HP pool, past vanilla's silent 1024 clamp. See TitanHealthCap's javadoc.
		com.projecthero.mod.titan.TitanHealthCap.initialize();
		HeroConfig.load();
		com.projecthero.mod.event.EventConfig.load();
		com.projecthero.mod.titan.TitanConfig.load();
		ModAttachments.initialize();
		ModItems.initialize();
		IronManItems.initialize();
		IronManBlocks.initialize();
		IronManSuits.initialize();
		IronManEntityTypes.initialize();
		ModEntityTypes.initialize();
		ProjectHeroSounds.initialize();
		com.projecthero.mod.spider.item.SpiderItems.initialize();
		com.projecthero.mod.maxsteel.item.MaxSteelItems.initialize();
		com.projecthero.mod.maxsteel.entity.MaxSteelEntityTypes.initialize();
		com.projecthero.mod.symbiote.entity.SymbioteEntityTypes.initialize();
		com.projecthero.mod.symbiote.item.SymbioteHostItems.initialize();
		com.projecthero.mod.titan.entity.TitanEntityTypes.initialize();
		com.projecthero.mod.firearm.item.FirearmItems.initialize();
		com.projecthero.mod.punisher.item.PunisherItems.initialize();
		com.projecthero.mod.punisher.entity.PunisherEntityTypes.initialize();
		com.projecthero.mod.greenlantern.item.GreenLanternItems.initialize();
		com.projecthero.mod.hero.power.p05.GeoEntityTypes.initialize();
		Powers.initialize();
		HeroPowerHandlers.registerAll();
		ModDevices.initialize();
		ModMobEffects.initialize();
		ModSerums.initialize();
		HeroPackItems.initialize();
		ModBrewing.initialize();
		MutationManager.initialize();
		HeroDamageRules.initialize();
		com.projecthero.mod.ironman.IronManDamage.initialize();
		// Spider-Man: the Spider Sense dodge veto and the traversal fall rules are two more
		// ALLOW_DAMAGE listeners, registered exactly like Thor's and Iron Man's.
		com.projecthero.mod.spider.SpiderSense.initialize();
		com.projecthero.mod.spider.SpiderPassives.initialize();
		com.projecthero.mod.maxsteel.MaxSteelDamage.initialize();
		com.projecthero.mod.punisher.Punisher.initialize();
		com.projecthero.mod.punisher.PunisherDamage.initialize();
		com.projecthero.mod.greenlantern.GreenLanternDamage.initialize();
		com.projecthero.mod.greenlantern.construct.GreenLanternConstructs.initialize();
		com.projecthero.mod.symbiote.SymbioteDamageRules.initialize();
		com.projecthero.mod.symbiote.SymbioteVitalsManager.initialize();
		com.projecthero.mod.combat.SonicTriggers.initialize();
		ModNetworking.initialize();
		// ---- Zombie Raid / world-event framework ----
		com.projecthero.mod.grave.item.GraveItems.initialize();
		com.projecthero.mod.event.entity.RaidEntityTypes.initialize();
		com.projecthero.mod.event.boss.BossPowers.initialize();
		com.projecthero.mod.event.EventTypes.initialize();
		com.projecthero.mod.event.entity.CursedZombieSpawns.initialize();
		com.projecthero.mod.grave.GraveboundEvents.initialize();
		com.projecthero.mod.worldgen.GraveyardTracker.initialize();
		// ---- Supervillain Village Raid ----
		com.projecthero.mod.event.raid.SupervillainRaidItems.initialize();
		com.projecthero.mod.event.raid.SupervillainRaidEvents.initialize();
		// The one and only command root: /projecthero, which assembles every hero/event subtree from
		// each command class's build().
		com.projecthero.mod.command.ProjectHeroCommand.initialize();
		// ---- Squads: friendly-fire suppression + the roster the squad screen (P) draws. ----
		com.projecthero.mod.squad.Squads.initialize();

		ModStructureTypes.initialize();
		ModStructurePieceTypes.initialize();
		CraterAmbience.initialize();
		com.projecthero.mod.maxsteel.worldgen.SteelCrashAmbience.initialize();
		com.projecthero.mod.symbiote.worldgen.SymbioteWorldgen.initialize();

		// See TickWatchdog's own javadoc: this is a permanent, near-zero-cost anomaly detector wrapped
		// around exactly the per-player call a "the world sometimes completely freezes" report pointed
		// at, not temporary debug spam -- it stays silent unless a single call genuinely takes far
		// longer than anything in this path should.
		com.projecthero.mod.hero.power.ConjuredStructures.initialize();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			TempBlocks.tick(server);
			com.projecthero.mod.hero.power.ConjuredStructures.tick(server);
			com.projecthero.mod.hero.power.p26.MagneticHandlers.tick(server);
			com.projecthero.mod.ironman.data.StarkSuitReturnQueue.tick(server);
			com.projecthero.mod.event.EventManager.tick(server);
			com.projecthero.mod.event.raid.PillagerSpySpawner.tick(server);
			com.projecthero.mod.titan.TitanSpawner.tick(server);
			com.projecthero.mod.spider.SpiderWebs.tick(server);
			com.projecthero.mod.greenlantern.construct.GreenLanternConstructs.tick(server);
			// Once/sec, for every online player (not just bonded Green Lanterns) -- a traded, gifted or
			// chest-stashed hard-light tool can end up on anyone, not only the Lantern who deployed it.
			if (server.getTickCount() % 20 == 0) {
				com.projecthero.mod.greenlantern.construct.GreenLanternConstructs.sweepLooseToolKitPieces(server);
			}
			com.projecthero.mod.greenlantern.GreenLanternTrial.tick(server);
			server.getPlayerList().getPlayers().forEach(player -> {
				TickWatchdog.run("ThorPowers.serverTick", () -> ThorPowers.serverTick(player));
				TickWatchdog.run("HeroFlight.tick", () -> HeroFlight.tick(player));
				TickWatchdog.run("AbilityRouter.serverTick", () -> AbilityRouter.serverTick(player));
				TickWatchdog.run("MutationManager.serverTick", () -> MutationManager.serverTick(player));
				TickWatchdog.run("IronManSuitTicker.tick", () -> IronManSuitTicker.tick(player));
				// "changes 22": bare Repulsor boots -- flight without a suit, so it has its own tick.
				TickWatchdog.run("RepulsorBoots.tick", () -> com.projecthero.mod.ironman.RepulsorBoots.tick(player));
				TickWatchdog.run("GraveboundEvents.serverTick", () -> com.projecthero.mod.grave.GraveboundEvents.serverTick(player));
				TickWatchdog.run("FirearmManager.serverTick", () -> com.projecthero.mod.firearm.FirearmManager.serverTick(player));
				TickWatchdog.run("Squads.serverTick", () -> com.projecthero.mod.squad.Squads.serverTick(player));
			});
		});

		// Every static server-side scratch collection in the mod (Iron Man "armour is travelling to you"
		// countdowns, crater ambience, temp blocks, conjured constructs, magnetic projectiles, shock /
		// shadow-bind markers) is dropped when the server stops, and the two markers nothing else owns
		// are swept periodically. A single-player client reuses this JVM for the next world it opens,
		// so without this the previous world stayed reachable -- see ServerStateReset for the full
		// write-up of the "it gets laggy after a long session" report this addresses.
		com.projecthero.mod.diagnostics.ServerStateReset.initialize();

		// Fall/lightning immunity for the Power of Thor.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ThorPassives::onAllowDamage);

		// Iron Man: Protocol Phoenix ("changes 17") gets first refusal on a Tony Stark player's death --
		// if it takes over, the death is cancelled and ordinary suit recovery is skipped. Otherwise a
		// suit worn on death flies itself home to the nearest Suit Platform (banged up) instead of
		// littering the ground -- runs before Player.die() drops equipment.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof net.minecraft.server.level.ServerPlayer sp) {
				if (com.projecthero.mod.ironman.ProtocolPhoenix.tryActivate(sp, source)) {
					return false;
				}
				// Max Steel: dying while unsuited pops an emergency totem -- the suit slams on around
				// the downed pilot (v0.6.17). Costs 150 T.U.R.B.O. Energy and 20 minutes of cooldown.
				if (com.projecthero.mod.maxsteel.MaxSteelTransform.tryEmergencyRevive(sp)) {
					return false;
				}
				com.projecthero.mod.ironman.suit.IronManSuitCall.recoverSuitOnDeath(sp);
				// A web line must not still be drawn on a corpse -- drop the anchor as the player dies
				// rather than waiting for the respawn (spec section 39).
				com.projecthero.mod.spider.SpiderMan.clearTransient(sp);
				// v0.9.14: the Symbiote is a standalone power now -- its own teardown must run for EVERY
				// player, not only via SpiderMan.clearTransient (which only ever covered the Spider-Man
				// variant). Idempotent / harmless to call twice for a bonded Spider-Man.
				com.projecthero.mod.symbiote.Symbiote.clearTransient(sp);
				// Max Steel: drop the suit on death (the power itself is kept via copyOnDeath) so no
				// flight/speed/stealth state or attribute modifier survives onto the corpse or respawn.
				com.projecthero.mod.maxsteel.MaxSteel.clearTransient(sp);
				// Punisher: drop Adrenaline / Suppressive / roll modifiers and clear placed C4 (the
				// power itself is kept via copyOnDeath).
				com.projecthero.mod.punisher.Punisher.clearTransient(sp);
				// Green Lantern: active constructs vanish, the suit deactivates, flight/shield/dome end
				// (the power and Ring Charge/Mastery are kept via copyOnDeath).
				com.projecthero.mod.greenlantern.GreenLantern.clearTransient(sp);
			}
			return true;
		});

		// Symbiote Host: when the rare taken-over mob dies, the Symbiote leaps clear of the corpse as a
		// free-floating entity a Spider-Man can bond with.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) ->
				com.projecthero.mod.symbiote.SymbioteHost.onDeath(entity));

		// "changes 17": while Protocol Phoenix has a player incapacitated, veto every interaction --
		// attacking, breaking / placing / using blocks, using items, interacting with entities.
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register(
				(player, world, hand, entity, hit) -> phoenixFrozen(player)
						? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);

		// Max Steel: a melee hit marks the player "in combat" (regen rate) and, in Turbo Strength Mode
		// while sprinting, triggers Heavy Punch.
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (player instanceof net.minecraft.server.level.ServerPlayer sp && com.projecthero.mod.maxsteel.MaxSteel.isTransformed(sp)) {
				com.projecthero.mod.maxsteel.MaxSteelDamage.onDealtDamage(sp);
				com.projecthero.mod.maxsteel.MaxSteelStrength.onMeleeHit(sp, entity);
				com.projecthero.mod.maxsteel.MaxSteelStealth.onOffensiveAction(sp);
			}
			return net.minecraft.world.InteractionResult.PASS;
		});
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register(
				(player, world, hand, entity, hit) -> phoenixFrozen(player)
						? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register(
				(player, world, hand, hit) -> phoenixFrozen(player)
						? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> phoenixFrozen(player)
				? net.minecraft.world.InteractionResultHolder.fail(player.getItemInHand(hand))
				: net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand)));
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register(
				(world, player, pos, state, be) -> !phoenixFrozen(player));

		// "changes 19": while the Mark 5's gauntlet blades are extended the pilot can't place blocks.
		// Read via the synced attachment so this fires client-side too (no place-then-revert flicker).
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) ->
				player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.IRON_MAN_BLADES, false)
						&& player.getItemInHand(hand).getItem() instanceof net.minecraft.world.item.BlockItem
						? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);

		// Safety nets for state that outlives (or fails to outlive) a session -- see ThorPowers'
		// "lifecycle safety nets" section for why each one is needed. The Power of Thor hooks are
		// here for a different reason: a respawned player is a *new* entity with none of the
		// transient attribute modifiers, so the passives have to be re-established rather than
		// merely preserved.
		ServerPlayerEvents.JOIN.register(ThorPowers::onPlayerJoin);
		ServerPlayerEvents.JOIN.register(ThorPassives::onPlayerJoin);
		ServerPlayerEvents.JOIN.register(player -> {
			PowerPassives.reconcileActive(player);
			com.projecthero.mod.hero.ExperimentalPowers.reconcileToggles(player);
			HeroFlight.clearStale(player);
			IronManFlight.clearStale(player);
			com.projecthero.mod.ironman.RepulsorBoots.clearStale(player);
			TonyStark.onPlayerJoin(player);
			com.projecthero.mod.ironman.ProtocolPhoenix.clearEmergency(player);
			com.projecthero.mod.spider.SpiderMan.onPlayerJoin(player);
			com.projecthero.mod.maxsteel.MaxSteel.onPlayerJoin(player);
			com.projecthero.mod.punisher.Punisher.onPlayerJoin(player);
			com.projecthero.mod.greenlantern.GreenLantern.onPlayerJoin(player);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			ThorPowers.onPlayerRespawn(newPlayer);
			ThorPassives.onPlayerRespawn(newPlayer);
			PowerPassives.reconcileActive(newPlayer);
			com.projecthero.mod.hero.ExperimentalPowers.reconcileToggles(newPlayer);
			HeroFlight.clearStale(newPlayer);
			IronManFlight.clearStale(newPlayer);
			com.projecthero.mod.ironman.RepulsorBoots.clearStale(newPlayer);
			TonyStark.onPlayerRespawn(newPlayer);
			com.projecthero.mod.ironman.ProtocolPhoenix.clearEmergency(newPlayer);
			com.projecthero.mod.spider.SpiderMan.onPlayerRespawn(newPlayer);
			com.projecthero.mod.maxsteel.MaxSteel.onPlayerRespawn(newPlayer);
			com.projecthero.mod.punisher.Punisher.onPlayerRespawn(newPlayer);
			com.projecthero.mod.greenlantern.GreenLantern.onPlayerRespawn(newPlayer);
		});

		// Spider-Man traversal cleanup (spec sections 39-41). A swing anchor is a raw coordinate, so
		// it means something completely different -- or nothing -- after a dimension change, and a
		// logged-out player must not leave an attachment, an anchor or a net behind them.
		net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD
				.register((player, origin, destination) -> {
					com.projecthero.mod.spider.SpiderMan.clearTransient(player);
					// A Turbo Mode / suit-up in progress means nothing in a new dimension -- drop it.
					com.projecthero.mod.maxsteel.MaxSteel.clearTransient(player);
					com.projecthero.mod.punisher.Punisher.clearTransient(player);
					com.projecthero.mod.symbiote.Symbiote.clearTransient(player);
					com.projecthero.mod.greenlantern.GreenLantern.clearTransient(player);
				});
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			com.projecthero.mod.spider.SpiderMan.clearTransient(handler.getPlayer());
			com.projecthero.mod.spider.SpiderWebs.clearFor(handler.getPlayer());
			com.projecthero.mod.maxsteel.MaxSteel.clearTransient(handler.getPlayer());
			com.projecthero.mod.firearm.FirearmManager.onCleanup(handler.getPlayer().getUUID());
			com.projecthero.mod.punisher.Punisher.clearTransient(handler.getPlayer());
			com.projecthero.mod.symbiote.Symbiote.clearTransient(handler.getPlayer());
			com.projecthero.mod.greenlantern.GreenLantern.clearTransient(handler.getPlayer());
		});

		LOGGER.info("ProjectHero is assembling!");
	}

	private static boolean phoenixFrozen(net.minecraft.world.entity.player.Player player) {
		return player instanceof net.minecraft.server.level.ServerPlayer sp
				&& com.projecthero.mod.ironman.ProtocolPhoenix.incapacitated(sp);
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
