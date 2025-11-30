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

**Filter BE impossible seeds**: This is a currently hardcoded mode for searching "impossible seeds" on Bedrock Edition. To search for this, you need to set the X/Z coords to (0,0), and I suggest you to enable **Check Height** for this. (The conditions are strict so it won't be much slower to enable check height) This condition will disable **"Entrance1 only"** option (Because it's already Entrance1 only) and **ALL Biome Climate Parameters options** (It has the biome climate conditions itself). This is a fast condition, but it might take you over an hour to find a potential seed, and dozens of hours to find an impossible seed (Spawn in lava and always respawn in lava, which requires no waterfalls and topsolid blocks within the spawn radius) 

**Entrance1 Only**: It's faster, and more likely to give you larger exposed caves (will ignore some small deep caves).

Next part is **Filter Mode**.

**Incremental**: Incrementally searching from a seed range in "Seed Input".

**Filter from list** : Searching from a seed list. You can load it from file.

**StructureSeed** : Searches for the seeds in the range/seed list and their sister seeds (low 48 bits same but high 16 bits different, 65536 in total). Sister seeds has same potential structure coords and almost same nether and end dimension. Choosing this is much better if you want to search for a structure in cave (e.g. villages, outposts, igloos) , just use cubiomes-viewer to search for low-48 bit seeds that has these structures and load them as a file into this program.

**WorldSeed** : Only Search for the seeds in the range/seed list.

The Bottom-left part is **Seed Input**.

**Start Seed & End Seed**: For Incremental mode. You don't need this for Filter from List mode.

**Seed List (one per line)**: For Filter from List mode. It's the seed list you want to search. You can load a seed file into the program (1 seed per line, no other characters or symbols). **It's not suggested to load a seed list with over 10 million seeds.** You don't need this for Incremental mode.

The top-right part is **Biome Climate Parameters**.

It includes 8 types. **Temperature, Humidity, Erosion, Ridge(Weirdness), Continentalness** determines the biome. It's better to add these climate parameters if you want to search an exposed cave in specific biome. They are searching in order of fastest to slowest in the program (Temperature, Humidity, Erosion and Ridge(Weirdness) are faster than complete cave entrance checking, though erosion and weirdness are still slower than entrance1). You can search on the Minecraft Wiki to look for the biome climate parameter condition for the biome you need. Structures' generation also determines on 
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

## 中文翻译：

## **洞穴查找器GUI**

一个用于搜索很深的露天洞穴的GUI程序。这个程序在Java版和基岩版1.18+基本通用。

## **如何运行**

**此程序需要 Java 17 或更高版本**。 请确保您已下载 Java。

您可以右键单击 jar 文件，并选择 "Java(TM) Platform SE binary" 作为打开方式。

您也可以使用 cmd 来运行：打开 cmd.exe（您可以在搜索栏中搜索它），然后使用以下命令运行：

输入 "java -jar"，然后输入**空格**，将 jar 文件拖到窗口中并按回车。

输入 "java -Xms2048m -Xmx4096m -jar"，然后输入**空格**，将 jar 文件拖到窗口中并按回车。这是用于分配内存的。

您可以将 -Xms2048m -Xmx4096m 更改为您想要的内存大小。但建议为此程序分配最大 4GB 或更多的内存。（它消耗内存较多）

**您需要等待 10-20 秒才能完全启动这个程序**。 这是因为它需要先初始化 Seedchecker，这会花费较长时间。在此阶段请不要进行其他操作。

## 如何使用？

点击**开始筛选**按钮启动筛选，点击**停止**按钮结束筛选。

左上角为**参数设置**区域，包含：

**洞穴深度**：期望的洞穴深度，默认值为 -50，有效范围为 -50 至 0。

**线程数**：使用的线程数量，有效范围为 1 至您计算机的最大线程数。

**X/Z 坐标**：搜索洞穴的坐标值，有效范围为 -30,000,000 至 30,000,000。

**分段大小**：仅递增模式适用。当总种子数超过该值时进行分段处理，具体数值取决于内存分配情况：

· 分配 4GB 内存时，建议设置 500 万至 1000 万
· 若内存更大，可尝试更高分段值
· 结构种子搜索通常无需关注此参数（因每个结构种子需遍历 65536 个世界种子，连续运行几天之内筛的种子数量都不会达到分段大小）

**筛高度**：导入Seed-checker检查精确高度。**此模式速度较慢，但能保证稳定发现深层洞穴。检查高度值越大，速度越慢**（例如 y=0 比 y=-10 更慢）

**筛基岩版无解种子**：当前是一个硬编码的筛选模式，用于搜索基岩版“无解种子”。使用时需要：

· 将 X/Z 坐标设为 (0,0)
· 建议开启**筛高度**功能（因筛选条件严格，开启后速度衰减不明显） 注意：此条件将自动禁用**只筛Entrance1**选项（因为这个模式本来就只筛Entrance1）与**所有群系气候参数选项**（使用程序内置条件）。这个条件筛得很快，但可能需要一个小时以上才能发现潜在种子，几十个小时才可能筛到真正的“无解种子”（出生点位于岩浆且重生后始终在岩浆中，需满足出生半径内无水流且无露天固体方块）

**只筛Entrance1**：速度更快，更容易发现大型露天洞穴（会忽略部分小型深层洞穴）

接下来是**筛选模式**区域：

**递增模式**：根据“种子输入”区的种子范围进行递增搜索

**列表筛选**：基于种子列表进行搜索，支持从文件加载列表

**结构种子**：在指定范围/列表内搜索种子及其姐妹种子（低48位二进制相同，高16位二进制不同，共65536个）。姐妹种子具有相同的结构坐标和近乎一致的下界/末地维度。若需筛选在洞穴中的结构（如村庄、前哨站、雪屋），推荐使用此模式（可先用 cubiomes-viewer 筛选含目标结构的低48位种子，再导入本程序处理）

**世界种子**：仅搜索指定范围/列表内的原始种子

左下角为**种子输入**区域：

**起始种子&结束种子**：递增模式专用，列表筛选模式无需使用此功能。

**种子列表（每行一个）**：列表筛选模式专用。支持导入种子文件（每行仅含一个种子，无其他字符）。**不建议加载超过1000万种子的列表**。递增模式无需使用此功能。

右上角区域是**群系气候参数**。

它包含8种类型。**Temperature、Humidity、Erosion、Ridge(Weirdness)、Continentalness** 决定了生物群系的生成。如果你想在特定的生物群系中寻找露天洞穴，最好筛选这些群系气候参数。程序会按照从最快到最慢的顺序进行搜索（Temperature、Humidity、Erosion 和 Ridge(Weirdness) 的检查速度比完整的洞穴入口检查更快，尽管Erosion 和 Weirdness 仍然比 Entrance1 慢）。你可以在Minecraft Wiki上搜索你所需生物群系的气候参数条件。结构的生成也取决于生物群系。Weirdness在-0.05到0.05之间通常代表河流，Continentalness低于-0.19代表海洋，为了更好地避开含水洞穴，我的默认设置会排除 Weirdness在-0.16到0.16之间的区间，且排除Continentalness低于-0.11的区间。

**Entrance、Cheese 和 AquiferFloodlevelFloodness** 并非生物群系气候参数，但它们仍在这个区域里面，用于高级搜索。通常，Entrance 和 Cheese 小于 0 表示有洞穴，而 99% 的非含水露天洞穴生成在 AquiferFloodlevelFloodness 低于 0.4 的位置。较低的 Entrance 和 Cheese 值可能意味着更大的洞穴，而较低的 AquiferFloodlevelFloodness 值可能意味着洞穴含水的几率更低。

右下角部分是**日志**，它在搜索过程中显示信息。

在这些部分下方，你可以看到**导出路径**，这是你想要将筛选后的列表导出到的路径。如果该列表已存在，程序会向你显示警告："结果文件已存在，将被覆盖，是否继续？"

在 GUI 的底部有一个**进度条**，它显示已完成的种子数量以及搜索速度。

## 此程序主要使用的库

https://github.com/KalleStruik/noise-sampler

https://github.com/jellejurre/seed-checker/tree/1.18.1

## 致谢

https://github.com/SunnySlopes

https://github.com/jellejurre

https://github.com/KalleStruik
