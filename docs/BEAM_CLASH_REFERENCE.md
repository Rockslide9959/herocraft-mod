# Beam clash reference (v0.17.3, reworked v0.17.5)

Code: `beamclash/BeamClash` (server), `beamclash/network/BeamClash{,Mash,Forfeit}Payload`, `client/beamclash/BeamClashClient`,
mixins `mixin/BeamClashFireMixin` (main), `client/mixin/BeamClashInputMixin`, `BeamClashKeyMixin`, `BeamClashParticleMixin`.
Tests: `BeamClashGameTests` (+ `SaiyanKitGameTests.kamehamehaJoinsABeamClash`).

## The convention for every beam

- Call `BeamClash.report(owner, from, dir, range, power, rgb)` **every live tick** (not just damage ticks). `range` is how far the
  beam can reach, not where it stops on a creature.
- While `BeamClash.locked(owner)` the beam's own tick keeps reporting but **skips its drain, damage, fire, knockback, effects,
  block ignition and its duration count** (an age counter isn't advanced; a game-time start is moved forward a tick). Usually an
  early `return` right after the report. Release handlers may still end the beam; the clash doesn't care.
- The renderer hides the beam while `BeamClashClient.hides(owner)` (or `hidesFrom(start)` for an ownerless beam): the clash draws both.
- Mob / boss beams only need the report: a locked mob is stood still and anything it deals is blocked.

## Start rule (v0.17.5, forgiving)

All of:
- at least one side is a player, the two may harm each other (`HeroTargets.canHarm` for player sides), same level, neither already
  clashing, the pair isn't on its 3 s cooldown;
- the beams point **at least 120 degrees apart** (`FACING`, dot <= -0.5);
- the shooters are **1.5 .. (rangeA + rangeB + 3) blocks** apart (`REACH_MARGIN`), so short beams count;
- **either** the two beam segments pass within **2 blocks** (`TOUCH`) of each other, **or** each beam is aimed within **25 degrees**
  (`AIM_CONE_DEG`) of the other shooter's body centre;
- clear line (blocks) from each start to the struggle point.

The point forms on the line between the two starts: at the closest approach when the beams touch, else halfway (or where the
shorter beam ends), clamped to 10..90 %. (v0.17.3 needed 1.6 blocks and 110 degrees, measured between the beams only, and dropped
the clash the moment a beam stopped reporting or turned 45 degrees off the point, which is why short beams and eye beams rarely held.)

## Locked in

- The clash has its own lifetime: reports only refresh where each beam starts, its power and colour. A beam that stops (short
  Kamehameha, key released, drained) is held where it was; the clash keeps drawing it.
- Ends only by: the point reaching a side (balance +-1), a **forfeit**, a side dying / leaving / being dragged 8+ blocks further
  apart than at the start (no outcome), or **20 s** (`MAX_TICKS` 400): whoever is ahead wins; a dead heat throws both back, no damage.
- Server, each tick: players get Slowness X (rooted) and are snapped back to their hotbar slot; mobs have navigation stopped and no
  horizontal motion; both are extinguished.
- Client (`BeamClashKeyMixin`): every key reads up and its presses are dropped except attack, chat, command, player list, screenshot,
  fullscreen, perspective and smooth camera. Right click does nothing. The local player hangs in place (no gravity, zero motion),
  look stays free.
- **No damage meanwhile** (`blocksDamage`, Fabric ALLOW_DAMAGE): anything whose attacker or direct entity is a locked shooter is
  blocked (both beams, any leftover hits, other attacks), and a locked shooter takes no burning. `BeamClashFireMixin` stops a locked
  shooter being set alight at all. Third parties can still hit a locked shooter.

## Push and outcome

- Players mash **left click** (`BeamClashMashPayload`): +0.03 x power per click, at most 20 a second (burst of 3).
- Mobs push 0.012 x power a tick. The stronger beam leans 0.0015 x (power difference) a tick.
- Win: the loser takes **14 + 10 x winner power** (bosses capped at 8 % max health) and is thrown back 1.4 blocks/tick + 0.5 up.
  The outcome is dealt after the clash is marked over, so it gets past `blocksDamage`.

## Escape = forfeit

- `Minecraft.pauseGame` during a clash (Escape, or anything else that would open the pause menu, focus loss included) sends
  `BeamClashForfeitPayload` and **does not open the menu**. One send per clash; the next Escape after the clash pauses normally.
- Server `forfeit`: the other side wins, the quitter is shoved 0.7 + 0.3 up, **no damage**, both released, pair cooldown.

## HUD

Top middle (`max(40, height/7)`): "BEAM CLASH" + seconds left, "You" vs the other's name in their beam colours (lifted toward white),
a 182 x 7 tug bar (your colour from the left, white marker), "Mash <attack key> to push!" (pulsing) and "Esc to forfeit", both
wrapped to 220 px. The result goes on the action bar (won / lost / you forfeited / they gave up / draw).
`BeamClashPayload` (every tick, to players within 128 blocks and always to both shooters) carries ticks left, the winner id and the
forfeit flag on the last packet.

## Numbers in one place

| Constant | Value |
|---|---|
| FACING | dot <= -0.5 (120 deg) |
| TOUCH | 2.0 blocks |
| AIM_CONE_DEG | 25 |
| REACH_MARGIN | 3 blocks |
| MAX_TICKS | 400 (20 s) |
| PAIR_COOLDOWN | 60 ticks |
| BREAK_DISTANCE | 8 blocks past the start gap |
| MASH_GAIN / MOB_PUSH / LEAN | 0.03 / 0.012 / 0.0015 |
| BACKLASH | 14 + 10 x power, bosses <= 8 % |
