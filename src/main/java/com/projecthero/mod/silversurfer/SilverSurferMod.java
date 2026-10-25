package com.projecthero.mod.silversurfer;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.silversurfer.network.SilverSurferActionPayload;
import com.projecthero.mod.silversurfer.network.SurferStatuePayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * v0.17.1: one entry point for the whole Silver Surfer power, called once from {@code ProjectHeroMod.onInitialize}: the
 * attachment, the thrown-board entity, the damage rules, the payloads and the lifecycle hooks. The Silver Surfer's Board
 * ({@code galactus.item.SurfboardItem}) calls {@link SilverSurfer#grant} when right-clicked. The per-player tick runs from
 * {@code AbilityRouter.serverTick}; static state is dropped by {@code ServerStateReset} through {@link #clearSessionState}.
 */
public final class SilverSurferMod {
	public static final EntityType<ThrownSurfboardEntity> THROWN_BOARD = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("thrown_surfboard")),
			EntityType.Builder.<ThrownSurfboardEntity>of(ThrownSurfboardEntity::new, MobCategory.MISC)
					.sized(1.1f, 0.3f).fireImmune().noSummon().noSave().clientTrackingRange(8).updateInterval(1).build("thrown_surfboard"));

	private SilverSurferMod() {
	}

	public static void initialize() {
		SilverSurferAttachments.init();
		SilverSurferDamage.initialize();
		PayloadTypeRegistry.playC2S().register(SilverSurferActionPayload.TYPE, SilverSurferActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SurferStatuePayload.TYPE, SurferStatuePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SilverSurferActionPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			if (!SilverSurfer.hasPower(p) || com.projecthero.mod.hero.PowerSuppression.refuse(p)) {
				return;
			}
			if (payload.action() == SilverSurferActionPayload.Action.TOGGLE_SURF) {
				// double-tap jump: the board flies up under his feet / drops away
				if (SilverSurfer.state(p).surfing || !p.onGround()) {
					SilverSurferFlight.toggle(p);
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(SilverSurferStatues::tick);
		ServerPlayerEvents.JOIN.register(SilverSurfer::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> SilverSurfer.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && SilverSurfer.hasPower(p)) {
				SilverSurfer.clearTransient(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
			if (SilverSurfer.hasPower(player)) {
				SilverSurfer.clearTransient(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (SilverSurfer.hasPower(handler.getPlayer())) {
				SilverSurfer.clearTransient(handler.getPlayer());
			}
		});
	}

	public static void clearSessionState() {
		SilverSurfer.clearSessionState();
	}
}
