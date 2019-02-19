package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import kotlin.annotation.Retention

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
annotation class SanityCheck(val name: String)
