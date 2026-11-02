# Test log publishing

The test log has two exports. Neither is published automatically by the exporter.

```powershell
py -3.13 tools/export_qa_site.py
```

- `docs-site/test-log/` is the interactive GitHub Pages site. Commit this folder
  with the source changes. The existing Documentation workflow includes it in
  the Pages artifact under `/Actors/test-log/`.
- `wiki-export/` contains the GitHub Wiki version: 41 Markdown pages, a sidebar,
  a footer and 164 referenced screenshots. The individual test pages include
  every result in the latest JUnit files. Feature guides are indexed by
  `Guides.md`. Prior failures remain in the history.

## GitHub Wiki

GitHub Wiki uses a separate Git repository and does not run JavaScript. Enable
the repository's Wiki and create its first page before cloning that repository:

```powershell
git clone https://github.com/PDX122/Actors.wiki.git ..\Actors.wiki
```

When publishing is requested, copy the contents of `wiki-export/` into that
checkout, review the diff, then commit and push there. Check an existing Home,
sidebar and footer before replacing them. The export supplies `Home.md`,
`_Sidebar.md` and `_Footer.md` for a new wiki; merge their links if the wiki
already contains other content. Keep the `images/` folder beside the Markdown
pages so relative image links continue to work.

For a different staging directory:

```powershell
py -3.13 tools/export_qa_site.py --wiki-output build\wiki-staging
```

The exporter reads local saved evidence; the uploaded snapshots are independent
of local game files. Machine-specific user paths are replaced with placeholders
and em dashes are normalized in the generated text. The Pages site vendors its
SVG icons and fonts with the upstream licenses. The wiki uses ordinary Markdown
links and embedded images.
