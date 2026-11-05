package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.upchuck.UpchuckKit;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Upchuck (user: "heres upchucks skin please build him ... do research on his powers and look at what he does in the show ...
 * Make this another alien you unlock on the 11th mistransformation"): a hidden prototype alien found by a mistransform; every key has
 * a move; what he eats fills his Gut (blocks only where he could break them, never a container), his loogies spend it and hit harder
 * the fuller he is; his tongues reel creatures in, he swallows small ones whole and spits them back out, gobbles shots out of the air.
 */
public class OmnitrixUpchuckGameTests implements FabricGameTest {
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
			for (int z = 0; z <= 9; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static ServerPlayer upchuck(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		Aliens.discover(p, UpchuckKit.ID, true); // found by a mistransform (the pick test below)
		helper.assertTrue(Omnitrix.select(p, UpchuckKit.ID), "Upchuck is on the prototype's dial once found");
		helper.assertTrue(Omnitrix.transform(p), "transformed into Upchuck");
		helper.onEachTick(() -> Omnitrix.tick(p));
		return p;
	}

	private static UpchuckKit kit() {
		return (UpchuckKit) Aliens.kit(UpchuckKit.ID);
	}

	private static void tap(ServerPlayer p, AbilitySlot slot, boolean shift) {
		p.setShiftKeyDown(shift);
		AbilityRouter.handleInput(p, slot.number(), true);
		AbilityRouter.handleInput(p, slot.number(), false);
		p.setShiftKeyDown(false);
	}

	private static void hold(ServerPlayer p, AbilitySlot slot) {
		AbilityRouter.handleInput(p, slot.number(), true);
	}

	private static Zombie zombie(GameTestHelper helper, double x, double y, double z) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static void lookAt(ServerPlayer p, Vec3 to) {
		Vec3 d = to.subtract(p.getEyePosition());
		float yaw = (float) (Math.atan2(-d.x, d.z) * 180 / Math.PI);
		float pitch = (float) (-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180 / Math.PI);
		p.setYRot(yaw);
		p.setXRot(pitch);
		p.setYHeadRot(yaw);
	}

	// ---------------------------------------------------------------- the dial

	@GameTest(template = EMPTY_STRUCTURE)
	public void upchuckIsAHiddenPrototypeAlien(GameTestHelper helper) {
		helper.assertTrue(Aliens.byId(UpchuckKit.ID) != null && !Aliens.byId(UpchuckKit.ID).recalibrated(), "declared on the prototype");
		helper.assertTrue(Aliens.hidden(UpchuckKit.ID) && !Aliens.itemOnly(UpchuckKit.ID) && Aliens.mistransformFinds().contains(Aliens.UPCHUCK),
				"hidden, found by a mistransform");
		helper.assertTrue(Aliens.dial(false).contains(Aliens.UPCHUCK) && !Aliens.dial(true).contains(Aliens.UPCHUCK), "on the prototype's dial only");
		ServerPlayer p = mock(helper, 1.5, 2, 1.5);
		Omnitrix.grant(p);
		helper.assertFalse(Aliens.playable(p).contains(Aliens.UPCHUCK), "not on the dial before he is found");
		helper.assertFalse(Omnitrix.select(p, UpchuckKit.ID), "and cannot be dialled");
		helper.assertTrue(Aliens.unfound(p).contains(Aliens.UPCHUCK), "a mistransform can find him");
		helper.assertTrue(kit().stats().scale() <= 0.6f, "a small alien");
		helper.assertTrue(com.projecthero.mod.omnitrix.OmnitrixInfo.icon(Aliens.UPCHUCK).getString().equals(String.valueOf((char) 0xE018)),
				"his dial glyph is U+E018");
		helper.succeed();
	}

	/** v0.17.2 (user: "make it an even chance"): he comes up as often as each other hidden alien, and never once found. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void heIsInTheEvenHiddenPick(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 1.5, 2, 1.5);
		Omnitrix.grant(p);
		int pool = Aliens.unfound(p).size();
		helper.assertTrue(pool >= 4, "Cannonbolt, Wildvine, Ditto and Upchuck all to find: " + Aliens.unfound(p));
		RandomSource r = RandomSource.create(4242L);
		int mine = 0, draws = 4000;
		for (int i = 0; i < draws; i++) {
			if (Aliens.pickHidden(p, r) == Aliens.UPCHUCK) {
				mine++;
			}
		}
		double share = mine / (double) draws;
		helper.assertTrue(Math.abs(share - 1.0 / pool) < 0.05, "Upchuck came up " + share + ", expected about " + 1.0 / pool);
		Aliens.discover(p, UpchuckKit.ID, true);
		for (int i = 0; i < 200; i++) {
			helper.assertFalse(Aliens.pickHidden(p, r) == Aliens.UPCHUCK, "found: never picked again");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyKeyHasAMoveAndNoneIsOnHOrN(GameTestHelper helper) {
		AlienKit k = kit();
		for (AbilitySlot s : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5,
				AbilitySlot.SLOT_6 }) {
			helper.assertTrue(k.moveOn(s, false, false) != null, "a move on " + s);
			helper.assertTrue(k.moveOn(s, true, false) != null, "a Shift move on " + s);
		}
		for (var m : k.moves()) {
			helper.assertTrue(!m.onSlot() || m.slot().ordinal() <= AbilitySlot.SLOT_6.ordinal(), m.id() + " on R/G/X/Z/V/C only");
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- eating

	/** Z: the block he looks at and the four round it are eaten -- nothing drops -- and his Gut fills (ore is rich). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void devourEatsBlocksAndFillsHisGut(GameTestHelper helper) {
		ServerPlayer p = upchuck(helper);
		for (int x = 2; x <= 4; x++) {
			for (int y = 2; y <= 4; y++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, 4)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(3, 3, 4)), Blocks.IRON_ORE.defaultBlockState(), 3);
		lookAt(p, helper.absoluteVec(new Vec3(3.5, 3.5, 4.0)));
		helper.assertTrue(UpchuckKit.gut(p) == 0, "an empty Gut to start");
		tap(p, AbilitySlot.SLOT_4, false);
		helper.assertTrue(helper.getBlockState(new BlockPos(3, 3, 4)).isAir(), "the ore is eaten");
		helper.assertTrue(helper.getBlockState(new BlockPos(2, 3, 4)).isAir() && helper.getBlockState(new BlockPos(4, 3, 4)).isAir()
				&& helper.getBlockState(new BlockPos(3, 2, 4)).isAir() && helper.getBlockState(new BlockPos(3, 4, 4)).isAir(), "and the four round it");
		helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 4)).is(Blocks.STONE), "the corners are not");
		helper.assertTrue(UpchuckKit.gut(p) == 8 + 4 * 2 && UpchuckKit.rich(p) > 0, "the Gut fills, the ore rich: " + UpchuckKit.gut(p) + "/" + UpchuckKit.rich(p));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(10)).isEmpty(),
					"nothing drops: he ate it");
			helper.succeed();
		});
	}

	/** v0.17.2 (Upgrade's merge refusals): never a block he couldn't break -- adventure mode -- and never a container. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void devourRefusesAdventureModeAndContainers(GameTestHelper helper) {
		ServerPlayer p = upchuck(helper);
		BlockPos chest = new BlockPos(3, 2, 3);
		helper.getLevel().setBlock(helper.absolutePos(chest), Blocks.CHEST.defaultBlockState(), 3);
		lookAt(p, helper.absoluteVec(new Vec3(3.5, 2.5, 3.0)));
		tap(p, AbilitySlot.SLOT_4, false);
		helper.assertTrue(helper.getBlockState(chest).is(Blocks.CHEST), "a chest is never eaten");
		helper.assertTrue(UpchuckKit.gut(p) == 0, "nothing eaten");
		BlockPos stone = new BlockPos(3, 3, 3);
		helper.getLevel().setBlock(helper.absolutePos(chest), Blocks.AIR.defaultBlockState(), 3);
		helper.getLevel().setBlock(helper.absolutePos(stone), Blocks.STONE.defaultBlockState(), 3);
		p.setGameMode(GameType.ADVENTURE);
		lookAt(p, helper.absoluteVec(new Vec3(3.5, 3.5, 3.0)));
		kit().cooldown(p, UpchuckKit.DEVOUR, 0);
		tap(p, AbilitySlot.SLOT_4, false);
		helper.assertTrue(helper.getBlockState(stone).is(Blocks.STONE), "adventure mode: he can't eat the world");
		helper.getLevel().setBlock(helper.absolutePos(stone), Blocks.BEDROCK.defaultBlockState(), 3);
		p.setGameMode(GameType.SURVIVAL);
		kit().cooldown(p, UpchuckKit.DEVOUR, 0);
		tap(p, AbilitySlot.SLOT_4, false);
		helper.assertTrue(helper.getBlockState(stone).is(Blocks.BEDROCK), "nor anything unbreakable");
		helper.succeed();
	}

	// ---------------------------------------------------------------- spitting

	@GameTest(template = EMPTY_STRUCTURE)
	public void loogiesHitHarderTheFullerHeIs(GameTestHelper helper) {
		helper.assertTrue(UpchuckKit.bombDamage(15, 1f) < UpchuckKit.bombDamage(60, 1f) && UpchuckKit.bombDamage(60, 1f) < UpchuckKit.bombDamage(100, 1f),
				"more Gut, a bigger bomb");
		helper.assertTrue(UpchuckKit.bombDamage(100, 1.2f) > UpchuckKit.bombDamage(100, 1f), "rich food, bigger still");
		helper.assertTrue(UpchuckKit.bombDamage(100, 1.2f) <= 27f && UpchuckKit.bombDamage(0, 1f) >= 9f, "on the prototype's scale");
		helper.assertTrue(UpchuckKit.megaDamage(100, 1f) > UpchuckKit.bombDamage(100, 1f), "the Mega Loogie is the biggest");
		ServerPlayer p = upchuck(helper);
		kit().fill(p, 50, 0);
		tap(p, AbilitySlot.SLOT_1, false);
		helper.assertTrue(UpchuckKit.gut(p) == 50 - UpchuckKit.BOMB_COST, "a Gut Bomb spends 15: " + UpchuckKit.gut(p));
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(AlienProjectileEntity.class, p.getBoundingBox().inflate(4),
				e -> e.kind().equals("upchuck_spit")).size() == 1, "a loogie flies");
		kit().fill(p, 5, 0);
		kit().cooldown(p, UpchuckKit.BOMB, 0);
		tap(p, AbilitySlot.SLOT_1, false);
		helper.assertTrue(UpchuckKit.gut(p) == 5, "too hungry: a free Slime Spit");
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(AlienProjectileEntity.class, p.getBoundingBox().inflate(4),
				e -> e.kind().equals("upchuck_slime_spit")).size() == 1, "a Slime Spit flies");
		// the ultimate, on Shift+Z (user: "make gobble the c ability and mega loogie shift+Z"): 40 at least, then all of it, after his heave
		kit().fill(p, 30, 0);
		tap(p, AbilitySlot.SLOT_4, true);
		helper.assertTrue(kit().cooldownLeft(p, UpchuckKit.MEGA) == 0 && UpchuckKit.gut(p) == 30, "no Mega Loogie under 40");
		kit().fill(p, 90, 30);
		tap(p, AbilitySlot.SLOT_4, true);
		helper.assertTrue(kit().cooldownLeft(p, UpchuckKit.MEGA) > 0 && UpchuckKit.gut(p) == 90, "Shift+Z starts the heave, the Gut still in him");
		helper.runAfterDelay(UpchuckKit.MEGA_WINDUP + 2, () -> {
			helper.assertTrue(UpchuckKit.gut(p) == 0 && UpchuckKit.rich(p) == 0, "the Mega Loogie empties him");
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(AlienProjectileEntity.class, p.getBoundingBox().inflate(12),
					e -> e.kind().equals("upchuck_mega")).size() == 1, "and it flies");
			helper.succeed();
		});
	}

	/** v0.17.2 (user: "make gobble the c ability and mega loogie shift+Z"): the keys are swapped; Devour and the Frenzy stay. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void gobbleIsOnCAndMegaLoogieOnShiftZ(GameTestHelper helper) {
		AlienKit k = kit();
		helper.assertTrue(UpchuckKit.GOBBLE.equals(k.moveOn(AbilitySlot.SLOT_6, false, false).id()), "C is Gobble");
		helper.assertTrue(UpchuckKit.MEGA.equals(k.moveOn(AbilitySlot.SLOT_4, true, false).id()), "Shift+Z is the Mega Loogie");
		helper.assertTrue(UpchuckKit.DEVOUR.equals(k.moveOn(AbilitySlot.SLOT_4, false, false).id()), "Z stays Devour");
		helper.assertTrue(UpchuckKit.FRENZY.equals(k.moveOn(AbilitySlot.SLOT_6, true, false).id()), "Shift+C stays the Feeding Frenzy");
		helper.succeed();
	}

	/** v0.17.2 (user: "upgrade his abilities vfx and models please"): a bomb that lands leaves its explosion and a splat, synced, gone again. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void aLoogieLeavesAnExplosionAndASplatThenTheyGo(GameTestHelper helper) {
		ServerPlayer p = upchuck(helper);
		lookAt(p, helper.absoluteVec(new Vec3(3.5, 2.0, 4.5)));
		kit().fill(p, 100, 0);
		tap(p, AbilitySlot.SLOT_1, false);
		boolean[] seen = new boolean[2];
		helper.onEachTick(() -> {
			var all = helper.getLevel().getEntitiesOfClass(AlienProjectileEntity.class, new AABB(p.blockPosition()).inflate(16));
			seen[0] |= all.stream().anyMatch(e -> e.kind().equals("upchuck_burst") && e.held());
			seen[1] |= all.stream().anyMatch(e -> e.kind().equals("upchuck_puddle") && e.held());
		});
		helper.runAfterDelay(30 + UpchuckKit.SPLAT_TICKS, () -> {
			helper.assertTrue(seen[0], "an explosion");
			helper.assertTrue(seen[1], "a splat on the ground");
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(AlienProjectileEntity.class, new AABB(p.blockPosition()).inflate(16),
					e -> e.kind().startsWith("upchuck")).isEmpty(), "both gone again");
			helper.succeed();
		});
	}

	/** A Gut Bomb into a zombie: it lands, it hurts. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void aGutBombHurtsWhatItHits(GameTestHelper helper) {
		ServerPlayer p = upchuck(helper);
		Zombie z = zombie(helper, 3.5, 2, 7.5);
		lookAt(p, z.position().add(0, 1.0, 0));
		kit().fill(p, 100, 0);
		tap(p, AbilitySlot.SLOT_1, false);
		helper.succeedWhen(() -> helper.assertTrue(z.getHealth() < z.getMaxHealth() - 8f || !z.isAlive(), "the loogie hit: " + z.getHealth()));
	}

	// ---------------------------------------------------------------- tongues

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void tongueGrabReelsACreatureInForAChomp(GameTestHelper helper) {
		ServerPlayer p = upchuck(helper);
		Zombie z = zombie(helper, 3.5, 2, 7.5);
		lookAt(p, z.position().add(0, 1.0, 0));
		double before = z.distanceTo(p);
		tap(p, AbilitySlot.SLOT_2, false);
		helper.assertTrue(UpchuckKit.grabbing(p), "the tongues latch on");
		helper.runAfterDelay(UpchuckKit.GRAB_TICKS + 3, () -> {
			helper.assertTrue(z.distanceTo(p) < before - 3, "reeled in: " + before + " -> " + z.distanceTo(p));
			helper.assertTrue(z.getHealth() <= z.getMaxHealth() - UpchuckKit.CHOMP_DAMAGE + 0.5f, "chomped: " + z.getHealth());
			helper.assertTrue(UpchuckKit.gut(p) >= 6, "a mouthful: " + UpchuckKit.gut(p));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void swallowWholeDigestsThenSpitsItOut(GameTestHelper helper) {
		ServerPlayer p = upchuck(helper);
		Zombie z = zombie(helper, 3.5, 2, 3.5);
		z.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 1)); // it survives to be spat out
		lookAt(p, z.position().add(0, 1.0, 0));
		tap(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(UpchuckKit.swallowed(p) == z, "gulped down");
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(z.distanceTo(p) < 0.5, "inside him");
			helper.assertTrue(UpchuckKit.gut(p) > 0 && kit().flag(p, UpchuckKit.F_FULL), "being digested; he's stuffed");
		});
		helper.runAfterDelay(UpchuckKit.GULP_TICKS + 4, () -> {
			helper.assertTrue(UpchuckKit.swallowed(p) == null && z.isAlive(), "spat back out, alive");
			helper.assertTrue(z.getHealth() < z.getMaxHealth(), "a little digested");
			helper.assertFalse(z.hasEffect(MobEffects.INVISIBILITY), "seen again");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void swallowRefusesBigOnes(GameTestHelper helper) {
		ServerPlayer p = upchuck(helper);
		var golem = EntityType.IRON_GOLEM.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2, 4.0));
		golem.moveTo(at.x, at.y, at.z, 180f, 0f);
		golem.setNoAi(true);
		helper.getLevel().addFreshEntity(golem);
		lookAt(p, golem.position().add(0, 1.4, 0));
		tap(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(UpchuckKit.swallowed(p) == null && kit().cooldownLeft(p, UpchuckKit.SWALLOW) == 0, "an iron golem is too big to swallow");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Gobble, Digest, Full Cheeks

	@GameTest(template = EMPTY_STRUCTURE)
	public void gobbleEatsShotsAndItems(GameTestHelper helper) {
		ServerPlayer p = upchuck(helper);
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.8, 4.5));
		arrow.moveTo(at.x, at.y, at.z);
		arrow.setDeltaMovement(0, 0, -0.01);
		helper.getLevel().addFreshEntity(arrow);
		ItemEntity bread = new ItemEntity(helper.getLevel(), at.x + 1, at.y - 0.5, at.z - 1, new ItemStack(Items.BREAD));
		helper.getLevel().addFreshEntity(bread);
		hold(p, AbilitySlot.SLOT_6);
		helper.assertTrue(kit().flag(p, UpchuckKit.F_GOBBLE), "mouth open");
		helper.assertFalse(kit().allowDamage(p, p.damageSources().arrow(arrow, null), 4f), "a shot can't hurt him while he gobbles");
		helper.runAfterDelay(1, () -> {
			helper.assertFalse(arrow.isRemoved() || bread.isRemoved(), "they fly to his mouth, they don't vanish where they are");
			helper.assertTrue(bread.hasPickUpDelay(), "and he eats it rather than pocketing it");
		});
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(arrow.isRemoved() && bread.isRemoved(), "the arrow and the bread went down");
			helper.assertTrue(UpchuckKit.gut(p) >= 5 + 5, "both fill his Gut: " + UpchuckKit.gut(p));
			helper.succeed();
		});
	}

	/** v0.17.2 (user: "remove gobbles time limit and make it last as long as player holds C"). */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void gobbleLastsExactlyAsLongAsCIsHeld(GameTestHelper helper) {
		ServerPlayer p = upchuck(helper);
		AbilityRouter.handleInput(p, AbilitySlot.SLOT_6.number(), true);
		helper.assertTrue(kit().flag(p, UpchuckKit.F_GOBBLE), "open the moment C goes down");
		helper.assertTrue(kit().keyActive(p, AbilitySlot.SLOT_6), "C lights");
		helper.assertTrue(kit().cooldownLeft(p, UpchuckKit.GOBBLE) == 0, "no cooldown while it's open");
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(kit().flag(p, UpchuckKit.F_GOBBLE), "still open after 6 s, twice the old limit");
			AbilityRouter.handleInput(p, AbilitySlot.SLOT_6.number(), false);
			helper.assertFalse(kit().flag(p, UpchuckKit.F_GOBBLE), "shut the moment C comes up");
			helper.assertTrue(kit().cooldownLeft(p, UpchuckKit.GOBBLE) == 120, "6 s from the release: " + kit().cooldownLeft(p, UpchuckKit.GOBBLE));
			tap(p, AbilitySlot.SLOT_6, false);
			helper.assertFalse(kit().flag(p, UpchuckKit.F_GOBBLE), "not again until it's ready");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void digestHealsAndFullCheeksSlowsHim(GameTestHelper helper) {
		ServerPlayer p = upchuck(helper);
		tap(p, AbilitySlot.SLOT_5, false);
		helper.assertTrue(kit().cooldownLeft(p, UpchuckKit.DIGEST) == 0, "nothing to digest yet");
		kit().fill(p, 90, 0);
		p.setHealth(p.getMaxHealth() - 10f);
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
		float hp = p.getHealth();
		tap(p, AbilitySlot.SLOT_5, false);
		helper.assertTrue(p.getHealth() >= hp + UpchuckKit.DIGEST_HEAL - 0.1f && UpchuckKit.gut(p) == 70, "20 Gut into 6 health");
		helper.assertFalse(p.hasEffect(MobEffects.POISON), "Iron Stomach: no poison");
		kit().fill(p, 90, 0);
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(kit().flag(p, UpchuckKit.F_FULL) && p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Full Cheeks over 75: slowed");
			kit().fill(p, 40, 0);
			helper.runAfterDelay(25, () -> {
				helper.assertFalse(kit().flag(p, UpchuckKit.F_FULL), "not stuffed at 40");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void vomitRocketLaunchesHimAndTurningBackEmptiesHim(GameTestHelper helper) {
		ServerPlayer p = upchuck(helper);
		kit().fill(p, 50, 10);
		lookAt(p, p.getEyePosition().add(0, 0.2, 5));
		tap(p, AbilitySlot.SLOT_3, false);
		helper.assertTrue(p.getDeltaMovement().horizontalDistance() > 1.0 && kit().flag(p, UpchuckKit.F_ROCKET), "launched, no fall damage");
		helper.assertTrue(UpchuckKit.gut(p) == 50 - UpchuckKit.ROCKET_COST, "8 Gut of vomit");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertTrue(Omnitrix.state(p).readyAt(UpchuckKit.ID + "." + UpchuckKit.GUT) == 0L, "turning back empties his Gut");
		helper.succeed();
	}
}
