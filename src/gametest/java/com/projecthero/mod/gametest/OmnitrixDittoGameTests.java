package com.projecthero.mod.gametest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.ditto.DittoCopyEntity;
import com.projecthero.mod.omnitrix.alien.ditto.DittoKit;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Ditto (user: "also create a moveset for ditto please ... Make this another alien you unlock on the 11th mistransformation.
 * When it comes to unlocking new aliens make it an even chance to unlock either cannonbolt or wildvine if you dont unlock any of
 * them"): a hidden prototype alien; his copies are real entities that swarm and punch, merge back, carry and throw; half a copy's
 * damage reaches him; every key has a move; and the hidden-alien pick -- every 11th mistransform finds one at random, evenly, never
 * twice, never an item-only one.
 */
public class OmnitrixDittoGameTests implements FabricGameTest {
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

	private static ServerPlayer ditto(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		Aliens.discover(p, DittoKit.ID, true); // found by a mistransform (its own test below)
		helper.assertTrue(Omnitrix.select(p, DittoKit.ID), "Ditto is on the prototype's dial once found");
		helper.assertTrue(Omnitrix.transform(p), "transformed into Ditto");
		helper.onEachTick(() -> Omnitrix.tick(p));
		return p;
	}

	private static DittoKit kit() {
		return (DittoKit) Aliens.kit(DittoKit.ID);
	}

	private static void tap(ServerPlayer p, AbilitySlot slot, boolean shift) {
		p.setShiftKeyDown(shift);
		AbilityRouter.handleInput(p, slot.number(), true);
		AbilityRouter.handleInput(p, slot.number(), false);
		p.setShiftKeyDown(false);
	}

	private static Zombie zombie(GameTestHelper helper, double x, double y, double z) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	/** Ditto at {@code (3.5, 2, 1.5)} looks along +z at the zombie straight ahead. */
	private static void lookAt(ServerPlayer p, Zombie z) {
		Vec3 d = z.position().add(0, z.getBbHeight() * 0.5, 0).subtract(p.getEyePosition());
		float yaw = (float) (Math.atan2(-d.x, d.z) * 180 / Math.PI);
		float pitch = (float) (-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180 / Math.PI);
		p.setYRot(yaw);
		p.setXRot(pitch);
		p.setYHeadRot(yaw);
	}

	// ---------------------------------------------------------------- the dial

	@GameTest(template = EMPTY_STRUCTURE)
	public void dittoIsAHiddenPrototypeAlien(GameTestHelper helper) {
		helper.assertTrue(Aliens.byId(DittoKit.ID) != null && !Aliens.byId(DittoKit.ID).recalibrated(), "declared on the prototype");
		helper.assertTrue(Aliens.hidden(DittoKit.ID) && Aliens.mistransformFinds().contains(Aliens.DITTO), "hidden, found by a mistransform");
		helper.assertTrue(Aliens.dial(false).contains(Aliens.DITTO) && !Aliens.dial(true).contains(Aliens.DITTO), "on the prototype's dial only");
		ServerPlayer p = mock(helper, 1.5, 2, 1.5);
		Omnitrix.grant(p);
		helper.assertFalse(Aliens.playable(p).contains(Aliens.DITTO), "not on the dial before he is found");
		helper.assertFalse(Omnitrix.select(p, DittoKit.ID), "and cannot be dialled");
		helper.assertTrue(Aliens.unfound(p).contains(Aliens.DITTO), "a mistransform can find him");
		helper.assertTrue(kit().stats().scale() < 0.6f, "a small alien");
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

	// ---------------------------------------------------------------- the hidden-alien pick

	/** v0.17.2 (user: "make it an even chance"): each unfound hidden alien comes up about as often as the others. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void theHiddenPickIsEven(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 1.5, 2, 1.5);
		Omnitrix.grant(p);
		List<Aliens.Alien> pool = Aliens.unfound(p);
		helper.assertTrue(pool.size() >= 3 && pool.contains(Aliens.CANNONBOLT) && pool.contains(Aliens.WILDVINE) && pool.contains(Aliens.DITTO), "Cannonbolt, Wildvine and Ditto all to find: " + pool);
		RandomSource r = RandomSource.create(1717L);
		Map<String, Integer> n = new HashMap<>();
		int draws = 6000;
		for (int i = 0; i < draws; i++) {
			n.merge(Aliens.pickHidden(p, r).id(), 1, Integer::sum);
		}
		double even = 1.0 / pool.size();
		for (Aliens.Alien a : pool) {
			double share = n.getOrDefault(a.id(), 0) / (double) draws;
			helper.assertTrue(Math.abs(share - even) < 0.05, a.id() + " came up " + share + ", expected about " + even);
		}
		// a found one leaves the pool
		Aliens.discover(p, DittoKit.ID, true);
		for (int i = 0; i < 200; i++) {
			helper.assertFalse(Aliens.pickHidden(p, r) == Aliens.DITTO, "a found alien is never picked again");
		}
		helper.succeed();
	}

	/**
	 * v0.17.2: the 11th mistransform finds one hidden alien, the 22nd another, the 33rd the next (as many as there are) -- never the
	 * same one twice, and no mistransform in between finds any.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 6000)
	public void every11thMistransformFindsANewHiddenAlien(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		int hidden = Aliens.unfound(p).size();
		helper.assertTrue(hidden >= 3, "Cannonbolt, Wildvine and Ditto hidden: " + hidden);
		Omnitrix.setMistransforms(p, 8); // two ordinary mistransforms, then the 11th
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
		slap(helper, p, 0, hidden, new HashSet<>());
	}

	private static void slap(GameTestHelper helper, ServerPlayer p, int n, int hidden, Set<String> found) {
		if (n >= 300) {
			helper.fail("only " + Omnitrix.mistransforms(p) + " mistransforms in 300 slaps (found " + found + ")");
			return;
		}
		if (Omnitrix.transformed(p)) {
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		}
		int before = Omnitrix.mistransforms(p);
		Omnitrix.setRecharge(p, 0);
		Omnitrix.activate(p);
		Omnitrix.finishRaise(p);
		Omnitrix.select(p, Aliens.HEATBLAST.id());
		Omnitrix.slap(p);
		helper.runAfterDelay(Omnitrix.SLAP_TICKS + 4, () -> {
			int count = Omnitrix.mistransforms(p);
			if (count == before) {
				slap(helper, p, n + 1, hidden, found); // no glitch this slap
				return;
			}
			String active = Omnitrix.state(p).active;
			Set<String> now = new HashSet<>();
			for (Aliens.Alien fa : Aliens.mistransformFinds()) {
				String id = fa.id();
				if (Aliens.found(p, id)) {
					now.add(id);
				}
			}
			if (count % 11 != 0) {
				helper.assertTrue(now.equals(found), "mistransform #" + count + " finds nothing: " + now);
				helper.assertFalse(Aliens.hidden(active) && !found.contains(active), "mistransform #" + count + " is no unfound hidden alien: " + active);
				slap(helper, p, n + 1, hidden, found);
				return;
			}
			helper.assertTrue(Aliens.hidden(active) && !found.contains(active), "mistransform #" + count + " lands on a new hidden alien, got " + active);
			helper.assertTrue(now.size() == found.size() + 1 && now.contains(active), "and finds it: " + now);
			helper.assertTrue(Omnitrix.state(p).selected.equals(Aliens.HEATBLAST.id()), "the dial stays on Heatblast");
			found.add(active);
			if (found.size() == hidden) {
				helper.assertTrue(Aliens.unfound(p).isEmpty() && count == 11 * hidden, "all " + hidden + " found by #" + count);
				helper.succeed();
				return;
			}
			Omnitrix.setMistransforms(p, count + 9); // skip ahead: two ordinary ones, then the next 11th
			slap(helper, p, n + 1, hidden, found);
		});
	}

	// ---------------------------------------------------------------- copies

	@GameTest(template = EMPTY_STRUCTURE)
	public void vSplitsCopiesUpToSixAndShiftVMergesThemBack(GameTestHelper helper) {
		ServerPlayer p = ditto(helper);
		DittoKit k = kit();
		for (int i = 0; i < 8; i++) {
			k.cooldown(p, DittoKit.SPLIT, 0);
			tap(p, AbilitySlot.SLOT_5, false);
		}
		List<DittoCopyEntity> l = DittoKit.copies(p);
		helper.assertTrue(l.size() == DittoKit.MAX_COPIES, "six copies at most: " + l.size());
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(DittoCopyEntity.class, p.getBoundingBox().inflate(8)).size() == DittoKit.MAX_COPIES,
				"real entities in the world");
		p.setHealth(p.getMaxHealth() * 0.5f);
		float hurt = p.getHealth();
		tap(p, AbilitySlot.SLOT_5, true);
		helper.assertTrue(DittoKit.copies(p).isEmpty(), "Shift+V merges them all back");
		helper.assertTrue(p.getHealth() > hurt + 5f, "and heals him: " + hurt + " -> " + p.getHealth());
		helper.runAfterDelay(1, () -> {
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(DittoCopyEntity.class, p.getBoundingBox().inflate(8)).isEmpty(), "gone from the world");
			helper.succeed();
		});
	}

	/** v0.17.2 (the show: damage to one is felt by all): half what a copy takes reaches Ditto; a copy breaks at 6. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void damageToACopyIsFeltByDitto(GameTestHelper helper) {
		ServerPlayer p = ditto(helper);
		tap(p, AbilitySlot.SLOT_5, false);
		DittoCopyEntity c = DittoKit.copies(p).get(0);
		Zombie z = zombie(helper, 6.5, 2, 6.5);
		float before = p.getHealth();
		helper.assertTrue(c.hurt(helper.getLevel().damageSources().mobAttack(z), 4f), "the copy takes the hit");
		helper.assertTrue(c.hp() == DittoCopyEntity.MAX_HP - 4f, "4 off the copy: " + c.hp());
		float felt = before - p.getHealth();
		helper.assertTrue(felt > 1.0f && felt <= 2.01f, "about half of it reaches Ditto: " + felt);
		helper.assertFalse(c.hurt(p.damageSources().playerAttack(p), 4f), "his own fists never hurt a copy");
		float mid = p.getHealth();
		c.hurt(helper.getLevel().damageSources().mobAttack(z), 20f);
		helper.assertTrue(c.isRemoved(), "a copy breaks at 6");
		helper.assertTrue(mid - p.getHealth() <= 1.01f, "he feels only what the copy had left (2 -> 1): " + (mid - p.getHealth()));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void swarmStrikeSendsTheCopiesToPunchIt(GameTestHelper helper) {
		ServerPlayer p = ditto(helper);
		Zombie z = zombie(helper, 3.5, 2, 5.5);
		lookAt(p, z);
		float before = z.getHealth();
		tap(p, AbilitySlot.SLOT_1, false);
		List<DittoCopyEntity> l = DittoKit.copies(p);
		helper.assertTrue(l.size() >= 2, "with no copies, two split off first");
		helper.assertTrue(l.stream().allMatch(c -> c.target() == z), "every copy goes for the zombie");
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(z.getHealth() < before - 2f, "the copies punch it: " + before + " -> " + z.getHealth());
			helper.assertTrue(DittoKit.copies(p).stream().allMatch(c -> c.distanceToSqr(z) < 4 * 4), "the pack is on it");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void pinwheelKickAndDogpileHit(GameTestHelper helper) {
		ServerPlayer p = ditto(helper);
		Zombie z = zombie(helper, 3.5, 2, 3.3);
		lookAt(p, z);
		float before = z.getHealth();
		tap(p, AbilitySlot.SLOT_2, false);
		helper.assertTrue(z.getHealth() < before, "the Pinwheel Kick hits it: " + z.getHealth());
		helper.assertTrue(kit().cooldownLeft(p, DittoKit.KICK) > 0, "G cools down");
		float mid = z.getHealth();
		z.invulnerableTime = 0;
		tap(p, AbilitySlot.SLOT_1, true);
		helper.assertTrue(z.getHealth() < mid, "the Dogpile hits it: " + z.getHealth());
		helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and pins it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void copyCarryLiftsAndThrows(GameTestHelper helper) {
		ServerPlayer p = ditto(helper);
		Zombie z = zombie(helper, 3.5, 2, 4.5);
		lookAt(p, z);
		tap(p, AbilitySlot.SLOT_6, true);
		helper.assertTrue(kit().cooldownLeft(p, DittoKit.CARRY) == 0, "no carry without two copies");
		tap(p, AbilitySlot.SLOT_5, false);
		kit().cooldown(p, DittoKit.SPLIT, 0);
		tap(p, AbilitySlot.SLOT_5, false);
		lookAt(p, z);
		double y0 = z.getY();
		float before = z.getHealth();
		tap(p, AbilitySlot.SLOT_6, true);
		helper.assertTrue(DittoKit.carrying(p), "two copies grab it");
		helper.runAfterDelay(14, () -> {
			helper.assertTrue(z.getY() > y0 + 0.9, "lifted over their heads: " + (z.getY() - y0));
			helper.assertTrue(DittoKit.copies(p).stream().filter(DittoCopyEntity::holding).count() == 2, "two copies hold it up");
			helper.runAfterDelay(10, () -> {
				helper.assertFalse(DittoKit.carrying(p), "and thrown");
				helper.assertTrue(z.getHealth() < before, "the throw hurts: " + z.getHealth());
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void leapTossAndArmy(GameTestHelper helper) {
		ServerPlayer p = ditto(helper);
		DittoKit k = kit();
		tap(p, AbilitySlot.SLOT_3, true);
		helper.assertTrue(k.cooldownLeft(p, DittoKit.TOSS) == 0, "no Copy Toss without a copy near");
		tap(p, AbilitySlot.SLOT_3, false);
		helper.assertTrue(p.getDeltaMovement().y > 0.5 && k.flag(p, DittoKit.F_LEAP), "X leaps, no fall damage");
		helper.assertFalse(k.allowDamage(p, helper.getLevel().damageSources().fall(), 6f), "a leap's fall is free");
		tap(p, AbilitySlot.SLOT_6, false);
		helper.assertTrue(k.flag(p, DittoKit.F_ARMY) && DittoKit.copies(p).size() == DittoKit.ARMY_COPIES, "C: a dozen copies at once");
		tap(p, AbilitySlot.SLOT_3, true);
		helper.assertTrue(k.cooldownLeft(p, DittoKit.TOSS) > 0, "Shift+X: a copy throws him");
		k.setTimer(p, DittoKit.ARMY_UNTIL, 1);
		Omnitrix.tick(p);
		helper.assertTrue(!k.flag(p, DittoKit.F_ARMY) && DittoKit.copies(p).size() <= DittoKit.MAX_COPIES, "the army ends: back to six");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertTrue(DittoKit.copies(p).isEmpty(), "turning back pops every copy");
		helper.succeed();
	}
}
