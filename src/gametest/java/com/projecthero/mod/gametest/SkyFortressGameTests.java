package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.alarm.AlarmGuards;
import com.projecthero.mod.alarm.AlarmKeycards;
import com.projecthero.mod.alarm.AlarmZone;
import com.projecthero.mod.alarm.AlarmZones;
import com.projecthero.mod.alarm.GuardRole;
import com.projecthero.mod.alarm.block.AlarmBlocks;
import com.projecthero.mod.alarm.block.BlastDoorBlock;
import com.projecthero.mod.loot.HeroVault;
import com.projecthero.mod.skyfortress.InSkyFortressCondition;
import com.projecthero.mod.skyfortress.SkyFling;
import com.projecthero.mod.skyfortress.SkyFortressEntityTypes;
import com.projecthero.mod.skyfortress.SkyFortressGuards;
import com.projecthero.mod.skyfortress.SkyFortressMod;
import com.projecthero.mod.skyfortress.SkyTurbines;
import com.projecthero.mod.skyfortress.block.SkyFortressBlocks;
import com.projecthero.mod.skyfortress.block.SkyTurbineBlock;
import com.projecthero.mod.skyfortress.block.WindVentBlock;
import com.projecthero.mod.skyfortress.block.WindVentBlockEntity;
import com.projecthero.mod.skyfortress.entity.AdmiralKestrelBoss;
import com.projecthero.mod.skyfortress.entity.DeckOfficerBoss;
import com.projecthero.mod.skyfortress.entity.KestrelMissileEntity;
import com.projecthero.mod.skyfortress.entity.SkyPrisoner;
import com.projecthero.mod.skyfortress.entity.SkyTrooper;
import com.projecthero.mod.skyfortress.item.KestrelThrusterItem;
import com.projecthero.mod.skyfortress.item.ParachuteItem;
import com.projecthero.mod.skyfortress.item.SkyFortressItems;
import com.projecthero.mod.skyfortress.worldgen.LaunchPadPiece;
import com.projecthero.mod.skyfortress.worldgen.SkyFortressBuilder;
import com.projecthero.mod.skyfortress.worldgen.SkyFortressPiece;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Tier 4 dungeon, the Sky Fortress: it builds without errors with every wing (alarm zone, four panels, lamps,
 * cameras, lasers, spawn points, the locked bridge door, every guard role, prisoners, the Deck Officer and Admiral
 * Kestrel bound to their homes); the Captain's keycard opens the bridge door; sabotaging the four turbines drops the
 * bridge shield (and not before); Kestrel's death opens the Fortress Vault and only then does Kestrel's Thruster roll;
 * the Launch Pad flings a pass holder (and only a pass holder) in eased steps and lands them; the Pad Sergeant drops the
 * pass; Kestrel switches to her top threat, takes to the air at half health (untouchable while she transforms), fires
 * missiles one salvo at a time and paints a full-deck strafing lane; the Deck Officer's flashbang and rally; freeing a
 * prisoner; the parachute, the thruster and the wind vent.
 *
 * <p>Each test is its own batch; the fortress is built 70 blocks above its test and everything is cleared after.
 */
public class SkyFortressGameTests implements FabricGameTest {
	/**
	 * A survival player (vanilla's mock player answers {@code isCreative()} true, and guards, cameras, missiles and the
	 * gusts ignore creative players) -- as {@code AlarmGameTests}. Invulnerable. Remove it with {@link #leave}.
	 */
	private static ServerPlayer survivor(GameTestHelper helper, Vec3 at) {
		ServerLevel level = helper.getLevel();
		net.minecraft.server.network.CommonListenerCookie cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
				new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "sky-tester"), false);
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
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		p.getAbilities().invulnerable = true;
		return p;
	}

	private static void leave(GameTestHelper helper, ServerPlayer p) {
		helper.getLevel().getServer().getPlayerList().remove(p);
	}

	private static AABB area(BoundingBox b) {
		return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
	}

	private static void clear(ServerLevel level, BoundingBox b) {
		AABB box = area(b).inflate(2);
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

	private static int count(ServerLevel level, BoundingBox b, java.util.function.Predicate<BlockState> what) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
			if (what.test(level.getBlockState(p))) {
				n++;
			}
		}
		return n;
	}

	private static void platform(ServerLevel level, BlockPos centre, int r) {
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				level.setBlockAndUpdate(centre.offset(x, -1, z), Blocks.STONE.defaultBlockState());
			}
		}
	}

	/** The whole fortress: layout, alarm kit, roles, bosses, keycard, turbines and the shield, the vault and its artifact. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "sky_build", timeoutTicks = 400)
	public void skyFortressBuildsAndClears(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
		BlockPos origin = helper.absolutePos(new BlockPos(4, 70, 4));
		SkyFortressPiece piece = new SkyFortressPiece(origin, Rotation.CLOCKWISE_90);
		BoundingBox box = piece.getBoundingBox();
		clear(level, box);
		AlarmZones.get(level).removeWithin(box);
		piece.buildAll(level);
		AlarmZone zone = AlarmZones.get(level).register(box, SkyFortressMod.PROFILE);
		ServerPlayer player = null;
		try {
			AlarmZones.scan(level, zone);
			// the alarm kit
			for (BlockPos l : SkyFortressBuilder.PANELS) {
				helper.assertTrue(level.getBlockState(piece.world(l)).is(AlarmBlocks.PANEL), "an Alarm Panel at local " + l);
			}
			helper.assertTrue(zone.panels.size() == 4, "the zone knows its four panels (got " + zone.panels.size() + ")");
			helper.assertTrue(count(level, box, s -> s.is(AlarmBlocks.LAMP)) >= 8, "at least eight Alarm Lamps");
			helper.assertTrue(count(level, box, s -> s.is(AlarmBlocks.CAMERA)) >= 6, "at least six cameras");
			helper.assertTrue(count(level, box, s -> s.is(AlarmBlocks.LASER)) == 3, "three laser tripwires");
			helper.assertTrue(count(level, box, s -> s.is(AlarmBlocks.SPAWN_POINT)) >= 10, "reinforcement spawn points");
			BlockPos door = piece.world(SkyFortressBuilder.BRIDGE_DOOR);
			BlockState ds = level.getBlockState(door);
			helper.assertTrue(ds.is(AlarmBlocks.BLAST_DOOR) && ds.getValue(BlastDoorBlock.LOCKED), "the bridge's flight-deck door is a locked Blast Door");
			helper.assertTrue(count(level, box, s -> s.is(SkyFortressBlocks.WIND_VENT)) == 4, "four wind vents");
			helper.assertTrue(count(level, box, s -> s.is(SkyFortressBlocks.PARACHUTE_CRATE)) == 2, "two parachute crates");
			helper.assertTrue(count(level, box, s -> s.is(com.projecthero.mod.vaultdungeon.block.VaultBlocks.TURRET)) == 2, "two hangar turrets");
			helper.assertTrue(level.getBlockState(piece.world(SkyFortressBuilder.VAULT)).is(SkyFortressBlocks.SEAL), "the sealed Fortress Vault");
			for (int sx = -1; sx <= 1; sx += 2) {
				helper.assertFalse(level.getBlockState(piece.world(new BlockPos(20 * sx, 1, 0))).isAir(), "an engine nacelle at local x " + (20 * sx));
			}
			// the lift hatch is open from below the keel to the hangar ceiling
			BlockPos hatch = piece.world(SkyFortressBuilder.HATCH);
			for (int y = -4; y <= 6; y++) {
				helper.assertTrue(level.getBlockState(hatch.offset(0, y, 0)).isAir(), "the lift shaft is clear at local y " + y);
			}
			helper.assertFalse(level.getBlockState(hatch.above(7)).isAir(), "the hangar ceiling closes the shaft (the fling's target)");
			// people
			List<Mob> guards = level.getEntitiesOfClass(Mob.class, area(box), SkyFortressGuards::isGuard);
			helper.assertTrue(guards.size() >= 18, "at least 18 guards (got " + guards.size() + ")");
			helper.assertTrue(guards.stream().allMatch(m -> m.isPersistenceRequired() && AlarmGuards.isGuard(m)), "every guard is a persistent alarm guard");
			helper.assertTrue(guards.stream().filter(m -> AlarmGuards.hasRole(m, GuardRole.CAPTAIN)).count() == 1, "exactly one Captain");
			for (GuardRole r : new GuardRole[] { GuardRole.RUNNER, GuardRole.MEDIC, GuardRole.SHIELD, GuardRole.SNIPER }) {
				helper.assertTrue(guards.stream().anyMatch(m -> AlarmGuards.hasRole(m, r)), "a " + r + " on board");
			}
			helper.assertTrue(level.getEntitiesOfClass(SkyPrisoner.class, area(box), SkyPrisoner::isAlive).size() == 4, "four prisoners");
			List<DeckOfficerBoss> officers = level.getEntitiesOfClass(DeckOfficerBoss.class, area(box), DeckOfficerBoss::isAlive);
			helper.assertTrue(officers.size() == 1 && piece.world(SkyFortressBuilder.OFFICER_HOME).equals(officers.get(0).home()),
					"one Deck Officer bound to the bridge");
			List<AdmiralKestrelBoss> admirals = level.getEntitiesOfClass(AdmiralKestrelBoss.class, area(box), AdmiralKestrelBoss::isAlive);
			helper.assertTrue(admirals.size() == 1, "exactly one Admiral Kestrel (got " + admirals.size() + ")");
			AdmiralKestrelBoss kestrel = admirals.get(0);
			helper.assertTrue(piece.world(SkyFortressBuilder.KESTREL_HOME).equals(kestrel.home()), "Kestrel is bound to the flight deck");

			// the Captain's keycard opens the bridge door
			player = survivor(helper, Vec3.atCenterOf(door).add(0, 0, 0));
			Mob captain = guards.stream().filter(m -> AlarmGuards.hasRole(m, GuardRole.CAPTAIN)).findFirst().orElseThrow();
			captain.kill();
			ItemStack card = level.getEntitiesOfClass(ItemEntity.class, captain.getBoundingBox().inflate(4), e -> AlarmKeycards.isKeycard(e.getItem()))
					.stream().map(ItemEntity::getItem).findFirst().orElse(ItemStack.EMPTY);
			helper.assertTrue(!card.isEmpty() && Long.valueOf(zone.id).equals(AlarmKeycards.zoneOf(card)), "the Captain drops this fortress's keycard");
			AlarmKeycards.useOnDoor(level, door, card, player);
			helper.assertTrue(level.getBlockState(door).getValue(BlastDoorBlock.OPEN), "the keycard opens the bridge door");

			// turbines: three down leaves the shield, the fourth drops it
			int shield = count(level, box, s -> s.is(SkyFortressBlocks.SHIELD));
			helper.assertTrue(shield == 9, "nine shield blocks over the stair-hall door (got " + shield + ")");
			List<BlockPos> turbines = SkyFortressBuilder.TURBINES.stream().map(piece::world).toList();
			for (BlockPos t : turbines) {
				helper.assertTrue(level.getBlockState(t).is(SkyFortressBlocks.TURBINE) && level.getBlockState(t).getValue(SkyTurbineBlock.RUNNING),
						"a running turbine at " + t);
			}
			for (int i = 0; i < 3; i++) {
				int left = SkyTurbines.sabotage(level, turbines.get(i), player);
				helper.assertTrue(left == 3 - i, "after " + (i + 1) + " turbines, " + (3 - i) + " still run (got " + left + ")");
				helper.assertTrue(count(level, box, s -> s.is(SkyFortressBlocks.SHIELD)) == 9, "the shield holds while a turbine runs");
			}
			helper.assertTrue(SkyTurbines.use(level, turbines.get(0), level.getBlockState(turbines.get(0)), player) == -1, "a wrecked turbine can't be sabotaged again");
			helper.assertTrue(SkyTurbines.sabotage(level, turbines.get(3), player) == 0, "the last turbine dies");
			helper.assertTrue(count(level, box, s -> s.is(SkyFortressBlocks.SHIELD)) == 0, "the bridge shield drops");

			// the vault holds while Kestrel lives, opens on her death; then (and only then) the Thruster rolls
			BlockPos vault = piece.world(SkyFortressBuilder.VAULT);
			LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(vault)).create(LootContextParamSets.CHEST);
			helper.assertFalse(InSkyFortressCondition.INSTANCE.test(new LootContext.Builder(params).create(java.util.Optional.empty())),
					"no Thruster while the Admiral lives");
			com.projecthero.mod.skyfortress.SkyFortressClearing.pokeSeal(level, vault, player);
			helper.assertTrue(level.getBlockState(vault).is(SkyFortressBlocks.SEAL), "the seal holds while Kestrel lives");
			kestrel.kill();
			helper.assertTrue(HeroVault.tierAt(level, vault) == 4, "Kestrel's death opens a Tier 4 Hero Vault");
			helper.assertTrue(InSkyFortressCondition.INSTANCE.test(new LootContext.Builder(params).create(java.util.Optional.empty())),
					"the Thruster's condition passes in the cleared fortress");
			LootTable table = level.getServer().reloadableRegistries().getLootTable(SkyFortressMod.TIER4_ARTIFACTS);
			int thrusters = 0;
			for (int i = 0; i < 300; i++) {
				for (ItemStack s : table.getRandomItems(params)) {
					if (s.is(SkyFortressItems.KESTREL_THRUSTER)) {
						thrusters++;
					}
				}
			}
			helper.assertTrue(thrusters > 20 && thrusters < 110, "Kestrel's Thruster rolls about 20% of the time (" + thrusters + "/300)");
		} finally {
			if (player != null) {
				leave(helper, player);
			}
			AlarmZones.get(level).remove(zone.id);
			clear(level, box);
		}
		helper.succeed();
	}

	/** The Launch Pad: no pass, no launch; a pass holder rises in eased steps and is set down on the deck. The Sergeant drops the pass. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "sky_pad", timeoutTicks = 200)
	public void launchPadFlingsPassHolder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
		BlockPos origin = helper.absolutePos(new BlockPos(4, 20, 4));
		LaunchPadPiece piece = new LaunchPadPiece(origin, Rotation.NONE, false);
		BoundingBox box = piece.getBoundingBox();
		clear(level, box);
		piece.buildAll(level);
		BlockPos ceiling = origin.above(30);
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				level.setBlockAndUpdate(ceiling.offset(x, 0, z), Blocks.STONE.defaultBlockState());
			}
		}
		ServerPlayer p = survivor(helper, Vec3.atBottomCenterOf(origin.above()));
		try {
			helper.assertTrue(level.getBlockState(origin).is(SkyFortressBlocks.LAUNCH_PAD), "the pad at the post's centre");
			helper.assertTrue(SkyFling.targetY(level, origin) == ceiling.getY() - SkyFling.CEILING_TO_FLOOR, "the target is the floor under the ceiling");
			// the Pad Sergeant and his pass
			List<SkyTrooper> sergeants = level.getEntitiesOfClass(SkyTrooper.class, area(box), SkyTrooper::isSergeant);
			helper.assertTrue(sergeants.size() == 1, "one Pad Sergeant (got " + sergeants.size() + ")");
			sergeants.get(0).hurt(level.damageSources().playerAttack(p), 1f);
			sergeants.get(0).kill();
			helper.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, sergeants.get(0).getBoundingBox().inflate(4),
					e -> e.getItem().is(SkyFortressItems.FORTRESS_PASS)).isEmpty(), "he drops the Fortress Pass");
			helper.assertTrue(level.getEntitiesOfClass(Mob.class, area(box), m -> AlarmGuards.hasRole(m, GuardRole.SNIPER)).size() == 1, "a sniper on the shack");

			helper.assertFalse(SkyFling.tryLaunch(level, origin, p, false), "no pass, no launch");
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SkyFortressItems.FORTRESS_PASS));
			helper.assertTrue(SkyFling.tryLaunch(level, origin, p, false), "a pass holder is launched");
			SkyFling.Fling f = SkyFling.active(p);
			helper.assertTrue(f != null, "the fling is running");
			double vy = p.getDeltaMovement().y;
			helper.assertTrue(vy >= SkyFling.MIN_RISE && vy <= SkyFling.MAX_RISE, "the first step rises " + vy + " b/t");
			helper.assertTrue(SkyFling.riseSpeed(origin.getY(), f.targetY) == SkyFling.MAX_RISE, "full speed far below the hangar");
			helper.assertTrue(SkyFling.riseSpeed(f.targetY - 1, f.targetY) < SkyFling.MAX_RISE, "it eases near the top");
			helper.assertFalse(SkyFling.tryLaunch(level, origin, p, false), "no second fling while one runs");
			// arrive: the next step lands them sideways with slow fall
			p.teleportTo(p.getX(), f.targetY + 1.0, p.getZ());
			helper.assertFalse(SkyFling.step(level, p, f), "the step at the top ends the fling");
			helper.assertTrue(p.hasEffect(MobEffects.SLOW_FALLING), "slow falling to settle on the deck");
			Vec3 v = p.getDeltaMovement();
			helper.assertTrue(Math.abs(v.y - 0.25) < 1.0e-6 && Math.hypot(v.x, v.z) > 0.4, "nudged off the hatch (" + v + ")");
		} finally {
			SkyFling.clear();
			leave(helper, p);
			clear(level, box);
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					level.setBlockAndUpdate(ceiling.offset(x, 0, z), Blocks.AIR.defaultBlockState());
				}
			}
		}
		helper.succeed();
	}

	/** Kestrel: threat switching, the half-health air phase (untouchable while transforming), missiles, the strafing lane. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "sky_kestrel", timeoutTicks = 200)
	public void kestrelPhasesAndMoves(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
		BlockPos at = helper.absolutePos(new BlockPos(4, 20, 4));
		platform(level, at, 12);
		AdmiralKestrelBoss boss = SkyFortressEntityTypes.ADMIRAL_KESTREL.create(level);
		boss.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		boss.setHome(at);
		level.addFreshEntity(boss);
		ServerPlayer a = survivor(helper, Vec3.atBottomCenterOf(at).add(6, 0, 0));
		ServerPlayer b = survivor(helper, Vec3.atBottomCenterOf(at).add(-6, 0, 0));
		for (ServerPlayer s : new ServerPlayer[] { a, b }) {
			// an invulnerable player is no boss's target (BossTargets.isVictim): Resistance V keeps them alive instead
			s.getAbilities().invulnerable = false;
			s.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 2000, 4, false, false));
		}
		boss.hurt(level.damageSources().playerAttack(a), 8f);
		boss.threat().applyNow();
		helper.assertTrue(boss.getTarget() == a, "the first attacker takes her attention");
		boss.hurt(level.damageSources().playerAttack(b), 60f);
		boss.threat().noteSwitched();
		boss.threat().ageForTest(40);
		boss.threat().applyNow();
		helper.assertTrue(boss.getTarget() == b, "she switches to the player who hurt her most");
		// missiles: one per 3 ticks after the wind-up
		boss.startMissiles();
		helper.assertTrue(boss.move() == AdmiralKestrelBoss.MOVE_MISSILES, "the barrage starts");
		// the strafing lane spans the deck
		helper.runAfterDelay(AdmiralKestrelBoss.MISSILE_WINDUP + 4, () -> {
			int missiles = level.getEntitiesOfClass(KestrelMissileEntity.class, new AABB(at).inflate(24)).size();
			helper.assertTrue(missiles >= 1 && missiles <= 2, "the first salvo is out, one missile per 3 ticks (got " + missiles + ")");
			level.getEntitiesOfClass(KestrelMissileEntity.class, new AABB(at).inflate(40)).forEach(Entity::discard);
			boss.startStrafe();
			helper.assertTrue(boss.laneStart() != null && Math.abs(boss.laneStart().distanceTo(boss.laneEnd()) - 2 * AdmiralKestrelBoss.STRAFE_HALF) < 1.0e-6,
					"the strafing lane runs the length of the deck");
			helper.assertTrue(boss.flying(), "she lifts off for the run");
			// half health: the air phase
			boss.setHealth(boss.getMaxHealth() * 0.5f + 4f);
			boss.hurt(level.damageSources().playerAttack(b), 10f);
			helper.assertTrue(boss.phase() == 2 && boss.move() == AdmiralKestrelBoss.MOVE_TRANSFORM, "phase 2 starts with the transformation");
			float hp = boss.getHealth();
			helper.assertFalse(boss.hurt(level.damageSources().playerAttack(b), 30f), "untouchable while she transforms");
			helper.assertTrue(boss.getHealth() == hp, "no damage taken mid-transformation");
		});
		helper.runAfterDelay(AdmiralKestrelBoss.MISSILE_WINDUP + 4 + AdmiralKestrelBoss.TRANSFORM_TICKS + 8, () -> {
			helper.assertTrue(boss.phase() == 2 && boss.move() != AdmiralKestrelBoss.MOVE_TRANSFORM, "the transformation ends");
			helper.assertTrue(boss.flying(), "phase 2: she is airborne");
			helper.assertTrue(boss.missileCount() == 9, "nine missiles a barrage in the air");
			level.getEntitiesOfClass(Entity.class, new AABB(at).inflate(48), e -> e instanceof KestrelMissileEntity || e.getTags().contains(AdmiralKestrelBoss.DRONE_TAG))
					.forEach(Entity::discard);
			boss.discard();
			leave(helper, a);
			leave(helper, b);
			helper.succeed();
		});
	}

	/** The Deck Officer's flashbang and rally, a freed prisoner's reward, the parachute, the thruster and a wind vent's gust. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "sky_kit", timeoutTicks = 200)
	public void officerPrisonerAndGear(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
		BlockPos at = helper.absolutePos(new BlockPos(4, 20, 4));
		platform(level, at, 10);
		ServerPlayer p = survivor(helper, Vec3.atBottomCenterOf(at).add(5, 0, 0));
		DeckOfficerBoss officer = SkyFortressEntityTypes.DECK_OFFICER.create(level);
		officer.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		officer.setHome(at);
		level.addFreshEntity(officer);
		officer.setTarget(p);
		officer.startFlash();
		helper.assertTrue(officer.flashSpot() != null && officer.flashSpot().distanceTo(p.position()) < 0.01, "the flashbang lands at the target's feet");
		helper.assertTrue(officer.flash(level, officer.flashSpot()) == 1, "the flash catches the player in the ring");
		helper.assertTrue(p.hasEffect(MobEffects.BLINDNESS) && p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "blinded and slowed");
		int came = officer.rally(level);
		helper.assertTrue(came >= 1, "Rally the Deck brings crew (got " + came + ")");
		helper.assertTrue(officer.getMaxHealth() >= 300f && officer.getMaxHealth() <= 340f, "about 320 health");
		// a prisoner
		SkyPrisoner prisoner = SkyFortressEntityTypes.PRISONER.create(level);
		prisoner.moveTo(at.getX() - 4.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		level.addFreshEntity(prisoner);
		helper.assertTrue(!prisoner.free(level, p).isEmpty() && prisoner.isFreed(), "a freed prisoner hands over a reward");
		helper.assertTrue(prisoner.free(level, p).isEmpty(), "only once");
		// the parachute opens itself in a long fall
		p.getInventory().clearContent();
		p.getInventory().add(new ItemStack(SkyFortressItems.PARACHUTE));
		p.removeAllEffects();
		p.teleportTo(p.getX(), at.getY() + 12, p.getZ());
		p.setOnGround(false);
		p.fallDistance = 14f;
		p.setDeltaMovement(0, -1.0, 0);
		helper.assertTrue(ParachuteItem.autoDeploy(level, p), "the parachute opens");
		helper.assertTrue(p.hasEffect(MobEffects.SLOW_FALLING) && p.fallDistance == 0f, "slow fall, the fall forgiven");
		helper.assertFalse(p.getInventory().contains(new ItemStack(SkyFortressItems.PARACHUTE)), "the parachute is used up");
		// Kestrel's Thruster
		p.setXRot(0f);
		p.setYRot(0f);
		KestrelThrusterItem.fire(level, p);
		Vec3 v = p.getDeltaMovement();
		helper.assertTrue(Math.abs(v.horizontalDistance() - KestrelThrusterItem.THRUST) < 0.05, "a thrust of " + KestrelThrusterItem.THRUST + " (" + v + ")");
		// a wind vent pushes a player in its lane by one step a tick
		BlockPos vent = at.offset(-6, -1, -6);
		level.setBlockAndUpdate(vent, SkyFortressBlocks.WIND_VENT.defaultBlockState().setValue(WindVentBlock.FACING, Direction.EAST));
		p.teleportTo(vent.getX() + 4.5, vent.getY() + 1.0, vent.getZ() + 0.5);
		p.setDeltaMovement(Vec3.ZERO);
		helper.assertTrue(WindVentBlockEntity.push(level, vent, Direction.EAST) == 1, "the gust finds the player in its lane");
		helper.assertTrue(Math.abs(p.getDeltaMovement().x - WindVentBlockEntity.PUSH) < 1.0e-6, "one push step east");
		level.getEntitiesOfClass(Entity.class, new AABB(at).inflate(16), e -> !(e instanceof Player)).forEach(Entity::discard);
		leave(helper, p);
		helper.succeed();
	}
}
