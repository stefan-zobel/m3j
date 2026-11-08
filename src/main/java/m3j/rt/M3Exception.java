// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

/**
 * The base class of the classes of Modula-3 exceptions. An exception
 * object carries the argument of the exception and the place of the
 * RAISE; it has no Java stack trace, so raising it is cheap.
 */
public abstract class M3Exception extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** The argument of the exception, boxed; null without an argument. */
    public transient Object arg;

    /** The source file of the RAISE, as the compiler was given it. */
    public String file;

    /** The line of the RAISE, or 0. */
    public int line;

    protected M3Exception() {
        super(null, null, false, false);
    }

    /** The name of the exception as cm3 prints it, such as "Main.a". */
    public abstract String m3Name();

    /** Sets the place of the RAISE and returns this exception. */
    public final M3Exception at(String file, int line) {
        this.file = file;
        this.line = line;
        return this;
    }

    @Override
    public String getMessage() {
        return m3Name();
    }
}
