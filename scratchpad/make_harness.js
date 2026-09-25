// Builds the v0.12.32 dev harness from the saved v0.12.31 one: Thor armour shots first, then the same Titan tour.
const fs = require('fs');
let s = fs.readFileSync('scratchpad/TitanDebugHarness.java.txt', 'utf8');
function rep(a, b) { if (!s.includes(a)) throw new Error('missing ' + a.slice(0, 60)); s = s.replace(a, () => b); }
rep(`				TitanShifter.grant(p);
				TitanShifter.transform(p);
			});`, `				com.projecthero.mod.worthiness.Worthiness.setScore(p, com.projecthero.mod.worthiness.Worthiness.TEST_WORTHY_SCORE);
				com.projecthero.mod.power.ThorPassives.bind(p, java.util.UUID.randomUUID());
			});`);
s = s.replace(/at\((\d+), /g, 'at(O + $1, ');
rep(`		int[] transformShots = { 4, 22, 40, 58, 70 };
		for (int t : transformShots) {
			at(t, () -> shot(mc, "01_transform_" + (inWorldTicks - t0)));
		}`, `		// --- Thor's Armour: H, lightning, then a look at the suit from the front and back ---
		at(2, () -> server(mc, com.projecthero.mod.thorarmor.ThorArmor::toggle));
		at(12, () -> shot(mc, "00_thor_bolt"));
		at(30, () -> shot(mc, "00_thor_back"));
		at(32, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		at(38, () -> shot(mc, "00_thor_front"));
		at(40, () -> server(mc, p -> { var it = p.drop(new net.minecraft.world.item.ItemStack(com.projecthero.mod.thorarmor.ThorArmorItems.CHESTPLATE), false); System.out.println("TITANDBG dropped piece removed=" + (it == null || it.isRemoved())); }));
		at(50, () -> server(mc, p -> {
			p.getCooldowns().removeCooldown(com.projecthero.mod.thorarmor.ThorArmorItems.CHESTPLATE);
			com.projecthero.mod.thorarmor.ThorArmor.toggle(p);
			System.out.println("TITANDBG armour after second H: " + com.projecthero.mod.thorarmor.ThorArmor.hasAnyPiece(p));
			com.projecthero.mod.power.ThorPassives.unbind(p);
			TitanShifter.grant(p);
			mc.execute(() -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			TitanShifter.transform(p);
		}));
		int[] transformShots = { 4, 22, 40, 58, 70 };
		for (int t : transformShots) {
			at(O + t, () -> shot(mc, "01_transform_" + (inWorldTicks - t0)));
		}`);
rep(`	private static boolean createdWorld;`, `	private static final int O = 60;
	private static boolean createdWorld;`);
rep(`			st.cooldownUntil = 0L;`, `			st.cooldownUntil = 0L;
			st.energy = 100f;`);
fs.writeFileSync('scratchpad/TitanDebugHarness.v01232.java.txt', s);
console.log('ok');
