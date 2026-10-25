import unittest
from verify_actor_packets import cadence_sites, check_source, INTEGER_TARGET, SCHEDULE_TARGET


class ActorPacketHookTest(unittest.TestCase):
    def test_integer_cadence(self):
        code = "public void sendChanges();\nCode:\n getfield #1 // Field updateInterval:I\n  private void next();"
        self.assertEqual((1, 0), cadence_sites(code))

    def test_multiple_integer_reads(self):
        code = "public void sendChanges();\n" + "getfield #1 // Field updateInterval:I\n" * 4
        self.assertEqual((4, 0), cadence_sites(code))

    def test_scheduled_cadence(self):
        code = "public void sendChanges();\ninvokeinterface #1 // UpdateInterval.test:(I)Z"
        self.assertEqual((0, 1), cadence_sites(code))

    def test_missing_or_changed_mechanism_fails(self):
        for code in ["", "public void sendChanges();\nCode:\nreturn", "public void sendChanges();\nField updateInterval:I\nUpdateInterval.test:(I)Z"]:
            with self.assertRaises(ValueError):
                cadence_sites(code)

    def test_other_methods_do_not_count_as_cadence(self):
        code = "public void sendChanges();\nreturn\n  private void another();\nField updateInterval:I"
        with self.assertRaises(ValueError):
            cadence_sites(code)

    def test_hooks_require_scope_registration_and_group(self):
        self.assertEqual(5, len(check_source("", [])))
        source = INTEGER_TARGET + SCHEDULE_TARGET + 'this.entity instanceof ActorClonePlayer'
        source += '@Group(name = "fbactors$packetCadence", min = 1)' * 2
        self.assertEqual([], check_source(source, ["MixinActorPacketTracker"]))


if __name__ == "__main__":
    unittest.main()
