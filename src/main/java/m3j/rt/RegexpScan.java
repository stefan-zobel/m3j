// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

import java.util.regex.Matcher;

/**
 * The matches of a {@link Regexp} in a text, one after the other, as
 * {@link Matcher#find()} gives them: after an empty match the next search
 * starts one character later, and "\G" stands for the end of the previous
 * match. The budget of each search is that of the characters from where it
 * starts to the end.
 *
 * <p>A caller names how many matches it has seen. Asking again for a match
 * that the matcher has passed, or after a search that went over its budget,
 * repeats the searches from the start, so a match of the module Regexp can
 * be asked for its successor more than once. The methods are synchronized,
 * as matches may be shared by threads.
 */
public final class RegexpScan {

    private final Regexp re;
    private final String text;
    private final int from;
    private final Regexp.Chars chars;
    private final Matcher m;
    private int count;          // the matches found so far
    private boolean done;       // the last search failed
    private boolean broken;     // the last search went over its budget

    RegexpScan(Regexp re, String text, int from) {
        this.re = re;
        this.text = text;
        this.from = from;
        this.chars = re.chars(text);
        this.m = re.matcher(text, chars);
    }

    /**
     * The bounds of the groups of the match after the first index matches
     * (as {@link Regexp#fullMatch}), null if there is none,
     * {@link Regexp#TOO_COMPLEX} if a search went over its budget.
     */
    public synchronized int[] next(int index) {
        try {
            if (broken || index != count) {
                broken = false;
                count = 0;
                done = false;
                while (count < index) {
                    if (!step()) return null;
                }
            }
            if (done) return null;
            return step() ? Regexp.bounds(m) : null;
        } catch (Regexp.Over | StackOverflowError e) {
            broken = true;
            return Regexp.TOO_COMPLEX;
        }
    }

    private boolean step() {
        int start = count == 0 ? from : m.end();
        if (chars != null) chars.left = re.budget(text.length() - start);
        boolean found = count == 0 ? m.find(from) : m.find();
        if (found) {
            count++;
        } else {
            done = true;
        }
        return found;
    }
}
