from pathlib import Path
import subprocess
import os
import tempfile
import unittest
from unittest.mock import patch

import affected_e2e as e2e


class AffectedE2ETests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.pom('.', 'root', ['core', 'feature', 'other', 'web', 'unrelated'])
        self.pom('core', 'core')
        self.pom('feature', 'feature', dependencies=['core'])
        self.pom('other', 'other')
        self.pom('web', 'web', dependencies=['feature'])
        self.pom('unrelated', 'unrelated', dependencies=['other'])
        for module in ('web', 'unrelated'):
            test = self.root / module / 'src/test/java/example/ui' / (module.title() + 'UiIT.java')
            test.parent.mkdir(parents=True)
            test.write_text('class BrowserIT {}')

    def pom(self, path, name, modules=(), dependencies=()):
        directory = self.root / path
        directory.mkdir(exist_ok=True)
        (directory / 'pom.xml').write_text(
            '<project xmlns="http://maven.apache.org/POM/4.0.0"><artifactId>' + name + '</artifactId><modules>'
            + ''.join('<module>' + module + '</module>' for module in modules) + '</modules><dependencies>'
            + ''.join('<dependency><artifactId>' + dep + '</artifactId></dependency>' for dep in dependencies)
            + '</dependencies></project>')

    def test_transitive_consumers_not_unrelated_browser_modules(self):
        plan = e2e.select(self.root, ['core/src/main/Core.java'])
        self.assertEqual(plan['modules'], ['web'])
        self.assertEqual(plan['tests'], ['WebUiIT'])

    def test_untracked_or_deleted_test_changes_select_owner(self):
        self.assertEqual(e2e.select(self.root, ['web/src/test/java/example/ui/NewUiIT.java'])['modules'], ['web'])

    def test_unknown_baseline_build_and_unmapped_changes_run_all(self):
        for paths in (None, ['pom.xml'], ['web/pom.xml'], ['.github/workflows/tests.yml'], ['scripts/tool.sh']):
            with self.subTest(paths=paths):
                self.assertEqual(e2e.select(self.root, paths)['modules'], ['unrelated', 'web'])

    def test_docs_and_unchanged_skip_browser_tests(self):
        for paths in ([], ['README.md', 'docs/development/testing.md']):
            self.assertEqual(e2e.select(self.root, paths)['tests'], [])

    def test_mixed_docs_and_code_does_not_skip(self):
        self.assertEqual(e2e.select(self.root, ['README.md', 'feature/src/main/F.java'])['modules'], ['web'])

    def test_diff_includes_committed_staged_unstaged_untracked_and_deleted_paths(self):
        def git(*args):
            return subprocess.check_output(['git', *args], cwd=self.root, stderr=subprocess.DEVNULL).decode().strip()
        git('init'); git('config', 'user.name', 'Test'); git('config', 'user.email', 'test@example.test')
        git('add', '.'); git('commit', '-m', 'baseline'); baseline = git('rev-parse', 'HEAD')
        (self.root / 'committed.txt').write_text('committed'); git('add', '.'); git('commit', '-m', 'change')
        (self.root / 'staged.txt').write_text('staged'); git('add', '.')
        (self.root / 'web/pom.xml').write_text('unstaged')
        (self.root / 'other/pom.xml').unlink()
        (self.root / 'new.txt').write_text('untracked')
        self.assertEqual(e2e.changed_paths(self.root, baseline),
                         ['committed.txt', 'new.txt', 'other/pom.xml', 'staged.txt', 'web/pom.xml'])

    def test_documentation_cli_does_not_create_untracked_bytecode(self):
        import shutil
        source = Path(__file__).resolve().parent
        scripts = self.root / ".github/scripts"
        scripts.mkdir(parents=True)
        for name in ("affected_e2e.py", "docs_only_scope.py"):
            shutil.copy(source / name, scripts / name)
        def git(*args):
            return subprocess.check_output(["git", *args], cwd=self.root, stderr=subprocess.DEVNULL).decode().strip()
        git("init"); git("config", "user.name", "Test"); git("config", "user.email", "test@example.test")
        (self.root / "README.md").write_text("baseline")
        git("add", "."); git("commit", "-m", "baseline")
        baseline = git("rev-parse", "HEAD")
        (self.root / "README.md").write_text("documentation change")
        output = subprocess.check_output(["python3", str(scripts / "affected_e2e.py"),
            "--baseline-sha", baseline, "--plan-only"], cwd=self.root, text=True)
        self.assertIn('"modules": []', output)
        self.assertEqual(git("ls-files", "--others", "--exclude-standard"), "")

    def test_invalid_baseline_falls_back_to_all(self):
        self.assertIsNone(e2e.changed_paths(self.root, '--unsafe'))

    def test_real_dashboard_change_selects_browser_regressions(self):
        root = Path(__file__).resolve().parents[2]
        plan = e2e.select(root, ['modules/investment/src/main/java/Dashboard.java'])
        self.assertEqual(plan['modules'], ['app'])
        for test in ('InvestmentDashboardGoldenUiIT', 'HappyInvestorReadOnlyUiIT', 'UiPageSmokeIT'):
            self.assertIn(test, plan['tests'])

    def test_validation_script_runs_e2e_after_maven_and_propagates_failure(self):
        root = Path(__file__).resolve().parents[2]
        tools = self.root / "bin"
        tools.mkdir()
        log = self.root / "commands.log"
        for name, exit_code in (("mvn", 0), ("python3", 9)):
            tool = tools / name
            tool.write_text(f'#!/bin/bash\nprintf "%s\\n" "{name} $*" >> "$COMMAND_LOG"\nexit {exit_code}\n')
            tool.chmod(0o755)
        env = {**os.environ, "PATH": str(tools) + os.pathsep + os.environ["PATH"], "COMMAND_LOG": str(log)}
        result = subprocess.run(["bash", "scripts/agent-validate.sh", "--baseline-sha", "a" * 40],
                                cwd=root, env=env, capture_output=True)
        self.assertEqual(result.returncode, 9)
        self.assertEqual(log.read_text().splitlines(), [
            "mvn -B spotless:check", "mvn -B test", "mvn -B package -DskipTests",
            "python3 .github/scripts/affected_e2e.py --baseline-sha " + "a" * 40])

    def test_executor_runs_selected_failsafe_tests_and_propagates_failure(self):
        with patch('sys.argv', ['affected_e2e.py']), patch.object(e2e.subprocess, 'call', return_value=1) as run:
            self.assertEqual(e2e.main(), 1)
        command = run.call_args.args[0]
        self.assertIn('failsafe:verify', command)
        self.assertIn('-Dit.test=HappyInvestorReadOnlyUiIT,InvestmentDashboardGoldenUiIT,LongTermAssetCrudUiIT,MarketRadarOutlookUiIT,PlanSimulationCrudUiIT,UiPageSmokeIT', command)


if __name__ == '__main__':
    unittest.main()
