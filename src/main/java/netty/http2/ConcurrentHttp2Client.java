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

import com.linkedin.data.ByteString;
import com.linkedin.r2.message.stream.StreamRequest;
import com.linkedin.r2.message.stream.StreamRequestBuilder;
import com.linkedin.r2.message.stream.entitystream.ByteStringWriter;
import com.linkedin.r2.message.stream.entitystream.EntityStreams;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.AsciiString;

import java.net.URI;
import java.util.concurrent.CountDownLatch;


/**
 * An HTTP2 client that allows you to send HTTP2 frames to a server. Inbound and outbound frames are
 * logged. When run from the command-line, sends a single HEADERS frame to the server and gets back
 * a "Hello World" response.
 */
public final class ConcurrentHttp2Client
{
  static final boolean SSL = System.getProperty("ssl") != null;
  static final String HOST = System.getProperty("host", "127.0.0.1");
  static final int PORT = Integer.parseInt(System.getProperty("port", SSL? "8443" : "8080"));
  static final String URL = System.getProperty("url", "http://localhost:8080/whatever/whenever?q=test");
  static final String URL2 = System.getProperty("url2");
  static final String URL2DATA = System.getProperty("url2data", "test data!");

  public static void main(String[] args) throws Exception {
    // Configure SSL.
    final SslContext sslCtx;
    if (SSL) {
      SslProvider provider = OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK;
      sslCtx = SslContextBuilder.forClient()
          .sslProvider(provider)
                /* NOTE: the cipher filter may not include all ciphers required by the HTTP/2 specification.
                 * Please refer to the HTTP/2 specification for cipher requirements. */
          .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
          .trustManager(InsecureTrustManagerFactory.INSTANCE)
          .applicationProtocolConfig(new ApplicationProtocolConfig(
              Protocol.ALPN,
              // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
              SelectorFailureBehavior.NO_ADVERTISE,
              // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
              SelectedListenerFailureBehavior.ACCEPT,
              ApplicationProtocolNames.HTTP_2,
              ApplicationProtocolNames.HTTP_1_1))
          .build();
    } else {
      sslCtx = null;
    }

    EventLoopGroup workerGroup = new NioEventLoopGroup();
    ConcurrentHttp2ClientInitializer initializer = new ConcurrentHttp2ClientInitializer();

    try {
      // Configure the client.
      Bootstrap bootstrap = new Bootstrap();
      bootstrap.group(workerGroup);
      bootstrap.channel(NioSocketChannel.class);
      bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
      bootstrap.remoteAddress(HOST, PORT);
      bootstrap.handler(initializer);

      // Start the client.
      Channel channel = bootstrap.connect().syncUninterruptibly().channel();
      System.out.println("Connected to [" + HOST + ':' + PORT + ']');

      HttpScheme scheme = SSL ? HttpScheme.HTTPS : HttpScheme.HTTP;
      AsciiString hostName = new AsciiString(HOST + ':' + PORT);
      System.err.println("Sending request(s)...");
      if (URL != null) {
        // Create a simple GET request.
        for (int i = 0; i < 1; i++) {
          StreamRequest request = new StreamRequestBuilder(new URI(URL))
              .setMethod("GET")
              //.setMethod("POST")
              .setHeader(HttpHeaderNames.HOST.toString(), hostName.toString())
              //.build(EntityStreams.emptyStream());
              .build(EntityStreams.newEntityStream(new ByteStringWriter(ByteString.copy(new byte[0 * 1024]))));
          channel.writeAndFlush(request);
          System.err.println("Sent request #" + i);
        }
      }
      System.err.println("Finished HTTP/2 request(s)");
      long start = System.currentTimeMillis();

      // Wait until the connection is closed.
      channel.closeFuture().sync();

      long end = System.currentTimeMillis();
      System.err.println("Server Idled for: " + (end - start) + " milliseconds");
    } finally {
      workerGroup.shutdownGracefully();
    }
  }
}
