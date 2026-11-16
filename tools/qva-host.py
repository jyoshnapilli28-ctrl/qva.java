#!/usr/bin/env python3
"""Provision the one QVA HOST locally against a migrated SQLite database."""
import argparse
import os
import sqlite3
import subprocess
import time
import uuid
from pathlib import Path


def operator_identity():
    if os.name == "nt":
        return subprocess.check_output(["whoami", "/user", "/fo", "csv", "/nh"],
                                       text=True, timeout=5).strip()
    import pwd
    uid = os.geteuid()
    return f"{pwd.getpwuid(uid).pw_name} (uid {uid})"


def provision(database, action, user_id):
    actor = operator_identity()
    with sqlite3.connect(database.resolve().as_uri() + "?mode=rw", uri=True) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        connection.execute("BEGIN IMMEDIATE")
        grants = connection.execute("SELECT user_id FROM qva_host_grants").fetchall()
        if len(grants) > 1:
            raise ValueError("Conflicting grants detected. Administrative access is disabled.")
        event_id = str(uuid.uuid4())
        if action == "grant":
            if grants:
                raise ValueError("An active HOST already exists. Revoke it explicitly first.")
            if not connection.execute("SELECT 1 FROM users WHERE id = ? AND is_active = 1",
                                      (user_id,)).fetchone():
                raise ValueError("The specified active user ID does not exist.")
            connection.execute("INSERT INTO qva_host_grants(slot, user_id, grant_id) VALUES (1, ?, ?)",
                               (user_id, event_id))
        else:
            if grants != [(user_id,)]:
                raise ValueError("The specified user ID is not the active HOST.")
            connection.execute("DELETE FROM qva_host_grants WHERE slot = 1 AND user_id = ?", (user_id,))
        connection.execute("""
            INSERT INTO qva_host_grant_audit(id, user_id, action, operator, occurred_ms)
            VALUES (?, ?, ?, ?, ?)
            """, (event_id, user_id, action.upper(), actor, time.time_ns() // 1_000_000))
    print(f"{action.upper()} recorded for user ID {user_id}.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", type=Path, required=True)
    parser.add_argument("action", choices=["grant", "revoke"])
    parser.add_argument("user_id", type=int)
    args = parser.parse_args()
    if args.user_id < 1:
        parser.error("user_id must be positive")
    try:
        provision(args.database, args.action, args.user_id)
    except (ValueError, sqlite3.Error, OSError, subprocess.SubprocessError) as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
