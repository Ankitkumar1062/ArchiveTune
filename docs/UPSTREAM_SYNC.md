# Upstream Sync

This fork keeps its `dev` branch automatically in sync with
[`rukamori/ArchiveTune` @ `dev`](https://github.com/rukamori/ArchiveTune/tree/dev)
via `.github/workflows/upstream-sync.yml`.

- **Runs:** hourly (`cron: 17 * * * *`, best-effort) + manually via
  **Actions → Upstream Sync → Run workflow**. A run with **no upstream
  changes is a cheap no-op** (~30s: checkout + fetch + compare) — it does
  NOT back up, merge, build, or open issues. Real work only happens when
  rukamori has pushed new commits.
- **Clean merge + green build** → pushed straight to `dev`.
- **Conflicts** → the run stops and reports which files need attention.
  Nothing is pushed and `dev` is untouched; resolve locally (see below).
- **Backup:** a single rolling `backup/dev-rolling` branch is force-updated to
  point at `dev` before each merge attempt.
- **Any failure** → nothing is pushed to `dev`; an issue is opened with logs.

## Conflict resolution is manual

This workflow used to resolve conflicts with a model via
`scripts/ai_resolve.py`. That has been **removed**, because both providers it
depended on stopped working:

- NVIDIA NIM returned `401 Unauthorized` — the `AI_API_KEY` secret was never set.
- The GitHub Models fallback returned `410 Gone` — that endpoint is retired.

With every provider failing, the resolver failed on every conflicted run, so the
sync had **never once completed successfully**. It also pushed a timestamped
backup branch *before* reaching the failure, which is where the accumulation of
`backup/dev-auto-*` branches came from (30 of them, since deleted).

Stopping on conflicts produces the same practical outcome — nothing merges
unattended — without the wasted CI time, the dead API calls, the branch litter,
or a model rewriting fork-critical code unreviewed.

To resolve a reported conflict:

```bash
git fetch upstream dev
git checkout dev
git merge upstream/dev
# resolve the listed files, keeping the invariants below in mind
git commit && git push origin dev
```

## Invariants the sync enforces

These are checked on every merge; a violation fails the run before anything is
pushed.

1. Fork features always survive: Tidal (`tidal/` package), the multi-source
   audio framework (`audiosource/`, `resolveMultiSourceDataSpec` in
   `MusicService.kt`), Telegram (`telegram/`, `TelegramDataSource`), and the
   fork's `persistent-debug.keystore` CI signing patch.
2. Never-touch files are always kept at the fork version, even if upstream
   changes them (the change is dropped and flagged in the report):
   `Koiverse.jks`, `Koiverse.jks.base64`, `ArchiveTuneKoiverseServer.txt`,
   `DataServer.txt`, plus the `applicationId` in `app/build.gradle.kts`.
3. Submodule pointers are resolved deterministically, never by hand-merging:
   `lyrics`, `moriextractor`, `IconPack` and `morideobfuscator` adopt
   upstream's pointers, while `core` is pinned to this fork's freshly merged
   `vossgraves/core` commit.
4. A merge is only delivered if (a) the fork contract tests pass
   (`:app:testGmsMobileUniversalDebugUnitTest` — palette, crossfade, artwork,
   multi-source, presence) and (b) `assembleGmsMobileUniversalDebug` builds.

## One-time setup

1. **PAT secret (`SYNC_PAT`).** Create a fine-grained personal access token
   (GitHub → Settings → Developer settings → Fine-grained tokens) with access
   to `vossgraves/ArchiveTune` **and** `vossgraves/core`, permissions:
   *Contents: write, Issues: write, Actions: write.*
   Add it as repo secret `SYNC_PAT`. (The built-in `GITHUB_TOKEN` cannot push
   to the `core` repo and cannot push workflow-file changes — hence the PAT.)
2. **Issues enabled.** Forks often have Issues disabled — the workflow uses
   them for sync reports and failure alerts: repo → Settings → General →
   Features → ☑ **Issues**. Also enable Actions if the Actions tab shows the
   "scheduled workflows are disabled for forks" banner.
3. This workflow file must exist on the **default branch** (`main`) for the
   schedule to fire, and the `scripts/` files must exist on `dev` (the
   workflow checks out `dev` and runs them from there).

No AI key is required. `AI_API_KEY`, `AI_BASE_URL` and `AI_MODEL` are no longer
read by anything and can be deleted from the repo's secrets and variables.

## Rollback

Each run force-updates `backup/dev-rolling` to `dev`'s pre-merge state. To undo
the most recent sync:

```bash
git fetch origin backup/dev-rolling
git checkout dev
git reset --hard origin/backup/dev-rolling
git push --force-with-lease origin dev
```

Only the latest sync can be undone this way. Earlier states are still reachable
through `dev`'s history and reflog.

## Interaction with the 4nx3b mirror

`.github/workflows/mirror-4nx3b.yml` force-pushes 4nx3b's release tree over
`dev` and `main`, while this workflow merges rukamori into `dev`. **They cannot
both own `dev`** — left both on a schedule, each partly undoes the other and
`dev` ends up in whichever state finished last.

Mirroring 4nx3b already includes rukamori's work transitively, since 4nx3b syncs
from rukamori themselves, so this workflow is redundant once the mirror is
trusted. The mirror's schedule is currently disabled pending verification; see
the header of that file.

## Troubleshooting

- **"SYNC_PAT secret is not set"** → do setup step 1.
- **"merge needs manual resolution in N file(s)"** → expected on a real
  conflict. `dev` is untouched; resolve locally using the commands above.
- **"could not push to dev"** → `dev` advanced during the run, so the
  fast-forward push was rejected. Nothing was lost; the next hourly run
  retries from the new state.
- **Sync issue says build failed** → read the linked run log; upstream likely
  needs a fix or the merge broke something. `dev` was not modified; the next
  hourly run will retry from the same state.
- **"vossgraves/core conflicts with rukamori/core"** → resolve in the
  `vossgraves/core` repo directly, then re-run this workflow.

## Running it manually

**Actions → Upstream Sync → Run workflow** (branch `main`). Watch the log;
the report issue appears within a minute of the run finishing.
