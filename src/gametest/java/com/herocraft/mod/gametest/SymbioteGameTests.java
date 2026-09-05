package com.herocraft.mod.gametest;

import com.herocraft.mod.hero.ExperimentalPowers;
import com.herocraft.mod.hero.Power;
import com.herocraft.mod.hero.Powers;
import com.herocraft.mod.spider.SpiderMan;
import com.herocraft.mod.spider.SpiderWebReserve;
import com.herocraft.mod.spider.item.SpiderItems;
import com.herocraft.mod.spider.item.SymbioteArmorItem;
import com.herocraft.mod.symbiote.Symbiote;
import com.herocraft.mod.symbiote.SymbioteSuit;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * Coverage for the Spider-Man Symbiote upgrade (v0.9.10): the bond gate, the toggle, that repeated
 * toggling never stacks anything, the doubled-but-not-refilled web capacity, the "cannot keep the
 * suit" gate, and infinite durability. Rendering, the H keybind routing and the particle crawl are
 * client-side and covered by the manual test plan.
 */
public class SymbioteGameTests implements FabricGameTest {

	private static ServerPlayer spiderMan(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Power adhesion = Powers.byKey(SpiderMan.SPIDER_ADHESION_KEY);
		ExperimentalPowers.grant(player, adhesion);
		SpiderMan.evolveFromAdhesion(player);
		return player;
	}

	/** Drop the ~0.75 s anti-spam gate so a test can toggle back and forth in one tick. */
	private static void clearCooldown(ServerPlayer player) {
		Symbiote.state(player).toggleReadyAt = 0L;
	}

	/**
	 * Toggling now starts a suit-up/suit-down <em>animation</em> (real game time has to pass before it
	 * settles -- see {@code Symbiote}/{@code SymbioteTransform}), which a gametest cannot simply wait
	 * out. Rewind the clock's own start tick far into the past and tick once, exactly the way
	 * {@code clearCooldown} rewinds the toggle gate -- both mutate the live attached state object
	 * directly rather than faking the passage of real time.
	 */
	private static void forceSettle(ServerPlayer player) {
		Symbiote.state(player).transformStartTick -= 10_000L;
		Symbiote.tick(player);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void unbondedPlayerCannotToggle(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Symbiote.toggle(player);
		helper.assertFalse(Symbiote.isActive(player), "an unbonded player's H press must do nothing");
		helper.assertFalse(SymbioteSuit.wearing(player), "and must not equip a suit");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ordinaryPlayerCanBondAndToggle(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		helper.assertTrue(Symbiote.grant(player), "v0.9.14: any player, not just Spider-Man, can bond");
		helper.assertTrue(Symbiote.canToggle(player), "and toggle once bonded");

		Symbiote.toggle(player);
		forceSettle(player);
		helper.assertTrue(Symbiote.isActive(player), "the suit engages");
		helper.assertTrue(
				player.getItemBySlot(EquipmentSlot.CHEST).getItem()
						instanceof com.herocraft.mod.symbiote.item.SymbioteHostArmorItem,
				"a non-Spider-Man host gets the plain Normal-host armour, not the Black Suit");
		helper.assertFalse(
				player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof SymbioteArmorItem,
				"and specifically NOT the Spider-Man Symbiote suit item");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spiderManWithoutBondCannotToggle(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		helper.assertFalse(Symbiote.canToggle(player), "no bond -> H does nothing");
		Symbiote.toggle(player);
		helper.assertFalse(Symbiote.isActive(player), "pressing H without a bond must not transform");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void toggleEngagesAndRetracts(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		helper.assertTrue(Symbiote.grant(player), "bond should take");
		helper.assertTrue(Symbiote.canToggle(player), "Spider-Man + bond -> H is live");

		Symbiote.toggle(player);
		helper.assertTrue(Symbiote.isActive(player), "H should engage the Symbiote (suit-up animation starts)");
		helper.assertTrue(SymbioteSuit.wearingFullSet(player),
				"all four pieces are worn from the start of the animation -- the client hides them progressively");
		forceSettle(player);
		helper.assertTrue(Symbiote.isActive(player), "still active once the suit-up animation settles");

		clearCooldown(player);
		Symbiote.toggle(player);
		helper.assertTrue(Symbiote.isActive(player), "still 'active' (on or coming off) mid retraction");
		helper.assertTrue(SymbioteSuit.wearing(player), "the suit stays worn through the retract animation");
		forceSettle(player);
		helper.assertFalse(Symbiote.isActive(player), "H again, once settled, retracts it");
		helper.assertFalse(SymbioteSuit.wearing(player), "and removes every piece");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spammingHNeverStacksAnything(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		Symbiote.grant(player);
		for (int i = 0; i < 8; i++) {
			clearCooldown(player);
			Symbiote.toggle(player);
			forceSettle(player);
		}
		// ended after an even number of toggles -> off, but still bonded
		helper.assertFalse(Symbiote.isActive(player), "8 toggles ends inactive");
		helper.assertFalse(SymbioteSuit.wearing(player), "no stuck suit after spamming");
		helper.assertTrue(SpiderWebReserve.maxFor(player) == SpiderWebReserve.MAX * 2,
				"web capacity stays doubled after spamming -- it is a bond perk, not a suit-on perk");

		clearCooldown(player);
		Symbiote.toggle(player); // now on
		int pieces = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {
				EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			if (player.getItemBySlot(slot).getItem() instanceof SymbioteArmorItem) {
				pieces++;
			}
		}
		helper.assertTrue(pieces == 4, "exactly four pieces, never duplicated -- got " + pieces);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void webCapacityDoublesOnBondWithoutRefilling(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		SpiderMan.setWebReserve(player, 50.0f);

		Symbiote.grant(player);
		helper.assertTrue(SpiderWebReserve.maxFor(player) == SpiderWebReserve.MAX * 2,
				"bonding alone roughly doubles the webbing ceiling, even with the suit off");
		helper.assertTrue(Math.abs(SpiderMan.state(player).webReserve - 50.0f) < 0.01f,
				"bonding must NOT refill the meter -- 50/100 becomes 50/200");

		Symbiote.toggle(player);
		forceSettle(player);
		helper.assertTrue(SpiderWebReserve.maxFor(player) == SpiderWebReserve.MAX * 2,
				"suiting up changes nothing about the ceiling -- it was already doubled");
		clearCooldown(player);
		Symbiote.toggle(player);
		forceSettle(player);
		helper.assertFalse(Symbiote.isActive(player), "retracted");
		helper.assertTrue(SpiderWebReserve.maxFor(player) == SpiderWebReserve.MAX * 2,
				"retracting the suit must NOT lower the ceiling -- only unbonding does that");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void unbondingClampsExcessWebbing(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		Symbiote.grant(player);
		SpiderMan.setWebReserve(player, SpiderWebReserve.MAX * 2); // 400, only possible while bonded

		Symbiote.remove(player);
		helper.assertTrue(SpiderMan.state(player).webReserve <= SpiderWebReserve.MAX + 0.01f,
				"a now-over-cap reserve is clamped back to the normal max on unbonding, got "
						+ SpiderMan.state(player).webReserve);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void deactivatingRestoresTheStowedArmor(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		Symbiote.grant(player);
		ItemStack diamondHelmet = new ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET);
		player.setItemSlot(EquipmentSlot.HEAD, diamondHelmet.copy());
		player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY); // nothing worn there

		Symbiote.toggle(player);
		helper.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof SymbioteArmorItem,
				"the Symbiote helmet replaces the real one the instant the suit engages");
		forceSettle(player); // finish suiting up before the suit-down press is even accepted

		clearCooldown(player);
		Symbiote.toggle(player);
		forceSettle(player);
		helper.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.DIAMOND_HELMET),
				"the real helmet worn underneath comes back once the Symbiote retracts");
		helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
				"a slot that had nothing worn stays empty, not filled with a leftover piece");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitPiecesAreCurseOfBindingLocked(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		Symbiote.grant(player);
		Symbiote.toggle(player);

		ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
		helper.assertTrue(worn.getItem() instanceof SymbioteArmorItem, "the chestplate slot holds the suit piece");
		helper.assertTrue(net.minecraft.world.item.enchantment.EnchantmentHelper.has(worn,
				net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE),
				"the piece must carry Curse of Binding's effect -- this is what stops a survival player "
						+ "pulling it out of the slot and duplicating it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void losingSpiderManPowerDeactivatesButKeepsTheBond(GameTestHelper helper) {
		ServerPlayer player = spiderMan(helper);
		Symbiote.grant(player);
		Symbiote.toggle(player);
		helper.assertTrue(Symbiote.isActive(player), "engaged");

		SpiderMan.revoke(player);
		helper.assertFalse(Symbiote.isActive(player), "losing the host retracts the Symbiote");
		helper.assertFalse(SymbioteSuit.wearing(player), "no leftover suit");
		helper.assertTrue(Symbiote.hasSymbiote(player), "the bond itself is kept for when the power returns");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void enforceStripsSuitFromANonOwner(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(SpiderItems.SYMBIOTE_CHESTPLATE));
		player.getInventory().add(new ItemStack(SpiderItems.SYMBIOTE_HELMET));

		Symbiote.enforce(player);

		helper.assertFalse(SymbioteSuit.wearing(player), "a non-owner cannot wear the Symbiote suit");
		boolean anyLoose = false;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).getItem() instanceof SymbioteArmorItem) {
				anyLoose = true;
			}
		}
		helper.assertFalse(anyLoose, "and cannot keep a copy in their inventory");
		helper.succeed();
	}

	// ---------------- natural acquisition ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void worldgenAndEntityTypesAreRegistered(GameTestHelper helper) {
		helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(
				com.herocraft.mod.HeroCraftMod.id("symbiote")), "the Symbiote entity type must be registered");
		helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.STRUCTURE_TYPE.containsKey(
				com.herocraft.mod.HeroCraftMod.id("symbiote_meteor")), "symbiote_meteor structure type");
		helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.STRUCTURE_TYPE.containsKey(
				com.herocraft.mod.HeroCraftMod.id("symbiote_lab")), "symbiote_lab structure type");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void symbioteHostBuffsAndKeepsTheMob(GameTestHelper helper) {
		net.minecraft.world.entity.monster.Zombie zombie = helper.spawn(
				net.minecraft.world.entity.EntityType.ZOMBIE, net.minecraft.core.BlockPos.ZERO);
		double baseHp = zombie.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);

		com.herocraft.mod.symbiote.SymbioteHost.mark(zombie);

		helper.assertTrue(com.herocraft.mod.symbiote.SymbioteHost.is(zombie), "the mob should be tagged");
		helper.assertTrue(zombie.getAttributeBaseValue(
				net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH) > baseHp + 0.5,
				"a Symbiote Host has more health than a plain zombie");
		helper.assertTrue(zombie.isPersistenceRequired(), "a Symbiote Host never de-spawns");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void freeSymbioteBondsAnyPlayer(GameTestHelper helper) {
		com.herocraft.mod.symbiote.entity.SymbioteEntity blob =
				com.herocraft.mod.symbiote.entity.SymbioteEntityTypes.SYMBIOTE.create(helper.getLevel());
		helper.assertFalse(blob == null, "the Symbiote entity should construct");
		blob.moveTo(helper.absoluteVec(net.minecraft.world.phys.Vec3.ZERO));
		helper.getLevel().addFreshEntity(blob);

		ServerPlayer ordinary = helper.makeMockServerPlayerInLevel();
		ordinary.setGameMode(GameType.SURVIVAL);
		com.herocraft.mod.symbiote.SymbioteBonding.attempt(ordinary, blob);
		helper.assertTrue(com.herocraft.mod.symbiote.Symbiote.hasSymbiote(ordinary),
				"v0.9.14: an ordinary player bonds too, not only a Spider-Man");
		helper.assertTrue(blob.isRemoved(), "and the entity is consumed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bondingKeepsSpiderManAndUsesTheBlackSuit(GameTestHelper helper) {
		com.herocraft.mod.symbiote.entity.SymbioteEntity blob =
				com.herocraft.mod.symbiote.entity.SymbioteEntityTypes.SYMBIOTE.create(helper.getLevel());
		blob.moveTo(helper.absoluteVec(net.minecraft.world.phys.Vec3.ZERO));
		helper.getLevel().addFreshEntity(blob);

		ServerPlayer spider = spiderMan(helper);
		com.herocraft.mod.symbiote.SymbioteBonding.attempt(spider, blob);
		helper.assertTrue(com.herocraft.mod.symbiote.Symbiote.hasSymbiote(spider), "Spider-Man bonds too");
		helper.assertTrue(SpiderMan.hasPower(spider), "and Spider-Man is NOT purged -- it's the compatible power");
		helper.assertTrue(com.herocraft.mod.symbiote.SymbioteHostType.of(spider)
				== com.herocraft.mod.symbiote.SymbioteHostType.SPIDER_MAN, "host type resolves to Black Suit");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bondingPurgesAnIncompatibleHeroTierPower(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		com.herocraft.mod.ironman.TonyStark.grant(player);
		helper.assertTrue(com.herocraft.mod.ironman.TonyStark.hasPower(player), "sanity: Tony Stark granted");

		com.herocraft.mod.symbiote.entity.SymbioteEntity blob =
				com.herocraft.mod.symbiote.entity.SymbioteEntityTypes.SYMBIOTE.create(helper.getLevel());
		blob.moveTo(helper.absoluteVec(net.minecraft.world.phys.Vec3.ZERO));
		helper.getLevel().addFreshEntity(blob);
		com.herocraft.mod.symbiote.SymbioteBonding.attempt(player, blob);

		helper.assertTrue(Symbiote.hasSymbiote(player), "the bond still succeeds");
		helper.assertFalse(com.herocraft.mod.ironman.TonyStark.hasPower(player),
				"Iron Man is purged -- it is not the whitelisted power");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bondingPurgesAnExperimentalPower(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Power strength = Powers.byKey("power_01_super_strength");
		ExperimentalPowers.grant(player, strength);
		helper.assertTrue(com.herocraft.mod.hero.HeroTiers.hasExperimental(player), "sanity: power granted");

		com.herocraft.mod.symbiote.entity.SymbioteEntity blob =
				com.herocraft.mod.symbiote.entity.SymbioteEntityTypes.SYMBIOTE.create(helper.getLevel());
		blob.moveTo(helper.absoluteVec(net.minecraft.world.phys.Vec3.ZERO));
		helper.getLevel().addFreshEntity(blob);
		com.herocraft.mod.symbiote.SymbioteBonding.attempt(player, blob);

		helper.assertTrue(Symbiote.hasSymbiote(player), "the bond still succeeds");
		helper.assertFalse(com.herocraft.mod.hero.HeroTiers.hasExperimental(player),
				"every experimental power (Super Strength included) is purged on bond");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void normalHostArmourIsBetweenIronAndDiamond(GameTestHelper helper) {
		double total = 0;
		for (var mat : new net.minecraft.world.item.ArmorItem.Type[] {
				net.minecraft.world.item.ArmorItem.Type.HELMET, net.minecraft.world.item.ArmorItem.Type.CHESTPLATE,
				net.minecraft.world.item.ArmorItem.Type.LEGGINGS, net.minecraft.world.item.ArmorItem.Type.BOOTS }) {
			total += com.herocraft.mod.item.ModArmorMaterials.SYMBIOTE_HOST.value().defense().get(mat);
		}
		helper.assertTrue(total == 17.0, "total armour should be 17 (iron 15 < 17 < diamond 20), got " + total);
		helper.assertTrue(com.herocraft.mod.item.ModArmorMaterials.SYMBIOTE_HOST.value().toughness() == 1.0f,
				"toughness should be 1.0 per piece");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void normalHostAbilitiesRespectCooldown(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Symbiote.grant(player);
		Symbiote.toggle(player);
		forceSettle(player);
		helper.assertTrue(com.herocraft.mod.symbiote.SymbioteAbilityManager.hasContext(player),
				"an active Normal host owns the six Symbiote ability slots");

		long readyAt = Symbiote.state(player).abilityCooldowns.get(3); // SLOT_4 = Symbiote Slam
		helper.assertTrue(readyAt == 0L, "no cooldown before first use");
		com.herocraft.mod.symbiote.SymbioteAbilityManager.handle(
				player, com.herocraft.mod.hero.AbilitySlot.SLOT_4, true);
		long after = Symbiote.state(player).abilityCooldowns.get(3);
		helper.assertTrue(after > player.level().getGameTime(), "using Symbiote Slam starts its cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sonicDisruptionBlocksAbilities(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Symbiote.grant(player);
		Symbiote.toggle(player);
		forceSettle(player);

		long now = player.level().getGameTime();
		com.herocraft.mod.combat.SonicVulnerability.expose(player, now, 100);
		helper.assertTrue(com.herocraft.mod.combat.SonicVulnerability.isDisrupted(player, now),
				"sanity: the player is marked disrupted");

		com.herocraft.mod.symbiote.SymbioteAbilityManager.handle(
				player, com.herocraft.mod.hero.AbilitySlot.SLOT_4, true);
		helper.assertTrue(Symbiote.state(player).abilityCooldowns.get(3) == 0L,
				"a sonically disrupted host's ability press must do nothing (no cooldown started)");
		helper.succeed();
	}

	// Note: fire/lava weakness (SymbioteDamageRules) and Symbiote Shield's damage reduction are NOT
	// gametest-covered -- ALLOW_DAMAGE never fires for a GameTestHelper mock ServerPlayer regardless
	// of invocation route (confirmed while building the mod's HeroDamageRules-family gametests; see
	// project memory). Needs the user's live playtest, same as every other player-damage rule in
	// this mod's power systems.

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitPiecesAreUnbreakable(GameTestHelper helper) {
		for (var item : new net.minecraft.world.item.Item[] {
				SpiderItems.SYMBIOTE_HELMET, SpiderItems.SYMBIOTE_CHESTPLATE,
				SpiderItems.SYMBIOTE_LEGGINGS, SpiderItems.SYMBIOTE_BOOTS }) {
			ItemStack stack = new ItemStack(item);
			helper.assertFalse(stack.isDamageableItem(),
					item + " must have no durability -- the Symbiote suit never breaks");
		}
		helper.succeed();
	}
}
