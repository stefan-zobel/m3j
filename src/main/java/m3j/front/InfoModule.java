// Java port of CM3's m3-sys/m3front/src/builtinInfo/InfoModule.i3 and InfoModule.m3.
// Original: Copyright 1996-2000, Critical Mass, Inc.  All rights reserved.
// Distributed under the Critical Mass license in licenses/COPYRIGHT-CMASS.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3ID;
import m3j.middle.Target;
import m3j.util.AssertError;

public final class InfoModule {
    private InfoModule() {}

    public static Module.T M;

    // Platform_names = Target.SystemNames;
    private static final String[] OS_names = Target.OSNames;
    private static final String[] Endian_names = Target.EndianNames;

    public static void initialize() {
        Scope.T zz;
        Type.T os_type, endian /* , platform_type */;
        Value.T[] enum_ = new Value.T[1];
        String nm;

        M = Module.newDefn("Compiler", true, null);

        // WARNING: The following list must be in the same order
        //  as the actual Compiler.i3 file, otherwise the version
        //  stamps will be messed up!

        zz = Scope.push(Module.exportScope(M));

        os_type = EnumType.build(OS_names);
        Tipe.define("OS", os_type, false);

        endian = EnumType.build(Endian_names);
        Tipe.define("ENDIAN", endian, false);

        nm = M3.target.OS_name;
        if (!EnumType.lookUp(os_type, M3ID.add(M3.ids, nm), enum_)) {
            Error.txt(nm, "Unknown Compiler.OS value");
            AssertError.check(false, "InfoModule");
        }
        Constant.declare("ThisOS", Value.toExpr(enum_[0]), false);

        /*
        platform_type := EnumType.Build (Platform_names);
        Tipe.Define ("Platform", platform_type, FALSE);

        nm := Target.System_name;
        IF NOT EnumType.LookUp (platform_type, M3ID.Add (nm), enum) THEN
          Error.Txt (nm, "Unknown Compiler.Platform value");
          <*ASSERT FALSE*>
        END;
        EVAL Constant.Declare ("ThisPlatform", Value.ToExpr (enum), FALSE);
        */
        if (M3.target.endian == Target.Endian.Little) {
            nm = "LITTLE";
        } else {
            nm = "BIG";
        }
        if (!EnumType.lookUp(endian, M3ID.add(M3.ids, nm), enum_)) {
            Error.txt(nm, "Unknown Compiler.ENDIAN value");
            AssertError.check(false, "InfoModule");
        }
        Constant.declare("ThisEndian", Value.toExpr(enum_[0]), false);

        InfoThisFile.initialize();
        InfoThisPath.initialize();
        InfoThisLine.initialize();
        InfoThisException.initialize();

        Scope.pop(zz);
    }

    public static void reset() {
        InfoThisFile.reset();
        InfoThisPath.reset();
    }
}
