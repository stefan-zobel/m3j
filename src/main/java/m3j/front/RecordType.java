// Java port of CM3's m3-sys/m3front/src/types/RecordType.i3 and RecordType.m3.
// Original: Copyright (C) 1992, Digital Equipment Corporation.
// Distributed under the Digital license in licenses/COPYRIGHT-DEC.
// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
// under the MIT license in the file LICENSE.

package m3j.front;

import m3j.middle.M3Buf;
import m3j.middle.M3ID;
import m3j.middle.Target;
import m3j.middle.Target.CGType;

// File: RecordType.m3
// Last modified on Tue Jun 20 09:57:05 PDT 1995 by kalsow
//      modified on Thu Jun 15 12:45:03 PDT 1995 by ericv
//      modified on Tue Mar 26 02:49:22 1991 by muller

public final class RecordType {
    private RecordType() {}

    private static long MaxBitSize = Long.MAX_VALUE;

    public static final class P extends Type.T {
        public Scope.T fields;
        public long recSize = 0;
        public long align = 0;

        @Override
        public void check() {
            RecordType.check(this);
        }

        @Override
        public boolean no_straddle(long offset, boolean IsEltOrField) {
            return noStraddle(this, offset, IsEltOrField);
        }

        @Override
        public boolean isEqual(Type.T t, Type.Assumption x) {
            return equalChk(this, t, x);
        }

        // Port: the override isSubtype := TypeRep.NoSubtypes restates the
        // inherited method.

        @Override
        public void compile() {
            compiler(this);
        }

        @Override
        public long initCost(boolean zeroed) {
            return initCoster(this, zeroed);
        }

        @Override
        public void initValue(boolean zeroed) {
            genInit(this, zeroed);
        }

        @Override
        public void mapper(long offset, long size, boolean refs) {
            genMap(this, offset, size, refs);
        }

        @Override
        public void gen_desc() {
            genDesc(this);
        }

        @Override
        public void fprint(M3.FPInfo x) {
            fPrinter(this, x);
        }
    }

    // EXPORTED:
    public static Type.T parse() {
        P p = create(Scope.pushNew(false, M3ID.NoID));
        Scanner.match(Token.T.tRECORD);
        parseFieldList();
        Scanner.match(Token.T.tEND);
        Scope.popNew();

        return p;
    }

    // EXPORTED:
    public static void parseFieldList() {
        long j, n;
        long nFields = 0;
        Field.Info info = new Field.Info();
        info.offset = 0;
        while ((Scanner.cur.token == Token.T.tIDENT)) {
            n = Ident.parseList();
            info.type = null;
            if ((Scanner.cur.token == Token.T.tCOLON)) {
                Scanner.getToken(); // :
                info.type = Type.parse();
            }
            info.dfault = null;
            if ((Scanner.cur.token == Token.T.tEQUAL)) {
                Error.msg("Field default value must begin with ':=' (2.2.4).");
                Scanner.cur.token = Token.T.tASSIGN;
            }
            if ((Scanner.cur.token == Token.T.tASSIGN)) {
                Scanner.getToken(); // :=
                info.dfault = Expr.parse();
            }
            if ((info.type == null) && (info.dfault == null)) {
                Error.msg("Fields must include a type or default value (2.2.4)");
            }
            j = Ident.top - n;
            for (long i = 0; i <= n - 1; i++) {
                info.name = Ident.stack[(int) (j + i)];
                info.index = nFields;
                nFields++;
                Scope.insert(Field.new_(info));
            }
            Ident.top -= n;
            if ((Scanner.cur.token != Token.T.tSEMI)) {
                break;
            }
            Scanner.getToken(); // ;
        }
    }

    // EXPORTED:
    public static Type.T new_(Scope.T fields) {
        P p = create(fields);
        return p;
    }

    private static P reduce(Type.T t) {
        t = Type.strip(t); // StripPacked?
        if ((t == null) || (t.info.class_ != Type.Class.Record)) {
            return null;
        }
        return (P) t;
    }

    /** Port: VAR fields is {@code fields[0]}. */
    // EXPORTED:
    public static boolean split(Type.T t, Value.T[] fields) {
        P p = reduce(t);
        if ((p == null)) {
            return false;
        }
        fields[0] = Scope.toList(p.fields);
        return true;
    }

    /** Port: VAR field is {@code field[0]}. */
    // EXPORTED:
    public static boolean lookUp(Type.T t, int field, Value.T[] obj) {
        P p = reduce(t);
        if ((p == null)) {
            return false;
        }
        obj[0] = Scope.lookUp(p.fields, field, true);
        return (obj[0] != null);
    }

    //*********************************************************************

    private static P create(Scope.T fields) {
        P p = new P();
        TypeRep.init(p, Type.Class.Record);
        p.fields = fields;
        return p;
    }

    private static void check(P p) {
        Value.T[] o = new Value.T[1];
        Field.Info fieldInfo = new Field.Info();
        Type.Info typeInfo = new Type.Info();
        M3.CheckState cs = M3.OuterCheckState.copy();
        long[] min_size = new long[1];
        long hash;
        boolean[] is_solid = new boolean[1];
        long[] recSize = new long[1], align = new long[1];
        Scope.typeCheck(p.fields, cs);

        // assign the final offsets to each field
        // Port: p.recSize and p.align are VAR parameters of SizeAndAlignment.
        recSize[0] = p.recSize;
        align[0] = p.align;
        sizeAndAlignment(p.fields, p.info.lazyAligned,
                         /*OUT*/ min_size, /*OUT*/ recSize, /*OUT*/ align,
                         /*OUT*/ is_solid);
        p.recSize = recSize[0];
        p.align = align[0];

        // compute the hash value and per-field predicates
        p.info.isTraced = false;
        p.info.isEmpty = false;
        p.info.isSolid = is_solid[0];
        hash = 943 * p.recSize + p.align;
        o[0] = Scope.toList(p.fields);
        while ((o[0] != null)) {
            Field.split(o[0], fieldInfo);
            Type.checkInfo(fieldInfo.type, typeInfo);
            p.info.isTraced = p.info.isTraced || typeInfo.isTraced;
            p.info.isEmpty = p.info.isEmpty || typeInfo.isEmpty;
            hash = hash * 41 + M3ID.hash(M3.ids, fieldInfo.name);
            hash = hash * 37 + typeInfo.size;
            Field.nameAnonConstr(/*IN OUT*/ o, cs);
            o[0] = o[0].next;
        }

        p.info.hash = hash;
        p.info.size = p.recSize;
        p.info.min_size = min_size[0];
        p.info.alignment = p.align;
        p.info.mem_type = CGType.Struct;
        p.info.stk_type = CGType.Addr;
        p.info.class_ = Type.Class.Record;
    }

    /**
     * Port: VAR (OUT) minSize, recSize, recAlign and is_solid are
     * {@code minSize[0]}, {@code recSize[0]}, {@code recAlign[0]} and
     * {@code is_solid[0]}.
     */
    // EXPORTED:
    public static void sizeAndAlignment(Scope.T fields, boolean lazyAligned,
                                        long[] minSize, long[] recSize, long[] recAlign,
                                        boolean[] is_solid) {
        // Lay out the fields, assuming the record starts at the largest
        // possible alignment for the target.
        long curSize;
        Type.T fieldType;
        Type.T[] packedFieldBaseType = new Type.T[1];
        long fieldAlign;
        long[] fieldSize = new long[1];
        Value.T fieldValue;
        Field.Info fieldInfo = new Field.Info();
        Type.Info fieldTypeInfo = new Type.Info();
        boolean anyPacked = false;
        boolean raiseRecAlign = false;
        // ^Let's disable this for now.  It will cause extra padding ahead of
        //  this record.  Its only benefit is that it may allow whole-record
        //  copying to be done with, e.g., a word-by-word copy instead of
        //  memcopy.
        boolean straddlesWord = false;
        recSize[0] = 0; // total size of the record
        recAlign[0] = Target.Structure_size_boundary; // minimum record alignment
        is_solid[0] = true;

        // extract the fields and set their offsets
        fieldValue = Scope.toList(fields);
        while ((fieldValue != null)) {
            Field.split(fieldValue, fieldInfo);
            fieldType = Type.checkInfo(fieldInfo.type, fieldTypeInfo);
            is_solid[0] = is_solid[0] && fieldTypeInfo.isSolid;
            if ((fieldTypeInfo.class_ == Type.Class.Packed)) {
                PackedType.split(fieldType, fieldSize, packedFieldBaseType);
                if (!Type.straddleFreeScalars(fieldType, recSize[0], /* IsEltOrField := */ true)) {
                    Error.ID(fieldInfo.name,
                             "CM3 restriction: scalars in packed fields cannot cross "
                             + "boundaries (2.2.5).");
                    recSize[0] = roundUp(recSize[0], M3.target.Word.size);
                    straddlesWord = true;
                }
                anyPacked = true;
            } else { // Field not packed.
                fieldSize[0] = fieldTypeInfo.size;
                fieldAlign = fieldTypeInfo.alignment;
                recAlign[0] = Math.max(recAlign[0], fieldAlign);
                curSize = recSize[0];
                recSize[0] = roundUp(curSize, fieldAlign);
                is_solid[0] = is_solid[0] && (curSize == recSize[0]);
            }
            Field.setOffset(fieldValue, recSize[0]);
            recSize[0] += fieldSize[0];
            // TODO: Fix so recSize won't overflow.
            fieldValue = fieldValue.next;
        }

        if (anyPacked && !straddlesWord) {
            // Look for a smaller alignment that also avoids word straddling.
            recAlignWithPackedElements(/*IN OUT*/ recAlign, fields, lazyAligned);
        }

        minSize[0] = recSize[0];
        recSize[0] = roundUp(minSize[0], recAlign[0]);
        // ^make sure that all copy operations are an integral number of
        //  aligned transfers.
        is_solid[0] = is_solid[0] && (minSize[0] == recSize[0]);

        if (raiseRecAlign && recSize[0] > 0) {
            // find the largest possible alignment that doesn't change the size
            // of the record...
            long z;
            z = M3.target.Integer.align; // Int64 or Int32
            if ((z > recAlign[0]) && (Math.floorMod(recSize[0], z) == 0)) {
                recAlign[0] = z;
            }
            z = Target.Int32.align;
            if ((z > recAlign[0]) && (Math.floorMod(recSize[0], z) == 0)) {
                recAlign[0] = z;
            }
            z = Target.Int16.align;
            if ((z > recAlign[0]) && (Math.floorMod(recSize[0], z) == 0)) {
                recAlign[0] = z;
            }
            z = Target.Int8.align;
            if ((z > recAlign[0]) && (Math.floorMod(recSize[0], z) == 0)) {
                recAlign[0] = z;
            }
        }

        if (recSize[0] > MaxBitSize) {
            Error.msg("CM3 restriction: record or object type is too large");
        }
    }

    /**
     * Assume no straddles if aligned initial value of recAlign.
     * Look for the smallest alignment that also is straddle-free.
     * <p>Port: VAR (IN OUT) recAlign is {@code recAlign[0]}.
     */
    private static void recAlignWithPackedElements(long[] recAlign, Scope.T fields,
                                                   boolean lazyAligned) {
        long trialAlign, offset;
        for (int a = 0; a < Target.Alignments.length; a++) { // Smallest first
            trialAlign = Target.Alignments[a];
            if (trialAlign >= M3.target.Word.align) { // Already know this one works.
                recAlign[0] = M3.target.Word.align;
                return;
            }
            if (trialAlign >= recAlign[0]) {
                offset = M3.target.Word.size;
                while (true) { // Backwards through multiples of trialAlign within a word.
                    offset -= trialAlign;
                    if (!offsetIsStraddleFree(offset, fields, lazyAligned)) {
                        // trialAlign won't work.
                        break;
                    } else if (offset <= 0) { // trialAlign works.
                        recAlign[0] = Math.max(recAlign[0], trialAlign);
                        return;
                    }
                } // LOOP
            }
        } // FOR
    }

    private static boolean offsetIsStraddleFree(long recOffset, Scope.T fields,
                                                boolean lazyAligned) {
        Value.T fieldValue;
        Field.Info fieldInfo = new Field.Info();
        boolean origLazyAligned;
        fieldValue = Scope.toList(fields);
        while ((fieldValue != null)) {

            Field.split(fieldValue, fieldInfo);
            origLazyAligned = Type.isLazyAligned(fieldInfo.type);
            Type.setLazyAlignment(fieldInfo.type, lazyAligned);
            if (!Type.straddleFreeScalars(fieldInfo.type, recOffset + fieldInfo.offset,
                                          /* IsEltOrField := */ true)) {
                Type.setLazyAlignment(fieldInfo.type, origLazyAligned);
                return false;
            }
            Type.setLazyAlignment(fieldInfo.type, origLazyAligned);
            fieldValue = fieldValue.next;
        } // WHILE
        return true;
    }

    private static boolean noStraddle(P p, long offset, boolean IsEltOrField) {
        // Port: IsEltOrField is <*UNUSED*>.
        return offsetIsStraddleFree(offset, p.fields, p.info.lazyAligned);
    }

    // EXPORTED:
    public static long roundUp(long size, long alignment) {
        if ((alignment == 0)) {
            return size;
        } else {
            return Math.floorDiv(size + alignment - 1, alignment) * alignment;
        }
    }

    private static void compiler(P p) {
        Value.T fields = Scope.toList(p.fields);
        Value.T o;
        long n;
        o = fields;
        n = 0;
        while ((o != null)) {
            Field.compile((Field.T) o);
            o = o.next;
            n++;
        }
        // Port: CG.TypeUID is int.
        CG.declare_record((int) Type.globalUID(p), p.recSize, n);
        o = fields;
        while ((o != null)) {
            Field.emitDeclaration(o);
            o = o.next;
        }
    }

    private static boolean equalChk(P a, Type.T t, Type.Assumption x) {
        // Note: it is important to do the surface syntax checks before
        // checking the types of the fields.  Otherwise, we will add "a = t"
        // to the current set of assumptions and may decide that
        // other types are equal when they are not!
        P b = (P) t;
        Value.T va, vb;
        //****** too sleazy!  since it depends on type checking ...
        // IF (a.align # 0) AND (b.align # 0) THEN
        //   (* both have already been sized *)
        //   (* => make some simple sanity checks *)
        //   IF (a.recSize # b.recSize) THEN RETURN FALSE END;
        //   IF (a.align # b.align) THEN RETURN FALSE END;
        // END;
        //*******************************************************

        va = Scope.toList(a.fields);
        vb = Scope.toList(b.fields);

        // check the field names and offsets
        if (!Field.isEqualList(va, vb, x, /* types := */ false)) {
            return false;
        }

        // check the field types and default values
        if (!Field.isEqualList(va, vb, x, /* types := */ true)) {
            return false;
        }

        return true;
    }

    private static long initCoster(P p, boolean zeroed) {
        long cost;
        Value.T v;
        Field.Info field = new Field.Info();
        v = Scope.toList(p.fields);
        cost = 0;
        while ((v != null)) {
            Field.split(v, field);
            if ((field.dfault == null)) {
                cost += Type.initCost(field.type, zeroed);
            } else if ((zeroed) && Expr.isZeroes(field.dfault)) {
                // no charge
            } else {
                cost += Math.max(1, Type.initCost(field.type, false));
            }
            if ((cost < 0)) {
                return Long.MAX_VALUE;
            }
            v = v.next;
        }
        return cost;
    }

    private static void genInit(P p, boolean zeroed) {
        Field.Info field = new Field.Info();
        Value.T v = Scope.toList(p.fields);
        CG.Val ptr = CG.pop(); // capture the record's address
        while ((v != null)) {
            Field.split(v, field);
            if ((field.dfault == null)) {
                if ((Type.initCost(field.type, zeroed) > 0)) {
                    CG.push(ptr);
                    CG.boost_addr_alignment(p.align);
                    CG.add_offset(field.offset);
                    Type.initValue(field.type, zeroed);
                }
            } else if ((!zeroed) || (!Expr.isZeroes(field.dfault))) {
                AssignStmt.prepForEmit(field.type, field.dfault, /* initializing := */ true);
                CG.push(ptr);
                CG.boost_addr_alignment(p.align);
                CG.add_offset(field.offset);
                // Port: lhs_align takes its default, Target.Byte.
                AssignStmt.doEmit(field.type, field.dfault, Target.Byte,
                                  /* initializing := */ true);
            }
            v = v.next;
        }
        CG.free(ptr);
    }

    private static void genMap(P p, long offset, long size, boolean refs_only) {
        // Port: size is <*UNUSED*>.
        Value.T v = Scope.toList(p.fields);
        Field.Info field = new Field.Info();
        while ((v != null)) {
            Field.split(v, field);
            Type.genMap(field.type, offset + field.offset, -1, refs_only);
            v = v.next;
        }
    }

    private static void genDesc(P p) {
        Value.T v = Scope.toList(p.fields);
        Field.Info field = new Field.Info();
        long n = 0;
        Value.T vv = v;
        if (TipeDesc.addO(TipeDesc.Op.Record, p)) {
            // count the fields
            while ((vv != null)) {
                n++;
                vv = vv.next;
            }
            TipeDesc.addI(n);

            // and generate them
            while ((v != null)) {
                Field.split(v, field);
                Type.genDesc(field.type);
                v = v.next;
            }
        }
    }

    private static void fPrinter(P p, M3.FPInfo x) {
        Value.T v;
        long n;
        M3Buf.putText(x.buf, "RECORD");

        // count the fields and build the tag
        v = Scope.toList(p.fields);
        n = 0;
        while ((v != null)) {
            n += Value.addFPTag(v, x);
            v = v.next;
        }
        x.n_nodes = n;

        // add the field edges
        if ((n > x.nodes.length)) {
            x.others = new Type.T[(int) n];
        }
        v = Scope.toList(p.fields);
        n = 0;
        while ((v != null)) {
            n = Value.addFPEdges(v, x, n);
            v = v.next;
        }
    }
}
