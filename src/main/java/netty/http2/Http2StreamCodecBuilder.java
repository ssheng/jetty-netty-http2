package netty.http2;

import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Settings;


public class Http2StreamCodecBuilder extends AbstractHttp2ConnectionHandlerBuilder<Http2StreamCodec, Http2StreamCodecBuilder>
{
  @Override
  protected Http2StreamCodec build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
      Http2Settings initialSettings)
      throws Exception
  {
    return new Http2StreamCodec(decoder, encoder, initialSettings);
  }

  @Override
  public Http2StreamCodecBuilder frameListener(Http2FrameListener frameListener)
  {
    return super.frameListener(frameListener);
  }

  @Override
  public Http2StreamCodecBuilder frameLogger(Http2FrameLogger frameLogger)
  {
    return super.frameLogger(frameLogger);
  }

  @Override
  public Http2StreamCodecBuilder connection(Http2Connection connection)
  {
    return super.connection(connection);
  }

  @Override
  public Http2StreamCodecBuilder initialSettings(Http2Settings settings) {
    return super.initialSettings(settings);
  }

  @Override
  public Http2StreamCodecBuilder gracefulShutdownTimeoutMillis(long gracefulShutdownTimeoutMillis) {
    return super.gracefulShutdownTimeoutMillis(gracefulShutdownTimeoutMillis);
  }

  @Override
  public Http2StreamCodec build() {
    return super.build();
  }
}
