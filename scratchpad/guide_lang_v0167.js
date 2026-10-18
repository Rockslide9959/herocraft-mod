// v0.16.7 guide agent: inserts the guidebook-remake + Quirk chapter lang keys right after the anchor line.
// Run from the worktree root: node scratchpad/guide_lang_v0167.js
const fs = require("fs");
const P = "src/main/resources/assets/projecthero/lang/en_us.json";
const ANCHOR = '"projecthero.guide.miraculous.countdown.body"';

const keys = {
  // categories (home page tiles)
  "projecthero.guide.category.getting_started": "Getting Started",
  "projecthero.guide.category.getting_started.desc": "The basics, the controls and squads.",
  "projecthero.guide.category.marvel": "Marvel",
  "projecthero.guide.category.marvel.desc": "Avengers, mutants, street heroes and cosmic champions.",
  "projecthero.guide.category.dc": "DC",
  "projecthero.guide.category.dc.desc": "Lanterns, Kryptonians, speedsters and more.",
  "projecthero.guide.category.quirks": "Quirks",
  "projecthero.guide.category.quirks.desc": "My Hero Academia: One For All and seven Quirks.",
  "projecthero.guide.category.miraculous": "Miraculous",
  "projecthero.guide.category.miraculous.desc": "The six Kwami and their jewels.",
  "projecthero.guide.category.other": "Other Heroes & Mutations",
  "projecthero.guide.category.other.desc": "Max Steel, the Titan Shifter and every mutation.",
  "projecthero.guide.category.events": "Bosses & Events",
  "projecthero.guide.category.events.desc": "Raids, invasions, world bosses and crime.",
  "projecthero.guide.category.world": "Items, Places & Crafting",
  "projecthero.guide.category.world.desc": "Structures, lab devices, Stark tech and cities.",
  "projecthero.guide.sub.quirks": "The seven Quirks",
  "projecthero.guide.sub.other_heroes": "Other heroes",
  "projecthero.guide.sub.mutations": "Mutations",
  "projecthero.guide.sub.raids": "Raids & invasions",
  "projecthero.guide.sub.bosses": "World bosses",
  // the screen
  "projecthero.guide.ui.search": "Search the guide...",
  "projecthero.guide.ui.results": "%s results for \"%s\"",
  "projecthero.guide.ui.result": "1 result for \"%s\"",
  "projecthero.guide.ui.no_results": "Nothing matches \"%s\".",
  "projecthero.guide.ui.no_results.hint": "Try a shorter word, a hero's name or a key like Shift+R.",
  "projecthero.guide.ui.chapters": "%s chapters",
  "projecthero.guide.ui.page": "Page %s of %s",
  "projecthero.guide.ui.pages": "Pages %s-%s of %s",
  "projecthero.guide.ui.prev": "< Prev",
  "projecthero.guide.ui.next": "Next >",
  "projecthero.guide.ui.next_chapter": "Next: %s >",
  "projecthero.guide.ui.continue": "Continue reading: %s",
  "projecthero.guide.ui.hint.home": "Pick a section, or just start typing to search.",
  "projecthero.guide.ui.hint.list": "Wheel scrolls - Backspace goes back - type to search",
  "projecthero.guide.ui.hint.chapter": "Wheel or arrow keys turn pages - Backspace goes back",
  "projecthero.guide.ui.hint.search": "Click a result to open it at that spot",
  "projecthero.guide.ui.back": "Back",
  "projecthero.guide.ui.in": "in %s",
  // the Quirks overview chapter
  "projecthero.guide.quirks": "Quirks: Overview",
  "projecthero.guide.quirks.tier": "My Hero Academia: seven Quirks, each a Primary power",
  "projecthero.guide.quirks.body": "A Quirk is a superpower you are born with. Seven can awaken in you: Hellflame, Overhaul, Engine, Dark Shadow, Erasure, Explosion and Zero Gravity. Each is a Primary power (you hold one at a time) and is always on: there is no transformation.",
  "projecthero.guide.quirks.get": "Getting a Quirk",
  "projecthero.guide.quirks.get.body": "The first time you spawn into a world, if neither a Green Lantern ring nor a Miraculous chose you, there is a 5% chance that a random Quirk awakens in you. Otherwise drink a Quirk Serum. A new Quirk replaces whatever Primary power you had.",
  "projecthero.guide.quirks.get.short": "A 5% chance when you first spawn into a world (after the Green Lantern ring and the Miraculous miss), or drink its Quirk Serum. It replaces your Primary power.",
  "projecthero.guide.quirks.serums": "Quirk Serums",
  "projecthero.guide.quirks.serums.body": "Every Quirk has its own serum (quirk_serum_<quirk>), and the Quirk Trigger (quirk_serum_random) awakens a random one. Craft them, or find them in dungeon loot chests.",
  "projecthero.guide.quirks.serum": "Its serum: %s",
  "projecthero.guide.quirks.meter": "The meter",
  "projecthero.guide.quirks.meter.body": "Every Quirk has its own meter on the HUD (Heat, Focus, Engine, Shadow, Dry Eyes, Sweat or Nausea). Push it to the limit and the Quirk overloads for a few seconds. Each Quirk's chapter says how, and which move still works.",
  "projecthero.guide.quirks.meter_of": "The %s meter",
  "projecthero.guide.quirks.controls": "Moves",
  "projecthero.guide.quirks.controls.body": "12 moves on R, G, X, Z, V and C, each with a Shift version, shown on the HUD. Some are held, some toggle. H is still the power selector.",
  "projecthero.guide.quirks.moves": "Moves",
  "projecthero.guide.quirks.moves.none": "(The move list appears once the Quirk's kit has loaded.)",
  "projecthero.guide.quirks.cooldown": "%s s",
  "projecthero.guide.quirks.costume": "The costume",
  "projecthero.guide.quirks.costume.body": "Type /projecthero quirk costume to wear your hero costume over your skin. Type it again to take it off.",
  "projecthero.guide.quirks.erasure": "Erasure stops every power",
  "projecthero.guide.quirks.erasure.body": "While Eraser Head stares at someone, ALL their powers stop: mutations, Hero-Tier powers, Miraculous and Quirks, players included. Tech gear such as Iron Man suits keeps working.",
  "projecthero.guide.quirks.erased": "Erased",
  "projecthero.guide.quirks.erased.body": "If Eraser Head stares at you, your Quirk (and every other power) stops until they blink or look away. Tech gear such as Iron Man suits still works.",
  "projecthero.guide.quirks.erased.self": "Your stare stops every power, other Erasure users' included. Tech gear such as Iron Man suits keeps working: it is not a power.",
  "projecthero.guide.quirks.list": "The seven Quirks",
  "projecthero.guide.quirks.one_for_all": "All Might's One For All is a Quirk too: it has its own chapter in this section.",
  // Hellflame
  "projecthero.guide.quirk.hellflame": "Hellflame (Endeavor)",
  "projecthero.guide.quirk.hellflame.tier": "Quirk (Primary power): Hellflame, the Quirk of Endeavor",
  "projecthero.guide.quirk.hellflame.body": "The Number One Hero's fire. Your body makes and controls flame: slicing threads, jets, a beam, flight on fire and a burning cloak. Every flame raises your Heat.",
  "projecthero.guide.quirk.hellflame.meter.body": "Heat rises as you use fire and vents about 4 a second when you stop, faster in water or rain.",
  "projecthero.guide.quirk.hellflame.overload": "Overheat",
  "projecthero.guide.quirk.hellflame.overload.body": "At 100 Heat you overheat for 8 s: Slowness II, a little self-burn and no fire moves. Shift+C Vent still works and dumps the Heat.",
  "projecthero.guide.quirk.hellflame.passive": "Good to know",
  "projecthero.guide.quirk.hellflame.passive.body": "Your squad is never burned. Bosses take at most 10% of their health per hit and are never rooted.",
  // Overhaul
  "projecthero.guide.quirk.overhaul": "Overhaul (Chisaki)",
  "projecthero.guide.quirk.overhaul.tier": "Quirk (Primary power): Overhaul, the Quirk of Kai Chisaki",
  "projecthero.guide.quirk.overhaul.body": "Take apart and rebuild anything you touch: burst enemies, raise spikes and walls from the ground, heal allies and fuse with stone.",
  "projecthero.guide.quirk.overhaul.meter.body": "Focus is spent by your moves and comes back on its own.",
  "projecthero.guide.quirk.overhaul.overload": "Out of Focus",
  "projecthero.guide.quirk.overhaul.overload.body": "Run Focus dry and your moves stop until it has come back.",
  "projecthero.guide.quirk.overhaul.passive": "Bare hands",
  "projecthero.guide.quirk.overhaul.passive.body": "Touch moves need an EMPTY main hand. Spikes, walls, ramps and pits are temporary: the ground always goes back.",
  // Engine
  "projecthero.guide.quirk.engine": "Engine (Ingenium)",
  "projecthero.guide.quirk.engine.tier": "Quirk (Primary power): Engine, the Quirk of Tenya Iida",
  "projecthero.guide.quirk.engine.body": "Engines in your calves: always faster, with Recipro kicks, bursts, dashes and a rescue carry on top.",
  "projecthero.guide.quirk.engine.meter.body": "Engine heat rises with Recipro moves and top speed, and cools while you walk.",
  "projecthero.guide.quirk.engine.overload": "Stall",
  "projecthero.guide.quirk.engine.overload.body": "At 100 your engines stall for 6 s: Slowness III and no moves. Shift+C Cool Down vents 50 heat.",
  "projecthero.guide.quirk.engine.passive": "Always on",
  "projecthero.guide.quirk.engine.passive.body": "Speed I and 1-block step assist. Rescue Carry lets you run with an ally or a mob in your arms.",
  // Dark Shadow
  "projecthero.guide.quirk.dark_shadow": "Dark Shadow (Tsukuyomi)",
  "projecthero.guide.quirk.dark_shadow.tier": "Quirk (Primary power): Dark Shadow, the Quirk of Fumikage Tokoyami",
  "projecthero.guide.quirk.dark_shadow.body": "A living shadow floats at your shoulder and fights for you. It is weak in daylight and fearsome in the dark.",
  "projecthero.guide.quirk.dark_shadow.meter.body": "Shadow rises in darkness and falls in light. The more Shadow, the harder Dark Shadow hits.",
  "projecthero.guide.quirk.dark_shadow.overload": "Berserk",
  "projecthero.guide.quirk.dark_shadow.overload.body": "At 100 Shadow in the dark it goes berserk for 8 s and attacks the nearest creatures (never you). Only Shift+C Calm works until it ends.",
  "projecthero.guide.quirk.dark_shadow.passive": "The shadow",
  "projecthero.guide.quirk.dark_shadow.passive.body": "Dark Shadow is always at your side. Abyssal Rampage only works in darkness.",
  // Erasure
  "projecthero.guide.quirk.erasure": "Erasure (Eraser Head)",
  "projecthero.guide.quirk.erasure.tier": "Quirk (Primary power): Erasure, the Quirk of Shota Aizawa",
  "projecthero.guide.quirk.erasure.body": "Stare at someone and their power stops. With your capture scarf you lash, bind, swing and slam.",
  "projecthero.guide.quirk.erasure.meter.body": "Dry Eyes starts at 100, drains while you erase and refills while you don't.",
  "projecthero.guide.quirk.erasure.overload": "Forced blink",
  "projecthero.guide.quirk.erasure.overload.body": "At 0 your eyes shut for 2 s: the erase ends and Dry Eyes refills. Shift+C Eye Drops refills it at once, even mid-blink.",
  "projecthero.guide.quirk.erasure.passive": "The Erase rule",
  "projecthero.guide.quirk.erasure.passive.body": "Erase cancels EVERY power while you stare, players included. On mobs: creepers can't swell, skeletons and pillagers can't shoot, endermen can't teleport, blazes and ghasts can't fire. Bosses lose their special moves but still fight.",
  // Explosion
  "projecthero.guide.quirk.explosion": "Explosion (Ground Zero)",
  "projecthero.guide.quirk.explosion.tier": "Quirk (Primary power): Explosion, the Quirk of Katsuki Bakugo",
  "projecthero.guide.quirk.explosion.body": "Your sweat is like nitroglycerin: palm blasts, rocket jumps, flashes and the Howitzer Impact.",
  "projecthero.guide.quirk.explosion.meter.body": "Sweat builds while you fight and move, faster where it is hot. Moves spend it: the more Sweat, the bigger the blast.",
  "projecthero.guide.quirk.explosion.overload": "Arm Strain",
  "projecthero.guide.quirk.explosion.overload.body": "Big blasts strain your arms. Fully strained, you get 5 s of Weakness and no moves.",
  "projecthero.guide.quirk.explosion.passive": "Safe blasts",
  "projecthero.guide.quirk.explosion.passive.body": "Your explosions never hurt you or your squad, and only break blocks (a little) when mobGriefing is on.",
  // Zero Gravity
  "projecthero.guide.quirk.zero_gravity": "Zero Gravity (Uravity)",
  "projecthero.guide.quirk.zero_gravity.tier": "Quirk (Primary power): Zero Gravity, the Quirk of Ochaco Uraraka",
  "projecthero.guide.quirk.zero_gravity.body": "Touch something to make it weightless: float mobs, rain debris, drift through the air, then slam it all down.",
  "projecthero.guide.quirk.zero_gravity.meter.body": "Nausea rises with how much you float and for how long, your own float included.",
  "projecthero.guide.quirk.zero_gravity.overload": "Nausea",
  "projecthero.guide.quirk.zero_gravity.overload.body": "At 100 everything you float drops, you get Nausea for 5 s and can't use moves.",
  "projecthero.guide.quirk.zero_gravity.passive": "Floating",
  "projecthero.guide.quirk.zero_gravity.passive.body": "Up to about 5 mobs float at once. Bosses can't be floated. Lifted blocks always go back."
};

let s = fs.readFileSync(P, "utf8");
const eol = s.includes("\r\n") ? "\r\n" : "\n";
const lines = s.split(eol);
const existing = JSON.parse(s);
for (const k of Object.keys(keys)) {
  if (k in existing) throw new Error("duplicate key " + k);
}
const at = lines.findIndex(l => l.trimStart().startsWith(ANCHOR));
if (at < 0) throw new Error("anchor not found");
const block = Object.entries(keys).map(([k, v]) => "  " + JSON.stringify(k) + ": " + JSON.stringify(v) + ",");
lines.splice(at + 1, 0, ...block);
let out = lines.join(eol);
// the spawn-callings line now mentions the Quirk roll
const oldCall = "otherwise a 5% chance that a Miraculous does (Tikki, Plagg, Trixx or Nooroo).";
if (out.includes(oldCall)) {
  out = out.replace(oldCall, "otherwise a 5% chance that a Miraculous does (Tikki, Plagg, Trixx or Nooroo), and failing both, a 5% chance that a Quirk awakens in you.");
}
JSON.parse(out);
fs.writeFileSync(P, out);
console.log("inserted", block.length, "keys after line", at + 1, "eol", JSON.stringify(eol));
