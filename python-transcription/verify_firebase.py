#!/usr/bin/env python3
"""
Firebase Setup Verifier for Armstrong & Getty Transcriber

Checks that your firebase-service-account.json is valid and that
the script can connect to Firestore, read, and write.

Usage:
    python3 verify_firebase.py [path/to/firebase-service-account.json]

    If no path is given, checks these locations in order:
      1. FIREBASE_CREDENTIALS_PATH env var
      2. ./firebase-service-account.json (next to this script)
      3. ~/ag_transcribe/firebase-service-account.json
"""

import json
import os
import sys
import time

EXPECTED_PROJECT_ID = "armstrong-and-getty-app-d67a4"

# ── Helpers ───────────────────────────────────────────────────────────────────

def ok(msg):
    print(f"  ✓ {msg}")

def fail(msg):
    print(f"  ✗ {msg}")

def warn(msg):
    print(f"  ⚠ {msg}")

def info(msg):
    print(f"    {msg}")

def section(title):
    print(f"\n{'─'*50}")
    print(f"  {title}")
    print(f"{'─'*50}")


# ── Step 1: Find the credentials file ────────────────────────────────────────

def find_credentials_file(explicit_path=None):
    section("1. LOCATING CREDENTIALS FILE")

    candidates = []

    if explicit_path:
        candidates.append(("command line argument", explicit_path))

    env_path = os.environ.get("FIREBASE_CREDENTIALS_PATH")
    if env_path:
        candidates.append(("FIREBASE_CREDENTIALS_PATH env var", env_path))

    script_dir = os.path.dirname(os.path.abspath(__file__))
    candidates.append(("same directory as this script", os.path.join(script_dir, "firebase-service-account.json")))
    candidates.append(("~/ag_transcribe/", os.path.expanduser("~/ag_transcribe/firebase-service-account.json")))

    for source, path in candidates:
        abs_path = os.path.abspath(os.path.expanduser(path))
        if os.path.isfile(abs_path):
            ok(f"Found via {source}")
            info(f"Path: {abs_path}")
            info(f"Size: {os.path.getsize(abs_path):,} bytes")
            return abs_path
        else:
            info(f"Not found at: {abs_path} ({source})")

    fail("Could not find firebase-service-account.json anywhere")
    print()
    print("  To fix this:")
    print("    1. Go to Firebase Console → Project Settings → Service Accounts")
    print("    2. Click 'Generate New Private Key'")
    print("    3. Save the file to ~/ag_transcribe/firebase-service-account.json")
    print()
    print("  Or pass the path explicitly:")
    print("    python3 verify_firebase.py /path/to/your/key.json")
    return None


# ── Step 2: Validate JSON structure ──────────────────────────────────────────

REQUIRED_FIELDS = [
    "type",
    "project_id",
    "private_key_id",
    "private_key",
    "client_email",
    "client_id",
    "auth_uri",
    "token_uri",
]

def validate_json(path):
    section("2. VALIDATING JSON STRUCTURE")

    try:
        with open(path, "r") as f:
            data = json.load(f)
        ok("File is valid JSON")
    except json.JSONDecodeError as e:
        fail(f"File is not valid JSON: {e}")
        return None

    # Check required fields
    missing = [f for f in REQUIRED_FIELDS if f not in data]
    if missing:
        fail(f"Missing required fields: {', '.join(missing)}")
        info("This doesn't look like a Firebase service account key file.")
        info("Make sure you downloaded it from Firebase Console → Service Accounts → Generate New Private Key")
        return None
    ok("All required fields present")

    # Check type
    if data.get("type") != "service_account":
        fail(f"'type' field is '{data.get('type')}', expected 'service_account'")
        info("This might be a google-services.json (client config) instead of a service account key.")
        return None
    ok("Type is 'service_account'")

    # Check project ID
    project_id = data.get("project_id", "")
    if project_id == EXPECTED_PROJECT_ID:
        ok(f"Project ID matches: {project_id}")
    else:
        warn(f"Project ID is '{project_id}', expected '{EXPECTED_PROJECT_ID}'")
        info("This key might be for a different Firebase project.")

    # Check private key format
    pk = data.get("private_key", "")
    if pk.startswith("-----BEGIN RSA PRIVATE KEY-----") or pk.startswith("-----BEGIN PRIVATE KEY-----"):
        ok("Private key format looks correct")
    else:
        fail("Private key doesn't start with expected PEM header")
        info("The key file may be corrupted or truncated.")
        return None

    # Print summary
    info(f"Client email: {data.get('client_email', '?')}")
    info(f"Key ID: {data.get('private_key_id', '?')[:12]}...")

    return data


# ── Step 3: Check firebase-admin is installed ────────────────────────────────

def check_dependencies():
    section("3. CHECKING DEPENDENCIES")

    try:
        import firebase_admin
        ok(f"firebase-admin installed (version {firebase_admin.__version__})")
    except ImportError:
        fail("firebase-admin is not installed")
        info("Run: pip3 install firebase-admin")
        return False

    try:
        from firebase_admin import credentials, firestore
        ok("credentials and firestore modules available")
    except ImportError as e:
        fail(f"Could not import submodules: {e}")
        return False

    try:
        from google.cloud import firestore as gc_firestore
        ok("google-cloud-firestore available")
    except ImportError:
        warn("google-cloud-firestore not found (may be bundled with firebase-admin)")

    return True


# ── Step 4: Test Firebase authentication ─────────────────────────────────────

def test_authentication(path):
    section("4. TESTING FIREBASE AUTHENTICATION")

    import firebase_admin
    from firebase_admin import credentials

    # Clean up any existing app (in case of re-runs)
    try:
        existing = firebase_admin.get_app()
        firebase_admin.delete_app(existing)
    except ValueError:
        pass

    try:
        cred = credentials.Certificate(path)
        ok("Service account credentials loaded")
    except Exception as e:
        fail(f"Failed to load credentials: {e}")
        return False

    try:
        app = firebase_admin.initialize_app(cred)
        ok(f"Firebase app initialized (project: {app.project_id})")
    except Exception as e:
        fail(f"Failed to initialize Firebase app: {e}")
        return False

    return True


# ── Step 5: Test Firestore connectivity ──────────────────────────────────────

def test_firestore():
    section("5. TESTING FIRESTORE CONNECTION")

    from firebase_admin import firestore

    try:
        db = firestore.client()
        ok("Firestore client created")
    except Exception as e:
        fail(f"Failed to create Firestore client: {e}")
        return False

    # Test read
    test_doc_id = "verify-setup-test"
    try:
        doc_ref = db.collection("transcripts").document(test_doc_id)
        doc = doc_ref.get()
        ok(f"Firestore READ works (test doc exists: {doc.exists})")
    except Exception as e:
        fail(f"Firestore READ failed: {e}")
        info("Check your Firestore security rules or network connection.")
        return False

    # Test write
    test_data = {
        "test": True,
        "timestamp": firestore.SERVER_TIMESTAMP,
        "message": "Setup verification test"
    }
    try:
        doc_ref = db.collection("transcripts").document(test_doc_id)
        doc_ref.set(test_data)
        ok("Firestore WRITE works")
    except Exception as e:
        fail(f"Firestore WRITE failed: {e}")
        info("The service account may not have write permissions.")
        return False

    # Test write to the actual path the transcriber uses
    try:
        seg_ref = (
            db.collection("transcripts")
            .document(test_doc_id)
            .collection("segments")
            .document("0")
        )
        seg_ref.set({
            "srtContent": "1\n00:00:00,000 --> 00:00:01,000\nTest\n\n",
            "segmentIndex": 0,
            "title": "Verification test",
            "uploadedAt": firestore.SERVER_TIMESTAMP,
        })
        ok("Firestore WRITE to transcripts/{date}/segments/{id} works")
    except Exception as e:
        fail(f"Firestore subcollection WRITE failed: {e}")
        return False

    # Test read back
    try:
        seg_doc = seg_ref.get()
        if seg_doc.exists and seg_doc.get("srtContent"):
            ok("Firestore READ-BACK of written data works")
        else:
            fail("Written data could not be read back")
            return False
    except Exception as e:
        fail(f"Firestore READ-BACK failed: {e}")
        return False

    # Clean up test data
    try:
        seg_ref.delete()
        doc_ref.delete()
        ok("Test data cleaned up")
    except Exception as e:
        warn(f"Could not clean up test data: {e}")
        info("You can manually delete transcripts/verify-setup-test in the Firebase Console.")

    return True


# ── Step 6: Check Firestore rules allow public reads ─────────────────────────

def check_public_read_hint():
    section("6. REMINDER: FIRESTORE SECURITY RULES")

    print()
    print("  The Android app reads transcripts WITHOUT authentication.")
    print("  Make sure your Firestore rules allow public reads on the")
    print("  transcripts collection. In Firebase Console → Firestore → Rules:")
    print()
    print("    rules_version = '2';")
    print("    service cloud.firestore {")
    print("      match /databases/{database}/documents {")
    print("        match /transcripts/{date}/segments/{segId} {")
    print("          allow read: if true;")
    print("          allow write: if false;")
    print("        }")
    print("        // ... your existing user rules ...")
    print("      }")
    print("    }")
    print()
    print("  The Python script uses the Admin SDK which bypasses rules,")
    print("  so writes will always work regardless of rules.")


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    print("╔══════════════════════════════════════════════════════════╗")
    print("║       Firebase Setup Verifier for AG Transcriber        ║")
    print("╚══════════════════════════════════════════════════════════╝")

    explicit_path = sys.argv[1] if len(sys.argv) > 1 else None

    # Step 1: Find file
    path = find_credentials_file(explicit_path)
    if not path:
        sys.exit(1)

    # Step 2: Validate JSON
    data = validate_json(path)
    if not data:
        sys.exit(1)

    # Step 3: Check dependencies
    if not check_dependencies():
        sys.exit(1)

    # Step 4: Authenticate
    if not test_authentication(path):
        sys.exit(1)

    # Step 5: Test Firestore
    firestore_ok = test_firestore()

    # Step 6: Rules reminder
    check_public_read_hint()

    # Final summary
    print(f"\n{'='*50}")
    if firestore_ok:
        print("  ✓ ALL CHECKS PASSED — your setup is ready!")
        print(f"    Credentials: {path}")
        print(f"    Project: {data.get('project_id')}")
    else:
        print("  ✗ SOME CHECKS FAILED — see above for details")
    print(f"{'='*50}\n")

    sys.exit(0 if firestore_ok else 1)


if __name__ == "__main__":
    main()