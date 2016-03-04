import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class AckServlet extends HttpServlet
{
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException
  {
    System.out.println(req + ", " + req.getProtocol());

    resp.setStatus(200);
    resp.getWriter().println("Ack");
  }
}
