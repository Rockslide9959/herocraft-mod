package com.projecthero.mod.gametest;

import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadBlocks;
import com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadKit;
import com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakKit;
import com.projecthero.mod.omnitrix.alien.upgrade.UpgradeEmp;
import com.projecthero.mod.omnitrix.alien.upgrade.UpgradeKit;
import com.projecthero.mod.omnitrix.alien.xlr8.Xlr8Kit;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Omnitrix wave A: XLR8, Diamondhead, Upgrade and Ghostfreak -- each one transformed into through the framework, its
 * body (attributes), every move's damage and cooldown, its passives (Diamondhead's reflection, Ghostfreak's intangibility and
 * its timer, Upgrade's slide hitbox and merges) and the clean-up when it turns back (no stuck intangibility, size or
 * invisibility). Mock players are not ticked by the server, so tests over time drive {@link Omnitrix#tick} themselves.
 */
public class OmnitrixWaveAGameTests implements FabricGameTest {
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
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		helper.assertTrue(Omnitrix.select(p, alien), alien + " is playable");
		helper.assertTrue(Omnitrix.transform(p), "transformed into " + alien);
		helper.assertTrue(Omnitrix.state(p).active.equals(alien), "the framework says " + alien);
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
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
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
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

	private static boolean near(float a, float b) {
		return Math.abs(a - b) < 0.05f;
	}

	// ================================================================ XLR8

	@GameTest(template = EMPTY_STRUCTURE)
	public void xlr8IsFastLightAndSprintsFaster(GameTestHelper helper) {
		ServerPlayer p = as(helper, Xlr8Kit.ID);
		Xlr8Kit kit = (Xlr8Kit) Aliens.kit(Xlr8Kit.ID);
		helper.assertTrue(near(p.getMaxHealth(), 22f), "22 health");
		double walk = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		helper.assertTrue(walk > 0.1 * 1.8, "very fast: " + walk);
		helper.assertTrue(p.getAttributeValue(Attributes.ARMOR) <= 2.0 + 1.0e-6, "light armour");
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_SPEED) > 4.0 + 1.5, "fast hands");
		p.setSprinting(true);
		kit.tick(p, Omnitrix.state(p), helper.getLevel().getGameTime());
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > walk * 1.3, "sprinting is faster still");
		// v0.17.1 revamp: V's Speed Mode (wall and water running came with it)
		double sprinting = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		helper.assertTrue(kit.toggleSpeedMode(p) && kit.flag(p, Xlr8Kit.F_SPEED), "Speed Mode on");
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > sprinting * 1.8, "Speed Mode: twice as fast again");
		helper.assertTrue(Xlr8Kit.visorDown(p), "the visor is down");
		helper.assertTrue(kit.standsOnFluid(p, Fluids.WATER.getSource(false)), "runs on water");
		helper.assertFalse(kit.standsOnFluid(p, Fluids.LAVA.getSource(false)), "not on lava");
		p.setSprinting(false); // vanilla's own sprint boost off too
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.1) < 1.0e-4, "the speed comes off with the body (sprint bonus too)");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_SPEED) - 4.0) < 1.0e-4, "and the fast hands");
		helper.assertFalse(kit.flag(p, Xlr8Kit.F_SPEED), "Speed Mode is off after turning back");
		helper.assertTrue(p.getAttributeValue(Attributes.STEP_HEIGHT) < 0.6 + 1.0e-4, "and its step assist");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void xlr8MovesHitAndCoolDown(GameTestHelper helper) {
		ServerPlayer p = as(helper, Xlr8Kit.ID);
		Xlr8Kit kit = (Xlr8Kit) Aliens.kit(Xlr8Kit.ID);
		Zombie z = zombie(helper, 3.5, 2, 5.5, 120f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 10f);
		helper.assertTrue(AlienKit.lookTarget(p, 14) == z, "the zombie is in sight");
		// v0.17.1 revamp: G is the plain Speedster's Blitz (borrowed from SuperSpeedMoves); v0.17.2: on his own 14
		helper.assertTrue(kit.blitz(p), "Blitz");
		helper.assertTrue(near(z.getHealth(), 120f - Xlr8Kit.BLITZ_HIT), "14 damage: " + z.getHealth());
		helper.assertFalse(kit.blitz(p), "then cools down");
		helper.assertTrue(p.distanceTo(z) < 2.5, "he closed the gap");
		z.moveTo(p.getX(), p.getY(), p.getZ() + 2.0, 180f, 0f);
		z.setDeltaMovement(Vec3.ZERO);
		float before = z.getHealth();
		z.invulnerableTime = 0;
		helper.assertTrue(kit.whirlwind(p), "Whirlwind");
		helper.assertTrue(near(before - z.getHealth(), Xlr8Kit.WHIRL_DAMAGE), "8 damage");
		helper.assertFalse(kit.whirlwind(p), "it cools down");
		helper.assertTrue(kit.timeBurst(p), "Time Burst");
		var slow = z.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		helper.assertTrue(slow != null && slow.getAmplifier() >= 5 && slow.getDuration() > Xlr8Kit.BURST_TICKS - 5, "the zombie crawls");
		helper.assertTrue(kit.cooldownLeft(p, Xlr8Kit.BURST) > 500, "a long cooldown");
		// the snatch
		z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		helper.assertTrue(kit.onUseEntity(p, InteractionHand.MAIN_HAND, z) == net.minecraft.world.InteractionResult.SUCCESS && z.getMainHandItem().isEmpty() && p.getInventory().contains(new ItemStack(Items.IRON_SWORD)), "the sword is his");
		// Speed Mine: a stone wall ahead
		p.moveTo(helper.absoluteVec(new Vec3(3.5, 2, 1.5)).x, p.getY(), helper.absoluteVec(new Vec3(3.5, 2, 1.5)).z, 0f, 0f);
		for (int i = 1; i <= 4; i++) {
			helper.getLevel().setBlock(p.blockPosition().south(i), Blocks.STONE.defaultBlockState(), 3);
			helper.getLevel().setBlock(p.blockPosition().south(i).above(), Blocks.DIRT.defaultBlockState(), 3);
		}
		helper.getLevel().setBlock(p.blockPosition().south(5), Blocks.OBSIDIAN.defaultBlockState(), 3); // too hard: the tunnel stops here
		z.moveTo(p.getX() + 2.5, p.getY(), p.getZ(), 180f, 0f);
		int mined = kit.speedMine(p);
		helper.assertTrue(mined == 8 && helper.getLevel().getBlockState(p.blockPosition().south(2)).isAir(), "the tunnel is mined: " + mined);
		helper.assertTrue(p.getInventory().countItem(Items.COBBLESTONE) == 4 && p.getInventory().countItem(Items.DIRT) == 4, "the drops are in his pockets");
		helper.assertTrue(kit.speedMine(p) == 0, "it cools down");
		// the flurry and the kicks over ticks, then the tornado and the dash
		z.moveTo(p.getX(), p.getY(), p.getZ() + 1.8, 180f, 0f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 10f);
		float hp = z.getHealth();
		z.invulnerableTime = 0;
		helper.assertTrue(kit.rapidAssault(p) == 1, "Rapid Assault hits the zombie");
		helper.assertTrue(hp - z.getHealth() >= Xlr8Kit.ASSAULT_PUNCH * Xlr8Kit.ASSAULT_PUNCHES - 0.1f, "five punches of 2.5: " + (hp - z.getHealth()));
		float afterAssault = z.getHealth();
		helper.assertTrue(kit.kickBarrage(p), "Kick Barrage");
		p.setDeltaMovement(Vec3.ZERO);
		pump(helper, p);
		helper.runAfterDelay(18, () -> {
			helper.assertTrue(z.getHealth() < afterAssault - Xlr8Kit.KICK_HIT * 4, "the kicks landed many hits: " + (afterAssault - z.getHealth()));
			helper.assertTrue(kit.cooldownLeft(p, Xlr8Kit.ASSAULT) > 0 && kit.cooldownLeft(p, Xlr8Kit.KICKS) > 0, "both cool down");
			helper.assertTrue(kit.tornadoRun(p) && kit.flag(p, Xlr8Kit.F_TORNADO), "Tornado Run");
			helper.assertTrue(kit.blurDash(p) && kit.flag(p, Xlr8Kit.F_DASH), "Blur Dash");
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.assertFalse(kit.flag(p, Xlr8Kit.F_TORNADO) || kit.flag(p, Xlr8Kit.F_DASH), "turning back stops the tornado and the dash");
			z.discard();
			helper.succeed();
		});
	}

	// ================================================================ Diamondhead

	@GameTest(template = EMPTY_STRUCTURE)
	public void diamondheadIsArmouredSlowAndReflects(GameTestHelper helper) {
		ServerPlayer p = as(helper, DiamondheadKit.ID);
		DiamondheadKit kit = (DiamondheadKit) Aliens.kit(DiamondheadKit.ID);
		helper.assertTrue(near(p.getMaxHealth(), 34f), "34 health");
		helper.assertTrue(p.getAttributeValue(Attributes.ARMOR) >= 12 - 1.0e-6, "heavy armour");
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) < 0.1, "a little slow");
		helper.assertTrue(Math.abs(p.getScale() - 1.1f) < 0.01f, "a touch bigger");
		Zombie archer = zombie(helper, 3.5, 2, 6.5, 40f);
		Arrow a = new Arrow(EntityType.ARROW, helper.getLevel());
		a.setOwner(archer);
		a.moveTo(p.getX(), p.getY() + 1.2, p.getZ() + 2.0);
		a.setDeltaMovement(0, 0, -1.5);
		helper.getLevel().addFreshEntity(a);
		helper.assertTrue(kit.tryReflect(p, a, 0.0f), "a lucky roll reflects");
		helper.assertTrue(a.getOwner() == p && a.getDeltaMovement().z > 0, "the arrow is his now and heads back the way it came");
		Arrow b = new Arrow(EntityType.ARROW, helper.getLevel());
		b.setOwner(archer);
		b.moveTo(p.getX(), p.getY() + 1.2, p.getZ() + 2.0);
		b.setDeltaMovement(0, 0, -1.5);
		helper.assertFalse(kit.tryReflect(p, b, 0.99f), "an unlucky roll does not");
		helper.assertTrue(DiamondheadKit.REFLECT_CHANCE > 0.2f && DiamondheadKit.REFLECT_CHANCE < 0.6f, "a share, not all");
		helper.assertTrue(kit.crystalArmour(p), "Crystal Armour");
		helper.assertTrue(kit.tryReflect(p, b, 0.99f), "under Crystal Armour every projectile glances back");
		float hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().magic(), 10f);
		helper.assertTrue(near(hp - p.getHealth(), 10f * DiamondheadKit.ARMOUR_FACTOR), "and half damage");
		a.discard();
		archer.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void diamondheadsMovesHitAndCoolDown(GameTestHelper helper) {
		ServerPlayer p = as(helper, DiamondheadKit.ID);
		DiamondheadKit kit = (DiamondheadKit) Aliens.kit(DiamondheadKit.ID);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		helper.assertTrue(kit.shardVolley(p), "Shard Volley");
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(AlienProjectileEntity.class, p.getBoundingBox().inflate(4)).size() == 5, "five shards");
		helper.assertFalse(kit.shardVolley(p), "then cools down");
		helper.assertTrue(kit.crystalSpear(p) && kit.cooldownLeft(p, DiamondheadKit.SPEAR) > 0, "Crystal Spear");
		double fist = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		helper.assertTrue(kit.bladeArm(p) && kit.flag(p, DiamondheadKit.F_BLADE), "Blade Arm on");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - fist - DiamondheadKit.BLADE_BONUS) < 1.0e-4, "+6 melee");
		int wall = kit.wall(p);
		helper.assertTrue(wall >= 10, "the wall rose: " + wall);
		helper.assertTrue(helper.getLevel().getBlockState(BlockPos.containing(p.position().add(0, 0.5, 2.5))).is(DiamondheadBlocks.CRYSTAL), "of crystal");
		helper.assertTrue(kit.wall(p) == 0, "the wall cools down");
		helper.assertTrue(kit.dome(p) > 10, "the dome closes");
		BlockPos face = p.blockPosition().west(2);
		helper.assertTrue(kit.placeCrystal(p, face) && helper.getLevel().getBlockState(face).is(DiamondheadBlocks.CRYSTAL), "Use places a crystal block");
		Zombie z = zombie(helper, 3.5, 2, 6.5, 60f);
		// the dome is in the way of the spikes' victim: clear it
		for (BlockPos bp : BlockPos.betweenClosed(p.blockPosition().offset(-3, 0, -3), p.blockPosition().offset(3, 3, 3))) {
			if (helper.getLevel().getBlockState(bp).is(DiamondheadBlocks.CRYSTAL)) {
				helper.getLevel().setBlock(bp, Blocks.AIR.defaultBlockState(), 3);
			}
		}
		helper.assertTrue(kit.spikeLine(p), "Spike Line");
		helper.assertTrue(kit.barrage(p), "Diamond Barrage");
		pump(helper, p);
		helper.runAfterDelay(14, () -> {
			helper.assertTrue(z.getHealth() <= 60f - DiamondheadKit.SPIKE_DAMAGE + 0.05f, "the spikes reached the zombie: " + z.getHealth());
			helper.assertTrue(kit.cooldownLeft(p, DiamondheadKit.SPIKES) > 0, "and cool down");
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - 1.0) < 1.0e-4, "turning back takes the blade off");
			helper.assertFalse(kit.flag(p, DiamondheadKit.F_BLADE), "and the flag");
			z.discard();
			helper.succeed();
		});
	}

	/**
	 * v0.17.2 (user: "Make Shift+G the spike Line ability / Make Z an aoe spike ability"): Shift+G is the Spike Line, Z the new
	 * Crystal Eruption -- rings of spikes all round him that reach a zombie behind him and one beside him (12 each, thrown up,
	 * slowed), once each.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void diamondheadsCrystalEruptionHitsAllAroundAndSpikeLineIsOnShiftG(GameTestHelper helper) {
		ServerPlayer p = as(helper, DiamondheadKit.ID);
		DiamondheadKit kit = (DiamondheadKit) Aliens.kit(DiamondheadKit.ID);
		helper.assertTrue(DiamondheadKit.SPIKES.equals(kit.moveOn(com.projecthero.mod.hero.AbilitySlot.SLOT_2, true, false).id()), "Shift+G is the Spike Line");
		helper.assertTrue(DiamondheadKit.ERUPT.equals(kit.moveOn(com.projecthero.mod.hero.AbilitySlot.SLOT_4, false, false).id()), "Z is the Crystal Eruption");
		helper.assertTrue(DiamondheadKit.BARRAGE.equals(kit.moveOn(com.projecthero.mod.hero.AbilitySlot.SLOT_4, true, false).id()), "Shift+Z is still the Barrage");
		kit.press(p, com.projecthero.mod.hero.AbilitySlot.SLOT_2, true);
		helper.assertTrue(kit.cooldownLeft(p, DiamondheadKit.SPIKES) > 0 && !kit.flag(p, DiamondheadKit.F_BLADE), "Shift+G runs the Spike Line, not the blade");
		Vec3 mid = helper.absoluteVec(new Vec3(3.5, 2, 3.5));
		p.moveTo(mid.x, mid.y, mid.z, 0f, 0f); // facing +z
		Zombie behind = zombie(helper, 3.5, 2, 0.5, 60f);
		Zombie beside = zombie(helper, 7.0, 2, 3.5, 60f);
		kit.press(p, com.projecthero.mod.hero.AbilitySlot.SLOT_4, false);
		helper.assertTrue(kit.cooldownLeft(p, DiamondheadKit.ERUPT) > DiamondheadKit.ERUPT_COOLDOWN - 5, "Z: the Crystal Eruption, on cooldown");
		helper.assertFalse(kit.eruption(p), "and not again until it cools");
		pump(helper, p);
		helper.runAfterDelay(DiamondheadKit.ERUPT_RINGS + 4, () -> {
			helper.assertTrue(near(behind.getHealth(), 60f - DiamondheadKit.ERUPT_DAMAGE), "the zombie behind him: 12, once: " + behind.getHealth());
			helper.assertTrue(near(beside.getHealth(), 60f - DiamondheadKit.ERUPT_DAMAGE), "the one beside him (3.5 out): " + beside.getHealth());
			helper.assertTrue(behind.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and slowed");
			behind.discard();
			beside.discard();
			helper.succeed();
		});
	}

	// ================================================================ Upgrade

	/** v0.17.2: the Crystal Slide is the Ice Slide in crystal -- a toggle, a ramp grows under him, X again steps off. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void diamondheadsCrystalSlideIsAToggleRideOnARamp(GameTestHelper helper) {
		ServerPlayer p = as(helper, DiamondheadKit.ID);
		DiamondheadKit kit = (DiamondheadKit) Aliens.kit(DiamondheadKit.ID);
		Vec3 up = helper.absoluteVec(new Vec3(3.5, 4, 3.5));
		p.moveTo(up.x, up.y, up.z, 0f, 0f);
		p.setOnGround(false);
		double step = p.getAttributeValue(Attributes.STEP_HEIGHT); // his body's own step-up (above a human's)
		helper.assertTrue(kit.slide(p) == 1 && kit.flag(p, DiamondheadKit.F_SLIDING), "X starts the slide");
		helper.assertTrue(p.getAttributeValue(Attributes.STEP_HEIGHT) > step + 0.5, "the slide steps up a block");
		pump(helper, p);
		helper.runAfterDelay(3, () -> {
			BlockPos under = BlockPos.containing(p.getX(), p.getY() - 0.6, p.getZ());
			helper.assertTrue(helper.getLevel().getBlockState(under).is(com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadBlocks.CRYSTAL),
					"crystal grows in under him");
			helper.assertTrue(kit.flag(p, DiamondheadKit.F_SLIDING), "and he keeps riding (no time limit)");
			helper.assertTrue(kit.slide(p) == 0 && !kit.flag(p, DiamondheadKit.F_SLIDING), "X again steps off");
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT) - step) < 1.0e-4, "the slide's step-up goes with it");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void upgradesSlideShrinksAndOnlyStandsWhereThereIsRoom(GameTestHelper helper) {
		ServerPlayer p = as(helper, UpgradeKit.ID);
		UpgradeKit kit = (UpgradeKit) Aliens.kit(UpgradeKit.ID);
		helper.assertTrue(near(p.getMaxHealth(), 28f), "28 health");
		helper.assertTrue(kit.liquidSlide(p) && kit.flag(p, UpgradeKit.F_SLIDE), "Liquid Slide");
		helper.assertTrue(p.getBbHeight() < 1.0f && p.getBbWidth() < 0.3f, "a puddle: fits under a 1-block gap (" + p.getBbHeight() + ")");
		p.setDeltaMovement(Vec3.ZERO);
		// a ceiling one block up: he cannot stand there
		BlockPos ceiling = p.blockPosition().above();
		helper.getLevel().setBlock(ceiling, Blocks.STONE.defaultBlockState(), 3);
		pump(helper, p);
		helper.runAfterDelay(UpgradeKit.SLIDE_TICKS + 6, () -> {
			helper.assertTrue(kit.flag(p, UpgradeKit.F_SLIDE) && p.getBbHeight() < 1.0f, "under the ceiling he stays a puddle");
			helper.getLevel().setBlock(ceiling, Blocks.AIR.defaultBlockState(), 3);
			helper.runAfterDelay(8, () -> {
				helper.assertFalse(kit.flag(p, UpgradeKit.F_SLIDE), "room again: he stands up");
				helper.assertTrue(Math.abs(p.getBbHeight() - 1.8f) < 0.01f, "full height again");
				// a slide cut short by turning back leaves no small body behind
				helper.assertTrue(kit.liquidSlide(p) || kit.cooldownLeft(p, UpgradeKit.SLIDE) > 0, "slide again");
				kit.cooldown(p, UpgradeKit.SLIDE, 0);
				kit.liquidSlide(p);
				Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
				helper.assertTrue(Math.abs(p.getBbHeight() - 1.8f) < 0.01f && Math.abs(p.getScale() - 1f) < 1.0e-4, "turning back: full size");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void upgradeMergesWithAFurnaceAndPilotsAGolem(GameTestHelper helper) {
		ServerPlayer p = as(helper, UpgradeKit.ID);
		UpgradeKit kit = (UpgradeKit) Aliens.kit(UpgradeKit.ID);
		BlockPos furnace = helper.absolutePos(new BlockPos(3, 2, 4));
		helper.getLevel().setBlock(furnace, Blocks.FURNACE.defaultBlockState(), 3);
		helper.assertTrue(kit.mergeWith(p, furnace), "merged with the furnace");
		helper.assertTrue(UpgradeKit.merge(p) != null && UpgradeKit.merge(p).kind() == UpgradeKit.MergeKind.FURNACE && kit.flag(p, UpgradeKit.F_MERGED),
				"inside the furnace");
		helper.assertTrue(p.hasEffect(MobEffects.INVISIBILITY), "unseen");
		helper.assertFalse(kit.mergeWith(p, furnace), "only one merge at a time");
		kit.endMerge(p, false);
		helper.assertTrue(UpgradeKit.merge(p) == null && !p.hasEffect(MobEffects.INVISIBILITY) && !kit.flag(p, UpgradeKit.F_MERGED), "out again, seen");
		// the golem
		IronGolem golem = EntityType.IRON_GOLEM.create(helper.getLevel());
		Vec3 g = helper.absoluteVec(new Vec3(3.5, 2, 3.5));
		golem.moveTo(g.x, g.y, g.z, 0f, 0f);
		golem.setPersistenceRequired();
		helper.getLevel().addFreshEntity(golem);
		kit.cooldown(p, UpgradeKit.MERGE, 0);
		helper.assertTrue(kit.mergeWith(p, golem), "merged with the golem");
		float hp = p.getHealth();
		float ghp = golem.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().magic(), 6f);
		helper.assertTrue(p.getHealth() == hp && golem.getHealth() < ghp, "inside the golem: it takes the hit");
		pump(helper, p);
		helper.runAfterDelay(3, () -> {
			p.teleportTo(p.getX() + 1.5, p.getY(), p.getZ());
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(golem.distanceTo(p) < 0.5, "the golem goes where he goes");
				Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
				helper.assertTrue(UpgradeKit.merge(p) == null && !p.hasEffect(MobEffects.INVISIBILITY), "turning back leaves the merge, seen again");
				golem.discard();
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void upgradesMovesHitCoolDownAndJamRedstone(GameTestHelper helper) {
		ServerPlayer p = as(helper, UpgradeKit.ID);
		UpgradeKit kit = (UpgradeKit) Aliens.kit(UpgradeKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 4.5, 100f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 10f);
		helper.assertTrue(kit.opticBeam(p), "Optic Beam");
		helper.assertTrue(near(z.getHealth(), 100f - UpgradeKit.BEAM_DAMAGE), "12 damage");
		helper.assertFalse(kit.opticBeam(p), "then cools down");
		helper.assertTrue(kit.techGrab(p, z), "Tech Grab");
		var slow = z.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		helper.assertTrue(slow != null && slow.getAmplifier() >= 9, "stunned");
		float before = z.getHealth();
		z.invulnerableTime = 0;
		helper.assertTrue(kit.fireChargedBeam(p) >= 1 && near(before - z.getHealth(), UpgradeKit.CHARGED_DAMAGE), "the charged beam pierces for 18");
		// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): the EMP falls off
		helper.assertTrue(near(UpgradeKit.empDamage(0), UpgradeKit.EMP_CENTER) && near(UpgradeKit.empDamage(UpgradeKit.EMP_RADIUS), UpgradeKit.EMP_EDGE)
				&& near(UpgradeKit.empDamage(UpgradeKit.EMP_RADIUS / 2), 21f), "EMP: 26 at the centre, 21 halfway, 16 at the edge");
		// Upgrade Tool
		ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
		pick.setDamageValue(200);
		p.setItemInHand(InteractionHand.MAIN_HAND, pick);
		helper.assertTrue(kit.upgradeTool(p) && p.getMainHandItem().getDamageValue() == 200 - pick.getMaxDamage() / 2, "half the wear repaired");
		ItemStack fresh = new ItemStack(Items.IRON_PICKAXE);
		p.setItemInHand(InteractionHand.MAIN_HAND, fresh);
		kit.cooldown(p, UpgradeKit.REPAIR, 0);
		var unbreaking = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING);
		helper.assertTrue(kit.upgradeTool(p) && EnchantmentHelper.getItemEnchantmentLevel(unbreaking, p.getMainHandItem()) == 1, "a whole tool gains Unbreaking");
		helper.assertFalse(kit.upgradeTool(p), "it cools down");
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		// the EMP jams redstone: a lamp lit by a redstone block goes dark, then comes back
		BlockPos lamp = helper.absolutePos(new BlockPos(6, 2, 6));
		helper.getLevel().setBlock(lamp, Blocks.REDSTONE_LAMP.defaultBlockState(), 3);
		helper.getLevel().setBlock(lamp.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
		helper.assertTrue(helper.getLevel().getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "the lamp is lit");
		UpgradeEmp.jam(helper.getLevel(), lamp, 2, 20);
		helper.assertTrue(UpgradeEmp.jammed(helper.getLevel(), lamp.east()), "the redstone block is jammed");
		helper.runAfterDelay(10, () -> {
			helper.assertFalse(helper.getLevel().getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "jammed: the lamp goes dark");
			helper.runAfterDelay(20, () -> {
				helper.assertFalse(UpgradeEmp.jammed(helper.getLevel(), lamp.east()), "the jam lapses");
				helper.assertTrue(helper.getLevel().getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "and the lamp lights again");
				z.discard();
				helper.succeed();
			});
		});
	}

	// ================================================================ Ghostfreak

	@GameTest(template = EMPTY_STRUCTURE)
	public void ghostfreakFloatsAndHalvesPhysicalBlows(GameTestHelper helper) {
		ServerPlayer p = as(helper, GhostfreakKit.ID);
		helper.assertTrue(near(p.getMaxHealth(), 24f), "24 health");
		helper.assertTrue(p.getAttributeValue(Attributes.GRAVITY) < 0.08 * 0.3, "he floats down slowly");
		Zombie z = zombie(helper, 3.5, 2, 3.5, 20f);
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true); // no difficulty scaling on the blow
		float hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().mobAttack(z), 8f);
		float lost = hp - p.getHealth();
		helper.assertTrue(lost > 0f && lost < 8f * 0.6f, "a blow does half: " + lost);
		hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().magic(), 4f);
		helper.assertTrue(near(hp - p.getHealth(), 4f), "magic does it all");
		hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().drown(), 4f);
		helper.assertTrue(p.getHealth() == hp, "he never drowns");
		helper.assertTrue(GhostfreakKit.physical(helper.getLevel().damageSources().fall()) && !GhostfreakKit.physical(helper.getLevel().damageSources().inFire()),
				"a fall is physical, fire is not");
		z.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void ghostfreaksPhaseIsTimedAndLeavesNothingBehind(GameTestHelper helper) {
		ServerPlayer p = as(helper, GhostfreakKit.ID);
		GhostfreakKit kit = (GhostfreakKit) Aliens.kit(GhostfreakKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 3.5, 20f);
		helper.assertTrue(kit.phase(p) && kit.phasing(p) && AlienKit.activeKit(p).phasing(p), "intangible");
		float hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().mobAttack(z), 8f);
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().inWall(), 2f);
		helper.assertTrue(p.getHealth() == hp, "nothing physical touches him (walls neither)");
		pump(helper, p);
		helper.runAfterDelay(GhostfreakKit.PHASE_TICKS + 3, () -> {
			helper.assertFalse(kit.phasing(p), "the phase runs out after 5 s");
			helper.assertTrue(kit.cooldownLeft(p, GhostfreakKit.PHASE) > GhostfreakKit.PHASE_COOLDOWN - 10, "and then cools down");
			// phased and faded, then turned back: nothing stuck
			kit.cooldown(p, GhostfreakKit.PHASE, 0);
			helper.assertTrue(kit.phase(p) && kit.fade(p) && p.hasEffect(MobEffects.INVISIBILITY), "phased and invisible");
			helper.assertTrue(kit.toggleFlight(p) && p.getAbilities().mayfly, "flying");
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.assertFalse(kit.phasing(p), "no stuck intangibility");
			helper.assertTrue(AlienKit.activeKit(p) == null, "human again");
			helper.assertFalse(p.hasEffect(MobEffects.INVISIBILITY), "no stuck invisibility");
			helper.assertTrue(Math.abs(p.getAbilities().getFlyingSpeed() - 0.05f) < 1.0e-4f, "no stuck ghost flight (the mock player is creative, so mayfly itself stays)");
			z.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void ghostfreaksMovesHitScareAndPossess(GameTestHelper helper) {
		ServerPlayer p = as(helper, GhostfreakKit.ID);
		GhostfreakKit kit = (GhostfreakKit) Aliens.kit(GhostfreakKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 3.5, 100f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 15f);
		helper.assertTrue(kit.claw(p) == 1 && near(z.getHealth(), 100f - GhostfreakKit.CLAW_DAMAGE), "Spectral Claw: 12");
		helper.assertTrue(z.hasEffect(MobEffects.WITHER), "it withers");
		helper.assertTrue(kit.claw(p) == 0, "then cools down");
		z.invulnerableTime = 0;
		helper.assertTrue(kit.scream(p) == 1 && GhostfreakKit.fleeing(p, z), "Scream: it flees");
		helper.assertTrue(kit.scream(p) == 0, "the scream cools down");
		// possession: a second zombie fights for him
		Zombie host = zombie(helper, 5.5, 2, 1.5, 40f);
		helper.assertTrue(kit.possess(p, host) && GhostfreakKit.possessed(p) == host && kit.flag(p, GhostfreakKit.F_POSSESS), "possessed");
		helper.assertFalse(kit.possess(p, z), "possession cools down");
		pump(helper, p);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(host.getTarget() != p, "the host never turns on him");
			helper.assertTrue(host.getTarget() == z, "it goes for the other zombie: " + host.getTarget());
			helper.assertTrue(kit.tendrils(p) && kit.haunt(p) >= 1, "Shadow Tendrils and Haunt");
			helper.assertTrue(z.hasEffect(MobEffects.DARKNESS), "dread");
			helper.assertTrue(kit.frightAura(p) && kit.flag(p, GhostfreakKit.F_AURA), "Fright Aura");
			float hp = p.getHealth() - 4f;
			p.setHealth(hp);
			helper.assertTrue(kit.siphon(p, z) && p.getHealth() > hp, "Soul Siphon heals him");
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.assertTrue(GhostfreakKit.possessed(p) == null && host.getTarget() == null, "turning back frees the host");
			helper.assertFalse(GhostfreakKit.fleeing(p, z), "and the fear lifts");
			z.discard();
			host.discard();
			helper.succeed();
		});
	}
}
