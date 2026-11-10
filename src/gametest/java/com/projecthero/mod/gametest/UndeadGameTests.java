package com.projecthero.mod.gametest;

import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadAbilities;
import com.projecthero.mod.undead.UndeadBoneSword;
import com.projecthero.mod.undead.UndeadConfig;
import com.projecthero.mod.undead.UndeadParts;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadCloneEntity;
import com.projecthero.mod.undead.entity.UndeadPartEntity;
import com.projecthero.mod.undead.item.UndeadItems;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.kryptonian.Kryptonian;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Undead (Undead Unluck): where hits land, a part breaking and regrowing (for hunger), Undead (lethal damage shatters,
 * /kill still kills), the card and the one-Primary rule, the speed cycle, Victor, and each move's server side. Mock players
 * are not ticked by the server, so tests over time drive {@link Undead#tick}.
 */
public class UndeadGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		p.setYHeadRot(0f);
		p.yBodyRot = 0f;
		p.invulnerableTime = 0;
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	private static ServerPlayer undead(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Undead.grant(p);
		return p;
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<UndeadState> fn) {
		UndeadState n = Undead.state(p).copy();
		fn.accept(n);
		p.setAttached(ModAttachments.UNDEAD_STATE, n);
	}

	private static Zombie zombie(GameTestHelper helper, int x, int z) {
		Zombie zo = helper.spawn(EntityType.ZOMBIE, new BlockPos(x, 2, z));
		zo.setNoAi(true);
		zo.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		zo.setHealth(200);
		return zo;
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void hitsLandOnThePartTheyTouch(GameTestHelper helper) {
		ServerPlayer p = undead(helper); // yaw 0: facing +z, his right is -x
		AABB b = p.getBoundingBox();
		Vec3 c = b.getCenter();
		double h = b.getYsize();
		helper.assertTrue(UndeadParts.locate(p, new Vec3(c.x, b.minY + h * 0.9, c.z)) == UndeadState.HEAD, "the top quarter is the head");
		helper.assertTrue(UndeadParts.locate(p, new Vec3(c.x, b.minY + h * 0.55, c.z)) == UndeadState.BODY, "the middle is the body");
		helper.assertTrue(UndeadParts.locate(p, new Vec3(c.x - 0.28, b.minY + h * 0.55, c.z)) == UndeadState.RIGHT_ARM, "his right side is the right arm");
		helper.assertTrue(UndeadParts.locate(p, new Vec3(c.x + 0.28, b.minY + h * 0.55, c.z)) == UndeadState.LEFT_ARM, "his left side is the left arm");
		helper.assertTrue(UndeadParts.locate(p, new Vec3(c.x - 0.1, b.minY + h * 0.2, c.z)) == UndeadState.RIGHT_LEG, "low right is the right leg");
		helper.assertTrue(UndeadParts.locate(p, new Vec3(c.x + 0.1, b.minY + h * 0.2, c.z)) == UndeadState.LEFT_LEG, "low left is the left leg");
		float[] fall = UndeadParts.assign(p, p.damageSources().fall(), 6f, p.getRandom());
		helper.assertTrue(fall[UndeadState.RIGHT_LEG] == 3f && fall[UndeadState.LEFT_LEG] == 3f && fall[UndeadState.HEAD] == 0f, "a fall lands on both legs");
		float[] blast = UndeadParts.assign(p, p.damageSources().explosion(null, null), 10f, p.getRandom());
		for (float f : blast) {
			helper.assertTrue(f > 0f, "an explosion hits every part");
		}
		float[] magic = UndeadParts.assign(p, p.damageSources().magic(), 10f, p.getRandom());
		for (float f : magic) {
			helper.assertTrue(f == 0f, "magic hits no part");
		}
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anArrowBreaksTheArmItHits(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		AABB b = p.getBoundingBox();
		net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(helper.getLevel(), b.getCenter().x - 0.27,
				b.minY + b.getYsize() * 0.55, b.getCenter().z, new ItemStack(net.minecraft.world.item.Items.ARROW), null);
		boolean hurt = p.hurt(p.damageSources().arrow(arrow, null), 9f);
		helper.assertTrue(Undead.missing(p, UndeadState.RIGHT_ARM), "9 damage on the right arm (8 HP) breaks it: hurt=" + hurt + " hp=" + Undead.state(p).partHp);
		helper.assertTrue(!Undead.missing(p, UndeadState.LEFT_ARM) && !Undead.missing(p, UndeadState.BODY), "nothing else breaks");
		helper.assertTrue(Undead.state(p).kind(UndeadState.RIGHT_ARM) == UndeadState.KIND_BURST, "it bursts into pixels");
		helper.succeed();
	}

	// v0.17.3: the three-layer regrowth takes 170 ticks at Normal -- with the delay that is past 200
	/** Breaks {@code parts} now; they may start regrowing as soon as their own wait is up (the tests then drive the ticks). */
	private static void breakAll(ServerPlayer p, int... parts) {
		for (int part : parts) {
			Undead.breakPart(p, part, UndeadState.KIND_BURST);
		}
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void lethalDamageShattersButKillStillKills(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.hurt(p.damageSources().magic(), 1000f);
		helper.assertTrue(p.isAlive() && p.getHealth() == p.getMaxHealth(), "Undead: alive, health refilled");
		UndeadState s = Undead.state(p);
		helper.assertTrue(!s.broken(UndeadState.HEAD), "the head stays");
		for (int i = UndeadState.BODY; i < UndeadState.PARTS; i++) {
			helper.assertTrue(s.broken(i) && s.kind(i) == UndeadState.KIND_SHATTER, "part " + i + " shattered");
		}
		helper.assertTrue(Undead.reforming(p), "the grace after the shatter");
		float hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().magic(), 5f);
		helper.assertTrue(p.getHealth() == hp, "nothing hurts while re-forming");
		p.hurt(p.damageSources().genericKill(), Float.MAX_VALUE);
		helper.assertTrue(!p.isAlive(), "/kill still kills");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theCardGrantsThePowerAndTakesThePrimarySlot(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Kryptonian.grant(p);
		ItemStack card = new ItemStack(UndeadItems.UNDEAD_CARD);
		p.setItemInHand(InteractionHand.MAIN_HAND, card);
		card.use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(Undead.hasPower(p), "the card grants the power");
		helper.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "the card is used up");
		helper.assertTrue(!Kryptonian.hasPower(p), "the old Primary is replaced");
		helper.assertTrue(HeroTiers.holdsHero(p, Undead.KEY), "listed as the held hero");
		helper.assertTrue(PowerGrants.NOT_FROM_SERUMS.contains(Undead.KEY), "never from a serum");
		helper.assertTrue(!Undead.grant(p), "a second grant is refused");
		Undead.revoke(p);
		helper.assertTrue(!Undead.hasPower(p), "revoked");
		helper.succeed();
	}

	// ---------------------------------------------------------------- v0.17.3: NBC Heroes' Regenerative Segmentation

	/** A lost arm (and its hand) grows back at once, one frame a tick: the arm in 18, the hand from the arm's 5th frame in 12. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void segmentsRegrowAtNbcSpeed(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Undead.breakPart(p, UndeadState.LEFT_ARM, UndeadState.KIND_BURST);
		helper.assertTrue(Undead.missing(p, UndeadState.LEFT_HAND), "the hand goes with the arm");
		long[] armStart = { 0L }, handStart = { 0L };
		helper.onEachTick(() -> {
			Undead.tick(p);
			UndeadState s = Undead.state(p);
			long now = helper.getLevel().getGameTime();
			if (armStart[0] == 0L) {
				armStart[0] = s.regrowStart(UndeadState.LEFT_ARM);
				helper.assertTrue(armStart[0] == now, "the arm starts at once (no wait), free");
				helper.assertTrue(s.regrowEnd(UndeadState.LEFT_ARM) - armStart[0] == 18, "an arm is 18 frames");
			}
			if (handStart[0] == 0L && s.regrowStart(UndeadState.LEFT_HAND) > 0L) {
				handStart[0] = s.regrowStart(UndeadState.LEFT_HAND);
				helper.assertTrue(handStart[0] == armStart[0] + UndeadConfig.HAND_NEEDS_ARM, "the hand starts once the arm has 5 frames: " + (handStart[0] - armStart[0]));
				helper.assertTrue(s.regrowEnd(UndeadState.LEFT_HAND) - handStart[0] == 12, "a hand is 12 frames");
			}
			if (now == armStart[0] + 6) {
				helper.assertTrue(s.stage(UndeadState.LEFT_ARM, now) == UndeadState.STAGE_BONE, "frame 6 of the arm: bone");
			}
			if (now == armStart[0] + 12) {
				helper.assertTrue(s.stage(UndeadState.LEFT_ARM, now) == UndeadState.STAGE_FLESH, "frame 12: flesh");
			}
			if (!s.broken(UndeadState.LEFT_ARM) && !s.broken(UndeadState.LEFT_HAND)) {
				helper.assertTrue(now - armStart[0] <= 18 + 2 && now - handStart[0] <= 12 + 2, "whole on time: " + (now - armStart[0]));
				helper.assertTrue(p.getFoodData().getFoodLevel() == 20, "and it cost no food");
				helper.succeed();
			}
		});
	}

	/** From just a head, NBC's order: the body at once, arms and legs at the body's 7th frame, hands and feet at their limbs' 5th. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theBodyGrowsBackInNbcOrder(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Undead.shatter(p);
		long[] body = { 0L };
		java.util.Set<Integer> seenStages = new java.util.LinkedHashSet<>();
		helper.onEachTick(() -> {
			Undead.tick(p);
			UndeadState s = Undead.state(p);
			long now = helper.getLevel().getGameTime();
			seenStages.add(com.projecthero.mod.undead.UndeadStages.stage(s, now));
			if (body[0] == 0L) {
				body[0] = s.regrowStart(UndeadState.BODY);
				helper.assertTrue(body[0] == now, "the head is whole: the body starts at once");
				helper.assertTrue(s.regrowStart(UndeadState.RIGHT_ARM) == 0L && s.regrowStart(UndeadState.RIGHT_LEG) == 0L, "the limbs wait");
				return;
			}
			if (now == body[0] + UndeadConfig.LIMB_NEEDS_BODY + 1) {
				for (int limb = UndeadState.RIGHT_ARM; limb <= UndeadState.LEFT_LEG; limb++) {
					helper.assertTrue(s.regrowStart(limb) == body[0] + UndeadConfig.LIMB_NEEDS_BODY, "limb " + limb + " starts at the body's 7th frame: "
							+ (s.regrowStart(limb) - body[0]));
				}
				helper.assertTrue(s.regrowStart(UndeadState.RIGHT_HAND) == 0L && s.regrowStart(UndeadState.RIGHT_FOOT) == 0L, "hands and feet wait");
			}
			if (now == body[0] + UndeadConfig.LIMB_NEEDS_BODY + UndeadConfig.HAND_NEEDS_ARM + 1) {
				helper.assertTrue(s.regrowStart(UndeadState.LEFT_HAND) == body[0] + 12 && s.regrowStart(UndeadState.LEFT_FOOT) == body[0] + 12,
						"hands and feet at their limbs' 5th frame");
			}
			if (!s.anyBroken()) {
				helper.assertTrue(now - body[0] <= 27 + 2, "whole from a head in about 1.4 s (the legs, 7 + 20 frames): " + (now - body[0]));
				helper.assertTrue(seenStages.contains(com.projecthero.mod.undead.UndeadStages.CRAWL), "he lay on the growing torso: " + seenStages);
				helper.assertTrue(com.projecthero.mod.undead.UndeadStages.stage(s, now) == com.projecthero.mod.undead.UndeadStages.NORMAL, "and walks");
				helper.succeed();
			}
		});
	}

	/** Any way of losing legs gives the stage: one leg a limp, both the hand walk. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theStagesFollowTheParts(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Undead.setRegenOff(p, true);
		Undead.breakPart(p, UndeadState.RIGHT_LEG, UndeadState.KIND_BURST);
		helper.assertTrue(Undead.stage(p) == com.projecthero.mod.undead.UndeadStages.LIMP, "one leg gone: a limp");
		Undead.breakPart(p, UndeadState.LEFT_LEG, UndeadState.KIND_BURST);
		helper.assertTrue(Undead.stage(p) == com.projecthero.mod.undead.UndeadStages.ARM_WALK, "both legs gone: on his hands");
		helper.succeed();
	}

	/** Shift+V: a tap toggles auto-regrowth; held, it opens the Regen Menu (and the release toggles nothing). */
	/** Auto-regrowth off: nothing grows on its own; the Regen Menu grows a part a stage at a time, or strips a whole one. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theRegenMenuGrowsAStageAtATime(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Undead.setRegenOff(p, true);
		Undead.breakPart(p, UndeadState.LEFT_ARM, UndeadState.KIND_BURST);
		int[] t = { 0 };
		helper.onEachTick(() -> {
			Undead.tick(p);
			t[0]++;
			UndeadState s = Undead.state(p);
			long now = helper.getLevel().getGameTime();
			switch (t[0]) {
				case 30 -> {
					helper.assertTrue(s.broken(UndeadState.LEFT_ARM) && s.regrowStart(UndeadState.LEFT_ARM) == 0L, "off: nothing grows on its own");
					com.projecthero.mod.undead.UndeadRegenMenu.press(p, UndeadState.LEFT_HAND);
					helper.assertTrue(Undead.state(p).regrowStart(UndeadState.LEFT_HAND) == 0L, "the hand can't grow before the arm");
					com.projecthero.mod.undead.UndeadRegenMenu.press(p, UndeadState.LEFT_ARM);
				}
				case 45 -> {
					helper.assertTrue(s.broken(UndeadState.LEFT_ARM) && Math.abs(s.frame(UndeadState.LEFT_ARM, now) - 5f) < 0.01f, "held at the end of the bone stage");
					helper.assertTrue(s.stage(UndeadState.LEFT_ARM, now) == UndeadState.STAGE_BONE, "bone");
					com.projecthero.mod.undead.UndeadRegenMenu.press(p, UndeadState.LEFT_ARM);
				}
				case 60 -> {
					helper.assertTrue(s.stage(UndeadState.LEFT_ARM, now) == UndeadState.STAGE_FLESH && Math.abs(s.frame(UndeadState.LEFT_ARM, now) - 11f) < 0.01f,
							"then held at the end of the flesh stage: " + s.frame(UndeadState.LEFT_ARM, now));
					com.projecthero.mod.undead.UndeadRegenMenu.press(p, UndeadState.LEFT_ARM);
				}
				case 80 -> {
					helper.assertTrue(!s.broken(UndeadState.LEFT_ARM), "then whole");
					helper.assertTrue(s.broken(UndeadState.LEFT_HAND), "the hand still waits for its own presses");
					com.projecthero.mod.undead.UndeadRegenMenu.press(p, UndeadState.LEFT_ARM); // a whole part: stripped
				}
				case 81 -> {
					helper.assertTrue(s.broken(UndeadState.LEFT_ARM) && s.broken(UndeadState.LEFT_HAND), "a press on a whole part strips it (and its hand)");
					com.projecthero.mod.undead.UndeadRegenMenu.whole(p);
				}
				default -> {
				}
			}
			if (t[0] > 81 && !s.anyBroken()) {
				helper.assertTrue(t[0] <= 81 + 18 + 2, "Regenerate Whole Body grows everything back, auto-regrowth off or not: " + t[0]);
				helper.succeed();
			}
		});
	}

	/** User: "if the players torso gets destroyed their armour falls off ... if leg gets destroyed remove leggings, if feet ... boots". */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void armourFallsOffTheLostParts(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Undead.setRegenOff(p, true);
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(net.minecraft.world.item.Items.IRON_LEGGINGS));
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(net.minecraft.world.item.Items.IRON_BOOTS));
		Undead.breakPart(p, UndeadState.RIGHT_FOOT, UndeadState.KIND_BURST);
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).isEmpty(), "a foot gone: the boots fall off");
		helper.assertTrue(!p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).isEmpty(), "the leggings stay");
		Undead.breakPart(p, UndeadState.LEFT_LEG, UndeadState.KIND_BURST);
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).isEmpty(), "a leg gone: the leggings fall off");
		Undead.breakPart(p, UndeadState.BODY, UndeadState.KIND_BURST);
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(), "the torso gone: the chestplate falls off");
		java.util.List<net.minecraft.world.entity.item.ItemEntity> drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				p.getBoundingBox().inflate(4));
		helper.assertTrue(drops.size() == 3, "all three dropped at his feet, nothing lost or doubled: " + drops.size());
		// put back on with the torso still gone: refused, back into the inventory
		p.getInventory().clearContent();
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
		com.projecthero.mod.undead.UndeadArmour.forTestsTick(p);
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()
				&& p.getInventory().countItem(net.minecraft.world.item.Items.IRON_CHESTPLATE) == 1, "no torso to wear it on: back in the inventory");
		drops.forEach(e -> e.discard());
		Undead.restore(p);
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
		com.projecthero.mod.undead.UndeadArmour.forTestsTick(p);
		helper.assertTrue(!p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(), "regrown: it stays on");
		helper.succeed();
	}

	/** User: "if an arm gets destroyed that shoulder pad doesnt grow back and players armour value is lowered slightly". */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void losingAnArmTearsOffItsShoulderPad(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		ItemStack chest = new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE);
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, chest);
		com.projecthero.mod.undead.UndeadArmour.forTestsTick(p);
		java.util.function.DoubleSupplier penalty = () -> p.getAttribute(Attributes.ARMOR).getModifiers().stream()
				.filter(m -> m.id().getPath().equals("undead_lost_pads")).mapToDouble(m -> m.amount()).sum();
		helper.assertTrue(penalty.getAsDouble() == 0.0, "no penalty with both pads");
		Undead.breakPart(p, UndeadState.RIGHT_ARM, UndeadState.KIND_BURST);
		ItemStack worn = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
		helper.assertTrue(!worn.isEmpty(), "an arm: the chestplate stays on");
		helper.assertTrue(com.projecthero.mod.undead.UndeadArmour.lostPads(worn) == com.projecthero.mod.undead.UndeadArmour.PAD_RIGHT, "but the right pad is torn off");
		helper.assertTrue(worn.getDamageValue() > 0, "and it took damage");
		double pad = com.projecthero.mod.undead.UndeadArmour.padArmour(worn);
		helper.assertTrue(pad == 1.0, "iron: a point a pad: " + pad);
		helper.assertTrue(Math.abs(penalty.getAsDouble() + pad) < 0.01, "his armour drops a little: " + penalty.getAsDouble());
		Undead.restore(p); // the arm is back
		com.projecthero.mod.undead.UndeadArmour.forTestsTick(p);
		helper.assertTrue(com.projecthero.mod.undead.UndeadArmour.lostPads(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)) != 0,
				"the pad does not grow back with the arm");
		p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).setDamageValue(0); // repaired (anvil / Mending / the suit)
		com.projecthero.mod.undead.UndeadArmour.forTestsTick(p);
		helper.assertTrue(com.projecthero.mod.undead.UndeadArmour.lostPads(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)) == 0, "repaired: the pad is back");
		helper.assertTrue(penalty.getAsDouble() == 0.0, "and the armour");
		helper.succeed();
	}

	/** Auto-regrowth off keeps a lost part gone until it is turned back on; the state (and old saves) load. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void regenOffKeepsPartsGoneAndIsSaved(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Undead.toggleRegen(p);
		helper.assertTrue(Undead.regenOff(p), "Shift+V: off");
		UndeadState s0 = Undead.state(p);
		net.minecraft.nbt.Tag tag = UndeadState.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, s0).getOrThrow();
		UndeadState back = UndeadState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertTrue(back.flag(UndeadState.FLAG_REGEN_OFF), "saved and loaded");
		net.minecraft.nbt.CompoundTag old = ((net.minecraft.nbt.CompoundTag) tag).copy();
		net.minecraft.nbt.ListTag six = new net.minecraft.nbt.ListTag();
		for (int i = 0; i < 18; i++) {
			six.add(net.minecraft.nbt.LongTag.valueOf(0L));
		}
		old.put("part_times", six); // a save from before the hands and feet were segments
		old.putInt("regen_style", 2);
		UndeadState fromOld = UndeadState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, old).getOrThrow();
		helper.assertTrue(fromOld.hasPower && fromOld.partTimes.size() == UndeadState.SEGS * 3, "an old save still loads, the new segments whole");
		Undead.breakPart(p, UndeadState.LEFT_ARM, UndeadState.KIND_BURST);
		int[] t = { 0 };
		helper.onEachTick(() -> {
			Undead.tick(p);
			t[0]++;
			if (t[0] == 60) {
				helper.assertTrue(Undead.missing(p, UndeadState.LEFT_ARM) && Undead.state(p).regrowStart(UndeadState.LEFT_ARM) == 0L, "off: still gone after 3 s");
				Undead.toggleRegen(p);
			}
			if (t[0] > 60 && !Undead.state(p).anyBroken()) {
				helper.assertTrue(t[0] <= 60 + 18 + 12 + 2, "on again: back in about a second: " + (t[0] - 60));
				Undead.clearTransient(p);
				helper.succeed();
			}
		});
	}

	/** One healing rate now (the old Fast): his health comes back on its own a short while after a hit, free. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void woundsHealOnTheirOwn(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.setHealth(6f);
		p.getFoodData().setFoodLevel(20);
		p.getFoodData().setSaturation(0f); // no vanilla saturation healing in the way
		edit(p, s -> s.lastHitAt = helper.getLevel().getGameTime());
		int[] t = { 0 };
		helper.onEachTick(() -> {
			t[0]++;
			Undead.tick(p);
			if (t[0] == 100) {
				helper.assertTrue(p.getHealth() >= 10f, "1.5 health a second after 1.5 s: " + p.getHealth());
				helper.assertTrue(p.getFoodData().getFoodLevel() == 20 && p.getFoodData().getExhaustionLevel() < 0.01f, "free");
				helper.succeed();
			}
		});
	}

	// ---------------------------------------------------------------- v0.17.3 tester fixes

	/** v0.17.3 (user): Undead's Suit mends itself while worn, one point every 2 s, never past whole, and not in the inventory. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void undeadsSuitMendsItselfWhileWorn(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		ItemStack worn = new ItemStack(UndeadItems.SUIT_LEGGINGS);
		worn.setDamageValue(10);
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, worn);
		ItemStack spare = new ItemStack(UndeadItems.SUIT_BOOTS);
		spare.setDamageValue(10);
		p.getInventory().setItem(5, spare);
		helper.assertTrue(UndeadItems.SUIT_CHESTPLATE.getDefense() == 6
				&& UndeadItems.SUIT_LEGGINGS.getDefense() == 5 && UndeadItems.SUIT_BOOTS.getDefense() == 2, "iron protection 6/5/2 (no helmet)");
		helper.assertTrue(worn.getMaxDamage() == net.minecraft.world.item.ArmorItem.Type.LEGGINGS.getDurability(15), "iron durability");
		helper.assertTrue(com.projecthero.mod.undead.item.UndeadSuitItem.REPAIR_EVERY == 40 && com.projecthero.mod.undead.item.UndeadSuitItem.REPAIR_AMOUNT == 1, "1 durability every 2 s");
		for (int i = 0; i < 20; i++) { // twenty 2 s steps
			com.projecthero.mod.undead.item.UndeadSuitItem.mend(worn, p);
			com.projecthero.mod.undead.item.UndeadSuitItem.mend(spare, p);
		}
		helper.assertTrue(worn.getDamageValue() == 0, "mended back to whole, never past: " + worn.getDamageValue());
		helper.assertTrue(spare.getDamageValue() == 10, "a spare piece in the inventory does not mend: " + spare.getDamageValue());
		helper.succeed();
	}

	/** v0.17.3 (user): the suit is not given with the power -- it is only crafted. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void undeadsSuitIsCraftedNotGiven(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		helper.assertTrue(p.getInventory().countItem(UndeadItems.SUIT_CHESTPLATE) == 0 && p.getInventory().countItem(UndeadItems.SUIT_LEGGINGS) == 0
				&& p.getInventory().countItem(UndeadItems.SUIT_BOOTS) == 0, "gaining the power gives no suit");
		var recipes = helper.getLevel().getRecipeManager();
		for (String id : new String[] { "undead_suit_chestplate", "undead_suit_leggings", "undead_suit_boots" }) {
			helper.assertTrue(recipes.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("projecthero", id)).isPresent(), "craftable: " + id);
		}
		helper.succeed();
	}

	/** Tester: "Alternation between left and right hands instead of just one when throwing arms or hands". */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shotsAlternateHands(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		long now = helper.getLevel().getGameTime();
		UndeadAbilities.partsBullet(p);
		UndeadState s = Undead.state(p);
		boolean firstLeft = s.eaten(UndeadState.LEFT_ARM, now) > 0f;
		helper.assertTrue(firstLeft != (s.eaten(UndeadState.RIGHT_ARM, now) > 0f), "one hand fired first");
		UndeadAbilities.partsBullet(p);
		s = Undead.state(p);
		helper.assertTrue(s.eaten(UndeadState.LEFT_ARM, now) > 0f && s.eaten(UndeadState.RIGHT_ARM, now) > 0f, "the second shot came from the other hand");
		UndeadAbilities.partsBullet(p);
		s = Undead.state(p);
		int arm = firstLeft ? UndeadState.LEFT_ARM : UndeadState.RIGHT_ARM;
		helper.assertTrue(Math.abs(s.eaten(arm, now) - 2f * UndeadConfig.PARTS_EAT) < 1.0e-3f, "and the third from the first again: " + s.eaten(arm, now));
		// with one arm gone, the other fires every time
		Undead.breakPart(p, UndeadState.LEFT_ARM, UndeadState.KIND_BURST);
		helper.assertTrue(Undead.shootingArm(p) == UndeadState.RIGHT_ARM, "one arm left: always that one");
		helper.succeed();
	}

	/** Tester: "Make deadline do damage less consistently" -- rolls vary and some miss, about 4 on average. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void deadLineRollsVary(GameTestHelper helper) {
		net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(42L);
		float sum = 0f, min = 99f, max = 0f;
		int misses = 0, n = 4000;
		for (int i = 0; i < n; i++) {
			float d = UndeadAbilities.deadLineRoll(r);
			sum += d;
			if (d <= 0f) {
				misses++;
			} else {
				min = Math.min(min, d);
				max = Math.max(max, d);
			}
		}
		float avg = sum / n;
		helper.assertTrue(avg > 3.6f && avg < 4.4f, "about 4 a hit on average: " + avg);
		helper.assertTrue(misses > n / 10 && misses < n / 5, "some miss: " + misses);
		helper.assertTrue(min < 3f && max > 6.5f, "hits range widely: " + min + ".." + max);
		helper.succeed();
	}

	/** Tester: "A way people can naturally pick up the head": right-click the thrown head and it goes back on. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theThrownHeadCanBePickedUp(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		UndeadAbilities.headBullet(p);
		helper.assertTrue(Undead.missing(p, UndeadState.HEAD), "the head is thrown");
		UndeadPartEntity head = helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, new AABB(p.blockPosition()).inflate(8),
				e -> e.ownerIs(p) && e.bodyPart() == UndeadState.HEAD).stream().findFirst().orElse(null);
		helper.assertTrue(head != null, "the head entity is out");
		helper.assertTrue(head.isPickable(), "and can be clicked");
		ServerPlayer friend = mock(helper);
		head.interact(friend, InteractionHand.MAIN_HAND);
		helper.assertTrue(!Undead.missing(p, UndeadState.HEAD), "a friend right-clicking it puts it straight back on");
		helper.assertTrue(head.isRemoved(), "and the thrown one is gone");
		helper.succeed();
	}

	/** Tester: "A way people can naturally pick up the head": an Undead who is only a head can be picked up and carried. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aHeadOnlyUndeadCanBeCarried(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		ServerPlayer friend = mock(helper);
		helper.assertTrue(com.projecthero.mod.undead.UndeadCarry.tryPickUp(friend, InteractionHand.MAIN_HAND, p) == net.minecraft.world.InteractionResult.PASS,
				"a whole Undead can not be picked up");
		for (int i = UndeadState.BODY; i < UndeadState.PARTS; i++) {
			Undead.breakPart(p, i, UndeadState.KIND_BURST);
		}
		com.projecthero.mod.undead.UndeadCarry.tryPickUp(friend, InteractionHand.MAIN_HAND, p);
		helper.assertTrue(p.getVehicle() == friend, "only a head: the friend picks him up");
		Vec3 seat = com.projecthero.mod.undead.UndeadCarry.seat(friend);
		helper.assertTrue(seat.y < 1.2 && seat.horizontalDistance() > 0.3, "carried in front of the chest, not on the head: " + seat);
		Undead.restore(p);
		Undead.tick(p);
		helper.assertTrue(p.getVehicle() == null, "with his body back he climbs down");
		helper.succeed();
	}

	/** Tester: the clones -- the bones self has its own skeleton texture (the blank white one is gone). */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBoneCloneHasASkeletonTexture(GameTestHelper helper) {
		helper.assertTrue(UndeadGameTests.class.getResource("/assets/projecthero/textures/entity/undead/bone_clone.png") != null, "bone_clone.png ships");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R: finger shots eat the arm, the stretch punch

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void fingerShotNumbers(GameTestHelper helper) {
		helper.assertTrue(UndeadConfig.PARTS_BULLET_COOLDOWN == 0, "Parts Bullet: no cooldown");
		helper.assertTrue(com.projecthero.mod.undead.UndeadAbilityManager.maxCooldown(UndeadAbilities.PARTS_BULLET) == 0, "and the HUD shows none");
		helper.assertTrue(UndeadConfig.CRIMSON_DAMAGE == 10f && UndeadConfig.CRIMSON_COOLDOWN == 15, "Crimson Bullet: 10 damage, 0.75 s");
		helper.assertTrue(UndeadConfig.PUNCH_DAMAGE == 14f && UndeadConfig.PUNCH_COOLDOWN == 15, "Parts Bullet: Arm: 14 damage (tester: 20 is a bit much), 0.75 s");
		helper.assertTrue(UndeadConfig.HEAD_BULLET_DAMAGE > UndeadConfig.PUNCH_DAMAGE, "the head hits harder than the arm");
		helper.assertTrue(Math.abs(UndeadConfig.CRIMSON_EAT - 4f * UndeadConfig.PARTS_EAT) < 1.0e-5f, "a Crimson Bullet takes four times the bite");
		helper.assertTrue(UndeadConfig.EATEN_REGROW_WAIT == 40, "regrowth waits 2 s without a shot");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void fingerShotsEatTheArmAndItWaitsToRegrow(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.setXRot(-85f);
		int arm = Undead.mainArm(p);
		UndeadAbilities.partsBullet(p);
		float one = Undead.state(p).eaten(arm, helper.getLevel().getGameTime());
		helper.assertTrue(Math.abs(one - UndeadConfig.PARTS_EAT) < 0.002f, "one shot takes a twelfth: " + one);
		edit(p, n -> { n.abilityReadyAt.clear(); n.setFlag(UndeadState.FLAG_NEXT_OFF, false); });
		UndeadAbilities.partsBullet(p);
		edit(p, n -> { n.abilityReadyAt.clear(); n.setFlag(UndeadState.FLAG_NEXT_OFF, false); });
		UndeadAbilities.crimsonBullet(p);
		float six = Undead.state(p).eaten(arm, helper.getLevel().getGameTime());
		helper.assertTrue(Math.abs(six - 6f / 12f) < 0.003f, "two Parts and a Crimson: half the arm: " + six);
		long shot = helper.getLevel().getGameTime();
		helper.onEachTick(() -> Undead.tick(p));
		helper.runAfterDelay(UndeadConfig.EATEN_REGROW_WAIT - 4, () -> helper.assertTrue(
				Math.abs(Undead.state(p).eaten(arm, helper.getLevel().getGameTime()) - six) < 0.001f, "nothing grows back before 2 s"));
		helper.runAfterDelay(UndeadConfig.EATEN_REGROW_WAIT + 2, () -> helper.assertTrue(Undead.state(p).eatenRegrow(arm, helper.getLevel().getGameTime()) > 0f,
				"it starts growing back after 2 s without a shot (" + (helper.getLevel().getGameTime() - shot) + " ticks)"));
		helper.runAfterDelay(UndeadConfig.EATEN_REGROW_WAIT + 4, () -> {
			// shooting again stops it and the bite carries on from what is still gone
			edit(p, n -> { n.abilityReadyAt.clear(); n.setFlag(UndeadState.FLAG_NEXT_OFF, false); });
			UndeadAbilities.partsBullet(p);
			UndeadState s = Undead.state(p);
			helper.assertTrue(s.eatenRegrow(arm, helper.getLevel().getGameTime()) == 0f, "a new shot restarts the wait");
			// eat the rest: the arm is used up and gone, and the other arm shoots
			for (int i = 0; i < 8 && !Undead.missing(p, arm); i++) {
				edit(p, n -> { n.abilityReadyAt.clear(); n.setFlag(UndeadState.FLAG_NEXT_OFF, false); });
				UndeadAbilities.crimsonBullet(p);
			}
			helper.assertTrue(Undead.missing(p, arm) && Undead.state(p).kind(arm) == UndeadState.KIND_SPENT, "a used-up arm is gone");
			helper.assertTrue(Undead.shootingArm(p) == Undead.otherArm(arm), "the other arm shoots now");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noArmsNoShot(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Undead.breakPart(p, UndeadState.RIGHT_ARM, UndeadState.KIND_BURST);
		Undead.breakPart(p, UndeadState.LEFT_ARM, UndeadState.KIND_BURST);
		UndeadAbilities.partsBullet(p);
		UndeadAbilities.crimsonBullet(p);
		AABB box = new AABB(p.blockPosition()).inflate(4);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, box, e -> true).isEmpty(), "nothing fires with no arms");
		helper.assertTrue(!UndeadAbilities.startPunch(p), "and no punch winds up");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void holdRThrowsTheArmAndUsesTheOtherWhenTheMainIsGone(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.setXRot(-85f);
		int main = Undead.mainArm(p);
		Undead.breakPart(p, main, UndeadState.KIND_BURST);
		com.projecthero.mod.hero.AbilitySlot r = com.projecthero.mod.hero.AbilitySlot.SLOT_1;
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, r, true);
		helper.onEachTick(() -> Undead.tick(p));
		helper.runAfterDelay(UndeadConfig.PUNCH_HOLD_TICKS + 3, () -> helper.assertTrue(Undead.state(p).flag(UndeadState.FLAG_PUNCH), "held: the arm draws back"));
		helper.runAfterDelay(UndeadConfig.PUNCH_MIN_HOLD + 4, () -> {
			com.projecthero.mod.undead.UndeadAbilityManager.handle(p, r, false);
			int other = Undead.otherArm(main);
			helper.assertTrue(Undead.missing(p, other) && Undead.state(p).kind(other) == UndeadState.KIND_LAUNCHED, "the other arm was thrown");
			AABB box = new AABB(p.blockPosition()).inflate(6);
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, box, e -> e.style() == UndeadPartEntity.STYLE_PUNCH && e.bodyPart() == other)
					.size() == 1, "it flies as the punch");
			helper.assertTrue(Undead.cooldownRemaining(p, UndeadAbilities.PUNCH) > 0, "1 s cooldown");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void aTapOfRIsStillAPartsBullet(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.setXRot(-85f);
		com.projecthero.mod.hero.AbilitySlot r = com.projecthero.mod.hero.AbilitySlot.SLOT_1;
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, r, true);
		helper.onEachTick(() -> Undead.tick(p));
		helper.runAfterDelay(2, () -> {
			com.projecthero.mod.undead.UndeadAbilityManager.handle(p, r, false);
			AABB box = new AABB(p.blockPosition()).inflate(6);
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, box, e -> e.style() == UndeadPartEntity.STYLE_FINGER).size() == 1,
					"a tap fires a finger");
			helper.assertTrue(!Undead.state(p).flag(UndeadState.FLAG_PUNCH), "and winds nothing up");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- flight, the boost, the head-only regrowth

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void flightSpeeds(GameTestHelper helper) {
		helper.assertTrue(Math.abs(UndeadConfig.PROPULSION_SPEED * 20 - 25) < 1.0e-6, "25 b/s");
		helper.assertTrue(Math.abs(UndeadConfig.BOOST_SPEED * 20 - 40) < 1.0e-6, "40 b/s boosting");
		helper.assertTrue(Math.abs(UndeadConfig.HEAD_FLIGHT_SPEED * 20 - 32) < 1.0e-6, "32 b/s as a head");
		ServerPlayer p = undead(helper);
		helper.assertTrue(com.projecthero.mod.undead.UndeadFlight.start(p) && com.projecthero.mod.undead.UndeadFlight.isFlying(p), "takes off");
		UndeadState s = Undead.state(p);
		long now = helper.getLevel().getGameTime();
		helper.assertTrue(com.projecthero.mod.undead.UndeadFlight.speed(s, now) == UndeadConfig.PROPULSION_SPEED, "flying at 25");
		com.projecthero.mod.undead.UndeadFlight.setBoost(p, true);
		helper.assertTrue(com.projecthero.mod.undead.UndeadFlight.speed(Undead.state(p), now) == UndeadConfig.BOOST_SPEED, "boosting at 40");
		for (int i = UndeadState.BODY; i < UndeadState.PARTS; i++) {
			int part = i;
			edit(p, n -> n.setTimes(part, now, 0L, 0L));
		}
		helper.assertTrue(com.projecthero.mod.undead.UndeadFlight.speed(Undead.state(p), now) == UndeadConfig.HEAD_FLIGHT_SPEED, "a head flies at 32, boost or not");
		helper.assertTrue(!Undead.state(p).headOnly(now) || Undead.stage(p) == com.projecthero.mod.undead.UndeadStages.ROLL, "and is a rolling head");
		com.projecthero.mod.undead.UndeadFlight.stop(p);
		helper.assertTrue(!com.projecthero.mod.undead.UndeadFlight.isFlying(p) && !Undead.state(p).flag(UndeadState.FLAG_BOOST), "lands");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
	public void theBoostBurnsLegsThenArmsThenBodyDownToTheHead(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.setXRot(-85f);
		float health = p.getHealth();
		com.projecthero.mod.undead.UndeadFlight.start(p);
		com.projecthero.mod.undead.UndeadFlight.setBoost(p, true);
		int legs = UndeadConfig.BURN_TICKS[0], arms = legs + UndeadConfig.BURN_TICKS[1], body = arms + UndeadConfig.BURN_TICKS[2];
		helper.assertTrue(body == 300, "15 s from whole to a head");
		int[] ticks = { 0 };
		helper.onEachTick(() -> {
			edit(p, n -> n.holdStart = helper.getLevel().getGameTime()); // no time limit in the test
			Undead.tick(p);
			ticks[0]++;
			UndeadState s = Undead.state(p);
			if (ticks[0] == legs - 2) {
				helper.assertTrue(!s.broken(UndeadState.RIGHT_LEG) && !s.broken(UndeadState.LEFT_LEG), "the legs are still burning");
			}
			if (ticks[0] == legs + 2) {
				helper.assertTrue(s.broken(UndeadState.RIGHT_LEG) && s.broken(UndeadState.LEFT_LEG) && s.kind(UndeadState.LEFT_LEG) == UndeadState.KIND_BURNT,
						"the legs went first");
				helper.assertTrue(!s.broken(UndeadState.RIGHT_ARM) && !s.broken(UndeadState.BODY), "nothing else yet");
			}
			if (ticks[0] == arms + 2) {
				helper.assertTrue(s.broken(UndeadState.RIGHT_ARM) && s.broken(UndeadState.LEFT_ARM) && !s.broken(UndeadState.BODY), "then the arms");
			}
			if (ticks[0] == body + 2) {
				helper.assertTrue(s.broken(UndeadState.BODY) && !s.broken(UndeadState.HEAD), "then the body: just the head");
				helper.assertTrue(s.regrowStart(UndeadState.BODY) == 0L, "nothing regrows mid-flight");
				helper.assertTrue(p.isAlive() && p.getHealth() >= health, "burning never hurts him");
				com.projecthero.mod.undead.UndeadFlight.stop(p);
			}
			if (ticks[0] == body + 4) {
				helper.assertTrue(Undead.state(p).regrowStart(UndeadState.BODY) > 0L, "landed: the head-only regrowth is planned");
				helper.succeed();
			}
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void victorIsDisabled(GameTestHelper helper) {
		// v0.17.3 (user: "remove the Victor ability for now"): no key pulls the card, the kit never swaps, a stale save clears
		ServerPlayer p = undead(helper);
		helper.assertTrue(!UndeadConfig.VICTOR_ENABLED, "Victor is off");
		double base = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		UndeadAbilities.pullCard(p);
		helper.assertTrue(!Undead.victor(p) && Undead.state(p).victorUntil == 0L, "pulling the card does nothing");
		com.projecthero.mod.hero.AbilitySlot v = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		helper.assertTrue(UndeadAbilities.REFORM.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, v)[0])
				&& com.projecthero.mod.undead.UndeadRibGuard.ID.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, v)[1]),
				"V is Reform, Shift+V the Rib Cage Guard");
		edit(p, n -> n.victorUntil = helper.getLevel().getGameTime() + 400);
		helper.assertTrue(!Undead.victor(p), "an old save with the card out is not Victor");
		helper.assertTrue(UndeadAbilities.PARTS_BULLET.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1)[0])
				&& UndeadAbilities.CRESCENT.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_2)[0])
				&& UndeadAbilities.DEAD_LINE.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_6)[0]),
				"the kit never swaps to Victor's");
		Undead.tick(p);
		helper.assertTrue(Undead.state(p).victorUntil == 0L, "and the tick clears it");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - base) < 0.01, "still Bloody Arts");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void slashesHitWhatIsInFront(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Zombie z = zombie(helper, 2, 4);
		UndeadAbilities.crescent(p);
		float after = z.getHealth();
		helper.assertTrue(after < 200f, "Crimson Crescent Moon hits");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R aim, Shift+V: the Bone Sword and its kit (v0.17.3)

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void thePartsBulletArmFliesAtTheCrosshair(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.setXRot(0f);
		Zombie z = zombie(helper, 2, 9); // straight ahead, the eye line through its head
		UndeadAbilities.startPunch(p);
		edit(p, n -> n.setEx(UndeadState.X_PUNCH_START, helper.getLevel().getGameTime() - 20));
		UndeadAbilities.throwPunch(p);
		AABB box = new AABB(p.blockPosition()).inflate(6);
		java.util.List<UndeadPartEntity> arms = helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, box, e -> e.style() == UndeadPartEntity.STYLE_PUNCH);
		helper.assertTrue(arms.size() == 1, "the arm flies");
		UndeadPartEntity a = arms.get(0);
		Vec3 centre = a.position().add(0, a.getBbHeight() * 0.5, 0);
		Vec3 aimAt = new Vec3(z.getX(), p.getEyeY(), z.getBoundingBox().minZ);
		double cos = a.getDeltaMovement().normalize().dot(aimAt.subtract(centre).normalize());
		helper.assertTrue(cos > Math.cos(Math.toRadians(2.5)), "it flies at the crosshair: " + Math.toDegrees(Math.acos(Math.min(1, cos))) + " degrees off");
		helper.assertTrue(!a.isLeftBehind(), "and is not drawn as a part left standing");
		// the finger shots aim the same way
		edit(p, n -> n.abilityReadyAt.clear());
		Undead.restore(p);
		UndeadAbilities.partsBullet(p);
		UndeadPartEntity f = helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, box, e -> e.style() == UndeadPartEntity.STYLE_FINGER).get(0);
		Vec3 fc = f.position().add(0, f.getBbHeight() * 0.5, 0);
		double fcos = f.getDeltaMovement().normalize().dot(aimAt.subtract(fc).normalize());
		helper.assertTrue(fcos > Math.cos(Math.toRadians(2.5)), "a Parts Bullet too: " + Math.toDegrees(Math.acos(Math.min(1, fcos))));
		UndeadAbilities.partsBullet(p);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, box, e -> e.style() == UndeadPartEntity.STYLE_FINGER).size() == 2,
				"and fires again at once (no cooldown)");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void theThrownArmSurvivesItsRegrowthStarting(GameTestHelper helper) {
		// v0.17.3 bug (user: "the part bullet arm ability doesnt seem to shoot properly anymore"): since the NBC regrowth a
		// launched arm starts growing back the very next tick, and the start of the regrowth burst the thrown arm -- it was gone
		// a tick after leaving the shoulder, nothing flew and nothing was hit. Held R through the key flow, his tick running,
		// thrown straight up (open sky): it must keep flying while the arm regrows, and burst once the arm is whole.
		ServerPlayer p = undead(helper);
		p.setXRot(-85f);
		com.projecthero.mod.hero.AbilitySlot r = com.projecthero.mod.hero.AbilitySlot.SLOT_1;
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, r, true);
		helper.onEachTick(() -> Undead.tick(p));
		int[] arm = { -1 };
		java.util.function.Supplier<java.util.List<UndeadPartEntity>> thrown = () -> helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class,
				new AABB(p.blockPosition()).inflate(64), e -> e.style() == UndeadPartEntity.STYLE_PUNCH && e.bodyPart() == arm[0]);
		int release = UndeadConfig.PUNCH_MIN_HOLD + 2;
		helper.runAfterDelay(release, () -> {
			com.projecthero.mod.undead.UndeadAbilityManager.handle(p, r, false);
			arm[0] = Undead.mainArm(p);
			helper.assertTrue(Undead.missing(p, arm[0]) && thrown.get().size() == 1, "the arm was thrown");
		});
		helper.runAfterDelay(release + 5, () -> {
			helper.assertTrue(Undead.state(p).regrowStart(arm[0]) > 0L, "it is already growing back (NBC: at once)");
			java.util.List<UndeadPartEntity> a = thrown.get();
			helper.assertTrue(a.size() == 1, "and the thrown arm is still flying");
			helper.assertTrue(a.get(0).position().distanceTo(p.getEyePosition()) > 2.5, "away from the shoulder: " + a.get(0).position().distanceTo(p.getEyePosition()));
		});
		helper.runAfterDelay(release + UndeadConfig.FRAMES[UndeadState.RIGHT_ARM] + 4, () -> {
			helper.assertTrue(!Undead.missing(p, arm[0]), "the arm has grown back");
			helper.assertTrue(thrown.get().isEmpty(), "and the thrown one burst once it was whole");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 300)
	public void holdVRipsAnArmIntoTheBoneSword(GameTestHelper helper) {
		// v0.17.3 (user: "make the bone sword hold V for 2 seconds"): the hold is the struggle; at its end the arm tears off
		ServerPlayer p = undead(helper);
		int main = Undead.mainArm(p), off = Undead.otherArm(main);
		double baseReach = p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		com.projecthero.mod.hero.AbilitySlot v = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, v, true);
		holdV(helper, p, UndeadConfig.BONE_SWORD_TAP + 3);
		helper.assertTrue(Undead.state(p).animId == UndeadState.ANIM_SWORD_STRUGGLE && !UndeadBoneSword.held(p) && !Undead.missing(p, off),
				"past a tap he struggles with the arm; it is still on");
		holdV(helper, p, UndeadConfig.BONE_SWORD_HOLD + 1);
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, v, false);
		helper.assertTrue(UndeadBoneSword.held(p), "held 2 s, V draws the sword");
		helper.assertTrue(Undead.missing(p, off) && !Undead.missing(p, main) && Undead.state(p).boneSwordArm() == off, "the off arm was torn off");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - UndeadConfig.BONE_SWORD_DAMAGE) < 0.01,
				"a sword's damage: " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - baseReach - UndeadConfig.BONE_SWORD_REACH_BONUS) < 0.01, "longer reach");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_SPEED) - UndeadConfig.BONE_SWORD_ATTACK_SPEED) < 0.01, "a sword's speed");
		helper.assertTrue(UndeadBoneSword.K_CRESCENT.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1)[0])
				&& UndeadBoneSword.K_WHIRL.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_6)[0])
				&& UndeadBoneSword.K_FLASH.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_4)[0]),
				"the keys are the katana's skills");
		helper.onEachTick(() -> Undead.tick(p));
		helper.runAfterDelay(30, () -> {
			UndeadState s = Undead.state(p);
			helper.assertTrue(s.broken(off) && s.regrowStart(off) == 0L, "the torn-off arm does not grow back while the sword is out");
			com.projecthero.mod.undead.UndeadAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_5, true);
			com.projecthero.mod.undead.UndeadAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_5, false);
			helper.assertTrue(!UndeadBoneSword.held(p), "V again puts it away");
			helper.assertTrue(Undead.cooldownRemaining(p, UndeadBoneSword.BONE_SWORD) > 0, "a short cooldown");
			helper.assertTrue(UndeadAbilities.PARTS_BULLET.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1)[0]),
					"the keys are his own again");
			helper.assertTrue(p.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).getModifiers().stream().noneMatch(m -> m.id().getPath().contains("bone_sword")),
					"the reach goes back");
		});
		helper.runAfterDelay(35, () -> {
			helper.assertTrue(Undead.state(p).regrowStart(off) > 0L, "then the arm regrows");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noSpareArmNoBoneSword(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Undead.breakPart(p, Undead.otherArm(Undead.mainArm(p)), UndeadState.KIND_BURST);
		helper.assertTrue(!UndeadBoneSword.draw(p) && !UndeadBoneSword.held(p), "one arm: nothing to tear off and hold");
		Undead.restore(p);
		Undead.breakPart(p, Undead.mainArm(p), UndeadState.KIND_BURST);
		helper.assertTrue(!UndeadBoneSword.draw(p), "no hand to hold it with");
		helper.succeed();
	}

	/** V held down for {@code ticks} game ticks' worth of the hold (the hold is timed off the game time: it is wound back). */
	private static void holdV(GameTestHelper helper, ServerPlayer p, int ticks) {
		com.projecthero.mod.undead.UndeadAbilityManager.forTestsHoldV(p, ticks);
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void lettingGoOfVEarlyLetsGoOfTheArm(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		int off = Undead.otherArm(Undead.mainArm(p));
		com.projecthero.mod.hero.AbilitySlot v = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, v, true);
		holdV(helper, p, UndeadConfig.BONE_SWORD_TAP + 10);
		helper.assertTrue(Undead.state(p).animId == UndeadState.ANIM_SWORD_STRUGGLE, "struggling");
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, v, false);
		helper.assertTrue(Undead.state(p).animId != UndeadState.ANIM_SWORD_STRUGGLE && !UndeadBoneSword.held(p) && !Undead.missing(p, off),
				"let go early: no sword, the arm stays on");
		helper.assertTrue(Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == 0, "and it was no tap: no Reform");
		helper.succeed();
	}

	// ---------------------------------------------------------------- tap V: Reform (replaced Tear Off)

	private static void feed(ServerPlayer p, int food, float saturation) {
		p.getFoodData().setFoodLevel(food);
		p.getFoodData().setSaturation(saturation);
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void tapVReformFinishesEveryPartAndCleanses(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		feed(p, 20, 5f);
		long now = helper.getLevel().getGameTime();
		int off = Undead.otherArm(Undead.mainArm(p));
		Undead.breakPart(p, UndeadState.LEFT_LEG, UndeadState.KIND_BURST); // gone, not started
		Undead.breakPart(p, off, UndeadState.KIND_BURST);
		edit(p, n -> n.setTimes(off, Math.max(1L, now - 30), now - 5, now + 100)); // regrowing
		p.setHealth(10f);
		p.igniteForSeconds(8f);
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.POISON, 200, 1));
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.WITHER, 200, 1));
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
		com.projecthero.mod.hero.AbilitySlot v = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, v, true);
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, v, false);
		UndeadState s = Undead.state(p);
		for (int i = 0; i < UndeadState.PARTS; i++) {
			helper.assertTrue(!s.broken(i) && Math.abs(s.hp(i) - UndeadConfig.PART_HP[i]) < 0.01f, "part " + i + " is whole");
		}
		helper.assertTrue(s.animId == UndeadState.ANIM_REFORM, "the snap-together animation");
		helper.assertTrue(s.ex(UndeadState.X_REFORMED) == ((1L << UndeadState.LEFT_LEG) | (1L << off)), "the flash on the two parts that closed");
		helper.assertTrue(Math.abs(p.getHealth() - (10f + UndeadConfig.REFORM_HEAL)) < 0.01f, "healed 6: " + p.getHealth());
		helper.assertTrue(!p.isOnFire() && !p.hasEffect(MobEffects.POISON) && !p.hasEffect(MobEffects.WITHER), "fire, poison and wither gone");
		helper.assertTrue(p.hasEffect(MobEffects.DAMAGE_RESISTANCE), "a good effect stays");
		helper.assertTrue(Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == UndeadConfig.REFORM_COOLDOWN, "20s cooldown");
		helper.assertTrue(com.projecthero.mod.undead.UndeadAbilityManager.maxCooldown(UndeadAbilities.REFORM) == UndeadConfig.REFORM_COOLDOWN, "the HUD's cooldown");
		Undead.tick(p);
		helper.assertTrue(!Undead.state(p).broken(off) && !Undead.state(p).broken(UndeadState.LEFT_LEG), "and the tick leaves it whole");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void reformHasACooldownWorksWithRegenOffAndNotWithTheSword(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		feed(p, 20, 20f);
		Undead.setRegenOff(p, true);
		Undead.breakPart(p, UndeadState.LEFT_LEG, UndeadState.KIND_BURST);
		helper.assertTrue(Undead.reform(p) && !Undead.missing(p, UndeadState.LEFT_LEG), "regeneration Off: Reform still works");
		Undead.breakPart(p, UndeadState.LEFT_LEG, UndeadState.KIND_BURST);
		helper.assertTrue(!Undead.reform(p) && Undead.missing(p, UndeadState.LEFT_LEG), "cooling down");
		edit(p, n -> n.abilityReadyAt.put(UndeadAbilities.REFORM, helper.getLevel().getGameTime()));
		helper.assertTrue(Undead.reform(p) && !Undead.missing(p, UndeadState.LEFT_LEG), "ready again");
		// the sword out: V puts it away and Reform does nothing
		Undead.setRegenOff(p, false);
		edit(p, n -> n.abilityReadyAt.clear());
		helper.assertTrue(UndeadBoneSword.draw(p), "the sword is drawn");
		int off = Undead.otherArm(Undead.mainArm(p));
		helper.assertTrue(!Undead.reform(p) && Undead.missing(p, off), "Reform does not grow the sword arm back");
		com.projecthero.mod.hero.AbilitySlot v = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, v, true);
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, v, false);
		helper.assertTrue(!UndeadBoneSword.held(p) && Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == 0, "V puts the sword away, no Reform with it");
		helper.succeed();
	}

	private static ServerPlayer swordsman(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		helper.assertTrue(UndeadBoneSword.draw(p), "the sword is drawn");
		return p;
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBoneSwordHitsLikeASwordAndSweeps(GameTestHelper helper) {
		ServerPlayer p = swordsman(helper);
		Zombie z = zombie(helper, 2, 4);
		Zombie side = zombie(helper, 3, 4);
		z.getAttribute(Attributes.ARMOR).setBaseValue(0);
		try {
			java.lang.reflect.Field f = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker");
			f.setAccessible(true);
			f.setInt(p, 100);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		helper.assertTrue(p.getAttackStrengthScale(0.5f) > 0.99f, "a full swing");
		UndeadBoneSword.melee(p, z, p.getAttackStrengthScale(0.5f)); // what the attack hook does before the hit
		p.attack(z);
		float dealt = 200f - z.getHealth();
		helper.assertTrue(dealt >= UndeadConfig.BONE_SWORD_DAMAGE - 0.5f, "a sword hit: " + dealt);
		helper.assertTrue(side.getHealth() < 200f, "the sweep catches the one beside it");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theThrownSwordPins(GameTestHelper helper) {
		ServerPlayer p = swordsman(helper);
		Zombie z = zombie(helper, 2, 4);
		UndeadBoneSword.pin(p, z);
		helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "pinned");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void fingerAndCrimsonBulletsFly(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		UndeadAbilities.partsBullet(p);
		AABB box = new AABB(p.blockPosition()).inflate(4);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, box, e -> e.style() == UndeadPartEntity.STYLE_FINGER).size() == 1,
				"a finger flies");
		UndeadAbilities.crimsonBullet(p);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, box, e -> e.style() == UndeadPartEntity.STYLE_CRIMSON).size() == 1,
				"a crimson bullet flies");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aOneArmMoveUsesTheOtherArmAndHeadBulletTheHead(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		int arm = Undead.mainArm(p);
		Undead.breakPart(p, arm, UndeadState.KIND_LAUNCHED);
		helper.assertTrue(Undead.missing(p, arm) && Undead.state(p).kind(arm) == UndeadState.KIND_LAUNCHED, "the arm is gone, thrown off");
		AABB box = new AABB(p.blockPosition()).inflate(4);
		UndeadAbilities.partsBullet(p);
		// v0.17.3: a one-arm move falls back to the other arm -- the finger comes from the hand he still has
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, box, e -> e.isFinger()).size() == 1, "the other hand shoots instead");
		helper.assertTrue(Undead.shootingArm(p) == (arm == UndeadState.RIGHT_ARM ? UndeadState.LEFT_ARM : UndeadState.RIGHT_ARM), "from the other arm");
		UndeadAbilities.headBullet(p);
		helper.assertTrue(Undead.missing(p, UndeadState.HEAD), "the head is gone");
		Undead.reconcile(p);
		helper.assertTrue(p.hasEffect(MobEffects.BLINDNESS), "and he is blind");
		helper.assertTrue(com.projecthero.mod.undead.UndeadMod.handGone(p, InteractionHand.MAIN_HAND), "the missing hand can't be used");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void quardeadSplitsIntoThreeClones(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		UndeadAbilities.quardead(p);
		AABB box = new AABB(p.blockPosition()).inflate(4);
		var clones = helper.getLevel().getEntitiesOfClass(UndeadCloneEntity.class, box, e -> true);
		helper.assertTrue(clones.size() == 3, "three copies: " + clones.size());
		helper.assertTrue(clones.stream().map(UndeadCloneEntity::tint).distinct().count() == 3, "skin, blood and bones");
		helper.assertTrue(!com.projecthero.mod.combat.HeroTargets.canHarm(p, clones.get(0)), "his own moves pass through them");
		clones.forEach(e -> e.discard());
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void deadLineIsABeamThatHurts(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Zombie z = zombie(helper, 2, 5);
		p.setXRot(8f);
		UndeadAbilities.startDeadLine(p);
		helper.assertTrue(Undead.state(p).flag(UndeadState.FLAG_DEAD_LINE), "the jet is on");
		helper.onEachTick(() -> Undead.tick(p));
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(z.getHealth() < 200f, "Dead Line hurts what it touches: " + z.getHealth());
			UndeadAbilities.stopDeadLine(p);
			helper.assertTrue(!Undead.state(p).flag(UndeadState.FLAG_DEAD_LINE), "and stops on release");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void propulsionAndWavesRun(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Zombie z = zombie(helper, 2, 5);
		UndeadAbilities.startPropulsion(p);
		helper.assertTrue(Undead.state(p).flag(UndeadState.FLAG_PROPELLING), "flying");
		Undead.tick(p);
		helper.assertTrue(Math.abs(p.getDeltaMovement().length() - UndeadConfig.PROPULSION_SPEED) < 1.0e-6, "along the look at 25 b/s");
		UndeadAbilities.stopPropulsion(p);
		helper.assertTrue(!Undead.state(p).flag(UndeadState.FLAG_PROPELLING), "stops on release");
		p.setDeltaMovement(Vec3.ZERO);
		p.moveTo(helper.absoluteVec(new Vec3(2.5, 2.0, 1.5)));
		UndeadAbilities.greatCrescent(p);
		helper.onEachTick(() -> Undead.tick(p));
		helper.runAfterDelay(14, () -> {
			helper.assertTrue(z.getHealth() < 200f, "the blood waves reach the zombie");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void victorVulcanAndDivisionFire(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.setXRot(-85f); // up, out of the walled test area
		UndeadAbilities.pullCard(p);
		UndeadAbilities.vulcan(p);
		UndeadAbilities.division(p);
		AABB box = new AABB(p.blockPosition()).inflate(6);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, box, e -> e.style() == UndeadPartEntity.STYLE_DIVISION).size() == 3,
				"three Division fingers");
		UndeadAbilities.divisionClone(p, p.position());
		helper.assertTrue(!helper.getLevel().getEntitiesOfClass(UndeadCloneEntity.class, box, e -> true).isEmpty(), "a finger grows into a copy");
		helper.onEachTick(() -> Undead.tick(p));
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(UndeadAbilities.vulcanLeft(p) > 0 && UndeadAbilities.vulcanLeft(p) <= UndeadConfig.VULCAN_SHOTS - 2, "Dead Vulcan keeps firing: " + UndeadAbilities.vulcanLeft(p));
			UndeadAbilities.deadRoad(p);
			helper.assertTrue(Undead.state(p).flag(UndeadState.FLAG_DEAD_ROAD), "Dead Road fires");
			helper.getLevel().getEntitiesOfClass(UndeadCloneEntity.class, box, e -> true).forEach(e -> e.discard());
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- X: Leg Launch, Head Launch, Shift + hold flight (v0.17.3)

	private static final com.projecthero.mod.hero.AbilitySlot X = com.projecthero.mod.hero.AbilitySlot.SLOT_3;

	private static java.util.List<UndeadPartEntity> leftBehind(GameTestHelper helper, ServerPlayer p) {
		return helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, new AABB(p.blockPosition()).inflate(8), e -> e.isLeftBehind() && e.ownerIs(p));
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void tapXLaunchesTheBodyOffTheLegs(GameTestHelper helper) {
		ServerPlayer p = undead(helper); // facing +z
		helper.onEachTick(() -> Undead.tick(p));
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, X, true);
		helper.runAfterDelay(4, () -> {
			helper.assertTrue(!Undead.missing(p, UndeadState.RIGHT_LEG), "nothing yet while X is still down");
			com.projecthero.mod.undead.UndeadAbilityManager.handle(p, X, false);
			UndeadState s = Undead.state(p);
			helper.assertTrue(s.broken(UndeadState.RIGHT_LEG) && s.broken(UndeadState.LEFT_LEG), "a tap breaks both legs");
			helper.assertTrue(s.kind(UndeadState.RIGHT_LEG) == UndeadState.KIND_LAUNCHED, "left behind, not burst");
			helper.assertTrue(!s.broken(UndeadState.HEAD) && !s.broken(UndeadState.BODY), "the rest of him goes");
			Vec3 v = p.getDeltaMovement();
			helper.assertTrue(v.z > 1.5 && Math.abs(v.y) < 0.05, "dashed straight along the aim (level look: no arc): " + v);
			helper.assertTrue(com.projecthero.mod.undead.UndeadLaunch.flying(p), "the launch runs");
			helper.assertTrue(com.projecthero.mod.undead.UndeadLaunch.fallImmune(p), "no fall damage from it");
			helper.assertTrue(leftBehind(helper, p).size() == 2, "the two legs stay where he stood: " + leftBehind(helper, p).size());
			helper.assertTrue(Undead.cooldownRemaining(p, com.projecthero.mod.undead.UndeadLaunch.LEG_LAUNCH) > 0, "on cooldown");
			leftBehind(helper, p).forEach(e -> e.discard());
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void holdXLaunchesTheHeadAndLeavesTheBody(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		helper.onEachTick(() -> Undead.tick(p));
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, X, true);
		helper.runAfterDelay(UndeadConfig.LAUNCH_CHARGE_SHOW + 3, () -> {
			helper.assertTrue(Undead.state(p).animId == UndeadState.ANIM_HEAD_CHARGE, "the wind-up shows while X is held");
			helper.assertTrue(!Undead.state(p).anyBroken(), "nothing breaks during the wind-up");
		});
		helper.runAfterDelay(UndeadConfig.HEAD_LAUNCH_HOLD + 3, () -> {
			// still held: it fired on its own at the full hold
			UndeadState s = Undead.state(p);
			helper.assertTrue(!s.broken(UndeadState.HEAD), "the player is the head");
			for (int i = UndeadState.BODY; i < UndeadState.PARTS; i++) {
				helper.assertTrue(s.broken(i), "part " + i + " is left behind");
			}
			helper.assertTrue(leftBehind(helper, p).size() == 5, "the body, arms and legs stay where he stood: " + leftBehind(helper, p).size());
			helper.assertTrue(Undead.cooldownRemaining(p, com.projecthero.mod.undead.UndeadLaunch.HEAD_LAUNCH) > 0, "on cooldown");
			com.projecthero.mod.undead.UndeadAbilityManager.handle(p, X, false);
			helper.assertTrue(Undead.cooldownRemaining(p, com.projecthero.mod.undead.UndeadLaunch.LEG_LAUNCH) == 0, "letting go after it does not also Leg Launch");
			leftBehind(helper, p).forEach(e -> e.discard());
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void headLaunchGivesTheHeadFlight(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		com.projecthero.mod.undead.UndeadLaunch.headLaunch(p);
		Vec3 v = p.getDeltaMovement();
		helper.assertTrue(v.z > 2.0 && Math.abs(v.y) < 0.05, "the head dashes straight along the aim (level look: no arc): " + v);
		helper.assertTrue(com.projecthero.mod.undead.UndeadLaunch.flying(p), "the launch runs");
		leftBehind(helper, p).forEach(e -> e.discard());
		helper.succeed();
	}

	/** v0.17.3 (user: "make the player keep the momentum"): at the end of its range the dash lets go at full speed, still fall-safe. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aLaunchKeepsItsMomentumPastItsRange(GameTestHelper helper) {
		ServerPlayer p = undead(helper); // facing +z
		com.projecthero.mod.undead.UndeadLaunch.legLaunch(p);
		helper.assertTrue(com.projecthero.mod.undead.UndeadLaunch.flying(p), "the launch runs");
		// past its range, up in the open air
		p.teleportTo(p.getX(), p.getY() + 6, p.getZ() + UndeadConfig.LEG_LAUNCH_RANGE + 2);
		p.setOnGround(false);
		com.projecthero.mod.undead.UndeadLaunch.tick(p);
		Vec3 v = p.getDeltaMovement();
		helper.assertTrue(!com.projecthero.mod.undead.UndeadLaunch.flying(p), "the scripted dash has ended");
		helper.assertTrue(v.z > 1.5, "but he flies on at full speed, not stopped in mid-air: " + v);
		helper.assertTrue(com.projecthero.mod.undead.UndeadLaunch.fallImmune(p), "and no fall damage while momentum carries him");
		leftBehind(helper, p).forEach(e -> e.discard());
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shiftHoldXFliesAndGlideIsGone(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		helper.assertTrue(java.util.Arrays.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, X),
				new String[] { com.projecthero.mod.undead.UndeadLaunch.LEG_LAUNCH, UndeadAbilities.PROPULSION }), "X: Leg Launch, Shift: the flight");
		boolean glide = true;
		try {
			UndeadAbilities.class.getField("GLIDE");
		} catch (NoSuchFieldException e) {
			glide = false;
		}
		helper.assertTrue(!glide, "Repair Glide is gone");
		helper.assertTrue(com.projecthero.mod.undead.UndeadAbilityManager.maxCooldown(com.projecthero.mod.undead.UndeadLaunch.LEG_LAUNCH) == UndeadConfig.LEG_LAUNCH_COOLDOWN
				&& UndeadConfig.LEG_LAUNCH_COOLDOWN == 40, "Leg Launch 2s cooldown");
		helper.assertTrue(com.projecthero.mod.undead.UndeadAbilityManager.maxCooldown(com.projecthero.mod.undead.UndeadLaunch.HEAD_LAUNCH) == UndeadConfig.HEAD_LAUNCH_COOLDOWN
				&& UndeadConfig.HEAD_LAUNCH_COOLDOWN == 160, "Head Launch 8s cooldown");
		helper.assertTrue(UndeadConfig.HEAD_LAUNCH_HOLD == 20, "the hold is a second");
		p.setShiftKeyDown(true);
		helper.onEachTick(() -> Undead.tick(p));
		com.projecthero.mod.undead.UndeadAbilityManager.handle(p, X, true);
		helper.runAfterDelay(1, () -> helper.assertTrue(!Undead.state(p).flag(UndeadState.FLAG_PROPELLING), "a Shift tap does not fly yet"));
		helper.runAfterDelay(UndeadConfig.FLIGHT_HOLD_TICKS + 3, () -> {
			helper.assertTrue(Undead.state(p).flag(UndeadState.FLAG_PROPELLING), "Shift + hold X flies");
			for (int i = 0; i < UndeadState.PARTS; i++) {
				helper.assertTrue(!Undead.state(p).broken(i), "and launches nothing");
			}
			helper.assertTrue(Undead.state(p).broken(UndeadState.RIGHT_HAND) && Undead.state(p).broken(UndeadState.LEFT_FOOT), "the hands and feet burst off");
			com.projecthero.mod.undead.UndeadAbilityManager.handle(p, X, false);
			helper.assertTrue(!Undead.state(p).flag(UndeadState.FLAG_PROPELLING), "letting go stops it");
			helper.assertTrue(Undead.cooldownRemaining(p, com.projecthero.mod.undead.UndeadLaunch.LEG_LAUNCH) == 0, "no Leg Launch on release");
			helper.succeed();
		});
	}
}
