#!/usr/bin/env python3
"""Offline checks for the shipped resources, index and user-content boundaries."""
import io
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile
from generate_catalog import ROOT, generated_files, read_catalog


def verify():
    rows = read_catalog()
    expected = generated_files(rows)
    for relative, content in expected.items():
        assert (ROOT / relative).read_text(encoding="utf-8") == content, f"Out of date: {relative}"
    resources = []
    for qualifier in ["values", "values-bn"]:
        path = ROOT / f"app/src/main/res/{qualifier}/language_strings.xml"
        nodes = ET.parse(path).getroot().findall("string")
        names = [node.attrib["name"] for node in nodes]
        assert len(set(names)) == len(names) == len(rows)
        resources.append(set(names))
    assert resources[0] == resources[1]
    names = ET.parse(ROOT / "app/src/main/res/values/strings.xml").getroot()
    brand = names.find("./string[@name='app_name']")
    assert brand is not None and brand.text == "Mongosky" and brand.get("translatable") == "false"
    manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
    android = "{http://schemas.android.com/apk/res/android}"
    application = manifest.find("application")
    assert application.get(android + "name") == "com.mongosky.app.language.LanguageApplication"
    assert application.get(android + "label") == "Mongosky"
    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    assert "enableSplit = false" in gradle and 'resourceConfigurations += listOf("en", "bn")' in gradle
    src = ROOT / "app/src/main/java/com/mongosky/app"
    for path in src.rglob("*.kt"):
        if "language" in path.parts:
            continue
        content = path.read_text(encoding="utf-8")
        # These are user/remote text, never app-owned UI labels.
        assert not re.search(r'appText\(\s*(?:comment\.text|post\.displayText|message\.content|'
                             r'reel\.caption|draft\.caption|item\.text|item\.title|chat\.title|'
                             r'user\.fullName|summary\.summaryText\(\))\s*\)', content), path
        # AnnotatedText retains its spans; translate only its app-owned string fragments.
        assert not re.search(r'Text\(appText\([^\n]*\),\s*inlineContent\s*=', content), path
    index = {row["en"]: row["bn"] for row in rows}
    for en, bn in [("Home", "হোম"), ("Settings", "সেটিংস"), ("Language", "ভাষা"),
                   ("Choose your language", "আপনার ভাষা বেছে নিন"), ("Cancel", "বাতিল")]:
        assert index[en] == bn
    assert "Mongosky" not in index
    assert index["Love"] == "লাভ"
    assert index["Share"] == "শেয়ার"
    assert not any("ভালোবাসা" in row["bn"] for row in rows), "Use লাভ for the app's Love reaction."
    # An illustrative text-only compression check, not an APK/AAB size claim.
    compressed = io.BytesIO()
    with zipfile.ZipFile(compressed, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for relative in expected:
            archive.writestr(relative, expected[relative].encode())
    print(f"PASS: {len(rows)} matching native resource pairs; placeholders and brand preserved.")
    print("PASS: generated Kotlin index, locale packaging, startup wiring and user-text boundaries.")
    print(f"Generated resources + index compress to {len(compressed.getvalue()):,} bytes (ZIP, not APK).")


if __name__ == "__main__":
    verify()
