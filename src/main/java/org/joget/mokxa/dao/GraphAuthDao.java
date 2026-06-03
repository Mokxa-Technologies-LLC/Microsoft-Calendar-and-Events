package org.joget.mokxa.dao;

import org.joget.mokxa.model.GraphAuth;

import java.sql.Timestamp;
import java.util.List;

public interface GraphAuthDao {

    GraphAuth getLatest(String username);

    void save(GraphAuth auth);

    List<GraphAuth> getTokensNeedingRefresh(Timestamp threshold);

    GraphAuth getById(String id);

    void saveOrUpdate(GraphAuth auth);

}