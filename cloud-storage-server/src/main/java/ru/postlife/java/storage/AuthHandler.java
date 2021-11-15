package ru.postlife.java.storage;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import ru.postlife.java.model.AuthModel;

@Slf4j
public class AuthHandler extends SimpleChannelInboundHandler<AuthModel> {

    AuthService authService;

    public AuthHandler(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("e", cause);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("Client try auth...");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, AuthModel authModel) throws Exception {
        authService.checkUserByLoginPass(authModel);
        if (authModel.isAuth()) {
            log.debug(authModel.getLogin() + " has success auth!");
        } else {
            log.debug(authModel.getLogin() + " dont find!");
        }
        ctx.writeAndFlush(authModel);
    }
}
