// v0.17.2: one-off -- stub kits (server + client) + test classes for the Recalibrated Omnitrix's ten, and their registration
// lines, so the parallel builds only ever touch their own files. Skips any file that already exists.
const fs = require('fs'), path = require('path');
const A = [['swampfire', 'SWAMPFIRE', 'Swampfire'], ['echoecho', 'ECHO_ECHO', 'EchoEcho'], ['humungousaur', 'HUMUNGOUSAUR', 'Humungousaur'],
	['jetray', 'JETRAY', 'Jetray'], ['bigchill', 'BIG_CHILL', 'BigChill'], ['chromastone', 'CHROMASTONE', 'Chromastone'],
	['brainstorm', 'BRAINSTORM', 'Brainstorm'], ['spidermonkey', 'SPIDERMONKEY', 'Spidermonkey'], ['goop', 'GOOP', 'Goop'], ['alienx', 'ALIEN_X', 'AlienX']];
const MAIN = 'src/main/java/com/projecthero/mod/omnitrix/alien', CLI = 'src/client/java/com/projecthero/mod/client/omnitrix/alien';
const put = (f, s) => { if (!fs.existsSync(f)) { fs.mkdirSync(path.dirname(f), { recursive: true }); fs.writeFileSync(f, s.replace(/\n/g, '\r\n')); console.log('wrote', f); } };
for (const [pkg, C, N] of A) {
	put(`${MAIN}/${pkg}/${N}Kit.java`, `package com.projecthero.mod.omnitrix.alien.${pkg};

import com.projecthero.mod.omnitrix.alien.Aliens;

/**
 * v0.17.2: the Recalibrated Omnitrix's ${N} -- STUB until its kit is built (the alien stays "coming soon" on the dial while
 * {@link #register} registers nothing). Replace this class with the real {@code AlienKit}.
 */
public final class ${N}Kit {
	public static final String ID = Aliens.${C}.id();

	private ${N}Kit() {
	}

	/** One line in {@code OmnitrixMod.initialize}: {@code Aliens.register(new ${N}Kit())} once the kit exists. */
	public static void register() {
	}

	/** From {@code OmnitrixMod.clearSessionState} (the ServerStateReset hook): clear every static per-player map. */
	public static void clearSessionState() {
	}
}
`);
	put(`${CLI}/${N}Client.java`, `package com.projecthero.mod.client.omnitrix.alien;

/**
 * v0.17.2: the Recalibrated Omnitrix's ${N}, client side -- STUB until its client kit is built. Replace with a class that
 * implements {@code AlienClientKit} and registers itself here with {@code AlienClients.register(Aliens.${C}.id(), ...)}.
 */
public final class ${N}Client {
	private ${N}Client() {
	}

	/** One line in {@code OmnitrixClient.initialize}. */
	public static void register() {
	}
}
`);
}
const T = 'src/gametest/java/com/projecthero/mod/gametest';
const groups = ['RecalibrationGameTests', 'RecalAGameTests', 'RecalBGameTests', 'RecalCGameTests', 'RecalAlienXGameTests'];
for (const g of groups) put(`${T}/Omnitrix${g}.java`, `package com.projecthero.mod.gametest;

/** v0.17.2: the Recalibrated Omnitrix's gametests (see docs/OMNITRIX_RECALIBRATED_0172.md). Placeholder until filled. */
public final class Omnitrix${g} {
}
`);
function ins(file, after, lines) {
	let s = fs.readFileSync(file, 'utf8'); const crlf = s.includes('\r\n'); s = s.replace(/\r\n/g, '\n');
	if (s.includes(lines[0].trim())) return;
	const i = s.indexOf(after); if (i < 0) throw new Error('anchor ' + after + ' in ' + file);
	const j = s.indexOf('\n', i) + 1; s = s.slice(0, j) + lines.join('\n') + '\n' + s.slice(j);
	fs.writeFileSync(file, crlf ? s.replace(/\n/g, '\r\n') : s); console.log('edited', file);
}
ins('src/main/java/com/projecthero/mod/omnitrix/OmnitrixMod.java', 'com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakKit.register();',
	['\t\t// v0.17.2: the Recalibrated Omnitrix\'s ten (Ben 10: Alien Force)', ...A.map(([p, C, N]) => `\t\tcom.projecthero.mod.omnitrix.alien.${p}.${N}Kit.register();`)]);
ins('src/main/java/com/projecthero/mod/omnitrix/OmnitrixMod.java', 'com.projecthero.mod.omnitrix.alien.xlr8.Xlr8Kit.clearSessionState();',
	A.map(([p, C, N]) => `\t\tcom.projecthero.mod.omnitrix.alien.${p}.${N}Kit.clearSessionState(); // v0.17.2`));
ins('src/client/java/com/projecthero/mod/client/omnitrix/OmnitrixClient.java', 'AlienClients.register(Aliens.GHOSTFREAK.id(), new com.projecthero.mod.client.omnitrix.alien.GhostfreakClient());',
	['\t\t// v0.17.2: the Recalibrated Omnitrix\'s ten', ...A.map(([p, C, N]) => `\t\tcom.projecthero.mod.client.omnitrix.alien.${N}Client.register();`)]);
ins('src/gametest/resources/fabric.mod.json', '"com.projecthero.mod.gametest.OmnitrixAutoGameTests",',
	groups.map(g => `      "com.projecthero.mod.gametest.Omnitrix${g}",`));
