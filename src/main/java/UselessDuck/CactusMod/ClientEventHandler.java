package UselessDuck.CactusMod;

import net.minecraft.block.Block;
import net.minecraft.block.BlockTripWire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientEventHandler {

    private static final Map<BlockPos, StringRenderData> cachedStringBlocks = new ConcurrentHashMap<>();
    private static final List<StringRenderData> renderList = new ArrayList<>();
    private static final List<StringRenderData> visibleStrings = new ArrayList<>();
    private long lastScanTime = 0;
    private long lastVisibilityCheck = 0;
    private static final long SCAN_INTERVAL = 200;
    private static final long VISIBILITY_CHECK_INTERVAL = 50;
    private static final int MAX_RENDER_DISTANCE = 21;
    private static final int REDUCED_SCAN_DISTANCE = 21;
    private BlockPos lastPlayerPos = null;
    private float lastPlayerYaw = 0;
    private float lastPlayerPitch = 0;
    private int frameCounter = 0;
    private static final double FOV_MARGIN = 25.0;
    private static final int MAX_VISIBLE_STRINGS = 100;
    private static final double MAX_DISTANCE_SQ = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;
    
    private static class StringRenderData {
        final BlockPos pos;
        final boolean north, south, east, west;
        final double x, y, z;
        final double centerX, centerY, centerZ;
        double lastDistanceSq;
        
        StringRenderData(BlockPos pos, boolean north, boolean south, boolean east, boolean west) {
            this.pos = pos;
            this.north = north;
            this.south = south;
            this.east = east;
            this.west = west;
            this.x = pos.getX();
            this.y = pos.getY() + 0.09375;
            this.z = pos.getZ();
            this.centerX = pos.getX() + 0.5;
            this.centerY = pos.getY() + 0.5;
            this.centerZ = pos.getZ() + 0.5;
        }
    }

    private static boolean texturesInitialized = false;
    private static float[] stringTextureCoords = new float[4];
    private static Map<IBlockState, float[]> stringStateTextures = new HashMap<>();

    public ClientEventHandler() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent event) {
        if (!Keybinds.isEnabled || event.world == null || event.entityPlayer == null || event.face == null || event.pos == null) {
            return;
        }

        if (event.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
            return;
        }

        BlockPos targetPos = null;

        switch (event.face) {
            case UP:
                targetPos = event.pos.up();
                break;
            case DOWN:
                targetPos = event.pos.down();
                break;
            case NORTH:
                targetPos = event.pos.north();
                break;
            case SOUTH:
                targetPos = event.pos.south();
                break;
            case EAST:
                targetPos = event.pos.east();
                break;
            case WEST:
                targetPos = event.pos.west();
                break;
        }

        if (targetPos != null) {
            Block blockBelow = null;

            try {
                blockBelow = event.world.getBlockState(targetPos.down()).getBlock();
            } catch (Exception e) {
                return;
            }
            String heldItemName = event.entityPlayer.getHeldItem() != null ? event.entityPlayer.getHeldItem().getUnlocalizedName() : "";
            if (heldItemName.contains("tile.sand") &&
                    (blockBelow.isAir(event.world, targetPos.down()) || blockBelow.getUnlocalizedName().contains("sand") || blockBelow.getUnlocalizedName().contains("cactus"))) {
                event.setCanceled(true);
            } else if (heldItemName.contains("item.string") && !blockBelow.isAir(event.world, targetPos.down())) {
                event.setCanceled(true);
            } else if (heldItemName.contains("tile.cactus") && blockBelow.getUnlocalizedName().contains("cactus")) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        if (!ShowStringCommand.isShowStringEnabled()) {
            return;
        }
        
        Block placedBlock = event.state.getBlock();
        if (placedBlock == Blocks.tripwire || Block.getIdFromBlock(placedBlock) == 132) {
            BlockPos pos = event.pos;
            World world = event.world;
            
            boolean north = isTripwireConnected(world, pos, EnumFacing.NORTH);
            boolean south = isTripwireConnected(world, pos, EnumFacing.SOUTH);
            boolean east = isTripwireConnected(world, pos, EnumFacing.EAST);
            boolean west = isTripwireConnected(world, pos, EnumFacing.WEST);
            
            StringRenderData data = new StringRenderData(pos, north, south, east, west);
            cachedStringBlocks.put(pos, data);
            
            renderList.clear();
            renderList.addAll(cachedStringBlocks.values());
            
            lastVisibilityCheck = 0;
        }
    }
    
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!ShowStringCommand.isShowStringEnabled()) {
            return;
        }
        
        Block brokenBlock = event.state.getBlock();
        if (brokenBlock == Blocks.tripwire || Block.getIdFromBlock(brokenBlock) == 132) {
            BlockPos pos = event.pos;
            cachedStringBlocks.remove(pos);
            
            renderList.clear();
            renderList.addAll(cachedStringBlocks.values());
            
            visibleStrings.removeIf(data -> data.pos.equals(pos));
        }
    }
    
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ShowStringCommand.isShowStringEnabled()) {
            return;
        }
        
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        
        frameCounter++;
        if (frameCounter % 10 == 0) {
            checkForNewStrings(mc);
        }
    }
    
    private void checkForNewStrings(Minecraft mc) {
        BlockPos playerPos = mc.thePlayer.getPosition();
        World world = mc.theWorld;
        
        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    
                    if (cachedStringBlocks.containsKey(pos)) {
                        continue;
                    }
                    
                    try {
                        Block block = world.getBlockState(pos).getBlock();
                        
                        if (block == Blocks.tripwire || Block.getIdFromBlock(block) == 132) {
                            boolean north = isTripwireConnected(world, pos, EnumFacing.NORTH);
                            boolean south = isTripwireConnected(world, pos, EnumFacing.SOUTH);
                            boolean east = isTripwireConnected(world, pos, EnumFacing.EAST);
                            boolean west = isTripwireConnected(world, pos, EnumFacing.WEST);
                            
                            StringRenderData data = new StringRenderData(pos, north, south, east, west);
                            cachedStringBlocks.put(pos, data);
                            
                            renderList.clear();
                            renderList.addAll(cachedStringBlocks.values());
                            
                            lastVisibilityCheck = 0;
                        }
                        
                    } catch (Exception e) {
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!ShowStringCommand.isShowStringEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        frameCounter++;
        long currentTime = System.currentTimeMillis();
        BlockPos currentPlayerPos = mc.thePlayer.getPosition();
        
        boolean shouldScan = false;
        if (currentTime - lastScanTime > SCAN_INTERVAL) {
            if (lastPlayerPos == null || !lastPlayerPos.equals(currentPlayerPos) || frameCounter % 40 == 0) {
                shouldScan = true;
                lastScanTime = currentTime;
                lastPlayerPos = currentPlayerPos;
            }
        }
        
        if (shouldScan) {
            updateStringCache(mc);
        }

        boolean shouldCheckVisibility = false;
        if (currentTime - lastVisibilityCheck > VISIBILITY_CHECK_INTERVAL) {
            float currentYaw = mc.thePlayer.rotationYaw;
            float currentPitch = mc.thePlayer.rotationPitch;
            
            if (Math.abs(currentYaw - lastPlayerYaw) > 5.0f || 
                Math.abs(currentPitch - lastPlayerPitch) > 5.0f ||
                !currentPlayerPos.equals(lastPlayerPos)) {
                
                shouldCheckVisibility = true;
                lastVisibilityCheck = currentTime;
                lastPlayerYaw = currentYaw;
                lastPlayerPitch = currentPitch;
            }
        }
        
        if (shouldCheckVisibility) {
            cleanup(mc);
        }

        if (!visibleStrings.isEmpty()) {
            RenderRGBString(mc, event.partialTicks, currentTime);
        }
    }

    private void updateStringCache(Minecraft mc) {
        BlockPos playerPos = mc.thePlayer.getPosition();
        cachedStringBlocks.entrySet().removeIf(entry -> {
            double distSq = entry.getKey().distanceSq(playerPos);
            return distSq > MAX_DISTANCE_SQ;
        });
        
        World world = mc.theWorld;
        
        for (int x = -REDUCED_SCAN_DISTANCE; x <= REDUCED_SCAN_DISTANCE; x++) {
            for (int y = -REDUCED_SCAN_DISTANCE; y <= REDUCED_SCAN_DISTANCE; y++) {
                for (int z = -REDUCED_SCAN_DISTANCE; z <= REDUCED_SCAN_DISTANCE; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    
                    if (cachedStringBlocks.containsKey(pos)) {
                        continue;
                    }
                    
                    try {
                        Block block = world.getBlockState(pos).getBlock();
                        
                        if (block == Blocks.tripwire || Block.getIdFromBlock(block) == 132) {
                            double distance = mc.thePlayer.getDistanceSq(pos);
                            if (distance <= MAX_DISTANCE_SQ) {
                                boolean north = isTripwireConnected(world, pos, EnumFacing.NORTH);
                                boolean south = isTripwireConnected(world, pos, EnumFacing.SOUTH);
                                boolean east = isTripwireConnected(world, pos, EnumFacing.EAST);
                                boolean west = isTripwireConnected(world, pos, EnumFacing.WEST);
                                
                                cachedStringBlocks.put(pos, new StringRenderData(pos, north, south, east, west));
                            }
                        }
                        
                    } catch (Exception e) {
                    }
                }
            }
        }
        
        renderList.clear();
        renderList.addAll(cachedStringBlocks.values());
    }
    
    private void cleanup(Minecraft mc) {
        visibleStrings.clear();
        
        if (renderList.isEmpty()) {
            return;
        }
        
        double playerX = mc.thePlayer.posX;
        double playerY = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double playerZ = mc.thePlayer.posZ;
        
        Vec3 lookVec = mc.thePlayer.getLookVec();
        double lookX = lookVec.xCoord;
        double lookY = lookVec.yCoord;
        double lookZ = lookVec.zCoord;
        
        float fov = mc.gameSettings.fovSetting;
        double fovRadians = Math.toRadians(fov + FOV_MARGIN);
        double cosFov = Math.cos(fovRadians * 0.5);
        
        for (StringRenderData data : renderList) {
            double dx = data.centerX - playerX;
            double dy = data.centerY - playerY;
            double dz = data.centerZ - playerZ;
            data.lastDistanceSq = dx * dx + dy * dy + dz * dz;
        }
        
        renderList.sort(Comparator.comparingDouble(a -> a.lastDistanceSq));
        
        int visibleCount = 0;
        int maxCheck = Math.min(renderList.size(), MAX_VISIBLE_STRINGS * 2);
        
        for (int i = 0; i < maxCheck && visibleCount < MAX_VISIBLE_STRINGS; i++) {
            StringRenderData data = renderList.get(i);
            
            if (data.lastDistanceSq > MAX_DISTANCE_SQ) {
                continue;
            }
            
            double dx = data.centerX - playerX;
            double dy = data.centerY - playerY;
            double dz = data.centerZ - playerZ;
            double distance = Math.sqrt(data.lastDistanceSq);
            
            if (distance < 0.1) {
                visibleStrings.add(data);
                visibleCount++;
                continue;
            }
            
            dx /= distance;
            dy /= distance;
            dz /= distance;
            
            double dotProduct = lookX * dx + lookY * dy + lookZ * dz;
            
            if (dotProduct >= cosFov) {
                visibleStrings.add(data);
                visibleCount++;
            }
        }
    }

    private void RenderRGBString(Minecraft mc, float partialTicks, long currentTime) {
        double playerX = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * partialTicks;
        double playerY = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * partialTicks;
        double playerZ = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * partialTicks;
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glTranslated(-playerX, -playerY, -playerZ);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        
        float time = (currentTime % 6000) / 6000.0F;
        double stringHeight = 0.03125;
        
        for (StringRenderData data : visibleStrings) {
            try {
                float positionOffset = (data.pos.getX() + data.pos.getY() + data.pos.getZ()) * 0.1F;
                float hue = (time + positionOffset) % 1.0F;
                Color color = Color.getHSBColor(hue, 1.0F, 1.0F);
                float red = color.getRed() / 255.0F;
                float green = color.getGreen() / 255.0F;
                float blue = color.getBlue() / 255.0F;
                
                Batching(worldRenderer, data, stringHeight, red, green, blue, 1.0F);
                
            } catch (Exception e) {
            }
        }
        tessellator.draw();
        GL11.glPopAttrib();
        GL11.glPopMatrix();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
    
    private void Batching(WorldRenderer worldRenderer, StringRenderData data, double stringHeight, float red, float green, float blue, float alpha) {
        boolean hasNorthSouth = data.north || data.south;
        boolean hasEastWest = data.east || data.west;
        
        if (!data.north && !data.south && !data.east && !data.west) {
            AddingTOBatch(worldRenderer, data.x + 0.5, data.y, data.z + 0.0, data.x + 0.5, data.y, data.z + 1.0, stringHeight, red, green, blue, alpha);
        } else {
            if (hasNorthSouth) {
                AddingTOBatch(worldRenderer, data.x + 0.5, data.y, data.z + 0.0, data.x + 0.5, data.y, data.z + 1.0, stringHeight, red, green, blue, alpha);
            }
            
            if (hasEastWest) {
                AddingTOBatch(worldRenderer, data.x + 0.0, data.y, data.z + 0.5, data.x + 1.0, data.y, data.z + 0.5, stringHeight, red, green, blue, alpha);
            }
        }
    }
    
    private void AddingTOBatch(WorldRenderer worldRenderer, double x1, double y1, double z1, double x2, double y2, double z2, double height, float red, float green, float blue, float alpha) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dz * dz);
        
        if (length < 0.001) return;
        
        dx /= length;
        dz /= length;
        
        double perpX = -dz * 0.025;
        double perpZ = dx * 0.025;
        worldRenderer.pos(x1 - perpX, y1, z1 - perpZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(x2 - perpX, y2, z2 - perpZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(x2 + perpX, y2, z2 + perpZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(x1 + perpX, y1, z1 + perpZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(x1 - perpX, y1 + height, z1 - perpZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(x1 + perpX, y1 + height, z1 + perpZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(x2 + perpX, y2 + height, z2 + perpZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(x2 - perpX, y2 + height, z2 - perpZ).color(red, green, blue, alpha).endVertex();
    }
    
    private void initializeStringTextures(Minecraft mc) {
        try {
            BlockRendererDispatcher dispatcher = mc.getBlockRendererDispatcher();
            
            IBlockState tripwireState = Blocks.tripwire.getDefaultState();
            IBakedModel tripwireModel = dispatcher.getModelFromBlockState(tripwireState, mc.theWorld, new BlockPos(0, 0, 0));
            
            stringTextureCoords = getTextureCoords(tripwireModel);
            
            try {
                if (Blocks.tripwire instanceof BlockTripWire) {
                    for (boolean attached : new boolean[]{false, true}) {
                        for (boolean powered : new boolean[]{false, true}) {
                            try {
                                IBlockState state = tripwireState
                                    .withProperty(BlockTripWire.ATTACHED, attached)
                                    .withProperty(BlockTripWire.POWERED, powered);
                                IBakedModel model = dispatcher.getModelFromBlockState(state, mc.theWorld, new BlockPos(0, 0, 0));
                                float[] coords = getTextureCoords(model);
                                stringStateTextures.put(state, coords);
                            } catch (Exception e) {
                            }
                        }
                    }
                }
            } catch (Exception e) {
            }
            
            texturesInitialized = true;
        } catch (Exception e) {
        }
    }
    
    private static float[] getTextureCoords(IBakedModel model) {
        List<BakedQuad> quads = model.getGeneralQuads();
        float minU = Float.MAX_VALUE;
        float minV = Float.MAX_VALUE;
        float maxU = Float.MIN_VALUE;
        float maxV = Float.MIN_VALUE;

        if (quads.size() > 0) {
            BakedQuad face = quads.get(0);
            int[] vertexData = face.getVertexData();
            for (int i = 0; i < 4; i++) {
                int index = i * 7;
                float tX = Float.intBitsToFloat(vertexData[index + 4]);
                float tY = Float.intBitsToFloat(vertexData[index + 5]);
                minU = Math.min(minU, tX);
                minV = Math.min(minV, tY);
                maxU = Math.max(maxU, tX);
                maxV = Math.max(maxV, tY);
            }
        }

        return new float[]{minU, minV, maxU, maxV};
    }
    
    private boolean isTripwireConnected(World world, BlockPos pos, EnumFacing facing) {
        BlockPos adjacentPos = pos.offset(facing);
        Block adjacentBlock = world.getBlockState(adjacentPos).getBlock();
        return adjacentBlock == Blocks.tripwire || adjacentBlock == Blocks.tripwire_hook;
    }
    
    private boolean GetTripwireDirection(World world, BlockPos pos) {
        try {
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() == Blocks.tripwire) {
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    public static void addChatMessage(String message) {
        if (Minecraft.getMinecraft().thePlayer != null) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.LIGHT_PURPLE + message));
        }
    }
}

