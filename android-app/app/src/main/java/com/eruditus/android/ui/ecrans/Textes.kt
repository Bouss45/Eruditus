package com.eruditus.android.ui.ecrans

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources

/**
 * Les textes des ressources, vus d'une fonction ordinaire : celles qui accordent un texte
 * (classement) le prennent ici, et les tests JVM leur donnent `strings.xml` lu directement, avec
 * les pluriels d'Android (`TextesRessources`, SPEC D72).
 */
interface Textes {
    fun texte(@StringRes id: Int, vararg args: Any): String

    /** Comme `getQuantityString` : la forme choisie par [n], mise en forme avec [args]. */
    fun pluriel(@PluralsRes id: Int, n: Int, vararg args: Any): String
}

/** Les textes de l'app, pour l'écran en cours. */
@Composable
fun textes(): Textes {
    val ressources = LocalResources.current
    return remember(ressources) { TextesAndroid(ressources) }
}

private class TextesAndroid(private val ressources: Resources) : Textes {
    override fun texte(id: Int, vararg args: Any): String = ressources.getString(id, *args)

    override fun pluriel(id: Int, n: Int, vararg args: Any): String = ressources.getQuantityString(id, n, *args)
}
