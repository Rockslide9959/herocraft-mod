# Doctor Fate and Shazam -- approved design (2026-11-12)

Approved by the user 2026-11-12 ("Yes, build both"). Open choices decided as proposed: the Shazam word must be the whole
message; during Possession Nabu fights for you while you watch.

Drawn from the comics (Kent Nelson, Khalid Nassour, the JSA; Billy Batson and the Shazam Family), Young Justice, Justice
League Unlimited, the Shazam! and Black Adam films, and Batman: The Brave and the Bold. Both are Hero-Tier **Primary** powers in
the Guidebook's **DC** section. House key rules: H = selector only, no N, X = dash, Shift+X = mobility combat move, V =
utility/mode. Flight is **double-tap Space**, like Starfire and Raven.

---

## Doctor Fate -- the Helmet of Fate and Nabu

**The idea:** the helmet makes you one of the strongest magic users in the mod, but it isn't *your* power. Nabu, the Lord
of Order inside it, wants your body. The longer and harder you use it, the more he takes over. You're always choosing
between more power and more control.

**Getting it:** the **Helmet of Fate** in a new small structure, the **Tomb of Nabu**, a sandstone burial chamber under rare
desert pyramids, guarded by mummy-skinned husks. Picking it up opens Nabu's offer: "Will you serve Order?" with **Accept** or
**Refuse**. Refusing leaves the helmet on its pedestal (another player can take it). Random serums never give it.

**Two states (V puts the helmet on or takes it off):**
- **Without the helmet (Kent Nelson's own magic):** weak versions of R, G and X only, normal health. Nabu's Dominion drains.
  The Amulet of Anubis lets you see Nabu's bar.
- **Wearing the Helmet:**
  - 40 health, 30% damage resistance, flight, immunity to Wither and Poison.
  - Every spell is at full strength.
  - Look: the golden helmet, Cloak of Destiny and amulet appear. They're a suit layer over your skin, and your face is
    hidden by the helmet.
  - v0.17.3 (user): the suit is the DCEU Doctor Fate skin by **JMCT** on PlanetMinecraft
    (https://www.planetminecraft.com/skin/doctor-fate-dceu/, original in `docs/reference/doctorfate/`, see `SOURCE.txt`).
    **No licence is stated on the page: check it with the author / credit them before a public release.**
    `scratchpad/doctorfate/build_fate_skin.js` makes `suit_slim.png` (the skin is slim) and a wide `suit.png` from it, cuts out
    the helmet's side openings so your own skin shows there, takes the painted cape off the back and paints the cloth
    Cloak of Destiny (`cloak.png`) in the skin's colours. The skin's head is the helmet; the old separate helmet model is now
    only worn by the Avatar of Order. Your own outer skin layer is hidden while the suit is on.

**Dominion (Nabu's control, a Gauge with Order gold creeping over your colour)** rises while you wear the helmet (slowly),
with every spell (bigger spells raise it more) and every time you accept one of Nabu's bargains. It falls with the helmet
off. As it climbs, Nabu fights you:

| Dominion | What Nabu does |
|---|---|
| 0-24 | Whispers. Faint golden subtitles ("Order must be kept."). Nothing else. |
| 25-49 | **Override:** 1 in 6 spells comes out as the spell Nabu prefers, a stronger one he picks for the situation (e.g. you cast Ankh Bolt, he fires Golden Chains at the most chaotic target). He won't let you hurt villagers, golems or tamed animals (the spell fizzles). |
| 50-74 | **Pull:** your aim drifts toward the nearest creature of Chaos (Nether mobs, the undead, End mobs, Raven players at Rage). The drift is gentle and you can fight it. Spells cost 10% less, because he's helping. Your voice in chat gets a gold "[Nabu]" tag. |
| 75-99 | **Taking hold:** gold tint at the edges of the screen, your movement keys sometimes lag a moment, and spells cost health on top of Dominion. You heal 2x, because he's protecting his vessel. |
| 100 | **Possession:** Nabu takes your body for 15 s. He flies and casts on his own at the strongest hostile nearby (huge damage), and you can only watch. Mash Space to **struggle**: each press shortens it. Afterward the helmet falls off by itself and can't be put back on for 60 s. |

**Bargains (choices):** Nabu offers deals on a small two-button screen that pauses nothing. You have 10 s to choose, and
ignoring one counts as refusing.
- **Death's door:** at 3 hearts or fewer, "Let me save you." Accept: full heal and a golden shield, **+35 Dominion**.
  Refuse: nothing.
- **Order's command:** sometimes at night, "Banish 5 creatures of Chaos before dawn." Accept: if you succeed, Dominion
  -30 and an Ankh Charm; if you fail, Dominion +40.
- **Forbidden spell:** Shift+Z asks Nabu first, every time. He always says yes, but it costs Dominion to 90+.
- Each bargain you **refuse** makes the next offer meaner (bigger reward, bigger cost) for the rest of that day.

| Key | Move | Spec (helmet on) |
|---|---|---|
| (double Space) | Flight | golden-ankh hover flight, cloak billowing |
| R | Ankh Bolt | golden ankh-shaped bolt, 10 dmg, passes through one creature. +1 Dominion |
| Shift+R | Golden Chains | glowing chains from the ground bind the target 3 s (bosses 1 s), 6 dmg. +3 |
| G | Ankh Shield | hold: giant golden ankh in front of you blocks everything from the front, reflects projectiles. +1/s |
| Shift+G | Banishment | open an ankh gate under the target. Normal mobs are banished (gone, no drops); strong mobs and players are thrown 40 blocks away. Bosses immune. +12, 20 s cd |
| Z | Glyph Storm | 3 s rain of golden hieroglyph blades in a 7-block circle where you look, 4 dmg per hit. +8 |
| Shift+Z | Avatar of Order | asks Nabu, then a 40-block-tall golden projection of Doctor Fate rises behind you for 12 s: its giant hand slams (40 dmg, 8 blocks) on each attack key press and stops all projectiles near you. Dominion to 90+ afterward. 3 min cd |
| **X** | **Ankh Step** | blink through a small golden ankh portal 12 blocks along your look |
| Shift+X | Gate Drop | open a portal under the target and its exit 15 blocks above it: it falls (fall damage) and you blink beside where it lands. +5 |
| C | Order Blast | cone of golden force, 14 dmg, big knockback. +4 |
| Shift+C | Ward of Order | 6-block golden glyph circle for 10 s: hostile projectiles and potions inside fizzle, squadmates in it take 30% less damage. +10 |
| V | Helmet | put the helmet on / take it off (1 s animation; can't take it off during Possession) |
| Shift+V | Sight of Nabu | 10 s: every creature within 64 blocks glows (Chaos red, Order gold, neutral white) and you see their health. +6 |

**Fair-play rules:** Possession never attacks squadmates. On a server you can't be possessed while inside spawn protection.
Dominion is saved per player.

**Possible boss later:** Klarion the Witch Boy, a Lord of Chaos raid. Helmet wearers get a bonus against him and Nabu stays
quiet during the fight.

---

## Shazam -- Billy Batson

**The idea:** type **Shazam** in chat and magic lightning strikes you. You become the champion: S.H.A.Z.A.M. (Wisdom of
Solomon, Strength of Hercules, Stamina of Atlas, Power of Zeus, Courage of Achilles, Speed of Mercury). Type it again to
turn back. Like in the comics and films, the transform lightning is itself a weapon.

**Saying the word**
- A chat message that is just the word ("shazam", "SHAZAM!", "Shazam.", any case) triggers it. That message is not posted
  to chat. Everyone nearby sees and hears the lightning instead, and the subtitle says "SHAZAM!".
- **The lightning hurts:** each transform bolt deals 12 to creatures within 3 blocks of you (never you or squadmates). In the
  films Billy "turns back and forth" to call lightning, so that's a real trick, but there's a 4 s gap between words.
- **You need your voice:** it doesn't work underwater with no air bubble, while muffled by Black Adam (later boss), or inside
  a powder snow block. You get the message "You can't say it!".
- Saying it while transformed turns you back to Billy (no damage to you) and the lightning still hits nearby enemies.

**Getting it:** during a thunderstorm, a very rare **Wizard Shazam** NPC appears near villages (player model, detailed skin,
long beard and robes). Right-click him: he judges if you're **pure of heart** using the existing worthiness check (no recent
villager or iron golem kills, good village reputation). If worthy he says your name and grants the power; otherwise he fades
in a lightning flash and won't return for 5 days. Random serums never give it.

**Look:** human form is your normal skin. Transformed you get the Shazam suit as a layer over your skin: red, a gold
lightning-bolt chest, gold sash and cuffs, and a short white cape with gold trim. You also get 0.1 blocks taller.

**Suit skin (2026-11-12, the user's pick):** the suit is the PlanetMinecraft skin "Shazam (Justice League)" by outofspace2
(https://www.planetminecraft.com/skin/shazam-justice-league/, wide model), kept untouched in
`docs/reference/shazam/` with `SOURCE.txt`. **No licence is stated: check with / credit the author before a public release.**
`scratchpad/shazam/make_shazam_suit.js` builds the suit from it: head, hair, neck and hands made transparent (your own head and
skin show), the painted cape taken off (it's a cape model on `FlowingCapeLayer` now, restyled to match, hanging to the belt),
a slim-arm version, and a Family version with gold piping.

**Passives (transformed only)**
- **Stamina of Atlas:** 50 health, 35% damage resistance, no hunger drain.
- **Strength of Hercules:** +10 melee.
- **Speed of Mercury:** +40% speed.
- **Courage of Achilles:** immune to fear effects (Warden darkness, T-Rex roar, Raven's fear).
- **Weakness:** magic damage (potions, evokers, witches, Doctor Fate, Raven) hurts you 50% more. In the comics magic is the
  champion's weakness.
- **Lightning bar (Slab):** refilled to full by every transform bolt. It also fills during thunderstorms and when lightning
  strikes near you.

| Key | Move | Spec |
|---|---|---|
| (chat) | **SHAZAM!** | transform or turn back, lightning strike, 12 dmg within 3 blocks, refills the Lightning bar. 4 s gap |
| (double Space) | Flight | fast flight, cape streaming |
| R | Lightning Hand | lightning bolt from the hand, 9 dmg, chains to 2 nearby creatures. 8 |
| Shift+R | Call the Lightning | real lightning strike where you look (64 blocks), 22 dmg, sets fire only if mob griefing is on. 30 |
| G | Hercules Punch | 16 dmg, launches the target 15 blocks; it takes extra damage if it hits a wall. 10 |
| Shift+G | Atlas Lift | grab a creature (or lift a big mob you couldn't normally move) and hurl it where you look. 15 |
| Z | Zeus Storm | 5 s: lightning hits a random hostile within 16 blocks every half second, 10 dmg each. 50, 15 s cd |
| Shift+Z | Eternity Strike | rise up, the sky darkens, a huge bolt hits you, then you slam down: 50 dmg within 10 blocks, Lightning bar emptied. 100, 3 min cd |
| **X** | **Mercury Dash** | red blur 12 blocks along your look, light crackle trail |
| Shift+X | Thunder Tackle | fly-tackle up to 20 blocks, carrying the first creature into the ground for 18 dmg with a lightning burst. 20 |
| C | Thunderclap | clap shockwave cone, 12 dmg, knockback, breaks glass only if mob griefing is on. 15 |
| Shift+C | Achilles Stand | 4 s: you take no damage, every hostile within 16 blocks targets you (threat table), and squadmates take 30% less. 30, 30 s cd |
| V | Wisdom of Solomon | 10 s: every creature's health, armour and weakness shows above it, and the best tool or weapon against the block or mob you look at is hinted |
| Shift+V | **Share the Power** | aim at a squadmate (transformed): they join the **Shazam Family** for 10 minutes, so typing "Shazam" transforms *them* too (half your health and damage, same keys except Shift+Z, gold suit trim). Up to 5 family members. 10 min cd |

**Possible boss later:** **Black Adam**. He says "Shazam" too: when he's below half health, typing "Shazam" near him strikes
*him* with lightning and turns him back into a mortal for 5 s. That's the classic trick.

---

## Build split
Two parallel worktree agents (FATE, SHAZAM), alongside the four Titans agents. Lang text in `scratchpad/<feature>/lang_*.json`.
