package com.projecthero.mod.gametest;

import com.projecthero.mod.carnage.CarnageEntityTypes;
import com.projecthero.mod.carnage.entity.CarnageEntity;
import com.projecthero.mod.punisher.PunisherConfig;
import com.projecthero.mod.spider.SpiderWebs;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/** v0.15.19: Punisher melee retune and Carnage ignoring webs. */
public class V01519GameTests implements FabricGameTest {

	@GameTest(template = EMPTY_STRUCTURE)
	public void punisherMeleeRetune(GameTestHelper helper) {
		helper.assertTrue(PunisherConfig.BRUTAL_STRIKE_DAMAGE == 10f, "Brutal Strike 10");
		helper.assertTrue(PunisherConfig.BREACH_KICK_DAMAGE == 15f, "Breach Kick (Shift+G) 15");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void carnageIgnoresWebs(GameTestHelper helper) {
		CarnageEntity c = CarnageEntityTypes.CARNAGE.create(helper.getLevel());
		c.moveTo(helper.absoluteVec(new Vec3(1.5, 2, 1.5)), 0f, 0f);
		c.configure(1);
		c.setNoAi(true);
		c.setInvulnerable(true);
		helper.getLevel().addFreshEntity(c);
		helper.assertTrue(SpiderWebs.movesFreelyThroughWebbing(c), "cobweb blocks and Web Nets do not hold Carnage");
		helper.assertTrue(SpiderWebs.cocoonFor(null, c, 100) == 0, "he cannot be cocooned");
		helper.assertFalse(c.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and is not slowed");

		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		z.moveTo(helper.absoluteVec(new Vec3(3.5, 2, 3.5)), 0f, 0f);
		z.setNoAi(true);
		helper.getLevel().addFreshEntity(z);
		helper.assertFalse(SpiderWebs.movesFreelyThroughWebbing(z), "an ordinary mob is still caught");
		c.discard();
		z.discard();
		helper.succeed();
	}
}
