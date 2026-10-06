package com.projecthero.mod.ironman.drone;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.network.IronManDroneDeployPayload;
import com.projecthero.mod.network.IronManDroneInputPayload;
import com.projecthero.mod.network.IronManDroneLinkPayload;
import com.projecthero.mod.network.IronManSuitListPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.29 Remote Pilot (Mark 42 style): deploy one of your suits as a drone from the Call Armour picker's PILOT button
 * and fly it from where you stand. Server side: the entity type, the three payloads, deploy validation, the link
 * bookkeeping and its clean-up hooks. The drone itself is {@link IronManDroneEntity}; the camera / input / HUD side is
 * the client's {@code IronManDroneClient}.
 *
 * <p>{@link #DRONES} (owner -> their deployed drone) is static scratch state: cleared on {@code SERVER_STOPPED} through
 * {@code ServerStateReset} and per player on disconnect (the drone's pieces go back to its platform or drop).
 */
public final class IronManDrones {
	/** Beyond this distance from the pilot the link drops and the suit flies home. */
	public static final double MAX_RANGE = 96.0;
	/** The HUD starts warning from here. */
	public static final double WARN_RANGE = 80.0;

	public static final EntityType<IronManDroneEntity> DRONE = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("iron_man_drone")),
			EntityType.Builder.<IronManDroneEntity>of(IronManDroneEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.9f)
					.eyeHeight(1.62f)
					.fireImmune()
					.clientTrackingRange(10) // 160 blocks: comfortably past MAX_RANGE
					.updateInterval(1)
					.build("iron_man_drone"));

	/** Owner -> their deployed drone (linked or flying home). One per player. */
	private static final Map<UUID, IronManDroneEntity> DRONES = new ConcurrentHashMap<>();

	private IronManDrones() {
	}

	public static void initialize() {
		PayloadTypeRegistry.playC2S().register(IronManDroneDeployPayload.TYPE, IronManDroneDeployPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(IronManDroneInputPayload.TYPE, IronManDroneInputPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(IronManDroneLinkPayload.TYPE, IronManDroneLinkPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(IronManDroneDeployPayload.TYPE, (payload, context) ->
				context.server().execute(() -> deploy(context.player(), payload.suitId(), payload.source())));
		ServerPlayNetworking.registerGlobalReceiver(IronManDroneInputPayload.TYPE, (payload, context) ->
				context.server().execute(() -> onInput(context.player(), payload)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onDisconnect(handler.getPlayer()));
		// any damage to the pilot's real body breaks their concentration -> the link drops
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseAmount, dealtAmount, blocked) -> {
			if (entity instanceof ServerPlayer sp && !blocked && baseAmount > 0f && linkedDrone(sp) != null) {
				endLink(sp, "message.projecthero.ironman.drone.hurt");
			}
		});
	}

	/** {@code ServerStateReset}: drop every reference (the entities themselves are saved with their chunks). */
	public static void clearSessionState() {
		DRONES.clear();
	}

	// ------------------------------------------------------------------ queries

	/**
	 * The link range actually enforced: {@link #MAX_RANGE}, shortened on servers with a small view / simulation distance
	 * so the drone never leaves the area where its pilot's client can see it and the server still ticks it.
	 */
	public static double effectiveRange(ServerLevel level) {
		var list = level.getServer().getPlayerList();
		int chunks = Math.min(list.getViewDistance(), list.getSimulationDistance());
		return Math.max(32.0, Math.min(MAX_RANGE, (chunks - 1) * 16.0 - 2.0));
	}

	/** The player's deployed drone (linked or returning), or null. */
	public static IronManDroneEntity droneOf(ServerPlayer player) {
		IronManDroneEntity d = DRONES.get(player.getUUID());
		if (d != null && d.isRemoved()) {
			DRONES.remove(player.getUUID(), d);
			return null;
		}
		return d;
	}

	/** The drone the player is piloting right now, or null. */
	public static IronManDroneEntity linkedDrone(ServerPlayer player) {
		IronManDroneEntity d = droneOf(player);
		return d != null && d.isPiloted() ? d : null;
	}

	static void track(IronManDroneEntity d) {
		if (d.ownerId() != null) {
			DRONES.putIfAbsent(d.ownerId(), d);
		}
	}

	static void forget(IronManDroneEntity d) {
		if (d.ownerId() != null) {
			DRONES.remove(d.ownerId(), d);
		}
	}

	// ------------------------------------------------------------------ deploy

	/** True if {@code suitId} can be remote-piloted from {@code source} (only full sets in the pack or on a platform). */
	public static boolean pilotableSource(int source) {
		return source == IronManSuitListPayload.SOURCE_INVENTORY || source == IronManSuitListPayload.SOURCE_PLATFORM;
	}

	/**
	 * Deploy {@code suitId} as a remote drone and open the link. Full re-validation: Tony Stark, not wearing a suit, no
	 * suit-up running, no drone already out, the complete set in the pack (or on one of the player's loaded platforms in
	 * range) and charge above zero. The real stacks move into the drone -- they exist nowhere else while it is out.
	 * Returns the drone, or null (with a message) if refused.
	 */
	public static IronManDroneEntity deploy(ServerPlayer player, String suitId, int source) {
		IronManSuit suit = IronManSuits.byId(suitId);
		if (suit == null || !TonyStark.hasPower(player) || !pilotableSource(source)) {
			return null;
		}
		if (IronManArmor.wearingAnyIronMan(player) || IronManSuitUpManager.inTransition(player)) {
			fail(player, "message.projecthero.ironman.drone.wearing", suit);
			return null;
		}
		if (droneOf(player) != null) {
			fail(player, "message.projecthero.ironman.drone.already", suit);
			return null;
		}
		if (source == IronManSuitListPayload.SOURCE_PLATFORM && !com.projecthero.mod.ironman.gear.StarkGear.canCall(player)) {
			com.projecthero.mod.ironman.gear.StarkGear.refuseCall(player); // v0.15.1: a platform deploy is a call
			return null;
		}
		ServerLevel level = player.serverLevel();
		ItemStack[] pieces = new ItemStack[4];
		GlobalPos home = null;
		Vec3 spawn;
		float yaw = player.getYRot();
		if (source == IronManSuitListPayload.SOURCE_INVENTORY) {
			int[] idx = new int[4];
			for (int i = 0; i < 4; i++) {
				idx[i] = inventoryIndex(player, suitId, IronManDroneEntity.TYPES[i]);
				if (idx[i] < 0) {
					fail(player, "message.projecthero.ironman.drone.not_full", suit);
					return null;
				}
			}
			ItemStack chest = player.getInventory().getItem(idx[1]);
			if (IronManEnergy.stackEnergy(chest, suitId) <= 0f) {
				fail(player, "message.projecthero.ironman.drone.no_energy", suit);
				return null;
			}
			for (int i = 0; i < 4; i++) {
				pieces[i] = player.getInventory().removeItem(idx[i], 1);
			}
			Vec3 look = player.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0, look.z);
			flat = flat.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, yaw) : flat.normalize();
			spawn = player.position().add(flat.scale(1.5));
			if (!level.noCollision(DRONE.getDimensions().makeBoundingBox(spawn))) {
				spawn = player.position();
			}
		} else {
			IronManSuitPlatformBlockEntity be = platformHoldingAll(level, player, suitId);
			if (be == null) {
				fail(player, "message.projecthero.ironman.drone.not_full", suit);
				return null;
			}
			if (be.suitEnergy() <= 0f) {
				fail(player, "message.projecthero.ironman.drone.no_energy", suit);
				return null;
			}
			if (be.owner().isEmpty()) {
				be.bindTo(player.getUUID());
			}
			for (int i = 0; i < 4; i++) {
				pieces[i] = be.takePieceStack(suitId, IronManDroneEntity.TYPES[i]);
			}
			home = GlobalPos.of(level.dimension(), be.getBlockPos());
			spawn = Vec3.atBottomCenterOf(be.getBlockPos()).add(0, 0.25, 0);
		}
		ItemStack ref = pieces[1];
		float energy = IronManEnergy.stackEnergy(ref, suitId);
		float integrity = IronManEnergy.stackIntegrity(ref, suitId);
		IronManDroneEntity drone = IronManDroneEntity.create(level, player, suitId, pieces, energy, integrity, home, spawn, yaw);
		level.addFreshEntity(drone);
		DRONES.put(player.getUUID(), drone);
		player.inventoryMenu.broadcastChanges();
		sendLink(player, drone.getId(), true);
		IronManSounds.play(drone, IronManSounds.POWER_UP, 1.0f, 1.0f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, spawn.x, spawn.y + 1.0, spawn.z, 14, 0.3, 0.6, 0.3, 0.06);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.drone.linked",
				Component.translatable(suit.nameKey())).withStyle(ChatFormatting.AQUA), true);
		return drone;
	}

	private static void fail(ServerPlayer player, String key, IronManSuit suit) {
		player.displayClientMessage(Component.translatable(key, Component.translatable(suit.nameKey()))
				.withStyle(ChatFormatting.RED), true);
	}

	private static int inventoryIndex(ServerPlayer player, String suitId, ArmorItem.Type type) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.items.size(); i++) {
			ItemStack s = inv.items.get(i);
			if (s.getItem() instanceof IronManArmorItem p && p.suitId().equals(suitId) && p.getType() == type) {
				return i;
			}
		}
		return -1;
	}

	/** The nearest loaded platform of the player's (or unbound) holding the whole set, within link range. */
	private static IronManSuitPlatformBlockEntity platformHoldingAll(ServerLevel level, ServerPlayer player, String suitId) {
		BlockPos here = player.blockPosition();
		int cx = here.getX() >> 4;
		int cz = here.getZ() >> 4;
		IronManSuitPlatformBlockEntity best = null;
		double bestSq = (MAX_RANGE - 8) * (MAX_RANGE - 8);
		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				var chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
				if (chunk == null) {
					continue;
				}
				for (var e : chunk.getBlockEntities().entrySet()) {
					if (!(e.getValue() instanceof IronManSuitPlatformBlockEntity p) || p.sequenceRunning()
							|| (p.owner().isPresent() && !p.owner().get().equals(player.getUUID()))) {
						continue;
					}
					boolean all = true;
					for (ArmorItem.Type t : IronManDroneEntity.TYPES) {
						all &= p.holds(suitId, t);
					}
					double d = e.getKey().distSqr(here);
					if (all && d < bestSq) {
						bestSq = d;
						best = p;
					}
				}
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ link

	private static void onInput(ServerPlayer player, IronManDroneInputPayload in) {
		IronManDroneEntity d = linkedDrone(player);
		if (d != null) {
			d.acceptInput(in);
		} else if ((in.flags() & IronManDroneInputPayload.END) != 0 || droneOf(player) == null) {
			sendLink(player, -1, false); // the client thinks it is still linked: put its camera back
		}
	}

	/** Close the link (C, damage, out of range): the drone stops taking input and flies home. */
	public static void endLink(ServerPlayer player, String messageKey) {
		IronManDroneEntity d = linkedDrone(player);
		if (d == null) {
			return;
		}
		d.beginReturn();
		sendLink(player, d.getId(), false);
		if (messageKey != null) {
			IronManSuit suit = d.suit();
			player.displayClientMessage(Component.translatable(messageKey,
					suit != null ? Component.translatable(suit.nameKey()) : Component.literal(d.suitId()))
					.withStyle(ChatFormatting.GOLD), true);
		}
	}

	/** The drone is about to vanish (destroyed / out of power): just put the pilot's camera back. */
	static void closeLinkSilently(ServerPlayer player, IronManDroneEntity d) {
		sendLink(player, d.getId(), false);
	}

	private static void sendLink(ServerPlayer player, int id, boolean active) {
		if (ServerPlayNetworking.canSend(player, IronManDroneLinkPayload.TYPE)) {
			ServerPlayNetworking.send(player, new IronManDroneLinkPayload(id, active,
					(int) Math.round(effectiveRange(player.serverLevel()))));
		}
	}

	/** Owner logging out: the suit goes back to its platform (or drops) right now -- nothing waits for them. */
	public static void onDisconnect(ServerPlayer player) {
		IronManDroneEntity d = DRONES.remove(player.getUUID());
		if (d != null && !d.isRemoved()) {
			d.ownerGone();
		}
	}
}
