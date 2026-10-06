package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.entity.MjolnirEntity;
import com.projecthero.mod.hammer.HammerRecord;
import com.projecthero.mod.hammer.MjolnirRecall;
import com.projecthero.mod.hammer.MjolnirRegistry;
import com.projecthero.mod.hammer.MjolnirStatus;
import com.projecthero.mod.hammer.ThorWeapon;
import com.projecthero.mod.item.ModDataComponents;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.ThorPowers;
import com.projecthero.mod.stormbreaker.StormbreakerEntity;
import com.projecthero.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.1: "Thor players should be able to call Stormbreaker and Mjolnir interchangeably" -- the call key considers
 * both bound weapons (one with you: call the other; neither: the closer; only one bound: that one; both with you:
 * nothing), and Stormbreaker now binds and is tracked with Mjolnir's anti-duplication guarantees. Every weapon here is
 * bound to its own mock player and every test entity stays inside its own test area.
 */
public class ThorRecallV0151GameTests implements FabricGameTest {
	private static final String BATCH = "thor_recall_v0151";

	/** A worthy survival Thor at {@code relative}; Mjolnir bound in the main hand and/or a Stormbreaker bound in slot 8. */
	private static ServerPlayer thor(GameTestHelper helper, Vec3 relative, boolean hammer, boolean axe) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
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
			p.getInventory().tick(); // the real binding path: StormbreakerItem#inventoryTick
		}
		return p;
	}

	private static MjolnirRegistry registry(GameTestHelper helper) {
		return MjolnirRegistry.get(helper.getLevel());
	}

	private static UUID hammerId(ServerPlayer p) {
		return p.getAttachedOrElse(ModAttachments.BOUND_HAMMER_ID, null);
	}

	private static UUID axeId(ServerPlayer p) {
		return p.getAttachedOrElse(ModAttachments.BOUND_STORMBREAKER_ID, null);
	}

	private static boolean isId(ItemStack stack, UUID id) {
		return !stack.isEmpty() && id.equals(stack.get(ModDataComponents.HAMMER_ID));
	}

	/** Takes the bound axe out of the player's inventory and returns it. */
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

	private static int generation(ItemStack stack) {
		Integer gen = stack.get(ModDataComponents.HAMMER_GENERATION);
		return gen == null ? -1 : gen;
	}

	/** Every live in-world copy of weapon {@code id}: Mjolnir entities, thrown axes and dropped items. */
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

	private static ItemEntity dropAxe(GameTestHelper helper, ItemStack axe, Vec3 relative) {
		Vec3 at = helper.absoluteVec(relative);
		ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, axe);
		item.setDeltaMovement(Vec3.ZERO);
		item.setUnlimitedLifetime();
		helper.getLevel().addFreshEntity(item);
		return item;
	}

	private static MjolnirEntity restHammer(GameTestHelper helper, ItemStack hammer, Vec3 relative) {
		MjolnirEntity entity = MjolnirEntity.createResting(helper.getLevel(), null, hammer,
				helper.absoluteVec(relative), Vec3.ZERO, false);
		helper.getLevel().addFreshEntity(entity);
		return entity;
	}

	private static void cleanUp(GameTestHelper helper, UUID id, ServerPlayer... players) {
		if (id != null) {
			worldCopies(helper, id).forEach(Entity::discard);
		}
		for (ServerPlayer p : players) {
			if (!p.isRemoved()) {
				helper.getLevel().getServer().getPlayerList().remove(p);
			}
		}
	}

	// ---------------- binding ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void stormbreakerBindsToTheFirstWorthyCarrierOnly(GameTestHelper helper) {
		ServerPlayer thor = thor(helper, new Vec3(1.5, 2, 1.5), true, true);
		UUID id = axeId(thor);
		helper.assertTrue(id != null, "carrying Stormbreaker bound it to the worthy player");
		ItemStack axe = thor.getInventory().getItem(8);
		helper.assertTrue(thor.getUUID().equals(axe.get(ModDataComponents.BOUND_OWNER)), "the axe names its owner");
		Optional<HammerRecord> record = registry(helper).record(id);
		helper.assertTrue(record.isPresent() && record.get().isOwnedBy(thor.getUUID()), "the registry knows the owner");
		helper.assertTrue(record.get().weapon() == ThorWeapon.STORMBREAKER, "and records it as a Stormbreaker");
		helper.assertTrue(record.get().placement() == HammerRecord.Placement.CARRIED, "carried by them");

		// a second axe stays unbound -- one each
		thor.getInventory().setItem(9, new ItemStack(ModItems.STORMBREAKER));
		thor.getInventory().tick();
		helper.assertTrue(thor.getInventory().getItem(9).get(ModDataComponents.BOUND_OWNER) == null, "a second axe stays unbound");
		helper.assertTrue(id.equals(axeId(thor)), "and the first stays theirs");

		// the unworthy never bind one
		ServerPlayer mortal = helper.makeMockServerPlayerInLevel();
		mortal.setGameMode(GameType.SURVIVAL);
		mortal.getInventory().setItem(3, new ItemStack(ModItems.STORMBREAKER));
		mortal.getInventory().tick();
		helper.assertTrue(axeId(mortal) == null, "an unworthy carrier binds nothing");
		helper.assertTrue(mortal.getInventory().getItem(3).get(ModDataComponents.BOUND_OWNER) == null, "the axe stays unbound");

		// another Thor picking up someone else's axe never takes it over
		ServerPlayer other = thor(helper, new Vec3(3.5, 2, 1.5), false, false);
		other.getInventory().setItem(4, takeAxe(thor));
		other.getInventory().tick();
		helper.assertTrue(axeId(other) == null, "a borrowed axe does not bind to the borrower");
		helper.assertTrue(thor.getUUID().equals(other.getInventory().getItem(4).get(ModDataComponents.BOUND_OWNER)),
				"it still answers to its owner");
		cleanUp(helper, id, thor, mortal, other);
		helper.succeed();
	}

	// ---------------- rule 1: one with you -> call the other ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 100)
	public void holdingMjolnirCallsStormbreakerOffTheGround(GameTestHelper helper) {
		ServerPlayer p = thor(helper, new Vec3(1.5, 2, 1.5), true, true);
		UUID id = axeId(p);
		ItemEntity onGround = dropAxe(helper, takeAxe(p), new Vec3(5.5, 2, 5.5));
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(MjolnirRecall.chooseWeapon(p) == ThorWeapon.STORMBREAKER, "Mjolnir is in hand, so Stormbreaker answers");
			helper.assertTrue(MjolnirRecall.call(p), "the call is answered");
			helper.assertTrue(onGround.isRemoved(), "the axe left the ground");
			List<Entity> copies = worldCopies(helper, id);
			helper.assertTrue(copies.size() == 1 && copies.get(0) instanceof StormbreakerEntity axe && axe.isReturning(),
					"exactly one axe is flying home, got " + copies);
			helper.succeedWhen(() -> {
				helper.assertTrue(isId(p.getMainHandItem(), id), "Stormbreaker arrives in the main hand");
				helper.assertTrue(inventoryCopies(p, hammerId(p)) == 1, "Mjolnir stepped back into the pack");
				helper.assertTrue(worldCopies(helper, id).isEmpty() && inventoryCopies(p, id) == 1, "one axe, never two");
				cleanUp(helper, id, p);
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 100)
	public void holdingStormbreakerCallsMjolnir(GameTestHelper helper) {
		ServerPlayer p = thor(helper, new Vec3(1.5, 2, 1.5), true, true);
		UUID hammer = hammerId(p);
		MjolnirEntity resting = restHammer(helper, p.getMainHandItem().copy(), new Vec3(5.5, 2, 5.5));
		p.setItemInHand(InteractionHand.MAIN_HAND, takeAxe(p));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(MjolnirRecall.chooseWeapon(p) == ThorWeapon.MJOLNIR, "Stormbreaker is in hand, so Mjolnir answers");
			helper.assertTrue(MjolnirRecall.call(p), "the call is answered");
			helper.assertTrue(resting.getState() == MjolnirEntity.State.RETURNING, "the hammer turns for home");
			helper.succeedWhen(() -> {
				helper.assertTrue(isId(p.getMainHandItem(), hammer), "Mjolnir arrives in the main hand");
				helper.assertTrue(inventoryCopies(p, axeId(p)) == 1, "Stormbreaker stepped back into the pack");
				helper.assertTrue(worldCopies(helper, hammer).isEmpty(), "no hammer left in the world");
				cleanUp(helper, hammer, p);
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 60)
	public void aStormbreakerInFlightIsTurnedRoundByTheCall(GameTestHelper helper) {
		ServerPlayer p = thor(helper, new Vec3(1.5, 2, 1.5), true, true);
		UUID id = axeId(p);
		StormbreakerEntity thrown = new StormbreakerEntity(helper.getLevel(), p);
		thrown.setItem(takeAxe(p));
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 3, 4.5));
		thrown.setPos(at.x, at.y, at.z);
		helper.getLevel().addFreshEntity(thrown);
		helper.runAfterDelay(1, () -> {
			thrown.setDeltaMovement(Vec3.ZERO);
			helper.assertTrue(MjolnirRecall.call(p), "the call is answered");
			helper.assertTrue(thrown.isReturning() && thrown.wasRecalled(), "the same axe turns for home, recalled");
			helper.assertTrue(worldCopies(helper, id).size() == 1, "and it is the only one");
			cleanUp(helper, id, p);
			helper.succeed();
		});
	}

	// ---------------- rule 2: neither with you -> the closer ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 60)
	public void withNeitherInHandTheCloserOneAnswers(GameTestHelper helper) {
		ServerPlayer p = thor(helper, new Vec3(1.5, 2, 1.5), true, true);
		UUID hammer = hammerId(p);
		UUID axe = axeId(p);
		MjolnirEntity resting = restHammer(helper, p.getMainHandItem().copy(), new Vec3(6.5, 2, 6.5));
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		ItemEntity axeItem = dropAxe(helper, takeAxe(p), new Vec3(3.5, 2, 2.5));
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(MjolnirRecall.chooseWeapon(p) == ThorWeapon.STORMBREAKER, "the nearer axe is chosen");
			Vec3 far = helper.absoluteVec(new Vec3(7.5, 2, 7.5));
			axeItem.setPos(far.x, far.y, far.z);
			helper.assertTrue(MjolnirRecall.chooseWeapon(p) == ThorWeapon.MJOLNIR, "now the nearer hammer is chosen");

			// an unloaded / other-dimension weapon ranks behind any in this dimension...
			MjolnirRegistry registry = registry(helper);
			ItemStack axeStack = axeItem.getItem().copy();
			axeItem.discard();
			Level nether = helper.getLevel().getServer().getLevel(Level.NETHER);
			if (nether != null) {
				registry.noteContainer(axeStack, (net.minecraft.server.level.ServerLevel) nether, new BlockPos(0, 64, 0));
				helper.assertTrue(MjolnirRecall.chooseWeapon(p) == ThorWeapon.MJOLNIR, "a weapon in another dimension is farthest");
				// ...but still answers when it is the only option
				resting.discard();
				p.setAttached(ModAttachments.BOUND_HAMMER_ID, null);
				helper.assertTrue(MjolnirRecall.chooseWeapon(p) == ThorWeapon.STORMBREAKER,
						"with no Mjolnir to answer, the other-dimension axe is called");
				p.setAttached(ModAttachments.BOUND_HAMMER_ID, hammer);
			}
			cleanUp(helper, hammer, p);
			cleanUp(helper, axe);
			helper.succeed();
		});
	}

	// ---------------- rule 3: only one bound -> that one ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 60)
	public void boundToOnlyMjolnirKeepsTheOldBehaviour(GameTestHelper helper) {
		ServerPlayer p = thor(helper, new Vec3(1.5, 2, 1.5), true, false);
		UUID hammer = hammerId(p);
		p.getInventory().setItem(5, p.getMainHandItem());
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.assertTrue(MjolnirRecall.chooseWeapon(p) == ThorWeapon.MJOLNIR, "only Mjolnir is bound");
		helper.assertTrue(MjolnirRecall.call(p), "the call is answered");
		helper.assertTrue(isId(p.getMainHandItem(), hammer), "Mjolnir swaps from the pack into the main hand");
		helper.assertTrue(worldCopies(helper, hammer).isEmpty(), "nothing is spawned");
		cleanUp(helper, hammer, p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 100)
	public void boundToOnlyStormbreakerCallsIt(GameTestHelper helper) {
		ServerPlayer p = thor(helper, new Vec3(1.5, 2, 1.5), false, true);
		UUID id = axeId(p);
		helper.assertTrue(hammerId(p) == null, "no Mjolnir bound");
		ItemStack axe = takeAxe(p);
		// with the axe in hand and no Mjolnir anywhere to answer, the axe's own feedback is given
		p.setItemInHand(InteractionHand.MAIN_HAND, axe);
		helper.assertTrue(MjolnirRecall.chooseWeapon(p) == ThorWeapon.STORMBREAKER, "only Stormbreaker can answer");
		helper.assertTrue(MjolnirRecall.call(p), "already in hand counts as answered");
		helper.assertTrue(isId(p.getMainHandItem(), id), "and it stays there");
		// on the ground, it is called home
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		dropAxe(helper, axe, new Vec3(4.5, 2, 4.5));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(MjolnirRecall.chooseWeapon(p) == ThorWeapon.STORMBREAKER, "the only bound weapon");
			helper.assertTrue(MjolnirRecall.call(p), "the call is answered");
			helper.succeedWhen(() -> {
				helper.assertTrue(isId(p.getMainHandItem(), id), "Stormbreaker comes home to the hand");
				helper.assertTrue(worldCopies(helper, id).isEmpty(), "with nothing left behind");
				cleanUp(helper, id, p);
			});
		});
	}

	// ---------------- rule 4: both with you -> nothing ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void withBothAlreadyOnYouNothingMoves(GameTestHelper helper) {
		ServerPlayer p = thor(helper, new Vec3(1.5, 2, 1.5), true, true);
		UUID hammer = hammerId(p);
		UUID axe = axeId(p);
		helper.assertTrue(MjolnirRecall.chooseWeapon(p) == null, "both are with the player");
		helper.assertTrue(MjolnirRecall.call(p), "the call is acknowledged");
		helper.assertTrue(isId(p.getMainHandItem(), hammer), "Mjolnir stays in the hand");
		helper.assertTrue(isId(p.getInventory().getItem(8), axe), "Stormbreaker stays in its slot");
		helper.assertTrue(worldCopies(helper, hammer).isEmpty() && worldCopies(helper, axe).isEmpty(), "nothing is spawned");
		cleanUp(helper, hammer, p);
		helper.succeed();
	}

	// ---------------- anti-duplication ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 60)
	public void callingStormbreakerOutOfAChestMovesItNeverCopiesIt(GameTestHelper helper) {
		ServerPlayer p = thor(helper, new Vec3(1.5, 2, 1.5), true, true);
		UUID id = axeId(p);
		ItemStack axe = takeAxe(p);
		int before = generation(axe);
		helper.setBlock(new BlockPos(5, 2, 5), Blocks.CHEST);
		ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(5, 2, 5));
		chest.setItem(4, axe.copy());

		helper.assertTrue(MjolnirRecall.call(p), "the call is answered");
		helper.assertTrue(chest.getItem(4).isEmpty(), "the axe LEFT the chest");
		List<Entity> copies = worldCopies(helper, id);
		helper.assertTrue(copies.size() == 1 && copies.get(0) instanceof StormbreakerEntity, "exactly one axe is on its way");
		helper.assertTrue(generation(((StormbreakerEntity) copies.get(0)).getItem()) > before, "at a newer generation");
		helper.assertTrue(registry(helper).isStale(axe), "so any lingering copy of the old stack is a ghost");

		// and a ghost dropped on the ground deletes itself instead of becoming a second axe
		ItemEntity ghost = dropAxe(helper, axe.copy(), new Vec3(3.5, 2, 5.5));
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(ghost.isRemoved(), "the ghost axe item is gone");
			cleanUp(helper, id, p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 60)
	public void stormbreakerIsCalledOutOfAnotherPlayersHand(GameTestHelper helper) {
		ServerPlayer owner = thor(helper, new Vec3(1.5, 2, 1.5), true, true);
		UUID id = axeId(owner);
		ServerPlayer borrower = thor(helper, new Vec3(5.5, 2, 5.5), false, false);
		borrower.setItemInHand(InteractionHand.MAIN_HAND, takeAxe(owner));

		helper.assertTrue(MjolnirRecall.chooseWeapon(owner) == ThorWeapon.STORMBREAKER, "Mjolnir in hand -> Stormbreaker");
		helper.assertTrue(MjolnirRecall.call(owner), "the call is answered");
		helper.assertTrue(borrower.getMainHandItem().isEmpty(), "the axe left the borrower's hand");
		helper.assertTrue(worldCopies(helper, id).size() == 1, "and exactly one is on its way");
		cleanUp(helper, id, owner, borrower);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 100)
	public void anUnreachableStormbreakerIsRebuiltAtANewGeneration(GameTestHelper helper) {
		// boxed in so the rebuilt axe's fly-in starts inside the loaded test area, right next to him
		for (int x = 2; x <= 4; x++) {
			for (int y = 1; y <= 4; y++) {
				for (int z = 2; z <= 4; z++) {
					boolean interior = x == 3 && z == 3 && (y == 2 || y == 3);
					if (!interior) {
						helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
					}
				}
			}
		}
		ServerPlayer p = thor(helper, new Vec3(3.5, 2, 3.5), true, true);
		UUID id = axeId(p);
		ItemStack lost = takeAxe(p);
		int before = generation(lost);
		helper.assertTrue(MjolnirRecall.call(p), "the call is answered by reconstruction");
		helper.assertTrue(registry(helper).isStale(lost), "the lost copy is now a ghost");
		helper.succeedWhen(() -> {
			ItemStack back = p.getMainHandItem();
			if (!isId(back, id)) {
				List<Entity> copies = worldCopies(helper, id);
				helper.assertTrue(copies.size() == 1, "one rebuilt axe in flight or in hand");
				back = ((StormbreakerEntity) copies.get(0)).getItem();
			}
			helper.assertTrue(back.is(ModItems.STORMBREAKER), "it is rebuilt as a Stormbreaker");
			helper.assertTrue(generation(back) == before + 1, "one generation newer, got " + generation(back));
			helper.assertTrue(p.getUUID().equals(back.get(ModDataComponents.BOUND_OWNER)), "still bound to its owner");
			cleanUp(helper, id, p);
		});
	}

	// ---------------- saved data ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void registryRecordsSurviveASaveIncludingOldMjolnirRecords(GameTestHelper helper) {
		var provider = helper.getLevel().registryAccess();
		UUID owner = UUID.randomUUID();
		HammerRecord hammer = new HammerRecord(UUID.randomUUID(), 3, Optional.of(owner), "Thor",
				HammerRecord.Placement.ENTITY, Optional.empty(), Level.OVERWORLD, new BlockPos(10, 64, -5),
				Optional.of(UUID.randomUUID()), MjolnirStatus.RESTING);
		HammerRecord axe = new HammerRecord(UUID.randomUUID(), 1, Optional.of(owner), "Thor",
				HammerRecord.Placement.CARRIED, Optional.of(owner), Level.NETHER, new BlockPos(1, 2, 3),
				Optional.empty(), MjolnirStatus.STORED, ThorWeapon.STORMBREAKER);
		helper.assertTrue(hammer.weapon() == ThorWeapon.MJOLNIR, "the old-shape constructor makes a Mjolnir record");

		// round trip through the codec
		for (HammerRecord record : List.of(hammer, axe)) {
			Tag encoded = HammerRecord.CODEC.encodeStart(NbtOps.INSTANCE, record).getOrThrow();
			HammerRecord decoded = HammerRecord.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
			helper.assertTrue(record.equals(decoded), "record survives the codec: " + record.weapon());
		}

		// a record written before v0.15.1 has no "weapon" field -- it loads as the Mjolnir it was
		CompoundTag oldFormat = (CompoundTag) HammerRecord.CODEC.encodeStart(NbtOps.INSTANCE, hammer).getOrThrow();
		oldFormat.remove("weapon");
		helper.assertFalse(oldFormat.contains("weapon"), "the old shape has no weapon field");
		CompoundTag file = new CompoundTag();
		ListTag list = new ListTag();
		list.add(oldFormat);
		list.add(HammerRecord.CODEC.encodeStart(NbtOps.INSTANCE, axe).getOrThrow());
		file.put("Hammers", list);
		MjolnirRegistry loaded = MjolnirRegistry.load(file, provider);
		helper.assertTrue(loaded.record(hammer.hammerId()).equals(Optional.of(hammer)), "the old Mjolnir record loads intact");
		helper.assertTrue(loaded.record(axe.hammerId()).equals(Optional.of(axe)), "the Stormbreaker record loads intact");

		// and the whole registry round-trips through save/load
		MjolnirRegistry again = MjolnirRegistry.load(loaded.save(new CompoundTag(), provider), provider);
		helper.assertTrue(again.record(axe.hammerId()).map(HammerRecord::weapon).orElse(null) == ThorWeapon.STORMBREAKER,
				"the weapon kind survives a save");
		helper.assertTrue(again.record(hammer.hammerId()).equals(Optional.of(hammer)), "so does the Mjolnir record");
		helper.succeed();
	}
}
