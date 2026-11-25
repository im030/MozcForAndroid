# mozc JNI lib build

The app bundles the upstream Mozc JNI shared library (`libmozc.so`). It is built
from the `mozc` git submodule (upstream tag 2.26.4660.102, ABI arm64-v8a) and does
not need to be checked in — the Gradle build regenerates it.

## Where things live

| Piece | Path |
|-------|------|
| Submodule (upstream mozc) | `mozc/` @ `063c41f1d7c1a877f44c1f8caad6be1897350336` |
| JNI glue source | `mozc/src/android/jni/mozcjni.cc` |
| Gradle integration | `app/build.gradle` (`buildMozcLib` / `copyMozcLib` tasks) |
| JNI wrapper (Java) | `app/src/main/java/org/mozc/android/inputmethod/japanese/session/MozcJNI.java` |
| Packaged lib | `app/src/main/jniLibs/arm64-v8a/libmozc.so` |
| Built artifact (untracked) | `mozc/src/bazel-bin/android/jni/libmozc_lib.so` |
| Bazel output cache | `.mozc-bazel-cache/` (gitignored) |

## How to build

Gradle handles this automatically: `preDebugBuild` / `preReleaseBuild`
depend on `copyMozcLib`, which runs `buildMozcLib` and copies the result into
`jniLibs/arm64-v8a/libmozc.so`. Running the normal build is enough:

```bash
./gradlew assembleDebug
```

Requirements (first time only):
- `bazelisk` on `PATH` (a bazelisk binary — the build uses `USE_BAZEL_VERSION=5.4.0`)
- `ANDROID_SDK_HOME` (or `ANDROID_HOME`, or `sdk.dir` in `local.properties`)
- `ANDROID_NDK_HOME` (or `$ANDROID_SDK_HOME/ndk/21.4.7075529`)

The NDK version is pinned to **21.4.7075529** because it is the last NDK whose
layout (`platforms/`) is understood by the pinned bazel version.

Manual command (equivalent):

```bash
cd mozc/src && \
  ANDROID_SDK_HOME=$ANDROID_SDK_HOME \
  ANDROID_NDK_HOME=$ANDROID_NDK_HOME \
  USE_BAZEL_VERSION=5.4.0 \
  bazelisk --output_user_root=/tmp/mozc-bazel \
    build --config=oss_android --cpu=arm64-v8a --jobs=4 \
    //android/jni:mozc_lib
```

## JNI package name

This app uses `org.mozc.android.inputmethod.japanese` as its Java package and
`applicationId`. Upstream exports JNI methods with the
`com.google.android.inputmethod.japanese` prefix, which would not link. The
single upstream JNI export name is fixed in `mozc/src/android/jni/mozcjni.cc`
(`Java_org_mozc_..._MozcJNI_initialize`). That is a submodule patch; the rest of
the app code was left untouched.

Upstream exports **only** `jboolean native initialize()` (and registers
`evalCommand` / `onPostLoad` / `getDataVersion` from inside it). There is no
`JNI_OnLoad`. `MozcJNI.initialize()` is therefore called from `MozcJNI.load()`
right after `System.loadLibrary("mozc")`. `getDataVersion` is registered but
never called by the Java side yet; `evalCommand` is invoked through
`SessionHandler` via `LocalSessionHandler`.

`app/src/main/jniLibs/arm64-v8a/libmozc.so.TOC` is a stale artifact from the old
checked-in prebuilt library; nothing reads it.

## Gotchas

- `--config=oss_android` defaults to `--cpu=armeabi-v7a`; we override it with
  `--cpu=arm64-v8a` (the app only ships arm64).
- bazel is pinned to 5.4.0 via `USE_BAZEL_VERSION=5.4.0` in the Gradle task.
  bazel 7+/bazelisk-default fails on `rules_apple 0.32.0` (see
  `mozc/src/third_party`) and on the `WORKSPACE.bazel`-only layout.
- The build is heavy (~3 min cold, thousands of targets). Do not raise `--jobs`.
- `.gitmodules` in this repo points at `https://github.com/google/mozc.git`.
- The bazel-built `libmozc.so` is self-contained: the `mozc_lib` target is a
  `cc_binary` with `linkshared=1`, so the mozc library closure is linked in
  statically and only `libc.so`/`liblog.so` come from the NDK. `copyMozcLib`
  also copies the NDK's `libc++_shared.so` into `jniLibs/arm64-v8a/`; it is
  not required for `System.loadLibrary("mozc")` but is harmless to package.
