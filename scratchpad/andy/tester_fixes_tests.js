// v0.17.3: Andy tester fixes -- gametests. Run from the repo root.
const fs = require('fs');
const G = 'src/gametest/java/com/projecthero/mod/gametest/AndyGameTests.java';
let t = fs.readFileSync(G, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
function rep(a, b) { if (!t.includes(a)) throw new Error('missing: ' + a.slice(0, 90)); t = t.replace(a, b); }

rep(`		Andy.cycleSpeed(p);
		helper.assertTrue(Andy.regenSpeed(p) == AndyConfig.SPEED_NORMAL, "and back to Normal (no Slow to pick)");`,
`		Andy.cycleSpeed(p);
		helper.assertTrue(Andy.regenOff(p), "then Off (tester: the option not to regenerate)");
		Andy.cycleSpeed(p);
		helper.assertTrue(!Andy.regenOff(p) && Andy.regenSpeed(p) == AndyConfig.SPEED_NORMAL, "and back to Normal (no Slow to pick)");`);
rep(`		helper.assertTrue(AndyConfig.CRIMSON_DAMAGE == 12f && AndyConfig.CRIMSON_COOLDOWN == 20, "Crimson Bullet: 12 damage, 1 s");
		helper.assertTrue(AndyConfig.PUNCH_DAMAGE == 20f && AndyConfig.PUNCH_COOLDOWN == 20, "Stretch Punch: 20 damage, 1 s");`,
`		helper.assertTrue(AndyConfig.CRIMSON_DAMAGE == 10f && AndyConfig.CRIMSON_COOLDOWN == 15, "Crimson Bullet: 10 damage, 0.75 s");
		helper.assertTrue(AndyConfig.PUNCH_DAMAGE == 14f && AndyConfig.PUNCH_COOLDOWN == 15, "Parts Bullet: Arm: 14 damage (tester: 20 is a bit much), 0.75 s");
		helper.assertTrue(AndyConfig.HEAD_BULLET_DAMAGE > AndyConfig.PUNCH_DAMAGE, "the head hits harder than the arm");`);

const tests = `
	// ---------------------------------------------------------------- v0.17.3 tester fixes

	/** Tester: "Alternation between left and right hands instead of just one when throwing arms or hands". */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shotsAlternateHands(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		long now = helper.getLevel().getGameTime();
		AndyAbilities.partsBullet(p);
		AndyState s = Andy.state(p);
		boolean firstLeft = s.eaten(AndyState.LEFT_ARM, now) > 0f;
		helper.assertTrue(firstLeft != (s.eaten(AndyState.RIGHT_ARM, now) > 0f), "one hand fired first");
		AndyAbilities.partsBullet(p);
		s = Andy.state(p);
		helper.assertTrue(s.eaten(AndyState.LEFT_ARM, now) > 0f && s.eaten(AndyState.RIGHT_ARM, now) > 0f, "the second shot came from the other hand");
		AndyAbilities.partsBullet(p);
		s = Andy.state(p);
		int arm = firstLeft ? AndyState.LEFT_ARM : AndyState.RIGHT_ARM;
		helper.assertTrue(Math.abs(s.eaten(arm, now) - 2f * AndyConfig.PARTS_EAT) < 1.0e-3f, "and the third from the first again: " + s.eaten(arm, now));
		// with one arm gone, the other fires every time
		Andy.breakPart(p, AndyState.LEFT_ARM, AndyState.KIND_BURST);
		helper.assertTrue(Andy.shootingArm(p) == AndyState.RIGHT_ARM, "one arm left: always that one");
		helper.succeed();
	}

	/** Tester: "Make deadline do damage less consistently" -- rolls vary and some miss, about 4 on average. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void deadLineRollsVary(GameTestHelper helper) {
		net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(42L);
		float sum = 0f, min = 99f, max = 0f;
		int misses = 0, n = 4000;
		for (int i = 0; i < n; i++) {
			float d = AndyAbilities.deadLineRoll(r);
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

	/** Tester: "Add the option to choose not to regenerate": Off stops regrowth until it is turned back on. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 600)
	public void regenOffKeepsPartsGone(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		Andy.setRegenOff(p, true);
		Andy.breakPart(p, AndyState.LEFT_ARM, AndyState.KIND_BURST);
		int[] t = { 0 };
		helper.onEachTick(() -> {
			Andy.tick(p);
			t[0]++;
			if (t[0] == 300) {
				helper.assertTrue(Andy.missing(p, AndyState.LEFT_ARM), "Off: the arm is still gone after 15 s");
				helper.assertTrue(Andy.state(p).regrowStart(AndyState.LEFT_ARM) == 0L, "and nothing is growing");
				Andy.cycleSpeed(p); // Off -> Normal
			}
			if (t[0] > 300 && !Andy.missing(p, AndyState.LEFT_ARM)) {
				helper.succeed();
			}
		});
	}

	/** Tester: "Make regeneration slow to impossible when completely starving, leaving only the head until you eat". */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 800)
	public void anEmptyStomachRegrowsNothingButTheHead(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		p.getFoodData().setFoodLevel(0);
		p.getFoodData().setSaturation(0f);
		for (int i = AndyState.BODY; i < AndyState.PARTS; i++) {
			Andy.breakPart(p, i, AndyState.KIND_BURST);
		}
		Andy.breakPart(p, AndyState.HEAD, AndyState.KIND_BURST);
		int[] t = { 0 };
		helper.onEachTick(() -> {
			Andy.tick(p);
			t[0]++;
			if (t[0] == 400) {
				AndyState s = Andy.state(p);
				for (int i = AndyState.BODY; i < AndyState.PARTS; i++) {
					helper.assertTrue(s.broken(i) && s.regrowStart(i) == 0L, "part " + i + " stays gone on an empty stomach");
				}
				p.getFoodData().setFoodLevel(20); // he eats
				p.getFoodData().setSaturation(5f);
			}
			if (t[0] > 400 && !Andy.state(p).broken(AndyState.BODY)) {
				helper.succeed();
			}
		});
	}

	/** Tester: "Fast regen doesnt feel fast enough when being hurt": Burst brings health back quickly, Normal slowly, Off not at all. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 300)
	public void burstHealsWoundsQuickly(GameTestHelper helper) {
		ServerPlayer burst = andy(helper);
		ServerPlayer normal = andy(helper);
		ServerPlayer off = andy(helper);
		Andy.setSpeed(burst, AndyConfig.SPEED_BURST);
		Andy.setRegenOff(off, true);
		for (ServerPlayer p : new ServerPlayer[] { burst, normal, off }) {
			p.setHealth(6f);
			p.getFoodData().setFoodLevel(20);
			p.getFoodData().setSaturation(0f); // no vanilla saturation healing in the way
			edit(p, s -> s.lastHitAt = helper.getLevel().getGameTime());
		}
		int[] t = { 0 };
		helper.onEachTick(() -> {
			t[0]++;
			for (ServerPlayer p : new ServerPlayer[] { burst, normal, off }) {
				Andy.tick(p);
			}
			if (t[0] == 100) {
				helper.assertTrue(burst.getHealth() >= 15f, "Burst: about 3 health a second after a short wait: " + burst.getHealth());
				helper.assertTrue(normal.getHealth() < burst.getHealth() - 5f, "Normal heals much slower: " + normal.getHealth());
				helper.assertTrue(off.getHealth() <= 6.5f, "Off heals nothing extra: " + off.getHealth());
				helper.succeed();
			}
		});
	}

	/** Tester: "A way people can naturally pick up the head": right-click the thrown head and it goes back on. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theThrownHeadCanBePickedUp(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		AndyAbilities.headBullet(p);
		helper.assertTrue(Andy.missing(p, AndyState.HEAD), "the head is thrown");
		AndyPartEntity head = helper.getLevel().getEntitiesOfClass(AndyPartEntity.class, new AABB(p.blockPosition()).inflate(8),
				e -> e.ownerIs(p) && e.bodyPart() == AndyState.HEAD).stream().findFirst().orElse(null);
		helper.assertTrue(head != null, "the head entity is out");
		helper.assertTrue(head.isPickable(), "and can be clicked");
		ServerPlayer friend = mock(helper);
		head.interact(friend, InteractionHand.MAIN_HAND);
		helper.assertTrue(!Andy.missing(p, AndyState.HEAD), "a friend right-clicking it puts it straight back on");
		helper.assertTrue(head.isRemoved(), "and the thrown one is gone");
		helper.succeed();
	}

	/** Tester: "A way people can naturally pick up the head": an Andy who is only a head can be picked up and carried. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aHeadOnlyAndyCanBeCarried(GameTestHelper helper) {
		ServerPlayer p = andy(helper);
		ServerPlayer friend = mock(helper);
		helper.assertTrue(com.projecthero.mod.andy.AndyCarry.tryPickUp(friend, InteractionHand.MAIN_HAND, p) == net.minecraft.world.InteractionResult.PASS,
				"a whole Andy can not be picked up");
		for (int i = AndyState.BODY; i < AndyState.PARTS; i++) {
			Andy.breakPart(p, i, AndyState.KIND_BURST);
		}
		com.projecthero.mod.andy.AndyCarry.tryPickUp(friend, InteractionHand.MAIN_HAND, p);
		helper.assertTrue(p.getVehicle() == friend, "only a head: the friend picks him up");
		Vec3 seat = com.projecthero.mod.andy.AndyCarry.seat(friend);
		helper.assertTrue(seat.y < 1.2 && seat.horizontalDistance() > 0.3, "carried in front of the chest, not on the head: " + seat);
		Andy.restore(p);
		Andy.tick(p);
		helper.assertTrue(p.getVehicle() == null, "with his body back he climbs down");
		helper.succeed();
	}

	/** Tester: the clones -- the bones self has its own skeleton texture (the blank white one is gone). */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBoneCloneHasASkeletonTexture(GameTestHelper helper) {
		helper.assertTrue(AndyGameTests.class.getResource("/assets/projecthero/textures/entity/andy/bone_clone.png") != null, "bone_clone.png ships");
		helper.succeed();
	}

`;
const anchor = '\t// ---------------------------------------------------------------- R: finger shots eat the arm, the stretch punch';
if (!t.includes(anchor)) throw new Error('anchor');
t = t.replace(anchor, tests.slice(1) + anchor);
fs.writeFileSync(G, crlf ? t.replace(/\n/g, '\r\n') : t);
console.log('ok');
