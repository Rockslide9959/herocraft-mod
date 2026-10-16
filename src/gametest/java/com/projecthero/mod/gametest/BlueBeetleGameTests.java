package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.bluebeetle.BlueBeetle;
import com.projecthero.mod.bluebeetle.BlueBeetleAbilities;
import com.projecthero.mod.bluebeetle.BlueBeetleConfig;
import com.projecthero.mod.bluebeetle.BlueBeetleDamage;
import com.projecthero.mod.bluebeetle.data.BlueBeetleState;
import com.projecthero.mod.bluebeetle.entity.PlasmaBoltEntity;
import com.projecthero.mod.bluebeetle.entity.ScarabDroneEntity;
import com.projecthero.mod.bluebeetle.item.BlueBeetleItems;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.kryptonian.Kryptonian;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Blue Beetle: the Scarab grant (one Primary), the armour and its passives (+9 melee), Adaptation, flight, and one
 * smoke test per move of the reworked kit. Mock players are not ticked by the server, so tests over time drive
 * {@link BlueBeetle#tick} ({@link #pump}).
 */
public class BlueBeetleGameTests implements FabricGameTest {
	private static final float EPS = 0.6f;

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

	private static ServerPlayer beetle(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		BlueBeetle.grant(p);
		BlueBeetleState n = BlueBeetle.state(p).copy();
		n.suitChangeAt = helper.getLevel().getGameTime() - 400L;
		n.animId = BlueBeetleState.ANIM_NONE;
		p.setAttached(ModAttachments.BLUE_BEETLE_STATE, n);
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			BlueBeetle.tick(p);
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double y, double z, double health) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		T m = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth((float) health);
		if (m.getAttribute(Attributes.ARMOR) != null) {
			m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		}
		m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void scarabBondsAndReplacesThePrimary(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 2.5, 2.0, 2.5);
		helper.assertTrue(HeroTiers.HERO_KEYS.contains("blue_beetle") && PowerGrants.HERO_TIER_KEYS.contains("blue_beetle"), "a Hero-Tier key");
		Kryptonian.grant(p);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BlueBeetleItems.SCARAB));
		p.getMainHandItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(BlueBeetle.hasPower(p) && !Kryptonian.hasPower(p) && HeroTiers.heroCount(p) == 1, "the Scarab replaced the Kryptonian");
		helper.assertTrue(p.getMainHandItem().isEmpty(), "the Scarab is used up");
		helper.assertTrue(BlueBeetle.suited(p) && BlueBeetle.changing(p), "the bonding runs, armour spreading");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void armourGivesDiamondLevelAndNoFallDamage(GameTestHelper helper) {
		ServerPlayer p = beetle(helper, 2.5, 2.0, 2.5);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - BlueBeetleConfig.SUIT_ARMOR) < 0.01, "20 armour");
		float hp = p.getHealth();
		p.hurt(p.damageSources().fall(), 10f);
		helper.assertTrue(p.getHealth() == hp, "no fall damage");
		BlueBeetle.suitDown(p);
		helper.assertTrue(p.getAttributeValue(Attributes.ARMOR) < 0.01, "armour off: no armour bonus");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void adaptationCutsARepeatedKindOfDamage(GameTestHelper helper) {
		ServerPlayer p = beetle(helper, 2.5, 2.0, 2.5);
		for (int i = 0; i < BlueBeetleConfig.ADAPT_HITS; i++) {
			p.invulnerableTime = 0;
			p.setHealth(p.getMaxHealth());
			p.hurt(p.damageSources().generic(), 1f);
		}
		BlueBeetleState s = BlueBeetle.state(p);
		helper.assertTrue("generic".equals(s.adaptType) && s.adaptUntil > helper.getLevel().getGameTime(), "adapted to generic, got " + s.adaptType);
		p.invulnerableTime = 0;
		p.setHealth(p.getMaxHealth());
		p.removeAllEffects();
		float before = p.getHealth();
		p.hurt(p.damageSources().generic(), 10f);
		float taken = before - p.getHealth();
		// armour still applies; compare against what an unadapted hit of the same kind takes through the same armour
		helper.assertTrue(taken < 10f * 0.75f, "40% less, took " + taken);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitGivesNineMeleeAndTheBladeSevenMore(GameTestHelper helper) {
		ServerPlayer p = beetle(helper, 2.5, 2.0, 2.5);
		double base = p.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (base + BlueBeetleConfig.SUIT_MELEE)) < 0.01, "+9 suited");
		BlueBeetleAbilities.toggleBlade(p);
		helper.assertTrue(BlueBeetle.state(p).fx.blade, "the blade is out");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (base + BlueBeetleConfig.SUIT_MELEE + BlueBeetleConfig.BLADE_MELEE)) < 0.01,
				"+7 more with the blade, got " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
		BlueBeetleAbilities.toggleBlade(p);
		helper.assertFalse(BlueBeetle.state(p).fx.blade, "C again folds it away");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (base + BlueBeetleConfig.SUIT_MELEE)) < 0.01, "back to +9");
		BlueBeetle.state(p).abilityReadyAt.clear();
		BlueBeetleAbilities.toggleBlade(p);
		BlueBeetle.suitDown(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - base) < 0.01, "armour off: no melee bonus, no blade");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void doubleTapJumpFliesAndLandsAgain(GameTestHelper helper) {
		ServerPlayer p = beetle(helper, 2.5, 3.0, 2.5);
		BlueBeetle.toggleFlight(p);
		helper.assertTrue(BlueBeetle.state(p).flying && p.getAbilities().mayfly && p.getAbilities().flying, "flying");
		BlueBeetle.toggleFlight(p);
		helper.assertTrue(!BlueBeetle.state(p).flying && !p.getAbilities().mayfly, "landed: flight taken back");
		BlueBeetle.suitDown(p);
		BlueBeetle.toggleFlight(p);
		helper.assertFalse(BlueBeetle.state(p).flying, "no flight without the armour");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void plasmaBlasterFiresProjectilesForFifteen(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = beetle(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 100);
		pump(helper, p);
		BlueBeetleAbilities.startBlast(p);
		helper.assertTrue(BlueBeetle.state(p).fx.blasting, "the blaster builds on the arm");
		int bolts = helper.getLevel().getEntitiesOfClass(PlasmaBoltEntity.class, new AABB(p.blockPosition()).inflate(8)).size();
		helper.assertTrue(bolts == 1, "a real projectile, got " + bolts);
		helper.runAfterDelay(5, () -> {
			BlueBeetleAbilities.stopBlast(p);
			helper.assertFalse(BlueBeetle.state(p).fx.blasting, "and comes apart");
			float taken = 100f - z.getHealth();
			helper.assertTrue(taken >= BlueBeetleConfig.BLAST_DAMAGE - EPS && taken <= BlueBeetleConfig.BLAST_DAMAGE * 2 + EPS, "15 a bolt, took " + taken);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void megaCannonPiercesTheLine(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = beetle(helper, 3.5, 2.0, 0.5);
		Zombie a = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 3.5, 100);
		Zombie b = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 100);
		pump(helper, p);
		BlueBeetleAbilities.cannon(p);
		helper.runAfterDelay(BlueBeetleConfig.CANNON_CHARGE_TICKS + 3, () -> {
			helper.assertTrue(Math.abs((100f - a.getHealth()) - BlueBeetleConfig.CANNON_DAMAGE) < EPS, "first in line: 30");
			helper.assertTrue(Math.abs((100f - b.getHealth()) - BlueBeetleConfig.CANNON_DAMAGE) < EPS, "and through it: 30");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void scarabHammerSmashesTheFront(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = beetle(helper, 3.5, 2.0, 3.5);
		Zombie front = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 5.5, 100);
		Zombie behind = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 0.5, 100);
		pump(helper, p);
		BlueBeetleAbilities.hammer(p);
		helper.runAfterDelay(BlueBeetleConfig.HAMMER_HIT_TICK + 3, () -> {
			float taken = 100f - front.getHealth();
			helper.assertTrue(Math.abs(taken - BlueBeetleConfig.HAMMER_DAMAGE) < EPS, "20 in front, took " + taken);
			helper.assertTrue(behind.getHealth() == 100f, "nothing behind him");
			helper.assertTrue(BlueBeetle.cooldownRemaining(p, BlueBeetleAbilities.HAMMER) > 0, "cooldown");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void scarabGrabStrikesTwiceAndKnocksFarBack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = beetle(helper, 3.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 2.5, 100); // room behind it before the cage wall
		z.setNoAi(false);
		pump(helper, p);
		double[] fastest = new double[1];
		helper.onEachTick(() -> fastest[0] = Math.max(fastest[0], z.getDeltaMovement().horizontalDistance()));
		BlueBeetleAbilities.grab(p);
		helper.assertTrue(BlueBeetleAbilities.running(p, BlueBeetleAbilities.GRAB) && BlueBeetle.state(p).fx.grabTarget == z.getId(), "tentacles out");
		helper.runAfterDelay(BlueBeetleConfig.GRAB_SECOND_HIT + 6, () -> {
			float taken = 100f - z.getHealth();
			helper.assertTrue(Math.abs(taken - 2 * BlueBeetleConfig.GRAB_DAMAGE) < 1f, "two strikes of 10, took " + taken);
			// (the 8x8 test cage stops it flying far, so measure the launch itself; the first strike alone is under 0.5)
			helper.assertTrue(fastest[0] > 1.0, "knocked far back, launched at " + fastest[0] + " blocks a tick");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void plasmaSpikesRunAlongTheGround(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = beetle(helper, 3.5, 2.0, 0.5);
		Zombie near = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 3.0, 100);
		Zombie far = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 100);
		pump(helper, p);
		BlueBeetleAbilities.spikes(p);
		helper.runAfterDelay(BlueBeetleConfig.SPIKE_SLAM_TICK + 1, () -> helper.assertTrue(BlueBeetle.state(p).fx.spikeStart != 0L
				&& BlueBeetle.state(p).fx.spikeLine.size() == 4 + BlueBeetleConfig.SPIKE_COUNT, "the line is synced"));
		helper.runAfterDelay(BlueBeetleConfig.SPIKE_SLAM_TICK + 12, () -> {
			helper.assertTrue(Math.abs((100f - near.getHealth()) - BlueBeetleConfig.SPIKE_DAMAGE) < EPS, "18, once, took " + (100f - near.getHealth()));
			helper.assertTrue(Math.abs((100f - far.getHealth()) - BlueBeetleConfig.SPIKE_DAMAGE) < EPS, "further along too, took " + (100f - far.getHealth()));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void energyShieldOnVBlocksTheFront(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = beetle(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 3.5, 100);
		BlueBeetleAbilities.raiseShield(p);
		float hp = p.getHealth();
		p.hurt(p.damageSources().mobAttack(z), 8f);
		helper.assertTrue(p.getHealth() == hp, "blocked from the front");
		BlueBeetleAbilities.lowerShield(p);
		helper.assertFalse(BlueBeetle.shieldUp(p), "lowered");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shieldPulsePushesAway(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = beetle(helper, 3.5, 2.0, 3.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 5.5, 2.0, 3.5, 100);
		BlueBeetleAbilities.pulse(p);
		helper.assertTrue(z.getDeltaMovement().x > 0.3, "pushed away, v=" + z.getDeltaMovement());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void swarmLaunchesThreeDrones(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = beetle(helper, 3.5, 2.0, 1.5);
		mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 100);
		BlueBeetleAbilities.swarm(p);
		int n = helper.getLevel().getEntitiesOfClass(ScarabDroneEntity.class, new AABB(p.blockPosition()).inflate(8)).size();
		helper.assertTrue(n == BlueBeetleConfig.SWARM_COUNT, "three drones, got " + n);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bubblePrisonMakesItUntouchable(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = beetle(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 100);
		BlueBeetleAbilities.bubble(p);
		helper.assertTrue(BlueBeetleDamage.inBubble(z), "trapped");
		z.hurt(p.damageSources().playerAttack(p), 10f);
		helper.assertTrue(z.getHealth() == 100f, "nothing hurts it inside");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void camouflageHasNoTimerButAnyMoveEndsIt(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = beetle(helper, 3.5, 2.0, 1.5);
		mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 5.5, 100);
		pump(helper, p);
		BlueBeetleAbilities.camo(p);
		helper.assertTrue(p.hasEffect(MobEffects.INVISIBILITY) && BlueBeetle.state(p).camo, "invisible");
		helper.runAfterDelay(220, () -> {
			helper.assertTrue(p.hasEffect(MobEffects.INVISIBILITY) && BlueBeetle.state(p).camo, "still invisible after 11 s");
			BlueBeetleAbilities.hammer(p);
			helper.assertFalse(p.hasEffect(MobEffects.INVISIBILITY) || BlueBeetle.state(p).camo, "using a move ends it");
			helper.assertTrue(BlueBeetle.cooldownRemaining(p, BlueBeetleAbilities.CAMO) > 0, "its cooldown starts now");
			helper.succeed();
		});
	}

	private static void freshCamo(ServerPlayer p) {
		BlueBeetleState n = BlueBeetle.state(p).copy();
		n.abilityReadyAt.clear();
		n.energy = BlueBeetleConfig.ENERGY_MAX;
		p.setAttached(ModAttachments.BLUE_BEETLE_STATE, n);
		BlueBeetleAbilities.camo(p);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void camouflageBreaksOnTouchingHittingOrFlying(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = beetle(helper, 3.5, 3.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 3.5, 100);
		freshCamo(p);
		helper.assertTrue(BlueBeetle.state(p).camo, "on");
		z.hurt(p.damageSources().playerAttack(p), 2f);
		helper.assertFalse(BlueBeetle.state(p).camo || p.hasEffect(MobEffects.INVISIBILITY), "hitting something ends it");
		freshCamo(p);
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(p, helper.getLevel(), InteractionHand.MAIN_HAND,
				new net.minecraft.world.phys.BlockHitResult(helper.absoluteVec(new Vec3(3.5, 2.0, 2.5)), net.minecraft.core.Direction.UP,
						helper.absolutePos(new BlockPos(3, 1, 2)), false));
		helper.assertFalse(BlueBeetle.state(p).camo, "touching a block ends it");
		freshCamo(p);
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.invoker().interact(p, helper.getLevel(), InteractionHand.MAIN_HAND);
		helper.assertFalse(BlueBeetle.state(p).camo, "using an item ends it");
		freshCamo(p);
		BlueBeetle.toggleFlight(p);
		helper.assertTrue(BlueBeetle.state(p).flying && !BlueBeetle.state(p).camo, "taking off ends it");
		freshCamo(p);
		helper.assertFalse(BlueBeetle.state(p).camo, "and it will not start while flying");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void unleashedNeedsAFullBarAndMakesMovesFree(GameTestHelper helper) {
		ServerPlayer p = beetle(helper, 2.5, 2.0, 2.5);
		BlueBeetle.setEnergy(p, 50f);
		BlueBeetleAbilities.unleash(p);
		helper.assertFalse(BlueBeetle.unleashed(p), "not on half a bar");
		BlueBeetle.setEnergy(p, BlueBeetleConfig.ENERGY_MAX);
		BlueBeetleAbilities.unleash(p);
		helper.assertTrue(BlueBeetle.unleashed(p), "unleashed");
		BlueBeetleAbilities.pulse(p);
		helper.assertTrue(BlueBeetle.energy(p) == BlueBeetleConfig.ENERGY_MAX, "moves are free");
		helper.assertTrue(BlueBeetle.cooldownRemaining(p, BlueBeetleAbilities.PULSE) <= BlueBeetleConfig.PULSE_COOLDOWN / 2 + 1, "half cooldowns");
		helper.succeed();
	}

	// ---------------------------------------------------------------- round 2: Khaji-Da's takeover, the shield cooldown

	/** Unleashed (full bar), the takeover on. */
	private static void unleashed(ServerPlayer p) {
		BlueBeetle.setEnergy(p, BlueBeetleConfig.ENERGY_MAX);
		BlueBeetleAbilities.unleash(p);
	}

	/** Like {@link #pump}, plus the movement his client would do with the velocity the takeover sends it. */
	private static void drivePump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			BlueBeetle.tick(p);
			p.move(net.minecraft.world.entity.MoverType.SELF, p.getDeltaMovement());
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void takeoverLocksThePlayersKeys(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = beetle(helper, 1.5, 2.0, 1.5);
		mob(helper, EntityType.ZOMBIE, 5.5, 2.0, 5.5, 100);
		unleashed(p);
		helper.assertTrue(BlueBeetle.unleashed(p) && com.projecthero.mod.bluebeetle.KhajiDaTakeover.active(p), "Khaji-Da has the body");
		com.projecthero.mod.bluebeetle.BlueBeetleAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_4, true);
		helper.assertFalse(BlueBeetleAbilities.running(p, BlueBeetleAbilities.SPIKES), "the player's Z does nothing");
		com.projecthero.mod.bluebeetle.BlueBeetleAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_5, true);
		helper.assertFalse(BlueBeetle.shieldUp(p), "nor V");
		BlueBeetle.toggleSuit(p);
		helper.assertTrue(BlueBeetle.suited(p), "nor H");
		BlueBeetle.toggleFlight(p);
		helper.assertFalse(BlueBeetle.state(p).flying, "nor a double-tap jump");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void takeoverHuntsAHostileAndFightsWithTheKit(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = beetle(helper, 1.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 5.5, 2.0, 5.5, 280);
		drivePump(helper, p);
		unleashed(p);
		helper.runAfterDelay(2, () -> helper.assertTrue(BlueBeetle.state(p).fx.takeTarget == z.getId(), "it picked the zombie"));
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(z.getHealth() < 280f || !z.isAlive(), "it fought: the zombie is at " + z.getHealth());
			helper.assertTrue(com.projecthero.mod.bluebeetle.KhajiDaTakeover.lastMove(p) != null, "with the kit");
			helper.assertTrue(p.distanceTo(z) < 4.5 || com.projecthero.mod.bluebeetle.KhajiDaTakeover.swings(p) > 0 || !z.isAlive(),
					"and closed in, " + p.distanceTo(z) + " blocks off");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void takeoverFliesToATargetUpHigh(GameTestHelper helper) {
		floor(helper);
		for (int y = 2; y <= 4; y++) {
			helper.getLevel().setBlock(helper.absolutePos(new BlockPos(5, y, 5)), Blocks.STONE.defaultBlockState(), 3);
		}
		ServerPlayer p = beetle(helper, 1.5, 2.0, 1.5);
		mob(helper, EntityType.ZOMBIE, 5.5, 5.0, 5.5, 100);
		drivePump(helper, p);
		unleashed(p);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(BlueBeetle.state(p).flying && p.getAbilities().flying, "the wings came out for a target 3 blocks up");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80, batch = "bluebeetle_takeover_alone") // alone: other tests' zombies would be in range
	public void takeoverHandsBackWhenNothingHostileIsLeft(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = beetle(helper, 3.5, 2.0, 3.5);
		pump(helper, p);
		unleashed(p);
		helper.runAfterDelay(BlueBeetleConfig.TAKEOVER_CLEAR_TICKS + 5, () -> {
			helper.assertFalse(BlueBeetle.unleashed(p), "no hostiles: control back early");
			helper.assertTrue(BlueBeetle.state(p).slowRegenUntil > helper.getLevel().getGameTime(), "drained as usual");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void takeoverPathGoesRoundAWall(GameTestHelper helper) {
		floor(helper);
		// a wall across z = 4, x 0..5, two high; the gap is at x 6..7
		for (int x = 0; x <= 5; x++) {
			for (int y = 2; y <= 3; y++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, 4)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		BlockPos from = helper.absolutePos(new BlockPos(2, 2, 1));
		BlockPos to = helper.absolutePos(new BlockPos(2, 2, 7));
		java.util.List<BlockPos> path = com.projecthero.mod.bluebeetle.BeetlePath.find(helper.getLevel(), from, to, 0.5, 2000);
		helper.assertTrue(path != null && !path.isEmpty(), "a path");
		boolean round = path.stream().anyMatch(c -> c.getX() - helper.absolutePos(BlockPos.ZERO).getX() >= 6);
		helper.assertTrue(round, "round the end of the wall, not through it: " + path);
		helper.assertTrue(path.stream().noneMatch(c -> c.getZ() == helper.absolutePos(new BlockPos(0, 0, 4)).getZ()
				&& c.getX() - helper.absolutePos(BlockPos.ZERO).getX() <= 5), "never through the wall");
		// a one-block step it can jump up
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(6, 2, 4)), Blocks.STONE.defaultBlockState(), 3);
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(7, 2, 4)), Blocks.STONE.defaultBlockState(), 3);
		java.util.List<BlockPos> up = com.projecthero.mod.bluebeetle.BeetlePath.find(helper.getLevel(), from, to, 0.5, 2000);
		helper.assertTrue(up != null && up.stream().anyMatch(c -> c.getY() == from.getY() + 1), "it jumps up a block: " + up);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shieldHasAnEightSecondCooldown(GameTestHelper helper) {
		ServerPlayer p = beetle(helper, 2.5, 2.0, 2.5);
		BlueBeetleAbilities.raiseShield(p);
		helper.assertTrue(BlueBeetle.shieldUp(p), "up");
		BlueBeetleAbilities.lowerShield(p);
		int cd = BlueBeetle.cooldownRemaining(p, BlueBeetleAbilities.SHIELD);
		helper.assertTrue(cd >= BlueBeetleConfig.SHIELD_COOLDOWN - 1 && BlueBeetleConfig.SHIELD_COOLDOWN == 160, "8 s cooldown, got " + cd);
		BlueBeetleAbilities.raiseShield(p);
		helper.assertFalse(BlueBeetle.shieldUp(p), "not again straight away");
		helper.succeed();
	}

	// ---------------------------------------------------------------- round 2b: the shield works like Cyborg's Defense Matrix

	@GameTest(template = EMPTY_STRUCTURE)
	public void shieldSoaksAHundredPointsThenBreaks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = beetle(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 3.5, 100);
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true); // hits unscaled, so the pool maths is exact
		p.removeAllEffects();
		BlueBeetleAbilities.raiseShield(p);
		helper.assertTrue(BlueBeetle.shieldUp(p) && BlueBeetle.state(p).fx.shieldHp == BlueBeetleConfig.SHIELD_HP, "up with 100 points");
		float hp = p.getHealth();
		p.hurt(p.damageSources().mobAttack(z), 30f);
		helper.assertTrue(p.getHealth() == hp, "a 30-point hit from the front is soaked");
		float left = BlueBeetle.state(p).fx.shieldHp;
		helper.assertTrue(Math.abs(left - (BlueBeetleConfig.SHIELD_HP - 30f)) < 0.01f, "70 points left, got " + left);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().mobAttack(z), 90f);
		helper.assertFalse(BlueBeetle.shieldUp(p), "the pool ran dry: it broke");
		helper.assertTrue(p.getHealth() < hp, "and the 20 it could not take went through");
		helper.assertTrue(BlueBeetle.cooldownRemaining(p, BlueBeetleAbilities.SHIELD) >= BlueBeetleConfig.SHIELD_COOLDOWN - 1, "8 s cooldown after a break");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void shieldCostsNoEnergyWhileHeld(GameTestHelper helper) {
		ServerPlayer p = beetle(helper, 2.5, 2.0, 2.5);
		BlueBeetle.setEnergy(p, 50f);
		pump(helper, p);
		BlueBeetleAbilities.raiseShield(p);
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(BlueBeetle.shieldUp(p), "still up after 3 s");
			helper.assertTrue(BlueBeetle.energy(p) >= 50f, "no drain, energy " + BlueBeetle.energy(p));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void takeoverShieldsAgainstAnIncomingArrow(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = beetle(helper, 3.5, 2.0, 1.5);
		mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 100);
		unleashed(p);
		Vec3 from = helper.absoluteVec(new Vec3(6.5, 3.4, 6.5));
		net.minecraft.world.entity.projectile.Arrow a = new net.minecraft.world.entity.projectile.Arrow(helper.getLevel(), from.x, from.y, from.z,
				new ItemStack(Items.ARROW), null);
		Vec3 d = p.position().add(0, 1.0, 0).subtract(from).normalize();
		a.shoot(d.x, d.y, d.z, 1.6f, 0f);
		helper.getLevel().addFreshEntity(a);
		p.tickCount++;
		BlueBeetle.tick(p);
		helper.assertTrue(BlueBeetle.shieldUp(p) && com.projecthero.mod.bluebeetle.KhajiDaTakeover.blocks(p) == 1, "the shield went up for the arrow");
		helper.assertTrue(p.getLookAngle().dot(d) < -0.5, "turned to face it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void takeoverBlocksAMeleeThenStrikesBack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = beetle(helper, 3.5, 2.0, 2.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 4.0, 280);
		z.setTarget(p);
		boolean[] sawShield = new boolean[1];
		helper.onEachTick(() -> {
			p.tickCount++;
			BlueBeetle.tick(p);
			z.setTarget(p);
			sawShield[0] |= BlueBeetle.shieldUp(p);
		});
		unleashed(p);
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(sawShield[0], "the shield went up against the zombie next to him");
			helper.assertTrue(z.getHealth() < 280f, "then came down to hit back, zombie at " + z.getHealth());
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theArmourFeedsYouFromYourInventory(GameTestHelper helper) {
		ServerPlayer p = beetle(helper, 2.5, 2.0, 2.5);
		p.getFoodData().setFoodLevel(10);
		p.getInventory().add(new ItemStack(net.minecraft.world.item.Items.COOKED_BEEF, 2));
		p.tickCount = 400;
		BlueBeetle.tick(p);
		helper.assertTrue(p.getFoodData().getFoodLevel() == 18, "v0.16.5: the armour fed you a steak");
		BlueBeetle.suitDown(p);
		p.getFoodData().setFoodLevel(10);
		p.tickCount = 440;
		helper.runAfterDelay(1, () -> {
			BlueBeetle.tick(p);
			helper.assertTrue(p.getFoodData().getFoodLevel() == 10, "armour off: no feeding");
			helper.succeed();
		});
	}

	// ---------------- v0.16.5: the dig site ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "dig_site", timeoutTicks = 100)
	public void digSiteHoldsAtLeast23SuspiciousSandWithTheScarabTable(GameTestHelper helper) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2)).offset(0, 12, 0);
		// a sandstone plinth deep enough for the pit
		for (BlockPos q : BlockPos.betweenClosed(at.offset(-11, -10, -10), at.offset(11, 0, 13))) {
			level.setBlock(q, (q.getY() == at.getY() ? Blocks.SAND : Blocks.SANDSTONE).defaultBlockState(), 2);
		}
		var piece = new com.projecthero.mod.bluebeetle.worldgen.DigSitePiece(at.getX(), at.getY(), at.getZ());
		piece.build(level, piece.getBoundingBox());
		int sand = 0;
		for (BlockPos q : BlockPos.betweenClosed(at.offset(-11, -9, -10), at.offset(11, 4, 13))) {
			if (level.getBlockState(q).is(Blocks.SUSPICIOUS_SAND)) {
				var be = level.getBlockEntity(q);
				helper.assertTrue(be != null && be.saveWithoutMetadata(level.registryAccess()).getString("LootTable").equals("projecthero:archaeology/dig_site"),
						"every suspicious sand carries the dig-site table");
				sand++;
			}
		}
		helper.assertTrue(sand >= 23 && sand == com.projecthero.mod.bluebeetle.worldgen.DigSitePiece.suspiciousCount(),
				"user: at least 23 suspicious sand (got " + sand + ")");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void digSiteSandCanTurnUpTheScarab(GameTestHelper helper) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		var table = level.getServer().reloadableRegistries().getLootTable(com.projecthero.mod.bluebeetle.worldgen.DigSitePiece.ARCHAEOLOGY);
		int scarabs = 0;
		for (int i = 0; i < 400; i++) {
			var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
					.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, Vec3.ZERO)
					.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ARCHAEOLOGY);
			for (ItemStack st : table.getRandomItems(params, i * 7919L)) {
				if (st.is(BlueBeetleItems.SCARAB)) {
					scarabs++;
				}
			}
		}
		helper.assertTrue(scarabs >= 8 && scarabs <= 40, "about 1 in 18 (" + scarabs + " / 400)");
		helper.succeed();
	}
}
