//用于接入VZ的车牌识别设备（包括智能识别一体机和智能识别终端等）的应用程序接口
#ifndef _VZ_LPRC_SDK_H_
#define _VZ_LPRC_SDK_H_
#include <VzLPRClientSDKDefine.h>

#ifdef _DEV_API_
#define VZ_LPRC_API extern "C" __declspec(dllexport)
#else
#define VZ_LPRC_API extern "C" __declspec(dllimport)
#endif

#define VZ_DEPRECATED __declspec(deprecated)

/**
* @defgroup group_global 全局操作函数
* @defgroup group_device 单个设备操作函数
* @defgroup group_callback 回调函数
*/

/**
*  @brief 全局初始化
*  @return 0表示成功，-1表示失败
*  @note 在所有接口调用之前调用
*  @ingroup group_global
*/
VZ_LPRC_API int __stdcall VzLPRClient_Setup();

/**
*  @brief 全局释放
*  @note 在程序结束时调用，释放SDK的资源
*  @ingroup group_global
*/
VZ_LPRC_API void __stdcall VzLPRClient_Cleanup();

/**
*  @brief 通过该回调函数获得找到的设备基本信息
*  @param  [IN] pStrDevName 设备名称
*  @param  [IN] pStrIPAddr	设备IP地址
*  @param  [IN] usPort1		设备端口号
*  @param  [IN] usPort2		预留
*  @param  [IN] SL          设备序列号低位字节
*  @param  [IN] SH			设备序列号高位字节	
*  @param  [IN] pUserData	回调函数上下文
*  @ingroup group_callback
*/
typedef void (__stdcall *VZLPRC_FIND_DEVICE_CALLBACK)(const char *pStrDevName, const char *pStrIPAddr, WORD usPort1, WORD usPort2, unsigned int SL,unsigned int SH, void *pUserData);

/**
*  @brief 开始查找设备
*  @param  [IN] func 找到的设备通过该回调函数返回
*  @param  [IN] pUserData 回调函数中的上下文
*  @return 0表示成功，-1表示失败
*  @ingroup group_global
*/
VZ_LPRC_API int __stdcall VZLPRClient_StartFindDevice(VZLPRC_FIND_DEVICE_CALLBACK func, void *pUserData);

/**
*  @brief 停止查找设备
*  @ingroup group_global
*/
VZ_LPRC_API void __stdcall VZLPRClient_StopFindDevice();

/**
*  @brief 通过该回调函数获得设备的一般状态信息
*  @param  [IN] handle		由VzLPRClient_Open函数获得的句柄
*  @param  [IN] pUserData	回调函数上下文
*  @param  [IN] eNotify		通用信息反馈类型
*  @param  [IN] pStrDetail	详细描述字符串
*  @ingroup group_callback
*/
typedef void (__stdcall *VZLPRC_COMMON_NOTIFY_CALLBACK)(VzLPRClientHandle handle, void *pUserData,
													   VZ_LPRC_COMMON_NOTIFY eNotify, const char *pStrDetail);

/**
*  @brief 设置设备连接反馈结果相关的回调函数
*  @param  [IN] func 设备连接结果和状态，通过该回调函数返回
*  @param  [IN] pUserData 回调函数中的上下文
*  @return 0表示成功，-1表示失败
*  @ingroup group_global
*/
VZ_LPRC_API int __stdcall VZLPRClient_SetCommonNotifyCallBack(VZLPRC_COMMON_NOTIFY_CALLBACK func, void *pUserData);

/**
*  @brief 打开一个设备
*  @param  [IN] pStrIP 设备的IP地址
*  @param  [IN] wPort 设备的端口号
*  @param  [IN] pStrUserName 访问设备所需用户名
*  @param  [IN] pStrPassword 访问设备所需密码
*  @return 返回设备的操作句柄，当打开失败时，返回0
*  @ingroup group_device
*/
VZ_LPRC_API VzLPRClientHandle __stdcall VzLPRClient_Open(const char *pStrIP, WORD wPort, const char *pStrUserName, const char *pStrPassword);

/**
*  @brief 打开一个设备
*  @param  [IN] pStrIP 设备的IP地址
*  @param  [IN] wPort 设备的端口号
*  @param  [IN] pStrUserName 访问设备所需用户名
*  @param  [IN] pStrPassword 访问设备所需密码
*  @param  [IN] wRtspPort 流媒体的端口号,为0表示使用默认
*  @return 返回设备的操作句柄，当打开失败时，返回0
*  @ingroup group_device
*/
VZ_LPRC_API VzLPRClientHandle __stdcall VzLPRClient_OpenEx(const char *pStrIP, WORD wPort, const char *pStrUserName, const char *pStrPassword,WORD wRtspPort);

/**
*  @brief 关闭一个设备
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @return 0表示成功，-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_Close(VzLPRClientHandle handle);

/**
*  @brief 通过IP地址关闭一个设备
*  @param  [IN] pStrIP 设备的IP地址
*  @return 0表示成功，-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_CloseByIP(const char *pStrIP);

/**
*  @brief 获取连接状态
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN/OUT] pStatus 输入获取状态的变量地址，输出内容为 1已连上，0未连上
*  @return 0表示成功，-1表示失败
*  @note   用户可以周期调用该函数来主动查询设备是否断线，以及断线后是否恢复连接
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_IsConnected(VzLPRClientHandle handle, BYTE *pStatus);

/**
*  @brief 播放实时视频
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] hWnd 窗口的句柄
*  @return 播放的句柄，-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_StartRealPlay(VzLPRClientHandle handle, void *hWnd);

/**
*  @brief 停止播放指定的播放句柄
*  @param  [IN] nPlayHandle 播放的句柄
*  @return 0表示成功，-1表示失败
*  @note   可用来停止播放来自函数VzLPRClient_StartRealPlay和VzLPRClient_StartRealPlayFrameCallBack的播放句柄
*  @note   停止播放以后，该播放句柄就失效了
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_StopRealPlay(int nPlayHandle);

/**
*  @brief  通过该回调函数获得实时图像数据
*  @param  [IN] handle		由VzLPRClient_Open函数获得的句柄
*  @param  [IN] pUserData	回调函数的上下文
*  @param  [IN] pFrame		图像帧信息，详见结构体定义VzYUV420P
*  @return 0表示成功，-1表示失败
*  @ingroup group_callback
*/
typedef void (__stdcall *VZLPRC_VIDEO_FRAME_CALLBACK)(VzLPRClientHandle handle, void *pUserData,
													  const VzYUV420P *pFrame);
/**
*  @brief 设置实时图像数据的回调函数
*  @param  [IN] handle		由VzLPRClient_Open函数获得的句柄
*  @param  [IN] func		实时图像数据函数
*  @param  [IN] pUserData	回调函数中的上下文
*  @return 0表示成功，-1表示失败
*  @note   DEPRECATED: 请转换为使用VzLPRClient_StartRealPlayFrameCallBack(handle, NULL, func, pUserData);
*  @ingroup group_device
*/
VZ_DEPRECATED VZ_LPRC_API int __stdcall VzLPRClient_SetVideoFrameCallBack(VzLPRClientHandle handle, VZLPRC_VIDEO_FRAME_CALLBACK func, void *pUserData);

/**
*  @brief  通过该回调函数获得实时图像数据
*  @param  [IN] handle		由VzLPRClient_Open函数获得的句柄
*  @param  [IN] pUserData	回调函数的上下文
*  @param  [IN] pFrame		图像帧信息，详见结构体定义VZ_LPRC_IMAGE_INFO
*  @ingroup group_callback
*/
typedef void (__stdcall *VZLPRC_VIDEO_FRAME_CALLBACK_EX)(VzLPRClientHandle handle, void *pUserData,
														 const VZ_LPRC_IMAGE_INFO *pFrame);
/**
*  @brief 获取实时视频帧，图像数据通过回调函数到用户层，用户可改动图像内容，并且显示到窗口
*  @param  [IN] handle		由VzLPRClient_Open函数获得的句柄
*  @param  [IN] hWnd		窗口的句柄，如果为有效值，则视频图像会显示到该窗口上，如果为空，则不显示视频图像
*  @param  [IN] func		实时图像数据函数
*  @param  [IN] pUserData	回调函数中的上下文
*  @return 播放的句柄，-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_StartRealPlayFrameCallBack(VzLPRClientHandle handle, void *hWnd,
												  VZLPRC_VIDEO_FRAME_CALLBACK_EX func, void *pUserData);

/**
*  @brief  通过该回调函数获得编码数据
*  @param  [IN] handle		由VzLPRClient_Open函数获得的句柄
*  @param  [IN] pUserData	回调函数的上下文
*  @param  [IN] eDataType	数据类型，详见枚举VZ_LPRC_DATA_TYPE
*  @param  [IN] pData		数据帧信息，详见结构体定义VZ_LPRC_DATA_INFO
*  @ingroup group_callback
*/
typedef void (__stdcall *VZLPRC_VIDEO_DATA_CALLBACK)(VzLPRClientHandle handle, void *pUserData,
													 VZ_LPRC_DATA_TYPE eDataType, const VZ_LPRC_DATA_INFO *pData);

/**
*  @brief 获取实时编码数据
*  @param  [IN] handle		由VzLPRClient_Open函数获得的句柄
*  @param  [IN] func		回调函数
*  @param  [IN] pUserData	回调函数中的上下文
*  @return 播放的句柄，-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetRealDataCallBack(VzLPRClientHandle handle,
												  VZLPRC_VIDEO_DATA_CALLBACK func, void *pUserData);

/**
*  @brief 通过该回调函数获得车牌识别信息
*  @param  [IN] handle			由VzLPRClient_Open函数获得的句柄
*  @param  [IN] pUserData		回调函数的上下文
*  @param  [IN] pResult			车牌信息数组首地址，详见结构体定义 TH_PlateResult
*  @param  [IN] uNumPlates		车牌数组中的车牌个数
*  @param  [IN] eResultType		车牌识别结果类型，详见枚举类型定义VZ_LPRC_RESULT_TYPE
*  @param  [IN] pImgFull		当前帧的图像内容，详见结构体定义VZ_LPRC_IMAGE_INFO
*  @param  [IN] pImgPlateClip	当前帧中车牌区域的图像内容数组，其中的元素与车牌信息数组中的对应
*  @return 0表示成功，-1表示失败
*  @note   如果需要该回调函数返回截图内容 pImgFull 和pImgPlateClip，需要在设置回调函数（VzLPRClient_SetPlateInfoCallBack）时允许截图内容，否则该回调函数返回的这两个指针为空；
*  @note   实时结果（VZ_LPRC_RESULT_REALTIME）的回调是不带截图内容的
*  @ingroup group_callback
*/
typedef int (__stdcall *VZLPRC_PLATE_INFO_CALLBACK)(VzLPRClientHandle handle, void *pUserData,
													const TH_PlateResult *pResult, unsigned uNumPlates,
													VZ_LPRC_RESULT_TYPE eResultType,
													const VZ_LPRC_IMAGE_INFO *pImgFull,
													const VZ_LPRC_IMAGE_INFO *pImgPlateClip);

/**
*  @brief 通过该回调函数获得车牌识别信息(组网)
*  @param  [IN] handle			由VzLPRClient_Open函数获得的句柄
*  @param  [IN] pUserData		回调函数的上下文
*  @param  [IN] exitEntryInfo	车辆出入口信息 0 为入口，1 为出口
*  @param  [IN] exitIvsInfo		车辆出口信息（当设备为入口设备时该字段信息无效）
*  @param  [IN] entryIvsInfo	车辆入口信息
*  @return 0表示成功，-1表示失败
*  @ingroup group_callback
*/

typedef int (__stdcall *VZLPRC_PLATE_GROUP_INFO_CALLBACK)(VzLPRClientHandle handle, 
														  void *pUserData,
														  int exitEntryInfo,
														  const TH_PlateResult *exitIvsInfo, 
														  const TH_PlateResult *entryIvsInfo, 
														  const IVS_DG_DEVICE_INFO *exitDGInfo, 
														  const IVS_DG_DEVICE_INFO *entryDGInfo);
/**
*  @brief 设置识别结果的回调函数
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] func 识别结果回调函数，如果为NULL，则表示关闭该回调函数的功能
*  @param  [IN] pUserData 回调函数中的上下文
*  @param  [IN] bEnableImage 指定识别结果的回调是否需要包含截图信息：1为需要，0为不需要
*  @return 0表示成功，-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetPlateInfoCallBack(VzLPRClientHandle handle, VZLPRC_PLATE_INFO_CALLBACK func, void *pUserData, int bEnableImage);

/**
*  @brief 设置识别结果的回调函数（组网）
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] func 识别结果回调函数，如果为NULL，则表示关闭该回调函数的功能（组网）
*  @param  [IN] pUserData 回调函数中的上下文
*  @return 0表示成功，-1表示失败
*  @ingroup group_device
*/

VZ_LPRC_API int __stdcall VzLPRClient_SetPlateGroupInfoCallBack(VzLPRClientHandle handle, VZLPRC_PLATE_GROUP_INFO_CALLBACK func, void *pUserData);


/**
*  @brief 设置脱机结果的回调函数
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] func 识别结果回调函数，如果为NULL，则表示关闭该回调函数的功能
*  @param  [IN] pUserData 回调函数中的上下文
*  @param  [IN] bEnableImage 指定识别结果的回调是否需要包含截图信息：1为需要，0为不需要
*  @return 0表示成功，-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetOfflinePlateInfoCallBack(VzLPRClientHandle handle, VZLPRC_PLATE_INFO_CALLBACK func, void *pUserData, int bEnableImage);


/**
*  @brief 发送软件触发信号，强制处理当前时刻的数据并输出结果
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @return 0表示成功，-1表示失败
*  @note   车牌识别结果通过回调函数的方式返回，如果当前视频画面中无车牌，则回调函数不会被调用
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_ForceTrigger(VzLPRClientHandle handle);

/**
*  @brief 设置虚拟线圈
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] pVirtualLoops 虚拟线圈的结构体指针
*  @return 0表示成功，-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetVirtualLoop(VzLPRClientHandle handle, const VZ_LPRC_VIRTUAL_LOOPS *pVirtualLoops);

/**
*  @brief 获取已设置的虚拟线圈
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] pVirtualLoops 虚拟线圈的结构体指针
*  @return 0表示成功，-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetVirtualLoop(VzLPRClientHandle handle,  VZ_LPRC_VIRTUAL_LOOPS *pVirtualLoops);

/**
*  @brief 设置虚拟线圈，线圈支持更多的顶点
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] pVirtualLoops 虚拟线圈的结构体指针
*  @return 0表示成功，-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetVirtualLoopEx(VzLPRClientHandle handle, const VZ_LPRC_VIRTUAL_LOOPS_EX *pVirtualLoops);

/**
*  @brief 获取已设置的虚拟线圈，线圈支持更多的顶点
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] pVirtualLoops 虚拟线圈的结构体指针
*  @return 0表示成功，-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetVirtualLoopEx(VzLPRClientHandle handle,  VZ_LPRC_VIRTUAL_LOOPS_EX *pVirtualLoops);

/**
*  @brief 设置识别区域
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] pROI 识别区域的结构体指针
*  @return 0表示成功，-1表示失败
*  @note   车牌在识别区域中才会被识别；
*  @note   识别区域可用于屏蔽复杂环境的干扰等，提高识别速度和准确性，避免误识别；
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetRegionOfInterest(VzLPRClientHandle handle, const VZ_LPRC_ROI *pROI);

/**
*  @brief 获取已设置的识别区域
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] pROI 识别区域的结构体指针
*  @return 0表示成功，-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetRegionOfInterest(VzLPRClientHandle handle, VZ_LPRC_ROI *pROI);

/**
*  @brief 设置识别区域
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] pROI 识别区域的结构体指针
*  @return 0表示成功，-1表示失败
*  @note   车牌在识别区域中才会被识别；
*  @note   识别区域可用于屏蔽复杂环境的干扰等，提高识别速度和准确性，避免误识别；
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetRegionOfInterestEx(VzLPRClientHandle handle, const VZ_LPRC_ROI_EX *pROI);

/**
*  @brief 获取已设置的识别区域
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] pROI 识别区域的结构体指针
*  @return 0表示成功，-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetRegionOfInterestEx(VzLPRClientHandle handle, VZ_LPRC_ROI_EX *pROI);
/**
*  @brief 获取已设置的预设省份
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] pProvInfo 预设省份信息指针
*  @return 0表示成功，非0表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetSupportedProvinces(VzLPRClientHandle handle, VZ_LPRC_PROVINCE_INFO *pProvInfo);

/**
*  @brief 设置预设省份
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] nIndex 设置预设省份的序号，序号需要参考VZ_LPRC_PROVINCE_INFO::strProvinces中的顺序，从0开始，如果小于0，则表示不设置预设省份
*  @return 0表示成功，-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_PresetProvinceIndex(VzLPRClientHandle handle, int nIndex);

/**
*  @brief 将图像保存为JPEG到指定路径
*  @param  [IN] pImgInfo 图像结构体，目前只支持默认的格式，即ImageFormatRGB
*  @param  [IN] pFullPathName 设带绝对路径和JPG后缀名的文件名字符串
*  @param  [IN] nQuality JPEG压缩的质量，取值范围1~100；
*  @return 0表示成功，-1表示失败
*  @note   给定的文件名中的路径需要存在
*  @ingroup group_global
*/
VZ_LPRC_API int __stdcall VzLPRClient_ImageSaveToJpeg(const VZ_LPRC_IMAGE_INFO *pImgInfo, const char *pFullPathName, int nQuality);

/**
*  @brief 将图像编码为JPEG，保存到指定内存
*  @param  [IN] pImgInfo 图像结构体，目前只支持默认的格式，即ImageFormatRGB
*  @param  [IN/OUT] pDstBuf JPEG数据的目的存储首地址
*  @param  [IN] uSizeBuf JPEG数据地址的内存的最大尺寸；
*  @param  [IN] nQuality JPEG压缩的质量，取值范围1~100；
*  @return >0表示成功，即编码后的尺寸，-1表示失败，-2表示给定的压缩数据的内存尺寸不够大
*  @ingroup group_global
*/
VZ_LPRC_API int __stdcall VzLPRClient_ImageEncodeToJpeg(const VZ_LPRC_IMAGE_INFO *pImgInfo, void *pDstBuf, unsigned uSizeBuf, int nQuality);

/**
*  @brief 将JPEG解码到图像内存
*  @param  [IN] pSrcData JPEG数据存储首地址
*  @param  [IN] uSizeData JPEG数据尺寸
*  @param  [OUT] pImgInfo 解码后的图像内容
*  @param  [IN] pBufExt 外部给定一个用于保存解码后的内存的地址；
*  @param  [IN] uSizeBufExt 外部给定内存的尺寸，需要大于等于JPEG图像解码后的的RGB内存尺寸
*  @return 0表示成功，-1表示失败，>0表示外部给定的内存不够大，需要给定大于等于此尺寸的内存地址
*  @note  如果没有预知JPEG解码后的图像分辨率，那么就将uSizeBufExt指定为0，接口将返回所需最小内存尺寸，分配好内存后，再调用该接口进行解码
*  @ingroup group_global
*/
VZ_LPRC_API int __stdcall VzLPRClient_JpegDecodeToImage(void *pSrcData, unsigned uSizeData, VZ_LPRC_IMAGE_INFO *pImgInfo, void *pBufExt, unsigned uSizeBufExt);

/**
*  @brief 保存正在播放的视频的当前帧的截图到指定路径
*  @param  [IN] hWnd 正在播放视频的窗口的句柄
*  @param  [IN] pFullPathName 设带绝对路径和JPG后缀名的文件名字符串
*  @param  [IN] nQuality JPEG压缩的质量，取值范围1~100；
*  @return 0表示成功，-1表示失败
*  @note   前提条件是指定的hWnd的窗口已经调用了VzLPRClient_StartRealPlay播放实时视频
*  @note   使用的文件名中的路径需要存在
*  @note   DEPRECATED: 请转为使用VzLPRClient_GetSnapShootToJpeg2
*  @ingroup group_device
*/
VZ_DEPRECATED VZ_LPRC_API int __stdcall VzLPRClient_GetSnapShootToJpeg(void *hWnd, const char *pFullPathName, int nQuality);

/**
*  @brief 保存正在播放的视频的当前帧的截图到指定路径
*  @param  [IN] nPlayHandle 播放的句柄
*  @param  [IN] pFullPathName 设带绝对路径和JPG后缀名的文件名字符串
*  @param  [IN] nQuality JPEG压缩的质量，取值范围1~100；
*  @return 0表示成功，-1表示失败
*  @note   使用的文件名中的路径需要存在
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetSnapShootToJpeg2(int nPlayHandle, const char *pFullPathName, int nQuality);

/**
*  @brief 获取IO输出的状态
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] uChnId IO输出的通道号，从0开始
*  @param  [OUT] pOutput IO输出的状态，0表示继电器开路，1表示继电器闭路
*  @return 0表示成功，-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetIOOutput(VzLPRClientHandle handle, unsigned uChnId, int *pOutput);

/**
*  @brief 设置IO输出的状态
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] uChnId IO输出的通道号，从0开始
*  @param  [OUT] nOutput 将要设置的IO输出的状态，0表示继电器开路，1表示继电器闭路
*  @return 0表示成功，-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetIOOutput(VzLPRClientHandle handle, unsigned uChnId, int nOutput);

/**
*  @brief 设置串口参数
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] nSerialPort 指定使用设备的串口序号：0表示第一个串口，1表示第二个串口
*  @param  [IN] pParameter 将要设置的串口参数，详见定义 VZ_SERIAL_PARAMETER
*  @return 0表示成功，-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetSerialParameter(VzLPRClientHandle handle, int nSerialPort,
														 const VZ_SERIAL_PARAMETER *pParameter);

/**
*  @brief 获取串口参数
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] nSerialPort 指定使用设备的串口序号：0表示第一个串口，1表示第二个串口
*  @param  [OUT] pParameter 将要获取的串口参数，详见定义 VZ_SERIAL_PARAMETER
*  @return 0表示成功，-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetSerialParameter(VzLPRClientHandle handle, int nSerialPort,
														 VZ_SERIAL_PARAMETER *pParameter);
/**
*  @brief 通过该回调函数获得透明通道接收的数据
*  @param  [IN] nSerialHandle VzLPRClient_SerialStart返回的句柄
*  @param  [IN] pRecvData	接收的数据的首地址
*  @param  [IN] uRecvSize	接收的数据的尺寸
*  @param  [IN] pUserData	回调函数上下文
*  @ingroup group_callback
*/
typedef void (__stdcall *VZDEV_SERIAL_RECV_DATA_CALLBACK)(int nSerialHandle, const unsigned char *pRecvData, unsigned uRecvSize, void *pUserData);

/**
*  @brief 开启透明通道
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] nSerialPort 指定使用设备的串口序号：0表示第一个串口，1表示第二个串口
*  @param  [IN] func 接收数据的回调函数
*  @param  [IN] pUserData 接收数据回调函数的上下文
*  @return 返回透明通道句柄，0表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_SerialStart(VzLPRClientHandle handle, int nSerialPort,
												  VZDEV_SERIAL_RECV_DATA_CALLBACK func, void *pUserData);
/**
*  @brief 透明通道发送数据
*  @param [IN] nSerialHandle 由VzLPRClient_SerialStart函数获得的句柄
*  @param [IN] pData 将要传输的数据块的首地址
*  @param [IN] uSizeData 将要传输的数据块的字节数
*  @return 0表示成功，其他值表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_SerialSend(int nSerialHandle, const unsigned char *pData, unsigned uSizeData);

/**
*  @brief 透明通道停止发送数据
*  @param [IN] nSerialHandle 由VzLPRClient_SerialStart函数获得的句柄
*  @return 0表示成功，其他值表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_SerialStop(int nSerialHandle);

/**
*  @brief 调整设备镜头的变倍和聚焦
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] eOPT 操作类型，详见定义VZ_LENS_OPT
*  @return 0表示成功，其他值表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_CtrlLens(VzLPRClientHandle handle, VZ_LENS_OPT eOPT);

/**
*  @brief 获取LED当前亮度等级和最大亮度等级
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [OUT] pLevelNow 用于输出当前亮度等级的地址
*  @param [OUT] pLevelMax 用于输出最高亮度等级的地址
*  @return 0表示成功，其他值表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetLEDLightStatus(VzLPRClientHandle handle, int *pLevelNow, int *pLevelMax);

/**
*  @brief 设置LED亮度等级
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] nLevel，LED亮度等级
*  @return 0表示成功，其他值表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetLEDLightLevel(VzLPRClientHandle handle, int nLevel);

/**
*  @brief 获取LED当前控制模式
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [OUT] pCtrl 用于输出当前LED开关控制模式的地址，详见定义 VZ_LED_CTRL
*  @return 0表示成功，其他值表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetLEDLightControlMode(VzLPRClientHandle handle, VZ_LED_CTRL *pCtrl);

/**
*  @brief 设置LED控制模式
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] eCtrl 控制LED开关模式，详见定义 VZ_LED_CTRL
*  @return 返回值为0表示成功，返回其他值表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetLEDLightControlMode(VzLPRClientHandle handle, VZ_LED_CTRL eCtrl);

/**
*  @brief 写入用户私有数据，可用于二次加密
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] pUserData 用户数据
*  @param [IN] uSizeData 用户数据的长度，最大128字节
*  @return 返回值为0表示成功，返回其他值表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_WriteUserData(VzLPRClientHandle handle, const unsigned char *pUserData, unsigned uSizeData);

/**
*  @brief 读出用户私有数据，可用于二次加密
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN/OUT] pBuffer 用于存放读到的用户数据
*  @param [IN] uSizeBuf 用户数据缓冲区的最小尺寸，不小于128字节
*  @return 返回值为实际用户数据的字节数，返回-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_ReadUserData(VzLPRClientHandle handle, unsigned char *pBuffer, unsigned uSizeBuf);

/**
*  @brief 读出设备序列号，可用于二次加密
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN/OUT] pSN 用于存放读到的设备序列号，详见定义 VZ_DEV_SERIAL_NUM
*  @return 返回值为0表示成功，返回-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetSerialNumber(VzLPRClientHandle handle, VZ_DEV_SERIAL_NUM *pSN);

/**
*  @brief 获取设备的日期时间
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN/OUT] pDTInfo 用于存放获取到的设备日期时间信息，详见定义 VZ_DATE_TIME_INFO
*  @return 返回值为0表示成功，返回-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetDateTime(VzLPRClientHandle handle, VZ_DATE_TIME_INFO *pDTInfo);

/**
*  @brief 设置设备的日期时间
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] pDTInfo 将要设置的设备日期时间信息，详见定义 VZ_DATE_TIME_INFO
*  @return 返回值为0表示成功，返回-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetDateTime(VzLPRClientHandle handle, const VZ_DATE_TIME_INFO *pDTInfo);

/**
*  @brief 获取设备的存储信息
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN/OUT] pDTInfo 用于存放获取到的设备日期时间信息，详见定义 VZ_DATE_TIME_INFO
*  @return 返回值为0表示成功，返回-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetStorageDeviceInfo(VzLPRClientHandle handle, VZ_STORAGE_DEVICES_INFO *pSDInfo);

/**
*  @brief 开始录像功能
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] sFileName 录像文件的路径
*  @return 返回值为0表示成功，返回-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_SaveRealData(VzLPRClientHandle handle,char *sFileName);

/**
*  @brief 停止录像
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @return 返回值为0表示成功，返回-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_StopSaveRealData(VzLPRClientHandle handle);

/**
*  @brief 设置视频OSD参数；
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @return 返回值为0表示成功，返回-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetOsdParam(VzLPRClientHandle handle, VZ_LPRC_OSD_Param *pParam);

/**
*  @brief 获取视频OSD参数；
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @return 返回值为0表示成功，返回-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetOsdParam(VzLPRClientHandle handle, VZ_LPRC_OSD_Param *pParam);

/**
*  @brief 开启脱机检查，实现脱机自动切换白名单的功能。
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] pUserData 接收数据回调函数的上下文
*  @note   有效前提：设备的“输入输出”页面的“白名单验证”TAB页中的“白名单启用条件”设置为“脱机自动启用”
*  @note   调用该接口，设备与调用该接口的客户端之间就会形成绑定，如果客户端与设备之间连接正常，那么设备上的白名单就为停用状态，不会输出IO信号控制道闸抬杆和输出485推送协议；
*  @note   如果客户端与设备设备之间的网络断了，或者客户端关闭，或者调用了关闭脱机检查（CancelOfflineCheck）接口，那么设备上的白名单将启用，设备依据白名单控制道闸抬杆和输出485推送协议；
*  @return 返回值为0表示成功，返回-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetOfflineCheck(VzLPRClientHandle handle);

/**
*  @brief 关闭脱机检查
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] pUserData 接收数据回调函数的上下文
*  @note   有效前提：设备的“输入输出”页面的“白名单验证”TAB页中的“白名单启用条件”设置为“脱机自动启用”
*  @note   关闭脱机检查，设备基于白名单来输出IO信号控制道闸抬杆和输出485推送协议。
*  @return 返回值为0表示成功，返回-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_CancelOfflineCheck(VzLPRClientHandle handle);

/**
*  @brief 根据ID查询记录
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] nID 车牌记录在数据中的ID
*  @return 返回值为0表示成功，返回-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_QueryPlateRecordByID(VzLPRClientHandle handle, int nID);

/**
*  @brief 根据起始时间和车牌关键字查询记录
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] pStartTime 起始时间，格式如"2015-01-02 12:20:30"
*  @param  [IN] pEndTime   起始时间，格式如"2015-01-02 19:20:30"
*  @param  [IN] keyword    车牌号关键字, 如"川"
*  @return 返回值为0表示成功，返回-1表示失败
*  @说明   通过回调返回数据，最多返回100条数据，超过时请调用分页查询的接口
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_QueryRecordByTimeAndPlate(VzLPRClientHandle handle, const char *pStartTime, const char *pEndTime, const char *keyword);


/**
*  @brief 根据时间和车牌号查询记录条数
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] pStartTime 起始时间，格式如"2015-01-02 12:20:30"
*  @param  [IN] pEndTime   起始时间，格式如"2015-01-02 19:20:30"
*  @param  [IN] keyword    车牌号关键字, 如"川"
*  @return 返回值为0表示失败，大于0表示记录条数
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_QueryCountByTimeAndPlate(VzLPRClientHandle handle, const char *pStartTime, const char *pEndTime, const char *keyword);

/**
*  @brief 根据时间和车牌号查询分页记录
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] pStartTime 起始时间，格式如"2015-01-02 12:20:30"
*  @param  [IN] pEndTime   起始时间，格式如"2015-01-02 19:20:30"
*  @param  [IN] keyword    车牌号关键字, 如"川"
*  @param  [IN] start      起始位置大于0,小于结束位置
*  @param  [IN] end        结束位置大于0,大于起始位置，获取记录条数不能大于100
*  @return 返回值为0表示成功，返回-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_QueryPageRecordByTimeAndPlate(VzLPRClientHandle handle, const char *pStartTime, const char *pEndTime, const char *keyword, int start, int end);


/**
*  @brief 设置查询车牌记录的回调函数
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] func 识别结果回调函数，如果为NULL，则表示关闭该回调函数的功能
*  @param  [IN] pUserData 回调函数中的上下文
*  @param  [IN] bEnableImage 指定识别结果的回调是否需要包含截图信息：1为需要，0为不需要
*  @return 0表示成功，-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetQueryPlateCallBack(VzLPRClientHandle handle, VZLPRC_PLATE_INFO_CALLBACK func, void *pUserData);


/**
*  @brief 根据ID获取车牌图片
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] id     车牌记录的ID
*  @param  [IN] pdata  存储图片的内存
*  @param  [IN][OUT] size 为传入传出值，传入为图片内存的大小，返回的是获取到jpg图片内存的大小
*  @return 返回值为0表示成功，返回-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_LoadImageById(VzLPRClientHandle handle, int id, void *pdata, int* size);

/**
*  @brief 获取视频的宽度和高度
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [OUT] pWidth		视频图像宽度变量的地址
*  @param  [OUT] pHeight	视频图像高度变量的地址
*  @return 返回值为0表示成功，返回-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetVideoFrameSize(VzLPRClientHandle handle, int *pWidth, int *pHeight);

/**
*  @brief 获取视频的编码方式
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [OUT] pEncType	返回的编码方式, 0:H264  1:MPEG4  2:JPEG  其他:错误
*  @return 返回值为0表示成功，返回-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetVideoEncodeType(VzLPRClientHandle handle, int *pEncType);

/**
*  @brief 获取GPIO的状态
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] gpioIn 数据为0或1
*  @param  [OUT] value 0代表短路，1代表开路
*  @return 返回值为0表示成功，返回-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetGPIOValue(VzLPRClientHandle handle, int gpioIn, int *value);

/**
*  @brief 根据时间查询脱机记录条数
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] pStartTime 起始时间，格式如"2015-01-02 12:20:30"
*  @param  [IN] pEndTime   起始时间，格式如"2015-01-02 19:20:30"
*  @return 返回值为0表示失败，大于0表示记录条数
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_QueryOfflineCountByTime(VzLPRClientHandle handle, const char *pStartTime, const char *pEndTime);

/**
*  @brief 根据时间和车牌号分页查询脱机记录
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] pStartTime 起始时间，格式如"2015-01-02 12:20:30"
*  @param  [IN] pEndTime   起始时间，格式如"2015-01-02 19:20:30"
*  @param  [IN] start      起始位置大于0,小于结束位置
*  @param  [IN] end        结束位置大于0,大于起始位置，获取记录条数不能大于100
*  @return 返回值为0表示成功，返回-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_QueryPageOfflineRecordByTime(VzLPRClientHandle handle, const char *pStartTime, const char *pEndTime, int start, int end);


/**
*  @brief 获取系统日志
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] iLogType 日志的类型
*  @param [IN/OUT] pstrLogBuffer 日志内容缓冲
*  @param [IN/OUT] iBufferLen 日志内容缓冲区长度,返回日志长度
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetLog(VzLPRClientHandle handle, int iLogType,char* pstrLogBuffer, unsigned int* iBufferLen);

/**
*  @brief 完全恢复默认参数；
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_RestoreConfig(VzLPRClientHandle handle);

/**
*  @brief 部分恢复默认参数，ip端口配置，用户配置，设备名保留；
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_RestoreConfigPartly(VzLPRClientHandle handle);

/**      
*  @brief 获取设备版本号，版本号由4个数值组成
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @return 返回值为0表示成功;
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetRemoteSoftWareVersion(VzLPRClientHandle handle, int *ver1, int *ver2, int *ver3, int *ver4);

/**
*  @brief 格式化sd卡
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SDFormat(VzLPRClientHandle handle);

//升级
/**
*  @brief 获得升级文件版本号
*  @param [IN] file_path_name 升级文件路径
*  @param [OUT] ver1 版本号1
*  @param [OUT] ver2 版本号2
*  @param [OUT] ver3 版本号3
*  @param [OUT] ver4 版本号4
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetUpdateFileVersion(char *file_path_name, int *ver1, int *ver2, int *ver3, int *ver4);

/**
*  @brief 开始升级
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] file_path_name 升级文件路径
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_Update(VzLPRClientHandle handle, char *file_path_name);


/**
*  @brief 获取升级的状态
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @return 升级的状态
*			0: 还未升过级
*			1: 升级成功
*			2: 正在升级，上传中，可以取消升级
*			3: 正在升级，写flash中，取消升级不可用
*		    4: 网络问题，状态未知
*		   -1: 升级失败
*			失败或者网络问题时可以通过getlasterror获得升级错误信息，定义在UPDATE_ERROR里
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetUpdateState(VzLPRClientHandle handle);


/**
*  @brief 取消升级，当升级状态在2时，可以取消升级，其余时候无用途
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_StopUpdate(VzLPRClientHandle handle);


/**
*  @brief 获取升级进度，上传完毕为50，升级完成为100，当前会从50直接跳到100，中间进度不会更新，以后解决
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetUpdateProgress(VzLPRClientHandle handle);

/**
*  @brief 设备网络参数配置。
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] netcfg 参见VZ_LPRC_NETCFG结构体定义
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetNetConfig(VzLPRClientHandle handle, VZ_LPRC_NETCFG* netcfg);

/**
*  @brief 获取设备网络参数配置。
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [OUT] netcfg 参见VZ_LPRC_NETCFG结构体定义
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetNetConfig(VzLPRClientHandle handle, VZ_LPRC_NETCFG* netcfg);


/**
*  @brief 设置IO输出，并自动复位
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] uChnId IO输出的通道号，从0开始
*  @param  [IN] nDuration 延时时间，取值范围[500, 5000]毫秒
*  @return 0表示成功，-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetIOOutputAuto(VzLPRClientHandle handle, unsigned uChnId, int nDuration);

/**
* 获取 LastError
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetLastError();

/**
* 重启设备
*/
VZ_LPRC_API int __stdcall VzLPRClient_RebootDVR( VzLPRClientHandle handle );


/**
*  @brief 开始发送本地音频文件的音频到远程，音频文件支持PCM编码的wav格式
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] strFileName 音频文件的绝对路径
*  @return 返回值：本地音频发送的句柄，需要保存起来，用于其他本地音频发送相关的操作
*/
VZ_LPRC_API int __stdcall VzLPRClient_TalkStartByWaveFile(VzLPRClientHandle handle, const char *strFileName);


/**
*  @brief 停止发送本地音频
*  @param [IN] iTalkHandle VZC_TalkStart函数和VZC_TalkStartByWaveFile函数返回的本地音频发送的句柄
*  @return 返回值：返回值为0表示成功，返回其他值表示失败
*/
VZ_LPRC_API int __stdcall VzLPRClient_TalkStop(int iTalkHandle);


typedef void (CALLBACK * VZLPRC_CallBackTalkData)(int iTalkHandle, const void *pDataBuffer, DWORD dwBufSize ,BYTE byAudioFlag, DWORD dwUser);

/**
*  @brief 开始发送本地话筒的音频到远程
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] fTalkDataCallBack 本地音频数据回调，可以为空
*  @param [IN] dwUser 本地音频数据回调的上下文
*  @return 返回值：本地音频发送的句柄，需要保存起来，用于其他本地音频发送相关的操作
*/
VZ_LPRC_API int __stdcall VzLPRClient_TalkStart(VzLPRClientHandle handle, VZLPRC_CallBackTalkData fTalkDataCallBack, DWORD dwUser);



/**
*  @brief 接收并播放音频
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @return 返回值：音频播放句柄，需要保存起来，用于其他音频输出的操作
*/
VZ_LPRC_API int __stdcall VzLPRClient_OpenSound(VzLPRClientHandle handle);

/**
*  @brief 停止接收和播放音频
*  @param [IN] iAudioPlayHandle VZC_OpenSound函数返回的音频播放句柄
*  @return 返回值：返回值为0表示成功，返回其他值表示失败
*/
VZ_LPRC_API int __stdcall VzLPRClient_CloseSound(int iAudioPlayHandle);


//接收及获取音频
/**
*  @brief 接收并获取音频数据
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] bNeedEncode 获取的数据是否压缩，0为ACM，1为G711
*  @param [IN] fAudioDataCallBack 音频数据回调函数
*  @param [IN] dwUser 回调函数的上下文
*  @return 返回值：音频播放句柄，需要保存起来，用于其他音频输出的操作
*/
VZ_LPRC_API int __stdcall VzLPRClient_OpenSoundData(VzLPRClientHandle handle, int bNeedEncode,
											VZLPRC_CallBackTalkData fAudioDataCallBack, DWORD dwUser);
/**
*  @brief 停止接收和获取音频数据
*  @param [IN] iSoundDataHandle VZC_OpenSoundData函数返回的音频数据句柄
*  @return 返回值：返回值为0表示成功，返回其他值表示失败
*/
VZ_LPRC_API int __stdcall VzLPRClient_CloseSoundData(int iSoundDataHandle);

/**
*  @brief 控制是否允许音频
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] bEnable 0为禁用音频播放和音频数据回调，1为允许
*  @return 返回值：返回值为0表示成功，返回其他值表示失败
*/
VZ_LPRC_API int __stdcall VzLPRClient_AudioEnable(VzLPRClientHandle handle, int bEnable);

/**
*  @brief 自动变焦的功能
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @return 返回值：返回值为0表示成功，返回其他值表示失败
*/
VZ_LPRC_API int __stdcall VzLPRClient_DoAutoFocus(VzLPRClientHandle handle);

/**
*  @brief 设置需要识别的车牌类型
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] uBitsRecType 需要识别的车牌类型按位或的值，车牌类型位详见定义VZ_LPRC_REC_XXX
*  @return 返回值：返回值为0表示成功，返回其他值表示失败
*  @note  在需要识别特定车牌时，调用该接口来设置，将不同类型的车牌位定义取或，得到的结果作为参数传入；
*  @note  在不必要的情况下，使用最少的车牌识别类型，将最大限度提高识别率；
*  @note  默认识别蓝牌和黄牌；
*  @note  例如，需要识别蓝牌、黄牌、警牌，那么输入参数uBitsRecType = VZ_LPRC_REC_BLUE|VZ_LPRC_REC_YELLOW|VZ_LPRC_REC_POLICE
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetPlateRecType(VzLPRClientHandle handle, unsigned uBitsRecType);

/**
*  @brief 获取已设置的需要识别的车牌类型位
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [OUT] pBitsRecType 需要识别的车牌类型按位或的变量的地址，车牌类型位详见定义VZ_LPRC_REC_XXX
*  @return 返回值：返回值为0表示成功，返回其他值表示失败
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetPlateRecType(VzLPRClientHandle handle, unsigned *pBitsRecType);

/**
*  @brief 获取系统版本号
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN/OUT] pstrVersion 保存返回版本号的BUF
*  @param [IN] uSizeBuf 保存返回版本号的BUF大小
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetSystemVersion(VzLPRClientHandle handle, char *pstrVersion, unsigned uSizeBuf);

/**
*  @brief 设置允许的车牌识别触发类型
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] uBitsTrigType 允许的车牌识别触发类型按位或的值，允许触发类型位详见定义VZ_LPRC_TRIG_ENABLE_XXX
*  @return 返回值：返回值为0表示成功，返回其他值表示失败
*  @note  如果设置不允许某种类型的触发，那么该种类型的触发结果也不会保存在设备的SD卡中
*  @note  默认输出稳定触发和虚拟线圈触发
*  @note  不会影响手动触发和IO输入触发
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetPlateTrigType(VzLPRClientHandle handle, unsigned uBitsTrigType);

/**
*  @brief 获取已设置的允许的车牌识别触发类型
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [OUT] pBitsTrigType 允许的车牌识别触发类型按位或的变量的地址，允许触发类型位详见定义VZ_LPRC_TRIG_ENABLE_XXX
*  @return 返回值：返回值为0表示成功，返回其他值表示失败
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetPlateTrigType(VzLPRClientHandle handle, unsigned *pBitsTrigType);


/**
*  @brief 设置车牌图片里是否显示车牌框
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] bShow 是否显示车牌框，输入值(0或1)，1代表显示，0代表不显示
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetIsShowPlateRect(VzLPRClientHandle handle, int bShow);

/**
*  @brief 获取用户自定义配置
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [OUT] buffer 保存返回配置的buffer
*  @param [IN] buffer_len 保存返回的buffer大小
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetUserDefinedConfig(VzLPRClientHandle handle, char* buffer, int buffer_len);

/**
*  @brief 设置用户自定义配置
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] buffer 配置内容，必需为字符串
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetUserDefinedConfig(VzLPRClientHandle handle, char* buffer);

/**
*  @brief 修改网络参数
*  @param  [IN] SL        设备序列号低位字节
*  @param  [IN] SH		  设备序列号高位字节	
*  @param [IN] strNewIP   新IP     格式如"192.168.3.109"
*  @param [IN] strGateway 网关     格式如"192.168.3.1"
*  @param [IN] strNetmask 子网掩码 格式如"255.255.255.0"
*  @note 可以用来实现跨网段修改IP的功能
*  @ingroup group_global
*/
VZ_LPRC_API int __stdcall VzLPRClient_UpdateNetworkParam(unsigned int SL, unsigned int SH, const char* strNewIP, const char* strGateway, const char *strNetmask);


/**
*  @brief 获取设备加密类型和当前加密类型
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] pData 加密信息
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetEMS(VzLPRClientHandle handle, VZ_LPRC_ACTIVE_ENCRYPT* pData);

/**
*  @brief 设置设备加密类型
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] pCurrentKey 当前识别密码
*  @param [IN] nEncyptId	修改的加密类型ID 
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetEncrypt(VzLPRClientHandle handle, const void* pCurrentKey, const unsigned nEncyptId);

/**
*  @brief 修改设备识别密码
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] pCurrentKey 当前识别密码
*  @param [IN] pNewKey	新识别密码
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_ChangeEncryptKey(VzLPRClientHandle handle, const void* pCurrentKey, const void* pNewKey);

/**
*  @brief 重置设备识别密码
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] pPrimeKey 当前设备主密码
*  @param [IN] pNewKey	新识别密码
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_ResetEncryptKey(VzLPRClientHandle handle, const void* pPrimeKey, const void* pNewKey);

/**
*  @brief GPIO输入回调函数
*  @param [IN] nGPIOId GPIO输入ID
*  @param [IN] nVal	GPIO输入值
*  @param [IN] pUserData 用户自定义数据
*/
typedef void (__stdcall *VZLPRC_GPIO_RECV_CALLBACK)(VzLPRClientHandle handle, int nGPIOId, int nVal, void* pUserData);

/**
*  @brief 设置GPIO输入回调函数
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] func GPIO输入回调函数
*  @param [IN] pUserData 用户自定义数据
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetGPIORecvCallBack(VzLPRClientHandle handle, VZLPRC_GPIO_RECV_CALLBACK func, void* pUserData);


// #ifdef _DEV_ENCRYPT_
/**
*  @brief 获取用户密码
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] pPrimeKey 主密码
*  @param [IN] pUserKey	 加密过的用户密码
*  @return 返回值为0表示成功，返回其他值表示失败。
*  @note 此接口不对外提供
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetEncryptKey(VzLPRClientHandle handle, const void* pPrimeKey, const void* pUserKey);

//#endif

VZ_LPRC_API int __stdcall VzLPRClient_GetSesID(VzLPRClientHandle handle, char* buffer,int buffer_len);

/**
*  @brief 将图像保存为JPEG到指定路径，可指定图像尺寸的模式
*  @param  [IN] pImgInfo 图像结构体，目前只支持默认的格式，即ImageFormatRGB
*  @param  [IN] pFullPathName 设带绝对路径和JPG后缀名的文件名字符串
*  @param  [IN] nQuality JPEG压缩的质量，取值范围1~100；
*  @param  [IN] mode     图像大小的模式；
*  @return 0表示成功，-1表示失败
*  @note   给定的文件名中的路径需要存在
*/
VZ_LPRC_API int __stdcall VzLPRClient_ImageSaveToJpegEx(const VZ_LPRC_IMAGE_INFO *pImgInfo, const char *pFullPathName, int nQuality, IMG_SIZE_MODE mode);

/**
*  @brief 将图像编码为JPEG，保存到指定内存，可指定图像尺寸的模式
*  @param  [IN] pImgInfo 图像结构体，目前只支持默认的格式，即ImageFormatRGB
*  @param  [IN/OUT] pDstBuf JPEG数据的目的存储首地址
*  @param  [IN] uSizeBuf JPEG数据地址的内存的最大尺寸；
*  @param  [IN] nQuality JPEG压缩的质量，取值范围1~100；
*  @param  [IN] mode     图像大小的模式；
*  @return >0表示成功，即编码后的尺寸，-1表示失败，-2表示给定的压缩数据的内存尺寸不够大
*/
VZ_LPRC_API int __stdcall VzLPRClient_ImageEncodeToJpegEx(const VZ_LPRC_IMAGE_INFO *pImgInfo, void *pDstBuf, unsigned uSizeBuf, int nQuality, IMG_SIZE_MODE mode);

/**
*  @brief 设置是否输出实时结果
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] bOutput 是否输出
*  @return 0表示成功，-1表示失败
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetIsOutputRealTimeResult( VzLPRClientHandle handle, bool bOutput );

/**
*  @brief 获取设备硬件信息
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [OUT] status 状态 0 没有取得硬件信息，1有基本信息，2有基本和扩展信息
*  @param [OUT] mac 输出mac地址，输入字符串缓冲长度至少为20
*  @param [OUT] serialno 输出序列号，输入字符串缓冲长度至少为20
*  @param [OUT] device_type 设备类型，参考IVS_TYPE定义
*  @param [OUT] pdata_ex 扩展硬件信息，参考VZ_FS_INFO_EX定义
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetHwInfo( VzLPRClientHandle handle, int* status, char* mac, char* serialno,int *device_type,VZ_FS_INFO_EX *pdata_ex);


/**
*  @brief 保存抓图数据到Jpeg文件
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] pFullPathName 图片路径
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SaveSnapImageToJpeg(VzLPRClientHandle handle, const char *pFullPathName);


/**
*  @brief 获取当前组网的设备信息
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] pData 当前所有已连接的组网设备
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetOvzid(VzLPRClientHandle handle, VZ_OVZID_RESULT* pData);

/**
*  @brief 配置组网
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] pInfo 组网设备信息
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_EnableDevicegroup(VzLPRClientHandle handle, VZ_OVZID_RESULT *pData);

/**
*  @brief 开启/关闭“接受组网识别结果”
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] bEnable true : 开启;false : 关闭
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetDGResultEnable(VzLPRClientHandle handle, bool bEnable);

/**
*  @brief 获取主码流分辨率；
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [OUT] sizeval 详见VZDEV_FRAMESIZE_宏定义
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetVideoFrameSizeIndex(VzLPRClientHandle handle, int *sizeval);

/**
*  @brief 设置主码流分辨率；
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] sizeval 详见VZDEV_FRAMESIZE_宏定义
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetVideoFrameSizeIndex(VzLPRClientHandle handle, int sizeval);

/**
*  @brief 获取主码流帧率
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [OUT] Rateval 帧率，范围1-25
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetVideoFrameRate(VzLPRClientHandle handle, int *Rateval);//1-25

/**
*  @brief 设置主码流帧率；
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] Rateval 帧率，范围1-25
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetVideoFrameRate(VzLPRClientHandle handle, int Rateval);//1-25

/**
*  @brief 获取主码流压缩模式；
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [OUT] modeval 详见VZDEV_VIDEO_COMPRESS_宏定义
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetVideoCompressMode(VzLPRClientHandle handle, int *modeval);//VZDEV_VIDEO_COMPRESS_XXX

/**
*  @brief 设置主码流压缩模式；
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] modeval 详见VZDEV_VIDEO_COMPRESS_宏定义
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetVideoCompressMode(VzLPRClientHandle handle, int modeval);//VZDEV_VIDEO_COMPRESS_XXX


/**
*  @brief 获取主码流比特率；
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [OUT] rateval 当前视频比特率
*  @param [OUT] ratelist 暂时不用
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetVideoCBR(VzLPRClientHandle handle, int *rateval/*Kbps*/, int *ratelist);

/**
*  @brief 设置主码流比特率；
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [OUT] rateval 当前视频比特率
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetVideoCBR(VzLPRClientHandle handle, int rateval/*Kbps*/);

/**
*  @brief 获取智能视频显示模式
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [OUT] pDrawMode 显示模式，参考VZ_LPRC_DRAWMODE
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetDrawMode(VzLPRClientHandle handle, VZ_LPRC_DRAWMODE * pDrawMode);

/**
*  @brief 设置智能视频显示模式
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] pDrawMode 显示模式，参考VZ_LPRC_DRAWMODE
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetDrawMode(VzLPRClientHandle handle, VZ_LPRC_DRAWMODE * pDrawMode);

/**
*  @brief 获取视频参数；
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [OUT] brt 亮度
*  @param [OUT] cst 对比度
*  @param [OUT] sat 饱和度
*  @param [OUT] hue 色度
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetVideoPara(VzLPRClientHandle handle, int *brt, int *cst, int *sat, int *hue);

/**
*  @brief 设置视频参数；
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] brt 亮度
*  @param [IN] cst 对比度
*  @param [IN] sat 饱和度
*  @param [IN] hue 色度
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetVideoPara(VzLPRClientHandle handle, int brt, int cst, int sat, int hue);

/**
*  @brief 设置通道主码流编码方式
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [OUT] cmd    返回的编码方式, 0->H264  1->MPEG4  2->JPEG  其他->错误
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetVideoEncodeType(VzLPRClientHandle handle, int cmd);

/**
*  @brief 获取视频图像质量；
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] levelval //0~6，6最好
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetVideoVBR(VzLPRClientHandle handle, int *levelval);

/**
*  @brief 设置视频图像质量；
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] levelval //0~6，6最好
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetVideoVBR(VzLPRClientHandle handle, int levelval);


/**
*  @brief 获取视频制式；
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] frequency 0:MaxOrZero, 1: 50Hz, 2:60Hz
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetFrequency(VzLPRClientHandle handle, int *frequency);

/**
*  @brief 设置视频制式；
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] frequency 0:MaxOrZero, 1: 50Hz, 2:60Hz
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetFrequency(VzLPRClientHandle handle, int frequency);

/**
*  @brief 获取曝光时间；
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] shutter 2:>0~8ms 停车场推荐, 3: 0~4ms, 4:0~2ms 卡口推荐
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetShutter(VzLPRClientHandle handle, int *shutter);

/**
*  @brief 设置曝光时间；
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] shutter 2:>0~8ms 停车场推荐, 3: 0~4ms, 4:0~2ms 卡口推荐
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetShutter(VzLPRClientHandle handle, int shutter);

/**
*  @brief 获取图像翻转；
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] flip, 0: 原始图像, 1:上下翻转, 2:左右翻转, 3:中心翻转
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetFlip(VzLPRClientHandle handle, int *flip);

/**
*  @brief 设置图像翻转；
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] flip, 0: 原始图像, 1:上下翻转, 2:左右翻转, 3:中心翻转
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetFlip(VzLPRClientHandle handle, int flip);



/**** VzLPRClientSDK V2扩展接口 主要提供x64平台使用(x86位平台可以使用新接口 但是x64平台一定要使用新接口)****/
/**
*  @brief 停止发送本地音频
*  @param [IN] iTalkHandle VZC_TalkStart函数和VZC_TalkStartByWaveFile函数返回的本地音频发送的句柄
*  @return 返回值：返回值为0表示成功，返回其他值表示失败
*/
VZ_LPRC_API int __stdcall VzLPRClient_TalkStop_V2(int iTalkHandle);


typedef void (CALLBACK * VZLPRC_CallBackTalkData_V2)(int iTalkHandle, const void *pDataBuffer, DWORD dwBufSize ,BYTE byAudioFlag, void* pUser);

/**
*  @brief 开始发送本地话筒的音频到远程
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] fTalkDataCallBack 本地音频数据回调，可以为空
*  @param [IN] pUser 本地音频数据回调的上下文
*  @return 返回值：本地音频发送的句柄，需要保存起来，用于其他本地音频发送相关的操作
*/
VZ_LPRC_API int __stdcall VzLPRClient_TalkStart_V2(VzLPRClientHandle handle, VZLPRC_CallBackTalkData_V2 fTalkDataCallBack, void* pUser);

/**
*  @brief 接收并获取音频数据
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] bNeedEncode 获取的数据是否压缩，0为ACM，1为G711
*  @param [IN] fAudioDataCallBack 音频数据回调函数
*  @param [IN] pUser 回调函数的上下文
*  @return 返回值：音频播放句柄，需要保存起来，用于其他音频输出的操作
*/
VZ_LPRC_API int __stdcall VzLPRClient_OpenSoundData_V2(VzLPRClientHandle handle, int bNeedEncode,
											VZLPRC_CallBackTalkData_V2 fAudioDataCallBack, void* pUser);
/**
*  @brief 停止接收和获取音频数据
*  @param [IN] iSoundDataHandle VZC_OpenSoundData函数返回的音频数据句柄
*  @return 返回值：返回值为0表示成功，返回其他值表示失败
*/
VZ_LPRC_API int __stdcall VzLPRClient_CloseSoundData_V2(int iSoundDataHandle);

/**
*  @brief 设置车牌识别触发延迟时间
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] nDelay 触发延迟时间,时间范围[0, 10000)
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetTriggerDelay(VzLPRClientHandle handle, int nDelay);

/**
*  @brief 获取车牌识别触发延迟时间
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [OUT] nDelay 触发延迟时间,时间范围[0, 10000)
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetTriggerDelay(VzLPRClientHandle handle, int* nDelay);

/**
*  @brief 设置白名单验证模式
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] nType 0 脱机自动启用;1 启用;2 不启用
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetWLCheckMethod(VzLPRClientHandle handle, int nType);

/**
*  @brief 获取白名单验证模式
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [OUT]	nType 0 脱机自动启用;1 启用;2 不启用
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetWLCheckMethod(VzLPRClientHandle handle, int* nType);

/**
*  @brief 设置白名单模糊匹配
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] nFuzzyType 0  精确匹配;1 相似字符匹配;2 普通字符模糊匹配
*  @param [IN] nFuzzyLen  允许误识别长度
*  @param [IN] nFuzzyType 忽略汉字
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetWLFuzzy(VzLPRClientHandle handle, int nFuzzyType, int nFuzzyLen, bool bFuzzyCC);

/**
*  @brief 获取白名单模糊匹配
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] nFuzzyType 0  精确匹配;1 相似字符匹配;2 普通字符模糊匹配
*  @param [IN] nFuzzyLen  允许误识别长度
*  @param [IN] nFuzzyType 忽略汉字
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetWLFuzzy(VzLPRClientHandle handle, int* nFuzzyType, int* nFuzzyLen, bool* bFuzzyCC);

/**
*  @brief 设置输出配置
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] pOutputConfig 输出配置
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetOutputConfig(VzLPRClientHandle handle, VZ_OutputConfigInfo* pOutputConfigInfo);

/**
*  @brief 获取输出配置0
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] pOutputConfig 输出配置
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetOutputConfig(VzLPRClientHandle handle, VZ_OutputConfigInfo* pOutputConfigInfo);

/**
*  @brief 获取设备序列号；
*  @param [IN] ip ip统一使用字符串的形式传入
*  @param [IN] port 使用和登录时相同的端口
*  @param [OUT] SerHi 序列号高位
*  @param [OUT] SerLo 序列号低位
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetSerialNo(char *ip, WORD port, unsigned int *SerHi, unsigned int *SerLo);

/**
*  @brief 开始实时图像数据流，用于实时获取图像数据
*  @param  [IN] handle		由VzLPRClient_Open函数获得的句柄
*  @return 返回值为0表示成功，返回其他值表示失败。
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_StartRealPlayDecData(VzLPRClientHandle handle);

/**
*  @brief 停止实时图像数据流
*  @param  [IN] handle		由VzLPRClient_Open函数获得的句柄
*  @return 返回值为0表示成功，返回其他值表示失败。
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_StopRealPlayDecData(VzLPRClientHandle handle);


/**
*  @brief 从解码流中获取JPEG图像，保存到指定内存
*  @param  [IN] handle		由VzLPRClient_Open函数获得的句柄
*  @param  [IN/OUT] pDstBuf JPEG数据的目的存储首地址
*  @param  [IN] uSizeBuf JPEG数据地址的内存的最大尺寸；
*  @param  [IN] nQuality JPEG压缩的质量，取值范围1~100；
*  @return >0表示成功，即编码后的尺寸，-1表示失败，-2表示给定的压缩数据的内存尺寸不够大
*  @ingroup group_global
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetJpegStreamFromRealPlayDec(VzLPRClientHandle handle, void *pDstBuf, unsigned uSizeBuf, int nQuality);

/**
*  @brief 获取串口推送配置
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] pConfigInfo 串口推送配置
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetRS485PushConfig(VzLPRClientHandle handle, VZ_RS485_Config* pConfigInfo);

/**
*  @brief 设置串口推送配置
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] pConfigInfo 串口推送配置
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetRS485PushConfig(VzLPRClientHandle handle, VZ_RS485_Config* pConfigInfo);


/**
*  @brief aes解密功能
*  @param [IN] hertextb    待解密16进制的数据
*  @param [IN] password    密码
*  @param [IN] nTextBits   数据长度
*  @param [IN] decryptText 解密字符串
*  @param [IN] decryptLen  传入解密字符串buffer的长度
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_AesCtrDecrypt(const char* hertextb, int nTextBits, const char* password, char *decryptText, int decryptLen);

/**
*  @brief 根据ID获取组网结果的大图片
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] id     车牌记录的ID
*  @param  [IN] sn     设备序列号
*  @param  [IN] pdata  存储图片的内存
*  @param  [IN][OUT] size 为传入传出值，传入为图片内存的大小，返回的是获取到jpg图片内存的大小
*  @return 返回值为0表示成功，返回-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_LoadGroupFullImageById(VzLPRClientHandle handle, int id, const char* sn, void* pdata, int* size);

/**
*  @brief 根据ID获取组网结果的小图片
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param  [IN] id     车牌记录的ID
*  @param  [IN] sn     设备序列号
*  @param  [IN] pdata  存储图片的内存
*  @param  [IN][OUT] size 为传入传出值，传入为图片内存的大小，返回的是获取到jpg图片内存的大小
*  @return 返回值为0表示成功，返回-1表示失败
*  @ingroup group_device
*/
VZ_LPRC_API int __stdcall VzLPRClient_LoadGroupClipImageById(VzLPRClientHandle handle, int id, const char* sn, void* pdata, int* size);

/**
*  @brief 获取NTP配置
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [OUT] ntp_server_name 服务器地址
*  @param [OUT] ntp_enable 是否启用NTP
*  @param [OUT] frequency_time 同步的时间
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_GetNtpConfig(VzLPRClientHandle handle, char* ntp_server_name, int name_size, bool* ntp_enable, int* frequency_time);

/**
*  @brief 设置NTP配置
*  @param  [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] ntp_server_name 服务器地址
*  @param [IN] ntp_enable 是否启用NTP
*  @param [IN] frequency_time 同步的时间
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetNtpConfig(VzLPRClientHandle handle, const char* ntp_server_name, bool ntp_enable, int frequency_time);


/**
*  @brief 开始二次登录
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] pData 加密信息
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_StartLogin(VzLPRClientHandle handle, VZ_LPRC_ACTIVE_ENCRYPT* pData);


/**
*  @brief 执行二次登录
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] key 用户密码
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_LoginAuth(VzLPRClientHandle handle, const char* key);

/**
*  @brief 打开
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @param [IN] key 用户密码
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_OpenAuth(const char *pStrIP, WORD wPort, const char *pStrUserName, const char *pStrPassword, const char* key);


/**
*  @brief 设置当前配置为默认配置
*  @param [IN] handle 由VzLPRClient_Open函数获得的句柄
*  @return 返回值为0表示成功，返回其他值表示失败。
*/
VZ_LPRC_API int __stdcall VzLPRClient_SetUserDefaultCfg(VzLPRClientHandle handle);

#endif
