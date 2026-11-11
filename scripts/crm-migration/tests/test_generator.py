import importlib.util
import sys
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / 'generate_import_sql.py'
spec = importlib.util.spec_from_file_location('crm2_generator', SCRIPT)
g = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = g
spec.loader.exec_module(g)

class GeneratorTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.clients = self.root/'clients.txt'
        self.notes = self.root/'note'
        self.notes.mkdir()
        self.output = self.root/'output'

    def tearDown(self):
        self.temp.cleanup()

    def row(self, **values):
        data = dict.fromkeys(g.FIELDS, '')
        data.update(ragione_sociale="Laboratorio D'Angelo", note_id='note1', operatore='mario', tipo_cliente='Laboratorio', acquisizione='2021-01-01', prossima_chiamata='2026-12-12', coinvolgimento='3', telefono='012345', email_generica='info@example.test')
        data.update(values)
        return ';'.join(data[f] for f in g.FIELDS)

    def prepare(self, row=None, xml=None, **kwargs):
        self.clients.write_text(row or self.row(), encoding='utf-8-sig')
        (self.notes/'note1.xml').write_text(xml or '<note><chiamata number="2" data="2021-02-03" operatore="Mario" newInterest="Listino">Una nota; con apici l\'ufficio</chiamata><chiamata number="1" data="2021-02-03" operatore="Mario" newInterest="Presentazione">Prima chiamata</chiamata></note>',encoding='utf-8')
        return g.generate(self.clients,self.notes,self.output,**kwargs)

    def test_namespaces_original_ids_and_historical_calls(self):
        counts=self.prepare()
        self.assertEqual(9,len(counts))
        sql=(self.output/'import_clienti.sql').read_text()
        self.assertIn('INSERT INTO CRM.CLIENTI',sql)
        self.assertIn("D''Angelo",sql)
        self.assertIn(g.uid('cliente','note1'),sql)
        self.assertIn("'Listino'",sql)
        notes=(self.output/'import_note_interazioni.sql').read_text()
        self.assertIn("TIMESTAMP('2021-02-03 00:00:01')",notes)
        self.assertIn("'LISTINO'",notes)
        self.assertLess(notes.index('Prima chiamata'),notes.index("Una nota; con apici"))
        self.assertEqual(1,notes.count("DATE('2026-12-12')"))
        self.assertIn('ECOS.OPERATORI',(self.output/'import_operatori.sql').read_text())
        self.assertIn('blocked=false',(self.output/'migration.properties').read_text())

    def test_final_empty_column_with_or_without_legacy_trailing_separator(self):
        for suffix in ('',';'):
            with self.subTest(suffix=suffix):
                self.prepare(row=self.row()+suffix)
                self.assertIn('clients=1',(self.output/'migration.properties').read_text())

    def test_stable_ids_and_hashes_across_repeated_preparation(self):
        self.prepare();before=(self.output/'migration.properties').read_bytes()
        self.prepare();self.assertEqual(before,(self.output/'migration.properties').read_bytes())

    def test_directory_and_merged_utf16_notes_produce_same_sql(self):
        self.prepare();before=(self.output/'import_note_interazioni.sql').read_bytes()
        merged=self.root/'all.txt'
        merged.write_text('===== FILE: note1.xml =====\n'+(self.notes/'note1.xml').read_text()+'\n===== END FILE: note1.xml =====\n',encoding='utf-16')
        g.generate(self.clients,merged,self.output)
        self.assertEqual(before,(self.output/'import_note_interazioni.sql').read_bytes())

    def test_invalid_xml_blocks_apply_instead_of_silently_dropping_calls(self):
        with self.assertRaises(ValueError):self.prepare(xml='<note><chiamata>')
        self.assertIn('blocked=true',(self.output/'migration.properties').read_text())
        self.assertTrue((self.output/'import_report.txt').exists())

    def test_allow_anomalies_is_explicit_and_keeps_original_bad_dates(self):
        self.prepare(row=self.row(acquisizione='31/02/2021',coinvolgimento='8'),allow_anomalies=True)
        self.assertIn('anomalies.accepted=true',(self.output/'migration.properties').read_text())
        self.assertIn('31/02/2021',(self.output/'import_note_interazioni.sql').read_text())

    def test_invalid_dates_block_by_default(self):
        with self.assertRaises(ValueError):self.prepare(row=self.row(acquisizione='not-a-date'))
        self.assertIn('blocked=true',(self.output/'migration.properties').read_text())

    def test_undated_calls_keep_their_order_and_schedule_without_inventing_a_contact_date(self):
        self.prepare(xml='<note><chiamata number="1">Prima</chiamata><chiamata number="2">Ultima</chiamata></note>')
        notes=(self.output/'import_note_interazioni.sql').read_text()
        self.assertIn('{fn TIMESTAMPADD(SQL_TSI_SECOND, 0, CURRENT_TIMESTAMP)}',notes)
        self.assertIn('{fn TIMESTAMPADD(SQL_TSI_SECOND, 1, CURRENT_TIMESTAMP)}',notes)
        self.assertEqual(1,notes.count("NULL, DATE('2026-12-12')"))

    def test_duplicate_source_client_ids_are_rejected(self):
        with self.assertRaisesRegex(ValueError,'duplicato'):self.prepare(row=self.row()+'\n'+self.row(ragione_sociale='Altro laboratorio'))
        self.assertFalse((self.output/'migration.properties').exists())

    def test_corrupt_new_input_invalidates_previous_successful_manifest(self):
        self.prepare()
        with self.assertRaises(g.ClientFormatError):self.prepare(row='poche;colonne')
        self.assertFalse((self.output/'migration.properties').exists())

    def test_cli_missing_input_also_invalidates_old_preparation(self):
        self.prepare()
        result=subprocess.run([sys.executable,str(SCRIPT),'--clients',str(self.root/'missing.txt'),'--notes-dir',str(self.notes),'--output',str(self.output)],capture_output=True,text=True)
        self.assertEqual(2,result.returncode)
        self.assertFalse((self.output/'migration.properties').exists())

    def test_no_operator_uses_explicit_unknown_identity(self):
        self.prepare(row=self.row(operatore='NULL'),xml='<note><chiamata data="2020-01-01">Test</chiamata></note>')
        self.assertIn(g.UNKNOWN_OPERATOR,(self.output/'import_operatori.sql').read_text())
        self.assertIn(g.UNKNOWN_OPERATOR,(self.output/'import_clienti.sql').read_text())

    def test_long_values_rejected_without_truncation(self):
        with self.assertRaisesRegex(ValueError,'255'):self.prepare(row=self.row(ragione_sociale='x'*256))

    def test_orphan_xml_is_reported_and_blocks(self):
        (self.notes/'orphan.xml').write_text('<note/>')
        with self.assertRaises(ValueError):self.prepare()
        self.assertIn('orphan',(self.output/'import_report.txt').read_text())

    def test_long_clob_preserves_whitespace_and_quotes_at_chunk_boundaries(self):
        text='x'*7999+' '+" l'ufficio; "+'y'*40000
        result=g.sql_clob(text)
        self.assertIn(" || ' l''ufficio; ",result)
        self.assertGreater(result.count(' || '),4)

    def test_nonempty_unrecognized_merged_notes_rejected(self):
        file=self.root/'broken.txt';file.write_text('<note/>')
        with self.assertRaises(ValueError):g.parse_notes(file,g.Diagnostics())

    def test_generator_matches_the_package_executed_by_the_derby_integration_test(self):
        source=Path(__file__).parent/'fixtures'
        g.generate(source/'clients.txt',source/'note',self.output)
        certified=SCRIPT.parents[2]/'orodent-crm/src/test/resources/migration-fixture'
        for file in certified.iterdir():
            if file.is_file():
                self.assertEqual(file.read_bytes(),(self.output/file.name).read_bytes(),file.name)

    def test_missing_history_is_reported_without_blocking_import(self):
        self.clients.write_text(self.row(note_id="missing",volte_contattati="3"),encoding="utf-8")
        merged=self.root/"empty-notes.txt"
        merged.write_text("",encoding="utf-8")
        g.generate(self.clients,merged,self.output)
        self.assertIn("blocked=false",(self.output/"migration.properties").read_text())
        self.assertIn('Storici XML mancanti',(self.output/'import_report.txt').read_text())

if __name__=='__main__':
    unittest.main()
