package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.UniquePowers;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.renarouge.RenaRougeConfig;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.benwolf.BenwolfKit;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.zerogravity.ZeroGravityConfig;
import com.projecthero.mod.silversurfer.SilverSurfer;
import com.projecthero.mod.silversurfer.SilverSurferAbilities;
import com.projecthero.mod.silversurfer.SilverSurferAttachments;
import com.projecthero.mod.silversurfer.data.SilverSurferState;
import com.projecthero.mod.stand.StandMove;
import com.projecthero.mod.stand.StandType;
import com.projecthero.mod.stand.Stands;
import com.projecthero.mod.stand.data.StandState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 (user: "if i use shift+R on symbiote i cant use regular R while Shift+R is on cooldown ... fix it for every power"): a key's
 * tap, Shift and hold moves each keep their own cooldown. For the Omnitrix, Stand, Quirk, Miraculous and Undead-family powers that is
 * the storage itself (one entry per move id); these tests keep it that way: every alien key's variants are separate entries, and for a
 * sample of kits the Shift move on cooldown does not stop the plain move (and the other way round), pressed through the real router.
 */
public class SplitCooldownGameTests implements FabricGameTest {
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

	/** A press and its release through the real router, Shift held or not. */
	private static void tap(ServerPlayer p, AbilitySlot slot, boolean shift) {
		p.setShiftKeyDown(shift);
		AbilityRouter.handleInput(p, slot.number(), true);
		AbilityRouter.handleInput(p, slot.number(), false);
		p.setShiftKeyDown(false);
	}

	// ---------------------------------------------------------------- Omnitrix

	/** Every alien: each key's tap / Shift / hold moves are separate cooldown entries -- one cooling leaves the others ready. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyAlienKeyVariantHasItsOwnCooldown(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 2.5, 2, 2.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		List<String> bad = new ArrayList<>();
		int pairs = 0;
		for (Aliens.Alien a : Aliens.all()) {
			AlienKit kit = Aliens.kit(a.id());
			if (kit == null) {
				continue;
			}
			for (AbilitySlot slot : AbilitySlot.values()) {
				List<AlienMove> on = kit.moves().stream().filter(m -> m.slot() == slot).toList();
				for (AlienMove m : on) {
					kit.cooldown(p, m.id(), 200);
					for (AlienMove o : on) {
						if (o == m) {
							continue;
						}
						pairs++;
						if (kit.key(o.id()).equals(kit.key(m.id())) || kit.cooldownLeft(p, o.id()) > 0) {
							bad.add(a.id() + ": " + m.id() + " -> " + o.id());
						}
					}
					kit.cooldown(p, m.id(), 0);
				}
			}
		}
		helper.assertTrue(pairs > 100, "the kits declare their key variants (" + pairs + " pairs)");
		helper.assertTrue(bad.isEmpty(), "a move's cooldown blocks another move on its key: " + bad);
		helper.succeed();
	}

	/** Benwolf: Shift+Z (Moon Howl) on cooldown, a Z tap still fires the Shockwave. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void benwolfZWorksWhileShiftZCools(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		Aliens.discover(p, BenwolfKit.ID, true);
		helper.assertTrue(Omnitrix.select(p, BenwolfKit.ID) && Omnitrix.transform(p), "transformed into Benwolf");
		BenwolfKit kit = (BenwolfKit) Aliens.kit(BenwolfKit.ID);
		helper.runAfterDelay(2, () -> {
			kit.cooldown(p, BenwolfKit.MOON, -1);
			kit.press(p, AbilitySlot.SLOT_4, false); // the tap: the uncharged Shockwave
			helper.assertTrue(kit.cooldownLeft(p, BenwolfKit.SHOCKWAVE) > 0, "Z fired while Shift+Z cools");
			helper.assertTrue(kit.cooldownLeft(p, BenwolfKit.MOON) > 0, "Shift+Z still cooling");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Stand

	private static ServerPlayer standUser(GameTestHelper helper, String cooling, int ticks) {
		ServerPlayer p = mock(helper, 2.5, 2.0, 2.5);
		Stands.grant(p, StandType.STAR_PLATINUM);
		Stands.summon(p);
		StandState n = Stands.state(p).copy();
		n.abilityReadyAt.clear();
		n.abilityReadyAt.put(cooling, p.level().getGameTime() + ticks);
		p.setAttached(ModAttachments.STAND_STATE, n);
		return p;
	}

	/** Star Platinum: Shift+X (Ora Rush) cooling, X is still the Stand Jump -- and the other way round. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void standXAndShiftXCoolApart(GameTestHelper helper) {
		ServerPlayer a = standUser(helper, StandMove.ORA_RUSH, 400);
		tap(a, AbilitySlot.SLOT_3, false);
		helper.assertTrue(Stands.cooldownRemaining(a, StandMove.STAND_JUMP) > 0, "X jumped while Shift+X cools");
		ServerPlayer b = standUser(helper, StandMove.STAND_JUMP, 400);
		tap(b, AbilitySlot.SLOT_3, true);
		helper.assertTrue(Stands.cooldownRemaining(b, StandMove.ORA_RUSH) > 0, "Shift+X rushed while X cools");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Silver Surfer

	private static ServerPlayer surfer(GameTestHelper helper, String cooling) {
		ServerPlayer p = mock(helper, 3.5, 2.0, 1.0);
		SilverSurfer.grant(p);
		SilverSurferState n = SilverSurfer.state(p).copy();
		n.animId = SilverSurferState.ANIM_NONE;
		n.abilityReadyAt.clear();
		n.abilityReadyAt.put(cooling, p.level().getGameTime() + 400);
		p.setAttached(SilverSurferAttachments.STATE, n);
		p.removeAllEffects();
		return p;
	}

	/** The Surfer: Light-Speed cooling, X still dashes -- and the other way round. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void surferXAndShiftXCoolApart(GameTestHelper helper) {
		ServerPlayer a = surfer(helper, SilverSurferAbilities.LIGHT_SPEED);
		tap(a, AbilitySlot.SLOT_3, false);
		helper.assertTrue(SilverSurfer.cooldownRemaining(a, SilverSurferAbilities.DASH) > 0, "X dashed while Shift+X cools");
		ServerPlayer b = surfer(helper, SilverSurferAbilities.DASH);
		tap(b, AbilitySlot.SLOT_3, true);
		helper.assertTrue(SilverSurfer.cooldownRemaining(b, SilverSurferAbilities.LIGHT_SPEED) > 0, "Shift+X went to Light-Speed while X cools");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Miraculous

	private static ServerPlayer rena(GameTestHelper helper, String cooling) {
		ServerPlayer p = mock(helper, 3.5, 2.0, 3.5);
		helper.assertTrue(UniquePowers.forced(() -> Miraculous.grant(p, Kwami.TRIXX)), "Trixx is granted");
		Miraculous.transform(p);
		long now = helper.getLevel().getGameTime();
		Miraculous.edit(p, n -> {
			n.formChangeAt = now - 200L;
			n.animId = MiraculousState.ANIM_NONE;
			n.abilityReadyAt.clear();
		});
		helper.assertTrue(Miraculous.canAct(p), "the transformation has finished");
		Miraculous.setCooldown(p, cooling, 400);
		return p;
	}

	/** Rena Rouge: Shift+R (Flute Sweep) cooling, R still strikes -- and the other way round. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void renaRAndShiftRCoolApart(GameTestHelper helper) {
		ServerPlayer a = rena(helper, RenaRougeConfig.SWEEP);
		tap(a, AbilitySlot.SLOT_1, false);
		helper.assertTrue(Miraculous.cooldownRemaining(a, RenaRougeConfig.STRIKE) > 0, "R struck while Shift+R cools");
		ServerPlayer b = rena(helper, RenaRougeConfig.STRIKE);
		tap(b, AbilitySlot.SLOT_1, true);
		helper.assertTrue(Miraculous.cooldownRemaining(b, RenaRougeConfig.SWEEP) > 0, "Shift+R swept while R cools");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Quirks

	/** Zero Gravity: Shift+X (Zero-G Push) and hold X (Float Self) cooling, an X tap is still the Leap. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void zeroGravityXTapWorksWhileShiftAndHoldCool(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2.0, 3.5);
		helper.assertTrue(UniquePowers.forced(() -> Quirks.grant(p, Quirk.ZERO_GRAVITY)), "Zero Gravity is granted");
		Quirks.setMeter(p, 0f);
		Quirks.edit(p, n -> {
			n.animId = QuirkState.ANIM_NONE;
			n.abilityReadyAt.clear();
		});
		Quirks.setCooldown(p, ZeroGravityConfig.PUSH, 400);
		Quirks.setCooldown(p, ZeroGravityConfig.SELF, 400);
		tap(p, AbilitySlot.SLOT_3, false);
		helper.assertTrue(Quirks.cooldownRemaining(p, ZeroGravityConfig.LEAP) > 0, "the X tap leapt while Shift+X and hold X cool");
		helper.succeed();
	}
}
