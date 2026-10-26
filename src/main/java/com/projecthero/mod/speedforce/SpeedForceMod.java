package com.projecthero.mod.speedforce;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.speedforce.network.SpeedForceFxPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * v0.17.1: one entry point for the whole Speed Force Speedster, called once from {@code ProjectHeroMod.onInitialize}: the
 * attachment, the Tachyon Device, the damage rules, the payload and the lifecycle hooks. The per-player tick runs from
 * {@code AbilityRouter.serverTick} (it also watches plain Speedsters for the awakening); static state is dropped by
 * {@code ServerStateReset} through {@link #clearSessionState}.
 */
public final class SpeedForceMod {
	public static Item TACHYON_DEVICE;

	private SpeedForceMod() {
	}

	public static void initialize() {
		SpeedForceAttachments.init();
		TACHYON_DEVICE = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("tachyon_device"),
				new TachyonDeviceItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
		SpeedForceDamage.initialize();
		PayloadTypeRegistry.playS2C().register(SpeedForceFxPayload.TYPE, SpeedForceFxPayload.CODEC);
		ServerPlayerEvents.JOIN.register(SpeedForceSpeedster::onPlayerJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> SpeedForceSpeedster.onPlayerRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && SpeedForceSpeedster.hasPower(p)) {
				SpeedForceSpeedster.clearTransient(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
			if (SpeedForceSpeedster.hasPower(player)) {
				SpeedForceSpeedster.clearTransient(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (SpeedForceSpeedster.hasPower(handler.getPlayer())) {
				SpeedForceSpeedster.clearTransient(handler.getPlayer());
			}
		});
	}

	public static void clearSessionState() {
		SpeedForceSpeedster.clearSessionState();
	}
}
