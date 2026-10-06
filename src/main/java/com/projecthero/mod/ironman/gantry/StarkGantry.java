package com.projecthero.mod.ironman.gantry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.network.StarkGantryMenuPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * v0.15.4, explicit user request: the <b>Stark Gantry</b> -- the 5x5 piston floor that replaced the Suit Platform's
 * robotic-arm deploy / retrieve.
 * <ul>
 *   <li><b>Formation</b>: any complete 5x5 of {@link StarkGantryFloorBlock} tiles on one level. The gantry a player is
 *       on is the complete 5x5 containing the tile under their feet whose centre is nearest them ({@link #findCentre}).</li>
 *   <li><b>H on the floor</b> (Tony Stark, not mid suit-up): unsuited, a menu lists every suit racked on a Suit Platform
 *       within {@value #RANGE} blocks of the floor ({@link #suitsInRange}); suited, it offers "Remove armour", which
 *       racks the suit on the platform it came from, else the nearest one in range with room ({@link #removeTarget}).</li>
 *   <li><b>The sequence</b> is run by the centre tile ({@link StarkGantryFloorBlockEntity}) off {@link GantryTimeline}.</li>
 * </ul>
 * Everything here re-validates on the server; the client only ever asks.
 */
public final class StarkGantry {
	/** Suit Platforms within this many blocks of the floor's edge are listed. */
	public static final int RANGE = 20;
	/** The floor is 5 x 5: the centre and two tiles either way. */
	public static final int HALF = 2;

	/** The Suit Platform each player's current suit last came from (preferred when it is taken off again). */
	private static final Map<UUID, GlobalPos> ORIGIN = new ConcurrentHashMap<>();

	private StarkGantry() {
	}

	public static void clearSessionState() {
		ORIGIN.clear();
	}

	static void rememberOrigin(ServerPlayer player, BlockPos platform) {
		if (platform != null) {
			ORIGIN.put(player.getUUID(), GlobalPos.of(player.level().dimension(), platform));
		}
	}

	// ---------------- formation ----------------

	public static boolean isFloor(BlockGetter level, BlockPos pos) {
		return level.getBlockState(pos).getBlock() instanceof StarkGantryFloorBlock;
	}

	/** Is the 5x5 centred on {@code centre} all gantry floor? */
	public static boolean complete(BlockGetter level, BlockPos centre) {
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				if (!isFloor(level, centre.offset(dx, 0, dz))) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * The centre of the complete 5x5 containing {@code tile}, or null. With more than one candidate (a floor bigger than
	 * 5x5) the one whose centre is nearest {@code tile} wins, ties broken by x then z, so the answer is stable.
	 */
	public static BlockPos findCentre(BlockGetter level, BlockPos tile) {
		if (!isFloor(level, tile)) {
			return null;
		}
		BlockPos best = null;
		int bestD = Integer.MAX_VALUE;
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				int d = dx * dx + dz * dz;
				if (d >= bestD) {
					continue;
				}
				BlockPos c = tile.offset(dx, 0, dz);
				if (complete(level, c)) {
					best = c;
					bestD = d;
				}
			}
		}
		return best;
	}

	/** The gantry floor tile a player stands on (feet just above it, or raised on the lift), or null. */
	public static BlockPos tileUnder(Player player) {
		for (double drop : new double[] { 0.2, 0.75 }) {
			BlockPos p = BlockPos.containing(player.getX(), player.getY() - drop, player.getZ());
			if (isFloor(player.level(), p)) {
				return p;
			}
		}
		return null;
	}

	/** The centre of the complete gantry the player is standing on, or null. */
	public static BlockPos centreUnder(Player player) {
		BlockPos tile = tileUnder(player);
		return tile == null ? null : findCentre(player.level(), tile);
	}

	/** A running sequence on this gantry -- or on any gantry sharing a tile with it -- or null. */
	public static StarkGantryFloorBlockEntity runningOn(Level level, BlockPos centre) {
		for (int dx = -2 * HALF; dx <= 2 * HALF; dx++) {
			for (int dz = -2 * HALF; dz <= 2 * HALF; dz++) {
				if (level.getBlockEntity(centre.offset(dx, 0, dz)) instanceof StarkGantryFloorBlockEntity be && be.running()) {
					return be;
				}
			}
		}
		return null;
	}

	// ---------------- the Suit Platforms in range ----------------

	/** Distance from a Suit Platform to the nearest point of the 5x5 floor centred on {@code centre}. */
	public static double distanceToFloor(BlockPos centre, BlockPos platform) {
		double dx = Math.max(0, Math.abs(platform.getX() - centre.getX()) - HALF);
		double dz = Math.max(0, Math.abs(platform.getZ() - centre.getZ()) - HALF);
		double dy = platform.getY() - centre.getY();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	public static boolean inRange(BlockPos centre, BlockPos platform) {
		return distanceToFloor(centre, platform) <= RANGE;
	}

	/** Every loaded Suit Platform within range this player may use (theirs or unowned), nearest first. */
	public static List<IronManSuitPlatformBlockEntity> platformsInRange(ServerLevel level, BlockPos centre, UUID player) {
		List<IronManSuitPlatformBlockEntity> out = new ArrayList<>();
		int reach = RANGE + HALF;
		int cx0 = (centre.getX() - reach) >> 4;
		int cx1 = (centre.getX() + reach) >> 4;
		int cz0 = (centre.getZ() - reach) >> 4;
		int cz1 = (centre.getZ() + reach) >> 4;
		for (int cx = cx0; cx <= cx1; cx++) {
			for (int cz = cz0; cz <= cz1; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity e : chunk.getBlockEntities().values()) {
					if (e instanceof IronManSuitPlatformBlockEntity p && !p.isRemoved() && inRange(centre, p.getBlockPos())
							&& (player == null || p.owner().isEmpty() || p.owner().get().equals(player))) {
						out.add(p);
					}
				}
			}
		}
		out.sort(Comparator.comparingDouble(p -> distanceToFloor(centre, p.getBlockPos())));
		return out;
	}

	/** One menu entry per racked suit in range. */
	public static List<StarkGantryMenuPayload.Entry> suitsInRange(ServerLevel level, BlockPos centre, UUID player) {
		List<StarkGantryMenuPayload.Entry> out = new ArrayList<>();
		for (IronManSuitPlatformBlockEntity p : platformsInRange(level, centre, player)) {
			String suitId = p.storedSuitId();
			IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);
			if (suit == null) {
				continue;
			}
			float e = p.suitEnergy() / Math.max(1f, suit.energyCapacity());
			float i = p.suitIntegrity() / Math.max(1f, IronManEnergy.maxIntegrity(suitId));
			out.add(new StarkGantryMenuPayload.Entry(p.getBlockPos(), suitId, p.pieceMask(), Mth.clamp(e, 0f, 1f),
					Mth.clamp(i, 0f, 1f), (int) Math.round(distanceToFloor(centre, p.getBlockPos()))));
		}
		return out;
	}

	/** Rack-slot mask (bit i = rack slot i) of the pieces of {@code suitId} the player is wearing. */
	public static int wornMask(Player player, String suitId) {
		int mask = 0;
		for (int idx = 0; idx < 4; idx++) {
			if (player.getItemBySlot(StarkGantryFloorBlockEntity.equipSlotOf(idx)).getItem() instanceof IronManArmorItem a
					&& a.suitId().equals(suitId)) {
				mask |= 1 << idx;
			}
		}
		return mask;
	}

	/** Where a suit taken off on this gantry is racked: the platform it came from if it can, else the nearest with room. */
	public static IronManSuitPlatformBlockEntity removeTarget(ServerLevel level, BlockPos centre, ServerPlayer player, String suitId) {
		int mask = wornMask(player, suitId);
		GlobalPos origin = ORIGIN.get(player.getUUID());
		List<IronManSuitPlatformBlockEntity> all = platformsInRange(level, centre, player.getUUID());
		if (origin != null && origin.dimension() == level.dimension()) {
			for (IronManSuitPlatformBlockEntity p : all) {
				if (p.getBlockPos().equals(origin.pos()) && p.canAccept(suitId, mask)) {
					return p;
				}
			}
		}
		// a rack already holding part of this suit, then an empty one -- each nearest first
		for (IronManSuitPlatformBlockEntity p : all) {
			if (suitId.equals(p.storedSuitId()) && p.canAccept(suitId, mask)) {
				return p;
			}
		}
		for (IronManSuitPlatformBlockEntity p : all) {
			if (p.canAccept(suitId, mask)) {
				return p;
			}
		}
		return null;
	}

	/**
	 * Put one piece back on a rack: {@code preferred} first, then any platform in range that takes it and belongs to
	 * {@code owner} (or nobody) -- never someone else's rack.
	 */
	static boolean rackPiece(ServerLevel level, BlockPos centre, BlockPos preferred, UUID owner, ItemStack stack) {
		if (!(stack.getItem() instanceof IronManArmorItem piece)) {
			return false;
		}
		int bit = 1 << IronManSuitPlatformBlockEntity.slotOf(piece.getType());
		if (preferred != null && level.isLoaded(preferred)
				&& level.getBlockEntity(preferred) instanceof IronManSuitPlatformBlockEntity p
				&& p.canAccept(piece.suitId(), bit) && p.store(stack)) {
			return true;
		}
		for (IronManSuitPlatformBlockEntity p : platformsInRange(level, centre, owner)) {
			if (p.canAccept(piece.suitId(), bit) && p.store(stack)) {
				return true;
			}
		}
		return false;
	}

	// ---------------- H on the floor ----------------

	/** Why the player can't use the gantry right now (an action-bar message key), or null if they can. */
	private static String blocker(ServerPlayer player, BlockPos centre) {
		if (!TonyStark.hasPower(player)) {
			return "message.projecthero.gantry.not_stark";
		}
		if (centre == null) {
			return tileUnder(player) == null ? "message.projecthero.gantry.not_on_floor" : "message.projecthero.gantry.incomplete";
		}
		if (IronManSuitUpManager.inTransition(player) || player.isPassenger() || !player.isAlive()) {
			return "message.projecthero.gantry.busy_player";
		}
		if (runningOn(player.level(), centre) != null) {
			return "message.projecthero.gantry.busy";
		}
		return null;
	}

	private static void tell(ServerPlayer player, String key, ChatFormatting colour) {
		player.displayClientMessage(Component.translatable(key).withStyle(colour), true);
	}

	/** H pressed on the floor: send the menu (or say why not). */
	public static void openMenu(ServerPlayer player) {
		BlockPos centre = centreUnder(player);
		String why = blocker(player, centre);
		if (why != null) {
			tell(player, why, ChatFormatting.GOLD);
			return;
		}
		ServerLevel level = player.serverLevel();
		ServerPlayNetworking.send(player, menuFor(level, centre, player));
	}

	/** The menu as the server sees it now (public for the gametests). */
	public static StarkGantryMenuPayload menuFor(ServerLevel level, BlockPos centre, ServerPlayer player) {
		String worn = IronManArmor.wornSuitId(player);
		if (worn != null) {
			IronManSuitPlatformBlockEntity target = removeTarget(level, centre, player, worn);
			return new StarkGantryMenuPayload(centre, worn, target != null,
					target == null ? -1 : (int) Math.round(distanceToFloor(centre, target.getBlockPos())), List.of());
		}
		return new StarkGantryMenuPayload(centre, "", false, -1, suitsInRange(level, centre, player.getUUID()));
	}

	/** The player picked a suit racked at {@code platformPos}. Re-validated; true if the sequence started. */
	public static boolean beginEquip(ServerPlayer player, BlockPos platformPos) {
		BlockPos centre = centreUnder(player);
		String why = blocker(player, centre);
		if (why == null && IronManArmor.wearingAnyIronMan(player)) {
			why = "message.projecthero.gantry.already_suited";
		}
		ServerLevel level = player.serverLevel();
		IronManSuitPlatformBlockEntity platform = null;
		if (why == null) {
			for (IronManSuitPlatformBlockEntity p : platformsInRange(level, centre, player.getUUID())) {
				if (p.getBlockPos().equals(platformPos) && p.storedSuitId() != null && IronManSuits.byId(p.storedSuitId()) != null) {
					platform = p;
				}
			}
			if (platform == null) {
				why = "message.projecthero.gantry.suit_gone";
			}
		}
		if (why != null || !(level.getBlockEntity(centre) instanceof StarkGantryFloorBlockEntity be)) {
			tell(player, why == null ? "message.projecthero.gantry.incomplete" : why, ChatFormatting.GOLD);
			return false;
		}
		if (platform.getLevel() instanceof ServerLevel && platform.owner().isEmpty()) {
			platform.bindTo(player.getUUID());
		}
		be.beginEquip(player, platform);
		return true;
	}

	/** The player chose "Remove armour". Re-validated; true if the sequence started. */
	public static boolean beginUnequip(ServerPlayer player) {
		BlockPos centre = centreUnder(player);
		String why = blocker(player, centre);
		String worn = IronManArmor.wornSuitId(player);
		if (why == null && worn == null) {
			why = "message.projecthero.gantry.not_suited";
		}
		ServerLevel level = player.serverLevel();
		IronManSuitPlatformBlockEntity target = why == null ? removeTarget(level, centre, player, worn) : null;
		if (why == null && target == null) {
			why = "message.projecthero.gantry.no_room";
		}
		if (why != null || !(level.getBlockEntity(centre) instanceof StarkGantryFloorBlockEntity be)) {
			tell(player, why == null ? "message.projecthero.gantry.incomplete" : why, ChatFormatting.GOLD);
			return false;
		}
		be.beginUnequip(player, worn, target);
		return true;
	}

	// ---------------- breaking ----------------

	/** A floor tile is being broken: stop any sequence it belongs to; a broken centre drops what its floor still held. */
	static void floorRemoved(ServerLevel level, BlockPos pos) {
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				if (level.getBlockEntity(pos.offset(dx, 0, dz)) instanceof StarkGantryFloorBlockEntity be && be.running()) {
					ServerPlayer p = be.playerId() == null ? null : level.getServer().getPlayerList().getPlayer(be.playerId());
					if (p != null) {
						p.displayClientMessage(Component.translatable("message.projecthero.gantry.broken").withStyle(ChatFormatting.RED), true);
					}
					be.abort(p);
				}
			}
		}
		if (level.getBlockEntity(pos) instanceof StarkGantryFloorBlockEntity be) {
			be.flushBuffer(null);
			be.dropBuffer(level);
		}
	}
}
