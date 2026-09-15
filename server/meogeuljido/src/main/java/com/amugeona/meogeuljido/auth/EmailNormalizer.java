package com.amugeona.meogeuljido.auth;

import java.util.Locale;

public class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return email.toLowerCase(Locale.ROOT);
    }

}
