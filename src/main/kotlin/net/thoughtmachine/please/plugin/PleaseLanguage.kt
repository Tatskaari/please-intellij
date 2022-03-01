package net.thoughtmachine.please.plugin

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.util.castSafelyTo
import com.jetbrains.python.inspections.PyInspectionExtension
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.impl.PyFileImpl
import net.thoughtmachine.please.plugin.graph.Package
import net.thoughtmachine.please.plugin.graph.PackageIndexer
import net.thoughtmachine.please.plugin.graph.PackageService
import javax.swing.Icon


val PLEASE_ICON = IconLoader.getIcon("/icons/please.png", PleaseBuildFileType.javaClass)
object PleaseLanguage : Language("Please")

abstract class PleaseFileType : LanguageFileType(PleaseLanguage)


object PleaseBuildFileType : PleaseFileType() {
    override fun getIcon() = PLEASE_ICON

    override fun getName() = "Please BUILD file"

    override fun getDefaultExtension() = ".plz"

    override fun getDescription() = "Please BUILD file"

    override fun getDisplayName(): String {
        return "BUILD"
    }
}

object PleaseBuildDefFileType : PleaseFileType() {
    override fun getIcon() = PLEASE_ICON

    override fun getName() = "Please build_defs file"

    override fun getDefaultExtension() = ".build_defs"

    override fun getDescription() = "Please build definition file"

    override fun getDisplayName(): String {
        return ".build_defs"
    }
}

class PleaseFile(viewProvider: FileViewProvider, private var type : PleaseFileType) : PyFileImpl(viewProvider, PleaseLanguage) {
    private var subincludes : MutableSet<String>? = null

    override fun getFileType(): FileType {
        return type
    }

    override fun toString(): String {
        return "Please File"
    }

    override fun getIcon(flags: Int): Icon {
        return PLEASE_ICON
    }

    /**
     * Gets the Please package name for the File by walking up the file tree to find the .plzconfig.
     */
    fun getPleasePackage() : Package? {
        // Build definitions file don't belong to a package.
        if (type == PleaseBuildDefFileType) {
            return null
        }

        val (root, pkg) = PackageIndexer.forFile(this) ?: return null
        return PackageService.resolvePackage(project, root, pkg)
    }

    fun targets() : List<PsiTarget> {
        val visitor = TargetVisitor()
        accept(visitor)
        return visitor.targets
    }

    fun getSubincludes() : Set<String> {
        if(subincludes != null ) {
            return subincludes!!
        }

        subincludes = mutableSetOf()
        accept(SubincludeVisitor(this))
        return subincludes!!
    }

    class SubincludeVisitor(private val file: PleaseFile) : PyRecursiveElementVisitor() {
        override fun visitPyCallExpression(call: PyCallExpression) {
            val functionName = call.callee?.name ?: ""
            if(functionName != "subinclude"){
                return
            }
            val includes = call.arguments.asSequence()
                .map { it.castSafelyTo<PyStringLiteralExpression>() }.filterNotNull()
                .toList()

            includes.asSequence()
                .map { it.castSafelyTo<PyStringLiteralExpression>() }.filterNotNull()
                .forEach { expr ->
                    file.subincludes!!.add(expr.stringValue)
                }
        }
    }

    /**
     * find is a convenience function to find a BUILD file for a package in the same Please repo as this file
     */
    fun find(pkgName : String) : PleaseFile? {
        val projectRoot = getPleasePackage()?.pleaseRoot ?: return null

        return PackageIndexer.lookup(project, projectRoot, pkgName)
    }
}

fun (PyCallExpression).asTarget(): PsiTarget? {
    val nameArg = argumentList?.getKeywordArgument("name")?.valueExpression
    if(nameArg != null && nameArg is PyStringLiteralExpression) {
        return PsiTarget( nameArg.stringValue, this)
    }
    return null
}

private class TargetVisitor : PyRecursiveElementVisitor() {
    val targets = mutableListOf<PsiTarget>()

    override fun visitPyCallExpression(node: PyCallExpression) {
        val target = node.asTarget()
        if (target != null) {
            targets.add(target)
        }
    }
}

/**
 * PsiTarget is a build target from an AST perspective.
 */
data class PsiTarget(val name : String, val element: PyCallExpression) : PsiElement by element {
    fun kind() : String {
        return element.callee?.text ?: ""
    }
}

class PleasePythonInspections : PyInspectionExtension() {
    override fun ignoreInterpreterWarnings(file: PyFile): Boolean {
        return file is PleaseFile
    }
}