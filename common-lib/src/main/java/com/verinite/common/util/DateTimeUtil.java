package com.verinite.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeUtil {

    private DateTimeUtil() {}

    /** ISO 8583 DE7 / DE12 format: MMDDHHmmss */
    private static final DateTimeFormatter ISO8583_DATETIME = DateTimeFormatter.ofPattern("MMddHHmmss");

    /** ISO 8583 DE13 format: MMDD */
    private static final DateTimeFormatter ISO8583_DATE = DateTimeFormatter.ofPattern("MMdd");

    public static String toIso8583DateTime(LocalDateTime dt) {
        return dt.format(ISO8583_DATETIME);
    }

    public static LocalDateTime fromIso8583DateTime(String mmddHHmmss) {
        // Use current year — ISO 8583 doesn't carry year in DE7/DE12
        String withYear = LocalDateTime.now().getYear() + mmddHHmmss;
        return LocalDateTime.parse(withYear, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }
}