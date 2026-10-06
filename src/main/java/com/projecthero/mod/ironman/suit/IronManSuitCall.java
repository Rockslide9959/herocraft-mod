package com.projecthero.mod.ironman.suit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFlight;
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
 *   <li>Only genuinely-assemblable suits are ever listed, so "that mark is not developed" can't appear.</li>
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
			// v0.14.29 (agent D): Sneak+C while suited -- the picker offers to send the worn suit (and anything carried)
			// home to its platform for repair; plain C stays suit-down (handled elsewhere)
			if (IronManSuitUpManager.inTransition(player)) {
				return;
			}
			List<IronManSuitListPayload.Option> home = sendBackOptions(player);
			if (home.isEmpty()) {
				String worn = IronManArmor.wornSuitId(player);
				IronManSuit ws = worn == null ? null : IronManSuits.byId(worn);
				player.displayClientMessage(Component.translatable("message.projecthero.ironman.send_back_no_platform",
						ws == null ? Component.literal("?") : Component.translatable(ws.nameKey()))
						.withStyle(ChatFormatting.RED), true);
				return;
			}
			ServerPlayNetworking.send(player, new IronManSuitListPayload(home));
			return;
		}
		List<IronManSuitListPayload.Option> options = new ArrayList<>(gather(player));
		// v0.15.1: without the Stark Glasses the picker still opens, but platform suits can't be called (greyed client-side)
		// v0.15.6: with only the Colantotte Bracelets on, only the Mark 7 can be called
		List<IronManSuitListPayload.Option> platformOpts = options.stream()
				.filter(o -> o.source() == IronManSuitListPayload.SOURCE_PLATFORM).toList();
		if (!platformOpts.isEmpty() && platformOpts.stream()
				.noneMatch(o -> com.projecthero.mod.ironman.gear.StarkGear.canCall(player, o.suitId()))) {
			com.projecthero.mod.ironman.gear.StarkGear.refuseCall(player, platformOpts.get(0).suitId());
		}
		// v0.14.27: pieces carried in the pack can also be sent home to a platform from the same picker
		options.addAll(sendBackOptions(player));
		ServerPlayNetworking.send(player, new IronManSuitListPayload(options));
	}

	// ---------------- v0.14.27: send carried pieces back to a platform ----------------

	/** The inventory (not worn) pieces of {@code suitId}, by type, with their inventory slot index. */
	private static java.util.EnumMap<ArmorItem.Type, Integer> carriedPieces(ServerPlayer player, String suitId) {
		java.util.EnumMap<ArmorItem.Type, Integer> out = new java.util.EnumMap<>(ArmorItem.Type.class);
		var items = player.getInventory().items;
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).getItem() instanceof IronManArmorItem p && p.suitId().equals(suitId)) {
				out.putIfAbsent(p.getType(), i);
			}
		}
		return out;
	}

	/**
	 * One {@link IronManSuitListPayload#SOURCE_SEND_BACK} option per mark that has pieces in the pack AND a platform of
	 * the player's (loaded, or in the registry) that could take them. Never part of {@link #gather} -- auto-equip, the
	 * quick call and Protocol Phoenix only ever see suits to put ON.
	 */
	public static List<IronManSuitListPayload.Option> sendBackOptions(ServerPlayer player) {
		List<IronManSuitListPayload.Option> out = new ArrayList<>();
		BlockPos here = player.blockPosition();
		List<IronManSuit> suits = new ArrayList<>(IronManSuits.all());
		suits.sort(java.util.Comparator.comparingInt(IronManSuit::markNumber));
		String worn = IronManArmor.wornSuitId(player);
		for (IronManSuit suit : suits) {
			var carried = carriedPieces(player, suit.id());
			boolean wearing = suit.id().equals(worn) && wornMask(player, suit.id()) != 0;
			if (carried.isEmpty() && !wearing) {
				continue;
			}
			BlockPos dock = sendBackTarget(player, suit.id());
			if (dock == null) {
				continue;
			}
			float e;
			float integ;
			if (wearing) {
				// v0.14.29: the worn suit's live pool
				e = IronManEnergy.energy(player, suit.id()) / Math.max(1f, suit.energyCapacity());
				integ = IronManEnergy.integrity(player, suit.id()) / IronManEnergy.maxIntegrity(suit.id());
			} else {
				ItemStack ref = player.getInventory().items.get(carried.containsKey(ArmorItem.Type.CHESTPLATE)
						? carried.get(ArmorItem.Type.CHESTPLATE) : carried.values().iterator().next());
				e = IronManEnergy.stackEnergy(ref, suit.id()) / Math.max(1f, suit.energyCapacity());
				integ = IronManEnergy.stackIntegrity(ref, suit.id()) / IronManEnergy.maxIntegrity(suit.id());
			}
			out.add(new IronManSuitListPayload.Option(suit.id(), IronManSuitListPayload.SOURCE_SEND_BACK, e, integ,
					(int) Math.sqrt(dock.distSqr(here))));
		}
		return out;
	}

	/** v0.14.29: bit mask (1 helmet .. 8 boots) of the pieces of {@code suitId} the player is wearing. */
	private static int wornMask(ServerPlayer player, String suitId) {
		int mask = 0;
		for (ArmorItem.Type t : TYPES) {
			if (IronManArmor.isPieceWorn(player, IronManSuitUpManager.slotFor(t), suitId)) {
				mask |= switch (t) {
					case HELMET -> 1;
					case CHESTPLATE -> 2;
					case LEGGINGS -> 4;
					default -> 8;
				};
			}
		}
		return mask;
	}

	/**
	 * Where pieces of {@code suitId} would go home to. v0.14.29: first the platform they were last called off (stamped on
	 * the pieces, see {@link IronManPlatformReturn#tagHome}) if it is in this dimension and still has room -- loaded or
	 * not -- then the nearest loaded dock, then the registry's nearest. Null = none.
	 */
	private static BlockPos sendBackTarget(ServerPlayer player, String suitId) {
		ServerLevel level = player.serverLevel();
		BlockPos origin = originPlatform(player, suitId);
		if (origin != null) {
			return origin;
		}
		IronManSuitPlatformBlockEntity dock = nearestLoadedDock(level, player.blockPosition(), player.getUUID(), suitId);
		if (dock != null) {
			return dock.getBlockPos();
		}
		return StarkPlatformRegistry.get(level)
				.nearestDockFor(player.getUUID(), level.dimension(), suitId, player.blockPosition())
				.map(StarkPlatformRegistry.Entry::blockPos).orElse(null);
	}

	/** v0.14.29: the platform a worn / carried piece of {@code suitId} was last called off, if it can take it back. */
	private static BlockPos originPlatform(ServerPlayer player, String suitId) {
		ServerLevel level = player.serverLevel();
		List<ItemStack> pieces = new ArrayList<>();
		for (ArmorItem.Type t : TYPES) {
			ItemStack w = player.getItemBySlot(IronManSuitUpManager.slotFor(t));
			if (w.getItem() instanceof IronManArmorItem a && a.suitId().equals(suitId)) {
				pieces.add(w);
			}
		}
		for (ItemStack s : player.getInventory().items) {
			if (s.getItem() instanceof IronManArmorItem a && a.suitId().equals(suitId)) {
				pieces.add(s);
			}
		}
		for (ItemStack s : pieces) {
			Optional<net.minecraft.core.GlobalPos> home = IronManPlatformReturn.homeOf(s);
			if (home.isEmpty() || home.get().dimension() != level.dimension()) {
				continue;
			}
			BlockPos pos = home.get().pos();
			if (level.isLoaded(pos)) {
				if (level.getBlockEntity(pos) instanceof IronManSuitPlatformBlockEntity be
						&& (be.owner().isEmpty() || be.owner().get().equals(player.getUUID()))
						&& (be.storedSuitId() == null || suitId.equals(be.storedSuitId())) && !be.isFull()) {
					return pos;
				}
				continue;
			}
			Optional<StarkPlatformRegistry.Entry> e = StarkPlatformRegistry.get(level).at(level, pos);
			if (e.isPresent() && (e.get().owner().isEmpty() || e.get().ownedBy(player.getUUID()))
					&& e.get().hasRoomFor(suitId)) {
				return pos;
			}
		}
		return null;
	}

	/**
	 * v0.14.27, explicit user request: send every carried (inventory, not worn) piece of {@code suitId} back to the
	 * player's platform -- docked at once if that platform is loaded, otherwise queued on {@link StarkSuitReturnQueue}
	 * (the same fly-home path a suit takes when its wearer dies, minus the crash damage). Returns pieces sent.
	 */
	/** v0.14.28: how far in front of the player a sent-home suit stands while it builds itself (blocks). */
	public static final double SEND_HOME_DISTANCE = 1.0;

	public static int sendBack(ServerPlayer player, String suitId) {
		IronManSuit suit = IronManSuits.byId(suitId);
		if (suit == null || !TonyStark.hasPower(player)) {
			return 0;
		}
		var carried = carriedPieces(player, suitId);
		// v0.14.29 (agent D): the worn suit can be sent home too (Sneak+C picker while suited) -- never mid-transition
		int worn = IronManSuitUpManager.inTransition(player) ? 0 : wornMask(player, suitId);
		if (carried.isEmpty() && worn == 0) {
			return 0;
		}
		ServerLevel level = player.serverLevel();
		var items = player.getInventory().items;
		// v0.14.29: the platform it was called off first (loaded or not), else the nearest dock
		BlockPos targetPos = sendBackTarget(player, suitId);
		IronManSuitPlatformBlockEntity dock = targetPos != null && level.isLoaded(targetPos)
				&& level.getBlockEntity(targetPos) instanceof IronManSuitPlatformBlockEntity b ? b : null;
		if (targetPos == null) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.send_back_no_platform",
					Component.translatable(suit.nameKey())).withStyle(ChatFormatting.RED), true);
			return 0;
		}
		java.util.EnumMap<ArmorItem.Type, ItemStack> wornStacks = new java.util.EnumMap<>(ArmorItem.Type.class);
		if (worn != 0) {
			float energy = IronManEnergy.energy(player, suitId);
			float integrity = IronManEnergy.integrity(player, suitId);
			if (IronManFlight.isFlying(player)) {
				IronManFlight.setFlying(player, false);
			}
			for (ArmorItem.Type t : TYPES) {
				net.minecraft.world.entity.EquipmentSlot slot = IronManSuitUpManager.slotFor(t);
				if (!IronManArmor.isPieceWorn(player, slot, suitId)) {
					continue;
				}
				ItemStack out = player.getItemBySlot(slot).copy();
				IronManEnergy.stampStack(out, energy, integrity);
				player.setItemSlot(slot, ItemStack.EMPTY);
				wornStacks.put(t, out);
			}
			if (!IronManArmor.wearingAnyIronMan(player)) {
				TonyStark.setActiveSuit(player, "");
			}
		}
		// v0.14.28: the pieces leave the pack and build themselves into a standing suit 1 block in front of the player
		// (boots first, ~1 s each), which then flies home to the platform and docks; a platform out of reach (unloaded
		// chunk) gets it through StarkSuitReturnQueue once the suit has climbed out of sight. Never lost: every stack is
		// in exactly one place (pack -> courier -> platform / queue / back to the player).
		int sent = 0;
		Vec3 from = player.position().add(0, 1.0, 0);
		BlockPos target = targetPos;
		if (dock != null && dock.owner().isEmpty()) {
			dock.bindTo(player.getUUID());
		}
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, player.getYRot()) : flat.normalize();
		Vec3 feet = player.position().add(flat.scale(SEND_HOME_DISTANCE));
		float faceYaw = player.getYRot() + 180f; // the suit faces its owner while it builds
		ArmorItem.Type[] order = { ArmorItem.Type.BOOTS, ArmorItem.Type.LEGGINGS, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.HELMET };
		int count = 0;
		for (ArmorItem.Type t : order) {
			if (carried.containsKey(t) || wornStacks.containsKey(t)) {
				count++;
			}
		}
		for (ArmorItem.Type t : order) {
			ItemStack one = wornStacks.get(t);
			if (one == null) {
				Integer idx = carried.get(t);
				if (idx == null) {
					continue;
				}
				one = items.get(idx).split(1);
			}
			// (a carried duplicate of a worn type stays in the pack -- one piece per slot on the rack)
			if (one.isEmpty()) {
				continue;
			}
			IronManPlatformReturn.clearHome(one);
			IronManSuitPartEntity.spawnHome(level, feet, faceYaw, player, one, sent, count, target);
			sent++;
		}
		if (sent > 0) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.send_back_flying",
					Component.translatable(suit.nameKey()), target.getX(), target.getY(), target.getZ())
					.withStyle(ChatFormatting.AQUA), true);
		}
		if (sent > 0) {
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, from.x, from.y, from.z, 12, 0.3, 0.4, 0.3, 0.06);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 1.4f);
			player.inventoryMenu.broadcastChanges();
		}
		return sent;
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
			if (suit.summonType() == SummonType.SUITCASE_ITEM) {
				continue; // v0.14.29: the Mark V suits up only from its suitcase (right-click), never from C / the picker
			}

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
	 * caller falls back to {@link #openMenu}) if no armour at all is in the inventory.
	 *
	 * <p>v0.14.22, explicit user request ("when player has an armour in their inventory pressing c should
	 * automatically equip that armour"): a packed Mark V suitcase and an incomplete set now count too --
	 * complete sets first, then the suitcase, then whichever partial set has the most pieces.
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
		if (pick != null) {
			return orbitalDrop(player, IronManSuits.byId(pick.suitId())) || IronManSuitUpManager.beginSuitUp(player, pick.suitId());
		}
		// v0.14.29: the Mark V suitcase is no longer deployed by C -- only by right-clicking the case
		String partial = null;
		int best = 0;
		for (IronManSuit suit : IronManSuits.all()) {
			if (suit.summonType() == SummonType.SUITCASE_ITEM) {
				continue; // v0.14.29: never by C
			}
			int n = 0;
			for (ArmorItem.Type type : TYPES) {
				if (findInInventory(player, suit.id(), type) != null) {
					n++;
				}
			}
			if (n > best || (n > 0 && n == best && suit.id().equals(active))) {
				best = n;
				partial = suit.id();
			}
		}
		return partial != null && IronManSuitUpManager.beginSuitUp(player, partial);
	}

	/**
	 * The quick gesture (double-tap jump on the ground): call the best suit that can actually be
	 * assembled right now, no picker. Prefers the last active suit, then the highest tech tier.
	 */
	public static boolean callBest(ServerPlayer player) {
		if (!TonyStark.hasPower(player) || IronManArmor.wearingAnyIronMan(player)) {
			return false;
		}
		if (!com.projecthero.mod.ironman.gear.StarkGear.canCall(player)) { // v0.15.1: suit calling needs the Stark Glasses
			com.projecthero.mod.ironman.gear.StarkGear.refuseCall(player);
			return false;
		}
		List<IronManSuitListPayload.Option> all = gather(player);
		if (all.isEmpty()) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.no_suit_available",
					Component.translatable("key.projecthero.ironman")), true);
			return false;
		}
		// v0.15.6: a suit carried whole can always go on; anything else is a call -- with only the bracelets on, the Mark 7
		List<IronManSuitListPayload.Option> options = all.stream()
				.filter(o -> (o.source() == IronManSuitListPayload.SOURCE_INVENTORY && fullyInInventory(player, o.suitId()))
						|| com.projecthero.mod.ironman.gear.StarkGear.canCall(player, o.suitId()))
				.toList();
		if (options.isEmpty()) {
			com.projecthero.mod.ironman.gear.StarkGear.refuseCall(player, all.get(0).suitId());
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
		if (source == IronManSuitListPayload.SOURCE_SEND_BACK) {
			sendBack(player, suitId); // v0.14.27: carried pieces home to a platform
			return;
		}
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
			if (!orbitalDrop(player, suit)) {
				IronManSuitUpManager.beginSuitUp(player, suitId);
			}
			return;
		}
		// v0.15.1: calling a suit in needs the Stark Glasses (v0.15.6: the bracelets call only the Mark 7)
		if (!com.projecthero.mod.ironman.gear.StarkGear.canCall(player, suitId)) {
			com.projecthero.mod.ironman.gear.StarkGear.refuseCall(player, suitId);
			return;
		}
		callFromPlatform(player, suit);
	}

	/**
	 * v0.14.29 (agent C): the Mark 7 orbital drop for a suit carried in the pack -- airborne (falling, gliding, jumping),
	 * a pod suit comes in by delivery pod out of the sky instead of the 12 s staged build, keeping the suit's charge.
	 * Returns false (nothing done) for any other suit or a grounded player.
	 */
	public static boolean orbitalDrop(ServerPlayer player, IronManSuit suit) {
		if (suit == null || suit.summonType() != SummonType.TRACKING_POD
				|| !com.projecthero.mod.ironman.entity.IronManDeliveryPodEntity.airborne(player)
				|| !TonyStark.hasPower(player) || IronManSuitUpManager.inTransition(player)
				|| !com.projecthero.mod.ironman.gear.StarkGear.canCall(player, suit.id())) { // v0.15.1: the orbital pod call needs the Stark Glasses (else a normal suit-up)
			return false;
		}
		ItemStack chest = findInInventory(player, suit.id(), ArmorItem.Type.CHESTPLATE);
		if (chest == null) {
			return false;
		}
		IronManEnergy.loadFromStack(player, suit.id(), chest);
		float energy = IronManEnergy.energy(player, suit.id());
		float integrity = IronManEnergy.integrity(player, suit.id());
		deliver(player, suit, null, null, false);
		IronManEnergy.setEnergy(player, suit.id(), energy);
		IronManEnergy.setIntegrity(player, suit.id(), integrity);
		return true;
	}

	/**
	 * v0.15.4: is this a Colantotte Bracelets call -- the Mark 7 (the pod suit) called while the bracelets are worn? Then
	 * the pod flies twice as fast, a far platform's travel wait is halved, and the suit goes on with the quick wrap-on.
	 */
	public static boolean fastCall(ServerPlayer player, IronManSuit suit) {
		return IronManSuitUpManager.braceletSuitUp(player, suit) && suit.summonType() == SummonType.TRACKING_POD;
	}

	/** v0.15.6: fallen at least this far (blocks) counts as "falling" for the suit-call speed-up. */
	public static final float FALLING_DISTANCE = 2.0f;

	/**
	 * v0.15.6: is {@code player} falling -- airborne (not standing, swimming, riding, gliding or flying, with or without
	 * a suit) and already {@value #FALLING_DISTANCE}+ blocks into the drop? Then every called suit races to catch them:
	 * couriers fly twice as fast, the pod descends twice as fast and spits its pieces at double rate, and each piece
	 * builds on in half the time ({@link IronManSuitFx#fast}). Server-side (fallDistance is tracked there).
	 */
	public static boolean falling(ServerPlayer player) {
		return !player.onGround() && !player.isInWater() && !player.isInLava() && !player.isPassenger()
				&& !player.isFallFlying() && !player.getAbilities().flying && !IronManFlight.isFlying(player)
				&& !com.projecthero.mod.ironman.RepulsorBoots.isFlying(player)
				&& player.fallDistance >= FALLING_DISTANCE;
	}

	/** Fixed distance the couriers cover on the final approach -- tuned so the equip always takes the
	 *  same satisfying couple of seconds regardless of how far the armour actually started. */
	private static final double ARRIVAL_DISTANCE = 26.0;
	/** v0.14.29: a loaded platform further than this (blocks) sends its pieces in from ARRIVAL_DISTANCE instead. */
	public static final double STRAIGHT_FLIGHT_RANGE = 64.0;
	/** v0.14.29: Protocol Phoenix's travel time from an unloaded platform (ticks). */
	public static final int PHOENIX_DELAY = 40;

	private static void callFromPlatform(ServerPlayer player, IronManSuit suit) {
		ServerLevel level = player.serverLevel();
		String suitId = suit.id();
		BlockPos here = player.blockPosition();
		StarkPlatformRegistry registry = StarkPlatformRegistry.get(level);

		// 1. A LOADED platform -> the armour flies straight to you off it, no wait.
		IronManSuitPlatformBlockEntity be = nearestLoadedPlatform(level, here, player.getUUID(), suitId);
		if (be != null) {
			// v0.14.29: straight off the rack only when that spot keeps entities ticking -- a courier launched into a
			// loaded-but-frozen border chunk used to sit there with the piece (saved into that chunk) until someone
			// walked by. Otherwise the pieces come in from the usual ARRIVAL_DISTANCE around the player.
			Vec3 rack = Vec3.atCenterOf(be.getBlockPos()).add(0, 1.0, 0);
			boolean straight = IronManChunkTickets.entityTicking(level, rack)
					&& rack.distanceTo(player.position()) <= STRAIGHT_FLIGHT_RANGE;
			deliver(player, suit, be, straight ? rack : null, straight);
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
			if (TonyStark.phoenixEmergency(player)) {
				delay = PHOENIX_DELAY; // v0.14.29: an emergency recall does not make a dying player wait 30 s
			}
			if (fastCall(player, suit)) {
				delay /= 2; // v0.15.4: the Colantotte Bracelets bring the Mark 7 in twice as fast
			}
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

		// v0.14.21: the REAL stacks leave the platform / pack and travel in the couriers (or the pod).
		List<ItemStack> taken = new ArrayList<>();
		for (ArmorItem.Type type : TYPES) {
			if (IronManArmor.isPieceWorn(player, IronManSuitUpManager.slotFor(type), suitId)) {
				continue;
			}
			ItemStack stack = be != null ? be.takePieceStack(suitId, type) : ItemStack.EMPTY;
			if (!stack.isEmpty() && be != null) {
				IronManPlatformReturn.tagHome(stack, level, be.getBlockPos()); // v0.14.29: remembers its own platform
			}
			if (stack.isEmpty()) {
				int idx = findInInventoryIndex(player, suitId, type);
				if (idx >= 0) {
					stack = player.getInventory().removeItem(idx, 1);
				}
			}
			if (!stack.isEmpty()) {
				taken.add(stack);
			}
		}
		int launched = taken.size();
		int arrival;
		boolean falling = falling(player); // v0.15.6: a falling caller gets everything at double speed
		int stagger = falling ? LAUNCH_STAGGER / 2 : LAUNCH_STAGGER;
		if (launched > 0 && suit.summonType() == SummonType.TRACKING_POD) {
			// Mark VII: one delivery pod carries the whole set and fires the pieces out at its owner
			boolean fast = fastCall(player, suit); // v0.15.4: the Colantotte Bracelets
			com.projecthero.mod.ironman.entity.IronManDeliveryPodEntity.spawn(level, player, taken,
					straightFromPlatform ? origin : null, fast).falling(falling);
			arrival = com.projecthero.mod.ironman.entity.IronManDeliveryPodEntity.ticksToLastPiece(launched, fast) + 30;
			if (fast) {
				// v0.15.6: the bracelets are spent on the call -- the pair is gone (claim a new one from the platform)
				com.projecthero.mod.ironman.gear.ColantotteBracelets.consume(player);
			}
		} else {
			for (int i = 0; i < taken.size(); i++) {
				Vec3 from;
				if (straightFromPlatform && origin != null) {
					from = origin;
				} else {
					double ang = level.random.nextDouble() * Math.PI * 2;
					from = player.position().add(Math.cos(ang) * ARRIVAL_DISTANCE,
							12 + level.random.nextDouble() * 5, Math.sin(ang) * ARRIVAL_DISTANCE);
				}
				// Stagger each courier so the pieces fly in and equip one at a time, in TYPES order.
				IronManSuitPartEntity.spawn(level, from, player, taken.get(i), i * stagger).falling(falling);
			}
			arrival = suit.suitUpType().durationTicks() + Math.max(0, launched - 1) * LAUNCH_STAGGER + 40;
			if (falling) {
				arrival /= 2; // v0.15.6: the couriers race in (the suit-up still waits for the last piece's build-on)
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
		s.transitionTotal = Math.max(20, arrival);
		s.transitionTicks = s.transitionTotal;
		s.transitionMask = 0;
		s.transitionReleaseMask = 0;
		s.transitionPlan = 0;
		s.transitionBracelet = false;
		// arms out to receive the pieces while they are inbound (every viewer sees it); v0.14.27: the last piece then
		// builds on over BUILD_TICKS, and the suit-up only completes once it has (IronManSuitUpManager.tick holds it)
		IronManSuitFx.startPose(player, IronManSuitFx.POSE_RECEIVE, s.transitionTotal + IronManSuitFx.BUILD_TICKS,
				IronManSuitFx.STYLE_PLATES, 0);
	}

	// ---------------- single pieces + the command path (v0.14.21: replaces IronManSuitSummonManager) ----------------

	/**
	 * {@code /ironman suit <id>}: put the whole suit on through the real call path -- a staged suit-up if it is all in
	 * the pack, otherwise a call off the nearest platform (loaded or not) or whatever pieces are carried.
	 */
	public static boolean commandCall(ServerPlayer player, String suitId) {
		IronManSuit suit = IronManSuits.byId(suitId);
		if (suit == null || !TonyStark.hasPower(player) || IronManSuitUpManager.inTransition(player)
				|| IronManArmor.wearingAnyIronMan(player)) {
			return false;
		}
		if (fullyInInventory(player, suitId)) {
			return IronManSuitUpManager.beginSuitUp(player, suitId);
		}
		ServerLevel level = player.serverLevel();
		boolean reachable = anyPieceInInventory(player, suitId)
				|| nearestLoadedPlatform(level, player.blockPosition(), player.getUUID(), suitId) != null
				|| StarkPlatformRegistry.get(level).nearestHolding(player.getUUID(), level.dimension(), suitId,
						player.blockPosition()).isPresent();
		if (!reachable) {
			return false;
		}
		if (!com.projecthero.mod.ironman.gear.StarkGear.canCall(player, suitId)) { // v0.15.1
			com.projecthero.mod.ironman.gear.StarkGear.refuseCall(player, suitId);
			return false;
		}
		callFromPlatform(player, suit);
		return true;
	}

	/**
	 * {@code /ironman part <id> <piece>}: fly ONE piece in -- out of the pack, else off the nearest loaded platform of
	 * the player's holding it. The real stack travels in the courier.
	 */
	public static boolean callPiece(ServerPlayer player, String suitId, ArmorItem.Type type) {
		if (!TonyStark.hasPower(player) || IronManSuits.byId(suitId) == null) {
			return false;
		}
		if (!com.projecthero.mod.ironman.gear.StarkGear.canCall(player, suitId)) { // v0.15.1: flying a piece in is a call -- needs the Stark Glasses
			com.projecthero.mod.ironman.gear.StarkGear.refuseCall(player, suitId);
			return false;
		}
		if (IronManArmor.isPieceWorn(player, IronManSuitUpManager.slotFor(type), suitId)) {
			return false;
		}
		ServerLevel level = player.serverLevel();
		int idx = findInInventoryIndex(player, suitId, type);
		if (idx >= 0) {
			ItemStack stack = player.getInventory().removeItem(idx, 1);
			double ang = level.random.nextDouble() * Math.PI * 2;
			Vec3 from = player.position().add(Math.cos(ang) * 6, 3 + level.random.nextDouble() * 2, Math.sin(ang) * 6);
			IronManSuitPartEntity.spawn(level, from, player, stack, 0);
			return true;
		}
		IronManSuitPlatformBlockEntity be = nearestLoadedPlatform(level, player.blockPosition(), player.getUUID(), suitId);
		if (be != null) {
			ItemStack stack = be.takePieceStack(suitId, type);
			if (!stack.isEmpty()) {
				IronManSuitPartEntity.spawn(level, Vec3.atCenterOf(be.getBlockPos()).add(0, 1.0, 0), player, stack, 0);
				return true;
			}
		}
		return false;
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

	/** v0.14.29 gametest hook: a pending far call finishes its travel countdown on the player's next tick. */
	public static void skipPendingTravel(ServerPlayer player) {
		PENDING.computeIfPresent(player.getUUID(), (k, p) -> new PendingCall(p.suitId(), p.platform(), 0));
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
		// v0.14.29: load + hold the platform chunk with our own short ticket, so the removal is saved with the chunk
		IronManSuitPlatformBlockEntity be = IronManChunkTickets.loadPlatform(level, pos);
		if (be != null && p.suitId().equals(be.storedSuitId())) {
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

			// v0.14.21: the very stacks the player had (enchantments, names, ...) go home, stamped with the crash damage
			for (ItemStack st : pieces.values()) {
				IronManEnergy.stampStack(st, energy, halved);
			}
			if (dock != null) {
				if (dock.owner().isEmpty()) {
					dock.bindTo(player.getUUID());
				}
				for (ItemStack st : pieces.values()) {
					ItemStack copy = st.copy();
					if (!dock.store(copy)) {
						player.drop(st.copy(), true, false); // a slot clash on the dock: never lose the piece
					}
				}
				BlockPos dp = dock.getBlockPos();
				player.sendSystemMessage(Component.translatable("message.projecthero.ironman.suit_recovered",
						Component.translatable(suit.nameKey()), halvedPct, dp.getX(), dp.getY(), dp.getZ())
						.withStyle(ChatFormatting.AQUA));
			} else {
				BlockPos pos = regEntry.get().blockPos();
				net.minecraft.core.GlobalPos gp = net.minecraft.core.GlobalPos.of(level.dimension(), pos);
				// v0.14.29: rack it on the (unloaded) platform right away via a chunk ticket; the queue is the fallback
				if (!IronManPlatformReturn.depositNow(level.getServer(), player.getUUID(), gp, new ArrayList<>(pieces.values()))) {
					StarkSuitReturnQueue.get(level).enqueue(player.getUUID(), gp, suitId, mask, energy, halved,
							new ArrayList<>(pieces.values()));
				}
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
