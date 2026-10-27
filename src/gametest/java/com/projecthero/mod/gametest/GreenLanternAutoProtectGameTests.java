package com.projecthero.mod.gametest;

import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternAutoProtect;
import com.projecthero.mod.greenlantern.GreenLanternAutoProtect.Save;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.GreenLanternEnergy;
import com.projecthero.mod.greenlantern.data.GreenLanternState;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Ring Auto-Protection: the Death Save (saves a lethal hit for 300 charge, then the cooldown; never on a short
 * ring; the burst shell), the Fall Cushion (fall damage cancelled, the pad before a landing), the Air Bubble, the Heat
 * Shell, the Blind-Side Guard, and every switch (and the master) turning its save off.
 *
 * <p>Damage goes through {@code ALLOW_DAMAGE.invoker()} directly: a mock player never ticks, so vanilla's spawn
 * invulnerability (and the other early returns in {@code Player#hurt}) could swallow the blow before any listener runs --
 * what is under test is the ring's decision. The Lantern faces +Z at half charge; the cage floor is stone at y 1.
 */
public class GreenLanternAutoProtectGameTests implements FabricGameTest {
	/**
	 * A survival player, not {@code helper.makeMockServerPlayerInLevel()}: vanilla's mock player overrides
	 * {@code isCreative()} to always answer true whatever its game mode, and Ring Auto-Protection (rightly) never runs for a
	 * creative player -- so every save looked dead. Like the mock it never ticks (no real connection); {@link #done} takes
	 * it back off the player list.
	 */
	private static ServerPlayer survivalPlayer(GameTestHelper helper) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		net.minecraft.server.network.CommonListenerCookie cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
				new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-lantern"), false);
		ServerPlayer p = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public boolean isSpectator() {
				return false;
			}

			@Override
			public boolean isCreative() {
				return false;
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
		p.setGameMode(GameType.SURVIVAL);
		return p;
	}

	private static void done(GameTestHelper helper, ServerPlayer p) {
		helper.getLevel().getServer().getPlayerList().remove(p);
		helper.succeed();
	}

	@SuppressWarnings("removal")
	private static ServerPlayer lantern(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerPlayer player = survivalPlayer(helper);
		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().invulnerable = false;
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(7, 1, 7)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
		player.moveTo(at.x, at.y, at.z, 0f, 0f);
		player.setYHeadRot(0f);
		GreenLantern.bond(player);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE * 0.5f;
		s.autoProtectOff = 0;
		GreenLantern.save(player, s);
		GreenLanternAutoProtect.resetCooldowns(player);
		player.setHealth(player.getMaxHealth());
		return player;
	}

	private static Husk husk(GameTestHelper helper, double x, double z) {
		Husk h = EntityType.HUSK.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		h.moveTo(at.x, at.y, at.z, 0f, 0f);
		h.setNoAi(true);
		helper.getLevel().addFreshEntity(h);
		return h;
	}

	private static boolean hit(ServerPlayer p, DamageSource source, float amount) {
		return ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(p, source, amount);
	}

	private static void off(ServerPlayer p, int bit) {
		GreenLanternAutoProtect.setEnabled(p, bit, false);
	}

	// ---------------------------------------------------------------- Death Save

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void aLethalHitIsSavedForThreeHundredThenTheCooldownHolds(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		float before = GreenLanternEnergy.get(p);
		DamageSource src = helper.getLevel().damageSources().generic();
		helper.assertFalse(hit(p, src, 40f), "a 40-damage hit on a 20 HP Lantern is stopped");
		helper.assertTrue(GreenLanternAutoProtect.lastSave(p) == Save.DEATH, "it was the Death Save, got " + GreenLanternAutoProtect.lastSave(p));
		float spent = before - GreenLanternEnergy.get(p);
		helper.assertTrue(Math.abs(spent - GreenLanternConfig.AUTO_DEATH_SAVE_COST) < 0.01f, "it cost 300 charge, cost " + spent);
		helper.assertTrue(GreenLantern.cooldownRemaining(p, GreenLanternAutoProtect.DEATH_SAVE_COOLDOWN) > 0, "the 60 s cooldown started");
		helper.assertTrue(GreenLanternAutoProtect.shellUp(p), "the burst shell is up");
		// the shell stops even a small follow-up hit
		helper.assertFalse(hit(p, src, 2f), "the shell blocks the next hit");
		helper.assertTrue(GreenLanternAutoProtect.lastSave(p) == Save.SHELL_BLOCK, "blocked by the shell");
		// but never the void
		helper.assertTrue(hit(p, helper.getLevel().damageSources().fellOutOfWorld(), 40f), "the void goes through the shell");
		helper.runAfterDelay(GreenLanternConfig.AUTO_DEATH_SAVE_SHELL_TICKS + 2, () -> {
			helper.assertFalse(GreenLanternAutoProtect.shellUp(p), "the shell is gone after a second");
			float mid = GreenLanternEnergy.get(p);
			helper.assertTrue(hit(p, src, 40f), "on cooldown, a second lethal hit is not saved");
			helper.assertTrue(GreenLanternEnergy.get(p) == mid, "and nothing was spent");
			done(helper, p);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void anAlmostLethalHitIsSavedTooButASmallOneIsNot(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		DamageSource src = helper.getLevel().damageSources().generic();
		helper.assertTrue(hit(p, src, 4f), "20 -> 16 HP: no save");
		helper.assertTrue(GreenLanternAutoProtect.lastSave(p) == null, "nothing fired");
		helper.assertFalse(hit(p, src, 15f), "20 -> 5 HP would leave him under 3 hearts: saved");
		helper.assertTrue(GreenLanternAutoProtect.lastSave(p) == Save.DEATH, "the Death Save");
		done(helper, p);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void noDeathSaveWhenTheRingIsShort(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		GreenLanternState s = GreenLantern.state(p).copy();
		s.ringCharge = GreenLanternConfig.EMERGENCY_RESERVE + GreenLanternConfig.AUTO_DEATH_SAVE_COST - 1f; // 299 spendable
		GreenLantern.save(p, s);
		float before = GreenLanternEnergy.get(p);
		helper.assertTrue(hit(p, helper.getLevel().damageSources().generic(), 40f), "an empty ring cannot protect: the hit lands");
		helper.assertTrue(GreenLanternAutoProtect.lastSave(p) != Save.DEATH, "no Death Save");
		helper.assertTrue(GreenLanternEnergy.get(p) == before, "nothing was spent");
		helper.assertTrue(GreenLantern.cooldownRemaining(p, GreenLanternAutoProtect.DEATH_SAVE_COOLDOWN) == 0, "no cooldown either");
		done(helper, p);
	}

	// ---------------------------------------------------------------- Fall Cushion

	@GameTest(template = EMPTY_STRUCTURE)
	public void fallDamageIsCancelled(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		p.fallDistance = 20f;
		helper.assertFalse(hit(p, helper.getLevel().damageSources().fall(), 17f), "fall damage is cancelled");
		helper.assertTrue(p.fallDistance == 0f, "and the fall is forgotten");
		done(helper, p);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theCushionPadCatchesAFallJustAboveTheGround(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 4.0, 3.5)); // 2 blocks over the stone (its top is y 2)
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		p.setOnGround(false);
		p.fallDistance = 10f;
		p.setDeltaMovement(0, -1.0, 0);
		float before = GreenLanternEnergy.get(p);
		GreenLanternAutoProtect.tick(p);
		helper.assertTrue(GreenLanternAutoProtect.lastSave(p) == Save.CUSHION, "the pad flashed, got " + GreenLanternAutoProtect.lastSave(p));
		float spent = before - GreenLanternEnergy.get(p);
		helper.assertTrue(Math.abs(spent - (20f + 2f * 10f)) < 0.01f, "20 + 2 a block fallen, spent " + spent);
		helper.assertTrue(p.getDeltaMovement().y >= -0.31, "the fall was braked, vy " + p.getDeltaMovement().y);
		helper.assertTrue(Math.abs(GreenLanternAutoProtect.fx(p).cushionY() - helper.absoluteVec(new Vec3(0, 2.0, 0)).y) < 0.01,
				"the pad sits on the stone");
		// 10 s cooldown on the pad
		p.setDeltaMovement(0, -1.0, 0);
		float mid = GreenLanternEnergy.get(p);
		GreenLanternAutoProtect.tick(p);
		helper.assertTrue(GreenLanternEnergy.get(p) == mid, "a second pad straight away waits for the cooldown");
		done(helper, p);
	}

	// ---------------------------------------------------------------- Air Bubble / Heat Shell

	@GameTest(template = EMPTY_STRUCTURE)
	public void theAirBubbleStopsDrowningAndRefillsAir(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		p.setAirSupply(0);
		helper.assertFalse(hit(p, helper.getLevel().damageSources().drown(), 2f), "drowning is stopped");
		helper.assertTrue(GreenLanternAutoProtect.lastSave(p) == Save.AIR, "by the Air Bubble");
		helper.assertTrue(p.getAirSupply() == p.getMaxAirSupply(), "the air refilled");
		helper.assertFalse(hit(p, helper.getLevel().damageSources().inWall(), 1f), "suffocating in a wall is stopped too");
		done(helper, p);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theHeatShellTakesThreeQuartersOffFire(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		helper.assertFalse(hit(p, helper.getLevel().damageSources().lava(), 4f), "the full lava hit is cancelled (and re-issued smaller)");
		helper.assertTrue(GreenLanternAutoProtect.lastSave(p) == Save.HEAT, "by the Heat Shell");
		helper.assertTrue(GreenLanternAutoProtect.fx(p).has(com.projecthero.mod.greenlantern.data.GreenLanternAutoProtectFx.HEAT),
				"the shell is up (synced for the render)");
		done(helper, p);
	}

	// ---------------------------------------------------------------- Blind-Side Guard

	@GameTest(template = EMPTY_STRUCTURE)
	public void aBlowFromBehindIsCutButOneFromTheFrontIsNot(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		Husk front = husk(helper, 3.5, 5.5);
		Husk behind = husk(helper, 3.5, 1.5);
		helper.assertTrue(hit(p, helper.getLevel().damageSources().mobAttack(front), 4f), "a blow from in front lands as it is");
		helper.assertTrue(GreenLanternAutoProtect.lastSave(p) == null, "no guard for it");
		helper.assertFalse(hit(p, helper.getLevel().damageSources().mobAttack(behind), 4f), "a blow from behind is re-issued 40% lighter");
		helper.assertTrue(GreenLanternAutoProtect.lastSave(p) == Save.BLIND, "by the Blind-Side Guard");
		helper.assertTrue(hit(p, helper.getLevel().damageSources().mobAttack(behind), 4f), "3 s cooldown: the next one lands");
		front.discard();
		behind.discard();
		done(helper, p);
	}

	// ---------------------------------------------------------------- switches

	@GameTest(template = EMPTY_STRUCTURE)
	public void eachSwitchTurnsItsSaveOff(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		Husk behind = husk(helper, 3.5, 1.5);
		var ds = helper.getLevel().damageSources();

		off(p, GreenLanternAutoProtect.DEATH_SAVE);
		helper.assertTrue(hit(p, ds.generic(), 40f), "Death Save off: the lethal hit lands");
		off(p, GreenLanternAutoProtect.FALL_CUSHION);
		helper.assertTrue(hit(p, ds.fall(), 10f), "Fall Cushion off: the fall hurts");
		off(p, GreenLanternAutoProtect.AIR_BUBBLE);
		helper.assertTrue(hit(p, ds.drown(), 2f), "Air Bubble off: drowning hurts");
		off(p, GreenLanternAutoProtect.HEAT_SHELL);
		helper.assertTrue(hit(p, ds.lava(), 4f), "Heat Shell off: lava at full");
		off(p, GreenLanternAutoProtect.BLIND_SIDE);
		helper.assertTrue(hit(p, ds.mobAttack(behind), 4f), "Blind-Side Guard off: the blow lands at full");
		helper.assertTrue(GreenLanternAutoProtect.lastSave(p) == null, "nothing fired at all");
		behind.discard();
		done(helper, p);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theMasterSwitchTurnsEverythingOff(GameTestHelper helper) {
		ServerPlayer p = lantern(helper);
		var ds = helper.getLevel().damageSources();
		helper.assertTrue(GreenLanternAutoProtect.toggle(p, GreenLanternAutoProtect.MASTER), "the suit screen's master switch");
		helper.assertTrue(hit(p, ds.generic(), 40f), "master off: no Death Save");
		helper.assertTrue(hit(p, ds.fall(), 10f), "master off: no Fall Cushion");
		helper.assertTrue(hit(p, ds.lava(), 4f), "master off: no Heat Shell");
		helper.assertTrue(GreenLanternAutoProtect.lastSave(p) == null, "nothing fired");
		// and back on: the saves' own switches were never touched
		GreenLanternAutoProtect.toggle(p, GreenLanternAutoProtect.MASTER);
		helper.assertFalse(hit(p, ds.generic(), 40f), "master on again: the Death Save is back");
		done(helper, p);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everySwitchStartsOnAndSurvivesTheCodec(GameTestHelper helper) {
		helper.assertTrue(new GreenLanternState().autoProtectOff == 0, "a new Lantern has every save on");
		GreenLanternState s = new GreenLanternState();
		s.autoProtectOff = GreenLanternAutoProtect.HEAT_SHELL | GreenLanternAutoProtect.BLIND_SIDE;
		var json = GreenLanternState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, s).getOrThrow();
		GreenLanternState back = GreenLanternState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(back.autoProtectOff == s.autoProtectOff, "the switches persist");
		helper.succeed();
	}
}
