#!/usr/bin/env python3
"""Run the vault browser test against a loopback JAR and disposable SQLite."""

import argparse
from contextlib import closing
import hashlib
import os
from pathlib import Path
import runpy
import secrets
import socket
import sqlite3
import subprocess
import tempfile
import time
import urllib.request
import uuid

REPOSITORY = Path(__file__).resolve().parents[1]


def terminate(process):
    if process is None or process.poll() is not None:
        return
    process.terminate()
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=10)


def start(java, jar, database, port, logfile):
    with logfile.open("w") as log:
        process = subprocess.Popen([
            java, "-jar", str(jar), "--server.address=127.0.0.1", f"--server.port={port}",
            f"--spring.datasource.url=jdbc:sqlite:{database}",
        ], stdout=log, stderr=subprocess.STDOUT)
    try:
        client = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        end = time.monotonic() + 45
        while process.poll() is None and time.monotonic() < end:
            try:
                with client.open(f"http://127.0.0.1:{port}/api/health", timeout=1):
                    return process
            except OSError:
                time.sleep(0.2)
        raise RuntimeError(f"Packaged application did not start ({logfile})")
    except BaseException:
        terminate(process)
        raise


def exercise(options, directory):
    database = directory / "vault-fixture.sqlite"
    jar = options.candidate_jar.resolve()
    password = secrets.token_urlsafe(32)
    device_cookie = secrets.token_urlsafe(32)
    generate = runpy.run_path(str(REPOSITORY / "tools/test-device-identity-restart.py"))["generate_fixtures"]
    hash_value, _ = generate(options.java, jar, directory, password)
    with closing(socket.socket()) as listener:
        listener.bind(("127.0.0.1", 0))
        port = listener.getsockname()[1]

    server = None
    browser = None
    try:
        server = start(options.java, jar, database, port, directory / "initial.log")
        with sqlite3.connect(database) as connection:
            version = connection.execute(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"
            ).fetchone()[0]
            assert int(version) >= 7, "The packaged vault requires Flyway V7 or later"
            user_id = connection.execute(
                "INSERT INTO users(username,display_name,password_hash) VALUES ('vault_fixture','Vault Fixture',?)",
                (hash_value,),
            ).lastrowid
            connection.execute("""
                INSERT INTO qva_devices(id,user_id,name,credential_digest,status,registered_ms)
                VALUES (?,?,?,?,'APPROVED',1)
                """, (str(uuid.uuid4()), user_id, "Vault test browser", hashlib.sha256(device_cookie.encode()).hexdigest()))
        environment = dict(os.environ,
            QVA_TEST_BASE_URL=f"http://127.0.0.1:{port}", QVA_TEST_USERNAME="vault_fixture",
            QVA_TEST_PASSWORD=password, QVA_TEST_DB_PATH=str(database),
            QVA_VAULT_COOKIE=device_cookie, QVA_VAULT_CONTROL=str(directory))
        browser = subprocess.Popen(
            ["npm", "test", "--", "vault.spec.ts", "--reporter=list", "--output", str(directory / "browser")],
            cwd=REPOSITORY / "frontend", env=environment)
        requested = directory / "restart.request"
        ready = directory / "restart.ready"
        deadline = time.monotonic() + 155
        while browser.poll() is None and time.monotonic() < deadline:
            if requested.exists() and not ready.exists():
                terminate(server)
                server = start(options.java, jar, database, port, directory / "restarted.log")
                ready.touch()
                print(f"Packaged server restarted on the same SQLite fixture at Flyway V{version}.", flush=True)
            time.sleep(0.2)
        if browser.poll() != 0:
            raise RuntimeError("Packaged vault browser scenario failed or timed out")
    finally:
        terminate(browser)
        terminate(server)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate-jar", type=Path, required=True)
    parser.add_argument("--java", default="java")
    parser.add_argument("--output", type=Path, help="Keep the disposable database, logs, and browser report here")
    options = parser.parse_args()
    if options.output:
        directory = options.output.resolve()
        directory.mkdir(parents=True, exist_ok=True)
        exercise(options, directory)
    else:
        with tempfile.TemporaryDirectory(prefix="qva-vault-fixture-") as directory:
            exercise(options, Path(directory))


if __name__ == "__main__":
    main()
