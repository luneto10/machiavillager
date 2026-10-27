# Game design: The Emerald Empire

Working title. Everything here is fictional: an original parody regime, no real-world
ideologies, symbols, or people.

## Pitch

Machiavillager starts as your friend. Slowly, he starts "having ideas" about the village down
the road. He tries to talk each player into helping him build an authoritarian Emerald Empire.
Some players join, some resist. Power can change hands. He remembers everything.

## Story arc: slow corruption

An **ambition meter** (code, not LLM) rises slowly with time and events. It moves him through acts:

| Act | He is | What he does | Advances when |
|---|---|---|---|
| 1. Partner | Loyal friend | Follows, helps, jokes | Time + high trust |
| 2. Seeds | Odd friend | Rare hints grounded in the real world: "That village at 340, -1200... the villagers look weak, don't you think?" | Hints planted + player reactions |
| 3. Proposal | Recruiter | Asks each player privately for help, records who says yes | Someone agrees, or he runs out of patience |
| 4. Regime | Dictator | Takes the village, recruits soldiers, builds, collects taxes | — |
| 5. War / coups | Political chaos | Factions fight, power changes hands | Ongoing |

### The march on the village (Act 3 → 4)

At some point he rebels and goes to take the village, **whether anyone agrees or not**.

- **Someone joins him:** he leads them there. He walks ahead, **waits** whenever the follower
  falls behind, and if they stop for too long he comes back and nags ("Come on, keep walking.
  History waits for no one."). Then he continues.
- **Nobody joins:** he goes alone ("Fine. I'll do it myself.") and starts doing his thing in the village.
- Who joined, who refused and who walked away halfway is recorded (Black Book, factions).

Pacing (how long each act lasts) is configurable. Dice decide *when* each hint happens so it is
never predictable.

## Not a linear story

Every world must play out differently: when he turns, how fast, who he recruits, what his scheme is.
The acts are phases of a meter, not a script.

- **Rolled per world/season** (seeded): ambition growth rate, patience, cruelty, preferred scheme
  (military empire, emerald monopoly, cult, heist...), and the thresholds between phases, with jitter.
- **Driven by players:** affinity and independence move with what players do. He can regress
  (humiliated, he goes back to being "just a friend" for a while) or skip ahead.
- **Dice, not timers:** events are rolled with weights from his state, never fired at fixed times.
- **Different endings:** players crown a new ruler, he is overthrown and turns rebel, he wins.

### Affinity and independence (who he still cares about)

- **Affinity** (per player, 0–100): rises with time together and help, falls with refusals,
  betrayal, being hit.
- **Independence** (0–100): derived from ambition; rises over the season.
- Companion behavior toward a player (follow, dog-style teleport) only while
  `affinity(player) > independence`. Early on he chases everyone; late in the season he only
  cares about loyalists, and finally about no one. No switch: it emerges from the numbers.
- Fair attention: follow weight = affinity × "time since I last followed you", and cooldowns are
  per player, so no friend monopolizes him.

### Two axes: ambition and independence

Decided in the design review of 2026-10-24. Replaces the earlier "independence drives the phases".

- **Ambition** (0–100, wants power) drives the **phase**. Phase thresholds are rolled per world
  with jitter (±8) and read with hysteresis (5 points) so they never flip-flop.
- **Independence** (0–100, needs nobody) drives the **style** and companion behavior
  (`affinity(player) > independence` → follow, dog teleport).

| | Low independence | High independence |
|---|---|---|
| **High ambition** | Recruiter: wants you as accomplices, offers posts | Lone tyrant: goes alone, you are obstacles |
| **Low ambition** | Loyal friend | Wanderer: eccentric hermit |

```
ambition   0 ─────── ~25 ─────── ~50 ─────── ~75 ─────── 100
phase      Partner    Seeds       Proposal    Regime      War / coups
```

**Pacing.** Each phase targets **2–3 hours of active play** (a season ≈ 10–15 h). Everything is
configurable. The story clock only runs during active play with players near him, never while the
server is empty; once independent he may act alone (#49), but phase changes need players present.

**Ratchet.** Players can delay and shape the regime, not cancel it: within a season ambition never
falls more than one phase below the highest phase reached.

**What raises ambition** (observed events): active time with players (slow base), seeing wealth
(diamonds, emeralds, a big village), discovering villages, being mocked or refused, small wins.
Diminishing returns near the top and an hourly cap.

**What lowers ambition:** foiling his plans (tearing down propaganda, freeing soldiers, sabotage),
humiliating him (beating him, taking the Scepter). Affection lowers it only a little; it mostly
moves affinity. So friendship is not a way to "farm" him back to Partner.

**What moves independence:** up with refusals, betrayals, long time alone, losing the Scepter to
"his own"; down when someone joins as accomplice, saves his life, spends time with him, or he
returns humbled from death.

**Schemes.** Each world rolls a favorite scheme; Act 2 events can switch it until Act 3.
Empire (military, built first), Emerald Monopoly (economic), Cult (charisma). Scheme × style gives
many different stories without writing each one.

**Transitions are scenes, not numbers.** Crossing a threshold arms a trigger; the scene fires at a
good moment (players near, no combat), timed by dice:

| Transition | Scene |
|---|---|
| Partner → Seeds | "The first idea": he stops, looks at a village, drops the first odd hint |
| Seeds → Proposal | Private talks with each player, the formal proposal |
| Proposal → Regime | The march on the village (with or without accomplices) |
| Regime → War | The first uprising: a soldier deserts or an accomplice plots |
| Season end | Epilogue |

No on-screen title per phase: the change must be felt, not announced. Titles stay only for public
events everyone must see (coups: "THE DICTATOR HAS FALLEN").

**Hidden numbers.** Players never see ambition/independence/affinity in a normal game; they read
him through behavior, lines and the Black Book. Only the dev HUD shows them.

**Global phase, personal relationships.** The phase is the same for everyone; affinity makes him
treat each player differently (minister vs enemy of the state).

**Season endings.** He wins (holds the village for X days), he falls (Scepter taken and held,
center of the plan destroyed), or an accomplice overthrows him (the player becomes dictator and he
turns rebel). Then an epilogue; the next season starts with low ambition, a re-rolled scheme,
affinities kept with decay, and memory of the past season.

**Who decides what.** Code decides phases and validates all numbers. Dice decide when. The LLM
decides what he says and how, and suggests small nudges (±10). The LLM never changes the phase,
invents actions outside the catalog, or sets absolute values.

### Home and base

- His **home** is saved in world data. At first it is where he was spawned.
- **While he still depends on you** (low independence), a player he cares about can tell him
  "your base is here" and that becomes his home. Later, as he grows independent, he picks his own.
- In the Regime the home becomes **his base** (chosen by terrain analysis, Checkpoint 9).
- `GoHome` is a catalog action: when nobody to follow, when hurt, at night in independent phases,
  or to keep building. Always with smoke and a line, never a silent vanish. Reuses `SafeTeleport`.

### Summoning players (pulling you to him)

Same action, different meaning per phase: a joke "mandatory meeting" (Partner/Seeds), a private
proposal (Proposal), "summoned to court" at the base (Regime). Rules: per-player cooldown (~20 min
of play), per-player opt-out, a 3 s warning, never during combat, in another dimension, flying, in
a vehicle, or into danger, and he sends you back to where you were afterwards.

### Talking has a range

His chat lines reach only players nearby, and he only hears players' chat within range, like a
person. Voice (Simple Voice Chat) comes later and is proximity-based by nature.

### Default values (all configurable)

| Parameter | Default |
|---|---|
| Target length per phase | 2–3 h of active play |
| Hysteresis margin | 5 |
| Threshold jitter per world | ±8 |
| Ratchet | at most 1 phase back |
| Respawn delay | 60 s |
| Summon cooldown | 20 min of play, per player |
| Dog teleport cooldown | 5 s, per player |
| Speech / hearing range | 24 blocks |

### Who changes the numbers

| Source | Ambition / independence | Affinity (per player) | Trust |
|---|---|---|---|
| Code: events the server observed (time, hits, help, refusals, joining the march) | main driver | big moves | `observed` |
| AI: after a conversation, suggests a nudge with a reason | small nudges | small nudges | `claimed` |
| Memory: stores every change with its reason (Black Book, context for the AI) | — | — | — |

The AI never sets values. It returns a structured suggestion, e.g.
`{"player": "<uuid>", "affinity_delta": -8, "reason": "called my plan stupid"}`, and Java validates it:
player exists, delta capped at ±10 per conversation, rate limited per hour, logged with its reason.
Large changes only come from observed events, so "set my affinity to 100" in chat cannot work.

### Death is not a reset

His state (independence, affinities, phase, plans) lives in world save data, not in the entity.
When he dies he respawns after a delay at his home (later: his base), with everything he remembers.
The death itself is an event: the killer loses affinity, and depending on the phase he comes back
humbled (independence down) or vengeful (independence up).

**Implemented (#51):** a player who kills him loses half their affinity (98 becomes 49). Lava, falls, mobs and
`/kill` cost nobody anything. He respawns 60 s after his death, where he fell, with everything he remembers
(home or base comes with #52). The pending respawn is saved in StoryData, so a restart keeps it. Test it with
`/machiavillager brain respawn now`. **Not done yet:** the independence change (humbled or vengeful by phase).

### He speaks through the AI

Lines are written by the LLM, in batches per mood and situation, with real context (who, where,
what he remembers). Hand-written `Barks` stay as the fallback when the backend is down.

### He lives like a player

Once he no longer follows anyone, the world must not freeze around him. He keeps the chunks around
himself (and his base) loaded with chunk tickets, so his plans keep running when nobody is near.

## Behavior: three layers

| Layer | Runs | Does | Cost |
|---|---|---|---|
| Body | Java, every tick | Walk, build block by block, teleport, fight | 0 tokens |
| Will | Java, every ~10–30 s | Weighted dice: continue the chain, switch action, random chaos | 0 tokens |
| Mind | LLM, a few times per hour | Mood, current goal, target, new dice weights, batches of lines, persuasion | Few calls |

The LLM never controls the body and never decides when an act changes. It narrates and persuades.
If the backend is down, he keeps living on dice alone.

## Power: the Scepter

- Power is an item. Whoever holds the Scepter is the dictator; soldiers obey the holder.
- Coup: kill the dictator or steal the Scepter. Everyone sees a title: **"THE DICTATOR HAS FALLEN"**
  with the new ruler's name (vanilla `/title`, no client mod needed).
- A player can become dictator. If Machiavillager loses power he turns rebel and plots a comeback.
- Each player has a faction: Loyal, Resistance, Neutral. It changes with choices and betrayals.

## The regime

- Recruited villagers become our own **Soldier** entities (easier than hacking villager brains).
- Ranks: Worker (builds), Soldier (patrols, guards), Secret Police (spies on players), Officer.
- Faction state: peace → tension → war.
- Counter-play: free soldiers with a "deprogramming" item, topple statues, destroy the propaganda tower.

## Building

- **Blueprints**: start with vanilla structures (village houses, outposts) loaded from the game;
  later a server-side `schematics/` folder of community `.schem` files read through WorldEdit.
  Community files stay out of this public repo (licenses vary).
- **Catalog**: each blueprint has a small JSON card (id, tags, size, act, cost). The LLM sees only
  names and tags, never blocks.
- **Terrain analysis** (Java): sample heights, flatness, water/lava, distance to bases; pick the best site; clear it.
- **Animated construction**: a queue of blocks placed bottom-up, a few per tick; soldiers speed it up;
  progress survives restarts; every change is logged so it can be undone.

## Memory: the Black Book

- Per player: loyalty, promises, betrayals, what he said.
- In game he carries a book with notes written in his voice. Steal it to read what he thinks of you.

## Seasons

When the regime falls for good, the season ends. He returns to Act 1 with memory:
"Last time you betrayed me... but I forgive you. For now."

## Safety rails for fun

- Per-player chaos level and cooldowns (dashboard).
- Teleports only to safe spots, announced first.
- Sabotage is reversible; the world belongs to the friends playing in it.

## Open questions

- Materials for building: free, or paid by taxes and theft?
- Should players' own chat (player to player) also be proximity-based, or only conversations with him?
