# Omnitrix revamp: Spidermonkey

Status: **BUILT (v0.17.2).** User: "can you work on revamps for the rest of the alien force aliens please, look at their powers and
abilities in the show and do research and make them good please" and "delete all the alien models and only use the skins i provided
please no extra things untill i say". No Ultimate Spidermonkey ("dont add ultimate aliens yet").

Code: `src/main/java/com/projecthero/mod/omnitrix/alien/spidermonkey/SpidermonkeyKit.java`, client
`src/client/java/com/projecthero/mod/client/omnitrix/alien/SpidermonkeyClient.java` + `spidermonkey/SpidermonkeyFx.java`, tests
`src/gametest/java/com/projecthero/mod/gametest/OmnitrixSpidermonkeyGameTests.java` (8) and the updated `OmnitrixRecalCGameTests`.

## Research
ben10.fandom returned 402, so sources are fan wikis / blogs / a respect thread via search snippets (5yl.fandom, myanimethoughts,
brickset, r/respectthreads) and memory. They agree on: an Arachnichimp (four arms, four eyes) who **shoots web from the tip of his
tail**, **sticks to walls and hangs from ceilings**, is **agile and acrobatic**, has **prehensile tail and feet** and is stronger
than he looks (he decked Ultimate Kevin). Weakness: he **gets tangled in his own webs**, and villains use his webs against him.
Fire burning fur / silk is a single-source claim, kept as gameplay. Debut cited as "What Are Little Girls Made Of?" (one blog).

Distinct from Spider-Man (a city swinger): he is a jungle gym -- chained swings (Monkey Bars), ceiling hangs, a Swing Kick off a
web line, four-fist combos and a cocoon slam.

## Kit (built)

| Key | Move | What it does | Numbers |
|---|---|---|---|
| R | Web Shot | glob of web from his tail | 6, rooted 2 s, cd 1 s |
| Shift+R | Web Net | spinning net | 12 r 2.5, rooted 3 s, cd 8 s |
| G | Quad Flurry | four fists in front | 4 x 3.5 (+1 each on a webbed foe), cd 5 s |
| Shift+G | Web Yank / Swing Kick | ground: reel the creature in; air or wall: he flies in on the line and kicks | yank 12 / kick 15 + knocked flying, range 16, cd 8 s |
| X | Web Zip | line to a block, zipped there | range 28, cd 1.5 s |
| Shift+X (hold) | Web Swing | pendulum while held, release flings; re-catch in the air within 2 s = no cooldown (Monkey Bars) | up to 4 s, cd 3 s |
| Z | Tail Whip | spin | 13 r 3.2; webbed foes torn loose and thrown twice as far, cd 6 s |
| Shift+Z | **Cocoon Crush** (ULT) | cocoon a creature, squeeze, then slam it | 10 x 5 + 14 = 64, rooted 5 s, range 16, cd 45 s |
| C | Web Trap | ground web, two at a time | 5 + rooted 3 s, r 2.5, 15 s, cd 15 s |
| Shift+C | Web Barrage | six webs in a fan | 6 x 5, rooted 1.5 s, cd 10 s |
| V | Wall Crawl | toggle (on at transform) | -- |
| Shift+V | Web Shield | web wall 2 ahead, shots stop, creatures wade | 8 s, cd 20 s |
| Attack | Four Fists | every 4th punch within 2 s | +4 and a shove |
| Sneak | Ceiling Hang | Wall Crawl on + a ceiling over his head: hangs instead of falling | -- |

Damage check: quick 5-6, normal 12-15, ultimate 64.

### Passives
- **Wall Crawl + Ceiling Hang.**
- **Nimble:** 1.25x speed, better jump, safe fall +4, no fall damage after his zips, swings and kicks.
- **Monkey Bars:** free re-catch of a swing line in the air.
- **Sticky:** webs root (bosses a quarter); fists hit webbed foes +1.
- Light frame: armour 2 only (not a tough alien), 22 HP, 0.85 scale.

### Weaknesses
- **Fire:** x1.5 fire damage; a burning creature can't be webbed.
- **Tangled (new, from the show):** walking through his own ground web gives him Slowness II for 1.5 s.
- **Light frame:** 22 HP, armour 2.

## Model
Removed the second pair of arms, the three-segment tail and spinneret, and `spidermonkey_extras.png` +
`scratchpad/recal_v0172/spidermonkey/gen_spidermonkey.js`. He is the player model only, in the user's 64x64 skin
(minecraftskins.com), wide-arm model, Omnitrix badge kept. Web lines now leave from just above and behind his shoulders (where the
show's tail curls), or low in front in his own first person.

## Screenshots
`docs/screenshots/v0172/spidermonkey/`: skin_front, web_net, web_swing, cocoon_crush_ult.
