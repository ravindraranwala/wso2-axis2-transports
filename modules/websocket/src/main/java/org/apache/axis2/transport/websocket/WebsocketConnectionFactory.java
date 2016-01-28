package org.apache.axis2.transport.websocket;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.net.URI;

import javax.net.ssl.SSLException;
import javax.xml.namespace.QName;

import org.apache.axiom.om.OMElement;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ServiceContext;
import org.apache.axis2.description.AxisOperation;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.description.TransportOutDescription;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class WebsocketConnectionFactory {
    private static final Log log = LogFactory.getLog(WebsocketConnectionFactory.class);

    private final TransportOutDescription transportOut;

    private AxisService axisService;
    private ServiceContext serviceContext;
    private AxisOperation axisOperation;
    private Object isInbound;
    private Object sender;
    private ConfigurationContext configCtx;

    public WebsocketConnectionFactory(TransportOutDescription transportOut) {
        super();
        this.transportOut = transportOut;
    }

    public Channel getConnection(final URI uri) {
        log.info("Creating a Connection for the specified EP.");
        EventLoopGroup group = null;
        Channel ch = null;

        try {
            String scheme = uri.getScheme() == null ? "ws" : uri.getScheme();
            final String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
            final int port;
            if (uri.getPort() == -1) {
                if ("ws".equalsIgnoreCase(scheme)) {
                    port = 80;
                } else if ("wss".equalsIgnoreCase(scheme)) {
                    port = 443;
                } else {
                    port = -1;
                }
            } else {
                port = uri.getPort();
            }

            if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
                System.err.println("Only WS(S) is supported.");
                return null;
            }

            final boolean ssl = "wss".equalsIgnoreCase(scheme);
            final SslContext sslCtx;
            if (ssl) {
                Parameter trustParam = transportOut.getParameter("truststore");

                OMElement tsEle = null;

                if (trustParam != null) {
                    tsEle = trustParam.getParameterElement().getFirstElement();
                }

                final String location =
                                        tsEle.getFirstChildWithName(new QName("Location"))
                                             .getText();
                final String storePassword =
                                             tsEle.getFirstChildWithName(new QName("Password"))
                                                  .getText();
                sslCtx =
                         SslContextBuilder.forClient()
                                          .trustManager(SSLUtil.createTrustmanager(location,
                                                                                   storePassword))
                                          .build();
            } else {
                sslCtx = null;
            }

            group = new NioEventLoopGroup();
            // Connect with V13 (RFC 6455 aka HyBi-17). You can change it to V08
            // or V00.
            // If you change it to V00, ping is not supported and remember to
            // change
            // HttpResponseDecoder to WebSocketHttpResponseDecoder in the
            // pipeline.
            final WebSocketClientHandler handler =
                                                   new WebSocketClientHandler(
                                                                              WebSocketClientHandshakerFactory.newHandshaker(uri,
                                                                                                                             WebSocketVersion.V13,
                                                                                                                             null,
                                                                                                                             false,
                                                                                                                             new DefaultHttpHeaders()));

            handler.setAxisService(axisService);
            handler.setServiceContext(serviceContext);
            handler.setAxisOperation(axisOperation);
            handler.setIsInbound(isInbound);
            handler.setSender(sender);
            Bootstrap b = new Bootstrap();
            b.group(group).channel(NioSocketChannel.class)
             .handler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ChannelPipeline p = ch.pipeline();
                     if (sslCtx != null) {
                         p.addLast(sslCtx.newHandler(ch.alloc(), host, port));
                     }
                     p.addLast(new HttpClientCodec(), new HttpObjectAggregator(8192), handler);
                 }
             });

            ch = b.connect(uri.getHost(), port).sync().channel();
            handler.handshakeFuture().sync();

        } catch (InterruptedException e) {
            log.error("Interruption occured", e);
        } catch (SSLException e) {
            log.error("Error occurred while building the SSL context", e);
        } finally {
            // group.shutdownGracefully();
        }

        return ch;
    }

    public void setAxisService(AxisService axisService) {
        this.axisService = axisService;
    }

    public void setServiceContext(ServiceContext serviceContext) {
        this.serviceContext = serviceContext;
    }

    public void setAxisOperation(AxisOperation axisOperation) {
        this.axisOperation = axisOperation;
    }

    public void setIsInbound(Object isInbound) {
        this.isInbound = isInbound;
    }

    public void setSender(Object sender) {
        this.sender = sender;
    }

    public void setConfigCtx(ConfigurationContext configCtx) {
        this.configCtx = configCtx;
    }

}
