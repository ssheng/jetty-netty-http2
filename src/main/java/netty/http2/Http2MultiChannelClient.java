package netty.http2;/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import com.linkedin.r2.message.stream.StreamRequest;
import com.linkedin.r2.message.stream.StreamRequestBuilder;
import com.linkedin.r2.message.stream.entitystream.EntityStreams;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;


public final class Http2MultiChannelClient
{
  static final boolean SSL = System.getProperty("ssl") != null;
  static final String HOST = System.getProperty("host", "127.0.0.1");
  static final int PORT = Integer.parseInt(System.getProperty("port", SSL? "8443" : "8080"));
  static final String URL = System.getProperty("url", "http://localhost:8080/whatever/whenever?q=test");

  public static void main(String[] args) throws Exception {
    EventLoopGroup workerGroup = new NioEventLoopGroup();
    NettyHttp2StreamingClientInitializer initializer = new NettyHttp2StreamingClientInitializer();

    try {
      // Configure the client.
      Bootstrap bootstrap = new Bootstrap();
      bootstrap.group(workerGroup);
      bootstrap.channel(NioSocketChannel.class);
      bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
      bootstrap.remoteAddress(HOST, PORT);
      bootstrap.handler(initializer);

      List<ChannelFuture> closeFutures = new ArrayList<>();
      for (int i = 0; i < 100; i++) {
        // Start the client.
        Channel channel = bootstrap.connect().syncUninterruptibly().channel();
        //System.out.println("#" + i + " Connected to [" + HOST + ':' + PORT + ']');

        AsciiString hostName = new AsciiString(HOST + ':' + PORT);
        if (URL != null)
        {
          // Create a simple GET request.
          StreamRequest request = new StreamRequestBuilder(new URI(URL)).setMethod("GET")
              .setHeader(HttpHeaderNames.HOST.toString(), hostName.toString())
              .setHeader("x-li-unique-id", "id" + i)
              .build(EntityStreams.emptyStream());
          channel.writeAndFlush(request);
        }
        // Wait until the connection is closed.
        closeFutures.add(channel.closeFuture());
      }
      //System.err.println("Finished HTTP/2 request(s)");

      for (ChannelFuture future : closeFutures) {
        future.sync();
      }
    } finally {
      workerGroup.shutdownGracefully();
    }
  }
}
