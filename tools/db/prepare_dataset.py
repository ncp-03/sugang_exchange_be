"""Reproducible synthetic fixtures, restricted to course_change_loadtest."""
from pathlib import Path
import argparse,csv,datetime,getpass,hashlib,json,os

HERE=Path(__file__).resolve().parent/'generated'

SCHEMA='course_change_loadtest'
FIRST=100001
N=10000
GROUPS=200
COURSES=GROUPS*6
ANCHOR='2026-10-26 09:00:00'
DEPARTMENTS=['컴퓨터공학과','소프트웨어융합학과','인공지능학과','교양학부']
NAMES=['자료구조','운영체제','컴퓨터네트워크','데이터베이스','프로그래밍언어론','컴파일러']
COLUMNS={
 'students':['student_id','student_no','nickname','department','year_level','max_credits','created_at'],
 'courses':['course_id','course_code','course_name','department','course_type'],
 'course_offerings':['offering_id','course_id','section_no','professor_name','credits','capacity','classroom'],
 'class_times':['class_time_id','offering_id','weekday','start_time','end_time'],
 'enrollments':['enrollment_id','student_id','offering_id','course_id','enrolled_at','version'],
 'wishlists':['wishlist_id','student_id','offering_id','created_at'],
 'exchange_requests':['request_id','student_id','offered_enrollment_id','status','queued_at','created_at','updated_at'],
 'exchange_preferences':['preference_id','request_id','target_offering_id','preference_rank'],
}
BASELINE={'students':10,'courses':13,'course_offerings':21,'class_times':40,'enrollments':45,'wishlists':0,'exchange_requests':0,'exchange_preferences':0}

def dataset():
    data={name:[] for name in COLUMNS}
    for c in range(COURSES):
        data['courses'].append([FIRST+c,f'LT{c+1:05d}',f'[부하테스트] {NAMES[c%6]} {c+1:04d}',DEPARTMENTS[c%4],['MAJOR_BASIC','MAJOR_REQUIRED','MAJOR_ELECTIVE','GENERAL'][c%4]])
        start=9*60+(c%6)*90
        fmt=lambda minutes:f'{minutes//60:02d}:{minutes%60:02d}:00'
        for section in range(2):
            offering=FIRST+c*2+section
            capacity=25 if c%6==0 and (c//6)%10==0 else 40
            data['course_offerings'].append([offering,FIRST+c,f'{section+1:02d}',f'가상교수{c%30+1:02d}',3,capacity,f'가상강의실{c+1:04d}'])
            for meeting,day in enumerate([1,3] if section==0 else [2,4]):
                data['class_times'].append([FIRST+c*4+section*2+meeting,offering,day,fmt(start),fmt(start+75)])
    for i in range(N):
        student=FIRST+i;group=(i//2)%GROUPS;section=i%2
        data['students'].append([student,f'LT20261026{i+1:05d}',f'부하학생{i+1:05d}',DEPARTMENTS[i%4],i%4+1,21,ANCHOR])
        for j in range(6):
            c=group*6+j
            data['enrollments'].append([FIRST+i*6+j,student,FIRST+c*2+section,FIRST+c,ANCHOR,0])
        for j in range(3):
            c=group*6+j
            data['wishlists'].append([FIRST+i*3+j,student,FIRST+c*2+(1-section),ANCHOR])
        request=FIRST+i
        queued=(datetime.datetime(2026,10,26,9)+datetime.timedelta(seconds=i%600)).isoformat(' ')
        data['exchange_requests'].append([request,student,FIRST+i*6,'WAITING',queued,ANCHOR,ANCHOR])
        data['exchange_preferences'].append([FIRST+i,request,FIRST+group*12+(1-section),1])
    return data

def generate():
    dest=HERE/'dataset';dest.mkdir(parents=True,exist_ok=True)
    tables={}
    for table,rows in dataset().items():
        path=dest/(table+'.csv')
        with path.open('w',encoding='utf-8',newline='') as fp:
            writer=csv.writer(fp,lineterminator='\n');writer.writerow(COLUMNS[table]);writer.writerows(rows)
        tables[table]={'added':len(rows),'baseline':BASELINE[table],'total':len(rows)+BASELINE[table],'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
    manifest={'schema':SCHEMA,'synthetic_id_start':FIRST,'anchor':ANCHOR,'tables':tables,'total_synthetic_rows':sum(t['added'] for t in tables.values()),'groups':GROUPS,'students_per_group':50,'credits_per_synthetic_student':18,'max_credits':21,'paired_students':[FIRST,FIRST+1],'hot_offering_id':FIRST}
    (HERE/'dataset-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    print('Generated',manifest['total_synthetic_rows'],'synthetic rows')
    return manifest

def migration_history(cursor):
    cursor.execute('SELECT version,success FROM flyway_schema_history ORDER BY installed_rank')
    history=cursor.fetchall()
    versions=[row['version'] for row in history]
    if versions not in (['1','2'],['1','2','3']) or not all(row['success']==1 for row in history):
        raise RuntimeError('Expected successful Flyway V1/V2 or V1/V2/V3 before using fixtures.')
    return history


def initial_password_hash(student_no):
    # Match V3 and AuthService: only synthetic fixture students use this initial password.
    return hashlib.sha256(f'{student_no}:{student_no}'.encode('utf-8')).hexdigest()


def student_insert_row(row,with_password):
    return row+[initial_password_hash(row[1])] if with_password else row


def apply(db,manifest):
    with db.cursor() as q:
        q.execute(f'USE `{SCHEMA}`');q.execute('SELECT DATABASE() AS name')
        if q.fetchone()['name']!=SCHEMA:
            raise RuntimeError('Refusing to write outside course_change_loadtest.')
        with_password=migration_history(q)[-1]['version']=='3'
        for table in COLUMNS:
            q.execute(f'SELECT COUNT(*) AS n FROM `{table}`')
            if q.fetchone()['n']!=BASELINE[table]:
                raise RuntimeError('Fixture table is not clean baseline; refusing duplicate or overwrite: '+table)
    db.begin()
    try:
        with db.cursor() as q:
            for table,columns in COLUMNS.items():
                path=HERE/'dataset'/(table+'.csv')
                if hashlib.sha256(path.read_bytes()).hexdigest()!=manifest['tables'][table]['sha256']:
                    raise RuntimeError('CSV hash mismatch: '+table)
                with path.open(encoding='utf-8',newline='') as fp:
                    reader=csv.reader(fp)
                    if next(reader,None)!=columns:
                        raise RuntimeError('CSV columns mismatch: '+table)
                    batch=[];done=0
                    insert_columns=columns+['password_hash'] if table=='students' and with_password else columns
                    sql=f'INSERT INTO `{SCHEMA}`.`{table}` ('+','.join('`'+c+'`' for c in insert_columns)+') VALUES ('+','.join(['%s']*len(insert_columns))+')'
                    for row in reader:
                        if len(row)!=len(columns):
                            raise RuntimeError('CSV row width mismatch: '+table)
                        if table=='students':row=student_insert_row(row,with_password)
                        batch.append(row)
                        if len(batch)>=1000:q.executemany(sql,batch);done+=len(batch);batch=[]
                    if batch:q.executemany(sql,batch);done+=len(batch)
                    if done!=manifest['tables'][table]['added']:
                        raise RuntimeError('CSV row count mismatch: '+table)
                    print('Inserted',table,done,flush=True)
        db.commit()
        print('Fixture transaction committed; production schema not targeted.')
    except BaseException:
        db.rollback();raise

CHECKS={
 'timetable_overlap':'''SELECT COUNT(*) n FROM enrollments a JOIN enrollments b ON a.student_id=b.student_id AND a.enrollment_id<b.enrollment_id JOIN class_times x ON x.offering_id=a.offering_id JOIN class_times y ON y.offering_id=b.offering_id WHERE x.weekday=y.weekday AND x.start_time<y.end_time AND y.start_time<x.end_time''',
 'credit_limit':'''SELECT COUNT(*) n FROM (SELECT e.student_id FROM enrollments e JOIN students s ON s.student_id=e.student_id JOIN course_offerings o ON o.offering_id=e.offering_id GROUP BY e.student_id,s.max_credits HAVING SUM(o.credits)>s.max_credits) v''',
 'capacity':'''SELECT COUNT(*) n FROM (SELECT o.offering_id FROM course_offerings o JOIN enrollments e ON e.offering_id=o.offering_id GROUP BY o.offering_id,o.capacity HAVING COUNT(*)>o.capacity) v''',
 'request_ownership':'''SELECT COUNT(*) n FROM exchange_requests r JOIN enrollments e ON e.enrollment_id=r.offered_enrollment_id WHERE r.student_id<>e.student_id''',
 'target_same_course_different_section':f'''SELECT COUNT(*) n FROM exchange_requests r JOIN enrollments e ON e.enrollment_id=r.offered_enrollment_id JOIN exchange_preferences p ON p.request_id=r.request_id JOIN course_offerings o ON o.offering_id=p.target_offering_id WHERE r.request_id>={FIRST} AND (e.course_id<>o.course_id OR e.offering_id=o.offering_id)''',
 'missing_reciprocal_partner':f'''SELECT COUNT(*) n FROM exchange_requests r JOIN enrollments e ON e.enrollment_id=r.offered_enrollment_id JOIN exchange_preferences p ON p.request_id=r.request_id WHERE r.request_id>={FIRST} AND NOT EXISTS (SELECT 1 FROM enrollments other JOIN exchange_requests rr ON rr.offered_enrollment_id=other.enrollment_id JOIN exchange_preferences pp ON pp.request_id=rr.request_id WHERE other.offering_id=p.target_offering_id AND pp.target_offering_id=e.offering_id AND other.student_id<>e.student_id)''',
 'wishlist_already_enrolled':'''SELECT COUNT(*) n FROM wishlists w JOIN enrollments e ON e.student_id=w.student_id AND e.offering_id=w.offering_id''',
}

def verify(db,manifest):
    report={'schema':SCHEMA,'counts':{},'invariant_violations':{}}
    with db.cursor() as q:
        q.execute(f'USE `{SCHEMA}`')
        report['flyway']=migration_history(q)
        for table,expected in manifest['tables'].items():
            q.execute(f'SELECT COUNT(*) n FROM `{table}`');actual=q.fetchone()['n']
            report['counts'][table]=actual;assert actual==expected['total'],(table,actual)
        for name,sql in CHECKS.items():
            q.execute(sql);actual=q.fetchone()['n'];report['invariant_violations'][name]=actual;assert actual==0,(name,actual)
        if report['flyway'][-1]['version']=='3':
            q.execute(f"SELECT COUNT(*) n FROM students WHERE student_id>={FIRST} AND (password_hash IS NULL OR password_hash<>SHA2(CONCAT(student_no, ':', student_no),256))")
            report['synthetic_password_hash_violations']=q.fetchone()['n']
            if report['synthetic_password_hash_violations']!=0:
                raise RuntimeError('Synthetic students have missing or invalid V3 login hashes.')
        q.execute(f'SELECT COUNT(*) n FROM enrollments WHERE student_id={FIRST}')
        report['example_student_enrollments']=q.fetchone()['n'];assert report['example_student_enrollments']==6
        q.execute(f'SELECT COUNT(*) n FROM enrollments WHERE offering_id={FIRST}')
        report['hot_offering_enrolled']=q.fetchone()['n'];assert report['hot_offering_enrolled']==25
    (HERE/'dataset-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))

def connect_db(args):
    # Generation uses only Python's standard library. Import the DB driver lazily.
    try:
        import pymysql
    except ImportError:
        raise SystemExit('Install tools/db/requirements.txt before apply or verify.')
    if not args.user:
        raise SystemExit('Specify --user or DB_USERNAME.')
    password=os.getenv('DB_PASSWORD')
    if password is None:
        password=getpass.getpass('MySQL password: ')
    return pymysql.connect(host=args.host,port=args.port,user=args.user,
        password=password,database=SCHEMA,charset='utf8mb4',
        autocommit=True,cursorclass=pymysql.cursors.DictCursor,
        connect_timeout=10,read_timeout=60,write_timeout=60)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('action',choices=['generate','apply','verify'])
    p.add_argument('--output-dir',type=Path,default=HERE,
        help='Generated CSV and manifest directory (default: tools/db/generated).')
    p.add_argument('--host',default=os.getenv('DB_HOST','127.0.0.1'))
    p.add_argument('--port',type=int,default=int(os.getenv('DB_PORT','3306')))
    p.add_argument('--user',default=os.getenv('DB_USERNAME'))
    args=p.parse_args()
    HERE=args.output_dir.resolve()
    if args.action=='generate':
        generate()
    else:
        manifest=json.loads((HERE/'dataset-manifest.json').read_text(encoding='utf-8'))
        if manifest['schema']!=SCHEMA:
            raise SystemExit('Only course_change_loadtest is supported; production is not a target.')
        db=connect_db(args)
        try:
            if args.action=='apply':apply(db,manifest)
            else:verify(db,manifest)
        finally:db.close()
