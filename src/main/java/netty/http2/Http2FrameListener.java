package netty.http2;

import com.linkedin.common.util.None;
import com.linkedin.data.ByteString;
import com.linkedin.r2.RemoteInvocationException;
import com.linkedin.r2.message.stream.StreamResponse;
import com.linkedin.r2.message.stream.StreamResponseBuilder;
import com.linkedin.r2.message.stream.entitystream.EntityStream;
import com.linkedin.r2.message.stream.entitystream.EntityStreams;
import com.linkedin.r2.message.stream.entitystream.WriteHandle;
import com.linkedin.r2.message.stream.entitystream.Writer;
import com.linkedin.r2.transport.http.common.HttpConstants;
import com.linkedin.r2.util.Timeout;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Http2FrameListener extends Http2EventAdapter
{
  private static final Logger LOG = LoggerFactory.getLogger(Http2FrameListener.class);

  private final Http2Connection _connection;
  private final Http2Connection.PropertyKey _writerKey;
  private final long _maxContentLength;

  public Http2FrameListener(Http2Connection connection, long maxContentLength)
  {
    _connection = connection;
    _writerKey = connection.newKey();
    _maxContentLength = maxContentLength;
  }

  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
      short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
    onHeadersRead(ctx, streamId, headers, padding, endStream);
  }

  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
      boolean endOfStream) throws Http2Exception
  {
    // Ignores response for the upgrade request
    if (streamId == Http2CodecUtil.HTTP_UPGRADE_STREAM_ID)
    {
      return;
    }
    final StreamResponseBuilder builder = new StreamResponseBuilder();

    // Process HTTP/2 pseudo headers
    if (headers.status() != null)
    {
      builder.setStatus(Integer.parseInt(headers.status().toString()));
    }
    if (headers.authority() != null)
    {
      builder.addHeaderValue(HttpHeaderNames.HOST.toString(), headers.authority().toString());
    }

    // Process other HTTP headers
    for (Map.Entry<CharSequence, CharSequence> header : headers)
    {
      if (Http2Headers.PseudoHeaderName.isPseudoHeader(header.getKey()))
      {
        // Do no set HTTP/2 pseudo headers to response
        continue;
      }

      final String key = header.getKey().toString();
      final String value = header.getValue().toString();
      if (key.equalsIgnoreCase(HttpConstants.RESPONSE_COOKIE_HEADER_NAME))
      {
        builder.addCookie(value);
      }
      else
      {
        builder.unsafeAddHeaderValue(key, value);
      }
    }

    final TimeoutBufferedWriter writer = new TimeoutBufferedWriter(ctx, streamId, _maxContentLength, null);

    // Associate writer to the stream
    if (_connection.stream(streamId).setProperty(_writerKey, writer) != null)
    {
      throw new IllegalStateException("Another writer is already associated with current stream ID " + streamId);
    }

    // Prepares StreamResponse for the channel pipeline
    EntityStream entityStream = EntityStreams.newEntityStream(writer);
    StreamResponse response = builder.build(entityStream);
    ctx.fireChannelRead(response);
  }

  @Override
  public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream)
      throws Http2Exception
  {
    // Ignores response for the upgrade request
    if (streamId == Http2CodecUtil.HTTP_UPGRADE_STREAM_ID)
    {
      return data.readableBytes() + padding;
    }
    final TimeoutBufferedWriter writer = _connection.stream(streamId).getProperty(_writerKey);
    if (writer == null)
    {
      throw new IllegalStateException("No writer is associated with current stream ID " + streamId);
    }
    writer.onDataRead(data, endOfStream);
    if (endOfStream)
    {
      _connection.stream(streamId).removeProperty(_writerKey);
    }
    return padding;
  }

  @Override
  public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
    if (Http2Error.NO_ERROR != Http2Error.valueOf(errorCode))
    {
      LOG.warn("Stream with ID {} encountered reset stream from remote with error {}", streamId, Http2Error.valueOf(errorCode));
      return;
    }
    LOG.debug("Stream with ID {} encountered reset stream from remove", streamId);
  }

  @Override
  public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
    ctx.fireChannelRead(settings);
  }

  /**
   * A buffered writer that stops reading from socket if buffered bytes is larger than high water mark
   * and resumes reading from socket if buffered bytes is smaller than low water mark.
   */
  class TimeoutBufferedWriter implements Writer
  {
    private final ChannelHandlerContext _ctx;
    private final int _streamId;
    private final long _maxContentLength;
    private WriteHandle _wh;
    private boolean _lastChunkReceived;
    private int _totalBytesWritten;
    private final Queue<ByteString> _buffer;
    //private final Timeout<None> _timeout;
    private volatile Throwable _failureBeforeInit;

    TimeoutBufferedWriter(final ChannelHandlerContext ctx, int streamId,
        long maxContentLength, final Timeout<None> timeout)
    {
      _ctx = ctx;
      _streamId = streamId;
      _maxContentLength = maxContentLength;
      _failureBeforeInit = null;
      _lastChunkReceived = false;
      _totalBytesWritten = 0;
      _buffer = new LinkedList<>();

      // schedule a timeout to set the stream and inform use
      Runnable timeoutTask = () -> _ctx.executor().execute(()
          -> fail(new TimeoutException("Timeout while receiving the response entity.")));
      //_timeout = timeout;
      //_timeout.addTimeoutTask(timeoutTask);
    }

    @Override
    public void onInit(WriteHandle wh)
    {
      _wh = wh;
    }

    @Override
    public void onWritePossible()
    {
      if (_failureBeforeInit != null)
      {
        fail(_failureBeforeInit);
        return;
      }

      if (_ctx.executor().inEventLoop())
      {
        doWrite();
      }
      else
      {
        _ctx.executor().execute(() -> doWrite());
      }
    }

    @Override
    public void onAbort(Throwable ex)
    {
      //_timeout.getItem();
      error(Http2Exception.streamError(_streamId, Http2Error.CANCEL,
          "Encountered entity stream abort event while reading from HTTP/2 stream {}", _streamId));
    }

    public void onDataRead(ByteBuf data, boolean end) throws TooLongFrameException
    {
      if (data.readableBytes() + _totalBytesWritten > _maxContentLength)
      {
        fail(new TooLongFrameException("HTTP content length exceeded " + _maxContentLength + " bytes."));
      }
      else
      {
        if (data.isReadable())
        {
          final InputStream is = new ByteBufInputStream(data);
          final ByteString bytes;
          try
          {
            bytes = ByteString.read(is, data.readableBytes());
          }
          catch (IOException ex)
          {
            fail(ex);
            return;
          }
          _buffer.add(bytes);
        }
        if (end)
        {
          _lastChunkReceived = true;
          //_timeout.getItem();
        }
        if (_wh != null)
        {
          doWrite();
        }
      }
    }

    public void fail(Throwable cause)
    {
      //_timeout.getItem();
      if (_wh != null)
      {
        _wh.error(new RemoteInvocationException(cause));
      }
      else
      {
        _failureBeforeInit = cause;
      }

      if (cause instanceof Http2Exception)
      {
        error((Http2Exception) cause);
      }
      else
      {
        error(Http2Exception.streamError(_streamId, Http2Error.CANCEL, cause, "Encountered writer error"));
      }
    }

    private void doWrite()
    {
      while(_wh.remaining() > 0)
      {
        if (!_buffer.isEmpty())
        {
          final ByteString bytes = _buffer.poll();
          _wh.write(bytes);
          _totalBytesWritten += bytes.length();
          try
          {
            Http2Stream stream = _connection.stream(_streamId);
            _connection.local().flowController().consumeBytes(stream, bytes.length());
          }
          catch (Http2Exception e)
          {
            fail(e);
            return;
          }
          finally
          {
            _ctx.flush();
          }
        }
        else
        {
          if (_lastChunkReceived)
          {
            _wh.done();
          }
          break;
        }
      }
    }

    private void error(Http2Exception cause)
    {
      throw new RuntimeException(cause);
    }
  }
}
