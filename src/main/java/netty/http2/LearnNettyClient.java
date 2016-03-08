package netty.http2;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.HttpConversionUtil;


public class LearnNettyClient
{
  static final String HOST = System.getProperty("host", "127.0.0.1");
  static final int PORT = Integer.parseInt(System.getProperty("port", "8080"));

  public static void main(String[] args) throws Exception {
    EventLoopGroup workerGroup = new NioEventLoopGroup();
    try
    {
      // Configure the client.
      Bootstrap b = new Bootstrap();
      b.group(workerGroup);
      b.channel(NioSocketChannel.class);
      b.option(ChannelOption.SO_KEEPALIVE, true);
      b.remoteAddress(HOST, PORT);
      b.handler(new ChannelInitializer<SocketChannel>()
      {

        @Override
        protected void initChannel(SocketChannel ch) throws Exception
        {
          ch.pipeline().addLast(new HttpClientCodec());
          ch.pipeline().addLast(new LearnNettyHandler("Handler #1"));
          ch.pipeline().addLast(new LearnNettyHandler("Handler #2"));
          ch.pipeline().addLast(new LearnNettyHandler("Handler #3"));
          ch.pipeline().addLast(new LearnNettyHandler("Handler #4"));
        }
      });
      Channel channel = b.connect().syncUninterruptibly().channel();
      DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/anything");
      request.headers().add(HttpHeaderNames.HOST, HOST);
      request.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "http");
      channel.writeAndFlush(request);

      channel.close().sync();
    }
    finally
    {
      workerGroup.shutdownGracefully();
    }
  }

  private static class LearnNettyHandler extends ChannelDuplexHandler
  {
    private final String _name;
    public LearnNettyHandler(String name)
    {
      _name = name;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
      System.out.println(_name + ": Channel Active");
      if (_name.equals("Handler #2")) ctx.fireUserEventTriggered(new Object());
      if (_name.equals("Handler #3")) ctx.fireExceptionCaught(new Exception());
      ctx.fireChannelActive();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception
    {
      System.out.println(_name + ": User Event Triggered");
      ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
    {
      System.out.println(_name + ": Exception Caught");
      ctx.fireExceptionCaught(cause);
    }
  }
}
