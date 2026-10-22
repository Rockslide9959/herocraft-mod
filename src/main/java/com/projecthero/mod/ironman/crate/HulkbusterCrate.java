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
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
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
 * ({@link HulkbusterCrateEntity}). Where the suit lives when it is not called: <b>in orbit</b>, i.e. inside the
 * {@link IronManItems#HULKBUSTER_UPLINK Hulkbuster Uplink} the player carries (the four real stacks, integrity stamped,
 * like the Mk VI briefcase). While the suit is down the uplink stays in the inventory, emptied, and the next fold fills it
 * again. See {@code docs/IRONMAN_ERA2_FOLD.md}.
 *
 * <ul>
 *   <li><b>C</b> (no suit on, an uplink holding the suit): the crate drops where you look, 5..40 blocks out, onto open
 *       ground under open sky, where you may build ({@link #call}).</li>
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

	// ======================================================================== the uplink

	private static boolean loaded(ItemStack st) {
		return st.is(IronManItems.HULKBUSTER_UPLINK) && (SuitcaseContents.isLegacyEmpty(st) || !SuitcaseContents.isEmpty(st));
	}

	/** Index of the uplink holding the suit (the selected slot first, a filled one before a fresh one), or -1. */
	static int findLoaded(ServerPlayer player) {
		var inv = player.getInventory();
		if (inv.selected >= 0 && inv.selected < 9 && loaded(inv.items.get(inv.selected))) {
			return inv.selected;
		}
		int fresh = -1;
		for (int i = 0; i < inv.items.size(); i++) {
			ItemStack st = inv.items.get(i);
			if (!loaded(st)) {
				continue;
			}
			if (!SuitcaseContents.isLegacyEmpty(st)) {
				return i;
			}
			if (fresh < 0) {
				fresh = i;
			}
		}
		return fresh;
	}

	/** Does {@code player} carry any uplink at all? */
	public static boolean carriesUplink(ServerPlayer player) {
		for (ItemStack st : player.getInventory().items) {
			if (st.is(IronManItems.HULKBUSTER_UPLINK)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The suit is back in orbit: its four stacks go into {@code owner}'s emptied uplink (else a new one, placed like a
	 * folded briefcase); with no owner online the uplink drops where the crate stood.
	 */
	static void toOrbit(ServerLevel level, ServerPlayer owner, List<ItemStack> pieces, float integrity, Vec3 at) {
		NonNullList<ItemStack> slots = NonNullList.withSize(SuitcaseContents.SLOTS, ItemStack.EMPTY);
		boolean any = false;
		// v0.16.7 (user) Era II: the reactor charge travels with the pieces (the lowest piece's, as the integrity)
		float energy = IronManEnergy.capacity(SUIT_ID);
		for (ItemStack s : pieces) {
			if (s.getItem() instanceof ArmorItem && !s.isEmpty()) {
				energy = Math.min(energy, IronManEnergy.stackEnergy(s, SUIT_ID));
			}
		}
		for (ItemStack s : pieces) {
			if (s.getItem() instanceof ArmorItem a && !s.isEmpty()) {
				IronManEnergy.stampStack(s, energy, integrity);
				slots.set(SuitcaseContents.slotOf(a.getType()), s);
				any = true;
			}
		}
		if (!any) {
			return;
		}
		ItemStack target = null;
		if (owner != null) {
			for (ItemStack st : owner.getInventory().items) {
				if (st.is(IronManItems.HULKBUSTER_UPLINK) && !SuitcaseContents.isLegacyEmpty(st) && SuitcaseContents.isEmpty(st)) {
					target = st;
					break;
				}
			}
		}
		boolean fresh = target == null;
		if (fresh) {
			target = new ItemStack(IronManItems.HULKBUSTER_UPLINK);
		}
		SuitcaseContents.write(target, slots);
		IronManEnergy.stampStack(target, energy, integrity);
		if (owner != null) {
			if (fresh) {
				IronManSuitUpManager.placeSuitcase(owner, target);
			}
			owner.getInventory().setChanged();
			owner.displayClientMessage(Component.translatable("message.projecthero.ironman.hb_crate.in_orbit")
					.withStyle(ChatFormatting.AQUA), true);
		} else {
			ItemEntity drop = new ItemEntity(level, at.x, at.y + 0.5, at.z, target);
			level.addFreshEntity(drop);
		}
	}

	// ======================================================================== C: call / recall

	/** C or the uplink's right-click with the crate down and the suit still in it: send it back up. */
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

	/** C: call the crate down where {@code player} is looking. False (with a reason where useful) if it cannot come. */
	public static boolean call(ServerPlayer player) {
		if (!TonyStark.hasPower(player) || IronManArmor.wearingAnyIronMan(player) || IronManSuitUpManager.inTransition(player)) {
			return false;
		}
		if (findLoaded(player) < 0) {
			if (carriesUplink(player)) {
				refuse(player, "not_in_orbit");
			}
			return false;
		}
		Vec3 ground = aim(player);
		if (ground == null) {
			refuse(player, "look_at_ground");
			return false;
		}
		return callTo(player, ground);
	}

	/** Call the crate down onto {@code ground} (a point on top of the ground). Public for the gametests. */
	public static boolean callTo(ServerPlayer player, Vec3 ground) {
		if (crateOf(player) != null) {
			refuse(player, "busy");
			return false;
		}
		int idx = findLoaded(player);
		if (idx < 0) {
			return false;
		}
		ServerLevel level = player.serverLevel();
		String problem = landingProblem(level, player, ground, true);
		if (problem != null) {
			refuse(player, problem);
			return false;
		}
		ItemStack uplink = player.getInventory().items.get(idx);
		float integrity = IronManEnergy.stackIntegrity(uplink, SUIT_ID);
		NonNullList<ItemStack> slots = SuitcaseContents.read(uplink);
		if (SuitcaseContents.isLegacyEmpty(uplink)) {
			for (int bit = 0; bit < 4; bit++) {
				slots.set(bit, new ItemStack(IronManItems.armor(SUIT_ID, IronManSuitUpManager.typeOf(SLOTS[bit]))));
			}
			integrity = IronManEnergy.maxIntegrity(SUIT_ID);
		}
		List<ItemStack> pieces = new java.util.ArrayList<>();
		for (ItemStack s : slots) {
			if (!s.isEmpty()) {
				pieces.add(s);
			}
		}
		if (pieces.isEmpty()) {
			return false;
		}
		// the uplink stays, emptied (never mistaken for a fresh one): the suit is in the crate now
		SuitcaseContents.write(uplink, NonNullList.withSize(SuitcaseContents.SLOTS, ItemStack.EMPTY));
		player.getInventory().setChanged();
		Vec3 d = ground.subtract(player.position());
		float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
		float h = Math.max(12f, Math.min(DROP_HEIGHT, level.getMaxBuildHeight() - (float) ground.y - 6f));
		HulkbusterCrateEntity.drop(level, player, ground, yaw, h, pieces, integrity);
		IronManSounds.play(player, IronManSounds.WEAPON_SELECT, 0.8f, 0.8f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.hb_crate.inbound")
				.withStyle(ChatFormatting.AQUA), true);
		return true;
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
