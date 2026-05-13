# Releasing TaxonDNA

This file documents how native installers (macOS `.dmg`, Windows `.msi`, Linux `.deb`) are built and released, and what additional setup is needed to sign them in the future. Refs [#70](https://github.com/gaurav/taxondna/issues/70) and [#45](https://github.com/gaurav/taxondna/issues/45).

## How the pipeline works

The Maven build defines three OS-specific profiles in `pom.xml` — `mac-installer`, `windows-installer`, `linux-installer` — each of which:

1. Runs jpackage (via the `org.panteleyev:jpackage-maven-plugin` wrapper) against the shaded JAR for that app to produce a native installer in `target/installers/`.
2. Runs `maven-assembly-plugin` to wrap the installer + `README.md` + `COPYING` + the user manual / sample files into an OS-specific zip in `target/`.

Profiles run only on their target OS — jpackage cannot cross-compile, so the Windows MSI must be built on Windows, the macOS DMG on macOS, etc.

GitHub Actions workflow `.github/workflows/release.yaml` runs all three profiles in parallel on a `{macos-latest, windows-latest, ubuntu-latest}` matrix and (on tag push) attaches the six resulting zips to a draft GitHub Release.

## Cutting a release

1. Bump `<version>` in `pom.xml` from `X.Y-SNAPSHOT` to the release version `X.Y.0`.
2. Commit, push, and open a PR. Once merged to `main`, tag locally and push the tag:

   ```sh
   git tag v1.11.0
   git push origin v1.11.0
   ```
3. The `Release` workflow builds installers on all three OSes and creates a **draft** GitHub Release with the six zip bundles attached. Review the draft, edit the release notes, then publish.
4. Bump `<version>` back to the next `-SNAPSHOT` (e.g., `1.12-SNAPSHOT`) and commit.

## Test build without releasing

Use `workflow_dispatch` ("Run workflow" button on the Release workflow in GitHub UI) with a version like `1.0.0`. Bundles are uploaded as workflow artifacts only — no GitHub Release is created.

## Build locally (single OS at a time)

```sh
mvn -Pmac-installer     package -Dapp.version=1.11.0   # produces target/SpeciesIdentifier-1.11.0-macos.zip etc.
mvn -Pwindows-installer package -Dapp.version=1.11.0   # Windows only; requires WiX 3
mvn -Plinux-installer   package -Dapp.version=1.11.0   # Linux only; requires dpkg + fakeroot
```

`app.version` must be `MAJOR[.MINOR[.PATCH]]` with `MAJOR >= 1` — jpackage rejects `-SNAPSHOT`, and macOS specifically refuses a leading zero. The default (`1.0.0`) in `pom.xml` keeps ad-hoc local builds working; real releases always pass it explicitly.

## macOS signing — what's needed to add

The pipeline currently produces an **unsigned** `.dmg`. macOS Gatekeeper will refuse to open unsigned apps without a right-click → Open the first time, and on recent macOS versions may mark the dmg as damaged unless the user runs `xattr -d com.apple.quarantine`.

To sign and notarize properly:

1. **Apple Developer Program** ($99/year). From developer.apple.com, generate:
   - A "Developer ID Application" certificate (signs the `.app` bundle and contents).
   - Optionally a "Developer ID Installer" certificate (signs `.pkg` installers — not needed for `.dmg`).
2. **Export the certificate** as a `.p12` from Keychain Access on your Mac, with a password.
3. **Create an app-specific password** at appleid.apple.com (Account → Sign-In and Security → App-Specific Passwords). This is *not* your Apple ID password.
4. **Add GitHub repository secrets**:
   - `MACOS_CERT_P12_BASE64` — `base64 -i developer-id.p12 | pbcopy`
   - `MACOS_CERT_P12_PASSWORD` — password used at export
   - `MACOS_NOTARIZATION_APPLE_ID` — your Apple ID email
   - `MACOS_NOTARIZATION_TEAM_ID` — 10-character team ID from developer.apple.com membership page
   - `MACOS_NOTARIZATION_PASSWORD` — the app-specific password from step 3
5. **Update `.github/workflows/release.yaml`** (macOS job only):
   - Before `mvn`, import the cert into a temporary keychain:

     ```sh
     echo "$MACOS_CERT_P12_BASE64" | base64 --decode > cert.p12
     security create-keychain -p tempci build.keychain
     security default-keychain -s build.keychain
     security unlock-keychain -p tempci build.keychain
     security import cert.p12 -k build.keychain -P "$MACOS_CERT_P12_PASSWORD" -T /usr/bin/codesign
     security set-key-partition-list -S apple-tool:,apple: -s -k tempci build.keychain
     ```
   - Pass these jpackage flags via the mac-installer profile (gate them on a property so unsigned local builds still work):
     - `--mac-sign`
     - `--mac-signing-key-user-name "Developer ID Application: Gaurav Vaidya (TEAMID)"`
   - After jpackage produces the `.dmg`, notarize and staple:

     ```sh
     xcrun notarytool submit target/installers/SpeciesIdentifier-*.dmg \
       --apple-id "$MACOS_NOTARIZATION_APPLE_ID" \
       --team-id  "$MACOS_NOTARIZATION_TEAM_ID" \
       --password "$MACOS_NOTARIZATION_PASSWORD" \
       --wait
     xcrun stapler staple target/installers/SpeciesIdentifier-*.dmg
     ```
6. **Entitlements** — Swing/AWT apps generally work with the default hardened runtime. If notarization complains about JIT, create `mac-entitlements.plist` allowing `com.apple.security.cs.allow-jit` and `com.apple.security.cs.allow-unsigned-executable-memory`, then pass `--mac-entitlements path/to/mac-entitlements.plist` to jpackage.

## Windows signing — what's needed to add

Authenticode signing follows a similar pattern:

1. Obtain a code signing certificate from a CA (DigiCert, Sectigo, etc.). EV certificates avoid SmartScreen warnings entirely.
2. Add the `.pfx` and password as GitHub secrets.
3. In the Windows job, import the cert and run `signtool sign /f cert.pfx /p $env:PFX_PASSWORD /tr http://timestamp.digicert.com /td SHA256 /fd SHA256 target/installers/*.msi`.

There's no jpackage-native flag for Authenticode (unlike `--mac-sign`); the post-build `signtool` step is the standard approach.

## Raising the JVM heap after install

The launcher's `-Xmx` defaults (4G for SpeciesIdentifier, 8G for SequenceMatrix) are baked into the installer at build time. Users with very large datasets can edit the launcher config file:

- **macOS**: `/Applications/SequenceMatrix.app/Contents/app/SequenceMatrix.cfg`
- **Windows**: `C:\Program Files\SequenceMatrix\app\SequenceMatrix.cfg`
- **Linux**: `/opt/sequencematrix/lib/app/SequenceMatrix.cfg`

Look for `java-options=-Xmx8G` and raise it. Alternatively, run the shaded JAR directly: `java -Xmx32G -jar TaxonDNA-*-SequenceMatrix.jar`.

## Known limitations

- **Intel Macs**: `macos-latest` is arm64, so the `.dmg` is Apple Silicon-only. Intel Mac users should run the JAR directly or wait for a `macos-13` build to be added to the matrix.
- **Non-Debian Linux**: only `.deb` is built. Fedora/RHEL/Arch users should run the JAR directly. Adding `.rpm` later is a one-line addition (a second jpackage execution with `<type>RPM</type>`).
- **GenBankExplorer** is JAR-only — no installer, since it's still beta.
- **Icons**: no `.icns` / `.ico` files exist; installers use jpackage's generic icons. Add icons as a polish pass with the `<icon>` plugin config.

