"""Regression tests for reproducibility and the V3 fixture-login bug."""
import contextlib
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock,patch

import prepare_dataset as fixtures


class FixtureRegressionTest(unittest.TestCase):
    def test_csv_generation_matches_the_shared_reference(self):
        expected=json.loads((Path(__file__).parent/'dataset-manifest.expected.json').read_text(encoding='utf-8'))
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(fixtures,'HERE',Path(directory)),contextlib.redirect_stdout(io.StringIO()):
                actual=fixtures.generate()
            self.assertEqual(expected,actual)
            for table,entry in expected['tables'].items():
                raw=(Path(directory)/'dataset'/(table+'.csv')).read_bytes()
                self.assertEqual(entry['sha256'],hashlib.sha256(raw).hexdigest())

    def test_v2_and_v3_histories_are_supported(self):
        for versions in (['1','2'],['1','2','3']):
            with self.subTest(versions=versions):
                cursor=Mock()
                cursor.fetchall.return_value=[{'version':v,'success':1} for v in versions]
                self.assertEqual(versions,[r['version'] for r in fixtures.migration_history(cursor)])

    def test_missing_failed_and_future_migrations_are_rejected(self):
        for history in ([],[{'version':'1','success':1}],
                        [{'version':'1','success':1},{'version':'2','success':0}],
                        [{'version':v,'success':1} for v in ['1','2','3','4']]):
            with self.subTest(history=history):
                cursor=Mock();cursor.fetchall.return_value=history
                with self.assertRaises(RuntimeError):fixtures.migration_history(cursor)

    def test_v3_student_insert_has_hash_matching_mysql_sha2(self):
        row=['100001','LT2026102600001','fixture','department','1','21',fixtures.ANCHOR]
        prepared=fixtures.student_insert_row(row,True)
        self.assertEqual(row,prepared[:-1])
        self.assertEqual(hashlib.sha256(b'LT2026102600001:LT2026102600001').hexdigest(),prepared[-1])
        self.assertEqual(7,len(row))

    def test_v2_student_insert_does_not_reference_v3_column(self):
        row=['100001','LT2026102600001','fixture','department','1','21',fixtures.ANCHOR]
        self.assertEqual(row,fixtures.student_insert_row(row,False))


if __name__=='__main__':unittest.main()
