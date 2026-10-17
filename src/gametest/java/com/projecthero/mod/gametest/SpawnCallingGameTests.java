package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.calling.SpawnCallings;
import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 (user): the spawn-in callings -- an offer only on the first spawn, Accept grants (the ring / the Miraculous),
 * Refuse grants nothing, one roll per player per world.
 */
public class SpawnCallingGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "spawn_calling_ring")
	public void acceptingTheRingMakesAGreenLantern(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		SpawnCallings.offerNow(p, SpawnCallings.Kind.GREEN_LANTERN, null);
		helper.assertTrue(SpawnCallings.pending(p) != null, "the ring's offer is open");
		helper.assertFalse(GreenLantern.hasPower(p), "nothing before Accept");
		helper.assertTrue(SpawnCallings.accept(p), "Accept bonds the ring");
		helper.assertTrue(GreenLantern.hasPower(p), "Green Lantern after Accept");
		helper.assertTrue(SpawnCallings.pending(p) == null, "the offer is gone");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "spawn_calling_miraculous")
	public void acceptingAMiraculousGrantsThatKwami(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		SpawnCallings.offerNow(p, SpawnCallings.Kind.MIRACULOUS, Kwami.PLAGG);
		helper.assertFalse(Miraculous.hasPower(p), "nothing before Accept");
		helper.assertTrue(SpawnCallings.accept(p), "Accept grants the Miraculous");
		helper.assertTrue(Miraculous.holds(p, Kwami.PLAGG), "Plagg's holder after Accept");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "spawn_calling_refuse")
	public void refusingGrantsNothing(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		SpawnCallings.offerNow(p, SpawnCallings.Kind.MIRACULOUS, Kwami.TIKKI);
		SpawnCallings.refuse(p);
		helper.assertFalse(Miraculous.hasPower(p), "Refuse grants nothing");
		helper.assertFalse(SpawnCallings.accept(p), "and there is nothing left to accept");
		helper.assertFalse(Miraculous.hasPower(p), "still nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "spawn_calling_once")
	public void theRollHappensOncePerPlayer(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		p.setAttached(ModAttachments.SPAWN_CALLING_DONE, false);
		SpawnCallings.forceNext = new SpawnCallings.Offer(SpawnCallings.Kind.GREEN_LANTERN, null, 0L, 0L, false);
		ServerJoin.call(p);
		helper.assertTrue(SpawnCallings.pending(p) != null && SpawnCallings.pending(p).kind() == SpawnCallings.Kind.GREEN_LANTERN,
				"a first spawn rolls (forced: the ring)");
		helper.assertTrue(Boolean.TRUE.equals(p.getAttachedOrElse(ModAttachments.SPAWN_CALLING_DONE, false)), "the roll is recorded");
		SpawnCallings.refuse(p);
		SpawnCallings.forceNext = new SpawnCallings.Offer(SpawnCallings.Kind.GREEN_LANTERN, null, 0L, 0L, false);
		ServerJoin.call(p);
		helper.assertTrue(SpawnCallings.pending(p) == null, "no second roll for the same player");
		SpawnCallings.forceNext = null;
		helper.succeed();
	}

	/** Package access to the join hook. */
	private static final class ServerJoin {
		static void call(ServerPlayer p) {
			try {
				java.lang.reflect.Method m = SpawnCallings.class.getDeclaredMethod("onJoin", ServerPlayer.class);
				m.setAccessible(true);
				m.invoke(null, p);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}
	}
}
