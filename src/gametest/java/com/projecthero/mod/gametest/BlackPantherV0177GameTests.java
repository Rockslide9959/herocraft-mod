package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.blackpanther.BlackPanther;
import com.projecthero.mod.blackpanther.BlackPantherAbilities;
import com.projecthero.mod.blackpanther.BlackPantherAbilityManager;
import com.projecthero.mod.blackpanther.BlackPantherConfig;
import com.projecthero.mod.blackpanther.BlackPantherUnsuited;
import com.projecthero.mod.blackpanther.item.BlackPantherItems;
import com.projecthero.mod.hero.AbilitySlot;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * v0.17.7 Black Panther rework (user): the crafted necklace plus H forms the suit and switches the kit (H again, or the necklace
 * off, puts it away; the necklace model whenever it is worn, suited or not; suited armour 20 / 8; Resistance I always; the Flying Kick follows the aim; old suits become one necklace); the claws are always out (no toggle, no model);
 * melee 5 unsuited / 10 suited; Regeneration I / II; the Warrior's Combo 5 / 7 / 10 (8 / 9 / 10 suited, and it is the suited R
 * too); Takedown reaches 3 blocks for 10; Block cuts frontal hits; Shift+R Kinetic Pulse 10; the new Ground Pound, Flying Kick and
 * Rising Uppercut. Vindicators (no natural armour, they do not burn in daylight) with 200 health take the exact numbers. Mock players are not ticked by the
 * server, so moves over time are stepped with {@link BlackPanther#tick}.
 */
public class BlackPantherV0177GameTests implements FabricGameTest {
	/** A tough, armourless, motionless target at {@code pos} (the player stands at 2.5, 2, 2.5 facing south, +z). */
	private static Vindicator dummy(GameTestHelper helper, BlockPos pos) {
		Vindicator s = helper.spawn(EntityType.VINDICATOR, pos);
		s.setNoAi(true);
		s.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		s.setHealth(200f);
		return s;
	}

	/** A bare fist with no power at all (whatever else the mod adds to every player). */
	private static double fist(GameTestHelper helper) {
		return helper.makeMockServerPlayerInLevel().getAttributeValue(Attributes.ATTACK_DAMAGE);
	}

	private static void aimAt(ServerPlayer p, net.minecraft.world.entity.LivingEntity e) {
		p.lookAt(EntityAnchorArgument.Anchor.EYES, e.getEyePosition().add(0, -0.4, 0));
	}

	private static void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		p.setShiftKeyDown(shift);
		BlackPantherAbilityManager.handle(p, slot, true);
		BlackPantherAbilityManager.handle(p, slot, false);
		p.setShiftKeyDown(false);
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void clawsAreAlwaysOutInTheSuit(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		helper.assertTrue(!BlackPanther.clawsOut(p), "no claws out of the suit");
		BlackPantherGameTests.wearSuit(p);
		BlackPantherGameTests.finishChange(p);
		helper.assertTrue(BlackPanther.clawsOut(p), "the suit on: the claws are out with no toggle");
		press(p, AbilitySlot.SLOT_8, false);
		helper.assertTrue(BlackPanther.clawsOut(p), "plain N does not put them away (there is no claw toggle)");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (fist(helper) - 1.0 + BlackPantherConfig.SUITED_MELEE)) < 1.0e-6,
				"the claws' damage is always on: " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void meleeIsFiveUnsuitedAndTenSuited(GameTestHelper helper) {
		double fist = fist(helper); // 1, the vanilla fist
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		helper.assertTrue(BlackPantherConfig.UNSUITED_MELEE == 5.0 && BlackPantherConfig.SUITED_MELEE == 10.0, "the user's numbers");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (fist + 4.0)) < 1.0e-6,
				"unsuited fist 5, got " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
		BlackPantherGameTests.wearSuit(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (fist + 9.0)) < 1.0e-6,
				"suited fist 10, got " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
		BlackPantherGameTests.takeOffSuit(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (fist + 4.0)) < 1.0e-6, "back to 5 with a piece off");
		BlackPanther.revoke(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - fist) < 1.0e-6, "nothing once the power is gone");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void regenerationIsOneUnsuitedAndTwoSuited(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		MobEffectInstance regen = p.getEffect(MobEffects.REGENERATION);
		helper.assertTrue(regen != null && regen.getAmplifier() == 0, "Regeneration I without the suit");
		BlackPantherGameTests.wearSuit(p);
		regen = p.getEffect(MobEffects.REGENERATION);
		helper.assertTrue(regen != null && regen.getAmplifier() == 1, "Regeneration II with it");
		BlackPantherGameTests.takeOffSuit(p);
		regen = p.getEffect(MobEffects.REGENERATION);
		helper.assertTrue(regen != null && regen.getAmplifier() == 0, "back to I");
		BlackPanther.revoke(p);
		helper.assertTrue(!p.hasEffect(MobEffects.REGENERATION), "gone with the power");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void warriorsComboIsFiveSevenTen(GameTestHelper helper) {
		helper.assertTrue(BlackPantherUnsuited.comboDamage(0, false) == 5f && BlackPantherUnsuited.comboDamage(1, false) == 7f
				&& BlackPantherUnsuited.comboDamage(2, false) == 10f, "unsuited 5 / 7 / 10");
		helper.assertTrue(BlackPantherUnsuited.comboDamage(0, true) == 8f && BlackPantherUnsuited.comboDamage(1, true) == 9f
				&& BlackPantherUnsuited.comboDamage(2, true) == 10f, "suited 8 / 9 / 10");
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		Vindicator s = dummy(helper, new BlockPos(2, 2, 4));
		BlackPantherUnsuited.combo(p);
		float first = 200f - s.getHealth();
		helper.assertTrue(Math.abs(first - 5f) < 0.05f, "the jab lands on the press for 5, dealt " + first);
		helper.onEachTick(() -> BlackPanther.tick(p));
		helper.runAfterDelay(BlackPantherConfig.COMBO_STEP * 2 + 3, () -> {
			float total = 200f - s.getHealth();
			helper.assertTrue(Math.abs(total - 22f) < 0.1f, "5 + 7 + 10 = 22, dealt " + total);
			helper.succeed();
		});
	}

	// v0.17.7 suited kit: the suited R / Shift+R tests moved to BlackPantherSuitedV0177GameTests (Vibranium / Kinetic Combo)

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void takedownReachesThreeBlocksForTwelve(GameTestHelper helper) {
		helper.assertTrue(BlackPantherConfig.TAKEDOWN_RANGE == 3.0 && BlackPantherConfig.TAKEDOWN_DAMAGE == 12f, "the user's numbers (\"takedown: increase damage to 12\")");
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		Vindicator far = dummy(helper, new BlockPos(2, 2, 6)); // its front face 3.7 blocks off
		aimAt(p, far);
		BlackPantherUnsuited.takedown(p);
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherUnsuited.TAKEDOWN) == 0, "out of reach past 3 blocks");
		far.discard();
		Vindicator near = dummy(helper, new BlockPos(2, 2, 5)); // its front face 2.7 blocks off
		aimAt(p, near);
		BlackPantherUnsuited.takedown(p);
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherUnsuited.TAKEDOWN) > 0, "grabbed at under 3 blocks");
		helper.onEachTick(() -> BlackPanther.tick(p));
		helper.succeedWhen(() -> {
			float dealt = 200f - near.getHealth();
			helper.assertTrue(Math.abs(dealt - 12f) < 0.05f, "the slam does 12, dealt " + dealt);
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void blockCutsFrontalHits(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		ServerPlayer control = BlackPantherGameTests.bare(helper);
		Zombie front = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 4)); // yaw 0 faces south, +z
		front.setNoAi(true);
		BlackPantherUnsuited.raiseBlock(p);
		helper.assertTrue(BlackPantherUnsuited.blocking(p) && BlackPanther.state(p).absorbing, "C raises the guard");
		float before = p.getHealth();
		p.hurt(p.damageSources().mobAttack(front), 10f);
		float lost = before - p.getHealth();
		float cBefore = control.getHealth();
		control.hurt(control.damageSources().mobAttack(front), 10f);
		float plain = cBefore - control.getHealth();
		helper.assertTrue(plain > 0f && Math.abs(lost / plain - (1f - BlackPantherConfig.BLOCK_REDUCTION)) < 0.05f,
				"60% less from the front: lost " + lost + " of " + plain);
		// from behind it does nothing
		p.invulnerableTime = 0;
		control.invulnerableTime = 0;
		Zombie back = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 0));
		back.setNoAi(true);
		before = p.getHealth();
		p.hurt(p.damageSources().mobAttack(back), 4f);
		lost = before - p.getHealth();
		cBefore = control.getHealth();
		control.hurt(control.damageSources().mobAttack(back), 4f);
		plain = cBefore - control.getHealth();
		helper.assertTrue(plain > 0f && Math.abs(lost - plain) < 0.05f, "nothing from behind: lost " + lost + " of " + plain);
		BlackPantherUnsuited.lowerBlock(p);
		helper.assertTrue(!BlackPantherUnsuited.blocking(p) && !BlackPanther.state(p).absorbing, "released, it comes down");
		helper.succeed();
	}

	/** Back-dates the running suit change (up or down) so the next H is taken. */
	private static void endChange(ServerPlayer p) {
		var s = BlackPanther.state(p).copy();
		s.suitChangeAt = p.level().getGameTime() - BlackPantherConfig.SUIT_UP_TICKS - 1;
		p.setAttached(ModAttachments.BLACK_PANTHER_STATE, s);
	}

	private static int necklaces(ServerPlayer p) {
		return p.getInventory().countItem(BlackPantherItems.NECKLACE); // counts the worn one too
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void hWithTheNecklaceFormsTheSuitAndHAgainPutsItAway(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		double baseArmour = p.getAttributeValue(Attributes.ARMOR);
		double baseTough = p.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		// no necklace: H does nothing to the suit, the unsuited kit stays
		BlackPanther.toggleSuit(p);
		helper.assertTrue(!BlackPanther.suited(p) && !BlackPanther.suitedKit(p), "no necklace: no suit");
		// the necklace on alone does not suit him up
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(BlackPantherItems.NECKLACE));
		BlackPanther.tick(p);
		helper.assertTrue(!BlackPanther.suited(p), "wearing the necklace is not the suit by itself");
		// H: the suit runs out, the suited kit is live, the armour and Regeneration II come with it
		BlackPanther.toggleSuit(p);
		helper.assertTrue(BlackPanther.suited(p) && BlackPanther.suitedKit(p), "H: the suit forms");
		helper.assertTrue(BlackPanther.changing(p) && BlackPanther.state(p).animId == com.projecthero.mod.blackpanther.data.BlackPantherState.ANIM_SUIT_UP,
				"the suit-up build plays");
		helper.assertTrue(BlackPantherConfig.SUIT_ARMOR == 20.0 && BlackPantherConfig.SUIT_TOUGHNESS == 8.0,
				"user: \"make the black panther armour diamond level armour\" -- full diamond, 20 / 8");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - baseArmour - 20.0) < 1.0e-4,
				"20 armour while suited, got +" + (p.getAttributeValue(Attributes.ARMOR) - baseArmour));
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS) - baseTough - 8.0) < 1.0e-4,
				"8 toughness while suited, got +" + (p.getAttributeValue(Attributes.ARMOR_TOUGHNESS) - baseTough));
		MobEffectInstance regen = p.getEffect(MobEffects.REGENERATION);
		helper.assertTrue(regen != null && regen.getAmplifier() == BlackPantherConfig.REGEN_SUITED, "Regeneration II in the suit");
		BlackPanther.toggleSuit(p);
		helper.assertTrue(BlackPanther.suited(p), "a second H mid-build is ignored (one change at a time)");
		BlackPantherGameTests.finishChange(p);
		press(p, AbilitySlot.SLOT_3, false);
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherAbilities.LEAP) > 0, "suited X: Panther Leap");
		// H again: the suit drains back into the necklace, which stays on
		BlackPanther.toggleSuit(p);
		helper.assertTrue(!BlackPanther.suited(p) && BlackPanther.state(p).animId == com.projecthero.mod.blackpanther.data.BlackPantherState.ANIM_SUIT_DOWN,
				"H again: the suit drains away");
		helper.assertTrue(BlackPanther.wearsNecklace(p), "the necklace stays on");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - baseArmour) < 1.0e-4, "the suit's armour goes with it");
		press(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherUnsuited.POUND) > 0, "unsuited Shift+G: Ground Pound");
		// suited again, then the necklace comes off: the suit goes too
		endChange(p);
		BlackPanther.toggleSuit(p);
		helper.assertTrue(BlackPanther.suited(p), "H once more: suited");
		p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		BlackPanther.syncSuit(p); // what the tick runs every tick (tick itself runs once a game tick, and this tick it already ran)
		helper.assertTrue(!BlackPanther.suited(p) && !BlackPanther.suitedKit(p), "necklace off: the suit drains away");
		// someone without the power: the necklace does nothing
		ServerPlayer other = helper.makeMockServerPlayerInLevel();
		other.setItemSlot(EquipmentSlot.CHEST, new ItemStack(BlackPantherItems.NECKLACE));
		BlackPanther.toggleSuit(other);
		helper.assertTrue(!BlackPanther.suited(other), "no power: no suit");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void hPutsOnANecklaceFromTheInventory(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		p.getInventory().add(new ItemStack(BlackPantherItems.NECKLACE));
		BlackPanther.toggleSuit(p);
		helper.assertTrue(BlackPanther.wearsNecklace(p) && BlackPanther.suited(p), "the necklace goes on and the suit forms");
		helper.assertTrue(p.getInventory().countItem(Items.IRON_CHESTPLATE) == 1, "the chestplate it replaced is kept");
		helper.assertTrue(necklaces(p) == 1, "still one necklace");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theNecklaceHasOneRecipeAndTheArmourPiecesAreGone(GameTestHelper helper) {
		var recipes = helper.getLevel().getServer().getRecipeManager();
		var r = recipes.byKey(com.projecthero.mod.ProjectHeroMod.id("black_panther_necklace"));
		helper.assertTrue(r.isPresent(), "the necklace recipe loads");
		helper.assertTrue(r.get().value().getResultItem(helper.getLevel().registryAccess()).is(BlackPantherItems.NECKLACE), "it makes the necklace");
		int vibranium = 0;
		for (var ing : r.get().value().getIngredients()) {
			if (ing.test(new ItemStack(com.projecthero.mod.ultron.contagion.item.ContagionItems.VIBRANIUM_INGOT))) {
				vibranium++;
			}
		}
		helper.assertTrue(vibranium == 6, "6 Vibranium Ingots, the old set's cost: " + vibranium);
		for (String id : new String[] { "black_panther_helmet", "black_panther_chestplate", "black_panther_leggings", "black_panther_boots", "panther_habit" }) {
			helper.assertTrue(recipes.byKey(com.projecthero.mod.ProjectHeroMod.id(id)).isEmpty(), "no recipe for " + id);
		}
		ItemStack necklace = new ItemStack(BlackPantherItems.NECKLACE);
		helper.assertTrue(!necklace.isDamageableItem(), "the necklace never wears out");
		helper.assertTrue(BlackPantherItems.NECKLACE instanceof Equipable e && e.getEquipmentSlot() == EquipmentSlot.CHEST,
				"worn in the chestplate slot, like the Flash Ring");
		helper.assertTrue(!(BlackPantherItems.SUIT_HELMET instanceof ArmorItem), "the old pieces are not armour any more");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theNecklaceShowsWheneverItIsWorn(GameTestHelper helper) {
		// user: "make the black panther necklace model visible when not transformed, should be visible when the necklace is equipped"
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		helper.assertTrue(!BlackPanther.showsNecklace(p), "no necklace, no suit: not drawn");
		p.getInventory().add(new ItemStack(BlackPantherItems.NECKLACE));
		helper.assertTrue(!BlackPanther.showsNecklace(p), "a necklace only in the inventory: not drawn");
		p.getInventory().clearContent();
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(BlackPantherItems.NECKLACE));
		helper.assertTrue(BlackPanther.showsNecklace(p) && !BlackPanther.suited(p), "necklace worn, not suited: drawn");
		BlackPanther.toggleSuit(p);
		helper.assertTrue(BlackPanther.showsNecklace(p) && BlackPanther.changing(p), "during the suit-up build: drawn");
		BlackPantherGameTests.finishChange(p);
		helper.assertTrue(BlackPanther.showsNecklace(p), "suited with the necklace: drawn");
		BlackPanther.toggleSuit(p);
		helper.assertTrue(BlackPanther.showsNecklace(p) && !BlackPanther.suited(p), "the suit draining back / put away: still drawn");
		p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		helper.assertTrue(!BlackPanther.showsNecklace(p), "necklace taken off: not drawn");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void oldSuitsBecomeOneNecklace(GameTestHelper helper) {
		// 0.17.4-0.17.6: the habit in the necklace, suited at the time
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		var old = BlackPanther.state(p).copy();
		old.v177 = false;
		old.habit = true;
		old.suited = true;
		old.necklaceOff = true;
		p.setAttached(ModAttachments.BLACK_PANTHER_STATE, old);
		BlackPanther.onPlayerJoin(p);
		helper.assertTrue(BlackPanther.wearsNecklace(p), "it was on: the necklace is worn");
		helper.assertTrue(BlackPanther.suited(p), "and the suit stays on");
		helper.assertTrue(BlackPanther.state(p).v177 && !BlackPanther.state(p).habit && !BlackPanther.state(p).necklaceOff, "migrated once");
		BlackPanther.onPlayerJoin(p);
		helper.assertTrue(necklaces(p) == 1, "not handed out twice");
		// 0.17.3: no habit flag, not migrated at all, suit off: the necklace goes into the inventory
		ServerPlayer o = BlackPantherGameTests.bare(helper);
		var older = new com.projecthero.mod.blackpanther.data.BlackPantherState();
		older.hasPower = true;
		o.setAttached(ModAttachments.BLACK_PANTHER_STATE, older);
		BlackPanther.onPlayerJoin(o);
		helper.assertTrue(necklaces(o) == 1 && !BlackPanther.wearsNecklace(o) && !BlackPanther.suited(o), "into the inventory");
		// a new Black Panther gets nothing for free
		ServerPlayer fresh = BlackPantherGameTests.bare(helper);
		BlackPanther.onPlayerJoin(fresh);
		helper.assertTrue(necklaces(fresh) == 0, "a new power is not given one");
		// today's first build: the four armour pieces worn (suited in them) -> one necklace, worn, still suited
		ServerPlayer dev = BlackPantherGameTests.bare(helper);
		dev.setItemSlot(EquipmentSlot.HEAD, new ItemStack(BlackPantherItems.SUIT_HELMET));
		dev.setItemSlot(EquipmentSlot.CHEST, new ItemStack(BlackPantherItems.SUIT_CHESTPLATE));
		dev.setItemSlot(EquipmentSlot.LEGS, new ItemStack(BlackPantherItems.SUIT_LEGGINGS));
		dev.setItemSlot(EquipmentSlot.FEET, new ItemStack(BlackPantherItems.SUIT_BOOTS));
		var was = BlackPanther.state(dev).copy();
		was.suited = true;
		dev.setAttached(ModAttachments.BLACK_PANTHER_STATE, was);
		BlackPanther.convertLegacyItems(dev);
		BlackPanther.tick(dev);
		helper.assertTrue(necklaces(dev) == 1 && BlackPanther.wearsNecklace(dev), "one necklace for the set, worn where the chestplate was");
		helper.assertTrue(dev.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && dev.getItemBySlot(EquipmentSlot.FEET).isEmpty(), "the pieces are gone");
		helper.assertTrue(BlackPanther.suited(dev), "and he stays suited");
		BlackPanther.convertLegacyItems(dev);
		helper.assertTrue(necklaces(dev) == 1, "never twice");
		// loose pieces (anyone): a set and a spare piece is still one necklace
		ServerPlayer loose = helper.makeMockServerPlayerInLevel();
		for (Item piece : new Item[] { BlackPantherItems.SUIT_HELMET, BlackPantherItems.SUIT_CHESTPLATE, BlackPantherItems.SUIT_LEGGINGS,
				BlackPantherItems.SUIT_BOOTS, BlackPantherItems.SUIT_BOOTS }) {
			loose.getInventory().add(new ItemStack(piece));
		}
		BlackPanther.convertLegacyItems(loose);
		helper.assertTrue(necklaces(loose) == 1 && loose.getInventory().countItem(BlackPantherItems.SUIT_BOOTS) == 0, "five pieces: one necklace");
		// an old Panther Habit item becomes a necklace
		ItemStack habit = new ItemStack(BlackPantherItems.HABIT);
		fresh.setItemInHand(InteractionHand.MAIN_HAND, habit);
		habit.use(fresh.level(), fresh, InteractionHand.MAIN_HAND);
		helper.assertTrue(necklaces(fresh) == 1 && fresh.getInventory().countItem(BlackPantherItems.HABIT) == 0, "the habit becomes the necklace");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void groundPoundSlamsRoundHim(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		Vindicator s = dummy(helper, new BlockPos(4, 2, 3));
		BlackPantherUnsuited.groundPound(p);
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherUnsuited.POUND) > 0, "hopped");
		helper.onEachTick(() -> BlackPanther.tick(p));
		helper.succeedWhen(() -> {
			float dealt = 200f - s.getHealth();
			helper.assertTrue(Math.abs(dealt - BlackPantherConfig.POUND_DAMAGE) < 0.05f, "the slam does 15 (user: \"Ground pound: increase damage to 15\"), dealt " + dealt);
			helper.assertTrue(s.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and slows");
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void flyingKickHitsTheFirstForTen(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		Vindicator s = dummy(helper, new BlockPos(2, 2, 3)); // right in front
		BlackPantherUnsuited.flyingKick(p);
		float dealt = 200f - s.getHealth();
		helper.assertTrue(Math.abs(dealt - 10f) < 0.05f, "the kick does 10, dealt " + dealt);
		helper.succeed();
	}

	/** A lone unsuited Black Panther at relative height {@code y}, airborne, facing south (+z) with the given look pitch. */
	private static ServerPlayer kicker(GameTestHelper helper, double y, float pitch) {
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, y, 2.5));
		p.moveTo(at.x, at.y, at.z, 0f, pitch);
		p.setOnGround(false);
		return p;
	}

	// user: "make the flying kick ability go where the player aims instead of just straight ahead please" -- one player per test
	// (another player in reach would take the kick)

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void flyingKickAimedUpClimbs(GameTestHelper helper) {
		ServerPlayer p = kicker(helper, 2.0, -45f);
		BlackPantherUnsuited.flyingKick(p);
		net.minecraft.world.phys.Vec3 v = p.getDeltaMovement();
		double s = BlackPantherConfig.KICK_SPEED * Math.sin(Math.toRadians(45));
		helper.assertTrue(Math.abs(v.y - s) < 0.02, "aimed 45 up: the kick climbs, vy " + v.y + " want " + s);
		helper.assertTrue(Math.abs(v.z - s) < 0.02 && Math.abs(v.x) < 0.02, "and goes forward as much, " + v);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void flyingKickAimedDownDives(GameTestHelper helper) {
		ServerPlayer p = kicker(helper, 5.5, 45f);
		BlackPantherUnsuited.flyingKick(p);
		net.minecraft.world.phys.Vec3 v = p.getDeltaMovement();
		double s = BlackPantherConfig.KICK_SPEED * Math.sin(Math.toRadians(45));
		helper.assertTrue(Math.abs(v.y + s) < 0.02 && Math.abs(v.z - s) < 0.02, "aimed 45 down: a dive kick, " + v);
		// it holds that line while it runs (stepped by hand: mock players are not ticked)
		BlackPanther.tick(p);
		helper.assertTrue(p.getDeltaMovement().y < -0.5, "still diving the next step, " + p.getDeltaMovement());
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void flyingKickLevelGoesStraightAhead(GameTestHelper helper) {
		ServerPlayer p = kicker(helper, 3.0, 0f);
		BlackPantherUnsuited.flyingKick(p);
		net.minecraft.world.phys.Vec3 v = p.getDeltaMovement();
		helper.assertTrue(Math.abs(v.z - BlackPantherConfig.KICK_SPEED) < 0.02 && Math.abs(v.y) < 0.02 && Math.abs(v.x) < 0.02,
				"level aim: straight ahead, " + v);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void flyingKickDiveStopsAtTheGround(GameTestHelper helper) {
		// a steep dive from just over the floor: the ground stops it at once, nothing carried into the block, a soft landing
		helper.setBlock(new BlockPos(2, 1, 2), net.minecraft.world.level.block.Blocks.STONE); // a floor right under his feet
		helper.setBlock(new BlockPos(2, 1, 3), net.minecraft.world.level.block.Blocks.STONE);
		ServerPlayer p = kicker(helper, 2.3, 80f);
		p.fallDistance = 6f;
		BlackPantherUnsuited.flyingKick(p);
		helper.assertTrue(p.getDeltaMovement().y > -0.3 && p.getDeltaMovement().horizontalDistance() < 0.01,
				"the floor stops the dive dead, " + p.getDeltaMovement());
		helper.assertTrue(p.fallDistance < 1f, "and the fall is forgiven, " + p.fallDistance);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void flyingKickStopsAtAWall(GameTestHelper helper) {
		helper.setBlock(new BlockPos(2, 2, 3), net.minecraft.world.level.block.Blocks.STONE);
		helper.setBlock(new BlockPos(2, 3, 3), net.minecraft.world.level.block.Blocks.STONE);
		ServerPlayer p = kicker(helper, 2.0, 0f);
		BlackPantherUnsuited.flyingKick(p);
		helper.assertTrue(p.getDeltaMovement().horizontalDistance() < 0.01, "a wall ahead: no dash into it, " + p.getDeltaMovement());
		helper.succeed();
	}

	/**
	 * The peak of a standing jump, in blocks, from what the player's own jump would launch him at (jump strength x the block's jump
	 * factor + any Jump Boost, as {@code LivingEntity.getJumpPower}) run through vanilla's airborne physics: each tick he moves by his
	 * vertical speed, then it loses gravity 0.08 and is multiplied by the 0.98 drag.
	 */
	private static double jumpPeak(ServerPlayer p) {
		double v = p.getAttributeValue(Attributes.JUMP_STRENGTH) + p.getJumpBoostPower();
		double y = 0, peak = 0;
		for (int i = 0; i < 200 && v > -1.0; i++) {
			y += v;
			v = (v - 0.08) * 0.98;
			peak = Math.max(peak, y);
		}
		return peak;
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void jumpsTwoBlocksUnsuitedAndThreeSuited(GameTestHelper helper) {
		// user: "player can jump 2 blocks without the suit / player can jump 3 blocks with the suit"
		ServerPlayer plain = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(jumpPeak(plain) < 1.3, "a vanilla jump is about 1.25 blocks, got " + jumpPeak(plain));
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		helper.assertTrue(!p.hasEffect(MobEffects.JUMP), "no Jump Boost any more (a jump-strength modifier instead)");
		double unsuited = jumpPeak(p);
		helper.assertTrue(unsuited >= 2.0 && unsuited < 3.0, "unsuited: clears a 2-block wall, not a 3-block one, peak " + unsuited);
		BlackPantherGameTests.wearSuit(p);
		double suited = jumpPeak(p);
		helper.assertTrue(suited >= 3.0 && suited < 4.0, "suited: clears a 3-block wall, not a 4-block one, peak " + suited);
		BlackPantherGameTests.takeOffSuit(p);
		helper.assertTrue(Math.abs(jumpPeak(p) - unsuited) < 1.0e-6, "back to 2 blocks out of the suit");
		BlackPanther.revoke(p);
		helper.assertTrue(jumpPeak(p) < 1.3, "a plain jump once the power is gone, peak " + jumpPeak(p));
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 300)
	public void blockHasNoTimeLimitAndSixSecondsAfterRelease(GameTestHelper helper) {
		// user: "Block: remove time limit, 6 second cooldown when block is released"
		helper.assertTrue(BlackPantherConfig.BLOCK_COOLDOWN == 120, "6 s cooldown");
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		BlackPantherUnsuited.raiseBlock(p);
		helper.assertTrue(BlackPantherUnsuited.blocking(p) && BlackPanther.cooldownRemaining(p, BlackPantherUnsuited.BLOCK) == 0,
				"raised; no cooldown while it is up");
		helper.onEachTick(() -> BlackPanther.tick(p));
		helper.runAfterDelay(160, () -> { // 8 s, past the old 5 s limit
			helper.assertTrue(BlackPantherUnsuited.blocking(p), "still up after 8 s while C is held");
			BlackPantherUnsuited.lowerBlock(p);
			int cd = BlackPanther.cooldownRemaining(p, BlackPantherUnsuited.BLOCK);
			helper.assertTrue(!BlackPantherUnsuited.blocking(p) && cd == 120, "released: the 6 s cooldown starts now, " + cd);
			BlackPantherUnsuited.raiseBlock(p);
			helper.assertTrue(!BlackPantherUnsuited.blocking(p), "it cannot go straight back up");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void wakandaForeverGivesFourBuffsForFiftySeconds(GameTestHelper helper) {
		// user: "Change the name to "Wakanda Forever", player gets speed 2, strength 2, regeneration 2, resistance 2, lasts 50 seconds,
		// 100 second cooldown upon activation, players emits purple particle effects while this is active"
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		BlackPantherUnsuited.resolve(p);
		helper.assertTrue(BlackPanther.cooldownRemaining(p, BlackPantherUnsuited.RESOLVE) == 2000, "100 s cooldown from activation");
		for (var e : java.util.List.of(MobEffects.MOVEMENT_SPEED, MobEffects.DAMAGE_BOOST, MobEffects.REGENERATION, MobEffects.DAMAGE_RESISTANCE)) {
			MobEffectInstance i = p.getEffect(e);
			helper.assertTrue(i != null && i.getAmplifier() == 1 && i.getDuration() == 1000, "level II for 50 s: " + e.getRegisteredName() + " " + i);
		}
		// the passives' refresh never strips them
		BlackPanther.reconcile(p);
		BlackPanther.tick(p);
		helper.assertTrue(p.getEffect(MobEffects.REGENERATION).getAmplifier() == 1 && p.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() == 1
				&& p.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 1, "II survives the passive refresh");
		// ...and the endless I comes back when they run out (stepped: run each effect's clock down)
		for (int t = 0; t < 1001; t++) {
			p.getEffect(MobEffects.REGENERATION).tick(p, () -> { });
			p.getEffect(MobEffects.DAMAGE_RESISTANCE).tick(p, () -> { });
		}
		BlackPanther.reconcile(p);
		MobEffectInstance regen = p.getEffect(MobEffects.REGENERATION), res = p.getEffect(MobEffects.DAMAGE_RESISTANCE);
		helper.assertTrue(regen != null && regen.getAmplifier() == 0 && regen.isInfiniteDuration(), "Regeneration I back after, " + regen);
		helper.assertTrue(res != null && res.getAmplifier() == 0 && res.isInfiniteDuration(), "Resistance I back after, " + res);
		helper.succeed();
	}

	/** Puts the necklace in front of {@code p} with a dispenser facing south (+z) at relative (2, 2, 1) and fires it. */
	private static void dispenseNecklace(GameTestHelper helper, ServerPlayer p) {
		BlockPos rel = new BlockPos(2, 2, 1);
		helper.setBlock(rel, net.minecraft.world.level.block.Blocks.DISPENSER.defaultBlockState()
				.setValue(net.minecraft.world.level.block.DispenserBlock.FACING, net.minecraft.core.Direction.SOUTH));
		BlockPos abs = helper.absolutePos(rel);
		var be = (net.minecraft.world.level.block.entity.DispenserBlockEntity) helper.getLevel().getBlockEntity(abs);
		var source = new net.minecraft.core.dispenser.BlockSource(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), be);
		com.projecthero.mod.blackpanther.item.BlackPantherNecklaceItem.DISPENSE.dispense(source, new ItemStack(BlackPantherItems.NECKLACE));
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void onlyThePantherCanWearTheNecklace(GameTestHelper helper) {
		// user: "black panther necklace can only be worn by players with black panther ability"
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		ItemStack necklace = new ItemStack(BlackPantherItems.NECKLACE);
		// the armour slot (drag / click), shift-click and the hotbar swap all ask this
		helper.assertTrue(p.getEquipmentSlotForItem(necklace) != EquipmentSlot.CHEST, "not a chest item for a non-Panther");
		net.minecraft.world.inventory.Slot chestSlot = p.inventoryMenu.getSlot(6); // InventoryMenu: 5 head, 6 chest, 7 legs, 8 feet
		helper.assertTrue(!chestSlot.mayPlace(necklace), "the chestplate slot refuses it");
		// shift-click from the inventory
		p.getInventory().setItem(9, necklace.copy());
		p.inventoryMenu.quickMoveStack(p, 9);
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "shift-click does not put it on");
		p.getInventory().clearContent();
		// right-click
		p.setItemInHand(InteractionHand.MAIN_HAND, necklace.copy());
		BlackPantherItems.NECKLACE.use(p.level(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "right-click is refused");
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		// a dispenser shoots it out instead of fitting it
		dispenseNecklace(helper, p);
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "a dispenser does not fit it on him");
		// forced on (a command, another mod): it pops off into the inventory on the next tick
		p.setItemSlot(EquipmentSlot.CHEST, necklace.copy());
		BlackPanther.tick(p);
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && p.getInventory().countItem(BlackPantherItems.NECKLACE) == 1,
				"forced on, it pops off into the inventory");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePantherCanWearTheNecklaceAndLosesItWithThePower(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		ItemStack necklace = new ItemStack(BlackPantherItems.NECKLACE);
		helper.assertTrue(p.getEquipmentSlotForItem(necklace) == EquipmentSlot.CHEST, "a chest item for the Black Panther");
		helper.assertTrue(p.inventoryMenu.getSlot(6).mayPlace(necklace), "the chestplate slot takes it");
		p.getInventory().setItem(9, necklace.copy());
		p.inventoryMenu.quickMoveStack(p, 9);
		helper.assertTrue(BlackPanther.wearsNecklace(p), "shift-click puts it on");
		p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		p.getInventory().clearContent();
		dispenseNecklace(helper, p);
		helper.assertTrue(BlackPanther.wearsNecklace(p), "a dispenser fits it on him");
		BlackPanther.tick(p);
		helper.assertTrue(BlackPanther.wearsNecklace(p), "and it stays on");
		// the power lost while wearing it: it comes off into the inventory
		BlackPanther.revoke(p);
		helper.assertTrue(!BlackPanther.wearsNecklace(p) && p.getInventory().countItem(BlackPantherItems.NECKLACE) == 1,
				"power gone: the necklace pops off into the inventory");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void resistanceOneSuitedAndUnsuited(GameTestHelper helper) {
		// user: "give player passive resistance one"
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		MobEffectInstance res = p.getEffect(MobEffects.DAMAGE_RESISTANCE);
		helper.assertTrue(res != null && res.getAmplifier() == 0 && res.isInfiniteDuration(), "Resistance I out of the suit");
		BlackPantherGameTests.wearSuit(p);
		res = p.getEffect(MobEffects.DAMAGE_RESISTANCE);
		helper.assertTrue(res != null && res.getAmplifier() == 0 && res.isInfiniteDuration(), "Resistance I in the suit");
		p.removeEffect(MobEffects.DAMAGE_RESISTANCE); // milk, say: the power puts it back
		BlackPanther.reconcile(p);
		helper.assertTrue(p.hasEffect(MobEffects.DAMAGE_RESISTANCE), "refreshed like the Regeneration");
		BlackPanther.revoke(p);
		helper.assertTrue(!p.hasEffect(MobEffects.DAMAGE_RESISTANCE), "gone with the power");
		helper.assertTrue(!BlackPanther.hasPower(helper.makeMockServerPlayerInLevel())
				&& !helper.makeMockServerPlayerInLevel().hasEffect(MobEffects.DAMAGE_RESISTANCE), "never without the power");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void risingUppercutIsVAndTrackingMovedToShiftV(GameTestHelper helper) {
		ServerPlayer p = BlackPantherGameTests.bare(helper);
		Vindicator s = dummy(helper, new BlockPos(2, 2, 4));
		aimAt(p, s);
		press(p, AbilitySlot.SLOT_5, false);
		float dealt = 200f - s.getHealth();
		helper.assertTrue(Math.abs(dealt - BlackPantherConfig.UPPERCUT_DAMAGE) < 0.05f, "V: Rising Uppercut 9, dealt " + dealt);
		helper.assertTrue(!BlackPanther.state(p).senses, "V is not the tracking any more");
		press(p, AbilitySlot.SLOT_5, true);
		helper.assertTrue(BlackPanther.state(p).senses, "Shift+V: Hunter's Tracking");
		helper.assertTrue(BlackPantherConfig.KNIFE_DAMAGE == 8f && BlackPantherConfig.SWEEP_DAMAGE == 7f, "knife 8, sweep 7");
		helper.succeed();
	}
}
