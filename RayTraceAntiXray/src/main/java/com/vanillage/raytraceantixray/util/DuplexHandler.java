package com.vanillage.raytraceantixray.util;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelPipeline;
import org.bukkit.entity.Player;

import java.net.InetAddress;
import java.util.NoSuchElementException;
import java.util.Objects;

public class DuplexHandler extends ChannelDuplexHandler {

    private final String name;

    private Channel channel;

    public DuplexHandler(final String name) {
        this.name = Objects.requireNonNull(name);
    }

    public String getAttachedName() {
        return this.name;
    }

    public Channel getAttachedChannel() {
        if (this.channel != null && this.channel.pipeline().get(this.name) != this)
            this.channel = null;
        return this.channel;
    }

    public void attach(final Player player) throws RuntimeException {
        this.attach(player.getAddress().getAddress());
    }

    public void attach(final InetAddress address) throws RuntimeException {
        this.attach(NetworkUtil.getChannelOrThrow(NetworkUtil.getServerConnectionOrThrow(address)));
    }

    public void attach(final Channel channel) {
        this.detach();
        final ChannelPipeline pipe = channel.pipeline();
        if (pipe.get("packet_handler") == null) {
            pipe.addLast(this.name, this);
        } else {
            pipe.addBefore("packet_handler", this.name, this);
        }
        this.channel = channel;
    }

    public void detach() throws RuntimeException {
        if (this.channel != null) {
            DuplexHandler.detach(this.channel, this.name);
            this.channel = null;
        }
    }

    public static void detach(final Player player, final String name) throws RuntimeException {
        DuplexHandler.detach(player.getAddress().getAddress(), name);
    }

    public static void detach(final InetAddress address, final String name) throws RuntimeException {
        DuplexHandler.detach(NetworkUtil.getChannelOrThrow(NetworkUtil.getServerConnectionOrThrow(address)), name);
    }

    public static void detach(final Channel channel, final String name) {
        try {
            if (channel.pipeline().remove(name) instanceof final DuplexHandler handler) {
                handler.channel = null;
            }
        } catch (final NoSuchElementException ignored) {
        }
    }

}
