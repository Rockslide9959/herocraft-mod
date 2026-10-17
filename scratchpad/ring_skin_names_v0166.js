// v0.16.6: which offline usernames get a slim / wide default skin (UUID.nameUUIDFromBytes("OfflinePlayer:"+name),
// DefaultPlayerSkin index = floorMod(uuid.hashCode(), 18), indices 0-8 slim, 9-17 wide).
const crypto = require('crypto');
function idx(name) {
	const h = crypto.createHash('md5').update('OfflinePlayer:' + name).digest();
	h[6] = (h[6] & 0x0f) | 0x30;
	h[8] = (h[8] & 0x3f) | 0x80;
	const msb = h.readBigInt64BE(0), lsb = h.readBigInt64BE(8);
	const x = msb ^ lsb;
	const hc = Number(BigInt.asIntN(32, x >> 32n) ^ BigInt.asIntN(32, x));
	return ((hc % 18) + 18) % 18;
}
for (const n of process.argv.slice(2)) console.log(n, idx(n), idx(n) < 9 ? 'slim' : 'wide');
