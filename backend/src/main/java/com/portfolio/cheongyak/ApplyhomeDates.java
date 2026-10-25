package com.portfolio.cheongyak;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** 청약홈 날짜 문자열. 종류에 따라 YYYY-MM-DD 와 YYYYMMDD 가 섞여 온다. 빈 값은 null */
@Slf4j
public final class ApplyhomeDates {

    private static final DateTimeFormatter COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private ApplyhomeDates() {
    }

    public static LocalDate parse(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String v = s.trim();
        try {
            return v.length() == 8 ? LocalDate.parse(v, COMPACT) : LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            log.warn("청약홈 날짜 형식이 낯설다: {}", v);
            return null;
        }
    }
}
