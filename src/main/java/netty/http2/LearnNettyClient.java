package netty.http2;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
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
import org.eclipse.jetty.client.HttpRequest;


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
          ch.pipeline().addLast("1", new LearnNettyHandler("Handler #1"));
          ch.pipeline().addLast("2", new LearnNettyHandler("Handler #2"));
          ch.pipeline().addLast("3", new LearnNettyHandler("Handler #3"));
          ch.pipeline().addLast("4", new LearnNettyHandler("Handler #4"));
        }
      });
      Channel channel = b.connect().syncUninterruptibly().channel();
      long start = System.currentTimeMillis();

      System.out.println("Sending Request #1");
      DefaultFullHttpRequest request1 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/anything1");
      request1.headers().add(HttpHeaderNames.HOST, HOST);
      request1.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "http");

      channel.writeAndFlush(request1);

      System.out.println("Sending Request #2");
      DefaultFullHttpRequest request2 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/anything2");
      request2.headers().add(HttpHeaderNames.HOST, HOST);

      channel.writeAndFlush(request2);

      /*ChannelPromise future = channel.newPromise();
      future.addListener(f -> System.out.println("#1 Callback"));
      future.addListener(f -> System.out.println("#2 Callback"));
      future.addListener(f -> System.out.println("#3 Callback"));
      future.addListener(f -> System.out.println("#4 Callback"));
      future.setFailure(new Exception());*/

      /* Thread.sleep(2000);
      System.out.println("Sending Request #2");
      DefaultFullHttpRequest request2 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/anything");
      request2.headers().add(HttpHeaderNames.HOST, HOST);
      request2.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "http");
      channel.writeAndFlush(request2, channel.voidPromise()); */

      channel.closeFuture().sync();
      long end = System.currentTimeMillis();
      System.err.println("Closed after " + (end - start) + " millis");
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
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception
    {
      System.out.println(_name + ": Handler Added");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
    {
      System.out.println(_name + ": Channel Read");
      ctx.fireChannelRead(msg);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
      System.out.println(_name + ": Channel Active");
      //if (_name.equals("Handler #2")) ctx.fireUserEventTriggered(new Object());
      //if (_name.equals("Handler #3")) ctx.fireExceptionCaught(new Exception());
      //if (_name.equals("Handler #3")) ctx.write(new Object());
      ctx.fireChannelActive();
      //throw new Exception("OMGBBQ");
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

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception
    {
      System.out.println(_name + ": Write " + ((DefaultFullHttpRequest)msg).uri());
      if (_name.equals("Handler #3"))
      {
        //ctx.pipeline().addBefore("4", "3.5", new LearnNettyHandler("Handler #3.5"));
        ctx.pipeline().remove("3");
        //ctx.pipeline().remove("3");
        //ctx.pipeline().remove("2");
        //ctx.pipeline().remove("1");
      }
      ctx.write(msg, promise);
    }
  }
}
