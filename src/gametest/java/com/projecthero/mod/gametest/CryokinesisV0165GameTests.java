package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p09.CryoEntities;
import com.projecthero.mod.hero.power.p09.CryoIce;
import com.projecthero.mod.hero.power.p09.CryokinesisHandlers;
import com.projecthero.mod.hero.power.p09.FrostShellEntity;
import com.projecthero.mod.hero.power.p09.FrostStacks;
import com.projecthero.mod.hero.power.p09.IceBoltEntity;
import com.projecthero.mod.hero.power.p09.IceSpikeEntity;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Cryokinesis rework (docs/V0165_POWERS_PROPOSAL.md section 9): six keys (+ Shift versions) on a 0-100 Cold meter
 * -- the meter, Frost stacking and freezing solid (and what a frozen creature can't do), every move's numbers, the
 * conjured ice and its cleanup, and the passives. Mock players are not reliably ticked by the server, so the tests drive
 * {@link ExperimentalPowers#serverTick} themselves. Target dummies are no-AI iron golems (100 HP, no armour, so the
 * damage numbers come through exactly).
 */
public class CryokinesisV0165GameTests implements FabricGameTest {
	private static final String K = CryokinesisHandlers.KEY;

	// ---------------- helpers ----------------

	/** A survival mock cryokinetic at (2.5, 2, 1.5) on a stone floor, facing +Z, with a clear 5x5x10 room. */
	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 1.5));
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, 0, -1), base.offset(2, 4, 8))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -1, -1), base.offset(2, -1, 8))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		Power power = Powers.byKey(K);
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		p.setOnGround(true);
		return p;
	}

	private static float res(ServerPlayer p, String name) {
		return ExperimentalPowers.getResource(p, Powers.byKey(K), name);
	}

	private static void tick(ServerPlayer p, int n) {
		for (int i = 0; i < n; i++) {
			ExperimentalPowers.serverTick(p);
		}
	}

	private static void press(ServerPlayer p, int slot) {
		AbilityRouter.handleInput(p, slot, true);
		AbilityRouter.handleInput(p, slot, false);
	}

	private static void shiftPress(ServerPlayer p, int slot) {
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, slot, true);
		AbilityRouter.handleInput(p, slot, false);
		p.setShiftKeyDown(false);
	}

	/** A no-AI iron golem dummy (100 HP, no armour) at relative (x, 2, z). */
	private static IronGolem dummy(GameTestHelper helper, int x, int z) {
		IronGolem g = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(x, 2, z));
		g.setPersistenceRequired();
		g.setNoAi(true);
		return g;
	}

	private static Zombie zombie(GameTestHelper helper, int x, int z) {
		Zombie zb = helper.spawn(EntityType.ZOMBIE, new BlockPos(x, 2, z));
		zb.setPersistenceRequired();
		zb.setNoAi(true);
		return zb;
	}

	private static void assertNear(GameTestHelper helper, float expected, float got, String what) {
		helper.assertTrue(Math.abs(expected - got) < 0.05f, what + ": expected " + expected + ", got " + got);
	}

	// ---------------- registry / HUD ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void sixKeysColdMeterAndEnabled(GameTestHelper helper) {
		Power power = Powers.byKey(K);
		helper.assertTrue(Powers.isEnabled(power) && Powers.isMutation(power), "Cryokinesis is an enabled mutation again");
		helper.assertTrue(power.abilities().size() == 6, "six abilities (no H / N), got " + power.abilities().size());
		String[] ids = { "ice_bolt", "freeze_beam", "ice_slide", "glacier_spikes", "glacier_wall", "ice_blade" };
		for (int i = 0; i < ids.length; i++) {
			Ability a = power.ability(AbilitySlot.byNumber(i + 1));
			helper.assertTrue(a != null && a.id().equals(ids[i]), "slot " + (i + 1) + " should be " + ids[i]);
			helper.assertTrue(AbilityHandlers.has(power, a), ids[i] + " has a handler");
		}
		var cold = MutationMeters.get(K, "cold");
		helper.assertTrue(cold != null && cold.always() && cold.style() == MutationMeters.Style.HAIRLINE,
				"Cold is an always-on Hairline");
		for (String timer : new String[] { "slide", "prison_cd", "zero_cd" }) {
			helper.assertTrue(MutationMeters.get(K, timer) != null, timer + " timer on the HUD");
		}
		for (String flag : new String[] { "p09.beam", "p09.slide", "p09.armor", "p09.blade", "p09.pillar" }) {
			helper.assertTrue(MutationVisuals.registeredFlags().contains(flag), flag + " is synced");
		}
		helper.assertTrue(CryoEntities.ICE_BOLT != null && CryoEntities.FROST_SHELL != null && CryoEntities.ICE_SPIKE != null,
				"the ice entities are registered");
		helper.succeed();
	}

	// ---------------- Cold ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void coldSpendsAndRefills(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		helper.assertTrue(CryokinesisHandlers.cold(p) >= 99.9f, "the meter starts full");
		press(p, 1);
		assertNear(helper, 100 - CryokinesisHandlers.COST_BOLT, CryokinesisHandlers.cold(p), "Ice Bolt costs 8");
		shiftPress(p, 1);
		assertNear(helper, 100 - 8 - 15, CryokinesisHandlers.cold(p), "Ice Shards cost 15");
		tick(p, 20);
		assertNear(helper, 100 - 8 - 15 + 15, CryokinesisHandlers.cold(p), "refills 15 a second (not in the cold)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void coldRefillsFasterOnSnow(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		helper.getLevel().setBlock(p.blockPosition().below(), Blocks.SNOW_BLOCK.defaultBlockState(), 2);
		CryokinesisHandlers.setCold(p, 10);
		tick(p, 20);
		assertNear(helper, 35, CryokinesisHandlers.cold(p), "25 a second standing on snow");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void unaffordableMovesAreRefused(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		CryokinesisHandlers.setCold(p, 5);
		press(p, 1);
		assertNear(helper, 5, CryokinesisHandlers.cold(p), "nothing spent");
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(IceBoltEntity.class, new AABB(p.blockPosition()).inflate(8)).isEmpty(),
				"and no bolt");
		shiftPress(p, 4);
		helper.assertTrue(res(p, "zero_cd") < 0.5f, "a refused Absolute Zero starts no cooldown");
		helper.succeed();
	}

	// ---------------- Frost ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void fiveFrostFreezeSolidFor2point5s(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 2, 5);
		helper.assertTrue(FrostStacks.add(p, z, 4) == 4 && !FrostStacks.frozen(z), "four Frost: not yet");
		helper.assertTrue(FrostStacks.add(p, z, 1) == FrostStacks.MAX && FrostStacks.frozen(z), "the fifth freezes it solid");
		helper.assertTrue(FrostStacks.frozenTicksLeft(z) == 50, "for 2.5 s, got " + FrostStacks.frozenTicksLeft(z));
		helper.assertTrue(!helper.getLevel().getEntitiesOfClass(FrostShellEntity.class, z.getBoundingBox().inflate(2)).isEmpty(),
				"the ice block model follows it");
		helper.assertTrue(z.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "pinned in place");
		// a frozen creature cannot strike
		float hp = p.getHealth();
		p.hurt(helper.getLevel().damageSources().mobAttack(z), 5.0f);
		assertNear(helper, hp, p.getHealth(), "a frozen zombie's hit does nothing");
		helper.runAfterDelay(55, () -> {
			helper.assertFalse(FrostStacks.frozen(z), "thawed after 2.5 s");
			helper.assertTrue(FrostStacks.add(p, z, 1) == 0, "and briefly immune to new Frost");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void frostFadesOneAtATime(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 2, 5);
		FrostStacks.add(p, z, 2);
		helper.runAfterDelay(65, () -> {
			helper.assertTrue(FrostStacks.stacks(z) == 1, "one Frost fades after 3 s, got " + FrostStacks.stacks(z));
			helper.succeed();
		});
	}

	// ---------------- R ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void iceBoltTenDamageOneFrost(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 6);
		press(p, 1);
		helper.runAfterDelay(10, () -> {
			assertNear(helper, 100 - CryokinesisHandlers.BOLT_DAMAGE, g.getHealth(), "Ice Bolt hits for 10");
			helper.assertTrue(FrostStacks.stacks(g) == 1, "and 1 Frost, got " + FrostStacks.stacks(g));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void iceShardsPointBlankFreeze(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 4);
		shiftPress(p, 1);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(IceBoltEntity.class, new AABB(p.blockPosition()).inflate(4)).size()
				== CryokinesisHandlers.SHARD_COUNT, "seven shards");
		helper.runAfterDelay(10, () -> {
			float dealt = 100 - g.getHealth();
			helper.assertTrue(dealt >= 5 * CryokinesisHandlers.SHARD_DAMAGE - 0.01f && Math.abs(dealt % 4) < 0.01f,
					"4 a shard, most of the fan landing point-blank, got " + dealt);
			helper.assertTrue(FrostStacks.frozen(g), "five or more shards freeze it outright");
			helper.succeed();
		});
	}

	// ---------------- G ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void freezeBeamBuildsFrostAndDrains(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 6);
		AbilityRouter.handleInput(p, 2, true);
		helper.assertTrue(CryokinesisHandlers.beaming(p), "holding G beams");
		tick(p, 20);
		helper.assertTrue(FrostStacks.stacks(g) == 2, "1 Frost every half second, got " + FrostStacks.stacks(g));
		assertNear(helper, 90, CryokinesisHandlers.cold(p), "10 Cold a second, no refill while beaming");
		AbilityRouter.handleInput(p, 2, false);
		helper.assertFalse(CryokinesisHandlers.beaming(p), "letting go ends it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void icePrisonFourSecondsNoBosses(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 6);
		shiftPress(p, 2);
		helper.assertTrue(FrostStacks.frozen(g), "Ice Prison encases the target");
		helper.assertTrue(FrostStacks.frozenTicksLeft(g) == CryokinesisHandlers.PRISON_TICKS, "for 4 s");
		assertNear(helper, 70, CryokinesisHandlers.cold(p), "30 Cold");
		assertNear(helper, CryokinesisHandlers.PRISON_CD, res(p, "prison_cd"), "12 s cooldown");
		shiftPress(p, 2);
		assertNear(helper, 70, CryokinesisHandlers.cold(p), "on cooldown: refused");
		helper.succeed();
	}

	// ---------------- X ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void iceSlideFiveSecondsAndItsRamp(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		press(p, 3);
		helper.assertTrue(CryokinesisHandlers.sliding(p), "X starts the slide");
		assertNear(helper, 80, CryokinesisHandlers.cold(p), "20 Cold");
		// the ramp: lay it over open air two blocks up
		Vec3 feet = p.position().add(0, 2, 2);
		int laid = CryokinesisHandlers.layRampForTest(helper.getLevel(), feet, new Vec3(0, 0, 1));
		helper.assertTrue(laid >= 3, "a 3-wide strip of ice, got " + laid);
		helper.assertTrue(helper.getLevel().getBlockState(BlockPos.containing(feet.x, feet.y - 0.6, feet.z)).is(Blocks.PACKED_ICE),
				"packed ice under the feet");
		float hp = p.getHealth();
		p.hurt(helper.getLevel().damageSources().fall(), 8.0f);
		assertNear(helper, hp, p.getHealth(), "no fall damage while sliding");
		tick(p, CryokinesisHandlers.SLIDE_TICKS + 1);
		helper.assertFalse(CryokinesisHandlers.sliding(p), "it lasts 5 s");
		helper.assertTrue(CryokinesisHandlers.fallSafe(p), "with a 3 s landing grace");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void icePillarLaunchesTenBlocks(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		double v = CryokinesisHandlers.pillarLaunchSpeed();
		helper.assertTrue(Math.abs(CryokinesisHandlers.apex(v) - CryokinesisHandlers.PILLAR_HEIGHT) < 0.05,
				"the launch peaks 10 blocks up, got " + CryokinesisHandlers.apex(v));
		shiftPress(p, 3);
		assertNear(helper, 85, CryokinesisHandlers.cold(p), "15 Cold");
		helper.assertTrue(Math.abs(p.getDeltaMovement().y - v) < 1.0e-3, "launched at " + v + ", got " + p.getDeltaMovement().y);
		helper.assertTrue(CryokinesisHandlers.rising(p) && CryokinesisHandlers.fallSafe(p), "rising, no fall damage after");
		helper.succeed();
	}

	// ---------------- Z ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void glacierSpikes16AndTwoFrost(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 6);
		press(p, 4);
		assertNear(helper, 75, CryokinesisHandlers.cold(p), "25 Cold");
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(IceSpikeEntity.class, new AABB(p.blockPosition()).inflate(14)).size() >= 6,
				"a line of spikes (over the floor)");
		helper.runAfterDelay(20, () -> {
			assertNear(helper, 100 - CryokinesisHandlers.SPIKES_DAMAGE, g.getHealth(), "16 damage, once per volley");
			helper.assertTrue(FrostStacks.stacks(g) == 2, "and 2 Frost, got " + FrostStacks.stacks(g));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void absoluteZeroFreezesEverythingWithinEight(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie near = zombie(helper, 2, 4);
		Zombie edge = zombie(helper, 2, 8);
		Zombie far = zombie(helper, 2, 12);
		BlockPos pool = p.blockPosition().offset(1, -1, 1);
		helper.getLevel().setBlock(pool, Blocks.WATER.defaultBlockState(), 2);
		shiftPress(p, 4);
		helper.assertTrue(FrostStacks.frozen(near) && FrostStacks.frozen(edge), "everything within 8 is frozen solid");
		helper.assertFalse(FrostStacks.frozen(far) || FrostStacks.stacks(far) > 0, "nothing beyond");
		helper.assertTrue(helper.getLevel().getBlockState(pool).is(Blocks.ICE), "nearby water freezes");
		assertNear(helper, 50, CryokinesisHandlers.cold(p), "50 Cold");
		assertNear(helper, CryokinesisHandlers.ZERO_CD, res(p, "zero_cd"), "20 s cooldown");
		CryoIce.meltAll();
		helper.assertTrue(helper.getLevel().getBlockState(pool).is(Blocks.WATER), "the ice melts back to water");
		helper.succeed();
	}

	// ---------------- V ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void glacierWallFiveByFourThenMelts(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		press(p, 5);
		assertNear(helper, 85, CryokinesisHandlers.cold(p), "15 Cold");
		BlockPos base = p.blockPosition();
		helper.runAfterDelay(12, () -> {
			int ice = 0;
			int crest = 0;
			java.util.List<BlockPos> cells = new java.util.ArrayList<>();
			for (BlockPos pos : BlockPos.betweenClosed(base.offset(-3, 0, 1), base.offset(3, 5, 5))) {
				if (CryoIce.isConjured(helper.getLevel(), pos)) {
					cells.add(pos.immutable());
					ice++;
					if (helper.getLevel().getBlockState(pos).is(Blocks.BLUE_ICE)) {
						crest++;
					}
				}
			}
			helper.assertTrue(ice == CryokinesisHandlers.WALL_WIDTH * CryokinesisHandlers.WALL_HEIGHT, "5 x 4 of ice, got " + ice);
			helper.assertTrue(crest == CryokinesisHandlers.WALL_WIDTH, "a blue-ice crest, got " + crest);
			CryoIce.meltAll();
			for (BlockPos pos : cells) {
				helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "every block melted back to air at " + pos);
			}
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void conjuredIceMeltsOnTimeAndNeverOverwrites(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		BlockPos a = p.blockPosition().offset(-1, 1, 3);
		BlockPos b = p.blockPosition().offset(1, 1, 3);
		helper.assertTrue(CryoIce.place(helper.getLevel(), a, Blocks.ICE.defaultBlockState(), 5), "placed a");
		helper.assertTrue(CryoIce.place(helper.getLevel(), b, Blocks.ICE.defaultBlockState(), 5), "placed b");
		helper.assertFalse(CryoIce.place(helper.getLevel(), p.blockPosition().below(), Blocks.ICE.defaultBlockState(), 5),
				"never into solid ground");
		helper.getLevel().setBlock(b, Blocks.OAK_PLANKS.defaultBlockState(), 2); // a player builds over it
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(helper.getLevel().getBlockState(a).isAir(), "melted after its own 5 ticks");
			helper.assertTrue(helper.getLevel().getBlockState(b).is(Blocks.OAK_PLANKS), "a block put there since is left alone");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void frozenArmorAbsorbsAndFrostsAttackers(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 2, 2);
		shiftPress(p, 5);
		helper.assertTrue(CryokinesisHandlers.armored(p), "Shift+V: Frozen Armor on");
		assertNear(helper, CryokinesisHandlers.ARMOR_ABSORPTION, p.getAbsorptionAmount(), "8 hearts of ice");
		p.hurt(helper.getLevel().damageSources().mobAttack(z), 4.0f);
		helper.assertTrue(p.getAbsorptionAmount() < CryokinesisHandlers.ARMOR_ABSORPTION - 0.5f, "the ice takes the hit");
		helper.assertTrue(FrostStacks.stacks(z) == 1, "the attacker takes 1 Frost, got " + FrostStacks.stacks(z));
		tick(p, 20);
		assertNear(helper, 97, CryokinesisHandlers.cold(p), "3 Cold a second, no refill");
		shiftPress(p, 5);
		helper.assertFalse(CryokinesisHandlers.armored(p), "Shift+V again takes it off");
		helper.assertTrue(p.getAbsorptionAmount() <= p.getAttributeValue(Attributes.MAX_ABSORPTION) + 0.01, "the hearts go with it");
		helper.succeed();
	}

	// ---------------- C ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void iceBladePlusEightAndFrost(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 3);
		double base = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		press(p, 6);
		helper.assertTrue(CryokinesisHandlers.bladed(p), "C grows the blade");
		assertNear(helper, (float) base + CryokinesisHandlers.BLADE_DAMAGE, (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE), "+8 melee");
		assertNear(helper, 100, CryokinesisHandlers.cold(p), "free");
		AttackEntityCallback.EVENT.invoker().interact(p, helper.getLevel(), InteractionHand.MAIN_HAND, g, null);
		helper.assertTrue(FrostStacks.stacks(g) == 1, "1 Frost a hit, got " + FrostStacks.stacks(g));
		press(p, 6);
		assertNear(helper, (float) base, (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE), "C again: gone");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shatterDoublesAgainstTheFrozen(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem frozen = dummy(helper, 0, 5);
		IronGolem frosted = dummy(helper, 4, 5);
		IronGolem clean = dummy(helper, 2, 8);
		shiftPress(p, 6);
		assertNear(helper, 100, CryokinesisHandlers.cold(p), "nothing frosted nearby: refused, nothing spent");
		FrostStacks.freezeNow(p, frozen);
		FrostStacks.add(p, frosted, 1);
		shiftPress(p, 6);
		assertNear(helper, 100 - 2 * CryokinesisHandlers.SHATTER_DAMAGE, frozen.getHealth(), "double (40) against the frozen");
		helper.assertFalse(FrostStacks.frozen(frozen), "and it bursts out of its ice");
		assertNear(helper, 100 - CryokinesisHandlers.SHATTER_DAMAGE, frosted.getHealth(), "20 against the frosted");
		assertNear(helper, 100, clean.getHealth(), "nothing for one with no ice on it");
		assertNear(helper, 80, CryokinesisHandlers.cold(p), "20 Cold");
		helper.succeed();
	}

	// ---------------- passives / cleanup ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void passivesFreezeImmunityGripAndFrostWalker(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setTicksFrozen(120);
		tick(p, 1);
		helper.assertTrue(p.getTicksFrozen() == 0, "the cold never builds up on you, got " + p.getTicksFrozen());
		float hp = p.getHealth();
		p.hurt(helper.getLevel().damageSources().freeze(), 5.0f);
		assertNear(helper, hp, p.getHealth(), "immune to freezing damage");
		helper.assertTrue(CryokinesisHandlers.surefooted(p), "no slipping on ice");
		BlockPos under = p.blockPosition().below().offset(1, 0, 0);
		helper.getLevel().setBlock(under, Blocks.WATER.defaultBlockState(), 2);
		tick(p, 1);
		helper.assertTrue(helper.getLevel().getBlockState(under).is(Blocks.FROSTED_ICE), "water freezes under your feet");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void forgettingThePowerDropsEverything(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		double base = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		press(p, 6);
		shiftPress(p, 5);
		AbilityRouter.handleInput(p, 2, true);
		ExperimentalPowers.forget(p, Powers.byKey(K));
		helper.assertFalse(CryokinesisHandlers.owns(p), "forgotten");
		assertNear(helper, (float) base, (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE), "the blade's +8 is gone");
		helper.assertTrue(p.getAttributeValue(Attributes.MAX_ABSORPTION) < 0.01, "the armour's hearts are gone");
		helper.succeed();
	}
}
