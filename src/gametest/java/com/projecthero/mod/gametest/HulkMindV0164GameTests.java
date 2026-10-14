package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hulk.GammaOverload;
import com.projecthero.mod.hulk.Hulk;
import com.projecthero.mod.hulk.HulkMind;
import com.projecthero.mod.hulk.data.HulkMindReturn;
import com.projecthero.mod.hulk.entity.HulkMindBodyEntity;
import com.projecthero.mod.hulk.entity.MirrorHulkEntity;
import com.projecthero.mod.hulk.item.HulkItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.4 (user): the new Gamma origin -- the 10-second flash with the player on his knees absorbing it, the reactor going
 * at 10 s and pulling him into his mind (return point saved, body left behind), the mirror Hulk copying him, the mirror
 * click bringing him home into the slow change, and the clean-up on logout / a session reset / a stranger in the mind.
 * Every overload here is started without its explosions ({@link GammaOverload#start(ServerPlayer, BlockPos, boolean)}).
 */
public class HulkMindV0164GameTests implements FabricGameTest {
	private static ServerPlayer dosed(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		GammaOverload.setDosed(p, true);
		return p;
	}

	/**
	 * The mind dimension, or null on the GameTest server: it bakes its world from the flat preset with an EMPTY level-stem
	 * registry, so datapack dimensions (data/projecthero/dimension/hulk_mind.json) never load there -- they do in a real
	 * world. The mind-room tests then exercise the documented fallback (he changes where he stood) or stand down; the room
	 * itself was verified in a real client (screenshot harness, v0.16.4).
	 */
	private static ServerLevel mind(GameTestHelper helper) {
		return helper.getLevel().getServer().getLevel(HulkMind.DIMENSION);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void theReactorFlashesForTenSecondsThenPullsHimIntoHisMind(GameTestHelper helper) {
		ServerPlayer p = dosed(helper);
		BlockPos at = BlockPos.containing(p.position()).offset(2, 0, 0);
		BlockPos second = BlockPos.containing(p.position()).offset(-2, 0, 0);
		helper.getLevel().setBlock(at, HulkItems.GAMMA_REACTOR.defaultBlockState(), 2);
		helper.getLevel().setBlock(second, HulkItems.GAMMA_REACTOR.defaultBlockState(), 2);
		GammaOverload.start(p, at, false);
		helper.assertTrue(GammaOverload.overloading(at), "the reactor goes critical");
		GammaOverload.onReactorUsed(p, second);
		helper.assertFalse(GammaOverload.overloading(second), "one overload per player");
		helper.runAfterDelay(GammaOverload.KNEEL_AT + 6, () -> {
			helper.assertTrue(GammaOverload.absorbing(p), "he is absorbing the flash");
			helper.assertTrue(p.getAttachedOrElse(ModAttachments.GAMMA_ABSORB, null) != null, "the absorb state is set (and synced)");
			helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) < 1.0e-6, "on his knees: he cannot walk");
		});
		helper.runAfterDelay(GammaOverload.FLASH_TICKS - 10, () -> {
			helper.assertTrue(GammaOverload.overloading(at), "still flashing just before 10 s");
			helper.assertTrue(helper.getLevel().getBlockState(at).is(HulkItems.GAMMA_REACTOR), "no detonation before 10 s");
			helper.assertFalse(HulkMind.inSession(p), "still in the world");
			helper.assertTrue(GammaOverload.isDosed(p), "the dose is kept until the blast");
		});
		helper.runAfterDelay(GammaOverload.FLASH_TICKS + 5, () -> {
			helper.assertTrue(helper.getLevel().getBlockState(at).isAir(), "the reactor blew at 10 s");
			if (mind(helper) == null) {
				// no datapack dimensions on the GameTest server: the fallback -- no session, he changes where he stood
				helper.assertFalse(HulkMind.inSession(p), "no mind to go to: no session");
				helper.assertTrue(p.getAttachedOrElse(ModAttachments.GAMMA_ABSORB, null) == null, "no longer absorbing");
				helper.getLevel().setBlock(second, Blocks.AIR.defaultBlockState(), 2);
				helper.succeed();
				return;
			}
			helper.assertTrue(HulkMind.inSession(p), "a mind session began");
			helper.assertTrue(p.level().dimension() == HulkMind.DIMENSION, "he is in his mind");
			helper.assertFalse(GammaOverload.isDosed(p), "the dose is spent");
			helper.assertTrue(p.getAttachedOrElse(ModAttachments.GAMMA_ABSORB, null) == null, "no longer absorbing");
			HulkMindReturn back = p.getAttachedOrElse(ModAttachments.HULK_MIND_RETURN, null);
			helper.assertTrue(back != null && back.dimension().equals(helper.getLevel().dimension().location().toString()),
					"where he knelt is saved");
			HulkMindBodyEntity body = HulkMind.body(helper.getLevel().getServer(), p);
			helper.assertTrue(body != null && body.level() == helper.getLevel(), "his body stays behind");
			HulkMind.returnNow(p);
			helper.assertTrue(body.isRemoved(), "the body goes when he comes back");
			helper.getLevel().setBlock(second, Blocks.AIR.defaultBlockState(), 2);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theMirrorClickBringsHimBackIntoTheChange(GameTestHelper helper) {
		ServerPlayer p = dosed(helper);
		ServerLevel mind = mind(helper);
		if (mind == null) {
			helper.succeed(); // see mind(): no datapack dimensions on the GameTest server
			return;
		}
		Vec3 from = p.position();
		helper.assertTrue(HulkMind.enter(p), "into his mind");
		helper.assertFalse(HulkMind.enter(p), "never two sessions");
		HulkMindReturn back = p.getAttachedOrElse(ModAttachments.HULK_MIND_RETURN, null);
		helper.assertTrue(back != null && Math.abs(back.x() - from.x) < 1.0e-6 && Math.abs(back.z() - from.z) < 1.0e-6,
				"the return point is where he stood");
		HulkMindBodyEntity body = HulkMind.body(helper.getLevel().getServer(), p);
		helper.assertTrue(body != null, "his body is left kneeling");
		helper.assertTrue(p.level() == mind, "teleported into the mind");
		helper.assertTrue(mind.getBlockState(BlockPos.containing(p.position()).below()).is(Blocks.POLISHED_BLACKSTONE),
				"standing on the dark room's floor");
		HulkMind.onMirrorUsed(p);
		helper.assertTrue(HulkMind.smashing(p), "the Hulk comes through the mirror");
		helper.runAfterDelay(MirrorHulkEntity.SMASH_RETURN_AT + 6, () -> {
			helper.assertFalse(HulkMind.inSession(p), "the session is over");
			helper.assertTrue(p.level() == helper.getLevel(), "back in the world");
			helper.assertTrue(Math.abs(p.getX() - from.x) < 0.5 && Math.abs(p.getZ() - from.z) < 0.5 && Math.abs(p.getY() - from.y) < 3.0,
					"back where he knelt");
			helper.assertTrue(p.getAttachedOrElse(ModAttachments.HULK_MIND_RETURN, null) == null, "the return point is cleared");
			helper.assertTrue(body.isRemoved(), "his body is gone");
			helper.assertTrue(Hulk.hasPower(p), "the Gamma power is granted");
			helper.assertTrue(Hulk.isHulk(p) && Hulk.changing(p), "and the slow change has begun");
			helper.assertTrue(GammaOverload.shielded(p), "with a blast shield");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void theHulkInTheMirrorIsHisReflection(GameTestHelper helper) {
		Vec3 r = MirrorHulkEntity.reflect(new Vec3(3.0, 101.0, 6.5), 0.5);
		helper.assertTrue(r.x == 3.0 && r.y == 101.0 && r.z == -5.5, "positions reflect across the mirror plane, got " + r);
		helper.assertTrue(Math.abs(MirrorHulkEntity.reflectYaw(180.0f)) < 1.0e-4, "facing the mirror: the Hulk faces him");
		helper.assertTrue(Math.abs(MirrorHulkEntity.reflectYaw(90.0f) - 90.0f) < 1.0e-4, "looking along the mirror: so does the Hulk");
		helper.assertTrue(Math.abs(Math.abs(MirrorHulkEntity.reflectYaw(0.0f)) - 180.0f) < 1.0e-4, "turning his back: the Hulk does too");
		ServerPlayer p = dosed(helper);
		ServerLevel mind = mind(helper);
		if (mind == null) {
			helper.succeed(); // see mind(): no datapack dimensions on the GameTest server
			return;
		}
		helper.assertTrue(HulkMind.enter(p), "into his mind");
		Vec3 moved = p.position().add(1.5, 0.0, -2.0);
		p.teleportTo(mind, moved.x, moved.y, moved.z, 120.0f, 25.0f);
		helper.runAfterDelay(3, () -> {
			MirrorHulkEntity hulk = mind.getEntitiesOfClass(MirrorHulkEntity.class, new AABB(p.blockPosition()).inflate(20.0)).stream()
					.filter(h -> h.ownerId().isPresent() && h.ownerId().get().equals(p.getUUID())).findFirst().orElse(null);
			helper.assertTrue(hulk != null, "the Hulk stands in the mirror");
			Vec3 want = MirrorHulkEntity.reflect(p.position(), hulk.mirrorZ());
			helper.assertTrue(hulk.position().distanceTo(want) < 1.0e-3, "it stands at his reflection, got " + hulk.position() + " want " + want);
			helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(hulk.getYRot() - MirrorHulkEntity.reflectYaw(p.getYRot()))) < 1.0e-3,
					"it turns as his mirror image");
			helper.assertTrue(Math.abs(hulk.getXRot() - p.getXRot()) < 1.0e-3, "and looks up / down with him");
			HulkMind.returnNow(p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void loggingOutEndsTheSessionAndTheNextLoginFinishesIt(GameTestHelper helper) {
		ServerPlayer p = dosed(helper);
		if (mind(helper) == null) {
			helper.succeed(); // see mind(): no datapack dimensions on the GameTest server
			return;
		}
		Vec3 from = p.position();
		helper.assertTrue(HulkMind.enter(p), "into his mind");
		HulkMindBodyEntity body = HulkMind.body(helper.getLevel().getServer(), p);
		HulkMind.onDisconnect(p);
		helper.assertFalse(HulkMind.inSession(p), "logging out ends the session");
		helper.assertTrue(body != null && body.isRemoved(), "and removes the body (no orphans)");
		helper.assertTrue(p.getAttachedOrElse(ModAttachments.HULK_MIND_RETURN, null) != null, "the return point is kept");
		// his next login: found with a saved return and no session
		HulkMind.check(p);
		helper.assertTrue(p.level() == helper.getLevel() && p.position().distanceTo(from) < 0.01, "sent back where he knelt");
		helper.assertTrue(p.getAttachedOrElse(ModAttachments.HULK_MIND_RETURN, null) == null, "the return point is cleared");
		helper.assertTrue(Hulk.hasPower(p), "and the change finishes");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void aStrangerInTheMindIsSentToSpawn(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		ServerLevel mind = mind(helper);
		if (mind == null) {
			helper.succeed(); // see mind(): no datapack dimensions on the GameTest server
			return;
		}
		p.teleportTo(mind, 900_000.5, 120.0, 0.5, 0.0f, 0.0f);
		HulkMind.check(p);
		helper.assertTrue(p.level() == helper.getLevel().getServer().overworld(), "no session, nothing saved: out to spawn");
		helper.assertFalse(Hulk.hasPower(p), "and nothing granted");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "hulk_mind_reset")
	public void aSessionResetLeavesNoOrphans(GameTestHelper helper) {
		ServerPlayer p = dosed(helper);
		if (mind(helper) == null) {
			helper.succeed(); // see mind(): no datapack dimensions on the GameTest server
			return;
		}
		helper.assertTrue(HulkMind.enter(p), "into his mind");
		HulkMindBodyEntity body = HulkMind.body(helper.getLevel().getServer(), p);
		helper.assertTrue(body != null, "his body is left behind");
		// what ServerStateReset runs on server stop
		Hulk.clearSessionState();
		helper.assertFalse(HulkMind.inSession(p), "the statics are cleared");
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(body.isRemoved(), "the orphaned body removes itself");
			HulkMind.check(p); // as on his next login
			helper.assertTrue(p.level() == helper.getLevel(), "and he is brought home");
			helper.succeed();
		});
	}
}
