from pathlib import Path
import argparse
import hashlib
import json
import shutil
import zipfile

ROOT=Path(__file__).resolve().parent.parent
FAMILIES=[
    ('1.21.1',['1.21','1.21.1'],21),
    ('1.21.4',['1.21.4'],21),
    ('1.21.5',['1.21.5'],21),
    ('1.21.8',['1.21.6','1.21.7','1.21.8'],21),
    ('1.21.10',['1.21.9','1.21.10'],21),
    ('1.21.11',['1.21.11'],21),
    ('26.1.2',['26.1','26.1.1','26.1.2'],25),
    ('26.2',['26.2'],25),
    ('26.3',['26.3'],25),
]

def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream,'sha256').hexdigest()

def package(source,output):
    hashes={name:sha for sha,name in (line.split('  ',1) for line in (source/'SHA256SUMS.txt').read_text().splitlines() if line.strip())}
    builds=[]
    version=None
    for family,compatible,java in FAMILIES:
        matches=list(source.glob(f'flashback-actors-{family}-*.jar'))
        if len(matches)!=1:raise ValueError(f'Expected one release JAR for {family}')
        path=matches[0];sha=digest(path)
        if hashes.get(path.name)!=sha:raise ValueError('Checksum mismatch: '+path.name)
        with zipfile.ZipFile(path) as jar:
            metadata=json.loads(jar.read('fabric.mod.json'))
            if metadata['id']!='fbactors':raise ValueError('Unexpected mod: '+path.name)
            if version is None:version=metadata['version']
            if version!=metadata['version']:raise ValueError('Mixed mod versions')
            if any(name.startswith('com/moulberry/flashback/') for name in jar.namelist()):
                raise ValueError('Proprietary Flashback classes found in '+path.name)
        builds.append({'family':family,'minecraft':compatible,'java':java,'filename':path.name,'sha256':sha,'bytes':path.stat().st_size})
    archive=Path(str(source)+'.zip')
    if not archive.is_file():raise FileNotFoundError(archive)
    with zipfile.ZipFile(archive) as zipped:
        for build in builds:
            if hashlib.sha256(zipped.read(build['filename'])).hexdigest()!=build['sha256']:
                raise ValueError('Bundle differs from version JAR: '+build['filename'])
    destination=output/version;destination.mkdir(parents=True,exist_ok=True)
    for build in builds:
        shutil.copy2(source/build['filename'],destination/build['filename'])
    bundle_name=f'flashback-actors-{version}-all-versions.zip'
    shutil.copy2(archive,destination/bundle_name)
    shutil.copy2(source/'SHA256SUMS.txt',destination/'SHA256SUMS.txt')
    record={'version':version,'status':'candidate','label':'Release candidate','builds':builds,
            'archive':{'filename':bundle_name,'bytes':archive.stat().st_size,'sha256':digest(archive)},'checksums':'SHA256SUMS.txt'}
    catalog_path=output/'catalog.json'
    catalog=json.loads(catalog_path.read_text()) if catalog_path.exists() else {'releases':[]}
    previous=next((r for r in catalog['releases'] if r['version']==version),None)
    revision=max(1,int(previous.get('revision',1))) if previous else 1
    if previous:
        old={b['filename']:b['sha256'] for b in previous['builds']}
        current={b['filename']:b['sha256'] for b in builds}
        if old!=current:revision+=1
    record['uploaded_at']=previous.get('uploaded_at') if previous and revision==previous.get('revision',1) else None
    record['revision']=revision
    record['display_version']=f'{version}.{revision}'
    catalog['releases']=[record]+[r for r in catalog['releases'] if r['version']!=version]
    catalog_path.write_text(json.dumps(catalog,indent=2)+'\n',encoding='utf-8')
    print(f'Packaged {version}: {len(builds)} verified JARs, all-version ZIP and checksums in {output}')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description='Package verified Actors builds for documentation downloads')
    parser.add_argument('--package',type=Path,default=ROOT/'build/dist/release-candidate-2026-11-01-final')
    parser.add_argument('--output',type=Path,default=ROOT/'docs-site/downloads')
    args=parser.parse_args();package(args.package.resolve(),args.output.resolve())
