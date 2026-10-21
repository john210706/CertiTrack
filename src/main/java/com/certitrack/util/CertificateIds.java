package com.certitrack.util;

import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

public final class CertificateIds {
    private static final Pattern FORMAT = Pattern.compile("CERT-[A-Z0-9]{2,12}-[0-9]{4}-[0-9]{5,8}");
    private CertificateIds() {}

    public static boolean isValid(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }

    public static String generate(String programCode, LocalDate issueDate, long databaseId) {
        if (databaseId < 1 || databaseId > 99_999_999L)
            throw new IllegalStateException("Certificate ID sequence capacity exceeded");
        if (issueDate.getYear() < 0 || issueDate.getYear() > 9999)
            throw new IllegalArgumentException("Issue year must have four digits");
        return String.format(Locale.ROOT, "CERT-%s-%04d-%05d",
                programCode.toUpperCase(Locale.ROOT), issueDate.getYear(), databaseId);
    }
}
