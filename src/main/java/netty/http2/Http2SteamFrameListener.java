package netty.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2LocalFlowController;
import io.netty.handler.codec.http2.Http2Stream;
import java.util.AbstractMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;


public class Http2SteamFrameListener extends Http2EventAdapter
{
  private final Http2Connection _connection;
  private final Http2LocalFlowController _flowController;
  private final Http2Connection.PropertyKey _bufferKey;

  public Http2SteamFrameListener(Http2Connection connection, Http2LocalFlowController flowController)
  {
    _connection = connection;
    _flowController = flowController;
    _bufferKey = connection.newKey();
  }

  @Override
  public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception
  {
    Http2Stream stream = _connection.stream(streamId);
    if (stream.getProperty(_bufferKey) == null)
    {
      stream.setProperty(_bufferKey, new LinkedList<>());
    }
    Queue<Map.Entry<ByteBuf, Integer>> buffer = stream.getProperty(_bufferKey);
    buffer.add(new AbstractMap.SimpleEntry<>(data, padding));
    return 0;
  }

  private class BufferedWriter
  {
    private final int _streamId;

    public BufferedWriter(int streamId)
    {
      _streamId = streamId;
    }

    public void onWritePossible() throws Exception
    {
      Http2Stream stream = _connection.stream(_streamId);
      Queue<Map.Entry<ByteBuf, Integer>> buffer = stream.getProperty(_bufferKey);
      if (buffer == null || buffer.isEmpty())
      {
        return;
      }

      for (Map.Entry<ByteBuf, Integer> entry : buffer)
      {
        _flowController.consumeBytes(stream, entry.getKey().readableBytes() + entry.getValue());
      }
    }
  }
}
