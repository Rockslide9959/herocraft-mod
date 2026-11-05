package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixInfo;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.feedback.FeedbackKit;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Feedback (user: "build a feedback alien please, do research on his powers and abilites in the show and base his moves on that
 * please, add him to the mistransformation list"): a hidden prototype alien found by a mistransform (the even draw itself is
 * {@code OmnitrixCannonboltGameTests}, which takes every hidden alien); his dial glyph; every key has a move; the Charge meter -- filled
 * by absorbing (braced: shots become Charge, not damage; unbraced: half of an energy hit), lightning, plugging into power and draining
 * creatures, and spent by his blasts; Overcharge past full; Redirect sends back what he absorbed; the whip, leap, swing, feed,
 * Overclock, Discharge and Cannon.
 */
public class OmnitrixFeedbackGameTests implements FabricGameTest {
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

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static ServerPlayer feedback(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		Aliens.discover(p, FeedbackKit.ID, true); // found by a mistransform (the draw's own tests)
		helper.assertTrue(Omnitrix.select(p, FeedbackKit.ID), "Feedback is on the prototype's dial once found");
		helper.assertTrue(Omnitrix.transform(p), "transformed into Feedback");
		helper.onEachTick(() -> Omnitrix.tick(p));
		return p;
	}

	private static FeedbackKit kit() {
		return (FeedbackKit) Aliens.kit(FeedbackKit.ID);
	}

	private static void tap(ServerPlayer p, AbilitySlot slot, boolean shift) {
		p.setShiftKeyDown(shift);
		AbilityRouter.handleInput(p, slot.number(), true);
		AbilityRouter.handleInput(p, slot.number(), false);
		p.setShiftKeyDown(false);
	}

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double y, double z) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		T m = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		if (m instanceof Zombie) {
			m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		}
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static void lookAt(ServerPlayer p, LivingEntity z) {
		lookAt(p, z.position().add(0, z.getBbHeight() * 0.5, 0));
	}

	private static void lookAt(ServerPlayer p, Vec3 at) {
		Vec3 d = at.subtract(p.getEyePosition());
		float yaw = (float) (Math.atan2(-d.x, d.z) * 180 / Math.PI);
		float pitch = (float) (-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180 / Math.PI);
		p.setYRot(yaw);
		p.setXRot(pitch);
		p.setYHeadRot(yaw);
	}

	private static Arrow arrow(GameTestHelper helper) {
		Arrow a = EntityType.ARROW.create(helper.getLevel());
		a.moveTo(helper.absoluteVec(new Vec3(3.5, 3, 5.5)));
		return a;
	}

	// ---------------------------------------------------------------- the dial

	@GameTest(template = EMPTY_STRUCTURE)
	public void feedbackIsAHiddenPrototypeAlien(GameTestHelper helper) {
		helper.assertTrue(Aliens.byId(FeedbackKit.ID) == Aliens.FEEDBACK && !Aliens.FEEDBACK.recalibrated(), "declared on the prototype");
		helper.assertTrue(Aliens.hidden(FeedbackKit.ID) && !Aliens.itemOnly(FeedbackKit.ID) && Aliens.mistransformFinds().contains(Aliens.FEEDBACK),
				"hidden, found by a mistransform (no item)");
		helper.assertTrue(Aliens.dial(false).contains(Aliens.FEEDBACK) && !Aliens.dial(true).contains(Aliens.FEEDBACK), "on the prototype's dial only");
		ServerPlayer p = mock(helper, 1.5, 2, 1.5);
		Omnitrix.grant(p);
		helper.assertFalse(Aliens.playable(p).contains(Aliens.FEEDBACK), "not on the dial before he is found");
		helper.assertFalse(Omnitrix.select(p, FeedbackKit.ID), "and cannot be dialled");
		helper.assertTrue(Aliens.unfound(p).contains(Aliens.FEEDBACK), "a mistransform can find him");
		Aliens.discover(p, FeedbackKit.ID, true);
		helper.assertTrue(Aliens.playable(p).contains(Aliens.FEEDBACK) && !Aliens.unfound(p).contains(Aliens.FEEDBACK), "found: on the dial, out of the draw");
		String glyph = OmnitrixInfo.icon(Aliens.FEEDBACK).getString();
		helper.assertTrue(glyph.length() == 1 && glyph.charAt(0) == '', "his dial glyph is U+E01A, got " + (glyph.isEmpty() ? "none" : Integer.toHexString(glyph.charAt(0))));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyKeyHasAMoveAndNoneIsOnHOrN(GameTestHelper helper) {
		AlienKit k = kit();
		for (AbilitySlot s : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5,
				AbilitySlot.SLOT_6 }) {
			helper.assertTrue(k.moveOn(s, false, false) != null, "a move on " + s);
			helper.assertTrue(k.moveOn(s, true, false) != null, "a Shift move on " + s);
		}
		for (var m : k.moves()) {
			helper.assertTrue(!m.onSlot() || m.slot().ordinal() <= AbilitySlot.SLOT_6.ordinal(), m.id() + " on R/G/X/Z/V/C only");
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- Charge: blasts spend it

	/** R with no Charge is a weak spark and spends nothing; with Charge it is the full blast (10 Charge), half again on a machine. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void blastSpendsChargeAndHitsMachinesHarder(GameTestHelper helper) {
		ServerPlayer p = feedback(helper);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2, 6.5);
		IronGolem g = mob(helper, EntityType.IRON_GOLEM, 5.5, 2, 6.5);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(FeedbackKit.charge(p) == 0, "he starts empty");
			lookAt(p, z);
			float zh = z.getHealth();
			tap(p, AbilitySlot.SLOT_1, false);
			float spark = zh - z.getHealth();
			helper.assertTrue(spark > 4f && spark <= 6.01f, "empty: a Static Spark, about 6, got " + spark);
			helper.assertTrue(FeedbackKit.charge(p) == 0, "and nothing spent");
			kit().fill(p, 50);
			kit().cooldown(p, FeedbackKit.BLAST, 0);
			z.invulnerableTime = 0;
			zh = z.getHealth();
			tap(p, AbilitySlot.SLOT_1, false);
			float full = zh - z.getHealth();
			helper.assertTrue(full > 11f && full <= 14.01f, "charged: about 14, got " + full);
			helper.assertTrue(FeedbackKit.charge(p) == 40, "10 Charge spent: " + FeedbackKit.charge(p));
			kit().cooldown(p, FeedbackKit.BLAST, 0);
			lookAt(p, g);
			float gh = g.getHealth();
			tap(p, AbilitySlot.SLOT_1, false);
			float gd = gh - g.getHealth();
			helper.assertTrue(gd > 20f && gd <= 21.01f, "on a machine: 21 (x1.5), got " + gd);
			helper.succeed();
		});
	}

	/** Moves that need Charge refuse without it, and spend no cooldown. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void chargedMovesRefuseWhenEmpty(GameTestHelper helper) {
		ServerPlayer p = feedback(helper);
		mob(helper, EntityType.ZOMBIE, 3.5, 2, 4.5);
		helper.runAfterDelay(2, () -> {
			kit().fill(p, 20);
			tap(p, AbilitySlot.SLOT_1, true);
			tap(p, AbilitySlot.SLOT_4, false);
			tap(p, AbilitySlot.SLOT_4, true);
			tap(p, AbilitySlot.SLOT_2, true);
			helper.assertTrue(kit().cooldownLeft(p, FeedbackKit.RAPID) == 0 && kit().cooldownLeft(p, FeedbackKit.DISCHARGE) == 0
					&& kit().cooldownLeft(p, FeedbackKit.CANNON) == 0 && kit().cooldownLeft(p, FeedbackKit.OVERCLOCK) == 0, "no cooldown spent");
			helper.assertTrue(FeedbackKit.charge(p) == 20, "and no Charge");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Charge: absorbing fills it

	/** Hold V: braced, an arrow does no damage and becomes Charge (x4); Shift+V sends it back at the crosshair, harder. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void absorbTurnsShotsIntoChargeAndRedirectSendsThemBack(GameTestHelper helper) {
		ServerPlayer p = feedback(helper);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2, 5.5);
		helper.runAfterDelay(2, () -> {
			kit().holdStart(p, AbilitySlot.SLOT_5);
			helper.assertTrue(kit().flag(p, FeedbackKit.F_ABSORB), "braced");
			float h = p.getHealth();
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().arrow(arrow(helper), z), 5f);
			helper.assertTrue(p.getHealth() >= h, "the arrow does no damage: " + h + " -> " + p.getHealth());
			// Hard difficulty scales a mob's shot at a player by 1.5: 5 -> 7.5
			helper.assertTrue(Math.abs(FeedbackKit.banked(p) - 7.5f) < 0.01f, "banked for Redirect (7.5 on Hard): " + FeedbackKit.banked(p));
			helper.assertTrue(FeedbackKit.charge(p) == 30, "it is 30 Charge (7.5 x4): " + FeedbackKit.charge(p));
			lookAt(p, z);
			float zh = z.getHealth();
			tap(p, AbilitySlot.SLOT_5, true);
			float d = zh - z.getHealth();
			helper.assertTrue(d > 16f, "Redirect: 12 + 1.5 x 7.5 = 23.25 (the zombie has 20), got " + d);
			helper.assertTrue(FeedbackKit.charge(p) == 15 && FeedbackKit.banked(p) == 0f, "15 Charge spent and the bank emptied: " + FeedbackKit.charge(p));
			helper.succeed();
		});
	}

	/** Unbraced, an energy hit (a fireball) still does only half; the other half is Charge. Redirect with nothing absorbed refuses. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void energySpongeHalvesEnergyHits(GameTestHelper helper) {
		ServerPlayer p = feedback(helper);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2, 5.5);
		helper.runAfterDelay(2, () -> {
			lookAt(p, z);
			kit().fill(p, 30);
			tap(p, AbilitySlot.SLOT_5, true);
			helper.assertTrue(kit().cooldownLeft(p, FeedbackKit.REDIRECT) == 0, "nothing absorbed yet: Redirect refuses, no cooldown");
			kit().fill(p, 0);
			SmallFireball fb = EntityType.SMALL_FIREBALL.create(helper.getLevel());
			p.invulnerableTime = 0;
			float h = p.getHealth();
			p.hurt(p.damageSources().fireball(fb, z), 8f);
			float lost = h - p.getHealth();
			// Hard difficulty: 8 -> 12, half of it drunk
			helper.assertTrue(lost <= 6.01f, "half of 12 at most: " + lost);
			helper.assertTrue(FeedbackKit.charge(p) == 24, "the other half is Charge (6 x4): " + FeedbackKit.charge(p));
			p.invulnerableTime = 0;
			h = p.getHealth();
			p.hurt(p.damageSources().arrow(arrow(helper), z), 4f);
			helper.assertTrue(p.getHealth() < h && FeedbackKit.charge(p) == 24, "a plain arrow unbraced just hurts");
			helper.succeed();
		});
	}

	/** Lightning never hurts him: it is 40 Charge. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void lightningIsAFeast(GameTestHelper helper) {
		ServerPlayer p = feedback(helper);
		helper.runAfterDelay(2, () -> {
			float h = p.getHealth();
			p.invulnerableTime = 0;
			helper.assertFalse(p.hurt(p.damageSources().lightningBolt(), 5f), "the strike is refused");
			helper.assertTrue(p.getHealth() >= h, "no damage");
			helper.assertTrue(FeedbackKit.charge(p) == FeedbackKit.LIGHTNING_GAIN, "40 Charge: " + FeedbackKit.charge(p));
			helper.succeed();
		});
	}

	/** Absorbing when full overcharges him: hurt (2) and slowed. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void absorbingWhenFullOvercharges(GameTestHelper helper) {
		ServerPlayer p = feedback(helper);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2, 5.5);
		helper.runAfterDelay(2, () -> {
			kit().fill(p, FeedbackKit.MAX_CHARGE);
			kit().holdStart(p, AbilitySlot.SLOT_5);
			float h = p.getHealth();
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().arrow(arrow(helper), z), 5f);
			helper.assertTrue(h - p.getHealth() > 1.5f, "overcharged: about 2, lost " + (h - p.getHealth()));
			helper.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and slowed");
			helper.assertTrue(FeedbackKit.charge(p) == FeedbackKit.MAX_CHARGE, "still full");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Charge: plugging in fills it

	/** Shift+C on a redstone block: the cables plug in and drink 2 a tick; with no power about, nothing (no cooldown). */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void plugInDrinksFromPower(GameTestHelper helper) {
		ServerPlayer p = feedback(helper);
		helper.runAfterDelay(2, () -> {
			tap(p, AbilitySlot.SLOT_6, true);
			helper.assertTrue(kit().cooldownLeft(p, FeedbackKit.PLUG) == 0 && !kit().flag(p, FeedbackKit.F_PLUG), "nothing to plug into: refused, no cooldown");
			BlockPos b = helper.absolutePos(new BlockPos(3, 3, 4));
			helper.getLevel().setBlock(b, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
			lookAt(p, Vec3.atCenterOf(b));
			tap(p, AbilitySlot.SLOT_6, true);
			helper.assertTrue(kit().flag(p, FeedbackKit.F_PLUG) && b.equals(FeedbackKit.plugBlock(p)), "plugged into the redstone block");
		});
		helper.runAfterDelay(14, () -> {
			int c = FeedbackKit.charge(p);
			helper.assertTrue(c >= 16 && c <= 28, "about 2 a tick for ~11 ticks: " + c);
		});
		helper.runAfterDelay(60, () -> {
			helper.assertFalse(kit().flag(p, FeedbackKit.F_PLUG), "unplugged after 2 s");
			helper.assertTrue(FeedbackKit.charge(p) >= 70, "about 80 from 2 s on a redstone block: " + FeedbackKit.charge(p));
			helper.succeed();
		});
	}

	/** Shift+C at an iron golem drains it: 4.5 every half second (a machine, x1.5) for +9 Charge, stunned. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void plugInDrainsAMachine(GameTestHelper helper) {
		ServerPlayer p = feedback(helper);
		IronGolem g = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 5.5);
		float[] start = new float[1];
		helper.runAfterDelay(2, () -> {
			lookAt(p, g);
			start[0] = g.getHealth();
			tap(p, AbilitySlot.SLOT_6, true);
			helper.assertTrue(FeedbackKit.plugEntity(p) == g.getId() + 1, "plugged into the golem");
		});
		helper.runAfterDelay(48, () -> {
			float d = start[0] - g.getHealth();
			helper.assertTrue(d >= 13f && d <= 18.1f, "four drains of 4.5: " + d);
			helper.assertTrue(FeedbackKit.charge(p) >= 27 && FeedbackKit.charge(p) <= 36, "+9 Charge each: " + FeedbackKit.charge(p));
			helper.assertTrue(FeedbackKit.plugEntity(p) == 0 && !kit().flag(p, FeedbackKit.F_PLUG), "and unplugged");
			helper.succeed();
		});
	}

	/** A punch shocks (+3) and sips 3 Charge. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void plugFingersSipCharge(GameTestHelper helper) {
		ServerPlayer p = feedback(helper);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2, 2.8);
		helper.runAfterDelay(25, () -> {
			lookAt(p, z);
			try { // a fully charged swing (a mock player never ticks its attack timer)
				java.lang.reflect.Field f = LivingEntity.class.getDeclaredField("attackStrengthTicker");
				f.setAccessible(true);
				f.setInt(p, 100);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
			float h = z.getHealth();
			kit().onAttack(p, z); // what the attack callback calls
			helper.assertTrue(h - z.getHealth() > 1.5f, "the shock lands: " + (h - z.getHealth()));
			helper.assertTrue(FeedbackKit.charge(p) == FeedbackKit.FINGER_GAIN, "+3 Charge a punch: " + FeedbackKit.charge(p));
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the other moves

	/** C: the tail lashes what is ahead (12); with 5 Charge the plug discharges for 4 more. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void plugWhipLashesAhead(GameTestHelper helper) {
		ServerPlayer p = feedback(helper);
		IronGolem g = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 3.8);
		helper.runAfterDelay(2, () -> {
			lookAt(p, g);
			float h = g.getHealth();
			tap(p, AbilitySlot.SLOT_6, false);
			float d = h - g.getHealth();
			helper.assertTrue(d > 11f && d <= 12.01f, "the lash: 12, got " + d);
			kit().fill(p, 10);
			kit().cooldown(p, FeedbackKit.WHIP, 0);
			g.invulnerableTime = 0;
			h = g.getHealth();
			tap(p, AbilitySlot.SLOT_6, false);
			d = h - g.getHealth();
			helper.assertTrue(d > 17f && d <= 18.01f, "charged: 12 + 4 x1.5 (machine) = 18, got " + d);
			helper.assertTrue(FeedbackKit.charge(p) == 5, "5 Charge spent: " + FeedbackKit.charge(p));
			helper.succeed();
		});
	}

	/** Shift+R: five bolts over a second, 6 each. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void rapidFireShootsFive(GameTestHelper helper) {
		ServerPlayer p = feedback(helper);
		IronGolem g = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 6.5);
		float[] start = new float[1];
		helper.runAfterDelay(2, () -> {
			lookAt(p, g);
			kit().fill(p, 30);
			start[0] = g.getHealth();
			tap(p, AbilitySlot.SLOT_1, true);
			helper.assertTrue(FeedbackKit.charge(p) == 5 && FeedbackKit.rapidLeft(p) == 4, "25 Charge, four shots to come");
		});
		helper.runAfterDelay(25, () -> {
			float d = start[0] - g.getHealth();
			helper.assertTrue(d > 44f && d <= 45.01f, "five bolts of 9 (6 x1.5 on a machine): " + d);
			helper.assertTrue(FeedbackKit.rapidLeft(p) == 0, "all fired");
			helper.succeed();
		});
	}

	/** Z spends all his Charge on a burst round him (30 at full); Shift+Z a beam through everything in line (24). */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void dischargeAndCannon(GameTestHelper helper) {
		ServerPlayer p = feedback(helper);
		IronGolem a = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 4.0);
		IronGolem b = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 7.0);
		helper.runAfterDelay(2, () -> {
			kit().fill(p, FeedbackKit.MAX_CHARGE);
			float h = a.getHealth();
			tap(p, AbilitySlot.SLOT_4, false);
			float d = h - a.getHealth();
			helper.assertTrue(d > 44f && d <= 45.01f, "Full Discharge at 100: 30 (x1.5 a machine), got " + d);
			helper.assertTrue(FeedbackKit.charge(p) == 0, "all of it spent");
			kit().fill(p, 60);
			a.invulnerableTime = 0;
			b.invulnerableTime = 0;
			a.removeAllEffects();
			b.removeAllEffects();
			lookAt(p, a);
			float ha = a.getHealth(), hb = b.getHealth();
			tap(p, AbilitySlot.SLOT_4, true);
			float da = ha - a.getHealth(), db = hb - b.getHealth();
			helper.assertTrue(da > 35f && db > 35f, "the beam pierces both: 36 each, got " + da + " / " + db);
			helper.assertTrue(FeedbackKit.charge(p) == 10, "50 Charge spent: " + FeedbackKit.charge(p));
			helper.succeed();
		});
	}

	/** G turns 25 Charge into 6 health; Shift+G Overclocks (30 Charge): faster, and blasts a quarter harder. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void powerFeedAndOverclock(GameTestHelper helper) {
		ServerPlayer p = feedback(helper);
		IronGolem g = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 6.5);
		helper.runAfterDelay(2, () -> {
			p.setHealth(10f);
			kit().fill(p, 80);
			tap(p, AbilitySlot.SLOT_2, false);
			helper.assertTrue(Math.abs(p.getHealth() - 16f) < 0.01f, "+6 health: " + p.getHealth());
			helper.assertTrue(FeedbackKit.charge(p) == 55, "25 Charge spent");
			tap(p, AbilitySlot.SLOT_2, true);
			helper.assertTrue(kit().flag(p, FeedbackKit.F_OVERCLOCK) && FeedbackKit.charge(p) == 25, "Overclocked for 30 Charge");
			lookAt(p, g);
			float h = g.getHealth();
			tap(p, AbilitySlot.SLOT_1, false);
			float d = h - g.getHealth();
			helper.assertTrue(d > 26f && d <= 26.26f, "Overclocked blast on a machine: 14 x1.5 x1.25 = 26.25, got " + d);
		});
		helper.runAfterDelay(14, () -> {
			helper.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SPEED), "faster while Overclocked");
			helper.succeed();
		});
	}

	/** X leaps him forward and up with no fall damage. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void leapsWithNoFallDamage(GameTestHelper helper) {
		ServerPlayer p = feedback(helper);
		helper.runAfterDelay(2, () -> {
			tap(p, AbilitySlot.SLOT_3, false);
			Vec3 v = p.getDeltaMovement();
			helper.assertTrue(v.z > 1.0 && v.y > 0.4 && kit().flag(p, FeedbackKit.F_LEAP), "the leap: forward and up, " + v);
			p.fallDistance = 10f;
			p.invulnerableTime = 0;
			helper.assertFalse(p.hurt(p.damageSources().fall(), 6f), "no fall damage just after");
			helper.succeed();
		});
	}

	/** Round 3 (user: "swap c and shift C abilities with z and shift z", "swap G and shift G abilities with v and shift V"). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void round3KeySwaps(GameTestHelper helper) {
		AlienKit k = kit();
		helper.assertTrue(FeedbackKit.FEED.equals(k.moveOn(AbilitySlot.SLOT_2, false, false).id()), "G is Power Feed");
		helper.assertTrue(FeedbackKit.OVERCLOCK.equals(k.moveOn(AbilitySlot.SLOT_2, true, false).id()), "Shift+G is Overclock");
		helper.assertTrue(FeedbackKit.ABSORB.equals(k.moveOn(AbilitySlot.SLOT_5, false, true).id()), "hold V is Absorb");
		helper.assertTrue(FeedbackKit.QUICK_ABSORB.equals(k.moveOn(AbilitySlot.SLOT_5, false, false).id()), "a tap of V is Quick Absorb");
		helper.assertTrue(FeedbackKit.REDIRECT.equals(k.moveOn(AbilitySlot.SLOT_5, true, false).id()), "Shift+V is Redirect");
		helper.assertTrue(FeedbackKit.DISCHARGE.equals(k.moveOn(AbilitySlot.SLOT_4, false, false).id()), "Z is Full Discharge");
		helper.assertTrue(FeedbackKit.CANNON.equals(k.moveOn(AbilitySlot.SLOT_4, true, false).id()), "Shift+Z is the Cannon");
		helper.assertTrue(FeedbackKit.WHIP.equals(k.moveOn(AbilitySlot.SLOT_6, false, false).id()), "C is Plug Whip");
		helper.assertTrue(FeedbackKit.PLUG.equals(k.moveOn(AbilitySlot.SLOT_6, true, false).id()), "Shift+C is Plug In");
		helper.assertTrue(FeedbackKit.FLIGHT.equals(k.moveOn(AbilitySlot.SLOT_3, true, false).id()), "Shift+X is Surge Flight");
		int c = FeedbackKit.COLOUR;
		helper.assertTrue((c & 0xFF) > ((c >> 8) & 0xFF) && ((c >> 8) & 0xFF) > ((c >> 16) & 0xFF), "his energy is blue");
		helper.succeed();
	}

	/** Round 3 (user: "remove time limit of absorb, player can hold v as long as they want to absorb energy"): through the real key. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void absorbLastsAsLongAsVIsHeld(GameTestHelper helper) {
		ServerPlayer p = feedback(helper);
		helper.runAfterDelay(2, () -> AbilityRouter.handleInput(p, AbilitySlot.SLOT_5.number(), true));
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(kit().flag(p, FeedbackKit.F_ABSORB), "still braced 5 s in (it used to end at 3 s)");
			helper.assertTrue(kit().keyActive(p, AbilitySlot.SLOT_5), "the V box lights");
			helper.assertTrue(kit().cooldownLeft(p, FeedbackKit.ABSORB) == 0, "no cooldown until he lets go");
			AbilityRouter.handleInput(p, AbilitySlot.SLOT_5.number(), false);
			helper.assertFalse(kit().flag(p, FeedbackKit.F_ABSORB), "let go: the brace ends");
			helper.assertTrue(kit().cooldownLeft(p, FeedbackKit.ABSORB) == FeedbackKit.ABSORB_COOLDOWN, "the cooldown starts now");
			helper.assertTrue(kit().cooldownLeft(p, FeedbackKit.QUICK_ABSORB) > 0, "a tap shares it");
			helper.succeed();
		});
	}

	/** A tap of V is a one-second brace. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void aTapOfVBracesForASecond(GameTestHelper helper) {
		ServerPlayer p = feedback(helper);
		helper.runAfterDelay(2, () -> {
			tap(p, AbilitySlot.SLOT_5, false);
			helper.assertTrue(kit().flag(p, FeedbackKit.F_ABSORB), "braced");
		});
		helper.runAfterDelay(2 + FeedbackKit.QUICK_ABSORB_TICKS + 3, () -> {
			helper.assertFalse(kit().flag(p, FeedbackKit.F_ABSORB), "over after 1 s");
			helper.assertTrue(kit().cooldownLeft(p, FeedbackKit.ABSORB) > 0, "and cooling down");
			helper.succeed();
		});
	}

	/** Round 3: Surge Flight needs Charge, spends 5 a second while Shift+X is down, and ends when you let go. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void surgeFlightSpendsChargeWhileHeld(GameTestHelper helper) {
		ServerPlayer p = feedback(helper);
		helper.runAfterDelay(2, () -> {
			p.setShiftKeyDown(true);
			AbilityRouter.handleInput(p, AbilitySlot.SLOT_3.number(), true);
			helper.assertFalse(FeedbackKit.holdFlying(p) || p.getAbilities().flying, "no Charge: no lift-off");
			helper.assertTrue(kit().cooldownLeft(p, FeedbackKit.FLIGHT) == 0, "and no cooldown");
			AbilityRouter.handleInput(p, AbilitySlot.SLOT_3.number(), false);
			kit().fill(p, 50);
			AbilityRouter.handleInput(p, AbilitySlot.SLOT_3.number(), true);
			helper.assertTrue(FeedbackKit.holdFlying(p) && p.getAbilities().flying && kit().keyActive(p, AbilitySlot.SLOT_3), "flying");
		});
		helper.runAfterDelay(42, () -> {
			int c = FeedbackKit.charge(p);
			helper.assertTrue(c >= 37 && c <= 41, "two seconds: about 10 Charge: " + c);
			AbilityRouter.handleInput(p, AbilitySlot.SLOT_3.number(), false);
			p.setShiftKeyDown(false);
			helper.assertFalse(FeedbackKit.holdFlying(p) || p.getAbilities().flying || p.getAbilities().mayfly, "let go: flight gone");
			helper.assertTrue(kit().cooldownLeft(p, FeedbackKit.FLIGHT) > 0, "the cooldown starts now");
			helper.succeed();
		});
	}

	/** Round 3: "stops when Charge runs out", with the keys still down. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void surgeFlightStopsWhenTheChargeRunsOut(GameTestHelper helper) {
		ServerPlayer p = feedback(helper);
		helper.runAfterDelay(2, () -> {
			kit().fill(p, FeedbackKit.FLIGHT_MIN_CHARGE);
			p.setShiftKeyDown(true);
			AbilityRouter.handleInput(p, AbilitySlot.SLOT_3.number(), true);
			helper.assertTrue(FeedbackKit.holdFlying(p), "5 Charge lifts him");
		});
		helper.runAfterDelay(2 + FeedbackKit.FLIGHT_MIN_CHARGE * FeedbackKit.FLIGHT_EVERY + 6, () -> {
			helper.assertTrue(FeedbackKit.charge(p) == 0, "all spent");
			helper.assertFalse(FeedbackKit.holdFlying(p) || p.getAbilities().flying, "he drops, keys still down");
			helper.assertTrue(kit().flag(p, FeedbackKit.F_LANDING) || p.onGround(), "with no fall damage to come");
			AbilityRouter.handleInput(p, AbilitySlot.SLOT_3.number(), false);
			p.setShiftKeyDown(false);
			helper.succeed();
		});
	}

	/** Water bleeds his Charge away. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void waterLeaksCharge(GameTestHelper helper) {
		ServerPlayer p = feedback(helper);
		helper.runAfterDelay(2, () -> {
			helper.getLevel().setBlock(p.blockPosition(), Blocks.WATER.defaultBlockState(), 3);
			helper.getLevel().setBlock(p.blockPosition().above(), Blocks.WATER.defaultBlockState(), 3);
			kit().fill(p, 50);
		});
		helper.onEachTick(p::baseTick); // isInWater() needs the base tick on a mock player
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(FeedbackKit.charge(p) <= 47 && FeedbackKit.charge(p) >= 40, "about 2 a second leaked: " + FeedbackKit.charge(p));
			helper.succeed();
		});
	}
}
