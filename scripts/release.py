#!/usr/bin/env python3
"""App-Releases (docs/veroeffentlichen.md, „Versionen“). Aufruf über scripts/release.sh.

    release.sh entwurf --version 0.32.0 [Ref]   versionName setzen, versionCode + 1 und die
                                                Änderungen seit dem letzten Tag in die
                                                fastlane-Changelogs (de-DE, en-US) schreiben
    release.sh taggen [Ref]                     prüfen, Tag v<versionName> und GitHub-Release

Quelle der Texte ist der Abschnitt „Für die Änderungshinweise“ der PRs, wie bei HUBIplan und der
Webseite. Der Herausgeber entscheidet, wann eine Version erscheint: „taggen“ nur nach
ausdrücklicher Freigabe (docs/veroeffentlichen.md). Ref ist ohne Angabe origin/main.
"""

# git und gh laufen mit festen Argumenten

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

GRADLE = "app/build.gradle.kts"
CHANGELOGS = {
    "de": "fastlane/metadata/android/de-DE/changelogs",
    "en": "fastlane/metadata/android/en-US/changelogs",
}
MAX_ZEICHEN = 500  # Grenze von Google Play und F-Droid
SEMVER = re.compile(r"^\d+\.\d+\.\d+$")
REPO = "hubiplan/hubiplan-android"


def fehler(text: str) -> None:
    print(f"Abbruch: {text}", file=sys.stderr)
    sys.exit(1)


def git(*args: str, check: bool = True) -> str:
    res = subprocess.run(["git", *args], capture_output=True, text=True, check=False)
    if check and res.returncode != 0:
        fehler(f"git {' '.join(args)}: {res.stderr.strip()}")
    return res.stdout.strip()


def gh(*args: str) -> str:
    exe = os.environ.get("GH", "gh")
    res = subprocess.run([exe, *args], capture_output=True, text=True, check=False)
    if res.returncode != 0:
        fehler(f"gh {' '.join(args[:2])}: {res.stderr.strip()}")
    return res.stdout


def semver(text: str) -> tuple[int, ...]:
    return tuple(int(x) for x in text.split("."))


def versionen(gradle: str) -> tuple[int, str]:
    code = re.search(r"^\s*versionCode\s*=\s*(\d+)\s*$", gradle, re.MULTILINE)
    name = re.search(r'^\s*versionName\s*=\s*"([^"]+)"\s*$', gradle, re.MULTILINE)
    if not code or not name:
        fehler(f"{GRADLE}: versionCode oder versionName nicht gefunden.")
    return int(code.group(1)), name.group(1)


def letzter_tag(ref: str) -> str:
    tags = git("tag", "--list", "v[0-9]*", "--merged", ref, "--sort=-v:refname").split()
    return next((t for t in tags if SEMVER.match(t[1:])), "")


def pr_nummern(von: str, bis: str) -> list[int]:
    bereich = f"{von}..{bis}" if von else bis
    nummern: list[int] = []
    for zeile in git("log", "--first-parent", "--format=%s", bereich).splitlines():
        m = re.search(r"\(#(\d+)\)\s*$", zeile) or re.match(
            r"Merge pull request #(\d+)\b", zeile
        )
        if m and int(m.group(1)) not in nummern:
            nummern.append(int(m.group(1)))
    return list(reversed(nummern))


def hinweise_aus_pr(body: str) -> dict[str, str]:
    m = re.search(
        r"^##\s*Für die Änderungshinweise\s*$(.*?)(?=^##\s|\Z)",
        body,
        re.MULTILINE | re.DOTALL,
    )
    if not m:
        return {}
    teil = re.sub(r"<!--.*?-->", "", m.group(1), flags=re.DOTALL)
    werte: dict[str, str] = {}
    for zeile in teil.splitlines():
        z = re.match(
            r"^\s*-\s*(de|en|Art|Braucht HUBIplan)\s*:\s*(.*)$", zeile, re.IGNORECASE
        )
        if z:
            werte[z.group(1).lower()] = z.group(2).strip()
    return werte


def leer(text: str) -> bool:
    return text.strip() in {"", "–", "-", "—"}


def entwurf(args: argparse.Namespace) -> None:
    ref = git("rev-parse", "--verify", f"{args.ref}^{{commit}}")
    if not SEMVER.match(args.version):
        fehler(f"Version „{args.version}“ hat nicht die Form X.Y.Z.")
    if git("tag", "--list", f"v{args.version}"):
        fehler(f"Tag v{args.version} gibt es schon.")
    gradle_pfad = Path(GRADLE)
    gradle = gradle_pfad.read_text(encoding="utf-8")
    code, name = versionen(gradle)
    # Gleich bleiben darf sie, solange es dafür noch keinen Tag gibt (Version schon erhöht)
    if semver(args.version) < semver(name) or (
        semver(args.version) == semver(name) and git("tag", "--list", f"v{name}")
    ):
        fehler(f"{args.version} ist nicht neuer als {name}.")
    vorher = args.seit or letzter_tag(ref)
    if not vorher:
        fehler(
            "Noch kein Tag v0.x.y: mit --seit <Commit> angeben, ab wann gesammelt wird."
        )

    punkte: dict[str, list[str]] = {"de": [], "en": []}
    ohne, braucht = [], []
    for nr in pr_nummern(vorher, ref):
        daten = json.loads(
            gh("pr", "view", str(nr), "--repo", REPO, "--json", "title,body")
        )
        h = hinweise_aus_pr(daten.get("body") or "")
        if not h:
            ohne.append(nr)
            continue
        if not leer(h.get("braucht hubiplan", "")):
            braucht.append(f"#{nr}: {h['braucht hubiplan']}")
        if leer(h.get("de", "")) and leer(h.get("en", "")):
            continue
        punkte["de"].append(f"* {h.get('de') or '(fehlt)'}")
        punkte["en"].append(f"* {h.get('en') or '(fehlt)'}")

    neu = code + 1
    for sprache, ordner in CHANGELOGS.items():
        text = "\n".join(punkte[sprache]) + "\n" if punkte[sprache] else ""
        pfad = Path(ordner) / f"{neu}.txt"
        pfad.parent.mkdir(parents=True, exist_ok=True)
        pfad.write_text(text, encoding="utf-8")
        if len(text) > MAX_ZEICHEN:
            print(
                f"Achtung: {pfad} hat {len(text)} Zeichen (höchstens {MAX_ZEICHEN}), kürzen."
            )
    gradle = re.sub(
        r"(^\s*versionCode\s*=\s*)\d+",
        rf"\g<1>{neu}",
        gradle,
        count=1,
        flags=re.MULTILINE,
    )
    gradle = re.sub(
        r'(^\s*versionName\s*=\s*)"[^"]+"',
        rf'\g<1>"{args.version}"',
        gradle,
        count=1,
        flags=re.MULTILINE,
    )
    gradle_pfad.write_text(gradle, encoding="utf-8")

    print(
        f"Entwurf für die App {args.version} (versionCode {neu}, seit {vorher[:12]}) eingetragen."
    )
    if ohne:
        print(
            "Ohne Abschnitt „Für die Änderungshinweise“: "
            + ", ".join(f"#{n}" for n in ohne)
        )
    if braucht:
        print("Braucht HUBIplan (in hubiplan/docs/versionen.md eintragen):")
        for zeile in braucht:
            print(f"  {zeile}")
    print(
        "Weiter: Changelogs lesen und kürzen, als PR „App " + args.version + "“ mergen,"
    )
    print(
        "lokal bauen und testen (scripts/test.sh), dann nach Freigabe des Herausgebers "
        "release.sh taggen."
    )


def taggen(args: argparse.Namespace) -> None:
    ref = git("rev-parse", "--verify", f"{args.ref}^{{commit}}")
    kurz = ref[:7]
    code, name = versionen(git("show", f"{ref}:{GRADLE}"))
    if not SEMVER.match(name):
        fehler(f"versionName „{name}“ hat nicht die Form X.Y.Z.")
    tag = f"v{name}"
    if git("tag", "--list", tag):
        fehler(f"{tag} gibt es schon. Neue Änderungen brauchen eine neue Version.")
    vorher = letzter_tag(ref)
    alle = git("tag", "--list", "v[0-9]*", "--sort=-v:refname").split()
    neuester = next((t for t in alle if SEMVER.match(t[1:])), "")
    if neuester and semver(name) <= semver(neuester[1:]):
        fehler(f"{name} ist nicht neuer als das neueste Release {neuester}.")
    if neuester and neuester != vorher:
        fehler(
            f"{kurz} enthält das neueste Release {neuester} nicht (wäre ein Rückschritt)."
        )
    texte = {}
    for sprache, ordner in CHANGELOGS.items():
        text = git("show", f"{ref}:{ordner}/{code}.txt", check=False)
        if not text.strip() or "(fehlt)" in text:
            fehler(f"{ordner}/{code}.txt fehlt oder ist unvollständig.")
        if len(text) > MAX_ZEICHEN:
            fehler(
                f"{ordner}/{code}.txt hat {len(text)} Zeichen (höchstens {MAX_ZEICHEN})."
            )
        texte[sprache] = text.strip()

    notizen = (
        f"{texte['de']}\n\n---\n\n**English**\n\n{texte['en']}\n\n"
        f"versionCode {code}. Signierte APK und AAB baut der Herausgeber "
        "(docs/veroeffentlichen.md); "
        "F-Droid baut selbst aus diesem Tag."
    )
    print(
        f"\nHUBIplan-App {name} (versionCode {code}) auf {kurz} (vorher: {vorher or '–'})\n"
    )
    print(notizen)
    if not args.ja:
        antwort = (
            input(f"\nRelease {tag} jetzt bei GitHub anlegen? [j/N] ").strip().lower()
        )
        if antwort not in {"j", "ja"}:
            print("Nichts angelegt.")
            return
    with tempfile.NamedTemporaryFile(
        "w", suffix=".md", delete=False, encoding="utf-8"
    ) as f:
        f.write(notizen)
    try:
        url = gh(
            "release",
            "create",
            tag,
            "--repo",
            REPO,
            "--target",
            ref,
            "--title",
            f"HUBIplan-App {name}",
            "--notes-file",
            f.name,
        )
    finally:
        os.unlink(f.name)
    print(url.strip())
    print(
        "Fertig. Tag holen: git fetch origin --tags. APK/AAB aus dem Tag bauen und anhängen."
    )


def main() -> None:
    p = argparse.ArgumentParser(
        prog="release.sh",
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    sub = p.add_subparsers(dest="befehl", required=True)
    e = sub.add_parser("entwurf", help="Version erhöhen und Changelogs eintragen")
    e.add_argument("ref", nargs="?", default="origin/main")
    e.add_argument("--version", required=True, help="neuer versionName, z. B. 0.32.0")
    e.add_argument(
        "--seit", help="ab diesem Commit sammeln (solange es keinen Tag gibt)"
    )
    t = sub.add_parser("taggen", help="Release anlegen (Tag und GitHub-Release)")
    t.add_argument("ref", nargs="?", default="origin/main")
    t.add_argument("--ja", action="store_true", help="ohne Rückfrage")
    args = p.parse_args()
    entwurf(args) if args.befehl == "entwurf" else taggen(args)


if __name__ == "__main__":
    main()
