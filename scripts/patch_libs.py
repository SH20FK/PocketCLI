import os
import io
import struct
import tarfile
import urllib.request

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JNI_LIBS_DIR = os.path.join(BASE_DIR, "app", "src", "main", "jniLibs")

PACKAGES = {
    "arm64-v8a": {
        "talloc": "https://packages.termux.dev/apt/termux-main/pool/main/libt/libtalloc/libtalloc_2.5.0_aarch64.deb",
        "shmem": "https://packages.termux.dev/apt/termux-main/pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_aarch64.deb"
    },
    "x86_64": {
        "talloc": "https://packages.termux.dev/apt/termux-main/pool/main/libt/libtalloc/libtalloc_2.5.0_x86_64.deb",
        "shmem": "https://packages.termux.dev/apt/termux-main/pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_x86_64.deb"
    }
}

def extract_lib_from_deb(deb_bytes, target_filename):
    assert deb_bytes[:8] == b"!<arch>\n", "Invalid deb file"
    pos = 8
    while pos < len(deb_bytes):
        hdr = deb_bytes[pos:pos+60]
        if len(hdr) < 60:
            break
        name = hdr[:16].strip().decode("latin1")
        size = int(hdr[48:58].strip())
        content = deb_bytes[pos+60 : pos+60+size]
        pos += 60 + size + (size % 2)
        if name.startswith("data.tar"):
            tf = tarfile.open(fileobj=io.BytesIO(content))
            for m in tf.getmembers():
                if m.name.endswith("/" + target_filename):
                    f = tf.extractfile(m)
                    if f:
                        return f.read()
    raise FileNotFoundError(f"Could not find {target_filename} in deb")

def patch_bytes(data):
    old_runpath = b"/data/data/com.termux/files/usr/lib\x00"
    new_runpath = b"$ORIGIN\x00" + b"\x00" * (len(old_runpath) - len(b"$ORIGIN\x00"))
    assert len(old_runpath) == len(new_runpath)

    old_talloc = b"libtalloc.so.2\x00"
    new_talloc = b"libtalloc.so\x00\x00\x00"
    assert len(old_talloc) == len(new_talloc)

    d = data.replace(old_runpath, new_runpath)
    d = d.replace(old_talloc, new_talloc)
    assert len(d) == len(data), f"Length changed from {len(data)} to {len(d)}"
    return d

def get_dynamic_tags(data):
    is_64 = data[4] == 2
    shoff = struct.unpack("<Q" if is_64 else "<I", data[40:48] if is_64 else data[32:36])[0]
    e_shentsize = struct.unpack("<H", data[58:60] if is_64 else data[46:48])[0]
    e_shnum = struct.unpack("<H", data[60:62] if is_64 else data[48:50])[0]
    e_shstrndx = struct.unpack("<H", data[62:64] if is_64 else data[50:52])[0]
    shstr_hdr = data[shoff + e_shstrndx * e_shentsize : shoff + (e_shstrndx + 1) * e_shentsize]
    shstr_offset = struct.unpack("<Q" if is_64 else "<I", shstr_hdr[24:32] if is_64 else shstr_hdr[16:20])[0]
    dynstr_offset = None
    dynamic_offset = None
    dynamic_size = None
    for i in range(e_shnum):
        hdr = data[shoff + i * e_shentsize : shoff + (i + 1) * e_shentsize]
        sh_name = struct.unpack("<I", hdr[:4])[0]
        name = data[shstr_offset + sh_name:].split(b"\x00")[0].decode()
        offset = struct.unpack("<Q" if is_64 else "<I", hdr[24:32] if is_64 else hdr[16:20])[0]
        size = struct.unpack("<Q" if is_64 else "<I", hdr[32:40] if is_64 else hdr[20:24])[0]
        if name == ".dynstr": dynstr_offset = offset
        elif name == ".dynamic": dynamic_offset, dynamic_size = offset, size
    tags = []
    if dynamic_offset and dynstr_offset:
        ent_size = 16 if is_64 else 8
        tag_names = {1: "NEEDED", 14: "SONAME", 15: "RPATH", 29: "RUNPATH"}
        for i in range(0, dynamic_size, ent_size):
            d_tag = struct.unpack("<q" if is_64 else "<i", data[dynamic_offset + i : dynamic_offset + i + (8 if is_64 else 4)])[0]
            d_val = struct.unpack("<Q" if is_64 else "<I", data[dynamic_offset + i + (8 if is_64 else 4) : dynamic_offset + i + ent_size])[0]
            if d_tag in tag_names:
                tags.append((tag_names[d_tag], data[dynstr_offset + d_val:].split(b"\x00")[0].decode()))
    return tags

def download_deb(url):
    print(f"Downloading {url}...")
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req) as resp:
        return resp.read()

def main():
    for arch, urls in PACKAGES.items():
        print(f"\n================ Processing {arch} ================")
        arch_dir = os.path.join(JNI_LIBS_DIR, arch)
        os.makedirs(arch_dir, exist_ok=True)

        # 1. Patch existing libproot.so
        proot_path = os.path.join(arch_dir, "libproot.so")
        with open(proot_path, "rb") as f:
            proot_orig = f.read()
        proot_patched = patch_bytes(proot_orig)
        with open(proot_path, "wb") as f:
            f.write(proot_patched)
        print(f"Patched libproot.so ({len(proot_patched)} bytes): {get_dynamic_tags(proot_patched)}")

        # 2. Extract and patch libtalloc.so
        talloc_deb = download_deb(urls["talloc"])
        talloc_bytes = extract_lib_from_deb(talloc_deb, "libtalloc.so")
        talloc_patched = patch_bytes(talloc_bytes)
        talloc_path = os.path.join(arch_dir, "libtalloc.so")
        with open(talloc_path, "wb") as f:
            f.write(talloc_patched)
        print(f"Installed libtalloc.so ({len(talloc_patched)} bytes): {get_dynamic_tags(talloc_patched)}")

        # 3. Extract and patch libandroid-shmem.so
        shmem_deb = download_deb(urls["shmem"])
        shmem_bytes = extract_lib_from_deb(shmem_deb, "libandroid-shmem.so")
        shmem_patched = patch_bytes(shmem_bytes)
        shmem_path = os.path.join(arch_dir, "libandroid-shmem.so")
        with open(shmem_path, "wb") as f:
            f.write(shmem_patched)
        print(f"Installed libandroid-shmem.so ({len(shmem_patched)} bytes): {get_dynamic_tags(shmem_patched)}")

    print("\n[SUCCESS] All jniLibs patched and installed successfully!")

if __name__ == "__main__":
    main()
