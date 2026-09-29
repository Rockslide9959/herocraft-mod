# v0.14.1 — getting a mutation (every path checked)

Every one of the 27 mutations is obtained the same way: craft its **reagent**, brew it into its **base potion** to get
the **serum**, drink it, then perform the power's **exposure event** before the serum wears off (60 s). A lab
device can always stand in for the natural event where one exists.

## What was broken and is fixed

| Problem | Fix |
|---|---|
| Elasticity's trigger (fall 10+ blocks onto a slime block) was never coded — unobtainable except by random serum | Detector added; the Gravity Distortion Rig also fires it |
| Water needed 60 s continuously underwater = the serum's whole duration, and the serum replaced its water-breathing base | 15 s underwater, and the serum keeps you breathing while you try |
| Telekinesis and Teleportation reagents had identical recipes (only one was craftable) | Teleportation reagent: Ender Pearl + Amethyst Shard + **Chorus Fruit** |
| Energy Absorption's reagent needed redstone the guide never listed | Recipe matches the guide (Gold + Copper + Amethyst) |
| Drinking a serum at full mutation capacity still cost you the Symbiote / your oldest hero, gave nothing, and spammed every tick | Capacity is checked first: nothing is lost, you are told once, the attempt ends |
| Milk, Purge, Resurrection, a totem, Max Steel's transform, Protocol Phoenix, death or a relog silently cancelled a pending serum | The attempt is saved with the player; a stripped marker effect is put back until the real expiry |
| Drinking a serum while another was pending could do nothing (vanilla keeps the higher effect level) | The newest serum always replaces the pending one |
| Cryokinesis' powder-snow check read a flag vanilla resets every tick | Checks the blocks at feet / eyes |
| Mutagenic / Prismatic serums skipped the Primary-slot rule (2 heroes + Symbiote + mutations possible) and recorded no research | Same slot rule and research/advancements as a serum mutation |
| Six devices named by the guide never existed | Built: Charged Copper Plates, Crystal Test Chamber, Enchanting Resonance Array, Controlled Blast Chamber, Resonant Chamber, Gravity Distortion Rig |
| Redstone devices at research sites needed a lever you had to bring | Every device also fires on right-click |
| Meteor Impact sites had 2 amethyst blocks (the Crystalkinesis trigger needs 8); the Mass Compression Chamber (Size) spawned nowhere | Meteor sites get a 3x3 amethyst floor; Research, Power, Geological and Government sites now also house the extra devices |
| `/projecthero power serum / research / status / active / list` were unreachable | Registered |

## Devices and what they fire

| Device | Activation | Exposure kinds |
|---|---|---|
| Overloaded Redstone Coil | redstone / right-click | electrical discharge (Strength, Electro, Speed) |
| Charged Copper Plates | redstone / right-click | electrical discharge |
| Experimental Light Projector | redstone / right-click | high-intensity light (Laser Vision) |
| Unstable Gravity Plate | redstone / right-click | gravity distortion (Flight, Gravity) |
| Gravity Distortion Rig | redstone / right-click | gravity distortion, slime impact (Elasticity) |
| Pressure Chamber | redstone / right-click | pressure (Wind; also Flight / Gravity) |
| Geological Resonance Chamber | right-click | geological resonance, amethyst geode |
| Crystal Test Chamber | right-click | amethyst geode (Crystal), powder snow (Cryo) |
| Enchanting Resonance Array | right-click | psionic resonance (Telekinesis), ender pearl (Teleportation) |
| Controlled Blast Chamber | redstone / right-click | explosion (Durability, Shockwave), fire (Pyro), energy overload (Energy Absorption) |
| Resonant Chamber | right-click | resonant horn (Sonic Scream) |
| Molecular Compression Chamber | redstone / right-click | molecular compression (Density) |
| Mass Compression Chamber | redstone / right-click | mass compression (Size) |
| Hydrostatic Test Tank | right-click | submersion (Water) |
| Electromagnetic Coil | redstone / right-click | magnetic field (Magnetic) |

Natural-only (no device, all easy): Super Regeneration (drop to 3 hearts), Invisibility/Light (direct sunlight),
Spider Climbing (spider bite), Shadow (true darkness at night), Plant (surrounded by plants under open sky).
