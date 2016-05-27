package netty.http2;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A handler that triggers the clear text upgrade to HTTP/2 upon channel active by sending
 * an initial HTTP request with connection upgrade headers. Calls to #write and #flush are
 * suspended before upgrade is succeeded. Handler removes itself upon upgrade success.
 */
class Http2UpgradeHandler extends ChannelDuplexHandler
{
  private static final Logger LOG = LoggerFactory.getLogger(Http2UpgradeHandler.class);

  private ChannelPromise _upgradePromise = null;

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    _upgradePromise = ctx.channel().newPromise();

    // TODO: find a more reasonable request uri
    DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.OPTIONS, "/");
    ctx.writeAndFlush(request).sync();
    ctx.fireChannelActive();
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception
  {
    if (_upgradePromise == null)
    {
      ctx.fireExceptionCaught(new IllegalStateException("Received request before channel is active"));
      return;
    }

    LOG.debug("Write call is delayed since upgrade is not yet successful");
    _upgradePromise.addListener(f -> {
      ChannelFuture future = (ChannelFuture)f;
      if (future.isSuccess())
      {
        LOG.debug("Send request after upgrade succeeded");
        ctx.writeAndFlush(msg, promise);
      }
      else
      {
        LOG.debug("Send request after upgrade failed");
        ctx.fireExceptionCaught(future.cause());
      }
    });
  }

  @Override
  public void flush(ChannelHandlerContext ctx) throws Exception
  {
    if (_upgradePromise == null)
    {
      ctx.fireExceptionCaught(new IllegalStateException("Received request before channel is active"));
      return;
    }

    // Intercepts #flush before upgrade is complete
    LOG.debug("Flush call is suspended since upgrade is not yet successful");
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt)
      throws Exception
  {
    LOG.debug("Received user event {}", evt);
    if (_upgradePromise == null)
    {
      ctx.fireExceptionCaught(new IllegalStateException("Received request before channel is active"));
      return;
    }

    if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_ISSUED)
    {
      LOG.debug("HTTP/2 clear text upgrade issued");
    }
    else if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_SUCCESSFUL)
    {
      LOG.debug("HTTP/2 clear text upgrade successful");
      // Remove handler from pipeline as it is no longer useful
      ctx.pipeline().remove(ctx.name());
      _upgradePromise.setSuccess();

    }
    else if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_REJECTED)
    {
      LOG.error("HTTP/2 clear text upgrade failed");
      _upgradePromise.setFailure(new IllegalStateException("HTTP/2 clear text upgrade failed"));
    }
    else
    {
      ctx.fireExceptionCaught(new IllegalStateException("Unexpected user event received " + evt));
    }
  }
}