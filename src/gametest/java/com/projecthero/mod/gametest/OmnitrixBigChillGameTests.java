package com.projecthero.mod.gametest;

import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.bigchill.BigChillKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

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
 * v0.17.2 Big Chill revamp (docs/OMNITRIX_REVAMP_BIGCHILL.md): his body (no armour, cold immune, fire x1.5), the wings / skin swap
 * flag, the held Freezing Breath (pulses, freezing solid from the third, water iced over, cooldown from the release), Frost Grip +
 * Shatter, Phase's Phantom Chill (once each; burning breaks it), the Cloak's Ambush, Frost Field, Absolute Zero's falloff, and
 * that turning back leaves nothing behind. Mock players are not ticked: tests over time drive {@link Omnitrix#tick} themselves.
 */
public class OmnitrixBigChillGameTests implements FabricGameTest {
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

	/** A recalibrated wearer turned into Big Chill, standing on the floor facing +z. */
	private static ServerPlayer bigChill(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p, true), "the Recalibrated Omnitrix goes on");
		helper.assertTrue(Omnitrix.select(p, BigChillKit.ID), "selected Big Chill");
		p.setOnGround(true);
		helper.assertTrue(Omnitrix.transform(p), "transformed into Big Chill");
		p.setOnGround(true);
		return p;
	}

	private static BigChillKit kit() {
		return (BigChillKit) Aliens.kit(BigChillKit.ID);
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 9; x++) {
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

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void bigChillBodyColdImmuneFireWeakAndTheWingsSwapTheSkin(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bigChill(helper);
		BigChillKit k = kit();
		near(helper, p.getMaxHealth(), 28f, "28 health");
		near(helper, (float) p.getAttributeValue(Attributes.ARMOR), 0f, "no armour of his own");
		float hp = p.getHealth();
		p.hurt(helper.getLevel().damageSources().freeze(), 5f);
		near(helper, p.getHealth(), hp, "the cold never touches him");
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().inFire(), 4f);
		helper.assertTrue(hp - p.getHealth() > 4f * 0.8f + 0.2f, "fire burns him harder (lost " + (hp - p.getHealth()) + " to a 4-damage fire)");
		p.clearFire();
		helper.assertFalse(k.flag(p, BigChillKit.F_OPEN), "standing, the wings are wrapped round him (the cloaked skin)");
		helper.assertTrue(k.iceShard(p), "the Ice Shard flies");
		helper.assertTrue(k.flag(p, BigChillKit.F_OPEN), "attacking opens the wings (the uncloaked skin)");
		pump(helper, p);
		helper.runAfterDelay(BigChillKit.OPEN_TICKS + 4, () -> {
			helper.assertFalse(k.flag(p, BigChillKit.F_OPEN), "and they fold again a moment later");
			helper.assertTrue(k.flight(p), "X: take off");
			helper.assertTrue(k.flag(p, BigChillKit.F_OPEN), "flying, the wings are open");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void bigChillBreathPulsesWhileHeldFreezesAndCoolsFromTheRelease(GameTestHelper helper) {
		floor(helper);
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(3, 1, 3)), Blocks.WATER.defaultBlockState(), 3);
		ServerPlayer p = bigChill(helper);
		BigChillKit k = kit();
		Villager v = prey(helper, 3.5, 2, 4.5, 60f);
		helper.assertTrue(k.breath(p), "the Freezing Breath starts");
		near(helper, v.getHealth(), 60f - (BigChillKit.BREATH_PULSE_DAMAGE * BigChillKit.heat(p)), "the first pulse lands at once (3)");
		helper.assertTrue(v.getTicksFrozen() >= v.getTicksRequiredToFreeze() && !BigChillKit.shelled(v), "chilled, not yet frozen solid");
		helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(3, 1, 3))).is(Blocks.FROSTED_ICE), "the water under it ices over");
		pump(helper, p);
		helper.runAfterDelay(BigChillKit.BREATH_PULSE * 2 + 1, () -> {
			near(helper, v.getHealth(), 60f - (BigChillKit.BREATH_PULSE_DAMAGE * BigChillKit.heat(p)) * 3, "three pulses after 1 s");
			helper.assertTrue(BigChillKit.shelled(v), "the third pulse froze it solid");
			helper.assertTrue(k.flag(p, BigChillKit.F_BREATH), "still breathing while held");
			k.release(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1, true, 21);
			helper.assertFalse(k.flag(p, BigChillKit.F_BREATH), "the release stops it");
			int cd = k.cooldownLeft(p, BigChillKit.BREATH_MOVE);
			helper.assertTrue(cd >= BigChillKit.BREATH_COOLDOWN - 1, "the 6 s cooldown runs from the release (" + cd + ")");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bigChillShatterBreaksFrozenFoesAndRefusesWhenNothingIsFrozen(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bigChill(helper);
		BigChillKit k = kit();
		Zombie z = zombie(helper, 3.5, 2, 3.5, 60f);
		Villager v = prey(helper, 4.7, 2, 3.5, 60f);
		helper.assertFalse(k.shatter(p), "nothing frozen: Shatter is refused");
		helper.assertTrue(k.cooldownLeft(p, BigChillKit.SHATTER) == 0, "and costs no cooldown");
		helper.assertTrue(k.frostGrip(p), "Frost Grip seizes the zombie");
		near(helper, z.getHealth(), 60f - (BigChillKit.GRIP_DAMAGE * BigChillKit.heat(p)), "the grip does 13");
		helper.assertTrue(BigChillKit.shelled(z), "and freezes it solid");
		helper.assertTrue(k.shatter(p), "Shatter");
		near(helper, z.getHealth(), 60f - (BigChillKit.GRIP_DAMAGE * BigChillKit.heat(p)) - (BigChillKit.SHATTER_DAMAGE * BigChillKit.heat(p)), "the ice bursts: 16");
		helper.assertFalse(BigChillKit.shelled(z), "out of its shell");
		near(helper, v.getHealth(), 60f - (BigChillKit.SHATTER_SPLASH * BigChillKit.heat(p)), "the flying ice cuts the villager beside it (5)");
		helper.assertTrue(k.cooldownLeft(p, BigChillKit.SHATTER) > 0, "Shatter cools down");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void bigChillPhantomChillFreezesWhatHePassesThroughOnce(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bigChill(helper);
		BigChillKit k = kit();
		Villager v = prey(helper, 3.5, 2, 1.7, 60f);
		helper.assertTrue(k.phase(p), "Shift+X: intangible");
		helper.assertTrue(k.phasing(p), "the phasing hook is on");
		helper.assertTrue(k.phantomChill(p) == 1, "passing through the villager freezes it from the inside");
		near(helper, v.getHealth(), 60f - (BigChillKit.PHANTOM_DAMAGE * BigChillKit.heat(p)), "8");
		helper.assertTrue(BigChillKit.shelled(v), "frozen solid");
		helper.assertTrue(k.phantomChill(p) == 0, "only once per Phase");
		// heat holds him solid
		p.igniteForSeconds(3);
		Omnitrix.tick(p);
		helper.assertFalse(k.phasing(p), "burning breaks the Phase");
		helper.assertFalse(k.cloak(p), "and he cannot cloak while alight");
		p.clearFire();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void bigChillCloakAmbushAndFrostField(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bigChill(helper);
		BigChillKit k = kit();
		Villager v = prey(helper, 3.5, 2, 3.0, 60f);
		helper.assertTrue(k.cloak(p), "V: the Cloak");
		helper.assertTrue(p.hasEffect(MobEffects.INVISIBILITY) && k.flag(p, BigChillKit.F_CLOAK), "invisible");
		helper.assertFalse(k.flag(p, BigChillKit.F_OPEN), "the wings wrapped round him");
		k.onAttack(p, v);
		near(helper, v.getHealth(), 60f - (BigChillKit.AMBUSH_DAMAGE * BigChillKit.heat(p)), "the Ambush adds 6");
		helper.assertTrue(BigChillKit.shelled(v), "and freezes solid");
		helper.assertFalse(k.flag(p, BigChillKit.F_CLOAK), "striking opens the cloak");
		helper.assertTrue(k.flag(p, BigChillKit.F_OPEN), "the wings flare open");
		// Frost Field: looking down, it forms at his feet
		Villager w = prey(helper, 5.5, 2, 1.5, 60f);
		p.setXRot(85f);
		helper.assertTrue(k.frostField(p), "Shift+C: the Frost Field");
		near(helper, w.getHealth(), 60f - (BigChillKit.FIELD_BURST * BigChillKit.heat(p)), "6 as it forms");
		helper.assertTrue(k.flag(p, BigChillKit.F_FIELD), "and it lies there");
		pump(helper, p);
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(w.getHealth() <= 60f - (BigChillKit.FIELD_BURST * BigChillKit.heat(p)) - (BigChillKit.FIELD_DAMAGE * BigChillKit.heat(p)) + 0.05f, "then 1 every half second (" + w.getHealth() + ")");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bigChillAbsoluteZeroFallsOffAndFreezesSolid(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bigChill(helper);
		BigChillKit k = kit();
		Villager close = prey(helper, 3.5, 2, 2.5, 100f);
		Villager far = prey(helper, 3.5, 2, 8.5, 100f);
		helper.assertTrue(k.absoluteZero(p), "Shift+Z: Absolute Zero winds up");
		helper.assertTrue(k.flag(p, BigChillKit.F_ZERO), "drawing the cold in");
		k.fireAbsoluteZero(p, helper.getLevel());
		float hitClose = 100f - close.getHealth(), hitFar = 100f - far.getHealth();
		helper.assertTrue(hitClose > 52f * BigChillKit.heat(p) && hitClose <= 55.01f, "close: about 55 (" + hitClose + ")");
		helper.assertTrue(hitFar >= 39.9f * BigChillKit.heat(p) && hitFar < hitClose, "at 7 blocks: less, not under 40 (" + hitFar + ")");
		helper.assertTrue(BigChillKit.shelled(close) && BigChillKit.shelled(far), "both frozen solid");
		helper.assertTrue(k.cooldownLeft(p, BigChillKit.ABSOLUTE_ZERO) > 0, "the ultimate cools down");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bigChillTurningBackLeavesNothingBehind(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bigChill(helper);
		BigChillKit k = kit();
		k.breath(p);
		k.frostField(p);
		k.phase(p);
		k.frostArmour(p);
		helper.assertTrue(k.flag(p, BigChillKit.F_BREATH) && k.flag(p, BigChillKit.F_FIELD) && k.phasing(p), "breathing, a field down, phased");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		OmnitrixState s = Omnitrix.state(p);
		helper.assertTrue(s.kitFlags == 0, "no kit flags left");
		helper.assertTrue(k.timer(p, BigChillKit.BREATH_START) == 0 && k.timer(p, BigChillKit.FIELD_END) == 0 && BigChillKit.pos(p, "field") == null,
				"no running timers or positions left");
		helper.assertFalse(p.getAbilities().mayfly, "no wings left");
		helper.succeed();
	}
}
