package com.projecthero.mod.gametest;

import com.projecthero.mod.syndicate.KingpinCaneSwing;
import com.projecthero.mod.syndicate.SyndicateEntityTypes;
import com.projecthero.mod.syndicate.SyndicateItems;
import com.projecthero.mod.syndicate.entity.KingpinEntity;
import com.projecthero.mod.syndicate.item.KingpinCaneItem;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** v0.14.31: the Kingpin's Cane strike chain and keyframes, the Kingpin wielding it, and its unchanged balance. */
public class KingpinCaneV01431GameTests implements FabricGameTest {
	@GameTest(template = EMPTY_STRUCTURE)
	public void caneStrikesChainInOrderAndResetAfterAPause(GameTestHelper helper) {
		helper.assertTrue(KingpinCaneSwing.nextStyle(-1, -1, 100) == KingpinCaneSwing.OVERHEAD, "the first swing is the overhead strike");
		int s = KingpinCaneSwing.OVERHEAD;
		long t = 100;
		s = KingpinCaneSwing.nextStyle(s, t, t + 18);
		helper.assertTrue(s == KingpinCaneSwing.SWIPE, "then the side swipe, got " + s);
		t += 18;
		s = KingpinCaneSwing.nextStyle(s, t, t + 18);
		helper.assertTrue(s == KingpinCaneSwing.THRUST, "then the thrust, got " + s);
		t += 18;
		s = KingpinCaneSwing.nextStyle(s, t, t + 24);
		helper.assertTrue(s == KingpinCaneSwing.OVERHEAD, "and round to the overhead strike again, got " + s);
		helper.assertTrue(KingpinCaneSwing.nextStyle(KingpinCaneSwing.OVERHEAD, 100, 100 + KingpinCaneSwing.RESET_TICKS + 1)
				== KingpinCaneSwing.OVERHEAD, "a pause starts the chain over");
		helper.assertTrue(KingpinCaneSwing.nextStyle(KingpinCaneSwing.SWIPE, 200, 100) == KingpinCaneSwing.OVERHEAD,
				"a clock that went backwards (rejoin) starts over");
		// the Kingpin swings every 24 ticks (16 enraged) -- inside the window, so he cycles through all three
		helper.assertTrue(KingpinCaneSwing.RESET_TICKS > 24, "the Kingpin's cane rhythm stays inside the chain window");
		helper.assertTrue(!KingpinCaneSwing.isNewStrike(100, 102), "a swing call mid-strike (held mining) is the same strike");
		helper.assertTrue(KingpinCaneSwing.isNewStrike(100, 100 + KingpinCaneSwing.MIN_GAP_TICKS), "a later swing is a new strike");
		helper.assertTrue(KingpinCaneSwing.isNewStrike(-1, 5), "the first swing is a new strike");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void caneKeyframesStartAndEndAtRest(GameTestHelper helper) {
		for (int style = 0; style < KingpinCaneSwing.STYLES; style++) {
			for (float[][] frames : new float[][][] { KingpinCaneSwing.body(style), KingpinCaneSwing.firstPerson(style) }) {
				float[] last = frames[frames.length - 1];
				helper.assertTrue(last[0] == KingpinCaneSwing.SWING_TICKS, "style " + style + " ends at the swing length");
				for (int k = 1; k < last.length; k++) {
					helper.assertTrue(last[k] == 0f, "style " + style + " ends at rest (channel " + k + ")");
				}
				for (int i = 1; i < frames.length; i++) {
					helper.assertTrue(frames[i][0] > frames[i - 1][0], "style " + style + " keyframes run forward in time");
				}
				helper.assertTrue(KingpinCaneSwing.sample(frames, KingpinCaneSwing.SWING_TICKS + 0.5f) == null, "nothing past the end");
				helper.assertTrue(KingpinCaneSwing.sample(frames, 4.5f) != null, "mid-swing samples");
			}
		}
		helper.assertTrue(KingpinCaneSwing.weight(0f) > 0f, "a strike shows from its first frame");
		helper.assertTrue(KingpinCaneSwing.weight(5f) == 1f, "and fully owns the arm mid-swing");
		helper.assertTrue(KingpinCaneSwing.weight(KingpinCaneSwing.SWING_TICKS) == 0f, "and hands it back at the end");
		// the overhead strike really goes overhead and comes down; the swipe crosses the body; the thrust points the cane out
		float[][] over = KingpinCaneSwing.body(KingpinCaneSwing.OVERHEAD);
		helper.assertTrue(over[1][1] < -2.5f && over[2][1] > -1.0f, "overhead: raised high, then chopped down");
		float[][] swipe = KingpinCaneSwing.body(KingpinCaneSwing.SWIPE);
		helper.assertTrue(swipe[1][2] > 0.5f && swipe[2][2] < -0.5f, "swipe: from out on the right across to the left");
		float[][] thrust = KingpinCaneSwing.body(KingpinCaneSwing.THRUST);
		helper.assertTrue(thrust[2][1] < -1.4f && thrust[2][12] == 1f, "thrust: arm straight out, cane along it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void kingpinWieldsTheCane(GameTestHelper helper) {
		KingpinEntity k = SyndicateEntityTypes.KINGPIN.create(helper.getLevel());
		k.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2, 2, 2)));
		k.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(k.blockPosition()), MobSpawnType.SPAWN_EGG, null);
		helper.assertTrue(k.getItemBySlot(EquipmentSlot.MAINHAND).is(SyndicateItems.KINGPIN_CANE), "the Kingpin holds his cane");
		helper.assertTrue(KingpinCaneSwing.wielding(k), "so his cane blows play the cane strikes");
		helper.assertTrue(!KingpinCaneSwing.isCane(new ItemStack(net.minecraft.world.item.Items.STICK)), "a stick is not the cane");
		k.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void caneBalanceIsUnchanged(GameTestHelper helper) {
		ItemStack cane = new ItemStack(SyndicateItems.KINGPIN_CANE);
		ItemAttributeModifiers mods = KingpinCaneItem.modifiers();
		double[] damage = { 0 };
		double[] speed = { 0 };
		mods.forEach(EquipmentSlot.MAINHAND, (attr, mod) -> {
			if (attr.is(Attributes.ATTACK_DAMAGE)) {
				damage[0] += mod.amount();
			} else if (attr.is(Attributes.ATTACK_SPEED)) {
				speed[0] += mod.amount();
			}
		});
		helper.assertTrue(damage[0] == 8.0, "+8 attack damage, got " + damage[0]);
		helper.assertTrue(Math.abs(speed[0] + 2.9) < 1e-6, "-2.9 attack speed, got " + speed[0]);
		helper.assertTrue(KingpinCaneItem.SHOT_DAMAGE == 7.0f && KingpinCaneItem.SHOT_COOLDOWN == 50, "the hidden barrel is unchanged");
		helper.assertTrue(cane.getMaxDamage() == 1200, "1,200 uses, got " + cane.getMaxDamage());
		helper.succeed();
	}
}
