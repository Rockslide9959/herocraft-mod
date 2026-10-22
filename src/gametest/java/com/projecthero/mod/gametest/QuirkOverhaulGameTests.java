package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.p05.GeoTerrain;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKits;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.overhaul.Overhaul;
import com.projecthero.mod.quirk.overhaul.OverhaulConfig;
import com.projecthero.mod.quirk.overhaul.OverhaulMoves;
import com.projecthero.mod.quirk.overhaul.OverhaulRestore;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 Overhaul (Kai Chisaki), revamped: one test per move -- the server effect and its number -- plus the bare-hand and
 * Focus rules, every temporary block (spikes, wall, ramp, pit and lid, the burst block) put back exactly, and the boss
 * rules (capped damage, no knockback, never pitted). Mock players are not ticked by the server, so tests that run over
 * time drive the Quirk tick themselves ({@link #pump}). The ground is three layers of stone (y 0..2); he stands on it
 * at y 3, facing +Z.
 */
public class QuirkOverhaulGameTests implements FabricGameTest {
	private static final float EPS = 0.6f;

	// ---------------------------------------------------------------- helpers

	@SuppressWarnings("removal")
	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z, float pitch) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, pitch);
		p.setYHeadRot(0f);
		p.setOnGround(true);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	/** Overhaul facing +Z, full Focus, every move ready. */
	private static ServerPlayer overhaul(GameTestHelper helper, double x, double z) {
		return overhaul(helper, x, z, 0f);
	}

	private static ServerPlayer overhaul(GameTestHelper helper, double x, double z, float pitch) {
		ServerPlayer p = mock(helper, x, 3.0, z, pitch);
		helper.assertTrue(Quirks.grant(p, Quirk.OVERHAUL), "Overhaul is granted");
		Quirks.edit(p, n -> {
			n.vals.put(Overhaul.V_INIT, 1L);
			n.abilityReadyAt.clear();
			n.animId = QuirkState.ANIM_NONE;
		});
		Quirks.setMeter(p, 100f);
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Quirks.tick(p);
		});
	}

	private static void ground(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				for (int y = 0; y <= 2; y++) {
					helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.STONE.defaultBlockState(), 3);
				}
			}
		}
	}

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double z, double health) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		T m = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, 3.0, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setNoAi(true);
		m.setOnGround(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth((float) health);
		if (m.getAttribute(Attributes.ARMOR) != null) {
			m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		}
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static BlockPos abs(GameTestHelper helper, int x, int y, int z) {
		return helper.absolutePos(new BlockPos(x, y, z));
	}

	/** How many pointed-dripstone tips stand in the 8x8x8 test box. */
	private static int tips(GameTestHelper helper) {
		int n = 0;
		for (int x = 0; x <= 7; x++) {
			for (int y = 0; y <= 7; y++) {
				for (int z = 0; z <= 7; z++) {
					if (helper.getLevel().getBlockState(abs(helper, x, y, z)).is(Blocks.POINTED_DRIPSTONE)) {
						n++;
					}
				}
			}
		}
		return n;
	}

	/** Nothing temporary or out of place left above the stone ground (y 3..7 all air, y 0..2 all stone). */
	private static void assertRestored(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				for (int y = 0; y <= 7; y++) {
					BlockPos pos = abs(helper, x, y, z);
					boolean air = level.getBlockState(pos).isAir();
					if (y <= 2) {
						helper.assertTrue(level.getBlockState(pos).is(Blocks.STONE), "the ground is whole at " + x + "," + y + "," + z);
					} else {
						helper.assertTrue(air, "nothing left standing at " + x + "," + y + "," + z + ": " + level.getBlockState(pos));
					}
					helper.assertFalse(GeoTerrain.isTemp(level, pos), "no ledger entry left at " + x + "," + y + "," + z);
				}
			}
		}
	}

	private static float taken(LivingEntity e) {
		return e.getMaxHealth() - e.getHealth();
	}

	// ---------------------------------------------------------------- framework / the gloves / Focus

	@GameTest(template = EMPTY_STRUCTURE)
	public void overhaulKitFollowsTheSlotRule(GameTestHelper helper) {
		var kit = QuirkKits.of(Quirk.OVERHAUL);
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_1)[0].equals(OverhaulConfig.DISASSEMBLE), "R: Disassemble");
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_3)[0].equals(OverhaulConfig.SURF) && kit.idsOf(AbilitySlot.SLOT_3)[1].equals(OverhaulConfig.PILLAR),
				"X: the movement pair");
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_5)[0].equals(OverhaulConfig.FUSION) && kit.idsOf(AbilitySlot.SLOT_5)[1].equals(OverhaulConfig.REASSEMBLE),
				"V: the mode / utility pair");
		for (AbilitySlot s : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5,
				AbilitySlot.SLOT_6 }) {
			String[] ids = kit.idsOf(s);
			helper.assertTrue(ids.length == 2 && kit.maxCooldown(ids[0]) > 0 && kit.maxCooldown(ids[1]) > 0, "tap + Shift with cooldowns on " + s);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void glovesHoldTheTouchBack(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 3.5, 100);
		helper.assertTrue(OverhaulMoves.gloves(p), "gloves on");
		helper.assertTrue(Overhaul.gloved(p) && QuirkKits.of(Quirk.OVERHAUL).keyLocked(p, AbilitySlot.SLOT_1, false), "the touch keys lock");
		helper.assertFalse(QuirkKits.of(Quirk.OVERHAUL).keyLocked(p, AbilitySlot.SLOT_6, true), "the gloves key never locks");
		helper.assertFalse(OverhaulMoves.disassemble(p), "refused with the gloves on");
		helper.assertTrue(Quirks.meter(p) == 100f && z.getHealth() == 100f, "nothing spent, nothing hit");
		helper.assertFalse(OverhaulMoves.gloves(p), "gloves off");
		Quirks.setMeter(p, OverhaulConfig.DISASSEMBLE_COST - 2f);
		helper.assertFalse(OverhaulMoves.disassemble(p), "refused without the Focus");
		Quirks.setMeter(p, OverhaulConfig.DISASSEMBLE_COST);
		helper.assertTrue(OverhaulMoves.disassemble(p), "the last of his Focus is enough");
		helper.assertTrue(Quirks.overloaded(p), "spent to nothing: his focus breaks");
		helper.assertTrue(QuirkKits.of(Quirk.OVERHAUL).usableWhileOverloaded(AbilitySlot.SLOT_6, true), "the gloves still work while spent");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void focusRegeneratesFasterWithTheGlovesOn(GameTestHelper helper) {
		ground(helper);
		ServerPlayer bare = overhaul(helper, 1.5, 1.5);
		ServerPlayer gloved = overhaul(helper, 6.5, 1.5);
		OverhaulMoves.gloves(gloved);
		Quirks.setMeter(bare, 20f);
		Quirks.setMeter(gloved, 20f);
		pump(helper, bare);
		pump(helper, gloved);
		helper.runAfterDelay(60, () -> {
			float a = Quirks.meter(bare), b = Quirks.meter(gloved);
			helper.assertTrue(a > 25f, "Focus refilled, now " + a);
			helper.assertTrue(b - 20f > (a - 20f) * 1.6f, "twice as fast gloved: " + a + " vs " + b);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- R

	@GameTest(template = EMPTY_STRUCTURE)
	public void disassembleHitsForEighteen(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 3.5, 100);
		helper.assertTrue(OverhaulMoves.disassemble(p), "the touch landed");
		helper.assertTrue(Math.abs(taken(z) - OverhaulConfig.DISASSEMBLE_DAMAGE) < EPS, "18 damage, took " + taken(z));
		helper.assertTrue(Math.abs(Quirks.meter(p) - (100f - OverhaulConfig.DISASSEMBLE_COST)) < 0.01f, "Focus spent");
		helper.assertTrue(Quirks.cooldownRemaining(p, OverhaulConfig.DISASSEMBLE) > 0, "cooldown set");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void disassembleKillBurstsOnTheNeighbours(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		Zombie a = mob(helper, EntityType.ZOMBIE, 4.5, 3.5, 10);
		Zombie b = mob(helper, EntityType.ZOMBIE, 6.0, 5.0, 100);
		helper.assertTrue(OverhaulMoves.disassemble(p), "the touch landed");
		helper.assertFalse(a.isAlive(), "the body came apart");
		helper.assertTrue(Math.abs(taken(b) - OverhaulConfig.DISASSEMBLE_BURST_DAMAGE) < EPS, "its pieces hit the neighbour for 6, " + taken(b));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void disarmDestroysAMobsWeapon(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 3.5, 100);
		z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		ItemStack taken = OverhaulMoves.disarm(p);
		helper.assertTrue(taken.is(Items.IRON_SWORD), "the sword came apart: " + taken);
		helper.assertTrue(z.getMainHandItem().isEmpty(), "its hand is empty");
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, z.getBoundingBox().inflate(4)).isEmpty(),
				"nothing dropped: it is gone");
		Quirks.edit(p, n -> n.abilityReadyAt.clear());
		taken = OverhaulMoves.disarm(p);
		helper.assertTrue(taken.is(Items.CARVED_PUMPKIN), "empty-handed: a piece of its armour instead, " + taken);
		helper.succeed();
	}

	// ---------------------------------------------------------------- G

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void groundSpikesHitAndCleanUp(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		// a stone pillar at the far edge stops the line inside the test box
		for (int y = 3; y <= 7; y++) {
			for (int x = 0; x <= 7; x++) {
				level.setBlock(abs(helper, x, y, 7), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		ServerPlayer p = overhaul(helper, 4.5, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 4.5, 100);
		pump(helper, p);
		helper.assertTrue(OverhaulMoves.spikeWave(p), "the spikes start");
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(Math.abs(taken(z) - OverhaulConfig.SPIKE_WAVE_DAMAGE) < EPS, "a spike hit for 10, took " + taken(z));
			helper.assertTrue(tips(helper) >= 3, "spikes stand, " + tips(helper));
			helper.assertTrue(GeoTerrain.isTemp(level, abs(helper, 4, 3, 2)), "the spike at z 2 is temporary");
		});
		helper.runAfterDelay(OverhaulConfig.SPIKE_TTL + 25, () -> {
			for (int y = 3; y <= 7; y++) {
				for (int x = 0; x <= 7; x++) {
					level.setBlock(abs(helper, x, y, 7), Blocks.AIR.defaultBlockState(), 3);
				}
			}
			assertRestored(helper);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void spikeFieldHitsAllAroundAndCleansUp(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.0, 4.0);
		Zombie a = mob(helper, EntityType.ZOMBIE, 4.0, 1.5, 100);
		Zombie b = mob(helper, EntityType.ZOMBIE, 6.5, 4.0, 100);
		pump(helper, p);
		helper.assertTrue(OverhaulMoves.spikeField(p), "the field starts");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(Math.abs(taken(a) - OverhaulConfig.SPIKE_FIELD_DAMAGE) < EPS && Math.abs(taken(b) - OverhaulConfig.SPIKE_FIELD_DAMAGE) < EPS,
					"both hit for 9: " + taken(a) + ", " + taken(b));
			helper.assertTrue(tips(helper) >= 6, "a ring of spikes, " + tips(helper));
			helper.assertTrue(p.getHealth() == p.getMaxHealth(), "never himself");
		});
		helper.runAfterDelay(OverhaulConfig.SPIKE_TTL + 25, () -> {
			assertRestored(helper);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void terrainIsRefusedWhenTerrainDamageIsOff(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		var cfg = com.projecthero.mod.hero.HeroConfig.get();
		boolean was = cfg.abilityTerrainDamage;
		cfg.abilityTerrainDamage = false;
		try {
			helper.assertTrue(OverhaulMoves.wall(p), "the move still plays");
			helper.assertTrue(GeoTerrain.pending() == 0 || !GeoTerrain.isTemp(helper.getLevel(), abs(helper, 4, 3, 3)), "but places no block");
		} finally {
			cfg.abilityTerrainDamage = was;
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- X (movement)

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void terrainSurfCarriesHimAndItsWakeCleansUp(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 4.5);
		pump(helper, p);
		helper.assertTrue(OverhaulMoves.surf(p), "on the wave");
		helper.assertTrue(QuirkKits.of(Quirk.OVERHAUL).active(p, AbilitySlot.SLOT_3), "the X box lights");
		helper.runAfterDelay(4, () -> {
			Vec3 v = p.getDeltaMovement();
			helper.assertTrue(v.z > OverhaulConfig.SURF_SPEED * 0.8, "carried forward: " + v);
			float fall = QuirkKits.of(Quirk.OVERHAUL).modifyIncomingDamage(p, helper.getLevel().damageSources().fall(), 6f);
			helper.assertTrue(fall == 0f, "no fall damage while surfing");
			helper.assertTrue(GeoTerrain.pending() > 0, "a wake of earth rises behind him");
		});
		helper.runAfterDelay(OverhaulConfig.SURF_TICKS + 2, () -> helper.assertFalse(QuirkKits.of(Quirk.OVERHAUL).active(p, AbilitySlot.SLOT_3), "it ends"));
		helper.runAfterDelay(OverhaulConfig.SURF_TICKS + OverhaulConfig.SURF_WAKE_TTL + 15, () -> {
			assertRestored(helper);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void pillarLaunchFlingsHimAndCleansUp(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer p = overhaul(helper, 4.5, 4.5);
		pump(helper, p);
		helper.assertTrue(OverhaulMoves.pillar(p), "the pillar rises");
		helper.assertTrue(p.getDeltaMovement().y >= OverhaulConfig.PILLAR_UP - 0.01, "flung up: " + p.getDeltaMovement());
		// he hangs in the air (mock players don't move), so the pillar rises under him unhindered
		p.teleportTo(p.getX(), p.getY() + 5, p.getZ());
		helper.runAfterDelay(6, () -> helper.assertTrue(level.getBlockState(abs(helper, 4, 3, 4)).is(Blocks.STONE), "the pillar's base"));
		helper.runAfterDelay(OverhaulConfig.PILLAR_TTL + 15, () -> {
			assertRestored(helper);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Z

	@GameTest(template = EMPTY_STRUCTURE)
	public void overhaulStrikeTouchesTheFirstEnemy(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.8, 100);
		helper.assertTrue(OverhaulMoves.strike(p), "the dash starts");
		helper.assertTrue(Math.abs(taken(z) - OverhaulConfig.STRIKE_DAMAGE) < EPS, "touched for 14, " + taken(z));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void disassembleTerrainPitsEveryoneAndClosesAgain(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer p = overhaul(helper, 4.5, 1.5, 20f);
		Zombie a = mob(helper, EntityType.ZOMBIE, 4.5, 5.5, 100);
		Zombie b = mob(helper, EntityType.ZOMBIE, 2.5, 5.5, 100);
		double y0 = a.getY();
		pump(helper, p);
		List<LivingEntity> caught = OverhaulMoves.pit(p);
		helper.assertTrue(caught.size() == 2, "both pitted, " + caught.size());
		helper.assertTrue(Math.abs(a.getY() - (y0 - OverhaulConfig.PIT_DEPTH)) < 0.05, "it dropped two blocks: " + (y0 - a.getY()));
		helper.assertTrue(Math.abs(taken(a) - OverhaulConfig.PIT_DAMAGE) < EPS, "6 damage, " + taken(a));
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(level.getBlockState(abs(helper, 4, 3, 5)).is(Blocks.STONE), "the lid closed over it");
			helper.assertTrue(a.isAlive() && !a.isInWall(), "trapped, not suffocating");
		});
		helper.runAfterDelay(OverhaulConfig.PIT_TICKS + 15, () -> {
			helper.assertTrue(a.getY() >= y0 - 0.01 && b.getY() >= y0 - 0.01, "put back on top");
			assertRestored(helper);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bossesAreCappedUnmovedAndNeverPitted(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		Zombie boss = mob(helper, EntityType.ZOMBIE, 4.5, 3.5, 1000);
		boss.setDeltaMovement(Vec3.ZERO);
		helper.assertTrue(OverhaulMoves.pit(p).isEmpty(), "a boss is never pitted");
		helper.assertTrue(Quirks.meter(p) == 100f, "nothing spent");
		Quirks.setVal(p, Overhaul.V_FUSION, helper.getLevel().getGameTime() + 100); // fused: the hardest touch
		helper.assertTrue(OverhaulMoves.disassemble(p), "the touch lands");
		float cap = boss.getMaxHealth() * OverhaulConfig.BOSS_MAX_FRACTION_PER_HIT;
		helper.assertTrue(taken(boss) <= cap + EPS, "capped at 10% a hit, took " + taken(boss));
		helper.assertTrue(boss.getDeltaMovement().horizontalDistance() < 1.0e-3, "no knockback: " + boss.getDeltaMovement());
		helper.assertTrue(OverhaulMoves.disarm(p).isEmpty(), "a boss is never disarmed");
		helper.succeed();
	}

	// ---------------------------------------------------------------- C

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void wallBurstRisesAndCrumblesBack(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		pump(helper, p);
		helper.assertTrue(OverhaulMoves.wall(p), "the wall starts");
		helper.runAfterDelay(12, () -> {
			for (int x = 2; x <= 6; x++) {
				for (int y = 3; y <= 5; y++) {
					helper.assertTrue(level.getBlockState(abs(helper, x, y, 3)).is(Blocks.STONE), "wall block at " + x + "," + y);
				}
			}
		});
		helper.runAfterDelay(OverhaulConfig.WALL_TTL + 15, () -> {
			assertRestored(helper);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- V

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void reassembleHealsToFullAndRestoresTheGround(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer p = overhaul(helper, 2.5, 2.5);
		p.setHealth(5f);
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
		// a block an explosion took (the ledger only records while an Overhaul holder is in the level)
		BlockPos hole = abs(helper, 5, 3, 5);
		OverhaulRestore.record(level, hole, Blocks.COBBLESTONE.defaultBlockState(), level.getGameTime());
		pump(helper, p);
		LivingEntity body = OverhaulMoves.reassemble(p);
		helper.assertTrue(body == p, "himself (nobody else looked at)");
		helper.assertTrue(p.getHealth() == p.getMaxHealth(), "whole");
		helper.assertFalse(p.hasEffect(MobEffects.POISON), "the poison is gone");
		helper.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SPEED), "good effects stay");
		helper.assertTrue(Quirks.cooldownRemaining(p, OverhaulConfig.REASSEMBLE) > 1100, "once a minute");
		helper.runAfterDelay(OverhaulConfig.RESTORE_WAVE_TICKS + 10, () -> {
			helper.assertTrue(level.getBlockState(hole).is(Blocks.COBBLESTONE), "the broken block is back");
			level.setBlock(hole, Blocks.AIR.defaultBlockState(), 3);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void fusionGrowsArmoursAndEnds(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 4.5);
		double armour = p.getAttributeValue(Attributes.ARMOR);
		pump(helper, p);
		helper.assertTrue(OverhaulMoves.fusion(p), "fused");
		helper.assertTrue(Overhaul.fused(p), "the Fusion timer runs");
		helper.assertTrue(p.getAttributeValue(Attributes.ARMOR) >= armour + OverhaulConfig.FUSION_ARMOR - 0.01, "stone armour");
		helper.assertTrue(p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) >= 3.0 + OverhaulConfig.FUSION_REACH_BONUS - 0.01, "extra reach");
		helper.runAfterDelay(OverhaulConfig.FUSION_GROW_TICKS + 4, () -> {
			AttributeModifier m = p.getAttribute(Attributes.SCALE).getModifier(OverhaulMoves.SCALE_ID);
			helper.assertTrue(m != null && Math.abs(m.amount() - OverhaulConfig.FUSION_SCALE) < 1.0e-3, "grown to full size: " + m);
			helper.assertTrue(OverhaulMoves.fusion(p), "pressed again: off");
			helper.assertFalse(Overhaul.fused(p), "ended");
			helper.assertTrue(Quirks.cooldownRemaining(p, OverhaulConfig.FUSION) > OverhaulConfig.FUSION_COOLDOWN - 5, "its cooldown starts at the end");
			helper.assertTrue(p.getAttributeValue(Attributes.ARMOR) < armour + 0.01, "the stone falls away");
		});
		helper.runAfterDelay(2 * OverhaulConfig.FUSION_GROW_TICKS + 12, () -> {
			helper.assertTrue(p.getAttribute(Attributes.SCALE).getModifier(OverhaulMoves.SCALE_ID) == null, "back to his own size");
			helper.succeed();
		});
	}
}
