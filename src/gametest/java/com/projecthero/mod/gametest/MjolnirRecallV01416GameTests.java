package com.projecthero.mod.gametest;

import java.util.List;
import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.entity.MjolnirEntity;
import com.projecthero.mod.hammer.MjolnirGuard;
import com.projecthero.mod.hammer.MjolnirRecall;
import com.projecthero.mod.hammer.MjolnirRegistry;
import com.projecthero.mod.item.ModDataComponents;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.ThorPowers;
import com.projecthero.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.16: "calling the hammer from unloaded chunks, a chest or someone else's inventory duplicates it". Every test
 * here runs in its own batch so no other suite's unbound hammer can answer these players' calls, and every hammer is
 * bound to its own mock player so the tests cannot answer each other's.
 */
public class MjolnirRecallV01416GameTests implements FabricGameTest {
	private static final String BATCH = "mjolnir_recall_v01416";

	private static ServerPlayer boundThor(GameTestHelper helper, Vec3 relative) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(relative);
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		Worthiness.setScore(p, Worthiness.TEST_WORTHY_SCORE);
		ItemStack hammer = new ItemStack(ModItems.MJOLNIR);
		p.setItemInHand(InteractionHand.MAIN_HAND, hammer);
		ThorPowers.toggleBinding(p, hammer);
		return p;
	}

	private static UUID idOf(ServerPlayer p) {
		return p.getAttachedOrElse(ModAttachments.BOUND_HAMMER_ID, null);
	}

	private static MjolnirRegistry registry(GameTestHelper helper) {
		return MjolnirRegistry.get(helper.getLevel());
	}

	private static List<MjolnirEntity> hammerEntities(GameTestHelper helper, UUID id) {
		List<MjolnirEntity> out = new java.util.ArrayList<>();
		helper.getLevel().getEntities(EntityTypeTest.forClass(MjolnirEntity.class),
				e -> !e.isRemoved() && id.equals(e.getItem().get(ModDataComponents.HAMMER_ID)), out);
		return out;
	}

	private static void discardAll(GameTestHelper helper, UUID id) {
		hammerEntities(helper, id).forEach(MjolnirEntity::discard);
	}

	private static ChestBlockEntity chestAt(GameTestHelper helper, BlockPos relative) {
		helper.setBlock(relative, Blocks.CHEST);
		return (ChestBlockEntity) helper.getBlockEntity(relative);
	}

	private static int generation(ItemStack stack) {
		Integer gen = stack.get(ModDataComponents.HAMMER_GENERATION);
		return gen == null ? -1 : gen;
	}

	/** Turns the player's current hammer into a ghost: returns the stale copy and re-stamps the held one as current. */
	private static ItemStack makeGhost(GameTestHelper helper, ServerPlayer p) {
		ItemStack held = p.getMainHandItem();
		ItemStack ghost = held.copy();
		int next = registry(helper).reconstruct(idOf(p));
		held.set(ModDataComponents.HAMMER_GENERATION, next);
		return ghost;
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 60)
	public void recallFromALoadedChestMovesTheHammerInsteadOfCopyingIt(GameTestHelper helper) {
		ServerPlayer p = boundThor(helper, new Vec3(1.5, 2, 1.5));
		UUID id = idOf(p);
		ItemStack hammer = p.getMainHandItem().copy();
		int before = generation(hammer);
		ChestBlockEntity chest = chestAt(helper, new BlockPos(5, 2, 5));
		chest.setItem(4, hammer);
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

		helper.assertTrue(MjolnirRecall.call(p), "the call is answered");
		helper.assertTrue(chest.getItem(4).isEmpty(), "the hammer LEFT the chest -- no copy stays behind");
		List<MjolnirEntity> flying = hammerEntities(helper, id);
		helper.assertTrue(flying.size() == 1, "exactly one hammer is on its way, got " + flying.size());
		helper.assertTrue(generation(flying.get(0).getItem()) > before, "the moved hammer carries a newer generation");
		helper.assertTrue(registry(helper).isStale(hammer), "so the old stack, wherever a copy of it lingered, is a ghost");
		discardAll(helper, id);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 60)
	public void aGhostInAChestIsDeletedTheMomentTheChestIsOpened(GameTestHelper helper) {
		ServerPlayer p = boundThor(helper, new Vec3(1.5, 2, 1.5));
		ItemStack ghost = makeGhost(helper, p);
		ChestBlockEntity chest = chestAt(helper, new BlockPos(3, 2, 3));
		chest.setItem(0, ghost);
		chest.setItem(1, new ItemStack(Items.DIAMOND));
		// what openMenu would install, without a client on the other end to show it to
		p.containerMenu = ChestMenu.threeRows(1, p.getInventory(), chest);
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.getItem(0).isEmpty(), "the ghost hammer is removed from the open chest");
			helper.assertTrue(chest.getItem(1).is(Items.DIAMOND), "nothing else in the chest is touched");
			helper.assertTrue(p.getMainHandItem().is(ModItems.MJOLNIR), "the real hammer is untouched");
			p.doCloseContainer();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void ghostsAreSweptFromContainersShulkersInventoriesAndEnderChests(GameTestHelper helper) {
		ServerPlayer p = boundThor(helper, new Vec3(1.5, 2, 1.5));
		MjolnirRegistry registry = registry(helper);
		ItemStack ghost = makeGhost(helper, p);

		// a closed chest (BLOCK_ENTITY_LOAD / recall sweeps use purgeContainer), including a ghost packed in a shulker box
		ChestBlockEntity chest = chestAt(helper, new BlockPos(3, 2, 3));
		ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
		shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(ghost.copy(), new ItemStack(Items.APPLE))));
		chest.setItem(0, ghost.copy());
		chest.setItem(1, shulker);
		helper.assertTrue(MjolnirGuard.purgeContainer(registry, chest) == 2, "both chest slots needed cleaning");
		helper.assertTrue(chest.getItem(0).isEmpty(), "the loose ghost is gone");
		ItemContainerContents inner = chest.getItem(1).get(DataComponents.CONTAINER);
		helper.assertTrue(inner != null && inner.nonEmptyStream().noneMatch(s -> s.is(ModItems.MJOLNIR)),
				"the ghost inside the shulker box is gone");
		helper.assertTrue(inner.nonEmptyStream().anyMatch(s -> s.is(Items.APPLE)), "the shulker's other contents are kept");

		// the player's own inventory (inventoryTick) and ender chest (join sweep)
		p.getInventory().setItem(9, ghost.copy());
		p.getEnderChestInventory().setItem(0, ghost.copy());
		p.getInventory().tick();
		helper.assertTrue(p.getInventory().getItem(9).isEmpty(), "a ghost in the backpack deletes itself on tick");
		MjolnirGuard.purgePlayer(registry, p);
		helper.assertTrue(p.getEnderChestInventory().getItem(0).isEmpty(), "a ghost in the ender chest is swept");
		helper.assertTrue(p.getMainHandItem().is(ModItems.MJOLNIR) && !registry.isStale(p.getMainHandItem()),
				"and the one real hammer survives all of it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 100)
	public void aReconstructedHammerKeepsItsNameAndEnchantments(GameTestHelper helper) {
		// boxed in so the hammer's fly-in starts inside the loaded test area, right next to him
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
		ServerPlayer p = boundThor(helper, new Vec3(3.5, 2, 3.5));
		UUID id = idOf(p);
		MjolnirRegistry registry = registry(helper);
		ItemStack hammer = p.getMainHandItem();
		hammer.set(DataComponents.CUSTOM_NAME, Component.literal("Stormcaller"));
		Holder<Enchantment> sharpness = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
				.getHolderOrThrow(Enchantments.SHARPNESS);
		hammer.enchant(sharpness, 3);
		registry.noteCarried(hammer, p, true); // what the periodic inventory refresh records
		int before = generation(hammer);
		// the hammer is now "somewhere unreachable": gone from every loaded place
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

		helper.assertTrue(MjolnirRecall.call(p), "the call is answered by reconstruction");
		helper.succeedWhen(() -> {
			ItemStack back = p.getMainHandItem();
			if (!back.is(ModItems.MJOLNIR)) {
				List<MjolnirEntity> flying = hammerEntities(helper, id);
				helper.assertTrue(!flying.isEmpty(), "the rebuilt hammer is in flight or in hand");
				back = flying.get(0).getItem();
			}
			helper.assertTrue(id.equals(back.get(ModDataComponents.HAMMER_ID)), "same hammer identity");
			helper.assertTrue(generation(back) == before + 1, "one generation newer, got " + generation(back));
			helper.assertTrue(Component.literal("Stormcaller").equals(back.get(DataComponents.CUSTOM_NAME)), "kept its custom name");
			helper.assertTrue(back.getEnchantments().getLevel(sharpness) == 3, "kept Sharpness III");
			helper.assertTrue(p.getUUID().equals(back.get(ModDataComponents.BOUND_OWNER)), "still bound to its owner");
			discardAll(helper, id);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void legacyHammersAreAdoptedNotDeleted(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		MjolnirRegistry registry = registry(helper);

		// 1. no identity at all (a pre-registry hammer, /give, a loot table)
		ItemStack bare = new ItemStack(ModItems.MJOLNIR);
		p.getInventory().setItem(5, bare);
		p.getInventory().tick();
		ItemStack adopted = p.getInventory().getItem(5);
		helper.assertTrue(adopted.is(ModItems.MJOLNIR), "an unidentified hammer is kept");
		helper.assertTrue(adopted.get(ModDataComponents.HAMMER_ID) != null && adopted.get(ModDataComponents.HAMMER_GENERATION) != null,
				"and given an id and a generation");

		// 2. an id but no generation stamp, while the registry's generation for it has moved on
		UUID id = UUID.randomUUID();
		ItemStack seed = new ItemStack(ModItems.MJOLNIR);
		seed.set(ModDataComponents.HAMMER_ID, id);
		seed.set(ModDataComponents.HAMMER_GENERATION, 0);
		registry.noteCarried(seed, p, false);
		registry.reconstruct(id);
		int current = registry.reconstruct(id);
		ItemStack legacy = new ItemStack(ModItems.MJOLNIR);
		legacy.set(ModDataComponents.HAMMER_ID, id);
		helper.assertFalse(registry.isStale(legacy), "a hammer with no generation stamp is never taken for a ghost");
		p.getInventory().setItem(6, legacy);
		p.getInventory().setItem(5, ItemStack.EMPTY);
		MjolnirGuard.purgePlayer(registry, p);
		p.tickCount = 100; // the periodic refresh that records (and stamps) it
		p.getInventory().tick();
		ItemStack kept = p.getInventory().getItem(6);
		helper.assertTrue(kept.is(ModItems.MJOLNIR), "the legacy hammer survives every sweep");
		helper.assertTrue(generation(kept) == current, "and is stamped with the current generation, got " + generation(kept));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 60)
	public void aGhostMjolnirEntityDiscardsItselfAndTheRealOneStays(GameTestHelper helper) {
		ServerPlayer p = boundThor(helper, new Vec3(1.5, 2, 1.5));
		UUID id = idOf(p);
		ItemStack ghost = makeGhost(helper, p);
		MjolnirEntity ghostEntity = MjolnirEntity.createResting(helper.getLevel(), null, ghost,
				helper.absoluteVec(new Vec3(4.5, 2, 4.5)), Vec3.ZERO, false);
		helper.getLevel().addFreshEntity(ghostEntity);
		MjolnirEntity realEntity = MjolnirEntity.createResting(helper.getLevel(), null, p.getMainHandItem().copy(),
				helper.absoluteVec(new Vec3(6.5, 2, 6.5)), Vec3.ZERO, false);
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.getLevel().addFreshEntity(realEntity);
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(ghostEntity.isRemoved(), "the superseded hammer entity is gone");
			helper.assertFalse(realEntity.isRemoved(), "the current one is untouched");
			realEntity.discard();
			discardAll(helper, id);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 60)
	public void aGhostDroppedAsAnItemIsNeverPromotedToAHammer(GameTestHelper helper) {
		ServerPlayer p = boundThor(helper, new Vec3(1.5, 2, 1.5));
		UUID id = idOf(p);
		ItemStack ghost = makeGhost(helper, p);
		int ghostGen = generation(ghost);
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 2, 4.5));
		ItemEntity dropped = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, ghost);
		helper.getLevel().addFreshEntity(dropped);
		helper.runAfterDelay(4, () -> {
			helper.assertTrue(dropped.isRemoved(), "the ghost item is gone");
			helper.assertTrue(hammerEntities(helper, id).stream().noneMatch(e -> generation(e.getItem()) == ghostGen),
					"and it never became a MjolnirEntity");
			discardAll(helper, id);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 60)
	public void recallTakesItOutOfAnotherPlayersEnderChest(GameTestHelper helper) {
		ServerPlayer owner = boundThor(helper, new Vec3(1.5, 2, 1.5));
		UUID id = idOf(owner);
		ServerPlayer other = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(5.5, 2, 5.5));
		other.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		other.getEnderChestInventory().setItem(3, owner.getMainHandItem().copy());
		owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

		helper.assertTrue(MjolnirRecall.call(owner), "the call is answered");
		helper.assertTrue(other.getEnderChestInventory().getItem(3).isEmpty(), "the hammer left the other player's ender chest");
		helper.assertTrue(hammerEntities(helper, id).size() == 1, "and exactly one is on its way");
		discardAll(helper, id);
		helper.succeed();
	}
}
