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
}
