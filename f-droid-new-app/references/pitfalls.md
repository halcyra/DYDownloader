# Common F-Droid New App Pitfalls

## Upstream Repo

- JitPack is a common blocker. Replace it before submission.
- Old tags may build but still be unsuitable for F-Droid. Cut a new tag when needed.
- If upstream already includes fastlane or Triple-T metadata, do not duplicate summary and description in `fdroiddata`.

## Metadata

- `Builds[].commit` should use the actual commit hash. Reviewers may ask to replace tag names with hashes.
- Keep `AntiFeatures` honest. `NonFreeNet` is often required for apps that depend on proprietary network services.
- `MaintainerNotes` is optional. Skip it unless it adds reviewer-critical context.
- Compare against another accepted app from the same maintainer when one exists.

## GitLab And MR Handling

- The current official MR template may not match older guides. Query the repository tree instead of assuming an old filename.
- Git push options help create an MR, but are not reliable for editing an existing MR description.
- Git Credential Manager can hang interactive pushes. A non-interactive push is often more reliable in automation-heavy environments.
- Keep the fork public and the submission branch unprotected.
- Use the exact MR title format `New app: <applicationId>`.

## Rebase And Pipeline State

- If GitLab reports "Fast forward merge is not possible", rebase onto the latest upstream `fdroiddata/master` and force-push with lease.
- After a rebase, any earlier pipeline result is stale because the MR head SHA changed.
- If the latest pipeline is paused, the MR is still effectively waiting; resume or rerun the pipeline on the latest commit.
