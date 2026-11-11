package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.saiyan.Saiyan;
import com.projecthero.mod.saiyan.SaiyanAbilityManager;
import com.projecthero.mod.saiyan.SaiyanConfig;
import com.projecthero.mod.saiyan.SaiyanForm;
import com.projecthero.mod.saiyan.data.SaiyanState;
import com.projecthero.mod.saiyan.item.SaiyanItems;
import com.projecthero.mod.saiyan.kit.SaiyanKit;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Saiyan revamp, the core: the Saiyan body (stats), Ki (regen, the 3-hit bonus, V tap / hold charge), Zenkai, the
 * stronger-enemy bonus, Rage and the 3-minute Super Saiyan, Kaioken (wind-up, drain, the 2 HP floor), the saturation mixin and
 * the key routing to the kit's ids. Mock players are not ticked by the server, so tests over time drive {@link Saiyan#tick}
 * themselves (one call per game tick); timed checks assert on per-tick steps.
 */
public class SaiyanGameTests implements FabricGameTest {
	private static final float EPS = 0.01f;

	private static ServerPlayer mock(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		try { // a fresh mock player is spawn-protected
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	private static ServerPlayer saiyan(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		helper.assertTrue(Saiyan.grant(p), "the grant works");
		return p;
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<SaiyanState> change) {
		SaiyanState n = Saiyan.state(p).copy();
		change.accept(n);
		p.setAttached(ModAttachments.SAIYAN_STATE, n);
		Saiyan.reconcile(p);
	}

	private static Zombie zombie(GameTestHelper helper, double maxHealth) {
		Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5, 2.0, 4.5));
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
		z.setHealth((float) maxHealth);
		return z;
	}

	/** One melee hit from {@code p} on {@code z} (fresh invulnerability each time). */
	private static void punch(ServerPlayer p, Zombie z, float amount) {
		z.invulnerableTime = 0;
		z.hurt(p.damageSources().playerAttack(p), amount);
	}

	private static void hurt(ServerPlayer p, float amount) {
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().generic(), amount);
	}

	/** Runs {@code ticks} Saiyan ticks, one per game tick ({@code each} before every one), then {@code then}. */
	// One onEachTick per test, registered by the first ticks() call. Later calls (including ones made from inside a
	// callback) only join the queue: onEachTick fills the test's tick map, and adding to it while the ticker walks it
	// crashes the gametest server.
	private static final java.util.Map<GameTestHelper, java.util.List<Runnable>> TICKERS = new java.util.WeakHashMap<>();

	private static void ticks(GameTestHelper helper, ServerPlayer p, int ticks, Runnable each, Runnable then) {
		int[] left = { ticks };
		Runnable[] self = new Runnable[1];
		self[0] = () -> {
			if (left[0] > 0) {
				if (each != null) {
					each.run();
				}
				Saiyan.tick(p);
				left[0]--;
				if (left[0] == 0) {
					then.run();
				}
			}
		};
		java.util.List<Runnable> list = TICKERS.get(helper);
		if (list == null) {
			java.util.List<Runnable> fresh = new java.util.ArrayList<>();
			TICKERS.put(helper, fresh);
			helper.onEachTick(() -> {
				for (Runnable r : new java.util.ArrayList<>(fresh)) {
					r.run();
				}
			});
			list = fresh;
		}
		list.add(self[0]);
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < EPS;
	}

	// ---------------------------------------------------------------- the power and the body

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void bloodGrantsThePowerAsTheOnePrimary(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		com.projecthero.mod.kryptonian.Kryptonian.grant(p);
		ItemStack blood = new ItemStack(SaiyanItems.SAIYAN_BLOOD);
		p.setItemInHand(InteractionHand.MAIN_HAND, blood);
		ItemStack left = blood.finishUsingItem(p.level(), p);
		helper.assertTrue(Saiyan.hasPower(p), "drinking Saiyan Blood makes a Saiyan");
		helper.assertTrue(left.is(Items.GLASS_BOTTLE), "the bottle comes back");
		helper.assertTrue(!com.projecthero.mod.kryptonian.Kryptonian.hasPower(p), "it replaced the Kryptonian (one Primary)");
		helper.assertTrue(HeroTiers.holdsHero(p, Saiyan.KEY), "HeroTiers knows the key");
		helper.assertTrue(Saiyan.ki(p) == SaiyanConfig.KI_MAX, "a full Ki bar to start");
		helper.assertTrue(Saiyan.rage(p) == 0f && !Saiyan.ssj(p), "no Rage, in Base");
		Saiyan.revoke(p);
		helper.assertTrue(!Saiyan.hasPower(p), "revoked");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theSaiyanBody(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		double speed0 = p.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
		double swing0 = p.getAttribute(Attributes.ATTACK_SPEED).getBaseValue();
		double jump0 = p.getAttribute(Attributes.JUMP_STRENGTH).getBaseValue();
		Saiyan.grant(p);
		helper.assertTrue(near(p.getMaxHealth(), 40.0), "40 max health: " + p.getMaxHealth());
		helper.assertTrue(near(p.getHealth(), 40.0), "and healed to it");
		helper.assertTrue(near(p.getAttributeValue(Attributes.MOVEMENT_SPEED), speed0 * 1.3), "30% faster");
		helper.assertTrue(near(p.getAttributeValue(Attributes.ATTACK_DAMAGE), 1.0 + 7.0), "+7 melee");
		helper.assertTrue(near(p.getAttributeValue(Attributes.ATTACK_SPEED), swing0 * 1.3), "30% faster attacks");
		helper.assertTrue(near(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.3), "30% knockback resistance");
		helper.assertTrue(p.getAttributeValue(Attributes.JUMP_STRENGTH) >= 0.55 && near(p.getAttributeValue(Attributes.JUMP_STRENGTH), jump0 + 0.14),
				"a 2-block jump (0.56 strength)");
		helper.assertTrue(Saiyan.damageMult(p, true) == 1f, "no damage bonus in a plain Base");
		Saiyan.revoke(p);
		helper.assertTrue(near(p.getMaxHealth(), 20.0) && near(p.getAttributeValue(Attributes.ATTACK_DAMAGE), 1.0), "all gone when revoked");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void regenerationOneHealsEveryTwoAndAHalfSeconds(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		p.setHealth(20f);
		float[] last = { 20f };
		int[] heals = { 0 };
		ticks(helper, p, 100, () -> {
			float h = p.getHealth();
			if (h > last[0] + EPS) {
				helper.assertTrue(near(h - last[0], SaiyanConfig.REGEN_AMOUNT), "1 HP a step: " + (h - last[0]));
				heals[0]++;
			}
			last[0] = h;
		}, () -> {
			helper.assertTrue(heals[0] >= 1 && heals[0] <= 2, "about one heal per 50 ticks: " + heals[0]);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void saturationLastsThirtyPercentLonger(GameTestHelper helper) {
		ServerPlayer s = saiyan(helper);
		ServerPlayer human = mock(helper);
		float s0 = s.getFoodData().getExhaustionLevel();
		float h0 = human.getFoodData().getExhaustionLevel();
		s.causeFoodExhaustion(1.3f);
		human.causeFoodExhaustion(1.3f);
		float ds = s.getFoodData().getExhaustionLevel() - s0;
		float dh = human.getFoodData().getExhaustionLevel() - h0;
		helper.assertTrue(near(dh, 1.3f) && near(ds, 1.0f), "the Saiyan takes 1/1.3 of the exhaustion: " + ds + " vs " + dh);
		helper.succeed();
	}

	// ---------------------------------------------------------------- Ki

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void kiRegensOneASecond(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Saiyan.setKi(p, 50f);
		float[] last = { 50f };
		ticks(helper, p, 20, () -> {
			float d = Saiyan.ki(p) - last[0];
			helper.assertTrue(Math.abs(d) < 1.0e-4f || Math.abs(d - SaiyanConfig.KI_REGEN_PER_TICK) < 1.0e-3f, "1/20 Ki a step: " + d);
			last[0] = Saiyan.ki(p);
		}, () -> {
			helper.assertTrue(Saiyan.ki(p) > 50.85f && Saiyan.ki(p) < 51.06f, "about +1 in a second: " + Saiyan.ki(p));
			helper.assertTrue(near(SaiyanConfig.KI_REGEN_PER_TICK * 20f, 1f), "1 a second");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void threeMeleeHitsInARowGiveFiveKi(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Zombie z = zombie(helper, 200.0);
		Saiyan.setKi(p, 50f);
		punch(p, z, 1f);
		punch(p, z, 1f);
		helper.assertTrue(near(Saiyan.ki(p), 50f) && Saiyan.streak(p) == 2, "two hits: nothing yet");
		punch(p, z, 1f);
		helper.assertTrue(near(Saiyan.ki(p), 55f), "the third gives 5 Ki: " + Saiyan.ki(p));
		helper.assertTrue(Saiyan.streak(p) == 0, "and the streak starts over");
		// a Ki move (not a punch) never counts
		z.invulnerableTime = 0;
		z.hurt(p.damageSources().indirectMagic(p, p), 1f);
		helper.assertTrue(Saiyan.streak(p) == 0, "magic is not a melee hit");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void spendAndAddKi(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Saiyan.setKi(p, 30f);
		helper.assertTrue(Saiyan.spendKi(p, 20f) && near(Saiyan.ki(p), 10f), "spend 20 of 30");
		helper.assertTrue(!Saiyan.spendKi(p, 20f) && near(Saiyan.ki(p), 10f), "not enough: nothing spent");
		Saiyan.addKi(p, 500f);
		helper.assertTrue(near(Saiyan.ki(p), SaiyanConfig.KI_MAX), "clamped at 100");
		helper.succeed();
	}

	// ---------------------------------------------------------------- V: charge (tap / hold)

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aTapOfVChargesUntilFull(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Saiyan.setKi(p, 90f);
		SaiyanAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		SaiyanAbilityManager.handle(p, AbilitySlot.SLOT_5, false);
		helper.assertTrue(Saiyan.charging(p), "a tap keeps charging with V up");
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) < p.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue(), "slowed");
		ticks(helper, p, 30, null, () -> {
			helper.assertTrue(Saiyan.ki(p) >= SaiyanConfig.KI_MAX - EPS, "full: " + Saiyan.ki(p));
			helper.assertTrue(!Saiyan.charging(p), "a tapped charge stops at full Ki");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSecondTapOfVStopsTheCharge(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Saiyan.setKi(p, 10f);
		SaiyanAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		SaiyanAbilityManager.handle(p, AbilitySlot.SLOT_5, false);
		ticks(helper, p, 5, null, () -> {
			helper.assertTrue(Saiyan.charging(p), "still charging");
			SaiyanAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
			helper.assertTrue(!Saiyan.charging(p), "the second tap stops it");
			SaiyanAbilityManager.handle(p, AbilitySlot.SLOT_5, false);
			helper.assertTrue(!Saiyan.charging(p), "and letting go doesn't restart it");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void holdingVChargesUntilLetGo(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Saiyan.setKi(p, 10f);
		SaiyanAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		float[] last = { 10f };
		ticks(helper, p, 15, () -> {
			float d = Saiyan.ki(p) - last[0];
			helper.assertTrue(Math.abs(d) < 1.0e-4f || Math.abs(d - SaiyanConfig.KI_CHARGE_PER_TICK) < 1.0e-3f, "12 a second while charging: " + d);
			last[0] = Saiyan.ki(p);
		}, () -> {
			helper.assertTrue(Saiyan.charging(p), "held: charging");
			helper.assertTrue(Saiyan.ki(p) > 10f + SaiyanConfig.KI_CHARGE_PER_TICK * 12f, "it filled: " + Saiyan.ki(p));
			SaiyanAbilityManager.handle(p, AbilitySlot.SLOT_5, false);
			helper.assertTrue(!Saiyan.charging(p), "a hold stops on release");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aMovePressStopsTheCharge(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Saiyan.setKi(p, 10f);
		SaiyanAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		SaiyanAbilityManager.handle(p, AbilitySlot.SLOT_5, false);
		helper.assertTrue(Saiyan.charging(p), "charging");
		SaiyanAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(!Saiyan.charging(p), "G stops the charge");
		SaiyanAbilityManager.handle(p, AbilitySlot.SLOT_2, false);
		helper.succeed();
	}

	// ---------------------------------------------------------------- keys -> the kit

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void keysRouteToTheKitIds(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Object[][] table = {
				{ AbilitySlot.SLOT_1, false, SaiyanKit.COMBO }, { AbilitySlot.SLOT_1, true, SaiyanKit.MARTIAL },
				{ AbilitySlot.SLOT_2, false, SaiyanKit.KI_BLAST }, { AbilitySlot.SLOT_2, true, SaiyanKit.BEAM },
				{ AbilitySlot.SLOT_4, false, SaiyanKit.STRONG }, { AbilitySlot.SLOT_4, true, SaiyanKit.SPIRIT_BOMB },
				{ AbilitySlot.SLOT_3, false, SaiyanKit.DASH }, { AbilitySlot.SLOT_3, true, SaiyanKit.RUSH },
				{ AbilitySlot.SLOT_6, false, SaiyanKit.AOE }, { AbilitySlot.SLOT_6, true, SaiyanKit.AOE2 } };
		for (Object[] row : table) {
			AbilitySlot slot = (AbilitySlot) row[0];
			boolean shift = (Boolean) row[1];
			helper.assertTrue(row[2].equals(SaiyanAbilityManager.moveFor(slot, shift)), slot + (shift ? " + Shift" : "") + " -> " + row[2]);
			if (SaiyanKit.busy(p)) {
				continue; // a real kit move from the previous row may still hold him
			}
			p.setShiftKeyDown(shift);
			SaiyanAbilityManager.handle(p, slot, true);
			helper.assertTrue(row[2].equals(SaiyanAbilityManager.heldId(p, slot)), "the press went to " + row[2]);
			SaiyanAbilityManager.handle(p, slot, false);
			helper.assertTrue(SaiyanAbilityManager.heldId(p, slot) == null, "and its release too");
		}
		p.setShiftKeyDown(false);
		helper.assertTrue(SaiyanAbilityManager.moveFor(AbilitySlot.SLOT_5, false) == null, "V is the core's (charge)");
		SaiyanAbilityManager.handle(p, AbilitySlot.SLOT_7, true);
		helper.assertTrue(SaiyanAbilityManager.heldId(p, AbilitySlot.SLOT_7) == null, "H is never a move");
		SaiyanAbilityManager.handle(p, AbilitySlot.SLOT_8, true);
		helper.assertTrue(SaiyanAbilityManager.heldId(p, AbilitySlot.SLOT_8) == null, "N is never a move");
		String[] v = SaiyanAbilityManager.idsOf(AbilitySlot.SLOT_5);
		helper.assertTrue(v.length == 2 && v[0].equals(SaiyanAbilityManager.CHARGE) && v[1].equals(SaiyanKit.UTILITY2), "V: charge / Shift+V: utility2");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Zenkai

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 500)
	public void zenkaiAfterTenSecondsBelowThreeHearts(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		long[] t = { -1L, -1L }; // first low tick, the tick Zenkai fired
		float[] before = { 0f };
		// held at 4 HP each tick (as if still being hit), so the regeneration (+1) can't lift him to 3 hearts
		ticks(helper, p, SaiyanConfig.ZENKAI_LOW_TICKS + 4, () -> {
			long now = p.level().getGameTime();
			if (t[0] < 0) {
				t[0] = now;
			}
			if (Saiyan.zenkai(p)) {
				if (t[1] < 0) {
					t[1] = now;
					before[0] = p.getHealth();
				}
			} else {
				p.setHealth(4f);
			}
		}, () -> {
			helper.assertTrue(Saiyan.zenkai(p), "10 s below 3 hearts: Zenkai");
			long took = t[1] - t[0];
			helper.assertTrue(took >= SaiyanConfig.ZENKAI_LOW_TICKS && took <= SaiyanConfig.ZENKAI_LOW_TICKS + 2, "after 10 s, not before: " + took);
			helper.assertTrue(before[0] >= 4f + SaiyanConfig.ZENKAI_HEAL - EPS, "healed 6: " + before[0]);
			helper.assertTrue(near(Saiyan.damageMult(p, true), 1.5f) && near(Saiyan.damageMult(p, false), 1.5f), "50% stronger, melee and Ki");
			int left = Saiyan.zenkaiTicksLeft(p);
			int cd = Saiyan.zenkaiCooldown(p);
			helper.assertTrue(left > SaiyanConfig.ZENKAI_TICKS - 5 && left <= SaiyanConfig.ZENKAI_TICKS, "1 minute: " + left);
			helper.assertTrue(cd > SaiyanConfig.ZENKAI_COOLDOWN - 5 && cd <= SaiyanConfig.ZENKAI_COOLDOWN, "5 minute cooldown: " + cd);
			long until = Saiyan.state(p).zenkaiUntil;
			ticks(helper, p, SaiyanConfig.ZENKAI_LOW_TICKS + 5, () -> p.setHealth(4f), () -> {
				helper.assertTrue(Saiyan.state(p).zenkaiUntil == until, "no second Zenkai inside the cooldown");
				helper.succeed();
			});
		});
	}

	// ---------------------------------------------------------------- a stronger enemy

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aStrongerEnemyToughensHim(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Zombie weak = zombie(helper, 20.0);
		punch(p, weak, 1f);
		helper.assertTrue(!Saiyan.strongFoe(p), "a 20 HP zombie is not stronger than a 40 HP Saiyan");
		Zombie big = zombie(helper, 60.0); // 1.5x his 40
		helper.assertTrue(Saiyan.isStronger(p, big), "60 HP is 1.5x");
		punch(p, big, 1f);
		helper.assertTrue(Saiyan.strongFoe(p), "hitting it starts the bonus");
		helper.assertTrue(near(Saiyan.damageMult(p, true), 1.1f) && near(Saiyan.damageMult(p, false), 1f), "+10% melee only");
		helper.assertTrue(near(Saiyan.damageResist(p), 0.1f), "10% resistance");
		helper.assertTrue(near(Saiyan.kiRegenPerTick(p) / SaiyanConfig.KI_REGEN_PER_TICK, 1.1f), "+10% Ki regen");
		float before = p.getHealth();
		hurt(p, 10f);
		helper.assertTrue(near(before - p.getHealth(), 9f), "takes 9 of 10: " + (before - p.getHealth()));
		long until = Saiyan.state(p).strongFoeUntil;
		helper.assertTrue(until - p.level().getGameTime() <= SaiyanConfig.STRONG_WINDOW, "for the last 10 s");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Rage and Super Saiyan

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void rageFillsFromAFight(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		hurt(p, 5f);
		helper.assertTrue(near(Saiyan.rage(p), 10f), "2 per HP lost: " + Saiyan.rage(p));
		Zombie z = zombie(helper, 20.0);
		punch(p, z, 1f);
		helper.assertTrue(near(Saiyan.rage(p), 11f), "1 per hit landed: " + Saiyan.rage(p));
		punch(p, z, 1f);
		helper.assertTrue(near(Saiyan.rage(p), 11f), "at most one hit's Rage every 5 ticks");
		p.setHealth(15f); // below half of 40
		hurt(p, 2f);
		helper.assertTrue(near(Saiyan.rage(p), 11f + 2f * 2f * SaiyanConfig.RAGE_LOW_HEALTH_MULT), "x1.5 below half health: " + Saiyan.rage(p));
		helper.assertTrue(!Saiyan.transform(p), "H does nothing until the bar is full");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void superSaiyanLastsThreeMinutesThenALockout(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		double speed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		double armour = p.getAttributeValue(Attributes.ARMOR);
		Saiyan.setRage(p, SaiyanConfig.RAGE_MAX);
		helper.assertTrue(Saiyan.transform(p), "full Rage: H transforms");
		helper.assertTrue(Saiyan.ssj(p) && Saiyan.form(p) == SaiyanForm.SSJ, "Super Saiyan");
		helper.assertTrue(Saiyan.ssjTicksLeft(p) == SaiyanConfig.SSJ_TICKS && SaiyanConfig.SSJ_TICKS == 3 * 60 * 20, "exactly 3 minutes");
		helper.assertTrue(Saiyan.rage(p) == 0f, "Rage empties");
		helper.assertTrue(Saiyan.transformingTicks(p) == 0, "the burst plays");
		helper.assertTrue(near(Saiyan.damageMult(p, false), 1.35f), "+35% damage");
		helper.assertTrue(near(p.getAttributeValue(Attributes.ARMOR), armour + 4.0), "+4 armour");
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > speed + 1.0e-4, "faster");
		helper.assertTrue(near(Saiyan.kiRegenPerTick(p), SaiyanConfig.KI_REGEN_PER_TICK * 2f), "Ki regen x2");
		hurt(p, 4f);
		helper.assertTrue(Saiyan.rage(p) == 0f, "no Rage while transformed");
		helper.assertTrue(!Saiyan.transform(p), "already transformed");
		// the clock runs on game time
		int t0 = Saiyan.ssjTicksLeft(p);
		long g0 = p.level().getGameTime();
		ticks(helper, p, 3, null, () -> {
			helper.assertTrue(t0 - Saiyan.ssjTicksLeft(p) == p.level().getGameTime() - g0, "the timer counts down a tick a tick");
			edit(p, s -> s.ssjUntil = p.level().getGameTime() + 2); // fast-forward to the end
			ticks(helper, p, 3, null, () -> {
				helper.assertTrue(!Saiyan.ssj(p), "the 3 minutes are up: Base again");
				int lock = Saiyan.lockoutTicksLeft(p);
				helper.assertTrue(lock > SaiyanConfig.SSJ_LOCKOUT - 5 && lock <= SaiyanConfig.SSJ_LOCKOUT, "a 60 s lockout: " + lock);
				helper.assertTrue(near(p.getAttributeValue(Attributes.ARMOR), armour), "the armour is gone");
				Saiyan.setRage(p, SaiyanConfig.RAGE_MAX);
				helper.assertTrue(!Saiyan.transform(p), "no transforming in the lockout, even at full Rage");
				edit(p, s -> s.ssjLockUntil = 0L);
				helper.assertTrue(Saiyan.transform(p), "after the lockout, a full bar transforms again");
				helper.succeed();
			});
		});
	}

	// ---------------------------------------------------------------- Kaioken

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void shiftHoldingVThreeSecondsIsKaioken(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		p.setShiftKeyDown(true);
		SaiyanAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		long held = Saiyan.state(p).kaiokenHoldAt;
		helper.assertTrue(held > 0L, "the wind-up started");
		ticks(helper, p, SaiyanConfig.KAIOKEN_WINDUP + 3, () -> {
			if (p.level().getGameTime() - held < SaiyanConfig.KAIOKEN_WINDUP) {
				helper.assertTrue(!Saiyan.kaioken(p), "winding up, not on yet");
			}
		}, () -> {
			helper.assertTrue(Saiyan.kaioken(p), "3 s of Shift+V: Kaioken");
			SaiyanAbilityManager.handle(p, AbilitySlot.SLOT_5, false);
			helper.assertTrue(Saiyan.kaioken(p), "letting go keeps it on");
			helper.assertTrue(near(Saiyan.damageMult(p, true), 1.4f), "+40% damage");
			// a Shift+V tap ends it
			SaiyanAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
			SaiyanAbilityManager.handle(p, AbilitySlot.SLOT_5, false);
			helper.assertTrue(!Saiyan.kaioken(p), "a Shift+V tap ends it");
			int cd = Saiyan.kaiokenCooldown(p);
			helper.assertTrue(cd > SaiyanConfig.KAIOKEN_COOLDOWN - 5 && cd <= SaiyanConfig.KAIOKEN_COOLDOWN, "30 s cooldown: " + cd);
			helper.assertTrue(!Saiyan.startKaioken(p), "not again during the cooldown");
			p.setShiftKeyDown(false);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aShortShiftVHoldCancelsTheWindup(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		p.setShiftKeyDown(true);
		SaiyanAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		ticks(helper, p, 20, null, () -> {
			SaiyanAbilityManager.handle(p, AbilitySlot.SLOT_5, false);
			helper.assertTrue(!Saiyan.kaioken(p) && Saiyan.kaiokenWindup(p) == 0f, "let go after 1 s: nothing");
			p.setShiftKeyDown(false);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void kaiokenDrainsHealthAndStopsAtTwo(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		helper.assertTrue(Saiyan.startKaioken(p), "on");
		helper.assertTrue(near(Saiyan.damageMult(p, false), 1.4f), "+40% damage");
		p.setHealth(4.5f);
		float[] last = { 4.5f, 4.5f }; // last seen, lowest seen
		java.util.List<Long> drops = new java.util.ArrayList<>();
		ticks(helper, p, 3 * SaiyanConfig.KAIOKEN_DRAIN_EVERY + 6, () -> {
			float h = p.getHealth();
			last[1] = Math.min(last[1], h);
			helper.assertTrue(h <= last[0] + EPS || !Saiyan.kaioken(p), "no regeneration under Kaioken");
			if (h < last[0] - EPS) {
				float d = last[0] - h;
				helper.assertTrue(near(d, SaiyanConfig.KAIOKEN_DRAIN) || near(h, SaiyanConfig.KAIOKEN_FLOOR), "1 HP a step: " + d);
				drops.add(p.level().getGameTime());
			}
			last[0] = h;
		}, () -> {
			helper.assertTrue(drops.size() == 3, "4.5 -> 3.5 -> 2.5 -> 2: " + drops.size());
			for (int i = 1; i < drops.size(); i++) {
				helper.assertTrue(drops.get(i) - drops.get(i - 1) == SaiyanConfig.KAIOKEN_DRAIN_EVERY, "every 1.5 s: " + (drops.get(i) - drops.get(i - 1)));
			}
			helper.assertTrue(near(last[1], SaiyanConfig.KAIOKEN_FLOOR), "down to 2 HP and never below: " + last[1]);
			helper.assertTrue(p.isAlive() && !Saiyan.kaioken(p), "it ends there");
			helper.assertTrue(Saiyan.kaiokenCooldown(p) > 0, "and cools down");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void kaiokenAndSuperSaiyanDontStack(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		helper.assertTrue(Saiyan.startKaioken(p), "Kaioken in Base");
		Saiyan.setRage(p, SaiyanConfig.RAGE_MAX);
		helper.assertTrue(Saiyan.transform(p), "transforming");
		helper.assertTrue(!Saiyan.kaioken(p), "ends Kaioken");
		edit(p, s -> s.kaiokenReadyAt = 0L);
		helper.assertTrue(!Saiyan.startKaioken(p), "no Kaioken as a Super Saiyan");
		helper.succeed();
	}
}
