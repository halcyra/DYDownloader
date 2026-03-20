---
name: f-droid-new-app
description: Prepare and submit a new Android app to F-Droid. Use when Codex needs to make an upstream repo F-Droid-ready, write `fdroiddata` metadata, open a new-app GitLab merge request, or recover from rebase and pipeline blockers.
---

# F-Droid New App

## Overview

Take an Android app from "not in F-Droid yet" to a clean `fdroiddata` merge request. Keep upstream buildable with F-Droid-compatible inputs, keep metadata minimal, and verify the current GitLab MR template before editing the MR body.

## Quick Flow

1. Confirm the app is not already in `fdroiddata`, then collect:
   upstream repo, application ID, license, release tag strategy, build system, external repos, and whether the requester is the upstream maintainer.
2. Make upstream F-Droid-ready.
3. Add `metadata/<applicationId>.yml` in `fdroiddata`.
4. Validate the release build and metadata.
5. Push from a public GitLab fork and open the MR.
6. Rebase and re-check the latest-commit pipeline when GitLab blocks merging.

If the app already exists in `fdroiddata`, stop and switch to an update workflow.

## Upstream Rules

- remove hostile repos such as JitPack before submission
- add `distributionSha256Sum` to the Gradle wrapper if missing
- prefer a clean tag that directly builds from source
- add upstream fastlane or Triple-T metadata when possible
- if older tags are unsuitable, cut a new F-Droid-ready tag
- verify locally before submission; use WSL only when the native environment is the blocker

## Metadata Rules

- add `metadata/<applicationId>.yml` on a submission branch
- keep fields minimal: `License`, `Categories`, `SourceCode`, `IssueTracker`, `RepoType`, `Repo`
- use the full upstream commit hash in `Builds[].commit`, not a tag name
- add `WebSite` and `Changelog` only if they help reviewers
- use `AntiFeatures` honestly, especially `NonFreeNet` for proprietary services
- keep `Summary` and `Description` out of `fdroiddata` if upstream fastlane or Triple-T metadata already exists
- prefer `UpdateCheckMode: Tags` and `AutoUpdateMode: Version` when upstream tags releases
- omit `MaintainerNotes` unless reviewers genuinely need context that cannot be inferred from the metadata
- if the maintainer already has an accepted app, compare style with that metadata first

## Validation Rules

- build the release artifact locally
- run enough tests to trust the release tag
- run `fdroid lint` when practical
- if full-repo lint is noisy, isolate validation of the new metadata instead of ignoring errors
- do not mark the MR pipeline checkbox as passed until the latest MR commit has a passing pipeline

## MR Rules

- push from a public GitLab fork, not upstream
- keep the source branch unprotected
- title the MR exactly as `New app: <applicationId>`
- fetch the current official MR template before editing; template names can change
- delete template boilerplate and keep the checklist sections
- keep every checklist item truthful
- remove `Closes rfp#...` and `Closes fdroiddata#...` if no related issues exist
- keep `/label ~"New App"` in the final body

Use [mr-body-template.md](references/mr-body-template.md) as a starting skeleton only after checking the current official template still matches.

## Rebase And Pipeline Rules
- if GitLab says fast-forward merge is not possible, fetch upstream `fdroiddata` and rebase the submission branch onto the latest `master`
- after rebasing, force-push with lease to the fork branch
- after rebasing, old pipelines are stale because the MR now points at a new commit SHA
- if the latest pipeline is paused, resumed, retried, or replaced, evaluate only the pipeline attached to the latest MR commit

## Common Pitfalls

Read [pitfalls.md](references/pitfalls.md) when any of these show up:

- dependency repositories like JitPack
- outdated assumptions about the F-Droid MR template name or body format
- GitLab push hanging because of interactive credential manager prompts
- trying to update an existing MR description with Git push options
- checking pipeline state on an old commit after a rebase

## Deliverables

A complete run should leave behind:

- an upstream tag that builds locally
- upstream fastlane or Triple-T metadata when possible
- `fdroiddata` metadata for `metadata/<applicationId>.yml`
- a GitLab MR titled `New app: <applicationId>`
- a latest-commit pipeline status that is either passing or explicitly waiting on F-Droid maintainers
