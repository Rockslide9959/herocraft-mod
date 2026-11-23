package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.loot.HeroVault;
import com.projecthero.mod.loot.LocatorSearch;
import com.projecthero.mod.loot.LootItems;
import com.projecthero.mod.vaultdungeon.VaultClearing;
import com.projecthero.mod.vaultdungeon.VaultDungeonState;
import com.projecthero.mod.vaultdungeon.VaultEntityTypes;
import com.projecthero.mod.vaultdungeon.VaultGuards;
import com.projecthero.mod.vaultdungeon.block.VaultAlarmBlock;
import com.projecthero.mod.vaultdungeon.block.VaultBlocks;
import com.projecthero.mod.vaultdungeon.block.VaultConsoleBlock;
import com.projecthero.mod.vaultdungeon.block.VaultTurretBlockEntity;
import com.projecthero.mod.vaultdungeon.entity.RiotWarden;
import com.projecthero.mod.vaultdungeon.entity.WardenKrossBoss;
import com.projecthero.mod.vaultdungeon.item.MjolnirShardItem;
import com.projecthero.mod.vaultdungeon.item.VaultItems;
import com.projecthero.mod.vaultdungeon.item.WardensKeyringItem;
import com.projecthero.mod.vaultdungeon.worldgen.VaultBuilder;
import com.projecthero.mod.vaultdungeon.worldgen.VaultPiece;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Tier 4 dungeon, The Vault: it builds with all three floors (guards, turrets, lasers, the console-opened gate,
 * the lockdown tracks, three sealed vaults, Warden Kross bound to his arena); Kross's death clears it for good and opens
 * the Tier 4 vaults; Kross switches to his top threat, enrages at half health and his Lockdown seals / lifts the
 * doorways; he drops Celestial Cores; the turret and the laser alarm work; the three artifacts do what they say and only
 * roll in a cleared Vault.
 *
 * <p>The dungeon is built 50 blocks above its test (its own batch) and everything it placed or spawned is cleared after.
 */
public class VaultDungeonGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, Vec3 at) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		return p;
	}

	private static AABB area(VaultPiece piece) {
		BoundingBox b = piece.getBoundingBox();
		return new AABB(b.minX(), b.minY() - 10, b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
	}

	private static void clear(ServerLevel level, VaultPiece piece) {
		// v0.17.7: buildAll registers the alarm zone; never leave it behind over other tests' ground
		com.projecthero.mod.alarm.AlarmZones.get(level).removeWithin(piece.getBoundingBox());
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

	private static int count(ServerLevel level, AABB box, java.util.function.Predicate<BlockState> what) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			if (what.test(level.getBlockState(p))) {
				n++;
			}
		}
		return n;
	}

	/** The whole dungeon: layout, guards, gate, boss, clearing. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "vault_build", timeoutTicks = 300)
	public void vaultBuildsAndClears(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true); // monsters live, mob hits land
		VaultDungeonState.get(level).resetForTest();
		BlockPos origin = helper.absolutePos(new BlockPos(4, 52, 4));
		VaultPiece piece = new VaultPiece(origin, Rotation.CLOCKWISE_90);
		clear(level, piece);
		piece.buildAll(level);
		try {
			AABB box = area(piece);
			BlockPos anchor = piece.anchor();
			for (BlockPos v : piece.vaults()) {
				helper.assertTrue(level.getBlockState(v).is(VaultBlocks.SEAL), "a sealed Dungeon Vault at " + v);
			}
			List<Mob> guards = level.getEntitiesOfClass(Mob.class, box, VaultGuards::isGuard);
			helper.assertTrue(guards.size() >= 18, "at least 18 guards (got " + guards.size() + ")");
			helper.assertTrue(guards.stream().allMatch(Mob::isPersistenceRequired), "guards never despawn");
			helper.assertTrue(guards.stream().anyMatch(m -> m.getType() == VaultEntityTypes.INMATE)
					&& guards.stream().anyMatch(m -> m.getType() == VaultEntityTypes.RIOT_WARDEN)
					&& guards.stream().anyMatch(m -> m.getType() == VaultEntityTypes.RIOT_GUNNER), "inmates, riot wardens and riot gunners");
			List<WardenKrossBoss> bosses = level.getEntitiesOfClass(WardenKrossBoss.class, box, WardenKrossBoss::isAlive);
			helper.assertTrue(bosses.size() == 1, "exactly one Warden Kross (got " + bosses.size() + ")");
			WardenKrossBoss kross = bosses.get(0);
			helper.assertTrue(anchor.equals(kross.home()), "Kross is bound to his arena (" + kross.home() + " vs " + anchor + ")");
			helper.assertTrue(kross.isPersistenceRequired(), "Kross never despawns");
			helper.assertTrue(count(level, box, s -> s.is(VaultBlocks.TURRET)) == 4, "four turrets");
			// v0.17.7 layout 2: the old laser alarms are the framework's laser tripwires now (2 + 5 in the corridor + 33 in the grid)
			helper.assertTrue(count(level, box, s -> s.is(VaultBlocks.ALARM)) == 0, "no old laser alarms in a layout-2 Vault");
			int lasers = count(level, box, s -> s.is(com.projecthero.mod.alarm.block.AlarmBlocks.LASER));
			helper.assertTrue(lasers == 40, "40 laser tripwires (got " + lasers + ")");
			helper.assertTrue(count(level, box, s -> s.is(VaultBlocks.TRACK)) >= 6, "lockdown tracks at the arena doorways");
			BlockPos console = piece.world(VaultBuilder.CONSOLE);
			helper.assertTrue(level.getBlockState(console).is(VaultBlocks.CONSOLE), "the security console at " + console);
			BlockPos gate = piece.world(VaultBuilder.GATE.get(0));
			BlockState door = level.getBlockState(gate);
			helper.assertTrue(door.is(Blocks.IRON_DOOR) && !door.getValue(DoorBlock.OPEN), "the lockdown gate starts shut");

			ServerPlayer player = mock(helper, Vec3.atCenterOf(console).add(0, 0, 0));
			int[] opened = VaultConsoleBlock.toggleDoors(level, console, player);
			helper.assertTrue(opened[0] == 2 && level.getBlockState(gate).getValue(DoorBlock.OPEN), "the console opens both gate doors");

			// the Lockdown seals both arena doorways and lifts again
			int sealed = VaultClearing.seal(level, anchor);
			helper.assertTrue(sealed == 6, "a Lockdown seals the 6 track columns of the two doorways (got " + sealed + ")");
			helper.assertTrue(count(level, box, s -> s.is(VaultBlocks.SHUTTER)) == 18, "18 shutter blocks");
			helper.assertTrue(VaultClearing.lift(level, anchor) == 18, "lifting removes them all");

			// a seal refuses while Kross lives
			player.moveTo(Vec3.atCenterOf(piece.vaults().get(1)).add(0, 0, -2));
			VaultClearing.pokeSeal(level, piece.vaults().get(1), player);
			helper.assertTrue(level.getBlockState(piece.vaults().get(1)).is(VaultBlocks.SEAL), "the seal holds while Kross lives");
			helper.assertFalse(VaultDungeonState.get(level).cleared(anchor), "not cleared yet");

			kross.kill();
			helper.assertTrue(VaultDungeonState.get(level).cleared(anchor), "Kross's death clears The Vault");
			for (BlockPos v : piece.vaults()) {
				helper.assertTrue(HeroVault.tierAt(level, v) == 4, "the seal at " + v + " is now an open Tier 4 Hero Vault");
			}
			HeroVault.Opened o = HeroVault.open(level, piece.vaults().get(0), player);
			helper.assertTrue(o.result() == HeroVault.OpenResult.OPENED, "the vault opens for the player");
			player.discard();
		} finally {
			clear(level, piece);
			VaultDungeonState.get(level).resetForTest();
		}
		helper.succeed();
	}

	/** Kross turns on whoever has hurt him most (the shared threat table). */
	@GameTest(template = EMPTY_STRUCTURE, batch = "vault_threat", timeoutTicks = 100)
	public void krossSwitchesToTopThreat(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true); // monsters live, mob hits land
		BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
		WardenKrossBoss boss = VaultEntityTypes.WARDEN_KROSS.create(level);
		boss.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		level.addFreshEntity(boss);
		ServerPlayer a = mock(helper, Vec3.atCenterOf(at).add(3, 0, 0));
		ServerPlayer b = mock(helper, Vec3.atCenterOf(at).add(-3, 0, 0));
		boss.hurt(level.damageSources().playerAttack(a), 8f);
		boss.threat().applyNow();
		helper.assertTrue(boss.getTarget() == a, "the first attacker takes his attention");
		boss.hurt(level.damageSources().playerAttack(b), 60f);
		boss.threat().noteSwitched();
		boss.threat().ageForTest(40);
		boss.threat().applyNow();
		helper.assertTrue(boss.getTarget() == b, "he switches to the player who hurt him most");
		helper.assertTrue(boss.getMaxHealth() >= 800f && boss.getMaxHealth() <= 1000f, "800-1000 health (" + boss.getMaxHealth() + ")");
		boss.discard();
		a.discard();
		b.discard();
		helper.succeed();
	}

	/** Half health = enraged; his Lockdown then seals a doorway marked by track, and his death lifts it. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "vault_lockdown", timeoutTicks = 200)
	public void krossEnragesAndLocksDown(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true); // monsters live, mob hits land
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				level.setBlockAndUpdate(at.offset(x - 1, -1, z - 1), Blocks.STONE.defaultBlockState());
			}
		}
		BlockPos track = at.offset(4, -1, 4);
		level.setBlockAndUpdate(track, VaultBlocks.TRACK.defaultBlockState());
		WardenKrossBoss boss = VaultEntityTypes.WARDEN_KROSS.create(level);
		boss.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		boss.setHome(at);
		level.addFreshEntity(boss);
		ServerPlayer p = mock(helper, Vec3.atCenterOf(at).add(2, 0, 0));
		boss.setHealth(boss.getMaxHealth() * 0.5f + 5f);
		boss.hurt(level.damageSources().playerAttack(p), 20f);
		helper.assertTrue(boss.enraged(), "enraged at half health");
		boss.startLockdown();
		helper.succeedWhen(() -> {
			helper.assertTrue(boss.inLockdown(), "the Lockdown is running");
			helper.assertTrue(level.getBlockState(track.above()).is(VaultBlocks.SHUTTER) && level.getBlockState(track.above(3)).is(VaultBlocks.SHUTTER),
					"the doorway over the track is shuttered");
			boss.kill();
			helper.assertTrue(level.getBlockState(track.above()).isAir(), "his death lifts the shutters");
			level.getEntitiesOfClass(Mob.class, new AABB(at).inflate(16), VaultGuards::isGuard).forEach(Entity::discard);
			level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(16)).forEach(Entity::discard);
			p.discard();
		});
	}

	/** Kross drops 2-3 Celestial Cores. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "vault_drops", timeoutTicks = 100)
	public void krossDropsCelestialCores(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true); // monsters live, mob hits land
		BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
		WardenKrossBoss boss = VaultEntityTypes.WARDEN_KROSS.create(level);
		boss.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		level.addFreshEntity(boss);
		ServerPlayer p = mock(helper, Vec3.atCenterOf(at).add(2, 0, 0));
		boss.hurt(level.damageSources().playerAttack(p), 1f);
		boss.kill();
		int cores = 0;
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(6))) {
			if (e.getItem().is(LootItems.CELESTIAL_CORE)) {
				cores += e.getItem().getCount();
			}
		}
		helper.assertTrue(cores >= 2 && cores <= 3, "2-3 Celestial Cores (got " + cores + ")");
		level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(8)).forEach(Entity::discard);
		p.discard();
		helper.succeed();
	}

	/** A turret under a ceiling fires at a player in sight. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "vault_turret", timeoutTicks = 120)
	public void turretFiresAtPlayerInSight(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true); // monsters live, mob hits land
		BlockPos turret = helper.absolutePos(new BlockPos(1, 6, 1));
		level.setBlockAndUpdate(turret.above(), Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(turret, VaultBlocks.TURRET.defaultBlockState());
		ServerPlayer p = mock(helper, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 5))));
		helper.succeedWhen(() -> {
			p.setGameMode(GameType.SURVIVAL); // the mock player drifts back to creative
			helper.assertTrue(level.getBlockEntity(turret) instanceof VaultTurretBlockEntity t && t.shotsFired() > 0, "the turret has fired (p=" + p.position() + " creative=" + p.isCreative() + " players=" + level.players().size() + ")");
			level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Arrow.class, new AABB(turret).inflate(16)).forEach(Entity::discard);
			p.discard();
		});
	}

	/** A laser snapped with a player near calls riot wardens; without one it doesn't fire. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "vault_laser", timeoutTicks = 100)
	public void laserAlarmCallsWardens(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true); // monsters live, mob hits land
		BlockPos base = helper.absolutePos(new BlockPos(0, 1, 0));
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				level.setBlockAndUpdate(base.offset(x, 0, z), Blocks.STONE.defaultBlockState());
			}
		}
		BlockPos alarm = base.offset(1, 1, 4);
		level.setBlockAndUpdate(alarm, VaultBlocks.ALARM.defaultBlockState().setValue(VaultAlarmBlock.FACING, Direction.EAST));
		level.setBlockAndUpdate(alarm.north(), Blocks.REDSTONE_BLOCK.defaultBlockState());
		if (VaultClearing.playersNear(level, alarm, 16).isEmpty()) { // another test's mock player can stand within 16
			helper.assertFalse(level.getBlockState(alarm).getValue(VaultAlarmBlock.SPENT), "no player near: the alarm stays armed");
		}
		level.setBlockAndUpdate(alarm.north(), Blocks.AIR.defaultBlockState());
		ServerPlayer p = mock(helper, Vec3.atBottomCenterOf(alarm.east(3)));
		level.setBlockAndUpdate(alarm.north(), Blocks.REDSTONE_BLOCK.defaultBlockState());
		helper.assertTrue(level.getBlockState(alarm).getValue(VaultAlarmBlock.SPENT), "a player near: it fires (signal=" + level.hasNeighborSignal(alarm) + " block=" + level.getBlockState(alarm) + " near=" + VaultClearing.playersNear(level, alarm, 16).size() + " players=" + level.players().size() + " p=" + p.position() + " alarm=" + alarm + " mode=" + p.gameMode.getGameModeForPlayer() + ")");
		List<Mob> squad = level.getEntitiesOfClass(Mob.class, new AABB(alarm).inflate(12), VaultGuards::isGuard);
		helper.assertTrue(squad.size() >= 2, "a riot squad arrives (got " + squad.size() + ")");
		squad.forEach(Entity::discard);
		p.discard();
		helper.succeed();
	}

	/** The three artifacts: Keyring shackles, the Mjolnir Shard's strike, the Riot Capacitor's discharge; riot shields. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "vault_artifacts", timeoutTicks = 100)
	public void artifactsWork(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true); // monsters live, mob hits land
		BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
		ServerPlayer p = mock(helper, Vec3.atBottomCenterOf(at));
		Zombie z1 = EntityType.ZOMBIE.create(level);
		z1.moveTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		level.addFreshEntity(z1);
		Zombie z2 = EntityType.ZOMBIE.create(level);
		z2.moveTo(at.getX() - 1.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		level.addFreshEntity(z2);
		try {
			// Warden's Keyring: Lockdown
			int n = WardensKeyringItem.shackle(level, p);
			helper.assertTrue(n == 2, "the keyring shackles both zombies (got " + n + ")");
			helper.assertTrue(z1.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && z1.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() >= 5, "shackled = Slowness VI");
			// Cracked Mjolnir Shard: the strike hurts mobs round it, never players
			float before = z1.getHealth();
			float playerBefore = p.getHealth();
			int hit = MjolnirShardItem.strike(level, p, z1.position());
			helper.assertTrue(hit >= 1 && before - z1.getHealth() >= 7f, "the strike hurts the zombie (" + before + " -> " + z1.getHealth() + ")");
			helper.assertTrue(p.getHealth() == playerBefore, "the strike never hurts players");
			z1.setHealth(z1.getMaxHealth());
			// a riot warden's shield cuts hits from the front
			RiotWarden w = VaultEntityTypes.RIOT_WARDEN.create(level);
			w.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 3.5, 180f, 0f);
			w.setYHeadRot(180f);
			w.setYBodyRot(180f);
			w.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(net.minecraft.world.item.Items.SHIELD));
			level.addFreshEntity(w);
			helper.assertTrue(RiotWarden.fromFront(w, level.damageSources().playerAttack(p)), "a hit from where the warden faces counts as frontal");
			w.discard();
		} finally {
			z1.discard();
			z2.discard();
			p.discard();
		}
		helper.succeed();
	}

	/** The Riot Capacitor, carried, shocks whoever hurts you and then recharges (after the mock player's spawn protection). */
	@GameTest(template = EMPTY_STRUCTURE, batch = "vault_capacitor", timeoutTicks = 140)
	public void riotCapacitorDischarges(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true); // monsters live, mob hits land
		BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
		ServerPlayer p = mock(helper, Vec3.atBottomCenterOf(at));
		Zombie z = EntityType.ZOMBIE.create(level);
		z.moveTo(at.getX() - 1.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		z.setNoAi(true);
		level.addFreshEntity(z);
		p.getInventory().add(new ItemStack(VaultItems.RIOT_CAPACITOR));
		helper.runAfterDelay(70, () -> { // a fresh ServerPlayer ignores hits for its first 60 ticks
			try {
				p.moveTo(Vec3.atBottomCenterOf(at));
				float zb = z.getHealth();
				p.invulnerableTime = 0;
				boolean hurt = p.hurt(level.damageSources().mobAttack(z), 2f);
				helper.assertTrue(hurt && zb - z.getHealth() >= 4f, "the capacitor shocks the attacker (hurt=" + hurt + ", " + zb + " -> " + z.getHealth() + ")");
				helper.assertTrue(p.getCooldowns().isOnCooldown(VaultItems.RIOT_CAPACITOR), "then recharges");
				float zb2 = z.getHealth();
				p.invulnerableTime = 0;
				p.hurt(level.damageSources().mobAttack(z), 2f);
				helper.assertTrue(z.getHealth() == zb2, "no second shock while recharging");
			} finally {
				z.discard();
				p.discard();
			}
			helper.succeed();
		});
	}

	/** The artifacts only roll in a cleared Vault; The Vault is in the tier 4 Locator tag. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "vault_loot", timeoutTicks = 100)
	public void artifactsOnlyRollInTheVault(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true); // monsters live, mob hits land
		VaultDungeonState state = VaultDungeonState.get(level);
		state.resetForTest();
		BlockPos anchor = helper.absolutePos(new BlockPos(4, 2, 4));
		state.markCleared(anchor, level.getGameTime());
		var table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
				ProjectHeroMod.id("chests/vault_tier4_artifacts")));
		int ours = 0;
		int other = 0;
		for (int i = 0; i < 300; i++) {
			LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(anchor).add(0, 0, 19))
					.create(LootContextParamSets.CHEST);
			for (ItemStack s : table.getRandomItems(params, i * 7919L)) {
				if (s.is(VaultItems.WARDENS_KEYRING) || s.is(VaultItems.MJOLNIR_SHARD) || s.is(VaultItems.RIOT_CAPACITOR)) {
					ours++;
				} else {
					other++;
				}
			}
		}
		helper.assertTrue(other == 0, "only The Vault's artifacts (got " + other + " others)");
		helper.assertTrue(ours > 100 && ours < 260, "about 0.59 artifacts a roll (got " + ours + " in 300)");
		LootParams far = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(anchor).add(200, 0, 0))
				.create(LootContextParamSets.CHEST);
		for (int i = 0; i < 50; i++) {
			helper.assertTrue(table.getRandomItems(far, i).isEmpty(), "no artifacts away from a cleared Vault");
		}
		state.resetForTest();
		helper.assertTrue(LocatorSearch.members(level.registryAccess(), 4).contains(ProjectHeroMod.id("the_vault")), "The Vault is a tier 4 Locator target");
		helper.succeed();
	}
}
