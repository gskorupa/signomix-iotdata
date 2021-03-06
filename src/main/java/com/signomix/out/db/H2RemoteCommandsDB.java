/*
* Copyright (C) Grzegorz Skorupa 2018.
* Distributed under the MIT License (license terms are at http://opensource.org/licenses/MIT).
 */
package com.signomix.out.db;

import com.signomix.events.ActuatorEvent;
import com.signomix.out.iot.ThingsDataException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.cricketmsf.Adapter;
import org.cricketmsf.event.Event;
import org.cricketmsf.out.db.H2RemoteDB;
import org.cricketmsf.out.db.KeyValueDBException;
import org.cricketmsf.out.db.SqlDBIface;

/**
 *
 * @author Grzegorz Skorupa <g.skorupa at gmail.com>
 */
public class H2RemoteCommandsDB extends H2RemoteDB implements SqlDBIface, ActuatorCommandsDBIface, Adapter {

    //TODO: store hexpayload
    
    @Override
    public void loadProperties(HashMap<String, String> properties, String adapterName) {
        super.loadProperties(properties, adapterName);
    }

    @Override
    public void addTable(String tableName, int maxSize, boolean persistent) throws KeyValueDBException {

        String query;
        String indexQuery = null;
        StringBuilder sb = new StringBuilder();
        switch (tableName) {
            case "commands":
                sb.append("create table commands (")
                        .append("id bigint,")
                        .append("origin varchar,")
                        .append("payload varchar,")
                        .append("hexpayload varchar,")
                        .append("createdat bigint)");
                indexQuery = "create index idxcommands on commands(origin);";
                break;
            case "commandslog":
                sb.append("create table commandslog (")
                        .append("id bigint,")
                        .append("origin varchar,")
                        .append("payload varchar,")
                        .append("hexpayload varchar,")
                        .append("createdat bigint)");
                indexQuery = "create index idxcommandslog on commandslog(origin);";
                break;
            default:
                throw new KeyValueDBException(KeyValueDBException.CANNOT_CREATE, "unable to create table " + tableName);
        }
        query = sb.toString();
        try (Connection conn = getConnection()) {
            PreparedStatement pst;
            pst = conn.prepareStatement(query);
            pst.executeUpdate();
            pst.close();
            if (indexQuery != null) {
                PreparedStatement pst2 = conn.prepareStatement(indexQuery);
                pst2.executeUpdate();
                pst2.close();
            }
            conn.close();
        } catch (SQLException e) {
            throw new KeyValueDBException(e.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public void putDeviceCommand(String deviceEUI, ActuatorEvent commandEvent) throws ThingsDataException {
        String query = "insert into commands (id,origin,payload,hexpayload,createdat) values (?,?,?,?,?);";
        //String query2 = "update commands set id=?,payload=?,createdat=? where category=? and type=? and origin=?";
        String query2 = "merge into commands key (origin) values (?,?,?,?,?)";
        String command = (String) commandEvent.getData().get("payload");
        boolean overwriteMode = false;
        if (command.startsWith("&")) {
            overwriteMode = false;
        } else if (command.startsWith("#")) {
            query = query2;
            overwriteMode = true;
        }
        command = command.substring(1);
        try (Connection conn = getConnection()) {
            PreparedStatement pst;
            pst = conn.prepareStatement(query);
            pst.setLong(1, commandEvent.getId());
            pst.setString(2, deviceEUI);
            pst.setString(3, command);
            pst.setString(4, command);
            pst.setLong(5, commandEvent.getCreatedAt());
            pst.executeUpdate();
            pst.close();
            conn.close();
        } catch (SQLException e) {
            throw new ThingsDataException(e.getErrorCode(), e.getMessage());
        }
        /*if (!overwriteMode) {
            try (Connection conn = getConnection()) {
                PreparedStatement pst;
                pst = conn.prepareStatement(query);
                pst.setLong(1, commandEvent.getId());
                pst.setString(2, commandEvent.getCategory());
                pst.setString(3, commandEvent.getType());
                pst.setString(4, deviceEUI);
                pst.setString(5, (String) commandEvent.getPayload());
                pst.setLong(6, commandEvent.getCreatedAt());
                pst.executeUpdate();
                pst.close();
                conn.close();
            } catch (SQLException e) {
                throw new ThingsDataException(e.getErrorCode(), e.getMessage());
            }
        } else {
            try (Connection conn = getConnection()) {
                PreparedStatement pst;
                pst = conn.prepareStatement(query2);
                pst.setLong(1, commandEvent.getId());
                pst.setString(2, (String) commandEvent.getPayload());
                pst.setLong(3, commandEvent.getCreatedAt());
                pst.setString(4, commandEvent.getCategory());
                pst.setString(5, commandEvent.getType());
                pst.setString(6, commandEvent.getOrigin());
                pst.executeUpdate();
                pst.close();
                conn.close();
            } catch (SQLException e) {
                throw new ThingsDataException(e.getErrorCode(), e.getMessage());
            }
        }*/
    }

    @Override
    public ActuatorEvent getFirstCommand(String deviceEUI) throws ThingsDataException {
        String query = "select id,payload,hexPayload,createdat from commands where origin like ? order by createdat limit 1";
        ActuatorEvent result = null;
        try (Connection conn = getConnection()) {
            PreparedStatement pst;
            pst = conn.prepareStatement(query);
            pst.setString(1, "%@" + deviceEUI);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                result = new ActuatorEvent(deviceEUI, rs.getString(2), rs.getString(3));
                result.setId(rs.getLong(1));
                result.setCreatedAt(rs.getLong(5));
            }
            pst.close();
            conn.close();
            return result;
        } catch (SQLException e) {
            throw new ThingsDataException(ThingsDataException.HELPER_EXCEPTION, e.getMessage());
        }
    }

    @Override
    public ActuatorEvent previewDeviceCommand(String deviceEUI, ActuatorEvent commandEvent) throws ThingsDataException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void clearAllCommands(String deviceEUI, long checkPoint) throws ThingsDataException {
        String query = "delete from commands where origin like ? and createdat<?";
        try (Connection conn = getConnection()) {
            PreparedStatement pst;
            pst = conn.prepareStatement(query);
            pst.setString(1, "%@" + deviceEUI);
            pst.setLong(2, checkPoint);
            //pst.setTimestamp(2, new java.sql.Timestamp(checkPoint));
            pst.executeUpdate();
            pst.close();
            conn.close();
        } catch (SQLException e) {
            throw new ThingsDataException(ThingsDataException.BAD_REQUEST, e.getMessage());
        }
    }

    @Override
    public void removeAllCommands(String deviceEUI) throws ThingsDataException {
        String query = "delete from commands where origin like ?";
        try (Connection conn = getConnection()) {
            PreparedStatement pst;
            pst = conn.prepareStatement(query);
            pst.setString(1, "%@" + deviceEUI);
            pst.executeUpdate();
            pst.close();
            conn.close();
        } catch (SQLException e) {
            throw new ThingsDataException(ThingsDataException.BAD_REQUEST, e.getMessage());
        }
    }

    @Override
    public List<ActuatorEvent> getAllCommands(String deviceEUI) throws ThingsDataException {
        String query = "select id,payload,hexpayload,createdat from commands where origin like ? order by createdat";
        ArrayList<ActuatorEvent> result = new ArrayList<>();
        try (Connection conn = getConnection()) {
            PreparedStatement pst;
            pst = conn.prepareStatement(query);
            pst.setString(1, "%@" + deviceEUI);
            ResultSet rs = pst.executeQuery();
            ActuatorEvent ev;
            while (rs.next()) {
                ev = new ActuatorEvent(deviceEUI, rs.getString(2), rs.getString(3));
                ev.setId(rs.getLong(1));
                ev.setCreatedAt(rs.getLong(5));
                result.add(ev);
            }
            pst.close();
            conn.close();
            return result;
        } catch (SQLException e) {
            throw new ThingsDataException(ThingsDataException.HELPER_EXCEPTION, e.getMessage());
        }
    }

    @Override
    public void putCommandLog(String deviceEUI, ActuatorEvent commandEvent) throws ThingsDataException {
        String query = "insert into commandslog (id,origin,payload,hexpayload,createdat) values (?,?,?,?,?);";
        String command=(String) commandEvent.getData().get("payload");
        if(command.startsWith("#")||command.startsWith("&")){
            command=command.substring(1);
        }
        try (Connection conn = getConnection()) {
            PreparedStatement pst;
            pst = conn.prepareStatement(query);
            pst.setLong(1, commandEvent.getId());
            pst.setString(2, deviceEUI);
            pst.setString(3, command);
            pst.setString(4, "");
            pst.setLong(5, commandEvent.getCreatedAt());
            pst.executeUpdate();
            pst.close();
            conn.close();
        } catch (SQLException e) {
            throw new ThingsDataException(e.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public void clearAllLogs(String deviceEUI, long checkPoint) throws ThingsDataException {
        String query = "delete from commandslog where origin like ? and createdat<?";
        try (Connection conn = getConnection()) {
            PreparedStatement pst;
            pst = conn.prepareStatement(query);
            pst.setString(1, "%@" + deviceEUI);
            pst.setLong(2, checkPoint);
            //pst.setTimestamp(2, new java.sql.Timestamp(checkPoint));
            pst.executeUpdate();
            pst.close();
            conn.close();
        } catch (SQLException e) {
            throw new ThingsDataException(ThingsDataException.BAD_REQUEST, e.getMessage());
        }
    }

    @Override
    public void removeAllLogs(String deviceEUI) throws ThingsDataException {
        String query = "delete from commandslog where origin like ?";
        try (Connection conn = getConnection()) {
            PreparedStatement pst;
            pst = conn.prepareStatement(query);
            pst.setString(1, "%@" + deviceEUI);
            pst.executeUpdate();
            pst.close();
            conn.close();
        } catch (SQLException e) {
            throw new ThingsDataException(ThingsDataException.BAD_REQUEST, e.getMessage());
        }
    }

    @Override
    public List<ActuatorEvent> getAllLogs(String deviceEUI) throws ThingsDataException {
        String query = "select id,payload,hexpayload,createdat from commandslog where origin like ? order by createdat";
        ArrayList<ActuatorEvent> result = new ArrayList<>();
        try (Connection conn = getConnection()) {
            PreparedStatement pst;
            pst = conn.prepareStatement(query);
            pst.setString(1, "%@" + deviceEUI);
            ResultSet rs = pst.executeQuery();
            ActuatorEvent ev;
            while (rs.next()) {
                ev = new ActuatorEvent(deviceEUI, rs.getString(2), rs.getString(3));
                ev.setId(rs.getLong(1));
                ev.setCreatedAt(rs.getLong(5));
                result.add(ev);
            }
            pst.close();
            conn.close();
            return result;
        } catch (SQLException e) {
            throw new ThingsDataException(ThingsDataException.HELPER_EXCEPTION, e.getMessage());
        }
    }

    @Override
    public void removeCommand(long id) throws ThingsDataException {
        String query = "delete from commands where id=?";
        try (Connection conn = getConnection()) {
            PreparedStatement pst;
            pst = conn.prepareStatement(query);
            pst.setLong(1, id);
            pst.executeUpdate();
            pst.close();
            conn.close();
        } catch (SQLException e) {
            throw new ThingsDataException(ThingsDataException.BAD_REQUEST, e.getMessage());
        }
    }

}
