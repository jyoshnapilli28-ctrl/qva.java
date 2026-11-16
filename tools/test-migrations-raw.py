"""Disposable raw SQLite smoke test for the checked-in V1-V8 SQL files.
This does not replace Flyway or Spring integration tests.
"""
import sqlite3
import tempfile
from pathlib import Path

MIGRATIONS = Path(__file__).resolve().parents[1] / "src/main/resources/db/migration"

with tempfile.TemporaryDirectory(prefix="qva-migration-smoke-") as temp:
    database = Path(temp) / "disposable.sqlite"
    connection = sqlite3.connect(database)
    connection.execute("PRAGMA foreign_keys=ON")
    for migration in sorted(MIGRATIONS.glob("V*.sql"), key=lambda item: int(item.name.split("__")[0][1:])):
        connection.executescript("BEGIN;\n" + migration.read_text() + "\nCOMMIT;")
        print(f"PASS {migration.name}")

    assert connection.execute("SELECT COUNT(*) FROM users").fetchone()[0] == 4
    tables = {row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    assert {
        "qva_devices", "qva_device_identities", "qva_device_key_packages",
        "qva_invitations", "qva_invitation_audit",
    } <= tables
    connection.close()

    connection = sqlite3.connect(database)
    assert connection.execute("SELECT COUNT(*) FROM users").fetchone()[0] == 4
    assert connection.execute("SELECT COUNT(*) FROM qva_invitations").fetchone()[0] == 0
    print("PASS disposable database close/reopen persistence check (4 seed rows; V1-V8 tables present)")
    connection.close()
