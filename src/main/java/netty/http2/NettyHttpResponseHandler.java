package netty.http2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;


public class NettyHttpResponseHandler extends ChannelInboundHandlerAdapter
{
  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg)
      throws Exception
  {
    System.err.println("<<<<<<<<<<<< Inbound Read <<<<<<<<<<<<");
    System.err.println(msg);
    System.err.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
    ctx.fireChannelRead(msg);
  }
}
