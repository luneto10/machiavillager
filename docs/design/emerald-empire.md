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
