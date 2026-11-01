package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixAuto;
import com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadKit;
import com.projecthero.mod.omnitrix.alien.fourarms.FourArmsKit;
import com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakKit;
import com.projecthero.mod.omnitrix.alien.greymatter.GreyMatterKit;
import com.projecthero.mod.omnitrix.alien.heatblast.HeatblastKit;
import com.projecthero.mod.omnitrix.alien.stinkfly.StinkflyKit;
import com.projecthero.mod.omnitrix.alien.upgrade.UpgradeKit;
import com.projecthero.mod.omnitrix.alien.wildmutt.WildmuttKit;
import com.projecthero.mod.omnitrix.alien.xlr8.Xlr8Kit;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "an omnitrix automatic transform ... holding R for 1 second turns the player into an alien"): the hold through the
 * router as the client sends it, and {@link OmnitrixAuto#pick} in each situation of the approved table. Mock players are not ticked
 * by the server, so the hold is driven with {@link Omnitrix#tick}.
 */
public class OmnitrixAutoGameTests implements FabricGameTest {
	private static ServerPlayer human(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2, 1.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0f);
		p.setOnGround(true);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		return p;
	}

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double z, ServerPlayer target) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		T m = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, 2, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		helper.getLevel().addFreshEntity(m);
		if (target != null) {
			m.setTarget(target);
		}
		return m;
	}

	private static void expect(GameTestHelper helper, ServerPlayer p, String alien, String reason) {
		OmnitrixAuto.Pick pick = OmnitrixAuto.pick(p);
		helper.assertTrue(alien.equals(pick.alien()) && reason.equals(pick.reason()),
				"expected " + alien + " (" + reason + "), got " + pick.alien() + " (" + pick.reason() + ")");
	}

	private static void r(ServerPlayer p, boolean down) {
		AbilityRouter.handleInput(p, AbilitySlot.SLOT_1.number(), down);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void tapRJustRaisesAndHoldingRAutoTransforms(GameTestHelper helper) {
		ServerPlayer p = human(helper);
		Omnitrix.noGlitch(p);
		// a tap: the watch comes up, nothing else
		r(p, true);
		helper.assertTrue(Omnitrix.state(p).selecting, "R raises the watch");
		helper.assertTrue(OmnitrixAuto.holdProgress(Omnitrix.state(p), helper.getLevel().getGameTime(), 0f) >= 0f, "and starts the hold");
		r(p, false);
		helper.assertTrue(OmnitrixAuto.holdProgress(Omnitrix.state(p), helper.getLevel().getGameTime(), 0f) < 0f, "letting go ends the hold");
		// R again lowers it
		r(p, true);
		r(p, false);
		helper.assertFalse(Omnitrix.state(p).selecting, "R again lowers the watch");
		// now hold it: falling, so it should become Stinkfly
		p.setOnGround(false);
		p.fallDistance = 6f;
		r(p, true);
		helper.onEachTick(() -> {
			p.fallDistance = 6f;
			p.setOnGround(false);
			Omnitrix.tick(p);
		});
		helper.runAfterDelay(OmnitrixAuto.HOLD_TICKS + 60, () -> {
			helper.assertTrue(Omnitrix.state(p).transformed(), "held for a second it transformed by itself");
			helper.assertTrue(StinkflyKit.ID.equals(Omnitrix.state(p).active), "falling: into Stinkfly: " + Omnitrix.state(p).active);
			r(p, false);
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void autoPicksForFallingFireAndTheDial(GameTestHelper helper) {
		ServerPlayer p = human(helper);
		String dial = Omnitrix.state(p).selected;
		helper.assertTrue(dial.equals(OmnitrixAuto.pick(p).alien()), "nothing special going on: the dial's alien");
		p.setOnGround(false);
		p.fallDistance = 6f;
		expect(helper, p, StinkflyKit.ID, "falling");
		p.setOnGround(true);
		p.fallDistance = 0f;
		p.setRemainingFireTicks(100);
		expect(helper, p, HeatblastKit.ID, "fire");
		p.setRemainingFireTicks(0);
		p.setSprinting(true);
		// (tests run side by side: a neighbour's hostile within 16 blocks is a threat, and then travel rightly does not apply)
		boolean alone = helper.getLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(OmnitrixAuto.THREAT_RANGE),
				m -> m instanceof net.minecraft.world.entity.monster.Enemy).isEmpty();
		if (alone) {
			expect(helper, p, Xlr8Kit.ID, "travel");
		}
		p.setSprinting(false);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void autoPicksForCombat(GameTestHelper helper) {
		ServerPlayer p = human(helper);
		Mob z = mob(helper, EntityType.ZOMBIE, 3.5, 5.5, p);
		expect(helper, p, WildmuttKit.ID, "melee");
		p.setHealth(p.getMaxHealth() * 0.2f);
		expect(helper, p, DiamondheadKit.ID, "low_health");
		p.setHealth(p.getMaxHealth());
		Mob a = mob(helper, EntityType.ZOMBIE, 2.5, 5.5, p);
		Mob b = mob(helper, EntityType.ZOMBIE, 4.5, 5.5, p);
		Mob c = mob(helper, EntityType.ZOMBIE, 5.5, 5.5, p);
		expect(helper, p, HeatblastKit.ID, "crowd");
		a.discard();
		b.discard();
		c.discard();
		z.discard();
		Mob s1 = mob(helper, EntityType.SKELETON, 2.5, 6.5, p);
		Mob s2 = mob(helper, EntityType.SKELETON, 4.5, 6.5, p);
		expect(helper, p, DiamondheadKit.ID, "ranged");
		s1.discard();
		s2.discard();
		Mob rav = mob(helper, EntityType.RAVAGER, 3.5, 6.5, p);
		expect(helper, p, FourArmsKit.ID, "boss");
		rav.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void autoPicksForStealthTechAndTightGaps(GameTestHelper helper) {
		ServerPlayer p = human(helper);
		Mob z = mob(helper, EntityType.ZOMBIE, 3.5, 6.5, null);
		p.setShiftKeyDown(true);
		expect(helper, p, GhostfreakKit.ID, "stealth");
		p.setShiftKeyDown(false);
		z.discard();
		var cart = EntityType.MINECART.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(5.5, 2, 1.5));
		cart.moveTo(at.x, at.y, at.z);
		helper.getLevel().addFreshEntity(cart);
		expect(helper, p, UpgradeKit.ID, "tech");
		cart.discard();
		// a 1-block gap in front (he faces +z): open at the feet, shut at the head
		helper.getLevel().setBlock(p.blockPosition().south().above(), Blocks.STONE.defaultBlockState(), 3);
		expect(helper, p, GreyMatterKit.ID, "tight");
		helper.succeed();
	}
}
