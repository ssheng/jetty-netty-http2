package jetty.server;

import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http2.HTTP2Cipher;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.NegotiatingServerConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;


public class JettyServer
{
  private static final int HTTP_PORT = 8080;
  private static final int HTTPS_PORT = 8443;

  private final Server _server;

  public JettyServer()
  {
    _server = new Server();

    // HTTP Configuration
    HttpConfiguration configuration = new HttpConfiguration();
    configuration.setSecureScheme("https");
    configuration.setSecurePort(HTTPS_PORT);
    configuration.setSendXPoweredBy(true);
    configuration.setSendServerVersion(true);
    configuration.setRequestHeaderSize(128 * 1024);

    // HTTP Connector
    ServerConnector http = new ServerConnector(
        _server,
        new HttpConnectionFactory(configuration),
        new HTTP2CServerConnectionFactory(configuration));
    http.setIdleTimeout(5000000);
    http.setPort(HTTP_PORT);
    _server.addConnector(http);

    // SSL Context Factory for HTTPS and HTTP/2
    SslContextFactory sslContextFactory = new SslContextFactory();
    sslContextFactory.setKeyStorePath("resources/etc/keystore");
    sslContextFactory.setKeyStorePassword("OBF:1vny1zlo1x8e1vnw1vn61x8g1zlu1vn4");
    sslContextFactory.setKeyManagerPassword("OBF:1u2u1wml1z7s1z7a1wnl1u2g");
    sslContextFactory.setCipherComparator(HTTP2Cipher.COMPARATOR);

    // HTTPS Configuration
    HttpConfiguration https_config = new HttpConfiguration(configuration);
    https_config.addCustomizer(new SecureRequestCustomizer());

    // HTTP/2 Connection Factory
    HTTP2ServerConnectionFactory h2 = new HTTP2ServerConnectionFactory(https_config);

    NegotiatingServerConnectionFactory.checkProtocolNegotiationAvailable();
    ALPNServerConnectionFactory alpn = new ALPNServerConnectionFactory();
    alpn.setDefaultProtocol(http.getDefaultProtocol());

    // SSL Connection Factory
    SslConnectionFactory ssl = new SslConnectionFactory(sslContextFactory,alpn.getProtocol());

    // HTTP/2 Connector
    ServerConnector http2Connector =
        new ServerConnector(_server,ssl,alpn,h2,new HttpConnectionFactory(https_config));
    http2Connector.setPort(HTTPS_PORT);
    _server.addConnector(http2Connector);

    ServletContextHandler handler = new ServletContextHandler(_server, "");
    handler.addServlet(new ServletHolder(new DelayedServlet()), "/*");
  }

  public Server instance()
  {
    return _server;
  }

  public static void main(String[] args) throws Exception
  {
    JettyServer server = new JettyServer();
    server.instance().start();
    server.instance().join();
  }
}