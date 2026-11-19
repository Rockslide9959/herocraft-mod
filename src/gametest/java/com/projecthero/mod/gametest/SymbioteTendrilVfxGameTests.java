package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.network.SymbioteVfxPayload;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteAbilityManager;
import com.projecthero.mod.symbiote.SymbioteVfx;

import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 tendril-vfx round (user: "instead of spawning in particles for the symbiote powers can you make it the tentacle models and
 * vfx please, for example when player uses symbiote lunge ... symbiote tendril come off the players legs and push off the ground"):
 * the moves send their tendril effects ({@link SymbioteVfx}) -- the Lunge's leg push planted at the host's feet, a wrap on a grabbed
 * target, Maw Bite's jaws, impacts on hits -- and the effect payload round-trips. The drawing itself is client-side (screenshots in
 * {@code docs/screenshots/v0175/symbiote_tendril_vfx/}).
 */
public class SymbioteTendrilVfxGameTests implements FabricGameTest {

	private static ServerPlayer host(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 1.5));
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-3, 0, -1), base.offset(3, 5, 6))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-3, -1, -1), base.offset(3, -1, 6))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0);
		p.setOnGround(true);
		Symbiote.grant(p);
		Symbiote.state(p).toggleReadyAt = 0L;
		Symbiote.toggle(p);
		Symbiote.state(p).transformStartTick -= 10_000L;
		Symbiote.tick(p);
		return p;
	}

	private static IronGolem golem(GameTestHelper helper, double x, double z) {
		IronGolem g = helper.spawn(EntityType.IRON_GOLEM, new Vec3(x, 2, z));
		g.setPersistenceRequired();
		g.setNoAi(true);
		return g;
	}

	/** What this host sends (other tests run in the same batch, so only {@code p}'s own effects count). */
	private static List<SymbioteVfxPayload> listen(ServerPlayer p) {
		List<SymbioteVfxPayload> sent = java.util.Collections.synchronizedList(new ArrayList<>());
		int id = p.getId();
		SymbioteVfx.testListener = x -> {
			if (x.owner() == id) {
				sent.add(x);
			}
		};
		return sent;
	}

	private static boolean has(List<SymbioteVfxPayload> sent, int kind) {
		return sent.stream().anyMatch(p -> p.kind() == kind);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theLungePushesOffWithLegTendrils(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		Vec3 feet = p.position();
		List<SymbioteVfxPayload> sent = listen(p);
		try {
			SymbioteAbilityManager.handle(p, AbilitySlot.SLOT_3, true);
			SymbioteAbilityManager.handle(p, AbilitySlot.SLOT_3, false);
		} finally {
			SymbioteVfx.testListener = null;
		}
		SymbioteVfxPayload push = sent.stream().filter(x -> x.kind() == SymbioteVfx.LEG_PUSH).findFirst().orElse(null);
		helper.assertTrue(push != null, "X sends the leg push");
		helper.assertTrue(push.owner() == p.getId(), "from the host's legs");
		// (the launch is only a velocity: the host has not moved yet, so its position is still the launch spot)
		Vec3 stood = p.position();
		double off = new Vec3(push.x(), push.y(), push.z()).distanceTo(stood);
		helper.assertTrue(off < 0.6, "planted where the host stood: " + off + " (" + push.x() + ", " + push.y() + ", " + push.z() + " vs " + stood + ", before " + feet + ")");
		helper.assertTrue(new Vec3(push.dx(), push.dy(), push.dz()).lengthSqr() > 0.01, "carries the launch direction");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hitsGrabsAndBitesSendTendrils(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		IronGolem g = golem(helper, 3.5, 4.5);
		List<SymbioteVfxPayload> sent = listen(p);
		try {
			// R tap: Tendril Strike hits -> an impact on the golem
			SymbioteAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
			SymbioteAbilityManager.handle(p, AbilitySlot.SLOT_1, false);
			helper.assertTrue(sent.stream().anyMatch(x -> x.kind() == SymbioteVfx.IMPACT && x.target() == g.getId()), "Tendril Strike: impact on the target");
			// C tap: Maw Bite -> the jaws
			sent.clear();
			SymbioteAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
			SymbioteAbilityManager.handle(p, AbilitySlot.SLOT_6, false);
			helper.assertTrue(has(sent, SymbioteVfx.JAWS), "Maw Bite: jaw tendrils");
			// Shift+C: Tendril Grab -> a wrap on the golem
			sent.clear();
			p.setShiftKeyDown(true);
			SymbioteAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
			SymbioteAbilityManager.handle(p, AbilitySlot.SLOT_6, false);
			if (Symbiote.state(p).tendrilGrabHeld) {
				helper.assertTrue(sent.stream().anyMatch(x -> x.kind() == SymbioteVfx.WRAP && x.target() == g.getId()), "Tendril Grab: a wrap");
			}
			// Shift+V: the shield goes up with a body burst (the disc itself is drawn from the synced state)
			sent.clear();
			SymbioteAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
			helper.assertTrue(has(sent, SymbioteVfx.BODY_BURST) && SymbioteAbilityManager.shieldActive(p), "Shield: body burst");
		} finally {
			SymbioteVfx.testListener = null;
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theEffectPayloadRoundTrips(GameTestHelper helper) {
		SymbioteVfxPayload p = new SymbioteVfxPayload(SymbioteVfx.LEG_PUSH, 42, -1, 1.5f, 64f, -3.25f, 0.1f, 0.4f, 0.9f, 1.0f, 11);
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		SymbioteVfxPayload.CODEC.encode(buf, p);
		helper.assertTrue(SymbioteVfxPayload.CODEC.decode(buf).equals(p), "round-trips");
		SymbioteVfxPayload junk = new SymbioteVfxPayload(1, 0, -1, 0, 0, 0, 0, 0, 0, Float.NaN, 99999);
		helper.assertTrue(junk.size() == 1.0f && junk.duration() == 400, "junk size / duration clamped");
		helper.succeed();
	}
}
