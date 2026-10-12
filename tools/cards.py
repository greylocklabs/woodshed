#!/usr/bin/env python3
"""Print practice cards composed from the seed data: tune x exercise x written key."""

import argparse
import json
import random
import re
from pathlib import Path

DATA = Path(__file__).resolve().parent.parent / "data"

# name: (family, pitch the horn is built in, semitones from concert to written)
INSTRUMENTS = {
    "soprano-sax": ("Saxophone", "Bb", 2),
    "alto-sax": ("Saxophone", "Eb", 9),
    "tenor-sax": ("Saxophone", "Bb", 2),
    "bari-sax": ("Saxophone", "Eb", 9),
    "clarinet": ("Clarinet", "Bb", 2),
    "bass-clarinet": ("Clarinet", "Bb", 2),
    "piccolo": ("Flute", "C", 0),
    "flute": ("Flute", "C", 0),
    "alto-flute": ("Flute", "G", 5),
    "trumpet": ("Brass", "Bb", 2),
    "trombone": ("Brass", "C", 0),
}

MAJOR = ["C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"]
MINOR = ["C", "C#", "D", "Eb", "E", "F", "F#", "G", "G#", "A", "Bb", "B"]
NATURALS = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
KEY_RE = re.compile(r"^([A-G])([b#]?)(-?)$")


def parse_key(raw):
    """Return (pitch class, is_minor), or None for anything that is not a single key."""
    m = KEY_RE.match(raw or "")
    if not m:
        return None
    letter, accidental, minor = m.groups()
    pc = (NATURALS[letter] + {"b": -1, "#": 1, "": 0}[accidental]) % 12
    return pc, minor == "-"


def spell(pc, minor):
    return (MINOR[pc] + " minor") if minor else MAJOR[pc]


def written_key(raw, offset):
    key = parse_key(raw)
    if key is None:
        return None
    pc, minor = key
    return spell((pc + offset) % 12, minor)


def load(name):
    with open(DATA / name) as f:
        return json.load(f)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("-i", "--instrument", default="alto-sax", choices=INSTRUMENTS)
    p.add_argument("-n", "--count", type=int, default=25)
    p.add_argument("--seed", type=int, help="repeat a previous draw")
    p.add_argument("--style", help="only tunes with this style, e.g. Ballad")
    p.add_argument("--detail", action="store_true", help="add exercise text and form")
    args = p.parse_args()

    family, _, offset = INSTRUMENTS[args.instrument]
    tunes = [t for t in load("standards.json") if not args.style or t["style"] == args.style]
    exercises = [
        e for e in load("exercises.json")
        if e["applies_to_tune"] and (not e["families"] or family in e["families"])
    ]
    if not tunes or not exercises:
        p.error("no tunes or exercises match")

    cards = [(t, e) for t in tunes for e in exercises]
    rng = random.Random(args.seed)
    print(f"{len(cards)} possible cards for {args.instrument}; drawing {min(args.count, len(cards))}\n")
    for tune, ex in rng.sample(cards, min(args.count, len(cards))):
        key = written_key(tune["common_key"], offset)
        key_text = f"written {key}" if key else f"concert {tune['common_key'] or 'key unknown'}, transpose it yourself"
        print(f"{tune['title']} | {ex['name']} | {key_text}")
        if args.detail:
            form = " ".join(str(x) for x in (tune["form"], tune["bars"] and f"{tune['bars']} bars") if x)
            print(f"    {ex['description']}")
            if form:
                print(f"    {form}, {tune['style']}")


if __name__ == "__main__":
    main()
