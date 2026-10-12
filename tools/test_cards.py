import unittest

from cards import INSTRUMENTS, MAJOR, load, parse_key, seed_key, written_key


class Transposition(unittest.TestCase):
    def test_offset_matches_built_pitch(self):
        # Concert pitch of the horn's name reads as written C on that horn.
        for name, (_, pitch, offset) in INSTRUMENTS.items():
            with self.subTest(name):
                self.assertEqual(written_key(pitch, offset), "C")

    def test_every_key_on_every_horn(self):
        for name, (_, _, offset) in INSTRUMENTS.items():
            for pc, concert in enumerate(MAJOR):
                self.assertEqual(written_key(concert, offset), MAJOR[(pc + offset) % 12])

    def test_minor_prefers_sharps(self):
        self.assertEqual(written_key("E-", 9), "C# minor")
        self.assertEqual(written_key("A-", 0), "A minor")

    def test_major_prefers_flats(self):
        self.assertEqual(written_key("Bb", 2), "C")
        self.assertEqual(written_key("E", 9), "Db")

    def test_unparseable_keys_return_none(self):
        for raw in ["Eb, C", "", None, "H", "Cb-x"]:
            self.assertIsNone(written_key(raw, 2))


class SeedData(unittest.TestCase):
    def test_every_key_in_data_parses(self):
        unparsed = {t["key"] for t in load("standards.json") if t["key"] and parse_key(seed_key(t)) is None}
        self.assertEqual(unparsed, set())

    def test_some_exercises_apply_to_tunes(self):
        self.assertTrue(any(e["applies_to_tune"] for e in load("exercises.json")))


if __name__ == "__main__":
    unittest.main()
