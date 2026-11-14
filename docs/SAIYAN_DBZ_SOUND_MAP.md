# Saiyan: Dragon Ball clip map (v0.17.4, test builds only)

The user cut 57 clips from the show into `src/main/resources/assets/projecthero/sounds/saiyan/dbz/` (git-ignored: copyrighted
show audio, public repo). The user's rule: **real clips in local test builds only, synthesised sound-alikes before the public
release.** This file is the to-do list for that swap.

## How it is wired

- `SaiyanSounds` registers every Saiyan sound event (the core's and the move kit's; `kit/SaiyanKitSounds` only names them).
- **Public set (committed):** `assets/projecthero/sounds.json` points every event at the v0.17.3 sounds -- the synthesised
  `sounds/saiyan/*.ogg` (`scratchpad/synth_saiyan.js`) or a vanilla file, with the old pitch/volume baked in. A CI or CurseForge
  build only ever has this set, so it can never ship a reference to a missing file.
- **Test set:** `resourcepacks/saiyan_dbz/assets/projecthero/sounds.json` (committed, it only names files) replaces every event
  with the cuts below. `client/saiyan/SaiyanDbzSounds` registers that built-in pack (always on) only when
  `sounds/saiyan/dbz/cut_aura_loop.ogg` is in the jar, i.e. only in a build made from a checkout that has the clips.
- `scratchpad/saiyan/dbz_cut.js` makes every `cut_*.ogg` from the user's clips (trim, 4 ms fade-in, fade-out, loudness
  normalised, mono 44.1 kHz Vorbis q5; loops crossfaded so the end flows into the start). Re-run it in any checkout that has the
  clips: `node scratchpad/saiyan/dbz_cut.js`. `scratchpad/saiyan/dbz_sounds_json.js` writes both sounds.json sets.

## The swap before a public release

For each row: make a synthesised OGG that matches the description, put it in `sounds/saiyan/`, point the event at it in the main
`sounds.json` (replacing the v0.17.3 stand-in), and when every row is done delete `resourcepacks/saiyan_dbz/` and
`client/saiyan/SaiyanDbzSounds` (and its call in `SaiyanClient`). The clips themselves never leave the user's machine.

## Map

Times are seconds into the user's clip. "Loop" = crossfaded loop (crossfade length in brackets).

| Event | Used for | Clip -> cut | Trim | A synthesised replacement must sound like |
|---|---|---|---|---|
| `saiyan_aura_loop_gold` | Super Saiyan aura hum, looped while transformed (the user: "the super saiyan aura should be the charge sound effect") | `charging_up` -> `cut_aura_loop` | 1.66-3.00, followed by itself reversed, loop (0.3) | A steady roaring energy rush: dense bright hiss over a low rumbling body, slowly churning, no clear pitch; seamless 2-3 s loop |
| `saiyan_aura_loop_red` | Kaioken aura hum | `charging_up` -> `cut_aura_loop` at pitch 0.88 | as above | The same roar, darker and lower, with a slight crackle |
| `saiyan_ki_charge_start` | V power-up start (also Zenkai and the Ultra Instinct unlock, pitched up) | `charging_up_2` -> `cut_charge_start` | 0.00-1.38 | A fast rising whoosh of energy swelling into a roar, ~1.4 s, ends at full intensity (the loop takes over) |
| `saiyan_ki_charge_loop` | V power-up held (starts 1.5 s in) | `charging_up_2` -> `cut_charge_loop` | 1.48-7.08, loop (0.4) | A sustained, louder charging roar with a slow pulsing swell and high crackles; ~5 s loop |
| `saiyan_transform_burst` | Super Saiyan / Ultra Instinct transformation, Kaioken flaring up, the Super Saiyan hero landing | `transforming` -> `cut_transform_1`; `super_saiyan_2` -> `cut_transform_2` | 0.86-3.00; 0.90-5.00 | An explosive energy eruption: sharp crack, a shockwave boom and a roaring burst that rings out over 2-4 s |
| `saiyan_power_down` | Super Saiyan / Kaioken ending, a Spirit Bomb fizzling | `landed_untransform` -> `cut_power_down` | 0.36-1.58 | A falling, dying-down energy hum, the roar collapsing to nothing in ~1.2 s |
| `saiyan_exhausted` | Super Saiyan's 3 minutes up | (kept: synthesised v0.17.3 sound) | -- | -- |
| `saiyan_ki_dash` | Ki Dash (X), Dragon Rush start, Ki Rain leap | `time_to_move` -> `cut_dash_1`; `time_to_move_2` -> `cut_dash_2` | 0.72-1.90; 0.05-1.88 | A fast "zip" whoosh with a rising tone, the air tearing past, ~1-1.8 s |
| `saiyan_take_off` | Taking off into flight | `time_to_fly` -> `cut_take_off` | 0.52-1.00 | A short upward whoosh-burst, ~0.5 s |
| `saiyan_land` | Hero landing (light) | `perfect_landing` -> `cut_land_1`; `perfect_landing_2` -> `cut_land_2` | 0.00-1.00; 0.25-2.68 | A clean thud with a falling whistle tail and a light energy puff |
| `saiyan_land_heavy` | Hero landing from high up or as a Super Saiyan | `crash_into_ground` -> `cut_land_heavy_1`; `unperfect_landing` -> `cut_land_heavy_2` | 1.22-3.00; 0.00-1.22 | A heavy ground impact: deep boom, rubble crunch, a rumbling tail |
| `saiyan_ui_dodge` | Ultra Instinct sidestep | `dodge_attack` -> `cut_dodge_1/2/3` | 0.02-0.55; 0.68-1.03; 1.10-1.90 | A very quick, light swish (the air parting), 0.3-0.8 s, slightly different each time |
| `saiyan_punch` | Combo hits, Dragon Rush hits | `combat_fight` -> `cut_punch_1`, `cut_punch_2` | 0.86-1.27; 1.31-1.66 | A snappy, punchy cartoon hit: a sharp smack with a short low thump, ~0.4 s |
| `saiyan_punch_heavy` | Rising Dragon Kick launch, the axe kick | `punch_knockback` -> `cut_punch_heavy` | 0.53-1.88 | A big impact: crunching hit, a deep boom, a short whoosh away |
| `saiyan_finisher` | The combo's last hit, the Meteor Smash hammer, Dragon Rush's last blow | `finisher` -> `cut_finisher` | 0.38-2.50 | A huge finishing blow: heavy smack into a ringing energy burst, ~2 s |
| `saiyan_swing` | A punch or kick that misses | `swing_punch` -> `cut_swing` | 0.00-0.50 | A fast fist whoosh, ~0.4 s |
| `saiyan_flurry` | Dragon Rush's flurry (every 12 ticks) | `combat_fight` -> `cut_flurry_1`, `cut_flurry_2` | 1.70-3.00; 0.00-0.78 | A rapid rattle of many hits and whooshes blurred together |
| `saiyan_vanish` | Meteor Smash vanish, Solar Flare (pitched up) | `teleport_away` -> `cut_vanish_away` | 0.36-2.00 | The instant-transmission "vwip": a quick high zip with a shimmering tail |
| `saiyan_appear` | Meteor Smash reappearing | `teleport` -> `cut_vanish` | 0.00-0.18 | A tiny, sharp "zip" pop, under 0.2 s |
| `saiyan_ki_blast` | Ki Blast Volley shot | `ki_blast_fire` -> `cut_blast_1`; `mini_ki_blast` -> `cut_blast_2`; `pew_2` -> `cut_blast_3` | 0.21-1.00; 0.00-1.00; 0.72-1.52 | A bright energy "pew": a sharp zap with a short sizzling tail, ~0.6-0.8 s |
| `saiyan_ki_blast_hit` | A Ki blast bursting, the Kamehameha's end pulses | `ki_blast_explosion` -> `cut_blast_hit` | 0.42-1.80 | A small energy explosion: a pop and a short crackling burst |
| `saiyan_rain_shot` | Ki Rain shots | `pew` -> `cut_rain_shot` | 0.42-1.60 | A thinner, rising energy whistle-zap, ~1 s |
| `saiyan_kame_charge` | Kamehameha charge | `kamehameha_charge` -> `cut_kame_charge` | 0.50-6.00 | A gathering hum that builds in pitch and intensity for ~5.5 s, with a bright crackling top |
| `saiyan_kame_fire` | Kamehameha leaving the hands | `kamehameha_fire` -> `cut_kame_fire` | 0.75-3.20 | A massive beam release: an explosive whoosh into a roaring blast |
| `saiyan_kame_loop` | Kamehameha roar while it fires (client loop) | `kamehameha_fire` -> `cut_beam_loop` | 1.50-5.50, loop (0.4) | A sustained roaring beam: thick rushing energy with a buzzing core |
| `saiyan_boom` | Rising Dragon Kick crater, Meteor Smash crater | `boom` -> `cut_boom_1`; `explosion` -> `cut_boom_2` | 0.00-3.00; 0.78-3.50 | A deep, punchy explosion with a rumbling tail |
| `saiyan_wave` | Explosive Wave | `explosion_2` -> `cut_wave` | 0.00-3.50 | A wide energy blast: a whump outward, a long roaring rush |
| `saiyan_spirit_bomb_rise` | Spirit Bomb start (rising, arms up) | `here_goes_spirit_bomb_charge` -> `cut_sb_rise` | 1.95-7.00 (the spoken part cut off) | A rising, airy gathering swell with soft twinkling, building over ~5 s |
| `saiyan_spirit_bomb_charge` | Spirit Bomb gathering (every 3.3 s while it grows) | `charging_spirit_bomb` -> `cut_sb_charge` | 0.02-3.44 | A warm, deep humming gather with a slow shimmer, ~3.4 s |
| `saiyan_spirit_bomb_throw` | Spirit Bomb thrown | `throw_it` -> `cut_sb_throw` | 0.29-1.45 | A heavy, slow whoosh of a huge ball launching |
| `saiyan_spirit_bomb_explode` | Spirit Bomb impact (no distance roll-off) | `spirit_bomb_explosion` -> `cut_sb_explode` | 2.78-8.00 | A colossal energy explosion: a bright blast into a long roaring rumble |
| `saiyan_spirit_bomb_boom` | Spirit Bomb impact, the deep layer heard from far away | `boom_2` -> `cut_sb_boom` | 0.45-6.00 | A very deep, distant thunder-like boom rolling out |

Clips not used (voice, music, or no fitting move yet): `almost_landing`, `bird_mourning`, `blast_fire`, `blast_fire_maybe`,
`blaster_vs_blaster`, `charging_blast`, `charging_blast_ssj2`, `charging_special_beam`, `crashes`, `dodge_attacks`,
`explosion_falling_rocks`, `explosion_kind`, `get_something_then_move`, `light_saber_noise`, `light_saber_noise_2`,
`lots_of_movement`, `moving_charging_echo`, `nope_move`, `seven_dragon_balls`, `slow_motion_finisher`, `some_kind_of_beam`,
`special_beam_cannon`, `teleport_away_2`.

## Notes on the trims

The clips were checked with spectrograms and silence detection (no listening was possible while cutting). Several are splices of
two or three show moments separated by a hard silence; each cut keeps the one segment that is the effect. Starts were cut where
a clip opens with what looks like speech (`here_goes_spirit_bomb_charge` 0-1.9 s, `kamehameha_charge` 0-0.5 s, `throw_it`
0-0.29 s, `spirit_bomb_explosion` 0-1.35 s, `explosion` 0-0.78 s). If a cut still has a voice in it, adjust its times in
`dbz_cut.js` and re-run it.

Test films with the clips laid under them (`docs/screenshots/v0174/saiyan/*_sound.mp4`, made by
`scratchpad/saiyan/v0174_films.js` from the sounds the harness logged) carry show audio too, so they are git-ignored as well.
