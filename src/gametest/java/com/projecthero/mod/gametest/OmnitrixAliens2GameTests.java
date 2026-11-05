package com.projecthero.mod.gametest;


import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.greymatter.GreyMatterKit;
import com.projecthero.mod.omnitrix.alien.greymatter.GreyMatterMechEntity;
import com.projecthero.mod.omnitrix.alien.greymatter.GreyMatterSentryEntity;
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
import net.minecraft.world.entity.npc.Villager;
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

	/**
	 * v0.17.2 (Ripjaws revamp): a no-AI villager to bite -- not a hostile, so a neighbouring test's 50-block auto-sweep (XLR8's
	 * Speed Sweep hits every hostile in range for 26) never lands on it and spoils an exact damage count.
	 */
	private static net.minecraft.world.entity.npc.Villager prey(GameTestHelper helper, double x, double y, double z, float health) {
		net.minecraft.world.entity.npc.Villager m = EntityType.VILLAGER.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth(health);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0);
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
		near(helper, p.getMaxHealth(), 20f, "20 health");
		near(helper, p.getScale(), 0.9f, "0.9x the size");
		helper.assertTrue(p.getAbilities().mayfly, "the wings are ready (a double-tap of jump flies)");
		// v0.17.2 (user: "if the player transforms into stinkfly midair i want the player to automatically start flying"): on the
		// ground he stays down; in the air the transformation takes off by itself
		p.getAbilities().flying = false;
		kit.setFlag(p, StinkflyKit.F_FLYING, false);
		p.setOnGround(true);
		kit.onTransform(p);
		helper.assertFalse(p.getAbilities().flying, "transforming on the ground: still on the ground");
		p.setOnGround(false);
		kit.onTransform(p);
		helper.assertTrue(p.getAbilities().flying && kit.flag(p, StinkflyKit.F_FLYING), "transforming in mid-air: flying at once");
		p.getAbilities().flying = false;
		kit.setFlag(p, StinkflyKit.F_FLYING, false);
		p.onUpdateAbilities();
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

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void stinkflysXIsWingDashAndShiftXIsAStingerLunge(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, StinkflyKit.ID);
		StinkflyKit kit = (StinkflyKit) Aliens.kit(StinkflyKit.ID);
		helper.assertTrue(kit.moves().stream().anyMatch(m -> m.id().equals(StinkflyKit.WING_DASH) && m.slot() == com.projecthero.mod.hero.AbilitySlot.SLOT_3 && !m.shift()),
				"X is Wing Dash");
		helper.assertTrue(kit.moves().stream().anyMatch(m -> m.id().equals(StinkflyKit.LUNGE) && m.slot() == com.projecthero.mod.hero.AbilitySlot.SLOT_3 && m.shift()),
				"Shift+X is Stinger Lunge");
		// two zombies in a line ahead, one off to the side
		Zombie first = zombie(helper, 3.5, 2, 3.5, 140f);
		Zombie far = zombie(helper, 3.5, 2, 6.0, 140f);
		Zombie side = zombie(helper, 6.8, 2, 4.5, 140f);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2, 1.0));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		helper.assertTrue(kit.stingerLunge(p), "Stinger Lunge");
		near(helper, first.getHealth(), 140f - StinkflyKit.LUNGE_DAMAGE, "the first in the path takes 14");
		near(helper, far.getHealth(), 140f - StinkflyKit.LUNGE_DAMAGE, "so does the one behind it");
		near(helper, side.getHealth(), 140f, "the one off to the side is missed");
		helper.assertTrue(p.getDeltaMovement().horizontalDistance() > 1.0, "he darts forward");
		helper.assertFalse(kit.stingerLunge(p), "it cools down");
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
		helper.assertTrue(kit.gooRain(p) && !kit.gooRain(p), "Goo Rain, then cools down"); // v0.17.2: was Slime Barrage / Goo Storm
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

	// ---------------------------------------------------------------- v0.17.2 the Stinkfly revamp (docs/OMNITRIX_REVAMP_STINKFLY.md)

	@GameTest(template = EMPTY_STRUCTURE)
	public void stinkflyRevampKeys(GameTestHelper helper) {
		StinkflyKit kit = (StinkflyKit) Aliens.kit(StinkflyKit.ID);
		helper.assertTrue(kit.moveOn(AbilitySlot.SLOT_4, true, false).id().equals(StinkflyKit.GOO_COCOON), "Shift+Z is the Goo Cocoon ultimate");
		helper.assertTrue(kit.moveOn(AbilitySlot.SLOT_6, true, false).id().equals(StinkflyKit.GOO_RAIN), "Shift+C is Goo Rain");
		helper.assertTrue(kit.moveOn(AbilitySlot.SLOT_5, true, false).id().equals(StinkflyKit.GOO_WALL), "Shift+V is the Goo Wall");
		helper.assertTrue(kit.moveOn(AbilitySlot.SLOT_5, false, false).id().equals(StinkflyKit.LIFT), "V is still Lift");
		helper.assertTrue(kit.move("goo_storm") == null && kit.move("slime_barrage") == null, "Goo Storm and Slime Barrage are gone");
		helper.assertTrue(kit.moves().stream().noneMatch(m -> m.slot() == AbilitySlot.SLOT_7 || m.slot() == AbilitySlot.SLOT_8), "nothing on H / N");
		helper.succeed();
	}

	/** A missed Slime Shot leaves a goo patch: a creature in it is Gooed, and fire burns the patch away. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void stinkflyMissedShotLeavesAPatchThatBurns(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, StinkflyKit.ID);
		StinkflyKit kit = (StinkflyKit) Aliens.kit(StinkflyKit.ID);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 60f);
		helper.assertTrue(kit.slimeShot(p), "Slime Shot at the floor ahead");
		pump(helper, p);
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(StinkflyKit.patches(p) == 1, "a goo patch where it hit: " + StinkflyKit.patches(p));
			Vec3 ahead = p.position().add(0, 0, 1.0);
			Zombie z = zombie(helper, 3.5, 2, 1.5, 60f);
			// find the patch: walk the zombie forward until it stands in it
			for (double d = 0.3; d < 4 && !StinkflyKit.inGoo(p, z); d += 0.25) {
				z.moveTo(p.getX(), p.getY(), p.getZ() + d, 0f, 0f);
			}
			helper.assertTrue(StinkflyKit.inGoo(p, z), "the zombie stands in the goo (" + ahead + ")");
			helper.runAfterDelay(4, () -> {
				helper.assertTrue(z.hasEffect(com.projecthero.mod.omnitrix.alien.stinkfly.StinkflyGooEffect.HOLDER), "it is Gooed");
				helper.assertTrue(z.getAttributeValue(Attributes.JUMP_STRENGTH) < 0.01, "Gooed: no jumping");
				z.igniteForSeconds(5);
				helper.runAfterDelay(4, () -> {
					helper.assertTrue(StinkflyKit.patches(p) == 0, "fire burnt the patch away");
					z.discard();
					helper.succeed();
				});
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 220)
	public void stinkflyGooCocoonWrapsAndBursts(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, StinkflyKit.ID);
		StinkflyKit kit = (StinkflyKit) Aliens.kit(StinkflyKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 6.5, 140f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 8f);
		helper.assertTrue(kit.gooCocoon(p) && kit.flag(p, StinkflyKit.F_COCOON), "Goo Cocoon: the stream starts");
		helper.assertTrue(kit.cocoonTarget(p) == z.getId(), "pumped at the zombie (the client draws the stream to it)");
		helper.assertFalse(kit.gooCocoon(p), "it cools down");
		pump(helper, p);
		helper.runAfterDelay(StinkflyKit.COCOON_STREAM + 3, () -> {
			helper.assertTrue(StinkflyKit.cocooned(z) && rooted(z), "the zombie is cocooned and can't move");
			helper.assertFalse(kit.flag(p, StinkflyKit.F_COCOON), "the stream ended");
			float before = z.getHealth();
			z.invulnerableTime = 0;
			z.hurt(helper.getLevel().damageSources().magic(), 8f);
			near(helper, z.getHealth(), before - 8f * StinkflyKit.COCOON_TAKEN, "a cocooned creature takes 25% more");
			float afterHit = z.getHealth();
			helper.runAfterDelay(StinkflyKit.COCOON_TICKS + 4, () -> {
				helper.assertFalse(StinkflyKit.cocooned(z), "the cocoon burst");
				helper.assertTrue(z.getHealth() <= afterHit - StinkflyKit.COCOON_BURST + 0.05f, "the burst's gas puff hurt it: " + z.getHealth());
				z.discard();
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void stinkflyGooWallCatchesArrows(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, StinkflyKit.ID);
		StinkflyKit kit = (StinkflyKit) Aliens.kit(StinkflyKit.ID);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		helper.assertTrue(kit.gooWall(p), "Goo Wall");
		Vec3 w = StinkflyKit.wallAt(p);
		helper.assertTrue(w != null && StinkflyKit.wallHp(p) == StinkflyKit.WALL_HP, "a 30 HP wall");
		helper.assertTrue(w.z > p.getZ() + 1.5 && Math.abs(w.y - (p.getY() + 1.5)) < 0.2, "standing on the ground in front: " + w.subtract(p.position()));
		net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(EntityType.ARROW, helper.getLevel());
		arrow.moveTo(w.x, w.y, w.z + 3.0, 180f, 0f);
		arrow.setDeltaMovement(0, 0, -1.0);
		helper.getLevel().addFreshEntity(arrow);
		pump(helper, p);
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(StinkflyKit.wallHp(p) == StinkflyKit.WALL_HP - 5, "the arrow stuck in it (-5): " + StinkflyKit.wallHp(p));
			helper.assertTrue(arrow.isAlive() && arrow.getDeltaMovement().lengthSqr() < 1e-4 && Math.abs(arrow.getZ() - w.z) < 1.0,
					"and stays there, visible: " + arrow.position().subtract(w));
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.assertTrue(StinkflyKit.wallHp(p) == 0, "turning back takes the wall down");
			arrow.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void stinkflyWingWeaknesses(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, StinkflyKit.ID);
		StinkflyKit kit = (StinkflyKit) Aliens.kit(StinkflyKit.ID);
		// Fire: +25%
		near(helper, kit.incomingDamage(p, helper.getLevel().damageSources().inFire(), 4f), 5f, "fire hurts 25% more");
		// Fragile Wings: a hit of 8+ in flight clips them (the mock spawns mid-air, so the transformation already took off)
		if (!p.getAbilities().flying) {
			kit.wingFlight(p);
		}
		helper.assertTrue(p.getAbilities().flying, "flying");
		kit.incomingDamage(p, helper.getLevel().damageSources().magic(), 9f);
		helper.assertTrue(!p.getAbilities().flying && !p.getAbilities().mayfly && kit.flag(p, StinkflyKit.F_CLIPPED),
				"a big hit clips his wings (flying " + p.getAbilities().flying + ", mayfly " + p.getAbilities().mayfly + ", clipped "
			+ kit.flag(p, StinkflyKit.F_CLIPPED) + ", creative " + p.isCreative() + ")");
		var n = Omnitrix.state(p).copy();
		n.timers.remove(kit.key(StinkflyKit.FLIGHT));
		Omnitrix.save(p, n);
		helper.assertFalse(kit.wingFlight(p), "clipped wings can't take off");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		// Wet Wings: soaked, no take-off
		ServerPlayer q = as(helper, StinkflyKit.ID);
		StinkflyKit.soak(q);
		q.tickCount++;
		Omnitrix.tick(q);
		helper.assertTrue(kit.flag(q, StinkflyKit.F_WET) && !q.getAbilities().mayfly, "wet wings: no flight");
		helper.assertFalse(kit.wingFlight(q), "and no take-off");
		Omnitrix.revert(q, Omnitrix.Reason.REVOKE);
		helper.succeed();
	}

	/** Use: a tap spits one glob (5); held, the rapid spit (4 a second). */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void stinkflyHeldUseIsARapidSpit(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, StinkflyKit.ID);
		StinkflyKit kit = (StinkflyKit) Aliens.kit(StinkflyKit.ID);
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, -30f);
		// every glob ever seen (they splat on the test's barrier ceiling within a few ticks, so count ids, not the living)
		java.util.Set<Integer> seen = new java.util.HashSet<>();
		Runnable look = () -> helper.getLevel().getEntitiesOfClass(com.projecthero.mod.omnitrix.entity.AlienProjectileEntity.class,
				p.getBoundingBox().inflate(64), e -> e.kind().equals("stinkfly_spit")).forEach(e -> seen.add(e.getId()));
		helper.assertTrue(kit.onUse(p, InteractionHand.MAIN_HAND).consumesAction(), "a tap spits");
		look.run();
		helper.assertTrue(seen.size() == 1, "a tap: one glob");
		int[] ticks = { 0 };
		helper.onEachTick(() -> {
			if (ticks[0]++ < 26) {
				kit.onUse(p, InteractionHand.MAIN_HAND); // the client re-sends Use every tick it is held
				look.run();
			}
		});
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(seen.size() >= 4, "held: a rapid stream of globs (" + seen.size() + ")");
			helper.succeed();
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
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) < 1 + 5 + RipjawsKit.WET_FIST - 0.5, "no wet-fist bonus on land");
		// 60 s on land (v0.17.2: was two minutes), then he chokes
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
				helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) >= 1 + 5 + RipjawsKit.WET_FIST - 1.0e-6, "and a heavier fist");
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
		Villager z = prey(helper, 3.5, 2, 4.0, 100f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 10f);
		p.setHealth(20f);
		helper.assertTrue(kit.bite(p), "Bite");
		near(helper, z.getHealth(), 100f - RipjawsKit.BITE_DAMAGE, "8 damage on land");
		near(helper, p.getHealth(), 21f, "a bite heals 1");
		helper.assertFalse(kit.bite(p), "the bite cools down");
		// v0.17.2 revamp: Shift+G Tail Sweep -- 270 degrees round him, the quarter in front spared
		Villager behind = prey(helper, 3.5, 2, 0.6, 100f);
		float b = z.getHealth();
		helper.assertTrue(kit.tailSweep(p), "Tail Sweep");
		near(helper, z.getHealth(), b, "the zombie straight ahead is spared");
		near(helper, behind.getHealth(), 100f - RipjawsKit.SWEEP_LAND_DAMAGE, "the one behind takes 10 on land");
		helper.assertTrue(behind.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and is slowed");
		helper.assertFalse(kit.tailSweep(p), "the sweep cools down");
		behind.discard();
		helper.assertTrue(kit.waterDash(p) && !kit.waterDash(p), "Water Dash, then cools down");
		helper.assertTrue(kit.breach(p) && kit.flag(p, RipjawsKit.F_BREACH) && !kit.breach(p), "Breach (X can dive after it), then cools down");
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
		// v0.17.2 Steel Jaws: the jaws bite through iron bars and chains
		for (var metal : new net.minecraft.world.level.block.Block[] { Blocks.IRON_BARS, Blocks.CHAIN }) {
			helper.getLevel().setBlock(sand, metal.defaultBlockState(), 3);
			n = Omnitrix.state(p).copy();
			n.timers.remove(kit.key(RipjawsKit.DIG));
			Omnitrix.save(p, n);
			helper.assertTrue(kit.dig(p, sand) && helper.getLevel().getBlockState(sand).isAir(), "bit through " + metal);
		}
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

	/** v0.17.2 Ripjaws revamp: Z on land is Gnash -- five bites in a second at the cone ahead (5 x 4 = 20). */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void ripjawsGnashBitesFiveTimesOnLand(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, RipjawsKit.ID);
		RipjawsKit kit = (RipjawsKit) Aliens.kit(RipjawsKit.ID);
		Villager z = prey(helper, 3.5, 2, 4.0, 100f);
		Villager side = prey(helper, 0.5, 2, 1.5, 100f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 10f);
		helper.assertTrue(kit.whirlpool(p), "Z on land: Gnash");
		near(helper, z.getHealth(), 100f - RipjawsKit.GNASH_DAMAGE, "the first bite lands at once");
		helper.assertFalse(kit.whirlpool(p), "it cools down");
		pump(helper, p);
		helper.runAfterDelay(RipjawsKit.GNASH_EVERY * RipjawsKit.GNASH_BITES + 2, () -> {
			near(helper, z.getHealth(), 100f - RipjawsKit.GNASH_DAMAGE * RipjawsKit.GNASH_BITES, "five bites, 20 in all");
			near(helper, side.getHealth(), 100f, "nothing outside the cone");
			z.discard();
			side.discard();
			helper.succeed();
		});
	}

	/** v0.17.2 Ripjaws revamp (user: "keep drag under the same"): G on land -- clamped 3 s, thrashed 3 x 4, then flung. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void ripjawsDragUnderThrashesAndFlingsOnLand(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, RipjawsKit.ID);
		RipjawsKit kit = (RipjawsKit) Aliens.kit(RipjawsKit.ID);
		helper.assertFalse(kit.dragUnder(p), "nothing to bite: no Drag Under (and no cooldown)");
		Villager z = prey(helper, 3.5, 2, 4.5, 100f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 10f);
		helper.assertTrue(kit.dragUnder(p), "Drag Under clamps on");
		helper.assertTrue(RipjawsKit.dragTarget(p) == z.getId() && kit.flag(p, RipjawsKit.F_DRAG), "the zombie is in his jaws");
		helper.assertTrue(Omnitrix.state(p).animId == RipjawsKit.ANIM_THRASH, "on land he thrashes it");
		helper.assertFalse(kit.dragUnder(p), "it cools down");
		z.setNoAi(false); // (a no-AI mob ignores velocity: let the throw move it)
		pump(helper, p);
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(z.distanceTo(p) < 2.5, "held in front of his face (" + z.distanceTo(p) + ")");
			helper.runAfterDelay(RipjawsKit.DRAG_TICKS - 30 + 1, () -> {
				near(helper, z.getHealth(), 100f - RipjawsKit.DRAG_THRASH_DAMAGE * 3, "three thrashes of 4");
				helper.assertTrue(RipjawsKit.dragTarget(p) == -1 && !kit.flag(p, RipjawsKit.F_DRAG), "let go after 3 s");
				helper.assertTrue(Omnitrix.state(p).animId == RipjawsKit.ANIM_FLING, "and flung it");
				Vec3 from = z.position();
				helper.runAfterDelay(6, () -> {
					helper.assertTrue(z.position().distanceTo(from) > 1.5, "the zombie flies off (" + z.position().distanceTo(from) + ")");
					z.discard();
					helper.succeed();
				});
			});
		});
	}

	/** v0.17.2: Drag Under lets go cleanly when the catch dies or he turns back; a boss is only held 1 s and never moved. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void ripjawsDragUnderReleasesCleanly(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, RipjawsKit.ID);
		RipjawsKit kit = (RipjawsKit) Aliens.kit(RipjawsKit.ID);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 10f);
		Villager z = prey(helper, 3.5, 2, 4.5, 100f);
		helper.assertTrue(kit.dragUnder(p), "Drag Under");
		z.kill();
		pump(helper, p);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(RipjawsKit.dragTarget(p) == -1 && !kit.flag(p, RipjawsKit.F_DRAG), "the catch died: let go");
			// a boss: held a second where it stands
			Villager boss = prey(helper, 3.5, 2, 4.5, 200f);
			var n = Omnitrix.state(p).copy();
			n.timers.remove(kit.key(RipjawsKit.DRAG));
			Omnitrix.save(p, n);
			Vec3 at = boss.position();
			helper.assertTrue(kit.dragUnder(p), "Drag Under on a boss");
			helper.runAfterDelay(RipjawsKit.DRAG_BOSS_TICKS + 2, () -> {
				helper.assertTrue(RipjawsKit.dragTarget(p) == -1, "a boss slips free after 1 s");
				helper.assertTrue(boss.position().distanceTo(at) < 0.5, "and was never towed");
				// turning back mid-drag lets go
				var n2 = Omnitrix.state(p).copy();
				n2.timers.remove(kit.key(RipjawsKit.DRAG));
				Omnitrix.save(p, n2);
				helper.assertTrue(kit.dragUnder(p), "Drag Under again");
				Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
				helper.assertTrue(RipjawsKit.dragTarget(p) == -1, "turning back lets go");
				boss.discard();
				helper.succeed();
			});
		});
	}

	/**
	 * v0.17.2 Ripjaws revamp (user: "Drop tidal roar"): Shift+Z Megamouth -- 0.75 s of gaping, then a 4-block chomp that ignores
	 * all armour (30), heals a quarter of the damage dealt (max 8), and every kill gives back 30 s of hydration.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void ripjawsMegamouthChompsThroughArmour(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, RipjawsKit.ID);
		RipjawsKit kit = (RipjawsKit) Aliens.kit(RipjawsKit.ID);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 10f);
		Villager z = prey(helper, 3.5, 2, 4.0, 100f);
		z.getAttribute(Attributes.ARMOR).setBaseValue(10);
		Villager weak = prey(helper, 4.2, 2, 3.6, 20f);
		Villager behind = prey(helper, 3.5, 2, 0.6, 100f);
		p.setHealth(10f);
		kit.setDryness(p, 1200, false, false);
		helper.assertTrue(kit.megamouth(p), "Megamouth");
		near(helper, z.getHealth(), 100f, "nothing yet: the jaw is still unhinging");
		helper.assertFalse(kit.megamouth(p), "it cools down");
		pump(helper, p);
		// (the health just before the chomp: the gametests run side by side, and a neighbour's area move now and then reaches in)
		float[] before = new float[3];
		boolean[] weakAlive = new boolean[1];
		helper.runAfterDelay(RipjawsKit.MEGA_WINDUP - 3, () -> {
			before[0] = z.getHealth();
			before[1] = behind.getHealth();
			before[2] = p.getHealth();
			weakAlive[0] = weak.isAlive();
		});
		helper.runAfterDelay(RipjawsKit.MEGA_WINDUP + 2, () -> {
			near(helper, z.getHealth(), before[0] - RipjawsKit.MEGA_DAMAGE, "30 through 10 armour");
			helper.assertFalse(weak.isAlive(), "the weak one is swallowed");
			near(helper, behind.getHealth(), before[1], "nothing behind him");
			float dealt = RipjawsKit.MEGA_DAMAGE + (weakAlive[0] ? 20f : 0f);
			near(helper, p.getHealth(), before[2] + Math.min(RipjawsKit.MEGA_HEAL_MAX, dealt * RipjawsKit.MEGA_HEAL_SHARE),
					"healed a quarter of the damage dealt, capped at 8");
			if (weakAlive[0]) {
				helper.assertTrue(RipjawsKit.dryness(p, helper.getLevel().getGameTime()) < 1200 - RipjawsKit.MEGA_KILL_REFILL + 60,
			"the kill gave back 30 s of hydration (" + RipjawsKit.dryness(p, helper.getLevel().getGameTime()) + ")");
			}
			z.discard();
			behind.discard();
			helper.succeed();
		});
	}

	/** v0.17.2 Steel Jaws: R ignores half the armour and wears what the target has on. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 30)
	public void ripjawsSteelJawsBiteThroughArmour(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, RipjawsKit.ID);
		RipjawsKit kit = (RipjawsKit) Aliens.kit(RipjawsKit.ID);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 10f);
		Villager z = prey(helper, 3.5, 2, 4.0, 100f);
		ItemStack plate = new ItemStack(Items.IRON_CHESTPLATE);
		z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, plate);
		// (the chestplate's armour reaches the attribute on the zombie's next tick)
		helper.runAfterDelay(2, () -> {
			float armour = z.getArmorValue();
			helper.assertTrue(armour >= 6f, "an iron chestplate on (" + armour + ")");
			float full = net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(z, RipjawsKit.BITE_DAMAGE,
					helper.getLevel().damageSources().generic(), armour, 0f);
			helper.assertTrue(kit.bite(p), "Bite");
			float lost = 100f - z.getHealth();
			helper.assertTrue(lost > full + 0.1f && lost <= RipjawsKit.BITE_DAMAGE + 0.01f, "half the armour ignored (" + lost + " vs " + full + " through all of it)");
			helper.assertTrue(z.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getDamageValue() >= RipjawsKit.STEEL_JAWS_WEAR,
					"the chestplate lost 8 durability");
			z.discard();
			helper.succeed();
		});
	}

	/** v0.17.2 Ripjaws revamp: Shift+X Breach, then X looking down in the air = the Dive Strike (14 where he lands). */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void ripjawsBreachThenDiveStrike(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, RipjawsKit.ID);
		RipjawsKit kit = (RipjawsKit) Aliens.kit(RipjawsKit.ID);
		Villager z = prey(helper, 3.5, 2, 4.0, 100f);
		p.setOnGround(false);
		p.moveTo(p.getX(), p.getY() + 3, p.getZ(), 0f, 60f);
		helper.assertFalse(kit.canDive(p), "no dive without a Breach first");
		helper.assertTrue(kit.breach(p), "Breach");
		p.setOnGround(false);
		p.moveTo(z.getX(), z.getY() + 3, z.getZ() - 1.2, 0f, 60f);
		helper.assertTrue(kit.canDive(p), "airborne after a Breach, looking down: X dives");
		kit.press(p, com.projecthero.mod.hero.AbilitySlot.SLOT_3, false);
		helper.assertTrue(kit.flag(p, RipjawsKit.F_DIVING) && !kit.flag(p, RipjawsKit.F_BREACH), "the Dive Strike plunges (once per Breach)");
		helper.assertTrue(kit.cooldownLeft(p, RipjawsKit.DASH) == 0, "X dived instead of dashing");
		p.moveTo(z.getX(), z.getY(), z.getZ() - 1.2, 0f, 60f);
		p.setOnGround(true);
		pump(helper, p);
		helper.runAfterDelay(2, () -> {
			helper.assertFalse(kit.flag(p, RipjawsKit.F_DIVING), "landed");
			near(helper, z.getHealth(), 100f - RipjawsKit.DIVE_DAMAGE, "the jaws-first impact bites for 14");
			z.discard();
			helper.succeed();
		});
	}

	/** v0.17.2 Ripjaws revamp: heat dries him faster -- the rates, and burning adds 10 s of dryness a second. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void ripjawsHeatAndFireDryHimFaster(GameTestHelper helper) {
		floor(helper);
		near(helper, RipjawsKit.dryRate(0), 1f, "dry land: 1");
		near(helper, RipjawsKit.dryRate(RipjawsKit.F_HOT), 1.5f, "a hot biome: x1.5");
		near(helper, RipjawsKit.dryRate(RipjawsKit.F_NETHER | RipjawsKit.F_HOT), 3f, "the Nether: x3");
		near(helper, RipjawsKit.dryRate(RipjawsKit.F_HOT | RipjawsKit.F_LURE), 1.875f, "the lure: another x1.25");
		near(helper, RipjawsKit.dryRate(RipjawsKit.F_WET | RipjawsKit.F_NETHER), -RipjawsKit.REFILL_WATER, "water always refills");
		ServerPlayer p = as(helper, RipjawsKit.ID);
		RipjawsKit kit = (RipjawsKit) Aliens.kit(RipjawsKit.ID);
		kit.setDryness(p, 100, false, false);
		p.setRemainingFireTicks(200);
		pump(helper, p);
		helper.runAfterDelay(22, () -> {
			int d = RipjawsKit.dryness(p, helper.getLevel().getGameTime());
			helper.assertTrue(d >= 100 + RipjawsKit.FIRE_DRY, "burning dried him 10 s at once (" + d + ")");
			p.clearFire();
			Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
			helper.succeed();
		});
	}

	/** v0.17.2 (user: "lower his time out of water to 60 seconds please"): a full meter lasts exactly 60 s of dry land. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void ripjawsHydrationLastsSixtySeconds(GameTestHelper helper) {
		floor(helper);
		helper.assertTrue(RipjawsKit.MAX_DRY == 60 * 20, "60 s of dryness (" + RipjawsKit.MAX_DRY + ")");
		ServerPlayer p = as(helper, RipjawsKit.ID);
		RipjawsKit kit = (RipjawsKit) Aliens.kit(RipjawsKit.ID);
		long now = helper.getLevel().getGameTime();
		kit.setDryness(p, 0, false, false); // full, on plain dry land
		near(helper, RipjawsKit.hydration(p, now), 1f, "full");
		near(helper, RipjawsKit.hydration(p, now + 600), 0.5f, "half gone after 30 s");
		helper.assertTrue(RipjawsKit.dryness(p, now + 60 * 20 - 1) < RipjawsKit.MAX_DRY, "still breathing at 59.95 s");
		helper.assertTrue(RipjawsKit.dryness(p, now + 60 * 20) == RipjawsKit.MAX_DRY, "empty at 60 s");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.succeed();
	}

	/**
	 * v0.17.2 (user: "make ripjaws cruise swim at 10 blocks per second and his sprint swim be 30 blocks per second please"): the swim
	 * velocity along his look, and the per-tick correction that takes vanilla's own additions back off -- run against a stand-in
	 * for vanilla's water travel (a constant key push and pitch nudge added before the move), the distance moved each tick settles
	 * on exactly 0.5 / 1.5 blocks (10 / 30 a second) from the second tick on.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void ripjawsSwimsTenCruisingAndThirtySprinting(GameTestHelper helper) {
		Vec3 look = new Vec3(0.3, -0.4, -0.8).normalize();
		near(helper, (float) (RipjawsKit.swimVelocity(look, false, true).length() * 20), 10f, "cruise: 10 blocks a second");
		near(helper, (float) (RipjawsKit.swimVelocity(look, true, true).length() * 20), 30f, "sprint swim: 30 blocks a second");
		helper.assertTrue(RipjawsKit.swimVelocity(look, true, true).normalize().distanceTo(look) < 1.0e-6, "steered by his look");
		Vec3 up = new Vec3(0, 0.6, -0.8);
		Vec3 surface = RipjawsKit.swimVelocity(up, false, false);
		helper.assertTrue(Math.abs(surface.y) < 1.0e-9 && Math.abs(surface.length() * 20 - 10) < 1.0e-6, "head above water: level, no climbing out");
		for (boolean sprint : new boolean[] { false, true }) {
			Vec3 desired = RipjawsKit.swimVelocity(look, sprint, true);
			Vec3 extra = new Vec3(0.04, -0.03, -0.06); // what vanilla adds on top each tick
			Vec3 set = null, moved = null;
			for (int t = 0; t < 6; t++) {
				set = RipjawsKit.swimPush(desired, set, moved, false);
				moved = set.add(extra); // vanilla's travel: its additions, then the move
				if (t >= 1) {
					helper.assertTrue(moved.distanceTo(desired) < 1.0e-9, (sprint ? "sprint" : "cruise") + " tick " + t + ": moved " + moved.length() * 20 + " b/s");
				}
			}
			near(helper, (float) (moved.length() * 20), sprint ? 30f : 10f, sprint ? "settles on 30 b/s" : "settles on 10 b/s");
		}
		// a bump resets the correction; a wild jump (a teleport) is capped
		Vec3 d = RipjawsKit.swimVelocity(look, false, true);
		helper.assertTrue(RipjawsKit.swimPush(d, d, Vec3.ZERO, true).equals(d), "after a bump: no correction");
		helper.assertTrue(RipjawsKit.swimPush(d, d, d.add(50, 50, 50), false).distanceTo(d) <= RipjawsKit.SWIM_BIAS_CAP * Math.sqrt(3) + 1.0e-9,
				"the correction is capped");
		// the launch moves keep their own velocity for a moment
		OmnitrixState s = new OmnitrixState();
		s.animId = RipjawsKit.ANIM_DASH;
		s.animStart = 100;
		helper.assertTrue(RipjawsKit.swimHeld(s, 105), "the Water Dash keeps its burst");
		helper.assertFalse(RipjawsKit.swimHeld(s, 130), "then he swims again");
		s.animId = RipjawsKit.ANIM_BITE;
		helper.assertFalse(RipjawsKit.swimHeld(s, 105), "a bite does not stop the swim");
		helper.succeed();
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
		near(helper, p.getMaxHealth(), 20f, "20 health (v0.17.2: no alien under 20)");
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
		// v0.17.2 round 3: Z builds the Galvan Sentry on the block he looks at (down at his feet here), then cools down
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 60f);
		helper.assertTrue(kit.galvanSentry(p) && GreyMatterKit.sentry(p) != null && !kit.galvanSentry(p), "Galvan Sentry, then cools down");
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		// v0.17.2: Scurry opens with the smoke puff (what is left of Smoke Charge): blinds within 3, no damage
		float bs = z.getHealth();
		helper.assertTrue(kit.scurry(p) && kit.flag(p, GreyMatterKit.F_SCURRY) && !kit.scurry(p), "Scurry, then cools down");
		helper.assertTrue(z.hasEffect(MobEffects.BLINDNESS), "Scurry's smoke puff blinds what is close");
		near(helper, z.getHealth(), bs, "the puff does no damage");
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
			helper.assertTrue(GreyMatterKit.sentry(p) == null, "and takes his sentry down");
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - p.getAttributeBaseValue(Attributes.MOVEMENT_SPEED)) < 1.0e-6,
					"no scurry speed left behind");
			z.discard();
			helper.succeed();
		});
	}

	// ================================================================ v0.17.2 Grey Matter revamp

	/** Shift+Z Inside Job: in, untouchable, six pulses of 6 through its armour, out the far side, it is stunned. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void greyMatterInsideJobThrashesAndPopsOutSafely(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, GreyMatterKit.ID);
		GreyMatterKit kit = (GreyMatterKit) Aliens.kit(GreyMatterKit.ID);
		// never a player
		ServerPlayer other = mock(helper, 3.5, 2, 3.0);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		helper.assertFalse(kit.insideJob(p), "Inside Job refuses a player");
		helper.assertTrue(kit.cooldownLeft(p, GreyMatterKit.INSIDE_JOB) == 0, "no cooldown spent on a refusal");
		other.discard();
		Zombie z = zombie(helper, 3.5, 2, 3.5, 100f);
		z.getAttribute(Attributes.ARMOR).setBaseValue(12); // the thrashing ignores this
		Vec3 start = p.position();
		helper.assertTrue(kit.insideJob(p), "Inside Job");
		helper.assertTrue(p.getVehicle() == z && GreyMatterKit.inside(p) && kit.flag(p, GreyMatterKit.F_INSIDE) && p.isInvisible(), "he is inside it");
		helper.assertFalse(kit.insideJob(p), "cooling down");
		float hp = p.getHealth();
		p.hurt(helper.getLevel().damageSources().generic(), 6f);
		near(helper, p.getHealth(), hp, "nothing reaches him in there");
		pump(helper, p);
		helper.runAfterDelay(GreyMatterKit.INSIDE_TICKS + 8, () -> {
			helper.assertFalse(GreyMatterKit.inside(p) || kit.flag(p, GreyMatterKit.F_INSIDE), "out again");
			helper.assertTrue(p.getVehicle() == null && !p.isInvisible(), "off it and visible");
			helper.assertTrue(Math.abs(z.getHealth() - (100f - 6 * GreyMatterKit.INSIDE_DAMAGE)) < 1.5f, "six pulses of 6 through 12 armour: " + z.getHealth());
			helper.assertTrue(rooted(z) && z.hasEffect(MobEffects.WEAKNESS), "it is stunned");
			double side = new Vec3(p.getX() - z.getX(), 0, p.getZ() - z.getZ()).dot(new Vec3(z.getX() - start.x, 0, z.getZ() - start.z));
			helper.assertTrue(side > 0, "he popped out of the FAR side");
			helper.assertTrue(helper.getLevel().noCollision(p, p.getBoundingBox()), "somewhere he fits");
			Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
			z.discard();
			helper.succeed();
		});
	}

	/** Inside Job on a machine (an iron golem): rewired -- x1.5 damage and shut down; and he always gets out (death, revert). */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void greyMatterInsideJobRewiresMachinesAndAlwaysGetsOut(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, GreyMatterKit.ID);
		GreyMatterKit kit = (GreyMatterKit) Aliens.kit(GreyMatterKit.ID);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		net.minecraft.world.entity.animal.IronGolem g = EntityType.IRON_GOLEM.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2, 4.0));
		g.moveTo(at.x, at.y, at.z, 180f, 0f);
		g.setNoAi(true);
		g.setPersistenceRequired();
		// v0.17.2 damage pass: six pulses of ~18 (109) would finish a plain 100-health golem -- a tougher one (still under a boss's 150) shows the full rewire
		g.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(140.0);
		g.setHealth(140f);
		helper.getLevel().addFreshEntity(g);
		helper.assertTrue(GreyMatterKit.machine(g) && GreyMatterKit.insidePulse(g) == GreyMatterKit.INSIDE_DAMAGE * GreyMatterKit.MACHINE_FACTOR, "a golem is a machine: ~18 a pulse");
		float g0 = g.getHealth();
		helper.assertTrue(kit.insideJob(p) && p.getVehicle() == g, "inside the golem");
		pump(helper, p);
		helper.runAfterDelay(GreyMatterKit.INSIDE_TICKS + 8, () -> {
			helper.assertTrue(p.getVehicle() == null && !GreyMatterKit.inside(p), "out of the golem");
			helper.assertTrue(Math.abs(g.getHealth() - (g0 - 6 * GreyMatterKit.INSIDE_DAMAGE * GreyMatterKit.MACHINE_FACTOR)) < 1.5f, "rewired for ~109: " + g.getHealth());
			helper.assertTrue(rooted(g), "shut down");
			g.discard();
			// a creature that dies while he is in it: out where it fell
			kit.cooldown(p, GreyMatterKit.INSIDE_JOB, 0);
			Zombie weak = zombie(helper, 3.5, 2, 3.5, 8f);
			p.moveTo(helper.absoluteVec(new Vec3(3.5, 2, 1.5)).x, helper.absoluteVec(new Vec3(3.5, 2, 1.5)).y, helper.absoluteVec(new Vec3(3.5, 2, 1.5)).z, 0f, 0f);
			helper.assertTrue(kit.insideJob(p) && p.getVehicle() == weak, "inside a weak zombie");
			helper.runAfterDelay(25, () -> {
				helper.assertFalse(weak.isAlive(), "it died of it");
				helper.assertTrue(p.getVehicle() == null && !GreyMatterKit.inside(p) && !p.isInvisible(), "he came out when it died");
				// turning back mid-job: out at once
				kit.cooldown(p, GreyMatterKit.INSIDE_JOB, 0);
				Zombie z2 = zombie(helper, 3.5, 2, 3.5, 100f);
				p.moveTo(helper.absoluteVec(new Vec3(3.5, 2, 1.5)).x, helper.absoluteVec(new Vec3(3.5, 2, 1.5)).y, helper.absoluteVec(new Vec3(3.5, 2, 1.5)).z, 0f, 0f);
				helper.assertTrue(kit.insideJob(p) && p.getVehicle() == z2, "inside another");
				Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
				helper.assertTrue(p.getVehicle() == null && !GreyMatterKit.inside(p) && !p.isInvisible() && z2.isAlive(), "turning back puts him outside");
				z2.discard();
				helper.succeed();
			});
		});
	}

	/** Z Galvan Sentry: built on the block looked at, in its material, shoots the analysed creature first at 8 a shot; gone when he turns back. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void greyMatterSentryShootsTheAnalysedFoeFirst(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, GreyMatterKit.ID);
		GreyMatterKit kit = (GreyMatterKit) Aliens.kit(GreyMatterKit.ID);
		Zombie near = zombie(helper, 2.5, 2, 6.0, 100f);
		Zombie far = zombie(helper, 5.5, 2, 2.5, 100f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 70f);
		helper.assertTrue(kit.moveOn(com.projecthero.mod.hero.AbilitySlot.SLOT_4, false, false).id().equals(GreyMatterKit.SENTRY), "Z is the Sentry");
		helper.assertTrue(kit.move(GreyMatterKit.SENTRY).cooldown() == 400 && GreyMatterKit.SENTRY_TICKS == 300, "20 s cooldown, 15 s long");
		helper.assertTrue(kit.galvanSentry(p), "a sentry goes up on the block he looks at");
		var sentry = GreyMatterKit.sentry(p);
		helper.assertTrue(sentry != null && sentry.material().isSolid(), "built from that block");
		GreyMatterKit.mark(p, far, 200);
		pump(helper, p);
		helper.runAfterDelay(GreyMatterSentryEntity.DEPLOY_TICKS + 16, () -> {
			helper.assertTrue(near.getHealth() == 100f, "the nearer zombie is left alone: " + near.getHealth());
			float lost = 100f - far.getHealth();
			helper.assertTrue(lost >= 2f * GreyMatterKit.SENTRY_DAMAGE * GreyMatterKit.MARK_FACTOR - 0.05f, "the analysed one is shot (x1.35 marked): " + lost);
			float hp = sentry.getHealth();
			sentry.hurt(com.projecthero.mod.omnitrix.OmnitrixDamage.alienStrike(p), 10f);
			helper.assertTrue(sentry.getHealth() == hp, "his own side cannot break it");
			Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
			helper.assertTrue(GreyMatterKit.sentry(p) == null && sentry.isRemoved(), "the sentry goes with him");
			near.discard();
			far.discard();
			helper.succeed();
		});
	}

	/** The Block Mech's HP comes from its block's hardness, 30 + 20 x hardness, at most 300. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void greyMatterMechHpFollowsTheBlock(GameTestHelper helper) {
		var l = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
		near(helper, GreyMatterMechEntity.hpFor(Blocks.DIRT.defaultBlockState(), l, at), 40f, "dirt");
		near(helper, GreyMatterMechEntity.hpFor(Blocks.STONE.defaultBlockState(), l, at), 60f, "stone");
		near(helper, GreyMatterMechEntity.hpFor(Blocks.OAK_PLANKS.defaultBlockState(), l, at), 70f, "planks");
		near(helper, GreyMatterMechEntity.hpFor(Blocks.IRON_BLOCK.defaultBlockState(), l, at), 130f, "iron");
		near(helper, GreyMatterMechEntity.hpFor(Blocks.OBSIDIAN.defaultBlockState(), l, at), 300f, "obsidian hits the cap");
		near(helper, GreyMatterMechEntity.hpFor(Blocks.BEDROCK.defaultBlockState(), l, at), 300f, "unbreakable is the cap");
		near(helper, GreyMatterMechEntity.hpFor(Blocks.TORCH.defaultBlockState(), l, at), 30f, "nothing is under the base");
		helper.succeed();
	}

	/** Shift+Z Block Mech: 1.5 tall, he rides it, it takes the hit and he takes none, it ends after 30 s or at 0 HP, and he is set down unhurt. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void greyMatterMechTakesAllDamageAndFights(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, GreyMatterKit.ID);
		GreyMatterKit kit = (GreyMatterKit) Aliens.kit(GreyMatterKit.ID);
		BlockPos stone = helper.absolutePos(new BlockPos(3, 1, 3));
		helper.getLevel().setBlock(stone, Blocks.IRON_BLOCK.defaultBlockState(), 3);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 80f);
		helper.assertTrue(kit.move(GreyMatterKit.MECH).cooldown() == 1000 && GreyMatterKit.MECH_TICKS == 600, "50 s cooldown, 30 s long");
		helper.assertTrue(kit.moveOn(com.projecthero.mod.hero.AbilitySlot.SLOT_4, true, false).id().equals(GreyMatterKit.MECH), "Shift+Z is the mech");
		helper.assertTrue(kit.moveOn(com.projecthero.mod.hero.AbilitySlot.SLOT_2, true, false).id().equals(GreyMatterKit.INSIDE_JOB), "Inside Job moved to Shift+G");
		helper.assertTrue(kit.blockMech(p), "the mech goes up");
		GreyMatterMechEntity m = GreyMatterMechEntity.mechOf(p);
		helper.assertTrue(m != null && kit.flag(p, GreyMatterKit.F_MECH), "he is riding it");
		near(helper, m.getBbHeight(), 1.5f, "1.5 blocks tall");
		near(helper, m.getMaxHealth(), GreyMatterMechEntity.hpFor(m.material(), helper.getLevel(), m.blockPosition()), "HP from its block");
		helper.assertTrue(p.getVehicle() == m && m.getControllingPassenger() == p, "and steers it");
		helper.assertTrue(kit.liveMoveOn(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1, false, false).id().equals(GreyMatterKit.MECH_PUNCH), "R is the mech's fist");
		Zombie z = zombie(helper, 3.0, 2, 3.0, 100f);
		pump(helper, p);
		helper.runAfterDelay(GreyMatterMechEntity.ASSEMBLE_TICKS + 2, () -> {
			helper.assertTrue(m.active(), "built");
			float hp = p.getHealth(), mech = m.getHealth();
			m.invulnerableTime = 0;
			p.hurt(helper.getLevel().damageSources().magic(), 9f);
			near(helper, p.getHealth(), hp, "the pilot takes nothing");
			near(helper, m.getHealth(), mech - 9f, "the mech takes it all");
			float before = z.getHealth();
			helper.assertTrue(kit.mechPunch(p, z), "a punch");
			near(helper, z.getHealth(), before - GreyMatterKit.MECH_PUNCH_DAMAGE, "the piston punch lands 15");
			helper.assertFalse(kit.mechPunch(p, z), "the fist has a beat");
			m.invulnerableTime = 0;
			m.hurt(helper.getLevel().damageSources().magic(), 100000f);
			helper.assertTrue(p.getVehicle() == null && GreyMatterMechEntity.mechOf(p) == null, "at 0 HP he is out");
			near(helper, p.getHealth(), hp, "unhurt");
			helper.runAfterDelay(3, () -> {
				helper.assertFalse(kit.flag(p, GreyMatterKit.F_MECH), "the flag clears");
				helper.assertTrue(kit.cooldownLeft(p, GreyMatterKit.MECH) > 0, "and the cooldown runs");
				z.discard();
				helper.succeed();
			});
		});
	}

	/** Shift+Z again climbs out, and the mech falls apart; turning back does the same. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void greyMatterClimbsOutOfTheMech(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, GreyMatterKit.ID);
		GreyMatterKit kit = (GreyMatterKit) Aliens.kit(GreyMatterKit.ID);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 80f);
		helper.assertTrue(kit.blockMech(p), "up");
		GreyMatterMechEntity m = GreyMatterMechEntity.mechOf(p);
		helper.assertTrue(m != null, "riding");
		helper.assertTrue(kit.blockMech(p) && p.getVehicle() == null && m.state() == GreyMatterMechEntity.STATE_COLLAPSING, "pressed again he climbs out and it falls apart");
		pump(helper, p);
		helper.runAfterDelay(GreyMatterMechEntity.COLLAPSE_TICKS + 4, () -> {
			helper.assertTrue(m.isRemoved(), "gone");
			kit.cooldown(p, GreyMatterKit.MECH, 0);
			helper.assertTrue(kit.blockMech(p), "up again");
			GreyMatterMechEntity m2 = GreyMatterMechEntity.mechOf(p);
			Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
			helper.assertTrue(p.getVehicle() == null && m2.state() == GreyMatterMechEntity.STATE_COLLAPSING, "turning back puts him out");
			helper.succeed();
		});
	}

	/** Rewire's blocks, the passives and the weaknesses. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void greyMatterRewirePassivesAndWeaknesses(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, GreyMatterKit.ID);
		GreyMatterKit kit = (GreyMatterKit) Aliens.kit(GreyMatterKit.ID);
		var l = helper.getLevel();
		refreshAttributes(p); // his small box and eyes now, not on the next tick
		// Rewire
		BlockPos lever = helper.absolutePos(new BlockPos(1, 2, 1));
		l.setBlock(lever, Blocks.LEVER.defaultBlockState(), 2);
		helper.assertTrue(kit.rewireBlock(p, l, lever) == 0 && l.getBlockState(lever).getValue(net.minecraft.world.level.block.LeverBlock.POWERED), "a lever flipped");
		BlockPos lamp = helper.absolutePos(new BlockPos(0, 2, 6)); // away from the lever (it would keep it lit)
		l.setBlock(lamp, Blocks.REDSTONE_LAMP.defaultBlockState(), 2);
		helper.assertTrue(kit.rewireBlock(p, l, lamp) == 0 && l.getBlockState(lamp).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT), "a lamp lit");
		BlockPos wire = helper.absolutePos(new BlockPos(1, 2, 3));
		l.setBlock(wire, Blocks.TRIPWIRE.defaultBlockState(), 2);
		helper.assertTrue(kit.rewireBlock(p, l, wire) == 0 && l.getBlockState(wire).isAir(), "a tripwire disarmed");
		BlockPos spawner = helper.absolutePos(new BlockPos(1, 2, 5));
		l.setBlock(spawner, Blocks.SPAWNER.defaultBlockState(), 2);
		var be = (net.minecraft.world.level.block.entity.SpawnerBlockEntity) l.getBlockEntity(spawner);
		helper.assertTrue(kit.rewireBlock(p, l, spawner) == GreyMatterKit.REWIRE_SPAWNER_COOLDOWN, "a spawner: the long cooldown");
		helper.assertTrue(GreyMatterKit.spawnerDelay(be) >= GreyMatterKit.SPAWNER_OFF_TICKS, "switched off for 60 s");
		helper.assertTrue(kit.rewireBlock(p, l, helper.absolutePos(new BlockPos(1, 1, 1))) < 0, "plain stone: nothing to rewire");
		// primed TNT straight ahead is defused
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		net.minecraft.world.entity.item.PrimedTnt tnt = EntityType.TNT.create(l);
		Vec3 tat = p.getEyePosition().add(0, -0.45, 2.0); // level with his eyes (a mock player's box is refreshed on its next tick only)
		tnt.moveTo(tat.x, tat.y, tat.z);
		tnt.setFuse(200);
		l.addFreshEntity(tnt);
		helper.assertTrue(kit.rewire(p) && tnt.isRemoved(), "Rewire defuses primed TNT");
		helper.assertTrue(!l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, tnt.getBoundingBox().inflate(1.5),
				i -> i.getItem().is(Items.TNT)).isEmpty(), "it drops back as an item");
		// Galvan Intellect / the machine check
		helper.assertTrue(GreyMatterKit.anvilCost(10) == 7 && GreyMatterKit.anvilCost(1) == 1, "anvil repairs 30% cheaper");
		helper.assertTrue(GreyMatterKit.galvan(p), "he is a Galvan");
		// Amphibian, Too Small for Big Weapons
		double speed0 = p.getAttributeValue(Attributes.ATTACK_SPEED);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		kit.tick(p, Omnitrix.state(p), 4000L);
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_SPEED) < speed0 * 0.6, "a sword swings at half speed");
		helper.assertTrue(p.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY) >= GreyMatterKit.SWIM_BONUS - 1.0e-4, "he swims 50% faster");
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		kit.tick(p, Omnitrix.state(p), 4001L);
		near(helper, (float) p.getAttributeValue(Attributes.ATTACK_SPEED), (float) speed0, "empty-handed: full speed again");
		// Sticky Climber
		p.horizontalCollision = true;
		helper.assertTrue(kit.climbs(p), "pressed into a wall, he climbs");
		p.horizontalCollision = false;
		l.setBlock(helper.absolutePos(new BlockPos(4, 2, 1)), Blocks.STONE.defaultBlockState(), 2);
		l.setBlock(helper.absolutePos(new BlockPos(4, 3, 1)), Blocks.STONE.defaultBlockState(), 2);
		Vec3 wall = helper.absoluteVec(new Vec3(3.9, 2.4, 1.5));
		p.moveTo(wall.x, wall.y, wall.z, 0f, 0f);
		p.setOnGround(false);
		p.setShiftKeyDown(true);
		helper.assertTrue(kit.climbs(p), "Sneak holds him on the wall");
		p.setShiftKeyDown(false);
		p.setOnGround(true);
		helper.assertFalse(kit.climbs(p), "standing on the ground by a wall is not climbing");
		Vec3 open = helper.absoluteVec(new Vec3(2.5, 2.4, 5.5));
		p.moveTo(open.x, open.y, open.z, 0f, 0f);
		p.setOnGround(false);
		helper.assertFalse(kit.climbs(p), "in the open air: nothing to cling to");
		p.setOnGround(true);
		// Swatted: x1.5 knockback; a big creature's hit stuns him
		helper.assertTrue(GreyMatterKit.knockbackScale(p) == GreyMatterKit.SWAT_KNOCKBACK, "x1.5 knockback");
		ServerPlayer human = mock(helper, 6.5, 2, 6.5);
		helper.assertTrue(GreyMatterKit.knockbackScale(human) == 1.0, "a human: normal knockback");
		p.setDeltaMovement(Vec3.ZERO);
		p.knockback(0.4, 1, 0);
		human.setDeltaMovement(Vec3.ZERO);
		human.knockback(0.4, 1, 0);
		near(helper, (float) Math.abs(p.getDeltaMovement().x), (float) Math.abs(human.getDeltaMovement().x) * GreyMatterKit.SWAT_KNOCKBACK, "knocked half again as far");
		net.minecraft.world.entity.animal.IronGolem g = EntityType.IRON_GOLEM.create(l);
		g.moveTo(p.getX() + 1, p.getY(), p.getZ());
		helper.assertTrue(GreyMatterKit.bigger(g), "a golem is bigger than a player");
		p.invulnerableTime = 0;
		kit.incomingDamage(p, l.damageSources().mobAttack(g), 4f);
		helper.assertTrue(kit.timer(p, GreyMatterKit.SWAT_END) > l.getGameTime() && rooted(p), "swatted: stunned 0.5 s");
		// Fragile
		Zombie z = zombie(helper, 6.5, 2, 1.5, 20f);
		near(helper, kit.incomingDamage(p, l.damageSources().anvil(z), 4f), 8f, "a falling anvil hits twice as hard");
		z.discard();
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.assertFalse(l.getBlockState(lamp).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT), "the lamp he lit goes dark when he turns back");
		near(helper, (float) p.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY), 0f, "no swim bonus left");
		helper.assertTrue(GreyMatterKit.knockbackScale(p) == 1.0, "normal knockback again");
		helper.succeed();
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
