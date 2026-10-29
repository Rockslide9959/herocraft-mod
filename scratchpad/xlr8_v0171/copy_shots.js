// Copies the final harness shots into docs/screenshots/v0171 as omnitrix_xlr8v2_*.png / omnitrix_vkey_*.png
'use strict';
const fs = require('fs'), path = require('path');
const root = path.join(__dirname, '..', '..');
const out = path.join(root, 'docs', 'screenshots', 'v0171');
fs.mkdirSync(out, { recursive: true });
let n = 0;
const take = (dir, prefix) => {
	for (const f of fs.readdirSync(dir)) {
		if (f.startsWith('omnitrix_look_' + prefix)) {
			fs.copyFileSync(path.join(dir, f), path.join(out, 'omnitrix_' + f.slice('omnitrix_look_'.length)));
			n++;
		}
	}
};
take(path.join(root, 'run', 'screenshots'), 'xlr8v2_');
take(path.join(__dirname, 'shots1'), 'vkey_');
console.log('copied', n);
