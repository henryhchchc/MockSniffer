package net.henryhc.mocksniffer.testinstrument

import net.henryhc.mocksniffer.codeinput.Project
import org.apache.log4j.BasicConfigurator
import org.apache.log4j.Logger
import soot.PackManager
import soot.Scene
import soot.SootClass
import soot.Transform
import soot.options.Options
import java.io.File
import java.nio.file.Path

class SootLauncher(
    val project: Project,
    tempDir: String,
    private val rtJarPath: String,
    private val targetType: String
) {

    private val tempDir = Path.of(tempDir).normalize().toAbsolutePath().toFile()

    fun start() {
        println("Instrumenting project ${project.rootDirectory}")
        BasicConfigurator.configure()
        Logger.getRootLogger().removeAllAppenders()
        setupSoot(project, targetType)
        when (targetType) {
            "src" -> {
                project.targetClassDir.takeIf { it.exists() && it.isDirectory }?.addClasses()
                project.targetClassDir.takeIf { it.exists() && it.isDirectory }?.also { instrument(it) }
            }
            "test" -> {
                project.targetClassDir.takeIf { it.exists() && it.isDirectory }?.addClasses()
                project.testTargetClassDir.takeIf { it.exists() && it.isDirectory }?.addClasses()
                project.testTargetClassDir.takeIf { it.exists() && it.isDirectory }?.also { instrument(it) }
            }
        }
    }


    private fun setupSoot(project: Project, type: String) {
        val outputDir = Path.of(tempDir.absolutePath, type).toAbsolutePath().normalize().toFile()
        with(Options.v()) {
            set_output_dir(outputDir.absolutePath)
            set_soot_classpath(project.getSootClassPath(rtJarPath))
            set_hierarchy_dirs(true)
            set_java_version(Options.java_version_8)
            set_verbose(false)
            set_ignore_resolution_errors(true)
            set_allow_phantom_refs(true)
            set_unfriendly_mode(true)
            set_ignore_resolving_levels(true)
            set_output_format(Options.output_format_class)
        }
//        PackManager.v().getPack("wjpp").add(Transform("wjpp.add_dumy", object : SceneTransformer() {
//            override fun internalTransform(phaseName: String, options: Map<String, String>) {
//                Scene.v().getSootClass(SootClass.INVOKEDYNAMIC_DUMMY_CLASS_NAME)
//            }
//        }))
        PackManager.v().getPack("jtp").add(Transform("jtp.record", MockInstrument()))
        Scene.v().addBasicClass("tool.MockitoLogger", SootClass.SIGNATURES)
        Scene.v().getSootClass(SootClass.INVOKEDYNAMIC_DUMMY_CLASS_NAME)

        if (System.getProperty("sun.boot.class.path") == null)
            System.setProperty("sun.boot.class.path", "")
        if (System.getProperty("java.ext.dirs") == null)
            System.setProperty("java.ext.dirs", "")
    }

    private fun instrument(dir: File) {
        val outputPath = Path.of(Options.v().output_dir()).toAbsolutePath()
        outputPath.toFile().deleteRecursively()
        try {
            Options.v().set_process_dir(listOf(dir.absolutePath))
            disablePacks.forEach { Options.v().setPhaseOption(it, "enabled:false") }
            soot.Main.v().run(emptyArray())
        } catch (ex: RuntimeException) {
            System.err.println("Error on processing: ${dir.absolutePath}")
            ex.printStackTrace()
        }
    }

    private fun File.addClasses() = this.walkTopDown().filter { it.extension == "class" }.forEach {
        val className = it.absolutePath
                .drop(this.absolutePath.length + 1)
                .dropLast(".class".length)
                .replace(File.separator, ".")
        Scene.v().addBasicClass(className, SootClass.SIGNATURES)
    }
}

private fun Project.getSootClassPath(rtJarPath: String) = (
        listOf(
                rtJarPath,
                targetClassDir.absolutePath,
                testTargetClassDir.absolutePath
        )
                + this.codeRepository.projects
                .flatMap { listOf(it.targetClassDir.absolutePath, it.testTargetClassDir.absolutePath) }
                + this.classPath
        ).toSet()
        .filter { File(it).exists() || it == "VIRTUAL_FS_FOR_JDK" }
        .joinToString(File.pathSeparator)


private val disablePacks = listOf(
        "jb.dtr",
        "jb.ese",
//        "jb.ls",
//        "jb.a",
        "jb.ule",
//        "jb.ulp",
        "jb.cp",
        "jb.dae",
        "jp.cp-ule",
        "jb.ne",
        "jb.uce",
        "jap",
        "jop",
        "cg",
        "wjtp",
        "wjop",
        "wjap"
)
