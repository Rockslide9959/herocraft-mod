// v0.17.7 Sky Fortress: Guidebook + JEI hooks (idempotent, CRLF-safe). node scratchpad/v0177/sky_guide.js
const fs = require('fs');
function edit(p, anchor, block, after = true, marker, skip = 0) {
  let s = fs.readFileSync(p, 'utf8');
  const nl = s.includes('\r\n') ? '\r\n' : '\n';
  if (s.includes(marker)) { console.log('already', marker); return; }
  const i = s.indexOf(anchor);
  if (i < 0) throw new Error('no anchor: ' + anchor);
  const lines = block.split('\n').join(nl);
  if (after) {
    let e = s.indexOf(nl, i + anchor.length - 1);
    for (let k = 0; k < skip; k++) e = s.indexOf(nl, e + nl.length);
    s = s.slice(0, e + nl.length) + lines + nl + s.slice(e + nl.length);
  } else {
    const b = s.lastIndexOf(nl, i) + nl.length;
    s = s.slice(0, b) + lines + nl + s.slice(b);
  }
  fs.writeFileSync(p, s);
  console.log('ok', marker);
}
const G = 'src/main/java/com/projecthero/mod/hero/guide/HeroPackGuide.java';
// Structures chapter: a Sky Fortress line after the small world places
edit(G, 'para(lines, "projecthero.guide.structures.places.body");',
  '\t\t\t// v0.17.7: the Sky Fortress (Tier 4; detailed in Villain Hideouts)\n\t\t\tblank(lines);\n\t\t\thead(lines, "projecthero.guide.structures.sky_fortress");\n\t\t\tpara(lines, "projecthero.guide.structures.sky_fortress.body");',
  true, 'projecthero.guide.structures.sky_fortress"');
// Loot & Progression: a Tier 4 dungeon line before rarity
edit(G, 'head(lines, "projecthero.guide.loot.rarity");',
  '\t\t\t// v0.17.7: the Sky Fortress, a Tier 4 dungeon (com.projecthero.mod.skyfortress)\n\t\t\tlines.add(Component.literal(" • ").withStyle(s -> s.withColor(com.projecthero.mod.loot.HeroVault.trimColour(4)))\n\t\t\t\t\t.append(Component.translatable("projecthero.guide.loot.dungeon.sky_fortress").withStyle(ChatFormatting.GRAY)));\n\t\t\tblank(lines);',
  false, 'projecthero.guide.loot.dungeon.sky_fortress');
// Villain Hideouts chapter: the Sky Fortress sub-section after The Vault's artifacts
edit(G, '.append(Component.translatable("projecthero.guide.hideouts.vault.a." + a).withStyle(ChatFormatting.GRAY)));',
  [
    '\t\t\t// v0.17.7: the Sky Fortress (Tier 4) -- a sub-section too (chapter counts unchanged)',
    '\t\t\tblank(lines);',
    '\t\t\tlines.add(Component.translatable("projecthero.guide.hideouts.sky").withStyle(s -> s.withColor(com.projecthero.mod.skyfortress.SkyFortressMod.COLOUR).withBold(true)));',
    '\t\t\tpara(lines, "projecthero.guide.hideouts.sky.body");',
    '\t\t\tfor (String part : new String[] { "launch", "wings", "turbines", "cells", "officer", "kestrel", "hazards", "clearing" }) {',
    '\t\t\t\tblank(lines);',
    '\t\t\t\thead(lines, "projecthero.guide.hideouts.sky." + part);',
    '\t\t\t\tpara(lines, "projecthero.guide.hideouts.sky." + part + ".body");',
    '\t\t\t}',
    '\t\t\tlines.add(Component.literal(" • ").withStyle(s -> s.withColor(com.projecthero.mod.skyfortress.SkyFortressMod.COLOUR))',
    '\t\t\t\t\t.append(Component.translatable("projecthero.guide.hideouts.sky.a.kestrel_thruster").withStyle(ChatFormatting.GRAY)));',
  ].join('\n'),
  true, 'projecthero.guide.hideouts.sky.body', 1);
// JEI: info pages for the items and blocks
const J = 'src/client/java/com/projecthero/mod/client/compat/jei/ProjectHeroJeiPlugin.java';
edit(J, '// v0.17.5: Sentry -- the serum',
  [
    '\t\t// v0.17.7: the Sky Fortress -- the pass, the parachute, Kestrel\'s Thruster and its blocks',
    '\t\tfor (net.minecraft.world.item.Item i : com.projecthero.mod.skyfortress.item.SkyFortressItems.all()) {',
    '\t\t\tregistration.addItemStackInfo(new ItemStack(i), net.minecraft.network.chat.Component.translatable(i.getDescriptionId() + ".hint"),',
    '\t\t\t\t\tnet.minecraft.network.chat.Component.translatable("projecthero.jei.sky_fortress"));',
    '\t\t}',
    '\t\tfor (net.minecraft.world.item.Item i : com.projecthero.mod.skyfortress.block.SkyFortressBlocks.all()) {',
    '\t\t\tregistration.addItemStackInfo(new ItemStack(i), net.minecraft.network.chat.Component.translatable(i.getDescriptionId() + ".hint"),',
    '\t\t\t\t\tnet.minecraft.network.chat.Component.translatable("projecthero.jei.sky_fortress"));',
    '\t\t}',
  ].join('\n'),
  false, 'SkyFortressItems.all()');
