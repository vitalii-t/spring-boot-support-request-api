package dev.support;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class Application {
  public static void main(String[] args) {
    Dotenv.configure().ignoreIfMissing().load().entries().forEach(e -> {
      if (System.getenv(e.getKey()) == null && System.getProperty(e.getKey()) == null) System.setProperty(e.getKey(), e.getValue());
    });
    var app = new SpringApplication(Application.class);
    boolean retry = java.util.Arrays.asList(args).contains("--retry-email");
    if (retry) app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
    var context = app.run(args);
    if (retry) { context.getBean(Receipts.class).retry(); context.close(); }
  }
}
