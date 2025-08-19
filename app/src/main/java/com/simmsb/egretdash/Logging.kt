package com.simmsb.egretdash

import com.juul.khronicle.ConsoleLogger
import com.juul.khronicle.ConstantTagGenerator
import com.juul.khronicle.Log

fun configureLogging() {
    Log.tagGenerator = ConstantTagGenerator(tag = "EgretDash")
    Log.dispatcher.install(ConsoleLogger)
}