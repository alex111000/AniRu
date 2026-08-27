"""Offline UI smoke test on an ephemeral CI emulator, never on a user's device."""
import json
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET

OUT = pathlib.Path("ui-smoke")
OUT.mkdir(exist_ok=True)
PACKAGE = "com.aniru.tv"


def adb(*args, timeout=25):
    return subprocess.check_output(["adb", *args], timeout=timeout)


def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/aniru-ui.xml")
    return adb("shell", "cat", "/sdcard/aniru-ui.xml")


def tap(node):
    bounds = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
    if len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1]:
        adb("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))
        return True
    return False


def wait_for(predicate, seconds=45):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        raw = dump()
        nodes = list(ET.fromstring(raw).iter("node"))
        if predicate(nodes):
            return raw
        for node in nodes:
            if node.get("resource-id", "").endswith("/configActionSkip"):
                tap(node)
        time.sleep(2)
    raise AssertionError("UI did not reach the expected state")


def screenshot(name, raw):
    (OUT / (name + ".xml")).write_bytes(raw)
    (OUT / (name + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))


try:
    apks = list(pathlib.Path("smoke-apk").rglob("*.apk"))
    assert len(apks) == 1, "Expected exactly one APK"
    adb("install", "-r", str(apks[0]), timeout=60)
    # Synthetic, explicitly labelled fixtures test cached grid rendering only.
    entries = []
    for kind, title, ident in [("MOVIE", "UI test movie", "900001"), ("SERIES", "UI test series", "900002")]:
        entries.append({"anime": {"provider": "ANILIBRIA", "id": ident, "title": title,
            "originalTitle": title, "description": "Offline CI fixture", "posterUrl": "", "year": "2024",
            "extra": "", "kind": kind, "genres": ["Тест"], "rating": 8.0, "addedAt": 1,
            "externalIds": {}}, "firstSeen": 1})
    fixture = json.dumps({"entries": entries, "batchTime": 1}).encode()
    adb("shell", "run-as", PACKAGE, "mkdir", "-p", "files")
    subprocess.run(["adb", "shell", "run-as", PACKAGE, "sh", "-c", "'cat > files/unified-catalog-v2.json'"], input=fixture, check=True, timeout=20)
    assert json.loads(adb("shell", "run-as", PACKAGE, "cat", "files/unified-catalog-v2.json")) == json.loads(fixture)
    adb("shell", "svc", "wifi", "disable")
    adb("shell", "svc", "data", "disable")
    adb("logcat", "-c")
    adb("shell", "am", "start", "-n", PACKAGE + "/ru.radiationx.anilibria.screen.launcher.MainActivity")
    home = wait_for(lambda nodes: any(n.get("text") == "Фильмы" for n in nodes), seconds=90)
    screenshot("01-main", home)
    for name, fixture_title, filename in [("Фильмы", "UI test movie", "02-movies"), ("Сериалы", "UI test series", "03-series")]:
        matching = []
        for attempt in range(3):
            nodes = list(ET.fromstring(dump()).iter("node"))
            matching = [n for n in nodes if n.get("text") == name]
            if matching:
                break
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
            time.sleep(1)
        assert matching, "Sidebar entry missing: " + name
        matching.sort(key=lambda n: "header" not in n.get("resource-id", ""))
        assert tap(matching[0])
        time.sleep(1)
        screenshot(filename + "-opening", dump())
        raw = wait_for(lambda ns: any(n.get("text") == fixture_title or n.get("content-desc") == fixture_title for n in ns))
        screenshot(filename, raw)
        assert any(n.get("text") == "Сортировка" for n in ET.fromstring(raw).iter("node"))
        # Exercise remote focus separately from touch navigation.
        for key in ["KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_DOWN", "KEYCODE_DPAD_LEFT"]:
            adb("shell", "input", "keyevent", key)
    print("PASS: cold launch, cached Movie/Series grids, filters and D-pad input")
finally:
    try:
        screenshot("04-final-state", dump())
    except Exception as error:
        print("Final UI capture unavailable:", error)
    try:
        (OUT / "catalog-cache.json").write_bytes(adb("shell", "run-as", PACKAGE, "cat", "files/unified-catalog-v2.json"))
    except Exception as error:
        print("Cache capture unavailable:", error)
    log = adb("logcat", "-d").decode("utf-8", errors="replace")
    (OUT / "logcat.txt").write_text(log)
    if re.search(r"FATAL EXCEPTION[^\n]*\n(?:[^\n]*\n){0,3}[^\n]*Process: com\.aniru\.tv", log):
        raise AssertionError("AniRu crashed; see logcat.txt")
