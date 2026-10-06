package dev.support;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.io.*;
import java.util.*;
import jakarta.mail.internet.InternetAddress;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
@Service
public class Receipts {
  private final JdbcTemplate db;
  private final Environment env;
  public Receipts(JdbcTemplate db,Environment env){this.db=db;this.env=env;}
  public String setting(String key,String fallback){return env.getProperty(key,fallback);}
  public JavaMailSenderImpl sender() {
    String mode=setting("MAIL_MODE","sandbox"), from=setting("MAIL_FROM","sender@example.com");
    try {var address=new InternetAddress(from,true);address.validate();if(from.contains("\r")||from.contains("\n"))throw new IllegalArgumentException();}
    catch(Exception e){throw new IllegalArgumentException("Set a valid MAIL_FROM.");}
    var sender=new JavaMailSenderImpl();
    if (mode.equals("log")) {
      if(!setting("APP_ENV","development").equals("development"))throw new IllegalArgumentException("Log previews require development mode.");
      return sender;
    }
    if(!Set.of("sandbox","production").contains(mode))throw new IllegalArgumentException("Unknown MAIL_MODE.");
    boolean live=mode.equals("production");
    String user=live?"api":setting("MAILTRAP_SANDBOX_USER",""), password=setting(live?"MAILTRAP_PRODUCTION_TOKEN":"MAILTRAP_SANDBOX_PASSWORD","");
    if(user.isBlank()||password.isBlank())throw new IllegalArgumentException("Configure credentials for the selected email mode.");
    sender.setHost(live?"live.smtp.mailtrap.io":"sandbox.smtp.mailtrap.io");sender.setPort(live?587:2525);
    sender.setUsername(user);sender.setPassword(password);
    var props=sender.getJavaMailProperties();
    props.setProperty("mail.smtp.auth","true");props.setProperty("mail.smtp.starttls.enable","true");props.setProperty("mail.smtp.starttls.required","true");props.setProperty("mail.smtp.ssl.checkserveridentity","true");
    for(String key:List.of("connectiontimeout","timeout","writetimeout"))props.setProperty("mail.smtp."+key,"10000");
    return sender;
  }
  public void send(String id) {
    if(db.update("UPDATE outbox SET status='sending',error=NULL WHERE id=? AND status IN ('pending','failed')",id)!=1)return;
    JavaMailSenderImpl sender;
    try {sender=sender();}
    catch(IllegalArgumentException e){db.update("UPDATE outbox SET status='failed',error=? WHERE id=?",e.getMessage(),id);return;}
    var row=db.queryForMap("SELECT * FROM requests WHERE id=?",id);
    try {
      var mail=sender.createMimeMessage();var helper=new MimeMessageHelper(mail,"UTF-8");
      helper.setFrom(setting("MAIL_FROM","sender@example.com"));helper.setTo(row.get("EMAIL").toString());helper.setSubject("Your support request was received");
      helper.setText("Hello "+row.get("NAME")+",\n\nReference: "+id+"\nSubject: "+row.get("SUBJECT")+"\n\n"+row.get("MESSAGE")+"\n\nThis demo records requests; it is not a staffed support service.");
      mail.saveChanges();
      String state;
      if(setting("MAIL_MODE","sandbox").equals("log")) {
        Path dir=Path.of(setting("PREVIEW_DIR",".data/mail-preview"));Files.createDirectories(dir);
        Path file=dir.resolve(id+".eml");
        if(Files.getFileStore(dir).supportsFileAttributeView("posix")){
          Files.setPosixFilePermissions(dir,PosixFilePermissions.fromString("rwx------"));
          Files.createFile(file,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } else {Files.createFile(file);}
        try(var output=Files.newOutputStream(file)){mail.writeTo(output);}state="previewed";
      } else {sender.send(mail);state="accepted";}
      db.update("UPDATE outbox SET status=?,error=NULL WHERE id=?",state,id);
    } catch(Exception e) {
      // A timeout may happen after SMTP acceptance. Never resend an ambiguous outcome automatically.
      db.update("UPDATE outbox SET status='unknown',error='Inspect provider history before taking further action.' WHERE id=?",id);
    }
  }
  public void retry(){db.queryForList("SELECT id FROM outbox WHERE status IN ('pending','failed')",String.class).forEach(this::send);}
}
