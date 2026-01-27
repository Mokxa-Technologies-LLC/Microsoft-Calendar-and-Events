package org.mokxa.plugins.util;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.TimeZoneUtil;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class CustomTimeZoneUtil {

    public static String getTimeZone() {
        String timeZone = AppUtil.processHashVariable("#currentUser.timeZone#", AppUtil.getCurrentAssignment(), null, null);
        LogUtil.info(CustomTimeZoneUtil.class.getName(), "Raw user timezone from Joget = [" + timeZone + "]");
        if (timeZone == null || timeZone.trim().isEmpty()) {
            LogUtil.warn(CustomTimeZoneUtil.class.getName(), "User timezone is empty → defaulting to UTC");
            return "UTC";
        }
        timeZone = timeZone.trim();
        if (isNumeric(timeZone)) {
            LogUtil.info(CustomTimeZoneUtil.class.getName(), "Numeric timezone detected = " + timeZone);
            String resolved = TimeZoneUtil.getTimeZoneByGMT(timeZone);
            LogUtil.info(CustomTimeZoneUtil.class.getName(), "Resolved GMT offset [" + timeZone + "] → [" + resolved + "]");
            if (resolved != null) {
                return resolved;
            }
            LogUtil.warn(CustomTimeZoneUtil.class.getName(), "Failed to resolve GMT offset → defaulting to UTC");
            return "UTC";
        }

        try {
            ZoneId.of(timeZone);
            LogUtil.info(CustomTimeZoneUtil.class.getName(), "Valid IANA timezone detected = " + timeZone);
            return timeZone;
        } catch (Exception e) {
            LogUtil.error(CustomTimeZoneUtil.class.getName(), e, "Invalid timezone value [" + timeZone + "] → defaulting to UTC");
            return "UTC";
        }
    }

    public static ZoneId getUserZoneId() {
        return ZoneId.of(getTimeZone());
    }

    public static String convertUtcToUserZone(String utcDateTime) {
        String userTimeZone = getTimeZone();
        if (utcDateTime == null || utcDateTime.isEmpty()) {
            return null;
        }
        Instant utcInstant;
        try {
            if (utcDateTime.contains(".")) {
                utcDateTime =
                        utcDateTime.substring(0, utcDateTime.indexOf(".")) + "Z";
            } else if (!utcDateTime.endsWith("Z")) {
                utcDateTime = utcDateTime + "Z";
            }
            utcInstant = Instant.parse(utcDateTime);
        } catch (Exception e) {
            throw new RuntimeException("Invalid UTC dateTime from Graph: " + utcDateTime, e);
        }
        ZonedDateTime userZonedDateTime = utcInstant.atZone(ZoneId.of(userTimeZone));
        String local = userZonedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        return local;
    }

    private static boolean isNumeric(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}