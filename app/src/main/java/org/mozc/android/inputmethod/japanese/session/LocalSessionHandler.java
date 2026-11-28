// Copyright 2010-2018, Google Inc.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
//     * Neither the name of Google Inc. nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package org.mozc.android.inputmethod.japanese.session;

import org.mozc.android.inputmethod.japanese.MozcLog;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Command;
import com.google.common.base.Preconditions;
import com.google.protobuf.InvalidProtocolBufferException;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Concrete SessionHandler. Calls JNI.
 *
 */
class LocalSessionHandler implements SessionHandler {

  private static final String USER_PROFILE_DIRECTORY_NAME = ".mozc";

  @Override
  public void initialize(Context context) {
    /* try */{
      ApplicationInfo info = Preconditions.checkNotNull(context).getApplicationInfo();

      // Ensure the user profile directory exists.
      File userProfileDirectory = new File(info.dataDir, USER_PROFILE_DIRECTORY_NAME);
      if (!userProfileDirectory.exists()) {
        // No profile directory is found. Create the one.
        if (!userProfileDirectory.mkdirs()) {
          // Failed to create a directory. The mozc conversion engine will be able to run
          // even in this case, but no persistent data (e.g. user history, user dictionary)
          // will be stored, so some fuctions using them won't work well.
          MozcLog.e(
              "Failed to create user profile directory: " + userProfileDirectory.getAbsolutePath());
        }
      }

      // Get Java package's version name, to check the version consistency with libmozc.so
      // Note that obtained version name is suffixed by android architecture (e.g., -arm).
//      String versionName =
//          context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
//      Matcher matcher = Pattern.compile("^(\\d+\\.\\d+\\.\\d+\\.\\d+)-\\w+$").matcher(versionName);
//      if (!matcher.matches()) {
//        throw new RuntimeException("Invalid version name: " + versionName);
//      }

      // Extract the bundled mozc.data data file for the conversion engine.
      // A marker file records the version name and byte size of the copy we made. It lets
      // us notice when the bundled asset has changed (e.g. after an app update ships an
      // updated dictionary), instead of reusing a stale copy forever.
      File dataFile = new File(userProfileDirectory, "mozc.data");
      File markerFile = new File(userProfileDirectory, "mozc.data.marker");
      try {
        String versionName = context.getPackageManager()
            .getPackageInfo(context.getPackageName(), 0).versionName;
        String marker = null;
        if (markerFile.exists() && markerFile.length() < 256) {
          marker = new String(Files.readAllBytes(markerFile.toPath()),
              StandardCharsets.UTF_8);
        }
        boolean upToDate = false;
        if (dataFile.exists() && marker != null && dataFile.length() > 0) {
          int separator = marker.indexOf('|');
          if (separator > 0
              && marker.substring(0, separator).equals(versionName)) {
            try {
              long storedSize = Long.parseLong(marker.substring(separator + 1));
              upToDate = storedSize == dataFile.length();
            } catch (NumberFormatException ignored) {}
          }
        }
        if (!upToDate) {
          // Copy to a temp file and swap it in, so a failed copy never damages the
          // existing mozc.data. A marker is written after the swap so that the
          // refresh is detected on the next launch.
          MozcLog.i("copying bundled mozc.data -> " + dataFile.getAbsolutePath());
          File tempFile = new File(userProfileDirectory, ".mozc.data.tmp");
          boolean copied = false;
          try {
            try (InputStream asset = context.getAssets().open("mozc.data");
                FileOutputStream out = new FileOutputStream(tempFile)) {
              byte[] buffer = new byte[64 * 1024];
              int read;
              while ((read = asset.read(buffer)) > 0) {
                out.write(buffer, 0, read);
              }
            }
            copied = tempFile.renameTo(dataFile);
          } catch (Exception e) {
            MozcLog.e("Failed to copy bundled mozc.data: " + e.toString());
          } finally {
            tempFile.delete();
          }
          if (copied) {
            try {
              Files.write(markerFile.toPath(),
                  (versionName + "|" + dataFile.length())
                      .getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {}
          } else if (!dataFile.exists()) {
            dataFile = null;
          }
        }
      } catch (Exception e) {
        MozcLog.e("Version check failed; falling back to existing mozc.data: " + e);
      }

      // Load the shared object.
      final String dataFilePath = dataFile != null ? dataFile.getAbsolutePath() : null;
      MozcJNI.load(userProfileDirectory.getAbsolutePath(), dataFilePath, "" /*matcher.group(1)*/);
    }
//    catch (NameNotFoundException e) {
//      throw new RuntimeException(e);
//    }
  }

  @Override
  public Command evalCommand(Command command) {
    byte[] inBytes = Preconditions.checkNotNull(command).toByteArray();
    byte[] outBytes = null;
    outBytes = MozcJNI.evalCommand(inBytes);
    try {
      return Command.parseFrom(outBytes);
    } catch (InvalidProtocolBufferException e) {
      MozcLog.w("InvalidProtocolBufferException is thrown."
          + "We can do nothing so just return default instance.");
      MozcLog.w(e.toString());
      return Command.getDefaultInstance();
    }
  }
}
