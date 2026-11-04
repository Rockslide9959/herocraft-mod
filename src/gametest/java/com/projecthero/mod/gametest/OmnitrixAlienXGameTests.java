package com.projecthero.mod.gametest;

import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.alienx.AlienXActions;
import com.projecthero.mod.omnitrix.alien.alienx.AlienXCouncil;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Alien X's Celestial Council debates (docs/OMNITRIX_REVAMP_ALIENX.md): a motion carried by good arguments happens at his
 * body (Unmake one-shots a 400-health creature), a motion argued badly is refused (nothing happens, the category rests), and the
 * server refuses what it must (a wrong target kind, an argument not on offer). The tones and concerns are forced per player.
 */
public class OmnitrixAlienXGameTests implements FabricGameTest {
	private static final int GLORY = 1, PROTECTION = 3, MERCY = 4, BALANCE = 5, WISDOM = 6;

	private static Villager prey(GameTestHelper helper, double x, double y, double z, float health) {
		Villager m = EntityType.VILLAGER.create(helper.getLevel());
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

	private static ServerPlayer alienX(GameTestHelper helper) {
		OmnitrixRecalAlienXGameTests.floor(helper);
		ServerPlayer p = OmnitrixRecalAlienXGameTests.alienX(helper, OmnitrixRecalAlienXGameTests.mock(helper, 3.5, 2, 1.5));
		helper.assertTrue(AlienXCouncil.inSession(p), "before the council");
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aWellArguedMotionIsCarriedAtHisBody(GameTestHelper helper) {
		ServerPlayer p = alienX(helper);
		Villager v = prey(helper, 3.5, 2, 5.5, 400f);
		AlienXCouncil.freeTones(p);
		AlienXCouncil.forceConcerns(p, GLORY, BALANCE);
		helper.assertTrue(AlienXCouncil.propose(p, "unmake", v.getId()), "Unmake the villager is moved");
		helper.assertTrue(AlienXCouncil.phase(p) == AlienXCouncil.PHASE_DEBATE, "the debate is open");
		float[] m = AlienXCouncil.meters(p);
		helper.assertTrue(m[0] == 50f && m[1] == 0f, "Bellicus leans in (50), Serena is against (0)");
		// glory (Bellicus's concern, x2), balance (Serena's, x2), protection, then wisdom carries it (both at 85: v0.17.2 user, was 100)
		int[] plan = { GLORY, BALANCE, PROTECTION };
		for (int t : plan) {
			helper.assertTrue(AlienXCouncil.argue(p, t), "argued " + AlienXCouncil.TONES.get(t));
			helper.assertTrue(v.isAlive() && v.getHealth() == 400f, "nothing happens before both agree");
		}
		m = AlienXCouncil.meters(p);
		helper.assertTrue(m[0] >= AlienXCouncil.NEED && m[1] >= 60f && m[1] < AlienXCouncil.NEED, "Bellicus is convinced, Serena almost (" + m[0] + ", " + m[1] + ")");
		helper.assertTrue(AlienXCouncil.argue(p, WISDOM), "wisdom");
		helper.assertTrue(AlienXCouncil.phase(p) == AlienXCouncil.PHASE_RESULT && AlienXCouncil.lastCarried(p), "the motion is carried");
		helper.assertTrue(!v.isAlive() || v.isDeadOrDying(), "the 400-health villager is unmade in one stroke");
		helper.assertTrue(AlienXCouncil.cooldownLeft(p, "unmake") > 0, "Unmake now rests");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aBadlyArguedMotionIsRefused(GameTestHelper helper) {
		ServerPlayer p = alienX(helper);
		Villager v = prey(helper, 3.5, 2, 5.5, 40f);
		AlienXCouncil.freeTones(p);
		helper.assertTrue(AlienXCouncil.propose(p, "unmake", v.getId()), "Unmake is moved");
		// pleading mercy while asking to kill: it backfires on both, every turn
		for (int i = 0; i < AlienXCouncil.TURNS; i++) {
			helper.assertTrue(AlienXCouncil.argue(p, MERCY), "argued mercy");
			if (i == 0) {
				float[] m = AlienXCouncil.meters(p);
				helper.assertTrue(m[0] < 50f && m[1] < 0f, "the contradiction cost both heads (" + m[0] + ", " + m[1] + ")");
			}
		}
		helper.assertTrue(AlienXCouncil.phase(p) == AlienXCouncil.PHASE_RESULT && !AlienXCouncil.lastCarried(p), "the motion is refused");
		helper.assertTrue(v.isAlive() && v.getHealth() == 40f, "the villager is untouched");
		helper.assertTrue(AlienXCouncil.cooldownLeft(p, "cat:strike") > 0, "every Strike motion rests");
		helper.assertFalse(AlienXCouncil.propose(p, "starfall", AlienXActions.T_POINT), "another Strike is refused while it rests");
		helper.assertTrue(AlienXCouncil.propose(p, "stop_time", AlienXActions.T_AREA), "another category can still be moved");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theServerRefusesWhatItMust(GameTestHelper helper) {
		ServerPlayer p = alienX(helper);
		Villager v = prey(helper, 3.5, 2, 5.5, 40f);
		helper.assertFalse(AlienXCouncil.propose(p, "restore", v.getId()), "Restore takes a player, not a villager");
		helper.assertFalse(AlienXCouncil.propose(p, "no_such_action", AlienXActions.T_AREA), "an unknown action");
		helper.assertFalse(AlienXCouncil.propose(p, "unmake", AlienXActions.T_AREA), "Unmake needs a creature");
		helper.assertFalse(AlienXCouncil.propose(p, "unmake", p.getId()), "never himself");
		helper.assertTrue(AlienXCouncil.propose(p, "unmake", v.getId()), "a proper motion");
		int[] offered = AlienXCouncil.offered(p);
		helper.assertTrue(offered.length == AlienXCouncil.OFFERS, "five arguments on offer");
		int missing = -1;
		for (int t = 0; t < AlienXCouncil.TONES.size() && missing < 0; t++) {
			boolean on = false;
			for (int o : offered) {
				on |= o == t;
			}
			missing = on ? -1 : t;
		}
		helper.assertFalse(AlienXCouncil.argue(p, missing), "an argument that is not on offer is refused");
		helper.assertTrue(AlienXCouncil.meters(p)[2] == 0f, "and costs no turn");
		helper.assertTrue(AlienXCouncil.listen(p), "Listen");
		helper.assertTrue(AlienXCouncil.meters(p)[2] == 1f, "costs a turn");
		AlienXCouncil.withdraw(p);
		helper.assertTrue(AlienXCouncil.phase(p) == AlienXCouncil.PHASE_PROPOSE, "withdrawn: back to proposing");
		helper.assertTrue(v.isAlive(), "and nothing happened");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.succeed();
	}
}
