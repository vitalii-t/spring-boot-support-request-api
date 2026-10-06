package dev.support;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
public class AccessFilter extends OncePerRequestFilter {
  private final Receipts settings;
  public AccessFilter(Receipts settings){this.settings=settings;}
  protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException {
    res.setHeader("X-Content-Type-Options","nosniff");res.setHeader("Cache-Control","no-store");
    if(req.getRequestURI().equals("/admin")) {
      String expected=settings.setting("ADMIN_PASSWORD",""),auth=req.getHeader("Authorization"),value="";
      try {if(auth!=null&&auth.startsWith("Basic "))value=new String(Base64.getDecoder().decode(auth.substring(6)),StandardCharsets.UTF_8);}catch(IllegalArgumentException ignored){}
      if(expected.length()<16||!MessageDigest.isEqual(value.getBytes(StandardCharsets.UTF_8),("admin:"+expected).getBytes(StandardCharsets.UTF_8))) {
        res.setHeader("WWW-Authenticate","Basic realm=Support");res.sendError(401);return;
      }
    }
    if(req.getMethod().equals("POST")) {
      String origin=req.getHeader("Origin");
      if(origin!=null&&!origin.equals(settings.setting("APP_URL","http://localhost:8080"))){res.sendError(403);return;}
      if(req.getContentLengthLong()>16384){res.sendError(413);return;}
      // Bound chunked bodies as well as requests declaring Content-Length.
      byte[] data=req.getInputStream().readNBytes(16385);
      if(data.length>16384){res.sendError(413);return;}
      var wrapped=new HttpServletRequestWrapper(req){
        public ServletInputStream getInputStream(){var in=new ByteArrayInputStream(data);return new ServletInputStream(){public int read(){return in.read();}public boolean isFinished(){return in.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException();}};}
        public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
      };
      chain.doFilter(wrapped,res);return;
    }
    chain.doFilter(req,res);
  }
}
