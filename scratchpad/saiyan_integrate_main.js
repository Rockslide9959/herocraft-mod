// v0.17.3 Saiyan wave 1: hook the power into the shared registries (run from the repo root).
const fs = require('fs');
function edit(f, pairs) {
  let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
  for (const [a, b] of pairs) { if (!t.includes(a)) throw new Error(f + ' missing: ' + a.slice(0, 100)); t = t.replace(a, b); }
  fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
}
const M = 'src/main/java/com/projecthero/mod/';
edit(M + 'attachment/ModAttachments.java', [[
`	/** v0.16.5: Blue Beetle (Jaime Reyes) -- the power, the armour, Scarab Energy, Adaptation, timers and visible effects. */`,
`	/** v0.17.3: the Saiyan -- the power, the form, Ki, mastery, timers. */
	public static final AttachmentType<com.projecthero.mod.saiyan.data.SaiyanState> SAIYAN_STATE =
			AttachmentRegistry.create(ProjectHeroMod.id("saiyan_state"),
					builder -> builder.persistent(com.projecthero.mod.saiyan.data.SaiyanState.CODEC)
							.copyOnDeath()
							.initializer(com.projecthero.mod.saiyan.data.SaiyanState::new)
							.syncWith(ByteBufCodecs.fromCodec(com.projecthero.mod.saiyan.data.SaiyanState.CODEC),
									AttachmentSyncPredicate.all()));

	/** v0.16.5: Blue Beetle (Jaime Reyes) -- the power, the armour, Scarab Energy, Adaptation, timers and visible effects. */`]]);
edit(M + 'ProjectHeroMod.java', [['		com.projecthero.mod.andy.AndyMod.initialize(); // v0.17.3\n',
  '		com.projecthero.mod.andy.AndyMod.initialize(); // v0.17.3\n		com.projecthero.mod.saiyan.SaiyanMod.initialize(); // v0.17.3\n']]);
edit(M + 'diagnostics/ServerStateReset.java', [['		com.projecthero.mod.andy.AndyMod.clearSessionState(); // v0.17.3\n',
  '		com.projecthero.mod.andy.AndyMod.clearSessionState(); // v0.17.3\n		com.projecthero.mod.saiyan.SaiyanMod.clearSessionState(); // v0.17.3\n']]);
edit(M + 'hero/AbilityRouter.java', [
  [`		// v0.17.3: Black Panther, on the same terms
		if (com.projecthero.mod.blackpanther.BlackPantherAbilityManager.hasContext(player)) {`,
   `		// v0.17.3: the Saiyan, on the same terms
		if (com.projecthero.mod.saiyan.SaiyanAbilityManager.hasContext(player)) {
			com.projecthero.mod.saiyan.SaiyanAbilityManager.handle(player, slot, pressed);
			return;
		}
		// v0.17.3: Black Panther, on the same terms
		if (com.projecthero.mod.blackpanther.BlackPantherAbilityManager.hasContext(player)) {`],
  ['		com.projecthero.mod.andy.Andy.tick(player); // v0.17.3\n',
   '		com.projecthero.mod.andy.Andy.tick(player); // v0.17.3\n		com.projecthero.mod.saiyan.Saiyan.tick(player); // v0.17.3: Ki, the form, mastery, flight\n'],
]);
edit(M + 'hero/HeroTiers.java', [
  ['				|| com.projecthero.mod.andy.Andy.hasPower(player) // v0.17.3\n',
   '				|| com.projecthero.mod.andy.Andy.hasPower(player) // v0.17.3\n				|| com.projecthero.mod.saiyan.Saiyan.hasPower(player) // v0.17.3\n'],
  ['			"andy", // v0.17.3: Undead Unluck\n', '			"andy", // v0.17.3: Undead Unluck\n			"saiyan", // v0.17.3\n'],
  ['			case "andy" -> com.projecthero.mod.andy.Andy.hasPower(player); // v0.17.3\n',
   '			case "andy" -> com.projecthero.mod.andy.Andy.hasPower(player); // v0.17.3\n			case "saiyan" -> com.projecthero.mod.saiyan.Saiyan.hasPower(player); // v0.17.3\n'],
  [`			case "andy" -> {
				if (com.projecthero.mod.andy.Andy.hasPower(player)) {
					com.projecthero.mod.andy.Andy.revoke(player);
				}
			}`,
   `			case "andy" -> {
				if (com.projecthero.mod.andy.Andy.hasPower(player)) {
					com.projecthero.mod.andy.Andy.revoke(player);
				}
			}
			case "saiyan" -> {
				if (com.projecthero.mod.saiyan.Saiyan.hasPower(player)) {
					com.projecthero.mod.saiyan.Saiyan.revoke(player);
				}
			}`],
  [`		if (!excludeHeroKeys.contains("andy") && com.projecthero.mod.andy.Andy.hasPower(player)) {
			return true; // v0.17.3
		}`,
   `		if (!excludeHeroKeys.contains("andy") && com.projecthero.mod.andy.Andy.hasPower(player)) {
			return true; // v0.17.3
		}
		if (!excludeHeroKeys.contains("saiyan") && com.projecthero.mod.saiyan.Saiyan.hasPower(player)) {
			return true; // v0.17.3
		}`],
]);
edit(M + 'hero/PowerGrants.java', [
  ['"blue_beetle", "black_panther", "andy",\n', '"blue_beetle", "black_panther", "andy", "saiyan",\n'],
  [`			case "andy" -> {
				boolean ok = com.projecthero.mod.andy.Andy.grant(target);`,
   `			case "saiyan" -> {
				boolean ok = com.projecthero.mod.saiyan.Saiyan.grant(target);
				return new Result(ok, Component.literal(ok ? "Made " + name + " a Saiyan" : name + " is already a Saiyan"));
			}
			case "andy" -> {
				boolean ok = com.projecthero.mod.andy.Andy.grant(target);`],
  ['			case "andy" -> com.projecthero.mod.andy.Andy.revoke(target); // v0.17.3\n',
   '			case "andy" -> com.projecthero.mod.andy.Andy.revoke(target); // v0.17.3\n			case "saiyan" -> com.projecthero.mod.saiyan.Saiyan.revoke(target); // v0.17.3\n'],
]);
edit(M + 'command/HeroCommand.java', [
  ['			case "andy" -> com.projecthero.mod.andy.Andy.revoke(target); // v0.17.3\n',
   '			case "andy" -> com.projecthero.mod.andy.Andy.revoke(target); // v0.17.3\n			case "saiyan" -> com.projecthero.mod.saiyan.Saiyan.revoke(target); // v0.17.3\n'],
  ['"Unknown Hero-Tier power (andy, black_panther, ', '"Unknown Hero-Tier power (andy, black_panther, saiyan, '],
]);
edit(M + 'command/ProjectHeroCommand.java', [['				.then(com.projecthero.mod.andy.AndyMod.command())\n',
  '				.then(com.projecthero.mod.andy.AndyMod.command())\n				.then(com.projecthero.mod.saiyan.SaiyanMod.command()) // v0.17.3\n']]);
edit(M + 'squad/HeroIdentity.java', [[`		if (com.projecthero.mod.andy.Andy.hasPower(player)) {
			return "projecthero.squad.identity.andy"; // v0.17.3
		}`, `		if (com.projecthero.mod.andy.Andy.hasPower(player)) {
			return "projecthero.squad.identity.andy"; // v0.17.3
		}
		if (com.projecthero.mod.saiyan.Saiyan.hasPower(player)) {
			return "projecthero.squad.identity.saiyan"; // v0.17.3
		}`]]);
edit(M + 'item/ModCreativeTab.java', [['				com.projecthero.mod.andy.item.AndyItems.addToCreativeTab(output);\n',
  '				com.projecthero.mod.andy.item.AndyItems.addToCreativeTab(output);\n				// v0.17.3 the Saiyan: Saiyan Blood\n				com.projecthero.mod.saiyan.item.SaiyanItems.addToCreativeTab(output);\n']]);
console.log('ok');
