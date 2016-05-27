package netty.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Exception;


public class NettyHttp2EventListenerDummy extends Http2EventAdapter
{
  @Override
  public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream)
      throws Http2Exception
  {
    if (streamId != 3) return data.readableBytes() + padding;
    throw Http2Exception.streamError(streamId, Http2Error.CANCEL, "A message sent");
    //return data.readableBytes() + padding;
  }
}
