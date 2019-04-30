package nl.utwente.ewi.fmt.uppaalSMC.urpal.util

import com.uppaal.engine.Problem

class ProblemWrapper(type: String?, path: String?, fline: Int, fcolumn: Int, lline: Int, lcolumn: Int, msg: String?) : Problem(type, path, fline, fcolumn, lline, lcolumn, msg)