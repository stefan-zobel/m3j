INTERFACE Test_T_v1;

IMPORT Test, Rd, Wr, StubLib, Thread, NetObj;
TYPE Surrogate_Test_T = Test.T OBJECT
      OVERRIDES
        add := Surrogate_add;
        echo := Surrogate_echo;
        rec := Surrogate_rec;
        list := Surrogate_list;
        sum := Surrogate_sum;
        arr := Surrogate_arr;
        chars := Surrogate_chars;
        texts := Surrogate_texts;
        fail := Surrogate_fail;
        callback := Surrogate_callback;
        other := Surrogate_other;
        reader := Surrogate_reader;
        writer := Surrogate_writer;
        quit := Surrogate_quit;
      END;

PROCEDURE Surrogate_add(self: Test.T; a: INTEGER; b: INTEGER): INTEGER
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_echo(self: Test.T; t: TEXT): TEXT RAISES {NetObj.Error,
    Thread.Alerted};

PROCEDURE Surrogate_rec(
    self: Test.T;
    READONLY r: Test.Rec;
    VAR out: Test.Rec) RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_list(self: Test.T; n: INTEGER): Test.List
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_sum(self: Test.T; l: Test.List): INTEGER
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_arr(self: Test.T; VAR a: ARRAY  OF INTEGER)
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_chars(
    self: Test.T;
    READONLY a: ARRAY  OF CHAR;
    VAR w: ARRAY  OF WIDECHAR): TEXT RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_texts(self: Test.T; READONLY a: ARRAY  OF TEXT): TEXT
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_fail(self: Test.T; code: INTEGER) RAISES {NetObj.Error,
    Thread.Alerted, Test.Oops};

PROCEDURE Surrogate_callback(self: Test.T; cb: Test.T; n: INTEGER): INTEGER
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_other(self: Test.T): Test.U RAISES {NetObj.Error,
    Thread.Alerted};

PROCEDURE Surrogate_reader(self: Test.T; rd: Rd.T): TEXT
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_writer(self: Test.T; wr: Wr.T; t: TEXT)
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_quit(self: Test.T) RAISES {NetObj.Error,
    Thread.Alerted};

PROCEDURE Stub_add(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_echo(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_rec(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_list(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_sum(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_arr(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_chars(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_texts(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_fail(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted,
    Test.Oops};

PROCEDURE Stub_callback(
    self: Test.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

PROCEDURE Stub_other(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_reader(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_writer(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_quit(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

END Test_T_v1.
