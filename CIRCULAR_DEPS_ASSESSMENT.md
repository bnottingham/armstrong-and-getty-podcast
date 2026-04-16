# Circular Dependency & Layering Assessment

**Scope:** 25 Kotlin source files under `Android/app/src/main/java/com/nomnomsom/armstrongandgetty/`.
**Method:** Grepped every `import com.nomnomsom.armstrongandgetty.*` statement across all Kotlin files and built the package-to-package edge list by hand. Verified suspicious bidirectional candidates at the class level.

## Package dependency graph (edge list)

Format: `source -> {targets}`. Only internal (`com.nomnomsom.armstrongandgetty.*`) edges are listed.

```
root (MainActivity, AGPodcastApp)
  MainActivity        -> ui.screens.episodelist, ui.screens.player, ui.screens.xfeed, ui.theme
  AGPodcastApp        -> work

work
  NewEpisodeCheckWorker -> root (MainActivity, R), data.model, data.repository

di
  AppModule           -> data.local

media
  PlaybackController  -> (none internal)
  PlaybackService     -> data.local
  CastOptionsProvider -> (none internal)

data.repository
  PodcastRepository   -> data.local, data.model, data.remote

data.remote
  AudioDownloader     -> data.model
  RssFeedParser       -> data.model
  XFeedParser         -> data.model

data.local
  PodcastDatabase     -> data.model
  PodcastDayDao       -> data.model

data.model             -> (leaf, no internal imports)
util                   -> (leaf, no internal imports)
ui.theme               -> (leaf, no internal imports)

ui.screens.episodelist
  EpisodeListViewModel -> data.model, data.repository, media
  EpisodeListScreen    -> data.model, media, ui.theme, util

ui.screens.player
  PlayerScreen         -> data.model, ui.screens.episodelist, ui.theme, util

ui.screens.xfeed
  XFeedViewModel       -> data.model, data.remote
  XFeedScreen          -> data.model, ui.theme
```

## Topological ordering

The graph is a DAG. A valid topological order (lowest to highest layer):

1. `data.model`, `ui.theme`, `util`  (leaves)
2. `data.local`, `data.remote`        (depend only on `data.model`)
3. `media`                            (depends on `data.local` only)
4. `data.repository`                  (depends on `data.local`, `data.model`, `data.remote`)
5. `ui.screens.episodelist`, `ui.screens.xfeed`
6. `ui.screens.player`                (depends on `ui.screens.episodelist`)
7. `di`                               (composition root — DI module)
8. `work`, root (`MainActivity`, `AGPodcastApp`)  (entry points)

A valid topological order exists, therefore **there are no cycles**.

## Cycles found

**None.**

Every bidirectional candidate I checked was one-directional:

| Candidate pair                   | Verdict                                                                                          |
|----------------------------------|--------------------------------------------------------------------------------------------------|
| `ui` vs `data`                   | `ui -> data` only. `data` never imports `ui`. Clean.                                            |
| `ui.screens.episodelist` vs `media` | `episodelist -> media` only. `media` never imports `ui`. Clean.                              |
| `media` vs `data`                | `media.PlaybackService -> data.local` only. `data` never imports `media`. Clean.                |
| `ui.screens.player` vs `ui.screens.episodelist` | `player -> episodelist` only (shared VM pattern). `episodelist` does not import `player`. Clean. |
| `util` vs anything               | `util` has zero internal imports. Leaf.                                                          |
| `work -> MainActivity` (root)    | Root package also contains `AGPodcastApp -> work`. Both are application entry points in the same root package; this is Android's standard pattern (app boot schedules the worker; worker launches notification intents pointing at `MainActivity`). Not an architectural cycle between layers. |

## Layering violations found

**None.**

- No `data.*` file imports `ui.*`, `media.*`, or `work.*`.
- No `util` file imports anything internal.
- No `media.*` file imports `ui.*`.
- `di/AppModule.kt` only imports `data.local` (`PodcastDatabase`, `PodcastDayDao`). That is minimal and correct for a composition root.

## Observations (not violations)

1. **`ui.screens.player` imports `EpisodeListViewModel`.** `PlayerScreen` takes `EpisodeListViewModel` as a parameter (see `PlayerScreen.kt:69`). This is an intentional shared-VM pattern between two sibling screens — one-directional, not a cycle. MED-confidence: could be cleaned by hoisting shared state into a dedicated `PlayerStateHolder` or by having each screen own its own VM and communicate through the repository. Not required for dependency hygiene.
2. **`data.repository.PodcastRepository` imports 7 types across 3 sub-packages.** Normal for an aggregating repository; no issue.
3. **`work.NewEpisodeCheckWorker` imports `MainActivity` and `R` from the root package.** Used to build the notification tap intent. Standard Android pattern; not a cycle because both are in the same root package and the root-level classes do not import `work` types (only `AGPodcastApp` references `work`, which is the opposite direction from the worker's reference to `MainActivity`).

## HIGH-confidence fixes

**None applicable.** There are no cycles or layering violations to cut.

## MED-confidence fixes (skipped per task instructions)

- Introduce a dedicated `PlayerViewModel` so `ui.screens.player` no longer depends on `ui.screens.episodelist`. Rationale: sibling UI packages sharing a VM is a smell even though it's one-directional. **Skipped** — no cycle exists, and the task says not to "introduce new interfaces just for architectural purity when there's no cycle."

## LOW-confidence fixes (skipped)

- Consider a `domain/` package for shared model/port definitions if the app grows. Not needed at 25 files.

## Conclusion

The codebase is already well-layered. Dependencies flow cleanly from leaves (`data.model`, `ui.theme`, `util`) up through `data.local`/`data.remote` -> `data.repository` -> `ui.screens.*` -> entry points, with `media` sitting between `data.local` and the UI layer. **No untangling needed.**
