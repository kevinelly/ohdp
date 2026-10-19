"""Publish a prebuilt test1013 release; reuse production credentials and volumes.

Usage on the server: python3 deploy-test1013.py release-YYYYMMDD-test1013-HHMMSS
The release directory must already contain app.jar, dist/ and manifest.json.
"""
import hashlib, json, os, pathlib, re, shutil, subprocess, sys, urllib.request

ROOT=pathlib.Path('/opt/bhfyzx')
if len(sys.argv)!=2 or not re.fullmatch(r'release-\d{8}-test1013-\d{6}',sys.argv[1]):
    raise SystemExit('Expected release-YYYYMMDD-test1013-HHMMSS')
NAME=sys.argv[1]
RELEASE=ROOT/'releases'/NAME
BACKUP=ROOT/'backups'/('before-'+NAME)
EXPECTED_PREVIOUS=ROOT/'releases/release-20261019-test1013-204840'
TAG=NAME.removeprefix('release-')

def run(args,**kwargs): return subprocess.run(args,check=True,**kwargs)
def compose(folder,*args): return run(['docker','compose','--env-file',str(folder/'.env'),'-f',str(folder/'compose.yaml'),*args])
def sql(query):
    return subprocess.check_output(['docker','exec','-i','bhfyzx-db-1','sh','-c',
        'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -N -B --raw'],input=query.encode()).decode().strip()
def state():
    tables=sql("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND (table_name LIKE 'ohdp_%' OR table_name LIKE 'qc_%' OR table_name LIKE 'radiology_%' OR table_name LIKE 'personal_dose_%') ORDER BY table_name").splitlines()
    counts={t:int(sql('SELECT COUNT(*) FROM `'+t+'`')) for t in tables}
    admin=sql("SELECT id,username,password_hash,must_change_password,deleted FROM sys_user WHERE username='admin'")
    return {'rows':counts,'adminDigest':hashlib.sha256(admin.encode()).hexdigest()}

env=dict(line.strip().split('=',1) for line in (ROOT/'shared/.env').read_text().splitlines() if line.strip() and not line.startswith('#') and '=' in line)
base='http://127.0.0.1:'+env.get('WEB_PORT','8088')
def request(path,data=None,token=None):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    req=urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=headers)
    with urllib.request.urlopen(req,timeout=45) as response:
        body=response.read();return json.loads(body) if body else None
def verify_api():
    login=request('/api/auth/login',{'username':env.get('ADMIN_USERNAME','admin'),'password':env['ADMIN_INITIAL_PASSWORD']})
    token=login['token']
    try:
        projects=request('/api/ohdp/projects?page=0&size=2',token=token)
        cases=request('/api/radiology/cases?page=0&size=2',token=token)
        ct=request('/api/radiology/catalog/CT',token=token);dr=request('/api/radiology/catalog/DR',token=token)
        assert len(ct)==12 and len(dr)==19
        if cases['content']:
            detail=request('/api/radiology/cases/'+str(cases['content'][0]['id']),token=token)
            assert 'dataVersion' in detail and 'submissionIssues' in detail and 'itemConditions' in detail
        dose=request('/api/personal-dose/cases?page=0&size=1',token=token)
        assert 'totalElements' in dose
        return {'personalDose':'passed','login':'passed','projects':projects['totalElements'],'cases':cases['totalElements'],'catalog':'CT=12, DR=19','newCaseFields':'passed'}
    finally: request('/api/auth/logout',{},token)

previous=(ROOT/'current').resolve()
assert previous==EXPECTED_PREVIOUS,'Online release changed; recheck before publishing'
assert RELEASE.is_dir() and not BACKUP.exists()
os.umask(0o077)
# Authenticate against the unchanged online account before touching services.
login=request('/api/auth/login',{'username':env.get('ADMIN_USERNAME','admin'),'password':env['ADMIN_INITIAL_PASSWORD']})
request('/api/auth/logout',{},login['token']);del login
for name in ('compose.yaml','Dockerfile.api','Dockerfile.web','nginx.conf'):
    if not (RELEASE/name).exists(): (RELEASE/name).write_bytes((previous/name).read_bytes())
(RELEASE/'.dockerignore').write_text('.env\n*.log\n')
(RELEASE/'.env').write_bytes((ROOT/'shared/.env').read_bytes())
compose_text=(RELEASE/'compose.yaml').read_text()
compose_text=re.sub(r'image: bhfyzx-api:[^\s]+','image: bhfyzx-api:'+TAG,compose_text)
compose_text=re.sub(r'image: bhfyzx-web:[^\s]+','image: bhfyzx-web:'+TAG,compose_text)
(RELEASE/'compose.yaml').write_text(compose_text)
manifest=json.loads((RELEASE/'manifest.json').read_text())
for path,digest in manifest['files'].items():
    assert hashlib.sha256((RELEASE/path).read_bytes()).hexdigest()==digest,path
# Open tabs may still request the preceding build's hashed lazy-loaded modules.
# Keep two published builds, selecting only original manifest assets to bound retention.
retained={}
for old in (previous,(ROOT/'previous').resolve()):
    if old.parent!=ROOT/'releases' or not (old/'manifest.json').is_file(): continue
    old_manifest=json.loads((old/'manifest.json').read_text())
    for relative,digest in old_manifest['files'].items():
        if not relative.startswith('dist/assets/') or pathlib.PurePosixPath(relative).name!=relative.removeprefix('dist/assets/'): continue
        source=old/relative;target=RELEASE/relative
        if target.exists() or not source.is_file(): continue
        assert hashlib.sha256(source.read_bytes()).hexdigest()==digest,relative
        shutil.copy2(source,target);retained[relative]=digest
(RELEASE/'retained-assets.json').write_text(json.dumps(retained,indent=2))
compose(RELEASE,'config','--quiet');compose(RELEASE,'build','api','web')
BACKUP.mkdir();(BACKUP/'previous-release.txt').write_text(str(previous)+'\n')
try:
    compose(previous,'stop','api')
    before=state();(BACKUP/'before-state.json').write_text(json.dumps(before))
    with (BACKUP/'database.sql').open('wb') as output:
        run(['docker','exec','bhfyzx-db-1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --triggers --no-tablespaces "$MYSQL_DATABASE"'],stdout=output)
    assert (BACKUP/'database.sql').stat().st_size>0
    run(['gzip',str(BACKUP/'database.sql')])
    for volume in ('bhfyzx_report_data','bhfyzx_reference_document_data','bhfyzx_radiology_data'):
        mount=subprocess.check_output(['docker','volume','inspect',volume,'--format','{{.Mountpoint}}'],text=True).strip()
        run(['tar','-czf',str(BACKUP/(volume+'.tar.gz')),'-C',mount,'.'])
    print('BACKUP_COMPLETE '+str(BACKUP),flush=True)
    compose(RELEASE,'up','-d','--no-deps','--wait','--wait-timeout','180','api','web')
    after=state()
    assert after['adminDigest']==before['adminDigest'],'Administrator credentials changed'
    assert all(after['rows'].get(t)==n for t,n in before['rows'].items()),'Existing business row counts changed'
    assert all(n==0 for t,n in after['rows'].items() if t not in before['rows']),'New tables unexpectedly contain data'
    checks=verify_api()
    for url,file in [('/', 'dist/index.html'),('/test1013-changes.html','dist/test1013-changes.html'),('/manuals/detailed-guide.docx','dist/manuals/detailed-guide.docx'),('/manuals/quick-guide.docx','dist/manuals/quick-guide.docx')]:
        with urllib.request.urlopen(base+url,timeout=30) as response: assert response.read()==(RELEASE/file).read_bytes(),file
    with urllib.request.urlopen(base+'/healthz',timeout=15) as response: assert response.status==200
    with urllib.request.urlopen(base+'/',timeout=15) as response: assert 'no-cache' in response.headers.get('Cache-Control','')
    for relative,digest in retained.items():
        with urllib.request.urlopen(base+'/'+relative.removeprefix('dist/'),timeout=15) as response: assert hashlib.sha256(response.read()).hexdigest()==digest,relative
    checks.update({'businessRows':'unchanged','adminPassword':'unchanged','frontend':'exact-build-match','entryCache':'revalidate','retainedAssets':len(retained),'manualDownloads':'unchanged','backup':str(BACKUP),'release':str(RELEASE),'gitCommit':manifest['gitCommit']})
    (RELEASE/'verification.json').write_text(json.dumps(checks,indent=2))
    for link,target in [('previous',previous),('current',RELEASE)]:
        temporary=ROOT/(link+'.deploy-new')
        if temporary.is_symlink(): temporary.unlink()
        temporary.symlink_to(target);temporary.replace(ROOT/link)
    print('DEPLOYMENT_PASSED '+json.dumps(checks),flush=True)
except BaseException:
    print('Validation failed; restoring previous application containers. Database backup remains available.',flush=True)
    compose(previous,'up','-d','--no-deps','--wait','--wait-timeout','180','api','web')
    raise
