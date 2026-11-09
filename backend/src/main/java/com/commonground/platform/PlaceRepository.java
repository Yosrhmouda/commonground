package com.commonground.platform;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
public class PlaceRepository {
    public JdbcTemplate jdbc(){return db;}
    final JdbcTemplate db;
    public PlaceRepository(JdbcTemplate db) { this.db=db; }
    public record Place(String id, String ownerId, String organizerId, String title, String location, String city, String country,
                        String description, String stage, String organizerName, String date, String time, String timeZone,
                        Long cleanupAt, Integer target, String supplies, String result, String liveUrl, boolean liveActive,
                        long createdAt) {}
    private Place map(ResultSet r, int n) throws SQLException {
        return new Place(r.getString("id"),r.getString("owner_id"),r.getString("organizer_id"),r.getString("title"),r.getString("location"),r.getString("city"),r.getString("country"),r.getString("description"),r.getString("stage"),r.getString("organizer_name"),r.getString("cleanup_date"),r.getString("cleanup_time"),r.getString("time_zone"),r.getObject("cleanup_at",Long.class),r.getObject("volunteer_target",Integer.class),r.getString("supplies"),r.getString("result_text"),r.getString("live_url"),r.getBoolean("live_active"),r.getLong("created_at"));
    }
    public List<Place> list() { return db.query("SELECT * FROM places ORDER BY created_at DESC LIMIT 200",this::map); }
    public Place get(String id, boolean lock) {
        var rows=db.query("SELECT * FROM places WHERE id=?"+(lock?" FOR UPDATE":""),this::map,id);
        if(rows.isEmpty()) throw new ApiException(404,"This place could not be found.");
        return rows.getFirst();
    }
    public int count(String table,String id) { return db.queryForObject("SELECT COUNT(*) FROM "+safeTable(table)+" WHERE place_id=?",Integer.class,id); }
    public boolean has(String table,String id,String actor) { return db.queryForObject("SELECT COUNT(*) FROM "+safeTable(table)+" WHERE place_id=? AND actor_id=?",Integer.class,id,actor)>0; }
    public void toggle(String table,String id,String actor,boolean active) {
        String name=safeTable(table);
        if(active && !has(table,id,actor)) db.update("INSERT INTO "+name+"(place_id,actor_id) VALUES(?,?)",id,actor);
        if(!active) db.update("DELETE FROM "+name+" WHERE place_id=? AND actor_id=?",id,actor);
    }
    private String safeTable(String table) { if(!Set.of("place_likes","participants").contains(table))throw new IllegalArgumentException();return table; }
}
