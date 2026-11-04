package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixAttachments;
import com.projecthero.mod.omnitrix.OmnitrixConfig;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.item.OmnitrixItems;
import com.projecthero.mod.omnitrix.pod.OmnitrixPodBlock;
import com.projecthero.mod.omnitrix.pod.OmnitrixPodBlockEntity;
import com.projecthero.mod.omnitrix.pod.OmnitrixPodBlocks;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: the Recalibrated Omnitrix (docs/OMNITRIX_RECALIBRATED_0172.md) -- the pod no longer recalibrates (the chip does it by
 * itself: OmnitrixRecalChipGameTests), the two dials kept apart (select / slap refuse the other watch's aliens), the recalibrated
 * item putting on the recalibrated watch, the user's four sounds registered and picked by {@link Omnitrix#cue}.
 */
public final class OmnitrixRecalibrationGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	/** A Containment Pod at (3, 2, 3) on a stone floor, fuelled. */
	private static OmnitrixPodBlockEntity pod(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		BlockPos lower = helper.absolutePos(new BlockPos(3, 2, 3));
		var state = OmnitrixPodBlocks.POD.defaultBlockState().setValue(OmnitrixPodBlock.FACING, Direction.NORTH);
		helper.getLevel().setBlock(lower, state.setValue(OmnitrixPodBlock.HALF, DoubleBlockHalf.LOWER), 3);
		helper.getLevel().setBlock(lower.above(), state.setValue(OmnitrixPodBlock.HALF, DoubleBlockHalf.UPPER), 3);
		OmnitrixPodBlockEntity be = (OmnitrixPodBlockEntity) helper.getLevel().getBlockEntity(lower);
		be.addFuel(OmnitrixConfig.FUEL_PER_EXTRACTION);
		return be;
	}

	/** The prototype's wearer standing in the pod. */
	private static ServerPlayer wearer(GameTestHelper helper, OmnitrixPodBlockEntity be, int transforms) {
		Vec3 c = be.centre();
		ServerPlayer p = mock(helper, 0, 0, 0);
		p.moveTo(c.x, c.y, c.z, 0f, 0f);
		helper.assertTrue(Omnitrix.grant(p), "the prototype goes on");
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(Omnitrix.TRANSFORMS, (long) transforms);
		Omnitrix.save(p, n);
		return p;
	}

	/**
	 * v0.17.2 (user: "the player can right click the chip and then an option to choose to recalibrate or not will pop up"): the pod no
	 * longer recalibrates -- the chip used on it only says to use the chip itself; nothing starts and the chip is kept.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void thePodPointsAtTheChipItself(GameTestHelper helper) {
		OmnitrixPodBlockEntity be = pod(helper);
		ServerPlayer p = wearer(helper, be, OmnitrixConfig.RECAL_MIN_TRANSFORMS);
		ItemStack chip = new ItemStack(OmnitrixItems.RECALIBRATION_CHIP);
		p.setItemInHand(InteractionHand.MAIN_HAND, chip);
		var hit = new net.minecraft.world.phys.BlockHitResult(be.centre(), Direction.UP, be.getBlockPos(), false);
		var state = helper.getLevel().getBlockState(be.getBlockPos());
		state.useItemOn(chip, helper.getLevel(), p, InteractionHand.MAIN_HAND, hit);
		helper.assertFalse(be.running(), "the pod runs nothing");
		helper.assertTrue(chip.getCount() == 1, "the chip is kept");
		helper.assertFalse(com.projecthero.mod.omnitrix.OmnitrixRecal.busy(p) || Omnitrix.recalibrated(p), "no recalibration started");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theTwoDialsAreKeptApart(GameTestHelper helper) {
		ServerPlayer proto = mock(helper, 1.5, 2, 1.5);
		Omnitrix.grant(proto);
		ServerPlayer recal = mock(helper, 3.5, 2, 1.5);
		Omnitrix.grant(recal, true);
		helper.assertFalse(Omnitrix.recalibrated(proto), "the prototype is not recalibrated");
		helper.assertTrue(Omnitrix.recalibrated(recal), "the recalibrated grant is");
		helper.assertTrue(Aliens.all().size() == 13 && Aliens.recalibrated().size() == 10, "thirteen on the prototype (v0.17.2: + Cannonbolt, Wildvine, Ditto), ten on the recalibrated");
		helper.assertFalse(Omnitrix.select(recal, Aliens.HEATBLAST.id()), "Heatblast is not on the recalibrated dial");
		helper.assertFalse(Omnitrix.select(proto, Aliens.SWAMPFIRE.id()), "Swampfire is not on the prototype's");
		helper.assertTrue(Aliens.byId(Omnitrix.state(recal).selected).recalibrated(), "the recalibrated wearer starts on an Alien Force alien");
		helper.assertFalse(Aliens.byId(Omnitrix.state(proto).selected).recalibrated(), "the prototype's on an original");
		for (Aliens.Alien a : Aliens.playable(recal)) {
			helper.assertTrue(a.recalibrated() && Aliens.available(recal, a.id()) && !Aliens.available(proto, a.id()), a.id() + " only on the recalibrated dial");
		}
		helper.assertTrue(Omnitrix.cue(recal, OmnitrixSounds.DIAL, OmnitrixSounds.RECAL_DIAL) == OmnitrixSounds.RECAL_DIAL, "the recalibrated dial's own click");
		helper.assertTrue(Omnitrix.cue(proto, OmnitrixSounds.DIAL, OmnitrixSounds.RECAL_DIAL) == OmnitrixSounds.DIAL, "the prototype keeps its own");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void onlyThePrototypeCountsItsTransformations(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 1.5, 2, 1.5);
		Omnitrix.grant(p);
		Omnitrix.select(p, Aliens.HEATBLAST.id());
		helper.assertTrue(Omnitrix.transform(p), "transformed");
		helper.assertTrue(Omnitrix.prototypeTransforms(p) == 1, "one counted");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		ServerPlayer q = mock(helper, 3.5, 2, 1.5);
		Omnitrix.grant(q, true);
		if (!Aliens.playable(q).isEmpty()) {
			Omnitrix.select(q, Aliens.playable(q).get(0).id());
			helper.assertTrue(Omnitrix.transform(q), "the recalibrated watch transforms");
			helper.assertTrue(Omnitrix.prototypeTransforms(q) == 0, "and does not count");
			Omnitrix.revert(q, Omnitrix.Reason.REVOKE);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theRecalibratedItemPutsOnTheRecalibratedWatch(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 1.5, 2, 1.5);
		ItemStack watch = new ItemStack(OmnitrixItems.RECALIBRATED_OMNITRIX);
		p.setItemInHand(InteractionHand.MAIN_HAND, watch);
		watch.use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(Omnitrix.hasPower(p) && Omnitrix.recalibrated(p), "on, recalibrated");
		helper.assertTrue(p.getMainHandItem().isEmpty(), "the item is used");
		helper.succeed();
	}

	/** v0.17.2 (user: "make sure recalibrated omnitrix also has master control"): the same long Shift+N sequence, through the Alien Force dial. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void masterControlFlipsThroughTheRecalibratedDial(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p, true), "the recalibrated watch goes on");
		helper.assertTrue(Omnitrix.activate(p), "the watch up");
		Omnitrix.finishRaise(p);
		helper.assertTrue(Omnitrix.startMasterSequence(p), "the sequence starts");
		java.util.Set<String> seen = new java.util.HashSet<>();
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
			seen.add(Omnitrix.state(p).selected);
		});
		int steps = (Aliens.playable(p).size() + 2) * OmnitrixConfig.MASTER_STEP_TICKS;
		helper.runAfterDelay(steps, () -> {
			helper.assertTrue(Omnitrix.masterControl(p), "Master Control is on");
			for (Aliens.Alien a : Aliens.playable(p)) {
				helper.assertTrue(seen.contains(a.id()), "the dial showed " + a.id());
			}
			for (String id : seen) {
				helper.assertTrue(Aliens.byId(id).recalibrated(), "only Alien Force aliens on the way, saw " + id);
			}
			helper.assertFalse(Omnitrix.masterSwitch(p, Aliens.HEATBLAST.id()), "never an original alien");
			if (!Aliens.playable(p).isEmpty()) {
				String first = Aliens.playable(p).get(0).id();
				helper.assertTrue(Omnitrix.masterSwitch(p, first) && first.equals(Omnitrix.active(p)), "straight into " + first);
				Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			}
			helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(OmnitrixSounds.RECAL_MASTER_SOUND.getLocation()), "the voice line is registered");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theUsersFourSoundsAreRegistered(GameTestHelper helper) {
		for (var e : OmnitrixSounds.RECAL) {
			helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(e.getLocation()), e.getLocation() + " registered");
		}
		helper.assertTrue(OmnitrixSounds.RECAL.length == 4, "prime, transform, detransform, dial turn");
		helper.succeed();
	}
}
