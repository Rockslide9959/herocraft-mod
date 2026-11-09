const j = JSON.parse(require('fs').readFileSync('src/main/resources/assets/projecthero/lang/en_us.json', 'utf8'));
for (const k of process.argv.slice(2)) console.log(k + ' => ' + j[k] + '\n');
