// v0.17.3 Spine Whip: comments, guide row, HUD comment. CRLF-safe. Run from the repo root.
const { edit } = require('./edit_lib.js');
edit('src/main/java/com/projecthero/mod/undead/UndeadAbilityManager.java', [
	[`				// v0.17.3 kit: Shift+V is the Rib Cage Guard (up while held, at most 4 s; a tap leaves it up the whole 4 s)
				// v0.17.3 (user: "a new combat V move"): tap V is the Rib Shotgun now and Reform moved to Shift + hold V (a second,
				// so it is never set off by accident). Which of the three a press is, is settled by how long it is held, and each
				// is decided once: a plain V let go before BONE_SWORD_TAP fires the ribs (past it the katana struggle owns the
				// press); a Shift+V let go before REFORM_TAP lashes the Spine Whip (it replaced the Rib Cage Guard), past it he gathers himself and at REFORM_HOLD he re-forms; let go in between and nothing happens. Whether
				// the press was shifted is read on the press only, so letting go of Shift mid-hold changes nothing.
`, `				// v0.17.3 (user: "a new combat V move"): tap V is the Rib Shotgun now and Reform moved to Shift + hold V (a second,
				// so it is never set off by accident); tap Shift+V is the Spine Whip (it replaced the Rib Cage Guard). Which of the
				// four a press is, is settled by how long it is held, and each is decided once: a plain V let go before
				// BONE_SWORD_TAP fires the ribs (past it the katana struggle owns the press); a Shift+V let go before REFORM_TAP
				// lashes the Spine Whip, past it he gathers himself and at REFORM_HOLD he re-forms; let go in between and nothing
				// happens. Whether the press was shifted is read on the press only, so letting go of Shift mid-hold changes nothing.
`],
]);
edit('src/main/java/com/projecthero/mod/hero/guide/HeroPackGuide.java', [
	['{ "Shift+V", "rib_cage_guard" }', '{ "Shift+V", "spine_whip" }'],
]);
edit('src/client/java/com/projecthero/mod/client/undead/UndeadSwordFx.java', [
	['(Vortex Bullet, Unluck Meteor, Rib Cage Guard, the shoulder sword)', '(Vortex Bullet, Unluck Meteor, Rib Shotgun, Spine Whip, the shoulder sword)'],
]);
edit('src/client/java/com/projecthero/mod/client/undead/UndeadHud.java', [
	['// v0.17.3: V\'s fourth input -- Shift + hold V, Reform (tap V is the Rib Shotgun now)', '// v0.17.3: V\'s fourth input -- Shift + hold V, Reform (tap V is the Rib Shotgun, tap Shift+V the Spine Whip)'],
]);
