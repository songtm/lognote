# LogNote

Android logcat viewer for Windows, Linux, Mac

kotlin + swing

Regular expression filter

Online / offline log view

Aging Test : Save split file by lines


# Run
windows : start javaw -Dfile.encoding=utf8 -Xmx4096m -jar LogNote.jar\
linux : java -Dfile.encoding=utf8 -Xmx4096m -jar LogNote.jar


# Color settings
Light(default) \
<img src="https://user-images.githubusercontent.com/75207513/148026944-d965a90e-f2e4-478d-a763-f9d229d36f4c.png" width="600">

Dark \
<img src="https://user-images.githubusercontent.com/75207513/148026947-e713661d-a876-41c6-99c3-877596c098ad.png" width="600">

Add or change color configuration in lognote.xml as below.
<details markdown="1">
<summary>Click to expand</summary>

```xml
<entry key="COLOR_MANAGER_22">#101010</entry>
<entry key="COLOR_MANAGER_21">#503030</entry>
<entry key="COLOR_MANAGER_20">#301010</entry>
<entry key="COLOR_MANAGER_19">#301010</entry>
<entry key="COLOR_MANAGER_18">#FFFFFF</entry>
<entry key="COLOR_MANAGER_17">#F0F0F0</entry>
<entry key="COLOR_MANAGER_16">#A0A0F0</entry>
<entry key="COLOR_MANAGER_15">#A0A0F0</entry>
<entry key="COLOR_MANAGER_14">#A0A0F0</entry>
<entry key="COLOR_MANAGER_13">#A0A0F0</entry>
<entry key="COLOR_MANAGER_12">#ED3030</entry>
<entry key="COLOR_MANAGER_11">#CD6C79</entry>
<entry key="COLOR_MANAGER_10">#CB8742</entry>
<entry key="COLOR_MANAGER_9">#5084C4</entry>
<entry key="COLOR_MANAGER_8">#6C9876</entry>
<entry key="COLOR_MANAGER_7">#F0F0F0</entry>
<entry key="COLOR_MANAGER_6">#F0F0F0</entry>
<entry key="COLOR_MANAGER_5">#503030</entry>
<entry key="COLOR_MANAGER_4">#353535</entry>
<entry key="COLOR_MANAGER_3">#2B2B2B</entry>
<entry key="COLOR_MANAGER_2">#2B2B2B</entry>
<entry key="COLOR_MANAGER_1">#101010</entry>
<entry key="COLOR_MANAGER_0">#F05050</entry>
```

</details>

\
Setting > Font & Color \
<img src="https://user-images.githubusercontent.com/75207513/160410523-afcb82c2-78de-4695-a372-ac7d32533464.png" width="300">

# Save split file by lines for aging test
![aging](https://user-images.githubusercontent.com/75207513/150263408-d64b7003-6b9c-460f-a4e6-02e6a4ee01e9.png) \
Each time 100000 lines are saved, it is changed to a new file
