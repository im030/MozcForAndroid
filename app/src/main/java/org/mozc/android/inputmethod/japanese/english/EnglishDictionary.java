// Copyright 2026 MozcForAndroid contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.mozc.android.inputmethod.japanese.english;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * English word dictionary for word-hint (completion / typo correction) in alphabet mode.
 *
 * <p>Loads the fcitx5 spell dictionary format ({@code en_dict.fscd}, magic "FSCD0000"):
 * <pre>
 *   magic   : 8 bytes "FSCD0000"
 *   count   : uint32 little endian, number of entries
 *   entries : [uint16 LE frequency][NUL-terminated word] * count
 * </pre>
 * Words are stored in ASCII sort order. The format and the fuzzy matching algorithm are
 * ported from fcitx5 ({@code src/modules/spell/comp_spell_dict.cpp} and
 * {@code spell-custom-dict.cpp}, LGPL-2.1-or-later, CSSlayer / Yichao Yu).
 *
 * <p>This class is pure Java (no Android dependency) so it can be unit-tested on the JVM.
 * It is immutable after {@link #load(InputStream)} and safe to publish across threads.
 */
public final class EnglishDictionary {
  private static final byte[] MAGIC = {'F', 'S', 'C', 'D', '0', '0', '0', '0'};
  private static final int HEADER_SIZE = MAGIC.length + 4;
  private static final int MAX_PREFIX_COLLECT = 256;

  // Raw file content. Words are NUL-terminated ASCII strings inside this array.
  private final byte[] data;
  // Offset of each word (just after its uint16 frequency) in data, in file (sorted) order.
  private final int[] offsets;

  private EnglishDictionary(byte[] data, int[] offsets) {
    this.data = data;
    this.offsets = offsets;
  }

  /** Number of words in the dictionary. */
  public int size() {
    return offsets.length;
  }

  /**
   * Parses a FSCD stream fully into memory.
   *
   * @throws IOException on read failure or malformed data.
   */
  public static EnglishDictionary load(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 21);
    byte[] chunk = new byte[65536];
    int n;
    while ((n = in.read(chunk)) > 0) {
      out.write(chunk, 0, n);
    }
    byte[] data = out.toByteArray();
    if (data.length <= HEADER_SIZE) {
      throw new IOException("FSCD file too small: " + data.length);
    }
    for (int i = 0; i < MAGIC.length; i++) {
      if (data[i] != MAGIC[i]) {
        throw new IOException("Bad FSCD magic");
      }
    }
    int count = readLe32(data, MAGIC.length);
    if (count <= 0) {
      throw new IOException("Bad FSCD word count: " + count);
    }
    int[] offsets = new int[count];
    int pos = HEADER_SIZE;
    for (int i = 0; i < count; i++) {
      if (pos + 2 >= data.length) {
        throw new IOException("Truncated FSCD entry at word " + i);
      }
      pos += 2;  // skip uint16 frequency
      int start = pos;
      while (pos < data.length && data[pos] != 0) {
        pos++;
      }
      if (pos >= data.length || pos == start) {
        throw new IOException("Malformed FSCD word at index " + i);
      }
      offsets[i] = start;
      pos++;  // skip NUL
    }
    return new EnglishDictionary(data, offsets);
  }

  private static int readLe32(byte[] data, int off) {
    return (data[off] & 0xFF) | ((data[off + 1] & 0xFF) << 8)
        | ((data[off + 2] & 0xFF) << 16) | ((data[off + 3] & 0xFF) << 24);
  }

  private int frequencyAt(int wordOffset) {
    int f = (data[wordOffset - 2] & 0xFF) | ((data[wordOffset - 1] & 0xFF) << 8);
    return f;
  }

  private String wordAt(int wordOffset) {
    int end = wordOffset;
    while (data[end] != 0) {
      end++;
    }
    return new String(data, wordOffset, end - wordOffset, java.nio.charset.StandardCharsets.US_ASCII);
  }

  /**
   * Compares the word at {@code offset} against {@code query} for prefix-range search:
   * negative when the word sorts before the query, 0 when the word starts with the query
   * (exact match or longer), positive when it sorts after the prefix range.
   */
  private int compareWord(int offset, byte[] query) {
    for (int i = 0; i < query.length; i++) {
      int b = data[offset + i] & 0xFF;  // 0 (NUL) sorts before any char
      int q = query[i] & 0xFF;
      if (b != q) {
        return b - q;
      }
    }
    return 0;
  }

  /** First index whose word is >= query (binary search over the sorted offsets). */
  private int lowerBound(byte[] query) {
    int lo = 0;
    int hi = offsets.length;
    while (lo < hi) {
      int mid = (lo + hi) >>> 1;
      if (compareWord(offsets[mid], query) < 0) {
        lo = mid + 1;
      } else {
        hi = mid;
      }
    }
    return lo;
  }

  private static boolean isAsciiLetter(int c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  private static int toLower(int c) {
    return (c >= 'A' && c <= 'Z') ? c + ('a' - 'A') : c;
  }

  private static boolean lettersEqual(int a, int b) {
    return isAsciiLetter(a) && isAsciiLetter(b) && toLower(a) == toLower(b);
  }

  /**
   * Faithful port of fcitx5 SpellCustomDict::getDistance (ASCII-only specialization).
   * Weighted greedy edit distance: replace/insert/remove cost 3, trailing dict chars cost 1.
   * Returns -1 when the words are too different (maxdiff = len/3, maxremove = (len-2)/3).
   *
   * @param word lowercased query bytes
   * @param dictOffset offset of the candidate word in {@link #data}
   */
  private int getDistance(byte[] word, int dictOffset) {
    int wlen = word.length;
    int maxdiff = wlen / 3;
    int maxremove = (wlen - 2) / 3;
    int replace = 0;
    int insert = 0;
    int remove = 0;
    int wi = 0;
    int di = 0;
    int curW = wlen > 0 ? word[0] : 0;
    int curD = data[dictOffset] & 0xFF;
    while (true) {
      int diff = replace + insert + remove;
      if (diff > maxdiff || remove > maxremove) {
        return -1;
      }
      if (curW == 0) {
        // Query exhausted: charge remaining dict characters (including current) with END_WEIGHT.
        int tail = 0;
        if (curD != 0) {
          int p = dictOffset + di;
          while (data[p] != 0) {
            tail++;
            p++;
          }
        }
        return replace * 3 + insert * 3 + remove * 3 + tail;
      }
      int nextW = (wi + 1 < wlen) ? (word[wi + 1] & 0xFF) : 0;
      if (curD == 0) {
        // Dict exhausted: only a trailing remove error is allowed.
        if (nextW != 0) {
          return -1;
        }
        remove++;
        if (diff <= maxdiff && remove <= maxremove) {
          return replace * 3 + insert * 3 + remove * 3;
        }
        return -1;
      }
      int nextD = data[dictOffset + di + 1] & 0xFF;
      if (curW == curD || lettersEqual(curW, curD)) {
        wi++;
        di++;
        curW = nextW;
        curD = nextD;
        continue;
      }
      // Remove error: query has an extra character.
      if (nextW == curD || (nextW != 0 && lettersEqual(nextW, curD))) {
        wi++;
        di++;
        curW = nextW;
        curD = nextD;
        remove++;
        continue;
      }
      // Insert error: dict has an extra character.
      if (curW == nextD || (nextD != 0 && lettersEqual(curW, nextD))) {
        wi++;
        di++;
        curW = nextW;
        curD = nextD;
        insert++;
        continue;
      }
      // Replace error.
      if (nextW == nextD || (nextW != 0 && nextD != 0 && lettersEqual(nextW, nextD))) {
        if (nextW != 0) {
          wi++;
          di++;
          curW = nextW;
          curD = nextD;
        } else {
          curW = 0;
          curD = 0;
        }
        replace++;
        continue;
      }
      return -1;
    }
  }

  private static final class Entry {
    final String word;
    final int frequency;
    final int distance;

    Entry(String word, int frequency, int distance) {
      this.word = word;
      this.frequency = frequency;
      this.distance = distance;
    }
  }

  /**
   * Returns up to {@code limit} word hints for the given user input.
   *
   * <p>Strategy: exact match first, then prefix completions ranked by frequency, then (when
   * there is still room and the query is long enough) fuzzy matches ranked by edit distance
   * and frequency. The capitalization pattern of the input (First capital / ALL CAPS) is
   * mirrored onto the hints, like fcitx5's SpellCustomDictEn.
   */
  public List<String> hint(String input, int limit) {
    if (input == null || input.isEmpty() || limit <= 0) {
      return Collections.emptyList();
    }
    // Case pattern of the original input.
    boolean firstCapital = false;
    boolean allCapital = true;
    boolean anyLetter = false;
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (c >= 'a' && c <= 'z') {
        allCapital = false;
        anyLetter = true;
      } else if (c >= 'A' && c <= 'Z') {
        anyLetter = true;
        if (i > 0) {
          // firstCapital only when exactly the first char is upper and rest lower.
        }
      }
    }
    if (anyLetter && input.charAt(0) >= 'A' && input.charAt(0) <= 'Z') {
      firstCapital = true;
      for (int i = 1; i < input.length(); i++) {
        char c = input.charAt(i);
        if (c >= 'A' && c <= 'Z') {
          firstCapital = false;
          break;
        }
      }
    }
    if (!anyLetter) {
      allCapital = false;
    }

    byte[] query = input.toLowerCase(Locale.US).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    List<Entry> prefixMatches = new ArrayList<>();
    int start = lowerBound(query);
    for (int i = start; i < offsets.length && prefixMatches.size() < MAX_PREFIX_COLLECT; i++) {
      int off = offsets[i];
      if (compareWord(off, query) != 0) {
        break;  // past the prefix range
      }
      prefixMatches.add(new Entry(wordAt(off), frequencyAt(off), 0));
    }
    // Exact match first, then frequency descending.
    Collections.sort(prefixMatches, new Comparator<Entry>() {
      @Override
      public int compare(Entry a, Entry b) {
        boolean aExact = a.word.length() == query.length;
        boolean bExact = b.word.length() == query.length;
        if (aExact != bExact) {
          return aExact ? -1 : 1;
        }
        return b.frequency - a.frequency;
      }
    });

    List<Entry> result = new ArrayList<>(prefixMatches);
    // Fuzzy fallback only when prefix completions are scarce.
    if (result.size() < limit && query.length >= 3) {
      List<Entry> fuzzy = new ArrayList<>();
      for (int off : offsets) {
        int dist = getDistance(query, off);
        if (dist > 0) {  // dist == 0 is the exact match, already covered by prefix search
          fuzzy.add(new Entry(wordAt(off), frequencyAt(off), dist));
        }
      }
      Collections.sort(fuzzy, new Comparator<Entry>() {
        @Override
        public int compare(Entry a, Entry b) {
          if (a.distance != b.distance) {
            return a.distance - b.distance;
          }
          return b.frequency - a.frequency;
        }
      });
      for (Entry e : fuzzy) {
        if (result.size() >= limit) {
          break;
        }
        boolean dup = false;
        for (Entry existing : result) {
          if (existing.word.equals(e.word)) {
            dup = true;
            break;
          }
        }
        if (!dup) {
          result.add(e);
        }
      }
    }

    List<String> hints = new ArrayList<>(Math.min(limit, result.size()));
    for (int i = 0; i < result.size() && i < limit; i++) {
      String word = result.get(i).word;
      if (allCapital) {
        word = word.toUpperCase(Locale.US);
      } else if (firstCapital && !word.isEmpty()) {
        word = Character.toUpperCase(word.charAt(0)) + word.substring(1);
      }
      hints.add(word);
    }
    return hints;
  }
}
