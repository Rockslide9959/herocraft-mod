package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.ThorPowers;
import com.projecthero.mod.stormbreaker.Bifrost;
import com.projecthero.mod.stormbreaker.StormbreakerEntity;
import com.projecthero.mod.stormbreaker.StormbreakerForge;
import com.projecthero.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.19 Stormbreaker: the recipe, the item's stats, forging in Nether lava (through the testable
 * {@link StormbreakerForge#tickForging} rule -- building a Nether in a gametest isn't practical), the Thor-weapon gate,
 * the returning throw and the Bifrost. The throw and Bifrost tests each run in their own batch so a flying axe can
 * never meet another suite's mobs or players.
 */
public class StormbreakerGameTests implements FabricGameTest {
	private static final String THROW_BATCH = "stormbreaker_throw_v01419";
	private static final String BIFROST_BATCH = "stormbreaker_bifrost_v01419";
	/**
	 * The throw tests run 40 blocks above their structure. Batches run side by side, and other suites' area abilities
	 * (Electrokinesis' chain lightning, which hops onto any living thing within 14 blocks, players included) would
	 * otherwise pick up this suite's zombie and Thor players -- found the hard way.
	 */
	private static final int ALT = 40;

	private static ServerPlayer player(GameTestHelper helper, Vec3 relative, boolean worthy) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(relative);
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		if (worthy) {
			Worthiness.setScore(p, Worthiness.TEST_WORTHY_SCORE);
		}
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.STORMBREAKER));
		return p;
	}

	/**
	 * Logs the mock players out once a test is done. Left standing, a worthy Thor holding Stormbreaker turns up as a
	 * chain target in whatever test is laid out on this spot in a later batch (found the hard way: Electrokinesis'
	 * chain lightning hopped onto a leftover player instead of its stacked zombie).
	 */
	private static void leave(GameTestHelper helper, ServerPlayer... players) {
		for (ServerPlayer p : players) {
			if (!p.isRemoved()) {
				helper.getLevel().getServer().getPlayerList().remove(p);
			}
		}
	}

	private static List<StormbreakerEntity> axes(GameTestHelper helper, ServerPlayer owner) {
		List<StormbreakerEntity> out = new ArrayList<>();
		helper.getLevel().getEntities(EntityTypeTest.forClass(StormbreakerEntity.class),
				e -> !e.isRemoved() && e.getOwner() == owner, out);
		return out;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void unforgedStormbreakerRecipeExists(GameTestHelper helper) {
		var holder = helper.getLevel().getRecipeManager().byKey(ProjectHeroMod.id("unforged_stormbreaker"));
		helper.assertTrue(holder.isPresent(), "a recipe for the Unforged Stormbreaker");
		ItemStack result = holder.get().value().getResultItem(helper.getLevel().registryAccess());
		helper.assertTrue(result.is(ModItems.UNFORGED_STORMBREAKER), "it makes the Unforged Stormbreaker, got " + result);
		var ingredients = holder.get().value().getIngredients();
		int netherite = 0;
		int stars = 0;
		int rods = 0;
		for (var ingredient : ingredients) {
			if (ingredient.test(new ItemStack(Items.NETHERITE_INGOT))) {
				netherite++;
			} else if (ingredient.test(new ItemStack(Items.NETHER_STAR))) {
				stars++;
			} else if (ingredient.test(new ItemStack(Items.BLAZE_ROD))) {
				rods++;
			}
		}
		helper.assertTrue(netherite == 4 && stars == 1 && rods == 2,
				"4 netherite + a nether star + 2 blaze rods, got " + netherite + "/" + stars + "/" + rods);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void stormbreakerHitsForFourteenAndNeitherItemBurns(GameTestHelper helper) {
		ItemStack axe = new ItemStack(ModItems.STORMBREAKER);
		ItemAttributeModifiers modifiers = axe.get(DataComponents.ATTRIBUTE_MODIFIERS);
		helper.assertTrue(modifiers != null, "Stormbreaker carries attribute modifiers");
		double damage = 1.0; // the player's base attack damage
		double speed = 4.0; // the player's base attack speed
		for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
			if (entry.attribute().equals(Attributes.ATTACK_DAMAGE)) {
				damage += entry.modifier().amount();
			} else if (entry.attribute().equals(Attributes.ATTACK_SPEED)) {
				speed += entry.modifier().amount();
			}
		}
		helper.assertTrue(Math.abs(damage - 14.0) < 1e-6, "14 attack damage, got " + damage);
		helper.assertTrue(Math.abs(speed - 0.9) < 1e-6, "0.9 attacks per second, got " + speed);
		helper.assertTrue(axe.getMaxStackSize() == 1 && !axe.isDamageableItem(), "unstackable, no durability");

		var lava = helper.getLevel().damageSources().lava();
		helper.assertTrue(axe.has(DataComponents.FIRE_RESISTANT) && !axe.canBeHurtBy(lava), "Stormbreaker never burns");
		ItemStack unforged = new ItemStack(ModItems.UNFORGED_STORMBREAKER);
		helper.assertTrue(unforged.has(DataComponents.FIRE_RESISTANT) && !unforged.canBeHurtBy(lava),
				"the Unforged Stormbreaker survives lava too");
		helper.succeed();
	}

	private static ItemEntity looseUnforged(GameTestHelper helper) {
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
		ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(ModItems.UNFORGED_STORMBREAKER));
		helper.assertTrue(item instanceof StormbreakerForge.Forgeable, "ItemEntityMixin gives item entities forging progress");
		return item;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void netherLavaForgesStormbreakerAfterTenSeconds(GameTestHelper helper) {
		ItemEntity item = looseUnforged(helper);
		for (int i = 1; i < StormbreakerForge.FORGE_TICKS; i++) {
			helper.assertFalse(StormbreakerForge.tickForging(item, true, true), "not done yet at tick " + i);
		}
		helper.assertTrue(item.getItem().is(ModItems.UNFORGED_STORMBREAKER), "still unforged one tick short of 10 s");
		helper.assertTrue(StormbreakerForge.tickForging(item, true, true), "forged on tick 200");
		helper.assertTrue(item.getItem().is(ModItems.STORMBREAKER), "the item entity now holds Stormbreaker");
		helper.assertTrue(item.getItem().getCount() == 1, "exactly one axe");
		helper.assertTrue(item.getDeltaMovement().y > 0.5, "it is thrown up out of the lava");
		helper.assertFalse(StormbreakerForge.tickForging(item, true, true), "a forged axe is not forged again");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void lavaOutsideTheNetherNeverForgesIt(GameTestHelper helper) {
		ItemEntity item = looseUnforged(helper);
		for (int i = 0; i < StormbreakerForge.FORGE_TICKS * 3; i++) {
			helper.assertFalse(StormbreakerForge.tickForging(item, true, false), "overworld lava never forges it");
		}
		helper.assertTrue(item.getItem().is(ModItems.UNFORGED_STORMBREAKER), "still unforged after 30 s of overworld lava");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void pullingItOutOfTheLavaLosesTheHeat(GameTestHelper helper) {
		ItemEntity item = looseUnforged(helper);
		StormbreakerForge.Forgeable forge = (StormbreakerForge.Forgeable) item;
		for (int i = 0; i < 150; i++) {
			StormbreakerForge.tickForging(item, true, true);
		}
		// a brief bob out of the lava keeps the heat...
		for (int i = 0; i < StormbreakerForge.OUT_OF_LAVA_GRACE; i++) {
			StormbreakerForge.tickForging(item, false, true);
		}
		helper.assertTrue(forge.projecthero$forgeTicks() == 150, "a bob at the surface keeps the progress");
		// ...a real pull-out loses it
		StormbreakerForge.tickForging(item, false, true);
		helper.assertTrue(forge.projecthero$forgeTicks() == 0, "out of the lava too long: progress resets");
		for (int i = 0; i < 150; i++) {
			StormbreakerForge.tickForging(item, true, true);
		}
		helper.assertTrue(item.getItem().is(ModItems.UNFORGED_STORMBREAKER), "it has to start the 10 s over");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void stormbreakerCountsAsThorsWeaponOnlyInAWorthyHand(GameTestHelper helper) {
		ServerPlayer worthy = player(helper, new Vec3(1.5, 2, 1.5), true);
		helper.assertTrue(ThorPowers.isHoldingThorWeapon(worthy), "Stormbreaker is a Thor weapon");
		helper.assertTrue(ThorPowers.wieldsThorWeapon(worthy), "and wields Thor's kit in a worthy hand");
		helper.assertTrue(AbilityRouter.hasThorContext(worthy), "Thor's keys route to Thor's powers");
		helper.assertFalse(ThorPowers.isHoldingMjolnir(worthy), "but it is not Mjolnir");

		ServerPlayer mjolnir = player(helper, new Vec3(3.5, 2, 1.5), true);
		mjolnir.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MJOLNIR));
		helper.assertTrue(ThorPowers.isHoldingThorWeapon(mjolnir), "Mjolnir is a Thor weapon too");

		ServerPlayer unworthy = player(helper, new Vec3(5.5, 2, 1.5), false);
		helper.assertTrue(ThorPowers.isHoldingThorWeapon(unworthy), "anyone can hold Stormbreaker");
		helper.assertFalse(ThorPowers.wieldsThorWeapon(unworthy), "but the unworthy get no Thor kit");
		helper.assertFalse(AbilityRouter.hasThorContext(unworthy), "and their keys stay their own");
		leave(helper, worthy, mjolnir, unworthy);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void anUnworthyRightClickOnlyClangs(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(1.5, 2, 1.5), false);
		InteractionResult result = p.getMainHandItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND).getResult();
		helper.assertTrue(result == InteractionResult.FAIL, "the unworthy right-click fails, got " + result);
		helper.assertTrue(p.getMainHandItem().is(ModItems.STORMBREAKER), "the axe never leaves their hand");
		helper.assertTrue(axes(helper, p).isEmpty(), "nothing is thrown");
		p.setShiftKeyDown(true);
		p.getMainHandItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertFalse(p.getCooldowns().isOnCooldown(ModItems.STORMBREAKER), "and no Bifrost either");
		leave(helper, p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = THROW_BATCH, timeoutTicks = 100)
	public void aThrownStormbreakerEmptiesTheHandAndComesBack(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(1.5, 2 + ALT, 1.5), true);
		ItemStack original = p.getMainHandItem().copy();
		InteractionResult result = p.getMainHandItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND).getResult();
		helper.assertTrue(result.consumesAction(), "the worthy right-click throws, got " + result);
		helper.assertTrue(p.getMainHandItem().isEmpty(), "the throw empties the hand -- one axe, never two");
		List<StormbreakerEntity> thrown = axes(helper, p);
		helper.assertTrue(thrown.size() == 1, "exactly one axe in flight, got " + thrown.size());
		StormbreakerEntity axe = thrown.get(0);
		helper.assertTrue(axe.getItem().is(ModItems.STORMBREAKER) && ItemStack.isSameItemSameComponents(axe.getItem(), original),
				"the axe carries the thrown stack itself");
		// turn it round at once so it never leaves the test area, then let it fly home on its own
		axe.beginReturn();
		helper.succeedWhen(() -> {
			helper.assertTrue(axe.isRemoved(), "the axe has arrived");
			helper.assertTrue(p.getMainHandItem().is(ModItems.STORMBREAKER), "and is back in the main hand");
			helper.assertTrue(axes(helper, p).isEmpty(), "with nothing left flying");
			leave(helper, p);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = THROW_BATCH, timeoutTicks = 100)
	public void theThrowPiercesAndHitsForTwenty(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(1.5, 2 + ALT, 1.5), true);
		// a wall right behind the target: the axe strikes it and turns for home inside the test area instead of flying
		// 40 blocks on into whatever test is running next door
		for (int y = 1; y <= 5; y++) {
			for (int x = 0; x <= 3; x++) {
				helper.setBlock(new BlockPos(x, y + ALT, 6), Blocks.STONE);
			}
		}
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2 + ALT, 4));
		zombie.setNoAi(true);
		float before = zombie.getHealth();
		p.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.position().add(0.0, 1.0, 0.0));
		helper.assertTrue(StormbreakerEntity.throwFrom(p), "thrown");
		StormbreakerEntity axe = axes(helper, p).get(0);
		helper.succeedWhen(() -> {
			helper.assertTrue(axe.piercedCount() >= 1, "the axe struck the zombie");
			// 20 damage, less the zombie's own 2 points of natural armour
			helper.assertTrue(zombie.isDeadOrDying() || before - zombie.getHealth() >= StormbreakerEntity.DAMAGE - 1.0f,
					"for 20 damage (health " + zombie.getHealth() + " of " + before + ")");
			axe.beginReturn(); // (the wall has usually done this already)
			helper.assertTrue(axe.isRemoved() && p.getMainHandItem().is(ModItems.STORMBREAKER), "and it came home");
			zombie.discard();
			leave(helper, p);
			for (int y = 1; y <= 5; y++) {
				for (int x = 0; x <= 3; x++) {
					helper.setBlock(new BlockPos(x, y + ALT, 6), Blocks.AIR);
				}
			}
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = THROW_BATCH, timeoutTicks = 40)
	public void anOrphanedAxeDropsAsAnItemThatNeverDespawns(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(1.5, 2 + ALT, 1.5), true);
		helper.assertTrue(StormbreakerEntity.throwFrom(p), "thrown");
		StormbreakerEntity axe = axes(helper, p).get(0);
		// the thrower logs out mid-flight (Projectile.setOwner(null) is a no-op in vanilla, so that cannot orphan it)
		leave(helper, p);
		axe.beginReturn();
		helper.succeedWhen(() -> {
			helper.assertTrue(axe.isRemoved(), "with nobody to return to, the flying axe is gone");
			// it falls from eye height to the floor, so look well below where the axe was
			List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
					axe.getBoundingBox().inflate(3.0, 8.0, 3.0), e -> !e.isRemoved() && e.getItem().is(ModItems.STORMBREAKER));
			helper.assertTrue(dropped.size() == 1, "and exactly one Stormbreaker item is left behind, got " + dropped.size());
			ItemEntity item = dropped.get(0);
			// unlimited lifetime = vanilla's "never despawn" age; it stays put for good
			helper.assertTrue(item.getAge() < 0, "it never despawns (age " + item.getAge() + ")");
			item.discard();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BIFROST_BATCH, timeoutTicks = 40)
	public void bifrostCarriesAWorthyThorToTheBlockHeIsLookingAt(GameTestHelper helper) {
		// a stone pillar-top 6 blocks out -- beyond arm's reach, so it is a Bifrost target rather than a block click
		BlockPos stone = new BlockPos(1, 2, 6);
		helper.setBlock(stone, Blocks.STONE);
		ServerPlayer p = player(helper, new Vec3(1.5, 2, 0.5), true);
		Vec3 top = helper.absoluteVec(new Vec3(1.5, 3.0, 6.6));
		p.lookAt(EntityAnchorArgument.Anchor.EYES, top);
		helper.assertFalse(Bifrost.targetsBlockInReach(p), "the pillar is out of arm's reach");
		p.setShiftKeyDown(true);
		InteractionResult result = p.getMainHandItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND).getResult();
		helper.assertTrue(result.consumesAction(), "Sneak + right-click opens the Bifrost, got " + result);
		BlockPos landed = p.blockPosition();
		BlockPos expected = helper.absolutePos(stone.above());
		helper.assertTrue(landed.equals(expected), "landed on top of the pillar at " + expected + ", got " + landed);
		helper.assertTrue(p.fallDistance == 0.0f, "no fall damage carried over");
		helper.assertTrue(p.getCooldowns().isOnCooldown(ModItems.STORMBREAKER), "30 s item cooldown started");
		helper.assertTrue(p.getMainHandItem().is(ModItems.STORMBREAKER), "the axe stays in hand -- no throw, no bind");
		helper.assertTrue(axes(helper, p).isEmpty(), "nothing was thrown");
		p.setShiftKeyDown(false);
		leave(helper, p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BIFROST_BATCH, timeoutTicks = 40)
	public void sneakClickingABlockInReachLeavesTheBifrostShut(GameTestHelper helper) {
		BlockPos chest = new BlockPos(1, 2, 3);
		helper.setBlock(chest, Blocks.CHEST);
		ServerPlayer p = player(helper, new Vec3(1.5, 2, 1.5), true);
		Vec3 start = p.position();
		p.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(1.5, 2.5, 3.5)));
		helper.assertTrue(Bifrost.targetsBlockInReach(p), "the chest is within reach");
		p.setShiftKeyDown(true);
		InteractionResult result = p.getMainHandItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND).getResult();
		helper.assertTrue(result == InteractionResult.PASS, "the click is left to vanilla, got " + result);
		helper.assertTrue(p.position().distanceToSqr(start) < 1.0E-6, "nobody moved");
		helper.assertFalse(p.getCooldowns().isOnCooldown(ModItems.STORMBREAKER), "no cooldown spent");
		p.setShiftKeyDown(false);
		leave(helper, p);
		helper.succeed();
	}
}
