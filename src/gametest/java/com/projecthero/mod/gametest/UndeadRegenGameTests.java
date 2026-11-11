package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadAbilities;
import com.projecthero.mod.undead.UndeadAbilityManager;
import com.projecthero.mod.undead.UndeadConfig;
import com.projecthero.mod.undead.UndeadMod;
import com.projecthero.mod.undead.UndeadSoundEvents;
import com.projecthero.mod.undead.UndeadStages;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadPartEntity;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Undead core regen rework: the health regen (1 HP every 5 ticks), the 1.5 s wait before a lost part starts to regrow,
 * the growth order (a child never before its parent has grown out to it), the Regen Menu / Toggle Regen gone, a missing hand
 * holding nothing, the left-behind pieces' stand / fall / dissolve life, and the fizz replacing the regrowth sounds. Mock players
 * are not ticked by the server, so tests over time drive {@link Undead#tick}.
 */
public class UndeadRegenGameTests implements FabricGameTest {
	private static ServerPlayer undead(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		p.setYHeadRot(0f);
		p.yBodyRot = 0f;
		Undead.grant(p);
		return p;
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<UndeadState> fn) {
		UndeadState n = Undead.state(p).copy();
		fn.accept(n);
		p.setAttached(ModAttachments.UNDEAD_STATE, n);
	}

	/** User: "Players should regenerate 1 hp every 5 ticks with this ability" -- always, a fresh hit doesn't pause it, and it is free. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void healthComesBackOneEveryFiveTicks(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		helper.assertTrue(UndeadConfig.HEAL_INTERVAL == 5 && UndeadConfig.HEAL_AMOUNT == 1f, "1 HP every 5 ticks");
		p.setHealth(4f);
		p.getFoodData().setFoodLevel(20);
		p.getFoodData().setSaturation(0f);
		int[] t = { 0 };
		helper.onEachTick(() -> {
			edit(p, s -> s.lastHitAt = helper.getLevel().getGameTime()); // hit every tick: still heals
			Undead.tick(p);
			t[0]++;
			if (t[0] == 20) {
				helper.assertTrue(Math.abs(p.getHealth() - 8f) <= 1.01f, "about 4 HP in a second: " + p.getHealth());
				helper.assertTrue(p.getFoodData().getFoodLevel() == 20 && p.getFoodData().getExhaustionLevel() < 0.01f, "free");
				helper.succeed();
			}
		});
	}

	/** User: "make the delay before a lost body part starts to regenerate 1,5 seconds"; then it grows over its frames. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
	public void aLostPartWaitsASecondAndAHalf(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		helper.assertTrue(UndeadConfig.REGROW_DELAY == 30, "30 ticks");
		helper.assertTrue(Undead.regrowDelay(p) == 30 && Undead.regrowTicks(p, UndeadState.LEFT_ARM) == UndeadConfig.LIMB_SWEEP_TICKS,
				"no slow-downs: the plain numbers");
		Undead.breakPart(p, UndeadState.LEFT_LEG, UndeadState.KIND_BURST);
		long broke = Undead.state(p).brokenAt(UndeadState.LEFT_LEG);
		helper.onEachTick(() -> {
			Undead.tick(p);
			UndeadState s = Undead.state(p);
			long now = helper.getLevel().getGameTime();
			if (now < broke + UndeadConfig.REGROW_DELAY) {
				helper.assertTrue(s.regrowStart(UndeadState.LEFT_LEG) == 0L, "still waiting at " + (now - broke));
				return;
			}
			if (now == broke + UndeadConfig.REGROW_DELAY) {
				helper.assertTrue(s.regrowStart(UndeadState.LEFT_LEG) == broke + UndeadConfig.REGROW_DELAY, "starts at 1.5 s");
				helper.assertTrue(s.regrowEnd(UndeadState.LEFT_LEG) - s.regrowStart(UndeadState.LEFT_LEG) == UndeadConfig.LIMB_SWEEP_TICKS,
						"a leg (with its foot) takes 1 s");
				helper.assertTrue(s.regrowStart(UndeadState.LEFT_FOOT) == s.regrowStart(UndeadState.LEFT_LEG), "the foot starts with the leg");
			}
			if (!s.broken(UndeadState.LEFT_LEG) && !s.broken(UndeadState.LEFT_FOOT)) {
				// v0.17.3 one sweep: leg and foot together, 30 + 20 (the delay, then exactly one second)
				helper.assertTrue(now - broke == UndeadConfig.REGROW_DELAY + UndeadConfig.LIMB_SWEEP_TICKS, "whole 2.5 s after it went: " + (now - broke));
				helper.succeed();
			}
		});
	}

	/**
	 * User: "sometimes the hand will start regrowing at the same time as the arm so it looks like the hand is materializing midair
	 * ... this applies to the rest of the body aswell". From just a head, every segment starts only once the one it grows from has
	 * grown out to it (its bone front at the joint), checked on every tick; the whole body still comes back.
	 */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void noSegmentStartsBeforeItsParentReachesIt(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		// the thresholds are the parent's last bone frame (its bone front at the far end): the hand at the wrist, the foot at the
		// ankle, the legs at the hips
		helper.assertTrue(UndeadConfig.HAND_NEEDS_ARM == UndeadConfig.FLESH_FRAME[UndeadState.RIGHT_ARM] - 1, "a hand waits for its arm's bone to reach the wrist");
		helper.assertTrue(UndeadConfig.FOOT_NEEDS_LEG == UndeadConfig.FLESH_FRAME[UndeadState.RIGHT_LEG] - 1, "a foot waits for its leg's bone to reach the ankle");
		helper.assertTrue(UndeadConfig.LEG_NEEDS_BODY == UndeadConfig.FLESH_FRAME[UndeadState.BODY] - 1, "the legs wait for the body to reach the hips");
		Undead.shatter(p);
		long shattered = helper.getLevel().getGameTime();
		java.util.Set<Integer> stages = new java.util.LinkedHashSet<>();
		boolean[] started = new boolean[UndeadState.SEGS];
		helper.onEachTick(() -> {
			Undead.tick(p);
			UndeadState s = Undead.state(p);
			long now = helper.getLevel().getGameTime();
			stages.add(UndeadStages.stage(s, now));
			for (int i = UndeadState.BODY; i < UndeadState.SEGS; i++) {
				if (started[i] || !s.broken(i) || s.regrowStart(i) <= 0L) {
					continue;
				}
				started[i] = true;
				long st = s.regrowStart(i);
				int parent = UndeadState.parentOf(i);
				float pf = s.frame(parent, st);
				helper.assertTrue(st >= s.brokenAt(i) + UndeadConfig.REGROW_DELAY, "segment " + i + " waited the 1.5 s");
				// (v0.17.3: a hand / foot starts WITH its arm / leg, as one sweep -- drawn as the last stretch of the limb's front)
				helper.assertTrue(!s.broken(parent) || pf >= Undead.needs(i) - 0.001f || i >= UndeadState.PARTS && s.sweeping(parent),
						"segment " + i + " started with its parent at frame " + pf + " (needs " + Undead.needs(i) + ")");
				helper.assertTrue(Undead.mayShow(s, i, st), "and may be drawn from its first frame");
			}
			// never drawn ahead: a growing child always has its parent far enough
			for (int i = UndeadState.BODY; i < UndeadState.SEGS; i++) {
				if (s.broken(i) && s.frame(i, now) > 0f) {
					helper.assertTrue(Undead.mayShow(s, i, now), "segment " + i + " showing ahead of its parent");
				}
			}
			if (now == shattered + UndeadConfig.REGROW_DELAY - 1) {
				helper.assertTrue(s.regrowStart(UndeadState.BODY) == 0L, "the body waits 1.5 s");
			}
			if (!s.anyBroken()) {
				for (int i = UndeadState.BODY; i < UndeadState.SEGS; i++) {
					helper.assertTrue(started[i], "segment " + i + " grew back");
				}
				helper.assertTrue(stages.contains(UndeadStages.CRAWL), "he lay on the growing torso: " + stages);
				helper.assertTrue(UndeadStages.stage(s, now) == UndeadStages.NORMAL, "and walks");
				helper.succeed();
			}
		});
	}

	/** A hand lost on its own (its arm whole) waits only its own delay. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void aHandAloneWaitsOnlyItsDelay(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Undead.breakPart(p, UndeadState.RIGHT_HAND, UndeadState.KIND_BURST);
		long broke = Undead.state(p).brokenAt(UndeadState.RIGHT_HAND);
		helper.onEachTick(() -> {
			Undead.tick(p);
			if (helper.getLevel().getGameTime() == broke + UndeadConfig.REGROW_DELAY + 1) {
				helper.assertTrue(Undead.state(p).regrowStart(UndeadState.RIGHT_HAND) == broke + UndeadConfig.REGROW_DELAY, "the arm is whole: straight after the delay");
				helper.succeed();
			}
		});
	}

	/**
	 * User: "the arm regenerates and then only after that the hand regenerates, it doesnt regenerate all at once, please fix that"
	 * and "make it take 1 full second to regenerate a limb completely": a lost arm + hand (and leg + foot) start together, grow as
	 * one sweep (the same progress every tick, 1/20 a tick) and are whole together exactly 20 ticks after the start.
	 */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void aLostLimbAndItsEndRegrowAsOneSweepInOneSecond(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		helper.assertTrue(UndeadConfig.LIMB_SWEEP_TICKS == 20, "one second");
		Undead.breakPart(p, UndeadState.RIGHT_ARM, UndeadState.KIND_BURST);
		Undead.breakPart(p, UndeadState.LEFT_LEG, UndeadState.KIND_BURST);
		UndeadState b = Undead.state(p);
		helper.assertTrue(b.broken(UndeadState.RIGHT_HAND) && b.broken(UndeadState.LEFT_FOOT), "the hand and foot go with them");
		int[] limbs = { UndeadState.RIGHT_ARM, UndeadState.LEFT_LEG };
		long[] start = { 0L };
		float[] last = new float[2];
		helper.onEachTick(() -> {
			Undead.tick(p);
			UndeadState s = Undead.state(p);
			long now = helper.getLevel().getGameTime();
			if (start[0] == 0L) {
				if (s.regrowStart(UndeadState.RIGHT_ARM) <= 0L) {
					helper.assertTrue(s.regrowStart(UndeadState.RIGHT_HAND) <= 0L, "the hand never starts before its arm");
					return;
				}
				start[0] = s.regrowStart(UndeadState.RIGHT_ARM);
				for (int limb : limbs) {
					int end = UndeadState.extremity(limb);
					helper.assertTrue(s.regrowStart(end) == s.regrowStart(limb), "segment " + end + " starts with its limb, not after it");
					helper.assertTrue(s.regrowEnd(end) == s.regrowEnd(limb) && s.regrowEnd(limb) - s.regrowStart(limb) == UndeadConfig.LIMB_SWEEP_TICKS,
							"and both end together, 20 ticks on");
					helper.assertTrue(s.sweeping(limb) && Undead.mayShow(s, end, now), "one sweep, drawn with its limb");
				}
			}
			long age = now - start[0];
			if (age < UndeadConfig.LIMB_SWEEP_TICKS) {
				for (int k = 0; k < limbs.length; k++) {
					int limb = limbs[k], end = UndeadState.extremity(limb);
					float pl = s.regrowth(limb, now), pe = s.regrowth(end, now);
					helper.assertTrue(Math.abs(pl - pe) < 1e-4f, "the hand / foot is the same growth as its limb: " + pl + " vs " + pe);
					if (age > 0) {
						helper.assertTrue(Math.abs(pl - last[k] - 1f / UndeadConfig.LIMB_SWEEP_TICKS) < 1e-4f, "1/20 of the limb a tick: " + (pl - last[k]));
					}
					last[k] = pl;
					helper.assertTrue(s.broken(limb) && s.broken(end), "still growing at " + age);
				}
				return;
			}
			helper.assertTrue(age == UndeadConfig.LIMB_SWEEP_TICKS, "whole on the 20th tick: " + age);
			for (int limb : limbs) {
				helper.assertTrue(!s.broken(limb) && !s.broken(UndeadState.extremity(limb)), "segment " + limb + " and its end whole together");
			}
			helper.succeed();
		});
	}

	/** A hand lost on its own still grows back on its own (its own, shorter time; no sweep). */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void aHandAloneStillGrowsAlone(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Undead.breakPart(p, UndeadState.LEFT_HAND, UndeadState.KIND_BURST);
		helper.onEachTick(() -> {
			Undead.tick(p);
			UndeadState s = Undead.state(p);
			if (s.regrowStart(UndeadState.LEFT_HAND) > 0L) {
				helper.assertTrue(!s.broken(UndeadState.LEFT_ARM) && !s.sweeping(UndeadState.LEFT_ARM), "no sweep: the arm is whole");
				helper.assertTrue(s.regrowEnd(UndeadState.LEFT_HAND) - s.regrowStart(UndeadState.LEFT_HAND) == UndeadConfig.REGROW_TICKS[UndeadState.LEFT_HAND]
						&& UndeadConfig.REGROW_TICKS[UndeadState.LEFT_HAND] < UndeadConfig.LIMB_SWEEP_TICKS, "its own, shorter time");
				helper.succeed();
			}
		});
	}

	/** User: "remove the regen menu from this power please": Shift+V toggles nothing, regrowth is always on, an old save's bits clear. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void theRegenMenuAndToggleRegenAreGone(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		// v0.17.3 merge: V is Reform and Shift+V is now the Rib Cage Guard (the kit), not Toggle Regen / the Regen Menu
		helper.assertTrue(com.projecthero.mod.undead.UndeadRibShot.ID.equals(UndeadAbilityManager.idsOf(p, AbilitySlot.SLOT_5)[0])
				&& com.projecthero.mod.undead.UndeadRibGuard.ID.equals(UndeadAbilityManager.idsOf(p, AbilitySlot.SLOT_5)[1]), "V: Rib Shotgun; Shift+V: Rib Cage Guard");
		// an old save with Toggle Regen off and the menu open: both bits dropped on load
		UndeadState s0 = Undead.state(p).copy();
		s0.flags = 128 | 512 | UndeadState.FLAG_NEXT_OFF;
		net.minecraft.nbt.Tag tag = UndeadState.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, s0).getOrThrow();
		net.minecraft.nbt.CompoundTag old = ((net.minecraft.nbt.CompoundTag) tag).copy();
		old.putInt("flags", 128 | 512 | UndeadState.FLAG_NEXT_OFF);
		net.minecraft.nbt.ListTag six = new net.minecraft.nbt.ListTag();
		for (int i = 0; i < 18; i++) {
			six.add(net.minecraft.nbt.LongTag.valueOf(0L));
		}
		old.put("part_times", six); // a save from before the hands and feet were segments
		UndeadState back = UndeadState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, old).getOrThrow();
		helper.assertTrue(back.flags == UndeadState.FLAG_NEXT_OFF, "the old regen bits are cleared, the rest kept: " + back.flags);
		helper.assertTrue(back.hasPower && back.partTimes.size() == UndeadState.SEGS * 3, "an old save still loads, the new segments whole");
		// and a lost part simply grows back
		Undead.breakPart(p, UndeadState.LEFT_ARM, UndeadState.KIND_BURST);
		helper.onEachTick(() -> {
			Undead.tick(p);
			if (!Undead.state(p).anyBroken()) {
				helper.succeed();
			}
		});
	}

	/** User: "players shouldn't be able to hold items while not having an arm for the respective arm". */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aMissingHandHoldsNothing(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.setMainArm(HumanoidArm.RIGHT);
		ItemStack bread = new ItemStack(Items.BREAD, 3);
		ItemStack shield = new ItemStack(Items.SHIELD);
		p.setItemInHand(InteractionHand.MAIN_HAND, bread);
		p.setItemInHand(InteractionHand.OFF_HAND, shield);
		p.getFoodData().setFoodLevel(10);
		helper.assertTrue(!UndeadMod.handGone(p, InteractionHand.MAIN_HAND) && !UndeadMod.handGone(p, InteractionHand.OFF_HAND), "whole: both hands work");
		// the off (left) hand alone gone: the offhand can't be used, the main hand can
		Undead.breakPart(p, UndeadState.LEFT_HAND, UndeadState.KIND_BURST);
		helper.assertTrue(UndeadMod.handGone(p, InteractionHand.OFF_HAND) && !UndeadMod.handGone(p, InteractionHand.MAIN_HAND), "the hand alone counts");
		helper.assertTrue(UseItemCallback.EVENT.invoker().interact(p, helper.getLevel(), InteractionHand.OFF_HAND).getResult() == InteractionResult.FAIL,
				"no raising the shield without the hand");
		helper.assertTrue(UseItemCallback.EVENT.invoker().interact(p, helper.getLevel(), InteractionHand.MAIN_HAND).getResult() != InteractionResult.FAIL,
				"the main hand still eats");
		Undead.restore(p);
		// raising the shield, then the arm comes off: it stops
		p.startUsingItem(InteractionHand.OFF_HAND);
		helper.assertTrue(p.isUsingItem(), "the shield is up");
		Undead.breakPart(p, UndeadState.LEFT_ARM, UndeadState.KIND_BURST);
		Undead.tick(p);
		helper.assertTrue(!p.isUsingItem(), "the arm went: the shield drops");
		// the main (right) arm gone: no using, no hitting
		Undead.breakPart(p, UndeadState.RIGHT_ARM, UndeadState.KIND_BURST);
		helper.assertTrue(UndeadMod.handGone(p, InteractionHand.MAIN_HAND), "no main hand");
		helper.assertTrue(UseItemCallback.EVENT.invoker().interact(p, helper.getLevel(), InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL,
				"no eating with no hand");
		helper.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND) == bread && bread.getCount() == 3
				&& p.getItemInHand(InteractionHand.OFF_HAND) == shield, "the items stay in their slots, nothing lost");
		// a left-handed player: the main hand is the left one
		Undead.restore(p);
		p.setMainArm(HumanoidArm.LEFT);
		Undead.breakPart(p, UndeadState.LEFT_ARM, UndeadState.KIND_BURST);
		helper.assertTrue(UndeadMod.handGone(p, InteractionHand.MAIN_HAND) && !UndeadMod.handGone(p, InteractionHand.OFF_HAND), "left-handed: the main hand is the left");
		p.setMainArm(HumanoidArm.RIGHT);
		helper.succeed();
	}

	/**
	 * User: "When player ejects a body part make the old limbs stay in place, 0.5 seconds later the limb falls over, 0.5 seconds
	 * later the part dissolves quickly over the course of 1 second". leaveBehind: still for 10 ticks (even in the air), then down
	 * to the ground, gone at 40 -- and the part regrowing on his body doesn't cut it short.
	 */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void aLeftBehindPieceStandsFallsAndDissolves(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		helper.assertTrue(UndeadPartEntity.LEFT_STAND == 10 && UndeadPartEntity.LEFT_FALL == 10 && UndeadPartEntity.LEFT_DISSOLVE == 20, "0.5 s, 0.5 s, 1 s");
		Vec3 at = p.position().add(0, 2.0, 0); // came off in the air
		Undead.breakPart(p, UndeadState.RIGHT_ARM, UndeadState.KIND_LAUNCHED);
		UndeadPartEntity e = UndeadPartEntity.leaveBehind(p, UndeadState.RIGHT_ARM, 6f, 12f, at);
		helper.assertTrue(e.isLeftBehind() && e.bodyPart() == UndeadState.RIGHT_ARM, "a left-behind arm piece");
		helper.assertTrue(e.fromRow() == 6f && e.toRow() == 12f, "half the forearm and the hand");
		UndeadPartEntity whole = UndeadPartEntity.leaveBehind(p, UndeadState.HEAD, 0f, 99f, p.position());
		helper.assertTrue(whole.toRow() == 8f, "the head's rows clamp to 8");
		whole.discard();
		java.util.function.BooleanSupplier alive = () -> !e.isRemoved() && helper.getLevel().getEntity(e.getId()) == e;
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(alive.getAsBoolean() && e.position().distanceTo(at) < 0.01, "standing still where it came off");
			Undead.restore(p); // his arm is whole again
		});
		helper.runAfterDelay(26, () -> {
			helper.assertTrue(alive.getAsBoolean(), "the regrown arm doesn't cut it short");
			helper.assertTrue(e.getY() < at.y - 1.0, "it fell to the ground: " + (at.y - e.getY()));
			helper.assertTrue(UndeadPartEntity.dissolveProgress(26) > 0f && UndeadPartEntity.fallProgress(26) >= 1f, "lying there, dissolving");
		});
		helper.runAfterDelay(UndeadPartEntity.LEFT_LIFE + 3, () -> {
			helper.assertTrue(!alive.getAsBoolean(), "gone after 2 s");
			helper.succeed();
		});
	}

	/** User: "make the regeneration sound effects the steam fizz sound": no undead.regrow.* events or files are left. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theRegrowthSoundsAreTheFizz(GameTestHelper helper) {
		for (var ev : UndeadSoundEvents.ALL) {
			helper.assertTrue(!ev.getLocation().getPath().startsWith("undead.regrow"), "no regrow event: " + ev.getLocation());
		}
		JsonObject sounds;
		try (InputStream in = UndeadRegenGameTests.class.getResourceAsStream("/assets/projecthero/sounds.json")) {
			sounds = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
		for (String k : sounds.keySet()) {
			helper.assertTrue(!k.startsWith("undead.regrow"), "sounds.json still defines " + k);
		}
		helper.assertTrue(UndeadRegenGameTests.class.getResource("/assets/projecthero/sounds/undead/regrow_finish1.ogg") == null, "the regrow takes are gone");
		helper.succeed();
	}
}
