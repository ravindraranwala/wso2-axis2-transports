package org.apache.axis2.transport.websocket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.CharsetUtil;

import javax.xml.stream.XMLStreamException;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.context.OperationContext;
import org.apache.axis2.context.ServiceContext;
import org.apache.axis2.description.AxisOperation;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.InOnlyAxisOperation;
import org.apache.axis2.engine.AxisEngine;
import org.apache.axis2.util.UUIDGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {
    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;
    private static final Log log = LogFactory.getLog(WebSocketClientHandler.class);

    private AxisService axisService;
    private ServiceContext serviceContext;
    private AxisOperation axisOperation;
    private Object isInbound;
    private Object sender;

    private MessageContext responseMsgCtx;
    private ConfigurationContext configurationContext;

    public WebSocketClientHandler(WebSocketClientHandshaker handshaker) {
        this.handshaker = handshaker;
    }

    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        handshaker.handshake(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("WebSocket Client disconnected!");
    }

    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        String responseMsg = null;
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            handshaker.finishHandshake(ch, (FullHttpResponse) msg);
            log.info("WebSocket Client connected!");
            handshakeFuture.setSuccess();
            return;
        }

        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            throw new IllegalStateException("Unexpected FullHttpResponse (getStatus=" +
                                            response.getStatus() + ", content=" +
                                            response.content().toString(CharsetUtil.UTF_8) + ')');
        }

        WebSocketFrame frame = (WebSocketFrame) msg;
        if (frame instanceof TextWebSocketFrame) {
            TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
            responseMsg = textFrame.text();
            log.info("WebSocket Client received message: " + textFrame.text());
        } else if (frame instanceof PongWebSocketFrame) {
            log.info("WebSocket Client received pong");
        } else if (frame instanceof CloseWebSocketFrame) {
            log.info("WebSocket Client received closing");
            ch.close();
        }

        MessageContext responseMsgCtx = new MessageContext();
        responseMsgCtx.setMessageID(UUIDGenerator.getUUID());
        // responseMsgCtx.setTo(null);

        setOperationAndServiceContext(responseMsgCtx);

        ConfigurationContext cfgCtx = responseMsgCtx.getConfigurationContext();
        responseMsgCtx.setTransportOut(cfgCtx.getAxisConfiguration().getTransportOut("ws"));
        responseMsgCtx.setTransportIn(cfgCtx.getAxisConfiguration().getTransportIn("ws"));
        responseMsgCtx.setIncomingTransportName("ws");

        responseMsgCtx.setProperty("isInbound", isInbound);

        responseMsgCtx.setProperty("inbound-response-worker", sender);

        responseMsgCtx.setEnvelope(createEnvelope(responseMsg));

        AxisEngine.receive(responseMsgCtx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Error encountered while processing the response", cause);
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }
        ctx.close();
    }

    private SOAPEnvelope createEnvelope(String msg) throws XMLStreamException {
        SOAPFactory fac = OMAbstractFactory.getSOAP11Factory();
        SOAPEnvelope envelope = fac.getDefaultEnvelope();
        OMElement websockElement = AXIOMUtil.stringToOM(msg);
        org.apache.axiom.om.OMNamespace ns =
                                             fac.createOMNamespace("http://wso2.org/websocket",
                                                                   "wsoc");
        OMElement messageEl = fac.createOMElement("message", ns);
        messageEl.addChild(websockElement);
        envelope.getBody().addChild(messageEl);
        return envelope;
    }

    // private void setOperationAndServiceContext(MessageContext axis2MsgCtx)
    // throws AxisFault {
    // ServiceContext svcCtx = new ServiceContext();
    // OperationContext opCtx = new OperationContext(new InOnlyAxisOperation(),
    // svcCtx);
    // axis2MsgCtx.setServiceContext(svcCtx);
    // axis2MsgCtx.setOperationContext(opCtx);
    // axis2MsgCtx.setProperty(org.apache.axis2.context.MessageContext.CLIENT_API_NON_BLOCKING,
    // Boolean.FALSE);
    // axis2MsgCtx.setServerSide(true);
    // }

    private void setOperationAndServiceContext(MessageContext axis2MsgCtx) throws AxisFault {
        // ServiceContext svcCtx = new ServiceContext();
        // OperationContext opCtx = new OperationContext(axisOperation,
        // serviceContext);
        // axis2MsgCtx.setServiceContext(svcCtx);
        // axis2MsgCtx.setServiceContext(serviceContext);
        axis2MsgCtx.setConfigurationContext(configurationContext);
        String defaultSvcName = "__SynapseService";
        axisService =
                      axis2MsgCtx.getConfigurationContext().getAxisConfiguration()
                                 .getService(defaultSvcName);
        axisService.addExposedTransport("ws");
        axisService.addExposedTransport("wss");

        axis2MsgCtx.setAxisService(axisService);
        // axis2MsgCtx.setOperationContext(opCtx);
        axis2MsgCtx.setProperty(org.apache.axis2.context.MessageContext.CLIENT_API_NON_BLOCKING,
                                Boolean.FALSE);
        axis2MsgCtx.setServerSide(true);
    }

    public void setResponseMsgCtx(MessageContext responseMsgCtx) {
        this.responseMsgCtx = responseMsgCtx;
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

    public void setConfigurationContext(ConfigurationContext configurationContext) {
        this.configurationContext = configurationContext;
    }

}
