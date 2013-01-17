package net.lshift.diffa.auth;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;

import java.net.InetSocketAddress;

public class AuthProxyInboundHandler extends SimpleChannelUpstreamHandler {

  private final ClientSocketChannelFactory cf;
  private final String remoteHost;
  private final int remotePort;

  // This lock guards against the race condition that overrides the
  // OP_READ flag incorrectly.
  // See the related discussion: http://markmail.org/message/x7jc6mqx6ripynqf
  final Object trafficLock = new Object();

  private volatile Channel outboundChannel;

  public AuthProxyInboundHandler(ClientSocketChannelFactory cf, String remoteHost, int remotePort) {
    this.cf = cf;
    this.remoteHost = remoteHost;
    this.remotePort = remotePort;
  }

  @Override
  public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
      throws Exception {
    // Suspend incoming traffic until connected to the remote host.
    final Channel inboundChannel = e.getChannel();
    inboundChannel.setReadable(false);

    // Start the connection attempt.
    ClientBootstrap cb = new ClientBootstrap(cf);

    cb.getPipeline().addLast("encoder", new HttpRequestEncoder() );
    cb.getPipeline().addLast("handler", new OutboundHandler(e.getChannel()));

    ChannelFuture f = cb.connect(new InetSocketAddress(remoteHost, remotePort));

    outboundChannel = f.getChannel();
    f.addListener(new ChannelFutureListener() {
      public void operationComplete(ChannelFuture future) throws Exception {
        if (future.isSuccess()) {
          // Connection attempt succeeded:
          // Begin to accept incoming traffic.
          inboundChannel.setReadable(true);
        } else {
          // Close the connection if the connection attempt has failed.
          inboundChannel.close();
        }
      }
    });
  }

  @Override
  public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e)
      throws Exception {

    synchronized (trafficLock) {
      outboundChannel.write(e.getMessage());
      // If outboundChannel is saturated, do not read until notified in
      // OutboundHandler.channelInterestChanged().
      if (!outboundChannel.isWritable()) {
        e.getChannel().setReadable(false);
      }
    }
  }

  @Override
  public void channelInterestChanged(ChannelHandlerContext ctx,
                                     ChannelStateEvent e) throws Exception {
    // If inboundChannel is not saturated anymore, continue accepting
    // the incoming traffic from the outboundChannel.
    synchronized (trafficLock) {
      if (e.getChannel().isWritable()) {
        if (outboundChannel != null) {
          outboundChannel.setReadable(true);
        }
      }
    }
  }

  @Override
  public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
      throws Exception {
    if (outboundChannel != null) {
      closeOnFlush(outboundChannel);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
      throws Exception {
    e.getCause().printStackTrace();
    closeOnFlush(e.getChannel());
  }

  private class OutboundHandler extends SimpleChannelUpstreamHandler {

    private final Channel inboundChannel;

    OutboundHandler(Channel inboundChannel) {
      this.inboundChannel = inboundChannel;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e)
        throws Exception {
      ChannelBuffer msg = (ChannelBuffer) e.getMessage();
      synchronized (trafficLock) {
        inboundChannel.write(msg);
        // If inboundChannel is saturated, do not read until notified in
        // HexDumpProxyInboundHandler.channelInterestChanged().
        if (!inboundChannel.isWritable()) {
          e.getChannel().setReadable(false);
        }
      }
    }

    @Override
    public void channelInterestChanged(ChannelHandlerContext ctx,
                                       ChannelStateEvent e) throws Exception {
      // If outboundChannel is not saturated anymore, continue accepting
      // the incoming traffic from the inboundChannel.
      synchronized (trafficLock) {
        if (e.getChannel().isWritable()) {
          inboundChannel.setReadable(true);
        }
      }
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
        throws Exception {
      closeOnFlush(inboundChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
        throws Exception {
      e.getCause().printStackTrace();
      closeOnFlush(e.getChannel());
    }
  }

  /**
   * Closes the specified channel after all queued write requests are flushed.
   */
  static void closeOnFlush(Channel ch) {
    if (ch.isConnected()) {
      ch.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }
  }
}
