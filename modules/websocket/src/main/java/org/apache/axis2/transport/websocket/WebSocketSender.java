package org.apache.axis2.transport.websocket;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.axiom.om.OMOutputFormat;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.TransportOutDescription;
import org.apache.axis2.transport.MessageFormatter;
import org.apache.axis2.transport.OutTransportInfo;
import org.apache.axis2.transport.base.AbstractTransportSender;
import org.apache.axis2.transport.base.BaseUtils;
import org.apache.axis2.util.MessageProcessorSelector;
import org.apache.commons.io.output.WriterOutputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class WebSocketSender extends AbstractTransportSender {
    private WebsocketConnectionFactory connectionFactory;

    private static final Log log = LogFactory.getLog(WebSocketSender.class);

    public void init(ConfigurationContext cfgCtx, TransportOutDescription transportOut)
                                                                                       throws AxisFault {
        log.info("Initializing the Connection Factory.");
        super.init(cfgCtx, transportOut);
        connectionFactory = new WebsocketConnectionFactory(transportOut);

    }

    public void sendMessage(MessageContext msgCtx, String targetEPR, OutTransportInfo trpOut)
                                                                                             throws AxisFault {
        try {
            OMOutputFormat format = BaseUtils.getOMOutputFormat(msgCtx);
            log.info("Fetching a Connection from the Connection Factory.");
            
            MessageContext clonedCtx = WebSocketUtil.cloneAxis2MessageContext(msgCtx, false);
            connectionFactory.setClonedMsgCtx(clonedCtx);
            
            Channel ch = connectionFactory.getConnection(new URI(targetEPR));
            MessageFormatter messageFormatter =
                                                MessageProcessorSelector.getMessageFormatter(msgCtx);
            StringWriter sw = new StringWriter();

            OutputStream out = new WriterOutputStream(sw, format.getCharSetEncoding());
            messageFormatter.writeTo(msgCtx, format, out, true);
            out.close();
            final String msg = sw.toString();
            WebSocketFrame frame = new TextWebSocketFrame(msg);
            log.info("Sending the message to the backend.");
            ch.writeAndFlush(frame);
        } catch (URISyntaxException e) {
            log.error("Error parsing the url", e);
        } catch (IOException e) {
            log.error("IO Exception was thrown", e);
        }
    }
}
