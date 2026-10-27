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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class EndstoneSpawnerClient implements ClientModInitializer {

    private static CreeperEntity chargedCreeper;
    // Потокобезопасный список исключает конфликты и вылеты между потоком тиков и потоком рендеринга
    private static final List<BlockPos> nearbyEndStones = new CopyOnWriteArrayList<>();
    private static float rotationTicks = 0.0f;

    @Override
    public void onInitializeClient() {
        System.out.println("[SpawnerMod] Клиентский инициализатор EndstoneSpawnerClient запущен!");

        // 1. Звук удара по спавнеру при клике/замахе на Эндерняк
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient && world.getBlockState(pos).isOf(Blocks.END_STONE)) {
                world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.BLOCK_SPAWNER_HIT, SoundCategory.BLOCKS, 1.0f, 1.0f, false);
            }
            return ActionResult.PASS;
        });

        // 2. Звук разрушения спавнера ровно в момент, когда эндерняк полностью сломали
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient && state.isOf(Blocks.END_STONE)) {
                world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.BLOCK_SPAWNER_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f, false);
            }
        });

        // 3. Сканирование блоков вокруг + Создание крипера + Спавн частиц
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) return;

            // Плавное вращение моба внутри спавнера
            rotationTicks += 5.0f;

            // Создаем сущность фантомного заряженного крипера для рендера
            if (chargedCreeper == null) {
                // В 1.21.4 метод create требует SpawnReason
                chargedCreeper = EntityType.CREEPER.create(client.world, SpawnReason.TRIGGERED);
                if (chargedCreeper != null) {
                    // Обходим приватное поле CHARGED через NBT-тег "powered"
                    NbtCompound nbt = new NbtCompound();
                    nbt.putBoolean("powered", true);
                    chargedCreeper.readNbt(nbt);
                }
            }

            // Поиск блоков Эндерняка каждые 15 тиков в радиусе 12 блоков
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
                // Атомарно перезаписываем основной список
                nearbyEndStones.clear();
                nearbyEndStones.addAll(tempFound);
            }

            // Спавн частиц огня и дыма вокруг найденных блоков (как у дефолтного спавнера)
            for (BlockPos pos : nearbyEndStones) {
                if (client.world.random.nextInt(5) == 0) {
                    double px = pos.getX() + client.world.random.nextDouble();
                    double py = pos.getY() + client.world.random.nextDouble();
                    double pz = pos.getZ() + client.world.random.nextDouble();

                    client.world.addParticle(ParticleTypes.FLAME, px, py, pz, 0.0, 0.0, 0.0);
                    client.world.addParticle(ParticleTypes.SMOKE, px, py, pz, 0.0, 0.0, 0.0);
                }
            }
        });

        // 4. Отрисовка (Рендеринг) Крипера под углом 45° с уменьшением размера
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

                // Центрируем крипера внутри блока
                matrices.translate(pos.getX() - camX + 0.5, pos.getY() - camY + 0.18, pos.getZ() - camZ + 0.5);

                // Уменьшаем размер на 20% под габариты спавнера (0.32f)
                matrices.scale(0.32f, 0.32f, 0.32f);

                // Наклон моба на 45 градусов в бок, как у оригинального спавнера в Minecraft
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(45.0f));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(15.0f));

                // Постоянное вращение вокруг своей оси Y
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotationTicks));

                // Принудительно обновляем позицию сущности на клиенте, чтобы Render States в 1.21.4 сработали корректно
                chargedCreeper.setPos(pos.getX() + 0.5, pos.getY() + 0.18, pos.getZ() + 0.5);

                // Рендерим моба (В сигнатуре 1.21.4 отсутствует аргумент tickDelta)
                client.getEntityRenderDispatcher().render(
                        chargedCreeper,
                        0.0, 0.0, 0.0,
                        0.0f,
                        matrices,
                        consumers,
                        15728880 // Максимальный уровень освещения (моб будет ярким внутри блока)
                );

                matrices.pop();
            }
        });
    }
}
