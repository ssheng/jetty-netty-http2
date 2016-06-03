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
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;


class Http2StreamCodec extends Http2ConnectionHandler
{
  private static final int NO_PADDING = 0;
  private static final boolean NOT_END_STREAM = false;
  private static final boolean END_STREAM = true;

  protected Http2StreamCodec(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
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
    Http2Headers http2Headers = new DefaultHttp2Headers()
        .authority(streamRequest.getHeader("HOST"))
        .method("GET")
        .path("/any")
        .scheme("http");
    BufferedReader reader = new BufferedReader(ctx, encoder, streamId);
    streamRequest.getEntityStream().setReader(reader);
    encoder.writeHeaders(ctx, streamId, http2Headers, NO_PADDING, NOT_END_STREAM, promise)
        .addListener(future -> reader.request());
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

    @Override
    public void onInit(ReadHandle rh)
    {
      _readHandle = rh;
    }

    @Override
    public void onDataAvailable(final ByteString data)
    {
      ByteBuf content = Unpooled.wrappedBuffer(data.asByteBuffer());
      _encoder.writeData(_ctx, _streamId, content, NO_PADDING, NOT_END_STREAM, _ctx.channel().newPromise())
          .addListener(future -> _readHandle.request(1));

      _notFlushedBytes += data.length();
      _notFlushedChunks++;
      if (_notFlushedBytes >= FLUSH_THRESHOLD || _notFlushedChunks == MAX_BUFFERED_CHUNKS)
      {
        flush();
        _notFlushedBytes = 0;
        _notFlushedChunks = 0;
      }
    }

    @Override
    public void onDone()
    {
      _encoder.writeData(_ctx, _streamId, Unpooled.EMPTY_BUFFER, NO_PADDING, END_STREAM, _ctx.channel().voidPromise());
      flush();
    }

    @Override
    public void onError(Throwable cause)
    {
      Http2StreamCodec.this.onError(_ctx, Http2Exception.streamError(_streamId, Http2Error.CANCEL, cause,
          "Encountered entity stream error event while writing to HTTP/2 stream {}", _streamId));
    }

    private void request()
    {
      _readHandle.request(MAX_BUFFERED_CHUNKS);
    }

    private void flush()
    {
      try
      {
        _encoder.flowController().writePendingBytes();
      }
      catch (Http2Exception e)
      {
        Http2StreamCodec.this.onError(_ctx, e);
      }
      finally
      {
        _ctx.flush();
      }
    }
  }
}
