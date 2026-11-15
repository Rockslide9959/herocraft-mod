// v0.17.5 Tier 2 Sites: wires SiteUpgrade.addAnnex into each old site's structure (CRLF-safe). Idempotent.
// Run from the repo root: node scratchpad/wire_sites_v0175.js
const fs = require('fs');
const base = 'src/main/java/com/projecthero/mod/';
const files = {
  'bluebeetle/worldgen/DigSiteStructure.java': ['DigSitePiece', 'DIG_SITE'],
  'doctorfate/worldgen/TombOfNabuStructure.java': ['TombOfNabuPiece', 'TOMB_OF_NABU'],
  'greenlantern/worldgen/FallenLanternSiteStructure.java': ['FallenLanternSitePiece', 'FALLEN_LANTERN_SITE'],
  'hulk/worldgen/GammaLabStructure.java': ['GammaLabPiece', 'GAMMA_LAB'],
  'kryptonian/worldgen/KryptoniteCraterStructure.java': ['KryptoniteCraterPiece', 'KRYPTONITE_CRATER'],
  'maxsteel/worldgen/SteelCrashSiteStructure.java': ['SteelCrashSitePiece', 'STEEL_CRASH_SITE'],
  'moonknight/temple/TempleOfKhonshuStructure.java': ['TempleOfKhonshuPiece', 'TEMPLE_OF_KHONSHU'],
  'nova/worldgen/NovaPodSiteStructure.java': ['NovaPodSitePiece', 'NOVA_POD_SITE'],
  'punisher/worldgen/VigilanteSafehouseStructure.java': ['VigilanteSafehousePiece', 'VIGILANTE_SAFEHOUSE'],
  'symbiote/worldgen/SymbioteLabStructure.java': ['SymbioteLabPiece', 'SYMBIOTE_LAB'],
  'symbiote/worldgen/SymbioteMeteorStructure.java': ['SymbioteMeteorPiece', 'SYMBIOTE_METEOR'],
  'worldgen/GraveyardStructure.java': ['GraveyardPiece', 'GRAVEYARD'],
  'worldgen/MjolnirCraterStructure.java': ['MjolnirCraterPiece', 'MJOLNIR_CRATER'],
  'worldgen/ResearchSiteStructure.java': ['ResearchSitePiece', null],
};
for (const [f, [piece, theme]] of Object.entries(files)) {
  const p = base + f;
  let s = fs.readFileSync(p, 'utf8');
  if (s.includes('SiteUpgrade.addAnnex')) { console.log('skip', f); continue; }
  const re = new RegExp('builder\\.addPiece\\((new ' + piece + '\\([\\s\\S]*?\\))\\);');
  const m = s.match(re);
  if (!m) { console.log('NO MATCH', f); process.exitCode = 1; continue; }
  const th = theme ? 'com.projecthero.mod.loot.sites.SiteTheme.' + theme
                   : 'com.projecthero.mod.loot.sites.SiteTheme.valueOf(siteType.name())';
  const rep = piece + ' sitePiece = ' + m[1] + ';\n' +
    'INDENTbuilder.addPiece(sitePiece);\n' +
    'INDENT// v0.17.5 Tier 2 Sites: the annex (vault room + guard squad) beside the original site\n' +
    'INDENTcom.projecthero.mod.loot.sites.SiteUpgrade.addAnnex(builder, context, sitePiece.getBoundingBox(), ' + th + ');';
  const idx = s.indexOf(m[0]);
  const lineStart = s.lastIndexOf('\n', idx) + 1;
  const indent = s.slice(lineStart, idx);
  const nl = s.includes('\r\n') ? '\r\n' : '\n';
  s = s.slice(0, idx) + rep.replace(/\n/g, nl).replace(/INDENT/g, indent) + s.slice(idx + m[0].length);
  fs.writeFileSync(p, s);
  console.log('wired', f);
}
