package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadAbilities;
import com.projecthero.mod.undead.UndeadAbilityManager;
import com.projecthero.mod.undead.UndeadBoneSword;
import com.projecthero.mod.undead.UndeadKitConfig;
import com.projecthero.mod.undead.UndeadLaunch;
import com.projecthero.mod.undead.UndeadMeteor;
import com.projecthero.mod.undead.UndeadRegenMods;
import com.projecthero.mod.undead.UndeadRibGuard;
import com.projecthero.mod.undead.UndeadShoulderSword;
import com.projecthero.mod.undead.UndeadVortex;
import com.projecthero.mod.undead.data.UndeadState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Undead kit rework: Z (Ten Twists: Vortex Bullet), Shift + hold Z (Unluck Meteor), Shift+V (Rib Cage Guard), hold V (the
 * bone katana's skills) and the shoulder sword -- each move's server side, its numbers, and that the shoulder sword is never
 * lost or doubled. Mock players are not ticked by the server, so tests over time drive {@link Undead#tick}.
 */
public class UndeadKitGameTests implements FabricGameTest {
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

	/** A still, floating zombie with plenty of health at {@code rel} (relative to the test). */
	private static Zombie zombie(GameTestHelper helper, Vec3 rel) {
		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(rel);
		z.moveTo(at.x, at.y, at.z, 180f, 0f);
		z.setNoAi(true);
		z.setNoGravity(true);
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		z.setHealth(200);
		z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning in the sun
		helper.getLevel().addFreshEntity(z);
		return z;
	}

	private static Zombie zombieAt(GameTestHelper helper, Vec3 abs) {
		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		z.moveTo(abs.x, abs.y, abs.z, 180f, 0f);
		z.setNoAi(true);
		z.setNoGravity(true);
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		z.setHealth(200);
		z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning in the sun
		helper.getLevel().addFreshEntity(z);
		return z;
	}

	/** "dealt X (by Y)" for a zombie that started on 200. */
	private static String hurt(Zombie z) {
		net.minecraft.world.damagesource.DamageSource s = z.getLastDamageSource();
		return (200f - z.getHealth()) + " (" + (s == null ? "-" : s.getMsgId()) + ")";
	}

	/** Stands him at the near edge of the 8x8 box, facing +z along its length. */
	private static void atStart(GameTestHelper helper, ServerPlayer p) {
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 0.5));
		p.teleportTo(at.x, at.y, at.z);
		p.setYRot(0f);
		p.setXRot(0f);
		p.setYHeadRot(0f);
	}

	private static void press(ServerPlayer p, AbilitySlot slot, boolean shift, boolean down) {
		p.setShiftKeyDown(shift);
		UndeadAbilityManager.handle(p, slot, down);
	}

	private static void clean(GameTestHelper helper, ServerPlayer p) {
		helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new AABB(p.blockPosition()).inflate(48),
				e -> e instanceof com.projecthero.mod.undead.entity.UndeadPartEntity || e instanceof ItemEntity).forEach(e -> e.discard());
	}

	// ---------------------------------------------------------------- Z: Ten Twists -- Vortex Bullet

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80, batch = "undeadkit_vortex_alone")
	public void vortexBulletTwistsThenBoresThroughALine(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		atStart(helper, p);
		int arm = Undead.mainArm(p);
		Zombie[] line = { zombie(helper, new Vec3(1.3, 2.0, 2.6)), zombie(helper, new Vec3(1.3, 2.0, 4.1)), zombie(helper, new Vec3(1.3, 2.0, 5.6)) };
		Zombie aside = zombie(helper, new Vec3(6.6, 2.0, 1.2)); // 5 blocks off the line, far from where it stops
		press(p, AbilitySlot.SLOT_4, false, true);
		press(p, AbilitySlot.SLOT_4, false, false);
		helper.assertTrue(UndeadVortex.windingUp(p) && Undead.state(p).animId == UndeadKitConfig.ANIM_VORTEX_TWIST, "Z winds the arm up first");
		helper.assertTrue(!Undead.missing(p, arm), "the arm is still on while it is twisted");
		helper.assertTrue(UndeadAbilityManager.idsOf(p, AbilitySlot.SLOT_4)[0].equals(UndeadAbilities.VORTEX)
				&& UndeadAbilityManager.idsOf(p, AbilitySlot.SLOT_4)[1].equals(UndeadMeteor.ID), "Z and Shift+Z on the HUD");
		int[] t = { 0 };
		helper.onEachTick(() -> {
			Undead.tick(p);
			t[0]++;
			if (t[0] == UndeadKitConfig.VORTEX_WINDUP - 2) {
				helper.assertTrue(!Undead.missing(p, arm), "not fired before the ten twists are done");
			}
			if (t[0] == UndeadKitConfig.VORTEX_WINDUP + 2) {
				helper.assertTrue(Undead.missing(p, arm) && Undead.state(p).kind(arm) == UndeadState.KIND_LAUNCHED, "the arm flies off as the drill");
				int cd = Undead.cooldownRemaining(p, UndeadAbilities.VORTEX);
				helper.assertTrue(cd > UndeadKitConfig.VORTEX_COOLDOWN - 6 && cd <= UndeadKitConfig.VORTEX_COOLDOWN, "8 s cooldown: " + cd);
			}
			if (t[0] == UndeadKitConfig.VORTEX_WINDUP + 16) {
				for (int i = 0; i < line.length; i++) {
					float dealt = 200f - line[i].getHealth();
					helper.assertTrue(dealt >= UndeadKitConfig.VORTEX_DAMAGE - 3f, "zombie " + i + " on the line is bored through: " + hurt(line[i]));
				}
				helper.assertTrue(aside.getHealth() == 200f, "one off the line is untouched: " + hurt(aside));
				clean(helper, p);
				helper.succeed();
			}
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void vortexBulletWindupBreaksOffWithTheArm(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		int arm = Undead.mainArm(p);
		helper.assertTrue(UndeadVortex.start(p), "it starts");
		Undead.breakPart(p, arm, UndeadState.KIND_BURST);
		Undead.tick(p);
		helper.assertTrue(!UndeadVortex.windingUp(p), "losing the arm mid-twist ends it");
		helper.assertTrue(Undead.cooldownRemaining(p, UndeadAbilities.VORTEX) == 0, "and costs no cooldown");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Shift + hold Z: Unluck Meteor

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void unluckMeteorLetGoEarlyIsCancelled(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		double speed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		press(p, AbilitySlot.SLOT_4, true, true);
		helper.assertTrue(UndeadMeteor.charging(p) && Undead.state(p).flag(UndeadKitConfig.FLAG_METEOR_CHARGE), "Shift+Z down: charging");
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) < speed * 0.5, "slowed while charging");
		UndeadMeteor.forTestsCharged(p, UndeadKitConfig.METEOR_CHARGE - 20);
		helper.assertTrue(UndeadMeteor.charging(p), "4 s in: still charging");
		press(p, AbilitySlot.SLOT_4, true, false);
		helper.assertTrue(!UndeadMeteor.charging(p) && !Undead.state(p).flag(UndeadKitConfig.FLAG_METEOR_CHARGE), "let go early: gone");
		helper.assertTrue(Undead.cooldownRemaining(p, UndeadMeteor.ID) == 0, "no cooldown");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < 1.0e-6, "full speed again");
		p.setShiftKeyDown(false);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120, batch = "undeadkit_meteor_alone")
	public void unluckMeteorFallsAfterFiveSecondsAndBlasts(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		atStart(helper, p);
		// a stone floor ahead to look at
		for (int x = 0; x <= 5; x++) {
			for (int z = 1; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
		p.setXRot(35f);
		Vec3 target = UndeadMeteor.target(p);
		helper.assertTrue(target.y > helper.absoluteVec(new Vec3(0, 1.5, 0)).y && target.y < helper.absoluteVec(new Vec3(0, 2.5, 0)).y,
				"he looks at the floor: " + target);
		Zombie near = zombieAt(helper, target.add(1.0, 0.1, 0));
		Zombie mid = zombieAt(helper, target.add(5.0, 0.1, 3.0)); // ~6 out, still in the box
		Zombie far = zombieAt(helper, target.add(0, 0.1, 14.0)); // beyond the far wall, outside the 10-block blast
		press(p, AbilitySlot.SLOT_4, true, true);
		UndeadMeteor.forTestsCharged(p, UndeadKitConfig.METEOR_CHARGE);
		helper.assertTrue(!UndeadMeteor.charging(p) && UndeadMeteor.forTestsFalling() >= 1, "5 s: the meteor is called down (still held)");
		helper.assertTrue(Undead.cooldownRemaining(p, UndeadMeteor.ID) == UndeadKitConfig.METEOR_COOLDOWN, "90 s cooldown");
		helper.assertTrue(near.getHealth() == 200f, "nothing yet: it is still falling");
		press(p, AbilitySlot.SLOT_4, true, false);
		p.setShiftKeyDown(false);
		helper.runAfterDelay(UndeadKitConfig.METEOR_FALL + 3, () -> {
			float dn = 200f - near.getHealth(), dm = 200f - mid.getHealth();
			helper.assertTrue(dn >= 28f, "the centre takes the brunt: " + hurt(near));
			helper.assertTrue(dm > 0f && dm < dn, "less further out: " + hurt(mid));
			helper.assertTrue(far.getHealth() == 200f, "outside the 10-block blast: untouched: " + hurt(far));
			helper.assertTrue(near.isOnFire(), "set alight");
			for (int x = 0; x <= 5; x++) {
				for (int z = 1; z <= 6; z++) {
					helper.assertBlockPresent(Blocks.STONE, new BlockPos(x, 1, z));
				}
			}
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Shift+V: Rib Cage Guard

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void ribCageGuardBlocksTheFrontAndBitesBack(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		helper.assertTrue(UndeadAbilityManager.idsOf(p, AbilitySlot.SLOT_5)[1].equals(UndeadRibGuard.ID), "Shift+V on the HUD");
		Zombie front = zombie(helper, new Vec3(2.5, 2.0, 4.0));
		Zombie behind = zombie(helper, new Vec3(2.5, 2.0, 1.0));
		press(p, AbilitySlot.SLOT_5, true, true);
		helper.assertTrue(UndeadRibGuard.up(p) && Undead.state(p).flag(UndeadKitConfig.FLAG_RIB_GUARD), "Shift+V: the ribs are up");
		helper.assertTrue(!UndeadBoneSword.held(p) && Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == 0, "not the sword, not Reform");
		float hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().mobAttack(front), 8f);
		float lost = hp - p.getHealth();
		helper.assertTrue(Math.abs(lost - 8f * UndeadKitConfig.RIB_MELEE_SHARE) < 0.05f, "a melee hit from the front: 75% blocked: lost " + lost);
		helper.assertTrue(200f - front.getHealth() >= UndeadKitConfig.RIB_THORNS - 2.1f, "and the attacker is bitten");
		Arrow arrow = new Arrow(helper.getLevel(), p.getX(), p.getEyeY() - 0.5, p.getZ() + 2.0, new ItemStack(Items.ARROW), null);
		hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().arrow(arrow, null), 6f);
		helper.assertTrue(p.getHealth() == hp, "an arrow from the front is stopped");
		// an arrow in flight toward him stops dead on the bone
		arrow.setDeltaMovement(0, 0, -1.2);
		helper.getLevel().addFreshEntity(arrow);
		Undead.tick(p);
		helper.assertTrue(arrow.getDeltaMovement().z > -0.3, "the flying arrow is stopped: " + arrow.getDeltaMovement());
		arrow.discard();
		hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().mobAttack(behind), 6f);
		helper.assertTrue(hp - p.getHealth() > 5.5f, "from behind: the full hit");
		press(p, AbilitySlot.SLOT_5, true, false);
		helper.assertTrue(UndeadRibGuard.up(p), "a quick tap leaves it up");
		UndeadRibGuard.forTestsLower(p);
		helper.assertTrue(!UndeadRibGuard.up(p) && Undead.cooldownRemaining(p, UndeadRibGuard.ID) == UndeadKitConfig.RIB_COOLDOWN, "down: 12 s cooldown");
		p.setShiftKeyDown(false);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 140)
	public void ribCageGuardIsUpWhileHeldUpToFourSeconds(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		press(p, AbilitySlot.SLOT_5, true, true);
		p.setShiftKeyDown(false);
		int[] t = { 0 };
		helper.onEachTick(() -> {
			Undead.tick(p);
			t[0]++;
			if (t[0] == 20) {
				helper.assertTrue(UndeadRibGuard.up(p), "held: up");
				press(p, AbilitySlot.SLOT_5, true, false);
				p.setShiftKeyDown(false);
				helper.assertTrue(!UndeadRibGuard.up(p), "a hold let go: down");
			}
			if (t[0] == 21) {
				helper.assertTrue(!Undead.state(p).flag(UndeadKitConfig.FLAG_RIB_GUARD) && Undead.cooldownRemaining(p, UndeadRibGuard.ID) > 0, "folded away, cooling down");
				edit(p, n -> n.abilityReadyAt.clear());
				press(p, AbilitySlot.SLOT_5, true, true); // and held on
				p.setShiftKeyDown(false);
				helper.assertTrue(UndeadRibGuard.up(p), "up again");
			}
			if (t[0] == 21 + UndeadKitConfig.RIB_TICKS - 5) {
				helper.assertTrue(UndeadRibGuard.up(p), "still held: still up");
			}
			if (t[0] == 21 + UndeadKitConfig.RIB_TICKS + 3) {
				helper.assertTrue(!UndeadRibGuard.up(p) && !Undead.state(p).flag(UndeadKitConfig.FLAG_RIB_GUARD), "4 s is the most");
				helper.succeed();
			}
		});
	}

	// ---------------------------------------------------------------- hold V: the bone katana's skills

	private static ServerPlayer swordsman(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		helper.assertTrue(UndeadBoneSword.draw(p), "the katana is drawn");
		return p;
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theKatanasKeysAndPuttingItAway(GameTestHelper helper) {
		ServerPlayer p = swordsman(helper);
		helper.assertTrue(UndeadBoneSword.K_CRESCENT.equals(UndeadAbilityManager.idsOf(p, AbilitySlot.SLOT_1)[0]), "R: Crimson Crescent Moon");
		helper.assertTrue(UndeadBoneSword.K_GREAT.equals(UndeadAbilityManager.idsOf(p, AbilitySlot.SLOT_2)[0]), "G: the Great one");
		helper.assertTrue(UndeadBoneSword.K_FLASH.equals(UndeadAbilityManager.idsOf(p, AbilitySlot.SLOT_4)[0]), "Z: Crimson Flash");
		helper.assertTrue(UndeadBoneSword.K_WHIRL.equals(UndeadAbilityManager.idsOf(p, AbilitySlot.SLOT_6)[0]), "C: Blood Whirl");
		helper.assertTrue(UndeadLaunch.EJECT_FEET.equals(UndeadAbilityManager.idsOf(p, AbilitySlot.SLOT_3)[0]), "X stays his own");
		for (String id : new String[] { UndeadBoneSword.K_CRESCENT, UndeadBoneSword.K_GREAT, UndeadBoneSword.K_FLASH, UndeadBoneSword.K_WHIRL }) {
			helper.assertTrue(UndeadAbilityManager.maxCooldown(id) > 0, "the HUD knows " + id);
		}
		press(p, AbilitySlot.SLOT_5, true, true);
		press(p, AbilitySlot.SLOT_5, true, false);
		helper.assertTrue(!UndeadBoneSword.held(p) && !UndeadRibGuard.up(p), "Shift+V puts it away (no Rib Cage Guard with it)");
		edit(p, n -> n.abilityReadyAt.clear());
		Undead.restore(p);
		helper.assertTrue(UndeadBoneSword.draw(p), "drawn again");
		press(p, AbilitySlot.SLOT_5, false, true);
		press(p, AbilitySlot.SLOT_5, false, false);
		helper.assertTrue(!UndeadBoneSword.held(p) && Undead.cooldownRemaining(p, UndeadAbilities.REFORM) == 0, "V puts it away, no Reform");
		p.setShiftKeyDown(false);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120, batch = "undeadkit_katana_alone")
	public void theKatanasSkillsHit(GameTestHelper helper) {
		ServerPlayer p = swordsman(helper);
		atStart(helper, p);
		Zombie crescentTarget = zombie(helper, new Vec3(1.5, 2.0, 7.0)); // 6.5 out: only the flying crescent reaches it
		press(p, AbilitySlot.SLOT_1, false, true);
		press(p, AbilitySlot.SLOT_1, false, false);
		helper.assertTrue(Undead.state(p).animId == UndeadKitConfig.ANIM_K_CRESCENT, "R: Crimson Crescent Moon");
		int[] t = { 0 };
		Zombie[] later = new Zombie[4];
		float[] hp = new float[1];
		helper.onEachTick(() -> {
			Undead.tick(p);
			t[0]++;
			switch (t[0]) {
				case 10 -> {
					helper.assertTrue(200f - crescentTarget.getHealth() >= 10f, "the crescent flies 6.5 blocks and cuts: " + hurt(crescentTarget));
					crescentTarget.discard();
					later[0] = zombie(helper, new Vec3(1.5, 2.0, 7.3)); // on the line, 7 out
					later[1] = zombie(helper, new Vec3(5.0, 2.0, 6.0)); // 3.5 off the line: the great one is wide
					edit(p, n -> n.abilityReadyAt.clear());
					press(p, AbilitySlot.SLOT_2, false, true);
					press(p, AbilitySlot.SLOT_2, false, false);
				}
				case 32 -> {
					helper.assertTrue(200f - later[0].getHealth() >= 18f, "Great Deep-Crimson Crescent Moon: " + hurt(later[0]));
					helper.assertTrue(later[1].getHealth() < 200f, "and wide: " + hurt(later[1]));
					later[0].discard();
					later[1].discard();
					// Z: the iaido draw through a zombie in front; the cut opens after and heals him
					atStart(helper, p);
					later[2] = zombie(helper, new Vec3(1.5, 2.0, 3.5));
					p.setHealth(10f);
					hp[0] = p.getHealth();
					edit(p, n -> n.abilityReadyAt.clear());
					press(p, AbilitySlot.SLOT_4, false, true);
					press(p, AbilitySlot.SLOT_4, false, false);
					helper.assertTrue(Undead.state(p).animId == UndeadKitConfig.ANIM_K_FLASH, "Z: Crimson Flash");
				}
				case 35 -> {
					helper.assertTrue(later[2].getHealth() == 200f, "passed through, not cut yet: " + hurt(later[2]));
					Vec3 end = UndeadBoneSword.forTestsFlashEnd(p);
					helper.assertTrue(end != null && p.position().distanceTo(end) < 0.3, "he is where the draw ends: " + p.position() + " / " + end);
					double went = p.position().distanceTo(helper.absoluteVec(new Vec3(1.5, 2.0, 0.5)));
					helper.assertTrue(went > 5.5 && went <= UndeadKitConfig.K_FLASH_DISTANCE + 0.01, "he went to the wall (8 blocks at most): " + went);
				}
				case 32 + UndeadKitConfig.K_FLASH_TICKS + UndeadKitConfig.K_FLASH_DELAY + 1 -> {
					helper.assertTrue(200f - later[2].getHealth() >= 12f, "the cut opens: " + hurt(later[2]));
					helper.assertTrue(p.getHealth() > hp[0], "and he drinks the blood it draws: " + p.getHealth());
					later[2].discard();
					// C: two turns, all round him
					later[3] = zombie(helper, p.position().subtract(helper.absoluteVec(Vec3.ZERO)).add(0, 0, -2.5)); // behind
					edit(p, n -> n.abilityReadyAt.clear());
					press(p, AbilitySlot.SLOT_6, false, true);
					press(p, AbilitySlot.SLOT_6, false, false);
				}
				case 32 + UndeadKitConfig.K_FLASH_TICKS + UndeadKitConfig.K_FLASH_DELAY + 1 + UndeadKitConfig.K_WHIRL_TICKS + 2 -> {
					float dealt = 200f - later[3].getHealth();
					helper.assertTrue(dealt >= UndeadKitConfig.K_WHIRL_DAMAGE * 2 - 4f, "Blood Whirl hits behind him, twice: " + hurt(later[3]));
					later[3].discard();
					helper.succeed();
				}
				default -> {
				}
			}
		});
	}

	// ---------------------------------------------------------------- the shoulder sword

	private static Holder<Enchantment> ench(GameTestHelper helper, net.minecraft.resources.ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static int swordsAnywhere(ServerPlayer p) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (UndeadShoulderSword.isSword(p.getInventory().getItem(i))) {
				n++;
			}
		}
		return n + (UndeadShoulderSword.has(p) ? 1 : 0);
	}

	private static void fullSwing(ServerPlayer p) {
		try {
			java.lang.reflect.Field f = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker");
			f.setAccessible(true);
			f.setInt(p, 100);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theShoulderSwordGoesInAndOutAndIsTheBareHandsWeapon(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.enchant(ench(helper, Enchantments.SHARPNESS), 5);
		p.setItemInHand(InteractionHand.MAIN_HAND, sword);
		double held = p.getAttributeValue(Attributes.ATTACK_DAMAGE) + 6.0; // a held diamond sword adds 6 to the bare hand
		p.setShiftKeyDown(true);
		helper.assertTrue(UndeadShoulderSword.interact(p), "Shift + right click with a sword: in it goes");
		p.setShiftKeyDown(false);
		helper.assertTrue(p.getMainHandItem().isEmpty() && UndeadShoulderSword.has(p), "out of the hand, into the shoulder");
		helper.assertTrue(swordsAnywhere(p) == 1, "one sword, not two");
		ItemStack in = UndeadShoulderSword.stored(p);
		helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(ench(helper, Enchantments.SHARPNESS), in) == 5, "its enchantments kept");
		helper.assertTrue(UndeadRegenMods.delayMult(p) == UndeadKitConfig.SHOULDER_REGEN_MULT && UndeadRegenMods.durationMult(p) == UndeadKitConfig.SHOULDER_REGEN_MULT,
				"regeneration 10% slower, 10% more delay");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - held) < 0.01,
				"a bare hand hits as hard as holding the sword: " + p.getAttributeValue(Attributes.ATTACK_DAMAGE) + " vs " + held);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_SPEED) - 1.6) < 0.01, "at the sword's speed: " + p.getAttributeValue(Attributes.ATTACK_SPEED));
		helper.assertTrue(p.getWeaponItem() == in, "its weapon is the shoulder sword");
		// a bare-handed hit: the sword's damage, sharpness, and it wears the sword
		Zombie z = zombie(helper, new Vec3(2.5, 2.0, 4.0));
		z.getAttribute(Attributes.ARMOR).setBaseValue(0);
		fullSwing(p);
		p.attack(z);
		float dealt = 200f - z.getHealth();
		helper.assertTrue(dealt >= (float) held + 2.5f, "sword damage plus sharpness: " + dealt);
		helper.assertTrue(in.getDamageValue() == 1, "and the sword is worn by it: " + in.getDamageValue());
		// pulled out into the empty hand
		ItemStack offHand = new ItemStack(Items.SHIELD);
		p.setItemInHand(InteractionHand.OFF_HAND, offHand);
		p.setShiftKeyDown(true);
		helper.assertTrue(UndeadShoulderSword.interact(p), "Shift + right click empty-handed: out it comes");
		p.setShiftKeyDown(false);
		helper.assertTrue(!UndeadShoulderSword.has(p) && p.getMainHandItem().is(Items.DIAMOND_SWORD) && p.getMainHandItem().getDamageValue() == 1,
				"the same sword, wear and all, in the hand");
		helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(ench(helper, Enchantments.SHARPNESS), p.getMainHandItem()) == 5, "enchantments and all");
		helper.assertTrue(swordsAnywhere(p) == 1 && p.getOffhandItem() == offHand, "still exactly one sword; the off hand untouched");
		helper.assertTrue(UndeadRegenMods.delayMult(p) == 1f && UndeadRegenMods.durationMult(p) == 1f, "regeneration back to normal");
		helper.assertTrue(p.getAttribute(Attributes.ATTACK_DAMAGE).getModifiers().stream().noneMatch(m -> m.id().getPath().contains("shoulder")),
				"the bare-hand bonus is gone");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theShoulderSwordsFireAspectAndWearingOutLeaveTheOffHandAlone(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		ItemStack sword = new ItemStack(Items.WOODEN_SWORD);
		sword.enchant(ench(helper, Enchantments.FIRE_ASPECT), 2);
		sword.setDamageValue(sword.getMaxDamage() - 1); // one hit from breaking
		p.setItemInHand(InteractionHand.MAIN_HAND, sword);
		ItemStack offHand = new ItemStack(Items.TORCH, 16);
		p.setItemInHand(InteractionHand.OFF_HAND, offHand);
		helper.assertTrue(UndeadShoulderSword.stab(p), "in");
		helper.assertTrue(!UndeadShoulderSword.stab(p), "nothing more to put in");
		Zombie z = zombie(helper, new Vec3(2.5, 2.0, 4.0));
		fullSwing(p);
		p.attack(z);
		helper.assertTrue(z.isOnFire(), "fire aspect from the shoulder sword");
		helper.assertTrue(p.getOffhandItem() == offHand && offHand.getCount() == 16, "the worn-out sword does not take the off hand with it");
		helper.assertTrue(UndeadShoulderSword.justBroke(p) || !UndeadShoulderSword.has(p), "the sword broke");
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(!UndeadShoulderSword.has(p) && p.getAttachedOrElse(UndeadShoulderSword.ATTACHMENT, null) == null, "and it is gone from the shoulder");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theShoulderSwordDropsOnDeathUnlessKeepInventory(GameTestHelper helper) {
		GameRules.BooleanValue keep = helper.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
		boolean was = keep.get();
		try {
			keep.set(false, helper.getLevel().getServer());
			ServerPlayer p = undead(helper);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			helper.assertTrue(UndeadShoulderSword.stab(p), "in");
			p.kill();
			helper.assertTrue(!UndeadShoulderSword.has(p), "dead: not in the shoulder any more");
			long dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(4), e -> e.getItem().is(Items.IRON_SWORD)).size();
			helper.assertTrue(dropped == 1, "it dropped with his things, once: " + dropped);
			helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(4), e -> true).forEach(e -> e.discard());

			keep.set(true, helper.getLevel().getServer());
			ServerPlayer q = undead(helper);
			q.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_SWORD));
			helper.assertTrue(UndeadShoulderSword.stab(q), "in");
			q.kill();
			helper.assertTrue(UndeadShoulderSword.has(q) && UndeadShoulderSword.stored(q).is(Items.GOLDEN_SWORD), "keepInventory: it stays (copied over on respawn)");
			long none = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(q.blockPosition()).inflate(4), e -> e.getItem().is(Items.GOLDEN_SWORD)).size();
			helper.assertTrue(none == 0, "and nothing dropped");
		} finally {
			keep.set(was, helper.getLevel().getServer());
		}
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theShoulderSwordComesBackWhenThePowerGoes(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_SWORD));
		helper.assertTrue(UndeadShoulderSword.stab(p), "in");
		Undead.revoke(p);
		helper.assertTrue(UndeadShoulderSword.weapon(p) == null, "no longer his bare hand's weapon");
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(!UndeadShoulderSword.has(p) && swordsAnywhere(p) == 1, "back in his inventory: " + swordsAnywhere(p));
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theShoulderSwordIsNotTheWeaponWhileHoldingSomethingOrTheKatana(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		UndeadShoulderSword.stab(p);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		helper.assertTrue(UndeadShoulderSword.weapon(p) == null && p.getWeaponItem().is(Items.STICK), "holding something: that is the weapon");
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.assertTrue(UndeadBoneSword.draw(p), "the katana is drawn");
		helper.assertTrue(UndeadShoulderSword.weapon(p) == null, "the katana out: the katana is the weapon");
		helper.succeed();
	}

	/** v0.17.3 (user: "when undead sword mode is active player shouldnt be able to hold items aswell"). */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void swordModeHoldsNoItems(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
		p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.BREAD));
		helper.assertTrue(!com.projecthero.mod.undead.UndeadMod.handsBusy(p, net.minecraft.world.InteractionHand.MAIN_HAND), "no sword: the hand is free");
		helper.assertTrue(UndeadBoneSword.draw(p) && UndeadBoneSword.held(p), "the katana is out");
		for (net.minecraft.world.InteractionHand h : net.minecraft.world.InteractionHand.values()) {
			helper.assertTrue(com.projecthero.mod.undead.UndeadMod.handsBusy(p, h), "nothing held can be used: " + h);
		}
		helper.assertTrue(com.projecthero.mod.undead.UndeadMod.attackBlocked(p, net.minecraft.world.InteractionHand.MAIN_HAND), "a hit with the diamond sword in hand is refused");
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.assertTrue(!com.projecthero.mod.undead.UndeadMod.attackBlocked(p, net.minecraft.world.InteractionHand.MAIN_HAND), "an empty hand swings the katana");
		helper.succeed();
	}
}
