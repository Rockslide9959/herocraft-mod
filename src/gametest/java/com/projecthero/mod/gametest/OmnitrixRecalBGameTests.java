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
		near(helper, (float) j.getAttributeValue(Attributes.ARMOR), 3f, "Jetray: 3 armour");
		ServerPlayer b = as(helper, BigChillKit.ID);
		near(helper, b.getMaxHealth(), 28f, "Big Chill: 28 health");
		near(helper, (float) b.getAttributeValue(Attributes.ARMOR), 4f, "Big Chill: 4 armour");
		ServerPlayer c = as(helper, ChromastoneKit.ID);
		near(helper, c.getMaxHealth(), 34f, "Chromastone: 34 health");
		near(helper, (float) c.getAttributeValue(Attributes.ARMOR), 10f, "Chromastone: 10 armour");
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
		near(helper, v.getHealth(), 40f - JetrayKit.EYES_DAMAGE, "the beam does 7");
		helper.assertTrue(Omnitrix.state(p).animId == JetrayKit.ANIM_EYES, "and plays its pose");
		helper.assertTrue(kit.cooldownLeft(p, JetrayKit.EYES) > 0, "the beam cools down");
		v.invulnerableTime = 0;
		helper.assertFalse(kit.eyes(p), "a second press while cooling down does nothing");
		near(helper, v.getHealth(), 40f - JetrayKit.EYES_DAMAGE, "no second hit");
		helper.succeed();
	}

	// ================================================================ Big Chill

	@GameTest(template = EMPTY_STRUCTURE)
	public void bigChillFreezingBreathChillsAndCoolsDown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, BigChillKit.ID);
		BigChillKit kit = (BigChillKit) Aliens.kit(BigChillKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 4.5, 40f);
		helper.assertTrue(kit.breath(p), "the breath fires");
		near(helper, z.getHealth(), 40f - BigChillKit.BREATH_DAMAGE, "the breath does 12");
		helper.assertTrue(z.getTicksFrozen() >= z.getTicksRequiredToFreeze(), "the zombie is frosted over (chilled)");
		helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and slowed");
		helper.assertFalse(BigChillKit.shelled(z), "not frozen solid by one breath");
		helper.assertFalse(kit.breath(p), "the breath cools down");
		// a chilled creature hit by more cold freezes solid
		BigChillKit.chill(p, z, 40);
		helper.assertTrue(BigChillKit.shelled(z), "chilled again: frozen solid (the ice shell)");
		var slow = z.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		helper.assertTrue(slow != null && slow.getAmplifier() >= 9, "and rooted");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bigChillPhasesAndShrugsOffTheCold(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, BigChillKit.ID);
		BigChillKit kit = (BigChillKit) Aliens.kit(BigChillKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 3.5, 20f);
		float hp = p.getHealth();
		p.hurt(helper.getLevel().damageSources().freeze(), 5f);
		near(helper, p.getHealth(), hp, "the cold never touches him");
		p.setTicksFrozen(120);
		Omnitrix.tick(p);
		helper.assertTrue(p.getTicksFrozen() == 0, "powder snow cannot freeze him");
		helper.assertFalse(kit.phasing(p), "solid to begin with");
		kit.press(p, AbilitySlot.SLOT_3, true);
		helper.assertTrue(kit.phasing(p) && kit.flag(p, BigChillKit.F_PHASE), "Shift+X: intangible (the phasing hook)");
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().mobAttack(z), 6f);
		near(helper, p.getHealth(), hp, "a blow passes through him while phased");
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().magic(), 2f);
		helper.assertTrue(p.getHealth() < hp, "magic still reaches him");
		// the 5 s run out
		var n = Omnitrix.state(p).copy();
		n.timers.put(kit.key("phase_end"), helper.getLevel().getGameTime());
		Omnitrix.save(p, n);
		Omnitrix.tick(p);
		helper.assertFalse(kit.phasing(p), "solid again when the phase ends");
		helper.assertTrue(kit.cooldownLeft(p, BigChillKit.PHASE_MOVE) > 0, "and Phase cools down");
		helper.succeed();
	}

	// ================================================================ Chromastone

	@GameTest(template = EMPTY_STRUCTURE)
	public void chromastoneAbsorbsAHitIntoChargeAndSpendsIt(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, ChromastoneKit.ID);
		ChromastoneKit kit = (ChromastoneKit) Aliens.kit(ChromastoneKit.ID);
		Villager v = prey(helper, 3.5, 2, 6.5, 40f);
		helper.assertTrue(ChromastoneKit.charge(p) == 0, "no charge to begin with");
		helper.assertFalse(kit.prismBeam(p), "no Prism Beam without charge");
		helper.assertTrue(kit.cooldownLeft(p, ChromastoneKit.PRISM_BEAM) == 0, "a refused beam costs no cooldown");
		float hp = p.getHealth();
		p.hurt(helper.getLevel().damageSources().magic(), 10f);
		float taken = hp - p.getHealth();
		helper.assertTrue(taken > 0f && taken <= 10f * ChromastoneKit.ABSORB_LEAK + 0.01f, "a fifth of the magic gets through (took " + taken + ")");
		helper.assertTrue(ChromastoneKit.charge(p) == 40, "absorbed into charge: 10 x 4 = 40 (got " + ChromastoneKit.charge(p) + ")");
		// blunt force is not absorbed
		p.invulnerableTime = 0;
		Zombie z = zombie(helper, 6.5, 2, 1.5, 20f);
		p.hurt(helper.getLevel().damageSources().mobAttack(z), 4f);
		helper.assertTrue(ChromastoneKit.charge(p) == 40, "a blow cracks him instead of charging him");
		helper.assertTrue(kit.prismBeam(p), "with 40 charge the Prism Beam fires");
		near(helper, v.getHealth(), 40f - ChromastoneKit.PRISM_DAMAGE, "the beam does 15");
		helper.assertTrue(ChromastoneKit.charge(p) == 40 - ChromastoneKit.PRISM_COST, "and spends 20 charge");
		helper.assertTrue(kit.cooldownLeft(p, ChromastoneKit.PRISM_BEAM) > 0, "the beam cools down");
		helper.assertFalse(kit.prismBeam(p), "a second press while cooling down does nothing");
		helper.assertTrue(ChromastoneKit.charge(p) == 20, "and costs nothing");
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
		ck.press(c, AbilitySlot.SLOT_5, false);
		helper.assertTrue(ck.flag(c, ChromastoneKit.F_STANCE), "Chromastone in the Absorb Stance");
		Omnitrix.revert(c, Omnitrix.Reason.SHIFT_V);
		helper.assertTrue(ChromastoneKit.charge(c) == 0 && ck.timer(c, ChromastoneKit.CHARGE) == 0, "the charge is gone");
		helper.assertTrue(Omnitrix.state(c).kitFlags == 0, "no kit flags left");
		helper.assertTrue(ck.timer(c, "stance_end") == 0, "no running timers left");
		near(helper, c.getMaxHealth(), 20f, "a human's 20 health again");
		helper.succeed();
	}
}
