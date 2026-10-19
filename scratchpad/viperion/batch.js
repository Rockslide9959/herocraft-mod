// puts each Second Chance gametest in its own batch (their 32-block rewinds reach the neighbouring tests)
'use strict';
const fs = require('fs');
const F = 'src/gametest/java/com/projecthero/mod/gametest/ViperionGameTests.java';
let s = fs.readFileSync(F, 'utf8');
for (const t of ['checkpointAndRewindRestoreHimAndAMob', 'aKillingBlowRewindsInstead', 'threeRewindsThenSpent', 'theNinetySecondWindowCloses', 'rewindBringsBackTheDeadAndTheBlocks']) {
	const re = new RegExp('@GameTest\\(template = EMPTY_STRUCTURE(, timeoutTicks = \\d+)?\\)(\\s+public void ' + t + '\\()');
	if (!re.test(s)) throw new Error('no ' + t);
	s = s.replace(re, (m, to, rest) => '@GameTest(template = EMPTY_STRUCTURE' + (to || '') + ', batch = "viperion_' + t + '")' + rest);
}
fs.writeFileSync(F, s);
console.log('ok');
