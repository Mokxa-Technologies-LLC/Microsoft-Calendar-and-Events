//package org.joget.mokxa.dao;
//
//import org.joget.commons.spring.model.AbstractSpringDao;
//import org.joget.mokxa.model.GraphAuth;
//
//import java.sql.Timestamp;
//import java.util.ArrayList;
//import java.util.Collection;
//import java.util.List;
//
//public class GraphAuthDaoImpl extends AbstractSpringDao implements GraphAuthDao {
//
//    @Override
//    public GraphAuth getLatest(String username) {
//
//        Collection list = find(
//                "GraphAuth",
//                "where jogetUsername=? and status='ACTIVE' order by lastTokenRefreshAt desc",
//                new Object[]{username},
//                null,
//                null,
//                null,
//                null
//        );
//
//        if (list != null && !list.isEmpty()) {
//            return (GraphAuth) list.iterator().next();
//        }
//
//        return null;
//    }
//
//    @Override
//    public void save(GraphAuth auth) {
//        saveOrUpdate("GraphAuth", auth);
//    }
//
//
//
//    @Override
//    public GraphAuth getById(String id) {
//        return (GraphAuth) find("GraphAuth", id);
//    }
//
//    @Override
//    public List<GraphAuth> getTokensNeedingRefresh(Timestamp threshold) {
//        List<GraphAuth> result = new ArrayList<>();
//        Collection list = find(
//                "GraphAuth",
//                "where status='ACTIVE' " +
//                        "and refreshToken is not null " +
//                        "and refreshToken != '' " +
//                        "and expiresAt <= ? " +
//                        "order by expiresAt asc",
//                new Object[]{threshold},
//                null,
//                null,
//                null,
//                null
//        );
//
//        if (list != null) {
//            for (Object o : list) {
//                result.add((GraphAuth) o);
//            }
//        }
//        return result;
//    }
//
//}



package org.joget.mokxa.dao;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.spring.model.AbstractSpringDao;
import org.joget.commons.util.LogUtil;
import org.joget.mokxa.model.GraphAuth;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GraphAuthDaoImpl extends AbstractSpringDao implements GraphAuthDao {

    @Override
    public GraphAuth getLatest(String username) {
        try {
            TransactionTemplate transactionTemplate = (TransactionTemplate) AppUtil.getApplicationContext().getBean("transactionTemplate");
            GraphAuth result = (GraphAuth) transactionTemplate.execute(new TransactionCallback() {
                @Override
                public Object doInTransaction(TransactionStatus ts) {
                    Collection list = find(
                            "GraphAuth",
                            "where jogetUsername=? and status='ACTIVE' order by lastTokenRefreshAt desc",
                            new Object[]{username},
                            null,
                            null,
                            null,
                            null
                    );
                    if (list != null && !list.isEmpty()) {
                        return (GraphAuth) list.iterator().next();
                    }
                    return null;
                }
            });
            return result;
        } catch (Exception e) {
            LogUtil.error(GraphAuthDaoImpl.class.getName(), e, "GetLatest GraphAuth Error for username: " + username);
            return null;
        }
    }

    @Override
    public void save(GraphAuth auth) {
        try {
            TransactionTemplate transactionTemplate = (TransactionTemplate) AppUtil.getApplicationContext().getBean("transactionTemplate");
            transactionTemplate.execute(new TransactionCallback() {
                @Override
                public Object doInTransaction(TransactionStatus ts) {
                    saveOrUpdate("GraphAuth", auth);
                    return null;
                }
            });
        } catch (Exception e) {
            LogUtil.error(GraphAuthDaoImpl.class.getName(), e, "Save GraphAuth Error");
        }
    }

    @Override
    public void saveOrUpdate(GraphAuth auth) {
        TransactionTemplate tt = (TransactionTemplate) AppUtil.getApplicationContext()
                .getBean("transactionTemplate");
        tt.execute(status -> {
            // Find existing record
            String query = "where jogetUsername = ? and tenantId = ? and msOid = ?";
            Collection existing = find("GraphAuth", query,
                    new Object[]{auth.getJogetUsername(), auth.getTenantId(), auth.getMsOid()},
                    null, null, null, null);

            if (existing != null && !existing.isEmpty()) {
                // Update the first found record
                GraphAuth persistent = (GraphAuth) existing.iterator().next();
                persistent.setMsUpn(auth.getMsUpn());
                persistent.setScopes(auth.getScopes());
                persistent.setTokenCacheEnc(auth.getTokenCacheEnc());
                persistent.setRefreshToken(auth.getRefreshToken());
                persistent.setExpiresAt(auth.getExpiresAt());
                persistent.setCacheFormat(auth.getCacheFormat());
                persistent.setStatus(auth.getStatus());
                persistent.setLastTokenRefreshAt(auth.getLastTokenRefreshAt());
                persistent.setAccessTokenDebug(auth.getAccessTokenDebug());
                saveOrUpdate("GraphAuth", persistent);
            } else {
                // Insert new
                saveOrUpdate("GraphAuth", auth);
            }
            return null;
        });
    }

    @Override
    public GraphAuth getById(String id) {
        try {
            TransactionTemplate transactionTemplate = (TransactionTemplate) AppUtil.getApplicationContext().getBean("transactionTemplate");
            GraphAuth result = (GraphAuth) transactionTemplate.execute(new TransactionCallback() {
                @Override
                public Object doInTransaction(TransactionStatus ts) {
                    return (GraphAuth) find("GraphAuth", id);
                }
            });
            return result;
        } catch (Exception e) {
            LogUtil.error(GraphAuthDaoImpl.class.getName(), e, "GetById GraphAuth Error for id: " + id);
            return null;
        }
    }

    @Override
    public List<GraphAuth> getTokensNeedingRefresh(Timestamp threshold) {
        try {
            TransactionTemplate transactionTemplate = (TransactionTemplate) AppUtil.getApplicationContext().getBean("transactionTemplate");
            List<GraphAuth> result = (List<GraphAuth>) transactionTemplate.execute(new TransactionCallback() {
                @Override
                public Object doInTransaction(TransactionStatus ts) {
                    Collection list = find(
                            "GraphAuth",
                            "where status='ACTIVE' " +
                                    "and refreshToken is not null " +
                                    "and refreshToken != '' " +
                                    "and expiresAt <= ? " +
                                    "order by expiresAt asc",
                            new Object[]{threshold},
                            null,
                            null,
                            null,
                            null
                    );
                    List<GraphAuth> tokens = new ArrayList<>();
                    if (list != null) {
                        for (Object o : list) {
                            tokens.add((GraphAuth) o);
                        }
                    }
                    return tokens;
                }
            });
            return result;
        } catch (Exception e) {
            LogUtil.error(GraphAuthDaoImpl.class.getName(), e, "GetTokensNeedingRefresh Error for threshold: " + threshold);
            return new ArrayList<>(); // return empty list on error
        }
    }
}