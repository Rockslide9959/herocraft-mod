package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.bigchill.BigChillKit;
import com.projecthero.mod.omnitrix.alien.chromastone.ChromastoneKit;
import com.projecthero.mod.omnitrix.alien.jetray.JetrayKit;

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
 * v0.17.2 the Recalibrated Omnitrix, group B -- Jetray, Big Chill and Chromastone (basic versions): their bodies on a
 * recalibrated watch, each signature (Jetray's flight on / off, Big Chill's freeze and phase, Chromastone absorbing a hit into
 * charge and spending it), one move's damage and cooldown each, and that turning back leaves nothing behind. Mock players are
 * not ticked by the server, so tests over time drive {@link Omnitrix#tick} themselves.
 */
public class OmnitrixRecalBGameTests implements FabricGameTest {
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

	/** A recalibrated wearer turned into {@code alien}, standing on the floor facing +z. */
	private static ServerPlayer as(GameTestHelper helper, String alien) {
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p, true), "the recalibrated Omnitrix goes on");
		helper.assertTrue(Omnitrix.recalibrated(p), "the watch is recalibrated");
		helper.assertTrue(Omnitrix.select(p, alien), "selected " + alien + " on the recalibrated dial");
		p.setOnGround(true);
		helper.assertTrue(Omnitrix.transform(p), "transformed into " + alien);
		p.setOnGround(true);
		return p;
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
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

	private static void clearCooldown(ServerPlayer p, AlienKit kit, String move) {
		var n = Omnitrix.state(p).copy();
		n.timers.remove(kit.key(move));
		Omnitrix.save(p, n);
	}

	// ================================================================ bodies

	@GameTest(template = EMPTY_STRUCTURE)
	public void recalBBodiesAreOnTheRecalibratedDial(GameTestHelper helper) {
		floor(helper);
		for (Aliens.Alien a : new Aliens.Alien[] { Aliens.JETRAY, Aliens.BIG_CHILL, Aliens.CHROMASTONE }) {
			helper.assertTrue(a.recalibrated(), a.id() + " is an Alien Force alien");
			helper.assertTrue(Aliens.kit(a.id()) != null, a.id() + " has a kit (no longer coming soon)");
		}
		ServerPlayer j = as(helper, JetrayKit.ID);
		near(helper, j.getMaxHealth(), 24f, "Jetray: 24 health");
		near(helper, (float) j.getAttributeValue(Attributes.ARMOR), 7f, "Jetray: 7 armour");
		ServerPlayer b = as(helper, BigChillKit.ID);
		near(helper, b.getMaxHealth(), 28f, "Big Chill: 28 health");
		near(helper, (float) b.getAttributeValue(Attributes.ARMOR), 10f, "Big Chill: gold-level armour 10");
		ServerPlayer c = as(helper, ChromastoneKit.ID);
		near(helper, c.getMaxHealth(), 34f, "Chromastone: 34 health");
		near(helper, (float) c.getAttributeValue(Attributes.ARMOR), 15f, "Chromastone: 15 armour");
		for (ServerPlayer p : new ServerPlayer[] { j, b, c }) {
			AlienKit k = AlienKit.activeKit(p);
			helper.assertTrue(k != null && k.moves().stream().noneMatch(m -> m.slot() == AbilitySlot.SLOT_7 || m.slot() == AbilitySlot.SLOT_8),
					"no H or N moves");
			helper.assertTrue(k.moveOn(AbilitySlot.SLOT_5, false, false) != null, k.id() + " has a tap V move");
		}
		helper.succeed();
	}

	// ================================================================ Jetray

	@GameTest(template = EMPTY_STRUCTURE)
	public void jetrayFliesOnXAndLandsAgain(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, JetrayKit.ID);
		JetrayKit kit = (JetrayKit) Aliens.kit(JetrayKit.ID);
		helper.assertTrue(p.getAbilities().mayfly, "the wings are ready");
		helper.assertFalse(p.getAbilities().flying, "on the ground he stays down");
		kit.press(p, AbilitySlot.SLOT_3, false);
		helper.assertTrue(p.getAbilities().flying && kit.flag(p, JetrayKit.F_FLYING), "X takes off");
		helper.assertTrue(Math.abs(p.getAbilities().getFlyingSpeed() - JetrayKit.FLY_SPEED) < 1.0e-4f, "fast flight");
		helper.assertFalse(kit.flight(p), "X has a short cooldown");
		clearCooldown(p, kit, JetrayKit.FLIGHT);
		helper.assertTrue(kit.flight(p) && !p.getAbilities().flying && !kit.flag(p, JetrayKit.F_FLYING), "X again lands");
		float hp = p.getHealth();
		p.hurt(helper.getLevel().damageSources().fall(), 12f);
		near(helper, p.getHealth(), hp, "no fall damage");
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().drown(), 4f);
		near(helper, p.getHealth(), hp, "never drowns");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void jetrayNeuroshockEyesHitAndCoolDown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, JetrayKit.ID);
		JetrayKit kit = (JetrayKit) Aliens.kit(JetrayKit.ID);
		Villager v = prey(helper, 3.5, 2, 6.5, 40f);
		helper.assertTrue(kit.eyes(p), "the eye beam fires");
		near(helper, v.getHealth(), 40f - JetrayKit.EYES_DAMAGE, "the beam does 11");
		helper.assertTrue(Omnitrix.state(p).animId == JetrayKit.ANIM_EYES, "and plays its pose");
		helper.assertTrue(kit.cooldownLeft(p, JetrayKit.EYES) > 0, "the beam cools down");
		v.invulnerableTime = 0;
		helper.assertFalse(kit.eyes(p), "a second press while cooling down does nothing");
		near(helper, v.getHealth(), 40f - JetrayKit.EYES_DAMAGE, "no second hit");
		helper.succeed();
	}

	// ================================================================ turning back

	@GameTest(template = EMPTY_STRUCTURE)
	public void recalBTurningBackLeavesNothingBehind(GameTestHelper helper) {
		floor(helper);
		// Jetray: flying, streamlined
		ServerPlayer j = as(helper, JetrayKit.ID);
		JetrayKit jk = (JetrayKit) Aliens.kit(JetrayKit.ID);
		jk.flight(j);
		jk.press(j, AbilitySlot.SLOT_5, false);
		helper.assertTrue(jk.flag(j, JetrayKit.F_STREAM) && j.getAbilities().flying, "Jetray flying in Streamline");
		Omnitrix.revert(j, Omnitrix.Reason.SHIFT_V);
		helper.assertFalse(Omnitrix.state(j).transformed(), "Jetray turned back");
		helper.assertFalse(j.getAbilities().mayfly || j.getAbilities().flying, "no flight left");
		helper.assertTrue(Math.abs(j.getAbilities().getFlyingSpeed() - 0.05f) < 1.0e-4f, "normal flying speed");
		helper.assertTrue(Omnitrix.state(j).kitFlags == 0, "no kit flags left");
		// Big Chill: phased and cloaked
		ServerPlayer b = as(helper, BigChillKit.ID);
		BigChillKit bk = (BigChillKit) Aliens.kit(BigChillKit.ID);
		bk.phase(b);
		bk.press(b, AbilitySlot.SLOT_5, false);
		helper.assertTrue(bk.phasing(b) && bk.flag(b, BigChillKit.F_CLOAK) && b.hasEffect(MobEffects.INVISIBILITY), "Big Chill phased and cloaked");
		Omnitrix.revert(b, Omnitrix.Reason.SHIFT_V);
		helper.assertFalse(bk.phasing(b), "not phasing after turning back");
		helper.assertFalse(b.hasEffect(MobEffects.INVISIBILITY), "the cloak's invisibility is gone");
		helper.assertFalse(b.getAbilities().mayfly, "no wings left");
		helper.assertTrue(Omnitrix.state(b).kitFlags == 0, "no kit flags left");
		helper.assertTrue(bk.timer(b, "phase_end") == 0 && bk.timer(b, "cloak_end") == 0, "no running timers left");
		// Chromastone: charged, in the stance
		ServerPlayer c = as(helper, ChromastoneKit.ID);
		ChromastoneKit ck = (ChromastoneKit) Aliens.kit(ChromastoneKit.ID);
		ck.setCharge(c, 70);
		ck.holdStart(c, AbilitySlot.SLOT_5);
		helper.assertTrue(ck.flag(c, ChromastoneKit.F_STANCE), "Chromastone in the Absorb Stance");
		Omnitrix.revert(c, Omnitrix.Reason.SHIFT_V);
		helper.assertTrue(ChromastoneKit.charge(c) == 0 && ck.timer(c, ChromastoneKit.CHARGE) == 0, "the charge is gone");
		helper.assertTrue(Omnitrix.state(c).kitFlags == 0, "no kit flags left");
		helper.assertTrue(ck.timer(c, "stance_end") == 0, "no running timers left");
		near(helper, c.getMaxHealth(), 20f, "a human's 20 health again");
		helper.succeed();
	}
}
