package net.thoughtmachine.please.plugin.runconfiguration

import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.rd.util.firstOrNull
import net.thoughtmachine.please.plugin.PleaseBuildFileType
import net.thoughtmachine.please.plugin.PleaseFile
import net.thoughtmachine.please.plugin.graph.BuildTarget
import net.thoughtmachine.please.plugin.graph.PackageIndexExtension


/**
 * Provides gutter icons against build rules in BUILD files
 */
object PleaseLineMarkerProvider : RunLineMarkerContributor() {

    // getInfo needs to apply the run info to the LeafPsiElement as that's what intellij demands. It looks for the
    // IDENT of the function call and applies the run actions to that.
    override fun getInfo(element: PsiElement): Info? {
        if(element !is LeafPsiElement) {
            return null
        }

        if(element.elementType != PyTokenTypes.IDENTIFIER) {
            return null
        }

        val callExpr = element.parent.parent
        if (callExpr !is PyCallExpression) {
            return null
        }

        val file = element.containingFile
        // Skip build defs as they don't define build rules
        if(file.fileType != PleaseBuildFileType) {
            return null
        }
        if(file !is PleaseFile) {
            return null
        }

        val name = callExpr.argumentList?.getKeywordArgument("name")
        if (name != null) {
            val pkg = file.getPleasePackage() ?: return null
            val expr = name.valueExpression
            if(expr is PyStringLiteralExpression) {
                val target = pkg.targetByName(expr.stringValue) ?: return null
                return Info(
                    AllIcons.Actions.Execute,
                    filterActions(element.project, target).toTypedArray(),
                ) { "run $target" }
            }
        }

        return null
    }

    private fun filterActions(project: Project, target: BuildTarget): List<AnAction> {
        val label = target.toString()
        val actions = mutableListOf(PleaseAction(project, label, "build",
            PleaseBuildExecutor, newBuildConfig(project, target.pkg.pleaseRoot, label)))

        val test = target.info?.test ?: true
        val binary = target.info?.binary ?: true

        if (test) {
            actions.addAll(listOf(
                PleaseAction(project, label, "test",
                    DefaultRunExecutor.getRunExecutorInstance(), newTestConfig(project, target.pkg.pleaseRoot, label)),
                PleaseAction(project, label, "test",
                    DefaultDebugExecutor.getDebugExecutorInstance(), newTestConfig(project, target.pkg.pleaseRoot, label)),
            ))
        }
        // Include these options if we don't have any information about the target. This seems more useful. If it's not
        // a test or binary, Please will give a decent error message anyway.
        if (binary && !test || target.info == null) {
            actions.addAll(listOf(
                PleaseAction(project, label, "run",
                    DefaultRunExecutor.getRunExecutorInstance(), newRunConfig(project, target.pkg.pleaseRoot, label)),
                PleaseAction(project, label, "run",
                    DefaultDebugExecutor.getDebugExecutorInstance(), newRunConfig(project, target.pkg.pleaseRoot, label)),
            ))
        }

        return actions
    }

    private fun newRunConfig(project: Project, pleaseRoot : String, target: String) = PleaseRunConfiguration(
        project,
        PleaseRunConfigurationType.Factory(PleaseRunConfigurationType()),
        PleaseRunConfigArgs(target, pleaseRoot = pleaseRoot)
    )

    private fun newBuildConfig(project: Project, pleaseRoot : String, target: String) = PleaseBuildConfiguration(
        project,
        PleaseBuildConfigurationType.Factory(PleaseBuildConfigurationType()),
        PleaseBuildConfigArgs(target, pleaseRoot = pleaseRoot)
    )

    fun newTestConfig(project: Project, pleaseRoot : String, target: String, tests: String = "") = PleaseTestConfiguration(
        project,
        PleaseTestConfigurationType.Factory(PleaseTestConfigurationType()),
        PleaseTestConfigArgs(target, tests = tests, pleaseRoot = pleaseRoot)
    )

}