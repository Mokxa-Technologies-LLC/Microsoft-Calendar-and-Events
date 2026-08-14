package org.joget.mokxa;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.*;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONObject;
import org.joget.mokxa.model.ApiResponse;
import org.joget.mokxa.util.EventUtil;
import org.joget.mokxa.util.LoginUtil;

import java.util.Map;

public class CancelEvent extends FormBinder implements FormStoreBinder , FormStoreElementBinder, FormStoreMultiRowElementBinder {

    private  String CURRENT_USERNAME;

    @Override
    public FormRowSet store(Element element, FormRowSet rows, FormData formData) {
        String commentField = getPropertyString("commentField");
        boolean hasError = false;
        String apiUsernameField = getPropertyString("apiUsernameField");





        if (rows != null && !rows.isEmpty()) {
            FormRow row = rows.get(0);
            try {

                if (apiUsernameField != null && !apiUsernameField.isEmpty()) {
                    CURRENT_USERNAME = row.getProperty(apiUsernameField);
//                    LogUtil.info(getClassName(),"Current Username via form: "+CURRENT_USERNAME);
                }

                if (CURRENT_USERNAME == null || CURRENT_USERNAME.isEmpty()) {
                    CURRENT_USERNAME = WorkflowUtil.getCurrentUsername();
                    LogUtil.info(getClassName(),"Current Username via Current user: "+CURRENT_USERNAME);
                }

                EventUtil eventUtil = new EventUtil(LoginUtil.getAccessToken(CURRENT_USERNAME),null,CURRENT_USERNAME);

//                LogUtil.info("Rows",rows.toString());
                showFormData(formData);
                String comment = row.getProperty(commentField);
                String eventId = row.getId();

                ApiResponse apiResponse= eventUtil.cancelEvent(eventId,comment);

                if (!(apiResponse != null && (apiResponse.getResponseCode() == 202 || apiResponse.getResponseCode()==404))){
                    formData.addFormError("comment",
                            "Failed to cancel Microsoft calendar event.");
                    LogUtil.error(getClass().getName(), null,
                            "Microsoft Graph error → "
                                    + (apiResponse != null ? apiResponse.getResponseBody() : "No response"));
                    hasError = true;
                }else{
                    FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
                    formDataDao.delete("event","lms_event",new String[]{eventId});
                }

            } catch (Exception e) {
                formData.addFormError("comment", "Unexpected error while cancelling event.");
                LogUtil.error(getClass().getName(), e, "Event Cancelation failed");
                hasError = true;
            }
        }

        if (hasError) {
            return null;
        }

        return rows;
    }

    private void showFormData(FormData formData) {
        JSONObject json = new JSONObject();
        Map<String, String[]> params = formData.getRequestParams();
        if (params != null) {
            for (Map.Entry<String, String[]> entry : params.entrySet()) {
                json.put(entry.getKey(), String.join(",", entry.getValue()));
            }
        }

//        LogUtil.info(getClass().getName(), "FormData params: " + json.toString());
    }


    @Override
    public String getName() {
        return getClass().getName();
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getLabel() {
        return "Cancel Microsoft Calendar Event";
    }

    @Override
    public String getDescription() {
        return "Cancel Microsoft Outlook calendar events from Joget forms, including recurring events and Teams meetings, using Microsoft Graph API.";
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/CancelEvent.json", null, true, null);
    }
}
