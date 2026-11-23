package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.alarm.AlarmDirector;
import com.projecthero.mod.alarm.AlarmGuards;
import com.projecthero.mod.alarm.AlarmKeycards;
import com.projecthero.mod.alarm.AlarmProfile;
import com.projecthero.mod.alarm.AlarmProfiles;
import com.projecthero.mod.alarm.AlarmRoles;
import com.projecthero.mod.alarm.AlarmSabotage;
import com.projecthero.mod.alarm.AlarmState;
import com.projecthero.mod.alarm.AlarmVision;
import com.projecthero.mod.alarm.AlarmZone;
import com.projecthero.mod.alarm.AlarmZones;
import com.projecthero.mod.alarm.GuardRole;
import com.projecthero.mod.alarm.block.AlarmBlocks;
import com.projecthero.mod.alarm.block.AlarmLampBlock;
import com.projecthero.mod.alarm.block.AlarmPanelBlock;
import com.projecthero.mod.alarm.block.BlastDoorBlock;
import com.projecthero.mod.alarm.block.LaserTripwireBlock;
import com.projecthero.mod.hideout.HideoutEntityTypes;
import com.projecthero.mod.hideout.boss.ForgeOverseerBoss;
import com.projecthero.mod.hideout.boss.FrostGiantJarlBoss;
import com.projecthero.mod.hideout.boss.HideoutPhase;
import com.projecthero.mod.hideout.boss.HiveMotherBoss;
import com.projecthero.mod.hideout.boss.KingpinEnforcerBoss;
import com.projecthero.mod.hideout.boss.ParademonCommanderBoss;
import com.projecthero.mod.loot.HeroVault;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 alarm & stealth framework: suspicion step sizes and the alert, raising the alarm (lamps, Blast Doors, waves)
 * and calming down, panel sabotage over real ticks (and its wired sensor dying), keycard doors, the Ghost bonus, the
 * guard roles (Shield-bearer, Captain aura, Medic, Runner) and every mini-boss's second phase.
 *
 * <p>Each test registers its own zone over the test area with a test profile and removes it at the end. Mock players
 * from other tests may linger, so nothing asserts "no player near"; time-based checks assert step sizes.
 */
public class AlarmGameTests implements FabricGameTest {
	static final String PROFILE = "test/alarm";

	private static void ensureProfile() {
		if (AlarmProfiles.get(PROFILE) == null) {
			AlarmProfiles.register(new AlarmProfile(PROFILE, 2, 0xFF4040, Component.literal("Test Site"), (level, spot, random) -> {
				Mob m = EntityType.HUSK.create(level);
				if (m == null) {
					return null;
				}
				m.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0f, 0f);
				level.addFreshEntity(m);
				return m;
			}, SoundEvents.BELL_BLOCK, 1.2f));
		}
	}

	private static AlarmZone zone(GameTestHelper helper, int dx) {
		ensureProfile();
		BlockPos a = helper.absolutePos(new BlockPos(dx - 2, 0, -2));
		BlockPos b = helper.absolutePos(new BlockPos(dx + 10, 8, 10));
		BoundingBox box = BoundingBox.fromCorners(a, b);
		AlarmZones.get(helper.getLevel()).removeWithin(box);
		return AlarmZones.get(helper.getLevel()).register(box, PROFILE);
	}

	private static void drop(GameTestHelper helper, AlarmZone z) {
		AlarmZones.get(helper.getLevel()).remove(z.id);
	}

	/**
	 * A survival player (not {@code makeMockServerPlayerInLevel}: vanilla's mock player always answers {@code isCreative()}
	 * true, and guards, cameras and lasers ignore creative players). Invulnerable. Remove it with {@link #leave}.
	 */
	private static ServerPlayer mock(GameTestHelper helper, BlockPos rel) {
		ServerLevel level = helper.getLevel();
		net.minecraft.server.network.CommonListenerCookie cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
				new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "alarm-tester"), false);
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
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(rel));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		p.getAbilities().invulnerable = true;
		return p;
	}

	private static void leave(GameTestHelper helper, ServerPlayer p) {
		helper.getLevel().getServer().getPlayerList().remove(p);
	}

	private static Mob guard(GameTestHelper helper, BlockPos rel, float yaw, GuardRole role, boolean ai) {
		ServerLevel level = helper.getLevel();
		Mob m = EntityType.HUSK.create(level);
		BlockPos p = helper.absolutePos(rel);
		m.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, 0f);
		m.setYHeadRot(yaw);
		m.setYBodyRot(yaw);
		m.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.STRUCTURE, null);
		m.setNoAi(!ai);
		AlarmGuards.makeGuard(m);
		if (role != null) {
			AlarmGuards.setRole(m, role);
		}
		level.addFreshEntity(m);
		return m;
	}

	// ---------------------------------------------------------------- suspicion

	@GameTest(template = EMPTY_STRUCTURE, batch = "alarm_suspicion", timeoutTicks = 60)
	public void suspicionFillsInStepsThenAlerts(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		AlarmZone z = zone(helper, 0);
		Mob g = guard(helper, new BlockPos(4, 2, 2), 0f, null, false);
		ServerPlayer p = mock(helper, new BlockPos(4, 2, 6));
		try {
			float rate = AlarmVision.guardRate(level, g, p);
			helper.assertTrue(rate > 0f, "a guard sees a player 4 blocks in front of it (dist " + g.distanceTo(p) + ", range "
					+ AlarmVision.range(level, p, AlarmVision.GUARD_RANGE) + ", head " + g.getYHeadRot() + ", los " + g.hasLineOfSight(p) + ", g "
					+ g.position() + ", p " + p.position() + ", creative " + p.isCreative() + ")");
			AlarmDirector.stepNow(level, z);
			float m1 = z.suspicion(g);
			AlarmDirector.stepNow(level, z);
			float m2 = z.suspicion(g);
			helper.assertTrue(Math.abs(m1 - rate * AlarmDirector.STEP) < 1.0e-4f, "first step fills rate x STEP (" + m1 + ")");
			helper.assertTrue(Math.abs((m2 - m1) - rate * AlarmDirector.STEP) < 1.0e-4f, "each step adds the same amount (" + (m2 - m1) + ")");
			helper.assertTrue(z.state == AlarmState.SUSPICIOUS, "a filling meter makes the zone Suspicious");
			// the cone: straight ahead yes, behind no
			Vec3 eye = g.getEyePosition();
			helper.assertTrue(AlarmVision.inCone(eye, new Vec3(0, 0, 1), eye.add(0, 0, 5), AlarmVision.GUARD_CONE), "ahead is in the cone");
			helper.assertFalse(AlarmVision.inCone(eye, new Vec3(0, 0, 1), eye.add(0, 0, -5), AlarmVision.GUARD_CONE), "behind is not");
			helper.assertFalse(AlarmVision.inCone(eye, new Vec3(0, 0, 1), eye.add(5, 0, 1), AlarmVision.GUARD_CONE), "far to the side is not");
			for (int i = 0; i < 40 && !z.isAlerted(g); i++) {
				AlarmDirector.stepNow(level, z);
			}
			helper.assertTrue(z.isAlerted(g), "a full meter alerts the guard");
			helper.assertTrue(g.getTarget() == p, "the alerted guard targets the player it saw");
			helper.assertTrue(z.state == AlarmState.SUSPICIOUS, "no runner, no sensor: still not Alert");
			helper.assertTrue(AlarmVision.fillRate(2, 16) > AlarmVision.fillRate(12, 16), "closer fills faster");
		} finally {
			g.discard();
			leave(helper, p);
			drop(helper, z);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- raising, waves, calming

	@GameTest(template = EMPTY_STRUCTURE, batch = "alarm_raise", timeoutTicks = 60)
	public void raiseShutsDoorsLightsLampsAndCalms(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(2, 5, 2), AlarmBlocks.LAMP.defaultBlockState());
		helper.setBlock(new BlockPos(6, 2, 6), AlarmBlocks.BLAST_DOOR.defaultBlockState().setValue(BlastDoorBlock.OPEN, true));
		helper.setBlock(new BlockPos(6, 3, 6), AlarmBlocks.BLAST_DOOR.defaultBlockState().setValue(BlastDoorBlock.OPEN, true));
		helper.setBlock(new BlockPos(1, 2, 6), AlarmBlocks.BLAST_DOOR.defaultBlockState().setValue(BlastDoorBlock.LOCKED, true));
		helper.setBlock(new BlockPos(4, 2, 3), AlarmBlocks.SPAWN_POINT.defaultBlockState());
		AlarmZone z = zone(helper, 0);
		ServerPlayer p = mock(helper, new BlockPos(4, 2, 7));
		List<Mob> made = new ArrayList<>();
		try {
			AlarmZones.scan(level, z);
			helper.assertTrue(z.lamps.size() == 1 && z.doors.size() == 3 && z.spawns.size() == 1, "the scan files lamp / doors / spawn point");
			helper.assertTrue(AlarmDirector.raise(level, z, AlarmDirector.Cause.SCRIPT, null, null), "the alarm goes up");
			helper.assertFalse(AlarmDirector.raise(level, z, AlarmDirector.Cause.SCRIPT, null, null), "raising twice does nothing");
			helper.assertTrue(z.state == AlarmState.ALERT && z.everAlert, "Alert, and remembered");
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 5, 2)).getValue(AlarmLampBlock.LIT), "the lamp lights");
			helper.assertFalse(helper.getBlockState(new BlockPos(6, 2, 6)).getValue(BlastDoorBlock.OPEN), "an open Blast Door shuts");
			helper.assertTrue(helper.getBlockState(new BlockPos(1, 2, 6)).getValue(BlastDoorBlock.LOCKED), "a locked door stays locked");
			helper.assertTrue(z.waveMobCount() >= AlarmProfiles.waveSize(2, 1), "a wave of at least " + AlarmProfiles.waveSize(2, 1) + " (got " + z.waveMobCount() + ")");
			made.addAll(level.getEntitiesOfClass(Mob.class, z.aabb().inflate(6), m -> m.getTags().contains(AlarmGuards.WAVE_TAG)));
			for (Mob m : made) {
				m.discard();
			}
			AlarmDirector.stepNow(level, z);
			helper.assertTrue(z.wave == 2, "killing the wave brings the next one (wave " + z.wave + ")");
			helper.assertTrue(helper.getBlockState(new BlockPos(6, 2, 6)).getValue(BlastDoorBlock.OPEN), "a cleared wave opens the doors");
			helper.assertFalse(helper.getBlockState(new BlockPos(1, 2, 6)).getValue(BlastDoorBlock.LOCKED), "and forces the keycard door");
			AlarmDirector.calm(level, z);
			helper.assertTrue(z.state == AlarmState.CALM && z.everAlert, "calm again, but it did go off");
			helper.assertFalse(helper.getBlockState(new BlockPos(2, 5, 2)).getValue(AlarmLampBlock.LIT), "the lamp goes dark");
		} finally {
			for (Mob m : level.getEntitiesOfClass(Mob.class, z.aabb().inflate(8), m -> AlarmGuards.isGuard(m))) {
				m.discard();
			}
			leave(helper, p);
			drop(helper, z);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- sabotage over real ticks

	@GameTest(template = EMPTY_STRUCTURE, batch = "alarm_sabotage", timeoutTicks = 140)
	public void holdingUseSabotagesAPanelAndKillsItsSensors(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos panelRel = new BlockPos(2, 2, 4);
		BlockPos laserRel = new BlockPos(6, 2, 2);
		helper.setBlock(panelRel, AlarmBlocks.PANEL.defaultBlockState().setValue(AlarmPanelBlock.FACING, Direction.EAST));
		helper.setBlock(laserRel, AlarmBlocks.LASER.defaultBlockState().setValue(LaserTripwireBlock.FACING, Direction.UP));
		AlarmZone z = zone(helper, 0);
		AlarmZones.scan(level, z);
		ServerPlayer p = mock(helper, new BlockPos(3, 2, 4));
		BlockPos panel = helper.absolutePos(panelRel);
		BlockPos laser = helper.absolutePos(laserRel);
		helper.assertTrue(z.sensorLive(level, laser), "the laser is live while its panel is");
		int[] last = { -1 };
		int[] uses = { 0 };
		for (int i = 0; i < 20; i++) {
			helper.runAtTickTime(2 + i * 4, () -> {
				BlockState s = level.getBlockState(panel);
				if (s.getValue(AlarmPanelBlock.SABOTAGED)) {
					return;
				}
				int prog = AlarmSabotage.use(level, panel, s, p);
				if (last[0] >= 0 && prog < AlarmSabotage.HOLD_TICKS) {
					helper.assertTrue(prog - last[0] == 4, "each use 4 ticks apart adds 4 (" + last[0] + " -> " + prog + ")");
				}
				last[0] = prog;
				uses[0]++;
			});
		}
		helper.runAtTickTime(90, () -> {
			try {
				helper.assertTrue(level.getBlockState(panel).getValue(AlarmPanelBlock.SABOTAGED), "3 s of holding sabotages the panel (" + uses[0] + " uses)");
				helper.assertFalse(z.sensorLive(level, laser), "the laser wired to it is dead");
				// a second panel, and the zone on alert: refused
				helper.setBlock(new BlockPos(5, 2, 6), AlarmBlocks.PANEL.defaultBlockState());
				BlockPos other = helper.absolutePos(new BlockPos(5, 2, 6));
				z.state = AlarmState.ALERT;
				helper.assertTrue(AlarmSabotage.use(level, other, level.getBlockState(other), p) == -1, "no sabotage during an Alert");
				z.state = AlarmState.CALM;
			} finally {
				leave(helper, p);
				drop(helper, z);
			}
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- keycards

	@GameTest(template = EMPTY_STRUCTURE, batch = "alarm_keycard", timeoutTicks = 40)
	public void keycardOpensOnlyItsOwnPlacesDoor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(4, 2, 4), AlarmBlocks.BLAST_DOOR.defaultBlockState().setValue(BlastDoorBlock.LOCKED, true));
		helper.setBlock(new BlockPos(4, 3, 4), AlarmBlocks.BLAST_DOOR.defaultBlockState().setValue(BlastDoorBlock.LOCKED, true));
		AlarmZone here = zone(helper, 0);
		AlarmZone elsewhere = zone(helper, 40);
		ServerPlayer p = mock(helper, new BlockPos(4, 2, 2));
		BlockPos door = helper.absolutePos(new BlockPos(4, 2, 4));
		try {
			AlarmKeycards.useOnDoor(level, door, AlarmKeycards.create(elsewhere), p);
			helper.assertTrue(level.getBlockState(door).getValue(BlastDoorBlock.LOCKED), "another place's keycard is refused");
			ItemStack card = AlarmKeycards.create(here);
			helper.assertTrue(AlarmKeycards.zoneOf(card) == here.id, "the card names its zone");
			AlarmKeycards.useOnDoor(level, door, card, p);
			BlockState low = level.getBlockState(door);
			BlockState high = level.getBlockState(door.above());
			helper.assertTrue(!low.getValue(BlastDoorBlock.LOCKED) && low.getValue(BlastDoorBlock.OPEN), "the right keycard unlocks and opens it");
			helper.assertTrue(high.getValue(BlastDoorBlock.OPEN), "the whole connected door opens");
		} finally {
			leave(helper, p);
			drop(helper, here);
			drop(helper, elsewhere);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Ghost bonus

	@GameTest(template = EMPTY_STRUCTURE, batch = "alarm_ghost", timeoutTicks = 40)
	public void ghostBonusOnlyWhenTheAlarmNeverWentOff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		AlarmZone quiet = zone(helper, 0);
		AlarmZone loud = zone(helper, 40);
		loud.everAlert = true;
		BlockPos v1 = helper.absolutePos(new BlockPos(4, 2, 4));
		BlockPos v2 = helper.absolutePos(new BlockPos(44, 2, 4));
		HeroVault.place(level, v1, 2, Direction.NORTH);
		HeroVault.place(level, v2, 2, Direction.NORTH);
		ServerPlayer p = mock(helper, new BlockPos(4, 2, 2));
		try {
			helper.assertTrue(HeroVault.open(level, v1, p).result() == HeroVault.OpenResult.OPENED, "the quiet vault opens");
			helper.assertTrue(quiet.ghosts.contains(p.getUUID()), "a never-alerted place gives the Ghost bonus");
			helper.assertTrue(HeroVault.open(level, v2, p).result() == HeroVault.OpenResult.OPENED, "the loud vault opens");
			helper.assertFalse(loud.ghosts.contains(p.getUUID()), "a place that went off gives none");
		} finally {
			level.removeBlock(v1, false);
			level.removeBlock(v2, false);
			leave(helper, p);
			drop(helper, quiet);
			drop(helper, loud);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- roles

	@GameTest(template = EMPTY_STRUCTURE, batch = "alarm_roles", timeoutTicks = 40)
	public void shieldCaptainAndMedicRoles(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		AlarmZone z = zone(helper, 0);
		Mob shield = guard(helper, new BlockPos(4, 2, 4), 0f, GuardRole.SHIELD, false); // faces +z
		ServerPlayer front = mock(helper, new BlockPos(4, 2, 7));
		ServerPlayer back = mock(helper, new BlockPos(4, 2, 1));
		Mob captain = guard(helper, new BlockPos(1, 2, 1), 0f, GuardRole.CAPTAIN, false);
		Mob near = guard(helper, new BlockPos(2, 2, 1), 0f, null, false);
		Mob medic = guard(helper, new BlockPos(7, 2, 1), 0f, GuardRole.MEDIC, false);
		try {
			helper.assertTrue(AlarmGuards.shieldUp(shield), "the Shield-bearer holds a shield");
			float f = AlarmRoles.modifyIncoming(shield, level.damageSources().playerAttack(front), 6f);
			float b = AlarmRoles.modifyIncoming(shield, level.damageSources().playerAttack(back), 6f);
			helper.assertTrue(Math.abs(f - 6f * AlarmRoles.SHIELD_FACTOR) < 1.0e-4f, "70% blocked from the front (" + f + ")");
			helper.assertTrue(Math.abs(b - 6f) < 1.0e-4f, "nothing blocked from behind (" + b + ")");
			front.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
			AlarmRoles.modifyIncoming(shield, level.damageSources().playerAttack(front), 6f);
			helper.assertFalse(AlarmGuards.shieldUp(shield), "an axe knocks the shield away");
			helper.assertTrue(Math.abs(AlarmRoles.modifyIncoming(shield, level.damageSources().playerAttack(front), 6f) - 6f) < 1.0e-4f,
					"no shield, no block");
			float boosted = AlarmRoles.modifyIncoming(back, level.damageSources().mobAttack(near), 5f);
			helper.assertTrue(Math.abs(boosted - 5f * AlarmRoles.CAPTAIN_BONUS) < 1.0e-4f, "a guard by the Captain hits 20% harder (" + boosted + ")");
			helper.assertTrue(captain.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET), "the Captain wears gold");
			captain.discard();
			float plain = AlarmRoles.modifyIncoming(back, level.damageSources().mobAttack(near), 5f);
			helper.assertTrue(Math.abs(plain - 5f) < 1.0e-4f, "no living Captain, no aura (" + plain + ")");
			AlarmZones.scan(level, z);
			AlarmDirector.stepNow(level, z);
			near.setHealth(4f);
			Mob healed = AlarmRoles.heal(level, z, medic);
			helper.assertTrue(healed == near && Math.abs(near.getHealth() - (4f + AlarmRoles.MEDIC_HEAL)) < 1.0e-3f, "the Medic heals the most-hurt guard by "
					+ AlarmRoles.MEDIC_HEAL);
		} finally {
			for (Mob m : new Mob[] { shield, captain, near, medic }) {
				m.discard();
			}
			leave(helper, front);
			leave(helper, back);
			drop(helper, z);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "alarm_runner", timeoutTicks = 300)
	public void runnerReachingAPanelRaisesTheAlarm(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(6, 2, 6), AlarmBlocks.PANEL.defaultBlockState());
		AlarmZone z = zone(helper, 0);
		Mob runner = guard(helper, new BlockPos(1, 2, 1), 0f, GuardRole.RUNNER, true);
		Mob spotter = guard(helper, new BlockPos(2, 2, 6), 180f, null, false);
		ServerPlayer p = mock(helper, new BlockPos(2, 2, 3));
		AlarmZones.scan(level, z);
		AlarmDirector.stepNow(level, z);
		AlarmDirector.alert(level, z, spotter, p, false);
		helper.succeedWhen(() -> {
			AlarmDirector.stepNow(level, z);
			helper.assertTrue(z.state == AlarmState.ALERT, "waiting for the runner to reach the panel");
			helper.assertTrue(runner.getTarget() == null || runner.getTarget() == p, "the runner ran instead of fighting");
			for (Mob m : level.getEntitiesOfClass(Mob.class, z.aabb().inflate(8), m -> AlarmGuards.isGuard(m))) {
				m.discard();
			}
			leave(helper, p);
			drop(helper, z);
		});
	}

	// ---------------------------------------------------------------- mini-boss second phases

	@GameTest(template = EMPTY_STRUCTURE, batch = "alarm_boss_phases", timeoutTicks = 60)
	public void everyMiniBossHasASecondPhase(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = mock(helper, new BlockPos(4, 2, 1));
		List<Mob> bosses = new ArrayList<>();
		try {
			int i = 0;
			for (EntityType<? extends Mob> type : HideoutEntityTypes.bosses()) {
				Mob boss = type.create(level);
				BlockPos at = helper.absolutePos(new BlockPos(4 + i * 12, 2, 6));
				boss.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180f, 0f);
				level.addFreshEntity(boss);
				bosses.add(boss);
				boss.setTarget(p);
				HideoutPhase phase = phaseOf(boss);
				helper.assertTrue(phase != null, type + ": has a phase");
				helper.assertFalse(phase.active(), type + ": not at full health");
				phase.enter(level);
				helper.assertTrue(phase.active(), type + ": enters phase 2");
				helper.assertTrue(((com.projecthero.mod.hideout.HideoutBoss) boss).hideoutLink().bar().getColor() == BossEvent.BossBarColor.RED,
						type + ": the bar turns red");
				if (boss instanceof KingpinEnforcerBoss k) {
					k.startRush();
					helper.assertTrue(k.rushesLeftForTest() == 3, "the Enforcer starts a three-charge Bull Rush");
				} else {
					helper.assertTrue(phase.startAttack(level) > 0, type + ": the phase attack places telegraphed strikes");
				}
				i++;
			}
		} finally {
			for (Mob b : bosses) {
				b.discard();
			}
			for (Mob m : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(80), AlarmGuards::isGuard)) {
				m.discard();
			}
			leave(helper, p);
		}
		helper.succeed();
	}

	private static HideoutPhase phaseOf(Mob boss) {
		if (boss instanceof KingpinEnforcerBoss b) {
			return b.phase();
		}
		if (boss instanceof ForgeOverseerBoss b) {
			return b.phase();
		}
		if (boss instanceof ParademonCommanderBoss b) {
			return b.phase();
		}
		if (boss instanceof HiveMotherBoss b) {
			return b.phase();
		}
		if (boss instanceof FrostGiantJarlBoss b) {
			return b.phase();
		}
		if (boss instanceof com.projecthero.mod.hideout.HideoutBoss b) {
			return b.phase(); // v0.17.7: the three new mini-bosses
		}
		return null;
	}
}
