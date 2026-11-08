// Java port of CM3's m3-sys/m3front/src/misc/M3Compiler.i3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.fingerprint.Fingerprint;
import m3j.middle.*;
import m3j.middle.Target.CGType;

public final class M3Compiler {
    private M3Compiler() {}

    // Port: TypeID = INTEGER (a compiler generated type id) is long.

    public static final class SourceFile {
        public String name;
        public java.io.InputStream contents;

        public SourceFile() {}

        public M3Compiler.SourceFile copy() {
            M3Compiler.SourceFile r = new SourceFile();
            r.name = name;
            r.contents = contents;
            return r;
        }
    }

    public static final class ImplList {
        public int impl;
        public ImplList next;
    }

    public abstract static class Environment {
        public abstract void report_error(String file, long line, String msg);

        public abstract M3Compiler.SourceFile find_source(int unit, boolean interface_, boolean generic);

        public abstract void note_unit(int name, boolean interface_);

        public abstract void note_comment(String msg);

        public abstract void note_interface_use(int name, boolean imported);

        public abstract void note_generic_use(int name);

        public abstract void note_version_stamp(int unit, int symbol, Fingerprint.T vs,
                                                boolean imported, boolean implemented);

        public abstract void note_opaque(long type, long super_type, int Name);

        public abstract void note_revelation(int unit, boolean interface_, long lhs, long rhs,
                                             boolean full, boolean imported);

        public abstract void note_opaque_magic(long type, long super_type, long data_size,
                                               long data_align, long method_size);

        /**
         * Port: the VAR (OUT) parameters are one-element arrays.
         */
        public abstract boolean find_opaque_magic(long type, long[] super_type, long[] data_size,
                                                  long[] data_align, long[] method_size);

        public abstract void note_ast(int unit, Object ast);

        public abstract Object find_ast(int unit);

        public abstract void note_type(long type, boolean imported);

        public abstract M3CG.T init_code_generator();

        public abstract void note_webinfo(String t);

        public abstract ImplList get_implementations(int interface_);
    }
}
