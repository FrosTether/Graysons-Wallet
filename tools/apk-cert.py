#!/usr/bin/env python3
"""Prints the SHA-256 of the certificate an APK is signed with, written the way keytool writes it (AB:CD:...).

Android installs an update only when it's signed with the same certificate as the app already on the phone, so
the Build workflow checks this before it publishes a release. It reads the APK Signature Scheme v3 block, or the
v2 block if there's no v3 one. It doesn't check the signature itself: Android does that when it installs the app.

    python3 tools/apk-cert.py GraysonsVault.apk
    python3 tools/apk-cert.py --der cert.der GraysonsVault.apk    # also saves the certificate
"""
import hashlib
import struct
import sys

V2_ID = 0x7109871A
V3_ID = 0xF05368C0
MAGIC = b"APK Sig Block 42"


def prefixed(buf, at=0):
    """The value at `at` that starts with its uint32 length, and the offset just after it."""
    (n,) = struct.unpack_from("<I", buf, at)
    end = at + 4 + n
    if end > len(buf):
        raise ValueError("signing block is cut short")
    return buf[at + 4:end], end


def sequence(buf):
    """A run of values that each start with their uint32 length."""
    items, at = [], 0
    while at < len(buf):
        item, at = prefixed(buf, at)
        items.append(item)
    return items


def signing_block(apk):
    """The ID-value pairs of the APK Signing Block, which sits just before the zip's central directory."""
    eocd = apk.rfind(b"PK\x05\x06", max(0, len(apk) - 65557))
    if eocd < 0:
        raise ValueError("not a zip file")
    (cd,) = struct.unpack_from("<I", apk, eocd + 16)
    if cd < 32 or apk[cd - 16:cd] != MAGIC:
        raise ValueError("no APK Signing Block, so no v2 or v3 signature")
    (size,) = struct.unpack_from("<Q", apk, cd - 24)
    start = cd - size - 8
    if start < 0 or struct.unpack_from("<Q", apk, start)[0] != size:
        raise ValueError("APK Signing Block is damaged")
    pairs, at = {}, start + 8
    while at < cd - 24:
        (n,) = struct.unpack_from("<Q", apk, at)
        (pair_id,) = struct.unpack_from("<I", apk, at + 8)
        pairs[pair_id] = apk[at + 12:at + 8 + n]
        at += 8 + n
    return pairs


def first_certificate(scheme_block):
    """The first signer's certificate, in DER. v2 and v3 both start a signer's signed data with its digests,
    then its certificates."""
    signers, _ = prefixed(scheme_block)
    signed_data, _ = prefixed(sequence(signers)[0])
    _digests, at = prefixed(signed_data)
    certificates, _ = prefixed(signed_data, at)
    return sequence(certificates)[0]


def main(argv):
    der_out = None
    if len(argv) == 4 and argv[1] == "--der":
        der_out, argv = argv[2], [argv[0], argv[3]]
    if len(argv) != 2:
        sys.exit(__doc__.strip())
    with open(argv[1], "rb") as f:
        apk = f.read()
    pairs = signing_block(apk)
    block = pairs.get(V3_ID) or pairs.get(V2_ID)
    if block is None:
        raise ValueError("no v2 or v3 signature")
    cert = first_certificate(block)
    if der_out:
        with open(der_out, "wb") as f:
            f.write(cert)
    digest = hashlib.sha256(cert).hexdigest().upper()
    print(":".join(digest[k:k + 2] for k in range(0, len(digest), 2)))


if __name__ == "__main__":
    try:
        main(sys.argv)
    except (OSError, ValueError, struct.error, IndexError) as e:
        sys.exit("apk-cert: %s" % e)
