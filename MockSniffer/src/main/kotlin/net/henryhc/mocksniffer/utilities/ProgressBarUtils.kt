package net.henryhc.mocksniffer.utilities

import me.tongfei.progressbar.ProgressBar

fun <E> Iterable<E>.wrapProgressBar(name: String) = ProgressBar.wrap(this, name)

fun ProgressBar.stepSynchronized() = synchronized(this) { this.step() }