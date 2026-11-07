package com.projecthero.mod.gametest;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.MiraculousPhrases;
import com.projecthero.mod.miraculous.MiraculousSequence;
import com.projecthero.mod.miraculous.ryuko.Ryuko;
import com.projecthero.mod.miraculous.ryuko.RyukoMoves;
import com.projecthero.mod.miraculous.ryuko.RyukoSequence;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** v0.17.3 Ryuko (Longg): the wiring, the elements, the moves' hits, Parry and Riposte, Dragon Form, the long sequence's order. */
public class RyukoGameTests implements FabricGameTest {
	/** Ryuko facing +Z, the transformation finished. */
	@SuppressWarnings("removal")
	private static ServerPlayer ryuko(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		p.setYHeadRot(0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		helper.assertTrue(Miraculous.grant(p, Kwami.LONGG), "Longg is granted");
		Miraculous.transform(p);
		long now = helper.getLevel().getGameTime();
		Miraculous.edit(p, n -> n.formChangeAt = now - 200L);
		return p;
	}

	private static Zombie zombie(GameTestHelper helper, int x, int z) {
		Zombie e = helper.spawn(EntityType.ZOMBIE, new BlockPos(x, 2, z));
		e.setNoAi(true);
		return e;
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBasicsAreWired(GameTestHelper helper) {
		helper.assertTrue(MiraculousPhrases.match(Kwami.LONGG, "Longg, bring the storm!") == MiraculousPhrases.Spoken.TRANSFORM_LONG, "the full phrase plays the scene");
		helper.assertTrue(MiraculousPhrases.match(Kwami.LONGG, "Bring the storm!") == MiraculousPhrases.Spoken.TRANSFORM, "the short phrase is quick");
		helper.assertTrue(MiraculousPhrases.match(Kwami.LONGG, "Calm the storm!") == MiraculousPhrases.Spoken.DETRANSFORM, "the way back");
		helper.assertTrue(Miraculous.foodOf(Kwami.LONGG) == Items.COOKED_SALMON, "cooked salmon");
		helper.assertTrue(MiraculousSequence.of(Kwami.LONGG) == RyukoSequence.TIMELINE, "she has a long transformation");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theMaskComesLastAndTheSuitNeverGoesBackward(GameTestHelper helper) {
		float last = 0f;
		for (int t = 0; t <= RyukoSequence.TOTAL; t++) {
			float c = RyukoSequence.cover(t);
			helper.assertTrue(c >= last - 1.0e-4f, "cover never falls, tick " + t);
			last = c;
		}
		helper.assertTrue(RyukoSequence.cover(RyukoSequence.BOLT_END + 1) <= RyukoSequence.BODY + 1.0e-3f, "the mask waits for her hand");
		helper.assertTrue(RyukoSequence.cover(RyukoSequence.MASK_END) >= 0.999f, "done with the mask");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void switchingCyclesTheThreeElements(GameTestHelper helper) {
		ServerPlayer p = ryuko(helper);
		helper.assertTrue(Ryuko.element(p) == Ryuko.WIND, "wind first");
		RyukoMoves.switchElement(p);
		helper.assertTrue(Ryuko.element(p) == Ryuko.WATER, "then water");
		RyukoMoves.switchElement(p);
		helper.assertTrue(Ryuko.element(p) == Ryuko.LIGHTNING, "then lightning");
		RyukoMoves.switchElement(p);
		helper.assertTrue(Ryuko.element(p) == Ryuko.WIND, "and round again");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBladeHitsWhatIsInFront(GameTestHelper helper) {
		ServerPlayer p = ryuko(helper);
		Zombie front = zombie(helper, 2, 4);
		float hp = front.getHealth();
		helper.assertTrue(RyukoMoves.strike(p) == 1, "one in front");
		helper.assertTrue(front.getHealth() < hp, "it hurts");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyElementsStrikeLands(GameTestHelper helper) {
		ServerPlayer p = ryuko(helper);
		Zombie z = zombie(helper, 2, 4);
		for (int el = 0; el < 3; el++) {
			z.setHealth(z.getMaxHealth());
			z.invulnerableTime = 0;
			RyukoMoves.elementStrike(p);
			helper.assertTrue(z.getHealth() < z.getMaxHealth(), "element " + el + " hits");
			RyukoMoves.switchElement(p);
		}
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aParryStopsTheHitAndOpensTheRiposte(GameTestHelper helper) {
		ServerPlayer p = ryuko(helper);
		Zombie z = zombie(helper, 2, 4);
		helper.assertTrue(!RyukoMoves.riposte(p), "no riposte without a parry");
		RyukoMoves.parry(p);
		float before = p.getHealth();
		p.hurt(p.damageSources().mobAttack(z), 6f);
		helper.assertTrue(p.getHealth() == before, "the parry takes the hit");
		float hp = z.getHealth();
		helper.assertTrue(RyukoMoves.riposte(p), "the riposte fires");
		helper.assertTrue(z.getHealth() < hp, "and lands");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void dragonFormIsUntouchableAndStartsTheCountdown(GameTestHelper helper) {
		ServerPlayer p = ryuko(helper);
		Zombie z = zombie(helper, 2, 4);
		RyukoMoves.dragonForm(p);
		helper.assertTrue(Ryuko.inForm(p), "she is the storm");
		helper.assertTrue(Miraculous.countdownLeft(p) > 0, "the countdown started");
		helper.assertTrue(p.getAbilities().flying, "she flies");
		float before = p.getHealth();
		p.hurt(p.damageSources().mobAttack(z), 8f);
		helper.assertTrue(p.getHealth() == before, "nothing touches her");
		RyukoMoves.endForm(p);
		helper.assertTrue(!Ryuko.inForm(p) && !p.getAbilities().flying, "it ends cleanly");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void detransformingClearsEverything(GameTestHelper helper) {
		ServerPlayer p = ryuko(helper);
		RyukoMoves.dragonForm(p);
		Miraculous.detransform(p, false);
		helper.assertTrue(!Ryuko.inForm(p) && !p.getAbilities().mayfly, "no flight left behind");
		helper.succeed();
	}
}
