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
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.List;

public class EndstoneSpawnerClient implements ClientModInitializer {

    private static CreeperEntity chargedCreeper;
    private static final List<BlockPos> nearbyEndStones = new ArrayList<>();
    private static float rotationTicks = 0.0f;

    @Override
    public void onInitializeClient() {

        // 1. Звук удара спавнера при клике по эндерняку
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient && world.getBlockState(pos).isOf(Blocks.END_STONE)) {
                world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.BLOCK_SPAWNER_HIT, SoundCategory.BLOCKS, 1.0f, 1.0f, false);
            }
            return ActionResult.PASS;
        });

        // 2. Звук ломания спавнера при разрушении эндерняка
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient && state.isOf(Blocks.END_STONE)) {
                world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.BLOCK_SPAWNER_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f, false);
            }
            return true;
        });

        // 3. Сканирование блоков эндерняка вокруг + Заряженный крипер + Частицы
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) return;

            // Скорость вращения моба
            rotationTicks += 10.0f;

            // Создаем сущность фантомного заряженного крипера для рендера
            if (chargedCreeper == null) {
                // Передаем SpawnReason.TRIGGERED вторым аргументом (Исправление №1)
                chargedCreeper = EntityType.CREEPER.create(client.world, SpawnReason.TRIGGERED);
                if (chargedCreeper != null) {
                    // Заряжаем через запись NBT, так как CHARGED приватный (Исправление №2)
                    NbtCompound nbt = new NbtCompound();
                    nbt.putBoolean("powered", true);
                    chargedCreeper.readNbt(nbt);
                }
            }

            // Поиск блоков Эндерняка каждые 15 тиков в радиусе 12 блоков
            if (client.world.getTime() % 15 == 0) {
                nearbyEndStones.clear();
                BlockPos playerPos = client.player.getBlockPos();
                int radius = 12;

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

            // Частицы пламени и дыма, как у стандартного спавнера
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

        // 4. Отрисовка (Рендеринг) вращающегося Крипера внутри блоков
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

                // Центрируем крипера внутри блока эндерняка
                matrices.translate(pos.getX() - camX + 0.5, pos.getY() - camY + 0.2, pos.getZ() - camZ + 0.5);

                // Уменьшаем размер моба под габариты спавнера
                matrices.scale(0.4f, 0.4f, 0.4f);

                // Вращаем вокруг оси Y
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotationTicks));

                // Рендерим моба — удален лишний аргумент tickDelta (Исправление №3)
                client.getEntityRenderDispatcher().render(
                        chargedCreeper,
                        0.0, 0.0, 0.0,
                        0.0f,
                        matrices,
                        consumers,
                        15728880 // Максимальная освещенность
                );

                matrices.pop();
            }
        });
    }
}
