package netty.http2;

import com.linkedin.data.ByteString;
import com.linkedin.r2.message.stream.StreamResponse;
import com.linkedin.r2.message.stream.entitystream.ReadHandle;
import com.linkedin.r2.message.stream.entitystream.Reader;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Settings;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.handler.logging.LogLevel.INFO;


public class ConcurrentHttp2ClientInitializer extends ChannelInitializer<SocketChannel>
{
  private static final Http2FrameLogger logger = new Http2FrameLogger(INFO, ConcurrentHttp2ClientInitializer.class);

  private final AtomicInteger _count = new AtomicInteger(0);

  @Override
  protected void initChannel(SocketChannel ch) throws Exception
  {
    final Http2Settings settings = new Http2Settings();
    settings.initialWindowSize(65535);
    settings.headerTableSize(4096);
    final Http2Connection connection = new DefaultHttp2Connection(false);
    final Http2StreamCodec connectionHandler = new Http2StreamCodecBuilder()
        .frameListener(new Http2FrameListener(connection, Integer.MAX_VALUE))
        //.frameLogger(logger)
        .connection(connection)
        .initialSettings(settings)
        .gracefulShutdownTimeoutMillis(30000)
        .build();
    HttpClientCodec sourceCodec = new HttpClientCodec();
    NettyHttp2ClientUpgradeCodec upgradeCodec = new NettyHttp2ClientUpgradeCodec(connectionHandler);
    HttpClientUpgradeHandler upgradeHandler = new HttpClientUpgradeHandler(sourceCodec, upgradeCodec, 65536);
    Http2UpgradeHandler initiateHandler = new Http2UpgradeHandler();

    ch.pipeline().addLast(sourceCodec, upgradeHandler, initiateHandler);
    ch.pipeline().addLast(new ChannelInboundHandlerAdapter()
    {
      @Override
      public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
      {
        if (msg instanceof Http2Settings)
        {
          ctx.fireChannelRead(msg);
          return;
        }

        StreamResponse response = (StreamResponse) msg;
        response.getEntityStream().setReader(new Reader()
        {
          ReadHandle _rh;
          int _consumed = 0;
          ScheduledExecutorService _scheduler = new ScheduledThreadPoolExecutor(1);
          ScheduledFuture<?> _future = null;

          @Override
          public void onDataAvailable(ByteString data)
          {
            _consumed += data.length();
            _rh.request(1);
          }

          @Override
          public void onDone()
          {
            _future.cancel(true);

            System.err.println("Done #" +  _count.incrementAndGet() + " consumed " + _consumed + " bytes");
          }

          @Override
          public void onError(Throwable e)
          {
            System.err.println("Error #" +  _count.incrementAndGet() + " at " + _consumed + " bytes. Caused by " + e.getClass());
          }

          @Override
          public void onInit(ReadHandle rh)
          {
            _rh = rh;
            _rh.request(1);
            _future = _scheduler.schedule(() -> onError(new TimeoutException()), 5, TimeUnit.SECONDS);
          }
        });
      }
    });
  }
}
