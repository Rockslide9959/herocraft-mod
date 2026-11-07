package com.projecthero.mod.gametest;

import com.projecthero.mod.doomsday.entity.DoomsdayEntity;
import com.projecthero.mod.doomsday.entity.DoomsdayEntityTypes;
import com.projecthero.mod.kryptonian.Kryptonian;
import com.projecthero.mod.kryptonian.KryptoniteStrikes;
import com.projecthero.mod.kryptonian.item.KryptoniteTools;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "make it so that you can craft tools from kryptonite, make them have iron level durability but diamond level
 * efficiency and power and stuff, make it deal 2 times damage to kryptonian beings(this includes doomsday please), should
 * completely bypass their defences, should stop their healing and powers for 20 seconds after getting hit 3 times").
 */
public class KryptoniteToolGameTests implements FabricGameTest {
	/** A no-AI zombie with a kryptonite sword: its mob-attack hits are kryptonite-tool hits. */
	private static Zombie wielder(GameTestHelper helper) {
		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
		z.moveTo(at.x, at.y, at.z, 0f, 0f);
		z.setNoAi(true);
		z.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(KryptoniteTools.SWORD));
		helper.getLevel().addFreshEntity(z);
		return z;
	}

	private static void near(GameTestHelper helper, float got, float want, String what) {
		helper.assertTrue(Math.abs(got - want) < 0.05f, what + " (expected " + want + ", got " + got + ")");
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void kryptoniteToolsHaveIronDurabilityAndDiamondPower(GameTestHelper helper) {
		ItemStack sword = new ItemStack(KryptoniteTools.SWORD);
		helper.assertTrue(sword.getMaxDamage() == 250, "iron durability: " + sword.getMaxDamage());
		var attrs = sword.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
		double[] dmg = { 0 };
		attrs.forEach(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (a, m) -> {
			if (a.is(Attributes.ATTACK_DAMAGE)) {
				dmg[0] += m.amount();
			}
		});
		near(helper, (float) dmg[0], 6f, "diamond sword damage bonus (7 with the fist)");
		ItemStack pick = new ItemStack(KryptoniteTools.PICKAXE);
		near(helper, pick.getDestroySpeed(Blocks.STONE.defaultBlockState()), 8f, "diamond mining speed");
		helper.assertTrue(pick.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()), "mines obsidian like diamond");
		helper.assertTrue(KryptoniteTools.TIER.getRepairIngredient().test(new ItemStack(com.projecthero.mod.kryptonian.item.KryptonianItems.KRYPTONITE_SHARD)),
				"repaired with shards");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void kryptoniteDoublesOnDoomsdayThroughHisDefencesAndPoisonsOnTheThirdHit(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		DoomsdayEntity d = DoomsdayEntityTypes.DOOMSDAY.create(level);
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 2, 4.5));
		d.moveTo(at.x, at.y, at.z, 0f, 0f);
		d.setNoAi(true);
		d.configure(1);
		level.addFreshEntity(d);
		Zombie z = wielder(helper);
		try {
			for (int i = 1; i <= 3; i++) {
				float before = d.getHealth();
				d.invulnerableTime = 0;
				helper.assertTrue(d.hurt(level.damageSources().mobAttack(z), 10f), "strike " + i + " lands");
				near(helper, before - d.getHealth(), 20f, "x2 straight through his 12 armour / 8 toughness, strike " + i);
				helper.assertTrue(KryptoniteStrikes.suppressed(d) == (i == 3), "poisoned only on the 3rd strike (" + i + ")");
			}
			helper.assertTrue(d.adaptation().mask() == 0, "the strikes taught him nothing");
			float hp = d.getHealth();
			d.heal(500f);
			near(helper, d.getHealth(), hp, "no healing while poisoned");
		} finally {
			d.discard();
			z.discard();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aKryptonianTakesDoubleFullDamageAndLosesHisPowersOnTheThirdHit(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true); // its neighbour above leaves the world on Hard
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		Kryptonian.grant(p);
		p.setHealth(p.getMaxHealth());
		Zombie bare = EntityType.ZOMBIE.create(helper.getLevel());
		bare.moveTo(at.x + 1, at.y, at.z, 0f, 0f);
		bare.setNoAi(true);
		helper.getLevel().addFreshEntity(bare);
		Zombie z = wielder(helper);
		try {
			float full = p.getHealth();
			p.invulnerableTime = 0;
			p.hurt(helper.getLevel().damageSources().mobAttack(bare), 10f);
			helper.assertTrue(full - p.getHealth() < 3f, "a bare hit is still cut by 80%: took " + (full - p.getHealth()));
			p.setHealth(p.getMaxHealth());
			for (int i = 1; i <= 3; i++) {
				float before = p.getHealth();
				p.invulnerableTime = 0;
				p.hurt(helper.getLevel().damageSources().mobAttack(z), 2f);
				if (i < 3) {
					near(helper, before - p.getHealth(), 4f, "x2 with no reduction, strike " + i);
				} else {
					// the 3rd also strips his powers, and with them his extra max health (clamped, as after a Solar Flare)
					helper.assertTrue(before - p.getHealth() >= 3.95f, "x2 with no reduction, strike 3: took " + (before - p.getHealth()));
				}
			}
			helper.assertTrue(KryptoniteStrikes.suppressed(p) && !Kryptonian.empowered(p), "three strikes: powerless");
			float hp = p.getHealth();
			p.heal(6f);
			near(helper, p.getHealth(), hp, "no healing while poisoned");
		} finally {
			z.discard();
			bare.discard();
		}
		helper.succeed();
	}
}
