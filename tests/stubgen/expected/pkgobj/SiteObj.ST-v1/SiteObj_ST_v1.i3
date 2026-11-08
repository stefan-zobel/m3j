INTERFACE SiteObj_ST_v1;

IMPORT SiteObj, Rd, Wr, StubLib, Site, Thread, NetObj;
TYPE Surrogate_SiteObj_ST = SiteObj.ST OBJECT
      OVERRIDES
        get := Surrogate_get;
      END;

PROCEDURE Surrogate_get(self: SiteObj.ST): Site.T RAISES {NetObj.Error,
    Thread.Alerted};

PROCEDURE Stub_get(self: SiteObj.ST; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

END SiteObj_ST_v1.
