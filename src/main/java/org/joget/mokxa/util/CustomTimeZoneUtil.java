package org.joget.mokxa.util;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.TimeZoneUtil;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.TimeZone;

public class CustomTimeZoneUtil {

    public static String getTimeZone(String username) {
        String timeZone = AppUtil.processHashVariable(
                "#user." + username + ".timeZone#",
                null,
                null,
                null
        );

//        LogUtil.info(CustomTimeZoneUtil.class.getName(), "Raw user timezone from Joget = [" + timeZone + "]");
        if (timeZone == null || timeZone.trim().isEmpty()) {
            LogUtil.warn(CustomTimeZoneUtil.class.getName(), "User timezone is empty → defaulting to UTC");
            return "UTC";
        }
        timeZone = timeZone.trim();
        if (isNumeric(timeZone)) {
//            LogUtil.info(CustomTimeZoneUtil.class.getName(), "Numeric timezone detected = " + timeZone);
            String resolved = TimeZoneUtil.getTimeZoneByGMT(timeZone);
//            LogUtil.info(CustomTimeZoneUtil.class.getName(), "Resolved GMT offset [" + timeZone + "] → [" + resolved + "]");
            if (resolved != null) {
                return resolved;
            }
            LogUtil.warn(CustomTimeZoneUtil.class.getName(), "Failed to resolve GMT offset → defaulting to UTC");
            return "UTC";
        }

        try {
            ZoneId.of(timeZone);
//            LogUtil.info(CustomTimeZoneUtil.class.getName(), "Valid IANA timezone detected = " + timeZone);
            return timeZone;
        } catch (Exception e) {
            LogUtil.error(CustomTimeZoneUtil.class.getName(), e, "Invalid timezone value [" + timeZone + "] → defaulting to UTC");
            return "UTC";
        }
    }

    public static ZoneId getUserZoneId(String username) {
        return ZoneId.of(getTimeZone(username));
    }

    public static String convertUtcToUserZone(String utcDateTime,String username) {
        String userTimeZone = getTimeZone(username);
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


    public static String convertUserZoneToUtc(String userDateTime, String username) {
        if (userDateTime == null || userDateTime.isEmpty()) {
            return null;
        }

        String userTimeZone = getTimeZone(username);

        try {
            // Parse the user's datetime (Joget format: yyyy-MM-dd HH:mm)
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            inputFormat.setTimeZone(TimeZone.getTimeZone(userTimeZone));

            Date date = inputFormat.parse(userDateTime);

            // Format to UTC ISO 8601 with Z suffix
            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            outputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            String utcDateTime = outputFormat.format(date);

//            LogUtil.info(CustomTimeZoneUtil.class.getName(),
//                    "Converted [" + userDateTime + "] in timezone [" + userTimeZone +
//                            "] to UTC [" + utcDateTime + "]");

            return utcDateTime;

        } catch (Exception e) {
            LogUtil.error(CustomTimeZoneUtil.class.getName(), e,
                    "Failed to convert [" + userDateTime + "] to UTC for user [" + username + "]");
            throw new RuntimeException("Failed to convert datetime to UTC: " + userDateTime, e);
        }
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