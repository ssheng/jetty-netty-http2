package netty.client.http;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpResponse;


@ChannelHandler.Sharable
public class NettyResponseLogger extends ChannelInboundHandlerAdapter
{
  private int counter = 0;

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    assert(msg instanceof FullHttpResponse);
    System.err.println("----------- Received Response " + counter + " -----------");
    System.err.println(msg);
    System.err.println("----------- End of Response " + counter + " -----------");
    System.err.println();
    ctx.fireChannelRead(msg);
    counter++;
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
      throws Exception {
    System.err.println("----------- Received Response " + counter + " -----------");
    System.err.println(cause);
    System.err.println("----------- End of Response " + counter + " -----------");
    System.err.println();
    ctx.fireExceptionCaught(cause);
  }
}