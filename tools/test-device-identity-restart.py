#!/usr/bin/env python3
"""Exercise the packaged identity API across a V6-to-V7 upgrade and process restart."""

import argparse
import base64
from contextlib import contextmanager
import hashlib
import http.cookiejar
import json
import os
from pathlib import Path
import re
import secrets
import sqlite3
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile

IDENTITY = "/api/v2/device-identities"


class Browser:
    def __init__(self, url, username, password, device_cookie):
        self.url = url
        jar = http.cookiejar.CookieJar()
        jar.set_cookie(http.cookiejar.Cookie(
            version=0, name="QVA_DEVICE", value=device_cookie, port=None, port_specified=False,
            domain="127.0.0.1", domain_specified=False, domain_initial_dot=False,
            path="/api/v2", path_specified=True, secure=False, expires=None, discard=True,
            comment=None, comment_url=None, rest={}))
        self.opener = urllib.request.build_opener(
            urllib.request.ProxyHandler({}),
            urllib.request.HTTPCookieProcessor(jar),
        )
        self.headers = {}
        self.call("POST", "/api/auth/login", 200,
                  urllib.parse.urlencode({"username": username, "password": password}).encode(),
                  {"Content-Type": "application/x-www-form-urlencoded"})
        csrf = self.call("GET", "/api/v2/csrf", 200)
        self.headers[csrf["headerName"]] = csrf["token"]

    def call(self, method, path, expected, body=None, headers=None):
        request = urllib.request.Request(
            self.url + path, method=method, data=body, headers={**self.headers, **(headers or {})})
        try:
            response = self.opener.open(request, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            content = response.read()
            if response.status != expected:
                raise AssertionError(f"{method} {path}: expected {expected}, got {response.status}")
            if response.headers.get_content_type() == "application/json":
                return json.loads(content)
            return content


@contextmanager
def server(java, jar, database, log_file, *extra):
    command = [java, "-jar", str(jar), "--server.address=127.0.0.1", "--server.port=0",
               f"--spring.datasource.url=jdbc:sqlite:{database}", *extra]
    with log_file.open("w") as output:
        process = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT)
        try:
            opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
            deadline = time.monotonic() + 60
            while process.poll() is None and time.monotonic() < deadline:
                match = re.search(r"Tomcat started on port (\d+)", log_file.read_text(encoding="utf-8"))
                if match:
                    url = "http://127.0.0.1:" + match[1]
                    try:
                        with opener.open(url + "/api/health", timeout=2):
                            break
                    except OSError:
                        pass
                time.sleep(0.2)
            else:
                raise RuntimeError(f"Packaged server did not become ready. See {log_file}")
            yield url
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=12)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=10)


def generate_fixtures(java, jar, directory, password):
    # Reuse the packaged Spring password encoder and JDK Ed25519 for disposable fixtures.
    libs = []
    with zipfile.ZipFile(jar) as archive:
        for prefix in ("BOOT-INF/lib/spring-security-crypto-", "BOOT-INF/lib/spring-jcl-"):
            item = next(name for name in archive.namelist() if name.startswith(prefix) and name.endswith(".jar"))
            libs.append(archive.extract(item, directory))
    source = directory / "IdentityFixture.java"
    source.write_text("""
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Scanner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
class IdentityFixture {
    public static void main(String[] args) throws Exception {
        System.out.println(new BCryptPasswordEncoder(12).encode(new Scanner(System.in).nextLine()));
        var keys = KeyPairGenerator.getInstance("Ed25519");
        for (int i = 0; i < 2; i++) {
            System.out.println(Base64.getEncoder().encodeToString(keys.generateKeyPair().getPublic().getEncoded()));
        }
    }
}
""", encoding="utf-8")
    result = subprocess.run([java, "--class-path", os.pathsep.join(libs), str(source)],
                            input=password + "\n", text=True, capture_output=True, timeout=30, check=True)
    digest, first_key, second_key = result.stdout.splitlines()
    return digest, [base64.b64decode(first_key), base64.b64decode(second_key)]


def schema_version(connection):
    return connection.execute("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1").fetchone()[0]


def seed(database, password_hash):
    browsers = []
    participants = []
    conversation = str(uuid.uuid4())
    with sqlite3.connect(database) as connection:
        assert schema_version(connection) == "6"
        for name in ("identity_fixture_a", "identity_fixture_b"):
            user = connection.execute(
                "INSERT INTO users(username,display_name,password_hash) VALUES (?,?,?)",
                (name, name, password_hash)).lastrowid
            device = str(uuid.uuid4())
            cookie = secrets.token_urlsafe(32)
            connection.execute("""
                INSERT INTO qva_devices(id,user_id,name,credential_digest,status,registered_ms)
                VALUES (?,?,?,?,'APPROVED',1)
                """, (device, user, "Disposable test browser", hashlib.sha256(cookie.encode()).hexdigest()))
            browsers.append((name, device, cookie))
            participants.append(user)
        connection.execute("""
            INSERT INTO qva_direct_conversations(id,lower_user_id,upper_user_id,created_ms) VALUES (?,?,?,1)
            """, (conversation, *participants))
    return browsers, conversation


def exercise(java, previous, candidate, directory):
    database = directory / "fixture.sqlite"
    password = secrets.token_urlsafe(32)
    password_hash, keys = generate_fixtures(java, candidate, directory, password)
    with server(java, previous, database, directory / "v6.log"):
        pass
    fixtures, conversation = seed(database, password_hash)
    peer = f"{IDENTITY}/conversations/{conversation}/devices/{fixtures[1][1]}"
    content_type = {"Content-Type": "application/octet-stream"}

    def browsers(url):
        return [Browser(url, name, password, device_cookie) for name, _, device_cookie in fixtures]

    with server(java, candidate, database, directory / "v7-default.log") as url:
        with sqlite3.connect(database) as connection:
            assert schema_version(connection) == "7"
            assert connection.execute("SELECT COUNT(*) FROM qva_direct_conversations").fetchone() == (1,)
        alice, _ = browsers(url)
        alice.call("GET", "/", 200)
        alice.call("GET", "/app/", 200)
        assert alice.call("GET", "/api/v2/conversations/status", 200)["enabled"] is False
        alice.call("GET", IDENTITY + "/me", 503)
    print("PASS: Packaged V6-to-V7 migration preserved the fixture. Both web routes respond and both feature flags default to off.")

    enabled = "--qva.device-identity.enabled=true"
    claim_id = str(uuid.uuid4())
    packets = [secrets.token_bytes(24), secrets.token_bytes(24)]
    with server(java, candidate, database, directory / "v7-identity.log", enabled) as url:
        alice, bob = browsers(url)
        for browser, key in zip((alice, bob), keys):
            browser.call("PUT", IDENTITY + "/me/credentials/0", 201, key, content_type)
        peer_identity = alice.call("GET", peer, 200)
        assert peer_identity["verification"] == "UNVERIFIED"
        assert peer_identity["fingerprint"] == hashlib.sha256(keys[1]).hexdigest()
        assert alice.call("PUT", peer + "/verification/1", 200, headers={
            "QVA-Identity-Fingerprint": peer_identity["fingerprint"]})["verification"] == "VERIFIED"
        for packet in packets:
            bob.call("POST", IDENTITY + "/me/credentials/1/key-packages", 201, packet, content_type)
        claim = alice.call("POST", peer + "/key-packages/1/claim", 200, headers={"Idempotency-Key": claim_id})
        first_packet = base64.b64decode(claim["keyPackage"])
        assert first_packet in packets

    with server(java, candidate, database, directory / "v7-restarted.log", enabled) as url:
        alice, bob = browsers(url)
        assert bob.call("GET", IDENTITY + "/me", 200)["version"] == 1
        assert alice.call("GET", peer, 200)["verification"] == "VERIFIED"
        alice.call("POST", peer + "/key-packages/1/claim", 409, headers={"Idempotency-Key": claim_id})
        second = alice.call("POST", peer + "/key-packages/1/claim", 200, headers={"Idempotency-Key": str(uuid.uuid4())})
        assert second["id"] != claim["id"]
        assert base64.b64decode(second["keyPackage"]) == next(packet for packet in packets if packet != first_packet)
        alice.call("POST", peer + "/key-packages/1/claim", 409, headers={"Idempotency-Key": str(uuid.uuid4())})
    with sqlite3.connect(database) as connection:
        assert connection.execute("""
            SELECT COUNT(*) FROM qva_device_key_packages WHERE package_bytes IS NULL AND claimed_ms IS NOT NULL
            """).fetchone() == (2,)
        assert connection.execute("PRAGMA foreign_key_check").fetchall() == []
    print("PASS: A fresh JVM and newly authenticated sessions restored identity and verification, prevented claim replay, and returned the remaining package once.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", default="java", help="Java 17 executable")
    parser.add_argument("--previous-jar", required=True, type=Path, help="Accepted M5.2 packaged JAR with latest migration V6")
    parser.add_argument("--candidate-jar", required=True, type=Path)
    options = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="qva-m62-restart-") as name:
        directory = Path(name)
        try:
            exercise(options.java, options.previous_jar.resolve(), options.candidate_jar.resolve(), directory)
        except Exception:
            for log in sorted(directory.glob("*.log")):
                print(f"--- {log.name} ---")
                print(log.read_text(encoding="utf-8"))
            raise


if __name__ == "__main__":
    main()
