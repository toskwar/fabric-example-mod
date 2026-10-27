package com.example.spawner;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class EndstoneSpawnerClient implements ClientModInitializer {

    private static CreeperEntity chargedCreeper;
    // Потокобезопасный список предотвращает вылеты между тиками игры и рендером
    private static final List<BlockPos> nearbyEndStones = new CopyOnWriteArrayList<>();
    private static float rotationTicks = 0.0f;

    @Override
    public void onInitializeClient() {
        System.out.println("[SpawnerMod] Мод запущен! Эндерняк теперь выглядит как спавнер.");

        // Включаем прозрачность (Cutout слой) для Эндерняка, чтобы решетка спавнера не имела черного фона
        BlockRenderLayerMap.INSTANCE.putBlock(Blocks.END_STONE, RenderLayer.getCutout());

        // 1. Звук удара по спавнеру при клике
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient && world.getBlockState(pos).isOf(Blocks.END_STONE)) {
                world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.BLOCK_SPAWNER_HIT, SoundCategory.BLOCKS, 1.0f, 1.0f, false);
            }
            return ActionResult.PASS;
        });

        // 2. Звук спавнера в момент разрушения блока эндерняка
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient && state.isOf(Blocks.END_STONE)) {
                world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.BLOCK_SPAWNER_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f, false);
            }
        });

        // 3. Поиск блоков эндерняка вокруг игрока и частицы огня/дыма
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) return;

            rotationTicks += 10.0f;

            if (chargedCreeper == null) {
                // Исправлено под 1.21.4 (добавлен SpawnReason)
                chargedCreeper = EntityType.CREEPER.create(client.world, SpawnReason.TRIGGERED);
                if (chargedCreeper != null) {
                    // Исправлено под 1.21.4 (зарядка крипера через NBT)
                    NbtCompound nbt = new NbtCompound();
                    nbt.putBoolean("powered", true);
                    chargedCreeper.readNbt(nbt);
                }
            }

            if (client.world.getTime() % 15 == 0) {
                List<BlockPos> tempFound = new java.util.ArrayList<>();
                BlockPos playerPos = client.player.getBlockPos();
                int radius = 12;

                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            BlockPos checkPos = playerPos.add(x, y, z);
                            if (client.world.getBlockState(checkPos).isOf(Blocks.END_STONE)) {
                                tempFound.add(checkPos);
                            }
                        }
                    }
                }
                nearbyEndStones.clear();
                nearbyEndStones.addAll(tempFound);
            }

            for (BlockPos pos : nearbyEndStones) {
                if (client.world.random.nextInt(3) == 0) {
                    double px = pos.getX() + client.world.random.nextDouble();
                    double py = pos.getY() + client.world.random.nextDouble();
                    double pz = pos.getZ() + client.world.random.nextDouble();

                    client.world.addParticle(ParticleTypes.FLAME, px, py, pz, 0.0, 0.0, 0.0);
                    client.world.addParticle(ParticleTypes.SMOKE, px, py, pz, 0.0, 0.0, 0.0);
                }
            }
        });

        // 4. Отрисовка крипера (уменьшен на 20%, повернут на 45°)
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || chargedCreeper == null || nearbyEndStones.isEmpty()) return;

            MatrixStack matrices = context.matrixStack();
            VertexConsumerProvider consumers = context.consumers();
            if (consumers == null) return;

            double camX = context.camera().getPos().x;
            double camY = context.camera().getPos().y;
            double camZ = context.camera().getPos().z;

            for (BlockPos pos : nearbyEndStones) {
                matrices.push();

                matrices.translate(pos.getX() - camX + 0.5, pos.getY() - camY + 0.18, pos.getZ() - camZ + 0.5);
                
                // Уменьшенный размер (-20%)
                matrices.scale(0.32f, 0.32f, 0.32f);

                // Наклон как у оригинального спавнера (45 градусов)
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(45.0f));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(15.0f));

                // Вращение во времени
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotationTicks));

                // Обновление позиции сущности для стабильного Render State
                chargedCreeper.setPos(pos.getX() + 0.5, pos.getY() + 0.18, pos.getZ() + 0.5);

                // Исправлено под 1.21.4 (удален лишний аргумент tickDelta перед matrices)
                client.getEntityRenderDispatcher().render(
                        chargedCreeper,
                        0.0, 0.0, 0.0,
                        0.0f,
                        matrices,
                        consumers,
                        15728880
                );

                matrices.pop();
            }
        });
    }
}
