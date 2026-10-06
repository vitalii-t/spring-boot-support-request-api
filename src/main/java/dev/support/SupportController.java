package dev.support;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
@RestController
public class SupportController {
  public record Submission(@NotBlank @Size(max=100) String name,@NotBlank @Email @Size(max=254) @Pattern(regexp="[^\\r\\n]+") String email,@NotBlank @Size(min=3,max=150) @Pattern(regexp="[^\\r\\n]+") String subject,@NotBlank @Size(min=10,max=2000) String message) {
    public Submission { name=name==null?null:name.trim(); email=email==null?null:email.trim(); subject=subject==null?null:subject.trim(); message=message==null?null:message.trim(); }
  }
  private final SupportService service;private final Receipts receipts;private final JdbcTemplate db;
  public SupportController(SupportService service,Receipts receipts,JdbcTemplate db){this.service=service;this.receipts=receipts;this.db=db;}
  @PostMapping("/requests") @ResponseStatus(HttpStatus.CREATED)
  public Map<String,String> submit(@Valid @RequestBody Submission input,HttpServletRequest request) {
    String id=service.create(input,request.getRemoteAddr());receipts.send(id);
    return Map.of("id",id,"email_status",db.queryForObject("SELECT status FROM outbox WHERE id=?",String.class,id));
  }
  @GetMapping("/admin") public List<Map<String,Object>> list(){return db.queryForList("SELECT r.*,o.status AS email_status,o.error FROM requests r JOIN outbox o ON r.id=o.id ORDER BY r.created_at DESC LIMIT 100");}
  @GetMapping("/health") public Map<String,String> health(){return Map.of("status","ok");}
}
