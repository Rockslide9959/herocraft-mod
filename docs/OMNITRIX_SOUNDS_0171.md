# Omnitrix + alien sound rework (v0.17.1)

The user said the demo sounds were "like tring sounds and loud and annoying" and asked for something closer to the show.
This page has three parts: an audit of the old sounds, the new design, and the waveform numbers before and after. The user's
own transform / power-down recordings and the synced detransform came after; they are in section 0.

## 0. The user's transform and power-down sounds, and the synced detransform

The user supplied two recordings for their test build: `ben-10-som-de-transformar.mp3` (transforming) and
`omnitrix-power-down.mp3` (detransforming: five beeps, then the power dying). Neither MP3 is committed.

**Conversion** (Medal's libvorbis `ffmpeg7.exe`): stereo averaged to mono (positional sounds must be mono), resampled to
44.1 kHz, leading silence trimmed (`silenceremove`, -50 dB), peaks normalised to about -6 dBFS, Vorbis q5:

| file | source length / peak | trimmed | gain | shipped |
|---|---|---|---|---|
| `sounds/omnitrix/transform_user.ogg` | 2.33 s, -9.4 dBFS (mono) | 0.50 s | +3.4 dB | 1.83 s, -6 dBFS |
| `sounds/omnitrix/power_down_user.ogg` | 4.71 s, -3.1 dBFS | 0.11 s | -2.9 dB | 4.60 s, -6 dBFS |

Events `projecthero:omnitrix_transform_user` / `omnitrix_power_down_user` (subtitles "Omnitrix transforms" / "Omnitrix beeps and
powers down"), Cues `TRANSFORM_USER` / `POWER_DOWN_USER` at 0.7 with **no pitch jitter** (a pitch change changes the playback
speed and would break the sync). Both play **on the wearer** (`OmnitrixSfx.playOn`, an entity-bound sound that follows a running
alien), positionally for everyone in range. They replace the old `TRANSFORM` cue and the `REVERT` / `TIMEOUT` cues for V and the
timeout; an *immediate* revert (suppression, the pod, a revoke, a world change) keeps the short `REVERT` / `TIMEOUT` whir, and an
interrupted power-down (death, logout, a world change, suppression) sends a stop-sound packet for `omnitrix_power_down_user` to
everyone within 48 blocks.

### Measured onsets (`scratchpad/detect_omnitrix_beeps.js`, on the decoded shipped OGG)

The power-down has a continuous low drone (215 Hz falling to 108 Hz) and a steady 5.3 kHz whine under the beeps, so broadband
RMS cannot see them (`scratchpad/analyse_user_omnitrix_audio.js` prints that envelope; `scratchpad/spectro_user_omnitrix_audio.js`
the coarse spectrogram used to find the bands). The detector: a 2048-point Hann STFT every 5 ms; the beeps are tones from 1.6 kHz
falling to ~0.9 kHz, so a beep onset is the first frame where the 600-2000 Hz band rises more than 10 dB above its previous-60 ms
minimum (and 8 dB over the band's median), refined to the halfway point (in dB) of the rise. The power-down onset is the first
frame after the last beep where the 0-150 Hz band jumps more than 6 dB over its previous-100 ms mean (the watch's clunk; 5-22 kHz
collapses at the same moment); its strongest part is the loudest 50 ms after that.

| event | shipped OGG | tick (x20, rounded) | original MP3 |
|---|---|---|---|
| beep 1 | 0.050 s | 1 | 0.160 s |
| beep 2 | 0.469 s | 9 | 0.579 s |
| beep 3 | 0.958 s | 19 | 1.073 s |
| beep 4 | 1.487 s | 30 | 1.596 s |
| beep 5 | 2.015 s | 40 | 2.130 s |
| power-down onset (the clunk) | 2.724 s | 54 | 2.834 s |
| power-down loudest 50 ms | 2.829-2.879 s | 57 | 2.943 s |
| **the revert** (`Omnitrix.POWER_DOWN_REVERT_TICK`) | 2.80 s | **56** | |

After beep 5 the tone sweeps down (970 to 430 Hz) into the clunk. The revert is on tick 56 so the red flash's hot opening (its
first 2.5 ticks) covers the loudest part; the body swap and the flash share the tick.

### The detransform sequence (server-authoritative, `Omnitrix.beginPowerDown`)

V while transformed (Shift+V is an alias) and the 3-minute timeout both start it: timers `omnitrix.power_down` (the start's game
time) and `omnitrix.power_down_timeout` (1 = the timeout) go into the synced state and the sound starts. On each beep tick
(`POWER_DOWN_BEEPS` = 1, 9, 19, 30, 40 after the start) every client blinks the watch red for 4 ticks (`Omnitrix.beepBlink`): the
wrist face and the alien's chest badge (`OmnitrixWatch.red`, plus a red glow on the wrist) and the HUD's Time bar (it flashes red,
five pips light one per beep, the icon goes red). On tick 56 the server reverts (the red Kirby-dot flash, the body swap, the 90 s /
10 s recharge as before). During the beeps the wearer is still the alien and can fight (the kit keeps ticking); the time limit
stops (`Omnitrix.displayLeft` holds it; 0:00 for the timeout); the 15-second warning blips stop; a second V / Shift+V does
nothing. Death and logout revert at once (no recharge, as before); a world change reverts at once with the recharge it was heading
for; none leaves power-down state behind. A save loaded mid-sequence reverts on the next tick. Tests: `OmnitrixPowerDownGameTests`.

### Transform sync

The transform recording has no sharp hit: it swells from 0.07 s and is loudest at 0.28 s (tick 5.6). So the sound now starts
**with the slap**, and the body changes `SLAP_TICKS` (5) later, on its loudest part; no visual timing was shifted. A direct
transform (not through the slap) plays it on the transform tick.

## 1. Audit: every Omnitrix / alien sound event before the rework

There were 69 events, every one a synthesised OGG under `sounds/omnitrix/`. The generators were
`scratchpad/synth_omnitrix_sounds.js` (the watch, Heatblast and Four Arms), `synth_omnitrix_wave_a_sounds.js` (XLR8,
Diamondhead, Upgrade and Ghostfreak) and `synth_omnitrix_aliens_b_sounds.js` (Stinkfly, Wildvine, Ripjaws and Grey Matter).
Every file was normalised to a 0.75-0.95 peak (-2.5 to -0.4 dBFS). Most were then played at volume 0.8-1.6.

In the "Repeats" column, *move* means once per use of a move. A tick cadence such as *every 20 t* means the sound plays
again every N ticks while something lasts.

| Event | Where it played | Old vol / pitch | Repeats | What it sounded like (from the generator) |
|---|---|---|---|---|
| omnitrix_slap | `Omnitrix.slap`, `grant`, plus *every* transform (`helixFx`) | 1.0 / 0.8-1.0 | move | noise click + 140 Hz sine thump + **880→1780 Hz sine chirp** |
| omnitrix_core_up | `Omnitrix.activate` (V) | 0.9 / 1.0 | move | click, hiss, **330 Hz sine with 2 partials rising** + lock click |
| omnitrix_core_down | `Omnitrix.deactivate` | 0.8 / 1.0 | move | **700 Hz falling sine** + click + sine thump |
| omnitrix_dial | `cycle` (scroll), and *also* used as the refusal sound in `transform` and `tellRejected` | 0.7-0.8 / 0.5-1.15 | every scroll notch | two clicks + **1500 Hz sine ping** |
| omnitrix_select | `select` (dial screen) | 0.8 / 1.0 | move | **990 + 1480 Hz sine double ping** |
| omnitrix_transform | `helixFx` on transform *and* on every non-timeout revert, plus the pod flicker | 1.0 / 1.0 | move | noise swoosh + **five-note sine chord (220-554 Hz) blooming** + hiss |
| omnitrix_revert | *never played* (Shift+V used the transform sound) | — | — | falling noise + **660 Hz falling sine** |
| omnitrix_timeout | `helixFx` on timeout, and every pod flicker | 1.0 / 1.0 | move; flicker every few s | **three 1760 Hz square beeps** + falling square + revert |
| omnitrix_warning | timeout warning in `Omnitrix.tick`, activate-while-recharging, pod "here I am", meteor scanner | 0.4-1.0 / 0.5-1.6 | **every 20 t for the last 10 s, pitch 1.0→1.6**; pod every 40 t | **1320 Hz square beep** (piercing) |
| omnitrix_recharged | recharge done | 0.8 / 1.0 | once | **784/1047/1568 Hz sine arpeggio (ding)** |
| omnitrix_extract_pulse | pod extraction drain | 0.9 / 0.8-1.2 | every pulse | band-passed noise + **90 Hz square wobble** |
| omnitrix_self_destruct | pod start + alarm | 0.9-1.2 / 1.0+ | **every 40 t** | **700-1200 Hz square siren ×2** |
| omnitrix_extract_done | extraction success | 1.4 / 1.0 | once | sine thump + noise + the recharged **ding chord** |
| omnitrix_pod_open | pod lid opens | 1.5 / 1.0 | once | click + high-passed hiss + **220 Hz sine** |
| omnitrix_meteor | meteor impact | 4.0 / 1.0 | once | falling whistle + roar (noise) |
| heatblast_blast | fire bolt, Fire Blast, quick bolt | 0.5-1.0 / 0.7-1.4 | move | low-passed noise roar + sine thump |
| heatblast_stream | Flame Stream | 0.6-1.0 / 1.0-1.1 | **every 12 t while streaming** | low-passed noise + crackle clicks (the best of the old set) |
| heatblast_nova | Supernova, nova bursts | 0.8-1.4 / 1.0-1.4 | move | **55 Hz sine thump** + noise roar |
| heatblast_jet | Fire Jet start / boost, **flight loop** | 0.35-1.0 / 1.0-1.3 | **every 20 t while flying** | band-passed noise wobble |
| heatblast_shield | Fire Shield | 1.0 / 0.6-1.0 | move | noise roar + **160 Hz sine drone** |
| heatblast_ignite | transform-in / ignite | 1.0 / 1.0-1.6 | move | click + noise roar + sine thump |
| four_arms_punch | combo hits, grabs, haymaker | 0.7-1.2 / 0.6-1.3 | per hit | click + noise + **75 Hz sine thump** (0.92 peak) |
| four_arms_clap | Thunder Clap | **1.4** / 1.0 | move | click + noise + **50 Hz sine thump** + boom |
| four_arms_slam | Ground Slam, leaps, Earthquake | 0.7-**1.5** / 0.7-1.4 | move | **48 Hz sine thump** + rubble clicks |
| four_arms_throw | throw | 1.2 / 1.0 | move | noise whoosh + 90 Hz sine |
| four_arms_roar | roar, transform-in | 0.7-**1.6** / 0.9-1.3 | move | **85 Hz square-wave "growl"** with 6 harmonics |
| *(vanilla ANVIL_LAND)* | Four Arms' Brace stance | 0.6 / 0.6 | move | a ringing anvil |
| xlr8_dash | dashes, Mach run, hit-and-run | 0.6-1.0 / 0.8-1.8 | move | noise whoosh (fine, but loud) |
| xlr8_hit | Flurry, Spin Kick | 0.3-1.0 / 0.6-1.5 | **every 2 t (10 a second) during the flurry** | click + **160 Hz sine thump** |
| xlr8_tornado | Tornado | 0.8-1.2 / 0.9-1.4 | every 20 t | wobbling noise + low rumble |
| xlr8_burst | Sonic burst | 1.2 / 1.0 | move | noise swell + **900 Hz falling sine** + thump |
| xlr8_visor | visor toggle | 0.6-1.0 / 0.9-1.4 | move | clicks + **1200→2800 Hz sine chirp** |
| diamondhead_shard | shard shots, barrage | 0.7-1.0 / 0.6-1.8 | every 6 t in the barrage | **2.6/3.5/4.2 kHz bell partials** + whoosh |
| diamondhead_grow | spikes, walls, armour, ten moves | 0.6-**1.2** / 0.7-1.6 | every other spike step | **30 random high bell "chimes"** + noise + thump |
| diamondhead_shatter | shard / spear hits, armour off | 0.4-1.0 / 0.9-1.9 | **per projectile hit** | click + **40 random bell chimes** + hiss |
| diamondhead_blade | arm blade | 0.6-0.9 / 1.0-1.3 | move | narrow noise + **1568/2349 Hz bell chimes** |
| upgrade_beam | optic beam, Overload | 0.9-**1.3** / 0.6-1.3 | move | **1400 Hz sawtooth laser** + noise |
| upgrade_charge | charge | 1.0 / 1.0 | move | **sawtooth sweeping 200→1200 Hz** |
| upgrade_merge | merge into mobs / tech | 0.7-1.1 / 0.7-1.5 | move | liquid noise + **six square-wave chirps** |
| upgrade_emp | EMP pulses | 0.9-**1.4** / 0.9-1.3 | move | sine thump + crackle + **120 Hz square drone** |
| upgrade_slide | goo slide in / out | 0.7-1.0 / 0.7-1.6 | move | narrow noise + 110 Hz wobbling sine |
| ghostfreak_claw | claw | 0.5-1.0 / 0.9-1.6 | move | noise whoosh + 300 Hz sine |
| ghostfreak_scream | scream | 1.0-**1.3** / 0.6-1.0 | move | **700-1200 Hz sawtooth "voice"** (shrill) |
| ghostfreak_phase | phase in / out | 0.6-1.0 / 0.7-1.4 | move | falling noise + **440/466 Hz sine beating** |
| ghostfreak_laugh | transform-in, possess, finale | 0.7-**1.2** / 0.7-1.3 | move | five **sawtooth "ha"s** |
| ghostfreak_tendril | tendrils | 0.6-1.1 / 0.8-1.5 | per tendril | low noise + 70 Hz sine |
| stinkfly_buzz | flight, dash, dive, spin | 0.25-**1.2** / 0.6-1.4 | **every 16 t while flying** | **190 Hz square-wave buzz** |
| stinkfly_spit | slime / goo shots | 0.6-1.0 / 0.6-1.4 | move | goo noise + **420 Hz falling sine** |
| stinkfly_splat | slime hits, storm | 0.6-**1.3** / 0.6-1.3 | every 30 t in the storm | sine thump + goo noise |
| stinkfly_gas | gas cloud | 1.0 / 0.6-1.0 | move | hiss + rumble (noise) |
| stinkfly_sting | sting | 1.0 / 1.0 | move | noise sweep + click + goo |
| stinkfly_dive | dive bomb | 1.0 / 1.0 | move | rising noise + square buzz |
| wildvine_vine | vine lash, grab, swing | 0.8-1.0 / 0.7-1.2 | move | noise whoosh + click + **140 Hz square "creak"** |
| wildvine_seed | seed bomb | 1.0 / 1.0 | move | click + **500→1200 Hz sine chirp** |
| wildvine_burst | seed bomb burst | 1.0 / 0.9-1.1 | per seed | sine thump + noise + clicks |
| wildvine_roots | roots, bramble | 1.0-**1.4** / 0.6-1.2 | move | rumble + **70/110 Hz square creaks** |
| wildvine_bloom | transform-in, bloom, flower | 0.6-1.0 / 1.0-1.6 | move | **C-E-G-C sine arpeggio (pure "tring")** |
| wildvine_rustle | bush disguise on / off | 0.7-1.0 / 0.9-1.2 | move | short noise grains (fine, quiet) |
| ripjaws_bite | bite, chomp, grab | 0.8-1.2 / 0.8-1.6 | move | noise + clicks + sine thump |
| ripjaws_roar | roar, transform-in | 0.7-**1.4** / 0.7-1.4 | move | **110 Hz square growl** + sine bubbles |
| ripjaws_splash | wet / dry, dives, slams | 0.5-1.2 / 0.6-1.8 | move | noise splash + **40 tiny sine "bloops"** |
| ripjaws_slam | slam | 1.2 / 1.0 | move | **55 Hz sine thump** + splash |
| ripjaws_gasp | drying warning, choking | 0.8-0.9 / 0.9-1.1 | every choke tick (`CHOKE_EVERY`) | noise + **220 Hz sine** |
| ripjaws_whirl | whirlpool | 0.8-1.2 / 0.8-1.0 | every whirl pulse | wobbling noise + **30 sine bloops** |
| grey_matter_pinch | pinch, bite | 1.0-1.2 / 0.6-1.0 | move | click + **1800→600 Hz sine chirp** |
| grey_matter_scan | scan, analyse | 0.5-1.0 / 1.0-1.6 | move | **five rising square beeps** + a sine ping |
| grey_matter_gadget | gadgets, traps | 1.0-**1.2** / 0.5-1.3 | move | clicks + **square chirp** + clicks |
| grey_matter_leap | leaps | 0.8-1.0 / 1.0-1.6 | move | **700→2100 Hz sine chirp** + noise |
| grey_matter_repair | repair | 1.0 / 1.0 | move | ten clicks + **1568 Hz sine ding** |
| grey_matter_think | transform-in, think | 1.0 / 1.0-1.4 | move | **330/495/660 Hz sustained sine chord** |
| *(vanilla NOTE_BLOCK_BIT)* | Grey Matter's decoy | 0.6 / 1.2-1.6 | **every 10 t while the decoy lives** | a chiptune square beep |

**Why they grated.** Over half of the events had a sustained sine, square or sawtooth tone, or bell partials, at their
core. That accounts for every "tring": the dial ping, the select ping, the recharge ding, Wildvine's bloom arpeggio,
Diamondhead's chimes and Grey Matter's chord. Every file peaked within 2.5 dB of full scale. On top of that, the calls
asked for volume 1.2-1.6 on most big moves. A few sounds also repeated: XLR8's flurry played 10 times a second, and the
timeout warning climbed to a 1.6 pitch.

The hero-landing hook was checked too. Heatblast's landing (`HeroLandingStyles`, element FIRE) plays the shared
`hero_landing_impact` thud and a vanilla `FIRECHARGE_USE`. It plays no Omnitrix tone, so it is unchanged.

## 2. The new design

**Engine.** `omnitrix/OmnitrixSfx` holds the sound recipes. A **Cue** is a few layered sound events, each with its own
volume, pitch and tick delay. Almost every layer is a vanilla sample. Every Omnitrix and alien sound is now a Cue: the
watch, pod, meteor, Heatblast and Four Arms Cues live in `OmnitrixSounds`, and every other alien's Cues live in its kit,
under the same constant names as before. All gameplay calls are unchanged; only what they play changed.

Playback rules:
- **Caller volume.** It is a relative scale. Up to 1 it is used as given; above 1 it is compressed as `1 + (v - 1) * 0.35`,
  so the old 1.6 becomes 1.21. The layer volumes are the real levels: watch UI 0.08-0.38, moves 0.12-0.6.
- **Pitch.** The caller's pitch is pulled halfway back toward 1, then gets one random jitter of ±6 % by default, applied
  to the whole Cue so the layers stay in tune with each other. Every layer is clamped to the engine's 0.5-2.0.
- **Throttle.** Each Cue has a gap (default 2 ticks). It won't play again within that gap in the same 3-block cell, or
  for the same player when it's a UI sound, so nothing stacks every tick.
- **Delayed layers.** They wait in a server-tick queue, which is cleared on server stop (`OmnitrixMod.clearSessionState`).

**Synthesised files** (only for what vanilla can't do; `scratchpad/synth_omnitrix_v2_sounds.js`). None of them is a
sustained sine or bell. They use rough pulse trains with jittered pitch and width, filtered noise, sweeps, envelopes and
tanh saturation. Peaks are about -6 dBFS, with raised-cosine fades.

| File (event id) | What it is |
|---|---|
| `core_up` (`omnitrix_core_up`) | the **whirr**: an uneven 70→210 Hz pulse train ("motor"), the filter opening, and a breath of rising noise |
| `transform` (`omnitrix_transform`) | a soft noise **thump**, then a fast **whoosh-zap** (noise band-passed 350→6.8 kHz), sparse electric **crackle** riding it, and an airy tail |
| `revert` (`omnitrix_revert`) | the **power-down**: the whirr winding 230→45 Hz with the filter closing, and a soft falling zap |
| `timeout` (`omnitrix_timeout`) | the same, longer (1.2 s), with a stronger red-flash zap and the power **sputtering** out |
| `warning` (`omnitrix_warning`) | one **soft blip**: a breathy noise band at ~780 Hz around a quiet, detuned, low-passed pulse; an 8 ms attack |
| `denied` (`omnitrix_denied`, new) | a **dull low buzz**: a 98 Hz pulse train muffled to 650 Hz with a slight flutter |

**What each sound is made of now.** Layer volumes are given at caller volume 1. "Old" is the caller volume the code
used before.

| Cue | Layers (event · volume · pitch · delay in ticks) | Plays at (caller vol) |
|---|---|---|
| CORE_UP (V) | IRON_TRAPDOOR_OPEN .18 ·1.7, PISTON_EXTEND .12 ·1.9, **whirr** .38 ·1.0 @1 | 1.0 (old 0.9 × a hot file) |
| CORE_DOWN | **power-down** .18 ·1.7, PISTON_CONTRACT .12 ·1.9 @2, IRON_TRAPDOOR_CLOSE .14 ·1.6 @3 | 1.0 |
| DIAL (scroll) | LEVER_CLICK .12 ·1.9, TRIPWIRE_CLICK_ON .08 ·2.0; gap 1 | 1.0, to the player only (old 0.8) |
| SELECT | LEVER_CLICK .18 ·1.5, IRON_TRAPDOOR_CLOSE .12 ·1.9 @1 | 1.0, player only |
| SLAP (core slammed) | IRON_TRAPDOOR_CLOSE .35 ·0.8, PLAYER_ATTACK_KNOCKBACK .3 ·0.7, PISTON_CONTRACT .15 ·1.4 | 1.0 |
| TRANSFORM | PLAYER_ATTACK_KNOCKBACK .3 ·0.55 (thump), **sweep** .55 | 1.0 (the extra SLAP on every transform was dropped) |
| REVERT (Shift+V etc.) | **power-down** .45, PISTON_CONTRACT .12 ·1.6 @8 | 1.0 (was the transform sound) |
| TIMEOUT | **long power-down** .5, IRON_TRAPDOOR_CLOSE .16 ·0.9 @20 | 1.0 |
| FLICKER (pod fights back) | **sweep** .22 ·1.3, **power-down** .18 ·1.4 @3; gap 6 | 1.0 (was the full timeout) |
| GRANT (watch jumps on) | IRON_TRAPDOOR_CLOSE .35 ·0.8, PLAYER_ATTACK_KNOCKBACK .3 ·0.7, **whirr** .35 ·0.9 @2, **sweep** .3 ·1.2 @8 | 1.0 (was SLAP + BEACON_ACTIVATE 0.8) |
| WARNING | **blip** .32; gap 4 | player only; once a second, **twice a second in the last 5 s**, pitch only 1.0→1.3 (was 1.0→1.6) |
| DENIED (recharging / held / rejected) | **buzz** .35, LEVER_CLICK .08 ·0.7; gap 6 | player only (replaces the dial / warning at pitch 0.5) |
| RECHARGED | **whirr** .3 ·1.25, LEVER_CLICK .14 ·1.6 @6 | player only (was the ding chord) |
| EXTRACT_PULSE | **sweep** .3 ·0.55, **whirr** .25 ·0.6; gap 10 | 1.0, blocks |
| SELF_DESTRUCT | **blip** .4 ·0.7 twice, 4 t apart; gap 10 | 1.0, blocks (pitch creeps +1 % per 32 s, was +1 % per 16 s) |
| EXTRACT_DONE | GENERIC_EXPLODE .35 ·1.4, RESPAWN_ANCHOR_DEPLETE .5 ·0.8, **long power-down** .4 ·0.8 @2 | 1.2 (old 1.4); the vanilla BEACON_DEACTIVATE beside it went 1.0 → 0.6 |
| POD_OPEN | PISTON_CONTRACT .5 ·0.6, FIRE_EXTINGUISH .4 ·0.7 (hiss), IRON_DOOR_OPEN .4 ·0.7 @4 | 1.2 (old 1.5) |
| POD_BEACON (open pod, every 40 t) | **blip** .18 ·1.25; gap 20 | 1.0 (was the warning beep at 0.4 × a hot file) |
| SCANNER (meteor scanner) | LEVER_CLICK .15 ·1.7, **blip** .3 ·1.2, **blip** .3 ·1.35 @3 | 1.0 (was BEACON_POWER_SELECT 1.0 + warning 1.0) |
| METEOR | GENERIC_EXPLODE .7 ·0.55, BLAZE_SHOOT .45 ·0.5 | 4.0 → 2.05 after compression (carries ~26 blocks, like the other meteor sounds) |
| HEATBLAST_BLAST | BLAZE_SHOOT .5, FIRECHARGE_USE .3 ·1.2, FURNACE_FIRE_CRACKLE .35 ·1.3 @2 | 0.5-1.0 |
| HEATBLAST_STREAM | BLAZE_BURN .45 ·0.9, FIRECHARGE_USE .2 ·0.7; gap 8 | 0.6 (every 12 t) / 1.0 |
| HEATBLAST_NOVA | GENERIC_EXPLODE .5 ·0.8, BLAZE_SHOOT .45 ·0.6, FIRECHARGE_USE .45 ·0.5 @2, FIRE_AMBIENT .6 ·0.8 @4 | 0.8-1.4 |
| HEATBLAST_JET | FIRECHARGE_USE .4 ·0.8, BLAZE_BURN .35 ·1.1 @1 | 1.0 |
| HEATBLAST_JET_LOOP (new; was the jet file at 0.35) | BLAZE_BURN .16 ·1.3, jitter 10 %; gap 16 | 1.0 (once a second while flying) |
| HEATBLAST_SHIELD | BLAZE_BURN .45 ·0.7, FIRE_AMBIENT .5 ·0.6, FIRECHARGE_USE .25 ·0.6 | 1.0 |
| HEATBLAST_IGNITE | FIRECHARGE_USE .45 ·0.8, BLAZE_BURN .4 @1, FURNACE_FIRE_CRACKLE .4 @3 | 1.0 |
| FOUR_ARMS_PUNCH | PLAYER_ATTACK_STRONG .5 ·0.6, IRON_GOLEM_ATTACK .45 ·0.9, PLAYER_ATTACK_KNOCKBACK .35 ·0.7 | 0.7-1.2 |
| FOUR_ARMS_CLAP | PLAYER_ATTACK_KNOCKBACK .55 ·0.5, GENERIC_EXPLODE .35 ·1.5, WIND_CHARGE_BURST .45 ·0.7 @1 | 1.4 → 1.14 |
| FOUR_ARMS_SLAM | GENERIC_EXPLODE .4 ·0.7, STONE_BREAK .55 ·0.6, GRAVEL_BREAK .45 ·0.7 @2 | 0.7-1.5 |
| FOUR_ARMS_THROW | IRON_GOLEM_ATTACK .5 ·0.7, PLAYER_ATTACK_SWEEP .35 ·0.6 | 1.2 |
| FOUR_ARMS_ROAR | RAVAGER_ROAR .5, HOGLIN_ANGRY .3 ·0.7; gap 10 | 0.7-1.6 |
| FOUR_ARMS_STANCE (new; was ANVIL_LAND) | HEAVY_CORE_PLACE .45 ·0.6, GRAVEL_BREAK .3 ·0.6 | 1.0 |
| XLR8 SND_DASH | WIND_CHARGE_THROW .35 ·1.4, PLAYER_ATTACK_SWEEP .3 ·1.6, BREEZE_JUMP .25 ·1.6 | 0.6-1.0 |
| XLR8 SND_HIT | PLAYER_ATTACK_WEAK .35 ·1.3, PLAYER_ATTACK_SWEEP .12 ·1.9, jitter 10 %; **gap 3** (≤ 7 a second) | 0.3-1.0 |
| XLR8 SND_TORNADO | BREEZE_WHIRL .5 ·0.8, BREEZE_INHALE .3 ·0.7; gap 10 | 0.8-1.2 |
| XLR8 SND_BURST | WIND_CHARGE_BURST .55 ·0.8, BREEZE_SHOOT .35 ·0.6, GENERIC_EXPLODE .15 ·1.7 | 1.2 |
| XLR8 SND_VISOR | IRON_TRAPDOOR_CLOSE .3 ·1.8, COPPER_BULB_TURN_ON .25 ·1.2 | 0.6-1.0 |
| Diamondhead SND_SHARD | TRIDENT_THROW .28 ·1.5, POINTED_DRIPSTONE_BREAK .35 ·1.3; gap 3 | 0.7-1.0 |
| Diamondhead SND_GROW | CALCITE_PLACE .45 ·0.7, POINTED_DRIPSTONE_BREAK .25 ·0.8, DEEPSLATE_PLACE .25 ·0.8 @1 | 0.6-1.2 |
| Diamondhead SND_SHATTER | GLASS_BREAK .3 ·0.8, DECORATED_POT_SHATTER .35, CALCITE_BREAK .3 ·0.9; gap 3 | 0.4-1.0 |
| Diamondhead SND_BLADE | CALCITE_PLACE .35 ·0.6, POINTED_DRIPSTONE_BREAK .3 ·0.7, PLAYER_ATTACK_SWEEP .25 ·1.3 | 0.6-0.9 |
| Upgrade SND_BEAM | **sweep** .3 ·1.8 (digital zap), SCULK_BLOCK_CHARGE .35 ·1.6, COPPER_BULB_TURN_ON .4 ·0.8 | 0.9-1.3 |
| Upgrade SND_CHARGE | **whirr** .35 ·0.7 (low techno hum), SCULK_BLOCK_CHARGE .3 ·1.2, COPPER_BULB_TURN_ON .3 @4 | 1.0 |
| Upgrade SND_MERGE | HONEY_BLOCK_PLACE .35 ·0.8, **whirr** .25 ·0.6 @2, SCULK_BLOCK_CHARGE .25 ·1.4 @3 | 0.7-1.1 |
| Upgrade SND_EMP | **power-down** .45 ·0.9, **sweep** .3 ·0.7, SCULK_BLOCK_CHARGE .4 ·0.8 | 0.9-1.4 |
| Upgrade SND_SLIDE | HONEY_BLOCK_SLIDE .4 ·0.9, SLIME_SQUISH_SMALL .25 ·1.2, **whirr** .15 ·0.5 | 0.7-1.0 |
| Ghostfreak SND_CLAW | PLAYER_ATTACK_SWEEP .35 ·1.1, PHANTOM_BITE .3 ·1.2, SOUL_ESCAPE .25 | 0.5-1.0 |
| Ghostfreak SND_SCREAM | PHANTOM_AMBIENT .45 ·0.6, VEX_CHARGE .3 ·0.7, SOUL_ESCAPE .5 ·0.6; gap 10 | 1.0-1.3 |
| Ghostfreak SND_PHASE | SOUL_ESCAPE .5 ·0.8, PHANTOM_FLAP .25 ·0.6, BREEZE_INHALE .2 ·0.6 | 0.6-1.0 |
| Ghostfreak SND_LAUGH | PHANTOM_AMBIENT .4 ·0.55, SOUL_ESCAPE .4 ·0.6, VEX_AMBIENT .22 ·0.5 @3; gap 10 | 0.7-1.2 |
| Ghostfreak SND_TENDRIL | PHANTOM_FLAP .3 ·0.7, VEX_CHARGE .22 ·0.6, SLIME_SQUISH_SMALL .15 ·0.6; gap 3 | 0.6-1.1 |
| Stinkfly BUZZ | BEE_POLLINATE .4 ·0.8, PHANTOM_FLAP .18 ·1.8, jitter 8 %; gap 6 | 0.25 (flying, every 16 t) to 1.2 |
| Stinkfly SPIT | LLAMA_SPIT .45, SLIME_SQUISH_SMALL .3 ·1.2 | 0.6-1.0 |
| Stinkfly SPLAT | SLIME_SQUISH .45 ·0.9, HONEY_BLOCK_FALL .3 ·0.8; gap 3 | 0.6-1.3 |
| Stinkfly GAS | FIRE_EXTINGUISH .3 ·0.5, BREWING_STAND_BREW .3 ·0.6, SLIME_SQUISH .2 ·0.6 | 1.0 |
| Stinkfly STING | BEE_STING .45 ·0.8, PLAYER_ATTACK_WEAK .3 ·1.2 | 1.0 |
| Stinkfly DIVE | BEE_POLLINATE .45 ·1.1, WIND_CHARGE_THROW .3, PHANTOM_FLAP .25 ·1.4 | 1.0 |
| Wildvine VINE | CAVE_VINES_PLACE .45 ·0.8, FISHING_BOBBER_THROW .3 ·0.6, PLAYER_ATTACK_SWEEP .25 ·0.8 | 0.8-1.0 |
| Wildvine SEED | CROP_PLANTED .4 ·1.4, CHICKEN_EGG .35 ·1.1 @1 (the pop) | 1.0 |
| Wildvine BURST | AZALEA_LEAVES_BREAK .5 ·0.8, GENERIC_EXPLODE .18 ·1.7, CROP_PLANTED .3 | 1.0 |
| Wildvine ROOTS | ROOTS_BREAK .5 ·0.7, ROOTED_DIRT_BREAK .45 ·0.6, BAMBOO_BREAK .3 ·0.6 @1 (wood crack) | 1.0-1.4 |
| Wildvine BLOOM | FLOWERING_AZALEA_PLACE .4 ·1.1, SPORE_BLOSSOM_PLACE .35, BIG_DRIPLEAF_TILT_UP .25 ·0.9 | 0.6-1.0 |
| Wildvine RUSTLE | AZALEA_LEAVES_STEP .4, GRASS_STEP .3 ·0.9; gap 8 | 0.7-1.0 |
| Ripjaws BITE_SOUND | EVOKER_FANGS_ATTACK .45 ·1.1 (jaw snap), PLAYER_ATTACK_STRONG .3 ·0.8 | 0.8-1.2 |
| Ripjaws ROAR | POLAR_BEAR_WARNING .4 ·0.8, DROWNED_AMBIENT .35 ·0.6; gap 10 | 0.7-1.4 |
| Ripjaws SPLASH | GENERIC_SPLASH .45, PLAYER_SPLASH .22 ·0.9; gap 4 | 0.5-1.2 |
| Ripjaws SLAM | GENERIC_SPLASH .55 ·0.7, PLAYER_ATTACK_KNOCKBACK .35 ·0.6, GENERIC_EXPLODE .2 ·1.4 | 1.2 |
| Ripjaws GASP | GUARDIAN_FLOP .45, DROWNED_HURT .25 ·0.8; gap 10 | 0.8-0.9 |
| Ripjaws WHIRL | BUBBLE_COLUMN_WHIRLPOOL_INSIDE .45 ·0.9, GENERIC_SWIM .35 ·0.8, TRIDENT_RIPTIDE_2 .22 ·0.8; gap 10 | 0.8-1.2 |
| Grey Matter PINCH | PLAYER_ATTACK_WEAK .35 ·1.6, SILVERFISH_HURT .22 ·1.4 | 1.0-1.2 |
| Grey Matter SCAN | LEVER_CLICK .2 ·1.9, BOOK_PAGE_TURN .25 ·1.5, **sweep** .12 ·2.0 @1 (light zap) | 0.5-1.0 |
| Grey Matter GADGET | CRAFTER_CRAFT .35 ·1.5, LEVER_CLICK .2 ·1.6, **sweep** .15 ·1.9 @2, COPPER_BULB_TURN_ON .25 ·1.4 @2 | 1.0-1.2 |
| Grey Matter LEAP | SILVERFISH_STEP .35 ·1.6, RABBIT_JUMP .35 ·1.4 (scurry) | 0.8-1.0 |
| Grey Matter REPAIR | SMITHING_TABLE_USE .22 ·1.7, CRAFTER_CRAFT .25 ·1.8 @3, COPPER_BULB_TURN_ON .2 ·1.5 @5 | 1.0 |
| Grey Matter THINK | SILVERFISH_AMBIENT .28 ·1.5, LEVER_CLICK .15 ·2.0, BOOK_PAGE_TURN .2 ·1.6 @3 | 1.0 |
| Grey Matter DECOY_SOUND (new; was NOTE_BLOCK_BIT) | TRIPWIRE_CLICK_ON .16 ·1.7, SILVERFISH_AMBIENT .12 ·1.6; gap 8 | 1.0, every 10 t |

The vanilla sounds the pod already played were turned down: BEACON_ACTIVATE 1.2 → 0.6, TRIDENT_THUNDER surge 0.5 → 0.3,
and BEACON_DEACTIVATE 1.0 → 0.6.

**Removed:** 64 synthesised OGGs, along with their `sounds.json` entries, subtitle lines and `SoundEvent` registrations.
The kits' private `sound()` registrars went too. The three old generators now carry a "SUPERSEDED, do not run" header.

## 3. Waveform check (`scratchpad/analyse_omnitrix_sounds.js`)

The script decodes each OGG through ffmpeg to 44.1 kHz mono float PCM and reports four numbers:
- **Peak** and **RMS**, both in dBFS. RMS is measured over the frames within 30 dB of the loudest one.
- **Spectral centroid**, in Hz.
- **Tonality**: the energy-weighted mean, over 2048-sample frames, of the largest FFT bin divided by the mean bin.

Tonality is about 10 for broadband noise and in the hundreds for a sustained pure tone. Low, boomy sounds also score high,
because their energy sits in a few bins: vanilla's explosion scores 225 and BEACON_ACTIVATE 239. So tonality only flags a
narrow-band tone when the centroid is also mid-to-high.

Vanilla references, from the same script:

| Vanilla file | Peak | RMS | Centroid | Tonality |
|---|---|---|---|---|
| piston/out | -4.3 | -24.0 | 4143 | 23 |
| random/glass1 | +2.5 | -19.6 | 7925 | 10 |
| irongolem/throw | -14.1 | -28.1 | 3903 | 17 |
| random/click | -0.3 | -23.1 | 4015 | 28 |
| bee/loop1 | -7.1 | -22.3 | 4138 | 40 |
| note/bit (a "beep") | -16.1 | -24.6 | 3569 | 183 |

**After: the six files that are left.**

| File | Length s | Peak dBFS | RMS dBFS | Centroid Hz | Tonality |
|---|---|---|---|---|---|
| core_up (whirr) | 0.55 | -6.1 | -18.1 | 2820 | 51 |
| transform (sweep) | 0.95 | -5.0 | -20.7 | 6796 | 10 |
| revert (power-down) | 0.70 | -5.9 | -19.8 | 2731 | 53 |
| timeout (long power-down) | 1.20 | -5.6 | -19.5 | 2840 | 48 |
| warning (blip) | 0.11 | -6.4 | -20.8 | 2869 | 88 |
| denied (buzz) | 0.30 | -6.0 | -17.6 | 1898 | 68 |

**Before: the same six names, plus the worst of the rest.** The full 69-file table is in the appendix.

| File | Length s | Peak dBFS | RMS dBFS | Centroid Hz | Tonality |
|---|---|---|---|---|---|
| core_up | 0.62 | -1.3 | -11.2 | 2980 | 125 |
| transform | 1.40 | **+0.3** | -11.9 | 6883 | 36 |
| revert | 1.00 | -1.8 | -14.3 | 4529 | 71 |
| timeout | 1.60 | -1.2 | -13.1 | 6217 | 68 |
| warning | 0.20 | -3.7 | -10.1 | 7804 | 110 |
| select | 0.37 | -3.1 | -14.2 | 1758 | 285 |
| recharged | 0.90 | -2.5 | -14.6 | 1522 | 186 |
| grey_matter_repair | 1.00 | -2.0 | -14.6 | 2233 | 296 |
| grey_matter_scan | 0.92 | -3.0 | -14.9 | 5262 | 182 |
| core_down | 0.45 | -1.9 | -12.2 | 838 | 237 |
| ripjaws_bite | 0.45 | -0.6 | -10.2 | 1438 | 211 |
| xlr8_hit | 0.20 | -1.5 | -11.9 | 931 | 197 |
| four_arms_slam | 1.40 | -0.3 | -6.4 | 272 | 325 |
| four_arms_clap | 1.30 | -0.4 | -7.0 | 654 | 270 |
| ghostfreak_tendril | 0.90 | -1.7 | -8.5 | 611 | 171 |
| diamondhead_blade | 0.80 | -2.0 | -16.1 | 4241 | 95 |
| self_destruct | 1.00 | -0.6 | **-7.0** | 8165 | 49 |

Summary:
- **Peaks.** Before, 0.3 dB over to 3.7 dB under full scale. Now about -6 dBFS.
- **RMS.** It went from -6 to -16 dBFS down to -18 to -21 dBFS. That sits in vanilla's range (-19 to -28) and is played at
  lower volumes on top.
- **Tonality.** It went from up to 325 to 10-88. The 88 is the warning blip, which is meant to be a short beep, and it
  scores well under vanilla's note-block bit (183).
- **Bells and chords.** None are left. The whirr and power-down sit at about 50, like a buzzing motor, not a ping.

## What couldn't be improved

- **I couldn't listen to any of this.** Everything was checked by waveform analysis and by choosing vanilla samples by
  name and file length. A listening pass in-game is still needed, especially for the balance between layers.
- **Subtitles are now vanilla names.** With subtitles on, most layers show vanilla captions ("Blaze shoots", "Piston moves"
  and so on) instead of the old per-alien ones. Only the six synthesised events keep a mod subtitle.
- **Some vanilla splash and roar files are long.** GENERIC_SPLASH has a 3.9 s variant, and RAVAGER_ROAR runs 2.8-3.9 s. The
  low layer volumes and the gaps keep them from piling up.
- **The warning blip is still a tone.** A beep is one by nature. It is softened (breathy noise band, detuned, low-passed,
  8 ms attack, 0.32 volume) and kept as quiet as the brief asked.

## Appendix: all 69 old files, before the rework

Flatness is the spectral flatness (0 = a pure tone, 1 = white noise). It is near 0 for almost everything here and adds
little.

| File | Length s | Peak dBFS | RMS dBFS | Centroid Hz | Tonality | Flatness |
|---|---|---|---|---|---|---|
| core_down | 0.45 | -1.9 | -12.2 | 838 | 237 | 0.000 |
| core_up | 0.62 | -1.3 | -11.2 | 2980 | 125 | 0.000 |
| dial | 0.12 | -9.2 | -24.0 | 6711 | 48 | 0.027 |
| diamondhead_blade | 0.80 | -2.0 | -16.1 | 4241 | 95 | 0.000 |
| diamondhead_grow | 1.00 | -0.9 | -13.8 | 5627 | 94 | 0.003 |
| diamondhead_shard | 0.50 | -1.9 | -16.1 | 6980 | 46 | 0.001 |
| diamondhead_shatter | 0.90 | -0.9 | -11.9 | 10315 | 24 | 0.156 |
| extract_done | 1.40 | -0.8 | -12.0 | 2117 | 123 | 0.000 |
| extract_pulse | 0.52 | -0.8 | -11.2 | 3922 | 42 | 0.018 |
| four_arms_clap | 1.30 | -0.4 | -7.0 | 654 | 270 | 0.000 |
| four_arms_punch | 0.40 | -0.7 | -8.0 | 854 | 241 | 0.000 |
| four_arms_roar | 1.30 | -0.2 | -6.9 | 1438 | 82 | 0.000 |
| four_arms_slam | 1.40 | -0.3 | -6.4 | 272 | 325 | 0.000 |
| four_arms_throw | 0.60 | -1.1 | -13.8 | 4333 | 74 | 0.021 |
| ghostfreak_claw | 0.45 | -1.4 | -14.2 | 8696 | 17 | 0.229 |
| ghostfreak_laugh | 1.50 | -1.4 | -11.3 | 2667 | 64 | 0.000 |
| ghostfreak_phase | 0.92 | -1.7 | -12.6 | 2528 | 112 | 0.000 |
| ghostfreak_scream | 1.40 | -0.5 | -10.5 | 4815 | 48 | 0.003 |
| ghostfreak_tendril | 0.90 | -1.7 | -8.5 | 611 | 171 | 0.000 |
| grey_matter_gadget | 0.72 | -0.8 | -11.6 | 7214 | 39 | 0.049 |
| grey_matter_leap | 0.42 | -2.5 | -12.5 | 3088 | 69 | 0.000 |
| grey_matter_pinch | 0.37 | -1.9 | -16.4 | 8911 | 45 | 0.040 |
| grey_matter_repair | 1.00 | -2.0 | -14.6 | 2233 | 296 | 0.000 |
| grey_matter_scan | 0.92 | -3.0 | -14.9 | 5262 | 182 | 0.005 |
| grey_matter_think | 1.40 | -3.0 | -13.5 | 6277 | 86 | 0.005 |
| heatblast_blast | 0.60 | -0.7 | -13.3 | 2608 | 45 | 0.000 |
| heatblast_ignite | 1.00 | -0.7 | -11.1 | 2716 | 78 | 0.001 |
| heatblast_jet | 0.90 | -2.9 | -18.3 | 4763 | 12 | 0.012 |
| heatblast_nova | 1.60 | -0.2 | -8.1 | 1982 | 133 | 0.000 |
| heatblast_shield | 1.00 | -1.0 | -12.8 | 1857 | 90 | 0.000 |
| heatblast_stream | 1.20 | -1.5 | -13.7 | 2377 | 17 | 0.000 |
| meteor | 3.00 | -1.3 | -15.9 | 3294 | 24 | 0.003 |
| pod_open | 1.60 | -1.1 | -14.9 | 11764 | 6 | 0.408 |
| recharged | 0.90 | -2.5 | -14.6 | 1522 | 186 | 0.000 |
| revert | 1.00 | -1.8 | -14.3 | 4529 | 71 | 0.001 |
| ripjaws_bite | 0.45 | -0.6 | -10.2 | 1438 | 211 | 0.001 |
| ripjaws_gasp | 0.70 | -2.7 | -16.0 | 4820 | 30 | 0.034 |
| ripjaws_roar | 1.40 | -0.1 | -7.0 | 1809 | 69 | 0.000 |
| ripjaws_slam | 1.00 | -0.5 | -6.6 | 2515 | 155 | 0.000 |
| ripjaws_splash | 1.00 | -0.5 | -13.3 | 3930 | 22 | 0.004 |
| ripjaws_whirl | 1.60 | -2.2 | -16.9 | 2900 | 34 | 0.002 |
| select | 0.37 | -3.1 | -14.2 | 1758 | 285 | 0.000 |
| self_destruct | 1.00 | -0.6 | -7.0 | 8165 | 49 | 0.038 |
| slap | 0.45 | -1.0 | -10.9 | 847 | 175 | 0.000 |
| stinkfly_buzz | 0.70 | -2.5 | -12.4 | 1453 | 52 | 0.000 |
| stinkfly_dive | 1.10 | -0.7 | -10.7 | 2414 | 48 | 0.002 |
| stinkfly_gas | 1.60 | -1.4 | -14.9 | 11252 | 6 | 0.490 |
| stinkfly_spit | 0.40 | -1.5 | -12.9 | 2843 | 90 | 0.000 |
| stinkfly_splat | 0.50 | -1.5 | -14.1 | 2541 | 114 | 0.000 |
| stinkfly_sting | 0.40 | -2.0 | -16.2 | 5932 | 11 | 0.008 |
| timeout | 1.60 | -1.2 | -13.1 | 6217 | 68 | 0.013 |
| transform | 1.40 | 0.3 | -11.9 | 6883 | 36 | 0.066 |
| upgrade_beam | 0.62 | -0.7 | -11.4 | 8273 | 13 | 0.135 |
| upgrade_charge | 1.10 | -1.8 | -11.1 | 7791 | 35 | 0.062 |
| upgrade_emp | 1.20 | -0.7 | -9.4 | 3254 | 117 | 0.003 |
| upgrade_merge | 0.92 | -1.9 | -11.0 | 1138 | 138 | 0.001 |
| upgrade_slide | 0.70 | -2.7 | -12.6 | 1646 | 140 | 0.000 |
| warning | 0.20 | -3.7 | -10.1 | 7804 | 110 | 0.004 |
| wildvine_bloom | 1.20 | -2.1 | -15.7 | 8976 | 50 | 0.076 |
| wildvine_burst | 0.90 | -0.3 | -10.1 | 3047 | 90 | 0.000 |
| wildvine_roots | 1.40 | -0.9 | -12.7 | 630 | 108 | 0.000 |
| wildvine_rustle | 0.80 | -3.3 | -21.7 | 6771 | 7 | 0.107 |
| wildvine_seed | 0.37 | -2.5 | -13.6 | 2386 | 86 | 0.000 |
| wildvine_vine | 0.55 | -1.2 | -13.7 | 4618 | 32 | 0.021 |
| xlr8_burst | 1.40 | -0.6 | -10.3 | 1753 | 120 | 0.005 |
| xlr8_dash | 0.55 | -1.2 | -12.5 | 8012 | 7 | 0.182 |
| xlr8_hit | 0.20 | -1.5 | -11.9 | 931 | 197 | 0.000 |
| xlr8_tornado | 1.60 | -1.7 | -17.6 | 3541 | 20 | 0.005 |
| xlr8_visor | 0.35 | -2.5 | -13.6 | 2142 | 89 | 0.000 |
