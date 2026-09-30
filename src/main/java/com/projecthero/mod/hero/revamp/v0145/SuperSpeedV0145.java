package com.projecthero.mod.hero.revamp.v0145;

import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationMeters.Kind;
import com.projecthero.mod.hero.visual.MutationMeters.Spec;
import com.projecthero.mod.hero.visual.MutationMeters.Style;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;

/**
 * v0.14.5 Super Speed rework: server-side registration (visual flags, HUD meters, event hooks) and the
 * per-tick hook for this power.
 */
public final class SuperSpeedV0145 {
	/** Speed Mode / Overdrive after-image trail (everyone sees it); {@link #TRAIL_RED} = 1 while Overdrive runs. */
	public static final String TRAIL = "p04.trail";
	public static final String TRAIL_RED = "p04.trail_red";

	private SuperSpeedV0145() {
	}

	public static void init() {
		MutationVisuals.registerFlag(TRAIL, p -> owns(p) && (SuperSpeedHandlers.speedMode(p) || SuperSpeedHandlers.overdrive(p)));
		MutationVisuals.registerValue(TRAIL_RED, p -> owns(p) && SuperSpeedHandlers.overdrive(p) ? 1 : 0);

		// HUD: running timers as Hairline bars above the key row (the Momentum gauge is gone with Momentum)
		MutationMeters.register(new Spec(SuperSpeedHandlers.KEY, SuperSpeedHandlers.OVERDRIVE_LEFT, Kind.TIMER, Style.HAIRLINE,
				"Overdrive", 600f, 0xFFE03A3A, false, false, true, 0));
		MutationMeters.register(new Spec(SuperSpeedHandlers.KEY, SuperSpeedTimeSlow.LEFT, Kind.TIMER, Style.HAIRLINE,
				"Time Slow", SuperSpeedTimeSlow.DURATION_TICKS, 0xFFB8B8B8, false, false, true, 0));

		// v0.14.7: after-image streaks (Blitz / Speed Sweep hops, the Vortex ring), and the game-wide Time Slow state
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(
				com.projecthero.mod.network.SpeedStreakPayload.TYPE, com.projecthero.mod.network.SpeedStreakPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(
				com.projecthero.mod.network.TimeSlowStatePayload.TYPE, com.projecthero.mod.network.TimeSlowStatePayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				SuperSpeedTimeSlow.onJoin(handler.getPlayer()));
		// ... a speedster logging out mid-Sweep is put back where they started before being saved
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				{
					com.projecthero.mod.hero.power.p04.SuperSpeedMoves.onDisconnect(handler.getPlayer());
					SuperSpeedTimeSlow.onDisconnect(handler.getPlayer());
				});

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			// Time Slow: the full-speed caster can keep hitting slowed victims at the normal cadence
			SuperSpeedTimeSlow.onIncomingDamage(entity, source);
			// Phase: a phasing speedster can't deal damage (their own immunity lives in HeroDamageRules)
			if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && SuperSpeedHandlers.phasing(attacker)) {
				return false;
			}
			// Speed Carry: no fall damage / suffocation while carried or for 3 s after being set down
			if ((source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FLY_INTO_WALL))
					&& SuperSpeedHandlers.carryProtected(entity)) {
				entity.resetFallDistance();
				return false;
			}
			// ... and the creature you carry can't hurt you
			if (entity instanceof ServerPlayer carrier && SuperSpeedHandlers.isCarriedBy(source.getEntity(), carrier)) {
				return false;
			}
			return true;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, target, hit) ->
				SuperSpeedHandlers.phasing(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		ServerLifecycleEvents.SERVER_STOPPING.register(SuperSpeedTimeSlow::onServerStopping);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SuperSpeedTimeSlow.clearSessionState();
			SuperSpeedHandlers.clearSessionState();
		});
	}

	public static void serverTick(MinecraftServer server) {
		SuperSpeedTimeSlow.serverTick(server);
		if (server.getTickCount() % 200 == 0) {
			SuperSpeedHandlers.prune(server);
		}
	}

	private static boolean owns(ServerPlayer p) {
		return ExperimentalPowers.owns(p, SuperSpeedHandlers.KEY);
	}
}
