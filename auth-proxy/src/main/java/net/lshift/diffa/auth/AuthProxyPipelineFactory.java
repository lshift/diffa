package net.lshift.diffa.auth;

import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;

public class AuthProxyPipelineFactory implements ChannelPipelineFactory {

  private final ClientSocketChannelFactory cf;
  private final String remoteHost;
  private final int remotePort;


  public AuthProxyPipelineFactory(ClientSocketChannelFactory cf, String remoteHost, int remotePort) {
    this.cf = cf;
    this.remoteHost = remoteHost;
    this.remotePort = remotePort;
  }

  @Override
  public ChannelPipeline getPipeline() throws Exception {

    ChannelPipeline p = Channels.pipeline();

    p.addLast("decoder", new HttpRequestDecoder());
    p.addLast("auth", new AuthenticatingTranscoder());
    p.addLast("handler", new AuthProxyInboundHandler(cf, remoteHost, remotePort));

    return p;
  }
}
