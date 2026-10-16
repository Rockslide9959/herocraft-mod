// v0.16.5 Wind Manipulation rework: rewrites only the power_24 lang block and the message.projecthero.wind.* keys
const fs = require('fs');
const f = process.argv[2];
let s = fs.readFileSync(f, 'utf8');
const crlf = s.includes('\r\n');
const lines = s.replace(/\r\n/g, '\n').split('\n');
const P = 'projecthero.power.power_24_wind_manipulation.';
const power = {
  'name': 'Wind Manipulation',
  'desc': 'An air-bender on a Gust meter instead of long cooldowns: throw crescents of wind that cut through whole crowds, rip the air out of a spot to drag enemies in, ride the wind at your back, raise tornadoes and hurricanes, turn arrows aside, glide like an elytra and ride a tornado of your own.',
  'serum': 'Atmospheric Pressure Serum',
  'trigger': 'Drink inside an active Pressure Chamber / Industrial Fan Array.',
  'ability.wind_blade': 'Wind Blade',
  'ability.wind_blade.desc': 'R: throw a crescent of compressed air up to 30 blocks. It passes through every enemy in its path, cutting each one for 12, and breaks up on the first wall. 8 Gust. Shift+R: Blade Flurry — hold to throw 4 smaller blades a second from both hands, 6 each. 12 Gust a second.',
  'ability.vacuum': 'Vacuum',
  'ability.vacuum.desc': 'G: rip the air out of the spot you aim at (up to 18 blocks). A vortex opens there and drags every enemy within 10 blocks into its eye, then holds them there, helpless, for 1 second. No bosses. 20 Gust. Shift+G: Wind Push — a 10-block cone of wind: 6 damage and a huge shove to everything in it, plus 8 more if it slams into a wall. Projectiles in the cone are blown away. 15 Gust.',
  'ability.tailwind': 'Tailwind',
  'ability.tailwind.desc': 'X: the wind at your back throws you 14 blocks along your look in a blink, streaking the air behind you. 10 Gust. Shift+X: Updraft — a rising column throws you 12 blocks straight up, and at the top your Glide opens on its own. 15 Gust.',
  'ability.tornado': 'Tornado',
  'ability.tornado.desc': 'Z: a tornado touches down in front of you and travels where you aimed for 8 seconds. It drags in every enemy within 7 blocks, whirls each one round its funnel for 6 seconds at 4 damage a second, then flings it out high and far. 35 Gust. Shift+Z: Hurricane — a wall of wind rises round you for 5 seconds: projectiles are flung away and mobs are pushed out past it. 30 Gust.',
  'ability.wind_barrier': 'Wind Barrier',
  'ability.wind_barrier.desc': 'Hold V: a spinning swirl of air in front of you blows every arrow, fireball and other projectile coming at you back where it came from. 6 Gust a second. Shift+V: Glide (toggle) — ride the wind like an elytra: steer with your look, dive for speed, pull up to climb (on the ground you hop up first). Lands on its own, or Shift+V again to stop. Free.',
  'ability.rideable_tornado': 'Rideable Tornado',
  'ability.rideable_tornado.desc': 'C (toggle): a tornado rises under your feet and carries you where you look, bowling through every mob it meets for 6 and throwing it aside. Press C again, or Sneak, to step off — no fall damage. 8 Gust a second. Shift+C: Air Bubble — 20 seconds in a bubble of clean air: you breathe underwater, fire on you goes out, and harmful effects and gas clouds round you (yours and your squad\'s) are blown away. 20 Gust.',
  'passive.gust': 'Gust (0–100): every move spends some — Wind Blade 8, Blade Flurry 12 a second, Vacuum 20, Wind Push 15, Tailwind 10, Updraft 15, Tornado 35, Hurricane 30, Wind Barrier 6 a second, Rideable Tornado 8 a second, Air Bubble 20 — and it refills 15 a second (25 above y 100) while you are not holding a move.',
  'passive.fall': 'No fall damage',
  'passive.veer': 'Projectiles aimed at you veer off 30% of the time',
  'passive.webs': 'Cobwebs do not slow you down',
};
const msgs = {
  'message.projecthero.wind.gust_low': 'Not enough Gust — let the wind build back',
  'message.projecthero.wind.riding': 'Not while riding a tornado',
  'message.projecthero.wind.ride_on': 'Riding the tornado — it goes where you look; C or Sneak to step off',
};
const keyOf = l => { const m = l.match(/^\s*"([^"]+)"\s*:/); return m ? m[1] : null; };
const esc = v => JSON.stringify(v);
let out = [], putPower = false, putMsg = false;
for (const l of lines) {
  const k = keyOf(l);
  if (k && k.startsWith(P)) {
    if (!putPower) { putPower = true; for (const [kk, v] of Object.entries(power)) out.push('  ' + esc(P + kk) + ': ' + esc(v) + ','); }
    continue;
  }
  if (k && k.startsWith('message.projecthero.wind.')) {
    if (!putMsg) { putMsg = true; for (const [kk, v] of Object.entries(msgs)) out.push('  ' + esc(kk) + ': ' + esc(v) + ','); }
    continue;
  }
  out.push(l);
}
if (!putPower || !putMsg) { console.error('block not found', putPower, putMsg); process.exit(1); }
let r = out.join('\n');
JSON.parse(r); // still valid
if (crlf) r = r.replace(/\n/g, '\r\n');
fs.writeFileSync(f, r);
console.log('lang ok');
