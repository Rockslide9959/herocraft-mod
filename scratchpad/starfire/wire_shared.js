// v0.17.3 Starfire: appends the power's one-line hooks into the shared files. Run once from the repo root:
//   node scratchpad/starfire/wire_shared.js
// Refuses a file that already mentions starfire (so a second run changes nothing).
const fs = require('fs');
const J = 'src/main/java/com/projecthero/mod/', C = 'src/client/java/com/projecthero/mod/client/';
const edits = [
  // [file, anchor (substring of exactly one line), text, 'after'|'before'|special]
  [J + 'ProjectHeroMod.java', 'com.projecthero.mod.spin.SpinMod.initialize();', '\t\tcom.projecthero.mod.starfire.StarfireMod.initialize(); // v0.17.3: Starfire -- items, Starstone, damage, flight payload', 'after'],
  [J + 'attachment/ModAttachments.java', '/** v0.16.5: Blue Beetle (Jaime Reyes) -- the power', [
    '\t/** v0.17.3: Starfire -- the power, the Starbolt bar, the Joy/Fury needle, flight, beams, holds, timers. */',
    '\tpublic static final AttachmentType<com.projecthero.mod.starfire.data.StarfireState> STARFIRE_STATE =',
    '\t\t\tAttachmentRegistry.create(ProjectHeroMod.id("starfire_state"),',
    '\t\t\t\t\tbuilder -> builder.persistent(com.projecthero.mod.starfire.data.StarfireState.CODEC)',
    '\t\t\t\t\t\t\t.copyOnDeath()',
    '\t\t\t\t\t\t\t.initializer(com.projecthero.mod.starfire.data.StarfireState::new)',
    '\t\t\t\t\t\t\t.syncWith(ByteBufCodecs.fromCodec(com.projecthero.mod.starfire.data.StarfireState.CODEC),',
    '\t\t\t\t\t\t\t\t\tAttachmentSyncPredicate.all()));', ''].join('\n'), 'before'],
  [J + 'hero/AbilityRouter.java', '// v0.17.3: Black Panther, on the same terms', [
    '\t\t// v0.17.3: Starfire, on the same terms',
    '\t\tif (com.projecthero.mod.starfire.StarfireAbilityManager.hasContext(player)) {',
    '\t\t\tcom.projecthero.mod.starfire.StarfireAbilityManager.handle(player, slot, pressed);',
    '\t\t\treturn;',
    '\t\t}'].join('\n'), 'before'],
  [J + 'hero/AbilityRouter.java', 'com.projecthero.mod.spin.Spin.tick(player);', '\t\tcom.projecthero.mod.starfire.Starfire.tick(player); // v0.17.3: the Starbolt bar, Joy/Fury, flight, the running moves', 'after'],
  [J + 'hero/HeroTiers.java', '|| com.projecthero.mod.spin.Spin.hasPower(player) // v0.17.3', '\t\t\t\t|| com.projecthero.mod.starfire.Starfire.hasPower(player) // v0.17.3: Starfire', 'after'],
  [J + 'hero/HeroTiers.java', '"spin", // v0.17.3: the Spin (the Spin)', '\t\t\t"starfire", // v0.17.3: Starfire (Teen Titans)', 'after'],
  [J + 'hero/HeroTiers.java', 'case "spin" -> com.projecthero.mod.spin.Spin.hasPower(player); // v0.17.3', '\t\t\tcase "starfire" -> com.projecthero.mod.starfire.Starfire.hasPower(player); // v0.17.3', 'after'],
  [J + 'hero/HeroTiers.java', 'case "spin" -> { // v0.17.3', [
    '\t\t\tcase "starfire" -> { // v0.17.3',
    '\t\t\t\tif (com.projecthero.mod.starfire.Starfire.hasPower(player)) {',
    '\t\t\t\t\tcom.projecthero.mod.starfire.Starfire.revoke(player);',
    '\t\t\t\t}',
    '\t\t\t}'].join('\n'), 'before'],
  [J + 'hero/HeroTiers.java', 'if (!excludeHeroKeys.contains("spin") && com.projecthero.mod.spin.Spin.hasPower(player)) {', [
    '\t\tif (!excludeHeroKeys.contains("starfire") && com.projecthero.mod.starfire.Starfire.hasPower(player)) {',
    '\t\t\treturn true; // v0.17.3',
    '\t\t}'].join('\n'), 'before'],
  [J + 'hero/PowerGrants.java', '"spin", // v0.17.3: the Spin (the Spin)', '\t\t\t"starfire", // v0.17.3: Starfire', 'after'],
  [J + 'hero/PowerGrants.java', '"spin", // v0.17.3: only the Zeppeli Steel Balls', '\t\t\t"starfire", // v0.17.3: only the Tamaranean Starstone (End Cities, meteor craters)', 'after'],
  [J + 'hero/PowerGrants.java', 'case "spin" -> { // v0.17.3', [
    '\t\t\tcase "starfire" -> { // v0.17.3',
    '\t\t\t\tboolean ok = com.projecthero.mod.starfire.Starfire.grant(target);',
    '\t\t\t\treturn new Result(ok, Component.literal(ok ? "Made " + name + " Starfire" : name + " is already Starfire"));',
    '\t\t\t}'].join('\n'), 'before'],
  [J + 'hero/PowerGrants.java', 'case "spin" -> com.projecthero.mod.spin.Spin.revoke(target); // v0.17.3', '\t\t\tcase "starfire" -> com.projecthero.mod.starfire.Starfire.revoke(target); // v0.17.3', 'after'],
  [J + 'command/HeroCommand.java', 'case "spin" -> com.projecthero.mod.spin.Spin.revoke(target); // v0.17.3', '\t\t\tcase "starfire" -> com.projecthero.mod.starfire.Starfire.revoke(target); // v0.17.3', 'after'],
  [J + 'command/ProjectHeroCommand.java', '.then(com.projecthero.mod.spin.SpinMod.command()) // v0.17.3', '\t\t\t\t.then(com.projecthero.mod.starfire.StarfireMod.command()) // v0.17.3', 'after'],
  [J + 'diagnostics/ServerStateReset.java', 'com.projecthero.mod.spin.SpinMod.clearSessionState();', '\t\tcom.projecthero.mod.starfire.StarfireMod.clearSessionState(); // v0.17.3: Starfire -- runs (grabbed / carried creatures), bolts, homing bolts, lights', 'after'],
  [J + 'squad/HeroIdentity.java', 'if (com.projecthero.mod.spin.Spin.hasPower(player)) {', [
    '\t\tif (com.projecthero.mod.starfire.Starfire.hasPower(player)) {',
    '\t\t\treturn "projecthero.squad.identity.starfire"; // v0.17.3',
    '\t\t}'].join('\n'), 'before'],
  [J + 'item/ModCreativeTab.java', 'com.projecthero.mod.spin.SpinItems.addToCreativeTab(output);', [
    '\t\t\t\t// v0.17.3 Starfire: the Tamaranean Starstone and her costume',
    '\t\t\t\tcom.projecthero.mod.starfire.item.StarfireItems.addToCreativeTab(output);'].join('\n'), 'after'],
  [J + 'omnitrix/entity/OmnitrixMeteorEntity.java', 'OmnitrixPodEntity.place(level, Vec3.atBottomCenterOf(bottom));', '\t\t\tcom.projecthero.mod.starfire.StarfireLearning.onMeteorCrater(level, bottom); // v0.17.3: a Tamaranean Starstone, 1 in 3', 'after'],
  [C + 'flight/DirectionalFlight.java', '// v0.17.3: Undead\'s blood-jet flight (double-tap jump)', [
    '\t\t// v0.17.3: Starfire\'s double-tap-Space flight -- Kryptonian handling, 16 b/s cruise, 32 sprinting; Joy up to x1.4, Fury down to x0.7',
    '\t\tif (com.projecthero.mod.starfire.Starfire.isFlying(player)) {',
    '\t\t\tdouble bps = (sprint ? com.projecthero.mod.starfire.StarfireConfig.FLIGHT_SPRINT_BPS : com.projecthero.mod.starfire.StarfireConfig.FLIGHT_CRUISE_BPS)',
    '\t\t\t\t\t* com.projecthero.mod.starfire.Starfire.flightSpeedMult(player);',
    '\t\t\treturn DirectionalFlightModel.fixedCruise(DirectionalFlightModel.kryptonian(sprint, false), bps / 20.0);',
    '\t\t}'].join('\n'), 'before'],
  [C + 'flight/FlightFeel.java', 'return KRYPTONIAN; // v0.17.3: the Saiyan banks and leans like the Kryptonian', [
    '\t\t}',
    '\t\tif (com.projecthero.mod.starfire.Starfire.isFlying(player)) {',
    '\t\t\treturn KRYPTONIAN; // v0.17.3: Starfire too'].join('\n'), 'after'],
  [C + 'FlightPoseHelper.java', '|| com.projecthero.mod.saiyan.Saiyan.isFlying(player);', null, 'starfire-pose'],
  [C + 'gui/PowerInfoScreen.java', 'if (com.projecthero.mod.spin.Spin.hasPower(mc.player)) {', [
    '\t\tif (com.projecthero.mod.starfire.Starfire.hasPower(mc.player)) {',
    '\t\t\treturn com.projecthero.mod.hero.guide.HeroPackGuide.starfireChapter(); // v0.17.3',
    '\t\t}'].join('\n'), 'before'],
  [C + 'gui/SquadScreen.java', 'case "saiyan": return 0xFFFFD84A;', '\t\t\t\tcase "starfire": return 0xFF5CFF5A; // v0.17.3: starbolt green', 'after'],
  [C + 'mixin/QuirkSuitArmourMixin.java', '|| entity.getItemBySlot(slot).getItem() instanceof com.projecthero.mod.undead.item.UndeadSuitItem)', null, 'quirk-mixin'],
  [C + 'quirk/QuirkCostume.java', '// v0.17.3: Undead\'s Suit is drawn the same way, from its own skin', [
    '\t\t// v0.17.3: Starfire\'s costume too',
    '\t\tif (com.projecthero.mod.starfire.item.StarfireSuitItem.wornIn(entity, slot)) {',
    '\t\t\treturn tex(com.projecthero.mod.starfire.item.StarfireSuitItem.texture(slim(entity)));',
    '\t\t}'].join('\n'), 'before'],
  [C + 'quirk/QuirkCostume.java', 'if (!bird && !QuirkSuits.anyWorn(entity) && !com.projecthero.mod.undead.item.UndeadSuitItem.anyWorn(entity)) {', null, 'quirk-anyworn'],
  ['src/client/resources/projecthero.client.mixins.json', '"SpinScanColorMixin",', '\t\t"StarfireBondGlowMixin",', 'after'],
  ['src/gametest/resources/fabric.mod.json', '"com.projecthero.mod.gametest.SpinGameTests",', '      "com.projecthero.mod.gametest.StarfireGameTests",', 'after'],
];
const files = {};
for (const [f, anchor, text, mode] of edits) {
  if (!(f in files)) {
    const orig = fs.readFileSync(f, 'utf8');
    if (/starfire/i.test(orig)) throw new Error('already wired: ' + f);
    files[f] = orig;
  }
  const crlf = files[f].includes('\r\n');
  const lines = files[f].replace(/\r\n/g, '\n').split('\n');
  const hits = lines.filter(l => l.includes(anchor)).length;
  if (hits !== 1) throw new Error('anchor found ' + hits + 'x in ' + f + ': ' + anchor);
  const idx = lines.findIndex(l => l.includes(anchor));
  if (mode === 'after') lines.splice(idx + 1, 0, text);
  else if (mode === 'before') lines.splice(idx, 0, text);
  else if (mode === 'starfire-pose') lines[idx] = lines[idx].replace('|| com.projecthero.mod.saiyan.Saiyan.isFlying(player);',
      '|| com.projecthero.mod.saiyan.Saiyan.isFlying(player)\n\t\t\t\t// v0.17.3: Starfire\'s double-tap-Space flight\n\t\t\t\t|| com.projecthero.mod.starfire.Starfire.isFlying(player);');
  else if (mode === 'quirk-mixin') lines[idx] = lines[idx].replace('UndeadSuitItem)', 'UndeadSuitItem\n\t\t\t\t|| entity.getItemBySlot(slot).getItem() instanceof com.projecthero.mod.starfire.item.StarfireSuitItem)');
  else if (mode === 'quirk-anyworn') lines[idx] = lines[idx].replace('UndeadSuitItem.anyWorn(entity)) {', 'UndeadSuitItem.anyWorn(entity)\n\t\t\t\t&& !com.projecthero.mod.starfire.item.StarfireSuitItem.anyWorn(entity)) {');
  const out = lines.join('\n');
  files[f] = crlf ? out.replace(/\n/g, '\r\n') : out;
}
for (const [f, s] of Object.entries(files)) {
  fs.writeFileSync(f, s);
  console.log('wired', f);
}
