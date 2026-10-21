package org.mwolff.fbcrm.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Die Eingaben der Anmeldeseite, wenn der Code eingegeben wird.
 *
 * <p>Geprueft wird nur die Form, nie der Inhalt: Ob es die Adresse gibt und ob der Code stimmt,
 * beantwortet ausschliesslich {@code LoginUseCase} — und zwar fuer beide Faelle gleich (Kriterium
 * 3). Darum steht hier auch keine Regel „genau sechs Ziffern": Ein Vertipper mit fuenf Ziffern ist
 * ein falscher Code wie jeder andere und zaehlt als Fehleingabe, statt als 400 an der Bremse
 * vorbeizugehen. Die Laengengrenzen schuetzen Argon2 und die Datenbank vor unnoetig grossen
 * Eingaben.
 *
 * @param email die eingegebene Adresse
 * @param code der eingegebene Code
 */
public record LoginRequest(
    @NotBlank @Email @Size(max = 320) String email, @NotBlank @Size(max = 32) String code) {}
