/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package netty.server;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.example.http.helloworld.HttpHelloWorldServerHandler;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslContext;

public class NettyHelloWorldInitializer extends ChannelInitializer<SocketChannel> {

  private final SslContext sslCtx;
  private final ConnectionTerminationHandler closeHandler = new ConnectionTerminationHandler();

  public NettyHelloWorldInitializer(SslContext sslCtx) {
    this.sslCtx = sslCtx;
  }

  @Override
  public void initChannel(SocketChannel ch) {
    ChannelPipeline p = ch.pipeline();
    p.addLast(new HttpServerCodec());
    p.addLast(closeHandler);
    p.addLast(new HttpHelloWorldServerHandler());
  }

  @Sharable
  private class ConnectionTerminationHandler extends ChannelInboundHandlerAdapter {
    private int counter = 0;
    /**
     * Calls {@link io.netty.channel.ChannelHandlerContext#fireChannelRead(Object)} to forward
     * to the next {@link io.netty.channel.ChannelInboundHandler} in the {@link ChannelPipeline}.
     *
     * Sub-classes may override this method to change behavior.
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (!(msg instanceof HttpRequest)) {
        ctx.fireChannelRead(msg);
        return;
      }
      counter++;
      if (counter >= 2) {
        System.err.println("Close");
        ctx.close();
      } else {
        System.err.println("Continue");
        ctx.fireChannelRead(msg);
        //ctx.writeAndFlush(Unpooled.copiedBuffer(new byte[] { 101, 102, 103, 104 })).syncUninterruptibly();
      }
    }
  }
}
