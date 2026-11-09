package com.commonground.platform;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.net.URI;
import java.time.*;
import java.util.*;

@Service
public class PlaceService {
    private final PlaceRepository repo; private final MediaStorage media;
    public PlaceService(PlaceRepository repo,MediaStorage media){this.repo=repo;this.media=media;}
    public record LiveView(String url,boolean active) {}
    public record ResultView(String id,String description,long createdAt,List<MediaStorage.Media> before,List<MediaStorage.Media> after) {}
    private List<ResultView> results(String id){return repo.jdbc().query("SELECT * FROM cleanup_results WHERE place_id=? ORDER BY created_at DESC,id",(r,n)->new ResultView(r.getString("id"),r.getString("description"),r.getLong("created_at"),media.forResult(r.getString("id"),"before"),media.forResult(r.getString("id"),"after")),id);}
    public record View(String id,String title,String location,String city,String country,String description,String stage,
                       String organizerName,String date,String time,String timeZone,Long cleanupAt,Integer target,String supplies,
                       String result,LiveView live,long createdAt,List<MediaStorage.Media> before,List<MediaStorage.Media> after,
                       int likes,boolean liked,int participants,boolean joined,boolean mine,boolean organizerMine,List<ResultView> results) {}
    @Transactional(readOnly=true)
    public List<View> list(String actor){return repo.list().stream().map(p->view(p,actor)).toList();}
    private View view(PlaceRepository.Place p,String actor){return new View(p.id(),p.title(),p.location(),p.city(),p.country(),p.description(),p.stage(),p.organizerName(),p.date(),p.time(),p.timeZone(),p.cleanupAt(),p.target(),p.supplies(),p.result(),p.liveUrl()==null?null:new LiveView(p.liveUrl(),p.liveActive()),p.createdAt(),media.list(p.id(),"before"),media.list(p.id(),"after"),repo.count("place_likes",p.id()),repo.has("place_likes",p.id(),actor),repo.count("participants",p.id()),repo.has("participants",p.id(),actor),actor.equals(p.ownerId()),actor.equals(p.organizerId()),results(p.id()));}
    private String required(String value,int max){if(value==null||value.isBlank()||value.length()>max)throw ApiException.badRequest("Complete all fields within the allowed length.");return value.trim();}
    private String country(String value){String c=required(value,2).toUpperCase(Locale.ROOT);if(!Set.of(Locale.getISOCountries()).contains(c))throw ApiException.badRequest("Choose a valid country.");return c;}
    @Transactional
    public String create(String actor,Requests.Edit data,List<MultipartFile> files){
        String title=required(data.title(),100),location=required(data.location(),180),city=required(data.city(),100),country=country(data.country()),description=required(data.description(),1500);
        media.validate(files);String id=UUID.randomUUID().toString();
        repo.jdbc().update("INSERT INTO places(id,owner_id,title,location,city,country,description,stage,created_at) VALUES(?,?,?,?,?,?,?,'reported',?)",id,actor,title,location,city,country,description,System.currentTimeMillis());
        media.store(id,"before",files);return id;
    }
    @Transactional
    public void edit(String id,String actor,Requests.Edit data){var p=repo.get(id,true);if(!actor.equals(p.ownerId()))throw new ApiException(403,"Only the reporter can edit this place.");repo.jdbc().update("UPDATE places SET title=?,location=?,city=?,country=?,description=? WHERE id=?",required(data.title(),100),required(data.location(),180),required(data.city(),100),country(data.country()),required(data.description(),1500),id);}
    @Transactional
    public void organize(String id,String actor,Requests.Cleanup data){
        var p=repo.get(id,true);if(!p.stage().equals("reported"))throw new ApiException(409,"A cleanup is already organized here. Refresh to join it.");
        if(data.target()<1||data.target()>500)throw ApiException.badRequest("Set a volunteer goal between 1 and 500.");
        ZonedDateTime when;
        try{
            var local=LocalDateTime.of(LocalDate.parse(data.date()),LocalTime.parse(data.time()));var zone=ZoneId.of(data.timeZone());
            var offsets=zone.getRules().getValidOffsets(local);
            if(offsets.size()!=1)throw ApiException.badRequest("That local time is skipped or repeated by daylight saving. Choose an unambiguous meeting time.");
            when=ZonedDateTime.ofStrict(local,offsets.getFirst(),zone);
        }catch(DateTimeException ex){throw ApiException.badRequest("Choose a valid date, time and IANA time zone.");}
        if(!when.toInstant().isAfter(Instant.now()))throw ApiException.badRequest("Choose a future cleanup time.");
        repo.jdbc().update("UPDATE places SET stage='organized',organizer_id=?,organizer_name=?,cleanup_date=?,cleanup_time=?,time_zone=?,cleanup_at=?,volunteer_target=?,supplies=? WHERE id=?",actor,required(data.organizerName(),100),data.date(),data.time(),data.timeZone(),when.toInstant().toEpochMilli(),data.target(),required(data.supplies(),1500),id);
        repo.toggle("participants",id,actor,true);
    }
    @Transactional
    public void like(String id,String actor,boolean active){repo.get(id,true);repo.toggle("place_likes",id,actor,active);}
    @Transactional
    public void participate(String id,String actor,boolean active){var p=repo.get(id,true);if(!p.stage().equals("organized"))throw ApiException.badRequest("This cleanup is not open for participation.");repo.toggle("participants",id,actor,active);}
    @Transactional
    public void result(String id,String actor,String result,List<MultipartFile> files,List<String> beforeIds){
        var p=repo.get(id,true);if(!actor.equals(p.organizerId())&&!repo.has("participants",id,actor))throw new ApiException(403,"Join the cleanup before sharing its result.");
        if(p.stage().equals("reported"))throw ApiException.badRequest("Organize a cleanup first.");
        String text=required(result,1500);
        var available=media.list(id,"before").stream().map(MediaStorage.Media::id).toList();
        // Older clients omit beforeIds: associate the original report's complete gallery.
        var selected=beforeIds==null?available:beforeIds.stream().distinct().toList();
        if(selected.isEmpty()||selected.size()>15||!available.containsAll(selected))throw ApiException.badRequest("Select 1 to 15 before photos or videos from this cleanup.");
        // Preserve earlier uploads created before comparison groups were introduced.
        var oldAfter=media.list(id,"after");
        if(!oldAfter.isEmpty()&&results(id).isEmpty()){
            String legacyId=UUID.randomUUID().toString();
            repo.jdbc().update("INSERT INTO cleanup_results(id,place_id,description,created_at) VALUES(?,?,?,?)",legacyId,id,p.result()==null?"Earlier cleanup result":p.result(),System.currentTimeMillis()-1);
            for(String mediaId:available)repo.jdbc().update("INSERT INTO result_media(result_id,media_id) VALUES(?,?)",legacyId,mediaId);
            for(var item:oldAfter)repo.jdbc().update("INSERT INTO result_media(result_id,media_id) VALUES(?,?)",legacyId,item.id());
        }
        var uploaded=media.store(id,"after",files);
        String resultId=UUID.randomUUID().toString();
        repo.jdbc().update("INSERT INTO cleanup_results(id,place_id,description,created_at) VALUES(?,?,?,?)",resultId,id,text,System.currentTimeMillis());
        for(String mediaId:selected)repo.jdbc().update("INSERT INTO result_media(result_id,media_id) VALUES(?,?)",resultId,mediaId);
        for(String mediaId:uploaded)repo.jdbc().update("INSERT INTO result_media(result_id,media_id) VALUES(?,?)",resultId,mediaId);
        repo.jdbc().update("UPDATE places SET stage='cleaned',result_text=?,completed_at=?,live_active=FALSE WHERE id=?",text,System.currentTimeMillis(),id);
    }
    @Transactional
    public void live(String id,String actor,Requests.Live data){
        var p=repo.get(id,true);if(!actor.equals(p.organizerId()))throw new ApiException(403,"Only the organizer can manage this livestream.");
        if(!p.stage().equals("organized"))throw ApiException.badRequest("The cleanup is no longer active.");
        if(!data.active()){repo.jdbc().update("UPDATE places SET live_active=FALSE WHERE id=?",id);return;}
        if(!data.consent())throw ApiException.badRequest("Confirm filming consent before sharing the livestream.");
        String url=required(data.url(),2048);
        try{
            var uri=new URI(url);String host=Objects.toString(uri.getHost(),"").toLowerCase(Locale.ROOT).replaceFirst("^www\\.","");
            if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getUserInfo()!=null||uri.getPort()!=-1&&!Objects.equals(uri.getPort(),443)||!Set.of("youtube.com","m.youtube.com","youtu.be","facebook.com","m.facebook.com","fb.watch","instagram.com","tiktok.com","twitch.tv","vimeo.com").contains(host))throw new IllegalArgumentException();
        }catch(Exception ex){throw ApiException.badRequest("Use an HTTPS link from YouTube, Facebook, Instagram, TikTok, Twitch or Vimeo.");}
        repo.jdbc().update("UPDATE places SET live_url=?,live_active=TRUE WHERE id=?",url,id);
    }
}
