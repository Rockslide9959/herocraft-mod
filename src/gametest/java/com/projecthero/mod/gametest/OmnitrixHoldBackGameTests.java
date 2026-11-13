package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixFeature;
import com.projecthero.mod.omnitrix.OmnitrixLongTransform;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.sequence.ShowSequences;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (user: "I want to exclude the new Ben 10 aliens and all the transformation scenes from this update"): with the release
 * switches off ({@link OmnitrixFeature#PROTOTYPE_ALIENS_0173}, {@link OmnitrixFeature#SHOW_SEQUENCES}) none of the six new prototype
 * aliens can be found, dialled or transformed into, and a right-click on the dial is the normal quick transformation (Four Arms'
 * long one, shipped in v0.17.2, stays). The gametest server has both switches on by default, so every check here flips them off and
 * back within one synchronous block (no other test runs in between).
 */
public class OmnitrixHoldBackGameTests implements FabricGameTest {
	private static final List<String> HELD = List.of(Aliens.SNARE_OH.id(), Aliens.SHOCK_ROCK.id(), Aliens.FRANKENSTRIKE.id(),
			Aliens.EYE_GUY.id(), Aliens.BENWOLF.id(), Aliens.ARCTIGUANA.id());

	private static ServerPlayer wearer(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2, 3.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setOnGround(true);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		Omnitrix.noGlitch(p);
		return p;
	}

	private static void raise(GameTestHelper helper, ServerPlayer p) {
		helper.assertTrue(Omnitrix.activate(p), "the watch comes up");
		Omnitrix.finishRaise(p);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void theNewPrototypeAliensAreHeldBack(GameTestHelper helper) {
		ServerPlayer p = wearer(helper);
		helper.assertTrue(OmnitrixFeature.HELD_ALIENS_0173.containsAll(HELD) && HELD.size() == OmnitrixFeature.HELD_ALIENS_0173.size(),
				"the six held-back aliens");
		helper.assertFalse(OmnitrixFeature.PROTOTYPE_ALIENS_0173, "held back in this release");
		// one of them is already in the world's save (a dev world): found, and he is that alien
		Aliens.discover(p, Aliens.SNARE_OH.id(), true);
		helper.assertTrue(Omnitrix.select(p, Aliens.SNARE_OH.id()), "with the switch on, a found Snare-oh is dialled");
		helper.assertTrue(Omnitrix.transform(p), "and transformed into");
		OmnitrixFeature.forcePrototypeAliensForTests(false);
		try {
			for (String id : HELD) {
				helper.assertTrue(Aliens.all().stream().noneMatch(a -> a.id().equals(id)), id + ": not in the roster (I menu, Guidebook)");
				helper.assertTrue(Aliens.dial(false).stream().noneMatch(a -> a.id().equals(id)), id + ": not on the dial");
				helper.assertTrue(Aliens.playable().stream().noneMatch(a -> a.id().equals(id)), id + ": not playable");
				helper.assertTrue(Aliens.playable(p).stream().noneMatch(a -> a.id().equals(id)), id + ": not on his dial / wheel");
				helper.assertTrue(Aliens.mistransformFinds().stream().noneMatch(a -> a.id().equals(id)), id + ": not in discover-all");
				helper.assertTrue(Aliens.unfound(p).stream().noneMatch(a -> a.id().equals(id)), id + ": no mistransform finds it");
				Aliens.discover(p, id, true);
				helper.assertFalse(Aliens.found(p, id), id + ": not found even when the save says so");
				helper.assertFalse(Aliens.available(p, id), id + ": not available");
			}
			// the world had him as Snare-oh: the next watch tick turns him back
			Omnitrix.tick(p);
			helper.assertFalse(Omnitrix.state(p).transformed(), "turned back out of a held-back alien");
			for (String id : HELD) {
				helper.assertFalse(Omnitrix.select(p, id), id + ": the dial refuses it");
				OmnitrixState n = Omnitrix.state(p).copy();
				n.selected = id;
				n.rechargeUntil = 0L;
				Omnitrix.save(p, n);
				helper.assertFalse(Omnitrix.transform(p), id + ": no transformation into it");
				helper.assertFalse(Omnitrix.state(p).transformed(), id + ": still human");
			}
			// the dial falls back to a playable alien when the watch comes up
			raise(helper, p);
			helper.assertFalse(HELD.contains(Omnitrix.state(p).selected), "the raise moves the dial off a held-back alien");
			helper.assertTrue(Aliens.available(p, Omnitrix.state(p).selected), "onto one he can be");
			// the older hidden aliens are untouched
			helper.assertTrue(Aliens.mistransformFinds().stream().anyMatch(a -> a.id().equals(Aliens.CANNONBOLT.id())), "Cannonbolt still findable");
		} finally {
			OmnitrixFeature.forcePrototypeAliensForTests(null);
		}
		for (String id : HELD) {
			Aliens.discover(p, id, false);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void rightClickIsTheQuickTransformationWithTheShowsHeldBack(GameTestHelper helper) {
		ServerPlayer p = wearer(helper);
		helper.assertFalse(OmnitrixFeature.SHOW_SEQUENCES, "held back in this release");
		OmnitrixFeature.forceShowSequencesForTests(false);
		try {
			helper.assertTrue(OmnitrixLongTransform.has(Aliens.FOUR_ARMS.id()), "Four Arms' long transformation (v0.17.2) stays");
			for (Aliens.Alien a : Aliens.all()) {
				if (!a.id().equals(Aliens.FOUR_ARMS.id())) {
					helper.assertFalse(ShowSequences.has(a.id()), a.id() + ": no show sequence while held back");
				}
			}
			for (String id : List.of(Aliens.HEATBLAST.id(), Aliens.XLR8.id(), Aliens.GHOSTFREAK.id())) {
				helper.assertTrue(ShowSequences.byHash(id.hashCode()) == null, id + ": a synced sequence id resolves to nothing");
			}
			raise(helper, p);
			helper.assertTrue(Omnitrix.select(p, Aliens.HEATBLAST.id()), "the dial turns to Heatblast");
			helper.assertTrue(Omnitrix.slap(p, true), "the right-click slaps the dial");
			helper.assertFalse(OmnitrixLongTransform.playing(p), "no show sequence: the quick transformation");
			helper.assertFalse(OmnitrixLongTransform.rooted(p), "not rooted");
			helper.assertFalse(Omnitrix.state(p).timers.containsKey(OmnitrixLongTransform.TIMER), "no sequence clock");
			helper.assertTrue(Omnitrix.state(p).animId == Omnitrix.ANIM_SLAP, "the quick slap");
		} finally {
			OmnitrixFeature.forceShowSequencesForTests(null);
		}
		helper.succeed();
	}
}
