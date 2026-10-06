package dev.support;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.env.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.MailSendException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"DB_URL=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class SupportTest {
  @Autowired MockMvc http;
  @Autowired JdbcTemplate db;
  @Autowired ConfigurableEnvironment env;
  @Autowired SupportService service;
  @MockitoSpyBean Receipts receipts;
  Map<String,Object> settings;
  Path previews;
  String payload="{\"name\":\"Sam\",\"email\":\"sam@example.com\",\"subject\":\"Sign in\",\"message\":\"I cannot sign in today.\"}";
  @BeforeEach void setup() throws Exception {
    db.update("DELETE FROM outbox");db.update("DELETE FROM requests");db.update("DELETE FROM limits");
    previews=Files.createTempDirectory("support-preview-");
    settings=new HashMap<>(Map.of("MAIL_MODE","log","APP_ENV","development","PREVIEW_DIR",previews.toString(),"ADMIN_PASSWORD","test-password-123456789"));
    env.getPropertySources().addFirst(new MapPropertySource("case",settings));
  }
  @AfterEach void cleanup() throws Exception {env.getPropertySources().remove("case");try(var paths=Files.walk(previews)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
  String save() {return service.create(new SupportController.Submission("Sam","sam@example.com","Sign in","I cannot sign in today."),"127.0.0.1");}
  @Test void savesRequestAndLocalMimeReceipt() throws Exception {
    http.perform(post("/requests").contentType("application/json").content(payload)).andExpect(status().isCreated()).andExpect(jsonPath("$.email_status").value("previewed"));
    assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM requests",Integer.class));
    try(var files=Files.list(previews)){Path file=files.findFirst().orElseThrow();if(Files.getFileStore(file).supportsFileAttributeView("posix"))assertEquals("rw-------",java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));String content=Files.readString(file);assertTrue(content.contains("sam@example.com"));assertTrue(content.contains("Your support request"));}
  }
  @Test void rejectsUnknownFields() throws Exception {http.perform(post("/requests").contentType("application/json").content(payload.replace("\"Sam\"","\"Sam\",\"to\":\"other@example.com\""))).andExpect(status().isBadRequest());assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM requests",Integer.class));}
  @Test void rejectsInvalidEmailAndShortTrimmedSubject() throws Exception {
    http.perform(post("/requests").contentType("application/json").content(payload.replace("sam@example.com","not-an-address"))).andExpect(status().isBadRequest());
    http.perform(post("/requests").contentType("application/json").content(payload.replace("Sign in","  x  "))).andExpect(status().isBadRequest());
    http.perform(post("/requests").contentType("application/json").content(payload.replace("Sign in","Sign\\r\\nBcc: other@example.com"))).andExpect(status().isBadRequest());
  }
  @Test void recipientLimitIsPersistentAndDoesNotCreateFourthRequest() throws Exception {for(int i=0;i<3;i++)save();http.perform(post("/requests").contentType("application/json").content(payload)).andExpect(status().isTooManyRequests());assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM requests",Integer.class));}
  @Test void clientLimitIgnoresForgedProxyHeaders() throws Exception {
    for(int i=0;i<12;i++)service.create(new SupportController.Submission("Sam","s"+i+"@example.com","Issue","Issue details here."),"127.0.0.1");
    http.perform(post("/requests").header("X-Forwarded-For","1.2.3.4").contentType("application/json").content(payload)).andExpect(status().isTooManyRequests());
  }
  @Test void operatorDataNeedsCorrectCredentials() throws Exception {
    save();http.perform(get("/admin")).andExpect(status().isUnauthorized());
    String auth=Base64.getEncoder().encodeToString("admin:test-password-123456789".getBytes());
    http.perform(get("/admin").header("Authorization","Basic "+auth)).andExpect(status().isOk()).andExpect(jsonPath("$[0].EMAIL").value("sam@example.com"));
  }
  @Test void rejectsForeignOriginAndOversizedBody() throws Exception {
    http.perform(post("/requests").header("Origin","https://elsewhere.example").contentType("application/json").content(payload)).andExpect(status().isForbidden());
    http.perform(post("/requests").contentType("application/json").content("x".repeat(16385))).andExpect(status().isPayloadTooLarge());
  }
  @Test void missingCredentialsKeepSavedRecordAndCanRetrySafely() {
    settings.put("MAIL_MODE","sandbox");String id=save();receipts.send(id);assertEquals("failed",state(id));
    settings.put("MAIL_MODE","log");receipts.retry();assertEquals("previewed",state(id));
  }
  @Test void interruptedSmtpIsUnknownAndNotRetried() {
    settings.put("MAIL_MODE","production");String id=save();
    var sender=spy(new JavaMailSenderImpl());doThrow(new MailSendException("Timeout")).when(sender).send(any(jakarta.mail.internet.MimeMessage.class));
    doReturn(sender).when(receipts).sender();receipts.send(id);receipts.retry();receipts.send(id);
    assertEquals("unknown",state(id));verify(sender,times(1)).send(any(jakarta.mail.internet.MimeMessage.class));
  }
  @Test void smtpModesUseSeparateCredentialsAndRequireTls() {
    settings.putAll(Map.of("MAIL_MODE","sandbox","MAILTRAP_SANDBOX_USER","inbox-user","MAILTRAP_SANDBOX_PASSWORD","inbox-pass","MAILTRAP_PRODUCTION_TOKEN","live-token"));
    var sandbox=receipts.sender();assertEquals("sandbox.smtp.mailtrap.io",sandbox.getHost());assertEquals(2525,sandbox.getPort());assertEquals("inbox-user",sandbox.getUsername());
    settings.put("MAIL_MODE","production");var live=receipts.sender();assertEquals("api",live.getUsername());assertEquals("live-token",live.getPassword());assertEquals("live.smtp.mailtrap.io",live.getHost());assertEquals(587,live.getPort());
    assertEquals("true",live.getJavaMailProperties().getProperty("mail.smtp.starttls.required"));assertEquals("true",live.getJavaMailProperties().getProperty("mail.smtp.ssl.checkserveridentity"));
  }
  @Test void deployedAppRejectsOfflinePreviews() {settings.put("APP_ENV","production");assertThrows(IllegalArgumentException.class,()->receipts.sender());}
  @Test void concurrentRecipientSubmissionsRespectLimit() throws Exception {
    var accepted=new AtomicInteger();try(var pool=Executors.newFixedThreadPool(8)){
      var futures=new ArrayList<Future<?>>();for(int i=0;i<8;i++)futures.add(pool.submit(()->{try{save();accepted.incrementAndGet();}catch(org.springframework.web.server.ResponseStatusException expected){assertEquals(429,expected.getStatusCode().value());}}));
      for(var future:futures)future.get();
    }assertEquals(3,accepted.get());assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM outbox",Integer.class));
  }
  String state(String id){return db.queryForObject("SELECT status FROM outbox WHERE id=?",String.class,id);}
}
