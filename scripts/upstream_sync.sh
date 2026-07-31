#!/usr/bin/env bash
# upstream_sync.sh — keep this fork's dev in sync with rukamori/ArchiveTune dev.
#
# What it does, in order:
#   1. Preflight checks (clean tree, secrets present)
#   2. Force-update one rolling backup ref for dev (undo button)
#   3. Sync the vossgraves/core submodule fork with rukamori/core
#   4. Merge upstream/dev into dev; stop on conflicts for a human to resolve
#   5. Verify: no markers, protected files untouched, fork features intact
#   6. Build gate: assembleGmsMobileUniversalDebug must succeed
#   7. Deliver: push dev
#
# Required env: SYNC_PAT, GH_TOKEN
#
# CONFLICT RESOLUTION IS MANUAL
# -----------------------------
# This used to call scripts/ai_resolve.py to resolve conflicts. Both providers it supported are
# gone -- NVIDIA NIM returns 401 without AI_API_KEY, and the GitHub Models endpoint now returns
# 410 Gone -- so every conflicted run failed at the resolver and the sync had never once
# succeeded. It also pushed a fresh backup branch BEFORE that failure, which is where 30-odd
# backup/dev-auto-* branches came from.
#
# Conflicts now stop the run and report which files need attention. That is the same practical
# outcome as before (nothing merges automatically) minus the wasted CI time, the dead API calls,
# and the branch litter -- and without a model rewriting fork-critical code unreviewed.
set -uo pipefail

log()  { echo "[sync] $*"; }
warn() { echo "::warning::$*"; }
die()  {
  echo "::error::$*"
  echo "result=failed" >> "$GITHUB_OUTPUT"
  exit 1
}

REPORT=/tmp/sync_report.md
PROTECTED_RESTORED=()

# --- Fork invariants (keep in sync with AGENTS.md) ---------------------------
NEVER_TOUCH=(
  "Koiverse.jks"
  "Koiverse.jks.base64"
  "ArchiveTuneKoiverseServer.txt"
  "DataServer.txt"
)
EXPECTED_APP_ID='applicationId = "moe.rukamori.archivetune"'
# -----------------------------------------------------------------------------

out() { echo "$1=$2" >> "$GITHUB_OUTPUT"; }

# =============================================================================
log "Phase 0: preflight"
# =============================================================================
[ -n "${SYNC_PAT:-}" ]  || die "SYNC_PAT is not set"
[ -n "${GH_TOKEN:-}" ]  || die "GH_TOKEN is not set"
[ -z "$(git status --porcelain)" ] || die "working tree is dirty"
git config user.name  "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"

git remote add upstream https://github.com/rukamori/ArchiveTune.git 2>/dev/null || true
git fetch upstream dev --quiet || die "could not fetch upstream/dev"

BEHIND=$(git rev-list --count HEAD..upstream/dev)
AHEAD=$(git rev-list --count upstream/dev..HEAD)
log "dev is $AHEAD ahead, $BEHIND behind upstream/dev"
out "behind" "$BEHIND"

if [ "$BEHIND" -eq 0 ]; then
  log "Already up to date. Nothing to do."
  out "result" "uptodate"
  exit 0
fi

# =============================================================================
log "Phase 1: backup"
# =============================================================================
# One rolling backup, force-updated in place.
#
# This used to be backup/dev-auto-<timestamp>, created before the merge was attempted -- so every
# run left a branch behind whether it succeeded or not, and with the resolver failing every time
# they only ever accumulated (30 of them, all deleted).
#
# A single ref is enough for the only job a backup has here: undoing the most recent sync. Older
# states are not lost either way, since dev's reflog and history still reach them.
BACKUP_BRANCH="backup/dev-rolling"
git branch -f "$BACKUP_BRANCH" HEAD
git push --quiet --force origin "$BACKUP_BRANCH" || die "could not push backup branch"
log "Backup updated: $BACKUP_BRANCH -> $(git rev-parse --short HEAD)"
out "backup" "$BACKUP_BRANCH"

# =============================================================================
log "Phase 2: sync vossgraves/core with rukamori/core"
# =============================================================================
CORE_DIR=$(mktemp -d)
git clone --quiet "https://x-access-token:${SYNC_PAT}@github.com/vossgraves/core.git" "$CORE_DIR" \
  || die "could not clone vossgraves/core"
pushd "$CORE_DIR" >/dev/null
git config user.name  "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git remote add upstream https://github.com/rukamori/core.git 2>/dev/null || true
git fetch upstream main --quiet
CORE_BEHIND=$(git rev-list --count origin/main..upstream/main)
log "vossgraves/core is $CORE_BEHIND behind rukamori/core"
if [ "$CORE_BEHIND" -gt 0 ]; then
  git merge --no-ff --no-commit upstream/main
  CORE_MERGE_RC=$?
  if [ "$CORE_MERGE_RC" -ne 0 ]; then
    mapfile -t CORE_CONFLICTS < <(git diff --name-only --diff-filter=U)
    git merge --abort 2>/dev/null || true
    die "vossgraves/core conflicts with rukamori/core in ${#CORE_CONFLICTS[@]} file(s) — resolve by hand in vossgraves/core, then re-run. Nothing was pushed. Files: ${CORE_CONFLICTS[*]}"
  else
    log "core merge clean"
  fi
  git commit --quiet -m "chore(sync): merge rukamori/core main ($CORE_BEHIND commits)" \
    || die "core merge commit failed"
  git push --quiet origin HEAD:main || die "could not push vossgraves/core"
  log "vossgraves/core updated"
fi
popd >/dev/null
CORE_NEW_SHA=$(git -C "$CORE_DIR" rev-parse HEAD)
log "core pinned at ${CORE_NEW_SHA:0:9}"

# =============================================================================
log "Phase 3: merge upstream/dev"
# =============================================================================
git merge --no-ff --no-commit upstream/dev
MERGE_RC=$?

if [ "$MERGE_RC" -ne 0 ]; then
  mapfile -t CONFLICTS < <(git diff --name-only --diff-filter=U)
  [ "${#CONFLICTS[@]}" -gt 0 ] || die "merge failed without file conflicts (e.g. untracked-file collision) — manual look needed; dev untouched (backup: $BACKUP_BRANCH)"
  log "Merge conflicts in ${#CONFLICTS[@]} file(s): ${CONFLICTS[*]}"

  # Two conflict classes still resolve deterministically without judgement: protected fork files
  # always keep the fork's version, and submodule pointers are re-pinned by the step below.
  # Anything left is a real code conflict and needs a human.
  MANUAL_FILES=()
  SUBMODULE_PATHS=$(git config --file .gitmodules --get-regexp path 2>/dev/null | awk '{print $2}')
  for f in "${CONFLICTS[@]}"; do
    SKIP=false
    for p in "${NEVER_TOUCH[@]}"; do
      if [ "$f" = "$p" ]; then SKIP=true; break; fi
    done
    if $SKIP; then
      # Fork identity/config always wins; upstream changes here are dropped.
      git checkout --ours -- "$f" && git add "$f"
      PROTECTED_RESTORED+=("$f")
      warn "protected file conflict — kept fork version: $f"
    elif echo "$SUBMODULE_PATHS" | grep -qx "$f"; then
      log "submodule pointer conflict: $f — will be pinned deterministically"
    else
      MANUAL_FILES+=("$f")
    fi
  done

  if [ "${#MANUAL_FILES[@]}" -gt 0 ]; then
    # Abort so the runner is not left mid-merge and dev keeps its current state. Print the
    # commands to reproduce locally, since that is the only way this gets resolved now.
    git merge --abort 2>/dev/null || true
    {
      echo "## Upstream sync stopped: manual merge needed ($(date -u +%Y-%m-%dT%H:%MZ))"
      echo
      echo "${#MANUAL_FILES[@]} file(s) conflict between \`dev\` and rukamori/ArchiveTune@dev:"
      echo
      for f in "${MANUAL_FILES[@]}"; do echo "- \`$f\`"; done
      echo
      echo "Nothing was pushed; \`dev\` is untouched and \`$BACKUP_BRANCH\` points at it."
      echo
      echo "To resolve locally:"
      echo '```bash'
      echo "git fetch upstream dev && git checkout dev && git merge upstream/dev"
      echo "# resolve, then: git commit && git push origin dev"
      echo '```'
    } > "$REPORT"
    die "merge needs manual resolution in ${#MANUAL_FILES[@]} file(s): ${MANUAL_FILES[*]}"
  fi
else
  log "Merge is clean (no conflicts)"
fi

# --- Submodule pointers -------------------------------------------------------
# rukamori-owned submodules: adopt upstream's recorded pointers exactly.
for sm in lyrics moriextractor IconPack morideobfuscator; do
  SHA=$(git ls-tree upstream/dev "$sm" | awk '{print $3}')
  if [ -n "$SHA" ]; then
    (cd "$sm" && git fetch --quiet origin && git checkout --quiet "$SHA") \
      || die "could not pin submodule $sm to upstream pointer $SHA"
    git add "$sm"
    log "submodule $sm -> ${SHA:0:9} (upstream pointer)"
  fi
done
# core: pin to OUR fork's freshly merged commit (never rukamori's pointer).
(cd core && git fetch --quiet origin main && git checkout --quiet "$CORE_NEW_SHA") \
  || die "could not pin core submodule to $CORE_NEW_SHA"
git add core
log "submodule core -> ${CORE_NEW_SHA:0:9} (vossgraves/core)"

# =============================================================================
log "Phase 4: verification"
# =============================================================================
# 4a. applicationId must remain the fork's
grep -q "$EXPECTED_APP_ID" app/build.gradle.kts \
  || die "applicationId changed by merge — refusing to continue"

# 4b. fork features must still be present and wired
grep -q "resolveMultiSourceDataSpec" \
  app/src/main/kotlin/moe/rukamori/archivetune/playback/MusicService.kt \
  || die "multi-source resolver no longer wired in MusicService.kt"
[ -d app/src/main/kotlin/moe/rukamori/archivetune/tidal ] \
  || die "tidal package missing after merge"
[ -d app/src/main/kotlin/moe/rukamori/archivetune/audiosource ] \
  || die "audiosource package missing after merge"
[ -d app/src/main/kotlin/moe/rukamori/archivetune/telegram ] \
  || die "telegram package missing after merge"
grep -q "TelegramDataSource" \
  app/src/main/kotlin/moe/rukamori/archivetune/playback/MusicService.kt \
  || die "telegram data source no longer wired in MusicService.kt"
grep -q "persistent-debug.keystore" .github/workflows/build.yml \
  || die "fork CI signing patch (persistent-debug.keystore) lost in build.yml"

# 4c. commit the merge, then prove protected files are byte-identical to backup
git commit --quiet -m "chore(sync): merge upstream/dev ($BEHIND commits) + core submodule sync" \
  || die "merge commit failed"
for f in "${NEVER_TOUCH[@]}"; do
  if ! git diff --quiet "$BACKUP_BRANCH" HEAD -- "$f" 2>/dev/null; then
    die "protected file changed by merge: $f (restoring is not enough — aborting; backup: $BACKUP_BRANCH)"
  fi
done
log "verification passed"

# =============================================================================
log "Phase 5: build gate (fork contract tests + assembleGmsMobileUniversalDebug)"
# =============================================================================
chmod +x gradlew
# 5a. Fork contract tests FIRST (palette, crossfade, artwork, multi-source,
#     presence) — they fail in minutes and prove no fork feature was broken.
./gradlew --console=plain :app:testGmsMobileUniversalDebugUnitTest \
  || die "fork contract tests failed — the merge broke a fork feature; nothing pushed to dev (backup: $BACKUP_BRANCH)"
log "contract tests OK"
# 5b. Full APK build.
./gradlew --console=plain assembleGmsMobileUniversalDebug --warning-mode summary \
  || die "build failed — nothing pushed to dev (backup: $BACKUP_BRANCH)"
log "build OK"

# =============================================================================
log "Phase 6: deliver"
# =============================================================================
# Only clean merges reach this point -- conflicted ones exit above -- so there is no longer a
# second delivery path pushing a sync/auto-* branch for review.
git push --quiet origin HEAD:dev || die "could not push to dev"
out "result" "clean"
log "clean merge pushed straight to dev"

# =============================================================================
log "Phase 7: report"
# =============================================================================
{
  echo "## Upstream sync report ($(date -u +%Y-%m-%dT%H:%MZ))"
  echo
  echo "- **Result:** clean, pushed to dev"
  echo "- **Pulled in:** $BEHIND commit(s) from [rukamori/ArchiveTune@dev](https://github.com/rukamori/ArchiveTune/tree/dev)"
  echo "- **Backup branch:** \`$BACKUP_BRANCH\` (rolling — force-updated each run)"
  echo "- **Run:** $GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID"
  echo
  echo "### Build gate"
  echo "\`assembleGmsMobileUniversalDebug\` passed on the runner before anything was pushed."
  echo
  echo "### Submodule actions"
  echo "- \`core\` -> vossgraves/core @ \`${CORE_NEW_SHA:0:9}\` (merged with rukamori/core first)"
  echo "- \`lyrics\`, \`moriextractor\`, \`IconPack\`, \`morideobfuscator\` -> upstream pointers"
  if [ "${#PROTECTED_RESTORED[@]}" -gt 0 ]; then
    echo
    echo "### Protected files kept at fork version (upstream change dropped — review if wanted)"
    for f in "${PROTECTED_RESTORED[@]}"; do echo "- \`$f\`"; done
  fi
  echo
  echo "### New upstream commits"
  echo '```'
  git log --oneline "$BACKUP_BRANCH"..upstream/dev | head -50
  echo '```'
  echo
  echo "### Rollback"
  echo '```bash'
  echo "git checkout dev && git reset --hard $BACKUP_BRANCH && git push --force-with-lease"
  echo '```'
} > "$REPORT"

log "done (clean)"
exit 0
