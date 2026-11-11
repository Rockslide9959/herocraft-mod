package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadAbilities;
import com.projecthero.mod.undead.UndeadAbilityManager;
import com.projecthero.mod.undead.UndeadBoneSword;
import com.projecthero.mod.undead.UndeadConfig;
import com.projecthero.mod.undead.UndeadKitConfig;
import com.projecthero.mod.undead.UndeadRibShot;
import com.projecthero.mod.undead.UndeadSpineWhip;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadPartEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (user: "a new combat V move for the undead ability"): tap V is the Rib Shotgun ({@link UndeadRibShot}), Shift+V tapped
 * the Spine Whip ({@link UndeadSpineWhip}, user: "give shift+V another combat ability that isnt the rib cage shield"), hold V the
 * katana, Shift + hold V Reform. The move's numbers (spread, damage, range, embedding, cooldown,
 * cancelling) and -- the point of most of these -- that the four V inputs never set each other off. Mock players are not ticked
 * by the server, so tests over time drive {@link Undead#tick}.
 */
public class UndeadVMoveGameTests implements FabricGameTest {
	private static ServerPlayer undead(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 0.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		p.setYRot(0f);
		p.setXRot(0f);
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
		Undead.grant(p);
		return p;
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<UndeadState> fn) {
		UndeadState n = Undead.state(p).copy();
		fn.accept(n);
		p.setAttached(ModAttachments.UNDEAD_STATE, n);
	}

	/** A still, floating zombie on 200 health at {@code rel} (relative to the test), feet level with his. */
	private static Zombie zombie(GameTestHelper helper, Vec3 rel) {
		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(rel);
		z.moveTo(at.x, at.y, at.z, 180f, 0f);
		z.setNoAi(true);
		z.setNoGravity(true);
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		z.setHealth(200);
		z.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0); // stays where it was put (the spikes' shove is tested apart)
		z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning in the sun
		helper.getLevel().addFreshEntity(z);
		return z;
	}

	private static float dealt(Zombie z) {
		return 200f - z.getHealth();
	}

	private static void press(ServerPlayer p, boolean shift, boolean down) {
		p.setShiftKeyDown(shift);
		UndeadAbilityManager.handle(p, AbilitySlot.SLOT_5, down);
	}

	private static List<UndeadPartEntity> ribs(GameTestHelper helper, ServerPlayer p) {
		return helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, new AABB(p.blockPosition()).inflate(64),
				e -> e.isRib() && e.ownerIs(p) && e.isAlive());
	}

	private static void clean(GameTestHelper helper, ServerPlayer p) {
		ribs(helper, p).forEach(e -> e.discard());
		p.setShiftKeyDown(false);
	}

	/** Nothing from V happened: no shot, no whip, no Reform, no katana. */
	private static void assertNothing(GameTestHelper helper, ServerPlayer p, String why) {
		helper.assertTrue(!UndeadRibShot.windingUp(p) && Undead.cooldownRemaining(p, UndeadRibShot.ID) == 0, why + ": no Rib Shotgun");
		helper.assertTrue(!UndeadSpineWhip.active(p) && Undead.cooldownRemaining(p, UndeadSpineWhip.ID) == 0, why + ": no Spine Whip");
		helper.assertTrue(Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == 0, why + ": no Reform");
		helper.assertTrue(!UndeadBoneSword.held(p), why + ": no katana");
	}

	// ---------------------------------------------------------------- the HUD

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theVSlotShowsTheRibShotgunAndTheSpineWhip(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		String[] ids = UndeadAbilityManager.idsOf(p, AbilitySlot.SLOT_5);
		helper.assertTrue(ids.length == 2 && UndeadRibShot.ID.equals(ids[0]) && UndeadSpineWhip.ID.equals(ids[1]), "V: Rib Shotgun, Shift+V: Spine Whip");
		helper.assertTrue(UndeadAbilityManager.maxCooldown(UndeadSpineWhip.ID) == UndeadKitConfig.SPINE_COOLDOWN, "the HUD knows the whip's 7 s cooldown");
		helper.assertTrue(UndeadKitConfig.SPINE_COOLDOWN == 140 && UndeadKitConfig.SPINE_DAMAGE == 14f && UndeadKitConfig.SPINE_RANGE == 7.0,
				"the whip's numbers: 7 s, 14 damage, 7 blocks");
		helper.assertTrue(UndeadKitConfig.REFORM_TAP <= 6, "the whip fires on a quick tap only (well before Reform's hold)");
		helper.assertTrue(UndeadAbilityManager.maxCooldown(UndeadRibShot.ID) == UndeadKitConfig.RIB_SHOT_COOLDOWN, "the HUD knows its 6 s cooldown");
		helper.assertTrue(UndeadAbilityManager.maxCooldown(UndeadAbilities.REFORM) == UndeadConfig.REFORM_COOLDOWN, "and Reform's");
		helper.assertTrue(UndeadKitConfig.RIB_SHOT_COOLDOWN == 120 && UndeadKitConfig.RIB_SHOT_COUNT == 7 && UndeadKitConfig.RIB_SHOT_DAMAGE == 6f
				&& UndeadKitConfig.RIB_SHOT_RANGE == 20.0 && UndeadKitConfig.RIB_SHOT_CONE == 15.0, "the agreed numbers");
		helper.assertTrue(UndeadKitConfig.REFORM_HOLD == 20 && UndeadKitConfig.REFORM_TAP < UndeadKitConfig.REFORM_HOLD, "Reform: a one-second Shift hold");
		helper.succeed();
	}

	// ---------------------------------------------------------------- tap V: Rib Shotgun

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60, batch = "undead_vmove_fire")
	public void tapVTearsTheChestOpenThenFiresSevenRibs(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		press(p, false, true);
		helper.assertTrue(!UndeadRibShot.windingUp(p), "nothing on the press (a hold would be the katana)");
		press(p, false, false);
		helper.assertTrue(UndeadRibShot.windingUp(p) && Undead.state(p).animId == UndeadKitConfig.ANIM_RIB_SHOT, "a tap: he tears his chest open");
		helper.assertTrue(ribs(helper, p).isEmpty(), "no ribs fly yet");
		helper.assertTrue(!UndeadSpineWhip.active(p) && Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == 0 && !UndeadBoneSword.held(p),
				"no whip, no Reform, no katana");
		int[] t = { 0 };
		helper.onEachTick(() -> {
			Undead.tick(p);
			t[0]++;
			if (t[0] == UndeadKitConfig.RIB_SHOT_WINDUP - 1) {
				helper.assertTrue(ribs(helper, p).isEmpty(), "still tearing");
			}
			if (t[0] == UndeadKitConfig.RIB_SHOT_WINDUP + 1) {
				List<UndeadPartEntity> r = ribs(helper, p);
				helper.assertTrue(r.size() == UndeadKitConfig.RIB_SHOT_COUNT, "seven ribs fly: " + r.size());
				helper.assertTrue(!UndeadRibShot.windingUp(p), "the wind-up is over");
				int cd = Undead.cooldownRemaining(p, UndeadRibShot.ID);
				helper.assertTrue(cd > UndeadKitConfig.RIB_SHOT_COOLDOWN - 4 && cd <= UndeadKitConfig.RIB_SHOT_COOLDOWN, "6 s cooldown from the shot: " + cd);
				// all inside the 30 degree cone round where it is aimed (the middle of the spread: the chest-to-crosshair line, which
				// tips up off the look when the crosshair is on something close)
				Vec3 mid = Vec3.ZERO;
				for (UndeadPartEntity e : r) {
					mid = mid.add(e.getDeltaMovement().normalize());
				}
				mid = mid.normalize();
				helper.assertTrue(mid.dot(p.getLookAngle()) > 0.9, "aimed the way he looks: " + mid);
				for (UndeadPartEntity e : r) {
					Vec3 v = e.getDeltaMovement().normalize();
					double deg = Math.toDegrees(Math.acos(Math.min(1.0, v.dot(mid))));
					helper.assertTrue(deg <= UndeadKitConfig.RIB_SHOT_CONE + 3.0, "a rib inside the cone: " + deg);
					helper.assertTrue(Math.abs(e.getDeltaMovement().length() - UndeadKitConfig.RIB_SHOT_SPEED) < 0.05, "at its speed");
				}
				helper.assertTrue(!Undead.missing(p, UndeadState.BODY), "the torso is not taken off (his chestplate stays on)");
				clean(helper, p);
				helper.succeed();
			}
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60, batch = "undead_vmove_group")
	public void theRibShotgunTearsIntoAGroupInFront(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		// a close one dead ahead takes most of the spread; one well off it; one behind
		Zombie close = zombie(helper, new Vec3(2.5, 2.0, 2.5));
		Zombie aside = zombie(helper, new Vec3(7.5, 2.0, 3.5)); // ~45 degrees off
		Zombie behind = zombie(helper, new Vec3(2.5, 2.0, -2.5));
		press(p, false, true);
		press(p, false, false);
		int[] t = { 0 };
		helper.onEachTick(() -> {
			Undead.tick(p);
			t[0]++;
			if (t[0] == UndeadKitConfig.RIB_SHOT_WINDUP + 8) {
				float c = dealt(close);
				helper.assertTrue(c >= UndeadKitConfig.RIB_SHOT_DAMAGE * 3 - 0.5f, "the close one takes several spikes: " + c);
				helper.assertTrue(c <= UndeadKitConfig.RIB_SHOT_DAMAGE * UndeadKitConfig.RIB_SHOT_COUNT + 0.5f, "never more than all seven: " + c);
				helper.assertTrue(aside.getHealth() == 200f, "one 45 degrees off is untouched: " + dealt(aside));
				helper.assertTrue(behind.getHealth() == 200f, "nothing behind him");
				helper.assertTrue(p.getHealth() == p.getMaxHealth(), "the ribs never hit him");
				clean(helper, p);
				helper.succeed();
			}
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60, batch = "undead_vmove_row")
	public void theSpreadCatchesAGroupFurtherOut(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		// five abreast across the cone 6 blocks out (the cone is ~3.2 blocks wide there); each spike is spent on its first hit
		Zombie[] row = new Zombie[5];
		for (int i = 0; i < row.length; i++) {
			row[i] = zombie(helper, new Vec3(1.3 + i * 0.6, 2.0, 6.5));
		}
		press(p, false, true);
		press(p, false, false);
		int[] t = { 0 };
		helper.onEachTick(() -> {
			Undead.tick(p);
			t[0]++;
			if (t[0] == UndeadKitConfig.RIB_SHOT_WINDUP + 8) {
				int hit = 0;
				float total = 0f;
				for (Zombie z : row) {
					hit += z.getHealth() < 200f ? 1 : 0;
					total += dealt(z);
				}
				helper.assertTrue(hit >= 3, "most of a group in front is hit: " + hit);
				helper.assertTrue(total <= UndeadKitConfig.RIB_SHOT_DAMAGE * UndeadKitConfig.RIB_SHOT_COUNT + 0.5f, "seven spikes' worth at most: " + total);
				clean(helper, p);
				helper.succeed();
			}
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60, batch = "undead_vmove_knock")
	public void aSpikeKnocksBackALittle(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Zombie z = zombie(helper, new Vec3(2.5, 2.0, 3.5));
		z.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0);
		Vec3 start = z.position();
		press(p, false, true);
		press(p, false, false);
		int[] t = { 0 };
		helper.onEachTick(() -> {
			Undead.tick(p);
			t[0]++;
			if (t[0] == UndeadKitConfig.RIB_SHOT_WINDUP + 6) {
				helper.assertTrue(dealt(z) > 0f, "hit");
				helper.assertTrue(z.position().z > start.z + 0.05 || z.getDeltaMovement().z > 0.05, "shoved away from him: " + z.position().subtract(start));
				clean(helper, p);
				helper.succeed();
			}
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120, batch = "undead_vmove_wall")
	public void spikesStickInAWallThenCrumbleAndTheWallStays(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		for (int x = 0; x <= 5; x++) {
			for (int y = 1; y <= 4; y++) {
				helper.setBlock(new BlockPos(x, y, 5), Blocks.STONE);
			}
		}
		press(p, false, true);
		press(p, false, false);
		int[] t = { 0 };
		int[] stuckAt = { -1 };
		helper.onEachTick(() -> {
			Undead.tick(p);
			t[0]++;
			if (stuckAt[0] < 0 && t[0] > UndeadKitConfig.RIB_SHOT_WINDUP) {
				List<UndeadPartEntity> r = ribs(helper, p);
				if (!r.isEmpty() && r.stream().allMatch(UndeadPartEntity::stuck)) {
					stuckAt[0] = t[0];
					helper.assertTrue(r.size() >= UndeadKitConfig.RIB_SHOT_COUNT - 1, "the spikes stick in the wall: " + r.size());
				}
			}
			if (stuckAt[0] > 0 && t[0] == stuckAt[0] + UndeadKitConfig.RIB_SHOT_EMBED - 12) {
				helper.assertTrue(!ribs(helper, p).isEmpty(), "still stuck a moment later");
			}
			if (stuckAt[0] > 0 && t[0] == stuckAt[0] + UndeadKitConfig.RIB_SHOT_EMBED + 3) {
				helper.assertTrue(ribs(helper, p).isEmpty(), "then they crumble away");
				for (int x = 0; x <= 5; x++) {
					for (int y = 1; y <= 4; y++) {
						helper.assertBlockPresent(Blocks.STONE, new BlockPos(x, y, 5));
					}
				}
				helper.succeed();
			}
			if (t[0] == UndeadKitConfig.RIB_SHOT_WINDUP + 20 && stuckAt[0] < 0) {
				helper.fail("the spikes never all stuck: " + ribs(helper, p).size());
			}
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60, batch = "undead_vmove_range")
	public void spikesAreGonePastTwentyBlocks(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Vec3 from = p.position();
		press(p, false, true);
		press(p, false, false);
		int[] t = { 0 };
		helper.onEachTick(() -> {
			Undead.tick(p);
			t[0]++;
			int flight = (int) Math.ceil(UndeadKitConfig.RIB_SHOT_RANGE / UndeadKitConfig.RIB_SHOT_SPEED);
			if (t[0] > UndeadKitConfig.RIB_SHOT_WINDUP) {
				for (UndeadPartEntity e : ribs(helper, p)) {
					helper.assertTrue(e.position().distanceTo(from) < UndeadKitConfig.RIB_SHOT_RANGE + 3.5, "never far past 20 blocks");
				}
			}
			if (t[0] == UndeadKitConfig.RIB_SHOT_WINDUP + flight + 3) {
				// none still flying: each is gone at the range, or stuck in whatever it met short of it (a neighbouring test's blocks)
				helper.assertTrue(ribs(helper, p).stream().noneMatch(UndeadPartEntity::inFlight), "none flies on past the range");
				clean(helper, p);
				helper.succeed();
			}
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void ribShotgunHasACooldownAndNeedsTheTorso(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		edit(p, n -> n.abilityReadyAt.put(UndeadRibShot.ID, helper.getLevel().getGameTime() + 60));
		press(p, false, true);
		press(p, false, false);
		helper.assertTrue(!UndeadRibShot.windingUp(p), "cooling down: nothing");
		edit(p, n -> n.abilityReadyAt.clear());
		Undead.breakPart(p, UndeadState.BODY, UndeadState.KIND_BURST);
		helper.assertTrue(!UndeadRibShot.start(p), "no torso, no ribs");
		Undead.restore(p);
		helper.assertTrue(UndeadRibShot.start(p) && !UndeadRibShot.start(p), "one at a time");
		clean(helper, p);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40)
	public void losingTheTorsoMidTearEndsItFree(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		helper.assertTrue(UndeadRibShot.start(p), "it starts");
		Undead.breakPart(p, UndeadState.BODY, UndeadState.KIND_BURST);
		int[] t = { 0 };
		helper.onEachTick(() -> {
			Undead.tick(p);
			t[0]++;
			if (t[0] == UndeadKitConfig.RIB_SHOT_WINDUP + 3) {
				helper.assertTrue(!UndeadRibShot.windingUp(p) && ribs(helper, p).isEmpty(), "nothing fires");
				helper.assertTrue(Undead.cooldownRemaining(p, UndeadRibShot.ID) == 0, "and it costs no cooldown");
				helper.succeed();
			}
		});
	}

	// ---------------------------------------------------------------- tap Shift+V: Spine Whip

	private static void tapShiftV(ServerPlayer p) {
		press(p, true, true);
		press(p, true, false);
		p.setShiftKeyDown(false);
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60, batch = "undead_vmove_whip")
	public void spineWhipTearsThenLashesTheWholeArcInFront(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		// (he stands at x 2.5, z 0.5 looking +z; his left is +x) one well to each side, one far out ahead (snagged), one behind
		Zombie left = zombie(helper, new Vec3(5.5, 2.0, 3.0));
		Zombie right = zombie(helper, new Vec3(0.3, 2.0, 2.5));
		Zombie ahead = zombie(helper, new Vec3(2.5, 2.0, 6.5));
		Zombie behind = zombie(helper, new Vec3(2.5, 2.0, -2.5));
		tapShiftV(p);
		helper.assertTrue(UndeadSpineWhip.windingUp(p), "a tap: he reaches back for his spine");
		helper.assertTrue(Undead.cooldownRemaining(p, UndeadSpineWhip.ID) == 0, "no cooldown until it lashes");
		int[] t = { 0 };
		helper.onEachTick(() -> {
			Undead.tick(p);
			t[0]++;
			if (t[0] == UndeadKitConfig.SPINE_WINDUP - 1) {
				helper.assertTrue(left.getHealth() == 200f && right.getHealth() == 200f && ahead.getHealth() == 200f, "nothing hit while he tears it out");
			}
			if (t[0] == UndeadKitConfig.SPINE_WINDUP + UndeadKitConfig.SPINE_SWEEP + 1) {
				helper.assertTrue(!UndeadSpineWhip.active(p), "the lash is over");
				float dl = dealt(left), dr = dealt(right), da = dealt(ahead);
				helper.assertTrue(Math.abs(dl - UndeadKitConfig.SPINE_DAMAGE) < 0.6f, "the one off to his left: 14: " + dl);
				helper.assertTrue(Math.abs(dr - UndeadKitConfig.SPINE_DAMAGE) < 0.6f, "the one off to his right: 14: " + dr);
				helper.assertTrue(Math.abs(da - UndeadKitConfig.SPINE_DAMAGE) < 0.6f, "the snagged one ahead: 14 too: " + da);
				helper.assertTrue(behind.getHealth() == 200f, "nothing behind him");
				helper.assertTrue(p.getHealth() == p.getMaxHealth(), "the whip never hits him");
				int cd = Undead.cooldownRemaining(p, UndeadSpineWhip.ID);
				helper.assertTrue(cd > UndeadKitConfig.SPINE_COOLDOWN - UndeadKitConfig.SPINE_SWEEP - 3 && cd <= UndeadKitConfig.SPINE_COOLDOWN,
						"7 s cooldown from the lash: " + cd);
				helper.assertTrue(!Undead.missing(p, UndeadState.BODY), "the torso is not taken off (his chestplate stays on)");
				helper.succeed();
			}
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60, batch = "undead_vmove_whip_order")
	public void theLashSweepsFromTheWhipHandSideAcross(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.setMainArm(net.minecraft.world.entity.HumanoidArm.RIGHT);
		// right-handed: it starts on his right (-x) and sweeps to his left (+x)
		Zombie right = zombie(helper, new Vec3(0.5, 2.0, 1.4));
		Zombie left = zombie(helper, new Vec3(4.5, 2.0, 1.4));
		tapShiftV(p);
		int[] t = { 0 };
		int[] rightAt = { -1 }, leftAt = { -1 };
		helper.onEachTick(() -> {
			Undead.tick(p);
			t[0]++;
			if (rightAt[0] < 0 && right.getHealth() < 200f) {
				rightAt[0] = t[0];
			}
			if (leftAt[0] < 0 && left.getHealth() < 200f) {
				leftAt[0] = t[0];
			}
			if (t[0] == UndeadKitConfig.SPINE_WINDUP + UndeadKitConfig.SPINE_SWEEP + 1) {
				helper.assertTrue(rightAt[0] > 0 && leftAt[0] > 0, "both hit: " + rightAt[0] + " / " + leftAt[0]);
				helper.assertTrue(rightAt[0] < leftAt[0], "the right one first, the left one as it comes across: " + rightAt[0] + " / " + leftAt[0]);
				helper.assertTrue(rightAt[0] >= UndeadKitConfig.SPINE_WINDUP, "not before the lash");
				helper.succeed();
			}
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60, batch = "undead_vmove_whip_snag")
	public void theTipSnagsTheOneAtTheCrosshairAndYanksItIn(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Zombie ahead = zombie(helper, new Vec3(2.5, 2.0, 6.5));
		ahead.setNoGravity(false);
		ahead.setNoAi(false); // (a no-AI mob is never moved by its velocity)
		ahead.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		ahead.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0);
		Zombie aside = zombie(helper, new Vec3(5.5, 2.0, 3.0));
		aside.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0);
		double startAhead = ahead.position().subtract(p.position()).horizontalDistance();
		double startAside = aside.position().subtract(p.position()).horizontalDistance();
		tapShiftV(p);
		int[] t = { 0 };
		helper.onEachTick(() -> {
			Undead.tick(p);
			t[0]++;
			if (t[0] == UndeadKitConfig.SPINE_WINDUP + UndeadKitConfig.SPINE_SWEEP + 1) {
				helper.assertTrue(dealt(ahead) > 0f && dealt(aside) > 0f, "both hit");
				Vec3 toHim = p.position().subtract(ahead.position());
				Vec3 v = ahead.getDeltaMovement();
				double moved = startAhead - ahead.position().subtract(p.position()).horizontalDistance();
				helper.assertTrue(moved > 0.3 || v.x * toHim.x + v.z * toHim.z > 0.1, "the snagged one comes toward him: moved " + moved + " v " + v);
				double away = aside.position().subtract(p.position()).horizontalDistance() - startAside;
				Vec3 va = aside.getDeltaMovement();
				Vec3 out = aside.position().subtract(p.position());
				helper.assertTrue(away > 0.05 || va.x * out.x + va.z * out.z > 0.05, "the other is knocked away: " + away + " v " + va);
			}
			if (t[0] == UndeadKitConfig.SPINE_WINDUP + UndeadKitConfig.SPINE_SWEEP + 12) {
				double d = ahead.position().subtract(p.position()).horizontalDistance();
				helper.assertTrue(d < startAhead - 1.5, "yanked well in: " + startAhead + " -> " + d);
				helper.assertTrue(d > 0.5, "landing in front of him, not through him: " + d);
				helper.succeed();
			}
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60, batch = "undead_vmove_whip_reach")
	public void theWhipReachesSevenBlocksAndNoFurther(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		// one dead ahead near the far end of the reach (the test's walls keep anything further out of sight)
		Zombie in = zombie(helper, new Vec3(2.5, 2.0, 7.2)); // 6.7 out (plus its width)
		tapShiftV(p);
		int[] t = { 0 };
		helper.onEachTick(() -> {
			Undead.tick(p);
			t[0]++;
			if (t[0] == UndeadKitConfig.SPINE_WINDUP + UndeadKitConfig.SPINE_SWEEP + 1) {
				helper.assertTrue(dealt(in) > 0f, "6.7 blocks out: in reach");
				helper.succeed();
			}
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void spineWhipHasACooldownAndNeedsTheTorsoAndAnArm(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		edit(p, n -> n.abilityReadyAt.put(UndeadSpineWhip.ID, helper.getLevel().getGameTime() + 60));
		tapShiftV(p);
		helper.assertTrue(!UndeadSpineWhip.active(p), "cooling down: nothing");
		edit(p, n -> n.abilityReadyAt.clear());
		Undead.breakPart(p, UndeadState.BODY, UndeadState.KIND_BURST);
		helper.assertTrue(!UndeadSpineWhip.start(p), "no torso, no spine");
		Undead.restore(p);
		Undead.breakPart(p, UndeadState.RIGHT_ARM, UndeadState.KIND_BURST);
		Undead.breakPart(p, UndeadState.LEFT_ARM, UndeadState.KIND_BURST);
		helper.assertTrue(!UndeadSpineWhip.start(p), "no arms, nothing to hold it with");
		Undead.restore(p);
		helper.assertTrue(UndeadSpineWhip.start(p) && !UndeadSpineWhip.start(p), "one at a time");
		helper.assertTrue(!UndeadRibShot.start(p), "and no rib shot in the middle of it");
		UndeadSpineWhip.forTestsCancel(p);
		helper.assertTrue(UndeadBoneSword.draw(p), "the katana is out");
		helper.assertTrue(!UndeadSpineWhip.start(p), "not with the katana out");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40)
	public void losingTheTorsoMidTearEndsTheWhipFree(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Zombie z = zombie(helper, new Vec3(2.5, 2.0, 3.5));
		helper.assertTrue(UndeadSpineWhip.start(p), "it starts");
		Undead.breakPart(p, UndeadState.BODY, UndeadState.KIND_BURST);
		int[] t = { 0 };
		helper.onEachTick(() -> {
			Undead.tick(p);
			t[0]++;
			if (t[0] == UndeadKitConfig.SPINE_WINDUP + UndeadKitConfig.SPINE_SWEEP + 2) {
				helper.assertTrue(!UndeadSpineWhip.active(p) && z.getHealth() == 200f, "nothing lashes");
				helper.assertTrue(Undead.cooldownRemaining(p, UndeadSpineWhip.ID) == 0, "and it costs no cooldown");
				helper.succeed();
			}
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60, batch = "undead_vmove_whip_wall")
	public void theWhipDoesNotCutThroughAWall(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		for (int x = 0; x <= 5; x++) {
			for (int y = 1; y <= 4; y++) {
				helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
			}
		}
		Zombie walled = zombie(helper, new Vec3(2.5, 2.0, 5.5));
		tapShiftV(p);
		int[] t = { 0 };
		helper.onEachTick(() -> {
			Undead.tick(p);
			t[0]++;
			if (t[0] == UndeadKitConfig.SPINE_WINDUP + UndeadKitConfig.SPINE_SWEEP + 1) {
				helper.assertTrue(walled.getHealth() == 200f, "behind a wall: untouched");
				helper.succeed();
			}
		});
	}

	// ---------------------------------------------------------------- the four V inputs never misfire into each other

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shiftVTappedIsOnlyTheSpineWhip(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Undead.breakPart(p, UndeadState.LEFT_LEG, UndeadState.KIND_BURST);
		press(p, true, true);
		assertNothing(helper, p, "Shift+V just pressed");
		press(p, true, false);
		helper.assertTrue(UndeadSpineWhip.windingUp(p) && Undead.state(p).animId == UndeadKitConfig.ANIM_SPINE_WHIP, "a Shift+V tap: the whip");
		helper.assertTrue(!UndeadRibShot.windingUp(p) && Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == 0 && Undead.missing(p, UndeadState.LEFT_LEG),
				"not the shot, not Reform");
		UndeadSpineWhip.forTestsCancel(p);
		edit(p, n -> n.abilityReadyAt.clear());
		// let go of Shift before V: still the whip (the press decided)
		press(p, true, true);
		press(p, false, false);
		helper.assertTrue(UndeadSpineWhip.windingUp(p) && !UndeadRibShot.windingUp(p), "Shift let go first: still the whip");
		clean(helper, p);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void vTappedIsOnlyTheRibShotgunEvenIfShiftComesDownLate(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		press(p, false, true);
		press(p, true, false); // Shift went down after V
		helper.assertTrue(UndeadRibShot.windingUp(p), "a V tap is the shot");
		helper.assertTrue(!UndeadSpineWhip.active(p) && Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == 0, "never the whip or Reform");
		clean(helper, p);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shiftHoldVOneSecondIsOnlyReform(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Undead.breakPart(p, UndeadState.LEFT_LEG, UndeadState.KIND_BURST);
		press(p, true, true);
		UndeadAbilityManager.forTestsHoldShiftV(p, UndeadKitConfig.REFORM_TAP + 2);
		helper.assertTrue(Undead.state(p).animId == UndeadKitConfig.ANIM_REFORM_GATHER, "past a tap he gathers himself");
		helper.assertTrue(!UndeadSpineWhip.active(p) && Undead.missing(p, UndeadState.LEFT_LEG), "no whip, nothing re-formed yet");
		UndeadAbilityManager.forTestsHoldShiftV(p, UndeadKitConfig.REFORM_HOLD - 2);
		helper.assertTrue(Undead.missing(p, UndeadState.LEFT_LEG), "not before the second is up");
		UndeadAbilityManager.forTestsHoldShiftV(p, UndeadKitConfig.REFORM_HOLD);
		helper.assertTrue(!Undead.missing(p, UndeadState.LEFT_LEG) && Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == UndeadConfig.REFORM_COOLDOWN,
				"held a second: Reform, still held");
		helper.assertTrue(Undead.state(p).animId == UndeadState.ANIM_REFORM, "its snap-together animation");
		UndeadAbilityManager.forTestsHoldShiftV(p, UndeadKitConfig.REFORM_HOLD + 30);
		press(p, true, false);
		helper.assertTrue(!UndeadSpineWhip.active(p) && !UndeadRibShot.windingUp(p) && !UndeadBoneSword.held(p), "letting go after: no whip, no shot, no katana");
		clean(helper, p);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shiftHoldVLetGoEarlyDoesNothing(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Undead.breakPart(p, UndeadState.LEFT_LEG, UndeadState.KIND_BURST);
		press(p, true, true);
		UndeadAbilityManager.forTestsHoldShiftV(p, UndeadKitConfig.REFORM_TAP + 4);
		press(p, true, false);
		assertNothing(helper, p, "Shift+V let go between a tap and a second");
		helper.assertTrue(Undead.missing(p, UndeadState.LEFT_LEG), "nothing re-formed");
		helper.assertTrue(Undead.state(p).animId != UndeadKitConfig.ANIM_REFORM_GATHER, "the gathering pose ends");
		clean(helper, p);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shiftHoldVWithReformCoolingDownDoesNothing(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		edit(p, n -> n.abilityReadyAt.put(UndeadAbilities.REFORM, helper.getLevel().getGameTime() + 200));
		press(p, true, true);
		UndeadAbilityManager.forTestsHoldShiftV(p, UndeadKitConfig.REFORM_TAP + 2);
		helper.assertTrue(Undead.state(p).animId != UndeadKitConfig.ANIM_REFORM_GATHER, "no gathering while Reform cools down");
		UndeadAbilityManager.forTestsHoldShiftV(p, UndeadKitConfig.REFORM_HOLD + 2);
		press(p, true, false);
		helper.assertTrue(!UndeadSpineWhip.active(p) && !UndeadRibShot.windingUp(p), "and the let-go is not a whip or a shot");
		clean(helper, p);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void holdVIsOnlyTheKatana(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Undead.breakPart(p, UndeadState.LEFT_LEG, UndeadState.KIND_BURST);
		press(p, false, true);
		UndeadAbilityManager.forTestsHoldV(p, UndeadConfig.BONE_SWORD_TAP + 3);
		helper.assertTrue(Undead.state(p).animId == UndeadState.ANIM_SWORD_STRUGGLE, "past a tap: the struggle");
		press(p, false, false);
		assertNothing(helper, p, "V let go mid-struggle");
		helper.assertTrue(Undead.missing(p, UndeadState.LEFT_LEG), "no Reform");
		edit(p, n -> n.abilityReadyAt.clear());
		press(p, false, true);
		UndeadAbilityManager.forTestsHoldV(p, UndeadConfig.BONE_SWORD_TAP + 3);
		UndeadAbilityManager.forTestsHoldV(p, UndeadConfig.BONE_SWORD_HOLD + 1);
		press(p, false, false);
		helper.assertTrue(UndeadBoneSword.held(p), "held 2 s: the katana");
		helper.assertTrue(!UndeadRibShot.windingUp(p) && Undead.cooldownRemaining(p, UndeadRibShot.ID) == 0 && !UndeadSpineWhip.active(p)
				&& Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == 0, "and nothing else");
		// with the katana out, tapping V puts it away -- it does not fire the ribs
		press(p, false, true);
		press(p, false, false);
		helper.assertTrue(!UndeadBoneSword.held(p) && !UndeadRibShot.windingUp(p), "V sheathes it; no shot");
		clean(helper, p);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shiftHoldVWithTheKatanaOutPutsItAwayOnly(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		helper.assertTrue(UndeadBoneSword.draw(p), "the katana is out");
		press(p, true, true);
		UndeadAbilityManager.forTestsHoldShiftV(p, UndeadKitConfig.REFORM_HOLD + 2);
		press(p, true, false);
		helper.assertTrue(!UndeadBoneSword.held(p), "Shift+V puts it away");
		helper.assertTrue(Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == 0 && !UndeadSpineWhip.active(p), "no Reform, no whip with it");
		clean(helper, p);
		helper.succeed();
	}
}
