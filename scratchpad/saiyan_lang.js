// v0.17.3 Saiyan wave 1: en_us strings. CRLF kept, duplicates refused, JSON validated. Run from the repo root.
const fs = require('fs');
const L = 'src/main/resources/assets/projecthero/lang/en_us.json';
let t = fs.readFileSync(L, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
const j = JSON.parse(t);
const add = {
  "item.projecthero.saiyan_blood": "Saiyan Blood",
  "item.projecthero.saiyan_blood.desc1": "Drink to become a Saiyan.",
  "item.projecthero.saiyan_blood.desc2": "It burns gold on the way down.",
  "key.projecthero.saiyan_charge": "Saiyan: Charge Ki",
  "projecthero.hero_tier.saiyan": "Saiyan",
  "projecthero.squad.identity.saiyan": "Saiyan",
  "gamerule.projectheroSaiyanAllForms": "Saiyans have every form unlocked",

  "projecthero.saiyan.form.base": "Base",
  "projecthero.saiyan.form.base.short": "Base",
  "projecthero.saiyan.form.ssj": "Super Saiyan",
  "projecthero.saiyan.form.ssj.short": "SSJ",
  "projecthero.saiyan.form.ssj2": "Super Saiyan 2",
  "projecthero.saiyan.form.ssj2.short": "SSJ2",
  "projecthero.saiyan.form.ssj3": "Super Saiyan 3",
  "projecthero.saiyan.form.ssj3.short": "SSJ3",
  "projecthero.saiyan.form.god": "Super Saiyan God",
  "projecthero.saiyan.form.god.short": "God",
  "projecthero.saiyan.form.blue": "Super Saiyan Blue",
  "projecthero.saiyan.form.blue.short": "Blue",
  "projecthero.saiyan.form.blue_overdrive": "Blue Overdrive",
  "projecthero.saiyan.form.blue_overdrive.short": "Overdrive",
  "projecthero.saiyan.form.instinct": "Instinct",
  "projecthero.saiyan.form.instinct.short": "Instinct",

  "projecthero.saiyan.ability.combo": "Saiyan Combo",
  "projecthero.saiyan.ability.combo.desc": "Left click a creature: three fast hits, then a launching kick.",
  "projecthero.saiyan.ability.feint": "Feint Strike",
  "projecthero.saiyan.ability.feint.desc": "A stunning jab, then a kick that breaks a guard.",
  "projecthero.saiyan.ability.ki_beam": "Ki Beam",
  "projecthero.saiyan.ability.ki_beam.desc": "Charge, then fire a beam of Ki. It clashes with other beams.",
  "projecthero.saiyan.ability.spirit_orb": "Spirit Orb",
  "projecthero.saiyan.ability.spirit_orb.desc": "A long charge, then a giant blast over a wide area. Base form only.",
  "projecthero.saiyan.ability.barrage": "Barrage",
  "projecthero.saiyan.ability.barrage.desc": "Rapid punches, ending in a knockback kick.",
  "projecthero.saiyan.ability.sky_combo": "Sky Combo",
  "projecthero.saiyan.ability.sky_combo.desc": "Kick an enemy up, chase them through the air and smash them down.",
  "projecthero.saiyan.ability.ki_dash": "Ki Dash",
  "projecthero.saiyan.ability.ki_dash.desc": "Burst forward where you look, faster in stronger forms. 6 Ki.",
  "projecthero.saiyan.ability.flight": "Flight",
  "projecthero.saiyan.ability.flight.desc": "Fly where you look (Sprint to go faster). Costs a little Ki while in the air; ends at 0 Ki or on landing.",
  "projecthero.saiyan.ability.dragon_rush": "Dragon Rush",
  "projecthero.saiyan.ability.dragon_rush.desc": "A wind-up, then a rushing punch. Super Saiyan 3 and up.",
  "projecthero.saiyan.ability.instant_step": "Instant Step",
  "projecthero.saiyan.ability.instant_step.desc": "Teleport to where you are looking.",
  "projecthero.saiyan.ability.power_down": "Power Down",
  "projecthero.saiyan.ability.power_down.desc": "Drop back to Base form, which stops the Ki drain.",
  "projecthero.saiyan.ability.form_wheel": "Form Wheel",
  "projecthero.saiyan.ability.form_wheel.desc": "Hold H and click a form to transform. Shift+H opens the power wheel.",
  "projecthero.saiyan.ability.charge": "Charge Ki",
  "projecthero.saiyan.ability.charge.desc": "Hold to fill the Ki bar (12 a second). You slow to a crawl and can't attack while charging.",

  "hud.projecthero.saiyan.ki": "Ki",
  "hud.projecthero.saiyan.mastery": "Mastery",
  "hud.projecthero.saiyan.exhausted": "Exhausted",

  "message.projecthero.saiyan.title": "SAIYAN",
  "message.projecthero.saiyan.subtitle": "Your power has awakened",
  "message.projecthero.saiyan.acquired": "You are a Saiyan.",
  "message.projecthero.saiyan.acquired_hint": "Hold Q to charge Ki, hold H to pick a form, and Shift+V to power down. Time in a form unlocks the next one.",
  "message.projecthero.saiyan.already": "You are already a Saiyan",
  "message.projecthero.saiyan.no_ki": "Not enough Ki",
  "message.projecthero.saiyan.cant": "You can't transform right now",
  "message.projecthero.saiyan.already_form": "You are already in %s",
  "message.projecthero.saiyan.locked": "%s isn't mastered yet",
  "message.projecthero.saiyan.exhausted": "Too exhausted to transform",
  "message.projecthero.saiyan.too_soon": "Steady yourself first",
  "message.projecthero.saiyan.overdrive_from_blue": "Blue Overdrive only works from Super Saiyan Blue",
  "message.projecthero.saiyan.instinct_health": "Instinct only awakens when you are close to defeat (35% health or less)",
  "message.projecthero.saiyan.instinct_ki": "Instinct needs at least 30 Ki",
  "message.projecthero.saiyan.already_base": "You are already in Base form",
  "message.projecthero.saiyan.exhausted_now": "Your Ki is spent: back to Base, exhausted",
  "message.projecthero.saiyan.unlocked": "New form mastered: %s",
  "message.projecthero.saiyan.overdrive_drop": "Your body can't take Overdrive any longer: back to Blue",
  "message.projecthero.saiyan.charging": "You can't do that while charging Ki",

  "screen.projecthero.saiyan.form_wheel": "Saiyan Forms",
  "screen.projecthero.saiyan.form_wheel.hint": "Click a form to transform. Let go of H to close.",
  "screen.projecthero.saiyan.locked": "Not mastered yet",
  "screen.projecthero.saiyan.current": "Your current form",
  "screen.projecthero.saiyan.exhausted": "Too exhausted",
  "screen.projecthero.saiyan.from_blue": "Only from Blue",
  "screen.projecthero.saiyan.low_hp": "Needs 35% health or less",
  "screen.projecthero.saiyan.no_ki": "Not enough Ki",
  "screen.projecthero.saiyan.cost": "%s Ki",

  "projecthero.guide.saiyan": "Saiyan",
  "projecthero.guide.saiyan.tier": "Hero-Tier Primary power",
  "projecthero.guide.saiyan.body": "A warrior race whose power grows in steps. You charge Ki, transform into stronger forms, and master each form to unlock the next.",
  "projecthero.guide.saiyan.getting": "Getting it",
  "projecthero.guide.saiyan.getting.body": "Drink Saiyan Blood (crafted from a glass bottle, a golden apple, two blaze powder, two gold ingots and a diamond), or roll it from a random power serum. It replaces your Primary power.",
  "projecthero.guide.saiyan.ki": "Ki",
  "projecthero.guide.saiyan.ki.body": "Your Ki bar holds 100. Hold Q to charge it (12 a second); you slow to a crawl and can't attack while charging. Transforming costs Ki, and from Super Saiyan 2 up every form drains it. In Base and Super Saiyan it slowly comes back by itself. At 0 Ki you drop to Base, exhausted: slowed and unable to transform for 5 seconds.",
  "projecthero.guide.saiyan.forms": "Forms",
  "projecthero.guide.saiyan.forms.body": "Hold H and click a form. Base: +4 attack, +4 health, 10% less damage taken. Super Saiyan (15 Ki): +8 attack, +8 health, 25% less damage. Super Saiyan 2 (20 Ki, drains 1 a second): +11 attack, +12 health, 35%. Super Saiyan 3 (30 Ki, drains 3): +14 attack, +12 health, 40%. God (30 Ki, drains 1.2): +14 attack, +16 health, 50%, heals 1 health every 4 seconds. Blue (40 Ki, drains 2): +17 attack, +20 health, 60%, heals. Every form is also faster and makes your moves hit harder. Shift+V powers down.",
  "projecthero.guide.saiyan.mastery": "Mastery",
  "projecthero.guide.saiyan.mastery.body": "Time spent in a form unlocks the next: 2 minutes in Super Saiyan unlocks Super Saiyan 2, 3 in SSJ2 unlocks SSJ3, 3 in SSJ3 unlocks God, 4 in God unlocks Blue, and 2 in Blue unlocks Blue Overdrive. The HUD shows how close you are. The gamerule projectheroSaiyanAllForms unlocks every form at once.",
  "projecthero.guide.saiyan.instinct": "Instinct",
  "projecthero.guide.saiyan.instinct.body": "Unlocked with Blue, but it only awakens when you are close to defeat: 35% health or less, and at least 30 Ki. In Instinct you sidestep about a third of the hits aimed at you, and you move very fast (+18 attack, 55% less damage, drains 1 Ki a second).",
  "projecthero.guide.saiyan.overdrive": "Blue Overdrive",
  "projecthero.guide.saiyan.overdrive.body": "Only from Blue (20 Ki more). The strongest form (+21 attack, 60% less damage), but it burns 1 health a second and drains 4 Ki a second. When your health gets very low it drops you back to Blue by itself.",
  "projecthero.guide.saiyan.flight": "Moving",
  "projecthero.guide.saiyan.flight.body": "X is a Ki Dash where you look (6 Ki). Shift+X takes off: fly where you look, Sprint to go faster, faster still in stronger forms. Flight costs a little Ki and ends on landing or at 0 Ki. No fall damage while flying or just after a dash.",
  "projecthero.guide.saiyan.controls": "Controls",
  "projecthero.guide.saiyan.commands": "Commands",
  "projecthero.guide.saiyan.commands.body": "Operators: /projecthero power tune saiyan ki <0-100>, /projecthero power tune saiyan form <name>, /projecthero power tune saiyan unlock_all."
};
let added = [];
for (const [k, v] of Object.entries(add)) {
  if (k in j) throw new Error('duplicate key ' + k);
  added.push('  ' + JSON.stringify(k) + ': ' + JSON.stringify(v));
}
const end = t.lastIndexOf('}');
let head = t.slice(0, end).replace(/\s+$/, '');
t = head + ',\n' + added.join(',\n') + '\n}\n';
JSON.parse(t);
fs.writeFileSync(L, crlf ? t.replace(/\n/g, '\r\n') : t);
console.log('added', added.length);
