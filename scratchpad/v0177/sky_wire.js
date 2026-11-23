// v0.17.7 Sky Fortress: one-line hooks into the shared start-up files (idempotent; keeps each file's line endings).
//   node scratchpad/v0177/sky_wire.js
const fs = require('fs');
function ins(p, anchor, line) {
  let s = fs.readFileSync(p, 'utf8');
  if (s.includes(line.trim())) { console.log('already', p); return; }
  const nl = s.includes('\r\n') ? '\r\n' : '\n';
  const i = s.indexOf(anchor);
  if (i < 0) throw new Error('no anchor in ' + p);
  const e = s.indexOf(nl, i);
  s = s.slice(0, e + nl.length) + line + nl + s.slice(e + nl.length);
  fs.writeFileSync(p, s);
  console.log('ok', p);
}
ins('src/main/java/com/projecthero/mod/ProjectHeroMod.java', 'com.projecthero.mod.vaultdungeon.VaultDungeonMod.initialize();',
  '\t\tcom.projecthero.mod.skyfortress.SkyFortressMod.initialize(); // v0.17.7: Tier 4 dungeon, the Sky Fortress');
ins('src/main/java/com/projecthero/mod/item/ModCreativeTab.java', 'com.projecthero.mod.vaultdungeon.item.VaultItems.addToCreativeTab(output);',
  '\t\t\t\tcom.projecthero.mod.skyfortress.item.SkyFortressItems.addToCreativeTab(output); // v0.17.7: Sky Fortress items + blocks');
ins('src/main/java/com/projecthero/mod/diagnostics/ServerStateReset.java', 'com.projecthero.mod.loot.LootMod.clearSessionState();',
  '\t\tcom.projecthero.mod.skyfortress.SkyFortressMod.clearSessionState(); // v0.17.7: Sky Fortress launch-pad flings + turbine sabotage progress');
ins('src/client/java/com/projecthero/mod/client/ProjectHeroModClient.java', 'com.projecthero.mod.client.vaultdungeon.VaultDungeonClient.initialize();',
  '\t\tcom.projecthero.mod.client.skyfortress.SkyFortressClient.initialize(); // v0.17.7: Sky Fortress crew, Admiral Kestrel, missiles, shield layer');
ins('src/gametest/resources/fabric.mod.json', '"com.projecthero.mod.gametest.VaultDungeonGameTests",',
  '      "com.projecthero.mod.gametest.SkyFortressGameTests",');
