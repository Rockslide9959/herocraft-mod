package com.projecthero.mod.gametest;


import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.greymatter.GreyMatterKit;
import com.projecthero.mod.omnitrix.alien.ripjaws.RipjawsKit;
import com.projecthero.mod.omnitrix.alien.stinkfly.StinkflyKit;
import com.projecthero.mod.omnitrix.alien.wildmutt.WildmuttKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 the Omnitrix's second batch of aliens -- Stinkfly, Ripjaws and Grey Matter (Wildvine was replaced by Wildmutt: OmnitrixWildmuttGameTests): their bodies (Grey Matter's
 * third-size hitbox and eye height, and that they come back), every move's damage or effect and its cooldown, Stinkfly's flight
 * on / off, Ripjaws' dry-out timer and water bonus, Grey Matter's mark, and that turning back
 * (early or by the timeout) leaves no flight, scale, speed or water buff behind. Mock players are not ticked by the server, so
 * tests over time drive {@link Omnitrix#tick} themselves.
 */
public class OmnitrixAliens2GameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	private static ServerPlayer as(GameTestHelper helper, String alien) {
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		Omnitrix.select(p, alien);
		helper.assertTrue(Omnitrix.transform(p), "transformed into " + alien);
		p.setOnGround(true);
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
	}

	/**
	 * What {@code LivingEntity.tick} does the tick after a scale change (a mock player is never ticked): {@code getScale() !=
	 * appliedScale} refreshes the hitbox and the eye height.
	 */
	private static void refreshAttributes(ServerPlayer p) {
		p.refreshDimensions();
	}

	private static Zombie zombie(GameTestHelper helper, double x, double y, double z, float health) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth(health);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0);
		m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static boolean rooted(LivingEntity e) {
		var s = e.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		return s != null && s.getAmplifier() >= 9;
	}

	private static void near(GameTestHelper helper, float actual, float expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < 0.05f, what + " (expected " + expected + ", got " + actual + ")");
	}

	// ================================================================ Stinkfly

	@GameTest(template = EMPTY_STRUCTURE)
	public void stinkflyFliesAndNeverTakesFallDamage(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, StinkflyKit.ID);
		StinkflyKit kit = (StinkflyKit) Aliens.kit(StinkflyKit.ID);
		near(helper, p.getMaxHealth(), 14f, "14 health");
		near(helper, p.getScale(), 0.9f, "0.9x the size");
		helper.assertTrue(p.getAbilities().mayfly, "the wings are ready (a double-tap of jump flies)");
		helper.assertTrue(kit.wingFlight(p) && p.getAbilities().flying && kit.flag(p, StinkflyKit.F_FLYING), "X takes off");
		helper.assertFalse(kit.wingFlight(p), "X has a short cooldown");
		var n = Omnitrix.state(p).copy();
		n.timers.remove(kit.key(StinkflyKit.FLIGHT));
		Omnitrix.save(p, n);
		helper.assertTrue(kit.wingFlight(p) && !p.getAbilities().flying && !kit.flag(p, StinkflyKit.F_FLYING), "X again lands");
		float hp = p.getHealth();
		p.hurt(helper.getLevel().damageSources().fall(), 12f);
		near(helper, p.getHealth(), hp, "no fall damage");
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().magic(), 3f);
		helper.assertTrue(p.getHealth() < hp, "other damage still hurts");
		kit.wingFlight(p);
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.assertFalse(Omnitrix.state(p).transformed(), "turned back");
		helper.assertFalse(p.getAbilities().mayfly, "turning back takes the wings away (mayfly, creative=" + p.isCreative() + ")");
		helper.assertFalse(p.getAbilities().flying, "turning back lands him");
		helper.assertTrue(Math.abs(p.getAbilities().getFlyingSpeed() - 0.05f) < 1.0e-4f, "normal flying speed again");
		helper.succeed();
	}

	/**
	 * v0.17.2 (user: "make stinky able to use items and tools"): his claws hold and use items -- a held item is used as normal,
	 * and the Use goo spit fires from an empty hand.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void stinkflyUsesItemsAndSpitsBareHanded(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, StinkflyKit.ID);
		StinkflyKit kit = (StinkflyKit) Aliens.kit(StinkflyKit.ID);
		helper.assertFalse(AlienKit.pawsOnlyNow(p), "his claws hold items");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
		helper.assertTrue(kit.onUse(p, InteractionHand.MAIN_HAND) == net.minecraft.world.InteractionResult.PASS, "a held item is used as normal");
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.assertTrue(kit.onUse(p, InteractionHand.MAIN_HAND).consumesAction(), "the goo spit fires from an empty hand");
		helper.assertTrue(Omnitrix.state(p).animId == StinkflyKit.ANIM_SHOT, "and plays the spit");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void stinkflysMovesHitAndCoolDown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, StinkflyKit.ID);
		StinkflyKit kit = (StinkflyKit) Aliens.kit(StinkflyKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 4.0, 140f); // v0.17.2: was 80 -- the rebalanced kit would kill an 80-health zombie before the dive (under 150: a boss is rooted for a quarter)
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 10f);
		helper.assertTrue(AlienKit.lookTarget(p, 3.75) == z, "the zombie is in reach");
		helper.assertTrue(kit.stingerStab(p), "Stinger Stab");
		near(helper, z.getHealth(), 140f - StinkflyKit.STAB_DAMAGE, "the stab hits for 12");
		// (no poison check: a zombie is undead and shrugs poison off)
		helper.assertFalse(kit.stingerStab(p), "the stab cools down");
		helper.assertTrue(kit.slimeShot(p) && !kit.slimeShot(p), "Slime Shot fires, then cools down");
		helper.assertTrue(kit.wingGust(p) && !kit.wingGust(p), "Wing Gust, then cools down");
		helper.assertTrue(kit.toxicGas(p) && !kit.toxicGas(p), "Toxic Gas, then cools down");
		helper.assertTrue(kit.slimeNet(p) && !kit.slimeNet(p), "Slime Net, then cools down");
		helper.assertTrue(kit.slimeBarrage(p) && !kit.slimeBarrage(p), "Slime Barrage, then cools down");
		helper.assertTrue(kit.gooStorm(p) && !kit.gooStorm(p), "Goo Storm, then cools down");
		helper.assertTrue(kit.wingDash(p) && !kit.wingDash(p), "Wing Dash, then cools down");
		float afterInstant = z.getHealth();
		helper.assertTrue(afterInstant <= 140f - StinkflyKit.STAB_DAMAGE - StinkflyKit.GUST_DAMAGE + 0.05f, "the gust hit too");
		pump(helper, p);
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(rooted(z), "the slime stuck the zombie in place: " + z.getActiveEffects() + " at " + z.position().subtract(p.position()) + " hp " + z.getHealth() + " alive " + z.isAlive());
			helper.assertTrue(z.getHealth() < afterInstant - StinkflyKit.SHOT_DAMAGE + 0.05f, "the goo and the gas hurt it");
			// the dive: airborne, plunge, splash down beside it
			p.setOnGround(false);
			p.moveTo(z.getX(), z.getY(), z.getZ() - 1.5, 0f, 60f);
			helper.assertTrue(kit.diveBomb(p) && kit.flag(p, StinkflyKit.F_DIVING), "Dive Bomb plunges");
			helper.assertFalse(kit.diveBomb(p), "the dive cools down");
			float before = z.getHealth();
			z.invulnerableTime = 0;
			p.setOnGround(true);
			helper.runAfterDelay(2, () -> {
				helper.assertFalse(kit.flag(p, StinkflyKit.F_DIVING), "it splashed down");
				helper.assertTrue(z.getHealth() < before, "the splash hit the zombie");
				z.discard();
				helper.succeed();
			});
		});
	}

	// ================================================================ Ripjaws

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void ripjawsDriesOutOnLandAndRefillsInWater(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, RipjawsKit.ID);
		RipjawsKit kit = (RipjawsKit) Aliens.kit(RipjawsKit.ID);
		near(helper, p.getMaxHealth(), 30f, "30 health");
		long now = helper.getLevel().getGameTime();
		helper.assertTrue(RipjawsKit.dryness(p, now) == 0, "starts soaked");
		float hp = p.getHealth();
		p.hurt(helper.getLevel().damageSources().drown(), 4f);
		near(helper, p.getHealth(), hp, "he breathes water: no drowning");
		near(helper, kit.damage(p, 10f), 10f, "on land a move does its base damage");
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) < 1 + 3 + RipjawsKit.WET_FIST - 0.5, "no wet-fist bonus on land");
		// two minutes on land, then he chokes
		kit.setDryness(p, RipjawsKit.MAX_DRY - 1, false, false);
		pump(helper, p);
		helper.runAfterDelay(RipjawsKit.CHOKE_EVERY + 4, () -> {
			helper.assertTrue(RipjawsKit.dryness(p, helper.getLevel().getGameTime()) == RipjawsKit.MAX_DRY,
					"the meter is empty");
			helper.assertTrue(kit.flag(p, RipjawsKit.F_CHOKING), "choking");
			helper.assertTrue(p.getHealth() < hp, "drying out hurts");
			// into the water: the meter refills, the bonus is on
			BlockPos feet = p.blockPosition();
			helper.getLevel().setBlock(feet, Blocks.WATER.defaultBlockState(), 3);
			helper.getLevel().setBlock(feet.above(), Blocks.WATER.defaultBlockState(), 3);
			helper.runAfterDelay(20, () -> {
				int d = RipjawsKit.dryness(p, helper.getLevel().getGameTime());
				helper.assertTrue(kit.flag(p, RipjawsKit.F_WET), "wet");
				helper.assertTrue(d < RipjawsKit.MAX_DRY - 10 * RipjawsKit.REFILL_WATER, "the water refills the meter fast (" + d + ")");
				helper.assertFalse(kit.flag(p, RipjawsKit.F_CHOKING), "no longer choking");
				near(helper, kit.damage(p, 10f), 10f * RipjawsKit.WATER_BONUS, "x1.25 damage in water");
				helper.assertFalse(p.hasEffect(MobEffects.WEAKNESS), "getting wet clears the choking's weakness");
				helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) >= 1 + 3 + RipjawsKit.WET_FIST - 1.0e-6, "and a heavier fist");
				helper.assertTrue(p.hasEffect(MobEffects.DOLPHINS_GRACE) && p.hasEffect(MobEffects.CONDUIT_POWER), "fast and clear-eyed in water");
				// turning back strips the water buffs
				Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
				helper.assertTrue(!p.hasEffect(MobEffects.DOLPHINS_GRACE) && !p.hasEffect(MobEffects.CONDUIT_POWER), "no water buffs left behind");
				helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) < 1.01 && p.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY) < 0.01,
						"no wet-fist or swim modifiers left behind");
				helper.assertTrue(Omnitrix.state(p).readyAt(kit.key("dry_base")) == 0, "the meter is gone");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void ripjawsMovesHitAndCoolDown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, RipjawsKit.ID);
		RipjawsKit kit = (RipjawsKit) Aliens.kit(RipjawsKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 4.0, 100f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 10f);
		p.setHealth(20f);
		helper.assertTrue(kit.bite(p), "Bite");
		near(helper, z.getHealth(), 100f - RipjawsKit.BITE_DAMAGE, "12 damage on land");
		near(helper, p.getHealth(), 21f, "a bite heals 1");
		helper.assertFalse(kit.bite(p), "the bite cools down");
		float b = z.getHealth();
		helper.assertTrue(kit.tailSlam(p), "Tail Slam");
		near(helper, z.getHealth(), b - RipjawsKit.SLAM_DAMAGE, "12 damage");
		helper.assertFalse(kit.tailSlam(p), "the slam cools down");
		z.moveTo(p.getX(), p.getY(), p.getZ() + 2.0, 180f, 0f);
		b = z.getHealth();
		helper.assertTrue(kit.finSlash(p), "Fin Slash");
		near(helper, z.getHealth(), b - RipjawsKit.SLASH_DAMAGE, "10 damage");
		helper.assertFalse(kit.finSlash(p), "the slash cools down");
		b = z.getHealth();
		helper.assertTrue(kit.whirlpool(p), "Z on land: Mud Splash");
		near(helper, z.getHealth(), b - RipjawsKit.MUD_DAMAGE, "3 damage");
		helper.assertTrue(z.hasEffect(MobEffects.BLINDNESS), "and blinds");
		helper.assertFalse(kit.whirlpool(p), "it cools down");
		helper.assertTrue(kit.tidalRoar(p) && z.hasEffect(MobEffects.WEAKNESS) && !kit.tidalRoar(p), "Tidal Roar weakens, then cools down");
		helper.assertTrue(kit.waterDash(p) && !kit.waterDash(p), "Water Dash, then cools down");
		helper.assertTrue(kit.breach(p) && !kit.breach(p), "Breach, then cools down");
		helper.assertTrue(kit.feedingFrenzy(p) && kit.flag(p, RipjawsKit.F_FRENZY), "Feeding Frenzy");
		near(helper, kit.damage(p, 10f), 10f * RipjawsKit.FRENZY_BONUS, "+30% in a frenzy");
		helper.assertFalse(kit.feedingFrenzy(p), "the frenzy cools down");
		kit.setDryness(p, RipjawsKit.MAX_DRY, false, false);
		helper.assertTrue(kit.drench(p), "Drench");
		helper.assertTrue(Math.abs(RipjawsKit.dryness(p, helper.getLevel().getGameTime()) - RipjawsKit.MAX_DRY / 2) <= 2, "half the meter back");
		helper.assertFalse(kit.drench(p), "it cools down");
		// dig
		BlockPos sand = helper.absolutePos(new BlockPos(6, 2, 6));
		helper.getLevel().setBlock(sand, Blocks.SAND.defaultBlockState(), 3);
		helper.assertTrue(kit.dig(p, sand) && helper.getLevel().getBlockState(sand).isAir(), "dug through the sand");
		helper.getLevel().setBlock(sand, Blocks.STONE.defaultBlockState(), 3);
		var n = Omnitrix.state(p).copy();
		n.timers.remove(kit.key(RipjawsKit.DIG));
		Omnitrix.save(p, n);
		helper.assertFalse(kit.dig(p, sand), "not through stone");
		// the lunge: the first creature in the way
		z.moveTo(p.getX(), p.getY(), p.getZ() + 1.4, 180f, 0f);
		z.invulnerableTime = 0;
		float before = z.getHealth();
		helper.assertTrue(kit.jawLunge(p) && !kit.jawLunge(p), "Jaw Lunge, then cools down");
		pump(helper, p);
		helper.runAfterDelay(2, () -> {
			near(helper, z.getHealth(), before - RipjawsKit.LUNGE_DAMAGE * RipjawsKit.FRENZY_BONUS, "the lunge bit for 16 (+30% frenzy)");
			Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - p.getAttributeBaseValue(Attributes.MOVEMENT_SPEED)) < 1.0e-6,
					"the frenzy's speed is gone");
			z.discard();
			helper.succeed();
		});
	}

	// ================================================================ Grey Matter

	@GameTest(template = EMPTY_STRUCTURE)
	public void greyMatterIsAThirdTheSizeAndComesBack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		Omnitrix.grant(p);
		float h0 = p.getBbHeight();
		float e0 = p.getEyeHeight();
		float w0 = p.getBbWidth();
		Omnitrix.select(p, GreyMatterKit.ID);
		helper.assertTrue(Omnitrix.transform(p), "Grey Matter");
		refreshAttributes(p); // what the next server tick does: the scale change refreshes the hitbox and the eyes
		near(helper, p.getScale(), GreyMatterKit.SCALE, "a third of the size");
		near(helper, p.getBbHeight(), h0 * GreyMatterKit.SCALE, "a third of the height (fits a one-block gap)");
		helper.assertTrue(p.getBbHeight() < 1.0f, "under one block tall");
		near(helper, p.getBbWidth(), w0 * GreyMatterKit.SCALE, "a third of the width");
		near(helper, p.getEyeHeight(), e0 * GreyMatterKit.SCALE, "eyes a third as high");
		near(helper, p.getMaxHealth(), 8f, "8 health");
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > p.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) * 1.3, "fast");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		refreshAttributes(p);
		near(helper, p.getScale(), 1f, "full size again");
		near(helper, p.getBbHeight(), h0, "the hitbox comes back");
		near(helper, p.getEyeHeight(), e0, "the eye height comes back");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void greyMattersMovesHitAndCoolDown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, GreyMatterKit.ID);
		GreyMatterKit kit = (GreyMatterKit) Aliens.kit(GreyMatterKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 3.5, 100f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		helper.assertTrue(AlienKit.lookTarget(p, 3.0) == z, "the zombie is in reach of something so small");
		helper.assertTrue(kit.nervePinch(p) && rooted(z) && z.hasEffect(MobEffects.WEAKNESS), "Nerve Pinch stuns");
		near(helper, z.getHealth(), 100f - GreyMatterKit.PINCH_DAMAGE, "a pinch (4)");
		helper.assertFalse(kit.nervePinch(p), "the pinch cools down");
		helper.assertTrue(kit.weakSpot(p), "Weak-Spot Strike");
		near(helper, z.getHealth(), 100f - GreyMatterKit.PINCH_DAMAGE - GreyMatterKit.STRIKE_DAMAGE, "a crit for 18");
		helper.assertFalse(kit.weakSpot(p), "the strike cools down");
		helper.assertTrue(kit.analyze(p) && GreyMatterKit.marked(z) && z.hasEffect(MobEffects.GLOWING), "Analyze marks it");
		helper.assertFalse(kit.analyze(p), "Analyze cools down");
		float b = z.getHealth();
		z.invulnerableTime = 0;
		z.hurt(helper.getLevel().damageSources().magic(), 10f);
		near(helper, z.getHealth(), b - 10f * GreyMatterKit.MARK_FACTOR, "a marked creature takes 35% more from anyone");
		// v0.17.2: on a marked creature the Weak-Spot Strike comes to 24 once the mark's x1.35 is on it
		near(helper, GreyMatterKit.strikeDamage(z) * GreyMatterKit.MARK_FACTOR, GreyMatterKit.STRIKE_MARKED_DAMAGE, "a marked weak spot takes 24");
		helper.assertTrue(kit.decoy(p) && !kit.decoy(p), "Decoy, then cools down");
		float bs = z.getHealth();
		z.invulnerableTime = 0;
		helper.assertTrue(kit.smokeBomb(p) && z.hasEffect(MobEffects.BLINDNESS) && !kit.smokeBomb(p), "Smoke Charge blinds, then cools down");
		// v0.17.2: Smoke Charge blasts for 22 (x1.35 on the marked zombie)
		near(helper, z.getHealth(), bs - GreyMatterKit.SMOKE_DAMAGE * GreyMatterKit.MARK_FACTOR, "the smoke charge blasts it for 22 (+35% marked)");
		helper.assertTrue(kit.scurry(p) && kit.flag(p, GreyMatterKit.F_SCURRY) && !kit.scurry(p), "Scurry, then cools down");
		helper.assertTrue(kit.bigLeap(p) && kit.flag(p, GreyMatterKit.F_LEAP) && !kit.bigLeap(p), "Big Leap, then cools down");
		float hp = p.getHealth();
		p.hurt(helper.getLevel().damageSources().fall(), 5f);
		near(helper, p.getHealth(), hp, "no fall damage from his own leap");
		helper.assertTrue(kit.mindMap(p) && !kit.mindMap(p), "Mind Map, then cools down");
		// genius repair: a damaged sword, then (after the cooldown) a chipped anvil
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.setDamageValue(200);
		p.setItemInHand(InteractionHand.MAIN_HAND, sword);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 180f, 0f); // looking at nothing to fix
		helper.assertTrue(kit.geniusRepair(p), "Genius Repair");
		helper.assertTrue(p.getMainHandItem().getDamageValue() < 200 - 70, "the sword is mended (" + p.getMainHandItem().getDamageValue() + ")");
		helper.assertFalse(kit.geniusRepair(p), "repair cools down");
		BlockPos anvil = helper.absolutePos(new BlockPos(6, 2, 6));
		helper.getLevel().setBlock(anvil, Blocks.CHIPPED_ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, net.minecraft.core.Direction.EAST), 3);
		helper.assertTrue(kit.fixBlock(p, helper.getLevel(), anvil) && helper.getLevel().getBlockState(anvil).is(Blocks.ANVIL), "the anvil is good as new");
		// a snare: the zombie steps on it
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		helper.assertTrue(kit.snare(p) && GreyMatterKit.traps(p) == 1, "a snare on the ground");
		helper.assertFalse(kit.snare(p), "the snare cools down");
		z.removeAllEffects();
		Vec3 trap = p.position().add(0, 0, 1.2);
		z.moveTo(trap.x, p.getY(), trap.z, 180f, 0f);
		pump(helper, p);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(GreyMatterKit.traps(p) == 0 && rooted(z), "the snare caught it");
			Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
			helper.assertFalse(GreyMatterKit.marked(z), "turning back clears his marks");
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - p.getAttributeBaseValue(Attributes.MOVEMENT_SPEED)) < 1.0e-6,
					"no scurry speed left behind");
			z.discard();
			helper.succeed();
		});
	}

	// ================================================================ the timeout cleans up too

	@GameTest(template = EMPTY_STRUCTURE)
	public void theTimeoutLeavesNoFlightScaleOrBuffs(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, StinkflyKit.ID);
		((StinkflyKit) Aliens.kit(StinkflyKit.ID)).wingFlight(p);
		timeOut(helper, p);
		helper.assertTrue(!p.getAbilities().mayfly && !p.getAbilities().flying, "Stinkfly timed out: no flight left");
		rearm(p, GreyMatterKit.ID);
		((GreyMatterKit) Aliens.kit(GreyMatterKit.ID)).scurry(p);
		timeOut(helper, p);
		near(helper, p.getScale(), 1f, "Grey Matter timed out: full size");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - p.getAttributeBaseValue(Attributes.MOVEMENT_SPEED)) < 1.0e-6, "normal speed");
		rearm(p, RipjawsKit.ID);
		BlockPos feet = p.blockPosition();
		helper.getLevel().setBlock(feet, Blocks.WATER.defaultBlockState(), 3);
		p.tickCount = 19;
		Omnitrix.tick(p);
		p.tickCount = 20;
		Omnitrix.tick(p);
		helper.assertTrue(p.hasEffect(MobEffects.DOLPHINS_GRACE), "Ripjaws swims fast");
		timeOut(helper, p);
		helper.assertTrue(!p.hasEffect(MobEffects.DOLPHINS_GRACE) && !p.hasEffect(MobEffects.CONDUIT_POWER), "Ripjaws timed out: no water buffs");
		helper.getLevel().setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
		rearm(p, WildmuttKit.ID); // v0.17.1: Wildmutt took Wildvine's slot
		WildmuttKit wm = (WildmuttKit) Aliens.kit(WildmuttKit.ID);
		wm.frenzy(p);
		timeOut(helper, p);
		helper.assertTrue(Omnitrix.state(p).kitFlags == 0 && Omnitrix.state(p).readyAt(wm.key("frenzy_end")) == 0, "Wildmutt timed out: nothing running");
		near(helper, (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE), (float) p.getAttributeBaseValue(Attributes.ATTACK_DAMAGE), "no frenzy claws left");
		helper.succeed();
	}

	private static void timeOut(GameTestHelper helper, ServerPlayer p) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.endsAt = helper.getLevel().getGameTime();
		Omnitrix.save(p, n);
		Omnitrix.tick(p);
		// v0.17.1: the timeout starts the five-beep power-down; jump to its revert tick
		helper.assertTrue(Omnitrix.poweringDown(p), "the power-down started");
		OmnitrixState m = Omnitrix.state(p).copy();
		m.timers.put(Omnitrix.POWER_DOWN_TIMER, helper.getLevel().getGameTime() - Omnitrix.POWER_DOWN_REVERT_TICK);
		Omnitrix.save(p, m);
		Omnitrix.tick(p);
		helper.assertFalse(Omnitrix.state(p).transformed(), "timed out");
	}

	private static void rearm(ServerPlayer p, String alien) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.rechargeUntil = 0;
		Omnitrix.save(p, n);
		Omnitrix.select(p, alien);
		Omnitrix.transform(p);
		p.setOnGround(true);
	}
}
