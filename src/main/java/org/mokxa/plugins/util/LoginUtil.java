package org.mokxa.plugins.util;

import org.joget.apps.app.service.AppUtil;

public class LoginUtil {
    public static String getAccessToken() {
        return AppUtil.processHashVariable("#appVariable.msToken#",AppUtil.getCurrentAssignment(),null,null);
    }
}
