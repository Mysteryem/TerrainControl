package com.khorn.terraincontrol.generator;

import com.khorn.terraincontrol.DefaultMaterial;
import com.khorn.terraincontrol.LocalBiome;
import com.khorn.terraincontrol.LocalWorld;
import com.khorn.terraincontrol.TerrainControl;
import com.khorn.terraincontrol.configuration.BiomeConfig;
import com.khorn.terraincontrol.configuration.TCDefaultValues;
import com.khorn.terraincontrol.configuration.WorldConfig;
import com.khorn.terraincontrol.generator.noise.NoiseGeneratorNewOctaves;
import com.khorn.terraincontrol.generator.resourcegens.Resource;

import java.util.Random;
import java.util.logging.Level;

public class ObjectSpawner
{

    private WorldConfig worldSettings;
    private Random rand;
    private LocalWorld world;
    private NoiseGeneratorNewOctaves noiseGen;
    private double[] reusableChunkNoiseArray;

    public ObjectSpawner(WorldConfig wrk, LocalWorld localWorld)
    {
        this.worldSettings = wrk;
        this.rand = new Random();
        this.world = localWorld;
        this.noiseGen = new NoiseGeneratorNewOctaves(new Random(world.getSeed()), 4);
    }

    public void populate(int chunkX, int chunkZ)
    {
        // Get the corner block coords
        int x = chunkX * 16;
        int z = chunkZ * 16;

        // Get the BiomeConfig of the other corner
        int biomeId = world.getBiomeId(x + 15, z + 15);
        BiomeConfig localBiomeConfig = this.worldSettings.biomeConfigs[biomeId];

        // Null check
        if (localBiomeConfig == null)
        {
            TerrainControl.log(Level.CONFIG, "Unknown biome id {0} at {1},{2}  (chunk {3},{4}). Population failed.", new Object[] {biomeId, (x + 15), (z + 15), chunkX, chunkZ});
            return;
        }

        // Get the random generator
        long resourcesSeed = worldSettings.resourcesSeed != 0L ? worldSettings.resourcesSeed : world.getSeed();
        this.rand.setSeed(resourcesSeed);
        long l1 = this.rand.nextLong() / 2L * 2L + 1L;
        long l2 = this.rand.nextLong() / 2L * 2L + 1L;
        this.rand.setSeed(chunkX * l1 + chunkZ * l2 ^ resourcesSeed);

        // Generate structures
        boolean hasGeneratedAVillage = world.placeDefaultStructures(rand, chunkX, chunkZ);

        // Fire event
        TerrainControl.firePopulationStartEvent(world, rand, hasGeneratedAVillage, chunkX, chunkZ);
        
        // Complex surface blocks
        placeComplexSurfaceBlocks(chunkX, chunkZ);

        // Resource sequence
        for (Resource res : localBiomeConfig.resourceSequence)
        {
            world.setChunksCreations(false);
            res.process(world, rand, hasGeneratedAVillage, chunkX, chunkZ);
        }

        // Animals
        world.placePopulationMobs(localBiomeConfig, rand, chunkX, chunkZ);

        // Snow and ice
        freezeChunk(chunkX, chunkZ);

        // Replace blocks
        world.replaceBlocks();

        // Replace biomes
        world.replaceBiomes();

        // Replace settings after Reload command
        if (this.worldSettings.isDeprecated)
            this.worldSettings = this.worldSettings.newSettings;

        // Fire event
        TerrainControl.firePopulationEndEvent(world, rand, hasGeneratedAVillage, chunkX, chunkZ);
    }
    
    protected void placeComplexSurfaceBlocks(int chunkX, int chunkZ)
    {
        this.reusableChunkNoiseArray = this.noiseGen.a(this.reusableChunkNoiseArray, chunkX * 16, chunkZ * 16, 16, 16, 0.0625D, 0.0625D, 1.0D);

        int x = chunkX * 16 + 8;
        int z = chunkZ * 16 + 8;
        for (int i = 0; i < 16; i++)
        {
            for (int j = 0; j < 16; j++)
            {
                int blockToFreezeX = x + i;
                int blockToFreezeZ = z + j;
                // Using the calculated biome id so that ReplaceToBiomeName can't mess up the ids
                BiomeConfig biomeConfig = this.worldSettings.biomeConfigs[this.world.getBiomeId(blockToFreezeX, blockToFreezeZ)];
                if (biomeConfig != null && biomeConfig.surfaceAndGroundControl != null)
                {
                    double noise = this.reusableChunkNoiseArray[i + j * 16];
                    biomeConfig.surfaceAndGroundControl.spawn(world, noise, blockToFreezeX, blockToFreezeZ);
                }
            }
        }
    }

    protected void freezeChunk(int chunkX, int chunkZ)
    {
        int x = chunkX * 16 + 8;
        int z = chunkZ * 16 + 8;
        for (int i = 0; i < 16; i++)
        {
            for (int j = 0; j < 16; j++)
            {
                int blockToFreezeX = x + i;
                int blockToFreezeZ = z + j;
                freezeColumn(blockToFreezeX, blockToFreezeZ);
            }
        }
    }

    protected void freezeColumn(int x, int z)
    {
        // Using the calculated biome id so that ReplaceToBiomeName can't mess up the ids
        BiomeConfig biomeConfig = world.getSettings().biomeConfigs[world.getBiomeId(x, z)];
        if (biomeConfig != null)
        {
            LocalBiome biome = biomeConfig.Biome;
            int blockToFreezeY = world.getHighestBlockYAt(x, z);
            if (blockToFreezeY > 0 && biome.getTemperatureAt(x, blockToFreezeY, z) < TCDefaultValues.snowAndIceMaxTemp.floatValue())
            {
                // Ice has to be placed one block in the world
                if (DefaultMaterial.getMaterial(world.getTypeId(x, blockToFreezeY - 1, z)).isLiquid())
                {
                    world.setBlock(x, blockToFreezeY - 1, z, biomeConfig.iceBlock, 0);
                } else
                {
                    // Snow has to be placed on an empty space on a
                    // block that accepts snow in the world
                    if (world.getMaterial(x, blockToFreezeY, z) == DefaultMaterial.AIR)
                    {
                        if (world.getMaterial(x, blockToFreezeY - 1, z).canSnowFallOn())
                        {
                            world.setBlock(x, blockToFreezeY, z, DefaultMaterial.SNOW.id, 0);
                        }
                    }
                }
            }
        }
    }

}