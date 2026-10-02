package com.projecthero.mod.stormbreaker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.joml.Vector3f;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.network.BifrostActionPayload;
import com.projecthero.mod.network.BifrostScreenPayload;
import com.projecthero.mod.squad.Squads;
import com.projecthero.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Stormbreaker's Bifrost (v0.14.19, reworked v0.14.20).
 *
 * <p>Sneak + right-click with a worthy Stormbreaker opens the Bifrost screen ({@link BifrostScreenPayload}): type X / Y /
 * Z and travel, or use one of three saved waypoints ({@link BifrostWaypoints}, kept per player for good). The server
 * re-checks everything a travel request claims ({@link #travelTo}):
 * <ul>
 *   <li>a worthy Stormbreaker in either hand, alive and not a spectator;</li>
 *   <li>the Bifrost's own {@link #COOLDOWN_TICKS 60 s} cooldown -- separate from the vanilla item cooldown, so the throw
 *   is never held;</li>
 *   <li>inside the world border and the build height, in the player's own dimension (no limit on distance);</li>
 *   <li>a safe landing in that column ({@link #findSafeLanding}) -- else nothing happens and no cooldown is spent.</li>
 * </ul>
 * The rainbow bridge takes the user and every squadmate ({@link Squads#areAllies}) within {@link #CARRY_RADIUS} blocks
 * (a squadmate can sneak to stay behind), each keeping roughly their offset and landing safely.
 */
public final class Bifrost {
	public static final double CARRY_RADIUS = 8.0;
	public static final int COOLDOWN_TICKS = 60 * 20;
	/** A squadmate keeps their offset only if their own safe spot is within this many blocks up/down of the user's. */
	private static final int ALLY_MAX_STEP = 6;
	private static final double COLUMN_HEIGHT = 22.0;

	/** Red, orange, yellow, green, blue, indigo, violet -- the bridge's colours bottom to top. */
	private static final Vector3f[] RAINBOW = {
			new Vector3f(1.00f, 0.15f, 0.15f), new Vector3f(1.00f, 0.55f, 0.10f), new Vector3f(1.00f, 0.95f, 0.20f),
			new Vector3f(0.20f, 0.95f, 0.30f), new Vector3f(0.20f, 0.55f, 1.00f), new Vector3f(0.35f, 0.25f, 0.90f),
			new Vector3f(0.70f, 0.30f, 1.00f),
	};

	/** Why a travel request did or did not go; each failure has a {@code message.projecthero.bifrost.fail.*} line. */
	public enum Result {
		OK, NOT_HOLDING, COOLDOWN, WRONG_DIMENSION, OUT_OF_BOUNDS, NO_LANDING, EMPTY_WAYPOINT;

		public String messageKey() {
			return "message.projecthero.bifrost.fail." + name().toLowerCase(Locale.ROOT);
		}
	}

	private Bifrost() {
	}

	// ---------------------------------------------------------------- holding / cooldown / waypoints

	/** A worthy player with Stormbreaker in either hand -- the only person whose Bifrost requests are honoured. */
	public static boolean canUse(Player player) {
		return player.isAlive() && !player.isSpectator() && Worthiness.isWorthy(player)
				&& (player.getMainHandItem().is(ModItems.STORMBREAKER) || player.getOffhandItem().is(ModItems.STORMBREAKER));
	}

	/**
	 * Whether the crosshair is on a block within the player's normal interaction reach. Sneak + right-clicking such a
	 * block with something in the off hand leaves the click to vanilla (so you can still sneak-place a block from your
	 * off hand while holding the axe); otherwise the Bifrost screen opens.
	 */
	public static boolean targetsBlockInReach(Player player) {
		return player.pick(player.blockInteractionRange(), 1.0f, false).getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
	}

	private static long now(ServerPlayer player) {
		return player.server.overworld().getGameTime();
	}

	/** Ticks until the Bifrost is ready again (0 = ready). */
	public static int cooldownRemaining(ServerPlayer player) {
		long readyAt = player.getAttachedOrElse(ModAttachments.BIFROST_READY_AT, 0L);
		return (int) Mth.clamp(readyAt - now(player), 0L, COOLDOWN_TICKS);
	}

	public static void resetCooldownForTests(ServerPlayer player) {
		player.setAttached(ModAttachments.BIFROST_READY_AT, 0L);
	}

	public static BifrostWaypoints waypoints(Player player) {
		return player.getAttachedOrElse(ModAttachments.BIFROST_WAYPOINTS, BifrostWaypoints.EMPTY);
	}

	/** Saves the player's current block position (and dimension) into {@code slot}. */
	public static boolean saveWaypoint(ServerPlayer player, int slot, String name) {
		if (slot < 0 || slot >= BifrostWaypoints.SLOTS) {
			return false;
		}
		BlockPos at = player.blockPosition();
		BifrostWaypoints.Waypoint waypoint = new BifrostWaypoints.Waypoint(BifrostWaypoints.cleanName(name, slot),
				at.getX(), at.getY(), at.getZ(), player.level().dimension().location().toString());
		player.setAttached(ModAttachments.BIFROST_WAYPOINTS, waypoints(player).with(slot, waypoint));
		return true;
	}

	public static void clearWaypoint(ServerPlayer player, int slot) {
		player.setAttached(ModAttachments.BIFROST_WAYPOINTS, waypoints(player).with(slot, BifrostWaypoints.Waypoint.EMPTY));
	}

	// ---------------------------------------------------------------- screen + packets

	/** Opens (or, with {@code open} false, refreshes) the player's Bifrost screen. */
	public static void sendScreen(ServerPlayer player, boolean open) {
		if (!ServerPlayNetworking.canSend(player, BifrostScreenPayload.TYPE)) {
			return; // gametest mock players, or a client without the mod
		}
		ServerPlayNetworking.send(player, new BifrostScreenPayload(open, waypoints(player), cooldownRemaining(player),
				player.level().dimension().location().toString()));
	}

	/** Server side of every Bifrost screen button. Silently ignores anyone not holding a worthy Stormbreaker. */
	public static void handleAction(ServerPlayer player, BifrostActionPayload payload) {
		if (player == null || !canUse(player)) {
			return;
		}
		switch (payload.action()) {
			case BifrostActionPayload.SAVE -> {
				if (saveWaypoint(player, payload.slot(), payload.name())) {
					player.displayClientMessage(Component.translatable("message.projecthero.bifrost.saved",
							waypoints(player).get(payload.slot()).name()), true);
				}
				sendScreen(player, false);
			}
			case BifrostActionPayload.CLEAR -> {
				clearWaypoint(player, payload.slot());
				sendScreen(player, false);
			}
			case BifrostActionPayload.TRAVEL_COORDS -> report(player, travelTo(player, payload.x(), payload.y(), payload.z()));
			case BifrostActionPayload.TRAVEL_WAYPOINT -> report(player, travelToWaypoint(player, payload.slot()));
			default -> {
			}
		}
	}

	private static void report(ServerPlayer player, Result result) {
		if (result == Result.OK) {
			return;
		}
		if (result == Result.COOLDOWN) {
			player.displayClientMessage(Component.translatable(result.messageKey(),
					(cooldownRemaining(player) + 19) / 20), true);
		} else {
			player.displayClientMessage(Component.translatable(result.messageKey()), true);
		}
		player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.4f);
		sendScreen(player, false);
	}

	// ---------------------------------------------------------------- travel

	public static Result travelToWaypoint(ServerPlayer player, int slot) {
		BifrostWaypoints.Waypoint waypoint = waypoints(player).get(slot);
		if (waypoint.isEmpty()) {
			return Result.EMPTY_WAYPOINT;
		}
		if (!waypoint.dimension().equals(player.level().dimension().location().toString())) {
			return canUse(player) ? Result.WRONG_DIMENSION : Result.NOT_HOLDING;
		}
		return travelTo(player, waypoint.x(), waypoint.y(), waypoint.z());
	}

	/** Validates and, if everything checks out, opens the Bifrost to {@code x y z} in the player's own dimension. */
	public static Result travelTo(ServerPlayer player, int x, int y, int z) {
		if (!canUse(player)) {
			return Result.NOT_HOLDING;
		}
		if (cooldownRemaining(player) > 0) {
			return Result.COOLDOWN;
		}
		ServerLevel level = player.serverLevel();
		if (!inBounds(level, x, y, z)) {
			return Result.OUT_OF_BOUNDS;
		}
		loadChunk(level, x, z, player);
		BlockPos landing = findSafeLanding(level, x, y, z);
		if (landing == null) {
			return Result.NO_LANDING;
		}

		Vec3 from = player.position();
		Vec3 to = Vec3.atBottomCenterOf(landing);
		List<ServerPlayer> allies = alliesInReach(player);

		level.playSound(null, from.x, from.y, from.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.6f);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5f, 1.3f);

		send(player, to);
		int carried = 0;
		for (ServerPlayer ally : allies) {
			Vec3 offset = ally.position().subtract(from);
			Vec3 dest = to;
			int ax = landing.getX() + Mth.floor(offset.x + 0.5);
			int az = landing.getZ() + Mth.floor(offset.z + 0.5);
			if (inBounds(level, ax, landing.getY(), az)) {
				loadChunk(level, ax, az, ally);
				BlockPos own = findSafeLanding(level, ax, landing.getY() + Mth.floor(offset.y + 0.5), az);
				if (own != null && Math.abs(own.getY() - landing.getY()) <= ALLY_MAX_STEP) {
					dest = Vec3.atBottomCenterOf(own);
				}
			}
			send(ally, dest);
			ally.displayClientMessage(Component.translatable("message.projecthero.bifrost.carried", player.getDisplayName()), true);
			carried++;
		}

		level.playSound(null, to.x, to.y, to.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.3f);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6f, 1.1f);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.5f, 1.2f);

		player.setAttached(ModAttachments.BIFROST_READY_AT, now(player) + COOLDOWN_TICKS);
		player.displayClientMessage(carried > 0
				? Component.translatable("message.projecthero.bifrost.travelled_with", carried, COOLDOWN_TICKS / 20)
				: Component.translatable("message.projecthero.bifrost.travelled", COOLDOWN_TICKS / 20), true);
		return Result.OK;
	}

	/** Squadmates within {@link #CARRY_RADIUS} of the user, in the same level, alive, and not sneaking to stay behind. */
	public static List<ServerPlayer> alliesInReach(ServerPlayer player) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer other : player.serverLevel().players()) {
			if (other != player && other.isAlive() && !other.isSpectator() && !other.isShiftKeyDown()
					&& other.distanceToSqr(player) <= CARRY_RADIUS * CARRY_RADIUS && Squads.areAllies(player, other)) {
				out.add(other);
			}
		}
		return out;
	}

	/** One rider across: dismount, rainbow at both ends, no momentum or fall distance carried over. */
	private static void send(ServerPlayer rider, Vec3 dest) {
		ServerLevel level = rider.serverLevel();
		column(level, rider.position());
		if (rider.isPassenger()) {
			rider.stopRiding();
		}
		if (rider.isVehicle()) {
			rider.ejectPassengers();
		}
		rider.teleportTo(level, dest.x, dest.y, dest.z, Set.of(), rider.getYRot(), rider.getXRot());
		rider.setDeltaMovement(Vec3.ZERO);
		rider.hurtMarked = true;
		rider.resetFallDistance();
		column(level, dest);
	}

	static boolean inBounds(Level level, int x, int y, int z) {
		return y >= level.getMinBuildHeight() && y < level.getMaxBuildHeight()
				&& level.getWorldBorder().isWithinBounds(new BlockPos(x, y, z));
	}

	/** Loads (generating if need be) the destination chunk and keeps it loaded while the riders arrive. */
	private static void loadChunk(ServerLevel level, int x, int z, ServerPlayer holder) {
		ChunkPos chunk = new ChunkPos(new BlockPos(x, 0, z));
		level.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunk, 1, holder.getId());
		level.getChunk(chunk.x, chunk.z);
	}

	// ---------------------------------------------------------------- safe landing

	/**
	 * A safe spot in column {@code x, z} near {@code y}: two free blocks to stand in (no lava, water, fire or solid
	 * blocks) above a solid floor (or a water surface to swim on), never magma, never the void. If {@code y} is open air
	 * the search drops straight down to the first floor; otherwise (or if the drop finds nothing) it climbs up to the
	 * first gap. The column never changes. Null when the column has nowhere safe at all.
	 */
	public static BlockPos findSafeLanding(Level level, int x, int y, int z) {
		int min = level.getMinBuildHeight() + 1;
		int max = level.getMaxBuildHeight() - 2;
		int start = Mth.clamp(y, min, max);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, start, z);
		for (int yy = start; yy >= min; yy--) {
			p.setY(yy);
			if (isStandable(level, p)) {
				return p.immutable();
			}
			if (!isPassable(level, p)) {
				break;
			}
		}
		for (int yy = start + 1; yy <= max; yy++) {
			p.setY(yy);
			if (isStandable(level, p)) {
				return p.immutable();
			}
		}
		return null;
	}

	public static boolean isStandable(Level level, BlockPos feet) {
		if (!level.isInWorldBounds(feet) || !level.isInWorldBounds(feet.above())) {
			return false;
		}
		if (!isPassable(level, feet) || !isPassable(level, feet.above())) {
			return false;
		}
		BlockPos floor = feet.below();
		BlockState below = level.getBlockState(floor);
		if (level.getFluidState(floor).is(FluidTags.WATER) && below.getCollisionShape(level, floor).isEmpty()) {
			return true; // a water surface: you swim, you don't fall
		}
		return !below.getCollisionShape(level, floor).isEmpty()
				&& !level.getFluidState(floor).is(FluidTags.LAVA)
				&& !below.is(Blocks.MAGMA_BLOCK) && !below.is(BlockTags.CAMPFIRES) && !below.is(Blocks.CACTUS)
				&& !below.is(Blocks.POWDER_SNOW);
	}

	/** Free to stand in: no collision, no fluid, no fire, no powder snow, no berry bush. */
	private static boolean isPassable(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty()
				&& level.getFluidState(pos).isEmpty()
				&& !state.is(BlockTags.FIRE) && !state.is(Blocks.POWDER_SNOW) && !state.is(Blocks.SWEET_BERRY_BUSH)
				&& !state.is(Blocks.COBWEB);
	}

	/** The rainbow bridge: a tall column of dust cycling red to violet, with a white sparkle core. */
	private static void column(ServerLevel level, Vec3 base) {
		int steps = (int) (COLUMN_HEIGHT / 0.5);
		for (int i = 0; i < steps; i++) {
			double y = base.y + i * 0.5;
			Vector3f colour = RAINBOW[(i / 3) % RAINBOW.length];
			level.sendParticles(new DustParticleOptions(colour, 2.0f), base.x, y, base.z, 3, 0.35, 0.15, 0.35, 0.0);
		}
		level.sendParticles(ParticleTypes.END_ROD, base.x, base.y + COLUMN_HEIGHT * 0.5, base.z,
				40, 0.25, COLUMN_HEIGHT * 0.3, 0.25, 0.02);
		level.sendParticles(ParticleTypes.FLASH, base.x, base.y + 1.0, base.z, 1, 0.0, 0.0, 0.0, 0.0);
	}
}
