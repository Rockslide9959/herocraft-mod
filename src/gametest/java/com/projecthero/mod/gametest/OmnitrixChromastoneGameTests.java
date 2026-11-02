package com.projecthero.mod.gametest;

import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.chromastone.ChromastoneKit;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Chromastone revamp (docs/OMNITRIX_REVAMP_CHROMASTONE.md): absorbing energy into charge (blows crack him instead), the
 * Prism Beam's cost, Redirect firing a hit back at its sender, Radiant Burst (plain and charged), the Prism Shield, the UV Flare
 * burning the undead, Charged Fists, the Overload, and that turning back leaves nothing behind.
 */
public class OmnitrixChromastoneGameTests implements FabricGameTest {
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

	/** A recalibrated wearer turned into Chromastone, standing on the floor facing +z. */
	private static ServerPlayer chromastone(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 3.5, 2, 2.5);
		helper.assertTrue(Omnitrix.grant(p, true), "the Recalibrated Omnitrix goes on");
		helper.assertTrue(Omnitrix.select(p, ChromastoneKit.ID), "selected Chromastone");
		p.setOnGround(true);
		helper.assertTrue(Omnitrix.transform(p), "transformed into Chromastone");
		p.setOnGround(true);
		return p;
	}

	private static ChromastoneKit kit() {
		return (ChromastoneKit) Aliens.kit(ChromastoneKit.ID);
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 10; x++) {
			for (int z = 0; z <= 10; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

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
		m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static void near(GameTestHelper helper, float actual, float expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < 0.05f, what + " (expected " + expected + ", got " + actual + ")");
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void chromastoneAbsorbsEnergyIntoChargeAndSpendsItOnThePrismBeam(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = chromastone(helper);
		ChromastoneKit k = kit();
		Villager v = prey(helper, 3.5, 2, 6.5, 40f);
		near(helper, p.getMaxHealth(), 34f, "34 health");
		near(helper, (float) p.getAttributeValue(Attributes.ARMOR), 10f, "10 armour");
		helper.assertTrue(ChromastoneKit.charge(p) == 0, "no charge to begin with");
		helper.assertFalse(k.prismBeam(p), "no Prism Beam without charge");
		helper.assertTrue(k.cooldownLeft(p, ChromastoneKit.PRISM_BEAM) == 0, "a refused beam costs no cooldown");
		float hp = p.getHealth();
		p.hurt(helper.getLevel().damageSources().magic(), 10f);
		float taken = hp - p.getHealth();
		helper.assertTrue(taken > 0f && taken <= 10f * ChromastoneKit.ABSORB_LEAK + 0.01f, "a fifth of the magic gets through (took " + taken + ")");
		helper.assertTrue(ChromastoneKit.charge(p) == 40, "absorbed into charge: 10 x 4 = 40 (got " + ChromastoneKit.charge(p) + ")");
		p.invulnerableTime = 0;
		Zombie z = zombie(helper, 6.5, 2, 2.5, 20f);
		p.hurt(helper.getLevel().damageSources().mobAttack(z), 4f);
		helper.assertTrue(ChromastoneKit.charge(p) == 40, "a blow cracks him instead of charging him");
		helper.assertTrue(k.prismBeam(p), "with 40 charge the Prism Beam fires");
		near(helper, v.getHealth(), 40f - ChromastoneKit.PRISM_DAMAGE, "the beam does 15");
		helper.assertTrue(ChromastoneKit.charge(p) == 40 - ChromastoneKit.PRISM_COST, "and spends 20 charge");
		helper.assertFalse(k.prismBeam(p), "a second press while cooling down does nothing");
		helper.assertTrue(ChromastoneKit.charge(p) == 20, "and costs nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void chromastoneRedirectFiresAHitBackAtItsSender(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = chromastone(helper);
		ChromastoneKit k = kit();
		Zombie z = zombie(helper, 3.5, 2, 6.5, 40f);
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true); // HARD would scale the mob-caused hit by 1.5
		helper.assertTrue(k.redirect(p), "G: the Redirect window opens");
		float hp = p.getHealth();
		p.hurt(helper.getLevel().damageSources().indirectMagic(z, z), 6f);
		near(helper, p.getHealth(), hp, "the energy hit is absorbed whole");
		near(helper, z.getHealth(), 40f - 9f, "and fired back at the zombie at 1.5x (9)");
		helper.assertTrue(ChromastoneKit.charge(p) == 24, "with full charge for it (24, got " + ChromastoneKit.charge(p) + ")");
		helper.assertTrue(k.cooldownLeft(p, ChromastoneKit.REDIRECT) > 0, "Redirect cools down");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void chromastoneRadiantBurstHitsAllRoundAndHarderWithCharge(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = chromastone(helper);
		ChromastoneKit k = kit();
		Villager front = prey(helper, 3.5, 2, 6.5, 60f);
		Villager side = prey(helper, 7.5, 2, 2.5, 60f);
		Villager back = prey(helper, 3.5, 2, 0.3, 60f);
		helper.assertTrue(k.radiantBurst(p), "Z: Radiant Burst");
		near(helper, front.getHealth(), 60f - ChromastoneKit.RADIANT_DAMAGE, "the beam ahead: 13");
		near(helper, side.getHealth(), 60f - ChromastoneKit.RADIANT_DAMAGE, "the beam to the side: 13");
		near(helper, back.getHealth(), 60f - ChromastoneKit.RADIANT_DAMAGE, "the beam behind: 13");
		var n = Omnitrix.state(p).copy();
		n.timers.remove(k.key(ChromastoneKit.RADIANT_BURST));
		Omnitrix.save(p, n);
		k.setCharge(p, 30);
		front.invulnerableTime = 0;
		helper.assertTrue(k.radiantBurst(p), "again, charged");
		near(helper, front.getHealth(), 60f - ChromastoneKit.RADIANT_DAMAGE - ChromastoneKit.RADIANT_CHARGED, "with 25 charge spent: 17");
		helper.assertTrue(ChromastoneKit.charge(p) == 5, "30 - 25 = 5 charge left");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void chromastoneShieldFlareAndChargedFists(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = chromastone(helper);
		ChromastoneKit k = kit();
		Zombie undead = zombie(helper, 3.5, 2, 5.5, 40f);
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Villager v = prey(helper, 5.5, 2, 2.5, 40f);
		// Prism Shield: frontal magic absorbed whole
		k.setCharge(p, 40);
		helper.assertTrue(k.prismShield(p), "Shift+V: the Prism Shield");
		helper.assertTrue(ChromastoneKit.charge(p) == 20 && k.flag(p, ChromastoneKit.F_SHIELD), "20 charge spent, the shield up");
		float hp = p.getHealth();
		p.hurt(helper.getLevel().damageSources().indirectMagic(undead, undead), 5f);
		near(helper, p.getHealth(), hp, "energy from the front is absorbed whole");
		helper.assertTrue(ChromastoneKit.charge(p) == 40, "into charge (20 + 20)");
		// UV Flare: blinds; the undead burn
		helper.assertTrue(k.uvFlare(p), "Shift+C: the UV Flare");
		helper.assertTrue(undead.hasEffect(MobEffects.BLINDNESS) && v.hasEffect(MobEffects.BLINDNESS), "everything near is blinded");
		near(helper, undead.getHealth(), 40f - ChromastoneKit.FLARE_UNDEAD, "the zombie burns in the ultraviolet (6)");
		helper.assertTrue(undead.isOnFire(), "and is set alight");
		near(helper, v.getHealth(), 40f, "the villager is unhurt");
		helper.assertTrue(ChromastoneKit.charge(p) == 25, "15 charge spent");
		// Charged Fists
		k.onAttack(p, v);
		near(helper, v.getHealth(), 40f - ChromastoneKit.FIST_BONUS, "a charged blow flares: +3");
		helper.assertTrue(ChromastoneKit.charge(p) == 23, "for 2 charge");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void chromastoneOverloadsWhenFullAndStillAbsorbing(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = chromastone(helper);
		ChromastoneKit k = kit();
		Villager v = prey(helper, 3.5, 2, 4.0, 40f);
		k.setCharge(p, 100);
		k.addCharge(p, 8f);
		helper.assertTrue(k.flag(p, ChromastoneKit.F_OVER), "full and still absorbing: Overcharged");
		k.setCharge(p, 90);
		helper.assertFalse(k.flag(p, ChromastoneKit.F_OVER), "spending some calms it");
		k.setCharge(p, 100);
		k.addCharge(p, 8f);
		float hp = p.getHealth();
		k.overload(p, helper.getLevel());
		helper.assertTrue(ChromastoneKit.charge(p) == ChromastoneKit.OVERLOAD_LEFT, "the charge bursts down to 40");
		near(helper, v.getHealth(), 40f - ChromastoneKit.OVERLOAD_DAMAGE, "10 to what stands near");
		helper.assertTrue(p.getHealth() < hp, "and it hurts him too");
		helper.assertFalse(k.flag(p, ChromastoneKit.F_OVER), "the overload is over");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void chromastoneTurningBackLeavesNothingBehind(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = chromastone(helper);
		ChromastoneKit k = kit();
		k.setCharge(p, 80);
		k.prismShield(p);
		k.redirect(p);
		k.flight(p);
		helper.assertTrue(k.flag(p, ChromastoneKit.F_SHIELD) && k.flag(p, ChromastoneKit.F_REDIRECT) && p.getAbilities().flying, "shielded, redirecting, flying");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.assertTrue(Omnitrix.state(p).kitFlags == 0, "no kit flags left");
		helper.assertTrue(ChromastoneKit.charge(p) == 0 && k.timer(p, ChromastoneKit.SHIELD_END) == 0 && k.timer(p, ChromastoneKit.REDIRECT_END) == 0,
				"no charge or timers left");
		helper.assertFalse(p.getAbilities().mayfly || p.getAbilities().flying, "no flight left");
		helper.succeed();
	}
}
