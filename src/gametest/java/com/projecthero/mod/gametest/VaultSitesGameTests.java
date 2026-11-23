package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.alarm.AlarmGuards;
import com.projecthero.mod.alarm.AlarmKeycards;
import com.projecthero.mod.alarm.AlarmZone;
import com.projecthero.mod.alarm.AlarmZones;
import com.projecthero.mod.alarm.GuardRole;
import com.projecthero.mod.alarm.block.AlarmBlocks;
import com.projecthero.mod.alarm.block.AlarmPanelBlock;
import com.projecthero.mod.alarm.block.BlastDoorBlock;
import com.projecthero.mod.loot.HeroVault;
import com.projecthero.mod.loot.sites.SiteAnnexPiece;
import com.projecthero.mod.loot.sites.SiteGuards;
import com.projecthero.mod.loot.sites.SiteTheme;
import com.projecthero.mod.loot.sites.SiteUpgrade;
import com.projecthero.mod.vaultdungeon.VaultDungeonState;
import com.projecthero.mod.vaultdungeon.VaultEntityTypes;
import com.projecthero.mod.vaultdungeon.VaultGuards;
import com.projecthero.mod.vaultdungeon.VaultSecurity;
import com.projecthero.mod.vaultdungeon.block.VaultBarrierBlock;
import com.projecthero.mod.vaultdungeon.block.VaultBlocks;
import com.projecthero.mod.vaultdungeon.block.VaultBreakerBlock;
import com.projecthero.mod.vaultdungeon.entity.RiotCaptainBoss;
import com.projecthero.mod.vaultdungeon.entity.WardenKrossBoss;
import com.projecthero.mod.vaultdungeon.worldgen.VaultBuilder;
import com.projecthero.mod.vaultdungeon.worldgen.VaultPiece;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: The Vault's two new floors and alarm (Maximum Security + the Riot Captain, the Warden's Control Room and the
 * power cut, the zone with its panels / lamps / spawn points / keycard door / guard roles, the pod inmates), the Riot
 * Captain (threat table, Rally, Flashbang, his field), Warden Kross's phase 2 (gas clouds, the Breach Slam), every one of
 * the 19 Tier 2 site annexes as an alarm zone (panel, Captain with the keycard that opens the locked vault-room door,
 * runner, cameras / ward stone, the Ghost bonus), and old pieces (no "Layout" tag) keeping the v0.17.5 plan.
 */
public class VaultSitesGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, Vec3 at) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		return p;
	}

	private static int count(ServerLevel level, BoundingBox b, java.util.function.Predicate<BlockState> what) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
			if (what.test(level.getBlockState(p))) {
				n++;
			}
		}
		return n;
	}

	private static void clearVault(ServerLevel level, VaultPiece piece) {
		AlarmZones.get(level).removeWithin(piece.getBoundingBox());
		BoundingBox b = piece.getBoundingBox();
		AABB box = new AABB(b.minX(), b.minY() - 10, b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1).inflate(2);
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

	private static ItemStack droppedKeycard(ServerLevel level, BlockPos near) {
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(near).inflate(6))) {
			if (AlarmKeycards.isKeycard(e.getItem())) {
				ItemStack s = e.getItem().copy();
				e.discard();
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	// ---------------------------------------------------------------- The Vault

	/** Layout 2: the zone, panels, lamps, spawn points, the keycard door, the roles, the Riot Captain, the power cut. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "vault_floors", timeoutTicks = 400)
	public void vaultNewFloorsAlarmAndPower(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true); // monsters live
		VaultDungeonState.get(level).resetForTest();
		BlockPos origin = helper.absolutePos(new BlockPos(4, 52, 4));
		VaultPiece piece = new VaultPiece(origin, Rotation.COUNTERCLOCKWISE_90);
		clearVault(level, piece);
		piece.buildAll(level);
		try {
			helper.assertTrue(piece.layout() == VaultBuilder.LAYOUT && VaultBuilder.LAYOUT == 2, "a new Vault is layout 2");
			BoundingBox box = piece.getBoundingBox();
			BlockPos breaker = piece.world(VaultBuilder.BREAKER);
			AlarmZone zone = AlarmZones.get(level).zoneAt(breaker);
			helper.assertTrue(zone != null && VaultSecurity.PROFILE.equals(zone.profileId), "The Vault is an alarm zone");
			AlarmZones.scan(level, zone);
			helper.assertTrue(zone.panels.size() == 7, "an Alarm Panel on every floor: 7 (got " + zone.panels.size() + ")");
			helper.assertTrue(zone.lamps.size() >= 20 && zone.spawns.size() >= 20, "lamps and spawn points (" + zone.lamps.size() + ", " + zone.spawns.size() + ")");
			BlockPos door = piece.world(VaultBuilder.MAXSEC_DOOR);
			BlockState ds = level.getBlockState(door);
			helper.assertTrue(ds.is(AlarmBlocks.BLAST_DOOR) && ds.getValue(BlastDoorBlock.LOCKED) && !ds.getValue(BlastDoorBlock.OPEN),
					"a locked Blast Door into Maximum Security (" + ds + ")");
			helper.assertTrue(count(level, box, s -> s.is(AlarmBlocks.CAMERA)) >= 14, "cameras on every floor");

			List<Mob> guards = level.getEntitiesOfClass(Mob.class, AABB.of(box), AlarmGuards::isGuard);
			java.util.function.Function<GuardRole, Long> role = r -> guards.stream().filter(m -> AlarmGuards.hasRole(m, r)).count();
			helper.assertTrue(role.apply(GuardRole.CAPTAIN) == 1, "exactly one Captain (got " + role.apply(GuardRole.CAPTAIN) + ")");
			helper.assertTrue(role.apply(GuardRole.RUNNER) >= 4, "a runner on the floors (got " + role.apply(GuardRole.RUNNER) + ")");
			helper.assertTrue(role.apply(GuardRole.MEDIC) >= 4 && role.apply(GuardRole.SHIELD) >= 5 && role.apply(GuardRole.SNIPER) >= 3,
					"medics, shield-bearers, snipers (" + role.apply(GuardRole.MEDIC) + ", " + role.apply(GuardRole.SHIELD) + ", " + role.apply(GuardRole.SNIPER) + ")");
			helper.assertTrue(guards.stream().allMatch(VaultGuards::isGuard), "the alarm guards are Vault guards");
			List<RiotCaptainBoss> rcs = level.getEntitiesOfClass(RiotCaptainBoss.class, AABB.of(box), RiotCaptainBoss::isAlive);
			helper.assertTrue(rcs.size() == 1, "exactly one Riot Captain (got " + rcs.size() + ")");
			RiotCaptainBoss rc = rcs.get(0);
			helper.assertTrue(piece.world(VaultBuilder.RIOT_CAPTAIN).equals(rc.home()), "the Riot Captain holds the hall");
			helper.assertFalse(AlarmGuards.isGuard(rc), "the mid-boss is not a guard (he keeps his threat table)");
			List<Mob> pods = level.getEntitiesOfClass(Mob.class, AABB.of(box), m -> m.getTags().contains(VaultSecurity.POD_TAG));
			helper.assertTrue(pods.size() == 8, "eight pod inmates (got " + pods.size() + ")");
			helper.assertTrue(level.getEntitiesOfClass(WardenKrossBoss.class, AABB.of(box), WardenKrossBoss::isAlive).size() == 1, "Warden Kross");

			BlockState cf = level.getBlockState(piece.world(VaultBuilder.CAPTAIN_FIELD));
			helper.assertTrue(cf.is(VaultBlocks.BARRIER) && cf.getValue(VaultBarrierBlock.KIND) == VaultBarrierBlock.Kind.CAPTAIN, "the red field on the hall's far door");
			BlockState pf = level.getBlockState(piece.world(VaultBuilder.POWER_FIELD));
			helper.assertTrue(pf.is(VaultBlocks.BARRIER) && pf.getValue(VaultBarrierBlock.KIND) == VaultBarrierBlock.Kind.POWER, "the blue field on the way down");
			helper.assertTrue(level.getBlockState(breaker).is(VaultBlocks.BREAKER) && level.getBlockState(breaker).getValue(VaultBreakerBlock.POWERED), "the breaker is on");

			// the Captain's keycard opens the Maximum Security door
			Mob captain = guards.stream().filter(m -> AlarmGuards.hasRole(m, GuardRole.CAPTAIN)).findFirst().orElseThrow();
			BlockPos at = captain.blockPosition();
			captain.kill();
			ItemStack card = droppedKeycard(level, at);
			helper.assertTrue(!card.isEmpty() && AlarmKeycards.zoneOf(card) == zone.id, "the Captain drops The Vault's keycard");
			ServerPlayer p = mock(helper, Vec3.atCenterOf(door));
			AlarmKeycards.useOnDoor(level, door, card, p);
			helper.assertTrue(level.getBlockState(door).getValue(BlastDoorBlock.OPEN), "the keycard opens the Maximum Security door");

			// the power cut: blue fields drop (pods included), the wing's panels die, the Control Room's lives
			int dropped = VaultSecurity.shutDown(level, breaker, p);
			helper.assertTrue(dropped >= 3 + 24, "the way down and the 8 pods' fields drop (got " + dropped + ")");
			helper.assertTrue(level.getBlockState(piece.world(VaultBuilder.POWER_FIELD)).isAir(), "the way down is open");
			helper.assertTrue(count(level, box, s -> s.is(VaultBlocks.BARRIER) && s.getValue(VaultBarrierBlock.KIND) == VaultBarrierBlock.Kind.POWER) == 0, "no blue field left");
			helper.assertFalse(level.getBlockState(breaker).getValue(VaultBreakerBlock.POWERED), "the breaker is off");
			BlockState hallPanel = level.getBlockState(piece.world(new BlockPos(4, VaultBuilder.F2 + 1, -35)));
			BlockState ctrlPanel = level.getBlockState(piece.world(new BlockPos(7, VaultBuilder.F2 + 1, -58)));
			helper.assertTrue(hallPanel.is(AlarmBlocks.PANEL) && hallPanel.getValue(AlarmPanelBlock.SABOTAGED), "the hall's panel dies with the power");
			helper.assertTrue(ctrlPanel.is(AlarmBlocks.PANEL) && !ctrlPanel.getValue(AlarmPanelBlock.SABOTAGED), "the Control Room's own panel lives");
			helper.assertTrue(VaultSecurity.shutDown(level, breaker, p) == 0, "a dead breaker does nothing");

			// the Riot Captain's death drops his red field
			rc.kill();
			helper.assertTrue(level.getBlockState(piece.world(VaultBuilder.CAPTAIN_FIELD)).isAir(), "the Riot Captain's death opens the Control Room");
			p.discard();
		} finally {
			clearVault(level, piece);
			VaultDungeonState.get(level).resetForTest();
		}
		helper.succeed();
	}

	private static void floor(ServerLevel level, BlockPos at, int r) {
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				level.setBlockAndUpdate(at.offset(x, -1, z), Blocks.STONE.defaultBlockState());
			}
		}
	}

	private static Zombie dummy(ServerLevel level, Vec3 at) {
		Zombie z = EntityType.ZOMBIE.create(level);
		z.moveTo(at.x, at.y, at.z, 0f, 0f);
		z.setNoAi(true);
		level.addFreshEntity(z);
		return z;
	}

	/** The Riot Captain: shared threat table, Rally at half health, the Flashbang, his field drops on death. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "vault_riot_captain", timeoutTicks = 120)
	public void riotCaptainThreatRallyFlash(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
		BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
		floor(level, at, 4);
		BlockPos field = at.offset(3, 0, 0);
		level.setBlockAndUpdate(field, VaultBlocks.BARRIER.defaultBlockState().setValue(VaultBarrierBlock.KIND, VaultBarrierBlock.Kind.CAPTAIN));
		RiotCaptainBoss rc = VaultEntityTypes.RIOT_CAPTAIN.create(level);
		rc.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		rc.setHome(at);
		level.addFreshEntity(rc);
		ServerPlayer a = mock(helper, Vec3.atBottomCenterOf(at).add(2, 0, 0));
		ServerPlayer b = mock(helper, Vec3.atBottomCenterOf(at).add(-2, 0, 0));
		Zombie z = dummy(level, Vec3.atBottomCenterOf(at).add(0, 0, 3));
		try {
			helper.assertTrue(rc.getMaxHealth() >= 300f && rc.getMaxHealth() <= 450f, "300-450 health (" + rc.getMaxHealth() + ")");
			rc.hurt(level.damageSources().playerAttack(a), 8f);
			rc.threat().applyNow();
			helper.assertTrue(rc.getTarget() == a, "the first attacker takes his attention");
			rc.hurt(level.damageSources().playerAttack(b), 60f);
			rc.threat().noteSwitched();
			rc.threat().ageForTest(40);
			rc.threat().applyNow();
			helper.assertTrue(rc.getTarget() == b, "he switches to the player who hurt him most");
			// the Flashbang blinds whoever has a clear line to it
			int caught = rc.pop(level, z.position().add(0, 0.5, 0));
			helper.assertTrue(caught >= 1 && z.hasEffect(MobEffects.BLINDNESS), "the flashbang blinds (caught " + caught + ")");
			// half health: the Rally (once)
			rc.setHealth(rc.getMaxHealth() * 0.5f + 2f);
			rc.invulnerableTime = 0; // the last hit's i-frames would swallow this one
			rc.hurt(level.damageSources().playerAttack(a), 80f);
			helper.assertTrue(rc.rallied(), "Rally at half health");
			long squad = level.getEntitiesOfClass(Mob.class, new AABB(at).inflate(12), m -> VaultGuards.isGuard(m) && AlarmGuards.hasRole(m, GuardRole.SHIELD)).size();
			helper.assertTrue(squad >= 1, "shield-bearers arrive (got " + squad + ")");
			helper.assertTrue(rc.rally(level) == 0, "only once");
			rc.kill();
			helper.assertTrue(level.getBlockState(field).isAir(), "his death drops the red field");
		} finally {
			level.getEntitiesOfClass(Mob.class, new AABB(at).inflate(16)).forEach(Entity::discard);
			level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(16)).forEach(Entity::discard);
			a.discard();
			b.discard();
		}
		helper.succeed();
	}

	/** Warden Kross phase 2: the gas clouds hurt and blind, the volley throws three, the Breach Slam's shockwave. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "vault_phase2", timeoutTicks = 200)
	public void krossPhaseTwo(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		floor(level, at, 5);
		WardenKrossBoss kross = VaultEntityTypes.WARDEN_KROSS.create(level);
		kross.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		kross.setHome(at);
		level.addFreshEntity(kross);
		ServerPlayer p = mock(helper, Vec3.atBottomCenterOf(at).add(-4, 0, 0));
		Zombie z = dummy(level, Vec3.atBottomCenterOf(at).add(3, 0, 0));
		kross.setHealth(kross.getMaxHealth() * 0.5f + 5f);
		kross.hurt(level.damageSources().playerAttack(p), 20f);
		helper.assertTrue(kross.enraged(), "phase 2 at half health");
		// the Breach Slam's landing hits everyone round him
		float zb = z.getHealth();
		int hit = kross.slamImpact(level);
		helper.assertTrue(hit >= 1 && z.getHealth() < zb, "the slam's shockwave hits (" + hit + ", " + zb + " -> " + z.getHealth() + ")");
		z.setHealth(z.getMaxHealth());
		z.invulnerableTime = 0;
		kross.addGasCloud(z.position());
		kross.startGas();
		helper.succeedWhen(() -> {
			helper.assertTrue(z.hasEffect(MobEffects.BLINDNESS) && z.getHealth() < z.getMaxHealth(), "the gas hurts and blinds");
			helper.assertTrue(kross.gasClouds() >= 4, "the volley lands three more clouds (" + kross.gasClouds() + ")");
			kross.discard();
			z.discard();
			p.discard();
		});
	}

	// ---------------------------------------------------------------- Tier 2 sites

	private static final int LIFT = 40;

	private static BlockPos siteCentre(GameTestHelper helper) {
		return helper.absolutePos(new BlockPos(3, 1, 3)).offset(0, LIFT, 0);
	}

	private static SiteAnnexPiece plinthAndBuild(GameTestHelper helper, SiteAnnexPiece piece) {
		ServerLevel level = helper.getLevel();
		BlockPos at = siteCentre(helper);
		for (BlockPos q : BlockPos.betweenClosed(at.offset(-16, -10, -16), at.offset(16, 10, 16))) {
			level.setBlock(q, (q.getY() > at.getY() ? Blocks.AIR : q.getY() == at.getY() ? Blocks.GRASS_BLOCK : Blocks.STONE).defaultBlockState(), 2);
		}
		SiteUpgrade.buildAll(level, piece);
		return piece;
	}

	private static SiteAnnexPiece newAnnex(GameTestHelper helper, SiteTheme theme) {
		BlockPos at = siteCentre(helper);
		return new SiteAnnexPiece(theme, at.getX(), at.getY() + 1, at.getZ() - 1, theme.ordinal() % 4, 0x7177L * (theme.ordinal() + 5));
	}

	private static void wipeSite(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = siteCentre(helper);
		level.getEntitiesOfClass(Entity.class, new AABB(at).inflate(22), e -> !(e instanceof Player)).forEach(Entity::discard);
		for (BlockPos q : BlockPos.betweenClosed(at.offset(-16, -10, -16), at.offset(16, 10, 16))) {
			level.setBlock(q, Blocks.AIR.defaultBlockState(), 2 | 16);
		}
		AlarmZones.get(level).removeWithin(new BoundingBox(at.getX() - 16, at.getY() - 10, at.getZ() - 16, at.getX() + 16, at.getY() + 10, at.getZ() + 16));
	}

	/** Every site: a zone, one panel, lamps, spawn points, a Captain whose keycard opens the locked door, a runner, sensors, Ghost. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "site_alarm", timeoutTicks = 300)
	public void everySiteHasZonePanelCaptainAndLockedVault(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
		for (SiteTheme theme : SiteTheme.values()) {
			SiteAnnexPiece piece = plinthAndBuild(helper, newAnnex(helper, theme));
			try {
				BoundingBox box = piece.getBoundingBox();
				BlockPos vault = piece.vaultPos();
				helper.assertTrue(HeroVault.tierAt(level, vault) == 2, theme + ": a Site Vault (tier 2) at " + vault);
				AlarmZone zone = AlarmZones.get(level).zoneAt(vault);
				helper.assertTrue(zone != null && SiteUpgrade.profileId(theme).equals(zone.profileId), theme + ": the annex is an alarm zone");
				AlarmZones.scan(level, zone);
				helper.assertTrue(zone.panels.size() == 1 && zone.panels.contains(piece.panelPos().asLong()), theme + ": one Alarm Panel in the hall");
				helper.assertTrue(zone.lamps.size() >= 3 && zone.spawns.size() >= 3, theme + ": lamps and spawn points");
				BlockPos door = piece.blastDoorPos();
				BlockState ds = level.getBlockState(door);
				helper.assertTrue(ds.is(AlarmBlocks.BLAST_DOOR) && ds.getValue(BlastDoorBlock.LOCKED) && !ds.getValue(BlastDoorBlock.OPEN), theme + ": the vault room is locked");
				helper.assertTrue(BlastDoorBlock.connected(level, door).size() == 6, theme + ": a 2x3 Blast Door");
				if (theme.oldWorld()) {
					helper.assertTrue(count(level, box, s -> s.is(SiteUpgrade.WARD)) == 1, theme + ": a ward stone tripwire");
				} else {
					helper.assertTrue(count(level, box, s -> s.is(AlarmBlocks.CAMERA)) == 2, theme + ": two cameras");
				}
				List<Mob> guards = level.getEntitiesOfClass(Mob.class, AABB.of(box).inflate(2), SiteGuards::isGuard);
				helper.assertTrue(guards.stream().allMatch(AlarmGuards::isGuard), theme + ": every guard is an alarm guard");
				List<Mob> captains = guards.stream().filter(m -> AlarmGuards.hasRole(m, GuardRole.CAPTAIN)).toList();
				helper.assertTrue(captains.size() == 1, theme + ": exactly one Captain (got " + captains.size() + ")");
				helper.assertTrue(guards.stream().filter(m -> AlarmGuards.hasRole(m, GuardRole.RUNNER)).count() == 1, theme + ": one Alarm Runner");
				helper.assertTrue(guards.size() == piece.totalGuards(), theme + ": " + piece.totalGuards() + " guards (got " + guards.size() + ")");
				// the Captain's keycard opens the door
				Mob captain = captains.get(0);
				BlockPos cAt = captain.blockPosition();
				captain.kill();
				ItemStack card = droppedKeycard(level, cAt);
				helper.assertTrue(!card.isEmpty() && AlarmKeycards.zoneOf(card) == zone.id, theme + ": the Captain drops this site's keycard");
				ServerPlayer p = mock(helper, Vec3.atCenterOf(door));
				AlarmKeycards.useOnDoor(level, door, card, p);
				helper.assertTrue(level.getBlockState(door).getValue(BlastDoorBlock.OPEN), theme + ": the keycard opens the vault room");
				// no alarm went off: the Ghost bonus on the Site Vault
				helper.assertTrue(HeroVault.open(level, vault, p).result() == HeroVault.OpenResult.OPENED, theme + ": the vault opens");
				helper.assertTrue(zone.ghosts.contains(p.getUUID()), theme + ": a quiet raid earns the Ghost bonus");
				p.discard();
			} finally {
				wipeSite(helper);
			}
		}
		helper.succeed();
	}

	/** A ward stone's tripwire raises the site's alarm; with the panel sabotaged it is dead. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "site_ward", timeoutTicks = 120)
	public void wardStoneRaisesTheAlarm(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SiteAnnexPiece piece = plinthAndBuild(helper, newAnnex(helper, SiteTheme.GRAVEYARD));
		try {
			AlarmZone zone = AlarmZones.get(level).zoneAt(piece.vaultPos());
			AlarmZones.scan(level, zone);
			BlockPos ward = null;
			for (BlockPos q : BlockPos.betweenClosed(piece.getBoundingBox().minX(), piece.getBoundingBox().minY(), piece.getBoundingBox().minZ(),
					piece.getBoundingBox().maxX(), piece.getBoundingBox().maxY(), piece.getBoundingBox().maxZ())) {
				if (level.getBlockState(q).is(SiteUpgrade.WARD)) {
					ward = q.immutable();
				}
			}
			helper.assertTrue(ward != null, "the graveyard has a ward stone");
			// sabotaged panel: the sensor is dead
			BlockPos panel = piece.panelPos();
			level.setBlockAndUpdate(panel, level.getBlockState(panel).setValue(AlarmPanelBlock.SABOTAGED, true));
			helper.assertFalse(com.projecthero.mod.loot.sites.SiteWardBlock.trip(level, ward), "a ward wired to a dead panel does nothing");
			level.setBlockAndUpdate(panel, level.getBlockState(panel).setValue(AlarmPanelBlock.SABOTAGED, false));
			helper.assertTrue(com.projecthero.mod.loot.sites.SiteWardBlock.trip(level, ward), "the ward raises the alarm");
			helper.assertTrue(zone.state == com.projecthero.mod.alarm.AlarmState.ALERT, "the site is on Alert");
			com.projecthero.mod.alarm.AlarmDirector.calm(level, zone);
		} finally {
			wipeSite(helper);
		}
		helper.succeed();
	}

	/** Pieces saved before v0.17.7 (no "Layout" tag) keep the v0.17.5 plan: an old annex builds its iron-door vault room. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "site_old_layout", timeoutTicks = 100)
	public void oldPiecesKeepTheirLayout(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = siteCentre(helper);
		// an old annex
		CompoundTag t = new CompoundTag();
		t.put("BB", BoundingBox.CODEC.encodeStart(NbtOps.INSTANCE, new BoundingBox(at.getX() - 6, at.getY(), at.getZ() - 7, at.getX() + 6, at.getY() + 9, at.getZ() + 7))
				.getOrThrow());
		t.putString("Theme", SiteTheme.GAMMA_LAB.name());
		t.putInt("CX", at.getX());
		t.putInt("CZ", at.getZ() - 1);
		t.putInt("Floor", at.getY() + 1);
		t.putInt("Rot", 0);
		t.putLong("Seed", 1234L);
		SiteAnnexPiece old = new SiteAnnexPiece(null, t);
		helper.assertTrue(old.layout() == 1, "an annex with no Layout tag is layout 1");
		plinthAndBuild(helper, old);
		try {
			BlockPos v = old.vaultPos();
			helper.assertTrue(HeroVault.tierAt(level, v) == 2, "the old annex's vault where it always was");
			long doors = BlockPos.betweenClosedStream(v.offset(-4, 0, -4), v.offset(4, 1, 4)).filter(q -> level.getBlockState(q).is(Blocks.IRON_DOOR)).count();
			helper.assertTrue(doors == 2, "its iron door (got " + doors + ")");
			helper.assertTrue(count(level, old.getBoundingBox(), s -> s.is(AlarmBlocks.BLAST_DOOR) || s.is(AlarmBlocks.PANEL)) == 0, "no alarm blocks in an old annex");
		} finally {
			wipeSite(helper);
		}
		// an old Vault piece
		CompoundTag vt = new CompoundTag();
		vt.put("BB", BoundingBox.CODEC.encodeStart(NbtOps.INSTANCE, new BoundingBox(0, 0, 0, 10, 10, 10)).getOrThrow());
		vt.putLong("Origin", at.asLong());
		vt.putInt("Rot", 0);
		vt.putLong("Seed", 99L);
		helper.assertTrue(new VaultPiece(null, vt).layout() == 1, "a Vault piece with no Layout tag is layout 1");
		helper.assertTrue(new VaultPiece(at, Rotation.NONE).layout() == 2, "a new Vault piece is layout 2");
		helper.succeed();
	}
}
