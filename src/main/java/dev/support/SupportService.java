package dev.support;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
public class SupportService {
  private final JdbcTemplate db;
  public SupportService(JdbcTemplate db) { this.db=db; }
  @Transactional
  public String create(SupportController.Submission input, String address) {
    // Serialize the tiny rate-counter transaction across requests and JVMs using the same database.
    db.queryForObject("SELECT id FROM submit_lock WHERE id=1 FOR UPDATE", Integer.class);
    long now=System.currentTimeMillis();
    db.update("DELETE FROM limits WHERE window_start < ?", now-3600000);
    limit("client:"+address,12,now);
    limit("email:"+input.email().toLowerCase(java.util.Locale.ROOT),3,now);
    String id=UUID.randomUUID().toString();
    db.update("INSERT INTO requests(id,name,email,subject,message) VALUES(?,?,?,?,?)",id,input.name().trim(),input.email().trim(),input.subject().trim(),input.message().trim());
    db.update("INSERT INTO outbox(id,status) VALUES(?, 'pending')",id);
    return id;
  }
  private void limit(String value,int max,long now) {
    String key;
    try {key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
    catch(Exception e){throw new IllegalStateException(e);}
    var hits=db.queryForList("SELECT hits FROM limits WHERE bucket=?",Integer.class,key);
    if (!hits.isEmpty() && hits.getFirst()>=max) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Please try again later.");
    if (hits.isEmpty()) db.update("INSERT INTO limits VALUES(?,?,1)",key,now);
    else db.update("UPDATE limits SET hits=hits+1 WHERE bucket=?",key);
  }
}
