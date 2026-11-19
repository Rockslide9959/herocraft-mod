// Key-by-key en_us edit for v0.17.5 Titan charges: replaces existing keys' values in place, inserts new keys after an anchor.
const fs = require('fs');
const p = process.argv[2];
let s = fs.readFileSync(p, 'utf8');
const crlf = s.includes('\r\n');
const nl = crlf ? '\r\n' : '\n';
let lines = s.split(nl);

const esc = v => JSON.stringify(v);
function idx(key) {
	const pre = '  ' + esc(key) + ':';
	return lines.findIndex(l => l.startsWith(pre));
}
function setVal(key, value) {
	const i = idx(key);
	if (i < 0) throw new Error('missing ' + key);
	const comma = lines[i].trimEnd().endsWith(',') ? ',' : '';
	lines[i] = '  ' + esc(key) + ': ' + esc(value) + comma;
}
function insertAfter(anchor, key, value) {
	if (idx(key) >= 0) { setVal(key, value); return; }
	const i = idx(anchor);
	if (i < 0) throw new Error('missing anchor ' + anchor);
	if (!lines[i].trimEnd().endsWith(',')) lines[i] = lines[i] + ',';
	lines.splice(i + 1, 0, '  ' + esc(key) + ': ' + esc(value) + ',');
}

const T = 'message.projecthero.titan_shifter.';
const H = 'hud.projecthero.titan_shifter.';
const G = 'projecthero.guide.titan_shifter.';

// changed values
setVal('item.projecthero.titan_serum.desc2', 'Press H afterwards to transform -- each shift spends a Titan charge (they come back one a minute).');
setVal(T + 'unlocked_hint', 'Press H to transform: each shift spends one Titan charge (your Titan stores 2-4; one comes back every minute, one at a time). With no charges left, hold H for 5 s at 30%+ Steam for a pale Emergency Titan. In human form R / G / X (and Shift) are Partial Shifting. Inside the Titan every key has a tap and a Shift move -- hold Left-Alt to see them; Shift+X is your Titan\'s signature, V roars.');
setVal(T + 'low_energy', 'Not enough Steam -- an Emergency Titan needs 30%');
setVal(T + 'bite_shift_needs_full', 'Bite Shift needs a Titan charge.');
setVal(H + 'move.shift', 'Titan Shift: 1 charge (none left: hold for an Emergency Titan)');
setVal('screen.projecthero.titan_picker.size', '%s blocks tall, %s charges');
setVal('screen.projecthero.titan_picker.size_current', 'Yours: %s tall, %s charges');
setVal(G + 'transform.body', 'Press H on open ground with at least one TITAN CHARGE (see below; the Titan needs room -- leaves and plants in the way are blown aside, solid blocks are not). With no charges left \"No Titan charges\" flashes in red above your hotbar with the seconds until the next one; you can still HOLD H for an Emergency Titan (see below). Lightning flashes without harm, bystanders are shoved back, and for about 3 seconds you are helpless while the Titan swells up around you. Press H again to change back: the Titan stops and steams for about 3 seconds (it cannot be hurt meanwhile) while you climb out through the back of its neck; then you drift down and its body slumps where it stood, steams and dissolves over one minute. Squad-mates can right-click the Titan to ride its shoulders (two seats, sneak to hop off). Hold Sprint while walking for 3 seconds and the Titan breaks into a run. Press N to grab the mob you are looking at, N again to EAT it (26 damage, 10 s recharge; it fills your hunger and the Titan heals 5 HP per second for 5 seconds), Shift+N to set it gently down.');
setVal(G + 'energy.body', 'A 100-point bar (yellow, with a percentage, on your HUD) -- it was called Titan Energy before v0.16.7. Since v0.17.5 a normal shift no longer needs it (Titan charges decide that); Steam is your Titan\'s fuel. It refills 1% a second outside the Titan. Leaving the Titan REFUNDS Steam by the Titan\'s remaining health: change back at full HP and you keep the whole bar, at half HP you keep half. Only a DEFEAT (0 HP) or being CUT OUT of the nape empties it. Inside the Titan, out of combat (no hit taken or dealt for 5 s), the Titan heals about 2 HP a second by burning Steam (0.1 per HP), and regrowing a limb costs Steam -- so a Titan shifted with an empty bar cannot heal itself. Partial Shifting moves cost a little Steam each, and an Emergency Titan needs 30%. Your human form heals itself with Regeneration II from its OWN small healing pool (the green bar under Steam), never from Steam: it drains while you heal and refills 1 a second otherwise. Shift+N in human form switches that healing off or on.');
setVal(G + 'emergency.body', 'Out of Titan charges you are not stuck: with at least 30% Steam, press and HOLD H for 5 seconds (electricity crackles around you and a countdown shows above your hotbar; let go -- or open a chest or other screen -- and it is cancelled, with a message saying so). It costs no charge, but the charge that was regenerating starts over from zero. You then shift into an Emergency Titan of your type -- PALE, about 60% of its height and 40% weaker in everything (health, armour, speed and the damage of every hit). You can stay in it for at most 2 minutes: a thin pale bar above the title slowly drains, and when it empties you are forced out (H changes back earlier, as usual). Afterwards you are exhausted (\"Titan Form Exhausted\" shows on your HUD until Steam is back at 100%): Steam refills 3 times slower, there is no passive human healing until you are back at 50%, and you cannot make another Emergency Titan until the bar is full. Charges still come back as usual meanwhile, and a charge still lets you shift normally.');
setVal(G + 'human.body', 'In human form (with no mutation selected) R, G and X shift part of your body. R TITAN ARM: your arm swells into a Titan\'s for one punch down your look -- 16 and a huge shove, 6 blocks, 4 Steam, 3 s. Shift+R CRYSTAL BLADE: a hardened forearm blade slashes the front (12, 2 Steam, 1.5 s). G STEAM BURST: a scalding cloud knocks everything within 5 blocks away (4, 3 Steam, 6 s). Shift+G BITE SHIFT: bite your hand -- an instant shift (0.6 s) that spends a Titan charge, like H. X CRYSTAL WALL: a 4x4 crystal wall two blocks ahead for 25 s -- it stops mobs, players and arrows, never touches the blocks around it, and works on every server (8 Steam, 20 s). Shift+X CRYSTAL COCOON: encased for 5 s -- nothing can hurt you, you cannot move, and you heal fast (15 Steam, 45 s).');
setVal(G + 'commands.body', '/projecthero power grant titan_shifter [player]  (op level 2) -- grants the Attack Titan; hand the player a Titan Serum to pick another. /projecthero power tune titan charges <n> sets your Titan charges, tune titan chargetime <seconds> sets the time to the next one, tune titan status shows them. Every number above is in config/projecthero_titan_shifter.json (the charge counts and the 60 s timer are in its \"charges\" section).');

// new keys
insertAfter(T + 'low_energy', T + 'no_charges', 'No Titan charges -- the next one is back in %ss.');
insertAfter(T + 'no_charges', T + 'charge_ready', 'Titan charge restored (%s/%s)');
insertAfter(H + 'energy_label', H + 'charges_label', 'Charges');
insertAfter(H + 'charges_label', H + 'next_charge', 'Next charge');
insertAfter(H + 'drained', H + 'no_charges', 'No Titan charges');
insertAfter(G + 'transform.body', G + 'charges', 'Titan charges');
insertAfter(G + 'charges', G + 'charges.body', 'Every shift into your Titan (H, or Bite Shift) spends ONE Titan charge. While you have a charge left you can shift again straight away -- change back and shift again as often as your charges allow. Spent charges come back ONE AT A TIME: the first takes 60 seconds, and only then does the next one start (two spent = one back after a minute, the other a minute after that). The timer only runs in human form: inside the Titan it pauses (it keeps its progress) and carries on when you are out. Your charges and the timer are kept when you log out, die or change world -- dying does not refill them. Re-forging into another Titan with a serum keeps your charges, cut down to the new Titan\'s maximum. The HUD shows one gold pip per charge (the regenerating one fills from the bottom) and, while one is missing, a thin bar with the seconds to the next. A defeat or being cut out still locks shifting for 60 s, charges or not.');
insertAfter(G + 'charges.body', G + 'charges.counts', 'How many each Titan stores -- the tougher the Titan, the fewer: FEMALE TITAN 4 (light and fast, built for hit-and-run shifting), ATTACK TITAN 3, BEAST TITAN 2 (long range and 600 HP), ARMOURED TITAN 2 (800 HP behind crystal plates).');

fs.writeFileSync(p, lines.join(nl));
JSON.parse(fs.readFileSync(p, 'utf8'));
console.log('en_us ok');
