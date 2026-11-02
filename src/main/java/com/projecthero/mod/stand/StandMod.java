package com.projecthero.mod.stand;

import com.projecthero.mod.stand.item.StandItems;
import com.projecthero.mod.stand.network.StandActionPayload;
import com.projecthero.mod.stand.network.TimeStopPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.2: one entry point for Stands, called once from {@code ProjectHeroMod.onInitialize}: the Stand Arrow (+ loot), the
 * sounds, the payloads (H / the menu in, the time stop out), the damage rules (time-stop queue, Guard, the dash catch),
 * the time-stop refusals and the lifecycle hooks. The per-player tick runs from {@code AbilityRouter.serverTick}; static
 * state is dropped by {@code ServerStateReset} through {@link #clearSessionState}.
 */
public final class StandMod {
	/** Re-entrancy guard for a Guard-reduced hit re-issued at its new amount. */
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private StandMod() {
	}

	public static void initialize() {
		StandItems.initialize();
		StandSounds.initialize();

		PayloadTypeRegistry.playC2S().register(StandActionPayload.TYPE, StandActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TimeStopPayload.TYPE, TimeStopPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.projecthero.mod.stand.network.StandFxPayload.TYPE,
				com.projecthero.mod.stand.network.StandFxPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(StandActionPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			if (!Stands.hasPower(p)) {
				return;
			}
			switch (payload.action()) {
				case TOGGLE_SUMMON -> {
					if (!com.projecthero.mod.hero.PowerSuppression.refuse(p, payload.action())) {
						Stands.toggleSummon(p);
					}
				}
				case SET_POSE -> Stands.setPoseChoice(p, payload.value());
				case SET_SETTINGS -> Stands.setSettings(p, (payload.value() & 1) != 0, (payload.value() & 2) != 0);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			TimeStop.tick(server);
			StandCombat.tickBleeding(server);
			StarPlatinum.tickThrown(server);
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(StandMod::allowDamage);

		// empty-hand Stand punches; nothing for a player frozen in stopped time
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			if (TimeStop.frozen(sp)) {
				return InteractionResult.FAIL;
			}
			return StarPlatinum.onAttack(sp, entity);
		});
		UseItemCallback.EVENT.register((player, level, hand) -> !level.isClientSide() && TimeStop.frozen(player)
				? InteractionResultHolder.fail(player.getItemInHand(hand))
				: InteractionResultHolder.pass(player.getItemInHand(hand)));
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> !level.isClientSide() && TimeStop.frozen(player)
				? InteractionResult.FAIL : InteractionResult.PASS);
		// inside the stop the caster can't open chests or use blocks either: it is for fighting and moving
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> !level.isClientSide() && (TimeStop.frozen(player) || TimeStop.isCaster(player))
				? InteractionResult.FAIL : InteractionResult.PASS);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> level.isClientSide()
				|| !(TimeStop.frozen(player) || TimeStop.isCaster(player)));

		ServerPlayerEvents.JOIN.register(Stands::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Stands.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && Stands.hasPower(p)) {
				Stands.clearTransient(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> Stands.clearTransient(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Stands.clearTransient(handler.getPlayer()));
	}

	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (REENTRANT.get()) {
			return true;
		}
		if (!TimeStop.allowDamage(victim, source, amount)) {
			return false;
		}
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != victim && Stands.hasPower(attacker)) {
			Stands.markCombat(attacker);
		}
		if (!(victim instanceof ServerPlayer p) || !Stands.hasPower(p)) {
			return true;
		}
		Stands.markCombat(p);
		if (!Stands.summoned(p) || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		if (StarPlatinum.tryDashCatch(p, source)) {
			return false;
		}
		if (StarPlatinum.guardsAgainst(p, source)) {
			float reduced = amount * StandConfig.GUARD_FACTOR;
			Stands.sound((net.minecraft.server.level.ServerLevel) p.level(), p.position(), StandSounds.GUARD, 0.6f, 1.3f);
			if (reduced >= 0.05f) {
				reissue(p, source, reduced);
			}
			return false;
		}
		return true;
	}

	private static void reissue(ServerPlayer player, DamageSource source, float amount) {
		if (source.scalesWithDifficulty()) {
			// Player#hurt scales a mob's hit by the difficulty BEFORE this veto sees it, and would scale the re-issued amount
			// again: hand it the pre-scaling amount so it lands once
			amount = switch (player.level().getDifficulty()) {
				case HARD -> amount / 1.5f;
				case EASY -> amount >= 2f ? 2f * (amount - 1f) : amount;
				default -> amount;
			};
		}
		REENTRANT.set(true);
		try {
			player.hurt(source, amount);
		} finally {
			REENTRANT.set(false);
		}
	}

	/** Every static scratch collection of the power (called by {@code ServerStateReset}). */
	public static void clearSessionState() {
		Stands.clearSessionState();
	}
}
