// v0.17.3 Undead kit: fit the long-reach tests inside the 8x8x8 gametest box (barrier walls round it).
const fs = require('fs');
const f = 'src/gametest/java/com/projecthero/mod/gametest/UndeadKitGameTests.java';
let t = fs.readFileSync(f, 'utf8');
const crlf = t.includes('\r\n');
t = t.replace(/\r\n/g, '\n');
function rep(a, b) {
	if (!t.includes(a)) throw new Error('not found: ' + a.slice(0, 90));
	t = t.replace(a, b);
}
// a damage report for the messages
rep(`	private static void press(ServerPlayer p, AbilitySlot slot, boolean shift, boolean down) {`,
	`	/** "dealt X (by Y)" for a zombie that started on 200. */
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

	private static void press(ServerPlayer p, AbilitySlot slot, boolean shift, boolean down) {`);
// vortex: the line inside the box; the drill buries itself in the far wall and bursts there
rep(`		ServerPlayer p = undead(helper); // facing +z
		int arm = Undead.mainArm(p);
		Zombie[] line = { zombie(helper, new Vec3(2.5, 2.0, 5.5)), zombie(helper, new Vec3(2.5, 2.0, 8.0)), zombie(helper, new Vec3(2.5, 2.0, 10.5)),
				zombie(helper, new Vec3(2.5, 2.0, 13.0)) };
		Zombie aside = zombie(helper, new Vec3(6.5, 2.0, 8.0)); // 4 blocks off the line`,
	`		ServerPlayer p = undead(helper);
		atStart(helper, p);
		int arm = Undead.mainArm(p);
		Zombie[] line = { zombie(helper, new Vec3(1.3, 2.0, 2.6)), zombie(helper, new Vec3(1.3, 2.0, 4.1)), zombie(helper, new Vec3(1.3, 2.0, 5.6)) };
		Zombie aside = zombie(helper, new Vec3(6.6, 2.0, 1.2)); // 5 blocks off the line, far from where it stops`);
rep(`					helper.assertTrue(dealt >= UndeadKitConfig.VORTEX_DAMAGE - 3f, "zombie " + i + " on the line is bored through: " + dealt);
				}
				helper.assertTrue(aside.getHealth() == 200f, "one off the line is untouched");`,
	`					helper.assertTrue(dealt >= UndeadKitConfig.VORTEX_DAMAGE - 3f, "zombie " + i + " on the line is bored through: " + hurt(line[i]));
				}
				helper.assertTrue(aside.getHealth() == 200f, "one off the line is untouched: " + hurt(aside));`);
// meteor: he stands at the near edge, the far zombie well out beyond the far wall
rep(`		ServerPlayer p = undead(helper);
		// a stone floor ahead to look at
		for (int x = 0; x <= 5; x++) {
			for (int z = 3; z <= 9; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}`,
	`		ServerPlayer p = undead(helper);
		atStart(helper, p);
		// a stone floor ahead to look at
		for (int x = 0; x <= 5; x++) {
			for (int z = 1; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}`);
rep(`		Zombie near = zombieAt(helper, target.add(1.0, 0, 0));
		Zombie mid = zombieAt(helper, target.add(0, 0, 7.0));
		Zombie far = zombieAt(helper, target.add(13.0, 0, 0));`,
	`		Zombie near = zombieAt(helper, target.add(1.0, 0.1, 0));
		Zombie mid = zombieAt(helper, target.add(5.0, 0.1, 3.0)); // ~6 out, still in the box
		Zombie far = zombieAt(helper, target.add(0, 0.1, 14.0)); // beyond the far wall, outside the 10-block blast`);
rep(`			helper.assertTrue(dn >= 28f, "the centre takes the brunt: " + dn);
			helper.assertTrue(dm > 0f && dm < dn, "less further out: " + dm);
			helper.assertTrue(far.getHealth() == 200f, "outside the 10-block blast: untouched");`,
	`			helper.assertTrue(dn >= 28f, "the centre takes the brunt: " + hurt(near));
			helper.assertTrue(dm > 0f && dm < dn, "less further out: " + hurt(mid));
			helper.assertTrue(far.getHealth() == 200f, "outside the 10-block blast: untouched: " + hurt(far));`);
rep(`			for (int x = 0; x <= 5; x++) {
				for (int z = 3; z <= 9; z++) {
					helper.assertBlockPresent(Blocks.STONE, new BlockPos(x, 1, z));`,
	`			for (int x = 0; x <= 5; x++) {
				for (int z = 1; z <= 6; z++) {
					helper.assertBlockPresent(Blocks.STONE, new BlockPos(x, 1, z));`);
// katana: inside the box
rep(`		ServerPlayer p = swordsman(helper); // facing +z
		Zombie crescentTarget = zombie(helper, new Vec3(2.5, 2.0, 9.5)); // 7 out: only the flying crescent reaches it`,
	`		ServerPlayer p = swordsman(helper);
		atStart(helper, p);
		Zombie crescentTarget = zombie(helper, new Vec3(1.5, 2.0, 7.0)); // 6.5 out: only the flying crescent reaches it`);
rep(`					helper.assertTrue(200f - crescentTarget.getHealth() >= 10f, "the crescent flies 7 blocks and cuts: " + (200f - crescentTarget.getHealth()));
					crescentTarget.discard();
					later[0] = zombie(helper, new Vec3(2.5, 2.0, 15.5)); // on the line, 13 out
					later[1] = zombie(helper, new Vec3(5.5, 2.0, 12.0)); // 3 off the line: the great one is wide`,
	`					helper.assertTrue(200f - crescentTarget.getHealth() >= 10f, "the crescent flies 6.5 blocks and cuts: " + hurt(crescentTarget));
					crescentTarget.discard();
					later[0] = zombie(helper, new Vec3(1.5, 2.0, 7.3)); // on the line, 7 out
					later[1] = zombie(helper, new Vec3(5.0, 2.0, 6.0)); // 3.5 off the line: the great one is wide`);
rep(`					helper.assertTrue(200f - later[0].getHealth() >= 18f, "Great Deep-Crimson Crescent Moon: " + (200f - later[0].getHealth()));
					helper.assertTrue(later[1].getHealth() < 200f, "and wide");`,
	`					helper.assertTrue(200f - later[0].getHealth() >= 18f, "Great Deep-Crimson Crescent Moon: " + hurt(later[0]));
					helper.assertTrue(later[1].getHealth() < 200f, "and wide: " + hurt(later[1]));`);
rep(`					p.teleportTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)).x, helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)).y,
							helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)).z);
					later[2] = zombie(helper, new Vec3(2.5, 2.0, 5.5));`,
	`					atStart(helper, p);
					later[2] = zombie(helper, new Vec3(1.5, 2.0, 3.5));`);
rep(`					helper.assertTrue(later[2].getHealth() == 200f, "passed through, not cut yet");`,
	`					helper.assertTrue(later[2].getHealth() == 200f, "passed through, not cut yet: " + hurt(later[2]));`);
rep(`					helper.assertTrue(p.position().distanceTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5))) > UndeadKitConfig.K_FLASH_DISTANCE - 1.0,
							"he went 8 blocks: " + p.position().distanceTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5))));`,
	`					double went = p.position().distanceTo(helper.absoluteVec(new Vec3(1.5, 2.0, 0.5)));
					helper.assertTrue(went > 5.5 && went <= UndeadKitConfig.K_FLASH_DISTANCE + 0.01, "he went to the wall (8 blocks at most): " + went);`);
rep(`					helper.assertTrue(200f - later[2].getHealth() >= 12f, "the cut opens: " + (200f - later[2].getHealth()));`,
	`					helper.assertTrue(200f - later[2].getHealth() >= 12f, "the cut opens: " + hurt(later[2]));`);
rep(`					helper.assertTrue(dealt >= UndeadKitConfig.K_WHIRL_DAMAGE * 2 - 4f, "Blood Whirl hits behind him, twice: " + dealt);`,
	`					helper.assertTrue(dealt >= UndeadKitConfig.K_WHIRL_DAMAGE * 2 - 4f, "Blood Whirl hits behind him, twice: " + hurt(later[3]));`);
if (crlf) t = t.replace(/\n/g, '\r\n');
fs.writeFileSync(f, t);
console.log('ok');
