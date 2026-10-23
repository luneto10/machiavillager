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

### Independence drives the phases

There is no separate "act counter": the phase is read from independence, using thresholds rolled
per world (with jitter) and hysteresis so it never flip-flops at a boundary.

```
independence  0 ─────── ~25 ─────── ~50 ─────── ~75 ─────── 100
phase         Partner    Seeds       Proposal    Regime      War / coups
```

- **Slow on purpose:** growth per real hour is capped and has diminishing returns near the top
  (`gain × (1 − independence/100)`), so reaching the end takes many sessions, never one evening.
- **Players can push it down:** spending time with him, gifts, saving his life, foiling his plans,
  humiliating him. Dropping below a threshold (minus the hysteresis margin) moves him back a phase.
- **Events can jump it:** a refused proposal or a betrayal adds a lump; these are rolled, not fixed.

### Who changes the numbers

| Source | Independence | Affinity (per player) | Trust |
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
- How fast does Act 1 → Act 4 go in real time?
