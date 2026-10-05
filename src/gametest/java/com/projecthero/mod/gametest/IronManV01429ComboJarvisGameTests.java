package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.ironman.IronManCombo;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.JarvisDialogue;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManAbilities;
import com.projecthero.mod.ironman.ability.IronManAbilityManager;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * v0.14.29 (agent F): the repulsor -&gt; melee stagger combo and the JARVIS voice-line triggers.
 */
public class IronManV01429ComboJarvisGameTests implements FabricGameTest {

	private static ServerPlayer suited(GameTestHelper h, String suitId) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		p.setYRot(0f);
		p.setYHeadRot(0f);
		p.setXRot(0f);
		TonyStark.grant(p);
		for (ArmorItem.Type t : ArmorItem.Type.values()) {
			var item = IronManItems.armor(suitId, t);
			if (item != null) {
				p.setItemSlot(IronManSuitUpManager.slotFor(t), new ItemStack(item));
			}
		}
		IronManEnergy.setEnergy(p, suitId, IronManEnergy.capacity(suitId));
		IronManEnergy.setIntegrity(p, suitId, IronManEnergy.maxIntegrity(suitId));
		return p;
	}

	private static Zombie zombieAhead(GameTestHelper h, ServerPlayer p, double distance) {
		h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Zombie z = EntityType.ZOMBIE.create(h.getLevel());
		z.moveTo(p.getX(), p.getY(), p.getZ() + distance);
		z.setNoAi(true);
		h.getLevel().addFreshEntity(z);
		return z;
	}

	// ---------------------------------------------------------------- combo

	@GameTest(template = EMPTY_STRUCTURE)
	public void repulsorHitStaggers(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_2");
		Zombie z = zombieAhead(h, p, 4.0);
		float hp = z.getHealth();
		IronManAbilities.fireHandRepulsor(p);
		h.assertTrue(z.getHealth() < hp, "the repulsor must hit the zombie");
		h.assertTrue(IronManCombo.isStaggered(z), "a repulsor hit staggers the target");
		h.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "a staggered target is briefly slowed");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void punchOnStaggeredTargetDealsBonusAndConsumes(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_2");
		Zombie z = zombieAhead(h, p, 1.5);
		IronManCombo.onRepulsorHit(p, z);
		h.assertTrue(IronManCombo.isStaggered(z), "staggered");
		z.invulnerableTime = 0;
		float hp = z.getHealth();
		p.attack(z);
		float lost = hp - z.getHealth();
		h.assertFalse(IronManCombo.isStaggered(z), "the punch consumes the stagger");
		h.assertTrue(IronManCombo.bonusDamage(IronManSuits.MARK_2) >= IronManCombo.MIN_BONUS, "bonus at least 3");
		// the zombie's 2 natural armour shaves the 3 bonus to ~2.76, plus the (weak, un-charged) punch itself
		h.assertTrue(lost >= 2.7f, "the punch deals the stagger bonus (lost " + lost + ")");

		// a second punch with no stagger: just the plain punch
		z.invulnerableTime = 0;
		float hp2 = z.getHealth();
		p.attack(z);
		h.assertTrue(hp2 - z.getHealth() < 2.6f, "no bonus without a stagger (lost " + (hp2 - z.getHealth()) + ")");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void staggerExpires(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_iii");
		Zombie z = zombieAhead(h, p, 3.0);
		IronManCombo.onRepulsorHit(p, z);
		h.assertTrue(IronManCombo.isStaggered(z), "staggered");
		h.runAfterDelay(IronManCombo.STAGGER_TICKS + 2, () -> {
			h.assertFalse(IronManCombo.isStaggered(z), "the stagger mark wears off after 3 s");
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markOneStrongPunchStaggers(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_1");
		Zombie z = zombieAhead(h, p, 2.0);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
		h.assertTrue(IronManCombo.isStaggered(z), "the Mark 1 Strong Punch applies the stagger (no repulsor)");
		h.succeed();
	}

	// ---------------------------------------------------------------- JARVIS

	@GameTest(template = EMPTY_STRUCTURE)
	public void jarvisEnergyLineFiresOnceAndRespectsCooldown(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_iii");
		JarvisDialogue.clearFor(p);
		JarvisDialogue.tick(p); // baseline: logging in suited says nothing
		h.assertTrue(JarvisDialogue.spokenCount(p) == 0, "the first look is only a baseline");
		IronManEnergy.setEnergy(p, "mark_iii", IronManEnergy.capacity("mark_iii") * 0.2f);
		JarvisDialogue.tick(p);
		h.assertTrue("energy_25".equals(JarvisDialogue.lastLine(p)), "energy under 25% -> energy_25 (got " + JarvisDialogue.lastLine(p) + ")");
		int n = JarvisDialogue.spokenCount(p);
		JarvisDialogue.tick(p);
		JarvisDialogue.tick(p);
		h.assertTrue(JarvisDialogue.spokenCount(p) == n, "it fires once, not every evaluation");
		IronManEnergy.setEnergy(p, "mark_iii", IronManEnergy.capacity("mark_iii"));
		JarvisDialogue.tick(p);
		IronManEnergy.setEnergy(p, "mark_iii", IronManEnergy.capacity("mark_iii") * 0.2f);
		JarvisDialogue.tick(p);
		h.assertTrue(JarvisDialogue.spokenCount(p) == n, "a second crossing inside the cooldown stays quiet");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void jarvisUrgentLineCutsInOnTheGap(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_iii");
		JarvisDialogue.clearFor(p);
		JarvisDialogue.tick(p);
		IronManEnergy.setEnergy(p, "mark_iii", IronManEnergy.capacity("mark_iii") * 0.2f);
		JarvisDialogue.tick(p);
		IronManEnergy.setIntegrity(p, "mark_iii", IronManEnergy.maxIntegrity("mark_iii") * 0.3f);
		JarvisDialogue.tick(p);
		h.assertTrue("integrity_35".equals(JarvisDialogue.lastLine(p)), "integrity under 35% speaks even inside the gap (got "
				+ JarvisDialogue.lastLine(p) + ")");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markOneOnlyGetsTheCrudeSystemReadout(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_1");
		JarvisDialogue.clearFor(p);
		JarvisDialogue.tick(p);
		IronManEnergy.setEnergy(p, "mark_1", IronManEnergy.capacity("mark_1") * 0.2f);
		JarvisDialogue.tick(p);
		h.assertTrue("system_energy_low".equals(JarvisDialogue.lastLine(p)), "Mark 1 -> crude SYSTEM line (got "
				+ JarvisDialogue.lastLine(p) + ")");
		h.succeed();
	}
}
