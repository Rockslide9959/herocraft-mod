# Hawk Moth (Nooroo, the Butterfly Miraculous) -- v0.16.6

The Hawk Moth kit on top of the shared Miraculous core (`docs/MIRACULOUS_REFERENCE.md`: the Butterfly Brooch, Nooroo,
"Nooroo, dark wings rise!" / "Dark wings fall!", feeding him honey, the Shift+Z countdown, the HUD). The core gives every
Miraculous suit +12 armour, +6 toughness, +10 max health, +5 melee, +25% speed, +35% jump and no fall damage; nothing
here repeats that. Every number lives in `HawkMothConfig`.

## Code

| What | Where |
|---|---|
| The six keys, the synced kit values, the shield / dash damage rule | `miraculous/hawkmoth/HawkMothKit` |
| Akumatized mobs, Champions, the offer, the damage veto, the outlines' sync | `miraculous/hawkmoth/HawkMothAkumas` |
| Registration, events, the akumas' server tick | `miraculous/hawkmoth/HawkMothMod` |
| `/hawkmoth accept / refuse / link <message>` | `miraculous/hawkmoth/HawkMothCommand` |
| The akuma butterfly entity (`projecthero:akuma`) | `miraculous/hawkmoth/entity/AkumaEntity` |
| Payloads (marks to everyone, sense to him, swarm, link) | `miraculous/hawkmoth/network/*` |
| Suit (wide / slim, per-texel reveal) | `client/miraculous/hawkmoth/HawkMothSuitRender` + `HawkMothLayer` |
| The cane with the hidden sword | `client/miraculous/hawkmoth/CaneRender` |
| Poses | `client/miraculous/hawkmoth/HawkMothPose` (anim ids 80..90) |
| Butterflies (3D model), the swarm effects, the masks | `ButterflyModel`, `ButterflyFx`, `AkumaRenderer` |
| Outlines / first person | `client/mixin/HawkMothGlowMixin`, `HawkMothColorMixin`, `HawkMothFirstPersonMixin` |
| Tests | `gametest/HawkMothGameTests` (11) |

## The moves

| Key | Move | Numbers |
|---|---|---|
| R | **Cane Strike** | 3-hit combo, 9 damage each, reach 3.6; a blow every 8 ticks at most, the combo resets after 1.5 s; the third knocks back and rests 1 s |
| Shift+R | **Cane Sword** | the blade slides out of the shaft (0.3 s), then a 6-block lunge (4 ticks x 1.5) cutting everything in the way once for 14; the blade stays out 1.5 s; 6 s cooldown |
| G | **Akuma** | an akuma flies (0.7 b/t) at the mob you look at (30 blocks); it fights for you 30 s with +50% health, +50% damage; up to 3; bosses (150+ max health, the Wither, the Dragon), players, pets and other Hawk Moths' akumas are immune; 4 s cooldown |
| Shift+G | **Recall** | every akuma flies home, the mobs are themselves again, Akuma's cooldown is cleared; Champions stay; 3 s cooldown |
| X | **Butterfly Swarm Flight** | 10 s of flight (speed 0.07); press again or land to stop; 20 s cooldown from the start |
| Shift+X | **Swarm Dash** | he turns into a cloud of white butterflies (hidden, untouchable), darts up to 12 blocks along the look (walls stop it) in 6 ticks and re-forms over 6; 8 s cooldown |
| Z (hold) | **Butterfly Shield** | a 3x3 wall of white butterflies 1.6 blocks in front; every projectile from the front is knocked out of the air (and any projectile hit from the front is cancelled); up to 10 s; 4 s cooldown after release |
| Shift+Z | **RISE, AKUMA!** | hands cupped for 1.5 s while a white butterfly darkens, then it flies at the target in sight (40 blocks); the 5-minute countdown starts on release. A **mob** becomes a Champion for 60 s: 1.5x size, 3x health, 2x damage and one power (fire: fireballs; ice: slowing ice shots, 5 x2 + Slowness II 3 s; storm: lightning on its target, 7 x2 in 2 blocks), every 2.5 s. A **player** is offered the power (only they see it): [Accept] / [Refuse], 30 s, silence = refused; accepted they are a Champion for 60 s (1.5x size, 3x max health, +100% damage, the same kind of power on their attack swing every 1.25 s). One Champion at a time; bosses immune. 60 s cooldown (10 s after a refusal / a failed flight) |
| V | **Swarm Blind** | every hostile within 8 blocks: Blindness + Slowness II for 6 s (halved on players, the server's crowd-control rule); 15 s cooldown |
| Shift+V | **Hawk Moth's Window** | the camera goes to the akumatized mob closest to where you look, for up to 15 s; your body stands still (speed and jump to 0); press again or take a hit to come back; 10 s cooldown |
| C | **Emotion Sense** | 15 s: every creature within 50 blocks glows through walls for you only -- hostile red, calm white, scared / fleeing / blinded-passive blue; players white above 70% health, light blue to 35%, blue under; refreshed every second; press again to stop; 25 s cooldown |
| Shift+C | **Akuma Link** | looking at a creature (40 blocks) with akumas out: they all turn on it. Otherwise, with a Champion player: the chat box opens with `/hawkmoth link ` and what he types goes to that player alone. Otherwise the akumas go back to guarding him |

## Akumatized mobs

* Their own target goals are lifted out (and put back on release); the akuma picks: the Akuma Link target, what Hawk Moth
  is hitting, what hit him, the fight it is already in, else the nearest threat to him within 16 blocks
  (`HeroTargets.isHostile`). Never him, his squad, his pets or his other akumas; a damage veto (`ALLOW_DAMAGE`) stops even
  arrows, fireballs and explosions from their side. Hits that are not their own melee are scaled by the bonus in the veto.
* A mob with no attack of its own (a cow, a villager) gets a simple melee goal (3 damage before the bonus).
* All the stat changes are TRANSIENT attribute modifiers: an unloaded chunk or a server stop can never leave a changed
  mob behind.
* Everyone sees the purple outline (Champion: deeper purple) and the glowing violet butterfly mask on the face.
* Everything ends on detransformation, logout, death, a dimension change and `ServerStateReset` (the kit's
  `clearSessionState` drops every static map).

## Decisions made while building

* The cane shows whenever he is transformed with an EMPTY main hand (holding anything else tucks it away); R uses it.
* Shift+G Recall frees plain akumas only; the Champion keeps its 60 s.
* The Champion player's power fires on a fresh swing of the main hand (the attack key), detected server-side.
* A Champion player isn't forced onto Hawk Moth's side: they keep their own will (and PvP rules) -- only his akumatized
  mobs are bound by the veto.
* Hawk Moth's suit head is drawn just outside the wearer's hat layer (which stays on), so the wearer's face and hat show
  only through the mask's see-through eyes and mouth.
