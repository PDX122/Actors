





from pathlib import Path
import argparse
import shutil
from urllib.parse import unquote
from qa_site.history import build_log
from qa_site.wiki import export_wiki

ROOT = Path(__file__).resolve().parent.parent


def main():
    parser = argparse.ArgumentParser(description='Saved test log tools')
    parser.add_argument('--output', type=Path, default=ROOT / 'docs-site/test-log')
    parser.add_argument('--wiki-output', type=Path, default=ROOT / 'wiki-export')
    args = parser.parse_args()
    output = args.output.resolve()
    routes, data = build_log(ROOT, ROOT / 'build/release-batches/production-final', ROOT / 'build/qa-site')
    output.mkdir(parents=True, exist_ok=True)
    for url, source in routes.items():
        if url == '/':
            continue
        destination = output / unquote(url.lstrip('/'))
        destination.resolve().relative_to(output)
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
    (output / '.nojekyll').write_text('')
    wiki = export_wiki(data, routes, args.wiki_output.resolve())
    print(f'Exported {len(data["entries"])} records and {data["stats"]["historicalFailures"]} historical failures to {output}')
    print(f'Wiki: {wiki["pages"]} Markdown pages and {wiki["images"]} images in {args.wiki_output.resolve()}')


if __name__ == '__main__':
    main()
