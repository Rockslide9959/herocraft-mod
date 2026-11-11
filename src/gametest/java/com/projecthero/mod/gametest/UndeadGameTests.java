package com.projecthero.mod.gametest;

import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadAbilities;
import com.projecthero.mod.undead.UndeadBoneSword;
import com.projecthero.mod.undead.UndeadConfig;
import com.projecthero.mod.undead.UndeadParts;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadCloneEntity;
import com.projecthero.mod.undead.entity.UndeadPartEntity;
import com.projecthero.mod.undead.item.UndeadItems;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.kryptonian.Kryptonian;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Undead (Undead Unluck): where hits land, a part breaking and regrowing (for hunger), Undead (lethal damage shatters,
 * /kill still kills), the card and the one-Primary rule, the speed cycle, Victor, and each move's server side. Mock players
 * are not ticked by the server, so tests over time drive {@link Undead#tick}.
 */
public class UndeadGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		p.setYHeadRot(0f);
		p.yBodyRot = 0f;
		p.invulnerableTime = 0;
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	private static ServerPlayer undead(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Undead.grant(p);
		return p;
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<UndeadState> fn) {
		UndeadState n = Undead.state(p).copy();
		fn.accept(n);
		p.setAttached(ModAttachments.UNDEAD_STATE, n);
	}

	private static Zombie zombie(GameTestHelper helper, int x, int z) {
		Zombie zo = helper.spawn(EntityType.ZOMBIE, new BlockPos(x, 2, z));
		zo.setNoAi(true);
		zo.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		zo.setHealth(200);
		return zo;
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void hitsLandOnThePartTheyTouch(GameTestHelper helper) {
		ServerPlayer p = undead(helper); // yaw 0: facing +z, his right is -x
		AABB b = p.getBoundingBox();
		Vec3 c = b.getCenter();
		double h = b.getYsize();
		helper.assertTrue(UndeadParts.locate(p, new Vec3(c.x, b.minY + h * 0.9, c.z)) == UndeadState.HEAD, "the top quarter is the head");
		helper.assertTrue(UndeadParts.locate(p, new Vec3(c.x, b.minY + h * 0.55, c.z)) == UndeadState.BODY, "the middle is the body");
		helper.assertTrue(UndeadParts.locate(p, new Vec3(c.x - 0.28, b.minY + h * 0.55, c.z)) == UndeadState.RIGHT_ARM, "his right side is the right arm");
		helper.assertTrue(UndeadParts.locate(p, new Vec3(c.x + 0.28, b.minY + h * 0.55, c.z)) == UndeadState.LEFT_ARM, "his left side is the left arm");
		helper.assertTrue(UndeadParts.locate(p, new Vec3(c.x - 0.1, b.minY + h * 0.2, c.z)) == UndeadState.RIGHT_LEG, "low right is the right leg");
		helper.assertTrue(UndeadParts.locate(p, new Vec3(c.x + 0.1, b.minY + h * 0.2, c.z)) == UndeadState.LEFT_LEG, "low left is the left leg");
		float[] fall = UndeadParts.assign(p, p.damageSources().fall(), 6f, p.getRandom());
		helper.assertTrue(fall[UndeadState.RIGHT_LEG] == 3f && fall[UndeadState.LEFT_LEG] == 3f && fall[UndeadState.HEAD] == 0f, "a fall lands on both legs");
		float[] blast = UndeadParts.assign(p, p.damageSources().explosion(null, null), 10f, p.getRandom());
		for (float f : blast) {
			helper.assertTrue(f > 0f, "an explosion hits every part");
		}
		float[] magic = UndeadParts.assign(p, p.damageSources().magic(), 10f, p.getRandom());
		for (float f : magic) {
			helper.assertTrue(f == 0f, "magic hits no part");
		}
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anArrowBreaksTheArmItHits(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		AABB b = p.getBoundingBox();
		net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(helper.getLevel(), b.getCenter().x - 0.27,
				b.minY + b.getYsize() * 0.55, b.getCenter().z, new ItemStack(net.minecraft.world.item.Items.ARROW), null);
		boolean hurt = p.hurt(p.damageSources().arrow(arrow, null), 9f);
		helper.assertTrue(Undead.missing(p, UndeadState.RIGHT_ARM), "9 damage on the right arm (8 HP) breaks it: hurt=" + hurt + " hp=" + Undead.state(p).partHp);
		helper.assertTrue(!Undead.missing(p, UndeadState.LEFT_ARM) && !Undead.missing(p, UndeadState.BODY), "nothing else breaks");
		helper.assertTrue(Undead.state(p).kind(UndeadState.RIGHT_ARM) == UndeadState.KIND_BURST, "it bursts into pixels");
		helper.succeed();
	}

	// v0.17.3: the three-layer regrowth takes 170 ticks at Normal -- with the delay that is past 200
	/** Breaks {@code parts} now; they may start regrowing as soon as their own wait is up (the tests then drive the ticks). */
	private static void breakAll(ServerPlayer p, int... parts) {
		for (int part : parts) {
			Undead.breakPart(p, part, UndeadState.KIND_BURST);
		}
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void lethalDamageShattersButKillStillKills(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.hurt(p.damageSources().magic(), 1000f);
		helper.assertTrue(p.isAlive() && p.getHealth() == p.getMaxHealth(), "Undead: alive, health refilled");
		UndeadState s = Undead.state(p);
		helper.assertTrue(!s.broken(UndeadState.HEAD), "the head stays");
		for (int i = UndeadState.BODY; i < UndeadState.PARTS; i++) {
			helper.assertTrue(s.broken(i) && s.kind(i) == UndeadState.KIND_SHATTER, "part " + i + " shattered");
		}
		helper.assertTrue(Undead.reforming(p), "the grace after the shatter");
		float hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().magic(), 5f);
		helper.assertTrue(p.getHealth() == hp, "nothing hurts while re-forming");
		p.hurt(p.damageSources().genericKill(), Float.MAX_VALUE);
		helper.assertTrue(!p.isAlive(), "/kill still kills");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theCardGrantsThePowerAndTakesThePrimarySlot(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Kryptonian.grant(p);
		ItemStack card = new ItemStack(UndeadItems.UNDEAD_CARD);
		p.setItemInHand(InteractionHand.MAIN_HAND, card);
		card.use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(Undead.hasPower(p), "the card grants the power");
		helper.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "the card is used up");
		helper.assertTrue(!Kryptonian.hasPower(p), "the old Primary is replaced");
		helper.assertTrue(HeroTiers.holdsHero(p, Undead.KEY), "listed as the held hero");
		helper.assertTrue(PowerGrants.NOT_FROM_SERUMS.contains(Undead.KEY), "never from a serum");
		helper.assertTrue(!Undead.grant(p), "a second grant is refused");
		Undead.revoke(p);
		helper.assertTrue(!Undead.hasPower(p), "revoked");
		helper.succeed();
	}

	// ---------------------------------------------------------------- v0.17.3: NBC Heroes' Regenerative Segmentation

	/** Any way of losing legs gives the stage: one leg a limp, both the hand walk. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theStagesFollowTheParts(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Undead.breakPart(p, UndeadState.RIGHT_LEG, UndeadState.KIND_BURST);
		helper.assertTrue(Undead.stage(p) == com.projecthero.mod.undead.UndeadStages.LIMP, "one leg gone: a limp");
		Undead.breakPart(p, UndeadState.LEFT_LEG, UndeadState.KIND_BURST);
		helper.assertTrue(Undead.stage(p) == com.projecthero.mod.undead.UndeadStages.ARM_WALK, "both legs gone: on his hands");
		helper.succeed();
	}

	/** User: "if the players torso gets destroyed their armour falls off ... if leg gets destroyed remove leggings, if feet ... boots". */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void armourFallsOffTheLostParts(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(net.minecraft.world.item.Items.IRON_LEGGINGS));
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(net.minecraft.world.item.Items.IRON_BOOTS));
		Undead.breakPart(p, UndeadState.RIGHT_FOOT, UndeadState.KIND_BURST);
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).isEmpty(), "a foot gone: the boots fall off");
		helper.assertTrue(!p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).isEmpty(), "the leggings stay");
		Undead.breakPart(p, UndeadState.LEFT_LEG, UndeadState.KIND_BURST);
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).isEmpty(), "a leg gone: the leggings fall off");
		Undead.breakPart(p, UndeadState.BODY, UndeadState.KIND_BURST);
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(), "the torso gone: the chestplate falls off");
		java.util.List<net.minecraft.world.entity.item.ItemEntity> drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				p.getBoundingBox().inflate(4));
		helper.assertTrue(drops.size() == 3, "all three dropped at his feet, nothing lost or doubled: " + drops.size());
		// put back on with the torso still gone: refused, back into the inventory
		p.getInventory().clearContent();
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
		com.projecthero.mod.undead.UndeadArmour.forTestsTick(p);
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()
				&& p.getInventory().countItem(net.minecraft.world.item.Items.IRON_CHESTPLATE) == 1, "no torso to wear it on: back in the inventory");
		drops.forEach(e -> e.discard());
		Undead.restore(p);
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
		com.projecthero.mod.undead.UndeadArmour.forTestsTick(p);
		helper.assertTrue(!p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(), "regrown: it stays on");
		helper.succeed();
	}

	/** User: "if an arm gets destroyed that shoulder pad doesnt grow back and players armour value is lowered slightly". */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void losingAnArmTearsOffItsShoulderPad(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		ItemStack chest = new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE);
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, chest);
		com.projecthero.mod.undead.UndeadArmour.forTestsTick(p);
		java.util.function.DoubleSupplier penalty = () -> p.getAttribute(Attributes.ARMOR).getModifiers().stream()
				.filter(m -> m.id().getPath().equals("undead_lost_pads")).mapToDouble(m -> m.amount()).sum();
		helper.assertTrue(penalty.getAsDouble() == 0.0, "no penalty with both pads");
		Undead.breakPart(p, UndeadState.RIGHT_ARM, UndeadState.KIND_BURST);
		ItemStack worn = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
		helper.assertTrue(!worn.isEmpty(), "an arm: the chestplate stays on");
		helper.assertTrue(com.projecthero.mod.undead.UndeadArmour.lostPads(worn) == com.projecthero.mod.undead.UndeadArmour.PAD_RIGHT, "but the right pad is torn off");
		helper.assertTrue(worn.getDamageValue() > 0, "and it took damage");
		double pad = com.projecthero.mod.undead.UndeadArmour.padArmour(worn);
		helper.assertTrue(pad == 1.0, "iron: a point a pad: " + pad);
		helper.assertTrue(Math.abs(penalty.getAsDouble() + pad) < 0.01, "his armour drops a little: " + penalty.getAsDouble());
		Undead.restore(p); // the arm is back
		com.projecthero.mod.undead.UndeadArmour.forTestsTick(p);
		helper.assertTrue(com.projecthero.mod.undead.UndeadArmour.lostPads(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)) != 0,
				"the pad does not grow back with the arm");
		p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).setDamageValue(0); // repaired (anvil / Mending / the suit)
		com.projecthero.mod.undead.UndeadArmour.forTestsTick(p);
		helper.assertTrue(com.projecthero.mod.undead.UndeadArmour.lostPads(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)) == 0, "repaired: the pad is back");
		helper.assertTrue(penalty.getAsDouble() == 0.0, "and the armour");
		helper.succeed();
	}

	// ---------------------------------------------------------------- v0.17.3 tester fixes

	/** v0.17.3 (user): Undead's Suit mends itself while worn, one point every 2 s, never past whole, and not in the inventory. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void undeadsSuitMendsItselfWhileWorn(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		ItemStack worn = new ItemStack(UndeadItems.SUIT_LEGGINGS);
		worn.setDamageValue(10);
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, worn);
		ItemStack spare = new ItemStack(UndeadItems.SUIT_BOOTS);
		spare.setDamageValue(10);
		p.getInventory().setItem(5, spare);
		helper.assertTrue(UndeadItems.SUIT_CHESTPLATE.getDefense() == 6
				&& UndeadItems.SUIT_LEGGINGS.getDefense() == 5 && UndeadItems.SUIT_BOOTS.getDefense() == 2, "iron protection 6/5/2 (no helmet)");
		helper.assertTrue(worn.getMaxDamage() == net.minecraft.world.item.ArmorItem.Type.LEGGINGS.getDurability(15), "iron durability");
		helper.assertTrue(com.projecthero.mod.undead.item.UndeadSuitItem.REPAIR_EVERY == 40 && com.projecthero.mod.undead.item.UndeadSuitItem.REPAIR_AMOUNT == 1, "1 durability every 2 s");
		for (int i = 0; i < 20; i++) { // twenty 2 s steps
			com.projecthero.mod.undead.item.UndeadSuitItem.mend(worn, p);
			com.projecthero.mod.undead.item.UndeadSuitItem.mend(spare, p);
		}
		helper.assertTrue(worn.getDamageValue() == 0, "mended back to whole, never past: " + worn.getDamageValue());
		helper.assertTrue(spare.getDamageValue() == 10, "a spare piece in the inventory does not mend: " + spare.getDamageValue());
		helper.succeed();
	}

	/** v0.17.3 (user): the suit is not given with the power -- it is only crafted. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void undeadsSuitIsCraftedNotGiven(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		helper.assertTrue(p.getInventory().countItem(UndeadItems.SUIT_CHESTPLATE) == 0 && p.getInventory().countItem(UndeadItems.SUIT_LEGGINGS) == 0
				&& p.getInventory().countItem(UndeadItems.SUIT_BOOTS) == 0, "gaining the power gives no suit");
		var recipes = helper.getLevel().getRecipeManager();
		for (String id : new String[] { "undead_suit_chestplate", "undead_suit_leggings", "undead_suit_boots" }) {
			helper.assertTrue(recipes.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("projecthero", id)).isPresent(), "craftable: " + id);
		}
		helper.succeed();
	}

	/** Tester: "Alternation between left and right hands instead of just one when throwing arms or hands". */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shotsAlternateHands(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		// v0.17.3: the finger shots no longer bite the arm; the hand each one came from is FLAG_OFF_ARM (what every viewer poses)
		UndeadAbilities.partsBullet(p);
		boolean firstOff = Undead.state(p).flag(UndeadState.FLAG_OFF_ARM);
		UndeadAbilities.partsBullet(p);
		helper.assertTrue(Undead.state(p).flag(UndeadState.FLAG_OFF_ARM) != firstOff, "the second shot came from the other hand");
		UndeadAbilities.partsBullet(p);
		helper.assertTrue(Undead.state(p).flag(UndeadState.FLAG_OFF_ARM) == firstOff, "and the third from the first again");
		// with one arm gone, the other fires every time
		Undead.breakPart(p, UndeadState.LEFT_ARM, UndeadState.KIND_BURST);
		helper.assertTrue(Undead.shootingArm(p) == UndeadState.RIGHT_ARM, "one arm left: always that one");
		helper.succeed();
	}

	/** Tester: "A way people can naturally pick up the head": right-click the thrown head and it goes back on. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theThrownHeadCanBePickedUp(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		UndeadAbilities.headBullet(p);
		helper.assertTrue(Undead.missing(p, UndeadState.HEAD), "the head is thrown");
		UndeadPartEntity head = helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, new AABB(p.blockPosition()).inflate(8),
				e -> e.ownerIs(p) && e.bodyPart() == UndeadState.HEAD).stream().findFirst().orElse(null);
		helper.assertTrue(head != null, "the head entity is out");
		helper.assertTrue(head.isPickable(), "and can be clicked");
		ServerPlayer friend = mock(helper);
		head.interact(friend, InteractionHand.MAIN_HAND);
		helper.assertTrue(!Undead.missing(p, UndeadState.HEAD), "a friend right-clicking it puts it straight back on");
		helper.assertTrue(head.isRemoved(), "and the thrown one is gone");
		helper.succeed();
	}

	/** Tester: "A way people can naturally pick up the head": an Undead who is only a head can be picked up and carried. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aHeadOnlyUndeadCanBeCarried(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		ServerPlayer friend = mock(helper);
		helper.assertTrue(com.projecthero.mod.undead.UndeadCarry.tryPickUp(friend, InteractionHand.MAIN_HAND, p) == net.minecraft.world.InteractionResult.PASS,
				"a whole Undead can not be picked up");
		for (int i = UndeadState.BODY; i < UndeadState.PARTS; i++) {
			Undead.breakPart(p, i, UndeadState.KIND_BURST);
		}
		com.projecthero.mod.undead.UndeadCarry.tryPickUp(friend, InteractionHand.MAIN_HAND, p);
		helper.assertTrue(p.getVehicle() == friend, "only a head: the friend picks him up");
		Vec3 seat = com.projecthero.mod.undead.UndeadCarry.seat(friend);
		helper.assertTrue(seat.y < 1.2 && seat.horizontalDistance() > 0.3, "carried in front of the chest, not on the head: " + seat);
		Undead.restore(p);
		Undead.tick(p);
		helper.assertTrue(p.getVehicle() == null, "with his body back he climbs down");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noArmsNoShot(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Undead.breakPart(p, UndeadState.RIGHT_ARM, UndeadState.KIND_BURST);
		Undead.breakPart(p, UndeadState.LEFT_ARM, UndeadState.KIND_BURST);
		UndeadAbilities.partsBullet(p);
		UndeadAbilities.handBullet(p);
		AABB box = new AABB(p.blockPosition()).inflate(4);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, box, e -> true).isEmpty(), "nothing fires with no arms");
		helper.assertTrue(!UndeadAbilities.startPunch(p), "and no punch winds up");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void holdRThrowsTheArmAndUsesTheOtherWhenTheMainIsGone(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.setXRot(-85f);
		int main = Undead.mainArm(p);
		Undead.breakPart(p, main, UndeadState.KIND_BURST);
		com.projecthero.mod.hero.AbilitySlot r = com.projecthero.mod.hero.AbilitySlot.SLOT_1;
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, r, true);
		helper.onEachTick(() -> Undead.tick(p));
		helper.runAfterDelay(com.projecthero.mod.undead.UndeadBulletConfig.ARM_WINDUP_SHOW + 3, () -> helper.assertTrue(Undead.state(p).flag(UndeadState.FLAG_PUNCH),
				"held: the arm draws back"));
		// v0.17.3: the arm flies at one second, still held (no release needed)
		helper.runAfterDelay(com.projecthero.mod.undead.UndeadBulletConfig.ARM_HOLD + 2, () -> {
			int other = Undead.otherArm(main);
			helper.assertTrue(Undead.missing(p, other) && Undead.state(p).kind(other) == UndeadState.KIND_LAUNCHED, "the other arm was thrown");
			AABB box = new AABB(p.blockPosition()).inflate(12);
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, box, e -> e.style() == UndeadPartEntity.STYLE_PUNCH && e.bodyPart() == other)
					.size() == 1, "it flies as the punch");
			com.projecthero.mod.undead.UndeadAbilityManager.handle(p, r, false);
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, box, e -> e.isFinger()).isEmpty(), "letting go after it flew fires nothing");
			helper.assertTrue(Undead.cooldownRemaining(p, UndeadAbilities.PUNCH) == 0, "no cooldown");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void aTapOfRIsStillAPartsBullet(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.setXRot(-85f);
		com.projecthero.mod.hero.AbilitySlot r = com.projecthero.mod.hero.AbilitySlot.SLOT_1;
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, r, true);
		helper.onEachTick(() -> Undead.tick(p));
		helper.runAfterDelay(2, () -> {
			com.projecthero.mod.undead.UndeadAbilityManager.handle(p, r, false);
			AABB box = new AABB(p.blockPosition()).inflate(6);
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, box, e -> e.style() == UndeadPartEntity.STYLE_FINGER).size() == 1,
					"a tap fires a finger");
			helper.assertTrue(!Undead.state(p).flag(UndeadState.FLAG_PUNCH), "and winds nothing up");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- flight, the boost, the head-only regrowth

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void victorIsDisabled(GameTestHelper helper) {
		// v0.17.3 (user: "remove the Victor ability for now"): no key pulls the card, the kit never swaps, a stale save clears
		ServerPlayer p = undead(helper);
		helper.assertTrue(!UndeadConfig.VICTOR_ENABLED, "Victor is off");
		double base = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		UndeadAbilities.pullCard(p);
		helper.assertTrue(!Undead.victor(p) && Undead.state(p).victorUntil == 0L, "pulling the card does nothing");
		com.projecthero.mod.hero.AbilitySlot v = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		helper.assertTrue(com.projecthero.mod.undead.UndeadRibShot.ID.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, v)[0])
				&& com.projecthero.mod.undead.UndeadRibGuard.ID.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, v)[1]),
				"V is the Rib Shotgun, Shift+V the Rib Cage Guard");
		edit(p, n -> n.victorUntil = helper.getLevel().getGameTime() + 400);
		helper.assertTrue(!Undead.victor(p), "an old save with the card out is not Victor");
		helper.assertTrue(UndeadAbilities.PARTS_BULLET.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1)[0])
				&& com.projecthero.mod.undead.UndeadBloodMoves.SPRAY.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_2)[0])
				&& com.projecthero.mod.undead.UndeadBloodMoves.DEADLINE.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_6)[0]),
				"the kit never swaps to Victor's");
		Undead.tick(p);
		helper.assertTrue(Undead.state(p).victorUntil == 0L, "and the tick clears it");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - base) < 0.01, "still Bloody Arts");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void slashesHitWhatIsInFront(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Zombie z = zombie(helper, 2, 4);
		UndeadAbilities.crescent(p);
		float after = z.getHealth();
		helper.assertTrue(after < 200f, "Crimson Crescent Moon hits");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R aim, Shift+V: the Bone Sword and its kit (v0.17.3)

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void thePartsBulletArmFliesAtTheCrosshair(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.setXRot(0f);
		Zombie z = zombie(helper, 2, 9); // straight ahead, the eye line through its head
		UndeadAbilities.startPunch(p);
		edit(p, n -> n.setEx(UndeadState.X_PUNCH_START, helper.getLevel().getGameTime() - 20));
		UndeadAbilities.throwPunch(p);
		AABB box = new AABB(p.blockPosition()).inflate(6);
		java.util.List<UndeadPartEntity> arms = helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, box, e -> e.style() == UndeadPartEntity.STYLE_PUNCH);
		helper.assertTrue(arms.size() == 1, "the arm flies");
		UndeadPartEntity a = arms.get(0);
		Vec3 centre = a.position().add(0, a.getBbHeight() * 0.5, 0);
		Vec3 aimAt = new Vec3(z.getX(), p.getEyeY(), z.getBoundingBox().minZ);
		double cos = a.getDeltaMovement().normalize().dot(aimAt.subtract(centre).normalize());
		helper.assertTrue(cos > Math.cos(Math.toRadians(2.5)), "it flies at the crosshair: " + Math.toDegrees(Math.acos(Math.min(1, cos))) + " degrees off");
		helper.assertTrue(!a.isLeftBehind(), "and is not drawn as a part left standing");
		// the finger shots aim the same way
		edit(p, n -> n.abilityReadyAt.clear());
		Undead.restore(p);
		UndeadAbilities.partsBullet(p);
		UndeadPartEntity f = helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, box, e -> e.style() == UndeadPartEntity.STYLE_FINGER).get(0);
		Vec3 fc = f.position().add(0, f.getBbHeight() * 0.5, 0);
		double fcos = f.getDeltaMovement().normalize().dot(aimAt.subtract(fc).normalize());
		helper.assertTrue(fcos > Math.cos(Math.toRadians(2.5)), "a Parts Bullet too: " + Math.toDegrees(Math.acos(Math.min(1, fcos))));
		UndeadAbilities.partsBullet(p);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, box, e -> e.style() == UndeadPartEntity.STYLE_FINGER).size() == 2,
				"and fires again at once (no cooldown)");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void theThrownArmSurvivesItsRegrowthStarting(GameTestHelper helper) {
		// v0.17.3 bug (user: "the part bullet arm ability doesnt seem to shoot properly anymore"): since the NBC regrowth a
		// launched arm starts growing back the very next tick, and the start of the regrowth burst the thrown arm -- it was gone
		// a tick after leaving the shoulder, nothing flew and nothing was hit. Held R through the key flow, his tick running,
		// thrown straight up (open sky): it must keep flying while the arm regrows, and burst once the arm is whole.
		ServerPlayer p = undead(helper);
		// v0.17.3: fired straight up from above the barrier-roofed test area: open air for its whole 60 blocks, and it stays over the test's own (ticking) chunk
		Vec3 high = helper.absoluteVec(new Vec3(2.5, 40.0, 2.5));
		p.moveTo(high.x, high.y, high.z, 0f, 0f);
		p.setXRot(-89f);
		com.projecthero.mod.hero.AbilitySlot r = com.projecthero.mod.hero.AbilitySlot.SLOT_1;
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, r, true);
		helper.onEachTick(() -> Undead.tick(p));
		int[] arm = { -1 };
		java.util.function.Supplier<java.util.List<UndeadPartEntity>> thrown = () -> helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class,
				new AABB(p.blockPosition()).inflate(64), e -> e.ownerIs(p) && e.style() == UndeadPartEntity.STYLE_PUNCH && e.bodyPart() == arm[0]);
		// v0.17.3: it flies at one second of holding; a thrown piece now keeps flying until it lands, hits or gives out (60 blocks)
		int release = com.projecthero.mod.undead.UndeadBulletConfig.ARM_HOLD + 1;
		helper.runAfterDelay(release, () -> {
			com.projecthero.mod.undead.UndeadAbilityManager.handle(p, r, false);
			arm[0] = Undead.mainArm(p);
			helper.assertTrue(Undead.missing(p, arm[0]) && thrown.get().size() == 1, "the arm was thrown");
		});
		helper.runAfterDelay(release + UndeadConfig.REGROW_DELAY + 2, () -> helper.assertTrue(Undead.state(p).regrowStart(arm[0]) > 0L,
				"it is growing back (after the 1.5 s delay)"));
		helper.runAfterDelay(release + 5, () -> {
			java.util.List<UndeadPartEntity> a = thrown.get();
			helper.assertTrue(a.size() == 1, "and the thrown arm is still flying");
			helper.assertTrue(a.get(0).position().distanceTo(p.getEyePosition()) > 2.5, "away from the shoulder: " + a.get(0).position().distanceTo(p.getEyePosition()));
		});
		helper.runAfterDelay(release + UndeadConfig.REGROW_DELAY + UndeadConfig.FRAMES[UndeadState.RIGHT_ARM] + 4, () -> {
			helper.assertTrue(!Undead.missing(p, arm[0]), "the arm has grown back");
			helper.succeed(); // (by now the arm has flown past its 60-block range: UndeadBulletGameTests.bulletsGiveOutAt60Blocks)
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 300)
	public void holdVRipsAnArmIntoTheBoneSword(GameTestHelper helper) {
		// v0.17.3 (user: "make the bone sword hold V for 2 seconds"): the hold is the struggle; at its end the arm tears off
		ServerPlayer p = undead(helper);
		int main = Undead.mainArm(p), off = Undead.otherArm(main);
		double baseReach = p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		com.projecthero.mod.hero.AbilitySlot v = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, v, true);
		holdV(helper, p, UndeadConfig.BONE_SWORD_TAP + 3);
		helper.assertTrue(Undead.state(p).animId == UndeadState.ANIM_SWORD_STRUGGLE && !UndeadBoneSword.held(p) && !Undead.missing(p, off),
				"past a tap he struggles with the arm; it is still on");
		holdV(helper, p, UndeadConfig.BONE_SWORD_HOLD + 1);
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, v, false);
		helper.assertTrue(UndeadBoneSword.held(p), "held 2 s, V draws the sword");
		helper.assertTrue(Undead.missing(p, off) && !Undead.missing(p, main) && Undead.state(p).boneSwordArm() == off, "the off arm was torn off");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - UndeadConfig.BONE_SWORD_DAMAGE) < 0.01,
				"a sword's damage: " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - baseReach - UndeadConfig.BONE_SWORD_REACH_BONUS) < 0.01, "longer reach");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_SPEED) - UndeadConfig.BONE_SWORD_ATTACK_SPEED) < 0.01, "a sword's speed");
		helper.assertTrue(UndeadBoneSword.K_CRESCENT.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1)[0])
				&& UndeadBoneSword.K_WHIRL.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_6)[0])
				&& UndeadBoneSword.K_FLASH.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_4)[0]),
				"the keys are the katana's skills");
		helper.onEachTick(() -> Undead.tick(p));
		helper.runAfterDelay(30, () -> {
			UndeadState s = Undead.state(p);
			helper.assertTrue(s.broken(off) && s.regrowStart(off) == 0L, "the torn-off arm does not grow back while the sword is out");
			com.projecthero.mod.undead.UndeadAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_5, true);
			com.projecthero.mod.undead.UndeadAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_5, false);
			helper.assertTrue(!UndeadBoneSword.held(p), "V again puts it away");
			helper.assertTrue(Undead.cooldownRemaining(p, UndeadBoneSword.BONE_SWORD) > 0, "a short cooldown");
			helper.assertTrue(UndeadAbilities.PARTS_BULLET.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1)[0]),
					"the keys are his own again");
			helper.assertTrue(p.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).getModifiers().stream().noneMatch(m -> m.id().getPath().contains("bone_sword")),
					"the reach goes back");
		});
		helper.runAfterDelay(35, () -> {
			helper.assertTrue(Undead.state(p).regrowStart(off) > 0L, "then the arm regrows");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noSpareArmNoBoneSword(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Undead.breakPart(p, Undead.otherArm(Undead.mainArm(p)), UndeadState.KIND_BURST);
		helper.assertTrue(!UndeadBoneSword.draw(p) && !UndeadBoneSword.held(p), "one arm: nothing to tear off and hold");
		Undead.restore(p);
		Undead.breakPart(p, Undead.mainArm(p), UndeadState.KIND_BURST);
		helper.assertTrue(!UndeadBoneSword.draw(p), "no hand to hold it with");
		helper.succeed();
	}

	/** V held down for {@code ticks} game ticks' worth of the hold (the hold is timed off the game time: it is wound back). */
	private static void holdV(GameTestHelper helper, ServerPlayer p, int ticks) {
		com.projecthero.mod.undead.UndeadAbilityManager.forTestsHoldV(p, ticks);
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void lettingGoOfVEarlyLetsGoOfTheArm(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		int off = Undead.otherArm(Undead.mainArm(p));
		com.projecthero.mod.hero.AbilitySlot v = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, v, true);
		holdV(helper, p, UndeadConfig.BONE_SWORD_TAP + 10);
		helper.assertTrue(Undead.state(p).animId == UndeadState.ANIM_SWORD_STRUGGLE, "struggling");
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, v, false);
		helper.assertTrue(Undead.state(p).animId != UndeadState.ANIM_SWORD_STRUGGLE && !UndeadBoneSword.held(p) && !Undead.missing(p, off),
				"let go early: no sword, the arm stays on");
		helper.assertTrue(Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == 0, "and it was no tap: no Reform");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Shift + hold V: Reform (was tap V; tap V is the Rib Shotgun now)

	private static void feed(ServerPlayer p, int food, float saturation) {
		p.getFoodData().setFoodLevel(food);
		p.getFoodData().setSaturation(saturation);
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shiftHoldVReformFinishesEveryPartAndCleanses(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		feed(p, 20, 5f);
		long now = helper.getLevel().getGameTime();
		int off = Undead.otherArm(Undead.mainArm(p));
		Undead.breakPart(p, UndeadState.LEFT_LEG, UndeadState.KIND_BURST); // gone, not started
		Undead.breakPart(p, off, UndeadState.KIND_BURST);
		edit(p, n -> n.setTimes(off, Math.max(1L, now - 30), now - 5, now + 100)); // regrowing
		p.setHealth(10f);
		p.igniteForSeconds(8f);
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.POISON, 200, 1));
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.WITHER, 200, 1));
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
		com.projecthero.mod.hero.AbilitySlot v = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		p.setShiftKeyDown(true);
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, v, true);
		com.projecthero.mod.undead.UndeadAbilityManager.forTestsHoldShiftV(p, com.projecthero.mod.undead.UndeadKitConfig.REFORM_HOLD);
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, v, false);
		p.setShiftKeyDown(false);
		UndeadState s = Undead.state(p);
		for (int i = 0; i < UndeadState.PARTS; i++) {
			helper.assertTrue(!s.broken(i) && Math.abs(s.hp(i) - UndeadConfig.PART_HP[i]) < 0.01f, "part " + i + " is whole");
		}
		helper.assertTrue(s.animId == UndeadState.ANIM_REFORM, "the snap-together animation");
		helper.assertTrue(s.ex(UndeadState.X_REFORMED) == ((1L << UndeadState.LEFT_LEG) | (1L << off)), "the flash on the two parts that closed");
		helper.assertTrue(Math.abs(p.getHealth() - (10f + UndeadConfig.REFORM_HEAL)) < 0.01f, "healed 6: " + p.getHealth());
		helper.assertTrue(!p.isOnFire() && !p.hasEffect(MobEffects.POISON) && !p.hasEffect(MobEffects.WITHER), "fire, poison and wither gone");
		helper.assertTrue(p.hasEffect(MobEffects.DAMAGE_RESISTANCE), "a good effect stays");
		helper.assertTrue(Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == UndeadConfig.REFORM_COOLDOWN, "20s cooldown");
		helper.assertTrue(com.projecthero.mod.undead.UndeadAbilityManager.maxCooldown(UndeadAbilities.REFORM) == UndeadConfig.REFORM_COOLDOWN, "the HUD's cooldown");
		Undead.tick(p);
		helper.assertTrue(!Undead.state(p).broken(off) && !Undead.state(p).broken(UndeadState.LEFT_LEG), "and the tick leaves it whole");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void reformHasACooldownWorksWithRegenOffAndNotWithTheSword(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		feed(p, 20, 20f);
		Undead.breakPart(p, UndeadState.LEFT_LEG, UndeadState.KIND_BURST);
		helper.assertTrue(Undead.reform(p) && !Undead.missing(p, UndeadState.LEFT_LEG), "Reform closes it at once");
		Undead.breakPart(p, UndeadState.LEFT_LEG, UndeadState.KIND_BURST);
		helper.assertTrue(!Undead.reform(p) && Undead.missing(p, UndeadState.LEFT_LEG), "cooling down");
		edit(p, n -> n.abilityReadyAt.put(UndeadAbilities.REFORM, helper.getLevel().getGameTime()));
		helper.assertTrue(Undead.reform(p) && !Undead.missing(p, UndeadState.LEFT_LEG), "ready again");
		// the sword out: V puts it away and Reform does nothing
		edit(p, n -> n.abilityReadyAt.clear());
		helper.assertTrue(UndeadBoneSword.draw(p), "the sword is drawn");
		int off = Undead.otherArm(Undead.mainArm(p));
		helper.assertTrue(!Undead.reform(p) && Undead.missing(p, off), "Reform does not grow the sword arm back");
		com.projecthero.mod.hero.AbilitySlot v = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, v, true);
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, v, false);
		helper.assertTrue(!UndeadBoneSword.held(p) && Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == 0, "V puts the sword away, no Reform with it");
		helper.succeed();
	}

	private static ServerPlayer swordsman(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		helper.assertTrue(UndeadBoneSword.draw(p), "the sword is drawn");
		return p;
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBoneSwordHitsLikeASwordAndSweeps(GameTestHelper helper) {
		ServerPlayer p = swordsman(helper);
		Zombie z = zombie(helper, 2, 4);
		Zombie side = zombie(helper, 3, 4);
		z.getAttribute(Attributes.ARMOR).setBaseValue(0);
		try {
			java.lang.reflect.Field f = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker");
			f.setAccessible(true);
			f.setInt(p, 100);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		helper.assertTrue(p.getAttackStrengthScale(0.5f) > 0.99f, "a full swing");
		UndeadBoneSword.melee(p, z, p.getAttackStrengthScale(0.5f)); // what the attack hook does before the hit
		p.attack(z);
		float dealt = 200f - z.getHealth();
		helper.assertTrue(dealt >= UndeadConfig.BONE_SWORD_DAMAGE - 0.5f, "a sword hit: " + dealt);
		helper.assertTrue(side.getHealth() < 200f, "the sweep catches the one beside it");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theThrownSwordPins(GameTestHelper helper) {
		ServerPlayer p = swordsman(helper);
		Zombie z = zombie(helper, 2, 4);
		UndeadBoneSword.pin(p, z);
		helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "pinned");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void fingerAndHandBulletsFly(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		UndeadAbilities.partsBullet(p);
		AABB box = new AABB(p.blockPosition()).inflate(4);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, box, e -> e.style() == UndeadPartEntity.STYLE_FINGER).size() == 1,
				"a finger flies");
		UndeadAbilities.handBullet(p);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, box, e -> e.style() == UndeadPartEntity.STYLE_HAND).size() == 1,
				"a hand flies");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aOneArmMoveUsesTheOtherArmAndHeadBulletTheHead(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		int arm = Undead.mainArm(p);
		Undead.breakPart(p, arm, UndeadState.KIND_LAUNCHED);
		helper.assertTrue(Undead.missing(p, arm) && Undead.state(p).kind(arm) == UndeadState.KIND_LAUNCHED, "the arm is gone, thrown off");
		AABB box = new AABB(p.blockPosition()).inflate(4);
		UndeadAbilities.partsBullet(p);
		// v0.17.3: a one-arm move falls back to the other arm -- the finger comes from the hand he still has
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, box, e -> e.isFinger()).size() == 1, "the other hand shoots instead");
		helper.assertTrue(Undead.shootingArm(p) == (arm == UndeadState.RIGHT_ARM ? UndeadState.LEFT_ARM : UndeadState.RIGHT_ARM), "from the other arm");
		UndeadAbilities.headBullet(p);
		helper.assertTrue(Undead.missing(p, UndeadState.HEAD), "the head is gone");
		Undead.reconcile(p);
		helper.assertTrue(p.hasEffect(MobEffects.BLINDNESS), "and he is blind");
		helper.assertTrue(com.projecthero.mod.undead.UndeadMod.handGone(p, InteractionHand.MAIN_HAND), "the missing hand can't be used");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void greatCrescentWavesRun(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Zombie z = zombie(helper, 2, 5);
		p.moveTo(helper.absoluteVec(new Vec3(2.5, 2.0, 1.5)));
		UndeadAbilities.greatCrescent(p);
		helper.onEachTick(() -> Undead.tick(p));
		helper.runAfterDelay(14, () -> {
			helper.assertTrue(z.getHealth() < 200f, "the blood waves reach the zombie");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void victorVulcanAndDivisionFire(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.setXRot(-85f); // up, out of the walled test area
		UndeadAbilities.pullCard(p);
		UndeadAbilities.vulcan(p);
		UndeadAbilities.division(p);
		AABB box = new AABB(p.blockPosition()).inflate(6);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, box, e -> e.style() == UndeadPartEntity.STYLE_DIVISION).size() == 1,
				"a Division finger (v0.17.3: one, the Shift+C move)");
		UndeadAbilities.divisionClone(p, p.position());
		helper.assertTrue(!helper.getLevel().getEntitiesOfClass(UndeadCloneEntity.class, box, e -> true).isEmpty(), "a finger grows into a copy");
		helper.onEachTick(() -> Undead.tick(p));
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(UndeadAbilities.vulcanLeft(p) > 0 && UndeadAbilities.vulcanLeft(p) <= UndeadConfig.VULCAN_SHOTS - 2, "Dead Vulcan keeps firing: " + UndeadAbilities.vulcanLeft(p));
			UndeadAbilities.deadRoad(p);
			helper.assertTrue(Undead.state(p).flag(UndeadState.FLAG_DEAD_ROAD), "Dead Road fires");
			helper.getLevel().getEntitiesOfClass(UndeadCloneEntity.class, box, e -> true).forEach(e -> e.discard());
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the Regen Menu's Eject Head (the old Head Launch; the X moves: UndeadEjectGameTests)

	private static final com.projecthero.mod.hero.AbilitySlot X = com.projecthero.mod.hero.AbilitySlot.SLOT_3;

	private static java.util.List<UndeadPartEntity> leftBehind(GameTestHelper helper, ServerPlayer p) {
		return helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, new AABB(p.blockPosition()).inflate(8), e -> e.isLeftBehind() && e.ownerIs(p));
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void headLaunchGivesTheHeadFlight(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		com.projecthero.mod.undead.UndeadLaunch.headLaunch(p);
		Vec3 v = p.getDeltaMovement();
		helper.assertTrue(v.z > 2.0 && Math.abs(v.y) < 0.05, "the head dashes straight along the aim (level look: no arc): " + v);
		helper.assertTrue(com.projecthero.mod.undead.UndeadLaunch.flying(p), "the launch runs");
		leftBehind(helper, p).forEach(e -> e.discard());
		helper.succeed();
	}

}
