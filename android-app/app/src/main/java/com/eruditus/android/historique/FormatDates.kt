package com.eruditus.android.historique

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Les dates de l'Historique (spec/ecrans.md § 16), dans la langue et le fuseau du téléphone, donnés
 * en paramètres : « 22 nov. 2026 » dans la liste, « 22 novembre 2026 » pour TalkBack, « 22 nov. »
 * dans la barre du détail. Vérifiées par `TextesHistoriqueTest`.
 */
object FormatDates {
    fun moyenne(date: Instant, zone: ZoneId, langue: Locale): String = formater(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM), zone, langue, date)

    fun longue(date: Instant, zone: ZoneId, langue: Locale): String = formater(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG), zone, langue, date)

    /** Le jour et le mois abrégé, sans l'année. */
    fun courte(date: Instant, zone: ZoneId, langue: Locale): String = formater(DateTimeFormatter.ofPattern("d MMM"), zone, langue, date)

    private fun formater(format: DateTimeFormatter, zone: ZoneId, langue: Locale, date: Instant): String =
        format.withLocale(langue).withZone(zone).format(date)
}
