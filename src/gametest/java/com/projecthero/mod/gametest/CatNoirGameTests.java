package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.MiraculousKits;
import com.projecthero.mod.miraculous.catnoir.CatNoir;
import com.projecthero.mod.miraculous.catnoir.CatNoirAbilities;
import com.projecthero.mod.miraculous.catnoir.CatNoirCataclysm;
import com.projecthero.mod.miraculous.catnoir.CatNoirConfig;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 Cat Noir (Plagg's Miraculous): one test per move (its server effect and its number), CATACLYSM on blocks and
 * creatures, the toggles. Mock players are not ticked by the server, so tests that run over time drive
 * {@link Miraculous#tick} themselves ({@link #pump}). Hostile mobs need a non-peaceful difficulty (HARD is set).
 */
public class CatNoirGameTests implements FabricGameTest {
	private static final float EPS = 0.6f;

	// ---------------------------------------------------------------- helpers

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

	/** Cat Noir facing +Z, transformed, the transformation already done (the moves wait for it). */
	private static ServerPlayer cat(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		helper.assertTrue(Miraculous.grant(p, Kwami.PLAGG), "Plagg's Miraculous is granted");
		Miraculous.transform(p);
		MiraculousState n = Miraculous.state(p).copy();
		n.formChangeAt = helper.getLevel().getGameTime() - 200L;
		n.animId = MiraculousState.ANIM_NONE;
		p.setAttached(ModAttachments.MIRACULOUS_STATE, n);
		helper.assertTrue(CatNoir.active(p) && Miraculous.canAct(p), "transformed as Cat Noir and done transforming");
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Miraculous.tick(p);
		});
	}

	/** Every cooldown off (several moves in one test). */
	private static void ready(ServerPlayer p) {
		Miraculous.edit(p, n -> n.abilityReadyAt.clear());
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double y, double z, double health) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		T m = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth((float) health);
		if (m.getAttribute(Attributes.ARMOR) != null) {
			m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		}
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN)); // no burning by day
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	// ---------------------------------------------------------------- the kit

	@GameTest(template = EMPTY_STRUCTURE)
	public void kitRegistersTwelveMovesWithCooldowns(GameTestHelper helper) {
		java.util.Set<String> ids = new java.util.HashSet<>();
		for (AbilitySlot slot : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4,
				AbilitySlot.SLOT_5, AbilitySlot.SLOT_6 }) {
			String[] two = MiraculousKits.of(Kwami.PLAGG).idsOf(slot);
			helper.assertTrue(two.length == 2, "two moves on " + slot);
			for (String id : two) {
				helper.assertTrue(id.startsWith("cat_noir_"), "id prefixed: " + id);
				helper.assertTrue(MiraculousKits.of(Kwami.PLAGG).maxCooldown(id) > 0, "a cooldown for " + id);
				ids.add(id);
			}
		}
		helper.assertTrue(ids.size() == 12, "twelve distinct moves, got " + ids.size());
		helper.assertTrue(MiraculousKits.of(Kwami.PLAGG).idsOf(AbilitySlot.SLOT_4)[1].equals(CatNoir.CATACLYSM), "Shift+Z is CATACLYSM");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R / Shift+R

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void batonStrikeIsThreeHitsOfNine(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = cat(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 3.5, 100);
		pump(helper, p);
		CatNoirAbilities.strike(p);
		helper.assertTrue(Miraculous.state(p).animId == CatNoir.ANIM_STRIKE, "the combo pose plays");
		helper.runAfterDelay(CatNoirConfig.STRIKE_TICKS + 2, () -> {
			float taken = 100f - z.getHealth();
			float want = CatNoirConfig.STRIKE_DAMAGE * 3;
			helper.assertTrue(Math.abs(taken - want) < EPS * 3, "three swings of 9 = 27, took " + taken);
			helper.assertTrue(Miraculous.cooldownRemaining(p, CatNoir.STRIKE) > 0, "on cooldown");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void lowSweepTripsForSix(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = cat(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 3.0, 100);
		pump(helper, p);
		CatNoirAbilities.sweep(p);
		helper.runAfterDelay(CatNoirConfig.SWEEP_TICKS, () -> {
			float taken = 100f - z.getHealth();
			helper.assertTrue(Math.abs(taken - CatNoirConfig.SWEEP_DAMAGE) < EPS, "6 at the legs, took " + taken);
			MobEffectInstance slow = z.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
			helper.assertTrue(slow != null && slow.getAmplifier() == CatNoirConfig.SWEEP_SLOW_AMPLIFIER, "tripped: Slowness IV");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- G / Shift+G

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void batonExtendReachesFarForFourteenWithKnockback(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = cat(helper, 3.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 100);
		double z0 = z.getZ();
		pump(helper, p);
		CatNoirAbilities.extend(p);
		helper.assertTrue(CatNoir.val(p, CatNoir.V_EXTEND_LENGTH) > 400L, "the staff reaches out past 4 blocks: "
				+ CatNoir.val(p, CatNoir.V_EXTEND_LENGTH));
		helper.runAfterDelay(CatNoirConfig.EXTEND_OUT_TICKS + 1, () -> {
			float taken = 100f - z.getHealth();
			helper.assertTrue(Math.abs(taken - CatNoirConfig.EXTEND_DAMAGE) < EPS, "14 at 6 blocks, took " + taken);
			helper.assertTrue(z.getDeltaMovement().z > 0.3 || z.getZ() - z0 > 0.5, "slammed away: v=" + z.getDeltaMovement());
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void batonThrowHitsForTwelveAndComesBack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = cat(helper, 3.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.0, 100);
		pump(helper, p);
		CatNoirAbilities.throwBaton(p);
		helper.assertTrue(CatNoir.thrownId(p) >= 0, "the baton is flying");
		helper.succeedWhen(() -> {
			float taken = 100f - z.getHealth();
			helper.assertTrue(Math.abs(taken - CatNoirConfig.THROW_DAMAGE) < EPS, "12 from the throw, took " + taken);
			helper.assertTrue(CatNoir.thrownId(p) < 0, "back in his hand");
		});
	}

	// ---------------------------------------------------------------- X / Shift+X

	/** v0.16.6 (user): the Pole Vault follows the look and keeps extending while X is held; letting go flings him. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void poleVaultExtendsAlongTheLookWhileHeld(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = cat(helper, 3.5, 2.6, 1.5);
		p.setYRot(0f);
		p.setXRot(0f); // looking level along +Z: the pole still tips him up a little
		pump(helper, p);
		CatNoirAbilities.vault(p);
		helper.assertTrue(Miraculous.vec(Miraculous.state(p), CatNoir.VEC_VAULT_BASE) != null, "the baton is planted on the floor");
		// v0.16.7 (user): turning once the pole is extending does not steer it
		helper.runAfterDelay(CatNoirConfig.VAULT_PLANT_TICKS + 1, () -> p.setYRot(90f));
		helper.runAfterDelay(CatNoirConfig.VAULT_PLANT_TICKS + 2, () -> {
			net.minecraft.world.phys.Vec3 v = p.getDeltaMovement();
			helper.assertTrue(v.z > 0.6 && v.y > 0.1 && Math.abs(v.x) < 0.1,
					"carried along the look it had when the pole started extending (+Z), not the new one (-X), got " + v);
			helper.assertTrue(Miraculous.state(p).val(CatNoir.V_VAULT_ON) != 0L, "still extending");
			helper.assertTrue(Miraculous.cooldownRemaining(p, CatNoir.VAULT) == 0, "no cooldown while it is held");
			CatNoirAbilities.releaseVault(p);
		});
		helper.runAfterDelay(CatNoirConfig.VAULT_PLANT_TICKS + CatNoirConfig.VAULT_MIN_EXTEND_TICKS + 4, () -> {
			helper.assertTrue(Miraculous.state(p).val(CatNoir.V_VAULT_ON) == 0L, "let go: off the pole");
			helper.assertTrue(Miraculous.cooldownRemaining(p, CatNoir.VAULT) > 0, "the cooldown starts at the fling");
			helper.succeed();
		});
	}

	/** v0.16.6: looking straight up, the pole carries him straight up for as long as X is held (no limit). */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void poleVaultStraightUpKeepsRising(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = cat(helper, 3.5, 2.6, 3.5);
		p.setXRot(-90f);
		pump(helper, p);
		CatNoirAbilities.vault(p);
		helper.runAfterDelay(CatNoirConfig.VAULT_PLANT_TICKS + 2, () -> {
			double vy = p.getDeltaMovement().y;
			helper.assertTrue(Math.abs(vy - CatNoirConfig.VAULT_EXTEND_SPEED) < 0.15, "rising at the extension speed, vy " + vy);
			CatNoirAbilities.releaseVault(p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void rooftopRunClimbsWallsAndSprintsFaster(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = cat(helper, 3.5, 2.0, 3.65);
		helper.assertFalse(p.onClimbable(), "no climbing in the open");
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(3, 3, 4)), Blocks.STONE.defaultBlockState(), 3);
		helper.assertFalse(p.onClimbable(), "a wall alone is not a ladder");
		CatNoirAbilities.rooftop(p);
		helper.assertTrue(CatNoir.rooftop(p), "Rooftop Run on");
		helper.assertTrue(p.onClimbable(), "against the wall he climbs");
		p.setSprinting(true);
		double base = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		Miraculous.tick(p);
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > base * 1.2, "sprints faster: " + base + " -> "
				+ p.getAttributeValue(Attributes.MOVEMENT_SPEED));
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT) - 2.0) < 1e-6, "v0.16.7: steps up 2 blocks, got " + p.getAttributeValue(Attributes.STEP_HEIGHT));
		ready(p);
		CatNoirAbilities.rooftop(p);
		Miraculous.tick(p);
		helper.assertFalse(CatNoir.rooftop(p) || p.onClimbable(), "off again: no climbing");
		helper.assertTrue(p.getAttributeValue(Attributes.STEP_HEIGHT) < 1.0, "and no step assist");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Z / Shift+Z

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void batonSpinHitsForEightAndBatsBackAnArrow(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = cat(helper, 3.5, 2.0, 2.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 5.5, 2.0, 2.5, 100);
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 3.3, 5.5));
		arrow.moveTo(at.x, at.y, at.z);
		arrow.setDeltaMovement(0, 0, -0.4);
		helper.getLevel().addFreshEntity(arrow);
		CatNoirAbilities.spin(p);
		helper.assertTrue(CatNoirAbilities.running(p, CatNoir.SPIN), "spinning");
		int n = CatNoirAbilities.deflect(p);
		helper.assertTrue(n >= 1 && arrow.getDeltaMovement().z > 0 && arrow.getOwner() == p, "the arrow is batted back, now his: " + n + " "
				+ arrow.getDeltaMovement());
		pump(helper, p);
		helper.runAfterDelay(6, () -> {
			float taken = 100f - z.getHealth();
			helper.assertTrue(Math.abs(taken - CatNoirConfig.SPIN_DAMAGE) < EPS, "8 from the spin, took " + taken);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void cataclysmCrumblesTheConnectedBlocksWithinTheRadius(GameTestHelper helper) {
		ServerPlayer p = cat(helper, 0.5, 2.0, 0.5);
		pump(helper, p);
		// v0.16.7 (user): radius 8. A beam of 8 stone at y 4 along x, then a bend up and back into the far corner (its end
		// (7,7,7) is ~8.6 from the touch, out of reach), a block hanging off its start, and one loose block close by
		for (int x = 0; x <= 7; x++) {
			helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 4, 3)), Blocks.STONE.defaultBlockState(), 3);
		}
		for (int y = 5; y <= 7; y++) {
			helper.getLevel().setBlock(helper.absolutePos(new BlockPos(7, y, 3)), Blocks.STONE.defaultBlockState(), 3);
		}
		for (int z = 4; z <= 7; z++) {
			helper.getLevel().setBlock(helper.absolutePos(new BlockPos(7, 7, z)), Blocks.STONE.defaultBlockState(), 3);
		}
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(0, 4, 2)), Blocks.COBBLESTONE.defaultBlockState(), 3);
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(1, 4, 5)), Blocks.STONE.defaultBlockState(), 3);
		CatNoirCataclysm.call(p);
		helper.assertTrue(CatNoir.charged(p), "the hand crackles");
		helper.assertTrue(Miraculous.countdownLeft(p) > 0, "calling it starts the 3-minute countdown");
		BlockPos touch = helper.absolutePos(new BlockPos(0, 4, 3));
		InteractionResult r = AttackBlockCallback.EVENT.invoker().interact(p, helper.getLevel(), InteractionHand.MAIN_HAND, touch, Direction.UP);
		helper.assertTrue(r == InteractionResult.SUCCESS, "the punch is the touch");
		helper.assertTrue(!CatNoir.charged(p) && CatNoir.spent(p) && !CatNoir.rotted(p), "spent, and the baton stays silver");
		helper.runAfterDelay(10 * CatNoirConfig.CATACLYSM_STEP_TICKS + CatNoirConfig.CATACLYSM_CRACK_TICKS + 4, () -> {
			helper.assertTrue(CatNoirConfig.CATACLYSM_RADIUS == 8.0, "radius 8");
			for (int x = 0; x <= 7; x++) {
				helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, 4, 3));
			}
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(0, 4, 2));
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(7, 7, 3)); // 7.6 away: gone
			helper.assertBlockPresent(Blocks.STONE, new BlockPos(7, 7, 7)); // 8.6 away: out of reach
			helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, 4, 5));
			ready(p);
			CatNoirCataclysm.call(p);
			helper.assertFalse(CatNoir.charged(p), "one Cataclysm per transformation");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void cataclysmOnAMobIsFortyWitherAndShatteredArmour(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = cat(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 3.0, 200); // v0.16.7: 100 damage now, so a 200-health target
		z.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		z.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
		pump(helper, p);
		CatNoirCataclysm.call(p);
		InteractionResult r = AttackEntityCallback.EVENT.invoker().interact(p, helper.getLevel(), InteractionHand.MAIN_HAND, z, null);
		helper.assertTrue(r == InteractionResult.SUCCESS, "the blow is the touch");
		// the armour shatters first, so the full 100 lands
		float taken = 200f - z.getHealth();
		helper.assertTrue(Math.abs(taken - CatNoirConfig.CATACLYSM_DAMAGE) < EPS && CatNoirConfig.CATACLYSM_DAMAGE == 100f,
				"100 through the shattered armour, took " + taken);
		MobEffectInstance wither = z.getEffect(MobEffects.WITHER);
		helper.assertTrue(wither != null && wither.getAmplifier() == 4 && wither.getDuration() <= 140, "Wither V for 7 s");
		helper.assertTrue(z.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && z.getItemBySlot(EquipmentSlot.FEET).isEmpty(), "its armour shattered");
		helper.assertTrue(CatNoir.spent(p) && !CatNoir.rotted(p) && !CatNoir.charged(p), "spent, the baton not rotted");
		helper.succeed();
	}

	// ---------------------------------------------------------------- V / Shift+V

	@GameTest(template = EMPTY_STRUCTURE)
	public void catLeapCoversTwelveBlocks(GameTestHelper helper) {
		ServerPlayer p = cat(helper, 3.5, 2.0, 3.5);
		Vec3 v = CatNoirAbilities.leapVelocity(new Vec3(0, 0, 1), true);
		double x = 0, y = 0, h = v.horizontalDistance(), vy = v.y;
		for (int t = 0; t < 200; t++) {
			x += h;
			y += vy;
			if (y <= 0 && vy < 0) {
				break;
			}
			vy = (vy - 0.08) * 0.98;
			h *= t == 0 ? 0.546 : 0.91;
		}
		helper.assertTrue(Math.abs(x - CatNoirConfig.LEAP_DISTANCE) < 1.0, "a 12-block pounce, simulated " + x);
		CatNoirAbilities.leap(p);
		helper.assertTrue(p.getDeltaMovement().y > 0.1 && Miraculous.state(p).animId == CatNoir.ANIM_LEAP, "launched, leap pose");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void pounceStrikeLandsForTwelve(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = cat(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 3.0, 100);
		pump(helper, p);
		CatNoirAbilities.pounce(p);
		helper.assertTrue(CatNoirAbilities.running(p, CatNoir.POUNCE), "pouncing on the zombie");
		helper.runAfterDelay(6, () -> {
			float taken = 100f - z.getHealth();
			helper.assertTrue(Math.abs(taken - CatNoirConfig.POUNCE_DAMAGE) < EPS, "12 on landing, took " + taken);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- C / Shift+C

	@GameTest(template = EMPTY_STRUCTURE)
	public void nightVisionTogglesTheCatEyes(GameTestHelper helper) {
		ServerPlayer p = cat(helper, 3.5, 2.0, 3.5);
		CatNoirAbilities.nightVision(p);
		helper.assertTrue(CatNoir.nightVision(p) && p.hasEffect(MobEffects.NIGHT_VISION), "cat eyes on: Night Vision");
		ready(p);
		CatNoirAbilities.nightVision(p);
		helper.assertTrue(!CatNoir.nightVision(p) && !p.hasEffect(MobEffects.NIGHT_VISION), "off again");
		ready(p);
		CatNoirAbilities.nightVision(p);
		Miraculous.detransform(p, false);
		helper.assertFalse(p.hasEffect(MobEffects.NIGHT_VISION), "detransforming takes the cat eyes away");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void catReflexesDodgeTheNextThreeHits(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = cat(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 3.5, 100);
		CatNoirAbilities.reflexes(p);
		helper.assertTrue(CatNoir.reflexesLeft(p) == CatNoirConfig.REFLEX_DODGES, "three dodges ready");
		float hp = p.getHealth();
		for (int i = 0; i < CatNoirConfig.REFLEX_DODGES; i++) {
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().mobAttack(z), 4f);
			helper.assertTrue(p.getHealth() == hp, "hit " + (i + 1) + " dodged");
		}
		helper.assertTrue(CatNoir.reflexesLeft(p) == 0, "all three used");
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().mobAttack(z), 4f);
		helper.assertTrue(p.getHealth() < hp, "the fourth lands");
		helper.succeed();
	}

	/** v0.16.7: a full-size Cataclysm crumble's VFX payload encodes (it once went over the list limit and kicked the player). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void cataclysmFxPayloadCarriesAFullCrumble(GameTestHelper helper) {
		helper.assertTrue(CatNoirConfig.CATACLYSM_MAX_BLOCKS <= com.projecthero.mod.miraculous.catnoir.CataclysmFxPayload.MAX_BLOCKS,
				"the block cap fits in one payload");
		java.util.List<BlockPos> blocks = new java.util.ArrayList<>();
		for (int i = 0; i < CatNoirConfig.CATACLYSM_MAX_BLOCKS; i++) {
			blocks.add(new BlockPos(i % 17, i / 289, (i / 17) % 17));
		}
		var payload = new com.projecthero.mod.miraculous.catnoir.CataclysmFxPayload(BlockPos.ZERO, blocks, -1, 0);
		var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
		com.projecthero.mod.miraculous.catnoir.CataclysmFxPayload.CODEC.encode(buf, payload);
		var back = com.projecthero.mod.miraculous.catnoir.CataclysmFxPayload.CODEC.decode(buf);
		helper.assertTrue(back.blocks().size() == blocks.size(), "all " + blocks.size() + " blocks round-trip");
		helper.succeed();
	}
}
