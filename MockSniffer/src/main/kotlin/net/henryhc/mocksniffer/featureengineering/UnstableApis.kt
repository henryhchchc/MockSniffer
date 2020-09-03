package net.henryhc.mocksniffer.featureengineering

val unstableInterfaces = setOf(
        "java.lang.AutoClosable",
        "java.io.Closable",
        "java.sql.",
        "javax.servlet.",
        "java.io.Flushable",
        "java.net.",
        "javax.naming.",
        "javax.security."
)

val unstableAPIs = setOf(
        // IO
        "java.io.IOException",
        "java.io.File",
        "java.io.FileNotFoundException",
        "java.io.FileInputStream",
        "java.io.FileOutputStream",
        "java.nio.",
        "javax.persistence.",

        // Threading
        "java.util.concurrent.",
        "java.lang.InterruptedException",
        "java.lang.Thread",
        "java.lang.Runnable",
        "java.util.Timer",
        "java.lang.Process",

        // Network
        "java.net.",
        "javax.net.",
        "javax.servlet.",
        "javax.mail.",

        // Database
        "java.sql.",

        // Crypto
        "java.security.",
        "javax.security.",
        "javax.crypto.",
        "javax.naming.",
        "java.lang.SecurityManager",

        // Time
        "java.time.Clock",
        "java.time.Clock\$SystemClock",
        "java.time.Clock\$TickClock",
        "java.time.Clock\$FixedClock",
        "java.time.Clock\$OffsetClock",

        // RPC
        "javax.jms.",
        "javax.ws.",

        // Others
        "java.util.Properties",
        "javax.resource.spi."

)