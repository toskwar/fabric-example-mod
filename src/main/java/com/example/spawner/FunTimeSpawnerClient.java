package com.example.spawner;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.List;

public class FunTimeSpawnerClient implements ClientModInitializer {

    private static CreeperEntity clientCreeper;
    private static final List<BlockPos> nearbyEndStones = new ArrayList<>();
    private static float rotationTicks = 0.0f;

    @Override
    public void onInitializeClient() {
        // 1. Звук при ударе по эндерняку
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient && world.getBlockState(pos).isOf(Blocks.END_STONE)) {
                world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.BLOCK_SPAWNER_HIT, SoundCategory.BLOCKS, 1.0f, 1.0f, false);
            }
            return ActionResult.PASS;
        });

        // 2. Звук при ломании эндерняка
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient && state.isOf(Blocks.END_STONE)) {
                world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.BLOCK_SPAWNER_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f, false);
            }
            return true;
        });

        // 3. Сканирование блоков и частицы
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) return;

            rotationTicks += 15.0f;

            // Каждые 20 тиков ищем эндерняк в радиусе 16 блоков
            if (client.world.getTime() % 20 == 0) {
                nearbyEndStones.clear();
                BlockPos playerPos = client.player.getBlockPos();
                int radius = 16;

                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            BlockPos checkPos = playerPos.add(x, y, z);
                            if (client.world.getBlockState(checkPos).isOf(Blocks.END_STONE)) {
                                nearbyEndStones.add(checkPos);
                            }
                        }
                    }
                }
            }

            // Спавн частиц пламени и дыма
            for (BlockPos pos : nearbyEndStones) {
                if (client.world.random.nextInt(4) == 0) {
                    double px = pos.getX() + client.world.random.nextDouble();
                    double py = pos.getY() + client.world.random.nextDouble();
                    double pz = pos.getZ() + client.world.random.nextDouble();

                    client.world.addParticle(ParticleTypes.FLAME, px, py, pz, 0.0, 0.0, 0.0);
                    client.world.addParticle(ParticleTypes.SMOKE, px, py, pz, 0.0, 0.0, 0.0);
                }
            }
        });

        // 4. Отрисовка крипера внутри блоков эндерняка
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || nearbyEndStones.isEmpty()) return;

            MatrixStack matrices = context.matrixStack();
            VertexConsumerProvider consumers = context.consumers();
            if (consumers == null) return;

            // Ленивая инициализация фантомного крипера для рендера
            if (clientCreeper == null) {
                clientCreeper = EntityType.CREEPER.create(client.world);
                if (clientCreeper != null) {
                    clientCreeper.setCharged(true); // Включаем ауру молний
                }
            }

            if (clientCreeper == null) return;

            double camX = context.camera().getPos().x;
            double camY = context.camera().getPos().y;
            double camZ = context.camera().getPos().z;
            float tickDelta = context.tickCounter().getTickDelta(true);

            for (BlockPos pos : nearbyEndStones) {
                matrices.push();
                
                // Центрируем крипера внутри блока
                matrices.translate(pos.getX() - camX + 0.5, pos.getY() - camY + 0.15, pos.getZ() - camZ + 0.5);
                matrices.scale(0.45f, 0.45f, 0.45f);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotationTicks));

                // Передаем саму сущность крипера в диспетчер — это работает на всех версиях 1.21.x
                client.getEntityRenderDispatcher().render(
                        clientCreeper,
                        0.0, 0.0, 0.0,
                        0.0f,
                        tickDelta,
                        matrices,
                        consumers,
                        15728880
                );

                matrices.pop();
            }
        });
    }
}
