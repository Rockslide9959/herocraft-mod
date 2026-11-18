# Squad knockdown & revive (v0.17.5)

User request: squad members who get killed are knocked down first and crawl; 75 s knockdown; no damage and no aggro
while knocked; a teammate holds right-click on them for 5 s to revive; the only on-screen indication is a vanilla-style
bar slowly draining at the top; while being revived the countdown pauses and a second bar in the middle shows the progress.

Code: `squad/SquadKnockdown` (server rules, all tuning constants), `squad/SquadKnockdownHudPayload`,
`squad/SquadKnockdownGiveUpPayload`, `client/squad/SquadKnockdownClient` (middle bar, give-up hold, outline test),
mixins `SquadKnockdownPlayerMixin` (crawl pose + `canBeSeenAsEnemy`), `SquadKnockdownMobTargetMixin` (`Mob#setTarget`),
`SquadKnockdownPayloadMixin` (drops power packets), `SquadKnockdownHealMixin` (no healing while knocked), client `SquadKnockdownInputMixin` (no jump/sneak),
`SquadKnockdownGlowMixin` + `SquadKnockdownGlowColorMixin` (squad-only red outline). Tests: `SquadKnockdownGameTests`.

## Rules

| | |
|---|---|
| When | A killing blow on a squad member, **if at least one other squad member is online, alive, not a spectator, not knocked, in the same dimension**. Otherwise a normal death. |
| Order | Last death check: runs in an `ALLOW_DEATH` phase after `DEFAULT` (every power death save, Phoenix, Symbiote, Wolverine, Hulk, ...) and before the Galactus orbit grave. A Totem of Undying in either hand fires instead. Anything tagged `bypasses_invulnerability` (`/kill`, the void, serum rejection, Omnitrix extraction) is never a knockdown. The main death hook's power cleanup (`clearTransient` etc.) has already run, so flight, suits, constructs and held moves drop as they would on death; a Sentry Void takeover ends (`VoidTakeover.End.DIED`). |
| Knocked | 1 health (half a heart), absorption cleared, health held there (`LivingEntity#heal` refused by `SquadKnockdownHealMixin`, so no regen, potions or heal powers, and no heart-bar blinking), hunger and air frozen, fire put out, no fall damage. **No damage taken** (except `/kill` and the void) and **none dealt** (any damage whose attacker is a knocked player is vetoed). |
| Aggro | `Player#canBeSeenAsEnemy` false (every vanilla target goal/sensor), `Mob#setTarget(knocked)` becomes `null`, mobs within 64 blocks that were on them let go (incl. brain `ATTACK_TARGET` / `ANGRY_AT`), `BossTargets.isVictim` false (so `BossThreat` never records or keeps them), `HeroTargets.canHarm` false (no ability grabs/shoves). |
| Body | Vanilla swim/crawl pose (0.6 box) forced both sides; vanilla crawl speed (sneak speed, 30% of walking). Jump strength x0, jump/sneak inputs cleared, sprint off. Dismounted, elytra closed, survival flight off. |
| Hands | No attacking, using items/blocks/entities, placing, breaking, or opening containers (Fabric callbacks, both sides). Every `projecthero:` C2S packet except `squad_*` ones is dropped (one gate for all power keys, H included). Look around, crawl, chat, own inventory: allowed. |
| Countdown | 75 s. **Re-knocked within 60 s of a revive: 45 s, then 25 s** (stays 25 s while the chain continues). |
| Revive | A squadmate holds right-click on the knocked player for 5 s straight, within 3.5 blocks (eyes to body). Vanilla resends the interact every 4 ticks while the button is held; the hold breaks if none arrives for 6 ticks (let go / looked away), if they step out of reach, get hurt (any damage taken), are knocked, or die. A broken hold restarts from 0. **One reviver at a time** (a second one gets an action-bar note); a reviver can revive one player at a time and cannot attack while holding. Only squadmates; anyone else's right-click is a normal interaction. |
| Paused | While a revive is in progress the countdown does not tick and the "nobody left" timer is reset. |
| Revived | Stands up (vanilla picks standing/crouching/crawling by the room there) with **6 hearts**, **Resistance II + Regeneration I for 5 s**, **2 s** in which nothing can hurt them (except `/kill`/void), soft totem particles + quiet totem/level-up sound, squad chat line. |
| Bleed out | Countdown out, or **give up (hold jump 3 s, not while being revived)**, or **nobody left standing for 5 s** (all others knocked, dead, offline or in another dimension = team wipe), or logging out while knocked. Real death via `projecthero:bled_out` (bypasses armour/effects/invulnerability, no knockback): items drop per gamerules, death screen, "<player> bled out". |
| Logout | **Logout = bleed out** (like most games), so nobody dodges it by leaving. Exceptions: the single-player/LAN **host** closing the world, and a **server stopping**: the knockdown is saved (`squad_knocked_saved` attachment) and resumes on the next join, with 30 s (instead of 5 s) for a squadmate to show up before the "nobody left" rule. |
| Reset | All static tables (`KNOCKED_PLAYERS`, `RECENT`, `SHIELD_UNTIL`, and the boss bars that hold players) cleared by `ServerStateReset` on server stop. |

## UI

- **Knocked player**: a real vanilla boss bar at the top, red, titled "Knocked down", draining over the countdown (client-side
  interpolation is vanilla's). It turns **white while paused** (being revived). Stacks with raid/boss bars like any other.
  The squad Locator Bar hides while you are knocked so the top is the bar's alone. No vignette, no text, no other overlay.
- **Middle bar** (only while something is happening), boss-bar sprites, 182 px, 24 px under the crosshair, label above:
  green "Being revived" (knocked player) / "Reviving <name>" (reviver), filling over 5 s; red "Giving up" while the knocked
  player holds jump. Label trimmed to the bar's width (never sprawls).
- **Squadmates**: knocked squadmates are outlined soft red on squadmates' screens only (local `shouldEntityAppearGlowing`,
  never the broadcast glow flag), their Locator Bar face is washed red with a pulsing red frame, and the squad screen (P)
  card says `KNOCKED 52s` (red chip, red pulsing strip) with a tooltip line.
- Chat (squadmates): "<name> is down! Hold right-click on them to revive them." and "<reviver> got <name> back on their
  feet." The knocked player only gets the revive line.

## Sync

`squad_knocked` attachment (synced to self + trackers, not saved) is what clients read for the pose, input lock and
outline. The server's own source of truth is `SquadKnockdown.KNOCKED_PLAYERS` (concurrent: the packet filter reads it on
the network thread). The roster payload (`SquadInfoPayload.Member.knockedTicks`) carries the seconds for the P screen and
the Locator Bar. `SquadKnockdownHudPayload` goes every tick to the knocked player and the reviver only.

## Not covered / choices

- Two revivers don't speed it up (simpler to read; matches the "hold for 5 s" rule exactly).
- Drowning: no damage while knocked, and air is held full, so you can't drown while down.
- A power death save that triggers on the bleed-out itself is allowed to save you (the save had its chance at the fatal
  hit too; if it was on cooldown then and is ready now, it wins).
- Squad friendly fire on does not let squadmates finish a knocked player (knocked players take no damage at all).
