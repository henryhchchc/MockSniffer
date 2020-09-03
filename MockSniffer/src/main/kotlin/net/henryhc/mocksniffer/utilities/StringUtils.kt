package net.henryhc.mocksniffer.utilities


fun String.splitCamelCase() =
        this.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])".toRegex())

val mockRegexps = listOf(
        "\\\$\\\$EnhancerByMockitoWithCGLIB\\\$\\\$\\w+".toRegex(),
        "\\\$MockitoMock\\\$\\d+".toRegex(),
        "\\\$\\\$EnhancerByCGLIB\\\$\\\$\\w+".toRegex(),
        "\\\$\\\$EnhancerBySpringCGLIB\\\$\\\$\\w+".toRegex(),
        "\\\$\\\$EnhancerByCGLIB\\\$\\\$\\w+".toRegex()
)

fun String.trimMockObjectTypeName() = mockRegexps.fold(this) { str, regex ->
    str.replace(regex, "")
}.replace("\$java", "java")

