package org.joget.mokxa;

import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.app.service.PushServiceUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SendCalendarEventReminderTool extends DefaultApplicationPlugin {

    @Override
    public String getName() {
        return "Send Calendar Event Reminder Tool";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getLabel() {
        return "Send Calendar Event Reminders";
    }

    @Override
    public String getDescription() {
        return "Sends reminders before events based on local event time and user timezone.";
    }

    @Override
    public Object execute(Map props) {

        LogUtil.info(getClass().getName(), "==== REMINDER TOOL START ====");

        try {

            String formId = getPropertyString("formDefId");
            String tableName = getTableName(formId);

            String fromDateTimeField = getPropertyString("fromDateTimeField");
            String allDayField = getPropertyString("allDayField");
            String reminderMinutesStr = getPropertyString("reminderMinutes");
            String reminderSentField = getPropertyString("reminderSentField");

            // NEW (configurable window)
            String windowMinutesStr = getPropertyString("reminderWindowMinutes");

            int reminderMinutes = 15;
            int windowMinutes = 5;

            try {
                reminderMinutes = Integer.parseInt(reminderMinutesStr);
            } catch (Exception ignore) {}

            try {
                if (windowMinutesStr != null && !windowMinutesStr.isEmpty()) {
                    windowMinutes = Integer.parseInt(windowMinutesStr);
                }
            } catch (Exception ignore) {}


            LogUtil.info(getClass().getName(),
                    "ReminderMinutes=" + reminderMinutes +
                            ", WindowMinutes=" + windowMinutes);

            // ALWAYS UTC
            Instant nowUtc = Instant.now();

            Instant targetUtc = nowUtc.plusSeconds(reminderMinutes * 60L);

            Instant windowStart = targetUtc.minusSeconds(windowMinutes * 60L);
            Instant windowEnd   = targetUtc.plusSeconds(windowMinutes * 60L);

            String start = windowStart.toString();
            String end   = windowEnd.toString();

            LogUtil.info(getClass().getName(),
                    "NowUTC=" + nowUtc +
                            ", Target=" + targetUtc +
                            ", WindowStart=" + start +
                            ", WindowEnd=" + end);

            FormDataDao dao =
                    (FormDataDao) AppUtil.getApplicationContext()
                            .getBean("formDataDao");

            String where =
                    "WHERE (" +
                            "c_" + reminderSentField + " = 'false' " +
                            "OR c_" + reminderSentField + " = '' " +
                            "OR c_" + reminderSentField + " IS NULL" +
                            ") " +
                            "AND c_" + fromDateTimeField + " IS NOT NULL " +
                            "AND c_" + fromDateTimeField + " BETWEEN ? AND ? ";


            LogUtil.info(getClass().getName(),
                    "Query WHERE = " + where);

            List<Object> params = new ArrayList<>();
            params.add(start);
            params.add(end);


            LogUtil.info(getClass().getName(),
                    "Query params = " + start + " , " + end);

            FormRowSet rows = dao.find(
                    formId,
                    tableName,
                    where,
                    params.toArray(),
                    null, null, null, null
            );

            LogUtil.info(getClass().getName(),
                    "Rows found = " + rows.size());

            for (FormRow row : rows) {

                try {

                    // skip all-day
                    if ("true".equalsIgnoreCase(
                            row.getProperty(allDayField))) {
                        continue;
                    }

                    String startStr =
                            row.getProperty(fromDateTimeField);

                    if (startStr == null || startStr.isEmpty()) {
                        continue;
                    }

                    Instant eventStartUtc;

                    try {
                        eventStartUtc = Instant.parse(startStr);
                    } catch (Exception ex) {
                        LogUtil.warn(
                                getClass().getName(),
                                "Invalid UTC date: " + startStr
                        );
                        continue;
                    }

                    Instant reminderTime =
                            eventStartUtc.minusSeconds(
                                    reminderMinutes * 60L
                            );

                    // if reminder time not reached → skip
                    if (nowUtc.isBefore(reminderTime)) {
                        continue;
                    }

                    // send
                    sendPushNotification(row);

                    // mark sent
                    row.setProperty(reminderSentField, "true");

                    FormRowSet rs = new FormRowSet();
                    rs.add(row);

                    dao.saveOrUpdate(
                            formId,
                            tableName,
                            rs
                    );

                } catch (Exception rowEx) {

                    LogUtil.error(
                            getClass().getName(),
                            rowEx,
                            "Reminder failed for row id=" +
                                    row.getId()
                    );
                }
            }

        } catch (Exception e) {

            LogUtil.error(
                    getClass().getName(),
                    e,
                    "Reminder tool execution failed"
            );
        }

        LogUtil.info(getClass().getName(), "==== REMINDER TOOL END ====");

        return null;
    }

    private void sendPushNotification(FormRow row) {

        try {
            String subject = row.getProperty("subject");
            if (subject == null || subject.isEmpty()) {
                subject = "Upcoming Meeting";
            }

            String message = "Your meeting starts in 15 minutes.";

            String eventUrl = row.getProperty("ms_event_url");
            if (eventUrl == null) {
                eventUrl = "";
            }

            // Semicolon-separated EMAILS
            String internalEmails = row.getProperty("internal_participants");

            if (internalEmails == null || internalEmails.isEmpty()) {
                LogUtil.warn(getClass().getName(), "No participant emails found for reminder");
                return;
            }

            for (String email : internalEmails.split(";")) {

                email = email.trim();
                if (email.isEmpty()) continue;

                String username = getUsernameByEmail(email);

                if (username == null) {
                    LogUtil.warn(   getClass().getName(), "No Joget user found for email=" + email);
                    continue;
                }

                int result = PushServiceUtil.sendUserPushNotification(   username, subject, message, eventUrl, "", "", true);

                if (result == 1) {
                    LogUtil.info( getClass().getName(), "Push sent to " + username + " (" + email + ")");
                } else {
                    LogUtil.warn(getClass().getName(), "Push failed for " + username + " (" + email + ")");
                }
            }

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Push notification failed" );
        }
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(
                getClassName(),
                "/properties/SendCalendarEventReminderTool.json",
                null,
                true,
                null
        );
    }

    private String getTableName(String formId) {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        FormDefinitionDao formDefinitionDao = (FormDefinitionDao) AppUtil.getApplicationContext().getBean("formDefinitionDao");
        return formDefinitionDao.loadById(formId, appDef).getTableName();
    }

    private String getUsernameByEmail(String email) {

        if (email == null || email.isEmpty()) {
            return null;
        }

        try {
            DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");

            String sql = "SELECT id FROM dir_user WHERE email = ? LIMIT 1";

            try (Connection con = ds.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setString(1, email.trim().toLowerCase());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("id"); // Joget username
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Failed to resolve username for email=" + email);
        }

        return null;
    }



}