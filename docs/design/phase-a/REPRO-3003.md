# Phase A — Reproduktion 3003 am 20.11.2026

## Beobachtung

A52 `RZ8T703XJWF`, Android 14, zentrale Device-Bridge, Paket `de.hendler.frame61`.

1. App über die Brücke gestartet; Startseite mit „Stranger Things“ belegt (`repro-initial/03.png`, `04.xml`).
2. Fokussierte Hero-Karte geöffnet; Detailansicht mit „Abspielen“ belegt (`repro-detail/02.png`, `03.xml`).
3. „Abspielen“ aktiviert. Nach acht Sekunden zeigte die App „Wiedergabe fehlgeschlagen“ und `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` (`repro-play/02.png`, `03.xml`).
4. Nach weiteren zehn Sekunden blieb derselbe Fehler ohne automatische Erholung sichtbar (`repro-play/05.png`, `06.xml`).

Damit sind ExoPlayer-3003 und fehlende automatische Erholung reproduziert. Die erste Aufnahme enthält noch keine belastbare End-URL, Header oder ersten Medienbytes; genau diese Diagnosespur wird in Phase A ergänzt.

## Quell-/Versionsgrenze

Der Owner meldet den Fehler für Release 1.2.0. Die Brücke erlaubt kein beliebiges `dumpsys package`; die installierte Version wurde vor der Fremdapp-Unterbrechung nicht ausgelesen. Diese Aufnahme beweist daher den Fehler im damals installierten Paket `de.hendler.frame61`, nicht eigenständig dessen exakten Versionscode. Die nächste Geräteprüfung muss ein hash-/quellgebundenes APK über die Device-Lab-Queue installieren und Version/Signatur aus dem Manifest binden.

## Verworfene Folgeaufnahme

Bei einer späteren Retry-Aufnahme stand unerwartet `de.hendlerdynasty.studioproof.app.debug` im Vordergrund. Die verworfenen Daten liegen ausschließlich privat unter `~/hendler-dynasty/evidence/frame61/phase-a-discarded-20261120/` und werden **nicht** als FRAME61-Fehler- oder Lognachweis verwendet. Sie sind wegen fremder App- und Kopplungsdaten nicht Teil des öffentlichen Repos. Die interaktive Gerätesitzung wurde sofort gestoppt; es erfolgten keine weiteren Taps oder Wiederherstellungsaktionen.
