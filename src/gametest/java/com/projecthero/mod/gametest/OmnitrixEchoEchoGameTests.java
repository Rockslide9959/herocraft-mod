package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.echoecho.EchoCloneEntity;
import com.projecthero.mod.omnitrix.alien.echoecho.EchoEchoKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Echo Echo revamp (docs/OMNITRIX_REVAMP_ECHOECHO.md): his fragile body (no armour, blasts +50%, water), the held Wall of
 * Sound (pulses, the cooldown from the release), Surround Sound (clones ring the target, scream inward, stun and Resonating, which
 * makes his sound hit 25% harder), Shatter Note (golems x1.5, glass shatters), the Sound Barrier on held right-click, Which One's
 * Real? (a clone takes the lethal hit), the Ultimate Chorus on Shift+Z, and that turning back leaves no state behind. Mock players
 * are not ticked: tests over time drive {@link Omnitrix#tick} themselves.
 */
public class OmnitrixEchoEchoGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z, float pitch) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, pitch);
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

	/** A recalibrated wearer, transformed into Echo Echo. */
	private static ServerPlayer echo(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 3.5, 2, 1.5, 0f);
		helper.assertTrue(Omnitrix.grant(p, true), "the Recalibrated Omnitrix goes on");
		helper.assertTrue(Omnitrix.select(p, EchoEchoKit.ID), "selected Echo Echo");
		helper.assertTrue(Omnitrix.transform(p), "transformed into Echo Echo");
		p.setOnGround(true);
		return p;
	}

	private static EchoEchoKit kit() {
		return (EchoEchoKit) Aliens.kit(EchoEchoKit.ID);
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 9; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	/** A no-AI creature to hit. */
	private static <T extends Mob> T prey(GameTestHelper helper, EntityType<T> type, double x, double y, double z, float health) {
		T m = type.create(helper.getLevel());
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

	private static Villager prey(GameTestHelper helper, double x, double y, double z, float health) {
		return prey(helper, EntityType.VILLAGER, x, y, z, health);
	}

	private static void near(GameTestHelper helper, float actual, float expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < 0.05f, what + " (expected " + expected + ", got " + actual + ")");
	}

	private static void clearCooldown(ServerPlayer p, String move) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.remove(EchoEchoKit.ID + "." + move);
		Omnitrix.save(p, n);
	}

	private static boolean stunned(LivingEntity e) {
		var s = e.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		return s != null && s.getAmplifier() >= 9 && e.hasEffect(MobEffects.WEAKNESS);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void echoEchoIsFragileAndHisKeysAreRight(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = echo(helper);
		EchoEchoKit k = kit();
		near(helper, p.getMaxHealth(), 16f, "16 health");
		near(helper, (float) p.getAttributeValue(Attributes.ARMOR), 0f, "no armour of his own");
		near(helper, p.getScale(), 0.6f, "small");
		near(helper, k.incomingDamage(p, helper.getLevel().damageSources().explosion(null, null), 4f), 6f, "hollow shell: blasts +50%");
		near(helper, k.incomingDamage(p, helper.getLevel().damageSources().generic(), 4f), 4f, "an ordinary hit is unchanged");
		helper.assertTrue(EchoEchoKit.CHORUS.equals(k.moveOn(AbilitySlot.SLOT_4, true, false).id()), "the ultimate is on Shift+Z");
		helper.assertTrue(EchoEchoKit.LEAP.equals(k.moveOn(AbilitySlot.SLOT_3, false, false).id()), "X is movement");
		helper.assertTrue(EchoEchoKit.SPLIT.equals(k.moveOn(AbilitySlot.SLOT_5, false, false).id()), "V splits off a clone");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void echoEchoWallOfSoundPulsesWhileHeldAndCoolsFromTheRelease(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = echo(helper);
		EchoEchoKit k = kit();
		Villager v = prey(helper, 3.5, 2, 5.5, 100f);
		helper.assertTrue(k.wallStart(p), "the Wall of Sound goes up");
		helper.assertTrue(k.flag(p, EchoEchoKit.F_WALL) && k.keyActive(p, AbilitySlot.SLOT_1), "held: the R box is lit");
		near(helper, v.getHealth(), 100f - EchoEchoKit.WALL_PULSE_DAMAGE, "the first pulse");
		helper.assertFalse(k.wallStart(p), "a second press does not restart it");
		pump(helper, p);
		helper.runAfterDelay(EchoEchoKit.WALL_PULSE + 1, () -> {
			near(helper, v.getHealth(), 100f - 2 * EchoEchoKit.WALL_PULSE_DAMAGE, "a second pulse half a second later");
			k.release(p, AbilitySlot.SLOT_1, true, EchoEchoKit.WALL_PULSE + 1);
			helper.assertFalse(k.flag(p, EchoEchoKit.F_WALL), "let go, it stops");
			helper.assertTrue(k.cooldownLeft(p, EchoEchoKit.WALL) > EchoEchoKit.WALL_COOLDOWN - 3, "the cooldown runs from the release");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void echoEchoSurroundSoundStunsAndLeavesItResonating(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = echo(helper);
		EchoEchoKit k = kit();
		Villager v = prey(helper, 3.5, 2, 6.5, 100f);
		helper.assertFalse(k.surround(p), "no clones: refused");
		helper.assertTrue(k.cooldownLeft(p, EchoEchoKit.SURROUND) == 0, "and no cooldown spent");
		k.split(p);
		clearCooldown(p, EchoEchoKit.SPLIT);
		k.split(p);
		helper.assertTrue(EchoEchoKit.clones(p).size() == 2, "two clones");
		helper.assertTrue(k.surround(p), "the clones run round it");
		helper.assertTrue(k.flag(p, EchoEchoKit.F_SURROUND), "the surround is on its way");
		pump(helper, p);
		helper.runAfterDelay(EchoEchoKit.SURROUND_DELAY + 2, () -> {
			helper.assertFalse(k.flag(p, EchoEchoKit.F_SURROUND), "it went off");
			near(helper, v.getHealth(), 100f - 2 * EchoEchoKit.SURROUND_DAMAGE, "5 a clone");
			helper.assertTrue(stunned(v), "stunned");
			helper.assertTrue(EchoEchoKit.resonating(p, v), "Resonating");
			near(helper, k.dealt(p, v, 10f), 10f * EchoEchoKit.RESONANT_FACTOR, "his sound hits it 25% harder");
			Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void echoEchoShatterNoteCracksGolemsAndBreaksGlass(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = echo(helper);
		EchoEchoKit k = kit();
		BlockPos glass = new BlockPos(5, 2, 6);
		helper.setBlock(glass, Blocks.GLASS);
		var golem = prey(helper, EntityType.IRON_GOLEM, 3.5, 2, 6.5, 120f);
		helper.assertTrue(k.note(p), "the note rings");
		near(helper, golem.getHealth(), 120f - EchoEchoKit.NOTE_DAMAGE * EchoEchoKit.NOTE_SHELL_FACTOR, "a golem takes 1.5x");
		helper.assertTrue(EchoEchoKit.resonating(p, golem), "and resonates");
		helper.assertTrue(helper.getBlockState(glass).isAir(), "the glass beside it shattered");
		helper.assertFalse(k.note(p), "the note cools down");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void echoEchoSoundBarrierHalvesDamageWhileHeld(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = echo(helper);
		EchoEchoKit k = kit();
		k.barrierUse(p);
		helper.assertTrue(k.flag(p, EchoEchoKit.F_BARRIER), "held right-click: the barrier is up");
		near(helper, k.incomingDamage(p, helper.getLevel().damageSources().generic(), 8f), 4f, "half damage");
		pump(helper, p);
		helper.runAfterDelay(EchoEchoKit.BARRIER_GRACE + 3, () -> {
			helper.assertFalse(k.flag(p, EchoEchoKit.F_BARRIER), "let go of right-click: it drops");
			helper.assertTrue(k.cooldownLeft(p, EchoEchoKit.BARRIER) > 0, "and cools down");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void echoEchoWhichOnesRealSavesHimOnceWithAClone(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = echo(helper);
		EchoEchoKit k = kit();
		helper.assertFalse(k.decoy(p, helper.getLevel().damageSources().generic()), "no clone: nothing takes the hit");
		k.split(p);
		EchoCloneEntity c = EchoEchoKit.clones(p).get(0);
		helper.assertFalse(k.decoy(p, helper.getLevel().damageSources().genericKill()), "/kill still kills");
		// a real lethal hit
		p.hurt(helper.getLevel().damageSources().magic(), 500f);
		helper.assertTrue(p.isAlive(), "a clone took the lethal hit");
		near(helper, p.getHealth(), EchoEchoKit.DECOY_HEALTH, "he is left on 6");
		helper.assertTrue(c.isRemoved() && EchoEchoKit.clones(p).isEmpty(), "that clone broke");
		helper.assertTrue(Omnitrix.transformed(p), "still Echo Echo");
		clearCooldown(p, EchoEchoKit.SPLIT);
		k.split(p);
		helper.assertFalse(k.decoy(p, helper.getLevel().damageSources().generic()), "only once per 90 s");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void echoEchoUltimateChorusFillsTheChorusAndStuns(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = echo(helper);
		EchoEchoKit k = kit();
		Villager close = prey(helper, 3.5, 2, 3.5, 200f);
		helper.assertTrue(k.chorusStart(p), "the chorus gathers");
		helper.assertTrue(EchoEchoKit.clones(p).size() == EchoEchoKit.MAX_CLONES, "a full chorus of three");
		helper.assertTrue(k.flag(p, EchoEchoKit.F_CHARGE), "building up");
		pump(helper, p);
		helper.runAfterDelay(EchoEchoKit.CHARGE_TICKS + 2, () -> {
			helper.assertFalse(k.flag(p, EchoEchoKit.F_CHARGE), "it went off");
			helper.assertTrue(close.getHealth() <= 200f - EchoEchoKit.CHORUS_EDGE, "the dome hit hard (" + close.getHealth() + ")");
			helper.assertTrue(stunned(close), "and stunned");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void echoEchoTurningBackLeavesNothingBehind(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = echo(helper);
		EchoEchoKit k = kit();
		Villager v = prey(helper, 3.5, 2, 6.5, 100f);
		k.split(p);
		k.wallStart(p);
		k.barrierUse(p);
		EchoEchoKit.resonate(p, v);
		k.tick(p, Omnitrix.state(p), p.level().getGameTime());
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.assertFalse(EchoEchoKit.holds(p), "no static state left");
		helper.assertTrue(Omnitrix.state(p).kitFlags == 0, "no kit flags left");
		helper.assertTrue(Omnitrix.state(p).readyAt(EchoEchoKit.ID + "." + EchoEchoKit.WALL_START) <= 0, "no move timers left");
		helper.succeed();
	}
}
