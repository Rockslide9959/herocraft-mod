package com.projecthero.mod.ironman.crate;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.entity.IronManEntityTypes;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.item.SuitcaseContents;
import com.projecthero.mod.ironman.suit.IronManPlatformReturn;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Iron Man Era II stage 2: the Hulkbuster I (mark_8) portable form -- an <b>orbital drop crate</b>
 * ({@link HulkbusterCrateEntity}). Where the suit lives when it is not called: <b>in orbit</b> -- v0.16.7 (user): a
 * server-side orbit slot ({@link HulkbusterOrbit}: the four real stacks with integrity, per-part damage and charge),
 * opened by the reusable {@link IronManItems#HULKBUSTER_UPLINK Hulkbuster Uplink} (a key, never used up). A fold, a
 * recall or a crate cleared away sends the suit back up into its slot as it is (no healing in orbit). It only goes home
 * to a Suit Platform when its Uplink is put into one. See {@code docs/HULKBUSTER_V0167.md}.
 *
 * <ul>
 *   <li><b>C</b> (no suit on, an Uplink whose suit is in orbit): the crate drops where you look, 5..40 blocks out, onto
 *       open ground under open sky, where you may build ({@link #call}). With the suit already down it is recalled.</li>
 *   <li><b>C</b> again with the suit still standing in the crate: it shuts and lifts back off ({@link #recall}).</li>
 *   <li><b>Walk into its open back / right-click it</b>: the Sentry Mode step-in closes it round you.</li>
 *   <li><b>Hold Shift+C</b> in the Hulkbuster: the crate comes down round you, you step out, it folds shut and lifts off
 *       ({@link #fold}, reached through {@code PortableDeploy.fold}).</li>
 * </ul>
 */
public final class HulkbusterCrate {
	public static final String SUIT_ID = "mark_8";
	/** The crate lands at least this far out (its front wall falls 4.6 blocks towards you)... */
	public static final double MIN_RANGE = 5.0;
	/** ...and at most this far. */
	public static final double MAX_RANGE = 40.0;
	/** It falls from this high (less under the build limit). */
	public static final float DROP_HEIGHT = 64f;
	/** A fold crate comes down from this high. */
	public static final float FOLD_HEIGHT = 40f;

	/** Players a fold crate is coming down round (abilities locked meanwhile). Cleared in {@code ServerStateReset}. */
	private static final Set<UUID> FOLDING = ConcurrentHashMap.newKeySet();

	private HulkbusterCrate() {
	}

	public static void clearSessionState() {
		FOLDING.clear();
	}

	public static void forget(UUID player) {
		FOLDING.remove(player);
	}

	/** Is a fold crate coming down round {@code player} (they are held still, nothing fires)? */
	public static boolean folding(ServerPlayer player) {
		return FOLDING.contains(player.getUUID());
	}

	/** {@code player}'s crate in their level, or null. */
	public static HulkbusterCrateEntity crateOf(ServerPlayer player) {
		for (HulkbusterCrateEntity c : player.serverLevel().getEntities(IronManEntityTypes.HULKBUSTER_CRATE,
				c -> !c.isRemoved() && player.getUUID().equals(c.ownerId()))) {
			return c;
		}
		return null;
	}

	// ======================================================================== the uplink (a key to an orbit slot)

	/**
	 * Index of the Uplink to act with: the selected one first, then one whose suit is in orbit, then any Uplink at all
	 * (whose suit is down: using it recalls the suit). -1 if none.
	 */
	static int findKey(ServerPlayer player) {
		var inv = player.getInventory();
		if (inv.selected >= 0 && inv.selected < 9 && inv.items.get(inv.selected).is(IronManItems.HULKBUSTER_UPLINK)) {
			return inv.selected;
		}
		HulkbusterOrbit orbit = HulkbusterOrbit.get(player.getServer());
		int any = -1;
		for (int i = 0; i < inv.items.size(); i++) {
			ItemStack st = inv.items.get(i);
			if (!st.is(IronManItems.HULKBUSTER_UPLINK)) {
				continue;
			}
			HulkbusterOrbit.Slot s = orbit.slotOfKey(st);
			if (s != null && s.inOrbit || !HulkbusterOrbit.isKey(st) && usableLegacy(st)) {
				return i;
			}
			if (any < 0 && s != null) {
				any = i;
			}
		}
		return any;
	}

	/** Does {@code player} carry any Uplink at all? */
	public static boolean carriesUplink(ServerPlayer player) {
		for (ItemStack st : player.getInventory().items) {
			if (st.is(IronManItems.HULKBUSTER_UPLINK)) {
				return true;
			}
		}
		return false;
	}

	/** An Uplink from before keys: never used (a fresh suit) or still holding the four stacks itself. */
	static boolean usableLegacy(ItemStack st) {
		return SuitcaseContents.isLegacyEmpty(st) || !SuitcaseContents.isEmpty(st);
	}

	/**
	 * v0.16.7 (user): the orbit slot {@code key} opens, or null (a dead key -- re-linked since -- or an old emptied one).
	 * An Uplink from before keys is bound on first use: a never-used one becomes a brand new Hulkbuster in orbit, one still
	 * holding the stacks moves them up into a new slot. Either way it is the same Uplink afterwards, now a key.
	 */
	public static HulkbusterOrbit.Slot resolve(ServerPlayer player, ItemStack key) {
		if (!key.is(IronManItems.HULKBUSTER_UPLINK)) {
			return null;
		}
		HulkbusterOrbit orbit = HulkbusterOrbit.get(player.getServer());
		if (HulkbusterOrbit.isKey(key)) {
			return orbit.slotOfKey(key);
		}
		List<ItemStack> pieces = new java.util.ArrayList<>();
		float integrity;
		if (SuitcaseContents.isLegacyEmpty(key)) {
			for (int bit = 0; bit < 4; bit++) {
				pieces.add(new ItemStack(IronManItems.armor(SUIT_ID, IronManSuitUpManager.typeOf(SLOTS[bit]))));
			}
			integrity = IronManEnergy.maxIntegrity(SUIT_ID);
		} else if (!SuitcaseContents.isEmpty(key)) {
			pieces.addAll(SuitcaseContents.nonEmpty(key));
			integrity = IronManEnergy.stackIntegrity(key, SUIT_ID);
		} else {
			return null; // emptied by the old single-use rules: nothing to bind
		}
		HulkbusterOrbit.Slot s = orbit.create(player.getUUID(), pieces, integrity);
		HulkbusterOrbit.bindKey(key, s);
		player.getInventory().setChanged();
		return s;
	}

	/**
	 * v0.16.7 (user): the suit goes back up. "the suit goes back into its pod/crate and up to ORBIT, NOT back to the
	 * platform. Its damage and integrity stay as they are". The four stacks go into the orbit slot their pieces carry
	 * ({@link HulkbusterOrbit}) -- nothing is healed and no Uplink is touched: the Uplink that called it calls it again.
	 * Pieces with no slot (a suit from before keys, or one whose slot was closed) open a new slot for their owner, and an
	 * online owner gets the new slot's Uplink. With no owner at all they drop where the crate stood. Never lost, never doubled.
	 */
	public static void toOrbit(ServerLevel level, ServerPlayer owner, List<ItemStack> pieces, float integrity, Vec3 at) {
		toOrbit(level, owner, owner == null ? null : owner.getUUID(), pieces, integrity, at);
	}

	/** As {@link #toOrbit(ServerLevel, ServerPlayer, List, float, Vec3)} for an owner known only by id (may be offline). */
	public static void toOrbit(ServerLevel level, ServerPlayer owner, UUID ownerId, List<ItemStack> pieces, float integrity, Vec3 at) {
		List<ItemStack> stacks = new java.util.ArrayList<>();
		for (ItemStack s : pieces) {
			if (s.getItem() instanceof ArmorItem && !s.isEmpty()) {
				IronManPlatformReturn.clearHome(s); // the single-use era's platform stamp: orbit is home now
				stacks.add(s);
			}
		}
		if (stacks.isEmpty()) {
			return;
		}
		HulkbusterOrbit orbit = HulkbusterOrbit.get(level.getServer());
		HulkbusterOrbit.Slot slot = orbit.slot(HulkbusterOrbit.linkOf(stacks));
		if (slot != null && !slot.inOrbit) {
			orbit.store(slot, stacks, integrity);
			if (owner != null) {
				owner.displayClientMessage(Component.translatable("message.projecthero.ironman.hb_crate.in_orbit", slot.integrityPct())
						.withStyle(ChatFormatting.AQUA), true);
			}
			return;
		}
		if (slot != null || ownerId == null) {
			// a second set claiming a slot that already holds its suit (should never happen) or nobody to own it: the
			// pieces come back down loose, unlinked -- never deleted, never merged into another suit
			if (slot != null) {
				com.projecthero.mod.ProjectHeroMod.LOGGER.warn("[ProjectHero] a Hulkbuster reached orbit for a slot that is already full; dropping it loose");
			}
			for (ItemStack s : stacks) {
				HulkbusterOrbit.unlink(s);
				if (owner != null) {
					IronManSuitUpManager.giveBack(owner, s);
				} else {
					level.addFreshEntity(new ItemEntity(level, at.x, at.y + 0.5, at.z, s));
				}
			}
			return;
		}
		slot = orbit.create(ownerId, stacks, integrity);
		if (owner != null) {
			ItemStack key = new ItemStack(IronManItems.HULKBUSTER_UPLINK);
			HulkbusterOrbit.bindKey(key, slot);
			IronManSuitUpManager.placeSuitcase(owner, key);
			owner.getInventory().setChanged();
			owner.displayClientMessage(Component.translatable("message.projecthero.ironman.hb_crate.in_orbit_new")
					.withStyle(ChatFormatting.AQUA), true);
		}
	}

	/**
	 * v0.16.7 (user): the Suit Platform's Uplink tab -- the racked Hulkbuster lifts off to orbit and {@code player} gets its
	 * Uplink (a key: the suit is in {@link HulkbusterOrbit}, not in the item). A suit that already had a slot (one racked
	 * by hand after a death, say) goes back into it and its key is re-cut, so any older Uplink for it goes dead.
	 */
	public static ItemStack mintUplink(ServerPlayer player, List<ItemStack> pieces, float integrity, ServerLevel level, BlockPos platform) {
		HulkbusterOrbit orbit = HulkbusterOrbit.get(level.getServer());
		List<ItemStack> stacks = new java.util.ArrayList<>();
		for (ItemStack s : pieces) {
			if (s.getItem() instanceof ArmorItem && !s.isEmpty()) {
				IronManPlatformReturn.clearHome(s);
				stacks.add(s);
			}
		}
		HulkbusterOrbit.Slot slot = orbit.slot(HulkbusterOrbit.linkOf(stacks));
		if (slot != null && !slot.inOrbit) {
			orbit.store(slot, stacks, integrity);
			slot.owner = player.getUUID();
			orbit.bump(slot);
		} else {
			slot = orbit.create(player.getUUID(), stacks, integrity);
		}
		ItemStack up = new ItemStack(IronManItems.HULKBUSTER_UPLINK);
		HulkbusterOrbit.bindKey(up, slot);
		IronManSuitUpManager.placeSuitcase(player, up);
		player.getInventory().setChanged();
		return up;
	}

	/**
	 * v0.16.7 (user): a Suit Platform's "Re-link" -- a lost Uplink. A new key is cut for {@code player}'s Hulkbuster whose
	 * key they don't carry; the slot's generation moves on, so the lost one (if it ever turns up) is dead. Null if there
	 * is nothing to re-link.
	 */
	public static ItemStack relink(ServerPlayer player) {
		HulkbusterOrbit orbit = HulkbusterOrbit.get(player.getServer());
		HulkbusterOrbit.Slot slot = orbit.keyless(player);
		if (slot == null) {
			return null;
		}
		orbit.bump(slot);
		ItemStack up = new ItemStack(IronManItems.HULKBUSTER_UPLINK);
		HulkbusterOrbit.bindKey(up, slot);
		IronManSuitUpManager.placeSuitcase(player, up);
		player.getInventory().setChanged();
		return up;
	}

	// ======================================================================== C / the Uplink: call / recall

	/** C or the Uplink's right-click with the crate down and the suit still in it: send it back up. */
	public static boolean recall(ServerPlayer player) {
		HulkbusterCrateEntity c = crateOf(player);
		if (c == null) {
			return false;
		}
		if (c.recall()) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.hb_crate.recalled")
					.withStyle(ChatFormatting.AQUA), true);
		} else {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.hb_crate.busy")
					.withStyle(ChatFormatting.GRAY), true);
		}
		return true;
	}

	/** C: use the Uplink {@code player} carries (call the suit down, or recall it if it is already down). */
	public static boolean call(ServerPlayer player) {
		int idx = findKey(player);
		if (idx < 0) {
			return false;
		}
		return use(player, player.getInventory().items.get(idx), com.projecthero.mod.ironman.item.HulkbusterUplinkItem.airborne(player));
	}

	/**
	 * v0.16.7 (user): the Uplink used (right-click or C). It is <b>not</b> used up. Suit in orbit: on the ground the drop
	 * crate falls where you look, in the air the pod builds it round you. Suit already down: it is recalled to orbit.
	 */
	public static boolean use(ServerPlayer player, ItemStack key, boolean airborne) {
		if (!TonyStark.hasPower(player) || IronManSuitUpManager.inTransition(player)) {
			return false;
		}
		if (com.projecthero.mod.ironman.HulkbusterInnerSuit.wearingHulkbusterPiece(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.portable_fold_hint").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		HulkbusterOrbit.Slot slot = resolve(player, key);
		if (slot == null) {
			player.displayClientMessage(Component.translatable(HulkbusterOrbit.isKey(key) ? "message.projecthero.ironman.hb_uplink.unlinked"
					: "message.projecthero.ironman.hb_uplink.spent").withStyle(ChatFormatting.GOLD), true);
			return false;
		}
		HulkbusterOrbit.view(key, slot);
		if (!slot.inOrbit) {
			return recallDown(player, slot);
		}
		if (airborne) {
			return buildInAir(player, slot);
		}
		Vec3 ground = aim(player);
		if (ground == null) {
			refuse(player, "look_at_ground");
			return false;
		}
		return callTo(player, slot, ground);
	}

	/** Call the crate down onto {@code ground} with the Uplink {@code player} carries. Public for the gametests. */
	public static boolean callTo(ServerPlayer player, Vec3 ground) {
		int idx = findKey(player);
		HulkbusterOrbit.Slot slot = idx < 0 ? null : resolve(player, player.getInventory().items.get(idx));
		if (slot == null || !slot.inOrbit) {
			if (idx >= 0) {
				refuse(player, "not_in_orbit");
			}
			return false;
		}
		return callTo(player, slot, ground);
	}

	static boolean callTo(ServerPlayer player, HulkbusterOrbit.Slot slot, Vec3 ground) {
		if (!TonyStark.hasPower(player) || com.projecthero.mod.ironman.HulkbusterInnerSuit.wearingHulkbusterPiece(player)
				|| IronManSuitUpManager.inTransition(player)) {
			return false;
		}
		if (crateOf(player) != null) {
			refuse(player, "busy");
			return false;
		}
		ServerLevel level = player.serverLevel();
		String problem = landingProblem(level, player, ground, true);
		if (problem != null) {
			refuse(player, problem);
			return false;
		}
		float integrity = slot.integrity;
		List<ItemStack> pieces = HulkbusterOrbit.get(level.getServer()).takeDown(slot);
		if (pieces.isEmpty()) {
			return false;
		}
		Vec3 d = ground.subtract(player.position());
		float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
		float h = Math.max(12f, Math.min(DROP_HEIGHT, level.getMaxBuildHeight() - (float) ground.y - 6f));
		HulkbusterCrateEntity.drop(level, player, ground, yaw, h, pieces, integrity);
		IronManSounds.play(player, IronManSounds.WEAPON_SELECT, 0.8f, 0.8f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.hb_crate.inbound")
				.withStyle(ChatFormatting.AQUA), true);
		return true;
	}

	/** v0.16.7 (user) Veronica: the Uplink used in the air (public for the gametests): the pod builds the suit round you. */
	public static boolean buildInAir(ServerPlayer player) {
		int idx = findKey(player);
		HulkbusterOrbit.Slot slot = idx < 0 ? null : resolve(player, player.getInventory().items.get(idx));
		if (slot == null || !slot.inOrbit) {
			if (idx >= 0) {
				refuse(player, "not_in_orbit");
			}
			return false;
		}
		return buildInAir(player, slot);
	}

	/**
	 * v0.16.7 (user) Veronica: no crate -- a pod comes in above and behind {@code player} and the Hulkbuster builds itself
	 * round them ({@code HulkbusterPodEntity#callBuild}), over whatever they wear. The Uplink stays.
	 */
	static boolean buildInAir(ServerPlayer player, HulkbusterOrbit.Slot slot) {
		if (!TonyStark.hasPower(player) || com.projecthero.mod.ironman.HulkbusterInnerSuit.wearingHulkbusterPiece(player)
				|| IronManSuitUpManager.inTransition(player) || crateOf(player) != null) {
			return false;
		}
		HulkbusterOrbit orbit = HulkbusterOrbit.get(player.getServer());
		float integrity = slot.integrity;
		List<ItemStack> pieces = orbit.takeDown(slot);
		for (ItemStack s : pieces) {
			IronManEnergy.stampStack(s, IronManEnergy.stackEnergy(s, SUIT_ID), integrity);
		}
		if (pieces.isEmpty() || com.projecthero.mod.ironman.entity.HulkbusterPodEntity.callBuild(player, pieces) == null) {
			orbit.store(slot, pieces, integrity); // the pod could not come: the suit stays up
			return false;
		}
		return true;
	}

	/**
	 * v0.16.7 (user): "Calling while the suit is already down: recall it to orbit". Standing in its open crate: the crate
	 * shuts and lifts. Standing free (Sentry Mode, a wreck): a crate comes down round it and takes it up. Anything else
	 * (worn, on its way down, pieces scattered) is explained.
	 */
	static boolean recallDown(ServerPlayer player, HulkbusterOrbit.Slot slot) {
		MinecraftServer server = player.getServer();
		for (ServerLevel lvl : server.getAllLevels()) {
			for (com.projecthero.mod.ironman.entity.IronManSentryEntity s : lvl.getEntities(IronManEntityTypes.SENTRY,
					s -> !s.isRemoved() && s.hulkbuster() && slot.id.equals(HulkbusterOrbit.linkOf(s.stack(1))))) {
				if (s.crated()) {
					for (HulkbusterCrateEntity c : lvl.getEntities(IronManEntityTypes.HULKBUSTER_CRATE, c -> !c.isRemoved() && c.holds(s))) {
						boolean ok = c.recall();
						player.displayClientMessage(Component.translatable(ok ? "message.projecthero.ironman.hb_crate.recalled"
								: "message.projecthero.ironman.hb_crate.busy").withStyle(ok ? ChatFormatting.AQUA : ChatFormatting.GRAY), true);
						return ok;
					}
				}
				String problem = HulkbusterCrateEntity.pickUp(lvl, player, s);
				if (problem != null) {
					refuse(player, problem);
					return false;
				}
				player.displayClientMessage(Component.translatable("message.projecthero.ironman.hb_crate.recalled")
						.withStyle(ChatFormatting.AQUA), true);
				return true;
			}
			for (HulkbusterCrateEntity c : lvl.getEntities(IronManEntityTypes.HULKBUSTER_CRATE, c -> !c.isRemoved())) {
				if (slot.id.equals(c.carriedLink())) {
					refuse(player, "on_its_way");
					return false;
				}
			}
			for (com.projecthero.mod.ironman.entity.HulkbusterPodEntity pod : lvl.getEntities(
					IronManEntityTypes.HULKBUSTER_POD, pod -> !pod.isRemoved())) {
				if (slot.id.equals(pod.carriedLink())) {
					refuse(player, "on_its_way");
					return false;
				}
			}
		}
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (slot.id.equals(HulkbusterOrbit.linkOf(other.getItemBySlot(EquipmentSlot.CHEST)))) {
				player.displayClientMessage(Component.translatable("message.projecthero.ironman.hb_crate.worn_by", other.getDisplayName())
						.withStyle(ChatFormatting.GOLD), true);
				return false;
			}
		}
		refuse(player, "unreachable");
		return false;
	}

	/** v0.16.7 (user): the platform can't take this Uplink -- the lang key tail of why, or null if it can. */
	public static String rackRefusal(MinecraftServer server, ItemStack uplink) {
		if (!uplink.is(IronManItems.HULKBUSTER_UPLINK)) {
			return "uplink_refused";
		}
		if (!HulkbusterOrbit.isKey(uplink)) {
			return usableLegacy(uplink) ? null : "uplink_spent";
		}
		HulkbusterOrbit.Slot s = HulkbusterOrbit.get(server).slotOfKey(uplink);
		if (s == null) {
			return "uplink_dead";
		}
		return s.inOrbit ? null : "uplink_down";
	}

	/** Where the look ray meets the ground, pushed out to {@link #MIN_RANGE}; null if it meets nothing in range. */
	static Vec3 aim(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		HitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(MAX_RANGE)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.ANY, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		Vec3 at = hit.getLocation().subtract(look.scale(0.05));
		Vec3 flat = at.subtract(player.position()).multiply(1, 0, 1);
		if (flat.length() < MIN_RANGE) {
			Vec3 dir = new Vec3(look.x, 0, look.z);
			if (dir.lengthSqr() < 1.0e-4) {
				dir = net.minecraft.world.phys.Vec3.directionFromRotation(0f, player.getYRot());
			}
			at = player.position().add(dir.normalize().scale(MIN_RANGE)).add(0, 1.5, 0);
		}
		return groundBelow(level, at, 10);
	}

	/** The top of the first solid block at or below {@code from} (within {@code depth}), centred on it; null if none. */
	static Vec3 groundBelow(ServerLevel level, Vec3 from, int depth) {
		BlockPos p = BlockPos.containing(from);
		for (int i = 0; i <= depth; i++, p = p.below()) {
			var st = level.getBlockState(p);
			if (!st.getCollisionShape(level, p).isEmpty() && st.isFaceSturdy(level, p, net.minecraft.core.Direction.UP)
					&& level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) {
				return new Vec3(p.getX() + 0.5, p.getY() + st.getCollisionShape(level, p).max(net.minecraft.core.Direction.Axis.Y), p.getZ() + 0.5);
			}
		}
		return null;
	}

	/**
	 * Why a crate cannot stand at {@code ground} (a lang key tail), or null: somewhere {@code player} may build, under
	 * open sky (centre and corners), with room for the closed crate.
	 */
	static String landingProblem(ServerLevel level, ServerPlayer player, Vec3 ground, boolean room) {
		BlockPos pos = BlockPos.containing(ground);
		if (!player.mayBuild() || !level.mayInteract(player, pos) || !level.getWorldBorder().isWithinBounds(pos)) {
			return "protected";
		}
		int top = (int) Math.ceil(ground.y - 1.0e-3);
		for (double[] o : new double[][] { { 0, 0 }, { -1, -1 }, { 1, -1 }, { -1, 1 }, { 1, 1 } }) {
			if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(ground.x + o[0]), (int) Math.floor(ground.z + o[1])) > top) {
				return "no_sky";
			}
		}
		if (room) {
			AABB box = new AABB(ground.x - HulkbusterCrateEntity.HALF, ground.y + 0.05, ground.z - HulkbusterCrateEntity.HALF,
					ground.x + HulkbusterCrateEntity.HALF, ground.y + HulkbusterCrateEntity.WALL_H + 0.1, ground.z + HulkbusterCrateEntity.HALF);
			if (level.getBlockCollisions(null, box).iterator().hasNext()) {
				return "no_room";
			}
		}
		return null;
	}

	private static void refuse(ServerPlayer player, String key) {
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.hb_crate." + key).withStyle(ChatFormatting.RED), true);
	}

	// ======================================================================== Shift+C: fold

	/**
	 * Hold Shift+C in the full Hulkbuster: the crate comes down round you, you step out, it folds shut and lifts off with
	 * the suit. False (with a reason) if it cannot.
	 */
	public static boolean fold(ServerPlayer player) {
		if (!IronManArmor.wearingFullSuit(player, SUIT_ID) || IronManSuitUpManager.inTransition(player) || folding(player)) {
			return false;
		}
		HulkbusterCrateEntity existing = crateOf(player);
		if (existing != null) {
			refuse(player, "busy");
			return false;
		}
		if (IronManFlight.isFlying(player) || player.isPassenger() || !player.onGround()) {
			refuse(player, "land_first");
			return false;
		}
		ServerLevel level = player.serverLevel();
		String problem = landingProblem(level, player, player.position(), false);
		if (problem != null) {
			refuse(player, problem);
			return false;
		}
		FOLDING.add(player.getUUID());
		IronManSuitPlatformBlockEntity.setFrozen(player, true);
		float h = Math.max(10f, Math.min(FOLD_HEIGHT, level.getMaxBuildHeight() - (float) player.getY() - 6f));
		HulkbusterCrateEntity.foldOver(level, player, h);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.hb_crate.folding")
				.withStyle(ChatFormatting.AQUA), true);
		return true;
	}

	static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
}
