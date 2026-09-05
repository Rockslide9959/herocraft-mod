package com.herocraft.mod;

import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.command.HeroCommand;
import com.herocraft.mod.command.ThorCommand;
import com.herocraft.mod.diagnostics.TickWatchdog;
import com.herocraft.mod.entity.ModEntityTypes;
import com.herocraft.mod.hero.AbilityRouter;
import com.herocraft.mod.hero.HeroConfig;
import com.herocraft.mod.hero.PowerPassives;
import com.herocraft.mod.hero.Powers;
import com.herocraft.mod.hero.power.HeroDamageRules;
import com.herocraft.mod.hero.power.HeroFlight;
import com.herocraft.mod.hero.power.HeroPowerHandlers;
import com.herocraft.mod.hero.power.TempBlocks;
import com.herocraft.mod.hero.device.ModDevices;
import com.herocraft.mod.hero.item.HeroPackItems;
import com.herocraft.mod.hero.mutation.ModBrewing;
import com.herocraft.mod.hero.mutation.ModMobEffects;
import com.herocraft.mod.hero.mutation.ModSerums;
import com.herocraft.mod.hero.mutation.MutationManager;
import com.herocraft.mod.ironman.IronManBlocks;
import com.herocraft.mod.ironman.IronManFlight;
import com.herocraft.mod.ironman.IronManSuitTicker;
import com.herocraft.mod.ironman.TonyStark;
import com.herocraft.mod.ironman.entity.IronManEntityTypes;
import com.herocraft.mod.ironman.item.IronManItems;
import com.herocraft.mod.ironman.suit.IronManSuits;
import com.herocraft.mod.command.IronManCommand;
import com.herocraft.mod.item.ModItems;
import com.herocraft.mod.network.ModNetworking;
import com.herocraft.mod.power.ThorPassives;
import com.herocraft.mod.power.ThorPowers;
import com.herocraft.mod.sound.HeroCraftSounds;
import com.herocraft.mod.worldgen.CraterAmbience;
import com.herocraft.mod.worldgen.ModStructurePieceTypes;
import com.herocraft.mod.worldgen.ModStructureTypes;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.resources.ResourceLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeroCraftMod implements ModInitializer {
	public static final String MOD_ID = "herocraft";

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
		com.herocraft.mod.titan.TitanHealthCap.initialize();
		HeroConfig.load();
		com.herocraft.mod.event.EventConfig.load();
		com.herocraft.mod.titan.TitanConfig.load();
		ModAttachments.initialize();
		ModItems.initialize();
		IronManItems.initialize();
		IronManBlocks.initialize();
		IronManSuits.initialize();
		IronManEntityTypes.initialize();
		ModEntityTypes.initialize();
		HeroCraftSounds.initialize();
		com.herocraft.mod.spider.item.SpiderItems.initialize();
		com.herocraft.mod.maxsteel.item.MaxSteelItems.initialize();
		com.herocraft.mod.maxsteel.entity.MaxSteelEntityTypes.initialize();
		com.herocraft.mod.symbiote.entity.SymbioteEntityTypes.initialize();
		com.herocraft.mod.symbiote.item.SymbioteHostItems.initialize();
		com.herocraft.mod.titan.entity.TitanEntityTypes.initialize();
		com.herocraft.mod.firearm.item.FirearmItems.initialize();
		com.herocraft.mod.punisher.item.PunisherItems.initialize();
		com.herocraft.mod.punisher.entity.PunisherEntityTypes.initialize();
		Powers.initialize();
		HeroPowerHandlers.registerAll();
		ModDevices.initialize();
		ModMobEffects.initialize();
		ModSerums.initialize();
		HeroPackItems.initialize();
		ModBrewing.initialize();
		MutationManager.initialize();
		HeroDamageRules.initialize();
		com.herocraft.mod.ironman.IronManDamage.initialize();
		// Spider-Man: the Spider Sense dodge veto and the traversal fall rules are two more
		// ALLOW_DAMAGE listeners, registered exactly like Thor's and Iron Man's.
		com.herocraft.mod.spider.SpiderSense.initialize();
		com.herocraft.mod.spider.SpiderPassives.initialize();
		com.herocraft.mod.maxsteel.MaxSteelDamage.initialize();
		com.herocraft.mod.punisher.Punisher.initialize();
		com.herocraft.mod.punisher.PunisherDamage.initialize();
		com.herocraft.mod.symbiote.SymbioteDamageRules.initialize();
		com.herocraft.mod.combat.SonicTriggers.initialize();
		ModNetworking.initialize();
		ThorCommand.initialize();
		HeroCommand.initialize();
		IronManCommand.initialize();
		com.herocraft.mod.command.SuperheroCommand.initialize();
		com.herocraft.mod.command.SpiderManCommand.initialize();
		com.herocraft.mod.command.MaxSteelCommand.initialize();
		com.herocraft.mod.command.PunisherCommand.initialize();
		com.herocraft.mod.command.SymbioteCommand.initialize();
		com.herocraft.mod.command.TitanCommand.initialize();
		// ---- Zombie Raid / world-event framework ----
		com.herocraft.mod.grave.item.GraveItems.initialize();
		com.herocraft.mod.event.entity.RaidEntityTypes.initialize();
		com.herocraft.mod.event.boss.BossPowers.initialize();
		com.herocraft.mod.event.EventTypes.initialize();
		com.herocraft.mod.event.entity.CursedZombieSpawns.initialize();
		com.herocraft.mod.grave.GraveboundEvents.initialize();
		com.herocraft.mod.command.ZombieRaidCommand.initialize();
		com.herocraft.mod.worldgen.GraveyardTracker.initialize();
		// ---- Supervillain Village Raid ----
		com.herocraft.mod.event.raid.SupervillainRaidItems.initialize();
		com.herocraft.mod.event.raid.SupervillainRaidEvents.initialize();
		com.herocraft.mod.command.SupervillainRaidCommand.initialize();
		com.herocraft.mod.command.HeroRaidCommand.initialize();

		ModStructureTypes.initialize();
		ModStructurePieceTypes.initialize();
		CraterAmbience.initialize();
		com.herocraft.mod.maxsteel.worldgen.SteelCrashAmbience.initialize();
		com.herocraft.mod.symbiote.worldgen.SymbioteWorldgen.initialize();

		// See TickWatchdog's own javadoc: this is a permanent, near-zero-cost anomaly detector wrapped
		// around exactly the per-player call a "the world sometimes completely freezes" report pointed
		// at, not temporary debug spam -- it stays silent unless a single call genuinely takes far
		// longer than anything in this path should.
		com.herocraft.mod.hero.power.ConjuredStructures.initialize();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			TempBlocks.tick(server);
			com.herocraft.mod.hero.power.ConjuredStructures.tick(server);
			com.herocraft.mod.hero.power.p26.MagneticHandlers.tick(server);
			com.herocraft.mod.ironman.data.StarkSuitReturnQueue.tick(server);
			com.herocraft.mod.event.EventManager.tick(server);
			com.herocraft.mod.event.raid.PillagerSpySpawner.tick(server);
			com.herocraft.mod.titan.TitanSpawner.tick(server);
			com.herocraft.mod.spider.SpiderWebs.tick(server);
			server.getPlayerList().getPlayers().forEach(player -> {
				TickWatchdog.run("ThorPowers.serverTick", () -> ThorPowers.serverTick(player));
				TickWatchdog.run("HeroFlight.tick", () -> HeroFlight.tick(player));
				TickWatchdog.run("AbilityRouter.serverTick", () -> AbilityRouter.serverTick(player));
				TickWatchdog.run("MutationManager.serverTick", () -> MutationManager.serverTick(player));
				TickWatchdog.run("IronManSuitTicker.tick", () -> IronManSuitTicker.tick(player));
				// "changes 22": bare Repulsor boots -- flight without a suit, so it has its own tick.
				TickWatchdog.run("RepulsorBoots.tick", () -> com.herocraft.mod.ironman.RepulsorBoots.tick(player));
				TickWatchdog.run("GraveboundEvents.serverTick", () -> com.herocraft.mod.grave.GraveboundEvents.serverTick(player));
				TickWatchdog.run("FirearmManager.serverTick", () -> com.herocraft.mod.firearm.FirearmManager.serverTick(player));
			});
		});

		// Every static server-side scratch collection in the mod (Iron Man "armour is travelling to you"
		// countdowns, crater ambience, temp blocks, conjured constructs, magnetic projectiles, shock /
		// shadow-bind markers) is dropped when the server stops, and the two markers nothing else owns
		// are swept periodically. A single-player client reuses this JVM for the next world it opens,
		// so without this the previous world stayed reachable -- see ServerStateReset for the full
		// write-up of the "it gets laggy after a long session" report this addresses.
		com.herocraft.mod.diagnostics.ServerStateReset.initialize();

		// Fall/lightning immunity for the Power of Thor.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ThorPassives::onAllowDamage);

		// Iron Man: Protocol Phoenix ("changes 17") gets first refusal on a Tony Stark player's death --
		// if it takes over, the death is cancelled and ordinary suit recovery is skipped. Otherwise a
		// suit worn on death flies itself home to the nearest Suit Platform (banged up) instead of
		// littering the ground -- runs before Player.die() drops equipment.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof net.minecraft.server.level.ServerPlayer sp) {
				if (com.herocraft.mod.ironman.ProtocolPhoenix.tryActivate(sp, source)) {
					return false;
				}
				// Max Steel: dying while unsuited pops an emergency totem -- the suit slams on around
				// the downed pilot (v0.6.17). Costs 150 T.U.R.B.O. Energy and 20 minutes of cooldown.
				if (com.herocraft.mod.maxsteel.MaxSteelTransform.tryEmergencyRevive(sp)) {
					return false;
				}
				com.herocraft.mod.ironman.suit.IronManSuitCall.recoverSuitOnDeath(sp);
				// A web line must not still be drawn on a corpse -- drop the anchor as the player dies
				// rather than waiting for the respawn (spec section 39).
				com.herocraft.mod.spider.SpiderMan.clearTransient(sp);
				// v0.9.14: the Symbiote is a standalone power now -- its own teardown must run for EVERY
				// player, not only via SpiderMan.clearTransient (which only ever covered the Spider-Man
				// variant). Idempotent / harmless to call twice for a bonded Spider-Man.
				com.herocraft.mod.symbiote.Symbiote.clearTransient(sp);
				// Max Steel: drop the suit on death (the power itself is kept via copyOnDeath) so no
				// flight/speed/stealth state or attribute modifier survives onto the corpse or respawn.
				com.herocraft.mod.maxsteel.MaxSteel.clearTransient(sp);
				// Punisher: drop Adrenaline / Suppressive / roll modifiers and clear placed C4 (the
				// power itself is kept via copyOnDeath).
				com.herocraft.mod.punisher.Punisher.clearTransient(sp);
			}
			return true;
		});

		// Symbiote Host: when the rare taken-over mob dies, the Symbiote leaps clear of the corpse as a
		// free-floating entity a Spider-Man can bond with.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) ->
				com.herocraft.mod.symbiote.SymbioteHost.onDeath(entity));

		// "changes 17": while Protocol Phoenix has a player incapacitated, veto every interaction --
		// attacking, breaking / placing / using blocks, using items, interacting with entities.
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register(
				(player, world, hand, entity, hit) -> phoenixFrozen(player)
						? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);

		// Max Steel: a melee hit marks the player "in combat" (regen rate) and, in Turbo Strength Mode
		// while sprinting, triggers Heavy Punch.
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (player instanceof net.minecraft.server.level.ServerPlayer sp && com.herocraft.mod.maxsteel.MaxSteel.isTransformed(sp)) {
				com.herocraft.mod.maxsteel.MaxSteelDamage.onDealtDamage(sp);
				com.herocraft.mod.maxsteel.MaxSteelStrength.onMeleeHit(sp, entity);
				com.herocraft.mod.maxsteel.MaxSteelStealth.onOffensiveAction(sp);
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
				player.getAttachedOrElse(com.herocraft.mod.attachment.ModAttachments.IRON_MAN_BLADES, false)
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
			com.herocraft.mod.hero.ExperimentalPowers.reconcileToggles(player);
			HeroFlight.clearStale(player);
			IronManFlight.clearStale(player);
			com.herocraft.mod.ironman.RepulsorBoots.clearStale(player);
			TonyStark.onPlayerJoin(player);
			com.herocraft.mod.ironman.ProtocolPhoenix.clearEmergency(player);
			com.herocraft.mod.spider.SpiderMan.onPlayerJoin(player);
			com.herocraft.mod.maxsteel.MaxSteel.onPlayerJoin(player);
			com.herocraft.mod.punisher.Punisher.onPlayerJoin(player);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			ThorPowers.onPlayerRespawn(newPlayer);
			ThorPassives.onPlayerRespawn(newPlayer);
			PowerPassives.reconcileActive(newPlayer);
			com.herocraft.mod.hero.ExperimentalPowers.reconcileToggles(newPlayer);
			HeroFlight.clearStale(newPlayer);
			IronManFlight.clearStale(newPlayer);
			com.herocraft.mod.ironman.RepulsorBoots.clearStale(newPlayer);
			TonyStark.onPlayerRespawn(newPlayer);
			com.herocraft.mod.ironman.ProtocolPhoenix.clearEmergency(newPlayer);
			com.herocraft.mod.spider.SpiderMan.onPlayerRespawn(newPlayer);
			com.herocraft.mod.maxsteel.MaxSteel.onPlayerRespawn(newPlayer);
			com.herocraft.mod.punisher.Punisher.onPlayerRespawn(newPlayer);
		});

		// Spider-Man traversal cleanup (spec sections 39-41). A swing anchor is a raw coordinate, so
		// it means something completely different -- or nothing -- after a dimension change, and a
		// logged-out player must not leave an attachment, an anchor or a net behind them.
		net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD
				.register((player, origin, destination) -> {
					com.herocraft.mod.spider.SpiderMan.clearTransient(player);
					// A Turbo Mode / suit-up in progress means nothing in a new dimension -- drop it.
					com.herocraft.mod.maxsteel.MaxSteel.clearTransient(player);
					com.herocraft.mod.punisher.Punisher.clearTransient(player);
					com.herocraft.mod.symbiote.Symbiote.clearTransient(player);
				});
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			com.herocraft.mod.spider.SpiderMan.clearTransient(handler.getPlayer());
			com.herocraft.mod.spider.SpiderWebs.clearFor(handler.getPlayer());
			com.herocraft.mod.maxsteel.MaxSteel.clearTransient(handler.getPlayer());
			com.herocraft.mod.firearm.FirearmManager.onCleanup(handler.getPlayer().getUUID());
			com.herocraft.mod.punisher.Punisher.clearTransient(handler.getPlayer());
			com.herocraft.mod.symbiote.Symbiote.clearTransient(handler.getPlayer());
		});

		LOGGER.info("HeroCraft is assembling!");
	}

	private static boolean phoenixFrozen(net.minecraft.world.entity.player.Player player) {
		return player instanceof net.minecraft.server.level.ServerPlayer sp
				&& com.herocraft.mod.ironman.ProtocolPhoenix.incapacitated(sp);
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
