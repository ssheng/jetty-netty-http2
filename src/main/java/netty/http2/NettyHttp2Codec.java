package netty.http2;

import com.linkedin.data.ByteString;
import com.linkedin.r2.filter.R2Constants;
import com.linkedin.r2.message.stream.StreamRequest;
import com.linkedin.r2.message.stream.entitystream.ReadHandle;
import com.linkedin.r2.message.stream.entitystream.Reader;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.HttpConversionUtil;


class NettyHttp2Codec extends Http2ConnectionHandler
{
  private static final int NO_PADDING = 0;
  private static final boolean NOT_END_STREAM = false;
  private static final boolean END_STREAM = true;

  protected NettyHttp2Codec(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
      Http2Settings initialSettings)
  {
    super(decoder, encoder, initialSettings);
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception
  {
    if (!(msg instanceof StreamRequest))
    {
      ctx.fireExceptionCaught(new IllegalArgumentException(msg.getClass().getName() + " is not allowed"));
      return;
    }

    final Http2ConnectionEncoder encoder = encoder();
    final int streamId = connection().local().incrementAndGetNextStreamId();
    StreamRequest streamRequest = (StreamRequest)msg;
    HttpRequest nettyRequest = NettyRequestAdapter.toNettyRequest(streamRequest);
    Http2Headers http2Headers = HttpConversionUtil.toHttp2Headers(nettyRequest, true);
    encoder.writeHeaders(ctx, streamId, http2Headers, NO_PADDING, NOT_END_STREAM, promise);

    BufferedReader reader = new BufferedReader(ctx, encoder, streamId);
    streamRequest.getEntityStream().setReader(reader);
    reader.request();
  }

  @Override
  public void flush(ChannelHandlerContext ctx)
  {
    // TODO: should we optimize and not flush here and rely on BufferedReader to flush
    ctx.flush();
  }

  /**
   * A reader that has pipelining/buffered reading
   *
   * Buffering is actually done by Netty; we just enforce the upper bound of the buffering
   */
  private class BufferedReader implements Reader
  {
    private static final int MAX_BUFFERED_CHUNKS = 10;

    // this threshold is to mitigate the effect of the inter-play of Nagle's algorithm & Delayed ACK
    // when sending requests with small entity
    private static final int FLUSH_THRESHOLD = R2Constants.DEFAULT_DATA_CHUNK_SIZE;

    private final int _streamId;
    private final ChannelHandlerContext _ctx;
    private final Http2ConnectionEncoder _encoder;
    private volatile ReadHandle _readHandle;
    private int _notFlushedBytes;
    private int _notFlushedChunks;

    BufferedReader(ChannelHandlerContext ctx, Http2ConnectionEncoder encoder, int streamId)
    {
      _streamId = streamId;
      _ctx = ctx;
      _encoder = encoder;
      _notFlushedBytes = 0;
      _notFlushedChunks = 0;
    }

    public void onInit(ReadHandle rh)
    {
      _readHandle = rh;
    }

    public void onDataAvailable(final ByteString data)
    {
      ByteBuf content = Unpooled.wrappedBuffer(data.asByteBuffer());
      _encoder.writeData(_ctx, _streamId, content, NO_PADDING, NOT_END_STREAM, _ctx.channel().newPromise())
          .addListener(future -> _readHandle.request(1));

      _notFlushedBytes += data.length();
      _notFlushedChunks++;
      if (_notFlushedBytes >= FLUSH_THRESHOLD || _notFlushedChunks == MAX_BUFFERED_CHUNKS)
      {
        _ctx.flush();
        _notFlushedBytes = 0;
        _notFlushedChunks = 0;
      }
    }

    public void onDone()
    {
      _encoder.writeData(_ctx, _streamId, Unpooled.EMPTY_BUFFER, NO_PADDING, END_STREAM, _ctx.channel().voidPromise());
    }

    public void onError(Throwable e)
    {
      _ctx.fireExceptionCaught(e);
    }

    private void request()
    {
      _readHandle.request(MAX_BUFFERED_CHUNKS);
    }
  }
}
