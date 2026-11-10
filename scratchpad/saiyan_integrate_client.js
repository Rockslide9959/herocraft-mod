// v0.17.3 Saiyan wave 1: hook the client side into the shared client code (run from the repo root).
const fs = require('fs');
function edit(f, pairs) {
  let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
  for (const [a, b] of pairs) { if (!t.includes(a)) throw new Error(f + ' missing: ' + a.slice(0, 100)); t = t.replace(a, b); }
  fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
}
const C = 'src/client/java/com/projecthero/mod/client/';
edit(C + 'ProjectHeroModClient.java', [
  ['		com.projecthero.mod.client.andy.AndyClient.initialize(); // v0.17.3\n',
   '		com.projecthero.mod.client.andy.AndyClient.initialize(); // v0.17.3\n		com.projecthero.mod.client.saiyan.SaiyanClient.initialize(); // v0.17.3: HUD, form wheel, Ki charge key\n'],
  [`			} else if (client.player != null && !Screen.hasShiftDown() && com.projecthero.mod.client.blackpanther.BlackPantherClient.ownsH(client.player)) {`,
   `			} else if (client.player != null && com.projecthero.mod.client.saiyan.SaiyanClient.ownsH(client.player)) {
				// v0.17.3: H as a Saiyan -- the form wheel (hold H, click a form; Shift+H still opens the power wheel).
				com.projecthero.mod.client.saiyan.SaiyanClient.pressH();
			} else if (client.player != null && !Screen.hasShiftDown() && com.projecthero.mod.client.blackpanther.BlackPantherClient.ownsH(client.player)) {`],
]);
edit(C + 'gui/PowerInfoScreen.java', [[
`		if (com.projecthero.mod.andy.Andy.hasPower(mc.player)) {
			return com.projecthero.mod.hero.guide.HeroPackGuide.andyChapter(); // v0.17.3
		}`,
`		if (com.projecthero.mod.andy.Andy.hasPower(mc.player)) {
			return com.projecthero.mod.hero.guide.HeroPackGuide.andyChapter(); // v0.17.3
		}
		if (com.projecthero.mod.saiyan.Saiyan.hasPower(mc.player)) {
			return com.projecthero.mod.hero.guide.HeroPackGuide.saiyanChapter(); // v0.17.3
		}`]]);
edit(C + 'gui/SquadScreen.java', [['				case "andy": return 0xFFC0101A; // v0.17.3\n',
  '				case "andy": return 0xFFC0101A; // v0.17.3\n				case "saiyan": return 0xFFFFD84A; // v0.17.3: Super Saiyan gold\n']]);
edit(C + 'flight/DirectionalFlight.java', [[
`		// v0.15.13: Nova flight -- 20 b/s, 40 sprinting
		if (com.projecthero.mod.nova.Nova.isFlying(player)) {`,
`		// v0.17.3: the Saiyan's Shift+X flight -- Kryptonian handling, 18 b/s cruise, 36 sprinting, x the form's speed (55 at most)
		if (com.projecthero.mod.saiyan.Saiyan.isFlying(player)) {
			double mult = com.projecthero.mod.saiyan.Saiyan.form(player).speedMult();
			double bps = Math.min(com.projecthero.mod.saiyan.SaiyanConfig.FLIGHT_MAX_BPS, (sprint ? com.projecthero.mod.saiyan.SaiyanConfig.FLIGHT_SPRINT_BPS
					: com.projecthero.mod.saiyan.SaiyanConfig.FLIGHT_CRUISE_BPS) * mult);
			return DirectionalFlightModel.fixedCruise(DirectionalFlightModel.kryptonian(sprint, false), bps / 20.0);
		}
		// v0.15.13: Nova flight -- 20 b/s, 40 sprinting
		if (com.projecthero.mod.nova.Nova.isFlying(player)) {`]]);
edit(C + 'flight/FlightFeel.java', [[
`		if (com.projecthero.mod.nova.Nova.isFlying(player)) {
			return NOVA;
		}`,
`		if (com.projecthero.mod.nova.Nova.isFlying(player)) {
			return NOVA;
		}
		if (com.projecthero.mod.saiyan.Saiyan.isFlying(player)) {
			return KRYPTONIAN; // v0.17.3: the Saiyan banks and leans like the Kryptonian
		}`]]);
edit(C + 'FlightPoseHelper.java', [[
`				// v0.17.1: the Falcon's jet flight (flat out while the jets burn)
				|| com.projecthero.mod.falcon.Falcon.flying(player);`,
`				// v0.17.1: the Falcon's jet flight (flat out while the jets burn)
				|| com.projecthero.mod.falcon.Falcon.flying(player)
				// v0.17.3: the Saiyan's Shift+X flight
				|| com.projecthero.mod.saiyan.Saiyan.isFlying(player);`]]);
console.log('ok');
