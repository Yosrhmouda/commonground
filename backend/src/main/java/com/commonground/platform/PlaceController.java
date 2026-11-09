package com.commonground.platform;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api")
public class PlaceController {
    private final PlaceService places;private final MediaStorage media;
    public PlaceController(PlaceService places,MediaStorage media){this.places=places;this.media=media;}
    @GetMapping("/health") public Map<String,String> health(){return Map.of("status","ok");}
    @GetMapping("/places") public Map<String,?> list(@RequestAttribute(VisitorFilter.ACTOR) String actor){return Map.of("places",places.list(actor));}
    @PostMapping(value="/places",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(@RequestAttribute(VisitorFilter.ACTOR) String actor,@RequestParam String title,@RequestParam String location,@RequestParam String city,@RequestParam String country,@RequestParam String description,@RequestParam("media") List<MultipartFile> files){return ResponseEntity.status(201).body(Map.of("id",places.create(actor,new Requests.Edit(title,location,city,country,description),files)));}
    @PostMapping("/places/{id}/edit") public Map<String,Boolean> edit(@PathVariable String id,@RequestAttribute(VisitorFilter.ACTOR) String actor,@Valid @RequestBody Requests.Edit data){places.edit(id,actor,data);return Map.of("ok",true);}
    @PostMapping("/places/{id}/cleanup") public Map<String,Boolean> organize(@PathVariable String id,@RequestAttribute(VisitorFilter.ACTOR) String actor,@Valid @RequestBody Requests.Cleanup data){places.organize(id,actor,data);return Map.of("ok",true);}
    @PostMapping("/places/{id}/like") public Map<String,Boolean> like(@PathVariable String id,@RequestAttribute(VisitorFilter.ACTOR) String actor,@RequestBody Requests.Toggle data){places.like(id,actor,data.active());return Map.of("ok",true);}
    @PostMapping("/places/{id}/join") public Map<String,Boolean> join(@PathVariable String id,@RequestAttribute(VisitorFilter.ACTOR) String actor,@RequestBody Requests.Toggle data){places.participate(id,actor,data.active());return Map.of("ok",true);}
    @PostMapping("/places/{id}/live") public Map<String,Boolean> live(@PathVariable String id,@RequestAttribute(VisitorFilter.ACTOR) String actor,@Valid @RequestBody Requests.Live data){places.live(id,actor,data);return Map.of("ok",true);}
    @PostMapping(value="/places/{id}/after",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Boolean> after(@PathVariable String id,@RequestAttribute(VisitorFilter.ACTOR) String actor,@RequestParam String result,@RequestParam("media") List<MultipartFile> files){places.result(id,actor,result,files);return Map.of("ok",true);}
    @GetMapping("/media/{id}") public ResponseEntity<Resource> download(@PathVariable String id){var file=media.read(id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.type())).contentLength(file.size()).header("Cache-Control","private, max-age=3600").body(file.resource());}
}
