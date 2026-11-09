// v0.17.3: Andy -- Leg Launch / Head Launch dash straight where he aims (no arc); passive auto-eat. Run from the repo root.
const fs = require('fs');
function edit(f, pairs) {
  let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
  for (const [a, b] of pairs) { if (!t.includes(a)) throw new Error(f + ' missing: ' + a.slice(0, 90)); t = t.replace(a, b); }
  fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
}
const A = 'src/main/java/com/projecthero/mod/andy/';
edit(A + 'AndyConfig.java', [
  ['public static final double LAUNCH_GRAVITY = 0.08;',
   'public static final double LAUNCH_GRAVITY = 0.08;\n\t/**\n\t * v0.17.3 (user: "make leg launch and the head launch dash the player where the player aims ... directly where the player is\n\t * aiming like with the other dash abilities"): both fly in a straight line along the look, no arc, this far (blocks).\n\t */\n\tpublic static final double LEG_LAUNCH_RANGE = 20.0;\n\tpublic static final double HEAD_LAUNCH_RANGE = 45.0;\n\tpublic static final double LEG_LAUNCH_DASH_SPEED = 2.0;\n\tpublic static final double HEAD_LAUNCH_DASH_SPEED = 2.5;\n\n\t/** v0.17.3 (user: passive auto-eat): he eats from his inventory once his food falls below this (food + saturation, points). */\n\tpublic static final int AUTO_EAT_BELOW = 14;\n\tpublic static final int AUTO_EAT_EVERY = 30;'],
]);
edit(A + 'AndyLaunch.java', [
  ['\t\tVec3 dir = flatLook(p);\n\t\t// the legs stay where he stood', '\t\tVec3 dir = aim(p);\n\t\t// the legs stay where he stood'],
  ['\t\tdouble lift = Math.max(0.25, Math.min(0.9, AndyConfig.LEG_LAUNCH_LIFT + p.getLookAngle().y * 0.5));\n\t\tstart(p, false, dir, AndyConfig.LEG_LAUNCH_SPEED, lift);',
   '\t\tstart(p, false, dir, AndyConfig.LEG_LAUNCH_DASH_SPEED, 0.0);'],
  ['\t\tVec3 dir = flatLook(p);\n\t\tboolean legless', '\t\tVec3 dir = aim(p);\n\t\tboolean legless'],
  ['\t\tdouble lift = Math.max(0.5, Math.min(1.6, AndyConfig.HEAD_LAUNCH_LIFT + p.getLookAngle().y * 0.8));\n\t\tstart(p, true, dir, AndyConfig.HEAD_LAUNCH_SPEED, lift);',
   '\t\tstart(p, true, dir, AndyConfig.HEAD_LAUNCH_DASH_SPEED, 0.0);'],
  ['\tprivate static Vec3 flatLook(ServerPlayer p) {',
   '\t/** Straight where he aims (v0.17.3): the full look, up and down included. */\n\tprivate static Vec3 aim(ServerPlayer p) {\n\t\treturn p.getLookAngle().normalize();\n\t}\n\n\tprivate static Vec3 flatLook(ServerPlayer p) {'],
  ['\t\tfinal Set<Integer> hit = new HashSet<>();\n\t\tVec3 last;', '\t\tfinal Set<Integer> hit = new HashSet<>();\n\t\tfinal Vec3 from;\n\t\tVec3 last;'],
  ['\t\t\tthis.start = start;\n\t\t\tthis.last = at;', '\t\t\tthis.start = start;\n\t\t\tthis.from = at;\n\t\t\tthis.last = at;'],
  ['\t\tVec3 v = r.dir.scale(r.speed).add(0, r.lift - AndyConfig.LAUNCH_GRAVITY * age, 0);\n\t\tAbilityHelpers.launchSelf(p, v);',
   '\t\t// a straight dash along the aim: no lift, no gravity\n\t\tAbilityHelpers.launchSelf(p, r.dir.scale(r.speed));'],
  ['\t\tboolean stalled = r.still >= 2;\n\t\tif (!p.isAlive() || p.isSpectator() || age > max || (age >= 3 && (p.onGround() || p.isInWater())) || stalled || age < 0) {\n\t\t\tland(p, now);\n\t\t\treturn;\n\t\t}',
   '\t\tboolean stalled = r.still >= 2;\n\t\tdouble range = r.head ? AndyConfig.HEAD_LAUNCH_RANGE : AndyConfig.LEG_LAUNCH_RANGE;\n\t\tboolean far = pos.distanceToSqr(r.from) >= range * range;\n\t\t// aimed down into the ground it ends on landing; aimed flat or up it runs its range (or hits a wall)\n\t\tboolean grounded = age >= 2 && r.dir.y < -0.25 && p.onGround();\n\t\tif (!p.isAlive() || p.isSpectator() || age > max || far || grounded || (age >= 3 && p.isInWater()) || stalled || age < 0) {\n\t\t\tif (far) {\n\t\t\t\tAbilityHelpers.launchSelf(p, r.dir.scale(0.35)); // ease out instead of flying on\n\t\t\t}\n\t\t\tland(p, now);\n\t\t\treturn;\n\t\t}'],
]);
fs.writeFileSync(A + 'AndyAutoEat.java', `package com.projecthero.mod.andy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * v0.17.3 (user: "the undead power should have some form of auto feeding" -- passive): food is what his regrowth runs on, so he
 * eats on his own. Once his food (hunger bar + saturation) falls below {@link AndyConfig#AUTO_EAT_BELOW}, every
 * {@link AndyConfig#AUTO_EAT_EVERY} ticks he eats one piece of plain food from his inventory -- the least filling first, and only
 * if it won't be wasted. Never anything with an effect (golden apples, rotten flesh, spider eyes, pufferfish, suspicious stew,
 * chorus fruit, honey), never in creative.
 */
public final class AndyAutoEat {
	private static final Map<UUID, Long> NEXT = new ConcurrentHashMap<>();

	private AndyAutoEat() {
	}

	public static void clearSessionState() {
		NEXT.clear();
	}

	public static void forget(UUID id) {
		NEXT.remove(id);
	}

	/** Every tick for an Andy (from {@link Andy#tick}). */
	static void tick(ServerPlayer p) {
		if (p.getAbilities().instabuild || p.isSpectator() || !p.isAlive()) {
			return;
		}
		long now = p.level().getGameTime();
		Long next = NEXT.get(p.getUUID());
		if (next != null && now < next) {
			return;
		}
		var food = p.getFoodData();
		if (food.getFoodLevel() + food.getSaturationLevel() >= AndyConfig.AUTO_EAT_BELOW) {
			return;
		}
		int slot = pick(p, 20 - food.getFoodLevel());
		NEXT.put(p.getUUID(), now + AndyConfig.AUTO_EAT_EVERY);
		if (slot < 0) {
			return;
		}
		ItemStack stack = p.getInventory().getItem(slot);
		ItemStack left = stack.finishUsingItem(p.level(), p); // eats it: food, saturation, stats, the bowl back
		if (left != stack) {
			p.getInventory().setItem(slot, ItemStack.EMPTY);
			if (!left.isEmpty() && !p.getInventory().add(left)) {
				p.drop(left, false);
			}
		}
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.6f,
				0.9f + p.getRandom().nextFloat() * 0.2f);
	}

	/** The inventory slot of the least filling plain food that fits in {@code room} points (or the smallest if none fits), or -1. */
	static int pick(ServerPlayer p, int room) {
		int best = -1, bestNut = Integer.MAX_VALUE;
		boolean bestFits = false;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			FoodProperties f = s.get(DataComponents.FOOD);
			if (s.isEmpty() || f == null || !plain(s, f)) {
				continue;
			}
			boolean fits = f.nutrition() <= room;
			if (best < 0 || (fits && !bestFits) || (fits == bestFits && f.nutrition() < bestNut)) {
				best = i;
				bestNut = f.nutrition();
				bestFits = fits;
			}
		}
		return best;
	}

	private static boolean plain(ItemStack s, FoodProperties f) {
		if (!f.effects().isEmpty() || f.canAlwaysEat()) {
			return false;
		}
		return !s.is(Items.SUSPICIOUS_STEW) && !s.is(Items.CHORUS_FRUIT) && !s.is(Items.HONEY_BOTTLE) && !s.is(Items.ROTTEN_FLESH)
				&& !s.is(Items.SPIDER_EYE) && !s.is(Items.POISONOUS_POTATO) && !s.is(Items.PUFFERFISH);
	}
}
`);
edit(A + 'Andy.java', [
  ['\t\tLAST_TICK.clear();\n', '\t\tLAST_TICK.clear();\n\t\tAndyAutoEat.clearSessionState();\n'],
  ['\t\tLAST_TICK.remove(id);\n', '\t\tLAST_TICK.remove(id);\n\t\tAndyAutoEat.forget(id);\n'],
  ['\t\tAndyState n = null;\n\t\tboolean whole = false;', '\t\tAndyAutoEat.tick(player);\n\t\tAndyState n = null;\n\t\tboolean whole = false;'],
]);
// lang: add a line to the regen guide
const L = 'src/main/resources/assets/projecthero/lang/en_us.json';
let t = fs.readFileSync(L, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
const k = '"projecthero.guide.andy.regen.body": ';
const i = t.indexOf(k); const e = t.indexOf('\n', i);
let line = t.slice(i, e);
const end = line.endsWith('",') ? '",' : '"';
line = line.slice(0, line.length - end.length) + ' Your body feeds itself: when you get hungry you eat plain food from your inventory on your own, the least filling first (never golden apples or food with effects).' + end;
t = t.slice(0, i) + line + t.slice(e);
JSON.parse(t);
fs.writeFileSync(L, crlf ? t.replace(/\n/g, '\r\n') : t);
// gametests
const G = 'src/gametest/java/com/projecthero/mod/gametest/AndyGameTests.java';
edit(G, [['\t// ---------------------------------------------------------------- R: finger shots', `\t/** v0.17.3 (user: passive auto-eat): hungry, he eats the least filling plain food; never a golden apple. */
\t@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
\tpublic void heFeedsHimselfPlainFood(GameTestHelper helper) {
\t\tServerPlayer p = andy(helper);
\t\tp.getInventory().clearContent();
\t\tp.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 2));
\t\tp.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKED_BEEF, 2));
\t\tp.getInventory().setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 2));
\t\tp.getFoodData().setFoodLevel(6);
\t\tp.getFoodData().setSaturation(0f);
\t\tAndy.tick(p);
\t\thelper.assertTrue(p.getFoodData().getFoodLevel() > 6, "he ate: " + p.getFoodData().getFoodLevel());
\t\thelper.assertTrue(p.getInventory().getItem(2).getCount() == 1, "the bread (least filling) went first");
\t\thelper.assertTrue(p.getInventory().getItem(0).getCount() == 2, "the golden apples are never eaten");
\t\tint food = p.getFoodData().getFoodLevel();
\t\tAndy.tick(p);
\t\thelper.assertTrue(p.getFoodData().getFoodLevel() == food, "not again straight away");
\t\thelper.succeed();
\t}

\t// ---------------------------------------------------------------- R: finger shots`]]);
console.log('ok');
