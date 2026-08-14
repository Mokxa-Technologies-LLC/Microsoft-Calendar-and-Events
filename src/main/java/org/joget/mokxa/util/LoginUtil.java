package org.joget.mokxa.util;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.mokxa.GraphAppContext;
import org.joget.mokxa.dao.GraphAuthDao;
import org.joget.mokxa.model.GraphAuth;
import org.json.JSONObject;

public class LoginUtil {

    public static String getAccessToken(String username) throws Exception {

        GraphAuthDao dao = (GraphAuthDao) GraphAppContext
                .getInstance()
                .getAppContext()
                .getBean("graphAuthDao");

        if (dao == null) {
            throw new Exception("graphAuthDao not found");
        }

        GraphAuth auth = dao.getLatest(username);

        if (auth == null) {
            throw new Exception(
                    "No token found for user: " + username
            );
        }

        String tokenJson =
                auth.getAccessTokenDebug();

        if (tokenJson == null ||
                tokenJson.trim().isEmpty()) {

            throw new Exception(
                    "Token JSON empty for user: " + username
            );
        }

//        LogUtil.info(LoginUtil.class.getName(),tokenJson);

        JSONObject json = new JSONObject(tokenJson);

        if (!json.has("access_token")) {

            throw new Exception(
                    "access_token missing in JSON"
            );
        }

        return json.getString("access_token");
    }
}