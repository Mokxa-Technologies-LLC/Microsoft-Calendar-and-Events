package org.joget.mokxa.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class EventFilterUtil {

    private static final DateTimeFormatter GRAPH_FORMAT =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public static Map<String, String> today(String currentUser) {
        ZoneId userZone = CustomTimeZoneUtil.getUserZoneId(currentUser);
        LocalDate today = LocalDate.now(userZone);
        return buildRange(
                today.atStartOfDay(userZone),
                today.atTime(23, 59, 59).atZone(userZone)
        );
    }

    public static Map<String, String> thisWeek(String currentUser) {
        ZoneId userZone = CustomTimeZoneUtil.getUserZoneId(currentUser);
        LocalDate today = LocalDate.now(userZone);
        LocalDate start = today.with(DayOfWeek.MONDAY);
        LocalDate end   = start.plusDays(6);

        return buildRange(
                start.atStartOfDay(userZone),
                end.atTime(23, 59, 59).atZone(userZone)
        );
    }

    public static Map<String, String> thisMonth(String currentUser) {
        ZoneId userZone = CustomTimeZoneUtil.getUserZoneId(currentUser);
        LocalDate today = LocalDate.now(userZone);
        LocalDate start = today.withDayOfMonth(1);
        LocalDate end   = start.plusMonths(1).minusDays(1);

        return buildRange(
                start.atStartOfDay(userZone),
                end.atTime(23, 59, 59).atZone(userZone)
        );
    }

    public static Map<String, String> custom(
            String from,
            String to,
            String currentUser
    ) {
        ZoneId userZone = CustomTimeZoneUtil.getUserZoneId(currentUser);
        LocalDate startDate = LocalDate.parse(from);
        LocalDate endDate   = LocalDate.parse(to);

        return buildRange(
                startDate.atStartOfDay(userZone),
                endDate.atTime(23, 59, 59).atZone(userZone)
        );
    }

    private static Map<String, String> buildRange(
            ZonedDateTime localStart,
            ZonedDateTime localEnd
    ) {
        ZonedDateTime startUtc =
                localStart.withZoneSameInstant(ZoneOffset.UTC);

        ZonedDateTime endUtc =
                localEnd.withZoneSameInstant(ZoneOffset.UTC);

        Map<String, String> range = new HashMap<>();
        range.put("start", startUtc.format(GRAPH_FORMAT));
        range.put("end", endUtc.format(GRAPH_FORMAT));
        return range;
    }
}