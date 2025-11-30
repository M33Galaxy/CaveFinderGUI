# CaveFinderGUI

A GUI program for the CaveFinder project, for searching deep caves. This program works for both Java and Bedrock Edition 1.18+.

## **How to run?**

**This program requires Java 17 or above.** Make sure you have downloaded Java.

You can right click the jar file and choose "Java(TM) Platform SE binary" as the open method.

You can also use cmd to run it: open cmd.exe (you can search it in the search tab) , and use the following commands to run it:

Type "java -jar" then type **space**, drag the jar file to the window and type enter.

Type "java -Xms2048m -Xmx4096m -jar" then type **space**, drag the jar file to the window and type enter. This is for allocating memory.

You can change the -Xms2048m -Xmx4096m to the memory amount you want. But I suggest to allocate maximum memory 4GB or more for this program. (It's memory-consuming)

**You have to wait 10-20 seconds to fully open it.** This is because it need to initialize Seedchecker first, which will take a bit long time. In this stage don't do other things.

## **How to use?**

Use the **Start Filtering** button to start filtering, and **Stop** Button to stop filtering.

The top-left part is **Parameter Settings**, including:

**Cave Depth**: The cave depth you want, default is -50, range is from -50 to 0.

**Thread Count**: The thread amount you want to use, range is from 1 to your computer's thread amount.

**X/Z coordinates**: The coordinate you want to search for caves, range is from -30,000,000 to 30,000,000.

**Segment size**: Only for incremental mode. Segments if the total seed amount is greater than the value. It depends on your memory allocation. 
For 4GB memory allocated, the suggested segment size is 5-10 million. If you have more memory, you can try greater segment size.
For structureseed search you probably won't care about this, because it searches for 65536 worldseeds for every structureseed and you won't reach the segment size within a few days.

**Check Height**: Import Seedchecker to check the exact height. **This is slower, but much more consistent on giving deep caves. The higher the height, the slower it'll be.** (e.g. y=0 is slower than y=-10) 

**Filter BE impossible seeds**: This is a currently hardcoded mode for searching "impossible seeds" on Bedrock Edition. To search for this, you need to set the X/Z coords to (0,0), and I suggest you to enable **Check Height** for this. (The conditions are strict so it won't be much slower to enable check height) This condition will disable **"Entrance1 only"** (Because it's already Entrance1 only) and **ALL Biome Climate Parameters** (It has the biome climate conditions itself). This is a fast condition, but it might take you over an hour to find a potential seed, and dozens of hours to find an impossible seed (Spawn in lava and always respawn in lava, which requires no waterfalls and topsolid blocks within the spawn radius) 

**Entrance1 Only**: It's faster, and more likely to give you larger exposed caves (will ignore some small deep caves).

Next part is **Filter Mode**.

**Incremental**: Incrementally searching from a seed range in "Seed Input".

**Filter from list** : Searching from a seed list. You can load it from file.

**StructureSeed** : Searches for the seeds in the range/seed list and their sister seeds (low 48 bits same but high 16 bits different, 65536 in total). Sister seeds has same potential structure coords and almost same nether and end dimension. Choosing this is much better if you want to search for a structure in cave (e.g. villages, outposts, igloos) , just use cubiomes-viewer to search for low-48 bit seeds that has these structures and load them as a file into this program.

**WorldSeed** : Only Search for the seeds in the range/seed list.

The Bottom-left part is **Seed Input**.

**Start Seed & End Seed**: For Incremental mode. You don't need this for Filter from List mode.

**Seed List (one per line)**: For Filter from List mode. It's the seed list you want to search. You can load a seed file into the program (1 seed per line, no other characters or symbols). It's not suggested to load a seed list with over 10 million seeds. You don't need this for Incremental mode.

The top-right part is **Biome Climate Parameters**.

It includes 8 types. **Temperature, Humidity, Erosion, Ridge(Weirdness), Continentalness** determines the biome. It's better to add these climate parameters if you want to search an exposed cave in specific biome. They are searching in order of fastest to slowest in the program (Temperature, Humidity, Erosion and Ridge(Weirdness) are faster than cave entrance checking, though erosion and weirdness are still slower than entrance1). You can search on the Minecraft Wiki to look for the biome climate parameter condition for the biome you need. Structures' generation also determines on 
biomes. Weirdness betweeen -0.05 and 0.05 mostly means rivers, Continentalness below -0.19 means ocean, for better avoiding waterlogged caves I set the default excluding weirdness to between -0.16 and 0.16, and default excluding continalness to below -0.11.

**Entrance, Cheese and AquiferFloodlevelFloodness** aren't Biome Climate Parameters, but they are still here for advanced searching. Normally, Entrance and Cheese <0 means caves, and 99% non-waterlogged exposed caves generates at AquiferFloodlevelFloodness below 0.4. Lower Entrance and Cheese might means larger caves, and lower AquiferFloodlevelFloodness might means less chance to get waterlogged caves.

The bottom-right part is the **log**, which shows information while searching.

Below these parts, you can see an **Export Path**, which is the filtered list you want to export to. If that list already exists, the program will show a warning to you: "Result file already exists 
and will be overwritten. Continue?" 

And at the bottom of the GUI there's a **progress bar** which shows the finished seed amount and searching speed.

## **Libraries mainly used in this program**

https://github.com/KalleStruik/noise-sampler

https://github.com/jellejurre/seed-checker/tree/1.18.1

## Credits

https://github.com/SunnySlopes

https://github.com/jellejurre

https://github.com/KalleStruik
