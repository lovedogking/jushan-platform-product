# 【显示屏通信协议】

## 文件修改记录

<table><tr><td rowspan=1 colspan=1>版本</td><td rowspan=1 colspan=1>修改日期</td><td rowspan=1 colspan=1>修改描述</td><td rowspan=1 colspan=1>修改人员</td></tr><tr><td rowspan=1 colspan=1>v1.00</td><td rowspan=1 colspan=1>2017-2-4</td><td rowspan=1 colspan=1>创建此文档</td><td rowspan=1 colspan=1>刘明</td></tr><tr><td rowspan=1 colspan=1>v1.01</td><td rowspan=1 colspan=1>2019-4-23</td><td rowspan=1 colspan=1>增加绘图二维码接口</td><td rowspan=1 colspan=1>Lyon</td></tr><tr><td rowspan=1 colspan=1>v1.02</td><td rowspan=1 colspan=1>2019-5-7</td><td rowspan=1 colspan=1>更正了文档描述的一些错误</td><td rowspan=1 colspan=1>Lyon</td></tr><tr><td rowspan=1 colspan=1>v1.03</td><td rowspan=1 colspan=1>2019-7-4</td><td rowspan=1 colspan=1>增加了脱机临时车权限指令</td><td rowspan=1 colspan=1>Lyon</td></tr><tr><td rowspan=1 colspan=1>v1.04</td><td rowspan=1 colspan=1>2019-11-29</td><td rowspan=1 colspan=1>增加了修改密码的接口</td><td rowspan=1 colspan=1>Lyon</td></tr><tr><td rowspan=1 colspan=1>v1.05</td><td rowspan=1 colspan=1>2020-1-06</td><td rowspan=1 colspan=1>1.更新了语音库列表2.增加了获取GPS位置信息接口</td><td rowspan=1 colspan=1>Lyon</td></tr><tr><td rowspan=1 colspan=1>v1.06</td><td rowspan=1 colspan=1>2020-4-16</td><td rowspan=1 colspan=1>增加了余位界面</td><td rowspan=1 colspan=1>Lyon</td></tr><tr><td rowspan=1 colspan=1>v1.07</td><td rowspan=1 colspan=1>2020-7-23</td><td rowspan=1 colspan=1>增加了开闸、关闸指令</td><td rowspan=1 colspan=1>Lyon</td></tr><tr><td rowspan=1 colspan=1>V1.08</td><td rowspan=1 colspan=1>2020-7-29</td><td rowspan=1 colspan=1>增加了继电器常开/常关功能</td><td rowspan=1 colspan=1>Lyon</td></tr><tr><td rowspan=1 colspan=1>V1.09</td><td rowspan=1 colspan=1>2020-9-16</td><td rowspan=1 colspan=1>修改了0x62指令字体描述的错误</td><td rowspan=1 colspan=1>Lyon</td></tr><tr><td rowspan=1 colspan=1>V1.10</td><td rowspan=1 colspan=1>2020-9-21</td><td rowspan=1 colspan=1>增加了设置补光灯时间段指令</td><td rowspan=1 colspan=1>Lyon</td></tr><tr><td rowspan=1 colspan=1>V2.0</td><td rowspan=1 colspan=1>2020-12-10</td><td rowspan=1 colspan=1>1．废除了6E指令，新增6F指令替代，增加字体、界面显示时间、显示标志位 三个参数。2．更正了文档其它地方的描述错误。</td><td rowspan=1 colspan=1>Lyon</td></tr></table>

## 目录

概述 .. ................................ ...4  
产品特点：...... ............................................................................................... .....4  
基本电气参数： .................................................................... ............................... ...4  
通信协议 .......... .....................................................................................................................5  
2.1 数据包格式 .. ................................................................................................. .....5  
2.2 数据校验算法(CRC16).......................................................................................... ......5  
2.3 指令集... ............................................... .............................. ....8  
0x00(连接设备)... .......................... ..................................................... .....9  
0x02(修改密码)... ............................................................................................... ....9  
0x05(同步时间)....................................................................................................................10  
0x07(更改通信地址) ... ......................................................................................... ..11  
0x0A(更改波特率) ...............................................................................................................11  
0x0C (调整显示亮度) ..... .......................................................................................... ....12  
0x0D(调整音量) ... ................................................ .......12  
0x0F(设置继电器状态/红绿灯/开闸/关闸) . .................................................. ...12  
0x1A(设置 485 工作模式)...... ............................................. .......13  
0x1E(恢复出厂设置) .... ........................................................................... .......13  
0xD3(配置脱机临时车权限) ..................................................................................... .......14  
0xD4(获取 GPS 位置信息) ... ......................................................................... ...15  
0x30(播放语音)............ ....................................................................................................16  
0x31(停止播放语音) .... .......................................................................................... .....17  
0x62(下载临时文本) ......... ............................................................................. .......18  
0x67(下载广告语)..... ..................................................................................... .......19  
0x68(显示广告语).......... ..................... ...................................................................20  
0x6F(单包多行显示，带语音)... ............................................................................ .......20  
0xE1(扫码支付界面) ......... ...............................................................22  
0xE5(扫码支付界面-绘图模式)---V2 指令集.. .............................................. ....23  
0xE3(余位界面)........ ..................................................................24  
0xD5(获取输入事件)... .................................................................................... ......24  
0xD8(设置补光灯时间段)...... ........................................................................ ......25  
2.4 显示时间 ... .................................................................................... ......26  
2.5 带有变量的语音匹配格式 ........................................................................................... .......27  
附表 1 ..... ..................................................................................... .......28  
语音列表： .... .....................................................................................................................28

## 概述

产品特点：

支持2路HUB08接口和1路HUB75接口，最大支持分辨率为64\*64单双色和全彩色。

 支持 RS485通信接口，最高波特率为 115200。

 支持多款相机脱机显示和语音播报协议。

 支持二维码显示。

 支持简体中文、繁体、英语文字显示。

支持语音播报功能，10 级音量调节。

 扫描频率最高达 2000Hz,10 级亮度调节。

 提供 1路继电器可以控制红绿灯板。

 板载实时时钟功能(RTC)，最大误差为每天+-3秒。

## 基本电气参数：

显示卡供电电压: 5V

工作温度： -20℃～+80℃

显示卡最大功耗: 7.5W（包括音频放大器）

音频输出功率: 4 欧姆5W

无故障工作时间： ≧60000 小时

## 通信协议

通信协议在这里是指显示屏应用层软件协议，主要功能是用来实现面向终端连接的数据传输，其规范了设备端(显示屏)与主机端的通信所必须遵循的规则和约定。关于传输介质的标准或者接口协议请参见相关的文档。

## 2.1 数据包格式

显示屏通信是以包为最小单元进行数据交互，数据包可以看作是运输数据的载体。在本协议中数据包结构由控制域、数据域、校验域3 部分组成，见下图。

数 据 包 格 式
<table><tr><td></td><td colspan="3">控制域</td><td colspan="3">数据域</td><td>校验域</td></tr><tr><td rowspan="2">字段</td><td>DA</td><td>VR</td><td>PN</td><td>CMD</td><td>DL</td><td>DATA</td><td>CRC</td></tr><tr><td>(设备地址)</td><td>(协议版本)</td><td>(16位包序列)</td><td>(指令ID）</td><td>(数据长度)</td><td>(数据)</td><td>(16 位包校验值)</td></tr><tr><td>取值范围</td><td>0x00~0xff</td><td>0x00~0xff</td><td>0x0000~0xffff</td><td>0x00~0xff</td><td>0x0000~0xffff</td><td>0x00~0xff</td><td>0x0000~0xffff</td></tr><tr><td>长度</td><td>1Byte</td><td>1Byte</td><td>2Bytes</td><td>1Byte</td><td>1~2Byte</td><td>Max 255Bytes</td><td>2Bytes</td></tr></table>

DA:为显示屏的地址，取值范围为 0x00 - 0xFF。

VR:描述了协议版本号，目前支持版本100 和版本200。

PN:是包的序列，在传输超过255 个字节的数据时，需要分包传输，PN表示了包的序列，每次交互完之后自增1，设置为最大值0XFFFF时表示当前包是最后一个包。在传输小于255 个字节的数据时，该值应该设置为0XFFFF。

CMD: 该字段描述了该包的作用，显示屏通过这个值完成不同的功能服务。

DL: 该字段用来描述 DATA 的数据长度，当 VR 为 100 时，DL 只有 1 个字节，最大取值为 255。  
当VR 为200 时，DL 为2 个字节组成16 位数据,最大取值为65535。

DATA: 是参数数据，每条指令携带的参数和长度是不同的，详解参见后指令集章节。

CRC: 数据包的校验码。参与校验的字段是从DA到DATA的最后一个字节。校验算法采用CRC16，见后章节详解。

## 2.2 数据校验算法(CRC16)

CRC16 是数据通信领域中最常用的一种查错校验码，其特征是信息字段和校验字段的长度可以任意选定。循环冗余检查（CRC）是一种数据传输检错功能，对数据进行多项式计算，并将得到的结果附在帧的后面，接收设备也执行类似的算法，以保证数据传输的正确性和完整性。

参与计算的字段为DA到DATA之间的数据。计算得到的16 位校验码在传送时，采用小端模式，低字节在前，高字节在后。

算法 C 实现:

static const uint8_t _CRCHi[] = {

<table><tr><td>0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41,0x00,0xC1,0x81,0x40, 0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41,0x00,0xC1,0x81,0x40, 0x01,0xC0,0x80,0x41,0x01,0xC0,0x80,0x41,0x00,0xC1,0x81,0x40, 0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41,0x01,0xC0,0x80,0x41, 0x00,0xC1,0x81, 0x40,0x01,0xC0,0x80,0x41,0x00,0xC1,0x81,0x40, 0x00,0xC1,0x81, 0x40,0x01,0xC0,0x80,0x41,0x01,0xC0,0x80,0x41, 0x00,0xC1,0x81, 0x40,0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41, 0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41,0x01,0xC0,0x80,0x41, 0x00,0xC1,0x81,0x40,0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41, 0x01,0xC0,0x80,0x41,0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41, 0x00,0xC1,0x81,0x40,0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41, 0x00,0xC1, 0x81, 0x40,0x01,0xC0,0x80,0x41,0x01,0xC0, 0x80,0x41, 0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41,0x00,0xC1,0x81,0x40, 0x00,0xC1,0x81, 0x40,0x01,0xC0,0x80,0x41,0x01,0xC0,0x80,0x41, 0x00,0xC1,0x81,0x40,0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41, 0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41,0x01,0xC0,0x80,0x41, 0x00,0xC1,0x81,0x40</td></tr></table>

0x00, 0xC0, 0xC1, 0x01, 0xC3, 0x03, 0x02, 0xC2, 0xC6, 0x06, 0x07, 0xC7,   
0x05, 0xC5, 0xC4, 0x04, 0xCC, 0x0C, 0x0D, 0xCD, 0x0F, 0xCF, 0xCE, 0x0E,   
0x0A, 0xCA, 0xCB, 0x0B, 0xC9, 0x09, 0x08, 0xC8, 0xD8, 0x18, 0x19, 0xD9,   
0x1B, 0xDB, 0xDA, 0x1A, 0x1E, 0xDE, 0xDF, 0x1F, 0xDD, 0x1D, 0x1C, 0xDC,   
0x14, 0xD4, 0xD5, 0x15, 0xD7, 0x17, 0x16, 0xD6, 0xD2, 0x12, 0x13, 0xD3,   
0x11, 0xD1, 0xD0, 0x10, 0xF0, 0x30, 0x31, 0xF1, 0x33, 0xF3, 0xF2, 0x32,   
0x36, 0xF6, 0xF7, 0x37, 0xF5, 0x35, 0x34, 0xF4, 0x3C, 0xFC, 0xFD, 0x3D,   
0xFF, 0x3F, 0x3E, 0xFE, 0xFA, 0x3A, 0x3B, 0xFB, 0x39, 0xF9, 0xF8, 0x38,   
0x28, 0xE8, 0xE9, 0x29, 0xEB, 0x2B, 0x2A, 0xEA, 0xEE, 0x2E, 0x2F, 0xEF,   
0x2D, 0xED, 0xEC, 0x2C, 0xE4, 0x24, 0x25, 0xE5, 0x27, 0xE7, 0xE6, 0x26,   
0x22, 0xE2, 0xE3, 0x23, 0xE1, 0x21, 0x20, 0xE0, 0xA0, 0x60, 0x61, 0xA1,   
0x63, 0xA3, 0xA2, 0x62, 0x66, 0xA6, 0xA7, 0x67, 0xA5, 0x65, 0x64, 0xA4,   
0x6C, 0xAC, 0xAD, 0x6D, 0xAF, 0x6F, 0x6E, 0xAE, 0xAA, 0x6A, 0x6B, 0xAB,   
0x69, 0xA9, 0xA8, 0x68, 0x78, 0xB8, 0xB9, 0x79, 0xBB, 0x7B, 0x7A, 0xBA,   
0xBE, 0x7E, 0x7F, 0xBF, 0x7D, 0xBD, 0xBC, 0x7C, 0xB4, 0x74, 0x75, 0xB5,   
0x77, 0xB7, 0xB6, 0x76, 0x72, 0xB2, 0xB3, 0x73, 0xB1, 0x71, 0x70, 0xB0,   
0x50, 0x90, 0x91, 0x51, 0x93, 0x53, 0x52, 0x92, 0x96, 0x56, 0x57, 0x97,   
0x55, 0x95, 0x94, 0x54, 0x9C, 0x5C, 0x5D, 0x9D, 0x5F, 0x9F, 0x9E, 0x5E,   
0x5A, 0x9A, 0x9B, 0x5B, 0x99, 0x59, 0x58, 0x98, 0x88, 0x48, 0x49, 0x89,   
0x4B, 0x8B, 0x8A, 0x4A, 0x4E, 0x8E, 0x8F, 0x4F, 0x8D, 0x4D, 0x4C, 0x8C,   
0x44, 0x84, 0x85, 0x45, 0x87, 0x47, 0x46, 0x86, 0x82, 0x42, 0x43, 0x83,   
0x41, 0x81, 0x80, 0x40

```c
CRC16 校验函数
*/
uint16_t MB_CRC16(uint8_t *pFrame, uint16_t count)
{
uint8_t CRCHi = 0xFF;
uint8_t CRCLo = 0xFF;
int32_t index;
while(count--) {
index = CRCLo ^ *(pFrame++);
CRCLo = (uint8_t)(CRCHi ^ _CRCHi[index]);
CRCHi = _CRCLo[index];
}
return (uint16_t)(CRCHi << 8 | CRCLo );
}
```

## 2.3 指令集

<table><tr><td rowspan=1 colspan=5>指           令           集</td><td rowspan=1 colspan=1></td></tr><tr><td rowspan=1 colspan=1></td><td rowspan=1 colspan=1>指令代码</td><td rowspan=1 colspan=1>功能描述</td><td rowspan=1 colspan=1>请求参数格式</td><td rowspan=1 colspan=1>回复参数格式</td><td rowspan=1 colspan=1>版本</td></tr><tr><td rowspan=4 colspan=1></td><td rowspan=1 colspan=1>0x00</td><td rowspan=1 colspan=1>连接</td><td rowspan=1 colspan=1>PSWD[6~32]</td><td rowspan=1 colspan=1>RET + HVR[3] + SVR[3] + CPVR[3]</td><td rowspan=20 colspan=1>1. 0. 0</td></tr><tr><td rowspan=1 colspan=1>0x02</td><td rowspan=1 colspan=1>修改密码</td><td rowspan=1 colspan=1>PSWD[6~32]</td><td rowspan=1 colspan=1>ACK(成功为0，失败返回非0的值）</td></tr><tr><td rowspan=1 colspan=1>0x05</td><td rowspan=1 colspan=1>同步时间</td><td rowspan=1 colspan=1>Y[2]+M +D+W+H+N+S</td><td rowspan=1 colspan=1>ACK(成功为0，失败返回非0的值)</td></tr><tr><td rowspan=1 colspan=1>0x07</td><td rowspan=1 colspan=1>更改通信地址</td><td rowspan=1 colspan=1>NDA</td><td rowspan=1 colspan=1>ACK（成功为0，失败返回非0的值）</td></tr><tr><td rowspan=7 colspan=1>系统管理和配置</td><td rowspan=1 colspan=1>0x0A</td><td rowspan=1 colspan=1>更改波特率</td><td rowspan=1 colspan=1>BAUD[4]</td><td rowspan=1 colspan=1>ACK（成功为0，失败返回非0的值）</td></tr><tr><td rowspan=1 colspan=1>0x0C</td><td rowspan=1 colspan=1>调整显示亮度</td><td rowspan=1 colspan=1>LIGHT</td><td rowspan=1 colspan=1>ACK(成功为0，失败返回非0的值）</td></tr><tr><td rowspan=1 colspan=1>0x0D</td><td rowspan=1 colspan=1>调整音量</td><td rowspan=1 colspan=1>VOL</td><td rowspan=1 colspan=1>ACK(成功为0，失败返回非0的值）</td></tr><tr><td rowspan=1 colspan=1>0x0F</td><td rowspan=1 colspan=1>设置继电器状态</td><td rowspan=1 colspan=1>CH+OF+RESERVED[3] + 0T</td><td rowspan=1 colspan=1>ACK(成功为0，失败返回非0的值）</td></tr><tr><td rowspan=1 colspan=1>0x1A</td><td rowspan=1 colspan=1>设置485模式</td><td rowspan=1 colspan=1>MODE</td><td rowspan=1 colspan=1>ACK（成功为0，失败返回非0的值）</td></tr><tr><td rowspan=1 colspan=1>0x1E</td><td rowspan=1 colspan=1>恢复出厂设置</td><td rowspan=1 colspan=1>无</td><td rowspan=1 colspan=1>ACK(成功为0，失败返回非0的值）</td></tr><tr><td rowspan=1 colspan=1>0xD3</td><td rowspan=1 colspan=1>配置临时车权限</td><td rowspan=1 colspan=1>TCA</td><td rowspan=1 colspan=1>ACK(成功为0，失败返回非0的值）</td></tr><tr><td rowspan=1 colspan=1></td><td rowspan=1 colspan=1>0xD4</td><td rowspan=1 colspan=1>获取GPS位置信息</td><td rowspan=1 colspan=1>无</td><td rowspan=1 colspan=1>MSG</td></tr><tr><td rowspan=1 colspan=1></td><td rowspan=1 colspan=1>0xD8</td><td rowspan=1 colspan=1>设置补光灯时间段</td><td rowspan=1 colspan=1>SH +SM + EH +EM</td><td rowspan=1 colspan=1>ACK(成功为0，失败返回非0的值）</td></tr><tr><td rowspan=2 colspan=1>语音接口</td><td rowspan=1 colspan=1>0x30</td><td rowspan=1 colspan=1>播放语音</td><td rowspan=1 colspan=1> OPT + TEXT[MAX 254]</td><td rowspan=1 colspan=1>ACK(成功为0，失败返回非0的值)</td></tr><tr><td rowspan=1 colspan=1>0x31</td><td rowspan=1 colspan=1>停止播放语音</td><td rowspan=1 colspan=1>无</td><td rowspan=1 colspan=1>ACK(成功为0，失败返回非0的值)</td></tr><tr><td rowspan=7 colspan=1>显示接口</td><td rowspan=1 colspan=1>0x62</td><td rowspan=1 colspan=1>下载临时信息</td><td rowspan=1 colspan=1> TWID+ETM+ETS+GST+DT+RA1[2]+FINDEX+DRS+TC[4]+RA2[4]+ TL+R3+TEXT[.]</td><td rowspan=1 colspan=1>ACK(成功为0，失败返回非0的值）</td></tr><tr><td rowspan=1 colspan=1>0x67</td><td rowspan=1 colspan=1>下载广告语</td><td rowspan=1 colspan=1> TWID+R1+R2+ETM+ETS+R3+DT+RA1[2]+FINDEX+TC[4]+RA2[4]+ TL+R3+TEXT[.]</td><td rowspan=1 colspan=1>ACK（成功为0，失败返回非0的值）</td></tr><tr><td rowspan=1 colspan=1>0x68</td><td rowspan=1 colspan=1>显示广告语</td><td rowspan=1 colspan=1>TWID + FID</td><td rowspan=1 colspan=1>ACK（成功为0，失败返回非0的值）</td></tr><tr><td rowspan=1 colspan=1>0x6F</td><td rowspan=1 colspan=1>多行单包显示带语音</td><td rowspan=1 colspan=1> SF+GST+TEXT_CONTEXT_NUMBER+TEXT_CONTEXT[.]+ VF+VTL+VT[..]</td><td rowspan=1 colspan=1>ACK(成功为0，失败返回非0的值)</td></tr><tr><td rowspan=1 colspan=1>0xE1</td><td rowspan=1 colspan=1>扫码支付界面</td><td rowspan=1 colspan=1> SF +EM+ETM +ST +NI +TIME +MONEY +ML +TL +FLAGS +QRSIZE + RESERVED[15] + MSG[ML +1]+TEXT[TL+1]</td><td rowspan=1 colspan=1>ACK(成功为0，失败返回非0的值)</td></tr><tr><td rowspan=1 colspan=1>0xE3</td><td rowspan=1 colspan=1>余位显示界面</td><td rowspan=1 colspan=1>SF+EM+ETM+ST+NI+NUM</td><td rowspan=1 colspan=1>ACK(成功为0，失败返回非0的值）</td><td rowspan=1 colspan=1></td></tr><tr><td rowspan=1 colspan=1>0xE5</td><td rowspan=1 colspan=1>扫码支付界面</td><td rowspan=1 colspan=1>SF + EM + ETM + ST + NI + VEN + TL + TEXT[TL] + BMPDATA[..]</td><td rowspan=1 colspan=1>ACK(成功为0，失败返回非0的值)</td><td rowspan=1 colspan=1>2.0.0</td></tr></table>

## 0x00(连接设备)

对于支持加密的显示屏设备，需要先通过连接进行密码验证，验证通过之后才能进行其它接口操作，否则其它接口将不会被执行。显示屏设备收到该指令之后会返回设备的版本信息。

对于不支持加密的显示屏设备，该指令仅用作获取版本信息。

请求格式: $D A + V R + P N [ 2 ] + 0 \times 0 0 + D L + P W D [ 6 ^ { \sim } 3 2 ] + C R C [ 2 ]$

请求参数描述:DL 取值为密码长度。

PWD:为 6\~32 位连接密码。

回复格式: $\mathsf { D A } + \mathsf { V R } + \mathsf { P N } [ 2 ] + \mathsf { O x } \mathsf { O } \mathsf { O } + \mathsf { D } \mathsf { L } + \mathsf { F }$

回复参数描述: 包含 10 个字节的参数，DL 取值为 10。

RET:密码校验结果，0 为校验通过，1 为不通过。

HVR:硬件版本号，3 个字节，表示格式为 x.y.z

SVR:软件版本号，3 个字节，格式为 x.y.z

CPVR:通信协议版本号，3 个字节，格式为 x.y.z

## 使用例子：

主机发送: 00 64 FF FF 00 06 31 35 39 37 35 33 FB 06

设备回复: 00 64 FF FF 00 0A 00 31 30 32 34 36 30 31 30 30 9F EF

## 0x02(修改密码)

该指令仅对支持加密的显示屏设备有效，在修改密码之前需要先通过旧密码连接设备，验证通过之后，才能正确修改密码。

请求格式: $D A + V R + P N [ 2 ] + 0 \times 0 2 + D L + P W D [ 6 ^ { \sim } 3 2 ] + C R C [ 2 ]$

请求参数描述:DL 取值为密码长度。

PWD:为 $6 \sim 3 2$ 位新密码。

回复格式: $D A + V R + P N [ 2 ] + 0 \times 0 2 + D L + A C K + C R C [ 2 ]$

回复参数描述: 包含1 个字节的参数，DL 取值为1。

ACK:密码校验结果，0 为校验通过，其它取值为不通过。

## 使用例子：

主机发送: 00 64 FF FF 02 06 38 38 38 38 38 38 23 2F

设备回复: 00 64 FF FF 02 01 00 97 74

## 0x05(同步时间)

同步时间的功能是从外部同步显示屏的系统时间。显示屏的系统时间每天的误差在+- 5 秒钟之内，因此在需要极高精度的时间应用场合，应该每隔一段时间对显示屏同步时间。

请求格式: DA + VR + PN[2] + 0x05 + DL + Y[2] + M + D + W + H + N + S + CRC[2]

请求参数描述:包含8 个字节的参数，DL 取值为8。

Y:为年的 16 位数据，小端模式，取值范围在 1970\~2099。

M:为月的 8 位数据,取值范围在 1 \~ 12。

D:为日的 8 位数据，取值范围在 1\~31。

W:为星期的8 位数据，取值范围在1\~7。1 对应星期日，2 \~7 对应星期一至六。

H:为小时的 8 位数据,取值范围在 0\~23。

N:为分钟的8 位数据，取值范围在0\~59。

S:为秒钟的8 位数据，取值范围在0\~59。

回复格式: DA + VR + PN[2] + 0x05 + DL + ACK + CRC[2]

回复参数描述: 包含 1 个字节的参数，DL 取值为 1， ACK 是显示屏返回的结果，取值为 0 表示成功，非 0 表示不成功。

使用例子：同步时间至 2016 年 3 月 10 日星期四 15:05:06。

主机发送: 00 64 FF FF 05 08 E0 07 03 0A 07 0F 05 06 65 68

设备回复: 00 64 FF FF 05 01 00 26 B5

## 0x07(更改通信地址)

通信地址是用来区分多个显示屏设备的身份标识。主机在进行任何请求时，通过指定通信地址来选择目标设备。主机通过该指令更改了设备端的地址之后，在随后的操作中应当使用新地址来通信。

请求格式: $D A + V R + P N [ 2 ] + 0 \times 0 7 + D L + N D A + C R C [ 2 ]$

请求参数描述:包含 1 个字节的参数，DL 取值为 1。

NDA:为新的通信地址，取值范围在0\~255。

回复格式: $D A + V R + P N [ 2 ] + 0 \times 0 7 + D L + A C K + C R C [ 2 ]$

回复参数描述: 包含1 个字节的参数，DL 取值为1， ACK 是显示屏返回的结果，取值为 0 表示成功，非0 表示不成功。

使用例子：通信地址更改为 1 。

主机发送: 00 64 FF FF 07 01 01 46 B5

设备回复: 00 64 FF FF 07 01 00 87 75

## 0x0A(更改波特率)

波特率是指在串口设备通信中的通信速率，即指每秒钟的比特位 BPS。速率越高每秒钟发送的数据位就越多，速度就越快。显示屏支持的波特率范围在2400 \~ 115200。在实际应用中建议使用标准的 2400、4800、9600、19200、38400、57600、115200，可以保证最小的频差。

主机通过该指令更改成功之后，需要使用新的波特率与显示屏通信。

请求格式: $D A + V R + P N [ 2 ] + 0 \times 0 A + D L + B A \cup D [ 4 ] + C R C [ 2 ]$

请求参数描述:包含 4 个字节的参数，DL 取值为 4。

BAUD:为新的波特率，取值范围在 2400\~115200。

回复格式: $D A + V R + P N [ 2 ] + 0 \times 0 A + D L + A C K + C R C [ 2 ]$

回复参数描述: 包含1 个字节的参数，DL 取值为1， ACK 是显示屏返回的结果，取值为 0 表示成功，非0 表示不成功。

使用例子：波特率更改为19200。

主机发送: 00 64 FF FF 0A 04 00 4B 00 00 2B B2

设备回复: 00 64 FF FF 0A 01 00 16 B6

## 0x0C (调整显示亮度)

显示屏的明亮程度可以通过该指令从 10%\~100% 调节。

请求格式: DA + VR + PN[2] + 0x0C + DL +LIGHT+ CRC[2]

请求参数描述:包含 1 个字节的参数，DL 取值为 1。

LIGHT:为新的亮度等级百分比，取值范围在10\~100。

回复格式: DA + VR + PN[2] + 0x0C+ DL + ACK + CRC[2]

回复参数描述: 包含1 个字节的参数，DL 取值为1， ACK 是显示屏返回的结果，取值为 0 表示成功，非0 表示不成功。

使用例子：亮度更改为50%。

主机发送: 00 64 FF FF 0C 01 32 77 62

设备回复: 00 64 FF FF 0C 01 00 F6 B7

## 0x0D(调整音量)

显示屏的音量等级可以通过该指令从10%\~100% 调节。

请求格式: $D A + V R + P N [ 2 ] + 0 \times 0 0 + D L + V O L + C R C [ 2 ]$

请求参数描述:包含 1 个字节的参数，DL 取值为 1。

VOL:为新的音量等级百分比，取值范围在10\~100。

回复格式: DA + VR + PN[2] + 0x0D+ DL + ACK + CRC[2]

回复参数描述: 包含1 个字节的参数，DL 取值为1， ACK 是显示屏返回的结果，取值为 0 表示成功，非0 表示不成功。

使用例子：音量更改为 50%。

主机发送: 00 64 FF FF 0D 01 32 26 A2

设备回复: 00 64 FF FF 0D 01 00 A7 77

## 0x0F(设置继电器状态/红绿灯/开闸/关闸)

本指令用来打开继电器，打开的时间可以控制，时间结束时显示卡会自动关闭继电器。

请求格式: DA + VR + PN[2] + 0x0F + DL + CH+OF+RESERVED[3] + OT + CRC[2]

请求参数描述:包含6 字节的参数，DL 取值为6

CH:操作的通道

<table><tr><td rowspan=1 colspan=2>CH取值及含义</td></tr><tr><td rowspan=1 colspan=1>通道</td><td rowspan=1 colspan=1>描述</td></tr><tr><td rowspan=1 colspan=1> $0 \mathrm { x } 0 0$ </td><td rowspan=1 colspan=1>红绿灯</td></tr><tr><td rowspan=1 colspan=1> $0 \mathrm { x 0 1 }$ </td><td rowspan=1 colspan=1>开闸</td></tr><tr><td rowspan=1 colspan=1> $0 \mathrm { x 0 2 }$ </td><td rowspan=1 colspan=1>关闸</td></tr><tr><td rowspan=1 colspan=1> $0 \mathrm { x F E }$ </td><td rowspan=1 colspan=1>红绿灯和开闸同步操作</td></tr></table>

OF:操作选项，目前没有用到，取值不关心。

RESERVED:保留的3 个字节，取值固定为0。

OT:为继电器打开时间，单位为秒，最大取值为254 秒。取值为0 时,立即关闭继电器.取值为255 时,继电器将会一直打开。

回复格式: DA + VR + PN[2] + 0x0F+ DL + ACK + CRC[2]

回复参数描述: 包含 1 个字节的参数，DL 取值为 1， ACK 是显示屏返回的结果，取值为 0 表示成功，非0 表示不成功。

使用例子：打开继电器 5 秒钟。

主机发送: 00 64 FF FF 0F 06 00 01 00 00 00 05 24 B7

设备回复: :00 64 FF FF 0F 01 00 06 B7

## 0x1A(设置 485 工作模式)

在有些时候可能不需要显示卡回复，此时可以通过该指令来设置显示卡的 485 工作模式为半工模式。

请求格式: DA + VR + PN[2] + 0x1A+ DL + MODE+ CRC[2]

请求参数描述:包含 1 个字节的参数，DL 取值为 1。

MODE:为0 时表示半双工模式，为1 时表示半工模式。

回复格式: $D A + V R + P N [ 2 ] + 0 \times 1 A + D L + A C K + C R C [ 2 ]$

回复参数描述: 包含 1 个字节的参数，DL 取值为 1， ACK 是显示屏返回的结果，取值为 0 表示成功，非0 表示不成功。

使用例子：显示屏 485 模式为半工模式。

主机发送: 00 64 FF FF 1A 01 01 D6 B3

设备回复: 设备工作在半工模式，不会返回数据。

## 0x1E(恢复出厂设置)

该指令用来将显示屏设置为出厂设置。

请求格式: $D A + V R + P N [ 2 ] + 0 { \times } 1 5 + D L + C R C [ 2 ]$

请求参数描述:包含0 个字节的参数，DL 取值为0。

回复格式: DA + VR + PN[2] + 0x1E+ DL + ACK + CRC[2]

回复参数描述: 包含1 个字节的参数，DL 取值为1， ACK 是显示屏返回的结果，取值为 0 表示成功，非0 表示不成功。

使用例子：恢复出厂设置。

主机发送: 00 64 FF FF 1E 00 78 57

设备回复: 00 64 FF FF 1E 01 00 56 B2

## 0xD3(配置脱机临时车权限)

该指令用来设置脱机模式下的临时车通行权限。

请求格式: DA + VR + PN[2] + 0xD3+ DL + TCA + CRC[2]

请求参数描述:包含 1 个字节的参数，DL 取值为 1。

TCA临时车权限,取值范围及含义如下表:

<table><tr><td rowspan=1 colspan=2>TCA取值含义</td></tr><tr><td rowspan=1 colspan=1>取值</td><td rowspan=1 colspan=1>描述</td></tr><tr><td rowspan=1 colspan=1>0x00</td><td rowspan=1 colspan=1>禁止通行</td></tr><tr><td rowspan=1 colspan=1>0x01</td><td rowspan=1 colspan=1>允许通行</td></tr><tr><td rowspan=1 colspan=1>0x02</td><td rowspan=1 colspan=1>人工放行</td></tr><tr><td rowspan=1 colspan=1>0x03</td><td rowspan=1 colspan=1>无权通行</td></tr></table>

回复格式: DA + VR + PN[2] + 0xD3+ DL + ACK + CRC[2]

回复参数描述: 包含 1 个字节的参数，DL 取值为 1， ACK 是显示屏返回的结果，取值为 0 表示成功，非0 表示不成功。

使用例子：显示屏脱机配置为临时车禁止通行。

主机发送: 00 64 FF FF D3 01 00 C7 4D

设备回复: 00 64 FF FF D3 01 00 C7 4D

## 0xD4(获取 GPS 位置信息)

该指令用来获取设备的GPS位置信息,该指令仅在安装了GPS定位模块的主板才支持。

请求格式: DA + VR + PN[2] + 0xD4+ DL + CRC[2]

请求参数描述:包含0 个字节的参数，DL 取值为0。

回复格式: DA + VR + PN[2] + 0Xd4+ DL + MSG + CRC[2]

回复参数描述:

MSG:返回的定位信息,格式如下:

RMC

\$--RMC.hmmss.ss,A.,Ill,a.y.y,a,x.x,x.x,xxxx,x.x,a\*h

样例数据：

\$GPRMC,100646.000,A,3109.9704,N,12123.4219,E,0.257,335.62,291216.,,A\*59

<table><tr><td rowspan=1 colspan=1>名称</td><td rowspan=1 colspan=1>样例</td><td rowspan=1 colspan=1>单位</td><td rowspan=1 colspan=1>描述</td></tr><tr><td rowspan=1 colspan=1>消息ID</td><td rowspan=1 colspan=1>$GPRMC</td><td rowspan=1 colspan=1></td><td rowspan=1 colspan=1>RMC 协议头</td></tr><tr><td rowspan=1 colspan=1>UTC时间</td><td rowspan=1 colspan=1>100646.000</td><td rowspan=1 colspan=1></td><td rowspan=1 colspan=1>hhmmss.ss</td></tr><tr><td rowspan=1 colspan=1>状态</td><td rowspan=1 colspan=1>A</td><td rowspan=1 colspan=1></td><td rowspan=1 colspan=1>A=数据有效；V=数据无效</td></tr><tr><td rowspan=1 colspan=1>纬度</td><td rowspan=1 colspan=1>2109.9704</td><td rowspan=1 colspan=1></td><td rowspan=1 colspan=1>ddmm.mmmm</td></tr><tr><td rowspan=1 colspan=1>N/S 指示</td><td rowspan=1 colspan=1>N</td><td rowspan=1 colspan=1></td><td rowspan=1 colspan=1>N=北，S=南</td></tr><tr><td rowspan=1 colspan=1>经度</td><td rowspan=1 colspan=1>11123.4219</td><td rowspan=1 colspan=1></td><td rowspan=1 colspan=1>dddmm.mmmm</td></tr><tr><td rowspan=1 colspan=1>E/W指示</td><td rowspan=1 colspan=1>E</td><td rowspan=1 colspan=1></td><td rowspan=1 colspan=1>W=西，E=东</td></tr><tr><td rowspan=1 colspan=1>地面速度</td><td rowspan=1 colspan=1>0.257</td><td rowspan=1 colspan=1>Knot（节）</td><td rowspan=1 colspan=1></td></tr><tr><td rowspan=1 colspan=1>方位</td><td rowspan=1 colspan=1>335.62</td><td rowspan=1 colspan=1>度</td><td rowspan=1 colspan=1></td></tr><tr><td rowspan=1 colspan=1>日期</td><td rowspan=1 colspan=1>291216</td><td rowspan=1 colspan=1></td><td rowspan=1 colspan=1> ddmmyy</td></tr><tr><td rowspan=1 colspan=1>磁变量</td><td rowspan=1 colspan=1></td><td rowspan=1 colspan=1></td><td rowspan=1 colspan=1></td></tr><tr><td rowspan=1 colspan=1>校验和</td><td rowspan=1 colspan=1>*59</td><td rowspan=1 colspan=1></td><td rowspan=1 colspan=1></td></tr><tr><td rowspan=1 colspan=1>&lt;CR&gt;&lt;LF&gt;</td><td rowspan=1 colspan=1></td><td rowspan=1 colspan=1></td><td rowspan=1 colspan=1>消息结束</td></tr></table>

## 使用例子：

主机发送: 00 64 FF FF D4 00 2E F7

<table><tr><td rowspan=1 colspan=1>设备回复:00 64 FF FF D4 47 24 47 4E 52 4D 43 2C 30 30 33</td><td rowspan=1 colspan=1>2C 30 3033</td></tr><tr><td rowspan=1 colspan=1>3734 32 2E 30 30 33 2C 56 2C 32 32 33 35 2E</td><td></td></tr><tr><td rowspan=1 colspan=1>32 30 39 34 2C 4E 2C 31 31 33 35 31 2E 35 34</td><td></td></tr><tr><td rowspan=1 colspan=1>32 31 2C 45 2C 30 2E 30 30 302C 30 2E3030</td><td></td></tr><tr><td rowspan=1 colspan=1>2C 30 36 30 31 38 30 2C 2C 2C 4E 2A 35 41 0D</td><td></td></tr></table>

0A 53 9D

## 0x30(播放语音)

显示屏系统储存了停车场常用的 300 句短语或词组，用户可以直接调出播放，语音列表参见附表1。语音文本的匹配方式采用词组或者短语进行匹配，在一些含有变量信息的文字中，如金钱、车牌号、日期等，显示屏会自动处理。在发送语音文本时，需要将词组和短语之间加入逗号或者句号分隔，显示屏才能正确的匹配。显示屏软件提供了语音播放队列(FIFO)，用户可以连续发送最多 50 条语音文本到队列中，显示屏会自动按照队列的顺序逐个播放；这些特性对语音组合使用提供了便利。

需要播报语音的文字通过该指令发送到显示屏，显示屏收到之后会与内置的语音库匹配，如果存在相应的语音文件，就会播报语音。显示屏的语音匹配方式是按照词组或短语匹配的，在发送文字时，需要用逗号或者句号分隔每个词组或短语。例如"欢迎光临,请入场停车"。

$$
\frac { 1 + 1 } { 1 4 } + 1 4 \leq t < \frac { 1 } { 2 } \leq t < \frac { 1 } { 3 } . \mathsf { D A } : \mathsf { D A } + \mathsf { V R } + \mathsf { P N } [ 2 ] + 0 \times 3 0 + \mathsf { D L } + { \mathsf { O T P } } + 7 \mathsf { E X T } [ \mathsf { M A X \ 2 5 4 } ] + \mathsf { C R C } [ 2 ]
$$

请求参数描述:参数长度 DL 为1 加上文字长度。

OTP:为操作选项字。

<table><tr><td rowspan=1 colspan=2>OPT 取值含义</td></tr><tr><td rowspan=1 colspan=1>取值</td><td rowspan=1 colspan=1>描述</td></tr><tr><td rowspan=1 colspan=1>0x00</td><td rowspan=1 colspan=1>添加到语音队列但是不开始播放</td></tr><tr><td rowspan=1 colspan=1>0x01</td><td rowspan=1 colspan=1>添加到语音队列并且开始播放</td></tr><tr><td rowspan=1 colspan=1>0x02</td><td rowspan=1 colspan=1>先清除队列，再添加新语音到队列，然后开始播放</td></tr></table>

TEXT:为播报语音的文字，最大的长度为 254 个字节。文字编码格式只支持 ASCII和 GBK2312。 注意不支持 UNICODE。

回复格式: DA + VR + PN[2] + 0x30+ DL + ACK + CRC[2]

回复参数描述: 包含1 个字节的参数，DL 取值为1， ACK 是显示屏返回的结果，取值为 0 表示成功，非0 表示不成功。

使用例子 1：立即播放"欢迎光临,请入场停车"。

主机发送: 00 64 FF FF 30 14 01 BB B6 D3 AD B9 E2 C1 D9 2C C7 EB C8 EB B3 A1 CD A3 B3 B5 A9 36 设备回复: 00 64 FF FF 30 01 00 36 BB

使用例子 2：向显示屏添加"欢迎光临,请入场停车"不立即播放语音，只添加到语音队列。然后再添加"请交费 10 元" 并且开始播放语音。

#1 主机发送: 00 64 FF FF 30 14 00 BB B6 D3 AD B9 E2 C1 D9 2C C7 EB C8 EB B3 A1 CD A3 B3 B5 94 E7#1 设备回复: 00 64 FF FF 30 01 00 36 BB

#2 主机发送: 00 64 FF FF 30 0B 01 C7 EB BD BB B7 D1 31 30 D4 AA 43 1E

#2 设备回复: 00 64 FF FF 30 01 00 36 BB

## 0x31(停止播放语音)

通过这个指令可以立即停止显示屏播报语音，并且清空语音队列。

请求格式: DA + VR + PN[2] + 0x31+ DL + CRC[2]

请求参数描述:无参数，DL 取值为0。

回复格式: DA + VR + PN[2] + 0x31+ DL + ACK + CRC[2]

回复参数描述: 包含1 个字节的参数，DL 取值为1， ACK 是显示屏返回的结果，取值为 0 表示成功，非0 表示不成功。

使用例子：立即停止播报语音。

主机发送: 00 64 FF FF 31 00 64 67

设备回复: 00 64 FF FF 31 01 00 67 7B

## 0x62(下载临时文本)

下载临时文本的功能是用来显示临时文字信息。临时的文字掉电不会保存。

请 求 格 式 : DA+VR+PN[2]+0x62+DL+TWID+ETM+ETS+GST+DT+RA1[2]+FINDEX+DRS+TC[4]+RA2[4]+TL+R3+TEXT[...]+CRC[2]

请求参数描述: DL 等于 19 个字节再加上文本长度。

TWID:为显示窗口的 ID，表示第几行。

ETM:文字显示的方式。取值范围及含义见下表:

<table><tr><td rowspan=1 colspan=2>ETM取值含义</td></tr><tr><td rowspan=1 colspan=1>编码</td><td rowspan=1 colspan=1>描述</td></tr><tr><td rowspan=1 colspan=1>0x00</td><td rowspan=1 colspan=1>立即显示</td></tr><tr><td rowspan=1 colspan=1>0x01</td><td rowspan=1 colspan=1>从右向左移动</td></tr><tr><td rowspan=1 colspan=1>0x02</td><td rowspan=1 colspan=1>从左向右移动</td></tr><tr><td rowspan=1 colspan=1>0x03</td><td rowspan=1 colspan=1>从下向上移动</td></tr><tr><td rowspan=1 colspan=1>0x04</td><td rowspan=1 colspan=1>从上向下移动</td></tr><tr><td rowspan=1 colspan=1>0x05</td><td rowspan=1 colspan=1>向下拉窗</td></tr><tr><td rowspan=1 colspan=1>0x06</td><td rowspan=1 colspan=1>向上拉窗</td></tr><tr><td rowspan=1 colspan=1>0x07</td><td rowspan=1 colspan=1>向左拉窗</td></tr><tr><td rowspan=1 colspan=1>0x08</td><td rowspan=1 colspan=1>向右拉窗</td></tr><tr><td rowspan=1 colspan=1>0x0D</td><td rowspan=1 colspan=1>逐字显示</td></tr><tr><td rowspan=1 colspan=1>0x15</td><td rowspan=1 colspan=1>连续左移</td></tr></table>

ETS:为文字进入的速度。取值范围为1\~32。时基取决于当前的扫描周期。

GST: 界面显示时间，单位为秒。该参数只有在多界面的显示模板才生效，取值为0 时表示使用模板默认值，取值为 255 表示一直显示当前界面，取值 1\~254 表示用户自定义的界面显示时间。

DT:为文字停留的时间。取值范围为0 \~255。

RA1[2]:2 个字节保留值，固定取值为 0x00 0x00。

FINDEX:为字体索引值，注意:每种主板支持字体不一样

<table><tr><td rowspan=1 colspan=2>FINDEX E取值含义</td></tr><tr><td rowspan=1 colspan=1>取值</td><td rowspan=1 colspan=1>描述</td></tr><tr><td rowspan=1 colspan=1>0x00</td><td rowspan=1 colspan=1>ASCII8</td></tr><tr><td rowspan=1 colspan=1>0x01</td><td rowspan=1 colspan=1>ASCII10</td></tr><tr><td rowspan=1 colspan=1>0x02</td><td rowspan=1 colspan=1>ASCII13</td></tr><tr><td rowspan=1 colspan=1>0x03</td><td rowspan=1 colspan=1>宋体16</td></tr><tr><td rowspan=1 colspan=1>0x04</td><td rowspan=1 colspan=1>宋体24</td></tr><tr><td rowspan=1 colspan=1>0x05</td><td rowspan=1 colspan=1>宋体32</td></tr><tr><td rowspan=1 colspan=1>0x06</td><td rowspan=1 colspan=1>宋体48</td></tr><tr><td rowspan=1 colspan=1>0x07</td><td rowspan=1 colspan=1>宋体64</td></tr></table>

DRS:为显示的次数。取值范围为0\~255,当为0 的时，表示无限循环显示。

TC[4]:为文字的颜色值。存储结构为 R G B A 三基色，各占 8 位，R 表示红色分量，G表示绿色分量,B 表示蓝色分量，A 目前没用使用，作为保留字。各取值范围为 0\~255。RA2[4]: 4 个字节保留值，固定取值为 0x00 0x00 0x00 0x00。

TL:为文字的长度。

R1:保留值，固定取值为 0x00。

TEXT:为显示的文字内容，支持 ASCII 和 GBK2312 编码。

回复格式: DA + VR + PN[2] + 0x62+ DL + ACK + CRC[2]

回复参数描述: 包含1 个字节的参数，DL 取值为1，ACK是显示屏返回的结果，取值为 0 表示成功，非0 表示不成功。

使用例子：第一行显示欢迎光临。

主机发送: 00 64 FF FF 62 1B 00 00 01 00 05 00 01 03 00 FF 00 00 00 00 00 00 00 08 00 BB B6 D3 AD B9 E2 C1 D9 58 96

设备回复: 00 64 FF FF 62 01 00 97 6A

## 0x67(下载广告语)

ID 为 0\~3 显示窗口都关联了 1 个独立的广告语文件，存储在显示屏的外部存储器中，掉电后不会丢失。显示屏空闲时，会主动循环的显示这个文本(如果存在)。需要注意的是不要频繁的擦写广告语，否则会降低显示屏的存储器寿命。频繁擦写的内容可以用 0X62(下载临时文本)指令显示。

请 求 格 式 : DA+VR+PN[2]+0x67+DL+TWID+R1+R2+ETM+ETS+R3+DT+RA1[2]+FINDEX+TC[4]+RA2[4]+TL+R3+TEXT[...]+CRC[2]

请求参数描述:DL 等于20 个字节再加上文本长度。

TWID:为显示窗口的ID，表示第几行。

R1:保留值，固定取值为0x00。

R2:保留值，固定取值为0x0C。

ETM:文字显示的方式。取值范围及含义请参考0X62 指令。

ETS:文字进入的速度。取值范围为1\~32。时基取决于当前的扫描周期。

R3:保留值，固定取值为0x00。

DT:为文字停留的时间。取值范围为 0 \~255。

RA1[2]:2 个字节保留值，固定取值为 0x00 0x00。

FINDEX:为文字的字体索引值。取值范围及含义请参考 0X62 指令。

TC:为文字的颜色值。取值范围及含义请参考 0X62 指令。

RA2[4]: 4 个字节保留值，固定取值为 0x00 0x00 0x00 0x00。

TL:为文字的长度。

R3:保留值，固定取值为0x00。

TEXT:为显示的文字内容，支持 ASCII 和 GBK2312 编码。

回复格式: DA + VR + PN[2] + 0x67+ DL + ACK + CRC[2]

回复参数描述: 包含1 个字节的参数，DL 取值为1，ACK是显示屏返回的结果，取值为 0 表示成功，非 0 表示不成功。

使用例子：下载“自动识别减速慢行”到显示屏第一行。

主机发送: 00 64 FF FF 67 24 00 00 0C 15 01 00 05 15 01 03 FF 00 00 00 00 00 00 00 10 00 D7 D4 B6 AF

CA B6 B1 F0 BC F5 CB D9 C2 FD D0 D0 84 F9

设备回复: 00 64 FF FF 67 01 00 87 6B

## 0x68(显示广告语)

显示文本文件的功能是在指定的窗口上立即显示已经下载的广告语。

请求格式: DA + VR + PN[2] + 0x68+ DL +TWID+FID + CRC[2]

请求参数描述: 包含2 个字节的参数，DL 取值为2。

TWID:为窗口 ID,用于标识创建的窗口身份。

FID:为文件的 ID，用于指定操作的文本文件。取值范围为 0\~1。

回复格式: $D A + V R + P N [ 2 ] + 0 \times 6 8 + D L + A C K + C R C [ 2 ]$

回复参数描述: 包含1 个字节的参数，DL 取值为1，ACK是显示屏返回的结果，取值为0 表示成功，非0 表示不成功。

使用例子：立即显示第一行广告语。

主机发送: 00 64 FF FF 68 02 00 00 D8 76

设备回复:00 64 FF FF 68 01 00 B7 68

## 0x6F(单包多行显示，带语音)

使用单包多行显示，减少了频繁的设置文本的工作，每行的内容用‘\n’ 换行符分隔，最后一行用‘\0’结束，语音用‘\a‘开始,‘\0’结束。 但是该指令也有一些限制，其文本头和文字以及语音的长度之和， 不能超出单包的最大长度，即 255 个字节。

请 求 格 式 : DA+VR+PN[2]+0x6F+DL+SF+GST+TEXT_CONTEXT_NUMBER+TEXT_CONTEXT[…]+VF+VTL+VT[...]+CRC[2]

请求参数描述:可变长参数，最大为 255 字节。

SF:取值为0 时表示下载到临时区，取值为1 时表示下载到存储区，频繁修改的内容建议下载到临时区。

GST: 界面显示时间，单位为秒。该参数只有在多界面的显示模板才生效，取值为0 时表示使用模板默认值，取值为255 表示一直显示当前界面，取值1\~254 表示用户自定义的界面显示时间。

TEXT_CONTEXT_NUMBER:为文本参数数量，即几行文本。目前版本最大支持 4 行。 TEXT_CONTEXT_NUMBER：为文本参数数量，即几行文本。目前版本最大支持4行。

TEXT_CONTEXT:为文本参数，每个文本参数控制一行，用 0X0D 分开，最后一个文本参数用 0X00 结束，最多 4 个文本参数。文本参数的结构为 LID + DM + DS + DT+ DR + TC[4]+TL +TEXT[...]+0X0D/0X00 各参数取值含义如下。

LID:为显示行号。0 表示第 1 行，1 表示第 2 行，以此类推。

DM:为显示模式

<table><tr><td rowspan=1 colspan=2>DM取值含义</td></tr><tr><td rowspan=1 colspan=1>编码</td><td rowspan=1 colspan=1>描述</td></tr><tr><td rowspan=1 colspan=1>0x00</td><td rowspan=1 colspan=1>立即显示</td></tr><tr><td rowspan=1 colspan=1>0x01</td><td rowspan=1 colspan=1>从右向左移动</td></tr><tr><td rowspan=1 colspan=1>0x02</td><td rowspan=1 colspan=1>从左向右移动</td></tr><tr><td rowspan=1 colspan=1>0x03</td><td rowspan=1 colspan=1>从下向上移动</td></tr><tr><td rowspan=1 colspan=1>0x04</td><td rowspan=1 colspan=1>从上向下移动</td></tr><tr><td rowspan=1 colspan=1>0x05</td><td rowspan=1 colspan=1>向下拉窗</td></tr><tr><td rowspan=1 colspan=1>0x06</td><td rowspan=1 colspan=1>向上拉窗</td></tr></table>