package UselessDuck.CactusMod;

import ibxm.Player;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.util.Timer;
import java.util.TimerTask;

public class TeleportSafetyListener {

    private boolean hasInjected = false;

    public TeleportSafetyListener() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (!hasInjected && Minecraft.getMinecraft().thePlayer != null) {
            injectNettyHandler();
            hasInjected = true;
        }
    }

    /**
     * This solution must be used instead as the Connect Event does not detect
     *  server switching when on the SaiCoPvP Network.
     * @param event
     */
    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (event.world.isRemote) {
            handleTeleportCheck();
        }
    }

    private void handleTeleportCheck(){
        Minecraft mc = Minecraft.getMinecraft();
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (mc.thePlayer != null && mc.theWorld != null) {
                    if (mc.getCurrentServerData() != null) {
                        if (mc.getCurrentServerData().serverName.contains("SaiCoPvP")) {
                            mc.thePlayer.sendChatMessage("/tpf faction");
                        }
                    } else {
                        String messageBold = "\u00a7c\u00a7lIn singleplayer or server data null";
                        ClientEventHandler.addChatMessage(messageBold);
                    }
                }
            }
        }, 750);
    }

    private void injectNettyHandler() {
        try {
            NetworkManager networkManager = Minecraft.getMinecraft().thePlayer.sendQueue.getNetworkManager();
            Channel channel = networkManager.channel();
            channel.pipeline().addBefore("packet_handler", "chat_interceptor", new ChannelDuplexHandler() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    if (msg instanceof C01PacketChatMessage) {
                        C01PacketChatMessage packet = (C01PacketChatMessage) msg;
                        String message = packet.getMessage();
                        if (message.startsWith("/f join")) {
                            super.write(ctx, msg, promise);
                            handleTeleportCheck();
                            return;
                        }
                    }
                    super.write(ctx, msg, promise);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}