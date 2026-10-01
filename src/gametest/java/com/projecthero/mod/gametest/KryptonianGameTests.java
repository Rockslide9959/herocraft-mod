package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.kryptonian.Kryptonian;
import com.projecthero.mod.kryptonian.KryptonianAbilities;
import com.projecthero.mod.kryptonian.KryptonianAbilityManager;
import com.projecthero.mod.kryptonian.KryptonianConfig;
import com.projecthero.mod.kryptonian.KryptonianFlight;
import com.projecthero.mod.kryptonian.SupermanSuit;
import com.projecthero.mod.kryptonian.item.KryptonianCrystalItem;
import com.projecthero.mod.kryptonian.item.KryptonianItems;
import com.projecthero.mod.kryptonian.item.SupermanSuitItem;
import com.projecthero.mod.kryptonian.meteor.MeteorManager;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.8 Kryptonian coverage: the crystal grant and the passives, revoke, the damage rules, kryptonite, flight, every
 * move (activates, spends Solar Energy, starts its cooldown), the Solar Flare burn-out and the Kryptonite Meteor's impact.
 * Mock players are not reliably ticked by the server, so each test drives {@link Kryptonian#tick} itself (and advances
 * {@code tickCount}, which the once-every-N-ticks checks read).
 */
public class KryptonianGameTests implements FabricGameTest {
	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		clearSpawnInvulnerability(p);
		Kryptonian.grant(p);
		return p;
	}

	/** A fresh (never-ticked) mock player keeps vanilla's 3 s spawn invulnerability; clear it so it can be hurt. */
	private static void clearSpawnInvulnerability(ServerPlayer p) {
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Kryptonian.tick(p);
		});
	}

	/** A target straight ahead of the player (south, yaw 0), no AI. */
	private static <T extends Mob> T ahead(GameTestHelper helper, ServerPlayer p, EntityType<T> type, double distance) {
		T m = type.create(helper.getLevel());
		m.moveTo(p.getX(), p.getY(), p.getZ() + distance, 180.0f, 0.0f);
		m.setNoAi(true);
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void crystalGrantsThePowerAndPassives(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		ItemStack crystal = new ItemStack(KryptonianItems.KRYPTONIAN_CRYSTAL);
		helper.assertFalse(Kryptonian.hasPower(p), "no power yet");
		helper.assertTrue(KryptonianCrystalItem.infuse(p, crystal, false), "the crystal infuses the player");
		helper.assertTrue(crystal.isEmpty(), "the crystal is used up");
		helper.assertTrue(Kryptonian.hasPower(p) && HeroTiers.holdsHero(p, Kryptonian.KEY), "a registered Hero-Tier Primary power");
		helper.assertFalse(KryptonianCrystalItem.infuse(p, new ItemStack(KryptonianItems.KRYPTONIAN_CRYSTAL), false), "a second crystal does nothing");
		helper.assertTrue(Math.abs(p.getMaxHealth() - 40.0) < 1e-6, "40 max health, got " + p.getMaxHealth());
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - 15.0) < 1e-6,
				"fists hit for 15, got " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "cannot be knocked back");
		helper.assertTrue(Kryptonian.solar(p) == KryptonianConfig.SOLAR_MAX, "starts with a full Solar Energy bar");
		Kryptonian.reconcile(p);
		Kryptonian.reconcile(p);
		helper.assertTrue(Math.abs(p.getMaxHealth() - 40.0) < 1e-6, "reconciling never stacks");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void grantReplacesAnotherHeroAndRevokeClearsEverything(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		com.projecthero.mod.hulk.Hulk.grant(p);
		Kryptonian.grant(p);
		helper.assertFalse(com.projecthero.mod.hulk.Hulk.hasPower(p), "ONE Primary power: the Hulk is replaced");
		KryptonianFlight.start(p);
		HeroTiers.wipeAll(p);
		helper.assertFalse(Kryptonian.hasPower(p), "wipeAll revokes the Kryptonian");
		helper.assertFalse(Kryptonian.isFlying(p), "flight stops");
		helper.assertTrue(Math.abs(p.getMaxHealth() - 20.0) < 1e-6, "back to 20 health, got " + p.getMaxHealth());
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - 1.0) < 1e-6, "back to a plain 1 attack");
		helper.assertFalse(p.getAbilities().mayfly, "no flight left behind");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void damageIsCutAndFallsAndFireNeverHurt(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setHealth(p.getMaxHealth());
		float before = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().generic(), 20.0f);
		float lost = before - p.getHealth();
		helper.assertTrue(Math.abs(lost - 5.0f) < 0.01f, "20 damage is cut by 75% to 5, lost " + lost);
		before = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().fall(), 30.0f);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().inFire(), 10.0f);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().lava(), 10.0f);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().drown(), 10.0f);
		helper.assertTrue(p.getHealth() >= before, "falls, fire, lava and drowning do nothing, " + before + " -> " + p.getHealth());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void kryptoniteWeakensAndRecovers(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		BlockPos ore = helper.absolutePos(new BlockPos(4, 1, 4));
		helper.getLevel().setBlock(ore, KryptonianItems.KRYPTONITE_ORE.defaultBlockState(), 3);
		p.tickCount = 10; // the exposure check runs every 10th tick
		Kryptonian.tick(p);
		helper.assertTrue(Kryptonian.weakened(p), "kryptonite ore 2 blocks away weakens him");
		helper.assertTrue(Math.abs(p.getMaxHealth() - 20.0) < 1e-6, "no bonus health while weakened");
		p.invulnerableTime = 0;
		float before = p.getHealth();
		p.hurt(p.damageSources().generic(), 4.0f);
		helper.assertTrue(before - p.getHealth() > 3.9f, "no damage reduction near kryptonite");
		KryptonianFlight.toggle(p);
		helper.assertFalse(Kryptonian.isFlying(p), "cannot fly near kryptonite");
		KryptonianAbilities.punch(p);
		helper.assertTrue(Kryptonian.cooldownRemaining(p, KryptonianAbilities.PUNCH) == 0, "no moves near kryptonite");
		helper.getLevel().setBlock(ore, Blocks.AIR.defaultBlockState(), 3);
		helper.runAfterDelay(KryptonianConfig.KRYPTONITE_LINGER_TICKS + 10, () -> {
			p.tickCount = 1000;
			Kryptonian.tick(p);
			helper.assertFalse(Kryptonian.weakened(p), "the weakness passes once the kryptonite is gone");
			Kryptonian.reconcile(p);
			helper.assertTrue(Math.abs(p.getMaxHealth() - 40.0) < 1e-6, "strength back");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void carryingAShardWeakens(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.getInventory().add(new ItemStack(KryptonianItems.KRYPTONITE_SHARD));
		helper.assertTrue(com.projecthero.mod.kryptonian.Kryptonite.exposed(p), "a shard in his own inventory counts");
		p.getInventory().clearContent();
		helper.assertFalse(com.projecthero.mod.kryptonian.Kryptonite.exposed(p), "nothing nearby");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void flightTogglesOnAndOff(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		KryptonianFlight.toggle(p);
		helper.assertTrue(Kryptonian.isFlying(p), "double-tap: flying");
		helper.assertTrue(p.getAbilities().mayfly && p.getAbilities().flying, "vanilla flight flags on");
		KryptonianFlight.toggle(p);
		helper.assertFalse(Kryptonian.isFlying(p), "double-tap again: landed");
		helper.assertFalse(p.getAbilities().mayfly, "flight flags off");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void everyMoveActivatesAndStartsItsCooldown(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		pump(helper, p);
		LivingEntity golem = ahead(helper, p, EntityType.IRON_GOLEM, 2.5);
		String[] instant = { KryptonianAbilities.PUNCH, KryptonianAbilities.FREEZE_BREATH, KryptonianAbilities.THUNDERCLAP,
				KryptonianAbilities.GROUND_SLAM, KryptonianAbilities.SUPER_DASH, KryptonianAbilities.SKY_LAUNCH, KryptonianAbilities.XRAY };
		float golemBefore = golem.getHealth();
		KryptonianAbilities.punch(p);
		helper.assertTrue(golem.getHealth() < golemBefore, "the punch lands, " + golemBefore + " -> " + golem.getHealth());
		golem.invulnerableTime = 0;
		KryptonianAbilities.freezeBreath(p);
		KryptonianAbilities.thunderclap(p);
		KryptonianAbilities.groundSlam(p);
		KryptonianAbilities.superDash(p);
		KryptonianAbilities.xray(p);
		helper.assertTrue(Kryptonian.xrayActive(p), "x-ray on");
		KryptonianAbilities.skyLaunch(p);
		for (String id : instant) {
			helper.assertTrue(Kryptonian.cooldownRemaining(p, id) > 0, id + " starts its cooldown");
		}
		helper.assertTrue(Kryptonian.solar(p) < KryptonianConfig.SOLAR_MAX - 50f, "the moves spent Solar Energy, " + Kryptonian.solar(p));
		helper.runAfterDelay(40, () -> {
			helper.assertFalse(KryptonianAbilities.running(p, KryptonianAbilities.FREEZE_BREATH), "the breath ends");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void heatVisionHoldsUntilReleased(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		pump(helper, p);
		LivingEntity golem = ahead(helper, p, EntityType.IRON_GOLEM, 3.0);
		float before = golem.getHealth();
		KryptonianAbilities.startHeatVision(p);
		helper.assertTrue(Kryptonian.heatVisionActive(p), "the beam is on");
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(Kryptonian.heatVisionActive(p) && KryptonianAbilities.running(p, KryptonianAbilities.HEAT_VISION),
					"still burning while held");
			helper.assertTrue(golem.getHealth() < before, "it burns the target");
			helper.assertTrue(golem.isOnFire(), "and sets it alight");
			KryptonianAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1, false); // key up
			helper.assertFalse(Kryptonian.heatVisionActive(p), "letting go stops it");
			helper.assertTrue(Kryptonian.cooldownRemaining(p, KryptonianAbilities.HEAT_VISION) > 0, "then the cooldown");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void grabAndThrow(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		pump(helper, p);
		LivingEntity sheep = ahead(helper, p, EntityType.SHEEP, 2.0);
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, sheep.position().add(0, sheep.getBbHeight() * 0.5, 0));
		KryptonianAbilities.superGrab(p);
		helper.assertTrue(KryptonianAbilities.holding(p), "grabbed the sheep");
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(sheep.distanceTo(p) < 4.0, "held in front of him");
			KryptonianAbilities.superGrab(p);
			helper.assertFalse(KryptonianAbilities.holding(p), "thrown");
			helper.assertTrue(Kryptonian.cooldownRemaining(p, KryptonianAbilities.SUPER_GRAB) > 0, "cooldown from the throw");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void solarFlareBlastsAndBurnsHimOut(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		pump(helper, p);
		LivingEntity golem = ahead(helper, p, EntityType.IRON_GOLEM, 3.0);
		float before = golem.getHealth();
		Kryptonian.setSolar(p, 40f);
		KryptonianAbilities.solarFlare(p);
		helper.assertFalse(KryptonianAbilities.running(p, KryptonianAbilities.SOLAR_FLARE), "needs 50 Solar Energy");
		Kryptonian.setSolar(p, KryptonianConfig.SOLAR_MAX);
		KryptonianAbilities.solarFlare(p);
		helper.assertTrue(KryptonianAbilities.running(p, KryptonianAbilities.SOLAR_FLARE), "charging");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(Kryptonian.depowered(p), "burnt out after the blast"))
				.thenExecute(() -> {
					helper.assertTrue(golem.getHealth() < before - 50f || !golem.isAlive(), "the flare devastates the target");
					helper.assertTrue(Kryptonian.solar(p) == 0f, "all the Solar Energy is gone");
					helper.assertTrue(Kryptonian.cooldownRemaining(p, KryptonianAbilities.SOLAR_FLARE) > 0, "long cooldown");
					helper.assertTrue(Math.abs(p.getMaxHealth() - 20.0) < 1e-6, "no passives while burnt out");
					KryptonianFlight.toggle(p);
					helper.assertFalse(Kryptonian.isFlying(p), "no flight while burnt out");
				})
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void meteorImpactLeavesTheCoreAndOre(GameTestHelper helper) {
		// a floor of stone to strike
		for (int x = 1; x <= 6; x++) {
			for (int z = 1; z <= 6; z++) {
				for (int y = 1; y <= 4; y++) {
					helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.STONE.defaultBlockState(), 3);
				}
			}
		}
		BlockPos target = helper.absolutePos(new BlockPos(3, 4, 3));
		MeteorManager.Pending pending = MeteorManager.launch(helper.getLevel(), target, 10);
		helper.assertTrue(pending.entity() != null, "the meteor is in the sky");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(count(helper, target, KryptonianItems.METEOR_CORE) == 1, "exactly one Meteor Core"))
				.thenExecute(() -> {
					int ore = count(helper, target, KryptonianItems.KRYPTONITE_ORE);
					helper.assertTrue(ore >= 3, "kryptonite ore around the core, got " + ore);
					helper.assertTrue(helper.getLevel().getEntity(pending.entity()) == null, "the fireball is gone");
				})
				.thenSucceed();
	}

	// ---------------------------------------------------------------- v0.14.10: the crafted crystal

	@GameTest(template = EMPTY_STRUCTURE)
	public void kryptonianCrystalCanBeCraftedTheHardWay(GameTestHelper helper) {
		var holder = helper.getLevel().getRecipeManager().byKey(com.projecthero.mod.ProjectHeroMod.id("kryptonian_crystal"));
		helper.assertTrue(holder.isPresent(), "a recipe for the Kryptonian Crystal");
		var recipe = holder.get().value();
		helper.assertTrue(recipe.getResultItem(helper.getLevel().registryAccess()).is(KryptonianItems.KRYPTONIAN_CRYSTAL), "it makes the crystal");
		int apples = 0;
		boolean star = false;
		for (var ingredient : recipe.getIngredients()) {
			if (ingredient.test(new ItemStack(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE))) {
				apples++;
			}
			star |= ingredient.test(new ItemStack(net.minecraft.world.item.Items.NETHER_STAR));
		}
		helper.assertTrue(apples == 4 && star, "four enchanted golden apples and a nether star");
		helper.succeed();
	}

	// ---------------------------------------------------------------- v0.14.9: the Superman Suit

	@GameTest(template = EMPTY_STRUCTURE)
	public void supermanSuitRecipesExistWithoutKryptonite(GameTestHelper helper) {
		for (String piece : new String[] { "chestplate", "leggings", "boots" }) {
			var holder = helper.getLevel().getRecipeManager().byKey(com.projecthero.mod.ProjectHeroMod.id("superman_suit_" + piece));
			helper.assertTrue(holder.isPresent(), "a recipe for the " + piece);
			var recipe = holder.get().value();
			ItemStack out = recipe.getResultItem(helper.getLevel().registryAccess());
			net.minecraft.world.item.Item expected = net.minecraft.core.registries.BuiltInRegistries.ITEM
					.get(com.projecthero.mod.ProjectHeroMod.id("superman_suit_" + piece));
			helper.assertTrue(expected instanceof SupermanSuitItem && out.is(expected), "the " + piece + " recipe makes the " + piece);
			for (var ingredient : recipe.getIngredients()) {
				for (ItemStack option : ingredient.getItems()) {
					helper.assertFalse(option.is(KryptonianItems.KRYPTONITE_SHARD) || option.is(KryptonianItems.KRYPTONITE_BLOCK_ITEM)
							|| option.is(KryptonianItems.KRYPTONITE_ORE_ITEM), "no kryptonite in the " + piece + " recipe");
				}
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void onlyAKryptonianCanPutOnTheSupermanSuit(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		ItemStack chest = new ItemStack(SupermanSuit.CHESTPLATE);
		// InventoryMenu armour slots: 5 head, 6 chest, 7 legs, 8 feet
		helper.assertFalse(p.inventoryMenu.getSlot(6).mayPlace(chest), "a non-Kryptonian's chest slot refuses it");
		helper.assertFalse(p.inventoryMenu.getSlot(8).mayPlace(new ItemStack(SupermanSuit.BOOTS)), "and the boots slot");
		p.setItemInHand(InteractionHand.MAIN_HAND, chest);
		helper.assertFalse(chest.getItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND).getResult().consumesAction(),
				"right-click does not put it on");
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "nothing worn");
		helper.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).is(SupermanSuit.CHESTPLATE), "still in his hand");

		Kryptonian.grant(p);
		helper.assertTrue(p.inventoryMenu.getSlot(6).mayPlace(chest), "a Kryptonian's chest slot takes it");
		chest.getItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).is(SupermanSuit.CHESTPLATE), "a Kryptonian puts it on with right-click");
		helper.assertTrue(SupermanSuit.wearsCape(p), "and wears the cape");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void losingThePowerPopsTheSupermanSuitOff(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(SupermanSuit.CHESTPLATE));
		p.setItemSlot(EquipmentSlot.FEET, new ItemStack(SupermanSuit.BOOTS));
		Kryptonian.tick(p);
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).is(SupermanSuit.CHESTPLATE), "a Kryptonian keeps it on");

		Kryptonian.revoke(p);
		Kryptonian.tick(p);
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && p.getItemBySlot(EquipmentSlot.FEET).isEmpty(),
				"it pops off when the power goes");
		helper.assertTrue(p.getInventory().contains(new ItemStack(SupermanSuit.CHESTPLATE))
				&& p.getInventory().contains(new ItemStack(SupermanSuit.BOOTS)), "into his inventory");

		// a full inventory: it drops at his feet instead
		p.getInventory().clearContent();
		for (int i = 0; i < p.getInventory().items.size(); i++) {
			p.getInventory().items.set(i, new ItemStack(Blocks.STONE, 64));
		}
		p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(SupermanSuit.LEGGINGS));
		Kryptonian.tick(p);
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.LEGS).isEmpty(), "the leggings pop off too");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertFalse(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
						p.getBoundingBox().inflate(4.0), e -> e.getItem().is(SupermanSuit.LEGGINGS)).isEmpty(), "dropped at his feet"))
				.thenSucceed();
	}

	private static int count(GameTestHelper helper, BlockPos center, net.minecraft.world.level.block.Block block) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-5, -5, -5), center.offset(5, 5, 5))) {
			if (helper.getLevel().getBlockState(p).is(block)) {
				n++;
			}
		}
		return n;
	}
}
