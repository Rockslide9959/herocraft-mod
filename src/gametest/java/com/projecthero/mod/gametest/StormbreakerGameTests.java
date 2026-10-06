package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.ThorPowers;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;
import com.projecthero.mod.stormbreaker.Bifrost;
import com.projecthero.mod.stormbreaker.BifrostWaypoints;
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
 * the returning throw and the Bifrost (v0.14.20: menu, waypoints, squad carry, 60 s cooldown). The throw and Bifrost tests each run in their own batch so a flying axe can
 * never meet another suite's mobs or players.
 */
public class StormbreakerGameTests implements FabricGameTest {
	private static final String THROW_BATCH = "stormbreaker_throw_v01419";
	private static final String BIFROST_BATCH = "stormbreaker_bifrost_v01420";
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
	public void anOrphanedAxeLiesDownAsItselfAndStaysPut(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(1.5, 2 + ALT, 1.5), true);
		helper.assertTrue(StormbreakerEntity.throwFrom(p), "thrown");
		StormbreakerEntity axe = axes(helper, p).get(0);
		// the thrower logs out mid-flight (Projectile.setOwner(null) is a no-op in vanilla, so that cannot orphan it)
		leave(helper, p);
		axe.beginReturn();
		helper.succeedWhen(() -> {
			// v0.15.3: with nobody to return to it settles where it is as the SAME entity (it used to become an item)
			helper.assertTrue(!axe.isRemoved() && axe.isResting() && !axe.isReturning(), "the axe lies down as itself");
			List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
					axe.getBoundingBox().inflate(3.0, 8.0, 3.0), e -> !e.isRemoved() && e.getItem().is(ModItems.STORMBREAKER));
			helper.assertTrue(dropped.isEmpty(), "and no Stormbreaker item appears, got " + dropped.size());
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(StormbreakerEntity.class, axe.getBoundingBox().inflate(3.0, 8.0, 3.0),
					e -> !e.isRemoved()).size() == 1, "exactly one axe");
			axe.discard();
		});
	}

	// ---- the Bifrost (v0.14.20: menu, waypoints, squad carry, its own 60 s cooldown) ----------------------------------

	/** The Bifrost tests travel onto a platform this high above their structure, inside their own column. */
	private static final int BIFROST_ALT = 50;

	private static void platform(GameTestHelper helper, int y, net.minecraft.world.level.block.Block block) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.setBlock(new BlockPos(x, y, z), block);
			}
		}
	}

	private static Bifrost.Result travel(GameTestHelper helper, ServerPlayer p, int rx, int ry, int rz) {
		BlockPos abs = helper.absolutePos(new BlockPos(rx, ry, rz));
		return Bifrost.travelTo(p, abs.getX(), abs.getY(), abs.getZ());
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BIFROST_BATCH, timeoutTicks = 40)
	public void sneakRightClickOpensTheBifrostMenuWithoutMovingOrThrowing(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(1.5, 2, 1.5), true);
		Vec3 start = p.position();
		p.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(1.5, 2.0, 5.5)));
		p.setShiftKeyDown(true);
		InteractionResult result = p.getMainHandItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND).getResult();
		helper.assertTrue(result.consumesAction(), "Sneak + right-click opens the Bifrost menu, got " + result);
		helper.assertTrue(p.position().distanceToSqr(start) < 1.0E-6, "opening the menu moves nobody");
		helper.assertTrue(p.getMainHandItem().is(ModItems.STORMBREAKER) && axes(helper, p).isEmpty(), "and throws nothing");
		helper.assertTrue(Bifrost.cooldownRemaining(p) == 0, "and spends no cooldown");
		helper.assertFalse(p.getCooldowns().isOnCooldown(ModItems.STORMBREAKER), "nor any item cooldown");
		p.setShiftKeyDown(false);
		leave(helper, p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BIFROST_BATCH, timeoutTicks = 40)
	public void sneakClickingABlockInReachWithAnOffhandItemIsLeftToVanilla(GameTestHelper helper) {
		BlockPos chest = new BlockPos(1, 2, 3);
		helper.setBlock(chest, Blocks.CHEST);
		ServerPlayer p = player(helper, new Vec3(1.5, 2, 1.5), true);
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH));
		Vec3 start = p.position();
		p.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(1.5, 2.5, 3.5)));
		helper.assertTrue(Bifrost.targetsBlockInReach(p), "the chest is within reach");
		p.setShiftKeyDown(true);
		InteractionResult result = p.getMainHandItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND).getResult();
		helper.assertTrue(result == InteractionResult.PASS, "the click is left to vanilla (off-hand placing), got " + result);
		helper.assertTrue(p.position().distanceToSqr(start) < 1.0E-6, "nobody moved");
		p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		result = p.getMainHandItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND).getResult();
		helper.assertTrue(result.consumesAction(), "with an empty off hand the menu opens even at a block, got " + result);
		p.setShiftKeyDown(false);
		leave(helper, p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BIFROST_BATCH, timeoutTicks = 40)
	public void threeWaypointsSaveAndSurviveASave(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(1.5, 2, 1.5), true);
		helper.assertTrue(ModAttachments.BIFROST_WAYPOINTS.isPersistent() && ModAttachments.BIFROST_WAYPOINTS.copyOnDeath(),
				"waypoints are saved with the player and kept through death");
		helper.assertTrue(ModAttachments.BIFROST_READY_AT.isPersistent() && ModAttachments.BIFROST_READY_AT.copyOnDeath(),
				"and so is the cooldown");
		helper.assertTrue(Bifrost.waypoints(p).equals(BifrostWaypoints.EMPTY), "a new player has three empty slots");

		String dim = helper.getLevel().dimension().location().toString();
		BlockPos[] spots = {new BlockPos(1, 2, 1), new BlockPos(3, 2, 5), new BlockPos(6, 4, 2)};
		String[] typed = {"Home", "   ", "A really very long waypoint name indeed"};
		for (int i = 0; i < 3; i++) {
			Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(spots[i]));
			p.moveTo(at.x, at.y, at.z);
			helper.assertTrue(Bifrost.saveWaypoint(p, i, typed[i]), "slot " + i + " saved");
		}
		helper.assertFalse(Bifrost.saveWaypoint(p, 3, "Nope"), "there is no fourth slot");

		BifrostWaypoints saved = Bifrost.waypoints(p);
		for (int i = 0; i < 3; i++) {
			BifrostWaypoints.Waypoint w = saved.get(i);
			BlockPos abs = helper.absolutePos(spots[i]);
			helper.assertTrue(w.x() == abs.getX() && w.y() == abs.getY() && w.z() == abs.getZ() && w.dimension().equals(dim),
					"slot " + i + " holds where the player stood, got " + w);
		}
		helper.assertTrue(saved.get(0).name().equals("Home"), "names are kept");
		helper.assertTrue(saved.get(1).name().equals("Waypoint 2"), "a blank name becomes 'Waypoint 2', got " + saved.get(1).name());
		helper.assertTrue(saved.get(2).name().length() <= BifrostWaypoints.MAX_NAME_LENGTH, "long names are cut short");

		// the attachment's own codec: what a relog writes and reads back
		var tag = BifrostWaypoints.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, saved).getOrThrow();
		BifrostWaypoints back = BifrostWaypoints.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertTrue(back.equals(saved), "all three waypoints survive a save and load");

		Bifrost.clearWaypoint(p, 1);
		helper.assertTrue(Bifrost.waypoints(p).get(1).isEmpty() && !Bifrost.waypoints(p).get(0).isEmpty()
				&& !Bifrost.waypoints(p).get(2).isEmpty(), "clearing one slot leaves the others");
		leave(helper, p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BIFROST_BATCH, timeoutTicks = 40)
	public void theBifrostCarriesNearbySquadmatesButNoOneElseThenRechargesForAMinute(GameTestHelper helper) {
		platform(helper, BIFROST_ALT, Blocks.STONE);
		ServerPlayer thor = player(helper, new Vec3(1.5, 2, 1.5), true);
		ServerPlayer mate = player(helper, new Vec3(3.5, 2, 1.5), false);
		ServerPlayer farMate = player(helper, new Vec3(1.5, 12, 1.5), false); // 10 blocks away: out of reach
		ServerPlayer stranger = player(helper, new Vec3(1.5, 2, 3.5), false); // close by, but not in the squad
		Vec3 farStart = farMate.position();
		Vec3 strangerStart = stranger.position();
		SquadManager squads = SquadManager.get(helper.getLevel().getServer());
		Squad squad = squads.create("bf" + thor.getUUID().toString().substring(0, 8), thor.getUUID());
		squads.addMember(squad, mate.getUUID());
		squads.addMember(squad, farMate.getUUID());
		try {
			helper.assertTrue(Bifrost.alliesInReach(thor).size() == 1 && Bifrost.alliesInReach(thor).get(0) == mate,
					"only the squadmate beside Thor is in reach, got " + Bifrost.alliesInReach(thor).size());

			Bifrost.Result result = travel(helper, thor, 2, BIFROST_ALT + 1, 2);
			helper.assertTrue(result == Bifrost.Result.OK, "the Bifrost opens, got " + result);
			helper.assertTrue(thor.blockPosition().equals(helper.absolutePos(new BlockPos(2, BIFROST_ALT + 1, 2))),
					"Thor lands on the platform at the coordinates, got " + thor.blockPosition());
			helper.assertTrue(mate.blockPosition().equals(helper.absolutePos(new BlockPos(4, BIFROST_ALT + 1, 2))),
					"the squadmate keeps their 2-block offset, got " + mate.blockPosition());
			helper.assertTrue(thor.fallDistance == 0.0f && mate.fallDistance == 0.0f, "no fall distance carried over");
			helper.assertTrue(farMate.position().distanceToSqr(farStart) < 1.0E-6, "a squadmate 10 blocks away stays put");
			helper.assertTrue(stranger.position().distanceToSqr(strangerStart) < 1.0E-6, "a player outside the squad stays put");

			helper.assertTrue(Bifrost.cooldownRemaining(thor) == Bifrost.COOLDOWN_TICKS && Bifrost.COOLDOWN_TICKS == 60 * 20,
					"a 60 s cooldown, got " + Bifrost.cooldownRemaining(thor));
			helper.assertFalse(thor.getCooldowns().isOnCooldown(ModItems.STORMBREAKER), "which does not hold the throw");

			BlockPos before = thor.blockPosition();
			Bifrost.Result again = travel(helper, thor, 5, BIFROST_ALT + 1, 5);
			helper.assertTrue(again == Bifrost.Result.COOLDOWN, "a second trip inside the minute is refused, got " + again);
			helper.assertTrue(thor.blockPosition().equals(before), "and goes nowhere");

			Bifrost.resetCooldownForTests(thor);
			helper.assertTrue(travel(helper, thor, 5, BIFROST_ALT + 1, 5) == Bifrost.Result.OK, "once recharged it works again");
		} finally {
			squads.disband(squad);
			platform(helper, BIFROST_ALT, Blocks.AIR);
			leave(helper, thor, mate, farMate, stranger);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BIFROST_BATCH, timeoutTicks = 40)
	public void theBifrostRefusesBadRequestsWithoutSpendingTheCooldown(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(1.5, 2, 1.5), true);
		Vec3 start = p.position();
		var level = helper.getLevel();
		BlockPos here = p.blockPosition();

		helper.assertTrue(Bifrost.travelTo(p, here.getX(), level.getMaxBuildHeight() + 5, here.getZ()) == Bifrost.Result.OUT_OF_BOUNDS,
				"above the build height is refused");
		helper.assertTrue(Bifrost.travelTo(p, here.getX(), level.getMinBuildHeight() - 5, here.getZ()) == Bifrost.Result.OUT_OF_BOUNDS,
				"below the world is refused");
		helper.assertTrue(Bifrost.travelTo(p, 40_000_000, 64, here.getZ()) == Bifrost.Result.OUT_OF_BOUNDS,
				"outside the world border is refused");
		helper.assertTrue(Bifrost.travelToWaypoint(p, 0) == Bifrost.Result.EMPTY_WAYPOINT, "an empty waypoint is refused");
		p.setAttached(ModAttachments.BIFROST_WAYPOINTS, BifrostWaypoints.EMPTY.with(1,
				new BifrostWaypoints.Waypoint("Hell", here.getX(), here.getY(), here.getZ(), "minecraft:the_nether")));
		helper.assertTrue(Bifrost.travelToWaypoint(p, 1) == Bifrost.Result.WRONG_DIMENSION,
				"a waypoint in another dimension is refused");

		ServerPlayer unworthy = player(helper, new Vec3(3.5, 2, 1.5), false);
		helper.assertTrue(Bifrost.travelTo(unworthy, here.getX(), here.getY(), here.getZ()) == Bifrost.Result.NOT_HOLDING,
				"the unworthy cannot open it");
		ServerPlayer emptyHanded = player(helper, new Vec3(5.5, 2, 1.5), true);
		emptyHanded.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.assertTrue(Bifrost.travelTo(emptyHanded, here.getX(), here.getY(), here.getZ()) == Bifrost.Result.NOT_HOLDING,
				"nor anyone without Stormbreaker in hand");

		helper.assertTrue(p.position().distanceToSqr(start) < 1.0E-6, "nobody moved");
		helper.assertTrue(Bifrost.cooldownRemaining(p) == 0, "and no cooldown was spent");
		leave(helper, p, unworthy, emptyHanded);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BIFROST_BATCH, timeoutTicks = 40)
	public void theBifrostAlwaysLandsSafely(GameTestHelper helper) {
		var level = helper.getLevel();
		platform(helper, BIFROST_ALT, Blocks.STONE);
		for (int y = 1; y <= 3; y++) {
			helper.setBlock(new BlockPos(5, BIFROST_ALT + y, 5), Blocks.STONE); // a 3-high pillar on the platform
		}
		helper.setBlock(new BlockPos(6, BIFROST_ALT, 6), Blocks.MAGMA_BLOCK);
		ServerPlayer p = player(helper, new Vec3(1.5, 2, 1.5), true);
		try {
			BlockPos inStone = helper.absolutePos(new BlockPos(5, BIFROST_ALT + 2, 5));
			BlockPos climbed = Bifrost.findSafeLanding(level, inStone.getX(), inStone.getY(), inStone.getZ());
			helper.assertTrue(climbed != null && climbed.equals(helper.absolutePos(new BlockPos(5, BIFROST_ALT + 4, 5))),
					"coordinates inside stone climb to the top of the pillar, got " + climbed);

			BlockPos inAir = helper.absolutePos(new BlockPos(2, BIFROST_ALT + 9, 2));
			BlockPos dropped = Bifrost.findSafeLanding(level, inAir.getX(), inAir.getY(), inAir.getZ());
			helper.assertTrue(dropped != null && dropped.equals(helper.absolutePos(new BlockPos(2, BIFROST_ALT + 1, 2))),
					"coordinates in the air drop to the platform, got " + dropped);
			for (BlockPos spot : new BlockPos[] {climbed, dropped}) {
				helper.assertTrue(level.getBlockState(spot).isAir() && level.getBlockState(spot.above()).isAir()
						&& level.getBlockState(spot.below()).isSolid(), "two free blocks over a solid floor at " + spot);
			}

			BlockPos overMagma = helper.absolutePos(new BlockPos(6, BIFROST_ALT + 5, 6));
			helper.assertTrue(Bifrost.findSafeLanding(level, overMagma.getX(), overMagma.getY(), overMagma.getZ()) == null,
					"a column whose only floor is magma has nowhere safe");
			helper.assertTrue(travel(helper, p, 6, BIFROST_ALT + 5, 6) == Bifrost.Result.NO_LANDING,
					"so the Bifrost refuses it");
			helper.assertTrue(Bifrost.cooldownRemaining(p) == 0, "without spending the cooldown");

			helper.assertTrue(travel(helper, p, 5, BIFROST_ALT + 2, 5) == Bifrost.Result.OK, "aimed into the pillar");
			helper.assertTrue(p.blockPosition().equals(helper.absolutePos(new BlockPos(5, BIFROST_ALT + 4, 5))),
					"lands on top of it, not inside it, got " + p.blockPosition());
			helper.assertTrue(p.fallDistance == 0.0f, "no fall distance carried over");
		} finally {
			platform(helper, BIFROST_ALT, Blocks.AIR);
			for (int y = 1; y <= 3; y++) {
				helper.setBlock(new BlockPos(5, BIFROST_ALT + y, 5), Blocks.AIR);
			}
			leave(helper, p);
		}
		helper.succeed();
	}
}
