package com.commonground.platform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
public class MediaStorage {
    private final Path root;
    private final JdbcTemplate db;
    public record Media(String id, String type, String name, long size) {}
    public record Download(Resource resource, String type, long size) {}
    private static final Set<String> TYPES=Set.of("image/jpeg","image/png","image/webp","image/gif","image/avif","video/mp4","video/webm","video/quicktime");
    public MediaStorage(@Value("${app.upload-dir}") String root, JdbcTemplate db) throws IOException {
        this.root=Path.of(root).toAbsolutePath().normalize(); this.db=db; Files.createDirectories(this.root);
    }
    public void validate(List<MultipartFile> files) {
        if(files==null||files.isEmpty()||files.size()>15)throw ApiException.badRequest("Choose between 1 and 15 photos or videos.");
        if(files.stream().mapToLong(MultipartFile::getSize).sum()>50L*1024*1024)throw ApiException.badRequest("Files must total 50 MB or less.");
        for(var file:files){
            if(file.isEmpty()||!TYPES.contains(Objects.toString(file.getContentType(),"")))throw ApiException.badRequest("Use JPG, PNG, WebP, GIF, AVIF, MP4, WebM or MOV files.");
            try(var in=file.getInputStream()){
                byte[] h=in.readNBytes(32); String ascii=new String(h,java.nio.charset.StandardCharsets.ISO_8859_1);
                boolean matches=switch(file.getContentType()){
                    case "image/png" -> h.length>=8&&h[0]==(byte)137&&ascii.substring(1,4).equals("PNG");
                    case "image/jpeg" -> h.length>=3&&h[0]==(byte)255&&h[1]==(byte)216&&h[2]==(byte)255;
                    case "image/gif" -> ascii.startsWith("GIF87a")||ascii.startsWith("GIF89a");
                    case "image/webp" -> ascii.startsWith("RIFF")&&ascii.length()>=12&&ascii.substring(8,12).equals("WEBP");
                    case "video/webm" -> h.length>=4&&h[0]==0x1a&&h[1]==0x45&&h[2]==(byte)0xdf&&h[3]==(byte)0xa3;
                    default -> h.length>=12&&(ascii.substring(4,8).equals("ftyp")||file.getContentType().equals("video/quicktime")&&Set.of("moov","mdat","wide").contains(ascii.substring(4,8)));
                };
                if(!matches)throw ApiException.badRequest("A file does not match its stated media type. Export it as a supported format.");
            }catch(IOException ex){throw new ApiException(500,"Could not read an uploaded file.");}
        }
    }
    public List<String> store(String place,String phase,List<MultipartFile> files) {
        var ids=new ArrayList<String>();
        validate(files);
        var written=new ArrayList<Path>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)written.forEach(MediaStorage.this::deleteQuietly);}
        });
        for(var file:files){
            String id=UUID.randomUUID().toString();Path path=root.resolve(id);written.add(path);ids.add(id);
            try(var in=file.getInputStream()){Files.copy(in,path);}catch(IOException ex){throw new ApiException(500,"Could not save media. Please try again.");}
            String name=Objects.toString(file.getOriginalFilename(),"media");name=name.substring(0,Math.min(name.length(),150));
            db.update("INSERT INTO place_media(id,place_id,phase,content_type,original_name,size_bytes,created_at) VALUES(?,?,?,?,?,?,?)",id,place,phase,file.getContentType(),name,file.getSize(),System.currentTimeMillis());
        }
        return ids;
    }
    public List<Media> forResult(String result,String phase){return db.query("SELECT m.* FROM place_media m JOIN result_media rm ON rm.media_id=m.id WHERE rm.result_id=? AND m.phase=? ORDER BY m.created_at,m.id",(r,n)->new Media(r.getString("id"),r.getString("content_type"),r.getString("original_name"),r.getLong("size_bytes")),result,phase);}
    private void deleteQuietly(Path path){try{Files.deleteIfExists(path);}catch(IOException ignored){}}
    public List<Media> list(String place,String phase){return db.query("SELECT * FROM place_media WHERE place_id=? AND phase=? ORDER BY created_at,id",(r,n)->new Media(r.getString("id"),r.getString("content_type"),r.getString("original_name"),r.getLong("size_bytes")),place,phase);}
    public Download read(String id){
        try {UUID.fromString(id);}catch(IllegalArgumentException ex){throw new ApiException(404,"Media not found.");}
        var rows=db.query("SELECT * FROM place_media WHERE id=?",(r,n)->new Media(r.getString("id"),r.getString("content_type"),r.getString("original_name"),r.getLong("size_bytes")),id);
        if(rows.isEmpty())throw new ApiException(404,"Media not found.");
        Path path=root.resolve(id).normalize();if(!path.getParent().equals(root)||!Files.isRegularFile(path))throw new ApiException(404,"Media not found.");
        try{return new Download(new UrlResource(path.toUri()),rows.getFirst().type(),rows.getFirst().size());}catch(IOException ex){throw new ApiException(500,"Media could not be loaded.");}
    }
}
