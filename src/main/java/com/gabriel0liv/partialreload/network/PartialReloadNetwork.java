package com.gabriel0liv.partialreload.network;

import java.util.function.Supplier;

import com.gabriel0liv.partialreload.client.network.ClientHandshakeController;
import com.gabriel0liv.partialreload.network.packet.ClientHelloC2S;
import com.gabriel0liv.partialreload.network.packet.HandshakeAcceptedS2C;
import com.gabriel0liv.partialreload.network.packet.ServerHelloS2C;
import com.gabriel0liv.partialreload.network.protocol.ClientCapability;
import com.gabriel0liv.partialreload.network.protocol.ClientSyncProtocol;
import com.gabriel0liv.partialreload.network.server.ClientHandshakeServer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.Connection;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class PartialReloadNetwork {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ClientSyncProtocol.CHANNEL_ID,
            () -> Integer.toString(ClientSyncProtocol.PROTOCOL_VERSION),
            PartialReloadNetwork::acceptsVersion,
            PartialReloadNetwork::acceptsVersion);

    private static boolean registered;

    private PartialReloadNetwork() {
    }

    private static boolean acceptsVersion(String version) {
        return version != null
                && (version.equals(Integer.toString(ClientSyncProtocol.PROTOCOL_VERSION))
                        || NetworkRegistry.ACCEPTVANILLA.equals(version)
                        || "ABSENT".equals(version)
                        || "ACCEPTVANILLA".equals(version));
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        CHANNEL.messageBuilder(ServerHelloS2C.class,
                        ClientSyncProtocol.SERVER_HELLO_DISCRIMINATOR, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerHelloS2C::encode)
                .decoder(ServerHelloS2C::decode)
                .consumerMainThread((message, context) -> clientPacket(context, () ->
                        ClientHandshakeController.handle(message)))
                .add();
        CHANNEL.messageBuilder(ClientHelloC2S.class,
                        ClientSyncProtocol.CLIENT_HELLO_DISCRIMINATOR, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClientHelloC2S::encode)
                .decoder(ClientHelloC2S::decode)
                .consumerMainThread((message, context) ->
                        ClientHandshakeServer.handleClientHello(message, context.get()))
                .add();
        CHANNEL.messageBuilder(HandshakeAcceptedS2C.class,
                        ClientSyncProtocol.HANDSHAKE_ACCEPTED_DISCRIMINATOR, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HandshakeAcceptedS2C::encode)
                .decoder(HandshakeAcceptedS2C::decode)
                .consumerMainThread((message, context) -> clientPacket(context, () ->
                        ClientHandshakeController.handle(message)))
                .add();
        registered = true;
    }

    private static void clientPacket(Supplier<NetworkEvent.Context> context,
            Runnable clientWork) {
        NetworkEvent.Context source = context.get();
        source.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> clientWork.run()));
        source.setPacketHandled(true);
    }

    public static void sendHello(net.minecraft.server.level.ServerPlayer player,
            ServerHelloS2C hello) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), hello);
    }

    public static void sendToServer(ClientHelloC2S hello) {
        CHANNEL.sendToServer(hello);
    }

    public static boolean isRemotePresent(Connection connection) {
        return CHANNEL.isRemotePresent(connection);
    }
}
