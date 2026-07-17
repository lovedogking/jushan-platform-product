<table><tr><td rowspan=1 colspan=1>0x07</td><td rowspan=1 colspan=1>向左拉窗</td></tr><tr><td rowspan=1 colspan=1>0x08</td><td rowspan=1 colspan=1>向右拉窗</td></tr><tr><td rowspan=1 colspan=1>0x0D</td><td rowspan=1 colspan=1>逐字显示</td></tr><tr><td rowspan=1 colspan=1>0x15</td><td rowspan=1 colspan=1>连续左移</td></tr></table>

DS:为显示速度，建议取值为0；

DT:停留时间，单位为秒，最大为255 秒；

DR：为显示次数，0 为无限循环显示。

FINDEX:为字体索引。注意:每种主板支持字体不一样

<table><tr><td rowspan=1 colspan=2>FINDEX 取值含义</td></tr><tr><td rowspan=1 colspan=1>取值</td><td rowspan=1 colspan=1>描述</td></tr><tr><td rowspan=1 colspan=1>0x00</td><td rowspan=1 colspan=1>ASCII8</td></tr><tr><td rowspan=1 colspan=1>0x01</td><td rowspan=1 colspan=1>ASCII10</td></tr><tr><td rowspan=1 colspan=1>0x02</td><td rowspan=1 colspan=1>ASCII13</td></tr><tr><td rowspan=1 colspan=1>0x03</td><td rowspan=1 colspan=1>宋体16</td></tr><tr><td rowspan=1 colspan=1>0x04</td><td rowspan=1 colspan=1>宋体24</td></tr><tr><td rowspan=1 colspan=1>0x05</td><td rowspan=1 colspan=1>宋体32</td></tr><tr><td rowspan=1 colspan=1>0x06</td><td rowspan=1 colspan=1>宋体48</td></tr><tr><td rowspan=1 colspan=1>0x07</td><td rowspan=1 colspan=1>宋体 64</td></tr></table>

FLAGS:显示标志位,目前没有用到，作为保留值，固定取值为0X00TC：为文本颜色，32 位数据类型，存储结构为 RGBA，R 为红色分量，G 为绿色分量，B 为蓝色分量，A 为透明值目前保留为0，没个颜色分量占用一个字节即8 位。

TL:为文本长度。

TEXT:为文本内容，最大32 字节。

VF:语音标志，固定取值为0X0A。

VTL:语音文本长度。

VOICE:语音文本内容。

回复格式: DA + VR + PN[2] + 0x6F+ DL + ACK + CRC[2]

回复参数描述: 包含 1 个字节的参数，DL 取值为 1，ACK 是显示屏返回的结果，取值为 0 表示成功，非 0 表示不成功。

使用例子：第一行到第四行分别显示 “第一行”、“第二行”“第三行”“欢迎光临”语音播报“欢迎光临”。

主机发送: 00 64 FF FF 6F 5C 00 00 04 00 15 01 05 00 03 00 FF 00 00 00 06 B5 DA D2 BB D0 D0 0D 01 01 01 05 00 03 00 00 FF 00 00 06 B5 DA B6 FE D0 D0 0D 02 01 01 05 00 03 00 FF 00 00 00 06 B5 DA

C8 FD D0 D0 0D 03 01 01 05 00 03 00 00 FF 00 00 08 BB B6 D3 AD B9 E2 C1 D9 00 0A 08 BB B6 D3 AD B9 E2 C1 D9 00 06 34

设备回复: 00 64 FF FF 6F 01 00 06 A9

## 0xE1(扫码支付界面)

该指令用来用来调用扫码支付界面。

## 请求参数描述:

SF: 显示标志，1 为显示，0 为不显示。  
EM: 进入模式，保留赋值为0(无操作)。  
ETM:退出模式，保留赋值为0(无操作)。  
ST:界面的显示时间，单位为秒，0 为一直显示。  
NI:下一个界面的索引号，目前保留取值为 0.  
TIME:停车时间(目前没有用到)，单位为秒，32 位数据类型，小端模式。  
MONEY:收费金额(目前没有用到)，单位为0.1 元，32 位数据类型，小端模式。  
ML:二维码信息长度。  
TL:文本信息长度。  
FLAGS:标志域，最高位为 1 时，表示携带文本信息，否则可以不携带文本信息；最低位为1 时表示同时播报语音。  
常用的组合是,0X80 不播报语音，0X81 显示及播报语音。  
QRSIZE:二维码尺寸，取值为 0 时表示 49X49 的像素，取值为 1 时表示 32X32 的像素。  
RESERVED:保留的 15 个字节MSG:二维码字符串，包含最后结束符。  
TEXT:文本信息字符串，包含最后结束符

回复格式: DA + VR + PN[2] + 0xE1+ DL + ACK + CRC[2]

回复参数描述: 包含 1 个字节的参数，DL 取值为 1， ACK 是显示屏返回的结果，取值为 0 表示成功，非0 表示不成功。

<table><tr><td rowspan=1 colspan=5>使用例子：调用扫码界面，二维码内容为“请输入支付地址”，显示内容为“粤 B123456,停车 1</td></tr><tr><td rowspan=1 colspan=4>小时 30 分,请交费 10 元粤 B123456,停车1小时30 分,请交费 10 元&quot;。</td><td rowspan=1 colspan=1></td></tr><tr><td rowspan=1 colspan=2>主机发送：0064FFFFE152</td><td rowspan=1 colspan=2>0100000000000000000000</td><td rowspan=3 colspan=1></td></tr><tr><td rowspan=1 colspan=4>00:000E22.8000.0000.00.8000.00.00.00.0000</td><td rowspan=1 colspan=1></td></tr><tr><td rowspan=1 colspan=4>00 85 87 31 41 C7 EB CA E4 C8 EB D6 A7 B8 B6 B5</td><td rowspan=1 colspan=1></td></tr><tr><td rowspan=1 colspan=4>D8 D6 B7 00 D4 C1 42 31 32 33 34 35 36 2C CD A3</td><td rowspan=1 colspan=1></td></tr><tr><td rowspan=1 colspan=4>B3 B5 31 DO A1 CA B1 33 30 B7 D6 2C C7 EB BD BB</td><td rowspan=1 colspan=1></td></tr><tr><td rowspan=1 colspan=1>B7 D1 31 30 D4 AA 00</td><td rowspan=1 colspan=3>F0 74</td><td rowspan=1 colspan=1></td></tr><tr><td rowspan=1 colspan=2>设备回复：0064FFFFE101006682</td><td rowspan=1 colspan=1>006682</td><td rowspan=1 colspan=1></td><td rowspan=1 colspan=1></td></tr></table>

## 0xE5(扫码支付界面-绘图模式)---V2 指令集

该指令通过绘图方式调用扫码支付界面。注意本指令属于 V2 指令集，DL 长度是 2 个字节。请求格式: DA + VR + PN[2] + 0xE5 + DL[2] SF + EM + ETM + ST + NI + VEN + TL + TEXT[TL] +BMPDATA [….] + CRC[2]

## 请求参数描述:

SF: 显示标志，1 为显示，0 为不显示。

EM: 进入模式，保留赋值为 0(无操作)。

ETM:退出模式，保留赋值为 0(无操作)。

ST:界面的显示时间，单位为秒，0 为一直显示。

NI:下一个界面的索引号，目前保留取值为 0.

VEN:语音播报开关，为0 不播报语音，为1 播报语音。

TL:显示文本长度。

TEXT:文本信息字符串。

BMPDATA:二维码单色位图数据,只支持 LEVEL3(29X29)和 LEVEL7(45X45)的二维码位图。  
29X29 为 2 行屏显示，45X45 为 4 行屏显示。

回复格式: DA + VR + PN[2] + 0xE5+ DL[2] + ACK + CRC[2]

回复参数描述: 包含1 个字节的参数，DL 取值为1， ACK 是显示屏返回的结果，取值为 0 表示成功，非 0 表示不成功。

<table><tr><td rowspan=1 colspan=8>使用例子：调用扫码界面，2行屏显示，二维码内容为“请输入支付地址”，显示和语音内容为&quot;粤</td></tr><tr><td rowspan=1 colspan=2>B123456,停车1小时30 分，请交费10元&quot;。</td><td rowspan=1 colspan=6></td></tr><tr><td rowspan=1 colspan=1>主机发送:00C8 FFFFE5DB000100 000A008122D4 C1</td><td rowspan=1 colspan=5>主机发送:00C8 FFFFE5DB000100 000A008122D4 C1</td><td></td><td></td></tr><tr><td rowspan=1 colspan=4>42 3132 33 34 35 36 2C CD A3 B3 B5 31D0 A1 </td><td rowspan=1 colspan=2></td><td></td><td></td></tr><tr><td rowspan=1 colspan=4>CA B1 33 30 B7 D6 2C C7 EB BD BB B7 D1 31 30</td><td rowspan=1 colspan=2></td><td></td><td></td></tr><tr><td rowspan=1 colspan=4>D4 AA 42 4D B2 00 0000 00 00 00 003E 0000</td><td rowspan=1 colspan=2></td><td></td><td></td></tr><tr><td rowspan=1 colspan=3>00 2800 00 00 1D00 0000 E3 FF FF FF 0100</td><td rowspan=1 colspan=1></td><td rowspan=1 colspan=2></td><td></td><td></td></tr><tr><td rowspan=1 colspan=3>010000000000000000000000000000</td><td rowspan=1 colspan=3></td><td></td><td></td></tr><tr><td rowspan=1 colspan=4>0000 000000 00 00 00 00 00 00 FF FF FF00</td><td rowspan=1 colspan=2></td><td></td><td></td></tr><tr><td rowspan=1 colspan=3>0000 00 00 FE 45 33 F8 82 85 8A08 BA 67 3A</td><td></td><td rowspan=1 colspan=2></td><td></td><td></td></tr><tr><td rowspan=1 colspan=4>E8 BA 70 FA E8 BA 66 0A E8 82 DD 2A 08 FE AA</td><td rowspan=1 colspan=2></td><td rowspan=1 colspan=1></td><td></td></tr><tr><td rowspan=1 colspan=4>AB F800 9D F8 000F02 3310 55 461C A8 8A</td><td rowspan=1 colspan=2></td><td></td><td></td></tr><tr><td rowspan=1 colspan=4>7D 0C 60 49 47 4100 7B E4 96 70 BD A2 2D 58</td><td rowspan=1 colspan=2></td><td></td><td></td></tr><tr><td rowspan=2 colspan=4>2E 49 31 70 75 F1 25 F0 22 A9 0E F0 8CE7 11</td><td rowspan=2 colspan=2></td><td rowspan=2 colspan=1></td><td rowspan=1 colspan=1></td></tr><tr><td rowspan=1 colspan=1></td></tr><tr><td rowspan=1 colspan=4>00 17 B0 D3 A0 04 38 FC FO CA 98 AF F8 00 DB</td><td></td><td></td><td></td><td></td></tr><tr><td rowspan=1 colspan=4>68 D0 FE 9C 9A A0 82 B6 28 F8 BA 85 EF C8 BA</td><td></td><td></td><td></td><td></td></tr><tr><td rowspan=1 colspan=4>68 22 E0 BA 4BD4 D8 82 08 5718 FE 42 1D B0</td><td></td><td></td><td></td><td></td></tr><tr><td rowspan=1 colspan=4>8B B9</td><td rowspan=1 colspan=1></td><td></td><td></td><td></td></tr></table>

## 0xE3(余位界面)

该指令调用余位显示界面。

请求格式: DA + VR + PN[2] + 0xE3 + DL + SF + EM + ETM + ST + NI +NUM + CRC[2]

请求参数描述:

SF: 显示标志，1 为显示，0 为不显示。

EM: 进入模式，保留赋值为 0(无操作)。

ETM:退出模式，保留赋值为 0(无操作)。

ST:界面的显示时间，单位为秒，0 为一直显示。

NI:下一个界面的索引号，目前保留取值为 0.

NUM:剩余车位数量，32 位数据类型，小端模式。

回复格式: $D A + V R + P N [ 2 ] + 0 \times E 3 + D L + A C K + C R C [ 2 ]$

回复参数描述: 包含1 个字节的参数，DL 取值为1， ACK 是显示屏返回的结果，取值为 0 表示成功，非 0 表示不成功。

## 使用例子：

主机发送: 00 64 FF FF E3 09 01 00 00 05 00 0A 00 00 00 A0 45

设备回复: 00 C8 FF FF E5 01 00 00 6F 10

## 0xD5(获取输入事件)

该指令用来获取模拟输入线路状态和遥控按键状态信息。

请求格式: DA + VR + PN[2] + 0xD5 + DL + CRC[2]

请求参数描述: 该指令没有请求参数,DL 为 0.

回复格式: $D A + V R + P N [ 2 ] + 0 \times D 5 + D L + R 1 D [ 4 ] + L E [ 2 ] + K E [ 2 ] + C R C [ 2 ]$

回复参数描述:

RID:为遥控器ID，4 个字节。

LE:为线路输入事件，最大支持 16 路输入，每 1 位表示线路输入的电平状态，1 为高电平，0 为低电平。

KE:为遥控按键事件,最大支持16 个键值，每1 位表示一个按键的状态，1 为按键按下，0 为按键没有被按下。

使用例子：

主机发送: 00 64 FF FF D5 00 2F 67

设备回复: 00 64 FF FF D5 08 6D 06 0A 00 00 00 03 00 92 B5

## 0xD8(设置补光灯时间段)

该指令用来设置补光灯打开的时间段。

请求格式: DA + VR + PN[2] + 0xD8 + DL + SH + SM + ES + EM + CRC[2]

请求参数描述: 该指令有 4 个字节参数，DL 为 4.

SH:为起始小时。

SM:为起始分钟。

EH:为结束小时。

EM:为结束分钟。

回复格式: DA + VR + PN[2] + 0xD8+ DL + ACK + CRC[2]

回复参数描述: 包含 1 个字节的参数，DL 取值为 1， ACK 是显示屏返回的结果，取值为 0 表示成功，非0 表示不成功。

## 使用例子：

主机发送: 00 64 FF FF D8 04 12 00 08 00 4B 6E

设备回复: 00 64 FF FF D8 01 00 B6 8F

## 2.4 显示时间

显示卡显示时间的方式采用转义字符替换的方案。用户在文字中插入相应的转义字符，显示卡收到后会对插入的位置用对应的时间替换成显示的字符。

支持的转义字符有：

\`C替换成常量值20

\`Y 表示年

\`M 表示月

\`D 表示日

\`V 表示星期

\`H 表示小时

\`N 表示分钟

\`S表示秒钟

例子："现在时间 20\`Y 年\`M 月\`D 日 星期\`V \`H:\`N:\`S"

## 2.5 带有变量的语音匹配格式

## 播报金钱语音的匹配格式:

1.请交费 XX.YY 元

2.请缴费 XX.YY 元

3.您的卡上余额为XX.YY元

4.￥XX.YY 元

5.收费 XX.YY 元

6.金额 XX.YY 元

7.此刻剩余金额 XX.YY 元

8.扣款金额为 XX.YY 元

## 播报有效期语音的匹配格式:

1. 此卡有效期至 YY-MM-DD

2.有效期至 YY-MM-DD

## 播报剩余天数语音的匹配格式:

1. 此卡可用日期DD天

2. 剩余天数为DD天

3. 剩余 DD 天

4. 剩余 DD 日

5.月租车剩余DD日

6.此卡剩余使用期DD天

7.此卡剩余天数为DD天

## 播报停车时长语音的匹配格式:

1. 此次停车时间为 DD 天 HH 小时 MM 分钟

2. 停车时间DD天HH 小时MM分钟

3. 停车时长DD天HH 小时MM分钟

4. 停车 DD 天 HH 小时 MM 分钟

## 播报剩余车位语音的匹配格式:

1. 剩余车位 NN 位

2. 剩余 NN 位

3. 空车位 NN 位

## 播报车牌号码语音的匹配格式:

1. 车牌号码P

2. 车牌号为P

3. P

## 播报入场时间语音的匹配格式:

1.入场时间为 HH-MM

## 附表 1

语音列表：

<table><tr><td colspan="1" rowspan="1">序号</td><td colspan="1" rowspan="1">词、词组、短语</td></tr><tr><td colspan="1" rowspan="1">1</td><td colspan="1" rowspan="1">0</td></tr><tr><td colspan="1" rowspan="1">2</td><td colspan="1" rowspan="1">1</td></tr><tr><td colspan="1" rowspan="1">3</td><td colspan="1" rowspan="1">2</td></tr><tr><td colspan="1" rowspan="1">4</td><td colspan="1" rowspan="1">3</td></tr><tr><td colspan="1" rowspan="1">5</td><td colspan="1" rowspan="1">4</td></tr><tr><td colspan="1" rowspan="1">6</td><td colspan="1" rowspan="1">5</td></tr><tr><td colspan="1" rowspan="1">7</td><td colspan="1" rowspan="1">6</td></tr><tr><td colspan="1" rowspan="1">8</td><td colspan="1" rowspan="1">7</td></tr><tr><td colspan="1" rowspan="1">9</td><td colspan="1" rowspan="1">8</td></tr><tr><td colspan="1" rowspan="1">10</td><td colspan="1" rowspan="1">9</td></tr><tr><td colspan="1" rowspan="1">11</td><td colspan="1" rowspan="1">A</td></tr><tr><td colspan="1" rowspan="1">12</td><td colspan="1" rowspan="1">B</td></tr><tr><td colspan="1" rowspan="1">13</td><td colspan="1" rowspan="1">C</td></tr><tr><td colspan="1" rowspan="1">14</td><td colspan="1" rowspan="1">D</td></tr><tr><td colspan="1" rowspan="1">15</td><td colspan="1" rowspan="1">E</td></tr><tr><td colspan="1" rowspan="1">16</td><td colspan="1" rowspan="1">F</td></tr><tr><td colspan="1" rowspan="1">17</td><td colspan="1" rowspan="1">G</td></tr><tr><td colspan="1" rowspan="1">18</td><td colspan="1" rowspan="1">H</td></tr><tr><td colspan="1" rowspan="1">19</td><td colspan="1" rowspan="1">I</td></tr><tr><td colspan="1" rowspan="1">20</td><td colspan="1" rowspan="1">IP</td></tr><tr><td colspan="1" rowspan="1">21</td><td colspan="1" rowspan="1">J</td></tr><tr><td colspan="1" rowspan="1">22</td><td colspan="1" rowspan="1">K</td></tr><tr><td colspan="1" rowspan="1">23</td><td colspan="1" rowspan="1">L</td></tr><tr><td colspan="1" rowspan="1">24</td><td colspan="1" rowspan="1">M</td></tr><tr><td colspan="1" rowspan="1">25</td><td colspan="1" rowspan="1">N</td></tr><tr><td colspan="1" rowspan="1">26</td><td colspan="1" rowspan="1">0</td></tr><tr><td colspan="1" rowspan="1">27</td><td colspan="1" rowspan="1">P</td></tr><tr><td colspan="1" rowspan="1">28</td><td colspan="1" rowspan="1">Q</td></tr><tr><td colspan="1" rowspan="1">29</td><td colspan="1" rowspan="1">R</td></tr><tr><td colspan="1" rowspan="1">30</td><td colspan="1" rowspan="1">S</td></tr><tr><td colspan="1" rowspan="1">31</td><td colspan="1" rowspan="1">T</td></tr><tr><td colspan="1" rowspan="1">32</td><td colspan="1" rowspan="1">U</td></tr><tr><td colspan="1" rowspan="1">33</td><td colspan="1" rowspan="1">V</td></tr><tr><td colspan="1" rowspan="1">34</td><td colspan="1" rowspan="1">W</td></tr><tr><td colspan="1" rowspan="1">35</td><td colspan="1" rowspan="1">X</td></tr><tr><td colspan="1" rowspan="1">36</td><td colspan="1" rowspan="1">Y</td></tr><tr><td colspan="1" rowspan="1">37</td><td colspan="1" rowspan="1">Z</td></tr><tr><td colspan="1" rowspan="1">38</td><td colspan="1" rowspan="1">澳</td></tr><tr><td colspan="1" rowspan="1">39</td><td colspan="1" rowspan="1">百</td></tr><tr><td colspan="1" rowspan="1">40</td><td colspan="1" rowspan="1">半</td></tr><tr><td colspan="1" rowspan="1">41</td><td colspan="1" rowspan="1">北</td></tr><tr><td colspan="1" rowspan="1">42</td><td colspan="1" rowspan="1">藏</td></tr><tr><td colspan="1" rowspan="1">43</td><td colspan="1" rowspan="1">车场已满</td></tr><tr><td colspan="1" rowspan="1">44</td><td colspan="1" rowspan="1">车牌</td></tr><tr><td colspan="1" rowspan="1">45</td><td colspan="1" rowspan="1">车牌号为</td></tr><tr><td colspan="1" rowspan="1">46</td><td colspan="1" rowspan="1">车未入场</td></tr><tr><td colspan="1" rowspan="1">47</td><td colspan="1" rowspan="1">车位已满</td></tr><tr><td colspan="1" rowspan="1">48</td><td colspan="1" rowspan="1">车已过期</td></tr><tr><td colspan="1" rowspan="1">49</td><td colspan="1" rowspan="1">车已在场</td></tr><tr><td colspan="1" rowspan="1">50</td><td colspan="1" rowspan="1">成</td></tr><tr><td colspan="1" rowspan="1">51</td><td colspan="1" rowspan="1">出入平安</td></tr><tr><td colspan="1" rowspan="1">52</td><td colspan="1" rowspan="1">储值车</td></tr><tr><td colspan="1" rowspan="1">53</td><td colspan="1" rowspan="1">川</td></tr><tr><td colspan="1" rowspan="1">54</td><td colspan="1" rowspan="1">此车到期时间剩余</td></tr><tr><td colspan="1" rowspan="1">55</td><td colspan="1" rowspan="1">此车可用日期</td></tr><tr><td colspan="1" rowspan="1">56</td><td colspan="1" rowspan="1">此车已过期</td></tr><tr><td colspan="1" rowspan="1">57</td><td colspan="1" rowspan="1">次</td></tr><tr><td colspan="1" rowspan="1">58</td><td colspan="1" rowspan="1">第</td></tr><tr><td colspan="1" rowspan="1">59</td><td colspan="1" rowspan="1">点</td></tr><tr><td colspan="1" rowspan="1">60</td><td colspan="1" rowspan="1">吨</td></tr><tr><td colspan="1" rowspan="1">61</td><td colspan="1" rowspan="1">鄂</td></tr><tr><td colspan="1" rowspan="1">62</td><td colspan="1" rowspan="1">分</td></tr><tr><td colspan="1" rowspan="1">63</td><td colspan="1" rowspan="1">分钟</td></tr><tr><td colspan="1" rowspan="1">64</td><td colspan="1" rowspan="1">负</td></tr><tr><td colspan="1" rowspan="1">65</td><td colspan="1" rowspan="1">该车</td></tr><tr><td colspan="1" rowspan="1">66</td><td colspan="1" rowspan="1">甘</td></tr><tr><td colspan="1" rowspan="1">67</td><td colspan="1" rowspan="1">赣</td></tr><tr><td colspan="1" rowspan="1">68</td><td colspan="1" rowspan="1">港</td></tr><tr><td colspan="1" rowspan="1">69</td><td colspan="1" rowspan="1">个</td></tr><tr><td colspan="1" rowspan="1">70</td><td colspan="1" rowspan="1">固定车</td></tr><tr><td colspan="1" rowspan="1">71</td><td colspan="1" rowspan="1">挂</td></tr><tr><td colspan="1" rowspan="1">72</td><td colspan="1" rowspan="1">广</td></tr><tr><td colspan="1" rowspan="1">73</td><td colspan="1" rowspan="1">贵</td></tr><tr><td colspan="1" rowspan="1">74</td><td colspan="1" rowspan="1">桂</td></tr><tr><td colspan="1" rowspan="1">75</td><td colspan="1" rowspan="1">过期车辆</td></tr><tr><td colspan="1" rowspan="1">76</td><td colspan="1" rowspan="1">过期用户</td></tr><tr><td colspan="1" rowspan="1">77</td><td colspan="1" rowspan="1">海</td></tr><tr><td colspan="1" rowspan="1">78</td><td colspan="1" rowspan="1">号</td></tr><tr><td colspan="1" rowspan="1">79</td><td colspan="1" rowspan="1">黑</td></tr><tr><td colspan="1" rowspan="1">80</td><td colspan="1" rowspan="1">沪</td></tr><tr><td colspan="1" rowspan="1">81</td><td colspan="1" rowspan="1">欢迎光临</td></tr><tr><td colspan="1" rowspan="1">82</td><td colspan="1" rowspan="1">吉</td></tr><tr><td colspan="1" rowspan="1">83</td><td colspan="1" rowspan="1">即将过期</td></tr><tr><td colspan="1" rowspan="1">84</td><td colspan="1" rowspan="1">已</td></tr><tr><td colspan="1" rowspan="1">85</td><td colspan="1" rowspan="1">济</td></tr><tr><td colspan="1" rowspan="1">86</td><td colspan="1" rowspan="1">冀</td></tr><tr><td colspan="1" rowspan="1">87</td><td colspan="1" rowspan="1">交费</td></tr><tr><td colspan="1" rowspan="1">88</td><td colspan="1" rowspan="1">角</td></tr><tr><td colspan="1" rowspan="1">89</td><td colspan="1" rowspan="1">津</td></tr><tr><td colspan="1" rowspan="1">90</td><td colspan="1" rowspan="1">晋</td></tr><tr><td colspan="1" rowspan="1">91</td><td colspan="1" rowspan="1">禁止通行</td></tr><tr><td colspan="1" rowspan="1">92</td><td colspan="1" rowspan="1">京</td></tr><tr><td colspan="1" rowspan="1">93</td><td colspan="1" rowspan="1">警</td></tr><tr><td colspan="1" rowspan="1">94</td><td colspan="1" rowspan="1">军</td></tr><tr><td colspan="1" rowspan="1">95</td><td colspan="1" rowspan="1">军警车</td></tr><tr><td colspan="1" rowspan="1">96</td><td colspan="1" rowspan="1">可用日期</td></tr><tr><td colspan="1" rowspan="1">97</td><td colspan="1" rowspan="1">空</td></tr><tr><td colspan="1" rowspan="1">98</td><td colspan="1" rowspan="1">兰</td></tr><tr><td colspan="1" rowspan="1">99</td><td colspan="1" rowspan="1">两</td></tr><tr><td colspan="1" rowspan="1">100</td><td colspan="1" rowspan="1">辽</td></tr><tr><td colspan="1" rowspan="1">101</td><td colspan="1" rowspan="1">临</td></tr><tr><td colspan="1" rowspan="1">102</td><td colspan="1" rowspan="1">临时车</td></tr><tr><td colspan="1" rowspan="1">103</td><td colspan="1" rowspan="1">临时车辆</td></tr><tr><td colspan="1" rowspan="1">104</td><td colspan="1" rowspan="1">鲁</td></tr><tr><td colspan="1" rowspan="1">105</td><td colspan="1" rowspan="1">蒙</td></tr><tr><td colspan="1" rowspan="1">106</td><td colspan="1" rowspan="1">免</td></tr><tr><td colspan="1" rowspan="1">107</td><td colspan="1" rowspan="1">秒</td></tr><tr><td colspan="1" rowspan="1">108</td><td colspan="1" rowspan="1">闽</td></tr><tr><td colspan="1" rowspan="1">109</td><td colspan="1" rowspan="1">南</td></tr><tr><td colspan="1" rowspan="1">110</td><td colspan="1" rowspan="1">内部车</td></tr><tr><td colspan="1" rowspan="1">111</td><td colspan="1" rowspan="1">年</td></tr><tr><td colspan="1" rowspan="1">112</td><td colspan="1" rowspan="1">您好</td></tr><tr><td colspan="1" rowspan="1">113</td><td colspan="1" rowspan="1">宁</td></tr><tr><td colspan="1" rowspan="1">114</td><td colspan="1" rowspan="1">农</td></tr><tr><td colspan="1" rowspan="1">115</td><td colspan="1" rowspan="1">千</td></tr><tr><td colspan="1" rowspan="1">116</td><td colspan="1" rowspan="1">青</td></tr><tr><td colspan="1" rowspan="1">117</td><td colspan="1" rowspan="1">请减速慢行</td></tr><tr><td colspan="1" rowspan="1">118</td><td colspan="1" rowspan="1">请交费</td></tr><tr><td colspan="1" rowspan="1">119</td><td colspan="1" rowspan="1">请入场停车</td></tr><tr><td colspan="1" rowspan="1">120</td><td colspan="1" rowspan="1">请通过</td></tr><tr><td colspan="1" rowspan="1">121</td><td colspan="1" rowspan="1">请通行</td></tr><tr><td colspan="1" rowspan="1">122</td><td colspan="1" rowspan="1">琼</td></tr><tr><td colspan="1" rowspan="1">123</td><td colspan="1" rowspan="1">人</td></tr><tr><td colspan="1" rowspan="1">124</td><td colspan="1" rowspan="1">日</td></tr><tr><td colspan="1" rowspan="1">125</td><td colspan="1" rowspan="1">入场</td></tr><tr><td colspan="1" rowspan="1">126</td><td colspan="1" rowspan="1">入场时间</td></tr><tr><td colspan="1" rowspan="1">127</td><td colspan="1" rowspan="1">入口</td></tr><tr><td colspan="1" rowspan="1">128</td><td colspan="1" rowspan="1">陕</td></tr><tr><td colspan="1" rowspan="1">129</td><td colspan="1" rowspan="1">深</td></tr><tr><td colspan="1" rowspan="1">130</td><td colspan="1" rowspan="1">沈</td></tr><tr><td colspan="1" rowspan="1">131</td><td colspan="1" rowspan="1">剩余</td></tr><tr><td colspan="1" rowspan="1">132</td><td colspan="1" rowspan="1">剩余车位</td></tr><tr><td colspan="1" rowspan="1">133</td><td colspan="1" rowspan="1">剩余金额</td></tr><tr><td colspan="1" rowspan="1">134</td><td colspan="1" rowspan="1">剩余天数为</td></tr><tr><td colspan="1" rowspan="1">135</td><td colspan="1" rowspan="1">+</td></tr><tr><td colspan="1" rowspan="1">136</td><td colspan="1" rowspan="1">时</td></tr><tr><td colspan="1" rowspan="1">137</td><td colspan="1" rowspan="1">时间</td></tr><tr><td colspan="1" rowspan="1">138</td><td colspan="1" rowspan="1">使</td></tr><tr><td colspan="1" rowspan="1">139</td><td colspan="1" rowspan="1">收费</td></tr><tr><td colspan="1" rowspan="1">140</td><td colspan="1" rowspan="1">苏</td></tr><tr><td colspan="1" rowspan="1">141</td><td colspan="1" rowspan="1">台</td></tr><tr><td colspan="1" rowspan="1">142</td><td colspan="1" rowspan="1">天</td></tr><tr><td colspan="1" rowspan="1">143</td><td colspan="1" rowspan="1">停车</td></tr><tr><td colspan="1" rowspan="1">144</td><td colspan="1" rowspan="1">停车时长</td></tr><tr><td colspan="1" rowspan="1">145</td><td colspan="1" rowspan="1">停车有效期</td></tr><tr><td colspan="1" rowspan="1">146</td><td colspan="1" rowspan="1">皖</td></tr><tr><td colspan="1" rowspan="1">147</td><td colspan="1" rowspan="1">万</td></tr><tr><td colspan="1" rowspan="1">148</td><td colspan="1" rowspan="1">为</td></tr><tr><td colspan="1" rowspan="1">149</td><td colspan="1" rowspan="1">未</td></tr><tr><td colspan="1" rowspan="1">150</td><td colspan="1" rowspan="1">未授权</td></tr><tr><td colspan="1" rowspan="1">151</td><td colspan="1" rowspan="1">位</td></tr><tr><td colspan="1" rowspan="1">152</td><td colspan="1" rowspan="1">无权出场</td></tr><tr><td colspan="1" rowspan="1">153</td><td colspan="1" rowspan="1">无权入场</td></tr><tr><td colspan="1" rowspan="1">154</td><td colspan="1" rowspan="1">湘</td></tr><tr><td colspan="1" rowspan="1">155</td><td colspan="1" rowspan="1">小时</td></tr><tr><td colspan="1" rowspan="1">156</td><td colspan="1" rowspan="1">谢谢</td></tr><tr><td colspan="1" rowspan="1">157</td><td colspan="1" rowspan="1">新</td></tr><tr><td colspan="1" rowspan="1">158</td><td colspan="1" rowspan="1">学</td></tr><tr><td colspan="1" rowspan="1">159</td><td colspan="1" rowspan="1">路平安</td></tr><tr><td colspan="1" rowspan="1">160</td><td colspan="1" rowspan="1">一路顺风</td></tr><tr><td colspan="1" rowspan="1">161</td><td colspan="1" rowspan="1">已过期</td></tr><tr><td colspan="1" rowspan="1">162</td><td colspan="1" rowspan="1">已缴费</td></tr><tr><td colspan="1" rowspan="1">163</td><td colspan="1" rowspan="1">有效期至</td></tr><tr><td colspan="1" rowspan="1">164</td><td colspan="1" rowspan="1">渝</td></tr><tr><td colspan="1" rowspan="1">165</td><td colspan="1" rowspan="1">豫</td></tr><tr><td colspan="1" rowspan="1">166</td><td colspan="1" rowspan="1">元</td></tr><tr><td colspan="1" rowspan="1">167</td><td colspan="1" rowspan="1">月</td></tr><tr><td colspan="1" rowspan="1">168</td><td colspan="1" rowspan="1">月卡车</td></tr><tr><td colspan="1" rowspan="1">169</td><td colspan="1" rowspan="1">月租车</td></tr><tr><td colspan="1" rowspan="1">170</td><td colspan="1" rowspan="1">月租车剩余</td></tr><tr><td colspan="1" rowspan="1">171</td><td colspan="1" rowspan="1">月租车已到期</td></tr><tr><td colspan="1" rowspan="1">172</td><td colspan="1" rowspan="1">月租已到期</td></tr><tr><td colspan="1" rowspan="1">173</td><td colspan="1" rowspan="1">粤</td></tr><tr><td colspan="1" rowspan="1">174</td><td colspan="1" rowspan="1">云</td></tr><tr><td colspan="1" rowspan="1">175</td><td colspan="1" rowspan="1">浙</td></tr><tr><td colspan="1" rowspan="1">176</td><td colspan="1" rowspan="1">整</td></tr><tr><td colspan="1" rowspan="1">177</td><td colspan="1" rowspan="1">正</td></tr><tr><td colspan="1" rowspan="1">178</td><td colspan="1" rowspan="1">祝您一路平安</td></tr><tr><td colspan="1" rowspan="1">179</td><td colspan="1" rowspan="1">祝您一路顺风</td></tr><tr><td colspan="1" rowspan="1">180</td><td colspan="1" rowspan="1">ETCP 为您服务</td></tr><tr><td colspan="1" rowspan="1">181</td><td colspan="1" rowspan="1">MNu11</td></tr><tr><td colspan="1" rowspan="1">182</td><td colspan="1" rowspan="1">SNu11</td></tr><tr><td colspan="1" rowspan="1">183</td><td colspan="1" rowspan="1">本车位已停车</td></tr><tr><td colspan="1" rowspan="1">184</td><td colspan="1" rowspan="1">波特率为</td></tr><tr><td colspan="1" rowspan="1">185</td><td colspan="1" rowspan="1">操作成功</td></tr><tr><td colspan="1" rowspan="1">186</td><td colspan="1" rowspan="1">超过离场限时</td></tr><tr><td colspan="1" rowspan="1">187</td><td colspan="1" rowspan="1">超时交费</td></tr><tr><td colspan="1" rowspan="1">188</td><td colspan="1" rowspan="1">车号已过期</td></tr><tr><td colspan="1" rowspan="1">189</td><td colspan="1" rowspan="1">车辆禁止出场</td></tr><tr><td colspan="1" rowspan="1">190</td><td colspan="1" rowspan="1">车辆匹配异常</td></tr><tr><td colspan="1" rowspan="1">191</td><td colspan="1" rowspan="1">车牌不符</td></tr><tr><td colspan="1" rowspan="1">192</td><td colspan="1" rowspan="1">车牌匹配异常</td></tr><tr><td colspan="1" rowspan="1">193</td><td colspan="1" rowspan="1">车牌识别失败</td></tr><tr><td colspan="1" rowspan="1">194</td><td colspan="1" rowspan="1">车位已使用</td></tr><tr><td colspan="1" rowspan="1">195</td><td colspan="1" rowspan="1">车位已用</td></tr><tr><td colspan="1" rowspan="1">196</td><td colspan="1" rowspan="1">车位已用完</td></tr><tr><td colspan="1" rowspan="1">197</td><td colspan="1" rowspan="1">车位有余</td></tr><tr><td colspan="1" rowspan="1">198</td><td colspan="1" rowspan="1">出场</td></tr><tr><td colspan="1" rowspan="1">199</td><td colspan="1" rowspan="1">出口</td></tr><tr><td colspan="1" rowspan="1">200</td><td colspan="1" rowspan="1">春节快乐</td></tr><tr><td colspan="1" rowspan="1">201</td><td colspan="1" rowspan="1">此车号无效</td></tr><tr><td colspan="1" rowspan="1">202</td><td colspan="1" rowspan="1">此车为黑名单车辆</td></tr><tr><td colspan="1" rowspan="1">203</td><td colspan="1" rowspan="1">此车未登记</td></tr><tr><td colspan="1" rowspan="1">204</td><td colspan="1" rowspan="1">此车位车辆已进场</td></tr><tr><td colspan="1" rowspan="1">205</td><td colspan="1" rowspan="1">此车位车辆已经入场</td></tr><tr><td colspan="1" rowspan="1">206</td><td colspan="1" rowspan="1">此车已超时</td></tr><tr><td colspan="1" rowspan="1">207</td><td colspan="1" rowspan="1">此卡未登记</td></tr><tr><td colspan="1" rowspan="1">208</td><td colspan="1" rowspan="1">此卡已出场</td></tr><tr><td colspan="1" rowspan="1">209</td><td colspan="1" rowspan="1">此卡已挂失</td></tr><tr><td colspan="1" rowspan="1">210</td><td colspan="1" rowspan="1">此卡已过期</td></tr><tr><td colspan="1" rowspan="1">211</td><td colspan="1" rowspan="1">此卡已锁定</td></tr><tr><td colspan="1" rowspan="1">212</td><td colspan="1" rowspan="1">此卡已在场</td></tr><tr><td colspan="1" rowspan="1">213</td><td colspan="1" rowspan="1">此卡已注销</td></tr><tr><td colspan="1" rowspan="1">214</td><td colspan="1" rowspan="1">道闸已开启</td></tr><tr><td colspan="1" rowspan="1">215</td><td colspan="1" rowspan="1">地址</td></tr><tr><td colspan="1" rowspan="1">216</td><td colspan="1" rowspan="1">地址为</td></tr><tr><td colspan="1" rowspan="1">217</td><td colspan="1" rowspan="1">端口</td></tr><tr><td colspan="1" rowspan="1">218</td><td colspan="1" rowspan="1">端口为</td></tr><tr><td colspan="1" rowspan="1">219</td><td colspan="1" rowspan="1">对不起</td></tr><tr><td colspan="1" rowspan="1">220</td><td colspan="1" rowspan="1">二维码</td></tr><tr><td colspan="1" rowspan="1">221</td><td colspan="1" rowspan="1">付款码</td></tr><tr><td colspan="1" rowspan="1">222</td><td colspan="1" rowspan="1">该时段不允许进入</td></tr><tr><td colspan="1" rowspan="1">223</td><td colspan="1" rowspan="1">管控车辆</td></tr><tr><td colspan="1" rowspan="1">224</td><td colspan="1" rowspan="1">贵宾车</td></tr><tr><td colspan="1" rowspan="1">225</td><td colspan="1" rowspan="1">国庆节快乐</td></tr><tr><td colspan="1" rowspan="1">226</td><td colspan="1" rowspan="1">过期出场</td></tr><tr><td colspan="1" rowspan="1">227</td><td colspan="1" rowspan="1">过期入场</td></tr><tr><td colspan="1" rowspan="1">228</td><td colspan="1" rowspan="1">黑名单卡</td></tr><tr><td colspan="1" rowspan="1">229</td><td colspan="1" rowspan="1">欢迎泊车</td></tr><tr><td colspan="1" rowspan="1">230</td><td colspan="1" rowspan="1">欢迎登录</td></tr><tr><td colspan="1" rowspan="1">231</td><td colspan="1" rowspan="1">欢迎回家</td></tr><tr><td colspan="1" rowspan="1">232</td><td colspan="1" rowspan="1">欢迎入场</td></tr><tr><td colspan="1" rowspan="1">233</td><td colspan="1" rowspan="1">欢迎使用车牌识别</td></tr><tr><td colspan="1" rowspan="1">234</td><td colspan="1" rowspan="1">欢迎下次光临</td></tr><tr><td colspan="1" rowspan="1">235</td><td colspan="1" rowspan="1">欢迎再次光临</td></tr><tr><td colspan="1" rowspan="1">236</td><td colspan="1" rowspan="1">会员车</td></tr><tr><td colspan="1" rowspan="1">237</td><td colspan="1" rowspan="1">缴费成功</td></tr><tr><td colspan="1" rowspan="1">238</td><td colspan="1" rowspan="1">进出延时</td></tr><tr><td colspan="1" rowspan="1">239</td><td colspan="1" rowspan="1">禁止出场</td></tr><tr><td colspan="1" rowspan="1">240</td><td colspan="1" rowspan="1">禁止出入</td></tr><tr><td colspan="1" rowspan="1">241</td><td colspan="1" rowspan="1">禁止取卡</td></tr><tr><td colspan="1" rowspan="1">242</td><td colspan="1" rowspan="1">禁止入场</td></tr><tr><td colspan="1" rowspan="1">243</td><td colspan="1" rowspan="1">拒绝进入</td></tr><tr><td colspan="1" rowspan="1">244</td><td colspan="1" rowspan="1">军警车免费通行</td></tr><tr><td colspan="1" rowspan="1">245</td><td colspan="1" rowspan="1">开闸请注意</td></tr><tr><td colspan="1" rowspan="1">246</td><td colspan="1" rowspan="1">扣款成功</td></tr><tr><td colspan="1" rowspan="1">247</td><td colspan="1" rowspan="1">扣款金额为</td></tr><tr><td colspan="1" rowspan="1">248</td><td colspan="1" rowspan="1">劳动节快乐</td></tr><tr><td colspan="1" rowspan="1">249</td><td colspan="1" rowspan="1">临时车位已满</td></tr><tr><td colspan="1" rowspan="1">250</td><td colspan="1" rowspan="1">临时车无权限</td></tr><tr><td colspan="1" rowspan="1">251</td><td colspan="1" rowspan="1">临时车无入场记录</td></tr><tr><td colspan="1" rowspan="1">252</td><td colspan="1" rowspan="1">免费车辆</td></tr><tr><td colspan="1" rowspan="1">253</td><td colspan="1" rowspan="1">免费放行</td></tr><tr><td colspan="1" rowspan="1">254</td><td colspan="1" rowspan="1">免费停车</td></tr><tr><td colspan="1" rowspan="1">255</td><td colspan="1" rowspan="1">免费通行</td></tr><tr><td colspan="1" rowspan="1">256</td><td colspan="1" rowspan="1">内部车场不允许进入</td></tr><tr><td colspan="1" rowspan="1">257</td><td colspan="1" rowspan="1">您有快递在管理处</td></tr><tr><td colspan="1" rowspan="1">258</td><td colspan="1" rowspan="1">欠费外出</td></tr><tr><td colspan="1" rowspan="1">259</td><td colspan="1" rowspan="1">请充值</td></tr><tr><td colspan="1" rowspan="1">260</td><td colspan="1" rowspan="1">请到管理处缴费</td></tr><tr><td colspan="1" rowspan="1">261</td><td colspan="1" rowspan="1">请等待确认</td></tr><tr><td colspan="1" rowspan="1">262</td><td colspan="1" rowspan="1">请等待确认放行</td></tr><tr><td colspan="1" rowspan="1">263</td><td colspan="1" rowspan="1">请等待人工放行</td></tr><tr><td colspan="1" rowspan="1">264</td><td colspan="1" rowspan="1">请及时缴纳管理费</td></tr><tr><td colspan="1" rowspan="1">265</td><td colspan="1" rowspan="1">请及时缴纳水电费</td></tr><tr><td colspan="1" rowspan="1">266</td><td colspan="1" rowspan="1">请尽快延期</td></tr><tr><td colspan="1" rowspan="1">267</td><td colspan="1" rowspan="1">请靠边停车检查</td></tr><tr><td colspan="1" rowspan="1">268</td><td colspan="1" rowspan="1">请入场</td></tr><tr><td colspan="1" rowspan="1">269</td><td colspan="1" rowspan="1">请扫码</td></tr><tr><td colspan="1" rowspan="1">270</td><td colspan="1" rowspan="1">请扫描屏上二维码</td></tr><tr><td colspan="1" rowspan="1">271</td><td colspan="1" rowspan="1">请稍等</td></tr><tr><td colspan="1" rowspan="1">272</td><td colspan="1" rowspan="1">请稍候</td></tr><tr><td colspan="1" rowspan="1">273</td><td colspan="1" rowspan="1">请使用</td></tr><tr><td colspan="1" rowspan="1">274</td><td colspan="1" rowspan="1">请停车检查</td></tr><tr><td colspan="1" rowspan="1">275</td><td colspan="1" rowspan="1">请先到中心岗亭缴费</td></tr><tr><td colspan="1" rowspan="1">276</td><td colspan="1" rowspan="1">请先登录操作员</td></tr><tr><td colspan="1" rowspan="1">277</td><td colspan="1" rowspan="1">请与管理员联系</td></tr><tr><td colspan="1" rowspan="1">278</td><td colspan="1" rowspan="1">请在</td></tr><tr><td colspan="1" rowspan="1">279</td><td colspan="1" rowspan="1">请重新补交停车费</td></tr><tr><td colspan="1" rowspan="1">280</td><td colspan="1" rowspan="1">请重新缴费</td></tr><tr><td colspan="1" rowspan="1">281</td><td colspan="1" rowspan="1">请重新进入识别区</td></tr><tr><td colspan="1" rowspan="1">282</td><td colspan="1" rowspan="1">请注意</td></tr><tr><td colspan="1" rowspan="1">283</td><td colspan="1" rowspan="1">区已满</td></tr><tr><td colspan="1" rowspan="1">284</td><td colspan="1" rowspan="1">人工开闸</td></tr><tr><td colspan="1" rowspan="1">285</td><td colspan="1" rowspan="1">人工落闸</td></tr><tr><td colspan="1" rowspan="1">286</td><td colspan="1" rowspan="1">扫码支付</td></tr><tr><td colspan="1" rowspan="1">287</td><td colspan="1" rowspan="1">圣诞节快乐</td></tr><tr><td colspan="1" rowspan="1">288</td><td colspan="1" rowspan="1">识别成功</td></tr><tr><td colspan="1" rowspan="1">289</td><td colspan="1" rowspan="1">收费中</td></tr><tr><td colspan="1" rowspan="1">290</td><td colspan="1" rowspan="1">手动放行</td></tr><tr><td colspan="1" rowspan="1">291</td><td colspan="1" rowspan="1">手动关闸</td></tr><tr><td colspan="1" rowspan="1">292</td><td colspan="1" rowspan="1">手动开闸</td></tr><tr><td colspan="1" rowspan="1">293</td><td colspan="1" rowspan="1">手机</td></tr><tr><td colspan="1" rowspan="1">294</td><td colspan="1" rowspan="1">刷卡成功</td></tr><tr><td colspan="1" rowspan="1">295</td><td colspan="1" rowspan="1">抬闸保持</td></tr><tr><td colspan="1" rowspan="1">296</td><td colspan="1" rowspan="1">外来车位已满</td></tr><tr><td colspan="1" rowspan="1">297</td><td colspan="1" rowspan="1">微信</td></tr><tr><td colspan="1" rowspan="1">298</td><td colspan="1" rowspan="1">未缴费请到缴费处缴费</td></tr><tr><td colspan="1" rowspan="1">299</td><td colspan="1" rowspan="1">无法人工入场</td></tr><tr><td colspan="1" rowspan="1">300</td><td colspan="1" rowspan="1">无法入场</td></tr><tr><td colspan="1" rowspan="1">301</td><td colspan="1" rowspan="1">无牌车</td></tr><tr><td colspan="1" rowspan="1">302</td><td colspan="1" rowspan="1">无入场记录</td></tr><tr><td colspan="1" rowspan="1">303</td><td colspan="1" rowspan="1">无剩余车位</td></tr><tr><td colspan="1" rowspan="1">304</td><td colspan="1" rowspan="1">无通行权限</td></tr><tr><td colspan="1" rowspan="1">305</td><td colspan="1" rowspan="1">系统过期</td></tr><tr><td colspan="1" rowspan="1">306</td><td colspan="1" rowspan="1">系统开始</td></tr><tr><td colspan="1" rowspan="1">307</td><td colspan="1" rowspan="1">系统退出</td></tr><tr><td colspan="1" rowspan="1">308</td><td colspan="1" rowspan="1">现在时间</td></tr><tr><td colspan="1" rowspan="1">309</td><td colspan="1" rowspan="1">新年快乐</td></tr><tr><td colspan="1" rowspan="1">310</td><td colspan="1" rowspan="1">信息已确认</td></tr><tr><td colspan="1" rowspan="1">311</td><td colspan="1" rowspan="1">星期</td></tr><tr><td colspan="1" rowspan="1">312</td><td colspan="1" rowspan="1">业主车辆</td></tr><tr><td colspan="1" rowspan="1">313</td><td colspan="1" rowspan="1">已超时</td></tr><tr><td colspan="1" rowspan="1">314</td><td colspan="1" rowspan="1">已电子支付</td></tr><tr><td colspan="1" rowspan="1">315</td><td colspan="1" rowspan="1">音量</td></tr><tr><td colspan="1" rowspan="1">316</td><td colspan="1" rowspan="1">银联卡</td></tr><tr><td colspan="1" rowspan="1">317</td><td colspan="1" rowspan="1">用户</td></tr><tr><td colspan="1" rowspan="1">318</td><td colspan="1" rowspan="1">余额</td></tr><tr><td colspan="1" rowspan="1">319</td><td colspan="1" rowspan="1">余额不足</td></tr><tr><td colspan="1" rowspan="1">320</td><td colspan="1" rowspan="1">余额不足请及时充值</td></tr><tr><td colspan="1" rowspan="1">321</td><td colspan="1" rowspan="1">余额为</td></tr><tr><td colspan="1" rowspan="1">322</td><td colspan="1" rowspan="1">元旦节快乐</td></tr><tr><td colspan="1" rowspan="1">323</td><td colspan="1" rowspan="1">月租车未授权</td></tr><tr><td colspan="1" rowspan="1">324</td><td colspan="1" rowspan="1">月租快到期</td></tr><tr><td colspan="1" rowspan="1">325</td><td colspan="1" rowspan="1">允许取卡</td></tr><tr><td colspan="1" rowspan="1">326</td><td colspan="1" rowspan="1">允许入场</td></tr><tr><td colspan="1" rowspan="1">327</td><td colspan="1" rowspan="1">暂停使用</td></tr><tr><td colspan="1" rowspan="1">328</td><td colspan="1" rowspan="1">闸杆下落</td></tr><tr><td colspan="1" rowspan="1">329</td><td colspan="1" rowspan="1">正在计费</td></tr><tr><td colspan="1" rowspan="1">330</td><td colspan="1" rowspan="1">正在人工处理</td></tr><tr><td colspan="1" rowspan="1">331</td><td colspan="1" rowspan="1">支付宝</td></tr><tr><td colspan="1" rowspan="1">332</td><td colspan="1" rowspan="1">直接放行</td></tr><tr><td colspan="1" rowspan="1">333</td><td colspan="1" rowspan="1">中秋节快乐</td></tr><tr><td colspan="1" rowspan="1">334</td><td colspan="1" rowspan="1">重复读卡</td></tr><tr><td colspan="1" rowspan="1">335</td><td colspan="1" rowspan="1">重复进入</td></tr><tr><td colspan="1" rowspan="1">336</td><td colspan="1" rowspan="1">重复进入不允许</td></tr><tr><td colspan="1" rowspan="1">337</td><td colspan="1" rowspan="1">重复外出</td></tr><tr><td colspan="1" rowspan="1">338</td><td colspan="1" rowspan="1">重复外出不允许</td></tr><tr><td colspan="1" rowspan="1">339</td><td colspan="1" rowspan="1">注意安全</td></tr><tr><td colspan="1" rowspan="1">340</td><td colspan="1" rowspan="1">祝您健康</td></tr><tr><td colspan="1" rowspan="1">341</td><td colspan="1" rowspan="1">祝您早日康复</td></tr></table>