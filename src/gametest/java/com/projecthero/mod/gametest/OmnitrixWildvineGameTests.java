package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.wildvine.WildvineKit;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Wildvine (user: "also create a moveset for wildvine please ... do research on his powers and look at what he does in the
 * show"): hidden on the prototype's dial until found, and every key's move does what its line says -- the vine lash and grab, the seed
 * bomb and mines, the swing, the burrow, the coils and snare, Regrowth, the camouflage, Overgrowth -- plus his passives and his fire /
 * frost weaknesses.
 */
public class OmnitrixWildvineGameTests implements FabricGameTest {
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

	/** A stone floor, a prototype wearer who has found Wildvine, transformed into him at (3.5, 2, 1.5) looking +Z. */
	private static ServerPlayer wildvine(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		helper.assertFalse(Omnitrix.select(p, WildvineKit.ID), "Wildvine is hidden until a mistransform finds him");
		Aliens.discover(p, WildvineKit.ID, true);
		helper.assertTrue(Omnitrix.select(p, WildvineKit.ID), "found, he can be dialled");
		helper.assertTrue(Omnitrix.transform(p), "transformed into Wildvine");
		return p;
	}

	private static void tap(ServerPlayer p, AbilitySlot slot, boolean shift) {
		p.setShiftKeyDown(shift);
		AbilityRouter.handleInput(p, slot.number(), true);
		AbilityRouter.handleInput(p, slot.number(), false);
		p.setShiftKeyDown(false);
	}

	private static Zombie zombie(GameTestHelper helper, double x, double y, double z) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static WildvineKit kit() {
		return (WildvineKit) Aliens.kit(WildvineKit.ID);
	}

	private static boolean rooted(Zombie z) {
		MobEffectInstance s = z.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		return s != null && s.getAmplifier() >= 9;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void wildvineIsAHiddenPrototypeAlienWithAMoveOnEveryKey(GameTestHelper helper) {
		helper.assertTrue(Aliens.byId(WildvineKit.ID) != null && !Aliens.byId(WildvineKit.ID).recalibrated(), "declared on the prototype");
		helper.assertTrue(Aliens.hidden(WildvineKit.ID), "found by a mistransform, like Cannonbolt");
		helper.assertTrue(Aliens.dial(false).contains(Aliens.WILDVINE) && !Aliens.dial(true).contains(Aliens.WILDVINE), "prototype only");
		helper.assertTrue(Aliens.WILDMUTT != Aliens.WILDVINE && Aliens.kit(Aliens.WILDMUTT.id()) != null, "Wildmutt is still his own alien");
		AlienKit k = kit();
		for (AbilitySlot s : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5,
				AbilitySlot.SLOT_6 }) {
			helper.assertTrue(k.moveOn(s, false, false) != null, "a move on " + s);
		}
		for (AbilitySlot s : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5 }) {
			helper.assertTrue(k.moveOn(s, true, false) != null, "a Shift move on " + s);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void vineLashHitsAtRangeAndVineGrabReelsIn(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		Zombie z = zombie(helper, 3.5, 2, 7.5);
		float before = z.getHealth();
		tap(p, AbilitySlot.SLOT_1, false);
		helper.assertTrue(z.getHealth() < before, "the Vine Lash reaches 6 blocks: " + z.getHealth());
		helper.assertTrue(kit().cooldownLeft(p, WildvineKit.LASH) > 0, "R cools down");
		z.discard();
		Zombie far = zombie(helper, 3.5, 2, 7.5);
		float farBefore = far.getHealth();
		tap(p, AbilitySlot.SLOT_1, true);
		helper.assertTrue(far.getHealth() < farBefore, "the Vine Grab hits");
		helper.assertTrue(far.getDeltaMovement().z < -0.2, "and reels it in toward him: " + far.getDeltaMovement());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void vineGrabAtABlockZipsHimToIt(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		for (int x = 2; x <= 5; x++) {
			for (int y = 2; y <= 5; y++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, 7)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		tap(p, AbilitySlot.SLOT_1, true);
		helper.assertTrue(WildvineKit.zipping(p) && WildvineKit.lineMode(p) == 2, "a vine to the wall: he is pulled along it");
		helper.assertTrue(WildvineKit.zipVelocity(p.getEyePosition(), WildvineKit.hook(p)).z > 1.0, "toward the wall");
		tap(p, AbilitySlot.SLOT_3, false);
		helper.assertFalse(WildvineKit.zipping(p), "X lets go of the vine");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void vineSwingHangsOffACeilingWhileHeld(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		for (int x = 1; x <= 6; x++) {
			for (int z = 3; z <= 9; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 9, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		p.setXRot(-55f); // up and ahead, at the ceiling
		AbilityRouter.handleInput(p, AbilitySlot.SLOT_3.number(), true);
		helper.assertTrue(WildvineKit.swinging(p) && WildvineKit.hook(p) != null, "X down: a vine to the ceiling");
		helper.assertTrue(kit().keyActive(p, AbilitySlot.SLOT_3), "X lights while he swings");
		helper.assertFalse(kit().allowDamage(p, helper.getLevel().damageSources().fall(), 6f), "no fall damage off his vine");
		AbilityRouter.handleInput(p, AbilitySlot.SLOT_3.number(), false);
		helper.assertFalse(WildvineKit.swinging(p), "letting go of X lets go of the vine");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void seedBombArcsAndBursts(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		Zombie z = zombie(helper, 3.5, 2, 6.5);
		float before = z.getHealth();
		tap(p, AbilitySlot.SLOT_2, false);
		helper.assertTrue(kit().cooldownLeft(p, WildvineKit.SEED_BOMB) > 0, "G cools down");
		helper.assertTrue(!helper.getLevel().getEntitiesOfClass(AlienProjectileEntity.class, p.getBoundingBox().inflate(4),
				e -> e.kind().equals("wildvine_seed")).isEmpty(), "a seed pod is thrown");
		helper.succeedWhen(() -> helper.assertTrue(z.getHealth() <= before - 9f, "the pod bursts on the zombie: " + z.getHealth()));
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void seedMinesArmAndBurstUnderAnEnemy(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		helper.onEachTick(() -> Omnitrix.tick(p));
		tap(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(WildvineKit.mineCount(p) == WildvineKit.MINES, "three pods planted: " + WildvineKit.mineCount(p));
		AlienProjectileEntity mine = WildvineKit.mines(p).get(1);
		helper.runAfterDelay(WildvineKit.MINE_ARM + 4, () -> {
			Zombie z = zombie(helper, 0, 0, 0);
			z.moveTo(mine.getX(), mine.getY(), mine.getZ());
			float before = z.getHealth();
			helper.succeedWhen(() -> {
				helper.assertTrue(z.getHealth() <= before - 8f, "the armed mine bursts under the zombie: " + z.getHealth());
				helper.assertTrue(WildvineKit.mineCount(p) < WildvineKit.MINES, "and is spent");
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void constrictRootsAndSqueezes(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		Zombie z = zombie(helper, 3.5, 2, 7.5);
		helper.onEachTick(() -> Omnitrix.tick(p));
		float before = z.getHealth();
		tap(p, AbilitySlot.SLOT_4, false);
		helper.assertTrue(WildvineKit.coiling(p) && rooted(z), "Constrict coils and roots the zombie 6 blocks off");
		helper.runAfterDelay(WildvineKit.CONSTRICT_TICKS + 2, () -> {
			helper.assertTrue(before - z.getHealth() >= 16f, "squeezed over 3 s: " + (before - z.getHealth()));
			helper.assertFalse(WildvineKit.coiling(p), "and let go");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void vineSnareAndOvergrowthHitAndRootEverythingAround(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		Zombie a = zombie(helper, 6.5, 2, 3.5);
		Zombie b = zombie(helper, 0.5, 2, 3.5);
		float ba = a.getHealth(), bb = b.getHealth();
		tap(p, AbilitySlot.SLOT_4, true);
		helper.assertTrue(a.getHealth() < ba && b.getHealth() < bb && rooted(a) && rooted(b), "the Vine Snare hits and roots both");
		Zombie c = zombie(helper, 3.5, 2, 7.5);
		float bc = c.getHealth();
		tap(p, AbilitySlot.SLOT_6, false);
		helper.assertTrue(bc - c.getHealth() >= 15f && rooted(c), "Overgrowth hits 6 blocks off hard and roots it: " + (bc - c.getHealth()));
		helper.assertTrue(kit().cooldownLeft(p, WildvineKit.OVERGROWTH) > 0, "C cools down");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void rootBurrowIsUntouchableThenBurstsUp(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		p.setOnGround(true);
		Zombie z = zombie(helper, 4.5, 2, 3.0);
		z.setTarget(p);
		tap(p, AbilitySlot.SLOT_3, true);
		helper.assertTrue(kit().flag(p, WildvineKit.F_BURROW), "Shift+X sinks him into the ground");
		helper.assertTrue(z.getTarget() == null, "the zombie hunting him loses track");
		DamageSource hit = helper.getLevel().damageSources().mobAttack(z);
		helper.assertFalse(kit().allowDamage(p, hit, 6f), "untouchable under the ground");
		helper.assertTrue(kit().allowDamage(p, helper.getLevel().damageSources().onFire(), 1f), "but fire still finds a plant");
		float before = z.getHealth();
		kit().endBurrowNow(p);
		Omnitrix.tick(p);
		helper.assertFalse(kit().flag(p, WildvineKit.F_BURROW), "up again");
		helper.assertTrue(z.getHealth() < before, "the burst hits the zombie beside him: " + z.getHealth());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void regrowthHealsAndCamouflageLosesHunters(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		p.setHealth(10f);
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
		tap(p, AbilitySlot.SLOT_5, false);
		helper.assertTrue(p.getHealth() >= 19.9f, "Regrowth: 10 back: " + p.getHealth());
		helper.assertFalse(p.hasEffect(MobEffects.POISON), "and the poison is shed");
		Zombie z = zombie(helper, 3.5, 2, 6.5);
		z.setTarget(p);
		tap(p, AbilitySlot.SLOT_5, true);
		helper.assertTrue(kit().flag(p, WildvineKit.F_CAMO) && p.hasEffect(MobEffects.INVISIBILITY), "Shift+V: camouflaged");
		helper.assertTrue(z.getTarget() == null, "the zombie hunting him loses track");
		tap(p, AbilitySlot.SLOT_1, false);
		helper.assertFalse(kit().flag(p, WildvineKit.F_CAMO), "a move ends the camouflage");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fireAndFrostHitAPlantHardAndHeClimbsWalls(GameTestHelper helper) {
		ServerPlayer p = wildvine(helper);
		WildvineKit k = kit();
		helper.assertTrue(Math.abs(k.incomingDamage(p, helper.getLevel().damageSources().onFire(), 4f) - 7f) < 0.01f, "fire: +75%");
		helper.assertTrue(Math.abs(k.incomingDamage(p, helper.getLevel().damageSources().freeze(), 4f) - 7f) < 0.01f, "frost: +75%");
		helper.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and frost slows him");
		Zombie z = zombie(helper, 6.5, 2, 6.5);
		helper.assertTrue(k.incomingDamage(p, helper.getLevel().damageSources().mobAttack(z), 4f) == 4f, "a plain hit is whole");
		helper.assertTrue(k.incomingDamage(p, helper.getLevel().damageSources().fall(), 4f) == 2f, "falls: half (a pliant body)");
		p.horizontalCollision = true;
		helper.assertTrue(k.climbs(p), "pressed into a wall he climbs it");
		p.horizontalCollision = false;
		helper.assertFalse(k.climbs(p), "not in the open");
		helper.succeed();
	}
}
