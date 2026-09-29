// v0.13.17: Oathbreaker damage buff (~+30% on every move) + phase scaling (x1.10 / x1.25), per the user: he is
// meant to be a stronger boss than the Abyssal Behemoth.
const fs = require('fs');
const file = 'src/main/java/com/projecthero/mod/oathbreaker/OathbreakerTuning.java';
let s = fs.readFileSync(file, 'utf8');
const nl = s.includes('\r\n') ? '\r\n' : '\n';
s = s.replace(/\r\n/g, '\n');
const vals = {
  SHOCKWAVE_DAMAGE: '8.0f', SOUL_SPEAR_DAMAGE: '13.0f', STANCE_DAMAGE: '26.0f', COMBO_DAMAGE_PER_HIT: '9.0f',
  RIPOSTE_DAMAGE: '17.0f', LEAP_DAMAGE: '18.0f', SOUL_REND_DAMAGE: '12.0f', JUDGEMENT_DAMAGE_CENTER: '31.0f',
  JUDGEMENT_DAMAGE_EDGE: '9.0f', JUDGEMENT_RING_DAMAGE_PER_SECOND: '4.0f', EXECUTION_DAMAGE: '36.0f',
};
for (const [k, v] of Object.entries(vals)) {
  const re = new RegExp('(public static final float ' + k + ' = )([0-9.]+f);( // .*)?$', 'm');
  const m = s.match(re);
  if (!m) throw new Error('missing ' + k);
  const hist = (m[3] || '').replace(/^ \/\/ /, '');
  s = s.replace(re, '$1' + v + '; // v0.13.17: ' + m[2].replace('f', '') + ' -> ' + v.replace('f', '') + (hist ? ' (' + hist + ')' : ''));
}
const anchor = '	// ---------------- stats ----------------\n';
if (!s.includes(anchor)) throw new Error('no anchor');
s = s.replace(anchor,
  '	// v0.13.17: every damage number up ~30% again, plus phase scaling below -- he is meant to be a harder fight than the\n' +
  '	// Abyssal Behemoth. Still well under the v0.13.9 numbers that made him overwhelming once his hits landed.\n\n' +
  '	/** v0.13.17: all his damage is multiplied by this in phase 2 ("Forsworn") and phase 3 ("Oathless"), like the Behemoth. */\n' +
  '	public static final float PHASE_2_DAMAGE_MULTIPLIER = 1.10f;\n' +
  '	public static final float PHASE_3_DAMAGE_MULTIPLIER = 1.25f;\n\n' + anchor);
fs.writeFileSync(file, s.replace(/\n/g, nl));
console.log('ok');
