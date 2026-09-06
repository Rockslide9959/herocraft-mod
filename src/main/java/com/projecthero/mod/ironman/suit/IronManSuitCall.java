package com.projecthero.mod.ironman.suit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.data.StarkPlatformRegistry;
import com.projecthero.mod.ironman.data.StarkSuitReturnQueue;
import com.projecthero.mod.ironman.entity.IronManSuitPartEntity;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.network.IronManSuitListPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The "call armour" flow behind the {@code C} key when no suit is worn (spec "changes 9").
 *
 * <p>Pressing the key asks the server for {@link #openMenu} a list of every suit the player can
 * actually assemble right now -- one that is fully in their inventory, or one bound to a Suit Platform
 * of theirs in this dimension (even in an unloaded chunk). The client shows a picker; the choice comes
 * back to {@link #execute}.
 *
 * <h2>Why this can't fail the way the old path did</h2>
 * <ul>
 *   <li>Only genuinely-assemblable suits are ever listed, so "Mark L is not developed" can't appear.</li>
 *   <li>A platform in an unloaded chunk is force-loaded once (in this packet-handler context, never
 *       from a chunk-load callback -- see {@link com.projecthero.mod.hammer.MjolnirRegistry}) so the
 *       block entity itself removes the pieces; if the block turns out to be gone, the stale registry
 *       entry is dropped and the player is told, nothing is duplicated.</li>
 *   <li>Cross-dimension platforms are filtered out up front.</li>
 *   <li>Couriers always launch near the player (like a Mjolnir recall from far away), so a courier is
 *       never stranded in an unloaded chunk; if the player logs out / changes dimension mid-flight the
 *       courier drops the piece safely and never destroys it.</li>
 *   <li>A second call for a suit already inbound is ignored.</li>
 * </ul>
 */
public final class IronManSuitCall {
	/**
	 * Fly-in / assemble order ("changes 10"): chestplate first (it carries the reactor and reads as
	 * the core of the suit), then boots, then leggings, then helmet last. Used as the courier launch
	 * order; every other use here is order-independent.
	 */
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.CHESTPLATE, ArmorItem.Type.BOOTS, ArmorItem.Type.LEGGINGS, ArmorItem.Type.HELMET };

	/** Ticks between one courier launching and the next, so pieces arrive one at a time. */
	private static final int LAUNCH_STAGGER = 16;

	private IronManSuitCall() {
	}

	// ---------------- open the picker ----------------

	public static void openMenu(ServerPlayer player) {
		if (!TonyStark.hasPower(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.armor_rejects")
					.withStyle(ChatFormatting.RED), true);
			return;
		}
		if (IronManArmor.wearingAnyIronMan(player)) {
			return; // wearing a suit -> C is suit-down, handled elsewhere
		}
		List<IronManSuitListPayload.Option> options = gather(player);
		ServerPlayNetworking.send(player, new IronManSuitListPayload(options));
	}

	/**
	 * "changes 17": every suit the player could assemble right now (fully in inventory, or on a bound
	 * Suit Platform of theirs in this dimension). Exposed for Protocol Phoenix's emergency suit
	 * selection so it reuses the exact same "is this suit reachable" logic as the C-key picker.
	 */
	public static List<IronManSuitListPayload.Option> assemblableSuits(ServerPlayer player) {
		return gather(player);
	}

	/** "changes 17": true while an armour recall is still counting down toward this player from an unloaded platform. */
	public static boolean hasPendingCall(ServerPlayer player) {
		return PENDING.containsKey(player.getUUID());
	}

	private static List<IronManSuitListPayload.Option> gather(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		BlockPos here = player.blockPosition();
		StarkPlatformRegistry registry = StarkPlatformRegistry.get(level);

		List<IronManSuitListPayload.Option> out = new ArrayList<>();
		// "changes 17": list the suits in progression order (Mark I, II, III, ...) rather than the order
		// the marks happened to be added to the registry.
		List<IronManSuit> suits = new ArrayList<>(IronManSuits.all());
		suits.sort(java.util.Comparator.comparingInt(IronManSuit::markNumber));
		for (IronManSuit suit : suits) {
			String suitId = suit.id();

			// fully in the inventory?
			int inInv = 0;
			ItemStack chest = null;
			for (ArmorItem.Type type : TYPES) {
				ItemStack s = findInInventory(player, suitId, type);
				if (s != null) {
					inInv++;
					if (type == ArmorItem.Type.CHESTPLATE) {
						chest = s;
					}
				}
			}
			if (inInv == 4) {
				float e = chest != null ? IronManEnergy.stackEnergy(chest, suitId) / Math.max(1f, suit.energyCapacity()) : 1f;
				float integ = chest != null
						? IronManEnergy.stackIntegrity(chest, suitId) / IronManEnergy.maxIntegrity(suitId) : 1f;
				out.add(new IronManSuitListPayload.Option(suitId, IronManSuitListPayload.SOURCE_INVENTORY, e, integ, 0));
				continue;
			}

			// on one of the player's platforms in this dimension (registry = works when unloaded)?
			Optional<StarkPlatformRegistry.Entry> entry =
					registry.nearestHolding(player.getUUID(), level.dimension(), suitId, here);
			// supplement with a fresh scan of loaded platforms nearby (may not have synced yet)
			var loaded = nearestLoadedPlatform(level, here, player.getUUID(), suitId);
			if (entry.isEmpty() && loaded == null) {
				continue;
			}
			int dist;
			float e;
			float integ;
			if (loaded != null) {
				dist = (int) Math.sqrt(loaded.getBlockPos().distSqr(here));
				e = loaded.suitEnergy() / Math.max(1f, suit.energyCapacity());
				integ = loaded.suitIntegrity() / IronManEnergy.maxIntegrity(suitId);
			} else {
				dist = (int) Math.sqrt(entry.get().blockPos().distSqr(here));
				e = entry.get().suitEnergy() / Math.max(1f, suit.energyCapacity());
				integ = entry.get().suitIntegrity() / IronManEnergy.maxIntegrity(suitId);
			}
			out.add(new IronManSuitListPayload.Option(suitId, IronManSuitListPayload.SOURCE_PLATFORM, e, integ, dist));
		}
		return out;
	}

	/**
	 * "changes 19": plain C when unarmoured -- if a whole suit is sitting in your inventory, just put it
	 * on (no picker). Prefers your last active suit, then the highest tech tier. Returns false (so the
	 * caller falls back to {@link #openMenu}) if no complete suit is in the inventory.
	 */
	public static boolean autoEquipInventorySuit(ServerPlayer player) {
		if (!TonyStark.hasPower(player) || IronManArmor.wearingAnyIronMan(player)
				|| IronManSuitUpManager.inTransition(player)) {
			return false;
		}
		String active = TonyStark.activeSuitId(player);
		IronManSuitListPayload.Option pick = null;
		for (IronManSuitListPayload.Option o : gather(player)) {
			if (o.source() != IronManSuitListPayload.SOURCE_INVENTORY) {
				continue;
			}
			if (o.suitId().equals(active)) {
				pick = o;
				break;
			}
			IronManSuit suit = IronManSuits.byId(o.suitId());
			if (pick == null || (suit != null
					&& suit.techLevel() > IronManSuits.byId(pick.suitId()).techLevel())) {
				pick = o;
			}
		}
		if (pick == null) {
			return false;
		}
		IronManSuitUpManager.beginSuitUp(player, pick.suitId());
		return true;
	}

	/**
	 * The quick gesture (double-tap jump on the ground): call the best suit that can actually be
	 * assembled right now, no picker. Prefers the last active suit, then the highest tech tier.
	 */
	public static boolean callBest(ServerPlayer player) {
		if (!TonyStark.hasPower(player) || IronManArmor.wearingAnyIronMan(player)) {
			return false;
		}
		List<IronManSuitListPayload.Option> options = gather(player);
		if (options.isEmpty()) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.no_suit_available",
					Component.translatable("key.projecthero.ironman")), true);
			return false;
		}
		String active = TonyStark.activeSuitId(player);
		IronManSuitListPayload.Option pick = null;
		for (IronManSuitListPayload.Option o : options) {
			if (o.suitId().equals(active)) {
				pick = o;
				break;
			}
			IronManSuit suit = IronManSuits.byId(o.suitId());
			if (pick == null || (suit != null && suit.techLevel() > IronManSuits.byId(pick.suitId()).techLevel())) {
				pick = o;
			}
		}
		execute(player, pick.suitId(), pick.source());
		return true;
	}

	// ---------------- execute a choice ----------------

	public static void execute(ServerPlayer player, String suitId, int source) {
		if (!TonyStark.hasPower(player)) {
			return;
		}
		IronManSuit suit = IronManSuits.byId(suitId);
		if (suit == null || IronManArmor.wearingAnyIronMan(player) || IronManSuitUpManager.inTransition(player)) {
			return;
		}
		var s = TonyStark.state(player);
		if (suitId.equals(s.transitionSuit) && s.transitionTicks > 0) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.suit_incoming",
					Component.translatable(suit.nameKey())), true);
			return;
		}

		if (source == IronManSuitListPayload.SOURCE_INVENTORY && fullyInInventory(player, suitId)) {
			// equip immediately -- staged suit-up straight from the inventory (spec "changes 9")
			IronManSuitUpManager.beginSuitUp(player, suitId);
			return;
		}
		callFromPlatform(player, suit);
	}

	/** Fixed distance the couriers cover on the final approach -- tuned so the equip always takes the
	 *  same satisfying couple of seconds regardless of how far the armour actually started. */
	private static final double ARRIVAL_DISTANCE = 26.0;

	private static void callFromPlatform(ServerPlayer player, IronManSuit suit) {
		ServerLevel level = player.serverLevel();
		String suitId = suit.id();
		BlockPos here = player.blockPosition();
		StarkPlatformRegistry registry = StarkPlatformRegistry.get(level);

		// 1. A LOADED platform -> the armour flies straight to you off it, no wait.
		IronManSuitPlatformBlockEntity be = nearestLoadedPlatform(level, here, player.getUUID(), suitId);
		if (be != null) {
			deliver(player, suit, be, Vec3.atCenterOf(be.getBlockPos()).add(0, 1.0, 0), true);
			return;
		}

		// 2. An UNLOADED platform (from the registry) -> the armour has to travel to you first: queue a
		//    delay scaled to the real distance, THEN it flies in and equips. Pieces are NOT removed from
		//    the platform until it actually launches, so a restart mid-wait loses nothing.
		Optional<StarkPlatformRegistry.Entry> entry =
				registry.nearestHolding(player.getUUID(), level.dimension(), suitId, here);
		if (entry.isPresent()) {
			double dist = Math.sqrt(entry.get().blockPos().distSqr(here));
			int delay = (int) Math.max(60, Math.min(600, dist * 0.6)); // 3 s .. 30 s of travel
			PENDING.put(player.getUUID(), new PendingCall(suitId,
					net.minecraft.core.GlobalPos.of(level.dimension(), entry.get().blockPos()), delay));
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 0.7f);
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.suit_incoming_far",
					Component.translatable(suit.nameKey()), (int) dist).withStyle(ChatFormatting.AQUA), true);
			return;
		}

		// 3. No platform at all -- fall back to whatever pieces are in the pack (flies in the normal way).
		if (anyPieceInInventory(player, suitId)) {
			deliver(player, suit, null, null, false);
			return;
		}
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.no_suit_available",
				Component.translatable(suit.nameKey())), true);
	}

	/**
	 * Actually launch the couriers. {@code be} non-null = pull the missing pieces off that platform;
	 * {@code origin} non-null (with {@code straightFromPlatform}) = the couriers spawn right there and
	 * fly straight in; otherwise they come in from {@link #ARRIVAL_DISTANCE} out so the equip always
	 * takes the same length of time.
	 */
	private static void deliver(ServerPlayer player, IronManSuit suit, IronManSuitPlatformBlockEntity be,
			Vec3 origin, boolean straightFromPlatform) {
		ServerLevel level = player.serverLevel();
		String suitId = suit.id();

		float energyFrac = 1f;
		float integrity = IronManEnergy.maxIntegrity(suitId);
		if (be != null) {
			if (be.owner().isEmpty()) {
				be.bindTo(player.getUUID());
			}
			energyFrac = be.suitEnergy() / Math.max(1f, suit.energyCapacity());
			integrity = be.suitIntegrity();
		}

		int launched = 0;
		for (ArmorItem.Type type : TYPES) {
			if (IronManArmor.isPieceWorn(player, IronManSuitUpManager.slotFor(type), suitId)) {
				continue;
			}
			boolean took = false;
			if (be != null && be.takePiece(suitId, type)) {
				took = true;
			} else {
				int idx = findInInventoryIndex(player, suitId, type);
				if (idx >= 0) {
					player.getInventory().removeItem(idx, 1);
					took = true;
				}
			}
			if (took) {
				Vec3 from;
				if (straightFromPlatform && origin != null) {
					from = origin;
				} else {
					double ang = level.random.nextDouble() * Math.PI * 2;
					from = player.position().add(Math.cos(ang) * ARRIVAL_DISTANCE,
							12 + level.random.nextDouble() * 5, Math.sin(ang) * ARRIVAL_DISTANCE);
				}
				// Stagger each courier so the pieces fly in and equip one at a time, in TYPES order.
				IronManSuitPartEntity.spawn(level, from, player, suitId, type, launched * LAUNCH_STAGGER);
				launched++;
			}
		}

		if (launched == 0) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.no_suit_available",
					Component.translatable(suit.nameKey())), true);
			return;
		}

		IronManEnergy.setEnergy(player, suitId, energyFrac * suit.energyCapacity());
		IronManEnergy.setIntegrity(player, suitId, integrity);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.NETHERITE_BLOCK_FALL, SoundSource.PLAYERS, 1.0f, 0.8f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.suit_incoming",
				Component.translatable(suit.nameKey())).withStyle(ChatFormatting.AQUA), true);

		var s = TonyStark.state(player);
		s.transitionSuit = suitId;
		s.transitionUp = true;
		s.transitionTotal = Math.max(20, suit.suitUpType().durationTicks()
				+ Math.max(0, launched - 1) * LAUNCH_STAGGER + 40);
		s.transitionTicks = s.transitionTotal;
		s.transitionMask = 0;
	}

	// ---------------- pending calls from unloaded platforms ----------------

	private record PendingCall(String suitId, net.minecraft.core.GlobalPos platform, int ticksLeft) {
		PendingCall tick() {
			return new PendingCall(suitId, platform, ticksLeft - 1);
		}
	}

	private static final java.util.Map<java.util.UUID, PendingCall> PENDING = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Drop every in-flight "armour is travelling to you" countdown. Registered on
	 * {@code SERVER_STOPPED} by {@code ProjectHeroMod}, because this map is static and a single-player
	 * client keeps the same JVM across worlds: without it, quitting to the title screen mid-call and
	 * opening a <em>different</em> save left a countdown pointing at a {@code GlobalPos} from the old
	 * world (dimension keys match across saves, so the abort check does not catch it), which would then
	 * force-load that chunk in the new world. Nothing to persist -- the pieces never left the platform.
	 */
	public static void clearPending() {
		PENDING.clear();
	}

	/** Called every tick from {@link com.projecthero.mod.ironman.IronManSuitTicker}. */
	public static void tickPending(ServerPlayer player) {
		PendingCall p = PENDING.get(player.getUUID());
		if (p == null) {
			return;
		}
		IronManSuit suit = IronManSuits.byId(p.suitId());
		// abort cases: player suited up / mid-transition / changed dimension -> pieces never left the platform
		if (suit == null || IronManArmor.wearingAnyIronMan(player) || IronManSuitUpManager.inTransition(player)
				|| player.level().dimension() != p.platform().dimension()) {
			PENDING.remove(player.getUUID());
			return;
		}
		if (p.ticksLeft() > 0) {
			PENDING.put(player.getUUID(), p.tick());
			if (p.ticksLeft() % 40 == 0) {
				player.displayClientMessage(Component.translatable("message.projecthero.ironman.suit_eta",
						Component.translatable(suit.nameKey()), p.ticksLeft() / 20).withStyle(ChatFormatting.GRAY), true);
			}
			return;
		}
		PENDING.remove(player.getUUID());

		ServerLevel level = player.serverLevel();
		BlockPos pos = p.platform().pos();
		level.getChunk(pos.getX() >> 4, pos.getZ() >> 4); // one-off synchronous load for the removal
		if (level.getBlockEntity(pos) instanceof IronManSuitPlatformBlockEntity be
				&& p.suitId().equals(be.storedSuitId())) {
			deliver(player, suit, be, null, false); // flies in from ARRIVAL_DISTANCE, normal equip time
		} else {
			StarkPlatformRegistry.get(level).remove(level, pos);
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.platform_gone",
					Component.translatable(suit.nameKey())), true);
		}
	}

	// ---------------- death recovery ----------------

	/**
	 * When a Tony Stark player dies -- wearing an Iron Man suit, OR simply carrying one in the pack
	 * ("changes 12": recovery isn't just for a worn suit any more) -- the suit's onboard AI doesn't let
	 * the armour litter the ground, it flies itself to the nearest of the player's Suit Platforms (this
	 * dimension), taking a flat 50% integrity hit from the crash. Every distinct suit id the player is
	 * holding any piece of (worn or carried) is recovered independently, each to whichever platform
	 * actually holds that mark.
	 *
	 * <p>If there is no platform for a given mark, its pieces are stamped in place (so the damage still
	 * applies) and left exactly where they are -- worn pieces then drop the ordinary way when
	 * {@code Player.die()} runs, carried ones the same way the rest of the inventory always does.
	 * Skipped entirely under keepInventory.
	 *
	 * <p>Called from {@code ALLOW_DEATH}; must not itself cancel the death.
	 */
	public static void recoverSuitOnDeath(ServerPlayer player) {
		if (!TonyStark.hasPower(player)
				|| player.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY)) {
			return;
		}
		ServerLevel level = player.serverLevel();

		// Every suit id touched by this death, worn pieces first (their stack -- carrying live charge
		// state -- wins as the "representative" one if the same type also happens to be duplicated in
		// the pack, which should not normally happen but costs nothing to prefer correctly).
		java.util.Map<String, java.util.EnumMap<ArmorItem.Type, ItemStack>> bySuit = new java.util.LinkedHashMap<>();
		java.util.Map<String, java.util.EnumSet<ArmorItem.Type>> wornTypesBySuit = new java.util.HashMap<>();
		for (ArmorItem.Type type : TYPES) {
			net.minecraft.world.entity.EquipmentSlot slot = IronManSuitUpManager.slotFor(type);
			ItemStack st = player.getItemBySlot(slot);
			if (st.getItem() instanceof IronManArmorItem p) {
				bySuit.computeIfAbsent(p.suitId(), k -> new java.util.EnumMap<>(ArmorItem.Type.class)).put(type, st.copy());
				wornTypesBySuit.computeIfAbsent(p.suitId(), k -> java.util.EnumSet.noneOf(ArmorItem.Type.class)).add(type);
			}
		}
		var items = player.getInventory().items;
		java.util.Map<String, java.util.EnumMap<ArmorItem.Type, Integer>> invIndexBySuit = new java.util.HashMap<>();
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).getItem() instanceof IronManArmorItem p) {
				bySuit.computeIfAbsent(p.suitId(), k -> new java.util.EnumMap<>(ArmorItem.Type.class))
						.putIfAbsent(p.getType(), items.get(i).copy());
				invIndexBySuit.computeIfAbsent(p.suitId(), k -> new java.util.EnumMap<>(ArmorItem.Type.class))
						.put(p.getType(), i);
			}
		}
		if (bySuit.isEmpty()) {
			return;
		}

		for (var suitEntry : bySuit.entrySet()) {
			String suitId = suitEntry.getKey();
			IronManSuit suit = IronManSuits.byId(suitId);
			if (suit == null) {
				continue;
			}
			var pieces = suitEntry.getValue();
			var wornTypes = wornTypesBySuit.getOrDefault(suitId, java.util.EnumSet.noneOf(ArmorItem.Type.class));
			var invIdx = invIndexBySuit.getOrDefault(suitId, new java.util.EnumMap<>(ArmorItem.Type.class));

			boolean anyWorn = !wornTypes.isEmpty();
			ItemStack reference = pieces.containsKey(ArmorItem.Type.CHESTPLATE)
					? pieces.get(ArmorItem.Type.CHESTPLATE) : pieces.values().iterator().next();
			float energy = anyWorn ? IronManEnergy.energy(player, suitId) : IronManEnergy.stackEnergy(reference, suitId);
			float integrity = anyWorn ? IronManEnergy.integrity(player, suitId) : IronManEnergy.stackIntegrity(reference, suitId);
			// "changes 14": a 50%-of-this-suit's-max integrity crash hit, worn or not. Now that condition
			// pools vary a lot per mark (200 on a Mark 1, 500 on the advanced marks), a flat 250 would
			// near-total a small suit, so the crash cost scales with the suit's own ceiling.
			float halved = Math.max(0f, integrity - IronManEnergy.maxIntegrity(suitId) * 0.5f);
			int halvedPct = Math.round(100f * halved / IronManEnergy.maxIntegrity(suitId));

			int mask = 0;
			for (ArmorItem.Type type : pieces.keySet()) {
				mask |= switch (type) {
					case HELMET -> 1;
					case CHESTPLATE -> 2;
					case LEGGINGS -> 4;
					case BOOTS -> 8;
					default -> 0;
				};
			}

			IronManSuitPlatformBlockEntity dock = nearestLoadedDock(level, player.blockPosition(), player.getUUID(), suitId);
			Optional<StarkPlatformRegistry.Entry> regEntry = dock != null ? Optional.empty()
					: StarkPlatformRegistry.get(level).nearestDockFor(player.getUUID(), level.dimension(), suitId, player.blockPosition());

			if (dock == null && regEntry.isEmpty()) {
				// No platform anywhere for this mark -- stamp everything in place and leave it exactly
				// where it is; worn pieces drop the ordinary way once Player.die() runs, carried ones
				// the same way the rest of the inventory always does.
				for (ArmorItem.Type type : wornTypes) {
					IronManEnergy.stampStack(player.getItemBySlot(IronManSuitUpManager.slotFor(type)), energy, halved);
				}
				for (var e : invIdx.entrySet()) {
					IronManEnergy.stampStack(items.get(e.getValue()), energy, halved);
				}
				player.sendSystemMessage(Component.translatable("message.projecthero.ironman.suit_lost_no_platform",
						Component.translatable(suit.nameKey())).withStyle(ChatFormatting.RED));
				continue;
			}

			// Pull every piece of this suit off the player entirely -- worn AND carried -- before it
			// either docks immediately or is queued to fly home.
			for (ArmorItem.Type type : wornTypes) {
				player.setItemSlot(IronManSuitUpManager.slotFor(type), ItemStack.EMPTY);
			}
			for (var e : invIdx.entrySet()) {
				items.set(e.getValue(), ItemStack.EMPTY);
			}

			if (dock != null) {
				if (dock.owner().isEmpty()) {
					dock.bindTo(player.getUUID());
				}
				for (ArmorItem.Type type : pieces.keySet()) {
					ItemStack s = new ItemStack(IronManItems.armor(suitId, type));
					IronManEnergy.stampStack(s, energy, halved);
					dock.store(s);
				}
				BlockPos dp = dock.getBlockPos();
				player.sendSystemMessage(Component.translatable("message.projecthero.ironman.suit_recovered",
						Component.translatable(suit.nameKey()), halvedPct, dp.getX(), dp.getY(), dp.getZ())
						.withStyle(ChatFormatting.AQUA));
			} else {
				BlockPos pos = regEntry.get().blockPos();
				StarkSuitReturnQueue.get(level).enqueue(player.getUUID(),
						net.minecraft.core.GlobalPos.of(level.dimension(), pos), suitId, mask, energy, halved);
				player.sendSystemMessage(Component.translatable("message.projecthero.ironman.suit_returning",
						Component.translatable(suit.nameKey()), pos.getX(), pos.getY(), pos.getZ())
						.withStyle(ChatFormatting.AQUA));
			}
		}
	}

	private static IronManSuitPlatformBlockEntity nearestLoadedDock(ServerLevel level, BlockPos here,
			java.util.UUID owner, String suitId) {
		int cx = here.getX() >> 4;
		int cz = here.getZ() >> 4;
		IronManSuitPlatformBlockEntity best = null;
		double bestSq = Double.MAX_VALUE;
		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				var chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
				if (chunk == null) {
					continue;
				}
				for (var e : chunk.getBlockEntities().entrySet()) {
					if (e.getValue() instanceof IronManSuitPlatformBlockEntity p
							&& (p.owner().isEmpty() || p.owner().get().equals(owner))
							&& (p.storedSuitId() == null || suitId.equals(p.storedSuitId()))
							&& !p.isFull()) {
						double d = e.getKey().distSqr(here);
						if (d < bestSq) {
							bestSq = d;
							best = p;
						}
					}
				}
			}
		}
		return best;
	}

	// ---------------- helpers ----------------

	public static boolean fullyInInventory(ServerPlayer player, String suitId) {
		for (ArmorItem.Type type : TYPES) {
			if (findInInventory(player, suitId, type) == null) {
				return false;
			}
		}
		return true;
	}

	private static IronManSuitPlatformBlockEntity nearestLoadedPlatform(ServerLevel level, BlockPos center,
			java.util.UUID owner, String suitId) {
		int cx = center.getX() >> 4;
		int cz = center.getZ() >> 4;
		IronManSuitPlatformBlockEntity best = null;
		double bestSq = Double.MAX_VALUE;
		int r = 6;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				var chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
				if (chunk == null) {
					continue;
				}
				for (var e : chunk.getBlockEntities().entrySet()) {
					if (e.getValue() instanceof IronManSuitPlatformBlockEntity p
							&& suitId.equals(p.storedSuitId())
							&& (p.owner().isEmpty() || p.owner().get().equals(owner))) {
						double d = e.getKey().distSqr(center);
						if (d < bestSq) {
							bestSq = d;
							best = p;
						}
					}
				}
			}
		}
		return best;
	}

	private static ItemStack findInInventory(ServerPlayer player, String suitId, ArmorItem.Type type) {
		int idx = findInInventoryIndex(player, suitId, type);
		return idx < 0 ? null : player.getInventory().getItem(idx);
	}

	private static boolean anyPieceInInventory(ServerPlayer player, String suitId) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).getItem() instanceof IronManArmorItem piece && piece.suitId().equals(suitId)) {
				return true;
			}
		}
		return false;
	}

	private static int findInInventoryIndex(ServerPlayer player, String suitId, ArmorItem.Type type) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.getItem() instanceof IronManArmorItem piece
					&& piece.suitId().equals(suitId) && piece.getType() == type) {
				return i;
			}
		}
		return -1;
	}
}
