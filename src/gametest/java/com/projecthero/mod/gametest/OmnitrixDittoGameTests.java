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

	private static void apart(GameTestHelper helper, List<DittoCopyEntity> l, double gap, String when) {
		for (int i = 0; i < l.size(); i++) {
			for (int j = i + 1; j < l.size(); j++) {
				// a copy that stepped off the test's small floor is below the others, not inside one
				double d = Math.sqrt(l.get(i).position().subtract(l.get(j).position()).horizontalDistanceSqr());
				helper.assertTrue(d >= gap || Math.abs(l.get(i).getY() - l.get(j).getY()) > 0.9, when + ": copies " + i + " and " + j + " only " + d + " apart");
			}
		}
	}

	private static void splitAll(ServerPlayer p, int n) {
		for (int i = 0; i < n; i++) {
			kit().cooldown(p, DittoKit.SPLIT, 0);
			tap(p, AbilitySlot.SLOT_5, false);
		}
	}

	/** v0.17.2 (user: "make the limit to dittos clones 10 please and give them their own position"). */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void vSplitsCopiesUpToTenAndEachHasItsOwnSpot(GameTestHelper helper) {
		ServerPlayer p = ditto(helper);
		Vec3 open = helper.absoluteVec(new Vec3(3.5, 2, 4.5)); // clear of the test's structure block, so the whole fan has room
		p.moveTo(open.x, open.y, open.z, 0f, 0f);
		splitAll(p, 12);
		List<DittoCopyEntity> l = DittoKit.copies(p);
		helper.assertTrue(l.size() == 10 && DittoKit.MAX_COPIES == 10, "ten copies at most: " + l.size());
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(DittoCopyEntity.class, p.getBoundingBox().inflate(8), c -> c.ownerId() == p.getId()).size() == 10, "real entities in the world");
		helper.assertTrue(l.stream().allMatch(c -> c.state() == DittoCopyEntity.EMERGING), "each starts by pulling itself out of him");
		helper.runAfterDelay(DittoCopyEntity.EMERGE_TICKS + 40, () -> {
			helper.assertTrue(l.stream().allMatch(c -> c.state() == DittoCopyEntity.NORMAL), "all out");
			apart(helper, l, 0.6, "following him");
			Zombie z = zombie(helper, 3.5, 2, 7.5);
			lookAt(p, z);
			tap(p, AbilitySlot.SLOT_1, false);
			helper.runAfterDelay(40, () -> {
				apart(helper, l, 0.55, "round a creature");
				helper.succeed();
			});
		});
	}

	/** v0.17.2 (user: "make the clones almost stretch and pull themselves out of the player and separate from the player"). */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void aCopyPullsItselfOutOfHimThenWalksToItsSpot(GameTestHelper helper) {
		ServerPlayer p = ditto(helper);
		tap(p, AbilitySlot.SLOT_5, false);
		DittoCopyEntity c = DittoKit.copies(p).get(0);
		helper.assertTrue(c.position().distanceTo(p.position()) < 0.01, "it starts inside him");
		helper.assertFalse(c.hurt(helper.getLevel().damageSources().magic(), 1f), "nothing can hit it as it comes out");
		helper.runAfterDelay(DittoCopyEntity.EMERGE_TICKS / 2, () -> {
			double half = c.position().distanceTo(p.position());
			helper.assertTrue(half > 0.15 && half < 1.2, "part way out: " + half);
			helper.runAfterDelay(DittoCopyEntity.EMERGE_TICKS / 2 + 1, () -> {
				helper.assertTrue(c.state() == DittoCopyEntity.NORMAL, "out");
				helper.assertTrue(c.hurt(helper.getLevel().damageSources().magic(), 1f), "and an ordinary copy now");
				helper.succeed();
			});
		});
	}

	/** v0.17.2 (user: "they do so one at a time only and then walk back into the player and merge with them ... give it a 0.5 second cooldown"). */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void shiftVWalksOneCopyBackAtATime(GameTestHelper helper) {
		ServerPlayer p = ditto(helper);
		DittoKit k = kit();
		splitAll(p, 3);
		helper.runAfterDelay(DittoCopyEntity.EMERGE_TICKS + 30, () -> {
			p.setHealth(p.getMaxHealth() * 0.5f);
			float hurt = p.getHealth();
			tap(p, AbilitySlot.SLOT_5, true);
			helper.assertTrue(DittoKit.copies(p).size() == 3, "none vanishes at once");
			helper.assertTrue(DittoKit.copies(p).stream().filter(DittoCopyEntity::returning).count() == 1, "one walks back");
			helper.assertTrue(p.getHealth() == hurt, "no health until it is in");
			tap(p, AbilitySlot.SLOT_5, true);
			helper.assertTrue(DittoKit.copies(p).stream().filter(DittoCopyEntity::returning).count() == 1, "only one at a time");
			helper.assertTrue(k.keyActive(p, AbilitySlot.SLOT_5), "the key is lit while it walks");
			helper.succeedWhen(() -> {
				helper.assertTrue(DittoKit.copies(p).size() == 2, "the copy is back in him");
				helper.assertTrue(Math.abs(p.getHealth() - (hurt + DittoKit.MERGE_HEAL)) < 0.01f, "+2 health: " + p.getHealth());
				helper.assertTrue(k.cooldownLeft(p, DittoKit.MERGE) > 0 && k.cooldownLeft(p, DittoKit.MERGE) <= DittoKit.MERGE_COOLDOWN, "0.5 s from when it is in");
				helper.assertTrue(DittoKit.MERGE_COOLDOWN == 10, "half a second");
			});
		});
	}

	/** Shift+V held: the next one starts when the last is in. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void holdingShiftVCallsThemAllIn(GameTestHelper helper) {
		ServerPlayer p = ditto(helper);
		splitAll(p, 2);
		helper.runAfterDelay(DittoCopyEntity.EMERGE_TICKS + 30, () -> {
			p.setShiftKeyDown(true);
			AbilityRouter.handleInput(p, AbilitySlot.SLOT_5.number(), true);
			p.setShiftKeyDown(false);
			helper.succeedWhen(() -> helper.assertTrue(DittoKit.copies(p).isEmpty(), "all back in"));
		});
	}

	/** v0.17.2 (the show: damage to one is felt by all): half what a copy takes reaches Ditto; a copy breaks at 6. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void damageToACopyIsFeltByDitto(GameTestHelper helper) {
		ServerPlayer p = ditto(helper);
		tap(p, AbilitySlot.SLOT_5, false);
		DittoCopyEntity c = DittoKit.copies(p).get(0);
		Zombie z = zombie(helper, 6.5, 2, 6.5);
		helper.runAfterDelay(DittoCopyEntity.EMERGE_TICKS + 1, () -> {
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
		});
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
		helper.assertTrue(z.getHealth() == mid, "the pack is still in the air");
		helper.runAfterDelay(DittoKit.DOGPILE_LEAP + 2, () -> {
			helper.assertTrue(z.getHealth() < mid, "the Dogpile hits it: " + z.getHealth());
			helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and pins it");
			helper.assertTrue(DittoKit.copies(p).stream().anyMatch(c -> c.state() == DittoCopyEntity.CLINGING), "and they hang on");
			helper.succeed();
		});
	}

	/** v0.17.2 (user: "replace copy carry with an ability where the player throws a clone and swaps place with it when it lands please"). */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void switcherooThrowsACopyAndSwapsPlacesWhereItLands(GameTestHelper helper) {
		ServerPlayer p = ditto(helper);
		p.setXRot(-20f);
		Vec3 from = p.position();
		tap(p, AbilitySlot.SLOT_6, true);
		helper.assertTrue(kit().cooldownLeft(p, DittoKit.SWITCHEROO) > 0, "Shift+C throws");
		helper.assertTrue(DittoKit.copies(p).size() == 1, "with no copy, one comes out for it");
		DittoCopyEntity c = DittoKit.copies(p).get(0);
		helper.assertTrue(c.state() == DittoCopyEntity.HOISTED, "up over his head first");
		helper.assertTrue(kit().keyActive(p, AbilitySlot.SLOT_6), "the key is lit while it flies");
		helper.runAfterDelay(DittoCopyEntity.HOIST_TICKS + 1, () -> helper.assertTrue(c.state() == DittoCopyEntity.FLYING, "thrown"));
		helper.succeedWhen(() -> {
			helper.assertFalse(kit().keyActive(p, AbilitySlot.SLOT_6), "it landed");
			helper.assertTrue(p.position().distanceTo(from) > 3.0, "he is where it landed: " + p.position().distanceTo(from));
			helper.assertTrue(c.position().distanceTo(from) < 1.5, "and it is where he stood: " + c.position().distanceTo(from));
		});
	}

	/** v0.17.2 (user: "make ditto army Shift+Z and shell game the C move please"). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void armyIsShiftZAndShellGameIsC(GameTestHelper helper) {
		DittoKit k = kit();
		helper.assertTrue(DittoKit.ARMY.equals(k.moveOn(AbilitySlot.SLOT_4, true, false).id()), "Ditto Army on Shift+Z");
		helper.assertTrue(DittoKit.SHUFFLE.equals(k.moveOn(AbilitySlot.SLOT_6, false, false).id()), "Shell Game on C");
		helper.assertTrue(DittoKit.SWITCHEROO.equals(k.moveOn(AbilitySlot.SLOT_6, true, false).id()), "Switcheroo on Shift+C");
		helper.assertTrue(k.move("copy_carry") == null, "Copy Carry is gone");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void shellGameSwapsWithACopy(GameTestHelper helper) {
		ServerPlayer p = ditto(helper);
		tap(p, AbilitySlot.SLOT_5, false);
		helper.runAfterDelay(DittoCopyEntity.EMERGE_TICKS + 30, () -> {
			Vec3 me = p.position();
			DittoCopyEntity c = DittoKit.copies(p).get(0);
			Vec3 it = c.position();
			tap(p, AbilitySlot.SLOT_6, false);
			helper.assertTrue(p.position().distanceTo(it) < 0.01, "he is where it was");
			helper.assertTrue(p.hasEffect(MobEffects.INVISIBILITY), "unseen");
			helper.runAfterDelay(DittoKit.SHUFFLE_HOP, () -> {
				helper.assertTrue(c.position().distanceTo(me) < 1.0, "and it hopped to where he was: " + c.position().distanceTo(me));
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void leapTossAndArmy(GameTestHelper helper) {
		ServerPlayer p = ditto(helper);
		DittoKit k = kit();
		tap(p, AbilitySlot.SLOT_3, true);
		helper.assertTrue(k.cooldownLeft(p, DittoKit.TOSS) == 0, "no Copy Toss without a copy near");
		tap(p, AbilitySlot.SLOT_3, false);
		helper.assertTrue(p.getDeltaMovement().y > 0.5 && k.flag(p, DittoKit.F_LEAP), "X leaps, no fall damage");
		helper.assertFalse(k.allowDamage(p, helper.getLevel().damageSources().fall(), 6f), "a leap's fall is free");
		tap(p, AbilitySlot.SLOT_4, true);
		helper.assertTrue(k.flag(p, DittoKit.F_ARMY) && DittoKit.copies(p).size() == 10, "Shift+Z: all ten at once");
		helper.assertTrue(DittoKit.frenzied(p), "and they punch twice as fast");
		helper.runAfterDelay(DittoCopyEntity.EMERGE_FAST + 10 * DittoKit.ARMY_STAGGER + 2, () -> {
			tap(p, AbilitySlot.SLOT_3, true);
			helper.assertTrue(k.cooldownLeft(p, DittoKit.TOSS) > 0, "Shift+X: a copy throws him");
			k.setTimer(p, DittoKit.ARMY_UNTIL, 1);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(!k.flag(p, DittoKit.F_ARMY) && DittoKit.copies(p).size() == 10, "the army ends, the copies stay");
				Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
				helper.assertTrue(DittoKit.copies(p).isEmpty(), "turning back pops every copy");
				helper.succeed();
			});
		});
	}
}
