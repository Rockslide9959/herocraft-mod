package com.projecthero.mod.ironman.suit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.item.SuitcaseContents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.7 (user) Iron Man Era II stage 2: the <b>server</b> half of the portable forms ({@link PortableForm}) -- C deploys
 * a suit out of its packed form, holding Shift+C folds it back in. Server-authoritative: this class alone moves the real
 * armour stacks; every viewer animates off the synced pose clock ({@link IronManSuitFx#STYLE_PORTABLE}, pose kind
 * {@link PortableForms#poseKind}).
 *
 * <h2>Deploy (C, or right-click the packed item)</h2>
 * The packed item stays in the inventory while the prop is "on the floor"; each piece leaves it for its armour slot at
 * that piece's {@link PortableForm#pieceOnAt} frame (so a piece is always in exactly one place), the helmet arrives with
 * its faceplate raised and the faceplate seals at {@link PortableForm.Beats#sealAt}; at the end the emptied item is used
 * up. The wearer is held still (the Suit Platform's movement lock) and the suit's abilities stay locked until it is done.
 *
 * <h2>Fold (hold Shift+C {@value #FOLD_HOLD_TICKS} ticks)</h2>
 * The same frames backwards. Every piece stays in its slot (its texels vanishing) until the very last tick, when all of
 * them -- integrity and charge stamped on -- move into a fresh packed item in one go ({@link #pack}), which goes to the
 * main hand if it is free, else the hotbar, the inventory, or the feet ({@link IronManSuitUpManager#placeSuitcase}).
 *
 * <h2>Edge cases</h2>
 * Damage never interrupts either (it keeps going). Dying aborts (nothing has moved that a death drop does not cover:
 * worn pieces drop as armour, the half-empty item drops as an item). Logging out / changing dimension finishes at once
 * ({@link #completeNow}) so the player is saved fully suited or fully packed. The fold hold is the only static
 * per-player state ({@link #clearSessionState} in {@code ServerStateReset}, {@link #forget} on disconnect).
 */
public final class PortableDeploy {
	/** Hold Shift+C this long (0.5 s) to start a fold. */
	public static final int FOLD_HOLD_TICKS = 10;
	/** A fold's synced clock outlives the fold by this long (see {@link #begin}). */
	public static final int END_HOLD = 10;

	private static final Map<UUID, Long> FOLD_HOLD = new ConcurrentHashMap<>();
	private static boolean hooked;

	private PortableDeploy() {
	}

	/** The death / logout / dimension-change safety hooks. Idempotent. */
	public static void initialize() {
		if (hooked) {
			return;
		}
		hooked = true;
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				abort(p);
			}
		});
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			completeNow(handler.getPlayer());
			forget(handler.getPlayer().getUUID());
		});
		net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
				(player, origin, destination) -> completeNow(player));
	}

	public static void clearSessionState() {
		FOLD_HOLD.clear();
	}

	public static void forget(UUID player) {
		FOLD_HOLD.remove(player);
	}

	/** Is a portable deploy / fold running for {@code player}? */
	public static boolean running(ServerPlayer player) {
		TonyStarkState s = TonyStark.state(player);
		return s.transitionPortable && !s.transitionSuit.isEmpty()
				|| com.projecthero.mod.ironman.crate.HulkbusterCrate.folding(player); // v0.16.7 (user): the Hulkbuster crate fold
	}

	/** Is {@code player} holding Shift+C toward a fold right now? */
	public static boolean holdingFold(ServerPlayer player) {
		return FOLD_HOLD.containsKey(player.getUUID());
	}

	// ======================================================================== deploy

	/**
	 * Plain C with no suit on: deploy the first portable form the player carries (the main hand first, then the rest of
	 * the inventory). False (and nothing happens) if they carry none -- the caller then tries its other C actions.
	 */
	public static boolean deployFromC(ServerPlayer player) {
		if (!TonyStark.hasPower(player) || IronManArmor.wearingAnyIronMan(player) || IronManSuitUpManager.inTransition(player)) {
			return false;
		}
		// v0.16.7 (user): the Hulkbuster crate is down with the suit still in it -- C sends it back up
		if (com.projecthero.mod.ironman.crate.HulkbusterCrate.recall(player)) {
			return true;
		}
		var inv = player.getInventory();
		PortableForm form = deployable(inv.getSelected()) ? PortableForms.byStack(inv.getSelected()) : null;
		for (int i = 0; form == null && i < inv.items.size(); i++) {
			if (deployable(inv.items.get(i))) {
				form = PortableForms.byStack(inv.items.get(i));
			}
		}
		return form != null && deploy(player, form.suitId());
	}

	/** Can {@code stack} be deployed (a packed item holding pieces, or a legacy / crafted one standing for a whole suit)? */
	static boolean deployable(ItemStack stack) {
		return PortableForms.byStack(stack) != null
				&& (SuitcaseContents.isLegacyEmpty(stack) || !SuitcaseContents.isEmpty(stack));
	}

	/** Index of the packed item of {@code form} to deploy (the selected slot first), or -1. */
	static int findPacked(ServerPlayer player, PortableForm form) {
		var inv = player.getInventory();
		if (inv.selected >= 0 && inv.selected < 9 && inv.items.get(inv.selected).is(form.packedItem().get())
				&& deployable(inv.items.get(inv.selected))) {
			return inv.selected;
		}
		int legacy = -1;
		for (int i = 0; i < inv.items.size(); i++) {
			ItemStack st = inv.items.get(i);
			if (!st.is(form.packedItem().get())) {
				continue;
			}
			if (!SuitcaseContents.isLegacyEmpty(st) && !SuitcaseContents.isEmpty(st)) {
				return i;
			}
			if (legacy < 0 && SuitcaseContents.isLegacyEmpty(st)) {
				legacy = i;
			}
		}
		return legacy;
	}

	/** Deploy {@code suitId} out of its packed form. False (with a reason where useful) if it cannot start. */
	public static boolean deploy(ServerPlayer player, String suitId) {
		PortableForm form = PortableForms.byId(suitId);
		IronManSuit suit = IronManSuits.byId(suitId);
		if (form == null || suit == null || !TonyStark.hasPower(player) || IronManSuitUpManager.inTransition(player)) {
			return false;
		}
		if (IronManArmor.wearingAnyIronMan(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.portable_take_off_first")
					.withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (form.rest() == PortableForm.Place.BLOCK) {
			return com.projecthero.mod.ironman.crate.HulkbusterCrate.call(player); // v0.16.7 (user): the drop crate (its block-like source)
		}
		int idx = findPacked(player, form);
		if (idx < 0) {
			return false;
		}
		ItemStack packed = player.getInventory().items.get(idx);
		if (SuitcaseContents.isLegacyEmpty(packed)) {
			// a case that never had a suit folded into it (crafted / creative) stands for a whole fresh suit
			NonNullList<ItemStack> fresh = NonNullList.withSize(SuitcaseContents.SLOTS, ItemStack.EMPTY);
			for (int bit = 0; bit < 4; bit++) {
				fresh.set(bit, new ItemStack(IronManItems.armor(suitId, IronManSuitUpManager.typeOf(SLOTS[bit]))));
			}
			SuitcaseContents.write(packed, fresh);
		}
		int mask = 0;
		var slots = SuitcaseContents.read(packed);
		for (int bit = 0; bit < 4; bit++) {
			if (!slots.get(bit).isEmpty()) {
				mask |= 1 << bit;
			}
		}
		if (mask == 0) {
			return false;
		}
		IronManEnergy.setEnergy(player, suitId, IronManEnergy.stackEnergy(packed, suitId));
		IronManEnergy.setIntegrity(player, suitId, IronManEnergy.stackIntegrity(packed, suitId));
		// setActiveSuit saves a COPY of the state -- call it before taking the state object we write the transition into
		TonyStark.setActiveSuit(player, suitId);
		begin(player, form, true, mask);
		IronManSounds.play(player, IronManSounds.SERVO, 0.6f, 1.2f);
		return true;
	}

	// ======================================================================== fold

	/** A Shift+C edge while wearing a suit with a portable form ({@code IronManAbilityManager}). */
	public static void foldKey(ServerPlayer player, IronManSuit suit, boolean pressed) {
		if (!pressed) {
			FOLD_HOLD.remove(player.getUUID());
			return;
		}
		if (suit == null || PortableForms.byId(suit.id()) == null || IronManSuitUpManager.inTransition(player)
				|| FOLD_HOLD.containsKey(player.getUUID())) {
			return;
		}
		FOLD_HOLD.put(player.getUUID(), player.level().getGameTime());
	}

	/** 0..1 of the Shift+C hold done (server). */
	public static float holdProgress(ServerPlayer player) {
		Long start = FOLD_HOLD.get(player.getUUID());
		return start == null ? 0f : Math.min(1f, (player.level().getGameTime() - start) / (float) FOLD_HOLD_TICKS);
	}

	/** Per player tick ({@code IronManSuitTicker}): start the fold once the Shift+C hold has lasted, or drop the hold. */
	public static void holdTick(ServerPlayer player) {
		holdTick(player, player.level().getGameTime());
	}

	/** As {@link #holdTick(ServerPlayer)} at game time {@code now} (the gametests step the hold with it). */
	public static void holdTick(ServerPlayer player, long now) {
		Long start = FOLD_HOLD.get(player.getUUID());
		if (start == null) {
			return;
		}
		String worn = IronManArmor.wornSuitId(player);
		if (!player.isAlive() || !player.isShiftKeyDown() || PortableForms.byId(worn) == null
				|| IronManSuitUpManager.inTransition(player)) {
			FOLD_HOLD.remove(player.getUUID());
			return;
		}
		if (now - start >= FOLD_HOLD_TICKS) {
			FOLD_HOLD.remove(player.getUUID());
			fold(player, worn);
		}
	}

	/** Fold the worn {@code suitId} back into its packed form, now (the hold has already been checked). */
	public static boolean fold(ServerPlayer player, String suitId) {
		PortableForm form = PortableForms.byId(suitId);
		if (form == null || IronManSuitUpManager.inTransition(player)) {
			return false;
		}
		if (form.rest() == PortableForm.Place.BLOCK) {
			return com.projecthero.mod.ironman.crate.HulkbusterCrate.fold(player); // v0.16.7 (user): the crate comes down round you
		}
		int mask = wornMask(player, suitId);
		if (mask == 0) {
			return false;
		}
		begin(player, form, false, mask);
		IronManSounds.play(player, IronManSounds.SERVO, 0.7f, 0.85f);
		return true;
	}

	// ======================================================================== the sequence

	private static void begin(ServerPlayer player, PortableForm form, boolean up, int mask) {
		if (IronManFlight.isFlying(player)) {
			IronManFlight.setFlying(player, false);
		}
		if (com.projecthero.mod.ironman.RepulsorBoots.isFlying(player)) {
			com.projecthero.mod.ironman.RepulsorBoots.setFlying(player, false);
		}
		TonyStarkState s = TonyStark.state(player);
		s.transitionSuit = form.suitId();
		s.transitionUp = up;
		s.transitionPortable = true;
		s.transitionToCase = false;
		s.transitionFromCase = false;
		s.transitionBracelet = false;
		s.transitionManual = 0;
		s.transitionPlan = 0;
		s.transitionReleaseMask = 0;
		s.transitionMask = mask;
		s.transitionTotal = form.beats().total();
		s.transitionTicks = s.transitionTotal;
		IronManSuitPlatformBlockEntity.setFrozen(player, true);
		if (up && PortableForms.keepsTanks(form)) {
			player.setAttached(ModAttachments.IRON_MAN_PACK_TANKS, true); // the Mk VIII's O2 tanks stay on the suit
		}
		// no piece clocks (a stale one must not draw a piece with another style's windows) -- just the pose clock
		IronManSuitFx fx = IronManSuitFx.of(player);
		// a fold's clock runs END_HOLD past the end, so a viewer keeps drawing the pieces gone (and the case in the hand)
		// until the emptied armour slots have synced -- else the whole suit flashes back for a frame
		player.setAttached(ModAttachments.IRON_MAN_SUIT_FX, IronManSuitFx.EMPTY.withFaceplate(fx.faceplateAt())
				.withPose(PortableForms.poseKind(form, up), player.level().getGameTime(),
						s.transitionTotal + (up ? 0 : END_HOLD), IronManSuitFx.STYLE_PORTABLE));
	}

	/** One tick of a running deploy / fold (from {@link IronManSuitUpManager#tick}). */
	static void tick(ServerPlayer player, TonyStarkState s) {
		PortableForm form = PortableForms.byId(s.transitionSuit);
		if (form == null) {
			end(player, s);
			return;
		}
		s.transitionTicks--;
		int age = s.transitionTotal - s.transitionTicks;
		int frame = Math.round(form.frame(s.transitionUp, age));
		PortableForm.Beats b = form.beats();
		IronManSuitPlatformBlockEntity.setFrozen(player, true);
		if (s.transitionUp) {
			for (int bit = 3; bit >= 0; bit--) {
				if ((s.transitionMask & (1 << bit)) != 0 && frame >= form.pieceOnAt(bit)) {
					s.transitionMask &= ~(1 << bit);
					putOn(player, form, bit);
				}
			}
			if (frame == b.dropTo()) {
				IronManSounds.play(player, IronManSounds.CASE_UNFOLD, 0.9f, 1.0f); // the case lands and flips open
			}
			if (frame == b.sealAt() && IronManArmor.isPieceWorn(player, EquipmentSlot.HEAD, form.suitId())) {
				if (IronManArmor.wearingFullSuit(player, form.suitId())) {
					IronManSuitUpManager.faceplateClose(player, form.suitId());
				} else {
					player.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false);
					IronManSuitFx.faceplateMoved(player);
				}
			}
		} else {
			if (frame == b.sealAt() && IronManArmor.isPieceWorn(player, EquipmentSlot.HEAD, form.suitId())) {
				player.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, true); // the faceplate lifts first
				IronManSuitFx.faceplateMoved(player);
			}
			if (frame == b.openTo()) {
				IronManSounds.play(player, IronManSounds.CASE_UNFOLD, 0.8f, 0.85f); // the case snaps shut
			}
		}
		if (age % 10 == 0 && s.transitionTicks > 0) {
			IronManSounds.play(player, IronManSounds.SERVO, 0.3f, 1.1f + player.getRandom().nextFloat() * 0.3f);
		}
		if (s.transitionTicks <= 0) {
			finish(player, s, form);
		}
	}

	/** Deploy: move piece {@code bit} out of the packed item onto the body (skipped if it has gone missing). */
	private static void putOn(ServerPlayer player, PortableForm form, int bit) {
		ItemStack piece = takeFromPacked(player, form, bit);
		if (piece.isEmpty()) {
			return;
		}
		EquipmentSlot slot = SLOTS[bit];
		IronManSuitUpManager.evictSlot(player, slot);
		player.setItemSlot(slot, piece);
		if (bit == 0) {
			player.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, true); // it comes raised and seals last
		}
		IronManSounds.play(player, IronManSounds.SERVO, 0.7f, 0.85f + bit * 0.08f);
	}

	private static ItemStack takeFromPacked(ServerPlayer player, PortableForm form, int bit) {
		var inv = player.getInventory();
		var type = IronManSuitUpManager.typeOf(SLOTS[bit]);
		int sel = inv.selected;
		if (sel >= 0 && sel < 9 && inv.items.get(sel).is(form.packedItem().get())
				&& !SuitcaseContents.read(inv.items.get(sel)).get(bit).isEmpty()) {
			return SuitcaseContents.take(inv.items.get(sel), type);
		}
		for (ItemStack st : inv.items) {
			if (st.is(form.packedItem().get()) && !SuitcaseContents.isLegacyEmpty(st)
					&& !SuitcaseContents.read(st).get(bit).isEmpty()) {
				return SuitcaseContents.take(st, type);
			}
		}
		return ItemStack.EMPTY;
	}

	private static void finish(ServerPlayer player, TonyStarkState s, PortableForm form) {
		boolean up = s.transitionUp;
		String suitId = s.transitionSuit;
		if (up) {
			for (int bit = 3; bit >= 0; bit--) {
				if ((s.transitionMask & (1 << bit)) != 0) {
					s.transitionMask &= ~(1 << bit);
					putOn(player, form, bit);
				}
			}
			useUpEmpty(player, form);
			if (player.getAttachedOrElse(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false)
					&& IronManArmor.isPieceWorn(player, EquipmentSlot.HEAD, suitId)) {
				IronManSuitUpManager.faceplateClose(player, suitId);
			}
		} else {
			pack(player, form);
			IronManSounds.play(player, IronManSounds.SUIT_STORED, 0.8f, IronManSounds.markPitch(suitId));
		}
		end(player, s, up);
		heatGrace(player, suitId); // v0.16.7 (user) Era II: the Mk IX heat case in fire / lava
		if (!up && !IronManArmor.wearingAnyIronMan(player)) {
			TonyStark.setActiveSuit(player, ""); // AFTER the transition is cleared (setActiveSuit copies the state)
		}
	}

	/** Fire Resistance a fire-immune suit's sequence leaves behind when it ends with the wearer in the heat (10 s). */
	public static final int HEAT_GRACE_TICKS = 200;

	/**
	 * v0.16.7 (user) Era II, the Mk IX heat case's lava rule. During a deploy nothing can hurt the wearer (it is a
	 * suit-up, {@code IronManDamage#suitUpImmune}); during a fold every piece stays in its slot until the last tick, so the
	 * full-set fire / lava immunity ({@code IronManDamage#fireProof}) holds the whole way. What is left is the moment
	 * after: a fold (or a deploy out of a case missing a piece) that ends with the wearer standing in fire or lava and no
	 * longer in the full suit -- the case's heat shroud then gives {@value #HEAT_GRACE_TICKS} ticks of Fire Resistance
	 * and puts the flames out, time enough to climb out. Only for {@link IronManSuit#fireImmune()} suits.
	 */
	static void heatGrace(ServerPlayer player, String suitId) {
		IronManSuit suit = IronManSuits.byId(suitId);
		if (suit == null || !suit.fireImmune() || IronManArmor.wearingFullSuit(player, suitId) || !inHeat(player)) {
			return;
		}
		player.clearFire();
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE,
				HEAT_GRACE_TICKS, 0, false, true, true));
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.heat_case_shroud")
				.withStyle(ChatFormatting.GOLD), true);
	}

	/** Is {@code player} burning, or touching fire, lava or a magma block (block states, so it works on mock players)? */
	public static boolean inHeat(ServerPlayer player) {
		if (player.isOnFire() || player.isInLava()) {
			return true;
		}
		return player.level().getBlockStates(player.getBoundingBox().inflate(0.0, 0.1, 0.0).move(0.0, -0.05, 0.0))
				.anyMatch(st -> st.is(net.minecraft.tags.BlockTags.FIRE) || st.is(net.minecraft.world.level.block.Blocks.LAVA)
						|| st.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
						|| st.getFluidState().is(net.minecraft.tags.FluidTags.LAVA));
	}

	/** Clears the transition, the movement lock and the pose clock. */
	private static void end(ServerPlayer player, TonyStarkState s) {
		end(player, s, true);
	}

	/** As {@link #end(ServerPlayer, TonyStarkState)}; {@code endPose} false leaves a finished fold's clock to run out. */
	private static void end(ServerPlayer player, TonyStarkState s, boolean endPose) {
		s.transitionPortable = false;
		s.transitionMask = 0;
		s.transitionTicks = 0;
		s.transitionSuit = "";
		IronManSuitPlatformBlockEntity.setFrozen(player, false);
		if (endPose) {
			IronManSuitFx.endPose(player);
		}
	}

	/** After a deploy: the emptied packed item is used up (it IS the folded suit). */
	private static void useUpEmpty(ServerPlayer player, PortableForm form) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.items.size(); i++) {
			ItemStack st = inv.items.get(i);
			if (st.is(form.packedItem().get()) && !SuitcaseContents.isLegacyEmpty(st) && SuitcaseContents.isEmpty(st)) {
				inv.removeItem(i, 1);
				return;
			}
		}
	}

	/**
	 * The end of a fold: every worn piece of the form's suit (integrity + charge stamped on, every other component kept)
	 * goes into a fresh packed item, stamped too, which lands in the main hand / hotbar / inventory / at the feet. Public
	 * for the gametests.
	 */
	public static ItemStack pack(ServerPlayer player, PortableForm form) {
		String suitId = form.suitId();
		float energy = IronManEnergy.energy(player, suitId);
		float integrity = IronManEnergy.integrity(player, suitId);
		NonNullList<ItemStack> slots = NonNullList.withSize(SuitcaseContents.SLOTS, ItemStack.EMPTY);
		boolean any = false;
		for (int bit = 0; bit < 4; bit++) {
			EquipmentSlot slot = SLOTS[bit];
			if (!IronManArmor.isPieceWorn(player, slot, suitId)) {
				continue;
			}
			ItemStack out = player.getItemBySlot(slot).copy();
			IronManEnergy.stampStack(out, energy, integrity);
			player.setItemSlot(slot, ItemStack.EMPTY);
			slots.set(bit, out);
			any = true;
		}
		if (!any) {
			return ItemStack.EMPTY;
		}
		player.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false);
		ItemStack packed = new ItemStack(form.packedItem().get());
		SuitcaseContents.write(packed, slots);
		IronManEnergy.stampStack(packed, energy, integrity);
		if (form.rest() == PortableForm.Place.BACK) {
			placePack(player, packed); // v0.16.7 (user): a backpack goes on the back (the inventory), not into the hand
		} else {
			IronManSuitUpManager.placeSuitcase(player, packed);
		}
		player.displayClientMessage(Component.translatable(form.rest() == PortableForm.Place.BACK
				? "message.projecthero.ironman.folded_to_pack" : "message.projecthero.ironman.folded_to_case",
				Component.translatable(IronManSuits.byId(suitId).nameKey())).withStyle(ChatFormatting.AQUA), true);
		return packed;
	}

	// ======================================================================== backpacks (v0.16.7 user, Era II stage 2)

	/**
	 * Where a freshly folded backpack goes: the first free main-inventory slot (it is "on your back", not in your hand),
	 * else a free hotbar slot other than the selected one, else the selected slot, else at the feet. Returns the inventory
	 * index, or -1 if it was dropped.
	 */
	public static int placePack(ServerPlayer player, ItemStack pack) {
		var inv = player.getInventory();
		int idx = -1;
		for (int i = 9; i < inv.items.size() && idx < 0; i++) {
			if (inv.items.get(i).isEmpty()) {
				idx = i;
			}
		}
		for (int i = 0; i < 9 && idx < 0; i++) {
			if (i != inv.selected && inv.items.get(i).isEmpty()) {
				idx = i;
			}
		}
		if (idx < 0 && inv.selected >= 0 && inv.selected < 9 && inv.items.get(inv.selected).isEmpty()) {
			idx = inv.selected;
		}
		if (idx < 0) {
			player.drop(pack, false);
			return -1;
		}
		inv.items.set(idx, pack);
		return idx;
	}

	/** The suit id of the backpack form {@code player} carries (and so wears on the back), or "" for none. */
	public static String carriedPack(ServerPlayer player) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.items.size(); i++) {
			PortableForm form = PortableForms.byStack(inv.items.get(i));
			if (form != null && form.rest() == PortableForm.Place.BACK && deployable(inv.items.get(i))) {
				return form.suitId();
			}
		}
		PortableForm off = PortableForms.byStack(player.getOffhandItem());
		return off != null && off.rest() == PortableForm.Place.BACK && deployable(player.getOffhandItem()) ? off.suitId() : "";
	}

	/**
	 * Per player tick ({@code IronManSuitTicker}): mirror the carried backpack into the synced
	 * {@link ModAttachments#IRON_MAN_BACK_PACK} (every viewer draws it on the back), and drop the Mk VIII's O2 tanks
	 * ({@link ModAttachments#IRON_MAN_PACK_TANKS}) once its chestplate is off outside a sequence. Writes only on a change.
	 */
	public static void backTick(ServerPlayer player) {
		String pack = carriedPack(player);
		if (!pack.equals(player.getAttachedOrElse(ModAttachments.IRON_MAN_BACK_PACK, ""))) {
			player.setAttached(ModAttachments.IRON_MAN_BACK_PACK, pack);
		}
		if (player.getAttachedOrElse(ModAttachments.IRON_MAN_PACK_TANKS, false) && !running(player)
				&& !IronManArmor.isPieceWorn(player, EquipmentSlot.CHEST, PortableForms.MK8_THRUSTER_PACK.suitId())) {
			player.setAttached(ModAttachments.IRON_MAN_PACK_TANKS, false);
		}
	}

	/** Death mid-sequence: just stop -- worn pieces drop as armour, the half-empty packed item as an item. */
	public static void abort(ServerPlayer player) {
		FOLD_HOLD.remove(player.getUUID());
		TonyStarkState s = TonyStark.state(player);
		if (!s.transitionPortable) {
			return;
		}
		end(player, s);
	}

	/** Logout / dimension change mid-sequence: finish it at once, so no piece is ever in two places or none. */
	public static void completeNow(ServerPlayer player) {
		TonyStarkState s = TonyStark.state(player);
		if (!s.transitionPortable || s.transitionSuit.isEmpty()) {
			return;
		}
		PortableForm form = PortableForms.byId(s.transitionSuit);
		if (form == null) {
			end(player, s);
			return;
		}
		if (s.transitionUp) {
			player.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false);
		}
		s.transitionTicks = 1;
		finish(player, s, form);
		player.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false);
	}

	static int wornMask(ServerPlayer player, String suitId) {
		int mask = 0;
		for (int bit = 0; bit < 4; bit++) {
			if (IronManArmor.isPieceWorn(player, SLOTS[bit], suitId)) {
				mask |= 1 << bit;
			}
		}
		return mask;
	}

	/** Bit 0 HEAD .. 3 FEET, as everywhere in the suit-up code. */
	static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
}
