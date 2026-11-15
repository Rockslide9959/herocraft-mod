package com.projecthero.mod.gametest;

import java.util.List;
import java.util.UUID;

import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventTypes;
import com.projecthero.mod.hideout.HideoutBoss;
import com.projecthero.mod.hideout.HideoutCalm;
import com.projecthero.mod.hideout.HideoutEntityTypes;
import com.projecthero.mod.hideout.HideoutFaction;
import com.projecthero.mod.hideout.HideoutGuards;
import com.projecthero.mod.hideout.HideoutState;
import com.projecthero.mod.hideout.block.HideoutBlocks;
import com.projecthero.mod.hideout.block.HideoutCoreBlock;
import com.projecthero.mod.hideout.worldgen.HideoutPiece;
import com.projecthero.mod.loot.HeroVault;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Tier 3 villain hideouts. For each faction: the hideout builds with its guards, mini-boss, traps, a sealed core
 * and a sealed vault; the core can't be broken while the boss lives; killing the boss + breaking the core clears it,
 * opens the Tier 3 vault and calms that faction's raids nearby (but not other factions', and not far away). Plus: every
 * mini-boss switches to the player who hurt it most (the shared threat table).
 *
 * <p>Each hideout is built 30 blocks above its test (in its own batch, so nothing else runs beside it) and everything it
 * placed or spawned is cleared before the test ends.
 */
public class VillainHideoutGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, Vec3 at) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		return p;
	}

	private static HideoutPiece build(GameTestHelper helper, HideoutFaction faction, Rotation rot) {
		BlockPos origin = helper.absolutePos(new BlockPos(4, 30, 4));
		HideoutState.get(helper.getLevel()).resetForTest();
		HideoutPiece piece = new HideoutPiece(faction, origin, rot);
		clear(helper.getLevel(), piece);
		piece.buildAll(helper.getLevel());
		return piece;
	}

	private static AABB area(HideoutPiece piece) {
		BoundingBox b = piece.getBoundingBox();
		return new AABB(b.minX(), b.minY() - 14, b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
	}

	/** Removes every block and non-player entity the hideout may have placed. */
	private static void clear(ServerLevel level, HideoutPiece piece) {
		AABB box = area(piece).inflate(2);
		for (Entity e : level.getEntitiesOfClass(Entity.class, box.inflate(8), e -> !(e instanceof Player))) {
			e.discard();
		}
		BlockPos.betweenClosedStream(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))
				.forEach(p -> {
					if (!level.getBlockState(p).isAir()) {
						level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
					}
				});
	}

	private static EventInstance raidOf(HideoutFaction faction) {
		String type = switch (faction) {
			case KINGPIN -> com.projecthero.mod.syndicate.SyndicateBust.TYPE_ID;
			case ULTRON -> com.projecthero.mod.ultron.UltronUprising.TYPE_ID;
			case APOKOLIPS -> com.projecthero.mod.darkseid.raid.DarkseidRaid.TYPE_ID;
			case FROST -> com.projecthero.mod.frostgiant.FrostGiantAssault.TYPE_ID;
			default -> null;
		};
		return type == null ? null : EventTypes.create(type, UUID.randomUUID());
	}

	/** The whole clearing flow for one faction. */
	private static void hideoutFlow(GameTestHelper helper, HideoutFaction faction, int minGuards) {
		ServerLevel level = helper.getLevel();
		HideoutPiece piece = build(helper, faction, Rotation.CLOCKWISE_90);
		try {
			BlockPos core = piece.corePos();
			BlockPos vault = piece.vaultPos();
			AABB box = area(piece);
			BlockState coreState = level.getBlockState(core);
			helper.assertTrue(coreState.is(HideoutBlocks.CORE) && coreState.getValue(HideoutCoreBlock.SEALED)
					&& coreState.getValue(HideoutCoreBlock.FACTION) == faction, faction + ": a sealed core of the right faction at " + core);
			helper.assertTrue(level.getBlockState(vault).is(HideoutBlocks.SEAL), faction + ": a sealed vault at " + vault);
			List<Mob> guards = level.getEntitiesOfClass(Mob.class, box, HideoutGuards::isGuard);
			helper.assertTrue(guards.size() >= minGuards, faction + ": at least " + minGuards + " guards (got " + guards.size() + ")");
			helper.assertTrue(guards.stream().allMatch(Mob::isPersistenceRequired), faction + ": guards never despawn");
			List<Mob> bosses = level.getEntitiesOfClass(Mob.class, box, m -> m instanceof HideoutBoss b && core.equals(b.hideoutLink().core()));
			helper.assertTrue(bosses.size() == 1, faction + ": exactly one mini-boss bound to the core (got " + bosses.size() + ")");
			int traps = 0;
			for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
				BlockState s = level.getBlockState(p);
				if (s.is(HideoutBlocks.ALARM) || s.is(HideoutBlocks.VENT) || s.is(Blocks.DISPENSER)) {
					traps++;
				}
			}
			helper.assertTrue(traps >= 2, faction + ": traps placed (got " + traps + ")");

			ServerPlayer player = mock(helper, Vec3.atCenterOf(core).add(1.5, 0, 0));
			helper.assertTrue(coreState.getDestroyProgress(player, level, core) == 0f, faction + ": the sealed core can't be broken");
			helper.assertFalse(HideoutState.get(level).cleared(core), faction + ": not cleared yet");

			Mob boss = bosses.get(0);
			boss.kill();
			BlockState open = level.getBlockState(core);
			helper.assertTrue(open.is(HideoutBlocks.CORE) && !open.getValue(HideoutCoreBlock.SEALED), faction + ": the boss's death drops the seal");
			helper.assertTrue(open.getDestroyProgress(player, level, core) > 0f, faction + ": the open core can be broken");
			helper.assertTrue(HideoutState.get(level).bossDead(core), faction + ": the boss's death is recorded");

			helper.assertTrue(player.gameMode.destroyBlock(core), faction + ": the player breaks the core");
			helper.assertTrue(HideoutState.get(level).cleared(core), faction + ": cleared after the core breaks");
			helper.assertTrue(HeroVault.tierAt(level, vault) == 3, faction + ": the sealed vault is now an open Tier 3 Hero Vault");
			HeroVault.Opened opened = HeroVault.open(level, vault, player);
			helper.assertTrue(opened.result() == HeroVault.OpenResult.OPENED, faction + ": the vault opens for the player");

			helper.assertTrue(HideoutCalm.calmed(level, faction, core.offset(300, 0, 0)), faction + ": raids calmed 300 blocks away");
			helper.assertFalse(HideoutCalm.calmed(level, faction, core.offset(700, 0, 0)), faction + ": not calmed 700 blocks away");
			HideoutFaction other = faction == HideoutFaction.KINGPIN ? HideoutFaction.ULTRON : HideoutFaction.KINGPIN;
			helper.assertFalse(HideoutCalm.calmed(level, other, core), faction + ": another faction is not calmed");
			EventInstance raid = raidOf(faction);
			if (raid != null) {
				helper.assertTrue(HideoutCalm.refuses(level, raid, core.offset(100, 0, 0)), faction + ": its raid start is refused nearby");
				helper.assertFalse(com.projecthero.mod.event.EventManager.start(level, raid, core.offset(100, 0, 0)),
						faction + ": EventManager.start refuses it");
			}
			// the calm runs out after five days
			HideoutState.Entry e = HideoutState.get(level).at(core);
			helper.assertTrue(e.calmLeft(e.clearedAt + HideoutState.CALM_TICKS - 1) > 0 && e.calmLeft(e.clearedAt + HideoutState.CALM_TICKS) == 0,
					faction + ": the calm lasts exactly five days");
			player.discard();
		} finally {
			clear(level, piece);
			HideoutState.get(level).resetForTest();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "hideout_kingpin", timeoutTicks = 200)
	public void kingpinsFrontClears(GameTestHelper helper) {
		hideoutFlow(helper, HideoutFaction.KINGPIN, 10);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "hideout_ultron", timeoutTicks = 200)
	public void ultronForgeClears(GameTestHelper helper) {
		hideoutFlow(helper, HideoutFaction.ULTRON, 10);
	}

	/** Every mini-boss turns on the player who has hurt it most. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "hideout_threat", timeoutTicks = 100)
	public void miniBossesSwitchToTopThreat(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
		for (EntityType<? extends Mob> type : HideoutEntityTypes.bosses()) {
			Mob boss = type.create(level);
			boss.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
			boss.setNoAi(false);
			level.addFreshEntity(boss);
			ServerPlayer a = mock(helper, Vec3.atCenterOf(at).add(3, 0, 0));
			ServerPlayer b = mock(helper, Vec3.atCenterOf(at).add(-3, 0, 0));
			boss.hurt(level.damageSources().playerAttack(a), 4f);
			((HideoutBoss) boss).hideoutLink().threat().applyNow();
			LivingEntity first = boss.getTarget();
			helper.assertTrue(first == a, type.getDescriptionId() + ": the first attacker takes its attention");
			boss.hurt(level.damageSources().playerAttack(b), 30f);
			((HideoutBoss) boss).hideoutLink().threat().noteSwitched();
			((HideoutBoss) boss).hideoutLink().threat().ageForTest(40);
			((HideoutBoss) boss).hideoutLink().threat().applyNow();
			helper.assertTrue(boss.getTarget() == b, type.getDescriptionId() + ": switches to the player who hurt it most");
			boss.discard();
			a.discard();
			b.discard();
		}
		helper.succeed();
	}
}
