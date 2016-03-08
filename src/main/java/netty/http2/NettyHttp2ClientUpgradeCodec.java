package netty.http2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.util.AsciiString;
import java.util.Collection;

import static io.netty.handler.codec.http2.Http2CodecUtil.HTTP_UPGRADE_SETTINGS_HEADER;


public class NettyHttp2ClientUpgradeCodec extends Http2ClientUpgradeCodec
{
  static final boolean SSL = System.getProperty("ssl") != null;
  static final int PORT = Integer.parseInt(System.getProperty("port", SSL? "8443" : "8080"));
  static final String HOST = System.getProperty("host", "127.0.0.1");
  static AsciiString hostName = new AsciiString(HOST + ':' + PORT);

  public NettyHttp2ClientUpgradeCodec(Http2ConnectionHandler connectionHandler)
  {
    super(connectionHandler);
  }

  public NettyHttp2ClientUpgradeCodec(String handlerName, Http2ConnectionHandler connectionHandler)
  {
    super(handlerName, connectionHandler);
  }

  @Override
  public Collection<CharSequence> setUpgradeHeaders(ChannelHandlerContext ctx,
      HttpRequest upgradeRequest) {
    upgradeRequest.headers().set(HttpHeaderNames.HOST, hostName);
    return super.setUpgradeHeaders(ctx, upgradeRequest);
  }
}
