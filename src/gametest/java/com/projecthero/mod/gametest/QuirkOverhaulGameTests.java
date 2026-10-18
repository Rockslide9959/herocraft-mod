package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.power.p05.GeoTerrain;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKits;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.overhaul.Overhaul;
import com.projecthero.mod.quirk.overhaul.OverhaulConfig;
import com.projecthero.mod.quirk.overhaul.OverhaulMoves;
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
 * v0.16.7 Overhaul (Kai Chisaki): one test per move -- the server effect and its number -- plus the bare-hand and
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

	// ---------------------------------------------------------------- framework

	@GameTest(template = EMPTY_STRUCTURE)
	public void overhaulKitRegistered(GameTestHelper helper) {
		helper.assertTrue(QuirkKits.of(Quirk.OVERHAUL).idsOf(com.projecthero.mod.hero.AbilitySlot.SLOT_1).length == 2, "the kit is registered");
		helper.assertTrue(QuirkKits.of(Quirk.OVERHAUL).maxCooldown(OverhaulConfig.FUSION) == OverhaulConfig.FUSION_COOLDOWN, "cooldowns");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void touchNeedsABareHandAndFocus(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 3.5, 100);
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		helper.assertFalse(OverhaulMoves.disassemble(p), "refused with a sword in hand");
		helper.assertTrue(Quirks.meter(p) == 100f && z.getHealth() == 100f, "nothing spent, nothing hit");
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		Quirks.setMeter(p, OverhaulConfig.DISASSEMBLE_COST - 2f);
		helper.assertFalse(OverhaulMoves.disassemble(p), "refused without the Focus");
		Quirks.setMeter(p, OverhaulConfig.DISASSEMBLE_COST);
		helper.assertTrue(OverhaulMoves.disassemble(p), "the last of his Focus is enough");
		helper.assertTrue(Quirks.overloaded(p), "spent to nothing: his focus breaks");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void focusRegenerates(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		Quirks.setMeter(p, 50f);
		pump(helper, p);
		helper.runAfterDelay(60, () -> {
			float m = Quirks.meter(p);
			helper.assertTrue(m > 55f, "Focus refilled, now " + m);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- R

	@GameTest(template = EMPTY_STRUCTURE)
	public void disassembleHitsForFourteen(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 3.5, 100);
		helper.assertTrue(OverhaulMoves.disassemble(p), "the touch landed");
		helper.assertTrue(Math.abs(taken(z) - OverhaulConfig.DISASSEMBLE_DAMAGE) < EPS, "14 damage, took " + taken(z));
		helper.assertTrue(Math.abs(Quirks.meter(p) - (100f - OverhaulConfig.DISASSEMBLE_COST)) < 0.01f, "Focus spent");
		helper.assertTrue(Quirks.cooldownRemaining(p, OverhaulConfig.DISASSEMBLE) > 0, "cooldown set");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shrapnelHitsEverythingNearTheTarget(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		Zombie a = mob(helper, EntityType.ZOMBIE, 4.5, 3.5, 100);
		Zombie b = mob(helper, EntityType.ZOMBIE, 6.5, 5.5, 100);
		int n = OverhaulMoves.shrapnel(p);
		helper.assertTrue(n == 2, "the target and its neighbour, hit " + n);
		helper.assertTrue(Math.abs(taken(a) - OverhaulConfig.SHRAPNEL_DAMAGE) < EPS, "the target took 10, " + taken(a));
		helper.assertTrue(Math.abs(taken(b) - OverhaulConfig.SHRAPNEL_SPLASH_DAMAGE) < EPS, "the shards did 8, " + taken(b));
		helper.succeed();
	}

	// ---------------------------------------------------------------- G

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void spikeWaveHitsAndItsSpikesGoBack(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		// a stone pillar at the far edge stops the wave inside the test box
		for (int y = 3; y <= 7; y++) {
			for (int x = 0; x <= 7; x++) {
				level.setBlock(abs(helper, x, y, 7), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		ServerPlayer p = overhaul(helper, 4.5, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 4.5, 100);
		pump(helper, p);
		helper.assertTrue(OverhaulMoves.spikeWave(p), "the wave starts");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(Math.abs(taken(z) - OverhaulConfig.SPIKE_WAVE_DAMAGE) < EPS, "a spike hit for 9, took " + taken(z));
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
	public void spikeFieldHitsAllAroundAndGoesBack(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.0, 4.0);
		Zombie a = mob(helper, EntityType.ZOMBIE, 4.0, 1.5, 100);
		Zombie b = mob(helper, EntityType.ZOMBIE, 6.5, 4.0, 100);
		pump(helper, p);
		helper.assertTrue(OverhaulMoves.spikeField(p), "the field starts");
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(Math.abs(taken(a) - OverhaulConfig.SPIKE_FIELD_DAMAGE) < EPS && Math.abs(taken(b) - OverhaulConfig.SPIKE_FIELD_DAMAGE) < EPS,
					"both hit for 8: " + taken(a) + ", " + taken(b));
			helper.assertTrue(tips(helper) >= 6, "a ring of spikes, " + tips(helper));
			helper.assertTrue(p.getHealth() == p.getMaxHealth(), "never himself");
		});
		helper.runAfterDelay(OverhaulConfig.SPIKE_TTL + 25, () -> {
			assertRestored(helper);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- X

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void wallRisesAndCrumblesBack(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		pump(helper, p);
		helper.assertTrue(OverhaulMoves.wall(p), "the wall starts");
		helper.runAfterDelay(10, () -> {
			for (int x = 2; x <= 6; x++) {
				for (int y = 3; y <= 5; y++) {
					helper.assertTrue(level.getBlockState(abs(helper, x, y, 3)).is(Blocks.STONE), "wall block at " + x + "," + y);
				}
			}
			helper.assertTrue(QuirkKits.of(Quirk.OVERHAUL).active(p, com.projecthero.mod.hero.AbilitySlot.SLOT_3), "the X box lights");
		});
		helper.runAfterDelay(OverhaulConfig.WALL_TTL + 15, () -> {
			assertRestored(helper);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void launchRampFlingsHimAndGoesBack(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer p = overhaul(helper, 4.5, 1.5);
		pump(helper, p);
		helper.assertTrue(OverhaulMoves.ramp(p), "the ramp rises");
		Vec3 v = p.getDeltaMovement();
		helper.assertTrue(v.y >= OverhaulConfig.RAMP_UP - 0.01 && v.z > 1.0, "flung up and forward: " + v);
		helper.assertTrue(level.getBlockState(abs(helper, 4, 3, 2)).is(Blocks.STONE) && level.getBlockState(abs(helper, 4, 4, 3)).is(Blocks.STONE),
				"the ramp's blocks");
		helper.runAfterDelay(OverhaulConfig.RAMP_TTL + 15, () -> {
			// he hangs in the air (mock players don't move): only the ramp must be gone
			assertRestored(helper);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Z

	@GameTest(template = EMPTY_STRUCTURE)
	public void restoreHealsHimAndHisSquad(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 2.5, 2.5);
		ServerPlayer mate = mock(helper, 5.5, 3.0, 2.5, 0f);
		SquadManager squads = SquadManager.get(helper.getLevel().getServer());
		Squad squad = squads.create("ov" + p.getUUID().toString().substring(0, 8), p.getUUID());
		squads.addMember(squad, mate.getUUID());
		try {
			p.setHealth(5f);
			p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
			mate.setHealth(4f);
			int n = OverhaulMoves.restore(p);
			helper.assertTrue(n == 2, "himself and the squadmate, " + n);
			helper.assertTrue(p.getHealth() == p.getMaxHealth() && mate.getHealth() == mate.getMaxHealth(), "both whole");
			helper.assertFalse(p.hasEffect(MobEffects.POISON), "the poison is gone");
			helper.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SPEED), "good effects stay");
		} finally {
			squads.disband(squad);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void reassembleMendsArmourAndShields(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 2.5, 2.5);
		ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
		chest.setDamageValue(150);
		p.setItemSlot(EquipmentSlot.CHEST, chest);
		ItemStack boots = new ItemStack(Items.IRON_BOOTS);
		boots.setDamageValue(20);
		p.setItemSlot(EquipmentSlot.FEET, boots);
		int n = OverhaulMoves.reassemble(p);
		helper.assertTrue(n == 2, "two pieces mended, " + n);
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() == 0, "the chestplate is whole");
		helper.assertTrue(p.hasEffect(MobEffects.ABSORPTION), "Absorption");
		helper.succeed();
	}

	// ---------------------------------------------------------------- V

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void overhaulFieldShattersAndReforms(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.0, 4.0);
		Zombie a = mob(helper, EntityType.ZOMBIE, 4.0, 0.8, 100);
		Zombie b = mob(helper, EntityType.ZOMBIE, 6.5, 6.5, 100);
		pump(helper, p);
		helper.assertTrue(OverhaulMoves.field(p), "the field spreads");
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(Math.abs(taken(a) - OverhaulConfig.FIELD_DAMAGE) < EPS && Math.abs(taken(b) - OverhaulConfig.FIELD_DAMAGE) < EPS,
					"each hit once for 12: " + taken(a) + ", " + taken(b));
			helper.assertTrue(tips(helper) >= 8, "a forest of spikes, " + tips(helper));
		});
		helper.runAfterDelay(OverhaulConfig.FIELD_TTL + 25, () -> {
			assertRestored(helper);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void collapsePitTrapsAndGivesTheGroundBack(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer p = overhaul(helper, 4.5, 1.5, 20f);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 5.5, 100);
		double y0 = z.getY();
		pump(helper, p);
		LivingEntity t = OverhaulMoves.pit(p);
		helper.assertTrue(t == z, "the zombie is pitted");
		helper.assertTrue(Math.abs(z.getY() - (y0 - OverhaulConfig.PIT_DEPTH)) < 0.05, "it dropped two blocks: " + (y0 - z.getY()));
		helper.assertTrue(Math.abs(taken(z) - OverhaulConfig.PIT_DAMAGE) < EPS, "6 damage, " + taken(z));
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(level.getBlockState(abs(helper, 4, 3, 5)).is(Blocks.STONE), "the lid closed over it");
			helper.assertTrue(level.getBlockState(abs(helper, 4, 2, 5)).isAir() && level.getBlockState(abs(helper, 4, 1, 5)).isAir(), "the hole");
			helper.assertTrue(z.isAlive() && !z.isInWall(), "trapped, not suffocating");
		});
		helper.runAfterDelay(OverhaulConfig.PIT_TICKS + 15, () -> {
			helper.assertTrue(z.getY() >= y0 - 0.01, "put back on top: " + (z.getY() - y0));
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
		helper.assertTrue(OverhaulMoves.pit(p) == null, "a boss is never pitted");
		helper.assertTrue(Quirks.meter(p) == 100f, "nothing spent");
		Quirks.edit(p, n -> n.abilityReadyAt.clear());
		Quirks.setVal(p, Overhaul.V_FUSION, helper.getLevel().getGameTime() + 100); // fused: the hardest touch
		helper.assertTrue(OverhaulMoves.shrapnel(p) >= 1, "the touch lands");
		float cap = boss.getMaxHealth() * OverhaulConfig.BOSS_MAX_FRACTION_PER_HIT;
		helper.assertTrue(taken(boss) <= cap + EPS, "capped at 10% a hit, took " + taken(boss));
		helper.assertTrue(boss.getDeltaMovement().horizontalDistance() < 1.0e-3, "no knockback: " + boss.getDeltaMovement());
		helper.succeed();
	}

	// ---------------------------------------------------------------- C

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void fusionGrowsArmoursAndEnds(GameTestHelper helper) {
		ground(helper);
		ServerPlayer p = overhaul(helper, 4.5, 4.5);
		double armour = p.getAttributeValue(Attributes.ARMOR);
		pump(helper, p);
		helper.assertTrue(OverhaulMoves.fusion(p), "fused");
		helper.assertTrue(Overhaul.fused(p), "the Fusion timer runs");
		helper.assertTrue(p.getAttributeValue(Attributes.ARMOR) >= armour + OverhaulConfig.FUSION_ARMOR - 0.01, "stone armour");
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

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void shardShotBurstsTheBlockAndItComesBack(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		// looking down at the ground two blocks ahead
		ServerPlayer p = overhaul(helper, 4.5, 0.5, 40f);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 5.5, 100);
		Zombie off = mob(helper, EntityType.ZOMBIE, 0.5, 0.5, 100);
		pump(helper, p);
		int n = OverhaulMoves.shard(p);
		helper.assertTrue(n == 1, "the shards hit the one in front, " + n);
		helper.assertTrue(Math.abs(taken(z) - OverhaulConfig.SHARD_DAMAGE) < EPS && off.getHealth() == 100f, "9 in the cone, none beside: " + taken(z));
		helper.assertTrue(level.getBlockState(abs(helper, 4, 2, 2)).isAir(), "the block burst");
		helper.runAfterDelay(OverhaulConfig.SHARD_BLOCK_TTL + 15, () -> {
			assertRestored(helper);
			helper.succeed();
		});
	}
}
