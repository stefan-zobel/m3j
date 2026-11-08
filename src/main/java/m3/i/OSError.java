// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3.i;

import m3j.rt.M3Exception;

/**
 * The interface OSError of libm3: EXCEPTION E (Code), where Code is an
 * AtomList.T. The runtime's file operations raise it with NIL; FS raises it
 * with a list of one atom, the message of the system.
 */
public final class OSError {
    private OSError() {}

    public static final boolean M3$PROVIDED = true;

    /** EXCEPTION E (AtomList.T) */
    public static final class E extends M3Exception {
        private static final long serialVersionUID = 1L;

        @Override
        public String m3Name() {
            return "OSError.E";
        }
    }
}
