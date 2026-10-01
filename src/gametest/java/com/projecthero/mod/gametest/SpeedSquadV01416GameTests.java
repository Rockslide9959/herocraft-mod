package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.flash.FlashRing;
import com.projecthero.mod.flash.FlashSuit;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.hero.power.p04.SuperSpeedMoves;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.16 speed + squad: Overdrive ends on a second V (never on the key repeat), Overdrive's walk / sprint speeds
 * (32 / 100 blocks/s, derived from the attribute), the 50-block Speed Sweep that runs until every enemy has been hit
 * (with its 5 s unreachable and 60 s failsafes and the X-again cancel), the packed Flash Suit's durability percentage,
 * and squad friendly fire (off by default, the leader's switch, codec round trip incl. pre-0.14.16 data).
 * Mock players are not reliably ticked, so time-based behaviour is driven by hand and asserted on states, not clocks.
 */
public class SpeedSquadV01416GameTests implements FabricGameTest {

	// ---- helpers ------------------------------------------------------------------------------------------

	/** A survival speedster at (2.5, 2, 1.5) facing +Z, in a cleared 7 x 4 x 7 room with a stone floor. */
	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 2, 0)), helper.absolutePos(new BlockPos(6, 5, 6)))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(6, 1, 6)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 1.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		Power power = power();
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		p.setOnGround(true);
		return p;
	}

	private static Power power() {
		return Powers.byKey(SuperSpeedHandlers.KEY);
	}

	/** A no-AI, armourless 100 HP zombie at relative {@code (x, 2, z)}. */
	private static Zombie zombie(GameTestHelper helper, double x, double z) {
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0);
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
		m.setHealth(100);
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static void shiftPress(ServerPlayer p, int slot) {
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, slot, true);
		p.setShiftKeyDown(false);
	}

	private static ServerPlayer mock(GameTestHelper helper, double x, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 v = helper.absoluteVec(new Vec3(x, 2.0, z));
		p.moveTo(v.x, v.y, v.z, 0f, 0f);
		return p;
	}

	// ---- V: Overdrive ends early on a fresh press ---------------------------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "v01416_overdrive")
	public void overdriveEndsOnASecondVButNeverOnTheKeyRepeat(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		AbilityRouter.handleInput(p, 5, true); // V
		helper.assertTrue(SuperSpeedHandlers.overdrive(p), "Overdrive starts");
		AbilityRouter.handleInput(p, 5, true); // the OS key repeat of the same press
		AbilityRouter.handleInput(p, 5, true);
		helper.assertTrue(SuperSpeedHandlers.overdrive(p), "the key repeat does not end it");
		AbilityRouter.handleInput(p, 5, false); // let go
		helper.assertTrue(SuperSpeedHandlers.overdrive(p), "letting go does not end it");
		ExperimentalPowers.serverTick(p);
		helper.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ProjectHeroMod.id("overdrive_speed")) != null,
				"precondition: the Overdrive speed tier is on");
		AbilityRouter.handleInput(p, 5, true); // a fresh press
		helper.assertFalse(SuperSpeedHandlers.overdrive(p), "a fresh V ends Overdrive");
		helper.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ProjectHeroMod.id("overdrive_speed")) == null,
				"its speed tier goes at once");
		helper.assertTrue(p.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(ProjectHeroMod.id("overdrive_attack_damage")) == null,
				"and its double damage");
		helper.assertFalse(ExperimentalPowers.cooldownReady(p, power(), power().ability(AbilitySlot.SLOT_5)),
				"the cooldown from switching it on keeps running");
		AbilityRouter.handleInput(p, 5, false);
		AbilityRouter.handleInput(p, 5, true);
		helper.assertFalse(SuperSpeedHandlers.overdrive(p), "and V cannot start it again during that cooldown");
		helper.succeed();
	}

	// ---- Overdrive speeds: 32 walking, 100 sprinting ------------------------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void overdriveSpeedsComeFromTheAttributeMaths(GameTestHelper helper) {
		// the conversion reproduces vanilla's published speeds
		double k = SuperSpeedHandlers.BLOCKS_PER_SECOND_PER_SPEED;
		helper.assertTrue(Math.abs(0.1 * k - 4.317) < 0.01, "vanilla walking 0.1 -> 4.317 b/s, got " + 0.1 * k);
		helper.assertTrue(Math.abs(0.13 * k - 5.612) < 0.01, "vanilla sprinting 0.13 -> 5.612 b/s, got " + 0.13 * k);
		// the helper and its inverse agree, and give the asked-for speeds
		double walk = SuperSpeedHandlers.blocksPerSecond(SuperSpeedHandlers.OVERDRIVE_WALK_BONUS, false);
		double sprint = SuperSpeedHandlers.blocksPerSecond(SuperSpeedHandlers.OVERDRIVE_BONUS, true);
		helper.assertTrue(Math.abs(walk - 32.0) < 1e-6, "Overdrive walking is 32 b/s, got " + walk);
		helper.assertTrue(Math.abs(sprint - 100.0) < 1e-6, "Overdrive sprinting is 100 b/s, got " + sprint);
		helper.assertTrue(Math.abs(SuperSpeedHandlers.OVERDRIVE_WALK_BONUS - 6.112) < 0.01,
				"walk bonus ~6.11, got " + SuperSpeedHandlers.OVERDRIVE_WALK_BONUS);
		helper.assertTrue(Math.abs(SuperSpeedHandlers.OVERDRIVE_BONUS - 16.519) < 0.01,
				"sprint bonus ~16.52, got " + SuperSpeedHandlers.OVERDRIVE_BONUS);
		// Speed Mode keeps its ~20 / ~40 tiers under the same maths
		double smWalk = SuperSpeedHandlers.blocksPerSecond(SuperSpeedHandlers.SPEED_MODE_WALK_BONUS, false);
		double smRun = SuperSpeedHandlers.blocksPerSecond(SuperSpeedHandlers.SPEED_MODE_BONUS, true);
		helper.assertTrue(smWalk > 19 && smWalk < 22 && smRun > 39 && smRun < 43, "Speed Mode ~20 / ~40, got " + smWalk + " / " + smRun);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "v01416_overdrive_speed")
	public void overdriveAppliesTheWalkAndSprintTiersOnThePlayer(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		ExperimentalPowers.serverTick(p);
		AbilityRouter.handleInput(p, 5, true); // V
		AbilityRouter.handleInput(p, 5, false);
		p.setSprinting(false);
		ExperimentalPowers.serverTick(p);
		var walk = p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ProjectHeroMod.id("overdrive_speed"));
		helper.assertTrue(walk != null && walk.amount() == SuperSpeedHandlers.OVERDRIVE_WALK_BONUS, "walking: the 32 b/s tier");
		double walkBps = p.getAttributeValue(Attributes.MOVEMENT_SPEED) * SuperSpeedHandlers.BLOCKS_PER_SECOND_PER_SPEED;
		helper.assertTrue(Math.abs(walkBps - 32.0) < 0.5, "the walking attribute runs ~32 b/s, got " + walkBps);
		p.setSprinting(true);
		ExperimentalPowers.serverTick(p);
		var run = p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ProjectHeroMod.id("overdrive_speed"));
		helper.assertTrue(run != null && run.amount() == SuperSpeedHandlers.OVERDRIVE_BONUS, "sprinting: the 100 b/s tier");
		double runBps = p.getAttributeValue(Attributes.MOVEMENT_SPEED) * SuperSpeedHandlers.BLOCKS_PER_SECOND_PER_SPEED;
		helper.assertTrue(Math.abs(runBps - 100.0) < 1.0, "the sprinting attribute (with vanilla's x1.3) runs ~100 b/s, got " + runBps);
		helper.succeed();
	}

	// ---- Shift+X: Speed Sweep, 50 blocks, until every enemy is hit -----------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void sweepTargetsBookkeepingAndFailsafes(GameTestHelper helper) {
		helper.assertTrue(SuperSpeedMoves.SWEEP_RANGE == 50.0, "a 50-block radius");
		helper.assertTrue(SuperSpeedMoves.SWEEP_UNREACHABLE_TICKS == 100, "an unreachable target is given 5 s");
		helper.assertTrue(SuperSpeedMoves.SWEEP_MAX_TICKS == 1200, "a sweep is capped at 60 s");
		SuperSpeedMoves.SweepTargets t = new SuperSpeedMoves.SweepTargets(List.of(1, 2, 3, 4));
		t.hit(1);
		helper.assertFalse(t.done(), "not done with three still to hit");
		helper.assertTrue(t.wasHit(1) && !t.isPending(1) && t.hitCount() == 1, "a hit target leaves the list");
		t.drop(2); // died
		t.unreachable(3, 10);
		t.unreachable(3, 50); // the clock started at 10, not 50
		helper.assertTrue(t.dropStale(109) == 0 && t.isPending(3), "still trying at 4.95 s");
		helper.assertTrue(t.dropStale(110) == 1 && !t.isPending(3), "given up on after 5 s unreachable");
		helper.assertFalse(t.done(), "4 has not been hit yet");
		t.hit(4);
		helper.assertTrue(t.done() && t.hitCount() == 2, "done once every one is hit or gone");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "v01416_sweep_every")
	public void sweepKeepsGoingUntilEveryZombieIsHitEvenIfOneDies(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Vec3 home = p.position();
		Zombie a = zombie(helper, 0.8, 4.5);
		Zombie b = zombie(helper, 4.5, 4.5);
		Zombie c = zombie(helper, 5.2, 1.5);
		Zombie d = zombie(helper, 1.5, 5.5);
		shiftPress(p, 3); // Shift+X
		helper.assertTrue(SuperSpeedMoves.sweeping(p), "the sweep starts");
		// (the 50-block radius can also take in a stray mob outside the test cage -- assert only on our own zombies)
		int hitAtOnce = 0;
		Zombie gone = null;
		for (Zombie z : new Zombie[] { a, b, c, d }) {
			if (SuperSpeedMoves.sweepHit(p, z)) {
				hitAtOnce++;
			} else if (gone == null) {
				gone = z;
			}
		}
		helper.assertTrue(hitAtOnce == 1, "the first hop lands at once on one zombie (" + hitAtOnce + ")");
		helper.assertTrue(SuperSpeedMoves.sweepPending(p) >= 3, "the other three are still to hit (" + SuperSpeedMoves.sweepPending(p) + ")");
		// one of the rest dies before the sweep gets to it
		gone.discard();
		// generous: a stray it cannot reach is only given up on after 5 s (100 ticks); the cap is 60 s
		for (int steps = 0; steps < 600 && SuperSpeedMoves.sweeping(p); steps++) {
			SuperSpeedMoves.sweepTick(p);
		}
		helper.assertFalse(SuperSpeedMoves.sweeping(p), "the sweep finishes");
		for (Zombie z : new Zombie[] { a, b, c, d }) {
			if (z == gone) {
				continue;
			}
			float dealt = 100f - z.getHealth();
			helper.assertTrue(Math.abs(dealt - SuperSpeedMoves.SWEEP_DAMAGE) < 0.01f, "each living zombie is hit exactly once, dealt " + dealt);
		}
		helper.assertTrue(p.position().distanceTo(home) < 0.01, "back where it started");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "v01416_sweep_cap")
	public void sweepStopsAtTheHardCapAndOnASecondPress(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Vec3 home = p.position();
		zombie(helper, 0.8, 4.5);
		zombie(helper, 4.5, 4.5);
		zombie(helper, 5.2, 1.5);
		// the 60 s failsafe: however many are left, the sweep ends and brings you home
		shiftPress(p, 3);
		helper.assertTrue(SuperSpeedMoves.sweeping(p) && SuperSpeedMoves.sweepPending(p) > 0, "a sweep with targets left");
		SuperSpeedMoves.ageSweep(p, SuperSpeedMoves.SWEEP_MAX_TICKS);
		SuperSpeedMoves.sweepTick(p);
		helper.assertFalse(SuperSpeedMoves.sweeping(p), "the hard cap ends it");
		helper.assertTrue(p.position().distanceTo(home) < 0.01, "and brings you home");
		// X again: the key repeat of the starting press is ignored, a fresh press calls it off
		ExperimentalPowers.setResource(p, power(), SuperSpeedMoves.SWEEP_READY, 0f, 1e12f); // skip the 10 s cooldown
		shiftPress(p, 3);
		helper.assertTrue(SuperSpeedMoves.sweeping(p), "a second sweep starts");
		shiftPress(p, 3); // the key repeat
		helper.assertTrue(SuperSpeedMoves.sweeping(p), "the key repeat does not end it");
		AbilityRouter.handleInput(p, 3, false); // let go of X
		AbilityRouter.handleInput(p, 3, true); // X again
		helper.assertFalse(SuperSpeedMoves.sweeping(p), "a fresh X calls it off");
		helper.assertTrue(p.position().distanceTo(home) < 0.01, "and brings you home");
		helper.succeed();
	}

	// ---- the packed Flash Suit's durability -----------------------------------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void packedSuitDurabilityIsTheOverallPercentage(GameTestHelper helper) {
		helper.assertTrue(FlashRing.storedDurabilityPercent(ItemStack.EMPTY) == -1, "no ring, no line");
		helper.assertTrue(FlashRing.storedDurabilityPercent(new ItemStack(FlashSuit.RING)) == -1, "an empty ring, no line");
		List<ItemStack> pieces = new ArrayList<>();
		long max = 0;
		long left = 0;
		for (ItemStack piece : new ItemStack[] { new ItemStack(FlashSuit.HELMET), new ItemStack(FlashSuit.CHESTPLATE),
				new ItemStack(FlashSuit.LEGGINGS), new ItemStack(FlashSuit.BOOTS) }) {
			int dmg = piece.getMaxDamage() / 4;
			piece.setDamageValue(dmg);
			max += piece.getMaxDamage();
			left += piece.getMaxDamage() - dmg;
			pieces.add(piece);
		}
		ItemStack ring = new ItemStack(FlashSuit.RING);
		ring.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(pieces));
		int expected = (int) Math.floor(left * 100.0 / max);
		int pct = FlashRing.storedDurabilityPercent(ring);
		helper.assertTrue(pct == expected && pct >= 74 && pct <= 76, "a quarter worn -> ~75%, got " + pct);
		// fully mended reads exactly 100
		List<ItemStack> mended = new ArrayList<>();
		for (ItemStack piece : pieces) {
			ItemStack m = piece.copy();
			m.setDamageValue(0);
			mended.add(m);
		}
		ItemStack fresh = new ItemStack(FlashSuit.RING);
		fresh.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(mended));
		helper.assertTrue(FlashRing.storedDurabilityPercent(fresh) == 100, "a mended suit is 100%");
		// worn in the chestplate slot by a speedster, the player-level reading matches
		ServerPlayer p = hero(helper);
		helper.assertTrue(FlashRing.storedDurabilityPercent(p) == -1, "no ring worn, no line");
		p.setItemSlot(EquipmentSlot.CHEST, ring);
		helper.assertTrue(FlashRing.storedDurabilityPercent(p) == expected, "the worn ring's percentage");
		helper.succeed();
	}

	// ---- squads: friendly fire ------------------------------------------------------------------------------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "v01416_squad_ff")
	public void friendlyFireIsOffByDefaultAndTheLeaderCanTurnItOn(GameTestHelper helper) {
		ServerPlayer leader = mock(helper, 2.5, 2.5);
		ServerPlayer mate = mock(helper, 4.5, 2.5);
		SquadManager squads = SquadManager.get(helper.getLevel().getServer());
		Squad squad = squads.create("ff" + leader.getUUID().toString().substring(0, 8), leader.getUUID());
		squads.addMember(squad, mate.getUUID());
		try {
			helper.assertFalse(squad.friendlyFire(), "off for a new squad");
			helper.assertTrue(Squads.isFriendlyFire(mate, mate.damageSources().playerAttack(leader)), "a squadmate's hit is vetoed");
			helper.assertTrue(Squads.shields(leader, mate) && Squads.shields(mate, leader), "both ways");

			helper.assertTrue(Squads.setFriendlyFire(mate, true) != null, "a member who is not the leader is refused");
			helper.assertFalse(squad.friendlyFire(), "and nothing changes");
			helper.assertTrue(Squads.setFriendlyFire(leader, true) == null, "the leader turns it on");
			helper.assertTrue(squad.friendlyFire() && Squads.friendlyFireOn(mate), "on, for the whole squad");
			helper.assertTrue(Squads.setFriendlyFire(leader, true) != null, "already on");

			helper.assertFalse(Squads.isFriendlyFire(mate, mate.damageSources().playerAttack(leader)), "now the leader's hit lands");
			helper.assertFalse(Squads.isFriendlyFire(leader, leader.damageSources().playerAttack(mate)), "and the other way");
			helper.assertFalse(Squads.shields(leader, mate), "ability damage checks let it through too");
			helper.assertTrue(Squads.areAllies(leader, mate), "but they are still allies for heals, buffs and AoE targeting");
			helper.assertTrue(Squads.snapshot(leader, squad).friendlyFire(), "the roster tells the squad screen");

			helper.assertTrue(Squads.setFriendlyFire(leader, false) == null, "and off again");
			helper.assertTrue(Squads.isFriendlyFire(mate, mate.damageSources().playerAttack(leader)), "protected again");
		} finally {
			squads.disband(squad);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void squadCodecKeepsFriendlyFireAndReadsOldSaves(GameTestHelper helper) {
		UUID leader = UUID.randomUUID();
		UUID mate = UUID.randomUUID();
		SquadManager squads = SquadManager.get(helper.getLevel().getServer());
		Squad squad = squads.create("codec" + leader.toString().substring(0, 8), leader);
		squads.addMember(squad, mate);
		try {
			squads.setFriendlyFire(squad, true);
			Tag saved = Squad.CODEC.encodeStart(NbtOps.INSTANCE, squad).getOrThrow();
			Squad back = Squad.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
			helper.assertTrue(back.friendlyFire(), "friendly fire survives a save");
			helper.assertTrue(back.id().equals(squad.id()) && back.name().equals(squad.name()) && back.leader().equals(leader)
					&& back.has(mate) && back.size() == 2, "and so does everything else");

			// a squad saved before v0.14.16 has no friendly_fire field
			CompoundTag old = new CompoundTag();
			old.putString("id", UUID.randomUUID().toString());
			old.putString("name", "Old Guard");
			old.putString("leader", leader.toString());
			ListTag members = new ListTag();
			members.add(StringTag.valueOf(leader.toString()));
			members.add(StringTag.valueOf(mate.toString()));
			old.put("members", members);
			Squad legacy = Squad.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
			helper.assertFalse(legacy.friendlyFire(), "an old squad loads with friendly fire off");
			helper.assertTrue(legacy.name().equals("Old Guard") && legacy.size() == 2, "and the rest intact");
		} finally {
			squads.disband(squad);
		}
		helper.succeed();
	}
}
