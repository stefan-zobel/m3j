(* The n-body simulation: records of LONGREAL and Math.sqrt. *)
MODULE Main;
IMPORT IO, Fmt, Math;

CONST
  Pi = 3.141592653589793d0;
  SolarMass = 4.0d0 * Pi * Pi;
  DaysPerYear = 365.24d0;

TYPE
  Body = RECORD x, y, z, vx, vy, vz, mass: LONGREAL END;
  System = ARRAY [0 .. 4] OF Body;

VAR bodies := System {
  Body {0.0d0, 0.0d0, 0.0d0, 0.0d0, 0.0d0, 0.0d0, SolarMass},
  Body {4.84143144246472090d+00, -1.16032004402742839d+00, -1.03622044471123109d-01,
        1.66007664274403694d-03 * DaysPerYear, 7.69901118419740425d-03 * DaysPerYear,
        -6.90460016972063023d-05 * DaysPerYear, 9.54791938424326609d-04 * SolarMass},
  Body {8.34336671824457987d+00, 4.12479856412430479d+00, -4.03523417114321381d-01,
        -2.76742510726862411d-03 * DaysPerYear, 4.99852801234917238d-03 * DaysPerYear,
        2.30417297573763929d-05 * DaysPerYear, 2.85885980666130812d-04 * SolarMass},
  Body {1.28943695621391310d+01, -1.51111514016986312d+01, -2.23307578892655734d-01,
        2.96460137564761618d-03 * DaysPerYear, 2.37847173959480950d-03 * DaysPerYear,
        -2.96589568540237556d-05 * DaysPerYear, 4.36624404335156298d-05 * SolarMass},
  Body {1.53796971148509165d+01, -2.59193146099879641d+01, 1.79258772950371181d-01,
        2.68067772490389322d-03 * DaysPerYear, 1.62824170038242295d-03 * DaysPerYear,
        -9.51592254519715870d-05 * DaysPerYear, 5.15138902046611451d-05 * SolarMass}};

PROCEDURE Offset (VAR b: System) =
  VAR px, py, pz := 0.0d0;
  BEGIN
    FOR i := FIRST (b) TO LAST (b) DO
      px := px + b[i].vx * b[i].mass;
      py := py + b[i].vy * b[i].mass;
      pz := pz + b[i].vz * b[i].mass;
    END;
    b[0].vx := -px / SolarMass;  b[0].vy := -py / SolarMass;  b[0].vz := -pz / SolarMass;
  END Offset;

PROCEDURE Energy (READONLY b: System): LONGREAL =
  VAR e := 0.0d0;  dx, dy, dz: LONGREAL;
  BEGIN
    FOR i := FIRST (b) TO LAST (b) DO
      WITH p = b[i] DO
        e := e + 0.5d0 * p.mass * (p.vx * p.vx + p.vy * p.vy + p.vz * p.vz);
        FOR j := i + 1 TO LAST (b) DO
          dx := p.x - b[j].x;  dy := p.y - b[j].y;  dz := p.z - b[j].z;
          e := e - p.mass * b[j].mass / Math.sqrt (dx * dx + dy * dy + dz * dz);
        END;
      END;
    END;
    RETURN e;
  END Energy;

PROCEDURE Advance (VAR b: System; dt: LONGREAL) =
  VAR dx, dy, dz, d2, mag: LONGREAL;
  BEGIN
    FOR i := FIRST (b) TO LAST (b) DO
      WITH p = b[i] DO
        FOR j := i + 1 TO LAST (b) DO
          WITH q = b[j] DO
            dx := p.x - q.x;  dy := p.y - q.y;  dz := p.z - q.z;
            d2 := dx * dx + dy * dy + dz * dz;
            mag := dt / (d2 * Math.sqrt (d2));
            p.vx := p.vx - dx * q.mass * mag;
            p.vy := p.vy - dy * q.mass * mag;
            p.vz := p.vz - dz * q.mass * mag;
            q.vx := q.vx + dx * p.mass * mag;
            q.vy := q.vy + dy * p.mass * mag;
            q.vz := q.vz + dz * p.mass * mag;
          END;
        END;
      END;
    END;
    FOR i := FIRST (b) TO LAST (b) DO
      WITH p = b[i] DO
        p.x := p.x + dt * p.vx;  p.y := p.y + dt * p.vy;  p.z := p.z + dt * p.vz;
      END;
    END;
  END Advance;

BEGIN
  Offset (bodies);
  IO.Put (Fmt.LongReal (Energy (bodies), Fmt.Style.Fix, 9) & "\n");
  FOR i := 1 TO 10000000 DO Advance (bodies, 0.01d0) END;
  IO.Put (Fmt.LongReal (Energy (bodies), Fmt.Style.Fix, 9) & "\n");
END Main.
