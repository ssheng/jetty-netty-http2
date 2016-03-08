package netty.http2;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;

public class Http2StreamCodec extends Http2ConnectionHandler
{
  private static final byte[] content = new byte[64 * 1024];

  protected Http2StreamCodec(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
      Http2Settings initialSettings)
  {
    super(decoder, encoder, initialSettings);
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception
  {
    Http2ConnectionEncoder encoder = encoder();
    Http2Headers headers = new DefaultHttp2Headers();
    headers.authority("localhost");
    headers.method(HttpMethod.POST.asciiName());
    headers.path("/any0");
    headers.scheme(HttpScheme.HTTP.name());
    int streamId = connection().local().incrementAndGetNextStreamId();
    encoder.writeHeaders(ctx, streamId, headers, 0, false, ctx.channel().voidPromise());
    encoder.writeData(ctx, streamId, Unpooled.copiedBuffer(content), 0, false, ctx.channel().voidPromise());
    encoder.writeData(ctx, streamId, Unpooled.copiedBuffer(content), 0, false, ctx.channel().voidPromise());
    encoder.writeData(ctx, streamId, Unpooled.copiedBuffer(content), 0, true, ctx.channel().voidPromise());
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
  {
    if (cause instanceof Http2Exception)
    {
      Http2Exception exception = (Http2Exception) cause;
      System.err.println("Received HTTP/2 exception " + exception.getMessage());
      return;
    }
    System.err.println("Received exception " + cause.getClass());
  }
}
