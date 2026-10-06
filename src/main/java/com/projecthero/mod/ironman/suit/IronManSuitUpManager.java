package com.projecthero.mod.ironman.suit;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.item.SuitcaseContents;

import net.minecraft.ChatFormatting;
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
 * The reusable suit-up / suit-down system (spec sections 22-27, 36). One implementation; the {@link SuitUpType} on the
 * {@link IronManSuit} definition selects the stage order and timing.
 *
 * <h2>Staging</h2>
 * The armour pieces are equipped (or removed) <em>one stage at a time</em> from a per-player timer in {@link #tick}.
 * v0.14.21: each stage is now animated for every viewer through {@link IronManSuitFx}: a piece that reaches the body
 * <b>locks on</b> over {@link IronManSuitFx#LOCK_TICKS} (its plates sweep into place behind a spark edge and the
 * GeckoLib {@code suit_lock_on} clip slides each plate home), and a piece coming off first <b>breaks away</b> over
 * {@link IronManSuitFx#RELEASE_TICKS} (the reverse) and only then leaves the slot. The body pose (arms out, the
 * faceplate-close beat) runs off the same synced clocks.
 *
 * <h2>Item identity</h2>
 * Server-authoritative throughout, and the real {@link ItemStack} always travels: {@link #beginSuitUp} only
 * <em>reserves</em> pieces, each is moved from the inventory onto the body in the very tick its stage fires, so a piece
 * is always in exactly one place (no loss window on logout / death / crash, no duplication window) and keeps its
 * charge, enchantments, custom name and every other component. Couriers, the delivery pod and the Suit Platform hand
 * their real stacks to {@link #receivePart}. The Mark V Suitcase now stores the four real stacks
 * ({@link SuitcaseContents}); a piece moves between case and body in its stage's tick, so the case and the body
 * between them always hold the whole suit.
 */
public final class IronManSuitUpManager {
	// slot index used in transitionMask: 0 HEAD, 1 CHEST, 2 LEGS, 3 FEET
	private static final EquipmentSlot[] SLOT_BY_BIT = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private IronManSuitUpManager() {
	}

	// ---------------- entry points ----------------

	/** Right-click a suitcase / press the suit-toggle slot. */
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

	/** Length of a whole staged sequence: the mark's stage timeline plus the last piece's lock-on / release. */
	public static int sequenceTicks(SuitUpType type, boolean up) {
		return type.durationTicks() + (up ? IronManSuitFx.LOCK_TICKS : IronManSuitFx.RELEASE_TICKS);
	}

	/**
	 * Equip the suit from the player's inventory as a staged sequence. Returns false (with a message) if the player is
	 * missing every piece or has not developed this mark.
	 */
	public static boolean beginSuitUp(ServerPlayer player, String suitId) {
		IronManSuit suit = IronManSuits.byId(suitId);
		if (suit == null || !TonyStark.hasPower(player)) {
			return false;
		}
		if (inTransition(player)) {
			return false;
		}
		if (suit.summonType() == SummonType.SUITCASE_ITEM) {
			// v0.14.29: the Mark 5 only ever suits up by right-clicking its suitcase (beginSuitUpFromCase)
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.mk5_use_case")
					.withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		// You can wear a suit you have developed OR simply have all four pieces of (creative, a gift, a Hall of Armor
		// you inherited) -- physically having the armour is authorisation enough.
		if (!TonyStark.hasBuilt(player, suitId) && TonyStark.techLevel(player) < suit.techLevel()
				&& !hasAllFourPieces(player, suitId)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.not_developed",
					Component.translatable(suit.nameKey())), true);
			return false;
		}

		// The suit's charge travels with the armour: adopt it from the chestplate in the inventory (or any piece, if the
		// chestplate is already worn), so putting a suit on restores where it was (spec "changes 9").
		if (!IronManArmor.isPieceWorn(player, EquipmentSlot.CHEST, suitId)) {
			ItemStack src = pieceInInventory(player, suitId, ArmorItem.Type.CHESTPLATE);
			if (src == null) {
				src = anyPieceInInventory(player, suitId);
			}
			if (src != null) {
				IronManEnergy.loadFromStack(player, suitId, src);
			}
		}

		// Only RESERVE the pieces here (one mask bit each); each is taken out of the inventory in the tick its stage
		// fires -- see the class doc.
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
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.no_suit_available",
					Component.translatable(suit.nameKey())), true);
			return false;
		}

		TonyStark.setActiveSuit(player, suitId);
		start(player, suit, true, mask, false, false);
		return true;
	}

	/**
	 * Fold the worn suit back off as a staged sequence (helmet first) -- storing it in the <b>main</b> inventory, never
	 * the hotbar (spec "changes 12"). Refuses up front, with a message and no side effects at all, if there is not one
	 * free main-inventory slot per piece being stored.
	 */
	public static boolean beginSuitDown(ServerPlayer player, String suitId) {
		if (inTransition(player)) {
			return false;
		}
		IronManSuit suit = IronManSuits.byId(suitId);
		if (suit == null) {
			return false;
		}
		int mask = wornMask(player, suitId);
		if (mask == 0) {
			return false;
		}
		if (freeMainInventorySlots(player) < Integer.bitCount(mask)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.no_space_to_store")
					.withStyle(ChatFormatting.RED), true);
			return false;
		}
		if (IronManFlight.isFlying(player)) {
			IronManFlight.setFlying(player, false);
		}
		start(player, suit, false, mask, false, false);
		return true;
	}

	// ---------------- Mark 5 suitcase ("changes 15", real contents v0.14.21) ----------------

	/** The suitcase item for a suit that stows as one ({@code mark_v}), or {@code null} for any other suit. */
	private static net.minecraft.world.item.Item suitcaseItemFor(String suitId) {
		return "mark_v".equals(suitId) ? IronManItems.MARK_V_SUITCASE : null;
	}

	/**
	 * C (or right-click the case) while wearing the Mark 5: fold the suit away into the Mark V Suitcase rather than
	 * storing four pieces in the inventory. v0.14.29: a 4 s fold ({@link IronManMk5Suitcase#DOWN_TICKS}); the pieces
	 * stay in their armour slots (hidden once they have come apart) until the very last tick, when all of them move into
	 * a fresh case in one go ({@link #packIntoCase}) -- so a logout / death mid-fold can never lose or duplicate one.
	 */
	public static boolean beginSuitDownToCase(ServerPlayer player, String suitId) {
		if (inTransition(player)) {
			return false;
		}
		IronManSuit suit = IronManSuits.byId(suitId);
		if (suit == null || suitcaseItemFor(suitId) == null) {
			return beginSuitDown(player, suitId); // no case for this mark -- fall back to the normal store
		}
		int mask = wornMask(player, suitId);
		if (mask == 0) {
			return false;
		}
		if (IronManFlight.isFlying(player)) {
			IronManFlight.setFlying(player, false);
		}
		start(player, suit, false, mask, true, false);
		return true;
	}

	/**
	 * Right-click the Mark V Suitcase: build the suit around the player out of the case -- v0.14.29: 6 s, the case held
	 * out first, then chest, arms, legs, head, faceplate ({@link IronManMk5Suitcase}). The case
	 * stays in the inventory until its last piece has come out; each stage takes that piece out of the case and onto
	 * the body in one tick. A legacy case (never had a suit folded into it) is first filled with a fresh Mark V.
	 */
	public static boolean beginSuitUpFromCase(ServerPlayer player, String suitId) {
		IronManSuit suit = IronManSuits.byId(suitId);
		if (suit == null || !TonyStark.hasPower(player) || inTransition(player)) {
			return false;
		}
		if (IronManArmor.wearingAnyIronMan(player)) {
			return false;
		}
		int caseIdx = findDeployableCase(player, suitId);
		if (caseIdx < 0) {
			return false;
		}
		ItemStack caseStack = player.getInventory().getItem(caseIdx);
		if (SuitcaseContents.isLegacyEmpty(caseStack)) {
			net.minecraft.core.NonNullList<ItemStack> fresh =
					net.minecraft.core.NonNullList.withSize(SuitcaseContents.SLOTS, ItemStack.EMPTY);
			for (int bit = 0; bit < 4; bit++) {
				fresh.set(bit, new ItemStack(IronManItems.armor(suitId, typeOf(SLOT_BY_BIT[bit]))));
			}
			SuitcaseContents.write(caseStack, fresh);
		}
		IronManEnergy.setEnergy(player, suitId, IronManEnergy.stackEnergy(caseStack, suitId));
		IronManEnergy.setIntegrity(player, suitId, IronManEnergy.stackIntegrity(caseStack, suitId));
		int mask = 0;
		var slots = SuitcaseContents.read(caseStack);
		for (int bit = 0; bit < 4; bit++) {
			if (!slots.get(bit).isEmpty()) {
				mask |= 1 << bit;
			}
		}
		TonyStark.setActiveSuit(player, suitId);
		start(player, suit, true, mask, false, true);
		IronManSounds.play(player, IronManSounds.CASE_UNFOLD, 1.0f, 1.0f);
		return true;
	}

	/**
	 * v0.14.27: the order a sequential suit-up builds its pieces in -- boots, leggings, chestplate, helmet (bits 3, 2, 1,
	 * 0) -- one after another, each over {@link IronManSuitFx#BUILD_TICKS}.
	 */
	public static final int[] BUILD_ORDER = { 3, 2, 1, 0 };

	/** v0.14.27: length of a sequential suit-up of {@code pieces} pieces: 3 s each, plus the first tick. */
	public static int buildSequenceTicks(int pieces) {
		return Math.max(1, pieces * IronManSuitFx.BUILD_TICKS + 1);
	}

	/**
	 * v0.14.27: the sequence tick at which piece {@code bit} of a sequential suit-up reaches the body and starts building:
	 * the first planned piece at tick 1, each later one {@link IronManSuitFx#BUILD_TICKS} after the one before. -1 if the
	 * piece is not in {@code plan}.
	 */
	public static int buildStageTick(int bit, int plan) {
		return stageTickIn(BUILD_ORDER, bit, plan);
	}

	/**
	 * v0.14.28: the order a C suit-down un-builds its pieces in -- helmet, chestplate, leggings, boots (bits 0..3) --
	 * each over {@link IronManSuitFx#BUILD_TICKS}, exactly as long as putting it on took.
	 */
	public static final int[] UNBUILD_ORDER = { 0, 1, 2, 3 };

	/** v0.14.28: the sequence tick at which piece {@code bit} of a suit-down starts un-building (-1 if not in {@code plan}). */
	public static int unbuildStageTick(int bit, int plan) {
		return stageTickIn(UNBUILD_ORDER, bit, plan);
	}

	/** v0.14.28: length of a suit-down of {@code pieces} pieces: the same 3 s each as the suit-up. */
	public static int unbuildSequenceTicks(int pieces) {
		return buildSequenceTicks(pieces);
	}

	private static int stageTickIn(int[] order, int bit, int plan) {
		if ((plan & (1 << bit)) == 0) {
			return -1;
		}
		int before = 0;
		for (int b : order) {
			if (b == bit) {
				break;
			}
			if ((plan & (1 << b)) != 0) {
				before++;
			}
		}
		return 1 + before * IronManSuitFx.BUILD_TICKS;
	}

	/** Shared start of every staged sequence: transition state + the synced pose clock + the launch FX. */
	private static void start(ServerPlayer player, IronManSuit suit, boolean up, int mask, boolean toCase, boolean fromCase) {
		TonyStarkState s = TonyStark.state(player);
		s.transitionSuit = suit.id();
		s.transitionUp = up;
		s.transitionToCase = toCase;
		s.transitionFromCase = fromCase;
		// v0.14.27: an ordinary suit-up builds the pieces one after another, 3 s each, and completes only once the last
		// one is fully built; the Mark V case build and every suit-down keep the staged timeline
		// v0.14.28: ...and an ordinary C suit-down un-builds them one after another, 3 s each, helmet first -- exactly as
		// long as the suit-up (the Mark V fold into the case keeps its quick staged timeline)
		boolean sequential = up ? !fromCase : !toCase;
		s.transitionPlan = sequential ? mask : 0;
		s.transitionTotal = sequential ? buildSequenceTicks(Integer.bitCount(mask)) : sequenceTicks(suit.suitUpType(), up);
		s.transitionTicks = s.transitionTotal;
		s.transitionMask = mask;
		s.transitionReleaseMask = 0;
		boolean casePose = toCase || fromCase;
		if (casePose) {
			// v0.14.29: the Mark 5 suitcase build (6 s) / fold (4 s) -- its own per-piece timetable, pose and style
			s.transitionTotal = up ? IronManMk5Suitcase.UP_TICKS : IronManMk5Suitcase.DOWN_TICKS;
			s.transitionTicks = s.transitionTotal;
			IronManSuitFx.startPose(player, up ? IronManSuitFx.POSE_MK5_UP : IronManSuitFx.POSE_MK5_DOWN,
					s.transitionTotal, IronManSuitFx.STYLE_MK5, 0);
			launchFx(player, up);
			return;
		}
		int kind = up ? (casePose ? IronManSuitFx.POSE_CASE_UP : IronManSuitFx.POSE_SUIT_UP)
				: (casePose ? IronManSuitFx.POSE_CASE_DOWN : IronManSuitFx.POSE_SUIT_DOWN);
		int style = casePose ? IronManSuitFx.STYLE_CASE : up ? IronManSuitFx.STYLE_PLATES : IronManSuitFx.STYLE_UNBUILD;
		// v0.14.28: no random pose variant any more -- the pose is a pure function of the piece building right now
		IronManSuitFx.startPose(player, kind, s.transitionTotal + (up ? IronManSuitFx.FACEPLATE_TICKS : 0), style, 0);
		launchFx(player, up);
	}

	static int findDeployableCase(ServerPlayer player, String suitId) {
		net.minecraft.world.item.Item caseItem = suitcaseItemFor(suitId);
		if (caseItem == null) {
			return -1;
		}
		var inv = player.getInventory();
		// prefer the case in hand, then any case that actually holds pieces, then a legacy one
		if (inv.selected >= 0 && inv.selected < 9 && deployable(inv.getItem(inv.selected), caseItem)) {
			return inv.selected;
		}
		int legacy = -1;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(caseItem) && !SuitcaseContents.isLegacyEmpty(st) && !SuitcaseContents.isEmpty(st)) {
				return i;
			}
			if (legacy < 0 && st.is(caseItem) && SuitcaseContents.isLegacyEmpty(st)) {
				legacy = i;
			}
		}
		return legacy;
	}

	private static boolean deployable(ItemStack st, net.minecraft.world.item.Item caseItem) {
		return st.is(caseItem) && (SuitcaseContents.isLegacyEmpty(st) || !SuitcaseContents.isEmpty(st));
	}

	/** A case the next fold can go into: an empty modern one (never a legacy case -- that already stands for a suit). */
	private static int findFoldTargetCase(ServerPlayer player, String suitId) {
		net.minecraft.world.item.Item caseItem = suitcaseItemFor(suitId);
		if (caseItem == null) {
			return -1;
		}
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(caseItem) && !SuitcaseContents.isLegacyEmpty(st) && SuitcaseContents.isEmpty(st)) {
				return i;
			}
		}
		// a partly filled case whose free slots cover what is being folded also works (an interrupted fold)
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(caseItem) && !SuitcaseContents.isLegacyEmpty(st)) {
				var slots = SuitcaseContents.read(st);
				boolean fits = true;
				for (int bit = 0; bit < 4; bit++) {
					if (IronManArmor.isPieceWorn(player, SLOT_BY_BIT[bit], suitId) && !slots.get(bit).isEmpty()) {
						fits = false;
					}
				}
				if (fits) {
					return i;
				}
			}
		}
		return -1;
	}

	/**
	 * Equip a single piece delivered by a suit-part courier / the delivery pod / the Suit Platform. {@code piece} is the
	 * real stack and is consumed only if it was equipped; returns false (stack untouched) if that slot already holds
	 * this suit's piece -- the caller then keeps the stack or stores it, it is never lost.
	 */
	public static boolean receivePart(ServerPlayer player, ItemStack piece) {
		return receivePart(player, piece, true);
	}

	/**
	 * v0.15.1: as {@link #receivePart(ServerPlayer, ItemStack)}; {@code buildOn} false = the piece was fitted whole (the Suit
	 * Platform's robotic arms carry it onto the body), so it shows complete at once instead of building itself on.
	 */
	public static boolean receivePart(ServerPlayer player, ItemStack piece, boolean buildOn) {
		if (!(piece.getItem() instanceof IronManArmorItem armor)) {
			return false;
		}
		String suitId = armor.suitId();
		EquipmentSlot slot = slotFor(armor.getType());
		ItemStack current = player.getItemBySlot(slot);
		if (current.getItem() instanceof IronManArmorItem existing && existing.suitId().equals(suitId)) {
			return false;
		}
		evictSlot(player, slot);
		player.setItemSlot(slot, piece.copyAndClear());
		TonyStark.setActiveSuit(player, suitId);
		IronManSuitFx fxNow = IronManSuitFx.of(player);
		if (!buildOn) {
			// no lock-on clock: drawn whole straight away (and any stale clock on that slot is cleared)
			player.setAttached(ModAttachments.IRON_MAN_SUIT_FX, fxNow.withPiece(IronManSuitFx.bit(slot), 0L, true));
			stageFx(player, slot, true);
			if (IronManArmor.wearingFullSuit(player, suitId) && !inTransition(player)) {
				faceplateClose(player, suitId);
			}
			return true;
		}
		if (fxNow.mk5()) {
			// v0.14.29: a leftover Mark 5 style must not time this piece's ordinary build-on
			player.setAttached(ModAttachments.IRON_MAN_SUIT_FX, fxNow.withPose(fxNow.poseKind(), fxNow.poseStart(),
					fxNow.poseTicks(), IronManSuitFx.STYLE_PLATES));
		}
		IronManSuitFx.markPiece(player, slot, true);
		stageFx(player, slot, true);
		if (IronManArmor.wearingFullSuit(player, suitId) && !inTransition(player)) {
			faceplateClose(player, suitId);
		}
		return true;
	}

	// ---------------- the staged timer ----------------

	public static void tick(ServerPlayer player) {
		TonyStarkState s = TonyStark.state(player);
		if (s.transitionSuit.isEmpty()) {
			// v0.15.1: a Suit Platform deploy's movement lock never outlives the suit-up it belongs to
			com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity.releaseStrayFreeze(player);
		}
		if (s.transitionSuit.isEmpty() || s.transitionTicks <= 0) {
			if (!s.transitionSuit.isEmpty() && s.transitionTicks <= 0) {
				s.transitionSuit = "";
			}
			return;
		}
		IronManSuit suit = IronManSuits.byId(s.transitionSuit);
		ServerLevel level = (ServerLevel) player.level();
		SuitUpType type = suit != null ? suit.suitUpType() : SuitUpType.MECHANICAL_REMOTE;

		s.transitionTicks--;
		int elapsed = s.transitionTotal - s.transitionTicks;

		boolean sequential = s.transitionPlan != 0;
		// v0.14.29: the Mark 5 suitcase build / fold runs on its own per-piece timetable
		boolean mk5 = s.transitionFromCase || s.transitionToCase;
		int releaseTicks = sequential ? IronManSuitFx.BUILD_TICKS : IronManSuitFx.RELEASE_TICKS;
		for (int bit = 0; bit < 4; bit++) {
			EquipmentSlot slot = SLOT_BY_BIT[bit];
			int stage = mk5 ? (s.transitionUp ? IronManMk5Suitcase.upStart(bit) : IronManMk5Suitcase.downStart(bit))
					: !sequential ? stageTick(bit, s.transitionUp, type)
					: s.transitionUp ? buildStageTick(bit, s.transitionPlan) : unbuildStageTick(bit, s.transitionPlan);
			int buildTicks = mk5 ? IronManMk5Suitcase.upWindow(bit) : IronManSuitFx.BUILD_TICKS;
			if ((sequential || mk5) && s.transitionUp && stage >= 0 && elapsed == stage + buildTicks - 1
					&& player.getItemBySlot(slot).getItem() instanceof IronManArmorItem) {
				// v0.14.27: the piece has finished building itself on -- it clamps home (sparks + the clamp sound)
				stageFx(player, slot, true);
			}
			if (stage < 0) {
				continue;
			}
			// 1. a piece whose stage has come round
			if ((s.transitionMask & (1 << bit)) != 0 && elapsed >= stage) {
				s.transitionMask &= ~(1 << bit);
				if (s.transitionUp) {
					ItemStack piece = takeForSuitUp(player, s, slot);
					if (piece.isEmpty()) {
						continue; // gone (dropped, traded, died) -- skip the stage rather than conjure one
					}
					evictSlot(player, slot);
					player.setItemSlot(slot, piece);
					IronManSuitFx.markPiece(player, slot, true);
					if (sequential || mk5) {
						// v0.14.27: the piece starts building on -- a servo whirr now, the clamp when it is done
						IronManSounds.play(player, IronManSounds.SERVO, 0.7f, 0.85f + bit * 0.08f);
					} else {
						stageFx(player, slot, true);
					}
				} else if (player.getItemBySlot(slot).getItem() instanceof IronManArmorItem) {
					// suit-down: the plates break away first; the piece leaves the slot when that finishes
					s.transitionReleaseMask |= 1 << bit;
					IronManSuitFx.markPiece(player, slot, false);
					IronManSounds.play(player, IronManSounds.RELEASE, 0.7f, 1.0f + bit * 0.05f);
				}
			}
			// 2. a released piece whose break-away has finished (or the sequence is ending). v0.14.29: not the Mark 5 fold
			// -- its pieces all move into the case together when it finishes (packIntoCase)
			if (!s.transitionToCase && (s.transitionReleaseMask & (1 << bit)) != 0
					&& (elapsed >= stage + releaseTicks || s.transitionTicks <= 0)) {
				s.transitionReleaseMask &= ~(1 << bit);
				removeForSuitDown(player, s, slot);
				stageFx(player, slot, false);
			}
		}

		// v0.14.27: no more continuous particle sweep up / down the body (its end rods drifted on long after the suit
		// was on) -- just the quiet servo ticking while the sequence runs
		if (elapsed % 10 == 0 && s.transitionTicks > 0) {
			IronManSounds.play(player, IronManSounds.SERVO, 0.3f, 1.1f + level.random.nextFloat() * 0.3f);
		}

		if (s.transitionTicks <= 0 && s.transitionUp && s.transitionPlan == 0 && !s.transitionFromCase
				&& stillBuilding(player)) {
			// v0.14.27: a suit-up whose pieces arrived some other way (couriers, the pod, the platform) completes only
			// once the last piece has finished building itself on
			s.transitionTicks = 1;
			return;
		}
		if (s.transitionTicks <= 0) {
			finish(player, s);
		}
	}

	/** A suit-up pose is still running and some piece is still building itself on (synced clock, real game time). */
	private static boolean stillBuilding(ServerPlayer player) {
		IronManSuitFx fx = IronManSuitFx.of(player);
		return fx.poseKind() != IronManSuitFx.POSE_NONE && fx.anyBuilding(player.level().getGameTime());
	}

	private static void finish(ServerPlayer player, TonyStarkState s) {
		ServerLevel level = (ServerLevel) player.level();
		s.transitionMask = 0;
		if (s.transitionUp) {
			if (s.transitionFromCase) {
				discardEmptyCase(player, s.transitionSuit);
			}
			if (IronManArmor.wearingFullSuit(player, s.transitionSuit)) {
				faceplateClose(player, s.transitionSuit);
			}
			if (!s.transitionFromCase) {
				// v0.14.28: the suit is on -- drop the pose clock now (the body pose itself already eases out within
				// 0.25 s of the last piece finishing, see IronManSuitPoses), so nothing holds the suit-up pose afterwards
				IronManSuitFx.endPose(player);
			}
		} else {
			if (s.transitionToCase) {
				packIntoCase(player, s.transitionSuit); // v0.14.29: the whole suit becomes the case in one tick
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.NETHERITE_BLOCK_BREAK, SoundSource.PLAYERS, 0.6f, 0.9f);
			IronManSounds.play(player, IronManSounds.SUIT_STORED, 0.8f, IronManSounds.markPitch(s.transitionSuit)); // v0.14.31
			IronManSuitFx.endPose(player);
		}
		s.transitionToCase = false;
		s.transitionFromCase = false;
		s.transitionReleaseMask = 0;
		s.transitionPlan = 0;
		s.transitionSuit = "";
		// v0.14.28: AFTER the transition is cleared -- setActiveSuit saves a copy of the state, so calling it earlier
		// carried the still-running transition into the new state object and the suit-down ended one tick late
		if (!s.transitionUp && !IronManArmor.wearingAnyIronMan(player)) {
			TonyStark.setActiveSuit(player, "");
		}
	}

	/** Suit-up: the real stack for this slot -- out of the suitcase (Mark V) or the inventory. EMPTY if it is gone. */
	private static ItemStack takeForSuitUp(ServerPlayer player, TonyStarkState s, EquipmentSlot slot) {
		if (s.transitionFromCase) {
			int idx = findCaseHolding(player, s.transitionSuit, typeOf(slot));
			if (idx < 0) {
				return ItemStack.EMPTY;
			}
			ItemStack caseStack = player.getInventory().getItem(idx);
			return SuitcaseContents.take(caseStack, typeOf(slot));
		}
		int idx = findInInventory(player, s.transitionSuit, typeOf(slot));
		return idx < 0 ? ItemStack.EMPTY : player.getInventory().removeItem(idx, 1);
	}

	/** Suit-down: take the piece off the body into the case (Mark V) or the main inventory, charge stamped on. */
	private static void removeForSuitDown(ServerPlayer player, TonyStarkState s, EquipmentSlot slot) {
		ItemStack removed = player.getItemBySlot(slot);
		if (!(removed.getItem() instanceof IronManArmorItem)) {
			return;
		}
		ItemStack out = removed.copy();
		IronManEnergy.stampStack(out, IronManEnergy.energy(player, s.transitionSuit),
				IronManEnergy.integrity(player, s.transitionSuit));
		player.setItemSlot(slot, ItemStack.EMPTY);
		if (s.transitionToCase) {
			int caseIdx = findFoldTargetCase(player, s.transitionSuit);
			if (caseIdx >= 0 && SuitcaseContents.put(player.getInventory().getItem(caseIdx), out)) {
				return;
			}
			// the case went missing mid-fold: the piece goes to the pack instead, never into thin air
		}
		// Main inventory only, never the hotbar (spec "changes 12"); the drop fallback only guards an inventory that
		// filled up mid-sequence.
		if (!addToMainInventoryOnly(player, out) && !player.getInventory().add(out)) {
			player.drop(out, false);
		}
	}

	/**
	 * v0.14.29: the end of the Mark 5 fold -- every worn piece of {@code suitId} (charge + integrity stamped on, every
	 * other component kept) goes into a fresh suitcase, which then lands in the main hand, else the first free hotbar
	 * slot, else the first free inventory slot, else at the player's feet ({@link #placeSuitcase}) -- never lost.
	 */
	public static ItemStack packIntoCase(ServerPlayer player, String suitId) {
		net.minecraft.world.item.Item caseItem = suitcaseItemFor(suitId);
		if (caseItem == null) {
			return ItemStack.EMPTY;
		}
		float energy = IronManEnergy.energy(player, suitId);
		float integrity = IronManEnergy.integrity(player, suitId);
		net.minecraft.core.NonNullList<ItemStack> slots =
				net.minecraft.core.NonNullList.withSize(SuitcaseContents.SLOTS, ItemStack.EMPTY);
		for (int bit = 0; bit < 4; bit++) {
			EquipmentSlot slot = SLOT_BY_BIT[bit];
			if (!IronManArmor.isPieceWorn(player, slot, suitId)) {
				continue;
			}
			ItemStack out = player.getItemBySlot(slot).copy();
			IronManEnergy.stampStack(out, energy, integrity);
			player.setItemSlot(slot, ItemStack.EMPTY);
			slots.set(SuitcaseContents.slotOf(typeOf(slot)), out);
		}
		ItemStack caseStack = new ItemStack(caseItem);
		SuitcaseContents.write(caseStack, slots);
		IronManEnergy.stampStack(caseStack, energy, integrity);
		placeSuitcase(player, caseStack);
		IronManSounds.play(player, IronManSounds.CASE_UNFOLD, 0.9f, 0.8f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.folded_to_case",
				Component.translatable(IronManSuits.byId(suitId).nameKey())).withStyle(ChatFormatting.AQUA), true);
		return caseStack;
	}

	/**
	 * v0.14.29: where a freshly packed suitcase goes -- the main hand if it is empty, else the first free hotbar slot,
	 * else the first free main-inventory slot, else dropped at the player's feet. Returns the inventory index, or -1 if
	 * it was dropped.
	 */
	public static int placeSuitcase(ServerPlayer player, ItemStack caseStack) {
		var inv = player.getInventory();
		int idx = -1;
		if (inv.selected >= 0 && inv.selected < 9 && inv.items.get(inv.selected).isEmpty()) {
			idx = inv.selected;
		}
		for (int i = 0; idx < 0 && i < MAIN_INV_END; i++) {
			if (inv.items.get(i).isEmpty()) {
				idx = i; // hotbar 0..8 first, then the main inventory 9..35
			}
		}
		if (idx < 0) {
			player.drop(caseStack, false);
			return -1;
		}
		inv.items.set(idx, caseStack);
		inv.setChanged();
		return idx;
	}

	/** After a from-case suit-up: the now-empty case is used up (it IS the folded suit). */
	private static void discardEmptyCase(ServerPlayer player, String suitId) {
		net.minecraft.world.item.Item caseItem = suitcaseItemFor(suitId);
		if (caseItem == null) {
			return;
		}
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(caseItem) && !SuitcaseContents.isLegacyEmpty(st) && SuitcaseContents.isEmpty(st)) {
				inv.removeItem(i, 1);
				return;
			}
		}
	}

	private static int findCaseHolding(ServerPlayer player, String suitId, ArmorItem.Type type) {
		net.minecraft.world.item.Item caseItem = suitcaseItemFor(suitId);
		if (caseItem == null) {
			return -1;
		}
		var inv = player.getInventory();
		int slot = SuitcaseContents.slotOf(type);
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(caseItem) && slot >= 0 && !SuitcaseContents.read(st).get(slot).isEmpty()) {
				return i;
			}
		}
		return -1;
	}

	// ---------------- FX ----------------

	/** The tick (into the sequence) at which the piece in {@code bit} reaches / starts to leave the body. */
	public static int stageTick(int bit, boolean up, SuitUpType type) {
		return Math.round(stageThreshold(bit, up, type) * type.durationTicks());
	}

	private static float stageThreshold(int bit, boolean up, SuitUpType type) {
		// bit: 0 HEAD 1 CHEST 2 LEGS 3 FEET
		if (up) {
			if (type == SuitUpType.SUITCASE_MOVIE) {
				// chest first, then legs, then feet, then helmet last ("changes 15")
				return switch (bit) { case 1 -> 0.10f; case 2 -> 0.45f; case 3 -> 0.60f; default -> 0.85f; };
			}
			return switch (bit) { case 3 -> 0.15f; case 2 -> 0.35f; case 1 -> 0.60f; default -> 0.85f; };
		}
		// suit-down: helmet comes away first
		if (type == SuitUpType.SUITCASE_MOVIE) {
			return switch (bit) { case 0 -> 0.10f; case 3 -> 0.40f; case 2 -> 0.55f; default -> 0.85f; };
		}
		return switch (bit) { case 0 -> 0.10f; case 1 -> 0.35f; case 2 -> 0.60f; default -> 0.80f; };
	}

	/** Height on the body a slot's piece sits at. */
	public static double slotHeight(EquipmentSlot slot) {
		return switch (slot) {
			case FEET -> 0.2;
			case LEGS -> 0.7;
			case CHEST -> 1.2;
			default -> 1.6;
		};
	}

	/** The clamp-on (up) / unlatch (down) burst at a piece's body height. */
	static void stageFx(ServerPlayer player, EquipmentSlot slot, boolean up) {
		ServerLevel level = (ServerLevel) player.level();
		double y = player.getY() + slotHeight(slot);
		// v0.14.27: short-lived sparks only -- the old end rods drifted down the finished suit for seconds
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), y, player.getZ(), up ? 18 : 10, 0.35, 0.15, 0.35, 0.12);
		IronManSounds.play(player, up ? IronManSounds.CLAMP : IronManSounds.RELEASE, 0.8f, up ? 1.0f : 0.9f);
	}

	private static void launchFx(ServerPlayer player, boolean up) {
		// v0.14.27: sound only -- the crit burst that rained down the body is gone
		IronManSounds.play(player, IronManSounds.SERVO, 0.9f, up ? 0.9f : 0.8f);
	}

	/** The faceplate-close beat that ends a suit-up: the visor swings shut (animated for every viewer), seal + power-up. */
	private static void faceplateClose(ServerPlayer player, String suitId) {
		ServerLevel level = (ServerLevel) player.level();
		if (player.getAttachedOrElse(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false)) {
			player.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false);
		}
		IronManSuitFx.faceplateMoved(player);
		IronManSounds.play(player, IronManSounds.FACEPLATE_SEAL, 0.9f, 1.0f);
		IronManSounds.play(player, IronManSounds.POWER_UP, 0.7f, 1.0f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.6, player.getZ(),
				8, 0.2, 0.1, 0.2, 0.05);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.suit_online",
				Component.translatable("projecthero.ironman.suit." + suitId + ".name")).withStyle(ChatFormatting.AQUA), true);
	}

	// ---------------- helpers ----------------

	public static boolean inTransition(ServerPlayer player) {
		return !TonyStark.state(player).transitionSuit.isEmpty();
	}

	/**
	 * v0.14.27: a suit-up is still running -- the suit is not online until every piece has built itself on, so its
	 * abilities and flight stay locked until then.
	 */
	public static boolean assembling(ServerPlayer player) {
		TonyStarkState s = TonyStark.state(player);
		return !s.transitionSuit.isEmpty() && s.transitionUp;
	}

	/** {@link #assembling}, telling the player so (action bar) when {@code notify}. */
	public static boolean blockedWhileAssembling(ServerPlayer player, boolean notify) {
		if (!assembling(player)) {
			return false;
		}
		if (notify) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.suit_assembling")
					.withStyle(ChatFormatting.GRAY), true);
		}
		return true;
	}

	private static int wornMask(ServerPlayer player, String suitId) {
		int mask = 0;
		for (int bit = 0; bit < 4; bit++) {
			if (IronManArmor.isPieceWorn(player, SLOT_BY_BIT[bit], suitId)) {
				mask |= (1 << bit);
			}
		}
		return mask;
	}

	/**
	 * Empty an armour slot back into the player's inventory (dropping only if it will not fit) before something else is
	 * put there -- {@link ServerPlayer#setItemSlot} overwrites, it does not return what was there. An Iron Man piece
	 * being evicted also gets its live charge / integrity stamped onto the outgoing stack.
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
		boolean toInventory = addToMainInventoryOnly(player, out) || player.getInventory().add(out);
		if (!toInventory) {
			player.drop(out, false);
		}
		if (foreign) {
			player.displayClientMessage(Component.translatable(
					toInventory ? "message.projecthero.ironman.armour_stowed" : "message.projecthero.ironman.armour_dropped",
					out.getHoverName()).withStyle(ChatFormatting.GRAY), true);
		}
	}

	/**
	 * {@code Inventory.items} indices 9..35 -- the 27 main-inventory slots, deliberately excluding the hotbar (0..8).
	 * Armour pieces are always max-stack-1, so "free space" only ever means an empty slot.
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
	public static boolean addToMainInventoryOnly(ServerPlayer player, ItemStack stack) {
		var items = player.getInventory().items;
		for (int i = MAIN_INV_START; i < MAIN_INV_END; i++) {
			if (items.get(i).isEmpty()) {
				items.set(i, stack);
				return true;
			}
		}
		return false;
	}

	/** Give a stack back to a player safely: main inventory, then anywhere, then dropped at their feet. Never lost. */
	public static void giveBack(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		if (!addToMainInventoryOnly(player, stack) && !player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	private static void reject(ServerPlayer player) {
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.armor_rejects")
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

	public static ArmorItem.Type typeOf(EquipmentSlot slot) {
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
