# F-Droid New App MR Body

Use this only after checking the current official template in `fdroiddata`. Template names and sections can change.

```md
## Required

* [x] The app complies with the [inclusion criteria](https://f-droid.org/docs/Inclusion_Policy)
* [x] The original app author has been notified (and does not oppose the inclusion)
* [x] All related [fdroiddata](https://gitlab.com/fdroid/fdroiddata/issues) and [RFP issues](https://gitlab.com/fdroid/rfp/issues) have been referenced in this merge request
* [ ] Builds with `fdroid build` and all pipelines pass

## Strongly Recommended

* [x] The upstream app source code repo contains the app metadata _(summary/description/images/changelog/etc)_ in a [Fastlane](https://gitlab.com/snippets/1895688) or [Triple-T](https://gitlab.com/snippets/1901490) folder structure
* [x] Releases are tagged

## Suggested

* [ ] External repos are added as git submodules instead of srclibs
* [ ] Enable [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds)
* [ ] Multiple apks for native code

Notes:
- This submission is from the upstream maintainer.
- No related `rfp` or `fdroiddata` issues were found for `<applicationId>`.
- `<tag>` is the first F-Droid-ready tag.

/label ~"New App"
```

Guidance:

- delete the instructional text from the official template
- leave the pipeline checkbox unchecked until the latest MR commit passes
- remove `Notes:` if it adds no value
- if the submitter is not the upstream maintainer, use a truthful unchecked item plus the upstream reply link
- if there are no related issues, do not leave placeholder `Closes ...` lines behind
