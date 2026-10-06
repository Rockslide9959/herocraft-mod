package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.entity.MjolnirEntity;
import com.projecthero.mod.entity.ModEntityTypes;
import com.projecthero.mod.hammer.HammerRecord;
import com.projecthero.mod.hammer.MjolnirRecall;
import com.projecthero.mod.hammer.MjolnirRegistry;
import com.projecthero.mod.hammer.MjolnirStatus;
import com.projecthero.mod.hammer.ThorWeapon;
import com.projecthero.mod.hammer.ThorWeaponSelection;
import com.projecthero.mod.item.ModDataComponents;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.ThorPowers;
import com.projecthero.mod.stormbreaker.StormbreakerEntity;
import com.projecthero.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.3 Thor: the N weapon selector (each of Mjolnir / Stormbreaker ACTIVE or INACTIVE for R) and Stormbreaker lying
 * in the world as its own resting entity, like Mjolnir. Every weapon is bound to its own mock player, every entity
 * stays inside its own test area, and every wait is a {@code succeedWhen} on the state itself.
 */
public class ThorWeaponSelectV0153GameTests implements FabricGameTest {
	private static final String BATCH = "thor_weapon_select_v0153";

	/** A worthy survival Thor at {@code relative}; Mjolnir bound in the main hand and/or a Stormbreaker bound in slot 8. */
	private static ServerPlayer thor(GameTestHelper helper, Vec3 relative, boolean hammer, boolean axe) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL); // mock players start creative, which would skip every worthiness rule
		Vec3 at = helper.absoluteVec(relative);
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		Worthiness.setScore(p, Worthiness.TEST_WORTHY_SCORE);
		if (hammer) {
			ItemStack mjolnir = new ItemStack(ModItems.MJOLNIR);
			p.setItemInHand(InteractionHand.MAIN_HAND, mjolnir);
			ThorPowers.toggleBinding(p, mjolnir);
		}
		if (axe) {
			p.getInventory().setItem(8, new ItemStack(ModItems.STORMBREAKER));
			p.getInventory().tick(); // binds it (StormbreakerItem#inventoryTick)
		}
		return p;
	}

	private static ServerPlayer mortal(GameTestHelper helper, Vec3 relative) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(relative);
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		return p;
	}

	private static UUID hammerId(ServerPlayer p) {
		return p.getAttachedOrElse(ModAttachments.BOUND_HAMMER_ID, null);
	}

	private static UUID axeId(ServerPlayer p) {
		return p.getAttachedOrElse(ModAttachments.BOUND_STORMBREAKER_ID, null);
	}

	private static boolean isId(ItemStack stack, UUID id) {
		return id != null && !stack.isEmpty() && id.equals(stack.get(ModDataComponents.HAMMER_ID));
	}

	private static ItemStack takeAxe(ServerPlayer p) {
		UUID id = axeId(p);
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (isId(p.getInventory().getItem(i), id)) {
				ItemStack stack = p.getInventory().getItem(i);
				p.getInventory().setItem(i, ItemStack.EMPTY);
				return stack;
			}
		}
		throw new IllegalStateException("no bound axe in the inventory");
	}

	private static List<Entity> worldCopies(GameTestHelper helper, UUID id) {
		List<Entity> out = new ArrayList<>();
		helper.getLevel().getEntities(EntityTypeTest.forClass(Entity.class), e -> !e.isRemoved() && (
				(e instanceof MjolnirEntity m && isId(m.getItem(), id))
						|| (e instanceof StormbreakerEntity s && isId(s.getItem(), id))
						|| (e instanceof ItemEntity i && isId(i.getItem(), id))), out);
		return out;
	}

	private static int inventoryCopies(ServerPlayer p, UUID id) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (isId(p.getInventory().getItem(i), id)) {
				n++;
			}
		}
		return n;
	}

	/** Lays the axe down as a resting entity -- what any drop becomes (see {@link #aDroppedStormbreakerBecomesTheAxeEntity}). */
	private static StormbreakerEntity restAxe(GameTestHelper helper, ItemStack axe, Vec3 relative) {
		StormbreakerEntity entity = StormbreakerEntity.createResting(helper.getLevel(), null, axe,
				helper.absoluteVec(relative), Vec3.ZERO);
		helper.getLevel().addFreshEntity(entity);
		return entity;
	}

	private static MjolnirEntity restHammer(GameTestHelper helper, ItemStack hammer, Vec3 relative) {
		MjolnirEntity entity = MjolnirEntity.createResting(helper.getLevel(), null, hammer,
				helper.absoluteVec(relative), Vec3.ZERO, false);
		helper.getLevel().addFreshEntity(entity);
		return entity;
	}

	private static void cleanUp(GameTestHelper helper, List<UUID> ids, ServerPlayer... players) {
		for (UUID id : ids) {
			if (id != null) {
				worldCopies(helper, id).forEach(Entity::discard);
			}
		}
		for (ServerPlayer p : players) {
			if (!p.isRemoved()) {
				helper.getLevel().getServer().getPlayerList().remove(p);
			}
		}
	}

	// ---------------- the selector itself ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void theToggleDefaultsToActiveAndPersists(GameTestHelper helper) {
		ServerPlayer p = thor(helper, new Vec3(1.5, 2, 1.5), true, true);
		helper.assertTrue(ThorWeaponSelection.isActive(p, ThorWeapon.MJOLNIR)
				&& ThorWeaponSelection.isActive(p, ThorWeapon.STORMBREAKER), "both weapons start Active");

		ThorWeaponSelection.handleToggle(p, ThorWeapon.STORMBREAKER.ordinal(), false);
		helper.assertTrue(!ThorWeaponSelection.isActive(p, ThorWeapon.STORMBREAKER), "Stormbreaker switched off");
		helper.assertTrue(ThorWeaponSelection.isActive(p, ThorWeapon.MJOLNIR), "Mjolnir untouched");

		// garbage from the client changes nothing
		ThorWeaponSelection.handleToggle(p, 7, false);
		ThorWeaponSelection.handleToggle(p, -1, false);
		helper.assertTrue(ThorWeaponSelection.isActive(p, ThorWeapon.MJOLNIR), "an unknown weapon index is ignored");

		// the attachment is saved with the player
		CompoundTag saved = p.saveWithoutId(new CompoundTag());
		saved.remove("UUID");
		ServerPlayer reloaded = mortal(helper, new Vec3(3.5, 2, 3.5));
		reloaded.load(saved);
		reloaded.getInventory().clearContent(); // only the attachment matters here, never a second copy of the weapons
		helper.assertTrue(!ThorWeaponSelection.isActive(reloaded, ThorWeapon.STORMBREAKER)
				&& ThorWeaponSelection.isActive(reloaded, ThorWeapon.MJOLNIR), "the choice survives a save and load");

		// back on, and then a player who is not Thor cannot change anything
		ThorWeaponSelection.handleToggle(p, ThorWeapon.STORMBREAKER.ordinal(), true);
		helper.assertTrue(ThorWeaponSelection.isActive(p, ThorWeapon.STORMBREAKER), "switched back on");
		ServerPlayer outsider = mortal(helper, new Vec3(5.5, 2, 5.5));
		helper.assertFalse(ThorWeaponSelection.ownsSelector(outsider), "the N screen is Thor's only");
		ThorWeaponSelection.handleToggle(outsider, ThorWeapon.MJOLNIR.ordinal(), false);
		helper.assertTrue(ThorWeaponSelection.isActive(outsider, ThorWeapon.MJOLNIR), "an unworthy player's request is refused");
		helper.assertTrue(ThorWeaponSelection.ownsSelector(p), "a bound Thor owns the selector");
		cleanUp(helper, List.of(hammerId(p), axeId(p)), p, reloaded, outsider);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 100)
	public void anInactiveWeaponIsNeverCalledEvenWhenCloser(GameTestHelper helper) {
		ServerPlayer p = thor(helper, new Vec3(1.5, 2, 1.5), true, true);
		UUID hammer = hammerId(p);
		UUID axe = axeId(p);
		// neither in hand: the hammer lies right next to him, the axe much farther away
		MjolnirEntity nearHammer = restHammer(helper, p.getMainHandItem().copy(), new Vec3(2.5, 2, 2.5));
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		StormbreakerEntity farAxe = restAxe(helper, takeAxe(p), new Vec3(7.5, 2, 7.5));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(MjolnirRecall.chooseWeapon(p) == ThorWeapon.MJOLNIR, "by default the nearer hammer is chosen");
			ThorWeaponSelection.set(p, ThorWeapon.MJOLNIR, false);
			helper.assertTrue(MjolnirRecall.chooseWeapon(p) == ThorWeapon.STORMBREAKER, "with Mjolnir Inactive, the axe answers");
			helper.assertTrue(MjolnirRecall.call(p), "the call is answered");
			helper.assertTrue(farAxe.isReturning() && !farAxe.isResting(), "the far axe is the one flying home");
			helper.assertTrue(nearHammer.getState() == MjolnirEntity.State.RESTING, "the nearer hammer never moves");
			helper.succeedWhen(() -> {
				helper.assertTrue(isId(p.getMainHandItem(), axe), "Stormbreaker arrives in the hand");
				helper.assertTrue(worldCopies(helper, axe).isEmpty(), "one axe, never two");
				helper.assertTrue(!nearHammer.isRemoved() && nearHammer.getState() == MjolnirEntity.State.RESTING,
						"Mjolnir is still lying where it was");
				// and the other way round: Stormbreaker in hand, axe Inactive -> Mjolnir, the one that was switched off,
				// is not called either; with only the hammer Active again, it is
				ThorWeaponSelection.set(p, ThorWeapon.MJOLNIR, true);
				ThorWeaponSelection.set(p, ThorWeapon.STORMBREAKER, false);
				helper.assertTrue(MjolnirRecall.chooseWeapon(p) == ThorWeapon.MJOLNIR, "now only Mjolnir is Active");
				cleanUp(helper, List.of(hammer, axe), p);
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 60)
	public void anInactiveOnlyBoundWeaponStaysPut(GameTestHelper helper) {
		ServerPlayer p = thor(helper, new Vec3(1.5, 2, 1.5), false, true);
		UUID axe = axeId(p);
		StormbreakerEntity lying = restAxe(helper, takeAxe(p), new Vec3(4.5, 2, 4.5));
		ThorWeaponSelection.set(p, ThorWeapon.STORMBREAKER, false);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(MjolnirRecall.chooseWeapon(p) == ThorWeapon.MJOLNIR, "the axe is out of the running");
			MjolnirRecall.call(p);
			helper.assertTrue(lying.isResting() && !lying.isReturning(), "the only bound weapon is not called while Inactive");
			helper.assertTrue(worldCopies(helper, axe).size() == 1 && inventoryCopies(p, axe) == 0, "nothing moved");
			cleanUp(helper, List.of(axe), p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 60)
	public void withBothInactiveTheCallBringsNothing(GameTestHelper helper) {
		ServerPlayer p = thor(helper, new Vec3(1.5, 2, 1.5), true, true);
		UUID hammer = hammerId(p);
		UUID axe = axeId(p);
		MjolnirEntity onGround = restHammer(helper, p.getMainHandItem().copy(), new Vec3(4.5, 2, 3.5));
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		StormbreakerEntity axeOnGround = restAxe(helper, takeAxe(p), new Vec3(3.5, 2, 4.5));
		ThorWeaponSelection.handleToggle(p, ThorWeapon.MJOLNIR.ordinal(), false);
		ThorWeaponSelection.handleToggle(p, ThorWeapon.STORMBREAKER.ordinal(), false);
		helper.runAfterDelay(2, () -> {
			helper.assertFalse(ThorWeaponSelection.anyActive(p), "both switched off");
			helper.assertFalse(MjolnirRecall.call(p), "the call goes unanswered");
			helper.assertTrue(onGround.getState() == MjolnirEntity.State.RESTING, "Mjolnir stays on the ground");
			helper.assertTrue(axeOnGround.isResting(), "Stormbreaker stays on the ground");
			helper.runAfterDelay(10, () -> {
				helper.assertTrue(p.getMainHandItem().isEmpty() && inventoryCopies(p, hammer) == 0
						&& inventoryCopies(p, axe) == 0, "and nothing arrives later either");
				cleanUp(helper, List.of(hammer, axe), p);
				helper.succeed();
			});
		});
	}

	// ---------------- Stormbreaker on the ground ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 140)
	public void aDroppedStormbreakerBecomesTheAxeEntity(GameTestHelper helper) {
		ServerPlayer p = thor(helper, new Vec3(1.5, 2, 1.5), false, true);
		UUID axe = axeId(p);
		// the Q path: Player#drop spawns the vanilla item, which ItemEntityMixin promotes on its first tick
		ItemEntity dropped = p.drop(takeAxe(p), false);
		helper.assertTrue(dropped != null, "vanilla dropped the axe");
		ServerPlayer mortal = mortal(helper, new Vec3(1.5, 2, 3.5));
		ServerPlayer otherThor = thor(helper, new Vec3(3.5, 2, 1.5), false, false);
		helper.succeedWhen(() -> {
			helper.assertTrue(dropped.isRemoved(), "the item is gone");
			List<Entity> copies = worldCopies(helper, axe);
			helper.assertTrue(copies.size() == 1 && copies.get(0) instanceof StormbreakerEntity,
					"exactly one copy, and it is the axe entity, got " + copies);
			StormbreakerEntity entity = (StormbreakerEntity) copies.get(0);
			helper.assertTrue(entity.isResting(), "lying in the world");
			Optional<HammerRecord> record = MjolnirRegistry.get(helper.getLevel()).record(axe);
			helper.assertTrue(record.isPresent() && record.get().placement() == HammerRecord.Placement.ENTITY
					&& record.get().status() == MjolnirStatus.RESTING
					&& record.get().entityId().equals(Optional.of(entity.getUUID())), "the registry knows where it lies");
			helper.assertTrue(entity.pickupDelay() == 0, "the pickup grace period has run out");

			// the unworthy cannot lift it
			entity.interact(mortal, InteractionHand.MAIN_HAND);
			helper.assertTrue(!entity.isRemoved() && inventoryCopies(mortal, axe) == 0, "an unworthy player cannot pick it up");

			// it survives a save and load still lying down
			StormbreakerEntity reloaded = new StormbreakerEntity(ModEntityTypes.STORMBREAKER, helper.getLevel());
			reloaded.load(entity.saveWithoutId(new CompoundTag()));
			helper.assertTrue(reloaded.isResting() && isId(reloaded.getItem(), axe), "saved and loaded, it is still resting");
			reloaded.discard();

			// a worthy player can
			entity.interact(otherThor, InteractionHand.MAIN_HAND);
			helper.assertTrue(entity.isRemoved() && isId(otherThor.getMainHandItem(), axe), "a worthy player picks it up");
			helper.assertTrue(worldCopies(helper, axe).isEmpty(), "and none is left behind");
			cleanUp(helper, List.of(axe), p, mortal, otherThor);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 100)
	public void aDroppedStormbreakerIsCalledHome(GameTestHelper helper) {
		ServerPlayer p = thor(helper, new Vec3(1.5, 2, 1.5), false, true);
		UUID axe = axeId(p);
		ItemEntity dropped = p.drop(takeAxe(p), false);
		helper.assertTrue(dropped != null, "vanilla dropped the axe");
		helper.runAfterDelay(3, () -> {
			List<Entity> copies = worldCopies(helper, axe);
			helper.assertTrue(copies.size() == 1 && copies.get(0) instanceof StormbreakerEntity, "it lies as the axe entity");
			StormbreakerEntity lying = (StormbreakerEntity) copies.get(0);
			helper.assertTrue(MjolnirRecall.call(p), "the call is answered");
			helper.assertTrue(!lying.isRemoved() && lying.isReturning() && lying.wasRecalled(),
					"the very same entity lifts off and flies home");
			helper.succeedWhen(() -> {
				helper.assertTrue(isId(p.getMainHandItem(), axe), "Stormbreaker is back in the hand");
				helper.assertTrue(worldCopies(helper, axe).isEmpty() && inventoryCopies(p, axe) == 1, "one axe, never two");
				cleanUp(helper, List.of(axe), p);
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 60)
	public void aGhostAxeOnTheGroundDeletesItself(GameTestHelper helper) {
		ServerPlayer p = thor(helper, new Vec3(1.5, 2, 1.5), false, true);
		UUID axe = axeId(p);
		ItemStack real = p.getInventory().getItem(8);
		ItemStack ghost = real.copy();
		// a recall elsewhere bumps the generation: the copy lying on the ground is now a ghost
		real.set(ModDataComponents.HAMMER_GENERATION, MjolnirRegistry.get(helper.getLevel()).reconstruct(axe));
		StormbreakerEntity ghostEntity = restAxe(helper, ghost, new Vec3(4.5, 2, 4.5));
		helper.succeedWhen(() -> {
			helper.assertTrue(ghostEntity.isRemoved(), "the ghost axe is gone");
			helper.assertTrue(worldCopies(helper, axe).isEmpty() && inventoryCopies(p, axe) == 1, "the real one is untouched");
			cleanUp(helper, List.of(axe), p);
		});
	}
}
