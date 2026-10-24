package com.projecthero.mod.gametest;

import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternArsenal;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.GreenLanternEnergy;
import com.projecthero.mod.greenlantern.GreenLanternRanks;
import com.projecthero.mod.greenlantern.bind.GreenLanternBinding;
import com.projecthero.mod.greenlantern.bind.GreenLanternBindings;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.greenlantern.entity.HardLightConstructEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Green Lantern revamp: Construct Menu bindings (defaults, binding, clearing, reset, the plain-C tap rule),
 * Lantern Ranks (thresholds, multipliers, XP from kills, kept on ring removal) and a few of the new constructs.
 */
public class V0171GreenLanternGameTests implements FabricGameTest {
	@SuppressWarnings("removal")
	private static ServerPlayer lantern(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(7, 1, 7)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
		player.moveTo(at.x, at.y, at.z, 0f, 0f);
		player.setYHeadRot(0f);
		GreenLantern.bond(player);
		// a half-full ring: no Willpower Surge either way, so damage numbers are exact
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE * 0.5f;
		GreenLantern.save(player, s);
		return player;
	}

	private static Husk husk(GameTestHelper helper, double x, double z) {
		Husk h = EntityType.HUSK.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		h.moveTo(at.x, at.y, at.z, 180f, 0f);
		h.setNoAi(true);
		h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		h.getAttribute(Attributes.ARMOR).setBaseValue(0);
		h.setHealth(200);
		helper.getLevel().addFreshEntity(h);
		return h;
	}

	// ---------------------------------------------------------------- bindings

	@GameTest(template = EMPTY_STRUCTURE)
	public void defaultsKeepTheOldLayout(GameTestHelper helper) {
		GreenLanternState fresh = new GreenLanternState();
		helper.assertTrue(GreenLanternBinding.resolve(fresh, "G").id().equals(GreenLanternBinding.FIST), "G = Construct Fist");
		helper.assertTrue(GreenLanternBinding.resolve(fresh, "SZ").id().equals(GreenLanternBinding.DOME), "Shift+Z = Dome");
		helper.assertTrue(GreenLanternBinding.resolve(fresh, "H").id().equals(GreenLanternBinding.SUIT), "H = Suit");
		helper.assertTrue(GreenLanternBinding.resolve(fresh, "V").id().equals(GreenLanternBinding.GIANT_HAND), "V = Giant Hand");
		helper.assertTrue(GreenLanternBinding.resolve(fresh, "C").id().equals(GreenLanternBinding.QUICK_DEPLOY), "C = Quick Deploy");
		helper.assertTrue(GreenLanternBinding.resolve(fresh, "SH") == null, "Shift+H is free (the power wheel)");
		helper.assertTrue(GreenLanternBinding.all().size() == 14 + ConstructType.values().length, "every construct is in the menu");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bindClearAndReset(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		String glove = GreenLanternBinding.forConstruct(ConstructType.BOXING_GLOVE).id();
		GreenLanternBindings.bind(p, "SG", glove);
		helper.assertTrue(GreenLanternBinding.resolve(GreenLantern.state(p), "SG").id().equals(glove), "Shift+G holds the glove");
		// plain C refuses a hold entry
		String cannon = GreenLanternBinding.forConstruct(ConstructType.EMERALD_CANNON).id();
		GreenLanternBindings.bind(p, "C", cannon);
		helper.assertTrue(GreenLanternBinding.resolve(GreenLantern.state(p), "C").id().equals(GreenLanternBinding.QUICK_DEPLOY),
				"plain C keeps its tap entry");
		GreenLanternBindings.bind(p, "SC", cannon);
		helper.assertTrue(GreenLanternBinding.resolve(GreenLantern.state(p), "SC").id().equals(cannon), "Shift+C may hold it");
		GreenLanternBindings.clearEntry(p, glove);
		helper.assertTrue(GreenLanternBinding.resolve(GreenLantern.state(p), "SG") == null, "right-click clears the key");
		GreenLanternBindings.reset(p);
		helper.assertTrue(GreenLanternBinding.resolve(GreenLantern.state(p), "SG").id().equals(GreenLanternBinding.HAMMER), "reset = defaults");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void aBoundKeyFiresItsConstruct(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		Husk h = husk(helper, 1.5, 6.5);
		GreenLanternBindings.bind(p, "G", GreenLanternBinding.forConstruct(ConstructType.BOXING_GLOVE).id());
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, h.getEyePosition());
		GreenLanternBindings.key(p, 'G', false, true);
		GreenLanternBindings.key(p, 'G', false, false);
		float dealt = 200f - h.getHealth();
		helper.assertTrue(Math.abs(dealt - GreenLanternConfig.GLOVE_DAMAGE) < 0.6f, "the glove punched for 20, dealt " + dealt);
		helper.succeed();
	}

	// ---------------------------------------------------------------- Lantern Ranks

	@GameTest(template = EMPTY_STRUCTURE)
	public void ranksScaleDamageChargeAndCost(GameTestHelper helper) {
		helper.assertTrue(GreenLanternRanks.rankFor(0) == 1 && GreenLanternRanks.rankFor(1499) == 1 && GreenLanternRanks.rankFor(1500) == 2
				&& GreenLanternRanks.rankFor(25000) == 5, "thresholds 0 / 1,500 / 5,000 / 12,000 / 25,000");
		ServerPlayer p = lantern(helper);
		GreenLanternRanks.setRank(p, 5);
		helper.assertTrue(GreenLanternRanks.rank(p) == 5, "rank set");
		helper.assertTrue(Math.abs(GreenLanternRanks.maxCharge(p) - GreenLanternConfig.MAX_RING_CHARGE * 1.4f) < 1f, "x1.4 pool");
		helper.assertTrue(Math.abs(GreenLanternRanks.power(p) - 1.25f) < 0.001f, "+25% damage at half charge (v0.17.1 balance)");
		float before = GreenLanternEnergy.get(p);
		GreenLanternEnergy.spend(p, 100f);
		helper.assertTrue(Math.abs(before - GreenLanternEnergy.get(p) - 80f) < 0.01f, "costs x0.8");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void surgeAndXpKeptOnRingRemoval(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternState s = GreenLantern.state(p).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
		GreenLantern.save(p, s);
		helper.assertTrue(Math.abs(GreenLanternRanks.power(p) - 1.15f) < 0.001f, "full ring: +15% Willpower Surge");
		GreenLanternRanks.award(p, 2000);
		helper.assertTrue(GreenLanternRanks.rank(p) == 2, "2,000 XP is Lantern");
		GreenLantern.removeRing(p);
		helper.assertTrue(GreenLantern.state(p).willpowerXp == 2000L, "XP survives taking the ring off");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void killsEarnWillpowerXp(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		Husk h = husk(helper, 1.5, 3.5);
		h.setHealth(5f);
		h.hurt(p.damageSources().playerAttack(p), 50f);
		long xp = GreenLantern.state(p).willpowerXp;
		helper.assertTrue(xp >= GreenLanternConfig.XP_PER_KILL, "a kill gives at least 50 XP, got " + xp);
		helper.succeed();
	}

	// ---------------------------------------------------------------- new constructs

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void bearTrapRootsAndBites(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		p.setXRot(60f);
		GreenLanternConstructs.deploy(p, ConstructType.BEAR_TRAP);
		helper.assertTrue(GreenLanternArsenal.liveCount(p.getUUID(), HardLightConstructEntity.Shape.BEAR_TRAP) == 1, "a trap is set");
		HardLightConstructEntity trap = GreenLanternArsenal.live(p.getUUID()).get(0);
		Husk h = EntityType.HUSK.create(helper.getLevel());
		h.moveTo(trap.getX(), trap.getY(), trap.getZ(), 0f, 0f);
		h.setNoAi(true);
		h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		h.setHealth(200);
		helper.getLevel().addFreshEntity(h);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(200f - h.getHealth() >= GreenLanternConfig.TRAP_DAMAGE - 0.6f, "bitten for 16, health " + h.getHealth());
			helper.assertTrue(h.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "and rooted");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void tentaclesGrabAndSlam(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		Husk h = husk(helper, 4.5, 4.5);
		GreenLanternConstructs.deploy(p, ConstructType.EMERALD_TENTACLES);
		helper.runAfterDelay(GreenLanternConfig.TENTACLES_HOLD_TICKS + 4, () -> {
			helper.assertTrue(200f - h.getHealth() >= GreenLanternConfig.TENTACLES_DAMAGE - 0.6f, "slammed for 18, health " + h.getHealth());
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void maceTogglesAndDismissClearsIt(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternConstructs.deploy(p, ConstructType.EMERALD_MACE);
		helper.assertTrue(GreenLanternArsenal.maceOn(p), "the mace is out");
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) >= GreenLanternConfig.MACE_DAMAGE - 0.01, "18 melee");
		com.projecthero.mod.greenlantern.GreenLanternAbilityManager.clearConstructs(p);
		GreenLanternArsenal.tick(p);
		helper.assertFalse(GreenLanternArsenal.maceOn(p), "N dismisses it");
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) < GreenLanternConfig.MACE_DAMAGE - 1, "and its damage goes");
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(HardLightConstructEntity.class, new AABB(p.blockPosition()).inflate(8),
				e -> e.shape() == HardLightConstructEntity.Shape.MACE && !e.isRemoved()).isEmpty(), "no mace model left");
		helper.succeed();
	}
}
