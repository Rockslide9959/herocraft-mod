package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hulk.Hulk;
import com.projecthero.mod.hulk.HulkAbilities;
import com.projecthero.mod.hulk.HulkAbilityManager;
import com.projecthero.mod.hulk.HulkConfig;
import com.projecthero.mod.hulk.gladiator.GladiatorAbilities;
import com.projecthero.mod.hulk.gladiator.GladiatorAxeEntity;
import com.projecthero.mod.hulk.gladiator.GladiatorGear;
import com.projecthero.mod.hulk.gladiator.GladiatorHammerEntity;
import com.projecthero.mod.hulk.gladiator.GladiatorSwing;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.3: the Gladiator Hulk's twelve moves. The kit is switched on with {@link GladiatorAbilities#forceKitForTests},
 * so these never depend on how the gear itself is worn. Every move is checked against a husk (a zombie that does not
 * burn in CI daylight). Each damage test has its own batch so no neighbouring shockwave reaches its mobs, and every
 * wait is a {@code succeedWhen} / {@code thenWaitUntil} (CI ticks differently -- never assert on elapsed totals).
 * Mock players are not ticked by the server: each test drives {@link Hulk#tick} itself.
 */
public class GladiatorMovesGameTests implements FabricGameTest {
	/** A Gamma player out as the Hulk (the willing change), in a cleared room with a dirt floor, facing +Z. */
	private static ServerPlayer hulk(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-4, 0, -6), base.offset(4, 5, 8))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-4, -1, -6), base.offset(4, -1, 8))) {
			helper.getLevel().setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
		}
		helper.assertTrue(Hulk.grant(p), "the Gamma power is granted");
		Hulk.setRage(p, 100.0f);
		Hulk.tryTransform(p);
		helper.assertTrue(Hulk.isHulk(p), "precondition: Hulk");
		p.setOnGround(true);
		return p;
	}

	private static ServerPlayer gladiator(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		GladiatorAbilities.forceKitForTests(p, true);
		helper.assertTrue(GladiatorAbilities.active(p), "precondition: the Gladiator kit is on");
		return p;
	}

	private static Husk husk(GameTestHelper helper, Vec3 at) {
		Husk z = EntityType.HUSK.create(helper.getLevel());
		z.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		z.setNoAi(true);
		helper.getLevel().addFreshEntity(z);
		return z;
	}

	/** A husk with 200 health, so a big hit (or a few) never kills it before the test has looked. */
	private static Husk tough(GameTestHelper helper, Vec3 at) {
		Husk z = husk(helper, at);
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		z.setHealth(200.0f);
		return z;
	}

	/** Hurt, and by him (batches reuse ground: a leftover from another test must not count). */
	private static boolean hurtBy(Husk z, ServerPlayer p) {
		return (z.isDeadOrDying() || z.getHealth() < z.getMaxHealth()) && z.getLastHurtByMob() == p;
	}

	private static Vec3 chest(Husk z) {
		return z.position().add(0, z.getBbHeight() * 0.5, 0);
	}

	/** Drive his tick every game tick, his eyes held on {@code look} (a mock player's rotation can drift on CI). */
	private static void drive(GameTestHelper helper, ServerPlayer p, Vec3 look) {
		helper.onEachTick(() -> {
			if (look != null) {
				p.lookAt(EntityAnchorArgument.Anchor.EYES, look);
			}
			Hulk.setRage(p, 100.0f);
			Hulk.tick(p);
		});
	}

	// ---------------------------------------------------------------- the kit switch, cooldowns, weapons in hand

	@GameTest(template = EMPTY_STRUCTURE, batch = "gladiator_kit")
	public void theGladiatorKitOnlyReplacesTheKeysWithTheFullKit(GameTestHelper helper) {
		ServerPlayer plain = hulk(helper);
		helper.assertFalse(GladiatorAbilities.active(plain), "no gear: the bare-handed kit");
		HulkAbilityManager.handle(plain, AbilitySlot.SLOT_2, true);
		helper.assertTrue(HulkAbilities.cooldownRemaining(plain, HulkAbilities.GROUND_SMASH) > 0, "G is Ground Smash without the kit");
		helper.assertTrue(HulkAbilities.cooldownRemaining(plain, GladiatorAbilities.HAMMER_QUAKE) == 0, "and never Hammer Quake");

		ServerPlayer glad = gladiator(helper);
		HulkAbilityManager.handle(glad, AbilitySlot.SLOT_2, true);
		helper.assertTrue(HulkAbilities.cooldownRemaining(glad, GladiatorAbilities.HAMMER_QUAKE) > 0, "with the kit G is Hammer Quake");
		helper.assertTrue(HulkAbilities.cooldownRemaining(glad, HulkAbilities.GROUND_SMASH) == 0, "and not Ground Smash");
		glad.setShiftKeyDown(true);
		HulkAbilityManager.handle(glad, AbilitySlot.SLOT_1, true);
		glad.setShiftKeyDown(false);
		helper.assertTrue(HulkAbilities.cooldownRemaining(glad, GladiatorAbilities.HAMMER_UPPERCUT) > 0, "Shift+R is Hammer Uppercut");
		helper.assertTrue(HulkAbilities.cooldownRemaining(glad, GladiatorAbilities.AXE_CLEAVE) == 0, "not the tap move");

		Hulk.revert(glad, false);
		helper.assertFalse(GladiatorAbilities.active(glad), "Banner has no Gladiator kit, gear or not");
		GladiatorAbilities.axeCleave(glad);
		helper.assertTrue(HulkAbilities.cooldownRemaining(glad, GladiatorAbilities.AXE_CLEAVE) == 0, "and Banner can't use the moves");
		helper.succeed();
	}

	/** v0.15.5: plain melee swings alternate hammer (main / right hand) and axe (off / left hand), strictly. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "gladiator_kit")
	public void gladiatorMeleeSwingsStrictlyAlternateHands(GameTestHelper helper) {
		ServerPlayer plain = hulk(helper);
		plain.swing(InteractionHand.OFF_HAND, true);
		helper.assertTrue(GladiatorSwing.next(plain, InteractionHand.MAIN_HAND) == InteractionHand.MAIN_HAND,
				"without the kit the Hulk swings the hand vanilla picks");

		ServerPlayer p = gladiator(helper);
		helper.assertTrue(GladiatorSwing.after(null) == InteractionHand.MAIN_HAND, "the first swing is the hammer's");
		helper.assertTrue(GladiatorSwing.arm(p, InteractionHand.MAIN_HAND) == HumanoidArm.RIGHT, "main hand = right arm (hammer)");
		helper.assertTrue(GladiatorSwing.arm(p, InteractionHand.OFF_HAND) == HumanoidArm.LEFT, "off hand = left arm (axe)");
		p.swing(InteractionHand.MAIN_HAND, true);
		InteractionHand last = InteractionHand.MAIN_HAND;
		for (int i = 0; i < 6; i++) {
			InteractionHand next = GladiatorSwing.next(p, InteractionHand.MAIN_HAND);
			helper.assertTrue(next != last, "swing " + i + " switches hands (was " + last + ")");
			p.swing(next, true);
			helper.assertTrue(p.swingingArm == next, "the swing is recorded on that hand");
			last = next;
		}

		Hulk.revert(p, false);
		p.swing(InteractionHand.MAIN_HAND, true);
		helper.assertTrue(GladiatorSwing.next(p, InteractionHand.MAIN_HAND) == InteractionHand.MAIN_HAND,
				"Banner swings the main hand as usual");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "gladiator_kit")
	public void aMoveRespectsItsCooldownAndAThrownAxeBlocksTheAxeMoves(GameTestHelper helper) {
		ServerPlayer p = gladiator(helper);
		GladiatorAbilities.axeCleave(p);
		int first = HulkAbilities.cooldownRemaining(p, GladiatorAbilities.AXE_CLEAVE);
		helper.assertTrue(first > 0, "Axe Cleave goes on cooldown");
		helper.assertTrue(first <= HulkConfig.gladiator().axeCleaveCooldownTicks, "its own cooldown, got " + first);
		GladiatorAbilities.axeCleave(p);
		helper.assertTrue(HulkAbilities.cooldownRemaining(p, GladiatorAbilities.AXE_CLEAVE) == first, "a second press while cooling does nothing");

		ServerPlayer q = gladiator(helper);
		GladiatorAbilities.axeThrow(q);
		helper.assertTrue(GladiatorAbilities.axeAway(q) && GladiatorGear.weaponAway(q, true), "the axe is out of his hand");
		GladiatorAbilities.earthsplitter(q);
		helper.assertTrue(HulkAbilities.cooldownRemaining(q, GladiatorAbilities.EARTHSPLITTER) == 0, "no Earthsplitter without the axe");
		GladiatorAbilities.hammerQuake(q);
		helper.assertTrue(HulkAbilities.cooldownRemaining(q, GladiatorAbilities.HAMMER_QUAKE) > 0, "the hammer moves still work");
		GladiatorAbilities.clear(q);
		helper.assertFalse(GladiatorGear.weaponAway(q, true), "clear() brings the axe home");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R / Shift+R

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160, batch = "gladiator_axe_cleave")
	public void axeCleaveHitsTheArcInFrontAndBleeds(GameTestHelper helper) {
		ServerPlayer p = gladiator(helper);
		Husk front = tough(helper, p.position().add(1.5, 0, 3.0));
		Husk behind = husk(helper, p.position().add(0, 0, -4.0));
		Vec3 look = p.position().add(0, 1.6, 6.0);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, look);
		Vec3 spot = front.position();
		GladiatorAbilities.axeCleave(p);
		drive(helper, p, look);
		// CI flake guard: if a swing whiffs (the husk nudged out of the arc, a late tick), hold it on its spot and swing again
		int[] age = { 0 };
		helper.onEachTick(() -> {
			age[0]++;
			if (!hurtBy(front, p)) {
				front.moveTo(spot.x, spot.y, spot.z, 0.0f, 0.0f);
				if (age[0] % 40 == 0) {
					HulkAbilities.cooldown(p, GladiatorAbilities.AXE_CLEAVE, 0);
					GladiatorAbilities.axeCleave(p);
				}
			}
		});
		float[] afterHit = { -1.0f };
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(hurtBy(front, p), "the husk in the arc is cleaved"))
				.thenExecute(() -> afterHit[0] = front.getHealth())
				.thenWaitUntil(() -> helper.assertTrue(front.getHealth() < afterHit[0] || front.isDeadOrDying(), "and it bleeds afterwards"))
				.thenExecute(() -> helper.assertTrue(behind.getLastHurtByMob() != p, "the one behind him is not hit"))
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "gladiator_uppercut")
	public void hammerUppercutLaunchesOneTargetHigh(GameTestHelper helper) {
		ServerPlayer p = gladiator(helper);
		Husk mob = tough(helper, p.position().add(0, 0, 3.0));
		double y0 = mob.getY();
		p.lookAt(EntityAnchorArgument.Anchor.EYES, chest(mob));
		GladiatorAbilities.hammerUppercut(p);
		helper.assertTrue(HulkAbilities.cooldownRemaining(p, GladiatorAbilities.HAMMER_UPPERCUT) > 0, "Hammer Uppercut goes on cooldown");
		double[] best = { 0.0 };
		helper.onEachTick(() -> {
			p.lookAt(EntityAnchorArgument.Anchor.EYES, chest(mob));
			Hulk.tick(p);
			best[0] = Math.max(best[0], Math.max(mob.getDeltaMovement().y, mob.getY() - y0));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(hurtBy(mob, p), "it is hit");
			helper.assertTrue(best[0] > 0.6, "and launched upward, best " + best[0]);
		});
	}

	// ---------------------------------------------------------------- G / Shift+G

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "gladiator_quake")
	public void hammerQuakeHitsTheConeInFront(GameTestHelper helper) {
		ServerPlayer p = gladiator(helper);
		Husk front = husk(helper, p.position().add(0, 0, 5.0));
		Husk behind = husk(helper, p.position().add(0, 0, -4.0));
		Vec3 look = p.position().add(0, 1.6, 8.0);
		GladiatorAbilities.hammerQuake(p);
		drive(helper, p, look);
		helper.succeedWhen(() -> {
			helper.assertTrue(hurtBy(front, p), "the husk in the cone is hit");
			helper.assertTrue(behind.getLastHurtByMob() != p, "the one behind is not");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "gladiator_earthsplitter")
	public void earthsplitterHitsAlongItsLineOnly(GameTestHelper helper) {
		ServerPlayer p = gladiator(helper);
		Husk onLine = tough(helper, p.position().add(0, 0, 6.0));
		Husk offLine = husk(helper, p.position().add(3.5, 0, 6.0));
		Vec3 look = p.position().add(0, 1.6, 10.0);
		double y0 = onLine.getY();
		GladiatorAbilities.earthsplitter(p);
		helper.assertTrue(HulkAbilities.cooldownRemaining(p, GladiatorAbilities.EARTHSPLITTER) > 0, "Earthsplitter goes on cooldown");
		double[] best = { 0.0 };
		helper.onEachTick(() -> {
			p.lookAt(EntityAnchorArgument.Anchor.EYES, look);
			Hulk.tick(p);
			best[0] = Math.max(best[0], Math.max(onLine.getDeltaMovement().y, onLine.getY() - y0));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(hurtBy(onLine, p), "the husk on the line is hit");
			helper.assertTrue(best[0] > 0.5, "and thrown up by the eruption, best " + best[0]);
			helper.assertTrue(offLine.getLastHurtByMob() != p, "the one beside the line is not");
		});
	}

	// ---------------------------------------------------------------- Z / Shift+Z

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "gladiator_roar")
	public void championsRoarBuffsHimAndStaggersMobs(GameTestHelper helper) {
		ServerPlayer p = gladiator(helper);
		Husk mob = husk(helper, p.position().add(0, 0, 4.0));
		Hulk.setRage(p, 50.0f);
		// v0.15.18: measured after the rage is set -- rage tiers change his attack damage too (50 and 65 are both Angry)
		double attack = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		GladiatorAbilities.championsRoar(p);
		helper.assertTrue(HulkAbilities.cooldownRemaining(p, GladiatorAbilities.CHAMPIONS_ROAR) > 0, "the roar goes on cooldown");
		helper.onEachTick(() -> Hulk.tick(p));
		helper.succeedWhen(() -> {
			helper.assertTrue(GladiatorAbilities.roaring(p), "the buff is on");
			helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) > attack + 1.0, "with more attack damage");
			helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 0.99, "and full knockback resistance");
			helper.assertTrue(Hulk.rage(p) > 50.0f, "and more rage, got " + Hulk.rage(p));
			helper.assertTrue(mob.hasEffect(MobEffects.WEAKNESS), "the husk is staggered");
			GladiatorAbilities.clear(p);
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - attack) < 1.0e-3, "clear() takes the buff off");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "gladiator_roar_fear")
	public void championsRoarScaresOffOnlyLowLevelMobs(GameTestHelper helper) {
		ServerPlayer p = gladiator(helper);
		Husk weak = husk(helper, p.position().add(0, 0, 4.0));
		Husk strong = tough(helper, p.position().add(0, 0, -4.0));
		GladiatorAbilities.championsRoar(p);
		helper.onEachTick(() -> Hulk.tick(p));
		helper.succeedWhen(() -> {
			helper.assertTrue(weak.hasEffect(MobEffects.WEAKNESS) && strong.hasEffect(MobEffects.WEAKNESS), "both are staggered");
			helper.assertTrue(GladiatorAbilities.fleeing(weak), "a 20-health husk runs from him");
			helper.assertTrue(weak.getTarget() == null, "and has no target");
			helper.assertFalse(GladiatorAbilities.fleeing(strong), "a 200-health one stands its ground");
			GladiatorAbilities.clear(p);
			helper.assertFalse(GladiatorAbilities.fleeing(weak), "clear() ends the fear");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "gladiator_clash")
	public void weaponClashHitsAndStunsAllRound(GameTestHelper helper) {
		ServerPlayer p = gladiator(helper);
		Husk front = husk(helper, p.position().add(0, 0, 3.0));
		Husk back = husk(helper, p.position().add(0, 0, -3.0));
		GladiatorAbilities.weaponClash(p);
		helper.assertTrue(HulkAbilities.cooldownRemaining(p, GladiatorAbilities.WEAPON_CLASH) > 0, "Weapon Clash goes on cooldown");
		helper.onEachTick(() -> Hulk.tick(p));
		helper.succeedWhen(() -> {
			for (Husk z : new Husk[] { front, back }) {
				helper.assertTrue(hurtBy(z, p), "everything round him is hit");
				MobEffectInstance slow = z.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
				helper.assertTrue(slow != null && slow.getAmplifier() >= 9, "and stunned");
			}
		});
	}

	// ---------------------------------------------------------------- X / Shift+X

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "gladiator_arena_leap")
	public void arenaLeapLaunchesAndLandsInASlam(GameTestHelper helper) {
		ServerPlayer p = gladiator(helper);
		Husk mob = husk(helper, p.position().add(0, 0, 2.5));
		p.lookAt(EntityAnchorArgument.Anchor.EYES, p.position().add(0, 1.6, 10.0));
		GladiatorAbilities.arenaLeap(p);
		helper.assertTrue(p.getDeltaMovement().length() > 0.5, "he is launched, got " + p.getDeltaMovement());
		helper.assertTrue(GladiatorAbilities.busy(p), "and in the leap");
		// a mock player never actually moves: he "lands" where he took off, a few ticks later
		helper.onEachTick(() -> {
			p.setOnGround(true);
			Hulk.tick(p);
		});
		helper.succeedWhen(() -> {
			helper.assertFalse(GladiatorAbilities.busy(p), "the leap ends on the ground");
			helper.assertTrue(hurtBy(mob, p), "in a weapons-first slam");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160, batch = "gladiator_meteor")
	public void meteorDiveRisesThenDivesAndHitsHard(GameTestHelper helper) {
		ServerPlayer p = gladiator(helper);
		Husk mob = tough(helper, p.position().add(0, 0, 2.0));
		GladiatorAbilities.meteorDive(p);
		helper.assertTrue(p.getDeltaMovement().y > 1.0, "straight up first, got " + p.getDeltaMovement());
		helper.assertTrue(GladiatorAbilities.busy(p), "in the dive");
		float before = mob.getHealth();
		helper.onEachTick(() -> {
			p.lookAt(EntityAnchorArgument.Anchor.EYES, mob.position());
			p.setOnGround(true);
			Hulk.tick(p);
		});
		helper.succeedWhen(() -> {
			helper.assertFalse(GladiatorAbilities.busy(p), "the dive lands");
			helper.assertTrue(hurtBy(mob, p), "on the husk");
			helper.assertTrue(mob.isDeadOrDying() || before - mob.getHealth() >= 10.0f, "and hard, health " + mob.getHealth());
		});
	}

	// ---------------------------------------------------------------- C / Shift+C

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "gladiator_axe_throw")
	public void axeThrowCutsAndComesBack(GameTestHelper helper) {
		ServerPlayer p = gladiator(helper);
		Husk mob = husk(helper, p.position().add(0, 0, 4.0));
		Vec3 look = chest(mob);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, look);
		GladiatorAbilities.axeThrow(p);
		helper.assertTrue(GladiatorGear.weaponAway(p, true), "the left hand is empty while the axe is out");
		drive(helper, p, look);
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertFalse(helper.getLevel().getEntitiesOfClass(GladiatorAxeEntity.class,
						p.getBoundingBox().inflate(30.0)).isEmpty(), "the axe is in flight"))
				.thenWaitUntil(() -> helper.assertTrue(hurtBy(mob, p), "it cuts the husk"))
				.thenWaitUntil(() -> {
					helper.assertFalse(GladiatorAbilities.axeAway(p), "it comes back");
					helper.assertFalse(GladiatorGear.weaponAway(p, true), "and the away flag is cleared");
					helper.assertTrue(helper.getLevel().getEntitiesOfClass(GladiatorAxeEntity.class,
							p.getBoundingBox().inflate(30.0)).isEmpty(), "and the flying axe is gone");
				})
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260, batch = "gladiator_hammer_hurl")
	public void hammerHurlLandsStaysStuckAndCIsTheRecall(GameTestHelper helper) {
		ServerPlayer p = gladiator(helper);
		Husk mob = husk(helper, p.position().add(0, 0, 3.5));
		Vec3 look = p.position().add(0, 0, 3.5); // down at the ground beside the husk
		p.lookAt(EntityAnchorArgument.Anchor.EYES, look);
		GladiatorAbilities.hammerHurl(p);
		helper.assertTrue(GladiatorGear.weaponAway(p, false), "the right hand is empty while the hammer is out");
		drive(helper, p, look);
		GladiatorHammerEntity[] hammer = { null };
		helper.startSequence()
				.thenWaitUntil(() -> {
					var found = helper.getLevel().getEntitiesOfClass(GladiatorHammerEntity.class, p.getBoundingBox().inflate(30.0));
					helper.assertTrue(!found.isEmpty() && found.get(0).stuck(), "the hammer lands and stays stuck in the ground");
					hammer[0] = found.get(0);
				})
				.thenExecute(() -> {
					helper.assertTrue(hurtBy(mob, p), "its landing shockwave hits the husk");
					helper.assertTrue(GladiatorAbilities.hammerAway(p), "it is still away while stuck");
					// any C calls it home (Shift or not)
					p.setShiftKeyDown(true);
					HulkAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
					p.setShiftKeyDown(false);
					helper.assertTrue(hammer[0].returning(), "C recalls it");
				})
				.thenWaitUntil(() -> {
					helper.assertFalse(GladiatorAbilities.hammerAway(p), "it flies home");
					helper.assertFalse(GladiatorGear.weaponAway(p, false), "and the away flag is cleared");
					helper.assertTrue(hammer[0].isRemoved(), "and the thrown hammer is gone");
				})
				.thenSucceed();
	}

	// ---------------------------------------------------------------- V / Shift+V

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "gladiator_whirlwind")
	public void whirlwindHitsRepeatedlyAndEndsInASlam(GameTestHelper helper) {
		ServerPlayer p = gladiator(helper);
		Husk close = tough(helper, p.position().add(0, 0, 2.0));
		GladiatorAbilities.whirlwind(p);
		helper.assertTrue(GladiatorAbilities.whirling(p), "V starts the spin");
		helper.assertTrue(GladiatorAbilities.busy(p), "nothing else while spinning");
		float[] last = { 200.0f };
		int[] hits = { 0 };
		helper.onEachTick(() -> {
			Hulk.tick(p);
			if (close.getHealth() < last[0] - 1.0e-3f) {
				hits[0]++;
			}
			last[0] = close.getHealth();
		});
		helper.succeedWhen(() -> {
			helper.assertFalse(GladiatorAbilities.whirling(p), "the spin ends");
			helper.assertTrue(hits[0] >= 3, "after hitting again and again, hits " + hits[0]);
			int cd = HulkAbilities.cooldownRemaining(p, GladiatorAbilities.WHIRLWIND);
			helper.assertTrue(cd > 0 && cd <= HulkConfig.gladiator().whirlwindCooldownTicks, "its cooldown runs from the end, got " + cd);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160, batch = "gladiator_grapple")
	public void arenaGrapplePinsPoundsAndThrows(GameTestHelper helper) {
		ServerPlayer p = gladiator(helper);
		Husk mob = tough(helper, p.position().add(0, 0, 2.5));
		p.lookAt(EntityAnchorArgument.Anchor.EYES, chest(mob));
		GladiatorAbilities.arenaGrapple(p);
		helper.assertTrue(GladiatorAbilities.grappling(p), "Shift+V grabs the husk");
		helper.assertTrue(GladiatorAbilities.isPinned(mob), "and pins it");
		helper.onEachTick(() -> Hulk.tick(p));
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(mob.getHealth() <= 200.0f - 2.0f * HulkConfig.gladiator().grappleBlowDamage + 0.01f,
						"the hammer blows land, health " + mob.getHealth()))
				.thenExecute(() -> {
					GladiatorAbilities.arenaGrapple(p);
					helper.assertFalse(GladiatorAbilities.grappling(p), "Shift+V again throws it");
					helper.assertFalse(GladiatorAbilities.isPinned(mob), "and lets it go");
					helper.assertTrue(mob.getDeltaMovement().length() > 0.5, "flying, got " + mob.getDeltaMovement());
					helper.assertTrue(HulkAbilities.cooldownRemaining(p, GladiatorAbilities.ARENA_GRAPPLE) > 0, "and the cooldown starts");
				})
				.thenSucceed();
	}

	// ---------------------------------------------------------------- lifecycle

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "gladiator_lifecycle")
	public void thrownWeaponsComeHomeWhenTheHulkReverts(GameTestHelper helper) {
		ServerPlayer p = gladiator(helper);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, p.position().add(0, 0, 3.0));
		GladiatorAbilities.hammerHurl(p);
		helper.onEachTick(() -> Hulk.tick(p));
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertFalse(helper.getLevel().getEntitiesOfClass(GladiatorHammerEntity.class,
						p.getBoundingBox().inflate(30.0)).isEmpty(), "the hammer is out"))
				.thenExecute(() -> {
					GladiatorAbilities.axeThrow(p);
					helper.assertTrue(GladiatorGear.weaponAway(p, true) && GladiatorGear.weaponAway(p, false), "both weapons away");
					Hulk.revert(p, false);
					helper.assertFalse(GladiatorGear.weaponAway(p, true), "the axe is back when he changes back");
					helper.assertFalse(GladiatorGear.weaponAway(p, false), "and the hammer");
					helper.assertFalse(GladiatorAbilities.axeAway(p) || GladiatorAbilities.hammerAway(p), "nothing tracked as away");
				})
				.thenWaitUntil(() -> helper.assertTrue(helper.getLevel().getEntitiesOfClass(GladiatorHammerEntity.class,
						p.getBoundingBox().inflate(30.0)).isEmpty(), "the thrown hammer is gone"))
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "gladiator_lifecycle")
	public void deathOrLogoutClearsEverything(GameTestHelper helper) {
		ServerPlayer p = gladiator(helper);
		GladiatorAbilities.whirlwind(p);
		GladiatorAbilities.championsRoar(p);
		helper.assertTrue(GladiatorAbilities.whirling(p), "spinning");
		Hulk.clearTransient(p); // what death / logout / a dimension change call
		helper.assertFalse(GladiatorAbilities.whirling(p), "the spin is over");
		helper.assertFalse(GladiatorAbilities.busy(p), "nothing running");
		helper.assertFalse(GladiatorGear.weaponAway(p, true) || GladiatorGear.weaponAway(p, false), "no weapon away");
		helper.succeed();
	}
}
