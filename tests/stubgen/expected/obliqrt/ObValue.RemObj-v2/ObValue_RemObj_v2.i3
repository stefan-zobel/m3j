INTERFACE ObValue_RemObj_v2;

IMPORT Rd, SharedObj, Wr, StubLib, ObValue, Thread, NetObj;
TYPE Surrogate_ObValue_RemObj = ObValue.RemObj OBJECT
      OVERRIDES
        Who := Surrogate_Who;
        Select := Surrogate_Select;
        Invoke := Surrogate_Invoke;
        Update := Surrogate_Update;
        Redirect := Surrogate_Redirect;
        Has := Surrogate_Has;
        Obtain := Surrogate_Obtain;
      END;

PROCEDURE Surrogate_Who(
    self: ObValue.RemObj;
    VAR protected: BOOLEAN;
    VAR serialized: BOOLEAN): TEXT RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_Select(
    self: ObValue.RemObj;
    label: TEXT;
    internal: BOOLEAN;
    VAR hint: INTEGER): ObValue.Val RAISES {ObValue.Error,
    ObValue.Exception, ObValue.ServerError, SharedObj.Error, NetObj.Error,
    Thread.Alerted};

PROCEDURE Surrogate_Invoke(
    self: ObValue.RemObj;
    label: TEXT;
    argNo: INTEGER;
    READONLY args: ObValue.Vals;
    internal: BOOLEAN;
    VAR hint: INTEGER): ObValue.Val RAISES {ObValue.Error,
    ObValue.Exception, ObValue.ServerError, SharedObj.Error, NetObj.Error,
    Thread.Alerted};

PROCEDURE Surrogate_Update(
    self: ObValue.RemObj;
    label: TEXT;
    val: ObValue.Val;
    internal: BOOLEAN;
    VAR hint: INTEGER) RAISES {ObValue.ServerError, SharedObj.Error,
    NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_Redirect(
    self: ObValue.RemObj;
    val: ObValue.Val;
    internal: BOOLEAN) RAISES {ObValue.ServerError, SharedObj.Error,
    NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_Has(
    self: ObValue.RemObj;
    label: TEXT;
    VAR hint: INTEGER): BOOLEAN RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_Obtain(self: ObValue.RemObj; internal: BOOLEAN)
    : REF ObValue.ObjFields RAISES {ObValue.ServerError, NetObj.Error,
    Thread.Alerted};

PROCEDURE Stub_Who(
    self: ObValue.RemObj;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

PROCEDURE Stub_Select(
    self: ObValue.RemObj;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, ObValue.Error, ObValue.Exception, ObValue.ServerError,
    SharedObj.Error};

PROCEDURE Stub_Invoke(
    self: ObValue.RemObj;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, ObValue.Error, ObValue.Exception, ObValue.ServerError,
    SharedObj.Error};

PROCEDURE Stub_Update(
    self: ObValue.RemObj;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, ObValue.ServerError, SharedObj.Error};

PROCEDURE Stub_Redirect(
    self: ObValue.RemObj;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, ObValue.ServerError, SharedObj.Error};

PROCEDURE Stub_Has(
    self: ObValue.RemObj;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

PROCEDURE Stub_Obtain(
    self: ObValue.RemObj;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, ObValue.ServerError};

END ObValue_RemObj_v2.
