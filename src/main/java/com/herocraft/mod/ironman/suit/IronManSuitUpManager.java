package com.herocraft.mod.ironman.suit;

import com.herocraft.mod.ironman.IronManArmor;
import com.herocraft.mod.ironman.IronManEnergy;
import com.herocraft.mod.ironman.IronManFlight;
import com.herocraft.mod.ironman.TonyStark;
import com.herocraft.mod.ironman.data.TonyStarkState;
import com.herocraft.mod.ironman.item.IronManArmorItem;
import com.herocraft.mod.ironman.item.IronManItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * The reusable suit-up / suit-down system (spec sections 22-27, 36). One implementation; the
 * {@link SuitUpType} on the {@link IronManSuit} definition selects the staged sequence and FX.
 *
 * <h2>How the staging works (no custom rendering needed)</h2>
 * The armour pieces are equipped (or removed) <em>one stage at a time</em> from a per-player timer in
 * {@link #tick}, so the suit visibly assembles boots &rarr; legs &rarr; torso &rarr; helmet using
 * only vanilla armour rendering. Each stage fires a particle burst + a mechanical clunk at the right
 * body height. Mark 50 ({@link SuitUpType#NANOTECH}) instead spreads from the chest housing outward
 * with nanite particles.
 *
 * <p>Server-authoritative throughout. {@link #beginSuitUp} only <em>reserves</em> the pieces it found
 * (one bit each in {@code transitionMask}); each one is moved from the inventory onto the body in the
 * same tick its stage fires, so a piece is always in exactly one place -- no duplication window, and
 * (unlike pulling all four out up front) no window in which a logout, death or crash mid-sequence
 * would destroy the suit, since the transition fields are transient and nothing would put it back.
 * The stack the player was carrying is the stack they end up wearing, so its stored charge,
 * enchantments and custom name survive; anything already occupying the slot is evicted to the
 * inventory rather than overwritten.
 */
public final class IronManSuitUpManager {
	// slot index used in transitionMask: 0 HEAD, 1 CHEST, 2 LEGS, 3 FEET
	private static final EquipmentSlot[] SLOT_BY_BIT = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private IronManSuitUpManager() {
	}

	// ---------------- entry points ----------------

	/** Right-click a suitcase / press the suit-toggle slot / {@code /ironman suit}. */
	public static boolean toggleSuit(ServerPlayer player, String suitId) {
		if (!TonyStark.hasPower(player)) {
			reject(player);
			return false;
		}
		if (IronManSuits.byId(suitId) == null) {
			return false;
		}
		if (IronManArmor.wearingAnyIronMan(player)) {
			String worn = IronManArmor.wornSuitId(player);
			return beginSuitDown(player, worn != null ? worn : suitId);
		}
		return beginSuitUp(player, suitId);
	}

	/**
	 * Equip the suit from the player's inventory as a staged sequence. Returns false (with a message)
	 * if the player is missing every piece or has not developed this mark.
	 */
	public static boolean beginSuitUp(ServerPlayer player, String suitId) {
		IronManSuit suit = IronManSuits.byId(suitId);
		if (suit == null || !TonyStark.hasPower(player)) {
			return false;
		}
		if (inTransition(player)) {
			return false;
		}
		// You can wear a suit you have developed OR simply have all four pieces of (creative, a gift,
		// a Hall of Armor you inherited) -- physically having the armour is authorisation enough, same
		// relaxation the summon path uses.
		if (!TonyStark.hasBuilt(player, suitId) && TonyStark.techLevel(player) < suit.techLevel()
				&& !hasAllFourPieces(player, suitId)) {
			player.displayClientMessage(Component.translatable("message.herocraft.ironman.not_developed",
					Component.translatable(suit.nameKey())), true);
			return false;
		}

		// The suit's charge travels with the armour: adopt it from the chestplate in the inventory (or
		// any piece, if the chestplate is already worn), so putting a suit on restores where it was --
		// not zero (spec "changes 9").
		if (!IronManArmor.isPieceWorn(player, EquipmentSlot.CHEST, suitId)) {
			ItemStack src = pieceInInventory(player, suitId, ArmorItem.Type.CHESTPLATE);
			if (src == null) {
				src = anyPieceInInventory(player, suitId);
			}
			if (src != null) {
				IronManEnergy.loadFromStack(player, suitId, src);
			}
		}

		// Only RESERVE the pieces here (one mask bit each); each one is actually taken out of the
		// inventory at the moment its stage fires, in tick(). Taking all four out up front looks
		// tidier but means that for the whole ~2 s sequence the armour exists in neither the
		// inventory nor a slot -- so a logout, death or crash mid-suit-up destroyed the suit outright
		// (transitionSuit/Mask are transient, so nothing put it back). Removing per stage keeps every
		// piece in exactly one place at every instant: no loss window, and no duplication window either.
		int mask = 0;
		int worn = 0;
		for (int bit = 0; bit < 4; bit++) {
			EquipmentSlot slot = SLOT_BY_BIT[bit];
			if (IronManArmor.isPieceWorn(player, slot, suitId)) {
				worn++;
				continue;
			}
			if (findInInventory(player, suitId, typeOf(slot)) >= 0) {
				mask |= (1 << bit);
			}
		}
		if (mask == 0 && worn == 0) {
			player.displayClientMessage(Component.translatable("message.herocraft.ironman.no_suit_available",
					Component.translatable(suit.nameKey())), true);
			return false;
		}

		TonyStark.setActiveSuit(player, suitId);
		TonyStarkState s = TonyStark.state(player);
		s.transitionSuit = suitId;
		s.transitionUp = true;
		s.transitionTotal = suit.suitUpType().durationTicks();
		s.transitionTicks = s.transitionTotal;
		s.transitionMask = mask;
		launchFx(player, suit, true);
		return true;
	}

	/**
	 * Fold the worn suit back off as a staged sequence (helmet first) -- storing it in the
	 * <b>main</b> inventory, never the hotbar (spec "changes 12"). Refuses up front, with a message
	 * and no side effects at all, if there is not one free main-inventory slot per piece being
	 * stored: better to leave the suit on than to strand a piece mid-sequence with nowhere to go.
	 */
	public static boolean beginSuitDown(ServerPlayer player, String suitId) {
		if (inTransition(player)) {
			return false;
		}
		IronManSuit suit = IronManSuits.byId(suitId);
		if (suit == null) {
			return false; // public entry point: an unknown id must not NPE on suitUpType() below
		}
		int mask = 0;
		int pieceCount = 0;
		for (int bit = 0; bit < 4; bit++) {
			if (IronManArmor.isPieceWorn(player, SLOT_BY_BIT[bit], suitId)) {
				mask |= (1 << bit);
				pieceCount++;
			}
		}
		if (mask == 0) {
			return false;
		}
		if (freeMainInventorySlots(player) < pieceCount) {
			player.displayClientMessage(Component.translatable("message.herocraft.ironman.no_space_to_store")
					.withStyle(ChatFormatting.RED), true);
			return false;
		}
		if (IronManFlight.isFlying(player)) {
			IronManFlight.setFlying(player, false);
		}
		TonyStarkState s = TonyStark.state(player);
		s.transitionSuit = suitId;
		s.transitionUp = false;
		s.transitionTotal = suit.suitUpType().durationTicks();
		s.transitionTicks = s.transitionTotal;
		s.transitionMask = mask;
		launchFx(player, suit, false);
		return true;
	}

	// ---------------- Mark 5 suitcase ("changes 15") ----------------

	/**
	 * The suitcase item for a suit that stows as one ({@code mark_v} &rarr; the Mark V Suitcase), or
	 * {@code null} for any other suit.
	 */
	private static net.minecraft.world.item.Item suitcaseItemFor(String suitId) {
		return "mark_v".equals(suitId) ? IronManItems.MARK_V_SUITCASE : null;
	}

	/**
	 * Sneak + C while wearing the Mark 5 ("changes 15"): fold the suit away into the Mark V Suitcase
	 * item rather than storing four pieces in the inventory. Runs the ordinary staged retract (helmet
	 * first), but each piece is folded into the case instead of dropped in the pack; the case (stamped
	 * with the suit's live charge) is handed over when the sequence finishes.
	 */
	public static boolean beginSuitDownToCase(ServerPlayer player, String suitId) {
		if (inTransition(player)) {
			return false;
		}
		IronManSuit suit = IronManSuits.byId(suitId);
		if (suit == null || suitcaseItemFor(suitId) == null) {
			return beginSuitDown(player, suitId); // no case for this mark -- fall back to the normal store
		}
		int mask = 0;
		for (int bit = 0; bit < 4; bit++) {
			if (IronManArmor.isPieceWorn(player, SLOT_BY_BIT[bit], suitId)) {
				mask |= (1 << bit);
			}
		}
		if (mask == 0) {
			return false;
		}
		if (freeMainInventorySlots(player) < 1 && !hasSuitcase(player, suitId)) {
			player.displayClientMessage(Component.translatable("message.herocraft.ironman.no_space_to_store")
					.withStyle(ChatFormatting.RED), true);
			return false;
		}
		if (IronManFlight.isFlying(player)) {
			IronManFlight.setFlying(player, false);
		}
		TonyStarkState s = TonyStark.state(player);
		s.transitionSuit = suitId;
		s.transitionUp = false;
		s.transitionToCase = true;
		s.transitionTotal = suit.suitUpType().durationTicks();
		s.transitionTicks = s.transitionTotal;
		s.transitionMask = mask;
		launchFx(player, suit, false);
		return true;
	}

	/**
	 * Right-click the Mark V Suitcase ("changes 15"): build the suit around the player from the case,
	 * chest first, ~4 s. The case is consumed up front and its stored charge loaded into the suit pool;
	 * the pieces are materialised stage by stage (there is nothing in the inventory to pull).
	 */
	public static boolean beginSuitUpFromCase(ServerPlayer player, String suitId) {
		IronManSuit suit = IronManSuits.byId(suitId);
		if (suit == null || !TonyStark.hasPower(player) || inTransition(player)) {
			return false;
		}
		if (IronManArmor.wearingAnyIronMan(player)) {
			return false;
		}
		int caseIdx = findSuitcase(player, suitId);
		if (caseIdx < 0) {
			return false;
		}
		ItemStack caseStack = player.getInventory().getItem(caseIdx);
		IronManEnergy.setEnergy(player, suitId, IronManEnergy.stackEnergy(caseStack, suitId));
		IronManEnergy.setIntegrity(player, suitId, IronManEnergy.stackIntegrity(caseStack, suitId));
		player.getInventory().removeItem(caseIdx, 1);

		TonyStark.setActiveSuit(player, suitId);
		TonyStarkState s = TonyStark.state(player);
		s.transitionSuit = suitId;
		s.transitionUp = true;
		s.transitionFromCase = true;
		s.transitionTotal = suit.suitUpType().durationTicks();
		s.transitionTicks = s.transitionTotal;
		s.transitionMask = 0b1111; // every slot materialises from the case
		launchFx(player, suit, true);
		return true;
	}

	private static void giveSuitcase(ServerPlayer player, String suitId) {
		net.minecraft.world.item.Item caseItem = suitcaseItemFor(suitId);
		if (caseItem == null) {
			return;
		}
		float energy = IronManEnergy.energy(player, suitId);
		float integrity = IronManEnergy.integrity(player, suitId);
		int existing = findSuitcase(player, suitId);
		ItemStack caseStack = existing >= 0 ? player.getInventory().getItem(existing) : new ItemStack(caseItem);
		IronManEnergy.stampStack(caseStack, energy, integrity);
		if (existing < 0 && !player.getInventory().add(caseStack)) {
			player.drop(caseStack, false);
		}
		player.displayClientMessage(Component.translatable("message.herocraft.ironman.folded_to_case",
				Component.translatable(IronManSuits.byId(suitId).nameKey())).withStyle(ChatFormatting.AQUA), true);
	}

	private static boolean hasSuitcase(ServerPlayer player, String suitId) {
		return findSuitcase(player, suitId) >= 0;
	}

	private static int findSuitcase(ServerPlayer player, String suitId) {
		net.minecraft.world.item.Item caseItem = suitcaseItemFor(suitId);
		if (caseItem == null) {
			return -1;
		}
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(caseItem)) {
				return i;
			}
		}
		return -1;
	}

	/** Equip a single piece delivered by a suit-part courier entity / the Suit Platform (no staging). */
	public static void receivePart(ServerPlayer player, String suitId, ArmorItem.Type type) {
		EquipmentSlot slot = slotFor(type);
		ItemStack current = player.getItemBySlot(slot);
		if (current.getItem() instanceof IronManArmorItem existing && existing.suitId().equals(suitId)) {
			return;
		}
		evictSlot(player, slot);
		player.setItemSlot(slot, new ItemStack(IronManItems.armor(suitId, type)));
		TonyStark.setActiveSuit(player, suitId);
		stageFx(player, slot, true, IronManSuits.byId(suitId));
		if (IronManArmor.wearingFullSuit(player, suitId)) {
			faceplateClose(player, suitId);
		}
	}

	// ---------------- the staged timer ----------------

	public static void tick(ServerPlayer player) {
		TonyStarkState s = TonyStark.state(player);
		if (s.transitionSuit.isEmpty() || s.transitionTicks <= 0) {
			if (!s.transitionSuit.isEmpty() && s.transitionTicks <= 0) {
				s.transitionSuit = "";
			}
			return;
		}
		IronManSuit suit = IronManSuits.byId(s.transitionSuit);
		ServerLevel level = (ServerLevel) player.level();

		s.transitionTicks--;
		float frac = s.transitionTotal <= 0 ? 1f : 1f - (float) s.transitionTicks / s.transitionTotal;
		boolean nanotech = suit != null && suit.suitUpType() == SuitUpType.NANOTECH;
		SuitUpType type = suit != null ? suit.suitUpType() : SuitUpType.MECHANICAL_REMOTE;

		// which bit reaches its stage at this fraction
		for (int bit = 0; bit < 4; bit++) {
			if ((s.transitionMask & (1 << bit)) == 0) {
				continue;
			}
			float threshold = stageThreshold(bit, s.transitionUp, type);
			if (frac >= threshold) {
				EquipmentSlot slot = SLOT_BY_BIT[bit];
				if (s.transitionUp) {
					ItemStack piece;
					if (s.transitionFromCase) {
						// Mark 5 ("changes 15"): the pieces are materialised from the Mark V Suitcase, which
						// was already consumed at the start -- there is nothing in the inventory to pull.
						piece = new ItemStack(IronManItems.armor(s.transitionSuit, typeOf(slot)));
					} else {
						// Take THIS piece out of the inventory now and wear that very stack, so its stored
						// charge / integrity / enchantments / custom name survive being put on, and so the
						// item is never in limbo between the two places. If it is gone (dropped, traded,
						// died mid-sequence) the stage is simply skipped rather than conjuring a new one.
						int idx = findInInventory(player, s.transitionSuit, typeOf(slot));
						if (idx < 0) {
							s.transitionMask &= ~(1 << bit);
							continue;
						}
						piece = player.getInventory().removeItem(idx, 1);
					}
					evictSlot(player, slot);
					player.setItemSlot(slot, piece);
				} else {
					ItemStack removed = player.getItemBySlot(slot);
					player.setItemSlot(slot, ItemStack.EMPTY);
					if (removed.getItem() instanceof IronManArmorItem && !s.transitionToCase) {
						ItemStack out = removed.copy();
						// Suit charge follows the armour off the body (spec "changes 9").
						IronManEnergy.stampStack(out, IronManEnergy.energy(player, s.transitionSuit),
								IronManEnergy.integrity(player, s.transitionSuit));
						// Main inventory only, never the hotbar (spec "changes 12") -- beginSuitDown
						// already verified enough room, so this should always succeed; the drop
						// fallback only guards an inventory that changed mid-sequence (trading, etc).
						if (!addToMainInventoryOnly(player, out)) {
							player.drop(out, false);
						}
					}
					// transitionToCase: the piece is simply folded away -- the Mark V Suitcase item handed
					// over at the end of the sequence represents the whole stowed suit.
				}
				s.transitionMask &= ~(1 << bit);
				stageFx(player, slot, s.transitionUp, suit);
			}
		}

		// continuous swirl
		ParticleOptions swirl = nanotech ? ParticleTypes.WAX_OFF : ParticleTypes.END_ROD;
		double h = player.getBbHeight();
		level.sendParticles(swirl, player.getX(), player.getY() + h * frac, player.getZ(),
				4, 0.35, 0.15, 0.35, 0.02);

		if (s.transitionTicks <= 0) {
			s.transitionMask = 0;
			if (s.transitionUp) {
				if (IronManArmor.wearingFullSuit(player, s.transitionSuit)) {
					faceplateClose(player, s.transitionSuit);
				}
			} else {
				if (s.transitionToCase) {
					giveSuitcase(player, s.transitionSuit);
				}
				if (!IronManArmor.wearingAnyIronMan(player)) {
					TonyStark.setActiveSuit(player, "");
				}
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.NETHERITE_BLOCK_BREAK, SoundSource.PLAYERS, 0.8f, 0.9f);
			}
			s.transitionToCase = false;
			s.transitionFromCase = false;
			s.transitionSuit = "";
		}
	}

	// ---------------- FX ----------------

	private static float stageThreshold(int bit, boolean up, SuitUpType type) {
		// bit: 0 HEAD 1 CHEST 2 LEGS 3 FEET
		boolean nanotech = type == SuitUpType.NANOTECH;
		if (up) {
			if (nanotech) {
				return switch (bit) { case 1 -> 0.10f; case 2 -> 0.40f; case 3 -> 0.40f; default -> 0.75f; };
			}
			if (type == SuitUpType.SUITCASE_MOVIE) {
				// chest first, then legs, then feet, then helmet last ("changes 15")
				return switch (bit) { case 1 -> 0.10f; case 2 -> 0.45f; case 3 -> 0.60f; default -> 0.85f; };
			}
			return switch (bit) { case 3 -> 0.15f; case 2 -> 0.35f; case 1 -> 0.60f; default -> 0.85f; };
		}
		// suit-down: helmet retracts first
		if (nanotech) {
			return switch (bit) { case 0 -> 0.15f; case 3 -> 0.45f; case 2 -> 0.45f; default -> 0.80f; };
		}
		if (type == SuitUpType.SUITCASE_MOVIE) {
			return switch (bit) { case 0 -> 0.10f; case 3 -> 0.40f; case 2 -> 0.55f; default -> 0.85f; };
		}
		return switch (bit) { case 0 -> 0.15f; case 1 -> 0.40f; case 2 -> 0.65f; default -> 0.85f; };
	}

	private static void stageFx(ServerPlayer player, EquipmentSlot slot, boolean up, IronManSuit suit) {
		ServerLevel level = (ServerLevel) player.level();
		double y = switch (slot) {
			case FEET -> player.getY() + 0.2;
			case LEGS -> player.getY() + 0.7;
			case CHEST -> player.getY() + 1.2;
			default -> player.getY() + 1.6;
		};
		boolean nanotech = suit != null && suit.suitUpType() == SuitUpType.NANOTECH;
		level.sendParticles(nanotech ? ParticleTypes.SCRAPE : ParticleTypes.ELECTRIC_SPARK,
				player.getX(), y, player.getZ(), 18, 0.35, 0.15, 0.35, 0.12);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), y, player.getZ(), 6, 0.25, 0.1, 0.25, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				up ? SoundEvents.NETHERITE_BLOCK_PLACE : SoundEvents.NETHERITE_BLOCK_HIT,
				SoundSource.PLAYERS, 0.7f, up ? 1.1f : 0.9f);
	}

	private static void launchFx(ServerPlayer player, IronManSuit suit, boolean up) {
		ServerLevel level = (ServerLevel) player.level();
		boolean nanotech = suit != null && suit.suitUpType() == SuitUpType.NANOTECH;
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				nanotech ? SoundEvents.CONDUIT_ACTIVATE : SoundEvents.NETHERITE_BLOCK_FALL, SoundSource.PLAYERS, 1.0f, 0.9f);
		level.sendParticles(nanotech ? ParticleTypes.WAX_OFF : ParticleTypes.CRIT,
				player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.9, 0.4, 0.1);
	}

	private static void faceplateClose(ServerPlayer player, String suitId) {
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 0.9f, 0.7f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7f, 1.6f);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.6, player.getZ(),
				12, 0.2, 0.1, 0.2, 0.05);
		player.displayClientMessage(Component.translatable("message.herocraft.ironman.suit_online",
				Component.translatable("herocraft.ironman.suit." + suitId + ".name")).withStyle(ChatFormatting.AQUA), true);
	}

	// ---------------- helpers ----------------

	public static boolean inTransition(ServerPlayer player) {
		return !TonyStark.state(player).transitionSuit.isEmpty();
	}

	/**
	 * Empty an armour slot back into the player's inventory (dropping only if it will not fit) before
	 * something else is put there. Without this, suiting up over ordinary armour silently deleted it --
	 * {@link ServerPlayer#setItemSlot} overwrites, it does not return what was there. An Iron Man piece
	 * being evicted also gets its live charge / integrity stamped onto the outgoing stack, so taking a
	 * suit off this way loses nothing either.
	 */
	private static void evictSlot(ServerPlayer player, EquipmentSlot slot) {
		ItemStack current = player.getItemBySlot(slot);
		if (current.isEmpty()) {
			return;
		}
		ItemStack out = current.copy();
		boolean foreign = !(current.getItem() instanceof IronManArmorItem);
		if (current.getItem() instanceof IronManArmorItem evicted) {
			IronManEnergy.stampStack(out, IronManEnergy.energy(player, evicted.suitId()),
					IronManEnergy.integrity(player, evicted.suitId()));
		}
		player.setItemSlot(slot, ItemStack.EMPTY);
		// "changes 15": calling a suit over your existing armour automatically takes that armour off
		// and puts it in your inventory as each Iron Man piece arrives -- tell the player which.
		boolean toInventory = addToMainInventoryOnly(player, out) || player.getInventory().add(out);
		if (!toInventory) {
			player.drop(out, false);
		}
		if (foreign) {
			player.displayClientMessage(Component.translatable(
					toInventory ? "message.herocraft.ironman.armour_stowed" : "message.herocraft.ironman.armour_dropped",
					out.getHoverName()).withStyle(ChatFormatting.GRAY), true);
		}
	}

	/**
	 * {@code Inventory.items} indices 9..35 -- the 27 main-inventory slots, deliberately excluding the
	 * hotbar (0..8). Armour pieces are always max-stack-1 ({@code Item.Properties#durability} implies
	 * {@code stacksTo(1)}), so "free space" only ever means an empty slot, never a mergeable stack.
	 */
	private static final int MAIN_INV_START = 9;
	private static final int MAIN_INV_END = 36; // exclusive

	private static int freeMainInventorySlots(ServerPlayer player) {
		var items = player.getInventory().items;
		int free = 0;
		for (int i = MAIN_INV_START; i < MAIN_INV_END; i++) {
			if (items.get(i).isEmpty()) {
				free++;
			}
		}
		return free;
	}

	/** Places {@code stack} into the first empty MAIN-inventory slot (never the hotbar). False = no room. */
	private static boolean addToMainInventoryOnly(ServerPlayer player, ItemStack stack) {
		var items = player.getInventory().items;
		for (int i = MAIN_INV_START; i < MAIN_INV_END; i++) {
			if (items.get(i).isEmpty()) {
				items.set(i, stack);
				return true;
			}
		}
		return false;
	}

	private static void reject(ServerPlayer player) {
		player.displayClientMessage(Component.translatable("message.herocraft.ironman.armor_rejects")
				.withStyle(ChatFormatting.RED), true);
	}

	private static ItemStack pieceInInventory(ServerPlayer player, String suitId, ArmorItem.Type type) {
		int idx = findInInventory(player, suitId, type);
		return idx < 0 ? null : player.getInventory().getItem(idx);
	}

	private static boolean hasAllFourPieces(ServerPlayer player, String suitId) {
		for (ArmorItem.Type type : new ArmorItem.Type[] {
				ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS }) {
			if (findInInventory(player, suitId, type) < 0
					&& !IronManArmor.isPieceWorn(player, slotFor(type), suitId)) {
				return false;
			}
		}
		return true;
	}

	private static ItemStack anyPieceInInventory(ServerPlayer player, String suitId) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.getItem() instanceof IronManArmorItem piece && piece.suitId().equals(suitId)) {
				return stack;
			}
		}
		return null;
	}

	private static int findInInventory(ServerPlayer player, String suitId, ArmorItem.Type type) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.getItem() instanceof IronManArmorItem piece
					&& piece.suitId().equals(suitId) && piece.getType() == type) {
				return i;
			}
		}
		return -1;
	}

	private static ArmorItem.Type typeOf(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> ArmorItem.Type.HELMET;
			case CHEST -> ArmorItem.Type.CHESTPLATE;
			case LEGS -> ArmorItem.Type.LEGGINGS;
			default -> ArmorItem.Type.BOOTS;
		};
	}

	public static EquipmentSlot slotFor(ArmorItem.Type type) {
		return switch (type) {
			case HELMET -> EquipmentSlot.HEAD;
			case CHESTPLATE -> EquipmentSlot.CHEST;
			case LEGGINGS -> EquipmentSlot.LEGS;
			case BOOTS -> EquipmentSlot.FEET;
			default -> EquipmentSlot.CHEST;
		};
	}
}
