// v0.17.3 Saiyan: the placeholder vanilla sounds swapped for SaiyanSounds (run from the repo root).
const fs = require('fs');
function edit(f, pairs) {
  let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
  for (const [a, b] of pairs) { if (!t.includes(a)) throw new Error(f + ' missing: ' + a.slice(0, 100)); t = t.replace(a, b); }
  fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
}
const S = 'src/main/java/com/projecthero/mod/saiyan/';
edit(S + 'Saiyan.java', [
  [`			// TODO(sounds): SaiyanSounds charge loop -- placeholder vanilla sound
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6f, 1.5f);`,
   `			// the power-up swell; the loop that follows while Q is held is the client's (SaiyanLoopSounds)
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SaiyanSounds.KI_CHARGE_START, SoundSource.PLAYERS, 0.7f, 1.0f);`],
  [`		// TODO(sounds): SaiyanSounds awaken -- placeholder vanilla sounds
		level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6f, 1.6f);`,
   `		level.playSound(null, c.x, c.y, c.z, SaiyanSounds.TRANSFORM_BURST, SoundSource.PLAYERS, 0.8f, 1.1f);`],
  [`		// TODO(sounds): SaiyanSounds power down -- placeholder vanilla sound
		level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7f, 1.4f);`,
   `		level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SaiyanSounds.POWER_DOWN, SoundSource.PLAYERS, 0.8f, 1.0f);`],
  [`		// TODO(sounds): SaiyanSounds transform per form -- placeholder vanilla sounds
		level.playSound(null, c.x, c.y + 1.0, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5f, 1.8f);
		level.playSound(null, c.x, c.y + 1.0, c.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 0.6f);`,
   `		// one burst for every form, pitched up a little the higher the form; Super Saiyan 2's lightning crackles on top
		level.playSound(null, c.x, c.y + 1.0, c.z, SaiyanSounds.TRANSFORM_BURST, SoundSource.PLAYERS, 0.8f, 0.95f + f.ordinal() * 0.02f);
		if (f == SaiyanForm.SSJ2) {
			level.playSound(null, c.x, c.y + 1.0, c.z, SaiyanSounds.SSJ2_SPARK, SoundSource.PLAYERS, 0.7f, 1.0f);
		}`],
  [`		level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.0f, 0.7f);`,
   `		level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SaiyanSounds.EXHAUSTED, SoundSource.PLAYERS, 0.8f, 1.0f);`],
]);
edit(S + 'SaiyanDamage.java', [[`		// TODO(sounds): SaiyanSounds instinct dodge -- placeholder vanilla sound
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.4f, 1.9f);`,
  `		level.playSound(null, c.x, c.y, c.z, SaiyanSounds.INSTINCT_DODGE, SoundSource.PLAYERS, 0.7f, 1.0f);`]]);
edit(S + 'SaiyanFlight.java', [
  [`		// TODO(sounds): SaiyanSounds dash -- placeholder vanilla sound
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.6f);`,
   `		level.playSound(null, c.x, c.y, c.z, SaiyanSounds.KI_DASH, SoundSource.PLAYERS, 0.7f, 1.0f);`],
  [`		// TODO(sounds): SaiyanSounds take-off -- placeholder vanilla sound
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.3f, 1.8f);`,
   `		level.playSound(null, c.x, c.y, c.z, SaiyanSounds.KI_DASH, SoundSource.PLAYERS, 0.45f, 0.8f); // the take-off: a softer, lower dash`],
]);
console.log('ok');
