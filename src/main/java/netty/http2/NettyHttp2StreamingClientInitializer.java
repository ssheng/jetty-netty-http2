package netty.http2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.example.http2.helloworld.client.Http2ClientInitializer;
import io.netty.example.http2.helloworld.client.Http2SettingsHandler;
import io.netty.example.http2.helloworld.client.HttpResponseHandler;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;

import static io.netty.handler.logging.LogLevel.INFO;


public class NettyHttp2StreamingClientInitializer extends ChannelInitializer<SocketChannel>
{
  private static final Http2FrameLogger logger = new Http2FrameLogger(INFO, NettyHttp2StreamingClientInitializer.class);

  private Http2SettingsHandler settingsHandler;

  @Override
  protected void initChannel(SocketChannel ch) throws Exception
  {
    settingsHandler = new Http2SettingsHandler(ch.newPromise());
    final Http2Settings settings = new Http2Settings();
    settings.initialWindowSize(65535);
    settings.headerTableSize(4096);
    final Http2Connection connection = new DefaultHttp2Connection(false);
    //final HttpToHttp2ConnectionHandler connectionHandler = new HttpToHttp2ConnectionHandlerBuilder()
    final Http2StreamCodec connectionHandler = new Http2StreamCodecBuilder()
        .frameListener(new DelegatingDecompressorFrameListener(connection,
            new InboundHttp2ToHttpAdapterBuilder(connection).maxContentLength(Integer.MAX_VALUE).propagateSettings(true)
                .build()))
        .frameLogger(logger)
        .connection(connection)
        .initialSettings(settings)
        .gracefulShutdownTimeoutMillis(30000)
        .build();
    HttpClientCodec sourceCodec = new HttpClientCodec();
    NettyHttp2ClientUpgradeCodec upgradeCodec = new NettyHttp2ClientUpgradeCodec(connectionHandler);
    HttpClientUpgradeHandler upgradeHandler = new HttpClientUpgradeHandler(sourceCodec, upgradeCodec, 65536);

    ch.pipeline().addLast(sourceCodec, upgradeHandler, new UpgradeRequestHandler());
  }

  /**
   * A handler that triggers the cleartext upgrade to HTTP/2 by sending an initial HTTP request.
   */
  private final class UpgradeRequestHandler extends ChannelInboundHandlerAdapter
  {
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
      DefaultFullHttpRequest upgradeRequest =
          new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.OPTIONS, "/");
      ctx.writeAndFlush(upgradeRequest).sync();

      ctx.fireChannelActive();

      // Done with this handler, remove it from the pipeline.
      ctx.pipeline().remove(this);

      ctx.pipeline().addLast(settingsHandler);
    }
  }

  public Http2SettingsHandler settingsHandler() {
    return settingsHandler;
  }
}
