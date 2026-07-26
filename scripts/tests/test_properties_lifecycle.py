import importlib.util
import sys
import pathlib
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
spec = importlib.util.spec_from_file_location("acceptance", ROOT / "scripts" / "run-dedicated-function-acceptance.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PropertiesLifecycleTest(unittest.TestCase):
    class Args:
        command_timeout = 1
        shutdown_timeout = 1
        server_startup_timeout = 1
        rcon_startup_timeout = 1

    def acceptance(self, directory: pathlib.Path):
        a = module.Acceptance(self.Args())
        a.server_properties = directory / "server.properties"
        a.properties_backup = directory / "server.properties.partialreload.bak"
        a.properties_existed_before = a.server_properties.exists()
        a.original_properties_bytes = a.server_properties.read_bytes() if a.properties_existed_before else None
        return a

    def test_existing_file_restores_byte_for_byte_and_is_idempotent(self):
        with tempfile.TemporaryDirectory() as raw:
            d = pathlib.Path(raw); p = d / "server.properties"; original = b"level-name=world\n# preserved\n"
            p.write_bytes(original); a = self.acceptance(d); a.configure_rcon(); self.assertNotEqual(p.read_bytes(), original)
            a.restore_properties(); a.restore_properties(); self.assertEqual(p.read_bytes(), original)
            self.assertFalse((d / "server.properties.partialreload.bak").exists())

    def test_absent_file_is_removed_after_cleanup(self):
        with tempfile.TemporaryDirectory() as raw:
            d = pathlib.Path(raw); a = self.acceptance(d); self.assertFalse(a.properties_existed_before)
            a.configure_rcon(); self.assertTrue(a.server_properties.exists()); a.restore_properties()
            self.assertFalse(a.server_properties.exists()); self.assertFalse(a.properties_backup.exists())


if __name__ == "__main__":
    unittest.main()
