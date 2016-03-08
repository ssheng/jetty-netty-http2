package jetty.server;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;


public class AckServlet extends HttpServlet
{
  private static final int RESPONSE_SIZE = 10;

  private final StringBuilder _builder = new StringBuilder();

  public AckServlet()
  {
    for (int i = 0; i < RESPONSE_SIZE; i++)
    {
      _builder.append((char)(i % 26 + 97));
    }
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException
  {
    System.out.println(req + ", " + req.getProtocol());

    resp.setStatus(200);
    resp.getWriter().println(_builder.toString());
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
  {
    System.out.println(req + ", " + req.getProtocol());
    try
    {
      while (true)
      {
        char[] bytes = new char[8192];
        if (req.getReader().read(bytes) < 0)
        {
          break;
        }
        System.err.println("Received: " + bytes);
        Thread.sleep(1000);
      }
    }
    catch (InterruptedException e)
    {
      e.printStackTrace();
    }

    resp.setStatus(200);
    //resp.getWriter().println(req.getReader().readLine());
  }

  @Override
  protected void doOptions(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException
  {
    System.out.println(req + ", " + req.getProtocol());

    resp.setStatus(200);
    resp.getWriter().println(_builder.toString());
  }
}
