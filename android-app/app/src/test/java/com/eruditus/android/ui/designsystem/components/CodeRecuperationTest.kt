package com.eruditus.android.ui.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Code de récupération (DESIGN.md § 7) : 4 groupes de 4 sans tiret ; une seule description TalkBack,
 * caractère par caractère. Le serveur le renvoie au format XXXX-XXXX-XXXX-XXXX (SPEC D35).
 */
class CodeRecuperationTest {

    @Test
    fun `le code du serveur donne 4 groupes de 4`() {
        assertEquals(listOf("7K3M", "Q9XA", "2HDE", "F8RT"), groupesDuCode("7K3M-Q9XA-2HDE-F8RT"))
    }

    @Test
    fun `espaces et casse ne changent pas les groupes`() {
        assertEquals(listOf("7K3M", "Q9XA", "2HDE", "F8RT"), groupesDuCode(" 7k3m q9xa-2hde F8RT "))
    }

    @Test
    fun `TalkBack lit chaque caractere, groupe par groupe`() {
        assertEquals("7 K 3 M, Q 9 X A, 2 H D E, F 8 R T", codeEpele(listOf("7K3M", "Q9XA", "2HDE", "F8RT")))
    }
}
