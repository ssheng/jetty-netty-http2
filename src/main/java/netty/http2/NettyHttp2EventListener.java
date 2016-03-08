package netty.http2;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Stream;


public class NettyHttp2EventListener extends Http2EventAdapter
{
  private int _lastStreamId = Integer.MAX_VALUE;

  @Override
  public void onGoAwaySent(int lastStreamId, long errorCode, ByteBuf debugData) {
    System.err.println("GoAway frame sent.");
    _lastStreamId = lastStreamId;
  }

  @Override
  public void onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {
    System.err.println("GoAway frame received.");
    _lastStreamId = lastStreamId;
  }

  @Override
  public void onStreamAdded(Http2Stream stream) {
    System.err.println("Stream " + stream.id() + " added");
    if (stream.id() > _lastStreamId) {

    }
  }
}
