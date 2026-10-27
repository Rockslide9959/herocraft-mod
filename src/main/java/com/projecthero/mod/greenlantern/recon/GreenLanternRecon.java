package com.projecthero.mod.greenlantern.recon;

import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.greenlantern.GreenLanternAbilityManager;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.network.GreenLanternGatewayPayload;
import com.projecthero.mod.network.GreenLanternReconPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * v0.17.1: the two "ring tech" constructs ({@link ConstructType.Kind#RING_TECH}) -- the Hard-Light Drone
 * ({@link GreenLanternDrone}) and the Hard-Light Gateway with the ring's waypoints ({@link GreenLanternGateway}). This is
 * their one door into the rest of the Green Lantern code: registration, the key presses (a key and its Shift twin, see
 * {@code GreenLanternBinding#isPair}), the per-player tick, N / death / logout clean-up and the server-stop reset.
 */
public final class GreenLanternRecon {
	public static final EntityType<HardLightDroneEntity> DRONE_TYPE = register("hard_light_drone",
			EntityType.Builder.<HardLightDroneEntity>of(HardLightDroneEntity::new, MobCategory.MISC)
					.sized(0.7f, 0.5f).fireImmune().noSave().noSummon().clientTrackingRange(8).updateInterval(1)
					.build("hard_light_drone"));
	public static final EntityType<HardLightGatewayEntity> GATEWAY_TYPE = register("hard_light_gateway",
			EntityType.Builder.<HardLightGatewayEntity>of(HardLightGatewayEntity::new, MobCategory.MISC)
					.sized(2.4f, 3.4f).fireImmune().noSave().noSummon().clientTrackingRange(10).updateInterval(2)
					.build("hard_light_gateway"));

	private GreenLanternRecon() {
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}

	public static void initialize() {
		PayloadTypeRegistry.playS2C().register(GreenLanternReconPayload.TYPE, GreenLanternReconPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(GreenLanternGatewayPayload.TYPE, GreenLanternGatewayPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(GreenLanternGatewayPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			if (GreenLanternAbilityManager.hasContext(p)) {
				GreenLanternGateway.handle(p, payload);
			}
		});
		// taking damage while the gateway is being channelled breaks it off (no cost, no cooldown)
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer p && taken > 0f) {
				GreenLanternGateway.onDamaged(p);
			}
		});
	}

	/** Server stop ({@code ServerStateReset} through {@code GreenLanternAbilityManager#clearSessionState}). */
	public static void clearSessionState() {
		GreenLanternDrone.clearSessionState();
		GreenLanternGateway.clearSessionState();
	}

	/** A Drone / Gateway key: {@code second} = Shift + the key while both keys of the pair hold it. */
	public static void press(ServerPlayer p, ConstructType type, boolean second) {
		if (com.projecthero.mod.hero.PowerSuppression.isSuppressed(p)) {
			return;
		}
		if (type == ConstructType.HARD_LIGHT_DRONE) {
			if (second) {
				GreenLanternDrone.scout(p);
			} else {
				GreenLanternDrone.toggle(p);
			}
		} else if (type == ConstructType.HARD_LIGHT_GATEWAY) {
			if (second) {
				GreenLanternGateway.openWaypoints(p);
			} else {
				GreenLanternGateway.openPicker(p);
			}
		}
	}

	/** Per player per tick (from {@code GreenLanternArsenal#tick}): the drone's upkeep, the gateway channel. */
	public static void tick(ServerPlayer p) {
		GreenLanternDrone.tick(p);
		GreenLanternGateway.tick(p);
	}

	/** N / death / logout / dimension change / power loss: the drone dissolves, a channel breaks off, open doors fold away. */
	public static void dismissAll(UUID owner) {
		GreenLanternDrone.dismiss(owner, false);
		GreenLanternGateway.dismiss(owner);
	}
}
