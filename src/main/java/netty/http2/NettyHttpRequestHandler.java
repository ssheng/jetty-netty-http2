package netty.http2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.eclipse.jetty.client.HttpRequest;


public class NettyHttpRequestHandler extends ChannelOutboundHandlerAdapter
{
  /**
   * Calls {@link io.netty.channel.ChannelHandlerContext#write(Object, io.netty.channel.ChannelPromise)} to forward
   * to the next {@link io.netty.channel.ChannelOutboundHandler} in the {@link io.netty.channel.ChannelPipeline}.
   *
   * Sub-classes may override this method to change behavior.
   */
  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
    assert(msg instanceof HttpRequest);

    System.err.println(">>>>>>>>>>>> Outbound Write >>>>>>>>>>>>>>>>");
    System.err.println(msg);
    System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
    ctx.write(msg, promise);
  }
}
