#!/usr/bin/env python3
"""Regress the real API-23 adb false-success and stale-installed-APK failure."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class CheckedInstallTest(unittest.TestCase):
    def run_install(self, output="Success", status=0, installed=b"new APK"):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            candidate = root / "candidate.apk"
            candidate.write_bytes(b"new APK")
            actual = root / "installed.apk"
            actual.write_bytes(installed)
            adb = root / "adb"
            adb.write_text("""#!/usr/bin/env python3
import os, shutil, sys
if sys.argv[1] == 'install':
    print(os.environ['Q7_TEST_ADB_OUTPUT'])
    sys.exit(int(os.environ['Q7_TEST_ADB_STATUS']))
elif sys.argv[1:4] == ['shell', 'pm', 'path']:
    print('package:/data/app/com.shihab.diplay.hudtest/base.apk')
elif sys.argv[1] == 'pull':
    shutil.copyfile(os.environ['Q7_TEST_INSTALLED_APK'], sys.argv[3])
else:
    sys.exit(2)
""")
            adb.chmod(0o700)
            env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ["PATH"],
                       Q7_TEST_ADB_OUTPUT=output, Q7_TEST_ADB_STATUS=str(status),
                       Q7_TEST_INSTALLED_APK=str(actual))
            script = Path(__file__).resolve().parents[1] / "adb_install_checked.sh"
            return subprocess.run(["bash", str(script), str(candidate), str(root / "install.txt")],
                                  env=env, capture_output=True, text=True)

    def test_api23_failure_with_zero_exit_is_rejected(self):
        result = self.run_install("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]", 0)
        self.assertNotEqual(result.returncode, 0)

    def test_success_with_old_apk_still_installed_is_rejected(self):
        result = self.run_install(installed=b"old APK")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("does not match", result.stderr)

    def test_transport_failure_is_rejected_even_if_output_says_success(self):
        self.assertNotEqual(self.run_install(status=1).returncode, 0)

    def test_success_requires_the_exact_candidate_bytes(self):
        result = self.run_install("Success\r")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Installed Q7 APK bytes verified:", result.stdout)


if __name__ == "__main__":
    unittest.main()
