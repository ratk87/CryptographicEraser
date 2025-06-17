# CryptographicEraser

CryptographicEraser is a lightweight Android application designed to securely delete files by encrypting them in place before removal, and optionally overwriting free space with cryptographically secure random data. Developed as part of a Bachelor’s thesis in Computer Science at FernUniversität in Hagen, this tool ensures that deleted data cannot be recovered—even on modern flash media where traditional shredding techniques are less effective.

---

## Motivation

Mobile devices increasingly store sensitive personal and business data—from photos and documents to authentication tokens and private messages. Simply deleting a file does not guarantee that its content can’t be recovered: on flash-based storage (e.g., internal eMMC or removable SD cards), wear-leveling and block remapping can leave remnants of “deleted” data intact.

To mitigate this risk, CryptographicEraser:

1. **Encrypts files in place** using AES (GCM for smaller files, CTR for larger) with a strong password-based key derivation (PBKDF2-HMAC-SHA256).  
2. **Deletes the encrypted file**, leaving uniformly random ciphertext on disk.  
3. **Optionally wipes free space** by writing and deleting dummy files filled with random data—twice—ensuring that any leftover blocks are overwritten.

This approach leverages cryptographic irrecoverability (ciphertext indistinguishability) as the first defense, followed by a free-space wipe as a second layer, providing a high degree of confidence that sensitive content cannot be reconstructed.

---

## Key Features

- **In-Place Encryption & Deletion**  
  Files are read into memory, encrypted with AES, then overwritten at the original location—no temporary `.enc` files remain.

- **Adaptive Cipher Selection**  
  - **AES-GCM** for files up to 20 MiB (offers authenticated encryption).  
  - **AES-CTR** for larger files (stream cipher with minimal memory overhead).

- **Password-Based Key Derivation**  
  Uses PBKDF2 with HMAC-SHA256 and 100 000 iterations to derive a 256-bit AES key from the user’s passphrase, resisting brute-force attacks.

- **Optional Free-Space Wipe**  
  Overwrites all available free space in the app’s sandbox directory with 1 MiB random-data files (twice), then deletes them.

- **Minimal Permissions**  
  Only requires full file-access permission (`MANAGE_EXTERNAL_STORAGE` on Android 11+, or `WRITE_EXTERNAL_STORAGE` on older OS versions) for operation within the app’s own sandbox.

- **Lightweight, Open-Source**  
  No background services. All code runs in a single Activity + Fragment architecture. Easily auditable and modifiable.

---

## Usage

1. **Install** the APK on your Android device.  
2. **Grant** the storage permission when prompted.  
3. **Select “Shred File”**, choose one or more files within the app’s sandbox view, and enter your passphrase.  
4. The app will **encrypt & delete** each file, then ask whether you’d like to wipe free space.  
5. If you agree, the app will **overwrite free space** with random data, then clean up.

---

## Architecture & Implementation

- **MainActivity**  
  - Displays storage statistics.  
  - Launches `FileExplorer` (Fragment) for file selection.  
  - Orchestrates the shred-and-wipe workflow via `CryptoEraseController`.

- **CryptoEraseController**  
  - Prompts for passphrase.  
  - Calls `CryptoUtils.encryptFileInPlace()` for each selected file.  
  - Deletes the encrypted file.  
  - Optionally invokes `WipeUtils` to overwrite free space.

- **CryptoUtils**  
  - Implements in-place encryption without temporary files.  
  - Logs key events (password length, file sizes, salt/IV values) to Logcat for debugging.

- **WipeUtils**  
  - Provides both silent and feedback-driven free-space overwriting routines.  
  - Writes 1 MiB dummy files until the volume is full (caught via exception), repeating twice.

- **FileExplorer**  
  - Simple `RecyclerView`-based file browser rooted at `filesDir`.  
  - Allows directory navigation and per-file “CryptoShred” buttons.

---

## Bachelor Thesis Context

CryptographicEraser was developed as part of a Bachelor’s thesis in Computer Science at FernUniversität in Hagen. The thesis investigates:

- Modern challenges in data sanitization on flash-based storage, focusing on mobile devices.  
- Comparative security of encryption-based shredding vs. overwriting strategies.  
- Usability considerations for mobile secure-delete tools.

The full thesis document, including design rationale, and security analysis, is available (in German) upon request.

---

## License

This project is released under the MIT License. Contributions and improvements are welcome!

---

**FernUniversität in Hagen** | Bachelor’s Thesis in Computer Science  
CryptographicEraser © 2025 by Fabian Kozlowski  
