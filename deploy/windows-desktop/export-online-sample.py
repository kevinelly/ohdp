"""Read only export over SSH. Credentials stay on the server; no production writes."""
import argparse
import base64
import io
import json
import subprocess
import zipfile
from pathlib import Path

REMOTE = r'''
import subprocess, json, datetime, io, zipfile, base64, hashlib
mysql=['docker','exec','-i','bhfyzx-db-1','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" --default-character-set=utf8mb4 -N -B --raw']
def query(sql):
    return subprocess.run(mysql,input=sql,text=True,capture_output=True,check=True).stdout
meta=query("SELECT table_name,column_name,data_type FROM information_schema.columns WHERE table_schema=DATABASE() AND (table_name LIKE 'ohdp\\_%' OR table_name LIKE 'qc\\_%') ORDER BY table_name,ordinal_position;")
tables={}
for line in meta.splitlines():
    table,col,kind=line.split('\t'); tables.setdefault(table,{'name':table,'columns':{},'rows':[]})['columns'][col]=kind
sql=['SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;', 'START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY;']
for table,spec in tables.items():
    fields=[]
    for col,kind in spec['columns'].items():
        value=f'CAST(`{col}` AS UNSIGNED)' if kind=='bit' else f'`{col}`'
        fields += ["'"+col+"'",value]
    sql += [f"SELECT '__TABLE__{table}';",f"SELECT JSON_OBJECT({','.join(fields)}) FROM `{table}` ORDER BY id;"]
sql += ['COMMIT;']
current=None
for line in query('\n'.join(sql)).splitlines():
    if line.startswith('__TABLE__'): current=tables[line[9:]]
    elif line: current['rows'].append(json.loads(line))
snapshot={'format':1,'snapshotId':'online-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'),
 'exportedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'source':'101.33.226.22 / BHFYZX',
 'tables':list(tables.values()),'files':[],'missingFiles':[]}
paths={}
for table in snapshot['tables']:
    for row in table['rows']:
        for col in ['file_path','storage_path']:
            path=row.get(col)
            if path:
                if not path.startswith(('/data/reports/','/data/reference-documents/','/data/radiology/')):
                    raise RuntimeError('Unexpected storage prefix: '+path)
                paths.setdefault(path,[]).append((table,row,col))
stream=io.BytesIO()
with zipfile.ZipFile(stream,'w',zipfile.ZIP_DEFLATED) as archive:
    for path,refs in paths.items():
        result=subprocess.run(['docker','exec','bhfyzx-api-1','cat',path],capture_output=True)
        if result.returncode:
            # Missing historical files cannot be presented as usable attachments.
            snapshot['missingFiles'].append({'path':path,'references':len(refs)})
            for table,row,col in refs: row['deleted']=1
            continue
        relative=path[len('/data/'):]
        archive.writestr('files/'+relative,result.stdout)
        snapshot['files'].append({'path':relative,'sha256':hashlib.sha256(result.stdout).hexdigest(),'bytes':len(result.stdout)})
    archive.writestr('dataset.json',json.dumps(snapshot,ensure_ascii=False,separators=(',',':')))
print(base64.b64encode(stream.getvalue()).decode('ascii'))
'''

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--key', required=True)
    parser.add_argument('--host', default='101.33.226.22')
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    result = subprocess.run(['ssh', '-i', args.key, '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=20',
                             'root@' + args.host, 'python3', '-'], input=REMOTE.encode('utf-8'), capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))
    payload = base64.b64decode(result.stdout.strip(), validate=True)
    with zipfile.ZipFile(io.BytesIO(payload)) as archive:
        dataset = json.loads(archive.read('dataset.json'))
        if any(not t['name'].startswith(('ohdp_', 'qc_')) for t in dataset['tables']):
            raise RuntimeError('Unexpected table in business-only snapshot')
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(payload)
    summary = {'snapshotId': dataset['snapshotId'], 'exportedAt': dataset['exportedAt'],
               'tables': {t['name']: len(t['rows']) for t in dataset['tables']},
               'activeProjects': sum(not r['deleted'] for t in dataset['tables'] if t['name']=='ohdp_project' for r in t['rows']),
               'activeCases': sum(not r['deleted'] for t in dataset['tables'] if t['name']=='qc_inspection_case' for r in t['rows']),
               'files': len(dataset['files']), 'missingFiles': len(dataset['missingFiles']), 'bytes': len(payload)}
    output.with_suffix('.summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(summary, ensure_ascii=False, indent=2))

if __name__ == '__main__':
    main()
