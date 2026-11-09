// Removes the Bone Sword v2 filming hooks from src (the harness itself is kept as scratchpad/andy/*.java.txt).
const fs = require('fs');
const f = 'src/client/java/com/projecthero/mod/client/andy/AndyBoneSwordRender.java';
let t = fs.readFileSync(f, 'utf8').replace(/\r\n/g, '\n');
function rep(a, b) {
  if (!t.includes(a)) throw new Error('missing: ' + a.slice(0, 90));
  t = t.replace(a, b);
}
rep(`	public static volatile boolean DEBUG_OVERLAY; // TEMP-HARNESS

`, '');
t = t.replace(/\t\tif \(DEBUG_OVERLAY\) \{ \/\/ TEMP-HARNESS\n[\s\S]*?\n\t\t\}\n/, '');
rep(`		float age = FREEZE_AGE >= 0f ? FREEZE_AGE : now - s.animStart; // TEMP-HARNESS freeze`, `		float age = now - s.animStart;`);
rep(`		if (FREEZE_AGE >= 0f) { age = FREEZE_AGE; } // TEMP-HARNESS\n`, '');
rep(`\n	public static float FREEZE_AGE = -1f; // TEMP-HARNESS\n`, '\n');
rep(`	public static float STRUGGLE_OFF_SHIFT =`, `	private static final float STRUGGLE_OFF_SHIFT =`);
rep(`	public static float STRUGGLE_MAIN_SHIFT =`, `	private static final float STRUGGLE_MAIN_SHIFT =`);
if (/TEMP-HARNESS|DEBUG_OVERLAY|FREEZE_AGE/.test(t)) throw new Error('left over');
fs.writeFileSync(f, t.replace(/\n/g, '\r\n'));
console.log('ok');
