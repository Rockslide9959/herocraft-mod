package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.beastboy.BeastBoy;
import com.projecthero.mod.beastboy.BeastBoyAbilityManager;
import com.projecthero.mod.beastboy.BeastBoyConfig;
import com.projecthero.mod.beastboy.BeastBoyForms;
import com.projecthero.mod.beastboy.BeastBoyItems;
import com.projecthero.mod.beastboy.BeastBoyLearning;
import com.projecthero.mod.beastboy.BeastBoyMoves;
import com.projecthero.mod.beastboy.BeastForm;
import com.projecthero.mod.beastboy.data.BeastBoyState;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Beast Boy: getting it (serum, the green ocelot's bite and the fever), the hitboxes per form, form health and the
 * knock-out, Morph Stamina (drain, refill, revert at zero), the Morph Chain, every move's damage, The Beast, the context morph
 * and the wheel. Mock players are not ticked by the server, so time-based checks drive {@link BeastBoy#tick} themselves and assert
 * on per-tick steps. Mob hits are asserted as ratios of the expected damage.
 */
public class BeastBoyGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		p.setOnGround(true);
		try { // a fresh mock player is spawn-protected
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	private static ServerPlayer bb(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 4.0, 2.0);
		helper.assertTrue(BeastBoy.grant(p), "the grant works");
		return p;
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<BeastBoyState> change) {
		BeastBoyState n = BeastBoy.state(p).copy();
		change.accept(n);
		p.setAttached(ModAttachments.BEAST_BOY_STATE, n);
	}

	private static void resetChain(ServerPlayer p) {
		edit(p, s -> {
			s.chain = 0;
			s.chainForm = -1;
			s.readyAt.clear();
			s.stamina = BeastBoyConfig.STAMINA_MAX;
		});
	}

	private static Zombie zombie(GameTestHelper helper, double x, double z) {
		Zombie zb = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(x, 2.0, z));
		zb.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		zb.setHealth(200f);
		return zb;
	}

	private static void look(ServerPlayer p, LivingEntity at) {
		p.lookAt(EntityAnchorArgument.Anchor.EYES, at.position().add(0, at.getBbHeight() * 0.55, 0));
	}

	private static boolean near(float lost, float expected) {
		float r = lost / expected;
		return r > 0.85f && r < 1.15f;
	}

	/** Uses {@code id} on a fresh 200-health zombie 2 blocks in front and checks the damage. */
	private static void damageCheck(GameTestHelper helper, ServerPlayer p, String id, float expected) {
		Zombie z = zombie(helper, 4.0, 4.2);
		look(p, z);
		z.invulnerableTime = 0;
		float before = z.getHealth();
		helper.assertTrue(BeastBoyMoves.use(p, id), id + " fires");
		float lost = before - z.getHealth();
		helper.assertTrue(near(lost, expected), id + ": expected about " + expected + ", dealt " + lost);
		z.discard();
	}

	// ---------------------------------------------------------------- getting it

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void sakutiaSerumMakesBeastBoyTheOnePrimary(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 4.0, 4.0);
		com.projecthero.mod.kryptonian.Kryptonian.grant(p);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BeastBoyItems.SAKUTIA_SERUM, 2));
		helper.assertTrue(BeastBoyLearning.drinkSerum(p, InteractionHand.MAIN_HAND), "drinking the serum works");
		helper.assertTrue(BeastBoy.hasPower(p) && HeroTiers.holdsHero(p, BeastBoy.KEY), "Beast Boy is a Hero-Tier power");
		helper.assertTrue(p.getMainHandItem().getCount() == 1, "one serum is used up");
		helper.assertTrue(!com.projecthero.mod.kryptonian.Kryptonian.hasPower(p), "it replaced the Kryptonian (one Primary)");
		helper.assertTrue(PowerGrants.NOT_FROM_SERUMS.contains(BeastBoy.KEY) && !PowerGrants.missingHeroTiers(p).contains(BeastBoy.KEY),
				"random serums never give it");
		helper.assertTrue(!BeastBoyLearning.drinkSerum(p, InteractionHand.MAIN_HAND) && p.getMainHandItem().getCount() == 1, "a second one does nothing");
		helper.assertTrue(BeastBoy.stamina(p) == BeastBoyConfig.STAMINA_MAX && BeastBoy.form(p) == BeastForm.HUMAN, "full stamina, human");
		helper.assertTrue(Math.abs(p.getMaxHealth() - BeastBoyConfig.HEALTH) < 0.01f, "20 health in human form");
		BeastBoy.revoke(p);
		helper.assertTrue(!BeastBoy.hasPower(p), "revoked");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void greenOcelotBitesAFeederWhoCrouchesThenTheFeverBreaks(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 4.0, 4.0);
		Ocelot o = helper.spawnWithNoFreeWill(EntityType.OCELOT, new Vec3(5.0, 2.0, 4.0));
		helper.assertTrue(!BeastBoyLearning.isGreenOcelot(o), "an ordinary ocelot to start");
		BeastBoyLearning.setGreen(o, true);
		helper.assertTrue(BeastBoyLearning.isGreenOcelot(o), "now the green one");
		// crouching next to it without feeding it does nothing
		p.setShiftKeyDown(true);
		p.tickCount = 5;
		BeastBoy.tick(p);
		helper.assertTrue(BeastBoy.feverTicksLeft(p) == 0, "no bite before it's fed");
		BeastBoyLearning.feed(p, o);
		helper.assertTrue(BeastBoyLearning.fedBy(o, p), "it remembers who fed it");
		p.tickCount = 10;
		BeastBoy.tick(p);
		helper.assertTrue(BeastBoy.feverTicksLeft(p) > 0 && !BeastBoy.hasPower(p), "bitten: the fever, not the power yet");
		helper.assertTrue(BeastBoy.feverTicksLeft(p) <= BeastBoyConfig.FEVER_TICKS && p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "60 s of fever, slowed");
		helper.assertTrue(!BeastBoyLearning.fedBy(o, p), "it bites once");
		// the fever breaks
		edit(p, s -> s.timers = s.timers.withFever(p.level().getGameTime()));
		BeastBoy.tick(p);
		helper.assertTrue(BeastBoy.hasPower(p) && BeastBoy.feverTicksLeft(p) == 0, "he wakes up as Beast Boy");
		helper.succeed();
	}

	// ---------------------------------------------------------------- forms

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyFormHasItsOwnHitbox(GameTestHelper helper) {
		ServerPlayer p = bb(helper);
		p.moveTo(helper.absoluteVec(new Vec3(4.0, 2.0, 4.0)));
		for (BeastForm f : BeastForm.values()) {
			edit(p, s -> s.form = f.ordinal());
			p.refreshDimensions();
			float w = f == BeastForm.HUMAN ? 0.6f : f.width;
			float h = f == BeastForm.HUMAN ? 1.8f : f.height;
			helper.assertTrue(Math.abs(p.getBbWidth() - w) < 1.0e-3f && Math.abs(p.getBbHeight() - h) < 1.0e-3f,
					f + ": " + w + "x" + h + ", got " + p.getBbWidth() + "x" + p.getBbHeight());
		}
		helper.assertTrue(BeastForm.MOUSE.width < 1f && BeastForm.MOUSE.height < 1f && BeastForm.FLY.height < 1f, "the mouse and the fly fit 1-block gaps");
		edit(p, s -> s.form = 0);
		p.refreshDimensions();
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void formHealthTakesTheHitsThenKnocksHimOut(GameTestHelper helper) {
		ServerPlayer p = bb(helper);
		helper.assertTrue(BeastBoyAbilityManager.pick(p, BeastForm.WOLF), "the wheel picks the wolf");
		helper.assertTrue(BeastBoy.form(p) == BeastForm.WOLF && BeastBoy.formHp(p) == BeastForm.WOLF.formHealth, "a wolf with a full 30 pool");
		helper.assertTrue(Math.abs(p.getBbHeight() - BeastForm.WOLF.height) < 1.0e-3f, "a wolf's body");
		float hearts = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().magic(), 10f);
		helper.assertTrue(p.getHealth() == hearts, "his own hearts are untouched: " + hearts + " -> " + p.getHealth() + ", pool " + BeastBoy.formHp(p)
				+ ", allow says " + BeastBoy.allowDamage(p, p.damageSources().magic(), 1f));
		helper.assertTrue(Math.abs(BeastBoy.formHp(p) - 20f) < 0.01f, "10 came off the form's pool, left " + BeastBoy.formHp(p));
		BeastBoy.absorb(p, 25f, null);
		helper.assertTrue(BeastBoy.form(p) == BeastForm.HUMAN, "the pool is gone: knocked back to human");
		helper.assertTrue(BeastBoy.dazed(p) && p.getHealth() == hearts, "dazed, his hearts kept");
		helper.assertTrue(Math.abs(p.getBbHeight() - 1.8f) < 1.0e-3f, "a human body again");
		helper.assertTrue(BeastBoy.formLockout(p, BeastForm.WOLF) > 0, "the wolf is locked for a while");
		edit(p, s -> s.timers = s.timers.withDazed(0L));
		helper.assertTrue(!BeastBoyAbilityManager.pick(p, BeastForm.WOLF) && BeastBoyAbilityManager.pick(p, BeastForm.BEAR), "not the wolf again yet, but the bear");
		helper.assertTrue(!BeastBoyAbilityManager.pick(p, BeastForm.TIGER), "a quick-morph shape isn't on the wheel");
		helper.assertTrue(BeastBoyAbilityManager.pick(p, BeastForm.HUMAN) && BeastBoy.form(p) == BeastForm.HUMAN, "the centre turns him back");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void staminaDrainsInFormsRefillsAsHumanAndRevertsAtZero(GameTestHelper helper) {
		ServerPlayer p = bb(helper);
		BeastBoy.setStamina(p, 50f);
		long t0 = p.level().getGameTime();
		// human: refills by a fixed step a tick (a fresh "tick" each call: game time is moved by hand)
		BeastBoy.tick(p);
		float a = BeastBoy.stamina(p);
		helper.assertTrue(Math.abs(a - (50f + BeastBoyConfig.STAMINA_REGEN_PER_TICK)) < 1.0e-3f, "human form refills " + BeastBoyConfig.STAMINA_REGEN_PER_TICK + " a tick, got " + a);
		helper.assertTrue(BeastBoyAbilityManager.pick(p, BeastForm.TREX), "into the T-Rex");
		float b = BeastBoy.stamina(p);
		helper.assertTrue(Math.abs(b - (a - BeastBoyConfig.MORPH_COST)) < 1.0e-3f, "morphing costs " + BeastBoyConfig.MORPH_COST);
		helper.getLevel().setDayTime(helper.getLevel().getDayTime()); // (no-op; game time advances with the test ticks)
		helper.runAfterDelay(1, () -> {
			BeastBoy.tick(p);
			float c = BeastBoy.stamina(p);
			helper.assertTrue(Math.abs(c - (b - BeastBoyForms.drainPerTick(BeastForm.TREX))) < 1.0e-3f, "the T-Rex drains " + BeastBoyForms.drainPerTick(BeastForm.TREX)
					+ " a tick, step " + (b - c));
			helper.assertTrue(BeastBoyForms.drainPerTick(BeastForm.MOUSE) < BeastBoyForms.drainPerTick(BeastForm.TREX) / 5f, "small forms are nearly free");
			BeastBoy.setStamina(p, 0.01f);
		});
		helper.runAfterDelay(2, () -> {
			BeastBoy.tick(p);
			helper.assertTrue(BeastBoy.form(p) == BeastForm.HUMAN, "at zero he turns back");
			helper.assertTrue(p.level().getGameTime() > t0, "time moved");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void morphChainGrowsWithDifferentAnimalsAndResetsOnARepeat(GameTestHelper helper) {
		ServerPlayer p = bb(helper);
		helper.assertTrue(BeastBoy.chain(p) == 0, "no chain to start");
		BeastBoy.chainHit(p, BeastForm.RHINO);
		helper.assertTrue(BeastBoy.chain(p) == 0, "the first animal starts it");
		float m = BeastBoy.chainHit(p, BeastForm.GORILLA);
		helper.assertTrue(BeastBoy.chain(p) == 1 && Math.abs(m - 1.08f) < 1.0e-4f, "a different animal: +1 (+8%)");
		BeastBoy.chainHit(p, BeastForm.TIGER);
		BeastBoy.chainHit(p, BeastForm.SNAKE);
		BeastBoy.chainHit(p, BeastForm.TREX);
		BeastBoy.chainHit(p, BeastForm.ELEPHANT);
		m = BeastBoy.chainHit(p, BeastForm.OCTOPUS);
		helper.assertTrue(BeastBoy.chain(p) == BeastBoyConfig.CHAIN_MAX && Math.abs(m - 1.4f) < 1.0e-4f, "capped at 5 (+40%)");
		BeastBoy.chainHit(p, BeastForm.OCTOPUS);
		helper.assertTrue(BeastBoy.chain(p) == 0, "the same animal twice resets it");
		// a real move feeds it only when it lands
		resetChain(p);
		Zombie z = zombie(helper, 4.0, 4.2);
		look(p, z);
		BeastBoyMoves.use(p, BeastBoyAbilityManager.GORILLA_SMASH);
		z.invulnerableTime = 0;
		float before = z.getHealth();
		BeastBoyMoves.use(p, BeastBoyAbilityManager.ELEPHANT_STOMP);
		helper.assertTrue(BeastBoy.chain(p) == 1, "smash then stomp: one link");
		helper.assertTrue(near(before - z.getHealth(), BeastBoyConfig.STOMP_DAMAGE * 1.08f), "the stomp carried the +8%, dealt " + (before - z.getHealth()));
		helper.succeed();
	}

	// ---------------------------------------------------------------- the moves

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void humanQuickMorphsDealTheirDamage(GameTestHelper helper) {
		ServerPlayer p = bb(helper);
		Object[][] moves = { { BeastBoyAbilityManager.RAM, BeastBoyConfig.RAM_DAMAGE }, { BeastBoyAbilityManager.POUNCE, BeastBoyConfig.POUNCE_DAMAGE },
				{ BeastBoyAbilityManager.GORILLA_SMASH, BeastBoyConfig.SMASH_DAMAGE }, { BeastBoyAbilityManager.SNAKE_STRIKE, BeastBoyConfig.SNAKE_DAMAGE },
				{ BeastBoyAbilityManager.TREX_ROAR, BeastBoyConfig.ROAR_DAMAGE }, { BeastBoyAbilityManager.ELEPHANT_STOMP, BeastBoyConfig.STOMP_DAMAGE } };
		for (Object[] m : moves) {
			resetChain(p);
			p.moveTo(helper.absoluteVec(new Vec3(4.0, 2.0, 2.0)));
			float st = BeastBoy.stamina(p);
			damageCheck(helper, p, (String) m[0], (Float) m[1]);
			helper.assertTrue(Math.abs(st - BeastBoy.stamina(p) - BeastBoyAbilityManager.cost((String) m[0])) < 0.01f, m[0] + " costs its stamina");
			helper.assertTrue(BeastBoy.quickForm(p) == BeastBoyAbilityManager.quickAnimal((String) m[0]), m[0] + " flashes its animal");
			helper.assertTrue(BeastBoy.form(p) == BeastForm.HUMAN, m[0] + ": back to human after");
		}
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void quickMorphSideEffects(GameTestHelper helper) {
		ServerPlayer p = bb(helper);
		// an illager, not undead: zombies shrug off poison
		net.minecraft.world.entity.monster.Vindicator z = helper.spawnWithNoFreeWill(EntityType.VINDICATOR, new Vec3(4.0, 2.0, 4.2));
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		z.setHealth(200f);
		look(p, z);
		helper.assertTrue(BeastBoyMoves.use(p, BeastBoyAbilityManager.POUNCE) && BeastBoyMoves.bleeding(z), "Pounce makes it bleed");
		helper.assertTrue(!BeastBoyMoves.use(p, BeastBoyAbilityManager.POUNCE), "and is on cooldown");
		resetChain(p);
		helper.assertTrue(BeastBoyMoves.use(p, BeastBoyAbilityManager.SNAKE_STRIKE) && z.hasEffect(MobEffects.POISON) && z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
				"Snake Strike poisons and slows");
		resetChain(p);
		helper.assertTrue(BeastBoyMoves.use(p, BeastBoyAbilityManager.TREX_ROAR) && BeastBoyMoves.afraid(z), "the roar scares it");
		resetChain(p);
		helper.assertTrue(BeastBoyMoves.use(p, BeastBoyAbilityManager.OCTOPUS_GRAB) && BeastBoyMoves.held(z), "the octopus holds it");
		resetChain(p);
		helper.assertTrue(BeastBoyMoves.use(p, BeastBoyAbilityManager.ANIMAL_SENSES) && BeastBoy.sensing(p), "Animal Senses");
		BeastBoy.setStamina(p, 5f);
		helper.assertTrue(!BeastBoyMoves.use(p, BeastBoyAbilityManager.RAM) && Math.abs(BeastBoy.stamina(p) - 5f) < 1.0e-3f, "too little stamina: refused, nothing spent");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void sustainedFormsHitWithTheirOwnMoves(GameTestHelper helper) {
		ServerPlayer p = bb(helper);
		Object[][] rows = { { BeastForm.TREX, "trex_bite", BeastBoyConfig.TREX_BITE }, { BeastForm.TREX, "trex_tail", BeastBoyConfig.TREX_TAIL },
				{ BeastForm.GORILLA, "gorilla_punch", BeastBoyConfig.GORILLA_PUNCH }, { BeastForm.RHINO, "rhino_charge", BeastBoyConfig.RHINO_CHARGE },
				{ BeastForm.ELEPHANT, "elephant_stomp", BeastBoyConfig.ELEPHANT_STOMP }, { BeastForm.ELEPHANT, "elephant_trunk", BeastBoyConfig.ELEPHANT_TRUNK },
				{ BeastForm.WOLF, "wolf_bite", BeastBoyConfig.WOLF_BITE }, { BeastForm.BEAR, "bear_maul", BeastBoyConfig.BEAR_MAUL },
				{ BeastForm.PTERODACTYL, "ptero_dive", BeastBoyConfig.PTERO_DIVE }, { BeastForm.HAWK, "hawk_talon", BeastBoyConfig.HAWK_TALON },
				{ BeastForm.SHARK, "shark_bite", BeastBoyConfig.SHARK_BITE }, { BeastForm.WHALE, "whale_slam", BeastBoyConfig.WHALE_SLAM },
				{ BeastForm.GORILLA, "gorilla_throw", BeastBoyConfig.GORILLA_THROW } };
		for (Object[] r : rows) {
			BeastForm f = (BeastForm) r[0];
			resetChain(p);
			p.moveTo(helper.absoluteVec(new Vec3(4.0, 2.0, 2.0)));
			BeastBoy.setForm(p, f);
			helper.assertTrue(BeastBoy.form(p) == f, "now a " + f);
			String id = (String) r[1];
			helper.assertTrue(BeastBoyAbilityManager.moveFor(f, AbilitySlot.SLOT_1, false).equals(id)
					|| id.equals(BeastBoyAbilityManager.moveFor(f, AbilitySlot.SLOT_2, false))
					|| id.equals(BeastBoyAbilityManager.moveFor(f, AbilitySlot.SLOT_6, false)), id + " is on the " + f + "'s R / G / C");
			damageCheck(helper, p, id, (Float) r[2]);
		}
		BeastBoy.setForm(p, BeastForm.MOUSE);
		resetChain(p);
		helper.assertTrue(!BeastBoyMoves.use(p, BeastBoyAbilityManager.RAM), "the mouse can't attack");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBeastLocksTheChainAndTheWheel(GameTestHelper helper) {
		ServerPlayer p = bb(helper);
		p.moveTo(helper.absoluteVec(new Vec3(4.0, 2.0, 2.0)));
		BeastBoy.setStamina(p, 50f);
		helper.assertTrue(!BeastBoyMoves.use(p, BeastBoyAbilityManager.THE_BEAST), "needs 100 stamina");
		BeastBoy.setStamina(p, 100f);
		helper.assertTrue(BeastBoyMoves.use(p, BeastBoyAbilityManager.THE_BEAST), "Shift+Z: The Beast");
		helper.assertTrue(BeastBoy.isBeast(p) && BeastBoy.formHp(p) == 60f && BeastBoy.stamina(p) < 1f, "60 form health, all the stamina");
		helper.assertTrue(BeastBoy.chain(p) == BeastBoyConfig.CHAIN_MAX, "the chain is locked at 5");
		helper.assertTrue(Math.abs(BeastBoy.beastTicksLeft(p) - BeastBoyConfig.BEAST_TICKS) <= 1, "for 30 s");
		helper.assertTrue(!BeastBoyAbilityManager.pick(p, BeastForm.WOLF) && BeastBoy.isBeast(p), "no wheel until it ends");
		helper.assertTrue(!BeastBoyMoves.use(p, BeastBoyAbilityManager.CONTEXT_MORPH) && BeastBoy.isBeast(p), "no context morph either");
		helper.assertTrue(BeastBoy.cooldownRemaining(p, BeastBoyAbilityManager.THE_BEAST) == BeastBoyConfig.BEAST_COOLDOWN, "3 min cooldown");
		damageCheck(helper, p, "beast_swipe", BeastBoyConfig.BEAST_SWIPE * 1.4f);
		edit(p, s -> s.timers = s.timers.withBeast(p.level().getGameTime()));
		BeastBoy.tick(p);
		helper.assertTrue(!BeastBoy.isBeast(p) && BeastBoy.form(p) == BeastForm.HUMAN, "it ends back in human form");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void contextAndFlyMorphs(GameTestHelper helper) {
		ServerPlayer p = bb(helper);
		p.setOnGround(true);
		helper.assertTrue(BeastBoyMoves.contextFor(p) == BeastForm.CHEETAH, "on the ground: the cheetah");
		helper.assertTrue(BeastBoyMoves.use(p, BeastBoyAbilityManager.CONTEXT_MORPH) && BeastBoy.form(p) == BeastForm.CHEETAH, "X: a cheetah");
		double base = p.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > base * 2.0, "very fast");
		resetChain(p);
		helper.assertTrue(BeastBoyMoves.use(p, BeastBoyAbilityManager.CONTEXT_MORPH) && BeastBoy.form(p) == BeastForm.HUMAN, "X again: human");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - base) < 1.0e-4, "normal speed again");
		p.setOnGround(false);
		helper.assertTrue(BeastBoyMoves.contextFor(p) == BeastForm.HAWK, "in the air: the hawk");
		resetChain(p);
		helper.assertTrue(BeastBoyMoves.use(p, BeastBoyAbilityManager.FLY_MORPH) && BeastBoy.form(p) == BeastForm.FLY, "Shift+X: a fly");
		helper.assertTrue(p.getBbHeight() < 0.5f && p.getBbWidth() < 0.5f, "tiny");
		helper.assertTrue(BeastBoyForms.harmless(BeastForm.FLY), "it can't attack");
		resetChain(p);
		helper.assertTrue(BeastBoyMoves.use(p, BeastBoyAbilityManager.FLY_MORPH) && BeastBoy.form(p) == BeastForm.HUMAN, "Shift+X again: human");
		helper.succeed();
	}
}
