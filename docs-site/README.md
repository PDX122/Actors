# Documentation

The site is a static build from the Markdown guides in `docs/`, the navigation in
`navigation.json`, the shared templates in `build.py` and the CSS/JavaScript in
`theme/`. No framework or runtime server is required after building.

```powershell
py -3.13 -m pip install -r docs-site/requirements.txt
py -3.13 docs-site/build.py
py -3.13 -m http.server 8770 --bind 127.0.0.1 --directory docs-site/site
```

Open `http://127.0.0.1:8770`. The build emits the same relative links when deployed
at `/Actors/` on GitHub Pages. Existing Home, Install, Record takes, Camera modes
and Changelog paths are retained.

## Update a guide

Edit the Markdown page in `docs/` and add or reorder its entry in `navigation.json`.
The builder generates the table of contents, page navigation, metadata and full
text search index. Screenshots use `assets/screenshots/<feature>.png`; the builder
copies these from the committed test-log snapshot.

## Update the evidence

```powershell
py -3.13 tools/export_qa_site.py
py -3.13 docs-site/build.py
```

The evidence exporter requires the local saved QA data. The docs build itself
uses the committed snapshot in `test-log/`; GitHub Actions needs no Minecraft
installation or proprietary JARs. The builder adds the shared docs navigation
and search to the log without changing its saved pass/fail history.

The Documentation workflow builds and deploys on a later push to `main`. These
commands do not push or publish anything. The old Zensical configuration has been
replaced by `build.py` and `navigation.json`.

Fonts and Lucide SVG icons are vendored with licenses. No font or icon CDN is
needed. The site supports light/dark themes, mobile navigation and full-text
search. Source code contains no explanatory comments; visible docs remain Markdown.

## Migrated source paths

The public guides previously in `../docs/` now live in `docs/`. Their old files
remain as short links to the canonical guides so existing repository links keep
working. Skin studio and Actors Studio are separate guides; custom shaders,
settings and troubleshooting are also split into their own pages. Internal
engineering and validation notes remain in the repository docs folder.

## Version downloads

`downloads/catalog.json` declares release status, Minecraft families, Java
requirements, artifact filenames, sizes and SHA-256 digests. The version-specific
JARs and the all-versions ZIP are stored beside it under the mod version, so
GitHub Pages serves the same files that work in the preview.

Prepare a new entry from a validated release folder with:

```powershell
py -3.13 tools/package_docs_downloads.py --package build/dist/release-candidate-2026-11-01-final
py -3.13 tools/export_qa_site.py
py -3.13 docs-site/build.py
```

Packaging checks the existing SHA-256 list, JAR metadata and the bundle's JARs
before copying them. It adds or updates that mod version while retaining older
catalog entries. The docs check validates every packaged JAR and ZIP checksum.
Keep the candidate status until the release is actually published. The packager
does not publish a GitHub Release or deploy the site.

Each release has a site revision such as `0.11.1.1`. Packaging increments it when
the JAR checksums change and retains it when the same JARs are packaged again.
The revision appears on the downloads page and in cache keys. Filenames and
`fabric.mod.json` keep the original mod version.

Upload dates are stored in UTC and displayed in each visitor's local timezone.
The Pages build preserves the timestamp for matching JAR checksums and stamps
new uploads. Rebuilding the docs does not change an existing upload date.
