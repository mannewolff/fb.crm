/**
 * Die gewaehlte Datei eines Dateifeldes — oder nichts.
 *
 * Ein `<input type="file">` fuehrt seine Auswahl als `FileList`, und der Zugriff darauf ist
 * `FileList | null`: An jedem anderen Eingabefeld antwortet `files` mit `null`. Die Maske
 * interessiert nur die erste Datei — mehrere nimmt kein Weg dieser Anwendung entgegen (Issue
 * #148, Plan #150).
 *
 * Eine eigene Funktion und keine Verzweigung in der Maske: Den Fall „keine Liste" kann kein Test
 * ueber die Oberflaeche herstellen, und eine Verzweigung ohne Fall ist genau die Luecke, die die
 * Schwelle von 100 % nicht durchlaesst.
 */
export function ersteDatei(dateien: FileList | null): File | null {
  return dateien === null || dateien.length === 0 ? null : dateien[0];
}
