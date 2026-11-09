package com.commonground.platform;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.*;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:platform;DB_CLOSE_DELAY=-1", "app.upload-dir=target/test-uploads"})
@AutoConfigureMockMvc
class PlatformTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    static final String ORIGIN="http://localhost:4200";
    Cookie visitor() throws Exception {return mvc.perform(get("/api/places")).andReturn().getResponse().getCookie("cg_session");}
    MockMultipartFile photo(){return new MockMultipartFile("media","park.png","image/png",Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII="));}
    String report(Cookie actor) throws Exception {
        String body=mvc.perform(multipart("/api/places").file(photo()).file(photo()).param("title","Riverside path").param("location","North entrance").param("city","Berlin").param("country","DE").param("description","Bottles and paper beside the path.").cookie(actor).header("Origin",ORIGIN)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }
    ResultActions postJson(String path,Cookie actor,String body) throws Exception {return mvc.perform(post(path).cookie(actor).header("Origin",ORIGIN).contentType("application/json").content(body));}
    String schedule="{\"organizerName\":\"Sam\",\"date\":\"2099-06-01\",\"time\":\"09:00\",\"timeZone\":\"Europe/Berlin\",\"target\":10,\"supplies\":\"Bring gloves\"}";
    JsonNode place(String id,Cookie actor) throws Exception {JsonNode all=json.readTree(mvc.perform(get("/api/places").cookie(actor)).andReturn().getResponse().getContentAsString());for(JsonNode p:all.get("places"))if(p.get("id").asText().equals(id))return p;throw new AssertionError("Place missing");}
    @Test void completeCommunityLifecycle() throws Exception {
        Cookie reporter=visitor(),organizer=visitor(),volunteer=visitor(),outsider=visitor();String id=report(reporter),base="/api/places/"+id;
        assertTrue(reporter.isHttpOnly());assertEquals(2,place(id,reporter).get("before").size());
        postJson(base+"/cleanup",organizer,schedule).andExpect(status().isOk());
        postJson(base+"/cleanup",reporter,schedule).andExpect(status().isConflict());
        JsonNode organized=place(id,organizer);assertTrue(organized.get("organizerMine").asBoolean());assertEquals(java.time.Instant.parse("2099-06-01T07:00:00Z").toEpochMilli(),organized.get("cleanupAt").asLong());
        postJson(base+"/join",volunteer,"{\"active\":true}").andExpect(status().isOk());
        postJson(base+"/join",volunteer,"{\"active\":true}").andExpect(status().isOk());
        postJson(base+"/like",volunteer,"{\"active\":true}").andExpect(status().isOk());
        postJson(base+"/like",volunteer,"{\"active\":true}").andExpect(status().isOk());
        assertEquals(2,place(id,volunteer).get("participants").asInt());assertEquals(1,place(id,volunteer).get("likes").asInt());
        String stream="{\"active\":true,\"url\":\"https://www.youtube.com/watch?v=example\",\"consent\":true}";
        postJson(base+"/live",outsider,stream).andExpect(status().isForbidden());
        postJson(base+"/live",organizer,stream).andExpect(status().isOk());
        mvc.perform(multipart(base+"/after").file(photo()).param("result","All clear").cookie(outsider).header("Origin",ORIGIN)).andExpect(status().isForbidden());
        mvc.perform(multipart(base+"/after").file(photo()).param("result","Collected three bags.").cookie(volunteer).header("Origin",ORIGIN)).andExpect(status().isOk());
        JsonNode result=place(id,reporter);assertEquals("cleaned",result.get("stage").asText());assertFalse(result.get("live").get("active").asBoolean());assertEquals(1,result.get("after").size());
        String mediaId=result.get("after").get(0).get("id").asText();mvc.perform(get("/api/media/"+mediaId)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get("/api/media/"+mediaId).header("Range","bytes=0-7")).andExpect(status().isPartialContent());
        postJson(base+"/join",outsider,"{\"active\":true}").andExpect(status().isBadRequest());
    }
    @Test void ownershipOriginsAndFilesAreValidated() throws Exception {
        Cookie owner=visitor(),other=visitor();String id=report(owner),base="/api/places/"+id;
        String edit="{\"title\":\"New title\",\"location\":\"Park\",\"city\":\"Berlin\",\"country\":\"DE\",\"description\":\"Needs cleanup\"}";
        postJson(base+"/edit",other,edit).andExpect(status().isForbidden());postJson(base+"/edit",owner,edit).andExpect(status().isOk());
        mvc.perform(post(base+"/like").cookie(owner).header("Origin","https://untrusted.example").contentType("application/json").content("{\"active\":true}")).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/places").file(new MockMultipartFile("media","fake.png","image/png","not an image".getBytes())).param("title","Test").param("location","Park").param("city","Tunis").param("country","TN").param("description","Waste").cookie(owner).header("Origin",ORIGIN)).andExpect(status().isBadRequest());
        assertEquals("New title",place(id,owner).get("title").asText());
    }
    @Test void ambiguousTimesAndUntrustedLivestreamsAreRejected() throws Exception {
        Cookie owner=visitor();String id=report(owner),base="/api/places/"+id;
        postJson(base+"/cleanup",owner,schedule.replace("2099-06-01","2030-03-10").replace("09:00","02:30").replace("Europe/Berlin","America/New_York")).andExpect(status().isBadRequest());
        postJson(base+"/cleanup",owner,schedule).andExpect(status().isOk());
        postJson(base+"/live",owner,"{\"active\":true,\"url\":\"https://youtube.com.evil.example/live\",\"consent\":true}").andExpect(status().isBadRequest());
        postJson(base+"/live",owner,"{\"active\":true,\"url\":\"https://youtube.com/watch?v=x\",\"consent\":false}").andExpect(status().isBadRequest());
    }
}
