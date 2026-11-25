package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.ThorFx;
import com.projecthero.mod.power.ThorPowers;
import com.projecthero.mod.power.ThorTargets;
import com.projecthero.mod.power.ThorVisuals;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;
import com.projecthero.mod.thorarmor.ThorArmor;
import com.projecthero.mod.thorarmor.ThorArmorItems;
import com.projecthero.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.4 Thor: every Thor power spares the caster's squad (damage, knockback, slow and fire alike) while still hitting
 * enemies; the move animations are published for every viewer; Thor's Armour forms piece by piece and dissolves before
 * it is removed.
 *
 * <p>Mock players can't be damaged through {@code hurt} (spawn invulnerability), so a squadmate being spared is proved
 * by what CAN be observed on them -- no knockback, no Slowness -- and by the target lists the abilities act on, with a
 * Husk alongside to prove enemies still take the hit.
 */
public class ThorSquadGameTests implements FabricGameTest {
	private static ServerPlayer at(GameTestHelper helper, double x, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		// mock players are placed at world spawn -- bring them into the test's own area
		Vec3 v = helper.absoluteVec(new Vec3(x, 2.0, z));
		p.moveTo(v.x, v.y, v.z, 0f, 0f);
		return p;
	}

	private static ServerPlayer thor(GameTestHelper helper, double x, double z) {
		ServerPlayer p = at(helper, x, z);
		Worthiness.setScore(p, Worthiness.TEST_WORTHY_SCORE);
		ItemStack hammer = new ItemStack(ModItems.MJOLNIR);
		p.setItemInHand(InteractionHand.MAIN_HAND, hammer);
		ThorPowers.toggleBinding(p, hammer);
		return p;
	}

	private static Squad squad(GameTestHelper helper, ServerPlayer leader, ServerPlayer mate) {
		SquadManager squads = SquadManager.get(helper.getLevel().getServer());
		Squad squad = squads.create("thor" + leader.getUUID().toString().substring(0, 8), leader.getUUID());
		squads.addMember(squad, mate.getUUID());
		return squad;
	}

	private static void disband(GameTestHelper helper, Squad squad) {
		SquadManager.get(helper.getLevel().getServer()).disband(squad);
	}

	private static Husk husk(GameTestHelper helper, double x, double z) {
		Husk h = EntityType.HUSK.create(helper.getLevel());
		Vec3 v = helper.absoluteVec(new Vec3(x, 2.0, z));
		h.moveTo(v.x, v.y, v.z, 180f, 0f);
		h.setNoAi(true);
		helper.getLevel().addFreshEntity(h);
		return h;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void thorsTargetRuleSparesTheSquadAndTheirPets(GameTestHelper helper) {
		ServerPlayer thor = thor(helper, 1.5, 1.5);
		ServerPlayer mate = at(helper, 3.5, 1.5);
		Squad squad = squad(helper, thor, mate);
		Husk husk = husk(helper, 1.5, 4.5);
		Wolf pet = EntityType.WOLF.create(helper.getLevel());
		pet.moveTo(thor.getX() + 1, thor.getY(), thor.getZ());
		pet.tame(mate);
		helper.getLevel().addFreshEntity(pet);

		helper.assertFalse(ThorTargets.canAffect(thor, thor), "never the caster");
		helper.assertFalse(ThorTargets.canAffect(thor, mate), "never a squadmate");
		helper.assertFalse(ThorTargets.canAffect(thor, pet), "never a squadmate's pet");
		helper.assertTrue(ThorTargets.canAffect(thor, husk), "enemies are still fair game");
		helper.assertTrue(ThorTargets.lightning(helper.getLevel(), thor).getEntity() == thor,
				"Thor's lightning is credited to Thor, so the squad friendly-fire veto sees it too");
		disband(helper, squad);
		helper.assertTrue(ThorTargets.canAffect(thor, husk), "still an enemy after the squad is gone");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void thunderclapHitsTheHuskButNeverShovesOrSlowsTheSquadmate(GameTestHelper helper) {
		ServerPlayer thor = thor(helper, 4.5, 4.5);
		ServerPlayer mate = at(helper, 6.5, 4.5);
		Squad squad = squad(helper, thor, mate);
		Husk husk = husk(helper, 2.5, 4.5);
		float before = husk.getHealth();
		mate.setDeltaMovement(Vec3.ZERO);

		ThorPowers.thunderclap(thor);

		helper.assertTrue(husk.getHealth() < before, "the Husk takes the shockwave");
		helper.assertTrue(husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and is slowed");
		helper.assertFalse(mate.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the squadmate is not slowed");
		helper.assertTrue(mate.getDeltaMovement().horizontalDistance() < 1.0E-6,
				"the squadmate is not knocked back, got " + mate.getDeltaMovement());
		helper.assertTrue(ThorVisuals.fx(thor).anim() == ThorFx.ANIM_THUNDERCLAP, "the slam animation is published");
		disband(helper, squad);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void chainLightningPassesOverTheSquadmate(GameTestHelper helper) {
		ServerPlayer thor = thor(helper, 1.5, 1.5);
		ServerPlayer mate = at(helper, 1.5, 3.5);
		Squad squad = squad(helper, thor, mate);
		Husk husk = husk(helper, 1.5, 6.5);
		thor.lookAt(EntityAnchorArgument.Anchor.EYES, husk.getEyePosition());

		List<LivingEntity> targets = ThorPowers.chainLightningTargets(thor);
		helper.assertTrue(targets.contains(husk), "the Husk in the cone is chained");
		helper.assertFalse(targets.contains(mate), "the squadmate standing in the cone is not");
		float before = husk.getHealth();
		ThorPowers.chainLightningCast(thor);
		helper.assertTrue(husk.getHealth() < before, "and the Husk really is hit");
		helper.assertTrue(ThorVisuals.fx(thor).anim() == ThorFx.ANIM_CHAIN, "the cast animation is published");
		disband(helper, squad);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void lightningStrikeGoesStraightPastASquadmateOntoTheEnemy(GameTestHelper helper) {
		ServerPlayer thor = thor(helper, 1.5, 1.5);
		ServerPlayer mate = at(helper, 1.5, 3.5);
		Squad squad = squad(helper, thor, mate);
		Husk husk = husk(helper, 1.5, 7.5);
		thor.lookAt(EntityAnchorArgument.Anchor.EYES, husk.getEyePosition());
		float before = husk.getHealth();

		ThorPowers.lightningStrike(thor);

		helper.assertTrue(husk.getHealth() < before, "the bolt ignores the squadmate in the way and lands on the Husk");
		ThorFx fx = ThorVisuals.fx(thor);
		helper.assertTrue(fx.anim() == ThorFx.ANIM_STRIKE, "the strike animation is published");
		helper.assertTrue(new Vec3(fx.x(), fx.y(), fx.z()).distanceTo(husk.position()) < 1.0, "at the Husk");
		disband(helper, squad);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void godOfThundersWrathBlastSparesTheSquad(GameTestHelper helper) {
		ServerPlayer thor = thor(helper, 1.5, 1.5);
		ServerPlayer mate = at(helper, 4.5, 4.5);
		Squad squad = squad(helper, thor, mate);
		Husk husk = husk(helper, 3.5, 5.5);
		List<LivingEntity> hit = ThorPowers.wrathTargets(helper.getLevel(), thor, mate.position());
		helper.assertTrue(hit.contains(husk), "the Husk inside the blast is hit");
		helper.assertFalse(hit.contains(mate), "the squadmate at the centre of it is not");
		helper.assertFalse(hit.contains(thor), "nor is Thor");
		disband(helper, squad);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void thorsArmourFormsPieceByPiece(GameTestHelper helper) {
		ServerPlayer thor = thor(helper, 1.5, 1.5);
		ThorArmor.toggle(thor);
		helper.assertTrue(thor.getItemBySlot(EquipmentSlot.FEET).is(ThorArmorItems.BOOTS)
				&& thor.getItemBySlot(EquipmentSlot.CHEST).is(ThorArmorItems.CHESTPLATE),
				"every piece is worn (and protecting) from the first tick");
		ThorFx fx = thor.getAttachedOrElse(ModAttachments.THOR_FX, ThorFx.EMPTY);
		helper.assertTrue(fx.suitDir() == ThorFx.SUIT_UP, "the suit clock is published for every viewer");
		// v0.17.7: the whole suit again, piece by piece -- boots, greaves, chestplate, helmet -- then the cape, then the last bolt
		int up = ThorFx.SUIT_UP;
		int[] order = { ThorArmor.PART_BOOTS, ThorArmor.PART_GREAVES, ThorArmor.PART_CHEST, ThorArmor.PART_HELMET, ThorArmor.PART_CAPE };
		helper.assertTrue(ThorArmor.partProgress(up, ThorArmor.PART_BOOTS, 4) == 0f, "nothing is on under the first bolt");
		for (int i = 0; i < order.length; i++) {
			float mid = (ThorArmor.partStart(order[i]) + ThorArmor.partEnd(order[i])) / 2f;
			helper.assertTrue(ThorArmor.partProgress(up, order[i], mid) > 0f && ThorArmor.partProgress(up, order[i], mid) < 1f,
					"part " + i + " is arriving half way through its window");
			for (int j = 0; j < i; j++) {
				helper.assertTrue(ThorArmor.partProgress(up, order[j], mid) == 1f, "part " + j + " is on before part " + i);
			}
			if (i + 1 < order.length) {
				helper.assertTrue(ThorArmor.partProgress(up, order[i + 1], mid) == 0f, "part " + (i + 1) + " waits for part " + i);
			}
		}
		// the helmet (drawn on the chestplate's pass) only starts once the chestplate has locked on
		helper.assertTrue(ThorArmor.partStart(ThorArmor.PART_HELMET) >= ThorArmor.partEnd(ThorArmor.PART_CHEST), "helmet after the chest");
		helper.assertTrue(ThorArmor.partEnd(ThorArmor.PART_CAPE) < ThorArmor.FINAL_BOLT_TICK && ThorArmor.FINAL_BOLT_TICK < ThorArmor.SUIT_UP_TICKS,
				"the last bolt lands once everything is on, before the suit-up ends");
		helper.assertTrue(ThorArmor.partProgress(up, ThorArmor.PART_CAPE, ThorArmor.SUIT_UP_TICKS) == 1f, "all on at the end");
		// H again: the build backwards -- cape, helmet, chestplate, greaves, boots -- all gone when the pieces are removed
		int down = ThorFx.SUIT_DOWN;
		for (int i = order.length - 1; i > 0; i--) {
			float mid = ThorArmor.partScatterStart(order[i]) + ThorArmor.partScatterTicks(order[i]) / 2f;
			helper.assertTrue(ThorArmor.partProgress(down, order[i], mid) < 1f && ThorArmor.partProgress(down, order[i - 1], mid) == 1f,
					"part " + i + " comes off before part " + (i - 1));
		}
		for (int part : order) {
			helper.assertTrue(ThorArmor.partProgress(down, part, ThorArmor.SUIT_DOWN_TICKS) == 0f, "and everything is gone when the pieces go");
		}
		helper.succeed();
	}
}
