package ru.postlife.java.storage;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyServer {

    public static void main(String[] args) {

        AuthService authService = null;

        EventLoopGroup auth = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();

        try {
            authService = new DatabaseAuthService();
            authService.start();

            ServerBootstrap bootstrap = new ServerBootstrap();
            AuthService finalAuthService = authService;
            bootstrap.channel(NioServerSocketChannel.class)
                    .group(auth, worker)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) throws Exception {
                            // TODO: 04.11.2021
                            channel.pipeline().addLast(
                                    new ObjectDecoder(ClassResolvers.cacheDisabled(null)),
                                    new ObjectEncoder(),
                                    new AuthHandler(finalAuthService),
                                    new FileListHandler(),
                                    new FileRequestHandler(),
                                    new FileModelHandler(),
                                    new FileDeleteRequestHandler(),
                                    new ObjectHandler()
                            );
                        }
                    });
            ChannelFuture future = bootstrap.bind(8189).sync();
            log.debug("Server started...");
            future.channel().closeFuture().sync(); // block
        } catch (Exception e) {
            log.error("e", e);
        } finally {
            auth.shutdownGracefully();
            worker.shutdownGracefully();
            authService.stop();
        }
    }
}
